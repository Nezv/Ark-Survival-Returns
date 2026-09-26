"""Creature eyes as their own geometry (dashboard X02), isolated from the painted bodies.

import_creatures.py calls add_eyes() for every species while importing: the body cubes, their UVs and
the painted skin stay untouched. The old sub-pixel eye cubes are emptied (their bones stay, animations
still target them) and a separate bone tree is added under the skull:

    ark_eyes                        rest rotation cancels the skull's, so the eyes sit world-aligned
      ark_eye_calm                  predatory (carnivore) or calm (herbivore) lids
        ark_eyeball_calm_l / _r     eyeballs, also used by the night-glow layer
      ark_eye_alert                 the attacking/defending variant, hidden unless the creature is alarmed
        ark_eyeball_alert_l / _r

Eye art lives in a strip appended below each variant atlas, so body UVs never move. CreatureRenderer
switches the two groups from the synced behaviour state.
"""
import math
import re
from pathlib import Path
import numpy as np
from PIL import Image

CORNERS = np.array([[0, 0, 0], [1, 0, 0], [1, 1, 0], [0, 1, 0], [0, 0, 1], [1, 0, 1], [1, 1, 1], [0, 1, 1]], float)
LID = re.compile(r'eyelid|lidmain|lid_|_lid|uppereye|lowereye|eyeupper|eyelower|top_eyelid|bot_eyelid', re.I)
NO_EYES = {'cnidaria', 'tusoteuthis', 'dragon'}  # no eye anchor in the rig; the dragon is replaced by I10
STRIP = 16                                       # rows appended below each atlas
# Pupil shape by body plan: slit for reptiles and archosaurs, round for mammals and birds, black for sharks.
ROUND_PUPIL = {'direwolf', 'sabertooth', 'ravager', 'mammoth', 'megalocerus', 'paraceratherium', 'unicorn',
               'megapithecus', 'argentavis', 'terrorbird', 'archaeopteryx'}
BLACK_EYE = {'megalodon'}

# Expression per state: upper-lid cover (share of the eye), brow slant (degrees, + lowers the snout end),
# lower-lid cover, and lid thickness (share of eye height).
EXPRESSIONS = {
    ('predator', 'calm'): dict(cover=.30, slant=14, low=.12, thick=.34),
    ('predator', 'alert'): dict(cover=.42, slant=26, low=.22, thick=.38),
    ('grazer', 'calm'): dict(cover=.22, slant=-4, low=0, thick=.26),
    ('grazer', 'alert'): dict(cover=.03, slant=-12, low=0, thick=.22),
}
SOCKET = (27, 21, 18)
PLACEMENT = {}  # identifier -> (skull, centre, eye height, surface x); read by the import report and previews
SPECIES_JAVA = Path(__file__).resolve().parents[1] / 'src/main/java/dev/nez/arksurvivalreturns/feature/creature/Species.java'


def predators():
    """Species ids whose `predator` flag is set in Species.java, the flag that also drives the night glow."""
    source = SPECIES_JAVA.read_text(encoding='utf-8')
    pattern = re.compile(r'^\s+[A-Z_]+\("(\w+)",\s*"[^"]*",(?:[^,]+,){8}\s*(true|false)', re.M)
    return {m.group(1) for m in pattern.finditer(source) if m.group(2) == 'true'}


def rgb(code):
    return np.array([int(code[i:i + 2], 16) for i in (1, 3, 5)], float)


# ------------------------------------------------------------------------------------ rig math
def euler(rotation):
    """Bedrock rotation (degrees) to the renderer's Z*Y*X matrix, with X and Y negated (skin_studio.load_cubes)."""
    a, b, c = np.radians(np.asarray(rotation, float) * [-1, -1, 1])
    rx = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
    ry = np.array([[math.cos(b), 0, math.sin(b)], [0, 1, 0], [-math.sin(b), 0, math.cos(b)]])
    rz = np.array([[math.cos(c), -math.sin(c), 0], [math.sin(c), math.cos(c), 0], [0, 0, 1]])
    return rz @ ry @ rx


