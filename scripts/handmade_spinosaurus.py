"""The Spinosaurus, designed box by box on the ARK rig (Spino); built by scripts/handmade_models.py.

A long river hunter in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x), standing on
all fours as the ARK idle holds it: a deep narrow trunk on heavy thighs and four-toed feet, strong arms that reach
the ground on three clawed fingers with a hooked thumb claw, and a long tail tapering to a fan of spikes. The neck
rises gently from the chest to a crocodile's head: a broad skull with small horns over the eyes, a long narrow snout
ending in the rosette, the lower jaw designed shut as the idle holds it and cut-out rows of teeth along both jaws.
The sail stands over the back on a solid base: a membrane cut out of cards, one per back bone, its edge scalloped
between the spines that poke through it, and it runs on down the tail as a low ridge. Spikes line the top of the
neck and the back of the skull.
"""
import math

import numpy as np

from handmade_models import cells, fbm, hexes, lift, noise, shade
from handmade_tyrannosaurus import face_grid, grid, scales, stripe_runs

FOLDER, STEM, HEIGHT = 'Spinosaurus', 'spinosaurus', 13.5
DENSITY = 1.0            # texels per runtime unit, Minecraft's own density
ATLAS_WIDTH = 1024
PREVIEW_VARIANT = 'Natural'
ASSET = 'Spino'
PREVIEW_CLIPS = ('Spino-Idle', 'Spino-Move-Fwd', 'Spino-Roar', 'Spino-Torpid-Idle')
JAW_POSE = ('Spino-Idle', 0.0, ['jaw', 'tongue'])     # the frame the jaw is designed shut in
HEAD, JAW = 'c_head', 'c_jaw'

# The trunk, segment by segment: (bone, rear z, front z, belly, back, half width), measured on the ARK mesh's
# cross-sections without the sail and the limbs; oval() stacks the tiers.
TRUNK = [('c_back1', -13.4, -9.0, 12.0, 24.2, 5.2), ('c_back1', -9.2, -4.4, 11.1, 24.5, 5.75),
         ('c_back1', -4.6, 0.85, 10.6, 24.7, 5.8), ('c_back2', 0.65, 6.85, 11.1, 24.8, 6.45),
         ('c_back3', 6.7, 12.9, 10.9, 24.5, 6.85), ('c_back4', 12.75, 16.2, 11.5, 23.9, 6.15),
         ('c_back4', 16.0, 18.9, 12.6, 23.7, 4.85)]
# The tiers of a cross-section from the belly up: (bottom, top) as shares of its height, the half width as a
# share of the widest; after the ARK mesh's sections over the ribs.
OVAL = [(0.0, 0.1, 0.6), (0.09, 0.25, 0.85), (0.24, 0.75, 1.0), (0.74, 0.9, 0.86), (0.89, 1.0, 0.6)]
# The tail's outline along z: (z, underside, top, half width), from the tip to the hips.
TAIL = [(-62.6, 17.95, 18.35, 0.2), (-58.0, 16.9, 18.0, 0.62), (-54.0, 15.9, 17.75, 0.95), (-50.0, 15.3, 17.6, 1.36),
        (-46.0, 14.6, 17.45, 1.74), (-42.0, 13.75, 17.4, 2.14), (-38.0, 13.0, 17.5, 2.57), (-34.0, 12.4, 17.9, 2.96),
        (-30.0, 12.15, 18.9, 3.26), (-26.0, 12.15, 20.2, 3.68), (-22.0, 12.4, 21.4, 4.31), (-18.0, 12.6, 22.6, 4.69),
        (-14.0, 12.4, 23.6, 4.9), (-12.0, 12.2, 24.0, 5.05)]
# Per tail bone: (bone, rear z, front z, pieces it is cut into).
TAIL_BONES = [('c_tail5', -62.6, -52.69, 3), ('c_tail4', -52.69, -44.69, 3), ('c_tail3', -44.69, -35.51, 3),
              ('c_tail2', -35.51, -25.01, 3), ('c_tail1', -25.01, -13.17, 3)]
# The neck between the chest and the skull: its top line and its throat along z, and the half width.
NECK_TOP = ([16.0, 18.0, 20.0, 22.0, 24.0, 26.0, 28.0, 30.0, 31.5],
            [23.7, 23.85, 24.1, 24.65, 25.4, 26.5, 27.7, 29.0, 30.0])
NECK_LOW = ([16.0, 18.0, 20.0, 22.0, 24.0, 26.0, 28.0, 30.0, 31.5],
            [12.8, 13.0, 13.6, 14.8, 16.3, 18.2, 20.2, 21.6, 22.5])
NECK_HALF = ([16.0, 20.0, 24.0, 28.0, 31.0], [4.6, 3.9, 3.35, 2.85, 2.55])
NECK = [('c_back4', 16.6, 18.9), ('c_neck1', 18.6, 21.3), ('c_neck1', 21.1, 23.85), ('c_neck2', 23.6, 25.8),
        ('c_neck2', 25.6, 27.85), ('c_neck3', 27.6, 30.6)]
# The sail's membrane edge along z (y of its top between the spines), and the spines poking through it.
SAIL = ([-14.0, -12.0, -10.0, -8.0, -6.0, -4.0, -2.0, 0.0, 2.0, 4.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 17.5, 18.6],
        [25.0, 27.0, 31.3, 35.0, 38.4, 40.5, 41.3, 41.9, 42.0, 41.8, 40.8, 39.8, 38.4, 36.3, 34.1, 30.8, 27.2, 24.6])
