"""The Pegomastax, designed box by box on the ARK rig; built by scripts/handmade_models.py.

A small bristly herbivore in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a short
round body on slim legs, a long thin tail held level, a short neck and a small head with a hooked parrot's beak,
the lower beak designed shut as the idle holds it. Bristles stand all down the back and the tail and in a tuft on
the crown; they are cut out of thin cards, a middle row and a row splayed to each side, bristle by bristle.
"""
import math

import numpy as np

from handmade_models import cells, fbm, hexes, lift, noise, separate, shade
from handmade_tyrannosaurus import face_grid, grid, midline_top, scales, stripe_runs
from handmade_velociraptor import blades, quill

FOLDER, STEM, HEIGHT = 'Pegomastax', 'pegomastax', 1.3
DENSITY = 2.0            # texels per runtime unit; a small animal, painted finer than the big ones
ATLAS_WIDTH = 256
PREVIEW_VARIANT = 'Natural'
ASSET = 'Pegomastax'
PREVIEW_CLIPS = ('Pegomastax-Idle', 'Pegomastax-Move-Fwd', 'Pegomastax-Attack-Bite', 'Pegomastax-Torpid-Idle')
JAW_POSE = ('Pegomastax-Idle', 0.0, ['jaw', 'tongue'])     # the frame the lower beak is designed in
HEAD, JAW = 'c_head', 'c_jaw'

# The body's outline along z: (z, underside, top, half width), measured on the ARK mesh's sections, the bristles and
# the limbs left out.
BODY = [(-11.6, 3.02, 3.12, 0.05), (-10.0, 2.94, 3.29, 0.11), (-8.0, 2.9, 3.41, 0.15), (-6.0, 2.87, 3.55, 0.22),
        (-4.0, 2.78, 3.73, 0.29), (-3.0, 2.69, 3.85, 0.35), (-2.0, 2.55, 4.06, 0.43), (-1.5, 2.4, 4.19, 0.51),
        (-1.0, 2.27, 4.34, 0.67), (-0.5, 2.21, 4.47, 0.76), (0.0, 2.13, 4.53, 0.74), (0.5, 2.07, 4.49, 0.76),
        (1.0, 2.08, 4.39, 0.79), (1.5, 2.11, 4.25, 0.79), (2.0, 2.06, 4.11, 0.73), (2.5, 2.09, 3.95, 0.55),
        (3.0, 2.35, 3.75, 0.42), (3.5, 2.54, 3.7, 0.36), (4.0, 2.81, 4.0, 0.36)]
SEGMENTS = [('c_tail6', -11.6, -10.55), ('c_tail5', -10.75, -9.15), ('c_tail4', -9.35, -7.55), ('c_tail3', -7.75, -5.85),
            ('c_tail2', -6.05, -3.55), ('c_tail1', -3.75, -1.25), ('c_back1', -1.45, 0.4), ('c_back2', 0.2, 1.35),
            ('c_back3', 1.15, 2.15), ('c_back4', 1.95, 3.05), ('c_neck1', 2.85, 3.5), ('c_neck2', 3.3, 3.95)]
TIER_SHAPE = [(0.0, 0.15, 0.55), (0.15, 0.75, 1.0), (0.75, 1.0, 0.6)]


def body_at(z):
    zs = [b[0] for b in BODY]
    return tuple(np.interp(z, zs, [b[k] for b in BODY]) for k in (1, 2, 3))


def tiers(d, bone, z0, z1, rows, mat='hide', part='trunk'):
    """Stacked boxes across a segment; every box is nudged its own hair, so no two neighbours share a plane."""
    core = max(range(len(rows)), key=lambda k: rows[k][2])
    for k, (y0, y1, half) in enumerate(rows):
        n = len(d.boxes)
        e = 0.0 if k == core else 0.02 * (1 + abs(k - core)) + (0.01 if k > core else 0.0)
        lo = y0 - (0.02 if k > core else 0.0) - 0.011 * (n % 3)
        hi = y1 + (0.02 if k < core else 0.0) + 0.011 * (n % 4)
        half = half * (1 + 0.03 * (n % 7))
        d.span(bone, (-half, lo, z0 + e), (half, hi, z1 - e), mat, part=part, tier=k - core)


def bristles(z0, z1, top, length, lean, seed):
    """A card's bristles: (root (y, z), tip (y, z), width), one every few hundredths along the back, leaning back."""
    rng = np.random.default_rng(seed)
    out = []
    for z in np.arange(z0 + 0.06, z1 - 0.02, 0.13):
        y = float(top(z))
        size = length(z) * (0.7 + 0.5 * rng.random())
        angle = math.radians(lean + 12 * (rng.random() - 0.5))
        out.append(((y - 0.04, float(z)), (y + size * math.cos(angle), float(z) - size * math.sin(angle)), 0.07))
    return out


