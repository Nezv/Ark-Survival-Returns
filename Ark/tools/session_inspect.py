"""The existence inspector: what recordings say of the wildlife register, against the existence model and against each other.

    python tools/session_inspect.py [<session> ...] [--bench FOLDER ...] [--label TEXT ...] [--runs N] [--no-model]
                                    [--no-ledger] [--ledger-only] [--page FILE]

A validation tool, apart from the game and from the showcase. For each recording it follows every wild animal on the
register, in the world or not, through the calendar of the register: how many of each class lived as bodies and how
many as records, in every biome region and in all the land; which of them were born, arrived and ended, when and of
what; what the rules of the life beyond the loaded land said of each region (the pressure on what each role eats);
and what the server and the frame rate paid for it. It then lives the recorded regions on in the model
(tools/existence_model.js, through Node) from the very animals the recording holds, wherever a region was left alone
for the rest of the session, and sets the band the model expects beside the curve the game traced.

Each run is entered in design/existence/runs.json, the ledger: one entry a recording, with its build, its rules, its
population and its performance, so the same figures can be followed from one build to the next. The page,
design/existence/inspector.html, shows the last run named ("now") beside the one before it ("then"), the ledger, and
the checks. With no session named, the page is rebuilt from the ledger alone.

The recording needs the register rows of a build that writes them (feature/recorder/Session.java, schema 2 and up);
the regions, the counts by region and the rounds' pressures come with schema 3. --bench names the benchmark folder of
the same launch (tools/session_bench.py --path return --record), one for each session in the same order, for the
frame rates. Run from Ark.
"""
from __future__ import annotations

import argparse
import bisect
import csv
import json
import math
import shutil
import statistics
import subprocess
import sys
import tempfile
import uuid as uuids
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path

import session_existence
from session_analyze import locate, read

ARK = Path(__file__).resolve().parents[1]
HERE = Path(__file__).resolve().parent
LEDGER = ARK / "design" / "existence" / "runs.json"
PAGE = ARK / "design" / "existence" / "inspector.html"
TEMPLATE = HERE / "inspector_template.html"
SCRIPT = HERE / "inspector_ui.js"
MODEL = ARK / "design" / "existence" / "model.json"
CLASSES = ("GRAZER", "HUNTER", "APEX", "FLYER", "SEA")
ROLES = ("grazer", "hunter", "apex", "flyer")
SIDES = ("prey", "predators")
LOADED = 16
# What ends a wild life, as the page groups it.
CAUSES = ("age", "starved", "hunted", "struck", "killed", "player", "removed", "tamed", "other")
SEA_SHARE = {"grazer": 0.05, "hunter": 0.5, "apex": 0.12, "flyer": 0.0}
WEIGHTS = ("supply", "dry", "fecundity", "starve", "kill", "carryHunter", "carryApex", "wander")


def clamp(value: float) -> float:
    return 0.0 if value < 0 else 1.0 if value > 1 else value


def traits(header: dict) -> dict:
    """The species as the rules see them, from the header of the recording: class, role, appetite, tempo and span."""
    span = float(header.get("config", {}).get("spawning.wildLifespanDays", 60.0))
    out = {}
    for s in header.get("species", []):
        realm, predator, apex = s.get("realm"), bool(s.get("predator")), bool(s.get("apex"))
        health = float(s.get("health", 50))
        out[s["id"]] = {
            "class": "SEA" if realm == "WATER" else "FLYER" if realm == "AIR" else "APEX" if apex else "HUNTER" if predator else "GRAZER",
            "role": ("apex" if apex else "hunter") if predator else "flyer" if realm == "AIR" else "grazer",
            "predator": predator, "aquatic": realm == "WATER", "health": health, "appetite": (health / 50.0) ** 0.75,
            "tempo": 1.0 / (0.5 + health / 100.0), "lifespan": span * (0.5 + health / 100.0),
            "min_group": s.get("min_group"), "max_group": s.get("max_group"), "weight": s.get("weight"), "danger": s.get("danger")}
    return out


def last_day(uuid: str, appeared: float, lifespan: float) -> float:
    """SilentLife.lastDay: the day an animal dies of age, from its id; the same number the game gets."""
    value = uuids.UUID(uuid).int
    mix = (((value >> 64) ^ (value & 0xFFFFFFFFFFFFFFFF)) * 0x9E3779B97F4A7C15) & 0xFFFFFFFFFFFFFFFF
    return appeared + lifespan * (0.25 + ((mix ^ (mix >> 32)) >> 11) / float(1 << 53))


class Clock:
    """The register's calendar against the server's ticks, from every row that carries both."""

    def __init__(self):
        self.points: dict[int, float] = {}
        self.ticks: list[int] = []
        self.days: list[float] = []

    def add(self, tick: int, day: float | None):
        if day is not None:
            self.points[tick] = day

    def close(self):
        self.ticks = sorted(self.points)
        self.days = [self.points[tick] for tick in self.ticks]

    def day(self, tick: int) -> float:
        if not self.ticks:
            return 0.0
        at = bisect.bisect_left(self.ticks, tick)
        if at == 0:
            first = 1 if len(self.ticks) > 1 else 0
            pace = (self.days[first] - self.days[0]) / max(1, self.ticks[first] - self.ticks[0])
            return self.days[0] - pace * (self.ticks[0] - tick)
        if at == len(self.ticks):
            pace = (self.days[-1] - self.days[-2]) / max(1, self.ticks[-1] - self.ticks[-2]) if len(self.ticks) > 1 else 0.0
            return self.days[-1] + pace * (tick - self.ticks[-1])
        low, high = self.ticks[at - 1], self.ticks[at]
        return self.days[at - 1] + (self.days[at] - self.days[at - 1]) * (tick - low) / max(1, high - low)


def cause_group(end: dict, kinds: dict) -> str:
    cause, silent = end.get("cause", ""), bool(end.get("silent"))
    if cause in ("age", "starved", "hunted", "removed", "tamed", "player"):
        return cause
    if cause in kinds or cause.startswith("tame:"):
        if not silent:
            return "killed"
        # Beyond the loaded land the first rules let prey that stands its ground kill one of the pack.
        victim, killer = kinds.get(end.get("species"), {}), kinds.get(cause, {})
        return "struck" if victim.get("predator") and not killer.get("predator") else "hunted"
    return "other"


