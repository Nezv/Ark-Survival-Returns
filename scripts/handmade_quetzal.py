"""The Quetzalcoatlus, designed box by box on the ARK rig (Quetzalcoatlus); built by scripts/handmade_models.py.

The giant azhdarchid in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x), wings spread:
a deep, egg-shaped furry trunk in tiers, a long stiff neck (two crossed boxes per bone, so it reads round), and a
long skull ending in a huge toothless beak whose culmen is two sloped slabs along the measured ARK profile; the jaw
is designed shut as the flight idle holds it (on the ground it hangs a little open, as in ARK). A tall comb of quills
runs along the skull, a cut-out card traced from the ARK mesh. Each wing is built as the Pteranodon's (arm and wing
finger as tapering boxes, clawed fingers at the wrist, the membrane traced from the ARK mesh's own wing triangles as
cut-out cards, one per bone that carries part of it), and the legs end in long clawed feet. The fur is painted in
short shaggy runs, the membrane veined, the beak streaked horn with a dark tip and the comb in bright bands.
"""
import numpy as np

from ark_geometry import Skeleton
from handmade_megalodon import tiers
from handmade_models import ROOT, cells, fbm, hexes, lift, separate, shade, sheet, unit
from handmade_pteranodon import CLAW_RAMP, TONGUE_RAMP, card, limb
from handmade_tyrannosaurus import face_grid

FOLDER, STEM, HEIGHT = 'Quetzal', 'quetzal', 5.0
DENSITY = 0.5            # texels per runtime unit: a giant (wingspan 30 blocks) on a small rig
FACE = 1.6               # the head and the beak at FACE times that
ATLAS_WIDTH = 512
PREVIEW_VARIANT = 'Natural'
ASSET = 'Quetzalcoatlus'
PREVIEW_CLIPS = ('Quetzalcoatlus-Ground-Idle', 'Quetzalcoatlus-Fly-Fwd', 'Quetzalcoatlus-Ground-Platform-Move-Fwd',
                 'Quetzalcoatlus-Fly-Attack-Bite')
JAW_POSE = ('Quetzalcoatlus-Fly-Idle', 0.0, ['jaw', 'tongue'])     # the frame the jaw is designed in: shut in flight
HEAD, JAW = 'c_head', 'c_jaw'

# The trunk and the tail along z: (z, underside, top, half width), from the ARK mesh's sections without the wings.
BODY = [(-27.0, 4.0, 5.9, 0.8), (-26.0, 3.6, 6.0, 1.1), (-24.0, 2.4, 6.2, 1.55), (-22.0, 1.0, 6.3, 2.3),
        (-20.0, 0.2, 6.9, 2.9), (-18.0, -0.7, 8.0, 3.4), (-16.0, -1.4, 9.0, 4.0), (-14.0, -2.05, 10.0, 4.6),
        (-12.0, -2.5, 10.8, 4.9), (-10.0, -3.2, 11.1, 5.2), (-8.0, -3.85, 11.3, 5.5), (-6.0, -4.2, 11.2, 5.8),
        (-4.0, -4.6, 10.9, 6.0), (-2.0, -4.4, 10.4, 6.0), (0.0, -4.0, 9.8, 5.8), (1.5, -3.2, 9.3, 5.0),
        (2.6, -2.3, 8.9, 4.4), (4.0, -1.8, 7.8, 3.8)]
SHAPE = [(0.0, 0.12, 0.6), (0.12, 0.32, 0.9), (0.32, 0.68, 1.0), (0.68, 0.88, 0.85), (0.88, 1.0, 0.5)]
SLICES = [('c_tail2', -27.0, -23.0), ('c_tail2', -23.0, -19.03), ('c_tail1', -19.03, -15.78),
          ('c_back1', -15.78, -12.7), ('c_back1', -12.7, -9.68), ('c_back2', -9.68, -5.27), ('c_back3', -5.27, -0.85),
          ('c_back4', -0.85, 1.6), ('c_back4', 1.6, 4.0)]
