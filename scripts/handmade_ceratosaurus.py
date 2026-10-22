"""The Ceratosaurus, designed box by box on the ARK rig; built by scripts/handmade_models.py.

A spiny mid-sized theropod in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x): a deep
trunk on long legs and three-toed feet, a long straight tail held level, a short neck and a deep skull with a blade
of a horn on the nose and a knob over each eye. The lower jaw and its dewlap are designed shut as the idle holds
them, the teeth are cut-out rows along both jaws, and the short arms end in small claws. A row of tall spikes runs
along the midline from the nape to the tail tip, and a row of spikes stands out of each side of the tail.
"""
import math

import numpy as np

from handmade_models import cells, fbm, hexes, lift, noise, shade
from handmade_tyrannosaurus import face_grid, grid, midline_top, scales, stripe_runs

FOLDER, STEM, HEIGHT = 'Ceratosaurus', 'ceratosaurus', 5.6
DENSITY = 1.0            # texels per runtime unit, Minecraft's own density
ATLAS_WIDTH = 512
PREVIEW_VARIANT = 'Natural'
ASSET = 'CeratosaurusAA_Mesh'
PREVIEW_CLIPS = ('Ceratosaurus_Idle', 'Ceratosaurus_MoveFWD', 'Ceratosaurus_Roar1', 'Cerato_Attack_Horn1')
JAW_POSE = ('Ceratosaurus_Idle', 0.0, ['Jaw', 'tongue', 'Dewlap'])     # the frame the jaw is designed in
HEAD, JAW = 'Head_M', 'Jaw_M'

# The trunk, segment by segment: (bone, rear z, front z, belly, back, half width) from the ARK mesh's sections, the
# spikes and osteoderms left out; the tiers across each follow the body's cross-section (TIER_SHAPE).
TRUNK = [('Waist_M', -7.9, -4.3, 10.0, 15.15, 2.1), ('Waist_M', -4.6, -0.9, 9.3, 15.55, 2.75),
         ('Waist_M', -1.2, 1.35, 8.3, 15.75, 3.05), ('Spine1_M', 1.05, 4.0, 8.1, 15.72, 2.75),
         ('Spine2_M', 3.7, 7.0, 8.5, 15.5, 2.8), ('Chest_M', 6.7, 8.6, 8.9, 15.05, 2.6)]
TIER_SHAPE = [(0.0, 0.1, 0.42), (0.1, 0.28, 0.75), (0.28, 0.78, 1.0), (0.78, 0.93, 0.7), (0.93, 1.0, 0.36)]
# The tail, a segment per bone: (bone, rear z, front z, underside, top, half width) at the segment's middle.
TAIL = [('Tail0_M', -12.23, -7.75, 10.26, 14.89, 1.83), ('Tail1_M', -16.47, -12.03, 10.62, 14.4, 1.48),
        ('Tail2_M', -20.68, -16.27, 11.05, 14.02, 1.2), ('Tail3_M', -24.81, -20.48, 11.48, 13.7, 0.9),
        ('Tail4_M', -28.5, -24.61, 11.81, 13.41, 0.68), ('Tail5_M', -32.3, -28.3, 12.15, 13.05, 0.4)]
# The short neck: per bone (rear z, front z, half width), laid along the top line of its back and the bottom line
# of its throat.
NECK = [('Neck0_M', 8.4, 9.1, 2.2), ('Neck1_M', 9.0, 10.1, 1.75), ('Neck3_M', 10.0, 11.35, 1.5),
        ('Neck5_M', 11.2, 12.7, 1.45)]
NECK_TOP = ([7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0], [15.2, 15.0, 14.75, 14.6, 14.7, 14.9, 15.0])
NECK_THROAT = ([7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0], [8.9, 9.4, 10.2, 10.85, 11.25, 11.55, 11.75])


