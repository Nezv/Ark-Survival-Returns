"""Author the exclusive workstations as vanilla cuboid models and original pixel materials.

Run from any directory: python tools/build_station_assets.py  (Pillow and numpy; add --preview for docs/station-assets.png)
Geometry lives under models/block/station; StationData owns blockstate/item/recipe wiring.

  working_station   the crafting table: log legs, a thick bench top with a scored grid, vise, mallet, shelf
  storage_crate     every wooden chest: a plank crate whose frame edges and rope handles are separate
                    multipart pieces, hidden where another crate touches, so joined crates read as one
  smithing_table    a stone forge block with an iron anvil top, horn, tongs and hammer
  medicine_bench    herb table: mortar and pestle, three mixtures, a bandage roll and drying herbs
  crusher           stone housing, feed hopper, two rollers and a side flywheel; four spin frames
"""
import json
import math
import random
import sys
from pathlib import Path
from PIL import Image, ImageDraw
from build_camp_assets import ROOT, ASSETS, box, rot, DISPLAY

MODELS = ASSETS / 'models/block/station'
TEXTURES = ASSETS / 'textures/block/station'

PALETTE = {
    'bench_top': ((170, 124, 74), 'grid'),
    'planks': ((150, 108, 64), 'planks'),
    'planks_dark': ((104, 72, 42), 'planks'),
    'log': ((96, 70, 44), 'bark'),
    'log_end': ((168, 128, 80), 'rings'),
    'iron': ((88, 90, 88), 'metal'),
    'iron_dark': ((52, 54, 55), 'metal'),
    'stone': ((122, 120, 112), 'stone'),
    'stone_dark': ((88, 86, 80), 'stone'),
    'crate': ((164, 122, 72), 'crate'),
    'crate_frame': ((92, 62, 36), 'planks'),
    'rope': ((175, 146, 94), 'rope'),
    'linen': ((214, 202, 170), 'weave'),
    'herb': ((96, 128, 60), 'leaf'),
    'herb_dry': ((142, 132, 70), 'leaf'),
    'glass_green': ((98, 160, 112), 'glass'),
    'glass_red': ((168, 64, 62), 'glass'),
    'glass_blue': ((82, 118, 170), 'glass'),
    'cork': ((150, 112, 72), 'stone'),
    'gear': ((126, 88, 52), 'planks'),
    'roller': ((96, 94, 90), 'metal'),
    'hopper': ((44, 40, 36), 'stone'),
}


def material(name):
    base, style = PALETTE[name]
    rng = random.Random(name)
    im = Image.new('RGBA', (32, 32))
    px = im.load()
    for y in range(32):
        for x in range(32):
            noise = rng.choice((-4, -2, -1, 0, 0, 1, 2, 4))
            grain = 4 * math.sin(y * 1.3 + x * .05) if style in ('planks', 'grid', 'crate', 'bark') else 0
            px[x, y] = tuple(max(0, min(255, round(c + noise + grain))) for c in base) + (255,)
    d = ImageDraw.Draw(im)
    dark = tuple(max(0, c - 28) for c in base) + (255,)
    light = tuple(min(255, c + 22) for c in base) + (255,)
    if style in ('planks', 'crate', 'grid'):
        for y in range(0, 32, 8):
            d.line((0, y, 31, y), fill=dark)
            d.line((0, y + 1, 31, y + 1), fill=light)
        for i, y in enumerate(range(0, 32, 8)):
            x = (i * 13 + 5) % 32
            d.line((x, y + 1, x, y + 7), fill=dark)
    if style == 'crate':
        d.line((0, 31, 31, 0), fill=dark, width=2)       # the diagonal brace of a slatted crate
        d.line((1, 31, 31, 1), fill=light)
    if style == 'grid':
        for v in (10, 21):
            d.line((v, 0, v, 31), fill=dark)
            d.line((0, v, 31, v), fill=dark)
    if style == 'bark':
        for x in range(1, 32, 5):
            d.line((x, 0, x + rng.randrange(-1, 2), 31), fill=dark)
    if style == 'rings':
        for r in (4, 8, 12):
            d.ellipse((16 - r, 16 - r, 16 + r, 16 + r), outline=dark)
    if style == 'metal':
        for _ in range(14):
            x, y = rng.randrange(31), rng.randrange(31)
            d.line((x, y, x + 1, y), fill=light)
        d.rectangle((0, 0, 31, 31), outline=dark)
    if style == 'stone':
        for _ in range(9):
            x, y = rng.randrange(28), rng.randrange(28)
            d.line((x, y, x + rng.randrange(2, 5), y + rng.randrange(-1, 2)), fill=dark)
    if style == 'rope':
        for x in range(-32, 33, 5):
            d.line((x, 0, x + 31, 31), fill=dark)
    if style == 'weave':
        for v in range(0, 32, 3):
            d.line((v, 0, v, 31), fill=light)
    if style == 'leaf':
        for _ in range(10):
            x, y = rng.randrange(30), rng.randrange(30)
            d.line((x, y, x + 2, y + 2), fill=light)
            d.line((x + 1, y, x + 1, y + 3), fill=dark)
    if style == 'glass':
        d.line((6, 3, 6, 28), fill=(255, 255, 255, 255), width=2)
    im.save(TEXTURES / (name + '.png'))


