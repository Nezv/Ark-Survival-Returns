"""The Mammoth, designed box by box on the ARK rig (SK_Mammoth_new); built by scripts/handmade_models.py.

Proportions follow the original mesh in its bind pose (source units: +y up, the animal faces +z, its left is
+x): a high domed skull over a neck dip, the shoulder hump, a back sloping to a round rump, long hair hanging
from the flanks, columnar legs on round feet with three nails, a trunk of seven tapering rings that reaches
the ground and tusks that sweep down, out, forward and up with their tips turned in.
"""
import numpy as np

from handmade_models import cells, fbm, hexes, lift, noise, shade, spline, strands

FOLDER, STEM, HEIGHT = 'Mammoth', 'mammoth', 4.0
DENSITY = 1.0            # texels per runtime unit, Minecraft's own density
ATLAS_WIDTH = 512
PREVIEW_VARIANT = 'Natural'
ASSET = 'SK_Mammoth_new'          # the ARK mesh the design is fitted to (--preview --outline)
OUTLINE_SKIP = ('tusk',)      # its two tusk sets overlap; the outline leaves them out
PREVIEW_CLIPS = ('Mammoth-Idle', 'Mammoth-Move-Fwd', 'Mammoth-Attack-Tusk-Jab', 'Mammoth-Torpid')
HEAD = 'c_head'

# (bone, rear z, front z, half width, belly, back): the trunk of the body from the rump to the neck. Each
# segment overlaps the next a little so the spine can bend without opening the flank.
TORSO = [
    ('c_back1', -6.6, -2.9, 6.7, 9.0, 23.3),
    ('c_back2', -3.3, 1.3, 7.05, 8.4, 24.5),
    ('c_back3', 0.9, 5.7, 6.9, 8.9, 25.7),
    ('c_back4', 5.3, 11.0, 6.55, 9.9, 26.15),
    ('c_back5', 10.6, 15.0, 4.75, 11.9, 25.4),
]
# The tusk's centre line from the socket to the tip, with its half thickness (after the ARK mesh's smooth tusk).
TUSK = [((2.75, 18.1, 21.0), 1.25), ((3.55, 15.3, 22.4), 1.15), ((4.95, 13.6, 24.7), 1.0), ((5.85, 13.25, 27.3), 0.88),
        ((5.75, 14.35, 29.9), 0.75), ((4.75, 16.75, 31.8), 0.62), ((3.25, 19.15, 32.45), 0.48), ((2.2, 20.6, 32.0), 0.34)]
TRUNK = ['c_trunk1', 'c_trunk2', 'c_trunk3', 'c_trunk4', 'c_trunk5', 'c_trunk6', 'c_trunk7']
TRUNK_WIDTH = [4.3, 4.1, 3.75, 3.45, 3.1, 2.75, 2.5]


