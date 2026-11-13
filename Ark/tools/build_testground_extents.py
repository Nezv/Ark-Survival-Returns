"""Measures every creature's drawn body for the testing ground, which lays its gallery, tanks, bays and arena out by it.

    python tools/build_testground_extents.py

The drawn body is several times a creature's hitbox (a Tyrannosaurus is 32 blocks long on a 2.8-block hitbox), so
feature/testground/TestGroundBuilder sizes each place from this table and not from Species.width. For every species
the script bakes its runtime model the way GeckoLib does (geckolib_mesh), at rest and in the first frame of its idle
clip, and writes the larger of both, in blocks from the creature's feet:

    half_width  to either side          front  ahead of it          back  behind it          height  above its feet

to src/main/resources/data/arksurvivalreturns/testground/extents.json. Run it again after creatures are imported or
remodelled; a species missing from the table gets a guess from its hitbox.
"""
from __future__ import annotations

import json
import math
import re
import sys
from pathlib import Path

import numpy as np

ARK = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ARK / "tools"))
import geckolib_mesh as mesh  # noqa: E402

SPECIES = ARK / "src/main/java/dev/nez/arksurvivalreturns/feature/creature/Species.java"
ASSETS = ARK / "src/main/resources/assets/arksurvivalreturns/geckolib"
OUT = ARK / "src/main/resources/data/arksurvivalreturns/testground/extents.json"
# The imported rigs face +Z and the renderer turns them; the authored wyverns already face -Z (CreatureRenderer).
FACES_MINUS_Z = {"dragon"}


def box(model, pose=None):
    points = np.concatenate([quad[0] for quad in mesh.quads(model, pose)]) / 16.0
    return points.min(0), points.max(0)


def measure(species: str, idle: str):
    models = sorted((ASSETS / "models/entity").glob(f"{species}.geo.json")) or sorted((ASSETS / "models/entity").glob(f"{species}_*.geo.json"))
    if not models:
        return None
    clips = ASSETS / "animations/entity" / f"{species}.animation.json"
    clip = json.loads(clips.read_text(encoding="utf-8")).get("animations", {}).get(idle) if clips.is_file() else None
    low, high = np.full(3, math.inf), np.full(3, -math.inf)
    for path in models:
        model = mesh.load(path)
        for pose in (None, mesh.clip_pose(clip, 0.0) if clip else None):
            lo, hi = box(model, pose)
            low, high = np.minimum(low, lo), np.maximum(high, hi)
    front, back = (-low[2], high[2]) if species in FACES_MINUS_Z else (high[2], -low[2])
    return {"half_width": round(float(max(-low[0], high[0])), 1), "front": round(float(max(front, 0)), 1),
            "back": round(float(max(back, 0)), 1), "height": round(float(high[1]), 1)}


def main() -> int:
    source = SPECIES.read_text(encoding="utf-8")
    rows = re.findall(r'^\s+[A-Z_]+\("([a-z_]+)", "[^"]+", (?:[\d.]+, ){3}[\d.]+f, [\d.]+f, (?:\d+, ){3}(?:true|false), "([^"]+)"', source, re.M)
    table, missing = {}, []
    for species, idle in rows:
        extent = measure(species, idle)
        if extent is None:
            missing.append(species)
        else:
            table[species] = extent
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(table, indent=2) + "\n", encoding="utf-8", newline="\n")
    longest = max(table, key=lambda name: table[name]["front"] + table[name]["back"])
    print(f"{len(table)} species -> {OUT}; longest {longest} {table[longest]}" + (f"; no model: {', '.join(missing)}" if missing else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
