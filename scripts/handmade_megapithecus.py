"""The Megapithecus, designed box by box on the ARK rig (Gorilla); built by scripts/handmade_models.py.

A silverback in the rig's knuckle-walking bind pose (source units: +y up, the animal faces +z, its left is
+x): the back rises from a round rump to the hump over the shoulders, the head hangs low in front of it
under a sagittal crest, with a heavy brow over close-set eyes, a broad nose and a muzzle that shuts on hidden
fangs. Huge deltoids and long-haired arms walk on curled fists; the short legs stand on flat feet with a
grasping big toe. The chest, face, hands and feet are bare skin; a silver saddle lies across the lower back.
"""
import numpy as np

from handmade_models import cells, fbm, hexes, lift, noise, shade, strands

FOLDER, STEM, HEIGHT = 'Megapithecus', 'megapithecus', 3.5
DENSITY = 1.0
ATLAS_WIDTH = 512
PREVIEW_VARIANT = 'Natural'
ASSET = 'Gorilla'          # the ARK mesh the design is fitted to (--preview --outline)
PREVIEW_CLIPS = ('Gorilla-Idle', 'Gorilla-Move-Fwd', 'Gorilla-Attack-Pound', 'Gorilla_Chest_Pounding')
HEAD = 'c_head'
FINGERS = 'BCDE'


