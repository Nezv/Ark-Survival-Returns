"""Creature eyes as their own geometry (dashboard X02), isolated from the painted bodies.

import_creatures.py calls add_eyes() for every species while importing: the body cubes, their UVs and
the painted skin stay untouched. The old sub-pixel eye cubes are emptied (their bones stay, animations
still target them) and a separate bone tree is added under the skull. Eye art lives in a strip appended
below each variant atlas, so body UVs never move.

The big carnivores are designed one by one (DESIGNS): each eye has its own frame, set into the head where
its skin is, with eyes that move.

    ark_eyes                          rest rotation cancels the skull's, so the eyes are built world-aligned
      ark_eye_l / _r                  the eye's frame: x out of the head, y up, z towards the snout
        ark_eyeball_calm_l            the yellow eye, an iris drawn at several texels per unit
        ark_eyeball_hunt_l            the red eye of a hunt or a fight
        ark_eye_pupil_l               a slit the renderer narrows and widens (scale z)
        ark_eye_lid_upper_l / lower   lids the renderer scales shut (scale y from their outer edge)
        ark_eye_brow_l                a brow ridge the renderer lowers and tilts

client/CreatureEyes drives them from the synced state: blinks, sleep, torpor and death close the eyes, a
hunt narrows them under a lowered brow. The other species keep the generated sets (procedural):

    ark_eyes
      ark_eye_calm                  predatory (carnivore) or calm (herbivore) lids
        ark_eyeball_calm_l / _r     eyeballs
      ark_eye_alert                 the attacking/defending variant, hidden unless the creature is alarmed
        ark_eyeball_alert_l / _r

Big carnivores and raptors (HUNTING_EYES) also get <id>_eyes.png, the texture of their red hunting eyes:
the vanilla spider's method, a sheet the size of the atlas that is transparent except for the irises, drawn
over the model with the eyes render type (client/HuntingEyesLayer). Run this file to rebuild the designed
eyes and rewrite those sheets from the imported geometry without a full import.
"""
import json
import math
import re
from pathlib import Path
import numpy as np
from PIL import Image
import geckolib_mesh

CORNERS = np.array([[0, 0, 0], [1, 0, 0], [1, 1, 0], [0, 1, 0], [0, 0, 1], [1, 0, 1], [1, 1, 1], [0, 1, 1]], float)
LID = re.compile(r'eyelid|lidmain|lid_|_lid|uppereye|lowereye|eyeupper|eyelower|top_eyelid|bot_eyelid', re.I)
NO_EYES = {'cnidaria', 'tusoteuthis', 'dragon'}  # no eye anchor in the rig; the dragon is replaced by I10
STRIP = 16                                       # rows appended below each atlas
# Pupil shape by body plan: slit for reptiles and archosaurs, round for mammals and birds, black for sharks.
ROUND_PUPIL = {'direwolf', 'sabertooth', 'ravager', 'mammoth', 'megalocerus', 'paraceratherium', 'unicorn',
               'megapithecus', 'argentavis', 'terrorbird', 'archaeopteryx'}
BLACK_EYE = {'megalodon'}
# Eyes that burn red while hunting; the same species as Species.glowingEyes() (CollectionGameTests checks it).
HUNTING_EYES = {'tyrannosaurus', 'giganotosaurus', 'spinosaurus', 'ceratosaurus', 'acrocanthosaurus', 'carnotaurus',
                'velociraptor'}

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
    """Species ids whose `predator` flag is set in Species.java."""
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


def predator_pupil(width, height, state, pupil):
    """Pixels of a carnivore's pupil: a vertical slit or a disc, narrower when alert."""
    y, x = np.mgrid[0:height, 0:width]
    cx, cy = (width - 1) / 2, (height - 1) / 2
    if pupil == 'slit':
        half = max(.5, width * (.09 if state == 'calm' else .05))
        return (np.abs(x - cx) <= half) & (np.abs(y - cy) <= height * .42)
    r = np.sqrt(((x - cx) / (width / 2)) ** 2 + ((y - cy) / (height / 2)) ** 2)
    return r <= (.34 if state == 'calm' else .24)


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
        image[eye & predator_pupil(width, height, state, pupil)] = rgb('#120d0a')
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


def glow_sprite(width, height, state, pupil):
    """The same eye face for the hunting glow: the iris in spider-eye red, pupil and socket transparent."""
    y, x = np.mgrid[0:height, 0:width]
    cx, cy = (width - 1) / 2, (height - 1) / 2
    r = np.sqrt(((x - cx) / (width / 2)) ** 2 + ((y - cy) / (height / 2)) ** 2)
    image = np.zeros((height, width, 4))
    iris = (r <= 1.08) & ~predator_pupil(width, height, state, pupil)
    blend = np.clip(r, 0, 1)[..., None]
    image[..., :3] = rgb('#ff5a44') * (1 - blend) + rgb('#d01212') * blend
    image[..., 3] = iris * 255
    return image.astype(np.uint8)


def hunting_eyes(width, height, sprites, pupil):
    """The eyes texture of a hunter: its four irises in red on a transparent sheet the size of the atlas."""
    pixels = np.zeros((height, width, 4), np.uint8)
    for (state, _), (u, v, w, h) in sprites.items():
        pixels[v:v + h, u:u + w] = glow_sprite(w, h, state, pupil)
    return Image.fromarray(pixels)


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
    """Add the eye bones to one imported geometry and the eye strip to its variant atlases (in place).

    A hunter's eyes texture comes back with the atlases, under the name 'eyes'.
    """
    model = geometry['minecraft:geometry'][0]
    bones = model['bones']
    if identifier in DESIGNS:
        return add_designed_eyes(identifier, geometry, atlases)
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
    if identifier in HUNTING_EYES:
        result['eyes'] = hunting_eyes(width, base + STRIP, sprites, pupil)
    return result


