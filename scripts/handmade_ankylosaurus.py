"""The Ankylosaurus, designed box by box on the ARK rig (Ankylo); built by scripts/handmade_models.py.

A low, broad tank in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a domed
shell that flares to a rim over the flanks and narrows to a pale belly, carried on four columnar legs with
hoofed toes. Its armour is laid in bands of osteoderms; a row of tall spikes runs down the spine, shorter ones
stand in rows beside it and along the rim, a pair of long spikes guards the neck, and the stiff tail ends in a
heavy club with horned lobes. The beaked head is armoured with knobbed scales under a pair of horns at the back
of the skull and another at the cheeks; two spikes hang from the jaw, which is designed shut as the idle holds it.
"""
import math

import numpy as np

from handmade_models import cells, fbm, hexes, lift, noise, shade, unit

FOLDER, STEM, HEIGHT = 'Ankylosaurus', 'ankylosaurus', 4.0
DENSITY = 1.0
ATLAS_WIDTH = 512
PREVIEW_VARIANT = 'Natural'
ASSET = 'Ankylo'
PREVIEW_CLIPS = ('Ankylo-Idle', 'Ankylo-Move-Fwd', 'Ankylo-Attack-Tail-Sweep', 'Ankylo-Torpid')
HEAD = 'c_neck3'

