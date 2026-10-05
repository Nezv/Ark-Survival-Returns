"""Wing membranes of the three dragons (I10).

Black is the user's hand-authored wyvern as it is (Creatures/Dragon/Dragon/dragon.geo.json and dragon.png). Red and
white are the same wing at another size, so their membrane planes take black's texture cells and get a drawing of
their own by the rules of the hand-drawn one: one flat colour, a clean cut along each finger bone so no membrane
passes a bone, a trailing edge torn in brush-sized blocks, and a few brush-sized holes. The right wing mirrors the
left, as in black.

Writes Creatures/Dragon/Dragon/out/wyvern_<variant>.{geo.json,png} and imports the three into the mod.
Run: python Ark/tools/build_dragon_wings.py
"""
import json
import math
import random
import re
import shutil
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT.parent / 'Creatures/Dragon/Dragon'
OUT = SOURCE / 'out'
HAND_GEO, HAND_TEX = SOURCE / 'dragon.geo.json', SOURCE / 'dragon.png'
sys.path.insert(0, str(ROOT.parent / 'scripts'))
import skin_studio as studio  # noqa: E402  (bone transforms only)

MEMBRANE_BONE = re.compile(r'bone2r?|wing_[lr]_finger[1-4]')
OLD_BLOCK = 1390  # the generated membranes this replaces were packed below this texture row
# Colour: black's membrane grey (131, 121, 121) through the ramp that tinted each variant's hide (derive_variants.py).
# brush: side of the square the trailing edge is torn with; hole: side of a hole; sag: how far the middle of the edge
# between two finger tips hangs towards the wrist, as a share of its distance; tear: bites, in brushes, to draw from;
# notches: deeper bites per edge; holes: against black's count; hold: share of the last finger with its full strip.
STYLE = {
    'red': dict(colour=(177, 82, 61), seed=7, brush=4, hole=3, sag=0.04, tear=(0, 1, 1, 2, 3, 4), notches=2,
                holes=1.0, hold=0.5),
    'white': dict(colour=(229, 232, 237), seed=23, brush=3, hole=3, sag=0.10, tear=(0, 0, 1, 1, 2, 3), notches=1,
                  holes=0.6, hold=0.62),
}
TIP = 0.97        # the membrane ends this far along a finger, short of its tip
HOLE_AREA = 380   # one hole per this many painted texels, as in black


def membranes(model):
    """(bone name, cube index, cube) of every membrane plane."""
    for bone in model['bones']:
        if MEMBRANE_BONE.fullmatch(bone['name']):
            for index, cube in enumerate(bone.get('cubes', [])):
                if cube['size'][1] == 0:
                    yield bone['name'], index, cube


def face_line(face, entry):
    return f'"{face}": {{"uv": {json.dumps(entry["uv"])}, "uv_size": {json.dumps(entry["uv_size"])}}}'


def black():
    """The hand-authored model, under the name the importer reads."""
    text = HAND_GEO.read_text(encoding='utf-8')
    name = json.loads(text)['minecraft:geometry'][0]['description']['identifier']
    (OUT / 'wyvern_black.geo.json').write_text(text.replace(f'"{name}"', '"geometry.allunderheaven.wyvern_black"', 1),
                                               encoding='utf-8', newline='\n')
    shutil.copyfile(HAND_TEX, OUT / 'wyvern_black.png')


def take_cells(variant):
    """Point the variant's membrane planes at black's texture cells; the file keeps its layout line for line."""
    path = OUT / f'wyvern_{variant}.geo.json'
    text = path.read_text(encoding='utf-8')
    model = json.loads(text)['minecraft:geometry'][0]
    hand = {(bone, index): cube for bone, index, cube in membranes(json.loads(HAND_GEO.read_text())['minecraft:geometry'][0])}
    planes = list(membranes(model))
    assert {(bone, index) for bone, index, _ in planes} == set(hand), 'membrane planes differ from the hand-authored wing'
    for bone, index, cube in planes:
        for face, entry in cube['uv'].items():
            old, new = face_line(face, entry), face_line(face, hand[bone, index]['uv'][face])
            if old != new:
                assert text.count(old) == 1, f'{bone} #{index} {face}: {old}'
                text = text.replace(old, new)
    path.write_text(text, encoding='utf-8', newline='\n')
    return path