def neck_section(d, bone, z0, z1, half, mat='hide'):
    """Four slabs along the neck between z0 and z1: a narrow crest on the top line, the upper and lower cores that
    meet in the middle, and a narrow keel along the throat, each sloped like its line."""
    top = lambda z: float(np.interp(z, *NECK_TOP))
    low = lambda z: float(np.interp(z, *NECK_THROAT))
    zm = (z0 + z1) / 2
    slope_top = math.atan2(top(z1) - top(z0), z1 - z0)
    slope_low = math.atan2(low(z1) - low(z0), z1 - z0)
    depth = (top(zm) - low(zm)) * math.cos((slope_top + slope_low) / 2)
    core = depth * 0.5 + 0.35
    a, b = z0 - 0.2, z1 + 0.2                        # the cores reach past the bends, so no wedge opens between them
    d.slab(bone, (top(z0), z0), (top(z1), z1), half * 0.6, 0.5, mat, part='neck', tier=2)
    d.slab(bone, (top(a) - 0.45, a), (top(b) - 0.45, b), half, core, mat, part='neck', tier=1)
    d.slab(bone, (low(a) + 0.45, a + 0.03), (low(b) + 0.45, b - 0.03), half * 0.93, core, mat, top=False,
           part='neck', tier=-1)
    d.slab(bone, (low(z0), z0 + 0.12), (low(z1), z1 - 0.12), half * 0.62, 0.5, mat, top=False, part='neck', tier=-2)


def tiers(d, bone, z0, z1, rows, mat='hide', part='trunk', inset=0.03):
    """Stacked boxes across a segment, each overlapping the next a little; the tiers off the core are inset a
    hair at both ends, so no two neighbours share an end plane."""
    core = max(range(len(rows)), key=lambda k: rows[k][2])
    for k, (y0, y1, half) in enumerate(rows):
        e = 0.0 if k == core else inset * (1 + abs(k - core)) + (0.015 if k > core else 0.0)
        lo = y0 - (0.04 if k > core else 0.0)
        hi = y1 + (0.04 if k < core else 0.0)
        d.span(bone, (-half, lo, z0 + e), (half, hi, z1 - e), mat, part=part, tier=k - core, rows=len(rows))


def bone_at(z):
    """The spine bone that carries the body at z."""
    for bone, start in (('Head_M', 12.07), ('Neck5_M', 11.3), ('Neck3_M', 10.04), ('Neck1_M', 9.0),
                        ('Neck0_M', 7.95), ('Chest_M', 6.97), ('Spine2_M', 3.92), ('Spine1_M', 1.25),
                        ('Waist_M', -7.91), ('Tail0_M', -12.23), ('Tail1_M', -16.47), ('Tail2_M', -20.68),
                        ('Tail3_M', -24.81), ('Tail4_M', -28.5)):
        if z >= start:
            return bone
    return 'Tail5_M'