def card(d, bone, blades_, x=0.0, half=0.012, roll=0.0, part='quill'):
    """A thin cut-out card in the yz plane holding the given bristles; roll splays it to the side."""
    ys = [v for root, tip, _ in blades_ for v in (root[0], tip[0])]
    zs = [v for root, tip, _ in blades_ for v in (root[1], tip[1])]
    y0, y1, z0, z1 = min(ys) - 0.06, max(ys) + 0.06, min(zs) - 0.06, max(zs) + 0.06
    return d.turned(bone, (x, (y0 + y1) / 2, (z0 + z1) / 2), (2 * half, y1 - y0, z1 - z0), roll=roll, mat='quill',
                    open=('up', 'down', 'north', 'south'), cutout=True, keep=True, density=2.0, part=part,
                    blades=blades_)


def design(d):
    rig = d.rig
    P = rig.pivot
    for bone in rig.bones:
        if bone == JAW or 'tongue' in bone:
            rig.design_as_in(bone, 'Pegomastax-Idle', 0.0, HEAD)

    # ---------------------------------------------------------------- body, tail and neck
    for bone, z0, z1 in SEGMENTS:
        low, high, half = (float(v) for v in body_at((z0 + z1) / 2))
        h = high - low
        tier = 'tail' if 'tail' in bone else 'neck' if 'neck' in bone else 'trunk'
        rows = [(low + h * a, low + h * b, half * w) for a, b, w in TIER_SHAPE]
        if half < 0.14:
            rows = [(low, high, half)]
        tiers(d, bone, z0, z1, rows, part=tier)

    # ---------------------------------------------------------------- head: a small skull and the parrot's beak
    d.span(HEAD, (-0.38, 3.25, 3.85), (0.38, 4.12, 4.62), 'head', part='cranium')
    # the face round the eye: one flat side where the eye is set (build_creature_eyes footprint)
    d.span(HEAD, (-0.33, 3.3, 4.6), (0.33, 4.02, 5.05), 'head', part='temple')
    d.span(HEAD, (-0.24, 3.3, 5.0), (0.24, 3.9, 5.43), 'beak', part='beak', along=(0.3, 0.8))
    d.span(HEAD, (-0.13, 3.08, 5.4), (0.13, 3.75, 5.62), 'beak', part='hook', along=(0.6, 1.0))
    d.span(JAW, (-0.32, 2.86, 4.1), (0.32, 3.33, 5.0), 'head', part='jaw')
    d.span(JAW, (-0.18, 2.9, 4.97), (0.18, 3.29, 5.45), 'beak', part='lower', along=(0.3, 0.9))

    # ---------------------------------------------------------------- bristles: a middle row and a splayed row each side
    body = list(d.boxes)
    top = lambda z: midline_top(body, z) if z < 3.9 else 4.1
    length = lambda z: float(np.interp(z, [-11.6, -6.0, -2.0, 0.5, 2.5, 3.6, 4.6], [0.8, 1.25, 1.5, 1.55, 1.2, 0.45, 0.6]))
    bones = [(b, z0, z1) for b, z0, z1 in SEGMENTS[:-2] if z1 - z0 > 0.5]
    for k, (bone, z0, z1) in enumerate(bones):        # neighbouring cards stand a little apart, never in one plane
        card(d, bone, bristles(z0, z1, top, length, 52, 300 + k), x=0.035 * (k % 3), half=0.012)
        for side, roll in ((1, -38.0), (-1, 38.0)):
            splay = bristles(z0 + 0.05, z1, lambda z: top(z) - 0.12, lambda z: 0.75 * length(z), 60, 400 + 7 * k + side)
            card(d, bone, splay, x=side * (0.12 + 0.035 * (k % 3)), half=0.01, roll=roll * (1 - 0.2 * (k % 2)))
    card(d, HEAD, bristles(3.9, 4.6, lambda z: 4.08, lambda z: 0.45, 40, 500), part='crest')

    # ---------------------------------------------------------------- arms and legs
    elbow, wrist = P('l_elbow'), P('l_wrist')
    d.span('l_scapula', (0.3, 2.25, 1.75), (0.95, 3.4, 3.05), 'hide', mirror=True, part='shoulder')
    d.seg('l_armTiwst', P('l_shoulder'), elbow, 0.26, 0.3, 'hide', ext=(0.08, 0.05), mirror=True, part='arm')
    d.seg('l_elbow', elbow, wrist, 0.2, 0.22, 'hide', ext=(0.04, 0.03), mirror=True, part='forearm')
    d.seg('l_wrist', wrist, P('l_fingerB1'), 0.2, 0.24, 'hide', ext=(0.03, 0.02), mirror=True, part='hand')
    for finger in ('A', 'B', 'C'):
        a, b, c = (P(f'l_finger{finger}{k}') for k in (1, 2, 3))
        tip = c + (c - b) * 1.2
        d.seg(f'l_finger{finger}1', a, c, 0.08, 0.09, 'hide', ext=(0.02, 0.0), mirror=True, part='finger')
        d.seg(f'l_finger{finger}3', c, tip, 0.05, 0.06, 'claw', mirror=True, part='claw', along=(0.0, 1.0))
    d.span('l_legTwist', (0.32, 2.1, -0.95), (0.95, 3.3, 0.7), 'hide', mirror=True, part='thigh')
    d.seg('l_knee', (0.66, 2.15, 0.1), P('l_ankle'), 0.42, 0.5, 'hide', ext=(0.0, 0.04), mirror=True, part='shin')
    d.seg('l_ankle', P('l_ankle'), (0.75, 0.2, -0.05), 0.3, 0.3, 'hide', ext=(0.04, 0.0), mirror=True, part='meta')
    for name, tip, w in (('A', (0.57, 0.04, 0.6), 0.14), ('B', (0.85, 0.04, 0.62), 0.16), ('C', (1.12, 0.04, 0.48), 0.14)):
        points = [P(f'l_toe{name}{k}') for k in (1, 2, 3)] + [np.array(tip)]
        for k in range(3):
            last = k == 2
            d.seg(f'l_toe{name}{k + 1}', points[k], points[k + 1], w * (0.6 if last else 1.0), w * (0.6 if last else 1.0),
                  'claw' if last else 'hide', ext=(0.02, 0.0), mirror=True, part='claw' if last else 'toe',
                  along=(0.0, 1.0))
    separate(d, DENSITY)


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(hide=hexes('#1d2615', '#28341c', '#344324', '#41532c', '#4f6335', '#5e743e', '#6d8548', '#7d9653'),
                    belly=hexes('#8a8d62', '#9a9d72', '#aaad83', '#babd94', '#c9cba5', '#d7d9b6'),
                    stripe=hexes('#0f140b', '#161d0f', '#1e2714', '#26311a'),
                    scute=hexes('#1d1f14', '#2a2d1d', '#383c27', '#474c31', '#575d3c'),
                    horn=hexes('#2a2620', '#3b362d', '#4e483c', '#625b4d', '#776f60', '#8c8474'),
                    quill=hexes('#3a2d1c', '#55402a', '#73573a', '#94704b', '#b38b5e', '#cfa874', '#e3c38f'),
                    mouth=hexes('#3a1414', '#55201f', '#71302c', '#8c423b', '#a5574c', '#bb6e5e')),
}
PALETTES['green'] = PALETTES['Natural']
PALETTES['olive'] = dict(hide=hexes('#22231a', '#303122', '#3f402b', '#4f5034', '#60603e', '#717148', '#838252', '#95935e'),
                         belly=hexes('#7d755a', '#8f8769', '#a19979', '#b2ab8a', '#c2bb9b', '#d1caac'),
                         stripe=hexes('#0f100b', '#161710', '#1d1e15', '#25261a'),
                         scute=hexes('#16170f', '#202116', '#2b2c1d', '#373825', '#44452e'),
                         horn=PALETTES['Natural']['horn'],
                         quill=hexes('#2e2a1c', '#463f29', '#615737', '#7d7146', '#9a8c57', '#b6a86b', '#cfc283'),
                         mouth=PALETTES['Natural']['mouth'])
