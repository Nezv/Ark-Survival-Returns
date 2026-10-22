"""The Sabertooth, designed box by box on the ARK rig (Saber); built by scripts/handmade_models.py.

A big cat in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a deep chest and
high withers under a shaggy mane that frames the face and runs back over the shoulders, a waist that tucks up
to heavy haunches, a long tail that sweeps down to its dark tip. The broad head has a short muzzle with whisker
pads and a pink-brown nose, rounded ears, and the two sabers that hang from the upper jaw past the narrow chin;
the jaw is designed shut as the idle holds it (the bind pose roars). The tawny coat carries thin tapering
stripes across the back and flanks, on the thighs and round the tail, over a cream throat, chest and belly.
"""
import math

import numpy as np

from handmade_models import cells, fbm, hexes, lift, noise, shade, strands

FOLDER, STEM, HEIGHT = 'Sabertooth', 'sabertooth', 1.6
DENSITY = 1.0            # texels per runtime unit; the face is painted at FACE times that
ATLAS_WIDTH = 256
PREVIEW_VARIANT = 'Natural'
ASSET = 'Saber'                 # the ARK mesh the design is fitted to (--preview --outline)
OUTLINE_SKIP = ('wisker',)      # whiskers are hair-thin cards; the outline leaves them out
PREVIEW_CLIPS = ('Saber-Idle', 'Saber-Move-Fwd', 'Saber-Attack-Bite', 'Saber-Torpid-Idle')
HEAD = 'c_head'
FACE = 2.0

# (bone, rear z, front z, half width, belly, back): the trunk from the rump to the withers. Each segment is a
# core with a narrower tier above and below it, so the cross-section rounds off; neighbours overlap a little so
# the spine can bend without opening the flank.
TORSO = [
    ('c_back1', -5.95, -2.25, 1.55, 3.75, 7.25),
    ('c_back2', -2.42, -0.30, 1.72, 3.55, 7.38),
    ('c_back3', -0.47, 1.70, 1.95, 3.42, 7.62),
    ('c_back4', 1.52, 3.75, 1.75, 3.62, 8.12),
]
# The saber's centre line from the gum to the tip (after the ARK mesh), with its width across and its depth.
SABER = [((0.48, 6.05, 8.42), 0.26, 0.44), ((0.49, 5.62, 8.31), 0.24, 0.38), ((0.51, 5.25, 8.12), 0.22, 0.3),
         ((0.52, 4.92, 7.84), 0.2, 0.2)]
TAIL = ['c_tail1', 'c_tail2', 'c_tail3', 'c_tail4', 'c_tail5', 'c_tail6']
TAIL_WIDTH = [1.16, 0.98, 0.86, 0.78, 0.72, 0.66]


