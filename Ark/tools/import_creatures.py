"""Import only runtime clips; normalize geometry and position tracks together. Originals are read-only."""
from pathlib import Path
import hashlib
import json
import shutil
import copy
from expansion_catalog import EXPANSION
from collection_catalog import COLLECTION, import_clips

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/arksurvivalreturns'
# source folder, runtime id, body height in blocks, idle / locomotion / attack clips
SPECIES = [
    ('Piterodon', 'pteranodon', 1.2, 'Ptero-Ground-Idle', 'Ptero-Ground-Move-Fwd', 'Ptero-Ground-Attack'),
    ('Velociraptor', 'velociraptor', 1.5, 'Raptor-Idle', 'Raptor-Move-Fwd', 'Raptor-Attack'),
    ('Argentavis', 'argentavis', 1.6, 'Argentavis-Ground-Idle', 'Argentavis-Ground-Move-Fwd', 'Argentavis-Ground-Attack'),
    ('Triceratops', 'triceratops', 2.5, 'Trike-Idle', 'Trike-Move-Fwd', 'Trike-Attack-Horn'),
    ('Therezinosaur', 'therizinosaurus', 3.0, 'Therizinosaurus-Idle', 'Therizinosaurus-Move-Fwd', 'Therizinosaurus-Attack-Claw'),
    ('Brontosaur', 'brontosaurus', 5.5, 'Sauropod-Idle', 'Sauropod-Move-Fwd', 'Sauropod-Attack-FootStomp'),
    ('Tyranosaur', 'tyrannosaurus', 4.5, 'Rex-Idle', 'Rex-Move-Fwd', 'Rex-Bite2'),
    ('Giganotosaur', 'giganotosaurus', 5.0, 'Giganotosaurus-Idle', 'Giganotosaurus-Move-Fwd', 'Giganotosaurus-Attack-Bite'),
    ('Titanosaur', 'titanosaur', 7.0, 'Titanosaur-Idle', 'Titanosaur-Move-Fwd', 'Titanosaur-Attack-Footstomp'),
]

SIZE_MULTIPLIERS = {'titanosaur': 6, 'giganotosaurus': 3, 'tyrannosaurus': 3, 'therizinosaurus': 2}
BEHAVIOR_CLIPS = {
    'pteranodon': ['Ptero-Ground-Eat', 'Ptero-Ground-Startled', 'Ptero-Fly-Fwd', 'Ptero-Fly-Hover', 'Ptero-Fly-Attack-Swoop-Loop', 'Ptero-Fly-Attack-Bite', 'Ptero-Fly-Attack-Swoop-Out', 'Ptero-Land', 'Ptero-Take-Off'],
    'velociraptor': ['Raptor-Charge-Fwd', 'Raptor-Eat-Additive', 'Raptor-Call'],
    'argentavis': ['Argentavis-Ground-Eat', 'Argentavis-Ground-Startled', 'Argentavis-Fly-Fwd', 'Argentavis-Fly-Hover', 'Argentavis-Fly-Attack-Claw', 'Argentavis-Fly-Attack-Swoop-Out', 'Argentavis-Land', 'Argentavis-Take-Off'],
    'triceratops': ['Trike-Charge-Fwd', 'Trike-Graze', 'Trike-Startled', 'Trike-Sleeping'],
    'therizinosaurus': ['Therizinosaurus-Charge-Fwd', 'Therizinosaurus-Eat', 'Therizinosaurus-Startled'],
    'brontosaurus': ['Sauropod-Charge-Fwd', 'Sauropod-Graze', 'Sauropod-Startled-Lft'],
    'tyrannosaurus': ['Rex-Charge-Fwd', 'Rex-Eat-Additive', 'Rex-Roar', 'Rex-Sleeping'],
    'giganotosaurus': ['Giganotosaurus-Charge-Fwd', 'Giganotosaurus-Eat', 'Giganotosaurus-Roar'],
    'titanosaur': ['Titanosaur-Charge-Fwd', 'Titanosaur-Eat', 'Titanosaur-Startled-Lft'],
}
AUTHORED_SLEEP = {'velociraptor', 'giganotosaurus', 'therizinosaurus', 'brontosaurus', 'titanosaur'}
for entry in EXPANSION:
    identifier=entry['id']
    SPECIES.append(tuple(entry[k] for k in ('folder','id','height','idle','walk','attack')))
    SIZE_MULTIPLIERS[identifier]=entry['size']
    BEHAVIOR_CLIPS[identifier]=[entry[k] for k in ('run','food','warning')]
    AUTHORED_SLEEP.add(identifier)
SPECIES = [(folder, identifier, height * SIZE_MULTIPLIERS.get(identifier, 2), *clips, *BEHAVIOR_CLIPS[identifier],
            *(['Ark-Sleep'] if identifier in AUTHORED_SLEEP else []))
           for folder, identifier, height, *clips in SPECIES]
# The collection catalog already names its final height and complete clip set.
for entry in COLLECTION:
    SPECIES.append((entry['folder'], entry['id'], entry['height'], *import_clips(entry)))

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, separators=(',', ':')), encoding='utf-8')