def to_bedrock(matrix):
    b = math.asin(max(-1.0, min(1.0, -matrix[2, 0])))
    a = math.atan2(matrix[2, 1], matrix[2, 2])
    c = math.atan2(matrix[1, 0], matrix[0, 0])
    return [round(-math.degrees(a), 6), round(-math.degrees(b), 6), round(math.degrees(c), 6)]


def skeleton(bones):
    names = [bone['name'] for bone in bones]
    index = {name: i for i, name in enumerate(names)}
    pivots, worlds, matrices = [], [], []
    for bone in bones:
        pivot = np.array(bone['pivot'], float) * [-1, 1, 1]
        matrix = euler(bone.get('rotation', [0, 0, 0]))
        parent = index.get(bone.get('parent'))
        if parent is None:
            worlds.append(pivot)
            matrices.append(matrix)
        else:
            worlds.append(worlds[parent] + matrices[parent] @ (pivot - pivots[parent]))
            matrices.append(matrices[parent] @ matrix)
        pivots.append(pivot)
    return index, pivots, worlds, matrices


def corners(cube, bone_index, rig):
    _, pivots, worlds, matrices = rig
    size = np.array(cube['size'], float)
    origin = np.array(cube['origin'], float)
    origin[0] = -origin[0] - size[0]
    pivot = np.array(cube.get('pivot', [0, 0, 0]), float) * [-1, 1, 1]
    local = origin + CORNERS * size
    if 'rotation' in cube:
        local = (local - pivot) @ euler(cube['rotation']).T + pivot
    return worlds[bone_index] + (local - pivots[bone_index]) @ matrices[bone_index].T


def is_eye(name):
    return 'eye' in name.lower() and not LID.search(name)


# ------------------------------------------------------------------------------------ placement
def anchor(bones, rig):
    """(skull bone, eye centre on the +x side, eye height) from the rig's own eye (or eyelid) cubes."""
    index = rig[0]
    for test in (is_eye, lambda n: bool(LID.search(n))):
        found = [(bone, corners(cube, index[bone['name']], rig)) for bone in bones if test(bone['name'])
                 for cube in bone.get('cubes', [])]
        if found:
            break
    else:
        return None
    points = np.concatenate([c for _, c in found])
    side = points[points[:, 0] > 0] if np.any(points[:, 0] > 0) else points * [-1, 1, 1]
    center = np.array([np.abs(side[:, 0]).mean(), side[:, 1].mean(), side[:, 2].mean()])
    skull = found[0][0]
    while skull.get('parent') and (is_eye(skull['name']) or LID.search(skull['name'])):
        skull = bones[index[skull['parent']]]
    return skull['name'], center, float(np.ptp(side[:, 1]))


def surface(bones, rig, center, height):
    """Outermost skin beside the eye, from the body cubes (eye and lid cubes excluded).

    Measured on both sides, since fitted rigs are not perfectly symmetric; the outer side wins so
    neither eye ends up inside the head.
    """
    index = rig[0]
    sides = []
    for sign in (1, -1):
        hits = []
        for dy in (-.2, 0, .2):
            for dz in (-.2, 0, .2):
                point = (0.0, center[1] + dy * height, center[2] + dz * height)
                best = None
                for bone in bones:
                    if is_eye(bone['name']) or LID.search(bone['name']):
                        continue
                    for cube in bone.get('cubes', []):
                        span = segment(corners(cube, index[bone['name']], rig), 0, 0, point, (sign, 0.0, 0.0))
                        if span is not None and span[1] > 0 and (best is None or span[1] > best):
                            best = span[1]
                if best is not None:
                    hits.append(best)
        if hits:
            sides.append(float(np.median(hits)))
    if not sides:
        return center[0]
    # Rig eyes often sit deep in the skull, so the skin can be several eye heights further out.
    return float(np.clip(max(sides), center[0] - .3 * height, center[0] + 4.0 * height))


