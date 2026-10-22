"""The Terror Bird, designed box by box on the ARK rig (TerrorBird); built by scripts/handmade_models.py.

A flightless predatory bird in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a
deep egg-shaped body of feathers that ends in a short drooping tail tuft, long bare scaly legs on feathered thighs
and drumsticks with three forward toes and a hind claw, small wings held out from the shoulders, and a bare neck
rising to a big head: a crown of shaggy feathers and a tall hooked beak, its lower half shut as the idle holds it.
The coat is painted feather by feather, in rows of rounded tips with dark edges, barred on the wings.
"""
import math

import numpy as np

from handmade_models import cells, fbm, hexes, lift, locks, noise, shade

FOLDER, STEM, HEIGHT = 'Terrorbird', 'terrorbird', 2.0
DENSITY = 2.0            # texels per runtime unit; the source units are large, the head is painted at FACE times that
FACE = 1.5
ATLAS_WIDTH = 512
PREVIEW_VARIANT = 'Natural'
ASSET = 'TerrorBird'
PREVIEW_CLIPS = ('TerrorBird-Idle', 'TerrorBird-Move-Fwd', 'TerrorBird-Attack-Bite', 'TerrorBird-Torpid-Idle')
JAW_POSE = ('TerrorBird-Idle', 0.0, ['jaw', 'tongue'])     # the frame the lower beak is designed in
HEAD, JAW = 'c_head', 'c_jaw'

# The body's outline along z: (z, underside, top, half width), measured on the ARK mesh's sections, wings and legs
# left out; the feathers' ragged ends lie inside it.
BODY = [(-10.4, 4.3, 4.6, 0.3), (-9.5, 3.7, 5.4, 0.85), (-8.5, 4.1, 7.6, 1.35), (-7.5, 4.6, 9.9, 1.75),
        (-6.5, 5.0, 11.45, 2.03), (-5.5, 5.2, 12.9, 2.4), (-4.5, 5.8, 13.85, 2.7), (-3.5, 6.35, 14.8, 2.86),
        (-2.5, 6.3, 15.5, 3.35), (-1.5, 6.2, 16.3, 3.55), (-0.5, 6.3, 16.7, 3.45), (0.5, 6.4, 17.05, 3.5),
        (1.5, 6.65, 17.15, 3.5), (2.5, 7.15, 17.2, 3.52), (3.5, 7.6, 17.15, 3.4), (4.5, 8.05, 17.05, 3.25),
        (5.5, 8.85, 16.8, 2.7), (6.5, 9.6, 16.3, 2.35), (7.5, 10.1, 16.2, 2.2), (8.5, 10.9, 16.6, 1.8)]
# The segments of the body: (bone, rear z, front z); each takes the outline at its middle.
SEGMENTS = [('c_tail3', -10.4, -7.4), ('c_tail2', -7.7, -5.2), ('c_tail1', -5.5, -3.2), ('c_back1', -3.5, -1.1),
            ('c_back1', -1.4, 0.75), ('c_back2', 0.45, 2.85), ('c_back3', 2.55, 4.95), ('c_back4', 4.65, 6.7),
            ('c_back4', 6.4, 8.9)]
TIER_SHAPE = [(0.0, 0.12, 0.5), (0.12, 0.3, 0.86), (0.3, 0.7, 1.0), (0.7, 0.88, 0.82), (0.88, 1.0, 0.5)]


def body_at(z):
    zs = [b[0] for b in BODY]
    return tuple(np.interp(z, zs, [b[k] for b in BODY]) for k in (1, 2, 3))


def tiers(d, bone, z0, z1, rows, mat='coat', part='body'):
    """Stacked boxes across a segment; every box is nudged its own hair, so no two neighbours share a plane."""
    core = max(range(len(rows)), key=lambda k: rows[k][2])
    for k, (y0, y1, half) in enumerate(rows):
        n = len(d.boxes)
        e = 0.0 if k == core else 0.04 * (1 + abs(k - core)) + (0.02 if k > core else 0.0)
        lo = y0 - (0.05 if k > core else 0.0) - 0.017 * (n % 3)
        hi = y1 + (0.05 if k < core else 0.0) + 0.017 * (n % 4)
        half = half * (1 + 0.02 * (n % 5))
        d.span(bone, (-half, lo, z0 + e), (half, hi, z1 - e), mat, part=part, tier=k - core)


