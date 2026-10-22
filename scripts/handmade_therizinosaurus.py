"""The Therizinosaurus, designed box by box on the ARK rig; built by scripts/handmade_models.py.

A tall feathered herbivore in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a deep
pot-bellied body tilted up toward the shoulders, carried on stout legs with four-toed feet, a feathered tail that
falls from the hips, a long neck rising to a small head with a toothless beak, and long arms whose three fingers end
in the great scythe claws. The coat is painted feather by feather, darker over the back, with a pale throat and
belly and barred feathers trailing from the forearms; the lower beak is designed shut as the idle holds it.
"""
import math

import numpy as np

from handmade_models import cells, fbm, hexes, lift, locks, noise, rotation, shade

FOLDER, STEM, HEIGHT = 'Therezinosaur', 'therezinosaur', 6.0
DENSITY = 1.0            # texels per runtime unit; the head is painted at FACE times that
FACE = 2.0
ATLAS_WIDTH = 512
PREVIEW_VARIANT = 'Natural'
ASSET = 'Therizinosaurus'
PREVIEW_CLIPS = ('Therizinosaurus-Idle', 'Therizinosaurus-Move-Fwd', 'Therizinosaurus-Attack-Claw',
                 'Therizinosaurus-Torpid-Idle')
JAW_POSE = ('Therizinosaurus-Idle', 0.0, ['jaw', 'tongue'])     # the frame the lower beak is designed in
HEAD, JAW = 'c_head', 'c_jaw'

# The body's outline along z: (z, underside, top, half width), measured on the ARK mesh's sections, the limbs left out.
BODY = [(-26.3, 7.9, 9.5, 1.0), (-24.5, 6.2, 10.8, 2.1), (-23.0, 5.9, 10.9, 2.3), (-21.5, 5.65, 10.8, 2.45),
        (-20.0, 4.95, 10.95, 2.55), (-18.5, 4.7, 11.5, 2.35), (-17.0, 4.6, 12.5, 2.4), (-15.5, 5.0, 13.8, 2.4),
        (-14.0, 5.1, 15.0, 2.35), (-12.5, 6.0, 16.35, 2.4), (-11.0, 6.45, 17.4, 2.4), (-9.5, 7.9, 18.2, 2.9),
        (-8.0, 9.7, 19.45, 3.6), (-6.5, 10.0, 20.5, 4.5), (-5.0, 9.45, 21.4, 4.7), (-3.5, 9.0, 21.95, 4.7),
        (-2.0, 9.0, 22.45, 5.0), (-0.5, 9.8, 22.85, 5.0), (1.0, 10.9, 23.4, 5.15), (2.5, 11.3, 23.8, 5.2),
        (4.0, 11.7, 24.0, 5.0), (5.5, 12.2, 24.1, 4.8), (7.0, 12.9, 24.1, 3.9), (8.5, 13.9, 24.6, 3.4)]
SEGMENTS = [('c_tail5', -26.3, -23.1), ('c_tail4', -23.4, -19.5), ('c_tail3', -19.8, -15.2), ('c_tail2', -15.5, -11.3),
            ('c_tail1', -11.6, -7.7), ('c_back1', -8.0, -4.0), ('c_back2', -4.3, -1.2), ('c_back3', -1.5, 1.75),
            ('c_back3', 1.45, 5.5), ('c_back4', 5.2, 9.2)]
TIER_SHAPE = [(0.0, 0.12, 0.5), (0.12, 0.3, 0.86), (0.3, 0.7, 1.0), (0.7, 0.88, 0.8), (0.88, 1.0, 0.45)]
# The long neck rises to the head: per bone (rear z, front z, half width), laid along its top and throat lines.
NECK = [('c_neck1', 8.9, 12.0, 3.0), ('c_neck2', 11.8, 14.2, 2.5), ('c_neck3', 14.0, 16.9, 2.35)]
NECK_TOP = ([7.0, 8.5, 10.0, 11.5, 13.0, 14.5, 16.0, 17.0], [24.1, 24.6, 26.0, 27.4, 28.9, 29.9, 30.5, 30.8])
NECK_THROAT = ([7.0, 9.0, 10.5, 12.0, 13.5, 15.0, 16.5, 17.5], [13.5, 15.3, 16.6, 18.5, 21.0, 23.5, 25.0, 25.3])


