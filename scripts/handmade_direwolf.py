"""The Direwolf, designed box by box on the ARK rig (Direwolf_New); built by scripts/handmade_models.py.

A big shaggy wolf in the rig's bind pose (source units: +y up, the animal faces +z, its left is +x), fitted to the
ARK mesh's fur coat rather than the body under it. Every spine segment has a solid core, a cape that hangs over the
upper flank and a fringe under the belly; the cape and the fringe are cut into ragged locks at their hems, so the
dark guard hairs fall over the grey flank and the buff belly fur hangs below it. The coat rises over the withers
into a mane and wraps the back of the head like a hood: a collar framing the face, a crest standing up behind the
ears and a ruff from the throat to the chest. Shaggy breeches over the thighs, slim lower legs and paws, and a
bushy brush of a tail. The head has forward-facing eyes beside a narrow bridge over the broader upper lip, cheek
tufts, stepped ears and two fangs over the narrow chin; the jaw is designed shut as the idle holds it (the bind pose
snarls open).
"""
import numpy as np

from handmade_models import cells, hexes, lift, noise, shade, strands, unit

FOLDER, STEM, HEIGHT = 'Direwolf', 'direwolf', 1.6
DENSITY = 1.0            # texels per runtime unit; the head is painted at FACE times that
FACE = 2.0
ATLAS_WIDTH = 256
PREVIEW_VARIANT = 'Natural'
ASSET = 'Direwolf_New'
OUTLINE_SKIP = ('wisker',)
PREVIEW_CLIPS = ('Direwolf-Idle', 'Direwolf-Move-Fwd', 'Direwolf-Attack-Bite', 'Direwolf-Howl')
JAW_POSE = ('Direwolf-Idle', 0.0, ['jaw', 'tongue'])     # the frame the jaw is designed shut in
HEAD, JAW = 'c_head', 'c_jaw'

# The trunk, segment by segment along the spine (source units): the bone, its rear and front z, the belly line
# under the core, the core (top, half width), the cape that hangs over the upper flank (bottom, top, half width),
# the back on top of it (top, half width) and the fringe hanging under the belly (bottom, top, half width). Every
# segment overlaps the next, so the spine can bend without opening the coat.
COAT = [
    ('c_back1', -3.45, -1.25, 4.3, (6.62, 1.82), (5.7, 7.12, 1.95), (7.6, 1.2), None),
    ('c_back2', -1.62, 0.42, 3.86, (6.56, 1.85), (5.78, 7.14, 1.99), (7.66, 1.3), (3.1, 4.72, 1.92)),
    ('c_back3', 0.04, 2.12, 3.62, (6.6, 1.97), (5.84, 7.3, 2.09), (7.84, 1.42), (2.86, 4.6, 2.03)),
    ('c_back4', 1.74, 3.92, 3.7, (6.78, 2.0), (6.0, 7.66, 2.13), (8.2, 1.48), (2.86, 4.7, 2.06)),
]


def segment(d, bone, z0, z1, belly, core, cape, back, fringe):
    """One segment of the coat: a solid core with its rounded belly and back, the cape over its upper flank and the
    fringe under the belly, both cut into ragged locks at their hems. Each box's ends are set in a little from the
    one before, so no two faces share a plane."""
    (top, half), (c0, c1, chalf) = core, cape
    hem = dict(open=('down',), cutout=True)
    d.span(bone, (-half, belly, z0), (half, top, z1), 'fur', part='body')
    d.span(bone, (-half * 0.72, belly - 0.34, z0 + 0.09), (half * 0.72, belly + 0.05, z1 - 0.09), 'fur', part='body')
    d.span(bone, (-chalf, c0, z0 + 0.03), (chalf, c1, z1 - 0.03), 'fur', part='cape', hem=0.85, **hem)
    if back:
        d.span(bone, (-back[1], c1 - 0.05, z0 + 0.06), (back[1], back[0], z1 - 0.06), 'fur', part='back')
    if fringe:
        d.span(bone, (-fringe[2], fringe[0], z0 + 0.12), (fringe[2], fringe[1], z1 - 0.12), 'fur', part='fringe',
               hem=0.9, open=('down', 'up'), cutout=True)


