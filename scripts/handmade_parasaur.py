"""The Parasaur, designed box by box on the ARK rig (Para); built by scripts/handmade_models.py.

A crested duck-billed grazer in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a
deep body on thick thighs, long shins and three-toed hoofed feet with a spur at the heel, arms with two spurs and a
mitten of a hand, and a long tail tapering to its tip. The neck runs forward from the chest, then turns up into the
back of the skull; the head is broad over the cheeks and narrower at the eyes, the snout steps down to the duck bill,
and the crest sweeps back from the forehead along the ARK mesh's centre line. A ridge of blades runs along the spine
from the neck to the tail tip, cut out of thin cards (half as many blades as the mesh's spikes, so they stay crisp at
game scale), and spikes stand back from the nape. The bill is designed shut as the idle holds it.
"""
import numpy as np

from handmade_models import cells, fbm, hexes, lift, noise, shade

FOLDER, STEM, HEIGHT = 'Parasaur', 'parasaur', 4.0
DENSITY = 1.0            # texels per runtime unit; the head, crest and ridge are painted at FACE times that
FACE = 2.0
ATLAS_WIDTH = 512
PREVIEW_VARIANT = 'Natural'
ASSET = 'Para'
PREVIEW_CLIPS = ('Para-Idle', 'Para-Move-Fwd', 'Para-Roar-Alert', 'Para-Torpid')
JAW_POSE = ('Para-Idle', 0.0, ['jaw', 'tongue'])     # the frame the bill is designed shut in
HEAD, JAW = 'c_head', 'c_jaw'

# The trunk, segment by segment: (bone, rear z, front z, tiers from the belly up as (bottom, top, half width)),
# measured on the ARK mesh's cross-sections, the legs and the dorsal ridge left out.
TRUNK = [
    ('c_back1', -9.7, -5.4, [(12.75, 14.1, 2.6), (14.0, 16.0, 3.85), (15.9, 22.2, 4.85), (22.1, 24.4, 4.0),
                             (24.3, 25.75, 2.6)]),
    ('c_back1', -5.7, -1.2, [(11.75, 13.1, 2.2), (13.0, 15.0, 3.3), (14.9, 22.6, 4.7), (22.5, 25.0, 4.55),
                             (24.9, 26.55, 3.05), (26.45, 27.2, 1.6)]),
    ('c_back1', -1.52, 2.1, [(10.95, 12.3, 2.15), (12.2, 14.0, 2.8), (13.9, 22.5, 4.6), (22.4, 24.9, 4.25),
                            (24.8, 26.6, 3.1), (26.5, 27.6, 1.75)]),
    ('c_back2', 1.8, 7.3, [(11.6, 12.9, 2.6), (12.8, 14.4, 4.2), (14.3, 21.6, 5.7), (21.5, 24.2, 4.65),
                           (24.1, 26.4, 3.55), (26.3, 27.85, 2.0)]),
    ('c_back3', 6.95, 11.7, [(12.75, 13.7, 2.7), (13.6, 15.0, 4.7), (14.9, 21.2, 5.75), (21.1, 23.8, 4.75),
                            (23.7, 26.1, 3.8), (26.0, 27.75, 2.25)]),
    ('c_back4', 11.4, 14.4, [(13.2, 14.5, 2.5), (14.4, 16.4, 4.0), (16.3, 22.4, 4.65), (22.3, 24.6, 3.95),
                             (24.5, 26.7, 2.9)]),
    ('c_back4', 14.1, 16.6, [(13.6, 14.9, 2.1), (14.8, 16.6, 3.0), (16.5, 22.0, 3.35), (21.9, 24.2, 3.0),
                             (24.1, 25.6, 2.2)]),
]
# The tail, two segments per bone: (bone, rear z, front z, (keel, core, top) tiers).
TAIL = [
    ('c_tail5', -42.7, -38.85, [(19.75, 20.05, 0.38), (20.0, 20.6, 0.58), (20.55, 20.8, 0.36)]),
    ('c_tail4', -39.1, -35.6, [(18.95, 19.35, 0.6), (19.3, 20.35, 0.92), (20.3, 20.68, 0.56)]),
    ('c_tail4', -35.9, -32.4, [(18.25, 18.75, 0.82), (18.7, 20.05, 1.18), (20.0, 20.5, 0.76)]),
    ('c_tail3', -32.65, -28.7, [(17.35, 17.95, 1.02), (17.9, 19.85, 1.52), (19.8, 20.42, 0.94)]),
    ('c_tail3', -28.95, -24.8, [(16.5, 17.25, 1.22), (17.2, 19.75, 1.92), (19.7, 20.55, 1.12)]),
    ('c_tail2', -25.05, -20.75, [(15.75, 16.6, 1.5), (16.55, 19.95, 2.38), (19.9, 20.95, 1.42)]),
    ('c_tail2', -21.0, -16.9, [(15.0, 16.0, 1.92), (15.95, 20.45, 2.88), (20.4, 21.65, 1.82)]),
    ('c_tail1', -17.15, -12.6, [(14.35, 15.4, 2.25), (15.35, 21.3, 3.5), (21.25, 22.9, 2.35)]),
    ('c_tail1', -12.85, -9.4, [(13.8, 15.0, 2.7), (14.95, 22.5, 4.15), (22.45, 24.4, 2.75)]),
]
# The crest's centre line from the forehead back to its tip, with its width and depth there (after the ARK mesh).
CREST = [((0.0, 34.2, 29.6), 2.4, 1.1), ((0.0, 34.75, 27.6), 2.75, 1.5), ((0.0, 35.25, 25.4), 2.7, 2.0),
         ((0.0, 35.65, 23.2), 2.55, 2.5), ((0.0, 35.85, 21.0), 2.3, 2.6), ((0.0, 35.75, 19.0), 1.7, 2.0),
         ((0.0, 35.35, 17.4), 1.3, 1.5), ((0.0, 34.6, 16.0), 0.85, 1.0), ((0.0, 33.75, 15.1), 0.4, 0.5)]
