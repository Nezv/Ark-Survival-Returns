"""The Tyrannosaurus, designed box by box on the ARK rig (Rex); built by scripts/handmade_models.py.

A heavy theropod in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a deep trunk
that rounds over the back and keels under the belly, carried on drumstick thighs, long shins and three-toed feet;
a thick neck that climbs steeply to the massive head, and a tail that tapers from the hips to a point. The skull
is broad behind the eyes under horned brows and narrows to a deep snout; the lower jaw is designed shut as the
idle holds it, and the teeth are cut-out rows along both jaws, so they show against the lips and in a roar. The
tiny arms end in two clawed fingers. A ridge of small scutes runs from the crown over the neck and down the back.
"""
import math

import numpy as np

from handmade_models import cells, fbm, hexes, lift, noise, shade, unit

FOLDER, STEM, HEIGHT = 'Tyranosaur', 'rex_ravager', 13.5
DENSITY = 1.0            # texels per runtime unit, Minecraft's own density
ATLAS_WIDTH = 1024
PREVIEW_VARIANT = 'Natural'
ASSET = 'Rex'
PREVIEW_CLIPS = ('Rex-Idle', 'Rex-Move-Fwd', 'Rex-Roar', 'Rex-Torpid')
JAW_POSE = ('Rex-Idle', 0.0, ['Jaw', 'Tongue'])     # the frame the jaw is designed in (--outline compares in it)
HEAD, JAW = 'Cnt_Head_JNT_SKL', 'Cnt_Jaw_JNT_SKL'

# The trunk, segment by segment: (bone, rear z, front z, tiers from the belly up as (bottom, top, half width)),
# measured on the ARK mesh's cross-sections, the thighs and the dorsal ridge left out.
TRUNK = [
    ('Cnt_Spine_000_JNT_SKL', -10.6, -6.2, [(16.3, 17.55, 2.25), (17.5, 20.0, 4.4), (20.0, 28.0, 5.75),
                                            (28.0, 30.45, 4.6), (30.45, 31.7, 2.9)]),
    ('Cnt_Spine_000_JNT_SKL', -6.4, -1.8, [(15.7, 17.05, 2.2), (17.0, 19.8, 4.6), (19.8, 28.7, 6.2),
                                           (28.7, 31.4, 4.9), (31.4, 32.8, 2.95)]),
    ('Cnt_Spine_001_JNT_SKL', -1.9, 3.1, [(15.0, 17.1, 2.2), (17.1, 20.0, 4.6), (20.0, 29.2, 6.1),
                                          (29.2, 32.0, 4.72), (32.0, 33.4, 2.45)]),
    ('Cnt_Spine_002_JNT_SKL', 3.0, 8.0, [(17.3, 18.7, 2.47), (18.7, 21.4, 4.26), (21.4, 30.3, 6.2),
                                         (30.3, 33.0, 4.07), (33.0, 34.35, 2.08)]),
    ('Cnt_Spine_003_JNT_SKL', 7.9, 12.9, [(20.7, 21.8, 2.19), (21.8, 24.0, 3.65), (24.0, 31.1, 5.15),
                                          (31.1, 33.3, 3.69), (33.3, 34.4, 2.03)]),
]
# The tail, two segments per bone: (bone, rear z, front z, (keel, core, top) tiers).
TAIL_SEGMENTS = [
    ('Cnt_Tail_007_JNT_SKL', -61.6, -58.6, [(26.12, 26.3, 0.27), (26.25, 26.6, 0.37), (26.55, 26.72, 0.25)]),
    ('Cnt_Tail_007_JNT_SKL', -58.8, -55.4, [(25.75, 25.95, 0.5), (25.91, 26.41, 0.61), (26.38, 26.6, 0.51)]),
    ('Cnt_Tail_006_JNT_SKL', -55.6, -52.3, [(25.18, 25.5, 0.59), (25.47, 26.33, 0.83), (26.3, 26.62, 0.61)]),
    ('Cnt_Tail_006_JNT_SKL', -52.5, -49.0, [(24.65, 25.08, 0.69), (25.05, 26.24, 1.05), (26.21, 26.64, 0.71)]),
    ('Cnt_Tail_005_JNT_SKL', -49.2, -45.9, [(24.08, 24.62, 0.73), (24.59, 26.11, 1.26), (26.08, 26.62, 0.81)]),
    ('Cnt_Tail_005_JNT_SKL', -46.1, -42.6, [(23.51, 24.15, 0.87), (24.12, 25.95, 1.49), (25.92, 26.56, 0.91)]),
    ('Cnt_Tail_004_JNT_SKL', -42.8, -39.5, [(22.91, 23.65, 1.02), (23.62, 25.75, 1.72), (25.72, 26.46, 1.19)]),
    ('Cnt_Tail_004_JNT_SKL', -39.7, -36.1, [(22.25, 23.11, 1.13), (23.08, 25.57, 1.97), (25.54, 26.4, 1.21)]),
    ('Cnt_Tail_003_JNT_SKL', -36.3, -33.0, [(21.54, 22.54, 1.22), (22.51, 25.42, 2.21), (25.39, 26.39, 1.28)]),
    ('Cnt_Tail_003_JNT_SKL', -33.2, -29.7, [(20.82, 21.98, 1.33), (21.95, 25.33, 2.44), (25.3, 26.45, 1.39)]),
    ('Cnt_Tail_002_JNT_SKL', -29.9, -26.6, [(20.13, 21.44, 1.52), (21.41, 25.25, 2.89), (25.22, 26.54, 1.5)]),
    ('Cnt_Tail_002_JNT_SKL', -26.8, -23.2, [(19.41, 20.92, 1.65), (20.89, 25.3, 3.18), (25.27, 26.77, 1.68)]),
    ('Cnt_Tail_001_JNT_SKL', -23.4, -20.1, [(18.86, 20.54, 1.82), (20.51, 25.48, 3.65), (25.45, 27.14, 2.04)]),
    ('Cnt_Tail_001_JNT_SKL', -20.3, -16.7, [(18.35, 20.27, 2.11), (20.24, 25.91, 4.26), (25.88, 27.8, 2.45)]),
    ('Cnt_Tail_000_JNT_SKL', -16.9, -13.7, [(17.94, 20.12, 2.63), (20.09, 26.54, 4.93), (26.51, 28.7, 3.0)]),
    ('Cnt_Tail_000_JNT_SKL', -13.9, -10.3, [(17.5, 20.04, 3.03), (20.01, 27.53, 5.4), (27.5, 30.03, 3.6)]),
]
# The neck climbs from the shoulders to the head: per bone (rear z, front z, half width), laid along the top line
# of its back and the bottom line of its throat as measured on the ARK mesh's side view (NECK_TOP, NECK_THROAT).
NECK = [('Cnt_Spine_004_JNT_SKL', 10.6, 15.6, 4.15), ('Cnt_Neck_001_JNT_SKL', 15.3, 18.2, 3.6),
        ('Cnt_Neck_002_JNT_SKL', 17.9, 20.7, 3.9), ('Cnt_Neck_003_JNT_SKL', 20.4, 22.4, 4.05)]