def tiers(d, bone, z0, z1, rows, mat='fur', part='body', inset=0.04, **tag):
    """Stacked boxes across a segment, each overlapping the next a little; the tiers off the core are inset at both
    ends, so no two neighbours share an end plane."""
    core = max(range(len(rows)), key=lambda k: rows[k][2])
    for k, (y0, y1, half) in enumerate(rows):
        e = 0.0 if k == core else inset * (1 + abs(k - core))
        d.span(bone, (-half, y0, z0 + e), (half, y1, z1 - e), mat, part=part, tier=k - core, **tag)


def design(d):
    rig = d.rig
    P = rig.pivot
    for bone in (JAW, 'c_tongue1', 'c_tongue2', 'c_tongue3', 'c_tongue4', 'c_tongue5'):
        rig.design_as_in(bone, 'Direwolf-Idle', 0.0, HEAD)
    f = dict(density=FACE)

    # ---------------------------------------------------------------- trunk and coat
    for row in COAT:
        segment(d, *row)
    hem = dict(open=('down',), cutout=True)
    # the rump: the coat rounds off and slopes down to the tail
    d.span('c_back1', (-1.52, 4.62, -4.88), (1.52, 6.42, -3.18), 'fur', part='body')
    d.span('c_back1', (-1.66, 5.55, -4.82), (1.66, 6.84, -3.22), 'fur', part='cape', hem=0.7, **hem)
    d.slab('c_back1', (6.5, -4.98), (7.44, -3.15), 1.06, 0.84, 'fur', part='back')
    d.span('c_back1', (-1.0, 4.78, -4.97), (1.0, 6.28, -4.62), 'fur', part='body')

    # ---------------------------------------------------------------- neck, collar and mane
    # The coat rises over the withers into a thick mane and wraps the back of the head like a hood: a collar whose
    # front frames the face, its crest standing up behind the ears, a ruff hanging under the throat to the chest.
    tiers(d, 'c_back5', 3.5, 5.0, [(4.14, 4.7, 1.25), (4.65, 7.4, 1.6), (7.35, 8.05, 1.22)], part='neck', inset=0.047)
    d.span('c_back4', (-1.7, 7.1, 2.42), (1.7, 8.52, 3.82), 'fur', part='mane', hem=0.7, **hem)
    d.span('c_back5', (-2.04, 5.35, 3.55), (2.04, 8.66, 5.05), 'fur', part='mane', hem=1.3, **hem)
    d.span('c_back5', (-1.82, 4.92, 4.94), (1.82, 8.36, 6.24), 'fur', part='collar', hem=1.2, **hem)
    d.span('c_back5', (-1.24, 8.2, 4.58), (1.24, 8.93, 6.14), 'fur', part='crest')
    d.span('c_back5', (-0.62, 8.86, 4.9), (0.62, 9.06, 5.95), 'fur', part='crest')
    d.span('c_back5', (-1.4, 3.72, 3.98), (1.4, 5.6, 6.06), 'fur', part='ruff', hem=0.9, **hem)
    d.span('c_back4', (-1.28, 2.84, 2.66), (1.28, 5.0, 4.12), 'fur', part='ruff', hem=0.9, **hem)

    # ---------------------------------------------------------------- head (painted at FACE density)
    d.span(HEAD, (-1.0, 6.3, 5.95), (1.0, 7.95, 7.32), 'face', part='cranium', **f)
    d.span(HEAD, (-0.78, 7.9, 5.9), (0.78, 8.3, 7.12), 'face', part='crown', **f)
    d.span(HEAD, (-0.45, 8.25, 5.85), (0.45, 8.45, 6.75), 'face', part='crown', **f)
    d.span(HEAD, (-1.38, 6.18, 5.92), (1.38, 7.72, 6.56), 'fur', part='cheek', hem=0.7, **hem, **f)
    # the muzzle: a narrow bridge over the broader upper lip, stepping down to the nose
    d.span(HEAD, (-0.42, 7.0, 7.25), (0.42, 7.62, 7.95), 'face', part='bridge', **f)
    d.span(HEAD, (-0.38, 6.95, 7.9), (0.38, 7.35, 8.5), 'face', part='bridge', **f)
    d.span(HEAD, (-0.56, 6.28, 7.25), (0.56, 7.05, 8.15), 'face', part='muzzle', **f)
    d.span(HEAD, (-0.46, 6.3, 8.1), (0.46, 7.0, 8.58), 'face', part='muzzle', **f)
    d.span(HEAD, (-0.22, 6.78, 8.47), (0.22, 7.03, 8.67), 'face', part='nose', **f)
    # the upper fangs hang from the lip just outside the narrower chin
    d.span(HEAD, (0.43, 6.06, 8.16), (0.49, 6.33, 8.28), 'tooth', mirror=True, part='fang', **f)
    # the lower jaw, shut under the muzzle
    d.span(JAW, (-0.5, 5.86, 6.4), (0.5, 6.32, 7.6), 'face', part='jaw', **f)
    d.span(JAW, (-0.4, 5.9, 7.55), (0.4, 6.34, 8.3), 'face', part='chin', **f)
    # ears: a stepped point, its open face forward
    ear = dict(yaw=-12, roll=-6, mat='face', mirror=True, part='ear', **f)
    d.turned('l_ear1', (0.95, 8.28, 6.38), (0.66, 0.46, 0.32), **ear)
    d.turned('l_ear2', (1.05, 8.62, 6.42), (0.46, 0.32, 0.26), **ear)
    d.turned('l_ear2', (1.13, 8.88, 6.45), (0.24, 0.24, 0.2), **ear)

    # ---------------------------------------------------------------- forelegs
    shoulder, elbow, wrist, toe = P('l_shoulder'), P('l_elbow'), P('l_wrist'), P('l_toeA1')
    d.seg('l_shoulder', shoulder + (-0.1, -0.6, -0.35), elbow, 0.78, 0.98, 'fur', ext=(0.0, 0.3), mirror=True,
          part='arm')
    d.seg('l_elbow', elbow, wrist, 0.7, 0.85, 'fur', ext=(0.3, 0.1), mirror=True, part='forearm')
    d.seg('l_wrist', wrist, toe + (0.0, 0.2, 0.0), 0.62, 0.72, 'fur', ext=(0.1, 0.0), mirror=True, part='wrist')
    d.span('l_toeA1', (0.5, 0.0, 2.6), (1.45, 0.55, 3.95), 'paw', mirror=True, part='paw')

    # ---------------------------------------------------------------- hind legs
    knee, ankle, foot = P('l_knee'), P('l_ankle'), P('l_foot')
    # the thigh in shaggy breeches: a fringe whose locks hang below the solid thigh, rounded off front and back
    d.span('l_leg', (0.62, 3.2, -4.32), (2.0, 5.95, -2.02), 'fur', mirror=True, part='thigh', hem=0.75, open=('down',),
           cutout=True)
    d.span('l_leg', (0.7, 3.9, -4.18), (1.88, 5.9, -2.16), 'fur', mirror=True, part='thigh')
    d.span('l_leg', (0.74, 4.2, -2.08), (1.84, 5.7, -1.72), 'fur', mirror=True, part='thigh')
    d.span('l_leg', (0.74, 3.85, -4.62), (1.84, 5.85, -4.26), 'fur', mirror=True, part='thigh')
    d.seg('l_knee', knee, ankle, 0.8, 0.95, 'fur', ext=(0.35, 0.25), mirror=True, part='shin')
    d.seg('l_ankle', ankle, foot + (0.0, 0.2, 0.0), 0.62, 0.72, 'fur', ext=(0.15, 0.0), mirror=True, part='hock')
    d.span('l_foot', (0.92, 0.0, -3.95), (1.95, 0.55, -2.62), 'paw', mirror=True, part='paw')

    # ---------------------------------------------------------------- tail: a bushy brush hanging to its tip
    tail = ['c_tail1', 'c_tail2', 'c_tail3', 'c_tail4']
    points = [P(b) for b in tail] + [np.array([0.0, 2.95, -10.05])]
    sizes = [(1.45, 1.7), (2.05, 2.3), (2.0, 2.2), (1.5, 1.7)]
    for k, bone in enumerate(tail):
        w, dd = sizes[k]
        d.seg(bone, points[k], points[k + 1], w, dd, 'fur', ext=(0.35 if k else 0.7, 0.12), part='tail', ring=k)
    tip = unit(points[-1] - points[-2])
    d.seg('c_tail4', points[-1] - tip * 0.5, points[-1] + tip * 0.42, 0.95, 1.1, 'fur', part='tail', ring=4)


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(fur=hexes('#3e3832', '#4f4840', '#625a50', '#766d61', '#8b8274', '#a09787', '#b4ac9b', '#c7c0af'),
                    cape=hexes('#221e1a', '#2d2823', '#39332c', '#463f37', '#554c42', '#655b4f'),
                    cream=hexes('#958c7a', '#aaa28f', '#beb7a4', '#d0cab8', '#dfdacb', '#ebe8dd'),
                    tan=hexes('#6f5a40', '#866e4f', '#9c8460', '#b19a73', '#c4ae88'),
                    nose=hexes('#0f0e0d', '#1a1816', '#26231f', '#34302b')),
    'Ivory': dict(fur=hexes('#7d786e', '#908b80', '#a39e92', '#b5b1a5', '#c6c2b7', '#d5d2c8', '#e2e0d7', '#eeece6'),
                  cape=hexes('#5e5950', '#6d685e', '#7c776c', '#8c877b', '#9c978b', '#aca79b'),
                  cream=hexes('#c8c4b9', '#d5d2c8', '#e0ded5', '#e9e7e0', '#f1f0eb', '#f8f7f4'),
                  tan=hexes('#8f8270', '#a29581', '#b4a893', '#c5baa6', '#d4cab8'),
                  nose=hexes('#1c1a19', '#292624', '#36322f', '#45403c')),
    'Darken': dict(fur=hexes('#101010', '#171716', '#1f1e1d', '#282725', '#32302e', '#3c3a37', '#474441', '#524f4b'),
                   cape=hexes('#060606', '#0a0a09', '#0f0f0e', '#151413', '#1b1a18', '#22201e'),
                   cream=hexes('#2f2d2a', '#3a3834', '#46433e', '#524f49', '#5f5b54', '#6c675f'),
                   tan=hexes('#231e18', '#2d271f', '#383027', '#433a2f', '#4f4538'),
                   nose=hexes('#050505', '#0a0909', '#100f0e', '#171514')),
    'Emerald': dict(fur=hexes('#111c13', '#17251a', '#1e3022', '#263c2b', '#2f4934', '#38563e', '#436448', '#4f7353'),
                    cape=hexes('#060b07', '#0a110b', '#0e1810', '#132015', '#19281b', '#1f3122'),
                    cream=hexes('#4e5f4c', '#5d6f5a', '#6e7f6a', '#7f907b', '#90a08c', '#a2b19e'),
                    tan=hexes('#2b3420', '#364128', '#424f31', '#4f5d3b', '#5c6c46'),
                    nose=hexes('#070807', '#0d0f0c', '#141712', '#1b1f19')),
    'Midnight': dict(fur=hexes('#111722', '#17202e', '#1e293b', '#263349', '#2f3e58', '#394a67', '#445777', '#516587'),
                     cape=hexes('#05070b', '#090c13', '#0d121b', '#121824', '#171f2e', '#1d2638'),
                     cream=hexes('#4d586c', '#5c677c', '#6c778c', '#7d889c', '#8f99ac', '#a1abbd'),
                     tan=hexes('#262d3c', '#2f3748', '#394255', '#434e62', '#4f5a70'),
                     nose=hexes('#06070a', '#0b0d11', '#111419', '#171b22')),
    'Burgundy': dict(fur=hexes('#240d0e', '#331314', '#43191a', '#542021', '#662828', '#783131', '#8a3b3a', '#9c4744'),
                     cape=hexes('#0e0405', '#160607', '#1f090a', '#290c0d', '#331011', '#3e1415'),
                     cream=hexes('#6f5352', '#826362', '#957473', '#a78685', '#b89897', '#c8abaa'),
                     tan=hexes('#3e1d17', '#4e261e', '#5f3026', '#703a2f', '#824539'),
                     nose=hexes('#0b0505', '#140909', '#1d0d0d', '#271212')),
}
# the darkest variants a shade lighter, so they still read under in-game light
PALETTES['Darken'] = lift(PALETTES['Darken'], 1.25, 6)
PALETTES['Emerald'] = lift(PALETTES['Emerald'], 1.08, 3)
PALETTES['Midnight'] = lift(PALETTES['Midnight'], 1.08, 3)
PALETTES['Burgundy'] = lift(PALETTES['Burgundy'], 1.08, 3)
CLAW_RAMP = hexes('#141210', '#211e1a', '#2f2b26', '#403a33')
PAD_RAMP = hexes('#161212', '#221c1b', '#2e2624')
MOUTH_RAMP = hexes('#2e1214', '#481c1e', '#64292a', '#81393a', '#9c4c4b', '#b46261')
TOOTH_RAMP = hexes('#a49b87', '#c4bba6', '#dcd5c3', '#ede8db', '#f8f5ee')

