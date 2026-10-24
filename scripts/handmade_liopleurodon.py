"""The Liopleurodon, designed box by box on the ARK rig; built by scripts/handmade_models.py.

The short-necked pliosaur in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a deep,
tear-drop trunk in stacked tiers tapering to a short pointed tail, a thick short neck and a huge, long head; the jaw
is designed shut as the idle holds it, both jaws lined with long cut-out fangs outside the lips and a barbed spike
hanging from the chin on its own bones. The four flippers and the row of spikes down the back are cut-out cards
traced from the ARK mesh's own triangles (handmade_models.sheet). The skin is a dark back crossed by darker tiger
bands and speckled pale over a pale belly, darker flipper edges and pale scars.
"""
import numpy as np

from ark_geometry import Skeleton
from handmade_megalodon import tiers
from handmade_models import ROOT, cells, fbm, hexes, lift, separate, shade, sheet
from handmade_tyrannosaurus import face_grid

FOLDER, STEM, HEIGHT = 'Liopleurodon', 'liopleurodon', 2.5
DENSITY = 0.8            # texels per runtime unit (1.4 runtime units per source unit)
FACE = 1.5               # the head, the jaw and the teeth at FACE times that
ATLAS_WIDTH = 512
PREVIEW_VARIANT = 'Natural'
ASSET = 'Liopleurodon'
PREVIEW_CLIPS = ('Liopleurodon-Idle', 'Liopleurodon-Swim-Fwd', 'Liopleurodon-Attack-Chomp', 'Liopleurodon-Spin')
JAW_POSE = ('Liopleurodon-Idle', 0.0, ['jaw', 'tongue'])     # the frame the jaw is designed in: the idle shuts it
HEAD, JAW = 'c_neck8', 'c_jaw'

# The body's outline along z: (z, underside, top, half width), from the ARK mesh's sections without the spikes.
BODY = [(-44.9, -1.1, -0.9, 0.12), (-44.5, -1.3, -0.8, 0.2), (-42.5, -1.9, -0.6, 0.6), (-40.5, -2.3, -0.35, 0.95),
        (-38.5, -2.7, -0.07, 1.3), (-36.5, -3.1, 0.3, 1.65), (-34.5, -3.5, 0.75, 2.0), (-32.5, -3.9, 1.3, 2.3),
        (-30.5, -4.2, 1.8, 2.65), (-28.5, -4.5, 2.5, 3.0), (-26.5, -4.8, 3.2, 3.35), (-24.5, -5.0, 4.0, 3.7),
        (-22.5, -5.2, 5.0, 4.2), (-20.5, -5.3, 6.0, 4.75), (-18.5, -5.3, 7.2, 5.3), (-16.5, -5.2, 8.3, 5.95),
        (-14.5, -5.2, 9.2, 6.6), (-12.5, -5.6, 9.8, 7.4), (-10.5, -6.2, 10.4, 8.1), (-8.5, -6.6, 10.8, 8.5),
        (-6.5, -6.9, 11.1, 8.6), (-4.5, -6.95, 11.3, 8.8), (-2.5, -6.9, 11.4, 8.9), (-0.5, -6.6, 11.4, 8.75),
        (1.5, -6.2, 11.3, 8.5), (3.5, -5.7, 11.1, 8.15), (5.5, -5.2, 10.9, 7.75), (7.5, -4.6, 10.7, 7.7),
        (9.5, -4.3, 10.3, 7.6), (11.5, -4.0, 9.5, 7.2), (13.0, -3.4, 8.7, 6.2), (15.0, -2.6, 8.3, 5.1),
        (17.0, -1.9, 8.5, 4.45), (19.0, -1.3, 8.7, 4.25), (21.0, -0.8, 8.9, 4.33), (23.0, -0.55, 9.0, 4.45),
        (24.6, 0.3, 9.0, 4.4)]
