"""Recipe tree (F12): an age's recipe sequence as one horizontal chart, station by station, for the showcase.

design/recipe-tree/<age>.json says which stations the age has, in the order they open, and which of their items belong
to it. Hands and the Inventory are stations like the benches: what is gathered in the world (and with which tool), and
what the inventory's own crafting graph makes. Every number comes from the game's sources: costs, counts and levels
from the bench designs (tools/workstation_plan.py compiles them), gathering rates from the loot tables and the config.
tools/spine.js draws the chart: stations left to right, each recipe a column on its station, inputs above the line and
outputs below; hovering an item lights where it is made and used.

Run from Ark/: python tools/recipe_tree.py [age] checks craftability: every input must be gathered or made somewhere in
the sequence, and is listed when it is not, or when only a station further right makes it.
"""
import base64
import io
import json
import re
import sys
import zipfile
from pathlib import Path

import workstation_plan as wp

ARK = Path(__file__).resolve().parents[1]
HERE = Path(__file__).resolve().parent
SEQUENCES = ARK / 'design/recipe-tree'
GENERATED = ARK / 'src/generated/resources/data'
CONFIG = ARK / 'src/main/java/dev/nez/arksurvivalreturns/Config.java'
NAMES = {'#minecraft:logs': 'Any Log', '#minecraft:planks': 'Any Planks', '#minecraft:meat': 'Any Meat',
         '#arksurvivalreturns:berries': 'Any Berry', '#c:leathers': 'Hide'}


