"""Import original ASE creature recordings from an installed Steam copy (read-only).

Requires numpy and soundfile. Cooked SoundWave packages contain complete Ogg
streams; workshop packages are chunked zlib. Copy mono streams verbatim and
only downmix stereo for Minecraft positional playback, retaining sample rate.
Package references resolve ARK's shared sounds, without inventing missing calls.
"""
from pathlib import Path
import argparse
import hashlib
import io
import json
import re
import struct
import zlib
import threading
from concurrent.futures import ThreadPoolExecutor
import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/arksurvivalreturns'
DEFAULT_CONTENT = Path(r'C:/Program Files (x86)/Steam/steamapps/common/ARK/ShooterGame/Content')
DEFAULT_WORKSHOP = Path(r'C:/Program Files (x86)/Steam/steamapps/workshop/content/346110/1522327484/WindowsNoEditor')
ROLES = ('ambient', 'attack', 'hurt', 'death', 'sleep', 'wake', 'warn', 'step', 'eat', 'flap', 'takeoff', 'land')
# Runtime species -> original creature directory, older sound directory.
LAYOUT = {
    'pteranodon': ('Ptero', 'Ptero'), 'velociraptor': ('Raptor', 'Raptor'),
    'argentavis': ('Argentavis', 'Argent'), 'triceratops': ('Trike', 'Trike'),
    'therizinosaurus': ('Therizinosaurus', None), 'brontosaurus': ('Sauropod', 'Bronto'),
    'tyrannosaurus': ('Rex', 'Rex'), 'giganotosaurus': ('Giganotosaurus', 'Gigantosaurus'),
    'titanosaur': ('Titanosaur', None), 'spinosaurus': ('Spino', None),
    'parasaur': ('Para', 'Para'), 'dilophosaur': ('Dilo', 'Dilo'),
    'allosaurus': ('Allosaurus', None), 'ankylosaurus': ('Ankylo', 'Anky'),
    'carnotaurus': ('Carno', 'Carno'), 'pegomastax': ('Pegomastax', 'Pegomastax'),
    'lystrosaurus': ('Lystrosaurus', None), 'cnidaria': ('Cnidaria', 'Cnidaria'),
    'plesiosaur': ('Plesiosaur', 'Plesiosaur'), 'megalodon': ('Megalodon', 'Megalodon'),
    'liopleurodon': ('Liopleurodon', None), 'mosasaurus': ('Mosasaurus', None),
    'tusoteuthis': ('Tusoteuthis', 'Tusoteuthis'), 'kaprosuchus': ('Kaprosuchus', None),
    'sarco': ('Sarco', 'Sarco'), 'titanoboa': ('Boa', 'Snake'),
    'megalocerus': ('Stag', 'Stag'), 'unicorn': ('Equus', None),
    'mammoth': ('Mammoth', None), 'direwolf': ('Direwolf', None),
    'sabertooth': ('Saber', 'Saber'), 'megapithecus': ('Gorilla', 'Gorilla'),
    'paraceratherium': ('Paraceratherium', 'Paracer'), 'terrorbird': ('TerrorBird', 'Terrorbird'),
    'ravager': ('Aberration/Dinos/CaveWolf', None), 'archaeopteryx': ('Archaeopteryx', None),
    'quetzal': ('Quetzalcoatlus', None), 'dragon': ('Dragon', 'Dragon'),
    'acrocanthosaurus': ('Mods/Additions_Pack/Acrocanthosaurus/Dinos', None),
    'ceratosaurus': ('Mods/Additions_Pack/Ceratosaurus/Dinos', None),
    'deinosuchus': ('Mods/Additions_Pack/Deinosuchus/Dino', None),
}
ATTACK_KIND = {'triceratops': 'horn', 'therizinosaurus': 'claw', 'brontosaurus': 'stomp',
               'titanosaur': 'stomp', 'spinosaurus': 'bite', 'allosaurus': 'bite',
               'ankylosaurus': 'tail', 'carnotaurus': 'bite', 'unicorn': 'kick', 'dragon': 'bite'}
PATTERNS = {
    'ambient': r'(^|_)(idle|mumble|voc_idle|vox_mumble|cosshort|coslong)(_|\d|$)',
    'attack': r'attack|bite|chomp|sting|shock',
    'hurt': r'hurt|pain', 'death': r'death|(^|_)die',
    'sleep': r'sleep.*(idle|loop)|snor|torp(id)?[_-]?(idle|loop)|(^|_)breath[0-9_]*$',
    'wake': r'torp(id)?[_-]?out|sleep.*(out|wake)',
    'warn': r'roar|bellow|howl|growl|(^|_)call|startled|hiss|bark',
    'step': r'(^|_)(move|footsteps?|step)(_|\d|$)',
    'eat': r'(^|_)(eat|graze|chew)(_|\d|$)', 'flap': r'(^|_)(fly|flap|wingflap|wing)(_|\d|$)',
    'takeoff': r'take[_-]?off', 'land': r'(^|_)land(_|\d|$)',
}

