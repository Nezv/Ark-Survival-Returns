"""The Velociraptor, designed box by box on the ARK rig (Raptor); built by scripts/handmade_models.py.

A lean pack hunter in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a short deep
trunk on muscular thighs, slim shins and two-toed feet whose raised second toe carries the hooked sickle claw; a long
stiff tail rising from the hips to a tuft of feathers; an S-shaped neck climbing almost straight up to a long low
skull with a narrow snout. The jaw is designed shut as the idle holds it, its teeth cut-out rows under the lips. A
crest of quills sweeps back from the crown and down the nape, a low ridge of spines runs along the back, and fans of
feathers hang behind the forearms; all of them are cut out of thin cards, blade by blade after the ARK mesh.
"""
import math

import numpy as np

from handmade_models import cells, fbm, hexes, lift, noise, shade
from handmade_tyrannosaurus import face_grid, grid, scales, stripe_runs

FOLDER, STEM, HEIGHT = 'Velociraptor', 'velociraptor', 3.0
DENSITY = 1.0            # texels per runtime unit; the head, quills and feathers are painted at FACE times that
FACE = 2.0
ATLAS_WIDTH = 256
PREVIEW_VARIANT = 'Natural'
ASSET = 'Raptor'
PREVIEW_CLIPS = ('Raptor-Idle', 'Raptor-Move-Fwd', 'Raptor-Attack', 'Raptor-Torpid')
JAW_POSE = ('Raptor-Idle', 0.0, ['Jaw', 'Tongue'])     # the frame the jaw is designed shut in
HEAD, JAW = 'Cnt_Head_JNT_SKL', 'Cnt_Jaw_JNT_SKL'

# The trunk, segment by segment: (bone, rear z, front z, tiers from the belly up as (bottom, top, half width)),
# measured on the ARK mesh's cross-sections, the legs and the spines left out.
TRUNK = [
    ('Cnt_Spine_000_JNT_SKL', -2.3, -0.55, [(4.95, 5.3, 0.5), (5.25, 5.75, 0.85), (5.7, 7.0, 1.1), (6.95, 7.5, 0.85),
                                            (7.45, 7.89, 0.5)]),
    ('Cnt_Spine_001_JNT_SKL', -0.8, 1.4, [(4.7, 5.1, 0.55), (5.05, 5.6, 0.95), (5.55, 7.35, 1.27), (7.3, 7.85, 0.95),
                                          (7.8, 8.2, 0.55)]),
    ('Cnt_Spine_003_JNT_SKL', 1.35, 2.9, [(4.85, 5.3, 0.5), (5.25, 5.85, 0.9), (5.8, 7.4, 1.15), (7.35, 7.9, 0.88),
                                          (7.85, 8.25, 0.52)]),
    ('Cnt_Spine_005_JNT_SKL', 2.85, 3.7, [(5.4, 5.8, 0.45), (5.75, 6.3, 0.75), (6.25, 7.45, 0.95), (7.4, 7.9, 0.75),
                                          (7.85, 8.2, 0.45)]),
]
# The tail's outline along z: (z, underside, top, half width), from the tip to the hips.
TAIL = [(-15.6, 9.2, 9.32, 0.03), (-14.5, 9.1, 9.42, 0.07), (-13.5, 8.95, 9.32, 0.1), (-12.5, 8.75, 9.15, 0.14),
        (-11.5, 8.42, 8.87, 0.19), (-10.5, 8.04, 8.64, 0.27), (-9.5, 7.65, 8.4, 0.34), (-8.5, 7.28, 8.15, 0.41),
        (-7.5, 6.9, 7.9, 0.48), (-6.5, 6.49, 7.66, 0.56), (-5.5, 6.09, 7.47, 0.64), (-4.5, 5.74, 7.39, 0.72),
        (-3.5, 5.47, 7.3, 0.85), (-2.5, 5.1, 7.48, 1.0), (-2.0, 4.9, 7.6, 1.05)]
TAIL_BONES = [('Cnt_Tail_014_JNT_SKL', -15.6, -14.4), ('Cnt_Tail_013_JNT_SKL', -14.4, -13.5),
              ('Cnt_Tail_012_JNT_SKL', -13.5, -12.67), ('Cnt_Tail_010_JNT_SKL', -12.67, -11.04),
              ('Cnt_Tail_008_JNT_SKL', -11.04, -9.37), ('Cnt_Tail_006_JNT_SKL', -9.37, -7.6),
              ('Cnt_Tail_004_JNT_SKL', -7.6, -5.83), ('Cnt_Tail_002_JNT_SKL', -5.83, -4.04),
              ('Cnt_Tail_000_JNT_SKL', -4.04, -2.15)]


def tail_at(z):
    """(underside, top, half width) of the tail at z."""
    zs = [t[0] for t in TAIL]
    return tuple(float(np.interp(z, zs, [t[k] for t in TAIL])) for k in (1, 2, 3))


def tiers(d, bone, z0, z1, rows, mat='hide', part='trunk', inset=0.03):
    """Stacked boxes across a segment, each overlapping the next a little; the tiers off the core are inset a
    hair at both ends, so no two neighbours share an end plane."""
    core = max(range(len(rows)), key=lambda k: rows[k][2])
    for k, (y0, y1, half) in enumerate(rows):
        e = 0.0 if k == core else inset * (1 + abs(k - core))
        d.span(bone, (-half, y0, z0 + e), (half, y1, z1 - e), mat, part=part, tier=k - core)


