"""Recipe gates: the item progression spec (Prehistoric + vanilla) drawn as a schematic page.

Sheet 1 is the workstation spine: every station or tool gate once, in unlock order, with its recipes as
pins (inputs on the left, outputs on the right). Items made on another row arrive as net labels instead of
long wires. Sheet 2 is the vanilla netlist: what each material unlocks. Then the cut list and the open
decisions.

Sources: the user's recipe-gate spec (2026-09-26), the mod's generated recipes and theme policy, and the
vanilla 26.1.2 jar (recipes and textures). Run from Ark/: python tools/build_item_flow.py
Writes build/item-flow.html.
"""
import base64
import html
import io
import json
import re
import zipfile
from pathlib import Path

from PIL import Image

ARK = Path(__file__).resolve().parents[1]
JARS = [ARK / 'build/moddev/artifacts/minecraft-patched-26.1.2.109-merged.jar',
        ARK / 'build/moddev/artifacts/minecraft-patched-26.1.2.109-sources.jar']
JAR = next((j for j in JARS if j.is_file()), JARS[0])
MOD_TEX = ARK / 'src/main/resources/assets/arksurvivalreturns/textures'
POLICY = ARK / 'src/main/java/dev/nez/arksurvivalreturns/feature/theme/ThemePolicy.java'
OUT = ARK / 'build/item-flow.html'
HERE = Path(__file__).resolve().parent
GRASS, FOLIAGE = (124, 189, 107), (89, 174, 48)