def design(d):
    rig = d.rig
    P = rig.pivot
    for bone in rig.bones:
        if bone == JAW or 'tongue' in bone:
            rig.design_as_in(bone, 'TerrorBird-Idle', 0.0, HEAD)

    # ---------------------------------------------------------------- the feathered body and its tail tuft
    for bone, z0, z1 in SEGMENTS:
        low, high, half = body_at((z0 + z1) / 2)
        h = high - low
        rows = [(low + h * a, low + h * b, half * w) for a, b, w in TIER_SHAPE]
        tiers(d, bone, z0, z1, rows)

    # ---------------------------------------------------------------- the bare neck, along its bones
    for bone, a, b, w, dp in (('c_neck1', (0, 13.9, 8.4), (0, 16.15, 9.75), 2.38, 2.7),
                              ('c_neck2', (0, 15.95, 9.55), (0, 17.85, 10.6), 2.2, 2.45),
                              ('c_neck3', (0, 17.65, 10.45), (0, 19.0, 11.85), 2.3, 2.55),
                              ('c_neck4', (0, 18.8, 11.7), (0, 19.6, 12.7), 2.6, 2.7)):
        d.seg(bone, a, b, w, dp, 'skin', ext=(0.25, 0.2), side=(1, 0, 0), part='neck')

    # ---------------------------------------------------------------- head: a shaggy crown and the hooked beak
    f = dict(density=FACE)
    d.span(HEAD, (-1.8, 17.0, 12.35), (1.8, 20.9, 14.4), 'coat', part='crown', **f)
    # the shaggy mane down the back of the head and the nape, over the bare throat
    d.span('c_neck4', (-1.9, 18.6, 10.9), (1.9, 21.6, 12.45), 'coat', part='crown', **f)
    d.span('c_neck3', (-1.7, 17.2, 10.0), (1.7, 20.3, 11.0), 'coat', part='crown', **f)
    d.span('c_neck2', (-1.55, 15.4, 8.95), (1.55, 18.4, 10.1), 'coat', part='crown', **f)
    d.span(HEAD, (-1.15, 20.8, 12.5), (1.15, 21.75, 14.05), 'coat', part='crest', **f)
    d.span(HEAD, (-0.55, 21.65, 12.7), (0.55, 22.05, 13.55), 'coat', part='crest', **f)
    # the eye sits on the beak's broad base (build_creature_eyes footprint), a flat side over it
    d.span(HEAD, (-1.25, 17.4, 14.3), (1.25, 20.6, 15.7), 'beak', part='base', **f)
    d.span(HEAD, (-0.8, 17.15, 15.6), (0.8, 20.1, 16.6), 'beak', part='beak', **f)
    d.span(HEAD, (-0.66, 17.05, 16.5), (0.66, 19.3, 17.3), 'beak', part='beak', **f)
    d.span(HEAD, (-0.52, 16.9, 17.23), (0.52, 18.6, 17.8), 'beak', part='beak', **f)
    d.span(HEAD, (-0.32, 16.45, 17.7), (0.32, 18.15, 18.05), 'beak', part='hook', **f)

    d.span(JAW, (-1.2, 15.9, 13.0), (1.2, 17.45, 15.7), 'beak', part='lower', **f)
    d.span(JAW, (-0.7, 15.85, 15.6), (0.7, 17.2, 17.2), 'beak', part='lower', **f)
    d.span(JAW, (-0.5, 16.0, 17.1), (0.5, 17.0, 17.72), 'beak', part='lower', **f)
    d.span('c_tongue2', (-0.35, 17.08, 14.5), (0.35, 17.24, 16.8), 'mouth', part='tongue', **f)

    # ---------------------------------------------------------------- wings: a feathered arm and a cut-out fan
    d.span('l_shoulder', (2.6, 12.75, 4.3), (5.6, 14.15, 7.9), 'coat', mirror=True, part='arm')
    d.span('l_elbow', (5.35, 13.25, 3.3), (9.55, 14.05, 8.0), 'wing', mirror=True, part='wing',
           open=('north', 'south', 'east', 'west'), cutout=True, keep=True)

    # ---------------------------------------------------------------- legs: feathered thighs and drumsticks, bare shanks
    d.span('l_leg', (1.95, 8.4, -2.6), (4.15, 13.1, 3.25), 'coat', mirror=True, part='thigh')
    d.seg('l_knee', (2.4, 9.0, 1.2), (1.55, 5.4, -0.9), 1.7, 2.1, 'coat', ext=(0.3, 0.0), mirror=True, part='drumstick')
    d.seg('l_ankle', P('l_ankle'), (1.75, 0.6, 0.1), 0.72, 0.78, 'leg', ext=(0.1, 0.0), mirror=True, part='shank')
    d.span('l_ankle', (1.05, 0.0, -0.55), (2.2, 0.62, 0.6), 'leg', mirror=True, part='foot')
    for name, joints, tip, w in (('C', (1, 2, 3), (3.75, 0.02, 0.58), 0.42), ('B', (1, 2, 3), (2.36, 0.02, 2.75), 0.46),
                                 ('A', (1, 2, 3), (0.68, 0.02, 2.35), 0.42)):
        points = [P(f'l_toe{name}{k}') for k in joints] + [np.array(tip)]
        for k, joint in enumerate(joints):
            a, b = points[k], points[k + 1]
            last = k == len(joints) - 1
            d.seg(f'l_toe{name}{joint}', a, b, w * (0.55 if last else 1 - 0.08 * k), w * (0.6 if last else 1 - 0.08 * k),
                  'claw' if last else 'leg', ext=(0.06 if k else 0.12, 0.0), mirror=True, part='claw' if last else 'toe',
                  along=(0.0, 1.0))
    # the hind claw: the rig names its joints differently on each side (l_toeD2-3, r_toeD1-3)
    for side, first, sign in (('l', 'D2', 1), ('r', 'D1', -1)):
        a, b = P(f'{side}_toe{first}'), P(f'{side}_toeD3')
        tip = np.array([1.12 * sign, 0.06, -1.9])
        d.seg(f'{side}_toe{first}', a, b, 0.34, 0.34, 'leg', ext=(0.08, 0.0), part='toe')
        d.seg(f'{side}_toeD3', b, tip, 0.19, 0.2, 'claw', ext=(0.04, 0.0), part='claw', along=(0.0, 1.0))


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(coat=hexes('#1f1610', '#2d2017', '#3c2b1f', '#4c3727', '#5d4430', '#6f523a', '#826145', '#957151'),
                    pale=hexes('#7a6a56', '#8c7c67', '#9e8e79', '#b0a18b', '#c1b39e', '#d1c5b1'),
                    skin=hexes('#4a2a24', '#603630', '#77443c', '#8e544a', '#a5665a', '#b9796b'),
                    beak=hexes('#3a2f1e', '#55452b', '#73603a', '#927c4c', '#b0985f', '#cab275', '#dfc98e'),
                    leg=hexes('#2e261c', '#3f3527', '#524633', '#665840', '#7b6b4f'),
                    mouth=hexes('#3a1414', '#55201f', '#71302c', '#8c423b', '#a5574c', '#bb6e5e')),
}
PALETTES['umber'] = PALETTES['Natural']
PALETTES['slate'] = dict(coat=hexes('#18191b', '#222427', '#2e3034', '#3a3d42', '#474b51', '#555a61', '#646a72', '#747a83'),
                         pale=hexes('#6f7278', '#80838a', '#92959c', '#a4a7ad', '#b5b8bd', '#c6c8cc'),
                         skin=hexes('#3e2a2a', '#523636', '#674444', '#7d5353', '#936363', '#a87474'),
                         beak=hexes('#2a2a2a', '#3c3c3b', '#51504d', '#686661', '#807d76', '#99958c', '#b1aca1'),
                         leg=hexes('#1f2023', '#2b2d31', '#393c41', '#484b51', '#585c62'),
                         mouth=PALETTES['Natural']['mouth'])