NECK_TOP = ([9.0, 10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 19.0, 20.0, 21.0, 22.0, 23.0],
            [34.4, 34.4, 34.8, 35.5, 36.6, 37.8, 38.5, 39.3, 39.8, 40.1, 40.4, 40.6, 40.7, 40.6, 40.2])
NECK_THROAT = ([9.0, 10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 19.0, 20.0, 21.0, 22.0, 23.0],
               [19.0, 19.9, 20.6, 21.4, 22.0, 23.1, 23.6, 24.8, 26.7, 28.5, 29.0, 29.1, 28.4, 28.0, 27.6])


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
    a, b = z0 - 0.45, z1 + 0.45                      # the cores reach past the bends, so no wedge opens between them
    d.slab(bone, (top(z0), z0), (top(z1), z1), half * 0.58, 1.45, mat, part='neck', tier=2)
    d.slab(bone, (top(a) - 1.25, a), (top(b) - 1.25, b), half, core, mat, part='neck', tier=1)
    d.slab(bone, (low(a) + 1.2, a + 0.05), (low(b) + 1.2, b - 0.05), half * 0.94, core, mat, top=False,
           part='neck', tier=-1)
    d.slab(bone, (low(z0), z0 + 0.25), (low(z1), z1 - 0.25), half * 0.6, 1.4, mat, top=False, part='neck', tier=-2)


def tiers(d, bone, z0, z1, rows, mat='hide', part='trunk', inset=0.03):
    """Stacked boxes across a segment, each overlapping the next a little; the tiers off the core are inset a
    hair at both ends, so no two neighbours share an end plane."""
    core = max(range(len(rows)), key=lambda k: rows[k][2])
    for k, (y0, y1, half) in enumerate(rows):
        e = 0.0 if k == core else inset * (1 + abs(k - core))
        lo = y0 - (0.04 if k > core else 0.0)
        hi = y1 + (0.04 if k < core else 0.0)
        d.span(bone, (-half, lo, z0 + e), (half, hi, z1 - e), mat, part=part, tier=k - core, rows=len(rows))


