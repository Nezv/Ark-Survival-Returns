"""One standard render per Ark block for the showcase, straight from the shipped model JSONs.

Every block uses the same camera, light and scale-to-fit, so the cards line up. Models resolve from the
hand-authored assets, then the generated ones (runData), then the design pack for planned blocks.
Run from Ark: python tools/render_blocks.py (writes build/block-renders/*.png); build_showcase.py imports it.
"""
import io
import json
from pathlib import Path

import numpy as np
from PIL import Image

import render_camp_assets as rc

ARK = Path(__file__).resolve().parents[1]
ROOTS = [ARK / 'src/main/resources/assets/arksurvivalreturns',
         ARK / 'src/generated/resources/assets/arksurvivalreturns',
         ARK / 'design/prehistoric-camp/assets/arksurvivalreturns']
OUT = ARK / 'build/block-renders'
TILE = (420, 300)


def find(kind, path):
    for root in ROOTS:
        file = root / kind / path
        if file.is_file():
            return file
    raise FileNotFoundError(f'{kind}/{path}')


def model(identifier):
    ns, path = identifier.split(':') if ':' in identifier else ('minecraft', identifier)
    if ns == 'minecraft':
        with rc.ZipFile(rc.JAR) as z:
            data = json.loads(z.read(f'assets/minecraft/models/{path}.json'))
    else:
        data = json.loads(find('models', path + '.json').read_text(encoding='utf-8'))
    parent = model(data['parent']) if 'parent' in data and data['parent'] not in ('minecraft:block/block', 'block/block') else {}
    return {**parent, **data, 'textures': {**parent.get('textures', {}), **data.get('textures', {})}}


def texture(name, textures):
    while name.startswith('#'):
        name = textures[name[1:]]
    ns, path = name.split(':') if ':' in name else ('minecraft', name)
    if ns == 'minecraft':
        with rc.ZipFile(rc.JAR) as z:
            image = Image.open(io.BytesIO(z.read(f'assets/minecraft/textures/{path}.png'))).convert('RGBA')
    else:
        image = Image.open(find('textures', path + '.png')).convert('RGBA')
    return np.array(image.crop((0, 0, image.width, image.width)))


# The camp renderer resolves models and textures through these module globals.
rc.model, rc.texture = model, texture

A = 'arksurvivalreturns:block/'
UP = (0, 16, 0)

# key, name, status, size, recipe, what it does, [(model, offset)]
BLOCKS = [
    ('loose_rock', 'Loose Rock', 'In game', 'Ground cover', 'Found in every biome',
     'Lies on the ground in every Overworld biome and is picked up by hand. The first resource.',
     [(A + 'loose_rock_stone', (0, 0, 0)), (A + 'loose_rock_granite', (6, 0, -3)), (A + 'loose_rock_sandstone', (2, 0, 5))]),
    ('stone_fire', 'Stone Fire', 'In game', '1 block', '5 Rock + Stick',
     'Holds fuel and is lit with the Fire Starter. Cooks four items on the spit and turns sticks into torches.',
     [(A + 'prehistoric/stone_fire_cooked', (0, 0, 0))]),
    ('pot', 'Cooking Pot', 'In game', 'Sits on the fire', '4 Cobblestone + 2 Fiber',
     'Rests on the top course of a lit Stone Fire and cooks two meals; the spit lifts while it is there.',
     [(A + 'prehistoric/stone_fire_pot_base', (0, 0, 0)), (A + 'camp/cooking_pot_stones', UP)]),
    ('forge', 'Primitive Forge', 'In game', '2 blocks tall', '5 Clay + Stone Fire + 3 Cobblestone',
     'A clay bloomery that replaces the furnace for everything that is not food, other mods\' recipes included.',
     [(A + 'prehistoric/primitive_forge_lit_lower', (0, 0, 0)), (A + 'prehistoric/primitive_forge_lit_upper', UP)]),
    ('rack', 'Drying Rack', 'In game', '2 blocks tall', '4 Stick + Fiber',
     'Food hangs from the top lines and cures from Dried Meat I to III; rations dry on the bottom shelf.',
     [(A + 'camp/drying_rack_lower', (0, 0, 0)), (A + 'camp/drying_rack_upper', UP),
      *[(A + f'camp/rack_meat_{i}', (0, 0, 0)) for i in (1, 2, 3)], (A + 'camp/rack_ready', (0, 0, 0))]),
    ('bedroll', 'Primitive Bedroll', 'In game', '2 blocks long', '4 Fiber + Leather',
     'A grass mat by Astra that sets your respawn point, and keeps it even if the bedroll is destroyed.',
     [(A + 'prehistoric/primitive_bedroll_head', (0, 0, 0)), (A + 'prehistoric/primitive_bedroll_foot', (0, 0, 16))]),
    ('trough', 'Feeding Trough', 'In game', '1 block, 10 woods', '5 Planks + Resin + Fiber',
     'Feeds hungry tames nearby. One per wood type.',
     [(A + 'camp/trough_oak', (0, 0, 0)), (A + 'camp/trough_feed', (0, 0, 0))]),
    ('cache', 'Recovery Cache', 'In game', '1 block', 'Placed where you die',
     'Marks a death site. The loot lives in world data, so breaking the marker never loses it.',
     [(A + 'recovery_cache', (0, 0, 0))]),
    ('bush', 'Berry Bush', 'In game', '1 block, 4 kinds', 'Plant any Ark berry',
     'Four berries with their own effects. All four still share the vanilla sweet berry art.',
     [(A + 'tintoberry_bush_stage3', (0, 0, 0))]),
    ('nest', 'Flyer Nest', 'In game', '1 block, 5 kinds', 'Built by flyers',
     'Each flyer keeps one nest on a cliff or shore and lays eggs you can collect.',
     [(A + 'pteranodon_nest', (0, 0, 0))]),
    ('mortar', 'Mortar and Pestle', 'Planned (B02)', '1 block', 'To be decided (F12)',
     'Grinds Sedative into Narcotics and starts the medicine path. Model from the design pack.',
     [(A + 'prehistoric/mortar_berry_whole', (0, 0, 0))]),
]


def bounds(objects):
    points = np.concatenate([v for identifier, offset in objects for v, *_ in rc.mesh(identifier, offset)])
    return points.min(0), points.max(0)


def render(objects):
    """Render once at a size that fits the tile, then crop to the model and pad to the tile."""
    low, high = bounds(objects)
    target = tuple((low + high) / 2)
    extent = float(np.linalg.norm(high - low))
    scale = min(TILE[0] * 1.9, TILE[1] * 2.3) / max(extent, 1.0)
    image = rc.render(objects, (TILE[0] * 2, TILE[1] * 2), scale, target)
    box = image.getbbox()
    image = image.crop(box) if box else image
    image.thumbnail((TILE[0] - 24, TILE[1] - 24), Image.Resampling.LANCZOS)
    tile = Image.new('RGBA', TILE)
    tile.alpha_composite(image, ((TILE[0] - image.width) // 2, TILE[1] - 12 - image.height))
    return tile


def renders():
    """[(block entry, RGBA tile)] for every block whose models resolve."""
    out = []
    for entry in BLOCKS:
        try:
            out.append((entry, render(entry[-1])))
        except (FileNotFoundError, KeyError) as missing:
            print(f'skipped {entry[0]}: {missing}')
    return out


if __name__ == '__main__':
    OUT.mkdir(parents=True, exist_ok=True)
    for entry, tile in renders():
        tile.save(OUT / f'{entry[0]}.png')
    print(f'Wrote {len(BLOCKS)} renders to {OUT}')