# The shell, segment by segment: (bone, rear z, front z, rim half width, rim height, back, belly). Each is built
# of five tiers, the dome on top, the body under it, the rim that flares over the flanks, the lower body and the
# belly, so its cross-section rounds over the back and tucks in below the rim like the ARK mesh's.
SHELL = [
    ('c_back1', -6.75, -4.35, 4.95, 8.2, 10.75, 4.75),
    ('c_back1', -4.55, -1.25, 5.85, 8.35, 11.75, 4.05),
    ('c_back2', -1.45, 1.95, 5.75, 8.1, 12.3, 3.7),
    ('c_back3', 1.75, 5.05, 5.25, 7.95, 11.95, 3.7),
    ('c_back4', 4.85, 7.75, 4.35, 7.6, 11.05, 4.15),
]
# The ARK mesh's spikes, measured: (bone, base, tip, base width). Left side and the midline; the left ones are
# mirrored. Spikes shorter than ~1 unit are left to the texture, except the saw of the tail ridge.
SPIKES = [
    # the median row: rear ones lean back, front ones forward
    ('c_tail1', (0.0, 10.95, -5.6), (0.0, 13.02, -7.25), 0.95),
    ('c_back1', (0.0, 11.75, -3.45), (0.0, 14.75, -5.19), 1.3),
    ('c_back1', (0.0, 12.05, -1.35), (0.0, 15.55, -2.96), 1.35),
    ('c_back2', (0.0, 12.15, 0.0), (0.0, 14.78, -0.56), 1.05),
    ('c_back2', (0.0, 12.15, 1.6), (0.0, 15.11, 2.49), 1.3),
    ('c_back3', (0.0, 11.8, 3.2), (0.0, 15.78, 5.18), 1.6),
    ('c_back4', (0.0, 10.85, 6.0), (0.0, 14.22, 7.55), 1.45),
    ('c_back4', (0.0, 10.2, 7.25), (0.0, 11.99, 8.92), 0.95),
    ('c_neck1', (0.0, 9.45, 8.45), (0.0, 12.08, 10.34), 1.3),
    ('c_neck1', (0.0, 8.85, 9.95), (0.0, 10.27, 9.68), 0.7),
    ('c_neck2', (0.0, 8.5, 10.9), (0.0, 9.81, 10.49), 0.6),
    # beside the spine
    ('c_tail1', (1.7, 10.75, -5.25), (2.35, 12.16, -6.26), 1.1),
    ('c_back1', (1.55, 11.75, -1.8), (1.69, 13.43, -2.95), 1.25),
    ('c_back2', (1.4, 12.15, 0.35), (2.11, 14.38, -0.51), 1.2),
    ('c_back2', (1.95, 11.8, 2.15), (2.97, 13.66, 1.12), 1.1),
    ('c_back3', (0.95, 11.6, 3.75), (1.35, 13.42, 4.36), 0.95),
    ('c_back4', (1.5, 10.85, 4.55), (1.92, 12.54, 5.2), 0.95),
    ('c_back4', (0.85, 10.75, 5.3), (1.31, 12.52, 5.71), 0.95),
    # over the shoulder of the shell
    ('c_tail1', (3.3, 9.85, -6.0), (3.89, 10.62, -6.82), 0.9),
    ('c_back1', (4.3, 10.1, -3.35), (4.95, 10.84, -4.17), 0.9),
    ('c_back1', (3.1, 10.85, -2.3), (3.34, 11.79, -3.21), 0.9),
    ('c_back1', (3.35, 10.8, -0.15), (3.84, 12.35, -0.81), 0.95),
    ('c_back1', (4.75, 10.1, -0.6), (5.7, 11.06, -1.63), 0.9),
    ('c_back2', (2.95, 11.0, 2.6), (4.38, 13.41, 2.09), 1.2),
    ('c_back2', (4.1, 10.25, 1.75), (5.22, 11.72, 1.09), 1.0),
    ('c_back3', (2.65, 10.6, 4.2), (3.23, 12.07, 4.06), 0.8),
    ('c_back3', (3.95, 9.4, 4.2), (4.76, 10.36, 3.95), 0.7),
    ('c_back4', (2.6, 9.55, 6.1), (3.22, 10.73, 5.59), 0.75),
    ('c_back4', (2.5, 8.6, 7.75), (3.34, 9.94, 7.52), 0.75),
    # out from the rim, down and back
    ('c_tail1', (4.15, 8.3, -6.25), (6.01, 7.48, -6.53), 0.95),
    ('c_back1', (4.8, 8.55, -5.15), (6.47, 7.8, -5.46), 0.9),
    ('c_back1', (5.15, 8.85, -3.95), (7.07, 8.26, -4.26), 0.95),
    ('c_back1', (5.3, 8.9, -2.6), (7.54, 8.22, -3.01), 1.15),
    ('c_back1', (5.35, 8.6, -1.05), (7.21, 8.05, -1.67), 1.0),
    ('c_back2', (5.3, 7.9, 0.15), (6.97, 7.32, -0.24), 0.9),
    ('c_back2', (5.15, 7.6, 1.5), (7.13, 6.8, 0.77), 1.15),
    ('c_back2', (5.1, 9.45, 1.65), (6.14, 9.94, 0.85), 0.85),
    ('c_back3', (4.95, 7.7, 2.9), (6.27, 7.05, 2.56), 0.8),
    ('c_back3', (4.65, 7.6, 3.75), (6.13, 6.9, 3.44), 0.85),
    ('c_back4', (4.15, 7.5, 5.0), (5.61, 6.85, 4.63), 0.85),
    ('c_back4', (3.4, 7.15, 6.1), (5.09, 6.51, 5.71), 0.85),
    ('c_back4', (2.7, 7.2, 7.5), (3.93, 6.69, 7.14), 0.7),
    # the long spike that guards the neck
    ('c_back4', (1.45, 9.35, 8.1), (3.68, 10.68, 11.94), 1.4),
    # the tail: its rim, the row above it and the saw along its ridge
    ('c_tail1', (3.15, 7.8, -7.65), (5.12, 7.2, -8.13), 0.95),
    ('c_tail1', (2.45, 7.85, -9.3), (3.83, 7.28, -9.69), 0.75),
    ('c_tail1', (1.8, 9.85, -8.4), (2.27, 10.62, -9.2), 0.8),
    ('c_tail2', (2.1, 7.95, -10.6), (3.37, 7.46, -10.92), 0.75),
    ('c_tail2', (1.7, 8.0, -11.85), (2.87, 7.53, -11.97), 0.7),
    ('c_tail3', (1.55, 8.1, -12.95), (2.8, 7.99, -13.0), 0.65),
    ('c_tail3', (1.35, 8.28, -13.85), (2.51, 7.76, -13.89), 0.65),
    ('c_tail3', (1.25, 8.36, -14.8), (2.1, 7.96, -14.87), 0.6),
    ('c_tail4', (1.05, 8.58, -15.7), (1.8, 8.28, -15.73), 0.5),
    ('c_tail4', (0.95, 8.77, -16.5), (1.6, 8.43, -16.79), 0.45),
    # elbows and knees
    ('l_elbow', (4.6, 3.15, 4.0), (5.9, 2.91, 3.58), 0.6),
    ('l_elbow', (4.55, 2.32, 4.13), (5.63, 2.08, 3.77), 0.55),
    ('l_knee', (5.25, 3.26, -4.34), (6.41, 2.97, -4.73), 0.6),
    ('l_knee', (5.05, 2.29, -4.46), (6.09, 2.2, -4.72), 0.55),
]
# The saw of small spikes along the tail's ridge: (bone, z).
SAW = [('c_tail1', -7.75), ('c_tail1', -8.85), ('c_tail1', -9.85), ('c_tail2', -10.7), ('c_tail2', -11.55),
       ('c_tail2', -12.35), ('c_tail3', -12.95), ('c_tail3', -13.65), ('c_tail3', -14.3), ('c_tail4', -15.0),
       ('c_tail4', -15.6), ('c_tail4', -16.2)]