# ------------------------------------------------------------------------------------ designed eyes
# The lid contract shared with client/CreatureEyes.java. A lid is built (rest + tuck) eye heights long, hinged
# on the edge it tucks away past the opening (above for the upper lid, below for the lower); the client reads
# the species' own rest off that length and scales the lid from its hinge, so a lid at closure c covers
# lid_cover(c, rest): the rest at 0, the whole shut cover at 1, less than the rest below 0.
LID_TUCK = {'upper': 0.25, 'lower': 0.15}
LID_SHUT = {'upper': 0.70, 'lower': 0.44}
PUPIL_GAP = 0.12        # units the pupil stands in front of the iris: clear of depth fighting at range
SOCKET_WET, SOCKET_DEEP = '#2a1510', '#100806'
# Iris colours from the pupil out to the rim, per species: the calm yellow and the red of a hunt or a fight.
REX_GOLD = [(0.00, '#fff4b8'), (0.16, '#ffd95a'), (0.42, '#f9b62c'), (0.70, '#ec8c1e'), (0.88, '#a8520f'), (1.0, '#3c1c06')]
REX_RED = [(0.00, '#ffd88a'), (0.16, '#ffa04a'), (0.42, '#ff5a2a'), (0.70, '#e0261a'), (0.88, '#8c0e0c'), (1.0, '#2c0505')]
GIGA_LEMON = [(0.00, '#fffde4'), (0.16, '#fff482'), (0.42, '#f4e24c'), (0.70, '#d2b82e'), (0.88, '#7e6c16'), (1.0, '#2a2406')]
GIGA_RED = [(0.00, '#ffe4b8'), (0.16, '#ff9e70'), (0.42, '#ff4c3c'), (0.70, '#e01c2c'), (0.88, '#8a0a18'), (1.0, '#2a0408')]
SPINO_OLIVE = [(0.00, '#f8f2b0'), (0.16, '#ecdc70'), (0.42, '#d2c04a'), (0.70, '#a69632'), (0.88, '#5e5218'), (1.0, '#1e1a06')]
SPINO_RED = [(0.00, '#ffcc94'), (0.16, '#ff7e44'), (0.42, '#e63a20'), (0.70, '#b41612'), (0.88, '#640808'), (1.0, '#200303')]
CERATO_AMBER = [(0.00, '#fff2b4'), (0.16, '#ffd04c'), (0.42, '#ffaa2c'), (0.70, '#f2821c'), (0.88, '#a6480e'), (1.0, '#3a1404')]
CERATO_RED = [(0.00, '#ffe2a4'), (0.16, '#ffae54'), (0.42, '#ff6c2c'), (0.70, '#ea3c16'), (0.88, '#921c08'), (1.0, '#300604')]
ACRO_BRONZE = [(0.00, '#fde8ac'), (0.16, '#f4c65e'), (0.42, '#dea03a'), (0.70, '#b87828'), (0.88, '#704014'), (1.0, '#261204')]
ACRO_RED = [(0.00, '#ffd4a4'), (0.16, '#ff8e54'), (0.42, '#ea4a2c'), (0.70, '#ba2618'), (0.88, '#6c100c'), (1.0, '#220404')]
CARNO_FIRE = [(0.00, '#fffac4'), (0.16, '#ffe44c'), (0.42, '#ffb82c'), (0.70, '#ff7c1e'), (0.88, '#ba380e'), (1.0, '#3c0c04')]
CARNO_RED = [(0.00, '#fff6b4'), (0.16, '#ffc444'), (0.42, '#ff6c1c'), (0.70, '#f02c12'), (0.88, '#9c0e08'), (1.0, '#300404')]

def _hex(code):
    return np.array([int(code[i:i + 2], 16) for i in (1, 3, 5)], float)


def palette(light, mid, deep, rim):
    """Iris colours from the pupil out to the rim from four colours: the light round the pupil, the body of the
    iris, its deep outer part and the dark limbal ring."""
    a, b, c, d = (_hex(code) for code in (light, mid, deep, rim))

    def code(colour):
        return '#%02x%02x%02x' % tuple(int(round(v)) for v in np.clip(colour, 0, 255))

    return [(0.00, code(a)), (0.16, code(a * .6 + b * .4)), (0.42, code(b)), (0.70, code(b * .5 + c * .5)), (0.88, code(c)), (1.0, code(d))]


# One design per predator, sea animal and flyer, each looked at in previews (tools/preview_creature_eyes.py):
#   skull, centre     the skull bone, and the eye's height and place along the head (y, z in rest-pose units)
#   level             the head's pitch in its idle clip against the rest pose, so that level means the idle's
#   size, gap         length and height of the opening (units); the face stands gap above the skin it covers
#   yaw, pitch, roll  the eye turned towards the snout and up (degrees), its long axis tilted front corner down
#   almond            the opening's curve: 0.5 is an ellipse, higher has sharper corners
#   px, iris          iris texels per unit; colours (calm, and hunt: none for an eye that never turns), radius
#                     in opening lengths, and the pattern: fibres along the radius, a crocodile's dark net,
#                     flecks (with fibres), or glass (a shark's black eye); amount of it; sclera, the colour the
#                     opening shows past the iris (none: the dark socket); glint, the size of the highlight
#   pupil             a slit (width and height at scale 1 as shares of the opening, and its taper: higher is
#                     sharper), a round pupil (its share of the iris' diameter) or none
#   rest, lids        how far each lid lies over the eye at rest, in eye heights; and for each lid that there is
#                     (none: no lid, as a snake has none), its protrusion out of the face (eye heights) and
#                     slant (degrees, front end down)
#   brow, crest       boxes (z from the eye centre in lengths, y above the opening in heights, x out of the face in
#                     heights): the brow ridge, which moves, with its slant (degrees, front end down), or none;
#                     the crest, bone that stays (a boss, a ridge, or pale horns when horn is set)
#   skip, skin        a bone-name pattern the skin under the eye is not measured on (a frill in the way), and a
#                     skin position (x) to take instead of measuring
DESIGN_DEFAULTS = dict(level=0, gap=0.22, almond=0.7, yaw=4, pitch=5, roll=3, rest=dict(upper=0.30, lower=0.12),
                       lids=dict(upper=(0.07, 6), lower=(0.05, -3)), brow=None, crest=(), horn=False, skip=None, skin=None)
IRIS_DEFAULTS = dict(hunt=None, pattern='fibres', amount=1.0, sclera=None, glint=1.0)


def design_of(identifier):
    """A species' design with the defaults filled in."""
    design = {**DESIGN_DEFAULTS, **DESIGNS[identifier]}
    design['iris'] = {**IRIS_DEFAULTS, **design['iris']}
    return design


def lid_cover(closure, rest, shut):
    """The share of the eye's height a lid covers at a closure (client/CreatureEyes.cover)."""
    return rest + closure * (shut - rest) if closure >= 0 else rest * (1 + closure)


