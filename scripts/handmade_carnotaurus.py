"""The Carnotaurus, designed box by box on the ARK rig (Carno); built by scripts/handmade_models.py.

A deep-bodied, long-legged theropod in the rig's bind pose (source units: +y up, the animal faces +z, its left is
+x): a barrel trunk on slim thighs and long shins, a tail that tapers from the hips to a thin point, and a short
neck that climbs steeply to a short, deep, narrow skull. A thick horn sweeps out and up over each eye, the lower jaw
is designed shut as the idle holds it with the teeth as cut-out rows along both jaws, and the arms are stubs with
tiny fingers. Rows of low osteoderm scutes run along both sides of the back and down the flanks.
"""
import math

import numpy as np

from handmade_models import cells, fbm, hexes, lift, noise, shade
from handmade_tyrannosaurus import face_grid, grid, midline_top, scales, stripe_runs

FOLDER, STEM, HEIGHT = 'Carnotaurus', 'carnotaurus', 6.0
DENSITY = 1.0            # texels per runtime unit, Minecraft's own density; the head is painted at FACE times that
FACE = 2.0
ATLAS_WIDTH = 512
PREVIEW_VARIANT = 'Natural'
ASSET = 'Carno'
PREVIEW_CLIPS = ('Carno-Idle', 'Carno-Move-Fwd', 'Carno-Attack-Bite', 'Carno-Torpid-Idle')
JAW_POSE = ('Carno-Idle', 0.0, ['jaw', 'tongue'])     # the frame the jaw is designed in
HEAD, JAW = 'c_head', 'c_jaw'

# The trunk, segment by segment: (bone, rear z, front z, belly, back, half width) from the ARK mesh's sections; the
# tiers across each follow the barrel's cross-section (TIER_SHAPE: share of the height, share of the half width).
TRUNK = [('c_back1', -8.1, -3.9, 12.3, 22.4, 3.3), ('c_back1', -4.1, 0.75, 11.4, 23.0, 3.65),
         ('c_back2', 0.55, 4.55, 11.0, 23.25, 3.95), ('c_back3', 4.35, 8.4, 12.0, 23.45, 4.1),
         ('c_back4', 8.2, 11.2, 13.0, 23.3, 3.7)]
TIER_SHAPE = [(0.0, 0.1, 0.42), (0.1, 0.28, 0.75), (0.28, 0.78, 1.0), (0.78, 0.93, 0.7), (0.93, 1.0, 0.36)]
# The tail, two segments per bone: (bone, rear z, front z, (keel, core, top) tiers).
TAIL_SEGMENTS = [
    ('c_tail1', -12.67, -7.95, [(13.86, 15.35, 1.22), (15.35, 19.81, 2.69), (19.81, 21.3, 1.34)]),
    ('c_tail1', -17.03, -12.47, [(14.71, 15.9, 1.3), (15.9, 19.47, 2.23), (19.47, 20.65, 1.04)]),
    ('c_tail2', -21.23, -16.83, [(15.31, 16.3, 1.09), (16.3, 19.26, 1.83), (19.26, 20.24, 0.95)]),
    ('c_tail2', -25.24, -21.03, [(16.04, 16.83, 0.93), (16.83, 19.2, 1.55), (19.2, 19.99, 0.84)]),
    ('c_tail3', -29.17, -25.04, [(17.01, 17.61, 0.81), (17.61, 19.42, 1.28), (19.42, 20.02, 0.69)]),
    ('c_tail3', -32.9, -28.97, [(17.9, 18.36, 0.71), (18.36, 19.71, 1.07), (19.71, 20.17, 0.59)]),
    ('c_tail4', -36.24, -32.7, [(18.34, 18.68, 0.56), (18.68, 19.71, 0.87), (19.71, 20.05, 0.52)]),
    ('c_tail4', -39.38, -36.04, [(18.3, 18.56, 0.45), (18.56, 19.35, 0.7), (19.35, 19.61, 0.43)]),
    ('c_tail5', -41.99, -39.18, [(17.92, 18.09, 0.33), (18.09, 18.63, 0.48), (18.63, 18.8, 0.3)]),
    ('c_tail5', -44.4, -41.79, [(17.33, 17.44, 0.2), (17.44, 17.75, 0.29), (17.75, 17.85, 0.17)]),
]
# The short neck climbs steeply from the shoulders to the head: per bone (rear z, front z, half width), laid along the
# top line of its back and the bottom line of its throat, measured on the ARK mesh's side view.
NECK = [('c_back4', 10.9, 12.4, 3.0), ('c_neck1', 12.2, 14.4, 2.3), ('c_neck2', 14.2, 16.6, 2.22),
        ('c_neck3', 16.4, 18.7, 2.18)]