# The dorsal ridge along the spine: (z, tip height) of the ARK mesh's spikes, from the tail tip to the head.
SPIKES = [(-42.56, 21.43), (-41.65, 21.31), (-40.96, 21.19), (-39.54, 21.24), (-38.93, 21.23), (-38.28, 21.37),
          (-37.28, 21.23), (-36.35, 21.15), (-35.44, 21.15), (-34.33, 21.27), (-33.21, 21.2), (-31.71, 21.22),
          (-30.48, 21.3), (-29.08, 21.39), (-27.73, 21.52), (-26.4, 21.6), (-25.31, 21.76), (-24.03, 21.84),
          (-22.69, 22.22), (-21.29, 22.55), (-19.89, 22.9), (-18.36, 23.42), (-17.04, 23.78), (-15.9, 24.11),
          (-14.63, 24.86), (-13.76, 24.68), (-12.99, 25.46), (-11.69, 26.0), (-10.52, 26.66), (-9.88, 26.47),
          (-9.0, 27.59), (-8.3, 27.3), (-7.33, 28.07), (-6.79, 27.79), (-5.71, 28.55), (-4.68, 28.36), (-4.07, 29.01),
          (-2.63, 29.42), (-2.07, 29.12), (-1.53, 29.17), (-0.86, 29.93), (0.6, 30.36), (1.13, 29.89), (1.63, 29.95),
          (2.27, 30.59), (3.55, 30.24), (4.39, 30.98), (5.69, 31.17), (7.27, 31.13), (8.91, 30.88), (9.85, 30.07),
          (10.57, 30.41), (11.14, 29.63), (12.19, 29.75), (13.83, 28.81), (15.25, 27.59), (16.11, 26.86),
          (17.19, 26.06), (17.78, 26.34), (18.57, 26.61), (19.33, 26.97), (20.59, 30.47), (22.42, 32.25)]
# The ridge is drawn with half as many, broader blades (the ARK mesh's 63 would blur at game scale): each pair of
# its spikes becomes one blade at their middle, as tall as the taller.
BLADES = [((SPIKES[k][0] + SPIKES[k + 1][0]) / 2, max(SPIKES[k][1], SPIKES[k + 1][1]))
          for k in range(0, len(SPIKES) - 1, 2)]
# The top of the body under the ridge along z (where the ridge's base sits).
BODY_TOP = ([-42.7, -39.0, -36.0, -32.0, -28.0, -24.0, -20.0, -16.0, -12.0, -8.0, -4.0, 0.0, 4.0, 8.0, 10.0, 12.0,
             14.0, 16.0, 18.0, 19.0, 20.0, 21.0, 22.0],
            [20.6, 20.5, 20.4, 20.2, 20.15, 20.4, 21.0, 22.3, 23.9, 25.45, 26.7, 27.4, 27.83, 27.83, 27.5, 26.86,
             26.05, 25.0, 24.4, 24.8, 25.4, 26.5, 27.9])


def tiers(d, bone, z0, z1, rows, mat='hide', part='trunk', inset=0.04, **tag):
    """Stacked boxes across a segment, each overlapping the next a little; the tiers off the core are inset at both
    ends, so no two neighbours share an end plane."""
    core = max(range(len(rows)), key=lambda k: rows[k][2])
    for k, (y0, y1, half) in enumerate(rows):
        e = 0.0 if k == core else inset * (1 + abs(k - core))
        d.span(bone, (-half, y0, z0 + e), (half, y1, z1 - e), mat, part=part, tier=k - core, **tag)


def spans(line, mat, d, bone, mirror=False, part='horn', ext=0.12, **tag):
    """A tapering horn along its centre line: one box per span, each as wide and deep as the line there."""
    total = sum(float(np.linalg.norm(np.subtract(line[k + 1][0], line[k][0]))) for k in range(len(line) - 1))
    run = 0.0
    for k in range(len(line) - 1):
        (a, wa, da), (b, wb, db) = line[k], line[k + 1]
        length = float(np.linalg.norm(np.subtract(b, a)))
        d.seg(bone, a, b, (wa + wb) / 2, (da + db) / 2, mat, ext=(ext if k else 0.0, ext if k < len(line) - 2 else 0.0),
              side=(1, 0, 0), mirror=mirror, part=part, along=(run / total, (run + length) / total), **tag)
        run += length


def ridge(d, bone, z0, z1, half=0.16, density=FACE):
    """The dorsal ridge over one bone: a thin card standing on the spine, its spikes cut out of its sides."""
    base = float(np.interp((z0 + z1) / 2, *BODY_TOP))
    tips = [y for z, y in BLADES if z0 - 1.2 <= z <= z1 + 1.2]
    top = max(tips) + 0.05 if tips else base + 1.0
    lo = min(float(np.interp(z0, *BODY_TOP)), float(np.interp(z1, *BODY_TOP))) - 0.35
    d.span(bone, (-half, lo, z0), (half, top, z1), 'ridge', part='ridge', open=('up', 'down', 'north', 'south'),
           cutout=True, keep=True, density=density)