TIER_SHAPE = [(0.0, 0.12, 0.62), (0.12, 0.32, 0.92), (0.32, 0.66, 1.0), (0.66, 0.86, 0.8), (0.86, 1.0, 0.45)]
# The slices: (bone, rear z, front z); the spine's bones carry the body forward from their pivots, the tail's back.
SLICES = [('c_tail4', -44.9, -38.8), ('c_tail3', -38.8, -32.8), ('c_tail2', -32.8, -24.9), ('c_tail1', -24.9, -17.1),
          ('c_back1', -17.1, -10.1), ('c_back1', -10.1, -4.76), ('c_back2', -4.76, 0.89), ('c_back3', 0.89, 7.25),
          ('c_back4', 7.25, 12.77), ('c_neck1', 12.77, 17.46), ('c_neck3', 17.46, 21.18), ('c_neck6', 21.18, 24.6)]
# The head, slice by slice: (rear z, front z, rows (bottom, top, half width)); the eye on the third's middle tier.
SKULL = [(24.2, 27.0, [(3.4, 6.0, 4.3), (6.0, 8.0, 3.8), (8.0, 9.0, 2.4)]),
         (27.0, 29.6, [(2.6, 4.9, 3.9), (4.9, 7.2, 3.45), (7.2, 8.4, 2.6)]),
         (29.6, 32.4, [(3.0, 5.5, 3.3), (5.5, 7.4, 2.6)]),
         (32.4, 35.5, [(3.0, 5.0, 2.8), (5.0, 6.7, 2.1)]),
         (35.5, 38.5, [(3.0, 4.6, 2.3), (4.6, 5.9, 1.7)]),
         (38.5, 41.3, [(2.7, 4.2, 1.8), (4.2, 5.2, 1.3)]),
         (41.3, 43.0, [(2.2, 4.2, 1.1)])]
# The lower jaw as the idle holds it.
JAWS = [(25.0, 28.6, [(-0.9, 1.0, 2.6), (1.0, 3.0, 3.95)]), (28.6, 32.2, [(-0.8, 1.0, 2.2), (1.0, 2.95, 3.35)]),
        (32.2, 36.0, [(-0.6, 1.0, 1.7), (1.0, 2.9, 2.7)]), (36.0, 39.5, [(-0.5, 2.8, 2.15)]),
        (39.5, 41.4, [(-0.4, 2.5, 1.5)])]
# The fangs, outside the lips: (rear z, front z, half width, the gum's y).
TEETH = [(25.4, 28.4, 4.0, 3.2), (28.8, 32.2, 3.45, 3.0), (32.6, 35.4, 2.95, 3.0), (35.7, 38.4, 2.35, 3.0),
         (38.7, 41.2, 1.85, 2.8)]
