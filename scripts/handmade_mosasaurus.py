"""The Mosasaurus, designed box by box on the ARK rig; built by scripts/handmade_models.py.

The great marine lizard in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a deep,
barrel trunk in stacked tiers on a short thick neck, a long tail flattened from side to side that rises at its end
into a tall paddle; a long, narrow head with a heavy lower jaw designed shut as the idle holds it, both jaws lined
with cut-out rows of conical teeth outside the lips. The four paddles and the row of scute spikes from the nape down
the back are cut-out cards traced from the ARK mesh's own triangles (handmade_models.sheet), on their own bones. The
skin is painted scale by scale: a dark back with pale blotches over a pale belly, darker scute spikes, pale scars.
"""
import numpy as np

from ark_geometry import Skeleton
from handmade_megalodon import tiers
from handmade_models import ROOT, cells, fbm, hexes, lift, separate, shade, sheet
from handmade_tyrannosaurus import face_grid

FOLDER, STEM, HEIGHT = 'Mosasaurus', 'mosasaurus', 5.0
DENSITY = 1.0            # texels per runtime unit
FACE = 1.5               # the head, the jaw and the teeth at FACE times that
ATLAS_WIDTH = 512
PREVIEW_VARIANT = 'Natural'
ASSET = 'Mosasaurus'
PREVIEW_CLIPS = ('Mosasaurus-Idle', 'Mosasaurus-Swim-Fwd', 'Mosasaurus-Attack-Bite', 'Mosasaurus-Torpid-Idle')
JAW_POSE = ('Mosasaurus-Idle', 0.0, ['jaw', 'tongue'])     # the frame the jaw is designed in: the idle shuts it
HEAD, JAW = 'c_head', 'c_jaw'

# The body's outline along z: (z, underside, top, half width), from the ARK mesh's sections without the spikes.
BODY = [(-129.5, -0.5, 1.0, 0.3), (-126.0, -3.3, 4.3, 1.0), (-122.0, -5.0, 6.8, 1.45), (-118.0, -5.3, 8.4, 1.5),
        (-114.0, -4.9, 9.2, 1.75), (-110.0, -4.4, 9.24, 2.0), (-106.0, -4.24, 9.0, 2.2), (-102.0, -4.25, 8.7, 2.3),
        (-98.0, -4.4, 8.3, 2.5), (-94.0, -4.56, 7.9, 2.8), (-90.0, -4.7, 7.4, 3.1), (-86.0, -4.77, 7.1, 3.35),
        (-82.0, -4.75, 6.95, 3.6), (-78.0, -4.6, 6.8, 3.8), (-74.0, -4.6, 6.7, 4.0), (-70.0, -4.7, 6.7, 4.2),
        (-66.0, -4.75, 6.9, 4.5), (-62.0, -4.9, 7.1, 4.9), (-58.0, -5.1, 7.5, 5.2), (-54.0, -5.5, 7.8, 5.6),
        (-50.0, -5.85, 8.2, 6.1), (-46.0, -6.2, 8.6, 6.8), (-42.0, -6.8, 9.2, 7.6), (-38.0, -7.75, 9.7, 8.8),
        (-34.0, -8.7, 10.35, 10.0), (-30.0, -9.8, 11.0, 11.2), (-26.0, -11.1, 11.6, 12.2), (-22.0, -12.3, 12.4, 12.8),
        (-18.0, -13.4, 13.6, 13.5), (-14.0, -14.3, 14.5, 14.0), (-10.0, -15.2, 15.0, 14.3), (-6.0, -16.0, 15.0, 14.8),
        (-2.0, -16.5, 14.8, 15.3), (2.0, -16.5, 15.0, 15.2), (6.0, -16.4, 15.0, 15.1), (10.0, -16.1, 15.0, 15.5),
        (14.0, -14.7, 13.6, 14.5), (18.0, -12.6, 12.8, 13.0), (22.0, -11.4, 11.0, 11.5), (26.0, -10.35, 10.2, 9.7),
        (30.0, -9.3, 9.3, 8.5), (34.0, -8.7, 7.6, 8.3), (36.0, -8.6, 7.0, 8.0)]
