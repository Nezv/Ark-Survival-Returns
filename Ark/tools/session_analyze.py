"""Analyse a creature session recording (feature/recorder/SessionRecorder.java).

    python tools/session_analyze.py                  newest recording under run/diagnostics
    python tools/session_analyze.py <dir-or-file>    a given recording
    ... --window E [T0 T1]                            compact timeline of creature E (game seconds)
    ... --sql "SELECT ..."                            a query against the session database
    ... --parquet                                     also export every table as Parquet

Reads session.jsonl or session.jsonl.gz line by line with the standard library, checks that no record
is missing (sequence numbers, footer, a cut last line), and loads typed tables into session.duckdb.
From those it writes summary.json (counts, coverage, timing) and incidents.jsonl (ranked episodes:
ignored approach, sensed but holding, pursuit without progress, failed or deferred paths, stalls with
the blocks around the body, missed strikes, and the ordinary responses for reference).

An incident is a hypothesis with its evidence (creature, ticks, distances, source sequence numbers);
read the window around it before calling it a cause. Times are game seconds: ticks since the first
recorded tick, divided by 20. Requires: pip install duckdb
"""
from __future__ import annotations

import argparse
import gzip
import json
import math
import sys
import tempfile
import zlib
from collections import Counter, defaultdict
from pathlib import Path

import duckdb

DIAGNOSTICS = Path(__file__).resolve().parents[1] / "run" / "diagnostics"
I, L, D, B, S = "INTEGER", "BIGINT", "DOUBLE", "BOOLEAN", "VARCHAR"


def xyz(prefix: str, kind: str) -> dict[str, str]:
    return {f"{prefix}x": kind, f"{prefix}y": kind, f"{prefix}z": kind}


# What a creature and its routine report about themselves; shared by the status and decision tables.
CREATURE_FLAGS = ("night", "tamed", "ridden", "torpor", "loco", "steer", "pivoting", "regroup", "hold", "trample")
CREATURE = {
    "st": S, "act": S, "tier": S, "night": B, "tamed": B, "ridden": B, "torpor": B, "loco": B, "engaged": I,
    "strike_in": I, "strike_cd": I, "steer": B, "pivoting": B, "turn": D, "pace": D,
    "why": S, "hunger": D, "thirst": D, "fatigue": D, "aware": D, "s_age": I, "mem": I, "warn": I, "prov": I,
    "chase": I, "rec": I, "feed": I, "calm": I, "flight": I, **xyz("home_", I), **xyz("herd_home_", I),
    "focus": I, **xyz("known_", D), **xyz("dest_", D), "herd_threat": I, "fail": I, "fail_escape": I,
    "cornered_t": I, "alarm_t": I, "alarm_in": I, "regroup": B, "routine_in": I, "mode": S, "hold": B, "beat": I,
    **xyz("water_dest_", D), **xyz("approach_", D), "water_scan": I, "trample": B,
    "beats": I, "amb": S, "amb_t": I, "phase": S, "phase_t": I, "thief": S, "defend_left": L,
    **xyz("fly_to_", D), **xyz("nest_", I), **xyz("roost_", I), "blocked": I, "lap": S, "hover": I,
}
DECISION_FLAGS = ("ground", "in_water", "s_vis", "alarm", "vis", "prey", "intr", "atk", "intim", "far", "water", "forage", "dark", "cycle",
                  "night_r", "sleepy", "unsafe", "danger", "defended", "cornered", "regroup_r", "guarded", "woken",
                  "changed", "urgent")
SENSE_FLAGS = ("in_range", "route_loaded", "los", "invisible", "seen", "moving", "crouch", "smelt", "wet")
TABLES: dict[str, dict[str, str]] = {
    "ticks": {"seq": L, "tick": I, "ns": L, "gt": L, "act_us": L, "paused_us": L, "dur_us": L, "cap_us": L, "mspt": D,
              "mobs": I, "near": I, "q": I, "dropped": L, "paths": I, "deferred": I, "frozen": B, "snap": B, "no_subject": B},
    "entities": {"seq": L, "tick": I, "ns": L, "e": I, "uuid": S, "type": S, "mc": I, "origin": S, "note": S, "dim": S,
                 "w": D, "h": D, "eye": D, **xyz("", D), "age": I, "hp": D, "mhp": D, "speed": D, "follow": D, "step": D,
                 "dmg": D, "cat": S, "spawn": S, "persistent": B, "tg": I, "ark": B, "species": S, "realm": S, "lvl": I,
                 "pack": S, "predator": B, "natural": B, "tamed": B, "danger": I, "name": S, "subject": B},
    "status": {"seq": L, "tick": I, "e": I, **xyz("", D), "yr": D, "br": D, "hr": D, "xr": D, **xyz("v", D), "hp": D,
               "age": I, "f": I, "zza": D, "spd": D, "mhp": D, "w": D, "h": D, "tg": I, "pid": I, "pi": I, "pn": I, "near": B,
               "dk": I,
               "goals": S, **CREATURE},
    "decisions": {"seq": L, "tick": I, "ns": L, "e": I, **xyz("", D), "yr": D, "age": I, "ground": B, "in_water": B,
                  **CREATURE, "scan": D, "cand": I,
                  "capped": I, "checked": I, "sensed": I, "s_gap": D, "s_vis": B, "s_str": D, "s_by": S, "score": D,
                  "forced": S, "alarm": B, **xyz("noise_", D), "st0": S, "sig": D, "vis": B, "prey": B, "intr": B,
                  "atk": B, "intim": B, "far": B, "water": B, "forage": B, "dark": B, "hpr": D, "cycle": B, "night_r": B,
                  "sleepy": B, "unsafe": B, "danger": B, "defended": B, "cornered": B, "regroup_r": B, "guarded": B,
                  "woken": B, "changed": B, "urgent": B, "tg": I, "br": S, "nav_more": I, "nav_n": I, "nav_o": S},
    "decision_players": {"seq": L, "tick": I, "e": I, "player": I, "stage": S, "body": D, "wake": D, "creative": B,
                         "spectator": B, "dead": B, "score": D, "invalid": B, "disguised": B, "dist": D, "sight": D,
                         "near_range": D, "fov": D, "in_range": B, "route_loaded": B, "los": B, "los_eye": B, "los_rays": I, "invisible": B,
                         "seen": B, "hear": D, "moving": B, "crouch": B, "scent": D, "downwind": D, "smelt": B, "wet": B,
                         "str": D},
    "decision_nav": {"seq": L, "tick": I, "e": I, "i": I, "o": S, **xyz("dest_", D), "fail": I},
    "motion": {"seq": L, "tick": I, "e": I, **xyz("", D), "yaw": D, "body": D, "head": D, **xyz("v", D), "flags": I,
               "zza": D, "speed": D, "node": I},
    "players": {"seq": L, "tick": I, "e": I, **xyz("", D), "yr": D, "xr": D, "f": I, "hp": D},
    "player_status": {"seq": L, "tick": I, "e": I, "dim": S, "eye": D, "w": D, "h": D, "mode": S, "valid_target": B,
                      "food": I, "armor": I, "held": S, "visibility": D, "noise": D, "scent": D, "vehicle": I, "view": I},
    "events": {"seq": L, "tick": I, "ns": L, "ev": S, "e": I, "tg": I, "at_e": I, "by_e": I, "from_name": S, "to_name": S,
               "why": S, "r": S, "o": S, "src": S, "via": S, "note": S, "origin": S, "dim": S, "block": S, "where_": S,
               "error": S, "act": S, **xyz("", D), **xyz("dest_", D), **xyz("pos_", I), **xyz("node_", I), "yr": D,
               "orig": D, "dmg": D, "hp": D, "radius": D, "moved": D, "over_ticks": I, "windup": I, "cd": I, "gap": D,
               "fail": I, "pid": I, "pi": I, "pn": I, "hcol": B, "ground": B, "pivoting": B, "terrain": B, "same_place": B,
               "no_terrain": B},
    "paths": {"seq": L, "tick": I, "e": I, "id": I, "prev": I, "pn": I, "pi": I, "reach": B, "left_": D,
              **xyz("goal_", I), "nodes": "INTEGER[]", "trunc": B, "done": B, "stuck": B, "ended": B, **xyz("", D)},
    "terrain": {"seq": L, "tick": I, "e": I, "why": S, **xyz("o", I), **xyz("s", I), "box": "DOUBLE[]", "pal": "VARCHAR[]",
                "b": "INTEGER[]", "shapes": "INTEGER[]", "scanned": I, "unloaded": I, "clip": B, "trunc": B},
    "terrain_blocks": {"seq": L, "tick": I, "e": I, **xyz("", I), "block": S, "kind": I},
    "world": {"seq": L, "tick": I, "dim": S, "gt": L, "day": L, "rain": B, "thunder": B, "bright": B, "wind": D,
              "diff": S, "chunks": I},
    "gaps": {"from_seq": L, "to_seq": L, "n": L, "tick0": I, "tick1": I, "why": S},
}
RENAMES = {"s": "seq", "k": "tick", "from": "from_name", "to": "to_name", "where": "where_", "left": "left_",
           "pivot": "pivoting", "drop": "dropped", "over": "over_ticks", "at": "at_e", "by": "by_e"}