PALETTES['tan'] = dict(hide=hexes('#3a2a18', '#4d3820', '#614729', '#765733', '#8b683e', '#a07a4a', '#b58c57', '#c99f65'),
                       belly=hexes('#a48d6c', '#b39d7d', '#c2ad8e', '#d0bd9f', '#dccbb0', '#e7d8c0'),
                       stripe=hexes('#21170d', '#2c1f12', '#372717', '#43301d'),
                       scute=hexes('#2b2016', '#3b2c1e', '#4c3928', '#5e4732', '#71563d'),
                       horn=PALETTES['Natural']['horn'],
                       quill=hexes('#3a1f12', '#55301a', '#734324', '#945a30', '#b3733f', '#cf9052', '#e3ad6a'),
                       mouth=PALETTES['Natural']['mouth'])
PALETTES['rust'] = dict(hide=hexes('#2b140d', '#3d1e14', '#50291b', '#643523', '#79422b', '#8e5034', '#a45f3e', '#ba6e49'),
                        belly=hexes('#8c6650', '#9d7861', '#ae8a73', '#bf9c85', '#cfae97', '#ddc0a9'),
                        stripe=hexes('#140806', '#1d0c09', '#26110c', '#30160f'),
                        scute=hexes('#1c0d09', '#28140e', '#341b13', '#422318', '#512b1e'),
                        horn=PALETTES['Natural']['horn'],
                        quill=hexes('#2a1a14', '#41271c', '#5a3626', '#744631', '#8f583d', '#aa6c4b', '#c2825c'),
                        mouth=PALETTES['Natural']['mouth'])
