"""Iron Age art: planned item sprites and the tech tree icons of the four Iron lanes.

Survey (map, radar, target, completionist), Power (wires, generator, electric bat, shock therapy), Steel kit
(steel armour, backpack, specialized boots, rifle) and Machine parts (engine, metal sheet, pipe, screw).
The steel armour sprites and the Harder icon are drawn from the worn steel models by build_armour_sprites.py.

Same method as build_bronze_age_art.py: hand-pixelled on a 16 px grid (the bat and the rifle on the 32 px
diagonal like the Bronze Longsword), doubled to the 32 px Ark item size; tech icons are 64 px. The items are
not registered yet: the sprites sit at the runtime paths of their planned ids (Dashboard F17), so the
implementation only has to register them. The generator is a block, so it only gets its icon here.
Deterministic: every output is a pure function of this file and the Minecraft jar.

Run from Ark: python tools/build_iron_age_art.py
"""
import math

from PIL import Image

import build_armour_sprites as armour_sprites
from build_bronze_age_art import (BRASS, ICONS, LEATHER, STEEL, TEX, WOOD, WRITTEN, axis_sprite, cartridges,
                                  glow, icon, ingot, item32, luminance, outline, paint, ramp_fn, retone, rgb,
                                  save, sparkle, vanilla)

INK = (18, 20, 26, 255)
COPPER = ['#3a1a0a', '#6e3416', '#a4542a', '#cf7a44', '#eba36c', '#fbd0a2']
# Steel ramp by digit, 0 (the slits) to 7 (a specular glint above the Bronze Age STEEL ramp).
PLATE = {str(i): rgb(c) for i, c in enumerate(STEEL)} | {'7': (226, 235, 242)}


# ------------------------------------------------------------------------------------------ steel kit

def backpack():
    """A leather rucksack: rolled canvas bedroll on top, buckled flap, side pockets, shoulder straps."""
    return outline(paint(["................",
                          "..cCCCCCCCCCCc..",
                          ".cCsCCCsCCCsCCc.",
                          "..cccccccccccc..",
                          "...kLLLLLLLLk...",
                          "..pkmmmmmmmmkp..",
                          ".ppLmmmmmmmmLpp.",
                          ".pPLmsmmmmsmLPp.",
                          ".pPLLbLLLLbLLPp.",
                          ".pPLLlLLLLlLLPp.",
                          ".ppLLLLLLLLLLpp.",
                          ".pPLLLLLLLLLLPp.",
                          "..pLLLLLLLLLLp..",
                          "...kkkkkkkkkk...",
                          "................",
                          "................"],
                         {'c': (98, 104, 70), 'C': (140, 146, 100), 's': (70, 52, 30),
                          'k': LEATHER[0], 'l': LEATHER[1], 'L': LEATHER[2], 'm': LEATHER[3],
                          'p': (92, 58, 36), 'P': (120, 80, 50), 'b': rgb(BRASS[3])}), INK)


def specialized_boots():
    """A laced leather runner in side view: steel toe cap and shin plate, brass buckles, a coiled heel spring."""
    return outline(paint(["................",
                          ".....LLLLL......",
                          ".....4mmmL......",
                          ".....45mmbL.....",
                          ".....45mmLL.....",
                          ".....45mmbL.....",
                          ".....45mmLL.....",
                          ".....45mmbL.....",
                          ".....4mmmLLL....",
                          "....LmmmmLwLL...",
                          "....LmmmmmwLLL..",
                          "....LLLLLLLL456.",
                          "....kkkkkkkk345.",
                          ".....sss........",
                          ".....SSS........",
                          ".....sss........"],
                         {'L': LEATHER[2], 'm': LEATHER[3], 'k': LEATHER[0], 'w': (220, 206, 170),
                          'b': rgb(BRASS[3]), '3': PLATE['3'], '4': PLATE['4'], '5': PLATE['5'], '6': PLATE['6'],
                          's': PLATE['5'], 'S': PLATE['2']}), INK)


