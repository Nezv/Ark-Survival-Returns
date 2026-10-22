"""The Giganotosaurus, designed box by box on the ARK rig; built by scripts/handmade_models.py.

The biggest theropod in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a deep trunk
carried on heavy thighs, long shins and three-toed feet with a small dewclaw, a long tail that falls from the hips to
a thin point, and a thick neck that rises gently to a long, low skull. The skull is broad at the cheeks and narrows
over the eyes under a knobbed brow with a short horn before it; the snout is long and low with knobs along its top
edges. The jaw is designed shut (the idle holds it a little open), the teeth are cut-out rows along both jaws. A
single row of small spikes runs from the crown along the neck, the back and the tail.
"""
import math

import numpy as np

from handmade_models import cells, fbm, hexes, lift, noise, rotation, shade
from handmade_tyrannosaurus import face_grid, grid, midline_top, scales, stripe_runs

FOLDER, STEM, HEIGHT = 'Giganotosaur', 'giganotosaur', 15.0
DENSITY = 1.0            # texels per runtime unit, Minecraft's own density
ATLAS_WIDTH = 1024
PREVIEW_VARIANT = 'Natural'
ASSET = 'Giganotosaurus'
PREVIEW_CLIPS = ('Giganotosaurus-Idle', 'Giganotosaurus-Move-Fwd', 'Giganotosaurus-Roar', 'Giganotosaurus-Torpid-Idle')
JAW_POSE = ('Giganotosaurus-Idle', 0.0, ['jaw', 'tongue'])     # the frame the jaw is designed shut in
JAW_REFERENCE = {'c_jaw': 42.0}                                 # the angle at which the ARK mesh's jaw is shut
HEAD, JAW = 'c_head', 'c_jaw'

# The trunk, segment by segment: (bone, rear z, front z, belly, back, half width) from the ARK mesh's sections; the
# tiers across each follow the body's cross-section (TIER_SHAPE: share of the height, share of the half width).
TRUNK = [('c_back1', -8.6, -4.4, 13.0, 25.25, 4.5), ('c_back1', -4.7, -0.05, 12.4, 26.6, 4.65),
         ('c_back2', -0.35, 3.3, 12.5, 27.1, 4.85), ('c_back3', 3.0, 7.5, 14.0, 26.95, 5.0),
         ('c_back4', 7.2, 11.8, 15.4, 26.45, 4.5)]
TIER_SHAPE = [(0.0, 0.1, 0.42), (0.1, 0.28, 0.75), (0.28, 0.78, 1.0), (0.78, 0.93, 0.7), (0.93, 1.0, 0.36)]
# The tail, two segments per bone: (bone, rear z, front z, (keel, core, top) tiers at the segment's middle); each
# segment is pitched along the tail's fall so neighbours meet without a step.
TAIL_SEGMENTS = [
    ('c_tail1', -13.92, -8.3, [(13.42, 15.22, 1.78), (15.22, 20.64, 3.33), (20.64, 22.44, 1.68)]),
    ('c_tail1', -19.2, -13.72, [(12.47, 13.96, 1.52), (13.96, 18.44, 2.7), (18.44, 19.94, 1.24)]),
    ('c_tail2', -24.36, -19.0, [(11.43, 12.74, 1.21), (12.74, 16.68, 2.34), (16.68, 17.99, 1.04)]),
    ('c_tail2', -29.32, -24.16, [(10.95, 12.06, 1.11), (12.06, 15.41, 1.93), (15.41, 16.53, 0.9)]),
    ('c_tail3', -33.77, -29.12, [(10.73, 11.66, 1.02), (11.66, 14.45, 1.57), (14.45, 15.38, 0.75)]),
    ('c_tail3', -38.01, -33.56, [(10.48, 11.22, 0.83), (11.22, 13.46, 1.27), (13.46, 14.2, 0.67)]),
    ('c_tail4', -41.42, -37.81, [(10.13, 10.69, 0.59), (10.69, 12.39, 0.92), (12.39, 12.95, 0.55)]),
    ('c_tail4', -44.63, -41.22, [(9.83, 10.22, 0.46), (10.22, 11.4, 0.7), (11.4, 11.79, 0.43)]),
    ('c_tail5', -47.57, -44.43, [(9.4, 9.59, 0.36), (9.59, 10.17, 0.45), (10.17, 10.37, 0.33)]),
    ('c_tail5', -50.3, -47.37, [(8.75, 8.84, 0.16), (8.84, 9.09, 0.21), (9.09, 9.18, 0.14)]),
]
# The neck rises gently from the shoulders to the head: per bone (rear z, front z, half width), laid along the top
# line of its back and the bottom line of its throat, measured on the ARK mesh's side view.
NECK = [('c_neck1', 11.6, 14.4, 3.2), ('c_neck2', 14.2, 16.9, 2.95), ('c_neck3', 16.7, 19.6, 2.92)]
NECK_TOP = ([9.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 21.0],
            [26.4, 26.1, 25.95, 26.6, 27.5, 27.95, 28.15, 28.2])