NECK_TOP = ([9.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 19.0],
            [23.3, 23.05, 23.0, 23.9, 25.15, 26.25, 27.15, 27.8, 28.15, 28.3])
NECK_THROAT = ([9.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 19.0, 20.0],
               [13.2, 13.8, 14.3, 15.0, 16.4, 17.6, 18.5, 19.3, 20.1, 20.8, 21.2])


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
    d.slab(bone, (top(z0), z0), (top(z1), z1), half * 0.6, 0.8, mat, part='neck', tier=2)
    d.slab(bone, (top(a) - 0.7, a), (top(b) - 0.7, b), half, core, mat, part='neck', tier=1)
    d.slab(bone, (low(a) + 0.7, a + 0.04), (low(b) + 0.7, b - 0.04), half * 0.93, core, mat, top=False,
           part='neck', tier=-1)
    d.slab(bone, (low(z0), z0 + 0.2), (low(z1), z1 - 0.2), half * 0.62, 0.8, mat, top=False, part='neck', tier=-2)


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
    moved = [type(b)(b.bone, b.center - np.array([x, 0.0, 0.0]), b.size, b.axes, b.mat, b.tag) for b in boxes]
    return midline_top(moved, z)


def bone_at(z):
    """The spine bone that carries the body at z."""
    for bone, start in (('c_neck2', 14.26), ('c_neck1', 12.19), ('c_back4', 8.34), ('c_back3', 4.5),
                        ('c_back2', 0.65), ('c_back1', -8.1), ('c_tail1', -17.03)):
        if z >= start:
            return bone
    return 'c_tail2'