# ---------------------------------------------------------------- items
# id: (name, icon, art, was). icon: 'mc:<texture>' (vanilla), 'ark:<texture>', 'cube' (placed block with
# its own 3D model) or None. art: 'new' = no sprite yet, 'standin' = drawn with a borrowed sprite.
ITEMS = {
    # world sources
    'w_loose_rock': ('Loose rock', 'ark:item/rock', None, None),
    'w_grass': ('Grass', 'mc:block/short_grass', None, None),
    'w_leaves': ('Leaves', 'mc:block/oak_leaves', None, None),
    'w_gravel': ('Gravel', 'mc:block/gravel', None, None),
    'w_clay': ('Clay', 'mc:block/clay', None, None),
    'w_log': ('Tree log', 'mc:block/oak_log', None, None),
    'w_stone': ('Stone', 'mc:block/stone', None, None),
    'w_coal_ore': ('Coal ore', 'mc:block/coal_ore', None, None),
    'w_metal_ore': ('Copper, iron, lapis ore', 'mc:block/iron_ore', None, None),
    'w_creature': ('Dinosaur', 'ark:item/parasaur_spawn_egg', None, None),
    'w_animal': ('Vanilla animal', 'mc:item/cow_spawn_egg', None, None),
    'w_spider': ('Spider', 'mc:item/spider_spawn_egg', None, None),
    'w_allosaurus': ('Wild Allosaurus', 'ark:item/allosaurus_spawn_egg', None, None),
    'w_horned': ('Horned, plated or beaked', 'ark:item/triceratops_spawn_egg', None, None),
    'w_goat': ('Goat', 'mc:item/goat_spawn_egg', None, None),
    # prehistoric
    'rock': ('Rock', 'ark:item/rock', 'standin', None),
    'blackberry': ('Blackberry', 'ark:item/narcoberry', None, 'Narcoberry'),
    'redberry': ('Redberry', 'ark:item/tintoberry', None, 'Tintoberry'),
    'yellowberry': ('Yellowberry', 'ark:item/amarberry', None, 'Amarberry'),
    'blueberry': ('Blueberry', 'ark:item/azulberry', None, 'Azulberry'),
    'berry': ('Any berry', 'ark:item/tintoberry', None, None),
    'fiber': ('Fiber', 'mc:item/wheat', 'standin', 'Plant Fiber'),
    'twig': ('Twig', None, 'new', None),
    'stick': ('Stick', 'mc:item/stick', None, None),
    'flint': ('Flint', 'mc:item/flint', None, None),
    'clay_ball': ('Clay Ball', 'mc:item/clay_ball', None, None),
    'sedative': ('Sedative', None, 'new', None),
    'narcotics': ('Narcotics', None, 'new', None),
    'medicine': ('Medicine', None, 'new', None),
    'blue_tbd': ('Blueberry product', None, 'new', None),
    'rock_axe': ('Rock Axe', 'mc:item/stone_axe', 'standin', 'Stone Hatchet'),
    'rock_pickaxe': ('Rock Pickaxe', None, 'new', None),
    'rock_sword': ('Rock Sword', None, 'new', 'Stone Knife'),
    'rock_shovel': ('Rock Shovel', None, 'new', None),
    'rock_hoe': ('Rock Hoe', None, 'new', None),
    'fire_starter': ('Fire Starter', 'ark:item/fire_starter', None, None),
    'bandage': ('Fiber Bandage', 'mc:item/paper', 'standin', None),
    'string': ('String', 'mc:item/string', None, None),
    'log': ('Log', 'mc:block/oak_log', None, None),
    'planks': ('Planks', 'mc:block/oak_planks', None, None),
    'crafting_table': ('Crafting Table', 'mc:block/crafting_table_front', None, None),
    'bedroll': ('Primitive Bedroll', 'cube', None, None),
    'cobblestone': ('Cobblestone', 'mc:block/cobblestone', None, None),
    'stone_tools': ('Stone tools ×6', 'mc:item/stone_pickaxe', None, None),
    'wood_spear': ('Wooden Spear', 'mc:item/wooden_spear', None, None),
    'stone_fire': ('Stone Fire', 'cube', None, None),
    'drying_rack': ('Drying Rack', 'cube', None, None),
    'mortar': ('Mortar & Pestle', 'cube', 'new', None),
    'cooking_pot': ('Cooking Pot', 'cube', None, None),
    'forge': ('Primitive Forge', 'cube', None, None),
    'sharp_rock': ('Sharp Rock', 'ark:item/sharp_rock', None, None),
    'keratin': ('Keratin', 'ark:item/keratin', None, None),
    'keratin_spear': ('Keratin Spear', 'ark:item/keratin_spear', None, 'Flint Spear'),
    'keratin_helmet': ('Keratin Helmet', 'ark:item/keratin_helmet', None, None),
    'keratin_chestplate': ('Keratin Chestplate', 'ark:item/keratin_chestplate', None, None),
    'keratin_leggings': ('Keratin Leggings', 'ark:item/keratin_leggings', None, None),
    'keratin_boots': ('Keratin Boots', 'ark:item/keratin_boots', None, None),
    'flint_knife': ('Flint Knife', 'mc:item/flint', 'standin', None),
    'bow': ('Bow', 'mc:item/bow', None, None),
    'arrow': ('Arrow', 'mc:item/arrow', None, None),
    'feather': ('Feather', 'mc:item/feather', None, None),
    'bone': ('Bone', 'mc:item/bone', None, None),
    'leather': ('Leather', 'mc:item/leather', None, None),
    'tranq_arrow': ('Tranq Arrow', 'mc:item/arrow', 'standin', None),
    'improved_tranq': ('Improved Tranq Arrow', 'mc:item/spectral_arrow', 'standin', None),
    'conc_sedative': ('Concentrated Sedative', 'mc:item/gunpowder', 'standin', None),
    'lead': ('Lead', 'mc:item/lead', None, None),
    'pack_harness': ('Pack Harness', 'mc:item/saddle', 'standin', None),
    'reinforced_harness': ('Reinforced Harness', 'mc:item/iron_horse_armor', 'standin', None),
    'book': ('Book', 'mc:item/book', None, None),
    'field_journal': ('Field Journal', 'mc:item/book', 'standin', None),
    'spruce_log': ('Spruce Log', 'mc:block/spruce_log', None, None),
    'resin': ('Resin Clump', 'mc:item/resin_clump', None, None),
    'trough': ('Trough', 'cube', None, None),
    'chest': ('Chest', 'cube', None, None),
    'coal': ('Coal', 'mc:item/coal', None, None),
    'torch': ('Torch', 'mc:block/torch', None, None),
    'campfire': ('Campfire', 'mc:item/campfire', None, None),
    'raw_meat': ('Raw meat ×7 kinds', 'ark:item/raw_herbivore_meat', None, None),
    'cooked_meat': ('Cooked meat ×7', 'ark:item/cooked_herbivore_meat', None, None),
    'hide_bone': ('Leather, Bone, Feather', 'mc:item/leather', None, None),
    'vanilla_drops': ('Vanilla meat, Wool, Leather', 'mc:item/beef', None, None),
    'vanilla_raw': ('Vanilla raw food ×9', 'mc:item/beef', None, None),
    'vanilla_cooked': ('Cooked ×9', 'mc:item/cooked_beef', None, None),
    'allosaur_heart': ('Allosaur Heart', 'ark:item/allosaur_heart', None, None),
    'creature_tbd': ('Blood, heart…', None, 'new', None),
    'raw_or_fish': ('Raw meat or fish', 'ark:item/raw_herbivore_meat', None, None),
    'dried_1': ('Dried Meat I', 'ark:item/dried_meat', None, None),
    'dried_2': ('Dried Meat II', 'ark:item/dried_meat', None, None),
    'dried_3': ('Dried Meat III', 'ark:item/dried_meat', None, None),
    'dried_ration': ('Dried Ration', 'mc:item/bread', 'standin', None),
    'dried_food': ('Dried food', 'ark:item/dried_meat', None, None),
    'meat': ('Any meat', 'ark:item/raw_herbivore_meat', None, None),
    'carrot': ('Carrot', 'mc:item/carrot', None, None),
    'hearty_stew': ('Hearty Stew', 'mc:item/rabbit_stew', 'standin', None),
    'trail_mix': ('Trail Mix', 'mc:item/cookie', 'standin', None),
    'raw_ores': ('Raw copper, iron, gold', 'mc:item/raw_iron', None, None),
    'ingots': ('Copper, Iron, Gold ingot', 'mc:item/iron_ingot', None, None),
    'blocked_ore': ('✕ nothing at wood tier', 'mc:item/raw_copper', None, None),
    'sand': ('Sand', 'mc:block/sand', None, None),
    'glass': ('Glass', 'mc:block/glass', None, None),
    'brick': ('Brick', 'mc:item/brick', None, None),
    'stone': ('Stone', 'mc:block/stone', None, None),
    'charcoal': ('Charcoal', 'mc:item/charcoal', None, None),
    'smelt_rest': ('Every other non-food smelt', 'mc:item/iron_nugget', None, None),
    'smelt_in': ('Terracotta, sponge, stone variants…', 'mc:block/terracotta', None, None),
    'workshop_schematic': ('Workshop Schematic', 'ark:item/workshop_schematic', None, None),
    'guardian_trophy': ('Guardian Trophy', 'ark:item/guardian_trophy', None, None),
    # vanilla stations
    'stone_family': ('Stone, brick, copper blocks', 'mc:block/stone_bricks', None, None),
    'stone_variants': ('~275 cut variants', 'mc:block/chiseled_stone_bricks', None, None),
    'damaged': ('Damaged item + material', 'mc:item/iron_pickaxe', None, None),
    'repaired': ('Repaired item', 'mc:item/iron_pickaxe', None, None),
    'name_tag': ('Item + new name', 'mc:item/name_tag', None, None),
    'renamed': ('Renamed item', 'mc:item/name_tag', None, None),
    'two_damaged': ('2 damaged tools', 'mc:item/iron_axe', None, None),
    'trim_in': ('Template + armour + material', 'mc:item/coast_armor_trim_smithing_template', None, None),
    'trimmed': ('Trimmed armour', 'mc:item/iron_chestplate', None, None),
    'netherite_up': ('Netherite upgrade', 'mc:item/netherite_ingot', None, None),
    'banner_in': ('Banner + dye (+ pattern)', 'mc:item/white_dye', None, None),
    'banner_out': ('Patterned banner', 'mc:item/flower_banner_pattern', None, None),
    'map_in': ('Map + paper / pane / map', 'mc:item/filled_map', None, None),
    'map_out': ('Zoomed, locked or copied map', 'mc:item/filled_map', None, None),
    'plants': ('Seeds, leaves, crops', 'mc:item/wheat_seeds', None, None),
    'bone_meal': ('Bone Meal', 'mc:item/bone_meal', None, None),
    'crafter_in': ('Ingredients + redstone pulse', 'mc:item/redstone', None, None),
    'crafter_out': ('Any crafting-table result', 'mc:block/crafter_north', None, None),
    'potion_in': ('Blaze powder + nether wart', 'mc:item/blaze_powder', None, None),
    'potions': ('Potions', 'mc:item/potion', None, None),
    'ench_in': ('Item + lapis + levels', 'mc:item/lapis_lazuli', None, None),
    'enchanted': ('Enchanted item', 'mc:item/enchanted_book', None, None),
    'fuel_food': ('Ore or food + fuel', 'mc:item/coal', None, None),
    'smelted': ('Smelted or cooked item', 'mc:item/iron_ingot', None, None),
}

NODE_ICONS = {  # extra textures used only as node glyphs
    'g_stonecutter': 'mc:block/stonecutter_saw', 'g_anvil': 'mc:block/anvil', 'g_grindstone': 'mc:block/grindstone_side',
    'g_smithing': 'mc:block/smithing_table_front', 'g_loom': 'mc:block/loom_front',
    'g_cartography': 'mc:block/cartography_table_top', 'g_fletching': 'mc:block/fletching_table_front',
    'g_composter': 'mc:block/composter_side', 'g_crafter': 'mc:block/crafter_north',
    'g_brewing': 'mc:item/brewing_stand', 'g_enchanting': 'mc:block/enchanting_table_top',
    'g_furnace': 'mc:block/furnace_front', 'g_pickaxe': 'mc:item/stone_pickaxe', 'g_sword': 'mc:item/stone_sword',
    'g_axe': 'mc:item/stone_axe', 'g_campfire': 'mc:item/campfire',
}


# ---------------------------------------------------------------- sheet 1: the spine
def i(item, n=1, ch=None, val=None):
    return {'id': item, 'n': n, 'ch': ch, 'val': val}