NECK_THROAT = ([8.0, 10.0, 12.0, 13.0, 14.0, 16.0, 18.0, 19.0, 20.0, 21.0],
               [15.5, 16.2, 17.3, 18.3, 19.1, 20.0, 20.6, 20.6, 20.7, 20.8])


def neck_section(d, bone, z0, z1, half, mat='hide'):
    """Four slabs along the neck between z0 and z1: a narrow crest on the top line, the upper and lower cores that
    meet in the middle, and a narrow keel along the throat, each sloped like its line."""
    top = lambda z: float(np.interp(z, *NECK_TOP))
    low = lambda z: float(np.interp(z, *NECK_THROAT))
    zm = (z0 + z1) / 2
    slope_top = math.atan2(top(z1) - top(z0), z1 - z0)
    slope_low = math.atan2(low(z1) - low(z0), z1 - z0)
    depth = (top(zm) - low(zm)) * math.cos((slope_top + slope_low) / 2)
    core = depth * 0.5 + 0.7
    a, b = z0 - 0.35, z1 + 0.35                      # the cores reach past the bends, so no wedge opens between them
    d.slab(bone, (top(z0), z0), (top(z1), z1), half * 0.6, 1.0, mat, part='neck', tier=2)
    d.slab(bone, (top(a) - 0.9, a), (top(b) - 0.9, b), half, core, mat, part='neck', tier=1)
    d.slab(bone, (low(a) + 0.9, a + 0.04), (low(b) + 0.9, b - 0.04), half * 0.93, core, mat, top=False,
           part='neck', tier=-1)
    d.slab(bone, (low(z0), z0 + 0.2), (low(z1), z1 - 0.2), half * 0.62, 1.0, mat, top=False, part='neck', tier=-2)


def tiers(d, bone, z0, z1, rows, mat='hide', part='trunk', inset=0.03, pitch=0.0):
    """Stacked boxes across a segment, each overlapping the next a little; the tiers off the core are inset a
    hair at both ends, so no two neighbours share an end plane. A pitched segment turns about its middle."""
    core = max(range(len(rows)), key=lambda k: rows[k][2])
    zm = (z0 + z1) / 2
    stretch = 1 / math.cos(math.radians(pitch))
    for k, (y0, y1, half) in enumerate(rows):
        e = 0.0 if k == core else inset * (1 + abs(k - core)) + (0.015 if k > core else 0.0)
        lo = y0 - (0.04 if k > core else 0.0)
        hi = y1 + (0.04 if k < core else 0.0)
        length = (z1 - z0) * stretch - 2 * e
        d.add(bone, (0.0, (lo + hi) / 2, zm), (2 * half, hi - lo, length), rotation(pitch=pitch), mat, part=part,
              tier=k - core, rows=len(rows))


def tail_pitch(k):
    """The fall of the tail at segment k: the slope of its core's middle line between the neighbouring segments."""
    def mid(i):
        bone, z0, z1, rows = TAIL_SEGMENTS[i]
        return (z0 + z1) / 2, (rows[1][0] + rows[1][1]) / 2
    a, b = mid(max(k - 1, 0)), mid(min(k + 1, len(TAIL_SEGMENTS) - 1))
    return math.degrees(math.atan2(a[1] - b[1], a[0] - b[0]))


def bone_at(z):
    """The spine bone that carries the body at z."""
    for bone, start in (('c_head', 19.36), ('c_neck3', 16.74), ('c_neck2', 14.3), ('c_neck1', 11.68),
                        ('c_back4', 7.41), ('c_back3', 3.14), ('c_back2', -0.25), ('c_back1', -8.45),
                        ('c_tail1', -19.2), ('c_tail2', -29.32), ('c_tail3', -38.01), ('c_tail4', -44.63)):
        if z >= start:
            return bone
    return 'c_tail5'