VECTORS = {"p": "", "v": "v", "known": "known_", "dest": "dest_", "home": "home_", "herd_home": "herd_home_",
           "water_dest": "water_dest_", "approach": "approach_",
           "fly_to": "fly_to_", "nest": "nest_", "roost": "roost_", "noise": "noise_", "goal": "goal_", "node": "node_",
           "pos": "pos_", "o": "o", "size": "s"}
TRAVEL_ACTIONS = ("WALK", "RUN", "STALK", "CHASE", "BOLT")
COMBAT = ("HUNT", "DEFEND")
NAV_BLOCKED = ("no_path", "budget_deferred", "route_unloaded", "surroundings_unloaded", "no_ground", "no_destination",
               "abandoned", "cornered")
H_COLLIDE, PATHING, PIVOT = 4, 128, 512


# ---------------------------------------------------------------------------------- reading

def locate(argument: str | None) -> Path:
    """The recording to read: a file, a session folder, or the newest folder under run/diagnostics."""
    if argument:
        target = Path(argument)
        if target.is_file():
            return target
        folders = [target]
    else:
        # Session folders are named by their start time; the GameTest fixture folder only counts when nothing else exists.
        folders = sorted((p for p in DIAGNOSTICS.iterdir() if p.is_dir()), key=lambda p: (p.name[:1].isdigit(), p.name),
                         reverse=True) if DIAGNOSTICS.is_dir() else []
    for folder in folders:
        # The plain file is only deleted once the archive was verified, so when both exist the archive is suspect.
        for name in ("session.jsonl", "session.jsonl.gz"):
            if (folder / name).is_file():
                return folder / name
    sys.exit(f"No session.jsonl or session.jsonl.gz found in {argument or DIAGNOSTICS}")


def read(path: Path, integrity: dict):
    """Yields every complete, parseable record. A cut last line and unreadable lines are counted, not fatal."""
    opener = gzip.open if path.suffix == ".gz" else open
    with opener(path, "rb") as handle:
        number = 0
        try:
            for raw in handle:
                number += 1
                if not raw.endswith(b"\n"):
                    integrity["cut_last_line"] = True
                    integrity["cut_last_line_bytes"] = len(raw)
                    return
                try:
                    yield json.loads(raw)
                except ValueError:
                    integrity["unreadable_lines"] = integrity.get("unreadable_lines", 0) + 1
                    integrity.setdefault("first_unreadable_line", number)
        except (EOFError, zlib.error, gzip.BadGzipFile, OSError) as error:
            integrity["archive_error"] = f"{type(error).__name__}: {error}"


class Splitter:
    """Sorts the mixed records into one newline-delimited JSON file per table, with flat, typed fields."""

    def __init__(self, folder: Path):
        self.folder = folder
        self.files = {name: (folder / f"{name}.ndjson").open("w", encoding="utf-8") for name in TABLES}
        self.counts = Counter()
        self.unmapped = Counter()
        self.announced: dict[int, str] = {}
        self.meta: dict[str, object] = {}

    def write(self, table: str, row: dict):
        self.files[table].write(json.dumps(row, separators=(",", ":")))
        self.files[table].write("\n")
        self.counts[table] += 1

    def flatten(self, table: str, row: dict, defaults: tuple[str, ...] = ()) -> dict:
        schema = TABLES[table]
        out = {}
        for key, value in row.items():
            if key == "t":
                continue
            name = RENAMES.get(key, key)
            if key in VECTORS and isinstance(value, list) and len(value) == 3 and f"{VECTORS[key]}x" in schema:
                for axis, part in zip("xyz", value):
                    out[f"{VECTORS[key]}{axis}"] = part
            elif name in schema:
                out[name] = value
            else:
                self.unmapped[f"{table}.{key}"] += 1
        # A flag is only written when true; for a record that evaluated it, absent means false.
        for flag in defaults:
            out.setdefault(flag, False)
        return out

    def add(self, row: dict):
        kind = row.get("t")
        if kind == "tick":
            self.write("ticks", self.flatten("ticks", row, ("frozen", "snap", "no_subject")))
        elif kind == "ent":
            self.write("entities", self.flatten("entities", row, ("persistent", "ark", "predator", "natural", "tamed", "subject")))
        elif kind == "s":
            self.write("status", self.flatten("status", row, ("near",) + (CREATURE_FLAGS if "st" in row else ())))
        elif kind == "d":
            notes, routes = row.pop("pl", []), row.pop("nav", [])
            flat = self.flatten("decisions", row, CREATURE_FLAGS + DECISION_FLAGS)
            flat["nav_n"] = len(routes)
            if routes:
                # The outcome that matters most: a refusal outranks a found or reused path.
                blocked = [r["o"] for r in routes if r["o"] in NAV_BLOCKED]
                flat["nav_o"] = blocked[-1] if blocked else routes[-1]["o"]
            self.write("decisions", flat)
            for note in notes:
                note = dict(note, player=note.pop("e"), near_range=note.pop("near", None))
                sensed = "dist" in note
                flat_note = self.flatten("decision_players", {**note, "s": row["s"], "k": row["k"], "e": row["e"]},
                                         SENSE_FLAGS + (("los_eye",) if "los_rays" in note else ()) if sensed else ())
                self.write("decision_players", {k: v for k, v in flat_note.items() if v is not None})
            for index, route in enumerate(routes):
                self.write("decision_nav", self.flatten("decision_nav", {**route, "s": row["s"], "k": row["k"], "e": row["e"], "i": index}))
        elif kind == "m":
            for sample in row["rows"]:
                e, x, y, z, yaw, body, head, vx, vy, vz, flags, zza, speed, node = sample
                self.write("motion", {"seq": row["s"], "tick": row["k"], "e": e, "x": x, "y": y, "z": z, "yaw": yaw, "body": body,
                                      "head": head, "vx": vx, "vy": vy, "vz": vz, "flags": flags, "zza": zza, "speed": speed,
                                      "node": node})
        elif kind == "p":
            self.write("players", self.flatten("players", row))
        elif kind == "ps":
            self.write("player_status", self.flatten("player_status", row, ("valid_target",)))
        elif kind == "ev":
            self.write("events", self.flatten("events", row, ("hcol", "ground", "pivoting", "terrain", "same_place", "no_terrain")
                                              if row.get("ev") == "stall" else ()))
        elif kind == "path":
            evaluated = ("reach", "trunc") if "nodes" in row else ("done", "stuck") if row.get("id") == 0 else ("ended",)
            self.write("paths", self.flatten("paths", row, evaluated))
        elif kind == "terrain":
            self.write("terrain", self.flatten("terrain", row, ("clip", "trunc")))
            ox, oy, oz = row["o"]
            blocks, palette = row["b"], row["pal"]
            for index in range(0, len(blocks), 5):
                dx, dy, dz, name, collision = blocks[index:index + 5]
                self.write("terrain_blocks", {"seq": row["s"], "tick": row["k"], "e": row["e"], "x": ox + dx, "y": oy + dy,
                                              "z": oz + dz, "block": palette[name], "kind": collision})
        elif kind == "w":
            self.write("world", self.flatten("world", row, ("rain", "thunder", "bright")))
        elif kind == "gap":
            self.announced[row["from"]] = row.get("why", "queue_full")
        elif kind in ("header", "end", "footer"):
            self.meta[kind] = row
        else:
            self.unmapped[f"record.{kind}"] += 1

    def close(self):
        for handle in self.files.values():
            handle.close()


