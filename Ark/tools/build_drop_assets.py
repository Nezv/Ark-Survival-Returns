"""Author the supply drops: four parachutes and the four loot crates they carry.

Run from any directory: python tools/build_drop_assets.py  (Pillow and numpy; add --preview for docs/drop-assets.png)
Geometry lives under models/block/drop; DropData owns the blockstate, loot and translation wiring, and
feature/drop/SupplyTier.java repeats the colours.

  loot_crate_<tier>   the Storage Crate's own model (build_station_assets.py), with its planks and frame repainted
                      in the tier colour
  parachute_<tier>    a hollow cloth dome in stepped rings, cords down to the crate and straps across its lid.
                      The model's y -16 is the crate's lid, so SupplyDropRenderer draws it two blocks up.
                      white   a small plain linen canopy on four cords
                      green   wider, a dark hem
                      blue    wider again, eight cords, a vent with a cap over it
                      purple  the full three blocks, gold gores, hem and finial, pennants round the hem
"""
import json
import math
import random
import sys
from PIL import Image, ImageDraw
from build_camp_assets import ROOT, ASSETS, box, DISPLAY

MODELS = ASSETS / 'models/block/drop'
TEXTURES = ASSETS / 'textures/block/drop'
STATION = ASSETS / 'textures/block/station'
CENTRE = 8.0
LID = -16.0
CELL = 2      # the canopy is drawn on a 2-pixel grid

# cloth, alternate gore, hem; the canopy's radius, its rings and where its hem hangs (model pixels)
TIERS = {
    'white':  dict(cloth=(226, 222, 208), gore=(196, 194, 184), hem=(150, 146, 134), radius=16, rings=3, base=6, cords=4),
    'green':  dict(cloth=(74, 150, 70), gore=(222, 220, 204), hem=(40, 92, 44), radius=19, rings=4, base=7, cords=4),
    'blue':   dict(cloth=(58, 110, 190), gore=(222, 222, 214), hem=(30, 58, 118), radius=22, rings=4, base=8, cords=8, vent=True),
    'purple': dict(cloth=(132, 66, 172), gore=(214, 170, 72), hem=(214, 170, 72), radius=24, rings=5, base=9, cords=8, vent=True,
                   pennants=True),
}
RING = 3      # height of one ring of the dome


def cloth(name, base, weave=4):
    rng = random.Random(name)
    im = Image.new('RGBA', (32, 32))
    px = im.load()
    for y in range(32):
        for x in range(32):
            shade = rng.choice((-5, -3, -1, 0, 0, 1, 3)) - (7 if y % weave == 0 else 0) - (4 if x % 8 == 0 else 0)
            px[x, y] = tuple(max(0, min(255, c + shade)) for c in base) + (255,)
    im.save(TEXTURES / (name + '.png'))


def painted(name, source, colour, band=None):
    """A Storage Crate texture under tier paint: the whole frame, or one band across the planks."""
    im = Image.open(STATION / (source + '.png')).convert('RGBA')
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            if band and not band[0] <= y < band[1]:
                continue
            r, g, b, a = px[x, y]
            light = (r + g + b) / 3 / 150            # the wood grain shows through the paint
            edge = band and y in (band[0], band[1] - 1)
            px[x, y] = tuple(max(0, min(255, round(c * light * (.72 if edge else 1)))) for c in colour) + (a,)
    im.save(TEXTURES / (name + '.png'))


def uv(e):
    """Faces up to a block wide take their own stretch of the tile, so neighbouring pieces do not repeat it."""
    (x, y, z), (X, Y, Z) = e['from'], e['to']
    spans = {'up': (x, z, X - x, Z - z), 'down': (x, z, X - x, Z - z), 'north': (x, y, X - x, Y - y),
             'south': (x, y, X - x, Y - y), 'east': (z, y, Z - z, Y - y), 'west': (z, y, Z - z, Y - y)}
    for face, (u, v, w, h) in spans.items():
        w, h = min(16, max(.25, w)), min(16, max(.25, h))
        u, v = min(u % 16, 16 - w), min(v % 16, 16 - h)
        e['faces'][face]['uv'] = [u, v, u + w, v + h]
    return e


