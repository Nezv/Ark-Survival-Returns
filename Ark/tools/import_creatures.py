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
TEXTURE_VARIANTS = ('Ivory', 'Darken', 'Emerald', 'Midnight', 'Burgundy')
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
# Source torpor sequences, imported verbatim so knockout taming can use the authored collapse, loop,
# feeding and wake clips instead of a standing sleep pose. Rigs without a torpor sequence keep the
# existing fallback and are listed in docs/taming-animation-matrix.json as missing assets.
TORPOR_CLIPS = {
    'acrocanthosaurus':  ['Acro_Torp_In', 'Acro_Torp_Loop', 'Acro_Torp_Eat', 'Acro_Torp_Out'],
    'allosaurus':        ['Allosaurus-Torpid-In', 'Allosaurus-Torpid-Idle', 'Allosaurus-Torpid-Eat', 'Allosaurus-Torpid-Out-Tamed', 'Allosaurus-Torpid-Out-Wild'],
    'ankylosaurus':      ['Ankylo-Torpid-In', 'Ankylo-Torpid', 'Ankylo-Torpid-Eat', 'Ankylo-Torpid-Out-Tamed', 'Ankylo-Torpid-Out-Wild'],
    'archaeopteryx':     ['Archaeopteryx-Torpid-In', 'Archaeopteryx-Torpid-Idle', 'Archaeopteryx-Torpid-Eat', 'Archaeopteryx-Torpid-Out-Tamed', 'Archaeopteryx-Torpid-Out-Wild'],
    'argentavis':        ['Argentavis-Torpid-In', 'Argentavis-Torpid-Idle', 'Argentavis-Torpid-Eat', 'Argentavis-Torpid-Out-Tamed', 'Argentavis-Torpid-Out-Wild'],
    'brontosaurus':      ['Sauropod-Torpid-In', 'Sauropod-Torpid', 'Sauropod-Torpid-Eat', 'Sauropod-Torpid-Out-Tamed', 'Sauropod-Torpid-Out-Wild'],
    'carnotaurus':       ['Carno-Torpid-In', 'Carno-Torpid-Idle', 'Carno-Torpid-Eat', 'Carno-Torpid-Out-Tamed', 'Carno-Torpid-Out-Wild'],
    'deinosuchus':       ['Deinosuchus_Torp_In'],
    'dilophosaur':       ['Dilo-Torpid-In', 'Dilo-Torpid', 'Dilo-Torpid-Eat', 'Dilo-Torpid-Out-Tamed', 'Dilo-Torpid-Out-Wild'],
    'direwolf':          ['Direwolf-Torpid-In', 'Direwolf-Torpid-Idle', 'Direwolf-Torpid-Eat', 'Direwolf-Torpid-Out-Tamed', 'Direwolf-Torpid-Out-Wild'],
    'giganotosaurus':    ['Giganotosaurus-Torpid-In', 'Giganotosaurus-Torpid-Idle', 'Giganotosaurus-Torpid-Eat', 'Giganotosaurus-Torpid-Out-Tamed', 'Giganotosaurus-Torpid-Out-Wild'],
    'kaprosuchus':       ['Kaprosuchus-Torpid-In', 'Kaprosuchus-Torpid-Idle', 'Kaprosuchus-Torpid-Eat', 'Kaprosuchus-Torpid-Out-Tamed', 'Kaprosuchus-Torpid-Out-Wild'],
    'liopleurodon':      ['Liopleurodon-Torpid-In', 'Liopleurodon-Torpid-Idle', 'Liopleurodon-Torpid-Eat', 'Liopleurodon-Torpid-Out-Tamed', 'Liopleurodon-Torpid-Out-Wild'],
    'lystrosaurus':      ['Lystrosaurus-Torpid-In', 'Lystrosaurus-Torpid-Idle', 'Lystrosaurus-Torpid-Eat', 'Lystrosaurus-Torpid-Out-Tamed', 'Lystrosaurus-Torpid-Out-Wild'],
    'mammoth':           ['Mammoth-Torpid-In', 'Mammoth-Torpid', 'Mammoth-Torpid-Eat', 'Mammoth-Torpid-Out-Tamed', 'Mammoth-Torpid-Out-Wild'],
    'megalocerus':       ['Stag-Torpid-In', 'Stag-Torpid-Idle', 'Stag-Torpid-Eat', 'Stag-Torpid-Out-Tamed', 'Stag-Torpid-Out-Wild'],
    'megalodon':         ['Megalodon-Torpid-In', 'Megalodon-Torpid', 'Megalodon-Torpid-Eat', 'Megalodon-Torpid-Out-Tamed', 'Megalodon-Torpid-Out-Wild'],
    'mosasaurus':        ['Mosasaurus-Torpid-In', 'Mosasaurus-Torpid-Idle', 'Mosasaurus-Torpid-Eat', 'Mosasaurus-Torpid-Out-Tamed', 'Mosasaurus-Torpid-Out-Wild'],
    'paraceratherium':   ['Paraceratherium-Torpid-In', 'Paraceratherium-Torpid-Idle', 'Paraceratherium-Torpid-Eat', 'Paraceratherium-Torpid-Out-Tamed', 'Paraceratherium-Torpid-Out-Wild'],
    'parasaur':          ['Para-Torpid-In', 'Para-Torpid', 'Para-Torpid-Eat', 'Para-Torpid-Out-Tamed', 'Para-Torpid-Out-Wild'],
    'pegomastax':        ['Pegomastax-Torpid-In', 'Pegomastax-Torpid-Idle', 'Pegomastax-Torpid-Eat', 'Pegomastax-Torpid-Out-Tamed', 'Pegomastax-Torpid-Out-Wild'],
    'plesiosaur':        ['Plesiosaur-Torpid-In', 'Plesiosaur-Torpid-Idle', 'Plesiosaur-Torpid-Eat', 'Plesiosaur-Torpid-Out-Tamed', 'Plesiosaur-Torpid-Out-Wild'],
    'pteranodon':        ['Ptero-Torpid-In', 'Ptero-Torpid-Idle', 'Ptero-Torpid-Eat', 'Ptero-Torpid-Out-Tamed', 'Ptero-Torpid-Out-Wild'],
    'quetzal':           ['Quetzalcoatlus-Torpid-In', 'Quetzalcoatlus-Torpid-Idle', 'Quetzalcoatlus-Torpid-Eat', 'Quetzalcoatlus-Torpid-Out-Tamed', 'Quetzalcoatlus-Torpid-Out-Wild'],
    'ravager':           ['CaveWolf-Torpid-In', 'CaveWolf-Torpid-Idle', 'CaveWolf-Torpid-Eat', 'CaveWolf-Torpid-Out-Tamed', 'CaveWolf-Torpid-Out-Wild'],
    'sabertooth':        ['Saber-Torpid-In', 'Saber-Torpid-Idle', 'Saber-Torpid-Eat', 'Saber-Torpid-Out-Tamed', 'Saber-Torpid-Out-Wild'],
    'sarco':             ['Sarco-Ground-Torpid-In', 'Sarco-Ground-Torpid-Idle', 'Sarco-Ground-Torpid-Eat', 'Sarco-Ground-Torpid-Out-Tamed', 'Sarco-Ground-Torpid-Out-Wild'],
    'spinosaurus':       ['Spino-Torpid-In', 'Spino-Torpid-Idle', 'Spino-Torpid-Eat', 'Spino-Torpid-Out-Tamed', 'Spino-Torpid-Out-Wild'],
    'terrorbird':        ['TerrorBird-Torpid-In', 'TerrorBird-Torpid-Idle', 'TerrorBird-Torpid-Eat', 'TerrorBird-Torpid-Out-Tamed', 'TerrorBird-Torpid-Out-Wild'],
    'therizinosaurus':   ['Therizinosaurus-Torpid-In', 'Therizinosaurus-Torpid-Idle', 'Therizinosaurus-Torpid-Eat', 'Therizinosaurus-Torpid-Out-Tamed', 'Therizinosaurus-Torpid-Out-Wild'],
    'titanosaur':        ['Titanosaur-Torpid-In', 'Titanosaur-Torpid-Idle', 'Titanosaur-Torpid-Eat', 'Titanosaur-Torpid-Out-Tamed', 'Titanosaur-Torpid-Out-Wild'],
    'triceratops':       ['Trike-Torpid-In', 'Trike-Torpid', 'Trike-Torpid-Eat', 'Trike-Torpid-Out-Tamed', 'Trike-Torpid-Out-Wild'],
    'tusoteuthis':       ['Tusoteuthis-Torpid-In', 'Tusoteuthis-Torpid-Idle', 'Tusoteuthis-Torpid-Out-Tamed', 'Tusoteuthis-Torpid-Out-Wild'],
    'tyrannosaurus':     ['Rex-Torpid-In', 'Rex-Torpid', 'Rex-Torpid-Eat', 'Rex-Torpid-Out-Tamed', 'Rex-Torpid-Out-Wild'],
    'unicorn':           ['Equus-Torpid-In', 'Equus-Torpid-Idle', 'Equus-Torpid-Eat', 'Equus-Torpid-Out-Tamed', 'Equus-Torpid-Out-Wild'],
    'velociraptor':      ['Raptor-Torpid-In', 'Raptor-Torpid', 'Raptor-Torpid-Eat', 'Raptor-Torpid-Out-Tamed', 'Raptor-Torpid-Out-Wild'],
}
for entry in EXPANSION:
    identifier=entry['id']
    SPECIES.append(tuple(entry[k] for k in ('folder','id','height','idle','walk','attack')))
    SIZE_MULTIPLIERS[identifier]=entry['size']
    BEHAVIOR_CLIPS[identifier]=[entry[k] for k in ('run','food','warning')]
    AUTHORED_SLEEP.add(identifier)
