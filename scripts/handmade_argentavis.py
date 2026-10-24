"""The Argentavis, designed box by box on the ARK rig; built by scripts/handmade_models.py.

The giant teratorn in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x), wings spread:
a deep, egg-shaped body in tiers under a short neck lost in a shaggy ruff, an eagle's head with a heavy hooked beak
and a spiky crest of feathers (a cut-out card traced from the ARK mesh); the jaw is designed shut as the ground idle
holds it. Each wing is its arm as tapering boxes and the feathers traced from the ARK mesh's own wing triangles as
cut-out cards, one per bone that carries them (handmade_models.sheet), so they spread, beat and fold along the body
with the clips; the tail fan likewise on its feather bones. The legs are feathered to the knee and end in scaly toes
with great black talons. Painted feather by feather: rows of coverts on the arm, long flight feathers with a pale
shaft and dark tips fanning to the wing's ragged edge, a pale ruff round the neck, horn beak with a dark hook.
"""
import numpy as np

from ark_geometry import Skeleton
from handmade_megalodon import tiers
from handmade_models import ROOT, cells, fbm, hexes, lift, separate, shade, sheet, unit
from handmade_terrorbird import feathers
from handmade_tyrannosaurus import face_grid

FOLDER, STEM, HEIGHT = 'Argentavis', 'argentavis', 3.2
DENSITY = 1.0            # texels per runtime unit (6.3 runtime units per source unit)
FACE = 1.5               # the head and the beak at FACE times that
ATLAS_WIDTH = 512
PREVIEW_VARIANT = 'Natural'
ASSET = 'Argentavis'
PREVIEW_CLIPS = ('Argentavis-Ground-Idle', 'Argentavis-Fly-Fwd', 'Argentavis-Ground-Move-Fwd', 'Argentavis-Fly-Attack-Claw')
JAW_POSE = ('Argentavis-Ground-Idle', 0.0, ['Jaw', 'Tongue'])     # the frame the jaw is designed in: the idle shuts it
HEAD, JAW = 'c_Head', 'c_Jaw'

# The body along z: (z, underside, top, half width), from the ARK mesh's sections without the wings and the legs.
BODY = [(-3.6, 4.1, 5.5, 0.45), (-3.2, 3.9, 5.6, 0.6), (-2.7, 3.7, 5.7, 0.9), (-2.2, 3.3, 5.7, 1.0),
        (-1.7, 2.9, 5.8, 1.15), (-1.2, 2.65, 6.05, 1.25), (-0.7, 2.7, 6.05, 1.3), (-0.2, 2.7, 6.15, 1.35),
        (0.3, 2.66, 6.25, 1.4), (0.8, 2.7, 6.28, 1.45), (1.3, 2.85, 6.2, 1.45), (1.8, 3.1, 6.35, 1.4),
        (2.3, 3.35, 6.6, 1.28), (2.8, 3.9, 6.6, 1.0)]
SHAPE = [(0.0, 0.14, 0.6), (0.14, 0.34, 0.9), (0.34, 0.7, 1.0), (0.7, 0.88, 0.85), (0.88, 1.0, 0.5)]
SLICES = [('c_Tail_Top', -3.7, -2.6), ('c_Hip', -2.6, -1.3), ('c_Hip', -1.3, -0.06), ('c_Spine_01', -0.06, 0.65),
          ('c_Spine_02', 0.65, 1.35), ('c_Spine_Top', 1.35, 2.5)]
NECK = [('c_Neck_01', (4.95, 1.75), (5.6, 2.65), 2.2, 2.1), ('c_Neck_02', (5.45, 2.35), (6.25, 3.3), 1.9, 1.9)]
SKULL = [(1.9, 2.6, [(6.2, 7.3, 0.85)]),
         (2.6, 3.3, [(5.9, 7.2, 0.92), (7.2, 7.55, 0.66)]),
         (3.3, 3.95, [(5.95, 7.0, 0.8), (7.0, 7.48, 0.6)]),
         (3.95, 4.6, [(6.37, 6.55, 0.47), (6.55, 7.1, 0.53), (7.1, 7.5, 0.4)])]      # the eye on the middle tier