def ingest(path: Path, database: Path) -> tuple[duckdb.DuckDBPyConnection, dict, dict]:
    """Streams the recording into typed tables and returns the connection, the header pieces and the integrity report."""
    integrity: dict[str, object] = {"file": str(path), "records": 0}
    counts = Counter()
    with tempfile.TemporaryDirectory(prefix="ark-session-") as scratch:
        splitter = Splitter(Path(scratch))
        expected, last_tick, holes = None, None, []
        for row in read(path, integrity):
            integrity["records"] += 1
            counts[row.get("t")] += 1
            seq = row.get("s")
            if seq is not None:
                if expected is not None and seq != expected:
                    holes.append({"from_seq": expected, "to_seq": seq - 1, "n": seq - expected, "tick0": last_tick,
                                  "tick1": row.get("k"), "why": "sequence"})
                expected, last_tick = seq + 1, row.get("k")
            splitter.add(row)
        # The sequence numbers are the authority on what is missing; a hole the recorder announced carries its reason.
        for hole in holes:
            hole["why"] = splitter.announced.get(hole["from_seq"], "sequence")
            splitter.write("gaps", hole)
        splitter.close()
        if database.exists():
            database.unlink()
        con = duckdb.connect(str(database))
        for name, schema in TABLES.items():
            source = Path(scratch) / f"{name}.ndjson"
            columns = ", ".join(f'"{column}" {kind}' for column, kind in schema.items())
            if splitter.counts[name] == 0:
                con.execute(f"CREATE TABLE {name} ({columns})")
                continue
            spec = ", ".join(f"'{column}': '{kind}'" for column, kind in schema.items())
            con.execute(f"CREATE TABLE {name} AS SELECT * FROM read_json(?, format='newline_delimited', columns={{{spec}}})",
                        [str(source)])
    availability(con)
    meta = splitter.meta
    header, end, footer = meta.get("header"), meta.get("end"), meta.get("footer")
    missing = sum(h["n"] for h in holes)
    integrity.update({
        "header": header is not None, "end_record": end is not None, "footer": footer is not None,
        "missing_records": missing, "holes": holes[:20], "holes_total": len(holes),
        "dropped_by_recorder": (footer or {}).get("dropped"), "writer_failure": (footer or {}).get("failure"),
        "capture_errors": (end or {}).get("errors"), "stop_reason": (end or footer or {}).get("reason"),
        "unmapped_fields": dict(splitter.unmapped),
    })
    integrity["complete"] = bool(header and end and footer and missing == 0 and not integrity.get("cut_last_line")
                                 and not integrity.get("unreadable_lines") and not integrity.get("archive_error")
                                 and not (footer or {}).get("dropped") and not (footer or {}).get("failure")
                                 and (footer or {}).get("rows") == integrity["records"] - 1)
    con.execute("CREATE TABLE meta (key VARCHAR, value VARCHAR)")
    for key in ("header", "end", "footer"):
        if meta.get(key) is not None:
            con.execute("INSERT INTO meta VALUES (?, ?)", [key, json.dumps(meta[key])])
    con.execute("INSERT INTO meta VALUES ('integrity', ?)", [json.dumps(integrity)])
    con.execute("INSERT INTO meta VALUES ('counts', ?)", [json.dumps(counts)])
    views(con)
    return con, meta, integrity


def availability(con: duckdb.DuckDBPyConnection):
    """When each entity was loaded. Outside these intervals nothing is known about it: it was not recorded, not still."""
    con.execute("CREATE TABLE availability (e INTEGER, tick0 INTEGER, tick1 INTEGER, ended_by VARCHAR)")
    changes = con.execute("""
        SELECT e, tick, seq, true AS loaded, NULL AS why FROM entities
        UNION ALL SELECT e, tick, seq, ev = 'return', why FROM events WHERE ev IN ('leave', 'return')
        ORDER BY seq""").fetchall()
    last = con.execute("SELECT max(tick) FROM ticks").fetchone()[0]
    since: dict[int, int] = {}
    for e, tick, _, loaded, why in changes:
        if loaded:
            since.setdefault(e, tick)
        elif e in since:
            con.execute("INSERT INTO availability VALUES (?, ?, ?, ?)", [e, since.pop(e), tick, why])
    for e, tick in since.items():
        # Still loaded when the recording ended.
        con.execute("INSERT INTO availability VALUES (?, ?, ?, NULL)", [e, tick, last])


def views(con: duckdb.DuckDBPyConnection):
    """Derived views: seconds for every tick, and each creature's distance to the recorded player.

    Time is game time: seconds at 20 ticks per second since the first recorded tick. Creature timers
    count ticks, pauses add none, and a lagging server only stretches the real seconds beside it.
    """
    con.execute("CREATE TABLE origin AS SELECT coalesce(min(tick), 0) AS tick0 FROM ticks")
    con.execute("CREATE MACRO game_s(t) AS (t - (SELECT tick0 FROM origin)) / 20.0")
    con.execute("""
        CREATE VIEW clock AS
        SELECT tick, game_s(tick) AS game_s, ns / 1e9 AS real_s, act_us / 1e6 AS active_s, dur_us, cap_us FROM ticks""")
    con.execute("""
        CREATE VIEW subject AS
        SELECT e, w, h FROM entities WHERE type = 'minecraft:player' ORDER BY subject DESC, e LIMIT 1""")
    # Gap between two upright boxes, the same measure WildlifeSenses.bodyDistance uses for the wake distance.
    gap = """sqrt(power(greatest(0, abs(c.x - p.x) - (n.w + u.w) / 2), 2)
                + power(greatest(0, abs(c.z - p.z) - (n.w + u.w) / 2), 2)
                + power(greatest(0, c.y - (p.y + u.h), p.y - (c.y + n.h)), 2))"""
    for name, source in (("status_x", "status"), ("motion_x", "motion"), ("decisions_x", "decisions")):
        con.execute(f"""
            CREATE VIEW {name} AS
            SELECT c.*, game_s(c.tick) AS game_s, n.species, n.type, n.realm, n.predator, n.w AS width, n.h AS height,
                   sqrt(power(c.x - p.x, 2) + power(c.y - p.y, 2) + power(c.z - p.z, 2)) AS player_dist,
                   {gap} AS player_gap, p.x AS px, p.y AS py, p.z AS pz
            FROM {source} c
            JOIN entities n ON n.e = c.e
            LEFT JOIN subject u ON true
            LEFT JOIN players p ON p.tick = c.tick AND p.e = u.e""")


# -------------------------------------------------------------------------------- incidents

def query(con, sql: str, parameters=None) -> list[dict]:
    cursor = con.execute(sql, parameters or [])
    names = [column[0] for column in cursor.description]
    return [dict(zip(names, row)) for row in cursor.fetchall()]


def runs(rows: list[dict], key):
    """Consecutive rows with the same key."""
    group, current = [], object()
    for row in rows:
        value = key(row)
        if value != current and group:
            yield current, group
            group = []
        current = value
        group.append(row)
    if group:
        yield current, group


# A person's mark flags a moment worth reading; the scripted player of an unattended run (tools/session_run.py)
# marks its own steps with notes that start with auto:, and those are a timeline, not incidents.
BY_HAND = "coalesce(note, '') NOT LIKE 'auto:%'"


