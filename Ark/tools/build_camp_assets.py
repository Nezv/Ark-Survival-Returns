"""Author the camp collection as vanilla cuboid models and original pixel materials.

Run from any directory: python tools/build_camp_assets.py
Geometry lives under models/block/camp; ArkData owns blockstate/item/recipe wiring.
No third-party art, client renderer or runtime model loader is required.
"""
import copy
import json
import math
import random
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/arksurvivalreturns'
MODELS = ASSETS / 'models/block/camp'
TEXTURES = ASSETS / 'textures/block/camp'
WOODS = {
    'oak': (156, 117, 67), 'spruce': (105, 76, 48),
    'birch': (203, 184, 130), 'jungle': (164, 112, 80),
    'acacia': (170, 91, 54), 'dark_oak': (76, 53, 35),
    'mangrove': (119, 58, 49), 'cherry': (205, 151, 137),
    'pale_oak': (216, 210, 183), 'bamboo': (182, 162, 76),
}
PALETTE = {
    'canvas': (105, 117, 76), 'canvas_light': (137, 146, 98),
    'linen': (207, 192, 152), 'leather': (99, 62, 40),
    'rope': (169, 140, 88), 'iron': (65, 65, 59),
    'iron_edge': (96, 96, 82), 'soot': (41, 40, 36),
    'resin': (135, 83, 34), 'meat': (156, 68, 52),
    'fat': (211, 161, 122), 'dried': (99, 52, 34),
    'fish': (121, 144, 134), 'berry': (115, 57, 62),
    'grain': (174, 141, 66),
}


def material(name, base, wood=False):
    rng = random.Random(name)
    im = Image.new('RGB', (32, 32))
    px = im.load()
    for y in range(32):
        for x in range(32):
            weave = ((x % 2) - (y % 2)) * 2 if name in ('canvas', 'canvas_light', 'linen') else 0
            grain = 5 * math.sin(y * 1.4) if wood else 0
            noise = rng.choice((-3, -2, 0, 0, 1, 2, 3))
            px[x, y] = tuple(max(0, min(255, round(c + weave + grain + noise))) for c in base)
    d = ImageDraw.Draw(im)
    dark = tuple(max(0, c - 21) for c in base)
    light = tuple(min(255, c + 18) for c in base)
    if wood:
        for y in (4, 11, 20, 28):
            x = rng.randrange(2, 15)
            d.line([(x, y), (x + 7, y), (x + 10, y + 1), (min(31, x + 18), y + 1)], fill=dark)
            d.line((x + 3, y + 2, min(31, x + 14), y + 2), fill=light)
        d.ellipse((19, 14, 26, 17), outline=dark)
        d.line((21, 15, 24, 15), fill=dark)
    elif name in ('canvas', 'canvas_light', 'linen', 'leather'):
        for x in range(1, 32, 4):
            d.line((x, 1, x + 1, 1), fill=light)
            d.line((x, 30, x + 1, 30), fill=light)
    elif name == 'rope':
        for x in range(-30, 33, 5):
            d.line((x, 0, x + 31, 31), fill=dark)
            d.line((x + 1, 0, x + 32, 31), fill=light)
    elif name in ('meat', 'dried', 'fish'):
        for y in (5, 14, 23):
            d.line([(0, y), (9, y + 2), (18, y), (31, y + 3)], fill=light, width=1)
    elif name in ('iron', 'iron_edge'):
        for _ in range(12):
            x, y = rng.randrange(30), rng.randrange(30)
            d.line((x, y, x + 1, y), fill=light)
    im.save(TEXTURES / (name + '.png'))


def box(a, b, tex, name='', rotation=None):
    x, y, z = a
    X, Y, Z = b
    dimensions = {'up': (X-x, Z-z), 'down': (X-x, Z-z),
                  'north': (X-x, Y-y), 'south': (X-x, Y-y),
                  'east': (Z-z, Y-y), 'west': (Z-z, Y-y)}
    result = {'name': name or tex, 'from': a, 'to': b, 'faces': {
        face: {'uv': [0, 0, max(.25, w), max(.25, h)], 'texture': '#' + tex}
        for face, (w, h) in dimensions.items()}}
    if rotation:
        result['rotation'] = rotation
    return result


