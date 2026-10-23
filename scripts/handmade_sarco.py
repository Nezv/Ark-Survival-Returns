"""The Sarcosuchus, designed box by box on the ARK rig; built by scripts/handmade_models.py.

A giant crocodile in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a broad, flat
trunk slung low between sprawling legs, a deep tail that narrows to a blade, and a long narrow snout ending in a
bulbous rosette over the front teeth. The eyes sit raised on top of the skull, the lower jaw is designed shut (the
bind pose gapes; the jaw is shut against the ARK mesh at JAW_REFERENCE) and the teeth of both jaws interlock outside
the closed mouth as cut-out rows. Rows of keeled osteoderms run down the back and two crests of upright scutes run
down the tail, joining into one toward its tip. The skin is painted as a crocodile's: square plates over the back,
rows of oval scales down the flanks, a pale belly of rectangular scales in transverse rows, dark bands across the
back and rings round the tail.
"""
import math

import numpy as np

from handmade_models import cells, fbm, hexes, noise, separate, shade
from handmade_tyrannosaurus import face_grid, grid, midline_top, scales, stripe_runs

FOLDER, STEM, HEIGHT = 'Sarco', 'sarco', 2.5
DENSITY = 1.0            # texels per runtime unit; the head is painted at FACE times that
FACE = 2.0
ATLAS_WIDTH = 512
PREVIEW_VARIANT = 'Natural'
ASSET = 'Sarco-New'
PREVIEW_CLIPS = ('Sarco-Ground-Idle', 'Sarco-Ground-Move-Fwd', 'Sarco-Ground-Attack-Bite', 'Sarco-Swim-Idle')
JAW_REFERENCE = {'c_jaw': (0.0, -24.0, 0.0)}      # the jaw shut against the upper teeth (the idle holds it at -13)
JAW_POSE = ('Sarco-Ground-Idle', 0.0, ['jaw'])
HEAD, JAW = 'c_neck3', 'c_jaw'

# The body's outline along z: (z, underside, top, half width), measured on the ARK mesh's sections, the legs and the
# osteoderms left out.
BODY = [(-125.4, 4.6, 5.2, 0.3), (-124, 3.74, 5.98, 0.55), (-120, 3.11, 7.51, 1.21), (-116, 2.78, 8.78, 1.61),
        (-112, 2.77, 9.86, 1.93), (-108, 2.8, 10.79, 2.17), (-104, 3.25, 11.77, 2.4), (-100, 3.82, 12.69, 2.54),
        (-96, 4.4, 13.87, 2.62), (-92, 4.96, 15.01, 2.78), (-88, 5.5, 16.01, 3.01), (-84, 6.22, 16.9, 3.17),
        (-80, 7.05, 17.95, 3.22), (-76, 7.91, 18.76, 3.25), (-72, 8.61, 19.84, 3.32), (-68, 9.33, 20.77, 3.55),
        (-64, 10.07, 21.55, 3.74), (-60, 10.55, 22.21, 3.86), (-56, 10.98, 22.93, 3.94), (-52, 11.36, 23.67, 4.15),
        (-48, 11.75, 24.36, 4.42), (-44, 12.11, 25.22, 5.13), (-40, 12.32, 26.01, 5.6), (-36, 12.45, 26.5, 6.21),
        (-32, 12.44, 27.02, 6.89), (-28, 12.24, 27.91, 7.66), (-24, 11.71, 28.37, 8.53), (-20, 11.04, 29.06, 9.2),
        (-16, 10.82, 29.5, 9.86), (-12, 10.32, 29.6, 10.3), (-8, 9.18, 29.58, 11.18), (-4, 8.33, 29.55, 11.87),
        (0, 7.7, 29.25, 12.01), (4, 7.7, 28.91, 11.79), (8, 7.77, 28.69, 11.2), (12, 8.44, 28.08, 10.51),
        (16, 9.6, 27.61, 9.79), (20, 10.3, 26.66, 9.6), (24, 10.13, 25.9, 9.1), (28, 9.46, 25.78, 7.5),
        (32, 9.43, 25.59, 6.75), (36, 10.29, 24.77, 6.4), (40, 11.0, 23.5, 6.2)]
