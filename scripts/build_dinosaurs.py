"""Fit ARK-influenced cuboids to native bones and bind original PSA clips.

Run workflow.py for extraction + build, or this script to rebuild saved inputs.
Outputs retain baked world bind axes, or native bind axes for scaled Workshop rigs.
"""
import argparse
import base64
import hashlib
import json
import math
from pathlib import Path
import shutil
import uuid
import warnings
import numpy as np
from PIL import Image, ImageDraw
from scipy.spatial.transform import Rotation as R
from ark_geometry import ROOT, Skeleton
from ark_animation import read_clips, convert_clip, animation_tracks
from extract_dinosaurs import SPECIES, resource_names, CREATURES, asset_directory
from model_catalog import MODELS, NATIVE_ASSETS, PALETTES as MODEL_PALETTES
from mesh_detail import voxel_cubes

PALETTES = {
 'Spinosaurus':['#687362','#98a081','#40594e','#beb393','#e4d5b2','#232f28','#dbad44','#a35b43'],
 'Parasaur':['#a3895e','#c4ae81','#627156','#d7c498','#ebdfb9','#343d2a','#e0ad42','#a6674d'],
 'Ceratosaurus':['#7d675b','#b1987c','#4d5751','#c7b294','#e4d5b7','#292e29','#dc9e39','#974d41'],
 'Dilophosaur':['#607f60','#8eab74','#3e5950','#bcb786','#e0d6aa','#25352b','#e0b33e','#b9754f'],
 'Acrochantosaur':['#796b5c','#a89978','#4c5850','#c4b497','#e3d5b6','#28312d','#d8a741','#985b47'],
 'Allosaurus':['#6d775d','#a3a984','#4b5b48','#c4b795','#dfd2b0','#263328','#db9a39','#a45d49'],
 'Ankylosaurus':['#797157','#ada07c','#504e43','#bba480','#e0cfaa','#302f28','#d3aa42','#99634b'],
 'Carnotaurus':['#976f57','#bea083','#5c564b','#cbb693','#e8dab7','#302e29','#e3aa3e','#a75846'],
 'Pegomastax':['#73695d','#a3947e','#4b5045','#bda987','#decca7','#2d3029','#d8a438','#8f5440'],
 'Lystrosaurus':['#828d63','#b0b68a','#57694b','#c4b88f','#e0d3ad','#303c2a','#daaf40','#aa7350'],
 'Tyranosaur':['#68715b','#969a79','#404f43','#bdb48e','#e4d8b4','#222d25','#dda440','#985843'],
 'Titanosaur':['#657061','#919482','#424e47','#c2bda1','#e0d6b0','#212d29','#c18c38','#a99e88'],
 'Giganotosaur':['#5e6963','#879083','#3e4e49','#b9b5a0','#ddd5b5','#1d2726','#dcad4c','#9e6956'],
 'Therezinosaur':['#52615b','#889183','#344b48','#c3b799','#e0d4b0','#1d2928','#ce9a46','#854e3f'],
 'Brontosaur':['#71806b','#9d9f84','#4b6054','#c1b998','#e0d7b5','#263b33','#d5aa50','#8a7560'],
 'Triceratops':['#896d50','#ae926d','#584f43','#c5ad81','#e5d6ac','#302e28','#cf9b3d','#a65e44'],
 'Velociraptor':['#786b55','#a69673','#424f48','#c4b38c','#e5d6b0','#242c29','#da9c34','#a2563e'],
 'Argentavis':['#65717c','#9aa5a5','#37454f','#c2c0ad','#d9ceac','#212c33','#d5a645','#9c7052'],
 'Piterodon':['#697864','#94a084','#405950','#b3a184','#dac59d','#25372f','#d1a543','#a46947'],
}
PALETTES.update(MODEL_PALETTES)

def write_json(path,data,pretty=False):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,indent=2 if pretty else None,separators=None if pretty else (',',':'),allow_nan=False)+'\n',encoding='utf-8')

def uid(value):
    return str(uuid.uuid5(uuid.NAMESPACE_URL,'ark-survival-returns/'+value))

def vec(a):
    return np.round(a,6).tolist()

def palette_index(bone,material,section):
    text = (bone+' '+material).lower()
    if any(x in text for x in ['eyelid','uppereye','lowereye']): return 2
    if 'eye' in text: return 6
    if any(x in text for x in ['tongue','mouth']): return 7
    if any(x in text for x in ['claw','horn','teeth','tooth','spike','beak','tusk','antler','ivory']): return 4
    if 'feather' in text: return 2 if section%3 else 1
    if any(x in text for x in ['belly','jaw']): return 3
    if 'tail' in text: return 2 if section%4==0 else 0
    return [0,0,1,0][section%4]

