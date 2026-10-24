"""The Plesiosaur, designed box by box on the ARK rig; built by scripts/handmade_models.py.

The long-necked marine reptile in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a
broad, flattened oval trunk in stacked tiers, a short tapering tail, and a long neck that narrows segment by segment
to a small head; the jaw is designed shut as the idle holds it, with long interlocking cut-out fangs outside the lips.
The four wing-like flippers and the low row of spikes down the nape are cut-out cards traced from the ARK mesh's own
triangles (handmade_models.sheet), one per bone so they row with the clips. The skin is a dark back speckled with
pale spots over a pale belly, darker flipper edges and pale scars.
"""
import numpy as np

from ark_geometry import Skeleton
from handmade_megalodon import tiers
from handmade_models import ROOT, cells, fbm, hexes, lift, separate, shade, sheet
from handmade_tyrannosaurus import face_grid

FOLDER, STEM, HEIGHT = 'Plesiosaur', 'plesiosaur', 3.5
DENSITY = 0.6            # texels per runtime unit: a long animal on a small rig (3.26 runtime units per source unit)
FACE = 2.0               # the head, the jaw and the teeth at FACE times that
ATLAS_WIDTH = 512
PREVIEW_VARIANT = 'Natural'
ASSET = 'Plesiosaur'
PREVIEW_CLIPS = ('Plesiosaur-Idle', 'Plesiosaur-Move-Fwd', 'Plesiosaur-Attack-Bite', 'Plesiosaur-Torpid-Idle')
JAW_POSE = ('Plesiosaur-Idle', 0.0, ['jaw', 'tongue'])     # the frame the jaw is designed in: the idle shuts it
HEAD, JAW = 'c_neck8', 'c_jaw'

# The body's outline along z: (z, underside, top, half width), from the ARK mesh's sections without the spikes.
BODY = [(-33.8, 3.0, 3.6, 0.4), (-32.0, 2.3, 3.95, 0.9), (-30.0, 1.35, 4.1, 1.6), (-28.0, 0.65, 4.35, 2.2),
        (-26.0, 0.0, 4.55, 2.95), (-24.0, -0.6, 5.0, 3.6), (-22.0, -1.0, 5.5, 4.45), (-20.0, -1.35, 6.0, 5.3),
        (-18.0, -1.8, 6.5, 6.05), (-16.0, -2.3, 7.1, 6.6), (-14.0, -2.8, 7.7, 7.1), (-12.0, -3.5, 8.15, 7.6),
        (-10.0, -4.3, 8.45, 8.2), (-8.0, -4.9, 8.6, 8.8), (-6.0, -5.4, 8.7, 9.8), (-4.0, -5.8, 8.8, 10.3),
        (-2.0, -6.1, 8.9, 10.45), (0.0, -6.4, 9.0, 10.45), (2.0, -6.6, 9.3, 10.4), (4.0, -6.6, 9.6, 10.2),
        (6.0, -6.5, 9.7, 9.6), (8.0, -6.1, 9.9, 9.4), (10.0, -5.5, 9.9, 9.3), (12.0, -4.8, 9.6, 8.4),
        (14.0, -3.9, 9.1, 7.6), (16.0, -3.1, 8.5, 6.5), (18.0, -2.2, 7.9, 5.6), (20.0, -1.6, 7.4, 4.9),
        (22.0, -1.2, 7.05, 4.45), (24.0, -0.9, 6.75, 4.1), (26.0, -0.7, 6.45, 3.9), (28.0, -0.6, 6.2, 3.7),
        (30.0, -0.5, 5.9, 3.4), (32.0, -0.35, 5.65, 3.25), (34.0, -0.33, 5.42, 3.1), (36.0, -0.27, 5.27, 3.0),
        (38.0, -0.2, 5.15, 2.8), (40.0, -0.1, 5.0, 2.7), (42.0, 0.05, 4.9, 2.56), (44.0, 0.16, 4.8, 2.45),
        (46.0, 0.3, 4.73, 2.36), (48.0, 0.43, 4.72, 2.29), (50.0, 0.6, 4.7, 2.21), (52.0, 0.8, 4.67, 2.15),
        (54.0, 0.94, 4.74, 2.14), (57.0, 1.2, 5.0, 2.15), (59.0, 1.4, 5.2, 2.2)]