def design(d):
    rig = d.rig
    P = rig.pivot
    # the jaw and the tongue are designed shut, as the idle holds them; the bind pose roars
    for bone in (JAW, 'Cnt_Tongue_000_JNT_SKL', 'Cnt_Tongue_001_JNT_SKL'):
        rig.design_as_in(bone, 'Rex-Idle', 0.0, HEAD)

    # ---------------------------------------------------------------- trunk and tail
    for bone, z0, z1, rows in TRUNK:
        tiers(d, bone, z0, z1, rows)
    for bone, z0, z1, rows in TAIL_SEGMENTS:
        tiers(d, bone, z0, z1, rows, part='tail', inset=0.08)

    # ---------------------------------------------------------------- neck
    for bone, z0, z1, half in NECK:
        neck_section(d, bone, z0, z1, half)

    # ---------------------------------------------------------------- head (the skull) and the shut jaw
    # the skull: broad over the jaw muscles behind the eyes, its roof rising to a crest
    d.span(HEAD, (-4.35, 31.8, 21.3), (4.35, 37.6, 25.6), 'head', part='cranium')
    d.span(HEAD, (-3.5, 37.5, 21.5), (3.5, 38.6, 25.56), 'head', part='roof')
    d.span(HEAD, (-2.4, 38.5, 21.75), (2.4, 39.75, 25.3), 'head', part='crown')
    d.span(HEAD, (-0.75, 39.6, 22.1), (0.75, 40.45, 24.8), 'head', part='crest')
    # the face: the eye sits between the brow ledge and the cheek, clear of both (build_creature_eyes footprint)
    d.span(HEAD, (-3.6, 31.5, 25.5), (3.6, 38.05, 28.7), 'head', part='temple')
    d.span(HEAD, (-3.88, 31.55, 25.4), (3.88, 35.45, 28.45), 'head', part='cheek')
    d.span(HEAD, (-3.82, 37.35, 25.45), (3.82, 38.3, 28.25), 'head', part='brow')
    d.span(HEAD, (3.12, 38.2, 25.6), (4.08, 39.1, 27.7), 'head', part='horn', mirror=True)
    # the snout narrows toward the nasal ridge on top
    d.span(HEAD, (-2.88, 30.0, 28.6), (2.88, 34.6, 32.15), 'head', part='snout')
    d.span(HEAD, (-2.25, 34.5, 28.65), (2.25, 36.6, 32.05), 'head', part='snout_top')
    d.span(HEAD, (-1.3, 36.5, 28.4), (1.3, 37.3, 30.9), 'head', part='bridge')
    d.span(HEAD, (-2.48, 29.6, 32.05), (2.48, 33.6, 34.55), 'head', part='snout')
    d.span(HEAD, (-1.95, 33.5, 32.1), (1.95, 35.0, 34.45), 'head', part='snout_top')
    d.span(HEAD, (-1.55, 29.85, 34.45), (1.55, 33.6, 35.45), 'head', part='tip')
    # the upper teeth: cut-out rows hanging from the lip, outside the narrower lower jaw, and across the front
    teeth = dict(open=('up', 'down', 'north', 'south'), cutout=True, keep=True, mirror=True)
    d.span(HEAD, (2.62, 29.95, 26.9), (2.76, 31.5, 28.6), 'tooth', part='teeth', row='upper', **teeth)
    d.span(HEAD, (2.6, 28.8, 28.6), (2.74, 30.0, 32.1), 'tooth', part='teeth', row='upper', **teeth)
    d.span(HEAD, (2.2, 28.45, 32.05), (2.34, 29.6, 34.4), 'tooth', part='teeth', row='upper', **teeth)
    d.span(HEAD, (-1.45, 28.6, 34.28), (1.45, 29.6, 34.42), 'tooth', part='teeth', row='front',
           open=('up', 'down', 'east', 'west'), cutout=True, keep=True)

    d.span(JAW, (-4.0, 27.2, 21.5), (4.0, 31.9, 26.6), 'head', part='jaw')
    d.span(JAW, (-2.55, 26.9, 26.4), (2.55, 31.4, 28.3), 'head', part='jaw')
    d.span(JAW, (-2.5, 26.7, 28.1), (2.5, 29.9, 29.8), 'head', part='jaw')
    d.span(JAW, (-1.95, 25.9, 29.6), (1.95, 29.0, 32.7), 'head', part='jaw')
    d.span(JAW, (-1.3, 26.05, 32.5), (1.3, 28.0, 33.85), 'head', part='chin')
    d.span(JAW, (1.6, 28.5, 29.8), (1.74, 29.75, 32.6), 'tooth', part='teeth', row='lower', **teeth)
    d.span('Cnt_Tongue_001_JNT_SKL', (-1.05, 28.95, 28.4), (1.05, 29.25, 32.2), 'mouth', part='tongue')

    # ---------------------------------------------------------------- dorsal ridge: small scutes from crown to hips
    body = list(d.boxes)
    for bone, z, h in RIDGE:
        y = midline_top(body, z)
        d.span(bone, (-0.55, y - 0.3, z - 0.42), (0.55, y + h, z + 0.42), 'scute', part='ridge')
    # the small spikes along the end of the tail, leaning back (the ARK mesh's, measured)
    for z in TAIL_SPIKES:
        bone = 'Cnt_Tail_007_JNT_SKL' if z < -55.5 else 'Cnt_Tail_006_JNT_SKL' if z < -49.1 else 'Cnt_Tail_005_JNT_SKL'
        y = midline_top(body, z)
        d.seg(bone, (0.0, y - 0.25, z + 0.2), (0.0, y + 0.85, z - 0.25), 0.42, 0.62, 'scute', side=(1, 0, 0),
              part='spike', along=(0.0, 1.0))

    # ---------------------------------------------------------------- arms
    d.span('Lft_Arm_Clav000_JNT_SKL', (2.6, 25.9, 12.6), (4.45, 28.7, 15.0), 'hide', mirror=True, part='shoulder')
    elbow, wrist = P('Lft_Arm_002Tear000_JNT_SKL'), P('Lft_Arm_002Tear006_JNT_SKL')
    d.seg('Lft_Arm_001Tear000_JNT_SKL', (3.5, 27.0, 13.6), elbow, 1.85, 2.0, 'hide', ext=(0.2, 0.45), mirror=True,
          part='arm')
    d.seg('Lft_Arm_002Tear000_JNT_SKL', elbow, wrist, 1.45, 1.4, 'hide', ext=(0.3, 0.3), mirror=True, part='forearm')
    for finger in ('Index', 'Middle'):
        a, b, c = (P(f'Lft_{finger}Finger_00{k}_CTL_JNT_SKL') for k in range(3))
        tip = c + (c - b) * 1.6 + np.array([0.0, -0.25, 0.1])
        d.seg(f'Lft_{finger}Finger_000_CTL_JNT_SKL', a, b, 0.72, 0.8, 'hide', ext=(0.35, 0.1), mirror=True,
              part='finger')
        d.seg(f'Lft_{finger}Finger_001_CTL_JNT_SKL', b, c, 0.62, 0.7, 'hide', ext=(0.1, 0.1), mirror=True,
              part='finger')
        d.seg(f'Lft_{finger}Finger_002_CTL_JNT_SKL', c, tip, 0.42, 0.5, 'claw', ext=(0.05, 0.0), mirror=True,
              part='claw', along=(0.0, 1.0))

    # ---------------------------------------------------------------- legs
    thigh = 'Lft_Leg_001Linear000_JNT_SKL'
    d.span(thigh, (4.66, 18.0, -11.6), (8.38, 26.9, 2.25), 'hide', mirror=True, part='thigh')
    d.span(thigh, (4.97, 26.7, -10.3), (7.55, 29.5, -1.6), 'hide', mirror=True, part='thigh')
    d.span(thigh, (3.0, 14.85, -5.5), (8.15, 18.2, 3.05), 'hide', mirror=True, part='thigh')
    d.span(thigh, (4.85, 16.6, -9.6), (7.9, 18.1, -5.4), 'hide', mirror=True, part='thigh')
    shin, meta, foot = 'Lft_Leg_002Linear000_JNT_SKL', 'Lft_Leg_003Linear000_JNT_SKL', 'Lft_Leg_AIk004_JNT_SKL'
    d.seg(shin, (5.1, 15.6, -0.45), (5.05, 10.6, -2.75), 5.2, 5.4, 'hide', ext=(0.0, 0.3), mirror=True, part='shin')
    d.seg(shin, (5.05, 11.0, -2.55), (5.05, 6.9, -4.45), 3.7, 3.9, 'hide', ext=(0.0, 0.4), mirror=True, part='shin')
    d.seg(meta, (5.0, 7.3, -4.55), (5.0, 1.9, -3.3), 3.75, 3.85, 'hide', ext=(0.0, 0.0), mirror=True, part='meta')
    d.span(foot, (2.75, 0.0, -5.2), (7.7, 2.35, -0.55), 'hide', mirror=True, part='foot')
    for toe, tip, w, h in (('Big', (1.55, 0.45, 2.75), 1.45, 1.55), ('Middle', (5.2, 0.45, 4.15), 1.8, 1.8),
                           ('Little', (8.55, 0.45, 2.2), 1.45, 1.5)):
        a, b = P(f'Lft_{toe}_Toe_000_JNT_SKL'), P(f'Lft_{toe}_Toe_001_JNT_SKL')
        tip = np.array(tip)
        d.seg(f'Lft_{toe}_Toe_000_JNT_SKL', a, b, w, h, 'hide', ext=(0.3, 0.15), side=(1, 0, 0), mirror=True,
              part='toe')
        knuckle = b + (tip - b) * 0.55
        d.seg(f'Lft_{toe}_Toe_001_JNT_SKL', b, knuckle, w * 0.88, h * 0.85, 'hide', ext=(0.1, 0.0), side=(1, 0, 0),
              mirror=True, part='toe')
        d.seg(f'Lft_{toe}_Toe_001_JNT_SKL', knuckle, tip, w * 0.55, h * 0.6, 'claw', ext=(0.05, 0.0), side=(1, 0, 0),
              mirror=True, part='claw', along=(0.0, 1.0))


