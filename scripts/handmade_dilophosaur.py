"""The Dilophosaurus, designed box by box on the ARK rig (Dilo); built by scripts/handmade_models.py.

A small lithe theropod in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a short
trunk on long slim legs and three-toed feet, a thin tail held level, an S-curved neck and a narrow skull topped by
two thin crests. The frill round the neck is a fan of membrane on the rig's own ribs (frill A to D on each side),
cut out of thin cards with a scalloped rim and a spine at each rib's end, so it folds and spreads with the ARK
clips. The bind pose holds the head a little turned and shifted to its right: the head is designed symmetric in its
own frame (HEAD_YAW about the head's pivot). The jaw is designed shut as the idle holds it, the teeth are cut-out
rows, and a row of small quills runs down the back.
"""
import math

import numpy as np

from handmade_models import cells, fbm, hexes, lift, noise, rotation, shade, unit
from handmade_tyrannosaurus import face_grid, grid, midline_top, scales, stripe_runs

FOLDER, STEM, HEIGHT = 'Dilophosaur', 'dilophosaur', 1.9
DENSITY = 1.0            # texels per runtime unit; the head, crests and frill are painted at FACE times that
FACE = 3.0
ATLAS_WIDTH = 256
PREVIEW_VARIANT = 'Natural'
ASSET = 'Dilo'
PREVIEW_CLIPS = ('Dilo-Idle', 'Dilo-Move-Fwd', 'Dilo-Attack-Spit', 'Dilo-Torpid')
JAW_POSE = ('Dilo-Idle', 0.0, ['jaw', 'tongue'])     # the frame the jaw is designed in
HEAD, JAW = 'c_head', 'c_jaw'
HEAD_PIVOT = np.array([-0.173, 0.0, 6.046])          # the head's midline passes here (x, -, z) in the bind pose
HEAD_YAW = -4.0                                      # and turns this far, front toward -x

# The trunk, segment by segment: (bone, rear z, front z, belly, back, half width) from the ARK mesh's sections, the
# quills left out; the tiers across each follow the body's cross-section (TIER_SHAPE).
TRUNK = [('c_back1', -2.3, -0.85, 4.0, 6.2, 0.95), ('c_back1', -1.15, 0.25, 3.55, 6.4, 1.15),
         ('c_back2', -0.05, 1.4, 3.6, 6.45, 1.12), ('c_back3', 1.1, 2.55, 3.95, 6.43, 1.04),
         ('c_back4', 2.25, 3.65, 4.3, 6.4, 0.88)]
TIER_SHAPE = [(0.0, 0.1, 0.42), (0.1, 0.28, 0.75), (0.28, 0.78, 1.0), (0.78, 0.93, 0.7), (0.93, 1.0, 0.36)]
# The tail's outline along z: (z, underside, top, half width), measured on the ARK mesh's sections.
TAIL = [(-15.6, 5.82, 5.86, 0.04), (-13.5, 5.72, 5.92, 0.1), (-12.5, 5.79, 6.13, 0.15), (-11.5, 5.84, 6.26, 0.2),
        (-10.5, 5.76, 6.29, 0.23), (-9.5, 5.6, 6.25, 0.29), (-8.5, 5.33, 6.11, 0.35), (-7.5, 5.1, 6.0, 0.39),
        (-6.5, 4.94, 5.98, 0.46), (-5.5, 4.78, 5.97, 0.56), (-4.5, 4.67, 6.02, 0.64), (-3.5, 4.38, 6.2, 0.72),
        (-2.5, 4.24, 6.3, 0.85)]
TAIL_BONES = [('c_tail1', -4.51, -2.15), ('c_tail2', -6.42, -4.31), ('c_tail3', -8.33, -6.22), ('c_tail4', -10.22, -8.13),
              ('c_tail5', -12.13, -10.02), ('c_tail6', -14.03, -11.93), ('c_tail7', -15.15, -13.83),
              ('c_tail8', -15.6, -14.95)]