SPINES = [-12.6, -10.6, -8.6, -6.6, -4.6, -2.5, -0.4, 1.7, 3.8, 5.9, 8.0, 10.1, 12.2, 14.2, 16.1]
# The top of the body under the sail and the ridge, from the tail tip to the neck.
BACK = ([-62.6, -54.0, -46.0, -38.0, -30.0, -22.0, -18.0, -14.0, -10.0, -6.0, 0.0, 6.0, 12.0, 16.0, 18.0, 20.0],
        [18.35, 17.75, 17.45, 17.5, 18.9, 21.4, 22.6, 23.6, 24.4, 24.6, 24.75, 24.8, 24.55, 23.9, 23.85, 24.1])
UNDER = ([-62.6, -54.0, -46.0, -38.0, -30.0, -22.0, -14.0, -10.0, -6.0, 0.0, 6.0, 10.0, 14.0, 18.0, 20.0],
         [17.95, 15.9, 14.6, 13.0, 12.15, 12.4, 12.4, 11.6, 10.9, 10.6, 11.0, 10.9, 11.5, 12.9, 13.6])


def back_y(z):
    return float(np.interp(z, *BACK))


def tail_at(z):
    """(underside, top, half width) of the tail at z."""
    zs = [t[0] for t in TAIL]
    return tuple(float(np.interp(z, zs, [t[k] for t in TAIL])) for k in (1, 2, 3))


def oval(d, bone, z0, z1, bottom, top, half, inset=0.05, part='trunk'):
    """A rounded trunk segment: the OVAL tiers stacked from the belly to the back, each a hair longer or shorter
    than its neighbours, so no two share an end plane."""
    height = top - bottom
    core = 2
    for k, (a, b, w) in enumerate(OVAL):
        e = 0.0 if k == core else inset * (1 + abs(k - core))
        d.span(bone, (-half * w, bottom + a * height, z0 + e), (half * w, bottom + b * height, z1 - e), 'hide',
               part=part, tier=k - core)


def neck_section(d, bone, z0, z1, k):
    """Four slabs along the neck between z0 and z1, each sloped like its line: a narrow crest on the top line, the
    upper and lower cores meeting in the middle, and a keel along the throat."""
    top = lambda z: float(np.interp(z, *NECK_TOP))
    low = lambda z: float(np.interp(z, *NECK_LOW))
    zm = (z0 + z1) / 2
    half = float(np.interp(zm, *NECK_HALF))
    slope_top = math.atan2(top(z1) - top(z0), z1 - z0)
    slope_low = math.atan2(low(z1) - low(z0), z1 - z0)
    depth = (top(zm) - low(zm)) * math.cos((slope_top + slope_low) / 2)
    core = depth * 0.5 + 1.1
    a, b = z0 - 0.5, z1 + 0.5
    j = 0.04 * (k % 2)                                   # neighbours a hair apart in width, so no shared planes
    d.slab(bone, (top(z0), z0), (top(z1), z1), half * (0.6 + j), 1.6, 'hide', part='neck', tier=2)
    d.slab(bone, (top(a) - 1.4, a), (top(b) - 1.4, b), half * (1.0 - j / 2), core, 'hide', part='neck', tier=1)
    d.slab(bone, (low(a) + 1.3, a + 0.05), (low(b) + 1.3, b - 0.05), half * (0.94 - j / 2), core, 'hide', top=False,
           part='neck', tier=-1)
    d.slab(bone, (low(z0), z0 + 0.3), (low(z1), z1 - 0.3), half * (0.62 + j), 1.5, 'hide', top=False, part='neck',
           tier=-2)


def card(d, bone, lo, hi, blades, x=0.0, half=0.08, yaw=0.0, roll=0.0, mat='spike', **tag):
    """A thin cut-out card standing in the yz plane between (y, z) corners lo and hi, centred on x: its blades
    ((root y, z), (tip y, z), width at the root) are all that shows of it. Yaw and roll turn it about its centre;
    the blades are given as if it were not turned."""
    (y0, z0), (y1, z1) = lo, hi
    centre = (x, (y0 + y1) / 2, (z0 + z1) / 2)
    return d.turned(bone, centre, (2 * half, abs(y1 - y0), abs(z1 - z0)), yaw=yaw, roll=roll, mat=mat,
                    open=('up', 'down', 'north', 'south'), cutout=True, keep=True, blades=blades, **tag)