def design(d):
    rig = d.rig
    P = rig.pivot
    # the jaw, its dewlap and the tongue are designed shut, as the idle holds them; the bind pose roars
    for bone in rig.bones:
        if bone == JAW or 'tongue' in bone or 'Dewlap' in bone:
            rig.design_as_in(bone, 'Ceratosaurus_Idle', 0.0, HEAD)

    # ---------------------------------------------------------------- trunk, tail and neck
    for bone, z0, z1, belly, back, half in TRUNK:
        rows = [(belly + (back - belly) * a, belly + (back - belly) * b, half * w) for a, b, w in TIER_SHAPE]
        tiers(d, bone, z0, z1, rows)
    for bone, z0, z1, low, high, half in TAIL:
        h = high - low
        rows = [(low, low + 0.2 * h, half * 0.65), (low + 0.2 * h, high - 0.2 * h, half), (high - 0.2 * h, high, half * 0.6)]
        tiers(d, bone, z0, z1 + 0.2, rows, part='tail', inset=0.05)
    for bone, z0, z1, half in NECK:
        neck_section(d, bone, z0, z1, half)

    # ---------------------------------------------------------------- head: deep, a horn on the nose, knobs over the eyes
    d.span(HEAD, (-1.48, 12.8, 12.3), (1.48, 15.1, 14.95), 'head', part='cranium')
    # the face round the eye: one flat side where the eye is set (build_creature_eyes footprint), a brow above it
    d.span(HEAD, (-1.05, 12.7, 14.9), (1.05, 14.95, 16.6), 'head', part='temple')
    d.span(HEAD, (-0.95, 14.9, 15.0), (0.95, 15.25, 16.45), 'head', part='brow')
    d.span(HEAD, (0.5, 15.1, 15.95), (0.9, 15.55, 16.55), 'horn', part='knob', mirror=True)
    # the snout, the nose horn on its top, the blunt tip
    d.span(HEAD, (-0.85, 12.65, 16.5), (0.85, 14.45, 17.6), 'head', part='snout')
    d.span(HEAD, (-0.55, 14.4, 16.55), (0.55, 14.75, 17.45), 'head', part='snout_top')
    d.span(HEAD, (-0.62, 12.6, 17.5), (0.62, 13.9, 18.5), 'head', part='tip')
    d.span(HEAD, (-0.4, 12.7, 18.45), (0.4, 13.4, 18.8), 'head', part='tip')
    d.seg(HEAD, (0.0, 14.3, 17.35), (0.0, 15.05, 17.72), 0.3, 0.78, 'horn', ext=(0.25, 0.05), side=(1, 0, 0),
          part='horn')
    d.seg(HEAD, (0.0, 15.0, 17.7), (0.0, 15.9, 17.98), 0.22, 0.44, 'horn', ext=(0.05, 0.0), side=(1, 0, 0),
          part='horn')
    # the upper teeth: cut-out rows hanging from the lip, outside the narrower lower jaw, and across the front
    teeth = dict(open=('up', 'down', 'north', 'south'), cutout=True, keep=True, mirror=True)
    d.span(HEAD, (1.3, 12.35, 12.9), (1.36, 12.9, 14.9), 'tooth', part='teeth', row='upper', **teeth)
    d.span(HEAD, (0.97, 12.3, 14.95), (1.02, 12.78, 16.5), 'tooth', part='teeth', row='upper', **teeth)
    d.span(HEAD, (0.7, 12.25, 16.5), (0.76, 12.72, 17.5), 'tooth', part='teeth', row='upper', **teeth)
    d.span(HEAD, (0.53, 12.22, 17.5), (0.58, 12.68, 18.35), 'tooth', part='teeth', row='upper', **teeth)
    d.span(HEAD, (-0.4, 12.25, 18.38), (0.4, 12.75, 18.44), 'tooth', part='teeth', row='front',
           open=('up', 'down', 'east', 'west'), cutout=True, keep=True)

    d.span(JAW, (-1.25, 10.6, 12.6), (1.25, 12.88, 15.0), 'head', part='jaw')
    d.span(JAW, (-0.93, 10.95, 14.9), (0.93, 12.82, 16.65), 'head', part='jaw')
    d.span(JAW, (-0.5, 11.5, 16.5), (0.5, 12.73, 18.32), 'head', part='chin')
    d.span(JAW, (1.08, 12.7, 13.0), (1.14, 13.15, 14.9), 'tooth', part='teeth', row='lower', **teeth)
    d.span(JAW, (0.78, 12.65, 15.0), (0.84, 13.1, 16.5), 'tooth', part='teeth', row='lower', **teeth)
    d.span('tongue2_M', (-0.45, 12.85, 13.5), (0.45, 13.0, 16.2), 'mouth', part='tongue')

    # ---------------------------------------------------------------- spikes: the midline row and the tail's side rows
    body = list(d.boxes)
    for z, h in SPIKES:
        y = midline_top(body, z)
        d.seg(bone_at(z), (0.0, y - 0.2, z + 0.12), (0.0, y + h, z - 0.22), 0.2 + 0.15 * h, 0.35 + 0.35 * h, 'scute',
              side=(1, 0, 0), part='spike', along=(0.0, 1.0))
    for k, z in enumerate(np.arange(-8.6, -30.0, -1.55)):
        bone, z0, z1, low, high, half = next(t for t in TAIL if t[1] <= z <= t[2] + 0.2)
        y = low + (high - low) * 0.62
        length = 0.25 + 0.5 * half
        side = 1 if k % 2 == 0 else -1
        a = np.array([side * (half - 0.15), y, z + 0.1])
        b = np.array([side * (half + length), y + 0.15, z - 0.25])
        d.seg(bone, a, b, 0.32, 0.22 + 0.12 * half, 'scute', side=(0, 1, 0), part='spike', along=(0.0, 1.0))
        d.seg(bone, a * [-1, 1, 1] + [0, 0, -0.75], b * [-1, 1, 1] + [0, 0, -0.75], 0.32, 0.22 + 0.12 * half, 'scute',
              side=(0, 1, 0), part='spike', along=(0.0, 1.0))

    # ---------------------------------------------------------------- arms: short, four small fingers
    elbow, wrist = P('Elbow_L'), P('Wrist_L')
    d.span('Shoulder_L', (1.6, 8.9, 7.7), (2.85, 11.0, 9.5), 'hide', mirror=True, part='shoulder')
    d.seg('Shoulder_L', (2.1, 10.0, 8.55), elbow, 1.0, 1.1, 'hide', ext=(0.0, 0.25), mirror=True, part='arm')
    d.seg('Elbow_L', elbow, wrist, 0.78, 0.8, 'hide', ext=(0.2, 0.15), mirror=True, part='forearm')
    d.seg('Wrist_L', wrist, P('MiddleFinger1_L'), 0.62, 0.75, 'hide', ext=(0.12, 0.05), mirror=True, part='hand')
    for finger in ('Index', 'Middle', 'Ring'):
        a, c = P(f'{finger}Finger1_L'), P(f'{finger}Finger4_L')
        tip = c + (c - a) * 0.6 + np.array([0.0, -0.12, 0.0])
        d.seg(f'{finger}Finger1_L', a, c, 0.24, 0.26, 'hide', ext=(0.05, 0.0), mirror=True, part='finger')
        d.seg(f'{finger}Finger4_L', c, tip, 0.15, 0.2, 'claw', ext=(0.03, 0.0), mirror=True, part='claw',
              along=(0.0, 1.0))

    # ---------------------------------------------------------------- legs
    d.span('Rump_L', (1.05, 8.0, -2.55), (3.85, 13.6, 2.55), 'hide', mirror=True, part='thigh')
    d.span('Rump_L', (1.4, 13.5, -2.0), (3.5, 14.6, 1.8), 'hide', mirror=True, part='thigh')
    d.seg('Hip_L', (2.37, 8.7, 0.35), (2.37, 4.3, -1.95), 2.35, 2.75, 'hide', ext=(0.0, 0.1), mirror=True, part='shin')
    d.seg('Knee_L', (2.37, 4.4, -2.2), (2.37, 0.62, -1.95), 1.85, 1.75, 'hide', ext=(0.0, 0.0), mirror=True,
          part='meta')
    d.span('Ankle_L', (1.4, 0.0, -2.85), (3.35, 0.78, -0.75), 'hide', mirror=True, part='foot')
    for chain, tip, w in ((('claw5_L', 'claw6_L', 'claw7_L', 'claw8_L'), (2.5, 0.1, 1.62), 0.68),
                          (('claw9_L', 'claw10_L', 'claw11_L', 'claw12_L'), (0.55, 0.1, 0.85), 0.6),
                          (('claw1_L', 'claw2_L', 'claw3_L', 'claw4_L'), (4.4, 0.1, 0.72), 0.6)):
        points = [P(b) for b in chain] + [np.array(tip)]
        for k, bone in enumerate(chain):
            a, b = points[k], points[k + 1]
            if k < len(chain) - 1:
                d.seg(bone, a, b, w * (1 - 0.1 * k), w * (1 - 0.1 * k), 'hide', ext=(0.12 if k == 0 else 0.04, 0.03),
                      mirror=True, part='toe')
            else:
                d.seg(bone, a, b, w * 0.45, w * 0.5, 'claw', ext=(0.04, 0.0), mirror=True, part='claw',
                      along=(0.0, 1.0))


