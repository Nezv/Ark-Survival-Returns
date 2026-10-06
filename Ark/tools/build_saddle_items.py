"""Saddle items: one saddle per species, its Saddlery recipe and its sprite (F28, F29), and the hide's sprite (F31).

The mod registers a <species>_saddle item for every species (ModContent.SADDLES) and a creature takes only its own.
This tool writes what follows from the roster (design/showcase/species.json, exported by runData) and from the
concept saddles (design/showcase/saddles.json, tools/preview_saddles.py):

- the saddle categories of design/workstations/saddlery.json, the one file both the game (shipped verbatim as
  data/arksurvivalreturns/workstation/saddlery.json) and the showcase's Saddlery screen read. A species goes to the
  land herbivores, the land carnivores, the fliers, the aquatic or the amphibious animals; the level that unlocks its
  saddle grows with the animal (a quarter of its health, rounded up); the cost follows the concept saddle: hide and
  fiber by the animal's size, bronze or iron for the two metal tiers, twigs for a framed chair or the Spinosaurus's
  rails, planks for a deck. The other categories of the file are kept as written.
- textures/item/<species>_saddle.png: the sprite of the concept saddle's tier and seat (build_saddle_sprites.py).
- assets/minecraft/textures/item/leather.png: the hide sprite, for the vanilla leather the mod renames to Hide.

Run from Ark after runData: python tools/build_saddle_items.py
"""
import json
import shutil
from pathlib import Path

ARK = Path(__file__).resolve().parents[1]
NS = 'arksurvivalreturns'
DESIGN = ARK / 'design/workstations/saddlery.json'
SPRITES = ARK / 'design/items/sprites'
ASSETS = ARK / 'src/main/resources/assets'
# id, title, colour, the species whose spawn egg is its icon, the category it follows in the graph
CATEGORIES = [('herbivores', 'Land Herbivores', '#8fb07f', 'triceratops', 'tack'),
              ('carnivores', 'Land Carnivores', '#c2573f', 'tyrannosaurus', 'herbivores'),
              ('fliers', 'Fliers', '#8fb6d9', 'pteranodon', 'tack'),
              ('aquatic', 'Aquatic', '#4f8fb5', 'megalodon', 'tack'),
              ('amphibious', 'Amphibious', '#6fa58c', 'sarco', 'aquatic')]
SEAT_SPRITE = {'straddle': 'straddle', 'chair': 'chair', 'platform': 'deck', 'sail': 'sail', 'none': 'straddle'}
# Steel is not registered yet: the steel tier is stitched with iron until it is.
METAL = {'hide': None, 'bronze': f'{NS}:bronze_ingot', 'steel': 'minecraft:iron_ingot'}
FRAME = {'chair': ('minecraft:stick', 4), 'sail': ('minecraft:stick', 8), 'platform': ('#minecraft:planks', 8)}


def load(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def category_of(species):
    if species['habitat'] == 'sky':
        return 'fliers'
    if species['habitat'] == 'sea':
        return 'aquatic'
    if species['habitat'] == 'wetland':
        return 'amphibious'
    return 'carnivores' if species['predator'] else 'herbivores'


def level_of(species):
    """The greater the animal, the higher the level: a quarter of its health, rounded up (the cap is 50)."""
    return max(2, -(-int(species['health']) // 4))


def share(health, per, low, high):
    return min(high, max(low, (int(health) + per // 2) // per))


def cost_of(species, saddle):
    health = species['health']
    cost = {'minecraft:leather': share(health, 8, 3, 24)}
    if METAL[saddle['tier']]:
        cost[METAL[saddle['tier']]] = share(health, 16, 2, 12)
    if saddle['seat'] in FRAME:
        item, count = FRAME[saddle['seat']]
        cost[item] = count
    cost[f'{NS}:plant_fiber'] = share(health, 12, 2, 12)
    return cost


def saddles():
    """Every species' saddle entry, by category, the lesser animals first."""
    concept = load(ARK / 'design/showcase/saddles.json')['species']
    out = {cid: [] for cid, *_ in CATEGORIES}
    for order, species in enumerate(load(ARK / 'design/showcase/species.json')['species']):
        saddle = concept[species['id']]
        out[category_of(species)].append((level_of(species), order, species['id'], saddle,
                                          {'item': f"{NS}:{species['id']}_saddle", 'level': level_of(species),
                                           'cost': cost_of(species, saddle)}))
    return {cid: sorted(entries, key=lambda e: e[:2]) for cid, entries in out.items()}


def dump(value):
    return json.dumps(value, ensure_ascii=False)


def write_design(found):
    design = load(DESIGN)
    own = {cid for cid, *_ in CATEGORIES}
    categories = [c for c in design['categories'] if c['id'] not in own]
    for cid, title, colour, icon, after in CATEGORIES:
        categories.append({'id': cid, 'title': title, 'icon': f'{NS}:{icon}_spawn_egg', 'color': colour,
                           'level': min(e[0] for e in found[cid]), 'after': after, 'items': [e[4] for e in found[cid]]})
    lines = ['{'] + [f'  {dump(key)}: {dump(value)},' for key, value in design.items() if key != 'categories']
    lines.append('  "categories": [')
    for index, category in enumerate(categories):
        end = '' if index == len(categories) - 1 else ','
        head = ', '.join(f'{dump(k)}: {dump(v)}' for k, v in category.items() if k != 'items')
        if not category.get('items'):
            lines.append('    {' + head + '}' + end)
            continue
        lines += ['    {', f'      {head},', '      "items": [']
        lines += [f'        {dump(item)}' + (',' if item is not category['items'][-1] else '') for item in category['items']]
        lines += ['      ]', '    }' + end]
    lines += ['  ]', '}']
    DESIGN.write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')


def write_sprites(found):
    items = ASSETS / NS / 'textures/item'
    for entries in found.values():
        for _, _, species, saddle, _ in entries:
            shutil.copyfile(SPRITES / f"{saddle['tier']}_{SEAT_SPRITE[saddle['seat']]}_saddle.png", items / f'{species}_saddle.png')
    vanilla = ASSETS / 'minecraft/textures/item'
    vanilla.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(SPRITES / 'hide.png', vanilla / 'leather.png')


def main():
    found = saddles()
    write_design(found)
    write_sprites(found)
    for cid, title, *_ in CATEGORIES:
        print(f'{title}: ' + ', '.join(f'{species} {level}' for level, _, species, _, _ in found[cid]))
    print(sum(map(len, found.values())), 'saddles')


if __name__ == '__main__':
    main()
