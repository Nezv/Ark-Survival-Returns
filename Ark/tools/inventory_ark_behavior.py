"""Read installed ASE package name tables and creature animation names; never execute or modify game assets."""
from pathlib import Path
import hashlib
import json
import struct
from import_creatures import ROOT, SPECIES

CONTENT = Path(r'C:/Program Files (x86)/Steam/steamapps/common/ARK/ShooterGame/Content')
DINOS = ['Ptero', 'Raptor', 'Argentavis', 'Trike', 'Therizinosaurus', 'Sauropod', 'Rex', 'Giganotosaurus', 'Titanosaur']

def name_table(path):
    data = path.read_bytes()
    if struct.unpack_from('<4i', data, 4) != (-3, 864, 405, 10):
        raise ValueError(f'Unsupported package header: {path.name}')
    count, cursor = struct.unpack_from('<ii', data, 41)
    if not 0 < count < 100000 or not 0 < cursor < len(data):
        raise ValueError(f'Invalid name table: {path.name}')
    names = []
    for _ in range(count):
        size = struct.unpack_from('<i', data, cursor)[0]; cursor += 4
        if not 0 < abs(size) <= 65536: raise ValueError(f'Invalid string: {path.name}')
        length = abs(size) * (2 if size < 0 else 1)
        value = data[cursor:cursor + length].decode('utf-16-le' if size < 0 else 'utf-8').rstrip('\0')
        names.append(value); cursor += length
    return {'asset': path.relative_to(CONTENT).as_posix(), 'sha256': hashlib.sha256(data).hexdigest(),
            'bytes': len(data), 'names': names}

def main():
    paths = list((CONTENT / 'PrimalEarth/CoreAI').glob('*.uasset'))
    for name in DINOS:
        root = CONTENT / 'PrimalEarth/Dinos' / name
        paths.extend(p for p in [root / f'{name}_AIController_BP.uasset', root / f'{name}_Character_BP.uasset'] if p.exists())
    records, errors = [], []
    for path in sorted(paths):
        try: records.append(name_table(path))
        except (ValueError, UnicodeError, struct.error) as error: errors.append(str(error))
    animations = []
    for folder, identifier, height, *runtime in SPECIES:
        path = next((ROOT.parent / 'Creatures' / folder / 'animations').glob('*.json'))
        available = json.loads(path.read_text())['animations']
        animations.append({'species': identifier, 'source_clips': sorted(available), 'runtime_clips': runtime, 'height_blocks': height})
    report = {'scope': 'Cooked package name-table metadata only. Names/references do not establish control flow, parameter values, or original source code.',
              'packages': records, 'errors': errors, 'animations': animations}
    target = ROOT / 'docs/ark-behavior-inventory.json'
    target.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'{len(records)} package name tables; {sum(len(a["source_clips"]) for a in animations)} available clips; {len(errors)} unsupported packages.')
    for r in records:
        if any(k in Path(r['asset']).stem for k in ['Dino_AIController_BP', 'DinoBehaviorTree', 'Rex_AIController_BP']):
            print(r['asset'], '\n  ', ', '.join(n for n in r['names'] if any(k.lower() in n.lower() for k in ['Aggro', 'Flee', 'Wander', 'Target', 'Attack', 'Blackboard', 'BehaviorTree'])))

if __name__ == '__main__': main()