def card(d, bone, lo, hi, blades, x=0.0, half=0.03, yaw=0.0, roll=0.0, part='quill', **tag):
    """A thin cut-out card standing in the yz plane between (y, z) corners lo and hi, centred on x: its blades
    ((root y, z), (tip y, z), width at the root) are all that shows of it. Yaw and roll turn it about its centre;
    the blades are given as if it were not turned."""
    (y0, z0), (y1, z1) = lo, hi
    centre = (x, (y0 + y1) / 2, (z0 + z1) / 2)
    return d.turned(bone, centre, (2 * half, abs(y1 - y0), abs(z1 - z0)), yaw=yaw, roll=roll, mat='quill',
                    open=('up', 'down', 'north', 'south'), cutout=True, keep=True, density=FACE, part=part,
                    blades=blades, **tag)


def design(d):
    rig = d.rig
    P = rig.pivot
    # the jaw and the tongue are designed shut, as the idle holds them; the bind pose snarls
    for bone in (JAW, 'Cnt_Tongue_000_JNT_SKL', 'Cnt_Tongue_001_JNT_SKL', 'Cnt_Tongue_002_JNT_SKL'):
        rig.design_as_in(bone, 'Raptor-Idle', 0.0, HEAD)
    f = dict(density=FACE)

    # ---------------------------------------------------------------- trunk and tail
    for k, (bone, z0, z1, rows) in enumerate(TRUNK):
        tiers(d, bone, z0, z1, rows, inset=0.03 + 0.008 * k)
    # the tail: per bone a core and a taller, narrower keel, both laid along its rising centre line
    for k, (bone, z0, z1) in enumerate(TAIL_BONES):
        lo0, hi0, w0 = tail_at(z0)
        lo1, hi1, w1 = tail_at(z1)
        a = np.array([0.0, (lo0 + hi0) / 2, z0])
        b = np.array([0.0, (lo1 + hi1) / 2, z1])
        depth, half = ((hi0 - lo0) + (hi1 - lo1)) / 2, (w0 + w1) / 2
        ext = (0.16, 0.16 if k < len(TAIL_BONES) - 1 else 0.2)
        d.seg(bone, a, b, 2 * half, depth * (0.62 if k % 2 else 0.68), 'hide', ext=ext, side=(1, 0, 0), part='tail',
              tier=0)
        if half < 0.13:                                  # the tip is too thin for a keel
            continue
        # the keels stop a hair short of each other, the cores reach past them over the joints
        d.seg(bone, a, b, 2 * half * (0.5 if k % 2 else 0.7), depth * (1.0 if k % 2 else 0.95), 'hide',
              ext=(-0.02, -0.02), side=(1, 0, 0), part='tail', tier=1)

    # ---------------------------------------------------------------- neck: up from the chest, almost upright
    d.seg('Cnt_Spine_005_JNT_SKL', (0.0, 5.95, 3.0), (0.0, 7.5, 3.75), 1.58, 0.9, 'hide', side=(1, 0, 0),
          part='neck', tier=-1)
    d.seg('Cnt_Neck_000_JNT_SKL', (0.0, 7.0, 3.88), (0.0, 8.6, 4.08), 1.02, 1.18, 'hide', ext=(0.3, 0.1),
          side=(1, 0, 0), part='neck', tier=0)
    d.seg('Cnt_Neck_002_JNT_SKL', (0.0, 8.45, 4.05), (0.0, 9.95, 4.05), 0.94, 1.12, 'hide', ext=(0.1, 0.0),
          side=(1, 0, 0), part='neck', tier=0)
    d.span('Cnt_Neck_002_JNT_SKL', (-0.54, 8.45, 4.25), (0.54, 9.05, 4.88), 'hide', part='neck', tier=-1)

    # ---------------------------------------------------------------- head (painted at FACE density)
    d.span(HEAD, (-0.7, 9.45, 3.62), (0.7, 10.45, 4.8), 'head', part='cranium', **f)
    d.span(HEAD, (-0.55, 10.4, 3.78), (0.55, 10.66, 4.9), 'head', part='crown', **f)
    d.span(HEAD, (-0.66, 9.5, 4.75), (0.66, 10.36, 5.76), 'head', part='temple', **f)
    d.span(HEAD, (-0.5, 10.3, 4.85), (0.5, 10.54, 5.62), 'head', part='brow', **f)
    d.span(HEAD, (-0.48, 9.45, 5.7), (0.48, 10.06, 6.55), 'head', part='snout', **f)
    d.span(HEAD, (-0.36, 10.0, 5.6), (0.36, 10.22, 6.32), 'head', part='bridge', **f)
    d.span(HEAD, (-0.3, 9.47, 6.5), (0.3, 9.92, 7.02), 'head', part='tip', **f)
    teeth = dict(open=('up', 'down', 'north', 'south'), cutout=True, keep=True, mirror=True, density=FACE)
    d.span(HEAD, (0.44, 9.24, 4.95), (0.462, 9.48, 6.5), 'tooth', part='teeth', row='upper', **teeth)
    d.span(HEAD, (0.262, 9.27, 6.5), (0.284, 9.49, 6.95), 'tooth', part='teeth', row='upper', **teeth)
    # the shut jaw, narrower than the snout, so the upper teeth close outside it
    d.span(JAW, (-0.62, 8.95, 4.3), (0.62, 9.47, 5.6), 'head', part='jaw', **f)
    d.span(JAW, (-0.42, 8.98, 5.55), (0.42, 9.46, 6.52), 'head', part='jaw', **f)
    d.span(JAW, (-0.25, 9.05, 6.48), (0.25, 9.43, 6.96), 'head', part='chin', **f)

    # ---------------------------------------------------------------- quills, spines and feathers (cut-out cards)
    card(d, HEAD, (10.25, 1.65), (11.32, 4.85), CROWN_QUILLS, part='quill')
    card(d, HEAD, (10.2, 2.1), (11.12, 4.6), SIDE_QUILLS, x=0.27, half=0.025, yaw=-9.0, roll=-14.0, part='quill',
         mirror=True)
    card(d, 'Cnt_Neck_002_JNT_SKL', (8.3, 1.75), (10.45, 3.62), NAPE_QUILLS, half=0.012, part='quill')
    for k, (bone, z0, z1) in enumerate(SPINE_CARDS):
        lo = min(back_y(z0), back_y(z1)) - 0.3
        hi = max(y for (_, _), (y, _), _ in spines(z0, z1)) + 0.05
        card(d, bone, (lo, z0), (hi, z1), spines(z0, z1), half=0.042 + 0.012 * (k % 2), part='spine')
    card(d, 'Cnt_Tail_012_JNT_SKL', (8.25, -16.2), (10.15, -12.3), TAIL_FEATHERS, half=0.015, part='feather')
    card(d, 'Cnt_Tail_012_JNT_SKL', (8.5, -15.9), (9.9, -12.5), TAIL_FEATHERS_SIDE, x=0.0, half=0.025, roll=38.0,
         part='feather', mirror=True)
    card(d, 'Lft_Arm_002Tear000_JNT_SKL', (3.75, 1.2), (5.6, 3.55), ARM_FEATHERS, x=0.9, part='feather', mirror=True)

    # ---------------------------------------------------------------- arms
    shoulder, elbow, wrist = P('Lft_Arm_001Tear000_JNT_SKL'), P('Lft_Arm_002Tear000_JNT_SKL'), \
        P('Lft_Arm_002Tear006_JNT_SKL')
    d.seg('Lft_Arm_001Tear000_JNT_SKL', shoulder, elbow, 0.44, 0.55, 'hide', ext=(0.15, 0.15), mirror=True,
          part='arm')
    d.seg('Lft_Arm_002Tear000_JNT_SKL', elbow, wrist, 0.36, 0.42, 'hide', ext=(0.1, 0.1), mirror=True, part='forearm')
    d.seg('Lft_Arm_002Tear006_JNT_SKL', wrist, (0.72, 3.72, 4.86), 0.48, 0.3, 'hide', ext=(0.08, 0.0), mirror=True,
          part='hand')
    for finger, w in (('Index', 0.17), ('Middle', 0.19), ('Little', 0.16)):
        a, b, c = (P(f'Lft_{finger}Finger_00{k}_JNT_SKL') for k in range(3))
        hook = c + np.array([0.0, -0.3, -0.05])
        tip = c + np.array([0.0, -0.55, -0.32])
        d.seg(f'Lft_{finger}Finger_000_JNT_SKL', a, b, w, w * 1.1, 'hide', ext=(0.08, 0.04), mirror=True,
              part='finger')
        d.seg(f'Lft_{finger}Finger_001_JNT_SKL', b, c, w * 0.9, w, 'hide', ext=(0.04, 0.03), mirror=True,
              part='finger')
        d.seg(f'Lft_{finger}Finger_002_JNT_SKL', c, hook, w * 0.75, w * 0.9, 'claw', ext=(0.03, 0.0), mirror=True,
              part='claw', along=(0.0, 0.6))
        d.seg(f'Lft_{finger}Finger_002_JNT_SKL', hook, tip, w * 0.5, w * 0.6, 'claw', ext=(0.02, 0.0), mirror=True,
              part='claw', along=(0.6, 1.0))

    # ---------------------------------------------------------------- legs
    thigh = 'Lft_Back_001Linear000_JNT_SKL'
    hip, knee, ankle = P(thigh), P('Lft_Back_002Linear000_JNT_SKL'), P('Lft_Back_003Linear000_JNT_SKL')
    d.span(thigh, (0.7, 5.21, -2.25), (1.62, 7.38, 0.35), 'hide', mirror=True, part='thigh')
    d.span(thigh, (0.9, 7.3, -1.95), (1.42, 7.65, -0.15), 'hide', mirror=True, part='thigh')
    d.seg(thigh, hip + (0.0, -0.9, -0.05), knee, 0.8, 1.35, 'hide', ext=(0.0, 0.25), mirror=True, part='thigh')
    mid = knee + (ankle - knee) * 0.45
    d.seg('Lft_Back_002Linear000_JNT_SKL', knee, mid, 0.58, 0.82, 'hide', ext=(0.2, 0.05), mirror=True, part='shin')
    d.seg('Lft_Back_002Linear000_JNT_SKL', mid, ankle, 0.42, 0.52, 'hide', ext=(0.05, 0.15), mirror=True,
          part='shin')
    foot, ball = P('Lft_Back_AIk004_JNT_SKL'), P('Lft_Back_AIk005_JNT_SKL')
    d.seg('Lft_Back_003Linear000_JNT_SKL', ankle, foot, 0.38, 0.42, 'hide', ext=(0.12, 0.1), mirror=True, part='meta')
    d.seg('Lft_Back_AIk004_JNT_SKL', foot, ball, 0.55, 0.38, 'hide', ext=(0.15, 0.1), mirror=True, part='foot')
    # the two walking toes, forward from the ball of the foot
    for bone, root, tip, w in (('Lft_Back_AIk005_JNT_SKL', (1.0, 0.2, 0.15), (1.02, 0.12, 1.42), 0.27),
                               ('Lft_Toe_01_JNT_SKL', (1.3, 0.2, 0.05), (1.5, 0.12, 1.1), 0.24)):
        root, tip = np.array(root), np.array(tip)
        knuckle = root + (tip - root) * 0.72
        d.seg(bone, root, knuckle, w, 0.26, 'hide', ext=(0.08, 0.02), side=(1, 0, 0), mirror=True, part='toe')
        d.seg(bone, knuckle, tip, w * 0.6, 0.2, 'claw', ext=(0.02, 0.0), side=(1, 0, 0), mirror=True, part='claw',
              along=(0.2, 1.0))
    # the raised second toe and its sickle claw, curling up and over to a point
    claw = 'Lft_Claw_JNT_SKL'
    d.seg(claw, (0.7, 0.25, -0.1), (0.68, 0.62, 0.32), 0.22, 0.3, 'hide', ext=(0.05, 0.04), side=(1, 0, 0),
          mirror=True, part='toe')
    for k, (a, b, w) in enumerate((((0.68, 0.62, 0.32), (0.68, 1.12, 0.62), 0.17),
                                   ((0.68, 1.12, 0.62), (0.68, 1.3, 1.02), 0.14),
                                   ((0.68, 1.3, 1.02), (0.68, 1.12, 1.33), 0.1))):
        d.seg(claw, a, b, w, w * 1.3, 'claw', ext=(0.03, 0.0), side=(1, 0, 0), mirror=True, part='sickle',
              along=(k / 3, (k + 1) / 3))


