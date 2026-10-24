"""The Tusoteuthis, designed box by box on the ARK rig; built by scripts/handmade_models.py.

The giant squid in the rig's bind pose (source units: +y up, the arms reach forward along +z, its left is +x): a
long, round mantle in stacked tiers tapering back to a diamond of fins (cut-out cards traced from the ARK mesh's own
fin triangles, handmade_models.sheet), a collar and a head with huge eyes and a hooked black beak, then eight arms
and two long hunting tentacles, one tapering box per bone so they curl with the clips, the tentacles ending in broad
clubs. The skin is painted chromatophore by chromatophore: a deep red mantle flecked dark and pale, a paler
underside, rows of pale suckers under the arms, black hooks on the clubs and golden eyes with a black pupil.
"""
import numpy as np

from ark_geometry import Skeleton
from handmade_megalodon import tiers
from handmade_models import ROOT, cells, fbm, hexes, lift, separate, shade, sheet, unit
from handmade_tyrannosaurus import face_grid

FOLDER, STEM, HEIGHT = 'Tusoteuthis', 'tusoteuthis', 3.5
DENSITY = 1.0            # texels per runtime unit
FACE = 1.5               # the head at FACE times that
ATLAS_WIDTH = 512
PREVIEW_VARIANT = 'Natural'
ASSET = 'Tusoteuthis'
PREVIEW_CLIPS = ('Tusoteuthis-Idle', 'Tusoteuthis-Swim-Fwd', 'Tusoteuthis-Attack-Grab-Loop', 'Tusoteuthis-Attack-Bite')
HEAD = 'c_head'

# The mantle, collar and head along z: (z, underside, top, half width), from the ARK mesh's sections.
BODY = [(-24.8, -2.3, -1.5, 0.4), (-23.0, -2.6, -0.4, 0.9), (-21.5, -2.6, 0.0, 1.2), (-20.0, -2.5, 0.4, 1.3),
        (-18.0, -2.15, 1.2, 1.7), (-16.5, -2.1, 1.9, 1.75), (-15.0, -2.25, 2.6, 2.15), (-13.5, -2.65, 3.15, 2.6),
        (-12.0, -3.1, 3.7, 3.05), (-10.5, -3.4, 4.25, 3.45), (-9.0, -3.65, 4.75, 3.75), (-7.5, -3.9, 5.2, 4.0),
        (-6.0, -4.05, 5.5, 4.2), (-4.5, -4.2, 5.6, 4.27), (-3.0, -4.3, 5.7, 4.33), (-1.5, -4.3, 5.5, 4.35),
        (0.0, -4.25, 5.05, 4.3), (1.0, -4.5, 4.65, 4.2), (2.5, -4.45, 4.3, 4.15), (4.0, -4.5, 3.6, 3.95),
        (5.5, -5.4, 2.7, 3.9), (7.0, -5.35, 2.1, 3.9), (8.4, -5.35, 1.1, 3.6), (9.8, -5.3, 0.2, 3.2),
        (10.6, -5.3, -0.6, 2.6)]
ROUND = [(0.0, 0.12, 0.55), (0.12, 0.3, 0.88), (0.3, 0.7, 1.0), (0.7, 0.88, 0.88), (0.88, 1.0, 0.55)]
SLICES = [('c_body5', -24.8, -20.8), ('c_body4', -20.8, -16.1), ('c_body3', -16.1, -13.2), ('c_body3', -13.2, -10.4),
          ('c_body2', -10.4, -7.1), ('c_body2', -7.1, -3.9), ('c_body1', -3.9, 0.56), ('c_main', 0.56, 4.5),
          ('c_head', 4.5, 7.6), ('c_head', 7.6, 10.4)]
# The arms on the left (mirrored to the right): (letter, bones, width at the base, at the tip, how far the last
# bone reaches past its pivot); B is the long hunting tentacle, whose last three bones carry the club.
ARMS = [('A', 5, 1.5, 0.6, 2.4), ('C', 6, 2.0, 1.0, 5.4), ('D', 9, 2.1, 0.7, 1.8), ('E', 6, 1.6, 0.7, 1.6),
        ('B', 8, 2.4, 1.3, 2.8)]
EYE = (6.2, -0.5, 1.35)     # (z, y, radius) of the eye on the head's side


def body_at(z):
    zs = [b[0] for b in BODY]
    return tuple(float(np.interp(z, zs, [b[k] for b in BODY])) for k in (1, 2, 3))


