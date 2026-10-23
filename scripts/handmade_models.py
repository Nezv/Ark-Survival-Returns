"""Hand-designed cuboid creatures on the original ARK rigs: geometry, UV atlas and painted skins.

The procedural pipeline (build_dinosaurs.py, then Creatures/Textures.py) slices every bone's mesh into
principal-axis boxes and paints them in layered shades. A hand-made species replaces that output with a
design module (scripts/handmade_<id>.py) that places every box itself:

- Boxes are placed in the rig's bind pose, in the space of the source geometry: +y up, the animal faces +z,
  its left side is +x, in source units (the importer later scales them to the species' height). Each box
  belongs to a bone and is turned into a GeckoLib cube in that bone's own frame, so the bones, pivots and
  animation tracks stay exactly as imported.
- Every face gets its own rectangle in the atlas, at one texel density in runtime units, with a one-texel
  border repeated around it so no neighbour bleeds in.
- Every texel is painted from where it lies on the body in the bind pose: world-space noise, gradients and
  marks, so neighbouring boxes match across their seams.

Outputs, read by Ark/tools/import_creatures.py: Creatures/<Folder>/textures/<stem>.geo.json, a skin
textures/<variant>/skin.png for each of the species' colour variants (Creatures/variants.json: only colourings
its kind could have; the design module's PALETTES paints each under that name) and the natural base
textures/skin.png. Creatures/Textures.py leaves these species alone (HAND_MADE there).

    python scripts/handmade_models.py [mammoth ... triceratops] [--preview <folder> [--outline]]
"""
from __future__ import annotations

import argparse
import base64
import copy
import importlib
import json
import math
import sys
import uuid
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'Ark/tools'))
sys.path.insert(0, str(Path(__file__).resolve().parent))   # the design modules, handmade_<id>.py
import geckolib_mesh as gm  # noqa: E402

# Every species' colour variants, in order (Creatures/variants.json).
VARIANTS = json.loads((ROOT / 'Creatures' / 'variants.json').read_text(encoding='utf-8'))['species']
DESIGNS = ('mammoth', 'megapithecus', 'sabertooth', 'ankylosaurus', 'tyrannosaurus', 'triceratops', 'direwolf',
           'parasaur', 'velociraptor', 'spinosaurus', 'allosaurus', 'carnotaurus',
           'giganotosaurus', 'ceratosaurus', 'acrocanthosaurus', 'dilophosaur',
           'terrorbird', 'therizinosaurus', 'pegomastax', 'lystrosaurus', 'sarco', 'kaprosuchus', 'deinosuchus', 'titanoboa')
# Creature folders whose art is hand-made here: Creatures/Textures.py leaves them alone, scripts/workflow.py
# rebuilds them after refitting their rigs.
HAND_MADE = {'Mammoth': 'mammoth', 'Megapithecus': 'megapithecus', 'Sabertooth': 'sabertooth',
             'Ankylosaurus': 'ankylosaurus', 'Tyranosaur': 'tyrannosaurus', 'Triceratops': 'triceratops',
             'Direwolf': 'direwolf', 'Parasaur': 'parasaur', 'Velociraptor': 'velociraptor',
             'Spinosaurus': 'spinosaurus', 'Allosaurus': 'allosaurus', 'Carnotaurus': 'carnotaurus',
             'Giganotosaur': 'giganotosaurus', 'Ceratosaurus': 'ceratosaurus',
             'Acrochantosaur': 'acrocanthosaurus', 'Dilophosaur': 'dilophosaur',
             'Terrorbird': 'terrorbird', 'Therezinosaur': 'therizinosaurus', 'Pegomastax': 'pegomastax',
             'Lystrosaurus': 'lystrosaurus', 'Sarco': 'sarco',
             'Kaprosuchus': 'kaprosuchus', 'Deinosuchus': 'deinosuchus', 'Titanoboa': 'titanoboa'}
# GeckoLib's corner names (geckolib_mesh.FACES): B/F low/high x, l/r low/high z, t/b high/low y.
CORNER = {'blb': (-1, -1, -1), 'brb': (-1, -1, 1), 'tlb': (-1, 1, -1), 'trb': (-1, 1, 1),
          'tlf': (1, 1, -1), 'trf': (1, 1, 1), 'blf': (1, -1, -1), 'brf': (1, -1, 1)}
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], float) / 16 + 1 / 32