def design(d):
    rig = d.rig
    P = rig.pivot
    # the jaw and the tongue are designed shut in the idle's frame: the idle holds the jaw a little open
    for bone in (JAW, 'c_tongue1', 'c_tongue2', 'c_tongue3', 'c_tongue4'):
        rig.design_as_in(bone, 'Giganotosaurus-Idle', 0.0, HEAD)

    # ---------------------------------------------------------------- trunk, tail and neck
    for bone, z0, z1, belly, back, half in TRUNK:
        rows = [(belly + (back - belly) * a, belly + (back - belly) * b, half * w) for a, b, w in TIER_SHAPE]
        tiers(d, bone, z0, z1, rows)
    for k, (bone, z0, z1, rows) in enumerate(TAIL_SEGMENTS):
        tiers(d, bone, z0, z1, rows, part='tail', inset=0.06, pitch=tail_pitch(k))
    for bone, z0, z1, half in NECK:
        neck_section(d, bone, z0, z1, half)

    # ---------------------------------------------------------------- head: long and low, broad at the cheeks
    d.span(HEAD, (-2.95, 23.75, 18.6), (2.95, 26.3, 21.0), 'head', part='cranium')
    d.span(HEAD, (-2.2, 26.2, 18.8), (2.2, 28.2, 20.95), 'head', part='roof')
    # the face round the eye: one flat side where the eye is set (build_creature_eyes footprint), a brow above it
    d.span(HEAD, (-2.8, 23.8, 20.9), (2.8, 26.3, 23.0), 'head', part='cheek')
    d.span(HEAD, (-1.9, 26.2, 20.85), (1.9, 27.75, 23.05), 'head', part='temple')
    d.span(HEAD, (-2.05, 27.7, 20.95), (2.05, 28.15, 22.9), 'head', part='brow')
    # the short horn before each eye
    d.span(HEAD, (1.05, 27.6, 22.95), (1.7, 28.5, 23.95), 'horn', part='horn', mirror=True)
    d.span(HEAD, (1.18, 28.45, 23.15), (1.55, 29.15, 23.7), 'horn', part='horn', mirror=True)
    # the long snout, stepping down to a blunt tip
    d.span(HEAD, (-2.2, 23.7, 22.95), (2.2, 26.0, 25.0), 'head', part='snout')
    d.span(HEAD, (-1.6, 25.9, 23.1), (1.6, 27.4, 24.95), 'head', part='snout_top')
    d.span(HEAD, (-1.52, 23.6, 24.9), (1.52, 25.8, 27.0), 'head', part='snout')
    d.span(HEAD, (-1.15, 25.7, 25.05), (1.15, 26.75, 26.95), 'head', part='snout_top')
    d.span(HEAD, (-1.3, 23.5, 26.9), (1.3, 26.0, 28.3), 'head', part='tip')
    d.span(HEAD, (-0.75, 23.75, 28.2), (0.75, 25.3, 28.9), 'head', part='tip')
    # knobs along the top edges of the snout
    for z in (23.3, 24.2, 25.1, 26.0, 26.9, 27.7):
        y = float(np.interp(z, [23.0, 24.95, 25.0, 26.95, 27.0, 28.3], [27.4, 27.4, 26.75, 26.75, 26.0, 26.0]))
        x = float(np.interp(z, [23.0, 25.0, 27.0, 28.3], [1.25, 1.0, 0.85, 0.8]))
        d.span(HEAD, (x - 0.24, y - 0.2, z - 0.3), (x + 0.24, y + 0.3, z + 0.3), 'head', part='knob', mirror=True)
    # the upper teeth: cut-out rows hanging from the lip, outside the narrower lower jaw, and across the front
    teeth = dict(open=('up', 'down', 'north', 'south'), cutout=True, keep=True, mirror=True)
    for z0, z1, x, top, low in ((19.5, 21.0, 2.6, 23.9, 22.9), (21.0, 23.0, 2.1, 23.92, 22.85),
                                (23.0, 25.0, 1.55, 23.85, 22.8), (25.0, 26.9, 1.22, 23.75, 22.85),
                                (26.9, 28.15, 1.12, 23.65, 22.95)):
        d.span(HEAD, (x, low, z0), (x + 0.12, top, z1), 'tooth', part='teeth', row='upper', **teeth)
    d.span(HEAD, (-0.7, 22.95, 28.33), (0.7, 23.8, 28.45), 'tooth', part='teeth', row='front',
           open=('up', 'down', 'east', 'west'), cutout=True, keep=True)

    d.span(JAW, (-2.5, 20.65, 18.7), (2.5, 23.85, 21.2), 'head', part='jaw')
    d.span(JAW, (-2.0, 21.0, 21.1), (2.0, 23.86, 23.2), 'head', part='jaw')
    d.span(JAW, (-1.45, 21.6, 23.1), (1.45, 23.78, 25.6), 'head', part='jaw')
    d.span(JAW, (-1.08, 22.2, 25.5), (1.08, 23.7, 28.35), 'head', part='chin')
    for z0, z1, x in ((19.5, 21.0, 2.25), (21.2, 23.0, 1.75), (23.2, 25.4, 1.2), (25.6, 28.0, 0.85)):
        d.span(JAW, (x, 23.3, z0), (x + 0.12, 24.3, z1), 'tooth', part='teeth', row='lower', **teeth)
    d.span('c_tongue2', (-0.9, 23.9, 20.0), (0.9, 24.12, 25.0), 'mouth', part='tongue')

    # ---------------------------------------------------------------- the spikes along the midline, crown to tail tip
    body = list(d.boxes)
    for z, h in SPIKES:
        y = midline_top(body, z)
        d.seg(bone_at(z), (0.0, y - 0.25, z + 0.18), (0.0, y + h, z - 0.22), 0.3 + 0.3 * h, 0.5 + 0.3 * h, 'scute',
              side=(1, 0, 0), part='spike', along=(0.0, 1.0))

    # ---------------------------------------------------------------- arms: three clawed fingers
    d.span('l_scapula', (2.7, 17.0, 8.3), (4.7, 22.0, 12.4), 'hide', mirror=True, part='shoulder')
    elbow, wrist = P('l_elbow'), P('l_wrist')
    d.seg('l_armTiwst', P('l_shoulder'), elbow, 1.9, 2.1, 'hide', ext=(0.4, 0.4), mirror=True, part='arm')
    d.seg('l_forearmTwist', elbow, wrist, 1.45, 1.55, 'hide', ext=(0.3, 0.25), mirror=True, part='forearm')
    d.seg('l_wrist', wrist, P('l_fingerB1'), 1.2, 1.6, 'hide', ext=(0.2, 0.15), mirror=True, part='hand')
    for finger, w in (('A', 0.6), ('B', 0.62), ('C', 0.52)):
        a, b, c = (P(f'l_finger{finger}{k}') for k in (1, 2, 3))
        tip = c + (c - b) * 1.8 + np.array([0.0, -0.15, 0.1])
        d.seg(f'l_finger{finger}1', a, b, w, w + 0.06, 'hide', ext=(0.18, 0.06), mirror=True, part='finger')
        d.seg(f'l_finger{finger}2', b, c, w * 0.9, w * 0.95, 'hide', ext=(0.06, 0.05), mirror=True, part='finger')
        d.seg(f'l_finger{finger}3', c, tip, w * 0.6, w * 0.72, 'claw', ext=(0.04, 0.0), mirror=True, part='claw',
              along=(0.0, 1.0))

    # ---------------------------------------------------------------- legs
    d.span('l_legTwist', (2.0, 15.0, -7.2), (6.25, 22.5, 2.3), 'hide', mirror=True, part='thigh')
    d.span('l_legTwist', (2.8, 22.4, -6.0), (5.6, 24.3, 1.0), 'hide', mirror=True, part='thigh')
    d.span('l_legTwist', (1.9, 12.6, -5.0), (6.1, 15.1, 1.4), 'hide', mirror=True, part='thigh')
    d.seg('l_knee', (3.75, 12.9, -0.6), (3.6, 9.0, -2.7), 3.95, 4.6, 'hide', mirror=True, part='shin')
    d.seg('l_knee', (3.6, 9.3, -2.55), (3.4, 5.6, -4.6), 3.1, 3.5, 'hide', ext=(0.0, 0.3), mirror=True, part='shin')
    d.seg('l_ankle', (3.4, 6.1, -5.0), (3.6, 1.6, -3.4), 2.9, 2.75, 'hide', mirror=True, part='meta')
    d.span('l_ankle1', (1.75, 0.0, -4.4), (5.5, 1.9, -1.9), 'hide', mirror=True, part='foot')
    toes = {'A': ([P('l_toeA1'), P('l_toeA2'), P('l_toeA3')], (0.8, 0.35, 2.55), 1.25),
            'B': ([P('l_toeB1'), P('l_toeB2'), P('l_toeB3')], (3.95, 0.35, 2.85), 1.35),
            'C': ([P('l_toeC1'), P('l_toeC2'), P('l_toeC3')], (7.25, 0.3, 1.45), 1.2),
            'D': ([P('l_toeD1'), P('l_toeD2'), P('l_toeD3')], (1.75, 1.45, -2.75), 0.6)}
    for name, (joints, tip, w) in toes.items():
        points = joints + [np.array(tip)]
        last = len(joints) - 1
        for k in range(len(joints)):
            a, b = points[k], points[k + 1]
            scale = 1 - 0.1 * k
            if k < last:
                d.seg(f'l_toe{name}{k + 1}', a, b, w * scale, w * scale, 'hide', ext=(0.2 if k == 0 else 0.06, 0.05),
                      mirror=True, part='toe')
            else:
                d.seg(f'l_toe{name}{k + 1}', a, b, w * scale * 0.62, w * scale * 0.68, 'claw', ext=(0.06, 0.0),
                      mirror=True, part='claw', along=(0.0, 1.0))


