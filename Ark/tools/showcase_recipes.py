"""Recipes page of the showcase: the workstation screens of the crafting rework (P14).

The page draws each station with tools/workstation_ui.js on the shared graph core tools/workstation_graph.js, from
the design data in design/workstations/ (one file per station, plus graph_style.json for every screen). This module
gathers what the page needs for that data: item names (vanilla and Ark lang, with Ark's renames of vanilla items),
sprites (Ark textures, the vanilla jar, an isometric render for block items), the vanilla bitmap font as a small
glyph atlas, and the inventory presets of the preview. build_showcase.py calls section().
"""
import io
import json
import re
import zipfile
from pathlib import Path

from PIL import Image

ARK = Path(__file__).resolve().parents[1]
HERE = Path(__file__).resolve().parent
DESIGN = ARK / 'design/workstations'
ASSETS = ARK / 'src/main/resources/assets/arksurvivalreturns'
GENERATED = ARK / 'src/generated/resources'
JAR = ARK / 'build/moddev/artifacts/minecraft-patched-26.1.2.109-sources.jar'

# (id, title, design file or None while its screen is undesigned, what it holds)
STATIONS = [
    ('armoury', 'Armoury', 'armoury.json', 'Every weapon, tool and armour piece.'),
    ('working_station', 'Working Station', None,
     'Keeps blocks, materials and camp gear once the Armoury takes the weapons, tools and armour.'),
    ('mortar_and_pestle', 'Mortar & Pestle', None, 'Narcotics and the herbal remedies.'),
    ('medicine_bench', 'Medicine Bench', None, 'Bandages and vitamins, from the Bronze Age.'),
    ('smithing_table', 'Smithing Table', None, 'Upgrades and trims.'),
]
# Item tags in costs: the item that stands for the tag, and its name.
TAGS = {'#minecraft:planks': ('minecraft:oak_planks', 'Any Planks')}
# Block items: top and side textures of their inventory cube.
BLOCKS = {'minecraft:oak_log': ('block/oak_log_top', 'block/oak_log'),
          'minecraft:oak_planks': ('block/oak_planks', 'block/oak_planks')}
STACKABLE = {'minecraft:arrow', 'arksurvivalreturns:tranquilizer_arrow', 'arksurvivalreturns:explosive_arrow',
             'arksurvivalreturns:ammunition'}
FONT_CHARS = ''.join(chr(c) for c in range(33, 127)) + '×·'
STARTER = {'minecraft:stick': 16, 'arksurvivalreturns:plant_fiber': 24, 'arksurvivalreturns:rock': 12, 'minecraft:flint': 3,
           'minecraft:string': 4, 'minecraft:feather': 6, 'arksurvivalreturns:sharp_rock': 3, 'arksurvivalreturns:keratin': 10,
           'minecraft:leather': 5, 'minecraft:bone': 3}