def segment(k, y, z, point=None, direction=(1.0, 0.0, 0.0)):
    """Where a line crosses a cube given by its eight world corners, as (enter, exit) along the line.

    By default the line is (s, y, z): lateral, through the eye.
    """
    origin = k[0]
    frame = np.column_stack([k[1] - k[0], k[3] - k[0], k[4] - k[0]])
    if abs(np.linalg.det(frame)) < 1e-9:
        return None
    inverse = np.linalg.inv(frame)
    start = inverse @ ((np.array([0.0, y, z]) if point is None else np.asarray(point, float)) - origin)
    step = inverse @ np.asarray(direction, float)
    lo, hi = -np.inf, np.inf
    for u, du in zip(start, step):
        if abs(du) < 1e-12:
            if u < 0 or u > 1:
                return None
            continue
        a, b = (0 - u) / du, (1 - u) / du
        lo, hi = max(lo, min(a, b)), min(hi, max(a, b))
    return (lo, hi) if lo <= hi else None


def head_height(bones, rig, center):
    """Height of the head where the eye is: the solid run of body cubes on a vertical line through it.

    Unlike the skull bone's bounds this ignores antlers, crests and frills that do not touch the run.
    """
    index = rig[0]
    point = np.array([center[0] * .5, 0.0, center[2]])
    runs = []
    for bone in bones:
        if is_eye(bone['name']) or LID.search(bone['name']):
            continue
        for cube in bone.get('cubes', []):
            span = segment(corners(cube, index[bone['name']], rig), 0, 0, point, (0.0, 1.0, 0.0))
            if span is not None:
                runs.append(span)
    lo = hi = center[1]
    for a, b in sorted(runs):
        if a <= hi + .05 and b >= lo - .05 and (a <= center[1] + (hi - lo) + 1 or b >= lo):
            lo, hi = min(lo, a), max(hi, b)
    changed = True
    while changed:
        changed = False
        for a, b in runs:
            if a <= hi + .05 and b >= lo - .05 and (a < lo or b > hi):
                lo, hi, changed = min(lo, a), max(hi, b), True
    return hi - lo


# ------------------------------------------------------------------------------------ art
def density(bones):
    """Texels per runtime unit on the head, from the painted UVs."""
    ratios = []
    for bone in bones:
        if not re.search('head|skull|neck3', bone['name'], re.I):
            continue
        for cube in bone.get('cubes', []):
            size = cube['size']
            for key, axes in (('north', (0, 1)), ('east', (2, 1)), ('up', (0, 2))):
                face = cube.get('uv', {}).get(key)
                if isinstance(face, dict) and size[axes[0]] > .5:
                    ratios.append(abs(face['uv_size'][0]) / size[axes[0]])
    return float(np.median(ratios)) if ratios else 1.0


def sprite(width, height, diet, state, pupil, front_left):
    """The eye face: iris, pupil and highlight in whole pixels; transparent corners read as socket."""
    y, x = np.mgrid[0:height, 0:width]
    cx, cy = (width - 1) / 2, (height - 1) / 2
    r = np.sqrt(((x - cx) / (width / 2)) ** 2 + ((y - cy) / (height / 2)) ** 2)
    image = np.zeros((height, width, 3))
    image[:] = SOCKET
    eye = r <= 1.08
    if pupil == 'black':
        image[eye] = rgb('#15181c')
        image[eye & (r > .78)] = rgb('#2c333b')
    elif diet == 'predator':
        inner, outer = (rgb('#f0b43a'), rgb('#9a5d12')) if state == 'calm' else (rgb('#ff7a2e'), rgb('#a8260f'))
        image[eye] = (inner * (1 - np.clip(r, 0, 1))[..., None] + outer * np.clip(r, 0, 1)[..., None])[eye]
        if pupil == 'slit':
            half = max(.5, width * (.09 if state == 'calm' else .05))
            image[eye & (np.abs(x - cx) <= half) & (np.abs(y - cy) <= height * .42)] = rgb('#120d0a')
        else:
            image[eye & (r <= (.34 if state == 'calm' else .24))] = rgb('#120d0a')
    else:
        if state == 'alert':
            image[eye] = rgb('#e3d9c6')                      # white shows around a startled iris
            iris = r <= .62
        else:
            iris = eye
        image[iris] = rgb('#5a3b24')
        image[iris & (r <= .82 * (.62 if state == 'alert' else 1))] = rgb('#6e4a2c')
        image[iris & (r <= (.26 if state == 'alert' else .46))] = rgb('#0d0b0a')
    if pupil != 'black' and width >= 4 and height >= 3:
        hx = int(round(cx - width * .22)) if front_left else int(round(cx + width * .22))
        image[max(0, int(cy - height * .25)), min(width - 1, max(0, hx))] = rgb('#f2eee4')
    return image.astype(np.uint8)


