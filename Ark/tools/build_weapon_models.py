"""3D in-hand models for the authored weapons: Keratin Spear, Bronze Longsword and Bronze Hammer.

Each weapon is built upright from cuboids (pommel or butt at the bottom, y up, centred on x = z = 8), then every
element is turned 45 degrees about the model centre onto the item diagonal its sprite uses: the longsword and the
hammer lean to the top right like vanilla handhelds, the spear to the top left like the vanilla spears. So the
vanilla display transforms apply unchanged (item/handheld, item/spear_in_hand), and the proportions follow the
32 px sprites (tools/build_bronze_age_art.py, tools/build_keratin_items.py). Inventory, ground and frames keep
the flat sprite; the item definitions (datagen, BronzeData and PrimitiveData) pick the 3D model everywhere else.

Materials are 32x32 textures sampled one model unit per two texels, the density of the 32 px item sprites, with
fixed per-texel noise on the same ramps as the sprites. Deterministic.

Writes:
  assets/arksurvivalreturns/models/item/<id>_3d.json
  assets/arksurvivalreturns/textures/item/weapon/<material>.png

Run from Ark: python tools/build_weapon_models.py
"""
import json
from pathlib import Path

from PIL import Image

from build_bronze_age_art import BRONZE, LEATHER, WOOD, ramp_fn, rgb
from build_machine_assets import face_uvs, hash2

ARK = Path(__file__).resolve().parents[1]
ASSETS = ARK / 'src/main/resources/assets/arksurvivalreturns'
NS = 'arksurvivalreturns'

# Ramps, darkest first. Keratin, shaft and ochre wrap are sampled from the keratin spear sprite.
RAMPS = {
    'bronze': BRONZE[1:7],
    'bronze_edge': BRONZE[4:],
    'bronze_dark': BRONZE[:5],
    'leather': ['#%02x%02x%02x' % c for c in LEATHER],
    'wood': ['#%02x%02x%02x' % c for c in WOOD[:4]],
    'keratin': ['#221b15', '#584634', '#826849', '#b3956a', '#d9c296'],
    'shaft': ['#281e0b', '#493615', '#684e1e', '#896727'],
    'ochre': ['#411805', '#67290b', '#834121', '#a34b22'],
    'fiber': ['#566030', '#768840', '#9aa85a', '#b0ba68'],
}


def texel(mat, x, y):
    """A step on the material ramp from clustered noise; wood grain runs along v, wraps run diagonally."""
    s = sum(map(ord, mat))
    v = 0.55 * hash2(x, y, s) + 0.3 * hash2(x >> 1, y >> 2, s + 1) + 0.15 * hash2(x >> 3, y >> 3, s + 2)
    if mat in ('wood', 'shaft'):
        v = 0.6 * hash2(x, y >> 3, s) + 0.4 * hash2(x, y, s + 3)       # long grain
    if mat in ('leather', 'ochre', 'fiber'):
        v = v * 0.6 + (0.4 if (x + y) % 4 < 2 else 0.0)                 # the wrap
    if mat.startswith('bronze') and (x + 2 * y) % 9 == 0:
        v = min(1.0, v + 0.25)                                          # hammer marks catch the light
    ramp = RAMPS[mat]
    t = (0.3 + 0.5 * v) if mat.startswith('bronze') else (0.15 + 0.7 * v)
    return ramp_fn(ramp)(t)


def texture(mat):
    image = Image.new('RGBA', (32, 32))
    px = image.load()
    for y in range(32):
        for x in range(32):
            px[x, y] = texel(mat, x, y) + (255,)
    return image


def bar(y1, y2, w, d, mat, cx=8.0, cz=8.0):
    """An upright cuboid centred on (cx, cz): w wide in x, d deep in z."""
    return [round(cx - w / 2, 3), round(y1, 3), round(cz - d / 2, 3),
            round(cx + w / 2, 3), round(y2, 3), round(cz + d / 2, 3), mat]


def longsword():
    """Pommel, wrapped grip, rolled crossguard, ricasso, a fullered blade with bright edges, the point."""
    return [
        bar(-1.8, -0.1, 1.8, 1.8, 'bronze'), bar(-1.5, -0.4, 2.2, 1.2, 'bronze_dark'),
        bar(-1.5, -0.4, 1.2, 2.2, 'bronze_dark'),                                  # faceted pommel
        bar(-0.1, 3.8, 1.2, 1.2, 'leather'),
        *[bar(y, y + 0.4, 1.4, 1.4, 'leather') for y in (0.4, 1.6, 2.8)],        # wrap ridges
        bar(3.8, 4.6, 5.0, 1.4, 'bronze'),                                        # crossguard
        bar(3.6, 4.8, 0.6, 1.8, 'bronze_dark', cx=5.8), bar(3.6, 4.8, 0.6, 1.8, 'bronze_dark', cx=10.2),
        bar(4.6, 5.6, 1.9, 0.9, 'bronze_dark'),                                   # ricasso
        bar(5.6, 16.4, 1.8, 0.5, 'bronze'),                                       # blade
        bar(5.9, 15.2, 0.5, 0.56, 'bronze_dark'),                                 # fuller
        bar(6.0, 16.4, 0.3, 0.4, 'bronze_edge', cx=7.2), bar(6.0, 16.4, 0.3, 0.4, 'bronze_edge', cx=8.8),
        bar(16.4, 17.1, 1.2, 0.45, 'bronze_edge'), bar(17.1, 17.6, 0.5, 0.4, 'bronze_edge'),  # point
    ]