# The midline spikes: (z, height above the skin), from the crown to the tail tip; tallest over the neck.
SPIKES = [(22.37, 0.45), (21.37, 0.6), (20.37, 0.85), (19.27, 1.0), (18.27, 1.1), (17.27, 1.15), (16.27, 1.1),
          (15.27, 1.0), (14.27, 0.9), (13.27, 0.75), (12.27, 0.6), (11.07, 0.48)] + \
         [(z, 0.42 - 0.3 * (8.0 - z) / 56.0) for z in np.arange(9.6, -48.0, -1.35)]


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(hide=hexes('#1d1e1d', '#2a2b29', '#383936', '#474843', '#575750', '#68675f', '#79786e', '#8b897e'),
                    belly=hexes('#7f7a6c', '#8f8a7c', '#a09a8c', '#b0ab9c', '#bfbaac', '#cdc9bb'),
                    stripe=hexes('#0d0d0d', '#131313', '#1a1a19', '#22221f'),
                    scute=hexes('#141412', '#1e1e1b', '#292925', '#35352f', '#42413a'),
                    horn=hexes('#1c1a17', '#2a2722', '#39352e', '#49443b', '#5a5449'),
                    mouth=hexes('#3a1414', '#55201f', '#71302c', '#8c423b', '#a5574c', '#bb6e5e')),
}
PALETTES['slate'] = PALETTES['Natural']
PALETTES['umber'] = dict(hide=hexes('#21170f', '#302217', '#402d1f', '#513928', '#634632', '#75543c', '#886347', '#9b7253'),
                         belly=hexes('#7a6650', '#8b7861', '#9c8a72', '#ad9c84', '#bdae96', '#ccbfa8'),
                         stripe=hexes('#0e0a07', '#150f0a', '#1c140e', '#241a12'),
                         scute=hexes('#16100b', '#211811', '#2c2017', '#38291e', '#453326'),
                         horn=hexes('#1e1712', '#2c221a', '#3b2e23', '#4b3b2e', '#5c4a3a'),
                         mouth=PALETTES['Natural']['mouth'])
