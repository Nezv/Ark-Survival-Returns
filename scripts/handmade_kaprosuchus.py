"""The Kaprosuchus, designed box by box on the ARK rig; built by scripts/handmade_models.py.

The boar crocodile in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a deep, narrow
trunk held high on upright legs, a long tail that thins to a whip and curls up at its tip, and a short, deep snout
with blade-like fangs that stand out of both jaws past the closed mouth. A pair of bony horns sweeps back from the
corners of the skull and the eyes sit high under a brow ridge. The lower jaw is designed shut (the bind pose gapes;
the idle all but shuts it). Paired rows of osteoderms run from the nape to the tail, the tail's two crests join
into one, and the skin is painted as the Sarco's: plates over the back, rows of scales down the flanks, a pale belly
of rectangular scales, dark bands across the back and rings round the tail.
"""
import math

import numpy as np

from handmade_models import cells, fbm, hexes, lift, noise, separate, shade
from handmade_tyrannosaurus import face_grid, grid, midline_top, scales, stripe_runs

FOLDER, STEM, HEIGHT = 'Kaprosuchus', 'kaprosuchus', 2.0
DENSITY = 1.0            # texels per runtime unit; the head is painted at FACE times that
FACE = 2.0
ATLAS_WIDTH = 256
PREVIEW_VARIANT = 'Natural'
ASSET = 'Kaprosuchus'
PREVIEW_CLIPS = ('Kaprosuchus-Idle', 'Kaprosuchus-Move-Fwd', 'Kaprosuchus-Attack-Bite', 'Kaprosuchus-Swim-Idle')
JAW_REFERENCE = {'c_jaw': (0.0, -27.0, 0.0)}      # the jaw shut against the upper teeth (the idle holds it at -25.9)
JAW_POSE = ('Kaprosuchus-Idle', 0.0, ['jaw', 'tongue'])
HEAD, JAW = 'c_neck3', 'c_jaw'

# The body's outline along z: (z, underside, top, half width), measured on the ARK mesh's sections, the legs and the
# osteoderms left out.
BODY = [(-41.6, 14.95, 15.15, 0.1), (-39.5, 14.32, 14.97, 0.2), (-38, 13.91, 14.77, 0.24), (-36.5, 13.54, 14.57, 0.29),
        (-35, 13.24, 14.39, 0.33), (-33.5, 12.96, 14.23, 0.37), (-32, 12.68, 14.08, 0.45), (-30.5, 12.43, 13.96, 0.52),
        (-29, 12.19, 13.88, 0.58), (-27.5, 11.97, 13.8, 0.64), (-26, 11.76, 13.73, 0.71), (-24.5, 11.51, 13.68, 0.83),
        (-23, 11.26, 13.67, 0.93), (-21.5, 11.03, 13.67, 1.0), (-20, 10.79, 13.71, 1.13), (-18.5, 10.54, 13.76, 1.24),
        (-17, 10.36, 13.88, 1.33), (-15.5, 10.19, 14.0, 1.45), (-14, 9.97, 14.16, 1.55), (-12.5, 9.77, 14.36, 1.65),
        (-11, 9.56, 14.52, 1.78), (-9.5, 9.38, 14.71, 1.89), (-8, 9.05, 14.9, 2.07), (-6.5, 8.33, 15.09, 2.29),
        (-5, 8.0, 15.24, 2.6), (-3.5, 8.01, 15.29, 2.75), (-2, 8.0, 15.13, 2.75), (-0.5, 7.72, 14.78, 2.72),
        (1, 6.69, 14.31, 2.82), (2.5, 5.91, 13.8, 3.05), (4, 5.5, 13.25, 3.15), (5.5, 5.34, 12.69, 3.1),
        (7, 5.34, 12.06, 2.87), (8.5, 5.42, 11.41, 2.6), (10, 5.73, 10.8, 2.15), (11.5, 6.1, 10.61, 1.94),
        (13, 5.92, 10.55, 1.84), (14.2, 5.9, 10.5, 1.82)]