# The spine's bones with where each one carries the body (rear z, front z), split into segments of about SEG units.
SPINE = [('c_tail8', -125.6, -118.11), ('c_tail7', -118.11, -108.05), ('c_tail6', -108.05, -96.91),
         ('c_tail5', -96.91, -84.85), ('c_tail4', -84.85, -71.42), ('c_tail3', -71.42, -56.94),
         ('c_tail2', -56.94, -41.54), ('c_tail1', -41.54, -25.38), ('c_back1', -25.38, -7.69),
         ('c_back2', -7.69, 3.41), ('c_back3', 3.41, 14.51), ('c_back4', 14.51, 25.67), ('c_neck1', 25.67, 33.16),
         ('c_neck2', 33.16, 40.2)]
SEG = 5.6
# A crocodile's cross-section: a flat belly, the flanks bulging at a third of the height, the back flattened.
TIER_SHAPE = [(0.0, 0.1, 0.55), (0.1, 0.27, 0.88), (0.27, 0.6, 1.0), (0.6, 0.77, 0.9), (0.77, 0.91, 0.7),
              (0.91, 1.0, 0.42)]


UPPER = []               # (rear z, front z, half width) of the upper jaw's boxes, for the painter


def body_at(z):
    zs = [b[0] for b in BODY]
    return tuple(float(np.interp(z, zs, [b[k] for b in BODY])) for k in (1, 2, 3))


def bone_at(z):
    for bone, z0, z1 in reversed(SPINE):
        if z >= z0:
            return bone
    return SPINE[0][0]


def tiers(d, bone, z0, z1, rows, mat='hide', part='trunk', nudge=0):
    """Stacked boxes across a segment, each overlapping the next a little; the tiers off the core are inset a hair
    at both ends (each segment its own hair), so no two neighbours share a plane."""
    core = max(range(len(rows)), key=lambda k: rows[k][2])
    for k, (y0, y1, half) in enumerate(rows):
        e = 0.0 if k == core else 0.05 * abs(k - core) + 0.013 * nudge
        lo = y0 - (0.05 if k > core else 0.0)
        hi = y1 + (0.05 if k < core else 0.0)
        d.span(bone, (-half, lo, z0 + e), (half, hi, z1 - e), mat, part=part, tier=k - core)


def card(d, bone, x, z0, z1, low, high, part='crest'):
    """A thin upright card standing along the top, its scutes cut out of it by the painter; moved off the midline a
    hair where one of its sides would lie in a tier's side."""
    sides = [abs(b.center[0]) + b.size[0] / 2 for b in d.boxes if b.bone == bone and b.mat == 'hide']
    while any(abs(abs(x) + s * 0.05 - edge) < 0.015 for edge in sides for s in (-1, 1)):
        x += 0.02 if x >= 0 else -0.02
    d.span(bone, (x - 0.05, low, z0), (x + 0.05, high, z1), 'crest', part=part, open=('up', 'down', 'north', 'south'),
           cutout=True, keep=True)