# The midline spikes: (z, height above the skin), from the crown to the tail tip; tallest over the hips.
SPIKES = [(14.1, 0.5), (13.1, 0.85), (12.1, 1.1), (11.1, 1.25), (10.1, 1.3), (9.1, 1.25), (8.0, 1.2), (6.8, 1.15),
          (5.5, 1.1), (4.2, 1.15), (2.9, 1.2), (1.6, 1.25), (0.3, 1.25), (-1.0, 1.2), (-2.3, 1.15), (-3.6, 1.1)] + \
         [(round(float(z), 2), round(1.05 - 0.75 * (-4.9 - z) / 26.0, 2)) for z in np.arange(-4.9, -31.5, -1.3)]


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(hide=hexes('#1f2318', '#2b3021', '#383e2a', '#464d33', '#555c3d', '#646c47', '#747c52', '#858d5e'),
                    belly=hexes('#7b7a5e', '#8c8b6e', '#9d9c7f', '#adac90', '#bdbca1', '#cbcab1'),
                    stripe=hexes('#0f110b', '#161910', '#1e2115', '#26291b'),
                    scute=hexes('#3b3527', '#544b38', '#6e634a', '#8a7d5e', '#a69873'),
                    horn=hexes('#3a1810', '#552417', '#70321f', '#8b4229', '#a55535'),
                    mouth=hexes('#3a1414', '#55201f', '#71302c', '#8c423b', '#a5574c', '#bb6e5e')),
}
PALETTES['Natural'] = PALETTES['olive'] = lift(PALETTES['Natural'], 1.15, 4)
PALETTES['umber'] = dict(hide=hexes('#21170f', '#302217', '#402d1f', '#513928', '#634632', '#75543c', '#886347', '#9b7253'),
                         belly=hexes('#7a6650', '#8b7861', '#9c8a72', '#ad9c84', '#bdae96', '#ccbfa8'),
                         stripe=hexes('#0e0a07', '#150f0a', '#1c140e', '#241a12'),
                         scute=hexes('#16100b', '#211811', '#2c2017', '#38291e', '#453326'),
                         horn=hexes('#3a1a10', '#532618', '#6d3421', '#87432b', '#a15336'),
                         mouth=PALETTES['Natural']['mouth'])
