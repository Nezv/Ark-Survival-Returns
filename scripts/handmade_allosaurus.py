"""The Allosaurus, designed box by box on the ARK rig; built by scripts/handmade_models.py.

A long, lean theropod in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a deep
trunk carried on heavy thighs, long shins and three-toed feet, a long tail that tapers from the hips to a point and
an S-curved neck that climbs to a long, narrow skull. A blade-like horn stands before each eye over a ledged brow,
the nasal ridge is rugose, and the lower jaw is designed shut as the idle holds it, its teeth cut-out rows along
both jaws. The arms are long for a theropod and end in three hooked claws. Paired rows of osteoderm scutes run from
the crown down the neck and the back to the hips.
"""
import math

import numpy as np

from handmade_models import cells, fbm, hexes, noise, shade
from handmade_tyrannosaurus import face_grid, grid, midline_top, scales, stripe_runs

FOLDER, STEM, HEIGHT = 'Allosaurus', 'allosaurus', 6.0
DENSITY = 1.0            # texels per runtime unit, Minecraft's own density; the head is painted at FACE times that
FACE = 2.0
ATLAS_WIDTH = 512
PREVIEW_VARIANT = 'Natural'
ASSET = 'Allosaurus'
PREVIEW_CLIPS = ('Allosaurus-Idle', 'Allosaurus-Move-Fwd', 'Allosaurus-Roar_Anim', 'Allosaurus-Torpid-Idle')
JAW_POSE = ('Allosaurus-Idle', 0.0, ['jaw', 'tongue'])     # the frame the jaw is designed in
HEAD, JAW = 'c_head', 'c_jaw'

# The trunk, segment by segment: (bone, rear z, front z, tiers from the belly up as (bottom, top, half width)),
# measured on the ARK mesh's cross-sections, the limbs and the scutes left out.
TRUNK = [
    ('c_back1', -10.9, -6.5, [(18.16, 19.1, 2.6), (19.05, 20.8, 3.6), (20.8, 25.5, 4.45), (25.5, 26.9, 3.3),
                              (26.9, 27.6, 1.9)]),
    ('c_back1', -6.7, -2.3, [(15.9, 17.2, 2.0), (17.15, 19.5, 3.8), (19.5, 25.9, 5.75), (25.9, 27.9, 4.8),
                             (27.9, 28.8, 2.85)]),
    ('c_back1', -2.5, 2.15, [(15.1, 16.6, 2.15), (16.55, 19.2, 4.05), (19.2, 26.5, 5.9), (26.5, 28.7, 5.0),
                              (28.7, 29.75, 2.95)]),
    ('c_back2', 1.95, 7.1, [(16.15, 17.6, 2.4), (17.55, 20.1, 4.4), (20.1, 27.2, 6.05), (27.2, 29.4, 4.15),
                           (29.4, 30.35, 1.95)]),
    ('c_back3', 6.9, 11.15, [(16.8, 18.15, 2.8), (18.1, 20.6, 4.65), (20.6, 27.35, 6.35), (27.35, 29.4, 3.9),
                              (29.4, 30.3, 2.1)]),
    ('c_back4', 10.95, 14.5, [(17.7, 18.95, 2.45), (18.9, 21.2, 4.0), (21.2, 27.5, 5.6), (27.5, 29.4, 3.7),
                             (29.4, 30.25, 2.05)]),
]
# The tail, two segments per bone: (bone, rear z, front z, (keel, core, top) tiers).
TAIL_SEGMENTS = [
    ('c_tail1', -15.5, -10.65, [(18.75, 20.38, 1.93), (20.38, 25.27, 3.59), (25.27, 26.9, 1.49)]),
    ('c_tail1', -19.89, -15.3, [(19.04, 20.49, 1.68), (20.49, 24.82, 2.94), (24.82, 26.26, 1.18)]),
    ('c_tail2', -24.57, -19.69, [(19.38, 20.63, 1.43), (20.63, 24.38, 2.48), (24.38, 25.63, 1.12)]),
    ('c_tail2', -29.04, -24.36, [(19.83, 20.88, 1.24), (20.88, 24.04, 2.17), (24.04, 25.09, 1.02)]),
    ('c_tail3', -34.02, -28.84, [(20.21, 21.08, 0.99), (21.08, 23.69, 1.8), (23.69, 24.57, 0.95)]),
    ('c_tail3', -38.79, -33.81, [(20.44, 21.15, 0.88), (21.15, 23.31, 1.59), (23.31, 24.03, 0.85)]),
    ('c_tail4', -43.16, -38.59, [(20.62, 21.21, 0.8), (21.21, 22.99, 1.41), (22.99, 23.59, 0.76)]),
    ('c_tail4', -47.32, -42.95, [(20.88, 21.37, 0.69), (21.37, 22.82, 1.23), (22.82, 23.3, 0.66)]),
    ('c_tail5', -51.41, -47.12, [(21.12, 21.49, 0.56), (21.49, 22.61, 0.99), (22.61, 22.99, 0.52)]),
    ('c_tail5', -55.3, -51.21, [(21.47, 21.67, 0.36), (21.67, 22.28, 0.63), (22.28, 22.48, 0.4)]),
]
# The neck climbs in an S from the shoulders to the head: per bone (rear z, front z, half width), laid along the top
# line of its back and the bottom line of its throat, measured on the ARK mesh's side view.
NECK = [('c_back4', 14.3, 17.3, 4.45), ('c_neck1', 17.1, 20.6, 3.45), ('c_neck2', 20.4, 23.6, 2.75),
        ('c_neck3', 23.4, 27.8, 2.62)]
