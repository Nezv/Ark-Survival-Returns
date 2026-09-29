"""Offline validation of the pinned optional pack, including embedded NeoForge mods."""
from pathlib import Path
from io import BytesIO
import hashlib
import fnmatch
import json
import re
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
INTEGRATED_STANDALONE_GLOBS = (
    "AmbientSounds_*.jar",
    "CreativeCore_*.jar",
    "sound-physics-remastered-*.jar",
    "PresenceFootsteps*.jar",
    "Presence-Footsteps*.jar",
)


def verify_shader_defaults(manifest):
    """Reject misspelled options and out-of-range values against the exact pinned shader."""
    defaults = ROOT / 'config/client-defaults'

    def properties(path):
        return dict(line.split('=', 1) for line in path.read_text().splitlines()
                    if line.strip() and not line.lstrip().startswith('#'))

    iris = properties(defaults / 'config/iris.properties')
    bliss = next(e for e in manifest['entries'] if e['project'] == 'bliss-shader')
    assert iris['shaderPack'] == bliss['filename'], 'Iris default does not select the pinned Bliss archive'
    assert iris['enableShaders'] == 'true', 'New instances must enable Bliss'
    options = properties(defaults / 'shaderpacks' / (bliss['filename'] + '.txt'))
    declarations = {}
    with zipfile.ZipFile(ROOT / 'run/shaderpacks' / bliss['filename']) as shader:
        for name in shader.namelist():
            if not name.endswith(('.glsl', '.vsh', '.fsh', '.csh')):
                continue
            for line in shader.read(name).decode().splitlines():
                match = re.match(r'^\s*(?://\s*)?#define\s+(\w+)\b(.*)', line)
                constant = re.match(r'^\s*const\s+(?:int|float|bool)\s+(\w+)\s*=\s*([^;]+);(.*)', line)
                if constant:
                    key, value, comment = constant.groups()
                elif match:
                    key, rest = match.groups()
                    value, _, comment = rest.partition('//')
                else:
                    continue
                value = value.strip()
                allowed = re.search(r'\[([^\]]+)\]', comment)
                if allowed:
                    declarations.setdefault(key, set()).update(allowed[1].split() + [value])
                elif not value:
                    declarations.setdefault(key, set()).update(('true', 'false'))
    for key, value in options.items():
        assert key in declarations, f'Unknown Bliss option: {key}'
        assert value in declarations[key], f'Unsupported Bliss value: {key}={value}'
    assert int(iris['maxShadowRenderDistance']) * 16 >= float(options['shadowDistance'])
    print(f'Validated {len(options)} prehistoric preset options against the pinned Bliss source.')


def verify():
    manifest = json.loads((ROOT / "config/client-mods.lock.json").read_text())
    mods = {}
    requirements = []

    def inspect(data, name):
        with zipfile.ZipFile(BytesIO(data)) as jar:
            metadata = "META-INF/neoforge.mods.toml"
            attributes = jar.read("META-INF/MANIFEST.MF").decode() if "META-INF/MANIFEST.MF" in jar.namelist() else ""
            # Sodium's outer service library carries metadata for tooling; the actual mod is nested.
            if metadata in jar.namelist() and "FMLModType: LIBRARY" not in attributes:
                meta = tomllib.loads(jar.read(metadata).decode())
                for mod in meta.get("mods", []):
                    assert mod["modId"] not in mods, f"Duplicate mod ID: {mod['modId']}"
                    mods[mod["modId"]] = mod["version"]
                for owner, deps in meta.get("dependencies", {}).items():
                    requirements.extend((owner, dep["modId"]) for dep in deps if dep.get("type") == "required")
            for embedded in jar.namelist():
                if embedded.endswith(".jar"):
                    inspect(jar.read(embedded), name + " > " + embedded)

    expected = set()
    for entry in manifest["entries"]:
        folder = ROOT / ("run/shaderpacks" if entry["kind"] == "shader" else "client-mods")
        path = folder / entry["filename"]
        content = path.read_bytes()
        assert len(content) == entry["size"], f"Wrong size: {path.name}"
        assert hashlib.sha512(content).hexdigest() == entry["sha512"], f"Checksum mismatch: {path.name}"
        if entry["kind"] == "mod":
            expected.add(path.name)
            with zipfile.ZipFile(BytesIO(content)) as jar:
                assert "META-INF/neoforge.mods.toml" in jar.namelist(), f"Not a NeoForge JAR: {path.name}"
            inspect(content, path.name)
        else:
            with zipfile.ZipFile(BytesIO(content)) as shader:
                assert any(name.startswith("shaders/") for name in shader.namelist()), "Invalid shader ZIP"
    actual = {
        path.name
        for path in (ROOT / "client-mods").glob("*.jar")
        if not any(fnmatch.fnmatchcase(path.name, pattern) for pattern in INTEGRATED_STANDALONE_GLOBS)
    }
    assert actual == expected, f"Unreviewed/missing client JARs: {actual ^ expected}"
    for owner, required in requirements:
        assert required in mods or required in {"minecraft", "neoforge"}, f"{owner} requires missing {required}"
    shaders = sum(1 for entry in manifest["entries"] if entry["kind"] == "shader")
    verify_shader_defaults(manifest)
    print(f"Verified {len(expected)} pinned NeoForge JARs, {shaders} shader packs, checksums and required mod IDs.")
    print("Embedded XaeroLib:", mods["xaerolib"])
    print("Rendering/audio compatibility still requires the user's client playtest.")


if __name__ == "__main__":
    verify()
