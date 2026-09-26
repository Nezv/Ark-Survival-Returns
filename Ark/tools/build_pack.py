"""Build the Ark modpack: every integration branded as "Ark - <Name>", plus Ark itself and its configs.

Inputs
  config/integrations.json          which mods, their Ark names, build mode (release | source | shader | embedded | blocked)
  config/*-mods.lock.json           pinned upstream files and SHA-512 hashes (downloaded by Install-Ark-Extras)
  pack/src/<project>                git clones for build = source (branch from the manifest)
  pack/patches/<project>/*.patch    Ark changes applied to a clone before it is built
  pack/overlays/<project>/          binary files (textures) copied into the clone before it is built

Outputs (pack/out, not committed)
  mods/, client-mods/               ready-to-copy jars for a server/client instance
  Ark-Survival-Returns-<version>.mrpack   import into the Modrinth App (File > Add instance > From file)

Branding rewrites only metadata: displayName, logo (ark_icon.png), and a credit line in the description.
The original mod id, version, license field and license files are kept, so dependencies and credits
still resolve. Release jars are otherwise byte-identical to upstream.

Run from Ark after ./gradlew build: python tools/build_pack.py [--rebuild-sources]
"""
import argparse
import fnmatch
import hashlib
import io
import json
import re
import shutil
import subprocess
import sys
import tomllib
import zipfile
from pathlib import Path

ARK = Path(__file__).resolve().parents[1]
REPO = ARK.parent
OUT = ARK / 'pack/out'
SRC = ARK / 'pack/src'
PATCHES = ARK / 'pack/patches'
OVERLAYS = ARK / 'pack/overlays'
ICON = ARK / 'src/main/resources/ark_icon.png'
SIGNATURE = re.compile(r'META-INF/[^/]+\.(SF|RSA|DSA|EC)$')