class Incidents:
    def __init__(self, con, first: int, marks: list[float]):
        self.con, self.first, self.marks, self.items = con, first, marks, []
        self.names = {r["e"]: (r["species"] or r["type"].split(":")[-1]) for r in query(con, "SELECT e, species, type FROM entities")}

    def time(self, tick):
        return (tick - self.first) / 20.0

    def add(self, kind: str, weight: float, e: int, tick0: int, tick1: int, gap_min=None, normal=False, **details):
        t0, t1 = self.time(tick0), self.time(tick1)
        marked = any(t0 - 10 <= mark <= t1 + 10 for mark in self.marks)
        score = weight + min(20.0, (t1 - t0) * 2) + (max(0.0, 16 - gap_min) if gap_min is not None else 0) + (50 if marked else 0)
        self.items.append({"kind": kind, "normal": normal, "score": round(score, 1), "e": e, "who": self.names.get(e, "?"),
                           "t0": round(t0, 2), "t1": round(t1, 2), "tick0": tick0, "tick1": tick1,
                           **({"gap_min": round(gap_min, 2)} if gap_min is not None else {}),
                           **({"marked": True} if marked else {}), **details})

    def funnel(self):
        """How each wild creature treated the recorded player, pass by pass."""
        rows = query(self.con, """
            SELECT d.e, d.tick, d.seq, d.st, d.act, d.why, d.br, d.nav_o, d.warn, d.x, d.z, d.sensed, d.forced, d.sleepy,
                   d.night_r, n.stage, n.body, n.wake, n.in_range, n.los, n.fov, n.near_range, n.dist, n.sight, n.seen,
                   n.hear, n.smelt, n.creative, n.spectator, n.dead, n.str
            FROM decisions d JOIN decision_players n ON n.seq = d.seq
            JOIN subject u ON u.e = n.player
            ORDER BY d.e, d.tick""")
        for (e, phase), group in runs(rows, lambda r: (r["e"], self.phase(r))):
            first, last = group[0], group[-1]
            gap = min(r["body"] for r in group)
            base = dict(e=e, tick0=first["tick"], tick1=last["tick"], gap_min=gap, passes=len(group),
                        seq0=first["seq"], seq1=last["seq"], states=sorted({r["st"] for r in group}),
                        reasons=sorted({r["why"] for r in group if r["why"]}))
            if phase in ("day_routine", "mode", "peaceful", "capped", "irrelevant", "out_of_scan"):
                if gap <= 16:
                    extra = {}
                    if phase == "day_routine":
                        extra = {"wake_distance": first["wake"], "asleep": any(r["st"] == "SLEEP" for r in group),
                                 "day_for_it": not any(r["night_r"] for r in group)}
                    if phase == "mode":
                        extra = {"creative": first["creative"], "spectator": first["spectator"], "dead": first["dead"]}
                    self.add("ignored_approach", 70, filter=phase, **extra, **base)
            elif phase == "undetected":
                if gap <= 16:
                    self.add("undetected_close", 65, out_of_sight_range=sum(not r["in_range"] for r in group),
                             behind=sum(bool(r["in_range"]) and r["dist"] >= r["near_range"] and r["fov"] <= -0.15 for r in group),
                             no_line_of_sight=sum(not r["los"] for r in group), **base)
            elif phase == "displaced":
                self.add("displaced_by_other_target", 60, winners=sorted({self.names.get(r["sensed"], "?") for r in group}),
                         forced=sorted({r["forced"] for r in group if r["forced"]}), **base)
            elif phase == "warning":
                # Building the warning is the ordinary response; only a long one that never escalates stands out.
                after = self.con.execute("SELECT st FROM decisions WHERE e = ? AND tick > ? ORDER BY tick LIMIT 1",
                                         [e, last["tick"]]).fetchone()
                escalated = bool(after and after[0] in COMBAT)
                long = last["tick"] - first["tick"] >= 200
                self.add("sensed_then_warning", 60 if long and not escalated else 30, normal=escalated or not long,
                         escalated=escalated, next_state=after[0] if after else None, sensed_by=self.senses(group), **base)
            elif phase == "attack":
                self.add("attack", 10, normal=True, **base)
            elif phase == "pursuit":
                moved = math.hypot(last["x"] - first["x"], last["z"] - first["z"])
                closing = first["body"] - last["body"]
                self.add("pursuit", 10, normal=True, moved=round(moved, 2), closed=round(closing, 2), **base)
            elif phase == "pursuit_blocked":
                self.add("pursuit_blocked", 90, outcomes=dict(Counter(r["nav_o"] for r in group)), **base)
            elif phase == "combat_hold":
                self.add("combat_hold", 25, normal=len(group) <= 6, actions=sorted({r["act"] for r in group}), **base)
            elif phase == "lost_sight":
                self.add("lost_sight", 55, **base)
            elif phase == "fled":
                self.add("fled_from_player", 20, normal=True, **base)
            elif phase == "gave_up":
                self.add("gave_up", 55, **base)

    @staticmethod
    def phase(r: dict) -> str:
        stage = r["stage"]
        if stage != "sensed":
            return stage
        state, branch = r["st"], r["br"]
        if state in COMBAT:
            if branch == "strike":
                return "attack"
            if branch == "chase":
                return "pursuit_blocked" if r["nav_o"] in NAV_BLOCKED else "pursuit"
            return "lost_sight" if branch == "no_sight" else "combat_hold"
        if state in ("ALERT", "THREATEN", "INVESTIGATE"):
            return "warning"
        if state == "FLEE":
            return "fled"
        if state == "RETURN_HOME":
            return "gave_up"
        return "calm"

    @staticmethod
    def senses(group: list[dict]) -> list[str]:
        found = set()
        for r in group:
            found.add("sight" if r["seen"] else "hearing" if (r["str"] or 0) >= 0.65 else "scent" if (r["str"] or 0) > 0 else "none")
        return sorted(found)

    def standing_pursuit(self):
        """In a combat state with a target, told to chase, and the body does not move: the apparent idle."""
        rows = query(self.con, """
            SELECT e, tick, seq, st, act, br, nav_o, x, z, s_gap, fail, strike_cd, hold, pivoting, tg, sensed,
                   lag(x) OVER w AS x0, lag(z) OVER w AS z0, lag(tick) OVER w AS tick0
            FROM decisions WHERE st IN ('HUNT', 'DEFEND') WINDOW w AS (PARTITION BY e ORDER BY tick) ORDER BY e, tick""")

        def still(r):
            return (r["br"] == "chase" and r["x0"] is not None and r["tick"] - r["tick0"] <= 12
                    and math.hypot(r["x"] - r["x0"], r["z"] - r["z0"]) < 0.3)

        for (e, standing), group in runs(rows, lambda r: (r["e"], still(r))):
            if not standing or len(group) < 2:
                continue
            first, last = group[0], group[-1]
            gaps = [r["s_gap"] for r in group if r["s_gap"] is not None]
            ended = query(self.con, "SELECT count(*) AS n FROM paths WHERE e = ? AND ended AND tick BETWEEN ? AND ?",
                          [e, first["tick"] - 20, last["tick"]])[0]["n"]
            self.add("chase_without_movement", 100, e, first["tick0"], last["tick"], gap_min=min(gaps) if gaps else None,
                     passes=len(group), seq0=first["seq"], seq1=last["seq"], target=self.names.get(first["sensed"], "?"),
                     path_requests=dict(Counter(r["nav_o"] or "none" for r in group)), failed_paths=last["fail"],
                     pivoting=sum(bool(r["pivoting"]) for r in group), paths_ended_early=ended)

    def return_home(self):
        """Stays in RETURN_HOME while its path requests keep being given up: the give-up re-arms the state."""
        rows = query(self.con, """
            SELECT d.e, d.tick, d.seq, d.st, d.nav_o, d.x, d.z, d.home_x, d.home_z, d.rec, d.aware, n.stage, n.body
            FROM decisions d LEFT JOIN subject u ON true LEFT JOIN decision_players n ON n.seq = d.seq AND n.player = u.e
            ORDER BY d.e, d.tick""")
        for (e, state), group in runs(rows, lambda r: (r["e"], r["st"])):
            given_up = sum(r["nav_o"] == "abandoned" for r in group)
            if state != "RETURN_HOME" or given_up < 2:
                continue
            first, last = group[0], group[-1]
            from_home = [math.hypot(r["x"] - r["home_x"] - 0.5, r["z"] - r["home_z"] - 0.5) for r in group if r["home_x"] is not None]
            sensed = [r for r in group if r["stage"] == "sensed"]
            self.add("return_home_loop", 95, e, first["tick"], last["tick"], gap_min=min((r["body"] for r in sensed), default=None),
                     passes=len(group), seq0=first["seq"], seq1=last["seq"], given_up=given_up,
                     blocks_from_home=round(max(from_home), 2) if from_home else None, passes_sensing_you=len(sensed),
                     still_in_it_at_the_end=last["tick"] == max(r["tick"] for r in rows if r["e"] == e))

    def airborne(self):
        """A path request refused because the body was neither on the ground nor in a liquid at that pass."""
        rows = query(self.con, """
            SELECT d.e, d.tick, d.seq, d.age, d.st, d.br, d.nav_n, d.player_gap FROM decisions_x d
            WHERE d.realm IN ('LAND', 'AMPHIBIOUS') AND NOT d.ground AND NOT d.in_water AND d.nav_o IN ('no_path', 'abandoned', 'no_destination')
            ORDER BY d.e, d.tick""")
        for e, group in runs(rows, lambda r: r["e"]):
            first, last = group[0], group[-1]
            gaps = [r["player_gap"] for r in group if r["player_gap"] is not None]
            self.add("path_refused_airborne", 85, e, first["tick"], last["tick"], gap_min=min(gaps) if gaps else None,
                     passes=len(group), seq0=first["seq"], age_ticks=first["age"], requests=sum(r["nav_n"] or 0 for r in group),
                     branches=sorted({r["br"] for r in group if r["br"]}))

    def stalls(self):
        rows = query(self.con, """
            SELECT e, tick, seq, why, moved, hcol, pivoting, terrain, act, x, y, z FROM events WHERE ev = 'stall' ORDER BY e, tick""")
        by_creature = defaultdict(list)
        for row in rows:
            by_creature[row["e"]].append(row)
        for e, events in by_creature.items():
            group = [events[0]]
            for event in events[1:] + [None]:
                if event is not None and event["tick"] - group[-1]["tick"] <= 100 and event["why"] == group[0]["why"]:
                    group.append(event)
                    continue
                first, last = group[0], group[-1]
                gap = query(self.con, "SELECT min(player_gap) AS g FROM motion_x WHERE e = ? AND tick BETWEEN ? AND ?",
                            [e, first["tick"], last["tick"]])[0]["g"]
                pushing, pivoting = any(g["hcol"] for g in group), any(g["pivoting"] for g in group)
                # A big animal that turns in place for a few seconds before it walks is not held by anything.
                turning = pivoting and not pushing and last["tick"] - first["tick"] <= 100
                self.add("stall", 80, e, first["tick"], last["tick"], gap_min=gap, normal=turning, why=first["why"],
                         events=len(group), seq0=first["seq"], pushing=pushing, pivoting=pivoting, action=first["act"],
                         around=self.around(e, first["tick"], last["tick"]))
                group = [event] if event is not None else []

    def around(self, e: int, tick0: int, tick1: int) -> dict | None:
        """What stands inside or against the body in the nearest terrain capture: block names by collision kind."""
        # The stall's own capture, or the one up to 200 ticks earlier that the recorder did not repeat for the same spot.
        capture = query(self.con, """
            SELECT seq, box, clip, trunc, unloaded FROM terrain WHERE e = ? AND tick BETWEEN ? AND ?
            ORDER BY (tick < ?), abs(tick - ?) LIMIT 1""", [e, tick0 - 200, tick1, tick0, tick0])
        if not capture:
            return None
        seq, box = capture[0]["seq"], capture[0]["box"]
        blocks = query(self.con, "SELECT x, y, z, block, kind FROM terrain_blocks WHERE seq = ?", [seq])
        feet = math.floor(box[1])
        inside, beside = Counter(), Counter()
        for b in blocks:
            if b["kind"] in (0, 3) or b["y"] < feet:
                continue
            overlaps = b["x"] + 1 > box[0] and b["x"] < box[3] and b["z"] + 1 > box[2] and b["z"] < box[5] and b["y"] < box[4]
            (inside if overlaps else beside)[b["block"].split(":")[-1]] += 1
        return {"terrain_seq": seq, "inside_body_box": dict(inside), "within_two_blocks": dict(beside),
                "clipped": capture[0]["clip"], "cut_short": capture[0]["trunc"], "unloaded": capture[0]["unloaded"]}

    def collisions(self):
        rows = query(self.con, f"SELECT e, tick, flags, player_gap FROM motion_x WHERE flags & {H_COLLIDE} != 0 ORDER BY e, tick")
        for e, group in runs(rows, lambda r: r["e"]):
            run = [group[0]]
            for row in group[1:] + [None]:
                if row is not None and row["tick"] - run[-1]["tick"] <= 2:
                    run.append(row)
                    continue
                if len(run) >= 10:
                    gaps = [r["player_gap"] for r in run if r["player_gap"] is not None]
                    self.add("pushing_against_blocks", 45, e, run[0]["tick"], run[-1]["tick"], gap_min=min(gaps) if gaps else None,
                             ticks=len(run), while_pathing=sum(bool(r["flags"] & PATHING) for r in run))
                run = [row] if row is not None else []

    def strikes(self):
        rows = query(self.con, "SELECT e, tick, seq, r, at_e, gap FROM events WHERE ev = 'strike' ORDER BY e, tick")
        for e, group in runs(rows, lambda r: r["e"]):
            results = Counter(r["r"] for r in group)
            missed = [r for r in group if r["r"] in ("gone", "out_of_reach", "no_sight", "no_damage", "no_clip", "torpor")]
            if missed:
                self.add("strike_missed", 50, e, missed[0]["tick"], missed[-1]["tick"], results=dict(results),
                         seq0=missed[0]["seq"], target=self.names.get(missed[0]["at_e"], "?"))
            if results.get("hit"):
                hits = [r for r in group if r["r"] == "hit"]
                self.add("strike_hit", 5, e, hits[0]["tick"], hits[-1]["tick"], normal=True, hits=len(hits),
                         target=self.names.get(hits[0]["at_e"], "?"))

    def animation(self):
        """A travelling action on a body that stands (the client then plays the standing clip), and the reverse."""
        rows = query(self.con, f"""
            WITH acts AS (
                SELECT e, tick, to_name AS act FROM events WHERE ev = 'action'
                UNION ALL SELECT e, min(tick), arg_min(act, tick) FROM status WHERE act IS NOT NULL GROUP BY e),
            steps AS (
                SELECT e, tick, flags, player_gap,
                       sqrt(power(x - lag(x) OVER w, 2) + power(z - lag(z) OVER w, 2)) * 20 AS bps
                FROM motion_x WHERE realm IN ('LAND', 'AMPHIBIOUS') WINDOW w AS (PARTITION BY e ORDER BY tick))
            SELECT s.e, s.tick, s.bps, s.flags, s.player_gap, a.act
            FROM steps s ASOF JOIN acts a ON s.e = a.e AND s.tick >= a.tick
            WHERE s.bps IS NOT NULL ORDER BY s.e, s.tick""")

        def label(r):
            travelling = r["act"] in TRAVEL_ACTIONS
            if travelling and r["bps"] < 0.2 and not r["flags"] & PIVOT:
                return "travel_action_standing"
            if not travelling and r["bps"] > 1.5:
                return "moving_in_standing_action"
            return None

        for (e, kind), group in runs(rows, lambda r: (r["e"], label(r))):
            if kind is None or group[-1]["tick"] - group[0]["tick"] < 20:
                continue
            gaps = [r["player_gap"] for r in group if r["player_gap"] is not None]
            self.add(kind, 45, e, group[0]["tick"], group[-1]["tick"], gap_min=min(gaps) if gaps else None,
                     actions=dict(Counter(r["act"] for r in group)), while_pathing=sum(bool(r["flags"] & PATHING) for r in group))

    def coverage(self):
        """Near the player without full behaviour, or in full detail without decisions."""
        rows = query(self.con, """
            SELECT e, tick, tier, player_gap, dk, torpor, tamed, ridden, f FROM status_x
            WHERE species IS NOT NULL AND realm != 'AIR' AND player_gap < 64 ORDER BY e, tick""")

        def label(r):
            if r["tier"] != "FULL":
                return "not_full_detail_near_player"
            if not (r["tamed"] or r["ridden"] or r["torpor"] or r["f"] & 64) and (r["dk"] is None or r["tick"] - r["dk"] > 30):
                return "full_detail_without_decisions"
            return None

        for (e, kind), group in runs(rows, lambda r: (r["e"], label(r))):
            if kind is None or len(group) < 2:
                continue
            self.add(kind, 40, e, group[0]["tick"], group[-1]["tick"], gap_min=min(r["player_gap"] for r in group),
                     tiers=sorted({r["tier"] for r in group}))

    def lifecycle(self):
        rows = query(self.con, """
            SELECT v.e, v.tick, v.seq, v.ev, v.why, v.note, v.x, v.y, v.z, p.x AS px, p.y AS py, p.z AS pz
            FROM events v LEFT JOIN subject u ON true LEFT JOIN players p ON p.tick = v.tick AND p.e = u.e
            WHERE v.ev IN ('leave', 'death')""")
        for r in rows:
            if r["px"] is None or r["x"] is None:
                continue
            distance = math.dist((r["x"], r["y"], r["z"]), (r["px"], r["py"], r["pz"]))
            if r["ev"] == "leave" and r["note"] == "budget_cull" and distance < 64:
                self.add("culled_in_sight", 35, r["e"], r["tick"], r["tick"], gap_min=distance, seq0=r["seq"])
            elif r["ev"] == "death" and distance < 64:
                self.add("death_near_player", 15, r["e"], r["tick"], r["tick"], gap_min=distance, normal=True, seq0=r["seq"])
        for r in query(self.con, """
                SELECT n.e, n.tick, n.seq, n.origin, n.note, n.x, n.y, n.z, p.x AS px, p.y AS py, p.z AS pz
                FROM entities n LEFT JOIN subject u ON true LEFT JOIN players p ON p.tick = n.tick AND p.e = u.e
                WHERE n.origin = 'spawn' AND n.ark"""):
            if r["px"] is not None and math.dist((r["x"], r["y"], r["z"]), (r["px"], r["py"], r["pz"])) < 32:
                self.add("spawned_close", 35, r["e"], r["tick"], r["tick"], seq0=r["seq"], note=r["note"],
                         gap_min=math.dist((r["x"], r["y"], r["z"]), (r["px"], r["py"], r["pz"])))

    def marked(self):
        for r in query(self.con, f"SELECT tick, seq, note FROM events WHERE ev = 'mark' AND {BY_HAND} ORDER BY tick"):
            near = query(self.con, """
                SELECT e, species, st, act, round(player_gap, 1) AS gap FROM status_x
                WHERE species IS NOT NULL AND tick = (SELECT max(tick) FROM status WHERE tick <= ?) ORDER BY player_gap LIMIT 5""",
                         [r["tick"]])
            self.add("mark", 200, -1, r["tick"], r["tick"], note=r["note"], seq0=r["seq"], nearest=near)

    def all(self) -> list[dict]:
        for step in (self.funnel, self.standing_pursuit, self.return_home, self.airborne, self.stalls, self.collisions,
                     self.strikes, self.animation, self.coverage, self.lifecycle, self.marked):
            step()
        self.items.sort(key=lambda item: (-item["score"], item["t0"]))
        return self.items