TAIL = ['c_tail1', 'c_tail2', 'c_tail3', 'c_tail4']
# Tail segments: (bone, rear z, front z, half width, bottom, top).
TAIL_BOXES = [('c_tail1', -9.55, -5.6, 3.05, 6.35, 10.3), ('c_tail2', -12.7, -9.25, 2.4, 6.85, 9.85),
              ('c_tail3', -15.5, -12.35, 1.85, 7.15, 9.6), ('c_tail4', -18.1, -15.15, 1.5, 7.45, 9.4)]


def spike(d, bone, base, tip, width, mat='horn', **tag):
    """A tapering spike from `base` (sunk a little into the body) to `tip`: two boxes, three when it is long."""
    base, tip = np.asarray(base, float), np.asarray(tip, float)
    direction = unit(tip - base)
    side = min(((1.0, 0.0, 0.0), (0.0, 0.0, 1.0), (0.0, 1.0, 0.0)), key=lambda a: abs(direction @ a))
    mirror = base[0] > 0.05
    stops = (0.0, 0.4, 0.74, 1.0) if np.linalg.norm(tip - base) > 2.4 or width >= 0.9 else (0.0, 0.5, 1.0)
    widths = (1.0, 0.66, 0.34) if len(stops) == 4 else (1.0, 0.5)
    for k in range(len(stops) - 1):
        a, b = base + (tip - base) * stops[k], base + (tip - base) * stops[k + 1]
        w = width * widths[k]
        d.seg(bone, a, b, w, w, mat, ext=(0.3 if k == 0 else 0.05, 0.0 if k == len(stops) - 2 else 0.05), side=side,
              mirror=mirror, part='spike', along=(stops[k], stops[k + 1]), **tag)