TIER_SHAPE = [(0.0, 0.1, 0.6), (0.1, 0.3, 0.9), (0.3, 0.7, 1.0), (0.7, 0.88, 0.82), (0.88, 1.0, 0.5)]
# The trunk's slices: (bone, rear z, front z), each bone carrying the body from the next one's pivot to its own.
SLICES = [('c_Tail4', -129.5, -121.0), ('c_Tail4', -121.0, -112.5), ('c_Tail3', -112.5, -103.2),
          ('c_Tail3', -103.2, -93.9), ('c_Tail2', -93.9, -84.6), ('c_Tail2', -84.6, -75.4), ('c_Tail1', -75.4, -66.1),
          ('c_Tail1', -66.1, -56.8), ('c_Tail', -56.8, -47.5), ('c_Tail', -47.5, -38.2), ('c_back4', -38.2, -31.4),
          ('c_back4', -31.4, -24.6), ('c_back3', -24.6, -17.8), ('c_back3', -17.8, -11.0), ('c_back2', -11.0, -4.2),
          ('c_back2', -4.2, 2.5), ('c_back1', 2.5, 10.2), ('c_back1', 10.2, 18.2), ('c_neck', 18.2, 24.0),
          ('c_neck', 24.0, 30.0), ('c_neck', 30.0, 35.6)]
# The upper head, slice by slice: (rear z, front z, rows (bottom, top, half width)); the eye sits on the middle tier
# of the fourth.
SKULL = [(34.6, 38.0, [(-1.0, 2.0, 7.8), (2.0, 5.2, 7.3), (5.2, 6.9, 4.6)]),
         (38.0, 42.0, [(-0.8, 2.0, 7.5), (2.0, 5.0, 6.8), (5.0, 6.9, 4.4)]),
         (42.0, 44.6, [(-1.2, 1.5, 6.7), (1.5, 4.8, 6.0), (4.8, 6.1, 3.9)]),
         (44.6, 48.0, [(-1.3, 1.4, 5.6), (1.4, 3.8, 5.35), (3.8, 5.4, 3.5)]),
         (48.0, 51.0, [(-1.7, 1.0, 4.4), (1.0, 3.6, 3.6)]),
         (51.0, 54.0, [(-1.6, 0.6, 3.3), (0.6, 2.6, 2.6)]),
         (54.0, 56.6, [(-1.5, 1.6, 2.4)]),
         (56.6, 58.1, [(-1.3, 0.4, 1.4)])]
# The lower jaw as the idle holds it, in the same way.
JAWS = [(36.0, 40.0, [(-8.7, -4.5, 5.6), (-4.5, -0.6, 7.3)]),
        (40.0, 44.0, [(-8.2, -4.4, 5.3), (-4.4, -0.7, 6.6)]),
        (44.0, 48.0, [(-7.2, -4.0, 4.4), (-4.0, -1.1, 5.2)]),
        (48.0, 52.0, [(-6.1, -3.6, 3.3), (-3.6, -1.5, 3.9)]),
        (52.0, 55.4, [(-5.2, -1.8, 2.3)])]
# The rows of teeth, outside the lips: (rear z, front z, half width, the upper gum's y).
TEETH = [(38.2, 44.4, 7.0, -0.7), (44.6, 50.8, 5.7, -1.2), (51.0, 56.4, 3.5, -1.5)]


def body_at(z):
    zs = [b[0] for b in BODY]
    return tuple(float(np.interp(z, zs, [b[k] for b in BODY])) for k in (1, 2, 3))


def rows_for(low, high, half):
    h = high - low
    if half < 3.0:                       # the tail, tall and thin: a body and a narrower keel up into the paddle
        return [(low, low + h * 0.55, half), (low + h * 0.55, high, half * 0.55)]
    if half < 9.0:
        return [(low, low + h * 0.25, half * 0.78), (low + h * 0.25, low + h * 0.75, half),
                (low + h * 0.75, high, half * 0.66)]
    return [(low + h * f0, low + h * f1, half * w) for f0, f1, w in TIER_SHAPE]