def design(d):
    rig = d.rig
    P = rig.pivot
    # the jaw is designed shut, as the idle holds it; the bind pose has it open
    rig.design_as_in('c_jaw', 'Gorilla-Idle', 0.0, 'c_head')

    # ---------------------------------------------------------------- body: cores, a sloping back, the belly
    d.span('c_back1', (-8.2, 12.6, -22.0), (8.2, 24.8, -10.6), 'fur', part='body')
    d.slab('c_back1', (25.8, -23.6), (30.0, -10.2), 7.6, 6.0, 'fur', part='back')
    d.span('c_back1', (-6.2, 16.9, -24.0), (6.2, 24.4, -21.6), 'fur', part='body')
    d.span('c_back1', (-6.4, 11.6, -19.5), (6.4, 13.2, -11.25), 'fur', part='belly')
    d.span('c_back2', (-8.7, 12.2, -11.0), (8.7, 29.4, -4.4), 'fur', part='body')
    d.slab('c_back2', (29.8, -11.2), (34.6, -4.0), 8.0, 6.0, 'fur', part='back')
    d.span('c_back2', (-7.0, 11.2, -10.6), (7.0, 12.8, -4.8), 'fur', part='belly')
    d.span('c_back3', (-9.0, 13.6, -4.8), (9.0, 34.0, 1.6), 'fur', part='body')
    d.slab('c_back3', (34.4, -5.0), (38.6, 1.8), 8.3, 6.0, 'fur', part='back')
    d.span('c_back3', (-7.2, 12.8, -4.15), (7.2, 14.2, 1.2), 'fur', part='belly')
    d.span('c_back4', (-8.0, 16.0, 1.2), (8.0, 36.8, 8.2), 'fur', part='chest')
    d.slab('c_back4', (38.4, 1.0), (39.8, 8.4), 7.2, 4.4, 'fur', part='back')
    # the hump over the shoulders and the throat under the head
    d.span('c_neck', (-6.2, 35.6, 1.6), (6.2, 41.8, 9.2), 'fur', part='hump')
    d.span('c_neck', (-4.2, 41.4, 2.6), (4.2, 42.6, 8.2), 'fur', part='hump')
    d.span('c_neck', (-5.6, 23.6, 8.6), (5.6, 35.8, 11.2), 'fur', part='throat')
    # hair hanging from the flanks
    for bone, z0, z1, half, low in (('c_back1', -19.5, -10.8, 8.2, 12.6), ('c_back2', -11.0, -4.4, 8.7, 12.2),
                                    ('c_back3', -4.8, 1.6, 9.0, 13.6)):
        d.span(bone, (-half - 0.4, low - 1.6, z0), (half + 0.4, low + 3.0, z1), 'fur', part='skirt', hem=2.0,
               open=('down', 'up', 'north', 'south'), cutout=True)

    # ---------------------------------------------------------------- shoulders and arms
    d.span('l_clavicle', (4.0, 33.0, 0.0), (10.0, 38.4, 9.0), 'fur', part='hump', mirror=True)
    d.span('l_shoulder', (6.4, 25.0, 1.0), (14.8, 34.6, 12.2), 'fur', part='shoulder', mirror=True)
    d.span('l_shoulder', (7.4, 34.4, 2.0), (13.6, 35.6, 11.0), 'fur', part='shoulder', mirror=True)
    elbow, wrist = P('l_elbow'), P('l_wrist')
    d.seg('l_shoulder', (10.4, 27.5, 7.0), elbow, 8.6, 9.0, 'fur', ext=(0, 1.2), mirror=True, part='arm')
    d.seg('l_elbow', elbow, wrist, 8.0, 8.6, 'fur', ext=(1.4, 0.2), mirror=True, part='forearm')
    d.seg('l_elbow', elbow, wrist, 9.4, 10.0, 'fur', ext=(-1.6, -1.2), mirror=True, part='fringe', hem=2.6,
          open=('down', 'up'), cutout=True)
    # the fist: a palm down to the knuckles, the fingers curled under it on the ground, the thumb along it
    knuckles = np.mean([P(f'l_finger{f}1') for f in FINGERS], axis=0)
    d.seg('l_wrist', wrist + (0, 0.6, 0), knuckles, 6.8, 4.6, 'hand', ext=(0, 0.9), side=(0, 0, 1), mirror=True,
          part='palm')
    for f in FINGERS:
        k1, k2, k3 = P(f'l_finger{f}1'), P(f'l_finger{f}2'), P(f'l_finger{f}3')
        d.seg(f'l_finger{f}1', k1, k2, 1.75, 1.7, 'hand', ext=(0.5, 0.45), side=(0, 0, 1), mirror=True, part='finger')
        d.seg(f'l_finger{f}2', k2, k3, 1.6, 1.55, 'hand', ext=(0.35, 0.55), side=(0, 0, 1), mirror=True, part='finger',
              nail=True)
    a1, a2, a3 = P('l_fingerA1'), P('l_fingerA2'), P('l_fingerA3')
    d.seg('l_fingerA1', a1, a2, 1.8, 1.8, 'hand', ext=(0.6, 0.3), mirror=True, part='finger')
    d.seg('l_fingerA2', a2, a3, 1.6, 1.6, 'hand', ext=(0.3, 0.6), mirror=True, part='finger', nail=True)

    # ---------------------------------------------------------------- legs and feet
    knee, ankle = P('l_knee'), P('l_ankle')
    d.seg('l_leg', (4.6, 23.2, -15.7), knee, 8.0, 9.0, 'fur', ext=(0, 1.2), mirror=True, part='thigh')
    d.seg('l_knee', knee, ankle, 6.6, 7.0, 'fur', ext=(1.2, 0.3), mirror=True, part='shin')
    heel = np.array([7.25, 1.55, -22.2])
    d.seg('l_ankle', heel, (9.3, 1.55, -16.4), 5.4, 3.7, 'foot', ext=(0, 0.4), mirror=True,
          part='sole')
    d.seg('l_footBall', (9.3, 1.25, -16.8), (9.9, 1.25, -14.6), 5.8, 2.6, 'foot', mirror=True,
          part='sole')
    for t in 'ABCD':
        t1, t2 = P(f'l_smallToe{t}1'), P(f'l_smallToe{t}2')
        d.seg(f'l_smallToe{t}2', t2 + (t2 - t1) * 0.2, t2 + (t2 - t1) * 1.15, 1.35, 1.25, 'foot',
              mirror=True, part='toe', nail=True)
    b1, b2 = P('l_bigToe1'), P('l_bigToe2')
    d.seg('l_bigToe1', b1 + (b1 - b2) * 0.2, b2, 2.0, 1.9, 'foot', mirror=True, part='toe')
    d.seg('l_bigToe2', b2, b2 + (b2 - b1) * 0.55, 1.8, 1.7, 'foot', ext=(0.35, 0), mirror=True, part='toe', nail=True)

    # ---------------------------------------------------------------- head
    d.span('c_head', (-5.1, 33.0, 8.8), (5.1, 40.4, 14.6), 'fur', part='crown')            # cranium
    d.span('c_head', (-2.6, 40.2, 7.0), (2.6, 42.5, 13.6), 'fur', part='crest')            # sagittal crest
    d.span('c_head', (-1.25, 42.3, 7.6), (1.25, 43.6, 12.2), 'fur', part='crest')
    d.span('c_head', (-3.6, 38.8, 13.8), (3.6, 40.0, 15.8), 'fur', part='crown')           # forehead
    d.span('c_head', (-4.35, 37.0, 14.2), (4.35, 39.0, 16.9), 'face', part='brow', density=2.0)  # brow ridge
    d.span('c_head', (-3.7, 33.8, 13.8), (3.7, 37.2, 15.9), 'face', part='eyes', density=2.0)  # eye band
    d.span('c_head', (-0.6, 33.6, 15.5), (0.6, 37.1, 16.5), 'face', part='bridge', density=2.0)  # between the eyes
    d.span('c_head', (-3.55, 30.6, 14.0), (3.55, 34.0, 18.8), 'face', part='muzzle', density=2.0)  # upper jaw
    d.span('c_head', (-2.3, 32.2, 17.6), (2.3, 34.6, 19.0), 'face', part='nose', density=2.0)  # broad flat nose
    d.span('c_head', (-5.4, 29.6, 9.4), (5.4, 35.2, 14.4), 'fur', part='cheek')            # jowls
    d.span('c_head', (-5.95, 29.0, 9.6), (5.95, 33.6, 13.8), 'fur', part='skirt', hem=2.2,
           open=('down', 'up', 'north', 'south'), cutout=True)
    d.span('c_head', (4.95, 34.4, 10.4), (5.8, 36.8, 12.4), 'face', part='ear', mirror=True, density=2.0)
    d.span('c_head', (-2.5, 30.0, 15.4), (2.5, 30.7, 18.3), 'tooth', part='teeth', density=2.0)
    d.span('c_head', (1.55, 29.4, 17.4), (2.35, 30.7, 18.2), 'tooth', part='fang', mirror=True, density=2.0)
    # the lower jaw, shut: chin, lip, teeth and the fangs that hide in the muzzle
    d.span('c_jaw', (-3.45, 27.4, 12.6), (3.45, 30.65, 18.6), 'face', part='jaw', density=2.0)
    d.span('c_jaw', (-2.9, 30.25, 17.3), (2.9, 31.25, 19.3), 'face', part='lip', density=2.0)
    d.span('c_jaw', (-2.4, 30.65, 15.6), (2.4, 31.2, 17.4), 'tooth', part='teeth', density=2.0)
    d.span('c_jaw', (1.5, 30.9, 16.3), (2.3, 32.0, 17.1), 'tooth', part='fang', mirror=True, density=2.0)


