"""Prehistoric tech icons and the Flint Knife sprite.

Monkeys is an oak tree, Tha rock the Ark rock, We should fight them the vanilla wooden spear, London a
duck holding a knife and Sharp thinking the Flint Knife, whose 32 px item sprite is written here too.
Hand-pixelled on a 16 px grid like vanilla items, then upscaled with the helpers of build_bronze_age_art.py.
Deterministic: every output is a pure function of this file, the mod rock texture and the Minecraft jar.

Run from Ark: python tools/build_prehistoric_icons.py
"""
from PIL import Image

from build_bronze_age_art import ICONS, TEX, icon, item32, mod_texture, outline, paint, save, vanilla, WRITTEN


def jitter(x, y, amount):
    """A fixed per-pixel shade offset, so flat fills read as a Minecraft texture."""
    return ((x * 73856093) ^ (y * 19349663)) % (2 * amount + 1) - amount


def shade(colour, delta):
    return tuple(max(0, min(255, c + delta)) for c in colour[:3]) + (255,)


def flint_knife():
    """A knapped flint blade bound with plant fiber to a wooden haft, drawn on the anti-diagonal."""
    edge, face, spine, chip = (150, 152, 162), (86, 86, 92), (46, 45, 48), (61, 60, 62)
    wood, bark = (137, 103, 39), (73, 54, 21)
    fiber, fiber_dark = (176, 186, 104), (118, 136, 64)
    image = Image.new('RGBA', (16, 16))
    px = image.load()

    def put(x, c, colour):
        y = c - x
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = colour + (255,)
    # A leaf-shaped flake, widest mid-blade: per column, the anti-diagonals (x + y) it covers.
    blade = {14: (15, 15), 13: (14, 16), 12: (13, 17), 11: (13, 17), 10: (13, 17), 9: (13, 17), 8: (14, 16), 7: (15, 16)}
    for x, (low, high) in blade.items():
        for c in range(low, high + 1):
            if c <= 13 or (c == 14 and x in (13, 8)):
                colour = edge                                  # the knapped cutting edge catches the light
            elif c >= 16:
                colour = spine
            else:
                colour = chip if (x + c) % 3 == 0 else face    # flake scars across the face
            put(x, c, colour)
    for x in (5, 6):
        for c in (15, 16, 17):
            put(x, c, fiber if (x + c) % 2 else fiber_dark)  # the binding
    for x in range(1, 5):
        put(x, 15, wood)
        put(x, 16, bark)
    return outline(image, (22, 20, 18, 255))


DUCK = [
    '................',
    '.....OOOOOO.....',
    '....OHHYYYYO....',
    '...OHYYYYYYYO...',
    '...OYYEYYYEYO...',
    '...OYYYBBBYYO...',
    '..OOYYYbbbYYYO..',
    '.OYYYYYYYYYYYYO.',
    'OSSWWWWWWYYYYYYO',
    'OSSGGGGGGgYYYYyO',
    '.OOOggggggYYYyyO',
    '..OYYYYYYYYYYyO.',
    '..OyYYYYYYYYyyO.',
    '...OOyyyyyyyOO..',
    '....FFO..OFF....',
    '...OFFFO.FFFO...',
]


def duck():
    """London: a round little duck with a kitchen knife, in the flat-shaded vanilla item style."""
    palette = {'O': (38, 28, 10), 'Y': (250, 212, 40), 'y': (214, 164, 22), 'H': (255, 242, 150),
               'E': (20, 18, 16), 'B': (247, 128, 30), 'b': (196, 82, 18), 'F': (236, 112, 26),
               'W': (236, 240, 244), 'G': (196, 202, 210), 'g': (128, 136, 146), 'S': (112, 62, 104)}
    image = paint(DUCK, palette)
    px = image.load()
    for y in range(16):
        for x in range(16):
            if DUCK[y][x] in 'Yy':
                px[x, y] = shade(px[x, y], jitter(x, y, 6))
    return image


CANOPY = [
    '....XXXXXXX.....',
    '..XXXXXXXXXXX...',
    '.XXXXXXXXXXXXX..',
    'XXXXXXXXXXXXXXX.',
    'XXXXXXXXXXXXXXXX',
    'XXXXXXXXXXXXXXXX',
    'XXXXXXXXXXXXXXX.',
    '.XXXXXXXXXXXXXX.',
    '..XXXXXXXXXXXX..',
    '....XXXXXXXX....',
]


def tree():
    """Monkeys: an oak, leaves from the vanilla oak_leaves texture in foliage green, bark from oak_log."""
    leaves, log = vanilla('block/oak_leaves'), vanilla('block/oak_log')
    lp, bp = leaves.load(), log.load()
    image = Image.new('RGBA', (16, 16))
    px = image.load()
    for y in range(9, 16):                                 # trunk, flared into roots on the last row
        for x in range(6, 10) if y < 15 else range(5, 11):
            px[x, y] = bp[x, y]
    tint = (0.46, 0.8, 0.28)
    for y, row in enumerate(CANOPY):
        for x, c in enumerate(row):
            if c != 'X':
                continue
            r, g, b, a = lp[x, y]
            value = r if a else 70                          # fill the texture's holes with deep shade
            px[x, y] = (round(value * tint[0]), round(value * tint[1]), round(value * tint[2]), 255)
    return outline(image, (22, 34, 12, 255))


def main():
    knife = flint_knife()
    save(item32(knife), TEX / 'item/flint_knife.png')
    icons = {
        'monkeys': icon(tree()),
        'rock': icon(mod_texture('item/rock')),
        'fight': icon(vanilla('item/wooden_spear')),
        'london': icon(duck()),
        'sharp': icon(knife),
    }
    for name, image in icons.items():
        save(image, ICONS / f'{name}.png')
    print(f'Wrote {len(WRITTEN)} files')


if __name__ == '__main__':
    main()