PALETTES['rust'] = dict(hide=hexes('#2b140d', '#3d1e14', '#50291b', '#643523', '#79422b', '#8e5034', '#a45f3e', '#ba6e49'),
                        belly=hexes('#8c6650', '#9d7861', '#ae8a73', '#bf9c85', '#cfae97', '#ddc0a9'),
                        stripe=hexes('#140806', '#1d0c09', '#26110c', '#30160f'),
                        scute=hexes('#1c0d09', '#28140e', '#341b13', '#422318', '#512b1e'),
                        horn=hexes('#2a1a14', '#3d261d', '#513327', '#664132', '#7c503d'),
                        mouth=PALETTES['Natural']['mouth'])
PALETTES['sand'] = dict(hide=hexes('#5e533f', '#6f644e', '#81755d', '#94876c', '#a69a7c', '#baac8c', '#cdbf9d', '#e1d3ae'),
                        belly=hexes('#c2b69c', '#ccc1a9', '#d6ccb7', '#e0d7c4', '#eae3d2', '#f4eee0'),
                        stripe=hexes('#4c4131', '#584c3c', '#645847', '#716452'),
                        scute=hexes('#3f3629', '#4e4434', '#5d5240', '#6d604c', '#7d6f59'),
                        horn=hexes('#3a3226', '#4a4031', '#5b4f3d', '#6d5f4a', '#7f7058'),
                        mouth=PALETTES['Natural']['mouth'])
