"""The Titanoboa, designed box by box on the ARK rig; built by scripts/handmade_models.py.

The frilled giant snake in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a long,
heavy body of 25 segments, round above a flat belly, thickest past its middle and tapering to a short tail; a
wedge-shaped head, broad at the jaws behind a blunt snout, the eyes under a scaly brow, the lower jaw in its two
halves designed shut as the idle holds it and the forked tongue on its own bones. Behind the head a frill of
membrane spreads on the rig's four ribs on each side, cut to a ruffled fan with spines along its rim; the idle folds
it back along the neck. The skin is painted scale by scale: overlapping keeled scales in diagonal rows, a row of dark
saddles down the back with pale-ringed blotches along the flanks, and broad transverse scutes under the belly.
"""
import numpy as np

from handmade_dilophosaur import frill, frill_fan
from handmade_models import cells, fbm, hexes, lift, separate, shade, unit
from handmade_tyrannosaurus import face_grid, grid

FOLDER, STEM, HEIGHT = 'Titanoboa', 'titanoboa', 1.2
DENSITY = 2.0            # texels per runtime unit; a slender animal, painted finer than the big ones
FACE = 1.5               # the head and the frill at FACE times that
ATLAS_WIDTH = 512
PREVIEW_VARIANT = 'Natural'
ASSET = 'BoaFrill'
PREVIEW_CLIPS = ('BoaFrill-Idle', 'BoaFrill-Move-Fwd', 'BoaFrill-Attack-Lunge', 'BoaFrill-Startled')
JAW_POSE = ('BoaFrill-Idle', 0.0, ['jaw', 'tongue'])     # the frame the jaw is designed in: the idle shuts it
HEAD, JAW = 'c_head', 'c_jaw'

# The body's outline along z: (z, underside, top, half width), measured on the ARK mesh's sections.
BODY = [(-46.6, 0.82, 0.9, 0.05), (-46.5, 0.79, 0.93, 0.09), (-42.77, 0.31, 1.55, 0.69), (-39.04, 0.18, 2.22, 1.11),
        (-35.31, 0.12, 2.75, 1.48), (-31.58, 0.07, 3.14, 1.78), (-27.85, 0.08, 3.55, 2.07), (-24.12, 0.08, 3.89, 2.26),
        (-20.39, 0.05, 4.24, 2.41), (-16.66, 0.05, 4.55, 2.57), (-12.93, 0.06, 4.71, 2.69), (-9.2, 0.05, 4.81, 2.84),
        (-5.47, 0.04, 4.88, 2.87), (-1.74, 0.05, 4.95, 2.91), (1.99, 0.02, 4.96, 2.97), (5.72, 0.01, 4.97, 2.97),
        (9.45, -0.01, 4.93, 2.95), (13.18, -0.04, 4.89, 2.86), (16.91, -0.1, 4.8, 2.77), (20.64, -0.12, 4.65, 2.67),
        (24.37, -0.18, 4.5, 2.58), (28.1, -0.16, 4.33, 2.46), (31.83, -0.08, 4.12, 2.33), (35.56, 0.0, 3.85, 2.18),
        (39.29, 0.1, 3.59, 1.95), (42.4, 0.05, 3.33, 1.8)]
# A snake's cross-section: a flat belly, round above it.
TIER_SHAPE = [(0.0, 0.1, 0.66), (0.1, 0.3, 0.96), (0.3, 0.62, 1.0), (0.62, 0.8, 0.9), (0.8, 0.92, 0.68),
              (0.92, 1.0, 0.4)]
# The spine's bones, each carrying the body from the next one's pivot (behind it) to its own.
PIVOTS = [43.01, 39.28, 35.55, 31.82, 28.09, 24.36, 20.63, 16.89, 13.16, 9.43, 5.7, 1.97, -1.76, -5.49, -9.23,
          -12.96, -16.69, -20.42, -24.14, -27.87, -31.6, -35.33, -39.05, -42.78, -46.5]
SPINE = [(f'c_back{k + 1}', (PIVOTS[k + 1] if k + 1 < len(PIVOTS) else -46.62), front)
         for k, front in enumerate(PIVOTS)]