def design(d):
    rig = d.rig
    P = rig.pivot
    rig.design_as_in(JAW, 'Sarco-Ground-Idle', 0.0, HEAD, JAW_REFERENCE)

    # ---------------------------------------------------------------- the trunk, the tail and the neck
    n = 0
    for bone, a, b in SPINE:
        count = max(1, int(round((b - a) / SEG)))
        for k in range(count):
            z0, z1 = a + (b - a) * k / count, a + (b - a) * (k + 1) / count
            low, high, half = body_at((z0 + z1) / 2)
            h = high - low
            part = 'tail' if 'tail' in bone else 'neck' if 'neck' in bone else 'trunk'
            rows = [(low + h * f0, low + h * f1, half * w) for f0, f1, w in TIER_SHAPE]
            if half < 1.0:
                rows = [(low, high, half)]
            elif half < 2.2:
                rows = [(low, low + h * 0.25, half * 0.75), (low + h * 0.25, low + h * 0.8, half),
                        (low + h * 0.8, high, half * 0.6)]
            tiers(d, bone, z0 - 0.5, z1 + 0.3, rows, part=part, nudge=n % 5)
            n += 1

    # ---------------------------------------------------------------- the head: a broad skull, a long snout, the rosette
    head = dict(density=FACE)
    d.span(HEAD, (-5.95, 16.7, 39.4), (5.95, 22.75, 43.25), 'head', part='cranium', **head)
    d.span(HEAD, (-4.9, 17.15, 43.1), (4.9, 20.95, 46.55), 'head', part='cheek', **head)
    d.span(HEAD, (-2.35, 20.85, 43.15), (2.35, 22.05, 46.45), 'head', part='roof', **head)
    # the raised orbits: the eye set into each one's outer face (build_creature_eyes), its brow over it
    d.span(HEAD, (2.25, 20.75, 43.3), (4.3, 23.0, 46.5), 'head', part='orbit', mirror=True, **head)
    d.span(HEAD, (2.45, 22.95, 43.7), (4.05, 23.3, 46.0), 'head', part='brow', mirror=True, **head)
    # the snout narrows and runs long and low to the rosette
    d.span(HEAD, (-3.75, 17.25, 46.4), (3.75, 21.55, 48.7), 'head', part='snout', **head)
    d.span(HEAD, (-3.0, 17.2, 48.55), (3.0, 20.85, 51.6), 'head', part='snout', **head)
    d.span(HEAD, (-2.6, 17.45, 51.45), (2.6, 20.75, 55.1), 'head', part='snout', **head)
    d.span(HEAD, (-2.5, 17.6, 54.95), (2.5, 21.25, 58.2), 'head', part='snout', **head)
    d.span(HEAD, (-3.25, 17.55, 58.05), (3.25, 22.35, 61.6), 'head', part='rosette', **head)
    d.span(HEAD, (-2.2, 18.2, 61.45), (2.2, 21.5, 62.85), 'head', part='tip', **head)
    # the teeth: upper rows hanging outside the narrower jaw, the rosette's fangs, the lower rows rising outside the snout
    teeth = dict(open=('up', 'down', 'north', 'south'), cutout=True, keep=True, mirror=True, **head)
    for x, y0, y1, z0, z1, row in ((3.82, 16.55, 17.35, 46.7, 48.6, 'upper'), (3.07, 16.4, 17.3, 48.7, 51.5, 'upper'),
                                   (2.67, 16.55, 17.55, 51.6, 55.0, 'upper'), (2.57, 16.6, 17.7, 55.1, 58.0, 'upper'),
                                   (3.32, 15.8, 17.65, 58.3, 61.3, 'fang')):
        d.span(HEAD, (x - 0.04, y0, z0), (x + 0.04, y1, z1), 'tooth', part='teeth', row=row, **teeth)
    d.span(HEAD, (-1.9, 16.4, 62.0), (1.9, 18.3, 62.08), 'tooth', part='teeth', row='front',
           open=('up', 'down', 'east', 'west'), cutout=True, keep=True, **head)
    for x, y0, y1, z0, z1 in ((3.12, 17.0, 18.05, 48.9, 51.3), (2.72, 17.2, 18.25, 51.8, 54.8),
                              (2.62, 17.4, 18.45, 55.3, 57.8)):
        d.span(JAW, (x - 0.04, y0, z0), (x + 0.04, y1, z1), 'tooth', part='teeth', row='lower', **teeth)
    # the lower jaw, shut: deep at the throat, a long narrow ramus, a spoon-shaped tip under the rosette
    d.span(JAW, (-5.55, 11.2, 38.9), (5.55, 16.75, 43.1), 'head', part='jaw', **head)
    d.span(JAW, (-4.15, 12.65, 42.95), (4.15, 17.2, 46.5), 'head', part='jaw', **head)
    d.span(JAW, (-2.85, 14.0, 46.35), (2.85, 17.27, 50.4), 'head', part='jaw', **head)
    d.span(JAW, (-2.45, 14.95, 50.25), (2.45, 17.5, 54.6), 'head', part='jaw', **head)
    d.span(JAW, (-2.35, 14.85, 54.45), (2.35, 17.65, 58.9), 'head', part='chin', **head)

    # ---------------------------------------------------------------- osteoderms and the tail's crests
    body = list(d.boxes)
    for z in np.arange(-23.0, 38.0, 3.2):
        low, high, half = body_at(z)
        rows = (1.5, 4.1, 6.9) if half > 9 else (1.5, 4.0) if half > 6.0 else (1.4,)
        for k, x in enumerate(rows):
            h = (0.95, 0.8, 0.6)[k] * (0.75 if z > 26 else 1.0)
            for side in (x, -x):
                y = midline_top([_shifted(b, -side) for b in body], z)
                d.span(bone_at(z), (side - 0.55, y - 0.4, z - 0.85), (side + 0.55, y + h, z + 0.85), 'scute',
                       part='scute')
    for bone, a, b in SPINE:
        if 'tail' not in bone:
            continue
        z0, z1 = max(a, -121.0), b + 0.08
        if z1 <= z0:
            continue
        lo = min(body_at(z0)[1], body_at(z1)[1]) - 0.7
        hi = max(body_at(z0)[1], body_at(z1)[1]) + 2.4
        if b > -82.0:                                   # the double crest, the two rows drawing together toward the tip
            x = crest_x((z0 + z1) / 2)
            card(d, bone, x, z0, z1, lo, hi)
            card(d, bone, -x, z0, z1, lo, hi)
        else:
            card(d, bone, 0.0, z0, z1, lo, hi)

    # ---------------------------------------------------------------- the sprawling legs and the webbed feet
    elbow, wrist = P('l_eblow'), P('l_wrist')
    d.seg('l_shoulder', P('l_shoulder') + (-1.0, 0.6, 0.0), elbow, 4.6, 5.2, 'hide', ext=(0.0, 0.8), mirror=True,
          part='arm')
    d.seg('l_eblow', elbow, wrist, 3.5, 3.9, 'hide', ext=(0.6, 0.4), mirror=True, part='forearm')
    d.span('l_wrist', (11.5, 0.0, 18.9), (17.6, 2.3, 23.4), 'hide', mirror=True, part='foot')
    for finger, w in (('A', 1.25), ('B', 1.3), ('C', 1.3), ('D', 1.2), ('E', 1.05)):
        a, b = P(f'l_finger{finger}11'), P(f'l_finger{finger}12')
        d.seg(f'l_finger{finger}11', a, b, w, 1.05, 'hide', ext=(0.3, 0.1), side=(0, 1, 0), mirror=True, part='toe')
        if finger != 'E':
            tip = b + (b - a) * 0.45
            d.seg(f'l_finger{finger}12', b, tip, w * 0.55, 0.6, 'claw', side=(0, 1, 0), mirror=True, part='claw',
                  along=(0.2, 1.0))
    knee, ankle = P('l_knee'), P('l_ankle')
    d.span('l_leg', (6.0, 12.0, -23.0), (12.4, 22.6, -12.2), 'hide', mirror=True, part='thigh')
    d.seg('l_leg', (9.0, 15.5, -17.6), knee, 5.4, 6.4, 'hide', ext=(0.0, 0.9), mirror=True, part='thigh')
    d.seg('l_knee', knee, ankle, 4.0, 4.6, 'hide', ext=(0.5, 0.4), mirror=True, part='shin')
    d.span('l_ankle', (11.0, 0.0, -20.6), (17.2, 2.6, -14.3), 'hide', mirror=True, part='foot')
    for toe, w in (('A', 1.35), ('B', 1.45), ('C', 1.45), ('D', 1.3), ('E', 1.1)):
        a, b = P(f'l_toe{toe}11'), P(f'l_toe{toe}12')
        d.seg(f'l_toe{toe}11', a, b, w, 1.1, 'hide', ext=(0.4, 0.1), side=(0, 1, 0), mirror=True, part='toe')
        if toe != 'E':
            tip = b + (b - a) * 0.4
            d.seg(f'l_toe{toe}12', b, tip, w * 0.55, 0.62, 'claw', side=(0, 1, 0), mirror=True, part='claw',
                  along=(0.2, 1.0))
    UPPER[:] = [(b.center[2] - b.size[2] / 2, b.center[2] + b.size[2] / 2, b.size[0] / 2) for b in d.boxes
                if b.bone == HEAD and b.mat == 'head']
    separate(d, DENSITY)