def design(d):
    rig = d.rig
    P = rig.pivot
    for bone in (JAW, 'c_tongue1', 'c_tongue2', 'c_tongue3', 'c_tongue4'):
        rig.design_as_in(bone, 'Para-Idle', 0.0, HEAD)
    f = dict(density=FACE)

    # ---------------------------------------------------------------- trunk, neck and tail
    for k, (bone, z0, z1, rows) in enumerate(TRUNK):
        tiers(d, bone, z0, z1, rows, inset=0.035 + 0.011 * k)
    for k, (bone, z0, z1, rows) in enumerate(TAIL):
        tiers(d, bone, z0, z1, rows, part='tail', inset=0.05 + 0.009 * k)
    # the lower neck runs forward from the chest, its back dipping under the ridge, its throat rising
    d.span('c_neck1', (-2.75, 17.6, 14.7), (2.75, 25.1, 18.3), 'hide', part='neck')
    d.span('c_neck1', (-2.4, 18.6, 17.9), (2.4, 24.9, 20.7), 'hide', part='neck')
    d.slab('c_neck1', (14.05, 14.9), (19.0, 20.6), 2.45, 3.4, 'hide', top=False, part='neck', tier=-1)
    # then it turns up into the back of the skull
    d.seg('c_neck2', (0.0, 22.0, 20.5), (0.0, 27.0, 22.85), 4.3, 4.9, 'hide', ext=(0.6, 0.3), side=(1, 0, 0),
          part='neck')
    d.slab('c_neck2', (19.6, 21.4), (25.0, 25.75), 1.95, 1.8, 'hide', top=False, part='neck', tier=-1)
    d.seg('c_neck3', (0.0, 26.3, 23.15), (0.0, 30.4, 24.5), 4.1, 5.6, 'hide', ext=(0.0, 0.5), side=(1, 0, 0),
          part='neck')
    d.slab('c_neck3', (24.8, 25.3), (28.6, 27.35), 1.85, 1.6, 'hide', top=False, part='neck', tier=-1)

    # ---------------------------------------------------------------- head (painted at FACE density)
    # the skull: broad over the cheeks, narrower at the eyes, the crest's root over the brow and the occiput behind
    d.span(HEAD, (-2.45, 29.3, 24.6), (2.45, 31.8, 28.6), 'head', part='skull', **f)
    d.span(HEAD, (-2.6, 29.6, 25.2), (2.6, 31.45, 28.1), 'head', part='cheek', **f)
    d.span(HEAD, (-1.95, 31.7, 24.2), (1.95, 33.5, 29.5), 'head', part='temple', **f)
    d.span(HEAD, (-1.72, 33.4, 25.4), (1.72, 34.3, 29.65), 'head', part='brow', **f)
    d.span(HEAD, (-1.9, 29.8, 22.6), (1.9, 33.3, 24.7), 'head', part='occiput', **f)
    # the snout, stepping down to the broad duck bill
    d.span(HEAD, (-1.62, 29.3, 28.5), (1.62, 32.2, 31.3), 'head', part='snout', **f)
    d.span(HEAD, (-1.3, 32.1, 29.4), (1.3, 32.85, 30.7), 'head', part='snout', **f)
    d.span(HEAD, (-1.32, 29.2, 31.2), (1.32, 31.35, 32.6), 'head', part='snout', **f)
    d.span(HEAD, (-1.5, 29.1, 32.4), (1.5, 30.6, 33.45), 'beak', part='bill', **f)
    # the lower bill, shut under the upper, its keratin edge ending in two points
    d.span(JAW, (-1.95, 28.5, 25.6), (1.95, 29.42, 28.5), 'head', part='jaw', **f)
    d.span(JAW, (-1.4, 28.65, 28.4), (1.4, 29.38, 32.5), 'head', part='jaw', **f)
    d.span(JAW, (-1.28, 28.75, 32.3), (1.28, 29.25, 33.2), 'beak', part='lowbill', **f)
    d.span(JAW, (0.8, 27.75, 31.65), (1.22, 28.85, 32.25), 'beak', part='point', mirror=True, **f)
    # the crest sweeps back from the forehead
    spans(CREST, 'crest', d, HEAD, part='crest', **f)

    # ---------------------------------------------------------------- dorsal ridge, bone by bone
    cards = (('c_tail5', -42.7, -38.7), ('c_tail4', -39.0, -32.4), ('c_tail3', -32.7, -24.8), ('c_tail2', -25.1, -16.9),
             ('c_tail1', -17.2, -9.2), ('c_back1', -9.5, 1.9), ('c_back2', 1.6, 7.2), ('c_back3', 6.9, 11.6),
             ('c_back4', 11.3, 16.4), ('c_neck1', 16.1, 18.9), ('c_neck2', 18.6, 20.2))
    for k, (bone, z0, z1) in enumerate(cards):            # neighbours alternate in width, so no two share a plane
        ridge(d, bone, z0, z1, half=0.07 + 0.025 * (k % 2))
    # up the back of the upright neck the spikes point backwards
    d.span('c_neck3', (-0.11, 25.6, 18.2), (0.11, 32.6, 22.1), 'ridge', part='nape',
           open=('up', 'down', 'north', 'south'), cutout=True, keep=True, density=FACE)

    # ---------------------------------------------------------------- arms
    shoulder, elbow, wrist = P('l_shoulder'), P('l_elbow'), P('l_wrist')
    d.seg('l_shoulder', shoulder + (0.0, 0.8, 0.3), elbow, 2.2, 2.5, 'hide', ext=(0.0, 0.4), mirror=True, part='arm')
    d.seg('l_elbow', elbow, wrist, 1.95, 2.15, 'hide', ext=(0.35, 0.25), mirror=True, part='forearm')
    d.seg('l_wrist', wrist, P('l_fingerB1'), 1.75, 1.55, 'hide', ext=(0.2, 0.25), mirror=True, part='hand')
    for finger in 'ABCD':
        a, b, c = (P(f'l_finger{finger}{k}') for k in (1, 2, 3))
        tip = c + (c - b) * 0.8
        w = 0.55 if finger != 'D' else 0.45
        d.seg(f'l_finger{finger}1', a, tip, w, 0.62, 'hide', ext=(0.15, 0.0), mirror=True, part='finger')
    # the spurs on the forearm
    d.seg('l_armTiwst', (3.5, 17.7, 17.85), (3.55, 16.85, 18.7), 0.42, 0.42, 'horn', ext=(0.35, 0.0), mirror=True,
          part='spur', along=(0.0, 1.0))
    d.seg('l_armTiwst', (3.45, 16.6, 17.15), (3.55, 15.15, 18.15), 0.4, 0.4, 'horn', ext=(0.35, 0.0), mirror=True,
          part='spur', along=(0.0, 1.0))

    # ---------------------------------------------------------------- legs
    thigh = 'l_leg'
    d.span(thigh, (3.0, 14.5, -4.8), (7.0, 23.0, 3.8), 'hide', mirror=True, part='thigh')
    d.span(thigh, (3.2, 22.9, -4.0), (6.4, 24.6, 2.6), 'hide', mirror=True, part='thigh')
    d.span(thigh, (3.6, 12.2, -1.8), (6.8, 14.6, 4.2), 'hide', mirror=True, part='thigh')
    d.span(thigh, (3.3, 15.5, 3.7), (6.7, 21.5, 4.85), 'hide', mirror=True, part='thigh')
    d.span(thigh, (3.3, 16.5, -5.65), (6.6, 22.5, -4.7), 'hide', mirror=True, part='thigh')
    knee, ankle = P('l_knee'), P('l_ankle')
    mid = knee + (ankle - knee) * 0.5
    d.seg('l_knee', knee, mid, 3.2, 3.7, 'hide', ext=(0.6, 0.1), mirror=True, part='shin')
    d.seg('l_knee', mid, ankle, 2.6, 2.9, 'hide', ext=(0.1, 0.3), mirror=True, part='shin')
    d.seg('l_ankle', ankle, P('l_toeB1') + (0.0, -0.6, 0.0), 2.05, 2.5, 'hide', ext=(0.2, 0.0), mirror=True,
          part='foot')
    d.seg('l_ankle', ankle + (0.0, 0.2, -0.9), ankle + (0.0, 2.5, -2.1), 0.5, 0.5, 'horn', ext=(0.3, 0.0), mirror=True,
          part='spur', along=(0.0, 1.0))
    for toe, w in (('A', 1.05), ('B', 1.25), ('C', 1.05)):
        a, b, c = (P(f'l_toe{toe}{k}') for k in (1, 2, 3))
        tip = c + (c - b) * 0.7
        d.seg(f'l_toe{toe}1', a, b, w, 1.15, 'hide', ext=(0.3, 0.1), mirror=True, part='toe')
        d.seg(f'l_toe{toe}2', b, c, w * 0.92, 1.0, 'hide', ext=(0.1, 0.05), mirror=True, part='toe')
        d.seg(f'l_toe{toe}2', c, tip, w * 0.85, 0.8, 'hoof', ext=(0.05, 0.0), mirror=True, part='hoof')