# The neck along its middle line: (bone, from (y, z), to (y, z), width, depth).
NECK = [('c_neck1', (3.2, 2.6), (4.5, 11.9), 5.6, 6.8), ('c_neck2', (4.4, 11.5), (8.7, 19.3), 4.6, 5.4),
        ('c_neck3', (8.6, 19.0), (10.6, 25.0), 5.0, 5.8)]
# The skull: (rear z, front z, rows (bottom, top, half width)); the eye on the third's middle tier.
SKULL = [(23.4, 26.5, [(8.5, 12.0, 3.4), (12.0, 15.6, 3.0)]),
         (26.5, 29.6, [(8.6, 12.0, 3.7), (12.0, 15.9, 3.3)]),
         (29.6, 32.2, [(9.7, 12.0, 3.5), (12.0, 14.0, 3.25), (14.0, 16.0, 2.4)]),
         (32.2, 35.0, [(10.4, 13.0, 2.9), (13.0, 16.0, 2.0)])]
# The upper beak's sides from the mouth line: (rear z, front z, bottom, top, half width).
BEAK = [(35.0, 38.5, 10.55, 13.6, 2.15), (38.5, 42.0, 10.15, 12.8, 1.7), (42.0, 45.5, 9.65, 11.9, 1.28),
        (45.5, 48.5, 9.2, 11.0, 0.92), (48.5, 50.6, 8.9, 10.1, 0.6), (50.6, 51.7, 8.6, 9.3, 0.32)]
CULMEN = [((15.75, 34.6), (12.2, 44.8), 1.25, 2.0), ((12.25, 44.6), (9.25, 51.6), 0.62, 1.1)]
# The lower jaw as the flight idle holds it.
JAWS = [(27.0, 30.0, [(5.7, 8.0, 2.4), (8.0, 10.0, 3.3)]), (30.0, 33.5, [(5.9, 8.5, 2.6), (8.5, 10.8, 3.4)]),
        (33.5, 37.0, [(6.5, 8.8, 2.1), (8.8, 10.7, 2.75)]), (37.0, 40.5, [(7.3, 9.0, 1.6), (9.0, 10.4, 2.0)]),
        (40.5, 44.0, [(8.0, 10.0, 1.45)]), (44.0, 47.5, [(8.4, 9.6, 1.0)]), (47.5, 49.6, [(8.4, 9.15, 0.62)])]
SKULL_TOP = ([23.0, 30.0, 36.0, 40.0, 44.8, 47.0], [15.6, 16.0, 15.6, 14.1, 12.1, 11.4])


def body_at(z):
    zs = [b[0] for b in BODY]
    return tuple(float(np.interp(z, zs, [b[k] for b in BODY])) for k in (1, 2, 3))


