"""The Triceratops, designed box by box on the ARK rig (Trike); built by scripts/handmade_models.py.

A heavy horned grazer in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a deep
barrel of a body over four columnar legs, the front ones bowed out at the elbows, hoofed toes, a tail that tapers
from the hips to a point and a row of small studs along the spine. The great frill fans out behind the skull in
radial plates whose rim carries a crown of spikes, two long horns rising from its top and a short one at its
centre; two long brow horns sweep forward over the eyes, a short horn sits on the nose, and the face ends in a
hooked parrot beak. The beak is designed shut in the idle (the ARK idle holds it a little open).
"""
import math

import numpy as np

from handmade_models import cells, fbm, hexes, lift, noise, shade, unit

FOLDER, STEM, HEIGHT = 'Triceratops', 'triceratops', 5.0
DENSITY = 1.0            # texels per runtime unit; the head, frill and horns are painted at FACE times that
FACE = 2.0
ATLAS_WIDTH = 512
PREVIEW_VARIANT = 'Natural'
ASSET = 'Trike'
PREVIEW_CLIPS = ('Trike-Idle', 'Trike-Move-Fwd', 'Trike-Attack-Horn', 'Trike-Torpid')
JAW_POSE = ('Trike-Idle', 0.0, ['jaw', 'tongue'])   # the frame the beak is designed shut in
JAW_REFERENCE = {'c_jaw': 18.0}                      # the angle at which the ARK mesh's beak is shut
HEAD, JAW = 'c_head', 'c_jaw'

# The trunk, segment by segment: (bone, rear z, front z, tiers from the belly up as (bottom, top, half width)),
# measured on the ARK mesh's cross-sections, the legs left out.
TRUNK = [
    ('c_back1', -11.6, -8.0, [(6.7, 7.62, 1.75), (7.62, 9.42, 3.9), (9.42, 15.33, 5.34), (15.33, 17.15, 4.27),
                              (17.15, 18.06, 2.51)]),
    ('c_back1', -8.2, -4.3, [(5.95, 7.0, 1.75), (7.0, 9.09, 3.7), (9.09, 15.88, 5.27), (15.88, 17.97, 4.21),
                             (17.97, 19.02, 2.27)]),
    ('c_back2', -4.5, -0.3, [(6.33, 7.33, 2.02), (7.33, 9.31, 3.75), (9.31, 15.76, 5.14), (15.76, 17.75, 3.66),
                             (17.75, 18.74, 1.97)]),
    ('c_back3', -0.5, 3.6, [(6.45, 7.6, 2.0), (7.6, 9.6, 3.9), (9.6, 15.6, 5.25), (15.6, 17.5, 3.9),
                            (17.5, 18.55, 2.0)]),
    ('c_back4', 3.4, 7.6, [(6.6, 7.7, 2.0), (7.7, 9.5, 3.8), (9.5, 15.0, 5.0), (15.0, 16.8, 3.7), (16.8, 17.6, 2.0)]),
]
NECK = [('c_neck1', 7.4, 10.0, [(8.6, 9.8, 2.2), (9.8, 14.6, 4.1), (14.6, 15.9, 2.8)]),
        ('c_neck2', 9.6, 12.2, [(9.85, 10.9, 2.0), (10.9, 14.5, 3.15), (14.5, 15.5, 2.2)])]
TAIL_SEGMENTS = [
    ('c_tail5', -34.8, -33.2, [(15.54, 15.64, 0.35), (15.64, 15.95, 0.41), (15.95, 16.05, 0.34)]),
    ('c_tail5', -33.4, -31.2, [(14.88, 15.08, 0.48), (15.08, 15.69, 0.6), (15.69, 15.89, 0.48)]),
    ('c_tail4', -31.4, -29.4, [(14.11, 14.46, 0.62), (14.46, 15.53, 0.86), (15.53, 15.88, 0.59)]),
    ('c_tail4', -29.6, -27.3, [(13.42, 13.91, 0.76), (13.91, 15.37, 1.12), (15.37, 15.86, 0.72)]),
    ('c_tail3', -27.5, -25.1, [(12.72, 13.34, 0.89), (13.34, 15.2, 1.39), (15.2, 15.82, 0.84)]),
    ('c_tail3', -25.3, -22.8, [(12.0, 12.76, 1.02), (12.76, 15.06, 1.74), (15.06, 15.82, 0.99)]),
    ('c_tail2', -23.0, -20.2, [(11.22, 12.14, 1.17), (12.14, 14.9, 2.17), (14.9, 15.82, 1.27)]),
    ('c_tail2', -20.4, -17.4, [(10.29, 11.41, 1.37), (11.41, 14.77, 2.7), (14.77, 15.89, 1.78)]),
    ('c_taill1', -17.6, -14.5, [(9.28, 10.65, 1.56), (10.65, 14.79, 3.31), (14.79, 16.17, 2.37)]),
    ('c_taill1', -14.7, -11.3, [(8.19, 9.93, 1.98), (9.93, 15.16, 4.34), (15.16, 16.9, 2.78)]),
]
# The small studs along the spine (the ARK mesh's, measured): z.
STUDS = [-7.4, -6.6, -5.55, -4.45, -3.5, -2.5, -1.3, -0.25, 0.6, 1.25, 2.3]

