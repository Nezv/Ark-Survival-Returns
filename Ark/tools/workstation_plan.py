"""Crafting rework plan (P14): where every recipe goes once the 3x3 grid is gone.

Reads every recipe of the vanilla jar and of the mod's generated data and gives each one a fate (go, stay, change or
decide), a bench (or the machine that keeps it), and a place in that bench's graph: material / group, plus a family
that folds variants such as the twelve wood types into one node. Three layers decide, the last one winning:

1. The rules in propose(): proposals from the theme policy, the recipe gates and what each bench is for.
2. The bench design files, design/workstations/<bench>.json: a recipe a bench writes out with its own cost is a
   change (a stay when the cost is still the old grid's). Items they list without a recipe anywhere are new.
3. design/workstations/vanilla_fates.json: the user's decisions, which the showcase review list saves. A key is a
   recipe id, or "family:<name>" for every recipe of a family.

compile_benches() turns the plan into one explicit tree per bench: what the showcase draws and, once shipped, what the
mod's workstation data will hold. Run from Ark/: python tools/workstation_plan.py prints the totals.
"""
import json
import re
import zipfile
from collections import Counter
from pathlib import Path

ARK = Path(__file__).resolve().parents[1]
DESIGN = ARK / 'design/workstations'
JAR = ARK / 'build/moddev/artifacts/minecraft-patched-26.1.2.109-merged.jar'
ARK_DATA = ARK / 'src/generated/resources/data/arksurvivalreturns'
POLICY = ARK / 'src/main/java/dev/nez/arksurvivalreturns/feature/theme/ThemePolicy.java'
PRIMITIVE = ARK / 'src/main/java/dev/nez/arksurvivalreturns/feature/primitive/PrimitiveEvents.java'
NS = 'arksurvivalreturns'

BENCHES = ['armoury', 'working_station', 'mortar_and_pestle', 'medicine_bench', 'smithing_table']
MACHINES = {'primitive_forge': 'Primitive Forge', 'stone_fire': 'Stone Fire', 'stonecutter': 'Stonecutter'}
FATES = ('go', 'stay', 'change', 'decide')
# The Ark blocks that replace vanilla ones; recipes that use a vanilla one take the Ark block (StationEvents).
REPLACED = {'minecraft:crafting_table': f'{NS}:working_station', 'minecraft:chest': f'{NS}:storage_crate',
            'minecraft:trapped_chest': f'{NS}:storage_crate', 'minecraft:smithing_table': f'{NS}:smithing_table'}
REPLACED_BY = {'minecraft:crafting_table': 'the Working Station', 'minecraft:chest': 'the Storage Crate',
               'minecraft:trapped_chest': 'the Storage Crate', 'minecraft:smithing_table': 'the Ark Smithing Table',
               'minecraft:furnace': 'the Stone Fire and the Primitive Forge', 'minecraft:smoker': 'the Stone Fire',
               'minecraft:blast_furnace': 'the Primitive Forge', 'minecraft:campfire': 'the Stone Fire'}
WOOD = ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'pale_oak', 'bamboo', 'crimson',
        'warped']
COLOURS = ['white', 'light_gray', 'gray', 'black', 'brown', 'red', 'orange', 'yellow', 'lime', 'green', 'cyan',
           'light_blue', 'blue', 'purple', 'magenta', 'pink']
AGES = ['', 'exposed', 'weathered', 'oxidized']
# Materials with no source while the Nether, the End and the disabled structures stay closed.
NETHER = {'quartz', 'nether_brick', 'nether_bricks', 'netherrack', 'blackstone', 'polished_blackstone',
          'polished_blackstone_bricks', 'basalt', 'polished_basalt', 'smooth_basalt', 'glowstone_dust', 'glowstone',
          'nether_wart', 'nether_wart_block', 'warped_wart_block', 'crimson_stem', 'warped_stem', 'crimson_hyphae',
          'warped_hyphae', 'crimson_planks', 'warped_planks', 'crimson_fungus', 'warped_fungus', 'shroomlight',
          'quartz_block', 'quartz_slab', 'chiseled_quartz_block', 'quartz_pillar', 'quartz_bricks', 'smooth_quartz',
          'red_nether_bricks', 'crying_obsidian', 'stripped_crimson_stem', 'stripped_warped_stem',
          'twisting_vines', 'weeping_vines', 'crimson_slab', 'warped_slab'}
END = {'end_stone', 'end_stone_bricks', 'purpur_block', 'purpur_pillar', 'purpur_slab', 'chorus_flower'}
STRUCTURES = {'prismarine_shard': 'ocean monuments are disabled and guardians removed',
              'prismarine_crystals': 'ocean monuments are disabled and guardians removed',
              'prismarine': 'ocean monuments are disabled and guardians removed',
              'prismarine_bricks': 'ocean monuments are disabled and guardians removed',
              'dark_prismarine': 'ocean monuments are disabled and guardians removed',
              'disc_fragment_5': 'ancient cities are disabled', 'dragon_head': 'the End is closed'}