# The quills as blades: ((root y, z), (tip y, z), width at the root), after the ARK mesh's.
CROWN_QUILLS = [((10.62, 4.75), (11.18, 3.55), 0.36), ((10.66, 4.45), (11.28, 3.0), 0.39),
                ((10.66, 4.15), (11.2, 2.5), 0.31), ((10.62, 3.9), (11.02, 2.05), 0.31),
                ((10.52, 3.7), (10.75, 1.72), 0.29)]
SIDE_QUILLS = [((10.5, 4.5), (11.02, 3.2), 0.26), ((10.5, 4.15), (10.98, 2.6), 0.26), ((10.45, 3.85), (10.7, 2.18), 0.26)]
NAPE_QUILLS = [((10.3, 3.55), (10.32, 1.85), 0.31), ((9.95, 3.5), (9.92, 1.95), 0.31), ((9.55, 3.48), (9.48, 2.1), 0.31),
               ((9.15, 3.46), (9.02, 2.35), 0.29), ((8.78, 3.45), (8.62, 2.62), 0.26), ((8.45, 3.42), (8.36, 2.95), 0.21)]
TAIL_FEATHERS = [((9.28, -12.45), (10.05, -13.9), 0.29), ((9.33, -12.95), (10.1, -14.6), 0.31),
                 ((9.36, -13.5), (9.98, -15.3), 0.31), ((9.36, -14.1), (9.72, -15.95), 0.29),
                 ((9.3, -14.6), (9.38, -16.15), 0.26), ((9.15, -14.4), (8.9, -16.0), 0.26),
                 ((9.05, -13.8), (8.55, -15.45), 0.29), ((8.95, -13.2), (8.35, -14.7), 0.29),
                 ((8.88, -12.6), (8.32, -13.8), 0.26)]