def save(name, elements, particle=None):
    keys = {f['texture'][1:] for e in elements for f in e['faces'].values()}
    textures = {key: 'arksurvivalreturns:block/station/' + key for key in sorted(keys)}
    textures['particle'] = 'arksurvivalreturns:block/station/' + (particle or sorted(keys)[0])
    model = {'parent': 'minecraft:block/block', 'textures': textures, 'display': DISPLAY, 'elements': elements}
    (MODELS / (name + '.json')).write_text(json.dumps(model, indent=2) + '\n', encoding='utf8')


def cull(element, *faces):
    """Faces on the block boundary may be hidden by a solid neighbour."""
    for face in faces:
        element['faces'][face]['cullface'] = face
    return element


def legs(tex, inset=1, top=12, size=2.5):
    return [box([x, 0, z], [x + size, top, z + size], tex, 'leg')
            for x in (inset, 16 - inset - size) for z in (inset, 16 - inset - size)]


# ------------------------------------------------------------------------------------ working station

def working_station():
    e = legs('log')
    e += [box([0, 12, 0], [16, 15, 16], 'bench_top', 'bench top with a scored grid'),
          box([1.5, 10.5, 1], [14.5, 12, 2.2], 'planks_dark', 'front apron'),
          box([1.5, 10.5, 13.8], [14.5, 12, 15], 'planks_dark', 'back apron'),
          box([1.5, 3, 1.5], [14.5, 4, 14.5], 'planks', 'lower shelf'),
          box([3, 4, 4], [9, 6.5, 6.5], 'log', 'spare log on the shelf'),
          box([9.5, 4, 3.5], [13, 5, 12], 'planks', 'stacked boards'),
          # A bench vise on the front right corner.
          box([10.5, 15, 0.5], [14.5, 17, 3], 'iron', 'vise jaw'),
          box([11.5, 15.4, 3], [13.5, 16.6, 5], 'iron_dark', 'vise screw block'),
          box([12.2, 15.7, -0.5], [12.8, 16.3, 0.5], 'iron_dark', 'vise handle'),
          # A wooden mallet lying across the top.
          box([2.5, 15, 10], [9.5, 15.8, 10.8], 'planks_dark', 'mallet handle'),
          box([9, 15, 9], [11.5, 17, 11.8], 'log', 'mallet head'),
          # Hand saw hung on the west side.
          box([-0.3, 6.5, 4], [0, 10.5, 12], 'iron', 'hand saw blade'),
          box([-0.5, 9.5, 11], [0, 11.5, 13], 'planks_dark', 'saw grip')]
    save('working_station', e, 'bench_top')


