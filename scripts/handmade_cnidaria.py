"""The Cnidaria, designed box by box on the ARK rig; built by scripts/handmade_models.py.

The giant jellyfish in the rig's bind pose (source units: +y up): a domed bell of sixteen petals on the rig's own bell
bones, eight round the crown and eight down the skirt to a lobed rim, so the bell pulses with the clips, over an
octagonal core; under it eight long trailing tentacles, eight shorter ones, four ruffled curtains (cut-out cards)
and five frilly oral arms, one tapering box per bone so they ripple with the clips. Painted as a moon jelly: a pale
glassy bell veined from the crown, four horseshoe gonads glowing through the crown, a ring of bright spots round
the rim, stinging cells speckled down the tentacles and deeper frills on the oral arms.
"""
import math

import numpy as np

from ark_geometry import Skeleton
from handmade_models import ROOT, cells, fbm, hexes, shade, unit
from handmade_tyrannosaurus import face_grid

FOLDER, STEM, HEIGHT = 'Cnidaria', 'cnidaria', 0.6
DENSITY = 4.0            # texels per runtime unit: a small animal (0.19 runtime units per source unit)
ATLAS_WIDTH = 256
PREVIEW_VARIANT = 'Natural'
ASSET = 'Cnidaria'
PREVIEW_CLIPS = ('Cnidaria-Idle', 'Cnidaria-Wide-Idle', 'Cnidaria-Shock', 'Cnidaria-Hurt')

# The bell's crown bones and the skirt bone each carries, and the core's octagonal tiers (bottom, top, half width).
CROWN = {'bodyTop1': 'bodyTop14', 'bodyTop2': 'bodyTop13', 'bodyTop3': 'bodyTop15', 'bodyTop4': 'bodyTop16',
         'bodyTop5': 'bodyTop10', 'bodyTop6': 'bodyTop12', 'bodyTop7': 'bodyTop11', 'bodyTop8': 'bodyTop9'}
CORE = [(-2.6, 1.7, 5.3), (1.5, 4.7, 4.4), (4.5, 6.3, 2.6)]
# The tentacles: (kind, chains of bone numbers, widths base to tip).
TENTACLES = [('long', [(1, 2, 3), (4, 5, 6), (7, 8, 9), (10, 11, 12), (13, 14, 15), (16, 17, 18), (19, 20, 21),
                       (22, 23, 24)], (1.3, 0.95, 0.7)),
             ('short', [(25, 26), (27, 28), (29, 30), (31, 32), (33, 34), (35, 36), (37, 38), (39, 40)], (1.0, 0.75)),
             ('curtain', [(41, 42, 43), (45, 46, 47), (49, 50, 51), (53, 54, 55)], (6.0, 5.0, 3.6)),
             ('oral', [(57, 58, 59), (61, 62, 63), (65, 66, 67), (69, 70, 71), (73, 74, 75)], (2.2, 1.9, 1.3))]