# The S-curved neck: per bone (rear z, front z, half width, x of its midline), laid along the top line of its back
# and the bottom line of its throat.
NECK = [('c_neck1', 3.4, 4.6, 0.64, 0.0), ('c_neck2', 4.45, 5.35, 0.56, -0.015), ('c_neck3', 5.25, 6.3, 0.52, -0.076)]
NECK_TOP = ([2.5, 3.5, 4.5, 5.0, 5.5, 6.0, 6.4], [6.4, 6.6, 7.15, 7.45, 7.7, 7.95, 8.05])
NECK_THROAT = ([2.5, 3.5, 4.5, 5.0, 5.5, 6.0, 6.5], [4.3, 4.6, 5.35, 5.75, 6.15, 6.5, 6.7])


def tail_at(z):
    zs = [t[0] for t in TAIL]
    return tuple(float(np.interp(z, zs, [t[k] for t in TAIL])) for k in (1, 2, 3))


def neck_section(d, bone, z0, z1, half, x=0.0, mat='hide'):
    """Four slabs along the neck between z0 and z1: a narrow crest on the top line, the upper and lower cores that
    meet in the middle, and a narrow keel along the throat, each sloped like its line."""
    top = lambda z: float(np.interp(z, *NECK_TOP))
    low = lambda z: float(np.interp(z, *NECK_THROAT))
    zm = (z0 + z1) / 2
    slope_top = math.atan2(top(z1) - top(z0), z1 - z0)
    slope_low = math.atan2(low(z1) - low(z0), z1 - z0)
    depth = (top(zm) - low(zm)) * math.cos((slope_top + slope_low) / 2)
    core = depth * 0.5 + 0.12
    a, b = z0 - 0.08, z1 + 0.08                      # the cores reach past the bends, so no wedge opens between them
    d.slab(bone, (top(z0), z0), (top(z1), z1), half * 0.6, 0.16, mat, x=x, part='neck', tier=2)
    d.slab(bone, (top(a) - 0.14, a), (top(b) - 0.14, b), half, core, mat, x=x, part='neck', tier=1)
    d.slab(bone, (low(a) + 0.14, a + 0.01), (low(b) + 0.14, b - 0.01), half * 0.93, core, mat, x=x, top=False,
           part='neck', tier=-1)
    d.slab(bone, (low(z0), z0 + 0.04), (low(z1), z1 - 0.04), half * 0.62, 0.16, mat, x=x, top=False, part='neck',
           tier=-2)


def tiers(d, bone, z0, z1, rows, mat='hide', part='trunk', inset=0.02):
    """Stacked boxes across a segment, each overlapping the next a little; the tiers off the core are inset a
    hair at both ends, so no two neighbours share an end plane."""
    core = max(range(len(rows)), key=lambda k: rows[k][2])
    for k, (y0, y1, half) in enumerate(rows):
        e = 0.0 if k == core else inset * (1 + abs(k - core)) + (0.01 if k > core else 0.0)
        lo = y0 - (0.015 if k > core else 0.0)
        hi = y1 + (0.015 if k < core else 0.0)
        n = len(d.boxes)                 # the body is small: every box is nudged its own hair, so none share a plane
        half, lo, hi = half * (1 + 0.04 * (n % 5)), lo - 0.021 * (n % 3), hi + 0.021 * (n % 4)
        d.span(bone, (-half, lo, z0 + e), (half, hi, z1 - e), mat, part=part, tier=k - core, rows=len(rows))


HEAD_TURN = rotation(yaw=HEAD_YAW)


def hspan(d, bone, lo, hi, mat, mirror=False, **tag):
    """A box given in the head's own symmetric frame (x across its midline, z along it from the world origin),
    turned and moved to where the bind pose holds the head; mirror adds its twin across the midline."""
    lo, hi = np.array(lo, float), np.array(hi, float)
    for sign in ((1, -1) if mirror else (1,)):
        a, b = lo * [sign, 1, 1], hi * [sign, 1, 1]
        centre = (a + b) / 2
        world = HEAD_TURN @ (centre - [0.0, 0.0, HEAD_PIVOT[2]]) + HEAD_PIVOT
        d.add(bone, world, np.abs(b - a), HEAD_TURN, mat, **tag)