# The dorsal ridge: (bone, z, height) from the hips to the crown; its scutes grow toward the neck.
RIDGE = [(b, z, h) for b, z, h in (
    ('Cnt_Tail_000_JNT_SKL', -12.55, 0.35), ('Cnt_Spine_000_JNT_SKL', -11.15, 0.4), ('Cnt_Spine_000_JNT_SKL', -9.75, 0.42),
    ('Cnt_Spine_000_JNT_SKL', -8.35, 0.45), ('Cnt_Spine_000_JNT_SKL', -6.95, 0.45), ('Cnt_Spine_000_JNT_SKL', -5.55, 0.48),
    ('Cnt_Spine_000_JNT_SKL', -4.15, 0.5), ('Cnt_Spine_000_JNT_SKL', -2.75, 0.5), ('Cnt_Spine_001_JNT_SKL', -1.35, 0.52),
    ('Cnt_Spine_001_JNT_SKL', 0.05, 0.55), ('Cnt_Spine_001_JNT_SKL', 1.45, 0.55), ('Cnt_Spine_001_JNT_SKL', 2.85, 0.58),
    ('Cnt_Spine_002_JNT_SKL', 4.25, 0.6), ('Cnt_Spine_002_JNT_SKL', 5.65, 0.62), ('Cnt_Spine_002_JNT_SKL', 7.05, 0.62),
    ('Cnt_Spine_003_JNT_SKL', 8.45, 0.62), ('Cnt_Spine_003_JNT_SKL', 9.85, 0.62), ('Cnt_Spine_003_JNT_SKL', 11.25, 0.66),
    ('Cnt_Spine_004_JNT_SKL', 12.5, 0.72), ('Cnt_Spine_004_JNT_SKL', 13.7, 0.8), ('Cnt_Spine_004_JNT_SKL', 14.9, 0.85),
    ('Cnt_Neck_001_JNT_SKL', 16.1, 0.9), ('Cnt_Neck_001_JNT_SKL', 17.2, 0.9), ('Cnt_Neck_002_JNT_SKL', 18.3, 0.85),
    ('Cnt_Neck_002_JNT_SKL', 19.4, 0.8), ('Cnt_Neck_003_JNT_SKL', 20.5, 0.75))]


TAIL_SPIKES = [-45.8, -47.1, -48.1, -49.2, -50.05, -51.0, -53.85, -54.95, -55.75, -56.55, -57.2, -57.85]