PALETTES['rust'] = dict(hide=hexes('#2b140d', '#3d1e14', '#50291b', '#643523', '#79422b', '#8e5034', '#a45f3e', '#ba6e49'),
                        belly=hexes('#8c6650', '#9d7861', '#ae8a73', '#bf9c85', '#cfae97', '#ddc0a9'),
                        stripe=hexes('#140806', '#1d0c09', '#26110c', '#30160f'),
                        scute=hexes('#1c0d09', '#28140e', '#341b13', '#422318', '#512b1e'),
                        horn=hexes('#2a1a14', '#3d261d', '#513327', '#664132', '#7c503d'),
                        mouth=PALETTES['Natural']['mouth'])
PALETTES['charcoal'] = lift(dict(hide=hexes('#0f0e0d', '#161514', '#1e1c1a', '#262421', '#2f2c29', '#393531', '#433f3a', '#4e4943'),
                                 belly=hexes('#3e3832', '#4a433c', '#564f47', '#625a52', '#6e665d', '#7a7168'),
                                 stripe=hexes('#2a1410', '#371a14', '#442018', '#52271d'),
                                 scute=hexes('#1c1a18', '#272421', '#322e2a', '#3e3934', '#4b453f'),
                                 horn=hexes('#3a1810', '#552417', '#70321f', '#8b4229', '#a55535'),
                                 mouth=hexes('#2e1212', '#451b1a', '#5c2724', '#743530', '#8a463f', '#9e584f')), 1.25, 6)
TOOTH_RAMP = hexes('#8a7f66', '#ab9f83', '#c9bf9f', '#e0d8bc', '#efe9d5', '#faf7ec')
CLAW_RAMP = hexes('#100e0b', '#1d1914', '#2b251e', '#3b3329', '#4e4436', '#655845')
TONGUE_RAMP = hexes('#5e2526', '#7a3333', '#954442', '#ad5853', '#c26e66')

# The body's outline along its length (z), from the tail tip to the snout: the top of the back and the underside.
BACK = ([-32, -26, -20, -14, -10, -6, -2, 2, 6, 8, 10, 12, 14, 16, 17, 18, 18.8],
        [13.0, 13.45, 13.9, 14.45, 14.9, 15.25, 15.65, 15.75, 15.4, 15.0, 14.6, 14.9, 15.1, 14.75, 14.4, 13.8, 13.3])
UNDER = ([-32, -26, -20, -14, -10, -6, -2, 2, 6, 8, 10, 12, 14, 16, 17, 18, 18.8],
         [12.3, 11.8, 11.2, 10.65, 10.26, 9.95, 8.9, 8.05, 8.6, 9.4, 10.85, 10.7, 10.55, 10.9, 11.45, 11.6, 12.7])
# The dark bands over the back, as pixel runs: (z where a band crosses the spine, its width in texels there, how far
# down the side it reaches in units).
STRIPES = [(10.6, 2, 1.6), (8.6, 3, 2.4), (6.3, 3, 3.2), (3.9, 3, 3.6), (1.5, 4, 3.8), (-0.9, 4, 3.8), (-3.3, 3, 3.6),
           (-5.7, 3, 3.2), (-8.1, 3, 2.8), (-10.5, 3, 2.5), (-12.9, 3, 2.2), (-15.4, 3, 2.0), (-17.9, 2, 1.8),
           (-20.4, 2, 1.6), (-22.9, 2, 1.4), (-25.4, 2, 1.2), (-27.9, 2, 1.0), (-30.4, 2, 0.8)]
RUNS = [stripe_runs(131 + k, w, 200) for k, (_, w, _) in enumerate(STRIPES)]
EYE = (15.70, 14.39)       # (z, y): the eye, where the socket and the stripe through it are painted


def back_y(z):
    return np.interp(z, *BACK)


def under_y(z):
    return np.interp(z, *UNDER)