# The back of the upright neck (y, z of its surface) and the spikes standing back from it (y, z of the tips).
NAPE = ([25.6, 26.5, 27.5, 28.5, 29.5, 30.5, 31.5, 32.6], [19.4, 19.9, 20.5, 21.0, 21.3, 21.55, 21.7, 21.8])
NAPE_SPIKES = [(26.0, 18.4), (26.9, 19.0), (27.9, 19.5), (28.9, 19.3), (29.7, 19.45), (30.4, 19.95), (31.2, 20.6),
               (32.1, 21.2)]


def nape_alpha(tx):
    """The spikes up the back of the neck: blades pointing back, sawtoothed along the neck's height."""
    p = tx.points
    y, z = p[..., 1], p[..., 2]
    base = np.interp(y, *NAPE) + 0.3
    ys = np.array([s[0] for s in NAPE_SPIKES])
    zs = np.array([s[1] for s in NAPE_SPIKES])
    k = np.clip(np.searchsorted(ys, y), 1, len(ys) - 1)
    y0, y1 = ys[k - 1], ys[k]
    t = np.clip((y - y0) / np.maximum(y1 - y0, 1e-3), 0, 1)
    reach = np.where(t > 0.75, (t - 0.75) / 0.25, 1 - t / 0.75)
    tip = np.where(t > 0.75, zs[k], zs[k - 1])
    back = np.interp(y, *NAPE)
    limit = back + (tip - back) * reach
    return (z >= limit) & (z <= base)


