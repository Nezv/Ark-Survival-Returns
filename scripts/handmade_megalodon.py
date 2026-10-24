"""The Megalodon, designed box by box on the ARK rig; built by scripts/handmade_models.py.

The giant shark in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a deep, torpedo
body in stacked tiers round above a broad belly, thickest behind the pectorals and narrowing to a slim tail stalk; a
conical snout over a heavy lower jaw, both lined with cut-out rows of serrated teeth that stand out of the closed
mouth. The fins are cut-out cards traced from the ARK mesh's own fin triangles (handmade_models.sheet), one per fin
bone so they bend with the clips: the tall first dorsal, the sickle pectorals, the pelvics, the small second dorsal
and anal fins and the two lobes of the crescent tail. The skin is a hard countershade, slate above an irregular line
and white below, fine denticle grain, five gill slits, pale rake scars and dark fin tips.
"""
import numpy as np

from ark_geometry import Skeleton
from handmade_models import ROOT, cells, fbm, hexes, lift, separate, shade, sheet, unit

FOLDER, STEM, HEIGHT = 'Megalodon', 'megalodon', 3.0
DENSITY = 1.0            # texels per runtime unit
FACE = 2.0               # the head, the jaw and the teeth at FACE times that
ATLAS_WIDTH = 512
PREVIEW_VARIANT = 'Natural'
ASSET = 'Megalodon'
PREVIEW_CLIPS = ('Megalodon-Idle', 'Megalodon-Swim-Fwd', 'Megalodon-Attack-Bite', 'Megalodon-Eat')
JAW_POSE = ('Megalodon-Idle', 0.0, ['jaw'])          # the bind pose and the idle both hold the jaw shut
HEAD, JAW = 'c_head', 'c_jaw'

# The body's outline along z: (z, underside, top, half width), from the ARK mesh's sections with the fins left out.
BODY = [(-53.0, -0.6, 3.8, 0.9), (-51.0, -1.0, 4.0, 1.1), (-48.0, -1.5, 5.0, 1.5), (-45.0, -2.0, 5.6, 2.3),
        (-42.0, -2.4, 6.0, 3.0), (-39.0, -2.5, 6.0, 3.7), (-36.0, -3.25, 6.15, 4.25), (-33.0, -3.4, 7.0, 4.8),
        (-30.0, -3.8, 7.2, 5.3), (-27.0, -4.5, 7.5, 5.9), (-24.0, -5.3, 8.0, 6.5), (-21.0, -6.1, 8.7, 7.3),
        (-18.0, -7.2, 9.3, 8.3), (-15.0, -8.4, 10.0, 9.1), (-12.0, -9.6, 11.6, 9.9), (-9.0, -11.2, 13.0, 10.9),
        (-6.0, -12.9, 14.0, 11.8), (-3.0, -14.2, 14.6, 12.6), (0.0, -15.0, 15.0, 13.35), (3.0, -15.7, 14.5, 13.7),
        (6.0, -16.0, 14.5, 14.1), (9.0, -16.2, 14.4, 14.4), (12.0, -16.4, 13.9, 14.9), (15.0, -16.45, 14.0, 15.0),
        (18.0, -16.5, 14.4, 15.4), (21.0, -16.5, 14.8, 15.5), (24.0, -16.4, 14.4, 15.5), (27.0, -16.1, 14.8, 15.7),
        (30.0, -15.8, 15.1, 15.8), (33.0, -15.6, 14.3, 15.6), (36.0, -15.2, 13.5, 15.4), (39.0, -14.7, 12.6, 15.2),
        (42.0, -14.1, 11.9, 14.6), (45.0, -13.3, 10.9, 14.1), (48.0, -12.5, 10.4, 13.3), (51.0, -11.4, 10.0, 12.5),
        (54.0, -10.2, 9.3, 11.0), (57.0, -8.6, 8.4, 9.3), (60.0, -5.6, 7.4, 8.0), (63.0, -2.9, 6.3, 6.3),
        (66.0, -1.4, 5.3, 4.6), (69.4, 1.2, 3.9, 1.6)]