def rifle():
    """A bolt-action rifle on the item diagonal: walnut stock and forend, blued receiver and barrel."""
    wood = [(64, 36, 18), (102, 58, 28), (138, 84, 40), (170, 112, 58)]
    blue = ramp_fn(STEEL)

    def shade(a, b):
        if 2 <= a <= 3 and -2 <= b <= 5:  # rubber butt plate
            return (40, 34, 30)
        if 4 <= a <= 13 and -2 <= b <= 5 - (a - 4) // 3:  # butt, deepest at the heel
            return wood[3 if b < 0 else 2 if b < 2 else 1]
        if 14 <= a <= 18 and -1 <= b <= 1:  # wrist
            return wood[3 if b < 0 else 2]
        if 19 <= a <= 29 and -1 <= b <= 1:  # receiver
            return blue(0.75 if b < 0 else 0.5 if b == 0 else 0.3)
        if 21 <= a <= 22 and -3 <= b <= -2:  # bolt handle and its ball
            return blue(0.9 if b == -3 else 0.6)
        if 20 <= a <= 24 and 2 <= b <= 3 and not (21 <= a <= 23 and b == 2):  # trigger guard
            return blue(0.35)
        if a == 22 and b == 2:  # trigger
            return blue(0.6)
        if 30 <= a <= 46 and 0 <= b <= 1:  # forend under the barrel
            return wood[2 if b == 0 else 1]
        if 30 <= a <= 60 and b == -1:  # barrel
            return blue(0.85 if a % 7 else 0.6)
        if 47 <= a <= 60 and b == 0:
            return blue(0.45)
        if a == 59 and b == -2:  # front sight
            return blue(0.7)
        if 35 <= a <= 36 and b == 2:  # sling swivel
            return blue(0.5)
        return None
    return outline(axis_sprite(shade), INK)


# ------------------------------------------------------------------------------------------ power