def rect(entry):
    (u, v), (du, dv) = entry['uv'], entry['uv_size']
    return (math.floor(min(u, u + du)), math.floor(min(v, v + dv)), math.ceil(max(u, u + du)), math.ceil(max(v, v + dv)))


class Cell:
    """A membrane plane's top face: its texels, and where each one lies on the wing laid flat."""

    def __init__(self, cube, corners):
        self.name = f'{cube["bone"]}#{cube["cube_index"]}'
        up = cube['source_uv']['up']
        (self.u0, self.v0), (self.du, self.dv) = up['uv'], up['uv_size']
        self.x, self.y, x1, y1 = rect(up)
        self.w, self.h = x1 - self.x, y1 - self.y
        self.origin, self.ex, self.ez = corners[0], corners[1] - corners[0], corners[4] - corners[0]
        self.size = (float(np.linalg.norm(self.ex)), float(np.linalg.norm(self.ez)))
        self.mask = np.zeros((self.h, self.w), bool)

    def flat(self, u, v):
        """Texture position to wing plane, GeckoLib's top face: u = u0 + du * (1 - x), v = v0 + dv * (1 - z)."""
        sx, sz = 1 - (u - self.u0) / self.du, 1 - (v - self.v0) / self.dv
        return self.origin + sx[..., None] * self.ex + sz[..., None] * self.ez, (sx >= 0) & (sx <= 1) & (sz >= 0) & (sz <= 1)

    def texel(self, point):
        """Wing plane to texel index within the cell (not rounded)."""
        rel = point - self.origin
        sx, sz = rel @ self.ex / self.size[0] ** 2, rel @ self.ez / self.size[1] ** 2
        return self.u0 + self.du * (1 - sx) - self.x, self.v0 + self.dv * (1 - sz) - self.y

    def depth(self, points):
        """How far inside the plane's rectangle each point lies, in model units; negative outside."""
        rel = points - self.origin
        a, b = rel @ self.ex / self.size[0], rel @ self.ez / self.size[1]
        return np.minimum.reduce([a, self.size[0] - a, b, self.size[1] - b])

    def centres(self):
        us, vs = np.meshgrid(self.x + np.arange(self.w) + 0.5, self.y + np.arange(self.h) + 0.5)
        return self.flat(us, vs)

    def blocks(self, brush, rng):
        """Centre of the brush-sized block each texel belongs to, on a lattice of the cell's own."""
        ox, oy = rng.randrange(brush), rng.randrange(brush)
        bu = ((np.arange(self.w) + ox) // brush) * brush - ox + brush / 2
        bv = ((np.arange(self.h) + oy) // brush) * brush - oy + brush / 2
        us, vs = np.meshgrid(self.x + bu, self.y + bv)
        return self.flat(us, vs)[0]

    def punch(self, point, width, height):
        """A hole, only where it lands whole and enclosed."""
        u, v = self.texel(point)
        i, j = int(round(u - width / 2)), int(round(v - height / 2))
        if i < 1 or j < 1 or i + width > self.w - 1 or j + height > self.h - 1:
            return False
        if not self.mask[j - 1:j + height + 1, i - 1:i + width + 1].all():
            return False
        self.mask[j:j + height, i:i + width] = False
        return True


class Wing:
    """The left wing laid flat. Every finger turns about one axis of the hand, so spars and planes share a plane;
    positions are polar about the wrist: distance, and angle from the first finger towards the last."""

    def __init__(self, geo_path):
        _, cubes, _ = studio.load_cubes(geo_path)
        hand = next(cube for cube in cubes if cube['bone'] == 'wing_l_hand')

        def lay(points):
            return ((np.asarray(points) - hand['bone_world']) @ hand['bone_matrix'] + hand['bone_pivot'])[:, [0, 2]]

        segments, self.cells = {}, {}
        for cube in cubes:
            match = re.fullmatch(r'wing_l_finger(\d)([bc]?)', cube['bone'])
            if not match:
                continue
            corners = lay(studio.cube_corners(cube))
            if cube['size'][1] == 0:
                self.cells.setdefault(int(match.group(1)), []).append(Cell(cube, corners))
            else:
                segments.setdefault(int(match.group(1)), {})[match.group(2)] = (
                    corners[[0, 3, 4, 7]].mean(0), corners[[1, 2, 5, 6]].mean(0))
        self.fingers = sorted(segments)
        bases = [segments[finger][''] for finger in self.fingers]
        # the fingers share their base: the end every first segment has in common
        self.wrist = min((end for base in bases for end in base),
                         key=lambda end: sum(min(np.linalg.norm(end - a), np.linalg.norm(end - b)) for a, b in bases))
        lines = {}
        for finger, parts in segments.items():
            line = [self.wrist]
            for suffix in sorted(parts):
                a, b = parts[suffix]
                line.append(b if np.linalg.norm(a - line[-1]) < np.linalg.norm(b - line[-1]) else a)
            lines[finger] = np.array(line)
        first, last = lines[self.fingers[0]], lines[self.fingers[-1]]
        self.axis = (first[1] - first[0]) / np.linalg.norm(first[1] - first[0])
        self.normal = np.array([-self.axis[1], self.axis[0]])
        if (last[1] - last[0]) @ self.normal < 0:
            self.normal = -self.normal
        self.spar = {}
        for finger, line in lines.items():
            steps = [np.linspace(a, b, max(2, int(np.linalg.norm(b - a) * 2)), endpoint=False) for a, b in zip(line, line[1:])]
            r, theta = self.polar(np.concatenate(steps + [line[-1:]]))
            keep = r > 1.0
            self.spar[finger] = (r[keep], theta[keep])

    def polar(self, points):
        rel = points - self.wrist
        return np.hypot(rel[..., 0], rel[..., 1]), np.arctan2(rel @ self.normal, rel @ self.axis)

    def point(self, r, theta):
        return self.wrist + r * (math.cos(theta) * self.axis + math.sin(theta) * self.normal)

    def angle(self, finger, r):
        return np.interp(r, *self.spar[finger])

    def tip(self, finger):
        return float(self.spar[finger][0][-1])


def grow(mask, fill):
    """The mask with every texel's eight neighbours, or without those that miss one (fill: what lies off the cell)."""
    padded = np.pad(mask, 1, constant_values=fill)
    parts = [padded[j:j + mask.shape[0], i:i + mask.shape[1]] for j in range(3) for i in range(3)]
    return np.logical_and.reduce(parts) if fill else np.logical_or.reduce(parts)


def close_seams(cells):
    """Two planes tear on lattices of their own, a few degrees apart, which leaves slits a texel or two wide where
    they meet. Each plane fills those, looking at its neighbours' paint as well as its own."""
    paint = [cell.mask.copy() for cell in cells]
    for cell in cells:
        points, _ = cell.centres()
        around = cell.mask.copy()
        for other, mask in zip(cells, paint):
            if other is cell:
                continue
            u, v = other.texel(points)
            i, j = np.floor(u).astype(int), np.floor(v).astype(int)
            on = (i >= 0) & (j >= 0) & (i < other.w) & (j < other.h)
            around[on] |= mask[j[on], i[on]]
        cell.mask |= grow(grow(around, False), True) & ~around & cell.room


def paint_between(wing, a, b, style, rng):
    """The membrane between finger a and the next one, b: cut along both bones, torn along the edge between the tips."""
    brush, cells = style['brush'], sorted(wing.cells[a], key=lambda cell: -cell.w * cell.h)
    ra, rb = wing.tip(a) * TIP, wing.tip(b) * TIP
    low, high = float(wing.angle(a, ra)), float(wing.angle(b, rb))
    # the edge before tearing: from one finger tip to the next, its middle sagging towards the wrist
    tip_a, tip_b = wing.point(ra, low), wing.point(rb, high)
    middle = (tip_a + tip_b) / 2
    control = middle - (middle - wing.wrist) * 2 * style['sag']
    f = np.linspace(0, 1, 200)[:, None]
    curve_r, curve_theta = wing.polar((1 - f) ** 2 * tip_a + 2 * f * (1 - f) * control + f ** 2 * tip_b)
    bins = max(6, round((ra + rb) / 2 * (high - low) / (1.5 * brush)))
    bites, level = [], 0
    for index in range(bins):
        if rng.random() < 0.45:
            level = rng.choice(style['tear'])
        from_bone = min(index, bins - 1 - index)
        bites.append(0 if from_bone == 0 else min(level, 1) if from_bone == 1 else level)
    for _ in range(style['notches']):
        bites[rng.randrange(2, bins - 2)] += rng.choice((2, 3))
    bites = np.array(bites) * brush

    # ... and a brush short of where the planes end, so a plane's straight border never stands as the edge
    angles = np.linspace(low, high, bins * 4 + 1)
    reach = np.arange(1.0, max(ra, rb) + 1, 0.5)
    rays = np.cos(angles)[:, None, None] * wing.axis + np.sin(angles)[:, None, None] * wing.normal
    covered = np.max([cell.depth(wing.wrist + reach[None, :, None] * rays) for cell in cells], axis=0) >= 0
    planes_r = np.where(covered, reach[None, :], 0).max(axis=1) - brush

    def edge(theta):
        share = np.clip((theta - low) / (high - low), 0, 1)
        whole = np.minimum(np.interp(theta, curve_theta, curve_r), np.interp(theta, angles, planes_r))
        return whole - bites[np.minimum((share * bins).astype(int), bins - 1)]

    for order, cell in enumerate(cells):
        points, inside = cell.centres()
        r, theta = wing.polar(points)
        cell.room = inside & (theta >= wing.angle(a, r)) & (theta <= wing.angle(b, r))
        for owner in cells[:order]:  # a plane leaves to a larger one what that one covers, but for a texel of seam
            cell.room &= owner.depth(points) < 1.0
        block_r, block_theta = wing.polar(cell.blocks(brush, rng))
        cell.mask = cell.room & (block_r <= edge(block_theta))
    close_seams(cells)

    area = sum(int(cell.mask.sum()) for cell in cells)
    placed, tries, wanted = [], 0, round(area / HOLE_AREA * style['holes'])
    while len(placed) < wanted and tries < 4000:
        tries += 1
        r = rng.uniform(0.2, 1.0) * max(ra, rb)
        from_a, from_b = float(wing.angle(a, r)), float(wing.angle(b, r))
        theta = rng.uniform(from_a, from_b)
        if r > float(edge(np.array(theta))) - 2 * brush or r * min(theta - from_a, from_b - theta) < style['hole'] + 2:
            continue
        point = wing.point(r, theta)
        if any(np.linalg.norm(point - other) < 3 * style['hole'] for other in placed):
            continue
        wide = style['hole'] * (2 if rng.random() < 0.25 else 1)
        if any([cell.punch(point, wide, style['hole']) for cell in cells]):
            placed.append(point)
    return cells


def paint_behind(wing, finger, style, rng):
    """The strip behind the last finger: cut along the bone, full width to `hold` of its length, then stepping in
    towards the bone until a brush is left at the tip."""
    brush = style['brush']
    (cell,) = wing.cells[finger]
    tip = wing.tip(finger) * TIP
    points, inside = cell.centres()
    r, theta = wing.polar(points)
    off = theta - wing.angle(finger, r)
    keep = inside & (off >= 0) & (off < math.pi / 2)
    rb_, tb = wing.polar(cell.blocks(brush, rng))
    off_b = np.clip(tb - wing.angle(finger, rb_), 0, math.pi / 2)
    along, away = rb_ * np.cos(off_b), rb_ * np.sin(off_b)
    full = math.hypot(*cell.size)
    bins = max(4, round(tip * (1 - style['hold']) / (1.5 * brush)))
    widths, bite = [], 0
    for index in range(bins):
        share = 1 - ((index + 1) / bins) ** 0.8
        if rng.random() < 0.4:
            bite = rng.choice((0, 0, 1))
        widths.append(max(brush, round(cell.size[1] * share / brush) * brush - bite * brush))
    widths = np.array([full] + widths)
    step = np.clip(np.floor((along / tip - style['hold']) / (1 - style['hold']) * bins).astype(int) + 1, 0, bins)
    cell.mask = keep & (along <= tip) & (away <= widths[step])
    return [cell]


def paint_arm(shape, style, rng):
    """The membrane behind the upper arm: the whole plane, its trailing (lower) edge in drips of one brush."""
    height, width = shape
    brush = style['brush'] + 1  # this plane has nearly two texels to the unit
    mask = np.ones(shape, bool)
    level, offset = 0, rng.randrange(brush)
    for start in range(-offset, width, brush):
        if rng.random() < 0.5:
            level = rng.choice(style['tear'])
        deep = level + (rng.choice((2, 3)) if rng.random() < 0.08 * style['notches'] else 0)
        if deep:
            mask[height - min(deep * brush, height * 2 // 5):, max(0, start):start + brush] = False
    placed, tries, wanted = [], 0, round(mask.sum() / (HOLE_AREA * 3) * style['holes'])
    while len(placed) < wanted and tries < 2000:
        tries += 1
        i, j = rng.randrange(2, width - brush - 2), rng.randrange(2, height - brush - 2)
        if mask[j - 2:j + brush + 2, i - 2:i + brush + 2].all() and all(abs(i - pi) + abs(j - pj) > 4 * brush for pi, pj in placed):
            mask[j:j + brush, i:i + brush] = False
            placed.append((i, j))
    return mask


def variant(name):
    style, rng = STYLE[name], random.Random(STYLE[name]['seed'])
    geo_path = take_cells(name)
    model = json.loads(geo_path.read_text())['minecraft:geometry'][0]
    cells = {rect(cube['uv'][face]) for _, _, cube in membranes(model) for face in ('up', 'down')}
    for bone in model['bones']:
        for cube in bone.get('cubes', []):
            if cube['size'][1] == 0 and MEMBRANE_BONE.fullmatch(bone['name']):
                continue
            for entry in cube['uv'].values():
                x0, y0, x1, y1 = rect(entry)
                assert y1 <= OLD_BLOCK, f'{bone["name"]} reaches the old membrane block'
                assert not any(x0 < c[2] and c[0] < x1 and y0 < c[3] and c[1] < y1 for c in cells if x1 > x0 and y1 > y0), \
                    f'{bone["name"]} shares texels with a membrane cell'

    image = np.array(Image.open(OUT / f'wyvern_{name}.png').convert('RGBA'))
    image[OLD_BLOCK:] = 0
    for x0, y0, x1, y1 in cells:
        image[y0:y1, x0:x1] = 0

    wing = Wing(geo_path)
    painted = []
    for a, b in zip(wing.fingers, wing.fingers[1:]):
        painted += paint_between(wing, a, b, style, rng)
    painted += paint_behind(wing, wing.fingers[-1], style, rng)
    for cell in painted:
        image[cell.y:cell.y + cell.h, cell.x:cell.x + cell.w][cell.mask] = style['colour'] + (255,)
    arm = next(cube for bone, index, cube in membranes(model) if bone == 'bone2')
    x0, y0, x1, y1 = rect(arm['uv']['up'])
    image[y0:y1, x0:x1][paint_arm((y1 - y0, x1 - x0), style, rng)] = style['colour'] + (255,)
    Image.fromarray(image, 'RGBA').save(OUT / f'wyvern_{name}.png')
    print(f'{name}: {sum(int(cell.mask.sum()) for cell in painted)} membrane texels in {len(painted)} finger planes')


def main():
    black()
    for name in STYLE:
        variant(name)
    sys.path.insert(0, str(Path(__file__).parent))
    from import_dragon_runtime import import_dragon
    import_dragon()


if __name__ == '__main__':
    main()