def rot(origin, axis, angle):
    return {'origin': origin, 'axis': axis, 'angle': angle, 'rescale': False}


def lift(elements, axis, by):
    """Copies of elements moved along one axis (x, y or z) by model units."""
    i = 'xyz'.index(axis)
    moved = copy.deepcopy(elements)
    for el in moved:
        el['from'][i] += by
        el['to'][i] += by
        if 'rotation' in el:
            el['rotation']['origin'][i] += by
    return moved


def split(elements, axis='y'):
    """Cut a two-block design at the block boundary (16): (first block, second block moved back to 0-16).

    Each half then stays inside its own block, so every face keeps a 0-16 UV and the lighting of its block.
    """
    i = 'xyz'.index(axis)
    first, second = [], []
    for el in elements:
        a, b = el['from'], el['to']
        if b[i] <= 16:
            first.append(copy.deepcopy(el))
            continue
        if a[i] >= 16:
            second += lift([el], axis, -16)
            continue
        r = el.get('rotation')
        assert not r or r['axis'] == axis, (el['name'], 'a rotated element crosses the block boundary')
        tex = next(iter(el['faces'].values()))['texture'][1:]
        low, high = list(b), list(a)
        low[i] = high[i] = 16
        first.append(box(list(a), low, tex, el['name'], copy.deepcopy(r)))
        second += lift([box(high, list(b), tex, el['name'], copy.deepcopy(r))], axis, -16)
    return first, second


DISPLAY = {
    'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [.8, .8, .8]},
    'ground': {'translation': [0, 3, 0], 'scale': [.45, .45, .45]},
    'fixed': {'rotation': [0, 180, 0], 'scale': [.6, .6, .6]},
    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [.4, .4, .4]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 2, 0], 'scale': [.5, .5, .5]},
    'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 2, 0], 'scale': [.5, .5, .5]},
}
STONE_SEAT = 5.1  # top of the stone fire's third course; CookingPotBlock.STONE_SEAT
# Two-block-tall stations: the item model stacks both halves (0-32), so it is scaled and lowered to fit.
TALL_DISPLAY = {
    'gui': {'rotation': [30, 225, 0], 'translation': [0, -3.4, 0], 'scale': [.42, .42, .42]},
    'ground': {'translation': [0, 1.5, 0], 'scale': [.25, .25, .25]},
    'fixed': {'rotation': [0, 180, 0], 'translation': [0, -3.2, 0], 'scale': [.4, .4, .4]},
    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 1, 0], 'scale': [.22, .22, .22]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, -1, 0], 'scale': [.28, .28, .28]},
    'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, -1, 0], 'scale': [.28, .28, .28]},
}


def save(name, elements, substitutions=None, display=DISPLAY):
    keys = {f['texture'][1:] for e in elements for f in e['faces'].values()}
    textures = {key: 'arksurvivalreturns:block/camp/' + (substitutions or {}).get(key, key) for key in sorted(keys)}
    textures['particle'] = textures.get('wood', next(iter(textures.values())))
    model = {'parent': 'minecraft:block/block', 'textures': textures,
             'display': display, 'elements': elements}
    (MODELS / (name + '.json')).write_text(json.dumps(model, indent=2) + '\n', encoding='utf8')