def design(d):
    rig = d.rig
    P = rig.pivot
    # the jaw and the tongue are designed shut, as the idle holds them; the bind pose roars
    for bone in ('c_jaw', 'c_tongue1', 'c_tongue2'):
        rig.design_as_in(bone, 'Saber-Idle', 0.0, 'c_head')

    # ---------------------------------------------------------------- body
    for k, (bone, z0, z1, half, belly, back) in enumerate(TORSO):
        inset = 0.08 + 0.03 * k                     # the tiers' ends never share a plane with a neighbour's
        d.span(bone, (-half, belly + 0.42, z0), (half, back - 0.45, z1), 'fur', part='body')
        d.span(bone, (-half + 0.42, back - 0.5, z0 + inset), (half - 0.42, back + 0.42, z1 - inset), 'fur', part='back')
        d.span(bone, (-half + 0.45, belly, z0 + inset), (half - 0.45, belly + 0.47, z1 - inset), 'fur', part='belly')
    # the rump rounds off over the tail
    d.span('c_back1', (-1.12, 4.55, -6.38), (1.12, 7.02, -5.88), 'fur', part='body')
    d.span('c_back1', (-0.72, 5.15, -6.66), (0.72, 6.62, -6.33), 'fur', part='body')
    # the chest drops in front of the forelegs; the throat slopes up from it to the jaw
    d.span('c_back5', (-1.4, 3.98, 3.55), (1.4, 7.3, 4.72), 'fur', part='chest')
    d.span('c_back5', (-1.0, 3.8, 3.7), (1.0, 4.1, 4.62), 'fur', part='chest')
    d.span('c_back5', (-1.22, 5.25, 4.62), (1.22, 8.18, 6.12), 'fur', part='neck')
    d.slab('c_back5', (4.25, 4.5), (5.2, 6.15), 1.05, 0.9, 'fur', top=False, part='chest')

    # ---------------------------------------------------------------- mane: layered fringes, open below
    d.span('c_back4', (-2.05, 6.4, 1.48), (2.05, 9.35, 3.85), 'fur', part='mane', hem=1.6,
           open=('down',), cutout=True)
    d.span('c_back4', (-1.22, 9.3, 1.8), (1.22, 9.64, 3.55), 'fur', part='mane')
    d.span('c_back3', (-1.42, 7.1, -0.5), (1.42, 8.72, 1.6), 'fur', part='mane', hem=1.0,
           open=('down',), cutout=True)
    d.span('c_back3', (-0.7, 8.68, -0.1), (0.7, 8.92, 1.45), 'fur', part='mane')
    d.span('c_back2', (-0.82, 7.45, -1.8), (0.82, 8.1, -0.36), 'fur', part='mane', hem=0.5,
           open=('down',), cutout=True)
    d.span('c_back5', (-1.95, 5.1, 3.76), (1.95, 9.05, 5.94), 'fur', part='mane', hem=1.6,
           open=('down', 'north'), cutout=True)
    d.span('c_back5', (-1.18, 9.0, 3.95), (1.18, 9.3, 5.62), 'fur', part='mane')
    # the ruff round the cheeks frames the face and turns with the head
    d.span('c_head', (-1.85, 5.4, 5.58), (1.85, 8.68, 6.3), 'fur', part='mane', hem=1.3,
           open=('down', 'north'), cutout=True)

    # ---------------------------------------------------------------- head (painted at FACE density)
    f = dict(density=FACE)
    d.span('c_head', (-1.25, 6.1, 5.95), (1.25, 8.3, 7.7), 'face', part='skull', **f)
    d.span('c_head', (-1.45, 6.0, 6.15), (1.45, 7.18, 7.45), 'face', part='cheek', **f)
    d.span('c_head', (-1.1, 7.42, 7.62), (1.1, 7.9, 7.96), 'face', part='brow', **f)
    d.span('c_head', (-0.24, 6.8, 7.62), (0.24, 7.46, 8.42), 'face', part='bridge', **f)
    d.span('c_head', (-0.36, 6.83, 8.3), (0.36, 7.14, 8.94), 'face', part='bridge', **f)
    d.span('c_head', (-0.92, 5.92, 7.55), (0.92, 6.7, 8.9), 'face', part='muzzle', **f)
    d.span('c_head', (-0.34, 6.45, 8.88), (0.34, 6.88, 9.08), 'face', part='nose', **f)
    d.span('c_head', (-0.32, 5.84, 8.6), (0.32, 5.95, 8.86), 'tooth', part='incisors', **f)
    # the sabers: a curved blade of three boxes from the gum past the chin
    for k in range(len(SABER) - 1):
        (a, wa, da), (b, wb, db) = SABER[k], SABER[k + 1]
        d.seg('c_head', a, b, (wa + wb) / 2, (da + db) / 2, 'saber', ext=(0.1 if k else 0.18, 0.04), side=(1, 0, 0),
              mirror=True, part='saber', along=(k / (len(SABER) - 1), (k + 1) / (len(SABER) - 1)), **f)
    # the lower jaw, shut: broad behind, narrow at the chin so the sabers hang outside it
    d.span('c_jaw', (-0.8, 5.44, 6.5), (0.8, 5.98, 7.75), 'face', part='jaw', **f)
    d.span('c_jaw', (-0.4, 5.36, 7.6), (0.4, 5.955, 8.66), 'face', part='chin', **f)
    d.span('c_jaw', (0.22, 5.94, 8.32), (0.34, 6.14, 8.46), 'tooth', part='fang', mirror=True, **f)
    d.span('c_tongue2', (-0.36, 5.97, 6.95), (0.36, 6.05, 8.1), 'mouth', part='tongue', **f)
    # rounded ears with a pale inner face
    d.turned('l_ear1', (1.12, 8.26, 6.62), (0.62, 0.68, 0.26), yaw=-14, roll=-20, mat='face', mirror=True,
             part='ear', **f)

    # ---------------------------------------------------------------- forelegs
    d.seg('l_scapula', (1.0, 7.9, 2.7), (1.45, 5.9, 3.4), 0.9, 2.2, 'fur', ext=(0, 0.3), mirror=True,
          part='shoulder')
    elbow, wrist, paw = P('l_elbow'), P('l_wrist'), P('l_toeA1')
    d.seg('l_shoulder', P('l_shoulder') - (0.08, 0, 0), elbow - (0.06, 0, 0), 1.35, 2.05, 'fur', ext=(0.7, 0.35),
          mirror=True, part='arm')
    d.seg('l_elbow', elbow, wrist, 1.2, 1.42, 'fur', ext=(0.45, 0.2), mirror=True, part='forearm')
    d.seg('l_wrist', wrist, paw, 1.14, 1.18, 'fur', ext=(0.1, 0.0), mirror=True, part='wrist')
    d.span('l_toeA1', (1.07, 0.0, 2.78), (2.48, 0.7, 4.42), 'paw', mirror=True, part='paw')

    # ---------------------------------------------------------------- hind legs
    knee, ankle, foot = P('l_knee'), P('l_ankle'), P('l_foot')
    d.seg('l_leg', P('l_leg') + (0.02, 0.1, 0.0), knee, 1.85, 3.0, 'fur', ext=(0.8, 0.35), mirror=True,
          part='thigh')
    d.seg('l_knee', knee, ankle, 1.22, 1.18, 'fur', ext=(0.45, 0.25), mirror=True, part='shin')
    d.seg('l_ankle', ankle, foot, 1.04, 1.0, 'fur', ext=(0.3, 0.05), mirror=True, part='hock')
    d.span('l_foot', (1.0, 0.0, -5.3), (2.14, 0.64, -3.62), 'paw', mirror=True, part='paw')

    # ---------------------------------------------------------------- tail
    points = [P(b) for b in TAIL] + [P('c_tail6') + (0.0, 0.12, -0.62)]
    run = 0.0
    for k, bone in enumerate(TAIL):
        w, ext = TAIL_WIDTH[k], (0.35 if k else 0.6, 0.15)
        d.seg(bone, points[k], points[k + 1], w, w, 'fur', ext=ext, part='tail', ring=k, side=(1, 0, 0),
              start=run - ext[0])                     # how far along the tail the box begins, for its rings
        run += float(np.linalg.norm(points[k + 1] - points[k]))


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(fur=hexes('#5e3a1d', '#7f4f27', '#9d6631', '#b77c3c', '#c99148', '#d8a65a', '#e4ba72'),
                    cream=hexes('#b09a72', '#c8b38a', '#dac8a2', '#e8d9b7', '#f1e6cb', '#f7f0de'),
                    stripe=hexes('#1e120a', '#2b190e', '#3a2414'),
                    mane=hexes('#40271a', '#58361f', '#704626', '#87572f', '#9c6839', '#b07a45'),
                    nose=hexes('#5a3430', '#7c4a43', '#9c6258'), skin=hexes('#24191a', '#352523', '#47332f')),
    'tawny': dict(fur=hexes('#5e3a1d', '#7f4f27', '#9d6631', '#b77c3c', '#c99148', '#d8a65a', '#e4ba72'),
                  cream=hexes('#b09a72', '#c8b38a', '#dac8a2', '#e8d9b7', '#f1e6cb', '#f7f0de'),
                  stripe=hexes('#1e120a', '#2b190e', '#3a2414'),
                  mane=hexes('#40271a', '#58361f', '#704626', '#87572f', '#9c6839', '#b07a45'),
                  nose=hexes('#5a3430', '#7c4a43', '#9c6258'), skin=hexes('#24191a', '#352523', '#47332f')),
    'sand': dict(fur=hexes('#7a6448', '#8d7759', '#9f8a6a', '#b39e7c', '#c7b28e', '#dbc7a1', '#efdcb4'),
                 cream=hexes('#cbbfa6', '#d4cab3', '#ded5c1', '#e8e0ce', '#f1ebdc', '#fbf6ea'),
                 stripe=hexes('#5a4732', '#6c5841', '#7e6a50'),
                 mane=hexes('#5c4934', '#715c43', '#866f53', '#9c8363', '#b29873', '#c9ad84'),
                 nose=hexes('#6a4440', '#8c5f58', '#b07c72'), skin=hexes('#2e2421', '#413531', '#544642')),
    'snow': dict(fur=hexes('#7a7060', '#958a77', '#ada38e', '#c2b9a4', '#d4ccb8', '#e3dccb', '#efeadc'),
                 cream=hexes('#c2bcae', '#d4cfc2', '#e2ded3', '#edeae1', '#f5f3ed', '#fbfaf6'),
                 stripe=hexes('#4e463b', '#5e554a', '#6f6659'),
                 mane=hexes('#7a705f', '#8f8572', '#a49a86', '#b8af9b', '#cac2af', '#dad3c2'),
                 nose=hexes('#7a5650', '#9a6e66', '#b8887e'), skin=hexes('#3e3430', '#504540', '#625650')),
    'sable': dict(fur=hexes('#1e140d', '#2e2016', '#3f2d1f', '#513b29', '#634933', '#76573d', '#8a6648'),
                  cream=hexes('#6a5a48', '#7c6c59', '#8e7e6b', '#a1917d', '#b4a48f', '#c8b8a2'),
                  stripe=hexes('#0c0806', '#18100a', '#24180f'),
                  mane=hexes('#140d09', '#211710', '#302117', '#3e2c1f', '#4e3727', '#5e432f'),
                  nose=hexes('#3a2624', '#533734', '#6e4844'), skin=hexes('#160f0e', '#221817', '#2e2220')),
}
SABER_RAMP = hexes('#6e5e44', '#948062', '#b7a383', '#d3c3a2', '#e7dbbf', '#f5eedb')
TOOTH_RAMP = hexes('#9a917e', '#bdb49f', '#d9d1bd', '#ece6d6', '#f8f4ea')
MOUTH_RAMP = hexes('#2c1012', '#47191b', '#652829', '#843838', '#9e4b49')
PAD_RAMP = hexes('#1c1414', '#2b1f1e', '#3b2b29')
# Flank stripes: (z where it leaves the spine, drift in z per unit down the flank, how far down it reaches,
# its width at the spine), from the rump to the shoulder.
# The coat's stripes as pixel runs, one texel wide. Flank: (z where it crosses the spine, texels it reaches over
# the back from each side, runs down the flank as (first row, last row, texels behind its start)), rows counted
# down from the spine.
FLANK = [(-5.7, 2, [(0, 2, 0), (3, 4, -1)]),
         (-4.55, 3, [(0, 3, 0), (4, 6, -1), (7, 8, -2)]),
         (-3.4, 1, [(1, 3, 0), (4, 5, -1)]),
         (-2.25, 3, [(0, 2, 0), (3, 6, -1), (7, 9, -1)]),
         (-1.1, 2, [(0, 3, 0), (4, 5, 1)]),
         (0.05, 3, [(0, 3, 0), (4, 7, -1), (8, 9, -2)]),
         (1.2, 2, [(0, 2, 0), (3, 5, -1)])]