BEAK = [(4.6, 5.0, 6.37, 7.15, 0.32), (5.0, 5.32, 6.32, 6.92, 0.21)]
CULMEN = [((7.5, 4.25), (6.8, 5.38), 0.3, 0.55)]
JAWS = [(3.3, 3.95, [(5.25, 6.36, 0.75)]), (3.95, 4.6, [(5.7, 6.38, 0.5)]), (4.6, 5.3, [(6.05, 6.4, 0.25)])]
TOES = ['InnerToe_01', 'InnerToe_02', 'InnerToe_03', 'HindToe_01']


def body_at(z):
    zs = [b[0] for b in BODY]
    return tuple(float(np.interp(z, zs, [b[k] for b in BODY])) for k in (1, 2, 3))


def limb(d, bone, a, b, w, depth, mat='coat', ext=(0.04, 0.03), **tag):
    d.seg(bone, a, b, w, depth, mat, ext=ext, side=(0, 1, 0) if abs(unit(b - a)[1]) < 0.9 else (1, 0, 0),
          mirror=True, **tag)


def design(d):
    rig = d.rig
    P = rig.pivot
    for bone in rig.bones:
        if bone == JAW or 'Tongue' in bone:
            rig.design_as_in(bone, 'Argentavis-Ground-Idle', 0.0, HEAD)
    sk = Skeleton(ASSET, ROOT / 'Creatures' / FOLDER / 'source')
    names = np.array(sk.names)[np.asarray(sk.face_bone)]
    centroid = sk.source_vertices[np.asarray(sk.triangles)].mean(1)

    # ---------------------------------------------------------------- the body, the neck and its ruff
    for n, (bone, a, b) in enumerate(SLICES):
        low, high, half = body_at((a + b) / 2)
        h = high - low
        rows = [(low + h * f0, low + h * f1, half * w) for f0, f1, w in SHAPE] if half > 0.8 else [(low, high, half)]
        tiers(d, bone, a - 0.05, b + 0.05, rows, mat='coat', nudge=n % 5, unit=0.12)
    for bone, (y0, z0), (y1, z1), w, depth in NECK:
        a, b = np.array([0.0, y0, z0]), np.array([0.0, y1, z1])
        d.seg(bone, a, b, w, depth * 0.75, 'ruff', ext=(0.1, 0.05), side=(1, 0, 0), part='neck')
        d.seg(bone, a, b, w * 0.75, depth, 'ruff', ext=(0.06, 0.03), side=(1, 0, 0), part='neck')

    # ---------------------------------------------------------------- the head, the hooked beak and the crest
    head = dict(density=FACE)
    for n, (z0, z1, rows) in enumerate(SKULL):
        tiers(d, HEAD, z0, z1, rows, mat='head', part='skull', nudge=n, unit=0.1, **head)
    for k, (z0, z1, y0, y1, half) in enumerate(BEAK):
        d.span(HEAD, (-half, y0, z0), (half, y1, z1 + 0.01 * k), 'beak', part='beak', **head)
    for back, front, half, thick in CULMEN:
        d.slab(HEAD, back, front, half, thick, 'beak', part='culmen', **head)
    d.span(HEAD, (-0.13, 6.05, 5.3), (0.13, 6.82, 5.55), 'beak', part='hook', **head)          # the hook's point
    for n, (z0, z1, rows) in enumerate(JAWS):
        tiers(d, JAW, z0, z1, rows, mat='beak', part='jaw', nudge=n, unit=0.1, **head)
    d.span(HEAD, (0.4, 7.06, 3.9), (0.6, 7.26, 4.55), 'brow', mirror=True, part='brow', **head)     # the glare
    crest = (names == HEAD) & (np.abs(centroid[:, 0]) < 0.8) & (centroid[:, 1] > 7.2) & (centroid[:, 2] < 3.9)
    sheet(d, sk, crest, [HEAD], mat='crest', res=0.05, fin='crest', thickness=0.05)

    # ---------------------------------------------------------------- the legs: feathered thighs, scaly toes, talons
    limb(d, 'l_Leg_Hip', P('l_Leg_Hip'), P('l_Leg_Knee1'), 1.25, 1.2, part='thigh')
    limb(d, 'l_Leg_Knee1', P('l_Leg_Knee1'), P('l_Leg_Knee2'), 0.8, 0.75, part='trouser')
    limb(d, 'l_Leg_Knee2', P('l_Leg_Knee2'), P('l_Leg_Ball'), 0.38, 0.38, mat='leg', part='tarsus')
    limb(d, 'l_Leg_Ball', P('l_Leg_Ball'), P('l_Leg_Toe'), 0.5, 0.36, mat='leg', part='foot')
    for toe in TOES:
        pts = [P(f'l_{toe}_0{k}') for k in (1, 2, 3, 4)]
        limb(d, f'l_{toe}_01', pts[0], pts[1], 0.2, 0.18, mat='leg', ext=(0.03, 0.02), part='toe')
        limb(d, f'l_{toe}_02', pts[1], pts[2], 0.18, 0.17, mat='leg', ext=(0.02, 0.02), part='toe')
        tip = pts[3] + unit(pts[3] - pts[2]) * 0.12 + np.array([0.0, -0.1, 0.0])
        limb(d, f'l_{toe}_03', pts[2], tip, 0.12, 0.15, mat='talon', ext=(0.02, 0.0), part='talon')

    # ---------------------------------------------------------------- the arms under the coverts
    arm = ['l_Wing_Shoulder', 'l_Wing_Elbow1', 'l_Wing_Elbow2', 'l_Wing_Wrist', 'l_Digit_01_01', 'l_Digit_01_02']
    pts = [P(b) for b in arm] + [P('l_Digit_01_02') + unit(P('l_Digit_01_02') - P('l_Digit_01_01')) * 0.6]
    for k, bone in enumerate(arm):
        limb(d, bone, pts[k], pts[k + 1], 1.3 - 0.16 * k, 0.75 - 0.08 * k, ext=(0.12, 0.06), part='arm')

    # ---------------------------------------------------------------- the feathers, traced
    for side in ('l', 'r'):
        bones = [f'{side}_{b}' for b in ('Wing_Shoulder', 'wing_feather_1', 'wing_feather_1_2', 'wing_feather_2',
                                         'wing_feather_2_2', 'wing_feather_3', 'wing_feather_3_2', 'Wing_Elbow1',
                                         'wing_feather_4', 'wing_feather_4_2', 'Wing_Elbow2', 'wing_feather_5',
                                         'wing_feather_5_2', 'Wing_Wrist', 'Alula_01_01', 'Alula_01_02',
                                         'Alula_01_03', 'Digit_01_01', 'Digit_01_02', 'wing_feather_6',
                                         'wing_feather_6_2', 'wing_feather_7', 'wing_feather_7_2')]
        bones = [b for b in bones if b in sk.names]
        chosen = np.isin(names, bones) & (np.abs(centroid[:, 0]) > 1.0)
        sheet(d, sk, chosen, bones, mat='wing', res=0.04, fin='wing', step=0.024, side=1.0 if side == 'l' else -1.0)
    tail = ['c_Tail_Top', 'l_Tailfeather_01_01', 'l_Tailfeather_02_01', 'r_Tailfeather_01_01', 'r_Tailfeather_02_01']
    chosen = np.isin(names, tail) & (centroid[:, 2] < -2.6)
    sheet(d, sk, chosen, [b for b in tail if b in sk.names], mat='tail', res=0.04, fin='tail', step=0.024)
    separate(d, DENSITY)


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(coat=hexes('#121010', '#1a1716', '#231f1d', '#2d2825', '#38312d', '#443b36', '#51463f', '#5e5149'),
                    pale=hexes('#8a857e', '#a29c94', '#b8b2a9', '#cbc6bd', '#dcd8d0', '#ebe8e2'),
                    flight=hexes('#0c0b0b', '#141212', '#1d1a19', '#272321', '#322d2a', '#3e3834', '#4c443f'),
                    panel=hexes('#6a6560', '#848079', '#9e9a93', '#b8b4ad', '#cfccc6'),
                    beak=hexes('#4e463a', '#6c6150', '#8a7d68', '#a89a82', '#c4b79e', '#dcd2bb'),
                    leg=hexes('#3e3a34', '#544e46', '#6b6459', '#827a6d', '#999082'),
                    skin=hexes('#5a2a24', '#743630', '#8e443c', '#a85448', '#c06656')),
}
PALETTES['black'] = PALETTES['Natural']
PALETTES['umber'] = dict(coat=hexes('#1c120b', '#26180f', '#311f14', '#3d271a', '#4a3020', '#583a27', '#66442e', '#754f36'),
                         pale=hexes('#8a6e4c', '#a2845e', '#b89a72', '#ccb088', '#dcc4a0', '#ead8ba'),
                         flight=hexes('#140d08', '#1c130c', '#251911', '#2f2016', '#3a281c', '#463122', '#533a29'),
                         panel=hexes('#5a4634', '#715843', '#886b53', '#9f7f64', '#b69476'),
                         beak=PALETTES['Natural']['beak'], leg=hexes('#5a4a28', '#746034', '#8e7642', '#a88c52', '#c2a464'),
                         skin=PALETTES['Natural']['skin'])
