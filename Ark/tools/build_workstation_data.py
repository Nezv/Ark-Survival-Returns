"""Build only rule-added families and the grid removal list of the benches in the game. Designs stay in design/.

processResources copies the authored documents verbatim and packages these generated additions.
The complete trees in fixtures/ are also the Node oracle inputs for the Java golden tests.
"""
import argparse
import copy
import json
import math
from pathlib import Path

import workstation_plan as wp
from PIL import Image

# The benches whose graph is in the game, the inventory's own among them: no crafting grid is left to the player.
PHASE_A = wp.SHIPPED


def build(output):
    if not wp.JAR.is_file():
        raise FileNotFoundError(f'Workstation plan needs the Minecraft recipe jar: {wp.JAR}')
    records, designs, _ = wp.plan()
    source = wp.Source()
    def recipe_json(recipe_id):  # vanilla, Ark or one of the pack's mods
        return source.recipe_json(recipe_id) or {}
    trees = wp.compile_benches(records, designs)
    data = output / 'resources/data' / wp.NS
    fixtures = output / 'fixtures'
    fixtures.mkdir(parents=True, exist_ok=True)
    rules_dir = data / 'workstation_rules'
    rules_dir.mkdir(parents=True, exist_ok=True)
    for bench in PHASE_A:
        tree = trees[bench]
        (fixtures / f'{bench}.json').write_text(json.dumps(tree), encoding='utf-8')
        # Compile the same tree without explicitly designed recipes. Only the rule additions ship here;
        # authored entries, levels, ordering and appearance are read from the verbatim design resource.
        empty = copy.deepcopy(designs)
        for cat in empty[bench]['categories']:
            cat['items'] = [{k: v for k, v in e.items() if k != 'items'}
                            for e in cat.get('items', []) if 'group' in e]
        extras = wp.compile_benches(records, empty)[bench]
        # A designed item must never reappear as a ruled family (including user decisions).
        designed = {e['item'] for _, e in wp.design_entries(designs[bench]) if 'item' in e}
        for cat in extras['categories']:
            for e in cat['items']:
                for family in e.get('items', [e]):
                    if 'variants' in family:
                        family['variants'] = [v for v in family['variants'] if v['item'] not in designed]
                        for variant in family['variants']:
                            original = recipe_json(variant['recipe'])
                            result = original.get('result')
                            if isinstance(result, dict) and result.get('components'):
                                variant['output'] = json.dumps(result)
                if 'group' in e:
                    e['items'] = [f for f in e['items'] if f.get('variants')]
            cat['items'] = [e for e in cat['items'] if e.get('variants') or e.get('items')]
        (rules_dir / f'{bench}.json').write_text(json.dumps(extras), encoding='utf-8')
    # Remove every original recipe assigned to a bench in the game, including changed/deleted and special grid
    # recipes (banner copying etc.); the inventory's graph took the field crafts and the planks over from the 2x2
    # grid. Phase B recipes stay loaded: the inventory's Bench work makes the small ones until their bench is built.
    removed = sorted({r['id'] for r in records if r.get('bench') in PHASE_A})
    (data / 'workstation_grid_removals.json').write_text(json.dumps(removed, indent=2) + '\n', encoding='utf-8')
    # Exact raster mask of workstation_ui.js shape(). White texels take the runtime palette tint.
    shapes = output / 'resources/assets' / wp.NS / 'textures/gui/workstation/shapes'
    shapes.mkdir(parents=True, exist_ok=True)
    for radius in range(1, 33):
        for thick in (0, 1, 2):
            image = Image.new('RGBA', (2 * radius, 2 * radius))
            for y in range(2 * radius):
                for x in range(2 * radius):
                    distance = math.sqrt((x + .5 - radius) ** 2 + (y + .5 - radius) ** 2)
                    if distance <= radius - .2 and not (thick and distance <= radius - thick - .2):
                        image.putpixel((x, y), (255, 255, 255, 255))
            image.save(shapes / f'{radius}_{thick}.png')
    # Metadata is taken from the same frame generator as the showcase, so recent frame changes align.
    import build_workstation_crests as frames
    assets = output / 'resources/assets' / wp.NS / 'workstation'
    assets.mkdir(parents=True, exist_ok=True)
    (assets / 'frame.json').write_text(json.dumps({'margin': frames.MARGIN, 'top': frames.PANEL_TOP,
             'bottom': frames.PANEL_BOTTOM, 'width': frames.FRAME_W, 'height': frames.FRAME_H}), encoding='utf-8')
    # Use the showcase's tag labels and representative icons, including its common-tag overrides.
    import showcase_recipes as display
    selectors = set()
    def collect(value):
        if isinstance(value, dict):
            for key, child in value.items():
                collect(key)
                collect(child)
        elif isinstance(value, list):
            for child in value:
                collect(child)
        elif isinstance(value, str) and value.startswith('#') and ':' in value:
            selectors.add(value)
    for bench in PHASE_A:
        collect(trees[bench])
    labels = display.names(source.jar)
    tags = {tag: {'name': display.item_name(tag, labels, source),
                  'item': display.representative(tag, source)} for tag in sorted(selectors)}
    (assets / 'tags.json').write_text(json.dumps(tags), encoding='utf-8')
    print(f'{len(PHASE_A)} benches in the game: {len(removed)} grid recipes removed, rule supplements generated')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('output', type=Path)
    build(parser.parse_args().output)