NECK_TOP = ([12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 24.0, 26.0, 28.0],
            [30.25, 30.25, 30.4, 31.0, 32.2, 33.4, 34.15, 34.55, 34.9])
NECK_THROAT = ([12.0, 14.0, 16.0, 18.0, 19.0, 20.0, 22.0, 24.0, 26.0, 27.0, 28.0],
               [17.4, 18.2, 19.4, 21.0, 22.4, 23.6, 25.2, 26.5, 27.3, 27.5, 27.6])


def neck_section(d, bone, z0, z1, half, mat='hide'):
    """Four slabs along the neck between z0 and z1: a narrow crest on the top line, the upper and lower cores that
    meet in the middle, and a narrow keel along the throat, each sloped like its line."""
    top = lambda z: float(np.interp(z, *NECK_TOP))
    low = lambda z: float(np.interp(z, *NECK_THROAT))
    zm = (z0 + z1) / 2
    slope_top = math.atan2(top(z1) - top(z0), z1 - z0)
    slope_low = math.atan2(low(z1) - low(z0), z1 - z0)
    depth = (top(zm) - low(zm)) * math.cos((slope_top + slope_low) / 2)
    core = depth * 0.5 + 0.6
    a, b = z0 - 0.3, z1 + 0.3                        # the cores reach past the bends, so no wedge opens between them
    d.slab(bone, (top(z0), z0), (top(z1), z1), half * 0.6, 0.9, mat, part='neck', tier=2)
    d.slab(bone, (top(a) - 0.8, a), (top(b) - 0.8, b), half, core, mat, part='neck', tier=1)
    d.slab(bone, (low(a) + 0.8, a + 0.04), (low(b) + 0.8, b - 0.04), half * 0.93, core, mat, top=False,
           part='neck', tier=-1)
    d.slab(bone, (low(z0), z0 + 0.2), (low(z1), z1 - 0.2), half * 0.62, 0.9, mat, top=False, part='neck', tier=-2)


def tiers(d, bone, z0, z1, rows, mat='hide', part='trunk', inset=0.03):
    """Stacked boxes across a segment, each overlapping the next a little; the tiers off the core are inset a
    hair at both ends, so no two neighbours share an end plane."""
    core = max(range(len(rows)), key=lambda k: rows[k][2])
    for k, (y0, y1, half) in enumerate(rows):
        e = 0.0 if k == core else inset * (1 + abs(k - core))
        lo = y0 - (0.04 if k > core else 0.0)
        hi = y1 + (0.04 if k < core else 0.0)
        d.span(bone, (-half, lo, z0 + e), (half, hi, z1 - e), mat, part=part, tier=k - core, rows=len(rows))


def top_at(boxes, x, z):
    """The highest point of the boxes along the vertical line at (x, z): where a scute sits."""
    return midline_top([_shifted(b, -x) for b in boxes], z)


def _shifted(box, dx):
    moved = type(box)(box.bone, box.center + np.array([dx, 0.0, 0.0]), box.size, box.axes, box.mat, box.tag)
    return moved


def bone_at(z):
    """The spine bone that carries the body at z."""
    for bone, start in (('c_neck3', 23.49), ('c_neck2', 20.44), ('c_neck1', 17.04), ('c_back4', 11.06),
                        ('c_back3', 7.04), ('c_back2', 2.02), ('c_back1', -10.91)):
        if z >= start:
            return bone
    return 'c_tail1'