PALETTES['black'] = lift(dict(coat=hexes('#0b0b0c', '#111113', '#18181b', '#1f2023', '#27282c', '#303135', '#393b40', '#43454b'),
                              pale=hexes('#2c2d31', '#36383c', '#414348', '#4c4f54', '#585b61', '#64676d'),
                              skin=hexes('#4a1e1e', '#622828', '#7a3333', '#933f3f', '#ab4c4c', '#c25b5b'),
                              beak=hexes('#3a3226', '#524634', '#6c5c44', '#877455', '#a28c67', '#bca57a', '#d4bd8e'),
                              leg=hexes('#141416', '#1c1d20', '#25262a', '#2f3035', '#3a3b41'),
                              mouth=PALETTES['Natural']['mouth']), 1.2, 6)
PALETTES['tawny'] = dict(coat=hexes('#3a2511', '#4e3216', '#63401c', '#795023', '#8f602b', '#a57134', '#ba833e', '#cf9649'),
                         pale=hexes('#a68c66', '#b69d77', '#c5ae89', '#d3bf9b', '#e0cfad', '#ecdfc0'),
                         skin=hexes('#5a2e22', '#733b2c', '#8c4a37', '#a55a43', '#bc6b50', '#d17e5f'),
                         beak=hexes('#4a3418', '#664a22', '#83612e', '#a07a3c', '#bc944c', '#d4ad5f', '#e8c576'),
                         leg=hexes('#3a2c1a', '#4d3b24', '#614b2f', '#765c3b', '#8b6e48'),
                         mouth=PALETTES['Natural']['mouth'])