# A shark's cross-section: (from, to as shares of the height, half width as a share of the widest), belly to back.
TIER_SHAPE = [(0.0, 0.1, 0.58), (0.1, 0.28, 0.88), (0.28, 0.68, 1.0), (0.68, 0.86, 0.8), (0.86, 1.0, 0.45)]
# The trunk's slices: (bone, rear z, front z), each bone carrying the body from the next one's pivot to its own.
SLICES = [('c_realTail', -53.0, -46.0), ('c_back4', -46.4, -39.5), ('c_back4', -39.5, -32.5), ('c_back4', -32.5, -25.5),
          ('c_back3', -25.9, -19.0), ('c_back3', -19.0, -12.0), ('c_back3', -12.0, -5.0), ('c_back2', -5.4, 1.5),
          ('c_back2', 1.5, 8.5), ('c_back2', 8.5, 15.5), ('c_back1', 15.1, 22.0), ('c_back1', 22.0, 29.0),
          ('c_back1', 29.0, 36.0), ('c_back1', 36.0, 43.0)]
# The mouth's line, back to front: (z, half width, y) where the upper and the lower teeth meet.
MOUTH = [(47.0, 11.6, -2.3), (50.5, 10.8, -2.8), (54.0, 9.2, -3.4), (57.0, 7.1, -4.0), (59.6, 4.6, -4.5),
         (61.2, 0.0, -4.7)]
EYE = (56.8, 3.7)        # (z, y) of the eye on the head's side (the eye overlay sits on a flat face there)


def body_at(z):
    zs = [b[0] for b in BODY]
    return tuple(float(np.interp(z, zs, [b[k] for b in BODY])) for k in (1, 2, 3))


def mouth_at(z):
    zs = [m[0] for m in MOUTH]
    return tuple(float(np.interp(z, zs, [m[k] for m in MOUTH])) for k in (1, 2))


def tiers(d, bone, z0, z1, rows, mat='hide', part='trunk', nudge=0, unit=1.0, **tag):
    """Stacked boxes across a slice, each overlapping the next a little; the tiers off the core are inset a hair
    at both ends (each slice its own hair), so no two neighbours share a plane. `unit` scales those hairs for rigs
    built small in source units (a bird's head is a few units long)."""
    core = max(range(len(rows)), key=lambda k: rows[k][2])
    for k, (y0, y1, half) in enumerate(rows):
        e = 0.0 if k == core else (0.12 * abs(k - core) + 0.03 * nudge) * unit
        lo = y0 - (0.1 * unit if k > core else 0.0)
        hi = y1 + (0.1 * unit if k < core else 0.0)
        d.span(bone, (-half, lo, z0 + e), (half, hi, z1 - e), mat, part=part, tier=k - core, **tag)


def rows_for(low, high, half, shape=TIER_SHAPE):
    h = high - low
    if half < 2.5:
        return [(low, high, half)]
    if half < 6.0:
        return [(low, low + h * 0.25, half * 0.75), (low + h * 0.25, low + h * 0.75, half),
                (low + h * 0.75, high, half * 0.62)]
    return [(low + h * f0, low + h * f1, half * w) for f0, f1, w in shape]


def teeth_cards(d, bone, row, z0, z1, half, y0, y1, front=False):
    """Cut-out cards of teeth just outside a lip box: one along each side at +-half, and with `front` one across its
    front end; hanging from the upper jaw (row 'upper') or standing on the lower one."""
    tag = dict(cutout=True, keep=True, density=FACE, part='teeth', row=row)
    t = 0.05 if row == 'upper' else 0.08
    d.span(bone, (half + 0.1, y0, z0 + 0.1), (half + 0.1 + t, y1, z1 - 0.1), 'tooth', mirror=True,
           open=('up', 'down', 'north', 'south'), **tag)
    if front:
        d.span(bone, (-half, y0, z1 + 0.1), (half, y1, z1 + 0.1 + t), 'tooth', open=('up', 'down', 'east', 'west'),
               **tag)


# The lips, under the snout's overhang and over the chin: (rear z, front z, half width); the teeth stand just
# outside them, the upper row over the lower.
LIPS_UP = [(47.3, 51.0, 10.6), (51.0, 54.3, 9.0), (54.3, 57.0, 7.6), (57.0, 59.4, 5.4), (59.4, 61.3, 3.0)]
LIPS_LOW = [(45.6, 49.5, 9.8), (49.5, 53.5, 8.4), (53.5, 57.2, 6.6), (57.2, 60.3, 3.9)]