def rib(d, side, name, joints, w, tip_reach):
    """The frill between a rib's joints, one thin cut-out card per bone, in the fan's plane; the last reaches past
    the rib's end by tip_reach (as the rib goes on) to carry the rim and its spine."""
    points = [d.rig.pivot(f'{side}_frill{name}{k}') for k in joints]
    end = points[-1] + unit(points[-1] - points[-2]) * tip_reach
    points.append(end)
    for k, joint in enumerate(joints):
        a, b = points[k], points[k + 1]
        across = np.cross((0.0, 0.0, 1.0), b - a)
        last = k == len(joints) - 1
        d.seg(f'{side}_frill{name}{joint}', a, b, w[k], 0.03 + 0.03 * (k % 2), 'frill', ext=(0.05, 0.0), side=across,
              open=('up', 'down', 'east', 'west'), cutout=True, keep=True, density=FACE, part='frill', rim=last,
              ring=k, rib=name)


def bone_at(z):
    """The spine bone that carries the body at z."""
    for bone, start in (('c_neck3', 5.3), ('c_neck2', 4.54), ('c_neck1', 3.57), ('c_back4', 2.44), ('c_back3', 1.31),
                        ('c_back2', 0.18), ('c_back1', -2.29)):
        if z >= start:
            return bone
    return next((b for b, z0, z1 in TAIL_BONES if z0 <= z < z1), 'c_tail8')


