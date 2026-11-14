"""Checks the playthrough (F43) against the recipe tree without running the game: replays the planner of
feature/playthrough/Gathering.java over the compiled bench data and lists what a survival player could not earn.

    python tools/playthrough_check.py

It reads Gathering's sources (break, hunt, process, cook, fill) from the Java file and the benches from
tools/workstation_plan.py, and mirrors Gathering.next(): sources first, then the bench recipe (one whose inputs all
have a way in first, then the inventory's own graph, then the lowest level), every bench and station earned first,
tags resolved held > gathered > crafted. Prints the root causes, the Ark items not earnable and each stage goal.
Rerun it after a recipe or source change.
"""
import json, re, sys
from collections import Counter
sys.path.insert(0, 'tools')
import recipe_tree as rt

NS = 'arksurvivalreturns:'
src = open('src/main/java/dev/nez/arksurvivalreturns/feature/playthrough/Gathering.java', encoding='utf-8').read()
CONST = {'FORGE': NS + 'primitive_forge', 'CRUSHER': NS + 'crusher', 'RACK': NS + 'drying_rack', 'STONECUTTER': 'minecraft:stonecutter'}


def val(tok):
    tok = tok.strip()
    if tok in CONST:
        return CONST[tok]
    if tok.startswith('Species.'):
        return tok
    return re.sub(r'^NS \+ "', NS, tok).strip('"')


SOURCES = {}
for m in re.finditer(r'^\s+(breaks|hunts|processes)\((.*)\);', src, re.M):
    args = [val(a) for a in m.group(2).split(',')]
    SOURCES[args[0]] = (m.group(1), args[1:])