# The frill as radial plates fanning out from behind the skull, seen from the front at angle phi (degrees from the
# animal's left, +x, over the top): (phi, radius of the rim from the frill's centre line y = FRILL_Y, z of the rim).
FRILL_Y, FRILL_HUB = 15.0, (2.3, 11.7)          # the plates start at radius 2.3 from the centre line, at z 11.7
FRILL = [(90, 7.2, 5.95), (70, 7.0, 5.9), (50, 6.95, 4.95), (30, 6.6, 4.6), (10, 6.4, 5.5), (-10, 6.25, 7.45),
         (-30, 5.75, 9.55), (-48, 5.05, 11.1)]
# Spikes round the rim, as the ARK mesh has them: (base, tip, base width), left side (mirrored).
RIM_SPIKES = [((4.3, 20.35, 5.05), (5.3, 21.75, 4.15), 0.85), ((5.55, 18.3, 4.75), (7.05, 19.05, 3.8), 0.9),
              ((5.36, 16.26, 4.88), (7.36, 16.54, 3.77), 0.9), ((5.8, 13.82, 7.74), (6.41, 11.84, 7.37), 0.95),
              ((5.16, 12.76, 9.76), (5.45, 11.03, 9.47), 0.85), ((3.09, 13.07, 12.7), (4.73, 12.5, 12.99), 0.65),
              ((3.13, 12.76, 13.8), (3.06, 11.04, 14.39), 0.72)]
# The small spikes down the frill's middle ridge: (base, tip).
CREST = [((0.0, 18.15, 11.29), (0.0, 19.01, 10.75)), ((0.0, 18.7, 10.32), (0.0, 19.8, 9.83)),
         ((0.0, 19.52, 9.11), (0.0, 20.7, 8.73)), ((0.0, 20.99, 8.02), (0.0, 22.44, 7.57))]
# Horn centre lines from the base (sunk into the head) to the tip, with their widths, after the ARK mesh.
BROW_HORN = [((1.8, 17.45, 14.2), 2.1), ((2.15, 18.92, 15.57), 1.6), ((2.52, 20.0, 17.3), 1.15),
             ((2.89, 21.2, 19.22), 0.75), ((3.21, 23.05, 20.85), 0.3)]
NOSE_HORN = [((0.0, 14.75, 18.85), 2.0), ((0.0, 15.77, 19.17), 1.5), ((0.0, 16.59, 20.0), 0.85),
             ((0.0, 17.8, 20.45), 0.3)]
SHIELD_HORN = [((2.0, 21.5, 5.35), 1.85), ((2.22, 23.06, 4.13), 1.5), ((2.72, 24.6, 3.25), 1.05),
               ((2.94, 26.24, 2.36), 0.7), ((3.52, 27.68, 1.02), 0.28)]
CENTRE_SPIKE = [((0.0, 21.4, 7.2), 2.0), ((0.0, 22.6, 6.25), 1.35), ((0.0, 23.7, 5.9), 0.85), ((0.0, 24.73, 5.12), 0.3)]


def tiers(d, bone, z0, z1, rows, mat='hide', part='trunk', inset=0.03):
    """Stacked boxes across a segment, each overlapping the next a little; the tiers off the core are inset a
    hair at both ends, so no two neighbours share an end plane."""
    core = max(range(len(rows)), key=lambda k: rows[k][2])
    for k, (y0, y1, half) in enumerate(rows):
        e = 0.0 if k == core else inset * (1 + abs(k - core))
        lo = y0 - (0.04 if k > core else 0.0)
        hi = y1 + (0.04 if k < core else 0.0)
        d.span(bone, (-half, lo, z0 + e), (half, hi, z1 - e), mat, part=part, tier=k - core, rows=len(rows))


def horn(d, bone, line, mat='horn', mirror=False, **tag):
    """A tapering horn along its centre line: one box per span, each as wide as the line there, overlapping the
    next a little; the spans are numbered along the horn for the painter."""
    total = sum(float(np.linalg.norm(np.subtract(line[k + 1][0], line[k][0]))) for k in range(len(line) - 1))
    run = 0.0
    for k in range(len(line) - 1):
        (a, wa), (b, wb) = line[k], line[k + 1]
        length = float(np.linalg.norm(np.subtract(b, a)))
        w = (wa + wb) / 2 if k < len(line) - 2 else (wa + 2 * wb) / 3
        d.seg(bone, a, b, w, w, mat, ext=(0.12 if k else 0.0, 0.0 if k == len(line) - 2 else 0.1), side=(1, 0, 0),
              mirror=mirror, part='horn', along=(run / total, (run + length) / total), **tag)
        run += length