# Limbs: (row down from the top end, texel from the front edge) on the outside face.
LIMB = {'thigh': [(0, 7), (1, 7), (2, 7), (3, 6), (4, 6), (5, 5), (6, 5),
                  (0, 4), (1, 4), (2, 3), (3, 3), (4, 2),
                  (6, 7), (7, 7), (8, 6)],
        'arm': [(4, 4), (4, 3), (4, 2), (6, 4), (6, 3)],
        'forearm': [(2, 3), (2, 2)]}


def back_line(z):
    """The height of the spine along the body (the top of the torso boxes)."""
    return np.interp(z, [-6.7, -5.9, -5.5, -2.0, 0.5, 2.5, 4.0, 6.2], [6.6, 7.05, 7.6, 7.75, 8.0, 8.55, 8.4, 8.2])


def belly_line(z):
    """The height where the cream underside starts on the flank."""
    return np.interp(z, [-6.7, -5.6, -4.5, -1.0, 1.5, 3.3, 4.6, 6.2], [5.4, 4.7, 4.25, 4.05, 4.1, 4.4, 5.6, 6.3])


def grid(tx, axis, lo=None, hi=None):
    """Per texel: its index along a world axis across a world-aligned box (0 at the low end), and the count."""
    box = tx.box
    if lo is None:
        lo, hi = box.center[axis] - box.size[axis] / 2, box.center[axis] + box.size[axis] / 2
    count = max(1, int(round((hi - lo) / tx.texel)))
    index = np.floor((tx.points[..., axis] - lo) / (hi - lo) * count).astype(int)
    return np.clip(index, 0, count - 1), count


