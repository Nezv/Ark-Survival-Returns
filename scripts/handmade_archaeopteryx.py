"""The Archaeopteryx, designed box by box on the ARK rig; built by scripts/handmade_models.py.

The little feathered dinosaur in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x), wings
spread: a compact body in feathered tiers on long legs, an S-neck of short segments thick with feathers, a narrow
toothed snout with the jaw designed shut as the idle holds it and rows of cut-out teeth along both jaws. Each wing
is its arm and three clawed fingers as tapering boxes, and the feathers traced from the ARK mesh's own wing
triangles as cut-out cards, one per feather bone (handmade_models.sheet), so they spread, beat and fold with the
clips; the long tail is a chain of small boxes fringed with its vanes, traced likewise on its nine bones. Painted
feather by feather (the Argentavis's painter): coverts over the arm, flight feathers with pale shafts and dark tips,
the tail's vanes barred, a bare dark snout and black claws.
"""
import numpy as np

from ark_geometry import Skeleton
from handmade_argentavis import TALON_RAMP, card
from handmade_megalodon import tiers
from handmade_models import ROOT, cells, fbm, hexes, lift, locks, separate, shade, sheet, unit
from handmade_terrorbird import feathers
from handmade_tyrannosaurus import face_grid

FOLDER, STEM, HEIGHT = 'Archaeopteryx', 'archaeopteryx', 0.5
DENSITY = 3.0            # texels per runtime unit: a small animal (0.84 runtime units per source unit)
FACE = 2.0               # the head and the teeth at FACE times that
ATLAS_WIDTH = 256
PREVIEW_VARIANT = 'Natural'
ASSET = 'Archaeopteryx'
PREVIEW_CLIPS = ('Archaeopteryx-Idle', 'Archaeopteryx-Fly', 'Archaeopteryx-Move-Fwd', 'Archaeopteryx-Glide')
JAW_POSE = ('Archaeopteryx-Idle', 0.0, ['Jaw', 'tongue'])     # the frame the jaw is designed in: the idle shuts it
HEAD, JAW = 'Head_Top', 'Head_Jaw'

# The body along z: (z, underside, top, half width), from the ARK mesh's sections without the wings and the legs.
BODY = [(-2.8, 5.0, 6.25, 0.7), (-2.5, 4.84, 6.31, 0.85), (-2.0, 4.1, 6.45, 1.08), (-1.5, 3.64, 6.53, 1.3),
        (-1.0, 3.42, 6.64, 1.45), (-0.5, 3.45, 6.73, 1.4), (0.0, 3.47, 6.76, 1.35), (0.5, 3.69, 6.82, 1.3),
        (1.0, 3.66, 6.88, 1.36), (1.5, 3.68, 6.95, 1.5), (1.9, 3.96, 6.95, 1.25), (2.4, 4.12, 6.84, 0.95),
        (2.9, 4.4, 6.72, 0.9), (3.4, 4.76, 6.64, 0.75)]
SHAPE = [(0.0, 0.14, 0.62), (0.14, 0.34, 0.9), (0.34, 0.7, 1.0), (0.7, 0.88, 0.85), (0.88, 1.0, 0.5)]
SLICES = [('Spine_base', -2.9, -1.5), ('Spine_base', -1.5, -0.09), ('Spine_01', -0.09, 0.66), ('Spine_02', 0.66, 1.41),
          ('Spine_03', 1.41, 2.16), ('Spine_Top', 2.16, 3.5)]
NECK = ['Neck_01', 'Neck_02', 'Neck_03', 'Neck_04', 'Neck_05', 'Neck_06', 'Neck_Top']
SKULL = [(3.8, 4.4, [(8.3, 9.25, 0.46)]),
         (4.4, 5.1, [(7.95, 8.8, 0.6), (8.8, 9.45, 0.5)]),
         (5.1, 5.7, [(7.95, 8.75, 0.58), (8.75, 9.3, 0.44)]),
         (5.7, 6.5, [(8.08, 8.2, 0.4), (8.2, 8.95, 0.47), (8.95, 9.15, 0.32)]),     # the eye on the middle tier
         (6.5, 6.95, [(8.13, 8.75, 0.3)]),
         (6.95, 7.44, [(8.15, 8.45, 0.17)])]
JAWS = [(5.2, 5.9, [(7.22, 8.08, 0.57)]), (5.9, 6.5, [(7.4, 8.1, 0.49)]), (6.5, 7.0, [(7.6, 8.03, 0.3)]),
        (7.0, 7.35, [(7.72, 7.95, 0.13)])]


def body_at(z):
    zs = [b[0] for b in BODY]
    return tuple(float(np.interp(z, zs, [b[k] for b in BODY])) for k in (1, 2, 3))


