"""The Lystrosaurus, designed box by box on the ARK rig; built by scripts/handmade_models.py.

A small stocky dicynodont in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a round
barrel of a body on short sprawling legs with broad five-toed feet, a short pointed tail, and a blunt head on a
thick neck with a horny beak, a tusk hanging at each corner of the mouth and the lower beak shut. Its skin is
smooth and wrinkled, darker over the back with pale blotches, paler underneath.
"""
import math

import numpy as np

from handmade_models import cells, fbm, hexes, lift, noise, separate, shade
from handmade_tyrannosaurus import face_grid, grid, midline_top, scales, stripe_runs

FOLDER, STEM, HEIGHT = 'Lystrosaurus', 'lystrosaurus', 1.0
DENSITY = 2.0            # texels per runtime unit; a small animal, painted finer than the big ones
ATLAS_WIDTH = 256
PREVIEW_VARIANT = 'Natural'
ASSET = 'Lystrosaurus'
PREVIEW_CLIPS = ('Lystrosaurus-Idle', 'Lystrosaurus-Move-Fwd', 'Lystrosaurus-Attack-Bite', 'Lystrosaurus-Torpid-Idle')
JAW_POSE = ('Lystrosaurus-Idle', 0.0, ['jaw', 'tongue'])     # the frame the lower beak is designed in
HEAD, JAW = 'c_neck3', 'c_jaw'

# The body's outline along z: (z, underside, top, half width), measured on the ARK mesh's sections, the legs left out.
BODY = [(-4.65, 1.35, 1.7, 0.08), (-4.2, 1.47, 2.22, 0.26), (-3.8, 1.52, 2.59, 0.41), (-3.4, 1.52, 2.88, 0.61),
        (-3.0, 1.37, 3.11, 0.81), (-2.6, 1.29, 3.32, 1.17), (-2.2, 1.14, 3.49, 1.34), (-1.8, 1.1, 3.6, 1.35),
        (-1.4, 1.05, 3.68, 1.33), (-1.0, 0.98, 3.72, 1.37), (-0.6, 0.97, 3.71, 1.42), (-0.2, 0.99, 3.68, 1.42),
        (0.2, 1.01, 3.62, 1.42), (0.6, 1.02, 3.54, 1.39), (1.0, 0.98, 3.44, 1.34), (1.4, 0.98, 3.31, 1.33),
        (1.8, 1.07, 3.28, 1.14), (2.2, 1.35, 3.34, 0.88), (2.6, 1.57, 3.3, 0.78)]
SEGMENTS = [('c_tail3', -4.65, -4.05), ('c_tail2', -4.3, -3.4), ('c_tail1', -3.65, -2.6), ('c_back1', -2.85, -1.55),
            ('c_back2', -1.8, -0.5), ('c_back3', -0.75, 0.55), ('c_back4', 0.3, 1.55), ('c_back4', 1.3, 1.95),
            ('c_neck1', 1.75, 2.45), ('c_neck2', 2.25, 2.85)]
TIER_SHAPE = [(0.0, 0.1, 0.5), (0.1, 0.3, 0.84), (0.3, 0.72, 1.0), (0.72, 0.88, 0.74), (0.88, 1.0, 0.4)]


def body_at(z):
    zs = [b[0] for b in BODY]
    return tuple(np.interp(z, zs, [b[k] for b in BODY]) for k in (1, 2, 3))


def tiers(d, bone, z0, z1, rows, mat='hide', part='trunk'):
    """Stacked boxes across a segment, each overlapping the next a little; separate() keeps them apart."""
    core = max(range(len(rows)), key=lambda k: rows[k][2])
    for k, (y0, y1, half) in enumerate(rows):
        e = 0.0 if k == core else 0.02 * (1 + abs(k - core))
        lo = y0 - (0.02 if k > core else 0.0)
        hi = y1 + (0.02 if k < core else 0.0)
        d.span(bone, (-half, lo, z0 + e), (half, hi, z1 - e), mat, part=part, tier=k - core)