for w in ['spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'pale_oak']:
    SOURCES['minecraft:' + w + '_log'] = ('breaks', ['minecraft:' + w + '_log'])
SOURCES.pop('"minecraft:" + wood + "_log"', None)
SOURCES['minecraft:water_bottle'] = ('fills', ['minecraft:glass_bottle'])
HELD = {'minecraft:short_grass': 'minecraft:shears'}
for b in ['tintoberry', 'amarberry', 'azulberry', 'narcoberry']:
    SOURCES[NS + b] = ('breaks', [NS + b + '_bush'])
for m in re.finditer(r'SOURCES\.put\(NS \+ "(\w+)", new Cooks\(List\.of\((.*?)\)\)\);', src):
    SOURCES[NS + m.group(1)] = ('cooks', [val(a) for a in m.group(2).split(',')])

lang = json.load(open('src/generated/resources/assets/arksurvivalreturns/lang/en_us.json', encoding='utf-8'))
ARK_ITEMS = {NS + k.split('.')[-1] for k in lang if k.count('.') == 2 and k.startswith(('item.' + NS[:-1], 'block.' + NS[:-1]))}


def registered(item):
    return not item.startswith(NS) or item in ARK_ITEMS


SRC = rt.Sources()
plan = SRC.plan
import pathlib
for f in pathlib.Path('src/generated/resources/data/c/tags/item').rglob('*.json'):
    key = '#c:' + f.relative_to('src/generated/resources/data/c/tags/item').as_posix()[:-5]
    plan.tags[key] = plan.tags.get(key, []) + json.load(open(f))['values']
PHASE_A = ['inventory', 'armoury', 'working_station', 'mortar_and_pestle', 'medicine_bench', 'smithing_table', 'saddlery']
recipes = {}
cands = {}


def walk(station, entries, level):
    for e in entries:
        if 'variants' in e:
            walk(station, [dict(v, level=v.get('level') or e.get('level') or level) for v in e['variants']], level)
        elif 'items' in e and 'item' not in e:
            walk(station, e['items'], e.get('level') or level)
        elif 'item' in e and not e.get('apply') and registered(e['item']):
            cands.setdefault(e['item'], []).append((station, e.get('cost', {}), e.get('count', 1), max(level, e.get('level') or 0)))


for st in PHASE_A:
    for c in SRC.trees[st].get('categories', []):
        walk(st, c.get('items', []), c.get('level') or 0)
BENCH = {st: NS + st for st in PHASE_A if st != 'inventory'}
MADE = set(cands)
def known(key):
    if not key.startswith('#'): return key in SOURCES or key in MADE
    return any(known(m) for m in plan.members(key))
for item, cs in cands.items():
    recipes[item] = max(cs, key=lambda r: all(known(k) for k in r[1]) * 10000 + (r[0] == 'inventory') * 1000 - r[3])


def resolve(key):
    if not key.startswith('#'):
        return key
    members = list(plan.members(key))
    for m in members:
        if m in SOURCES:
            return m
    for m in members:
        if m in recipes:
            return m
    return members[0] if members else None


memo = {}


def why(item, stack=()):
    if item in memo:
        return memo[item]
    if len(stack) > 12 or item in stack:
        return 'needs itself: ' + ' > '.join(s.split(':')[1] for s in stack + (item,))
    stack += (item,)
    r = None
    if item in SOURCES:
        kind, a = SOURCES[item]
        if kind == 'fills':
            r = why(a[0], stack)
        elif kind == 'breaks' and item in HELD:
            r = why(HELD[item], stack)
        elif kind == 'processes':
            r = why(a[0], stack) or why(a[1], stack) or (why('minecraft:oak_log', stack) if a[0].endswith('forge') else None)
        elif kind == 'cooks':
            for need in [NS + 'stone_fire', NS + 'cooking_pot', NS + 'fire_starter', *a]:
                r = r or why(need, stack)
    elif item in recipes:
        st, cost, n, lvl = recipes[item]
        if st != 'inventory':
            r = 'made at itself' if BENCH[st] == item else why(BENCH[st], stack)
        for k in cost:
            if r:
                break
            m = resolve(k)
            r = f'unknown cost {k}' if m is None else why(m, stack)
    else:
        r = 'no source: ' + item
    memo[item] = r
    return r


if __name__ == '__main__':
    blocked = {t: why(t) for t in sorted(recipes) if why(t)}
    print(len(recipes), 'items the benches make;', len(recipes) - len(blocked), 'earnable;', len(blocked), 'not')
    print('\nroot causes:')
    for reason, n in Counter(blocked.values()).most_common():
        print(f'  {n:4}  {reason}')
    ark = sorted(t for t in blocked if t.startswith(NS))
    print('\nArk items not earnable:', len(ark))
    for t in ark:
        print('  ', t.split(':')[1], '->', blocked[t])
    goals = ['rock', 'stone_hatchet', 'tintoberry', 'rock_pickaxe', 'rock_sword', 'rock_shovel', 'dried_meat', 'minecraft:leather_helmet',
             'flint_knife', 'keratin', 'keratin_spear', 'keratin_helmet', 'saddlery', 'mattress', 'stone_fire', 'hearty_stew', 'trail_mix',
             'narcotics', 'primitive_forge', 'bronze_ingot', 'bronze_pickaxe', 'bronze_axe', 'bronze_shovel', 'bronze_longsword', 'bronze_hammer',
             'bronze_helmet', 'bedroll', 'minecraft:coal', 'minecraft:iron_ingot', 'sulphur', 'minecraft:gunpowder', 'minecraft:glass',
             'medicine_bench', 'herbal_bandage', 'vitamins', 'explosive_arrow', 'storage_crate', 'azulberry', 'minecraft:bow',
             'tranquilizer_arrow', 'minecraft:carrot', 'minecraft:oak_planks', 'minecraft:gold_ingot', 'minecraft:golden_sword',
             'minecraft:golden_chestplate', 'minecraft:diamond', 'minecraft:diamond_sword', 'minecraft:diamond_chestplate',
             'minecraft:crossbow', 'improved_tranquilizer_arrow', 'minecraft:arrow']
    print('\nstage goals:')
    for g in goals:
        g = g if ':' in g else NS + g
        print('  ', g.split(':')[1], '->', why(g) or 'ok')
