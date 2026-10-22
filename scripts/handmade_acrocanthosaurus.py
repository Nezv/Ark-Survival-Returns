"""The Acrocanthosaurus, designed box by box on the ARK rig (Acro); built by scripts/handmade_models.py.

A big high-spined theropod in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a deep
trunk whose back rises into a tall narrow ridge over the hips and shoulders, carried on heavy thighs, thick calves,
long feet with a small dewclaw, and a long tail that tapers from the ridge to a point. The neck climbs steeply to a
long skull with a rugose brow ridge over each eye; a row of tall thin spines stands along the nape and smaller ones
along the end of the tail. The lower jaw (the rig's lowerLipJoint0_M) is designed shut as the idle holds it, the
teeth are cut-out rows along both jaws, and the arms end in three hooked claws.
"""
import math

import numpy as np

from handmade_models import cells, fbm, hexes, lift, noise, shade
from handmade_tyrannosaurus import face_grid, grid, midline_top, scales, stripe_runs

FOLDER, STEM, HEIGHT = 'Acrochantosaur', 'acrochantosaur', 14.4
DENSITY = 1.0            # texels per runtime unit, Minecraft's own density
ATLAS_WIDTH = 1024
PREVIEW_VARIANT = 'Natural'
ASSET = 'Acro_Mesh'
PREVIEW_CLIPS = ('Acro_Idle', 'Acro_Move_Walk_FWD', 'Acro_Attack_Roar', 'Acro_Torp_Loop')
JAW_POSE = ('Acro_Idle', 0.0, ['Lip', 'Jaw', 'Tongue', 'Teeth'])     # the frame the jaw is designed in
HEAD, JAW = 'Head_M', 'lowerLipJoint0_M'

# The trunk, segment by segment: (bone, rear z, front z, belly, top of the ridge, half width) from the ARK mesh's
# sections; the tiers across each follow the body's cross-section up into the ridge (TIER_SHAPE).
TRUNK = [('Root_M', -4.1, 0.5, 16.2, 39.8, 4.9), ('Root_M', 0.2, 4.6, 15.8, 41.1, 5.75),
         ('Root_M', 4.3, 7.8, 15.2, 41.35, 5.6), ('Spine1_M', 7.5, 11.0, 15.6, 41.5, 5.3),
         ('Spine1_M', 10.7, 14.4, 16.6, 41.7, 5.35), ('Spine2_M', 14.1, 17.5, 16.9, 41.6, 5.15),
         ('Spine2_M', 17.2, 20.9, 17.6, 41.0, 4.5), ('Chest_M', 20.6, 24.4, 18.8, 39.4, 3.7)]
TIER_SHAPE = [(0.0, 0.1, 0.38), (0.1, 0.25, 0.8), (0.25, 0.6, 1.0), (0.6, 0.76, 0.72), (0.76, 0.88, 0.38),
              (0.88, 1.0, 0.2)]
# The tail's outline along z: (z, underside, top, half width), measured on the ARK mesh's sections.
TAIL = [(-74, 23.98, 24.42, 0.2), (-71, 23.9, 24.7, 0.34), (-68, 23.77, 24.98, 0.44), (-65, 23.66, 25.29, 0.6),
        (-62, 23.63, 25.49, 0.7), (-59, 23.43, 25.69, 0.81), (-56, 23.2, 25.83, 0.91), (-53, 22.98, 26.15, 1.06),
        (-50, 22.79, 26.44, 1.17), (-47, 22.58, 26.74, 1.29), (-44, 22.3, 26.99, 1.38), (-41, 21.97, 27.33, 1.52),
        (-38, 21.61, 27.81, 1.63), (-35, 21.34, 28.26, 1.84), (-32, 21.09, 28.97, 2.03), (-29, 20.83, 29.84, 2.28),
        (-26, 20.55, 30.74, 2.45), (-23, 20.22, 31.86, 2.65), (-20, 19.93, 32.9, 2.91), (-17, 19.63, 34.1, 3.21),
        (-14, 19.28, 35.39, 3.57), (-11, 18.82, 36.74, 3.79), (-8, 17.33, 38.11, 3.95), (-5, 16.22, 39.3, 4.39)]
TAIL_BONES = ['Tail0_M', 'Tail0Part1_M', 'Tail0Part2_M', 'Tail1_M', 'Tail1Part1_M', 'Tail1Part2_M', 'Tail2_M',
              'Tail2Part1_M', 'Tail2Part2_M', 'Tail3_M', 'Tail3Part1_M', 'Tail3Part2_M', 'Tail4_M', 'Tail4Part1_M',
              'Tail4Part2_M', 'Tail5_M', 'Tail5Part1_M', 'Tail5Part2_M']