def design(d):
    rig = d.rig
    P = rig.pivot
    # the jaw and the tongue are designed shut, as the idle holds them; the bind pose roars
    for bone in (JAW, 'c_tongue1', 'c_tongue2', 'c_tongue3', 'c_tongue4'):
        rig.design_as_in(bone, 'Carno-Idle', 0.0, HEAD)

    # ---------------------------------------------------------------- trunk, tail and neck
    for bone, z0, z1, belly, back, half in TRUNK:
        rows = [(belly + (back - belly) * a, belly + (back - belly) * b, half * w) for a, b, w in TIER_SHAPE]
        tiers(d, bone, z0, z1, rows)
    for bone, z0, z1, rows in TAIL_SEGMENTS:
        tiers(d, bone, z0, z1, rows, part='tail', inset=0.06)
    for bone, z0, z1, half in NECK:
        neck_section(d, bone, z0, z1, half)

    # ---------------------------------------------------------------- head: short, deep and narrow, horned over the eyes
    f = dict(density=FACE)
    d.span(HEAD, (-2.2, 23.45, 17.5), (2.2, 27.95, 20.75), 'head', part='cranium', **f)
    d.span(HEAD, (-1.25, 27.85, 17.8), (1.25, 28.45, 20.6), 'head', part='roof', **f)
    # the face round the eye: one flat side where the eye is set (build_creature_eyes footprint), a brow above it
    d.span(HEAD, (-1.45, 23.6, 20.65), (1.45, 27.62, 23.6), 'head', part='temple', **f)
    d.span(HEAD, (-1.62, 27.6, 20.9), (1.62, 28.1, 23.35), 'head', part='brow', **f)
    # the horns sweep out, up and a little forward from above the eyes
    for a, b, w, mat in (((1.15, 27.75, 22.25), (2.05, 28.2, 22.95), 0.8, 'horn'),
                         ((1.95, 28.15, 22.85), (2.65, 28.48, 23.45), 0.58, 'horn'),
                         ((2.6, 28.45, 23.4), (3.05, 28.62, 23.85), 0.36, 'horn')):
        d.seg(HEAD, a, b, w, w, mat, ext=(0.15, 0.05), side=(0, 1, 0), mirror=True, part='horn', **f)
    # the short snout, its tip rounded down to the nostrils
    d.span(HEAD, (-1.12, 23.85, 23.5), (1.12, 27.4, 24.9), 'head', part='snout', **f)
    d.span(HEAD, (-0.88, 23.95, 24.8), (0.88, 26.95, 25.85), 'head', part='tip', **f)
    d.span(HEAD, (-0.55, 24.4, 25.75), (0.55, 26.3, 26.25), 'head', part='tip', **f)
    # the upper teeth: cut-out rows hanging from the lip, outside the narrower lower jaw, and across the front
    teeth = dict(open=('up', 'down', 'north', 'south'), cutout=True, keep=True, mirror=True, **f)
    d.span(HEAD, (1.92, 22.9, 19.4), (2.02, 23.6, 20.7), 'tooth', part='teeth', row='upper', **teeth)
    d.span(HEAD, (1.32, 23.05, 20.7), (1.42, 23.75, 23.5), 'tooth', part='teeth', row='upper', **teeth)
    d.span(HEAD, (0.95, 23.25, 23.5), (1.05, 23.95, 24.9), 'tooth', part='teeth', row='upper', **teeth)
    d.span(HEAD, (-0.75, 23.35, 25.55), (0.75, 24.05, 25.65), 'tooth', part='teeth', row='front',
           open=('up', 'down', 'east', 'west'), cutout=True, keep=True, **f)

    d.span(JAW, (-1.82, 21.2, 18.0), (1.82, 23.58, 21.5), 'head', part='jaw', **f)
    d.span(JAW, (-1.25, 21.85, 21.4), (1.25, 23.72, 23.55), 'head', part='jaw', **f)
    d.span(JAW, (-0.85, 22.75, 23.45), (0.85, 23.92, 25.5), 'head', part='chin', **f)
    d.span(JAW, (1.55, 23.4, 19.8), (1.65, 24.0, 21.4), 'tooth', part='teeth', row='lower', **teeth)
    d.span(JAW, (1.1, 23.55, 21.6), (1.2, 24.15, 23.4), 'tooth', part='teeth', row='lower', **teeth)
    d.span('c_tongue2', (-0.6, 23.62, 19.5), (0.6, 23.8, 23.0), 'mouth', part='tongue', **f)

    # ---------------------------------------------------------------- scutes: low rows along the back and the flanks
    body = list(d.boxes)
    for z, h in SCUTES:
        for x in (1.05, -1.05):
            y = top_at(body, x, z)
            d.span(bone_at(z), (x - 0.25, y - 0.3, z - 0.32), (x + 0.25, y + h, z + 0.32), 'scute', part='scute')
    for z, y, h in FLANK_SCUTES:                 # a row of bosses down each flank, standing out of the side
        half = max(b.size[0] / 2 for b in body if b.tag.get('part') == 'trunk' and b.contains((0.0, y, z), 0.0))
        d.span(bone_at(z), (half - 0.25, y - 0.24, z - 0.3), (half + h, y + 0.24, z + 0.3), 'scute', part='boss',
               mirror=True)

    # ---------------------------------------------------------------- arms: stubs with tiny fingers
    d.span('l_scapula', (2.3, 15.8, 9.2), (4.0, 19.6, 12.6), 'hide', mirror=True, part='shoulder')
    elbow, wrist = P('l_elbow'), P('l_wrist')
    d.seg('l_shoulder', P('l_shoulder'), elbow, 1.15, 1.2, 'hide', ext=(0.35, 0.25), mirror=True, part='arm')
    d.seg('l_elbow', elbow, wrist, 0.85, 0.85, 'hide', ext=(0.2, 0.2), mirror=True, part='forearm')
    for finger in ('A', 'B', 'C'):
        a = P(f'l_finger{finger}1')
        tip = a + (a - wrist) * 1.1 + np.array([0.0, -0.15, 0.0])
        d.seg(f'l_finger{finger}1', a, tip, 0.28, 0.3, 'claw', ext=(0.12, 0.0), mirror=True, part='claw',
              along=(0.2, 1.0))

    # ---------------------------------------------------------------- legs
    d.span('l_legTwist', (2.45, 13.4, -5.4), (5.1, 19.8, 2.4), 'hide', mirror=True, part='thigh')
    d.span('l_legTwist', (2.8, 19.7, -4.3), (4.7, 21.7, 1.3), 'hide', mirror=True, part='thigh')
    d.span('l_legTwist', (1.8, 11.4, -3.5), (5.0, 13.5, 1.7), 'hide', mirror=True, part='thigh')
    d.seg('l_knee', (3.4, 11.8, 0.2), (3.25, 8.2, -2.6), 3.05, 3.5, 'hide', mirror=True, part='shin')
    d.seg('l_knee', (3.25, 8.5, -2.35), (3.1, 5.4, -4.15), 2.3, 2.7, 'hide', ext=(0.0, 0.3), mirror=True, part='shin')
    d.seg('l_ankle', (3.05, 5.9, -4.45), (3.3, 1.45, -3.55), 2.1, 2.15, 'hide', mirror=True, part='meta')
    d.span('l_ankle', (2.05, 0.0, -4.65), (4.75, 1.65, -2.65), 'hide', mirror=True, part='foot')
    toes = {'A': ([P('l_toeA1'), P('l_toeA2'), P('l_toeA3')], (2.12, 0.28, 0.35), 0.95),
            'B': ([P('l_toeB1'), P('l_toeB2'), P('l_toeB3')], (4.18, 0.28, 0.85), 1.05),
            'C': ([P('l_toeC1'), P('l_toeC2'), P('l_toeC3')], (6.15, 0.28, -0.6), 0.9)}
    for name, (joints, tip, w) in toes.items():
        points = joints + [np.array(tip)]
        last = len(joints) - 1
        for k in range(len(joints)):
            a, b = points[k], points[k + 1]
            scale = 1 - 0.12 * k
            if k < last:
                d.seg(f'l_toe{name}{k + 1}', a, b, w * scale, w * scale, 'hide', ext=(0.2 if k == 0 else 0.06, 0.04),
                      mirror=True, part='toe')
            else:
                d.seg(f'l_toe{name}{k + 1}', a, b, w * scale * 0.62, w * scale * 0.68, 'claw', ext=(0.06, 0.0),
                      mirror=True, part='claw', along=(0.0, 1.0))