# The coat's outline along the body (z): the top of the back and mane, and the bottom of the hanging fur.
BACK = ([-5.0, -4.5, -4.0, -3.5, -3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 6.5],
        [6.5, 6.85, 7.12, 7.3, 7.45, 7.62, 7.62, 7.66, 7.8, 8.2, 8.42, 8.66, 8.9, 8.95, 8.95])
UNDER = ([-5.0, -4.0, -3.0, -2.0, -1.5, -1.0, 0.0, 1.0, 2.0, 3.0, 3.5, 4.0, 5.0, 6.0, 6.5],
         [4.6, 4.0, 3.6, 3.3, 3.15, 3.12, 3.0, 2.86, 2.85, 2.86, 3.2, 3.7, 4.4, 5.0, 5.2])
# How far the dark cape reaches down the side from the top line, along the body.
CAPE = ([-5.0, -4.0, -3.0, -2.0, 0.0, 2.0, 3.0, 4.0, 5.0, 6.0, 6.5],
        [0.95, 1.2, 1.35, 1.3, 1.3, 1.5, 1.8, 2.0, 2.05, 1.9, 1.8])


def back_y(z):
    return np.interp(z, *BACK)


def under_y(z):
    return np.interp(z, *UNDER)


def grid(tx, axis):
    """Per texel: its index along a world axis across a world-aligned box (0 at the low end), and the count."""
    box = tx.box
    lo, hi = box.center[axis] - box.size[axis] / 2, box.center[axis] + box.size[axis] / 2
    count = max(1, int(round((hi - lo) / tx.texel)))
    index = np.floor((tx.points[..., axis] - lo) / (hi - lo) * count).astype(int)
    return np.clip(index, 0, count - 1), count