# ---------------------------------------------------------------------------------- summary

def summarise(con, meta: dict, integrity: dict, incidents: list[dict]) -> dict:
    header, end = meta.get("header") or {}, meta.get("end") or {}
    one = lambda sql: con.execute(sql).fetchone()
    ticks, real, active, paused, capture, capture_max, tick_ms, tick_max, queue = one("""
        SELECT count(*), max(ns) / 1e9, max(act_us) / 1e6, max(paused_us) / 1e6, avg(cap_us), max(cap_us),
               avg(dur_us) / 1e3, max(dur_us) / 1e3, max(q) FROM ticks""")
    config = header.get("config", {})
    creatures = query(con, """
        SELECT s.e, any_value(s.species) AS species, count(*) AS snapshots, round(min(player_gap), 1) AS gap_min,
               round(avg(player_gap), 1) AS gap_avg, list(DISTINCT st) AS states, list(DISTINCT tier) AS tiers
        FROM status_x s WHERE species IS NOT NULL AND player_gap IS NOT NULL
        GROUP BY s.e ORDER BY gap_min LIMIT 15""")
    stages = query(con, """
        SELECT n.e, any_value(c.species) AS species, stage, count(*) AS passes, round(min(body), 1) AS gap_min
        FROM decision_players n JOIN entities c ON c.e = n.e JOIN subject u ON u.e = n.player
        GROUP BY n.e, stage ORDER BY gap_min LIMIT 40""")
    kinds = Counter(item["kind"] for item in incidents)
    # Every sense check a creature ran on the recorded player, by distance between the body centres: what found
    # the player and what stopped it. was_moving is the player's real movement that tick, flagged_moving what the
    # hearing check saw.
    detection = query(con, """
        WITH step AS (SELECT p.e, p.tick, sqrt(power(p.x - lag(p.x) OVER w, 2) + power(p.y - lag(p.y) OVER w, 2)
                                               + power(p.z - lag(p.z) OVER w, 2)) AS moved
                      FROM players p WINDOW w AS (PARTITION BY p.e ORDER BY p.tick))
        SELECT CASE WHEN n.dist < 8 THEN 'under 8' WHEN n.dist < 16 THEN '8-16' WHEN n.dist < 32 THEN '16-32' ELSE '32+' END AS blocks,
               count(*) AS checks, count(*) FILTER (n.str > 0) AS detected, count(*) FILTER (n.seen) AS seen,
               count(*) FILTER (NOT n.seen AND n.hear > n.dist) AS heard,
               count(*) FILTER (NOT n.seen AND NOT n.hear > n.dist AND n.smelt) AS smelt,
               count(*) FILTER (NOT n.route_loaded) AS unloaded, count(*) FILTER (n.route_loaded AND NOT n.los) AS no_line_of_sight,
               count(*) FILTER (n.los AND NOT n.in_range) AS beyond_sight,
               count(*) FILTER (n.los AND n.in_range AND NOT n.seen) AS behind_it,
               count(*) FILTER (s.moved > 0.02) AS was_moving, count(*) FILTER (n.moving) AS flagged_moving
        FROM decision_players n JOIN subject u ON u.e = n.player LEFT JOIN step s ON s.e = n.player AND s.tick = n.tick
        WHERE n.dist IS NOT NULL GROUP BY 1 ORDER BY min(n.dist)""")
    # Bodies that turn on the spot while they hold a path, from the per-tick samples near the player.
    turning = query(con, """
        WITH f AS (SELECT m.e, m.tick, (m.flags & 512) > 0 AS turning,
                          (m.flags & 512) > 0 AND NOT coalesce(lag((m.flags & 512) > 0) OVER w AND lag(m.tick) OVER w = m.tick - 1, false) AS starts
                   FROM motion m WINDOW w AS (PARTITION BY m.e ORDER BY m.tick)),
             g AS (SELECT e, sum(starts::INT) OVER (PARTITION BY e ORDER BY tick) AS run FROM f WHERE turning),
             r AS (SELECT e, run, count(*) AS ticks FROM g GROUP BY e, run),
             turns AS (SELECT c.species, count(*) AS turns, count(DISTINCT r.e) AS bodies, median(r.ticks) AS median_ticks,
                              max(r.ticks) AS longest_ticks, count(*) FILTER (r.ticks >= 20) AS a_second_or_more
                       FROM r JOIN entities c ON c.e = r.e WHERE c.species IS NOT NULL GROUP BY 1),
             share AS (SELECT c.species, count(*) FILTER ((m.flags & 128) > 0) AS pathing,
                              count(*) FILTER ((m.flags & 128) > 0 AND (m.flags & 512) > 0) AS both
                       FROM motion m JOIN entities c ON c.e = m.e WHERE c.species IS NOT NULL GROUP BY 1)
        SELECT t.*, round(100.0 * s.both / nullif(s.pathing, 0), 1) AS percent_of_path_time
        FROM turns t JOIN share s USING (species) ORDER BY t.turns DESC""")
    # Escapes: what each attempt to run from a threat came to, and the animals that stood in FLEE without moving.
    stood_10s, longest = one("""
        WITH f AS (SELECT d.e, d.tick, lag(d.tick) OVER w AS previous,
                          d.st = 'FLEE' AND sqrt(power(d.x - lag(d.x) OVER w, 2) + power(d.z - lag(d.z) OVER w, 2)) < 0.25 AS stood
                   FROM decisions d WINDOW w AS (PARTITION BY d.e ORDER BY d.tick)),
             g AS (SELECT e, stood, sum(CASE WHEN stood AND tick - previous <= 10 THEN 0 ELSE 1 END)
                                    OVER (PARTITION BY e ORDER BY tick) AS run FROM f),
             r AS (SELECT e, run, count(*) FILTER (stood) AS passes FROM g GROUP BY e, run)
        SELECT count(*) FILTER (passes >= 20), coalesce(max(passes), 0) / 2.0 FROM r""")
    flee = {
        "requests": dict(con.execute("""
            SELECT n.o, count(*) FROM decision_nav n JOIN decisions d ON d.seq = n.seq WHERE d.st = 'FLEE'
            GROUP BY 1 ORDER BY 2 DESC""").fetchall()),
        "passes": one("SELECT count(*) FROM decisions WHERE st = 'FLEE'")[0],
        "bodies": one("SELECT count(DISTINCT e) FROM decisions WHERE st = 'FLEE'")[0],
        "stood_ten_seconds_or_more": stood_10s, "longest_stand_s": longest,
    }
    # Pursuit: strikes at the recorded player, trees knocked down on the way, and bodies that showed a run while standing.
    run_shown, run_standing = one("""
        SELECT count(*), count(*) FILTER (NOT coalesce(loco, false) AND NOT coalesce(pivoting, false))
        FROM status WHERE act IN ('CHASE', 'BOLT')""")
    pursuit = {
        "strikes_at_player": query(con, """
            SELECT c.species, v.r AS result, count(*) AS n FROM events v JOIN entities c ON c.e = v.e JOIN subject u ON u.e = v.at_e
            WHERE v.ev = 'strike' GROUP BY 1, 2 ORDER BY 1, 3 DESC"""),
        "trampling": query(con, """
            SELECT c.species, count(*) AS events, count(DISTINCT v.e) AS bodies,
                   sum(try_cast(regexp_extract(v.to_name, 'logs=(\\d+)', 1) AS INTEGER)) AS logs,
                   sum(try_cast(regexp_extract(v.to_name, 'leaves=(\\d+)', 1) AS INTEGER)) AS leaves
            FROM events v JOIN entities c ON c.e = v.e WHERE v.ev = 'trample' GROUP BY 1 ORDER BY 2 DESC"""),
        "snapshots_showing_a_run": run_shown, "of_them_standing": run_standing,
    }
    return {
        "session": header.get("session"), "started": header.get("started"), "schema": header.get("schema"),
        "integrity": {key: integrity.get(key) for key in (
            "complete", "records", "missing_records", "holes_total", "dropped_by_recorder", "capture_errors",
            "writer_failure", "cut_last_line", "unreadable_lines", "archive_error", "header", "end_record", "footer",
            "stop_reason", "unmapped_fields")},
        "timing": {"ticks": ticks, "real_s": real, "unpaused_s": active, "paused_s": paused,
                   "ticks_per_second": round(ticks / active, 2) if active else None,
                   "tick_ms_avg": round(tick_ms or 0, 2), "tick_ms_max": round(tick_max or 0, 2),
                   "capture_us_avg": round(capture or 0), "capture_us_max": capture_max, "queue_depth_max": queue,
                   "asked_record_s": header.get("record_s"), "delay_s": header.get("delay_s")},
        "setup": {"build": header.get("build"), "server": header.get("server"), "client": header.get("client"),
                  "armed_by": header.get("armed_by"), "ready_by": header.get("ready_by"),
                  "day_time_set": header.get("day_time_set"), "healed": header.get("healed", False),
                  "wake_distance": config.get("nighttime.playerWakeDistance"),
                  "carnivore_day_sleep": config.get("nighttime.carnivoreDaySleepFraction"),
                  "night": [config.get("nighttime.nightStartTick"), config.get("nighttime.nightEndTick")],
                  "tiers": [config.get("behavior.fullRadius"), config.get("behavior.ambientRadius"), config.get("behavior.dormantRadius")],
                  "population_model": config.get("spawning.populationModel"),
                  "density_per_chunk": config.get("spawning.populationDensityPerChunk"),
                  "spawn_mobs": (header.get("gamerules") or {}).get("spawn_mobs")},
        "world": query(con, "SELECT dim, min(day) % 24000 AS day_from, max(day) % 24000 AS day_to, bool_or(rain) AS rain, any_value(diff) AS difficulty, max(chunks) AS chunks FROM world GROUP BY dim"),
        "player": query(con, "SELECT mode, bool_and(valid_target) AS valid_target, min(visibility) AS visibility, min(noise) AS noise, min(scent) AS scent FROM player_status s JOIN subject u ON u.e = s.e GROUP BY mode"),
        "records": json.loads(one("SELECT value FROM meta WHERE key = 'counts'")[0]),
        "entities": {"total": one("SELECT count(*) FROM entities")[0],
                     "by_origin": dict(con.execute("SELECT origin, count(*) FROM entities GROUP BY origin").fetchall()),
                     "ark_by_species": dict(con.execute("SELECT species, count(*) FROM entities WHERE ark GROUP BY species ORDER BY 2 DESC").fetchall()),
                     "other_by_type": dict(con.execute("SELECT type, count(*) FROM entities WHERE NOT ark GROUP BY type ORDER BY 2 DESC LIMIT 12").fetchall())},
        "events": dict(con.execute("SELECT ev, count(*) FROM events GROUP BY ev ORDER BY 2 DESC").fetchall()),
        # No removal reason: the chunk left the area where entities are tracked (the player moved away), it is still in memory.
        "leaves": dict(con.execute("SELECT CASE why WHEN 'UNKNOWN' THEN 'OUT_OF_RANGE' ELSE why END || coalesce(' (' || note || ')', ''), count(*) FROM events WHERE ev = 'leave' GROUP BY 1").fetchall()),
        # An entity is only known while loaded; these left before the end and are unavailable from then on.
        "unavailable": dict(zip(("entities_that_left", "of_them_ark", "came_back"), one("""
            SELECT count(DISTINCT a.e) FILTER (a.ended_by IS NOT NULL), count(DISTINCT a.e) FILTER (a.ended_by IS NOT NULL AND n.ark),
                   (SELECT count(*) FROM events WHERE ev = 'return')
            FROM availability a JOIN entities n ON n.e = a.e"""))),
        "decisions": {"total": one("SELECT count(*) FROM decisions")[0],
                      "reasons": dict(con.execute("SELECT why, count(*) FROM decisions GROUP BY why ORDER BY 2 DESC").fetchall()),
                      "branches": dict(con.execute("SELECT br, count(*) FROM decisions GROUP BY br ORDER BY 2 DESC").fetchall()),
                      "path_requests": dict(con.execute("SELECT o, count(*) FROM decision_nav GROUP BY o ORDER BY 2 DESC").fetchall())},
        "path_budget": dict(zip(("granted", "deferred", "ticks_with_deferral"),
                                one("SELECT coalesce(sum(paths), 0), coalesce(sum(deferred), 0), count(*) FILTER (deferred > 0) FROM ticks"))),
        "nearest_creatures": creatures,
        "player_funnel": stages,
        "script": query(con, f"SELECT round(game_s(tick), 1) AS t, substr(note, 6) AS step FROM events WHERE ev = 'mark' AND NOT {BY_HAND} ORDER BY tick"),
        "detection": detection,
        "turn_in_place": turning,
        "flee": flee,
        "pursuit": pursuit,
        "incident_counts": dict(kinds),
        "top_incidents": [item for item in incidents if not item["normal"]][:12],
        "recorder_end": {key: end.get(key) for key in ("ticks", "emitted", "dropped", "errors", "terrain", "terrain_suppressed")},
    }