def spike(d, bone, base, tip, width, mat='horn', mirror=False, reach=1.12, **tag):
    """A spike from `base` (sunk a little) to `tip`, drawn out by `reach`: three tapering boxes."""
    base, tip = np.asarray(base, float), np.asarray(tip, float)
    tip = base + (tip - base) * reach
    side = min(((1.0, 0.0, 0.0), (0.0, 0.0, 1.0), (0.0, 1.0, 0.0)), key=lambda a: abs(unit(tip - base) @ a))
    stops, widths = (0.0, 0.42, 0.75, 1.0), (1.0, 0.68, 0.36)
    for k in range(3):
        a, b = base + (tip - base) * stops[k], base + (tip - base) * stops[k + 1]
        d.seg(bone, a, b, width * widths[k], width * widths[k], mat, ext=(0.25 if k == 0 else 0.04, 0.0 if k == 2 else 0.04),
              side=side, mirror=mirror, part='spike', along=(stops[k], stops[k + 1]), **tag)


def frill_point(phi, radius, z):
    a = math.radians(phi)
    return np.array([radius * math.cos(a), FRILL_Y + radius * math.sin(a), z])


def frill(d, mat='frill'):
    """The frill's radial plates: each runs from the hub behind the skull out to its point on the rim, laid in the
    frill's dished surface, as wide as its share of the rim and a little more, so neighbours overlap."""
    rim = {phi: frill_point(phi, r, z) for phi, r, z in FRILL}
    phis = [phi for phi, _, _ in FRILL]
    for k, (phi, r, z) in enumerate(FRILL):
        outer = rim[phi]
        inner = frill_point(phi, FRILL_HUB[0], FRILL_HUB[1])
        # the rim's tangent from the neighbours (mirrored across the top for the middle plate)
        prev = rim[phis[k + 1]] if k + 1 < len(phis) else outer
        nxt = rim[phis[k - 1]] if k > 0 else rim[phis[1]] * np.array([-1.0, 1.0, 1.0])
        tangent = unit(nxt - prev)
        along = unit(outer - inner)
        normal = unit(np.cross(along, tangent))
        if normal[2] < 0:
            normal = -normal
        across = np.cross(along, normal)
        step = math.radians(20.0)
        width = r * step * 1.18 + 0.3
        thick = 0.95
        length = float(np.linalg.norm(outer - inner)) + 0.25
        centre = inner + along * length / 2 - normal * thick / 2
        d.add(HEAD, centre, (width, length, thick), np.column_stack([across, along, normal]), mat,
              mirror=phi != 90, part='frill', phi=phi, density=FACE)