def edge(tx, axis=0):
    """Per texel: how many texels it lies from the nearer side of the box across `axis` (0 at the side)."""
    i, count = grid(tx, axis)
    return np.minimum(i, count - 1 - i)


def mark(i, j, cells):
    out = np.zeros(np.shape(i), bool)
    for a, b in cells:
        out |= (i == a) & (j == b)
    return out


def limb(tx):
    """Per texel on a limb box: how far down it (0 at the top end, 1 at the bottom) and across its depth
    (-0.5 in front, 0.5 behind); seg() boxes run from their first point down to their second."""
    box = tx.box
    rel = tx.points - box.center
    return rel @ box.axes[:, 1] / box.size[1] + 0.5, rel @ box.axes[:, 2] / box.size[2]


def hair_alpha(tx):
    """Jagged locks at the bottom of a mane fringe: each lock and strand ends at its own length."""
    depth = tx.box.tag.get('hem')
    if not depth or abs(tx.normal[1]) > 0.5:
        return None
    tone, _, lock = strands(tx, 2, 1000, seed=5)
    rise = tx.up_from_bottom() + 0.5
    reach = depth / tx.texel * (0.08 + 0.7 * lock + 0.22 * tone)
    return rise >= reach


def flank_stripes(tx):
    """The stripes of FLANK on a torso face: over the back from each side, then down the flank in runs, snapped to
    the face's own texels along the body so each stays one crisp texel wide."""
    p, n = tx.points, tx.normal
    band = np.zeros(p.shape[:2], bool)
    if abs(n[2]) > 0.5 or n[1] < -0.5 or tx.box.tag.get('part') not in ('body', 'back'):
        return band
    iz, count = grid(tx, 2)
    lo, span = tx.box.center[2] - tx.box.size[2] / 2, tx.box.size[2]
    y = p[..., 1]
    for z0, over, runs in FLANK:
        col = int(np.floor((z0 - lo) / span * count))
        if n[1] > 0.5:
            reach = edge(tx) < over if tx.box.tag['part'] == 'back' else np.ones(y.shape, bool)
            band |= (iz == col) & reach
            continue
        row = np.floor((back_line(z0) + 0.42 - y) / tx.texel).astype(int)
        for first, last, behind in runs:
            band |= (iz == col + behind) & (row >= first) & (row <= last)
    return band & (y > belly_line(p[..., 2]) + 0.3)