def show(summary: dict):
    integrity, timing, setup = summary["integrity"], summary["timing"], summary["setup"]
    print(f"session {summary['session']}  started {summary['started']}")
    state = "COMPLETE" if integrity["complete"] else "INCOMPLETE"
    print(f"{state}: {integrity['records']} records, {integrity['missing_records']} missing in {integrity['holes_total']} holes, "
          f"dropped {integrity['dropped_by_recorder']}, capture errors {integrity['capture_errors']}, stop reason {integrity['stop_reason']}")
    for key in ("cut_last_line", "unreadable_lines", "archive_error", "writer_failure"):
        if integrity.get(key):
            print(f"  {key}: {integrity[key]}")
    if not integrity["footer"]:
        print("  no footer: the game ended before the file was closed; everything up to the last complete line was read")
    if integrity["unmapped_fields"]:
        print(f"  fields the analyzer does not know: {integrity['unmapped_fields']}")
    print(f"time: {timing['ticks']} ticks, {timing['unpaused_s']:.1f} s unpaused of {timing['real_s']:.1f} s real "
          f"({timing['paused_s']:.1f} s paused), {timing['ticks_per_second']} ticks/s, tick {timing['tick_ms_avg']} ms avg "
          f"{timing['tick_ms_max']} max, capture {timing['capture_us_avg']} us avg {timing['capture_us_max']} max")
    print(f"setup: build {setup['build']}, server {setup['server']}, client {setup['client']}")
    print(f"       wake distance {setup['wake_distance']}, carnivore day sleep {setup['carnivore_day_sleep']}, night {setup['night']}, "
          f"tiers {setup['tiers']}, population {setup['population_model']} {setup['density_per_chunk']}/chunk"
          + (f", clock moved to {setup['day_time_set']} at the join" if setup["day_time_set"] is not None else "")
          + (", player healed at the join" if setup["healed"] else ""))
    print(f"world: {summary['world']}  player: {summary['player']}")
    print(f"entities: {summary['entities']['total']} {summary['entities']['by_origin']}; ark {summary['entities']['ark_by_species']}")
    print(f"events: {summary['events']}")
    if summary["leaves"]:
        print(f"leaves: {summary['leaves']}; unavailable after leaving: {summary['unavailable']} (table availability)")
    print(f"decisions: {summary['decisions']['total']} reasons {summary['decisions']['reasons']}")
    print(f"           branches {summary['decisions']['branches']} path requests {summary['decisions']['path_requests']}")
    print(f"path budget: {summary['path_budget']}")
    print("nearest creatures (id species snapshots gap_min gap_avg states):")
    for c in summary["nearest_creatures"][:10]:
        print(f"  {c['e']:>4} {c['species']:<18} {c['snapshots']:>4} {c['gap_min']:>6} {c['gap_avg']:>6} {','.join(sorted(s for s in c['states'] if s))}")
    print("player through each creature's candidate funnel (id species stage passes gap_min):")
    for s in summary["player_funnel"][:20]:
        print(f"  {s['e']:>4} {s['species'] or '?':<18} {s['stage']:<14} {s['passes']:>4} {s['gap_min']:>6}")
    print("sense checks on you (blocks: checks, detected = seen + heard + smelt | no line of sight, beyond sight, behind it | you moved, hearing saw you move):")
    for band in summary["detection"]:
        print(f"  {band['blocks']:>8}: {band['checks']:>6} {band['detected']:>5} = {band['seen']} + {band['heard']} + {band['smelt']} | "
              f"{band['no_line_of_sight']} {band['beyond_sight']} {band['behind_it']} | {band['was_moving']} {band['flagged_moving']}")
    if summary["turn_in_place"]:
        print("turning on the spot with a path (species turns bodies median_ticks longest a_second_or_more percent_of_path_time):")
        for turn in summary["turn_in_place"]:
            print(f"  {turn['species']:<16} {turn['turns']:>5} {turn['bodies']:>4} {turn['median_ticks']:>6.0f} {turn['longest_ticks']:>5} "
                  f"{turn['a_second_or_more']:>5} {turn['percent_of_path_time']}")
    flee, pursuit = summary["flee"], summary["pursuit"]
    print(f"escapes: {flee['passes']} passes in FLEE by {flee['bodies']} bodies, requests {flee['requests']}; "
          f"stood ten seconds or more {flee['stood_ten_seconds_or_more']} times, longest {flee['longest_stand_s']:g} s")
    print(f"pursuit: strikes at you {[(row['species'], row['result'], row['n']) for row in pursuit['strikes_at_player']]}; "
          f"trees knocked down {pursuit['trampling']}; a run shown on {pursuit['snapshots_showing_a_run']} snapshots, "
          f"{pursuit['of_them_standing']} of them standing")
    if summary["script"]:
        print("scripted player (game s, step): " + "; ".join(f"{step['t']:g} {step['step']}" for step in summary["script"]))
    print(f"incidents: {summary['incident_counts']}")
    for item in summary["top_incidents"]:
        rest = {k: v for k, v in item.items() if k not in ("kind", "normal", "score", "e", "who", "t0", "t1", "tick0", "tick1",
                                                           "seq0", "seq1")}
        print(f"  [{item['score']:>5}] {item['kind']} #{item['e']} {item['who']} {item['t0']}-{item['t1']} s {json.dumps(rest, default=str)}")