def part(lo, hi, tex, name, rotation=None):
    return uv(box([round(v, 3) for v in lo], [round(v, 3) for v in hi], tex, name, rotation))


def merged(cells):
    """Grid cells (i, j) joined into rectangles of at most 8 by 8 cells (one block), row by row."""
    left = set(cells)
    out = []
    for i, j in sorted(cells):
        if (i, j) not in left:
            continue
        w = 1
        while w < 8 and (i + w, j) in left:
            w += 1
        h = 1
        while h < 8 and all((i + k, j + h) in left for k in range(w)):
            h += 1
        left -= {(i + k, j + l) for k in range(w) for l in range(h)}
        out.append((i, j, w, h))
    return out


def ring(inner, outer, y, Y, gores, name):
    """The cells whose centres lie between two radii, as boxes; each of the eight gores takes its own cloth."""
    reach = int(outer // CELL) + 1
    cells = {}
    for i in range(-reach, reach):
        for j in range(-reach, reach):
            cx, cz = (i + .5) * CELL, (j + .5) * CELL
            if inner <= math.hypot(cx, cz) < outer:
                gore = int((math.degrees(math.atan2(cz, cx)) + 360 + 22.5) // 45) % 2
                cells.setdefault(gores[gore], []).append((i, j))
    out = []
    for tex, group in cells.items():
        for i, j, w, h in merged(group):
            out.append(part([CENTRE + i * CELL, y, CENTRE + j * CELL],
                            [CENTRE + (i + w) * CELL, Y, CENTRE + (j + h) * CELL], tex, name))
    return out


def cord(start, end, name='cord'):
    """A rope from one point to another: an upright rod tilted about x, then turned about y."""
    d = [b - a for a, b in zip(start, end)]
    length = math.sqrt(sum(v * v for v in d))
    tilt = math.degrees(math.acos(d[1] / length))
    turn = math.degrees(math.atan2(d[0], d[2]))
    x, y, z = start
    return part([x - .3, y, z - .3], [x + .3, y + length, z + .3], 'rope', name,
                {'origin': [round(v, 3) for v in start], 'x': round(tilt, 2), 'y': round(turn, 2), 'z': 0})


def parachute(tier, t):
    radius, rings, base = t['radius'], t['rings'], t['base']
    gores = (f'cloth_{tier}', f'gore_{tier}')
    hem = f'hem_{tier}'
    edge = [radius * math.sqrt(1 - (k / rings) ** 2) for k in range(rings)]
    e = ring(radius - CELL, radius, base - 2, base, (hem, hem), 'hem')
    for k in range(rings):
        # Each ring reaches a cell under the next, so the dome is closed and hollow; the last one is the crown.
        inner = max(0, edge[k + 1] - CELL) if k + 1 < rings else (4 if t.get('vent') else 0)
        e += ring(inner, edge[k], base + k * RING, base + (k + 1) * RING, gores, f'ring {k + 1}')
    top = base + rings * RING
    if t.get('vent'):
        e += ring(0, 6, top + 2, top + 3, (hem, hem), 'vent cap')
        e += [part([CENTRE + dx - .4, top, CENTRE + dz - .4], [CENTRE + dx + .4, top + 2, CENTRE + dz + .4], 'rope', 'cap stay')
              for dx, dz in ((4, 0), (-4, 0), (0, 4), (0, -4))]
    if t.get('pennants'):
        e.append(part([CENTRE - 1, top + 3, CENTRE - 1], [CENTRE + 1, top + 6, CENTRE + 1], hem, 'finial'))
        for k in range(8):
            a = math.radians(k * 45)
            x, z = CENTRE + (radius - 1) * math.cos(a), CENTRE + (radius - 1) * math.sin(a)
            e.append(part([x - .75, base - 8, z - .75], [x + .75, base - 2, z + .75], gores[k % 2], 'pennant'))
    # Cords from the lid's corners out to the hem; the bigger canopies take four more from the middle of each side.
    reach = radius - CELL
    for sx, sz in ((-1, -1), (-1, 1), (1, -1), (1, 1)):
        corner = [CENTRE + 8 * sx, LID, CENTRE + 8 * sz]
        e.append(cord(corner, [CENTRE + reach * .707 * sx, base - 1, CENTRE + reach * .707 * sz]))
        e.append(part([corner[0] - .8, LID, corner[2] - .8], [corner[0] + .8, LID + 1.2, corner[2] + .8], 'rope', 'knot'))
    if t['cords'] == 8:
        for sx, sz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            e.append(cord([CENTRE + 8 * sx, LID, CENTRE + 8 * sz], [CENTRE + reach * sx, base - 1, CENTRE + reach * sz]))
    e += [part([0, LID, 7], [16, LID + .6, 9], 'rope', 'lid strap'), part([7, LID, 0], [9, LID + .7, 16], 'rope', 'lid strap')]
    save(f'parachute_{tier}', e, f'cloth_{tier}')
    return len(e)


def save(name, elements, particle):
    keys = {f['texture'][1:] for el in elements for f in el['faces'].values()}
    textures = {key: 'arksurvivalreturns:block/' + ('station/' if key == 'rope' else 'drop/') + key for key in sorted(keys)}
    textures['particle'] = 'arksurvivalreturns:block/drop/' + particle
    model = {'parent': 'minecraft:block/block', 'textures': textures, 'display': DISPLAY, 'elements': elements}
    (MODELS / (name + '.json')).write_text(json.dumps(model, indent=2) + '\n', encoding='utf8')


def loot_crate(tier):
    """The Storage Crate's geometry untouched: only its two materials change."""
    model = {'parent': 'arksurvivalreturns:block/station/storage_crate_item',
             'textures': {'crate': f'arksurvivalreturns:block/drop/crate_{tier}',
                          'crate_frame': f'arksurvivalreturns:block/drop/frame_{tier}',
                          'particle': f'arksurvivalreturns:block/drop/crate_{tier}'}}
    (MODELS / f'loot_crate_{tier}.json').write_text(json.dumps(model, indent=2) + '\n', encoding='utf8')


def main():
    MODELS.mkdir(parents=True, exist_ok=True)
    TEXTURES.mkdir(parents=True, exist_ok=True)
    for tier, t in TIERS.items():
        cloth(f'cloth_{tier}', t['cloth'])
        cloth(f'gore_{tier}', t['gore'])
        cloth(f'hem_{tier}', t['hem'], weave=2)
        paint = t['cloth'] if tier != 'white' else (238, 236, 226)
        painted(f'crate_{tier}', 'crate', paint, band=(12, 20))
        painted(f'frame_{tier}', 'crate_frame', paint)
        loot_crate(tier)
        print(f'{tier}: {parachute(tier, t)} elements')
    if '--preview' in sys.argv:
        preview()


def preview():
    from render_camp_assets import render
    from PIL import ImageFont
    font = lambda n: ImageFont.truetype(str(ROOT / 'tools/fonts/Bitter.ttf'), n)
    m = lambda name: 'arksurvivalreturns:block/drop/' + name
    sheet = Image.new('RGB', (1640, 760), '#eee9df')
    d = ImageDraw.Draw(sheet)
    d.text((40, 24), 'SUPPLY DROPS', font=font(34), fill='#343e30')
    notes = {'white': '3 slots', 'green': '5 slots', 'blue': '7 slots', 'purple': '9 slots'}
    for i, tier in enumerate(TIERS):
        x = 30 + i * 400
        d.rounded_rectangle((x, 90, x + 380, 730), radius=10, fill='#f8f5ed')
        d.text((x + 18, 102), f'{tier.title()}  /  {notes[tier]}', font=font(22), fill='#414c39')
        im = render([(m(f'loot_crate_{tier}'), (0, 0, 0)), (m(f'parachute_{tier}'), (0, 32, 0))], (360, 420), 6.6, (8, 27, 8))
        sheet.paste(im, (x + 10, 140), im)
        im = render([(m(f'loot_crate_{tier}'), (0, 0, 0))], (200, 160), 8, (8, 8, 8))
        sheet.paste(im, (x + 90, 560), im)
    target = ROOT / 'docs/drop-assets.png'
    sheet.save(target)
    print(target)


if __name__ == '__main__':
    main()