def design(d):
    rig = d.rig
    P = rig.pivot
    for bone in rig.bones:
        if bone == JAW or 'tongue' in bone:
            rig.design_as_in(bone, 'Quetzalcoatlus-Fly-Idle', 0.0, HEAD)
    sk = Skeleton(ASSET, ROOT / 'Creatures' / FOLDER / 'source')
    names = np.array(sk.names)[np.asarray(sk.face_bone)]
    centroid = sk.source_vertices[np.asarray(sk.triangles)].mean(1)

    # ---------------------------------------------------------------- the trunk, the tail and the neck
    for n, (bone, a, b) in enumerate(SLICES):
        low, high, half = body_at((a + b) / 2)
        h = high - low
        rows = [(low + h * f0, low + h * f1, half * w) for f0, f1, w in SHAPE] if half > 3.5 else \
            [(low, low + h * 0.3, half * 0.8), (low + h * 0.3, low + h * 0.75, half), (low + h * 0.75, high, half * 0.6)]
        tiers(d, bone, a - 0.2, b + 0.2, rows, mat='fur', nudge=n % 5)
    for bone, (y0, z0), (y1, z1), w, depth in NECK:
        a, b = np.array([0.0, y0, z0]), np.array([0.0, y1, z1])
        d.seg(bone, a, b, w, depth * 0.72, 'fur', ext=(0.4, 0.3), side=(1, 0, 0), part='neck')
        d.seg(bone, a, b, w * 0.72, depth, 'fur', ext=(0.25, 0.15), side=(1, 0, 0), part='neck')

    # ---------------------------------------------------------------- the skull, the beak, the comb and the jaw
    head = dict(density=FACE)
    for n, (z0, z1, rows) in enumerate(SKULL):
        tiers(d, HEAD, z0, z1, rows, mat='head', part='skull', nudge=n, **head)
    for k, (z0, z1, y0, y1, half) in enumerate(BEAK):
        d.span(HEAD, (-half, y0, z0), (half, y1, z1 + 0.03 * k), 'beak', part='beak', **head)
    for k, (back, front, half, thick) in enumerate(CULMEN):
        d.slab(HEAD, back, front, half + 0.02 * k, thick, 'beak', part='culmen', **head)
    for n, (z0, z1, rows) in enumerate(JAWS):
        tiers(d, JAW, z0, z1, rows, mat='beak', part='jaw', nudge=n, **head)
    points = [P(f'c_tongue{k}') for k in range(1, 6)]
    for k in range(4):
        d.seg(f'c_tongue{k + 1}', points[k], points[k + 1], 1.6 - 0.3 * k, 0.6, 'tongue', ext=(0.1, 0.1),
              side=(1, 0, 0), part='tongue')
    top = np.interp(centroid[:, 2], *SKULL_TOP)
    comb = (names == HEAD) & (np.abs(centroid[:, 0]) < 2.6) & (centroid[:, 1] > top + 0.2) & (centroid[:, 2] < 46.0)
    sheet(d, sk, comb, [HEAD], mat='comb', res=0.15, fin='comb', thickness=0.35)

    # ---------------------------------------------------------------- the legs and the feet
    limb(d, 'l_hip', P('l_hip'), P('l_knee'), 4.2, 4.0, mat='fur', part='thigh')
    limb(d, 'l_knee', P('l_knee'), P('l_ankle'), 2.2, 2.1, part='shin')
    limb(d, 'l_ankle', P('l_ankle'), (P('l_toeB1') + P('l_toeD1')) / 2, 2.4, 1.3, part='foot')
    for t in 'ABCDE':
        a, b = P(f'l_toe{t}1'), P(f'l_toe{t}2')
        limb(d, f'l_toe{t}1', a, b, 0.7, 0.65, ext=(0.15, 0.1), part='toe')
        limb(d, f'l_toe{t}2', b, b + unit(b - a) * 1.4 + np.array([0, -0.3, 0]), 0.45, 0.5, mat='claw',
             ext=(0.05, 0.0), part='claw')

    # ---------------------------------------------------------------- the arms and the wing finger
    limb(d, 'l_shoulder', P('l_shoulder'), P('l_elbow'), 3.2, 2.6, part='arm')
    limb(d, 'l_elbow', P('l_elbow'), P('l_wrist'), 2.6, 2.2, part='arm')
    limb(d, 'l_wrist', P('l_wrist'), P('l_bigFinger1'), 2.0, 1.8, part='arm')
    finger = [P(f'l_bigFinger{k}') for k in range(1, 6)]
    for k in range(4):
        limb(d, f'l_bigFinger{k + 1}', finger[k], finger[k + 1], 1.6 - 0.25 * k, 1.2 - 0.18 * k, ext=(0.3, 0.1),
             part='finger')
    limb(d, 'l_miniFingerA3', P('l_wrist') + (P('l_miniFingerA3') - P('l_wrist')) * 0.85, P('l_miniFingerA3'),
         1.5, 1.2, part='hand')
    for f in 'ABC':
        a, b = P(f'l_miniFinger{f}1'), P(f'l_miniFinger{f}2')
        limb(d, f'l_miniFinger{f}1', a, b, 0.65, 0.6, ext=(0.1, 0.05), part='digit')
        limb(d, f'l_miniFinger{f}2', b, b + unit(b - a) * 1.3, 0.42, 0.45, mat='claw', ext=(0.05, 0.0), part='claw')

    # ---------------------------------------------------------------- the membranes, traced
    for side in ('l', 'r'):
        bones = [f'{side}_{b}' for b in ('shoulderTwist1', 'shoulderTwist2', 'shoulder', 'frontArmWing1', 'frontArmWing',
                                         'frontArmWing2', 'frontArmWing3', 'elbow', 'rearArmWing', 'wrist',
                                         'mediumFinger1', 'mediumFinger2', 'mediumFinger3', 'bigFinger1', 'wingA1',
                                         'wingA2', 'bigFinger2', 'wingB1', 'wingB2', 'bigFinger3', 'wingC1', 'wingC2',
                                         'bigFinger4', 'wingD1', 'bigFinger5', 'bodyWing', 'bodyWing2', 'bodyWing3',
                                         'legWing')]
        bones = [b for b in bones if b in sk.names]
        chosen = np.isin(names, bones) & (np.abs(centroid[:, 0]) > 6.0)
        sheet(d, sk, chosen, bones, mat='membrane', res=0.3, fin='wing', step=0.03, root=8.0, span=70.0, veins=11.0)
    separate(d, DENSITY)


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(hide=hexes('#3a3532', '#48423e', '#57504b', '#665e58', '#766d66', '#877d75', '#988e85', '#aa9f96'),
                    belly=hexes('#b8b0a6', '#c6beb4', '#d2cbc2', '#ddd7cf', '#e8e3dc'),
                    wing=hexes('#3e3632', '#4c433e', '#5b514b', '#6b6059', '#7b6f67', '#8c7f76', '#9d8f85'),
                    crest=hexes('#4a0e0e', '#741614', '#9e2418', '#c43a1e', '#e05e26', '#f08c34'),
                    beak=hexes('#4a4236', '#655a4a', '#81745f', '#9d8f77', '#b8aa90', '#d0c4aa'),
                    dark=hexes('#0d0b0a', '#151210', '#1d1916', '#26211d'),
                    mouth=hexes('#3e1a1c', '#5a2628', '#763536', '#924645', '#a95a56')),
}
PALETTES['slate'] = dict(PALETTES['Natural'],
                         hide=hexes('#2a2e33', '#363b41', '#434950', '#51585f', '#60676f', '#707880', '#808891', '#9199a2'),
                         wing=hexes('#2a2c30', '#36393e', '#43474c', '#51555b', '#5f646a', '#6e737a', '#7d838a'))