PALETTES['slate'] = dict(coat=hexes('#16191c', '#1e2226', '#272c31', '#31373d', '#3c434a', '#485058', '#555e67', '#626c76'),
                         pale=hexes('#7e868e', '#959ca3', '#abb1b7', '#c0c5ca', '#d3d7db', '#e4e7ea'),
                         flight=hexes('#101215', '#171a1e', '#1f2328', '#282d33', '#32383f', '#3d444c', '#49515a'),
                         panel=hexes('#5e666e', '#757d85', '#8c949b', '#a3aab0', '#babfc4'),
                         beak=PALETTES['Natural']['beak'], leg=PALETTES['Natural']['leg'],
                         skin=PALETTES['Natural']['skin'])
PALETTES['tawny'] = dict(coat=hexes('#3a2410', '#4a2e14', '#5b3a1a', '#6d4620', '#805428', '#946230', '#a8713a', '#bc8146'),
                         pale=hexes('#b89a6a', '#c8ac7e', '#d6bc92', '#e2cca6', '#ecdaba', '#f4e8d0'),
                         flight=hexes('#24160a', '#30200e', '#3d2a14', '#4b341a', '#5a3f21', '#6a4a29', '#7a5632'),
                         panel=hexes('#7a5a38', '#946f48', '#ae855a', '#c69b6e', '#dcb184'),
                         beak=PALETTES['Natural']['beak'], leg=hexes('#7a6420', '#94792a', '#ae8f36', '#c6a544', '#dcbb56'),
                         skin=PALETTES['Natural']['skin'])
