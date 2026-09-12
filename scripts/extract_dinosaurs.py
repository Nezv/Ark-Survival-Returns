"""Extract installed ARK adults without modifying game files.

Package adaptation: ARK 405/10 uses 64-bit export sizes/offsets despite its
old engine version. Compact those records in copies, leaving payload addresses
unchanged. UE Viewer can then read mesh, skeleton and animation payloads.
"""
from pathlib import Path
import argparse, hashlib, json, struct, subprocess, zlib
from model_catalog import MODELS

ROOT = Path(__file__).resolve().parents[1]
CREATURES = ROOT / 'Creatures'
WORKSHOP = Path('C:/Program Files (x86)/Steam/steamapps/workshop/content/346110/1522327484/WindowsNoEditor')
SPECIES = {
    'Titanosaur': 'Titanosaur',
    'Giganotosaur': 'Giganotosaurus',
    'Velociraptor': 'Raptor',
    'Triceratops': 'Trike',
    'Brontosaur': 'Sauropod',
    'Therezinosaur': 'Therizinosaurus',
    'Argentavis': 'Argentavis',
    'Piterodon': 'Ptero',
    'Tyranosaur': 'Rex',
    'Spinosaurus': 'Spino',
    'Parasaur': 'Para',
    'Ceratosaurus': 'CeratosaurusAA_Mesh',
    'Dilophosaur': 'Dilo',
    'Acrochantosaur': 'Acro_Mesh',
    'Allosaurus': 'Allosaurus',
    'Ankylosaurus': 'Ankylo',
    'Carnotaurus': 'Carno',
    'Pegomastax': 'Pegomastax',
    'Lystrosaurus': 'Lystrosaurus',
}
MOD_LAYOUT = {
    'Ceratosaurus': ('Ceratosaurus/Dinos', 'CeratosaurusAA_Skeleton'),
    'Acrochantosaur': ('Acrocanthosaurus/Dinos', 'Acrocanthosaurus_Mesh_Skeleton'),
    'Deinosuchus': ('Deinosuchus/Dino', 'Deinosuchus_TLC_Rig_Skeleton'),
}
SPECIES.update({label:settings['asset'] for label,settings in MODELS.items()})

def asset_directory(label):
    return 'Mods/Additions_Pack/' + MOD_LAYOUT[label][0] if label in MOD_LAYOUT else MODELS.get(label,{}).get('directory','PrimalEarth/Dinos/' + SPECIES[label])

def unpack_workshop(source, destination):
    """Decode ARK's chunked zlib workshop format into workspace copies."""
    data = source.read_bytes()
    tag, block, compressed, uncompressed = struct.unpack_from('<4Q', data)
    assert tag == 0x9E2A83C1 and block == 131072, source
    count = (uncompressed + block - 1) // block
    offset = 32 + count * 16
    output = []
    for i in range(count):
        size, expected = struct.unpack_from('<2Q', data, 32 + i*16)
        decoded = zlib.decompress(data[offset:offset+size])
        assert len(decoded) == expected, source
        output.append(decoded); offset += size
    decoded = b''.join(output)
    assert offset == len(data) and len(decoded) == uncompressed, source
    assert sum(struct.unpack_from('<Q',data,32+i*16)[0] for i in range(count)) == compressed
    size_file = Path(str(source) + '.uncompressed_size')
    if size_file.exists(): assert int(size_file.read_text()) == len(decoded)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(decoded)
    return {'workshop_source':str(source),'workshop_sha256':hashlib.sha256(data).hexdigest()}

def resource_names(label):
    """Keep the original Rex resource paths when regenerating its artwork."""
    if label == 'Tyranosaur':
        return 'rex_ravager', 'Rex_Ravager_GeckoLib.bbmodel'
    return label.lower(), f'{label}_GeckoLib.bbmodel'

def normalize(source, destination):
    original = source.read_bytes()
    data = bytearray(original)
    version = struct.unpack_from('<4i', data, 4)
    assert version in [(-3, 864, 405, 10), (-3, 864, 404, 10)], (source,version)
    assert data[28:37] == b'\x05\x00\x00\x00None\x00', source
    count, offset = struct.unpack_from('<ii', data, 49)
    depends = struct.unpack_from('<i', data, 65)[0]
    record_size = (depends - offset) // count
    assert depends - offset == count * record_size and record_size in (68,76), (source,count,offset,depends)
    records = []
    for index in range(count):
        record = data[offset + index*record_size:offset + (index+1)*record_size]
        size, position = struct.unpack_from('<qq' if record_size == 76 else '<ii', record, 24)
        assert 0 <= position <= len(data) and 0 <= size <= len(data) and position+size <= len(data)
        records.append(record[:24] + struct.pack('<ii', size, position) + record[40:] if record_size == 76 else record)
    data[offset:offset+count*68] = b''.join(records)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(data)
    return {'source': str(source), 'sha256': hashlib.sha256(original).hexdigest(),
            'size': len(original), 'export_count': count}