PALETTES['Natural'] = PALETTES['slate']
PALETTES['umber'] = dict(hide=hexes('#2e2016', '#3c2a1d', '#4b3525', '#5b412e', '#6b4e38', '#7c5b42', '#8d694d', '#9f7858'),
                         belly=hexes('#b09c84', '#bfac94', '#cdbba4', '#d9c9b4', '#e4d6c3'),
                         wing=hexes('#3a281c', '#4a3324', '#5a3f2d', '#6b4b36', '#7c5840', '#8d654b', '#9f7357'),
                         crest=hexes('#4a2a08', '#6e3e0c', '#945612', '#b8701c', '#d68e2c', '#ecb048'),
                         beak=hexes('#4a4236', '#655a4a', '#81745f', '#9d8f77', '#b8aa90', '#d0c4aa'),
                         dark=hexes('#0d0906', '#140e09', '#1b130d', '#231912'),
                         mouth=hexes('#3e1a1c', '#5a2628', '#763536', '#924645', '#a95a56'))
PALETTES['snow'] = dict(hide=hexes('#9a9c9e', '#a8aaac', '#b6b8ba', '#c4c6c8', '#d1d3d5', '#dcdee0', '#e6e8ea', '#eff1f2'),
                        belly=hexes('#dcdedf', '#e4e6e7', '#ebedee', '#f2f3f4', '#f8f9f9'),
                        wing=hexes('#62666c', '#777b81', '#8c9096', '#a1a5aa', '#b5b9bd', '#c8cbcf', '#d9dbde'),
                        crest=hexes('#141416', '#2e0e10', '#5a1216', '#8a1a1c', '#b82824', '#d84432'),
                        beak=hexes('#7a6a4c', '#94845f', '#ae9e76', '#c6b890', '#dacfaa', '#ebe2c4'),
                        dark=hexes('#1a1c1f', '#26292d', '#33373c', '#41464c'),
                        mouth=hexes('#3e1a1c', '#5a2628', '#763536', '#924645', '#a95a56'))