def midline_top(boxes, z):
    """The highest point of the boxes along the midline at z: where the ridge's scutes sit."""
    top = -np.inf
    for box in boxes:
        # the vertical line x = 0, z = z in the box's own frame: p(t) = (0, t, z)
        o = box.axes.T @ (np.array([0.0, 0.0, z]) - box.center)
        dvec = box.axes.T @ np.array([0.0, 1.0, 0.0])
        lo, hi = -np.inf, np.inf
        inside = True
        for k in range(3):
            half = box.size[k] / 2
            if abs(dvec[k]) < 1e-9:
                inside &= abs(o[k]) <= half
                continue
            t0, t1 = sorted(((-half - o[k]) / dvec[k], (half - o[k]) / dvec[k]))
            lo, hi = max(lo, t0), min(hi, t1)
        if inside and lo <= hi:
            top = max(top, hi)
    return float(top)


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(hide=hexes('#2a2b1b', '#3a3a24', '#4c4a2d', '#5f5b37', '#736d41', '#88804c', '#9d9358', '#b2a666'),
                    belly=hexes('#7c6f52', '#8f8262', '#a29473', '#b3a684', '#c2b694', '#cfc4a5'),
                    stripe=hexes('#141510', '#1d1e15', '#26271b', '#30301f'),
                    scute=hexes('#1b1c14', '#26271b', '#333424', '#42422e', '#53523a'),
                    mouth=hexes('#3a1414', '#55201f', '#71302c', '#8c423b', '#a5574c', '#bb6e5e')),
    'Ivory': dict(hide=hexes('#5f5a4f', '#736d61', '#878174', '#9b9587', '#aea99b', '#c1bcae', '#d2cec1', '#e2ded3'),
                  belly=hexes('#bdb7a6', '#cbc6b6', '#d8d4c6', '#e4e1d5', '#eeece3', '#f7f5ef'),
                  stripe=hexes('#3f3a32', '#4d473e', '#5b554a', '#696357'),
                  scute=hexes('#3b3730', '#4b463e', '#5c564d', '#6d675d', '#7f796e'),
                  mouth=hexes('#4a2522', '#663531', '#824742', '#9d5b54', '#b57168', '#c8877d')),
    'Darken': dict(hide=hexes('#0f0f0e', '#161614', '#1e1d1b', '#262522', '#2f2e2a', '#393733', '#43413c', '#4e4b45'),
                   belly=hexes('#3a3833', '#45433d', '#504e47', '#5b5851', '#67635b', '#736f66'),
                   stripe=hexes('#060606', '#0a0a09', '#0f0e0d', '#141311'),
                   scute=hexes('#090908', '#0f0f0e', '#161614', '#1e1d1b', '#272623'),
                   mouth=hexes('#2e1212', '#451b1a', '#5c2724', '#743530', '#8a463f', '#9e584f')),
    'Emerald': dict(hide=hexes('#0d180e', '#132114', '#192b1b', '#203622', '#28422a', '#304f33', '#3a5d3d', '#456c48'),
                    belly=hexes('#4b5b42', '#5a6b4f', '#6a7b5e', '#7b8c6e', '#8c9c7f', '#9dac90'),
                    stripe=hexes('#050a05', '#081008', '#0c160c', '#101c10'),
                    scute=hexes('#070d07', '#0c140c', '#111c12', '#172518', '#1e2f1f'),
                    mouth=hexes('#341414', '#4d1f1d', '#672d29', '#803d36', '#995045', '#ad6556')),
    'Midnight': dict(hide=hexes('#0e131b', '#141b26', '#1b2431', '#222e3d', '#2a384a', '#334358', '#3d4f67', '#485c77'),
                     belly=hexes('#4b5566', '#5a6476', '#6a7486', '#7b8597', '#8c96a7', '#9ea7b6'),
                     stripe=hexes('#05070b', '#080b11', '#0c1017', '#10151e'),
                     scute=hexes('#070a0e', '#0c1016', '#11161e', '#171e28', '#1e2733'),
                     mouth=hexes('#331518', '#4a2024', '#632d31', '#7c3c40', '#954e50', '#a96363')),
    'Burgundy': dict(hide=hexes('#1e0a0a', '#2b0f0f', '#391515', '#481b1a', '#572220', '#682a27', '#7a332f', '#8d3d38'),
                     belly=hexes('#6a4b44', '#7c5c54', '#8e6d64', '#a07f75', '#b19187', '#c1a399'),
                     stripe=hexes('#0f0404', '#170606', '#1f0909', '#280c0b'),
                     scute=hexes('#130606', '#1c0909', '#260d0d', '#311212', '#3d1817'),
                     mouth=hexes('#3e1312', '#591d1b', '#742a26', '#8f3a33', '#a84d43', '#bd6355')),
}
PALETTES['Darken'] = lift(PALETTES['Darken'], 1.25, 6)
PALETTES['Emerald'] = lift(PALETTES['Emerald'], 1.08, 3)
PALETTES['Midnight'] = lift(PALETTES['Midnight'], 1.08, 3)
PALETTES['Burgundy'] = lift(PALETTES['Burgundy'], 1.08, 3)
TOOTH_RAMP = hexes('#8a7f66', '#ab9f83', '#c9bf9f', '#e0d8bc', '#efe9d5', '#faf7ec')
CLAW_RAMP = hexes('#100e0b', '#1d1914', '#2b251e', '#3b3329', '#4e4436', '#655845')
TONGUE_RAMP = hexes('#5e2526', '#7a3333', '#954442', '#ad5853', '#c26e66')

# The body's outline along its length (z), from the tail tip to the head: the top of the back and the underside.
BACK = ([-62, -50, -40, -30, -24, -20, -16, -12, -8, -4, 0, 4, 8, 10, 12, 14, 16, 18, 20, 22],
        [26.7, 26.9, 26.7, 26.6, 27.0, 27.6, 28.5, 30.2, 31.8, 32.7, 33.2, 34.2, 34.6, 34.4, 35.5, 37.8, 39.3, 40.1,
         40.6, 40.6])