def skin_colour(bones, atlas):
    """Median head colour of one variant atlas, for the lids."""
    pixels = []
    array = np.asarray(atlas.convert('RGB'))
    for bone in bones:
        if not re.search('head|skull|neck3', bone['name'], re.I):
            continue
        for cube in bone.get('cubes', []):
            for face in cube.get('uv', {}).values():
                if isinstance(face, dict):
                    (u, v), (w, h) = face['uv'], face['uv_size']
                    block = array[int(v):int(v + abs(h)), int(u):int(u + abs(w))]
                    if block.size:
                        pixels.append(block.reshape(-1, 3))
    return np.median(np.concatenate(pixels), 0) if pixels else np.array([120, 110, 95], float)


# ------------------------------------------------------------------------------------ geometry
def box(lo, hi, uv, rotation=None, pivot=None):
    """A Bedrock cube from Java-space world bounds inside a world-aligned bone (see add_eyes)."""
    lo, hi = np.asarray(lo, float), np.asarray(hi, float)
    size = hi - lo
    cube = {'origin': [round(-hi[0], 5), round(lo[1], 5), round(lo[2], 5)], 'size': [round(v, 5) for v in size], 'uv': uv}
    if rotation is not None:
        cube['rotation'] = rotation
        cube['pivot'] = [round(-pivot[0], 5), round(pivot[1], 5), round(pivot[2], 5)]
    return cube


def face_uv(rect):
    u, v, w, h = rect
    return {'uv': [u, v], 'uv_size': [w, h]}