# ------------------------------------------------------------------------------------ small maths
def unit(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


def rotation(yaw=0.0, pitch=0.0, roll=0.0):
    """A world rotation in degrees: roll about z (+ lifts +x towards +y), then pitch about x (+ lifts the
    front, +z, towards +y), then yaw about y (+ turns the front towards +x)."""
    a, b, c = (math.radians(v) for v in (pitch, yaw, roll))
    rx = np.array([[1, 0, 0], [0, math.cos(a), math.sin(a)], [0, -math.sin(a), math.cos(a)]])
    ry = np.array([[math.cos(b), 0, math.sin(b)], [0, 1, 0], [-math.sin(b), 0, math.cos(b)]])
    rz = np.array([[math.cos(c), -math.sin(c), 0], [math.sin(c), math.cos(c), 0], [0, 0, 1]])
    return ry @ rx @ rz


def euler_zyx(m):
    """(a, b, c) radians with Rz(c) @ Ry(b) @ Rx(a) == m."""
    b = math.asin(max(-1.0, min(1.0, -m[2, 0])))
    if abs(m[2, 0]) < 0.9999995:
        return math.atan2(m[2, 1], m[2, 2]), b, math.atan2(m[1, 0], m[0, 0])
    return 0.0, b, math.atan2(-m[0, 1], m[1, 1])


def swap_side(name):
    for a, b in (('l_', 'r_'), ('r_', 'l_'), ('Lft_', 'Rht_'), ('Rht_', 'Lft_')):
        if name.startswith(a):
            return b + name[len(a):]
    for a, b in (('_L', '_R'), ('_R', '_L')):         # suffixed rigs (the Ceratosaurus: Shoulder_L)
        if name.endswith(a):
            return name[:-len(a)] + b
    return name


# ------------------------------------------------------------------------------------ noise
def _hash(ix, iy, iz, seed):
    h = (ix.astype(np.uint64) * np.uint64(73856093)) ^ (iy.astype(np.uint64) * np.uint64(19349663)) \
        ^ (iz.astype(np.uint64) * np.uint64(83492791)) ^ np.uint64((seed * 2654435761) & 0xFFFFFFFF)
    h ^= h >> np.uint64(13)
    h *= np.uint64(1274126177)
    h ^= h >> np.uint64(16)
    return (h & np.uint64(0xFFFF)).astype(float) / 65535.0


def noise(points, scale, seed=0):
    """Smooth value noise in [0, 1] at world points; scale is a length or one per axis."""
    q = np.asarray(points, float) / np.asarray(scale, float)
    i = np.floor(q).astype(np.int64)
    f = q - i
    w = f * f * (3 - 2 * f)
    out = np.zeros(q.shape[:-1])
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                h = _hash(i[..., 0] + dx, i[..., 1] + dy, i[..., 2] + dz, seed)
                out += h * (w[..., 0] if dx else 1 - w[..., 0]) * (w[..., 1] if dy else 1 - w[..., 1]) \
                    * (w[..., 2] if dz else 1 - w[..., 2])
    return out


def cells(points, scale, seed=0):
    """Blocky noise: one value per world cell of the given size (no smoothing), in [0, 1]."""
    q = np.floor(np.asarray(points, float) / np.asarray(scale, float)).astype(np.int64)
    return _hash(q[..., 0], q[..., 1], q[..., 2], seed)


def fbm(points, scale, seed=0, octaves=3, gain=0.5):
    total, amp, norm = 0.0, 1.0, 0.0
    scale = np.asarray(scale, float)
    for k in range(octaves):
        total = total + amp * noise(points, scale / (2 ** k), seed + 17 * k)
        norm += amp
        amp *= gain
    return total / norm


def hexes(*codes):
    return np.array([[int(c[i:i + 2], 16) for i in (1, 3, 5)] for c in codes], float)


def lift(palette, gain, add=0.0):
    """A palette's ramps brightened (gain on every channel, then add), for the darkest variants."""
    return {key: np.clip(ramp * gain + add, 0, 255) for key, ramp in palette.items()}


def shade(palette, value, x, y, dither=1.0):
    """Quantize value in [0, 1] onto a dark-to-light palette with ordered dithering on the atlas texels."""
    palette = np.asarray(palette, float)
    n = len(palette)
    level = np.clip(value, 0, 1) * (n - 1)
    jitter = (BAYER[y % 4, x % 4] - 0.5) * dither
    index = np.clip(np.floor(level + 0.5 + jitter), 0, n - 1).astype(int)
    return palette[index]


def mix(a, b, t):
    t = np.asarray(t, float)[..., None] if np.ndim(t) else t
    return np.asarray(a, float) * (1 - t) + np.asarray(b, float) * t


def spline(points, steps):
    """A Catmull-Rom curve through points (the ends held), sampled `steps` times per span."""
    p = [np.asarray(v, float) for v in points]
    p = [2 * p[0] - p[1]] + p + [2 * p[-1] - p[-2]]
    out = []
    for k in range(1, len(p) - 2):
        p0, p1, p2, p3 = p[k - 1], p[k], p[k + 1], p[k + 2]
        for i in range(steps):
            t = i / steps
            out.append(0.5 * (2 * p1 + (p2 - p0) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t
                              + (3 * p1 - p0 - 3 * p2 + p3) * t ** 3))
    out.append(p[-2])
    return np.array(out)


def hair_axes(tx, flow=None):
    """(across, down) unit vectors in a face's plane: hair hangs down on the sides, lies along `flow`
    (default backwards, -z) on top and below."""
    n = tx.normal
    if abs(n[1]) < 0.6:
        down = np.array([0.0, -1.0, 0.0]) + n * n[1]
    else:
        down = np.asarray(flow if flow is not None else (0.0, 0.0, -1.0), float)
        down = down - n * (down @ n)
        if np.linalg.norm(down) < 1e-3:          # looking straight along the flow: any direction in the face
            down = np.cross(n, (1.0, 0.0, 0.0) if abs(n[0]) < 0.9 else (0.0, 0.0, 1.0))
    down = down / np.linalg.norm(down)
    return np.cross(n, down), down


def strands(tx, width, layer, seed=0, flow=None):
    """Long hair as pixel art: one strand per texel column with its own tone, strands grouped into locks
    `width` texels wide that hang in layers `layer` texels long, each lock staggered by its own offset.
    Returns per texel (strand tone, position down the layer in [0, 1), lock tone), tones in [0, 1]."""
    across, down = hair_axes(tx, flow)
    a = (tx.points @ across) / tx.texel
    b = (tx.points @ down) / tx.texel
    column = np.floor(a + 1e-6).astype(np.int64)
    lock = np.floor(a / width + 1e-6).astype(np.int64)
    zeros = np.zeros_like(column)
    tone = _hash(column, zeros, np.full(column.shape, 5, np.int64), seed)
    offset = _hash(lock, zeros, np.full(column.shape, 9, np.int64), seed) * layer
    phase = (b + offset) / layer
    row = np.floor(phase).astype(np.int64)
    lock_tone = _hash(lock, row, np.full(column.shape, 2, np.int64), seed + 1)
    # a slow change of tone down every strand
    drift = noise(np.stack([column.astype(float), b / 7.0, np.zeros_like(b)], -1), (1.0, 1.0, 1.0), seed + 2)
    return 0.65 * tone + 0.35 * drift, phase - row, lock_tone


def locks(tx, width, height, seed=0, flow=None):
    """Hair locks on a face, as a staggered brick grid in world space: per texel (across, down) inside its
    lock in [0, 1) and one random value per lock. Locks hang down on the sides; on top they lie along `flow`
    (default backwards, -z); width and height in texels."""
    n = tx.normal
    p = tx.points
    if abs(n[1]) < 0.6:
        down = np.array([0.0, -1.0, 0.0]) - n * (-n[1])
        down = down / np.linalg.norm(down)
    else:
        down = np.asarray(flow if flow is not None else (0.0, 0.0, -1.0), float)
        down = down - n * (down @ n)
        down = down / np.linalg.norm(down)
    across = np.cross(n, down)
    a = (p @ across) / tx.texel / width
    column = np.floor(a)
    offset = _hash(column.astype(np.int64), np.zeros_like(column, np.int64), np.full(column.shape, 7, np.int64), seed)
    b = (p @ down) / tx.texel / height + offset
    row = np.floor(b)
    value = _hash(column.astype(np.int64), row.astype(np.int64), np.full(column.shape, 3, np.int64), seed + 1)
    return a - column, b - row, value


def reference_rotation(degrees):
    """A JAW_REFERENCE value as a BoneSnapshot rotation: degrees about x, or [x, y, z] for rigs whose jaw turns
    about another axis (the Sarco's about y)."""
    value = [degrees, 0.0, 0.0] if np.ndim(degrees) == 0 else list(degrees)
    return np.radians(value) * [-1, -1, 1]


# ------------------------------------------------------------------------------------ rig and boxes
class Rig:
    """The bones of a source geometry in their bind pose (Java model space, source units)."""

    def __init__(self, geo_path, height_blocks, animation_path=None):
        self.path = Path(geo_path)
        self.doc = json.loads(self.path.read_text(encoding='utf-8'))
        self.model = self.doc['minecraft:geometry'][0]
        self.bare = {'bones': [{k: v for k, v in b.items() if k != 'cubes'} for b in self.model['bones']]}
        self.world, _ = gm.bone_matrices(self.bare)
        self.bones = {b['name']: b for b in self.bare['bones']}
        desc = self.model['description']
        self.factor = height_blocks / ((desc['visible_bounds_height'] - 2) / 2)  # runtime units per source unit
        self.animations = json.loads(Path(animation_path).read_text())['animations'] if animation_path else {}
        self.placed = {}  # bone -> the frame its boxes are designed in, when not the bind pose

    def design_as_in(self, bone, clip, time, anchor, reference=None):
        """Design a bone's boxes as it sits against `anchor` at `time` in `clip` (a jaw shut as in the idle,
        when the bind pose has it open): its boxes are placed in bind-pose world space but follow the bone
        from where the clip holds it. `reference` ({bone: degrees}) overrides the clip's rotation of bones, a
        jaw the clip leaves open set shut."""
        pose = gm.clip_pose(self.animations[clip], time)
        for name, degrees in (reference or {}).items():
            pose.setdefault(name, {})['rot'] = reference_rotation(degrees)
        posed, _ = gm.bone_matrices(self.bare, pose)
        self.placed[bone] = self.world[anchor] @ np.linalg.inv(posed[anchor]) @ posed[bone]

    def frame_of(self, bone):
        return self.placed.get(bone, self.world[bone])

    def pivot(self, name):
        p = np.r_[np.array(self.bones[name]['pivot'], float) * [-1, 1, 1], 1.0]
        return (self.world[name] @ p)[:3]

    def frame(self, name):
        return self.world[name][:3, :3]


@dataclass
class Box:
    bone: str
    center: np.ndarray
    size: np.ndarray
    axes: np.ndarray            # columns: the box's x, y and z in world space (a rotation)
    mat: str = 'fur'
    tag: dict = field(default_factory=dict)

    def corners(self):
        return {k: self.center + self.axes @ (np.array(s, float) * self.size / 2) for k, s in CORNER.items()}

    def contains(self, point, margin=1e-3):
        local = self.axes.T @ (np.asarray(point, float) - self.center)
        return bool(np.all(np.abs(local) <= self.size / 2 - margin))


def mirrored(box):
    flip = np.diag([-1.0, 1.0, 1.0])
    axes = flip @ box.axes
    axes[:, 0] *= -1
    tag = dict(box.tag)
    tag['mirror'] = not tag.get('mirror', False)
    return Box(swap_side(box.bone), flip @ box.center, box.size.copy(), axes, box.mat, tag)


class Design:
    """Collects the boxes of one design. Distances in source units, positions in the bind-pose world."""

    def __init__(self, rig):
        self.rig = rig
        self.boxes: list[Box] = []

    def add(self, bone, center, size, axes=None, mat='fur', mirror=False, **tag):
        if bone not in self.rig.bones:
            raise KeyError(f'no bone {bone}')
        size = np.array(size, float)
        if np.any(size <= 0):
            raise ValueError(f'{bone}: box size {size}')
        box = Box(bone, np.array(center, float), size, np.eye(3) if axes is None else np.array(axes, float), mat, tag)
        self.boxes.append(box)
        if mirror:
            self.boxes.append(mirrored(box))
        return box

    def span(self, bone, lo, hi, mat='fur', mirror=False, **tag):
        """A world-aligned box between two corners."""
        lo, hi = np.array(lo, float), np.array(hi, float)
        return self.add(bone, (lo + hi) / 2, np.abs(hi - lo), None, mat, mirror, **tag)

    def turned(self, bone, center, size, yaw=0.0, pitch=0.0, roll=0.0, mat='fur', mirror=False, **tag):
        return self.add(bone, center, size, rotation(yaw, pitch, roll), mat, mirror, **tag)

    def slab(self, bone, back, front, half_width, thickness, mat='fur', x=0.0, top=True, mirror=False, **tag):
        """A box in the side view's slope: `back` and `front` are (y, z) points on its top edge (its bottom
        edge when top is False), `thickness` measured square to that edge, centred on x."""
        (y0, z0), (y1, z1) = back, front
        f = unit((0.0, y1 - y0, z1 - z0))
        u = np.array([0.0, f[2], -f[1]])
        if u[1] < 0:
            u = -u
        mid = np.array([x, (y0 + y1) / 2, (z0 + z1) / 2]) + (-u if top else u) * thickness / 2
        length = math.hypot(y1 - y0, z1 - z0)
        axes = np.column_stack([np.cross(u, f), u, f])
        return self.add(bone, mid, (2 * half_width, thickness, length), axes, mat, mirror, **tag)

    def seg(self, bone, a, b, w, d, mat='fur', ext=(0.0, 0.0), side=(1, 0, 0), mirror=False, **tag):
        """A box whose y axis runs from a to b (ext[0] past a, ext[1] past b), w across along the direction
        nearest `side`, d deep."""
        a, b = np.array(a, float), np.array(b, float)
        y = unit(b - a)
        side = np.asarray(side, float)
        x = side - y * np.dot(side, y)
        if np.linalg.norm(x) < 1e-6:
            x = np.array([0.0, 0.0, 1.0]) - y * y[2]
        x = unit(x)
        z = np.cross(x, y)
        length = np.linalg.norm(b - a) + ext[0] + ext[1]
        center = a - y * ext[0] + y * length / 2
        return self.add(bone, center, (w, length, d), np.column_stack([x, y, z]), mat, mirror, **tag)


# ------------------------------------------------------------------------------------ geometry out
def cube_of(box, rig):
    """The GeckoLib cube (Bedrock file values) for a box, in its bone's frame."""
    m = rig.frame_of(box.bone)
    linear, offset = m[:3, :3], m[:3, 3]
    centre = linear.T @ (box.center - offset)
    local = linear.T @ box.axes
    a, b, c = euler_zyx(local)
    size = box.size
    origin = centre - size / 2
    cube = {'origin': [round(-(origin[0] + size[0]), 5), round(origin[1], 5), round(origin[2], 5)],
            'size': [round(float(v), 5) for v in size],
            'pivot': [round(-centre[0], 5), round(centre[1], 5), round(centre[2], 5)]}
    angles = [-math.degrees(a), -math.degrees(b), math.degrees(c)]
    if any(abs(v) > 1e-4 for v in angles):
        cube['rotation'] = [round(v, 4) for v in angles]
    return cube


@dataclass
class Face:
    box: Box
    index: int                  # the box's position in the design
    key: str
    corners: np.ndarray         # 4x3, GeckoLib's order for the face
    normal: np.ndarray
    width: int                  # texels along U (corner 1 to 0)
    height: int                 # texels along V (corner 1 to 2)
    hidden: bool = False        # inside another box of its bone: shares one dark swatch
    open: bool = False          # left transparent by the design (a hair fringe's open bottom): shares a clear swatch
    rect: tuple = (0, 0, 0, 0)


def faces_of(design, density):
    """Every face of every box with its texel size; density in texels per runtime unit."""
    per_unit = density * design.rig.factor
    out = []
    for index, box in enumerate(design.boxes):
        corners = box.corners()
        scale = box.tag.get('density', 1.0)
        for key, names in gm.FACES.items():
            pts = np.array([corners[n] for n in names])
            normal = box.axes @ np.array(gm.NORMALS[key], float)
            w = max(1, int(round(np.linalg.norm(pts[0] - pts[1]) * per_unit * scale)))
            h = max(1, int(round(np.linalg.norm(pts[2] - pts[1]) * per_unit * scale)))
            out.append(Face(box, index, key, pts, normal, w, h, open=key in box.tag.get('open', ())))
    return out


def mark_hidden(faces, design):
    """A face is hidden when every sample on it lies inside another box of the same bone (rigid together)."""
    by_bone = {}
    for box in design.boxes:
        by_bone.setdefault(box.bone, []).append(box)
    grid = [(s, t) for s in (0.02, 0.5, 0.98) for t in (0.02, 0.5, 0.98)]
    for face in faces:
        if face.box.tag.get('keep') or face.open:
            continue
        c = face.corners
        samples = [c[1] + s * (c[0] - c[1]) + t * (c[2] - c[1]) + face.normal * 0.02 for s, t in grid]
        for other in by_bone[face.box.bone]:
            if other is face.box or other.tag.get('cutout'):
                continue
            if all(other.contains(p, margin=0.0) for p in samples):
                face.hidden = True
                break


def pack(faces, width):
    """Shelf-pack every visible face's rectangle plus a one-texel border; returns the atlas height."""
    visible = sorted((f for f in faces if not (f.hidden or f.open)), key=lambda f: (-(f.height + 2), -(f.width + 2)))
    x = y = shelf = 0
    for face in visible:
        w, h = face.width + 2, face.height + 2
        if w > width:
            raise ValueError(f'face {face.width}x{face.height} wider than the atlas')
        if x + w > width:
            x, y, shelf = 0, y + shelf, 0
        face.rect = (x + 1, y + 1, face.width, face.height)
        x += w
        shelf = max(shelf, h)
    hidden, clear = (1, y + shelf + 1, 2, 2), (5, y + shelf + 1, 2, 2)  # shared swatches under the rectangles
    for face in faces:
        if face.hidden or face.open:
            face.rect = hidden if face.hidden else clear
    return int(math.ceil((y + shelf + 4) / 16) * 16)


def texel_points(face):
    """World points and (s, t) of every texel of a face, row-major (V, U)."""
    u0, v0, w, h = face.rect
    s = (np.arange(w) + 0.5) / w
    t = (np.arange(h) + 0.5) / h
    ss, tt = np.meshgrid(s, t)
    c = face.corners
    pts = c[1] + ss[..., None] * (c[0] - c[1]) + tt[..., None] * (c[2] - c[1])
    xs, ys = np.meshgrid(np.arange(u0, u0 + w), np.arange(v0, v0 + h))
    return pts, ss, tt, xs, ys


# ------------------------------------------------------------------------------------ painting
@dataclass
class Texels:
    """What a painter gets for one face: everything per texel is an (h, w) array, points (h, w, 3)."""
    face: Face
    points: np.ndarray
    s: np.ndarray
    t: np.ndarray
    x: np.ndarray
    y: np.ndarray
    rig: Rig
    density: float

    @property
    def normal(self):
        return self.face.normal

    @property
    def box(self):
        return self.face.box

    @property
    def texel(self):
        """The size of a texel in source units."""
        return 1.0 / (self.density * self.rig.factor * self.box.tag.get('density', 1.0))

    def down(self):
        """How far each texel lies below the face's top edge, in texels (0 at the top row of a side face)."""
        y = self.points[..., 1]
        return (y.max() - y) / self.texel

    def up_from_bottom(self):
        y = self.points[..., 1]
        return (y - y.min()) / self.texel


def paint(design, faces, width, height, painter, variant, density):
    """Run a design's painter over every face; returns an RGBA atlas with bordered rectangles."""
    atlas = np.zeros((height, width, 4), np.uint8)
    for face in faces:
        if face.hidden or face.open:
            u, v, w, h = face.rect
            atlas[v:v + h, u:u + w] = (30, 26, 24, 255) if face.hidden else (0, 0, 0, 0)
            continue
        pts, ss, tt, xs, ys = texel_points(face)
        rgb, alpha = painter(Texels(face, pts, ss, tt, xs, ys, design.rig, density), variant)
        u, v, w, h = face.rect
        block = np.zeros((h, w, 4))
        block[..., :3] = np.clip(np.round(rgb), 0, 255)
        block[..., 3] = 255 if alpha is None else np.where(np.asarray(alpha) > 0.5, 255, 0)
        atlas[v:v + h, u:u + w] = block
        # the border repeats the edge texels, so sampling a hair past the rectangle shows the same colour
        atlas[v - 1, u:u + w] = atlas[v, u:u + w]
        atlas[v + h, u:u + w] = atlas[v + h - 1, u:u + w]
        atlas[v - 1:v + h + 1, u - 1] = atlas[v - 1:v + h + 1, u]
        atlas[v - 1:v + h + 1, u + w] = atlas[v - 1:v + h + 1, u + w - 1]
    return Image.fromarray(atlas, 'RGBA')


# ------------------------------------------------------------------------------------ checks
def coplanar_overlaps(faces, tolerance=0.01):
    """Pairs of visible faces of different boxes that lie in one plane and overlap: they would flicker."""
    found = []
    visible = [f for f in faces if not (f.hidden or f.open)]
    centres = np.array([f.corners.mean(0) for f in visible])
    radius = np.array([np.linalg.norm(f.corners - f.corners.mean(0), axis=1).max() for f in visible])
    for i, a in enumerate(visible):
        near = np.nonzero(np.linalg.norm(centres - centres[i], axis=1) < radius + radius[i])[0]
        for j in near:
            if j <= i:
                continue
            b = visible[j]
            if a.box is b.box or abs(float(a.normal @ b.normal)) < 0.9999:
                continue
            if abs(float(a.normal @ (b.corners[0] - a.corners[0]))) > tolerance:
                continue
            # sample a on a grid and see whether any point falls inside b
            e1, e2 = b.corners[0] - b.corners[1], b.corners[2] - b.corners[1]
            gram = np.array([[e1 @ e1, e1 @ e2], [e1 @ e2, e2 @ e2]])
            inv = np.linalg.inv(gram)
            hit = False
            for s in np.linspace(0.05, 0.95, 7):
                for t in np.linspace(0.05, 0.95, 7):
                    p = a.corners[1] + s * (a.corners[0] - a.corners[1]) + t * (a.corners[2] - a.corners[1])
                    r = p - b.corners[1]
                    k = inv @ np.array([r @ e1, r @ e2])
                    if 0.001 < k[0] < 0.999 and 0.001 < k[1] < 0.999:
                        hit = True
                        break
                if hit:
                    break
            if hit:
                found.append((a, b))
    return found


def separate(design, density, step=0.025, rounds=30):
    """Grow, by step across the shared plane, the later box of every pair of coplanar overlapping faces until none
    is left: for small animals, where the checker's tolerance is large next to the boxes. Cut-out cards are left
    alone."""
    for _ in range(rounds):
        faces = faces_of(design, density)
        mark_hidden(faces, design)
        pairs = coplanar_overlaps(faces)
        if not pairs:
            return
        for a, b in pairs:
            if a.box.tag.get('cutout') or b.box.tag.get('cutout'):     # cards are kept apart by their designs
                continue
            box = b.box if b.index > a.index else a.box
            k = int(np.argmax(np.abs(box.axes.T @ b.normal)))
            box.size[k] += step


# ------------------------------------------------------------------------------------ fans (frills)
def fan_polar(points, centre, sign):
    """Angle (radians: 0 straight out to the side, pi/2 straight up) and radius of points about a fan's centre
    (x, y) in the bind pose's x-y plane, on the side `sign` (+1 left, -1 right)."""
    x = (points[..., 0] - centre[0]) * sign
    y = points[..., 1] - centre[1]
    return np.arctan2(y, x), np.hypot(x, y)


def fan_card(d, bone, a, b, centre, sign, lo, hi, r0, r1, thickness, mat, margin=0.04, **tag):
    """A thin cut-out card in the plane of a fan's rib (along a to b, across square to z), just large enough for
    the band of the fan it carries: angles lo..hi and radii r0..r1 about `centre` in the bind pose's x-y plane, as
    seen from the front. The painter trims it to that band (fan_polar), so the cards of all the ribs tile one
    membrane in the spread pose and fold over each other as the ribs close."""
    a, b = np.asarray(a, float), np.asarray(b, float)
    y = unit(b - a)
    x = unit(np.cross((0.0, 0.0, 1.0), y))
    z = np.cross(x, y)
    angle, radius = np.meshgrid(np.linspace(lo, hi, 17), np.linspace(r0, r1, 9))
    px = centre[0] + sign * radius * np.cos(angle) - a[0]
    py = centre[1] + radius * np.sin(angle) - a[1]
    us = np.linalg.solve(np.array([[y[0], x[0]], [y[1], x[1]]]), np.stack([px.ravel(), py.ravel()]))
    (u0, s0), (u1, s1) = us.min(1) - margin, us.max(1) + margin
    middle = a + y * (u0 + u1) / 2 + x * (s0 + s1) / 2
    return d.add(bone, middle, (s1 - s0, u1 - u0, thickness), np.column_stack([x, y, z]), mat,
                 open=('up', 'down', 'east', 'west'), cutout=True, keep=True, fan=(centre[0], centre[1], sign),
                 lo=lo, hi=hi, r0=r0, r1=r1, **tag)


def fan_side(d, ribs, centre, sign, mat, top=math.pi / 2, bottom=0.25, root=0.7, spine=0.5, thick=(0.03, 0.06),
             **tag):
    """One side's frill as a single membrane: ribs are (bones, points), the points being each bone's pivot and the
    rib's tip past the last one. Each rib carries the sector between the bisectors to its neighbours (the topmost
    reaching `top`, where the other side's begins; the lowest `bottom` radians past its rib), one card per bone
    for the band between its pivot and the next point; the outer card reaches `spine` past the tip for the rim's
    spines. Every card is tagged with the side's profile (rib angles and tip radii) for fan_rim."""
    rays = []
    for bones, points in ribs:
        angle, radius = fan_polar(np.array(points), centre, sign)
        rays.append((float(angle[-1]), bones, points, radius))
    rays.sort(key=lambda r: -r[0])
    angles = [r[0] for r in rays]
    profile = (tuple(angles), tuple(float(r[3][-1]) for r in rays), top, angles[-1] - bottom)
    for n, (angle, bones, points, radius) in enumerate(rays):
        hi = top if n == 0 else (angles[n - 1] + angle) / 2
        lo = profile[3] if n == len(rays) - 1 else (angles[n + 1] + angle) / 2
        for k, bone in enumerate(bones):
            r0 = float(radius[0]) * root if k == 0 else float(radius[k])
            last = k == len(bones) - 1
            r1 = float(radius[-1]) + spine if last else float(radius[k + 1]) + 0.02
            fan_card(d, bone, points[k], points[k + 1], centre, sign, lo, hi, r0, r1, thick[(n + k) % 2], mat,
                     profile=profile, ray=angle, ring=k, rings=len(bones), outer=last, **tag)


def fan_rim(theta, profile, sag=0.14):
    """The membrane's edge (radius) at each angle: through the rib tips, sagging in a scallop between neighbours,
    meeting the other side's mirror at the top and rounding off below the lowest rib."""
    angles, radii, top, bottom = profile
    a = [2 * top - angles[0], *angles, bottom]
    r = [radii[0], *radii, radii[-1] * 0.55]
    a, r = np.array(a[::-1]), np.array(r[::-1])                 # ascending for interp
    base = np.interp(theta, a, r)
    k = np.clip(np.searchsorted(a, theta) - 1, 0, len(a) - 2)
    f = np.clip((theta - a[k]) / (a[k + 1] - a[k]), 0, 1)
    edge = (k == 0)                                             # the rounded end below the lowest rib: no scallop
    return base * (1 - np.where(edge, 0.0, sag) * np.sin(np.pi * f))


# ------------------------------------------------------------------------------------ build
def build(module_name, density=None, width=None):
    """Build one design: returns (rig, design, geo document, faces, {variant: atlas}, base atlas)."""
    module = importlib.import_module(f'handmade_{module_name}')
    folder, stem = module.FOLDER, module.STEM
    rig = Rig(ROOT / 'Creatures' / folder / 'geo' / f'{stem}.geo.json', module.HEIGHT,
              ROOT / 'Creatures' / folder / 'animations' / f'{stem}.animation.json')
    design = Design(rig)
    module.design(design)
    density = density or module.DENSITY
    faces = faces_of(design, density)
    mark_hidden(faces, design)
    width = width or module.ATLAS_WIDTH
    height = pack(faces, width)

    doc = copy.deepcopy(rig.doc)
    model = doc['minecraft:geometry'][0]
    model['description']['texture_width'] = width
    model['description']['texture_height'] = height
    cubes = {}
    for index, box in enumerate(design.boxes):
        cube = cube_of(box, rig)
        cube['uv'] = {}
        cubes.setdefault(box.bone, []).append((index, cube))
    for face in faces:
        u, v, w, h = face.rect
        cube = next(c for i, c in cubes[face.box.bone] if i == face.index)
        cube['uv'][face.key] = {'uv': [u, v], 'uv_size': [w, h]}
    for bone in model['bones']:
        bone.pop('cubes', None)
        if bone['name'] in cubes:
            bone['cubes'] = [c for _, c in cubes[bone['name']]]
    missing = [v for v in VARIANTS[folder] if v not in module.PALETTES]
    if missing:
        raise KeyError(f'{module_name}: no palette for its variants {missing} (Creatures/variants.json)')
    skins = {variant: paint(design, faces, width, height, module.painter, variant, density)
             for variant in ['Natural', *VARIANTS[folder]]}
    return module, rig, design, doc, faces, skins


def verify_faces(doc, faces, rig):
    """Every baked face lands where the design put it (geckolib_mesh bakes as GeckoLib does)."""
    model = doc['minecraft:geometry'][0]
    baked = gm.quads(model)
    # quads() walks bones in order, cubes in order and faces in FACES order: rebuild that order from the design
    order = []
    for bone in model['bones']:
        indices = sorted({f.index for f in faces if f.box.bone == bone['name']})
        for index in indices:
            for key in gm.FACES:
                order.append(next(f for f in faces if f.index == index and f.key == key))
    worst = 0.0
    for (pts, uv, n, name, key), face in zip(baked, order):
        assert key == face.key and name == face.box.bone, (key, face.key, name, face.box.bone)
        expected = face.corners
        if name in rig.placed:  # designed against another pose: in the bind pose it sits where the bone does
            move = rig.world[name] @ np.linalg.inv(rig.placed[name])
            expected = expected @ move[:3, :3].T + move[:3, 3]
        worst = max(worst, float(np.abs(pts - expected).max()))
    return worst


def write(module, doc, skins, design, faces):
    """The painted geometry and skins the importer reads, the Blockbench project and the previews."""
    out = ROOT / 'Creatures' / module.FOLDER
    folder = out / 'textures'
    folder.mkdir(parents=True, exist_ok=True)
    (folder / f'{module.STEM}.geo.json').write_text(json.dumps(doc, indent=1) + '\n', encoding='utf-8')
    width, height = skins['Natural'].size
    report = {'creature': module.FOLDER, 'builder': f'scripts/{module.__name__}.py', 'atlas_size': [width, height],
              'boxes': len(design.boxes), 'faces': len(faces), 'hidden_faces': sum(f.hidden for f in faces),
              'texels_per_runtime_unit': module.DENSITY}
    for variant in VARIANTS[module.FOLDER]:
        (folder / variant).mkdir(exist_ok=True)
        skins[variant].save(folder / variant / 'skin.png')
        (folder / variant / 'skin_report.json').write_text(json.dumps({**report, 'variant': variant}, indent=2) + '\n',
                                                         encoding='utf-8')
    skins['Natural'].save(folder / 'skin.png')
    (folder / 'skin_report.json').write_text(json.dumps({**report, 'variant': 'Natural'}, indent=2) + '\n', encoding='utf-8')
    project = next(out.glob('*_GeckoLib.bbmodel'))
    project.write_text(json.dumps(blockbench(module, doc, folder / 'skin.png'), separators=(',', ':')) + '\n',
                       encoding='utf-8')
    previews(module, doc, skins['Natural'], out / 'previews', len(design.boxes))


def _uid(value):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'ark-survival-returns/handmade/' + value))