SPINE[0] = ('c_back1', PIVOTS[1], 42.45)
# The frill's ribs: (rib, its joint carrying the membrane, how far the membrane reaches along it), and the fan's
# centre height.
FRILL_RIBS = [('A', (1,), 3.5), ('B', (1,), 4.1), ('C', (1,), 4.6), ('D', (1,), 3.9)]
FRILL_CENTRE_Y = 1.8


def body_at(z):
    zs = [b[0] for b in BODY]
    return tuple(float(np.interp(z, zs, [b[k] for b in BODY])) for k in (1, 2, 3))


def tiers(d, bone, z0, z1, rows, mat='hide', part='trunk', nudge=0, **tag):
    """Stacked boxes across a segment, each overlapping the next a little; the tiers off the core are inset a hair
    at both ends (each segment its own hair), so no two neighbours share a plane."""
    core = max(range(len(rows)), key=lambda k: rows[k][2])
    for k, (y0, y1, half) in enumerate(rows):
        e = 0.0 if k == core else 0.02 * abs(k - core) + 0.006 * nudge
        lo = y0 - (0.02 if k > core else 0.0)
        hi = y1 + (0.02 if k < core else 0.0)
        d.span(bone, (-half, lo, z0 + e), (half, hi, z1 - e), mat, part=part, tier=k - core, **tag)


def design(d):
    rig = d.rig
    for bone in rig.bones:
        if bone in (JAW, 'l_jaw', 'r_jaw') or 'tongue' in bone:
            rig.design_as_in(bone, 'BoaFrill-Idle', 0.0, HEAD)

    # ---------------------------------------------------------------- the body, one segment per bone
    for n, (bone, a, b) in enumerate(SPINE):
        low, high, half = body_at((a + b) / 2)
        h = high - low
        rows = [(low + h * f0, low + h * f1, half * w) for f0, f1, w in TIER_SHAPE]
        if half < 0.3:
            rows = [(low, high, half)]
        elif half < 1.0:
            rows = [(low, low + h * 0.25, half * 0.8), (low + h * 0.25, low + h * 0.8, half),
                    (low + h * 0.8, high, half * 0.6)]
        tiers(d, bone, a - 0.45, b + 0.3, rows, nudge=n % 5)

    # ---------------------------------------------------------------- the head: broad jaws, a blunt snout
    head = dict(density=FACE)
    d.span(HEAD, (-1.82, 0.05, 42.3), (1.82, 3.3, 44.08), 'head', part='cranium', **head)
    d.span(HEAD, (-1.5, 1.22, 44.0), (1.5, 3.15, 44.97), 'head', part='temple', **head)
    d.span(HEAD, (-1.2, 1.56, 44.9), (1.2, 2.98, 45.86), 'head', part='temple', **head)     # the eye on its side
    d.span(HEAD, (-0.95, 1.56, 45.8), (0.95, 2.72, 46.42), 'head', part='snout', **head)
    d.span(HEAD, (-0.78, 1.52, 46.36), (0.78, 2.47, 46.96), 'head', part='tip', **head)
    d.span(HEAD, (0.55, 2.96, 44.75), (1.25, 3.1, 45.75), 'head', part='brow', mirror=True, **head)
    teeth = dict(open=('up', 'down', 'north', 'south'), cutout=True, keep=True, mirror=True, **head)
    d.span(HEAD, (1.21, 1.28, 45.0), (1.24, 1.6, 45.8), 'tooth', part='teeth', row='upper', **teeth)
    d.span(HEAD, (0.96, 1.3, 45.85), (0.99, 1.6, 46.35), 'tooth', part='teeth', row='upper', **teeth)
    # the lower jaw in its two halves, each on its own bone, meeting at the chin
    d.span('l_jaw', (-0.02, 0.12, 44.0), (1.66, 1.58, 45.0), 'head', part='jaw', mirror=True, **head)
    d.span('l_jaw', (-0.02, 0.48, 44.92), (1.16, 1.63, 45.82), 'head', part='jaw', mirror=True, **head)
    d.span('l_jaw', (-0.02, 0.78, 45.75), (0.72, 1.58, 46.62), 'head', part='chin', mirror=True, **head)
    # the forked tongue
    points = [rig.pivot(f'c_tongue{k}') for k in range(1, 6)]
    for k in range(4):
        d.seg(f'c_tongue{k + 1}', points[k], points[k + 1], 0.2 - 0.025 * k, 0.07, 'tongue', ext=(0.03, 0.03),
              side=(1, 0, 0), part='tongue')
    for side in ('l', 'r'):
        a = rig.pivot(f'{side}_tongue')
        d.seg(f'{side}_tongue', a, a + unit(a - points[-1]) * 0.45 + (0.08 if side == 'l' else -0.08, 0, 0), 0.06,
              0.05, 'tongue', ext=(0.04, 0.0), side=(1, 0, 0), part='tongue')

    # ---------------------------------------------------------------- the frill on its ribs
    frill_fan(d, FRILL_RIBS, FRILL_CENTRE_Y, bottom=0.15, spine=0.8, density=FACE)
    separate(d, DENSITY)


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(hide=hexes('#1d2214', '#283019', '#343e1f', '#414c26', '#4f5b2e', '#5e6a37', '#6e7a42', '#7f8a4e'),
                    belly=hexes('#8a8456', '#9c9666', '#ada776', '#bdb887', '#ccc798', '#d9d5aa'),
                    blot=hexes('#0d0f0a', '#14170f', '#1b1f14', '#23281a'),
                    ring=hexes('#8c8a52', '#a19f62', '#b5b273'),
                    frill=hexes('#2a0c0a', '#4a120e', '#6e1a12', '#922414', '#b33418', '#cc4c1e', '#e06a28'),
                    flash=hexes('#b07a1c', '#d09c28', '#e6bc3a', '#f2d65a', '#faeb98'),
                    mouth=hexes('#3a1a18', '#552622', '#70352e', '#8b463c', '#a4594c', '#ba6d5e')),
}
PALETTES['green'] = PALETTES['Natural']
PALETTES['olive'] = dict(hide=hexes('#22231a', '#2f3022', '#3d3e2b', '#4b4c34', '#5a5b3e', '#6a6a48', '#7a7a53', '#8b8a5f'),
                         belly=hexes('#8d8562', '#9e9672', '#afa783', '#bfb894', '#cec7a5', '#dbd5b6'),
                         blot=hexes('#0f0f0b', '#161610', '#1d1d15', '#25251b'),
                         ring=hexes('#8f8a62', '#a49f74', '#b8b385'),
                         frill=hexes('#2c160c', '#4a2210', '#6a3014', '#8c4018', '#aa541e', '#c26c26', '#d68832'),
                         flash=hexes('#a88a2a', '#c4a838', '#dac44c', '#e8d868', '#f4eaa0'),
                         mouth=PALETTES['Natural']['mouth'])