def design(d):
    rig = d.rig
    P = rig.pivot
    # the beak and tongue are designed shut in the idle (the ARK idle holds them a little open)
    for bone in (JAW, 'c_tongue1', 'c_tongue2'):
        rig.design_as_in(bone, 'Trike-Idle', 0.0, HEAD)
    f = dict(density=FACE)

    # ---------------------------------------------------------------- trunk, neck and tail
    for bone, z0, z1, rows in TRUNK:
        tiers(d, bone, z0, z1, rows)
    for bone, z0, z1, rows in NECK:
        tiers(d, bone, z0, z1, rows, part='neck')
    for bone, z0, z1, rows in TAIL_SEGMENTS:
        tiers(d, bone, z0, z1, rows, part='tail', inset=0.06)
    for z in STUDS:
        bone = 'c_back1' if z < -4.4 else 'c_back2' if z < -0.4 else 'c_back3'
        top = next(rows[-1][1] for b, z0, z1, rows in TRUNK if b == bone and z0 <= z <= z1)
        d.seg(bone, (0.0, top - 0.2, z + 0.12), (0.0, top + 0.55, z - 0.22), 0.42, 0.5, 'horn', side=(1, 0, 0),
              part='stud', along=(0.0, 1.0))

    # ---------------------------------------------------------------- head: skull, face, snout and beak
    d.span(HEAD, (-3.75, 12.9, 10.2), (3.75, 17.4, 13.2), 'head', part='skull', **f)
    d.span(HEAD, (-1.65, 17.3, 11.0), (1.65, 17.9, 15.5), 'head', part='forehead', **f)
    d.span(HEAD, (-2.72, 13.0, 13.0), (2.72, 16.75, 15.6), 'head', part='face', **f)
    d.span(HEAD, (-2.95, 16.65, 13.1), (2.95, 17.55, 15.65), 'head', part='brow', **f)
    d.span(HEAD, (-3.45, 12.2, 12.3), (3.45, 14.9, 14.95), 'head', part='cheek', **f)
    d.span(HEAD, (-2.4, 12.6, 15.4), (2.4, 16.4, 17.4), 'head', part='snout', **f)
    d.span(HEAD, (-1.95, 12.5, 17.2), (1.95, 15.75, 18.8), 'head', part='snout', **f)
    # the upper beak narrows in steps from under the nose horn to its hooked point
    d.span(HEAD, (-1.55, 13.6, 18.6), (1.55, 15.0, 19.75), 'beak', part='beak', **f)
    d.span(HEAD, (-1.15, 12.6, 18.65), (1.15, 13.7, 20.0), 'beak', part='beak', **f)
    d.span(HEAD, (-0.75, 11.8, 18.85), (0.75, 12.7, 20.25), 'beak', part='beak', **f)
    d.span(HEAD, (-0.45, 11.25, 19.85), (0.45, 12.0, 20.45), 'beak', part='hook', **f)
    # the lower jaw, shut under the snout, its beak inside the hook of the upper one
    d.span(JAW, (-2.7, 10.3, 12.35), (2.7, 12.95, 14.6), 'head', part='jaw', **f)
    d.span(JAW, (-2.3, 11.0, 14.4), (2.3, 12.9, 16.6), 'head', part='jaw', **f)
    d.span(JAW, (-1.85, 11.3, 16.4), (1.85, 12.85, 18.2), 'head', part='jaw', **f)
    d.span(JAW, (-1.1, 11.36, 18.0), (1.1, 12.55, 19.45), 'beak', part='lowbeak', **f)
    d.span('c_tongue1', (-0.8, 12.92, 14.5), (0.8, 13.12, 17.3), 'mouth', part='tongue', **f)

    # ---------------------------------------------------------------- horns and the frill
    horn(d, 'l_browhorn', BROW_HORN, mirror=True, **f)
    horn(d, 'c_nosehorn', NOSE_HORN, **f)
    horn(d, 'l_shieldhorn', SHIELD_HORN, mirror=True, **f)
    horn(d, 'c_shieldhorn', CENTRE_SPIKE, **f)
    frill(d)
    for base, tip, width in RIM_SPIKES:
        spike(d, HEAD, base, tip, width, mirror=True, **f)
    for base, tip in CREST:
        spike(d, HEAD, base, tip, 0.5, **f)

    # ---------------------------------------------------------------- forelegs, bowed out at the elbows
    d.span('l_scapula', (3.96, 9.4, 2.6), (5.75, 14.6, 8.2), 'hide', mirror=True, part='shoulder')
    shoulder, elbow, wrist = P('l_shoulder'), P('l_eblow'), P('l_wrist')
    d.seg('l_shoulder', shoulder + (0.2, 0.6, 0.3), elbow, 3.1, 3.6, 'hide', ext=(0.4, 0.5), mirror=True, part='arm')
    d.seg('l_eblow', elbow, wrist, 2.9, 3.3, 'hide', ext=(0.35, 0.2), mirror=True, part='forearm')
    d.span('l_wrist', (3.2, 0.05, 3.5), (6.3, 2.2, 6.9), 'hide', mirror=True, part='hand')
    for k, (toe, (x0, x1, z0, z1, h)) in enumerate({'A': (2.95, 3.78, 4.7, 6.75, 0.9), 'B': (3.55, 4.5, 6.15, 7.85, 1.15),
                                                   'C': (4.4, 5.4, 6.35, 8.05, 1.05), 'D': (5.12, 6.25, 6.15, 7.6, 1.0),
                                                   'E': (5.85, 6.72, 4.75, 6.7, 0.95)}.items()):
        d.span(f'l_frontToe{toe}1', (x0, 0.03 * (k % 2), z0), (x1, h, z1), 'hoof', mirror=True, part='toe')

    # ---------------------------------------------------------------- hind legs
    d.span('l_hip', (4.3, 12.8, -10.6), (6.85, 15.85, -4.56), 'hide', mirror=True, part='thigh')
    d.span('l_legTwist', (3.6, 8.2, -10.9), (7.25, 13.4, -3.55), 'hide', mirror=True, part='thigh')
    knee, ankle = P('l_knee'), P('l_ankle')
    d.seg('l_kneeTwist', knee + (0.0, 0.4, 0.0), ankle, 3.6, 4.2, 'hide', ext=(0.6, 0.2), mirror=True, part='shin')
    d.span('l_ankle', (4.35, 0.05, -9.85), (8.0, 2.4, -6.25), 'hide', mirror=True, part='foot')
    for k, (toe, (x0, x1, z0, z1, h)) in enumerate({'A': (4.3, 5.6, -7.6, -5.62, 1.5), 'B': (5.5, 7.0, -6.9, -5.02, 1.3),
                                                   'C': (6.72, 8.08, -7.3, -5.68, 1.2)}.items()):
        d.span(f'l_backToe{toe}1', (x0, 0.03 * (k % 2), z0), (x1, h, z1), 'hoof', mirror=True, part='toe')


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(hide=hexes('#2b2c22', '#393a2c', '#484a37', '#595b43', '#6b6d50', '#7e805e', '#92936d', '#a6a77d'),
                    belly=hexes('#7f7a60', '#918b70', '#a39d80', '#b4ae91', '#c4bfa3', '#d3cfb5'),
                    band=hexes('#1d1e16', '#26271d', '#303125', '#3b3c2e'),
                    frill=hexes('#5a2a18', '#73361e', '#8c4626', '#a65830', '#bd6c3e', '#d0824f'),
                    rim=hexes('#24160f', '#321f15', '#41291c', '#513424')),
    'olive': dict(hide=hexes('#2b2c22', '#393a2c', '#484a37', '#595b43', '#6b6d50', '#7e805e', '#92936d', '#a6a77d'),
                  belly=hexes('#7f7a60', '#918b70', '#a39d80', '#b4ae91', '#c4bfa3', '#d3cfb5'),
                  band=hexes('#1d1e16', '#26271d', '#303125', '#3b3c2e'),
                  frill=hexes('#5a2a18', '#73361e', '#8c4626', '#a65830', '#bd6c3e', '#d0824f'),
                  rim=hexes('#24160f', '#321f15', '#41291c', '#513424')),
    'slate': dict(hide=hexes('#2c2f31', '#3c4042', '#4d5153', '#5f6365', '#717678', '#84898b', '#979d9f', '#abb1b3'),
                  belly=hexes('#7e8181', '#8f9292', '#a1a4a2', '#b4b6b4', '#c6c8c5', '#d9dbd7'),
                  band=hexes('#16191b', '#202326', '#2a2e31', '#34393c'),
                  frill=hexes('#6e2219', '#832f23', '#983d2d', '#ad4b37', '#c35942', '#da684d'),
                  rim=hexes('#2a0f0b', '#3a1510', '#4a1c15', '#5b231a')),
    'umber': dict(hide=hexes('#2a2018', '#3a2e23', '#4b3c2e', '#5c4b3a', '#6e5a46', '#816a53', '#947a60', '#a78b6d'),
                  belly=hexes('#807159', '#91836b', '#a3957d', '#b5a790', '#c7baa3', '#dacdb7'),
                  band=hexes('#14100c', '#1d1712', '#271f18', '#31271e'),
                  frill=hexes('#6b4d13', '#82611e', '#99752a', '#b18a36', '#c9a042', '#e2b64e'),
                  rim=hexes('#2a1c07', '#3a280a', '#4b350e', '#5c4311')),
    'sand': dict(hide=hexes('#5e563f', '#6f674e', '#81785e', '#93896d', '#a59b7e', '#b8ad8f', '#cbc0a0', '#dfd3b1'),
                 belly=hexes('#c3baa1', '#cdc5ae', '#d7cfbb', '#e1dac8', '#ebe5d6', '#f5f0e3'),
                 band=hexes('#4b4334', '#595040', '#685e4c', '#776c58'),
                 frill=hexes('#7b3b25', '#8d4a2f', '#a05a3a', '#b26b45', '#c67b50', '#d98c5c'),
                 rim=hexes('#3b1e13', '#4a2719', '#5a3120', '#6b3b27')),
}
HORN_RAMP = hexes('#3f3427', '#5b4c38', '#7a6a50', '#9a8a6c', '#b9aa8a', '#d3c6a7', '#e8dec6', '#f4eedf')
BEAK_RAMP = hexes('#121110', '#1b1a18', '#252320', '#302d29', '#3d3934', '#4b4740')
HOOF_RAMP = hexes('#1a1714', '#25211d', '#312c26', '#3e3830', '#4c453b')
MOUTH_RAMP = hexes('#3a1414', '#55201f', '#71302c', '#8c423b', '#a5574c')
TONGUE_RAMP = hexes('#6a2f2c', '#86403b', '#a0534b', '#b7675d', '#c97c70')