def local_grid(tx, axis):
    """Per texel: its index along one of its box's own axes (0 at the low end), and the count across."""
    box = tx.box
    local = (tx.points - box.center) @ box.axes[:, axis]
    count = max(1, int(round(box.size[axis] / tx.texel)))
    return np.clip(np.floor((local / box.size[axis] + 0.5) * count).astype(int), 0, count - 1), count


def edge(tx, axis=0):
    """Per texel: how many texels it lies from the nearer side of the box across a world axis."""
    i, count = grid(tx, axis)
    return np.minimum(i, count - 1 - i)


def hair_alpha(tx):
    """Jagged locks at the bottom of a hanging fringe: each lock and strand ends at its own length."""
    depth = tx.box.tag.get('hem')
    if not depth or abs(tx.normal[1]) > 0.5:
        return None
    tone, _, lock = strands(tx, 2, 1000, seed=5)
    rise = tx.up_from_bottom() + 0.5
    reach = depth / tx.texel * (0.1 + 0.68 * lock + 0.22 * tone)
    return rise >= reach


def fur_texture(tx, seed=7):
    """Value offsets for short shaggy fur: one tone per strand (texel column), locks layered three texels long,
    each lit at its root and darker toward its tip, and a fine grain."""
    tone, phase, lock = strands(tx, 1, 3, seed=seed)
    grain = cells(tx.points, tx.texel * 1.01, seed=seed + 10)
    return 0.07 * (tone - 0.5) + 0.04 * (lock - 0.5) + 0.05 * (0.5 - phase) + 0.03 * (grain - 0.5), tone, lock