def window(con, e: int, t0: float | None, t1: float | None):
    """Everything recorded about one creature in a time window, one line per record, oldest first."""
    low, high = (t0 if t0 is not None else -1e9), (t1 if t1 is not None else 1e9)
    lines = []
    for r in query(con, """
            SELECT d.game_s, d.tick, d.seq, d.st0, d.st, d.act, d.why, d.br, d.nav_o, d.fail, round(d.player_gap, 2) AS gap,
                   d.aware, d.warn, d.chase, d.hold, d.beat, d.pivoting, n.stage, d.sensed, d.s_by
            FROM decisions_x d LEFT JOIN subject u ON true LEFT JOIN decision_players n ON n.seq = d.seq AND n.player = u.e
            WHERE d.e = ? AND d.game_s BETWEEN ? AND ?""", [e, low, high]):
        lines.append((r["tick"], r["seq"], f"{r['game_s']:7.2f} k{r['tick']} decide {r['st0']}>{r['st']} {r['act']} why={r['why']} "
                      f"br={r['br']} nav={r['nav_o']} fail={r['fail']} gap={r['gap']} stage={r['stage']} sensed={r['sensed']}/{r['s_by']} "
                      f"aware={r['aware']} warn={r['warn']} chase={r['chase']} hold={r['hold']}/{r['beat']} pivot={r['pivoting']}"))
    for r in query(con, """
            SELECT v.*, game_s(v.tick) AS game_s FROM events v
            WHERE (v.e = ? OR v.by_e = ? OR v.at_e = ?) AND game_s(v.tick) BETWEEN ? AND ?""", [e, e, e, low, high]):
        detail = {k: v for k, v in r.items() if v is not None and k not in ("seq", "tick", "ns", "ev", "game_s") and v is not False}
        lines.append((r["tick"], r["seq"], f"{r['game_s']:7.2f} k{r['tick']} {r['ev']} {json.dumps(detail, default=str)}"))
    for r in query(con, """
            SELECT p.tick, p.seq, game_s(p.tick) AS game_s, p.id, p.prev, p.pn, p.pi, p.reach, p.left_, p.ended, p.done, p.stuck, p.goal_x, p.goal_y,
                   p.goal_z, round(p.x, 2) AS x, round(p.z, 2) AS z
            FROM paths p WHERE p.e = ? AND game_s(p.tick) BETWEEN ? AND ?""", [e, low, high]):
        head = f"{r['game_s']:7.2f} k{r['tick']} path"
        if r["id"] == 0:
            text = f"{head} dropped id={r['prev']} at node {r['pi']}/{r['pn']} done={r['done']} stuck={r['stuck']}"
        elif r["ended"]:
            text = f"{head} ran out id={r['id']} at node {r['pi']}/{r['pn']} body at ({r['x']}, {r['z']})"
        else:
            text = (f"{head} new id={r['id']} {r['pn']} nodes, reaches goal={r['reach']} "
                    f"ends {r['left_']} from goal ({r['goal_x']},{r['goal_y']},{r['goal_z']})")
        lines.append((r["tick"], r["seq"], text))
    for r in query(con, """
            SELECT cast(floor(game_s) AS INTEGER) AS second, min(tick) AS tick, min(seq) AS seq, count(*) AS n,
                   round(sqrt(power(last(x ORDER BY tick) - first(x ORDER BY tick), 2) + power(last(z ORDER BY tick) - first(z ORDER BY tick), 2)), 2) AS moved,
                   round(min(player_gap), 2) AS gap, bit_or(flags) AS flags, round(avg(zza), 2) AS zza
            FROM motion_x WHERE e = ? AND game_s BETWEEN ? AND ? GROUP BY 1 ORDER BY 1""", [e, low, high]):
        flags = [name for bit, name in ((4, "collide"), (128, "pathing"), (256, "stuck"), (512, "pivot"), (2, "water")) if r["flags"] & bit]
        lines.append((r["tick"], r["seq"], f"{r['second']:7d} k{r['tick']} second: moved {r['moved']} gap {r['gap']} zza {r['zza']} {','.join(flags)}"))
    for _, _, line in sorted(lines, key=lambda item: (item[0], item[1])):
        print(line)