# ------------------------------------------------------------------------------------ paint
PALETTES = {
    'Natural': dict(fur=hexes('#0f0f10', '#18181a', '#222224', '#2d2d30', '#39393c', '#46464a', '#56565a'),
                    silver=hexes('#4a4a4c', '#5e5e60', '#747476', '#8b8b8c', '#a3a3a3', '#bcbcbb', '#d2d2d0'),
                    skin=hexes('#1d1a1a', '#2a2525', '#383131', '#463d3c', '#554a48', '#665855')),
    'silverback': dict(fur=hexes('#0f0f10', '#18181a', '#222224', '#2d2d30', '#39393c', '#46464a', '#56565a'),
                       silver=hexes('#4a4a4c', '#5e5e60', '#747476', '#8b8b8c', '#a3a3a3', '#bcbcbb', '#d2d2d0'),
                       skin=hexes('#1d1a1a', '#2a2525', '#383131', '#463d3c', '#554a48', '#665855')),
    'black': dict(fur=hexes('#0f0f10', '#18181a', '#222224', '#2d2d30', '#39393c', '#46464a', '#56565a'),
                  silver=hexes('#202022', '#29292c', '#333336', '#3d3d40', '#47474a', '#515155', '#5c5c60'),
                  skin=hexes('#1d1a1a', '#2a2525', '#383131', '#463d3c', '#554a48', '#665855')),
    'brown': dict(fur=hexes('#1e140f', '#2a1d17', '#37271f', '#443127', '#523c30', '#604739', '#6e5242'),
                  silver=hexes('#3e3128', '#504136', '#635245', '#776454', '#8b7663', '#a08974', '#b59c84'),
                  skin=hexes('#1d1a1a', '#2a2525', '#383131', '#463d3c', '#554a48', '#665855')),
}
TOOTH_RAMP = hexes('#8f887a', '#b3ab9a', '#d2cbb9', '#e9e3d3', '#f7f3e8')
MOUTH_RAMP = hexes('#2a1012', '#43191b', '#5e2627', '#7a3634', '#934744')
NAIL_RAMP = hexes('#2a2522', '#3e3632', '#544a44', '#6b5f57')
LOCKS = {'fringe': (2, 8), 'skirt': (2, 7), 'arm': (2, 6), 'forearm': (2, 7), 'shoulder': (2, 5), 'hump': (2, 4),
         'crest': (2, 3), 'crown': (2, 3), 'cheek': (2, 4), 'thigh': (2, 5), 'shin': (2, 4), 'back': (2, 4),
         'body': (2, 5), 'belly': (2, 5), 'chest': (2, 4), 'throat': (2, 4)}