def coat(tx, pal, part=None):
    """The coat of the trunk, neck and mane, in strands and locks: the dark cape over the back, its guard hairs
    letting more and more grey through toward its ragged hem; a grey flank warming to buff low down; the hanging
    fringe buff with cream tips; the cream underside, chest and throat."""
    p, n, box = tx.points, tx.normal, tx.box
    part = part or box.tag.get('part', 'body')
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    top, bottom = back_y(z), under_y(z)
    f = np.clip((y - bottom) / np.maximum(top - bottom, 0.5), 0, 1)
    texture, tone, lock = fur_texture(tx)
    lit = 0.04 * max(n[1], 0)
    grey = shade(pal['fur'], 0.58 + 0.14 * f + texture + lit, tx.x, tx.y, dither=0.0)
    buff = shade(pal['tan'], 0.6 + texture * 1.5 + lit, tx.x, tx.y, dither=0.0)
    cream = shade(pal['cream'], 0.58 + texture * 1.6 - 0.1 * max(-n[1], 0), tx.x, tx.y, dither=0.0)
    dark = shade(pal['cape'], 0.52 + texture * 2.2 + lit, tx.x, tx.y, dither=0.0)
    side = abs(n[1]) < 0.5
    if part in ('cape', 'back', 'crest'):
        colour = dark
        if box.tag.get('hem') and side:
            # toward the hem the cape thins out: more and more strands are the flank's grey
            rise = np.clip(tx.up_from_bottom() * tx.texel / max(box.size[1], 0.1), 0, 1)
            colour = np.where((tone < 0.62 * (1 - rise) ** 1.4)[..., None], grey, colour)
        # pale guard-hair tips flecked through the cape
        fleck = (cells(p, tx.texel * 1.01, seed=19) > 0.86) & (lock > 0.35)
        return np.where(fleck[..., None], shade(pal['fur'], 0.42 + texture, tx.x, tx.y, dither=0.0), colour)
    if part == 'fringe':
        rise = np.clip(tx.up_from_bottom() * tx.texel / max(box.size[1], 0.1), 0, 1)
        colour = np.where((tone < 0.25 + 0.6 * rise)[..., None], buff, cream)
        if n[1] < -0.5:
            colour = cream
        return colour
    # the flank, neck and the rest of the coat
    colour = grey
    warm = (f < 0.42 + 0.1 * (tone - 0.5)) & (f > 0.18 + 0.16 * (lock - 0.5))
    colour = np.where(warm[..., None], buff, colour)
    line = 0.2 + 0.1 * (noise(p, 0.7, seed=9) - 0.5) + 0.12 * (lock - 0.5) + (0.6 if part == 'ruff' else 0.0)
    under = (f < line) | (n[1] < -0.5)
    if part in ('collar', 'ruff') and n[2] > 0.5:
        under |= (y < 6.0 + 0.3 * (lock - 0.5)) & (np.abs(x) < 1.3)
    colour = np.where(under[..., None], cream, colour)
    if part in ('mane', 'collar', 'neck'):
        # the cape runs on over the withers and down the back of the neck, ragged lock by lock
        reach = np.interp(z, *CAPE) * (0.85 + 0.3 * lock)
        cape = (top - y < reach) & ~under
        fleck = (cells(p, tx.texel * 1.01, seed=19) > 0.86) & (lock > 0.35)
        colour = np.where((cape & ~fleck)[..., None], dark, colour)
    return colour