def scale_track(value, scale):
    if isinstance(value, (float, int)):
        return round(value * scale, 6)
    if isinstance(value, list):
        return [scale_track(v, scale) for v in value]
    if isinstance(value, dict):
        return {k: v if k in ('lerp_mode',) else scale_track(v, scale) for k, v in value.items()}
    raise ValueError(f'Non-numeric position expression needs manual conversion: {value}')

def sleep_pose(idle, model, identifier):
    """Authored quiet standing sleep: stable feet, lowered head, closed eyes and slow breathing.

    Keep the first imported idle pose so we retain the skeleton's bind-pose corrections.
    This is deliberately not an unconscious/torpor animation or a claimed lie-down clip.
    """
    tracks = {}
    for name, bone in idle['bones'].items():
        tracks[name] = {kind: {'0': copy.deepcopy(values[min(values, key=float)])}
                        for kind, values in bone.items() if kind in ('position', 'rotation', 'scale')}
    available={bone['name'] for bone in model['bones']}
    head=next((n for n in ['Cnt_Head_JNT_SKL','c_head','Head_M','head'] if n in available),None)
    if head is not None:
        rotation = tracks.setdefault(head, {}).get('rotation', {'0': [0, 0, 0]})['0']
        tucked = [rotation[0] + 12, rotation[1], rotation[2]]
        breath = [tucked[0] - 1, tucked[1], tucked[2]]
        tracks[head]['rotation'] = {'0': tucked, '2': breath, '4': tucked}
    for bone in model['bones']:
        name = bone['name']
        lower = name.lower()
        if 'eye' in lower and not any(part in lower for part in ('upper', 'lower', 'eyelid')):
            tracks.setdefault(name, {})['scale'] = {'0': [0.02, 0.02, 0.02]}
    return {'loop': True, 'animation_length': 4, 'bones': tracks}

def main():
    report = []
    for folder, identifier, height, *clips in SPECIES:
        source = ROOT.parent / 'Creatures' / folder
        geo_path = next((source / 'geo').glob('*.json'))
        anim_path = next((source / 'animations').glob('*.json'))
        texture_path = next((source / 'textures/entity').glob('*.png'))
        geometry = json.loads(geo_path.read_text())
        model = geometry['minecraft:geometry'][0]
        desc = model['description']
        # Exporter bounds contain twice the mesh height plus two blocks of padding.
        source_height = (desc['visible_bounds_height'] - 2) / 2
        factor = height / source_height
        floor = desc['visible_bounds_offset'][1] - source_height / 2
        shift = -floor * 16 * factor
        roots = {b['name'] for b in model['bones'] if 'parent' not in b}
        for bone in model['bones']:
            for obj in [bone, *bone.get('cubes', [])]:
                for key in ('pivot', 'origin', 'size'):
                    if key in obj:
                        obj[key] = [round(x * factor, 6) for x in obj[key]]
                        if key != 'size': obj[key][1] = round(obj[key][1] + shift, 6)
                if 'inflate' in obj: obj['inflate'] *= factor
        desc['identifier'] = 'geometry.' + identifier
        desc['visible_bounds_width'] *= factor
        desc['visible_bounds_height'] = height * 2 + 2
        desc['visible_bounds_offset'] = [0, height / 2, 0]
        source_animations = json.loads(anim_path.read_text())['animations']
        animations = {}
        for index, name in enumerate(clips):
            if name == 'Ark-Sleep':
                animations[name] = sleep_pose(animations[clips[0]], model, identifier)
                continue
            clip = source_animations[name]
            clip['loop'] = index != 2 and not any(word in name for word in ('Startled', 'Roar', 'Call', 'Attack-Bite', 'Attack-Claw', 'Swoop-Out', '-Land', 'Take-Off'))
            for bone_name, tracks in clip.get('bones', {}).items():
                if 'position' in tracks:
                    tracks['position'] = scale_track(tracks['position'], factor)
                    if bone_name in roots:
                        # AI owns travel through the world; retain vertical root motion only.
                        for value in tracks['position'].values():
                            if isinstance(value, list): value[0] = value[2] = 0.0
            animations[name] = clip
        write(ASSETS / f'geckolib/models/entity/{identifier}.geo.json', geometry)
        write(ASSETS / f'geckolib/animations/entity/{identifier}.animation.json',
              {'format_version': '1.8.0', 'geckolib_format_version': 2, 'animations': animations})
        destination = ASSETS / f'textures/entity/{identifier}.png'
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(texture_path, destination)
        report.append({'id': identifier, 'source': f'Creatures/{folder}', 'height_blocks': height,
                       'scale': factor, 'clips': clips,
                       'source_sha256': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in (geo_path, anim_path, texture_path)}})
    write(ROOT / 'docs/creature-import.json', report)
    print(f'Imported {len(report)} creatures, {sum(len(row[3:]) for row in SPECIES)} clips, original palettes; source projects unchanged.')

if __name__ == '__main__': main()