def design(d):
    rig = d.rig
    # ---------------------------------------------------------------- body
    for k, (bone, z0, z1, half, belly, back) in enumerate(TORSO):
        # three tiers round the back and a keel the belly: the cross-section narrows towards the spine
        d.span(bone, (-half, belly + 1.2, z0), (half, back - 2.6, z1), 'fur', part='body')
        d.span(bone, (-half + 0.85, belly + 0.45, z0 + 0.05), (half - 0.85, back - 0.95, z1 - 0.05), 'fur', part='body')
        d.span(bone, (-half + 2.3, back - 1.2, z0), (half - 2.3, back + (1.0 if bone == 'c_back4' else 0.7), z1),
               'fur', part='ridge')
        if bone == 'c_back5':
            continue
        d.span(bone, (-half + 1.6, belly - 0.55, z0 + 0.3), (half - 1.6, belly + 1.4, z1 - 0.3), 'fur', part='belly')
        # long hair hanging from the flanks: a fringe ring open at the bottom, longest between the legs
        drop = 1.9 if bone in ('c_back2', 'c_back3') else 1.5
        d.span(bone, (-half - 0.3, belly - drop, z0 - 0.15), (half + 0.3, belly + 3.4, z1 + 0.15), 'fur',
               part='skirt', hem=drop + 0.5, open=('down', 'north', 'south'), cutout=True)
    # the haunch slopes down to a round rump; its hair hangs over the thighs
    d.slab('c_back1', (23.0, -6.4), (19.0, -11.7), 4.8, 7.2, 'fur', part='body')
    d.slab('c_back1', (23.9, -6.0), (20.0, -11.0), 3.2, 1.6, 'fur', part='ridge')
    d.span('c_back1', (-5.7, 9.7, -12.35), (5.7, 17.4, -6.3), 'fur', part='body')
    d.span('c_back1', (-3.9, 11.0, -12.9), (3.9, 16.6, -11.8), 'fur', part='body')
    d.span('c_back1', (-6.05, 7.6, -12.55), (6.05, 13.2, -6.2), 'fur', part='skirt', hem=2.1,
           open=('down', 'up', 'south'), cutout=True)
    # the chest hair under the neck, between the front legs, and the neck under the skull
    d.span('c_back5', (-5.1, 8.9, 12.0), (5.1, 16.6, 13.2), 'fur', part='skirt', hem=2.6, open=('down', 'up', 'north'), cutout=True)
    d.span('c_back5', (-4.3, 11.4, 13.9), (4.3, 24.6, 16.0), 'fur', part='body')

    # ---------------------------------------------------------------- head
    d.span('c_head', (-4.05, 17.6, 14.1), (4.05, 26.9, 20.9), 'fur', part='head')            # skull
    d.span('c_head', (-3.05, 26.7, 14.7), (3.05, 28.45, 20.0), 'fur', part='crown')          # dome
    d.span('c_head', (-1.75, 28.3, 15.4), (1.75, 29.25, 18.3), 'fur', part='crown')          # its peak
    d.span('c_head', (-3.3, 18.9, 20.7), (3.3, 25.8, 21.9), 'fur', part='face')              # forehead plate
    d.span('c_head', (-3.75, 23.5, 19.9), (3.75, 24.5, 21.35), 'fur', part='brow')           # brow over the eyes
    d.span('c_head', (-3.55, 16.5, 17.8), (3.55, 21.0, 22.5), 'fur', part='cheek')           # tusk sockets
    d.span('c_head', (-4.45, 14.5, 14.4), (4.45, 18.9, 17.9), 'fur', part='skirt', hem=2.4,
           open=('down', 'up', 'south'), cutout=True)
    d.span('c_jaw', (-2.45, 15.05, 16.9), (2.45, 17.4, 20.7), 'skin', part='jaw')
    d.span('c_jaw', (-1.6, 14.6, 17.6), (1.6, 15.2, 20.2), 'skin', part='chin')
    d.span('c_tongue2', (-0.9, 16.6, 18.1), (0.9, 17.3, 19.9), 'mouth', part='tongue')
    d.span('l_earB1', (4.15, 19.7, 15.1), (4.8, 23.8, 16.9), 'fur', part='ear', mirror=True)
    d.span('l_earB2', (4.25, 20.0, 13.7), (4.95, 23.2, 15.2), 'fur', part='ear', mirror=True)

    # ---------------------------------------------------------------- trunk
    pivots = [rig.pivot(b) for b in TRUNK] + [rig.pivot('c_trunkUpperGrip') + (0, -0.7, 0.7)]
    for k, bone in enumerate(TRUNK):
        w = TRUNK_WIDTH[k]
        d.seg(bone, pivots[k], pivots[k + 1], w, w * 0.92, 'trunk', ext=(0.9 if k else 1.6, 0.45), part='trunk', ring=k)
    tip = rig.pivot('c_trunkUpperGrip')
    d.seg('c_trunkUpperGrip', tip + (0, 0.4, -0.3), tip + (0, -0.8, 1.1), 1.8, 1.0, 'skin', part='lip')
    low = rig.pivot('c_trunkLowerGrip')
    d.seg('c_trunkLowerGrip', low + (0, 0.5, 0.1), low + (0, -0.6, -0.4), 1.5, 0.8, 'skin', part='lip')

    # ---------------------------------------------------------------- tusks: a smooth curve in short boxes
    centre = spline([p for p, _ in TUSK], 2)
    radius = np.interp(np.linspace(0, len(TUSK) - 1, len(centre)), range(len(TUSK)), [r for _, r in TUSK])
    lengths = np.linalg.norm(np.diff(centre, axis=0), axis=1)
    total, run = lengths.sum(), 0.0
    for k in range(len(centre) - 1):
        r = (radius[k] + radius[k + 1]) / 2
        d.seg('l_tusk', centre[k], centre[k + 1], 2 * r, 2 * r, 'tusk', ext=(0.45 * r if k else 0.9, 0.45 * r),
              side=(0, 0, 1), mirror=True, part='tusk', along=(run / total, (run + lengths[k]) / total))
        run += lengths[k]

    # ---------------------------------------------------------------- legs
    def leg(upper, lower, heel, top, knee, ankle, foot, w_up, d_up, w_low, d_low):
        knee_p, ankle_p = rig.pivot(knee), rig.pivot(ankle)
        d.seg(upper, np.array(top), knee_p, w_up, d_up, 'fur', ext=(0, 0.6), mirror=True, part='leg')
        d.seg(lower, knee_p, ankle_p, w_low, d_low, 'fur', ext=(1.2, 0.3), mirror=True, part='shin')
        cx, cz = foot
        d.span(heel, (cx - 2.85, -0.25, cz - 2.85), (cx + 2.85, 2.3, cz + 2.85), 'foot', mirror=True, part='foot')

    leg('l_shoulder', 'l_elbow', 'l_wristHeel1', (4.0, 19.5, 10.2), 'l_elbow', 'l_wrist', (3.35, 9.75),
        5.5, 6.4, 5.3, 5.8)
    leg('l_hip', 'l_knee', 'l_ankleHell1', (3.3, 18.5, -6.0), 'l_knee', 'l_ankle', (3.35, -5.55),
        6.5, 7.6, 5.4, 6.0)

    # ---------------------------------------------------------------- tail: it hangs behind the rump
    tail = [np.array(p) for p in ((0.0, 17.4, -11.5), (0.0, 14.6, -12.75), (0.0, 12.2, -13.25), (0.0, 10.1, -13.4),
                                  (0.0, 8.4, -13.4))]
    for k, bone in enumerate(('c_tail1', 'c_tail2', 'c_tail3', 'c_tail4')):
        w = 1.9 - 0.2 * k
        d.seg(bone, tail[k], tail[k + 1], w, w, 'fur', ext=(0.4, 0.2), part='tail')
    d.seg('c_tail4', tail[3] + (0, -0.4, 0), tail[4] + (0, -1.5, -0.1), 2.3, 2.3, 'fur', part='tuft', hem=1.3,
          open=('down',), cutout=True)


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(fur=hexes('#24150e', '#341f14', '#4a2c1b', '#613b23', '#7a4c2d', '#946039', '#ab7546'),
                    skin=hexes('#2a221e', '#382e28', '#473b33', '#574940', '#68594e')),
    'Ivory': dict(fur=hexes('#5e564a', '#7a705f', '#958a75', '#ada28b', '#c3b9a1', '#d8cfb8', '#e9e2cf'),
                  skin=hexes('#3a332d', '#4b423a', '#5d5248', '#706457', '#857768')),
    'Darken': dict(fur=hexes('#0e0c0b', '#171412', '#201c19', '#2a2521', '#352f2a', '#423a33', '#51483f'),
                   skin=hexes('#1a1715', '#23201d', '#2d2925', '#38332e', '#443e38')),
    'Emerald': dict(fur=hexes('#0b150b', '#122012', '#1a2c19', '#233a20', '#2e4a29', '#3b5b33', '#4b6d3f'),
                    skin=hexes('#181d17', '#20271f', '#2a3228', '#343e31', '#40493b')),
    'Midnight': dict(fur=hexes('#0d131c', '#141d2a', '#1d2938', '#273647', '#334457', '#405468', '#4f657a'),
                     skin=hexes('#181d24', '#20262f', '#29303a', '#333b46', '#3e4753')),
    'Burgundy': dict(fur=hexes('#1e0a0c', '#2c1013', '#3d171b', '#4f1f23', '#63292c', '#783537', '#8d4442'),
                     skin=hexes('#231818', '#2e2120', '#3a2a29', '#463433', '#543f3d')),
}
# the darkest variants a shade lighter, so they still read under in-game light
PALETTES['Darken'] = lift(PALETTES['Darken'], 1.2, 5)
PALETTES['Emerald'] = lift(PALETTES['Emerald'], 1.15, 4)
PALETTES['Midnight'] = lift(PALETTES['Midnight'], 1.15, 4)
PALETTES['Burgundy'] = lift(PALETTES['Burgundy'], 1.15, 4)
TUSK_RAMP = hexes('#5a4a33', '#7d6a4b', '#9f8b66', '#bfab84', '#d8c8a2', '#e9dec0', '#f6f0de')
NAIL_RAMP = hexes('#39332c', '#544c42', '#70675a', '#8d8373', '#a89e8c')
MOUTH_RAMP = hexes('#2a1214', '#45201f', '#6a3532', '#8c4b45', '#a86259')
# Hair locks (texels across, layer length) by part: short on the face, long in the fringes.
LOCKS = {'skirt': (3, 9), 'tuft': (2, 6), 'head': (3, 4), 'crown': (3, 3), 'face': (2, 3), 'brow': (2, 2),
         'cheek': (2, 3), 'ear': (2, 3), 'leg': (3, 5), 'shin': (3, 5), 'foot': (3, 4), 'tail': (2, 3),
         'ridge': (3, 5), 'belly': (3, 6), 'body': (3, 7)}