SPINE = [('c_tail8', -41.66, -38.83), ('c_tail7', -38.83, -35.4), ('c_tail6', -35.4, -31.54),
         ('c_tail5', -31.54, -27.33), ('c_tail4', -27.33, -22.59), ('c_tail3', -22.59, -17.47),
         ('c_tail2', -17.47, -11.99), ('c_tail1', -11.99, -6.24), ('c_back1', -6.24, -0.68),
         ('c_back2', -0.68, 2.39), ('c_back3', 2.39, 5.46), ('c_back4', 5.46, 9.61), ('c_neck1', 9.61, 12.1),
         ('c_neck2', 12.1, 13.9)]
SEG = 2.6
# A tall, narrow cross-section: the flanks bulge at a third of the height, the back rounds into a ridge.
TIER_SHAPE = [(0.0, 0.12, 0.5), (0.12, 0.3, 0.86), (0.3, 0.62, 1.0), (0.62, 0.8, 0.88), (0.8, 0.92, 0.64),
              (0.92, 1.0, 0.36)]
SMALL = (0.3, 0.75)       # below these half widths a segment is one box, or three


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
        e = 0.0 if k == core else 0.025 * abs(k - core) + 0.007 * nudge
        lo = y0 - (0.03 if k > core else 0.0)
        hi = y1 + (0.03 if k < core else 0.0)
        d.span(bone, (-half, lo, z0 + e), (half, hi, z1 - e), mat, part=part, tier=k - core)