PALETTES['charcoal'] = lift(dict(hide=hexes('#141414', '#1b1b1c', '#222324', '#2a2b2d', '#333436', '#3c3e40', '#46484b', '#505356'),
                                 belly=hexes('#6e6c6a', '#7e7c79', '#8e8c89', '#9e9b98', '#adaaa6'),
                                 wing=hexes('#111112', '#18191a', '#202123', '#28292c', '#313336', '#3b3d40', '#45474b'),
                                 crest=hexes('#3a0a0c', '#5a0e12', '#7c1418', '#9e1c1e', '#be2a26', '#d63e30'),
                                 beak=hexes('#3e3934', '#514a44', '#655d55', '#797066', '#8d8378', '#a1968a'),
                                 dark=hexes('#050505', '#09090a', '#0d0d0f', '#121214'),
                                 mouth=hexes('#3e1a1c', '#5a2628', '#763536', '#924645', '#a95a56')), 1.15, 4)


def countershade(p):
    z, y = p[..., 2], p[..., 1]
    low = np.interp(z, [b[0] for b in BODY], [b[1] for b in BODY])
    high = np.interp(z, [b[0] for b in BODY], [b[2] for b in BODY])
    return np.clip((y - low) / np.maximum(high - low, 0.5), 0, 1)


def shag(tx):
    """Shaggy pycnofibres: per texel a jittered tone in runs of three down the face, the run's end darker."""
    col, row = face_grid(tx)
    salt = float(tx.face.index % 997)
    r = row // 3
    run = cells(np.stack([col.astype(float), r.astype(float), np.full(col.shape, salt)], -1), 1.0, seed=281)
    return 0.14 * (run - 0.5) - 0.07 * (row % 3 == 2)


def fur(tx, pal):
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    f = countershade(p) if part not in ('neck', 'thigh') else np.full(p.shape[:-1], 0.7)
    if n[1] > 0.5:
        f = np.maximum(f, 0.8)
    mottle = fbm(p, 6.0, seed=282, octaves=2) - 0.5
    colour = shade(pal['hide'], 0.42 + 0.12 * (1 - f) + 0.12 * mottle + shag(tx), tx.x, tx.y, dither=0.0)
    under = (n[1] < -0.5) | (f < 0.32)
    colour = np.where(under[..., None], shade(pal['belly'], 0.55 + 0.1 * mottle + shag(tx), tx.x, tx.y, dither=0.0),
                      colour)
    return colour


def hide(tx, pal):
    p = tx.points
    part = tx.box.tag.get('part')
    mottle = fbm(p, 3.0, seed=283, octaves=2) - 0.5
    if part in ('foot', 'toe', 'shin', 'hand', 'digit'):
        return shade(pal['dark'], 0.6 + 0.2 * mottle, tx.x, tx.y, dither=0.0)
    if part == 'finger':
        return shade(pal['wing'], 0.3 + 0.1 * mottle, tx.x, tx.y, dither=0.0)
    return shade(pal['hide'], 0.4 + 0.12 * mottle + shag(tx), tx.x, tx.y, dither=0.0)


def head(tx, pal):
    """The skull: short fur over the crown, bare darker skin round the eye and down to the gape."""
    p, n = tx.points, tx.normal
    mottle = fbm(p, 2.0, seed=284, octaves=2) - 0.5
    colour = shade(pal['hide'], 0.5 + 0.1 * mottle + shag(tx), tx.x, tx.y, dither=0.0)
    if abs(n[0]) > 0.5:
        bare = (p[..., 1] < 12.6) | (np.hypot(p[..., 2] - 30.9, p[..., 1] - 13.0) < 1.9)
        colour = np.where(bare[..., None], shade(pal['beak'], 0.35 + 0.1 * mottle, tx.x, tx.y, dither=0.0), colour)
    if n[1] < -0.5:
        colour = shade(pal['belly'], 0.5, tx.x, tx.y, dither=0.0)
    return colour


