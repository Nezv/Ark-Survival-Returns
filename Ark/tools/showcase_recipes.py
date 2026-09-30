"""Recipes page of the showcase: the workstation screens of the crafting rework (P14) and the recipe review.

tools/workstation_plan.py decides where every vanilla and Ark recipe goes and compiles each bench; the page draws the
benches with tools/workstation_ui.js on the shared graph core tools/workstation_graph.js, and lists every recipe with
tools/recipe_review.js, where the user marks what goes, stays or changes and saves design/workstations/vanilla_fates.json.
This module gathers what the page needs: item names (vanilla and Ark lang, with Ark's renames of vanilla items), one
icon atlas for every item the page shows (tools/item_icons.py), the frames (tools/build_workstation_crests.py), the
vanilla bitmap font as a small glyph atlas, and the inventory presets of the preview. build_showcase.py calls section().
"""
import io
import json
import math
import re
import zipfile
from pathlib import Path

from PIL import Image

import workstation_plan as wp
from item_icons import Icons

ARK = Path(__file__).resolve().parents[1]
HERE = Path(__file__).resolve().parent
DESIGN = ARK / 'design/workstations'
CRESTS = DESIGN / 'crests'
GENERATED = ARK / 'src/generated/resources'
JAR = ARK / 'build/moddev/artifacts/minecraft-patched-26.1.2.109-sources.jar'
TITLES = {'armoury': 'Armoury', 'working_station': 'Working Station', 'mortar_and_pestle': 'Mortar & Pestle',
          'medicine_bench': 'Medicine Bench', 'smithing_table': 'Smithing Table', **wp.MACHINES}
TAG_NAMES = {'#minecraft:planks': 'Any Planks', '#minecraft:logs': 'Any Log', '#minecraft:wool': 'Any Wool',
             '#minecraft:wooden_slabs': 'Any Wooden Slab', '#minecraft:coals': 'Coal or Charcoal',
             '#minecraft:stone_crafting_materials': 'Cobblestone (any)', '#minecraft:stone_tool_materials': 'Cobblestone (any)',
             '#minecraft:wooden_tool_materials': 'Any Planks', '#minecraft:trimmable_armor': 'Any Armour Piece',
             '#minecraft:trim_materials': 'Any Trim Material', '#minecraft:dyes': 'Any Dye', '#minecraft:bundles': 'Any Bundle',
             '#minecraft:candles': 'Any Candle', '#minecraft:wool_carpets': 'Any Carpet', '#minecraft:beds': 'Any Bed',
             '#minecraft:decorated_pot_ingredients': 'Brick or Pottery Sherd', '#minecraft:soul_fire_base_blocks': 'Soul Sand or Soil',
             '#minecraft:metal_nuggets': 'Iron or Copper Nugget', '#c:leathers': 'Leather', '#c:ingots/bronze': 'Bronze Ingot',
             '#c:ores/tin': 'Tin Ore', '#arksurvivalreturns:berries': 'Any Berry'}
TAG_ITEMS = {'#c:leathers': 'minecraft:leather', '#c:ingots/bronze': 'arksurvivalreturns:bronze_ingot',
             '#c:ores/tin': 'arksurvivalreturns:tin_ore'}
DISPLAY = {'minecraft:water_bottle': 'Water Bottle'}
STACKABLE = {'minecraft:arrow', 'arksurvivalreturns:tranquilizer_arrow', 'arksurvivalreturns:explosive_arrow',
             'arksurvivalreturns:ammunition'}
FONT_CHARS = ''.join(chr(c) for c in range(33, 127)) + '×·'
STARTER = {'minecraft:stick': 16, 'arksurvivalreturns:plant_fiber': 24, 'arksurvivalreturns:rock': 12, 'minecraft:flint': 3,
           'minecraft:string': 4, 'minecraft:feather': 6, 'arksurvivalreturns:sharp_rock': 3, 'arksurvivalreturns:keratin': 10,
           'minecraft:leather': 5, 'minecraft:bone': 3, '#minecraft:planks': 12, '#minecraft:logs': 6, 'minecraft:cobblestone': 16,
           'arksurvivalreturns:narcoberry': 8, 'minecraft:poppy': 4, 'minecraft:dandelion': 3}