def add_eyes(identifier, geometry, atlases, predator):
    """Add the eye bones to one imported geometry and the eye strip to its variant atlases (in place)."""
    model = geometry['minecraft:geometry'][0]
    bones = model['bones']
    if identifier in NO_EYES:
        return atlases
    bones[:] = [b for b in bones if not b['name'].startswith('ark_eye')]
    rig = skeleton(bones)
    found = anchor(bones, rig)
    if found is None:
        return atlases
    skull_name, center, source_height = found
    # The rig's own eye sets the size, kept between a sixth and a third of the head's height there.
    head = head_height(bones, rig, center)
    height = float(np.clip(max(source_height * 1.2, .16 * head), .3, max(.3, .32 * head))) if head > 0 else max(source_height * 1.2, .3)
    diet = 'predator' if predator else 'grazer'
    length = height * (1.35 if predator else 1.15)
    out = surface(bones, rig, center, height)
    PLACEMENT[identifier] = (skull_name, np.round(center, 2).tolist(), round(height, 2), round(out, 2))
    for bone in bones:
        if is_eye(bone['name']):
            bone.pop('cubes', None)  # the sub-pixel eyes; the bone stays for its animation tracks
    rig = skeleton(bones)
    index, pivots, worlds, matrices = rig
    skull = index[skull_name]

    desc = model['description']
    width, base = desc['texture_width'], desc['texture_height']
    px = float(np.clip(3 * density(bones), 2.5, 8))
    sw, sh = int(np.clip(round(length * px), 3, 12)), int(np.clip(round(height * px), 3, 10))
    pupil = 'black' if identifier in BLACK_EYE else 'round' if identifier in ROUND_PUPIL or not predator else 'slit'
    sprites, x = {}, 2
    for state in ('calm', 'alert'):
        for side in ('l', 'r'):
            sprites[state, side] = (x, base + 2, sw, sh)
            x += sw + 2
    socket_rect = (x, base + 2, 2, 2)
    lid_rect = (x + 4, base + 2, 6, 6)
    assert lid_rect[0] + 6 <= width, f'{identifier}: eye strip does not fit'

    # World-aligned bone tree: the pivot sits between the eyes, the rotation cancels the skull's.
    mid = np.array([0.0, center[1], center[2]])
    pivot_model = pivots[skull] + matrices[skull].T @ (mid - worlds[skull])
    cancel = to_bedrock(matrices[skull].T)
    pivot = [round(-pivot_model[0], 5), round(pivot_model[1], 5), round(pivot_model[2], 5)]

    def local(point):
        """World point to the model-space coordinates of a cube in the world-aligned bone."""
        return pivot_model + (np.asarray(point, float) - mid)

    tree = [{'name': 'ark_eyes', 'parent': skull_name, 'pivot': pivot, 'rotation': cancel}]
    depth, pop = max(.6 * height, .5), .06 * height + .05
    for state in ('calm', 'alert'):
        look = EXPRESSIONS[diet, state]
        group = {'name': f'ark_eye_{state}', 'parent': 'ark_eyes', 'pivot': pivot, 'cubes': []}
        tree.append(group)
        for side, sign in (('l', 1), ('r', -1)):
            c = np.array([sign * out, center[1], center[2]])
            inner, outer = sign * (out - depth), sign * (out + pop)
            lo = local([min(inner, outer), c[1] - height / 2, c[2] - length / 2])
            hi = local([max(inner, outer), c[1] + height / 2, c[2] + length / 2])
            ball_pivot = [round(-local(c)[0], 5), round(local(c)[1], 5), round(local(c)[2], 5)]
            socket, iris = face_uv(socket_rect), face_uv(sprites[state, side])
            tree.append({'name': f'ark_eyeball_{state}_{side}', 'parent': group['name'], 'pivot': ball_pivot,
                         'cubes': [box(lo, hi, {'north': socket, 'south': socket, 'up': socket, 'down': socket,
                                                'east': iris, 'west': iris})]})
            lid = {k: face_uv(lid_rect) for k in ('north', 'south', 'east', 'west', 'up', 'down')}
            top = c[1] + height / 2
            lid_in, lid_out = sign * (out - depth * .5), sign * (out + pop + .12 * height)
            hinge = local([sign * out, top, c[2]])
            upper_lo = local([min(lid_in, lid_out), top - look['cover'] * height, c[2] - .58 * length])
            upper_hi = local([max(lid_in, lid_out), top - look['cover'] * height + look['thick'] * height + .12 * height,
                              c[2] + .58 * length])
            group['cubes'].append(box(upper_lo, upper_hi, lid, [-look['slant'], 0, 0], hinge))
            if look['low'] > 0:
                bottom = c[1] - height / 2
                low_lo = local([min(lid_in, lid_out), bottom - .08 * height, c[2] - .55 * length])
                low_hi = local([max(lid_in, lid_out), bottom + look['low'] * height, c[2] + .55 * length])
                group['cubes'].append(box(low_lo, low_hi, lid, [look['slant'] * .4, 0, 0], local([sign * out, bottom, c[2]])))
    bones.extend(tree)
    desc['texture_height'] = base + STRIP

    result = {}
    for variant, atlas in atlases.items():
        canvas = Image.new('RGBA', (width, base + STRIP), (0, 0, 0, 0))
        canvas.paste(atlas.convert('RGBA'), (0, 0))
        pixels = np.array(canvas)
        for (state, side), (u, v, w, h) in sprites.items():
            art = sprite(w, h, diet, state, pupil, front_left=(side == 'l'))
            pixels[v:v + h, u:u + w, :3], pixels[v:v + h, u:u + w, 3] = art, 255
        u, v, w, h = socket_rect
        pixels[v:v + h, u:u + w] = (*SOCKET, 255)
        tone = skin_colour(bones, atlas)
        u, v, w, h = lid_rect
        rng = np.random.default_rng(len(identifier) * 7919 + len(variant))
        shades = np.array([-6, -3, 0, 3, 6])[rng.integers(0, 5, (2, 2))].repeat(3, 0).repeat(3, 1)
        pixels[v:v + h, u:u + w, :3] = np.clip(tone * .9 + shades[..., None], 0, 255)
        pixels[v:v + h, u:u + w, 3] = 255
        result[variant] = Image.fromarray(pixels)
    return result