def stripes(tx):
    """The dark bands of STRIPES on a body face: across the back on top, then down the sides in their runs, counted
    on the face's own texels along its box (a sloped neck slab counts along its slope)."""
    p, n, box = tx.points, tx.normal, tx.box
    band = np.zeros(p.shape[:2], bool)
    axis = box.axes[:, 2]
    if abs(n @ axis) > 0.7 or n[1] < -0.5:
        return band
    count = max(1, int(round(box.size[2] / tx.texel)))
    local = (p - box.center) @ axis
    iz = np.clip(np.floor((local / box.size[2] + 0.5) * count).astype(int), 0, count - 1)
    rows = np.floor((back_y(p[..., 2]) - p[..., 1] + np.abs(p[..., 0]) * 0.35) / tx.texel).astype(int)
    for (z0, width, reach), runs in zip(STRIPES, RUNS):
        along = (np.array([0.0, back_y(z0), z0]) - box.center) @ axis
        col = int(np.floor((along / box.size[2] + 0.5) * count))
        if col < -40 or col > count + 40:
            continue
        limit = int(reach / tx.texel)
        for first, last, shift, w in runs:
            if first > limit:
                break
            w = max(1, min(w, int(w * (1.15 - first / max(limit, 1)))))
            c0 = col + shift - w // 2
            band |= (rows >= first) & (rows <= min(last, limit)) & (iz >= c0) & (iz < c0 + w)
    return band


def countershade(tx):
    """0 on the underside to 1 along the spine, from the body's outline at the texel's z."""
    p = tx.points
    top, bottom = back_y(p[..., 2]), under_y(p[..., 2])
    return np.clip((p[..., 1] - bottom) / np.maximum(top - bottom, 0.5), 0, 1)