def card(d, bone, x, z0, z1, low, high, part='crest'):
    """A thin upright card standing along the top, its scutes cut out of it by the painter; moved off the midline a
    hair where one of its sides would lie in a tier's side."""
    sides = [abs(b.center[0]) + b.size[0] / 2 for b in d.boxes if b.bone == bone and b.mat == 'hide']
    while any(abs(abs(x) + s * 0.025 - edge) < 0.015 for edge in sides for s in (-1, 1)):
        x += 0.02 if x >= 0 else -0.02
    d.span(bone, (x - 0.025, low, z0), (x + 0.025, high, z1), 'crest', part=part, open=('up', 'down', 'north', 'south'),
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
            part = 'tail' if 'tail' in bone else 'neck' if 'neck' in bone else 'trunk'
            rows = [(low + h * f0, low + h * f1, half * w) for f0, f1, w in TIER_SHAPE]
            if half < SMALL[0]:
                rows = [(low, high, half)]
            elif half < SMALL[1]:
                rows = [(low, low + h * 0.25, half * 0.75), (low + h * 0.25, low + h * 0.8, half),
                        (low + h * 0.8, high, half * 0.6)]
            tiers(d, bone, z0 - 0.25, z1 + 0.12, rows, part=part, nudge=n % 5)
            n += 1


def design(d):
    rig = d.rig
    P = rig.pivot
    for bone in rig.bones:
        if bone == JAW or 'tongue' in bone:
            rig.design_as_in(bone, 'Kaprosuchus-Idle', 0.0, HEAD, JAW_REFERENCE)
    body(d)

    # ---------------------------------------------------------------- the head: a deep skull, horns, a short snout
    head = dict(density=FACE)
    d.span(HEAD, (-1.86, 7.45, 13.7), (1.86, 10.42, 16.1), 'head', part='cranium', **head)
    d.span(HEAD, (-1.55, 7.5, 15.95), (1.55, 9.95, 17.6), 'head', part='cheek', **head)
    d.span(HEAD, (-1.25, 9.9, 15.85), (1.25, 10.28, 17.45), 'head', part='brow', **head)
    d.span(HEAD, (-1.15, 7.45, 17.45), (1.15, 9.32, 19.2), 'head', part='snout', **head)
    d.span(HEAD, (-0.8, 7.32, 19.05), (0.8, 8.78, 20.65), 'head', part='snout', **head)
    d.span(HEAD, (-0.84, 6.95, 20.5), (0.84, 8.38, 21.7), 'head', part='rosette', **head)
    # the squamosal horns sweep back and out from the skull's corners
    d.seg(HEAD, (1.4, 10.0, 14.5), (2.5, 10.95, 12.5), 0.6, 0.5, 'horn', ext=(0.2, 0.0), mirror=True, part='horn',
          along=(0.0, 1.0))
    # the teeth: rows along both jaws, the fangs standing out past the shut mouth
    teeth = dict(open=('up', 'down', 'north', 'south'), cutout=True, keep=True, mirror=True, **head)
    for x, y0, y1, z0, z1 in ((1.59, 6.95, 7.6, 16.1, 17.5), (1.19, 6.85, 7.55, 17.6, 19.1),
                              (0.84, 6.8, 7.4, 19.2, 20.5)):
        d.span(HEAD, (x - 0.02, y0, z0), (x + 0.02, y1, z1), 'tooth', part='teeth', row='upper', **teeth)
    d.seg(HEAD, (0.7, 7.1, 20.95), (0.74, 6.05, 21.05), 0.16, 0.2, 'tusk', mirror=True, part='fang', along=(0.0, 1.0))
    d.seg(JAW, (0.66, 7.2, 19.5), (0.84, 8.65, 19.6), 0.18, 0.22, 'tusk', mirror=True, part='fang', along=(0.0, 1.0))
    for x, y0, y1, z0, z1 in ((1.2, 7.35, 7.95, 17.7, 19.0), (0.86, 7.3, 7.85, 19.9, 20.4)):
        d.span(JAW, (x - 0.02, y0, z0), (x + 0.02, y1, z1), 'tooth', part='teeth', row='lower', **teeth)
    d.span(JAW, (-1.78, 5.72, 13.6), (1.78, 7.5, 16.2), 'head', part='jaw', **head)
    d.span(JAW, (-1.45, 6.1, 16.05), (1.45, 7.55, 17.6), 'head', part='jaw', **head)
    d.span(JAW, (-1.05, 6.35, 17.45), (1.05, 7.5, 19.2), 'head', part='jaw', **head)
    d.span(JAW, (-0.66, 6.22, 19.05), (0.66, 7.35, 21.3), 'head', part='chin', **head)

    # ---------------------------------------------------------------- osteoderms and the tail's crests
    trunk = list(d.boxes)
    for z in np.arange(-9.0, 13.6, 1.3):
        low, high, half = body_at(z)
        rows = (0.42, 1.15) if half > 2.0 else (0.42,)
        for k, x in enumerate(rows):
            h = (0.42, 0.3)[k] * (0.8 if z > 9.6 else 1.0)
            for side in (x, -x):
                y = midline_top([_shifted(b, -side) for b in trunk], z)
                d.span(bone_at(z), (side - 0.2, y - 0.15, z - 0.38), (side + 0.2, y + h, z + 0.38), 'scute',
                       part='scute')
    for bone, a, b in SPINE:
        if 'tail' not in bone:
            continue
        z0, z1 = max(a, -40.0), b + 0.03
        lo = min(body_at(z0)[1], body_at(z1)[1]) - 0.3
        hi = max(body_at(z0)[1], body_at(z1)[1]) + 0.75
        if b > -22.0:
            x = crest_x((z0 + z1) / 2)
            card(d, bone, x, z0, z1, lo, hi)
            card(d, bone, -x, z0, z1, lo, hi)
        else:
            card(d, bone, 0.0, z0, z1, lo, hi)

    # ---------------------------------------------------------------- the upright legs, the spiked elbows and knees
    elbow, wrist = P('l_eblow'), P('l_wrist')
    d.seg('l_shoulder', P('l_shoulder') + (0.4, 0.4, 0.2), elbow, 1.75, 2.1, 'hide', ext=(0.0, 0.3), mirror=True,
          part='arm')
    d.seg('l_eblow', elbow, wrist, 1.35, 1.55, 'hide', ext=(0.25, 0.2), mirror=True, part='forearm')
    d.span('l_wrist', (2.25, 0.0, 7.75), (4.2, 0.85, 9.3), 'hide', mirror=True, part='foot')
    for finger, w in (('A', 0.45), ('B', 0.5), ('C', 0.52), ('D', 0.5), ('E', 0.45)):
        a, b = P(f'l_finger{finger}11'), P(f'l_finger{finger}12')
        tip = b + (b - a) * 0.8
        d.seg(f'l_finger{finger}11', a, b, w, 0.42, 'hide', ext=(0.15, 0.05), side=(0, 1, 0), mirror=True, part='toe')
        d.seg(f'l_finger{finger}12', b, tip, w * 0.6, 0.28, 'claw', side=(0, 1, 0), mirror=True, part='claw',
              along=(0.2, 1.0))
    knee, ankle = P('l_knee'), P('l_ankle')
    d.span('l_leg', (1.05, 7.4, -6.1), (3.75, 12.2, -1.0), 'hide', mirror=True, part='thigh')
    d.seg('l_knee', knee, ankle, 1.6, 1.85, 'hide', ext=(0.35, 0.2), mirror=True, part='shin')
    d.span('l_ankle', (2.05, 0.0, -5.1), (3.75, 0.9, -2.3), 'hide', mirror=True, part='foot')
    for toe, w in (('A', 0.42), ('B', 0.48), ('C', 0.5), ('D', 0.48), ('E', 0.42)):
        a, b = P(f'l_toe{toe}11'), P(f'l_toe{toe}12')
        tip = b + (b - a) * 0.8
        d.seg(f'l_toe{toe}11', a, b, w, 0.42, 'hide', ext=(0.15, 0.05), side=(0, 1, 0), mirror=True, part='toe')
        d.seg(f'l_toe{toe}12', b, tip, w * 0.6, 0.28, 'claw', side=(0, 1, 0), mirror=True, part='claw',
              along=(0.2, 1.0))
    # the fans of spiked osteoderms behind the elbow and the knee
    for bone, root, tips in (('l_eblow', (3.6, 5.2, 6.9), [(4.4, 5.9, 6.0), (4.6, 5.1, 5.9), (4.3, 4.4, 6.1)]),
                             ('l_knee', (3.3, 7.2, -2.9), [(4.0, 8.0, -3.9), (4.3, 7.2, -3.9), (4.0, 6.4, -3.7)])):
        for tip in tips:
            d.seg(bone, root, tip, 0.22, 0.26, 'horn', mirror=True, part='spike', along=(0.0, 1.0))
    UPPER[:] = [(b.center[2] - b.size[2] / 2, b.center[2] + b.size[2] / 2, b.size[0] / 2) for b in d.boxes
                if b.bone == HEAD and b.mat == 'head']
    separate(d, DENSITY)


def crest_x(z):
    """Where the tail's paired crests stand off the midline: over the shoulder of the tail's top, drawing in."""
    return float(np.interp(z, [-22, -14, -6], [0.35, 0.55, 0.75]))


def _shifted(box, dx):
    return type(box)(box.bone, box.center + np.array([dx, 0.0, 0.0]), box.size, box.axes, box.mat, box.tag)


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(hide=hexes('#22231a', '#2e3022', '#3b3e2b', '#494c35', '#585b40', '#686a4b', '#787a57', '#898a64'),
                    belly=hexes('#8f8566', '#a09677', '#b0a788', '#bfb799', '#cdc6aa', '#d9d3bb'),
                    stripe=hexes('#121310', '#1a1b15', '#22241b', '#2b2d22'),
                    scute=hexes('#17180f', '#202116', '#2a2c1e', '#363827', '#434531', '#51533c'),
                    horn=hexes('#2a2620', '#3b362d', '#4e483c', '#625b4d', '#776f60', '#8c8474'),
                    mouth=hexes('#3a1a18', '#552622', '#70352e', '#8b463c', '#a4594c', '#ba6d5e')),
}
PALETTES['olive'] = PALETTES['Natural']
PALETTES['umber'] = dict(hide=hexes('#22180f', '#2f2216', '#3e2d1e', '#4d3927', '#5d4631', '#6d543b', '#7e6246', '#8f7152'),
                         belly=hexes('#93805f', '#a39170', '#b2a181', '#c1b192', '#cebfa3', '#dacbb3'),
                         stripe=hexes('#0f0b07', '#16100b', '#1d160f', '#251c14'),
                         scute=hexes('#140f09', '#1e170f', '#292016', '#352a1d', '#423526', '#504130'),
                         horn=PALETTES['Natural']['horn'], mouth=PALETTES['Natural']['mouth'])
