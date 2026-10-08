"""The existence column of the showcase's Existence Model page: the data its simulator runs on.

design/existence/model.json holds the weights of the proposed model, the sample regions and the constants of the
rules the game runs today; design/existence/weights.json, once the page's Save weights has written it, holds the
weights as they were tuned there and wins. This module joins them with what the game itself says: the species of
design/showcase/species.json (runData), their spawn weight and timidity from Species.java (runData does not export
them yet), and who lives in each sample biome from the generated spawn tags, so the simulator follows the code.
The model is tools/existence_model.js, the page's controls tools/existence_ui.js; build_showcase.py calls
section(), tools/existence_check.py calls data().

Run from Ark: python tools/showcase_existence.py  (prints the assembled data as JSON)
"""
import json
import re
import sys
from pathlib import Path

ARK = Path(__file__).resolve().parents[1]
HERE = Path(__file__).resolve().parent
MODEL = ARK / 'design/existence/model.json'
TUNED = ARK / 'design/existence/weights.json'
SPECIES = ARK / 'design/showcase/species.json'
TAGS = ARK / 'src/generated/resources/data/arksurvivalreturns/tags/worldgen/biome'
JAVA = ARK / 'src/main/java/dev/nez/arksurvivalreturns'
# NAME("id", "Name", health, damage, speed, width, height, groupMin, groupMax, weight, predator, ...
CONSTANT = re.compile(r'^    [A-Z_]+\("(\w+)", "[^"]+", ([\d.]+), ([\d.]+), [\d.]+, [\d.]+f, [\d.]+f, (\d+), (\d+), (\d+), (true|false),', re.M)
TIMID = re.compile(r'new FlyerProfile\((?:[^,()]+,\s*){5}(true|false)|new LandProfile\(LandFamily\.\w+,\s*(true|false)')


def members(tag):
    """Biome ids of an Ark biome tag, through its references to other Ark tags; optional entries count."""
    out = set()
    for value in json.loads((TAGS / f'{tag}.json').read_text(encoding='utf-8'))['values']:
        value = value['id'] if isinstance(value, dict) else value
        if value.startswith('#arksurvivalreturns:'):
            out |= members(value.split(':', 1)[1])
        elif value.startswith('#'):
            raise SystemExit(f'{tag}: {value} is not an Ark tag; showcase_existence.py cannot resolve it')
        else:
            out.add(value)
    return out


def java_traits(species):
    """Spawn weight and timidity of every species, from the constants of Species.java; the build stops if they no longer read."""
    source = (JAVA / 'feature/creature/Species.java').read_text(encoding='utf-8')
    found = list(CONSTANT.finditer(source))
    traits = {}
    for index, match in enumerate(found):
        block = source[match.start():found[index + 1].start() if index + 1 < len(found) else source.index('public enum Realm')]
        timid = TIMID.search(block)
        traits[match.group(1)] = {'health': float(match.group(2)), 'damage': float(match.group(3)), 'groupMin': int(match.group(4)),
                                  'groupMax': int(match.group(5)), 'weight': int(match.group(6)), 'predator': match.group(7) == 'true',
                                  'timid': bool(timid) and 'true' in timid.groups()}
    for s in species:
        mine = traits.get(s['id'])
        if not mine or any(mine[key] != s[key] for key in ('health', 'damage', 'groupMin', 'groupMax', 'predator')):
            raise SystemExit(f'Species.java no longer reads as showcase_existence.py expects ({s["id"]}): '
                             'run ./gradlew runData, or fix CONSTANT in tools/showcase_existence.py')
    return traits


def drift(coded):
    """Constants of the game's rules that no longer are what model.json says they were; never stops the build."""
    notes = []
    silent = (JAVA / 'feature/spawn/SilentLife.java').read_text(encoding='utf-8')
    for name, value in coded['silent'].items():
        match = re.search(rf'\b{name} = ([\d.]+)', silent)
        if not match:
            notes.append(f'SilentLife.{name} is gone')
        elif float(match.group(1)) != value:
            notes.append(f'SilentLife.{name} is {match.group(1)}, model.json says {value}')
    classes = (JAVA / 'feature/spawn/WildClass.java').read_text(encoding='utf-8')
    for name, value in coded['share'].items():
        match = re.search(rf'\b{name}\(([\d.]+)\)', classes)
        if not match or float(match.group(1)) != value:
            notes.append(f'WildClass.{name} is {match.group(1) if match else "gone"}, model.json says {value}')
    config = (JAVA / 'Config.java').read_text(encoding='utf-8')
    for name, value in coded['config'].items():
        match = re.search(rf'defineInRange\("{name}", ([\d.]+)', config)
        if not match or float(match.group(1)) != value:
            notes.append(f'Config {name} is {match.group(1) if match else "gone"}, model.json says {value}')
    return notes