def ridge_alpha(tx):
    """The ridge's spikes: each a pointed blade leaning back, its tip at the ARK spike's height."""
    p = tx.points
    z, y = p[..., 2], p[..., 1]
    base = np.interp(z, *BODY_TOP)
    zs = np.array([s[0] for s in BLADES])
    ys = np.array([s[1] for s in BLADES])
    # the nearest blade tip in front of and behind each texel
    k = np.clip(np.searchsorted(zs, z), 1, len(zs) - 1)
    z0, z1 = zs[k - 1], zs[k]
    y0, y1 = ys[k - 1], ys[k]
    t = np.clip((z - z0) / np.maximum(z1 - z0, 1e-3), 0, 1)
    # a sawtooth of blades leaning back: from the tip behind, the front edge falls to the base, then the next blade's
    # back edge rises steeply to its tip
    rise = np.where(t > 0.7, (t - 0.7) / 0.3, 1 - t / 0.7)
    tip = np.where(t > 0.7, y1, y0)
    height = base + (tip - base) * rise
    return y <= height


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(hide=hexes('#2e3120', '#3d4129', '#4d5233', '#5e633e', '#70754a', '#838757', '#969965', '#a9ab74'),
                    belly=hexes('#8e8a62', '#a19d74', '#b3af86', '#c4c098', '#d3cfab', '#e0dcbe'),
                    band=hexes('#1c1e12', '#252818', '#2f321f', '#3a3d27'),
                    crest=hexes('#6a2414', '#86301a', '#a23e20', '#bb5028', '#d16634', '#e27f44'),
                    rim=hexes('#2a120c', '#3a1a10', '#4b2215', '#5c2c1b'),
                    ridge=hexes('#3a1a10', '#4f2415', '#66301b', '#7d3d22', '#944b2a')),
    'olive': dict(hide=hexes('#2e3120', '#3d4129', '#4d5233', '#5e633e', '#70754a', '#838757', '#969965', '#a9ab74'),
                  belly=hexes('#8e8a62', '#a19d74', '#b3af86', '#c4c098', '#d3cfab', '#e0dcbe'),
                  band=hexes('#1c1e12', '#252818', '#2f321f', '#3a3d27'),
                  crest=hexes('#6a2414', '#86301a', '#a23e20', '#bb5028', '#d16634', '#e27f44'),
                  rim=hexes('#2a120c', '#3a1a10', '#4b2215', '#5c2c1b'),
                  ridge=hexes('#3a1a10', '#4f2415', '#66301b', '#7d3d22', '#944b2a')),
    'sand': dict(hide=hexes('#5f5541', '#706651', '#827861', '#958a71', '#a79c82', '#bbaf93', '#cec2a5', '#e2d6b7'),
                 belly=hexes('#cabfa3', '#d3c9b0', '#dcd3be', '#e5ddcb', '#eee8d9', '#f7f2e7'),
                 band=hexes('#4c4132', '#5b4f3d', '#6b5d48', '#7b6b53'),
                 crest=hexes('#7b3521', '#8f452c', '#a45638', '#b96744', '#cf7950', '#e58b5d'),
                 rim=hexes('#3b1d13', '#4c271a', '#5d3121', '#6f3b29'),
                 ridge=hexes('#502b1b', '#633623', '#77412b', '#8c4d33', '#a1593b')),
    'umber': dict(hide=hexes('#2c2218', '#3b2f22', '#4b3c2d', '#5b4a38', '#6c5943', '#7d674f', '#8f765b', '#a18667'),
                  belly=hexes('#8b7b63', '#9c8d75', '#ad9f88', '#beb19b', '#cfc4af', '#e1d7c3'),
                  band=hexes('#15100c', '#1e1711', '#271e17', '#31251c'),
                  crest=hexes('#6e5510', '#856b1c', '#9d8127', '#b69933', '#cfb03f', '#e9c94b'),
                  rim=hexes('#2a2006', '#3a2c09', '#4a390c', '#5b4610'),
                  ridge=hexes('#4a390c', '#604a11', '#765c17', '#8e6f1c', '#a68222')),
    'green': dict(hide=hexes('#0e1c10', '#142616', '#1b311d', '#223d25', '#2a4a2d', '#335836', '#3d6740', '#48764b'),
                  belly=hexes('#56664a', '#66775a', '#77896a', '#899b7b', '#9bad8d', '#adbf9f'),
                  band=hexes('#050b06', '#08110a', '#0c180e', '#101f12'),
                  crest=hexes('#6e5510', '#8a6c15', '#a7841b', '#c39d24', '#d9b434', '#e9c94b'),
                  rim=hexes('#2a2006', '#3a2c09', '#4a390c', '#5b4610'),
                  ridge=hexes('#4a390c', '#604a10', '#775c15', '#8e6f1b', '#a68222')),
}
# the darkest variants a shade lighter, so they still read under in-game light
PALETTES['green'] = lift(PALETTES['green'], 1.08, 3)
BEAK_RAMP = hexes('#141210', '#1e1b18', '#292521', '#35302b', '#423c35', '#514940')
HOOF_RAMP = hexes('#171411', '#221e1a', '#2e2823', '#3b342d', '#4a4138')
HORN_RAMP = hexes('#4f4434', '#6b5d47', '#88795e', '#a69778', '#c3b593', '#ddd1b0', '#efe6cd')
MOUTH_RAMP = hexes('#3a1414', '#55201f', '#71302c', '#8c423b', '#a5574c')

# The body's underside along its length (z): where the pale belly ends at the bottom of the body.
UNDER = ([-42.7, -39.0, -36.0, -32.0, -28.0, -24.0, -20.0, -16.0, -12.0, -8.0, -4.0, 0.0, 4.0, 8.0, 12.0, 14.0, 16.0,
          18.0, 20.0, 22.0, 24.0, 26.0],
         [19.8, 19.2, 18.5, 17.4, 16.45, 15.7, 15.0, 14.35, 13.87, 12.96, 11.78, 11.0, 12.03, 12.73, 13.16, 13.36, 14.2,
          16.4, 18.7, 20.0, 22.0, 25.5])