def hide(tx, pal):
    """The body's skin: pebbled scales over a dark back that lightens down the flanks, the bands, and the pale
    underside with its transverse belly scales."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part', 'trunk')
    f = countershade(tx)
    mottle = fbm(p, 3.0, seed=3, octaves=2) - 0.5
    v = 0.8 - 0.46 * f ** 1.15 + 0.14 * mottle + scales(tx, 3, seed=5)
    v += 0.04 * max(n[1], 0)
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    band = stripes(tx)
    if np.any(band):
        dark = shade(pal['stripe'], 0.45 + 0.6 * mottle + 0.6 * scales(tx, 3, seed=5), tx.x, tx.y, dither=0.0)
        colour = np.where(band[..., None], dark, colour)
    pebble = scales(tx, 3, seed=7)
    edge = 0.3 + 0.06 * (noise(p, 0.8, seed=8) - 0.5) + (0.05 if part == 'neck' else 0.0) + 0.9 * pebble
    under = (f < edge) | ((n[1] < -0.5) & (part != 'thigh'))
    if np.any(under):
        belly = 0.55 + 0.1 * mottle + 0.8 * scales(tx, 3, seed=6, seam=0.08)
        if n[1] < -0.5:                                # transverse belly scutes across the underside
            iz, _ = grid(tx, 2)
            belly = 0.6 + 0.06 * mottle - 0.16 * (iz % 4 == 0) + 0.04 * (iz % 4 == 1)
        elif part == 'neck' or (abs(n[2]) > 0.7 and p[..., 2].mean() > 10):
            col, row = face_grid(tx)                   # loose folds across the throat and the chest
            wave = np.floor(2.2 * np.sin(col * 0.21 + tx.face.index))
            belly = belly - 0.14 * ((row + wave.astype(int)) % 5 == 0)
        pale = shade(pal['belly'], belly, tx.x, tx.y, dither=0.0)
        colour = np.where(under[..., None], pale, colour)
    return colour


def limb(tx, pal):
    """Legs, arms and toes: rings of scales round the limb, darker outside, paler inside and below."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    mottle = fbm(p, 2.5, seed=13, octaves=2) - 0.5
    col, row = face_grid(tx)
    ring = (row // 2) % 2
    v = 0.5 + 0.12 * mottle + scales(tx, 2 if part in ('toe', 'finger', 'hand') else 3, seed=14) - 0.03 * ring
    if part == 'thigh':
        v = 0.42 + 0.12 * mottle + scales(tx, 3, seed=14) - 0.12 * np.clip((p[..., 1] - 17) / 9, 0, 1)
    if abs(n[0]) > 0.5 and n[0] * np.sign(tx.box.center[0]) < 0:
        v += 0.1                                       # the inside of the limb is paler
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    if part in ('thigh', 'shoulder', 'arm'):
        band = stripes(tx)
        if np.any(band):
            colour = np.where(band[..., None], shade(pal['stripe'], 0.5 + 0.5 * mottle, tx.x, tx.y, dither=0.0),
                              colour)
    if part in ('toe', 'foot', 'meta', 'finger', 'hand') and n[1] > 0.5:
        k, _ = grid(tx, 2)                             # broad scutes across the top of the toes and the foot
        colour = np.where((k % 3 == 0)[..., None], shade(pal['hide'], v - 0.14, tx.x, tx.y, dither=0.0), colour)
    if n[1] < -0.5:
        colour = shade(pal['belly'], 0.4 + 0.08 * mottle, tx.x, tx.y, dither=0.0)
    return colour


def head(tx, pal):
    """The head: the hide's scales under a dark crown, a dark band back from the eye, a pale jaw and throat, the
    row of lip scales along both jaws, rugged knobs over the skull, the nostrils at the tip."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    mottle = fbm(p, 2.0, seed=23, octaves=2) - 0.5
    col, row = face_grid(tx)
    top = np.interp(z, [12.5, 14, 15, 16, 17, 18, 18.8], [15.1, 15.1, 14.95, 14.75, 14.4, 13.8, 13.3])
    bottom = np.interp(z, [12.5, 15, 16, 17, 18], [10.6, 10.6, 10.95, 11.45, 11.6])
    f = np.clip((y - bottom) / (top - bottom), 0, 1)
    v = 0.8 - 0.44 * f ** 1.1 + 0.12 * mottle + scales(tx, 3, seed=24) + 0.04 * max(n[1], 0)
    colour = shade(pal['hide'], v, tx.x, tx.y, dither=0.0)
    side = abs(n[0]) > 0.5
    jaw = tx.box.bone == JAW
    if side:
        # a dark band from the eye back along the temple, and the socket round the eye
        dz, dy = (z - EYE[0]) / tx.texel, (y - EYE[1]) / tx.texel
        back = np.clip(-dz, 0, 26)
        band = (dz < 3) & (dz > -26) & (np.abs(dy + back * 0.12) < 2.4 - back * 0.04)
        socket = (dz ** 2 / 36 + dy ** 2 / 20) < 1
        colour = np.where((band | socket)[..., None],
                          shade(pal['hide'], v - 0.22 + 0.4 * scales(tx, 3, seed=29), tx.x, tx.y, dither=0.0), colour)
        rim = ((dz ** 2 / 36 + dy ** 2 / 20) < 1.45) & ~socket & (dy > 0)
        colour = np.where(rim[..., None], shade(pal['hide'], v + 0.16, tx.x, tx.y, dither=0.0), colour)
        if part in ('snout', 'tip', 'cranium', 'temple') and not jaw:
            lip = row >= (tx.face.rect[3] - 2)         # the two rows of lip scales along the mouth
            k, _ = grid(tx, 2)
            light = (k // 2) % 2 == 0
            colour = np.where((lip & light)[..., None], shade(pal['belly'], 0.62 + 0.1 * mottle, tx.x, tx.y), colour)
            colour = np.where((lip & ~light)[..., None], shade(pal['hide'], 0.42, tx.x, tx.y), colour)
        if part == 'tip':                              # the nostril: a dark slit high on the side near the tip
            nostril = (np.abs(z - 18.3) < 0.15) & (np.abs(y - 13.35) < 0.09)
            colour = np.where(nostril[..., None], shade(pal['stripe'], 0.1, tx.x, tx.y), colour)
        if jaw:
            k, _ = grid(tx, 2)
            lip = row <= 1
            light = (k // 2) % 2 == 1
            colour = np.where((lip & light)[..., None], shade(pal['belly'], 0.58, tx.x, tx.y), colour)
            colour = np.where((lip & ~light)[..., None], shade(pal['hide'], 0.44, tx.x, tx.y), colour)
            pale = (row > 1) & (y < 11.9 + 0.3 * (noise(p, 0.7, seed=27) - 0.5))
            colour = np.where(pale[..., None], shade(pal['belly'], 0.52 + 0.08 * mottle + 0.5 * scales(tx, 3, seed=26),
                                                     tx.x, tx.y, dither=0.0), colour)
    if n[1] > 0.5 and part in ('snout_top', 'brow', 'cranium'):
        knob = (cells(p, tx.texel * 2.02, seed=28) > 0.72) & (scales(tx, 3, seed=24) > -0.02)
        colour = np.where(knob[..., None], shade(pal['hide'], v + 0.16, tx.x, tx.y), colour)
    if part == 'ridge':
        colour = shade(pal['horn'], 0.45 + 0.5 * mottle + 0.3 * max(n[1], 0) + scales(tx, 2, seed=31), tx.x, tx.y,
                       dither=0.0)
    if n[1] < -0.5:
        colour = shade(pal['belly'], 0.48 + 0.06 * mottle + scales(tx, 3, seed=25), tx.x, tx.y, dither=0.0) if jaw or part == 'cranium' else \
            shade(pal['mouth'], 0.35 + 0.1 * mottle, tx.x, tx.y)     # the palate
    if jaw and n[1] > 0.5:
        i, ci = grid(tx, 0)                            # the mouth floor inside a rim of lip scales
        k, ck = grid(tx, 2)
        e = np.minimum(np.minimum(i, ci - 1 - i), ck - 1 - k if part == 'chin' else 99)
        colour = np.where((e <= 1)[..., None], shade(pal['hide'], 0.56 + 0.1 * mottle, tx.x, tx.y),
                          shade(pal['mouth'], 0.3 + 0.1 * mottle, tx.x, tx.y))
    return colour


def horn(tx, pal):
    """The nose horn and the knobs over the eyes: red keratin, darker at the base, with grooves up them."""
    p, n = tx.points, tx.normal
    t = np.clip((p[..., 1] - 14.3) / 1.6, 0, 1)
    col, _ = face_grid(tx)
    v = 0.3 + 0.55 * t + 0.1 * (cells(p, tx.texel * 1.01, seed=33) - 0.5) - 0.12 * (col % 3 == 0) + 0.08 * max(n[1], 0)
    return shade(pal['horn'], v, tx.x, tx.y, dither=0.0)


def teeth(tx):
    """A row of pointed teeth as pixel art: each tooth a few texels wide, tapering to its tip, the rest clear."""
    box = tx.box
    p = tx.points
    along = 2 if box.tag['row'] != 'front' else 0
    a = (p[..., along] - (box.center[along] - box.size[along] / 2)) / tx.texel
    h = (p[..., 1] - (box.center[1] - box.size[1] / 2)) / tx.texel
    rows = box.size[1] / tx.texel
    width = 3.0
    k = np.floor(a / width)
    f = a / width - k
    zc = box.center[2] - box.size[2] / 2 + (k + 0.5) * width * tx.texel
    grow = np.interp(zc, [12.9, 14.0, 15.5, 17.0, 18.3], [0.6, 0.9, 1.0, 0.8, 0.6]) if along == 2 else 0.7
    length = rows * grow * (0.8 + 0.2 * cells(np.stack([k, np.zeros_like(k), np.full(k.shape, 7.0)], -1), 1.0, seed=5))
    root = (rows - h) if box.tag['row'] != 'lower' else h          # distance from the gum
    half = 0.5 * (1 - np.clip(root / np.maximum(length, 1e-3), 0, 1)) ** 0.8 + 0.08
    alpha = (np.abs(f - 0.5) <= half) & (root <= length)
    v = 0.85 - 0.45 * (root / np.maximum(length, 1)) ** 2
    v = np.where(root < 1, 0.35, v)                                 # stained at the gum
    return shade(TOOTH_RAMP, v, tx.x, tx.y, dither=0.0), alpha


def keratin(tx, ramp):
    """Claws and scutes: dark at the base, lighter to the tip."""
    box, n = tx.box, tx.normal
    if 'along' in box.tag:
        a, b = box.tag['along']
        s = ((tx.points - box.center) @ box.axes[:, 1]) / box.size[1] + 0.5
        t = a + (b - a) * np.clip(s, 0, 1)
    else:
        t = np.clip((tx.points[..., 1] - (box.center[1] - box.size[1] / 2)) / box.size[1], 0, 1)
    v = 0.25 + 0.55 * t + 0.12 * (cells(tx.points, tx.texel * 1.01, seed=41) - 0.5) + 0.06 * max(n[1], 0)
    return shade(ramp, v, tx.x, tx.y, dither=0.0)


def painter(tx, variant):
    pal = PALETTES[variant]
    mat = tx.box.mat
    part = tx.box.tag.get('part')
    if mat == 'tooth':
        return teeth(tx)
    if mat == 'hide':
        if part in ('trunk', 'tail', 'neck'):
            return hide(tx, pal), None
        return limb(tx, pal), None
    if mat == 'head':
        return head(tx, pal), None
    if mat == 'horn':
        return horn(tx, pal), None
    if mat == 'scute':
        return keratin(tx, pal['scute']), None
    if mat == 'claw':
        return keratin(tx, CLAW_RAMP), None
    return shade(TONGUE_RAMP, 0.55 + 0.25 * (cells(tx.points, tx.texel * 1.01, seed=43) - 0.5), tx.x, tx.y), None