def pressures(counts: dict, region: dict, kinds: dict, weights: dict) -> dict:
    """BoundedLife.Census, from the animals of a region by species: appetite, food and pressure of each role."""
    sea = bool(region.get("sea"))
    demand = dict.fromkeys(ROLES, 0.0)
    for species, number in counts.items():
        kind = kinds.get(species)
        if kind and kind["aquatic"] == sea:
            demand[kind["role"]] += number * kind["appetite"]
    # What feeds a region is the land that was seen of it; a recording that does not say takes all of it.
    supply = region.get("feeds", region.get("cells", 0)) * weights["supply"] * region.get("rich", 1.0)
    food = {"grazer": (SEA_SHARE["grazer"] if sea else 1.0) * supply,
            "hunter": (SEA_SHARE["hunter"] * supply if sea else 0.0) + weights["carryHunter"] * demand["grazer"],
            "apex": (SEA_SHARE["apex"] * supply if sea else 0.0) + weights["carryApex"] * (demand["grazer"] + demand["hunter"]),
            "flyer": 0.0 if sea else 0.07 * supply}
    pressure = {role: demand[role] / food[role] if food[role] > 0 else 2.0 if demand[role] > 0 else 0.0 for role in ROLES}
    return {"demand": demand, "food": food, "pressure": pressure, "supply": supply}