def package_bytes(path):
    data = path.read_bytes()
    if path.suffix != '.z':
        return data
    tag, block, compressed, length = struct.unpack_from('<4Q', data)
    if tag != 0x9E2A83C1 or block != 131072:
        raise ValueError(f'Invalid workshop header: {path}')
    count = (length + block - 1) // block
    cursor = 32 + count * 16
    chunks = []
    for i in range(count):
        size, expected = struct.unpack_from('<2Q', data, 32 + i * 16)
        decoded = zlib.decompress(data[cursor:cursor + size])
        if len(decoded) != expected:
            raise ValueError(f'Invalid workshop chunk: {path}')
        chunks.append(decoded)
        cursor += size
    result = b''.join(chunks)
    if len(result) != length or cursor != len(data) or cursor - (32 + count * 16) != compressed:
        raise ValueError(f'Invalid workshop size: {path}')
    return result

def references(data):
    """Read the cooked package name table, including full dependency paths."""
    version = struct.unpack_from('<4i', data, 4)
    if version not in ((-3, 864, 405, 10), (-3, 864, 404, 10)):
        raise ValueError(f'Unsupported ASE package: {version}')
    count, cursor = struct.unpack_from('<ii', data, 41)
    if not 0 < count < 100000 or not 0 < cursor < len(data):
        raise ValueError('Invalid ASE name table')
    names = []
    for _ in range(count):
        size = struct.unpack_from('<i', data, cursor)[0]
        cursor += 4
        if not 0 < abs(size) <= 65536:
            raise ValueError('Invalid ASE string')
        length = abs(size) * (2 if size < 0 else 1)
        name = data[cursor:cursor + length].decode('utf-16-le' if size < 0 else 'utf-8').rstrip('\0')
        cursor += length
        names.append(name)
    # UE FName stores a numeric suffix separately. E.g. the package name
    # Pegomastax_Idle plus number 2 means Pegomastax_Idle_1, not a missing asset.
    import_count, import_offset = struct.unpack_from('<ii', data, 57)
    if import_count < 0 or import_offset + import_count * 28 > len(data):
        raise ValueError('Invalid ASE import table')
    numbered = {}
    for index in range(import_count):
        name_index, number = struct.unpack_from('<ii', data, import_offset + index * 28 + 20)
        if not 0 <= name_index < len(names) or number < 0:
            raise ValueError('Invalid ASE import FName')
        name = names[name_index]
        if name.startswith('/Game/'):
            numbered.setdefault(name, []).append(name + ('_' + str(number - 1) if number else ''))
    result = []
    for name in names:
        if name.startswith('/Game/'):
            for reference in numbered.get(name, [name]):
                if reference[6:] not in result:
                    result.append(reference[6:])
    return result

def ogg_stream(data):
    start = data.find(b'OggS')
    if start < 0:
        return None
    cursor = start
    serial = None
    sequence = 0
    while True:
        if data[cursor:cursor + 4] != b'OggS' or data[cursor + 4] != 0:
            raise ValueError('Invalid or truncated Ogg page')
        current_serial, current_sequence = struct.unpack_from('<II', data, cursor + 14)
        if serial is None:
            serial = current_serial
            if not data[cursor + 5] & 2:
                raise ValueError('Ogg beginning-of-stream flag missing')
        if current_serial != serial or current_sequence != sequence:
            raise ValueError('Ogg page sequence mismatch')
        segments = data[cursor + 26]
        size = 27 + segments + sum(data[cursor + 27:cursor + 27 + segments])
        if cursor + size > len(data):
            raise ValueError('Truncated Ogg payload')
        end = bool(data[cursor + 5] & 4)
        cursor += size
        sequence += 1
        if end:
            break
    return data[start:cursor]