# ------------------------------------------------------------------------------------ storage crate

FACES = ('north', 'south', 'east', 'west', 'up', 'down')
EDGES = [('up', 'north'), ('up', 'south'), ('up', 'east'), ('up', 'west'),
         ('down', 'north'), ('down', 'south'), ('down', 'east'), ('down', 'west'),
         ('north', 'east'), ('north', 'west'), ('south', 'east'), ('south', 'west')]


def edge_box(a, b):
    """A 2-pixel frame beam along the edge shared by faces a and b, raised 0.05 off the plank faces."""
    lo, hi = [0.0, 0.0, 0.0], [16.0, 16.0, 16.0]
    for face in (a, b):
        axis = {'east': 0, 'west': 0, 'up': 1, 'down': 1, 'north': 2, 'south': 2}[face]
        if face in ('east', 'up', 'south'):
            lo[axis], hi[axis] = 14.0, 16.05
        else:
            lo[axis], hi[axis] = -0.05, 2.0
    return box(lo, hi, 'crate_frame', f'{a}-{b} frame')


def storage_crate():
    core = cull(box([0, 0, 0], [16, 16, 16], 'crate', 'plank body'), *FACES)
    save('storage_crate_core', [core], 'crate')
    for a, b in EDGES:
        save(f'storage_crate_edge_{a}_{b}', [edge_box(a, b)], 'crate_frame')
    handles = {}
    for side in ('north', 'south', 'east', 'west'):
        if side in ('north', 'south'):
            z0, z1 = (-0.6, 0) if side == 'north' else (16, 16.6)
            h = [box([5, 9, z0], [11, 9.8, z1], 'rope', 'rope handle'),
                 box([5, 7.5, z0], [5.8, 9.8, z1], 'rope', 'handle knot'),
                 box([10.2, 7.5, z0], [11, 9.8, z1], 'rope', 'handle knot')]
        else:
            x0, x1 = (-0.6, 0) if side == 'west' else (16, 16.6)
            h = [box([x0, 9, 5], [x1, 9.8, 11], 'rope', 'rope handle'),
                 box([x0, 7.5, 5], [x1, 9.8, 5.8], 'rope', 'handle knot'),
                 box([x0, 7.5, 10.2], [x1, 9.8, 11], 'rope', 'handle knot')]
        handles[side] = h
        save(f'storage_crate_handle_{side}', h, 'rope')
    item = [core] + [edge_box(a, b) for a, b in EDGES] + handles['north'] + handles['east']
    save('storage_crate_item', item, 'crate')


# ------------------------------------------------------------------------------------ smithing table

def smithing_table():
    e = [box([1, 0, 1], [15, 9, 15], 'stone', 'stone forge block'),
         box([0, 0, 0], [16, 2, 16], 'stone_dark', 'plinth'),
         box([0, 9, 0], [16, 12, 16], 'iron_dark', 'iron working plate'),
         box([3, 12, 5], [13, 15, 11], 'iron', 'anvil body'),
         box([13, 12.8, 6.5], [16, 14.5, 9.5], 'iron', 'anvil horn'),
         box([1, 12.5, 6], [3, 14.5, 10], 'iron_dark', 'anvil heel'),
         # Tongs and a hammer resting on the plate.
         box([2, 12, 12], [9, 12.6, 12.6], 'iron_dark', 'tong arm'),
         box([2, 12, 13.2], [9, 12.6, 13.8], 'iron_dark', 'tong arm'),
         box([9, 12, 11.8], [11, 12.9, 14], 'iron', 'tong jaws'),
         box([8, 12, 1.5], [14, 12.8, 2.3], 'planks_dark', 'hammer handle'),
         box([12.8, 12, 0.6], [15.2, 13.8, 3.2], 'iron', 'hammer head'),
         # A quench trough set into the front.
         box([4, 3, -0.5], [12, 7, 1], 'stone_dark', 'quench trough'),
         box([4.5, 6.2, -0.4], [11.5, 6.6, 0.8], 'glass_blue', 'quench water')]
    save('smithing_table', e, 'iron_dark')


