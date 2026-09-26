"""Worn-accessory art kit: geometry on the player skeleton, procedural materials, UV packing, previews.

An accessory is a set of bones hung on the vanilla humanoid parts (head, body, arms, legs). Cubes use the
box UV layout of ModelPart.Cube, so the Java side (client/accessory/WornModels) bakes them exactly as a
vanilla model: they follow the player's pose, sneak, swim and first-person arm like armour does.

build_accessories.py describes every item with this kit and writes, per accessory:
  assets/arksurvivalreturns/worn/<id>.json               geometry (bones, cubes, UVs, fit, side, animation)
  assets/arksurvivalreturns/textures/entity/accessory/<id>.png   the painted texture
and renders previews on Steve (wide arms) and Alex (slim arms) with the same software rasteriser the
showcase uses for blocks. Coordinates are model space: +x is the wearer's left, +y is down, -z is the front.
"""
import io
import json
import math
import random
import zipfile
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
from PIL import Image

ARK = Path(__file__).resolve().parents[1]
JAR = ARK / 'build/moddev/artifacts/minecraft-patched-26.1.2.109-sources.jar'

PART_POSE = {'head': (0, 0, 0), 'body': (0, 0, 0), 'right_arm': (-5, 2, 0), 'left_arm': (5, 2, 0),
             'right_leg': (-1.9, 12, 0), 'left_leg': (1.9, 12, 0)}
PART_BOX = {'head': ((-4, -8, -4), (8, 8, 8)), 'body': ((-4, 0, -2), (8, 12, 4)),
            'right_arm': ((-3, -2, -2), (4, 12, 4)), 'left_arm': ((-1, -2, -2), (4, 12, 4)),
            'right_leg': ((-2, 0, -2), (4, 12, 4)), 'left_leg': ((-2, 0, -2), (4, 12, 4))}
SLIM_BOX = {'right_arm': ((-2, -2, -2), (3, 12, 4)), 'left_arm': ((-1, -2, -2), (3, 12, 4))}
FACES = ('down', 'up', 'west', 'north', 'east', 'south')   # model Direction names; 'down' is the visual top
ALL = frozenset(FACES)


# ---------------------------------------------------------------------------------------- geometry

@dataclass(eq=False)
class Cube:
    origin: tuple
    size: tuple
    mat: object
    grow: tuple = (0.0, 0.0, 0.0)
    faces: frozenset = ALL
    mirror: bool = False
    source: 'Cube' = None          # a mirrored copy shares its source's UV rectangle
    uv: tuple = None

    def owner(self):
        return self.source or self


@dataclass(eq=False)
class Bone:
    name: str
    part: str
    pivot: tuple = (0.0, 0.0, 0.0)
    rot: tuple = (0.0, 0.0, 0.0)        # degrees, applied like ModelPart: Z, then Y, then X
    fit: str = 'none'                   # armour piece that pushes this bone outward: helmet|chest|legs|boots
    side: str = None                    # right|left: only drawn for that slot side
    arms: str = None                    # wide|slim: only drawn on that arm model
    anim: str = None                    # sway|cape|wing
    open: tuple = None                  # wing rotation (degrees) when fully spread
    cubes: list = field(default_factory=list)

    def box(self, origin, size, mat, grow=0.0, faces=ALL):
        g = (grow, grow, grow) if isinstance(grow, (int, float)) else tuple(grow)
        assert all(float(s).is_integer() for s in size), f'{self.name}: cube sizes must be whole texels'
        cube = Cube(tuple(float(o) for o in origin), tuple(int(s) for s in size), mat, tuple(float(v) for v in g),
                    frozenset(faces))
        self.cubes.append(cube)
        return cube