DESIGNS = {
    # The king: deep gold under a heavy brow, a rugose boss behind it.
    'tyrannosaurus': dict(
        skull='Cnt_Head_JNT_SKL', centre=(191.3, 141.6), level=0, size=(12.5, 8.2), gap=0.25,
        yaw=4, pitch=6, roll=3, almond=0.7, px=3.4,
        iris=dict(calm=REX_GOLD, hunt=REX_RED, radius=0.47, pattern='fibres', amount=1.2),
        pupil=(0.14, 0.88, 0.8), lids=dict(upper=(0.07, 6), lower=(0.05, -3)),
        brow=dict(slant=5, boxes=[((-0.85, 0.65), (-0.02, 0.34), (-0.9, 0.18)), ((-0.80, 0.45), (0.30, 0.50), (-0.9, 0.09))]),
        crest=[((-1.00, -0.30), (0.10, 0.64), (-0.9, 0.16)), ((-0.88, -0.46), (0.60, 0.80), (-0.9, 0.09))], horn=False),
    # The berserker: a pale lemon stare with a thin slit, under a long jagged ridge.
    'giganotosaurus': dict(
        skull='c_head', centre=(215.85, 175.06), level=0, size=(14.0, 9.0), gap=0.25,
        yaw=4, pitch=5, roll=3, almond=0.6, px=3.2,
        iris=dict(calm=GIGA_LEMON, hunt=GIGA_RED, radius=0.47, pattern='flecks', amount=1.0),
        pupil=(0.11, 0.92, 0.9), lids=dict(upper=(0.07, 8), lower=(0.05, -4)),
        brow=dict(slant=7, boxes=[((-0.90, 0.75), (-0.02, 0.32), (-0.9, 0.18)), ((-0.85, 0.55), (0.28, 0.44), (-0.9, 0.09))]),
        crest=[((0.22, 0.52), (0.10, 0.50), (-0.9, 0.15)), ((-0.18, 0.12), (0.10, 0.60), (-0.9, 0.16)),
               ((-0.62, -0.32), (0.10, 0.48), (-0.9, 0.14))], horn=False),
    # The river hunter: an olive-gold crocodile's eye, netted, with a hairline slit, set high under a low ridge.
    'spinosaurus': dict(
        skull='c_head', centre=(139.8, 164.8), level=0, size=(10.0, 6.6), gap=0.25,
        yaw=3, pitch=8, roll=2, almond=0.8, px=4.0,
        iris=dict(calm=SPINO_OLIVE, hunt=SPINO_RED, radius=0.47, pattern='net', amount=1.0),
        pupil=(0.09, 0.94, 1.0), lids=dict(upper=(0.06, 4), lower=(0.05, -2)),
        brow=dict(slant=4, boxes=[((-0.70, 0.55), (-0.02, 0.30), (-0.9, 0.16)), ((-0.62, 0.35), (0.26, 0.40), (-0.9, 0.08))]),
        crest=[((-0.66, -0.30), (0.10, 0.46), (-0.9, 0.12))], horn=False),
    # The horned stalker: flecked amber behind a hornlet.
    'ceratosaurus': dict(
        skull='Head_M', centre=(75.33, 82.19), level=6.4, size=(6.6, 4.4), gap=0.2,
        yaw=4, pitch=5, roll=3, almond=0.7, px=6.0,
        iris=dict(calm=CERATO_AMBER, hunt=CERATO_RED, radius=0.47, pattern='flecks', amount=1.3),
        pupil=(0.13, 0.88, 0.8), lids=dict(upper=(0.08, 6), lower=(0.06, -3)),
        brow=dict(slant=6, boxes=[((-0.75, 0.55), (-0.02, 0.34), (-0.9, 0.18)), ((-0.70, 0.10), (0.30, 0.48), (-0.9, 0.09))]),
        crest=[((0.10, 0.50), (0.10, 0.72), (-0.9, 0.16)), ((0.18, 0.42), (0.70, 0.96), (-0.9, 0.08))], horn=True),
    # The ridge-back: bronze, a rounder slit, a ridge running on towards the snout.
    'acrocanthosaurus': dict(
        skull='upperFaceJoint_M', centre=(198.42, 200.04), level=8.3, size=(12.0, 8.0), gap=0.25,
        yaw=4, pitch=5, roll=3, almond=0.65, px=3.5,
        iris=dict(calm=ACRO_BRONZE, hunt=ACRO_RED, radius=0.47, pattern='fibres', amount=1.0),
        pupil=(0.15, 0.86, 0.6), lids=dict(upper=(0.07, 5), lower=(0.05, -3)),
        brow=dict(slant=5, boxes=[((-0.75, 0.60), (-0.02, 0.32), (-0.9, 0.16)), ((-0.70, 0.45), (0.28, 0.44), (-0.9, 0.08))]),
        crest=[((0.45, 1.25), (-0.06, 0.26), (-0.9, 0.14))], horn=False),
    # The bull: a fiery glare under a scowl, horns over the eyes.
    'carnotaurus': dict(
        skull='c_head', centre=(89.07, 73.23), level=0, size=(7.2, 4.8), gap=0.2,
        yaw=6, pitch=4, roll=4, almond=0.75, px=5.5,
        iris=dict(calm=CARNO_FIRE, hunt=CARNO_RED, radius=0.47, pattern='fibres', amount=1.4),
        pupil=(0.12, 0.90, 0.85), lids=dict(upper=(0.08, 9), lower=(0.06, -4)),
        brow=dict(slant=9, boxes=[((-0.80, 0.62), (-0.02, 0.42), (-0.9, 0.22)), ((-0.72, 0.40), (0.38, 0.56), (-0.9, 0.11))]),
        crest=[((-0.56, 0.0), (0.20, 0.95), (-0.6, 0.40)), ((-0.46, -0.10), (0.48, 0.86), (0.40, 0.92))], horn=True),

    # ---------------------------------------------------------------- the other predators
    # The pack hunter: a bright amber stare under a thin brow, a small ridge before the eye.
    'velociraptor': dict(
        skull='Cnt_Head_JNT_SKL', centre=(43.9, 22.9), level=5, size=(3.0, 2.0), gap=0.18,
        yaw=6, pitch=4, roll=3, almond=0.75, px=9,
        iris=dict(calm=palette('#fff4b0', '#f7b22a', '#b8620f', '#3a1a06'), hunt=palette('#ffd890', '#ff4a22', '#8a0c0c', '#2a0404'),
                  radius=0.47, pattern='fibres', amount=1.0),
        pupil=(0.12, 0.90, 0.9), brow=dict(slant=6, boxes=[((-0.80, 0.60), (-0.02, 0.30), (-0.9, 0.16))]),
        crest=[((0.10, 0.50), (0.14, 0.50), (-0.9, 0.10))]),
    # The spitter: a venomous green-yellow stare, a knobbed brow.
    'dilophosaur': dict(
        skull='c_head', centre=(27.5, 25.3), level=-5.4, size=(2.2, 1.5), gap=0.16,
        yaw=8, pitch=4, roll=3, almond=0.75, px=12,
        iris=dict(calm=palette('#f6ffb4', '#b8de2e', '#4e7c12', '#14260a'), hunt=palette('#ffd890', '#ff6a1e', '#a02608', '#2a0804'),
                  radius=0.47, pattern='fibres', amount=1.0),
        pupil=(0.10, 0.90, 0.9), brow=dict(slant=6, boxes=[((-0.80, 0.60), (-0.02, 0.32), (-0.9, 0.18))]), crest=[]),
    # The cousin: copper, two bony hornlets before a ridge.
    'allosaurus': dict(
        skull='c_head', centre=(85.8, 83.1), level=-1.7, size=(7.0, 4.6), gap=0.22,
        yaw=5, pitch=5, roll=3, almond=0.7, px=5,
        iris=dict(calm=palette('#ffe4a8', '#e8902a', '#9a4a14', '#321404'), hunt=palette('#ffc890', '#ff5a20', '#8e1408', '#2a0604'),
                  radius=0.47, pattern='fibres', amount=1.1),
        pupil=(0.13, 0.88, 0.8), brow=dict(slant=6, boxes=[((-0.80, 0.62), (-0.02, 0.34), (-0.9, 0.18))]),
        crest=[((0.15, 0.45), (0.30, 0.75), (-0.9, 0.10)), ((-0.45, -0.05), (0.30, 0.62), (-0.9, 0.10))], horn=True),
    # The boar-faced crocodile: amber and net, high on the skull.
    'kaprosuchus': dict(
        skull='c_neck3', centre=(18.8, 33.6), level=0, size=(2.6, 1.7), gap=0.16,
        yaw=3, pitch=8, roll=2, almond=0.8, px=11,
        iris=dict(calm=palette('#fff6a8', '#d8b82c', '#7a6a14', '#241e06'), hunt=palette('#ffd090', '#ee5a1e', '#7e1208', '#240404'),
                  radius=0.47, pattern='net', amount=1.0),
        pupil=(0.09, 0.94, 1.0), lids=dict(upper=(0.12, 4), lower=(0.05, -2)),
        brow=dict(slant=3, boxes=[((-0.70, 0.55), (-0.02, 0.34), (-0.9, 0.20))]), crest=[((-0.60, -0.10), (0.20, 0.55), (-0.9, 0.12))]),
    'sarco': dict(
        skull='c_neck3', centre=(27.6, 57.1), level=-3.9, size=(3.6, 2.3), gap=0.18,
        yaw=3, pitch=8, roll=2, almond=0.8, px=8,
        iris=dict(calm=palette('#f4f0a0', '#c2b03a', '#6c5c1a', '#1e1806'), hunt=palette('#ffcc94', '#e63a20', '#7a1010', '#200303'),
                  radius=0.47, pattern='net', amount=1.0),
        pupil=(0.09, 0.94, 1.0), lids=dict(upper=(0.12, 4), lower=(0.05, -2)),
        brow=dict(slant=3, boxes=[((-0.70, 0.55), (-0.02, 0.34), (-0.9, 0.20))]), crest=[((-0.65, -0.05), (0.20, 0.55), (-0.9, 0.12))]),
    'deinosuchus': dict(
        skull='Head', centre=(44.3, 56.8), level=-1.6, size=(3.8, 2.4), gap=0.18,
        yaw=3, pitch=8, roll=2, almond=0.8, px=12,
        iris=dict(calm=palette('#f2e0a0', '#cf9a34', '#80521a', '#2a1a08'), hunt=palette('#ffc890', '#e04a1e', '#781410', '#220404'),
                  radius=0.47, pattern='net', amount=1.0),
        pupil=(0.09, 0.94, 1.0), lids=dict(upper=(0.12, 4), lower=(0.05, -2)),
        brow=dict(slant=3, boxes=[((-0.70, 0.55), (-0.02, 0.34), (-0.9, 0.20))]), crest=[((-0.65, -0.05), (0.20, 0.55), (-0.9, 0.12))]),
    # The constrictor: a gold razor stare under a scaly brow, no lids.
    'titanoboa': dict(
        skull='c_head', centre=(12.1, 97.1), level=0, size=(1.5, 1.0), gap=0.12,
        yaw=6, pitch=10, roll=0, almond=0.6, px=18,
        iris=dict(calm=palette('#fff4b0', '#ffc42e', '#c47012', '#3a1a06'), hunt=palette('#ffd890', '#ff5a1e', '#a02608', '#2a0804'),
                  radius=0.5, pattern='fibres', amount=1.0),
        pupil=(0.09, 0.95, 1.0), lids={}, crest=[((-0.50, 0.50), (0.08, 0.30), (-0.9, 0.10))]),
    # ---------------------------------------------------------------- mammals: a round pupil and a frown
    'direwolf': dict(
        skull='c_head', centre=(21.2, 20.7), level=0, size=(1.9, 1.5), gap=0.16,
        yaw=12, pitch=4, roll=3, almond=0.5, px=15,
        iris=dict(calm=palette('#fff2bc', '#e2aa30', '#8c5c16', '#2c1c08'), hunt=palette('#ffd8a0', '#ff6a24', '#9c2008', '#2a0804'),
                  radius=0.34, pattern='fibres', amount=1.0, sclera='#2c1e16'),
        pupil=0.42, brow=dict(slant=4, boxes=[((-0.70, 0.60), (0.00, 0.40), (-0.9, 0.20))]), crest=[]),
    'sabertooth': dict(
        skull='c_head', centre=(18.6, 20.6), level=0, size=(2.1, 1.7), gap=0.16,
        yaw=12, pitch=4, roll=3, almond=0.5, px=14,
        iris=dict(calm=palette('#f6f8b6', '#d4cc3a', '#7c7a18', '#22240a'), hunt=palette('#ffd8a0', '#ff6a24', '#9c2008', '#2a0804'),
                  radius=0.36, pattern='fibres', amount=1.0, sclera='#2a2018'),
        pupil=0.44, brow=dict(slant=5, boxes=[((-0.75, 0.65), (0.00, 0.44), (-0.9, 0.22))]), crest=[]),
    'ravager': dict(
        skull='Head', centre=(19.9, 26.6), level=-2.8, size=(1.9, 1.5), gap=0.16,
        yaw=12, pitch=4, roll=3, almond=0.5, px=15,
        iris=dict(calm=palette('#fff8c4', '#e6de50', '#8a8820', '#2a2a0a'), hunt=palette('#ffd8a0', '#ff5a1e', '#940c08', '#2a0604'),
                  radius=0.34, pattern='flecks', amount=1.0, sclera='#2a1e18'),
        pupil=0.40, brow=dict(slant=5, boxes=[((-0.70, 0.60), (0.00, 0.40), (-0.9, 0.20))]), crest=[]),
    'megapithecus': dict(
        skull='c_head', centre=(45.8, 20.6), level=0, size=(3.4, 2.7), gap=0.2,
        yaw=6, pitch=2, roll=2, almond=0.5, px=9,
        iris=dict(calm=palette('#f0c080', '#8e5c2c', '#4c2e16', '#1a0e06'), hunt=palette('#ffd89a', '#e8501c', '#7a1408', '#240804'),
                  radius=0.3, pattern='fibres', amount=0.8, sclera='#3c2c24'),
        pupil=0.40, brow=dict(slant=3, boxes=[((-0.90, 0.80), (0.00, 0.55), (-0.9, 0.30))]),
        crest=[((-1.10, 1.00), (0.40, 0.95), (-0.9, 0.40))]),
    # ---------------------------------------------------------------- the terror bird
    'terrorbird': dict(
        skull='c_head', centre=(28.1, 21.5), level=7.3, size=(1.3, 1.2), gap=0.12,
        yaw=6, pitch=2, roll=0, almond=0.5, px=22, rest=dict(upper=0.12, lower=0.22),
        iris=dict(calm=palette('#fff6a8', '#f0c82a', '#c27014', '#3a1c06'), hunt=palette('#ffd890', '#ff5a1e', '#a02608', '#2a0804'),
                  radius=0.40, pattern='fibres', amount=0.5),
        pupil=0.40, lids=dict(upper=(0.05, 4), lower=(0.07, -4)), crest=[((-0.70, 0.60), (0.12, 0.40), (-0.9, 0.14))]),
    # ---------------------------------------------------------------- the sea
    'plesiosaur': dict(
        skull='c_neck8', centre=(35.1, 205.1), level=1.2, size=(5.2, 4.4), gap=0.2,
        yaw=4, pitch=4, roll=0, almond=0.5, px=7, rest=dict(upper=0.18, lower=0.10),
        iris=dict(calm=palette('#f4e8a0', '#a88a2c', '#4a3c12', '#14100a'), hunt=palette('#ffc890', '#c82a18', '#6a0c0c', '#200404'),
                  radius=0.40, pattern='fibres', amount=0.8),
        pupil=0.5, lids=dict(upper=(0.05, 3), lower=(0.04, -2))),
    'megalodon': dict(
        skull='c_head', centre=(23.2, 52.2), level=0, size=(4.2, 3.6), gap=0.2,
        yaw=4, pitch=4, roll=0, almond=0.5, px=9,
        iris=dict(calm=palette('#46505c', '#222c38', '#0e141c', '#05080c'), radius=0.5, pattern='glass', amount=1.0),
        pupil=None, lids={}),
    'liopleurodon': dict(
        skull='c_neck8', centre=(31.3, 39.8), level=0, size=(3.8, 2.9), gap=0.18,
        yaw=4, pitch=4, roll=0, almond=0.6, px=8, rest=dict(upper=0.18, lower=0.10),
        iris=dict(calm=palette('#f4eea0', '#c6b43a', '#6a5c18', '#1e1a08'), hunt=palette('#ffc890', '#d82a18', '#700c0c', '#200404'),
                  radius=0.45, pattern='fibres', amount=1.0),
        pupil=(0.16, 0.88, 0.6), lids=dict(upper=(0.05, 3), lower=(0.04, -2)),
        brow=dict(slant=3, boxes=[((-0.70, 0.55), (-0.02, 0.28), (-0.9, 0.14))])),
    'mosasaurus': dict(
        skull='c_head', centre=(53.1, 79.2), level=0, size=(4.0, 3.0), gap=0.2,
        yaw=4, pitch=4, roll=0, almond=0.55, px=8, rest=dict(upper=0.2, lower=0.10),
        iris=dict(calm=palette('#f6f4a4', '#c8d04c', '#6c7a1a', '#1c2406'), hunt=palette('#ffc890', '#d82a18', '#700c0c', '#200404'),
                  radius=0.42, pattern='fibres', amount=1.0),
        pupil=0.40, lids=dict(upper=(0.06, 4), lower=(0.05, -2)),
        brow=dict(slant=4, boxes=[((-0.70, 0.55), (-0.02, 0.30), (-0.9, 0.16))])),
    # ---------------------------------------------------------------- the flyers: round pupils, lids that close from below
    'pteranodon': dict(
        skull='c_head', centre=(19.3, 18.6), level=21.8, size=(3.8, 3.4), gap=0.15, skip='wing',
        yaw=6, pitch=2, roll=0, almond=0.5, px=8, rest=dict(upper=0.12, lower=0.22),
        iris=dict(calm=palette('#ffeaa4', '#f6a232', '#b0580e', '#301606'), radius=0.40, pattern='fibres', amount=0.6),
        pupil=0.40, lids=dict(upper=(0.05, 4), lower=(0.07, -4)), crest=[((-0.80, 0.50), (0.12, 0.40), (-0.9, 0.12))]),
    'quetzal': dict(
        skull='c_head', centre=(57.9, 92.8), level=21.8, size=(6.2, 5.4), gap=0.18, skip='wing',
        yaw=6, pitch=2, roll=0, almond=0.5, px=5, rest=dict(upper=0.12, lower=0.22),
        iris=dict(calm=palette('#fff2b4', '#eab232', '#a06414', '#301a06'), radius=0.40, pattern='fibres', amount=0.6),
        pupil=0.40, lids=dict(upper=(0.05, 4), lower=(0.07, -4)), crest=[((-0.80, 0.50), (0.12, 0.40), (-0.9, 0.12))]),
    'argentavis': dict(
        skull='c_Head', centre=(42.4, 26.9), level=12.3, size=(3.0, 2.7), gap=0.14,
        yaw=8, pitch=2, roll=0, almond=0.5, px=10, rest=dict(upper=0.14, lower=0.22),
        iris=dict(calm=palette('#f6d680', '#b87c2c', '#6a3c14', '#20120a'), radius=0.40, pattern='fibres', amount=0.6),
        pupil=0.40, lids=dict(upper=(0.05, 4), lower=(0.07, -4)), crest=[((-1.00, 0.90), (0.20, 0.70), (-0.9, 0.34))]),
    'archaeopteryx': dict(
        skull='Head_Top', centre=(7.2, 5.1), level=2.1, size=(0.65, 0.6), gap=0.05,
        yaw=6, pitch=2, roll=0, almond=0.5, px=36, rest=dict(upper=0.14, lower=0.22),
        iris=dict(calm=palette('#ffe49a', '#f08c2a', '#a2420e', '#301004'), radius=0.40, pattern='fibres', amount=0.4),
        pupil=0.45, lids=dict(upper=(0.05, 4), lower=(0.06, -4))),
}