# ------------------------------------------------------------------------------------ medicine bench

def medicine_bench():
    e = legs('planks_dark')
    e += [box([0, 12, 0], [16, 14, 16], 'planks', 'bench top'),
          box([1.5, 3, 1.5], [14.5, 4, 14.5], 'planks', 'lower shelf'),
          # Mortar and pestle.
          box([2, 14, 2.5], [6.5, 16, 7], 'stone', 'mortar'),
          box([2.5, 15.9, 3], [6, 16.1, 6.5], 'herb', 'ground herbs'),
          box([4.6, 15, 4.2], [5.4, 18.5, 5], 'stone_dark', 'pestle', rot([5, 16, 4.6], 'z', -22.5)),
          # Three mixtures with cork stoppers.
          box([8.5, 14, 2.5], [10.5, 17, 4.5], 'glass_green', 'green mixture'),
          box([8.9, 17, 2.9], [10.1, 17.8, 4.1], 'cork', 'cork'),
          box([11, 14, 2.5], [13, 16.5, 4.5], 'glass_red', 'red mixture'),
          box([11.4, 16.5, 2.9], [12.6, 17.3, 4.1], 'cork', 'cork'),
          box([13.5, 14, 3.5], [15, 15.5, 5], 'glass_blue', 'blue vial'),
          # A bandage roll and a folded strip.
          box([3, 14, 10], [8, 16, 12.5], 'linen', 'bandage roll'),
          box([8.5, 14, 10.5], [14, 14.4, 13], 'linen', 'bandage strip'),
          # Herb bundles drying under the top.
          box([3, 9, 7.5], [4.5, 12, 9], 'herb_dry', 'drying herbs'),
          box([7, 8.5, 7.5], [8.5, 12, 9], 'herb', 'drying herbs'),
          box([11, 9.5, 7.5], [12.5, 12, 9], 'herb_dry', 'drying herbs'),
          box([2.5, 11.8, 8], [13.5, 12, 8.5], 'rope', 'drying line'),
          # Jars on the shelf.
          box([3, 4, 3], [6, 7, 6], 'glass_green', 'shelf jar'),
          box([9, 4, 9], [12, 6.5, 12], 'planks_dark', 'herb box')]
    save('medicine_bench', e, 'planks')


# ------------------------------------------------------------------------------------ crusher

SPIN = (0, 22.5, 45, -22.5)   # quarter-pitch steps; the gear and rollers repeat every 90 degrees


def spin_angle(base, frame):
    angle = (base + SPIN[frame]) % 90
    return angle - 90 if angle > 45 else angle


def crusher_base():
    e = [box([1, 0, 1], [15, 11, 15], 'stone', 'stone housing'),
         box([0, 0, 0], [16, 1.5, 16], 'stone_dark', 'footing'),
         box([2, 11, 2], [14, 12, 3], 'stone_dark', 'hopper wall'),
         box([2, 11, 13], [14, 12, 14], 'stone_dark', 'hopper wall'),
         box([2, 11, 3], [3, 12, 13], 'stone_dark', 'hopper wall'),
         box([13, 11, 3], [14, 12, 13], 'stone_dark', 'hopper wall'),
         box([1, 12, 1], [15, 14.5, 2.5], 'planks_dark', 'feed chute board'),
         box([1, 12, 13.5], [15, 14.5, 15], 'planks_dark', 'feed chute board'),
         box([3, 10.8, 3], [13, 11, 13], 'hopper', 'hopper throat'),
         box([15, 6, 6], [16.5, 10, 10], 'iron_dark', 'axle bearing'),
         box([5, 1.5, -0.4], [11, 4, 1], 'iron_dark', 'output chute')]
    return e