EYE = np.array([3.4, 22.2, 19.6])


def hair_alpha(tx):
    """Jagged locks at the bottom of a hair fringe: each lock and strand ends at its own length."""
    depth = tx.box.tag.get('hem')
    if not depth or abs(tx.normal[1]) > 0.5:
        return None
    tone, _, lock = strands(tx, 3, 1000, seed=5)
    rise = tx.up_from_bottom() + 0.5
    reach = depth / tx.texel * (0.1 + 0.62 * lock + 0.28 * tone)
    return rise >= reach


def fur(tx, pal, lift=0.0):
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part', 'body')
    width, layer = LOCKS.get(part, (3, 7))
    tone, phase, lock = strands(tx, width, layer, seed=5)
    y = p[..., 1]
    v = 0.56 - 0.15 * np.clip((y - 9.0) / 18.0, 0, 1) + lift        # the back a shade darker than the flanks
    v += 0.17 * (tone - 0.5) + 0.12 * (lock - 0.5)                  # strands and locks
    v += 0.10 * (0.5 - phase)                                       # each layer lit at its root ...
    v -= 0.11 * (phase > 1 - 1.0 / layer)                           # ... and shaded under its tip
    v += 0.08 * (fbm(p, 8.0, seed=6, octaves=2) - 0.5)
    v += 0.05 * max(n[1], 0) - 0.16 * max(-n[1], 0)
    if part in ('skirt', 'tuft'):
        v -= 0.12 * np.clip(1.3 - tx.up_from_bottom() * tx.texel / max(tx.box.tag.get('hem', 2.0), 0.5), 0, 1)
        if n[1] > 0.5:
            v -= 0.22                                               # the shadowed edge of an upper layer of hair
    if part in ('head', 'face', 'cheek', 'brow', 'crown'):
        v -= 0.05
        eye = np.linalg.norm(np.stack([np.abs(p[..., 0]) - EYE[0], y - EYE[1], p[..., 2] - EYE[2]], -1), axis=-1)
        v -= 0.16 * np.clip(1.0 - eye / 1.9, 0, 1)                  # a darker ring round the eye
    if part == 'crown':
        v += 0.07                                                   # the sun-bleached top of the dome
    if part == 'face':
        v += 0.04
    if part in ('leg', 'shin'):
        v -= 0.04
    if part == 'ear':
        v -= 0.07
    return shade(pal['fur'], v, tx.x, tx.y, dither=0.0)