PALETTES['umber'] = dict(hide=hexes('#22180f', '#302217', '#3f2d1f', '#4f3928', '#5f4632', '#70543c', '#816247', '#937153'),
                         belly=hexes('#93805f', '#a39170', '#b2a181', '#c1b192', '#cebfa3', '#dacbb3'),
                         blot=hexes('#0f0a07', '#160f0b', '#1d150f', '#251b14'),
                         ring=hexes('#9c8a62', '#b09d74', '#c3b086'),
                         frill=hexes('#2a0e0a', '#44160e', '#621e12', '#822a16', '#a0381c', '#ba4c26', '#d06634'),
                         flash=hexes('#a8641e', '#c68228', '#dca23a', '#ecc058', '#f6dc92'),
                         mouth=PALETTES['Natural']['mouth'])
PALETTES['black'] = lift(dict(hide=hexes('#0e0f0e', '#151715', '#1c1e1c', '#242724', '#2d302d', '#363a36', '#404440', '#4a4f4a'),
                              belly=hexes('#4a4a40', '#56564b', '#636357', '#706f63', '#7d7c70', '#8a897d'),
                              blot=hexes('#050505', '#090909', '#0d0e0d', '#121312'),
                              ring=hexes('#6a6a5c', '#7c7c6c', '#8e8e7d'),
                              frill=hexes('#200808', '#360c0c', '#501210', '#6c1814', '#88221a', '#a22e22', '#ba3e2e'),
                              flash=hexes('#8a8270', '#a49c88', '#bcb5a0', '#d2ccb8', '#e6e1d0'),
                              mouth=PALETTES['Natural']['mouth']), 1.2, 5)