def row(step, st, ins, outs, op='', note=None):
    return {'step': step, 'st': st, 'ins': ins, 'outs': outs, 'op': op, 'note': note}


def node(key, kind, name, sub, glyph, rows, divider=None, st=None):
    return {'key': key, 'kind': kind, 'name': name, 'sub': sub, 'glyph': glyph, 'rows': rows,
            'divider': divider, 'st': st}


W = lambda item, **k: dict(i(item, **k), world=True)  # a world source, not a net label

SPINE = [
    node('hands', 'gate', 'Bare hands', '1 · starts with nothing', 'empty', [
        row('1.1', 'you', [W('w_loose_rock')], [i('rock')], 'pick up',
            'Loose rocks lie on the ground in every Overworld biome (F02). A rock is not stone: it never turns '
            'into cobblestone. Needs a dedicated sprite.'),
        row('1.2', 'you', [W('w_grass')], [i('blackberry', val='+1 food · torpor'), i('redberry', val='+1 heart · +1 food'),
                                           i('yellowberry', val='+3 food'), i('blueberry', val='TBD')], 'break, 35%',
            'In game today: Narcoberry, Tintoberry, Amarberry and Azulberry. A grass block drops a berry 35% of the '
            'time: red, yellow and blue 30% each of that, black 10%. The food values are yours; the red berry '
            'starts the medicine path.'),
        row('1.3', 'you', [W('w_grass')], [i('fiber', ch='45%')], 'break, 45%',
            'Fiber is the primitive rope and goes into every rock tool. Today it is drawn with the wheat sprite.'),
        row('1.4', 'you', [W('w_leaves')], [i('twig')], 'break',
            'New item. Today leaves drop a Stick 20% of the time; that drop becomes the Twig.'),
        row('', 'now', [W('w_gravel')], [i('flint', ch='10%')], 'break'),
        row('', 'now', [W('w_clay')], [i('clay_ball', 4)], 'dig'),
    ]),
    node('inv', 'station', 'Inventory', '2×2 grid · always open', 'grid2', [
        row('1.2.1', 'you', [i('blackberry', 4)], [i('sedative', 4)], 'shapeless'),
        row('1.4', 'prop', [i('twig', 2)], [i('stick')], 'shapeless',
            'You set Twig → Stick; the 2 → 1 ratio is a proposal. After the axe, planks give sticks cheaply, so '
            'twigs only matter before the first log.'),
        row('2.1', 'change', [i('rock'), i('stick'), i('fiber')], [i('rock_axe', val='wood stats')], 'shapeless',
            'Today this is the Stone Hatchet. It has to fit the 2×2 grid: logs need an axe, and the crafting '
            'table needs logs. Uses the vanilla stone axe sprite (B04).'),
        row('', 'now', [i('rock', 2)], [i('sharp_rock')], 'shapeless',
            'Knapping: one rock struck on another. The sharp flake tips arrows in place of flint.'),
        row('', 'now', [i('stick', 2), i('fiber')], [i('fire_starter')], 'shapeless'),
        row('', 'now', [i('fiber', 3), i('string')], [i('bandage', 2)], 'shapeless'),
        row('2.1', 'now', [i('log')], [i('planks', 4)], 'vanilla'),
        row('2.1', 'now', [i('planks', 2)], [i('stick', 4)], 'vanilla'),
        row('2.1', 'now', [i('planks', 4)], [i('crafting_table')], 'vanilla'),
    ]),
    node('axe', 'gate', 'Rock Axe', '2.1 · chops logs', 'g_axe', [
        row('2.1', 'you', [W('w_log')], [i('log', val='10 Overworld woods')], 'chop',
            'Without an axe a log breaks at a quarter speed and drops nothing (in game). Crimson and warped '
            'wood are Nether-only, so 10 kinds remain.'),
    ]),
    node('table', 'station', 'Crafting Table', '3×3 grid', 'grid3', [
        row('1.3', 'change', [i('fiber', 9)], [i('bedroll')], 'shapeless',
            'Today: 4 Fiber + 1 Leather. Nine items need the 3×3 grid, so the bedroll arrives right after the '
            'axe and the table, not at step 1 (Q2).'),
        row('2.2', 'you', [i('rock', 3), i('stick', 2), i('fiber')], [i('rock_pickaxe', val='wood stats')],
            'pickaxe + fiber', 'The vanilla pickaxe pattern with one Fiber in any free slot (Q3). Its mining '
            'tier decides whether copper and iron can ever be reached (Q1).'),
        row('2.3', 'you', [i('rock', 2), i('stick'), i('fiber')], [i('rock_sword', val='wood stats')],
            'sword + fiber', 'Replaces the Stone Knife; the London tech node moves to it (Q6).'),
        row('', 'prop', [i('rock'), i('stick', 2), i('fiber')], [i('rock_shovel', val='wood stats')], 'shovel + fiber'),
        row('', 'prop', [i('rock', 2), i('stick', 2), i('fiber')], [i('rock_hoe', val='wood stats')], 'hoe + fiber'),
        row('', 'cut', [i('rock', 4)], [i('cobblestone')], '2×2',
            'Removed by your spec: cobblestone only comes from mining stone with the rock pickaxe.'),
        row('', 'cut', [i('cobblestone', 3), i('stick', 2)], [i('stone_tools')], 'vanilla',
            'Stone pickaxe, axe, shovel, hoe, sword and spear. Wooden tools are already removed.'),
        row('', 'cut', [i('planks'), i('stick')], [i('wood_spear')], 'vanilla',
            'The 1.21.11 wooden spear is missing from today\'s wooden-tool filter, so it is still craftable.'),
        row('', 'now', [i('rock', 5), i('stick')], [i('stone_fire')], 'shaped'),
        row('2.3.1', 'change', [i('log', 2), i('stick', 4)], [i('drying_rack')], 'shaped',
            'You asked for wood and sticks; the 2 logs + 4 sticks split is a proposal. Today: 4 Sticks + 1 Fiber.'),
        row('', 'prop', [i('cobblestone', 3), i('rock')], [i('mortar')], 'shaped',
            'Planned station (B02). The recipe is a proposal: a stone bowl and a rock pestle.'),
        row('', 'now', [i('cobblestone', 4), i('fiber', 2)], [i('cooking_pot')], 'shapeless'),
        row('2.2', 'now', [i('cobblestone', 8), i('stone_fire')], [i('forge')], 'shaped',
            'Your step "Stone → Forge": stone only, eight cobblestone walled around a Stone Fire (no clay). '
            'The forge needs cobblestone, so it follows the rock pickaxe.'),
        row('', 'now', [i('keratin'), i('stick', 2), i('fiber')], [i('keratin_spear', val='stone-tier spear')], 'shapeless',
            'The first weapon tier, replacing the Flint Spear: a vanilla spear (jab, and a charge on use) with '
            'the Better Combat two-handed stab and a block of extra reach (I11).'),
        row('', 'now', [i('keratin', 5)], [i('keratin_helmet', val='1 armour')], 'helmet'),
        row('', 'now', [i('keratin', 8)], [i('keratin_chestplate', val='4 armour')], 'chestplate'),
        row('', 'now', [i('keratin', 7)], [i('keratin_leggings', val='2 armour')], 'leggings'),
        row('', 'now', [i('keratin', 4)], [i('keratin_boots', val='1 armour')], 'boots',
            'The first armour tier: 8 points for the set, above leather (7) and below copper (10).'),
        row('', 'cut', [i('flint'), i('stick'), i('fiber')], [i('flint_knife')], 'shapeless',
            'Proposed removal: the Rock Sword covers the knife role. It is also in the starter kit (Q4, Q6).'),
        row('', 'now', [i('stick', 3), i('string', 3)], [i('bow')], 'vanilla'),
        row('', 'change', [i('sharp_rock'), i('stick'), i('feather')], [i('arrow', 4)], 'shaped',
            'The vanilla arrow recipe now takes a Sharp Rock; flint no longer tips arrows.'),
        row('', 'change', [i('arrow', 4), i('sedative'), i('bone')], [i('tranq_arrow', 4)], 'shapeless',
            'Today it takes a Narcoberry. The Sedative replaces it (Q7).'),
        row('', 'change', [i('tranq_arrow', 4), i('narcotics')], [i('improved_tranq', 4)], 'shapeless',
            'Today it takes a Concentrated Sedative. Narcotics replace it (Q7).'),
        row('', 'cut', [i('blackberry', 3), i('fiber')], [i('conc_sedative')], 'shapeless',
            'Proposed removal: Narcotics from the mortar take its place; the Narcotraffic node moves to '
            'Narcotics (Q7).'),
        row('', 'now', [i('fiber', 5)], [i('lead')], 'shaped'),
        row('', 'now', [i('leather', 3), i('fiber', 2)], [i('pack_harness')], 'shapeless'),
        row('', 'now', [i('pack_harness'), i('leather'), i('flint'), i('fiber', 2)], [i('reinforced_harness')], 'shapeless'),
        row('', 'now', [i('book'), i('leather', 2)], [i('field_journal')], 'shapeless'),
        row('', 'now', [i('spruce_log'), i('flint')], [i('resin', 2)], 'shapeless'),
        row('', 'now', [i('planks', 5), i('resin'), i('fiber')], [i('trough')], 'shaped'),
        row('', 'now', [i('planks', 8)], [i('chest')], 'vanilla'),
        row('', 'now', [i('coal'), i('stick')], [i('torch', 4)], 'vanilla'),
        row('', 'now', [i('log', 3), i('stick', 3), i('coal')], [i('campfire')], 'vanilla',
            'Also lights the "A little warmth" node. Keep it beside the Stone Fire? (Q12)'),
    ]),
    node('pick', 'gate', 'Rock Pickaxe', '2.2 · mines stone', 'g_pickaxe', [
        row('2.2', 'you', [W('w_stone')], [i('cobblestone')], 'mine'),
        row('', 'now', [W('w_coal_ore')], [i('coal')], 'mine'),
        row('', 'block', [W('w_metal_ore')], [i('blocked_ore')], 'mine',
            'At wood tier the pickaxe cannot harvest copper, iron or lapis ore, and stone tools are gone. '
            'Nothing past the forge is reachable until this is decided (Q1).'),
    ]),
    node('sword', 'gate', 'Rock Sword', '2.3 · kills', 'g_sword', [
        row('2.3', 'you', [W('w_creature')], [i('raw_meat')], 'kill',
            'Seven meat families by body plan (F05); big creatures add prime cuts.'),
        row('', 'now', [W('w_creature')], [i('hide_bone')], 'kill',
            'Leather scales with the creature\'s health; birds and sea creatures give no hide.'),
        row('2.3.2', 'tbd', [W('w_creature')], [i('creature_tbd')], 'kill', 'Yours to decide: blood, heart and other creature drops.'),
        row('', 'now', [W('w_horned')], [i('keratin', val='Trike 2-4 · Anky 1-3')], 'kill',
            'Horns, antlers, plates and big beaks: Triceratops, Ankylosaurus, Megalocerus, Carnotaurus, '
            'Ceratosaurus, Unicorn, Therizinosaurus, Argentavis, Terrorbird, Pegomastax and Lystrosaurus. '
            'Stegosaurus is listed for when it joins the roster.'),
        row('', 'now', [W('w_goat')], [i('keratin', ch='50%')], 'kill'),
        row('', 'now', [W('w_allosaurus')], [i('allosaur_heart')], 'kill'),
        row('', 'now', [W('w_spider')], [i('string')], 'kill'),
        row('', 'now', [W('w_animal')], [i('vanilla_drops')], 'kill',
            'Cows, pigs, sheep, chickens and the rest keep their drops; they also drop bones now that skeletons '
            'are gone.'),
    ]),
    node('fire', 'station', 'Stone Fire', 'rocks · lit with the Fire Starter', 'cube', [
        row('2.3', 'now', [i('raw_meat')], [i('cooked_meat')], '4-item spit'),
        row('', 'now', [i('vanilla_raw')], [i('vanilla_cooked')], '4-item spit'),
        row('', 'now', [i('stick')], [i('torch')], 'hold to flame', 'The Prometheus node.'),
    ]),
    node('rack', 'station', 'Drying Rack', 'two blocks tall', 'cube', [
        row('2.3.1', 'you', [i('raw_or_fish')], [i('dried_1'), i('dried_2', val='1 day · haste'),
                                                 i('dried_3', val='3 days · strength')], 'hang',
            'Left hanging, Dried Meat I cures into II and III (F04). III also gives regeneration.'),
        row('', 'now', [i('berry')], [i('dried_ration')], 'hang'),
    ]),
    node('mortar', 'station', 'Mortar & Pestle', 'planned · B02', 'cube', [
        row('1.2.1', 'you', [i('sedative', 4)], [i('narcotics', 4)], 'grind'),
        row('1.2.2', 'tbd', [i('redberry')], [i('medicine')], 'grind', 'The medicine path starts here; recipes to be decided.'),
        row('1.2.4', 'tbd', [i('blueberry')], [i('blue_tbd')], 'grind'),
    ]),
    node('pot', 'station', 'Cooking Pot', 'sits on a lit Stone Fire', 'cube', [
        row('', 'now', [i('dried_food', 2), i('meat'), i('carrot')], [i('hearty_stew', val='regeneration')], '4 slots'),
        row('', 'now', [i('dried_food', 2), i('berry', 2)], [i('trail_mix', val='speed')], '4 slots'),
    ]),
    node('forge', 'station', 'Primitive Forge', 'two blocks · clay bloomery', 'cube', [
        row('', 'now', [i('raw_ores')], [i('ingots')], 'smelt', 'Ore tier: see Q1.'),
        row('', 'now', [i('sand')], [i('glass')], 'smelt'),
        row('', 'now', [i('clay_ball')], [i('brick')], 'smelt'),
        row('', 'now', [i('cobblestone')], [i('stone')], 'smelt'),
        row('', 'now', [i('log')], [i('charcoal')], 'smelt'),
        row('', 'now', [i('smelt_in')], [i('smelt_rest')], 'smelt',
            'Every non-food smelting recipe, modded ones included. Food goes to the Stone Fire.'),
    ]),
    node('stonecutter', 'station', 'Stonecutter', '3 Stone + Iron ingot', 'g_stonecutter', [
        row('', 'now', [i('stone_family')], [i('stone_variants')], 'cut'),
    ], divider='Vanilla stations · built from forge metals'),
    node('anvil', 'station', 'Anvil', '3 Iron blocks + 4 Iron', 'g_anvil', [
        row('', 'now', [i('damaged')], [i('repaired')], 'repair'),
        row('', 'now', [i('name_tag')], [i('renamed')], 'rename', 'No enchanting, so no book merging.'),
    ]),
    node('grindstone', 'station', 'Grindstone', '2 Stick + Stone slab + 2 Planks', 'g_grindstone', [
        row('', 'now', [i('two_damaged')], [i('repaired')], 'combine', 'Only repairs: there are no enchantments to strip.'),
    ]),
    node('smithing', 'station', 'Smithing Table', '2 Iron + 4 Planks', 'g_smithing', [
        row('', 'now', [i('trim_in')], [i('trimmed')], 'trim',
            '7 of 18 trim templates can still be found (coast, dune, wild and the four trail-ruin ones); the '
            'other 11 come from removed structures.'),
        row('', 'cut', [i('netherite_up')], [i('netherite_up')], 'upgrade'),
    ]),
    node('loom', 'station', 'Loom', '2 String + 2 Planks', 'g_loom', [
        row('', 'now', [i('banner_in')], [i('banner_out')], 'weave'),
    ]),
    node('cartography', 'station', 'Cartography Table', '2 Paper + 4 Planks', 'g_cartography', [
        row('', 'now', [i('map_in')], [i('map_out')], 'map', 'Xaero\'s map (I01) is the main map.'),
    ]),
    node('composter', 'station', 'Composter', '7 Wooden slabs', 'g_composter', [
        row('', 'now', [i('plants')], [i('bone_meal')], 'compost'),
    ]),
    node('crafter', 'station', 'Crafter', '5 Iron + Table + 2 Redstone + Dropper', 'g_crafter', [
        row('', 'now', [i('crafter_in')], [i('crafter_out')], 'auto-craft'),
    ]),
    node('furnaces', 'station', 'Furnace, Smoker, Blast Furnace', 'removed · the fire and forge replace them', 'g_furnace', [
        row('', 'cut', [i('fuel_food')], [i('smelted')], 'smelt'),
    ], st='cut'),
    node('brewing', 'station', 'Brewing Stand', 'removed by the theme', 'g_brewing', [
        row('', 'cut', [i('potion_in')], [i('potions')], 'brew'),
    ], st='cut'),
    node('enchanting', 'station', 'Enchanting Table', 'removed by the theme', 'g_enchanting', [
        row('', 'cut', [i('ench_in')], [i('enchanted')], 'enchant'),
    ], st='cut'),
    node('guardian', 'gate', 'First Guardian', 'Prehistoric finale → Bronze Age', 'ark:item/guardian_trophy', [
        row('', 'now', [i('allosaur_heart')], [i('workshop_schematic'), i('guardian_trophy')], 'ritual',
            'The heart starts the Guardian Giganotosaurus ritual (P05).'),
    ], divider='Age gate'),
]