# The neck climbs steeply to the head: per bone (rear z, front z, half width), laid along the top line of its back
# and the bottom line of its throat.
NECK = [('Neck0_M', 24.1, 28.6, 3.7), ('Neck1_M', 28.4, 32.4, 3.0), ('Neck5_M', 32.2, 37.0, 2.9)]
NECK_TOP = ([22.0, 24.0, 26.0, 28.0, 30.0, 32.0, 34.0, 36.0, 37.0], [39.4, 39.2, 40.6, 42.3, 43.3, 43.6, 43.4, 42.9, 42.5])
NECK_THROAT = ([21.0, 23.0, 25.0, 27.0, 29.0, 31.0, 33.0, 35.0, 37.0], [18.4, 19.4, 21.0, 24.4, 27.6, 29.4, 30.6, 31.2, 31.4])


def tail_at(z):
    zs = [t[0] for t in TAIL]
    return tuple(float(np.interp(z, zs, [t[k] for t in TAIL])) for k in (1, 2, 3))


def neck_section(d, bone, z0, z1, half, mat='hide'):
    """Four slabs along the neck between z0 and z1: a narrow crest on the top line, the upper and lower cores that
    meet in the middle, and a narrow keel along the throat, each sloped like its line."""
    top = lambda z: float(np.interp(z, *NECK_TOP))
    low = lambda z: float(np.interp(z, *NECK_THROAT))
    zm = (z0 + z1) / 2
    slope_top = math.atan2(top(z1) - top(z0), z1 - z0)
    slope_low = math.atan2(low(z1) - low(z0), z1 - z0)
    depth = (top(zm) - low(zm)) * math.cos((slope_top + slope_low) / 2)
    core = depth * 0.5 + 0.9
    a, b = z0 - 0.4, z1 + 0.4                        # the cores reach past the bends, so no wedge opens between them
    d.slab(bone, (top(z0), z0), (top(z1), z1), half * 0.55, 1.2, mat, part='neck', tier=2)
    d.slab(bone, (top(a) - 1.1, a), (top(b) - 1.1, b), half, core, mat, part='neck', tier=1)
    d.slab(bone, (low(a) + 1.1, a + 0.05), (low(b) + 1.1, b - 0.05), half * 0.93, core, mat, top=False,
           part='neck', tier=-1)
    d.slab(bone, (low(z0), z0 + 0.25), (low(z1), z1 - 0.25), half * 0.62, 1.2, mat, top=False, part='neck', tier=-2)


def tiers(d, bone, z0, z1, rows, mat='hide', part='trunk', inset=0.03):
    """Stacked boxes across a segment, each overlapping the next a little; the tiers off the core are inset a
    hair at both ends, so no two neighbours share an end plane."""
    core = max(range(len(rows)), key=lambda k: rows[k][2])
    for k, (y0, y1, half) in enumerate(rows):
        e = 0.0 if k == core else inset * (1 + abs(k - core)) + (0.015 if k > core else 0.0)
        lo = y0 - (0.04 if k > core else 0.0)
        hi = y1 + (0.04 if k < core else 0.0)
        d.span(bone, (-half, lo, z0 + e), (half, hi, z1 - e), mat, part=part, tier=k - core, rows=len(rows))


