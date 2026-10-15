"""A GeckoLib geo.json as GeckoLib 5.5.2 bakes and draws it, for offline renders and for reading a model's skin.

quads() follows GeometryCube.bake, VertexSet and GeometryQuadUvs (read by CFR decompile): the cube origin with
its x mirrored, the vertex order of every face, the per-face UVs with u running from uv + uv_size back to uv.
Bones pose the way RenderUtil.prepMatrixForBone does (translation, pivot, Z then Y then X, scale, pivot back).
render() samples textures nearest-texel with the entity cutout's alpha test, draws both sides of every face
(entityCutoutNoCull) and lights them with the level's two entity lights. Everything is in Java model units
(1/16 block, x the Bedrock x negated).
"""
import json
import math

import numpy as np
from PIL import Image

L0 = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])
L1 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])
NORMALS = {'west': (-1, 0, 0), 'east': (1, 0, 0), 'north': (0, 0, -1), 'south': (0, 0, 1), 'up': (0, 1, 0), 'down': (0, -1, 0)}
# VertexSet: B/F are the low/high x, L/R the low/high z, t/b the high/low y.
FACES = {'west': ('trb', 'tlb', 'blb', 'brb'), 'east': ('tlf', 'trf', 'brf', 'blf'), 'north': ('tlb', 'tlf', 'blf', 'blb'),
         'south': ('trf', 'trb', 'brb', 'brf'), 'up': ('trb', 'trf', 'tlf', 'tlb'), 'down': ('blb', 'blf', 'brf', 'brb')}
AXIS = {'west': 0, 'east': 0, 'up': 1, 'down': 1, 'north': 2, 'south': 2}


def rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def affine(linear=None, offset=(0, 0, 0)):
    m = np.eye(4)
    if linear is not None:
        m[:3, :3] = linear
    m[:3, 3] = offset
    return m


def load(path):
    return json.loads(open(path, encoding='utf-8').read())['minecraft:geometry'][0]


# ------------------------------------------------------------------------------------ animation
def _value(entry):
    if isinstance(entry, dict):
        entry = entry.get('post', entry.get('vector', entry.get('pre')))
        if isinstance(entry, dict):
            entry = entry.get('vector')
    if isinstance(entry, (int, float)):
        return np.array([entry] * 3, float)
    return np.array(entry, float)


def sample(track, t):
    """A channel at time t, linear between keys (GeckoLib's default)."""
    if track is None:
        return None
    if not isinstance(track, dict) or any(k in track for k in ('post', 'vector', 'pre')):
        return _value(track)
    keys = sorted(track, key=float)
    times = np.array([float(k) for k in keys])
    values = np.array([_value(track[k]) for k in keys])
    return np.array([np.interp(t, times, values[:, i]) for i in range(3)])


def clip_pose(clip, t=0.0):
    """A clip's bones at time t as BoneSnapshot values: radians with x and y negated, positions as written."""
    pose = {}
    for name, tracks in clip.get('bones', {}).items():
        p = {}
        r = sample(tracks.get('rotation'), t)
        if r is not None:
            p['rot'] = np.radians(r) * [-1, -1, 1]
        q = sample(tracks.get('position'), t)
        if q is not None:
            p['pos'] = q
        s = sample(tracks.get('scale'), t)
        if s is not None:
            p['scale'] = s
        pose[name] = p
    return pose


def merge(pose, extra):
    """A pose with more snapshot values on top: rotations add, the rest replace."""
    out = {name: dict(values) for name, values in pose.items()}
    for name, values in extra.items():
        target = out.setdefault(name, {})
        for key, value in values.items():
            target[key] = np.asarray(target[key]) + np.asarray(value) if key == 'rot' and 'rot' in target else value
    return out


