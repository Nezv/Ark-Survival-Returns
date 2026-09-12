"""Validate the actual packaged asset graph and read-only creature import provenance."""
import hashlib
import json
import math
from pathlib import Path
from PIL import Image
from import_creatures import ROOT, ASSETS, SPECIES, AUTHORED_SLEEP

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
        if identifier in AUTHORED_SLEEP:
            assert anim['Ark-Sleep']['loop'] and anim['Ark-Sleep']['animation_length'] == 4
        if identifier in ('velociraptor', 'tyrannosaurus', 'giganotosaurus'):
            eye_names = {'l_eye', 'r_eye'} if identifier == 'giganotosaurus' else {'Lft_Eye_JNT_SKL', 'Rht_Eye_JNT_SKL'}
            for name in eye_names:
                eye = next(b for b in geo['bones'] if b['name'] == name)
                assert len(eye.get('cubes', [])) == 2, f'Expected eye-only geometry: {identifier}/{name}'
        with Image.open(ASSETS/f'textures/entity/{identifier}.png') as image:
            assert image.size == (geo['description']['texture_width'], geo['description']['texture_height'])
        original = ROOT.parent/'Creatures'/folder
        entry = next(r for r in report if r['id'] == identifier)
        for name, checksum in entry['source_sha256'].items():
            path = next(original.rglob(name))
            assert hashlib.sha256(path.read_bytes()).hexdigest() == checksum, f'Original changed: {path}'
        assert entry['height_blocks'] == height
    definitions = list((generated/f'assets/arksurvivalreturns/items').glob('*.json'))
    assert len(definitions) == len(SPECIES) + 4 + 2
    for definition in definitions:
        model_id = json.loads(definition.read_text())['model']['model'].split(':')[1]
        model = json.loads((generated/f'assets/arksurvivalreturns/models/{model_id}.json').read_text())
        texture=model['textures']['layer0']
        if texture.startswith('minecraft:'):continue
        tex = texture.split(':')[1]
        with Image.open(ASSETS/f'textures/{tex}.png') as image:
            assert image.mode == 'RGBA' and image.size == (32,32)
            assert image.getextrema()[3] == (0,255), tex
    assert not list((generated/'data/arksurvivalreturns/neoforge/biome_modifier').glob('*.json')), 'Vanilla spawns bypass group replenishment'
    for _, identifier, *_ in SPECIES:
        path = generated/f'data/arksurvivalreturns/tags/worldgen/biome/spawns/{identifier}.json'
        assert json.loads(path.read_text())['values'], identifier
    surfaces = json.loads((generated/'data/arksurvivalreturns/tags/block/spawn_surfaces.json').read_text())['values']
    assert {'minecraft:grass_block', 'minecraft:podzol', 'minecraft:mycelium'} <= set(surfaces)
    for locale in ['en_us', 'pt_br']:
        lang = json.loads((generated/f'assets/arksurvivalreturns/lang/{locale}.json').read_text(encoding='utf-8'))
        assert lang['chat.arksurvivalreturns.biome'].count('%s') == 4
        assert 'chat.arksurvivalreturns.biome_unrated' in lang
    print(f'PASS: {len(SPECIES)} creatures, {sum(len(row[3:]) for row in SPECIES)} valid clips, unchanged originals, {len(definitions)} item definitions, JSON and spawn resource references.')

if __name__ == '__main__': main()