def design(d):
    rig = d.rig
    P = rig.pivot
    rig.design_as_in('c_jaw', 'Ankylo-Idle', 0.0, 'c_neck3')
    shut = rig.placed['c_jaw'] @ np.linalg.inv(rig.world['c_jaw'])     # the bind pose's jaw, moved as the idle holds it

    def closed(point):
        return (shut @ np.r_[np.asarray(point, float), 1.0])[:3]

    # ---------------------------------------------------------------- the shell
    for k, (bone, z0, z1, rim, ye, back, belly) in enumerate(SHELL):
        inset = 0.1 + 0.04 * k
        d.span(bone, (-rim * 0.46, back - 0.6, z0 + inset), (rim * 0.46, back, z1 - inset), 'shell', part='dome')
        d.span(bone, (-rim * 0.8, ye + 1.15, z0), (rim * 0.8, back - 0.55, z1), 'shell', part='upper')
        d.span(bone, (-rim, ye - 0.95, z0 + 0.05), (rim, ye + 1.25, z1 - 0.05), 'shell', part='rim')
        d.span(bone, (-rim * 0.72, belly + 0.95, z0 + 0.02), (rim * 0.72, ye - 0.9, z1 - 0.02), 'hide', part='lower')
        d.span(bone, (-rim * 0.46, belly, z0 + inset), (rim * 0.46, belly + 1.0, z1 - inset), 'hide', part='belly')
    # the shell's rear edge over the tail and its front edge over the neck
    d.span('c_back1', (-3.6, 7.4, -7.15), (3.6, 10.25, -6.55), 'shell', part='upper')
    d.span('c_back4', (-3.4, 6.5, 7.55), (3.4, 10.4, 8.15), 'shell', part='upper')

    # ---------------------------------------------------------------- neck: armour half-rings
    d.span('c_neck1', (-2.65, 4.9, 7.3), (2.65, 9.15, 9.85), 'hide', part='neck')
    d.span('c_neck1', (-2.25, 9.05, 7.6), (2.25, 9.75, 9.5), 'shell', part='band')
    d.span('c_neck2', (-2.2, 5.0, 9.45), (2.2, 8.7, 11.55), 'hide', part='neck')
    d.span('c_neck2', (-1.85, 8.6, 9.75), (1.85, 9.15, 11.25), 'shell', part='band')

    # ---------------------------------------------------------------- spikes, as the ARK mesh has them
    for bone, base, tip, width in SPIKES:
        spike(d, bone, base, tip, width)
    for bone, z in SAW:
        _, z0, z1, half, bottom, top = next(t for t in TAIL_BOXES if t[0] == bone)
        spike(d, bone, (0.0, top - 0.1, z), (0.0, top + 0.75 - 0.03 * (-z - 7), z - 0.12), 0.55 - 0.012 * (-z - 7))

    # ---------------------------------------------------------------- head: knobbed skull, horns, beak
    d.span('c_neck3', (-1.75, 5.3, 11.15), (1.75, 8.25, 12.85), 'head', part='skull')
    d.span('c_neck3', (-1.5, 5.35, 12.75), (1.5, 8.0, 14.25), 'head', part='skull')
    d.span('c_neck3', (-1.2, 8.15, 11.4), (1.2, 8.45, 13.8), 'head', part='skull')
    d.span('c_neck3', (-1.2, 5.45, 14.15), (1.2, 7.55, 15.55), 'head', part='snout')
    d.span('c_neck3', (-0.78, 5.62, 15.45), (0.78, 6.95, 16.12), 'beak', part='beak')
    d.span('c_neck3', (-0.5, 5.36, 15.98), (0.5, 6.25, 16.42), 'beak', part='hook')
    spike(d, 'c_neck3', (1.6, 7.75, 11.95), (3.23, 8.13, 11.31), 1.0)
    # the lower jaw, shut, with the cheek horns and the two spikes that hang from it
    d.span('c_jaw', (-1.3, 4.75, 12.05), (1.3, 5.5, 15.5), 'hide', part='jaw')
    d.span('c_jaw', (-0.75, 4.9, 15.4), (0.75, 5.6, 16.05), 'beak', part='lowbeak')
    spike(d, 'c_jaw', closed((1.5, 5.78, 12.44)), closed((3.03, 4.86, 11.82)), 1.0)
    spike(d, 'c_jaw', closed((0.84, 5.0, 12.67)), closed((0.71, 2.79, 13.63)), 0.62)

    # ---------------------------------------------------------------- legs: columns on hoofed feet
    elbow, wrist = P('l_elbow'), P('l_wrist')
    d.seg('l_shoulder', P('l_shoulder'), elbow, 2.4, 2.55, 'hide', ext=(1.0, 0.45), mirror=True, part='arm')
    d.seg('l_elbow', elbow, wrist, 2.45, 2.25, 'hide', ext=(0.45, 0.25), mirror=True, part='leg')
    d.span('l_wrist', (3.2, 0.25, 3.55), (5.15, 1.85, 5.55), 'hide', mirror=True, part='foot')
    for toe, (x0, x1, z0, z1) in {'l_frontToeA1': (2.95, 3.6, 4.55, 5.75), 'l_frontToeB1': (3.63, 4.15, 5.2, 6.15),
                                  'l_frontToeC1': (4.18, 4.75, 5.2, 6.1), 'l_frontToeD1': (4.78, 5.4, 4.6, 5.65)}.items():
        d.span(toe, (x0, 0.0, z0), (x1, 0.85 - 0.04 * (toe[-2] in 'BC'), z1), 'nail', mirror=True, part='toe')
    knee, ankle = P('l_knee'), P('l_ankle')
    d.seg('l_hip', P('l_hip') + (0.1, 0.0, -0.3), knee, 2.75, 3.5, 'hide', ext=(0.5, 0.45), mirror=True, part='thigh')
    d.seg('l_knee', knee, ankle, 2.55, 2.6, 'hide', ext=(0.5, 0.25), mirror=True, part='leg')
    d.span('l_ankle', (3.65, 0.25, -5.35), (6.0, 1.75, -3.35), 'hide', mirror=True, part='foot')
    for toe, (x0, x1, z0, z1) in {'l_rearToeA1': (3.55, 4.25, -4.1, -2.85), 'l_rearToeB1': (4.28, 5.0, -3.4, -2.35),
                                  'l_rearToeC1': (5.03, 5.75, -3.75, -2.7), 'l_rearToeD1': (5.78, 6.3, -4.45, -3.45)}.items():
        d.span(toe, (x0, 0.0, z0), (x1, 0.85 - 0.04 * (toe[-2] in 'AC'), z1), 'nail', mirror=True, part='toe')

    # ---------------------------------------------------------------- tail: banded, ending in the club
    for k, (bone, z0, z1, half, bottom, top) in enumerate(TAIL_BOXES):
        d.span(bone, (-half, bottom, z0), (half, top, z1), 'shell', part='tail', ring=k)
        d.span(bone, (-half - 0.45, bottom + 1.0, z0 + 0.38 + 0.03 * k), (half + 0.45, top - 1.15, z1 - 0.38 - 0.03 * k),
               'shell', part='tailrim')
    d.span('c_tail5', (-1.75, 7.7, -20.95), (1.75, 10.6, -17.95), 'club', part='club')
    d.span('c_tail5', (-2.35, 8.15, -20.55), (2.35, 10.1, -18.4), 'club', part='club')
    d.span('c_tail5', (-1.2, 10.55, -20.4), (1.2, 11.05, -18.5), 'club', part='club')
    d.span('c_tail5', (-1.25, 7.95, -21.3), (1.25, 10.3, -20.85), 'club', part='club')
    spike(d, 'c_tail5', (1.6, 8.85, -19.45), (3.69, 8.15, -19.91), 1.5, mat='club')
    spike(d, 'c_tail5', (0.85, 10.55, -19.85), (1.31, 11.24, -20.54), 0.75, mat='club')
    spike(d, 'c_tail5', (1.05, 10.3, -18.6), (1.77, 10.91, -18.85), 0.7, mat='club')


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(shell=hexes('#3e3122', '#54422d', '#6b5539', '#816845', '#967b52', '#aa8f62', '#bda374'),
                    hide=hexes('#3d3329', '#504436', '#635545', '#766755', '#897966', '#9c8b78'),
                    belly=hexes('#8a7b66', '#9d8e78', '#b0a28c', '#c2b5a0', '#d2c7b3'),
                    horn=hexes('#4a3a28', '#6a5439', '#8c7150', '#ad916b', '#c9ae88', '#dfcaa7', '#efe3c8')),
    'umber': dict(shell=hexes('#3e3122', '#54422d', '#6b5539', '#816845', '#967b52', '#aa8f62', '#bda374'),
                  hide=hexes('#3d3329', '#504436', '#635545', '#766755', '#897966', '#9c8b78'),
                  belly=hexes('#8a7b66', '#9d8e78', '#b0a28c', '#c2b5a0', '#d2c7b3'),
                  horn=hexes('#4a3a28', '#6a5439', '#8c7150', '#ad916b', '#c9ae88', '#dfcaa7', '#efe3c8')),
    'olive': dict(shell=hexes('#2f3121', '#41432f', '#55563d', '#696a4c', '#7e7f5c', '#93946c', '#a9aa7c'),
                  hide=hexes('#303224', '#424332', '#545640', '#67684f', '#7b7c5e', '#8f906e'),
                  belly=hexes('#86876a', '#98997c', '#aaab8f', '#bcbda2', '#cfd0b6'),
                  horn=hexes('#4a3a28', '#6a5439', '#8c7150', '#ad916b', '#c9ae88', '#dfcaa7', '#efe3c8')),
    'sand': dict(shell=hexes('#5e5442', '#726753', '#867b65', '#9b9078', '#b1a58b', '#c7bb9e', '#ddd1b2'),
                 hide=hexes('#56503f', '#68624f', '#7c7460', '#8f8772', '#a39a84', '#b8ae96'),
                 belly=hexes('#b8ae96', '#c6bda8', '#d4ccb9', '#e2dbcc', '#f0ebde'),
                 horn=hexes('#4a3a28', '#6a5439', '#8c7150', '#ad916b', '#c9ae88', '#dfcaa7', '#efe3c8')),
    'slate': dict(shell=hexes('#313537', '#45494b', '#5a5e60', '#707476', '#878b8d', '#9ea3a4', '#b6bbbc'),
                  hide=hexes('#2f3234', '#414547', '#54585b', '#686c6f', '#7d8184', '#92979a'),
                  belly=hexes('#878b8c', '#9a9d9d', '#adb0af', '#c0c3c1', '#d4d6d4'),
                  horn=hexes('#4a3a28', '#6a5439', '#8c7150', '#ad916b', '#c9ae88', '#dfcaa7', '#efe3c8')),
}
BEAK_RAMP = hexes('#221d18', '#322a22', '#43392e', '#564a3c', '#6a5c4b', '#7e6e5b')
NAIL_RAMP = hexes('#2c2620', '#3e362d', '#52483c', '#675b4c')
MOUTH_RAMP = hexes('#2c1012', '#47191b', '#652829', '#843838', '#9e4b49')