PALETTES['slate'] = dict(hide=hexes('#1b1d1d', '#252828', '#303434', '#3c4140', '#494e4c', '#565c59', '#646a66', '#737974'),
                         belly=hexes('#857f6c', '#96917e', '#a7a290', '#b7b3a2', '#c6c2b3', '#d3d0c3'),
                         stripe=hexes('#0d0e0e', '#131414', '#191b1b', '#202222'),
                         scute=hexes('#121414', '#1a1d1d', '#232727', '#2d3232', '#383e3d', '#444b49'),
                         horn=PALETTES['Natural']['horn'], mouth=PALETTES['Natural']['mouth'])
PALETTES['charcoal'] = lift(dict(hide=hexes('#0f0f0e', '#161614', '#1e1d1b', '#262522', '#2f2e2a', '#393733', '#43413c', '#4e4b45'),
                                 belly=hexes('#4a4740', '#56534b', '#625f56', '#6f6b61', '#7c776d', '#898479'),
                                 stripe=hexes('#060606', '#0a0a09', '#0f0e0d', '#141311'),
                                 scute=hexes('#090908', '#0f0f0e', '#161614', '#1e1d1b', '#272623', '#302f2b'),
                                 horn=PALETTES['Natural']['horn'], mouth=PALETTES['Natural']['mouth']), 1.25, 6)