def body_at(z):
    zs = [b[0] for b in BODY]
    return tuple(np.interp(z, zs, [b[k] for b in BODY]) for k in (1, 2, 3))


def neck_section(d, bone, z0, z1, half, mat='coat'):
    """Four slabs along the neck between z0 and z1: a narrow crest on the top line, the upper and lower cores that
    meet in the middle, and a narrow keel along the throat, each sloped like its line."""
    top = lambda z: float(np.interp(z, *NECK_TOP))
    low = lambda z: float(np.interp(z, *NECK_THROAT))
    zm = (z0 + z1) / 2
    slope_top = math.atan2(top(z1) - top(z0), z1 - z0)
    slope_low = math.atan2(low(z1) - low(z0), z1 - z0)
    depth = (top(zm) - low(zm)) * math.cos((slope_top + slope_low) / 2)
    core = depth * 0.5 + 0.7
    a, b = z0 - 0.35, z1 + 0.35
    d.slab(bone, (top(z0), z0), (top(z1), z1), half * 0.6, 1.0, mat, part='neck', tier=2)
    d.slab(bone, (top(a) - 0.9, a), (top(b) - 0.9, b), half, core, mat, part='neck', tier=1)
    d.slab(bone, (low(a) + 0.9, a + 0.05), (low(b) + 0.9, b - 0.05), half * 0.93, core, mat, top=False, part='throat',
           tier=-1)
    d.slab(bone, (low(z0), z0 + 0.2), (low(z1), z1 - 0.2), half * 0.62, 1.0, mat, top=False, part='throat', tier=-2)


def tiers(d, bone, z0, z1, rows, pitch=0.0, mat='coat', part='body'):
    """Stacked boxes across a segment, turned about its middle by pitch; every box is nudged its own hair, so no two
    neighbours share a plane."""
    core = max(range(len(rows)), key=lambda k: rows[k][2])
    zm = (z0 + z1) / 2
    for k, (y0, y1, half) in enumerate(rows):
        n = len(d.boxes)
        e = 0.0 if k == core else 0.06 * (1 + abs(k - core)) + (0.03 if k > core else 0.0)
        lo = y0 - (0.05 if k > core else 0.0) - 0.017 * (n % 3)
        hi = y1 + (0.05 if k < core else 0.0) + 0.017 * (n % 4)
        half = half * (1 + 0.02 * (n % 7))
        length = (z1 - z0) / math.cos(math.radians(pitch)) - 2 * e
        d.add(bone, (0.0, (lo + hi) / 2, zm), (2 * half, hi - lo, length), rotation(pitch=pitch), mat, part=part,
              tier=k - core)