# The paired scutes along the back: (z, height above the skin), from the base of the tail to the nape.
SCUTES = [(-14.0, 0.18), (-12.4, 0.2), (-10.8, 0.22), (-9.2, 0.25), (-7.6, 0.28), (-6.0, 0.3), (-4.4, 0.32),
          (-2.8, 0.33), (-1.2, 0.34), (0.4, 0.35), (2.0, 0.35), (3.6, 0.35), (5.2, 0.35), (6.8, 0.34), (8.4, 0.33),
          (10.0, 0.32), (11.6, 0.3), (13.1, 0.28), (14.4, 0.26)]
# The bosses down each flank: (z, height, how far they stand out of the side).
FLANK_SCUTES = [(-6.6, 19.6, 0.22), (-4.4, 19.9, 0.25), (-2.2, 20.1, 0.26), (0.0, 20.2, 0.27), (2.2, 20.3, 0.27),
                (4.4, 20.3, 0.26), (6.6, 20.2, 0.25), (8.8, 20.0, 0.22)]


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(hide=hexes('#2a120d', '#3b1a12', '#4d2318', '#602d1e', '#743825', '#88442d', '#9c5136', '#b05f40'),
                    belly=hexes('#8a6450', '#9b7661', '#ac8873', '#bc9a85', '#cbac97', '#d9bea9'),
                    stripe=hexes('#120806', '#1a0c09', '#22100c', '#2b150f'),
                    scute=hexes('#3a2a22', '#4e3a2f', '#634c3e', '#7a5f4e', '#927460'),
                    horn=hexes('#2a1d16', '#3d2b20', '#52392b', '#684a38', '#7f5c46'),
                    mouth=hexes('#3a1414', '#55201f', '#71302c', '#8c423b', '#a5574c', '#bb6e5e')),
}
PALETTES['rust'] = PALETTES['Natural']
PALETTES['umber'] = dict(hide=hexes('#21170f', '#302217', '#402d1f', '#513928', '#634632', '#75543c', '#886347', '#9b7253'),
                         belly=hexes('#7a6650', '#8b7861', '#9c8a72', '#ad9c84', '#bdae96', '#ccbfa8'),
                         stripe=hexes('#0e0a07', '#150f0a', '#1c140e', '#241a12'),
                         scute=hexes('#3a3026', '#4c3f33', '#5f4f41', '#736150', '#887461'),
                         horn=hexes('#1e1712', '#2c221a', '#3b2e23', '#4b3b2e', '#5c4a3a'),
                         mouth=PALETTES['Natural']['mouth'])