TAIL_FEATHERS_SIDE = [((9.25, -12.75), (9.75, -14.2), 0.26), ((9.3, -13.4), (9.7, -15.1), 0.29),
                      ((9.25, -14.05), (9.35, -15.75), 0.26), ((9.0, -13.4), (8.65, -15.0), 0.26)]
ARM_FEATHERS = [((5.45, 2.8), (4.95, 1.38), 0.34), ((5.15, 2.78), (4.5, 1.3), 0.36), ((4.88, 2.82), (4.12, 1.48), 0.36),
                ((4.72, 3.05), (3.88, 1.95), 0.34), ((4.58, 3.38), (3.82, 2.58), 0.31)]
# The spines along the back: per card (bone, rear z, front z); spines() lays the blades out along it.
SPINE_CARDS = [('Cnt_Tail_002_JNT_SKL', -5.8, -4.04), ('Cnt_Tail_000_JNT_SKL', -4.04, -2.2),
               ('Cnt_Spine_000_JNT_SKL', -2.2, -0.6), ('Cnt_Spine_001_JNT_SKL', -0.6, 1.38),
               ('Cnt_Spine_003_JNT_SKL', 1.38, 2.9), ('Cnt_Spine_005_JNT_SKL', 2.9, 3.4)]
# The top of the body (without its spines) along z, from the tail tip to the shoulders.
BACK = ([-15.6, -14.5, -12.5, -10.5, -8.5, -6.5, -4.5, -3.5, -2.5, -1.5, -0.5, 0.5, 1.5, 2.5, 3.0, 3.4],
        [9.32, 9.42, 9.15, 8.64, 8.15, 7.66, 7.39, 7.3, 7.48, 7.78, 8.0, 8.17, 8.25, 8.22, 8.2, 8.25])