def design(d):
    rig = d.rig
    P = rig.pivot
    # the jaw and the tongue are designed shut, as the idle holds them
    for bone in rig.bones:
        if bone == JAW or 'tongue' in bone:
            rig.design_as_in(bone, 'Dilo-Idle', 0.0, HEAD)

    # ---------------------------------------------------------------- trunk, tail and neck
    # neighbouring segments are nudged apart a hair (j), so their tiers never share a plane where they overlap
    for k, (bone, z0, z1, belly, back, half) in enumerate(TRUNK):
        j = 0.035 * (k % 3)
        rows = [(belly + (back - belly) * a + j, belly + (back - belly) * b + j, half * w * (1 + 0.03 * (k % 2)))
                for a, b, w in TIER_SHAPE]
        tiers(d, bone, z0, z1, rows)
    for k, (bone, z0, z1) in enumerate(TAIL_BONES):
        low, high, half = tail_at((z0 + z1) / 2)
        h = high - low
        j, half = 0.03 * (k % 2), half * (1.1 if k % 2 else 0.9)
        rows = [(low + j, low + 0.2 * h + j, half * 0.62), (low + 0.2 * h + j, high - 0.2 * h + j, half),
                (high - 0.2 * h + j, high + j, half * 0.55)]
        if half < 0.25:                  # the thin end of the tail is one box per bone
            rows = [(low + j, high + j, half)]
        tiers(d, bone, z0, z1, rows, part='tail', inset=0.02)
    for bone, z0, z1, half, x in NECK:
        neck_section(d, bone, z0, z1, half, x)

    # ---------------------------------------------------------------- head, in its own frame: narrow, twin crests
    f = dict(density=FACE)
    hspan(d, HEAD, (-0.62, 7.1, 5.95), (0.62, 8.12, 6.9), 'head', part='cranium', **f)
    # the face round the eye: one flat side where the eye is set (build_creature_eyes footprint), a roof above it
    hspan(d, HEAD, (-0.52, 7.08, 6.85), (0.52, 8.15, 7.65), 'head', part='temple', **f)
    hspan(d, HEAD, (-0.42, 8.1, 6.0), (0.42, 8.24, 7.62), 'head', part='roof', **f)
    hspan(d, HEAD, (-0.42, 7.04, 7.6), (0.42, 7.95, 8.35), 'head', part='snout', **f)
    hspan(d, HEAD, (-0.3, 7.9, 7.62), (0.3, 8.05, 8.3), 'head', part='snout_top', **f)
    hspan(d, HEAD, (-0.28, 7.07, 8.3), (0.28, 7.62, 8.68), 'head', part='tip', **f)
    # the twin crests: thin plates cut to their profile along the top of the skull
    hspan(d, HEAD, (0.16, 8.0, 6.5), (0.23, 8.85, 8.5), 'crest', mirror=True, part='crest',
          open=('up', 'down', 'north', 'south'), cutout=True, keep=True, **f)
    # the teeth: cut-out rows hanging from the upper lip outside the narrower jaw, and across the front
    teeth = dict(open=('up', 'down', 'north', 'south'), cutout=True, keep=True, **f)
    hspan(d, HEAD, (0.35, 6.9, 7.5), (0.385, 7.1, 8.3), 'tooth', mirror=True, part='teeth', row='upper', **teeth)
    hspan(d, HEAD, (-0.24, 6.9, 8.6), (0.24, 7.1, 8.635), 'tooth', part='teeth', row='front',
          open=('up', 'down', 'east', 'west'), cutout=True, keep=True, **f)

    hspan(d, JAW, (-0.46, 6.65, 6.1), (0.46, 7.12, 7.5), 'head', part='jaw', **f)
    hspan(d, JAW, (-0.33, 6.75, 7.45), (0.33, 7.105, 8.42), 'head', part='chin', **f)
    hspan(d, JAW, (0.27, 7.0, 7.55), (0.3, 7.22, 8.3), 'tooth', mirror=True, part='teeth', row='lower', **teeth)
    hspan(d, 'c_tongue2', (-0.2, 7.14, 6.5), (0.2, 7.17, 7.9), 'mouth', part='tongue', **f)

    # ---------------------------------------------------------------- the frill: membrane on the ribs of each side
    for side in ('l', 'r'):
        rib(d, side, 'A', (1, 2), (0.85, 1.6), 0.8)
        rib(d, side, 'B', (1, 2, 3), (0.9, 1.5, 2.1), 0.6)
        rib(d, side, 'C', (1, 2, 3), (0.9, 1.6, 2.2), 0.45)
        rib(d, side, 'D', (1, 2, 3), (0.9, 1.5, 2.1), 0.8)

    # ---------------------------------------------------------------- quills down the back and the tail
    body = list(d.boxes)
    for z, h in QUILLS:
        y = midline_top(body, z)
        d.seg(bone_at(z), (0.0, y - 0.06, z + 0.04), (0.0, y + h, z - 0.18), 0.06, 0.1, 'scute', side=(1, 0, 0),
              part='spike', along=(0.0, 1.0))

    # ---------------------------------------------------------------- arms: three clawed fingers
    elbow, wrist = P('l_elbow'), P('l_wrist')
    d.span('l_scapula', (0.55, 4.4, 2.15), (1.2, 5.6, 3.6), 'hide', mirror=True, part='shoulder')
    d.seg('l_armTiwst', P('l_shoulder'), elbow, 0.38, 0.42, 'hide', ext=(0.12, 0.08), mirror=True, part='arm')
    d.seg('l_elbow', elbow, wrist, 0.3, 0.3, 'hide', ext=(0.06, 0.05), mirror=True, part='forearm')
    d.seg('l_wrist', wrist, P('l_fingerB1'), 0.24, 0.32, 'hide', ext=(0.04, 0.03), mirror=True, part='hand')
    for finger in ('A', 'B', 'C'):
        a, b, c = (P(f'l_finger{finger}{k}') for k in (1, 2, 3))
        tip = c + (c - b) * 1.5 + np.array([0.0, -0.04, 0.0])
        d.seg(f'l_finger{finger}1', a, b, 0.13, 0.14, 'hide', ext=(0.04, 0.01), mirror=True, part='finger')
        d.seg(f'l_finger{finger}2', b, c, 0.11, 0.12, 'hide', ext=(0.01, 0.01), mirror=True, part='finger')
        d.seg(f'l_finger{finger}3', c, tip, 0.08, 0.1, 'claw', ext=(0.01, 0.0), mirror=True, part='claw',
              along=(0.0, 1.0))

    # ---------------------------------------------------------------- legs
    d.span('l_legTwist', (0.45, 3.55, -1.5), (1.45, 5.7, 0.7), 'hide', mirror=True, part='thigh')
    d.seg('l_knee', (0.97, 3.55, 0.15), P('l_ankle'), 0.62, 0.78, 'hide', ext=(0.0, 0.05), mirror=True, part='shin')
    d.seg('l_ankle', P('l_ankle'), (0.9, 0.36, -0.15), 0.5, 0.48, 'hide', ext=(0.06, 0.0), mirror=True, part='meta')
    d.span('l_ankle', (0.55, 0.0, -0.45), (1.3, 0.38, 0.05), 'hide', mirror=True, part='foot')
    for name, tip, w in (('A', (0.55, 0.08, 1.0), 0.26), ('B', (1.15, 0.08, 1.25), 0.3), ('C', (1.75, 0.08, 0.75), 0.25)):
        joints = [P(f'l_toe{name}{k}') for k in (1, 2, 3)]
        points = joints + [np.array(tip)]
        for k in range(3):
            a, b = points[k], points[k + 1]
            if k < 2:
                d.seg(f'l_toe{name}{k + 1}', a, b, w * (1 - 0.1 * k), w * (1 - 0.1 * k), 'hide',
                      ext=(0.05 if k == 0 else 0.015, 0.01), mirror=True, part='toe')
            else:
                d.seg(f'l_toe{name}3', a, b, w * 0.55, w * 0.6, 'claw', ext=(0.015, 0.0), mirror=True, part='claw',
                      along=(0.0, 1.0))