def trunk(tx, pal):
    p, n = tx.points, tx.normal
    box = tx.box
    ring = box.tag.get('ring', 0)
    along = (p - box.center) @ box.axes[:, 1] / tx.texel
    creases = (np.floor(along + 100) % 3 == 0) & (noise(p, 1.8 * tx.texel, seed=21) > 0.3)
    tone, phase, lock = strands(tx, 2, 3, seed=8, flow=box.axes[:, 1])
    v = 0.50 + 0.14 * (tone - 0.5) + 0.08 * (lock - 0.5) - 0.11 * creases - 0.025 * ring
    back = float(box.axes[:, 2] @ n)                                 # the side toward the mouth is barer and darker
    v -= 0.10 * max(-back, 0)
    colour = shade(pal['fur'], v, tx.x, tx.y, dither=0.0)
    if ring >= 5 and back < -0.5:
        colour = shade(pal['skin'], 0.55 + 0.25 * (tone - 0.5) - 0.2 * creases, tx.x, tx.y, dither=0.0)
    return colour


def tusk(tx):
    p, n = tx.points, tx.normal
    box = tx.box
    a, b = box.tag['along']
    s = ((p - box.center) @ box.axes[:, 1]) / box.size[1] + 0.5
    along = a + (b - a) * np.clip(s, 0, 1)
    rings = noise(np.stack([along * 40, np.zeros_like(along), np.zeros_like(along)], -1), 1.0, seed=31)
    v = 0.32 + 0.6 * along ** 0.75 + 0.10 * (rings - 0.5)
    v += 0.07 * max(n[1], 0) - 0.13 * max(-n[1], 0)
    v -= 0.20 * np.clip(1 - along / 0.10, 0, 1)                     # stained where it leaves the skull
    return shade(TUSK_RAMP, v, tx.x, tx.y, dither=0.35)