UNDER = ([-15.6, -14.5, -12.5, -10.5, -8.5, -6.5, -4.5, -3.5, -2.5, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0, 3.6],
         [9.2, 9.1, 8.75, 8.04, 7.28, 6.49, 5.74, 5.47, 5.1, 5.0, 4.95, 4.75, 4.75, 4.9, 5.36, 5.75])


def back_y(z):
    return float(np.interp(z, *BACK))


def spines(z0, z1):
    """The back's spines between z0 and z1: blades every 0.42 units leaning back, tallest over the shoulders and
    shrinking down the tail."""
    out = []
    for z in np.arange(math.ceil(z0 / 0.42) * 0.42, z1 - 0.1, 0.42):
        h = float(np.interp(z, [-5.8, -3.0, 0.0, 2.0, 3.4], [0.16, 0.28, 0.42, 0.5, 0.42]))
        y = back_y(z) - 0.05
        out.append(((y, z + 0.16), (y + h, z - 0.18), 0.26))
    return out


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(hide=hexes('#2d2619', '#3c3322', '#4c412c', '#5e5136', '#706142', '#83724f', '#97845c', '#ab966a'),
                    belly=hexes('#8a7b5e', '#9c8d70', '#ae9f82', '#bfb194', '#cfc2a6', '#ddd2b8'),
                    stripe=hexes('#17120c', '#211a12', '#2b2218', '#352b1f'),
                    quill=hexes('#3a160e', '#561f12', '#722b16', '#8e3a1d', '#a74c26', '#bd6131', '#d07a42')),
    'tan': dict(hide=hexes('#4a3b26', '#5c4a30', '#6f5a3a', '#836b45', '#977d51', '#ab905e', '#bfa36c', '#d2b67b'),
                belly=hexes('#b8a682', '#c6b592', '#d3c3a3', '#dfd1b4', '#eadec4', '#f3e9d4'),
                stripe=hexes('#2a1e12', '#352617', '#40301d', '#4c3a24'),
                quill=hexes('#3a160e', '#561f12', '#722b16', '#8e3a1d', '#a74c26', '#bd6131', '#d07a42')),
    'umber': dict(hide=hexes('#231912', '#30231a', '#3e2e22', '#4d3a2b', '#5d4734', '#6d553f', '#7e634a', '#8f7256'),
                  belly=hexes('#7c6a55', '#8e7c66', '#a08e78', '#b2a08a', '#c3b29c', '#d4c4ae'),
                  stripe=hexes('#0f0a07', '#160f0b', '#1d150f', '#251b14'),
                  quill=hexes('#2a1a0c', '#3e2711', '#543617', '#6b461d', '#835825', '#9b6b2e', '#b27f3a')),
    'slate': dict(hide=hexes('#1c2024', '#262b30', '#31373d', '#3d444b', '#4a525a', '#58616a', '#67707a', '#777f89'),
                  belly=hexes('#6f747a', '#80858b', '#92979c', '#a3a8ad', '#b4b9bd', '#c5c9cc'),
                  stripe=hexes('#0b0d0f', '#111316', '#171a1d', '#1e2125'),
                  quill=hexes('#1c2a3a', '#25384e', '#2f4863', '#3a5a78', '#476d8e', '#5682a3', '#6898b6')),
    'olive': dict(hide=hexes('#252718', '#323520', '#404329', '#4f5233', '#5f623d', '#707348', '#828453', '#93955f'),
                  belly=hexes('#827f5c', '#94906d', '#a5a17e', '#b6b290', '#c6c2a1', '#d5d1b2'),
                  stripe=hexes('#101109', '#17180e', '#1e2013', '#262818'),
                  quill=hexes('#3a2a0c', '#544010', '#6e5615', '#886d1b', '#a18523', '#b99d2d', '#ceb43a')),
    'rust': dict(hide=hexes('#2c160e', '#3d2015', '#4f2b1c', '#623724', '#76442d', '#8a5236', '#9f6141', '#b4714c'),
                 belly=hexes('#987660', '#a88772', '#b79984', '#c6aa96', '#d4bba8', '#e1cbb9'),
                 stripe=hexes('#140906', '#1d0e09', '#26130d', '#2f1811'),
                 quill=hexes('#141414', '#202020', '#2d2d2b', '#3b3a37', '#4a4944', '#5b5952', '#6d6a61')),
}
PALETTES['slate'] = lift(PALETTES['slate'], 1.08, 3)
TOOTH_RAMP = hexes('#8a7f66', '#ab9f83', '#c9bf9f', '#e0d8bc', '#efe9d5', '#faf7ec')
CLAW_RAMP = hexes('#100e0b', '#1d1914', '#2b251e', '#3b3329', '#4e4436', '#655845')
MOUTH_RAMP = hexes('#3a1414', '#55201f', '#71302c', '#8c423b', '#a5574c', '#bb6e5e')