def bone_at(z):
    """The spine bone that carries the body at z."""
    for bone, start in (('Head_M', 36.79), ('Neck5_M', 32.35), ('Neck1_M', 28.57), ('Neck0_M', 24.67),
                        ('Chest_M', 20.74), ('Spine2_M', 14.18), ('Spine1_M', 7.59), ('Root_M', -3.97)):
        if z >= start:
            return bone
    k = min(int((-3.97 - z) // 3.985), len(TAIL_BONES) - 1)
    return TAIL_BONES[k]


def design(d):
    rig = d.rig
    P = rig.pivot
    # the lower jaw and the tongue are designed shut, as the idle holds them
    for bone in rig.bones:
        if bone == JAW or bone.startswith('Tongue'):
            rig.design_as_in(bone, 'Acro_Idle', 0.0, HEAD)

    # ---------------------------------------------------------------- trunk with its ridge, tail and neck
    for bone, z0, z1, belly, back, half in TRUNK:
        rows = [(belly + (back - belly) * a, belly + (back - belly) * b, half * w) for a, b, w in TIER_SHAPE]
        tiers(d, bone, z0, z1, rows)
    for k, bone in enumerate(TAIL_BONES):
        front = -3.97 - 3.985 * k
        rear = max(front - 3.985, -74.4)
        low, high, half = tail_at((front + rear) / 2)
        h = high - low
        rows = [(low, low + 0.15 * h, half * 0.6), (low + 0.15 * h, low + 0.6 * h, half),
                (low + 0.6 * h, low + 0.82 * h, half * 0.7), (low + 0.82 * h, high, half * 0.32)]
        tiers(d, bone, rear, front + (0.3 if k else 0.25), rows, part='tail', inset=0.05)
    for bone, z0, z1, half in NECK:
        neck_section(d, bone, z0, z1, half)

    # ---------------------------------------------------------------- head: a long skull under rugose brow ridges
    d.span(HEAD, (-3.1, 35.9, 36.6), (3.1, 38.45, 38.9), 'head', part='cranium')
    d.span(HEAD, (-2.4, 38.35, 36.7), (2.4, 42.1, 38.85), 'head', part='cranium')
    d.span(HEAD, (-1.5, 42.0, 36.9), (1.5, 42.65, 41.4), 'head', part='roof')
    # the face round the eye: one flat side where the eye is set (build_creature_eyes footprint), a brow ridge above
    d.span(HEAD, (-2.2, 38.3, 38.8), (2.2, 41.5, 42.6), 'head', part='temple')
    d.span(HEAD, (-2.7, 35.6, 38.8), (2.7, 38.4, 42.6), 'head', part='cheek')
    d.span(HEAD, (-2.0, 41.4, 38.6), (2.0, 42.2, 43.2), 'head', part='brow')
    # the long snout, stepping down to the nostrils
    d.span(HEAD, (-2.0, 35.3, 42.5), (2.0, 38.0, 45.5), 'head', part='snout')
    d.span(HEAD, (-1.45, 37.9, 42.55), (1.45, 40.9, 45.45), 'head', part='snout_top')
    d.span(HEAD, (-1.6, 35.2, 45.4), (1.6, 37.6, 48.2), 'head', part='snout')
    d.span(HEAD, (-1.05, 37.5, 45.55), (1.05, 39.6, 48.05), 'head', part='snout_top')
    d.span(HEAD, (-1.15, 35.1, 48.1), (1.15, 38.2, 50.0), 'head', part='tip')
    d.span(HEAD, (-0.7, 35.4, 49.9), (0.7, 37.2, 50.6), 'head', part='tip')
    # the upper teeth: cut-out rows hanging from the lip, outside the narrower lower jaw, and across the front
    teeth = dict(open=('up', 'down', 'north', 'south'), cutout=True, keep=True, mirror=True)
    for z0, z1, x, top, low in ((39.6, 42.6, 2.35, 35.75, 34.4), (42.6, 45.5, 1.75, 35.45, 34.1),
                                (45.5, 48.2, 1.32, 35.35, 34.05), (48.2, 49.85, 0.92, 35.25, 34.15)):
        d.span(HEAD, (x, low, z0), (x + 0.12, top, z1), 'tooth', part='teeth', row='upper', **teeth)
    d.span(HEAD, (-0.85, 34.2, 49.86), (0.85, 35.5, 49.97), 'tooth', part='teeth', row='front',
           open=('up', 'down', 'east', 'west'), cutout=True, keep=True)

    d.span(JAW, (-2.75, 31.1, 36.8), (2.75, 36.0, 39.6), 'head', part='jaw')
    d.span(JAW, (-2.2, 31.3, 39.5), (2.2, 35.7, 42.6), 'head', part='jaw')
    d.span(JAW, (-1.6, 32.0, 42.5), (1.6, 35.4, 45.5), 'head', part='jaw')
    d.span(JAW, (-1.2, 32.7, 45.4), (1.2, 35.3, 48.2), 'head', part='jaw')
    d.span(JAW, (-0.85, 32.8, 48.1), (0.85, 35.2, 49.8), 'head', part='chin')
    for z0, z1, x in ((39.8, 42.5, 1.95), (42.6, 45.4, 1.38), (45.5, 48.1, 1.0)):
        d.span(JAW, (x, 35.1, z0), (x + 0.12, 36.25, z1), 'tooth', part='teeth', row='lower', **teeth)
    d.span('Tongue1Joint_M', (-1.0, 35.75, 38.5), (1.0, 35.95, 43.5), 'mouth', part='tongue')

    # ---------------------------------------------------------------- spines along the nape and the end of the tail
    body = list(d.boxes)
    for z, h in SPIKES:
        y = midline_top(body, z)
        d.seg(bone_at(z), (0.0, y - 0.3, z + 0.15), (0.0, y + h, z - 0.35), 0.18 + 0.06 * h, 0.3 + 0.1 * h, 'scute',
              side=(1, 0, 0), part='spike', along=(0.0, 1.0))

    # ---------------------------------------------------------------- arms: three hooked claws
    d.span('ShoulderUpper1_L', (3.0, 22.6, 19.4), (5.5, 28.0, 24.3), 'hide', mirror=True, part='shoulder')
    elbow, wrist = P('Elbow_L'), P('Wrist_L')
    d.seg('Shoulder_L', P('Shoulder_L'), elbow, 2.2, 2.4, 'hide', ext=(0.5, 0.45), mirror=True, part='arm')
    d.seg('Elbow_L', elbow, wrist, 1.75, 1.8, 'hide', ext=(0.35, 0.3), mirror=True, part='forearm')
    d.seg('Wrist_L', wrist, P('MiddleFinger5_L'), 1.35, 1.9, 'hide', ext=(0.2, 0.2), mirror=True, part='hand')
    for finger, w in (('Middle', 0.7), ('Ring', 0.68), ('Pinky', 0.62)):
        a, b, c = (P(f'{finger}Finger{k}_L') for k in (5, 7, 8))
        tip = c + (c - b) * 1.6 + np.array([0.0, -0.3, 0.1])
        d.seg(f'{finger}Finger5_L', a, b, w, w + 0.08, 'hide', ext=(0.2, 0.05), mirror=True, part='finger')
        d.seg(f'{finger}Finger7_L', b, c, w * 0.85, w * 0.9, 'hide', ext=(0.05, 0.05), mirror=True, part='finger')
        d.seg(f'{finger}Finger8_L', c, tip, w * 0.6, w * 0.72, 'claw', ext=(0.04, 0.0), mirror=True, part='claw',
              along=(0.0, 1.0))

    # ---------------------------------------------------------------- legs
    d.span('Rump_L', (2.8, 17.2, -5.0), (7.7, 27.8, 6.8), 'hide', mirror=True, part='thigh')
    d.span('Rump_L', (3.5, 27.7, -3.8), (7.1, 30.3, 4.5), 'hide', mirror=True, part='thigh')
    d.span('Rump_L', (3.1, 15.6, -2.4), (7.5, 17.3, 5.8), 'hide', mirror=True, part='thigh')
    d.seg('Hip_L', (5.78, 16.0, 4.0), (5.78, 13.0, -2.0), 4.6, 4.4, 'hide', ext=(0.0, 0.0), mirror=True, part='shin')
    d.seg('Hip_L', (5.78, 13.25, -1.6), (5.78, 10.4, -7.2), 3.6, 3.3, 'hide', ext=(0.0, 0.4), mirror=True, part='shin')
    d.seg('Knee_L', (5.78, 10.5, -7.9), (5.78, 2.9, -5.0), 3.1, 2.7, 'hide', mirror=True, part='meta')
    d.span('Ankle_L', (4.0, 1.0, -6.9), (7.6, 3.6, -3.4), 'hide', mirror=True, part='foot')
    d.span('Toes_L', (3.9, -0.2, -6.4), (8.0, 1.4, -3.3), 'hide', mirror=True, part='foot')
    for chain, tip, w in ((('toe_mid1_L', 'toe_mid2_L', 'toe_mid3_L', 'toe_mid4_L'), (6.45, 0.45, 1.6), 1.45),
                          (('toe_in1_L', 'toe_in2_L', 'toe_in3_L', 'toe_in4_L'), (3.4, 0.4, 1.05), 1.3),
                          (('toe_out5_L', 'toe_out6_L', 'toe_out7_L', 'toe_out8_L'), (9.1, 0.4, 0.95), 1.25),
                          (('sideToe2_L', 'sideToe3_L'), (3.9, 3.55, -5.55), 0.75)):
        points = [P(b) for b in chain] + [np.array(tip)]
        for k, bone in enumerate(chain):
            a, b = points[k], points[k + 1]
            if k < len(chain) - 1:
                d.seg(bone, a, b, w * (1 - 0.08 * k), w * (1 - 0.08 * k), 'hide', ext=(0.25 if k == 0 else 0.06, 0.05),
                      mirror=True, part='toe')
            else:
                d.seg(bone, a, b, w * 0.55, w * 0.62, 'claw', ext=(0.06, 0.0), mirror=True, part='claw',
                      along=(0.0, 1.0))


# The spines: (z, height above the skin) along the nape and the end of the tail; tallest behind the head.
SPIKES = [(26.6, 0.9), (27.7, 1.2), (28.8, 1.6), (29.9, 2.0), (31.0, 2.4), (32.1, 2.6), (33.2, 2.6), (34.3, 2.4),
          (35.4, 2.1), (36.5, 1.6), (37.5, 1.1), (38.4, 0.6)] + \
         [(round(float(z), 2), round(1.15 - 0.6 * (-38.0 - z) / 35.0, 2)) for z in np.arange(-38.0, -73.5, -1.7)]


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(hide=hexes('#24170f', '#342217', '#462e1f', '#593b28', '#6d4932', '#82583d', '#986849', '#ae7956'),
                    belly=hexes('#8a7258', '#9b8469', '#ac967b', '#bca88d', '#cbb99f', '#d9cab1'),
                    stripe=hexes('#120b07', '#1a100a', '#22160e', '#2b1c12'),
                    scute=hexes('#4a3d2c', '#62533e', '#7c6b52', '#968468', '#b09d80'),
                    horn=hexes('#2e1810', '#432318', '#5a3021', '#723e2b', '#8b4d36'),
                    mouth=hexes('#3a1414', '#55201f', '#71302c', '#8c423b', '#a5574c', '#bb6e5e')),
}
PALETTES['umber'] = PALETTES['Natural']
PALETTES['olive'] = dict(hide=hexes('#22231a', '#303122', '#3f402b', '#4f5034', '#60603e', '#717148', '#838252', '#95935e'),
                         belly=hexes('#7d755a', '#8f8769', '#a19979', '#b2ab8a', '#c2bb9b', '#d1caac'),
                         stripe=hexes('#0f100b', '#161710', '#1d1e15', '#25261a'),
                         scute=hexes('#2b2a1f', '#3d3c2c', '#504f3a', '#64634a', '#79785b'),
                         horn=hexes('#36190f', '#4f2416', '#69311e', '#833f27', '#9c4e31'),
                         mouth=PALETTES['Natural']['mouth'])
PALETTES['rust'] = dict(hide=hexes('#2b140d', '#3d1e14', '#50291b', '#643523', '#79422b', '#8e5034', '#a45f3e', '#ba6e49'),
                        belly=hexes('#8c6650', '#9d7861', '#ae8a73', '#bf9c85', '#cfae97', '#ddc0a9'),
                        stripe=hexes('#140806', '#1d0c09', '#26110c', '#30160f'),
                        scute=hexes('#2e1810', '#432318', '#5a3021', '#723e2b', '#8b4d36'),
                        horn=hexes('#2a1a14', '#3d261d', '#513327', '#664132', '#7c503d'),
                        mouth=PALETTES['Natural']['mouth'])
PALETTES['slate'] = lift(dict(hide=hexes('#1d1e1d', '#2a2b29', '#383936', '#474843', '#575750', '#68675f', '#79786e', '#8b897e'),
                              belly=hexes('#7f7a6c', '#8f8a7c', '#a09a8c', '#b0ab9c', '#bfbaac', '#cdc9bb'),
                              stripe=hexes('#0d0d0d', '#131313', '#1a1a19', '#22221f'),
                              scute=hexes('#2a2a27', '#3b3b37', '#4e4e48', '#62625a', '#77776d'),
                              horn=hexes('#1c1a17', '#2a2722', '#39352e', '#49443b', '#5a5449'),
                              mouth=PALETTES['Natural']['mouth']), 1.1, 4)
TOOTH_RAMP = hexes('#8a7f66', '#ab9f83', '#c9bf9f', '#e0d8bc', '#efe9d5', '#faf7ec')
CLAW_RAMP = hexes('#100e0b', '#1d1914', '#2b251e', '#3b3329', '#4e4436', '#655845')
TONGUE_RAMP = hexes('#5e2526', '#7a3333', '#954442', '#ad5853', '#c26e66')

# The body's outline along its length (z), from the tail tip to the snout: the top of the ridge and the underside.
BACK = ([-74, -62, -50, -38, -26, -17, -11, -5, 1, 7, 13, 19, 22, 24, 26, 28, 30, 32, 34, 36, 38, 41, 44, 47, 50],
        [24.4, 25.5, 26.4, 27.8, 30.7, 34.1, 36.7, 39.3, 40.9, 41.4, 41.7, 41.3, 39.4, 39.2, 40.6, 42.3, 43.3, 43.6,
         43.4, 42.9, 42.6, 42.2, 41.5, 39.8, 37.5])
UNDER = ([-74, -62, -50, -38, -26, -17, -11, -5, 1, 7, 13, 19, 22, 25, 27, 29, 31, 33, 35, 37, 40, 43, 46, 49, 50],
         [24.0, 23.6, 22.8, 21.6, 20.55, 19.63, 18.8, 16.2, 16.1, 15.0, 16.8, 17.4, 18.6, 21.0, 24.4, 27.6, 29.4,
          30.6, 31.2, 31.1, 31.3, 32.0, 32.7, 32.8, 33.0])
# The dark bands over the back, as pixel runs: (z where a band crosses the ridge, its width in texels there, how far
# down the side it reaches in units). Broad bands down the ridge and flanks, narrowing to rings round the tail.
STRIPES = [(33.5, 4, 3.0), (30.4, 5, 4.0), (27.3, 5, 5.0), (23.6, 6, 7.0), (19.6, 7, 9.0), (15.6, 7, 10.0),
           (11.6, 8, 10.5), (7.6, 8, 10.5), (3.6, 8, 10.0), (-0.4, 7, 9.5), (-4.4, 7, 8.6), (-8.6, 6, 7.6),
           (-12.8, 6, 6.8), (-17.0, 6, 6.0), (-21.4, 5, 5.2), (-25.8, 5, 4.6), (-30.2, 5, 4.0), (-34.6, 4, 3.4),
           (-39.0, 4, 3.0), (-43.4, 4, 2.6), (-47.8, 3, 2.2), (-52.2, 3, 1.8), (-56.6, 3, 1.5), (-61.0, 3, 1.2),
           (-65.4, 2, 0.9), (-69.8, 2, 0.6)]
RUNS = [stripe_runs(151 + k, w, 200) for k, (_, w, _) in enumerate(STRIPES)]
EYE = (40.38, 39.66)       # (z, y): the eye, where the socket and the stripe through it are painted


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
    row of lip scales along both jaws, rugged knobs along the brow ridges and the snout, the nostrils near the tip."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    mottle = fbm(p, 2.0, seed=23, octaves=2) - 0.5
    col, row = face_grid(tx)
    top = np.interp(z, [36, 38, 41, 44, 47, 50.6], [42.6, 42.6, 42.2, 41.5, 39.8, 37.2])
    bottom = np.interp(z, [37, 41, 45, 49], [31.1, 31.4, 32.7, 32.8])
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
        if part in ('snout', 'tip', 'cranium', 'cheek') and not jaw:
            lip = row >= (tx.face.rect[3] - 2)         # the two rows of lip scales along the mouth
            k, _ = grid(tx, 2)
            light = (k // 2) % 2 == 0
            colour = np.where((lip & light)[..., None], shade(pal['belly'], 0.62 + 0.1 * mottle, tx.x, tx.y), colour)
            colour = np.where((lip & ~light)[..., None], shade(pal['hide'], 0.42, tx.x, tx.y), colour)
        if part == 'tip':                              # the nostril: a dark slit high on the side near the tip
            nostril = (np.abs(z - 49.55) < 0.35) & (np.abs(y - 37.55) < 0.16)
            colour = np.where(nostril[..., None], shade(pal['stripe'], 0.1, tx.x, tx.y), colour)
        if jaw:
            k, _ = grid(tx, 2)
            lip = row <= 1
            light = (k // 2) % 2 == 1
            colour = np.where((lip & light)[..., None], shade(pal['belly'], 0.58, tx.x, tx.y), colour)
            colour = np.where((lip & ~light)[..., None], shade(pal['hide'], 0.44, tx.x, tx.y), colour)
            pale = (row > 1) & (y < 33.3 + 0.3 * (noise(p, 0.7, seed=27) - 0.5))
            colour = np.where(pale[..., None], shade(pal['belly'], 0.52 + 0.08 * mottle + 0.5 * scales(tx, 3, seed=26),
                                                     tx.x, tx.y, dither=0.0), colour)
    if n[1] > 0.5 and part in ('snout_top', 'roof', 'brow'):
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
    t = np.clip((p[..., 1] - 41.4) / 1.0, 0, 1)
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
    grow = np.interp(zc, [39.6, 41.5, 44.5, 47.5, 49.9], [0.6, 0.9, 1.0, 0.8, 0.6]) if along == 2 else 0.7
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