CLAW_RAMP = hexes('#100e0b', '#1d1914', '#2b251e', '#3b3329', '#4e4436', '#655845')
TONGUE_RAMP = hexes('#5e2526', '#7a3333', '#954442', '#ad5853', '#c26e66')
EYE = (15.0, 19.6)       # (z, y): the eye, ringed by bare skin on the beak's base


def feathers(tx, base, pal, flow=None, width=3, height=2, seed=0):
    """Rows of feathers as pixel art: each lock a feather with its own tone, a lighter shaft down its middle and a
    dark rounded tip along its lower edge. base is the coat's value here (0 dark to 1 light)."""
    a, b, value = locks(tx, width, height, seed=seed, flow=flow)
    v = base + 0.12 * (value - 0.5) + 0.06 * (np.abs(a - 0.5) < 0.2) - 0.05 * b
    tip = (b > 0.72) & (np.abs(a - 0.5) < 0.45 - 0.5 * (b - 0.72))
    colour = shade(pal['coat'], v, tx.x, tx.y, dither=0.0)
    edge = (b > 0.72) & ~tip
    return np.where(edge[..., None], shade(pal['coat'], v - 0.3, tx.x, tx.y, dither=0.0), colour)


def coat(tx, pal):
    """The body's feathers: darker over the back, paler down the flanks and under the belly, barred on the wing
    coverts; the crown's shaggy feathers point back."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    low, high, _ = body_at(np.clip(p[..., 2], -10.4, 8.5))
    f = np.clip((p[..., 1] - low) / np.maximum(high - low, 0.5), 0, 1)
    if part in ('crown', 'crest'):
        f = np.clip((p[..., 1] - 17.0) / 4.5, 0, 1)
    base = 0.62 - 0.32 * f + 0.1 * (fbm(p, 2.5, seed=3, octaves=2) - 0.5) + 0.05 * max(n[1], 0)
    colour = feathers(tx, base, pal, seed=tx.face.index % 5)
    if part in ('body',):
        pale = (f < 0.3 + 0.08 * (noise(p, 1.5, seed=4) - 0.5)) | (n[1] < -0.5)
        under = feathers(tx, 0.5 + 0.1 * (fbm(p, 2.0, seed=5) - 0.5), dict(coat=pal['pale']), seed=7)
        colour = np.where(pale[..., None], under, colour)
    if part in ('arm',):
        bar = (np.floor(p[..., 0] / tx.texel / 3) % 2) == 0
        colour = np.where(bar[..., None], shade(pal['coat'], 0.25, tx.x, tx.y, dither=0.0), colour)
    return colour


def wing(tx, pal):
    """The wing's fan: long feathers out from the elbow, barred dark and light, cut out round their tips."""
    p = tx.points
    x, z = np.abs(p[..., 0]), p[..., 2]
    # the fan's outline from above: an ellipse from the elbow out to the tips, scalloped at the trailing edge
    u, v = (x - 6.9) / 2.75, (z - 5.65) / 2.45
    angle = np.arctan2(v, u)
    scallop = 0.08 * (1 - np.abs(np.cos(angle * 7)))
    alpha = (u ** 2 + v ** 2) <= (1 - scallop) ** 2
    feather = np.floor(angle / 0.21)
    tone = cells(np.stack([feather, np.zeros_like(feather), np.zeros_like(feather)], -1), 1.0, seed=31)
    r = np.sqrt(u ** 2 + v ** 2)
    value = 0.32 + 0.18 * tone + 0.15 * (r > 0.7) - 0.14 * (np.abs(angle / 0.21 - feather - 0.5) > 0.4)
    return shade(pal['coat'], value, tx.x, tx.y, dither=0.0), alpha