UNDER = ([-62, -56, -50, -44, -40, -36, -30, -24, -18, -12, -8, -4, 0, 4, 8, 10, 12, 14, 16, 18, 20, 22],
         [26.5, 25.3, 24.3, 23.3, 22.5, 21.7, 20.3, 19.05, 18.1, 17.4, 16.0, 15.2, 14.9, 15.9, 18.2, 19.9, 21.4, 23.1,
          24.8, 28.5, 29.1, 28.0])
# The dark bands over the back, as pixel runs (see stripe_runs): (z where a band crosses the spine, its width in
# texels there, how far down the side it reaches in units). Wide saddles over the back, rings round the tail.
STRIPES = [(19.4, 5, 3.2), (16.9, 6, 4.4), (13.8, 7, 6.0), (10.6, 8, 7.4), (7.3, 8, 8.6), (4.0, 9, 9.0),
           (0.6, 9, 8.8), (-2.9, 8, 8.2), (-6.4, 8, 7.4), (-9.9, 7, 6.6), (-13.3, 7, 5.8), (-16.7, 6, 5.2),
           (-20.1, 6, 4.8), (-23.6, 6, 4.5), (-27.1, 5, 4.2), (-30.6, 5, 4.0), (-34.0, 5, 3.6), (-37.4, 5, 3.2),
           (-40.8, 4, 2.8), (-44.1, 4, 2.6), (-47.4, 4, 2.3), (-50.6, 4, 2.0), (-53.8, 3, 1.8), (-57.0, 3, 1.4),
           (-60.0, 3, 1.0)]


def back_y(z):
    return np.interp(z, *BACK)


def under_y(z):
    return np.interp(z, *UNDER)


def stripe_runs(seed, width, rows):
    """A band as a pixel artist draws it: runs of 3-5 rows, each a texel or two behind the one above it (the bands
    sweep back down the flank), its width shrinking by a texel at a time over the lower half and ending in a point;
    some bands fork near their end. Returns (first row, last row, shift, width) in texels."""
    rng = np.random.default_rng(seed)
    runs, row, shift, w = [], 0, 0, width
    while row < rows and w > 0:
        n = int(rng.choice([3, 4, 4, 5]))
        runs.append((row, min(row + n, rows) - 1, shift, w))
        row += n
        shift -= int(rng.choice([1, 1, 1, 2]))
        if row > rows * 0.4:
            w -= int(rng.choice([1, 1, 2]))
    return runs


RUNS = [stripe_runs(31 + k, w, 200) for k, (_, w, _) in enumerate(STRIPES)]


def face_grid(tx):
    """Per texel: its column and row inside its own face's rectangle (rows run down a side face)."""
    u0, v0, _, _ = tx.face.rect
    return tx.x - u0, tx.y - v0


def grid(tx, axis):
    """Per texel: its index along a world axis across the box (0 at the low end), and the count."""
    box = tx.box
    lo, hi = box.center[axis] - box.size[axis] / 2, box.center[axis] + box.size[axis] / 2
    count = max(1, int(round((hi - lo) / tx.texel)))
    index = np.floor((tx.points[..., axis] - lo) / (hi - lo) * count).astype(int)
    return np.clip(index, 0, count - 1), count