def silver(p):
    """How far a point lies in the silver saddle over the lower back (0 outside, 1 in its middle)."""
    z, y, x = p[..., 2], p[..., 1], np.abs(p[..., 0])
    along = np.cos(np.clip((z + 14.0) / 8.5, -1, 1) * np.pi / 2) ** 2
    height = np.clip((y - 21.5 + 0.03 * x * x) / 4.0, 0, 1)
    edge = 0.35 * (fbm(p, 2.5, seed=61, octaves=2) - 0.5)
    return np.clip(along * height + edge, 0, 1)


def hair_alpha(tx):
    depth = tx.box.tag.get('hem')
    if not depth or abs(tx.normal[1]) > 0.5:
        return None
    tone, _, lock = strands(tx, 3, 1000, seed=5)
    rise = tx.up_from_bottom() + 0.5
    reach = depth / tx.texel * (0.1 + 0.62 * lock + 0.28 * tone)
    return rise >= reach


def fur(tx, pal):
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part', 'body')
    width, layer = LOCKS.get(part, (3, 6))
    tone, phase, lock = strands(tx, width, layer, seed=5)
    v = 0.50 + 0.13 * (tone - 0.5) + 0.08 * (lock - 0.5) + 0.08 * (0.5 - phase) - 0.09 * (phase > 1 - 1.0 / layer)
    v += 0.06 * (fbm(p, 7.0, seed=6, octaves=2) - 0.5)
    v += 0.04 * max(n[1], 0) - 0.05 * max(-n[1], 0)
    if part in ('skirt', 'fringe'):
        v -= 0.12 * np.clip(1.3 - tx.up_from_bottom() * tx.texel / max(tx.box.tag.get('hem', 2.0), 0.5), 0, 1)
    if part in ('crown', 'crest'):
        v += 0.04
    colour = shade(pal['fur'], v, tx.x, tx.y, dither=0.0)
    s = silver(p)
    if np.any(s > 0) and part in ('body', 'back', 'skirt', 'thigh', 'belly'):
        bright = shade(pal['silver'], v - 0.14 + 0.22 * s, tx.x, tx.y, dither=0.0)
        blend = s + 0.45 * (tone - 0.5) + 0.2 * (lock - 0.5) > 0.42
        colour = np.where(blend[..., None], bright, colour)
    if part == 'chest':                                             # the bare chest between the arms
        bare = (n[2] > 0.5) & (p[..., 1] < 31.5) & (np.abs(p[..., 0]) < 6.6 + 0.4 * (noise(p, 1.5, seed=62) - 0.5))
        if np.any(bare):
            colour = np.where(bare[..., None], skin(tx, pal, 0.52, pecs=True), colour)
    return colour