# Drops of removed creatures: recipes that need them go.
GONE_SOURCES = {'wither_rose': 'the Wither is removed', 'creeper_head': 'creepers are removed',
                'skeleton_skull': 'skeletons are removed', 'zombie_head': 'zombies are removed',
                'piglin_head': 'piglins are removed', 'echo_shard': 'the Warden is removed'}
# Where each trim template is found; the ones from closed places have no source.
TEMPLATES = {'coast': 'shipwrecks', 'dune': 'desert pyramids', 'wild': 'jungle temples', 'wayfinder': 'trail ruins',
             'raiser': 'trail ruins', 'shaper': 'trail ruins', 'host': 'trail ruins',
             'sentry': None, 'vex': None, 'tide': None, 'snout': None, 'rib': None, 'eye': None, 'spire': None,
             'ward': None, 'silence': None, 'flow': None, 'bolt': None}
CLOSED_TEMPLATE = {'sentry': 'pillager outposts', 'vex': 'woodland mansions', 'tide': 'ocean monuments',
                   'snout': 'bastions (Nether)', 'rib': 'nether fortresses', 'eye': 'strongholds', 'spire': 'end cities',
                   'ward': 'ancient cities', 'silence': 'ancient cities', 'flow': 'trial chambers', 'bolt': 'trial chambers'}
# Family titles where the family id does not read well.
TITLES = {'planks': 'Planks', 'bark': 'Wood and Hyphae', 'sticks': 'Twigs', 'wooden_stairs': 'Wooden Stairs',
          'wooden_slab': 'Wooden Slabs', 'wooden_fence': 'Fences', 'wooden_fence_gate': 'Fence Gates',
          'wooden_door': 'Doors', 'wooden_trapdoor': 'Trapdoors', 'wooden_button': 'Wooden Buttons',
          'wooden_pressure_plate': 'Wooden Pressure Plates', 'wooden_sign': 'Signs', 'hanging_sign': 'Hanging Signs',
          'shelf': 'Shelves', 'boat': 'Boats', 'chest_boat': 'Chest Boats', 'stone_stairs': 'Stairs',
          'stone_slabs': 'Slabs', 'stone_walls': 'Walls', 'stone_bricks': 'Bricks and Tiles',
          'polished_stone': 'Polished Stone', 'chiseled_stone': 'Chiseled Stone', 'rough_stone': 'Rough Stone',
          'sandstone': 'Sandstone', 'stained_glass': 'Stained Glass', 'stained_glass_pane': 'Stained Glass Panes',
          'stained_terracotta': 'Terracotta', 'concrete_powder': 'Concrete Powder', 'wool': 'Wool',
          'dye_wool': 'Dye Wool', 'carpet': 'Carpets', 'dye_carpet': 'Dye Carpets', 'bed': 'Beds', 'dye_bed': 'Dye Beds',
          'banner': 'Banners', 'banner_pattern': 'Banner Patterns', 'banner_copy': 'Copy a Banner',
          'bundle_dye': 'Dye Bundles', 'dyed_candle': 'Dyed Candles', 'lead': 'Leads',
          'storage_blocks': 'Pack into Blocks', 'unpack_blocks': 'Break Blocks Down', 'nuggets': 'Nuggets',
          'ingot_from_nuggets': 'Ingots from Nuggets', 'cut_copper': 'Cut Copper', 'copper_stairs': 'Copper Stairs',
          'copper_slabs': 'Copper Slabs', 'chiseled_copper': 'Chiseled Copper', 'copper_grate': 'Copper Grates',
          'copper_bulb': 'Copper Bulbs', 'copper_door': 'Copper Doors', 'copper_trapdoor': 'Copper Trapdoors',
          'copper_bars': 'Copper Bars', 'copper_chain': 'Copper Chains', 'copper_lantern': 'Copper Lanterns',
          'copper_chest': 'Copper Chests', 'lightning_rod': 'Lightning Rods', 'wax_copper': 'Wax Copper',
          'copper_block': 'Copper Blocks', 'trough': 'Troughs', 'feeding_trough': 'Troughs', 'armour_trim': 'Armour Trim',
          'trim_template': 'Copy a Trim Template', 'suspicious_stew': 'Suspicious Stew', 'rabbit_stew': 'Rabbit Stew',
          'sugar': 'Sugar', 'bonemeal': 'Bone Meal', 'resin_clump': 'Resin Clump', 'mossy_stone_bricks': 'Mossy Stone Bricks'}


def short(item):
    return item.split(':', 1)[1] if item and ':' in item else (item or '')


def title_of(family):
    return TITLES.get(family) or family.replace('_', ' ').title()


# --------------------------------------------------------------------------------------------- loading