# Dark bands over the back as pixel runs (see handmade_tyrannosaurus.stripe_runs): (z where a band crosses the
# spine, its width in texels there, how far down the side it reaches in units). Saddles over the back, rings down
# the tail.
STRIPES = [(2.4, 2, 1.3), (0.9, 2, 1.8), (-0.6, 2, 1.9), (-2.1, 2, 1.6), (-3.8, 2, 1.3), (-5.6, 2, 1.1),
           (-7.5, 2, 1.0), (-9.4, 1, 0.8), (-11.2, 1, 0.6)]
RUNS = [stripe_runs(57 + k, w, 60) for k, (_, w, _) in enumerate(STRIPES)]
EYE = (5.3, 10.17)       # (z, y): the eye, where the socket and the stripe through it are painted


def under_y(z):
    return np.interp(z, *UNDER)


def countershade(tx):
    """0 on the underside to 1 along the spine; up the neck, 0 down the throat's front to 1 down its back."""
    p = tx.points
    y, z = p[..., 1], p[..., 2]
    top, bottom = np.interp(z, *BACK), under_y(z)
    if tx.box.tag.get('part') == 'thigh':              # the thigh hangs below the belly line: shade it as flank
        bottom = bottom - 1.6
    f = np.clip((y - bottom) / np.maximum(top - bottom, 0.3), 0, 1)
    if tx.box.tag.get('part') == 'neck':
        front = np.interp(y, [5.9, 6.9, 7.6, 8.4, 9.1, 10.0], [3.7, 4.35, 4.76, 5.1, 5.15, 5.0])
        f = np.clip((front - z) / 1.45, 0, 1) ** 0.8
    return f


def stripes(tx):
    """The dark bands of STRIPES on a body face, across the back on top and down the sides in their runs."""
    p, n, box = tx.points, tx.normal, tx.box
    band = np.zeros(p.shape[:2], bool)
    axis = box.axes[:, 1] if box.tag.get('part') == 'tail' else box.axes[:, 2]
    if abs(n @ axis) > 0.7 or n[1] < -0.5:
        return band
    length = box.size[1] if box.tag.get('part') == 'tail' else box.size[2]
    count = max(1, int(round(length / tx.texel)))
    local = (p - box.center) @ axis
    iz = np.clip(np.floor((local / length + 0.5) * count).astype(int), 0, count - 1)
    rows = np.floor((np.interp(p[..., 2], *BACK) - p[..., 1] + np.abs(p[..., 0]) * 0.3) / tx.texel).astype(int)
    for (z0, width, reach), runs in zip(STRIPES, RUNS):
        along = (np.array([0.0, back_y(z0), z0]) - box.center) @ axis
        col = int(np.floor((along / length + 0.5) * count))
        if col < -12 or col > count + 12:
            continue
        limit = int(reach / tx.texel)
        for first, last, shift, w in runs:
            if first > limit:
                break
            w = max(1, min(w, int(round(w * (1.2 - first / max(limit, 1))))))
            c0 = col + shift // 2 - w // 2
            band |= (rows >= first) & (rows <= min(last, limit)) & (iz >= c0) & (iz < c0 + w)
    return band


def hide(tx, pal):
    """Trunk, neck and tail: fine scales over a dark back lightening down the flanks, the bands across the back,
    the pale underside up the throat and under the tail."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    f = countershade(tx)
    mottle = fbm(p, 1.6, seed=3, octaves=2) - 0.5
    v = 0.84 - 0.4 * f ** 1.1 + 0.12 * mottle + scales(tx, 2, seed=5, seam=0.04) + 0.04 * max(n[1], 0)
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    if part != 'neck':
        band = stripes(tx)
        if np.any(band):
            dark = shade(pal['stripe'], 0.5 + 0.6 * mottle, tx.x, tx.y, dither=0.0)
            colour = np.where(band[..., None], dark, colour)
    pebble = scales(tx, 2, seed=7)
    edge = 0.3 + 0.08 * (noise(p, 0.5, seed=8) - 0.5) + 0.8 * pebble
    under = (f < edge) | (n[1] < -0.5)
    if part == 'neck':
        under = f < 0.36 + 0.8 * pebble
    if part == 'thigh':
        under = (n[1] < -0.5) | ((n[0] * np.sign(tx.box.center[0]) < -0.5) & (f < 0.45 + 0.8 * pebble))
    if np.any(under):
        belly = 0.5 + 0.1 * mottle + 0.6 * scales(tx, 2, seed=6, seam=0.06)
        if n[1] < -0.5 and part == 'trunk':
            iz, _ = grid(tx, 2)
            belly = 0.52 + 0.06 * mottle - 0.12 * (iz % 2 == 0)
        elif part == 'neck':                             # loose folds across the throat
            col, row = face_grid(tx)
            wave = np.floor(1.5 * np.sin(col * 0.5 + tx.face.index)).astype(int)
            belly = belly - 0.1 * ((row + wave) % 3 == 0)
        colour = np.where(under[..., None], shade(pal['belly'], belly, tx.x, tx.y, dither=0.0), colour)
    return colour


def limb(tx, pal):
    """Legs, arms and toes: small scales, darker outside, paler inside and below, broad scutes over the toes."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    mottle = fbm(p, 1.2, seed=13, octaves=2) - 0.5
    v = 0.5 + 0.12 * mottle + scales(tx, 2, seed=14, seam=0.06)
    if part == 'thigh':
        v = 0.44 + 0.12 * mottle + scales(tx, 2, seed=14) - 0.14 * np.clip((p[..., 1] - 5.6) / 2.0, 0, 1)
    if abs(n[0]) > 0.5 and n[0] * np.sign(tx.box.center[0]) < 0:
        v = v + 0.12                                     # the inside of the limb is paler
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    if part == 'thigh':
        band = stripes(tx)
        colour = np.where(band[..., None], shade(pal['stripe'], 0.5 + 0.5 * mottle, tx.x, tx.y, dither=0.0), colour)
    if part in ('toe', 'foot', 'meta', 'finger', 'hand') and n[1] > 0.3:
        k, _ = grid(tx, 2)
        colour = np.where((k % 2 == 0)[..., None], shade(pal['hide'], v - 0.12, tx.x, tx.y, dither=0.0), colour)
    if n[1] < -0.5 or (part in ('arm', 'forearm') and n[2] < -0.5):
        colour = shade(pal['belly'], 0.45 + 0.08 * mottle, tx.x, tx.y, dither=0.0)
    return colour


