"""Static sweeps behind Audit/02-bugs-and-design.md and Audit/03-recipes-and-gameplay.md. Nothing is built or run: sources and build/resources/main are read.

    python Ark/tools/audit_static.py            every sweep
    python Ark/tools/audit_static.py lang ids   only the named ones

lang    translatable keys the code names that en_us lacks
config  server config values nothing reads
lone    classes no other class refers to and that do not register themselves
ids     ArkSurvivalReturns.id("...") values that match no resource, registered name or sound
items   Ark items with no source, and items that go into nothing (needs a built jar's resources)
levels  the player level each bench craft asks for against the XP it takes
taming  how long a knock-out sleep lasts against the feeding each species needs

Behind Audit/03-recipes-and-gameplay.md:

combat    a wild bite against a player in each armour, zone by zone, and the player's blows against wild health
sedation  doses, berries and feeding time to tame each knock-out species
yield     hide a carcass gives against the hide its saddle and the armour sets ask for
roles     what a tame of each species can do
grid      items only a 3 x 3 grid makes, which no Ark bench has
"""
import collections
import glob
import json
import math
import os
import re
import sys

ARK = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NS = 'arksurvivalreturns'
SRC = os.path.join(ARK, 'src/main/java/dev/nez', NS)
BUILT = os.path.join(ARK, 'build/resources/main')
DATA = os.path.join(BUILT, 'data')
BENCHES = ['inventory', 'armoury', 'working_station', 'mortar_and_pestle', 'medicine_bench', 'smithing_table', 'saddlery']


def sources(skip=('client/audio/physics/',)):
    out = {}
    for path in glob.glob(SRC + '/**/*.java', recursive=True):
        rel = os.path.relpath(path, SRC).replace(os.sep, '/')
        if not rel.startswith(skip):
            out[rel] = open(path, encoding='utf-8', errors='replace').read()
    return out


def lang():
    built = os.path.join(BUILT, 'assets', NS, 'lang/en_us.json')
    generated = os.path.join(ARK, 'src/generated/resources/assets', NS, 'lang/en_us.json')
    return json.load(open(built if os.path.exists(built) else generated, encoding='utf-8'))


def default(config, key, fallback):
    """The default of a defineInRange("key", default, ...) in Config.java."""
    found = re.search(r'defineInRange\("%s",\s*([-\d.]+)' % re.escape(key), config)
    return float(found.group(1)) if found else fallback


def sweep_lang():
    names, used = lang(), collections.defaultdict(set)
    for rel, text in sources().items():
        if rel.startswith(('gametest/', 'datagen/')):
            continue
        for found in re.finditer(r'(?:translatable|I18n\.get)\(\s*"([a-z0-9_.]+)"\s*[,)]', text):
            used[found.group(1)].add(rel)
    missing = sorted(k for k in used if k not in names and NS in k)
    print('lang: %d literal keys, %d absent from en_us' % (len(used), len(missing)))
    for key in missing:
        print('   %s  <- %s' % (key, sorted(used[key])[0]))
    return len(missing)


def sweep_config():
    files = sources(skip=())
    config = files['Config.java']
    names = set(re.findall(r'\b([A-Z][A-Z0-9_]{2,})\s*=\s*b\.', config))
    names |= set(re.findall(r'\b([A-Z][A-Z0-9_]{2,})\.put\(', config))
    rest = '\n'.join(text for rel, text in files.items() if rel != 'Config.java' and not rel.startswith('gametest/'))
    unread = sorted(n for n in names if not re.search(r'Config\.%s\b' % n, rest))
    print('config: %d values, %d read by nothing' % (len(names), len(unread)))
    for name in unread:
        print('   ' + name)
    return len(unread)


def sweep_lone():
    files = {rel: text for rel, text in sources().items() if not rel.startswith('gametest/')}
    lone = []
    for rel, text in sorted(files.items()):
        name = os.path.basename(rel)[:-5]
        if any(mark in text for mark in ('EventBusSubscriber', '@Mixin', '@Mod(')):
            continue
        if not any(other != rel and re.search(r'\b%s\b' % re.escape(name), body) for other, body in files.items()):
            lone.append(rel)
    print('lone: %d classes nothing refers to' % len(lone))
    for rel in lone:
        print('   ' + rel)
    return len(lone)