def design(d):
    rig = d.rig
    P = rig.pivot
    for bone in rig.bones:
        if bone == JAW or 'tongue' in bone:
            rig.design_as_in(bone, 'Therizinosaurus-Idle', 0.0, HEAD)

    # ---------------------------------------------------------------- body and tail, each segment pitched along its slope
    mids = []
    for bone, z0, z1 in SEGMENTS:
        low, high, _ = body_at((z0 + z1) / 2)
        mids.append(((z0 + z1) / 2, (low + high) / 2))
    for k, (bone, z0, z1) in enumerate(SEGMENTS):
        a, b = mids[max(k - 1, 0)], mids[min(k + 1, len(mids) - 1)]
        pitch = math.degrees(math.atan2(b[1] - a[1], b[0] - a[0])) * 0.8
        low, high, half = (float(v) for v in body_at((z0 + z1) / 2))
        h = (high - low) * math.cos(math.radians(pitch))
        mid = (low + high) / 2
        rows = [(mid - h / 2 + h * s, mid - h / 2 + h * t, half * w) for s, t, w in TIER_SHAPE]
        tiers(d, bone, z0, z1, rows, pitch)
    for bone, z0, z1, half in NECK:
        neck_section(d, bone, z0, z1, half)

    # ---------------------------------------------------------------- head: small, feathered, a toothless beak
    f = dict(density=FACE)
    d.span(HEAD, (-2.45, 26.8, 14.8), (2.45, 30.95, 18.0), 'coat', part='crown', **f)
    # the face round the eye: one flat side where the eye is set (build_creature_eyes footprint)
    d.span(HEAD, (-1.8, 26.6, 17.95), (1.8, 29.7, 19.4), 'face', part='face', **f)
    d.span(HEAD, (-1.3, 29.6, 18.0), (1.3, 30.4, 19.2), 'coat', part='crown', **f)
    d.span(HEAD, (-1.25, 26.5, 19.35), (1.25, 28.7, 20.3), 'face', part='snout', **f)
    d.span(HEAD, (-0.9, 26.4, 20.25), (0.9, 28.0, 21.2), 'beak', part='beak', **f)
    d.span(HEAD, (-0.55, 26.5, 21.15), (0.55, 27.5, 21.85), 'beak', part='beak', **f)
    d.span(JAW, (-1.9, 25.0, 15.8), (1.9, 26.9, 19.37), 'face', part='jaw', **f)
    d.span(JAW, (-1.15, 25.0, 19.3), (1.15, 26.65, 20.4), 'face', part='jaw', **f)
    d.span(JAW, (-0.75, 25.4, 20.32), (0.75, 26.56, 21.6), 'beak', part='lower', **f)
    d.span('c_tongue2', (-0.6, 26.62, 17.5), (0.6, 26.78, 20.0), 'mouth', part='tongue', **f)

    # ---------------------------------------------------------------- arms: feathered, three great scythe claws
    elbow, wrist = P('l_elbow'), P('l_wrist')
    d.span('l_scapula', (2.6, 15.6, 5.0), (5.4, 21.0, 9.8), 'coat', mirror=True, part='shoulder')
    d.seg('l_armTiwst', P('l_shoulder'), elbow, 1.9, 2.2, 'coat', ext=(0.5, 0.4), mirror=True, part='arm')
    d.seg('l_forearmTwist', elbow, wrist, 1.5, 1.7, 'coat', ext=(0.35, 0.25), mirror=True, part='forearm')
    d.seg('l_wrist', wrist, P('l_fingerB1'), 1.25, 1.5, 'coat', ext=(0.25, 0.15), mirror=True, part='hand')
    # the trailing feathers behind the forearm: a cut-out card hanging from it
    d.seg('l_forearmTwist', elbow + np.array([0.2, -0.9, -0.9]), wrist + np.array([0.15, -1.2, -1.0]), 0.12, 1.8,
          'vane', side=(1, 0, 0), mirror=True, part='vane', open=('up', 'down', 'north', 'south'), cutout=True,
          keep=True)
    for finger, tip, w in (('A', (3.75, 5.55, 13.5), 0.6), ('B', (3.25, 4.75, 12.4), 0.65), ('C', (2.55, 4.8, 10.6), 0.55)):
        a, b, c = (P(f'l_finger{finger}{k}') for k in (1, 2, 3))
        d.seg(f'l_finger{finger}1', a, b, w, w + 0.1, 'coat', ext=(0.2, 0.05), mirror=True, part='finger')
        d.seg(f'l_finger{finger}2', b, c, w * 0.8, w * 0.88, 'claw', ext=(0.08, 0.0), mirror=True, part='claw',
              along=(0.0, 0.65))
        d.seg(f'l_finger{finger}3', c, np.array(tip), w * 0.55, w * 0.7, 'claw', ext=(0.04, 0.0), mirror=True,
              part='claw', along=(0.65, 1.0))

    # ---------------------------------------------------------------- legs
    d.span('l_leg', (2.4, 10.6, -7.6), (6.15, 17.6, 0.3), 'coat', mirror=True, part='thigh')
    d.span('l_leg', (3.0, 17.5, -6.2), (5.6, 19.2, -1.6), 'coat', mirror=True, part='thigh')
    d.seg('l_knee', (4.2, 10.4, -1.2), (3.75, 4.5, -3.45), 3.2, 3.6, 'leg', ext=(0.2, 0.0), mirror=True, part='shin')
    d.seg('l_ankle', (3.7, 4.6, -3.7), (4.25, 1.0, -2.0), 2.3, 2.2, 'leg', mirror=True, part='meta')
    d.span('l_ankle', (2.75, 0.15, -3.3), (5.75, 1.35, -0.9), 'leg', mirror=True, part='foot')
    for name, tip, w in (('A', (4.3, 0.35, 2.8), 0.85), ('B', (6.2, 0.4, 2.65), 0.9), ('C', (7.7, 0.2, 1.1), 0.8),
                         ('D', (2.3, 0.3, 1.6), 0.75)):
        points = [P(f'l_toe{name}{k}') for k in (1, 2, 3)] + [np.array(tip)]
        for k in range(3):
            a, b = points[k], points[k + 1]
            last = k == 2
            d.seg(f'l_toe{name}{k + 1}', a, b, w * (0.55 if last else 1 - 0.1 * k), w * (0.6 if last else 1 - 0.1 * k),
                  'claw' if last else 'leg', ext=(0.06 if k else 0.15, 0.0), mirror=True,
                  part='claw' if last else 'toe', along=(0.0, 1.0))


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(coat=hexes('#2a1c10', '#3a2817', '#4b351f', '#5d4328', '#705232', '#84623d', '#987349', '#ad8556'),
                    pale=hexes('#8f7b5e', '#a08c6e', '#b19d80', '#c1ae92', '#d0bfa4', '#ddcfb6'),
                    face=hexes('#3c2c1e', '#4f3b29', '#634b35', '#785c42', '#8d6e51'),
                    beak=hexes('#2a2620', '#3b362d', '#4e483c', '#625b4d', '#776f60', '#8c8474'),
                    leg=hexes('#2a241c', '#3a3226', '#4b4232', '#5e533f', '#71644d'),
                    mouth=hexes('#3a1414', '#55201f', '#71302c', '#8c423b', '#a5574c', '#bb6e5e')),
}
PALETTES['tawny'] = PALETTES['Natural']
PALETTES['umber'] = dict(coat=hexes('#1d140d', '#2a1d13', '#38271a', '#473222', '#573e2a', '#684b34', '#7a593e', '#8c6849'),
                         pale=hexes('#76644f', '#87755f', '#988770', '#a99982', '#b9ab94', '#c8bca6'),
                         face=hexes('#2e2218', '#3e2e20', '#4f3b2a', '#614934', '#74583f'),
                         beak=hexes('#22201c', '#312e28', '#423e35', '#544f44', '#676153', '#7a7363'),
                         leg=hexes('#221d17', '#2f2820', '#3d342a', '#4c4134', '#5c4f3f'),
                         mouth=PALETTES['Natural']['mouth'])