def design(d):
    sk = Skeleton(ASSET, ROOT / 'Creatures' / FOLDER / 'source')

    # ---------------------------------------------------------------- the trunk, slice by slice
    for n, (bone, a, b) in enumerate(SLICES):
        low, high, half = body_at((a + b) / 2)
        tiers(d, bone, a - 0.3, b + 0.3, rows_for(low, high, half), nudge=n % 5)

    # ---------------------------------------------------------------- the head: the gills' throat, the snout
    head = dict(density=FACE)
    low, high, half = body_at(45.0)
    tiers(d, HEAD, 42.6, 47.5, rows_for(low, high, half), mat='head', part='skull', **head)
    for n, (z0, z1, rows) in enumerate((                       # over the lips, from the mouth's line up
            (47.5, 51.0, [(-1.2, 4.5, 13.0), (4.5, 8.4, 11.6), (8.4, 10.3, 7.4)]),
            (51.0, 54.3, [(-1.6, 1.6, 12.0), (1.6, 6.4, 11.2), (6.4, 9.8, 7.4)]),
            (54.3, 59.4, [(-2.3, 1.6, 9.9), (1.6, 6.0, 8.6), (6.0, 9.0, 6.0)]),          # the eye on the middle tier
            (59.4, 62.6, [(-3.2, 0.8, 7.3), (0.8, 5.6, 6.6), (5.6, 7.4, 4.4)]),
            (62.6, 65.6, [(-3.0, 1.2, 5.6), (1.2, 6.3, 5.0)]),
            (65.6, 68.0, [(-1.2, 2.2, 4.0), (2.2, 5.4, 3.3)]),
            (68.0, 69.6, [(1.0, 4.2, 2.0)]))):
        tiers(d, HEAD, z0, z1, rows, mat='head', part='snout', nudge=n + 2, **head)
    for k, (z0, z1, half) in enumerate(LIPS_UP):
        m = mouth_at((z0 + z1) / 2)[1]
        d.span(HEAD, (-half, m - 0.15, z0), (half, m + 1.55, z1), 'head', part='lip', **head)
        teeth_cards(d, HEAD, 'upper', z0, z1, half, m - 1.3, m + 1.1, front=k == len(LIPS_UP) - 1)
    # the lower jaw, shut as the idle holds it: the chin and the lip with its row of teeth
    for n, (z0, z1, rows) in enumerate((
            (45.2, 49.5, [(-13.0, -8.0, 9.6), (-8.0, -3.4, 11.4)]),
            (49.5, 53.5, [(-11.8, -7.5, 8.6), (-7.5, -4.0, 10.4)]),
            (53.5, 57.2, [(-10.0, -6.8, 6.8), (-6.8, -4.8, 8.6)]),
            (57.2, 60.6, [(-8.0, -6.6, 4.4), (-6.6, -5.4, 6.2)]))):
        tiers(d, JAW, z0, z1, rows, mat='head', part='jaw', nudge=n, **head)
    for k, (z0, z1, half) in enumerate(LIPS_LOW):
        m = mouth_at((z0 + z1) / 2)[1]
        d.span(JAW, (-half, m - 1.45, z0), (half, m - 0.1, z1), 'head', part='lip', **head)
        teeth_cards(d, JAW, 'lower', z0, z1, half, m - 1.0, m + 0.9, front=k == len(LIPS_LOW) - 1)

    # ---------------------------------------------------------------- the fins, traced from the ARK mesh
    names = np.array(sk.names)[np.asarray(sk.face_bone)]
    tris = np.asarray(sk.triangles)
    centroid = sk.source_vertices[tris].mean(1)
    fins = dict(mat='fin', res=0.25)
    sheet(d, sk, np.isin(names, ['c_BackFlipperA1', 'c_BackFlipperA2']), ['c_BackFlipperA1', 'c_BackFlipperA2'],
          fin='dorsal', **fins)
    for side in ('l', 'r'):
        bones = [f'{side}_sideFlipper{k}' for k in (1, 2, 3)]
        sheet(d, sk, np.isin(names, bones), bones, fin='pectoral', **fins)
        bones = [f'{side}_bellyFlipper{k}' for k in (1, 2)]
        sheet(d, sk, np.isin(names, bones), bones, fin='pelvic', **fins)
    for lobe in ('A', 'C'):
        bones = [f'c_realTail{lobe}{k}' for k in (1, 2, 3)]
        sheet(d, sk, np.isin(names, bones), bones, fin='tail', **fins)
    rear = np.isin(names, ['c_back4', 'c_realTail']) & (np.abs(centroid[:, 0]) < 1.4)
    zc, yc = centroid[:, 2], centroid[:, 1]
    top = np.array([body_at(z)[1] for z in zc])
    under = np.array([body_at(z)[0] for z in zc])
    sheet(d, sk, rear & (yc > top + 0.3) & (zc < -36), ['c_back4', 'c_realTail'], fin='dorsal2', **fins)
    sheet(d, sk, rear & (yc < under - 0.3) & (zc < -34) & (zc > -47), ['c_back4', 'c_realTail'], fin='anal', **fins)
    separate(d, DENSITY)


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(hide=hexes('#1c232b', '#252e38', '#2f3a46', '#3a4754', '#465462', '#536271', '#617181', '#708191'),
                    belly=hexes('#a9adaf', '#bcc0c1', '#cdd0d0', '#dcdedd', '#e8e9e7', '#f2f2ef'),
                    dark=hexes('#0b0e12', '#11161b', '#181e25', '#20272f'),
                    scar=hexes('#8e8f92', '#a8a9aa', '#c3c2c0', '#d9d6d1'),
                    gum=hexes('#3a1216', '#561c20', '#72282a', '#8e3836', '#a64a44')),
}
PALETTES['slate'] = PALETTES['Natural']
PALETTES['abyss'] = dict(hide=hexes('#0f1726', '#152034', '#1b2a42', '#223550', '#2a415f', '#344e6e', '#3f5c7e', '#4b6a8e'),
                         belly=hexes('#93a0ad', '#a6b2bd', '#b8c2cb', '#c9d1d8', '#d8dee3', '#e5e9ec'),
                         dark=hexes('#060a12', '#0a0f19', '#0e1521', '#131b2a'),
                         scar=hexes('#7f8a98', '#98a2ae', '#b2bac3', '#ccd1d6'),
                         gum=PALETTES['Natural']['gum'])
