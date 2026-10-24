"""The Pteranodon, designed box by box on the ARK rig (Ptero); built by scripts/handmade_models.py.

The pterosaur in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x), wings spread: a small,
deep trunk in tiers on a long S-neck, a skull drawn out into a long toothed beak whose culmen is a few sloped slabs
along the measured ARK profile, and the tall blade of the crest sweeping back, a cut-out card traced from the ARK
mesh. The jaw is designed shut as the ground idle holds it. Each wing is its bones (humerus, forearm, metacarpal and
the four joints of the wing finger as tapering boxes, three clawed fingers at the wrist) and a membrane traced from
the ARK mesh's own wing triangles as cut-out cards, one per bone that carries part of it (handmade_models.sheet), so
it spreads, flaps and folds with the clips. The skin is short fuzz, darker above; the membrane is veined with fine
fibres, darkening toward the wing tips, and the crest is painted in bold bands.
"""
import numpy as np

from ark_geometry import Skeleton
from handmade_megalodon import tiers
from handmade_models import ROOT, cells, fbm, hexes, lift, separate, shade, sheet, unit
from handmade_tyrannosaurus import face_grid

FOLDER, STEM, HEIGHT = 'Piterodon', 'piterodon', 2.4
DENSITY = 1.0            # texels per runtime unit (3 runtime units per source unit)
FACE = 1.5               # the head, the beak and the teeth at FACE times that
ATLAS_WIDTH = 512
PREVIEW_VARIANT = 'Natural'
ASSET = 'Ptero'
PREVIEW_CLIPS = ('Ptero-Ground-Idle', 'Ptero-Fly-Fwd', 'Ptero-Ground-Move-Fwd', 'Ptero-Fly-Attack-Bite')
JAW_POSE = ('Ptero-Ground-Idle', 0.0, ['jaw', 'tongue'])     # the frame the jaw is designed in: the idle shuts it
HEAD, JAW = 'c_head', 'c_jaw'

# The trunk and the tail along z: (z, underside, top, half width), from the ARK mesh's sections without the wings.
BODY = [(-8.8, 0.75, 1.3, 0.5), (-7.7, 0.62, 1.95, 0.95), (-7.0, 0.6, 2.35, 1.1), (-6.4, 0.4, 2.65, 1.25),
        (-5.7, 0.2, 3.0, 1.45), (-5.0, 0.0, 3.1, 1.6), (-4.4, 0.0, 3.15, 1.65), (-3.8, 0.05, 3.2, 1.7),
        (-3.1, 0.0, 3.3, 1.9), (-2.5, -0.13, 3.47, 2.1), (-1.8, -0.3, 3.6, 2.2), (-1.2, -0.4, 3.74, 2.3),
        (-0.5, -0.42, 3.83, 2.25), (0.1, -0.37, 3.88, 2.1), (0.8, -0.3, 3.9, 1.95), (1.4, -0.15, 3.77, 1.8),
        (2.1, 0.28, 3.54, 1.5)]
SLICES = [('c_tail2', -8.9, -7.45), ('c_tail1', -7.45, -6.2), ('c_back1', -6.2, -3.55), ('c_back2', -3.55, -1.57),
          ('c_back3', -1.57, 0.41), ('c_back4', 0.41, 2.1)]
NECK = [('c_neck1', 2.3, 2.6), ('c_neck2', 2.15, 2.5), ('c_neck3', 1.95, 2.3)]      # (bone, width, depth)
# The skull behind the beak: (rear z, front z, rows (bottom, top, half width)); the eye on the second's lower tier.
SKULL = [(4.4, 5.3, [(3.3, 5.8, 1.25), (5.8, 8.4, 0.5)]),
         (5.3, 7.0, [(4.3, 6.0, 1.33), (6.0, 7.4, 1.05), (7.4, 8.9, 0.32)])]