def blockbench(module, doc, texture_path):
    """A GeckoLib Blockbench project of the built model (per-face UV, the natural skin, every clip), for viewing
    and editing; the build stays the source. Blockbench keeps up and down face UVs turned half round."""
    label = module.FOLDER
    model = doc['minecraft:geometry'][0]
    groups, outlines, elements, roots = [], {}, [], []
    for bone in model['bones']:
        key = _uid(f'{label}/bone/{bone["name"]}')
        rx, ry, rz = bone.get('rotation', [0, 0, 0])
        px, py, pz = bone['pivot']
        groups.append({'name': bone['name'], 'uuid': key, 'origin': [-px, py, pz], 'rotation': [-rx, -ry, rz],
                       'export': True, 'visibility': True, 'isOpen': False, 'children': []})
        outlines[bone['name']] = {'uuid': key, 'isOpen': False, 'children': []}
    for bone in model['bones']:
        (outlines[bone['parent']]['children'] if 'parent' in bone else roots).append(outlines[bone['name']])
    for bone in model['bones']:
        for k, cube in enumerate(bone.get('cubes', [])):
            key = _uid(f'{label}/cube/{bone["name"]}/{k}')
            (ox, oy, oz), (sx, sy, sz) = cube['origin'], cube['size']
            px, py, pz = cube.get('pivot', [0, 0, 0])
            rx, ry, rz = cube.get('rotation', [0, 0, 0])
            faces = {}
            for face, uv in cube['uv'].items():
                (u, v), (w, h) = uv['uv'], uv['uv_size']
                faces[face] = {'uv': [u + w, v + h, u, v] if face in ('up', 'down') else [u, v, u + w, v + h], 'texture': 0}
            start = [-(ox + sx), oy, oz]
            elements.append({'name': f'{bone["name"]}.{k}', 'type': 'cube', 'uuid': key, 'box_uv': False, 'export': True,
                             'from': start, 'to': [start[0] + sx, oy + sy, oz + sz], 'origin': [-px, py, pz],
                             'rotation': [-rx, -ry, rz], 'color': 0, 'faces': faces})
            outlines[bone['name']]['children'].append(key)
    animations = json.loads((ROOT / 'Creatures' / label / 'animations' / f'{module.STEM}.animation.json').read_text())['animations']
    clips = []
    for name, clip in animations.items():
        animators = {}
        for bone, tracks in clip['bones'].items():
            frames = []
            for channel, track in tracks.items():
                sign = np.array([-1, -1, 1] if channel == 'rotation' else [-1, 1, 1] if channel == 'position' else [1, 1, 1])
                for time, values in track.items():
                    value = np.array(values, float) * sign
                    frames.append({'channel': channel, 'data_points': [dict(zip('xyz', map(float, value)))],
                                   'uuid': _uid(f'{label}/{name}/{bone}/{channel}/{time}'), 'time': float(time),
                                   'interpolation': 'linear', 'color': -1})
            animators[_uid(f'{label}/bone/{bone}')] = {'name': bone, 'type': 'bone', 'keyframes': frames,
                                                       'rotation_global': False, 'quaternion_interpolation': False}
        clips.append({'uuid': _uid(f'{label}/animation/{name}'), 'name': name, 'loop': 'loop' if clip.get('loop') else 'once',
                      'override': True, 'length': clip['animation_length'], 'snapping': 30, 'saved': True,
                      'animators': animators})
    width, height = model['description']['texture_width'], model['description']['texture_height']
    return {'meta': {'format_version': '5.0', 'model_format': 'geckolib_model', 'box_uv': False},
            'name': label, 'model_identifier': module.STEM, 'geckolib_model_type': 'entity',
            'resolution': {'width': width, 'height': height}, 'elements': elements, 'groups': groups, 'outliner': roots,
            'textures': [{'name': texture_path.name, 'id': '0', 'uuid': _uid(f'{label}/texture'), 'internal': True,
                          'width': width, 'height': height, 'uv_width': width, 'uv_height': height,
                          'source': 'data:image/png;base64,' + base64.b64encode(texture_path.read_bytes()).decode()}],
            'animations': clips}