PALETTES['tan'] = dict(hide=hexes('#3a2a18', '#4d3820', '#614729', '#765733', '#8b683e', '#a07a4a', '#b58c57', '#c99f65'),
                       belly=hexes('#a48d6c', '#b39d7d', '#c2ad8e', '#d0bd9f', '#dccbb0', '#e7d8c0'),
                       stripe=hexes('#21170d', '#2c1f12', '#372717', '#43301d'),
                       scute=hexes('#4f3e2c', '#655039', '#7b6348', '#917658', '#a78a69'),
                       horn=hexes('#2b2018', '#3e2f23', '#52402f', '#67523d', '#7c654c'),
                       mouth=PALETTES['Natural']['mouth'])
PALETTES['charcoal'] = lift(dict(hide=hexes('#0f0e0d', '#161514', '#1e1c1a', '#262421', '#2f2c29', '#393531', '#433f3a', '#4e4943'),
                                 belly=hexes('#3e3832', '#4a433c', '#564f47', '#625a52', '#6e665d', '#7a7168'),
                                 stripe=hexes('#2a1410', '#371a14', '#442018', '#52271d'),
                                 scute=hexes('#1c1a18', '#272421', '#322e2a', '#3e3934', '#4b453f'),
                                 horn=hexes('#121110', '#1b1917', '#25221f', '#302c28', '#3c3732'),
                                 mouth=hexes('#2e1212', '#451b1a', '#5c2724', '#743530', '#8a463f', '#9e584f')), 1.25, 6)
TOOTH_RAMP = hexes('#8a7f66', '#ab9f83', '#c9bf9f', '#e0d8bc', '#efe9d5', '#faf7ec')
CLAW_RAMP = hexes('#100e0b', '#1d1914', '#2b251e', '#3b3329', '#4e4436', '#655845')
TONGUE_RAMP = hexes('#5e2526', '#7a3333', '#954442', '#ad5853', '#c26e66')