def foot(tx, pal):
    p, n = tx.points, tx.normal
    box = tx.box
    if n[1] < -0.5:                                                  # the sole: a cracked dark pad
        crack = cells(p, (1.5 * tx.texel, 1, 1.5 * tx.texel), seed=41)
        return shade(pal['skin'], 0.22 + 0.3 * (crack - 0.5), tx.x, tx.y)
    colour = fur(tx, pal, lift=-0.04)
    if n[1] > 0.5:
        return colour
    y = p[..., 1]
    c = box.center
    angle = np.degrees(np.arctan2(p[..., 0] - c[0], p[..., 2] - c[2]))
    skin = shade(pal['skin'], 0.35 + 0.2 * (noise(p, 1.2 * tx.texel, seed=42) - 0.5), tx.x, tx.y, dither=0.0)
    colour = np.where((y < 0.8)[..., None], skin, colour)
    nails = np.zeros(y.shape, bool)
    for centre in (-30.0, 0.0, 30.0):                                # three broad nails round the front
        nails |= (np.abs(angle - centre) < 9.5) & (y < 1.25)
    nail = shade(NAIL_RAMP, 0.25 + 0.5 * np.clip(y / 1.25, 0, 1), tx.x, tx.y, dither=0.0)
    return np.where(nails[..., None], nail, colour)


def painter(tx, variant):
    pal = PALETTES[variant]
    mat = tx.box.mat
    alpha = hair_alpha(tx)
    if mat == 'fur':
        rgb = fur(tx, pal)
    elif mat == 'trunk':
        rgb = trunk(tx, pal)
    elif mat == 'tusk':
        rgb = tusk(tx)
    elif mat == 'foot':
        rgb = foot(tx, pal)
    elif mat == 'mouth':
        rgb = shade(MOUTH_RAMP, 0.6 + 0.3 * (noise(tx.points, tx.texel, seed=51) - 0.5), tx.x, tx.y)
    else:  # bare skin: the lips, the jaw and the trunk's fingers
        p = tx.points
        v = 0.62 + 0.25 * (noise(p, 1.3 * tx.texel, seed=52) - 0.5) + 0.08 * max(tx.normal[1], 0)
        rgb = shade(pal['skin'], v, tx.x, tx.y, dither=0.3)
        if tx.box.tag.get('part') == 'jaw' and tx.normal[1] > 0.5:
            rgb = shade(MOUTH_RAMP, 0.45 + 0.3 * (noise(p, tx.texel, seed=53) - 0.5), tx.x, tx.y)
    return rgb, alpha
