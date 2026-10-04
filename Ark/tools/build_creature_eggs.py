"""Creature spawn eggs: one generated sprite per species, none drawn by hand.

Each egg is a function of the species itself:
  colour   its eight-colour palette strip (textures/entity/<id>.png): hide, light, shade, pale, cream, darkest,
           eye and flesh
  size     its body height (Species.java); sea animals lay rounder eggs, the amphibious longer ones
  shell    predators lay eggs in their hide colour with dark marks, the others in their light tone with pale ones
  pattern  its way of life: speckled for flyers, rippled for sea animals, scaled for the amphibious, slashed or
           jagged for hunters, spotted, blotched or banded for grazers; a hash of the id picks the variant and
           places the marks
Drawn on a 16 px grid and doubled to the 32 px Ark item size, with the cosy sprites' soft outline
(build_cosy_items.py).

Deterministic: every output is a pure function of this file, Species.java and the palette strips.

Run from Ark: python tools/build_creature_eggs.py
"""
import hashlib
import math
import random
import re
from collections import namedtuple

from PIL import Image, ImageDraw

from build_bronze_age_art import ARK, TEX, item32, save, WRITTEN
from build_cosy_items import rim

REVIEW = ARK / 'design/items/creature-eggs.png'
SPECIES_JAVA = ARK / 'src/main/java/dev/nez/arksurvivalreturns/feature/creature/Species.java'
CONSTANT = re.compile(r'^    [A-Z_]+\("(\w+)", "([^"]+)", \d+, \d+, [\d.]+, [\d.]+f, ([\d.]+)f, \d+, \d+, \d+, (true|false),',
                      re.M)
GRID, FLOOR = 16, 14  # the egg stands on row 14, which leaves row 15 to the outline
STEPS = ((1, 0), (-1, 0), (0, 1), (0, -1))

Creature = namedtuple('Creature', 'id name height predator realm')
Cell = namedtuple('Cell', 'x y nx ny')  # pixel, and its place on the shell from -1 (left, top) to 1 (right, bottom)


def species():
    """Id, name, body height, diet and realm of every species, read from the Species enum."""
    text = SPECIES_JAVA.read_text(encoding='utf-8')
    found = list(CONSTANT.finditer(text))
    out = []
    for match, following in zip(found, found[1:] + [None]):
        body = text[match.end():following.start() if following else text.index(';', match.end())]
        body = re.sub(r'//.*|/\*.*?\*/', '', body, flags=re.S)
        realm = ('air' if 'FlyerProfile' in body else 'water' if 'LandFamily.AQUATIC' in body
                 else 'amphibious' if 'SwimProfile' in body else 'land')
        out.append(Creature(match[1], match[2], float(match[3]), match[4] == 'true', realm))
    return out


def palette(identifier):
    strip = Image.open(TEX / f'entity/{identifier}.png').convert('RGB')
    assert strip.size == (64, 8), f'{identifier}: the palette strip is {strip.size}'
    return [strip.getpixel((x, 4)) for x in range(4, 64, 8)]


def mix(a, b, t):
    return tuple(round(p + (q - p) * t) for p, q in zip(a, b))


# ------------------------------------------------------------------------------------------------- shell

