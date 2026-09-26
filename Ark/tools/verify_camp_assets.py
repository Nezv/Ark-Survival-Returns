"""Check native camp model references and all multipart display combinations."""
import itertools
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'src/main/resources/assets/arksurvivalreturns'
GENERATED = ROOT / 'src/generated/resources/assets/arksurvivalreturns'


def verify_camp_assets():
    visited = set()

    def check_model(identifier):
        if identifier.startswith('minecraft:') or identifier in visited:
            return
        visited.add(identifier)
        namespace, name = identifier.split(':')
        assert namespace == 'arksurvivalreturns', identifier
        path = MAIN / 'models' / (name + '.json')
        if not path.exists(): path = GENERATED / 'models' / (name + '.json')
        data = json.loads(path.read_text())
        if 'parent' in data: check_model(data['parent'])
        for value in data.get('textures', {}).values():
            if value.startswith('#'): continue
            ns, tex = value.split(':')
            if ns == 'arksurvivalreturns':
                with Image.open(MAIN / 'textures' / (tex + '.png')) as image:
                    assert image.size == (32, 32), tex
        for element in data.get('elements', []):
            assert all(-16 <= a < b <= 32 for a, b in zip(element['from'], element['to'])), (name, element['name'])
            if 'rotation' in element:
                assert element['rotation']['angle'] in (-45, -22.5, 0, 22.5, 45), name
            for face in element['faces'].values():
                assert face['texture'][1:] in data['textures'], (name, face)
                assert all(0 <= c <= 16 for c in face['uv']), (name, face)

    def matches(when, state):
        return all(state[key] in value.split('|') for key, value in when.items())

    for path in (MAIN / 'models/block/camp').glob('*.json'):
        check_model('arksurvivalreturns:block/camp/' + path.stem)
    checked = 0
    for path in (GENERATED / 'blockstates').glob('*.json'):
        name = path.stem
        if name not in ('bedroll', 'drying_rack', 'cooking_pot', 'trough') and not name.endswith('_trough'): continue
        data = json.loads(path.read_text())
        if 'variants' in data:
            for value in data['variants'].values(): check_model(value['model'])
            assert len(data['variants']) == 8, name
        else:
            parts = data['multipart']
            for part in parts: check_model(part['apply']['model'])
            domains = {'facing': ['north','east','south','west']}
            if name == 'drying_rack': domains.update(half=['lower','upper'], food=['meat','fish','berries'], hanging=['0','1','2','3'], ready=['false','true'])
            else: domains['filled'] = ['false','true']
            for combination in itertools.product(*domains.values()):
                state = dict(zip(domains, combination))
                active = [part for part in parts if matches(part['when'], state)]
                if name == 'drying_rack':
                    expected = 1 if state['half'] == 'upper' else 1 + int(state['hanging']) + (state['ready'] == 'true')
                else:
                    expected = 1 + (state['filled'] == 'true')
                assert len(active) == expected, (name, state, len(active))
                checked += 1
    print(f'PASS: {len(visited)} camp models; textures, cuboids, rotations and {checked} multipart states resolve.')


if __name__ == '__main__': verify_camp_assets()