def sweep_ids():
    if not os.path.isdir(BUILT):
        print('ids: no build/resources/main (run ./gradlew processResources)')
        return 0
    stems = set()
    for root, _, names in os.walk(BUILT):
        parts = os.path.relpath(root, BUILT).replace(os.sep, '/').split('/')
        if len(parts) < 2 or parts[1] != NS:
            continue
        for name in names:
            full = parts[2:] + [name]
            for cut in range(len(full)):
                tail = '/'.join(full[cut:])
                stems.add(tail)
                stems.add(tail.rsplit('.', 1)[0])
    registered = {key.split('.', 2)[2] for key in lang() if key.count('.') >= 2 and key.split('.')[1] == NS}
    sound_file = os.path.join(BUILT, 'assets', NS, 'sounds.json')
    sounds = set(json.load(open(sound_file, encoding='utf-8'))) if os.path.exists(sound_file) else set()
    # Names that are not resources: payload types, saved data files, attribute modifiers, reload listeners.
    plumbing = re.compile(r'(Payloads?|Data|Register|Ledger|Protection|Sync|Network|Client|Events|Service|Content|Spyglass|'
                          r'Structure|Pack|Models|Hud|Effects|Engine)\.java$')
    unmatched = []
    for rel, text in sources().items():
        if rel.startswith(('gametest/', 'datagen/')):
            continue
        for found in re.finditer(r'\bid\(\s*"([a-z0-9_./-]+)"\s*\)', text):
            ident = found.group(1)
            if ident in stems or ident in registered or ident in sounds or plumbing.search(rel):
                continue
            unmatched.append((ident, rel))
    print('ids: %d named in resource-facing code and found nowhere' % len(unmatched))
    for ident, rel in sorted(set(unmatched)):
        print('   %-40s %s' % (ident, rel))
    return len(unmatched)


def bench_crafts():
    """(station, item, level, cost) of every craft the shipped bench graphs hold."""
    crafts = []
    names = lang()

    def registered(item):
        ns, _, path = item.partition(':')
        return ns != NS or any(('%s.%s.%s' % (kind, NS, path)) in names for kind in ('item', 'block'))

    def walk(node, station, level):
        if isinstance(node, dict):
            level = node.get('level', level)
            # A planned craft is in the game as soon as its item is registered (WorkstationDefinition.normalise).
            if 'item' in node and 'cost' in node and registered(node['item']):
                crafts.append((station, node['item'], level or 0, node['cost']))
            for value in node.values():
                walk(value, station, level)
        elif isinstance(node, list):
            for value in node:
                walk(value, station, level)

    for station in BENCHES:
        for folder in ('workstation', 'workstation_rules'):
            path = os.path.join(DATA, NS, folder, station + '.json')
            if os.path.exists(path):
                walk(json.load(open(path, encoding='utf-8')), station, 0)
    return crafts


def sweep_items():
    if not os.path.isdir(os.path.join(DATA, NS, 'workstation')):
        print('items: no built bench data (run ./gradlew processResources)')
        return 0
    made, used = collections.defaultdict(set), collections.defaultdict(set)
    for station, item, _, cost in bench_crafts():
        made[item].add('bench')
        for selector in cost:
            used[selector].add('bench')
    removed = set(json.load(open(os.path.join(DATA, NS, 'workstation_grid_removals.json'), encoding='utf-8')))
    grid = collections.defaultdict(set)
    for space in glob.glob(DATA + '/*'):
        ns = os.path.basename(space)
        for path in glob.glob(space + '/recipe/**/*.json', recursive=True):
            recipe_id = ns + ':' + os.path.relpath(path, os.path.join(space, 'recipe')).replace(os.sep, '/')[:-5]
            if recipe_id in removed:
                continue
            recipe = json.load(open(path, encoding='utf-8'))
            result = recipe.get('result')
            result = result.get('id') if isinstance(result, dict) else result
            kind = recipe.get('type', '')
            text = json.dumps({k: v for k, v in recipe.items() if k not in ('result', 'type', 'category', 'group', 'pattern')})
            if 'crafting' in kind or kind.endswith('bound_shaped'):
                grid[result].add(recipe_id)   # no bench runs a grid recipe
                continue
            made[result].add(kind)
            for ident in re.findall(r'"(#?[a-z0-9_]+:[a-z0-9_/]+)"', text):
                used[ident].add(kind)
        for path in glob.glob(space + '/loot_table/**/*.json', recursive=True):
            table = os.path.relpath(path, os.path.join(space, 'loot_table')).replace(os.sep, '/')[:-5]
            for ident in re.findall(r'"name":\s*"([a-z0-9_]+:[a-z0-9_/]+)"', open(path, encoding='utf-8').read()):
                # A block that drops itself is not where the block comes from.
                if table != 'blocks/' + ident.split(':')[1]:
                    made[ident].add('loot')
        for path in glob.glob(space + '/tags/item/**/*.json', recursive=True):
            for value in json.load(open(path, encoding='utf-8')).get('values', []):
                used[value if isinstance(value, str) else value.get('id', '')].add('tag')
    ark = sorted({NS + ':' + key.split('.', 2)[2] for key in lang()
                  if key.startswith(('item.%s.' % NS, 'block.%s.' % NS)) and key.count('.') == 2})
    ark = [item for item in ark if not item.endswith('_spawn_egg')]
    unsourced = [item for item in ark if not made.get(item)]
    unused = [item for item in ark if not used.get(item)]
    print('items: %d Ark items; %d made by code or the world only, %d of them only by a grid recipe; %d go into nothing'
          % (len(ark), len(unsourced), sum(1 for i in unsourced if i in grid), len(unused)))
    print('   no bench, station recipe or loot table: ' + ', '.join(i.split(':')[1] for i in unsourced))
    endpoints = ('_saddle', '_nest', '_bush', '_trough')
    print('   go into nothing (saddles, nests, bushes and troughs left out): '
          + ', '.join(i.split(':')[1] for i in unused if not i.endswith(endpoints)))
    return 0