def head(tx, pal):
    """The head as pixel art: a dark crown and a darker stripe from the snout through the eye to the back of the
    skull, the eye's socket and lit rim, a pale lip line along both jaws and a pale throat, the nostrils."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    jaw = tx.box.bone == JAW
    mottle = fbm(p, 0.8, seed=23, octaves=2) - 0.5
    col, row = face_grid(tx)
    top = np.interp(z, [3.6, 4.8, 5.6, 6.3, 7.0], [10.66, 10.66, 10.54, 10.22, 9.92])
    f = np.clip((y - 9.0) / (top - 9.0), 0, 1)
    v = 0.74 - 0.38 * f ** 1.1 + 0.12 * mottle + scales(tx, 2, seed=24, seam=0.07) + 0.04 * max(n[1], 0)
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    if abs(n[0]) > 0.5:
        dz, dy = (z - EYE[0]) / tx.texel, (y - EYE[1]) / tx.texel
        # the dark stripe: from the snout's side through the eye and on back to the nape
        line = np.where(dz > 0, -dz * 0.18, -dz * 0.12)
        stripe = (dz > -16) & (dz < 10) & (np.abs(dy - line) < 1.6 + 0.05 * np.clip(-dz, 0, 12)) & ~jaw
        colour = np.where(stripe[..., None], shade(pal['stripe'], 0.65 + 0.6 * mottle, tx.x, tx.y, dither=0.0),
                          colour)
        socket = (dz ** 2 / 6.5 + dy ** 2 / 4) < 1
        rim = ((dz ** 2 / 6.5 + dy ** 2 / 4) < 1.9) & ~socket & (dy > 0.5)
        colour = np.where(socket[..., None], shade(pal['stripe'], 0.2, tx.x, tx.y), colour)
        colour = np.where(rim[..., None], shade(pal['hide'], v + 0.2, tx.x, tx.y, dither=0.0), colour)
        # the lips: a pale row of scales along the edge of both jaws, the throat pale below
        if not jaw and part in ('snout', 'tip', 'temple', 'cranium'):
            lip = (row >= tx.face.rect[3] - 2) & (z > 4.9)
            k, _ = grid(tx, 2)
            colour = np.where((lip & (k % 2 == 0))[..., None], shade(pal['belly'], 0.62, tx.x, tx.y), colour)
            colour = np.where((lip & (k % 2 == 1))[..., None], shade(pal['belly'], 0.4, tx.x, tx.y), colour)
        if jaw:
            k, _ = grid(tx, 2)
            lip = row <= 0
            colour = np.where((lip & (k % 2 == 1))[..., None], shade(pal['belly'], 0.5, tx.x, tx.y), colour)
            pale = (row > 0) & (y < 9.3 + 0.12 * (noise(p, 0.4, seed=27) - 0.5))
            colour = np.where(pale[..., None], shade(pal['belly'], 0.58 + 0.08 * mottle + 0.5 * scales(tx, 2, seed=26),
                                                     tx.x, tx.y), colour)
    if part == 'bridge' and n[1] > 0.5:
        nostril = (np.abs(np.abs(x) - 0.2) < 0.07) & (np.abs(z - 6.22) < 0.07)
        colour = np.where(nostril[..., None], shade(pal['stripe'], 0.1, tx.x, tx.y), colour)
    if part == 'tip' and abs(n[0]) > 0.5:
        nostril = (np.abs(z - 6.82) < 0.07) & (np.abs(y - 9.75) < 0.06)
        colour = np.where(nostril[..., None], shade(pal['stripe'], 0.1, tx.x, tx.y), colour)
    if n[1] < -0.5:
        colour = shade(pal['belly'], 0.5 + 0.06 * mottle, tx.x, tx.y) if jaw or part == 'cranium' else \
            shade(MOUTH_RAMP, 0.35 + 0.1 * mottle, tx.x, tx.y)
    if jaw and n[1] > 0.5:
        i, ci = grid(tx, 0)
        colour = np.where(((i == 0) | (i == ci - 1))[..., None], shade(pal['hide'], 0.56, tx.x, tx.y),
                          shade(MOUTH_RAMP, 0.32 + 0.1 * mottle, tx.x, tx.y))
    return colour


def teeth(tx):
    """A row of small pointed teeth hanging from the lip, as pixel art, the rest clear."""
    box, p = tx.box, tx.points
    a = (p[..., 2] - (box.center[2] - box.size[2] / 2)) / tx.texel
    h = (box.center[1] + box.size[1] / 2 - p[..., 1]) / tx.texel          # down from the gum
    rows = box.size[1] / tx.texel
    k = np.floor(a / 2.0)
    f = a / 2.0 - k
    length = rows * (0.7 + 0.3 * cells(np.stack([k, np.zeros_like(k), np.full(k.shape, 3.0)], -1), 1.0, seed=5))
    alpha = (h <= length) & ((f < 0.5) | (h < 0.8))
    v = 0.85 - 0.4 * (h / np.maximum(length, 1)) ** 2
    return shade(TOOTH_RAMP, v, tx.x, tx.y, dither=0.0), alpha


def blades(tx):
    """Where a card's blades are, as pixel art: per texel whether it lies in a blade, how far out along the blade
    (0 at the root, 1 at the tip), across it (-1 to 1) and which blade."""
    box = tx.box
    local = (tx.points - box.center) @ box.axes                # the card as if it were not turned
    y = box.center[1] + local[..., 1]
    z = box.center[2] + local[..., 2]
    inside = np.zeros(y.shape, bool)
    along = np.zeros(y.shape)
    across = np.zeros(y.shape)
    which = np.full(y.shape, -1)
    for k, ((ry, rz), (ty, tz), width) in enumerate(box.tag['blades']):
        d = np.array([ty - ry, tz - rz])
        length = float(np.hypot(*d))
        d = d / length
        t = ((y - ry) * d[0] + (z - rz) * d[1]) / length
        s = (-(y - ry) * d[1] + (z - rz) * d[0])
        half = width / 2 * np.clip(1 - t ** 2, 0, 1) ** 0.7 + tx.texel * 0.35
        hit = (t >= -0.05) & (t <= 1.0) & (np.abs(s) <= half) & ~inside
        inside |= hit
        along = np.where(hit, t, along)
        across = np.where(hit, s / np.maximum(half, 1e-6), across)
        which = np.where(hit, k, which)
    return inside, along, across, which


def quill(tx, pal):
    """The quills and feathers: the body's dark colour at the root, the accent ramp out along the blade with a
    darker shaft down its middle and a lighter edge, a dark band near the tip of the crest's and the tail's."""
    inside, along, across, which = blades(tx)
    part = tx.box.tag.get('part')
    if part == 'spine':
        v = 0.25 + 0.5 * along + 0.1 * (cells(tx.points, tx.texel * 1.01, seed=61) - 0.5)
        return shade(pal['quill'], 0.6 * v, tx.x, tx.y, dither=0.0), inside
    tone = cells(np.stack([which.astype(float), np.zeros_like(along), np.zeros_like(along)], -1), 1.0, seed=62)
    v = 0.3 + 0.55 * along + 0.12 * (tone - 0.5) - 0.18 * (np.abs(across) < 0.3) + 0.08 * (np.abs(across) > 0.7)
    colour = shade(pal['quill'], v, tx.x, tx.y, dither=0.0)
    root = along < 0.18
    colour = np.where(root[..., None], shade(pal['hide'], 0.3 + 0.3 * along, tx.x, tx.y, dither=0.0), colour)
    if part == 'feather':
        tip = (along > 0.72) & (along < 0.84)
        colour = np.where(tip[..., None], shade(pal['stripe'], 0.4, tx.x, tx.y, dither=0.0), colour)
    return colour, inside


def keratin(tx, ramp):
    """Claws: dark at the base, lighter to the tip."""
    box, n = tx.box, tx.normal
    a, b = box.tag.get('along', (0.0, 1.0))
    s = ((tx.points - box.center) @ box.axes[:, 1]) / box.size[1] + 0.5
    t = a + (b - a) * np.clip(s, 0, 1)
    v = 0.25 + 0.6 * t + 0.1 * (cells(tx.points, tx.texel * 1.01, seed=41) - 0.5) + 0.06 * max(n[1], 0)
    return shade(ramp, v, tx.x, tx.y, dither=0.0)


def painter(tx, variant):
    pal = PALETTES[variant]
    mat = tx.box.mat
    part = tx.box.tag.get('part')
    if mat == 'quill':
        if abs(tx.normal @ tx.box.axes[:, 0]) > 0.5:
            return quill(tx, pal)
        return shade(pal['hide'], 0.3, tx.x, tx.y), None
    if mat == 'tooth':
        return teeth(tx)
    if mat == 'hide':
        if part in ('trunk', 'tail', 'neck', 'thigh'):
            return hide(tx, pal), None
        return limb(tx, pal), None
    if mat == 'head':
        return head(tx, pal), None
    if mat == 'claw':
        return keratin(tx, CLAW_RAMP), None
    return shade(MOUTH_RAMP, 0.6, tx.x, tx.y), None