def design(d):
    rig = d.rig
    P = rig.pivot
    # the jaw and the tongue are designed shut, as the idle holds them; the bind pose roars
    for bone in (JAW, 'c_tongue1', 'c_tongue2', 'c_tongue3', 'c_tongue4'):
        rig.design_as_in(bone, 'Allosaurus-Idle', 0.0, HEAD)

    # ---------------------------------------------------------------- trunk, tail and neck
    for bone, z0, z1, rows in TRUNK:
        tiers(d, bone, z0, z1, rows)
    for bone, z0, z1, rows in TAIL_SEGMENTS:
        tiers(d, bone, z0, z1, rows, part='tail', inset=0.06)
    for bone, z0, z1, half in NECK:
        neck_section(d, bone, z0, z1, half)

    # ---------------------------------------------------------------- head: a long narrow skull, horns before the eyes
    d.span(HEAD, (-3.1, 28.35, 27.2), (3.1, 34.3, 31.1), 'head', part='cranium', density=FACE)
    d.span(HEAD, (-2.35, 34.2, 27.6), (2.35, 35.05, 30.9), 'head', part='roof', density=FACE)
    # the face round the eye: one flat side where the eye is set (build_creature_eyes footprint), a brow ledge above
    d.span(HEAD, (-2.72, 28.4, 30.95), (2.72, 34.0, 33.65), 'head', part='temple', density=FACE)
    d.span(HEAD, (-2.95, 33.9, 30.7), (2.95, 34.55, 33.5), 'head', part='brow', density=FACE)
    d.span(HEAD, (-2.1, 34.45, 31.0), (2.1, 35.2, 33.9), 'head', part='roof', density=FACE)
    # the lacrimal horns: a blade before each eye, broad at the base
    d.span(HEAD, (1.75, 34.5, 32.25), (2.8, 35.35, 33.75), 'horn', part='horn', mirror=True, density=FACE)
    d.span(HEAD, (1.95, 35.3, 32.5), (2.6, 36.05, 33.45), 'horn', part='horn', mirror=True, density=FACE)
    d.span(HEAD, (2.08, 36.0, 32.75), (2.45, 36.45, 33.2), 'horn', part='horn', mirror=True, density=FACE)
    # the snout narrows and falls to the tip; the paired nasal ridges run along its top
    d.span(HEAD, (-2.25, 28.3, 33.6), (2.25, 33.85, 35.5), 'head', part='snout', density=FACE)
    d.span(HEAD, (-1.55, 33.8, 33.8), (1.55, 34.45, 35.4), 'head', part='snout_top', density=FACE)
    d.span(HEAD, (-1.68, 28.2, 35.45), (1.68, 32.6, 37.4), 'head', part='snout', density=FACE)
    d.span(HEAD, (-1.1, 32.55, 35.55), (1.1, 33.1, 37.2), 'head', part='snout_top', density=FACE)
    d.span(HEAD, (-1.12, 28.25, 37.35), (1.12, 31.4, 38.65), 'head', part='tip', density=FACE)
    d.span(HEAD, (-0.62, 28.6, 38.6), (0.62, 30.6, 39.0), 'head', part='tip', density=FACE)
    # the rugose nasal ridges: a low knobbed crest either side of the midline
    d.span(HEAD, (0.35, 34.4, 33.95), (0.95, 34.75, 35.3), 'head', part='ridge', mirror=True, density=FACE)
    d.span(HEAD, (0.3, 33.05, 35.6), (0.8, 33.35, 37.1), 'head', part='ridge', mirror=True, density=FACE)
    # the upper teeth: cut-out rows hanging from the lip, outside the narrower lower jaw, and across the front
    teeth = dict(open=('up', 'down', 'north', 'south'), cutout=True, keep=True, mirror=True)
    d.span(HEAD, (2.38, 27.75, 30.7), (2.5, 28.6, 33.55), 'tooth', part='teeth', row='upper', **teeth, density=FACE)
    d.span(HEAD, (1.92, 27.55, 33.55), (2.04, 28.5, 35.4), 'tooth', part='teeth', row='upper', **teeth, density=FACE)
    d.span(HEAD, (1.38, 27.5, 35.4), (1.5, 28.4, 37.3), 'tooth', part='teeth', row='upper', **teeth, density=FACE)
    d.span(HEAD, (-1.0, 27.55, 38.38), (1.0, 28.45, 38.5), 'tooth', part='teeth', row='front',
           open=('up', 'down', 'east', 'west'), cutout=True, keep=True, density=FACE)

    d.span(JAW, (-2.55, 26.9, 27.3), (2.55, 28.5, 31.0), 'head', part='jaw', density=FACE)
    d.span(JAW, (-2.0, 26.75, 30.9), (2.0, 28.47, 33.6), 'head', part='jaw', density=FACE)
    d.span(JAW, (-1.55, 26.45, 33.5), (1.55, 28.43, 35.5), 'head', part='jaw', density=FACE)
    d.span(JAW, (-1.05, 26.3, 35.4), (1.05, 28.33, 38.05), 'head', part='chin', density=FACE)
    d.span(JAW, (1.72, 28.05, 31.0), (1.84, 28.95, 33.5), 'tooth', part='teeth', row='lower', **teeth, density=FACE)
    d.span(JAW, (1.3, 27.95, 33.6), (1.42, 28.8, 35.4), 'tooth', part='teeth', row='lower', **teeth, density=FACE)
    d.span('c_tongue2', (-0.8, 28.44, 30.0), (0.8, 28.62, 34.5), 'mouth', part='tongue', density=FACE)

    # ---------------------------------------------------------------- scutes: paired rows from the crown to the hips
    body = list(d.boxes)
    for z, h in SCUTES:
        for x in (0.95, -0.95):
            y = top_at(body, x, z)
            d.span(bone_at(z), (x - 0.27, y - 0.3, z - 0.4), (x + 0.27, y + h, z + 0.4), 'scute', part='scute')
    for z, h in NECK_SPIKES:                     # the bigger spikes along the sides of the neck
        for x in (1.85, -1.85):
            y = top_at(body, x, z)
            d.span(bone_at(z), (x - 0.3, y - 0.45, z - 0.45), (x + 0.3, y + h, z + 0.45), 'scute', part='scute')

    # ---------------------------------------------------------------- arms: long, three hooked claws
    d.span('l_clavicle', (3.6, 20.6, 13.4), (5.85, 24.5, 17.6), 'hide', mirror=True, part='shoulder')
    elbow, wrist = P('l_elbow'), P('l_wrist')
    d.seg('l_shoulder', (4.65, 22.4, 15.7), elbow, 1.95, 2.2, 'hide', ext=(0.0, 0.45), mirror=True, part='arm')
    d.seg('l_elbow', elbow, wrist, 1.5, 1.55, 'hide', ext=(0.35, 0.25), mirror=True, part='forearm')
    d.seg('l_wrist', wrist, P('l_finger_B1'), 1.2, 2.1, 'hide', ext=(0.15, 0.1), side=(1, 0, 0), mirror=True,
          part='hand')
    for finger, w in (('A', 0.62), ('B', 0.66), ('C', 0.55)):
        a, b, c = (P(f'l_finger_{finger}{k}') for k in (1, 2, 3))
        tip = c + (c - b) * 2.4 + np.array([0.0, -0.2, 0.15])
        d.seg(f'l_finger_{finger}1', a, b, w, w + 0.06, 'hide', ext=(0.2, 0.08), mirror=True, part='finger')
        d.seg(f'l_finger_{finger}2', b, c, w * 0.9, w * 0.95, 'hide', ext=(0.08, 0.06), mirror=True, part='finger')
        d.seg(f'l_finger_{finger}3', c, tip, w * 0.6, w * 0.75, 'claw', ext=(0.04, 0.0), mirror=True, part='claw',
              along=(0.0, 1.0))

    # ---------------------------------------------------------------- legs
    d.span('l_hip', (4.0, 16.0, -5.4), (7.85, 24.0, 5.6), 'hide', mirror=True, part='thigh')
    d.span('l_hip', (4.7, 23.9, -4.6), (7.55, 26.45, 4.0), 'hide', mirror=True, part='thigh')
    d.span('l_hip', (3.65, 13.75, -2.3), (7.4, 16.1, 4.65), 'hide', mirror=True, part='thigh')
    d.seg('l_knee', (5.45, 14.3, 1.25), (5.2, 10.0, -1.05), 3.65, 4.3, 'hide', ext=(0.0, 0.0), mirror=True,
          part='shin')
    d.seg('l_knee', (5.2, 10.3, -0.9), (4.95, 6.2, -3.15), 2.75, 3.15, 'hide', ext=(0.0, 0.25), mirror=True,
          part='shin')
    d.seg('l_ankle', (4.8, 6.6, -4.15), (4.95, 1.45, -1.9), 2.6, 2.45, 'hide', mirror=True, part='meta')
    d.span('l_heel', (3.2, 0.0, -3.1), (6.8, 1.85, 0.35), 'hide', mirror=True, part='foot')
    toes = {'A': ([P('l_toe_A1'), P('l_toe_A2')], (1.95, 0.35, 3.85), 1.45),
            'B': ([P('l_toe_B1'), P('l_toe_B2'), P('l_toe_B3')], (5.6, 0.35, 5.85), 1.6),
            'C': ([P('l_toe_C1'), P('l_toe_C2'), P('l_toe_C3')], (9.45, 0.3, 2.45), 1.4)}
    for name, (joints, tip, w) in toes.items():
        tip = np.array(tip)
        points = joints + [tip]
        last = len(joints) - 1
        for k in range(len(joints)):
            a, b = points[k], points[k + 1]
            scale = 1 - 0.12 * k
            if k < last:
                d.seg(f'l_toe_{name}{k + 1}', a, b, w * scale, w * scale, 'hide', ext=(0.25 if k == 0 else 0.08, 0.05),
                      mirror=True, part='toe')
            else:
                knuckle = a + (b - a) * 0.45
                d.seg(f'l_toe_{name}{k + 1}', a, knuckle, w * scale, w * scale * 0.95, 'hide', ext=(0.08, 0.0),
                      mirror=True, part='toe')
                d.seg(f'l_toe_{name}{k + 1}', knuckle, b, w * scale * 0.6, w * scale * 0.65, 'claw', ext=(0.04, 0.0),
                      mirror=True, part='claw', along=(0.0, 1.0))