def design(d):
    rig = d.rig
    P = rig.pivot
    # the jaw and the tongue are designed shut, as the idle holds them; the bind pose roars
    for bone in (JAW, 'c_tongue1', 'c_tongue2', 'c_tongue3', 'c_tongue4'):
        rig.design_as_in(bone, 'Spino-Idle', 0.0, HEAD)

    # ---------------------------------------------------------------- trunk and tail
    for k, (bone, z0, z1, bottom, top, half) in enumerate(TRUNK):
        oval(d, bone, z0, z1, bottom, top, half, inset=0.05 + 0.012 * k)
    # the tail: per piece a core and a taller, narrower keel, both laid along its centre line
    for k, (bone, z0, z1, pieces) in enumerate(TAIL_BONES):
        cuts = np.linspace(z0, z1, pieces + 1)
        for j in range(pieces):
            a_z, b_z = cuts[j], cuts[j + 1]
            lo0, hi0, w0 = tail_at(a_z)
            lo1, hi1, w1 = tail_at(b_z)
            a = np.array([0.0, (lo0 + hi0) / 2, a_z])
            b = np.array([0.0, (lo1 + hi1) / 2, b_z])
            depth, half = ((hi0 - lo0) + (hi1 - lo1)) / 2, (w0 + w1) / 2
            n = k * pieces + j
            last = k == len(TAIL_BONES) - 1 and j == pieces - 1
            d.seg(bone, a, b, 2 * half, depth * (0.62 if n % 2 else 0.68), 'hide', ext=(0.3, 0.3 if not last else 0.5),
                  side=(1, 0, 0), part='tail', tier=0)
            if half > 0.5:              # the keels stop a hair short of each other, the cores reach past the joints
                d.seg(bone, a, b, 2 * half * (0.48 if n % 2 else 0.72), depth * (1.0 if n % 2 else 0.96), 'hide',
                      ext=(-0.03, -0.03), side=(1, 0, 0), part='tail', tier=1)

    # ---------------------------------------------------------------- neck
    for k, (bone, z0, z1) in enumerate(NECK):
        neck_section(d, bone, z0, z1, k)

    # ---------------------------------------------------------------- head and the shut jaw
    d.span(HEAD, (-2.5, 25.8, 28.8), (2.5, 30.3, 32.6), 'head', part='cranium')
    d.span(HEAD, (-1.9, 30.2, 29.2), (1.9, 30.9, 32.4), 'head', part='crown')
    d.span(HEAD, (-2.2, 26.15, 32.5), (2.2, 29.9, 36.4), 'head', part='temple')
    d.span(HEAD, (-1.75, 29.8, 32.7), (1.75, 30.45, 36.2), 'head', part='brow')
    d.span(HEAD, (2.0, 30.25, 34.9), (2.7, 31.2, 36.2), 'head', part='horn', mirror=True)
    d.span(HEAD, (-1.32, 26.1, 36.3), (1.32, 28.95, 38.4), 'head', part='snout')
    d.span(HEAD, (-1.1, 25.95, 38.3), (1.1, 28.25, 41.3), 'head', part='snout')
    d.span(HEAD, (-0.55, 28.15, 36.5), (0.55, 28.65, 40.6), 'head', part='ridge')
    d.span(HEAD, (-1.38, 25.3, 41.2), (1.38, 27.8, 43.9), 'head', part='rosette')
    d.span(HEAD, (-1.15, 25.0, 43.8), (1.15, 27.3, 44.65), 'head', part='tip')
    teeth = dict(open=('up', 'down', 'north', 'south'), cutout=True, keep=True, mirror=True)
    d.span(HEAD, (1.25, 25.25, 33.4), (1.29, 26.2, 38.3), 'tooth', part='teeth', row='upper', **teeth)
    d.span(HEAD, (1.04, 25.2, 38.3), (1.08, 25.97, 41.2), 'tooth', part='teeth', row='upper', **teeth)
    d.span(HEAD, (1.24, 24.3, 41.3), (1.28, 25.35, 43.7), 'tooth', part='teeth', row='rosette', **teeth)
    d.span(JAW, (-2.3, 22.9, 29.0), (2.3, 26.3, 33.2), 'head', part='jaw')
    d.span(JAW, (-1.2, 23.55, 33.0), (1.2, 26.12, 37.2), 'head', part='jaw')
    d.span(JAW, (-1.0, 23.85, 37.0), (1.0, 25.98, 41.25), 'head', part='jaw')
    d.span(JAW, (-1.17, 24.15, 41.1), (1.17, 25.65, 44.2), 'head', part='chin')
    d.span(JAW, (1.4, 25.55, 41.5), (1.44, 26.6, 43.7), 'tooth', part='teeth', row='lower', **teeth)
    d.span('c_tongue2', (-0.6, 25.4, 34.0), (0.6, 25.7, 39.5), 'mouth', part='tongue')

    # ---------------------------------------------------------------- the sail, on a solid base, and the ridges
    for k, (bone, z0, z1) in enumerate((('c_back1', -13.4, 0.85), ('c_back2', 0.65, 6.85), ('c_back3', 6.7, 12.9),
                                        ('c_back4', 12.75, 18.7))):
        base = min(back_y(z0), back_y(z1)) - 0.6
        top = max(back_y(z0), back_y(z1)) + 2.4
        d.span(bone, (-0.48 - 0.04 * (k % 2), base, z0), (0.48 + 0.04 * (k % 2), top + 0.15 * (k % 2), z1), 'sail',
               part='base')
        tips = [float(np.interp(z, *SAIL)) + 2.2 for z in SPINES if z0 - 1.0 <= z <= z1 + 1.0]
        d.span(bone, (-0.16 - 0.03 * (k % 2), base, z0), (0.16 + 0.03 * (k % 2), max(tips) + 0.1, z1), 'sail',
               part='membrane', open=('up', 'down', 'north', 'south'), cutout=True, keep=True)
    # the low ridge of spikes along the tail, and the spikes along the neck and the back of the skull
    for k, (bone, z0, z1) in enumerate((('c_tail3', -42.0, -35.51), ('c_tail2', -35.51, -25.01),
                                        ('c_tail1', -25.01, -13.17))):
        card(d, bone, (min(back_y(z0), back_y(z1)) - 0.6, z0), (max(back_y(z0), back_y(z1)) + 1.6, z1),
             spikes(z0, z1, 1.6, 1.25), half=0.2 + 0.05 * (k % 2), part='ridge')
    card(d, 'c_neck2', (24.0, 19.7), (30.2, 27.85), NECK_SPIKES[:4], half=0.22, part='ridge')
    card(d, 'c_neck3', (26.8, 25.2), (34.2, 31.6), NECK_SPIKES[4:], half=0.27, part='ridge')
    # the fan of spikes at the tail tip: a card standing up and one lying flat across the tail
    card(d, 'c_tail5', (16.8, -62.8), (22.2, -52.6), TAIL_SPIKES_UP, half=0.12, part='fan')
    card(d, 'c_tail5', (14.1, -62.8), (20.9, -51.6), TAIL_SPIKES_FLAT, half=0.12, roll=90.0, part='fan')

    # ---------------------------------------------------------------- arms: the animal stands on them
    shoulder, elbow, wrist = P('l_shoulder'), P('l_elbow'), P('l_wrist')
    d.span('l_scapula', (3.5, 15.6, 12.4), (6.2, 21.2, 17.4), 'hide', mirror=True, part='shoulder')
    d.seg('l_shoulder', shoulder + (0.0, 0.8, 0.0), elbow, 2.6, 3.1, 'hide', ext=(0.0, 0.6), mirror=True, part='arm')
    d.seg('l_elbow', elbow, wrist, 2.1, 2.4, 'hide', ext=(0.5, 0.4), mirror=True, part='forearm')
    d.seg('l_wrist', wrist, P('l_fingerB1'), 2.8, 1.7, 'hide', ext=(0.4, 0.3), mirror=True, part='hand')
    for finger, w in (('A', 0.9), ('B', 1.0), ('C', 0.9)):
        a, b = P(f'l_finger{finger}1'), P(f'l_finger{finger}2')
        tip = b + np.array([0.0, -b[1] + 0.15, 1.6])
        d.seg(f'l_finger{finger}1', a, b, w, w, 'hide', ext=(0.3, 0.1), side=(1, 0, 0), mirror=True, part='finger')
        d.seg(f'l_finger{finger}2', b, tip, w * 0.7, w * 0.8, 'claw', ext=(0.1, 0.0), side=(1, 0, 0), mirror=True,
              part='claw', along=(0.1, 1.0))
    # the thumb's hooked claw, turned in under the hand
    a, b, c = P('l_fingerD1'), P('l_fingerD2'), P('l_fingerD3')
    d.seg('l_fingerD1', a, b, 0.9, 0.95, 'hide', ext=(0.3, 0.1), side=(0, 1, 0), mirror=True, part='finger')
    d.seg('l_fingerD2', b, c + (c - b) * 0.35, 0.65, 0.8, 'claw', ext=(0.1, 0.0), side=(0, 1, 0), mirror=True,
          part='claw', along=(0.0, 1.0))

    # ---------------------------------------------------------------- legs
    knee, ankle = P('l_knee'), P('l_ankle')
    d.span('l_leg', (3.4, 13.4, -9.6), (7.5, 22.4, -0.6), 'hide', mirror=True, part='thigh')
    d.span('l_leg', (3.9, 15.0, -11.2), (7.0, 21.6, -9.5), 'hide', mirror=True, part='thigh')
    d.span('l_leg', (3.9, 11.4, -4.6), (7.0, 13.5, 0.4), 'hide', mirror=True, part='thigh')
    d.span('l_leg', (4.0, 14.4, -0.7), (6.9, 21.2, 0.6), 'hide', mirror=True, part='thigh')
    mid = knee + (ankle - knee) * 0.45
    d.seg('l_knee', knee, mid, 2.9, 3.6, 'hide', ext=(0.8, 0.1), mirror=True, part='shin')
    d.seg('l_knee', mid, ankle, 2.3, 2.7, 'hide', ext=(0.1, 0.35), mirror=True, part='shin')
    d.seg('l_ankle', ankle, P('l_toeB1') + (-0.4, 0.0, 0.0), 2.2, 2.3, 'hide', ext=(0.4, 0.3), mirror=True,
          part='meta')
    for toe, w in (('A', 1.1), ('B', 1.25), ('C', 1.1)):
        a, b, c = (P(f'l_toe{toe}{k}') for k in (1, 2, 3))
        tip = c + (c - b) * 0.45 + np.array([0.0, -0.75, 0.0])
        d.seg(f'l_toe{toe}1', a, b, w, 1.2, 'hide', ext=(0.4, 0.15), side=(1, 0, 0), mirror=True, part='toe')
        d.seg(f'l_toe{toe}2', b, c, w * 0.9, 1.05, 'hide', ext=(0.15, 0.1), side=(1, 0, 0), mirror=True, part='toe')
        d.seg(f'l_toe{toe}3', c, tip, w * 0.62, 0.8, 'claw', ext=(0.08, 0.0), side=(1, 0, 0), mirror=True,
              part='claw', along=(0.1, 1.0))
    a, b = P('l_toeD1'), P('l_toeD2')
    d.seg('l_toeD1', a, b, 0.7, 0.75, 'hide', ext=(0.2, 0.05), side=(1, 0, 0), mirror=True, part='toe')
    d.seg('l_toeD2', b, b + (b - a) * 0.7, 0.45, 0.55, 'claw', ext=(0.05, 0.0), side=(1, 0, 0), mirror=True,
          part='claw', along=(0.2, 1.0))