def field_bedroll():
    """Iron Age Field Bedroll: reserved art (2 x 2 blocks, 12/16 tall), not registered in game yet.

    Canvas bedding on a low timber frame with iron corner brackets, split into four quarter models
    (head/foot x west/east) ready for a four-block bed.
    """
    e = []
    for x in (1, 29):
        for z in (1, 29):
            e += [box([x, 0, z], [x+2, 5, z+2], 'wood', 'frame leg'),
                  box([x-.2, 4, z-.2], [x+2.2, 6.2, z+2.2], 'iron', 'iron corner bracket')]
    for z in (1, 29):
        e.append(box([3, 4.5, z], [29, 6.5, z+2], 'wood', 'side rail'))
    for x in (1, 29):
        e.append(box([x, 4.5, 3], [x+2, 6.5, 29], 'wood', 'end rail'))
    e += [box([1.5, 6.5, 1.5], [30.5, 7.2, 30.5], 'leather', 'waterproof groundsheet'),
          box([2, 7.2, 2], [30, 9, 30], 'linen', 'padded sleeping mat'),
          box([2.4, 9, 9.5], [29.6, 10.4, 29.6], 'canvas', 'sage blanket'),
          box([3.2, 10.4, 11.2], [28.8, 10.8, 28.4], 'canvas', 'soft raised centre'),
          box([2.3, 10.2, 9.4], [29.7, 10.9, 11.2], 'linen', 'turned linen cuff'),
          box([2.3, 8.9, 27.8], [29.7, 11, 30], 'canvas_light', 'soft foot fold')]
    for x in (3.5, 16.5):
        e += [box([x, 9, 2.6], [x+12, 11.2, 8.4], 'linen', 'camp pillow'),
              box([x+.7, 11.2, 3.2], [x+11.3, 11.8, 7.8], 'linen', 'pillow crown')]
    for x in (2.2, 29.4):
        e.append(box([x, 8.9, 11.4], [x+.4, 10.5, 27.6], 'canvas_light', 'bound blanket edge'))
    for x in (7, 24):
        e += [box([x, 6.2, .8], [x+1, 9.4, 1.5], 'leather', 'packing tie'),
              box([x, 6.2, 30.5], [x+1, 9.4, 31.2], 'leather', 'foot tie')]
    west, east = split(e, 'x')
    for side, half in (('west', west), ('east', east)):
        head, foot = split(half, 'z')
        save(f'field_bedroll_head_{side}', head, {'wood': 'dark_oak'})
        save(f'field_bedroll_foot_{side}', foot, {'wood': 'dark_oak'})
    # Inventory reads as a portable rolled blanket with two leather straps.
    e = [box([2, 4, 5], [14, 10, 11], 'canvas'),
         box([2, 5, 4], [14, 9, 12], 'canvas'),
         box([2, 3.6, 6], [14, 10.4, 10], 'canvas_light')]
    for x in (1.95, 13.9):
        e += [box([x, 5, 5.4], [x + .15, 9, 10.6], 'linen'),
              box([x - .03, 6, 6.3], [x + .18, 8.3, 9.5], 'canvas'),
              box([x - .06, 6.8, 7], [x + .21, 7.7, 8.8], 'linen')]
    for x in (4, 10.8):
        for a, b in (([x, 3.4, 5.8], [x+1.2, 10.6, 10.2]),
                     ([x, 4.8, 3.8], [x+1.2, 9.2, 12.2])):
            e.append(box(a, b, 'leather'))
        e.append(box([x+.15, 6, 12.2], [x+1.05, 7.25, 12.45], 'resin', 'wooden toggle'))
    e += [box([5, 10.6, 7.4], [11, 11.3, 8.5], 'leather', 'carrying handle')]
    save('field_bedroll_rolled', e)


