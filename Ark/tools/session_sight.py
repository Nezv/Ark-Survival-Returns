"""What stood between each creature's eyes and the recorded player when the game found no line of sight.

    python tools/session_sight.py [session] [--world NAME] [--within 32]

The recording holds every sense check with the positions of both bodies, and the saved world holds the
blocks. This walks the game's own eye-to-eye ray through the saved blocks (read-only) and names the first
block with a collision box on it: ground, a trunk or leaves. The walk is compared with the recorded central
eye ray; newer recordings also count checks where an alternate physical ray supplied line of sight.
Use the world the session was played on (session_run.py plays a copy of it; the terrain is the same).
"""
from __future__ import annotations

import argparse
import gzip
import json
import math
import struct
import sys
import zlib
from collections import Counter
from pathlib import Path

import duckdb

from session_analyze import analyse, locate

SAVES = Path(__file__).resolve().parent.parent / "run" / "saves"
# Words in the names of blocks without a collision box; a sight ray passes through them.
PASSABLE = ("air", "grass", "fern", "flower", "tulip", "dandelion", "poppy", "orchid", "allium", "bluet", "daisy", "cornflower",
            "lilac", "peony", "rose_bush", "sunflower", "sapling", "mushroom", "vine", "torch", "snow", "water", "lava",
            "dead_bush", "sugar_cane", "kelp", "seagrass", "lichen", "petals", "carpet", "button", "rail", "sweet_berry",
            "dripleaf", "spore_blossom", "hanging_roots", "bush", "leaf_litter", "loose_rock", "lily_of_the_valley", "oxeye",
            "azure", "pitcher", "firefly")


def nbt(buf: bytes, pos: int, tag: int):
    """Reads one NBT value of the given type; returns it with the position after it."""
    if tag == 1:
        return struct.unpack_from(">b", buf, pos)[0], pos + 1
    if tag == 2:
        return struct.unpack_from(">h", buf, pos)[0], pos + 2
    if tag == 3:
        return struct.unpack_from(">i", buf, pos)[0], pos + 4
    if tag == 4:
        return struct.unpack_from(">q", buf, pos)[0], pos + 8
    if tag == 5:
        return struct.unpack_from(">f", buf, pos)[0], pos + 4
    if tag == 6:
        return struct.unpack_from(">d", buf, pos)[0], pos + 8
    if tag == 7:
        size = struct.unpack_from(">i", buf, pos)[0]
        return buf[pos + 4:pos + 4 + size], pos + 4 + size
    if tag == 8:
        size = struct.unpack_from(">H", buf, pos)[0]
        return buf[pos + 2:pos + 2 + size].decode("utf-8", "replace"), pos + 2 + size
    if tag == 9:
        kind, size = buf[pos], struct.unpack_from(">i", buf, pos + 1)[0]
        pos += 5
        values = []
        for _ in range(size):
            value, pos = nbt(buf, pos, kind)
            values.append(value)
        return values, pos
    if tag == 10:
        values = {}
        while True:
            kind = buf[pos]
            pos += 1
            if kind == 0:
                return values, pos
            size = struct.unpack_from(">H", buf, pos)[0]
            name = buf[pos + 2:pos + 2 + size].decode("utf-8", "replace")
            values[name], pos = nbt(buf, pos + 2 + size, kind)
    if tag == 11:
        size = struct.unpack_from(">i", buf, pos)[0]
        return struct.unpack_from(f">{size}i", buf, pos + 4), pos + 4 + 4 * size
    if tag == 12:
        size = struct.unpack_from(">i", buf, pos)[0]
        return struct.unpack_from(f">{size}Q", buf, pos + 4), pos + 4 + 8 * size
    raise ValueError(f"NBT tag {tag}")