# The quills: (z, height above the skin) from the shoulders down the back to the tail's tuft.
QUILLS = [(round(float(z), 2), round(0.3 + 0.12 * math.sin(z * 1.7), 2)) for z in np.arange(3.0, -14.5, -0.42)]


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(hide=hexes('#1c2414', '#27321b', '#334123', '#40512b', '#4d6133', '#5b713c', '#6a8246', '#7a9350'),
                    belly=hexes('#8a8a5e', '#9b9b6e', '#acac7f', '#bcbc90', '#cbcba1', '#d9d9b2'),
                    stripe=hexes('#0d110a', '#141a0e', '#1b2313', '#232d18'),
                    scute=hexes('#1d1f14', '#2a2d1d', '#383c27', '#474c31', '#575d3c'),
                    horn=hexes('#3a1810', '#552417', '#70321f', '#8b4229', '#a55535'),
                    frill=hexes('#5a1a12', '#7e2616', '#a3381a', '#c4531f', '#dc7628', '#eb9d36', '#f2c24c'),
                    mouth=hexes('#3a1414', '#55201f', '#71302c', '#8c423b', '#a5574c', '#bb6e5e')),
}
PALETTES['green'] = PALETTES['Natural']
PALETTES['olive'] = dict(hide=hexes('#22231a', '#303122', '#3f402b', '#4f5034', '#60603e', '#717148', '#838252', '#95935e'),
                         belly=hexes('#7d755a', '#8f8769', '#a19979', '#b2ab8a', '#c2bb9b', '#d1caac'),
                         stripe=hexes('#0f100b', '#161710', '#1d1e15', '#25261a'),
                         scute=hexes('#16170f', '#202116', '#2b2c1d', '#373825', '#44452e'),
                         horn=hexes('#36190f', '#4f2416', '#69311e', '#833f27', '#9c4e31'),
                         frill=hexes('#4a1d10', '#6b2a14', '#8c3a18', '#ad4f1d', '#c86b24', '#da8e30', '#e6b343'),
                         mouth=PALETTES['Natural']['mouth'])
PALETTES['umber'] = dict(hide=hexes('#21170f', '#302217', '#402d1f', '#513928', '#634632', '#75543c', '#886347', '#9b7253'),
                         belly=hexes('#7a6650', '#8b7861', '#9c8a72', '#ad9c84', '#bdae96', '#ccbfa8'),
                         stripe=hexes('#0e0a07', '#150f0a', '#1c140e', '#241a12'),
                         scute=hexes('#16100b', '#211811', '#2c2017', '#38291e', '#453326'),
                         horn=hexes('#3a1a10', '#532618', '#6d3421', '#87432b', '#a15336'),
                         frill=hexes('#401614', '#5e1f1a', '#7d2a20', '#9d3826', '#bb4b2e', '#d36539', '#e48848'),
                         mouth=PALETTES['Natural']['mouth'])