# the hides a shade lighter, so they still read under in-game light (side faces get half of it)
for _pal in {id(p): p for p in PALETTES.values()}.values():
    _pal['hide'] = np.clip(_pal['hide'] * 1.18 + 6, 0, 255)
TOOTH_RAMP = hexes('#8a7f66', '#ab9f83', '#c9bf9f', '#e0d8bc', '#efe9d5', '#faf7ec')
TONGUE_RAMP = hexes('#1a0c10', '#2e1218', '#481a22', '#64242e', '#80303a')
SADDLE = 2.6             # units between the dark saddles down the back
EYE = (45.38, 2.63)      # (z, y): the eye on the temple's side


def back_y(z):
    return np.interp(z, [b[0] for b in BODY], [b[2] for b in BODY])


def under_y(z):
    return np.interp(z, [b[0] for b in BODY], [b[1] for b in BODY])


def snake_scales(tx, size=2):
    """Overlapping keeled scales as pixel art: diagonal rows on the face's own texel grid, each scale `size` texels,
    the rows offset by half a scale; per texel the scale's tone, a lit keel down its middle and a dark rear edge."""
    col, row = face_grid(tx)
    r = row // size
    c = (col + (r % 2) * (size // 2 + 1)) // (size + 1)
    salt = float(tx.face.index % 997)
    tone = cells(np.stack([c.astype(float), r.astype(float), np.full(col.shape, salt)], -1), 1.0, seed=91)
    fc = (col + (r % 2) * (size // 2 + 1)) % (size + 1)
    edge = (fc == size) | (row % size == size - 1)
    keel = (fc == size // 2) & (row % size == 0)
    return 0.1 * (tone - 0.5) - 0.12 * edge + 0.06 * keel


def countershade(tx):
    p = tx.points
    top, bottom = back_y(p[..., 2]), under_y(p[..., 2])
    return np.clip((p[..., 1] - bottom) / np.maximum(top - bottom, 0.3), 0, 1)


def blotches(tx):
    """The pattern: dark saddles across the back every SADDLE units, and between them round blotches low on the
    flanks, each with a pale ring; per texel (saddle, flank blotch, ring)."""
    p = tx.points
    z, y, x = p[..., 2], p[..., 1], p[..., 0]
    f = countershade(tx)
    k = np.round(z / SADDLE)
    dz = z - k * SADDLE
    wob = 0.25 * (cells(np.stack([k, np.sign(x), np.zeros_like(k)], -1), 1.0, seed=93) - 0.5)
    saddle = (np.abs(dz + wob) < 0.62 - 0.25 * np.clip(0.78 - f, 0, 1) * 3) & (f > 0.58)
    k2 = np.round(z / SADDLE - 0.5)
    dz2 = z - (k2 + 0.5) * SADDLE
    dy2 = (f - 0.38) * 3.6
    r2 = dz2 ** 2 / 0.42 + dy2 ** 2
    flank = r2 < 1.0
    ring = (r2 >= 1.0) & (r2 < 1.7)
    return saddle, flank, ring


def hide(tx, pal):
    p, n = tx.points, tx.normal
    f = countershade(tx)
    mottle = fbm(p, 1.6, seed=94, octaves=2) - 0.5
    v = 0.66 - 0.3 * f + 0.14 * mottle + snake_scales(tx)
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    saddle, flank, ring = blotches(tx)
    dark = shade(pal['blot'], 0.5 + 0.8 * mottle + 2 * snake_scales(tx), tx.x, tx.y, dither=0.0)
    colour = np.where((saddle | flank)[..., None], dark, colour)
    colour = np.where(ring[..., None], shade(pal['ring'], 0.2 + mottle + 2 * snake_scales(tx), tx.x, tx.y,
                                             dither=0.0), colour)
    if n[1] < -0.5 or np.any(f < 0.1):
        iz, _ = grid(tx, 2)                            # the broad belly scutes, one every three texels
        belly = shade(pal['belly'], 0.62 + 0.08 * mottle - 0.25 * (iz % 3 == 0), tx.x, tx.y, dither=0.0)
        under = (f < 0.1) | (n[1] < -0.5)
        colour = np.where(under[..., None], belly, colour)
    return colour


def head(tx, pal):
    """The head: big plates over the crown and the snout, small scales on the sides, a dark stripe back from the eye
    to the jaw's corner, a pale lip and throat."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    mottle = fbm(p, 0.8, seed=95, octaves=2) - 0.5
    jaw = tx.box.bone in (JAW, 'l_jaw', 'r_jaw')
    v = 0.45 + 0.12 * mottle + snake_scales(tx, 2)
    if n[1] > 0.5:                                     # the crown's plates: a grid of big scales
        ix = np.floor((np.abs(x) + 0.001) / (tx.texel * 3)).astype(int)
        iz = np.floor(z / (tx.texel * 3)).astype(int)
        seam = ((np.abs(x) / (tx.texel * 3)) % 1 < 0.34) | ((z / (tx.texel * 3)) % 1 < 0.34)
        v = 0.42 + 0.12 * mottle + 0.1 * (cells(np.stack([ix, iz, np.zeros_like(ix)], -1).astype(float), 1.0,
                                                  seed=96) - 0.5) - 0.12 * seam
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    if abs(n[0]) > 0.5 and not jaw:
        dz, dy = z - EYE[0], y - EYE[1]
        stripe = (dz < 0.2) & (np.abs(dy + dz * 0.45) < 0.22)
        colour = np.where(stripe[..., None], shade(pal['blot'], 0.4 + mottle, tx.x, tx.y, dither=0.0), colour)
        lip = y < 1.78
        colour = np.where(lip[..., None], shade(pal['belly'], 0.5 + 0.1 * mottle, tx.x, tx.y, dither=0.0), colour)
    if jaw and n[1] < 0.5:
        colour = shade(pal['belly'], 0.55 + 0.1 * mottle + snake_scales(tx, 2), tx.x, tx.y, dither=0.0)
    if jaw and n[1] > 0.5:
        colour = shade(pal['mouth'], 0.35 + 0.1 * mottle, tx.x, tx.y)
    if not jaw and n[1] < -0.5:
        colour = shade(pal['mouth'], 0.3 + 0.1 * mottle, tx.x, tx.y) if part != 'cranium' else \
            shade(pal['belly'], 0.5 + 0.1 * mottle, tx.x, tx.y, dither=0.0)
    if part == 'tip' and abs(n[2]) > 0.5:              # the nostrils
        nostril = (np.abs(np.abs(x) - 0.38) < tx.texel * 0.8) & (np.abs(y - 2.2) < tx.texel * 0.8)
        colour = np.where(nostril[..., None], shade(pal['blot'], 0.0, tx.x, tx.y), colour)
    if part == 'brow':
        colour = shade(pal['hide'], 0.3 + 0.1 * mottle, tx.x, tx.y, dither=0.0)
    return colour


def teeth(tx):
    """Small hooked teeth along the upper jaw, every third texel."""
    box = tx.box
    p = tx.points
    a = (p[..., 2] - (box.center[2] - box.size[2] / 2)) / tx.texel
    h = (box.center[1] + box.size[1] / 2 - p[..., 1]) / tx.texel
    alpha = (np.floor(a) % 3 == 1) & (h <= box.size[1] / tx.texel * 0.9)
    return shade(TOOTH_RAMP, 0.9 - 0.3 * (h < 1), tx.x, tx.y, dither=0.0), alpha


def painter(tx, variant):
    pal = PALETTES[variant]
    mat = tx.box.mat
    if mat == 'frill':
        return frill(tx, pal, dark='blot', veins=4.0)
    if mat == 'tooth':
        return teeth(tx)
    if mat == 'head':
        return head(tx, pal), None
    if mat == 'tongue':
        return shade(TONGUE_RAMP, 0.4 + 0.4 * (cells(tx.points, tx.texel * 1.01, seed=99) - 0.5), tx.x, tx.y), None
    return hide(tx, pal), None