def local_grid(tx, axis):
    """Per texel: its index along one of its box's own axes (0 at the low end), and the count across."""
    box = tx.box
    local = (tx.points - box.center) @ box.axes[:, axis]
    count = max(1, int(round(box.size[axis] / tx.texel)))
    return np.clip(np.floor((local / box.size[axis] + 0.5) * count).astype(int), 0, count - 1), count


def limb_stripes(tx, part):
    """Stripes on the outside of the thighs and the upper forelegs, rings round the tail."""
    p, n = tx.points, tx.normal
    outer = n[0] * np.sign(tx.box.center[0]) > 0.5
    if part in LIMB:
        row, _ = local_grid(tx, 1)                    # down the limb from its top end
        col, _ = local_grid(tx, 2)                    # seg() boxes' depth axis points backwards: 0 in front
        return mark(row, col, LIMB[part]) & outer
    if part == 'tail':
        a, _ = limb(tx)
        s = tx.box.tag['start'] + a * tx.box.size[1]
        return ((s % 1.15) < 0.4) & (s > 1.1)
    return np.zeros(p.shape[:2], bool)


def coat(tx, pal, part=None):
    """Short fur: the tawny back a shade darker, a fine texture of strands, the cream underside, the stripes."""
    p, n = tx.points, tx.normal
    part = part or tx.box.tag.get('part', 'body')
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    tone, phase, lock = strands(tx, 1, 3, seed=7)
    grain = cells(p, tx.texel * 1.01, seed=17)
    body = part in ('body', 'back', 'belly', 'chest', 'neck')
    dorsal = np.clip((y - belly_line(z)) / np.maximum(back_line(z) - belly_line(z), 0.5), 0, 1) if body else \
        np.clip((y - 2.5) / 5.0, 0, 1)
    v = 0.66 - 0.16 * dorsal ** 1.4 + 0.07 * (tone - 0.5) + 0.05 * (grain - 0.5) + 0.03 * (0.5 - phase)
    v += 0.03 * max(n[1], 0) - 0.05 * max(-n[1], 0)
    if part in ('shin', 'hock', 'forearm', 'wrist', 'paw'):
        v += 0.03
    if part in ('thigh', 'arm', 'forearm', 'shin', 'hock', 'shoulder') and abs(n[0]) > 0.5:
        back, depth = local_grid(tx, 2)                                  # the limb rounds off at its front and back
        v -= 0.07 * ((back == 0) | (back == depth - 1))
    colour = shade(pal['fur'], v, tx.x, tx.y, dither=0.0)
    # the cream underside: belly, chest, throat and the inside of the legs, with a pale fringe at its edge
    wave = 0.22 * (noise(p, 0.8, seed=9) - 0.5)
    under = np.zeros(y.shape, bool)
    if body:
        line = belly_line(z) + wave
        under = (y < line) | (n[1] < -0.5)
        if part in ('chest', 'neck'):
            under |= (n[2] > 0.5) & (np.abs(x) < 0.95 + wave) & (y < 7.0)
        fringe = (y >= line) & (y < line + tx.texel * 1.05) & ~under
        colour = np.where(fringe[..., None], shade(pal['fur'], 0.98, tx.x, tx.y), colour)
    elif part in ('arm', 'forearm', 'wrist', 'thigh', 'shin', 'hock', 'shoulder'):
        inner = n[0] * np.sign(tx.box.center[0]) < -0.5
        low = {'thigh': 4.4, 'arm': 4.6, 'shoulder': 4.0}.get(part, 9.0)
        under = (inner & (y < low)) | (n[1] < -0.5)
        if part in ('forearm', 'wrist'):
            under |= (n[2] < -0.5) & (y < 2.6 + wave)                 # the back of the foreleg pales to the paw
        if part == 'hock':
            under |= (n[2] > 0.5) & (y < 1.4 + wave)
    if np.any(under):
        cream = shade(pal['cream'], 0.6 + 0.10 * (tone - 0.5) + 0.06 * (grain - 0.5) - 0.08 * max(-n[1], 0),
                      tx.x, tx.y, dither=0.0)
        colour = np.where(under[..., None], cream, colour)
    band = flank_stripes(tx) if body else limb_stripes(tx, part)
    band &= ~under
    if np.any(band):
        colour = np.where(band[..., None], shade(pal['stripe'], 0.5 + 0.5 * (grain - 0.5), tx.x, tx.y, dither=0.0),
                          colour)
    return colour