PALETTES['sand'] = dict(hide=hexes('#5e533f', '#6f644e', '#81755d', '#94876c', '#a69a7c', '#baac8c', '#cdbf9d', '#e1d3ae'),
                        belly=hexes('#c2b69c', '#ccc1a9', '#d6ccb7', '#e0d7c4', '#eae3d2', '#f4eee0'),
                        stripe=hexes('#4c4131', '#584c3c', '#645847', '#716452'),
                        scute=hexes('#3f3629', '#4e4434', '#5d5240', '#6d604c', '#7d6f59'),
                        horn=hexes('#5a2a18', '#763820', '#92482a', '#ad5a34', '#c56d40'),
                        frill=hexes('#2a3e52', '#344f69', '#3f6181', '#4b7499', '#5d89ae', '#76a0c0', '#93b8d0'),
                        mouth=PALETTES['Natural']['mouth'])
TOOTH_RAMP = hexes('#8a7f66', '#ab9f83', '#c9bf9f', '#e0d8bc', '#efe9d5', '#faf7ec')
CLAW_RAMP = hexes('#100e0b', '#1d1914', '#2b251e', '#3b3329', '#4e4436', '#655845')
TONGUE_RAMP = hexes('#5e2526', '#7a3333', '#954442', '#ad5853', '#c26e66')

# The body's outline along its length (z), from the tail tip to the snout: the top of the back and the underside.
BACK = ([-15.6, -12.5, -10.5, -8.5, -6.5, -4.5, -2.5, -0.5, 1.5, 2.5, 3.5, 4.5, 5.5, 6.4, 7.5, 8.7],
        [5.86, 6.13, 6.29, 6.11, 5.98, 6.02, 6.3, 6.42, 6.45, 6.42, 6.6, 7.15, 7.7, 8.1, 8.15, 7.6])
UNDER = ([-15.6, -12.5, -10.5, -8.5, -6.5, -4.5, -2.5, -0.5, 1.5, 2.5, 3.5, 4.5, 5.5, 6.5, 7.5, 8.4],
         [5.82, 5.79, 5.76, 5.33, 4.94, 4.67, 4.24, 3.55, 3.75, 4.3, 4.6, 5.35, 6.15, 6.65, 6.75, 6.85])
# The dark bands over the back, as pixel runs: (z where a band crosses the spine, its width in texels there, how far
# down the side it reaches in units).
STRIPES = [(5.6, 1, 0.5), (4.6, 1, 0.7), (3.4, 1, 0.9), (2.2, 1, 1.1), (1.0, 2, 1.2), (-0.2, 2, 1.2), (-1.4, 2, 1.2),
           (-2.7, 1, 1.0), (-4.0, 1, 0.9), (-5.3, 1, 0.8), (-6.6, 1, 0.7), (-7.9, 1, 0.6), (-9.2, 1, 0.5),
           (-10.6, 1, 0.4), (-12.0, 1, 0.35), (-13.4, 1, 0.3)]
RUNS = [stripe_runs(171 + k, w, 60) for k, (_, w, _) in enumerate(STRIPES)]
EYE = (7.2, 7.84)       # (z, y): the eye, where the socket and the stripe through it are painted
FRILL_CENTRE, FRILL_RADIUS = (-0.2, 6.6), 2.45                   # the fan in the bind pose (x, y)
CREST = ([6.5, 6.85, 7.3, 7.8, 8.15, 8.5], [8.6, 8.82, 8.45, 8.5, 8.72, 8.12])   # the crest's top edge along z


