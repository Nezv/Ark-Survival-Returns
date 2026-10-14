"""Nest block textures (F21): the flyers' nests in their own material instead of vanilla planks and the turtle egg.

Each nesting species builds of what its home offers: oak twigs for the Quetzal, spruce for the Argentavis, pale
driftwood for the seaside Pteranodon, mossy twigs for the Archaeopteryx and charred branches with embers for the
Dragon. The twigs are woven at random angles round the bowl over a dark gap; the lining is dry grass and down (ash
for the Dragon); the egg wears its nest-egg item's shell and marks (build_cosy_items.NEST_SHELLS), speckled all over,
since a block face shows the part of the texture its element covers. 16 px like vanilla blocks, deterministic.

Writes assets/arksurvivalreturns/textures/block/nest/{twigs_<wood>,lining,lining_ash,egg_<species>}.png and the
review sheet design/items/nest-blocks.png. ArkData.flying() builds the models from them.
Run from Ark/: python tools/build_nest_blocks.py
"""
import math
import random
from pathlib import Path

from PIL import Image

from build_cosy_items import NEST_SHELLS, STRAW

ARK = Path(__file__).resolve().parents[1]
OUT = ARK / 'src/main/resources/assets/arksurvivalreturns/textures/block/nest'
REVIEW = ARK / 'design/items/nest-blocks.png'

# Gap, then twig tones dark to light; moss and embers ride on top for two of them.
WOODS = {
    'oak': ((54, 38, 22), [(92, 64, 36), (118, 84, 48), (150, 108, 62), (186, 140, 84)]),
    'spruce': ((36, 26, 16), [(62, 42, 26), (84, 58, 36), (108, 76, 46), (136, 98, 60)]),
    'drift': ((92, 84, 72), [(136, 124, 106), (168, 156, 136), (196, 186, 166), (222, 214, 196)]),
    'mossy': ((40, 34, 20), [(86, 62, 36), (112, 82, 48), (140, 104, 62), (170, 132, 80)]),
    'charred': ((18, 14, 16), [(34, 28, 28), (48, 40, 38), (64, 54, 50), (84, 72, 66)]),
}
MOSS = [(84, 116, 46), (108, 144, 60), (134, 170, 78)]
EMBERS = [(196, 84, 36), (240, 140, 56), (252, 196, 96)]
# Which wood each nesting species builds with (ArkData.nestWood keeps the same table).
NEST_WOOD = {'quetzal': 'oak', 'argentavis': 'spruce', 'pteranodon': 'drift', 'archaeopteryx': 'mossy',
             'dragon': 'charred'}
SIZE = 16


def wrap(image, x, y, colour):
    image.putpixel((x % SIZE, y % SIZE), colour)


def twigs(wood):
    gap, tones = WOODS[wood]
    rng = random.Random('twigs-' + wood)
    image = Image.new('RGBA', (SIZE, SIZE), gap + (255,))
    for _ in range(26):
        x, y = rng.uniform(0, SIZE), rng.uniform(0, SIZE)
        # Nest twigs run round the bowl: mostly level, some steep crossings.
        angle = rng.choice([rng.uniform(-30, 30), rng.uniform(-30, 30), rng.uniform(40, 70) * rng.choice([-1, 1])])
        length = rng.uniform(5, 13)
        tone = rng.randrange(1, len(tones))
        dx, dy = math.cos(math.radians(angle)), math.sin(math.radians(angle))
        for step in range(int(length * 2)):
            px, py = int(x + dx * step / 2), int(y + dy * step / 2)
            wrap(image, px, py, tones[tone] + (255,))
            wrap(image, px, py - 1, tones[min(len(tones) - 1, tone + 1)] + (255,))   # lit upper edge
            if rng.random() < 0.5:
                wrap(image, px, py + 1, tones[tone - 1] + (255,))                    # shade under it
        if wood == 'charred' and rng.random() < 0.45:
            wrap(image, int(x + dx * length), int(y + dy * length), rng.choice(EMBERS) + (255,))
    if wood == 'mossy':
        for _ in range(7):
            cx, cy = rng.randrange(SIZE), rng.randrange(SIZE)
            for ox, oy in ((0, 0), (1, 0), (0, 1), (-1, 0), (1, 1)):
                if rng.random() < 0.8:
                    wrap(image, cx + ox, cy + oy, rng.choice(MOSS) + (255,))
    return image


def lining(ash=False):
    rng = random.Random('lining-ash' if ash else 'lining')
    if ash:
        base, strands, flecks = (70, 64, 62), [(88, 80, 76), (54, 48, 48), (106, 98, 92)], EMBERS[:2]
    else:
        base, strands, flecks = STRAW['n'], [STRAW['N'], STRAW['s'], (210, 176, 106)], [(244, 240, 232), (222, 216, 206)]
    image = Image.new('RGBA', (SIZE, SIZE), base + (255,))
    for _ in range(18):
        x, y = rng.randrange(SIZE), rng.randrange(SIZE)
        angle = rng.uniform(0, math.pi)
        tone = rng.choice(strands)
        for step in range(rng.randrange(3, 8)):
            wrap(image, int(x + math.cos(angle) * step), int(y + math.sin(angle) * step), tone + (255,))
    for _ in range(5 if ash else 9):
        x, y = rng.randrange(SIZE), rng.randrange(SIZE)
        tone = rng.choice(flecks)
        wrap(image, x, y, tone + (255,))
        if not ash and rng.random() < 0.6:
            wrap(image, x + 1, y, flecks[1] + (255,))
    return image


def egg(species):
    (h, l, b, d), marks = NEST_SHELLS[species]
    rng = random.Random('egg-' + species)
    image = Image.new('RGBA', (SIZE, SIZE), l + (255,))
    for y in range(SIZE):
        for x in range(SIZE):
            r = rng.random()
            if r < 0.12:
                image.putpixel((x, y), b + (255,))
            elif r < 0.16:
                image.putpixel((x, y), h + (255,))
    count = 22 if species == 'dragon' else 14
    for _ in range(count):
        x, y = rng.randrange(SIZE), rng.randrange(SIZE)
        tone = marks[0] if len(marks) == 1 or rng.random() < 0.6 else marks[1]
        wrap(image, x, y, tone + (255,))
        if rng.random() < 0.4:
            wrap(image, x + 1, y, tone + (255,))
    return image


def build():
    OUT.mkdir(parents=True, exist_ok=True)
    made = {}
    for wood in WOODS:
        made[f'twigs_{wood}'] = twigs(wood)
    made['lining'] = lining()
    made['lining_ash'] = lining(ash=True)
    for species in NEST_SHELLS:
        made[f'egg_{species}'] = egg(species)
    for name, image in made.items():
        image.save(OUT / f'{name}.png')
    # Review sheet: every texture at 8x, tiled 2x2 so the seams show.
    cell = SIZE * 2 * 8
    sheet = Image.new('RGBA', (cell * 4 + 50, cell * 3 + 40), (24, 22, 20, 255))
    for i, (name, image) in enumerate(made.items()):
        tile = Image.new('RGBA', (SIZE * 2, SIZE * 2))
        for ty in (0, SIZE):
            for tx in (0, SIZE):
                tile.paste(image, (tx, ty))
        sheet.paste(tile.resize((cell, cell), Image.Resampling.NEAREST), (10 + (i % 4) * (cell + 10), 10 + (i // 4) * (cell + 10)))
    REVIEW.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(REVIEW)
    print(f'F21: {len(made)} nest textures written.')


if __name__ == '__main__':
    build()