def data():
    """What tools/existence_model.js runs on: the species, the sample regions with who lives there, and the model."""
    model = json.loads(MODEL.read_text(encoding='utf-8'))
    if TUNED.is_file():
        tuned = json.loads(TUNED.read_text(encoding='utf-8'))['weights']
        for weight in model['weights']:
            weight['value'] = tuned.get(weight['id'], weight['value'])
    species = json.loads(SPECIES.read_text(encoding='utf-8'))['species']
    traits = java_traits(species)
    spawns = {s['id']: members(f'spawns/{s["id"]}') for s in species}
    regions = []
    for region in model['regions']:
        pool = [s['id'] for s in species if region['biome'] in spawns[s['id']]]
        if not pool:
            raise SystemExit(f'{region["biome"]} holds no Ark wildlife; take it out of design/existence/model.json')
        fertility = model['fertility'][region['type']] * (model['fixed']['snow'] if region['snowy'] else 1)
        regions.append({**region, 'fertility': round(fertility, 4), 'pool': pool})
    for note in drift(model['coded']):
        print(f'existence: {note}', file=sys.stderr)
    keep = ('id', 'name', 'health', 'damage', 'groupMin', 'groupMax', 'predator', 'apex', 'realm', 'cold', 'danger')
    return {
        'species': [{**{key: s[key] for key in keep}, 'weight': traits[s['id']]['weight'], 'timid': traits[s['id']]['timid']} for s in species],
        'regions': regions,
        'model': {key: model[key] for key in ('weights', 'fixed', 'fertility', 'coded')},
    }


CODED_NAMES = {
    'GRAZED': 'Hunger of a plant eater after a round', 'FED': 'Hunger of a pack after a kill', 'APPETITE': 'Hunger a round adds to a hunter',
    'HUNTS_FROM': 'Hunger a pack hunts from', 'BREEDS_BELOW': 'Hunger below which a group breeds', 'BIRTH': 'Odds of a young a round',
    'DRY': 'What is left of them without water', 'STARVES': 'Odds a round that a starving pack loses one',
    'STRIKES_BACK': 'Odds that prey standing its ground kills a hunter', 'silentRoundDays': 'Game days between two rounds',
    'silentLivedInRounds': 'Rounds as often where players stayed a day', 'wildLifespanDays': 'Span of a 50 HP animal, game days',
    'populationRefillDays': 'Days to allow a region its whole quota', 'wildGroupsPerPlayer': 'Groups within the population radius',
    'populationRadius': 'Population radius, blocks', 'healthGrowth': 'Health growth a level', 'damageGrowth': 'Damage growth a level'}


def beyond(config):
    """
    The stretch past the last distance tier, which the existence column opens with as the behaviour column opens
    with its tiers: no body, a record lived on by rounds, and the hand-over as the chunk loads.
    """
    every, lived = config['silentRoundDays'], config['silentLivedInRounds']
    cards = [
        ('Record only', 'Chunk not loaded', 'No body. The animal is its line on the wildlife register of its biome region, '
                                            'and nothing of it runs between two rounds.'),
        ('Rounds', 'A round a game day' if every == 1 else f'A round every {every:g} game days',
         f'The existence model lives the region on: its animals feed, are hunted, breed and age. {lived:g} times as often '
         'where players have stayed a day.'),
        ('Hand-over', 'As the chunk loads', 'Each animal gets its body where and as its record is, hunger included: a young '
                                            'born meanwhile has one, an animal that died has none.'),
    ]
    return ('<div class="lod-scale" aria-hidden="true"><em>Past the last tier</em><span class="lod-beyond"><b>Record only</b>'
            '<small>any distance</small></span></div><div class="lod-cols">'
            + ''.join(f'<div class="lod-col"><h4><i class="lod-beyond"></i>{title}</h4><b class="range">{span}</b><p>{text}</p></div>'
                      for title, span, text in cards) + '</div>')


def section(e):
    """The placeholders of the existence column: the data and scripts of its simulator, and the constants of the coded rules."""
    payload = data()
    coded = payload['model']['coded']
    rows = [(CODED_NAMES[name], f'SilentLife.{name}', value) for name, value in coded['silent'].items()]
    rows += [(f'Share of a region\'s groups, {name.lower()}', f'WildClass.{name}', value) for name, value in coded['share'].items()]
    rows += [(CODED_NAMES[name], name, value) for name, value in coded['config'].items()]
    table = ''.join(f'<tr><th>{e(title)}</th><td><code>{e(source)}</code></td><td>{value:g}</td></tr>' for title, source, value in rows)
    return {
        'EXISTENCE_DATA': json.dumps(payload, ensure_ascii=False, separators=(',', ':')).replace('</', '<\\/'),
        'EXISTENCE_JS': '\n'.join((HERE / name).read_text(encoding='utf-8') for name in ('existence_model.js', 'existence_ui.js')),
        'EXISTENCE_CODED': table,
        'EXISTENCE_COMMIT': e(coded['commit']),
        'EXISTENCE_REGIONS': len(payload['regions']),
        'EXISTENCE_BEYOND': beyond(coded['config']),
    }


if __name__ == '__main__':
    json.dump(data(), sys.stdout, ensure_ascii=False, separators=(',', ':'))