SPECIES = [(folder, identifier, height * SIZE_MULTIPLIERS.get(identifier, 2), *clips, *BEHAVIOR_CLIPS[identifier],
            *(['Ark-Sleep'] if identifier in AUTHORED_SLEEP else []), *TORPOR_CLIPS.get(identifier, []))
           for folder, identifier, height, *clips in SPECIES]
# The collection catalog already names its final height and complete clip set.
for entry in COLLECTION:
    SPECIES.append((entry['folder'], entry['id'], entry['height'], *import_clips(entry),
                    *TORPOR_CLIPS.get(entry['id'], [])))

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

def source_files(folder):
    """Resolve the read-only originals the importer reads. Keys match the report's source_sha256 names.

    Painted geometry, when present, supersedes the plain exporter file. verify_assets.py imports this
    resolver, so a provenance check always hashes the exact file the import recorded.
    """
    source = ROOT.parent / 'Creatures' / folder
    painted_geometry = sorted((source / 'textures').glob('*.geo.json'))
    geometry = painted_geometry[0] if painted_geometry else next((source / 'geo').glob('*.json'))
    animation = next((source / 'animations').glob('*.json'))
    files = {geometry.name: geometry, animation.name: animation}
    files.update({f'texture_{variant.lower()}': source / 'textures' / variant / 'skin.png'
                  for variant in TEXTURE_VARIANTS})
    return geometry, animation, files


