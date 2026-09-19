"""Offline validation of the pinned optional pack, including embedded NeoForge mods."""
from pathlib import Path
from io import BytesIO
import hashlib
import fnmatch
import json
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
    print(f"Verified {len(expected)} pinned NeoForge JARs, one shader pack, checksums and required mod IDs.")
    print("Embedded XaeroLib:", mods["xaerolib"])
    print("Rendering/audio compatibility still requires the user's client playtest.")


if __name__ == "__main__":
    verify()