def shell(creature):
    """The egg's cells: an ellipse drawn in at the top, as tall as the animal is big."""
    t = min(1, max(0, math.log(creature.height / 0.5) / math.log(7 / 0.5)))
    height = round(9 + 5 * t)
    aspect = {'water': 0.9, 'amphibious': 0.72}.get(creature.realm, 0.8)
    width = 2 * round(height * aspect / 2)
    halves = [(1 - abs(s) ** 2.3) ** (1 / 2.3) * (1 + 0.24 * s)
              for s in ((row + 0.5) / height * 2 - 1 for row in range(height))]
    top = FLOOR + 1 - height
    cells = []
    for row, half in enumerate(halves):
        # A blunt crown and a broad base: at this size a pointed end reads as a gem, not an egg.
        reach = max(width // 4 if row < height / 2 else width // 2 - 2, round(half / max(halves) * width / 2))
        for x in range(GRID // 2 - reach, GRID // 2 + reach):
            cells.append(Cell(x, top + row, (x + 0.5 - GRID / 2) / (width / 2), (row + 0.5) / height * 2 - 1))
    return cells


def tone(cell):
    """Lit from the upper left: h glint, l light, b body, d shade."""
    nz = math.sqrt(max(0, 1 - cell.nx ** 2 - cell.ny ** 2))
    lit = -0.48 * cell.nx - 0.56 * cell.ny + 0.68 * nz
    return 'h' if lit > 0.95 else 'l' if lit > 0.7 else 'b' if lit > 0.16 else 'd'


# ---------------------------------------------------------------------------------------------- patterns
# Each returns {(x, y): 'm' | 'a'}: the main mark and its accent.

def speckles(cells, rng, variant):
    """Bird speckles, thicker toward the blunt end."""
    marks = {}
    order = sorted(cells, key=lambda c: rng.random() / (0.5 + (c.ny + 1) * (0.9 if variant else 0.45)))
    for cell in order:
        if len(marks) >= len(cells) * 0.17:
            break
        if not any((cell.x + dx, cell.y + dy) in marks for dx, dy in STEPS):
            marks[cell.x, cell.y] = 'a' if len(marks) % 4 == 3 else 'm'
    return marks


def spots(cells, rng, variant, ring=False):
    """Round spots; with ring, each keeps an accent centre like a bubble."""
    inside = {(c.x, c.y) for c in cells}
    marks, centres = {}, []
    for cell in sorted(cells, key=lambda c: rng.random()):
        if all(max(abs(cell.x - x), abs(cell.y - y)) >= 4 for x, y in centres):
            centres.append((cell.x, cell.y))
            big = (variant + len(centres)) % 2 and len(cells) > 80
            shape = ((0, 0), (1, 0), (0, 1), (1, 1)) + (((-1, 0), (-1, 1), (0, -1), (1, -1), (2, 0), (2, 1), (0, 2), (1, 2))
                                                       if big else ())
            for dx, dy in shape:
                if (cell.x + dx, cell.y + dy) in inside:
                    marks[cell.x + dx, cell.y + dy] = 'm'
            if ring:
                marks[cell.x, cell.y] = 'a'
    return marks


def blotches(cells, rng, variant):
    """Two or three irregular patches, grown outward from a seed."""
    inside = {(c.x, c.y) for c in cells}
    marks = {}
    for patch in range(2 + variant):
        seed = rng.choice([c for c in cells if abs(c.nx) < 0.75 and (c.x, c.y) not in marks])
        grown = {(seed.x, seed.y)}
        while len(grown) < len(cells) * (0.12 if variant else 0.17):
            edge = sorted({(x + dx, y + dy) for x, y in grown for dx, dy in STEPS} & inside - grown)
            # Cells with more grown neighbours go first, so a patch stays a patch and does not branch.
            grown.add(rng.choices(edge, [sum((x + dx, y + dy) in grown for dx, dy in STEPS) ** 2 for x, y in edge])[0])
        marks.update({cell: 'a' if patch == 2 else 'm' for cell in grown})
    return marks


def bands(cells, rng, variant):
    """Belts around the shell, bowed with its curve."""
    marks = {}
    rows = sorted({c.y for c in cells})
    belts = (0.3, 0.68) if variant else (0.24, 0.5, 0.76)
    for index, share in enumerate(belts):
        y0 = rows[round(share * (len(rows) - 1))]
        for cell in cells:
            bow = y0 + (0 if abs(cell.nx) < 0.55 else -1)
            if cell.y == bow or (variant and index == 1 and cell.y == bow + 1):
                marks[cell.x, cell.y] = 'a' if index == 1 and not variant else 'm'
    return marks


def zigzag(cells, rng, variant):
    """A jagged belt, like a row of teeth; the second variant carries two."""
    marks = {}
    rows = sorted({c.y for c in cells})
    for index, share in enumerate((0.3, 0.66) if variant else (0.5,)):
        y0 = rows[round(share * (len(rows) - 1))]
        for cell in cells:
            edge = y0 + (0, 1, 2, 1)[(cell.x + index * 2) % 4] - 1
            if cell.y == edge or (not variant and cell.y == edge + 1):
                marks[cell.x, cell.y] = 'm'
            elif not variant and cell.y == edge + 2 and cell.x % 4 == 1:
                marks[cell.x, cell.y] = 'a'
    return marks


def slashes(cells, rng, variant):
    """Claw marks: short bars reaching in from alternate sides."""
    marks = {}
    rows = sorted({c.y for c in cells})
    for index, y in enumerate(rows[2 + variant:-1:2 + variant]):
        row = sorted((c for c in cells if c.y == y), key=lambda c: c.x, reverse=bool(index % 2))
        reach = max(2, round(len(row) * rng.choice((0.4, 0.5, 0.6))))
        for step, cell in enumerate(row[:reach]):
            marks[cell.x, cell.y - (1 if step >= reach - 1 and variant else 0)] = 'a' if step == reach - 1 else 'm'
    inside = {(c.x, c.y) for c in cells}
    return {cell: mark for cell, mark in marks.items() if cell in inside}


def stripes(cells, rng, variant):
    """Slanted stripes, leaning either way."""
    lean = 1 if variant else -1
    phase = rng.randrange(4)
    marks = {}
    for cell in cells:
        if (cell.x + lean * cell.y + phase) % 4 == 0 and abs(cell.nx) < 0.92:
            marks[cell.x, cell.y] = 'a' if (cell.x + lean * cell.y + phase) % 8 == 0 else 'm'
    return marks


def scales(cells, rng, variant):
    """A net of diamond scales over the lower shell (variant: the whole shell, finer)."""
    marks = {}
    for cell in cells:
        if variant:
            if (cell.x + cell.y) % 3 == 0 and (cell.x - cell.y) % 3 == 0 or (cell.x + 2 * cell.y) % 6 == 4:
                marks[cell.x, cell.y] = 'm'
        elif cell.ny > -0.35 and ((cell.x + cell.y) % 4 == 0 or (cell.x - cell.y) % 4 == 0):
            marks[cell.x, cell.y] = 'a' if (cell.x + cell.y) % 4 == 0 and (cell.x - cell.y) % 4 == 0 else 'm'
    return marks


def waves(cells, rng, variant):
    """Ripples, with a few bubbles between them."""
    marks = {}
    rows = sorted({c.y for c in cells})
    phase = rng.randrange(4)
    for index, y0 in enumerate(rows[2 + variant::3 + variant]):
        if y0 >= rows[-1]:
            break
        for cell in cells:
            if cell.y == y0 + (0, 0, 1, 1)[(cell.x + phase + index * 2) % 4]:
                marks[cell.x, cell.y] = 'm'
    free = [c for c in cells if (c.x, c.y) not in marks and (c.x, c.y - 1) not in marks and abs(c.nx) < 0.6]
    for cell in rng.sample(free, min(len(free), 2 + variant)):
        marks[cell.x, cell.y] = 'a'
    return marks


def capped(cells, rng, variant):
    """Dipped at one end, the edge of the dip ragged, with stray flecks beyond it."""
    marks = {}
    ragged = [rng.choice((-0.12, 0, 0.12)) for _ in range(GRID)]
    for cell in cells:
        depth = cell.ny if variant else -cell.ny
        if depth > 0.3 + ragged[cell.x]:
            marks[cell.x, cell.y] = 'm'
        elif depth > -0.25 and rng.random() < 0.14:
            marks[cell.x, cell.y] = 'a'
    return marks


def cracks(cells, rng, variant):
    """Glowing fissures climbing from the base."""
    inside = {(c.x, c.y) for c in cells}
    floor = max(c.y for c in cells)
    marks = {}
    for start in (-2, 1, 3) if variant else (-3, 0, 2):
        x, y = GRID // 2 + start, floor - 1
        while (x, y) in inside and y > floor - len({c.y for c in cells}) * 0.8:
            marks[x, y] = 'a'
            if (x, y + 1) in inside and (x, y + 1) not in marks:
                marks[x, y + 1] = 'm'
            x += rng.choice((-1, 0, 0, 1))
            y -= 1
    return marks


def bubbles(cells, rng, variant):
    return spots(cells, rng, variant, ring=True)


# ---------------------------------------------------------------------------------------------- the egg

def pattern(creature, pick):
    big = creature.height >= 2.8
    if creature.realm == 'air':
        return cracks if creature.predator else (speckles, capped)[pick % 2]
    if creature.realm == 'water':
        return waves if creature.predator else bubbles
    if creature.realm == 'amphibious':
        return scales
    if creature.predator:
        return ((zigzag, slashes) if big else (stripes, slashes, zigzag))[pick % (2 if big else 3)]
    return ((blotches, bands) if big else (spots, capped, bands))[pick % (2 if big else 3)]


def egg(creature):
    digest = hashlib.sha256(creature.id.encode()).digest()
    rng = random.Random(int.from_bytes(digest[:8], 'big'))
    hide, light, shade, pale, cream, darkest, eye, flesh = palette(creature.id)
    draw = pattern(creature, digest[8])
    if creature.predator:
        tones = {'h': pale, 'l': light, 'b': hide, 'd': shade}
        marks = {'m': darkest, 'a': (eye, flesh)[digest[9] % 2]}
        if draw is waves:
            marks = {'m': pale, 'a': cream}
        elif draw is cracks:
            marks = {'m': flesh, 'a': eye}
    else:
        tones = {'h': mix(cream, (255, 255, 255), 0.35), 'l': cream, 'b': pale, 'd': light}
        marks = {'m': hide, 'a': (shade, flesh)[digest[9] % 2]}
        if draw in (speckles, bubbles):
            marks = {'m': shade, 'a': (darkest, eye)[draw is bubbles]}
    cells = shell(creature)
    drawn = draw(cells, rng, digest[10] % 2)
    image = Image.new('RGBA', (GRID, GRID))
    px = image.load()
    for cell in cells:
        lit = tone(cell)
        colour = tones[lit]
        if (cell.x, cell.y) in drawn:
            colour = marks[drawn[cell.x, cell.y]]
            colour = mix(colour, tones['d'], 0.4) if lit == 'd' else mix(colour, tones['l'], 0.25) if lit in 'hl' else colour
        px[cell.x, cell.y] = colour + (255,)
    return rim(image)


def sprites():
    return {f'{creature.id}_spawn_egg': egg(creature) for creature in species()}


def review(images):
    """The review sheet: every egg at twice its texture size, on a light slot and on a dark panel, with its name."""
    cell, columns = 96, 9
    rows = -(-len(images) // columns)
    sheet = Image.new('RGB', (columns * cell, rows * cell * 2), (36, 30, 26))
    draw = ImageDraw.Draw(sheet)
    for band, (colour, ink) in enumerate((((198, 190, 172), (40, 32, 26)), ((58, 46, 38), (214, 204, 186)))):
        for i, (name, image) in enumerate(images.items()):
            x, y = (i % columns) * cell, (i // columns + band * rows) * cell
            draw.rectangle([x + 3, y + 3, x + cell - 4, y + cell - 4], fill=colour)
            big = image.resize((64, 64), Image.Resampling.NEAREST)
            sheet.paste(big, (x + 16, y + 8), big)
            draw.text((x + 8, y + 76), name.removesuffix('_spawn_egg')[:14], fill=ink)
    return sheet


def main():
    images = {name: item32(image) for name, image in sprites().items()}
    assert len({image.tobytes() for image in images.values()}) == len(images), 'two species share an egg'
    for name, image in images.items():
        save(image, TEX / f'item/{name}.png')
    save(review(images), REVIEW)
    print(f'Wrote {len(images)} spawn eggs and the review sheet ({len(WRITTEN)} files)')


if __name__ == '__main__':
    main()