def design(d):
    rig = d.rig
    P = rig.pivot
    for bone in rig.bones:
        if bone == JAW or 'tongue' in bone:
            rig.design_as_in(bone, 'Lystrosaurus-Idle', 0.0, HEAD)

    # ---------------------------------------------------------------- the barrel body, the short tail and the neck
    for bone, z0, z1 in SEGMENTS:
        low, high, half = (float(v) for v in body_at((z0 + z1) / 2))
        h = high - low
        part = 'tail' if 'tail' in bone else 'neck' if 'neck' in bone else 'trunk'
        rows = [(low + h * a, low + h * b, half * w) for a, b, w in TIER_SHAPE]
        if half < 0.3:
            rows = [(low, high, half)]
        tiers(d, bone, z0, z1, rows, part=part)

    # ---------------------------------------------------------------- head: blunt, a horny beak and two tusks
    d.span(HEAD, (-0.78, 1.85, 2.6), (0.78, 3.45, 3.4), 'head', part='cranium')
    # the face round the eye: one flat side where the eye is set (build_creature_eyes footprint), a brow above it
    d.span(HEAD, (-0.62, 2.45, 3.35), (0.62, 3.25, 3.85), 'head', part='temple')
    d.span(HEAD, (-0.45, 3.2, 3.37), (0.45, 3.38, 3.84), 'head', part='brow')
    # the snout falls steeply from the brow to a downturned beak: a slab along its ridge, the beak's front, the lip
    d.slab(HEAD, (3.32, 3.4), (2.62, 4.12), 0.52, 0.6, 'muzzle', part='ridge')
    d.slab(HEAD, (2.75, 4.08), (1.9, 4.21), 0.4, 0.35, 'muzzle', part='beak')
    d.span(HEAD, (-0.48, 2.05, 3.8), (0.48, 2.95, 4.13), 'muzzle', part='lip')
    d.seg(HEAD, (0.42, 2.25, 3.92), (0.44, 1.76, 3.98), 0.1, 0.12, 'tusk', ext=(0.04, 0.0), mirror=True, part='tusk',
          along=(0.2, 1.0))
    d.span(JAW, (-0.7, 1.82, 2.95), (0.7, 2.5, 3.85), 'head', part='jaw')
    d.span(JAW, (-0.36, 1.86, 3.8), (0.36, 2.1, 4.1), 'muzzle', part='lower')

    # ---------------------------------------------------------------- the sprawling legs and broad feet
    elbow, wrist = P('l_eblow'), P('l_wrist')
    d.seg('l_shoulder', P('l_shoulder'), elbow, 0.58, 0.62, 'hide', ext=(0.15, 0.1), mirror=True, part='arm')
    d.seg('l_eblow', elbow, wrist, 0.5, 0.55, 'hide', ext=(0.08, 0.0), mirror=True, part='forearm')
    d.span('l_wrist', (1.3, 0.0, 1.45), (2.05, 0.3, 2.15), 'hide', mirror=True, part='foot')
    for finger in ('A', 'B', 'C', 'D', 'E'):
        a, b = P(f'l_finger{finger}11'), P(f'l_finger{finger}12')
        d.seg(f'l_finger{finger}12', a, b + (b - a) * 0.4, 0.07, 0.08, 'claw', ext=(0.0, 0.0), mirror=True,
              part='claw', along=(0.2, 1.0))
    d.span('l_leg', (0.5, 1.15, -2.5), (1.75, 2.45, -1.3), 'hide', mirror=True, part='thigh')
    d.seg('l_knee', P('l_knee'), P('l_ankle'), 0.55, 0.6, 'hide', ext=(0.12, 0.05), mirror=True, part='shin')
    d.span('l_ankle', (1.0, 0.0, -2.2), (1.75, 0.3, -1.45), 'hide', mirror=True, part='foot')
    for toe in ('A', 'B', 'C', 'D', 'E'):
        a, b = P(f'l_toe{toe}11'), P(f'l_toe{toe}12')
        d.seg(f'l_toe{toe}12', a, b + (b - a) * 0.4, 0.07, 0.08, 'claw', mirror=True, part='claw', along=(0.2, 1.0))
    separate(d, DENSITY)


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(hide=hexes('#2e2418', '#3e3121', '#4f3f2b', '#614d35', '#735c40', '#866c4c', '#997c59', '#ac8d67'),
                    belly=hexes('#9c8a6c', '#ab9a7c', '#baa98d', '#c8b89e', '#d5c6af', '#e1d3bf'),
                    stripe=hexes('#1e170f', '#271e14', '#31261a', '#3b2f20'),
                    scute=hexes('#1e1710', '#2a2016', '#372a1d', '#453525', '#55422e'),
                    horn=hexes('#2a2620', '#3b362d', '#4e483c', '#625b4d', '#776f60', '#8c8474'),
                    mouth=hexes('#3a1414', '#55201f', '#71302c', '#8c423b', '#a5574c', '#bb6e5e')),
}
PALETTES['tan'] = PALETTES['Natural']
PALETTES['umber'] = dict(hide=hexes('#21170f', '#302217', '#402d1f', '#513928', '#634632', '#75543c', '#886347', '#9b7253'),
                         belly=hexes('#7a6650', '#8b7861', '#9c8a72', '#ad9c84', '#bdae96', '#ccbfa8'),
                         stripe=hexes('#0e0a07', '#150f0a', '#1c140e', '#241a12'),
                         scute=hexes('#16100b', '#211811', '#2c2017', '#38291e', '#453326'),
                         horn=PALETTES['Natural']['horn'], mouth=PALETTES['Natural']['mouth'])