# ---------------------------------------------------------------- sheet 2: vanilla netlist
# station tags: 2x2, T table, SC stonecutter, FG forge, FI stone fire, SM smithing, LM loom, W world
# status: now, spec (your spec changes it), cut, starved (recipe stays, the material has no source)
def f(label, tag='T', st='now', need=''):
    return {'label': label, 'tag': tag, 'st': st, 'need': need}


NETS = [
    ('WOOD', 'Rock Axe · 2.1', 'mc:block/oak_log', [
        f('Planks, sticks', '2x2'), f('Slabs, stairs, fences, gates, doors, trapdoors, plates, buttons, signs, boats, shelves', 'T', need='×10 woods'),
        f('Hanging signs', need='+ chain'), f('Crafting table, chest, barrel, bowl, ladder, composter'),
        f('Bookshelf, lectern, chiseled bookshelf', need='+ book'), f('Beds', need='+ wool ×16'),
        f('Item frame, painting', need='+ leather / wool'), f('Charcoal', 'FG'),
        f('Wooden tools', st='cut'), f('Wooden spear', st='spec', need='still craftable'),
        f('Crimson and warped sets', st='starved', need='Nether only'),
    ]),
    ('STONE', 'Rock Pickaxe · 2.2', 'mc:block/cobblestone', [
        f('Stone, smooth stone', 'FG'), f('Stone, brick, deepslate, tuff, andesite, diorite, granite, sandstone, mud and resin families', 'SC', need='~275 cuts'),
        f('Lever, stone button and plate, grindstone'), f('Armor stand', need='+ smooth slab'),
        f('Stone tools, stone spear', st='spec', need='removed'), f('Cobblestone from 4 rocks', st='spec', need='removed'),
        f('Furnace, smoker, blast furnace', st='cut'), f('Brewing stand', st='cut'),
    ]),
    ('COAL', 'Rock Pickaxe (ore) · Forge (charcoal)', 'mc:item/coal', [
        f('Torch', '2x2'), f('Campfire, coal block'), f('Lantern, copper torch, copper lantern', need='+ nuggets'),
        f('Soul torch, lantern, campfire', st='cut'), f('Fire charge', st='cut'),
    ]),
    ('LEATHER', 'kills · 2.3', 'mc:item/leather', [
        f('Leather armour ×4, horse armour', need='dyeable'), f('Book, writable book', need='+ paper'),
        f('Bundle ×16', need='+ string'), f('Saddle', need='+ iron'),
        f('Harness ×16', st='starved', need='happy ghast removed'),
    ]),
    ('STRING', 'spiders · 2.3', 'mc:item/string', [
        f('Bow, crossbow, fishing rod, loom'), f('Wool', '2x2'), f('Scaffolding', need='+ bamboo'),
        f('Candle ×17', need='+ honeycomb'), f('Lead', need='+ slime; Ark: 5 fiber'),
    ]),
    ('WOOL', 'kill or shear sheep', 'mc:block/white_wool', [
        f('Carpets ×16'), f('Beds ×16'), f('Banners ×16', 'LM', need='patterns'), f('Painting'),
    ]),
    ('BONE · FEATHER · FLINT', 'kills · gravel', 'mc:item/bone', [
        f('Bone meal, bone block, white dye', '2x2'), f('Arrows', st='spec', need='sharp rock + feather'),
        f('Writable book', need='+ ink sac'), f('Flint and steel', need='+ iron'), f('Fletching table'),
        f('Spectral arrow', st='starved', need='glowstone'),
    ]),
    ('KERATIN', 'kills · 2.3', 'ark:item/keratin', [
        f('Keratin Spear', need='+ 2 stick, fiber'), f('Keratin armour ×4', need='8 armour for the set'),
        f('Flint Spear', st='cut', need='replaced'),
    ]),
    ('CLAY · SAND · GLASS', 'hand · Forge', 'mc:block/glass', [
        f('Bricks, flower pot, decorated pot', 'FG', need='sherds by brush'), f('Terracotta ×17, glazed ×16', 'FG'),
        f('Glass, panes, stained ×16, bottle', 'FG'), f('Sandstone families', 'SC'),
        f('Concrete powder ×16', need='+ gravel, dye; set by water'), f('Tinted glass', need='+ amethyst'),
        f('TNT, TNT minecart', st='starved', need='gunpowder is loot only'), f('Beacon', st='cut'),
    ]),
    ('COPPER', 'Forge · ore tier Q1', 'mc:item/copper_ingot', [
        f('Copper tools ×6 (axe, pickaxe, shovel, hoe, sword, spear)', need='becomes tier 2'),
        f('Copper armour ×4'), f('Lightning rod, spyglass, brush', need='+ amethyst / feather'),
        f('Blocks, cut, chiseled, grate, door, trapdoor, bars, chain, lantern, chest', 'SC', need='oxidise, wax'),
        f('Copper bulb ×8', need='Ark recipe: torch replaces blaze rod'),
        f('Copper golem statue', st='starved', need='golem removed'),
    ]),
    ('IRON', 'Forge · ore tier Q1', 'mc:item/iron_ingot', [
        f('Iron tools ×6, armour ×4'), f('Bucket, shears, flint and steel, compass, map, clock'),
        f('Anvil, smithing table, stonecutter, cauldron, hopper, crafter'),
        f('Minecarts, rails', need='+ gold for powered'), f('Door, trapdoor, bars, chain, lantern, heavy plate'),
        f('Shield, crossbow, tripwire hook, piston'),
        f('Furnace minecart', st='cut'), f('Iron golem', st='cut'),
    ]),
    ('GOLD', 'iron pickaxe · Forge', 'mc:item/gold_ingot', [
        f('Golden tools ×6, armour ×4'), f('Clock, powered rail, light plate'), f('Golden carrot, golden dandelion'),
        f('Golden apple, glistering melon', st='cut'),
    ]),
    ('REDSTONE', 'iron pickaxe', 'mc:item/redstone', [
        f('Redstone torch, repeater, lever, piston, sticky piston, dispenser, dropper, hopper'),
        f('Note block, target, rails, crafter, redstone block'),
        f('Comparator, observer, daylight detector', st='starved', need='nether quartz'),
        f('Redstone lamp', st='starved', need='glowstone: cleric trade only'), f('Sculk sensors', st='cut'),
    ]),
    ('DIAMOND · LAPIS · EMERALD', 'iron pickaxe (lapis: stone tier)', 'mc:item/diamond', [
        f('Diamond tools ×6, armour ×4, jukebox'), f('Lapis block, blue dye', need='no enchanting'),
        f('Emerald block, villager trades', need='filtered'), f('Enchanting table', st='cut'),
        f('Netherite gear ×12', st='cut'),
    ]),
    ('AMETHYST · OBSIDIAN', 'geodes · diamond pickaxe', 'mc:item/amethyst_shard', [
        f('Amethyst block, tinted glass, spyglass'), f('Obsidian', need='decoration only'),
        f('Nether portal, ender chest, beacon', st='cut'), f('Calibrated sculk sensor', st='cut'),
    ]),
    ('HONEY · SLIME', 'bees · pandas', 'mc:item/honeycomb', [
        f('Honey bottle, honey block, honeycomb block, beehive'), f('Waxed copper', need='all variants'),
        f('Slime block, sticky piston'), f('Magma cream', st='cut'),
    ]),
    ('DYES', 'flowers, plants, bone meal, ink, cocoa, lapis', 'mc:item/red_dye', [
        f('16 dyes', '2x2'), f('Wool, carpet, bed, banner, stained glass, terracotta, concrete, candle, bundle'),
        f('Leather armour, wolf armour', need='dyeing'), f('Shulker box ×17', st='cut'),
    ]),
    ('FOOD', 'farms, animals', 'mc:item/bread', [
        f('Bread, cake, cookie, pumpkin pie, sugar'), f('Mushroom, beetroot, rabbit, suspicious stew'),
        f('Cooked vanilla meats, baked potato, dried kelp', 'FI'),
    ]),
    ('NO SOURCE', 'Nether, End and monuments closed', 'mc:item/quartz', [
        f('Nether quartz', '', 'starved', 'Nether removed'), f('Glowstone', '', 'starved', 'cleric trade only'),
        f('Prismarine, sea lantern, sponge', '', 'starved', 'monuments removed'),
        f('Trident', '', 'starved', 'drowned removed'),
    ]),
    ("TOM'S STORAGE", 'pack mod (I04)', 'mc:block/barrel_side', [
        f('Trim, open crate, cable, proxy, filing cabinet, item filter, basic hopper, configurator'),
        f('Crafting terminal', need='+ storage terminal'),
        f('Storage terminal, level emitter', st='starved', need='comparator, glowstone'),
        f('Inventory connector, interface, cable connector, wireless terminals', st='starved', need='ender pearls'),
    ]),
]