TOOTH_RAMP = hexes('#8a7f66', '#ab9f83', '#c9bf9f', '#e0d8bc', '#efe9d5', '#faf7ec')
CLAW_RAMP = hexes('#100e0b', '#1d1914', '#2b251e', '#3b3329', '#4e4436', '#655845')
TONGUE_RAMP = hexes('#5e2526', '#7a3333', '#954442', '#ad5853', '#c26e66')

# The body's outline along its length (z), from the tail tip to the beak: the top of the back and the underside.
BACK = ([b[0] for b in BODY] + [4.6, 5.1, 5.6], [b[2] for b in BODY] + [4.1, 3.95, 3.75])
UNDER = ([b[0] for b in BODY] + [4.6, 5.1, 5.6], [b[1] for b in BODY] + [2.86, 2.9, 3.0])
# The dark bands over the back, as pixel runs: (z where a band crosses the spine, its width in texels there, how far
# down the side it reaches in units).
STRIPES = [(2.6, 1, 0.5), (1.8, 2, 0.8), (0.9, 2, 1.0), (0.0, 2, 1.1), (-0.9, 2, 1.0), (-1.9, 2, 0.8), (-3.0, 1, 0.6),
           (-4.2, 1, 0.5), (-5.5, 1, 0.4), (-6.8, 1, 0.35), (-8.1, 1, 0.3), (-9.4, 1, 0.25), (-10.6, 1, 0.2)]
RUNS = [stripe_runs(191 + k, w, 60) for k, (_, w, _) in enumerate(STRIPES)]
EYE = (4.83, 3.82)       # (z, y): the eye, where the socket and the stripe through it are painted


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
    row of lip scales along the jaw, small knobs over the crown."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    mottle = fbm(p, 2.0, seed=23, octaves=2) - 0.5
    col, row = face_grid(tx)
    top = np.interp(z, [3.85, 4.6, 5.0, 5.4, 5.62], [4.12, 4.12, 4.02, 3.9, 3.75])
    bottom = np.interp(z, [4.1, 5.0, 5.5], [2.86, 2.9, 2.95])
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
            nostril = (np.abs(z - 37.95) < 0.45) & (np.abs(y - 30.65) < 0.2)
            colour = np.where(nostril[..., None], shade(pal['stripe'], 0.1, tx.x, tx.y), colour)
        if jaw:
            k, _ = grid(tx, 2)
            lip = row <= 1
            light = (k // 2) % 2 == 1
            colour = np.where((lip & light)[..., None], shade(pal['belly'], 0.58, tx.x, tx.y), colour)
            colour = np.where((lip & ~light)[..., None], shade(pal['hide'], 0.44, tx.x, tx.y), colour)
            pale = (row > 1) & (y < 3.0 + 0.04 * (noise(p, 0.7, seed=27) - 0.5))
            colour = np.where(pale[..., None], shade(pal['belly'], 0.52 + 0.08 * mottle + 0.5 * scales(tx, 3, seed=26),
                                                     tx.x, tx.y, dither=0.0), colour)
    if n[1] > 0.5 and part in ('cranium',):
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
    grow = np.interp(zc, [30.5, 32.0, 34.0, 36.5, 38.5], [0.6, 0.9, 1.0, 0.8, 0.6]) if along == 2 else 0.7
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
    if mat == 'quill':
        return quill(tx, pal)
    if mat == 'beak':
        return keratin(tx, pal['horn']), None
    if mat == 'scute':
        return keratin(tx, pal['scute']), None
    if mat == 'claw':
        return keratin(tx, CLAW_RAMP), None
    return shade(TONGUE_RAMP, 0.55 + 0.25 * (cells(tx.points, tx.texel * 1.01, seed=43) - 0.5), tx.x, tx.y), None
