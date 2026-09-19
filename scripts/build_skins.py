#!/usr/bin/env python3
"""Batch the painted skin studio over the theropod roster.

Creates a new ``skin/`` folder inside each creature directory (texture, textured
geo, Blockbench project, previews, report) plus a collection overview. Existing
files are never modified.

    python scripts/build_skins.py
    python scripts/build_skins.py --species Allosaurus Carnotaurus
    python scripts/build_skins.py --species Giganotosaur --suffix _v2
    python scripts/build_skins.py --list
"""
from __future__ import annotations

import argparse
import json
import sys
import traceback
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
import skin_studio as studio  # noqa: E402
from ark_geometry import ROOT  # noqa: E402
from build_dinosaurs import PALETTES  # noqa: E402

THEROPODS = [
    "Acrochantosaur",
    "Allosaurus",
    "Carnotaurus",
    "Ceratosaurus",
    "Dilophosaur",
    "Spinosaurus",
    "Tyranosaur",
    "Velociraptor",
    "Therezinosaur",
]
REFERENCE = "Giganotosaur"
ROSTER = THEROPODS + [REFERENCE]
COLLECTION = ROOT / "Creatures" / "Collection" / "skins"


def font(size):
    for name in ("segoeui.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(f"C:/Windows/Fonts/{name}", size)
        except OSError:
            continue
    return ImageFont.load_default()


def overview(labels, destination, suffix=""):
    previews = []
    for label in labels:
        path = ROOT / "Creatures" / label / f"skin{suffix}" / "previews" / "three_quarter.png"
        if path.exists():
            previews.append((label, Image.open(path).convert("RGB")))
    if not previews:
        return
    columns = 3
    tile_w, tile_h = 520, 369
    rows = (len(previews) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * tile_w, rows * tile_h + 54), (14, 21, 28))
    for index, (label, image) in enumerate(previews):
        sheet.paste(image.resize((tile_w, tile_h)), ((index % columns) * tile_w, (index // columns) * tile_h))
    draw = ImageDraw.Draw(sheet)
    draw.text((18, rows * tile_h + 14),
              f"theropod skin batch  /  {len(previews)} species  /  painted atlas + true per-face UVs",
              fill="#8fa6a4", font=font(17))
    destination.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(destination)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--species", nargs="+", choices=ROSTER, default=THEROPODS)
    parser.add_argument("--list", action="store_true", help="print the theropod roster and exit")
    parser.add_argument("--no-previews", action="store_true")
    parser.add_argument("--skip-overview", action="store_true")
    parser.add_argument("--suffix", default="", help="output folder suffix, e.g. _v2")
    parser.add_argument("--reference", action="store_true", help="include the hand-tuned Giganotosaur reference")
    args = parser.parse_args()
    if args.list:
        for label in ROSTER:
            print(label)
        return

    species = list(args.species)
    if args.reference and REFERENCE not in species:
        species.append(REFERENCE)

    reports, failures = [], []
    for label in species:
        creature_dir = ROOT / "Creatures" / label
        palette = PALETTES.get(label)
        if palette is None:
            failures.append({"creature": label, "error": "no palette entry in build_dinosaurs.PALETTES"})
            print(f"{label:16s} SKIP (no palette)")
            continue
        try:
            report, geo_path, model_path, texture_path = studio.build_skin(
                label, creature_dir, palette, style=studio.SPECIES_STYLE.get(label),
                out_name=f"skin{args.suffix}", write_previews=not args.no_previews)
            reports.append(report)
            checks = report["checks"]
            bad = [name for name, value in checks.items() if not value]
            metrics = report["metrics"]
            print(f"{label:16s} {report['texture']['size'][0]}px  cubes={report['cubes']:3d}  "
                  f"colours={metrics['palette_colors']:2d}  symmetry={metrics['bilateral_symmetry_delta']:5.1f}  "
                  f"seam={metrics['seam_delta_mean']:5.1f}  {'OK' if not bad else 'CHECK ' + ','.join(bad)}")
        except Exception as error:  # keep the batch going
            failures.append({"creature": label, "error": str(error), "trace": traceback.format_exc()})
            print(f"{label:16s} FAILED: {error}")

    COLLECTION.mkdir(parents=True, exist_ok=True)
    batch = {
        "reference": REFERENCE,
        "suffix": args.suffix,
        "species": [report["creature"] for report in reports],
        "reports": reports,
        "failures": failures,
    }
    report_path = COLLECTION / f"report{args.suffix}.json"
    report_path.write_text(json.dumps(batch, indent=2) + "\n", encoding="utf-8")
    if not args.skip_overview:
        labels = [report["creature"] for report in reports]
        if REFERENCE not in labels and (ROOT / "Creatures" / REFERENCE / f"skin{args.suffix}"
                                        / "previews" / "three_quarter.png").exists():
            labels.insert(0, REFERENCE)
        overview(labels, COLLECTION / f"overview{args.suffix}.png", args.suffix)
    print(f"\ncollection report -> {report_path}")
    print(f"overview          -> {COLLECTION / f'overview{args.suffix}.png'}")
    if failures:
        print(f"{len(failures)} failure(s): " + ", ".join(f["creature"] for f in failures))


if __name__ == "__main__":
    main()