def mane(tx, pal):
    """The mane, collar and crest: the coat in longer locks, the cape over the top and down the back of the neck, and
    on the collar's face the fur framing the head, shadowed where it meets the head and pale at the throat."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    colour = coat(tx, pal)
    if part == 'collar' and n[2] > 0.5:
        # the collar's face: a darker ring of shadow round the head
        x, y = p[..., 0], p[..., 1]
        ring = (np.abs(x) < 1.18) & (y > 6.3) & (y < 8.12)
        texture, _, _ = fur_texture(tx, seed=23)
        colour = np.where(ring[..., None], shade(pal['cape'], 0.75 + texture * 2, tx.x, tx.y, dither=0.0), colour)
    return colour


def limb(tx, pal):
    """Legs: the thighs and upper forelegs in the flank's grey, the lower legs buff outside and cream inside and
    behind, a dark stripe down the front of each foreleg."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    y = p[..., 1]
    texture, tone, lock = fur_texture(tx, seed=31)
    inner = n[0] * np.sign(tx.box.center[0]) < -0.5
    if part in ('thigh', 'arm'):
        v = 0.54 + texture * 1.5 + 0.05 * max(n[1], 0)
        colour = shade(pal['fur'], v + 0.1 * np.clip((y - 3.6) / 2.2, 0, 1), tx.x, tx.y, dither=0.0)
        low = y < 4.5 + 0.5 * (lock - 0.5)
        colour = np.where(low[..., None], shade(pal['tan'], 0.6 + texture * 1.6, tx.x, tx.y, dither=0.0), colour)
        pale = inner | ((n[2] < -0.5 if part == 'thigh' else n[2] > 0.5) & (y < 5.2 + 0.4 * (tone - 0.5)))
        colour = np.where(pale[..., None], shade(pal['cream'], 0.56 + texture * 1.6, tx.x, tx.y, dither=0.0), colour)
        return colour
    v = 0.6 + texture
    colour = shade(pal['tan'], v, tx.x, tx.y, dither=0.0)
    pale = inner | (n[2] < -0.5)
    colour = np.where(pale[..., None], shade(pal['cream'], 0.6 + texture * 1.5, tx.x, tx.y, dither=0.0), colour)
    if part == 'forearm' and n[2] > 0.5:
        col, count = local_grid(tx, 0)
        stripe = (np.abs(col - (count - 1) / 2) < 0.8) & (y > 1.7)
        colour = np.where(stripe[..., None], shade(pal['cape'], 0.5 + texture * 2, tx.x, tx.y, dither=0.0), colour)
    return colour