def fitted_cubes(s):
    """Principal-axis slices preserve source proportions and bone ownership.

    We partition by source material before slicing so eyes/feathers/horns don't
    get swallowed by body boxes. Box thickness has a small scale-relative floor.
    These are editable rigid cuboids, not a smooth skin-weighted source mesh.
    """
    if s.asset in NATIVE_ASSETS:return native_cubes(s,voxel_cubes(s))
    result=[]
    floor = max(.035,float(np.ptp(s.source_vertices,axis=0).max())/1800)
    for bone,name in enumerate(s.names):
        for mat,material in enumerate(s.material_names):
            points=s.surface(bone,mat)
            if len(points)<3: continue
            center=points.mean(0)
            _,_,vt=np.linalg.svd(points-center,full_matrices=False)
            basis=vt.T
            # Deterministic handedness and longitudinal direction.
            if basis[np.argmax(abs(basis[:,0])),0]<0: basis[:,0]*=-1
            if np.linalg.det(basis)<0: basis[:,2]*=-1
            rotation=R.from_matrix(basis)
            local=(points-center)@basis
            extent=np.ptp(local,axis=0)
            slices=max(1,min(14,int(np.ceil(len(points)/45)),int(np.ceil(extent[0]/max(floor*3,extent[1]*.28)))))
            edges=np.linspace(local[:,0].min(),local[:,0].max(),slices+1)
            with warnings.catch_warnings():
                warnings.simplefilter('ignore',UserWarning)
                euler=rotation.as_euler('xyz',degrees=True)
            for j in range(slices):
                mask=(local[:,0]>=edges[j])&(local[:,0]<=edges[j+1])
                pts=local[mask]
                if len(pts)<2: continue
                lo,hi=pts.min(0),pts.max(0)
                lo[0],hi[0]=edges[j],edges[j+1]
                # Slight overlap hides seams between slices during rotation.
                mid=(lo+hi)/2
                size=np.maximum(hi-lo,floor)
                size[0]+=floor*.25
                origin=center+mid-size/2
                result.append({'bone':bone,'name':f'{name} / {material} / {j+1}',
                               'origin':origin,'size':size,'pivot':center,'rotation':euler,
                               'palette':palette_index(name,material,j)})
    assert result and all(np.all(c['size']>0) for c in result)
    return native_cubes(s,result)

def native_cubes(s,result):
    if s.native_bind_axes:
        for c in result:
            i=c['bone'];inverse=s.wr[i].inv()
            pivot=s.model_pivots[i]+inverse.apply(c['pivot']-s.wp[i])
            c['origin']=pivot+c['origin']-c['pivot'];c['pivot']=pivot
            with warnings.catch_warnings():
                warnings.simplefilter('ignore',UserWarning)
                c['rotation']=(inverse*R.from_euler('xyz',c['rotation'],degrees=True)).as_euler('xyz',degrees=True)
    return result

def geometry(s,cubes,identifier):
    bones=[]
    for i,name in enumerate(s.names):
        b={'name':name,'pivot':vec(s.model_pivots[i]*[-1,1,1])}
        if s.native_bind_axes:b['rotation']=vec(s.model_rotations[i]*[-1,-1,1])
        if s.parents[i]>=0: b['parent']=s.names[s.parents[i]]
        for c in (c for c in cubes if c['bone']==i):
            pos=c['origin'].copy();pos[0]=-pos[0]-c['size'][0]
            uv={face:{'uv':[c['palette']*8+1,1],'uv_size':[6,6]} for face in ['north','east','south','west','up','down']}
            b.setdefault('cubes',[]).append({'origin':vec(pos),'size':vec(c['size']),
                 'pivot':vec(c['pivot']*[-1,1,1]),'rotation':vec(c['rotation']*[-1,-1,1]),'uv':uv})
        bones.append(b)
    extent=np.ptp(s.source_vertices,axis=0)
    desc={'identifier':'geometry.'+identifier,'texture_width':64,'texture_height':8,
          'visible_bounds_width':float(max(extent[0],extent[2])/16*2+2),
          'visible_bounds_height':float(extent[1]/16*2+2),
          'visible_bounds_offset':[0,float((s.source_vertices[:,1].max()+s.source_vertices[:,1].min())/32),0]}
    return {'format_version':'1.12.0','minecraft:geometry':[{'description':desc,'bones':bones}]}

def texture(path,label):
    im=Image.new('RGB',(64,8));d=ImageDraw.Draw(im)
    for i,col in enumerate(PALETTES[label]):
        d.rectangle((i*8,0,i*8+7,7),fill=col)
    path.parent.mkdir(parents=True,exist_ok=True);im.save(path)