PALETTES['slate'] = dict(hide=hexes('#1d1e1d', '#2a2b29', '#383936', '#474843', '#575750', '#68675f', '#79786e', '#8b897e'),
                         belly=hexes('#7f7a6c', '#8f8a7c', '#a09a8c', '#b0ab9c', '#bfbaac', '#cdc9bb'),
                         stripe=hexes('#0d0d0d', '#131313', '#1a1a19', '#22221f'),
                         scute=hexes('#141412', '#1e1e1b', '#292925', '#35352f', '#42413a'),
                         horn=PALETTES['Natural']['horn'], mouth=PALETTES['Natural']['mouth'])
PALETTES['olive'] = dict(hide=hexes('#22231a', '#303122', '#3f402b', '#4f5034', '#60603e', '#717148', '#838252', '#95935e'),
                         belly=hexes('#7d755a', '#8f8769', '#a19979', '#b2ab8a', '#c2bb9b', '#d1caac'),
                         stripe=hexes('#0f100b', '#161710', '#1d1e15', '#25261a'),
                         scute=hexes('#16170f', '#202116', '#2b2c1d', '#373825', '#44452e'),
                         horn=PALETTES['Natural']['horn'], mouth=PALETTES['Natural']['mouth'])
TOOTH_RAMP = hexes('#8a7f66', '#ab9f83', '#c9bf9f', '#e0d8bc', '#efe9d5', '#faf7ec')
CLAW_RAMP = hexes('#100e0b', '#1d1914', '#2b251e', '#3b3329', '#4e4436', '#655845')
TONGUE_RAMP = hexes('#5e2526', '#7a3333', '#954442', '#ad5853', '#c26e66')