def frill(tx, pal):
    """The frill's membrane: dark at the neck, through red and orange to a yellow rim, with dark spots and a darker
    rib down each card's middle; the outer card is scalloped at its rim and ends in a spine along the rib."""
    box = tx.box
    local = (tx.points - box.center) @ box.axes
    t = local[..., 1] / box.size[1] + 0.5                      # along the rib, 0 at the neck end of this card
    s = local[..., 0] / (box.size[0] / 2)                      # across, -1 to 1
    r = (box.tag['ring'] + t) / 3.0                            # out from the neck over the whole fan
    v = 0.15 + 0.85 * np.clip(r, 0, 1) + 0.1 * (cells(tx.points, tx.texel * 2.02, seed=181) - 0.5)
    colour = shade(pal['frill'], v, tx.x, tx.y, dither=0.0)
    spot = cells(tx.points, tx.texel * 3.03, seed=182) > 0.86
    colour = np.where(spot[..., None], shade(pal['frill'], v - 0.35, tx.x, tx.y, dither=0.0), colour)
    rib_line = np.abs(s) < 0.12
    colour = np.where(rib_line[..., None], shade(pal['frill'], v - 0.25, tx.x, tx.y, dither=0.0), colour)
    # every card is trimmed to the fan: a disc round the neck (in the bind pose) with a scalloped rim
    x, y = tx.points[..., 0] - FRILL_CENTRE[0], tx.points[..., 1] - FRILL_CENTRE[1]
    radius, angle = np.hypot(x, y), np.arctan2(y, np.abs(x))
    rim = FRILL_RADIUS - 0.13 * (1 - np.abs(np.cos(angle * 4.5)))
    alpha = radius <= rim
    colour = np.where((alpha & (radius > rim - 0.16))[..., None], shade(pal['frill'], 0.98, tx.x, tx.y, dither=0.0),
                      colour)
    if box.tag.get('rim'):                                        # the spine running on past the rim along the rib
        spine = (np.abs(s) < 0.22 * (1.0 - t) + 0.05) & (t > 0.2)
        colour = np.where((spine & ~alpha)[..., None], shade(pal['scute'], 0.4 + 0.5 * t, tx.x, tx.y, dither=0.0),
                          colour)
        alpha = alpha | spine
    return colour, alpha


def crest(tx, pal):
    """The crests: thin plates cut to their profile, the hide's colour at the base turning to the frill's red toward
    the top edge, with fine ribs up them."""
    p = tx.points
    z, y = p[..., 2], p[..., 1]
    top = np.interp(z, *CREST)
    alpha = y <= top
    t = np.clip((y - 8.05) / np.maximum(top - 8.05, 0.1), 0, 1)
    col, _ = face_grid(tx)
    v = 0.35 + 0.5 * t - 0.12 * (col % 3 == 0)
    colour = np.where((t > 0.45)[..., None], shade(pal['frill'], v, tx.x, tx.y, dither=0.0),
                      shade(pal['hide'], 0.4 + 0.4 * t, tx.x, tx.y, dither=0.0))
    return colour, alpha


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
    row of lip scales along both jaws, knobs along the snout, the nostrils at the tip."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    mottle = fbm(p, 2.0, seed=23, octaves=2) - 0.5
    col, row = face_grid(tx)
    top = np.interp(z, [6.0, 7.0, 7.6, 8.3, 8.68], [8.24, 8.24, 8.05, 7.95, 7.6])
    bottom = np.interp(z, [6.1, 7.5, 8.4], [6.65, 6.75, 6.8])
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
            nostril = (np.abs(z - 8.5) < 0.07) & (np.abs(y - 7.42) < 0.05)
            colour = np.where(nostril[..., None], shade(pal['stripe'], 0.1, tx.x, tx.y), colour)
        if jaw:
            k, _ = grid(tx, 2)
            lip = row <= 1
            light = (k // 2) % 2 == 1
            colour = np.where((lip & light)[..., None], shade(pal['belly'], 0.58, tx.x, tx.y), colour)
            colour = np.where((lip & ~light)[..., None], shade(pal['hide'], 0.44, tx.x, tx.y), colour)
            pale = (row > 1) & (y < 6.95 + 0.05 * (noise(p, 0.7, seed=27) - 0.5))
            colour = np.where(pale[..., None], shade(pal['belly'], 0.52 + 0.08 * mottle + 0.5 * scales(tx, 3, seed=26),
                                                     tx.x, tx.y, dither=0.0), colour)
    if n[1] > 0.5 and part in ('snout_top', 'roof'):
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
    grow = np.interp(zc, [7.5, 7.8, 8.0, 8.3, 8.6], [0.6, 0.9, 1.0, 0.8, 0.6]) if along == 2 else 0.7
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
    if mat == 'frill':
        return frill(tx, pal)
    if mat == 'crest':
        return crest(tx, pal)
    if mat == 'horn':
        return horn(tx, pal), None
    if mat == 'scute':
        return keratin(tx, pal['scute']), None
    if mat == 'claw':
        return keratin(tx, CLAW_RAMP), None
    return shade(TONGUE_RAMP, 0.55 + 0.25 * (cells(tx.points, tx.texel * 1.01, seed=43) - 0.5), tx.x, tx.y), None