def scutes(tx, size):
    """Osteoderms laid in bands across the body: per texel (inside its scute in [0, 1], on its rim, the scute's
    own random value). Bands run across the back and down the flanks, each shifted half a scute from the next;
    the rims round off at the corners."""
    p, n = tx.points, tx.normal
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    # distance from the spine over the surface: across the top, then down the side; the bands run along z, and on
    # a face looking along z (the ends of a segment) they run down it instead
    u = np.abs(x) * 0.4 + np.clip(12.5 - y, 0, None) if abs(n[0]) > 0.5 else np.abs(x)
    if abs(n[2]) > 0.5:
        z = y
    band = np.floor(z / size)
    shift = (band % 2) * 0.5
    a = u / size + shift
    col = np.floor(a)
    fa, fz = a - col, z / size - band
    ea, ez = np.minimum(fa, 1 - fa) * size, np.minimum(fz, 1 - fz) * size
    rim = (ea < tx.texel * 0.75) | (ez < tx.texel * 0.75) | ((ea < tx.texel * 1.5) & (ez < tx.texel * 1.5))
    value = cells(np.stack([band, col, np.zeros_like(col)], -1), 1.0, seed=91)
    centre = 1 - 2 * np.maximum(np.abs(fa - 0.5), np.abs(fz - 0.5))
    return centre, rim, value