# Dark bands across the back and down the flanks, as pixel runs: (z where a band crosses the spine, its width in
# texels there, how far down the side it reaches in units). Broad saddles over the body, rings round the tail.
BANDS = [(17.6, 3, 2.6), (14.2, 4, 4.0), (10.6, 4, 5.4), (6.9, 5, 6.2), (3.1, 5, 6.6), (-0.8, 5, 6.6), (-4.7, 5, 6.2),
         (-8.6, 4, 5.4), (-12.4, 4, 4.6), (-16.2, 4, 3.8), (-20.0, 3, 3.2), (-23.7, 3, 2.7), (-27.3, 3, 2.2),
         (-30.8, 3, 1.8), (-34.2, 2, 1.4), (-37.5, 2, 1.1), (-40.6, 2, 0.8)]


def back_y(z):
    return np.interp(z, *BODY_TOP)


def under_y(z):
    return np.interp(z, *UNDER)


def face_grid(tx):
    """Per texel: its column and row inside its own face's rectangle (rows run down a side face)."""
    u0, v0, _, _ = tx.face.rect
    return tx.x - u0, tx.y - v0


def local_grid(tx, axis):
    """Per texel: its index along one of its box's own axes (0 at the low end), and the count across."""
    box = tx.box
    local = (tx.points - box.center) @ box.axes[:, axis]
    count = max(1, int(round(box.size[axis] / tx.texel)))
    return np.clip(np.floor((local / box.size[axis] + 0.5) * count).astype(int), 0, count - 1), count


def scales(tx, size=3, seed=0, seam=0.1):
    """Pebbled scales as pixel art on the face's own texel grid: every texel belongs to the nearest of a jittered
    lattice of scale centres `size` texels apart; each scale has its own tone, a lit texel or two toward its top
    and a dark seam where it meets its neighbours. Returns a value offset per texel."""
    col, row = face_grid(tx)
    gi, gj = col // size, row // size
    best = np.full(col.shape, np.inf)
    second = np.full(col.shape, np.inf)
    owner_i, owner_j = np.zeros_like(gi), np.zeros_like(gj)
    salt = float(tx.face.index % 997)
    for di in (-1, 0, 1):
        for dj in (-1, 0, 1):
            ci, cj = gi + di, gj + dj
            key = np.stack([ci.astype(float), cj.astype(float), np.full(ci.shape, salt)], -1)
            sx = ci * size + cells(key, 1.0, seed=seed) * (size - 1)
            sy = cj * size + cells(key, 1.0, seed=seed + 1) * (size - 1)
            dist = (col - sx) ** 2 + (row - sy) ** 2 * 1.3
            closer = dist < best
            second = np.where(closer, best, np.minimum(second, dist))
            best = np.where(closer, dist, best)
            owner_i, owner_j = np.where(closer, ci, owner_i), np.where(closer, cj, owner_j)
    tone = cells(np.stack([owner_i.astype(float), owner_j.astype(float), np.full(col.shape, salt)], -1), 1.0,
                 seed=seed + 2)
    v = 0.09 * (tone - 0.5)
    v = v - seam * (np.sqrt(second) - np.sqrt(best) < 0.9)
    lit = cells(np.stack([owner_i.astype(float), owner_j.astype(float), np.full(col.shape, salt + 1)], -1), 1.0,
                seed=seed + 3)
    return v + 0.045 * ((best < 0.6) & (lit > 0.45))


def band_runs(seed, width, rows):
    """A band as pixel runs down the flank: runs of 2-4 rows, wobbling a texel back and forth, narrowing over its
    lower half to a ragged end. Returns (first row, last row, shift, width) in texels."""
    rng = np.random.default_rng(seed)
    runs, row, shift, w = [], 0, 0, width
    while row < rows and w > 0:
        n = int(rng.choice([2, 3, 3, 4]))
        runs.append((row, min(row + n, rows) - 1, shift, w))
        row += n
        shift += int(rng.choice([-1, 0, 0, 1]))
        if row > rows * 0.45:
            w -= int(rng.choice([0, 1, 1]))
    return runs


RUNS = [band_runs(71 + k, w, 40) for k, (_, w, _) in enumerate(BANDS)]


def bands(tx):
    """The dark bands of BANDS across the back and down the sides, on the face's own texels along the body."""
    p, n, box = tx.points, tx.normal, tx.box
    mask = np.zeros(p.shape[:2], bool)
    axis = box.axes[:, 2]
    if abs(n @ axis) > 0.7 or n[1] < -0.5:
        return mask
    count = max(1, int(round(box.size[2] / tx.texel)))
    local = (p - box.center) @ axis
    iz = np.clip(np.floor((local / box.size[2] + 0.5) * count).astype(int), 0, count - 1)
    rows = np.floor((back_y(p[..., 2]) - p[..., 1] + np.abs(p[..., 0]) * 0.35) / tx.texel).astype(int)
    for (z0, width, reach), runs in zip(BANDS, RUNS):
        along = (np.array([0.0, back_y(z0), z0]) - box.center) @ axis
        col = int(np.floor((along / box.size[2] + 0.5) * count))
        if col < -20 or col > count + 20:
            continue
        limit = int(reach / tx.texel)
        for first, last, shift, w in runs:
            if first > limit:
                break
            c0 = col + shift - w // 2
            mask |= (rows >= first) & (rows <= min(last, limit)) & (iz >= c0) & (iz < c0 + w)
    return mask


def countershade(tx):
    """0 at the belly line to 1 along the spine, from the body's outline at the texel's z."""
    p = tx.points
    top, bottom = back_y(p[..., 2]), under_y(p[..., 2])
    return np.clip((p[..., 1] - bottom) / np.maximum(top - bottom, 0.5), 0, 1)