# The upper beak's sides, from the tooth line up: (rear z, front z, bottom, top, half width).
BEAK = [(7.0, 8.6, 4.33, 6.1, 1.06), (8.6, 10.2, 4.37, 5.9, 0.92), (10.2, 11.8, 4.33, 5.4, 0.76),
        (11.8, 13.2, 4.25, 5.05, 0.6), (13.2, 14.4, 4.1, 4.8, 0.4), (14.4, 15.05, 3.95, 4.42, 0.2)]
# The culmen: sloped slabs along the beak's measured top edge, (back (y, z), front (y, z), half width, thickness).
CULMEN = [((8.3, 7.2), (6.82, 10.2), 0.62, 1.7), ((6.85, 10.1), (5.17, 12.6), 0.45, 1.1),
          ((5.2, 12.5), (4.05, 15.0), 0.28, 0.6)]
# The lower jaw as the idle holds it: (rear z, front z, rows (bottom, top, half width)).
JAWS = [(6.2, 7.8, [(2.45, 3.4, 0.95), (3.4, 4.36, 1.02)]), (7.8, 9.6, [(2.9, 3.6, 0.72), (3.6, 4.38, 0.88)]),
        (9.6, 11.4, [(3.3, 4.36, 0.72)]), (11.4, 13.0, [(3.42, 4.31, 0.55)]), (13.0, 14.6, [(3.55, 4.12, 0.3)])]
SCARS = []


def body_at(z):
    zs = [b[0] for b in BODY]
    return tuple(float(np.interp(z, zs, [b[k] for b in BODY])) for k in (1, 2, 3))


def limb(d, bone, a, b, w, depth, mat='hide', ext=(0.15, 0.1), **tag):
    d.seg(bone, a, b, w, depth, mat, ext=ext, side=(0, 1, 0) if abs(unit(b - a)[1]) < 0.9 else (1, 0, 0),
          mirror=True, **tag)