def tail(tx, pal):
    """The brush: the coat's grey with the cape along its top, a pale underside and a dark tip."""
    p, n, box = tx.points, tx.normal, tx.box
    texture, tone, lock = fur_texture(tx, seed=41)
    ring = box.tag.get('ring', 0)
    up = box.axes.T @ np.array([0.0, 1.0, 0.0])               # world up in the box's frame
    rel = (p - box.center) @ box.axes                          # texel positions in the box's frame
    height = rel @ up / (np.abs(up) @ (box.size / 2))         # -1 under the tail, 1 on top
    v = 0.58 + 0.1 * height + texture
    colour = shade(pal['fur'], v, tx.x, tx.y, dither=0.0)
    cape = height > 0.45 - 0.25 * (lock - 0.5)
    colour = np.where(cape[..., None], shade(pal['cape'], 0.55 + texture * 2, tx.x, tx.y, dither=0.0), colour)
    under = height < -0.4 + 0.2 * (lock - 0.5)
    colour = np.where(under[..., None], shade(pal['cream'], 0.55 + texture * 1.5, tx.x, tx.y, dither=0.0), colour)
    along = rel[..., 1] / box.size[1] + 0.5                    # 0 at the root end of the box, 1 at its tip end
    if ring >= 3:
        tipped = (along > 0.55 - 0.2 * (lock - 0.5)) if ring == 3 else np.ones(p.shape[:2], bool)
        colour = np.where(tipped[..., None], shade(pal['cape'], 0.5 + texture * 2, tx.x, tx.y, dither=0.0), colour)
    return colour