def design(d):
    rig = d.rig
    sk = Skeleton(ASSET, ROOT / 'Creatures' / FOLDER / 'source')

    # ---------------------------------------------------------------- the mantle, the collar and the head
    for n, (bone, a, b) in enumerate(SLICES):
        low, high, half = body_at((a + b) / 2)
        h = high - low
        rows = [(low + h * f0, low + h * f1, half * w) for f0, f1, w in ROUND] if half > 2.0 else \
            [(low, low + h * 0.3, half * 0.8), (low + h * 0.3, low + h * 0.75, half), (low + h * 0.75, high, half * 0.7)]
        tiers(d, bone, a - 0.15, b + 0.15, rows, mat='head' if bone == HEAD else 'mantle', part='body', nudge=n % 5,
              **(dict(density=FACE) if bone == HEAD else {}))
    # the beak, black and hooked, at the arms' root
    d.span('upper_beak', (-0.75, -3.4, 9.3), (0.75, -2.5, 11.0), 'beak', part='beak')
    d.span('lower_beak', (-0.65, -4.5, 8.9), (0.65, -3.35, 10.7), 'beak', part='beak')

    # ---------------------------------------------------------------- the arms and the tentacles
    for letter, count, w0, w1, reach in ARMS:
        bones = [f'l_tentacle{letter}{k}' for k in range(1, count + 1)]
        points = [rig.pivot(b) for b in bones]
        points.append(points[-1] + unit(points[-1] - points[-2]) * reach)
        for k, bone in enumerate(bones):
            t = k / max(count - 1, 1)
            w = w0 + (w1 - w0) * t
            club = letter == 'B' and k >= count - 3
            if club:
                w = (3.6, 4.2, 2.6)[k - (count - 3)]
            depth = (1.0 if club else w * 0.92) + 0.02 * (k % 3)
            d.seg(bone, points[k], points[k + 1], w + 0.03 * (k % 2), depth, 'club' if club else 'arm',
                  ext=(0.25 if k else 0.6, 0.15), side=(1, 0, 0), mirror=True, part='arm', arm=letter, k=k)

    # ---------------------------------------------------------------- the fins at the mantle's tip, traced
    names = np.array(sk.names)[np.asarray(sk.face_bone)]
    c = sk.source_vertices[np.asarray(sk.triangles)].mean(1)
    tip = np.isin(names, ['c_body4', 'c_body5']) & (c[:, 2] < -18.5)
    sheet(d, sk, tip & (np.abs(c[:, 1] + 1.3) < 0.7) & (np.abs(c[:, 0]) > 1.2), ['c_body5', 'c_body4'], mat='fin',
          res=0.15, fin='side')
    sheet(d, sk, tip & (np.abs(c[:, 0]) < 0.5) & (np.abs(c[:, 1] + 1.3) > 1.4), ['c_body5', 'c_body4'], mat='fin',
          res=0.15, fin='keel')
    separate(d, DENSITY)


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(skin=hexes('#2a0a0e', '#3e1016', '#55161d', '#6c1e24', '#84282c', '#9a3634', '#ae463e', '#c05a4c'),
                    under=hexes('#9a5a56', '#ad6c66', '#bf8078', '#cf948b', '#dca99f'),
                    fleck=hexes('#18050a', '#d88a6c', '#ecb490'),
                    sucker=hexes('#c8a49a', '#dcbcb2', '#ecd4cc'),
                    eye=hexes('#5a3a08', '#a8781a', '#e0b030', '#f6dc6a')),
}
PALETTES['crimson'] = PALETTES['Natural']
PALETTES['maroon'] = dict(skin=hexes('#1e070a', '#2c0b10', '#3c1015', '#4c161a', '#5e1d20', '#702628', '#833232', '#963f3c'),
                          under=hexes('#7e4a48', '#905a57', '#a26c68', '#b37f7a', '#c3938d'),
                          fleck=hexes('#100306', '#b8705a', '#d09878'),
                          sucker=PALETTES['Natural']['sucker'], eye=PALETTES['Natural']['eye'])
PALETTES['violet'] = dict(skin=hexes('#1a0a24', '#261034', '#331646', '#421d58', '#52266a', '#63317c', '#753e8e', '#874c9e'),
                          under=hexes('#7a6288', '#8c7498', '#9e88a8', '#b09cb8', '#c2b0c8'),
                          fleck=hexes('#0c0412', '#c08ad0', '#dcb0e6'),
                          sucker=hexes('#c4b0cc', '#d8c8de', '#eadeee'), eye=PALETTES['Natural']['eye'])
PALETTES['pale'] = dict(skin=hexes('#6a4a46', '#7e5c56', '#927068', '#a6847a', '#b8988c', '#c8ac9e', '#d6bfb0', '#e2d0c2'),
                        under=hexes('#d4c4b8', '#ddd0c6', '#e6dcd4', '#eee6e0', '#f5f0ec'),
                        fleck=hexes('#5a2a28', '#9a4a40', '#b8665a'),
                        sucker=hexes('#e8dcd4', '#f0e8e2', '#f8f4f0'),
                        eye=hexes('#3a3a3a', '#5a5a5e', '#8a8e94', '#b8bec6'))
FIN_RIM = 0.06


def underside(p, n):
    """0 on the back, 1 underneath: by the face's facing and, on the sides, its height in the body."""
    z, y = p[..., 2], p[..., 1]
    low = np.interp(z, [b[0] for b in BODY], [b[1] for b in BODY])
    high = np.interp(z, [b[0] for b in BODY], [b[2] for b in BODY])
    f = np.clip((y - low) / np.maximum(high - low, 0.5), 0, 1)
    return (n[1] < -0.5) | ((abs(n[0]) > 0.5) & (f < 0.3))