def crusher_frame(frame):
    """Rollers along x and the side flywheel turn about the x axle through (8, 8, 8)."""
    moving = []
    for z in (5.8, 10.2):
        origin = [8, 11, z]
        for base in (0, 45):
            moving.append(box([3.5, 9.8, z - 1.2], [12.5, 12.2, z + 1.2], 'roller', 'crushing roller',
                              rot(origin, 'x', spin_angle(base, frame))))
    hub = [16.5, 8, 8]
    for base in (0, 45):
        moving.append(box([16.5, 3.5, 3.5], [18, 12.5, 12.5], 'gear', 'flywheel disc', rot(hub, 'x', spin_angle(base, frame))))
    for base in (0, 90):
        moving.append(box([16.8, 2, 7], [17.7, 14, 9], 'gear', 'flywheel teeth', rot(hub, 'x', spin_angle(base, frame))))
    moving.append(box([18, 6.8, 6.8], [19, 9.2, 9.2], 'iron', 'hub cap', rot(hub, 'x', spin_angle(0, frame))))
    moving.append(box([19, 7.4, 11], [20.5, 8.6, 12.2], 'planks_dark', 'crank handle', rot(hub, 'x', spin_angle(0, frame))))
    return moving


def crusher():
    base = crusher_base()
    for frame in range(4):
        save(f'crusher_spin{frame}', base + crusher_frame(frame), 'stone')


def main():
    MODELS.mkdir(parents=True, exist_ok=True)
    TEXTURES.mkdir(parents=True, exist_ok=True)
    for name in PALETTE:
        material(name)
    working_station()
    storage_crate()
    smithing_table()
    medicine_bench()
    crusher()
    print(f'{len(list(MODELS.glob("*.json")))} models, {len(PALETTE)} textures -> {MODELS.relative_to(ROOT)}')
    if '--preview' in sys.argv:
        preview()


def preview():
    from render_camp_assets import render
    from PIL import ImageFont
    font = lambda n: ImageFont.truetype(str(ROOT / 'tools/fonts/Bitter.ttf'), n)
    m = lambda name: 'arksurvivalreturns:block/station/' + name
    crate = lambda: [m('storage_crate_core')]
    sheet = Image.new('RGB', (1500, 900), '#eee9df')
    d = ImageDraw.Draw(sheet)
    d.text((40, 24), 'WORKSTATIONS', font=font(34), fill='#343e30')
    joined = []
    for dx, dz, conn in ((0, 0, {'east'}), (16, 0, {'west'})):
        joined += [(m('storage_crate_core'), (dx, 0, dz))]
        joined += [(m(f'storage_crate_edge_{a}_{b}'), (dx, 0, dz)) for a, b in EDGES if a not in conn and b not in conn]
        joined += [(m(f'storage_crate_handle_{s}'), (dx, 0, dz)) for s in ('north', 'east', 'south', 'west') if s not in conn]
    panels = [('Working Station', [(m('working_station'), (0, 0, 0))], (8, 7, 8), 15),
              ('Storage Crate', [(m('storage_crate_item'), (0, 0, 0))], (8, 8, 8), 14),
              ('Two crates, joined', joined, (16, 8, 8), 9),
              ('Smithing Table', [(m('smithing_table'), (0, 0, 0))], (8, 7, 8), 15),
              ('Medicine Bench', [(m('medicine_bench'), (0, 0, 0))], (8, 8, 8), 15),
              ('Crusher', [(m('crusher_spin0'), (0, 0, 0))], (9, 7, 8), 13)]
    for i, (title, objects, target, scale) in enumerate(panels):
        x, y = 30 + (i % 3) * 490, 90 + (i // 3) * 400
        d.rounded_rectangle((x, y, x + 470, y + 380), radius=10, fill='#f8f5ed')
        d.text((x + 18, y + 12), title, font=font(22), fill='#414c39')
        im = render(objects, (440, 320), scale, target)
        sheet.paste(im, (x + 15, y + 50), im)
    target = ROOT / 'docs/station-assets.png'
    sheet.save(target)
    print(target)


if __name__ == '__main__':
    main()