def smoothstep(edge0, edge1, x):
    t = np.clip((x - edge0) / (edge1 - edge0), 0, 1)
    return t * t * (3 - 2 * t)


def ramp(r, stops):
    """Colour along stops [(radius, '#rrggbb')] at the radii r."""
    radii = np.array([s for s, _ in stops])
    colours = np.array([_hex(c) for _, c in stops])
    return np.stack([np.interp(r, radii, colours[:, k]) for k in range(3)], -1)


def eye_frame(design):
    """Columns: out of the head, up, towards the snout, for the +x eye (Java space, rest pose)."""
    level = math.radians(design['level'])
    o, u, f = np.array([1.0, 0, 0]), np.array([0, math.cos(level), -math.sin(level)]), np.array([0, math.sin(level), math.cos(level)])
    yaw, pitch, roll = (math.radians(design[k]) for k in ('yaw', 'pitch', 'roll'))
    o, f = math.cos(yaw) * o + math.sin(yaw) * f, -math.sin(yaw) * o + math.cos(yaw) * f
    o, u = math.cos(pitch) * o + math.sin(pitch) * u, -math.sin(pitch) * o + math.cos(pitch) * u
    f, u = math.cos(roll) * f - math.sin(roll) * u, math.sin(roll) * f + math.cos(roll) * u
    return np.column_stack([o, u, f])