def spikes(z0, z1, step, height):
    """Spikes leaning back along the top of the tail between z0 and z1, every `step` units, shrinking toward
    the tip."""
    out = []
    for z in np.arange(math.ceil(z0 / step) * step, z1 - 0.3, step):
        h = height * float(np.interp(z, [-42.0, -30.0, -13.0], [0.45, 0.8, 1.0]))
        y = back_y(z) - 0.15
        out.append(((y, z + 0.45), (y + h, z - 0.55), 0.85))
    return out


# The spikes along the top of the neck and the back of the skull: ((root y, z), (tip y, z), width at the root).
NECK_SPIKES = [((24.6, 22.2), (26.8, 20.9), 1.03), ((25.1, 23.8), (27.8, 22.3), 1.15), ((25.7, 25.2), (28.8, 23.5), 1.15),
               ((26.4, 26.6), (29.6, 24.8), 1.21), ((27.2, 27.9), (30.8, 26.2), 1.21), ((28.1, 29.1), (32.0, 27.4), 1.26),
               ((29.0, 30.2), (33.1, 28.8), 1.26), ((29.9, 31.2), (34.0, 30.4), 1.15)]
TAIL_SPIKES_UP = [((17.7, -53.4), (21.9, -55.6), 0.75), ((17.8, -55.4), (21.6, -58.2), 0.75),
                  ((17.9, -57.4), (20.6, -60.6), 0.7), ((18.0, -59.4), (19.4, -62.6), 0.6)]
