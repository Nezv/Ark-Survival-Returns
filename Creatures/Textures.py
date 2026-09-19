#!/usr/bin/env python3
"""Generate anatomy-aware texture variants for the complete creature roster.

The Giganotosaur painter remains the rendering engine.  This superset owns the
body-plan registry, density profiles, batch orchestration, documentation table,
and optional runtime import.
"""
from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path
import subprocess
import sys

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
CREATURES = ROOT / "Creatures"
PAINTER_PATH = CREATURES / "Giganotosaur" / "textures" / "build_skin_v7.py"
PALETTE_PATH = CREATURES / "Giganotosaur" / "textures" / "batch_palettes.json"
TABLE_PATH = CREATURES / "textures.md"

spec = importlib.util.spec_from_file_location("ark_texture_painter", PAINTER_PATH)
painter = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(painter)

VARIANTS = ("Ivory", "Darken", "Emerald", "Midnight", "Burgundy")

# Body plans are deliberately anatomical rather than taxonomic: they tune UV
# density for the silhouette while the painter derives head, jaw, dorsal,
# ventral, limb, eye, claw, tongue, feather, and horn regions from each rig.
ANATOMY = {
    "Bipedal theropod": {
        "density": 2.0,
        "creatures": ("Acrochantosaur", "Allosaurus", "Carnotaurus", "Ceratosaurus",
                      "Dilophosaur", "Giganotosaur", "Spinosaurus", "Terrorbird",
                      "Therezinosaur", "Tyranosaur", "Velociraptor"),
    },
    "Armored or horned quadruped": {
        "density": 1.7,
        "creatures": ("Ankylosaurus", "Lystrosaurus", "Parasaur", "Triceratops"),
    },
    "Sauropod": {"density": 0.7, "creatures": ("Brontosaur", "Titanosaur")},
    "Winged vertebrate": {
        "density": 1.35,
        "creatures": ("Archaeopteryx", "Argentavis", "Piterodon", "Quetzal"),
    },
    "Winged dragon": {"density": 0.85, "creatures": ("Dragon",)},
    "Crocodyliform": {"density": 1.35, "creatures": ("Deinosuchus", "Kaprosuchus", "Sarco")},
    "Serpentine": {"density": 1.7, "creatures": ("Titanoboa",)},
    "Marine flipper": {"density": 1.45, "creatures": ("Liopleurodon", "Mosasaurus", "Plesiosaur")},
    "Shark": {"density": 1.35, "creatures": ("Megalodon",)},
    "Cephalopod": {"density": 1.45, "creatures": ("Tusoteuthis",)},
    "Cnidarian": {"density": 1.1, "creatures": ("Cnidaria",)},
    "Quadrupedal mammal": {
        "density": 1.6,
        "creatures": ("Direwolf", "Mammoth", "Megalocerus", "Paraceratherium",
                      "Ravager", "Sabertooth", "Unicorn"),
    },
    "Primate": {"density": 1.15, "creatures": ("Megapithecus",)},
    "Small biped": {"density": 1.8, "creatures": ("Pegomastax",)},
}

RUNTIME_IDS = {
    "Acrochantosaur": "acrocanthosaurus", "Allosaurus": "allosaurus",
    "Ankylosaurus": "ankylosaurus", "Archaeopteryx": "archaeopteryx",
    "Argentavis": "argentavis", "Brontosaur": "brontosaurus",
    "Carnotaurus": "carnotaurus", "Ceratosaurus": "ceratosaurus",
    "Cnidaria": "cnidaria", "Deinosuchus": "deinosuchus",
    "Dilophosaur": "dilophosaur", "Direwolf": "direwolf", "Dragon": "dragon",
    "Giganotosaur": "giganotosaurus", "Kaprosuchus": "kaprosuchus",
    "Liopleurodon": "liopleurodon", "Lystrosaurus": "lystrosaurus",
    "Mammoth": "mammoth", "Megalocerus": "megalocerus", "Megalodon": "megalodon",
    "Megapithecus": "megapithecus", "Mosasaurus": "mosasaurus",
    "Paraceratherium": "paraceratherium", "Parasaur": "parasaur",
    "Pegomastax": "pegomastax", "Piterodon": "pteranodon", "Plesiosaur": "plesiosaur",
    "Quetzal": "quetzal", "Ravager": "ravager", "Sabertooth": "sabertooth",
    "Sarco": "sarco", "Spinosaurus": "spinosaurus", "Terrorbird": "terrorbird",
    "Therezinosaur": "therizinosaurus", "Titanoboa": "titanoboa",
    "Titanosaur": "titanosaur", "Triceratops": "triceratops",
    "Tusoteuthis": "tusoteuthis", "Tyranosaur": "tyrannosaurus",
    "Unicorn": "unicorn", "Velociraptor": "velociraptor",
}


