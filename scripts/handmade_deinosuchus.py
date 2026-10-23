"""The Deinosuchus, designed box by box on the ARK rig; built by scripts/handmade_models.py.

The terror crocodile in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a vast barrel
of a trunk on thick, bowed legs, a heavy tail that drops to a fin of tall paired scutes, and a broad, blunt head
whose jaw bulges wide at the cheeks behind a snout that swells again at the tip. The eyes sit on a raised table
on top of the skull. The jaw is designed shut as the idle holds it, with the great teeth of the upper jaw hanging
outside the lower and the front fangs crossing it. Paired rows of osteoderms cover the back and the neck; the skin is
painted as the Sarco's: plates over the back, rows of scales down the flanks, a pale belly of rectangular scales,
dark bands across the back and rings round the tail.
"""
import math

import numpy as np

from handmade_models import cells, fbm, hexes, lift, noise, separate, shade
from handmade_tyrannosaurus import face_grid, grid, midline_top, scales, stripe_runs

FOLDER, STEM, HEIGHT = 'Deinosuchus', 'deinosuchus', 3.5
DENSITY = 1.0            # texels per runtime unit; the head is painted at FACE times that
FACE = 2.0
ATLAS_WIDTH = 512
PREVIEW_VARIANT = 'Natural'
ASSET = 'Deinosuchus_TLC_Rig'
PREVIEW_CLIPS = ('Deinosuchus_Idle', 'Deinosuchus_Move_FWD', 'Deinosuchus_Attack_Bite', 'Deinosuchus_Swim_Idle')
JAW_POSE = ('Deinosuchus_Idle', 0.0, ['Jaw', 'dewlap'])     # the frame the jaw is designed in: the idle shuts it
HEAD, JAW = 'Head', 'Jaw'

# The body's outline along z: (z, underside, top, half width), measured on the ARK mesh's sections, the legs, the
# osteoderms and the tail's crests left out.
BODY = [(-279.0, 1.5, 3.5, 0.6), (-276, -1.17, 7.14, 2.2), (-268, -1.36, 14.3, 3.08), (-260, -2.17, 19.45, 4.14),
        (-252, -2.33, 22.6, 4.81), (-244, -1.9, 24.0, 4.85), (-236, -1.96, 25.2, 4.93), (-228, -2.13, 25.4, 5.15),
        (-220, -2.39, 26.3, 5.59), (-212, -2.49, 27.4, 5.75), (-204, -2.48, 27.8, 6.15), (-196, -2.51, 28.1, 6.52),
        (-188, -2.42, 28.4, 6.87), (-180, -3.0, 29.0, 7.89), (-172, -3.15, 29.0, 8.59), (-164, -3.23, 29.4, 9.46),
        (-156, -3.26, 30.15, 10.58), (-148, -3.19, 31.0, 11.34), (-140, -3.32, 31.6, 12.23), (-132, -3.17, 33.2, 12.98),
        (-124, -3.09, 34.75, 13.93), (-116, -2.93, 35.5, 14.91), (-108, -2.86, 35.9, 15.61), (-100, -2.56, 36.4, 16.51),
        (-92, -2.47, 37.0, 17.26), (-84, -2.14, 37.6, 18.26), (-76, -1.69, 38.45, 19.08), (-68, -1.21, 39.7, 19.63),
        (-60, -0.8, 41.1, 19.8), (-52, -1.5, 42.9, 20.2), (-44, -3.0, 44.5, 21.0), (-36, -5.0, 45.3, 22.5),
        (-28, -7.6, 45.7, 25.0), (-20, -8.9, 47.3, 26.2), (-12, -9.3, 47.5, 26.1), (-4, -9.3, 47.7, 26.1),
        (4, -8.9, 47.5, 25.9), (12, -8.2, 47.1, 25.0), (20, -7.0, 46.5, 23.2), (28, -4.6, 45.7, 21.7),
        (36, -4.4, 44.75, 20.5), (44, -2.5, 43.2, 20.5), (52, -1.0, 41.5, 19.9), (60, -1.9, 42.0, 19.7),
        (68.5, -2.5, 41.0, 17.5)]