PALETTES['slate'] = dict(coat=hexes('#18191b', '#222427', '#2e3034', '#3a3d42', '#474b51', '#555a61', '#646a72', '#747a83'),
                         pale=hexes('#7a7c80', '#8b8d91', '#9c9ea2', '#adafb3', '#bdbfc2', '#cdcfd1'),
                         face=hexes('#26272a', '#333539', '#424449', '#52545a', '#62656b'),
                         beak=hexes('#1d1d1d', '#2b2b2a', '#3a3a38', '#4b4a47', '#5c5b57', '#6e6c67'),
                         leg=hexes('#1e1f21', '#2a2b2e', '#37393c', '#46484c', '#55585c'),
                         mouth=PALETTES['Natural']['mouth'])
PALETTES['cream'] = dict(coat=hexes('#6b5a43', '#7d6b52', '#8f7d62', '#a19073', '#b3a285', '#c4b497', '#d4c5a9', '#e2d5bb'),
                         pale=hexes('#c8bba2', '#d3c7af', '#ddd3bd', '#e7decb', '#efe8d8', '#f7f2e6'),
                         face=hexes('#6e5a44', '#806b53', '#937d63', '#a68f74', '#b8a186'),
                         beak=hexes('#3c352b', '#51483a', '#675c4b', '#7e715d', '#958770', '#ac9d84'),
                         leg=hexes('#4a3f32', '#5d5040', '#71624f', '#86755f', '#9b8870'),
                         mouth=PALETTES['Natural']['mouth'])