# The body's outline along its length (z), from the tail tip to the neck: the top of the back and the underside.
BACK = ([-35, -30, -25, -20, -15, -11, -7, -4, -1, 2, 5, 8, 11],
        [16.0, 15.9, 15.8, 15.85, 16.6, 17.8, 19.1, 19.1, 18.9, 18.3, 17.4, 16.0, 15.5])
UNDER = ([-35, -30, -25, -20, -15, -11, -7, -4, -1, 2, 5, 8, 11],
         [15.6, 14.2, 12.6, 10.6, 8.9, 6.9, 5.9, 6.2, 6.4, 6.4, 6.6, 8.5, 9.8])
# Broad dark saddles across the back, as pixel runs: (z where a band crosses the spine, its width in texels, how
# far down the side it reaches in units).
BANDS = [(4.2, 4, 3.6), (0.4, 5, 5.4), (-3.4, 5, 5.8), (-7.2, 5, 5.6), (-11.0, 4, 4.8), (-15.0, 4, 3.6),
         (-19.2, 3, 2.8), (-23.3, 3, 2.3), (-27.3, 2, 1.8), (-31.0, 2, 1.3)]


def back_y(z):
    return np.interp(z, *BACK)


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
    """A saddle band as pixel runs down the flank: runs of 2-4 rows, wobbling a texel back and forth, narrowing over
    its lower half to a ragged end. Returns (first row, last row, shift, width) in texels."""
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