SPINE = [('Tail8', -279.1, -255.01), ('Tail7', -255.01, -231.47), ('Tail6', -231.47, -200.84),
         ('Tail5', -200.84, -169.46), ('Tail4', -169.46, -138.52), ('Tail3', -138.52, -107.15),
         ('Tail2', -107.15, -82.2), ('Tail1', -82.2, -59.82), ('Back4', -59.82, -26.33), ('Back3', -26.33, 10.98),
         ('Back2', 10.98, 36.23), ('Back1', 36.23, 51.78), ('Neck2', 51.78, 62.55), ('Neck1', 62.55, 68.3)]
SEG = 12.0
# A barrel of a cross-section: the flanks bulge low, the back is broad and flat.
TIER_SHAPE = [(0.0, 0.1, 0.5), (0.1, 0.27, 0.85), (0.27, 0.6, 1.0), (0.6, 0.78, 0.92), (0.78, 0.91, 0.74),
              (0.91, 1.0, 0.46)]
SMALL = (1.0, 3.0)        # below these half widths a segment is one box, or three
# The head: per z span, its tiers from the lip up as (bottom, top, half width), measured on the ARK mesh shut.
HEAD_ROWS = [(68.0, 76.2, [(24.0, 29.5, 16.9), (29.4, 33.6, 15.0), (33.5, 35.5, 12.0)]),
             (76.0, 84.2, [(23.6, 29.5, 16.6), (29.4, 33.6, 14.0), (33.5, 36.5, 10.6), (36.4, 38.6, 8.4)]),
             (84.0, 92.8, [(23.1, 29.0, 15.6), (28.9, 32.2, 12.5), (32.1, 35.4, 9.3)]),
             (92.6, 100.8, [(20.6, 28.0, 14.9), (27.9, 33.4, 8.65)]),
             (100.6, 108.2, [(19.4, 26.0, 13.2), (25.9, 29.6, 8.5), (29.5, 31.3, 4.5)]),
             (108.0, 116.2, [(18.9, 26.0, 12.4), (25.9, 30.6, 7.0)]),
             (116.0, 120.2, [(18.9, 26.0, 10.3), (25.9, 30.4, 6.0)]),
             (120.0, 128.2, [(17.5, 27.0, 10.8), (26.9, 31.6, 7.5)]),
             (128.0, 132.0, [(18.0, 29.4, 7.0)])]
JAW_ROWS = [(66.0, 74.2, [(-0.5, 8.0, 10.5), (7.9, 13.0, 15.5), (12.9, 27.0, 19.3)]),
            (74.0, 82.2, [(1.5, 8.0, 9.5), (7.9, 11.5, 15.0), (11.4, 26.6, 19.3)]),
            (82.0, 90.2, [(2.4, 8.0, 8.5), (7.9, 13.0, 13.0), (12.9, 25.6, 17.6)]),
            (90.0, 98.2, [(1.8, 8.0, 8.0), (7.9, 13.5, 12.0), (13.4, 24.6, 14.6)]),
            (98.0, 106.2, [(2.1, 8.0, 7.0), (7.9, 13.5, 10.0), (13.4, 22.8, 12.8)]),
            (106.0, 114.2, [(3.5, 9.0, 6.5), (8.9, 14.0, 9.0), (13.9, 21.0, 11.4)]),
            (114.0, 120.2, [(6.0, 11.0, 6.0), (10.9, 20.0, 10.0)]),
            (120.0, 126.2, [(9.4, 13.0, 5.5), (12.9, 18.8, 8.8)]),
            (126.0, 129.0, [(12.5, 18.0, 5.0)])]


UPPER = []               # (rear z, front z, half width) of the upper jaw's boxes, for the painter


def body_at(z):
    zs = [b[0] for b in BODY]
    return tuple(float(np.interp(z, zs, [b[k] for b in BODY])) for k in (1, 2, 3))