# The body's outline along its length (z), from the tail tip to the snout: the top of the back and the underside.
BACK = ([-44, -36, -30, -24, -18, -12, -8, -4, 0, 4, 8, 11, 12, 14, 16, 18, 20, 22, 24, 26],
        [17.8, 20.0, 20.2, 20.0, 20.4, 21.1, 21.9, 23.0, 22.9, 23.3, 23.5, 23.1, 23.0, 25.15, 27.15, 28.3, 28.4,
         28.1, 27.4, 25.9])
UNDER = ([-44, -36, -30, -24, -18, -12, -8, -4, 0, 4, 8, 11, 12, 14, 16, 18, 20, 22, 24, 26],
         [17.3, 18.3, 17.7, 16.2, 15.1, 14.3, 12.6, 11.7, 11.1, 11.6, 12.8, 13.8, 14.3, 16.4, 18.5, 20.1, 21.0, 21.85,
          22.75, 24.4])
# The dark bands over the back, as pixel runs: (z where a band crosses the spine, its width in texels there, how far
# down the side it reaches in units). Short bands across the back break into spots down the flank.
STRIPES = [(16.6, 2, 2.0), (14.4, 2, 2.6), (11.0, 3, 3.6), (8.3, 3, 4.6), (5.6, 4, 5.2), (2.9, 4, 5.4),
           (0.2, 4, 5.4), (-2.5, 4, 5.2), (-5.2, 4, 4.8), (-7.9, 3, 4.2), (-10.6, 3, 3.6), (-13.4, 3, 3.2),
           (-16.4, 3, 2.8), (-19.6, 3, 2.4), (-22.8, 2, 2.1), (-26.1, 2, 1.8), (-29.5, 2, 1.5), (-33.0, 2, 1.3),
           (-36.6, 2, 1.1), (-40.2, 2, 0.8)]
RUNS = [stripe_runs(91 + k, w, 200) for k, (_, w, _) in enumerate(STRIPES)]
EYE = (21.98, 26.74)       # (z, y): the eye, where the socket and the stripe through it are painted


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
        elif part == 'neck' or (abs(n[2]) > 0.7 and p[..., 2].mean() > 9):
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
    row of lip scales along both jaws, rugged knobs over the skull, the nostrils at the tip."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    mottle = fbm(p, 2.0, seed=23, octaves=2) - 0.5
    col, row = face_grid(tx)
    top = np.interp(z, [17, 19, 21, 23, 24.5, 26.2], [28.4, 28.45, 28.1, 27.6, 27.3, 25.6])
    bottom = np.interp(z, [17, 20, 23, 25.5], [21.3, 21.2, 22.4, 23.0])
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
            nostril = (np.abs(z - 25.45) < 0.3) & (np.abs(y - 26.35) < 0.16)
            colour = np.where(nostril[..., None], shade(pal['stripe'], 0.1, tx.x, tx.y), colour)
        if jaw:
            k, _ = grid(tx, 2)
            lip = row <= 1
            light = (k // 2) % 2 == 1
            colour = np.where((lip & light)[..., None], shade(pal['belly'], 0.58, tx.x, tx.y), colour)
            colour = np.where((lip & ~light)[..., None], shade(pal['hide'], 0.44, tx.x, tx.y), colour)
            pale = (row > 1) & (y < 22.6 + 0.3 * (noise(p, 0.7, seed=27) - 0.5))
            colour = np.where(pale[..., None], shade(pal['belly'], 0.52 + 0.08 * mottle + 0.5 * scales(tx, 3, seed=26),
                                                     tx.x, tx.y, dither=0.0), colour)
    if n[1] > 0.5 and part in ('roof', 'brow', 'snout', 'cranium'):
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
    """The horns over the eyes: dark keratin, paler toward the tips, with grooves along them."""
    p, n = tx.points, tx.normal
    t = np.clip((np.abs(p[..., 0]) - 1.15) / 1.9, 0, 1)
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
    grow = np.interp(zc, [19.4, 20.5, 22.5, 24.5, 25.7], [0.6, 0.9, 1.0, 0.8, 0.6]) if along == 2 else 0.7
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