def sweep_levels():
    config = open(os.path.join(SRC, 'Config.java'), encoding='utf-8').read()
    base, exponent = default(config, 'baseXp', 100), default(config, 'exponent', 1.5)
    kill = default(config, 'killPerLevel', 5)
    cost = lambda level: sum(math.ceil(base * (n + 1) ** exponent) for n in range(level))
    by_level = collections.defaultdict(set)
    for _, item, level, _ in bench_crafts():
        by_level[level].add(item.split(':')[1])
    print('levels: XP to reach a level, and the crafts that ask for it (kills are of a level 30 animal, %d XP)' % (kill * 30))
    for level in sorted(by_level):
        if level < 8 and level not in (3, 5):
            continue
        saddles = sorted(i for i in by_level[level] if i.endswith('_saddle'))
        rest = sorted(by_level[level] - set(saddles))
        print('   %2d  %7d XP  %5d kills  %s' % (level, cost(level), math.ceil(cost(level) / (kill * 30)),
                                               ', '.join(saddles + rest[:3]) + (' ...' if len(rest) > 3 else '')))
    return 0


def sweep_taming():
    config = open(os.path.join(SRC, 'Config.java'), encoding='utf-8').read()
    delay = default(config, 'torporRecoveryDelayTicks', 200) / 20
    rate = default(config, 'torporRecoveryPerSecond', 0.005)
    wake = default(config, 'wakeThresholdRatio', 0.2)
    interval = default(config, 'minimumFeedIntervalTicks', 400) / 20
    favourite = default(config, 'preferredFoodMultiplier', 1.5)
    sleep = delay + (1 - wake) / rate
    registry = open(os.path.join(SRC, 'feature/taming/CreatureProfileRegistry.java'), encoding='utf-8').read()
    print('taming: a full meter lasts %.0f s; meals are %.0f s apart' % (sleep, interval))
    late_plain = late_favourite = total = 0
    for found in re.finditer(r'add\(Species\.([A-Z_]+), TamingMethod\.KNOCKOUT, (\d+),', registry):
        total += 1
        meals = max(1, math.ceil(int(found.group(2)) / interval))
        fast = math.ceil(meals / favourite - 1e-9)
        plain, best = (meals - 1) * interval, (fast - 1) * interval
        late_plain += plain >= sleep
        late_favourite += best >= sleep
        if best >= sleep:
            print('   %-17s last meal at %3.0f s with its favourite food, %3.0f s without' % (found.group(1), best, plain))
    print('   %d knock-out species; %d wake before the last plain meal, %d even with the favourite food'
          % (total, late_plain, late_favourite))
    return late_favourite


# ------------------------------------------------------------------ phase 3 (Audit/03-recipes-and-gameplay.md)

ZONES = {1: (1, 12), 2: (8, 50), 3: (32, 80)}
ARMOUR = [('none', 0, 0), ('hide', 7, 0), ('keratin', 8, 0), ('bronze', 13, 0), ('diamond', 20, 8)]   # no iron armour is craftable
SIZE_TORPOR = [(1.6, 'small', 60), (3.5, 'medium', 150), (8.0, 'large', 350), (1e9, 'giant', 700)]