class Worn:
    """One accessory's worn model."""

    def __init__(self, ident, width=64, height=32):
        self.id = ident
        self.width, self.height = width, height
        self.bones = []

    def bone(self, part, name=None, **kw):
        b = Bone(name or f'{part}_{len(self.bones)}', part, **kw)
        self.bones.append(b)
        return b

    # -------------------------------------------------------------- symmetric and sided helpers

    def mirror(self, bone, part=None, name=None, side=None):
        """Copy of a bone mirrored across x (right limb -> left limb, or one half of the head/body)."""
        m = Bone(name or bone.name + '_mirror', part or bone.part, (-bone.pivot[0], bone.pivot[1], bone.pivot[2]),
                 (bone.rot[0], -bone.rot[1], -bone.rot[2]), bone.fit, side if side is not None else bone.side,
                 bone.arms, bone.anim, None if bone.open is None else (bone.open[0], -bone.open[1], -bone.open[2]))
        for c in bone.cubes:
            x, y, z = c.origin
            m.cubes.append(Cube((-(x + c.size[0]), y, z), c.size, c.mat, c.grow, _mirror_faces(c.faces), True,
                                c.owner()))
        self.bones.append(m)
        return m

    def arm(self, build, sides=('right', 'left'), sided=True):
        """Builds arm geometry once (right arm, wide coordinates) and derives slim and left copies.

        build(bone) adds cubes to a fresh right-arm bone; sided items tag each copy with its slot side so a
        bracelet in the second slot is drawn on the left wrist only.
        """
        wide = self.bone('right_arm', f'right_arm_wide_{len(self.bones)}', arms='wide')
        build(wide)
        slim = self._slim(wide)
        out = []
        for bone in (wide, slim):
            if 'right' in sides:
                bone.side = 'right' if sided else None
                out.append(bone)
            if 'left' in sides:
                out.append(self.mirror(bone, 'left_arm', bone.name.replace('right', 'left'), 'left' if sided else None))
        if 'right' not in sides:
            self.bones.remove(wide)
            self.bones.remove(slim)
        return out

    def legs(self, build, fit='none'):
        right = self.bone('right_leg', f'right_leg_{len(self.bones)}', fit=fit)
        build(right)
        self.mirror(right, 'left_leg', right.name.replace('right', 'left'))
        return right

    def _slim(self, wide):
        """Alex arms are 3 px wide: the outer face moves 1 px in, the inner face stays put."""
        def f(x):
            if x <= -3:
                return x + 1
            if x >= 1:
                return x
            return 1 - (1 - x) * 0.75
        slim = Bone(wide.name.replace('wide', 'slim'), wide.part, wide.pivot, wide.rot, wide.fit, wide.side, 'slim',
                    wide.anim, wide.open)
        if any(wide.rot):
            px = wide.pivot[0]
            slim.pivot = (f(px), wide.pivot[1], wide.pivot[2])
            for c in wide.cubes:
                slim.cubes.append(Cube(c.origin, c.size, c.mat, c.grow, c.faces, c.mirror, c.owner()))
        else:
            for c in wide.cubes:
                x0 = wide.pivot[0] + c.origin[0] - c.grow[0]
                x1 = wide.pivot[0] + c.origin[0] + c.size[0] + c.grow[0]
                n0, n1 = f(x0), f(x1)
                visual = n1 - n0
                grow_x = (visual - c.size[0]) / 2
                centre = (n0 + n1) / 2 - wide.pivot[0]
                origin = (centre - c.size[0] / 2, c.origin[1], c.origin[2])
                slim.cubes.append(Cube(origin, c.size, c.mat, (grow_x, c.grow[1], c.grow[2]), c.faces, c.mirror,
                                       c.owner()))
        self.bones.append(slim)
        return slim

    # ------------------------------------------------------------------------------ packing

    def pack(self):
        owners = []
        for b in self.bones:
            for c in b.cubes:
                if c.source is None and c not in owners:
                    owners.append(c)
        rects = sorted(owners, key=lambda c: -(c.size[2] + c.size[1]))
        for width, height in ((self.width, self.height), (64, 64), (128, 64), (128, 128)):
            placed = _shelf(rects, width, height)
            if placed is not None:
                self.width, self.height = width, height
                for c, uv in placed.items():
                    c.uv = uv
                break
        else:
            raise ValueError(f'{self.id}: UVs do not fit in 128x128')
        for b in self.bones:
            for c in b.cubes:
                if c.source is not None:
                    c.uv = c.source.uv

    # ------------------------------------------------------------------------------- output

    def paint(self, seed=0):
        tex = np.zeros((self.height, self.width, 4), dtype=np.uint8)
        glow = np.zeros_like(tex)
        done = set()
        index = 0
        for b in self.bones:
            for c in b.cubes:
                if c.source is not None or id(c) in done:
                    continue
                done.add(id(c))
                index += 1
                paint_cube(tex, c, b, random.Random(f'{self.id}:{seed}:{index}'), glow)
        self.glow = Image.fromarray(glow, 'RGBA') if glow[..., 3].any() else None
        return Image.fromarray(tex, 'RGBA')

    def to_json(self):
        bones = []
        for b in self.bones:
            data = {'name': b.name, 'part': b.part, 'pivot': _r(b.pivot), 'rotation': _r(b.rot)}
            if b.fit != 'none':
                data['fit'] = b.fit
            for key in ('side', 'arms', 'anim'):
                if getattr(b, key):
                    data[key] = getattr(b, key)
            if b.open is not None:
                data['open'] = _r(b.open)
            data['cubes'] = []
            for c in b.cubes:
                cube = {'origin': _r(c.origin), 'size': list(c.size), 'uv': list(c.uv)}
                if any(c.grow):
                    cube['grow'] = _r(c.grow)
                if c.mirror:
                    cube['mirror'] = True
                if c.faces != ALL:
                    cube['faces'] = sorted(c.faces)
                data['cubes'].append(cube)
            bones.append(data)
        return {'texture_size': [self.width, self.height], 'bones': bones}