def beak(tx, pal):
    """Horn, streaked along its length, darkening to a black tip, a dark line along the gape; the mouth's inside
    dark red."""
    p, n = tx.points, tx.normal
    z, y = p[..., 2], p[..., 1]
    streak = cells(np.stack([np.floor(y / tx.texel), np.zeros_like(y), np.full(y.shape, float(tx.face.index % 97))],
                            -1), 1.0, seed=285) - 0.5
    v = 0.66 + 0.14 * streak - 0.55 * np.clip((z - 44.0) / 7.5, 0, 1) ** 1.5
    colour = shade(pal['beak'], v, tx.x, tx.y, dither=0.0)
    jaw = tx.box.bone == JAW
    gape = np.interp(z, [35.0, 42.0, 48.0, 51.7], [10.6, 10.0, 9.15, 8.7])
    edge = np.abs(y - gape) < 0.25
    colour = np.where((edge & (abs(n[0]) > 0.5))[..., None], shade(pal['dark'], 0.5, tx.x, tx.y, dither=0.0), colour)
    if (jaw and n[1] > 0.5) or (not jaw and n[1] < -0.5 and tx.box.tag.get('part') == 'beak'):
        colour = shade(pal['mouth'], 0.3 + 0.1 * streak, tx.x, tx.y)
    if not jaw and tx.box.tag.get('part') == 'beak' and abs(n[0]) > 0.5:
        nostril = (np.abs(z - 40.0) < 1.2) & (np.abs(y - 12.0) < tx.texel * 0.6)
        colour = np.where(nostril[..., None], shade(pal['dark'], 0.1, tx.x, tx.y, dither=0.0), colour)
    return colour


def comb(tx, pal):
    """The comb of quills: one quill every second texel along the skull, each banded and dark-tipped, the ragged top
    the ARK comb's own outline."""
    tag = tx.box.tag
    mask = tag['sheet']
    p = tx.points
    alpha = mask.at(p) == tag['owner']
    z, y = p[..., 2], p[..., 1]
    quill = np.floor(z / tx.texel)
    tone = cells(np.stack([quill, np.zeros_like(quill), np.zeros_like(quill)], -1), 1.0, seed=286) - 0.5
    base = np.interp(z, *SKULL_TOP)
    t = np.clip((y - base) / 4.5, 0, 1)
    colour = shade(pal['crest'], 0.35 + 0.55 * t + 0.2 * tone, tx.x, tx.y, dither=0.0)
    gap = (quill % 2 == 1)
    colour = np.where(gap[..., None], shade(pal['crest'], 0.15 + 0.4 * t, tx.x, tx.y, dither=0.0), colour)
    off = np.zeros(p.shape[:-1], bool)
    for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        off |= mask.at(p + (mask.u * du + mask.v * dv) * tx.texel) < 0
    colour = np.where((off & (t > 0.5))[..., None], shade(pal['dark'], 0.3, tx.x, tx.y, dither=0.0), colour)
    return colour, alpha


def painter(tx, variant):
    pal = PALETTES[variant]
    mat = tx.box.mat
    if mat == 'membrane':
        return card(tx, pal)
    if mat == 'comb':
        return comb(tx, pal)
    if mat == 'beak':
        return beak(tx, pal), None
    if mat == 'head':
        return head(tx, pal), None
    if mat == 'fur':
        return fur(tx, pal), None
    if mat == 'claw':
        return shade(CLAW_RAMP, 0.4 + 0.3 * (cells(tx.points, tx.texel, seed=287) - 0.5), tx.x, tx.y), None
    if mat == 'tongue':
        return shade(TONGUE_RAMP, 0.5, tx.x, tx.y), None
    return hide(tx, pal), None