def crest_x(z):
    """Where the tail's paired crests stand off the midline: over the shoulder of the tail's top, drawing in."""
    return float(np.interp(z, [-82, -60, -40, -26], [1.25, 1.75, 2.4, 3.1]))


def _shifted(box, dx):
    return type(box)(box.bone, box.center + np.array([dx, 0.0, 0.0]), box.size, box.axes, box.mat, box.tag)


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(hide=hexes('#1f2318', '#2a2f1f', '#363c27', '#434a30', '#51583a', '#606745', '#707751', '#81875e'),
                    belly=hexes('#8a8462', '#9b9572', '#aca683', '#bcb794', '#cbc6a5', '#d8d4b6'),
                    stripe=hexes('#121510', '#181c14', '#1f2419', '#272d1f'),
                    scute=hexes('#16190f', '#1f2316', '#292e1e', '#343a27', '#414831', '#4f573b'),
                    mouth=hexes('#3a1a18', '#552622', '#70352e', '#8b463c', '#a4594c', '#ba6d5e')),
}
PALETTES['olive'] = PALETTES['Natural']
PALETTES['umber'] = dict(hide=hexes('#21190f', '#2e2316', '#3c2e1e', '#4b3a27', '#5a4731', '#6a553b', '#7b6446', '#8c7352'),
                         belly=hexes('#8f7d5f', '#a08f70', '#b0a081', '#bfb092', '#cdbfa3', '#d9ccb3'),
                         stripe=hexes('#0f0b07', '#16100b', '#1d160f', '#251c14'),
                         scute=hexes('#140f09', '#1e170f', '#292016', '#352a1d', '#423526', '#504130'),
                         mouth=PALETTES['Natural']['mouth'])