def _mirror_faces(faces):
    swap = {'west': 'east', 'east': 'west'}
    return frozenset(swap.get(f, f) for f in faces)


def _r(values):
    return [round(float(v), 4) for v in values]


def _shelf(cubes, width, height):
    x = y = row = 0
    placed = {}
    for c in cubes:
        w, h, d = c.size
        rw, rh = max(1, 2 * (d + w)), max(1, d + h)
        if rw > width:
            return None
        if x + rw > width:
            x, y, row = 0, y + row, 0
        if y + rh > height:
            return None
        placed[c] = (x, y)
        x += rw
        row = max(row, rh)
    return placed


def face_rect(c, face):
    u, v = c.uv
    w, h, d = c.size
    return {'down': (u + d, v, w, d), 'up': (u + d + w, v, w, d), 'west': (u, v + d, d, h),
            'north': (u + d, v + d, w, h), 'east': (u + d + w, v + d, d, h),
            'south': (u + 2 * d + w, v + d, w, h)}[face]


def texel_point(c, face, i, j):
    """Cube-local point (0..w, 0..h, 0..d) under texel (i, j) of a face, matching ModelPart.Cube's UVs."""
    w, h, d = c.size
    a, b = i + 0.5, j + 0.5
    return {'north': (a, b, 0.0), 'south': (w - a, b, float(d)), 'west': (0.0, b, d - a), 'east': (float(w), b, a),
            'down': (a, 0.0, d - b), 'up': (a, float(h), d - b)}[face]


# --------------------------------------------------------------------------------------- materials