def shell(tx, pal):
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    size = 1.0 if part not in ('tail', 'tailrim') else 0.8
    centre, rim, value = scutes(tx, size)
    grain = cells(p, tx.texel * 1.01, seed=92)
    v = 0.62 + 0.10 * (value - 0.5) + 0.06 * centre + 0.04 * (grain - 0.5)
    v += 0.05 * max(n[1], 0) - 0.05 * max(-n[1], 0)
    v -= 0.05 * (fbm(p, 4.0, seed=93, octaves=2) - 0.5)
    colour = shade(pal['shell'], v, tx.x, tx.y, dither=0.0)
    colour = np.where(rim[..., None], shade(pal['shell'], v - 0.18, tx.x, tx.y, dither=0.0), colour)
    # a knob in the middle of every few scutes, the base of a small spike that wore down
    knob = (value > 0.8) & (centre > 0.6) & (n[1] > 0.5) & (tx.box.tag.get('part') != 'band')
    colour = np.where(knob[..., None], shade(pal['horn'], 0.45 + 0.25 * value, tx.x, tx.y, dither=0.0), colour)
    if n[1] < -0.5:
        colour = shade(pal['hide'], 0.45 + 0.1 * (grain - 0.5), tx.x, tx.y, dither=0.0)
    return colour