PALETTES['slate'] = dict(hide=hexes('#1b1d1d', '#252828', '#303434', '#3c4140', '#494e4c', '#565c59', '#646a66', '#737974'),
                         belly=hexes('#857f6c', '#96917e', '#a7a290', '#b7b3a2', '#c6c2b3', '#d3d0c3'),
                         stripe=hexes('#0d0e0e', '#131414', '#191b1b', '#202222'),
                         scute=hexes('#121414', '#1a1d1d', '#232727', '#2d3232', '#383e3d', '#444b49'),
                         mouth=PALETTES['Natural']['mouth'])
PALETTES['swamp'] = dict(hide=hexes('#15201a', '#1c2a21', '#24352a', '#2d4133', '#374e3d', '#425b47', '#4e6952', '#5b775e'),
                         belly=hexes('#7f8462', '#909572', '#a1a583', '#b1b594', '#c0c4a5', '#cdd1b5'),
                         stripe=hexes('#0b100c', '#101611', '#151d17', '#1b251d'),
                         scute=hexes('#0f1510', '#151d17', '#1d271f', '#263228', '#303d32', '#3b493d'),
                         mouth=PALETTES['Natural']['mouth'])
# the hides a shade lighter, so they still read under in-game light (side faces get half of it)
for _pal in {id(p): p for p in PALETTES.values()}.values():
    _pal['hide'] = np.clip(_pal['hide'] * 1.18 + 6, 0, 255)
TOOTH_RAMP = hexes('#8a7f66', '#ab9f83', '#c9bf9f', '#e0d8bc', '#efe9d5', '#faf7ec')
CLAW_RAMP = hexes('#100e0b', '#1d1914', '#2b251e', '#3b3329', '#4e4436', '#655845')

# The dark bands across the back and round the tail, as pixel runs (see stripe_runs): (z where a band crosses the
# spine, its width in texels there, how far down the side it reaches in units).
STRIPES = [(20.0, 3, 7.0), (11.0, 4, 9.0), (2.0, 4, 10.0), (-7.0, 4, 10.0), (-16.0, 4, 9.5), (-25.0, 4, 9.0),
           (-34.0, 4, 9.0), (-43.0, 4, 9.0), (-52.0, 4, 9.0), (-61.0, 4, 9.0), (-70.0, 4, 9.0), (-79.0, 4, 9.0),
           (-88.0, 4, 9.0), (-97.0, 3, 9.0), (-106.0, 3, 9.0), (-115.0, 3, 9.0), (-122.5, 2, 9.0)]
RUNS = [stripe_runs(301 + k, w, 60) for k, (_, w, _) in enumerate(STRIPES)]
EYE = (44.9, 21.9)       # (z, y): the eye on the orbit's outer face
EYE_RING = (1.75, 1.15)  # the dark ring round it, (z, y) radii


def upper_half(z):
    """The upper jaw's half width at z (an array), from the head's boxes as designed."""
    out = np.zeros(np.shape(z))
    for z0, z1, half in UPPER:
        out = np.where((z >= z0) & (z <= z1), np.maximum(out, half), out)
    return out


def back_y(z):
    return np.interp(z, [b[0] for b in BODY], [b[2] for b in BODY])


def under_y(z):
    return np.interp(z, [b[0] for b in BODY], [b[1] for b in BODY])