SCARS = [(-4.0, 6.0, 7.0, -0.3, 3), (8.0, 4.0, 5.0, 0.25, 2), (-20.0, 3.0, 4.0, -0.2, 2), (31.0, 5.6, 3.0, 0.3, 2)]


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
            rig.design_as_in(bone, 'Liopleurodon-Idle', 0.0, HEAD)
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
        d.span(HEAD, (half + 0.05, gum - 1.9, z0), (half + 0.1, gum + 0.2, z1), 'tooth', row='upper', **teeth)
        d.span(JAW, (half + 0.16, gum - 0.5, z0 + 0.1), (half + 0.21, gum + 1.3, z1 - 0.1), 'tooth', row='lower',
               **teeth)

    front = dict(cutout=True, keep=True, density=FACE, part='teeth', open=('up', 'down', 'east', 'west'))
    d.span(HEAD, (-1.1, 0.6, 42.9), (1.1, 2.6, 42.95), 'tooth', row='upper', **front)
    d.span(JAW, (-1.4, 2.2, 41.42), (1.4, 3.8, 41.47), 'tooth', row='lower', **front)
    # the barbed spike hanging from the chin, on its own bones
    points = [rig.pivot(f'c_jawSpike{k}') for k in range(1, 6)]
    for k in range(4):
        d.seg(f'c_jawSpike{k + 1}', points[k], points[k + 1], 0.42 - 0.07 * k, 0.42 - 0.07 * k, 'barb',
              ext=(0.1, 0.05), side=(1, 0, 0), part='barb')

    # ---------------------------------------------------------------- the flippers and the back's spikes, traced
    names = np.array(sk.names)[np.asarray(sk.face_bone)]
    centroid = sk.source_vertices[np.asarray(sk.triangles)].mean(1)
    for side in ('l', 'r'):
        for bones in ([f'{side}_FlipperA{k}' for k in (1, 2, 3, 4)], [f'{side}_flipperB{k}' for k in (1, 2, 3, 4)]):
            sheet(d, sk, np.isin(names, bones), bones, mat='fin', res=0.2, fin='flipper')
    spine = ['c_neck1', 'c_neck3', 'c_neck6', 'c_back4', 'c_back2', 'c_back1', 'c_tail1']
    top = np.array([body_at(z)[1] for z in centroid[:, 2]])
    chosen = np.isin(names, spine) & (np.abs(centroid[:, 0]) < 0.8) & (centroid[:, 1] > top - 0.2)
    sheet(d, sk, chosen, [b for b in spine if np.any(chosen & (names == b))], mat='spike', res=0.15, fin='ridge',
          thickness=0.15)
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
PALETTES['charcoal'] = lift(dict(hide=hexes('#101112', '#161718', '#1c1e1f', '#232527', '#2b2d30', '#333639', '#3c3f43', '#46494d'),
                                 belly=hexes('#85847f', '#979691', '#a8a7a2', '#b8b7b2', '#c7c6c1', '#d4d3ce'),
                                 spot=hexes('#6a6d70', '#7a7d80', '#8b8e91'),
                                 dark=hexes('#050505', '#09090a', '#0d0d0f', '#121214'),
                                 scar=hexes('#7a7977', '#93918e', '#acaaa6'),
                                 mouth=PALETTES['Natural']['mouth']), 1.15, 4)
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
    low = np.interp(z, [b[0] for b in BODY] + [27.0], [b[1] for b in BODY] + [-0.9])     # the head: chin to crown
    high = np.interp(z, [b[0] for b in BODY] + [27.0], [b[2] for b in BODY] + [8.6])
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
    wave = p[..., 2] / 3.4 + 0.5 * np.sin(p[..., 0] * 0.6) + 0.4 * (fbm(p, 4.0, seed=248, octaves=2) - 0.5)
    band = (np.abs((wave % 1.0) - 0.5) < 0.13 + 0.2 * np.clip(f - 0.6, 0, 1)) & (f > 0.5)
    colour = np.where(band[..., None], shade(pal['dark'], 0.55 + 2 * scales(tx), tx.x, tx.y, dither=0.0), colour)
    spot = (cells(p, tx.texel * 2.02, seed=243) > 0.93) & (f > 0.4) & ~band
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
        gum = np.interp(p[..., 2], [25.0, 30.0, 41.0], [3.2, 3.0, 2.8])
        lip = (p[..., 1] - gum > -1.9) & (p[..., 1] - gum < 0.25) if jaw else np.abs(p[..., 1] - gum) < 0.3
        # the jaw dark behind the hanging fangs, so they stand out white
        colour = np.where(lip[..., None], shade(pal['dark'], 0.5, tx.x, tx.y, dither=0.0), colour)
    if not jaw and n[2] > 0.5 and tx.box.center[2] > 42.0:
        nostril = (np.abs(np.abs(p[..., 0]) - 0.45) < tx.texel * 0.6) & (np.abs(p[..., 1] - 3.6) < tx.texel * 0.6)
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
    if mat == 'barb':
        return shade(TOOTH_RAMP, 0.35 + 0.4 * (cells(tx.points, tx.texel * 1.01, seed=249) - 0.5), tx.x, tx.y), None
    return hide(tx, pal), None