# The paired scutes: (z, height above the skin), from the hips to the crown; they grow toward the neck.
SCUTES = [(-9.4, 0.3), (-7.6, 0.34), (-5.8, 0.38), (-4.0, 0.42), (-2.2, 0.45), (-0.4, 0.48), (1.4, 0.5), (3.2, 0.52),
          (5.0, 0.55), (6.8, 0.58), (8.6, 0.6), (10.4, 0.62), (12.2, 0.65), (14.0, 0.7), (15.8, 0.75), (17.5, 0.8),
          (19.1, 0.82), (20.7, 0.8), (22.3, 0.78), (23.9, 0.74), (25.4, 0.7), (26.8, 0.6), (28.2, 0.45), (29.6, 0.4)]
NECK_SPIKES = [(18.4, 0.55), (20.2, 0.6), (22.0, 0.6), (23.8, 0.55), (25.5, 0.5)]


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(hide=hexes('#2c2216', '#3d2f1e', '#4f3d27', '#634d31', '#775d3c', '#8c6e48', '#a18055', '#b69263'),
                    belly=hexes('#8a7558', '#9b8768', '#ac9978', '#bcaa89', '#cbbb9a', '#d8cbab'),
                    stripe=hexes('#1a120c', '#24190f', '#2e2014', '#39281a'),
                    scute=hexes('#1e1710', '#2a2016', '#372a1d', '#453525', '#55422e'),
                    horn=hexes('#3a1810', '#552417', '#70321f', '#8b4229', '#a55535'),
                    mouth=hexes('#3a1414', '#55201f', '#71302c', '#8c423b', '#a5574c', '#bb6e5e')),
}
PALETTES['tan'] = PALETTES['Natural']
PALETTES['umber'] = dict(hide=hexes('#22180f', '#312318', '#412f20', '#523c29', '#634a33', '#75583d', '#886748', '#9b7754'),
                         belly=hexes('#76634d', '#88755d', '#9a876e', '#ac9a80', '#bdac92', '#cdbea4'),
                         stripe=hexes('#0f0a07', '#160f0a', '#1d140e', '#251a12'),
                         scute=hexes('#16100b', '#211811', '#2c2017', '#38291e', '#453326'),
                         horn=hexes('#2e1a12', '#43271b', '#593525', '#6f4430', '#86543c'),
                         mouth=PALETTES['Natural']['mouth'])
