"""Validate the actual packaged asset graph and read-only creature import provenance."""
import hashlib
import json
import math
from pathlib import Path
from PIL import Image
from collection_catalog import COLLECTION
from verify_camp_assets import verify_camp_assets
from import_creatures import ROOT, ASSETS, SPECIES, source_files

# Runtime texture contract, mirroring client/CreatureModel.java: a creature renders one of five
# variant textures chosen from its UUID, so every <species>_<variant>.png must match the geometry's
# declared texture size. The bare <species>.png is the offline preview palette (eight 8x8 swatches)
# consumed by tools/preview_runtime_creatures.py, not a render texture.
VARIANTS = ('ivory', 'darken', 'emerald', 'midnight', 'burgundy')
PALETTE_SIZE = (64, 8)

# Eye geometry used by the emissive night layer, mirroring Species.eyeBones().
# Deinosuchus, Dragon and Mosasaurus own eye bones without usable cube geometry, so they are excluded
# there and here: an empty bone would render nothing.
EYE_PAIRS = {'velociraptor': ('Lft_Eye_JNT_SKL', 'Rht_Eye_JNT_SKL'),
             'tyrannosaurus': ('Lft_Eye_JNT_SKL', 'Rht_Eye_JNT_SKL'),
             'ceratosaurus': ('Eye_L', 'Eye_R'), 'acrocanthosaurus': ('Eye_L', 'Eye_R'),
             'argentavis': ('l_Eye_01', 'r_Eye_01'), 'ravager': ('l_Eye_01', 'r_Eye_01'),
             'archaeopteryx': ('l_Eye', 'r_Eye')}
NO_EYE_GEOMETRY = {'lystrosaurus', 'cnidaria', 'tusoteuthis', 'kaprosuchus', 'sarco', 'terrorbird',
                   'deinosuchus', 'dragon', 'mosasaurus'}
FLYERS = ['pteranodon', 'argentavis'] + [entry['id'] for entry in COLLECTION if entry['realm'] == 'AIR']
# Only predators receive the emissive layer, so only predators must own usable eye geometry.
PREDATORS = {'velociraptor', 'tyrannosaurus', 'giganotosaurus'} | {entry['id'] for entry in COLLECTION if entry['predator']}


def eye_bones(identifier):
    if identifier in NO_EYE_GEOMETRY:
        return ()
    return EYE_PAIRS.get(identifier, ('l_eye', 'r_eye'))


def check_creature(folder, identifier, height, clips, report, check, fail):
    """Validate one species' model, animations, textures and unchanged import provenance."""
    geo = json.loads((ASSETS/f'geckolib/models/entity/{identifier}.geo.json').read_text())['minecraft:geometry'][0]
    anim = json.loads((ASSETS/f'geckolib/animations/entity/{identifier}.animation.json').read_text())['animations']
    check(set(anim) == set(clips), f'{identifier}: clips {sorted(anim)} do not match {sorted(clips)}')
    bones = {bone['name'] for bone in geo['bones']}
    check(len(bones) == len(geo['bones']), f'{identifier}: duplicate bone names')
    for bone in geo['bones']:
        check('parent' not in bone or bone['parent'] in bones, f'{identifier}: unknown parent of {bone["name"]}')
        for cube in bone.get('cubes', []):
            check(all(math.isfinite(x) and x > 0 for x in cube['size']),
                  f'{identifier}: invalid cube size {cube["size"]} in {bone["name"]}')
    for clip in anim.values():
        check(set(clip['bones']) <= bones, f'{identifier}: clip targets unknown bones')
        check(clip['animation_length'] > 0, f'{identifier}: non-positive animation length')
    if 'Ark-Sleep' in clips:
        check(anim['Ark-Sleep']['loop'] and anim['Ark-Sleep']['animation_length'] == 4,
              f'{identifier}: Ark-Sleep must loop for four seconds')
    # A glowing eye bone must exist and carry geometry, or the night layer would light nothing.
    for name in eye_bones(identifier):
        eye = next((b for b in geo['bones'] if b['name'] == name), None)
        check(eye is not None, f'Missing eye geometry: {identifier}/{name}')
        if identifier in PREDATORS and eye is not None:
            check(eye.get('cubes'), f'Glowing eye bone has no geometry: {identifier}/{name}')
    expected = (geo['description']['texture_width'], geo['description']['texture_height'])
    for variant in VARIANTS:
        path = ASSETS/f'textures/entity/{identifier}_{variant}.png'
        if not path.is_file():
            fail(f'Missing variant texture: {path.name}')
            continue
        with Image.open(path) as image:
            check(image.size == expected, f'{path.name}: {image.size}, expected {expected}')
    palette = ASSETS/f'textures/entity/{identifier}.png'
    if not palette.is_file():
        fail(f'Missing preview palette: {palette.name}')
    else:
        with Image.open(palette) as image:
            check(image.size == PALETTE_SIZE, f'{palette.name}: {image.size}, expected {PALETTE_SIZE} preview palette')
    entry = next(r for r in report if r['id'] == identifier)
    _, _, sources = source_files(folder)
    for name, checksum in entry['source_sha256'].items():
        path = sources.get(name)
        if path is None or not path.is_file():
            fail(f'Missing original: {name}')
            continue
        check(hashlib.sha256(path.read_bytes()).hexdigest() == checksum, f'Original changed: {path}')
    check(entry['height_blocks'] == height, f'{identifier}: imported height {entry["height_blocks"]} != {height}')