class World:
    """Block names of a saved world, chunk by chunk from its region files."""

    def __init__(self, regions: Path):
        self.regions, self.chunks = regions, {}

    def chunk(self, cx: int, cz: int):
        key = (cx, cz)
        if key not in self.chunks:
            self.chunks[key] = self.load(cx, cz)
        return self.chunks[key]

    def load(self, cx: int, cz: int):
        path = self.regions / f"r.{cx >> 5}.{cz >> 5}.mca"
        if not path.is_file():
            return None
        with path.open("rb") as handle:
            handle.seek(4 * ((cx & 31) + (cz & 31) * 32))
            entry = struct.unpack(">I", handle.read(4))[0]
            if not entry >> 8:
                return None
            handle.seek((entry >> 8) * 4096)
            length, packing = struct.unpack(">IB", handle.read(5))
            raw = handle.read(length - 1)
        data = zlib.decompress(raw) if packing == 2 else gzip.decompress(raw) if packing == 1 else raw if packing == 3 else None
        if data is None:
            return None
        root, _ = nbt(data, 3 + struct.unpack_from(">H", data, 1)[0], 10)
        sections = {}
        for section in root.get("sections", []):
            states = section.get("block_states")
            if states:
                sections[section["Y"]] = ([entry["Name"] for entry in states["palette"]], states.get("data"))
        return sections

    def block(self, x: int, y: int, z: int) -> str | None:
        """Block name, or None where the chunk is not in the save."""
        sections = self.chunk(x >> 4, z >> 4)
        if sections is None:
            return None
        section = sections.get(y >> 4)
        if section is None:
            return "minecraft:air"
        palette, data = section
        if data is None or len(palette) == 1:
            return palette[0]
        bits = max(4, (len(palette) - 1).bit_length())
        per = 64 // bits
        index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
        return palette[data[index // per] >> ((index % per) * bits) & ((1 << bits) - 1)]

    def first_hit(self, a: tuple, b: tuple) -> str | None:
        """First block with collision in the cells the segment crosses, walked the way the game's ray does."""
        cell, last = [math.floor(v) for v in a], [math.floor(v) for v in b]
        d = [b[k] - a[k] for k in range(3)]
        step = [(d[k] > 0) - (d[k] < 0) for k in range(3)]
        t_delta = [abs(1 / d[k]) if d[k] else math.inf for k in range(3)]
        t_max = [((cell[k] + (step[k] > 0)) - a[k]) / d[k] if d[k] else math.inf for k in range(3)]
        for _ in range(1024):
            name = self.block(*cell)
            if name is None:
                return "unsaved"
            if not passable(name):
                return name.split(":")[-1]
            k = t_max.index(min(t_max))
            if cell == last or t_max[k] > 1:
                break
            cell[k] += step[k]
            t_max[k] += t_delta[k]
        return None


def passable(name: str) -> bool:
    short = name.split(":")[-1]
    return short.endswith("air") or (any(word in short for word in PASSABLE) and "block" not in short)


def kind(hit: str | None) -> str:
    if hit is None:
        return "nothing"
    if hit == "unsaved":
        return "chunk not in the save"
    if "leaves" in hit:
        return "leaves"
    if "log" in hit or hit.endswith("_wood") or hit.endswith("_stem"):
        return "trunk"
    return "ground"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("session", nargs="?", help="session folder or file; default: the newest under run/diagnostics")
    parser.add_argument("--world", help="folder under run/saves the session was played on (default: the one played last)")
    parser.add_argument("--within", type=float, default=32, help="only checks with the bodies closer than this many blocks")
    arguments = parser.parse_args()

    source = locate(arguments.session)
    database = source.parent / "session.duckdb"
    if not database.is_file():
        analyse(source, source.parent)[0].close()
    worlds = [folder for folder in SAVES.iterdir() if (folder / "level.dat").is_file()]
    save = SAVES / arguments.world if arguments.world else max(worlds, key=lambda folder: (folder / "level.dat").stat().st_mtime)
    regions = save / "dimensions" / "minecraft" / "overworld" / "region"
    if not regions.is_dir():
        regions = save / "region"
    if not regions.is_dir():
        sys.exit(f"no region files under {save}")
    world = World(regions)

    con = duckdb.connect(str(database), read_only=True)
    columns = {row[0] for row in con.execute("DESCRIBE decision_players").fetchall()}
    eye = "coalesce(n.los_eye, n.los)" if "los_eye" in columns else "n.los"
    rows = con.execute(f"""
        SELECT c.species, d.x, d.y + c.eye, d.z, p.x, p.y + coalesce(s.eye, 1.62), p.z, {eye}, n.los
        FROM decision_players n JOIN subject u ON u.e = n.player JOIN decisions d ON d.seq = n.seq JOIN entities c ON c.e = n.e
        JOIN players p ON p.tick = n.tick AND p.e = n.player
        ASOF LEFT JOIN player_status s ON s.e = n.player AND s.tick <= n.tick
        WHERE n.dist < ? AND n.los IS NOT NULL AND n.route_loaded""", [arguments.within]).fetchall()
    con.close()
    agree, differ, blockers, blocks, species = 0, 0, Counter(), Counter(), {}
    alternate_clear, effective_blocked = 0, 0
    for name, x, y, z, px, py, pz, clear, effective in rows:
        alternate_clear += bool(effective) and not bool(clear)
        effective_blocked += not bool(effective)
        hit = world.first_hit((x, y, z), (px, py, pz))
        if hit == "unsaved":
            blockers[kind(hit)] += 1
            continue
        if (hit is None) == bool(clear):
            agree += 1
        else:
            differ += 1
        if not clear:
            blockers[kind(hit)] += 1
            blocks[hit] += 1
            species.setdefault(name, Counter())[kind(hit)] += 1
    blocked = sum(count for what, count in blockers.items() if what != "chunk not in the save")
    result = {"world": save.name, "within_blocks": arguments.within, "checks": len(rows), "walk_agrees_with_game": agree,
              "walk_differs": differ, "no_line_of_sight": effective_blocked, "eye_ray_blocked": blocked,
              "alternate_rays_clear": alternate_clear, "blocked_by": dict(blockers.most_common()),
              "blocks": dict(blocks.most_common(12)), "by_species": {name: dict(count.most_common()) for name, count in species.items()}}
    (source.parent / "sight.json").write_text(json.dumps(result, indent=1), encoding="utf-8")
    print(f"world '{save.name}': {len(rows)} sense checks within {arguments.within:g} blocks; the walk through the saved blocks agrees "
          f"with the game on {agree}, differs on {differ}")
    print(f"central eye ray blocked in {blocked}; alternate rays clear in {alternate_clear}; no line of sight in {effective_blocked}")
    print(f"first block on the central ray: {dict(blockers.most_common())}")
    print(f"blocks: {dict(blocks.most_common(12))}")
    for name, count in sorted(species.items(), key=lambda item: -sum(item[1].values())):
        print(f"  {name:<16} {dict(count.most_common())}")
    print(f"written: {source.parent / 'sight.json'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