def mane(tx, pal):
    """The mane: long locks hanging in layers, lit at their roots; towards its fringe more and more strands take
    the coat's tawny, so the ruff grows out of the body instead of lying on it."""
    p, n = tx.points, tx.normal
    tone, phase, lock = strands(tx, 1, 4, seed=11)
    v = 0.56 + 0.26 * (tone - 0.5) + 0.06 * (lock - 0.5) + 0.12 * (0.5 - phase) - 0.12 * (phase > 0.74)
    v += 0.04 * (fbm(p, 4.0, seed=12, octaves=2) - 0.5) + 0.07 * max(n[1], 0)
    if n[1] > 0.5:                                                  # the top rounds off: lit along the crest
        v += 0.12 * (0.5 - np.abs(p[..., 0]) / tx.box.size[0]) - 0.05 * (edge(tx) == 0)
    if n[2] > 0.5 and tx.box.bone == 'c_head':
        v += 0.06                                                   # the ruff's face, round the cheeks, catches light
    colour = shade(pal['mane'], v, tx.x, tx.y, dither=0.0)
    if tx.box.tag.get('hem') and abs(n[1]) < 0.5:
        fringe = np.clip(1.15 - tx.up_from_bottom() * tx.texel / (1.6 * tx.box.tag['hem']), 0, 1)
        coat_strand = tone < fringe * 0.9
        colour = np.where(coat_strand[..., None], shade(pal['fur'], v + 0.02, tx.x, tx.y, dither=0.0), colour)
    return colour