TIER_SHAPE = [(0.0, 0.12, 0.62), (0.12, 0.32, 0.92), (0.32, 0.66, 1.0), (0.66, 0.86, 0.8), (0.86, 1.0, 0.45)]
# The slices: (bone, rear z, front z); the spine's bones carry the body forward from their pivots, the tail's back.
SLICES = [('c_tail4', -33.8, -30.6), ('c_tail3', -30.6, -26.0), ('c_tail2', -26.0, -20.0), ('c_tail1', -20.0, -14.2),
          ('c_back1', -14.2, -10.1), ('c_back1', -10.1, -4.76), ('c_back2', -4.76, 0.89), ('c_back3', 0.89, 7.25),
          ('c_back4', 7.25, 14.05), ('c_neck1', 14.05, 20.28), ('c_neck2', 20.28, 27.7), ('c_neck3', 27.7, 35.43),
          ('c_neck4', 35.43, 42.28), ('c_neck5', 42.28, 49.13), ('c_neck6', 49.13, 53.95), ('c_neck7', 53.95, 58.95)]
# The head, slice by slice: (rear z, front z, rows (bottom, top, half width)); the eye on the third's middle tier.
SKULL = [(57.6, 59.5, [(1.3, 3.2, 2.35), (3.2, 5.5, 2.05)]),
         (59.5, 61.6, [(2.0, 3.6, 2.5), (3.6, 5.6, 2.05)]),
         (61.6, 64.0, [(2.2, 3.3, 2.15), (3.3, 4.9, 1.85), (4.9, 5.45, 1.15)]),
         (64.0, 65.95, [(2.0, 3.4, 1.3), (3.4, 4.8, 0.95)])]
# The lower jaw as the idle holds it.
JAWS = [(58.6, 60.6, [(0.7, 2.1, 2.2)]), (60.6, 62.6, [(0.5, 2.2, 2.15)]), (62.6, 64.4, [(0.7, 2.2, 1.55)]),
        (64.4, 65.4, [(0.9, 2.1, 1.0)])]
# The fangs, outside the lips: (rear z, front z, half width, the gum's y).
TEETH = [(59.6, 61.5, 2.5, 2.05), (61.7, 63.9, 2.15, 2.25), (64.1, 65.8, 1.3, 2.05)]
SCARS = [(-2.0, 5.0, 6.0, -0.3, 3), (10.0, 3.0, 5.0, 0.25, 2), (-14.0, 4.0, 4.0, -0.2, 2)]


def body_at(z):
    zs = [b[0] for b in BODY]
    return tuple(float(np.interp(z, zs, [b[k] for b in BODY])) for k in (1, 2, 3))


def rows_for(low, high, half):
    h = high - low
    if half < 3.0:
        return [(low, low + h * 0.5, half), (low + h * 0.5, high, half * 0.78)]
    if half < 8.0:
        return [(low, low + h * 0.25, half * 0.78), (low + h * 0.25, low + h * 0.72, half),
                (low + h * 0.72, high, half * 0.66)]
    return [(low + h * f0, low + h * f1, half * w) for f0, f1, w in TIER_SHAPE]


def design(d):
    rig = d.rig
    for bone in rig.bones:
        if bone == JAW or 'tongue' in bone:
            rig.design_as_in(bone, 'Plesiosaur-Idle', 0.0, HEAD)
    sk = Skeleton(ASSET, ROOT / 'Creatures' / FOLDER / 'source')

    # ---------------------------------------------------------------- the trunk, the tail and the neck
    for n, (bone, a, b) in enumerate(SLICES):
        low, high, half = body_at((a + b) / 2)
        overlap = 0.15 if half < 3.0 else 0.3
        tiers(d, bone, a - overlap, b + overlap, rows_for(low, high, half), nudge=n % 5)

    # ---------------------------------------------------------------- the head, the shut jaw and the fangs
    head = dict(density=FACE)
    for n, (z0, z1, rows) in enumerate(SKULL):
        tiers(d, HEAD, z0, z1, rows, mat='head', part='skull', nudge=n, **head)
    for n, (z0, z1, rows) in enumerate(JAWS):
        tiers(d, JAW, z0, z1, rows, mat='head', part='jaw', nudge=n, **head)
    teeth = dict(cutout=True, keep=True, mirror=True, density=FACE, part='teeth', open=('up', 'down', 'north', 'south'))
    for z0, z1, half, gum in TEETH:
        d.span(HEAD, (half + 0.05, gum - 1.1, z0), (half + 0.08, gum + 0.15, z1), 'tooth', row='upper', **teeth)
        d.span(JAW, (half + 0.13, gum - 0.35, z0 + 0.1), (half + 0.16, gum + 0.75, z1 - 0.1), 'tooth', row='lower',
               **teeth)

    # ---------------------------------------------------------------- the flippers and the nape's spikes, traced
    names = np.array(sk.names)[np.asarray(sk.face_bone)]
    centroid = sk.source_vertices[np.asarray(sk.triangles)].mean(1)
    for side in ('l', 'r'):
        for bones in ([f'{side}_FlipperA{k}' for k in (1, 2, 3, 4)], [f'{side}_flipperB{k}' for k in (1, 2, 3, 4)]):
            sheet(d, sk, np.isin(names, bones), bones, mat='fin', res=0.15, fin='flipper')
    spine = [f'c_neck{k}' for k in range(1, 8)] + ['c_back4', 'c_back2', 'c_back1']
    top = np.array([body_at(z)[1] for z in centroid[:, 2]])
    chosen = np.isin(names, spine) & (np.abs(centroid[:, 0]) < 0.6) & (centroid[:, 1] > top + 0.1)
    sheet(d, sk, chosen, [b for b in spine if np.any(chosen & (names == b))], mat='spike', res=0.12, fin='ridge',
          thickness=0.12)
    separate(d, DENSITY)


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(hide=hexes('#141c1f', '#1b2529', '#233034', '#2c3b40', '#36474c', '#41545a', '#4d6268', '#5a7077'),
                    belly=hexes('#8d958c', '#9fa79d', '#b0b8ae', '#c0c7bd', '#ced4ca', '#dbe0d7'),
                    spot=hexes('#7c9196', '#8ea3a7', '#a1b5b8'),
                    dark=hexes('#070a0b', '#0c1012', '#121719', '#191f22'),
                    scar=hexes('#8e9294', '#a6aaab', '#bec0bf'),
                    mouth=hexes('#3e1a1c', '#5a2628', '#763536', '#924645', '#a95a56')),
}
PALETTES['slate'] = PALETTES['Natural']
PALETTES['abyss'] = dict(hide=hexes('#0c1322', '#111b2f', '#17243d', '#1e2e4b', '#26395a', '#2f456a', '#3a527a', '#45608a'),
                         belly=hexes('#869099', '#98a2aa', '#aab3ba', '#bbc3c9', '#cad1d6', '#d8dde1'),
                         spot=hexes('#6f86a8', '#8299b9', '#97adc9'),
                         dark=hexes('#04070d', '#080c15', '#0c121d', '#111826'),
                         scar=hexes('#7f8a98', '#98a2ae', '#b2bac3'),
                         mouth=PALETTES['Natural']['mouth'])