def skin(tx, pal, base, pecs=False):
    p, n = tx.points, tx.normal
    wrinkle = noise(p, (1.6 * tx.texel, 0.9 * tx.texel, 1.6 * tx.texel), seed=71)
    v = base + 0.16 * (wrinkle - 0.5) + 0.06 * max(n[1], 0) - 0.05 * max(-n[1], 0)
    if pecs:
        x, y = np.abs(p[..., 0]), p[..., 1]
        v = v + 0.10 * np.clip(1 - np.hypot(x - 3.4, y - 25.0) / 3.6, 0, 1)   # the two slabs of the chest
        v = v - 0.12 * ((x < 0.6) | (np.abs(y - 21.4) < 0.45))                 # the cleft and the line below
    return shade(pal['skin'], v, tx.x, tx.y, dither=0.0)


def face(tx, pal):
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    base = {'brow': 0.62, 'eyes': 0.30, 'bridge': 0.52, 'muzzle': 0.55, 'nose': 0.6, 'jaw': 0.5, 'lip': 0.44,
            'ear': 0.46}.get(part, 0.5)
    colour = skin(tx, pal, base)
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    if part == 'nose' and n[2] > 0.5:                                # two flared nostrils low on the nose
        nostril = (np.abs(np.abs(x) - 1.15) < 0.38) & (y < 32.75)
        colour = np.where(nostril[..., None], shade(pal['skin'], 0.02, tx.x, tx.y), colour)
        ridge = np.abs(x) < 0.4
        colour = np.where((ridge & (y > 33.2))[..., None], shade(pal['skin'], 0.72, tx.x, tx.y), colour)
        wing = (np.abs(np.abs(x) - 1.15) < 0.62) & (y < 33.1) & ~nostril  # the flared wings round each nostril
        colour = np.where(wing[..., None], shade(pal['skin'], 0.66, tx.x, tx.y), colour)
    if part == 'muzzle' and n[2] > 0.5:                              # the upper lip's line
        colour = np.where((y < 31.3)[..., None], shade(pal['skin'], 0.28, tx.x, tx.y), colour)
    if part == 'jaw' and n[1] > 0.5:                                 # inside the mouth
        colour = shade(MOUTH_RAMP, 0.55 + 0.3 * (noise(p, tx.texel, seed=72) - 0.5), tx.x, tx.y)
    if part == 'muzzle' and n[1] < -0.5:
        colour = shade(MOUTH_RAMP, 0.3, tx.x, tx.y)
    if part == 'brow' and n[1] > 0.5:                                # hair on top of the brow
        colour = fur(tx, pal)
    if part == 'eyes' and abs(n[0]) > 0.5:
        colour = fur(tx, pal)
    return colour


def hand(tx, pal):
    p, n = tx.points, tx.normal
    part = tx.box.tag.get('part')
    colour = skin(tx, pal, 0.46)
    if part == 'palm':
        # hair on the back of the hand down to the knuckles; the palm's inner face bare
        furry = p[..., 1] > 6.2 + 0.8 * (noise(p, (2 * tx.texel, 1, 2 * tx.texel), seed=73) - 0.5)
        colour = np.where(furry[..., None], fur(tx, pal), colour)
    if tx.box.tag.get('nail'):
        tip = (p - tx.box.center) @ tx.box.axes[:, 1] > tx.box.size[1] * 0.18
        colour = np.where(tip[..., None], shade(NAIL_RAMP, 0.6, tx.x, tx.y), colour)
    return colour


def painter(tx, variant):
    pal = PALETTES[variant]
    mat = tx.box.mat
    alpha = hair_alpha(tx)
    if mat == 'fur':
        rgb = fur(tx, pal)
    elif mat == 'face':
        rgb = face(tx, pal)
    elif mat in ('hand', 'foot'):
        rgb = hand(tx, pal)
        if mat == 'foot' and tx.box.tag.get('part') == 'sole' and tx.normal[1] > 0.5:
            rgb = fur(tx, pal)
    elif mat == 'tooth':
        v = 0.7 + 0.25 * (noise(tx.points, tx.texel, seed=74) - 0.5) - 0.2 * max(-tx.normal[1], 0)
        rgb = shade(TOOTH_RAMP, v, tx.x, tx.y)
    else:
        rgb = skin(tx, pal, 0.5)
    return rgb, alpha