# ---------------------------------------------------------------- cut list and decisions
CUTS = [
    ('Your spec removes', [
        'Wooden tools (already gone) and the vanilla <b>wooden spear</b>, which slipped through the filter',
        'Vanilla <b>stone tools</b> ×6, stone spear included',
        '<b>4 Rock → Cobblestone</b>',
        'Leaves → Stick (becomes Leaves → Twig)',
        'Bedroll from 4 Fiber + Leather (becomes 9 Fiber)',
    ]),
    ('Done on 2026-09-26', [
        '<b>Recovery Cache</b> removed: deaths drop items as usual',
        '<b>Flint Spear</b> replaced by the <b>Keratin Spear</b>',
        'Flint in arrows replaced by the <b>Sharp Rock</b>',
        'Clay removed from the <b>Primitive Forge</b> recipe',
    ]),
    ('Proposed with it', [
        '<b>Stone Knife</b> → Rock Sword; <b>Flint Knife</b> removed',
        '<b>Concentrated Sedative</b> → Narcotics',
        'The <b>starter kit</b> (bedroll, 2 bandages, 8 fiber, flint knife), if "starts with nothing" is literal',
    ]),
    ('Already removed by the theme (70 vanilla recipes)', [
        'Magic: enchanting table, brewing stand, potions, tipped arrows, golden apple, glistering melon',
        'Teleport and End: ender pearl, eye and chest, end rod, end crystal, shulker box, purpur',
        'Nether tiers: netherite ×15, respawn anchor, lodestone, soul torch, lantern and campfire, magma',
        'Trials and flight: mace, wind charge, flow template, fireworks, fire charge',
        'Furnace, smoker, blast furnace, furnace minecart; beacon, conduit, recovery compass',
        'Copper bulbs: re-added by Ark with a torch instead of the blaze rod',
    ]),
    ('Recipe stays, material has no source', [
        '<b>Nether quartz</b> → comparator, observer, daylight detector',
        '<b>Glowstone</b> (cleric trades only) → redstone lamp, spectral arrow',
        '<b>Ender pearls</b> → Tom\'s Storage connector, interface and wireless terminals',
        '<b>Gunpowder</b> (finite chest loot) → TNT',
        'Prismarine, sponge, trident, crimson and warped wood, 11 trim templates, harness, copper golem statue',
    ]),
]