def design(d):
    rig = d.rig
    for bone in rig.bones:
        if bone == JAW or 'tongue' in bone:
            rig.design_as_in(bone, 'Mosasaurus-Idle', 0.0, HEAD)
    sk = Skeleton(ASSET, ROOT / 'Creatures' / FOLDER / 'source')

    # ---------------------------------------------------------------- the trunk and the tail, slice by slice
    for n, (bone, a, b) in enumerate(SLICES):
        low, high, half = body_at((a + b) / 2)
        tiers(d, bone, a - 0.4, b + 0.4, rows_for(low, high, half), nudge=n % 5)

    # ---------------------------------------------------------------- the head and the shut jaw
    head = dict(density=FACE)
    for n, (z0, z1, rows) in enumerate(SKULL):
        tiers(d, HEAD, z0, z1, rows, mat='head', part='skull', nudge=n, **head)
    for n, (z0, z1, rows) in enumerate(JAWS):
        tiers(d, JAW, z0, z1, rows, mat='head', part='jaw', nudge=n, **head)
    teeth = dict(cutout=True, keep=True, mirror=True, density=FACE, part='teeth')
    for z0, z1, half, gum in TEETH:
        d.span(HEAD, (half + 0.1, gum - 1.5, z0), (half + 0.15, gum + 0.3, z1), 'tooth', row='upper',
               open=('up', 'down', 'north', 'south'), **teeth)
        d.span(JAW, (half + 0.42, gum - 0.5, z0 + 0.3), (half + 0.48, gum + 1.1, z1 - 0.2), 'tooth', row='lower',
               open=('up', 'down', 'north', 'south'), **teeth)
    d.span(HEAD, (-1.3, -2.6, 58.0), (1.3, -1.2, 58.06), 'tooth', row='upper', cutout=True, keep=True, density=FACE,
           part='teeth', open=('up', 'down', 'east', 'west'))
    # the tongue, lying in the jaw
    points = [rig.pivot(f'c_tongue{k}') for k in range(1, 5)]
    for k in range(3):
        d.seg(f'c_tongue{k + 1}', points[k], points[k + 1], 2.4 - 0.5 * k, 0.5, 'tongue', ext=(0.3, 0.3),
              side=(1, 0, 0), part='tongue')

    # ---------------------------------------------------------------- the paddles and the spikes, traced
    names = np.array(sk.names)[np.asarray(sk.face_bone)]
    centroid = sk.source_vertices[np.asarray(sk.triangles)].mean(1)
    fins = dict(mat='fin', res=0.3)
    for side in ('l', 'r'):
        bones = [f'{side}_sideFlipper{k}' for k in (1, 2, 3)]
        sheet(d, sk, np.isin(names, bones), bones, fin='paddle', **fins)
        bones = [f'{side}_bellyFlipper{k}' for k in (1, 2)]
        sheet(d, sk, np.isin(names, bones), bones, fin='paddle', **fins)
    spine = ['c_neck', 'c_back1', 'c_back2', 'c_back3', 'c_back4']
    top = np.array([body_at(z)[1] for z in centroid[:, 2]])
    sheet(d, sk, np.isin(names, spine) & (np.abs(centroid[:, 0]) < 1.2) & (centroid[:, 1] > top - 0.2), spine,
          mat='spike', res=0.3, fin='ridge', thickness=0.3)
    separate(d, DENSITY)


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(hide=hexes('#151b22', '#1c242d', '#243039', '#2d3b46', '#374754', '#425462', '#4e6271', '#5b7080'),
                    belly=hexes('#8f9a96', '#a1aba6', '#b2bbb5', '#c2c9c3', '#d0d6cf', '#dde1da'),
                    blot=hexes('#5d6f7a', '#6c7f8a', '#7c8f99'),
                    dark=hexes('#080b0e', '#0d1115', '#13181d', '#1a2026'),
                    scar=hexes('#8e9294', '#a6aaab', '#bec0bf'),
                    mouth=hexes('#3e1a1c', '#5a2628', '#763536', '#924645', '#a95a56')),
}
PALETTES['slate'] = PALETTES['Natural']
PALETTES['abyss'] = dict(hide=hexes('#0c1322', '#111b2f', '#17243d', '#1e2e4b', '#26395a', '#2f456a', '#3a527a', '#45608a'),
                         belly=hexes('#869099', '#98a2aa', '#aab3ba', '#bbc3c9', '#cad1d6', '#d8dde1'),
                         blot=hexes('#4d6380', '#5b7290', '#6a82a0'),
                         dark=hexes('#04070d', '#080c15', '#0c121d', '#111826'),
                         scar=hexes('#7f8a98', '#98a2ae', '#b2bac3'),
                         mouth=PALETTES['Natural']['mouth'])