def war_hammer():
    """Butt cap, wrapped haft, socket langets and a flared, two-faced head."""
    return [
        bar(-1.9, -0.8, 1.6, 1.6, 'bronze'),                                      # butt cap
        bar(-0.8, 11.4, 1.1, 1.1, 'wood'),                                        # haft
        bar(-0.2, 3.4, 1.35, 1.35, 'leather'),                                    # grip wrap
        bar(9.6, 11.4, 1.5, 1.5, 'bronze_dark'),                                  # socket langets
        bar(11.4, 14.6, 6.0, 2.8, 'bronze'),                                      # head
        bar(11.1, 14.9, 0.8, 3.4, 'bronze_edge', cx=4.6), bar(11.1, 14.9, 0.8, 3.4, 'bronze_edge', cx=11.4),
        bar(14.6, 15.0, 4.4, 2.4, 'bronze_edge'),                                 # upper face
        bar(12.4, 13.6, 1.2, 3.0, 'bronze_dark'),                                 # collar rivet band
    ]


def spear():
    """Butt cap, shaft, ochre-dyed hide wrap, fiber binding and a leaf-shaped keratin head with a mid-ridge."""
    return [
        bar(-3.0, -2.4, 1.0, 1.0, 'shaft'),
        bar(-2.4, 13.4, 0.8, 0.8, 'shaft'),
        bar(3.0, 7.0, 1.0, 1.0, 'ochre'),                                         # grip wrap
        bar(12.2, 13.8, 1.1, 1.1, 'fiber'),                                       # binding
        bar(13.4, 14.2, 1.3, 1.0, 'keratin'),                                     # socket
        bar(14.2, 15.2, 2.2, 0.7, 'keratin'), bar(15.2, 16.6, 2.6, 0.7, 'keratin'),
        bar(16.6, 17.8, 1.8, 0.7, 'keratin'), bar(17.8, 18.7, 1.0, 0.6, 'keratin'),
        bar(18.7, 19.3, 0.4, 0.5, 'keratin'),                                     # the leaf, tapering
        bar(14.2, 18.4, 0.5, 1.0, 'keratin'),                                     # mid-ridge
    ]


HANDHELD = {
    'thirdperson_righthand': {'rotation': [0, -90, 55], 'translation': [0, 4.0, 0.5], 'scale': [0.85, 0.85, 0.85]},
    'thirdperson_lefthand': {'rotation': [0, 90, -55], 'translation': [0, 4.0, 0.5], 'scale': [0.85, 0.85, 0.85]},
    'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
}
SPEAR_IN_HAND = {
    'firstperson_righthand': {'rotation': [-20, 90, -35], 'translation': [3.13, 2.0, 0.13], 'scale': [1.36, 1.36, 0.68]},
    'firstperson_lefthand': {'rotation': [-20, -90, 35], 'translation': [3.13, 2.0, 0.13], 'scale': [1.36, 1.36, 0.68]},
    'thirdperson_righthand': {'rotation': [5, 270, -40], 'translation': [0, 2, 2], 'scale': [1.7, 1.7, 0.85]},
    'thirdperson_lefthand': {'rotation': [5, -270, 40], 'translation': [0, 2, 2], 'scale': [1.7, 1.7, 0.85]},
}
# id -> (boxes, lean in degrees about z: negative leans to the top right, display transforms of the sprite)
WEAPONS = {
    'bronze_longsword': (longsword, -45, HANDHELD),
    'bronze_hammer': (war_hammer, -45, HANDHELD),
    'keratin_spear': (spear, 45, SPEAR_IN_HAND),
}


def model(boxes, lean, display):
    elements = []
    for b in boxes:
        elements.append({'from': b[0:3], 'to': b[3:6],
                         'rotation': {'origin': [8, 8, 8], 'axis': 'z', 'angle': lean},
                         'faces': {f: {'uv': list(uv), 'texture': '#' + b[6]} for f, uv in face_uvs(b).items()}})
    mats = sorted({b[6] for b in boxes})
    textures = {m: f'{NS}:item/weapon/{m}' for m in mats} | {'particle': f'{NS}:item/weapon/{mats[0]}'}
    return {'gui_light': 'front', 'textures': textures, 'elements': elements, 'display': display}


def main():
    written = []
    for folder in ('textures/item/weapon', 'models/item'):
        (ASSETS / folder).mkdir(parents=True, exist_ok=True)
    for mat in RAMPS:
        path = ASSETS / 'textures/item/weapon' / f'{mat}.png'
        texture(mat).save(path)
        written.append(path)
    for name, (build, lean, display) in WEAPONS.items():
        path = ASSETS / 'models/item' / f'{name}_3d.json'
        path.write_text(json.dumps(model(build(), lean, display), indent=1) + '\n', encoding='utf-8')
        written.append(path)
    print(f'Wrote {len(written)} files:')
    for path in written:
        print('  ' + path.relative_to(ARK).as_posix())


if __name__ == '__main__':
    main()