DECISIONS = [
    ('Q1', 'Blocking', 'Rock tool mining tier',
     'The rock set uses the wooden tier today, which cannot harvest copper, iron or lapis ore. With stone '
     'tools removed nothing past the forge can be reached.',
     'Keep wood stats (speed, durability, damage) but let the rock pickaxe mine at stone level. Copper then '
     'becomes the second tool tier.'),
    ('Q2', 'Spec gap', 'The bedroll needs the 3×3 grid',
     '9 Fiber does not fit the 2×2 inventory, so the bedroll comes after the axe and the table.',
     'Accept it. Step 1 stays "gather", and the first night comes after the first log.'),
    ('Q3', 'Answer', 'Fiber in any slot',
     'Yes, this works: a custom shaped recipe type matches the tool pattern and accepts one Fiber in any empty '
     'slot. JEI shows it in one slot with a note.',
     'Use it for the whole rock set.'),
    ('Q4', 'Spec gap', 'Starter kit',
     'New players still get a bedroll, 2 bandages, 8 fiber and a flint knife, which contradicts "starts with '
     'nothing".',
     'Remove it, or reduce it to the Field Journal.'),
    ('Q5', 'Naming', 'Berry names',
     'The chart uses your names; the game has Narco-, Tinto-, Amar- and Azulberry.',
     'Rename them to Black-, Red-, Yellow- and Blueberry and keep the old ids so existing worlds load.'),
    ('Q6', 'Content', 'One rock set',
     'Stone Knife and Flint Knife overlap with the new set; the Keratin Spear is now the first weapon tier.',
     'The Stone Knife becomes the Rock Sword (London node moves) and the Flint Knife goes.'),
    ('Q7', 'Content', 'Sedative chain',
     'Blackberry → Sedative (2×2) → Narcotics (mortar) replaces Concentrated Sedative.',
     'Tranq Arrow takes a Sedative, Improved Tranq takes Narcotics, and Narcotraffic tracks Narcotics.'),
    ('Q8', 'Balance', 'Twig and rack ratios',
     'Twig → Stick and the rack\'s wood + sticks need numbers.',
     '2 Twig → 1 Stick; Drying Rack = 2 Log + 4 Stick.'),
    ('Q9', 'Blocking', 'No quartz',
     'Without the Nether there is no nether quartz: comparator, observer and daylight detector cannot be made.',
     'Add an Overworld quartz source (a deep ore or a geode drop), or give those three Ark recipes.'),
    ('Q10', 'Blocking', "Tom's Storage can't be built",
     'The inventory connector, interface and wireless terminals need ender pearls. The storage terminal needs '
     'a comparator and glowstone. None of these can be crafted, so no storage network works in survival.',
     'Override those recipes in Ark data, for example with diamond, redstone and copper.'),
    ('Q11', 'Balance', 'Gunpowder',
     'Creepers, ghasts and witches are gone, so TNT depends on finite chest loot.',
     'Accept it for now; a later mortar recipe could make it (charcoal + bone meal + sand).'),
    ('Q12', 'Content', 'Vanilla campfire',
     'The vanilla campfire (logs, sticks, coal) cooks like the Stone Fire and also completes the warmth node.',
     'Keep it as the coal-era option.'),
    ('Q13', 'Yours', 'Open design',
     'Blueberry effect, the Redberry medicine path and creature drops (blood, heart…).',
     'Left open, as in your spec.'),
]