def extract(content, umodel, selected, workshop=WORKSHOP):
    work = ROOT / '.work'
    normalized = work / 'normalized'
    exported = work / 'exports'
    logs = work / 'logs'
    logs.mkdir(parents=True, exist_ok=True)
    for label in selected:
        asset = SPECIES[label]
        settings = MODELS.get(label,{})
        mesh = settings.get('mesh',asset)
        relative = asset_directory(label)
        animation_dir = settings.get('animation_dir',{'Dilophosaur':'Anims','Pegomastax':'Animation'}.get(label,'Animations'))
        source = content / relative
        skeleton = settings.get('skeleton',f'{asset}_Skeleton')
        workshop_sources = {}
        if label in MOD_LAYOUT:
            mod_dir, skeleton = MOD_LAYOUT[label]
            source = work / 'workshop' / relative
            archive = workshop / mod_dir
            selected_files = [archive/f'{asset}.uasset.z',archive/f'{skeleton}.uasset.z']
            selected_files += sorted((archive/animation_dir).rglob('*.uasset.z'))
            for package in selected_files:
                dest = source / package.relative_to(archive).with_suffix('')
                workshop_sources[str(dest)] = unpack_workshop(package,dest)
        files = [source / f'{mesh}.uasset', source / f'{skeleton}.uasset']
        files += [source/f'{extra}.uasset' for extra in settings.get('extras',[])]
        files += sorted((source / animation_dir).rglob('*.uasset'))
        manifest = [{**normalize(p, normalized / relative / p.relative_to(source)), **workshop_sources.get(str(p),{})} for p in files]
        (logs / f'{label}_sources.json').write_text(json.dumps(manifest, indent=2))
        for kind, target, flags in [
            ('mesh', f'{relative}/{mesh}.uasset', []),
            ('gltf', f'{relative}/{mesh}.uasset', ['-gltf']),
            ('animations', f'{relative}/{animation_dir}/*.uasset', []),
        ]:
            output = exported / ('gltf' if kind == 'gltf' else 'actorx')
            command = [str(umodel), '-export', '-game=ark', '-notex',
                       f'-path={normalized}', f'-out={output}', *flags, target]
            result = subprocess.run(command, cwd=work, capture_output=True)
            (logs / f'{label}_{kind}.log').write_bytes(result.stdout + result.stderr)
            if result.returncode:
                raise RuntimeError(f'{label} {kind} failed: see {logs}')
        for extra in settings.get('extras',[]):
            for kind,flags in [('actorx',[]),('gltf',['-gltf'])]:
                result=subprocess.run([str(umodel),'-export','-game=ark','-notex',f'-path={normalized}',
                    f'-out={exported/kind}',*flags,f'{relative}/{extra}.uasset'],cwd=work,capture_output=True)
                (logs/f'{label}_{extra}_{kind}.log').write_bytes(result.stdout+result.stderr)
                if result.returncode:raise RuntimeError(f'{label} attachment export failed: {extra}')
        if settings.get('extras'):
            result=subprocess.run([str(umodel),'-dump','-all','-game=ark','-notex',f'-path={normalized}',
                f'{relative}/{skeleton}.uasset'],cwd=work,capture_output=True)
            (logs/f'{label}_skeleton-properties.txt').write_bytes(result.stdout+result.stderr)
            if result.returncode:raise RuntimeError(f'{label} socket export failed')
        clips = list((exported / f'actorx/{relative}/{animation_dir}').rglob('*.psa'))
        assert clips, f'No animation exports: {label}'
        print(f'{label}: mesh, skeleton, {len(clips)} animation sequences exported', flush=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--content', type=Path, default=Path(r'C:\Program Files (x86)\Steam\steamapps\common\ARK\ShooterGame\Content'))
    parser.add_argument('--umodel', type=Path, required=True)
    parser.add_argument('--workshop', type=Path, default=WORKSHOP)
    parser.add_argument('--species', nargs='+', choices=SPECIES, default=list(SPECIES))
    args = parser.parse_args()
    extract(args.content, args.umodel, args.species,args.workshop)