# ------------------------------------------------------------------------------------ bones and quads
def bone_matrices(model, pose=None):
    """Model-space 4x4 of every bone, and the bones not drawn (hidden, or under a bone hiding its children)."""
    pose = pose or {}
    bones = model['bones']
    index = {b['name']: i for i, b in enumerate(bones)}
    world, hidden = {}, set()

    def compute(name):
        if name in world:
            return world[name]
        b = bones[index[name]]
        parent = b.get('parent')
        base = compute(parent) if parent in index else np.eye(4)
        p = pose.get(name, {})
        pivot = np.array(b['pivot'], float) * [-1, 1, 1]
        r = np.radians(np.array(b.get('rotation', [0, 0, 0]), float)) * [-1, -1, 1] + np.array(p.get('rot', (0, 0, 0)), float)
        t = np.array(p.get('pos', (0, 0, 0)), float) * [-1, 1, 1]
        s = np.array(p.get('scale', (1, 1, 1)), float)
        world[name] = (base @ affine(offset=t) @ affine(offset=pivot) @ affine(rz(r[2]) @ ry(r[1]) @ rx(r[0]))
                       @ affine(np.diag(s)) @ affine(offset=-pivot))
        return world[name]

    def children_hidden(name):
        parent = bones[index[name]].get('parent')
        return parent in index and (pose.get(parent, {}).get('hide_children') or children_hidden(parent))

    for b in bones:
        compute(b['name'])
        if pose.get(b['name'], {}).get('hide') or children_hidden(b['name']):
            hidden.add(b['name'])
    return world, hidden


def quads(model, pose=None, only=None, skip=lambda name: False):
    """Every baked face: (4x3 corners, 4x2 uv in texels, unit normal, bone name, face key)."""
    world, hidden = bone_matrices(model, pose)
    out = []
    for b in model['bones']:
        name = b['name']
        if name in hidden or (only is not None and name not in only) or skip(name):
            continue
        m = world[name]
        for cube in b.get('cubes', []):
            size = np.array(cube['size'], float)
            o = np.array(cube.get('origin', [0, 0, 0]), float)
            origin = np.array([-(o[0] + size[0]), o[1], o[2]])
            inflate = float(cube.get('inflate', b.get('inflate', 0)))
            (x0, y0, z0), (x1, y1, z1) = origin - inflate, origin + size + inflate
            v = {'blb': (x0, y0, z0), 'brb': (x0, y0, z1), 'tlb': (x0, y1, z0), 'trb': (x0, y1, z1),
                 'tlf': (x1, y1, z0), 'trf': (x1, y1, z1), 'blf': (x1, y0, z0), 'brf': (x1, y0, z1)}
            r = np.radians(np.array(cube.get('rotation', [0, 0, 0]), float)) * [-1, -1, 1]
            p = np.array(cube.get('pivot', [0, 0, 0]), float) * [-1, 1, 1]
            full = m @ affine(offset=p) @ affine(rz(r[2]) @ ry(r[1]) @ rx(r[0])) @ affine(offset=-p)
            uvs = cube.get('uv', {})
            if not isinstance(uvs, dict) or cube.get('mirror') or b.get('mirror'):
                raise ValueError(f'{name}: box UV and mirrored cubes are not read here')
            for key, corners in FACES.items():
                face = uvs.get(key)
                axis = AXIS[key]
                if face is None or any(size[k] == 0 and axis != k for k in range(3)):
                    continue
                (u, vv), (us, vs) = face['uv'], face['uv_size']
                uv = np.array([(u + us, vv), (u, vv), (u, vv + vs), (u + us, vv + vs)], float)
                pts = np.array([v[c] for c in corners], float) @ full[:3, :3].T + full[:3, 3]
                n = full[:3, :3] @ np.array(NORMALS[key], float)
                n = n / max(np.linalg.norm(n), 1e-9)
                out.append((pts, uv, n, name, key))
    return out


def hit(quad, start, direction):
    """Where a ray meets a quad (a parallelogram, as box faces are): (distance, uv in texels) or None."""
    pts, uv = quad[0], quad[1]
    e1, e2 = pts[1] - pts[0], pts[3] - pts[0]
    normal = np.cross(e1, e2)
    denom = normal @ direction
    if abs(denom) < 1e-12:
        return None
    t = normal @ (pts[0] - start) / denom
    if t < 0:
        return None
    rel = start + t * direction - pts[0]
    gram = np.array([[e1 @ e1, e1 @ e2], [e1 @ e2, e2 @ e2]])
    if abs(np.linalg.det(gram)) < 1e-12:
        return None
    s, w = np.linalg.solve(gram, [rel @ e1, rel @ e2])
    if not (-1e-6 <= s <= 1 + 1e-6 and -1e-6 <= w <= 1 + 1e-6):
        return None
    return t, uv[0] + s * (uv[1] - uv[0]) + w * (uv[3] - uv[0])