class Source:
    """The vanilla jar and the mod's generated data: recipes, item tags and the theme policy lists."""

    def __init__(self):
        self.jar = zipfile.ZipFile(JAR) if JAR.is_file() else None
        self.tags = {}
        if self.jar:
            for name in self.jar.namelist():
                if name.startswith('data/minecraft/tags/item/') and name.endswith('.json'):
                    self.tags['#minecraft:' + name[len('data/minecraft/tags/item/'):-5]] = json.loads(self.jar.read(name))['values']
        for file in (ARK_DATA / 'tags/item').rglob('*.json'):
            self.tags[f'#{NS}:' + file.relative_to(ARK_DATA / 'tags/item').as_posix()[:-5]] = json.loads(file.read_text())['values']
        policy = POLICY.read_text(encoding='utf-8')
        block = policy[policy.index('REMOVED_ITEMS = List.of('):policy.index('DISABLED_BLOCKS')]
        self.removed = {'minecraft:' + x for x in re.findall(r'"([a-z_]+)"', block)}
        entities = policy[policy.index('REMOVED_ENTITIES = List.of('):policy.index('REMOVED_ITEMS')]
        self.removed_entities = set(re.findall(r'"([a-z_]+)"', entities))
        primitive = PRIMITIVE.read_text(encoding='utf-8')
        self.retired = set(re.findall(r'"(minecraft:[a-z_]+)"', primitive[primitive.index('WOODEN_TOOLS'):primitive.index('REPLACED =')]))

    def members(self, tag, seen=None):
        seen = seen or set()
        out = []
        for value in self.tags.get(tag, []):
            value = value if isinstance(value, str) else value.get('id', '')
            if value.startswith('#'):
                if value not in seen:
                    seen.add(value)
                    out += self.members(value, seen)
            else:
                out.append(value)
        return out

    def as_tag(self, items):
        """A list of alternatives as the smallest vanilla tag that holds them all (else the first item)."""
        best = None
        for tag in self.tags:
            members = set(self.members(tag))
            if set(items) <= members and (best is None or len(members) < best[1]):
                best = (tag, len(members))
        return best[0] if best else items[0]

    def ingredient(self, value):
        if isinstance(value, str):
            return REPLACED.get(value, value)
        if isinstance(value, list):
            return self.as_tag([self.ingredient(v) for v in value]) if len(value) > 1 else self.ingredient(value[0])
        if isinstance(value, dict):
            if value.get('neoforge:ingredient_type') == 'neoforge:components':
                return 'minecraft:water_bottle'  # a potion of water: a display id, not a registered item
            if 'item' in value:
                return self.ingredient(value['item'])
            if 'tag' in value:
                return '#' + value['tag']
            if 'items' in value:
                return self.ingredient(value['items'])
        return str(value)

    def recipe(self, rid, data, source):
        kind = data['type'].split(':')[1]
        cost = Counter()
        if 'pattern' in data and 'key' in data:
            for row in data['pattern']:
                for ch in row:
                    if ch != ' ':
                        cost[self.ingredient(data['key'][ch])] += 1
            if data.get('binding'):
                cost[self.ingredient(data['binding'])] += 1
        elif 'ingredients' in data:
            for value in data['ingredients']:
                cost[self.ingredient(value)] += 1
        elif kind == 'crafting_transmute':
            cost[self.ingredient(data['input'])] += 1
            cost[self.ingredient(data['material'])] += 1
        elif kind == 'crafting_dye':
            cost[self.ingredient(data['target'])] += 1
            cost[self.ingredient(data['dye'])] += 1
        elif kind == 'crafting_imbue':
            cost[self.ingredient(data['source'])] += 1
            cost[self.ingredient(data['material'])] += 8
        elif kind == 'crafting_decorated_pot':
            cost['#minecraft:decorated_pot_ingredients'] += 4
        elif kind.startswith('smithing'):
            for key in ('template', 'base', 'addition'):
                if data.get(key):
                    cost[self.ingredient(data[key])] += 1
        elif 'ingredient' in data:
            cost[self.ingredient(data['ingredient'])] += 1
        result = data.get('result')
        item, count = (result.get('id'), result.get('count', 1)) if isinstance(result, dict) else (result, 1)
        if kind == 'smithing_trim':
            item, count = data['template'], 1
        path = short(rid)
        return {'id': rid, 'source': source, 'type': kind,
                'special': 'special' in kind or kind in ('crafting_dye', 'crafting_decorated_pot', 'crafting_imbue'),
                'result': item, 'count': count, 'cost': dict(cost),
                'family': data.get('group') or short(item) or path, 'book': data.get('category', '')}

    def recipes(self):
        found = []
        if self.jar:
            for name in sorted(self.jar.namelist()):
                if name.startswith('data/minecraft/recipe/') and name.endswith('.json'):
                    rid = 'minecraft:' + name[len('data/minecraft/recipe/'):-5]
                    found.append(self.recipe(rid, json.loads(self.jar.read(name)), 'vanilla'))
        for file in sorted((ARK_DATA / 'recipe').rglob('*.json')):
            rid = f'{NS}:' + file.relative_to(ARK_DATA / 'recipe').as_posix()[:-5]
            found.append(self.recipe(rid, json.loads(file.read_text(encoding='utf-8')), 'ark'))
        return found