def analyse(source: Path, out: Path) -> tuple[duckdb.DuckDBPyConnection, dict, list[dict]]:
    """Builds session.duckdb, summary.json and incidents.jsonl in the folder; returns the open database with both."""
    out.mkdir(parents=True, exist_ok=True)
    con, meta, integrity = ingest(source, out / "session.duckdb")
    first = con.execute("SELECT tick0 FROM origin").fetchone()[0]
    marks = [row[0] for row in con.execute(f"SELECT game_s(tick) FROM events WHERE ev = 'mark' AND {BY_HAND}").fetchall()]
    incidents = Incidents(con, first, marks).all()
    summary = summarise(con, meta, integrity, incidents)
    (out / "summary.json").write_text(json.dumps(summary, indent=1, default=str), encoding="utf-8")
    with (out / "incidents.jsonl").open("w", encoding="utf-8") as handle:
        for item in incidents:
            handle.write(json.dumps(item, default=str) + "\n")
    return con, summary, incidents


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("session", nargs="?", help="session folder or file; default: the newest under run/diagnostics")
    parser.add_argument("--out", help="folder for session.duckdb, summary.json and incidents.jsonl (default: beside the recording)")
    parser.add_argument("--window", nargs="+", metavar=("E", "T"), help="creature id and optional start and end, in game seconds")
    parser.add_argument("--sql", help="run one query against the session database and print the rows")
    parser.add_argument("--parquet", action="store_true", help="also export the tables as Parquet")
    parser.add_argument("--quiet", action="store_true", help="write the files without printing the summary")
    arguments = parser.parse_args()

    source = locate(arguments.session)
    out = Path(arguments.out) if arguments.out else source.parent
    con, summary, incidents = analyse(source, out)
    integrity = summary["integrity"]
    if arguments.parquet:
        con.execute(f"EXPORT DATABASE '{(out / 'parquet').as_posix()}' (FORMAT PARQUET)")
    if arguments.sql:
        cursor = con.execute(arguments.sql)
        print("\t".join(column[0] for column in cursor.description))
        for row in cursor.fetchall():
            print("\t".join(str(value) for value in row))
    elif arguments.window:
        values = arguments.window
        window(con, int(values[0]), float(values[1]) if len(values) > 1 else None, float(values[2]) if len(values) > 2 else None)
    elif not arguments.quiet:
        show(summary)
        print(f"written: {out / 'session.duckdb'}, summary.json, incidents.jsonl ({len(incidents)} episodes)")
    con.close()
    return 0 if integrity["complete"] else 2


if __name__ == "__main__":
    sys.exit(main())