# The face as pixel art at FACE density: (texels from the side of the box, row) per marking. Columns count from
# the box's nearer side, so every marking comes in a mirrored pair; rows as each painter says.
FOREHEAD = [(5, 0), (5, 1), (4, 2), (4, 3), (3, 4), (3, 5), (2, 0), (2, 1), (1, 2), (1, 3), (0, 4), (6, 1), (6, 2)]
CHEEK_BARS = [(2, 4), (3, 4), (4, 5), (5, 5), (2, 2), (3, 2), (4, 3), (5, 3), (6, 3)]   # (from the front, from below)
TEMPLE = [(0, 8), (1, 8), (2, 9), (3, 9)]                                                # (from the front, from below)
WHISKER_SIDE = [(1, 2), (2, 1), (3, 2)]                                                    # (from the front, from below)


def face(tx, pal):
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    colour = coat(tx, pal, 'head')
    grain = cells(p, tx.texel * 1.01, seed=21)
    cream = shade(pal['cream'], 0.66 + 0.14 * (grain - 0.5) - 0.1 * max(-n[1], 0), tx.x, tx.y, dither=0.0)
    dark = shade(pal['stripe'], 0.6, tx.x, tx.y, dither=0.0)
    lip = shade(pal['skin'], 0.5, tx.x, tx.y, dither=0.0)
    side = abs(n[0]) > 0.5
    front, top, below = n[2] > 0.5, n[1] > 0.5, n[1] < -0.5
    if part in ('muzzle', 'jaw', 'chin', 'cheek') and below:
        return cream
    if part == 'skull':
        e = edge(tx)
        if top:
            j, count = grid(tx, 2)
            colour = np.where(mark(e, count - 1 - j, FOREHEAD)[..., None], dark, colour)
        elif front:
            j, count = grid(tx, 1)
            row = count - 1 - j                                          # 0 at the top
            colour = np.where((mark(e, row, [(5, 0), (2, 0), (5, 1)]))[..., None], dark, colour)
        elif side:
            k, count = grid(tx, 2)
            j, _ = grid(tx, 1)
            colour = np.where(mark(count - 1 - k, j, TEMPLE)[..., None], dark, colour)
    elif part == 'brow':
        if top:
            j, count = grid(tx, 2)
            e = edge(tx)
            colour = np.where(((count - 1 - j == 0) & ((e == 4) | (e == 1)))[..., None], dark, colour)
    elif part == 'cheek':
        if side:
            k, kc = grid(tx, 2)
            l, _ = grid(tx, 1)
            k = kc - 1 - k
            pale = ((l <= 2) & (k <= 4)) | (l <= 1)
            colour = np.where(pale[..., None], cream, colour)
            colour = np.where(mark(k, l, CHEEK_BARS)[..., None], dark, colour)
        elif front:
            l, _ = grid(tx, 1)
            colour = np.where((l <= 2)[..., None], cream, colour)
    elif part == 'bridge':
        if side:
            k, kc = grid(tx, 2)
            l, _ = grid(tx, 1)
            k = kc - 1 - k
            if tx.box.size[2] > 0.7:                                     # the tear line from the eye's inner corner
                colour = np.where(mark(k, l, [(2, 1), (1, 0)])[..., None], dark, colour)
            else:
                colour = np.where(mark(k, l, [(2, 1), (2, 0)])[..., None], dark, colour)
    elif part == 'muzzle':
        colour = cream
        l, lc = grid(tx, 1)
        if side:
            k, kc = grid(tx, 2)
            k = kc - 1 - k
            colour = np.where(((l == 0) & (k <= kc - 2))[..., None], lip, colour)
            colour = np.where(mark(k, l, WHISKER_SIDE)[..., None], shade(pal['fur'], 0.25, tx.x, tx.y), colour)
            colour = np.where(((l == lc - 1) & (k >= kc - 3))[..., None], shade(pal['fur'], 0.7, tx.x, tx.y), colour)
        elif front:
            e = edge(tx)
            colour = np.where((l == 0)[..., None], lip, colour)
            colour = np.where(((e == 4) & (l == 1))[..., None], shade(pal['skin'], 0.9, tx.x, tx.y), colour)
        elif top:
            k, kc = grid(tx, 2)
            colour = np.where((kc - 1 - k >= 4)[..., None], shade(pal['fur'], 0.72, tx.x, tx.y), colour)
    elif part == 'nose':
        colour = shade(pal['nose'], 0.5 + 0.2 * (grain - 0.5) + 0.3 * max(n[1], 0), tx.x, tx.y)
        if front:
            l, _ = grid(tx, 1)
            colour = np.where(((edge(tx) == 0) & (l == 0))[..., None], shade(pal['skin'], 0.0, tx.x, tx.y), colour)
    elif part in ('jaw', 'chin'):
        colour = cream
        if top:
            colour = shade(MOUTH_RAMP, 0.5 + 0.3 * (grain - 0.5), tx.x, tx.y)
        else:
            l, lc = grid(tx, 1)
            colour = np.where((l == lc - 1)[..., None], lip, colour)
    elif part == 'ear':
        local = (p - tx.box.center) @ tx.box.axes
        i = np.floor((local[..., 0] / tx.box.size[0] + 0.5) * 3).clip(0, 2)
        j = np.floor((local[..., 1] / tx.box.size[1] + 0.5) * 3).clip(0, 2)
        middle = (i == 1) & (j <= 1)
        facing = tx.box.axes.T @ n
        if facing[2] > 0.5:                                              # the pale inside of the ear
            colour = np.where(middle[..., None], shade(pal['nose'], 0.9, tx.x, tx.y), cream)
        else:                                                            # the dark back with its pale spot
            colour = dark
            if facing[2] < -0.5:
                colour = np.where(middle[..., None], cream, dark)
    return colour