def bone_at(z):
    for bone, z0, z1 in reversed(SPINE):
        if z >= z0:
            return bone
    return SPINE[0][0]


def tiers(d, bone, z0, z1, rows, mat='hide', part='trunk', nudge=0, **tag):
    """Stacked boxes across a segment, each overlapping the next a little; the tiers off the core are inset a hair
    at both ends (each segment its own hair), so no two neighbours share a plane."""
    core = max(range(len(rows)), key=lambda k: rows[k][2])
    for k, (y0, y1, half) in enumerate(rows):
        e = 0.0 if k == core else 0.1 * abs(k - core) + 0.027 * nudge
        lo = y0 - (0.1 if k > core else 0.0)
        hi = y1 + (0.1 if k < core else 0.0)
        d.span(bone, (-half, lo, z0 + e), (half, hi, z1 - e), mat, part=part, tier=k - core, **tag)


def card(d, bone, x, z0, z1, low, high, part='crest'):
    """A thin upright card standing along the top, its scutes cut out of it by the painter; moved off the midline a
    hair where one of its sides would lie in a tier's side."""
    sides = [abs(b.center[0]) + b.size[0] / 2 for b in d.boxes if b.bone == bone and b.mat == 'hide']
    while any(abs(abs(x) + s * 0.1 - edge) < 0.03 for edge in sides for s in (-1, 1)):
        x += 0.05 if x >= 0 else -0.05
    d.span(bone, (x - 0.1, low, z0), (x + 0.1, high, z1), 'crest', part=part, open=('up', 'down', 'north', 'south'),
           cutout=True, keep=True)


def body(d):
    """The trunk, the tail and the neck, segment by segment along SPINE."""
    n = 0
    for bone, a, b in SPINE:
        count = max(1, int(round((b - a) / SEG)))
        for k in range(count):
            z0, z1 = a + (b - a) * k / count, a + (b - a) * (k + 1) / count
            low, high, half = body_at((z0 + z1) / 2)
            h = high - low
            part = 'tail' if 'Tail' in bone else 'neck' if 'Neck' in bone else 'trunk'
            rows = [(low + h * f0, low + h * f1, half * w) for f0, f1, w in TIER_SHAPE]
            if half < SMALL[0]:
                rows = [(low, high, half)]
            elif half < SMALL[1]:
                rows = [(low, low + h * 0.25, half * 0.75), (low + h * 0.25, low + h * 0.8, half),
                        (low + h * 0.8, high, half * 0.6)]
            tiers(d, bone, z0 - 1.1, z1 + 0.6, rows, part=part, nudge=n % 5)
            n += 1