def design(d):
    rig = d.rig
    P = rig.pivot
    for bone in rig.bones:
        if bone == JAW or 'tongue' in bone:
            rig.design_as_in(bone, 'Ptero-Ground-Idle', 0.0, HEAD)
    sk = Skeleton(ASSET, ROOT / 'Creatures' / FOLDER / 'source')
    names = np.array(sk.names)[np.asarray(sk.face_bone)]
    centroid = sk.source_vertices[np.asarray(sk.triangles)].mean(1)

    # ---------------------------------------------------------------- the trunk, the tail and the neck
    for n, (bone, a, b) in enumerate(SLICES):
        low, high, half = body_at((a + b) / 2)
        h = high - low
        rows = [(low, low + h * 0.3, half * 0.8), (low + h * 0.3, low + h * 0.78, half),
                (low + h * 0.78, high, half * 0.62)] if half > 1.0 else [(low, high, half)]
        tiers(d, bone, a - 0.1, b + 0.1, rows, nudge=n % 5)
    for k, (bone, w, depth) in enumerate(NECK):
        nxt = P(NECK[k + 1][0]) if k + 1 < len(NECK) else P(HEAD)
        d.seg(bone, P(bone), nxt, w, depth, 'hide', ext=(0.35, 0.3), side=(1, 0, 0), part='neck')

    # ---------------------------------------------------------------- the skull, the beak, the crest and the jaw
    head = dict(density=FACE)
    for n, (z0, z1, rows) in enumerate(SKULL):
        tiers(d, HEAD, z0, z1, rows, mat='head', part='skull', nudge=n, **head)
    for k, (z0, z1, y0, y1, half) in enumerate(BEAK):
        d.span(HEAD, (-half, y0, z0), (half, y1, z1 + 0.02 * k), 'beak', part='beak', **head)
    for k, (back, front, half, thick) in enumerate(CULMEN):
        d.slab(HEAD, back, front, half + 0.01 * k, thick, 'beak', part='culmen', **head)
    for n, (z0, z1, rows) in enumerate(JAWS):
        tiers(d, JAW, z0, z1, rows, mat='beak', part='jaw', nudge=n, **head)
    teeth = dict(cutout=True, keep=True, mirror=True, density=FACE, part='teeth', open=('up', 'down', 'north', 'south'))
    for z0, z1, _, _, half in BEAK[1:4]:
        d.span(HEAD, (half + 0.04, 3.65, z0 + 0.1), (half + 0.07, 4.45, z1 - 0.1), 'tooth', row='upper', **teeth)
        d.span(JAW, (half + 0.11, 4.2, z0 + 0.25), (half + 0.14, 4.85, z1 - 0.2), 'tooth', row='lower', **teeth)
    points = [P(f'c_tongue{k}') for k in range(1, 6)]
    for k in range(4):
        d.seg(f'c_tongue{k + 1}', points[k], points[k + 1], 0.7 - 0.12 * k, 0.3, 'tongue', ext=(0.05, 0.05),
              side=(1, 0, 0), part='tongue')
    crest = (names == HEAD) & (np.abs(centroid[:, 0]) < 0.7) & ((centroid[:, 2] < 4.3) | (centroid[:, 1] > 7.7))
    sheet(d, sk, crest, [HEAD], mat='crest', res=0.08, fin='crest', thickness=0.12)

    # ---------------------------------------------------------------- the legs and the feet
    limb(d, 'l_hip', P('l_hip'), P('l_knee'), 1.5, 1.4, part='thigh')
    limb(d, 'l_knee', P('l_knee'), P('l_ankle'), 0.9, 0.85, part='shin')
    toes = 'ABCDE'
    limb(d, 'l_ankle', P('l_ankle'), (P('l_toeB1') + P('l_toeD1')) / 2, 1.1, 0.6, part='foot')
    for t in toes:
        a, b = P(f'l_toe{t}1'), P(f'l_toe{t}2')
        limb(d, f'l_toe{t}1', a, b, 0.32, 0.3, ext=(0.05, 0.05), part='toe')
        limb(d, f'l_toe{t}2', b, b + unit(b - a) * 0.6 + np.array([0, -0.12, 0]), 0.22, 0.24, mat='claw',
             ext=(0.03, 0.0), part='claw')

    # ---------------------------------------------------------------- the arms and the wing finger
    limb(d, 'l_shoulder', P('l_shoulder'), P('l_elbow'), 1.35, 1.2, part='arm')
    limb(d, 'l_elbow', P('l_elbow'), P('l_wrist'), 1.15, 1.0, part='arm')
    limb(d, 'l_wrist', P('l_wrist'), P('l_bigFinger1'), 0.95, 0.9, part='arm')
    finger = [P(f'l_bigFinger{k}') for k in range(1, 6)]
    finger.append(finger[-1] + unit(finger[-1] - finger[-2]) * 2.5)
    for k in range(5):
        limb(d, f'l_bigFinger{k + 1}', finger[k], finger[k + 1], 0.78 - 0.11 * k, 0.6 - 0.07 * k, ext=(0.12, 0.05),
             part='finger')
    limb(d, 'l_miniFingerA3', P('l_wrist') + (P('l_miniFingerA3') - P('l_wrist')) * 0.85, P('l_miniFingerA3'),
         0.7, 0.55, part='hand')
    for f in 'ABC':
        a, b = P(f'l_miniFinger{f}1'), P(f'l_miniFinger{f}2')
        limb(d, f'l_miniFinger{f}1', a, b, 0.3, 0.28, ext=(0.05, 0.03), part='digit')
        limb(d, f'l_miniFinger{f}2', b, b + unit(b - a) * 0.7, 0.2, 0.22, mat='claw', ext=(0.03, 0.0), part='claw')

    # ---------------------------------------------------------------- the membranes, traced
    for side in ('l', 'r'):
        bones = [f'{side}_{b}' for b in ('shoulderTwist1', 'shoulderTwist2', 'shoulder', 'frontArmWing1', 'frontArmWing',
                                         'frontArmWing2', 'frontArmWing3', 'elbow', 'rearArmWing', 'wrist',
                                         'mediumFinger1', 'mediumFinger2', 'mediumFinger3', 'bigFinger1', 'wingA1',
                                         'wingA2', 'bigFinger2', 'wingB1', 'wingB2', 'bigFinger3', 'wingC1', 'wingC2',
                                         'bigFinger4', 'wingD1', 'bigFinger5', 'bodyWing', 'bodyWing2', 'bodyWing3',
                                         'legWing')]
        bones = [b for b in bones if b in sk.names]
        chosen = np.isin(names, bones) & (np.abs(centroid[:, 0]) > 2.4)
        sheet(d, sk, chosen, bones, mat='membrane', res=0.2, fin='wing', step=0.024)
    separate(d, DENSITY)


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(hide=hexes('#22252a', '#2d3137', '#393e45', '#464c54', '#545b64', '#636b74', '#737b84', '#848c95'),
                    belly=hexes('#8e9294', '#a0a4a5', '#b2b5b5', '#c3c5c4', '#d2d4d2'),
                    wing=hexes('#2c2a2a', '#3a3634', '#4a4441', '#5a534f', '#6b635e', '#7c736d', '#8d847d'),
                    crest=hexes('#4a1210', '#6e1c14', '#94301a', '#b84a22', '#d66e2c', '#e8963c'),
                    beak=hexes('#5a5246', '#746a5a', '#8e8470', '#a89e88', '#c2b8a0'),
                    dark=hexes('#0a0b0d', '#121417', '#1a1d21', '#23272c'),
                    mouth=hexes('#3e1a1c', '#5a2628', '#763536', '#924645', '#a95a56')),
}
PALETTES['slate'] = PALETTES['Natural']
PALETTES['umber'] = dict(hide=hexes('#2a1e14', '#36281b', '#443223', '#523d2b', '#614934', '#71563e', '#816349', '#927154'),
                         belly=hexes('#a0907a', '#b0a18a', '#bfb09a', '#ccbea9', '#d8cbb7'),
                         wing=hexes('#34241a', '#443022', '#553c2b', '#664935', '#785740', '#8a654b', '#9c7457'),
                         crest=hexes('#5a2a08', '#80400c', '#a65a12', '#c8781c', '#e09a2c', '#f0bc4a'),
                         beak=PALETTES['Natural']['beak'], dark=hexes('#0d0906', '#140e09', '#1b130d', '#231912'),
                         mouth=PALETTES['Natural']['mouth'])