PALETTES['charcoal'] = lift(dict(hide=hexes('#121314', '#18191b', '#1f2022', '#26282a', '#2e3033', '#37393c', '#404346', '#4a4d50'),
                                 belly=hexes('#8f8f8d', '#a2a2a0', '#b4b4b1', '#c5c5c1', '#d4d4d0', '#e1e1dd'),
                                 dark=hexes('#060606', '#0a0a0b', '#0e0e10', '#131315'),
                                 scar=hexes('#777674', '#908f8c', '#aaa8a4', '#c3c0bb'),
                                 gum=PALETTES['Natural']['gum']), 1.15, 4)
# the backs a shade lighter, so they still read under in-game light (side faces get half of it)
for _pal in {id(p): p for p in PALETTES.values()}.values():
    _pal['hide'] = np.clip(_pal['hide'] * 1.12 + 6, 0, 255)
TOOTH_RAMP = hexes('#8f8a7c', '#b3ad9c', '#d2ccba', '#e6e1d2', '#f3f0e6', '#fbfaf4')
GILLS = [34.6, 37.0, 39.4, 41.8, 44.2]       # z of the five gill slits
# Rake scars, two or three parallel scratches each: (z, y on the side, length, slope, how many).
SCARS = [(24.0, 4.0, 9.0, -0.25, 3), (6.0, -1.0, 8.0, 0.3, 2), (-14.0, 4.0, 7.0, -0.15, 3), (32.0, -6.0, 6.0, 0.1, 2)]


def countershade(p):
    """0 on the belly, 1 on the back; the hard line between them lies at `line`, wavering along the body."""
    z, y = p[..., 2], p[..., 1]
    low = np.interp(z, [b[0] for b in BODY], [b[1] for b in BODY])
    high = np.interp(z, [b[0] for b in BODY], [b[2] for b in BODY])
    return np.clip((y - low) / np.maximum(high - low, 0.5), 0, 1)


def line_at(p):
    z = p[..., 2]
    waver = 0.07 * (fbm(np.stack([z * 0.6, np.sign(p[..., 0]), np.zeros_like(z)], -1), 1.0, seed=211, octaves=2) - 0.5)
    snout = np.clip((z - 52.0) / 10.0, 0, 1)                        # the line dips under the eye toward the mouth
    return 0.44 + waver - 0.14 * snout