def blockbench(s,cubes,animations,label,texture_path):
    groups=[];outlines=[];elements=[]
    for i,name in enumerate(s.names):
        key=uid(label+'/bone/'+name)
        groups.append({'name':name,'uuid':key,'origin':vec(s.model_pivots[i]),'rotation':vec(s.model_rotations[i]),
                       'export':True,'visibility':True,'isOpen':False,'children':[]})
        outlines.append({'uuid':key,'isOpen':False,'children':[]})
    for i,p in enumerate(s.parents):
        if p>=0:outlines[p]['children'].append(outlines[i])
    for n,c in enumerate(cubes):
        key=uid(label+'/cube/'+str(n));sw=c['palette']*8
        elements.append({'name':c['name'],'type':'cube','uuid':key,'box_uv':False,'export':True,
            'from':vec(c['origin']),'to':vec(c['origin']+c['size']),'origin':vec(c['pivot']),
            'rotation':vec(c['rotation']),'color':c['palette'],
            'faces':{face:{'uv':[sw+1,1,sw+7,7],'texture':0} for face in ['north','east','south','west','up','down']}})
        outlines[c['bone']]['children'].append(key)
    bb_animations=[]
    for name,clip in animations.items():
        animators={}
        for bone,tracks in clip['bones'].items():
            frames=[]
            for channel,track in tracks.items():
                sign=np.array([-1,-1,1] if channel=='rotation' else [-1,1,1] if channel=='position' else [1,1,1])
                for time,values in track.items():
                    v=np.array(values)*sign
                    frames.append({'channel':channel,'data_points':[dict(zip('xyz',map(float,v)))],
                        'uuid':uid(label+'/'+name+'/'+bone+'/'+channel+'/'+time),'time':float(time),
                        'interpolation':'linear','color':-1})
            animators[uid(label+'/bone/'+bone)]={'name':bone,'type':'bone','keyframes':frames,
                 'rotation_global':False,'quaternion_interpolation':False}
        bb_animations.append({'uuid':uid(label+'/animation/'+name),'name':name,
             'loop':'loop' if clip['loop'] else 'once','override':True,'length':clip['animation_length'],
             'snapping':30,'saved':True,'animators':animators})
    return {'meta':{'format_version':'5.0','model_format':'geckolib_model','box_uv':False},
        'name':label,'model_identifier':resource_names(label)[0],'geckolib_model_type':'entity',
        'resolution':{'width':64,'height':8},'elements':elements,'groups':groups,
        'outliner':[outlines[i] for i,p in enumerate(s.parents) if p<0],
        'textures':[{'name':texture_path.name,'id':'0','uuid':uid(label+'/texture'),'internal':True,
           'width':64,'height':8,'uv_width':64,'uv_height':8,'source':'data:image/png;base64,'+base64.b64encode(texture_path.read_bytes()).decode()}],
        'animations':bb_animations}

def preserve_inputs(label,asset,out,refresh=False):
    source=out/'source'
    for kind in ['actorx','gltf']:
        cached=ROOT/f'.work/exports/{kind}/{asset_directory(label)}'
        if cached.exists() and (refresh or not (source/kind).exists()):shutil.copytree(cached,source/kind,dirs_exist_ok=True)
    # Updated DLC meshes live below the shared skeleton/animation directory.
    mesh=MODELS.get(label,{}).get('mesh',asset)
    if '/' in mesh:
        for kind in ['actorx','gltf']:
            nested=source/kind/Path(mesh).parent
            if nested.exists():
                for file in nested.iterdir():
                    if file.is_file():shutil.copy2(file,source/kind/file.name)
    assert (source/f'actorx/{asset}.psk').exists(), f'Run workflow.py to extract {label} first'
    logs=ROOT/'.work/logs'
    old={'Giganotosaur':'Giganotosaurus','Brontosaur':'Brontosaurus','Therezinosaur':'Therizinosaurus'}.get(label,label)
    for kind in ['sources.json','mesh.log','gltf.log','animations.log','skeleton-properties.txt']:
        candidates=[logs/f'{label}_{kind}',logs/f'{old}_{kind}']
        p=next((p for p in candidates if p.exists()),None)
        if p:
            (source/'logs').mkdir(parents=True,exist_ok=True)
            shutil.copy2(p,source/'logs'/kind)
    return source

