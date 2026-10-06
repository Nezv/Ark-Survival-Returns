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


_mesh = rc.mesh


def mesh(identifier, offset=(0, 0, 0)):
    """A model id ending in @180 is turned half round its block: its north front then faces the camera."""
    name, _, turn = identifier.partition('@')
    if not turn:
        return _mesh(name, offset)
    flip = np.array([-1.0, 1.0, -1.0])
    return [(v * flip + (16, 0, 16) + np.array(offset, dtype=float), normal * flip, uv, tex) for v, normal, uv, tex in _mesh(name)]


# The camp renderer resolves models and textures through these module globals.
rc.model, rc.texture, rc.mesh = model, texture, mesh

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
    ('forge', 'Primitive Forge', 'In game', '2 blocks tall', '8 Cobblestone + Stone Fire',
     'A stone bloomery that replaces the furnace for everything that is not food, other mods\' recipes included.',
     [(A + 'prehistoric/primitive_forge_lit_lower', (0, 0, 0)), (A + 'prehistoric/primitive_forge_lit_upper', UP)]),
    ('rack', 'Drying Rack', 'In game', '2 blocks tall', '4 Stick + Fiber',
     'Food hangs from the top lines and cures from Dried Meat I to III; rations dry on the bottom shelf.',
     [(A + 'camp/drying_rack_lower', (0, 0, 0)), (A + 'camp/drying_rack_upper', UP),
      *[(A + f'camp/rack_meat_{i}', (0, 0, 0)) for i in (1, 2, 3)], (A + 'camp/rack_ready', (0, 0, 0))]),
    ('mattress', 'Mattress', 'In game', '2 blocks long', '9 Fiber',
     'A grass mat to sleep the night on. It never sets a respawn point.',
     [(A + 'prehistoric/primitive_bedroll_head', (0, 0, 0)), (A + 'prehistoric/primitive_bedroll_foot', (0, 0, 16))]),
    ('bedroll', 'Bedroll', 'In game', '2 blocks long', 'Mattress + 3 Hide + 2 Fiber',
     'Sets your respawn point, and keeps it even if the bedroll is destroyed. Two side by side join into one.',
     [(A + 'camp/bedroll_head_west', (0, 0, 0)), (A + 'camp/bedroll_foot_west', (0, 0, 16))]),
    ('trough', 'Feeding Trough', 'In game', '1 block, 10 woods', '4 Planks + Resin + Fiber',
     'Feeds hungry tames nearby. One per wood type.',
     [(A + 'camp/trough_oak', (0, 0, 0)), (A + 'camp/trough_feed', (0, 0, 0))]),
    ('working_station', 'Working Station', 'In game', '1 block', '2 Planks + 2 Logs',
     'Replaces the crafting table: a graph of what is put together by hand (carpentry, textiles, camp gear, accessories). Vanilla tables that reach an inventory turn into it.',
     [(A + 'station/working_station', (0, 0, 0))]),
    ('armoury', 'Armoury', 'In game', '1 block', '2 Logs + 4 Planks + 4 Rock + 3 Fiber',
     'Every weapon, tool and armour piece is made here, on a graph that opens with the permanent level of the player.',
     [(A + 'station/armoury', (0, 0, 0))]),
    ('saddlery', 'Saddlery', 'In game', '2 blocks tall', '2 Logs + 4 Planks + 2 Hide + 4 Fiber',
     'Tack for tames is stitched here: the saddle, leads and the pack harnesses. The Chronicle asks for it at We should ride them.',
     [(A + 'station/saddlery_lower@180', (0, 0, 0)), (A + 'station/saddlery_upper@180', UP)]),
    ('storage_crate', 'Storage Crate', 'In game', '1 block, joins', '8 Planks',
     'Replaces every wooden chest. 27 slots; crates side by side join into one look, but each keeps and shows only its own slots.',
     [(A + 'station/storage_crate_item', (0, 0, 0))]),
    ('medicine_bench', 'Medicine Bench', 'In game', '1 block', '2 Bronze + Bottle + Glass + 3 Planks + 2 Logs',
     'Bronze Age medicine: bandages, remedies and tonics.',
     [(A + 'station/medicine_bench', (0, 0, 0))]),
    ('crusher', 'Crusher', 'In game', '1 block', '2 Logs + Grindstone + 5 Cobblestone',
     'Unpowered: the flywheel turns while it grinds stone to gravel and sand, bones to meal, wool to string, and coal with sulphur to gunpowder.',
     [(A + 'station/crusher_spin0', (0, 0, 0))]),
    ('smithing_table', 'Smithing Table', 'In game', '1 block', '2 Iron + 2 Smooth Stone + 2 Planks',
     "Ark's own smithing table replaces the vanilla one: metal goods are hammered here, and armour trims applied.",
     [(A + 'station/smithing_table', (0, 0, 0))]),
    ('bush', 'Berry Bush', 'In game', '1 block, 4 kinds', 'Plant any Ark berry',
     'Four berries with their own effects. All four still share the vanilla sweet berry art.',
     [(A + 'tintoberry_bush_stage3', (0, 0, 0))]),
    ('nest', 'Flyer Nest', 'In game', '1 block, 5 kinds', 'Built by flyers',
     'Each flyer keeps one nest on a cliff or shore and lays eggs you can collect.',
     [(A + 'pteranodon_nest', (0, 0, 0))]),
    ('mortar', 'Mortar & Pestle', 'In game', 'Small, 1 block', 'Rock + 3 Cobblestone',
     'Everything ground or pounded: Narcotics from Blackberries, the Healing Mixture, dyes from flowers, bone meal, sugar and red ochre.',
     [(A + 'prehistoric/mortar_empty', (0, 0, 0))]),
    ('tin_ore', 'Tin Ore', 'In game', '1 block', 'Found underground',
     'The Bronze Age ore, in stone and deepslate. Smelted with copper into Bronze in the Primitive Forge.',
     [(A + 'tin_ore', (0, 0, 0))]),
    ('sulphur', 'Sulphur Crystals', 'In game', '1 block, 4 stages', 'Grows in caves',
     'Buds grow on budding sulphur into clusters. Crushed with coal, sulphur makes the Bronze Age gunpowder.',
     [(A + 'sulphur_block', (0, 0, 0)), (A + 'sulphur_cluster', UP)]),
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