def skin(tx, pal):
    """Back and belly: slate denticle grain above the line, a darker band along the spine, white below."""
    p, n = tx.points, tx.normal
    f = countershade(p)
    line = line_at(p)
    grain = cells(p, tx.texel * 1.01, seed=212) - 0.5
    mottle = fbm(p, 6.0, seed=213, octaves=2) - 0.5
    v = 0.42 + 0.35 * (1 - f) * 0.5 + 0.1 * mottle + 0.06 * grain - 0.12 * (f > 0.9)
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    belly = shade(pal['belly'], 0.62 + 0.25 * (line - f) + 0.06 * grain, tx.x, tx.y, dither=0.0)
    under = (f < line - (0.2 if n[1] > 0.5 else 0.0)) | (n[1] < -0.6)     # ledges seen from above stay dark
    colour = np.where(under[..., None], belly, colour)
    edge = (f >= line) & (f < line + 0.035)                       # a dark lip along the line
    colour = np.where(edge[..., None], shade(pal['hide'], 0.12, tx.x, tx.y, dither=0.0), colour)
    return colour, f


def scars(tx, pal, colour):
    p = tx.points
    z, y = p[..., 2], p[..., 1]
    for z0, y0, length, slope, count in SCARS:
        for k in range(count):
            t = (z - z0) / length
            dy = y - (y0 + (z - z0) * slope + k * 1.8)
            hit = (np.abs(t) < 0.5) & (np.abs(dy) < tx.texel * 0.55 * (1.0 - 0.6 * np.abs(t) * 2))
            colour = np.where(hit[..., None], shade(pal['scar'], 0.5 + 0.4 * (k % 2), tx.x, tx.y, dither=0.0),
                              colour)
    return colour


def gills(tx, pal, colour, f):
    """Five slits ahead of the pectorals, curving back at their ends, each with a pale fold behind it."""
    p = tx.points
    z = p[..., 2]
    for k, g in enumerate(GILLS):
        bow = (f - 0.5) ** 2 * (2.2 + 0.2 * k)
        slit = (np.abs(z - g + bow) < tx.texel * 0.5) & (f > 0.3) & (f < 0.66 - 0.02 * k)
        fold = (np.abs(z - g + bow + tx.texel) < tx.texel * 0.5) & (f > 0.33) & (f < 0.63 - 0.02 * k)
        colour = np.where(fold[..., None], shade(pal['hide'], 0.62, tx.x, tx.y, dither=0.0), colour)
        colour = np.where(slit[..., None], shade(pal['hide'], 0.02, tx.x, tx.y, dither=0.0), colour)
    return colour


def hide(tx, pal):
    colour, f = skin(tx, pal)
    if abs(tx.normal[0]) > 0.5 and tx.box.bone in ('c_back1', HEAD):
        colour = gills(tx, pal, colour, f)
    if (abs(tx.normal[0]) > 0.5 or tx.normal[1] > 0.5) and tx.box.bone not in (HEAD, JAW):
        colour = scars(tx, pal, colour)
    return colour


def head(tx, pal):
    """The head as the body, with the mouth's inside dark red, the gums under the teeth, nostril slits at the snout
    and a dark rim round the eye's socket."""
    p, n = tx.points, tx.normal
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    colour = hide(tx, pal)
    part = tx.box.tag.get('part')
    jaw = tx.box.bone == JAW
    mouth_y = np.interp(z, [m[0] for m in MOUTH], [m[2] for m in MOUTH])
    mouth_x = np.interp(z, [m[0] for m in MOUTH], [m[1] for m in MOUTH])
    if (jaw and n[1] > 0.5) or (not jaw and part == 'snout' and n[1] < -0.5):
        inside = np.abs(x) < mouth_x - 0.5
        colour = np.where(inside[..., None], shade(pal['gum'], 0.3 + 0.2 * (np.abs(x) > mouth_x - 1.3), tx.x, tx.y),
                          colour)
    if part == 'lip' and abs(n[1]) < 0.5:                     # dark gums, so the white teeth stand out
        grain = cells(p, tx.texel * 1.01, seed=215) - 0.5
        colour = shade(pal['gum'], 0.12 + 0.15 * grain, tx.x, tx.y, dither=0.0)
    if part == 'snout' and n[1] < -0.5:
        nostril = (np.abs(np.abs(x) - 2.6) < 0.9) & (np.abs(z - 64.6) < tx.texel * 0.6)
        colour = np.where(nostril[..., None], shade(pal['dark'], 0.1, tx.x, tx.y), colour)
    return colour