PALETTES['charcoal'] = lift(dict(hide=hexes('#101112', '#161718', '#1c1e1f', '#232527', '#2b2d30', '#333639', '#3c3f43', '#46494d'),
                                 belly=hexes('#85847f', '#979691', '#a8a7a2', '#b8b7b2', '#c7c6c1', '#d4d3ce'),
                                 blot=hexes('#55585c', '#63666a', '#727579'),
                                 dark=hexes('#050505', '#09090a', '#0d0d0f', '#121214'),
                                 scar=hexes('#7a7977', '#93918e', '#acaaa6'),
                                 mouth=PALETTES['Natural']['mouth']), 1.15, 4)
PALETTES['umber'] = dict(hide=hexes('#1d150f', '#271c14', '#32241a', '#3e2d20', '#4a3727', '#57412f', '#644c38', '#725841'),
                         belly=hexes('#a0907a', '#b0a18a', '#bfb09a', '#ccbea9', '#d8cbb7', '#e2d7c4'),
                         blot=hexes('#7a6450', '#89735e', '#98826c'),
                         dark=hexes('#0b0805', '#110c08', '#17110b', '#1e160f'),
                         scar=hexes('#a39a8d', '#b8b0a3', '#cbc4b8'),
                         mouth=PALETTES['Natural']['mouth'])
for _pal in {id(p): p for p in PALETTES.values()}.values():
    _pal['hide'] = np.clip(_pal['hide'] * 1.12 + 6, 0, 255)
TOOTH_RAMP = hexes('#8f8a7c', '#b3ad9c', '#d2ccba', '#e6e1d2', '#f3f0e6', '#fbfaf4')
TONGUE_RAMP = hexes('#4a1c1e', '#62282a', '#7a3536', '#924442')
SCARS = [(6.0, 6.0, 9.0, -0.3, 3), (-20.0, 2.0, 8.0, 0.25, 2), (26.0, 2.0, 5.0, -0.4, 2), (-52.0, 3.0, 7.0, 0.1, 2)]


def countershade(p):
    z, y = p[..., 2], p[..., 1]
    low = np.interp(z, [b[0] for b in BODY], [b[1] for b in BODY])
    high = np.interp(z, [b[0] for b in BODY], [b[2] for b in BODY])
    return np.clip((y - low) / np.maximum(high - low, 0.5), 0, 1)


