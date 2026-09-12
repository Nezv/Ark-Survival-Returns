"""Source mesh attachments and disconnected surfaces for detailed cuboid fitting."""
import re
import numpy as np
from scipy.spatial.transform import Rotation as R


def voxel_cubes(s):
    """Sample original triangles, then merge adjacent surface cells per bone.

    Thin curved tusks, antlers, fins and tentacles keep their empty spaces.
    Source fur cards become volumetric blocks at the same sampling resolution.
    No helper bones are introduced and disconnected limbs are never bridged.
    """
    from build_dinosaurs import palette_index
    result=[];grids={}
    base=float(np.ptp(s.source_vertices,axis=0).max())/70
    for bone,name in enumerate(s.names):
        for mat,material in enumerate(s.material_names):
            triangles=s.source_vertices[s.triangles[(s.face_bone==bone)&(s.face_material==mat)]]
            if not len(triangles):continue
            extent=float(np.ptp(triangles.reshape(-1,3),axis=0).max())
            pitch=max(base/4,min(base,extent/14))
            samples=[]
            for triangle in triangles:
                steps=max(1,int(np.ceil(np.linalg.norm(np.roll(triangle,-1,axis=0)-triangle,axis=1).max()/pitch*1.8)))
                if steps not in grids:
                    u,v=np.triu_indices(steps+1)
                    grids[steps]=np.column_stack([u,steps-v])/steps
                uv=grids[steps]
                points=triangle[0]+uv[:,0,None]*(triangle[1]-triangle[0])+uv[:,1,None]*(triangle[2]-triangle[0])
                samples.append(np.floor(points/pitch).astype(np.int32))
            cells=np.unique(np.concatenate(samples),axis=0)
            # Coalesce X runs, then identical adjacent rows in Y. Each cell
            # keeps its original triangle's bone and material ownership.
            rows={}
            for x,y,z in cells:rows.setdefault((int(z),int(y)),[]).append(int(x))
            runs={}
            for (z,y),xs in rows.items():
                xs.sort();start=last=xs[0]
                for x in xs[1:]+[None]:
                    if x is not None and x==last+1:last=x;continue
                    runs.setdefault((z,start,last),[]).append(y)
                    start=last=x
            slabs={}
            for (z,x0,x1),ys in sorted(runs.items()):
                ys.sort();start=last=ys[0]
                for y in ys[1:]+[None]:
                    if y is not None and y==last+1:last=y;continue
                    slabs.setdefault((x0,x1,start,last),[]).append(z)
                    start=last=y
            merged_cells=0
            for (x0,x1,y0,y1),zs in sorted(slabs.items()):
                zs.sort();start=last=zs[0]
                for z in zs[1:]+[None]:
                    if z is not None and z==last+1:last=z;continue
                    origin=np.array([x0,y0,start],dtype=float)*pitch
                    size=np.array([x1-x0+1,y1-y0+1,last-start+1],dtype=float)*pitch
                    merged_cells+=(x1-x0+1)*(y1-y0+1)*(last-start+1)
                    color=palette_index(name,material,1)
                    result.append({'bone':bone,'name':f'{name} / {material} / surface {len(result)+1}',
                                   'origin':origin,'size':size,'pivot':s.wp[bone].copy(),'rotation':np.zeros(3),'palette':color})
                    start=last=z
            assert merged_cells==len(cells),'Surface merging changed occupied volume'
    assert result
    return result