PALETTES['snow'] = dict(hide=hexes('#8a8e92', '#9a9ea2', '#aaaeb1', '#babdc0', '#c9cccf', '#d6d9db', '#e2e4e6', '#eceef0'),
                        belly=hexes('#d0d2d2', '#dadcdc', '#e4e6e5', '#eceeed', '#f4f5f4'),
                        wing=hexes('#5a5e64', '#70757b', '#878c92', '#9ea3a8', '#b4b8bd', '#c8cbcf', '#dadcdf'),
                        crest=hexes('#1a1a1c', '#3a1012', '#6a1418', '#9a1c1e', '#c42a26', '#e04a36'),
                        beak=hexes('#8a7a5a', '#a69470', '#c0ae88', '#d6c6a2', '#e8dabc'),
                        dark=hexes('#1a1c1f', '#26292d', '#33373c', '#41464c'),
                        mouth=PALETTES['Natural']['mouth'])
PALETTES['charcoal'] = lift(dict(hide=hexes('#121314', '#18191b', '#1f2022', '#26282a', '#2e3033', '#37393c', '#404346', '#4a4d50'),
                                 belly=hexes('#6a6a68', '#7a7a78', '#8a8a88', '#9a9a97', '#a9a9a6'),
                                 wing=hexes('#0e0f10', '#151618', '#1c1e20', '#24262a', '#2c2f33', '#35383d', '#3f4247'),
                                 crest=hexes('#3a0a0c', '#5a0e12', '#7c1418', '#9e1c1e', '#be2a26', '#d63e30'),
                                 beak=hexes('#4a4440', '#5e5752', '#736b64', '#888077', '#9d948a'),
                                 dark=hexes('#050505', '#09090a', '#0d0d0f', '#121214'),
                                 mouth=PALETTES['Natural']['mouth']), 1.15, 4)