# --------------------------------------------------------------------------------------------- the rules

def decision(fate, bench='', place='', family=None, note='', level=None):
    return {'fate': fate, 'bench': bench, 'place': place, 'family': family, 'note': note, 'level': level}


def closed_material(recipe):
    """Why a recipe cannot be made while the Nether, the End and some structures are closed, or ''."""
    for item in [recipe['result']] + list(recipe['cost']):
        path = short(item)
        if path in NETHER or path.startswith(('crimson_', 'warped_', 'stripped_crimson', 'stripped_warped')):
            return f'needs {path.replace("_", " ")}: the Nether is closed'
        if path in END:
            return f'needs {path.replace("_", " ")}: the End is closed'
        if path in STRUCTURES:
            return f'needs {path.replace("_", " ")}: {STRUCTURES[path]}'
    return ''


def ws(place, family=None, note='', level=None):
    return decision('stay', 'working_station', place, family, note, level)


def propose(recipe, source):
    """The rule layer: a proposal for one recipe."""
    kind, item, fam, rid = recipe['type'], recipe['result'] or '', recipe['family'], recipe['id']
    path, cost = short(item), recipe['cost']
    removed = source.removed

    # Machines outside the rework keep their recipe types.
    if kind == 'smelting':
        return decision('stay', 'primitive_forge', note='the Primitive Forge smelts it')
    if kind == 'campfire_cooking':
        return decision('stay', 'stone_fire', note='the Stone Fire cooks it')
    if kind == 'blasting':
        return decision('go', note='no blast furnace: the Primitive Forge smelts these')
    if kind == 'smoking':
        return decision('go', note='no smoker: the Stone Fire cooks these')
    if kind == 'stonecutting':
        why = closed_material(recipe)
        return decision('decide' if why else 'stay', 'stonecutter', note=why or 'the Stonecutter keeps it')
    if kind == 'smithing_transform':
        return decision('go', note='netherite is removed')
    if kind == 'smithing_trim':
        pattern = short(item).replace('_armor_trim_smithing_template', '')
        where = TEMPLATES.get(pattern)
        return decision('stay' if where else 'decide', 'smithing_table', 'trims', 'armour_trim',
                        f'template from {where}' if where else f'template only in {CLOSED_TEMPLATE.get(pattern, "a closed place")}')

    # Removed by the theme, replaced by an Ark block, or made from something removed.
    if item in removed:
        return decision('go', note='removed by the theme')
    if item in REPLACED_BY and source_is_vanilla(recipe):
        return decision('go', note=f'replaced by {REPLACED_BY[item]}')
    if item in source.retired:
        return decision('go', note='the rock set replaces wooden and stone tools')
    for ingredient in cost:
        if ingredient in removed:
            return decision('go', note=f'needs {short(ingredient).replace("_", " ")}, removed by the theme')
    if fam in ('harness', 'harness_dye') or path == 'dried_ghast':
        return decision('go', note='the happy ghast is removed')
    if 'copper_golem_statue' in path:
        return decision('go', note='the copper golem is removed')
    if path in ('creaking_heart', 'calibrated_sculk_sensor') or 'sculk' in path:
        return decision('go', note='the creaking and sculk are removed')
    if rid in ('minecraft:map_extending',) or 'firework' in rid:
        return decision('go', note='map editing and fireworks are removed')
    if path in ('furnace_minecart',):
        return decision('go', note='needs a furnace, which the Stone Fire replaced')
    if path == 'netherite_upgrade_smithing_template' or 'netherite' in path:
        return decision('go', note='netherite is removed')
    if 'shulker_box' in path:
        return decision('go', note='shulkers are removed')
    if path.endswith('_armor_trim_smithing_template'):
        pattern = path.replace('_armor_trim_smithing_template', '')
        where = TEMPLATES.get(pattern)
        return decision('stay' if where else 'decide', 'smithing_table', 'templates', path,
                        f'template from {where}' if where else f'template only in {CLOSED_TEMPLATE.get(pattern, "a closed place")}')
    for ingredient in [item] + list(cost):
        if short(ingredient) in GONE_SOURCES:
            return decision('go', note=f'needs {short(ingredient).replace("_", " ")}: {GONE_SOURCES[short(ingredient)]}')
    why = closed_material(recipe)
    if why:
        placed = place(recipe) or decision('decide', 'working_station', '')
        return {**placed, 'fate': 'decide', 'note': why}

    # Equipment the answers of 2026-09-29 do not cover, and the special recipes.
    if re.fullmatch(r'(copper|iron)_(helmet|chestplate|leggings|boots)', path):
        return decision('go', note='assumed gone with the copper and iron tools')
    if rid == 'minecraft:repair_item':
        return decision('decide', 'armoury', note='combine two worn tools: keep, or repair at the Smithing Table?')
    if kind == 'crafting_dye':
        return decision('decide', 'armoury', note='dye leather gear: keep as an Armoury action?')
    if rid == 'minecraft:map_cloning':
        return decision('decide', 'working_station', 'fibre/paper', note='copy a filled map (Xaero replaces map editing)')
    if rid == 'minecraft:shield_decoration':
        return decision('stay', 'armoury', 'wood', note='puts a banner on a shield')
    if rid == 'minecraft:book_cloning':
        return decision('stay', 'working_station', 'fibre/paper', note='copies a written book')
    placed = place(recipe)
    if placed:
        return placed
    return decision('decide', '', '', note='no rule places it yet')