def roster() -> dict[str, str]:
    result = {}
    for anatomy, profile in ANATOMY.items():
        for creature in profile["creatures"]:
            if creature in result:
                raise ValueError(f"{creature} is assigned to two anatomy groups")
            result[creature] = anatomy
    folders = {path.name for path in CREATURES.iterdir()
               if path.is_dir() and path.name != "Collection" and any(path.glob("*.bbmodel"))}
    if set(result) != folders or set(result) != set(RUNTIME_IDS):
        raise ValueError(f"Roster drift: missing={sorted(folders - set(result))}, extra={sorted(set(result) - folders)}")
    return result


def settings_for(name: str, anatomy: str, settings: dict, variant: str | None = None) -> dict:
    profile = ANATOMY[anatomy]
    config = {**painter.DEFAULTS, **settings.get("defaults", {}),
              "pixels_per_unit": profile["density"],
              **settings.get("creatures", {}).get(name, {})}
    if variant:
        config.update(settings["variants"][variant])
    return config


def write_output(name: str, anatomy: str, config: dict, variant: str | None, dry_run: bool) -> dict:
    creature = CREATURES / name
    geo, model, cubes, skeleton, faces, style, canvas, colors, report, _ = painter.make_texture(creature, config)
    report.update({"anatomy": anatomy, "variant": variant or "Base"})
    if dry_run:
        return report
    output = creature / "textures" / variant if variant else creature / "textures"
    output.mkdir(parents=True, exist_ok=True)
    Image.fromarray(canvas).save(output / "skin.png")
    (output / "skin_report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    if variant is None:
        painter.st.assign_uvs(faces)
        painter.st.write_geo(geo, cubes, len(canvas), output / geo.name)
    return report


def write_table(mapping: dict[str, str]) -> None:
    lines = [
        "# Procedural creature textures", "",
        "Each creature receives the five runtime variants `Ivory`, `Darken`, `Emerald`, `Midnight`, and `Burgundy`.",
        "The anatomy group controls atlas density; the shared painter derives the detailed body regions directly from each rig.", "",
        "| Anatomy | Creatures | UV density | Procedural treatment |", "|---|---|---:|---|",
    ]
    treatment = "Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails"
    for anatomy, profile in ANATOMY.items():
        creatures = ", ".join(profile["creatures"])
        lines.append(f"| {anatomy} | {creatures} | {profile['density']:.2f} px/unit | {treatment} |")
    lines.extend(["", "## Generate and ship", "", "```powershell",
                  "python Creatures/Textures.py --ship-runtime", "```", "",
                  "Use `--dry-run` to validate every atlas without writing files. Use `--creatures NAME ...` or `--anatomies NAME ...` for a subset.", ""])
    TABLE_PATH.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--creatures", nargs="+", choices=sorted(RUNTIME_IDS))
    parser.add_argument("--anatomies", nargs="+", choices=list(ANATOMY))
    parser.add_argument("--variants", nargs="+", choices=VARIANTS, default=list(VARIANTS))
    parser.add_argument("--skip-base", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--ship-runtime", action="store_true",
                        help="Run Ark/tools/import_creatures.py after a complete successful build.")
    args = parser.parse_args()
    mapping = roster()
    selected = set(args.creatures or mapping)
    if args.anatomies:
        selected &= {name for anatomy in args.anatomies for name in ANATOMY[anatomy]["creatures"]}
    settings = json.loads(PALETTE_PATH.read_text(encoding="utf-8"))
    failures = []
    for name in mapping:
        if name not in selected:
            continue
        anatomy = mapping[name]
        try:
            reports = []
            if not args.skip_base:
                reports.append(write_output(name, anatomy, settings_for(name, anatomy, settings), None, args.dry_run))
            for variant in args.variants:
                reports.append(write_output(name, anatomy, settings_for(name, anatomy, settings, variant), variant, args.dry_run))
            sizes = sorted({report["atlas_size"] for report in reports})
            suffix = " [dry run]" if args.dry_run else ""
            print(f"{name} ({anatomy}): {len(reports)} textures, atlas={sizes}px{suffix}")
        except Exception as error:
            failures.append(name)
            print(f"{name}: FAILED: {type(error).__name__}: {error}", file=sys.stderr)
    if failures:
        raise SystemExit(f"Texture generation failed for: {', '.join(failures)}")
    if not args.dry_run:
        write_table(mapping)
    if args.ship_runtime:
        if args.dry_run or selected != set(mapping) or set(args.variants) != set(VARIANTS) or args.skip_base:
            raise SystemExit("Runtime shipping requires a complete non-dry build with the base and all five variants.")
        subprocess.run([sys.executable, str(ROOT / "Ark" / "tools" / "import_creatures.py")],
                       cwd=ROOT / "Ark", check=True)


if __name__ == "__main__":
    main()