# the flat fan: y here runs across the tail (17.5 is its middle), turned to lie along x
TAIL_SPIKES_FLAT = [((18.6, -52.2), (20.6, -54.6), 0.75), ((16.4, -52.2), (14.4, -54.6), 0.75),
                    ((18.5, -54.5), (20.4, -57.3), 0.75), ((16.5, -54.5), (14.6, -57.3), 0.75),
                    ((18.3, -56.8), (19.8, -60.0), 0.7), ((16.7, -56.8), (15.2, -60.0), 0.7),
                    ((18.0, -59.0), (18.7, -62.6), 0.6), ((17.0, -59.0), (16.3, -62.6), 0.6)]


def sail_alpha(tx):
    """The membrane: up to its scalloped edge between the spines, and the spines poking through above it."""
    p = tx.points
    y, z = p[..., 1], p[..., 2]
    edge = np.interp(z, *SAIL)
    zs = np.array(SPINES)
    k = np.clip(np.searchsorted(zs, z), 1, len(zs) - 1)
    t = np.clip((z - zs[k - 1]) / (zs[k] - zs[k - 1]), 0, 1)
    edge = edge - 0.9 * np.sin(math.pi * t) ** 0.7                 # the membrane sags between the spines
    near = np.minimum(np.abs(z - zs[k - 1]), np.abs(z - zs[k]))
    nearest = np.where(np.abs(z - zs[k - 1]) < np.abs(z - zs[k]), zs[k - 1], zs[k])
    tip = np.interp(nearest, *SAIL) + 2.2
    spine = near < 0.5 * np.clip((tip - y) / 2.4, 0.2, 1.0) + 0.15
    return (y <= edge) | (spine & (y <= tip))


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(hide=hexes('#262a20', '#33382a', '#414735', '#505741', '#61684d', '#727a5a', '#848c68', '#979f77'),
                    belly=hexes('#7a7a62', '#8b8b72', '#9c9c83', '#adad94', '#bebea5', '#cdcdb6'),
                    stripe=hexes('#121510', '#1a1e16', '#22271d', '#2b3125'),
                    sail=hexes('#4a1a12', '#661f14', '#842a19', '#a0391f', '#b84c28', '#cc6434', '#dc7f45'),
                    spike=hexes('#1a1c16', '#262920', '#33372b', '#424637', '#525745')),
    'olive': dict(hide=hexes('#262a20', '#33382a', '#414735', '#505741', '#61684d', '#727a5a', '#848c68', '#979f77'),
                  belly=hexes('#7a7a62', '#8b8b72', '#9c9c83', '#adad94', '#bebea5', '#cdcdb6'),
                  stripe=hexes('#121510', '#1a1e16', '#22271d', '#2b3125'),
                  sail=hexes('#4a1a12', '#661f14', '#842a19', '#a0391f', '#b84c28', '#cc6434', '#dc7f45'),
                  spike=hexes('#1a1c16', '#262920', '#33372b', '#424637', '#525745')),
    'umber': dict(hide=hexes('#271d15', '#352820', '#44342a', '#544135', '#654f40', '#775e4c', '#896d59', '#9b7d66'),
                  belly=hexes('#857563', '#968674', '#a79785', '#b8a896', '#c8b9a7', '#d7c9b8'),
                  stripe=hexes('#120d0a', '#1a130f', '#221913', '#2b2018'),
                  sail=hexes('#3e2a0e', '#563a12', '#6e4b17', '#875d1d', '#a07024', '#b8852e', '#cc9a3c'),
                  spike=hexes('#19130e', '#251d16', '#31271d', '#3e3126', '#4c3c2e')),
    'slate': dict(hide=hexes('#1c2125', '#262c31', '#31383e', '#3d454c', '#4a535b', '#58626b', '#67717b', '#77808b'),
                  belly=hexes('#6e757b', '#7f868c', '#90979d', '#a1a8ad', '#b2b9bd', '#c3c9cc'),
                  stripe=hexes('#0b0d0f', '#111316', '#171a1d', '#1e2125'),
                  sail=hexes('#16283a', '#1c3550', '#244466', '#2e557c', '#3b6891', '#4c7ea5', '#6094b8'),
                  spike=hexes('#0f1113', '#181b1e', '#22262a', '#2d3237', '#393f45')),
    'sand': dict(hide=hexes('#5a503d', '#6b604b', '#7d715a', '#8f8269', '#a19478', '#b4a689', '#c6b89a', '#d8caac'),
                 belly=hexes('#c0b498', '#cabfa6', '#d4cab4', '#ded5c2', '#e8e0d0', '#f1eade'),
                 stripe=hexes('#463c2d', '#524737', '#5e5241', '#6b5e4c'),
                 sail=hexes('#5a2a18', '#73351d', '#8c4223', '#a5512b', '#bc6234', '#d0763f', '#e08c4d'),
                 spike=hexes('#3f3629', '#4e4434', '#5d5240', '#6d604c', '#7d6f59')),
}
PALETTES['slate'] = lift(PALETTES['slate'], 1.08, 3)
TOOTH_RAMP = hexes('#8a7f66', '#ab9f83', '#c9bf9f', '#e0d8bc', '#efe9d5', '#faf7ec')
CLAW_RAMP = hexes('#100e0b', '#1d1914', '#2b251e', '#3b3329', '#4e4436', '#655845')
MOUTH_RAMP = hexes('#3a1414', '#55201f', '#71302c', '#8c423b', '#a5574c', '#bb6e5e')