def limb(d, bone, a, b, w, depth, mat='coat', ext=(0.06, 0.04), **tag):
    d.seg(bone, a, b, w, depth, mat, ext=ext, side=(0, 1, 0) if abs(unit(b - a)[1]) < 0.9 else (1, 0, 0),
          mirror=True, **tag)


def design(d):
    rig = d.rig
    P = rig.pivot
    for bone in rig.bones:
        if bone == JAW or 'tongue' in bone:
            rig.design_as_in(bone, 'Archaeopteryx-Idle', 0.0, HEAD)
    sk = Skeleton(ASSET, ROOT / 'Creatures' / FOLDER / 'source')
    names = np.array(sk.names)[np.asarray(sk.face_bone)]
    centroid = sk.source_vertices[np.asarray(sk.triangles)].mean(1)

    # ---------------------------------------------------------------- the body and the feathered neck
    for n, (bone, a, b) in enumerate(SLICES):
        low, high, half = body_at((a + b) / 2)
        h = high - low
        rows = [(low + h * f0, low + h * f1, half * w) for f0, f1, w in SHAPE] if half > 1.0 else \
            [(low, low + h * 0.3, half * 0.8), (low + h * 0.3, low + h * 0.75, half), (low + h * 0.75, high, half * 0.6)]
        tiers(d, bone, a - 0.05, b + 0.05, rows, mat='coat', nudge=n % 5, unit=0.15)
    points = [P(b) for b in NECK] + [P(HEAD) + np.array([0.0, 0.0, 0.3])]
    for k, bone in enumerate(NECK):
        if np.linalg.norm(points[k + 1] - points[k]) < 0.1:
            continue
        d.seg(bone, points[k], points[k + 1], 1.3 - 0.04 * k, 1.8 - 0.06 * k, 'coat', ext=(0.25, 0.15),
              side=(1, 0, 0), part='neck')

    # ---------------------------------------------------------------- the head, the toothed jaws
    head = dict(density=FACE)
    for n, (z0, z1, rows) in enumerate(SKULL):
        tiers(d, HEAD, z0, z1, rows, mat='head', part='skull', nudge=n, unit=0.1, **head)
    for n, (z0, z1, rows) in enumerate(JAWS):
        tiers(d, JAW, z0, z1, rows, mat='snout', part='jaw', nudge=n, unit=0.1, **head)
    teeth = dict(open=('up', 'down', 'east', 'west'), cutout=True, keep=True, mirror=True, density=FACE, part='teeth')
    d.seg(HEAD, (0.6, 7.98, 5.3), (0.2, 8.05, 7.3), 0.3, 0.02, 'tooth', ext=(0.0, 0.0), side=(0, 1, 0), row='upper',
          **teeth)
    d.seg(JAW, (0.62, 8.02, 5.4), (0.17, 7.95, 7.2), 0.26, 0.02, 'tooth', ext=(0.0, 0.0), side=(0, 1, 0), row='lower',
          **teeth)

    # ---------------------------------------------------------------- the legs and the clawed feet
    limb(d, 'l_Leg_Hip', P('l_Leg_Hip'), P('l_Leg_Knee'), 1.35, 1.5, part='thigh')
    limb(d, 'l_Leg_Knee', P('l_Leg_Knee'), P('l_Leg_Ankle'), 0.85, 0.9, part='shin')
    limb(d, 'l_Leg_Ankle', P('l_Leg_Ankle'), P('l_Leg_Ball'), 0.32, 0.32, mat='leg', part='tarsus')
    limb(d, 'l_Leg_Ball', P('l_Leg_Ball'), P('l_Leg_Toe'), 0.36, 0.26, mat='leg', part='foot')
    for toe in ('InnerToe', 'MiddleToe', 'OuterToe'):
        a, b, c = (P(f'l_{toe}_01_0{k}') for k in (1, 2, 3))
        limb(d, f'l_{toe}_01_01', a, b, 0.16, 0.15, mat='leg', ext=(0.02, 0.02), part='toe')
        limb(d, f'l_{toe}_01_02', b, c + unit(c - b) * 0.15, 0.12, 0.13, mat='talon', ext=(0.01, 0.0), part='claw')
    a, b = P('l_HindToe_01_01'), P('l_HindToe_01_02')
    limb(d, 'l_HindToe_01_01', a, b + unit(b - a) * 0.12, 0.12, 0.12, mat='talon', ext=(0.01, 0.0), part='claw')

    # ---------------------------------------------------------------- the arms and the clawed fingers
    for bone, a, b, w in (('l_Wing_Shoulder', 'l_Wing_Shoulder', 'l_Wing_Elbow', 1.0),
                          ('l_Wing_Elbow', 'l_Wing_Elbow', 'l_Wing_Wrist', 0.8),
                          ('l_Wing_Wrist', 'l_Wing_Wrist', 'l_Hand_02_01', 0.6)):
        limb(d, bone, P(a), P(b), w, w * 0.65, ext=(0.12, 0.05), part='arm')
    for f in ('01', '02', '03'):
        a, b = P(f'l_Hand_{f}_01'), P(f'l_Hand_{f}_02')
        limb(d, f'l_Hand_{f}_01', a, b, 0.16, 0.16, mat='leg', ext=(0.02, 0.02), part='finger')
        limb(d, f'l_Hand_{f}_02', b, b + unit(b - a) * 0.45 + np.array([0, -0.1, 0]), 0.11, 0.14, mat='talon',
             ext=(0.01, 0.0), part='claw')

    # ---------------------------------------------------------------- the feathers and the tail, traced
    for side in ('l', 'r'):
        bones = [f'{side}_Wing_Shoulder', f'{side}_wing_upper_base'] + \
            [f'{side}_wing_feather__{k}' for k in (1, 2, 3, 4)] + [f'{side}_Wing_Elbow', f'{side}_wing_elbow_base'] + \
            [f'{side}_wing_feather__{k}' for k in range(5, 14)] + [f'{side}_Wing_Wrist', f'{side}_wing_hand_base'] + \
            [f'{side}_wing_feather__{k}' for k in (14, 15, 16, 17)] + \
            [f'{side}_Hand_02_01', f'{side}_Hand_02_02', f'{side}_wing_indexfinger_base',
             f'{side}_wing_feather__18', f'{side}_wing_feather__19']
        bones = [b for b in bones if b in sk.names]
        chosen = np.isin(names, bones) & (np.abs(centroid[:, 0]) > 1.2)
        sheet(d, sk, chosen, bones, mat='wing', res=0.06, fin='wing', step=0.024, side=1.0 if side == 'l' else -1.0,
              span0=1.2, span1=9.5, sweep0=5.0, sweep1=5.5)
    tail = [f'Tail_01_0{k}' for k in range(1, 9)]
    for k, bone in enumerate(tail):
        nxt = P(tail[k + 1]) if k + 1 < len(tail) else P('Tail_01_09') + np.array([0.0, 0.0, -1.0])
        d.seg(bone, P(bone), nxt, 0.55 - 0.04 * k, 0.6 - 0.04 * k, 'coat', ext=(0.08, 0.04), side=(1, 0, 0),
              part='tail')
    chosen = np.isin(names, tail) & (np.abs(centroid[:, 0]) > 0.3)
    sheet(d, sk, chosen, tail, mat='tail', res=0.06, fin='tail', step=0.024)
    separate(d, DENSITY)


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(coat=hexes('#0e0e10', '#151518', '#1d1d21', '#26262b', '#303036', '#3b3b42', '#47474f', '#54545c'),
                    pale=hexes('#5c5c64', '#707078', '#85858c', '#9a9aa0', '#aeaeb3', '#c2c2c6'),
                    flight=hexes('#14161c', '#1c1f27', '#252933', '#2f3440', '#3a404e', '#464d5d', '#535b6c'),
                    panel=hexes('#2c3440', '#36404e', '#414c5c', '#4d596a', '#5a6778'),
                    leg=hexes('#2a2622', '#3a352f', '#4b453d', '#5d564c', '#70685c'),
                    skin=hexes('#3a3230', '#4c423e', '#5e544e', '#72665e', '#867870')),
}
PALETTES['black'] = PALETTES['Natural']
PALETTES['umber'] = dict(coat=hexes('#1e140c', '#281b10', '#332315', '#3f2c1b', '#4c3621', '#5a4028', '#684b30', '#775739'),
                         pale=hexes('#8a7258', '#a08668', '#b49a7a', '#c6ae8e', '#d6c2a4', '#e4d4bc'),
                         flight=hexes('#160e08', '#1e140b', '#271a0f', '#312114', '#3c291a', '#483220', '#553c27'),
                         panel=hexes('#6a4e34', '#7e5e40', '#926f4e', '#a6815e', '#ba9470'),
                         leg=PALETTES['Natural']['leg'], skin=hexes('#4a3a30', '#5c4a3e', '#6e5a4c', '#826c5c', '#967e6c'))