PALETTES['umber'] = dict(hide=hexes('#1d150f', '#271c14', '#32241a', '#3e2d20', '#4a3727', '#57412f', '#644c38', '#725841'),
                         belly=hexes('#a0907a', '#b0a18a', '#bfb09a', '#ccbea9', '#d8cbb7', '#e2d7c4'),
                         spot=hexes('#a08870', '#b29a80', '#c4ad93'),
                         dark=hexes('#0b0805', '#110c08', '#17110b', '#1e160f'),
                         scar=hexes('#a39a8d', '#b8b0a3', '#cbc4b8'),
                         mouth=PALETTES['Natural']['mouth'])
for _pal in {id(p): p for p in PALETTES.values()}.values():
    _pal['hide'] = np.clip(_pal['hide'] * 1.12 + 6, 0, 255)
TOOTH_RAMP = hexes('#8f8a7c', '#b3ad9c', '#d2ccba', '#e6e1d2', '#f3f0e6', '#fbfaf4')


def countershade(p):
    z, y = p[..., 2], p[..., 1]
    low = np.interp(z, [b[0] for b in BODY] + [60.0], [b[1] for b in BODY] + [0.6])     # the head: chin to crown
    high = np.interp(z, [b[0] for b in BODY] + [60.0], [b[2] for b in BODY] + [5.6])
    return np.clip((y - low) / np.maximum(high - low, 0.5), 0, 1)