def load(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def props(path):
    out = {}
    for line in Path(path).read_text(encoding='utf-8').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            out[key.strip()] = value.strip()
    return out


# ------------------------------------------------------------------------------------------- brand

def brand_toml(text, ark_name, credit):
    """Rewrites display name, logo and description; returns the new text, validated as TOML."""
    original = tomllib.loads(text)
    mods = original.get('mods', [])
    count = [0]

    def display(match):
        count[0] += 1
        name = match.group(2)
        if name.startswith('Ark - '):
            return match.group(0)
        new = ark_name if len(mods) == 1 and ark_name else 'Ark - ' + name
        return f'{match.group(1)}{json.dumps(new, ensure_ascii=False)}'
    text = re.sub(r'(?m)^(\s*displayName\s*=\s*)"((?:[^"\\]|\\.)*)"', display, text)
    text = re.sub(r"(?m)^(\s*displayName\s*=\s*)'([^']*)'", display, text)
    if re.search(r'(?m)^\s*logoFile\s*=', text):
        text = re.sub(r'(?m)^(\s*logoFile\s*=\s*)(["\']).*?\2', r'\1"ark_icon.png"', text)
    else:
        text = re.sub(r'(?m)^(\s*license\s*=.*)$', r'\1\nlogoFile="ark_icon.png"', text, count=1)
    text = re.sub(r'(?m)^(\s*catalogueImageIcon\s*=\s*)(["\']).*?\2', r'\1"ark_icon.png"', text)
    line = credit.replace('\\', '\\\\')
    if "'''" in text and re.search(r"(?m)^\s*description\s*=\s*'''", text):
        text = re.sub(r"(?m)^(\s*description\s*=\s*''')\n?", lambda m: m.group(1) + line + '\n\n', text, count=1)
    elif re.search(r'(?m)^\s*description\s*=\s*"', text):
        text = re.sub(r'(?m)^(\s*description\s*=\s*")', lambda m: m.group(1) + line.replace('"', '\\"') + ' ', text, count=1)
    branded = tomllib.loads(text)  # raises if an edit broke the file
    assert [m['modId'] for m in branded['mods']] == [m['modId'] for m in mods], 'mod ids changed'
    return text


def brand_jar(data, ark_name, credit, strip=()):
    zin = zipfile.ZipFile(io.BytesIO(data))
    manifest = zin.read('META-INF/MANIFEST.MF').decode(errors='replace') if 'META-INF/MANIFEST.MF' in zin.namelist() else ''
    library = 'FMLModType: LIBRARY' in manifest
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():
            name = info.filename
            if SIGNATURE.match(name) or any(fnmatch.fnmatchcase(name, pattern) for pattern in strip):
                continue
            body = zin.read(name)
            if name == 'META-INF/neoforge.mods.toml':
                body = brand_toml(body.decode('utf-8'), ark_name, credit).encode('utf-8')
            elif library and name.startswith('META-INF/jarjar/') and name.endswith('-mod.jar'):
                body = brand_jar(body, ark_name, credit)  # Sodium ships its real mod inside a service jar
            zout.writestr(info, body)
        if 'ark_icon.png' not in zin.namelist():
            zout.writestr('ark_icon.png', ICON.read_bytes())
    return buffer.getvalue()


# ------------------------------------------------------------------------------------------ build

def source_jar(mod, rebuild):
    """Clone (if needed), apply Ark patches, build with the project's own wrapper, return the jar bytes."""
    project = mod['project']
    repo = SRC / project
    if not repo.exists():
        subprocess.run(['git', 'clone', '--depth', '1', '--branch', mod['branch'], mod['source'] + '.git', str(repo)], check=True)
    for patch in sorted((PATCHES / project).glob('*.patch')) if (PATCHES / project).is_dir() else []:
        applied = subprocess.run(['git', 'apply', '--check', '--reverse', str(patch)], cwd=repo, capture_output=True).returncode == 0
        if not applied:
            subprocess.run(['git', 'apply', '--whitespace=nowarn', str(patch)], cwd=repo, check=True)
            rebuild = True
    overlay = OVERLAYS / project
    for path in overlay.rglob('*') if overlay.is_dir() else []:
        if path.is_file():
            target = repo / path.relative_to(overlay)
            if not target.exists() or target.read_bytes() != path.read_bytes():
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(path.read_bytes())
                rebuild = True
    gradle_dir = repo / mod.get('gradleDir', '.')
    libs = gradle_dir / 'build/libs'
    jars = [j for j in libs.glob('*.jar') if not re.search(r'-(sources|javadoc|api|slim|dev)\.jar$', j.name)] if libs.is_dir() else []
    if rebuild or not jars:
        wrapper = gradle_dir / ('gradlew.bat' if sys.platform == 'win32' else 'gradlew')
        subprocess.run([str(wrapper), 'build', '-x', 'test', '--console=plain', '-q'], cwd=gradle_dir, check=True)
        jars = [j for j in libs.glob('*.jar') if not re.search(r'-(sources|javadoc|api|slim|dev)\.jar$', j.name)]
    if not jars:
        raise SystemExit(f'{project}: the source build produced no jar in {libs}')
    return max(jars, key=lambda j: j.stat().st_mtime).read_bytes(), 'source ' + mod['branch']


def pinned_file(lock_entry):
    folder = {'shader': 'run/shaderpacks', 'shared': 'shared-mods'}.get(lock_entry['kind'], 'client-mods')
    path = ARK / folder / lock_entry['filename']
    data = path.read_bytes()
    if hashlib.sha512(data).hexdigest() != lock_entry['sha512']:
        raise SystemExit(f'{path.name}: hash mismatch, rerun Install-Ark-Extras.bat')
    return data


def safe_name(text):
    return re.sub(r'[^A-Za-z0-9.+-]+', '-', text.replace("'", '')).strip('-')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--rebuild-sources', action='store_true')
    args = parser.parse_args()
    manifest = load(ARK / 'config/integrations.json')
    locks = {}
    for name in ('client-mods.lock.json', 'shared-mods.lock.json'):
        for entry in load(ARK / 'config' / name)['entries']:
            locks[entry['project']] = entry
    gradle = props(ARK / 'gradle.properties')
    version = gradle.get('mod_version', '0.0.0')
    if OUT.exists():
        shutil.rmtree(OUT)
    (OUT / 'mods').mkdir(parents=True)
    (OUT / 'client-mods').mkdir()
    index_files, overrides, report = [], {}, []

    for integration in manifest['integrations']:
        for mod in integration['mods']:
            client_only = mod.get('side', integration['side']) == 'client'
            mode = mod.get('build', 'release')
            if mode in ('embedded', 'blocked'):
                report.append((integration['id'], mod['arkName'], mode, ''))
                continue
            lock = locks.get(mod['project'])
            if mode == 'shader':
                # Shader packs go in unmodified, downloaded by the launcher from Modrinth.
                index_files.append({'path': f"shaderpacks/{lock['filename']}", 'hashes': {
                    'sha1': hashlib.sha1(pinned_file(lock)).hexdigest(), 'sha512': lock['sha512']},
                    'env': {'client': 'required', 'server': 'unsupported'}, 'downloads': [lock['url']], 'fileSize': lock['size']})
                report.append((integration['id'], mod['arkName'], 'shader (unmodified)', lock['version']))
                continue
            if mode == 'source':
                data, origin = source_jar(mod, args.rebuild_sources)
                upstream_version = lock['version'] if lock else 'source'
            else:
                data, origin, upstream_version = pinned_file(lock), 'release ' + lock['version'], lock['version']
            toml = tomllib.loads(zipfile.ZipFile(io.BytesIO(data)).read('META-INF/neoforge.mods.toml').decode())
            first = toml['mods'][0]
            credit = (f"Part of Ark Survival Returns. Original: {first.get('displayName', mod['project'])} by "
                      f"{first.get('authors', 'its authors')} ({mod['license']}), {mod['source']}. Built from {origin}.")
            branded = brand_jar(data, mod['arkName'], credit, mod.get('strip', ()))
            file_name = f"Ark-{safe_name(mod['arkName'].removeprefix('Ark - '))}-{safe_name(first.get('version', upstream_version))}.jar"
            folder = 'client-mods' if client_only else 'mods'
            (OUT / folder / file_name).write_bytes(branded)
            overrides[f"{'client-overrides' if client_only else 'overrides'}/mods/{file_name}"] = branded
            report.append((integration['id'], mod['arkName'], origin, file_name))

    # Ark itself.
    ark_jars = sorted((ARK / 'build/libs').glob(f"{gradle.get('mod_id', 'arksurvivalreturns')}-*.jar"))
    ark_jars = [j for j in ark_jars if not j.name.endswith(('-sources.jar', '-javadoc.jar'))]
    if not ark_jars:
        raise SystemExit('Build Ark first: ./gradlew build')
    ark = max(ark_jars, key=lambda j: j.stat().st_mtime)
    (OUT / 'mods' / ark.name).write_bytes(ark.read_bytes())
    overrides[f'overrides/mods/{ark.name}'] = ark.read_bytes()
    report.append(('Ark', 'Ark Survival Returns', 'gradle build', ark.name))

    # Authored configs that the instance reads from its config folder.
    for folder in ('ftbquests', 'fancymenu'):
        root = ARK / 'config' / folder
        for path in root.rglob('*') if root.is_dir() else []:
            if path.is_file():
                overrides[f'overrides/config/{folder}/{path.relative_to(root).as_posix()}'] = path.read_bytes()

    index = {'formatVersion': 1, 'game': 'minecraft', 'versionId': version, 'name': 'Ark Survival Returns',
             'summary': 'ARK-style survival for Minecraft 26.1.2 with every integration branded and pre-configured.',
             'files': index_files,
             'dependencies': {'minecraft': gradle.get('minecraft_version', '26.1.2'), 'neoforge': gradle.get('neo_version', '')}}
    mrpack = OUT / f'Ark-Survival-Returns-{version}.mrpack'
    with zipfile.ZipFile(mrpack, 'w', zipfile.ZIP_DEFLATED) as pack:
        pack.writestr('modrinth.index.json', json.dumps(index, indent=2))
        for path, body in overrides.items():
            pack.writestr(path, body)
    width = max(len(r[1]) for r in report)
    for row in report:
        print(f'{row[0]:4} {row[1]:<{width}}  {row[2]:<26} {row[3]}')
    print(f'\nWrote {mrpack.relative_to(ARK)} ({mrpack.stat().st_size / 1e6:.1f} MB) and pack/out/mods, pack/out/client-mods')


if __name__ == '__main__':
    main()