CLAW_RAMP = hexes('#100e0b', '#1d1914', '#2b251e', '#3b3329', '#4e4436', '#655845', '#7c6d57', '#93836a')
TONGUE_RAMP = hexes('#5e2526', '#7a3333', '#954442', '#ad5853', '#c26e66')
EYE = (18.68, 28.58)       # (z, y): the eye


def feathers(tx, base, ramp, flow=None, width=3, height=2, seed=0):
    """Rows of feathers as pixel art: each lock a feather with its own tone, a lighter shaft down its middle and a
    dark rounded tip along its lower edge. base is the coat's value here (0 dark to 1 light)."""
    a, b, value = locks(tx, width, height, seed=seed, flow=flow)
    v = base + 0.12 * (value - 0.5) + 0.06 * (np.abs(a - 0.5) < 0.2) - 0.05 * b
    tip = (b > 0.72) & (np.abs(a - 0.5) < 0.45 - 0.5 * (b - 0.72))
    colour = shade(ramp, v, tx.x, tx.y, dither=0.0)
    edge = (b > 0.72) & ~tip
    return np.where(edge[..., None], shade(ramp, v - 0.3, tx.x, tx.y, dither=0.0), colour)


def coat(tx, pal):
    """The feathers: darker over the back and the crown, paler down the flanks, the throat and the belly, barred on
    the arms."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    low, high, _ = body_at(np.clip(p[..., 2], -26.3, 8.5))
    f = np.clip((p[..., 1] - low) / np.maximum(high - low, 0.5), 0, 1)
    if part in ('crown', 'neck', 'throat'):
        top, bottom = np.interp(p[..., 2], *NECK_TOP), np.interp(p[..., 2], *NECK_THROAT)
        f = np.clip((p[..., 1] - bottom) / np.maximum(top - bottom, 0.5), 0, 1)
    if part in ('shoulder', 'arm', 'forearm', 'hand', 'finger', 'thigh'):
        f = np.full(p.shape[:2], 0.6)
    base = 0.68 - 0.36 * f + 0.1 * (fbm(p, 3.0, seed=3, octaves=2) - 0.5) + 0.05 * max(n[1], 0)
    colour = feathers(tx, base, pal['coat'], seed=tx.face.index % 5)
    pale = (f < 0.32 + 0.08 * (noise(p, 2.0, seed=4) - 0.5)) | ((n[1] < -0.5) & (part in ('body', 'throat')))
    if part in ('body', 'neck', 'throat'):
        under = feathers(tx, 0.5 + 0.1 * (fbm(p, 2.0, seed=5) - 0.5), pal['pale'], seed=7)
        colour = np.where(pale[..., None], under, colour)
    if part in ('arm', 'forearm'):
        bar = (np.floor(p[..., 1] / tx.texel / 2) % 3) == 0
        colour = np.where(bar[..., None], shade(pal['coat'], 0.2, tx.x, tx.y, dither=0.0), colour)
    return colour


def vane(tx, pal):
    """The trailing feathers behind the forearm: a fringe of long barred feathers, cut out at their tips."""
    box = tx.box
    local = (tx.points - box.center) @ box.axes
    s = local[..., 1] / box.size[1] + 0.5                          # along the forearm
    t = local[..., 2] / box.size[2] + 0.5                          # out from it, 0 at the arm
    feather = np.floor(s * 7)
    within = s * 7 - feather
    length = 0.75 + 0.25 * cells(np.stack([feather, feather, feather], -1), 1.0, seed=51)
    alpha = t <= length * (1 - 0.6 * np.abs(within - 0.5) ** 2 * 4 * (t > length - 0.25))
    v = 0.35 + 0.25 * t - 0.15 * ((np.floor(t * 8) % 2) == 0) - 0.1 * (np.abs(within - 0.5) > 0.42)
    return shade(pal['coat'], v, tx.x, tx.y, dither=0.0), alpha


def face(tx, pal):
    """The bare face and jaw: fine scales, a dark ring round the eye, the gape's edge dark, the mouth inside."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    z, y = p[..., 2], p[..., 1]
    v = 0.55 + 0.1 * (fbm(p, 0.8, seed=61, octaves=2) - 0.5) + 0.12 * (cells(p, tx.texel * 1.01, seed=62) - 0.5)
    colour = shade(pal['face'], v, tx.x, tx.y, dither=0.0)
    if abs(n[0]) > 0.5:
        ring = ((z - EYE[0]) ** 2 + (y - EYE[1]) ** 2) < 0.8 ** 2
        colour = np.where(ring[..., None], shade(pal['face'], 0.2, tx.x, tx.y, dither=0.0), colour)
        gape = np.abs(y - (26.75 if part != 'jaw' else 26.8)) < 0.12
        colour = np.where(gape[..., None], shade(pal['face'], 0.05, tx.x, tx.y, dither=0.0), colour)
    if n[1] > 0.5 and part == 'jaw':
        colour = shade(pal['mouth'], 0.35, tx.x, tx.y, dither=0.0)
    if n[1] < -0.5 and part in ('face', 'snout'):
        colour = shade(pal['mouth'], 0.3, tx.x, tx.y, dither=0.0)
    return colour