def out_of_body(points, kind):
    """How far points stand out of the body's oval section (out of the tail stalk's end, for the tail's lobes)."""
    if kind == 'tail':
        return np.linalg.norm(points - np.array([0.0, 1.7, -49.0]), axis=-1)
    z = points[..., 2]
    low = np.interp(z, [b[0] for b in BODY], [b[1] for b in BODY])
    high = np.interp(z, [b[0] for b in BODY], [b[2] for b in BODY])
    half = np.interp(z, [b[0] for b in BODY], [b[3] for b in BODY])
    x, y = points[..., 0], points[..., 1] - (low + high) / 2
    r = np.hypot(x, y)
    edge = 1.0 / np.sqrt((x / np.maximum(r, 1e-6) / half) ** 2 + (y / np.maximum(r, 1e-6) / ((high - low) / 2)) ** 2)
    return np.maximum(r - edge, 0.0)


def fin(tx, pal):
    """A fin card trimmed to the ARK fin's cells of its own bone: the back's colour (white on the pectorals' and the
    pelvics' undersides and the lower tail lobe's lower half), darkening to black tips and a dark trailing rim."""
    tag = tx.box.tag
    mask = tag['sheet']
    p = tx.points
    alpha = mask.at(p) == tag['owner']
    texel = tx.texel
    off = np.zeros(p.shape[:-1], bool)
    for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        off |= mask.at(p + (mask.u * du + mask.v * dv) * texel) < 0
    grain = cells(p, texel * 1.01, seed=214) - 0.5
    kind = tag['fin']
    if not hasattr(mask, 'reach'):                                 # how far the fin's farthest cell stands out
        rows, cols = np.nonzero(mask.grid >= 0)
        centres = mask.origin + np.outer(mask.lo[0] + (cols + 0.5) * mask.res, mask.u) + \
            np.outer(mask.lo[1] + (rows + 0.5) * mask.res, mask.v)
        mask.reach = max(float(out_of_body(centres, kind).max()), 1.0)
    far = np.clip(out_of_body(p, kind) / mask.reach, 0, 1)
    v = 0.45 + 0.08 * grain - 0.3 * far ** 2
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    if kind in ('pectoral', 'pelvic') and tx.normal[1] < 0:
        colour = shade(pal['belly'], 0.6 + 0.08 * grain - 0.25 * far ** 3, tx.x, tx.y, dither=0.0)
    if kind == 'tail':
        f = countershade(p)
        colour = np.where((p[..., 1] < 0.0)[..., None] & (far < 0.4)[..., None],
                          shade(pal['belly'], 0.5 + 0.08 * grain, tx.x, tx.y, dither=0.0), colour)
    tip = far > 0.82
    colour = np.where(tip[..., None], shade(pal['dark'], 0.4 + 0.3 * grain, tx.x, tx.y, dither=0.0), colour)
    colour = np.where((off & alpha)[..., None], shade(pal['dark'], 0.6, tx.x, tx.y, dither=0.0), colour)
    return colour, alpha


def teeth(tx):
    """Serrated triangles every third texel, white at the tips, on a red gum: hanging from the upper jaw, standing
    on the lower."""
    box = tx.box
    p = tx.points
    along = (p[..., 2] if abs(tx.normal[0]) > 0.5 else p[..., 0]) / tx.texel
    rows = box.size[1] / tx.texel
    h = (p[..., 1] - (box.center[1] - box.size[1] / 2)) / tx.texel   # 0 at the bottom of the card
    if box.tag['row'] == 'upper':
        h = rows - h                                                   # measured down from the gum
    width = 3.0 if box.tag['row'] == 'upper' else 2.6
    phase = np.abs(((along + (0.0 if box.tag['row'] == 'upper' else 1.4)) % width) / width - 0.5) * 2
    reach = (rows - 0.5) * (1 - phase) + 0.4
    gum = h < 1.0
    alpha = (h <= reach) | gum
    colour = shade(TOOTH_RAMP, 0.95 - 0.3 * (h < 1.8) - 0.15 * (phase > 0.55), tx.x, tx.y, dither=0.0)
    colour = np.where(gum[..., None], shade(PALETTES['Natural']['gum'], 0.45, tx.x, tx.y, dither=0.0), colour)
    return colour, alpha


def painter(tx, variant):
    pal = PALETTES[variant]
    mat = tx.box.mat
    if mat == 'fin':
        return fin(tx, pal)
    if mat == 'tooth':
        return teeth(tx)
    if mat == 'head':
        return head(tx, pal), None
    return hide(tx, pal), None