def source_is_vanilla(recipe):
    return recipe['source'] == 'vanilla'


def place(recipe):
    """Where a kept recipe sits: bench, material / group and family."""
    kind, item, fam, rid = recipe['type'], recipe['result'] or '', recipe['family'], recipe['id']
    path = short(item)
    ark = recipe['source'] == 'ark'

    if ark:
        if short(rid).startswith('accessory/'):
            accessory = short(rid)[10:]
            return ws('accessories/' + ACCESSORY_GROUPS.get(accessories().get(accessory, ''), 'charms'), accessory,
                      level=10 if accessory in relics() else None)
        if path.endswith('trough'):
            return ws('camp/rest', 'trough')
        if path in ('bedroll', 'mattress', 'storage_crate'):
            return ws('camp/rest')
        if path in ('working_station', 'drying_rack', 'cooking_pot', 'stone_fire', 'primitive_forge', 'mortar_and_pestle',
                    'medicine_bench', 'smithing_table', 'crusher'):
            return ws('camp/stations')
        if path in ('pack_harness', 'reinforced_harness'):
            return ws('camp/tame')
        if path == 'field_journal':
            return ws('fibre/paper')
        if path == 'lead':
            return ws('fibre/leather', 'lead')
        if path == 'resin_clump':
            return ws('wood/resin', 'resin_clump')
        if path in ('bronze_blend', 'sulphur_block'):
            return ws('metal/blocks')
        if path == 'sharp_rock':
            return ws('stone/machines')
        return None

    # Dyes, bone meal and sugar are ground at the Mortar & Pestle.
    if path.endswith('_dye') and not fam.endswith('_bundle'):
        colour = path[:-4]
        group = 'flowers/warm' if colour in ('red', 'orange', 'yellow', 'pink', 'magenta', 'brown') else \
                'flowers/cool' if colour in ('blue', 'light_blue', 'cyan', 'purple', 'lime', 'green') else 'flowers/neutral'
        return decision('stay', 'mortar_and_pestle', group, fam)
    if fam == 'bonemeal':
        return decision('stay', 'mortar_and_pestle', 'bone', 'bonemeal')
    if fam == 'sugar':
        return decision('stay', 'mortar_and_pestle', 'minerals', 'sugar')
    if fam == 'suspicious_stew':
        return decision('stay', 'medicine_bench', 'tonics', 'suspicious_stew',
                        'a tonic here: the flower picks the effect')

    # Wood.
    if fam in ('planks', 'bark', 'sticks') or path in ('bamboo_block', 'bamboo_mosaic', 'torch'):
        return ws('wood/planks', fam if fam in ('planks', 'bark', 'sticks') else None)
    if path in ('bamboo_mosaic_stairs', 'bamboo_mosaic_slab'):
        return ws('wood/building', 'wooden_stairs' if path.endswith('stairs') else 'wooden_slab')
    if fam in ('wooden_stairs', 'wooden_slab', 'wooden_fence', 'wooden_fence_gate', 'wooden_door', 'wooden_trapdoor') \
            or path in ('ladder', 'scaffolding'):
        return ws('wood/building', fam if fam.startswith('wooden_') else None)
    if fam in ('wooden_button', 'wooden_pressure_plate'):
        return ws('wood/switches', fam)
    if fam in ('wooden_sign', 'hanging_sign', 'shelf') or path in ('item_frame', 'glow_item_frame', 'painting', 'armor_stand'):
        return ws('wood/signs', fam if fam in ('wooden_sign', 'hanging_sign', 'shelf') else None)
    if fam in ('boat', 'chest_boat'):
        return ws('wood/boats', fam)
    if path in ('barrel', 'bookshelf', 'chiseled_bookshelf', 'lectern', 'composter', 'note_block', 'jukebox',
                'fletching_table', 'beehive', 'bowl'):
        return ws('wood/furniture')
    if path.startswith('resin') or 'resin_brick' in path:
        if not path.endswith(('_stairs', '_slab', '_wall')):
            return ws('wood/resin', 'resin_clump' if path == 'resin_clump' else None)

    # Metals first, so copper stairs and slabs stay with copper.
    if 'copper' in path or 'lightning_rod' in path or path == 'spyglass':
        if rid.endswith('_from_honeycomb'):
            return ws('metal/copper', 'wax_copper')
        base = path.replace('waxed_', '')
        for age in ('exposed_', 'weathered_', 'oxidized_'):
            base = base.replace(age, '')
        families = {'cut_copper': 'cut_copper', 'cut_copper_stairs': 'copper_stairs', 'cut_copper_slab': 'copper_slabs',
                    'chiseled_copper': 'chiseled_copper', 'copper_grate': 'copper_grate', 'copper_bulb': 'copper_bulb',
                    'copper_door': 'copper_door', 'copper_trapdoor': 'copper_trapdoor', 'copper_bars': 'copper_bars',
                    'copper_chain': 'copper_chain', 'copper_lantern': 'copper_lantern', 'copper_chest': 'copper_chest',
                    'lightning_rod': 'lightning_rod', 'copper_block': 'copper_block'}
        if path in ('copper_ingot', 'copper_nugget', 'raw_copper', 'raw_copper_block', 'copper_block'):
            return ws('metal/blocks', metal_family(recipe))
        goods = base in ('copper_door', 'copper_trapdoor', 'copper_bars', 'copper_chain', 'copper_lantern', 'copper_chest',
                         'copper_torch', 'lightning_rod', 'spyglass')
        return ws('metal/copper_goods' if goods else 'metal/copper', families.get(base))
    if path in ('iron_ingot', 'iron_nugget', 'gold_ingot', 'gold_nugget', 'raw_iron', 'raw_gold', 'coal', 'diamond',
                'emerald', 'lapis_lazuli') or path.endswith('_block') and path.replace('_block', '') in (
                'iron', 'gold', 'diamond', 'emerald', 'lapis', 'coal', 'raw_iron', 'raw_gold', 'amethyst', 'redstone'):
        if path in ('redstone_block',):
            return ws('redstone/power')
        return ws('metal/blocks', metal_family(recipe))
    if path in ('redstone',):
        return ws('redstone/power')
    if path in ('iron_bars', 'iron_chain', 'iron_door', 'iron_trapdoor', 'bucket', 'cauldron', 'hopper', 'anvil',
                'heavy_weighted_pressure_plate', 'lantern', 'compass'):
        return ws('metal/iron')
    if path in ('clock', 'light_weighted_pressure_plate', 'golden_dandelion'):
        return ws('metal/gold')
    if path.endswith('rail') or path.endswith('minecart'):
        return ws('metal/rails')

    # Stone and every mineral stairs, slab and wall.
    if path.endswith('_stairs'):
        return ws('stone/stairs', 'stone_stairs')
    if path.endswith('_slab'):
        return ws('stone/stairs', 'stone_slabs')
    if path.endswith('_wall'):
        return ws('stone/walls', 'stone_walls')
    if path.startswith('chiseled_') and 'sandstone' not in path and 'bookshelf' not in path:
        return ws('stone/bricks', 'chiseled_stone')
    if path.startswith('polished_'):
        return ws('stone/bricks', 'polished_stone')
    if path in ('stone_bricks', 'mossy_stone_bricks', 'deepslate_bricks', 'deepslate_tiles', 'tuff_bricks',
                'end_stone_bricks', 'nether_bricks', 'red_nether_bricks', 'quartz_bricks', 'prismarine_bricks'):
        return ws('stone/bricks', 'stone_bricks')
    if path in ('andesite', 'diorite', 'granite', 'mossy_cobblestone', 'dripstone_block', 'purpur_block', 'purpur_pillar',
                'quartz_block', 'quartz_pillar', 'prismarine', 'dark_prismarine', 'sea_lantern', 'glowstone'):
        return ws('stone/bricks', 'rough_stone')
    if path in ('stonecutter', 'grindstone', 'lever', 'stone_button', 'stone_pressure_plate'):
        return ws('stone/machines')

    # Clay, mud, sand, glass, snow and ice.
    if path in ('clay', 'bricks', 'flower_pot', 'decorated_pot', 'mud_bricks', 'packed_mud', 'muddy_mangrove_roots',
                'coarse_dirt') or fam == 'stained_terracotta':
        return ws('earth/clay', fam if fam == 'stained_terracotta' else None)
    if 'sandstone' in path:
        return ws('earth/sand', 'sandstone')
    if fam in ('stained_glass', 'stained_glass_pane', 'concrete_powder') or path in ('glass_pane', 'tinted_glass', 'glass_bottle'):
        return ws('earth/sand', fam if fam in ('stained_glass', 'stained_glass_pane', 'concrete_powder') else None)
    if path in ('snow_block', 'snow', 'packed_ice', 'blue_ice'):
        return ws('earth/snow')

    # Wool, beds, banners, leather, paper.
    if path == 'white_wool' or fam == 'wool':
        return ws('fibre/wool', 'dye_wool' if fam == 'wool' else 'wool')
    if fam in ('carpet', 'carpet_dye'):
        return ws('fibre/wool', 'carpet' if fam == 'carpet' else 'dye_carpet')
    if fam in ('bed', 'bed_dye'):
        return ws('fibre/banners', 'bed' if fam == 'bed' else 'dye_bed')
    if fam == 'banner' or rid.endswith('_banner_duplicate'):
        return ws('fibre/banners', 'banner' if fam == 'banner' else 'banner_copy')
    if path.endswith('_banner_pattern'):
        return ws('fibre/banners', 'banner_pattern')
    if path in ('leather', 'bundle') or fam in ('bundle_dye', 'lead'):
        return ws('fibre/leather', fam if fam in ('bundle_dye', 'lead') else None)
    if path in ('paper', 'book', 'writable_book', 'written_book', 'map', 'name_tag'):
        return ws('fibre/paper')

    # Redstone.
    if path in ('redstone_torch', 'repeater', 'comparator', 'observer', 'daylight_detector', 'target', 'redstone_lamp',
                'tripwire_hook'):
        return ws('redstone/power')
    if path in ('piston', 'sticky_piston', 'dispenser', 'dropper', 'crafter', 'tnt'):
        return ws('redstone/mechanisms')

    # Farm: food, crops, honey, wax and slime.
    if path in ('bread', 'cake', 'cookie', 'pumpkin_pie', 'mushroom_stew', 'beetroot_soup', 'golden_carrot',
                'dried_kelp') or fam == 'rabbit_stew':
        return ws('farm/food', fam if fam == 'rabbit_stew' else None)
    if path in ('hay_block', 'wheat', 'melon', 'melon_seeds', 'pumpkin_seeds', 'jack_o_lantern', 'dried_kelp_block',
                'bone_block'):
        return ws('farm/crops')
    if path in ('honey_bottle', 'honeycomb_block', 'honey_block', 'slime_block', 'slime_ball', 'candle') or fam == 'dyed_candle':
        return ws('farm/honey', fam if fam == 'dyed_candle' else None)
    if path in ('carrot_on_a_stick', 'warped_fungus_on_a_stick'):
        return decision('stay', 'armoury', 'wood')
    return None