def extract(path: Path, bench: Path | None = None, label: str | None = None) -> dict:
    """Everything the page and the ledger need of one recording."""
    integrity: dict = {}
    header: dict = {}
    clock = Clock()
    regs, rolls, lives, marks, ticks, tiles, regions, entities, moves = [], [], [], [], [], {}, {}, {}, []
    described: dict = defaultdict(list)
    for row in read(path, integrity):
        kind = row.get("t")
        if kind == "header":
            header = row
        elif kind == "tick":
            ticks.append((row["k"], row.get("dur_us", 0), row.get("mobs", 0)))
        elif kind == "reg":
            regs.append(row)
            clock.add(row["k"], row.get("day"))
        elif kind == "roll":
            rolls.append(row)
            clock.add(row["k"], row.get("day"))
        elif kind == "life":
            lives.append(row)
            if row.get("ev") == "round":
                clock.add(row["k"], row.get("day"))
        elif kind == "region":
            regions[",".join(map(str, row["tile"]))] = row
            described[",".join(map(str, row["tile"]))].append(row)
        elif kind == "tile":
            tiles[tuple(row["tile"])] = bytes.fromhex(row["cells"])
        elif kind == "ent":
            if "uuid" in row:
                entities[row["e"]] = row
        elif kind == "ev" and row.get("ev") in ("leave", "return"):
            moves.append(row)
        elif kind == "ev" and row.get("ev") == "mark":
            marks.append(row)
    if not rolls:
        raise SystemExit(f"{path}: no roll call of the register; the recording is of a build before the recorder followed it")
    clock.close()
    kinds = traits(header)
    config = header.get("config", {})
    first_tick = ticks[0][0] if ticks else rolls[0]["k"]
    last_tick = ticks[-1][0] if ticks else rolls[-1]["k"]
    seconds = lambda tick: round((tick - first_tick) / 20.0, 1)
    day0 = clock.day(first_tick)
    day = lambda tick: round(clock.day(tick) - day0, 3)
    columns = header.get("legend", {}).get("roll") or ["uuid", "species", "pack", "lvl", "x", "y", "z", "hunger", "appeared", "seen", "flags"]

    def region_at(key: str, tick: int) -> dict:
        """The region as the recording described it then: more of it is seen as the session goes."""
        rows = described[key]
        return next((row for row in reversed(rows) if row["k"] <= tick), rows[0])

    def region_of(x: int, z: int) -> str | None:
        chunk_x, chunk_z = x >> 4, z >> 4
        cells = tiles.get((chunk_x // 32, chunk_z // 32))
        return None if cells is None else f"{chunk_x // 32},{chunk_z // 32},{cells[(chunk_z % 32) * 32 + chunk_x % 32]}"

    # ---- who lives: a sample at every count of the register (schema 3) or, failing that, at every roll call.
    samples = []
    if any("regions" in reg for reg in regs):
        for reg in regs:
            by_region = {}
            for entry in reg.get("regions", []):
                by_region[",".join(map(str, entry["tile"])) if "tile" in entry else "-"] = entry.get("species", {})
            samples.append((reg["k"], by_region))
    else:
        for roll in rolls:
            by_region: dict = defaultdict(lambda: defaultdict(lambda: [0, 0]))
            for cells in roll.get("rows", []):
                record = dict(zip(columns, cells))
                by_region[region_of(record["x"], record["z"]) or "-"][record["species"]][0 if record["flags"] & LOADED else 1] += 1
            samples.append((roll["k"], {key: dict(value) for key, value in by_region.items()}))
    keys = sorted({key for _, by_region in samples for key in by_region})
    blank = lambda: {name: {"world": [0] * len(samples), "record": [0] * len(samples)} for name in CLASSES + SIDES}
    total, per_region, per_species = blank(), {key: blank() for key in keys}, {}
    for index, (_, by_region) in enumerate(samples):
        for key, species in by_region.items():
            for name, (world, record) in species.items():
                kind = kinds.get(name)
                if kind is None:
                    continue
                mine = per_species.setdefault(name, {"world": [0] * len(samples), "record": [0] * len(samples)})
                mine["world"][index] += world
                mine["record"][index] += record
                for group in (kind["class"], "predators" if kind["predator"] else "prey"):
                    for target in (total, per_region[key]):
                        target[group]["world"][index] += world
                        target[group]["record"][index] += record
    series = {"t": [seconds(tick) for tick, _ in samples], "day": [day(tick) for tick, _ in samples], "all": total, "regions": per_region,
              "species": per_species}

    # ---- the weights the run lived by, and the pressures: recorded by the rounds, and counted again from the animals.
    model = json.loads(MODEL.read_text(encoding="utf-8"))
    weights = {entry["id"]: entry["value"] for entry in model["weights"]}
    for name in WEIGHTS:
        if f"spawning.boundedLife.{name}" in config:
            weights[name] = float(config[f"spawning.boundedLife.{name}"])
    weights["refill"] = float(config.get("spawning.populationRefillDays", weights["refill"]))
    rules = str(config.get("spawning.silentRules", "ODDS")).upper()
    counted = {}
    for key in keys:
        if key not in regions:
            continue
        rows = {name: {role: [] for role in ROLES} for name in ("pressure", "demand", "food")}
        for tick, by_region in samples:
            species = by_region.get(key, {})
            now = pressures({name: sum(pair) for name, pair in species.items()}, region_at(key, tick), kinds, weights)
            for role in ROLES:
                rows["pressure"][role].append(round(min(now["pressure"][role], 3.0), 3))
                rows["demand"][role].append(round(now["demand"][role], 2))
                rows["food"][role].append(round(now["food"][role], 2))
        counted[key] = rows
    rounds: dict = defaultdict(lambda: defaultdict(list))
    unplaced = Counter()
    by_look = defaultdict(list)
    for key, region in regions.items():
        by_look[(region.get("biome"), region.get("cells"))].append(key)
    for life in lives:
        if life.get("ev") != "round":
            continue
        key = ",".join(map(str, life["tile"])) if "tile" in life else None
        if key is None:
            # The first rules name the region by its biome and size only.
            same = by_look.get((life.get("biome"), life.get("cells")), [])
            key = same[0] if len(same) == 1 else "-"
            unplaced[key == "-"] += 1
        mine = rounds[key]
        mine["day"].append(day(life["k"]) if life.get("day") is None else round(life["day"] - day0, 3))
        mine["t"].append(seconds(life["k"]))
        for name in ("records", "ended", "born", "arrived", "aged", "hunted", "starved", "groups"):
            mine[name].append(life.get(name, 0))
        mine["days"].append(life.get("days", float(config.get("spawning.silentRoundDays", 1.0))))
        for name in ("pressure", "demand", "food", "animals", "taking"):
            if name in life:
                mine[name].append([round(value, 3) if isinstance(value, float) else value for value in life[name]])

    # ---- what happened to whom: every record on a roll call or in an event, and its days as a body and as a record.
    born = {life["u"]: life for life in lives if life.get("ev") == "born"}
    came = {life["u"]: life for life in lives if life.get("ev") == "arrived"}
    ends = {life["u"]: life for life in lives if life.get("ev") == "end"}
    bodies = {entity["uuid"]: entity for entity in entities.values()}
    switches: dict = defaultdict(list)
    for entity in entities.values():
        switches[entity["uuid"]].append((entity["k"], "w"))
    for move in moves:
        entity = entities.get(move.get("e"))
        if entity is not None:
            switches[entity["uuid"]].append((move["k"], "w" if move["ev"] == "return" else "r"))
    people: dict = {}
    for roll in rolls:
        for cells in roll.get("rows", []):
            record = dict(zip(columns, cells))
            person = people.setdefault(record["uuid"], {"first": roll["k"], "record": record, "start": "there" if roll is rolls[0] else None})
            person["last"] = roll["k"]
            person["hunger"] = record["hunger"]
            switches[record["uuid"]].append((roll["k"], "w" if record["flags"] & LOADED else "r"))
    for uuid, life in list(born.items()) + list(came.items()) + list(ends.items()):
        if uuid in people:
            continue
        pos = life.get("pos", [0, 0, 0])
        people[uuid] = {"first": life["k"], "last": life["k"], "start": None, "hunger": None,
                        "record": {"uuid": uuid, "species": life.get("species"), "pack": life.get("pack", ""), "lvl": life.get("lvl", 0),
                                   "x": pos[0], "y": pos[1], "z": pos[2], "appeared": None}}
    individuals, entered, causes = [], Counter(), Counter()
    events = []
    for uuid, person in people.items():
        record, end = person["record"], ends.get(uuid)
        kind = kinds.get(record["species"])
        if kind is None:
            continue
        start = person["start"] or ("born" if uuid in born else "arrived" if uuid in came else "placed" if uuid in bodies else "unexplained")
        if start != "there":
            entered[start] += 1
        begin = born.get(uuid, came.get(uuid, {})).get("k", bodies.get(uuid, {}).get("k", person["first"]))
        begin = min(begin, person["first"])
        finish = end["k"] if end else last_tick
        state, spans, at = None, [], begin
        if uuid in born or uuid in came:
            state = "r"
        for tick, to in sorted(switches.get(uuid, [])):
            if tick > finish:
                break
            if state is None:
                state, at = to, min(at, tick)
            elif to != state:
                spans.append([day(at), day(tick), state])
                state, at = to, tick
        if state is not None and finish > at or not spans:
            spans.append([day(at), day(finish), state or "r"])
        entry = {"u": uuid[:8], "s": record["species"], "l": record["lvl"], "p": record.get("pack", "")[:8], "c": kind["class"],
                 "r": region_of(record["x"], record["z"]), "a": start, "d0": day(begin), "d1": day(finish), "sp": spans}
        if record.get("appeared") is not None:
            entry["ap"] = round(record["appeared"] - day0, 2)
        if person.get("hunger") is not None:
            entry["h"] = round(person["hunger"], 2)
        if end:
            group = cause_group(end, kinds)
            causes[group] += 1
            entry["e"] = group
            entry["ec"] = end.get("cause")
            entry["es"] = 1 if end.get("silent") else 0
            entry["lv"] = round(end.get("lived", 0), 1)
            events.append((day(end["k"]) if end.get("day") is None else round(end["day"] - day0, 3), group, kind["class"], entry["r"]))
        individuals.append(entry)
    for uuid, life in list(born.items()) + list(came.items()):
        kind = kinds.get(life.get("species"))
        if kind:
            pos = life.get("pos", [0, 0, 0])
            events.append((round(life["day"] - day0, 3) if life.get("day") is not None else day(life["k"]),
                           "born" if uuid in born else "arrived", kind["class"], region_of(pos[0], pos[2])))
    individuals.sort(key=lambda entry: (CLASSES.index(entry["c"]), entry["s"], entry["d0"], entry["u"]))

    # ---- every record accounted for, from one roll call to the next.
    vanished, unexplained = 0, 0
    for before, after in zip(rolls, rolls[1:]):
        a = {cells[0] for cells in before.get("rows", [])}
        b = {cells[0] for cells in after.get("rows", [])}
        vanished += sum(1 for uuid in a - b if uuid not in ends)
        unexplained += sum(1 for uuid in b - a if uuid not in born and uuid not in came and uuid not in bodies)

    # ---- what it cost: the server's tick by the second, the budget's pass, the rounds and the bodies brought back.
    per_second: dict = defaultdict(list)
    mobs: dict = {}
    for tick, micros, count in ticks:
        per_second[(tick - first_tick) // 20].append(micros / 1000.0)
        mobs[(tick - first_tick) // 20] = count
    order = sorted(per_second)
    cost = lambda name: [life for life in lives if life.get("ev") == name]
    perf = {"t": order, "tick_ms": [round(statistics.fmean(per_second[s]), 2) for s in order],
            "tick_max": [round(max(per_second[s]), 1) for s in order], "mobs": [mobs[s] for s in order],
            "pass": [[seconds(l["k"]), round(l["us"] / 1000, 2), l.get("records", 0), l.get("bodies", 0)] for l in cost("pass")],
            "rounds": [[seconds(l["k"]), round(l["us"] / 1000, 2), l.get("regions", 0), l.get("rounds", 0)] for l in cost("rounds")],
            "welcomes": [[seconds(l["k"]), round(l["us"] / 1000, 2), l.get("bodies", 0)] for l in cost("welcomes")]}
    data = session_existence.load(path)
    legs = session_existence.legs(data)
    frames, gpu, meta, fps = [], {}, {}, {}
    if bench is not None and (bench / "meta.json").is_file():
        meta = json.loads((bench / "meta.json").read_text(encoding="utf-8"))
        frames = session_existence.frames(bench)
        with (bench / "samples.csv").open(encoding="utf-8") as handle:
            rows = [row for row in csv.DictReader(handle) if row["phase"] not in ("settle", "done")]
        if rows:
            zero = float(rows[0]["t_ms"])
            fps = {"t": [round((float(row["t_ms"]) - zero) / 1000, 1) for row in rows], "fps": [float(row["fps"]) for row in rows],
                   "phase": [row["phase"] for row in rows], "creatures": [int(row["creatures"]) for row in rows],
                   "server_tick_ms": [float(row["server_tick_ms"]) for row in rows]}
        if (bench / "gpu.csv").is_file():
            readings = [line.split(",") for line in (bench / "gpu.csv").read_text(encoding="utf-8").splitlines() if line.count(",") >= 4]
            # Columns: time, load %, memory %, temperature, watts. The tail of the file is the measured flight.
            readings = readings[len(readings) // 4:]
            if readings:
                gpu = {"temperature": round(statistics.fmean(float(r[3]) for r in readings), 1), "watts": round(statistics.fmean(float(r[4]) for r in readings), 1),
                       "load": round(statistics.fmean(float(r[1]) for r in readings), 1)}

    # ---- the place left and come back to, as tools/session_existence.py judges it.
    place = None
    try:
        verdict = session_existence.analyse(path)
        if verdict.get("recorded"):
            place = {"from": verdict["from"], "to": verdict["to"], "cohort": verdict["cohort"]["animals"], "groups": verdict["cohort"]["groups"],
                     "fates": verdict["fates"], "ends": verdict["ends"], "newcomers": verdict["newcomers"],
                     "without_body": verdict["lived_without_body"]["animals"], "result": verdict["verdict"]["result"], "why": verdict["verdict"]["why"]}
    except SystemExit as stop:
        place = {"result": "not_exercised", "why": str(stop)}

    build = header.get("build", {})
    span = day(last_tick)
    run = {
        "session": header.get("session", path.parent.name), "label": label or "", "started": header.get("started"),
        "schema": header.get("schema"), "complete": not integrity.get("truncated") and "footer" in integrity.get("kinds", ("footer",)),
        "build": {"commit": str(build.get("commit", ""))[:8], "dirty": str(build.get("dirty", "")).lower() == "true", "classes": build.get("classes_crc")},
        "rules": rules, "speed": float(config.get("spawning.wildCalendarSpeed", 1.0)),
        "round_days": float(config.get("spawning.silentRoundDays", 1.0)), "model": config.get("spawning.populationModel"),
        "weights": {name: weights[name] for name in WEIGHTS + ("refill",)},
        "lifespan": float(config.get("spawning.wildLifespanDays", 60.0)),
        "view_distance": header.get("server", {}).get("view_distance"), "seed": header.get("server", {}).get("seed"),
        "seconds": seconds(last_tick), "days": span,
        "marks": [{"name": mark.get("note"), "t": seconds(mark["k"]), "day": day(mark["k"])} for mark in marks],
        "kinds": {name: {key: kind[key] for key in ("class", "role", "predator", "health")} | {"appetite": round(kind["appetite"], 3),
                                                                                              "lifespan": round(kind["lifespan"], 1)}
                  for name, kind in kinds.items() if name in per_species or any(e["s"] == name for e in individuals)},
        "regions": {key: {name: region.get(name) for name in ("biome", "type", "cells", "surveyed", "feeds", "rich", "room", "quota", "zones", "pool")}
                    | {flag: bool(region.get(flag)) for flag in ("snowy", "water", "sea")} for key, region in regions.items()},
        "series": series, "pressure": {"counted": counted},
        "rounds": {key: dict(value) for key, value in rounds.items()},
        "events": sorted(events, key=lambda event: event[0]),
        "individuals": individuals, "entered": dict(entered), "causes": dict(causes),
        "accounting": {"vanished": vanished, "unexplained": unexplained, "roll_calls": len(rolls)},
        "perf": perf, "legs": legs, "frames": frames, "fps": fps, "gpu": gpu,
        "bench": {key: meta.get(key) for key in ("shaders", "render_distance", "window", "anchor", "teleport_blocks", "out_blocks", "hold_seconds",
                                                 "path", "gpu") if key in meta},
        "place": place,
        "_state": {"rolls": rolls, "columns": columns, "region_of": region_of, "region_at": region_at, "kinds": kinds, "clock": clock, "day0": day0,
                   "samples": samples, "config": config},
    }
    return run


# ----------------------------------------------------------------------------------------------- the model


def alone(run: dict, least: float = 10.0) -> dict:
    """For each region, its longest stretch of counts with none of its animals in the world: the first and the last of them."""
    out = {}
    days = run["series"]["day"]
    for key, mine in run["series"]["regions"].items():
        if key not in run["regions"]:
            continue
        best, start = None, None
        for index in range(len(days) + 1):
            empty = index < len(days) and all(mine[name]["world"][index] == 0 for name in CLASSES) \
                and any(mine[name]["record"][index] for name in CLASSES)
            if empty and start is None:
                start = index
            elif not empty and start is not None:
                if best is None or days[index - 1] - days[start] > days[best[1]] - days[best[0]]:
                    best = (start, index - 1)
                start = None
        if best is not None and days[best[1]] - days[best[0]] >= least:
            out[key] = best
    return out


def model_bands(run: dict, runs: int) -> dict:
    """Lives each region that was left alone on in the model, from the records of the first roll call after it was left."""
    node = shutil.which("node")
    state = run["_state"]
    free = alone(run)
    if not node or not free:
        return {"why": "Node is not installed" if not node else "no region was left alone for the rest of the recording"}
    import showcase_existence
    data = showcase_existence.data()
    known = {s["id"] for s in data["species"]}
    kinds, clock, day0, config = state["kinds"], state["clock"], state["day0"], state["config"]
    setups, starts = [], {}
    for key, (index, until) in free.items():
        tick = state["samples"][index][0]
        end_day = run["series"]["day"][until]
        roll = next((r for r in state["rolls"] if tick <= r["k"] <= state["samples"][until][0]), None)
        if roll is None:
            continue
        # The region as it was known when it was left: nothing more of it is seen while nobody is there.
        described = state["region_at"](key, state["samples"][until][0])
        region = {**run["regions"][key], **{name: described.get(name) for name in ("feeds", "cells", "rich", "room", "quota", "zones", "pool")}}
        packs: dict = defaultdict(list)
        for cells in roll.get("rows", []):
            record = dict(zip(state["columns"], cells))
            kind = kinds.get(record["species"])
            if kind is None or record["species"] not in known or state["region_of"](record["x"], record["z"]) != key:
                continue
            packs[(record["pack"], record["species"])].append(
                {"level": record["lvl"], "hunger": record["hunger"], "born": record["appeared"] - day0,
                 "last": last_day(record["uuid"], record["appeared"], kind["lifespan"]) - day0})
        start = clock.day(roll["k"]) - day0
        if end_day - start < 8:
            continue
        zones = region.get("zones") or [0, 0, 0, 1]
        zone = max(range(1, len(zones)), key=lambda z: zones[z]) if any(zones[1:]) else 3
        starts[key] = (round(start, 2), round(end_day, 2))
        setups.append({
            "key": key, "series": True, "runs": runs, "seed": 11, "rules": "bounded" if run["rules"] == "BOUNDED" else "coded",
            "region": {"id": key, "name": region["biome"], "type": region["type"], "water": region["water"], "fertility": region["rich"],
                       "pool": [name for name in region.get("pool") or [] if name in known]},
            "cells": region.get("feeds") or region["cells"], "zone": zone, "sea": region["sea"], "richness": region["rich"],
            "quota": dict(zip(CLASSES, region.get("quota") or [0] * 5)), "room": dict(zip(CLASSES, region.get("room") or [0] * 5)),
            "state": [{"species": species, "members": members} for (_, species), members in packs.items()],
            "day": start, "days": end_day - start, "every": 1, "pace": 1.0 / run["round_days"],
            # Under the first rules a region nobody is near takes no arrivals in.
            "arrivals": run["rules"] == "BOUNDED", "weights": run["weights"],
            "config": {"wildLifespanDays": run["lifespan"], "populationRefillDays": run["weights"]["refill"],
                       "silentRoundDays": run["round_days"],
                       "healthGrowth": float(config.get("combat.healthGrowth", config.get("creatures.healthGrowth", 0.1))),
                       "damageGrowth": float(config.get("combat.damageGrowth", config.get("creatures.damageGrowth", 0.14)))}})
    if not setups:
        return {"why": "no region was left alone for long enough"}
    with tempfile.TemporaryDirectory(prefix="ark-inspect-") as scratch:
        files = [Path(scratch) / "data.json", Path(scratch) / "setups.json"]
        files[0].write_text(json.dumps(data), encoding="utf-8")
        files[1].write_text(json.dumps(setups), encoding="utf-8")
        done = subprocess.run([node, str(HERE / "existence_model.js"), *map(str, files)], capture_output=True, text=True, encoding="utf-8")
    if done.returncode != 0:
        return {"why": "the model did not run: " + done.stderr.strip()[-400:]}
    bands = {"runs": runs, "regions": {}}
    for setup, result in zip(setups, json.loads(done.stdout)):
        mine = result["series"]
        rounded = lambda rows: [round(value, 2) for value in rows]
        bands["regions"][setup["key"]] = {
            "from": starts[setup["key"]][0], "to": starts[setup["key"]][1], "days": rounded(mine["days"]),
            "classes": {kind: {"mean": rounded(mine["mean"][kind]), "low": rounded(mine["low"][kind]), "high": rounded(mine["high"][kind])}
                        for kind in mine["kinds"]},
            "sides": {side: {name: rounded(rows) for name, rows in mine["sides"][side].items()} for side in ("prey", "predators", "total")},
            "pressure": {role: rounded(rows) for role, rows in mine["pressure"].items()},
            "gone": result["verdict"]["gone"], "start": result["start"]}
    return bands


def pooled(run: dict, bands: dict) -> dict | None:
    """The land left alone, taken together: what the game counted there and what the model expects of it, day by day."""
    if not bands.get("regions"):
        return None
    # The regions that were alone at the same time: the one alone for the shortest goes until what is left shares 20 days.
    keys = sorted(bands["regions"])
    window = lambda: (max(bands["regions"][key]["from"] for key in keys), min(bands["regions"][key]["to"] for key in keys))
    while len(keys) > 1 and window()[1] - window()[0] < 20:
        keys.remove(min(keys, key=lambda key: bands["regions"][key]["to"] - bands["regions"][key]["from"]))
    begin, end = window()
    days = [d for d in run["series"]["day"] if begin <= d <= end]
    if len(days) < 3:
        return None

    def at(rows_days, rows, when):
        index = min(max(bisect.bisect_right(rows_days, when) - 1, 0), len(rows) - 1)
        if index + 1 < len(rows) and rows_days[index + 1] > rows_days[index]:
            share = min(1.0, max(0.0, (when - rows_days[index]) / (rows_days[index + 1] - rows_days[index])))
            return rows[index] + (rows[index + 1] - rows[index]) * share
        return rows[index]

    out = {"from": begin, "to": end, "keys": keys, "days": days, "sides": {}}
    for side in ("prey", "predators", "total"):
        mean, low, high = [], [], []
        for when in days:
            m = v_low = v_high = 0.0
            for key in keys:
                mine = bands["regions"][key]
                here = at(mine["days"], mine["sides"][side]["mean"], when)
                m += here
                v_low += (here - at(mine["days"], mine["sides"][side]["low"], when)) ** 2
                v_high += (at(mine["days"], mine["sides"][side]["high"], when) - here) ** 2
            mean.append(round(m, 2))
            low.append(round(m - math.sqrt(v_low), 2))
            high.append(round(m + math.sqrt(v_high), 2))
        out["sides"][side] = {"mean": mean, "low": low, "high": high}
    return out


def observed(run: dict, keys: list[str], group: str, begin: float = -1e9, end: float = 1e9) -> tuple[list[float], list[int]]:
    """The animals of a class or a side in these regions, bodies and records together, at every count between two days."""
    days, rows = [], []
    for index, when in enumerate(run["series"]["day"]):
        if when < begin or when > end:
            continue
        days.append(when)
        if group == "total":
            rows.append(sum(run["series"]["regions"][key][side][kind][index] for key in keys for side in SIDES for kind in ("world", "record")))
        else:
            rows.append(sum(run["series"]["regions"][key][group][kind][index] for key in keys for kind in ("world", "record")))
    return days, rows


# ---------------------------------------------------------------------------------------- checks and ledger


def checks(run: dict, bands: dict) -> list[dict]:
    """What a recording has to show for the register to be trusted; each with what was found."""
    out = []
    add = lambda name, what, result, found: out.append({"id": name, "what": what, "result": result, "found": found})
    accounting = run["accounting"]
    lost = accounting["vanished"] + accounting["unexplained"]
    add("accounted", "Every record that left the register has an end on it, and every new one an origin",
        "pass" if not lost else "fail",
        f"{accounting['roll_calls']} roll calls, {len(run['individuals'])} animals followed: {accounting['vanished']} left without an end, "
        f"{accounting['unexplained']} came from nowhere")
    removed = run["causes"].get("removed", 0)
    add("bodies", "No record is dropped for want of room for its body", "pass" if not removed else "review",
        f"{removed} taken off the register without dying" if removed else "none taken off the register without dying")
    pool = pooled(run, bands)
    free = pool["keys"] if pool else []
    if free:
        begin, until = pool["from"], pool["to"]
        days, total = observed(run, free, "total", begin, until)
        tail = total[len(total) * 2 // 3:]
        peak, level = max(total), max(total[0], statistics.fmean(tail))
        add("bounded", "The land left alone never holds far more animals than it started or settled with",
            "pass" if peak <= level * 1.5 + 4 else "fail", f"{total[0]} at the start, {peak} at the most, {statistics.fmean(tail):.0f} over the last third of "
            f"{days[-1] - days[0]:.0f} days")
        gone = []
        for kind in CLASSES:
            _, rows = observed(run, free, kind, begin, until)
            room = sum((run["regions"][key].get("room") or [0] * 5)[CLASSES.index(kind)] for key in free)
            if rows[0] > 0 and room >= 1 and statistics.fmean(rows[len(rows) * 2 // 3:]) < 0.5:
                gone.append(kind.lower())
        add("alive", "Every class the land left alone has room for a group of, and started with, is still there at the end",
            "pass" if not gone else "fail", "all still there" if not gone else "gone: " + ", ".join(gone))
        if pool:
            inside, notes = [], []
            for side in ("prey", "predators"):
                _, rows = observed(run, free, side, begin, until)
                band = pool["sides"][side]
                near = [max(2.0, 0.15 * mean) for mean in band["mean"]]
                hits = sum(1 for value, low, high, slack in zip(rows, band["low"], band["high"], near) if low - slack <= value <= high + slack)
                inside.append(hits / len(rows))
                notes.append(f"{side} inside the band on {100 * hits / len(rows):.0f} % of the counts (ended at {rows[-1]}, the model at "
                             f"{band['mean'][-1]:.0f}, {band['low'][-1]:.0f} to {band['high'][-1]:.0f})")
            add("model", f"The game goes where the model goes: the land left alone stays in the band of {bands['runs']} model runs "
                "from the same animals (widened by 15 %, at least two animals)", "pass" if min(inside) >= 0.7 else "review",
                f"{len(free)} regions alone from day {begin:.0f} to day {until:.0f}: " + "; ".join(notes))
    else:
        add("model", "The game goes where the model goes", "not_exercised", bands.get("why", "no region was left alone"))
    if run["rules"] == "BOUNDED" and any("pressure" in mine for mine in run["rounds"].values()):
        worst, late, expected, seen, compared = 0.0, 0.0, 0.0, 0, 0.0
        kinds = run["_state"]["kinds"]
        for key, mine in run["rounds"].items():
            region = run["regions"].get(key)
            for index, pressure in enumerate(mine.get("pressure", [])):
                worst = max(worst, pressure[0])
                if index >= len(mine["pressure"]) // 2:
                    late = max(late, pressure[0])
                if region is None:
                    continue
                # Hunted: each animal that took part against the daily odds its hunters' pressure gives it.
                demand = mine["demand"][index]
                chased = {"grazer": (clamp(pressure[1]) if demand[1] > 0 else 0) + (clamp(pressure[2]) if demand[2] > 0 else 0),
                          "hunter": clamp(pressure[2]) if demand[2] > 0 else 0}
                sample = min(range(len(run["series"]["day"])), key=lambda i: abs(run["series"]["day"][i] - mine["day"][index]))
                species = run["_state"]["samples"][sample][1].get(key, {})
                for slot, role in ((0, "grazer"), (1, "hunter")):
                    members = [(kinds[name]["tempo"], pair[1]) for name, pair in species.items()
                               if name in kinds and kinds[name]["role"] == role and kinds[name]["aquatic"] == region["sea"] and pair[1] > 0]
                    if not members or not mine["taking"][index][slot]:
                        continue
                    tempo = sum(t * n for t, n in members) / sum(n for _, n in members)
                    odds = 1 - (1 - min(1.0, run["weights"]["kill"] * tempo * chased[role])) ** mine["days"][index]
                    expected += mine["taking"][index][slot] * odds
                seen += mine["hunted"][index]
        add("pressure", "The pressure on the plant eaters settles at or below what the land grows",
            "pass" if late <= 1.1 else "review", f"at most {late:.2f} over the later half of the rounds ({worst:.2f} at the most in all)")
        if expected > 0:
            slack = 3 * math.sqrt(expected) + 2
            add("hunted", "As many records are hunted as the pressure of their hunters says, round by round",
                "pass" if abs(seen - expected) <= slack else "review", f"{seen} hunted against {expected:.1f} expected (within {slack:.1f})")
    place = run.get("place") or {}
    if place.get("result"):
        add("place", "The animals of the place left and come back to are alive there, alive as records, or have an end on record",
            {"persisted": "pass", "failed": "fail"}.get(place["result"], "not_exercised"),
            (f"{place.get('cohort', 0)} animals at '{place.get('from')}', {place.get('without_body', 0)} without a body in between: "
             + ", ".join(f"{count} {name.replace('_', ' ')}" for name, count in sorted(place.get("fates", {}).items(), key=lambda item: -item[1])))
            if "cohort" in place else place.get("why", ""))
    return out


def summary(run: dict, bands: dict, found: list[dict]) -> dict:
    """The ledger's entry for a recording: its build, its rules, its population and what it cost."""
    series = run["series"]
    count = lambda index: {kind: series["all"][kind]["world"][index] + series["all"][kind]["record"][index] for kind in CLASSES}
    totals = [sum(series["all"][side][kind][i] for side in SIDES for kind in ("world", "record")) for i in range(len(series["t"]))]
    days = series["day"]
    animal_days = sum((days[i + 1] - days[i]) * (totals[i] + totals[i + 1]) / 2 for i in range(len(days) - 1))
    present = lambda index: sorted(name for name, mine in series["species"].items() if mine["world"][index] + mine["record"][index] > 0)
    first, last = present(0), present(len(days) - 1)
    rate = lambda number: round(100 * number / animal_days, 2) if animal_days > 0 else None
    born = sum(1 for event in run["events"] if event[1] == "born")
    arrived = sum(1 for event in run["events"] if event[1] == "arrived")
    rounds = sum(len(mine["day"]) for mine in run["rounds"].values())
    # The pressure on each role over all the land, counted from the animals: the same yardstick for either rule set.
    pressure, counted = {}, run["pressure"]["counted"].values()
    for role in ROLES:
        rows = []
        for index in range(len(days) * 2 // 3, len(days)):
            demand, food = sum(mine["demand"][role][index] for mine in counted), sum(mine["food"][role][index] for mine in counted)
            if food > 0:
                rows.append(demand / food)
        if rows and any(rows):
            pressure[role] = round(statistics.fmean(rows), 3)
    leaving = next((mark["day"] for mark in run["marks"] if mark["name"] == "depart"), None)
    left = min(range(len(days)), key=lambda index: abs(days[index] - leaving)) if leaving is not None else 0
    ticks = [value for value in run["perf"]["tick_ms"]]
    passes = [row[1] for row in run["perf"]["pass"]]
    lived = [row[1] for row in run["perf"]["rounds"]]
    welcomes = [row[1] for row in run["perf"]["welcomes"]]
    step = max(1, len(days) // 48)
    return {
        "session": run["session"], "label": run["label"], "started": run["started"], "commit": run["build"]["commit"] + ("+" if run["build"]["dirty"] else ""),
        "rules": run["rules"], "calendar_speed": run["speed"], "round_days": run["round_days"], "weights": run["weights"] if run["rules"] == "BOUNDED" else None,
        "path": run["bench"].get("path"), "out_blocks": run["bench"].get("out_blocks"), "render_distance": run["bench"].get("render_distance", run["view_distance"]),
        "seconds": run["seconds"], "days": round(run["days"], 2), "rounds": rounds, "regions": len(run["series"]["regions"]),
        "population": {"start": count(0), "depart": count(left), "end": count(len(days) - 1), "total_start": totals[0],
                       "total_depart": totals[left], "total_end": totals[-1],
                       "total_min": min(totals), "total_max": max(totals), "species_start": len(first), "species_end": len(last),
                       "lost": sorted(set(first) - set(last)), "gained": sorted(set(last) - set(first))},
        "animal_days": round(animal_days, 1),
        "per_100_animal_days": {"ended": rate(sum(run["causes"].values())), "born": rate(born), "arrived": rate(arrived),
                                **{cause: rate(run["causes"].get(cause, 0)) for cause in CAUSES if run["causes"].get(cause)}},
        "counts": {"ended": sum(run["causes"].values()), "born": born, "arrived": arrived, **run["causes"], "placed": run["entered"].get("placed", 0)},
        "pressure": pressure or None,
        "place": {key: run["place"].get(key) for key in ("cohort", "without_body", "fates", "result")} if run.get("place") else None,
        "checks": {check["id"]: check["result"] for check in found},
        "performance": {
            "fps": {leg["leg"]: leg["fps"] for leg in run["frames"]} or None,
            "fps_low_1pc": {leg["leg"]: leg["fps_low_1pc"] for leg in run["frames"]} or None,
            "tick_ms": round(statistics.fmean(ticks), 2) if ticks else None,
            "tick_ms_p95": round(sorted(ticks)[int(len(ticks) * 0.95)], 2) if ticks else None,
            "tick_ms_max": max(run["perf"]["tick_max"], default=None),
            "pass_ms_median": round(statistics.median(passes), 2) if passes else None, "pass_ms_max": max(passes, default=None),
            "rounds_ms_mean": round(statistics.fmean(lived), 2) if lived else None, "rounds_ms_max": max(lived, default=None),
            "welcome_ms_max": max(welcomes, default=None), "records_max": max((row[2] for row in run["perf"]["pass"]), default=None),
            "gpu": run["gpu"] or None},
        "series": {"day": [round(d, 1) for d in days[::step]],
                   **{side: [series["all"][side]["world"][i] + series["all"][side]["record"][i] for i in range(0, len(days), step)] for side in SIDES}},
    }


def ledger(entries: list[dict] | None = None) -> dict:
    """The ledger as it is, with these entries entered: a recording has one, and a later reading of it takes the place of the first."""
    book = json.loads(LEDGER.read_text(encoding="utf-8")) if LEDGER.is_file() else {
        "about": "The ledger of the existence inspector (tools/session_inspect.py): one entry a recording of the wildlife register, oldest "
                 "first, with its build, its rules, its population and what it cost. Rates are per 100 animal-days of the register's "
                 "calendar. Written by the tool; the recordings themselves stay in run/diagnostics.", "runs": []}
    for entry in entries or []:
        book["runs"] = [run for run in book["runs"] if run["session"] != entry["session"]] + [entry]
    book["runs"].sort(key=lambda run: run.get("started") or "")
    if entries:
        LEDGER.parent.mkdir(parents=True, exist_ok=True)
        LEDGER.write_text(json.dumps(book, indent=1) + "\n", encoding="utf-8")
    return book


def page(runs: list[dict], book: dict, target: Path):
    """The inspector: the runs named, the ledger and the page's script in one file that needs nothing else."""
    for run in runs:
        run.pop("_state", None)
    payload = {"built": datetime.now().astimezone().isoformat(timespec="seconds"), "runs": runs, "ledger": book["runs"],
               "classes": CLASSES, "roles": ROLES, "causes": CAUSES}
    text = TEMPLATE.read_text(encoding="utf-8")
    text = text.replace("/*INSPECTOR_UI*/", SCRIPT.read_text(encoding="utf-8").replace("</script", "<\\/script"))
    text = text.replace("/*INSPECTOR_DATA*/null", json.dumps(payload, separators=(",", ":")).replace("</", "<\\/"))
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("sessions", nargs="*", help="session folders or files, oldest first; the last is 'now', the one before it 'then'")
    parser.add_argument("--bench", action="append", default=[], help="the benchmark folder of a session, in the order of the sessions")
    parser.add_argument("--label", action="append", default=[], help="a few words for a session in the ledger, in the order of the sessions")
    parser.add_argument("--runs", type=int, default=40, help="model runs behind each band (default 40)")
    parser.add_argument("--no-model", action="store_true", help="leave the model's bands out (no Node needed)")
    parser.add_argument("--no-ledger", action="store_true", help="do not enter the sessions in design/existence/runs.json")
    parser.add_argument("--ledger-only", action="store_true", help="enter the sessions in the ledger and leave the page as it is")
    parser.add_argument("--page", default=str(PAGE), help=f"the page to write (default {PAGE.relative_to(ARK)})")
    arguments = parser.parse_args()
    runs, entries = [], []
    for index, name in enumerate(arguments.sessions):
        path = locate(name)
        bench = Path(arguments.bench[index]) if index < len(arguments.bench) and arguments.bench[index] else None
        run = extract(path, bench, arguments.label[index] if index < len(arguments.label) else None)
        bands = {"why": "left out"} if arguments.no_model else model_bands(run, arguments.runs)
        run["model"] = bands
        run["pool"] = pooled(run, bands)
        run["checks"] = checks(run, bands)
        entry = summary(run, bands, run["checks"])
        run["summary"] = entry
        runs.append(run)
        entries.append(entry)
        print(f"{run['session']}: {run['rules']}, {run['days']:.1f} days of the calendar in {run['seconds']:.0f} s, "
              f"{entry['population']['total_start']} animals at the start and {entry['population']['total_end']} at the end, "
              f"{entry['counts']['ended']} ended, {entry['counts']['born']} born, {entry['counts']['arrived']} arrived")
        for check in run["checks"]:
            print(f"  {check['result'].upper():<14}{check['id']:<10}{check['found']}")
    book = ledger(None if arguments.no_ledger else entries)
    if arguments.no_ledger:
        for entry in entries:
            book["runs"] = [run for run in book["runs"] if run["session"] != entry["session"]] + [entry]
    if arguments.ledger_only:
        print(f"written: {LEDGER}")
    else:
        page(runs, book, Path(arguments.page))
        print(f"written: {arguments.page}" + ("" if arguments.no_ledger or not entries else f" and {LEDGER}"))
    return 1 if any(check["result"] == "fail" for run in runs for check in run["checks"]) else 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())