def rgb(hexcode):
    hexcode = hexcode.lstrip('#')
    return tuple(int(hexcode[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(round(a[k] + (b[k] - a[k]) * t) for k in range(3))


def shade(color, factor):
    return tuple(max(0, min(255, round(c * factor))) for c in color)


class Ctx:
    """Everything a material needs about one texel."""
    __slots__ = ('face', 'x', 'y', 'z', 'w', 'h', 'd', 'i', 'j', 'fw', 'fh', 'rng', 'axis')

    def along(self):
        """0..1 position along the cube's long axis (x, y or z)."""
        size = {'x': self.w, 'y': self.h, 'z': self.d}[self.axis]
        value = {'x': self.x, 'y': self.y, 'z': self.z}[self.axis]
        return value / max(size, 1e-6)

    def edge(self):
        return self.i == 0 or self.j == 0 or self.i == self.fw - 1 or self.j == self.fh - 1

    def top(self):
        return self.face == 'down'

    def bottom(self):
        return self.face == 'up'


class Mat:
    """Tone ramp with dither noise; subclasses override pattern()."""
    face_light = {'down': 1.08, 'up': 0.80, 'north': 1.0, 'south': 0.94, 'west': 0.96, 'east': 0.96}

    def __init__(self, *tones, noise=0.18, edge=0.0, light=True):
        self.tones = [rgb(t) if isinstance(t, str) else t for t in tones]
        self.noise = noise
        self.edge_dark = edge
        self.light = light

    def tone(self, t):
        t = max(0.0, min(0.999, t))
        return self.tones[int(t * len(self.tones))]

    def pattern(self, c):
        return 0.5 + (c.rng.random() - 0.5) * 2 * self.noise, 255

    def color(self, c):
        t, alpha = self.pattern(c)
        if alpha == 0:
            return (0, 0, 0, 0)
        col = t if isinstance(t, tuple) else self.tone(t)
        if self.light:
            col = shade(col, self.face_light[c.face])
        if self.edge_dark and c.edge() and c.fw > 2 and c.fh > 2:
            col = shade(col, 1 - self.edge_dark)
        return (*col, alpha)


class Solid(Mat):
    pass


class Grain(Mat):
    """Wood, leather straps, hide: streaks along the long axis."""

    def __init__(self, *tones, streak=0.35, **kw):
        super().__init__(*tones, **kw)
        self.streak = streak
        self._lines = {}

    def pattern(self, c):
        across = {'x': (c.y, c.z), 'y': (c.x, c.z), 'z': (c.x, c.y)}[c.axis]
        key = (round(across[0] * 2), round(across[1] * 2))
        base = self._lines.setdefault(key, c.rng.random())
        return 0.45 + (base - 0.5) * self.streak + (c.rng.random() - 0.5) * self.noise, 255


class Ivory(Mat):
    """Bone and ivory: pale body, darker knobs toward both ends, a few pores."""

    def pattern(self, c):
        a = c.along()
        ends = max(0.0, abs(a - 0.5) * 2 - 0.55) * 1.2
        pore = 0.25 if c.rng.random() < 0.06 else 0
        return 0.62 - ends * 0.45 - pore + (c.rng.random() - 0.5) * self.noise, 255


class Tapered(Mat):
    """Teeth, claws, horns and antler tines: root colour to tip colour along one cube axis."""

    def __init__(self, *tones, tip='+y', ridge=None, **kw):
        super().__init__(*tones, **kw)
        self.tip = tip
        self.ridge = ridge

    def pattern(self, c):
        axis, sign = self.tip[1], self.tip[0]
        size = {'x': c.w, 'y': c.h, 'z': c.d}[axis]
        value = {'x': c.x, 'y': c.y, 'z': c.z}[axis] / max(size, 1e-6)
        t = value if sign == '+' else 1 - value
        return min(0.999, t * 0.9 + (c.rng.random() - 0.5) * self.noise), 255

    def color(self, c):
        rgba = super().color(c)
        if self.ridge and c.face == self.ridge[0] and c.i == self.ridge[1]:
            return (*shade(rgba[:3], 1.25), rgba[3])
        return rgba


class Twist(Mat):
    """Cords and fibre rope: diagonal twist stripes."""

    def __init__(self, *tones, period=2, **kw):
        super().__init__(*tones, **kw)
        self.period = period

    def pattern(self, c):
        p = (math.floor(c.x) + math.floor(c.y) + math.floor(c.z)) % self.period
        return (0.25 if p == 0 else 0.7) + (c.rng.random() - 0.5) * self.noise, 255


class Fur(Mat):
    """Pelts: vertical locks, a ragged lower edge on the sides."""

    def __init__(self, *tones, fringe=True, **kw):
        super().__init__(*tones, **kw)
        self.fringe = fringe
        self._locks = {}

    def pattern(self, c):
        key = (round(c.x), round(c.z), c.face)
        lock = self._locks.setdefault(key, c.rng.random())
        if self.fringe and c.face in ('north', 'south', 'east', 'west') and c.j == c.fh - 1 and lock < 0.45:
            return 0, 0
        t = 0.2 + lock * 0.6 - (c.y / max(c.h, 1)) * 0.15 + (c.rng.random() - 0.5) * self.noise
        return t, 255


class Feather(Mat):
    """A flat feather along the cube's long axis: pale shaft, barred vane, notched tip."""

    def __init__(self, *tones, bars=0, shaft='#e8e2d0', **kw):
        super().__init__(*tones, **kw)
        self.bars = bars
        self.shaft = rgb(shaft)

    def pattern(self, c):
        a = c.along()
        width_axis = [ax for ax in 'xyz' if ax != c.axis and {'x': c.w, 'y': c.h, 'z': c.d}[ax] > 0]
        side = 0.5
        if width_axis:
            ax = max(width_axis, key=lambda k: {'x': c.w, 'y': c.h, 'z': c.d}[k])
            size = {'x': c.w, 'y': c.h, 'z': c.d}[ax]
            side = {'x': c.x, 'y': c.y, 'z': c.z}[ax] / max(size, 1e-6)
            if size >= 3 and abs(side - 0.5) < 0.5 / size:
                return self.shaft, 255
            # A tapered, notched outline keeps it from reading as a plank.
            half = abs(side - 0.5) * 2
            if a > 0.82 and half > (1 - a) * 4:
                return 0, 0
        t = 0.35 + a * 0.4 + (c.rng.random() - 0.5) * self.noise
        if self.bars and int(a * self.bars * 2) % 2 == 1:
            t -= 0.3
        return t, 255


class Membrane(Mat):
    """Wing skin: warm translucent-looking tan with darker veins radiating from the spar."""

    def pattern(self, c):
        vein = (math.floor(c.x * 0.5 + c.z * 0.5 + c.y * 0.25)) % 3 == 0 and c.rng.random() < 0.7
        t = 0.55 + (c.rng.random() - 0.5) * self.noise - (0.35 if vein else 0)
        return t, 255


class Scales(Mat):
    """Crocodilian scutes: a grid of raised plates with dark seams."""

    def __init__(self, *tones, cell=2, **kw):
        super().__init__(*tones, **kw)
        self.cell = cell

    def pattern(self, c):
        u = c.i % self.cell
        v = (c.j + (c.i // self.cell) % 2) % self.cell
        if u == self.cell - 1 or v == self.cell - 1:
            return 0.22 + c.rng.random() * 0.08, 255
        return 0.62 + (0.25 if u == 0 and v == 0 else 0) + (c.rng.random() - 0.5) * self.noise, 255


class Leaves(Mat):
    """Ghillie foliage: clustered greens with holes along the edges."""

    def pattern(self, c):
        if c.face in ('north', 'south', 'east', 'west') and (c.j == c.fh - 1 or c.i in (0, c.fw - 1)) \
                and c.rng.random() < 0.35:
            return 0, 0
        cluster = (math.floor(c.x / 2) * 7 + math.floor(c.y / 2) * 13 + math.floor(c.z / 2) * 5) % 5 / 5
        return 0.2 + cluster * 0.6 + (c.rng.random() - 0.5) * self.noise, 255


class Glint(Mat):
    """Obsidian, flint and polished stone: dark body with sparse bright glints."""

    def __init__(self, *tones, glints=0.08, **kw):
        super().__init__(*tones, **kw)
        self.glints = glints

    def pattern(self, c):
        if c.rng.random() < self.glints:
            return 0.95, 255
        return 0.3 + (c.rng.random() - 0.5) * self.noise, 255


class Amber(Mat):
    """Amber: honey gradient with a dark trapped speck and a highlight; the brightest tones glow."""

    def glows(self, c, col):
        return sum(col[:3]) > 3 * 200

    def pattern(self, c):
        t = 0.35 + (1 - c.y / max(c.h, 1)) * 0.4 + (c.rng.random() - 0.5) * self.noise
        if c.face == 'north' and c.i == c.fw // 2 and c.j == c.fh // 2 and c.fw >= 2:
            return 0.0, 255
        if c.face in ('north', 'west') and c.i == 0 and c.j == 0:
            return 0.999, 255
        return t, 255


class Painted(Mat):
    """A base material with ochre bands or dots painted on (relics and totems)."""

    def __init__(self, base, paint='#b5402a', every=3, dots=False):
        super().__init__('#000000')
        self.base, self.paint, self.every, self.dots = base, rgb(paint), every, dots

    def color(self, c):
        rgba = self.base.color(c)
        if rgba[3] == 0:
            return rgba
        hit = (c.i + c.j) % self.every == 0 and c.j % 2 == 0 if self.dots else math.floor(c.y) % self.every == 0
        if hit and c.face in ('north', 'south', 'east', 'west'):
            return (*shade(self.paint, self.face_light[c.face]), 255)
        return rgba


class Spiral(Mat):
    """Unicorn horn: pearly spiral stripes."""

    def pattern(self, c):
        s = (math.floor(c.y * 1.0 + c.x + c.z)) % 3
        return (0.85 if s == 0 else 0.5) + (c.rng.random() - 0.5) * self.noise, 255


class Beads(Mat):
    """A cord threaded with coloured beads every few texels."""

    def __init__(self, cord, beads, every=2, **kw):
        super().__init__(cord, **kw)
        self.beads = [rgb(b) for b in beads]
        self.every = every

    def pattern(self, c):
        k = math.floor(c.x + c.y + c.z)
        if k % self.every == 0:
            return self.beads[(k // self.every) % len(self.beads)], 255
        return self.tones[0], 255


def paint_cube(tex, cube, bone, rng, glow=None):
    w, h, d = cube.size
    dims = {'x': w, 'y': h, 'z': d}
    axis = max(dims, key=lambda k: dims[k])
    for face in cube.faces:
        u, v, fw, fh = face_rect(cube, face)
        for j in range(fh):
            for i in range(fw):
                x, y, z = texel_point(cube, face, i, j)
                c = Ctx()
                c.face, c.x, c.y, c.z, c.w, c.h, c.d = face, x, y, z, w, h, d
                c.i, c.j, c.fw, c.fh, c.rng, c.axis = i, j, fw, fh, rng, axis
                col = cube.mat.color(c)
                tex[v + j, u + i] = col
                if glow is not None and col[3] and getattr(cube.mat, 'glows', None) and cube.mat.glows(c, col):
                    glow[v + j, u + i] = col


# ------------------------------------------------------------------------------------------ icons

def icon(rows, palette, size=16):
    """ASCII pixel art: each character is a palette key, '.' is transparent."""
    image = Image.new('RGBA', (size, size))
    assert len(rows) == size, f'icon needs {size} rows, got {len(rows)}'
    for y, row in enumerate(rows):
        assert len(row) == size, f'row {y} is {len(row)} wide: {row!r}'
        for x, ch in enumerate(row):
            if ch != '.':
                col = palette[ch]
                image.putpixel((x, y), (*rgb(col), 255) if isinstance(col, str) else (*col, 255))
    return image


# ----------------------------------------------------------------------------------------- render

def _vanilla(path):
    with zipfile.ZipFile(JAR) as jar:
        return Image.open(io.BytesIO(jar.read(path))).convert('RGBA')


def rot_matrix(rx, ry, rz):
    """ModelPart order: rotationZYX(z, y, x) == Rz * Ry * Rx."""
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    mx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    my = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    mz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return mz @ my @ mx


def _cube_faces(origin, size, grow, uv, texsize, mirror=False, faces=ALL):
    """Quads of one ModelPart.Cube in its bone's space: (4 vertices, 4 uv pairs)."""
    x0, y0, z0 = (origin[k] - grow[k] for k in range(3))
    x1, y1, z1 = (origin[k] + size[k] + grow[k] for k in range(3))
    if mirror:
        x0, x1 = x1, x0
    w, h, d = size
    u, v = uv
    tw, th = texsize
    t0, t1, t2, t3 = (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)
    l0, l1, l2, l3 = (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)
    u0, u1, u2, u22, u3, u4 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    v0, v1, v2 = v, v + d, v + d + h
    polys = {'down': ([l1, l0, t0, t1], u1, v0, u2, v1), 'up': ([t2, t3, l3, l2], u2, v1, u22, v0),
             'west': ([t0, l0, l3, t3], u0, v1, u1, v2), 'north': ([t1, t0, t3, t2], u1, v1, u2, v2),
             'east': ([l1, t1, t2, l2], u2, v1, u3, v2), 'south': ([l0, l1, l2, l3], u3, v1, u4, v2)}
    out = []
    swap = {'west': 'east', 'east': 'west'}
    for face, (verts, a0, b0, a1, b1) in polys.items():
        if (swap.get(face, face) if mirror else face) not in faces:
            continue
        uvs = [(a1, b0), (a0, b0), (a0, b1), (a1, b1)]
        verts = list(verts)
        if mirror:
            verts.reverse()
            uvs.reverse()
        out.append((np.array(verts, dtype=float), np.array(uvs, dtype=float) / (tw, th)))
    return out


def player_quads(slim=False, skin=None, pose=None):
    """The player's own cubes (skin base and overlay) as (part, quads, texture)."""
    skin = skin or _vanilla(f'assets/minecraft/textures/entity/player/{"slim" if slim else "wide"}/'
                            f'{"alex" if slim else "steve"}.png')
    tex = np.array(skin)
    arm_w = 3 if slim else 4
    cubes = [('head', (-4, -8, -4), (8, 8, 8), 0.0, (0, 0)), ('head', (-4, -8, -4), (8, 8, 8), 0.5, (32, 0)),
             ('body', (-4, 0, -2), (8, 12, 4), 0.0, (16, 16)), ('body', (-4, 0, -2), (8, 12, 4), 0.25, (16, 32)),
             ('right_arm', (-3 if not slim else -2, -2, -2), (arm_w, 12, 4), 0.0, (40, 16)),
             ('right_arm', (-3 if not slim else -2, -2, -2), (arm_w, 12, 4), 0.25, (40, 32)),
             ('left_arm', (-1, -2, -2), (arm_w, 12, 4), 0.0, (32, 48)),
             ('left_arm', (-1, -2, -2), (arm_w, 12, 4), 0.25, (48, 48)),
             ('right_leg', (-2, 0, -2), (4, 12, 4), 0.0, (0, 16)), ('right_leg', (-2, 0, -2), (4, 12, 4), 0.25, (0, 32)),
             ('left_leg', (-2, 0, -2), (4, 12, 4), 0.0, (16, 48)), ('left_leg', (-2, 0, -2), (4, 12, 4), 0.25, (0, 48))]
    out = []
    for part, origin, size, grow, uv in cubes:
        quads = _cube_faces(origin, size, (grow,) * 3, uv, (64, 64))
        out.append((part, _to_root(part, quads, pose), tex))
    return out


def _part_matrix(part, pose):
    px, py, pz = PART_POSE[part]
    rx, ry, rz = (pose or {}).get(part, (0, 0, 0))
    return np.array([px, py, pz], dtype=float), rot_matrix(math.radians(rx), math.radians(ry), math.radians(rz))


def _to_root(part, quads, pose):
    offset, matrix = _part_matrix(part, pose)
    return [((verts @ matrix.T) + offset, uvs) for verts, uvs in quads]


def worn_quads(model, texture, pose=None, side=None, arms='wide', fitted=()):
    """Accessory quads in root space for one arm model and slot side, like WornModel on the client."""
    data = model if isinstance(model, dict) else model.to_json()
    tex = np.array(texture.convert('RGBA'))
    size = data['texture_size']
    out = []
    for b in data['bones']:
        if b.get('arms') and b['arms'] != arms:
            continue
        if side and b.get('side') and b['side'] != side:
            continue
        quads = []
        for c in b['cubes']:
            quads += _cube_faces(c['origin'], c['size'], c.get('grow', (0, 0, 0)), c['uv'], size, c.get('mirror', False),
                                 frozenset(c.get('faces', FACES)))
        rot = b['open'] if b.get('anim') == 'wing' and 'spread' in (pose or {}) and b.get('open') else b['rotation']
        if b.get('anim') == 'wing' and 'spread' in (pose or {}) and b.get('open'):
            t = pose['spread']
            rot = [b['rotation'][k] + (b['open'][k] - b['rotation'][k]) * t for k in range(3)]
        m = rot_matrix(*(math.radians(r) for r in rot))
        pivot = np.array(b['pivot'], dtype=float)
        quads = [((verts @ m.T) + pivot, uvs) for verts, uvs in quads]
        if b.get('fit') in fitted:
            quads = [(_fit(b['part'], b['fit'], verts), uvs) for verts, uvs in quads]
        out.append((b['part'], _to_root(b['part'], quads, pose), tex))
    return out


FIT_SCALE = {'helmet': (1.22, 1.22, 1.22), 'chest': (1.25, 1.12, 1.5), 'legs': (1.25, 1.06, 1.3),
             'boots': (1.3, 1.1, 1.3)}
FIT_ARM_SCALE = {'chest': (1.45, 1.12, 1.45)}


def fit_centre(part):
    (x, y, z), (w, h, d) = PART_BOX[part]
    return np.array([x + w / 2, y + h / 2, z + d / 2], dtype=float)


def fit_scale(part, fit):
    if part.endswith('_arm'):
        return FIT_ARM_SCALE.get(fit, (1, 1, 1))
    return FIT_SCALE.get(fit, (1, 1, 1))


def _fit(part, fit, verts):
    centre = fit_centre(part)
    return (verts - centre) * np.array(fit_scale(part, fit)) + centre


def rasterise(groups, size=(220, 300), yaw=-28.0, pitch=12.0, scale=7.0, centre=(0, 12, 0), background=None):
    """Orthographic render of root-space quads. Model space is converted to a right-handed world first."""
    width, height = size
    pixels = np.zeros((height, width, 4), dtype=np.uint8)
    if background is not None:
        pixels[:, :] = background
    depth = np.full((height, width), -np.inf)
    ya, pa = math.radians(yaw), math.radians(pitch)
    # World: x right, y up, z toward the viewer at yaw 0 (the wearer faces the camera).
    view = np.array([[math.cos(ya), 0, math.sin(ya)],
                     [math.sin(ya) * math.sin(pa), math.cos(pa), -math.cos(ya) * math.sin(pa)],
                     [-math.sin(ya) * math.cos(pa), math.sin(pa), math.cos(ya) * math.cos(pa)]])
    light = np.array([-0.35, 0.9, 0.55])
    light /= np.linalg.norm(light)
    c = np.array(centre, dtype=float)
    for _part, quads, tex in groups:
        th, tw = tex.shape[:2]
        for verts, uvs in quads:
            world = verts - c
            world = world * np.array([1, -1, -1])           # model +x left, +y down, -z front -> world
            cam = world @ view.T
            normal = np.cross(cam[1] - cam[0], cam[2] - cam[0])
            n = np.linalg.norm(normal)
            if n < 1e-9:
                continue
            normal /= n
            if normal[2] < -0.02:
                continue                                    # back faces never win the depth test here
            shading = 0.62 + 0.38 * max(0.0, float(normal @ (view @ light)))
            p = cam.copy()
            p[:, 0] = p[:, 0] * scale + width / 2
            p[:, 1] = height / 2 - p[:, 1] * scale
            for tri in ((0, 1, 2), (0, 2, 3)):
                t = p[list(tri)]
                tuv = uvs[list(tri)]
                lo = np.maximum([0, 0], np.floor(t[:, :2].min(axis=0)).astype(int))
                hi = np.minimum([width - 1, height - 1], np.ceil(t[:, :2].max(axis=0)).astype(int))
                if (lo > hi).any():
                    continue
                xx, yy = np.meshgrid(np.arange(lo[0], hi[0] + 1) + .5, np.arange(lo[1], hi[1] + 1) + .5)
                a, b, cc = t[:, :2]
                den = (b[1] - cc[1]) * (a[0] - cc[0]) + (cc[0] - b[0]) * (a[1] - cc[1])
                if abs(den) < 1e-9:
                    continue
                w0 = ((b[1] - cc[1]) * (xx - cc[0]) + (cc[0] - b[0]) * (yy - cc[1])) / den
                w1 = ((cc[1] - a[1]) * (xx - cc[0]) + (a[0] - cc[0]) * (yy - cc[1])) / den
                w2 = 1 - w0 - w1
                zz = w0 * t[0, 2] + w1 * t[1, 2] + w2 * t[2, 2]
                region = depth[lo[1]:hi[1] + 1, lo[0]:hi[0] + 1]
                coords = w0[..., None] * tuv[0] + w1[..., None] * tuv[1] + w2[..., None] * tuv[2]
                tx = np.clip((coords[..., 0] * tw).astype(int), 0, tw - 1)
                ty = np.clip((coords[..., 1] * th).astype(int), 0, th - 1)
                col = tex[ty, tx].copy()
                col[..., :3] = np.clip(col[..., :3] * shading, 0, 255).astype(np.uint8)
                inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
                mask = inside & (zz > region + 1e-4) & (col[..., 3] > 25)
                region[mask] = zz[mask]
                pixels[lo[1]:hi[1] + 1, lo[0]:hi[0] + 1][mask] = col[mask]
    return Image.fromarray(pixels, 'RGBA')


WALK = {'right_arm': (-18, 0, 4), 'left_arm': (18, 0, -4), 'right_leg': (16, 0, 0), 'left_leg': (-16, 0, 0)}


def preview(model, texture, slim=False, side=None, views=((-30, 10), (150, 10)), pose=None, fitted=(),
            size=(200, 280), scale=7.0, background=(236, 233, 224, 255), centre=(0, 12, 0)):
    """Accessory on a player from the front three-quarter and the back three-quarter."""
    pose = dict(WALK if pose is None else pose)
    groups = player_quads(slim, pose=pose) + worn_quads(model, texture, pose, side, 'slim' if slim else 'wide', fitted)
    frames = [rasterise(groups, size, yaw, pitch, scale, centre, background) for yaw, pitch in views]
    sheet = Image.new('RGBA', (size[0] * len(frames), size[1]), background)
    for k, frame in enumerate(frames):
        sheet.alpha_composite(frame, (k * size[0], 0))
    return sheet