# The body's outline along its length (z), from the tail tip to the beak: the top of the back and the underside.
BACK = ([b[0] for b in BODY] + [3.0, 3.4, 3.8, 4.27], [b[2] for b in BODY] + [3.45, 3.38, 3.1, 2.75])
UNDER = ([b[0] for b in BODY] + [3.0, 3.4, 3.8, 4.27], [b[1] for b in BODY] + [1.82, 1.82, 1.9, 2.0])
# The dark blotches over the back, as pixel-run bands: (z where a band crosses the spine, its width in texels there,
# how far down the side it reaches in units).
STRIPES = [(2.4, 2, 0.6), (1.4, 3, 1.0), (0.4, 3, 1.2), (-0.6, 3, 1.2), (-1.6, 3, 1.1), (-2.6, 2, 0.9), (-3.5, 2, 0.6)]
RUNS = [stripe_runs(211 + k, w, 60) for k, (_, w, _) in enumerate(STRIPES)]
EYE = (3.61, 2.9)       # (z, y): the eye, where the socket and the stripe through it are painted


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
    row of lip scales along the jaw, small knobs over the crown and brow."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    mottle = fbm(p, 2.0, seed=23, octaves=2) - 0.5
    col, row = face_grid(tx)
    top = np.interp(z, [2.6, 3.4, 3.85, 4.1, 4.27], [3.45, 3.45, 3.36, 3.1, 2.75])
    bottom = np.interp(z, [2.6, 3.0, 3.85, 4.08], [1.85, 1.82, 1.82, 1.9])
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
            pale = (row > 1) & (y < 2.1 + 0.04 * (noise(p, 0.7, seed=27) - 0.5))
            colour = np.where(pale[..., None], shade(pal['belly'], 0.52 + 0.08 * mottle + 0.5 * scales(tx, 3, seed=26),
                                                     tx.x, tx.y, dither=0.0), colour)
    if n[1] > 0.5 and part in ('brow', 'cranium'):
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


def muzzle(tx, pal):
    """The snout and the beak, painted in each box's own frame: wrinkled skin over the snout's ridge, two nostrils
    high on its front, the horn of the beak round the mouth with a dark rim where the skin ends, the gape dark."""
    p, n = tx.points, tx.normal
    box = tx.box
    part = box.tag.get('part')
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    local = (p - box.center) @ box.axes
    rows = box.size[1] / tx.texel
    from_bottom = (local[..., 1] / box.size[1] + 0.5) * rows
    facing = float(n @ box.axes[:, 1])
    mottle = fbm(p, 0.5, seed=81, octaves=2) - 0.5
    wrinkle = (np.floor((y + 0.3 * z) / tx.texel) % 3 == 0)
    colour = shade(pal['hide'], 0.55 + 0.15 * mottle - 0.08 * wrinkle + 0.08 * (facing > 0.5), tx.x, tx.y,
                   dither=0.0)
    horn = (y < 2.38 + 0.06 * np.sin(x * 9.0)) | (part == 'lower')
    hv = 0.7 - 0.25 * np.clip((2.38 - y) / 0.5, 0, 1) + 0.06 * mottle
    colour = np.where(horn[..., None], shade(pal['horn'], hv, tx.x, tx.y, dither=0.0), colour)
    rim = horn & (y > 2.38 - tx.texel * 1.1) & (part != 'lower')
    colour = np.where(rim[..., None], shade(pal['horn'], 0.1, tx.x, tx.y, dither=0.0), colour)
    if part == 'beak' and abs(n[2]) > 0.3:
        nostril = (np.abs(np.abs(x) - 0.17) < tx.texel * 0.5) & (np.abs(y - 2.47) < tx.texel * 0.5)
        colour = np.where(nostril[..., None], shade(pal['stripe'], 0.35, tx.x, tx.y, dither=0.0), colour)
    if part == 'lip':
        gape = from_bottom < 1.2
        colour = np.where(gape[..., None], shade(pal['stripe'], 0.1, tx.x, tx.y, dither=0.0), colour)
    if part == 'lower' and facing > 0.5:
        colour = shade(pal['mouth'], 0.35, tx.x, tx.y, dither=0.0)
    return colour


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
    if mat == 'muzzle':
        return muzzle(tx, pal), None
    if mat == 'tusk':
        return keratin(tx, TOOTH_RAMP), None
    if mat == 'scute':
        return keratin(tx, pal['scute']), None
    if mat == 'claw':
        return keratin(tx, CLAW_RAMP), None
    return shade(TONGUE_RAMP, 0.55 + 0.25 * (cells(tx.points, tx.texel * 1.01, seed=43) - 0.5), tx.x, tx.y), None