PALETTES['rust'] = dict(hide=hexes('#2b140d', '#3d1e14', '#50291b', '#643523', '#79422b', '#8e5034', '#a45f3e', '#ba6e49'),
                        belly=hexes('#8c6650', '#9d7861', '#ae8a73', '#bf9c85', '#cfae97', '#ddc0a9'),
                        stripe=hexes('#140806', '#1d0c09', '#26110c', '#30160f'),
                        scute=hexes('#1c0d09', '#28140e', '#341b13', '#422318', '#512b1e'),
                        horn=hexes('#3c120c', '#581b12', '#742619', '#903221', '#ab402a'),
                        mouth=PALETTES['Natural']['mouth'])
PALETTES['olive'] = dict(hide=hexes('#22231a', '#303122', '#3f402b', '#4f5034', '#60603e', '#717148', '#838252', '#95935e'),
                         belly=hexes('#7d755a', '#8f8769', '#a19979', '#b2ab8a', '#c2bb9b', '#d1caac'),
                         stripe=hexes('#0f100b', '#161710', '#1d1e15', '#25261a'),
                         scute=hexes('#16170f', '#202116', '#2b2c1d', '#373825', '#44452e'),
                         horn=hexes('#36190f', '#4f2416', '#69311e', '#833f27', '#9c4e31'),
                         mouth=PALETTES['Natural']['mouth'])
TOOTH_RAMP = hexes('#8a7f66', '#ab9f83', '#c9bf9f', '#e0d8bc', '#efe9d5', '#faf7ec')
CLAW_RAMP = hexes('#100e0b', '#1d1914', '#2b251e', '#3b3329', '#4e4436', '#655845')
TONGUE_RAMP = hexes('#5e2526', '#7a3333', '#954442', '#ad5853', '#c26e66')

# The body's outline along its length (z), from the tail tip to the snout: the top of the back and the underside.
BACK = ([-55, -45, -35, -25, -18, -12, -8, -4, 0, 4, 8, 12, 16, 18, 20, 22, 24, 26, 28, 32, 36, 39],
        [22.4, 23.4, 24.2, 25.2, 26.4, 27.3, 27.8, 28.9, 29.8, 30.3, 30.35, 30.3, 30.4, 31.0, 32.2, 33.4, 34.15,
         34.55, 35.0, 35.2, 33.1, 30.6])
UNDER = ([-55, -45, -35, -25, -18, -12, -8, -4, 0, 4, 8, 12, 16, 18, 20, 22, 24, 26, 28, 32, 36, 39],
         [21.5, 20.8, 20.3, 19.5, 18.9, 18.4, 18.0, 16.0, 15.1, 16.1, 16.8, 17.6, 19.4, 21.0, 23.6, 25.2, 26.5, 27.3,
          26.9, 26.8, 26.4, 28.5])