def scales(tx, size=2):
    col, row = face_grid(tx)
    r = row // size
    c = (col + (r % 2) * (size // 2)) // size
    salt = float(tx.face.index % 997)
    tone = cells(np.stack([c.astype(float), r.astype(float), np.full(col.shape, salt)], -1), 1.0, seed=241)
    edge = ((col + (r % 2) * (size // 2)) % size == size - 1) & (row % size == size - 1)
    return 0.08 * (tone - 0.5) - 0.06 * edge


def hide(tx, pal):
    """A dark back with scattered pale spots, fading to a pale belly; pale scars on the flanks."""
    p, n = tx.points, tx.normal
    f = countershade(p)
    if n[1] > 0.5:
        f = np.maximum(f, 0.75)
    mottle = fbm(p, 5.0, seed=242, octaves=2) - 0.5
    v = 0.3 + 0.32 * (1 - f) + 0.12 * mottle + scales(tx)
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    spot = (cells(p, tx.texel * 2.02, seed=243) > 0.9) & (f > 0.4)
    colour = np.where(spot[..., None], shade(pal['spot'], 0.5 + 3 * scales(tx), tx.x, tx.y, dither=0.0), colour)
    belly = shade(pal['belly'], 0.55 + 0.3 * (0.4 - f) + 0.6 * scales(tx), tx.x, tx.y, dither=0.0)
    edge = 0.36 + 0.06 * (fbm(np.stack([p[..., 2] * 0.8, np.sign(p[..., 0]), 0 * p[..., 0]], -1), 1.0, seed=244) - 0.5)
    colour = np.where(((f < edge) | (n[1] < -0.6))[..., None], belly, colour)
    if abs(n[0]) > 0.5:
        z, y = p[..., 2], p[..., 1]
        for z0, y0, length, slope, count in SCARS:
            for k in range(count):
                hit = (np.abs((z - z0) / length) < 0.5) & \
                    (np.abs(y - (y0 + (z - z0) * slope + k * 1.0)) < tx.texel * 0.55)
                colour = np.where(hit[..., None], shade(pal['scar'], 0.4 + 0.5 * (k % 2), tx.x, tx.y, dither=0.0),
                                  colour)
    return colour


def head(tx, pal):
    p, n = tx.points, tx.normal
    colour = hide(tx, pal)
    jaw = tx.box.bone == JAW
    if (jaw and n[1] > 0.5) or (not jaw and n[1] < -0.5):
        colour = shade(pal['mouth'], 0.3 + 0.15 * (cells(p, tx.texel * 1.01, seed=245) - 0.5), tx.x, tx.y)
    if abs(n[0]) > 0.5:
        gum = np.interp(p[..., 2], [59.6, 62.8, 65.8], [2.05, 2.25, 2.05])
        lip = np.abs(p[..., 1] - gum) < (0.22 if not jaw else 0.3)
        colour = np.where(lip[..., None], shade(pal['dark'], 0.5, tx.x, tx.y, dither=0.0), colour)
    if not jaw and n[2] > 0.5 and tx.box.center[2] > 64.5:
        nostril = (np.abs(np.abs(p[..., 0]) - 0.45) < tx.texel * 0.6) & (np.abs(p[..., 1] - 4.2) < tx.texel * 0.6)
        colour = np.where(nostril[..., None], shade(pal['dark'], 0.0, tx.x, tx.y), colour)
    return colour


def fin(tx, pal):
    """A flipper or spike card trimmed to its bone's cells of the ARK mesh: the back's colour on top, the belly's
    below, a dark edge; the spikes dark with pale tips."""
    tag = tx.box.tag
    mask = tag['sheet']
    p = tx.points
    alpha = mask.at(p) == tag['owner']
    off = np.zeros(p.shape[:-1], bool)
    for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        off |= mask.at(p + (mask.u * du + mask.v * dv) * tx.texel) < 0
    grain = cells(p, tx.texel * 1.01, seed=246) - 0.5
    if tag['fin'] == 'ridge':
        top = np.interp(p[..., 2], [b[0] for b in BODY], [b[2] for b in BODY])
        rise = np.clip((p[..., 1] - top) / 0.8, 0, 1)
        colour = shade(pal['dark'], 0.3 + 0.5 * rise + 0.2 * grain, tx.x, tx.y, dither=0.0)
        return colour, alpha
    colour = shade(pal['hide'], 0.42 + 0.08 * grain, tx.x, tx.y, dither=0.0)
    spot = cells(p, tx.texel * 2.02, seed=247) > 0.9
    colour = np.where(spot[..., None], shade(pal['spot'], 0.4, tx.x, tx.y, dither=0.0), colour)
    if tx.normal[1] < 0:
        colour = shade(pal['belly'], 0.5 + 0.08 * grain, tx.x, tx.y, dither=0.0)
    colour = np.where((off & alpha)[..., None], shade(pal['dark'], 0.6, tx.x, tx.y, dither=0.0), colour)
    return colour, alpha


def teeth(tx):
    """Long fangs every third texel, pale at the tips, on a dark gum; the lower row offset to interlock."""
    box = tx.box
    p = tx.points
    along = p[..., 2] / tx.texel
    rows = box.size[1] / tx.texel
    h = (p[..., 1] - (box.center[1] - box.size[1] / 2)) / tx.texel
    if box.tag['row'] == 'upper':
        h = rows - h
    phase = np.abs(((along + (0.0 if box.tag['row'] == 'upper' else 1.5)) % 3.0) / 3.0 - 0.5) * 2
    alpha = (h <= (rows - 0.3) * (1 - phase ** 1.2) + 0.3) | (h < 0.8)
    colour = shade(TOOTH_RAMP, 0.95 - 0.3 * (h < 1.6), tx.x, tx.y, dither=0.0)
    colour = np.where((h < 0.8)[..., None], shade(PALETTES['Natural']['mouth'], 0.2, tx.x, tx.y, dither=0.0), colour)
    return colour, alpha


def painter(tx, variant):
    pal = PALETTES[variant]
    mat = tx.box.mat
    if mat in ('fin', 'spike'):
        return fin(tx, pal)
    if mat == 'tooth':
        return teeth(tx)
    if mat == 'head':
        return head(tx, pal), None
    return hide(tx, pal), None