def copper_wire():
    """A wooden spool wound with bare copper wire, the loose end curling off to the right."""
    image = Image.new('RGBA', (16, 16))
    px = image.load()
    wood = ramp_fn(['#3c2610', '#6a4520', '#98683a', '#c09460', '#dcb888'])
    copper = ramp_fn(COPPER)
    for y in range(16):
        for x in range(16):
            top = ((x + 0.5 - 8) / 6.0) ** 2 + ((y + 0.5 - 3) / 2.0) ** 2
            bottom = ((x + 0.5 - 8) / 6.0) ** 2 + ((y + 0.5 - 12) / 2.0) ** 2
            if top <= 1:
                hole = ((x + 0.5 - 8) / 1.6) ** 2 + ((y + 0.5 - 3) / 0.8) ** 2 <= 1
                px[x, y] = (wood(0.05) if hole else wood(0.85 - 0.3 * max(0, x - 8) / 6)) + (255,)
            elif 3 <= x <= 12 and 4 <= y <= 11:
                profile = 1 - abs(x + 0.5 - 6.5) / 6.5  # the cylinder's highlight sits left of centre
                t = 0.15 + 0.75 * profile - (0.18 if (y + x // 4) % 2 else 0)
                px[x, y] = copper(t) + (255,)
            elif bottom <= 1:
                px[x, y] = wood(0.55 if y <= 12 else 0.3) + (255,)
    for x, y in ((13, 7), (14, 7), (15, 8), (15, 9), (14, 10), (15, 11)):
        px[x, y] = copper(0.8 if y < 9 else 0.5) + (255,)
    return outline(image, INK)


def electric_bat():
    """A hardwood bat wound with copper wire, steel end cap and two electrode prongs, on the item diagonal."""
    wood = [(84, 56, 26), (130, 92, 46), (176, 134, 76), (208, 170, 110)]
    copper = ramp_fn(COPPER)
    blue = ramp_fn(STEEL)

    def shade(a, b):
        if 3 <= a <= 5 and abs(b) <= 2:  # knob
            return wood[2 if b < 0 else 1]
        if 6 <= a <= 20 and abs(b) <= 1:  # grip, leather wrapped
            wrap = (a + b) % 4 in (0, 1)
            return LEATHER[(2 if wrap else 1) - (b > 0) + (b < 0)]
        width = 1 if a < 30 else 2 if a < 42 else 3
        if 21 <= a <= 52 and abs(b) <= width:
            if 30 <= a <= 46 and (a + b) % 3 == 0:  # the copper winding
                return copper(0.9 if b < 0 else 0.6 if b == 0 else 0.35)
            return wood[3 if b < -1 else 2 if b <= 0 else 1 if b < width else 0]
        if 53 <= a <= 55 and abs(b) <= 3:  # steel end cap
            return blue(0.85 if b < 0 else 0.55)
        if 56 <= a <= 59 and b in (-2, 2):  # electrode prongs
            return blue(0.95 if a == 59 else 0.7)
        return None
    return outline(axis_sprite(shade), INK)


def generator():
    """A dynamo: steel frame, copper-wound drum, a drive pulley and two brass terminals."""
    return outline(paint(["................",
                          "....b......b....",
                          "....B......B....",
                          "...3333333333...",
                          "..355555555553..",
                          ".q3cCCCCCCCCc3..",
                          "qQq3cCcCcCcCc3..",
                          "q1q3oOoOoOoOo3..",
                          "qQq3cCcCcCcCc3..",
                          ".q3oOoOoOoOoo3..",
                          "..355555555553..",
                          "..222222222222..",
                          ".22111111111122.",
                          ".11..........11.",
                          "................",
                          "................"],
                         {'b': rgb(BRASS[4]), 'B': rgb(BRASS[2]), '1': PLATE['1'], '2': PLATE['2'], '3': PLATE['3'],
                          '5': PLATE['5'], 'c': rgb(COPPER[2]), 'C': rgb(COPPER[4]), 'o': rgb(COPPER[1]),
                          'O': rgb(COPPER[3]), 'q': PLATE['4'], 'Q': PLATE['6']}), INK)


# ------------------------------------------------------------------------------------------ survey

def radar():
    """A handheld radar scope: steel case, green sweep screen with blips, brass knobs and an antenna."""
    image = Image.new('RGBA', (16, 16))
    px = image.load()
    for y in range(3, 15):
        for x in range(2, 14):
            px[x, y] = PLATE['4' if y < 5 else '3' if x < 12 else '2'] + (255,)
    cx, cy = 7.5, 8.5
    for y in range(4, 14):
        for x in range(3, 13):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d > 4.3:
                continue
            angle = (math.degrees(math.atan2(-(y + 0.5 - cy), x + 0.5 - cx)) - 45) % 360
            glow_t = max(0.0, 1 - angle / 120)  # the sweep's afterglow fades counter-clockwise
            base = (18, 52, 30)
            lit = (70, 220, 110)
            colour = tuple(round(b + (l - b) * glow_t * 0.7) for b, l in zip(base, lit))
            if d > 3.6:
                colour = (12, 30, 18)  # bezel shadow
            px[x, y] = colour + (255,)
    for x, y in ((8, 8), (9, 7), (10, 6)):  # sweep arm
        px[x, y] = (170, 255, 190, 255)
    for x, y in ((5, 7), (9, 11)):  # blips
        px[x, y] = (255, 90, 70, 255)
    for x in (4, 11):  # knobs
        px[x, 14] = rgb(BRASS[3]) + (255,)
    for x, y in ((12, 2), (13, 1), (14, 0)):  # antenna
        px[x, y] = PLATE['5'] + (255,)
    return outline(image, INK)


def target_icon():
    """Target acquired: a red reticle closing on a fresh three-toed track."""
    art = Image.new('RGBA', (32, 32))
    px = art.load()
    mud, mud_dark = (132, 98, 62, 255), (98, 70, 42, 255)

    def segment(p, a, b):
        ax, ay = a
        bx, by = b
        t = max(0, min(1, ((p[0] - ax) * (bx - ax) + (p[1] - ay) * (by - ay)) / ((bx - ax) ** 2 + (by - ay) ** 2)))
        return math.hypot(p[0] - ax - t * (bx - ax), p[1] - ay - t * (by - ay)), t
    toes = (((12.8, 15.4), (7.5, 7.5)), ((16, 13.6), (16, 4.5)), ((19.2, 15.4), (24.5, 7.5)))
    for y in range(32):
        for x in range(32):
            p = (x + 0.5, y + 0.5)
            heel = ((p[0] - 16) / 3.6) ** 2 + ((p[1] - 20.8) / 3.0) ** 2 <= 1
            toe = any(d <= 1.8 - 1.2 * t for d, t in (segment(p, a, b) for a, b in toes))
            if heel or toe:
                px[x, y] = mud_dark if p[0] > 16.5 else mud
    red = (220, 40, 34, 255)
    cx, cy, r = 16, 15, 12.5
    for y in range(32):
        for x in range(32):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if r - 1.2 <= d <= r + 0.2:
                px[x, y] = red
            elif (abs(dx) < 0.8 and r - 4 <= abs(dy) <= r + 3) or (abs(dy) < 0.8 and r - 4 <= abs(dx) <= r + 3):
                px[x, y] = red
    return icon(outline(art, (40, 10, 8, 255)))


def completionist_icon():
    """Completionist: a Tyrannosaur skull with a gold star, every species accounted for."""
    skull = outline(paint(["................",
                           "................",
                           "................",
                           "...BBBBB........",
                           "..BWHHHWBBB.....",
                           ".BWHoooWWWWBBB..",
                           "BWWoooooWWWWWWB.",
                           "BWWWoooWWooWWnWB",
                           "BWooWWWWWWWWWWWB",
                           "BWooWWtWtWtWtWB.",
                           ".BWWBt.t.t.t.t..",
                           "..BWW.t.t.t.tB..",
                           "..BWWtWtWtWtWB..",
                           "...BBWWWWWWBB...",
                           ".....BBBBBB.....",
                           "................"],
                          {'B': (150, 138, 110), 'W': (226, 216, 190), 'H': (250, 246, 230), 'o': (66, 54, 38),
                           'n': (66, 54, 38), 't': (250, 248, 238)}), (48, 40, 28, 255))
    art = Image.new('RGBA', (32, 32))
    art.alpha_composite(item32(skull), (0, 2))
    star = Image.new('RGBA', (32, 32))
    sp = star.load()
    cx, cy = 25.5, 6.5
    for y in range(32):
        for x in range(32):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            angle = math.atan2(dy, dx) + math.pi / 2
            spike = 0.5 + 0.5 * abs(math.cos(angle * 2.5)) ** 2
            if math.hypot(dx, dy) <= 5.4 * spike:
                sp[x, y] = (250, 204, 60, 255) if dy < 0.5 else (214, 150, 30, 255)
    art.alpha_composite(outline(star, (90, 60, 10, 255)))
    return icon(art)


# ------------------------------------------------------------------------------------------ machine parts

def engine():
    """A single-cylinder engine: finned cylinder, spark plug, crankcase, a spoked flywheel and a copper exhaust."""
    image = paint(["................",
                   "........cc......",
                   ".......2552.....",
                   "......46666554..",
                   "......11111111..",
                   "......45666554ee",
                   "......11111111.e",
                   "......45666554.e",
                   "......11111111.e",
                   "....33333333333e",
                   "....45555555553.",
                   "....45555555553.",
                   "....33333333333.",
                   "....22222222222.",
                   ".....11.....11..",
                   "................"],
                  PLATE | {'c': rgb(COPPER[3]), 'e': rgb(COPPER[2])})
    px = image.load()
    brass = ramp_fn(BRASS)
    cx, cy = 4.5, 10.5
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d <= 3.6:
                if d > 2.6:
                    px[x, y] = brass(0.85 if dx + dy < 0 else 0.45) + (255,)  # rim
                elif d < 0.8:
                    px[x, y] = PLATE['6'] + (255,)  # hub
                elif abs(dx) < 0.6 or abs(dy) < 0.6:
                    px[x, y] = PLATE['4'] + (255,)  # spokes
                else:
                    px[x, y] = PLATE['1'] + (255,)
    return outline(image, INK)


def metal_sheet():
    """A thin steel sheet lying flat: one-pixel edge, four rivet holes, a diagonal sheen."""
    image = Image.new('RGBA', (16, 16))
    px = image.load()
    steel_t = ramp_fn(STEEL)
    for y in range(16):
        for x in range(16):
            if abs(x + 0.5 - 8) / 7.5 + abs(y + 0.5 - 7.5) / 4.5 <= 1:
                sheen = 0.32 if 0 <= (x - 2 * y) + 9 <= 2 else 0.16 if (x - 2 * y) + 9 in (-1, 3) else 0
                px[x, y] = steel_t(min(1.0, 0.56 + sheen)) + (255,)
            elif y > 7 and abs(x + 0.5 - 8) / 7.5 + abs(y - 0.5 - 7.5) / 4.5 <= 1:
                px[x, y] = steel_t(0.34 if x < 8 else 0.2) + (255,)  # the sheet's edge
    for x, y in ((3, 7), (12, 7), (7, 4), (8, 11)):
        px[x, y] = PLATE['1'] + (255,)
    return outline(image, INK)


def metal_pipe():
    """A steel pipe with flanged ends, its bore open toward the viewer at the top."""
    image = Image.new('RGBA', (16, 16))
    px = image.load()
    blue = ramp_fn(STEEL)
    for y in range(16):
        for x in range(16):
            a, b = x + (15 - y), x + y - 15
            flange = a in (4, 5, 25, 26)
            if not (3 <= a <= 27 and abs(b) <= (3 if flange else 2)):
                continue
            if a == 27 and abs(b) <= 1:
                colour = PLATE['0']  # the open bore
            else:
                t = 0.95 if b <= -2 else 0.75 if b == -1 else 0.55 if b == 0 else 0.4 if b == 1 else 0.25
                colour = blue(t - (0.1 if flange else 0))
            px[x, y] = colour + (255,)
    return outline(image, INK)


def metal_screw():
    """A slotted steel screw: wide flat head, spiral thread, pointed tip."""
    image = Image.new('RGBA', (16, 16))
    px = image.load()
    blue = ramp_fn(STEEL)
    head = {20: 4, 21: 5, 22: 5, 23: 4}
    for y in range(16):
        for x in range(16):
            a, b = x + (15 - y), x + y - 15
            if a in head and abs(b) <= head[a]:
                if a == 20:
                    colour = blue(0.3)  # under the head
                elif abs(b) <= 1 and a == 23:
                    colour = PLATE['0']  # the slot, cut across the top
                else:
                    colour = blue(0.98 if b < -2 else 0.8 if b < 0 else 0.55 if b < 3 else 0.35)
            elif 8 <= a <= 19 and abs(b) <= 1:
                crest = (a - b) % 3 == 0
                colour = blue((0.95 if crest else 0.62) if b < 0 else (0.72 if crest else 0.4) if b == 0 else
                              (0.45 if crest else 0.22))
            elif 4 <= a <= 7 and abs(b) <= (a - 4) // 2:
                colour = blue(0.62 if b <= 0 else 0.35)
            else:
                continue
            px[x, y] = colour + (255,)
    return outline(image, INK)


# ------------------------------------------------------------------------------------------ icons

def map_icon():
    """They will fail: the vanilla map with a dashed route to a red cross."""
    image = vanilla('item/map').copy()
    px = image.load()
    for x, y in ((4, 9), (6, 9), (7, 8), (9, 8)):
        px[x, y] = (122, 84, 44, 255)
    for x, y in ((10, 5), (12, 5), (11, 6), (10, 7), (12, 7)):
        px[x, y] = (200, 36, 30, 255)
    for x, y in ((5, 6), (6, 6), (6, 5), (7, 5)):
        px[x, y] = (122, 150, 84, 255)
    return icon(image)


def bolts(image, points, colour=(150, 220, 255, 255), core=(240, 252, 255, 255)):
    px = image.load()
    for path in points:
        for i, (x, y) in enumerate(path):
            px[x, y] = core if i % 2 == 0 else colour
    return image


def shock_icon(bat):
    art = item32(bat).copy()
    art = bolts(art, [((28, 1), (27, 2), (28, 3), (27, 4), (26, 5)), ((31, 6), (30, 5), (29, 6), (28, 7)),
                      ((22, 2), (23, 3), (22, 4), (23, 5))])
    return glow(icon(art), (90, 170, 255), 5, 1.6)


def generator_icon(dynamo):
    art = item32(dynamo).copy()
    art = bolts(art, [((10, 1), (11, 0), (12, 1), (13, 0), (14, 1), (15, 0), (16, 1), (17, 0), (18, 1), (19, 0),
                       (20, 1), (21, 0))])
    return icon(art)


def main():
    # The steel armour sprites and the Harder icon are drawn from the worn models (build_armour_sprites).
    armour = {p: armour_sprites.sprite('steel', p) for p in armour_sprites.PIECES}
    steel_ingot = ingot(STEEL, gamma=1.1)
    px = steel_ingot.load()
    for y in range(16):  # folded-steel waves across the faces, as on the Better Together icon
        for x in range(16):
            p = px[x, y]
            if p[3] and luminance(p) > 50 and (x + 2 * y + (x // 3)) % 5 == 0:
                px[x, y] = tuple(max(0, c - 18) for c in p[:3]) + (255,)
    items = {
        'steel_ingot': steel_ingot,
        **{f'steel_{p}': image for p, image in armour.items()},
        'backpack': backpack(),
        'specialized_boots': specialized_boots(),
        'rifle': rifle(),
        'ammunition': cartridges(),
        'copper_wire': copper_wire(),
        'electric_bat': electric_bat(),
        'radar': radar(),
        'engine': engine(),
        'metal_sheet': metal_sheet(),
        'metal_pipe': metal_pipe(),
        'metal_screw': metal_screw(),
    }
    for name, image in items.items():
        save(item32(image), TEX / 'item' / f'{name}.png')

    icons = {
        'cartography': map_icon(),
        'radar': icon(items['radar']),
        'target': target_icon(),
        'completionist': completionist_icon(),
        'wires': icon(items['copper_wire']),
        'generator': generator_icon(generator()),
        'circuit': icon(items['electric_bat']),
        'shock': shock_icon(items['electric_bat']),
        'steel_set': armour_sprites.set_icon('steel'),
        'backpack': icon(items['backpack']),
        'boots': icon(items['specialized_boots']),
        'rifle': icon(items['rifle']),
        'engine': icon(items['engine']),
        'sheet': icon(items['metal_sheet']),
        'pipe': icon(items['metal_pipe']),
        'screw': icon(items['metal_screw']),
    }
    for name, image in icons.items():
        assert image.size == (64, 64) and image.mode == 'RGBA', name
        save(image, ICONS / f'{name}.png')
    print(f'Wrote {len(WRITTEN)} textures:')
    for path in WRITTEN:
        print('  ' + path.relative_to(TEX.parents[5]).as_posix())


if __name__ == '__main__':
    main()