# The dark bands over the back, as pixel runs: (z where a band crosses the spine, its width in texels there, how far
# down the side it reaches in units). Narrow bands from the neck to the tail tip, a ring round the tail.
STRIPES = [(25.8, 2, 2.0), (23.4, 3, 3.0), (20.9, 3, 3.6), (18.2, 3, 4.4), (15.0, 4, 5.2), (11.8, 4, 6.0),
           (8.6, 4, 6.6), (5.4, 5, 7.0), (2.2, 5, 7.0), (-1.0, 5, 6.8), (-4.2, 4, 6.2), (-7.4, 4, 5.6), (-10.6, 4, 5.0),
           (-13.8, 4, 4.4), (-17.0, 3, 4.0), (-20.4, 3, 3.6), (-23.8, 3, 3.2), (-27.3, 3, 2.9), (-30.8, 3, 2.6),
           (-34.3, 3, 2.3), (-37.9, 3, 2.0), (-41.5, 2, 1.8), (-45.1, 2, 1.5), (-48.7, 2, 1.3), (-52.3, 2, 1.0)]
RUNS = [stripe_runs(71 + k, w, 200) for k, (_, w, _) in enumerate(STRIPES)]
EYE = (31.8, 32.82)       # (z, y): the eye, where the socket and the stripe through it are painted


def back_y(z):
    return np.interp(z, *BACK)


def under_y(z):
    return np.interp(z, *UNDER)


def stripes(tx):
    """The dark bands of STRIPES on a body face: across the back on top, then down the sides in their runs, counted
    on the face's own texels along its box (a sloped neck slab counts along its slope)."""
    p, n, box = tx.points, tx.normal, tx.box
    band = np.zeros(p.shape[:2], bool)
    axis = box.axes[:, 2]
    if abs(n @ axis) > 0.7 or n[1] < -0.5:
        return band
    count = max(1, int(round(box.size[2] / tx.texel)))
    local = (p - box.center) @ axis
    iz = np.clip(np.floor((local / box.size[2] + 0.5) * count).astype(int), 0, count - 1)
    rows = np.floor((back_y(p[..., 2]) - p[..., 1] + np.abs(p[..., 0]) * 0.35) / tx.texel).astype(int)
    for (z0, width, reach), runs in zip(STRIPES, RUNS):
        along = (np.array([0.0, back_y(z0), z0]) - box.center) @ axis
        col = int(np.floor((along / box.size[2] + 0.5) * count))
        if col < -40 or col > count + 40:
            continue
        limit = int(reach / tx.texel)
        for first, last, shift, w in runs:
            if first > limit:
                break
            w = max(1, min(w, int(w * (1.15 - first / max(limit, 1)))))
            c0 = col + shift - w // 2
            band |= (rows >= first) & (rows <= min(last, limit)) & (iz >= c0) & (iz < c0 + w)
    return band


def countershade(tx):
    """0 on the underside to 1 along the spine, from the body's outline at the texel's z."""
    p = tx.points
    top, bottom = back_y(p[..., 2]), under_y(p[..., 2])
    return np.clip((p[..., 1] - bottom) / np.maximum(top - bottom, 0.5), 0, 1)