def almond(w, h, shape):
    """The opening drawn on the eye's face: inside it, and how many texels a texel lies outside its edge."""
    y, x = np.mgrid[0:h, 0:w] + 0.5
    across, along = (x - w / 2) / (w / 2), (y - h / 2) / (h / 2)
    half = np.clip(1 - np.abs(across) ** 2, 0, None) ** shape
    outside = (np.abs(along) - half) * h / 2
    return outside <= 0, outside


def iris_art(w, h, stops, radius, centre, seed, glow=False, skin=None, shape=0.7, pattern='fibres', amount=1.0, sclera=None, glint=1.0):
    """The eye's face, front corner on the left: an almond opening (corners pointed, skin round it, a dark line
    along its edge) holding a round iris (radius in texels) with a dark rim, fibres along the radius, a ruffled
    ring round the pupil, the lid's shadow along the top and a glint; past the iris, the sclera or the wet
    socket. glow: the same iris as an eyes-layer sheet (RGBA, unshaded, no glint)."""
    y, x = np.mgrid[0:h, 0:w] + 0.5
    cx, cy = centre
    r = np.hypot(x - cx, y - cy) / radius
    angle = np.arctan2(y - cy, x - cx)
    opening, outside = almond(w, h, shape)
    rng = np.random.default_rng(seed)
    colour = ramp(r, stops)
    glass = pattern == 'glass'
    fibres = np.zeros_like(r)
    for k in range(0 if glass else int((12 + 1.6 * radius) * amount)):
        at, start, end = rng.uniform(-np.pi, np.pi), rng.uniform(.16, .36), rng.uniform(.6, .95)
        strength = rng.uniform(.10, .22) * (1 if k % 3 == 0 else -1)
        width = .6 / np.maximum(r * radius, 1.0)
        off = np.angle(np.exp(1j * (angle - at)))
        fibres += strength * np.exp(-(off / width) ** 2) * smoothstep(start, start + .08, r) * (1 - smoothstep(end - .1, end, r))
    colour *= (1 + np.clip(fibres, -.35, .3))[..., None]
    annulus = (r > .3) & (r < .97)
    if pattern == 'net':
        # A crocodile's iris: a dark net of cells round the pupil.
        count = int(np.clip(.3 * radius * radius, 16, 90))
        spread, spin = rng.uniform(.2, 1.05, count) * radius, rng.uniform(-np.pi, np.pi, count)
        seeds_x, seeds_y = cx + spread * np.cos(spin), cy + spread * np.sin(spin)
        distance = np.sort(np.hypot(x[..., None] - seeds_x, y[..., None] - seeds_y), -1)
        net = annulus & (distance[..., 1] - distance[..., 0] < .9 * amount)
        colour[net] *= .52
    elif pattern == 'flecks':
        spots = annulus & (rng.random(r.shape) < .05 * amount)
        colour[spots] *= .5
        glints = annulus & (rng.random(r.shape) < .02 * amount)
        colour[glints] = colour[glints] * .6 + 255 * .4
    if not glass:
        ruffle = .33 + .035 * np.sin(angle * 9 + rng.uniform(0, 6))
        colour *= (1 - .18 * np.exp(-((r - ruffle) / .045) ** 2))[..., None]
    colour *= (1 + rng.normal(0, .02, r.shape))[..., None]
    iris = (r <= 1.0) & opening
    if glow:
        rgba = np.zeros((h, w, 4))
        rgba[..., :3] = np.clip(colour * 1.1, 0, 255)
        rgba[..., 3] = iris * 255 * (1 - .45 * smoothstep(.72, 1.0, r))
        return rgba.astype(np.uint8)
    top = y / h
    colour *= (1 - .26 * (1 - smoothstep(.10, .44, top)) - .10 * smoothstep(.82, 1.0, top))[..., None]
    if sclera is not None:
        # A sclera is lit evenly and darkens towards the corners of the opening.
        edge = np.clip(np.hypot((x - w / 2) / (w / 2), (y - h / 2) / (h / 2)), 0, 1)
        socket = _hex(sclera) * (1 - .45 * smoothstep(.45, 1.0, edge))[..., None]
    else:
        socket = _hex(SOCKET_WET) * (1 - smoothstep(1.0, 1.45, r))[..., None] + _hex(SOCKET_DEEP) * smoothstep(1.0, 1.45, r)[..., None]
    out = np.where(iris[..., None], colour, socket)
    # The wet line just inside the edge, darker towards the corners.
    rim = opening & (outside > -1.2)
    out[rim] = out[rim] * .45 + _hex(SOCKET_DEEP) * .55
    spots = ((cx - .40 * radius, .41 * h, .15 * radius, .09 * radius, .88), (cx + .46 * radius, .68 * h, .06 * radius, .045 * radius, .35))
    if glass:
        spots = ((cx - .38 * radius, .36 * h, .22 * radius, .13 * radius, .75), (cx + .30 * radius, .66 * h, .09 * radius, .06 * radius, .30))
    for gx, gy, rx_, ry_, alpha in spots:
        spot = (((x - gx) / (rx_ * glint)) ** 2 + ((y - gy) / (ry_ * glint)) ** 2 <= 1) & opening & (outside < -1.2)
        out[spot] = out[spot] * (1 - alpha) + _hex('#fff8e8') * alpha
    if skin is not None:
        rng = np.random.default_rng(seed + 17)
        flesh = np.asarray(skin, float) * (1 + rng.normal(0, .025, (h, w)))[..., None]
        line = ~opening & (outside < 1.3)
        flesh[line] = flesh[line] * .35 + _hex(SOCKET_DEEP) * .65
        out = np.where(opening[..., None], out, flesh)
    rgba = np.zeros((h, w, 4), np.uint8)
    rgba[..., :3], rgba[..., 3] = np.clip(out, 0, 255), 255
    return rgba