def chromatophores(tx, pal, base):
    """The squid's flecks: dark and pale dots scattered over the skin at two sizes."""
    p = tx.points
    colour = base
    big = cells(p, tx.texel * 2.02, seed=251)
    small = cells(p, tx.texel * 1.01, seed=252)
    colour = np.where((big > 0.9)[..., None], shade(pal['fleck'], 0.0, tx.x, tx.y, dither=0.0), colour)
    colour = np.where(((small > 0.95) & (big < 0.5))[..., None], shade(pal['fleck'], 0.6, tx.x, tx.y, dither=0.0),
                      colour)
    return colour


def mantle(tx, pal):
    p, n = tx.points, tx.normal
    mottle = fbm(p, 4.0, seed=253, octaves=2) - 0.5
    col, row = face_grid(tx)
    ridge = (row % 5 == 0) & (abs(n[0]) > 0.5)                     # faint ridges running along the mantle
    v = 0.5 + 0.18 * mottle - 0.08 * ridge
    colour = shade(pal['skin'], v, tx.x, tx.y, dither=0.0)
    colour = np.where(underside(p, n)[..., None], shade(pal['under'], 0.5 + 0.15 * mottle, tx.x, tx.y, dither=0.0),
                      colour)
    return chromatophores(tx, pal, colour)


def head(tx, pal):
    """The head as the mantle, with a huge eye on each side: a dark socket, a golden iris, a black pupil and a
    glint."""
    p, n = tx.points, tx.normal
    colour = mantle(tx, pal)
    if abs(n[0]) > 0.5:
        z0, y0, r = EYE
        dz, dy = (p[..., 2] - z0) / r, (p[..., 1] - y0) / r
        d = np.hypot(dz, dy)
        colour = np.where((d < 1.25)[..., None], shade(pal['fleck'], 0.0, tx.x, tx.y, dither=0.0), colour)
        colour = np.where((d < 1.0)[..., None], shade(pal['eye'], 0.75 - 0.4 * dy, tx.x, tx.y, dither=0.0), colour)
        pupil = (np.abs(dy) < 0.32) & (np.abs(dz) < 0.62)            # a wide, horizontal pupil
        colour = np.where(pupil[..., None], np.zeros(3) + 6, colour)
        glint = (np.abs(dz - 0.35) < 0.18) & (np.abs(dy - 0.45) < 0.18)
        colour = np.where(glint[..., None], np.zeros(3) + 235, colour)
    return colour


def arm(tx, pal):
    """An arm: the mantle's red above, paler below with a double row of pale suckers; the clubs' undersides
    studded with black hooks."""
    p, n = tx.points, tx.normal
    mottle = fbm(p, 3.0, seed=254, octaves=2) - 0.5
    colour = shade(pal['skin'], 0.48 + 0.18 * mottle, tx.x, tx.y, dither=0.0)
    colour = chromatophores(tx, pal, colour)
    if n[1] < -0.3:
        colour = shade(pal['under'], 0.45 + 0.15 * mottle, tx.x, tx.y, dither=0.0)
        col, row = face_grid(tx)
        if tx.box.mat == 'club':
            hook = (col % 3 == 1) & (row % 3 == 1)
            colour = np.where(hook[..., None], shade(pal['fleck'], 0.0, tx.x, tx.y, dither=0.0), colour)
            ring = ((col % 3 == 1) & (row % 3 != 1)) & (row % 3 == 2)
            colour = np.where(ring[..., None], shade(pal['sucker'], 0.8, tx.x, tx.y, dither=0.0), colour)
        else:
            sucker = (row % 2 == 0) & (col % 2 == (row // 2) % 2)
            colour = np.where(sucker[..., None], shade(pal['sucker'], 0.5 + 0.3 * mottle, tx.x, tx.y, dither=0.0),
                              colour)
    return colour


def fin(tx, pal):
    tag = tx.box.tag
    mask = tag['sheet']
    p = tx.points
    alpha = mask.at(p) == tag['owner']
    off = np.zeros(p.shape[:-1], bool)
    for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        off |= mask.at(p + (mask.u * du + mask.v * dv) * tx.texel) < 0
    colour = shade(pal['skin'], 0.55 + 0.15 * (fbm(p, 2.0, seed=255, octaves=2) - 0.5), tx.x, tx.y, dither=0.0)
    colour = chromatophores(tx, pal, colour)
    colour = np.where((off & alpha)[..., None], shade(pal['skin'], 0.1, tx.x, tx.y, dither=0.0), colour)
    return colour, alpha


def painter(tx, variant):
    pal = PALETTES[variant]
    mat = tx.box.mat
    if mat == 'fin':
        return fin(tx, pal)
    if mat in ('arm', 'club'):
        return arm(tx, pal), None
    if mat == 'beak':
        return shade(pal['fleck'], 0.0, tx.x, tx.y, dither=0.0) + 10 * (cells(tx.points, tx.texel, seed=256)[..., None]
                                                                         > 0.7), None
    if mat == 'head':
        return head(tx, pal), None
    return mantle(tx, pal), None