# Dark bands over the back as pixel runs (see handmade_tyrannosaurus.stripe_runs): (z where a band crosses the
# spine, its width in texels there, how far down the side it reaches in units). Narrow bands down the flanks, rings
# round the tail.
STRIPES = [(15.5, 3, 4.5), (12.0, 3, 6.0), (8.4, 4, 7.0), (4.8, 4, 7.4), (1.2, 4, 7.4), (-2.4, 4, 7.0), (-6.0, 4, 6.6),
           (-9.6, 4, 6.0), (-13.4, 4, 5.4), (-17.4, 4, 5.0), (-21.6, 4, 4.4), (-26.0, 4, 3.8), (-30.4, 3, 3.2),
           (-34.8, 3, 2.8), (-39.2, 3, 2.4), (-43.6, 3, 2.1), (-48.0, 3, 1.8), (-52.4, 2, 1.5), (-56.8, 2, 1.2),
           (-60.6, 2, 0.8)]
RUNS = [stripe_runs(71 + k, w, 120) for k, (_, w, _) in enumerate(STRIPES)]
EYE = (35.46, 29.53)       # (z, y): the eye


def under_y(z):
    return np.interp(z, *UNDER)


def countershade(tx):
    """0 on the underside to 1 along the spine, from the body's outline at the texel's z."""
    p = tx.points
    y, z = p[..., 1], p[..., 2]
    part = tx.box.tag.get('part')
    if part == 'neck':
        top, bottom = np.interp(z, *NECK_TOP), np.interp(z, *NECK_LOW)
    else:
        top, bottom = np.interp(z, *BACK), under_y(z)
        if part == 'thigh':                            # the thigh hangs below the belly line: shade it as flank
            bottom = bottom - 3.0
    return np.clip((y - bottom) / np.maximum(top - bottom, 0.5), 0, 1)


def stripes(tx):
    """The dark bands of STRIPES on a body face, across the back on top and down the sides in their runs."""
    p, n, box = tx.points, tx.normal, tx.box
    band = np.zeros(p.shape[:2], bool)
    along_y = box.tag.get('part') == 'tail'
    axis = box.axes[:, 1] if along_y else box.axes[:, 2]
    length = box.size[1] if along_y else box.size[2]
    if abs(n @ axis) > 0.7 or n[1] < -0.5:
        return band
    count = max(1, int(round(length / tx.texel)))
    local = (p - box.center) @ axis
    iz = np.clip(np.floor((local / length + 0.5) * count).astype(int), 0, count - 1)
    rows = np.floor((np.interp(p[..., 2], *BACK) - p[..., 1] + np.abs(p[..., 0]) * 0.3) / tx.texel).astype(int)
    for (z0, width, reach), runs in zip(STRIPES, RUNS):
        along = (np.array([0.0, back_y(z0), z0]) - box.center) @ axis
        col = int(np.floor((along / length + 0.5) * count))
        if col < -30 or col > count + 30:
            continue
        limit = int(reach / tx.texel)
        for first, last, shift, w in runs:
            if first > limit:
                break
            w = max(1, min(w, int(round(w * (1.15 - first / max(limit, 1))))))
            c0 = col + shift - w // 2
            band |= (rows >= first) & (rows <= min(last, limit)) & (iz >= c0) & (iz < c0 + w)
    return band