def trough():
    e = []
    for x in (2, 12):
        e.append(box([x, 0, 3.25], [x+2, 2, 12.75], 'wood', 'runner foot'))
    for z in (5, 7, 9):
        e.append(box([1, 2, z], [15, 3.25, z+1.95], 'wood', 'floor plank'))
    for z in (3.5, 10.75):
        e += [box([.5, 3, z], [15.5, 6.75, z+1.75], 'wood', 'trough side'),
              box([.25, 6.75, z-.25], [15.75, 7.5, z+2], 'wood', 'worn top lip'),
              box([1.8, 3, z-.08], [2.4, 6.75, z+1.83], 'resin', 'sealed join'),
              box([13.6, 3, z-.08], [14.2, 6.75, z+1.83], 'resin', 'sealed join')]
    for x in (.6, 13.9):
        e.append(box([x, 3, 5.25], [x+1.5, 7.1, 10.75], 'wood', 'end board'))
    for x in (3, 12.2):
        for z in (3.38, 12.52):
            e.append(box([x, 5.4, z], [x+.7, 6.1, z+.12], 'leather', 'wooden peg'))
    for wood in WOODS:
        save('trough_' + wood, e, {'wood': wood})
    save('trough_feed', [box([2.15, 3.3, 5.3], [13.85, 4.4, 10.7], 'grain'),
                         box([4, 4.4, 6.1], [10.5, 4.9, 9.5], 'grain')])


def rack():
    """Two blocks tall: rations cure on the bottom shelf, food hangs from the top lines."""
    e = []
    for x in (1, 13.25):
        for z in (3, 11.25):
            e.append(box([x, 0, z], [x+1.75, 31, z+1.75], 'wood', 'upright'))
            e.append(box([x+.2, 31, z+.2], [x+1.55, 31.6, z+1.55], 'wood', 'trimmed post top'))
            for y in (2, 15, 27):
                e.append(box([x-.15, y, z-.15], [x+1.9, y+1, z+1.9], 'rope', 'lashed joint'))
    for y in (1, 15.5, 28):
        for z in (3.25, 11.5):
            e.append(box([1, y, z], [15, y+1.5, z+1.25], 'wood', 'cross rail'))
    for x in (1.2, 13.5):
        e.append(box([x, 28, 3], [x+1.3, 29.5, 13], 'wood', 'side rail'))
        e.append(box([x, 14.5, 4.75], [x+1.3, 15.5, 11.25], 'wood', 'side brace'))
    # Air gaps below the finished food; stacked racks stay separate working units.
    for x in (3, 5.5, 8, 10.5, 13):
        e.append(box([x, 1.8, 3.4], [x+.9, 2.4, 12.6], 'wood', 'slatted shelf'))
    for z in (5.6, 9.6):
        e.append(box([2.75, 27.7, z], [13.25, 28.2, z+.4], 'rope', 'drying line'))
    lower, upper = split(e)
    save('drying_rack_lower', lower, {'wood': 'spruce'})
    save('drying_rack_upper', upper, {'wood': 'spruce'})
    save('drying_rack_item', lower + lift(upper, 'y', 16), {'wood': 'spruce'}, TALL_DISPLAY)
    # Hanging food belongs to the lower half's multipart; its elements reach up to the lines (below 32).
    for food in ('meat', 'fish', 'berries'):
        for slot, x in enumerate((4, 8, 12), 1):
            e = [box([x-.16, 24.5, 5.6], [x+.16, 28.1, 6], 'rope', 'hanging tie')]
            if food == 'meat':
                e += [box([x-.85, 20, 5.1], [x+.85, 25.8, 6.5], 'meat'),
                      box([x-.5, 19.1, 5.2], [x+.6, 20, 6.4], 'meat'),
                      box([x-.6, 21, 5.04], [x-.23, 25.1, 5.1], 'fat'),
                      box([x+.3, 20.2, 6.5], [x+.6, 24.1, 6.56], 'fat')]
            elif food == 'fish':
                e += [box([x-.7, 20.1, 5.2], [x+.7, 24.7, 6.3], 'fish'),
                      box([x-.35, 19.3, 5.3], [x+.35, 20.1, 6.2], 'fish'),
                      box([x-1, 24.7, 5.35], [x+1, 25.5, 6.15], 'fish'),
                      box([x-.3, 19.85, 5.12], [x, 20.15, 5.2], 'soot')]
            else:
                e += [box([x-.9, 22, 5], [x+.9, 24.6, 6.8], 'rope', 'woven berry pouch'),
                      box([x-.65, 24.6, 5.2], [x+.65, 25, 6.5], 'berry')]
            save(f'rack_{food}_{slot}', e)
    save('rack_ready', [box([x, 2.45, z], [x+2.8, 3, z+1.2], 'dried', 'finished ration')
                        for x, z in ((3, 8), (7, 9), (10, 7.7))])