def design(d):
    rig = d.rig
    P = rig.pivot
    for bone in rig.bones:
        if bone == JAW or 'dewlap' in bone or bone == 'delap1_end':
            rig.design_as_in(bone, 'Deinosuchus_Idle', 0.0, HEAD)
    body(d)

    # ---------------------------------------------------------------- the head: a broad skull, the eyes on its table
    head = dict(density=FACE)
    for k, (z0, z1, rows) in enumerate(HEAD_ROWS):
        part = 'snout' if z0 > 100 else 'cranium'
        part = 'rosette' if z0 >= 120 else part
        tiers(d, HEAD, z0, z1, rows, 'head', part=part, nudge=k % 5, **head)
    d.span(HEAD, (5.2, 33.3, 93.4), (8.3, 34.7, 100.0), 'head', part='brow', mirror=True, **head)
    for k, (z0, z1, rows) in enumerate(JAW_ROWS):
        tiers(d, JAW, z0, z1, rows, 'head', part='chin' if z0 >= 114 else 'jaw', nudge=(k + 2) % 5, **head)
    # the teeth: the upper rows hang outside the lower jaw, the front fangs cross it, the lower rows rise outside
    teeth = dict(open=('up', 'down', 'north', 'south'), cutout=True, keep=True, mirror=True, **head)
    for x, y0, y1, z0, z1, row in ((13.35, 15.8, 19.7, 100.8, 108.0, 'upper'), (12.55, 15.3, 19.2, 108.2, 116.0, 'upper'),
                                   (10.95, 11.8, 17.8, 120.4, 127.6, 'fang')):
        d.span(HEAD, (x - 0.08, y0, z0), (x + 0.08, y1, z1), 'tooth', part='teeth', row=row, **teeth)
    d.span(HEAD, (-6.0, 12.5, 131.7), (6.0, 18.2, 131.85), 'tooth', part='teeth', row='front',
           open=('up', 'down', 'east', 'west'), cutout=True, keep=True, **head)
    for x, y0, y1, z0, z1 in ((13.4, 18.6, 21.6, 98.6, 105.6), (10.45, 19.5, 25.5, 116.4, 119.8)):
        d.span(JAW, (x - 0.08, y0, z0), (x + 0.08, y1, z1), 'tooth', part='teeth', row='lower', **teeth)

    # ---------------------------------------------------------------- osteoderms and the tail's crests
    trunk = list(d.boxes)
    for z in np.arange(-56.0, 68.0, 6.5):
        low, high, half = body_at(z)
        rows = (3.5, 9.5, 15.5) if half > 21 else (3.5, 9.5)
        for k, x in enumerate(rows):
            h = (2.6, 2.1, 1.5)[k] * (0.8 if z > 52 else 1.0)
            for side in (x, -x):
                y = midline_top([_shifted(b, -side) for b in trunk], z)
                d.span(bone_at(z), (side - 1.4, y - 1.0, z - 2.1), (side + 1.4, y + h, z + 2.1), 'scute',
                       part='scute')
    for bone, a, b in SPINE:
        if 'Tail' not in bone:
            continue
        z0, z1 = max(a, -272.0), b + 0.15
        lo = min(body_at(z0)[1], body_at(z1)[1]) - 1.5
        hi = max(body_at(z0)[1], body_at(z1)[1]) + (8.0 if b < -195 else 4.0)
        if b > -175.0:
            x = crest_x((z0 + z1) / 2)
            card(d, bone, x, z0, z1, lo, hi)
            card(d, bone, -x, z0, z1, lo, hi)
        else:
            card(d, bone, 0.0, z0, z1, lo, hi)

    # ---------------------------------------------------------------- the bowed legs and the broad feet
    elbow, wrist = P('L_Elbow'), P('L_Wrist')
    d.seg('L_Shoulder', P('L_Shoulder') + (3.0, 0.0, 0.0), elbow, 12.0, 13.0, 'hide', ext=(0.0, 3.0), mirror=True,
          part='arm')
    d.seg('L_Elbow', elbow, wrist, 9.5, 10.5, 'hide', ext=(2.0, 1.5), mirror=True, part='forearm')
    d.span('L_Wrist', (27.6, -37.6, 34.4), (41.6, -26.0, 43.6), 'hide', mirror=True, part='foot')
    for k, w in ((1, 4.4), (2, 4.8), (3, 4.6), (4, 3.6)):
        a = P(f'L_Fingy{k}')
        tip = np.array([a[0] + (a[0] - 34.0) * 0.25, -43.4, a[2] + 4.5])
        d.seg(f'L_Fingy{k}', a + (0, 2.0, -1.0), tip + (0, 1.6, -1.5), w, 4.0, 'hide', side=(1, 0, 0), mirror=True,
              part='toe')
        d.seg(f'L_Fingy{k}', tip + (0, 1.6, -1.6), tip + (0, 0.6, 2.2), w * 0.55, 1.8, 'claw', side=(1, 0, 0),
              mirror=True, part='claw', along=(0.2, 1.0))
    knee, ankle = P('L_Knee'), P('L_Ankle')
    d.span('L_Hip', (17.5, 5.0, -57.5), (32.5, 34.0, -36.5), 'hide', mirror=True, part='thigh')
    d.seg('L_Knee', knee, ankle, 11.0, 12.0, 'hide', ext=(3.0, 2.0), mirror=True, part='shin')
    d.span('L_Ankle', (34.0, -33.5, -53.5), (47.8, -22.0, -41.5), 'hide', mirror=True, part='foot')
    for k, w in ((1, 4.8), (2, 5.2), (3, 5.0), (4, 3.6)):
        a = P(f'L_Toe{k}')
        tip = np.array([a[0] + (a[0] - 42.0) * 0.3, -43.4, a[2] + 4.0])
        d.seg(f'L_Toe{k}', a + (0, 2.0, -1.0), tip + (0, 1.6, -1.5), w, 4.0, 'hide', side=(1, 0, 0), mirror=True,
              part='toe')
        d.seg(f'L_Toe{k}', tip + (0, 1.6, -1.6), tip + (0, 0.6, 2.2), w * 0.55, 1.8, 'claw', side=(1, 0, 0),
              mirror=True, part='claw', along=(0.2, 1.0))
    UPPER[:] = [(b.center[2] - b.size[2] / 2, b.center[2] + b.size[2] / 2, b.size[0] / 2) for b in d.boxes
                if b.bone == HEAD and b.mat == 'head']
    separate(d, DENSITY, step=0.06)