def scales(tx, size=3, seed=0, seam=0.1):
    """Pebbled scales as pixel art on the face's own texel grid: every texel belongs to the nearest of a jittered
    lattice of scale centres `size` texels apart; each scale has its own tone, a lit texel or two toward its top
    and a dark seam where it meets its neighbours. Returns a value offset per texel."""
    col, row = face_grid(tx)
    gi, gj = col // size, row // size
    best = np.full(col.shape, np.inf)
    second = np.full(col.shape, np.inf)
    owner_i, owner_j = np.zeros_like(gi), np.zeros_like(gj)
    salt = float(tx.face.index % 997)
    for di in (-1, 0, 1):
        for dj in (-1, 0, 1):
            ci, cj = gi + di, gj + dj
            key = np.stack([ci.astype(float), cj.astype(float), np.full(ci.shape, salt)], -1)
            sx = ci * size + cells(key, 1.0, seed=seed) * (size - 1)
            sy = cj * size + cells(key, 1.0, seed=seed + 1) * (size - 1)
            dist = (col - sx) ** 2 + (row - sy) ** 2 * 1.3
            closer = dist < best
            second = np.where(closer, best, np.minimum(second, dist))
            best = np.where(closer, dist, best)
            owner_i, owner_j = np.where(closer, ci, owner_i), np.where(closer, cj, owner_j)
    tone = cells(np.stack([owner_i.astype(float), owner_j.astype(float), np.full(col.shape, salt)], -1), 1.0,
                 seed=seed + 2)
    v = 0.09 * (tone - 0.5)
    v = v - seam * (np.sqrt(second) - np.sqrt(best) < 0.9)
    lit = cells(np.stack([owner_i.astype(float), owner_j.astype(float), np.full(col.shape, salt + 1)], -1), 1.0,
                seed=seed + 3)
    v = v + 0.045 * ((best < 0.6) & (lit > 0.45))
    return v


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
    """The body's skin: scales over a dark back that lightens down the flanks, the bands, the pale underside with
    its transverse belly scales."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part', 'trunk')
    f = countershade(tx)
    mottle = fbm(p, 5.0, seed=3, octaves=2) - 0.5
    v = 0.8 - 0.46 * f ** 1.15 + 0.14 * mottle + scales(tx, 3, seed=5)
    if n[1] > 0.5 and part in ('trunk', 'neck', 'tail'):
        # two rows of broad osteoderms either side of the spine
        near = np.abs(p[..., 0]) < 2.1
        v = np.where(near, 0.8 - 0.46 * f ** 1.15 + 0.12 * mottle + scales(tx, 5, seed=9, seam=0.16) + 0.06, v)
    v += 0.04 * max(n[1], 0)
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    band = stripes(tx)
    if np.any(band):
        dark = shade(pal['stripe'], 0.45 + 0.6 * mottle + 0.6 * scales(tx, 3, seed=5), tx.x, tx.y, dither=0.0)
        colour = np.where(band[..., None], dark, colour)
    # the pale underside: from a ragged line low on the flank, under the belly, throat and tail; the line runs along
    # whole scales, so pale and dark pebbles interlock along it
    pebble = scales(tx, 3, seed=7)
    edge = 0.28 + 0.06 * (noise(p, 1.2, seed=8) - 0.5) + (0.05 if part == 'neck' else 0.0) + 0.9 * pebble
    under = (f < edge) | ((n[1] < -0.5) & (part != 'thigh'))     # the thigh's underside shows as it swings
    if np.any(under):
        belly = 0.55 + 0.1 * mottle + 0.8 * scales(tx, 4, seed=6, seam=0.08)
        if n[1] < -0.5:                                # transverse belly scutes across the underside
            iz, _ = grid(tx, 2)
            belly = 0.6 + 0.06 * mottle - 0.16 * (iz % 4 == 0) + 0.04 * (iz % 4 == 1)
        elif part == 'neck' or (abs(n[2]) > 0.7 and p[..., 2].mean() > 8):
            # loose folds across the throat and the chest
            col, row = face_grid(tx)
            wave = np.floor(2.2 * np.sin(col * 0.21 + tx.face.index))
            belly = belly - 0.14 * ((row + wave.astype(int)) % 5 == 0)
        pale = shade(pal['belly'], belly, tx.x, tx.y, dither=0.0)
        colour = np.where(under[..., None], pale, colour)
    return colour


def limb(tx, pal):
    """Legs, arms and toes: rings of scales round the limb, darker outside, paler inside and below."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    mottle = fbm(p, 4.0, seed=13, octaves=2) - 0.5
    col, row = face_grid(tx)
    ring = (row // 2) % 2
    v = 0.5 + 0.12 * mottle + scales(tx, 2 if part in ('toe', 'finger') else 3, seed=14) - 0.03 * ring
    if part == 'thigh':
        v = 0.42 + 0.12 * mottle + scales(tx, 3, seed=14) - 0.12 * np.clip((p[..., 1] - 18) / 10, 0, 1)
    if abs(n[0]) > 0.5 and n[0] * np.sign(tx.box.center[0]) < 0:
        v += 0.1                                       # the inside of the leg is paler
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    if part == 'thigh':
        band = stripes(tx)
        if np.any(band):
            colour = np.where(band[..., None], shade(pal['stripe'], 0.5 + 0.5 * mottle, tx.x, tx.y, dither=0.0),
                              colour)
    if part in ('toe', 'foot', 'meta', 'finger') and n[1] > 0.5:
        # broad scutes across the top of the toes and the foot
        k, _ = grid(tx, 2)
        colour = np.where((k % 3 == 0)[..., None], shade(pal['hide'], v - 0.14, tx.x, tx.y, dither=0.0), colour)
    if n[1] < -0.5:
        colour = shade(pal['belly'], 0.4 + 0.08 * mottle, tx.x, tx.y, dither=0.0)
    return colour


# The face as pixel art: (z, y) points in source units that are snapped to each face's texels.
EYE = (27.05, 36.45)


def head(tx, pal):
    """The head: the hide's scales, a dark crown and a darker band back from the eye, a pale jaw and throat, the
    row of lip scales along both jaws, rugged knobs on the nasal ridge and the horns, the nostrils."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    mottle = fbm(p, 3.0, seed=23, octaves=2) - 0.5
    col, row = face_grid(tx)
    top = np.interp(z, [21, 25, 28, 31, 34, 35.5], [40.4, 39.6, 38.3, 36.8, 35.0, 33.5])
    bottom = np.interp(z, [21, 25, 29, 32, 34], [27.3, 27.2, 26.6, 25.9, 26.2])
    f = np.clip((y - bottom) / (top - bottom), 0, 1)
    v = 0.78 - 0.42 * f ** 1.1 + 0.12 * mottle + scales(tx, 3, seed=24) + 0.04 * max(n[1], 0)
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    side = abs(n[0]) > 0.5
    jaw = tx.box.bone == JAW
    if side:
        # a darker band from the eye back along the temple, and the socket round the eye
        dz, dy = (z - EYE[0]) / tx.texel, (y - EYE[1]) / tx.texel
        back = np.clip(-dz, 0, 30)
        band = (dz < 3) & (dz > -30) & (np.abs(dy - back * 0.16) < 2.6 - back * 0.05)
        socket = (dz ** 2 / 49 + dy ** 2 / 25) < 1
        colour = np.where((band | socket)[..., None],
                          shade(pal['hide'], v - 0.2 + 0.4 * scales(tx, 3, seed=29), tx.x, tx.y, dither=0.0), colour)
        rim = ((dz ** 2 / 49 + dy ** 2 / 25) < 1.45) & ~socket & (dy > 0)
        colour = np.where(rim[..., None], shade(pal['hide'], v + 0.16, tx.x, tx.y, dither=0.0), colour)
        # lip scales: the two rows along the mouth, alternately light and dark
        if part in ('snout', 'tip', 'cheek', 'temple') and not jaw:
            lip = row >= (tx.face.rect[3] - 2)
            if part in ('cheek', 'temple'):
                lip &= z > 26.0
            k, _ = grid(tx, 2)
            light = (k // 2) % 2 == 0
            colour = np.where((lip & light)[..., None], shade(pal['belly'], 0.62 + 0.1 * mottle, tx.x, tx.y), colour)
            colour = np.where((lip & ~light)[..., None], shade(pal['hide'], 0.42, tx.x, tx.y), colour)
        if jaw:
            k, _ = grid(tx, 2)
            lip = row <= 1
            light = (k // 2) % 2 == 1
            colour = np.where((lip & light)[..., None], shade(pal['belly'], 0.58, tx.x, tx.y), colour)
            colour = np.where((lip & ~light)[..., None], shade(pal['hide'], 0.44, tx.x, tx.y), colour)
            pale = (row > 1) & (y < 28.3 + 0.4 * (noise(p, 1.0, seed=27) - 0.5))
            colour = np.where(pale[..., None], shade(pal['belly'], 0.52 + 0.08 * mottle + 0.5 * scales(tx, 3, seed=26),
                                                     tx.x, tx.y), colour)
    if part == 'snout_top' and n[1] > 0.5:
        # the nostrils: two dark slits on top of the snout near its front, either side of the ridge
        nostril = (np.abs(np.abs(x) - 1.0) < 0.3) & (np.abs(z - 34.05) < 0.21)
        colour = np.where(nostril[..., None], shade(pal['stripe'], 0.1, tx.x, tx.y), colour)
    if n[1] > 0.5 and part in ('bridge', 'snout_top', 'crest', 'crown', 'roof', 'brow'):
        knob = (cells(p, tx.texel * 2.02, seed=28) > 0.8) & (scales(tx, 3, seed=24) > -0.02)
        colour = np.where(knob[..., None], shade(pal['hide'], v + 0.14, tx.x, tx.y), colour)
    if part == 'horn':
        colour = shade(pal['scute'], 0.55 + 0.5 * mottle + 0.25 * max(n[1], 0), tx.x, tx.y, dither=0.0)
    if n[1] < -0.5:
        colour = shade(pal['belly'], 0.48 + 0.06 * mottle, tx.x, tx.y) if jaw or part in ('cranium',) else \
            shade(pal['mouth'], 0.35 + 0.1 * mottle, tx.x, tx.y)     # the palate
    if jaw and n[1] > 0.5:
        # the mouth floor and the gums, inside a rim of lip scales along the jaw's edge
        i, ci = grid(tx, 0)
        k, ck = grid(tx, 2)
        e = np.minimum(np.minimum(i, ci - 1 - i), ck - 1 - k if part == 'chin' else 99)
        colour = np.where((e <= 1)[..., None], shade(pal['hide'], 0.56 + 0.1 * mottle, tx.x, tx.y),
                          shade(pal['mouth'], 0.3 + 0.1 * mottle, tx.x, tx.y))
    return colour


def teeth(tx):
    """A row of pointed teeth as pixel art: each tooth a few texels wide, tapering to its tip, the rest clear; the
    teeth grow from the back of the jaw to the middle and shorten again toward the front."""
    box = tx.box
    p = tx.points
    along = 2 if box.tag['row'] != 'front' else 0
    a = (p[..., along] - (box.center[along] - box.size[along] / 2)) / tx.texel
    h = (p[..., 1] - (box.center[1] - box.size[1] / 2)) / tx.texel
    rows = box.size[1] / tx.texel
    width = 4.0
    k = np.floor(a / width)
    f = a / width - k
    zc = box.center[2] - box.size[2] / 2 + (k + 0.5) * width * tx.texel
    grow = np.interp(zc, [26.5, 28.5, 31.0, 33.5, 35.0], [0.55, 0.85, 1.0, 0.75, 0.6]) if along == 2 else 0.7
    length = rows * grow * (0.8 + 0.2 * cells(np.stack([k, np.zeros_like(k), np.full(k.shape, 7.0)], -1), 1.0, seed=5))
    root = (rows - h) if box.tag['row'] != 'lower' else h          # distance from the gum
    half = 0.5 * (1 - np.clip(root / np.maximum(length, 1e-3), 0, 1)) ** 0.8 + 0.06
    alpha = (np.abs(f - 0.5) <= half) & (root <= length)
    v = 0.85 - 0.45 * (root / np.maximum(length, 1)) ** 2
    v = np.where(root < 1, 0.35, v)                                 # stained at the gum
    return shade(TOOTH_RAMP, v, tx.x, tx.y, dither=0.0), alpha


def keratin(tx, ramp):
    """Claws and the ridge scutes: dark at the base, lighter to the tip."""
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
        if part in ('trunk', 'tail', 'neck', 'thigh'):
            return hide(tx, pal), None
        return limb(tx, pal), None
    if mat == 'head':
        return head(tx, pal), None
    if mat == 'scute':
        return keratin(tx, pal['scute']), None
    if mat == 'claw':
        return keratin(tx, CLAW_RAMP), None
    return shade(TONGUE_RAMP, 0.55 + 0.25 * (cells(tx.points, tx.texel * 1.01, seed=43) - 0.5), tx.x, tx.y), None