def main():
    generated = ROOT / 'src/generated/resources'
    failures = []

    def check(condition, message):
        if not condition:
            failures.append(str(message))

    def fail(message):
        failures.append(str(message))

    for path in list((ROOT/'src/main/resources').rglob('*.json')) + list(generated.rglob('*.json')):
        try:
            json.loads(path.read_text(encoding='utf-8'))
        except Exception as error:
            fail(f'Invalid JSON: {path.relative_to(ROOT)}: {error!r}')
    report = json.loads((ROOT/'docs/creature-import.json').read_text())
    for folder, identifier, height, *clips in SPECIES:
        try:
            check_creature(folder, identifier, height, clips, report, check, fail)
        except Exception as error:
            fail(f'{identifier}: unexpected {error!r}')
    # Spawn eggs, nest eggs, the four berries, the debug tool, the tranquilizer arrow, the companion
    # whistle, the field journal, five camp items, two cargo harnesses, the recovery cache marker,
    # three homestead items (trough, drying rack, dried ration), two medicine items, three kitchen
    # items (cooking pot, hearty stew, trail mix) and nine additional wood variants of the feeding trough.
    definitions = list((generated/'assets/arksurvivalreturns/items').glob('*.json'))
    expected_items = len(SPECIES) + len(FLYERS) + 57  # 36 camp/farm/taming items + 21 prehistoric (rocks, tools, fire, forge, meats)
    check(len(definitions) == expected_items, f'{len(definitions)} item definitions, expected {expected_items}')
    for definition in definitions:
        try:
            body = json.loads(definition.read_text())['model']
            if body.get('type') != 'minecraft:model':
                continue  # Shaped definitions such as the debug scope select a vanilla model per context.
            model_id = body['model'].split(':')[1]
            model = json.loads((generated/f'assets/arksurvivalreturns/models/{model_id}.json').read_text())
            texture = model.get('textures', {}).get('layer0')
            if texture is None:
                continue  # Block-parent item models are checked through their block assets below.
            if texture.startswith('minecraft:'):
                continue
            tex = texture.split(':')[1]
            with Image.open(ASSETS/f'textures/{tex}.png') as image:
                check(image.mode == 'RGBA' and image.size == (32, 32), f'{tex}: {image.mode} {image.size}, expected RGBA 32x32')
                check(image.getextrema()[3] == (0, 255), f'{tex}: alpha range {image.getextrema()[3]}')
        except Exception as error:
            fail(f'{definition.name}: unexpected {error!r}')
    # Vanilla spawns would bypass the danger gate and group replenishment, so biome modifiers may
    # only add Ark species; removing vanilla spawns stays a theme concern.
    for path in (generated/'data/arksurvivalreturns/neoforge/biome_modifier').glob('*.json'):
        try:
            body = json.loads(path.read_text())
        except Exception as error:
            fail(f'{path.name}: invalid JSON: {error!r}')
            continue
        if body.get('type') != 'neoforge:add_spawns':
            continue
        spawner = body.get('spawners', {}).get('type', '')
        check(spawner.startswith('arksurvivalreturns:'), f'{path.name} adds a vanilla spawner: {spawner}')
    for _, identifier, *_ in SPECIES:
        path = generated/f'data/arksurvivalreturns/tags/worldgen/biome/spawns/{identifier}.json'
        if not path.is_file():
            fail(f'Missing spawn tag: {identifier}')
            continue
        check(json.loads(path.read_text())['values'], f'Empty spawn tag: {identifier}')
    # Every flying species owns a complete nest: block state, both models, loot table and egg item.
    for identifier in FLYERS:
        for relative in (f'assets/arksurvivalreturns/blockstates/{identifier}_nest.json',
                         f'assets/arksurvivalreturns/models/block/{identifier}_nest.json',
                         f'assets/arksurvivalreturns/models/block/{identifier}_nest_empty.json',
                         f'assets/arksurvivalreturns/items/{identifier}_egg.json',
                         f'assets/arksurvivalreturns/models/item/{identifier}_egg.json',
                         f'data/arksurvivalreturns/loot_table/blocks/{identifier}_nest.json'):
            check((generated/relative).is_file(), f'Missing nest asset: {relative}')
    # Camp blocks: the bedroll and the recovery cache marker own a state, a model and a loot table.
    for identifier in ('bedroll', 'recovery_cache'):
        for relative in (f'assets/arksurvivalreturns/blockstates/{identifier}.json',
                         f'assets/arksurvivalreturns/models/block/{identifier}.json',
                         f'data/arksurvivalreturns/loot_table/blocks/{identifier}.json'):
            check((generated/relative).is_file(), f'Missing block asset: {relative}')
    surfaces = json.loads((generated/'data/arksurvivalreturns/tags/block/spawn_surfaces.json').read_text())['values']
    check({'minecraft:grass_block', 'minecraft:podzol', 'minecraft:mycelium'} <= set(surfaces),
          'Spawn surfaces tag is incomplete')
    for locale in ['en_us', 'pt_br']:
        lang = json.loads((generated/f'assets/arksurvivalreturns/lang/{locale}.json').read_text(encoding='utf-8'))
        check(lang['chat.arksurvivalreturns.biome'].count('%s') == 4, f'{locale}: biome message placeholders')
        check('chat.arksurvivalreturns.biome_unrated' in lang, f'{locale}: missing unrated biome message')
        for _, identifier, *_ in SPECIES:
            check(f'entity.arksurvivalreturns.{identifier}' in lang, f'Missing name: {locale}/{identifier}')
        for identifier in FLYERS:
            check(f'block.arksurvivalreturns.{identifier}_nest' in lang, f'Missing nest name: {locale}/{identifier}')
        check('block.arksurvivalreturns.bedroll' in lang, f'{locale}: missing bedroll name')
        check('block.arksurvivalreturns.recovery_cache' in lang, f'{locale}: missing recovery cache name')
    try:
        verify_camp_assets()
    except Exception as error:
        fail(f"Camp assets: {error!r}")
    if failures:
        for failure in failures:
            print(f'FAIL: {failure}')
        raise SystemExit(f'{len(failures)} asset verification failure(s)')
    print(f'PASS: {len(SPECIES)} creatures, {sum(len(row[3:]) for row in SPECIES)} valid clips, {len(FLYERS)} nests, '
          f'unchanged originals, {len(definitions)} item definitions, JSON and spawn resource references.')

if __name__ == '__main__': main()