def crest_x(z):
    """Where the tail's paired crests stand off the midline: over the shoulder of the tail's top, drawing in."""
    return float(np.interp(z, [-175, -130, -95, -62], [2.6, 4.0, 5.5, 7.0]))


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
STRIPES = [(40.0, 3, 20.0), (18.0, 4, 26.0), (-4.0, 4, 28.0), (-26.0, 4, 28.0), (-48.0, 4, 28.0), (-70.0, 4, 30.0),
           (-92.0, 4, 30.0), (-114.0, 4, 30.0), (-136.0, 4, 30.0), (-158.0, 4, 30.0), (-180.0, 3, 30.0),
           (-202.0, 3, 30.0), (-224.0, 3, 30.0), (-246.0, 3, 30.0), (-266.0, 2, 30.0)]
RUNS = [stripe_runs(501 + k, w, 60) for k, (_, w, _) in enumerate(STRIPES)]
EYE = (96.8, 31.4)       # (z, y): the eye on the side of the skull's table
EYE_RING = (4.0, 2.6)    # the dark ring round it, (z, y) radii


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
        lip = y < np.interp(z, [68, 92, 100, 108, 120, 132], [25.4, 24.4, 21.8, 21.2, 19.8, 20.2])              # the pale lip along the upper jaw
        light = (np.floor(z / tx.texel) // 2) % 2 == 0
        colour = np.where((lip & light)[..., None], shade(pal['belly'], 0.5 + 0.1 * mottle, tx.x, tx.y), colour)
        colour = np.where((lip & ~light)[..., None], shade(pal['hide'], 0.62, tx.x, tx.y), colour)
        if part in ('orbit', 'cheek', 'cranium'):
            r = ((z - EYE[0]) / EYE_RING[0]) ** 2 + ((y - EYE[1]) / EYE_RING[1]) ** 2
            ring = (r < 1.0) & (r > 0.62)
            colour = np.where(ring[..., None], shade(pal['stripe'], 0.2, tx.x, tx.y), colour)
    if jaw and n[1] < 0.5:
        pale = (y < 15.5 - 0.1 * np.maximum(z - 100, 0)) | (n[1] < -0.5)
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
        nostril = (np.abs(np.abs(x) - 2.6) < tx.texel * 1.0) & (np.abs(z - 127.0) < tx.texel * 1.5)
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
    pitch = 8.0
    t = (z / pitch) % 1.0
    tall = np.interp(z, [-272, -250, -200, -175, -130, -62], [4.0, 7.0, 6.0, 3.5, 3.0, 2.4])
    rise = np.where(t > 0.75, (t - 0.75) / 0.25, 1 - t / 0.75)
    alpha = y <= base + 0.5 + tall * rise
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