ACCESSORY_GROUPS = {'head': 'head', 'necklace': 'head', 'back': 'body', 'body': 'body', 'hands': 'hands', 'ring': 'hands',
                    'bracelet': 'hands', 'belt': 'legs', 'legs': 'legs', 'feet': 'legs', 'charm': 'charms'}


def accessories():
    """Accessory id -> Curios slot (design/showcase/accessories.json, exported by runData)."""
    if not hasattr(accessories, 'slots'):
        data = load(ARK / 'design/showcase/accessories.json')
        accessories.slots = {a['id']: a['slot'] for a in data['accessories']}
        accessories.relics = {a['id'] for a in data['accessories'] if a.get('group') == 'relic'}
    return accessories.slots


def relics():
    accessories()
    return accessories.relics


def metal_family(recipe):
    """Ingots, nuggets and storage blocks: packing into blocks, breaking them down, nuggets and back."""
    item, rid = recipe['result'], recipe['id']
    path = short(item)
    if path.endswith('_nugget'):
        return 'nuggets'
    if 'from_nuggets' in rid:
        return 'ingot_from_nuggets'
    if path.endswith('_block'):
        return 'storage_blocks'
    return 'unpack_blocks'


# --------------------------------------------------------------------------------------------- the plan

def load(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def benches():
    return {bench: load(DESIGN / f'{bench}.json') for bench in BENCHES}


def design_entries(bench_data):
    """Every item a bench design writes out: (place, entry), place = 'category' or 'category/group'."""
    for category in bench_data['categories']:
        for entry in category.get('items', []):
            if 'group' in entry:
                for sub in entry.get('items', []):
                    yield f"{category['id']}/{entry['group']}", sub
            else:
                yield category['id'], entry


def plan(source=None):
    """Every recipe with its fate, bench, place and family, the layers applied in order."""
    source = source or Source()
    designs = benches()
    decisions = load(DESIGN / 'vanilla_fates.json').get('decisions', {}) if (DESIGN / 'vanilla_fates.json').is_file() else {}
    designed = {}  # result item -> (bench, place, entry)
    for bench, data in designs.items():
        for where, entry in design_entries(data):
            designed.setdefault(entry['item'], (bench, where, entry))
    records = []
    for recipe in source.recipes():
        proposal = propose(recipe, source)
        rule_bench = proposal.get('bench')
        by = 'rule'
        crafting = recipe['type'].startswith('crafting') or recipe['type'] == 'bound_shaped'
        hit = designed.get(recipe['result']) if crafting and not recipe['special'] else None
        if hit:
            bench, where, entry = hit
            same = entry.get('cost') == recipe['cost'] and entry.get('count', 1) == recipe['count']
            proposal = decision('stay' if same else 'change', bench, where, None,
                                'same cost as the grid' if same else 'new cost in the bench design')
            if not same:
                proposal['newCost'], proposal['newCount'] = entry['cost'], entry.get('count', 1)
            by = 'design'
        family = proposal.get('family') or recipe['family']
        for key in dict.fromkeys((f"family:{recipe['family']}", f'family:{family}', recipe['id'])):
            if key in decisions:
                user = decisions[key]
                proposal = {**proposal, **{k: v for k, v in user.items() if k in ('fate', 'bench', 'place', 'note')}}
                if 'bench' in user and 'place' not in user and user['bench'] != (hit[0] if hit else rule_bench):
                    # A recipe moved to another bench without a place: its rule place when the rules agree, else the
                    # bench's first material, where the user can see it and move it on.
                    ruled = place(recipe) or {}
                    first = designs[user['bench']]['categories'][0]['id'] if user['bench'] in designs else ''
                    proposal['place'] = ruled.get('place') if ruled.get('bench') == user['bench'] else first
                by = 'user'
        records.append({**recipe, **proposal, 'family': proposal.get('family') or recipe['family'], 'by': by})
    # Ark recipes an Ark design replaces are designed too (the Ark arrow replaces the vanilla one).
    return records, designs, decisions


def variant_key(item):
    """Sort key inside a family: wood types, colours and copper ages in the game's order."""
    path = short(item)
    for i, wood in enumerate(WOOD):
        if path.startswith(wood + '_') or path.startswith('stripped_' + wood):
            return (0, i, path)
    for i, colour in enumerate(COLOURS):
        if path.startswith(colour + '_'):
            return (1, i, path)
    for i, age in enumerate(AGES[1:], 1):
        if age in path:
            return (2, i + (4 if 'waxed' in path else 0), path)
    return (3, 0 if 'waxed' not in path else 1, path)


def compile_benches(records, designs):
    """One explicit tree per bench: categories, groups and families of variants, only the kept recipes."""
    kept = [r for r in records if r['fate'] in ('stay', 'change') and r['bench'] in BENCHES and not r['special']]
    out = {}
    for bench, data in designs.items():
        tree = {k: v for k, v in data.items() if k != 'categories'}
        tree['categories'] = []
        for category in data['categories']:
            cat = {k: v for k, v in category.items() if k != 'items'}
            cat['items'] = []
            for entry in category.get('items', []):
                if 'group' in entry:
                    group = {k: v for k, v in entry.items() if k != 'items'}
                    group['items'] = entries(bench, f"{category['id']}/{entry['group']}", entry.get('items', []), kept, group)
                    if group['items']:
                        cat['items'].append(group)
            cat['items'] = entries(bench, category['id'], [e for e in category.get('items', []) if 'group' not in e], kept, cat) \
                + [e for e in cat['items'] if 'group' in e]
            tree['categories'].append(cat)
        out[bench] = tree
    return out


def entries(bench, where, designed, kept, parent):
    """The families at one place: the designed items in their order, then the ruled recipes by family."""
    level = parent.get('level', 0)
    result, taken = [], set()
    for entry in designed:
        variant = {'item': entry['item'], 'count': entry.get('count', 1), 'cost': entry['cost']}
        result.append({'family': short(entry['item']), 'level': entry.get('level', level), 'variants': [variant],
                       **({'title': entry['title']} if entry.get('title') else {}),
                       **({'planned': True} if entry.get('planned') else {})})
        taken.add(entry['item'])
    families = {}
    for r in kept:
        if r['bench'] != bench or r['place'] != where or r['by'] == 'design' or r['result'] in taken:
            continue
        families.setdefault(r['family'], []).append(r)
    for family, members in families.items():
        members.sort(key=lambda r: variant_key(r['result']))
        seen, variants = set(), []
        for r in members:
            if (r['result'], json.dumps(r['cost'], sort_keys=True)) in seen:
                continue
            seen.add((r['result'], json.dumps(r['cost'], sort_keys=True)))
            variant = {'item': r['result'], 'count': r['count'], 'cost': r['cost'], 'recipe': r['id']}
            if r['type'] == 'smithing_trim':
                variant['apply'] = True  # the trim goes onto the armour piece; nothing new comes out
            variants.append(variant)
        result.append({'family': family, 'title': title_of(family) if len(variants) > 1 else None,
                       'level': max([level] + [r['level'] for r in members if r.get('level')]), 'variants': variants})
    return result


def summary(records):
    counts = Counter((r['source'], r['fate']) for r in records)
    return {source: {fate: counts[(source, fate)] for fate in FATES} for source in ('vanilla', 'ark')}


if __name__ == '__main__':
    records, designs, decisions = plan()
    print(json.dumps(summary(records)))
    trees = compile_benches(records, designs)
    for bench, tree in trees.items():
        families = sum(len(g['items']) if 'group' in g else 1 for c in tree['categories'] for g in c['items'])
        print(bench, len(tree['categories']), 'materials', families, 'families')