def load(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def names(jar):
    table = json.loads(jar.read('assets/minecraft/lang/en_us.json')) if jar else {}
    table.update(load(GENERATED / 'assets/minecraft/lang/en_us.json'))  # Ark's renames: Stick is the Twig
    table.update(load(GENERATED / 'assets/arksurvivalreturns/lang/en_us.json'))
    return table


def registered(item_id, table):
    ns, path = item_id.split(':')
    return f'item.{ns}.{path}' in table or f'block.{ns}.{path}' in table


def name(item_id, table):
    if item_id in TAGS:
        return TAGS[item_id][1]
    ns, path = item_id.split(':')
    return table.get(f'item.{ns}.{path}') or table.get(f'block.{ns}.{path}') or path.replace('_', ' ').title()


def vanilla(jar, path):
    try:
        return Image.open(io.BytesIO(jar.read(f'assets/minecraft/textures/{path}.png'))).convert('RGBA')
    except (KeyError, AttributeError):
        return None


def iso_block(top, side):
    """A block item as the inventory shows it: three faces of a cube on a 2:1 grid, top lit, sides shaded."""
    canvas = Image.new('RGBA', (32, 32))
    for texture, coeffs, shade in ((side, (1, 0, 0, -0.5, 1, -8), 0.8),       # left face
                                   (side, (1, 0, -16, 0.5, 1, -24), 0.6),    # right face
                                   (top, (0.5, -1, 8, 0.5, 1, -8), 1.0)):    # top face
        face = texture.resize((16, 16), Image.Resampling.NEAREST).transform(
            (32, 32), Image.Transform.AFFINE, coeffs, resample=Image.Resampling.NEAREST, fillcolor=(0, 0, 0, 0))
        if shade != 1:
            r, g, b, a = face.split()
            face = Image.merge('RGBA', [c.point(lambda v, s=shade: round(v * s)) for c in (r, g, b)] + [a])
        canvas.alpha_composite(face)
    return canvas


def sprite(item_id, jar):
    item_id = TAGS.get(item_id, (item_id,))[0]
    if item_id in BLOCKS:
        top, side = (vanilla(jar, path) for path in BLOCKS[item_id])
        return iso_block(top, side) if top and side else None
    ns, path = item_id.split(':')
    texture = f'{ns}:item/{path}'
    model = GENERATED / f'assets/arksurvivalreturns/models/item/{path}.json'
    if ns == 'arksurvivalreturns' and model.is_file():
        texture = load(model).get('textures', {}).get('layer0', texture)  # stand-ins point at vanilla sprites
    tex_ns, tex_path = texture.split(':') if ':' in texture else ('minecraft', texture)
    if tex_ns == 'minecraft':
        image = vanilla(jar, tex_path)
    else:
        file = ASSETS / f'textures/{tex_path}.png'
        image = Image.open(file).convert('RGBA') if file.is_file() else None
    return image.crop((0, 0, image.width, image.width)) if image else None  # animated strips: first frame


def font_atlas(jar, uri):
    """The glyphs the page writes, from the vanilla bitmap fonts, as one 8 px row, with Minecraft's advances."""
    providers = json.loads(jar.read('assets/minecraft/font/include/default.json'))['providers']
    sheets = []
    for provider in providers:
        if provider.get('type') != 'bitmap' or provider.get('height', 8) != 8:
            continue
        image = Image.open(io.BytesIO(jar.read('assets/minecraft/textures/' + provider['file'].split(':')[1]))).convert('RGBA')
        rows = provider['chars']
        sheets.append((image, rows, image.width // len(rows[0]), image.height // len(rows)))
    atlas, advances = Image.new('RGBA', (8 * len(FONT_CHARS), 8)), []
    for index, char in enumerate(FONT_CHARS):
        for image, rows, cw, ch in sheets:  # the first provider with the glyph wins, as in the game
            row = next((r for r, line in enumerate(rows) if char in line), None)
            if row is None:
                continue
            col = rows[row].index(char)
            cell = image.crop((col * cw, row * ch, col * cw + cw, row * ch + ch)).resize((8, 8), Image.Resampling.NEAREST)
            alpha = cell.getchannel('A')
            width = max((x + 1 for x in range(8) for y in range(8) if alpha.getpixel((x, y))), default=0)
            atlas.paste(Image.merge('RGBA', [Image.new('L', (8, 8), 255)] * 3 + [alpha]), (index * 8, 0))
            advances.append(width + 1)
            break
        else:
            advances.append(6)
    return {'atlas': uri(atlas, 'PNG'), 'chars': FONT_CHARS, 'advances': advances}


def published():
    """The Recipes payload of the page as last built: the fallback for vanilla sprites and the font without the jar."""
    page = ARK.parent / 'Ark-Survival-Returns.html'
    found = page.is_file() and re.search(r'<script type="application/json" id="ws-data">(.*?)</script>',
                                         page.read_text(encoding='utf-8'), re.S)
    return json.loads(found.group(1)) if found else {}


def section(uri, e):
    jar = zipfile.ZipFile(JAR) if JAR.is_file() else None
    old = {} if jar else published()
    table = names(jar)
    style = load(DESIGN / 'graph_style.json')
    stations, ids = [], []
    for sid, title, file, about in STATIONS:
        data = load(DESIGN / file) if file else None
        for category in (data or {}).get('categories', []):
            ids.append(category['icon'])
            for entry in category['items']:
                ids.append(entry['item'])
                ids.extend(entry['cost'])
        stations.append({'id': sid, 'title': title, 'about': about, 'data': data})
    items, before = {}, old.get('items', {})
    for item_id in dict.fromkeys(ids):
        image = sprite(item_id, jar)
        items[item_id] = {'name': name(item_id, table) if jar else before.get(item_id, {}).get('name', name(item_id, table)),
                          'sprite': uri(image, 'PNG') if image else before.get(item_id, {}).get('sprite', ''),
                          'stack': 64 if item_id in STACKABLE else 1,
                          'planned': not item_id.startswith('#') and item_id.startswith('arksurvivalreturns:')
                                     and not registered(item_id, table)}
    font = font_atlas(jar, uri) if jar else old.get('font')
    if not font:
        raise SystemExit(f'{JAR} is missing: run ./gradlew build once so the Recipes page gets the vanilla font')
    costs = list(dict.fromkeys(i for s in stations if s['data'] for c in s['data']['categories']
                               for entry in c['items'] for i in entry['cost']))
    payload = {'style': style, 'stations': stations, 'items': items, 'font': font,
               'presets': {'empty': {}, 'starter': STARTER, 'stocked': {i: 64 for i in costs}}}
    armoury = stations[0]['data']
    return {
        'RECIPES_DATA': json.dumps(payload, ensure_ascii=False, separators=(',', ':')).replace('</', '<\\/'),
        'RECIPES_JS': (HERE / 'workstation_graph.js').read_text(encoding='utf-8') + '\n'
                      + (HERE / 'workstation_ui.js').read_text(encoding='utf-8'),
        'RECIPES_COUNT': sum(len(c['items']) for c in armoury['categories']),
        'RECIPES_MATERIALS': len(armoury['categories']),
    }