def previews(module, doc, skin, folder, boxes):
    """previews/model.png, side.png and motion.gif as the procedural previews lay them out, drawn with the skin."""
    from PIL import ImageDraw, ImageFont

    def font(size):
        try:
            return ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', size)
        except OSError:
            return ImageFont.load_default()

    model = doc['minecraft:geometry'][0]
    animations = json.loads((ROOT / 'Creatures' / module.FOLDER / 'animations' / f'{module.STEM}.animation.json').read_text())['animations']
    bones = len(model['bones'])
    background = (19, 30, 39)

    def picture(quads, direction, size, title, subtitle):
        pts = np.concatenate([q[0] for q in quads])
        lo, hi = pts.min(0), pts.max(0)
        centre = (lo + hi) / 2
        radius = np.linalg.norm(hi - lo) / 2
        eye = centre + unit(direction) * radius / math.tan(math.radians(13)) * 1.05
        image = gm.render(quads, skin, eye, centre, size=size, fov=26, background=background, ss=2)
        draw = ImageDraw.Draw(image)
        draw.text((38, 23), title, fill='#e9eddf', font=font(32 if size[0] > 900 else 26))
        draw.text((40, 69 if size[0] > 900 else 58), subtitle, fill='#95a9ac', font=font(17 if size[0] > 900 else 15))
        draw.text((40, size[1] - 40), 'ARK skeleton  /  hand-designed cubes and skin  /  original animation tracks',
                  fill='#718c92', font=font(15))
        return image

    folder.mkdir(exist_ok=True)
    rest = gm.quads(model)
    picture(rest, (0.85, 0.32, 0.8), (1100, 780), module.FOLDER,
            f'{bones} bones  |  {boxes} cubes  |  {len(animations)} clips  |  bind pose').save(folder / 'model.png')
    picture(rest, (1.0, 0.08, 0.0), (1100, 780), module.FOLDER, 'Side view').save(folder / 'side.png')
    name = module.PREVIEW_CLIPS[1]
    clip = animations[name]
    frames = [picture(gm.quads(model, gm.clip_pose(clip, float(t))), (0.85, 0.32, 0.8), (760, 550), module.FOLDER, name)
              for t in np.linspace(0, clip['animation_length'], 18, endpoint=False)]
    frames[0].save(folder / 'motion.gif', save_all=True, append_images=frames[1:],
                   duration=max(35, int(clip['animation_length'] * 1000 / len(frames))), loop=0)
    (folder / 'motion_clip.txt').write_text(name + '\n', encoding='utf-8')