TALON_RAMP = hexes('#0a0908', '#131110', '#1d1a18', '#282421', '#35302c')


def countershade(p):
    z, y = p[..., 2], p[..., 1]
    low = np.interp(z, [b[0] for b in BODY], [b[1] for b in BODY])
    high = np.interp(z, [b[0] for b in BODY], [b[2] for b in BODY])
    return np.clip((y - low) / np.maximum(high - low, 0.3), 0, 1)


def coat(tx, pal):
    """Body feathers: rows of rounded feathers, a little paler underneath."""
    p, n = tx.points, tx.normal
    f = countershade(p)
    base = 0.45 + 0.15 * (1 - f) * (n[1] < 0.5) + 0.1 * (fbm(p, 1.0, seed=291, octaves=2) - 0.5)
    return feathers(tx, base, pal, width=3, height=2, seed=292)


def ruff(tx, pal):
    """The ruff: long shaggy pale feathers hanging round the neck."""
    a, b, value = __import__('handmade_models').locks(tx, 2, 4, seed=293)
    v = 0.6 + 0.2 * (value - 0.5) - 0.25 * (b > 0.8) + 0.08 * (np.abs(a - 0.5) < 0.2)
    return shade(pal['pale'], v, tx.x, tx.y, dither=0.0)


def head(tx, pal):
    """The head: short pale feathers over the crown and cheeks, greyer toward the throat and round the eye."""
    p, n = tx.points, tx.normal
    a, b, value = __import__('handmade_models').locks(tx, 2, 2, seed=294)
    low = np.clip((6.9 - p[..., 1]) / 0.8, 0, 1)
    v = 0.6 + 0.12 * (value - 0.5) - 0.12 * (b > 0.75) - 0.22 * low
    return shade(pal['pale'], v, tx.x, tx.y, dither=0.0)