def design(d):
    rig = d.rig
    sk = Skeleton(ASSET, ROOT / 'Creatures' / FOLDER / 'source')
    owner = np.array(sk.names)[np.asarray(sk.face_bone)]
    tris = np.asarray(sk.triangles)

    def lowest(bone):
        pts = sk.source_vertices[tris[owner == bone].ravel()]
        return float(pts[:, 1].min()) if len(pts) else rig.pivot(bone)[1] - 2.0

    # ---------------------------------------------------------------- the core, an octagon in three tiers
    for k, (y0, y1, half) in enumerate(CORE):
        d.add('body', (0.0, (y0 + y1) / 2, 0.0), (2 * half, y1 - y0, 2 * half), None, 'bell', part='core', tier=k)
        d.turned('body', (0.0, (y0 + y1) / 2 + 0.05 * (k - 1), 0.0), (2 * half * 0.98, y1 - y0 - 0.3, 2 * half * 0.98),
                 yaw=45, mat='bell', part='core', tier=k)

    # ---------------------------------------------------------------- the bell's petals on their own bones
    for crown, skirt in CROWN.items():
        c = rig.pivot(skirt)
        direction = unit(np.array([c[0], 0.0, c[2]]))
        at = lambda r, y: direction * r + np.array([0.0, y, 0.0])
        d.seg(crown, at(0.2, 6.75), at(3.9, 5.75), 2.6, 1.2, 'bell', ext=(0.0, 0.2), side=np.cross((0, 1, 0), direction),
              part='crown')
        d.seg(skirt, at(3.7, 6.0), at(6.9, 3.4), 4.3, 1.5, 'bell', ext=(0.1, 0.1),
              side=np.cross((0, 1, 0), direction), part='shoulder')
        d.seg(skirt, at(7.2, 3.7), at(7.9, -1.6), 5.4, 1.4, 'bell', ext=(0.1, 0.0),
              side=np.cross((0, 1, 0), direction), part='rim')

    # ---------------------------------------------------------------- the tentacles, curtains and oral arms
    for kind, chains, widths in TENTACLES:
        for n, chain in enumerate(chains):
            bones = [f'tentacle{k}' for k in chain]
            points = [rig.pivot(b) for b in bones]
            points.append(np.array([points[-1][0], min(lowest(bones[-1]), points[-1][1] - 1.0), points[-1][2]]))
            radial = unit(np.array([points[0][0], 0.0, points[0][2]]) + 1e-6)
            for k, bone in enumerate(bones):
                w = widths[k] + 0.04 * (n % 3)
                if kind == 'curtain':
                    d.seg(bone, points[k], points[k + 1], w, 0.3 + 0.15 * (k % 2), 'curtain', ext=(0.2, 0.2),
                          side=np.cross((0, 1, 0), radial), open=('up', 'down', 'east', 'west'), cutout=True,
                          keep=True, part=kind, k=k)
                else:
                    d.seg(bone, points[k], points[k + 1], w, w * (0.85 + 0.05 * n if kind == 'oral' else 1.0), kind,
                          ext=(0.25 + 0.07 * n, 0.1 + 0.05 * n),        # each chain its own ends, no shared planes side=np.cross((0, 1, 0), radial) if np.any(radial) else (1, 0, 0),
                          part=kind, k=k)


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(bell=hexes('#3a5a8a', '#4c72a6', '#6290c0', '#80acd6', '#a2c8e8', '#c6e2f6'),
                    glow=hexes('#7ad0ff', '#a8e4ff', '#d8f6ff'),
                    deep=hexes('#1e2c5a', '#2c3e78', '#3e5496', '#566eb2'),
                    sting=hexes('#e8f6ff', '#fff6d8')),
}
PALETTES['azure'] = PALETTES['Natural']
PALETTES['violet'] = dict(bell=hexes('#4a3a7a', '#5e4c96', '#7662b0', '#927ec8', '#b09edc', '#d0c2ee'),
                          glow=hexes('#c890ff', '#dcb4ff', '#f0dcff'),
                          deep=hexes('#2a1a4e', '#3a266a', '#4e3488', '#6646a4'),
                          sting=hexes('#f4ecff', '#fff0f8'))
PALETTES['rose'] = dict(bell=hexes('#7a3a52', '#964c68', '#b06280', '#c87e9a', '#dc9eb6', '#eec2d2'),
                        glow=hexes('#ff8ab8', '#ffb0d0', '#ffd8e8'),
                        deep=hexes('#4a1a30', '#662440', '#823052', '#9e4066'),
                        sting=hexes('#fff0f4', '#fff8e0'))
PALETTES['moon'] = dict(bell=hexes('#6a7480', '#808a96', '#98a2ac', '#b2bac2', '#ccd2d8', '#e4e8ec'),
                        glow=hexes('#c8b0e0', '#dccaf0', '#f0e6fa'),
                        deep=hexes('#5a4a7a', '#6e5c92', '#8470aa', '#9c88c0'),
                        sting=hexes('#ffffff', '#f4f0ff'))
PALETTES['amber'] = dict(bell=hexes('#7a4a1a', '#966024', '#b07a30', '#c8963e', '#dcb252', '#eecc70'),
                         glow=hexes('#ffb040', '#ffcc70', '#ffe6a8'),
                         deep=hexes('#4a2408', '#66320c', '#824212', '#9e5418'),
                         sting=hexes('#fff6e0', '#ffffff'))


def polar(p):
    return np.arctan2(p[..., 2], p[..., 0]), np.hypot(p[..., 0], p[..., 2])