def face(tx, pal):
    """The head as pixel art: a grizzled crown and forehead, pale brows over the eyes, cream cheeks, muzzle sides and
    chin, a buff bridge darkening to the black nose, the lip line, and the ears pale inside with dark rims."""
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    texture, tone, lock = fur_texture(tx, seed=51)
    side, front, top, below = abs(n[0]) > 0.5, n[2] > 0.5, n[1] > 0.5, n[1] < -0.5
    grey = shade(pal['fur'], 0.62 + texture + 0.05 * max(n[1], 0), tx.x, tx.y, dither=0.0)
    cream = shade(pal['cream'], 0.62 + texture * 1.2, tx.x, tx.y, dither=0.0)
    buff = shade(pal['tan'], 0.66 + texture * 1.2, tx.x, tx.y, dither=0.0)
    dark = shade(pal['cape'], 0.5 + texture * 2, tx.x, tx.y, dither=0.0)
    lip = shade(pal['nose'], 0.85, tx.x, tx.y, dither=0.0)
    if part in ('cranium', 'crown'):
        colour = grey
        if top:
            colour = np.where((np.abs(x) < 0.35)[..., None], dark, grey)
        if front:
            # dark forehead between pale brows; cream cheeks below the eyes
            brow = (np.abs(np.abs(x) - 0.72) < 0.2) & (y > 7.72)
            mid = (np.abs(x) < 0.42) & (y > 7.6)
            colour = np.where(mid[..., None], dark, colour)
            colour = np.where(brow[..., None], cream, colour)
            colour = np.where((y < 7.0)[..., None], cream, colour)
        if side:
            colour = np.where((y < 7.05 + 0.15 * (z - 6.6))[..., None], cream, colour)
        if below:
            colour = cream
        return colour
    if part == 'cheek':
        colour = cream
        if top:
            colour = grey
        return colour
    if part == 'bridge':
        colour = buff
        if side:
            colour = np.where((y < 7.12)[..., None], cream, buff)
        if top:
            colour = np.where((z > 8.25)[..., None], shade(pal['tan'], 0.45 + texture, tx.x, tx.y, dither=0.0), buff)
        return colour
    if part == 'muzzle':
        colour = cream
        if side:
            colour = np.where((tx.up_from_bottom() < 1)[..., None], lip, colour)
        if below:
            colour = shade(MOUTH_RAMP, 0.35 + texture, tx.x, tx.y, dither=0.0)
            colour = np.where((edge(tx, 0) == 0)[..., None], shade(TOOTH_RAMP, 0.7, tx.x, tx.y), colour)
        return colour
    if part == 'nose':
        colour = shade(pal['nose'], 0.35 + 0.4 * max(n[1], 0) + texture, tx.x, tx.y, dither=0.0)
        if front:
            nostril = (np.abs(np.abs(x) - 0.14) < 0.06) & (y < 6.86)
            colour = np.where(nostril[..., None], shade(pal['nose'], 0.0, tx.x, tx.y), colour)
        return colour
    if part in ('jaw', 'chin'):
        colour = cream
        if top:
            colour = shade(MOUTH_RAMP, 0.45 + texture, tx.x, tx.y, dither=0.0)
            e = edge(tx, 0)
            tongue = (np.abs(x) < 0.22) & (z > 6.7)
            colour = np.where(tongue[..., None], shade(MOUTH_RAMP, 0.82 + texture, tx.x, tx.y), colour)
            colour = np.where((e == 0)[..., None], shade(TOOTH_RAMP, 0.7, tx.x, tx.y), colour)
        return colour
    if part == 'ear':
        facing = tx.box.axes.T @ n
        if facing[2] > 0.5:                                    # the open front of the ear
            i, ci = local_grid(tx, 0)
            rim = (i == 0) | (i == ci - 1)
            colour = np.where(rim[..., None], buff, cream)
            return colour
        return np.where((tx.down() < 1)[..., None], dark, buff) if top else buff
    return grey


def paw(tx, pal):
    p, n = tx.points, tx.normal
    if n[1] < -0.5:                                            # the pads
        return shade(PAD_RAMP, 0.5 + 0.4 * (cells(p, tx.texel, seed=61) - 0.5), tx.x, tx.y)
    texture, _, _ = fur_texture(tx, seed=62)
    colour = shade(pal['cream'], 0.55 + texture * 1.5, tx.x, tx.y, dither=0.0)
    if n[2] > 0.5 or n[1] > 0.5:                               # the toes and claws
        i, count = grid(tx, 0)
        toes = np.round(np.arange(1, 4) * count / 4).astype(int)
        split = np.isin(i, toes)
        if n[2] > 0.5:
            j, _ = grid(tx, 1)
            split &= j >= 1
            claw = (j == 0) & ~np.isin(i, toes)
            colour = np.where(claw[..., None], shade(CLAW_RAMP, 0.5, tx.x, tx.y), colour)
        else:
            k, kc = grid(tx, 2)
            split &= k >= kc - 2
        colour = np.where(split[..., None], shade(pal['tan'], 0.3, tx.x, tx.y), colour)
    return colour


def painter(tx, variant):
    pal = PALETTES[variant]
    mat = tx.box.mat
    part = tx.box.tag.get('part')
    alpha = hair_alpha(tx)
    if mat == 'fur':
        if part in ('mane', 'collar', 'crest'):
            rgb = mane(tx, pal)
        elif part in ('body', 'cape', 'back', 'fringe', 'neck', 'ruff'):
            rgb = coat(tx, pal)
        elif part == 'tail':
            rgb = tail(tx, pal)
        elif part == 'cheek':
            rgb = face(tx, pal)
        else:
            rgb = limb(tx, pal)
    elif mat == 'face':
        rgb = face(tx, pal)
    elif mat == 'paw':
        rgb = paw(tx, pal)
    elif mat == 'tooth':
        rgb = shade(TOOTH_RAMP, 0.75 + 0.2 * (noise(tx.points, tx.texel, seed=71) - 0.5), tx.x, tx.y)
    else:
        rgb = shade(MOUTH_RAMP, 0.7, tx.x, tx.y)
    return rgb, alpha