def beak(tx, pal):
    """Horn, streaked, the hook darker, a dark line along the gape; the mouth's inside red."""
    p, n = tx.points, tx.normal
    z, y = p[..., 2], p[..., 1]
    streak = cells(np.stack([np.floor(y / tx.texel), np.zeros_like(y), np.full(y.shape, float(tx.face.index % 97))],
                            -1), 1.0, seed=295) - 0.5
    v = 0.66 + 0.12 * streak - 0.5 * np.clip((z - 4.95) / 0.5, 0, 1)
    colour = shade(pal['beak'], v, tx.x, tx.y, dither=0.0)
    jaw = tx.box.bone == JAW
    if abs(n[0]) > 0.5:
        gape = np.abs(y - 6.37) < 0.035
        colour = np.where(gape[..., None], shade(TALON_RAMP, 0.2, tx.x, tx.y, dither=0.0), colour)
    if (jaw and n[1] > 0.5) or (not jaw and n[1] < -0.5 and tx.box.tag.get('part') == 'beak'):
        colour = shade(pal['skin'], 0.3, tx.x, tx.y)
    return colour


def card(tx, pal):
    """A wing, tail or crest card trimmed to its bone's cells of the ARK mesh, painted feather by feather."""
    tag = tx.box.tag
    mask = tag['sheet']
    p = tx.points
    alpha = mask.at(p) == tag['owner']
    texel = tx.texel
    if tag['fin'] == 'crest':                                  # quills swept up and back, dark at the tips
        y = p[..., 1]
        quill = np.floor((p[..., 2] + 0.8 * y) / (texel * 1.5))
        tone = cells(np.stack([quill, np.zeros_like(quill), np.zeros_like(quill)], -1), 1.0, seed=296) - 0.5
        rise = np.clip((y - 7.2) / 0.9, 0, 1)
        colour = shade(pal['pale'], 0.55 + 0.15 * tone - 0.3 * (quill % 2 == 1) * rise, tx.x, tx.y, dither=0.0)
        off = np.zeros(p.shape[:-1], bool)
        for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            off |= mask.at(p + (mask.u * du + mask.v * dv) * texel) < 0
        colour = np.where((off & (rise > 0.3))[..., None], shade(pal['coat'], 0.25, tx.x, tx.y, dither=0.0), colour)
        return colour, alpha
    side = tag.get('side', 1.0)
    x = np.abs(p[..., 0])
    if tag['fin'] == 'tail':
        flow = unit(np.array([0.0, 0.0, -1.0]))
        span = 0.0
    else:
        span = np.clip((x - 2.0) / 13.0, 0, 1)
        flow = None
    # the direction the feathers point: straight back on the inner wing, swept out toward the tip
    sweep = 0.95 * (np.clip((x - 5.5) / 8.0, 0, 1) if tag['fin'] == 'wing' else 0 * x)
    dz, dx = -np.cos(sweep), np.sin(sweep) * (side if tag['fin'] == 'wing' else 0)
    across_x, across_z = -dz, dx                                          # square to the feather, in the plane
    s = (p[..., 0] * across_x + p[..., 2] * across_z) / texel
    t = (p[..., 0] * dx + p[..., 2] * dz) / texel
    width = 3.0 if tag['fin'] == 'wing' else 3.5
    feather = np.floor(s / width)
    fs = s / width - feather                                              # across this feather, 0 to 1
    tone = cells(np.stack([feather, np.full(feather.shape, float(tag['owner'])), np.zeros_like(feather)], -1), 1.0,
                 seed=297) - 0.5
    # how far each texel lies from the card's ragged trailing edge, along the feather
    edge = np.full(p.shape[:-1], 9)
    direction = np.stack([dx, np.zeros_like(dx), dz], -1)
    for k in range(6, 0, -1):
        edge = np.where(mask.at(p + direction * texel * k) < 0, k, edge)
    coverts = tag['owner'] == 0 or edge >= 9
    flight = shade(pal['flight'], 0.5 + 0.25 * tone + 0.1 * (np.abs(fs - 0.5) < 0.18) - 0.15 * (fs > 0.85), tx.x, tx.y,
                   dither=0.0)
    if tag['fin'] == 'wing':
        panel = (span > 0.05) & (span < 0.45) & (edge <= 6) & (edge > 1) & (tx.normal[1] > 0)
        flight = np.where(panel[..., None], shade(pal['panel'], 0.5 + 0.2 * tone, tx.x, tx.y, dither=0.0), flight)
    tip = edge <= 1
    flight = np.where(tip[..., None], shade(pal['flight'], 0.05, tx.x, tx.y, dither=0.0), flight)
    # the coverts: rows of short rounded feathers over the feathers' roots
    a = (s / 2.0) % 1.0
    row = np.floor(t / 2.0 + 0.5 * (np.floor(s / 2.0) % 2))
    rb = (t / 2.0 + 0.5 * (np.floor(s / 2.0) % 2)) % 1.0
    cv = cells(np.stack([np.floor(s / 2.0), row, np.zeros_like(row)], -1), 1.0, seed=298) - 0.5
    cov = shade(pal['coat'], 0.45 + 0.2 * cv - 0.2 * (rb > 0.7), tx.x, tx.y, dither=0.0)
    root = edge >= 9
    colour = np.where(root[..., None], cov, flight)
    if tx.normal[1] < 0:
        colour = colour * 0.9 + 6
    return colour, alpha


def painter(tx, variant):
    pal = PALETTES[variant]
    mat = tx.box.mat
    if mat in ('wing', 'tail', 'crest'):
        return card(tx, pal)
    if mat == 'beak':
        return beak(tx, pal), None
    if mat == 'brow':
        return shade(pal['coat'], 0.35 + 0.1 * (cells(tx.points, tx.texel, seed=301) - 0.5), tx.x, tx.y,
                     dither=0.0), None
    if mat == 'head':
        return head(tx, pal), None
    if mat == 'ruff':
        return ruff(tx, pal), None
    if mat == 'leg':
        col, row = face_grid(tx)
        scale = (row % 2 == 0)
        return shade(pal['leg'], 0.55 - 0.15 * scale + 0.1 * (cells(tx.points, tx.texel, seed=299) - 0.5), tx.x,
                     tx.y, dither=0.0), None
    if mat == 'talon':
        return shade(TALON_RAMP, 0.4 + 0.3 * (cells(tx.points, tx.texel, seed=300) - 0.5), tx.x, tx.y), None
    return coat(tx, pal), None