def bell(tx, pal):
    """The bell: glassy, paler toward the crown, veined by radial canals; on the crown the four horseshoe gonads; a
    ring of bright spots along the rim's lower edge."""
    p, n = tx.points, tx.normal
    theta, r = polar(p)
    y = p[..., 1]
    part = tx.box.tag.get('part')
    mottle = fbm(p, 3.0, seed=261, octaves=2) - 0.5
    v = 0.5 + 0.05 * y + 0.1 * mottle
    colour = shade(pal['bell'], v, tx.x, tx.y, dither=0.0)
    canal = np.abs(((theta / (2 * math.pi) * 16) % 1.0) - 0.5) * r * (2 * math.pi / 16) < tx.texel * 0.5
    colour = np.where((canal & (r > 1.5))[..., None], shade(pal['bell'], v + 0.25, tx.x, tx.y, dither=0.0), colour)
    if part in ('crown', 'core') and n[1] > 0.5:
        for k in range(4):                                     # the four gonads, horseshoes round the centre
            a = k * math.pi / 2 + math.pi / 4
            cx, cz = 1.9 * math.cos(a), 1.9 * math.sin(a)
            d = np.hypot(p[..., 0] - cx, p[..., 2] - cz)
            shoe = (np.abs(d - 1.0) < tx.texel * 0.7) & ((p[..., 0] * math.cos(a) + p[..., 2] * math.sin(a)) > 1.3)
            colour = np.where(shoe[..., None], shade(pal['glow'], 0.6, tx.x, tx.y, dither=0.0), colour)
    if part == 'rim':
        low = y < -0.4
        spot = low & (np.abs(((theta / (2 * math.pi) * 48) % 1.0) - 0.5) < 0.18)
        colour = np.where(low[..., None], shade(pal['deep'], 0.6 + 0.2 * mottle, tx.x, tx.y, dither=0.0), colour)
        colour = np.where(spot[..., None], shade(pal['glow'], 0.9, tx.x, tx.y, dither=0.0), colour)
    if part == 'core' and n[1] < -0.5:
        colour = shade(pal['deep'], 0.4 + 0.2 * mottle, tx.x, tx.y, dither=0.0)
    return colour


def tentacle(tx, pal):
    """A tentacle: pale, with stinging cells speckled bright along it."""
    p = tx.points
    colour = shade(pal['bell'], 0.65 + 0.1 * (fbm(p, 4.0, seed=262, octaves=1) - 0.5), tx.x, tx.y, dither=0.0)
    sting = cells(p, tx.texel * 1.01, seed=263) > 0.86
    return np.where(sting[..., None], shade(pal['glow'], 1.0, tx.x, tx.y, dither=0.0), colour)


def oral(tx, pal):
    """An oral arm: deep-coloured frills, folds every second texel, paler edges."""
    col, row = face_grid(tx)
    fold = (row + col // 2) % 3 == 0
    colour = shade(pal['deep'], 0.55 + 0.25 * fold, tx.x, tx.y, dither=0.0)
    edge = (col == 0) | (col == col.max())
    return np.where(edge[..., None], shade(pal['glow'], 0.3, tx.x, tx.y, dither=0.0), colour)


def curtain(tx, pal):
    """A ruffled curtain: wavy hems down both sides and along the bottom, deep-coloured with pale folds."""
    box = tx.box
    local = (tx.points - box.center) @ box.axes
    s = local[..., 0] / (box.size[0] / 2)                   # across, -1 to 1
    t = local[..., 1] / box.size[1] + 0.5                   # along, 0 at the top of this card
    wave = 0.12 * np.sin(t * 9.0 + box.tag['k'] * 1.7)
    alpha = np.abs(s) < 0.92 - 0.25 * box.tag['k'] * t + wave
    folds = (np.floor((s + 1) / tx.texel * box.size[0] / 2) % 3) == 0
    colour = shade(pal['deep'], 0.5 + 0.3 * folds + 0.1 * t, tx.x, tx.y, dither=0.0)
    return colour, alpha


def painter(tx, variant):
    pal = PALETTES[variant]
    mat = tx.box.mat
    if mat == 'curtain':
        return curtain(tx, pal)
    if mat == 'bell':
        return bell(tx, pal), None
    if mat == 'oral':
        return oral(tx, pal), None
    return tentacle(tx, pal), None