# ---------------------------------------------------------------- icons
def load_icons():
    jar = zipfile.ZipFile(JAR) if JAR.is_file() else None  # fresh checkout: no vanilla sprites
    cache = {}

    def uri(spec):
        if spec in cache:
            return cache[spec]
        if not spec or spec == 'cube':
            cache[spec] = None
            return None
        ns, path = spec.split(':', 1)
        try:
            if ns == 'mc':
                if jar is None:
                    raise KeyError(path)
                img = Image.open(io.BytesIO(jar.read(f'assets/minecraft/textures/{path}.png')))
            else:
                img = Image.open(MOD_TEX / f'{path}.png')
        except (KeyError, FileNotFoundError):
            cache[spec] = None
            return None
        img = img.convert('RGBA')
        img = img.crop((0, 0, img.width, img.width))  # animated strips: first frame
        if path.endswith(('short_grass', 'oak_leaves')):
            tint = GRASS if 'grass' in path else FOLIAGE
            px = img.load()
            for x in range(img.width):
                for y in range(img.height):
                    r, g, b, a = px[x, y]
                    px[x, y] = (r * tint[0] // 255, g * tint[1] // 255, b * tint[2] // 255, a)
        if img.width != 16:
            img = img.resize((16, 16), Image.NEAREST)
        buf = io.BytesIO()
        img.save(buf, 'PNG', optimize=True)
        cache[spec] = 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()
        return cache[spec]

    items = {k: {'name': v[0], 'icon': uri(v[1]), 'cube': v[1] == 'cube', 'art': v[2], 'was': v[3]}
             for k, v in ITEMS.items()}
    glyphs = {k: uri(v) for k, v in NODE_ICONS.items()}
    for n in SPINE:
        if n['glyph'].startswith(('ark:', 'mc:')):
            glyphs[n['glyph']] = uri(n['glyph'])
    nets = {name: uri(icon) for name, _, icon, _ in NETS}
    return items, glyphs, nets


def removed_count():
    """Vanilla recipes the theme and the prehistoric filter drop (the mod does this at load time)."""
    text = POLICY.read_text(encoding='utf-8')
    block = text[text.index('REMOVED_ITEMS = List.of('):text.index('DISABLED_BLOCKS')]
    removed = {'minecraft:' + x for x in re.findall(r'"([a-z_]+)"', block)}
    removed |= {f'minecraft:wooden_{t}' for t in ('pickaxe', 'axe', 'shovel', 'hoe', 'sword')}
    removed |= {'minecraft:furnace', 'minecraft:smoker', 'minecraft:blast_furnace'}
    jar = zipfile.ZipFile(JAR)
    names = [n for n in jar.namelist() if n.startswith('data/minecraft/recipe/') and n.endswith('.json')]
    cut = 0
    for n in names:
        raw = jar.read(n).decode()
        if any(f'"{r}"' in raw for r in removed):
            cut += 1
    return len(names), cut


# ---------------------------------------------------------------- html
def notes_and_numbers():
    notes = []
    for n in SPINE:
        for r in n['rows']:
            if r['note']:
                notes.append(r['note'])
                r['noteNo'] = len(notes)
    return notes


def netlist_html(net_icons):
    out = []
    for name, src, _, fams in NETS:
        icon = net_icons.get(name)
        img = f'<img src="{icon}" alt="" width="16" height="16">' if icon else ''
        chips = ''.join(
            f'<li class="fam st-{x["st"]}">'
            + (f'<span class="tag">{html.escape(x["tag"])}</span>' if x['tag'] else '')
            + f'<span class="lbl">{html.escape(x["label"])}</span>'
            + (f'<span class="need">{html.escape(x["need"])}</span>' if x['need'] else '') + '</li>'
            for x in fams)
        out.append(f'<div class="net"><div class="netname"><span class="flag">{img}{html.escape(name)}</span>'
                   f'<span class="src">{html.escape(src)}</span></div><ul class="fams">{chips}</ul></div>')
    return '\n'.join(out)


def cuts_html():
    return '\n'.join(f'<section class="cut"><h3>{html.escape(t)}</h3><ul>'
                     + ''.join(f'<li>{x}</li>' for x in items) + '</ul></section>' for t, items in CUTS)


def decisions_html():
    return '\n'.join(
        f'<li class="q" id="{q}"><div class="qhead"><span class="qid">{q}</span>'
        f'<span class="qtag t-{tag.lower().replace(" ", "-")}">{html.escape(tag)}</span>'
        f'<h3>{html.escape(title)}</h3></div><p>{html.escape(body)}</p>'
        f'<p class="rec"><span>Recommendation</span>{html.escape(rec)}</p></li>'
        for q, tag, title, body, rec in DECISIONS)


TITLE_BLOCK = {
    'title': 'Recipe Gates · workstation spine', 'project': 'Ark: Survival Returns', 'rev': 'Draft A',
    'source': 'Your spec · mod data · vanilla 26.1.2', 'date': '2026-09-26',
    'notes': ['Pin numbers are the steps of the recipe-gate spec (F12). Unnumbered pins are in the game or proposed.',
              'Stations below the "Vanilla stations" line need forge metals; the Age gate ends the Prehistoric.'],
}

LEGEND = [
    ('Row status', [
        ('<rect x="1" y="2" width="42" height="16" rx="2" fill="var(--sp-chip)" stroke="var(--sp-you)" stroke-width="2"/>', 'Your spec'),
        ('<rect x="1" y="2" width="42" height="16" rx="2" fill="var(--sp-chip)" stroke="var(--sp-prop)" stroke-width="1.6" stroke-dasharray="5 3"/>', 'Proposed here'),
        ('<rect x="1" y="2" width="42" height="16" rx="2" fill="var(--sp-chip)" stroke="var(--sp-now)"/>', 'In the game now, unchanged'),
        ('<rect x="1" y="2" width="42" height="16" rx="2" fill="var(--sp-chip)" stroke="var(--sp-change)" stroke-width="2"/>', 'In the game, changes to match the spec'),
        ('<rect x="1" y="2" width="42" height="16" rx="2" fill="var(--sp-chip)" stroke="var(--sp-cut)" stroke-dasharray="2 2"/>'
         '<line x1="4" x2="40" y1="10" y2="10" stroke="var(--sp-cut)" stroke-width="1.4"/>', 'Removed'),
        ('<rect x="1" y="2" width="42" height="16" rx="2" fill="var(--sp-chip)" stroke="var(--sp-cut)" stroke-width="2" stroke-dasharray=".5 3.5" stroke-linecap="round"/>', 'Blocked until a decision'),
        ('<rect x="1" y="2" width="42" height="16" rx="2" fill="var(--sp-chip)" stroke="var(--sp-tbd)" stroke-width="2" stroke-dasharray=".5 3.5" stroke-linecap="round"/>', 'Yours to decide'),
    ]),
    ('Symbols', [
        ('<rect x="1" y="2" width="42" height="16" rx="2" fill="none" stroke="var(--sp-ink2)" stroke-dasharray="3 2.5"/>', 'World source: a block or creature, gathered'),
        ('<path d="M1 2h32l10 8l-10 8h-32z" fill="var(--sp-chip)" stroke="var(--sp-ink)" stroke-width="1.2"/>', 'Net label: made on another row (hover to trace)'),
        ('<rect x="1" y="2" width="42" height="16" rx="2" fill="var(--sp-chip)" stroke="var(--sp-ink2)"/>', 'Output of this row'),
        ('<rect x="14" y="2" width="16" height="16" fill="url(#sp-legend-hatch)" stroke="var(--sp-ink2)" stroke-width=".6"/>', 'Needs a new sprite'),
        ('<rect x="14" y="2" width="16" height="16" fill="var(--sp-rule)"/><path d="M24 1h7v7z" fill="var(--sp-change)"/>', 'Drawn with a borrowed sprite today'),
        ('<path class="sp-cube" d="M22 3l7 3.5v8L22 18l-7-3.5v-8zM15 6.5l7 3.5l7-3.5M22 10v8"/>', 'Placed block with its own 3D model'),
    ]),
]


def legend_html():
    hatch = ('<svg width="0" height="0" style="position:absolute" aria-hidden="true"><defs><pattern id="sp-legend-hatch" width="4" '
             'height="4" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="4" height="4" fill="var(--sp-chip)"/>'
             '<line x1="0" y1="0" x2="0" y2="4" stroke="var(--sp-change)" stroke-width="1.5"/></pattern></defs></svg>')
    groups = ''.join(f'<div><h4>{html.escape(title)}</h4><ul>'
                     + ''.join(f'<li><svg width="44" height="20" aria-hidden="true">{mark}</svg>{html.escape(text)}</li>' for mark, text in rows)
                     + '</ul></div>' for title, rows in LEGEND)
    return hatch + groups


def spine_payload():
    """Everything the shared renderer (spine.js) needs, plus the numbered notes."""
    items, glyphs, _ = load_icons()
    notes = notes_and_numbers()
    return {'spine': SPINE, 'items': items, 'glyphs': glyphs, 'notes': notes, 'titleBlock': TITLE_BLOCK}, notes


def notes_html(notes):
    return '\n'.join(f'<li value="{k}">{html.escape(t)}</li>' for k, t in enumerate(notes, 1))


def spine_assets():
    """The shared stylesheet and renderer, inlined by both pages."""
    return (HERE / 'spine.css').read_text(encoding='utf-8'), (HERE / 'spine.js').read_text(encoding='utf-8')



def main():
    data, notes = spine_payload()
    _, _, net_icons = load_icons()
    total, cut = removed_count()
    css, js = spine_assets()
    page = TEMPLATE
    page = page.replace('/*SPINECSS*/', css).replace('/*SPINEJS*/', js)
    page = page.replace('/*DATA*/null', json.dumps(data, ensure_ascii=False, separators=(',', ':')))
    page = page.replace('<!--LEGEND-->', legend_html())
    page = page.replace('<!--NOTES-->', notes_html(notes))
    page = page.replace('<!--NETLIST-->', netlist_html(net_icons))
    page = page.replace('<!--CUTS-->', cuts_html())
    page = page.replace('<!--DECISIONS-->', decisions_html())
    page = page.replace('{{TOTAL}}', f'{total:,}').replace('{{CUT}}', str(cut))
    page = page.replace('{{STATIONS}}', str(sum(1 for n in SPINE if n['kind'] == 'station' and n['st'] != 'cut')))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(page, encoding='utf-8')
    items = data['items']
    missing = sorted(k for k, v in items.items() if not v['icon'] and not v['cube'] and v['art'] != 'new')
    print(f'{OUT} ({OUT.stat().st_size // 1024} KB), {len(notes)} notes, {total} vanilla recipes, {cut} removed')
    if missing:
        print('no icon:', ', '.join(missing))


TEMPLATE = (HERE / 'item_flow_template.html').read_text(encoding='utf-8')

if __name__ == '__main__':
    main()