def load(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


class Sources:
    """The vanilla jar, the mod's generated data and the plan: loot tables, tags, names and the compiled benches."""

    def __init__(self):
        self.plan = wp.Source()
        self.jar = self.plan.jar
        # Ark's additions to vanilla tags (the dinosaur meats in #minecraft:meat) join the vanilla lists.
        for file in (GENERATED / 'minecraft/tags/item').glob('*.json'):
            key = '#minecraft:' + file.stem
            self.plan.tags[key] = self.plan.tags.get(key, []) + load(file)['values']
        records, self.designs, _ = wp.plan(self.plan)
        self.trees = wp.compile_benches(records, self.designs)

    def loot(self, table):
        namespace, path = table.split(':', 1)
        file = GENERATED / namespace / 'loot_table' / f'{path}.json'
        if file.is_file():
            return load(file)
        return json.loads(self.jar.read(f'data/{namespace}/loot_table/{path}.json')) if self.jar else {}

    def tags_of(self, item, used):
        return {tag for tag in used if item in self.plan.members(tag)}


# ------------------------------------------------------------------------------------------------- rates

def count_range(functions):
    for fn in functions:
        if fn.get('function', '').endswith('set_count'):
            count = fn['count']
            if isinstance(count, (int, float)):
                return int(count), int(count)
            if count.get('type', '').endswith('uniform'):
                return int(count['min']), int(count['max'])
            if count.get('type', '').endswith('constant'):
                return int(count['value']), int(count['value'])
    return None


def chance(conditions):
    p = 1.0
    for condition in conditions:
        kind = condition.get('condition', '')
        if kind.endswith('random_chance'):
            p *= condition['chance']
        elif kind.endswith('table_bonus'):
            p *= condition['chances'][0]
    return p


def entries(node, conditions=(), functions=()):
    """Every item entry under a pool or a composite entry, with the conditions and functions on its way."""
    for entry in node.get('entries', node.get('children', [])):
        conds = list(conditions) + entry.get('conditions', [])
        funcs = list(functions) + entry.get('functions', [])
        if 'children' in entry:
            yield from entries(entry, conds, funcs)
        elif entry.get('type', '').endswith('item'):
            yield entry, conds, funcs


def loot_rate(sources, table, item, members=None):
    """'45% · 1–2' for one item (or any of a tag's members) of a block's loot table."""
    data = sources.loot(table)
    wanted = set(members or [item])
    total_weight = None
    for pool in data.get('pools', []):
        found = [(e, c, f) for e, c, f in entries(pool) if e.get('name') in wanted]
        if not found:
            continue
        all_items = [e for e, _, _ in entries(pool)]
        weights = sum(e.get('weight', 1) for e in all_items)
        mine = sum(e.get('weight', 1) for e, _, _ in found)
        entry, conds, funcs = found[0]
        p = chance(list(pool.get('conditions', [])) + conds) * (mine / weights if weights and len(all_items) > 1 and
                                                                 not any('children' in x for x in pool['entries']) else 1)
        n = count_range(list(funcs) + list(pool.get('functions', []))) or (1, 1)
        amount = str(n[0]) if n[0] == n[1] else f'{n[0]}–{n[1]}'
        ripe = any('block_state_property' in c.get('condition', '') for c in pool.get('conditions', []))
        rate = f'{round(p * 100)}%' if p < 0.999 else ''
        text = ' · '.join(part for part in (rate, '' if rate and amount == '1' else amount) if part)
        return text + (' when ripe' if ripe else '')
    return ''


def config_rate(key):
    found = re.search(r'defineInRange\("' + re.escape(key) + r'",\s*([0-9.]+)', CONFIG.read_text(encoding='utf-8'))
    return f'{round(float(found.group(1)) * 100)}%' if found else ''


def kill_rate(sources, items):
    """'0–3 a kill · 41 kinds' over every creature loot table that drops one of the items."""
    low, high, kinds = None, 0, 0
    for file in sorted((GENERATED / 'arksurvivalreturns/loot_table/entities').glob('*.json')):
        best = None
        for pool in load(file).get('pools', []):
            for entry, conds, funcs in entries(pool):
                if entry.get('name') in items:
                    n = count_range(list(funcs) + list(pool.get('functions', []))) or (1, 1)
                    best = n if best is None else (min(best[0], n[0]), max(best[1], n[1]))
        if best:
            kinds += 1
            low = best[0] if low is None else min(low, best[0])
            high = max(high, best[1])
    if not kinds:
        return ''
    return f'{low}–{high} a kill · {kinds} kinds'


# ------------------------------------------------------------------------------------------------- rows

def find(tree, item):
    """The first variant of a compiled bench that makes the item, with its family's level."""
    for category in tree['categories']:
        for entry in category['items']:
            for family in entry['items'] if 'group' in entry else [entry]:
                for variant in family['variants']:
                    if variant['item'] == item:
                        return variant, family.get('level', 0), len(family['variants'])
    return None, 0, 0


def bench_rows(sources, station):
    tree = sources.trees[station['bench']]
    rows = []
    for spec in station['items']:
        spec = {'item': spec} if isinstance(spec, str) else spec
        variant, level, variants = find(tree, spec['item'])
        if variant is None:
            raise SystemExit(f"{station['id']}: {tree['title']} makes no {spec['item']}")
        rename = spec.get('as', {})
        ins = [{'id': rename.get(k, k), 'n': n} for k, n in variant['cost'].items()]
        out = {'id': rename.get(variant['item'], variant['item']), 'n': variant.get('count', 1)}
        if spec.get('val'):
            out['val'] = spec['val']
        rows.append({'op': tree.get('verb', 'Craft').lower(), 'lvl': level if station.get('levels', True) else 0,
                     'ins': ins, 'outs': [out]})
    return rows


def hand_rows(sources, station):
    rows = []
    for spec in station['rows']:
        if 'from' not in spec:
            rows.append({'op': spec['op'], 'lvl': spec.get('lvl', 0), 'ins': [dict(c) for c in spec['ins']],
                         'outs': [dict(c) for c in spec['outs']]})
            continue
        ins = [{'id': spec['icon'], 'world': spec['from']}]
        if spec.get('tool'):
            ins.append({'id': spec['tool'], 'tool': True})
        outs = []
        for out in spec['outs']:
            chip = {'id': out['id']}
            members = sources.plan.members(out['id']) if out['id'].startswith('#') else None
            if out.get('loot'):
                chip['val'] = loot_rate(sources, out['loot'], out['id'], members)
            elif out.get('config'):
                chip['val'] = config_rate(out['config'])
            elif out.get('kills'):
                chip['val'] = kill_rate(sources, set(out['kills']))
            if out.get('val'):
                chip['val'] = out['val']
            outs.append(chip)
        rows.append({'op': spec['op'], 'lvl': 0, 'ins': ins, 'outs': outs})
    return rows


# ------------------------------------------------------------------------------------------------- the chart

def build(age='prehistoric'):
    """The sequence, its sources, the stations with their rows, the inputs nothing makes and the loops back."""
    sequence = load(SEQUENCES / f'{age}.json')
    sources = Sources()
    stations = []
    for station in sequence['stations']:
        design = sources.designs.get(station.get('bench', ''), {})
        rows = bench_rows(sources, station) if 'items' in station else hand_rows(sources, station)
        title = station.get('title') or design.get('title') or station['id'].replace('_', ' ').title()
        stations.append({'id': station['id'], 'name': title, 'sub': station.get('sub', ''),
                         'sigil': station.get('sigil') or design.get('sigil'), 'icon': station.get('icon'),
                         'gate': station.get('gate', False), 'rows': rows})
    # Keys tie chips that are the same thing: an item and every tag it belongs to that the chart uses.
    chips = [(s, r, c, side) for s in stations for r in s['rows'] for side in ('ins', 'outs') for c in r[side]]
    used = {c['id'] for _, _, c, _ in chips if c['id'].startswith('#')}
    for _, _, chip, _ in chips:
        chip['k'] = sorted({chip['id']} | (sources.tags_of(chip['id'], used) if not chip['id'].startswith('#') else set()))
    # An input nothing makes is a gap in the age; one first made further right is a loop back (a held tool is one).
    problems, later = [], []
    order = {s['id']: i for i, s in enumerate(stations)}
    for station, row, chip, side in chips:
        if side != 'ins' or chip.get('world'):
            continue
        makers = [s for s, _, c, sd in chips if sd == 'outs' and set(c['k']) & set(chip['k'])]
        if not makers:
            problems.append(f"{station['name']}: nothing in the sequence makes {chip['id']}")
        elif min(order[s['id']] for s in makers) > order[station['id']] and not chip.get('tool'):
            later.append(f"{station['name']}: {chip['id']} is first made further right, at {makers[0]['name']}")
    return sequence, sources, stations, problems, later


def icon_uri(image):
    buffer = io.BytesIO()
    image.save(buffer, 'PNG', optimize=True)
    return 'data:image/png;base64,' + base64.b64encode(buffer.getvalue()).decode()


def section(uri=None, e=None, age='prehistoric'):
    """The showcase placeholders: the chart data, the renderer and its styles."""
    from item_icons import Icons
    import showcase_recipes as display
    import build_workstation_crests as crests
    sequence, sources, stations, problems, _ = build(age)
    table = display.names(sources.jar)
    icons = Icons()
    items = {}
    for station in stations:
        for row in station['rows']:
            for chip in row['ins'] + row['outs']:
                key = chip['id']
                if key in items:
                    continue
                rep = display.representative(key, sources.plan)
                image = icons.icon(rep) if rep else None
                name = NAMES.get(key) or display.item_name(key, table, sources.plan)
                items[key] = {'name': name, 'icon': icon_uri(image) if image else ''}
        glyph = crests.sigil(station['sigil']).resize((32, 32)) if station['sigil'] else icons.icon(station['icon'])
        station['glyph'] = icon_uri(glyph) if glyph else ''
    data = {'title': sequence['title'], 'next': sequence.get('next', ''), 'stations': stations, 'items': items,
            'problems': problems}
    return {
        'TREE_DATA': json.dumps(data, ensure_ascii=False, separators=(',', ':')).replace('</', '<\\/'),
        'TREE_JS': (HERE / 'spine.js').read_text(encoding='utf-8'),
        'TREE_CSS': (HERE / 'spine.css').read_text(encoding='utf-8'),
        'TREE_ROWS': sum(len(s['rows']) for s in stations),
        'TREE_STATIONS': len(stations),
    }


if __name__ == '__main__':
    _, _, found, issues, loops = build(sys.argv[1] if len(sys.argv) > 1 else 'prehistoric')
    for station in found:
        print(f"{station['name']}: {len(station['rows'])} rows")
    print('\n'.join(issues) if issues else 'Every input is gathered or made somewhere in the sequence.')
    if loops:
        print('Made further right (labels that point back):\n' + '\n'.join(loops))