def roster():
    """Every species from Species.java with its taming profile, saddle, cargo, job and carcass."""
    text = open(os.path.join(SRC, 'feature/creature/Species.java'), encoding='utf-8').read()
    body = text[:text.index('public enum Realm')]
    size_switch = text[text.index('float size = switch'):text.index('sizeMultiplier = size')]
    danger_switch = text[text.index('public int minimumDanger'):text.index('public boolean apex')]

    def switched(block, ident, fallback):
        for found in re.finditer(r'case ([^>]+)->\s*(\d+)', block):
            if re.search(r'\b%s\b' % ident, found.group(1).replace('"', '')):
                return int(found.group(2))
        return fallback

    registry = open(os.path.join(SRC, 'feature/taming/CreatureProfileRegistry.java'), encoding='utf-8').read()
    taming = {m.group(1): (m.group(2), int(m.group(3)), m.group(4).strip(), float(m.group(5)))
              for m in re.finditer(r'add\(Species\.([A-Z_]+), TamingMethod\.([A-Z]+), (\d+), \w+, (.+?), ([\d.]+)\);', registry)}
    cargo_text = open(os.path.join(SRC, 'feature/cargo/CargoProfiles.java'), encoding='utf-8').read()
    cargo = {}
    for found in re.finditer(r'put\(Harness\.(\w+), (\d+),([^;]+)\);', cargo_text):
        for name in re.findall(r'Species\.([A-Z_]+)', found.group(3)):
            cargo[name] = (found.group(1), int(found.group(2)))
    work = dict(re.findall(r'PROFILES\.put\(Species\.([A-Z_]+), new Profile\(Job\.(\w+)\)',
                           open(os.path.join(SRC, 'feature/work/WorkProfiles.java'), encoding='utf-8').read()))
    trophies = set(re.findall(r'put\(Species\.([A-Z_]+), new Drop',
                              open(os.path.join(SRC, 'feature/accessory/TrophyDrops.java'), encoding='utf-8').read()))
    saddles = {item.split(':')[1][:-7]: (level, cost) for _, item, level, cost in bench_crafts() if item.endswith('_saddle')}
    out = []
    pattern = re.compile(r'^\s+([A-Z_]+)\("(\w+)", "[^"]+", ([\d.]+), ([\d.]+), [\d.]+, ([\d.]+)f, ([\d.]+)f, \d+, \d+, \d+, (true|false),(.*?)(?=^\s+[A-Z_]+\("|\Z)',
                         re.S | re.M)
    for m in pattern.finditer(body):
        name, ident, rest = m.group(1), m.group(2), m.group(8)
        size = switched(size_switch, ident, 2)
        land = re.search(r'new LandProfile\(LandFamily\.\w+, (?:true|false), (\d)', rest)
        flyer = re.search(r'new FlyerProfile\((?:[^,]+, ){6}(\d)', rest)
        danger = int(land.group(1)) if land else int(flyer.group(1)) if flyer else switched(danger_switch, name, 1)
        loot = {}
        table = os.path.join(ARK, 'src/generated/resources/data', NS, 'loot_table/entities', ident + '.json')
        if os.path.exists(table):
            for pool in json.load(open(table, encoding='utf-8')).get('pools', []):
                chance = next((c['chance'] for c in pool.get('conditions', []) if 'chance' in c), 1.0)
                for entry in pool['entries']:
                    entry = entry.get('children', [entry])[-1]   # keratin: the yield without the flint knife
                    count = next((f['count'] for f in entry.get('functions', []) if f['function'].endswith('set_count')), 1)
                    mean = (count['min'] + count['max']) / 2 if isinstance(count, dict) else count
                    loot[entry['name'].split(':')[1]] = mean * chance
        height = float(m.group(6)) * size
        band = next(b for b in SIZE_TORPOR if height <= b[0])
        out.append(dict(name=name, id=ident, hp=float(m.group(3)), damage=float(m.group(4)), height=height, width=float(m.group(5)) * size,
                        predator=m.group(7) == 'true', danger=danger, taming=taming.get(name), cargo=cargo.get(name, ('PACK', 150)),
                        job=work.get(name), trophy=name in trophies, saddle=saddles.get(ident), loot=loot,
                        torpor=band[2], size=band[1]))
    return out