def stripes(tx):
    """The dark bands of STRIPES on a body face: across the back on top, then down the sides in their runs, counted
    on the face's own texels along its box."""
    p, n, box = tx.points, tx.normal, tx.box
    band = np.zeros(p.shape[:2], bool)
    axis = box.axes[:, 2]
    if abs(n @ axis) > 0.7:
        return band
    count = max(1, int(round(box.size[2] / tx.texel)))
    local = (p - box.center) @ axis
    iz = np.clip(np.floor((local / box.size[2] + 0.5) * count).astype(int), 0, count - 1)
    rows = np.floor((back_y(p[..., 2]) - p[..., 1] + np.abs(p[..., 0]) * 0.6) / tx.texel).astype(int)
    for (z0, width, reach), runs in zip(STRIPES, RUNS):
        along = (np.array([0.0, back_y(z0), z0]) - box.center) @ axis
        col = int(np.floor((along / box.size[2] + 0.5) * count))
        if col < -40 or col > count + 40:
            continue
        limit = int(reach / tx.texel)
        for first, last, shift, w in runs:
            if first > limit:
                break
            c0 = col + shift // 2 - w // 2
            band |= (rows >= first) & (rows <= min(last, limit)) & (iz >= c0) & (iz < c0 + w)
    return band


def countershade(tx):
    p = tx.points
    top, bottom = back_y(p[..., 2]), under_y(p[..., 2])
    return np.clip((p[..., 1] - bottom) / np.maximum(top - bottom, 0.5), 0, 1)


def plates(tx, along=3, across=2):
    """Square osteoderm plates over the back, in world-aligned rows: per texel the plate's tone, whether it lies on
    the seam between plates and whether it is the plate's keel (its middle column)."""
    p = tx.points
    t = tx.texel
    row = np.floor(p[..., 2] / (t * (along + 1)) + 1e-6)
    stagger = (row % 2) * 0.5
    col = np.floor(np.abs(p[..., 0]) / (t * (across + 1)) + stagger)
    fz = p[..., 2] / (t * (along + 1)) - row
    fx = np.abs(p[..., 0]) / (t * (across + 1)) + stagger - col
    tone = cells(np.stack([row, col, np.sign(p[..., 0])], -1), 1.0, seed=51)
    seam = (fz < 1.0 / (along + 1)) | (fx < 1.0 / (across + 1))
    keel = (np.abs(fx - 0.6) < 0.5 / (across + 1)) & ~seam
    return tone, seam, keel