def scales(tx, size=2):
    """Small scales in staggered rows on the face's own texel grid: each its own tone, a dark rear edge."""
    col, row = face_grid(tx)
    r = row // size
    c = (col + (r % 2) * (size // 2)) // size
    salt = float(tx.face.index % 997)
    tone = cells(np.stack([c.astype(float), r.astype(float), np.full(col.shape, salt)], -1), 1.0, seed=231)
    edge = ((col + (r % 2) * (size // 2)) % size == size - 1) & (row % size == size - 1)
    return 0.08 * (tone - 0.5) - 0.07 * edge


def scars(tx, pal, colour):
    p = tx.points
    z, y = p[..., 2], p[..., 1]
    for z0, y0, length, slope, count in SCARS:
        for k in range(count):
            t = (z - z0) / length
            dy = y - (y0 + (z - z0) * slope + k * 1.8)
            hit = (np.abs(t) < 0.5) & (np.abs(dy) < tx.texel * 0.55)
            colour = np.where(hit[..., None], shade(pal['scar'], 0.4 + 0.5 * (k % 2), tx.x, tx.y, dither=0.0), colour)
    return colour


def hide(tx, pal):
    """A dark back fading through the flanks to a pale belly, pale blotches scattered over the back and flanks."""
    p, n = tx.points, tx.normal
    f = countershade(p)
    if n[1] > 0.5:
        f = np.maximum(f, 0.75)                       # ledges seen from above stay dark
    mottle = fbm(p, 9.0, seed=232, octaves=2) - 0.5
    v = 0.3 + 0.35 * (1 - f) + 0.12 * mottle + scales(tx)
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    spots = (fbm(p * [1, 1.4, 0.7], 2.2, seed=233, octaves=2) > 0.7) & (f > 0.45)
    colour = np.where(spots[..., None], shade(pal['blot'], 0.5 + 2 * scales(tx), tx.x, tx.y, dither=0.0), colour)
    belly = shade(pal['belly'], 0.55 + 0.3 * (0.42 - f) + 0.6 * scales(tx), tx.x, tx.y, dither=0.0)
    edge = 0.36 + 0.05 * (fbm(np.stack([p[..., 2] * 0.5, np.sign(p[..., 0]), 0 * p[..., 0]], -1), 1.0, seed=234) - 0.5)
    colour = np.where(((f < edge) | (n[1] < -0.6))[..., None], belly, colour)
    if abs(n[0]) > 0.5 or n[1] > 0.5:
        colour = scars(tx, pal, colour)
    return colour


def head(tx, pal):
    """The head as the body, finer; the mouth's inside dark red and dark lips under the rows of teeth."""
    p, n = tx.points, tx.normal
    colour = hide(tx, pal)
    jaw = tx.box.bone == JAW
    if (jaw and n[1] > 0.5) or (not jaw and n[1] < -0.5):
        colour = shade(pal['mouth'], 0.3 + 0.15 * (cells(p, tx.texel * 1.01, seed=235) - 0.5), tx.x, tx.y)
    if abs(n[0]) > 0.5:
        lip = (p[..., 1] < (-0.1 if not jaw else -10)) | (jaw & (p[..., 1] > -1.6 - 0.04 * (p[..., 2] - 40)))
        colour = np.where(lip[..., None], shade(pal['dark'], 0.5, tx.x, tx.y, dither=0.0), colour)
    if not jaw and n[2] > 0.5 and tx.box.center[2] > 56.0:     # the nostrils on the snout's tip
        nostril = (np.abs(np.abs(p[..., 0]) - 0.6) < tx.texel * 0.6) & (p[..., 1] > -0.2)
        colour = np.where(nostril[..., None], shade(pal['dark'], 0.0, tx.x, tx.y), colour)
    return colour


def fin(tx, pal):
    """A paddle or a spike card trimmed to its bone's cells of the ARK mesh: the back's colour on top, the belly's
    below, a dark rim; the spikes dark with pale tips."""
    tag = tx.box.tag
    mask = tag['sheet']
    p = tx.points
    alpha = mask.at(p) == tag['owner']
    off = np.zeros(p.shape[:-1], bool)
    for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        off |= mask.at(p + (mask.u * du + mask.v * dv) * tx.texel) < 0
    grain = cells(p, tx.texel * 1.01, seed=236) - 0.5
    if tag['fin'] == 'ridge':
        top = np.interp(p[..., 2], [b[0] for b in BODY], [b[2] for b in BODY])
        rise = np.clip((p[..., 1] - top) / 2.5, 0, 1)
        colour = shade(pal['dark'], 0.3 + 0.5 * rise + 0.2 * grain, tx.x, tx.y, dither=0.0)
        colour = np.where((off & (rise > 0.5))[..., None], shade(pal['blot'], 0.6, tx.x, tx.y, dither=0.0), colour)
        return colour, alpha
    colour = shade(pal['hide'], 0.4 + 0.08 * grain, tx.x, tx.y, dither=0.0)
    if tx.normal[1] < 0:
        colour = shade(pal['belly'], 0.5 + 0.08 * grain, tx.x, tx.y, dither=0.0)
    col, row = face_grid(tx)
    digits = (col % 3 == 0)                                     # the paddle's finger bones showing as faint ridges
    colour = np.where(digits[..., None], colour * 0.88, colour)
    colour = np.where((off & alpha)[..., None], shade(pal['dark'], 0.6, tx.x, tx.y, dither=0.0), colour)
    return colour, alpha


def teeth(tx):
    """Conical teeth every third texel, pale at the tips, on a dark gum."""
    box = tx.box
    p = tx.points
    along = (p[..., 2] if abs(tx.normal[0]) > 0.5 else p[..., 0]) / tx.texel
    rows = box.size[1] / tx.texel
    h = (p[..., 1] - (box.center[1] - box.size[1] / 2)) / tx.texel
    if box.tag['row'] == 'upper':
        h = rows - h
    phase = np.abs(((along + (0.0 if box.tag['row'] == 'upper' else 1.5)) % 3.0) / 3.0 - 0.5) * 2
    alpha = (h <= (rows - 0.3) * (1 - phase ** 1.5) + 0.3) | (h < 0.8)
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
    if mat == 'tongue':
        return shade(TONGUE_RAMP, 0.5 + 0.4 * (cells(tx.points, tx.texel * 1.01, seed=237) - 0.5), tx.x, tx.y), None
    return hide(tx, pal), None