def skin(tx, pal):
    """The bare neck: wrinkled skin in rings round the neck, a little paler down the throat."""
    p = tx.points
    row = np.floor((p[..., 1] - p[..., 2] * 0.4) / tx.texel)
    v = 0.5 + 0.12 * (fbm(p, 0.8, seed=11, octaves=2) - 0.5) - 0.12 * (row % 4 == 0) + 0.08 * (tx.normal[2] > 0.5)
    return shade(pal['skin'], v, tx.x, tx.y, dither=0.0)


def beak(tx, pal):
    """The beak: horn, darker at its base and the hook's tip, a ridge along its top, the gape's edge dark, and the bare
    skin round the eye on the beak's base."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    z, y = p[..., 2], p[..., 1]
    t = np.clip((z - 14.3) / 3.7, 0, 1)
    v = 0.35 + 0.45 * t + 0.08 * (fbm(p, 0.6, seed=21, octaves=2) - 0.5) + 0.06 * max(n[1], 0)
    v = np.where(z > 17.75, v - 0.35 * np.clip((z - 17.75) / 0.3, 0, 1), v)      # the hook's dark tip
    colour = shade(pal['beak'], v, tx.x, tx.y, dither=0.0)
    if abs(n[0]) > 0.5:
        lip = (np.abs(y - 17.2) < 0.12) & (part in ('beak', 'base', 'hook'))
        colour = np.where(lip[..., None], shade(pal['beak'], 0.1, tx.x, tx.y, dither=0.0), colour)
        nostril = (np.abs(z - 16.3) < 0.18) & (np.abs(y - 19.2) < 0.1)
        colour = np.where(nostril[..., None], shade(pal['coat'], 0.05, tx.x, tx.y, dither=0.0), colour)
        if part == 'base':
            ring = ((z - EYE[0]) ** 2 + (y - EYE[1]) ** 2) < 0.75 ** 2
            colour = np.where(ring[..., None], shade(pal['skin'], 0.45, tx.x, tx.y, dither=0.0), colour)
    if n[1] > 0.5 and part == 'lower':
        colour = shade(pal['mouth'], 0.3 + 0.1 * (fbm(p, 0.5, seed=22) - 0.5), tx.x, tx.y, dither=0.0)
    if n[1] < -0.5 and part != 'lower':
        colour = shade(pal['mouth'], 0.35, tx.x, tx.y, dither=0.0)
    return colour


def leg(tx, pal):
    """The bare shanks and toes: rows of scutes across the front, finer scales behind."""
    p, n = tx.points, tx.normal
    k = np.floor(p[..., 1] / (tx.texel * 2) + (p[..., 2] if tx.box.tag.get('part') == 'toe' else 0) / (tx.texel * 2))
    v = 0.5 + 0.1 * (fbm(p, 0.6, seed=41, octaves=2) - 0.5) - 0.12 * (k % 2 == 0) * (n[2] > 0.3) + 0.05 * max(n[1], 0)
    return shade(pal['leg'], v, tx.x, tx.y, dither=0.0)


def keratin(tx, ramp):
    box, n = tx.box, tx.normal
    a, b = box.tag.get('along', (0.0, 1.0))
    s = ((tx.points - box.center) @ box.axes[:, 1]) / box.size[1] + 0.5
    t = a + (b - a) * np.clip(s, 0, 1)
    v = 0.25 + 0.55 * t + 0.12 * (cells(tx.points, tx.texel * 1.01, seed=41) - 0.5) + 0.06 * max(n[1], 0)
    return shade(ramp, v, tx.x, tx.y, dither=0.0)


def painter(tx, variant):
    pal = PALETTES[variant]
    mat = tx.box.mat
    if mat == 'coat':
        return coat(tx, pal), None
    if mat == 'wing':
        return wing(tx, pal)
    if mat == 'skin':
        return skin(tx, pal), None
    if mat == 'beak':
        return beak(tx, pal), None
    if mat == 'leg':
        return leg(tx, pal), None
    if mat == 'claw':
        return keratin(tx, CLAW_RAMP), None
    return shade(TONGUE_RAMP, 0.55 + 0.25 * (cells(tx.points, tx.texel * 1.01, seed=43) - 0.5), tx.x, tx.y), None