RUNS = [band_runs(61 + k, w, 60) for k, (_, w, _) in enumerate(BANDS)]


def bands(tx):
    """The dark saddles of BANDS across the back and down the sides, on the face's own texels along the body."""
    p, n, box = tx.points, tx.normal, tx.box
    mask = np.zeros(p.shape[:2], bool)
    axis = box.axes[:, 2]
    if abs(n @ axis) > 0.7 or n[1] < -0.5:
        return mask
    count = max(1, int(round(box.size[2] / tx.texel)))
    local = (p - box.center) @ axis
    iz = np.clip(np.floor((local / box.size[2] + 0.5) * count).astype(int), 0, count - 1)
    rows = np.floor((back_y(p[..., 2]) - p[..., 1] + np.abs(p[..., 0]) * 0.4) / tx.texel).astype(int)
    for (z0, width, reach), runs in zip(BANDS, RUNS):
        col = int(np.floor(((z0 - box.center[2]) / box.size[2] + 0.5) * count))
        if col < -20 or col > count + 20:
            continue
        limit = int(reach / tx.texel)
        for first, last, shift, w in runs:
            if first > limit:
                break
            c0 = col + shift - w // 2
            mask |= (rows >= first) & (rows <= min(last, limit)) & (iz >= c0) & (iz < c0 + w)
    return mask


def hide(tx, pal):
    """The body: big pebbled scales over a dark back that lightens down the flanks, a row of broad plates along the
    spine, the dark saddles, pale speckles on the flanks and the pale wrinkled underside."""
    p, n = tx.points, tx.normal
    f = np.clip((p[..., 1] - under_y(p[..., 2])) / np.maximum(back_y(p[..., 2]) - under_y(p[..., 2]), 0.5), 0, 1)
    mottle = fbm(p, 4.0, seed=3, octaves=2) - 0.5
    v = 0.8 - 0.44 * f ** 1.2 + 0.12 * mottle + scales(tx, 4, seed=5, seam=0.11)
    if n[1] > 0.5:
        near = np.abs(p[..., 0]) < 1.6
        v = np.where(near, 0.78 - 0.44 * f ** 1.2 + 0.1 * mottle + scales(tx, 5, seed=9, seam=0.16) + 0.05, v)
    v += 0.04 * max(n[1], 0)
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    band = bands(tx)
    if np.any(band):
        colour = np.where(band[..., None], shade(pal['band'], 0.55 + 0.6 * mottle + 0.6 * scales(tx, 4, seed=5),
                                                 tx.x, tx.y, dither=0.0), colour)
    speck = (cells(p, tx.texel * 2.02, seed=11) > 0.9) & (f > 0.25) & (f < 0.7) & ~band & (abs(n[1]) < 0.5)
    colour = np.where(speck[..., None], shade(pal['hide'], v + 0.2, tx.x, tx.y, dither=0.0), colour)
    pebble = scales(tx, 4, seed=7)
    edge = 0.24 + 0.05 * (noise(p, 1.2, seed=8) - 0.5) + 0.9 * pebble
    under = (f < edge) | (n[1] < -0.5)
    if np.any(under):
        col, row = face_grid(tx)
        wave = np.floor(1.6 * np.sin(col * 0.25 + tx.face.index)).astype(int)
        belly = 0.55 + 0.1 * mottle + 0.7 * scales(tx, 4, seed=6, seam=0.08) - 0.12 * ((row + wave) % 4 == 0)
        colour = np.where(under[..., None], shade(pal['belly'], belly, tx.x, tx.y, dither=0.0), colour)
    return colour