def hide(tx, pal):
    """The body's skin: pebbled scales over a dark back that lightens down the flanks, the bands, and the pale
    underside with its transverse belly scales."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part', 'trunk')
    f = countershade(tx)
    mottle = fbm(p, 3.0, seed=3, octaves=2) - 0.5
    v = 0.8 - 0.46 * f ** 1.15 + 0.14 * mottle + scales(tx, 3, seed=5)
    v += 0.04 * max(n[1], 0)
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    band = stripes(tx)
    if np.any(band):
        dark = shade(pal['stripe'], 0.45 + 0.6 * mottle + 0.6 * scales(tx, 3, seed=5), tx.x, tx.y, dither=0.0)
        colour = np.where(band[..., None], dark, colour)
    pebble = scales(tx, 3, seed=7)
    edge = 0.3 + 0.06 * (noise(p, 0.8, seed=8) - 0.5) + (0.05 if part == 'neck' else 0.0) + 0.9 * pebble
    under = (f < edge) | ((n[1] < -0.5) & (part != 'thigh'))
    if np.any(under):
        belly = 0.55 + 0.1 * mottle + 0.8 * scales(tx, 3, seed=6, seam=0.08)
        if n[1] < -0.5:                                # transverse belly scutes across the underside
            iz, _ = grid(tx, 2)
            belly = 0.6 + 0.06 * mottle - 0.16 * (iz % 4 == 0) + 0.04 * (iz % 4 == 1)
        elif part == 'neck' or (abs(n[2]) > 0.7 and p[..., 2].mean() > 10):
            col, row = face_grid(tx)                   # loose folds across the throat and the chest
            wave = np.floor(2.2 * np.sin(col * 0.21 + tx.face.index))
            belly = belly - 0.14 * ((row + wave.astype(int)) % 5 == 0)
        pale = shade(pal['belly'], belly, tx.x, tx.y, dither=0.0)
        colour = np.where(under[..., None], pale, colour)
    return colour


def limb(tx, pal):
    """Legs, arms and toes: rings of scales round the limb, darker outside, paler inside and below."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    mottle = fbm(p, 2.5, seed=13, octaves=2) - 0.5
    col, row = face_grid(tx)
    ring = (row // 2) % 2
    v = 0.5 + 0.12 * mottle + scales(tx, 2 if part in ('toe', 'finger', 'hand') else 3, seed=14) - 0.03 * ring
    if part == 'thigh':
        v = 0.42 + 0.12 * mottle + scales(tx, 3, seed=14) - 0.12 * np.clip((p[..., 1] - 17) / 9, 0, 1)
    if abs(n[0]) > 0.5 and n[0] * np.sign(tx.box.center[0]) < 0:
        v += 0.1                                       # the inside of the limb is paler
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    if part in ('thigh', 'shoulder', 'arm'):
        band = stripes(tx)
        if np.any(band):
            colour = np.where(band[..., None], shade(pal['stripe'], 0.5 + 0.5 * mottle, tx.x, tx.y, dither=0.0),
                              colour)
    if part in ('toe', 'foot', 'meta', 'finger', 'hand') and n[1] > 0.5:
        k, _ = grid(tx, 2)                             # broad scutes across the top of the toes and the foot
        colour = np.where((k % 3 == 0)[..., None], shade(pal['hide'], v - 0.14, tx.x, tx.y, dither=0.0), colour)
    if n[1] < -0.5:
        colour = shade(pal['belly'], 0.4 + 0.08 * mottle, tx.x, tx.y, dither=0.0)
    return colour


def head(tx, pal):
    """The head: the hide's scales under a dark crown, a dark band back from the eye, a pale jaw and throat, the
    row of lip scales along both jaws, rugged knobs along the nasal ridges, the nostrils near the tip."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    mottle = fbm(p, 2.0, seed=23, octaves=2) - 0.5
    col, row = face_grid(tx)
    top = np.interp(z, [27, 30, 33, 35, 37, 39], [35.1, 35.2, 35.2, 34.4, 33.0, 30.6])
    bottom = np.interp(z, [27, 31, 35, 38], [26.9, 26.8, 26.45, 26.3])
    f = np.clip((y - bottom) / (top - bottom), 0, 1)
    v = 0.8 - 0.44 * f ** 1.1 + 0.12 * mottle + scales(tx, 3, seed=24) + 0.04 * max(n[1], 0)
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    side = abs(n[0]) > 0.5
    jaw = tx.box.bone == JAW
    if side:
        # a dark band from the eye back along the temple, and the socket round the eye
        dz, dy = (z - EYE[0]) / tx.texel, (y - EYE[1]) / tx.texel
        back = np.clip(-dz, 0, 26)
        band = (dz < 3) & (dz > -26) & (np.abs(dy + back * 0.12) < 2.4 - back * 0.04)
        socket = (dz ** 2 / 36 + dy ** 2 / 20) < 1
        colour = np.where((band | socket)[..., None],
                          shade(pal['hide'], v - 0.22 + 0.4 * scales(tx, 3, seed=29), tx.x, tx.y, dither=0.0), colour)
        rim = ((dz ** 2 / 36 + dy ** 2 / 20) < 1.45) & ~socket & (dy > 0)
        colour = np.where(rim[..., None], shade(pal['hide'], v + 0.16, tx.x, tx.y, dither=0.0), colour)
        if part in ('snout', 'tip', 'cranium', 'temple') and not jaw:
            lip = row >= (tx.face.rect[3] - 2)         # the two rows of lip scales along the mouth
            k, _ = grid(tx, 2)
            light = (k // 2) % 2 == 0
            colour = np.where((lip & light)[..., None], shade(pal['belly'], 0.62 + 0.1 * mottle, tx.x, tx.y), colour)
            colour = np.where((lip & ~light)[..., None], shade(pal['hide'], 0.42, tx.x, tx.y), colour)
        if part == 'tip':                              # the nostril: a dark slit high on the side near the tip
            nostril = (np.abs(z - 37.95) < 0.45) & (np.abs(y - 30.65) < 0.2)
            colour = np.where(nostril[..., None], shade(pal['stripe'], 0.1, tx.x, tx.y), colour)
        if jaw:
            k, _ = grid(tx, 2)
            lip = row <= 1
            light = (k // 2) % 2 == 1
            colour = np.where((lip & light)[..., None], shade(pal['belly'], 0.58, tx.x, tx.y), colour)
            colour = np.where((lip & ~light)[..., None], shade(pal['hide'], 0.44, tx.x, tx.y), colour)
            pale = (row > 1) & (y < 27.7 + 0.3 * (noise(p, 0.7, seed=27) - 0.5))
            colour = np.where(pale[..., None], shade(pal['belly'], 0.52 + 0.08 * mottle + 0.5 * scales(tx, 3, seed=26),
                                                     tx.x, tx.y, dither=0.0), colour)
    if n[1] > 0.5 and part in ('ridge', 'snout_top', 'roof', 'brow'):
        knob = (cells(p, tx.texel * 2.02, seed=28) > 0.72) & (scales(tx, 3, seed=24) > -0.02)
        colour = np.where(knob[..., None], shade(pal['hide'], v + 0.16, tx.x, tx.y), colour)
    if part == 'ridge':
        colour = shade(pal['horn'], 0.45 + 0.5 * mottle + 0.3 * max(n[1], 0) + scales(tx, 2, seed=31), tx.x, tx.y,
                       dither=0.0)
    if n[1] < -0.5:
        colour = shade(pal['belly'], 0.48 + 0.06 * mottle + scales(tx, 3, seed=25), tx.x, tx.y, dither=0.0) if jaw or part == 'cranium' else \
            shade(pal['mouth'], 0.35 + 0.1 * mottle, tx.x, tx.y)     # the palate
    if jaw and n[1] > 0.5:
        i, ci = grid(tx, 0)                            # the mouth floor inside a rim of lip scales
        k, ck = grid(tx, 2)
        e = np.minimum(np.minimum(i, ci - 1 - i), ck - 1 - k if part == 'chin' else 99)
        colour = np.where((e <= 1)[..., None], shade(pal['hide'], 0.56 + 0.1 * mottle, tx.x, tx.y),
                          shade(pal['mouth'], 0.3 + 0.1 * mottle, tx.x, tx.y))
    return colour


def horn(tx, pal):
    """The lacrimal horns: rust-red keratin, darker at the base, with grooves up the blade."""
    p, n = tx.points, tx.normal
    t = np.clip((p[..., 1] - 34.5) / 2.0, 0, 1)
    col, _ = face_grid(tx)
    v = 0.3 + 0.55 * t + 0.1 * (cells(p, tx.texel * 1.01, seed=33) - 0.5) - 0.12 * (col % 3 == 0) + 0.08 * max(n[1], 0)
    return shade(pal['horn'], v, tx.x, tx.y, dither=0.0)


def teeth(tx):
    """A row of pointed teeth as pixel art: each tooth a few texels wide, tapering to its tip, the rest clear."""
    box = tx.box
    p = tx.points
    along = 2 if box.tag['row'] != 'front' else 0
    a = (p[..., along] - (box.center[along] - box.size[along] / 2)) / tx.texel
    h = (p[..., 1] - (box.center[1] - box.size[1] / 2)) / tx.texel
    rows = box.size[1] / tx.texel
    width = 3.0
    k = np.floor(a / width)
    f = a / width - k
    zc = box.center[2] - box.size[2] / 2 + (k + 0.5) * width * tx.texel
    grow = np.interp(zc, [30.5, 32.0, 34.0, 36.5, 38.5], [0.6, 0.9, 1.0, 0.8, 0.6]) if along == 2 else 0.7
    length = rows * grow * (0.8 + 0.2 * cells(np.stack([k, np.zeros_like(k), np.full(k.shape, 7.0)], -1), 1.0, seed=5))
    root = (rows - h) if box.tag['row'] != 'lower' else h          # distance from the gum
    half = 0.5 * (1 - np.clip(root / np.maximum(length, 1e-3), 0, 1)) ** 0.8 + 0.08
    alpha = (np.abs(f - 0.5) <= half) & (root <= length)
    v = 0.85 - 0.45 * (root / np.maximum(length, 1)) ** 2
    v = np.where(root < 1, 0.35, v)                                 # stained at the gum
    return shade(TOOTH_RAMP, v, tx.x, tx.y, dither=0.0), alpha


def keratin(tx, ramp):
    """Claws and scutes: dark at the base, lighter to the tip."""
    box, n = tx.box, tx.normal
    if 'along' in box.tag:
        a, b = box.tag['along']
        s = ((tx.points - box.center) @ box.axes[:, 1]) / box.size[1] + 0.5
        t = a + (b - a) * np.clip(s, 0, 1)
    else:
        t = np.clip((tx.points[..., 1] - (box.center[1] - box.size[1] / 2)) / box.size[1], 0, 1)
    v = 0.25 + 0.55 * t + 0.12 * (cells(tx.points, tx.texel * 1.01, seed=41) - 0.5) + 0.06 * max(n[1], 0)
    return shade(ramp, v, tx.x, tx.y, dither=0.0)


def painter(tx, variant):
    pal = PALETTES[variant]
    mat = tx.box.mat
    part = tx.box.tag.get('part')
    if mat == 'tooth':
        return teeth(tx)
    if mat == 'hide':
        if part in ('trunk', 'tail', 'neck'):
            return hide(tx, pal), None
        return limb(tx, pal), None
    if mat == 'head':
        return head(tx, pal), None
    if mat == 'horn':
        return horn(tx, pal), None
    if mat == 'scute':
        return keratin(tx, pal['scute']), None
    if mat == 'claw':
        return keratin(tx, CLAW_RAMP), None
    return shade(TONGUE_RAMP, 0.55 + 0.25 * (cells(tx.points, tx.texel * 1.01, seed=43) - 0.5), tx.x, tx.y), None