def main():
    report = []
    for folder, identifier, height, *clips in SPECIES:
        geo_path, anim_path, sources = source_files(folder)
        texture_paths = {variant: sources[f'texture_{variant.lower()}'] for variant in TEXTURE_VARIANTS}
        missing = [name for name, path in sources.items() if not path.is_file()]
        if missing:
            raise FileNotFoundError(f'{folder} is missing source files: {", ".join(missing)}')
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
            if 'torp' in name.lower():
                # Torpor sequences carry their authored one-shot/loop intent; the locomotion heuristic
                # below is calibrated for movement clips and must not rewrite a collapse or a wake.
                clip['loop'] = clip.get('loop', False)
            else:
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
        texture_dir = ASSETS / 'textures/entity'
        texture_dir.mkdir(parents=True, exist_ok=True)
        for variant, texture_path in texture_paths.items():
            shutil.copyfile(texture_path, texture_dir / f'{identifier}_{variant.lower()}.png')
        report.append({'id': identifier, 'source': f'Creatures/{folder}', 'height_blocks': height,
                       'scale': factor, 'clips': clips,
                       'source_sha256': {name: hashlib.sha256(path.read_bytes()).hexdigest()
                                         for name, path in sources.items()}})
    write(ROOT / 'docs/creature-import.json', report)
    print(f'Imported {len(report)} creatures, {sum(len(row[3:]) for row in SPECIES)} clips, and five procedural texture variants.')

if __name__ == '__main__': main()