PALETTES['slate'] = dict(coat=hexes('#181c22', '#20252c', '#293037', '#333b43', '#3e4750', '#4a545e', '#57626d', '#64717c'),
                         pale=hexes('#7a8690', '#8e9aa4', '#a2adb6', '#b5bfc7', '#c7d0d6', '#d8dfe4'),
                         flight=hexes('#101318', '#171b21', '#1f242b', '#282e36', '#323942', '#3d454f', '#49525d'),
                         panel=hexes('#4e6a86', '#5a7896', '#6886a4', '#7894b2', '#88a2c0'),
                         leg=PALETTES['Natural']['leg'], skin=PALETTES['Natural']['skin'])
PALETTES['rust'] = dict(coat=hexes('#2a120a', '#38180d', '#471f11', '#572716', '#68301b', '#7a3a21', '#8c4428', '#9f4f30'),
                        pale=hexes('#a48064', '#b69276', '#c6a488', '#d4b69c', '#e0c8b0', '#ebd8c4'),
                        flight=hexes('#1c0c07', '#26110a', '#31170e', '#3d1d12', '#4a2417', '#582c1c', '#673422'),
                        panel=hexes('#c06a2a', '#d07c36', '#de8e44', '#eaa056', '#f2b26a'),
                        leg=PALETTES['Natural']['leg'], skin=PALETTES['Natural']['skin'])