def build(label,refresh=False):
    asset=SPECIES[label];identifier,bb_filename=resource_names(label);out=CREATURES/label
    source=preserve_inputs(label,asset,out,refresh)
    s=Skeleton(asset,source)
    cubes=fitted_cubes(s)
    geo=geometry(s,cubes,identifier)
    write_json(out/f'geo/{identifier}.geo.json',geo,True)
    bones_only=json.loads(json.dumps(geo))
    for b in bones_only['minecraft:geometry'][0]['bones']:b.pop('cubes',None)
    write_json(out/f'source/{identifier}.skeleton.geo.json',bones_only,True)
    write_json(out/'source/skeleton.json',s.info(),True)
    tex=out/f'textures/entity/{identifier}.png';texture(tex,label)
    animations={};reports=[]
    for clip in read_clips(s):
        angles,offsets,check=convert_clip(s,clip)
        anim,keys=animation_tracks(s,clip,angles,offsets)
        assert clip['name'] not in animations
        animations[clip['name']]=anim
        reports.append({'name':clip['name'],'frames':clip['frames'],'fps':clip['fps'],
             'source_fps':clip['source_fps'],'timing_note':clip['timing_note'],
             'retained_keys':keys,'extra_source_bones_not_in_mesh':clip['extra_bones'],**check})
    assert animations
    write_json(out/f'animations/{identifier}.animation.json',{'format_version':'1.8.0','geckolib_format_version':2,'animations':animations})
    write_json(out/bb_filename,blockbench(s,cubes,animations,label,tex))
    hashes={str(p.relative_to(source)).replace('\\','/'):hashlib.sha256(p.read_bytes()).hexdigest()
            for kind in ['actorx','gltf'] for p in sorted((source/kind).rglob('*')) if p.is_file()}
    report={'species':label,'ark_asset':asset,'unit_scale':'1 Unreal cm = 0.1 Blockbench units',
        'bone_axes':'native bind axes (nonuniform scale supported)' if s.native_bind_axes else 'baked world bind axes',
        'bones':len(s.names),'cubes':len(cubes),'source_vertices':len(s.source_vertices),'source_triangles':s.faces,
        'animations':len(animations),'source_hashes':hashes,'clips':reports,
        'model_method':('Triangle-sampled surface cells merged into cuboids per original bone/material; fine features use smaller cells'
                        if asset in NATIVE_ASSETS else 'Material-separated principal-axis cuboid slices fitted to dominant bone vertex influences'),
        'attachments':MODELS.get(label,{}).get('extras',[]),
        'position_key_tolerance':.0005,'rotation_key_tolerance_degrees':.03,
        'timing_adjustments':[{'clip':c['name'],'note':c['timing_note']} for c in reports if c['timing_note']],
        'limitations':['Rigid cubes approximate the smooth source skin; no blended vertex weights.',
                      'Palette texture is generated, not the ARK texture.',
                      'Loop flags are name-based suggestions; review in Blockbench.',
                      'Entity controller/state transitions, sounds and Unreal notifies are not converted.']}
    write_json(out/'build_report.json',report,True)
    (out/'rebuild.py').write_text('from pathlib import Path\nimport sys\nsys.path.insert(0,str(Path(__file__).resolve().parents[2]/"scripts"))\nfrom build_dinosaurs import build\nfrom validate_dinosaurs import validate\nif __name__ == "__main__":\n    build('+repr(label)+')\n    validate('+repr(label)+')\n',encoding='utf-8')
    (out/'README.md').write_text(f'# {label}\n\nARK asset: `{asset}`. {len(s.names)} original mesh bones, {len(cubes)} fitted cubes, {len(animations)} original animation clips.\n\n'
        f'Open `{bb_filename}` in Blockbench with the GeckoLib plugin. Geometry, palette texture and all animations are embedded.\n\n'
        f'Game resources: `geo/{identifier}.geo.json`, `animations/{identifier}.animation.json`, `textures/entity/{identifier}.png`.\n\n'
        'Run `python rebuild.py` to rebuild using the shared scripts and saved source exports. This overwrites generated files, so save manual edits under another name first.\n\n'
        '`source/` includes original extracted PSK/PSA and glTF, a skeleton-only GeckoLib JSON, bone transforms, extraction logs and input hashes in `build_report.json`.\n\n'
        'Keep bone names, pivots and parent links when editing cubes. The imported clips already target these bones. Choose clips in your mod animation controller; assigning matching names does not create entity behavior automatically.\n\n'
        'The mesh is an automatic cuboid approximation with an original palette. Inspect shoulders, mouths and wing joints before final art approval. Loop flags are inferred from clip names. Unreal notifies, sounds, physics and AI are outside this conversion.\n',encoding='utf-8')
    from preview_dinosaurs import previews
    previews(out,s,cubes,animations,label)
    print(f'{label}: {len(s.names)} bones, {len(cubes)} cubes, {len(animations)} clips; FK checks passed',flush=True)
    return report

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--species',nargs='+',choices=SPECIES,default=list(SPECIES))
    args=parser.parse_args()
    for label in args.species:build(label)