def hide(tx, pal):
    """The body: small pebbled scales over a dark back lightening down the flanks, the dark bands, a row of pale spots
    along the flank and the pale underside with its transverse belly scales."""
    p, n = tx.points, tx.normal
    f = countershade(tx)
    mottle = fbm(p, 5.0, seed=3, octaves=2) - 0.5
    # a ledge where a tier steps in lies in the shade of the hide above it
    ledge = -0.06 if n[1] > 0.5 and float(p[..., 1].mean()) < float(back_y(p[..., 2]).mean()) - 0.4 else 0.0
    v = 0.8 - 0.44 * f ** 1.15 + 0.12 * mottle + scales(tx, 3, seed=5) + 0.04 * max(n[1], 0) + ledge
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    band = bands(tx)
    if np.any(band):
        colour = np.where(band[..., None], shade(pal['band'], 0.55 + 0.6 * mottle + 0.6 * scales(tx, 3, seed=5),
                                                 tx.x, tx.y, dither=0.0), colour)
    # pale spots strung along the flank between the bands
    if abs(n[0]) > 0.5:
        spot = (cells(p, tx.texel * 2.02, seed=11) > 0.78) & (np.abs(f - 0.5) < 0.09) & ~band
        colour = np.where(spot[..., None], shade(pal['belly'], 0.45 + 0.2 * mottle, tx.x, tx.y, dither=0.0), colour)
    edge = 0.3 + 0.06 * (noise(p, 1.2, seed=8) - 0.5) + 0.9 * scales(tx, 3, seed=7)
    under = (f < edge) | (n[1] < -0.5)
    if np.any(under):
        belly = 0.55 + 0.1 * mottle + 0.8 * scales(tx, 4, seed=6, seam=0.08)
        if n[1] < -0.5:
            col, row = face_grid(tx)
            k, _ = local_grid(tx, 2)
            belly = 0.6 + 0.06 * mottle - 0.15 * (k % 4 == 0) + 0.04 * (k % 4 == 1)
        colour = np.where(under[..., None], shade(pal['belly'], belly, tx.x, tx.y, dither=0.0), colour)
    return colour


def limb(tx, pal):
    """Legs and arms: rings of scales round the limb, darker outside and toward the feet, paler inside and under the
    thighs; the bands run on over the thighs."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    mottle = fbm(p, 4.0, seed=13, octaves=2) - 0.5
    col, row = face_grid(tx)
    wave = np.floor(1.4 * np.sin(col * 0.4 + tx.face.index * 1.7)).astype(int)
    ring = ((row + wave) % 3 == 0) & (abs(n[1]) < 0.5)
    v = 0.56 + 0.12 * mottle + scales(tx, 2 if part in ('toe', 'finger', 'hand') else 3, seed=14) - 0.08 * ring
    v -= 0.12 * np.clip((8.0 - p[..., 1]) / 8.0, 0, 1) if part in ('shin', 'foot', 'toe') else 0.0
    if part == 'thigh':
        v = 0.5 + 0.12 * mottle + scales(tx, 3, seed=14) - 0.06 * np.clip((p[..., 1] - 18) / 6, 0, 1)
    if abs(n[0]) > 0.5 and n[0] * np.sign(tx.box.center[0]) < 0:
        v += 0.1
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    if part == 'thigh':
        band = bands(tx)
        if np.any(band):
            colour = np.where(band[..., None], shade(pal['band'], 0.6 + 0.6 * mottle, tx.x, tx.y, dither=0.0), colour)
    if part in ('hand', 'finger') or (n[1] < -0.5):
        colour = shade(pal['belly'], 0.42 + 0.1 * mottle + scales(tx, 2, seed=15), tx.x, tx.y, dither=0.0)
    if part in ('toe', 'foot') and n[1] > 0.5:
        k, _ = local_grid(tx, 1)
        colour = np.where((k % 3 == 0)[..., None], shade(pal['hide'], v - 0.14, tx.x, tx.y, dither=0.0), colour)
    return colour


EYE = (28.4, 32.35)


def head(tx, pal):
    """The head: scales darkening over the crown, a dark stripe back through the eye, pale cheeks, throat and lips,
    the nostrils near the front of the snout and the inside of the mouth."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    mottle = fbm(p, 2.5, seed=41, octaves=2) - 0.5
    f = np.clip((y - 29.0) / 5.0, 0, 1)
    v = 0.78 - 0.4 * f + 0.1 * mottle + scales(tx, 3, seed=42) + 0.04 * max(n[1], 0)
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    side = abs(n[0]) > 0.5
    jaw = tx.box.bone == JAW
    if side and not jaw:
        # a dark stripe from the snout back through the eye to the back of the head
        dz, dy = (z - EYE[0]) / tx.texel, (y - EYE[1]) / tx.texel
        stripe = (np.abs(dy - 0.12 * dz) < 1.6 + 0.04 * np.abs(dz)) & (dz > -26) & (dz < 12)
        colour = np.where(stripe[..., None], shade(pal['band'], 0.5 + 0.5 * mottle, tx.x, tx.y, dither=0.0), colour)
        # pale cheeks and lips below it
        pale = y < 30.2 + 0.25 * (noise(p, 0.6, seed=44) - 0.5)
        colour = np.where(pale[..., None], shade(pal['belly'], 0.55 + 0.08 * mottle + 0.5 * scales(tx, 3, seed=43),
                                                 tx.x, tx.y, dither=0.0), colour)
        if part == 'snout':
            nostril = (np.abs(z - 32.1) < 0.32) & (np.abs(y - 30.95) < 0.17)
            colour = np.where(nostril[..., None], shade(pal['band'], 0.0, tx.x, tx.y), colour)
    if jaw:
        colour = shade(pal['belly'], 0.52 + 0.08 * mottle + 0.5 * scales(tx, 3, seed=45), tx.x, tx.y, dither=0.0)
        if n[1] > 0.5:
            colour = shade(MOUTH_RAMP, 0.4 + 0.2 * mottle, tx.x, tx.y)
    elif n[1] < -0.5:
        colour = shade(MOUTH_RAMP, 0.3 + 0.2 * mottle, tx.x, tx.y) if part == 'snout' else \
            shade(pal['belly'], 0.45 + 0.08 * mottle, tx.x, tx.y)
    return colour