TOOTH_RAMP = hexes('#8f8a7c', '#b3ad9c', '#d2ccba', '#e6e1d2', '#f3f0e6', '#fbfaf4')
CLAW_RAMP = hexes('#100e0b', '#1d1914', '#2b251e', '#3b3329', '#4e4436', '#655845')
TONGUE_RAMP = hexes('#5e2526', '#7a3333', '#954442', '#ad5853')


def countershade(p):
    z, y = p[..., 2], p[..., 1]
    low = np.interp(z, [b[0] for b in BODY], [b[1] for b in BODY])
    high = np.interp(z, [b[0] for b in BODY], [b[2] for b in BODY])
    return np.clip((y - low) / np.maximum(high - low, 0.5), 0, 1)


def fuzz(tx):
    """Short pycnofibres: per texel a jittered tone in short runs along the body."""
    col, row = face_grid(tx)
    salt = float(tx.face.index % 997)
    run = cells(np.stack([(col // 2).astype(float), row.astype(float), np.full(col.shape, salt)], -1), 1.0, seed=271)
    return 0.12 * (run - 0.5)


def hide(tx, pal):
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    f = countershade(p) if part not in ('neck', 'thigh', 'shin', 'arm') else np.full(p.shape[:-1], 0.7)
    if n[1] > 0.5:
        f = np.maximum(f, 0.8)
    mottle = fbm(p, 3.0, seed=272, octaves=2) - 0.5
    colour = shade(pal['hide'], 0.35 + 0.35 * (1 - f) * 0.4 + 0.12 * mottle + fuzz(tx), tx.x, tx.y, dither=0.0)
    under = (n[1] < -0.5) | (f < 0.3)
    if part in ('neck',):
        under = np.full(p.shape[:-1], n[1] < -0.5)
    colour = np.where(under[..., None], shade(pal['belly'], 0.5 + 0.12 * mottle + fuzz(tx), tx.x, tx.y, dither=0.0),
                      colour)
    if part in ('foot', 'toe', 'shin', 'hand', 'digit'):
        colour = shade(pal['dark'], 0.55 + 0.2 * mottle, tx.x, tx.y, dither=0.0)
    if part == 'finger':
        colour = shade(pal['wing'], 0.35 + 0.1 * mottle, tx.x, tx.y, dither=0.0)
    return colour


def head(tx, pal):
    """The skull as the body; the eye's socket darker, the beak's horn starting under it."""
    p, n = tx.points, tx.normal
    colour = hide(tx, pal)
    if n[1] < -0.5:
        colour = shade(pal['belly'], 0.45, tx.x, tx.y, dither=0.0)
    return colour


def beak(tx, pal):
    """Horn: pale, streaked along its length, darker toward the tip and along the cutting edges; the mouth's inside
    dark red."""
    p, n = tx.points, tx.normal
    z, y = p[..., 2], p[..., 1]
    col, row = face_grid(tx)
    streak = cells(np.stack([np.floor(y / tx.texel), np.zeros_like(y), np.full(y.shape, float(tx.face.index % 97))],
                            -1), 1.0, seed=273) - 0.5
    v = 0.62 + 0.15 * streak - 0.4 * np.clip((z - 12.0) / 3.0, 0, 1)
    colour = shade(pal['beak'], v, tx.x, tx.y, dither=0.0)
    jaw = tx.box.bone == JAW
    edge = np.abs(y - 4.35) < 0.18
    colour = np.where((edge & (abs(n[0]) > 0.5))[..., None], shade(pal['dark'], 0.6, tx.x, tx.y, dither=0.0), colour)
    if (jaw and n[1] > 0.5) or (not jaw and n[1] < -0.5 and tx.box.tag.get('part') == 'beak'):
        colour = shade(pal['mouth'], 0.3 + 0.1 * streak, tx.x, tx.y)
    if not jaw and tx.box.tag.get('part') == 'beak' and abs(n[0]) > 0.5:
        nostril = (np.abs(z - 9.6) < 0.5) & (np.abs(y - 5.3) < tx.texel * 0.6)
        colour = np.where(nostril[..., None], shade(pal['dark'], 0.1, tx.x, tx.y, dither=0.0), colour)
    return colour


def card(tx, pal):
    """A crest or a membrane card trimmed to its bone's cells of the ARK mesh."""
    tag = tx.box.tag
    mask = tag['sheet']
    p = tx.points
    alpha = mask.at(p) == tag['owner']
    off = np.zeros(p.shape[:-1], bool)
    for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        off |= mask.at(p + (mask.u * du + mask.v * dv) * tx.texel) < 0
    grain = cells(p, tx.texel * 1.01, seed=274) - 0.5
    if tag['fin'] == 'crest':                                    # bold bands up the blade, a dark rim
        z, y = p[..., 2], p[..., 1]
        along = (y * 0.6 - z * 0.8)
        band = (np.floor(along / 0.9) % 2 == 0)
        colour = shade(pal['crest'], 0.55 + 0.35 * band + 0.08 * grain - 0.4 * np.clip((4.0 - z) / 4.5, 0, 1) * 0.5,
                       tx.x, tx.y, dither=0.0)
        colour = np.where(off[..., None], shade(pal['dark'], 0.5, tx.x, tx.y, dither=0.0), colour)
        return colour, alpha
    # the membrane: fine fibres across the span, a few dark veins from the arm, darkening to the tip
    span = np.clip((np.abs(p[..., 0]) - tag.get('root', 4.0)) / tag.get('span', 38.0), 0, 1)
    uv = mask.uv(p) / tx.texel
    fibre = (np.floor(uv[..., 0]) % 3 == 0)
    v = 0.6 - 0.35 * span ** 1.5 + 0.06 * grain - 0.07 * fibre
    if tx.normal[1] < 0:
        v = v + 0.12
    colour = shade(pal['wing'], v, tx.x, tx.y, dither=0.0)
    vein = (np.abs(((np.abs(p[..., 0]) * 0.55 + p[..., 2] * 0.35) % tag.get('veins', 6.0)) - tag.get('veins', 6.0) / 2)
            < tx.texel * 0.5) & (span < 0.7)
    colour = np.where(vein[..., None], shade(pal['wing'], v - 0.25, tx.x, tx.y, dither=0.0), colour)
    colour = np.where((off & alpha)[..., None], shade(pal['dark'], 0.4, tx.x, tx.y, dither=0.0), colour)
    return colour, alpha


def teeth(tx):
    """Small sharp teeth every second texel, white on a dark gum."""
    box = tx.box
    p = tx.points
    along = p[..., 2] / tx.texel
    rows = box.size[1] / tx.texel
    h = (p[..., 1] - (box.center[1] - box.size[1] / 2)) / tx.texel
    if box.tag['row'] == 'upper':
        h = rows - h
    phase = np.abs(((along + (0.0 if box.tag['row'] == 'upper' else 1.0)) % 2.0) / 2.0 - 0.5) * 2
    alpha = (h <= (rows - 0.3) * (1 - phase) + 0.3)
    return shade(TOOTH_RAMP, 0.95 - 0.3 * (h < 1.2), tx.x, tx.y, dither=0.0), alpha


def painter(tx, variant):
    pal = PALETTES[variant]
    mat = tx.box.mat
    if mat in ('membrane', 'crest'):
        return card(tx, pal)
    if mat == 'tooth':
        return teeth(tx)
    if mat == 'beak':
        return beak(tx, pal), None
    if mat == 'head':
        return head(tx, pal), None
    if mat == 'claw':
        return shade(CLAW_RAMP, 0.4 + 0.3 * (cells(tx.points, tx.texel, seed=275) - 0.5), tx.x, tx.y), None
    if mat == 'tongue':
        return shade(TONGUE_RAMP, 0.5, tx.x, tx.y), None
    return hide(tx, pal), None
