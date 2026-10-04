"""Crafting rework plan (P14): where every recipe goes once the 3x3 grid is gone.

Reads every recipe of the vanilla jar and of the mod's generated data and gives each one a fate (go, stay, change or
decide), a bench, and a place in that bench's graph: material / group, plus a family that folds variants such as the
twelve wood types into one node. Every recipe type lands on a bench, the furnace and stonecutter ones too, at the
place where the thing is really made (see the rules). Three layers decide, the last one winning:

1. The rules in propose(): proposals from the theme policy, the recipe gates and what each bench is for.
2. The bench design files, design/workstations/<bench>.json: a recipe a bench writes out with its own cost is a
   change (a stay when the cost is still the old grid's). Items they list without a recipe anywhere are new, and so is
   a family a design writes out with its own variants ({"family", "title", "variants": [{"item", "count", "cost"}]}),
   which only adds recipes: the Cutting Machine's sawn planks leave the Working Station's planks where they are.
3. design/workstations/vanilla_fates.json: the user's decisions, which the showcase review list saves. A key is a
   recipe id, or "family:<name>" for every recipe of a family.

compile_benches() turns the plan into one explicit tree per bench: what the showcase draws and, once shipped, what the
mod's workstation data will hold. Run from Ark/: python tools/workstation_plan.py prints the totals and every kept
recipe that no bench shows (a place its design does not have).
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

BENCHES = ['armoury', 'working_station', 'campfire', 'stonecutter', 'mortar_and_pestle', 'primitive_forge',
           'medicine_bench', 'smithing_table', 'mechanical_press', 'milling_machine', 'cutting_machine']
TIMED = ('smelting', 'campfire_cooking')  # recipes that take time over the fire; the variant carries it in seconds
FATES = ('go', 'stay', 'change', 'decide')
# The Ark blocks that replace vanilla ones; recipes that use a vanilla one take the Ark block (StationEvents).
REPLACED = {'minecraft:crafting_table': f'{NS}:working_station', 'minecraft:chest': f'{NS}:storage_crate',
            'minecraft:trapped_chest': f'{NS}:storage_crate', 'minecraft:smithing_table': f'{NS}:smithing_table'}
REPLACED_BY = {'minecraft:crafting_table': 'the Working Station', 'minecraft:chest': 'the Storage Crate',
               'minecraft:trapped_chest': 'the Storage Crate', 'minecraft:smithing_table': 'the Ark Smithing Table',
               'minecraft:furnace': 'the Campfire (Stone Fire) and the Primitive Forge',
               'minecraft:smoker': 'the Campfire (Stone Fire)', 'minecraft:blast_furnace': 'the Primitive Forge',
               'minecraft:campfire': 'the Campfire (Stone Fire)'}
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
          'twisting_vines', 'weeping_vines', 'crimson_slab', 'warped_slab', 'nether_gold_ore', 'nether_quartz_ore',
          'ancient_debris'}
END = {'end_stone', 'end_stone_bricks', 'purpur_block', 'purpur_pillar', 'purpur_slab', 'chorus_flower', 'chorus_fruit',
       'popped_chorus_fruit'}
STRUCTURES = {'prismarine_shard': 'ocean monuments are disabled and guardians removed',
              'prismarine_crystals': 'ocean monuments are disabled and guardians removed',
              'prismarine': 'ocean monuments are disabled and guardians removed',
              'prismarine_bricks': 'ocean monuments are disabled and guardians removed',
              'dark_prismarine': 'ocean monuments are disabled and guardians removed',
              'wet_sponge': 'ocean monuments are disabled and guardians removed',
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
          'shelf': 'Shelves', 'boat': 'Boats', 'chest_boat': 'Chest Boats', 'torches': 'Torches',
          'stained_glass': 'Stained Glass', 'stained_glass_pane': 'Stained Glass Panes',
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
          'wax_copper_goods': 'Wax Copper Goods', 'glazed_terracotta': 'Glazed Terracotta',
          'cracked_bricks': 'Cracked Bricks', 'smooth_sandstone': 'Smooth Sandstone', 'recycle_gold': 'Melt Gold Gear',
          'bales': 'Pack into Bales', 'bale_unpack': 'Break Bales Down', 'honey_blocks': 'Pack Honey, Wax and Slime',
          'honey_unpack': 'Press Honey and Slime', 'packed_ice': 'Packed Ice',
          'copper_block': 'Copper Blocks', 'trough': 'Troughs', 'feeding_trough': 'Troughs', 'armour_trim': 'Armour Trim',
          'trim_template': 'Copy a Trim Template', 'suspicious_stew': 'Suspicious Stew', 'rabbit_stew': 'Rabbit Stew',
          'sugar': 'Sugar', 'bonemeal': 'Bone Meal', 'resin_clump': 'Resin Clump', 'mossy_stone_bricks': 'Mossy Stone Bricks'}


def short(item):
    return item.split(':', 1)[1] if item and ':' in item else (item or '')


def title_of(family):
    kind, _, shape = family.rpartition('_')
    if kind in KIND_WORD and shape in ('blocks', 'stairs', 'slabs', 'walls'):
        return f'{KIND_WORD[kind]} {shape.title()}'
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
                'family': data.get('group') or short(item) or path, 'book': data.get('category', ''),
                **({'time': data.get('cookingtime', 200 if kind == 'smelting' else 600) / 20} if kind in TIMED else {})}

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
#
# Every recipe goes where the thing is really made: food over the Campfire, stone shapes at the Stonecutter,
# anything fired or smelted in the Primitive Forge, metal goods at the Smithing Table, paper, books, pressed blocks and
# sheet metal at the Mechanical Press, powders at the Mortar & Pestle, medicine at the Medicine Bench, weapons, tools
# and armour at the Armoury. The Working Station keeps what is assembled by hand: carpentry, textiles, camp gear,
# accessories and redstone.

def decision(fate, bench='', place='', family=None, note='', level=None):
    return {'fate': fate, 'bench': bench, 'place': place, 'family': family, 'note': note, 'level': level}


def at(bench, place, family=None, note='', level=None, fate='stay'):
    return decision(fate, bench, place, family, note, level)


def ws(place, family=None, note='', level=None):
    return at('working_station', place, family, note, level)


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


def propose(recipe, source):
    """The rule layer: a proposal for one recipe."""
    kind, item, fam, rid = recipe['type'], recipe['result'] or '', recipe['family'], recipe['id']
    path, cost = short(item), recipe['cost']
    removed = source.removed

    # Machines that are gone, and the netherite upgrade.
    if kind == 'blasting':
        return decision('go', note='no blast furnace: the Primitive Forge smelts these')
    if kind == 'smoking':
        return decision('go', note='no smoker: the Campfire cooks these')
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
    if 'copper_bulb' in path:
        return decision('go', note='the copper bulb needs a blaze rod: the Nether is closed')
    if kind == 'smelting' and path in ('iron_nugget', 'copper_nugget'):
        return decision('go', note='melts copper and iron tools and armour, which are gone')
    if kind == 'smelting' and item in source.fire_results:
        return decision('go', note='the Campfire cooks it (its campfire recipe)')
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
        placed = place(recipe, source) or decision('decide')
        return {**placed, 'fate': 'decide', 'note': why}

    # Equipment the answers of 2026-09-29 do not cover, and the special recipes.
    if re.fullmatch(r'(copper|iron)_(helmet|chestplate|leggings|boots)', path):
        return decision('go', note='assumed gone with the copper and iron tools')
    if rid == 'minecraft:repair_item':
        return decision('decide', 'armoury', note='combine two worn tools: keep, or repair at the Smithing Table?')
    if kind == 'crafting_dye':
        return decision('decide', 'armoury', note='dye leather gear: keep as an Armoury action?')
    if rid == 'minecraft:map_cloning':
        return decision('decide', 'mechanical_press', 'paper', note='copy a filled map (Xaero replaces map editing)')
    if rid == 'minecraft:shield_decoration':
        return decision('stay', 'armoury', 'wood', note='puts a banner on a shield')
    if rid == 'minecraft:book_cloning':
        return decision('stay', 'mechanical_press', 'paper', note='copies a written book: the press prints it')
    # A shape the Stonecutter cuts one for one replaces the grid recipe for it (waxing is not a shape).
    if kind.startswith('crafting') and item in source.cut_results and not rid.endswith('_from_honeycomb'):
        return decision('go', note='the Stonecutter cuts it: one block, one piece')
    placed = place(recipe, source)
    if placed:
        return placed
    return decision('decide', '', '', note='no rule places it yet')


def source_is_vanilla(recipe):
    return recipe['source'] == 'vanilla'


def place(recipe, source):
    """Where a kept recipe sits: bench, material / group and family."""
    kind = recipe['type']
    if kind == 'smelting':
        return smelted(recipe)
    if kind == 'campfire_cooking':
        return cooked(short(recipe['result']))
    if kind == 'stonecutting':
        return cut(recipe)
    if recipe['source'] == 'ark':
        return ark_place(recipe)
    return crafted(recipe)


# ------------------------------------------------------------------------------ Campfire: food, charcoal

FARM_MEAT = ('cooked_beef', 'cooked_porkchop', 'cooked_mutton', 'cooked_chicken', 'cooked_rabbit')


def cooked(path, family=None):
    """Food over the Campfire, by what it is."""
    if path in FARM_MEAT:
        return at('campfire', 'meat/farm', family)
    if path.startswith('cooked_') and path.endswith('_meat') and path != 'cooked_marine_meat':
        return at('campfire', 'meat/creatures', family)
    if path in ('cooked_cod', 'cooked_salmon', 'cooked_marine_meat', 'dried_kelp'):
        return at('campfire', 'fish', family)
    if path in ('baked_potato', 'golden_carrot', 'popped_chorus_fruit'):
        return at('campfire', 'garden', family)
    if path in ('bread', 'cookie', 'cake', 'pumpkin_pie'):
        return at('campfire', 'baking', family)
    if path in ('mushroom_stew', 'beetroot_soup', 'rabbit_stew'):
        return at('campfire', 'pot', family)
    return None


# ---------------------------------------------------------------------- Primitive Forge: fire and metal

def smelted(recipe):
    """A furnace recipe: the forge fires it, unless it is food, charcoal (the Campfire) or a dye (the Mortar)."""
    path = short(recipe['result'])
    forge = 'primitive_forge'
    if path in ('charcoal', 'leaf_litter'):
        return at('campfire', 'embers', path, 'chars over the fire')
    if path in ('green_dye', 'lime_dye'):
        return at('mortar_and_pestle', 'flowers/cool', path, 'ground at the Mortar & Pestle, not smelted', fate='change')
    if path == 'bronze_ingot':
        return at(forge, 'alloys', 'bronze_ingot')
    if path.endswith('_ingot'):
        return at(forge, 'ores/ingots', path)
    if path in ('coal', 'diamond', 'emerald', 'lapis_lazuli', 'redstone', 'quartz'):
        return at(forge, 'ores/minerals', path)
    if path == 'gold_nugget':
        return at(forge, 'nuggets', 'recycle_gold', 'melts golden tools and armour')
    if path == 'glass':
        return at(forge, 'glass', 'glass')
    if path.endswith('_glazed_terracotta'):
        return at(forge, 'clay/glazed', 'glazed_terracotta')
    if path in ('brick', 'terracotta', 'resin_brick', 'nether_brick'):
        return at(forge, 'clay/fired', path)
    if path.startswith('cracked_'):
        return at(forge, 'stone', 'cracked_bricks')
    if path.startswith('smooth_') and 'sandstone' in path:
        return at(forge, 'stone', 'smooth_sandstone')
    if path in ('stone', 'smooth_stone', 'deepslate', 'smooth_basalt', 'smooth_quartz', 'sponge'):
        return at(forge, 'stone', path)
    return cooked(path)


FORGE_CRAFTS = {'glass_pane': 'glass', 'tinted_glass': 'glass', 'glass_bottle': 'glass', 'flower_pot': 'clay/pottery',
                'decorated_pot': 'clay/pottery'}


# ------------------------------------------------------------------------------- Stonecutter: stone shapes

# Stone kinds, first match wins: 'sandstone' before 'stone', the closed stones before the bricks they name.
KINDS = [('copper', ('copper',)), ('resin', ('resin',)),
         ('closed', ('blackstone', 'nether_brick', 'quartz', 'purpur', 'end_stone', 'prismarine', 'basalt', 'sea_lantern')),
         ('deepslate', ('deepslate',)), ('tuff', ('tuff',)), ('sandstone', ('sandstone',)),
         ('igneous', ('granite', 'diorite', 'andesite')), ('bricks', ('mud_brick',)),
         ('stone', ('stone', 'cobble', 'dripstone'))]
KIND_WORD = {'stone': 'Stone', 'igneous': 'Igneous', 'sandstone': 'Sandstone', 'bricks': 'Brick', 'deepslate': 'Deepslate',
             'tuff': 'Tuff', 'resin': 'Resin Brick', 'closed': 'Closed'}
COPPER_BLOCKS = {'cut_copper': 'cut_copper', 'cut_copper_stairs': 'copper_stairs', 'cut_copper_slab': 'copper_slabs',
                 'chiseled_copper': 'chiseled_copper', 'copper_grate': 'copper_grate'}
COPPER_GOODS = ('copper_bars', 'copper_chain', 'copper_chest', 'copper_door', 'copper_lantern', 'copper_trapdoor',
                'lightning_rod')
# Blocks the grid makes from something else (sand, brick items, packed mud), which the Stonecutter keeps with its cuts.
MASON_BLOCKS = ('sandstone', 'red_sandstone', 'bricks', 'mud_bricks', 'resin_bricks', 'mossy_cobblestone',
                'mossy_stone_bricks', 'andesite', 'diorite', 'granite', 'dripstone_block', 'prismarine', 'dark_prismarine',
                'sea_lantern', 'purpur_block', 'purpur_pillar', 'quartz_block', 'quartz_pillar', 'nether_brick_fence')


def kind_of(path):
    if path == 'bricks' or path.startswith('brick_'):
        return 'bricks'
    for kind, words in KINDS:
        if any(w in path for w in words):
            return kind
    return None


def copper_base(path):
    base = path.replace('waxed_', '')
    for age in ('exposed_', 'weathered_', 'oxidized_'):
        base = base.replace(age, '')
    return base


def cut(recipe):
    """A stone (or copper) shape: its stone kind is the material, its shape the family."""
    path, rid = short(recipe['result']), recipe['id']
    kind = kind_of(path)
    if kind == 'copper':
        base = copper_base(path)
        if base in COPPER_GOODS or path.endswith('_bulb'):
            return None
        if rid.endswith('_from_honeycomb'):
            return at('stonecutter', 'copper', 'wax_copper')
        return at('stonecutter', 'copper', COPPER_BLOCKS.get(base, 'cut_copper'))
    if kind is None:
        return None
    shape = 'stairs' if path.endswith('_stairs') else 'slabs' if path.endswith('_slab') else \
        'walls' if path.endswith('_wall') else 'blocks'
    return at('stonecutter', '' if kind == 'closed' else kind, f'{kind}_{shape}')


def is_mason(path):
    """A stone building block or shape the grid makes."""
    if path in MASON_BLOCKS:
        return True
    if path.endswith(('_stairs', '_slab', '_wall')) or path.startswith(('polished_', 'chiseled_', 'cut_')) \
            or path.endswith(('_bricks', '_tiles')):
        return kind_of(path) is not None and not path.endswith(('_button', '_pressure_plate', 'bookshelf'))
    return False


# --------------------------------------------------------------- Mechanical Press: paper, blocks, plates

PAPER = ('paper', 'book', 'writable_book', 'written_book', 'map', 'name_tag')
STORAGE = ('iron', 'gold', 'diamond', 'emerald', 'lapis', 'coal', 'raw_iron', 'raw_gold', 'raw_copper', 'amethyst',
           'redstone', 'copper', 'glowstone')
BALES = {'hay_block': 'bales', 'dried_kelp_block': 'bales', 'bone_block': 'bales', 'melon': 'bales',
         'wheat': 'bale_unpack', 'dried_kelp': 'bale_unpack'}
HONEY = {'honey_block': 'honey_blocks', 'honeycomb_block': 'honey_blocks', 'slime_block': 'honey_blocks',
         'honey_bottle': 'honey_unpack', 'slime_ball': 'honey_unpack'}


def pressed(recipe):
    path, rid = short(recipe['result']), recipe['id']
    press = 'mechanical_press'
    if path in PAPER or path.endswith('_banner_pattern'):
        return at(press, 'paper', 'banner_pattern' if path.endswith('_banner_pattern') else None)
    if path in ('heavy_weighted_pressure_plate', 'light_weighted_pressure_plate'):
        return at(press, 'metal')
    if path in BALES:
        return at(press, 'compress/farm', BALES[path])
    if path in HONEY:
        return at(press, 'compress/honey', HONEY[path])
    if path in ('packed_ice', 'blue_ice'):
        return at(press, 'compress/ice', 'packed_ice')
    if path in ('resin_block', 'sulphur_block', 'glowstone') or path.endswith('_block') and path[:-6] in STORAGE:
        return at(press, 'compress/metals', 'storage_blocks')
    if rid == 'minecraft:resin_clump' or recipe['cost'] and \
            all(short(k).endswith('_block') and copper_base(short(k))[:-6] in STORAGE for k in recipe['cost']):
        return at(press, 'compress/metals', 'unpack_blocks')
    return None


# ------------------------------------------------------------------------ Smithing Table: metal goods

IRON_GOODS = ('anvil', 'bucket', 'cauldron', 'compass', 'hopper', 'iron_bars', 'iron_chain', 'iron_door',
              'iron_trapdoor', 'lantern')


def smithed(recipe):
    path, rid = short(recipe['result']), recipe['id']
    base = copper_base(path)
    if base in COPPER_GOODS:
        return at('smithing_table', 'copper', 'wax_copper_goods' if rid.endswith('_from_honeycomb') else base)
    if path in IRON_GOODS:
        return at('smithing_table', 'iron')
    if path in ('clock', 'golden_dandelion'):
        return at('smithing_table', 'gold')
    if path.endswith('rail') or path.endswith('minecart'):
        return at('smithing_table', 'rails')
    return None


# --------------------------------------------------------------------------------- the grid recipes

def crafted(recipe):
    """A vanilla grid recipe: the bench where the thing is made."""
    item, fam, rid = recipe['result'] or '', recipe['family'], recipe['id']
    path = short(item)

    # Powders and medicine.
    if path.endswith('_dye') and not fam.endswith('_bundle'):
        colour = path[:-4]
        group = 'flowers/warm' if colour in ('red', 'orange', 'yellow', 'pink', 'magenta', 'brown') else \
                'flowers/cool' if colour in ('blue', 'light_blue', 'cyan', 'purple', 'lime', 'green') else 'flowers/neutral'
        return at('mortar_and_pestle', group, fam)
    if fam == 'bonemeal':
        return at('mortar_and_pestle', 'bone', 'bonemeal')
    if fam == 'sugar':
        return at('mortar_and_pestle', 'minerals', 'sugar')
    if fam == 'suspicious_stew':
        return at('medicine_bench', 'tonics', 'suspicious_stew', 'a tonic here: the flower picks the effect')

    # Paper, pressed blocks and plates (unpacking a bale comes before the food it holds), metal goods, food, fire.
    for rule in (pressed, smithed):
        placed = rule(recipe)
        if placed:
            return placed
    food = cooked(path, fam if fam == 'rabbit_stew' else None)
    if food:
        return food
    if path in ('iron_nugget', 'gold_nugget', 'copper_nugget') or 'from_nuggets' in rid:
        return at('primitive_forge', 'nuggets', metal_family(recipe))
    if fam in ('stained_glass', 'stained_glass_pane'):
        return at('primitive_forge', 'glass', fam)
    if path in FORGE_CRAFTS:
        return at('primitive_forge', FORGE_CRAFTS[path])
    if fam == 'stained_terracotta':
        return at('primitive_forge', 'clay/fired', 'stained_terracotta')

    # Stone and copper blocks.
    if 'copper' in path and (copper_base(path) in COPPER_BLOCKS or rid.endswith('_from_honeycomb')
                             and copper_base(path) in ('copper', 'copper_block')):
        return cut(recipe)
    if is_mason(path) and not fam.startswith('wooden_') and not path.startswith('bamboo_mosaic'):
        return cut(recipe)

    # The Working Station: carpentry.
    if fam in ('planks', 'bark', 'sticks') or path in ('bamboo_block', 'bamboo_mosaic'):
        return ws('wood/planks', fam if fam in ('planks', 'bark', 'sticks') else None)
    if path in ('torch', 'copper_torch'):
        return ws('wood/planks', 'torches')
    if path in ('bamboo_mosaic_stairs', 'bamboo_mosaic_slab'):
        return ws('wood/building', 'wooden_stairs' if path.endswith('stairs') else 'wooden_slab')
    if fam in ('wooden_stairs', 'wooden_slab', 'wooden_fence', 'wooden_fence_gate', 'wooden_door', 'wooden_trapdoor') \
            or path in ('ladder', 'scaffolding'):
        return ws('wood/building', fam if fam.startswith('wooden_') else None)
    if fam in ('wooden_sign', 'hanging_sign', 'shelf') or path in ('item_frame', 'glow_item_frame', 'painting', 'armor_stand'):
        return ws('wood/signs', fam if fam in ('wooden_sign', 'hanging_sign', 'shelf') else None)
    if fam in ('boat', 'chest_boat'):
        return ws('wood/boats', fam)
    if path in ('barrel', 'bookshelf', 'chiseled_bookshelf', 'lectern', 'composter', 'note_block', 'jukebox',
                'fletching_table', 'beehive', 'bowl', 'grindstone') or path.startswith('music_disc'):
        return ws('wood/furniture')
    if path == 'stonecutter':
        return ws('camp/stations')

    # Switches, power and machines.
    if fam in ('wooden_button', 'wooden_pressure_plate') or path in ('stone_button', 'stone_pressure_plate', 'lever',
                                                                      'tripwire_hook', 'polished_blackstone_button',
                                                                      'polished_blackstone_pressure_plate'):
        return ws('redstone/switches', fam if fam in ('wooden_button', 'wooden_pressure_plate') else None)
    if path in ('redstone_torch', 'repeater', 'comparator', 'observer', 'daylight_detector', 'target', 'redstone_lamp'):
        return ws('redstone/power')
    if path in ('piston', 'sticky_piston', 'dispenser', 'dropper', 'crafter', 'tnt'):
        return ws('redstone/mechanisms')

    # Earth, farm and fibre.
    if path in ('clay', 'packed_mud', 'muddy_mangrove_roots', 'coarse_dirt', 'snow_block', 'snow') or fam == 'concrete_powder':
        return ws('earth', fam if fam == 'concrete_powder' else None)
    if path in ('melon_seeds', 'pumpkin_seeds', 'jack_o_lantern', 'candle') or fam == 'dyed_candle':
        return ws('farm', fam if fam == 'dyed_candle' else None)
    if path == 'white_wool' or fam == 'wool':
        return ws('fibre/wool', 'dye_wool' if fam == 'wool' else 'wool')
    if fam in ('carpet', 'carpet_dye'):
        return ws('fibre/wool', 'carpet' if fam == 'carpet' else 'dye_carpet')
    if fam in ('bed', 'bed_dye'):
        return ws('fibre/banners', 'bed' if fam == 'bed' else 'dye_bed')
    if fam == 'banner' or rid.endswith('_banner_duplicate'):
        return ws('fibre/banners', 'banner' if fam == 'banner' else 'banner_copy')
    if path in ('leather', 'bundle') or fam in ('bundle_dye', 'lead'):
        return ws('fibre/leather', fam if fam in ('bundle_dye', 'lead') else None)

    # Weapons and tools the Armoury adds to its own designs.
    if path in ('carrot_on_a_stick', 'warped_fungus_on_a_stick', 'spectral_arrow'):
        return at('armoury', 'wood')
    return None


def ark_place(recipe):
    """The mod's own grid recipes."""
    item, rid = recipe['result'] or '', recipe['id']
    path = short(item)
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
    if path == 'lead':
        return ws('fibre/leather', 'lead')
    if path == 'resin_clump':
        return ws('wood', 'resin_clump')
    if path == 'bronze_blend':
        return at('primitive_forge', 'alloys')
    if path == 'sulphur_block':
        return at('mechanical_press', 'compress/metals', 'storage_blocks')
    if path == 'sharp_rock':
        return at('armoury', 'rock', note='knapped like the flint knife: the arrowhead')
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
            if 'item' in entry:   # a family of variants only adds recipes
                designed.setdefault(entry['item'], (bench, where, entry))
    records = []
    recipes = source.recipes()
    source.cut_results = {r['result'] for r in recipes if r['type'] == 'stonecutting'}
    source.fire_results = {r['result'] for r in recipes if r['type'] == 'campfire_cooking'}
    for recipe in recipes:
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
                    ruled = place(recipe, source) or {}
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
        if 'variants' in entry:
            variants = [{'item': v['item'], 'count': v.get('count', 1), 'cost': v['cost']} for v in entry['variants']]
            result.append({'family': entry['family'], 'level': entry.get('level', level), 'variants': variants,
                           **({'title': entry['title']} if entry.get('title') else {})})
            continue
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
            if r.get('time'):
                variant['time'] = r['time']
            if r['type'] == 'smithing_trim':
                variant['apply'] = True  # the trim goes onto the armour piece; nothing new comes out
            variants.append(variant)
        result.append({'family': family, 'title': title_of(family) if len(variants) > 1 else None,
                       'level': max([level] + [r['level'] for r in members if r.get('level')]), 'variants': variants})
    return result


def places(designs):
    """Every place each bench design has: its materials and material/group pairs."""
    return {bench: {c['id'] for c in data['categories']} | {f"{c['id']}/{e['group']}" for c in data['categories']
                                                             for e in c.get('items', []) if 'group' in e}
            for bench, data in designs.items()}


def unshown(records, designs):
    """Kept recipes that no bench draws: a bench or a place its design does not have."""
    known = places(designs)
    return [r for r in records if r['fate'] in ('stay', 'change') and not r['special']
            and r['place'] not in known.get(r['bench'], ())]


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
    for r in unshown(records, designs):
        print('not shown:', r['id'], r['bench'] or '(no bench)', r['place'] or '(no place)')