def hide(tx, pal):
    """The body's skin: plates over the back, rows of oval scales down the flanks with a few big bumps, the bands,
    and the pale belly's rectangular scales in transverse rows."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part', 'trunk')
    f = countershade(tx)
    mottle = fbm(p, 4.0, seed=3, octaves=2) - 0.5
    v = 0.72 - 0.28 * f + 0.16 * mottle + scales(tx, 3, seed=5, seam=0.12)
    if n[1] > 0.5:                                     # the plates across the back
        tone, seam, keel = plates(tx)
        v = 0.5 + 0.14 * mottle + 0.12 * (tone - 0.5) - 0.18 * seam + 0.1 * keel
    elif abs(n[1]) < 0.5:                              # the flanks: horizontal rows of oval scales
        col, row = face_grid(tx)
        lane = (np.floor((back_y(p[..., 2]) - p[..., 1]) / tx.texel) // 2).astype(int)
        knob = (cells(np.stack([lane, np.floor(p[..., 2] / (tx.texel * 3)), np.zeros_like(lane)], -1), 1.0, seed=9)
                > 0.86) & (f > 0.45)
        v = v - 0.07 * (lane % 2) + 0.12 * knob
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    band = stripes(tx)
    if np.any(band):
        dark = shade(pal['stripe'], 0.5 + 0.7 * mottle + 0.8 * scales(tx, 3, seed=5), tx.x, tx.y, dither=0.0)
        colour = np.where(band[..., None], dark, colour)
    edge = 0.22 + 0.05 * (noise(p, 2.0, seed=8) - 0.5) + 0.6 * scales(tx, 3, seed=7)
    under = (f < edge) | (n[1] < -0.5)
    if part == 'tail':
        under &= f < 0.12
        under |= n[1] < -0.5
    if np.any(under):
        col, row = face_grid(tx)
        if n[1] < -0.5:                                # transverse rows of rectangular belly scales
            iz, _ = grid(tx, 2)
            ix = np.floor(np.abs(p[..., 0]) / tx.texel).astype(int)
            belly = 0.62 + 0.06 * mottle - 0.22 * (iz % 3 == 0) - 0.12 * ((ix % 3 == 0) & (iz % 3 != 0))
        else:
            belly = 0.55 + 0.08 * mottle + 0.7 * scales(tx, 2, seed=6, seam=0.12)
        pale = shade(pal['belly'], belly, tx.x, tx.y, dither=0.0)
        colour = np.where(under[..., None], pale, colour)
    return colour


def limb(tx, pal):
    """Legs and feet: rows of scales, darker outside with a few bands, paler inside and below; plates over the toes."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    mottle = fbm(p, 3.0, seed=13, octaves=2) - 0.5
    col, row = face_grid(tx)
    v = 0.5 + 0.14 * mottle + scales(tx, 2 if part in ('toe', 'foot') else 3, seed=14, seam=0.12) \
        - 0.06 * ((row // 2) % 2)
    if abs(n[0]) > 0.5 and n[0] * np.sign(tx.box.center[0]) < 0:
        v += 0.12                                      # the inside of the limb is paler
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    if part in ('toe', 'foot') and n[1] > 0.5:
        k, _ = grid(tx, 2)
        colour = np.where((k % 2 == 0)[..., None], shade(pal['hide'], v - 0.15, tx.x, tx.y, dither=0.0), colour)
    if n[1] < -0.5 and part in ('foot', 'toe'):
        colour = shade(pal['belly'], 0.3 + 0.08 * mottle, tx.x, tx.y, dither=0.0)
    return colour


def head(tx, pal):
    """The head: fine pitted scales over the skull and the snout, darker on top with mottles, a pale lip line and a
    pale jaw and throat with the lip scales along both jaws; the eye's orbit ringed, two nostrils on the rosette."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    mottle = fbm(p, 1.6, seed=23, octaves=2) - 0.5
    col, row = face_grid(tx)
    jaw = tx.box.bone == JAW
    pit = cells(p, tx.texel * 1.01, seed=24)
    v = 0.5 + 0.18 * mottle + 0.08 * (pit - 0.5) + scales(tx, 2, seed=24, seam=0.08)
    if n[1] > 0.5:
        v = v - 0.06 - 0.1 * ((pit > 0.8) & (part in ('snout', 'rosette', 'cranium', 'roof')))
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    blotch = (fbm(p, 1.2, seed=26, octaves=2) > 0.66) & (n[1] > -0.5)
    colour = np.where(blotch[..., None], shade(pal['stripe'], 0.9 + 0.6 * mottle, tx.x, tx.y, dither=0.0), colour)
    side = abs(n[0]) > 0.5
    if side and not jaw:
        lip = y < 17.95 + 0.02 * (z - 50)              # the pale lip along the upper jaw
        light = (np.floor(z / tx.texel) // 2) % 2 == 0
        colour = np.where((lip & light)[..., None], shade(pal['belly'], 0.5 + 0.1 * mottle, tx.x, tx.y), colour)
        colour = np.where((lip & ~light)[..., None], shade(pal['hide'], 0.62, tx.x, tx.y), colour)
        if part in ('orbit', 'cheek', 'cranium'):
            r = ((z - EYE[0]) / EYE_RING[0]) ** 2 + ((y - EYE[1]) / EYE_RING[1]) ** 2
            ring = (r < 1.0) & (r > 0.62)
            colour = np.where(ring[..., None], shade(pal['stripe'], 0.2, tx.x, tx.y), colour)
    if jaw and n[1] < 0.5:
        pale = (y < 16.6 - 0.04 * np.maximum(z - 46, 0)) | (n[1] < -0.5)
        belly = 0.56 + 0.08 * mottle + 0.6 * scales(tx, 2, seed=26, seam=0.1)
        if n[1] < -0.5:                                # the throat's rectangular scales
            iz, _ = grid(tx, 2)
            belly = 0.6 + 0.05 * mottle - 0.2 * (iz % 3 == 0)
        colour = np.where(pale[..., None], shade(pal['belly'], belly, tx.x, tx.y, dither=0.0), colour)
        if side:
            lip = row <= 1
            light = (np.floor(z / tx.texel) // 2) % 2 == 1
            colour = np.where((lip & light)[..., None], shade(pal['belly'], 0.48, tx.x, tx.y), colour)
            colour = np.where((lip & ~light)[..., None], shade(pal['hide'], 0.6, tx.x, tx.y), colour)
    if jaw and n[1] > 0.5:                              # the mouth's floor, inside the upper jaw's reach
        inside = np.abs(x) < upper_half(z) - tx.texel
        colour = np.where(inside[..., None], shade(pal['mouth'], 0.3 + 0.1 * mottle, tx.x, tx.y), colour)
    if not jaw and n[1] < -0.5:
        colour = shade(pal['mouth'], 0.3 + 0.1 * mottle, tx.x, tx.y)      # the palate
    if part == 'rosette' and n[1] > 0.5:                # the nostrils on top of the rosette
        nostril = (np.abs(np.abs(x) - 0.75) < tx.texel * 1.0) & (np.abs(z - 60.3) < tx.texel * 1.5)
        colour = np.where(nostril[..., None], shade(pal['stripe'], 0.05, tx.x, tx.y), colour)
    if part == 'brow':
        colour = shade(pal['scute'], 0.55 + 0.2 * mottle + 0.15 * (pit - 0.5), tx.x, tx.y, dither=0.0)
    return colour


def teeth(tx):
    """A row of conical teeth as pixel art, a tooth every few texels tapering to its tip, the rest clear."""
    box = tx.box
    p = tx.points
    kind = box.tag['row']
    along = 0 if kind == 'front' else 2
    a = (p[..., along] - (box.center[along] - box.size[along] / 2)) / tx.texel
    h = (p[..., 1] - (box.center[1] - box.size[1] / 2)) / tx.texel
    rows = box.size[1] / tx.texel
    width = 4.0 if kind == 'fang' else 3.0
    k = np.floor(a / width)
    f = a / width - k
    key = np.stack([k, np.zeros_like(k), np.full(k.shape, float(tx.face.index % 97))], -1)
    length = rows * (0.75 + 0.25 * cells(key, 1.0, seed=5))
    root = h if kind == 'lower' else rows - h          # distance from the gum
    half = 0.36 * (1 - np.clip(root / np.maximum(length, 1e-3), 0, 1)) ** 0.8 + 0.06
    alpha = (np.abs(f - 0.5) <= half) & (root <= length) & ((k % 2 == 0) | (kind in ('fang', 'front')))
    v = 0.9 - 0.4 * (root / np.maximum(length, 1)) ** 2
    v = np.where(root < 1, 0.35, v)
    return shade(TOOTH_RAMP, v, tx.x, tx.y, dither=0.0), alpha


def crest(tx, pal):
    """The tail's crests: a sawtooth of upright scutes, each a triangle leaning back, darker at the base."""
    p = tx.points
    y, z = p[..., 1], p[..., 2]
    base = back_y(z) - (np.abs(p[..., 0]) * 0.25)
    pitch = 2.6
    t = (z / pitch) % 1.0
    tall = np.interp(z, [-121, -100, -80, -50, -26], [0.9, 1.3, 1.6, 1.8, 1.4])
    rise = np.where(t > 0.75, (t - 0.75) / 0.25, 1 - t / 0.75)
    alpha = y <= base + 0.15 + tall * rise
    v = 0.3 + 0.5 * np.clip((y - base) / np.maximum(tall, 0.5), 0, 1) + 0.1 * (cells(p, tx.texel * 1.01, seed=61) - 0.5)
    return shade(pal['scute'], v, tx.x, tx.y, dither=0.0), alpha


def keratin(tx, ramp):
    """Osteoderms and claws: dark at the base, lighter to the keel or the tip."""
    box, n = tx.box, tx.normal
    if 'along' in box.tag:
        a, b = box.tag['along']
        s = ((tx.points - box.center) @ box.axes[:, 1]) / box.size[1] + 0.5
        t = a + (b - a) * np.clip(s, 0, 1)
    else:
        t = np.clip((tx.points[..., 1] - (box.center[1] - box.size[1] / 2)) / box.size[1], 0, 1)
    v = 0.25 + 0.55 * t + 0.12 * (cells(tx.points, tx.texel * 1.01, seed=41) - 0.5) + 0.08 * max(n[1], 0)
    return shade(ramp, v, tx.x, tx.y, dither=0.0)


def painter(tx, variant):
    pal = PALETTES[variant]
    mat = tx.box.mat
    part = tx.box.tag.get('part')
    if mat == 'tooth':
        return teeth(tx)
    if mat == 'crest':
        return crest(tx, pal)
    if mat == 'hide':
        if part in ('trunk', 'tail', 'neck'):
            return hide(tx, pal), None
        return limb(tx, pal), None
    if mat == 'head':
        return head(tx, pal), None
    if mat == 'scute':
        return keratin(tx, pal['scute']), None
    if mat == 'horn':
        return keratin(tx, pal['horn']), None
    if mat == 'tusk':
        return keratin(tx, TOOTH_RAMP), None
    return keratin(tx, CLAW_RAMP), None