def growth():
    config = open(os.path.join(SRC, 'Config.java'), encoding='utf-8').read()
    health, damage = default(config, 'healthGrowth', 0.10), default(config, 'damageGrowth', 0.14)
    return (lambda base, level: min(1024.0, base * (1 + health * (level - 1) ** 0.85)),
            lambda base, level: base * (1 + damage * math.sqrt(level - 1)))


def after_armour(damage, armour, toughness):
    """Vanilla CombatRules.getDamageAfterAbsorb."""
    return damage * (1 - min(20.0, max(armour / 5.0, armour - damage / (2 + toughness / 4.0))) / 25.0)


def sweep_combat():
    health, damage = growth()
    species = roster()
    print('combat: a bite against a 20 HP player, by danger zone (lowest and highest wild level of the zone)')
    print('   a hit of 30 or more after armour kills outright (downed.overkillMultiplier 1.5); the player never gains health')
    print('   %-17s %-5s %-9s %-11s %s' % ('', 'zone', 'health', 'bite', 'bites to down: ' + ' / '.join(a[0] for a in ARMOUR)))
    worst = 0
    for s in sorted(species, key=lambda s: (s['danger'], s['damage'])):
        if not s['predator']:
            continue
        for zone in range(s['danger'], 4):
            low, high = ZONES[zone]
            cells = []
            for name, points, tough in ARMOUR:
                per = [after_armour(damage(s['damage'], level), points, tough) for level in (low, high)]
                cells.append('-'.join('X' if d >= 30 else str(math.ceil(20 / d)) for d in per))
                worst += name == 'diamond' and per[1] >= 30
            print('   %-17s %-5d %4.0f-%-4.0f %4.1f-%-5.1f  %s' % (s['id'] if zone == s['danger'] else '', zone,
                  health(s['hp'], low), health(s['hp'], high), damage(s['damage'], low), damage(s['damage'], high), '  '.join(cells)))
    print('   the player on the attack: rock sword 4, keratin spear about 5, bronze longsword 7, bronze hammer 9, an arrow 6 to 10;')
    for ident, level in (('parasaur', 6), ('triceratops', 12), ('carnotaurus', 30), ('tyrannosaurus', 32), ('tyrannosaurus', 80)):
        s = next(x for x in species if x['id'] == ident)
        hp = health(s['hp'], level)
        print('   %-14s level %2d  %4.0f HP  %3d longsword blows  %3d arrows' % (ident, level, hp, math.ceil(hp / 7), math.ceil(hp / 7.5)))
    return worst


def sweep_sedation():
    config = open(os.path.join(SRC, 'Config.java'), encoding='utf-8').read()
    arrow, strong = default(config, 'tranquilizerArrowPotency', 75), default(config, 'improvedTranquilizerArrowPotency', 100)
    narcotic = default(config, 'narcoticsPotency', 100)
    health, _ = growth()
    print('sedation: doses to put a species under, whatever its level (the ceiling comes from its height alone)')
    print('   5 narcoberries grind 2 Narcotics; 2 Narcotics tip 4 arrows: an arrow is 1.25 berries, a Narcotics blow 2.5')
    print('   %-17s %-7s %-6s %-7s %-12s %-10s %s' % ('', 'ceiling', 'arrows', 'berries', 'arrow damage', 'tame time', 'health at its zone'))
    for s in sorted(roster(), key=lambda s: (s['torpor'], s['id'])):
        if not s['taming'] or s['taming'][0] != 'KNOCKOUT':
            continue
        resistance = s['taming'][3]
        doses = math.ceil(s['torpor'] / (arrow * resistance))
        low, high = ZONES[s['danger']][0], ZONES[3][1]
        print('   %-17s %-7d %-6d %-7.0f %-12s %3d s      %4.0f (level %d) to %4.0f (level %d)' % (
            s['id'], s['torpor'], doses, math.ceil(doses * 1.25), '%d-%d' % (doses * 6, doses * 10), s['taming'][1],
            health(s['hp'], low), low, health(s['hp'], high), high))
    return 0