def load(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def names(jar):
    table = json.loads(jar.read('assets/minecraft/lang/en_us.json')) if jar else {}
    table.update(load(GENERATED / 'assets/minecraft/lang/en_us.json'))  # Ark's renames: Stick is the Twig
    table.update(load(GENERATED / 'assets/arksurvivalreturns/lang/en_us.json'))
    return table


def item_name(item_id, table, source):
    if item_id in DISPLAY:
        return DISPLAY[item_id]
    if item_id.startswith('#'):
        if item_id in TAG_NAMES:
            return TAG_NAMES[item_id]
        members = source.members(item_id)
        if len(members) == 1:
            return item_name(members[0], table, source)
        base = item_id.split(':', 1)[1].split('/')[-1].replace('_', ' ')
        return 'Any ' + (base[:-1] if base.endswith('s') and not base.endswith('ss') else base).title()
    ns, path = item_id.split(':')
    if path.endswith('_armor_trim_smithing_template'):  # every template is "Smithing Template" in the lang file
        pattern = table.get(f"trim_pattern.minecraft.{path.replace('_armor_trim_smithing_template', '')}")
        if pattern:
            return pattern + ' Template'
    return table.get(f'item.{ns}.{path}') or table.get(f'block.{ns}.{path}') or path.replace('_', ' ').title()


def short_path(item_id):
    return item_id.split(':', 1)[1] if ':' in item_id else item_id


def representative(item_id, source):
    """The item that stands for a tag in icons."""
    if not item_id.startswith('#'):
        return item_id
    if item_id in TAG_ITEMS:
        return TAG_ITEMS[item_id]
    members = source.members(item_id)
    return members[0] if members else None


def missing_icon():
    image = Image.new('RGBA', (32, 32), (0, 0, 0, 255))
    px = image.load()
    for y in range(32):
        for x in range(32):
            if (x // 16 + y // 16) % 2 == 0:
                px[x, y] = (248, 0, 248, 255)
    return image


def atlas(ids, icons, source, uri):
    """One PNG with every icon on a 32 px grid (cell 0 is the missing texture) and each id's cell."""
    images = [missing_icon()]
    cells = {}
    seen = {}
    for item_id in ids:
        rep = representative(item_id, source)
        image = icons.icon(rep) if rep else None
        if image is None:
            cells[item_id] = 0
            continue
        key = image.tobytes()
        if key not in seen:
            seen[key] = len(images)
            images.append(image)
        cells[item_id] = seen[key]
    cols = 40
    sheet = Image.new('RGBA', (cols * 32, math.ceil(len(images) / cols) * 32))
    for i, image in enumerate(images):
        sheet.alpha_composite(image.resize((32, 32), Image.Resampling.NEAREST), ((i % cols) * 32, (i // cols) * 32))
    return {'image': uri(sheet, 'PNG'), 'cols': cols, 'size': 32}, cells


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
    glyphs, advances = Image.new('RGBA', (8 * len(FONT_CHARS), 8)), []
    for index, char in enumerate(FONT_CHARS):
        for image, rows, cw, ch in sheets:  # the first provider with the glyph wins, as in the game
            row = next((r for r, line in enumerate(rows) if char in line), None)
            if row is None:
                continue
            col = rows[row].index(char)
            cell = image.crop((col * cw, row * ch, col * cw + cw, row * ch + ch)).resize((8, 8), Image.Resampling.NEAREST)
            alpha = cell.getchannel('A')
            width = max((x + 1 for x in range(8) for y in range(8) if alpha.getpixel((x, y))), default=0)
            glyphs.paste(Image.merge('RGBA', [Image.new('L', (8, 8), 255)] * 3 + [alpha]), (index * 8, 0))
            advances.append(width + 1)
            break
        else:
            advances.append(6)
    return {'atlas': uri(glyphs, 'PNG'), 'chars': FONT_CHARS, 'advances': advances}


def published():
    """The Recipes payload of the page as last built: the fallback for the font and names without the jar."""
    page = ARK.parent / 'Ark-Survival-Returns.html'
    found = page.is_file() and re.search(r'<script type="application/json" id="ws-data">(.*?)</script>',
                                         page.read_text(encoding='utf-8'), re.S)
    return json.loads(found.group(1)) if found else {}


def ids_of(trees, records):
    out = []
    for tree in trees.values():
        for category in tree['categories']:
            out.append(category['icon'])
            for entry in category['items']:
                families = entry['items'] if 'group' in entry else [entry]
                if 'group' in entry:
                    out.append(entry['icon'])
                for family in families:
                    for variant in family['variants']:
                        out.append(variant['item'])
                        out.extend(variant['cost'])
    for r in records:
        if r['result']:
            out.append(r['result'])
        out.extend(r['cost'])
        out.extend(r.get('newCost', {}))
    return list(dict.fromkeys(i for i in out if i))


def place_titles(designs):
    titles = {}
    for bench, data in designs.items():
        titles[bench] = {}
        for category in data['categories']:
            titles[bench][category['id']] = category['title']
            for entry in category.get('items', []):
                if 'group' in entry:
                    titles[bench][f"{category['id']}/{entry['group']}"] = f"{category['title']} › {entry['title']}"
    return titles


def compact(record):
    """A review record with short keys and the defaults left out."""
    out = {'i': record['id'], 'r': record['result'] or '', 'c': record['cost'], 'f': record['family'], 'fate': record['fate']}
    for key, short, default in (('source', 's', 'vanilla'), ('type', 't', 'crafting_shaped'), ('count', 'n', 1),
                                ('bench', 'b', ''), ('place', 'p', ''), ('note', 'note', ''), ('by', 'by', 'rule'),
                                ('special', 'sp', False), ('newCost', 'nc', None), ('newCount', 'nn', None)):
        if record.get(key, default) not in (default, None):
            out[short] = record[key]
    return out


def section(uri, e):
    jar = zipfile.ZipFile(JAR) if JAR.is_file() else None
    old = {} if jar else published()
    table = names(jar)
    source = wp.Source()
    records, designs, decisions = wp.plan(source)
    trees = wp.compile_benches(records, designs)
    for tree in trees.values():  # a trim is named after its pattern, not its template
        for category in tree['categories']:
            for entry in category['items']:
                for family in entry['items'] if 'group' in entry else [entry]:
                    for variant in family['variants']:
                        if variant.get('apply'):
                            pattern = short_path(variant['item']).replace('_armor_trim_smithing_template', '')
                            variant['name'] = table.get(f'trim_pattern.minecraft.{pattern}', pattern.title() + ' Armor Trim')
    style = load(DESIGN / 'graph_style.json')
    frames = __import__('build_workstation_crests')
    crest_meta = {'margin': frames.MARGIN, 'top': frames.PANEL_TOP, 'bottom': frames.PANEL_BOTTOM}
    baroque_meta = {'margin': frames.B_MARGIN, 'top': frames.B_TOP, 'bottom': frames.B_BOTTOM}
    sigils = {bench: Image.open(CRESTS / f'{bench}_sigil.png').convert('RGBA') for bench in wp.BENCHES
              if (CRESTS / f'{bench}_sigil.png').is_file()}
    icons = Icons(overrides={f'{wp.NS}:armoury': sigils.get('armoury')} if 'armoury' in sigils else {})
    ids = ids_of(trees, records)
    icon_atlas, cells = atlas(ids, icons, source, uri)
    before = old.get('items', {})
    items = {}
    for item_id in ids:
        planned = item_id.startswith(wp.NS + ':') and f"item.{item_id.replace(':', '.')}" not in table \
            and f"block.{item_id.replace(':', '.')}" not in table
        name = item_name(item_id, table, source) if jar or item_id not in before else before[item_id]['name']
        items[item_id] = {'name': name, 'icon': cells.get(item_id, 0), **({'stack': 64} if item_id in STACKABLE else {}),
                          **({'planned': True} if planned else {})}
    font = font_atlas(jar, uri) if jar else old.get('font')
    if not font:
        raise SystemExit(f'{JAR} is missing: run ./gradlew build once so the Recipes page gets the vanilla font')
    stations = []
    for bench in wp.BENCHES:
        tree = trees[bench]
        crest, baroque = CRESTS / f'{bench}.png', CRESTS / f'{bench}_baroque.png'
        stations.append({'id': bench, 'title': tree['title'], 'accent': tree.get('accent', '#e6e8e1'), 'well': tree.get('well', 'dots'),
                         'crest': {'image': uri(Image.open(crest), 'PNG'), **crest_meta} if crest.is_file() else None,
                         'baroque': {'image': uri(Image.open(baroque), 'PNG'), **baroque_meta} if baroque.is_file() else None,
                         'sigil': uri(sigils[bench], 'PNG') if bench in sigils else '', 'data': tree})
    costs = list(dict.fromkeys(i for tree in trees.values() for c in tree['categories'] for entry in c['items']
                               for family in (entry['items'] if 'group' in entry else [entry])
                               for variant in family['variants'] for i in variant['cost']))
    counts = wp.summary(records)
    payload = {'style': style, 'stations': stations, 'items': items, 'font': font, 'atlas': icon_atlas,
               'presets': {'empty': {}, 'starter': STARTER, 'stocked': {i: 64 for i in costs}},
               'review': {'records': [compact(r) for r in records], 'decisions': decisions,
                          'benches': TITLES, 'places': place_titles(designs)}}
    armoury = trees['armoury']
    families = sum(len(entry['items']) if 'group' in entry else 1 for tree in trees.values()
                   for c in tree['categories'] for entry in c['items'])
    return {
        'RECIPES_DATA': json.dumps(payload, ensure_ascii=False, separators=(',', ':')).replace('</', '<\\/'),
        'RECIPES_JS': '\n'.join((HERE / name).read_text(encoding='utf-8')
                                for name in ('workstation_graph.js', 'workstation_ui.js', 'recipe_review.js')),
        'RECIPES_COUNT': sum(len(c['items']) for c in armoury['categories']),
        'RECIPES_MATERIALS': len(armoury['categories']),
        'RECIPES_FAMILIES': families,
        'RECIPES_VANILLA': counts['vanilla']['go'] + counts['vanilla']['stay'] + counts['vanilla']['change'] + counts['vanilla']['decide'],
        'RECIPES_DECIDE': counts['vanilla']['decide'],
    }