def hide(tx, pal):
    """The skin between the plates: small pebbled scales, the pale belly below."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    grain = cells(p, tx.texel * 1.01, seed=94)
    pebble = cells(p, tx.texel * 2.02, seed=95)
    v = 0.5 + 0.12 * (pebble - 0.5) + 0.08 * (grain - 0.5) + 0.04 * max(n[1], 0) - 0.06 * max(-n[1], 0)
    v -= 0.08 * (fbm(p, 3.0, seed=96, octaves=2) - 0.5)
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    pale = np.zeros(p.shape[:2], bool)
    if part in ('belly', 'lower', 'neck'):
        pale = (n[1] < -0.5) | (p[..., 1] < 5.3) if part != 'neck' else (n[1] < -0.5) | (p[..., 1] < 6.0)
    if part == 'jaw':
        pale = n[1] < -0.5
    if part in ('arm', 'leg', 'thigh'):
        pale = (n[0] * np.sign(tx.box.center[0]) < -0.5) & (p[..., 1] < 4.2)
    if np.any(pale):
        belly = shade(pal['belly'], 0.55 + 0.15 * (pebble - 0.5) + 0.08 * (grain - 0.5), tx.x, tx.y, dither=0.0)
        colour = np.where(pale[..., None], belly, colour)
    if part in ('leg', 'arm', 'thigh', 'foot') and abs(n[1]) < 0.5:
        # rings of bigger scales round the legs
        ring = (np.floor(p[..., 1] / 0.62) % 2 == 0) & (pebble > 0.5)
        colour = np.where((ring & ~pale)[..., None], shade(pal['hide'], v + 0.12, tx.x, tx.y, dither=0.0), colour)
    if part == 'jaw' and n[1] > 0.5:
        colour = shade(MOUTH_RAMP, 0.5 + 0.3 * (grain - 0.5), tx.x, tx.y)
    return colour


def head(tx, pal):
    """The skull's armour: small plates in grooves, a few pale knobs, the mouth's line along the bottom."""
    p, n = tx.points, tx.normal
    grain = cells(p, tx.texel * 1.01, seed=97)
    a, b = (p[..., 0], p[..., 2]) if abs(n[1]) > 0.5 else (p[..., 1], p[..., 2]) if abs(n[0]) > 0.5 else (p[..., 0], p[..., 1])
    size = tx.texel * 3
    ia, ib = np.floor(a / size), np.floor(b / size + 0.5 * (np.floor(a / size) % 2))
    plate = cells(np.stack([ia, ib, np.zeros_like(ia)], -1), 1.0, seed=98)
    fa, fb = a / size - ia, b / size + 0.5 * (ia % 2) - ib
    groove = (fa < 1 / 3) | (fb < 1 / 3)                              # one texel of groove, two of plate
    v = 0.6 + 0.12 * (plate - 0.5) + 0.04 * (grain - 0.5) + 0.05 * max(n[1], 0) - 0.06 * max(-n[1], 0)
    colour = shade(pal['shell'], v, tx.x, tx.y, dither=0.0)
    colour = np.where(groove[..., None], shade(pal['shell'], v - 0.18, tx.x, tx.y, dither=0.0), colour)
    knob = (plate > 0.85) & ~groove & (n[1] > -0.5)
    colour = np.where(knob[..., None], shade(pal['horn'], 0.5, tx.x, tx.y, dither=0.0), colour)
    if tx.box.tag.get('part') == 'snout' and n[1] < -0.5:
        colour = shade(MOUTH_RAMP, 0.35, tx.x, tx.y)
    return colour


def horn(tx, pal):
    p, n, box = tx.points, tx.normal, tx.box
    a, b = box.tag['along']
    s = ((p - box.center) @ box.axes[:, 1]) / box.size[1] + 0.5
    t = a + (b - a) * np.clip(s, 0, 1)
    grain = cells(p, tx.texel * 1.01, seed=99)
    v = 0.12 + 0.86 * t ** 0.9 + 0.06 * (grain - 0.5) + 0.05 * max(n[1], 0)
    if abs(n @ box.axes[:, 1]) > 0.5:                                   # the ledge where a spike steps in
        v = v - 0.22
    return shade(pal['horn'], v, tx.x, tx.y, dither=0.0)


def painter(tx, variant):
    pal = PALETTES[variant]
    mat = tx.box.mat
    if mat == 'shell':
        rgb = shell(tx, pal)
    elif mat == 'club':
        rgb = shell(tx, pal) if tx.box.tag.get('part') == 'club' else horn(tx, pal)
    elif mat == 'hide':
        rgb = hide(tx, pal)
    elif mat == 'head':
        rgb = head(tx, pal)
    elif mat == 'horn':
        rgb = horn(tx, pal)
    elif mat == 'beak':
        v = 0.62 + 0.2 * (cells(tx.points, tx.texel * 1.01, seed=100) - 0.5) + 0.15 * max(tx.normal[1], 0)
        rgb = shade(BEAK_RAMP, v, tx.x, tx.y, dither=0.0)
        if tx.box.tag.get('part') == 'lowbeak' and tx.normal[1] > 0.5:
            rgb = shade(MOUTH_RAMP, 0.4, tx.x, tx.y)
    elif mat == 'nail':
        p = tx.points
        rgb = hide(tx, pal)
        front = (p[..., 1] < 0.55) & (tx.normal[1] < 0.5)
        rgb = np.where(front[..., None], shade(NAIL_RAMP, 0.5 + 0.3 * (p[..., 1] / 0.55), tx.x, tx.y), rgb)
    else:
        rgb = shade(MOUTH_RAMP, 0.6, tx.x, tx.y)
    return rgb, None