# the hides a shade lighter, so they still read under in-game light (side faces get half of it)
for _pal in {id(p): p for p in PALETTES.values()}.values():
    _pal['hide'] = np.clip(_pal['hide'] * 1.18 + 6, 0, 255)
TOOTH_RAMP = hexes('#8a7f66', '#ab9f83', '#c9bf9f', '#e0d8bc', '#efe9d5', '#faf7ec')
CLAW_RAMP = hexes('#100e0b', '#1d1914', '#2b251e', '#3b3329', '#4e4436', '#655845')

# The dark bands across the back and round the tail, as pixel runs (see stripe_runs): (z where a band crosses the
# spine, its width in texels there, how far down the side it reaches in units).
STRIPES = [(10.5, 2, 3.0), (6.6, 3, 4.0), (2.6, 3, 4.5), (-1.4, 3, 4.5), (-5.4, 3, 4.5), (-9.4, 3, 4.0),
           (-13.4, 3, 4.0), (-17.4, 3, 4.0), (-21.4, 3, 4.0), (-25.4, 3, 4.0), (-29.4, 2, 4.0), (-33.4, 2, 4.0),
           (-37.4, 2, 4.0)]
RUNS = [stripe_runs(401 + k, w, 30) for k, (_, w, _) in enumerate(STRIPES)]
EYE = (16.68, 9.33)       # (z, y): the eye on the cheek's side face
EYE_RING = (0.85, 0.56)  # the dark ring round it, (z, y) radii


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
        lip = y < 7.74              # the pale lip along the upper jaw
        light = (np.floor(z / tx.texel) // 2) % 2 == 0
        colour = np.where((lip & light)[..., None], shade(pal['belly'], 0.5 + 0.1 * mottle, tx.x, tx.y), colour)
        colour = np.where((lip & ~light)[..., None], shade(pal['hide'], 0.62, tx.x, tx.y), colour)
        if part in ('orbit', 'cheek', 'cranium'):
            r = ((z - EYE[0]) / EYE_RING[0]) ** 2 + ((y - EYE[1]) / EYE_RING[1]) ** 2
            ring = (r < 1.0) & (r > 0.62)
            colour = np.where(ring[..., None], shade(pal['stripe'], 0.2, tx.x, tx.y), colour)
    if jaw and n[1] < 0.5:
        pale = (y < 6.85 - 0.06 * np.maximum(z - 17, 0)) | (n[1] < -0.5)
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
        nostril = (np.abs(np.abs(x) - 0.3) < tx.texel * 0.6) & (np.abs(z - 21.25) < tx.texel * 1.0)
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
    pitch = 0.95
    t = (z / pitch) % 1.0
    tall = np.interp(z, [-40, -30, -20, -12, -6], [0.25, 0.4, 0.55, 0.6, 0.5])
    rise = np.where(t > 0.75, (t - 0.75) / 0.25, 1 - t / 0.75)
    alpha = y <= base + 0.05 + tall * rise
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