def pot():
    e = []
    # A hollow eight-sided iron body. Four angled wall elements soften the silhouette.
    e.append(box([4, 1.5, 4], [12, 2.5, 12], 'soot', 'heavy base'))
    for axis in ('x', 'z'):
        for side in (3, 12):
            a, b = ([5, 2.5, side], [11, 8, side+1]) if axis == 'x' else ([side, 2.5, 5], [side+1, 8, 11])
            e.append(box(a, b, 'iron', 'hammered wall'))
    for x, z, angle in ((4.5, 4.5, -45), (11.5, 4.5, 45), (4.5, 11.5, 45), (11.5, 11.5, -45)):
        e.append(box([x-1.5, 2.5, z-.5], [x+1.5, 8, z+.5], 'iron', 'chamfered corner', rot([x, 5, z], 'y', angle)))
    for a, b in (([4.6, 8, 2.8], [11.4, 8.7, 4.2]), ([4.6, 8, 11.8], [11.4, 8.7, 13.2]),
                 ([2.8, 8, 4.6], [4.2, 8.7, 11.4]), ([11.8, 8, 4.6], [13.2, 8.7, 11.4])):
        e.append(box(a, b, 'iron_edge', 'rolled rim'))
    for x, z, angle in ((4.5, 4.5, -45), (11.5, 4.5, 45), (4.5, 11.5, 45), (11.5, 11.5, -45)):
        e.append(box([x-1.6, 8, z-.6], [x+1.6, 8.7, z+.6], 'iron_edge', 'rim corner', rot([x, 8, z], 'y', angle)))
    for x in (1.2, 13.8):
        e += [box([x, 6.3, 5.7], [x+1, 7, 10.3], 'iron_edge', 'loop handle'),
              box([x if x<8 else 12.5, 6.3, 5.7], [3.5 if x<8 else x+1, 7, 6.4], 'iron_edge'),
              box([x if x<8 else 12.5, 6.3, 9.6], [3.5 if x<8 else x+1, 7, 10.3], 'iron_edge')]
    # Short feet rest on the campfire log surface rather than floating one block above it.
    for x, z in ((4, 5), (11, 5), (7.5, 11)):
        e.append(box([x, 0, z], [x+1, 2, z+1], 'soot', 'trivet foot'))
    save('cooking_pot', e)
    mounted = copy.deepcopy(e)
    for part in mounted:
        if part['name'] == 'trivet foot':
            part['from'][1] = -9
    mounted += [box([3.5, .2, 4.5], [12.5, 1, 5.5], 'iron', 'trivet crossbar'),
                box([7.5, .2, 5], [8.5, 1, 12], 'iron', 'trivet crossbar')]
    save('cooking_pot_campfire', mounted)
    # Over a stone fire the pot sits down on the top course (5.1 in the fire's block): no trivet.
    seated = lift([part for part in e if part['name'] != 'trivet foot'], 'y', STONE_SEAT - 2.5 - 16)
    save('cooking_pot_stones', seated)


def main():
    MODELS.mkdir(parents=True, exist_ok=True)
    TEXTURES.mkdir(parents=True, exist_ok=True)
    for stale in MODELS.glob('*.json'):
        stale.unlink()
    for name, color in WOODS.items():
        material(name, color, wood=True)
    for name, color in PALETTE.items():
        material(name, color)
    field_bedroll()
    trough()
    rack()
    pot()
    print(f'Authored {len(list(MODELS.glob("*.json")))} models and {len(list(TEXTURES.glob("*.png")))} original materials.')


if __name__ == '__main__':
    main()