# ------------------------------------------------------------------------------------ render
def look_at(eye, target, up=(0, 1, 0)):
    eye, target = np.asarray(eye, float), np.asarray(target, float)
    forward = (target - eye) / np.linalg.norm(target - eye)
    right = np.cross(forward, up)
    if np.linalg.norm(right) < 1e-6:
        right = np.cross(forward, (0, 0, 1))
    right /= np.linalg.norm(right)
    return eye, np.stack([right, np.cross(right, forward), forward])


def render(faces, texture, eye, target, size=(800, 600), fov=30.0, glow=None, background=(28, 32, 38), ss=2, tint=None):
    """A perspective picture of quads(); texture and glow (an eyes-layer sheet, added full bright) are RGBA images."""
    width, height = size[0] * ss, size[1] * ss
    tex = np.asarray(texture.convert('RGBA'), np.float32)
    shine = np.asarray(glow.convert('RGBA'), np.float32) if glow is not None else None
    origin, basis = look_at(eye, target)
    focal = (height / 2) / math.tan(math.radians(fov) / 2)
    zbuf = np.full((height, width), np.inf, np.float32)
    color = np.zeros((height, width, 3), np.float32)
    color[:] = background
    added = np.zeros((height, width, 3), np.float32)
    for pts, uv, n, _, _ in faces:
        cam = (pts - origin) @ basis.T
        if np.any(cam[:, 2] < 0.5):
            continue
        sx = width / 2 + focal * cam[:, 0] / cam[:, 2]
        sy = height / 2 - focal * cam[:, 1] / cam[:, 2]
        light = min(1.0, 0.4 + 0.6 * (max(0.0, float(n @ L0)) + max(0.0, float(n @ L1))))
        for tri in ((0, 1, 2), (0, 2, 3)):
            _raster(zbuf, color, added, tex, shine, sx[list(tri)], sy[list(tri)], cam[list(tri), 2], uv[list(tri)], light)
    if tint is not None:
        color *= np.asarray(tint, np.float32) / 255
    image = Image.fromarray(np.clip(color + added, 0, 255).astype(np.uint8))
    return image.resize(size, Image.Resampling.LANCZOS) if ss > 1 else image


def _raster(zbuf, color, added, tex, shine, sx, sy, z, uv, light):
    height, width = zbuf.shape
    x0, x1 = max(0, int(math.floor(sx.min()))), min(width - 1, int(math.ceil(sx.max())))
    y0, y1 = max(0, int(math.floor(sy.min()))), min(height - 1, int(math.ceil(sy.max())))
    if x0 > x1 or y0 > y1:
        return
    area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0])
    if abs(area) < 1e-9:
        return
    gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
    w0 = ((sx[1] - gx) * (sy[2] - gy) - (sx[2] - gx) * (sy[1] - gy)) / area
    w1 = ((sx[2] - gx) * (sy[0] - gy) - (sx[0] - gx) * (sy[2] - gy)) / area
    w2 = 1 - w0 - w1
    inside = (w0 >= -1e-7) & (w1 >= -1e-7) & (w2 >= -1e-7)
    if not inside.any():
        return
    inverse = w0 / z[0] + w1 / z[1] + w2 / z[2]
    depth = 1 / np.where(inside, inverse, 1)
    u = (w0 * uv[0, 0] / z[0] + w1 * uv[1, 0] / z[1] + w2 * uv[2, 0] / z[2]) * depth
    v = (w0 * uv[0, 1] / z[0] + w1 * uv[1, 1] / z[1] + w2 * uv[2, 1] / z[2]) * depth
    th, tw = tex.shape[:2]
    ui = np.clip(np.floor(u).astype(int), 0, tw - 1)
    vi = np.clip(np.floor(v).astype(int), 0, th - 1)
    texel = tex[vi, ui]
    zb = zbuf[y0:y1 + 1, x0:x1 + 1]
    drawn = inside & (texel[..., 3] >= 0.1 * 255) & (depth < zb - 1e-4)
    if not drawn.any():
        return
    zb[drawn] = depth[drawn]
    color[y0:y1 + 1, x0:x1 + 1][drawn] = texel[..., :3][drawn] * light
    block = added[y0:y1 + 1, x0:x1 + 1]
    block[drawn] = 0
    if shine is not None:
        glow = shine[vi, ui]
        block[drawn] = (glow[..., :3] * glow[..., 3:4] / 255)[drawn]