PALETTES['Natural'] = PALETTES['slate'] = lift(PALETTES['Natural'], 1.1, 4)
TOOTH_RAMP = hexes('#8a7f66', '#ab9f83', '#c9bf9f', '#e0d8bc', '#efe9d5', '#faf7ec')
CLAW_RAMP = hexes('#100e0b', '#1d1914', '#2b251e', '#3b3329', '#4e4436', '#655845')
TONGUE_RAMP = hexes('#5e2526', '#7a3333', '#954442', '#ad5853', '#c26e66')

# The body's outline along its length (z), from the tail tip to the snout: the top of the back and the underside.
BACK = ([-50, -44, -38, -32, -26, -20, -14, -10, -6, -2, 2, 6, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 29],
        [8.8, 11.6, 13.7, 15.4, 17.0, 18.8, 21.1, 22.9, 25.1, 26.7, 27.3, 26.9, 26.1, 25.95, 26.6, 27.5, 27.95,
         28.2, 28.15, 27.4, 26.75, 26.0, 25.3])
UNDER = ([-50, -44, -38, -32, -26, -20, -14, -10, -6, -2, 2, 6, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 29],
         [8.4, 9.5, 10.2, 10.6, 10.8, 11.5, 12.75, 13.4, 13.0, 12.4, 12.7, 14.7, 16.2, 17.3, 19.1, 20.0, 20.6, 20.7,
          21.1, 21.7, 22.2, 22.9, 23.5])
# The dark bands over the back, as pixel runs: (z where a band crosses the spine, its width in texels there, how far
# down the side it reaches in units). Broad saddles over the back, narrowing to rings round the tail.
STRIPES = [(18.4, 4, 2.6), (15.6, 5, 3.4), (12.6, 6, 4.4), (9.4, 7, 5.6), (6.0, 7, 6.6), (2.6, 8, 7.0),
           (-0.8, 8, 7.0), (-4.2, 7, 6.6), (-7.6, 7, 6.0), (-11.0, 6, 5.2), (-14.4, 6, 4.6), (-17.9, 5, 4.0),
           (-21.4, 5, 3.6), (-25.0, 5, 3.2), (-28.6, 4, 2.8), (-32.2, 4, 2.4), (-35.8, 4, 2.1), (-39.4, 3, 1.8),
           (-43.0, 3, 1.4), (-46.6, 3, 1.0)]
RUNS = [stripe_runs(111 + k, w, 200) for k, (_, w, _) in enumerate(STRIPES)]
EYE = (21.91, 27.02)       # (z, y): the eye, where the socket and the stripe through it are painted


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
    row of lip scales along both jaws, rugged knobs over the snout and brow, the nostrils near the tip."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    mottle = fbm(p, 2.0, seed=23, octaves=2) - 0.5
    col, row = face_grid(tx)
    top = np.interp(z, [19, 21, 23, 25, 27, 28.9], [28.2, 28.15, 27.4, 26.75, 26.0, 25.0])
    bottom = np.interp(z, [19, 22, 25, 28], [20.6, 21.1, 21.9, 22.9])
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
            nostril = (np.abs(z - 28.0) < 0.3) & (np.abs(y - 25.35) < 0.13)
            colour = np.where(nostril[..., None], shade(pal['stripe'], 0.1, tx.x, tx.y), colour)
        if jaw:
            k, _ = grid(tx, 2)
            lip = row <= 1
            light = (k // 2) % 2 == 1
            colour = np.where((lip & light)[..., None], shade(pal['belly'], 0.58, tx.x, tx.y), colour)
            colour = np.where((lip & ~light)[..., None], shade(pal['hide'], 0.44, tx.x, tx.y), colour)
            pale = (row > 1) & (y < 22.9 + 0.3 * (noise(p, 0.7, seed=27) - 0.5))
            colour = np.where(pale[..., None], shade(pal['belly'], 0.52 + 0.08 * mottle + 0.5 * scales(tx, 3, seed=26),
                                                     tx.x, tx.y, dither=0.0), colour)
    if n[1] > 0.5 and part in ('knob', 'snout_top', 'roof', 'brow'):
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
    """The horns before the eyes: dark keratin, paler to the tips, with grooves up them."""
    p, n = tx.points, tx.normal
    t = np.clip((p[..., 1] - 27.6) / 1.5, 0, 1)
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
    grow = np.interp(zc, [19.5, 21.0, 23.5, 26.0, 28.2], [0.6, 0.9, 1.0, 0.8, 0.6]) if along == 2 else 0.7
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