def limb(tx, pal):
    """Columnar legs: thick skin in wrinkled rings, darker toward the feet, paler inside and under the thighs."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    mottle = fbm(p, 3.0, seed=13, octaves=2) - 0.5
    col, row = face_grid(tx)
    wave = np.floor(1.4 * np.sin(col * 0.4 + tx.face.index * 1.7)).astype(int)
    ring = ((row + wave) % 4 == 0) & (abs(n[1]) < 0.5)
    v = 0.6 + 0.1 * mottle + scales(tx, 3, seed=14) - 0.14 * ring - 0.16 * np.clip((4.0 - p[..., 1]) / 4.0, 0, 1)
    if part in ('shoulder', 'thigh'):
        v = 0.5 + 0.1 * mottle + scales(tx, 4, seed=14, seam=0.1) - 0.08 * np.clip((p[..., 1] - 12) / 4, 0, 1)
    if abs(n[0]) > 0.5 and n[0] * np.sign(tx.box.center[0]) < 0:
        v += 0.08
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    if part in ('shoulder', 'thigh'):
        band = bands(tx)
        if np.any(band):
            colour = np.where(band[..., None], shade(pal['band'], 0.6 + 0.6 * mottle, tx.x, tx.y, dither=0.0), colour)
    if n[1] < -0.5:
        colour = shade(pal['belly'], 0.42 + 0.08 * mottle, tx.x, tx.y, dither=0.0)
    return colour


def frill_skin(tx, pal):
    """The frill's front face: the head's skin near the hub, then the coloured fan with a light rib down the middle
    of every plate and dark seams between them, a row of pale spots and the dark rim; its back is the body's."""
    p, n, box = tx.points, tx.normal, tx.box
    s = ((p - box.center) @ box.axes[:, 1]) / box.size[1] + 0.5           # 0 at the hub, 1 at the rim
    across, count = local_grid(tx, 0)
    along, rows = local_grid(tx, 1)
    mottle = fbm(p, 2.5, seed=31, octaves=2) - 0.5
    facing = n @ box.axes[:, 2]
    rim = along >= rows - 2
    if facing < 0.5:
        if facing < -0.5:                                                  # the back of the frill
            v = 0.42 + 0.1 * mottle + scales(tx, 3, seed=34)
            colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
            return np.where(rim[..., None], shade(pal['rim'], 0.5, tx.x, tx.y, dither=0.0), colour)
        return shade(pal['rim'], 0.45 + 0.3 * (cells(p, tx.texel, seed=35) - 0.5), tx.x, tx.y, dither=0.0)
    mid = np.abs(across - (count - 1) / 2)
    v = 0.52 + 0.12 * mottle + 0.5 * scales(tx, 3, seed=32, seam=0.08)
    v = v + 0.14 * (mid <= 1) - 0.16 * (mid >= (count - 1) / 2 - 0.5)
    colour = shade(pal['frill'], v, tx.x, tx.y, dither=0.0)
    skin = s < 0.42 + 0.08 * (cells(p, tx.texel * 2.02, seed=33) - 0.5)
    colour = np.where(skin[..., None], shade(pal['hide'], 0.62 + 0.1 * mottle + scales(tx, 3, seed=36), tx.x, tx.y,
                                             dither=0.0), colour)
    spot_row = int(rows * 0.72)
    spot = (np.abs(along - spot_row) <= 1) & (mid <= 1)
    colour = np.where(spot[..., None], shade(pal['frill'], 0.98, tx.x, tx.y, dither=0.0), colour)
    ring = ((np.abs(along - spot_row) == 2) & (mid <= 1)) | ((np.abs(along - spot_row) <= 1) & (np.abs(mid - 2) < 0.6))
    colour = np.where(ring[..., None], shade(pal['rim'], 0.7, tx.x, tx.y, dither=0.0), colour)
    return np.where(rim[..., None], shade(pal['rim'], 0.4 + 0.3 * (cells(p, tx.texel, seed=37) - 0.5), tx.x, tx.y,
                                          dither=0.0), colour)


