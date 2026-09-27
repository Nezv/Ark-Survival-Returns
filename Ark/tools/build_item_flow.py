"""Recipe gates: the item progression spec (Prehistoric + vanilla) drawn as a schematic page.

Sheet 1 is the dependency map: every item once, in the first column (crafting steps from bare hands) where
it can be made, grouped under the station or tool that makes it, with a line from each ingredient. Hovering an
item lights its whole ancestry and everything it unlocks (tools/spine.js). Sheet 2 is the vanilla netlist: what each material unlocks. Then the cut list and the open
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
    'w_sand': ('Sand', 'mc:block/sand', None, None),
    'w_sugar_cane': ('Sugar cane', 'mc:item/sugar_cane', None, None),
    'w_flowers': ('Flowers', 'mc:block/poppy', None, None),
    'w_farm': ('Village crops', 'mc:item/carrot', None, None),
    'w_redstone_ore': ('Redstone ore', 'mc:block/redstone_ore', None, None),
    'w_creature': ('Dinosaur', 'ark:item/parasaur_spawn_egg', None, None),
    'w_animal': ('Vanilla animal', 'mc:item/cow_spawn_egg', None, None),
    'w_spider': ('Spider', 'mc:item/spider_spawn_egg', None, None),
    'w_allosaurus': ('Wild Allosaurus', 'ark:item/allosaurus_spawn_egg', None, None),
    'w_horned': ('Horned, plated or beaked', 'ark:item/triceratops_spawn_egg', None, None),
    'w_goat': ('Goat', 'mc:item/goat_spawn_egg', None, None),
    # prehistoric
    'rock': ('Rock', 'ark:item/rock', 'standin', None),
    'blackberry': ('Blackberry', 'ark:item/narcoberry', None, None),
    'redberry': ('Redberry', 'ark:item/tintoberry', None, None),
    'yellowberry': ('Yellowberry', 'ark:item/amarberry', None, None),
    'blueberry': ('Blueberry', 'ark:item/azulberry', None, None),
    'berry': ('Any berry', 'ark:item/tintoberry', None, None),
    'fiber': ('Fiber', 'mc:item/wheat', 'standin', 'Plant Fiber'),
    'twig': ('Twig', None, 'new', None),
    'stick': ('Twig', 'mc:item/stick', None, 'Stick'),
    'flint': ('Flint', 'mc:item/flint', None, None),
    'clay_ball': ('Clay Ball', 'mc:item/clay_ball', None, None),
    'sedative': ('Sedative', None, 'new', None),
    'narcotics': ('Narcotics', 'ark:item/narcotics', None, None),
    'medicine': ('Medicine', None, 'new', None),
    'blue_tbd': ('Blueberry product', None, 'new', None),
    'rock_axe': ('Rock Axe', 'ark:item/stone_hatchet', None, None),
    'rock_pickaxe': ('Rock Pickaxe', 'ark:item/rock_pickaxe', None, None),
    'rock_sword': ('Rock Sword', 'ark:item/rock_sword', None, None),
    'rock_shovel': ('Rock Shovel', 'ark:item/rock_shovel', None, None),
    'rock_hoe': ('Rock Hoe', 'ark:item/rock_hoe', None, None),
    'fire_starter': ('Fire Starter', 'ark:item/fire_starter', None, None),
    'bandage': ('Fiber Bandage', 'mc:item/paper', 'standin', None),
    'string': ('String', 'mc:item/string', None, None),
    'log': ('Log', 'mc:block/oak_log', None, None),
    'planks': ('Planks', 'mc:block/oak_planks', None, None),
    'working_station': ('Working Station', 'cube', None, 'Crafting Table'),
    'bedroll': ('Primitive Bedroll', 'cube', None, None),
    'cobblestone': ('Cobblestone', 'mc:block/cobblestone', None, None),
    'stone_tools': ('Stone tools ×6', 'mc:item/stone_pickaxe', None, None),
    'wood_spear': ('Wooden Spear', 'mc:item/wooden_spear', None, None),
    'stone_fire': ('Stone Fire', 'cube', None, None),
    'drying_rack': ('Drying Rack', 'cube', None, None),
    'mortar': ('Mortar & Pestle', 'cube', None, None),
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
    'resin': ('Resin Clump', 'mc:item/resin_clump', None, None),
    'trough': ('Trough', 'cube', None, None),
    'storage_crate': ('Storage Crate', 'cube', None, 'Chest'),
    'coal': ('Coal', 'mc:item/coal', None, None),
    'torch': ('Torch', 'mc:block/torch', None, None),
    'campfire': ('Campfire', 'mc:item/campfire', None, None),
    'raw_meat': ('Raw meat ×7 kinds', 'ark:item/raw_herbivore_meat', None, None),
    'cooked_meat': ('Cooked meat ×7', 'ark:item/cooked_herbivore_meat', None, None),
    'wool': ('Wool', 'mc:block/white_wool', None, None),
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
    'sand': ('Sand', 'mc:block/sand', None, None),
    'glass': ('Glass', 'mc:block/glass', None, None),
    'brick': ('Brick', 'mc:item/brick', None, None),
    'stone': ('Stone', 'mc:block/stone', None, None),
    'charcoal': ('Charcoal', 'mc:item/charcoal', None, None),
    'smelt_rest': ('Every other non-food smelt', 'mc:item/iron_nugget', None, None),
    'smelt_in': ('Terracotta, sponge, stone variants…', 'mc:block/terracotta', None, None),
    'gravel': ('Gravel', 'mc:block/gravel', None, None),
    'gunpowder': ('Gunpowder', 'mc:item/gunpowder', None, None),
    'flowers': ('Flowers', 'mc:block/poppy', None, None),
    'dyes': ('Dyes', 'mc:item/red_dye', None, None),
    'sugar_cane': ('Sugar Cane', 'mc:item/sugar_cane', None, None),
    'paper': ('Paper', 'mc:item/paper', None, None),
    'smooth_stone': ('Smooth Stone', 'mc:block/smooth_stone', None, None),
    'stone_slab': ('Stone Slab', 'mc:block/stone', None, None),
    'wood_slab': ('Wooden Slab', 'mc:block/oak_planks', None, None),
    'glass_bottle': ('Glass Bottle', 'mc:item/glass_bottle', None, None),
    'redstone': ('Redstone', 'mc:item/redstone', None, None),
    'dropper': ('Dropper', 'mc:block/dropper_front', None, None),
    'ore_block': ('Ore block (Silk Touch)', 'mc:block/iron_ore', None, None),
    'herbal_bandage': ('Herbal Bandage', 'mc:item/paper', 'standin', None),
    'healing_mixture': ('Healing Mixture', 'mc:item/honey_bottle', 'standin', None),
    'medicine_bench': ('Medicine Bench', 'cube', None, None),
    'crusher': ('Crusher', 'cube', None, None),
    'smithing_table': ('Smithing Table', 'cube', None, None),
    'stonecutter': ('Stonecutter', 'mc:block/stonecutter_top', None, None),
    'anvil': ('Anvil', 'mc:block/anvil', None, None),
    'grindstone': ('Grindstone', 'mc:block/grindstone_side', None, None),
    'composter': ('Composter', 'mc:block/composter_side', None, None),
    'crafter': ('Crafter', 'mc:block/crafter_north', None, None),
    'furnace': ('Furnace', 'mc:block/furnace_front', None, None),
    'brewing_stand': ('Brewing Stand', 'mc:item/brewing_stand', None, None),
    'enchanting_table': ('Enchanting Table', 'mc:block/enchanting_table_top', None, None),
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
    'plants': ('Seeds, leaves, crops', 'mc:item/wheat_seeds', None, None),
    'bone_meal': ('Bone Meal', 'mc:item/bone_meal', None, None),
    'crafter_in': ('Ingredients + redstone pulse', 'mc:item/redstone', None, None),
    'crafter_out': ('Any Working Station result', 'mc:block/crafter_north', None, None),
    'potion_in': ('Blaze powder + nether wart', 'mc:item/blaze_powder', None, None),
    'potions': ('Potions', 'mc:item/potion', None, None),
    'ench_in': ('Item + lapis + levels', 'mc:item/lapis_lazuli', None, None),
    'enchanted': ('Enchanted item', 'mc:item/enchanted_book', None, None),
    'fuel_food': ('Ore or food + fuel', 'mc:item/coal', None, None),
    'smelted': ('Smelted or cooked item', 'mc:item/iron_ingot', None, None),
}

NODE_ICONS = {  # extra textures used only as node glyphs
    'g_stonecutter': 'mc:block/stonecutter_saw', 'g_anvil': 'mc:block/anvil', 'g_grindstone': 'mc:block/grindstone_side',
    'g_smithing': 'mc:block/smithing_table_front', 'g_fletching': 'mc:block/fletching_table_front',
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


def node(key, kind, name, sub, glyph, rows, st=None, item=None):
    """A station or tool gate. item: the item that is the station (its tier gates every row here)."""
    return {'key': key, 'kind': kind, 'name': name, 'sub': sub, 'glyph': glyph, 'rows': rows, 'st': st, 'item': item}


W = lambda item, **k: dict(i(item, **k), world=True)  # a world source, not a crafted item

# "Any of" inputs: the renderer draws them as one chip fed by each member.
GROUPS = {
    'berry': ['blackberry', 'redberry', 'yellowberry', 'blueberry'],
    'meat': ['raw_meat', 'cooked_meat'],
    'raw_or_fish': ['raw_meat', 'vanilla_raw'],
    'dried_food': ['dried_1', 'dried_2', 'dried_3', 'dried_ration'],
}

SPINE = [
    node('hands', 'gate', 'Bare hands', '1 · starts with nothing', 'empty', [
        row('1.1', 'you', [W('w_loose_rock')], [i('rock')], 'pick up',
            'Loose rocks lie on the ground in every Overworld biome (F02). A rock is not stone: it never turns '
            'into cobblestone. Needs a dedicated sprite.'),
        row('1.2', 'you', [W('w_grass')], [i('blackberry', val='+1 food · torpor'), i('redberry', val='+1 heart · +1 food'),
                                           i('yellowberry', val='+3 food'), i('blueberry', val='+1 food · heals tames')], 'break, 35%',
            'In game today: Narcoberry, Tintoberry, Amarberry and Azulberry. A grass block drops a berry 35% of the '
            'time: red, yellow and blue 30% each of that, black 10%. The food values are yours; the red berry '
            'starts the medicine path.'),
        row('1.3', 'you', [W('w_grass')], [i('fiber', ch='45%')], 'break, 45%',
            'Fiber is the primitive rope and goes into every rock tool. Today it is drawn with the wheat sprite.'),
        row('1.4', 'now', [W('w_leaves')], [i('stick')], 'break',
            'The vanilla Stick is renamed Twig (Q8): leaves drop it 20% of the time, and planks still make it later.'),
        row('', 'now', [W('w_gravel')], [i('flint', ch='10%'), i('gravel')], 'dig'),
        row('', 'now', [W('w_clay')], [i('clay_ball', 4)], 'dig'),
        row('', 'now', [W('w_sand')], [i('sand')], 'dig'),
        row('', 'now', [W('w_sugar_cane')], [i('sugar_cane')], 'break'),
        row('', 'now', [W('w_flowers')], [i('flowers')], 'pick'),
        row('', 'now', [W('w_farm')], [i('carrot')], 'harvest'),
    ]),
    node('inv', 'station', 'Inventory', '2×2 grid · always open', 'grid2', [
        row('2.1', 'now', [i('rock'), i('stick'), i('fiber')], [i('rock_axe', val='wood stats, stone tier')], 'shapeless',
            'The Rock Axe (old id stone_hatchet) fits the 2×2 grid: logs need an axe, and the Working Station needs logs.'),
        row('', 'now', [i('rock', 2)], [i('sharp_rock')], 'shapeless',
            'Knapping: one rock struck on another. The sharp flake tips arrows in place of flint.'),
        row('', 'now', [i('stick', 2), i('fiber')], [i('fire_starter')], 'shapeless'),
        row('', 'now', [i('fiber', 3), i('string')], [i('bandage', 2)], 'shapeless'),
        row('', 'now', [i('bone')], [i('bone_meal', 3)], 'vanilla'),
        row('', 'now', [i('flowers')], [i('dyes')], 'vanilla'),
        row('2.1', 'now', [i('log')], [i('planks', 4)], 'vanilla'),
        row('2.1', 'now', [i('planks', 2)], [i('stick', 4)], 'vanilla'),
        row('2.1', 'now', [i('planks', 2), i('log', 2)], [i('working_station')], 'shaped',
            'The Working Station replaces the crafting table (F14): two planks over two logs, so it needs the '
            'axe. Vanilla tables that turn up in an inventory become Working Stations.'),
    ]),
    node('axe', 'gate', 'Rock Axe', '2.1 · chops logs', 'g_axe', [
        row('2.1', 'you', [W('w_log')], [i('log', val='10 Overworld woods')], 'chop',
            'Without an axe a log breaks at a quarter speed and drops nothing (in game). Crimson and warped '
            'wood are Nether-only, so 10 kinds remain.'),
    ], item='rock_axe'),
    node('table', 'station', 'Working Station', '3×3 grid · replaces the crafting table', 'grid3', [
        row('1.3', 'now', [i('fiber', 9)], [i('bedroll')], 'shaped',
            'Nine fiber needs the 3×3 grid, so the bedroll comes right after the Working Station (Q2).'),
        row('2.2', 'now', [i('rock', 3), i('stick', 2), i('fiber')], [i('rock_pickaxe', val='wood stats, stone tier')],
            'pickaxe + fiber', 'The vanilla pickaxe pattern with one Fiber in any free slot (Q3). It mines what a stone '
            'pickaxe mines, iron ore included, with wooden durability and speed (Q1).'),
        row('2.3', 'now', [i('rock', 2), i('stick'), i('fiber')], [i('rock_sword', val='wood stats')],
            'sword + fiber', 'The Stone Knife is gone (Q6); crafting any rock tool or the flint knife completes London.'),
        row('', 'now', [i('rock'), i('stick', 2), i('fiber')], [i('rock_shovel', val='wood stats')], 'shovel + fiber'),
        row('', 'now', [i('rock', 2), i('stick', 2), i('fiber')], [i('rock_hoe', val='wood stats')], 'hoe + fiber'),
        row('', 'cut', [i('rock', 4)], [i('cobblestone')], '2×2',
            'Removed by your spec: cobblestone only comes from mining stone with the rock pickaxe.'),
        row('', 'cut', [i('cobblestone', 3), i('stick', 2)], [i('stone_tools')], 'vanilla',
            'Stone pickaxe, axe, shovel, hoe, sword and spear. Wooden tools are already removed.'),
        row('', 'cut', [i('planks'), i('stick')], [i('wood_spear')], 'vanilla', 'Removed with the wooden tools.'),
        row('', 'now', [i('rock', 5), i('stick')], [i('stone_fire')], 'shaped'),
        row('2.3.1', 'change', [i('log', 2), i('stick', 4)], [i('drying_rack')], 'shaped',
            'You asked for wood and sticks; the 2 logs + 4 sticks split is a proposal. Today: 4 Sticks + 1 Fiber.'),
        row('', 'now', [i('cobblestone', 3), i('rock')], [i('mortar')], 'shaped', 'A stone bowl and a rock pestle (B02).'),
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
        row('', 'now', [i('flint'), i('stick'), i('fiber')], [i('flint_knife')], 'shapeless',
            'The flint knife stays, and it is the whole starter kit (Q4, Q6).'),
        row('', 'now', [i('stick', 3), i('string', 3)], [i('bow')], 'vanilla'),
        row('', 'change', [i('sharp_rock'), i('stick'), i('feather')], [i('arrow', 4)], 'shaped',
            'The vanilla arrow recipe now takes a Sharp Rock; flint no longer tips arrows.'),
        row('', 'now', [i('arrow', 4), i('narcotics'), i('bone')], [i('tranq_arrow', 4)], 'shapeless',
            'Narcotics tip the tranquilizer arrow (Q7).'),
        row('', 'cut', [i('tranq_arrow', 4), i('conc_sedative')], [i('improved_tranq', 4)], 'shapeless',
            'Waits for the Bronze Age with the Concentrated Sedative (Q7).'),
        row('', 'now', [i('fiber', 5)], [i('lead')], 'shaped'),
        row('', 'now', [i('leather', 3), i('fiber', 2)], [i('pack_harness')], 'shapeless'),
        row('', 'now', [i('pack_harness'), i('leather'), i('flint'), i('fiber', 2)], [i('reinforced_harness')], 'shapeless'),
        row('', 'now', [i('sugar_cane', 3)], [i('paper', 3)], 'vanilla'),
        row('', 'now', [i('paper', 3), i('leather')], [i('book')], 'vanilla'),
        row('', 'now', [i('book'), i('leather', 2)], [i('field_journal')], 'shapeless'),
        row('', 'now', [i('log', ch='spruce'), i('flint')], [i('resin', 2)], 'shapeless'),
        row('', 'now', [i('planks', 5), i('resin'), i('fiber')], [i('trough')], 'shaped'),
        row('', 'now', [i('planks', 8)], [i('storage_crate')], 'shaped',
            'The Storage Crate replaces every wooden chest (F14): 27 slots, and neighbouring crates join into '
            'one look while each keeps its own slots, so Tom\'s Storage, hoppers and tame cargo never count '
            'twice.'),
        row('', 'now', [i('coal'), i('stick')], [i('torch', 4)], 'vanilla'),
        row('', 'cut', [i('log', 3), i('stick', 3), i('coal')], [i('campfire')], 'vanilla',
            'Removed: the Stone Fire is its redesign, with more features, not a parallel (Q12).'),
        row('', 'now', [i('planks', 3)], [i('wood_slab', 6)], 'vanilla'),
        row('', 'now', [i('stone', 3)], [i('stone_slab', 6)], 'vanilla'),
        row('', 'now', [i('glass', 3)], [i('glass_bottle', 3)], 'vanilla'),
        row('', 'block', [i('ingots', 2), i('glass_bottle'), i('planks', 3), i('log', 2)], [i('medicine_bench')], 'shaped',
            'Iron Age (Q15): the bench needs iron, and its medicine is still to be designed.'),
        row('', 'now', [i('log', 2), i('grindstone'), i('cobblestone', 5)], [i('crusher')], 'shaped',
            'F14. Unpowered: a flywheel turns while it grinds.'),
        row('', 'now', [i('stick', 2), i('stone_slab'), i('planks', 2)], [i('grindstone')], 'vanilla'),
        row('', 'now', [i('wood_slab', 7)], [i('composter')], 'vanilla'),
        row('', 'block', [i('stone', 3), i('ingots')], [i('stonecutter')], 'vanilla'),
        row('', 'block', [i('ingots', 31)], [i('anvil')], 'vanilla', 'Three iron blocks and four ingots.'),
        row('', 'block', [i('ingots', 2), i('smooth_stone', 2), i('planks', 2)], [i('smithing_table')], 'shaped',
            'Ark\'s own Smithing Table replaces the vanilla one (F14).'),
        row('', 'block', [i('cobblestone', 7), i('redstone')], [i('dropper')], 'vanilla'),
        row('', 'block', [i('ingots', 5), i('working_station'), i('redstone', 2), i('dropper')], [i('crafter')], 'vanilla'),
    ], item='working_station'),
    node('pick', 'gate', 'Rock Pickaxe', '2.2 · mines stone', 'g_pickaxe', [
        row('2.2', 'you', [W('w_stone')], [i('cobblestone')], 'mine'),
        row('', 'now', [W('w_coal_ore')], [i('coal')], 'mine'),
        row('', 'now', [W('w_metal_ore')], [i('raw_ores')], 'mine', 'The rock pickaxe mines at stone tier: copper, iron and lapis (Q1).'),
        row('', 'block', [W('w_redstone_ore')], [i('redstone')], 'mine',
            'Redstone ore needs an iron pickaxe, so it waits on the metals too (Q1).'),
    ], item='rock_pickaxe'),
    node('sword', 'gate', 'Rock Sword', '2.3 · kills', 'g_sword', [
        row('2.3', 'you', [W('w_creature')], [i('raw_meat')], 'kill',
            'Seven meat families by body plan (F05); big creatures add prime cuts.'),
        row('', 'now', [W('w_creature')], [i('leather'), i('bone'), i('feather')], 'kill',
            'Leather scales with the creature\'s health; birds and sea creatures give no hide.'),
        row('2.3.2', 'tbd', [W('w_creature')], [i('creature_tbd')], 'kill', 'Yours to decide: blood, heart and other creature drops.'),
        row('', 'now', [W('w_horned')], [i('keratin', val='Trike 2-4 · Anky 1-3')], 'kill',
            'Horns, antlers, plates and big beaks: Triceratops, Ankylosaurus, Megalocerus, Carnotaurus, '
            'Ceratosaurus, Unicorn, Therizinosaurus, Argentavis, Terrorbird, Pegomastax and Lystrosaurus. '
            'Stegosaurus is listed for when it joins the roster.'),
        row('', 'now', [W('w_goat')], [i('keratin', ch='50%')], 'kill'),
        row('', 'now', [W('w_allosaurus')], [i('allosaur_heart')], 'kill'),
        row('', 'now', [W('w_spider')], [i('string')], 'kill'),
        row('', 'now', [W('w_animal')], [i('vanilla_raw'), i('wool'), i('leather')], 'kill',
            'Cows, pigs, sheep, chickens and the rest keep their drops; they also drop bones now that skeletons '
            'are gone.'),
    ], item='rock_sword'),
    node('fire', 'station', 'Stone Fire', 'rocks · lit with the Fire Starter', 'cube', [
        row('2.3', 'now', [i('raw_meat')], [i('cooked_meat')], '4-item spit'),
        row('', 'now', [i('vanilla_raw')], [i('vanilla_cooked')], '4-item spit'),
        row('', 'now', [i('stick')], [i('torch')], 'hold to flame', 'The Prometheus node.'),
    ], item='stone_fire'),
    node('rack', 'station', 'Drying Rack', 'two blocks tall', 'cube', [
        row('2.3.1', 'you', [i('raw_or_fish')], [i('dried_1'), i('dried_2', val='1 day · haste'),
                                                 i('dried_3', val='3 days · strength')], 'hang',
            'Left hanging, Dried Meat I cures into II and III (F04). III also gives regeneration.'),
        row('', 'now', [i('berry')], [i('dried_ration')], 'hang'),
    ], item='drying_rack'),
    node('pot', 'station', 'Cooking Pot', 'sits on a lit Stone Fire', 'cube', [
        row('', 'now', [i('dried_food', 2), i('meat'), i('carrot')], [i('hearty_stew', val='regeneration')], '4 slots'),
        row('', 'now', [i('dried_food', 2), i('berry', 2)], [i('trail_mix', val='speed')], '4 slots'),
    ], item='cooking_pot'),
    node('medbench', 'station', 'Medicine Bench', 'Iron Age · medicine to be designed', 'cube', [
        row('', 'cut', [i('blackberry', 4), i('fiber')], [i('conc_sedative')], 'shapeless',
            'The Concentrated Sedative waits for the Bronze Age (Q7); nothing makes it now.'),
    ], item='medicine_bench'),
    node('mortar', 'station', 'Mortar & Pestle', 'grinds herbs · makes nothing else', 'cube', [
        row('1.2.1', 'now', [i('blackberry', 4)], [i('narcotics', 4)], 'grind', 'Blackberries grind straight into Narcotics (Q7).'),
        row('', 'now', [i('bandage', 2), i('blueberry'), i('yellowberry'), i('fiber')],
            [i('herbal_bandage', 2, val='double heal · regeneration')], 'mix', 'The herbal remedies moved here from the Medicine Bench (Q15).'),
        row('', 'now', [i('glass_bottle'), i('blueberry', 2), i('redberry'), i('fiber')],
            [i('healing_mixture', val='regeneration II · cures poison')], 'mix'),
        row('1.2.2', 'tbd', [i('redberry')], [i('medicine')], 'grind', 'More medicine from the Redberry path, to be decided.'),
    ], item='mortar'),
    node('forge', 'station', 'Primitive Forge', 'two blocks · stone bloomery', 'cube', [
        row('', 'now', [i('raw_ores')], [i('ingots')], 'smelt', 'Ore tier: see Q1.'),
        row('', 'now', [i('sand')], [i('glass')], 'smelt'),
        row('', 'now', [i('clay_ball')], [i('brick')], 'smelt'),
        row('', 'now', [i('cobblestone')], [i('stone')], 'smelt'),
        row('', 'now', [i('stone')], [i('smooth_stone')], 'smelt'),
        row('', 'now', [i('log')], [i('charcoal')], 'smelt'),
        row('', 'now', [i('smelt_in')], [i('smelt_rest')], 'smelt',
            'Every non-food smelting recipe, modded ones included. Food goes to the Stone Fire.'),
    ], item='forge'),
    node('crusher', 'station', 'Crusher', 'unpowered · flywheel and rollers', 'cube', [
        row('', 'now', [i('cobblestone')], [i('gravel')], 'crush', 'No ore doubling (Q14) and no gunpowder until the Bronze Age (Q11).'),
        row('', 'now', [i('gravel')], [i('sand')], 'crush'),
        row('', 'now', [i('bone')], [i('bone_meal', 5)], 'crush'),
        row('', 'now', [i('wool')], [i('string', 4)], 'crush'),
        row('', 'now', [i('flowers')], [i('dyes', 2)], 'crush'),
    ], item='crusher'),
    node('stonecutter', 'station', 'Stonecutter', '3 Stone + Iron ingot', 'g_stonecutter', [
        row('', 'now', [i('stone_family')], [i('stone_variants')], 'cut'),
    ], item='stonecutter'),
    node('anvil', 'station', 'Anvil', '3 Iron blocks + 4 Iron', 'g_anvil', [
        row('', 'now', [i('damaged')], [i('repaired')], 'repair'),
        row('', 'now', [i('name_tag')], [i('renamed')], 'rename', 'No enchanting, so no book merging.'),
    ], item='anvil'),
    node('grindstone', 'station', 'Grindstone', '2 Stick + Stone slab + 2 Planks', 'g_grindstone', [
        row('', 'now', [i('two_damaged')], [i('repaired')], 'combine', 'Only repairs: there are no enchantments to strip.'),
    ], item='grindstone'),
    node('smithing', 'station', 'Smithing Table', 'Ark model · replaces the vanilla table', 'cube', [
        row('', 'now', [i('trim_in')], [i('trimmed')], 'trim',
            '7 of 18 trim templates can still be found (coast, dune, wild and the four trail-ruin ones); the '
            'other 11 come from removed structures.'),
        row('', 'cut', [i('netherite_up')], [i('netherite_up')], 'upgrade'),
    ], item='smithing_table'),
    node('composter', 'station', 'Composter', '7 Wooden slabs', 'g_composter', [
        row('', 'now', [i('plants')], [i('bone_meal')], 'compost'),
    ], item='composter'),
    node('crafter', 'station', 'Crafter', '5 Iron + Station + 2 Redstone + Dropper', 'g_crafter', [
        row('', 'now', [i('crafter_in')], [i('crafter_out')], 'auto-craft'),
    ], item='crafter'),
    node('furnaces', 'station', 'Furnace, Smoker, Blast Furnace', 'removed · the fire and forge replace them', 'g_furnace', [
        row('', 'cut', [i('fuel_food')], [i('smelted')], 'smelt'),
    ], st='cut', item='furnace'),
    node('brewing', 'station', 'Brewing Stand', 'removed by the theme', 'g_brewing', [
        row('', 'cut', [i('potion_in')], [i('potions')], 'brew'),
    ], st='cut', item='brewing_stand'),
    node('enchanting', 'station', 'Enchanting Table', 'removed by the theme', 'g_enchanting', [
        row('', 'cut', [i('ench_in')], [i('enchanted')], 'enchant'),
    ], st='cut', item='enchanting_table'),
    node('guardian', 'gate', 'First Guardian', 'Prehistoric finale → Bronze Age', 'ark:item/guardian_trophy', [
        row('', 'now', [i('allosaur_heart')], [i('workshop_schematic'), i('guardian_trophy')], 'ritual',
            'The heart starts the Guardian Giganotosaurus ritual (P05).'),
    ]),
]

# ---------------------------------------------------------------- sheet 2: vanilla netlist
# station tags: 2x2, T Working Station, SC stonecutter, FG forge, FI stone fire, CR crusher, MB medicine bench, W world
# status: now, spec (your spec changes it), cut, starved (recipe stays, the material has no source)
def f(label, tag='T', st='now', need=''):
    return {'label': label, 'tag': tag, 'st': st, 'need': need}


NETS = [
    ('WOOD', 'Rock Axe · 2.1', 'mc:block/oak_log', [
        f('Planks, sticks', '2x2'), f('Slabs, stairs, fences, gates, doors, trapdoors, plates, buttons, signs, boats, shelves', 'T', need='×10 woods'),
        f('Hanging signs', need='+ chain'), f('Working Station, Storage Crate', need='replace the crafting table and chests'),
        f('Barrel, bowl, ladder, composter'), f('Mortar & Pestle', need='rock + cobblestone'),
        f('Loom, cartography table', st='cut', need='removed'),
        f('Bookshelf, lectern, chiseled bookshelf', need='+ book'), f('Beds', need='+ wool ×16'),
        f('Item frame, painting', need='+ leather / wool'), f('Charcoal', 'FG'),
        f('Wooden tools and spear', st='cut'),
        f('Crimson and warped sets', st='starved', need='Nether only'),
    ]),
    ('STONE', 'Rock Pickaxe · 2.2', 'mc:block/cobblestone', [
        f('Stone, smooth stone', 'FG'), f('Stone, brick, deepslate, tuff, andesite, diorite, granite, sandstone, mud and resin families', 'SC', need='~275 cuts'),
        f('Lever, stone button and plate, grindstone'), f('Armor stand', need='+ smooth slab'),
        f('Gravel, sand', 'CR', need='crushed from cobblestone'), f('Crusher', need='+ grindstone, logs'),
        f('Stone tools, stone spear', st='cut'), f('Cobblestone from 4 rocks', st='cut'),
        f('Furnace, smoker, blast furnace', st='cut'), f('Brewing stand', st='cut'),
    ]),
    ('COAL', 'Rock Pickaxe (ore) · Forge (charcoal)', 'mc:item/coal', [
        f('Torch', '2x2'), f('Coal block'), f('Campfire', st='cut', need='the Stone Fire replaces it'), f('Lantern, copper torch, copper lantern', need='+ nuggets'),
        f('Soul torch, lantern, campfire', st='cut'), f('Fire charge', st='cut'),
    ]),
    ('LEATHER', 'kills · 2.3', 'mc:item/leather', [
        f('Leather armour ×4, horse armour', need='dyeable'), f('Book, writable book', need='+ paper'),
        f('Bundle ×16', need='+ string'), f('Saddle', need='+ iron'),
        f('Harness ×16', st='starved', need='happy ghast removed'),
    ]),
    ('STRING', 'spiders · 2.3', 'mc:item/string', [
        f('Bow, crossbow, fishing rod'), f('Wool', '2x2'), f('Scaffolding', need='+ bamboo'),
        f('Candle ×17', need='+ honeycomb'), f('Lead', need='+ slime; Ark: 5 fiber'),
    ]),
    ('WOOL', 'kill or shear sheep', 'mc:block/white_wool', [
        f('Carpets ×16'), f('Beds ×16'), f('Banners ×16', need='plain: no loom'), f('Painting'),
    ]),
    ('BONE · FEATHER · FLINT', 'kills · gravel', 'mc:item/bone', [
        f('Bone meal, bone block, white dye', '2x2'), f('Bone meal ×5', 'CR'),
        f('Arrows', st='spec', need='sharp rock + feather'),
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
        f('TNT, TNT minecart', st='starved', need='gunpowder: Bronze Age sulphur'), f('Glass bottle', need='→ Mortar & Pestle'), f('Beacon', st='cut'),
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
        f('Anvil, Ark Smithing Table, stonecutter, cauldron, crafter'), f('Hopper', need='takes a Storage Crate'),
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
        f('16 dyes', '2x2'), f('2 dyes per flower', 'CR'), f('Wool, carpet, bed, banner, stained glass, terracotta, concrete, candle, bundle'),
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
        f('Storage terminal, level emitter, filters', need='copper instead of comparators and glowstone'),
        f('Inventory connector, interface, cable connector, wireless terminals', need='iron instead of ender pearls'),
    ]),
]

# ---------------------------------------------------------------- cut list and decisions
CUTS = [
    ('Removed on 2026-09-26', [
        '<b>Stone tools</b> ×6 (stone spear included) and the <b>wooden spear</b>: the rock set replaces them',
        '<b>Stone Knife</b> (the Flint Knife stays)',
        '<b>4 Rock → Cobblestone</b>: cobblestone only comes from mining stone',
        'The vanilla <b>campfire</b> recipe: the Stone Fire is its redesign',
        '<b>Recovery Cache</b>; the <b>Flint Spear</b> (replaced by the Keratin Spear); flint in arrows (Sharp Rock)',
        '<b>Loom</b> and <b>Cartography Table</b>; the crafting table, chests and smithing table (replaced, F14)',
        'Crusher <b>ore doubling</b> and <b>flint → gunpowder</b>',
    ]),
    ('Moved to a later age', [
        '<b>Concentrated Sedative</b> and the <b>Improved Tranquilizer Arrow</b>: Bronze Age',
        '<b>Gunpowder</b>: Bronze Age, from sulphur and cinnabar crystals in caves (F15)',
        '<b>Medicine Bench</b>: Iron Age, medicine to be designed',
    ]),
    ('Already removed by the theme ({{CUT}} vanilla recipes)', [
        'Magic: enchanting table, brewing stand, potions, tipped arrows, golden apple, glistering melon',
        'Teleport and End: ender pearl, eye and chest, end rod, end crystal, shulker box, purpur',
        'Nether tiers: netherite ×15, respawn anchor, lodestone, soul torch, lantern and campfire, magma',
        'Trials and flight: mace, wind charge, flow template, fireworks, fire charge',
        'Furnace, smoker, blast furnace, furnace minecart; beacon, conduit, recovery compass',
        'Copper bulbs: re-added by Ark with a torch instead of the blaze rod',
    ]),
    ('Recipe stays, material has no source (accepted)', [
        '<b>Nether quartz</b> → comparator, observer, daylight detector',
        '<b>Glowstone</b> (cleric trades only) → redstone lamp, spectral arrow',
        'Prismarine, sponge, trident, crimson and warped wood, 11 trim templates, harness, copper golem statue',
    ]),
]

DECISIONS = [
    ('Q1', 'Decided', 'Rock tool mining tier',
     'The rock set used the wooden tier, which cannot harvest copper, iron or lapis ore.',
     'Done: wood stats (durability 59, speed, damage), but the rock tools break what stone tools break.'),
    ('Q2', 'Decided', 'The bedroll needs the 3×3 grid',
     '9 Fiber does not fit the 2×2 inventory.',
     'Done: the bedroll is made at the Working Station.'),
    ('Q3', 'Decided', 'Fiber in any slot',
     'A shaped recipe that accepts one Fiber in any free slot.',
     'Done for the whole rock set: a bound_shaped recipe type; the recipe book shows the Fiber in one slot.'),
    ('Q4', 'Decided', 'Starter kit',
     'New players got a bedroll, 2 bandages, 8 fiber and a flint knife.',
     'Done: only a flint knife.'),
    ('Q5', 'Decided', 'Berry names',
     'The game had Narco-, Tinto-, Amar- and Azulberry.',
     'Done: Blackberry, Redberry, Yellowberry and Blueberry; the old ids stay so existing worlds load.'),
    ('Q6', 'Decided', 'One rock set',
     'The Stone Knife and Flint Knife overlapped with the new set.',
     'Done: the Stone Knife is deleted, the Flint Knife stays, and London completes on the first equipment '
     '(any rock tool or the flint knife).'),
    ('Q7', 'Decided', 'Sedative chain',
     'Blackberry, Narcotics and the tranquilizer arrows.',
     'Done: Blackberries grind into Narcotics in the Mortar & Pestle, and Narcotics tip the Tranquilizer Arrow. '
     'The Concentrated Sedative and the Improved Tranquilizer Arrow wait for the Bronze Age.'),
    ('Q8', 'Decided', 'Twig',
     'Twig and Stick overlapped.',
     'Done: the vanilla Stick is renamed Twig. Still open: the Drying Rack recipe (2 logs + 4 twigs proposed).'),
    ('Q9', 'Decided', 'No quartz',
     'Comparator, observer and daylight detector need nether quartz.',
     'Accepted: they do not fit a prehistoric survival theme, so they stay without a source.'),
    ('Q10', 'Decided', "Tom's Storage recipes",
     'The connector, interface and terminals needed ender pearls, comparators and glowstone.',
     'Done: ender pearls became iron ingots and every redstone part and glowstone became copper, so the storage '
     'network arrives with the metals.'),
    ('Q11', 'Decided', 'Gunpowder',
     'Creepers, ghasts and witches are gone.',
     'Bronze Age: new gunpowder recipes from sulphur and cinnabar crystals in caves (dashboard F15). The Crusher '
     'no longer makes it.'),
    ('Q12', 'Decided', 'Vanilla campfire',
     'The campfire cooked like the Stone Fire.',
     'Done: its recipe is removed; the Stone Fire is the redesign, not a parallel.'),
    ('Q13', 'Decided', 'Blueberry',
     'The Blueberry had no effect.',
     'Done: it restores one hunger point, and fed to a hurt tame it heals it and starts regeneration.'),
    ('Q14', 'Decided', 'Crusher ore doubling',
     'Crushing ore needed Silk Touch ore blocks.',
     'Done: the Crusher no longer processes ore at all.'),
    ('Q15', 'Decided', 'Medicine Bench and the Mortar',
     'Both made medicine.',
     'Done: the herbal remedies moved to the Mortar & Pestle; the Medicine Bench needs iron and waits for the Iron '
     'Age medicine.'),
]


# ---------------------------------------------------------------- icons
# Placed blocks: the item id and the tools/render_blocks.py entry that draws it.
CUBE_RENDERS = {'stone_fire': 'stone_fire', 'cooking_pot': 'pot', 'forge': 'forge', 'drying_rack': 'rack',
                'bedroll': 'bedroll', 'trough': 'trough', 'mortar': 'mortar', 'working_station': 'working_station',
                'storage_crate': 'storage_crate', 'smithing_table': 'smithing_table',
                'medicine_bench': 'medicine_bench', 'crusher': 'crusher'}
_BLOCK_ICONS = {}


def block_icons():
    """Small icons for placed blocks, cut from the standard block renders (the showcase's block cards)."""
    if _BLOCK_ICONS or not JAR.is_file():
        return _BLOCK_ICONS
    import render_blocks
    entries = {entry[0]: entry for entry in render_blocks.BLOCKS}
    for item, key in CUBE_RENDERS.items():
        try:
            tile = render_blocks.render(entries[key][-1])
        except (KeyError, FileNotFoundError):
            continue
        box = tile.getbbox()
        tile = tile.crop(box) if box else tile
        tile.thumbnail((40, 40), Image.Resampling.LANCZOS)
        canvas = Image.new('RGBA', (40, 40))
        canvas.alpha_composite(tile, ((40 - tile.width) // 2, (40 - tile.height) // 2))
        buf = io.BytesIO()
        canvas.save(buf, 'PNG', optimize=True)
        _BLOCK_ICONS[item] = 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()
    return _BLOCK_ICONS


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

    rendered = block_icons()
    items = {k: {'name': v[0], 'icon': rendered.get(k) or uri(v[1]), 'cube': v[1] == 'cube' and k not in rendered,
                 'smooth': k in rendered, 'art': v[2], 'was': v[3]}
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
        f'<p class="rec"><span>{"Decision" if tag == "Decided" else "Recommendation"}</span>{html.escape(rec)}</p></li>'
        for q, tag, title, body, rec in DECISIONS)


TITLE_BLOCK = {
    'title': 'Recipe Gates · dependency map', 'project': 'Ark: Survival Returns', 'rev': 'Draft A',
    'source': 'Your spec · mod data · vanilla 26.1.2', 'date': '2026-09-26',
    'notes': ['Columns count crafting steps from bare hands; every item sits in the first column where it can be made.',
              'Small numbers on a chip are the steps of your recipe-gate spec (F12); circled numbers point to the notes.'],
}

LEGEND = [
    ('Recipe status', [
        ('<rect x="1" y="2" width="42" height="16" rx="3" fill="var(--sp-chip)" stroke="var(--sp-you)" stroke-width="2"/>', 'Your spec'),
        ('<rect x="1" y="2" width="42" height="16" rx="3" fill="var(--sp-chip)" stroke="var(--sp-prop)" stroke-width="1.6" stroke-dasharray="5 3"/>', 'Proposed here'),
        ('<rect x="1" y="2" width="42" height="16" rx="3" fill="var(--sp-chip)" stroke="var(--sp-now)"/>', 'In the game now, unchanged'),
        ('<rect x="1" y="2" width="42" height="16" rx="3" fill="var(--sp-chip)" stroke="var(--sp-change)" stroke-width="2"/>', 'In the game, changes to match the spec'),
        ('<rect x="1" y="2" width="42" height="16" rx="3" fill="var(--sp-chip)" stroke="var(--sp-cut)" stroke-dasharray="2 2"/>'
         '<line x1="4" x2="40" y1="10" y2="10" stroke="var(--sp-cut)" stroke-width="1.4"/>', 'Removed (shown with "Show removed")'),
        ('<rect x="1" y="2" width="42" height="16" rx="3" fill="var(--sp-chip)" stroke="var(--sp-cut)" stroke-width="2" stroke-dasharray=".5 3.5" stroke-linecap="round"/>', 'Blocked until a decision'),
        ('<rect x="1" y="2" width="42" height="16" rx="3" fill="var(--sp-chip)" stroke="var(--sp-tbd)" stroke-width="2" stroke-dasharray=".5 3.5" stroke-linecap="round"/>', 'Yours to decide'),
    ]),
    ('Reading the map', [
        ('<rect x="1" y="2" width="42" height="16" rx="3" fill="var(--sp-gate)" stroke="var(--sp-ink)" stroke-width="2"/>', 'Station or tool gate: it opens the groups it points to'),
        ('<rect x="1" y="2" width="42" height="16" rx="8" fill="none" stroke="var(--sp-ink2)" stroke-dasharray="3 2.5"/>', 'Gathered from the world'),
        ('<rect x="1" y="2" width="42" height="16" rx="3" fill="var(--sp-chip)" stroke="var(--sp-ink2)" stroke-dasharray="1 2"/>', '"Any of": any member will do'),
        ('<path d="M2 10C16 10 26 4 42 4" fill="none" stroke="var(--sp-wire)" stroke-width="1.6"/>', 'Ingredient: goes into the item on the right'),
        ('<path d="M2 10C16 10 26 16 42 16" fill="none" stroke="var(--sp-ink2)" stroke-width="1.2" stroke-dasharray="4 3"/>', 'Needs this station or tool'),
        ('<rect x="1" y="2" width="42" height="16" rx="3" fill="var(--sp-chip)" stroke="var(--sp-cut)" stroke-dasharray="1 2" opacity=".55"/>', 'Waits on a blocked decision further back'),
        ('<rect x="14" y="2" width="16" height="16" fill="url(#sp-legend-hatch)" stroke="var(--sp-ink2)" stroke-width=".6"/>', 'Needs a new sprite'),
        ('<rect x="14" y="2" width="16" height="16" fill="var(--sp-rule)"/><path d="M24 1h7v7z" fill="var(--sp-change)"/>', 'Drawn with a borrowed sprite today'),
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
    return {'spine': SPINE, 'groups': GROUPS, 'items': items, 'glyphs': glyphs, 'notes': notes,
            'titleBlock': TITLE_BLOCK}, notes


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
    page = page.replace('{{STATIONS}}', str(sum(1 for n in SPINE if n['item'] and n['st'] != 'cut')))
    page = page.replace('{{DECISIONS}}', str(len(DECISIONS)))
    page = page.replace('{{DECIDED}}', str(sum(1 for d in DECISIONS if d[1] == 'Decided')))
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