def sweep_yield():
    species = roster()
    by_id = {s['id']: s for s in species}
    print('yield: hide a carcass gives (mean) against the hide its own saddle asks for')
    print('   %-17s %-5s %-9s %-10s %-11s %s' % ('', 'level', 'hide/kill', 'saddle', 'own kills', 'other costs'))
    short = 0
    for s in sorted(species, key=lambda s: (s['saddle'] or (99, {}))[0]):
        if not s['saddle']:
            continue
        level, cost = s['saddle']
        hide = cost.get('minecraft:leather', 0)
        per = s['loot'].get('leather', 0)
        kills = '%.0f' % math.ceil(hide / per) if per else 'none'
        short += per == 0
        rest = ', '.join('%d %s' % (v, k.split(':')[1]) for k, v in cost.items() if k != 'minecraft:leather')
        print('   %-17s %-5d %-9.1f %-10s %-11s %s' % (s['id'], level, per, '%d hide' % hide, kills, rest))
    easy = [s for s in species if s['danger'] == 1 and s['loot'].get('leather')]
    mean = sum(s['loot']['leather'] for s in easy) / max(1, len(easy))
    print('   zone 1 animals with hide: %s; %.1f hide a kill on average' % (', '.join(s['id'] for s in easy), mean))
    sets = {'hide armour': 24, 'keratin armour': 6, 'bronze armour': 11, 'saddlery': 4, 'bedroll': 4, 'forge': 3,
            'pack harness': 4, 'reinforced harness': 8, 'tyrannosaurus saddle': by_id['tyrannosaurus']['saddle'][1].get('minecraft:leather', 0)}
    print('   kills of a zone 1 animal for: ' + ', '.join('%s %d' % (k, math.ceil(v / mean)) for k, v in sets.items()))
    return short


def sweep_roles():
    species = roster()
    print('roles: what a tame of each species can do (every tame can be ridden with its saddle, follow, stay and wander)')
    print('   %-17s %-9s %-5s %-6s %-17s %-8s %s' % ('', 'method', 'tame', 'saddle', 'cargo', 'job', 'parts'))
    for s in sorted(species, key=lambda s: (s['danger'], (s['saddle'] or (99,))[0])):
        method, seconds = (s['taming'][0].lower(), s['taming'][1]) if s['taming'] else ('?', 0)
        print('   %-17s %-9s %3ds  L%-5s %-17s %-8s %s' % (s['id'], method, seconds, s['saddle'][0] if s['saddle'] else '-',
              '%d (%s)' % (s['cargo'][1], s['cargo'][0].lower()), (s['job'] or '-').lower(), 'trophy' if s['trophy'] else '-'))
    jobs = sum(1 for s in species if s['job'])
    print('   %d species; %d have a job; none has an ability of its own (one melee strike each)' % (len(species), jobs))
    return 0


def sweep_grid():
    """Items a player can only make on a 3 x 3 grid, which no Ark bench has: a village's crafting table is the one left."""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import workstation_plan as plan
    here = os.getcwd()
    os.chdir(ARK)
    try:
        records, _, _ = plan.plan()
    finally:
        os.chdir(here)
    made = {item for _, item, _, _ in bench_crafts()}
    made |= {r['result'] for r in records if r['fate'] != 'go' and not r['type'].startswith(('crafting', 'bound'))}
    waits = collections.defaultdict(set)
    for r in records:
        if r['result'] and r['result'] not in made and r['fate'] != 'go' and r['source'] != 'mod':
            waits[r['bench'] if r['fate'] in ('stay', 'change') and r['bench'] else 'undecided'].add(r['result'].split(':')[1])
    total = sum(len(v) for v in waits.values())
    print('grid: %d items are made on no bench and by no station; their grid recipes stay loaded' % total)
    for bench in sorted(waits, key=lambda b: (b == 'undecided', b)):
        names = sorted(waits[bench])
        print('   %-17s %3d  %s' % (bench, len(names), ', '.join(names[:14]) + (' ...' if len(names) > 14 else '')))
    return total


SWEEPS = {'lang': sweep_lang, 'config': sweep_config, 'lone': sweep_lone, 'ids': sweep_ids, 'items': sweep_items,
          'levels': sweep_levels, 'taming': sweep_taming, 'combat': sweep_combat, 'sedation': sweep_sedation,
          'yield': sweep_yield, 'roles': sweep_roles, 'grid': sweep_grid}

if __name__ == '__main__':
    chosen = sys.argv[1:] or list(SWEEPS)
    unknown = [name for name in chosen if name not in SWEEPS]
    if unknown:
        sys.exit('unknown sweep: %s (choose from %s)' % (', '.join(unknown), ', '.join(SWEEPS)))
    for name in chosen:
        SWEEPS[name]()
