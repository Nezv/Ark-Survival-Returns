"""Build the Prehistoric item sprites: rock, stone knife, fire starter, dried meat and dinosaur meats.

Meats start from the vanilla meat sprites in the local Minecraft source jar, doubled to 32 px with
nearest-neighbour scaling (so the pixel grid stays crisp) and re-toned per meat family; prime meat
gains fat marbling. The item models scale them up in hand and on the ground (see ArkData).
Hand-drawn items are authored on a 16 px grid and doubled the same way.

Run from Ark: python tools/build_primitive_items.py
"""
import colorsys
import io
import random
import zipfile
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / 'build/moddev/artifacts/minecraft-patched-26.1.2.109-sources.jar'
OUT = ROOT / 'src/main/resources/assets/arksurvivalreturns/textures/item'

# family: (raw sprite, cooked sprite, hue shift, saturation factor, value factor, marbled)
MEATS = {
    'herbivore': ('porkchop', 'cooked_porkchop', 0.00, 0.90, 1.05, False),
    'carnivore': ('beef', 'cooked_beef', -0.015, 1.15, 0.82, False),
    'prime': ('beef', 'cooked_beef', 0.00, 1.10, 1.00, True),
    'bird': ('chicken', 'cooked_chicken', 0.02, 1.10, 1.00, False),
    'reptile': ('mutton', 'cooked_mutton', 0.07, 0.75, 0.88, False),
    'game': ('mutton', 'cooked_mutton', -0.01, 1.10, 0.78, False),
    'marine': ('salmon', 'cooked_salmon', -0.03, 1.00, 1.00, False),
}


def vanilla(name):
    with zipfile.ZipFile(JAR) as jar:
        return Image.open(io.BytesIO(jar.read(f'assets/minecraft/textures/item/{name}.png'))).convert('RGBA')


def double(image):
    return image.resize((image.width * 2, image.height * 2), Image.Resampling.NEAREST)


def retone(image, hue, sat, val):
    out = image.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            r2, g2, b2 = colorsys.hsv_to_rgb((h + hue) % 1.0, min(1.0, s * sat), min(1.0, v * val))
            px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    return out


def marble(image, seed):
    """Thin fat streaks on the reddest interior pixels of a 16 px sprite."""
    out = image.copy()
    px = out.load()
    rng = random.Random(seed)
    for y in range(1, out.height - 1):
        for x in range(1, out.width - 1):
            r, g, b, a = px[x, y]
            neighbours = [px[x + dx, y + dy][3] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
            if a and min(neighbours) and r > g + 40 and rng.random() < 0.16:
                px[x, y] = (min(255, r + 60), min(255, g + 90), min(255, b + 80), a)
    return out


def canvas():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


def rock():
    img = canvas()
    px = img.load()
    rng = random.Random(7)
    shape = ["................",
             "................",
             "................",
             ".....######.....",
             "...##########...",
             "..############..",
             ".##############.",
             ".##############.",
             ".##############.",
             ".##############.",
             "..############..",
             "...##########...",
             ".....######.....",
             "................",
             "................",
             "................"]
    for y, row in enumerate(shape):
        for x, c in enumerate(row):
            if c != '#':
                continue
            edge = any(shape[y + dy][x + dx] != '#' for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            light = 1.0 - (x + y) / 40.0
            base = 118 + int(light * 40) + rng.randint(-8, 8)
            if edge:
                base = 72 if (x + y) > 12 else 96
            px[x, y] = (base, base - 2, base - 6, 255)
    for x, y in ((5, 5), (6, 5), (4, 6)):
        px[x, y] = (178, 176, 170, 255)
    return img


def paint(rows, palette):
    img = canvas()
    px = img.load()
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in palette:
                px[x, y] = palette[c]
    return img


def stone_knife():
    # A knapped leaf-shaped blade with a fiber-wrapped grip.
    return paint(["................",
                  "............hL..",
                  "...........hLLd.",
                  "..........hLLdd.",
                  ".........hLLdd..",
                  "........hLLdd...",
                  ".......hLLdd....",
                  "......hLddd.....",
                  ".....bLddd......",
                  "....ffbd........",
                  "...FfF..........",
                  "..fFf...........",
                  ".FfF............",
                  ".ow.............",
                  "................",
                  "................"],
                 {'h': (205, 203, 196, 255), 'L': (160, 157, 150, 255), 'd': (108, 104, 98, 255),
                  'b': (84, 80, 76, 255), 'f': (178, 146, 84, 255), 'F': (126, 98, 52, 255),
                  'o': (92, 66, 36, 255), 'w': (70, 50, 28, 255)})


def fire_starter():
    # A hand drill: spindle on a notched fireboard, with an ember in the notch.
    return paint(["................",
                  "...........sS...",
                  "..........sS....",
                  ".........sS.....",
                  "........sS......",
                  ".......sS.......",
                  "......sS........",
                  ".....sS.........",
                  "....sS..........",
                  "...cS...........",
                  "..ecbbbbbbbbbb..",
                  ".BEeBBBBBBBBBBB.",
                  ".BBBBBBBBBBBBBB.",
                  "..DDDDDDDDDDDD..",
                  "................",
                  "................"],
                 {'s': (176, 132, 76, 255), 'S': (122, 88, 46, 255), 'c': (60, 42, 24, 255),
                  'b': (170, 124, 70, 255), 'B': (138, 98, 52, 255), 'D': (96, 66, 34, 255),
                  'e': (255, 170, 60, 255), 'E': (230, 90, 30, 255)})


def save(image, name):
    OUT.mkdir(parents=True, exist_ok=True)
    image.save(OUT / f'{name}.png')


def main():
    save(double(rock()), 'rock')
    save(double(stone_knife()), 'stone_knife')
    save(double(fire_starter()), 'fire_starter')
    save(double(retone(vanilla('cooked_beef'), 0.03, 0.70, 0.72)), 'dried_meat')
    for family, (raw, cooked, hue, sat, val, marbled) in MEATS.items():
        for kind, source in (('raw', raw), ('cooked', cooked)):
            sprite = retone(vanilla(source), hue, sat, val)
            if marbled:
                sprite = marble(sprite, sum(map(ord, family + kind)))
            save(double(sprite), f'{kind}_{family}_meat')
    print(f'Wrote {3 + 1 + 2 * len(MEATS)} sprites to {OUT}')


if __name__ == '__main__':
    main()