def paw(tx, pal):
    p, n = tx.points, tx.normal
    if n[1] < -0.5:                                                      # the dark pads
        return shade(PAD_RAMP, 0.5 + 0.4 * (cells(p, tx.texel, seed=31) - 0.5), tx.x, tx.y)
    colour = coat(tx, pal, 'paw')
    grain = cells(p, tx.texel * 1.01, seed=32)
    cream = shade(pal['cream'], 0.62 + 0.12 * (grain - 0.5), tx.x, tx.y)
    pale = (p[..., 1] < 0.4) | ((n[0] * np.sign(tx.box.center[0])) < -0.5)
    colour = np.where(pale[..., None], cream, colour)
    if n[2] > 0.5 or n[1] > 0.5:                                         # dark lines between the four toes
        i, count = grid(tx, 0)
        toes = np.round(np.arange(1, 4) * count / 4).astype(int)
        split = np.isin(i, toes)
        if n[2] > 0.5:
            j, _ = grid(tx, 1)
            split &= j <= 0
        else:
            k, kc = grid(tx, 2)
            split &= k >= kc - 2
        colour = np.where(split[..., None], shade(pal['stripe'], 0.8, tx.x, tx.y), colour)
    return colour


def painter(tx, variant):
    pal = PALETTES[variant]
    mat = tx.box.mat
    part = tx.box.tag.get('part')
    alpha = hair_alpha(tx)
    if mat == 'fur':
        if part == 'mane':
            rgb = mane(tx, pal)
        else:
            rgb = coat(tx, pal)
            if part == 'tail' and tx.box.tag.get('ring') == len(TAIL) - 1:   # the dark tip of the tail
                a, _ = limb(tx)
                rgb = np.where((a > 0.62)[..., None], shade(pal['stripe'], 0.8, tx.x, tx.y), rgb)
    elif mat == 'face':
        rgb = face(tx, pal)
    elif mat == 'paw':
        rgb = paw(tx, pal)
    elif mat == 'saber':
        p, n, box = tx.points, tx.normal, tx.box
        a, b = box.tag['along']
        s = ((p - box.center) @ box.axes[:, 1]) / box.size[1] + 0.5
        t = a + (b - a) * np.clip(s, 0, 1)
        v = 0.8 - 0.2 * t ** 2 + 0.08 * max(n[2], 0) - 0.08 * max(-n[2], 0)
        v -= 0.32 * np.clip(1 - t / 0.14, 0, 1)                          # stained at the gum
        rgb = shade(SABER_RAMP, v, tx.x, tx.y, dither=0.0)
    elif mat == 'tooth':
        rgb = shade(TOOTH_RAMP, 0.7 + 0.2 * (noise(tx.points, tx.texel, seed=42) - 0.5), tx.x, tx.y)
    else:  # the mouth: the tongue
        rgb = shade(MOUTH_RAMP, 0.72 + 0.2 * (noise(tx.points, tx.texel, seed=43) - 0.5), tx.x, tx.y)
    return rgb, alpha
