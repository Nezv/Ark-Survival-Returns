"""ActorX/glTF readers and coordinate conversion shared by the offline build."""
from pathlib import Path
import json, struct, warnings
import numpy as np
from scipy.spatial.transform import Rotation as R
from model_catalog import NATIVE_ASSETS

ROOT = Path(__file__).resolve().parents[1]

def chunks(path):
    data = path.read_bytes()
    offset, result = 0, {}
    while offset < len(data):
        name, _, size, count = struct.unpack_from('<20siii', data, offset)
        offset += 32
        assert size >= 0 and count >= 0 and offset + size*count <= len(data), path
        result[name.rstrip(b'\0').decode()] = (size, count, data[offset:offset+size*count])
        offset += size*count
    assert offset == len(data), path
    return result

def bone_names(chunk):
    size, count, data = chunk
    assert size == 120
    return [data[i*size:i*size+64].split(b'\0')[0].decode() for i in range(count)]

class Skeleton:
    def __init__(self, asset, source=None):
        self.asset = asset
        self.folder = source / 'actorx' if source else ROOT / f'.work/exports/actorx/PrimalEarth/Dinos/{asset}'
        gltf = source / f'gltf/{asset}.gltf' if source else ROOT / f'.work/exports/gltf/PrimalEarth/Dinos/{asset}/{asset}.gltf'
        doc = json.loads(gltf.read_text())
        self.nodes = doc['nodes'][1:]
        self.names = [node['name'] for node in self.nodes]
        self.index = {name: i for i, name in enumerate(self.names)}
        self.parents = [-1] * len(self.nodes)
        self.children = [[] for _ in self.nodes]
        for i, node in enumerate(self.nodes):
            for c in node.get('children', []):
                self.parents[c-1] = i
                self.children[i].append(c-1)
        self.t = np.array([node.get('translation', [0,0,0]) for node in self.nodes]) * 10
        self.r = R.from_quat([node.get('rotation', [0,0,0,1]) for node in self.nodes])
        self.wp, world_r = [], []
        for i, p in enumerate(self.parents):
            assert p < i
            self.wp.append(self.t[i] if p < 0 else self.wp[p] + world_r[p].apply(self.t[i]))
            world_r.append(self.r[i] if p < 0 else world_r[p]*self.r[i])
        self.wp = np.array(self.wp)
        self.wr = R.from_quat([r.as_quat() for r in world_r])
        psk = chunks(self.folder / f'{asset}.psk')
        # Use full-precision ActorX bind transforms; glTF text rounds to six digits.
        raw = psk['REFSKELT'][2]
        self.t = np.array([struct.unpack_from('<3f', raw, i*120+92) for i in range(len(self.names))])[:,[0,2,1]] * [.1,.1,-.1]
        q = np.array([struct.unpack_from('<4f', raw, i*120+76) for i in range(len(self.names))])[:,[0,2,1,3]] * [1,1,-1,-1]
        q[0,:3] *= -1
        self.r = R.from_quat(q)
        self.wp, world_r = [], []
        for i, p in enumerate(self.parents):
            self.wp.append(self.t[i] if p < 0 else self.wp[p] + world_r[p].apply(self.t[i]))
            world_r.append(self.r[i] if p < 0 else world_r[p]*self.r[i])
        self.wp = np.array(self.wp)
        self.wr = R.from_quat([r.as_quat() for r in world_r])
        self.source_vertices = np.frombuffer(psk['PNTS0000'][2], '<f4').reshape(-1,3)[:,[0,2,1]] * [.1,.1,-.1]
        self.source_bone_names = bone_names(psk['REFSKELT'])
        assert self.source_bone_names == self.names, 'ActorX/glTF bone order mismatch'
        assert all(self.parents[i] == (struct.unpack_from('<i', raw, i*120+72)[0] if i else -1) for i in range(len(self.names)))
        self.material_names = [psk['MATT0000'][2][i*88:i*88+64].split(b'\0')[0].decode() for i in range(psk['MATT0000'][1])]
        wedges = np.frombuffer(psk['VTXW0000'][2], dtype=[('vertex','<u4'),('uv','<f4',2),('material','u1'),('pad','u1',3)])
        self.material = np.zeros(len(self.source_vertices), dtype=int)
        self.material[wedges['vertex']] = wedges['material']
        self.weights = np.frombuffer(psk['RAWWEIGHTS'][2], dtype=[('weight','<f4'),('vertex','<i4'),('bone','<i4')])
        self.dominant = np.full(len(self.source_vertices), -1, dtype=int)
        maximum = np.zeros(len(self.source_vertices))
        for weight, vertex, bone in self.weights:
            if weight > maximum[vertex]:
                self.dominant[vertex] = self.index[self.source_bone_names[bone]]
                maximum[vertex] = weight
        self.faces = psk.get('FACE0000', psk.get('FACE3200'))[1]
        face_chunk = psk.get('FACE0000', psk.get('FACE3200'))
        face_dtype = np.dtype([('wedge','<u2' if face_chunk[0] == 12 else '<u4',3),('mat','u1'),('aux','u1'),('smooth','<u4')])
        faces = np.frombuffer(face_chunk[2],dtype=face_dtype)
        self.triangles = wedges['vertex'][faces['wedge']]
        self.face_material = faces['mat'].astype(int)
        weights = np.zeros((len(self.source_vertices),len(self.names)))
        for weight, vertex, bone in self.weights:
            weights[vertex,self.index[self.source_bone_names[bone]]] += weight
        self.face_bone = weights[self.triangles].sum(axis=1).argmax(axis=1)
        if asset in NATIVE_ASSETS:
            from mesh_detail import extend_source
            extend_source(self)
        self.native_bind_axes = asset in NATIVE_ASSETS | {'CeratosaurusAA_Mesh','Acro_Mesh'}
        self.model_pivots = self.wp.copy()
        self.model_rotations = np.zeros((len(self.names),3))
        if self.native_bind_axes:
            # Native local axes preserve anisotropic animated scale without
            # inserting helper bones or changing the source parent hierarchy.
            for i,p in enumerate(self.parents):
                self.model_pivots[i] = self.t[i] + (self.model_pivots[p] if p>=0 else 0)
            with warnings.catch_warnings():
                warnings.simplefilter('ignore',UserWarning)
                self.model_rotations = self.r.as_euler('xyz',degrees=True)

    def surface(self, bone, material):
        # Include complete triangles at influence boundaries. Using only vertex
        # groups leaves gaps between sparsely sampled tail/neck rings.
        indices = self.triangles[(self.face_bone == bone)&(self.face_material == material)].ravel()
        original = np.flatnonzero((self.dominant == bone)&(self.material == material))
        return self.source_vertices[np.unique(np.concatenate([indices,original]))]

    def influenced(self, i):
        return self.source_vertices[self.dominant == i]

    def info(self):
        return [{'name': name, 'parent': self.names[self.parents[i]] if self.parents[i]>=0 else None,
                 'local_translation_bb': self.t[i].tolist(), 'local_rotation_xyzw': self.r[i].as_quat().tolist(),
                 'world_origin_bb': self.wp[i].tolist(), 'world_rotation_xyzw': self.wr[i].as_quat().tolist()}
                for i, name in enumerate(self.names)]

if __name__ == '__main__':
    from extract_dinosaurs import SPECIES
    for label, asset in SPECIES.items():
        skeleton = Skeleton(asset)
        print('\n'+label, len(skeleton.names), 'bones; bounds', np.round(skeleton.source_vertices.min(0),2), np.round(skeleton.source_vertices.max(0),2))
        for i, name in enumerate(skeleton.names):
            print(i, name, skeleton.parents[i], np.round(skeleton.wp[i],2), len(skeleton.influenced(i)))