TOOTH_RAMP = hexes('#8f8a7c', '#b3ad9c', '#d2ccba', '#e6e1d2', '#f3f0e6', '#fbfaf4')


def countershade(p):
    z, y = p[..., 2], p[..., 1]
    low = np.interp(z, [b[0] for b in BODY], [b[1] for b in BODY])
    high = np.interp(z, [b[0] for b in BODY], [b[2] for b in BODY])
    return np.clip((y - low) / np.maximum(high - low, 0.3), 0, 1)


def coat(tx, pal):
    """Body feathers: rows of rounded feathers, paler underneath; the tail's core barred."""
    p, n = tx.points, tx.normal
    f = countershade(p)
    base = 0.5 + 0.2 * (1 - f) * (n[1] < 0.5) + 0.08 * (fbm(p, 1.0, seed=311, octaves=2) - 0.5)
    colour = feathers(tx, base, pal, width=2, height=2, seed=312)
    if tx.box.tag.get('part') == 'tail':
        bar = (np.floor(p[..., 2] / 1.3) % 2 == 0)
        colour = np.where(bar[..., None], colour * 0.7, colour)
    if (n[1] < -0.5) or tx.box.tag.get('part') == 'neck' and n[1] < 0:
        colour = feathers(tx, 0.55, dict(coat=pal['pale']), width=2, height=2, seed=313)
    return colour


def head(tx, pal):
    """The head: small feathers over the crown and the nape, bare dark skin over the snout."""
    p, n = tx.points, tx.normal
    a, b, value = locks(tx, 2, 2, seed=314)
    colour = shade(pal['coat'], 0.5 + 0.12 * (value - 0.5) - 0.12 * (b > 0.75), tx.x, tx.y, dither=0.0)
    bare = p[..., 2] > 5.7
    colour = np.where(bare[..., None], shade(pal['skin'], 0.45 + 0.1 * (value - 0.5), tx.x, tx.y, dither=0.0), colour)
    if n[1] < -0.5:
        colour = shade(pal['skin'], 0.3, tx.x, tx.y, dither=0.0)
    return colour


def snout(tx, pal):
    p, n = tx.points, tx.normal
    colour = shade(pal['skin'], 0.4 + 0.1 * (cells(p, tx.texel, seed=315) - 0.5), tx.x, tx.y, dither=0.0)
    if n[1] > 0.5:
        colour = shade(hexes('#3e1a1c', '#5a2628', '#763536'), 0.5, tx.x, tx.y, dither=0.0)
    return colour


def teeth(tx):
    """Small sharp teeth every second texel along the card, white on the gum."""
    box = tx.box
    local = (tx.points - box.center) @ box.axes
    along = (local[..., 1] + box.size[1] / 2) / tx.texel
    rows = box.size[0] / tx.texel
    h = (local[..., 0] + box.size[0] / 2) / tx.texel
    if box.tag['row'] == 'upper':
        h = rows - h
    phase = np.abs(((along + (0.0 if box.tag['row'] == 'upper' else 1.0)) % 2.0) / 2.0 - 0.5) * 2
    alpha = h <= (rows - 0.2) * (1 - phase) + 0.25
    return shade(TOOTH_RAMP, 0.95 - 0.3 * (h < 1.0), tx.x, tx.y, dither=0.0), alpha


def painter(tx, variant):
    pal = PALETTES[variant]
    mat = tx.box.mat
    if mat in ('wing', 'tail'):
        return card(tx, pal)
    if mat == 'tooth':
        return teeth(tx)
    if mat == 'head':
        return head(tx, pal), None
    if mat == 'snout':
        return snout(tx, pal), None
    if mat == 'leg':
        col, row = face_grid(tx)
        return shade(pal['leg'], 0.55 - 0.15 * (row % 2 == 0), tx.x, tx.y, dither=0.0), None
    if mat == 'talon':
        return shade(TALON_RAMP, 0.4 + 0.3 * (cells(tx.points, tx.texel, seed=316) - 0.5), tx.x, tx.y), None
    return coat(tx, pal), None