def pupil_art(w, h, taper=0.8):
    """A vertical slit, pointed at both ends (taper: higher is sharper), black with a dark red edge; clear round it."""
    y, x = np.mgrid[0:h, 0:w] + 0.5
    across, along = np.abs(x / w * 2 - 1), y / h * 2 - 1
    half = np.clip(1 - along ** 2, 0, 1) ** taper
    inside = across <= half
    edge = inside & ((half - across) * w / 2 < 1.0)
    rgba = np.zeros((h, w, 4), np.uint8)
    rgba[inside] = (7, 5, 5, 255)
    rgba[edge] = (40, 13, 9, 255)
    return rgba


def round_pupil_art(d):
    """A round pupil: black with a dark red edge; clear round it."""
    y, x = np.mgrid[0:d, 0:d] + 0.5
    r = np.hypot(x - d / 2, y - d / 2) / (d / 2)
    rgba = np.zeros((d, d, 4), np.uint8)
    rgba[r <= 1.0] = (7, 5, 5, 255)
    rgba[(r <= 1.0) & (r * d / 2 > d / 2 - 1.0)] = (40, 13, 9, 255)
    return rgba


def skin_art(w, h, tone, seed, margin=None, rough=0.0):
    """Skin for lids and brows from the colours round the eye: blotches of the head's own light and dark, a
    dark lid margin along one edge, pits on a rough brow."""
    rng = np.random.default_rng(seed)
    base, dark, light = (np.asarray(tone[k], float) for k in ('base', 'dark', 'light'))
    blot = rng.uniform(-1, 1, (h // 2 + 1, w // 2 + 1)).repeat(2, 0).repeat(2, 1)[:h, :w]
    colour = base + np.where(blot[..., None] > 0, (light - base) * blot[..., None] * .35, (base - dark) * blot[..., None] * .35)
    if rough:
        pits = rng.random((h, w)) < rough
        colour[pits] = colour[pits] * .6 + dark * .4
    rows = np.arange(h)[:, None]
    if margin is not None:
        edge = (h - 1 - rows) if margin == 'bottom' else rows
        shade = np.where(edge < 1, .55, np.where(edge < 3, .25, 0))[..., None]
        colour = colour * (1 - shade) + (dark * .45) * shade
    rgba = np.zeros((h, w, 4), np.uint8)
    rgba[..., :3], rgba[..., 3] = np.clip(colour, 0, 255), 255
    return rgba


def _rays(faces, starts, direction):
    """Nearest hit of each ray on the faces, as (distance, uv) or None."""
    found = []
    for start in starts:
        best = None
        for face in faces:
            got = geckolib_mesh.hit(face, start, direction)
            if got is not None and (best is None or got[0] < best[0]):
                best = got
        found.append(best)
    return found


def add_designed_eyes(identifier, geometry, atlases):
    """The designed eyes of one species (DESIGNS): bones, the eye strip under each atlas, and for the hunters
    of HUNTING_EYES the sheet of their glowing irises, under the name 'eyes'."""
    design = design_of(identifier)
    iris_design = design['iris']
    model = geometry['minecraft:geometry'][0]
    bones = model['bones']
    bones[:] = [b for b in bones if not b['name'].startswith('ark_eye')]
    for bone in bones:
        if is_eye(bone['name']) or LID.search(bone['name']):
            bone.pop('cubes', None)  # the rig's own eyes and lids; the bones stay for their animation tracks
    rig = skeleton(bones)
    index, pivots, worlds, matrices = rig
    skull_name = design['skull']
    skull = index[skull_name]
    length, height = design['size']
    frame = eye_frame(design)
    out, up, forward = frame.T
    mirror = np.diag([-1.0, 1.0, 1.0])
    states = ('calm', 'hunt') if iris_design['hunt'] else ('calm',)
    # The pupil stands clear of the iris, but behind the lids, so a shut eye hides it.
    lid_out = [out_by * height for out_by, _ in design['lids'].values()]
    pupil_gap = float(np.clip(.5 * min(lid_out), .04, PUPIL_GAP)) if lid_out else PUPIL_GAP

    # Where the skin is: the face stands gap above the highest skin under the opening, measured on both sides.
    head = {'bones': [b for b in bones], 'description': model['description']}
    skipped = re.compile(design['skip']) if design['skip'] else None
    faces = [q for q in geckolib_mesh.quads(head) if q[3] != 'ark_eyes' and not (skipped and skipped.search(q[3]))]
    y0, z0 = design['centre']
    footprint = [(a * height, b * length) for a in np.linspace(-.55, .55, 5) for b in np.linspace(-.55, .55, 5)]
    if design['skin'] is not None:
        skin_x = design['skin']
    else:
        lateral = _rays(faces, [np.array([90.0, y0, z0])], np.array([-1.0, 0, 0]))[0]
        if lateral is None:
            raise ValueError(f'{identifier}: no skin at the eye centre {design["centre"]}')
        skin_x = 90.0 - lateral[0]
    rises = []
    for sign in (1, -1):
        flip = np.diag([sign, 1.0, 1.0])
        o = flip @ out
        anchor_point = np.array([sign * skin_x, y0, z0])
        starts = [anchor_point + a * (flip @ up) + b * (flip @ forward) + 40 * o for a, b in footprint]
        for got in _rays(faces, starts, -o):
            if got is not None:
                rises.append(40.0 - got[0])
    rise = max(rises) if rises else 0.0
    sink = min(rises) if rises else 0.0
    centre = np.array([skin_x, y0, z0]) + (rise + design['gap']) * out
    depth = (rise - sink) + design['gap'] + .6 * height
    PLACEMENT[identifier] = (skull_name, np.round(centre, 2).tolist(), round(height, 2), round(float(centre[0]), 2))

    # The strip under the atlas: the irises, the pupil, the skins.
    desc = model['description']
    width = desc['texture_width']
    base = next(iter(atlases.values())).height
    px = design['px']
    iris_r = iris_design['radius'] * length
    sw, sh = int(round(length * px)), int(round(height * px))
    pupil = design['pupil']
    if isinstance(pupil, tuple):
        pw, ph, taper = pupil
        pupil_size = (pw * length, ph * height)
    elif pupil:
        pupil_size = (pupil * 2 * iris_r,) * 2
    else:
        pupil_size = None
    pupil_px = (max(4, int(round(pupil_size[0] * px * 2))), max(4, int(round(pupil_size[1] * px * 2)))) if pupil_size else (0, 0)
    rects, x, row = {}, 2, base + 2
    tallest = max(sh, pupil_px[1], 16)

    def place(key, w, h):
        nonlocal x, row
        if x + w + 2 > width:
            x, row = 2, row + tallest + 2
        rects[key] = (x, row, w, h)
        x += w + 2

    for state in states:
        for side in ('l', 'r'):
            place(('iris', state, side), sw, sh)
    if pupil_size:
        place('pupil', *pupil_px)
    for lid in design['lids']:
        place(f'lid_{lid}', 16, 16)
    if design['brow']:
        place('brow', 16, 16)
    if design['crest']:
        place('crest', 16, 16)
    place('socket', 4, 4)
    place('margin', 4, 4)
    strip = row + tallest + 2 - base
    desc['texture_height'] = base + strip

    # Bones: the cancel root between the eyes, then each eye in its own frame.
    mid = np.array([0.0, centre[1], centre[2]])
    pivot_model = pivots[skull] + matrices[skull].T @ (mid - worlds[skull])

    def bedrock(point):
        return [round(-point[0], 5), round(point[1], 5), round(point[2], 5)]

    def local(point):
        return pivot_model + (np.asarray(point, float) - mid)

    tree = [{'name': 'ark_eyes', 'parent': skull_name, 'pivot': bedrock(pivot_model), 'rotation': to_bedrock(matrices[skull].T)}]
    half_w, half_h = length / 2, height / 2
    for side, sign in (('l', 1), ('r', -1)):
        rotation = frame if sign == 1 else mirror @ frame @ mirror
        q = local(np.diag([sign, 1.0, 1.0]) @ centre)
        outward = 'east' if sign == 1 else 'west'

        def cube(lo, hi, uv):
            """A box in the eye's frame: x out of the head, y up, z towards the snout."""
            x0, x1 = (lo[0], hi[0]) if sign == 1 else (-hi[0], -lo[0])
            return box(q + [x0, lo[1], lo[2]], q + [x1, hi[1], hi[2]], uv)

        def at(point):
            return bedrock(q + [sign * point[0], point[1], point[2]])

        name = f'ark_eye_{side}'
        tree.append({'name': name, 'parent': 'ark_eyes', 'pivot': bedrock(q), 'rotation': to_bedrock(rotation)})
        socket = face_uv(rects['socket'])
        for state in states:
            tree.append({'name': f'ark_eyeball_{state}_{side}', 'parent': name, 'pivot': bedrock(q),
                         'cubes': [cube((-depth, -half_h, -half_w), (0.0, half_h, half_w),
                                        {outward: face_uv(rects['iris', state, side]), 'up': socket, 'down': socket,
                                         'north': socket, 'south': socket})]})
        if pupil_size:
            tree.append({'name': f'ark_eye_pupil_{side}', 'parent': name, 'pivot': at((pupil_gap, 0.0, 0.0)),
                         'cubes': [cube((pupil_gap - .05, -pupil_size[1] / 2, -pupil_size[0] / 2),
                                        (pupil_gap, pupil_size[1] / 2, pupil_size[0] / 2), {outward: face_uv(rects['pupil'])})]})
        margin = face_uv(rects['margin'])
        for lid, (out_by, slant) in design['lids'].items():
            edge = 1 if lid == 'upper' else -1
            far = edge * (half_h + LID_TUCK[lid] * height)
            near = edge * (half_h - design['rest'][lid] * height)
            skin = face_uv(rects[f'lid_{lid}'])
            lip = 'down' if lid == 'upper' else 'up'
            uv = {outward: skin, 'north': skin, 'south': skin, lip: margin, ('up' if lid == 'upper' else 'down'): skin}
            tree.append({'name': f'ark_eye_lid_{lid}_{side}', 'parent': name, 'pivot': at((0.0, far, 0.0)),
                         'rotation': to_bedrock(geckolib_mesh.rx(math.radians(slant))),
                         'cubes': [cube((-.6 * depth, min(far, near), -half_w * 1.14), (max(out_by * height, 1.6 * pupil_gap), max(far, near), half_w * 1.14), uv)]})
        if design['brow']:
            brow = face_uv(rects['brow'])
            brow_uv = {outward: brow, 'north': brow, 'south': brow, 'up': brow, 'down': brow}
            boxes = [cube((xa * height, half_h + ya * height, za * length), (xb * height, half_h + yb * height, zb * length), brow_uv)
                     for (za, zb), (ya, yb), (xa, xb) in design['brow']['boxes']]
            back = min(za for (za, _), _, _ in design['brow']['boxes']) * length
            tree.append({'name': f'ark_eye_brow_{side}', 'parent': name, 'pivot': at((0.0, half_h, back)),
                         'rotation': to_bedrock(geckolib_mesh.rx(math.radians(design['brow']['slant']))), 'cubes': boxes})
        if design['crest']:
            crest = face_uv(rects['crest'])
            crest_uv = {outward: crest, 'north': crest, 'south': crest, 'up': crest, 'down': crest}
            tree.append({'name': f'ark_eye_crest_{side}', 'parent': name, 'pivot': bedrock(q),
                         'cubes': [cube((xa * height, half_h + ya * height, za * length), (xb * height, half_h + yb * height, zb * length), crest_uv)
                                   for (za, zb), (ya, yb), (xa, xb) in design['crest']]})
    bones.extend(tree)

    # The art, per skin variant: the colours round the eye come from that variant's own head.
    centre_px = (sw / 2, sh / 2)
    starts = [centre + a * height * up + b * length * forward + 30 * out
              for a in np.linspace(-1.4, 1.4, 6) for b in np.linspace(-1.2, 1.2, 6)]
    hits = [got for got in _rays(faces, starts, -out) if got is not None]
    seed = sum(map(ord, identifier))
    result = {}
    for variant, atlas in atlases.items():
        canvas = Image.new('RGBA', (width, base + strip), (0, 0, 0, 0))
        canvas.paste(atlas.convert('RGBA'), (0, 0))
        pixels = np.array(canvas)
        source = np.asarray(atlas.convert('RGB'), float)
        samples = np.array([source[int(v) % source.shape[0], int(u) % source.shape[1]] for _, (u, v) in hits]) \
            if hits else np.array([[120.0, 110.0, 95.0]])
        lum = samples @ [.3, .59, .11]
        order = np.argsort(lum)
        tone = {'base': np.median(samples, 0), 'dark': samples[order[len(order) // 6]], 'light': samples[order[len(order) * 5 // 6]]}
        lids = {k: value * .84 for k, value in tone.items()}
        for key, rect in rects.items():
            u, v, w, h = rect
            if key[0] == 'iris':
                _, state, side = key
                art = iris_art(w, h, iris_design[state], iris_r * px, centre_px, seed + (state == 'hunt'),
                               skin=(lids if design['lids'] else tone)['base'], shape=design['almond'], pattern=iris_design['pattern'],
                               amount=iris_design['amount'], sclera=iris_design['sclera'], glint=iris_design['glint'])
                if side == 'r':
                    art = art[:, ::-1]
            elif key == 'pupil':
                art = pupil_art(w, h, taper) if isinstance(pupil, tuple) else round_pupil_art(w)
            elif key == 'socket':
                # The walls the eye sits in: the head's own shadow, nearly black.
                art = np.tile(np.array([*np.clip(tone['dark'] * .42 + _hex(SOCKET_DEEP) * .35, 0, 255), 255], np.uint8), (h, w, 1))
            elif key == 'margin':
                art = np.tile(np.array([*np.clip(tone['dark'] * .45, 0, 255), 255], np.uint8), (h, w, 1))
            elif key == 'brow':
                art = skin_art(w, h, tone, seed + 3, rough=.08)
            elif key == 'crest':
                # Horns are pale keratin; a boss or a ridge is the brow's own skin.
                bone = {k: np.clip(value * 1.12 + 12, 0, 255) for k, value in tone.items()} if design['horn'] else tone
                art = skin_art(w, h, bone, seed + 4, rough=.14 if design['horn'] else .08)
            else:
                art = skin_art(w, h, lids, seed + (1 if key == 'lid_upper' else 2), margin='bottom' if key == 'lid_upper' else 'top')
            pixels[v:v + h, u:u + w] = art
        result[variant] = Image.fromarray(pixels)
    if identifier in HUNTING_EYES:
        assert iris_design['hunt'], f'{identifier} is a hunter of HUNTING_EYES and needs hunting eyes'
        sheet = np.zeros((base + strip, width, 4), np.uint8)
        for side in ('l', 'r'):
            u, v, w, h = rects['iris', 'hunt', side]
            art = iris_art(w, h, iris_design['hunt'], iris_r * px, centre_px, seed + 1, glow=True, shape=design['almond'],
                           pattern=iris_design['pattern'], amount=iris_design['amount'])
            sheet[v:v + h, u:u + w] = art[:, ::-1] if side == 'r' else art
        result['eyes'] = Image.fromarray(sheet)
    return result


def rebuild_designed(identifier):
    """Rebuild one species' designed eyes on its imported geometry and its original skins, without an import."""
    from import_creatures import SPECIES, ASSETS, TEXTURE_VARIANTS, source_files, write
    folder = next(f for f, i, *_ in SPECIES if i == identifier)
    _, _, sources = source_files(folder)
    path = ASSETS / f'geckolib/models/entity/{identifier}.geo.json'
    geometry = json.loads(path.read_text())
    atlases = {variant: Image.open(sources[f'texture_{variant.lower()}']) for variant in TEXTURE_VARIANTS}
    result = add_designed_eyes(identifier, geometry, atlases)
    write(path, geometry)
    for name, image in result.items():
        image.save(ASSETS / f'textures/entity/{identifier}_{name.lower()}.png')


def main():
    import sys
    assets = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/arksurvivalreturns'
    designed = [i for i in sys.argv[1:] if i in DESIGNS] or sorted(DESIGNS)
    for identifier in designed:
        rebuild_designed(identifier)
    print(f'Rebuilt the designed eyes of {", ".join(designed)}.')
    for identifier in sorted(HUNTING_EYES - set(DESIGNS)):
        model = json.loads((assets / f'geckolib/models/entity/{identifier}.geo.json').read_text())['minecraft:geometry'][0]
        sprites = {}
        for bone in model['bones']:
            if bone['name'].startswith('ark_eyeball_'):
                face = bone['cubes'][0]['uv']['east']
                sprites[tuple(bone['name'].split('_')[2:])] = (*face['uv'], *face['uv_size'])
        assert len(sprites) == 4, f'{identifier}: {len(sprites)} eyeballs in the imported geometry'
        desc = model['description']
        pupil = 'round' if identifier in ROUND_PUPIL else 'slit'
        hunting_eyes(desc['texture_width'], desc['texture_height'], sprites, pupil).save(
            assets / f'textures/entity/{identifier}_eyes.png')
    print(f'Wrote the hunting eyes of {len(HUNTING_EYES - set(DESIGNS))} more species.')


if __name__ == '__main__':
    main()
