"""Validate the actual packaged asset graph and read-only creature import provenance."""
import hashlib
import json
import math
from pathlib import Path
from PIL import Image
from collection_catalog import COLLECTION
from import_creatures import ROOT, ASSETS, SPECIES

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


def main():
    generated = ROOT / 'src/generated/resources'
    for path in list((ROOT/'src/main/resources').rglob('*.json')) + list(generated.rglob('*.json')):
        json.loads(path.read_text(encoding='utf-8'))
    report = json.loads((ROOT/'docs/creature-import.json').read_text())
    for folder, identifier, height, *clips in SPECIES:
        geo = json.loads((ASSETS/f'geckolib/models/entity/{identifier}.geo.json').read_text())['minecraft:geometry'][0]
        anim = json.loads((ASSETS/f'geckolib/animations/entity/{identifier}.animation.json').read_text())['animations']
        assert set(anim) == set(clips), identifier
        bones = {bone['name'] for bone in geo['bones']}
        assert len(bones) == len(geo['bones'])
        for bone in geo['bones']:
            assert 'parent' not in bone or bone['parent'] in bones
            for cube in bone.get('cubes', []):
                assert all(math.isfinite(x) and x > 0 for x in cube['size'])
        for clip in anim.values():
            assert set(clip['bones']) <= bones
            assert clip['animation_length'] > 0
        if 'Ark-Sleep' in clips:
            assert anim['Ark-Sleep']['loop'] and anim['Ark-Sleep']['animation_length'] == 4
        # A glowing eye bone must exist and carry geometry, or the night layer would light nothing.
        for name in eye_bones(identifier):
            eye = next((b for b in geo['bones'] if b['name'] == name), None)
            assert eye is not None, f'Missing eye geometry: {identifier}/{name}'
            if identifier in PREDATORS:
                assert eye.get('cubes'), f'Glowing eye bone has no geometry: {identifier}/{name}'
        with Image.open(ASSETS/f'textures/entity/{identifier}.png') as image:
            assert image.size == (geo['description']['texture_width'], geo['description']['texture_height'])
        original = ROOT.parent/'Creatures'/folder
        entry = next(r for r in report if r['id'] == identifier)
        for name, checksum in entry['source_sha256'].items():
            path = next(original.rglob(name))
            assert hashlib.sha256(path.read_bytes()).hexdigest() == checksum, f'Original changed: {path}'
        assert entry['height_blocks'] == height
    # Spawn eggs, nest eggs, the four berries, the debug tool, the tranquilizer arrow, the companion
    # whistle, the field journal, five camp items and the recovery cache marker.
    expected_items = len(SPECIES) + len(FLYERS) + 14
    assert len(definitions) == expected_items, f'{len(definitions)} item definitions, expected {expected_items}'
    for definition in definitions:
        body = json.loads(definition.read_text())['model']
        if body.get('type') != 'minecraft:model':
            continue  # Shaped definitions such as the debug scope select a vanilla model per context.
        model_id = body['model'].split(':')[1]
        model = json.loads((generated/f'assets/arksurvivalreturns/models/{model_id}.json').read_text())
        texture = model.get('textures', {}).get('layer0')
        if texture is None:
            continue  # Block-parent item models are checked through their block assets below.
        if texture.startswith('minecraft:'):continue
        tex = texture.split(':')[1]
        with Image.open(ASSETS/f'textures/{tex}.png') as image:
            assert image.mode == 'RGBA' and image.size == (32,32)
            assert image.getextrema()[3] == (0,255), tex
    assert not list((generated/'data/arksurvivalreturns/neoforge/biome_modifier').glob('*.json')), 'Vanilla spawns bypass group replenishment'
    for _, identifier, *_ in SPECIES:
        path = generated/f'data/arksurvivalreturns/tags/worldgen/biome/spawns/{identifier}.json'
        assert json.loads(path.read_text())['values'], identifier
    # Every flying species owns a complete nest: block state, both models, loot table and egg item.
    for identifier in FLYERS:
        for relative in (f'assets/arksurvivalreturns/blockstates/{identifier}_nest.json',
                         f'assets/arksurvivalreturns/models/block/{identifier}_nest.json',
                         f'assets/arksurvivalreturns/models/block/{identifier}_nest_empty.json',
                         f'assets/arksurvivalreturns/items/{identifier}_egg.json',
                         f'assets/arksurvivalreturns/models/item/{identifier}_egg.json',
                         f'data/arksurvivalreturns/loot_table/blocks/{identifier}_nest.json'):
            assert (generated/relative).is_file(), f'Missing nest asset: {relative}'
    # Camp blocks: the bedroll and the recovery cache marker own a state, a model and a loot table.
    for identifier in ('bedroll', 'recovery_cache'):
        for relative in (f'assets/arksurvivalreturns/blockstates/{identifier}.json',
                         f'assets/arksurvivalreturns/models/block/{identifier}.json',
                         f'data/arksurvivalreturns/loot_table/blocks/{identifier}.json'):
            assert (generated/relative).is_file(), f'Missing block asset: {relative}'
    surfaces = json.loads((generated/'data/arksurvivalreturns/tags/block/spawn_surfaces.json').read_text())['values']
    assert {'minecraft:grass_block', 'minecraft:podzol', 'minecraft:mycelium'} <= set(surfaces)
    for locale in ['en_us', 'pt_br']:
        lang = json.loads((generated/f'assets/arksurvivalreturns/lang/{locale}.json').read_text(encoding='utf-8'))
        assert lang['chat.arksurvivalreturns.biome'].count('%s') == 4
        assert 'chat.arksurvivalreturns.biome_unrated' in lang
        for _, identifier, *_ in SPECIES:
            assert f'entity.arksurvivalreturns.{identifier}' in lang, f'Missing name: {locale}/{identifier}'
        for identifier in FLYERS:
            assert f'block.arksurvivalreturns.{identifier}_nest' in lang, f'Missing nest name: {locale}/{identifier}'
        assert f'block.arksurvivalreturns.bedroll' in lang
        assert f'block.arksurvivalreturns.recovery_cache' in lang
    print(f'PASS: {len(SPECIES)} creatures, {sum(len(row[3:]) for row in SPECIES)} valid clips, {len(FLYERS)} nests, '
          f'unchanged originals, {len(definitions)} item definitions, JSON and spawn resource references.')

if __name__ == '__main__': main()