def append_mesh(s, path, socket=None):
    from ark_geometry import chunks, bone_names
    psk=chunks(path)
    points=np.frombuffer(psk['PNTS0000'][2],'<f4').reshape(-1,3)[:,[0,2,1]]*[.1,.1,-.1]
    wedges=np.frombuffer(psk['VTXW0000'][2],dtype=[('vertex','<u4'),('uv','<f4',2),('material','u1'),('pad','u1',3)])
    raw_faces=psk.get('FACE0000',psk.get('FACE3200'))
    faces=np.frombuffer(raw_faces[2],dtype=[('wedge','<u2' if raw_faces[0]==12 else '<u4',3),('mat','u1'),('aux','u1'),('smooth','<u4')])
    triangles=wedges['vertex'][faces['wedge']]
    if socket:
        bone=s.index[socket['bone']]
        points=s.wp[bone]+s.wr[bone].apply(socket['rotation'].apply(points)+socket['translation'])
        dominant=np.full(len(points),bone)
        face_bone=np.full(len(faces),bone)
    else:
        names=bone_names(psk['REFSKELT'])
        assert set(names)<=set(s.names),f'Attachment has unknown bones: {path}'
        # Both skeletal components share one bind pose; reject incompatible rigs.
        raw=psk['REFSKELT'][2]
        import struct
        translation=np.array([struct.unpack_from('<3f',raw,i*120+92) for i in range(len(names))])[:,[0,2,1]]*[.1,.1,-.1]
        assert np.max(abs(translation-s.t[[s.index[n] for n in names]]))<.001
        q=np.array([struct.unpack_from('<4f',raw,i*120+76) for i in range(len(names))])[:,[0,2,1,3]]*[1,1,-1,-1]
        q[0,:3]*=-1
        rotations=R.from_quat(q);world_p=[];world_r=[]
        for i,name in enumerate(names):
            parent=struct.unpack_from('<i',raw,i*120+72)[0] if i else -1
            assert (names[parent] if parent>=0 else None)==(s.names[s.parents[s.index[name]]] if s.parents[s.index[name]]>=0 else None)
            world_p.append(translation[i] if parent<0 else world_p[parent]+world_r[parent].apply(translation[i]))
            world_r.append(rotations[i] if parent<0 else world_r[parent]*rotations[i])
        weights=np.zeros((len(points),len(s.names)))
        for weight,vertex,i in np.frombuffer(psk['RAWWEIGHTS'][2],dtype=[('weight','<f4'),('vertex','<i4'),('bone','<i4')]):
            weights[vertex,s.index[names[i]]]+=weight
        # Normalize secondary-mesh inverse binds onto the animation skeleton.
        # Gorilla's eye binds differ by about 2.3 degrees between components.
        normalized=np.zeros_like(points)
        for i,name in enumerate(names):
            target=s.index[name];w=weights[:,target];mask=w>0
            local=world_r[i].inv().apply(points[mask]-world_p[i])
            normalized[mask]+=(s.wp[target]+s.wr[target].apply(local))*w[mask,None]
        assert np.all(weights.sum(axis=1)>0)
        points=normalized/weights.sum(axis=1)[:,None]
        dominant=weights.argmax(axis=1)
        face_bone=weights[triangles].sum(axis=1).argmax(axis=1)
    material_offset=len(s.material_names)
    material=np.zeros(len(points),dtype=int)
    material[wedges['vertex']]=wedges['material']
    s.material_names.extend([('horn' if socket else 'fur')+f'_{i}' for i in range(psk['MATT0000'][1])])
    s.triangles=np.concatenate([s.triangles,triangles+len(s.source_vertices)])
    s.source_vertices=np.concatenate([s.source_vertices,points])
    s.material=np.concatenate([s.material,material+material_offset])
    s.dominant=np.concatenate([s.dominant,dominant])
    s.face_bone=np.concatenate([s.face_bone,face_bone])
    s.face_material=np.concatenate([s.face_material,faces['mat'].astype(int)+material_offset])
    s.faces+=len(faces)


def extend_source(s):
    if s.asset=='Gorilla':
        append_mesh(s,s.folder/'Gorilla_fur.psk')
    if s.asset=='Equus':
        text=(s.folder.parent/'logs/skeleton-properties.txt').read_text()
        block=re.search(r'SocketName = UnicornSocket\s+BoneName = (\w+)\s+RelativeLocation = \{ ([^}]+)\}\s+RelativeRotation = \{ ([^}]+)\}\s+RelativeScale = \{ ([^}]+)\}',text)
        assert block,'Missing original UnicornSocket'
        def values(row):return {key:float(value) for key,value in re.findall(r'(\w+)=([0-9.eE+\-]+)',row)}
        location,angles,scale=[values(block[i]) for i in (2,3,4)]
        assert all(abs(v-1)<1e-6 for v in scale.values()),'Unsupported horn socket scale'
        # UE -> mirrored ActorX -> our Y-up coordinates. UE pitch/roll reverse
        # their right-handed signs; yaw is unchanged before the basis change.
        basis=np.array([[1,0,0],[0,0,1],[0,1,0]])
        rotation=R.from_euler('xyz',[-angles['Roll'],-angles['Pitch'],angles['Yaw']],degrees=True)
        socket={'bone':block[1],'translation':basis@np.array([location[a] for a in 'XYZ'])*.1,
                'rotation':R.from_matrix(basis@rotation.as_matrix()@basis.T)}
        append_mesh(s,s.folder/'SM_Unicorn_Horn_01.pskx',socket)