def beak(tx, pal):
    """The bill's keratin: dark, its worn edge a shade lighter, the inside of the mouth on the lower bill."""
    p, n, part = tx.points, tx.normal, tx.box.tag.get('part')
    v = 0.42 + 0.14 * (cells(p, tx.texel * 1.01, seed=52) - 0.5) + 0.12 * max(n[1], 0)
    colour = shade(BEAK_RAMP, v, tx.x, tx.y, dither=0.0)
    if part == 'bill' and abs(n[0]) > 0.5:
        _, row = face_grid(tx)
        colour = np.where((row >= tx.face.rect[3] - 1)[..., None], shade(BEAK_RAMP, 0.85, tx.x, tx.y), colour)
    if part == 'lowbill' and n[1] > 0.5:
        colour = shade(MOUTH_RAMP, 0.4, tx.x, tx.y)
    return colour


def crest(tx, pal):
    """The crest: its colour deepening toward the root, a pale line along the groove on top, dark edges where it
    rounds over, and the skull's hide at its root on the forehead."""
    p, n, box = tx.points, tx.normal, tx.box
    a, b = box.tag.get('along', (0.0, 1.0))
    s = ((p - box.center) @ box.axes[:, 1]) / box.size[1] + 0.5
    t = a + (b - a) * np.clip(s, 0, 1)                       # 0 at the forehead, 1 at the tip
    mottle = fbm(p, 2.0, seed=61, octaves=2) - 0.5
    v = 0.42 + 0.42 * t + 0.1 * mottle + 0.6 * scales(tx, 3, seed=62, seam=0.07)
    facing = box.axes.T @ n
    across, count = local_grid(tx, 0)
    colour = shade(pal['crest'], v, tx.x, tx.y, dither=0.0)
    if abs(facing[2]) > 0.5:                                  # the top and the underside of the crest
        mid = np.abs(across - (count - 1) / 2)
        if facing[2] > 0:
            colour = np.where((mid < 0.8)[..., None], shade(pal['crest'], 0.95, tx.x, tx.y, dither=0.0), colour)
        colour = np.where((mid >= (count - 1) / 2 - 0.5)[..., None], shade(pal['rim'], 0.6 + mottle, tx.x, tx.y,
                                                                            dither=0.0), colour)
        if facing[2] < 0:
            colour = shade(pal['rim'], 0.75 + 0.2 * mottle, tx.x, tx.y, dither=0.0)
    elif abs(facing[0]) > 0.5:                                # the sides: darker along the top and bottom edges
        k, kc = local_grid(tx, 2)
        colour = np.where(((k == 0) | (k == kc - 1))[..., None], shade(pal['rim'], 0.7 + mottle, tx.x, tx.y,
                                                                         dither=0.0), colour)
    root = t < 0.06 + 0.04 * (cells(p, tx.texel * 2.02, seed=63) - 0.5)
    return np.where(root[..., None], shade(pal['hide'], 0.45 + 0.1 * mottle, tx.x, tx.y, dither=0.0), colour)


def ridge_paint(tx, pal):
    """The dorsal ridge: dark at its base, the spikes lighter to their tips."""
    p = tx.points
    if tx.box.tag.get('part') == 'nape':
        t = np.clip((np.interp(p[..., 1], *NAPE) - p[..., 2]) / 2.0, 0, 1)
    else:
        t = np.clip((p[..., 1] - back_y(p[..., 2])) / 2.6, 0, 1)
    v = 0.4 + 0.52 * t + 0.1 * (cells(p, tx.texel * 1.01, seed=71) - 0.5)
    return shade(pal['ridge'], v, tx.x, tx.y, dither=0.0)


def keratin(tx, ramp, base, tip):
    """Spurs and hooves: darker at the base, pale to the tip."""
    box, n = tx.box, tx.normal
    a, b = box.tag.get('along', (0.0, 1.0))
    s = ((tx.points - box.center) @ box.axes[:, 1]) / box.size[1] + 0.5
    t = a + (b - a) * np.clip(s, 0, 1)
    v = base + (tip - base) * t ** 0.8 + 0.1 * (cells(tx.points, tx.texel * 1.01, seed=81) - 0.5) + 0.05 * max(n[1], 0)
    return shade(ramp, v, tx.x, tx.y, dither=0.0)


def painter(tx, variant):
    pal = PALETTES[variant]
    mat = tx.box.mat
    part = tx.box.tag.get('part')
    alpha = None
    if mat == 'ridge':
        if abs(tx.normal[0]) > 0.5:
            alpha = nape_alpha(tx) if part == 'nape' else ridge_alpha(tx)
        return ridge_paint(tx, pal), alpha
    if mat == 'hide':
        if part in ('trunk', 'tail', 'neck'):
            return hide(tx, pal), None
        return limb(tx, pal), None
    if mat == 'head':
        return head(tx, pal), None
    if mat == 'beak':
        return beak(tx, pal), None
    if mat == 'crest':
        return crest(tx, pal), None
    if mat == 'horn':
        return keratin(tx, HORN_RAMP, 0.3, 0.95), None
    if mat == 'hoof':
        return keratin(tx, HOOF_RAMP, 0.3, 0.8), None
    return shade(MOUTH_RAMP, 0.6, tx.x, tx.y), None