def hide(tx, pal):
    """Trunk, neck, tail and thighs: scales over a dark back lightening down the flanks, the bands, the pale
    underside with its transverse belly scales and the folds of the throat."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    f = countershade(tx)
    mottle = fbm(p, 5.0, seed=3, octaves=2) - 0.5
    v = 0.8 - 0.44 * f ** 1.15 + 0.13 * mottle + scales(tx, 3, seed=5, seam=0.08) + 0.04 * max(n[1], 0)
    if n[1] > 0.5 and part in ('trunk', 'tail'):
        # a row of broad scutes either side of the sail
        near = np.abs(p[..., 0]) < 1.8
        v = np.where(near, v + 0.04 + scales(tx, 4, seed=9, seam=0.12), v)
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    if part != 'neck':
        band = stripes(tx)
        if np.any(band):
            dark = shade(pal['stripe'], 0.5 + 0.6 * mottle + 0.6 * scales(tx, 3, seed=5), tx.x, tx.y, dither=0.0)
            colour = np.where(band[..., None], dark, colour)
    pebble = scales(tx, 3, seed=7)
    edge = 0.26 + 0.06 * (noise(p, 1.2, seed=8) - 0.5) + (0.08 if part == 'neck' else 0.0) + 0.9 * pebble
    under = (f < edge) | ((n[1] < -0.5) & (part != 'thigh'))
    if part == 'thigh':
        under = (n[1] < -0.5) | ((n[0] * np.sign(tx.box.center[0]) < -0.5) & (f < 0.45 + 0.9 * pebble))
    if np.any(under):
        belly = 0.52 + 0.1 * mottle + 0.8 * scales(tx, 4, seed=6, seam=0.08)
        if n[1] < -0.5:
            iz, _ = grid(tx, 2)
            belly = 0.56 + 0.06 * mottle - 0.15 * (iz % 4 == 0) + 0.04 * (iz % 4 == 1)
        elif part == 'neck':
            col, row = face_grid(tx)
            wave = np.floor(2.0 * np.sin(col * 0.23 + tx.face.index)).astype(int)
            belly = belly - 0.13 * ((row + wave) % 5 == 0)
        colour = np.where(under[..., None], shade(pal['belly'], belly, tx.x, tx.y, dither=0.0), colour)
    return colour


def limb(tx, pal):
    """Arms, shins, feet and toes: rings of scales round the limb, darker outside, paler inside and below, broad
    scutes over the toes and fingers."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    mottle = fbm(p, 4.0, seed=13, octaves=2) - 0.5
    col, row = face_grid(tx)
    v = 0.5 + 0.12 * mottle + scales(tx, 2 if part in ('toe', 'finger') else 3, seed=14) - 0.03 * ((row // 2) % 2)
    if part in ('shoulder', 'arm'):
        v = v - 0.12 * np.clip((p[..., 1] - 12) / 8, 0, 1)
    if abs(n[0]) > 0.5 and n[0] * np.sign(tx.box.center[0]) < 0:
        v = v + 0.1
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    if part in ('shoulder', 'arm'):
        band = stripes(tx)
        colour = np.where(band[..., None], shade(pal['stripe'], 0.5 + 0.5 * mottle, tx.x, tx.y, dither=0.0), colour)
    if part in ('toe', 'meta', 'finger', 'hand') and n[1] > 0.3:
        k, _ = grid(tx, 2)
        colour = np.where((k % 3 == 0)[..., None], shade(pal['hide'], v - 0.14, tx.x, tx.y, dither=0.0), colour)
    if n[1] < -0.5:
        colour = shade(pal['belly'], 0.4 + 0.08 * mottle, tx.x, tx.y, dither=0.0)
    return colour


def head(tx, pal):
    """The head: the hide's scales, a dark crown and a darker band back from the eye, the socket and lit rim, a pale
    jaw and throat, a row of lip scales along both jaws, pits along the snout, the nostrils high on the snout."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    jaw = tx.box.bone == JAW
    mottle = fbm(p, 2.5, seed=23, octaves=2) - 0.5
    col, row = face_grid(tx)
    top = np.interp(z, [29, 32, 36, 38, 41, 44.6], [30.9, 30.9, 30.4, 28.9, 28.2, 27.3])
    f = np.clip((y - 23.0) / (top - 23.0), 0, 1)
    v = 0.78 - 0.42 * f ** 1.1 + 0.12 * mottle + scales(tx, 2, seed=24, seam=0.08) + 0.04 * max(n[1], 0)
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    if abs(n[0]) > 0.5:
        dz, dy = (z - EYE[0]) / tx.texel, (y - EYE[1]) / tx.texel
        back = np.clip(-dz, 0, 30)
        band = (dz < 3) & (dz > -26) & (np.abs(dy - back * 0.1) < 2.2 - back * 0.03) & ~jaw
        socket = (dz ** 2 / 20 + dy ** 2 / 10) < 1
        colour = np.where((band | socket)[..., None], shade(pal['stripe'], 0.45 + 0.6 * mottle, tx.x, tx.y, dither=0.0),
                          colour)
        rim = ((dz ** 2 / 20 + dy ** 2 / 10) < 1.6) & ~socket & (dy > 0.5)
        colour = np.where(rim[..., None], shade(pal['hide'], v + 0.18, tx.x, tx.y, dither=0.0), colour)
        if not jaw and part in ('snout', 'rosette', 'tip', 'temple', 'cranium'):
            lip = (row >= tx.face.rect[3] - 2) & (z > 31.0)
            k, _ = grid(tx, 2)
            colour = np.where((lip & (k % 2 == 0))[..., None], shade(pal['belly'], 0.6 + 0.1 * mottle, tx.x, tx.y),
                              colour)
            colour = np.where((lip & (k % 2 == 1))[..., None], shade(pal['hide'], 0.42, tx.x, tx.y), colour)
            # sensory pits along the snout
            pit = (part in ('snout', 'rosette')) & (cells(p, tx.texel * 2.02, seed=25) > 0.82) & (row < tx.face.rect[3] - 2)
            colour = np.where(pit[..., None], shade(pal['hide'], v - 0.16, tx.x, tx.y), colour)
        if jaw:
            k, _ = grid(tx, 2)
            lip = row <= 1
            colour = np.where((lip & (k % 2 == 1))[..., None], shade(pal['belly'], 0.58, tx.x, tx.y), colour)
            colour = np.where((lip & (k % 2 == 0))[..., None], shade(pal['hide'], 0.44, tx.x, tx.y), colour)
            pale = (row > 1) & (y < 25.0 + 0.4 * (noise(p, 1.0, seed=27) - 0.5))
            colour = np.where(pale[..., None], shade(pal['belly'], 0.52 + 0.08 * mottle + 0.5 * scales(tx, 3, seed=26),
                                                     tx.x, tx.y), colour)
    if part == 'ridge' and n[1] > 0.5:
        nostril = (np.abs(np.abs(x) - 0.3) < 0.2) & (np.abs(z - 38.6) < 0.35)
        colour = np.where(nostril[..., None], shade(pal['stripe'], 0.1, tx.x, tx.y), colour)
    if part == 'horn':
        colour = shade(pal['spike'], 0.55 + 0.5 * mottle + 0.25 * max(n[1], 0), tx.x, tx.y, dither=0.0)
    if n[1] < -0.5:
        colour = shade(pal['belly'], 0.48 + 0.06 * mottle, tx.x, tx.y) if jaw or part == 'cranium' else \
            shade(MOUTH_RAMP, 0.35 + 0.1 * mottle, tx.x, tx.y)
    if jaw and n[1] > 0.5:
        i, ci = grid(tx, 0)
        e = np.minimum(i, ci - 1 - i)
        colour = np.where((e <= 0)[..., None], shade(pal['hide'], 0.56 + 0.1 * mottle, tx.x, tx.y),
                          shade(MOUTH_RAMP, 0.3 + 0.1 * mottle, tx.x, tx.y))
    return colour


def teeth(tx):
    """A row of conical teeth as pixel art, the rest clear: hanging from the upper jaw, standing up from the lower,
    the rosette's longer and further apart."""
    box, p = tx.box, tx.points
    row = box.tag['row']
    a = (p[..., 2] - (box.center[2] - box.size[2] / 2)) / tx.texel
    rows = box.size[1] / tx.texel
    h = (box.center[1] + box.size[1] / 2 - p[..., 1]) / tx.texel if row != 'lower' else \
        (p[..., 1] - (box.center[1] - box.size[1] / 2)) / tx.texel
    width = 3.0 if row != 'rosette' else 4.0
    k = np.floor(a / width)
    f = a / width - k
    length = rows * (0.65 + 0.35 * cells(np.stack([k, np.zeros_like(k), np.full(k.shape, 3.0)], -1), 1.0, seed=5))
    half = 0.5 * (1 - np.clip(h / np.maximum(length, 1e-3), 0, 1)) ** 0.8 + 0.08
    alpha = (np.abs(f - 0.5) <= half) & (h <= length)
    v = 0.85 - 0.45 * (h / np.maximum(length, 1)) ** 2
    return shade(TOOTH_RAMP, np.where(h < 1, 0.35, v), tx.x, tx.y, dither=0.0), alpha


def blades(tx):
    """Where a card's blades are: per texel whether it lies in a blade and how far out along it (0 at the root)."""
    box = tx.box
    local = (tx.points - box.center) @ box.axes                # the card as if it were not turned
    y = box.center[1] + local[..., 1]
    z = box.center[2] + local[..., 2]
    inside = np.zeros(y.shape, bool)
    along = np.zeros(y.shape)
    for (ry, rz), (ty, tz), width in box.tag['blades']:
        d = np.array([ty - ry, tz - rz])
        length = float(np.hypot(*d))
        d = d / length
        t = ((y - ry) * d[0] + (z - rz) * d[1]) / length
        s = (-(y - ry) * d[1] + (z - rz) * d[0])
        half = width / 2 * np.clip(1 - t, 0, 1) ** 0.85 + tx.texel * 0.3
        hit = (t >= -0.1) & (t <= 1.0) & (np.abs(s) <= half) & ~inside
        inside |= hit
        along = np.where(hit, t, along)
    return inside, along


def spike(tx, pal):
    """The spikes: dark keratin at the root, lighter toward the tip; the cards' edges the body's colour."""
    inside, along = blades(tx)
    v = 0.3 + 0.6 * along + 0.1 * (cells(tx.points, tx.texel * 1.01, seed=61) - 0.5)
    return shade(pal['spike'], v, tx.x, tx.y, dither=0.0), inside


def sail(tx, pal):
    """The sail: the back's colour at the base turning into the sail's colours up the membrane, the spines as paler
    ribs, a dark band and a pale fringe toward the edge, dark spots between the ribs."""
    p, n = tx.points, tx.normal
    y, z = p[..., 1], p[..., 2]
    part = tx.box.tag.get('part')
    base = np.interp(z, *BACK)
    edge = np.interp(z, *SAIL)
    h = np.clip((y - base) / np.maximum(edge - base, 1.0), 0, 1.2)
    mottle = fbm(p, 3.0, seed=63, octaves=2) - 0.5
    colour = shade(pal['hide'], 0.45 + 0.12 * mottle + scales(tx, 3, seed=65, seam=0.06), tx.x, tx.y, dither=0.0)
    if part == 'base' and n[1] > 0.5:              # the ledge where the membrane leaves the base, in shadow
        return shade(pal['hide'], 0.12, tx.x, tx.y, dither=0.0)
    zs = np.array(SPINES)
    near = np.abs(z[..., None] - zs).min(-1)
    rib = near < tx.texel * 0.9
    v = 0.35 + 0.45 * h + 0.12 * mottle
    membrane = shade(pal['sail'], v, tx.x, tx.y, dither=0.0)
    blend = h > 0.18 + 0.12 * (noise(p, 1.5, seed=66) - 0.5)
    colour = np.where(blend[..., None], membrane, colour)
    spot = blend & (cells(p, tx.texel * 2.02, seed=67) > 0.86) & ~rib & (h < 0.8)
    colour = np.where(spot[..., None], shade(pal['stripe'], 0.5, tx.x, tx.y), colour)
    band = (h > 0.76) & (h < 0.82) & ~rib
    colour = np.where(band[..., None], shade(pal['sail'], 0.1 + 0.2 * mottle, tx.x, tx.y, dither=0.0), colour)
    colour = np.where(rib[..., None], shade(pal['spike'], 0.45 + 0.5 * np.clip(h, 0, 1), tx.x, tx.y, dither=0.0),
                      colour)
    fringe = h > 0.9
    colour = np.where((fringe & ~rib)[..., None], shade(pal['sail'], 0.95, tx.x, tx.y, dither=0.0), colour)
    tip = y > edge + 0.2
    colour = np.where(tip[..., None], shade(pal['spike'], 0.6 + 0.3 * np.clip((y - edge) / 2.2, 0, 1), tx.x, tx.y,
                                            dither=0.0), colour)
    return colour


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
    side = abs(tx.normal @ tx.box.axes[:, 0]) > 0.5
    if mat == 'spike':
        return spike(tx, pal) if side else (shade(pal['spike'], 0.3, tx.x, tx.y), None)
    if mat == 'sail':
        if part == 'membrane':
            return sail(tx, pal), (sail_alpha(tx) if side else None)
        return sail(tx, pal), None
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