def head(tx, pal):
    """The face: the hide's scales, darker round the eyes and over the snout's ridge, pale under the jaw, the
    nostrils; the inside of the mouth."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    mottle = fbm(p, 2.0, seed=41, octaves=2) - 0.5
    f = np.clip((y - 11.0) / 6.5, 0, 1)
    v = 0.78 - 0.36 * f + 0.1 * mottle + scales(tx, 3, seed=42)
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    if abs(n[0]) > 0.5:
        eye = ((z - 14.4) / 1.35) ** 2 + ((y - 15.9) / 1.05) ** 2 < 1
        colour = np.where(eye[..., None], shade(pal['hide'], v - 0.24, tx.x, tx.y, dither=0.0), colour)
        if part == 'snout':
            nostril = (np.abs(z - 18.45) < 0.3) & (np.abs(y - 14.6) < 0.22)
            colour = np.where(nostril[..., None], shade(pal['band'], 0.0, tx.x, tx.y), colour)
        if part == 'cheek':
            _, row = face_grid(tx)
            colour = np.where((row >= tx.face.rect[3] - 2)[..., None], shade(pal['hide'], v + 0.16, tx.x, tx.y), colour)
    if tx.box.bone == JAW:
        pale = (y < 11.0 + 0.3 * (noise(p, 0.8, seed=44) - 0.5)) | (n[1] < -0.5)
        colour = np.where(pale[..., None], shade(pal['belly'], 0.5 + 0.08 * mottle + 0.5 * scales(tx, 3, seed=43),
                                                 tx.x, tx.y, dither=0.0), colour)
        if n[1] > 0.5:
            colour = shade(MOUTH_RAMP, 0.4 + 0.2 * mottle, tx.x, tx.y)
    elif n[1] < -0.5:
        colour = shade(MOUTH_RAMP, 0.3 + 0.2 * mottle, tx.x, tx.y) if part in ('snout', 'face') else \
            shade(pal['belly'], 0.45 + 0.08 * mottle, tx.x, tx.y)
    return colour


def beak(tx, pal):
    """The beak's dark keratin: it covers the front and the lower edge of the upper beak and the whole lower beak; the
    skin of the snout reaches down over its top behind the nose horn, and its worn edge catches a lighter tone."""
    p, n, part = tx.points, tx.normal, tx.box.tag.get('part')
    y, z = p[..., 1], p[..., 2]
    v = 0.42 + 0.14 * (cells(p, tx.texel * 1.01, seed=52) - 0.5) + 0.12 * max(n[1], 0)
    colour = shade(BEAK_RAMP, v, tx.x, tx.y, dither=0.0)
    if part == 'beak':
        skin = (z < 18.9 + (15.0 - y) * 0.55) & (n[2] < 0.5)
        if n[1] > 0.5:
            skin = z < 19.35
        mottle = fbm(p, 2.0, seed=41, octaves=2) - 0.5
        colour = np.where(skin[..., None], shade(pal['hide'], 0.5 + 0.1 * mottle + scales(tx, 3, seed=42), tx.x, tx.y,
                                                 dither=0.0), colour)
        _, row = face_grid(tx)
        edge = (row >= tx.face.rect[3] - 1) & (abs(n[0]) > 0.5) & ~skin
        colour = np.where(edge[..., None], shade(BEAK_RAMP, 0.85, tx.x, tx.y, dither=0.0), colour)
    if part == 'lowbeak' and n[1] > 0.5:
        colour = shade(MOUTH_RAMP, 0.4, tx.x, tx.y)
    return colour


def keratin(tx, ramp, base, tip):
    """Horns, spikes and studs: ridged and darker at the base, smooth and pale to the tip."""
    box, n = tx.box, tx.normal
    a, b = box.tag.get('along', (0.0, 1.0))
    s = ((tx.points - box.center) @ box.axes[:, 1]) / box.size[1] + 0.5
    t = a + (b - a) * np.clip(s, 0, 1)
    k, _ = local_grid(tx, 1)
    ridge = (k % 3 == 0) & (t < 0.5)
    v = base + (tip - base) * t ** 0.8 + 0.1 * (cells(tx.points, tx.texel * 1.01, seed=51) - 0.5) - 0.08 * ridge
    v = v + 0.05 * max(n[1], 0)
    if abs(n @ box.axes[:, 1]) > 0.5:
        v = v - 0.12                                                       # the ledge where a span steps in
    return shade(ramp, v, tx.x, tx.y, dither=0.0)


def painter(tx, variant):
    pal = PALETTES[variant]
    mat = tx.box.mat
    part = tx.box.tag.get('part')
    if mat == 'hide':
        if part in ('trunk', 'neck', 'tail'):
            return hide(tx, pal), None
        return limb(tx, pal), None
    if mat == 'head':
        return head(tx, pal), None
    if mat == 'frill':
        return frill_skin(tx, pal), None
    if mat == 'horn':
        if part == 'stud':
            return keratin(tx, pal['band'], 0.3, 0.9), None
        return keratin(tx, HORN_RAMP, 0.18, 0.95), None
    if mat == 'beak':
        return beak(tx, pal), None
    if mat == 'hoof':
        p = tx.points
        v = 0.45 + 0.4 * np.clip(p[..., 1] / 1.4, 0, 1) + 0.1 * (cells(p, tx.texel * 1.01, seed=53) - 0.5)
        return shade(HOOF_RAMP, v, tx.x, tx.y, dither=0.0), None
    return shade(TONGUE_RAMP, 0.55 + 0.2 * (cells(tx.points, tx.texel * 1.01, seed=54) - 0.5), tx.x, tx.y), None