def rebuild(name):
    """Build one design and write everything it owns; returns the summary line."""
    module, rig, design, doc, faces, skins = build(name)
    error = verify_faces(doc, faces, rig)
    if error > 1e-3:
        raise AssertionError(f'{name}: baked faces are {error} off the design')
    write(module, doc, skins, design, faces)
    width, height = skins['Natural'].size
    return f'{name}: {len(design.boxes)} boxes, atlas {width}x{height}'


def preview(module, doc, skins, out, clips=(), size=(900, 640)):
    """A sheet of bind-pose views per variant and the named clips (time 0.35 of each) in the first variant."""
    model = doc['minecraft:geometry'][0]
    anim_path = ROOT / 'Creatures' / module.FOLDER / 'animations' / f'{module.STEM}.animation.json'
    anims = json.loads(anim_path.read_text())['animations']
    shots = []
    views = {'three_quarter': (0.85, 0.45, 0.8), 'side': (1.0, 0.12, 0.0), 'front': (0.05, 0.18, 1.0),
             'back': (-0.5, 0.4, -0.9), 'below': (0.6, -0.5, 0.7)}

    def frame(faces, direction):
        pts = np.concatenate([f[0] for f in faces])
        lo, hi = pts.min(0), pts.max(0)
        c = (lo + hi) / 2
        r = np.linalg.norm(hi - lo) / 2
        d = unit(direction)
        return c + d * r / math.tan(math.radians(15)) * 1.02, c

    texture = skins[module.PREVIEW_VARIANT]
    faces = gm.quads(model)
    for name, direction in views.items():
        eye, target = frame(faces, direction)
        shots.append((name, gm.render(faces, texture, eye, target, size=size, fov=30, ss=1)))
    for variant in ['Natural', *VARIANTS[module.FOLDER]]:
        eye, target = frame(faces, views['three_quarter'])
        shots.append((variant, gm.render(faces, skins[variant], eye, target, size=size, fov=30, ss=1)))
    for clip in clips:
        data = anims[clip]
        pose = gm.clip_pose(data, 0.35 * float(data.get('animation_length', 1)))
        posed = gm.quads(model, pose)
        eye, target = frame(posed, views['three_quarter'])
        shots.append((clip, gm.render(posed, texture, eye, target, size=size, fov=30, ss=1)))
    cols = 3
    rows = math.ceil(len(shots) / cols)
    sheet = Image.new('RGB', (size[0] * cols, (size[1] + 22) * rows), (16, 18, 22))
    from PIL import ImageDraw
    draw = ImageDraw.Draw(sheet)
    for k, (label, image) in enumerate(shots):
        x, y = (k % cols) * size[0], (k // cols) * (size[1] + 22)
        sheet.paste(image, (x, y + 22))
        draw.text((x + 8, y + 5), label, fill=(225, 225, 225))
    sheet.save(out)


def skinned(skeleton, rig, pose):
    """The ARK mesh's vertices skinned (linear blend, the mesh's own weights) by the rig in a pose."""
    posed, _ = gm.bone_matrices(rig.bare, pose)
    v = np.c_[skeleton.source_vertices, np.ones(len(skeleton.source_vertices))]
    out, total = np.zeros_like(skeleton.source_vertices), np.zeros(len(v))
    for weight, vertex, bone in skeleton.weights:
        name = skeleton.source_bone_names[bone]
        if name in rig.world:
            out[vertex] += weight * ((posed[name] @ np.linalg.inv(rig.world[name])) @ v[vertex])[:3]
            total[vertex] += weight
    moved = total > 1e-6
    out[moved] /= total[moved, None]
    out[~moved] = skeleton.source_vertices[~moved]
    return out


def outline(module, doc, skin, out, rig=None, size=(900, 680), fov=20.0):
    """The design in its bind pose with the outline of the ARK mesh it is fitted to drawn over it in red, from the
    side, the front, above and three-quarters: where boxes stand out of or fall short of the original silhouette.
    A jaw designed in another frame (the module's JAW_POSE: clip, time and the bones it moves) is shown there, the
    ARK mesh posed alike, its jaw at JAW_REFERENCE degrees where the ARK clip does not shut it."""
    from PIL import ImageDraw, ImageFilter
    from ark_geometry import Skeleton
    skeleton = Skeleton(module.ASSET, ROOT / 'Creatures' / module.FOLDER / 'source')
    vertices, triangles = skeleton.source_vertices, skeleton.triangles
    skip = getattr(module, 'OUTLINE_SKIP', None)  # bones of the mesh left out (the mammoth's second tusk set)
    if skip:
        triangles = triangles[[not any(word in skeleton.names[b] for word in skip) for b in skeleton.face_bone]]
    pose = None
    if rig is not None and getattr(module, 'JAW_POSE', None):
        clip, time, moved = module.JAW_POSE
        pose = {k: v for k, v in gm.clip_pose(rig.animations[clip], time).items() if any(w in k for w in moved)}
        reference = {k: dict(v) for k, v in pose.items()}
        for bone, degrees in getattr(module, 'JAW_REFERENCE', {}).items():
            reference.setdefault(bone, {})['rot'] = reference_rotation(degrees)
        vertices = skinned(skeleton, rig, reference)
    quads = gm.quads(doc['minecraft:geometry'][0], pose)
    shots = []
    for direction, label in (((1, 1e-4, 1e-4), 'side'), ((1e-4, 1e-4, 1), 'front'), ((1e-4, 1, 0.02), 'top'),
                             ((0.85, 0.4, 0.8), 'three-quarter')):
        points = np.concatenate([q[0] for q in quads] + [vertices])
        lo, hi = points.min(0), points.max(0)
        centre = (lo + hi) / 2
        eye = centre + unit(direction) * np.linalg.norm(hi - lo) / 2 / math.tan(math.radians(fov / 2)) * 1.05
        image = np.asarray(gm.render(quads, skin, eye, centre, size=size, fov=fov, ss=1)).copy()
        origin, basis = gm.look_at(eye, centre)
        focal = (size[1] / 2) / math.tan(math.radians(fov) / 2)
        cam = (vertices - origin) @ basis.T
        screen = np.stack([size[0] / 2 + focal * cam[:, 0] / cam[:, 2], size[1] / 2 - focal * cam[:, 1] / cam[:, 2]], 1)
        mask = Image.new('L', size, 0)
        draw = ImageDraw.Draw(mask)
        for t in triangles:
            draw.polygon([tuple(screen[t[0]]), tuple(screen[t[1]]), tuple(screen[t[2]])], fill=255)
        image[np.asarray(mask.filter(ImageFilter.FIND_EDGES)) > 0] = (255, 60, 60)
        shot = Image.fromarray(image)
        ImageDraw.Draw(shot).text((8, 6), label, fill=(240, 240, 240))
        shots.append(shot)
    sheet = Image.new('RGB', (size[0] * 2, size[1] * 2))
    for k, shot in enumerate(shots):
        sheet.paste(shot, ((k % 2) * size[0], (k // 2) * size[1]))
    sheet.save(out)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('designs', nargs='*', default=list(DESIGNS), choices=DESIGNS)
    parser.add_argument('--preview', type=Path, help='folder for preview sheets (nothing is written to Creatures/)')
    parser.add_argument('--clips', nargs='*', default=None)
    parser.add_argument('--outline', action='store_true', help='with --preview: also the ARK mesh outline sheet')
    args = parser.parse_args()
    for name in args.designs:
        module, rig, design, doc, faces, skins = build(name)
        error = verify_faces(doc, faces, rig)
        flicker = coplanar_overlaps(faces)
        width, height = skins['Natural'].size
        print(f'{name}: {len(design.boxes)} boxes, {len(faces)} faces ({sum(f.hidden for f in faces)} hidden), '
              f'atlas {width}x{height}, bake error {error:.2e}, coplanar overlaps {len(flicker)}')
        for a, b in flicker[:12]:
            print(f'   coplanar: {a.box.bone}/{a.box.mat}/{a.key} and {b.box.bone}/{b.box.mat}/{b.key}')
        if args.preview:
            args.preview.mkdir(parents=True, exist_ok=True)
            clips = module.PREVIEW_CLIPS if args.clips is None else args.clips
            preview(module, doc, skins, args.preview / f'{name}.png', clips)
            skins[module.PREVIEW_VARIANT].save(args.preview / f'{name}_atlas.png')
            if args.outline:
                outline(module, doc, skins[module.PREVIEW_VARIANT], args.preview / f'{name}_outline.png', rig)
        else:
            write(module, doc, skins, design, faces)


if __name__ == '__main__':
    main()