def beak(tx, pal):
    """The beak: dark horn, lighter at its cutting edge and tip, the nostril high on its side."""
    p, n = tx.points, tx.normal
    z, y = p[..., 2], p[..., 1]
    v = 0.4 + 0.35 * np.clip((z - 20.2) / 1.6, 0, 1) + 0.08 * (fbm(p, 0.4, seed=71) - 0.5) + 0.05 * max(n[1], 0)
    colour = shade(pal['beak'], v, tx.x, tx.y, dither=0.0)
    if abs(n[0]) > 0.5:
        nostril = (np.abs(z - 20.55) < 0.18) & (np.abs(y - 27.55) < 0.1)
        colour = np.where(nostril[..., None], shade(pal['beak'], 0.0, tx.x, tx.y, dither=0.0), colour)
    if n[1] > 0.5 and tx.box.tag.get('part') == 'lower':
        colour = shade(pal['mouth'], 0.3, tx.x, tx.y, dither=0.0)
    return colour


def leg(tx, pal):
    """The scaly shanks, feet and toes: rows of scutes across the front, finer scales behind."""
    p, n = tx.points, tx.normal
    k = np.floor((p[..., 1] + (p[..., 2] if tx.box.tag.get('part') == 'toe' else 0)) / (tx.texel * 2))
    v = 0.5 + 0.1 * (fbm(p, 1.2, seed=41, octaves=2) - 0.5) - 0.12 * (k % 2 == 0) * (n[2] > 0.3) + 0.05 * max(n[1], 0)
    return shade(pal['leg'], v, tx.x, tx.y, dither=0.0)


def keratin(tx, ramp):
    box, n = tx.box, tx.normal
    a, b = box.tag.get('along', (0.0, 1.0))
    s = ((tx.points - box.center) @ box.axes[:, 1]) / box.size[1] + 0.5
    t = a + (b - a) * np.clip(s, 0, 1)
    v = 0.2 + 0.65 * t + 0.1 * (cells(tx.points, tx.texel * 1.01, seed=41) - 0.5) + 0.06 * max(n[1], 0)
    return shade(ramp, v, tx.x, tx.y, dither=0.0)


def painter(tx, variant):
    pal = PALETTES[variant]
    mat = tx.box.mat
    if mat == 'coat':
        return coat(tx, pal), None
    if mat == 'vane':
        return vane(tx, pal)
    if mat == 'face':
        return face(tx, pal), None
    if mat == 'beak':
        return beak(tx, pal), None
    if mat == 'leg':
        return leg(tx, pal), None
    if mat == 'claw':
        return keratin(tx, CLAW_RAMP), None
    return shade(TONGUE_RAMP, 0.55 + 0.25 * (cells(tx.points, tx.texel * 1.01, seed=43) - 0.5), tx.x, tx.y), None