def roles_for(name):
    name = name.lower()
    return [r for r in ROLES if re.search(PATTERNS[r], name)
            and not (r in ('ambient', 'attack', 'eat', 'step', 'flap') and any(t in name for t in ('torp', 'sleep')))
            and not (r == 'flap' and any(t in name for t in ('attack', 'roll')))]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--content', type=Path, default=DEFAULT_CONTENT)
    parser.add_argument('--workshop', type=Path, default=DEFAULT_WORKSHOP)
    parser.add_argument('--inspect', action='store_true', help='Print coverage without replacing files')
    args = parser.parse_args()
    enum = ROOT / 'src/main/java/dev/nez/arksurvivalreturns/feature/creature/Species.java'
    ids = re.findall(r'^    [A-Z_]+\("([^"]+)"', enum.read_text(), re.M)
    if set(ids) != set(LAYOUT):
        raise ValueError('Audio catalog and registered species differ')
    def locate(relative):
        path = args.content / (relative + '.uasset')
        if path.exists():
            return path
        if relative.startswith('Mods/Additions_Pack/'):
            path = args.workshop / (relative.removeprefix('Mods/Additions_Pack/') + '.uasset.z')
            if path.exists():
                return path
        return None
    def label(path):
        if path.is_relative_to(args.content):
            return 'Content/' + path.relative_to(args.content).as_posix()
        return 'Workshop/1522327484/' + path.relative_to(args.workshop).as_posix()
    catalog = {}
    recordings = {}
    for identifier in ids:
        directory, old = LAYOUT[identifier]
        directory = directory if '/' in directory else 'PrimalEarth/Dinos/' + directory
        root = args.content / directory
        if directory.startswith('Mods/Additions_Pack/') and not root.exists():
            root = args.workshop / directory.removeprefix('Mods/Additions_Pack/')
        roots = [root / 'Sounds']
        if old:
            roots.append(args.content / 'PrimalEarth/Sound/SFX/Dinos' / old)
        events = {role: [] for role in ROLES}
        event_sources = {role: [] for role in ROLES}
        def add(path, role, source, visited=None):
            visited = set() if visited is None else visited
            if path in visited:
                return
            visited.add(path)
            data = package_bytes(path)
            stream = ogg_stream(data)
            if stream is None:
                for ref in references(data):
                    dependency = locate(ref)
                    if dependency and ('sound' in ref.lower() or 'cue' in ref.lower()):
                        add(dependency, role, source, visited)
                return
            # Both decoding and the Ogg framing must succeed before any live asset is replaced.
            samples, rate = sf.read(io.BytesIO(stream), dtype='float32', always_2d=True)
            if not len(samples) or not np.isfinite(samples).all():
                raise ValueError(f'Invalid decoded recording: {path}')
            resource = 'creature/' + identifier + '/' + re.sub(r'[^a-z0-9_]', '_', path.name.split('.uasset')[0].lower())
            record = {'source': label(path), 'package_sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                      'original_ogg_sha256': hashlib.sha256(stream).hexdigest(), 'sample_rate': rate,
                      'source_channels': samples.shape[1], 'duration_seconds': round(len(samples) / rate, 4)}
            if resource in recordings and recordings[resource][0]['source'] != record['source']:
                raise ValueError(f'Output collision: {resource}')
            if resource not in recordings:
                recordings[resource] = (record, stream, path)
            if resource not in events[role]:
                events[role].append(resource)
            if source not in event_sources[role]:
                event_sources[role].append(source)
        # Prefer current creature recordings to older duplicates.
        for sound_root in roots:
            paths = sorted(p for p in sound_root.glob('*.uasset*') if p.name.endswith(('.uasset', '.uasset.z')))
            for role in ROLES:
                if events[role]:
                    continue
                candidates = paths
                if role == 'attack' and identifier in ATTACK_KIND:
                    preferred = [p for p in paths if ATTACK_KIND[identifier] in p.name.lower() and role in roles_for(p.name)]
                    if preferred:
                        candidates = preferred
                for path in candidates:
                    if role in roles_for(path.name.split('.uasset')[0]) and 'savage' not in path.name.lower():
                        add(path, role, label(path))
        # Character blueprints and animation notifies identify shared original cues.
        for path in sorted(root.rglob('*.uasset*')):
            if not path.name.endswith(('.uasset', '.uasset.z')):
                continue
            if 'sound' in path.as_posix().lower() or any(t in path.name.lower() for t in ('savage', 'baby', 'mega_', 'subboss')):
                continue
            relevant = roles_for(path.name.split('.uasset')[0])
            is_character = 'character_bp' in path.name.lower() and not any(t in path.name.lower() for t in ('bog_', 'paleo', 'scorched_', 'gen2_', 'cave', '_mega'))
            if not relevant and not is_character:
                continue
            for ref in references(package_bytes(path)):
                if not any(t in ref.lower() for t in ('sound', 'cue')) or 'footstepssfx' in ref.lower():
                    continue
                dependency = locate(ref)
                if dependency is None:
                    continue
                # Some original torpid loops intentionally reuse their own idle calls.
                referenced_roles = ['sleep'] if 'sleep' in relevant else roles_for(Path(ref).name)
                for role in referenced_roles:
                    if not events[role] and (is_character or role in relevant):
                        add(dependency, role, label(path) + ' -> ' + ref)
        # Warnings can use this creature's idle vocalizations; missing sleep/death never borrow idle calls.
        aliases = {}
        if not events['warn'] and events['ambient']:
            events['warn'] = list(events['ambient']); aliases['warn'] = 'ambient'
        catalog[identifier] = {'events': events, 'references': event_sources, 'aliases': aliases,
                               'missing': [r for r in ROLES if not events[r]]}
        print(identifier, ' '.join(f'{r}:{len(events[r])}' for r in ROLES), flush=True)
    if args.inspect:
        print(f'{len(recordings)} recordings; {len(catalog)} species (inspection only)')
        return
    sounds_path = ASSETS / 'sounds.json'
    sounds = json.loads(sounds_path.read_text()) if sounds_path.exists() else {}
    sounds = {k:v for k,v in sounds.items() if not k.startswith('creature.')}
    records = {}
    output_root = (ASSETS / 'sounds/creature').resolve()
    expected = set()
    for index, (resource, (record, stream, source_path)) in enumerate(recordings.items(), 1):
        samples, rate = sf.read(io.BytesIO(stream), dtype='float32', always_2d=True)
        if index % 100 == 0:
            print(f'Encoding {index}/{len(recordings)}: {resource}', flush=True)
        destination = ASSETS / 'sounds' / (resource + '.ogg')
        destination.parent.mkdir(parents=True, exist_ok=True)
        if record['source_channels'] == 1:
            encoded = stream
        else:
            buffer = io.BytesIO()
            sf.write(buffer, samples.mean(axis=1), record['sample_rate'], format='OGG', subtype='VORBIS')
            encoded = buffer.getvalue()
        decoded, rate = sf.read(io.BytesIO(encoded), always_2d=True)
        if decoded.shape[1] != 1 or len(decoded) != len(samples) or rate != record['sample_rate']:
            raise ValueError(f'Invalid mono output: {resource}')
        destination.write_bytes(encoded)
        expected.add(destination.resolve())
        records[resource] = record | {'output_sha256': hashlib.sha256(encoded).hexdigest(), 'output_channels': 1}
    # Delete only obsolete files in the dedicated creature directory, after validating all sources.
    for path in output_root.rglob('*.ogg'):
        resolved = path.resolve()
        if not resolved.is_relative_to(output_root):
            raise ValueError(f'Unsafe output path: {path}')
        if resolved not in expected:
            path.unlink()
    for identifier, entry in catalog.items():
        for role, resources in entry['events'].items():
            sounds[f'creature.{identifier}.{role}'] = {
                'subtitle': f'entity.arksurvivalreturns.{identifier}',
                'sounds': [{'name': 'arksurvivalreturns:' + r} for r in resources]}
    sounds_path.write_text(json.dumps(sounds, indent=2) + '\n', encoding='utf-8')
    runtime = {identifier: {role: int(np.ceil(max((records[r]['duration_seconds'] for r in paths), default=0) * 20))
                           for role, paths in entry['events'].items()} for identifier, entry in catalog.items()}
    (ASSETS / 'audio/creature_catalog.json').write_text(json.dumps(runtime, indent=2) + '\n', encoding='utf-8')
    manifest = {'format': 1, 'source': 'Local Steam ARK: Survival Evolved and installed ARK Additions workshop 1522327484',
                'conversion': 'Mono originals copied verbatim; multichannel downmixed to mono at original sample rate for positional sound; no pitch or gain changes.',
                'sleep': 'Original sleep/snore, torpid idle/breath, or cues explicitly referenced by torpid-loop animations; missing categories are silent.',
                'species': catalog, 'recordings': records}
    (ASSETS / 'audio/creature_sources.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    total = sum((ASSETS / 'sounds' / (r + '.ogg')).stat().st_size for r in recordings)
    print(f'Imported {len(recordings)} recordings for {len(catalog)} species ({total/1048576:.1f} MiB).')

if __name__ == '__main__':
    # libvorbis requires more than Windows' default 1 MiB thread stack for 96 kHz Additions audio.
    threading.stack_size(16 * 1024 * 1024)
    with ThreadPoolExecutor(max_workers=1) as worker:
        worker.submit(main).result()
