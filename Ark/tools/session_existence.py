"""What became of the wildlife register during a recording: who was on it, who lived on without a body, who came back.

    python tools/session_existence.py [<session>] [--from MARK] [--to MARK] [--radius BLOCKS] [--place X Z] [--bench FOLDER]

A creature whose chunk is not loaded has no body, so the body records of a session (ent, leave, return) say nothing
of it. The recorder therefore also writes the register (feature/recorder/Session.java): a roll call of every living
record at the start, at each mark and at the end, a count every ten seconds, and every end, silent birth, round of
rules beyond the loaded land, body brought back to its record and saved body kept out. This script reads those rows
straight from session.jsonl or session.jsonl.gz and answers one question: of the animals around a place at one roll
call, what had become of each by a later one.

The place is where the recorded player stood at the first roll call (--from, default 'depart' when the recording has
that mark, else 'start') and its animals are the records with a body there; --place X Z names another place, and then
every record within the radius counts, body or not (land the player had already left). The later roll call is --to
(default 'settled', else 'end'). An animal persisted when the
same record is alive at both; it lived on without a body when its record had none at a roll call between them. An
animal that is on neither the later roll call nor among the ends on record has vanished, and one that ended as
'removed' was taken out of the world without dying: both fail the verdict, as does a record left without its body
where the chunks are loaded again.

The marks 'depart', 'far', 'turn', 'home' and 'settled' are set by the way out and back of the frame benchmark
(tools/session_bench.py --path return --record); with them the report also gives the server's tick and the cost of
the population budget's pass for each leg, and with --bench the frame rates of the same legs.
Writes existence.json beside the recording. Exit code 1 when the verdict fails.
"""
from __future__ import annotations

import argparse
import json
import math
import statistics
import sys
from collections import Counter
from pathlib import Path

from session_analyze import locate, read

SHOWN, BODY, SILENT, CLAIMED, LOADED = 1, 2, 4, 8, 16
LEGS = (("arrive", "start", "depart"), ("out", "depart", "far"), ("away", "far", "turn"), ("back", "turn", "home"),
        ("home", "home", "settled"))


def load(path: Path) -> dict:
    """The rows of the recording this report needs, in the order they were written."""
    data = {"header": {}, "rolls": [], "lives": [], "entities": {}, "moves": [], "marks": {}, "ticks": [], "world": [],
            "census": [], "subject": None, "positions": {}, "integrity": {}}
    for row in read(path, data["integrity"]):
        kind = row.get("t")
        if kind == "header":
            data["header"] = row
        elif kind == "roll":
            columns = data["header"].get("legend", {}).get("roll") or ["uuid", "species", "pack", "lvl", "x", "y", "z", "hunger",
                                                                       "appeared", "seen", "flags"]
            data["rolls"].append({"at": row["at"], "tick": row["k"], "day": row.get("day"), "ends": row.get("ends"),
                                  "records": {cells[0]: dict(zip(columns, cells)) for cells in row.get("rows", [])}})
        elif kind == "life":
            data["lives"].append(row)
        elif kind == "reg":
            data["census"].append(row)
        elif kind == "ent":
            data["entities"][row["e"]] = row
            if row.get("subject"):
                data["subject"] = row["e"]
        elif kind == "ev" and row.get("ev") in ("leave", "return"):
            data["moves"].append(row)
        elif kind == "ev" and row.get("ev") == "mark":
            data["marks"].setdefault(row.get("note"), row)
        elif kind == "tick":
            data["ticks"].append(row)
        elif kind == "w" and row.get("dim") == "minecraft:overworld":
            data["world"].append(row)
        elif kind == "p" and row.get("e") == data["subject"]:
            data["positions"][row["k"]] = row["p"]
    return data


def roll(data: dict, name: str) -> dict | None:
    return next((r for r in data["rolls"] if r["at"] == name), None)


def near(record: dict, place: tuple[float, float], radius: float) -> bool:
    return math.hypot(record["x"] + 0.5 - place[0], record["z"] + 0.5 - place[1]) <= radius


def quantile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int(len(ordered) * fraction))] if ordered else 0.0


def legs(data: dict) -> list[dict]:
    """The server's tick, the budget's pass and the loaded world for each leg of the way out and back, or for the whole recording."""
    ticks = {name: r["tick"] for r in data["rolls"] for name in [r["at"]]}
    spans = [(name, ticks[first], ticks[last]) for name, first, last in LEGS if first in ticks and last in ticks]
    if not spans and "start" in ticks and "end" in ticks:
        spans = [("all", ticks["start"], ticks["end"])]
    out = []
    for name, begin, end in spans:
        rows = [t for t in data["ticks"] if begin <= t["k"] < end]
        if not rows:
            continue
        long_ms = [t["dur_us"] / 1000 for t in rows]
        passes = [l for l in data["lives"] if l.get("ev") == "pass" and begin <= l["k"] < end]
        welcomes = [l for l in data["lives"] if l.get("ev") == "welcomes" and begin <= l["k"] < end]
        chunks = [w["chunks"] for w in data["world"] if begin <= w["k"] < end]
        out.append({"leg": name, "seconds": round((end - begin) / 20, 1), "tick_ms": round(statistics.fmean(long_ms), 2),
                    "tick_ms_p95": round(quantile(long_ms, 0.95), 2), "tick_ms_max": round(max(long_ms), 1),
                    "ticks_over_50ms": sum(value > 50 for value in long_ms),
                    "mobs": round(statistics.fmean(t["mobs"] for t in rows)),
                    "chunks": round(statistics.fmean(chunks)) if chunks else None,
                    "pass_ms": round(statistics.fmean(p["us"] for p in passes) / 1000, 2) if passes else None,
                    "pass_ms_max": round(max(p["us"] for p in passes) / 1000, 2) if passes else None,
                    "records": max((p["records"] for p in passes), default=None),
                    "welcomed": sum(w["bodies"] for w in welcomes),
                    "welcome_ms_max": round(max(w["us"] for w in welcomes) / 1000, 2) if welcomes else None,
                    "recorder_ms": round(statistics.fmean(t["cap_us"] for t in rows) / 1000, 3)})
    return out


def analyse(path: Path, first: str | None = None, last: str | None = None, radius: float | None = None,
            where: tuple[float, float] | None = None) -> dict:
    data = load(path)
    header, rolls = data["header"], data["rolls"]
    if not rolls:
        return {"session": header.get("session"), "recorded": False,
                "why": "the recording has no roll call of the register: it was made by a build before the recorder followed it"}
    names = [r["at"] for r in rolls]
    first = first or ("depart" if "depart" in names else "start")
    last = last or ("settled" if "settled" in names else "end")
    before, after = roll(data, first), roll(data, last)
    if before is None or after is None:
        raise SystemExit(f"the recording has the roll calls {', '.join(names)}; --from {first} or --to {last} is none of them")
    config = header.get("config", {})
    radius = radius or float(config.get("spawning.populationRadius", 128))
    mark = data["marks"].get(first)
    at = mark["p"] if mark and "p" in mark else data["positions"].get(before["tick"]) or next(iter(data["positions"].values()), [0, 0, 0])
    place = where or (at[0], at[2])
    between = [r for r in rolls if before["tick"] <= r["tick"] <= after["tick"]]
    ends = {life["u"]: life for life in data["lives"] if life.get("ev") == "end" and before["tick"] <= life["k"] <= after["tick"]}
    born = {life["u"]: life for life in data["lives"] if life.get("ev") == "born"}
    came = {life["u"]: life for life in data["lives"] if life.get("ev") == "arrived"}
    rounds = [life for life in data["lives"] if life.get("ev") == "round" and before["tick"] <= life["k"] <= after["tick"]]
    welcomes = {life["u"]: life for life in data["lives"] if life.get("ev") == "welcome" and before["tick"] <= life["k"] <= after["tick"]}
    refused = [life for life in data["lives"] if life.get("ev") == "refused" and before["tick"] <= life["k"] <= after["tick"]]
    kept_out = {life["u"] for life in refused}
    by_uuid = {entity["uuid"]: entity for entity in data["entities"].values() if "uuid" in entity}
    cohort = {uuid: record for uuid, record in before["records"].items()
              if (where or record["flags"] & LOADED) and near(record, place, radius)}

    fates, animals = Counter(), []
    for uuid, record in cohort.items():
        later, end = after["records"].get(uuid), ends.get(uuid)
        # Without a body at a roll call in between: the chunk was not loaded, the animal was its record alone.
        bodiless = [r["at"] for r in between if uuid in r["records"] and not r["records"][uuid]["flags"] & LOADED]
        changed = any(r["records"][uuid]["flags"] & SILENT for r in between if uuid in r["records"])
        if later is not None and later["flags"] & LOADED:
            fate = "back" if bodiless else "stayed"
        elif later is not None:
            # A record without its body is a failure only where the chunks are loaded: around the player at that roll call.
            fate = "no_body" if near(later, _where(data, after, place), _reach(header)) else "alive_as_record"
        elif end is not None:
            fate = "removed" if end.get("cause") == "removed" else "tamed" if end.get("cause") == "tamed" \
                else "died_as_record" if end.get("silent") else "died_loaded"
        else:
            fate = "vanished"
        fates[fate] += 1
        entry = {"uuid": uuid, "species": record["species"], "pack": record["pack"], "level": record["lvl"], "fate": fate,
                 "without_body_at": bodiless, "record_changed": changed}
        if later is not None:
            entry["moved"] = round(math.hypot(later["x"] - record["x"], later["z"] - record["z"]), 1)
            entry["hunger"] = [round(record["hunger"], 2), round(later["hunger"], 2)]
        if end is not None:
            entry["end"] = {"cause": end.get("cause"), "silent": bool(end.get("silent")), "lived_days": round(end.get("lived", 0), 2)}
        if uuid in welcomes:
            entry["welcomed"] = {"moved": round(welcomes[uuid].get("moved", 0), 1),
                                 "hunger": [round(welcomes[uuid].get("hunger0", 0), 2), round(welcomes[uuid].get("hunger", 0), 2)]}
        animals.append(entry)

    # Who is at the place afterwards and was not there before.
    newcomers = Counter()
    for uuid, record in after["records"].items():
        if uuid in cohort or not record["flags"] & LOADED or not near(record, place, radius):
            continue
        entity = by_uuid.get(uuid, {})
        newcomers["born_as_record" if uuid in born else "arrived_as_record" if uuid in came else "walked_in" if uuid in before["records"]
                  else "arrived" if entity.get("note") == "budget_spawn" else "loaded_later" if entity.get("origin") in ("load", "present")
                  else "unexplained"] += 1

    lived = [a for a in animals if a["without_body_at"]]
    packs_before = {a["pack"] for a in animals}
    packs_after = {a["pack"] for a in animals if a["fate"] in ("back", "stayed")}
    left = [m for m in data["moves"] if m["ev"] == "leave" and before["tick"] <= m["k"] <= after["tick"]
            and data["entities"].get(m["e"], {}).get("uuid") in cohort]
    returned = [m for m in data["moves"] if m["ev"] == "return" and before["tick"] <= m["k"] <= after["tick"]
                and data["entities"].get(m["e"], {}).get("uuid") in cohort]
    failed = fates["vanished"] + fates["removed"] + fates["no_body"] + newcomers["unexplained"]
    census = data["census"]
    result = {
        "session": header.get("session"), "recorded": True, "complete": not data["integrity"],
        "build": header.get("build", {}),
        "from": first, "to": last, "roll_calls": [{"at": r["at"], "game_s": round((r["tick"] - rolls[0]["tick"]) / 20, 1),
                                                  "records": len(r["records"]),
                                                  "with_body": sum(1 for x in r["records"].values() if x["flags"] & LOADED)} for r in rolls],
        "place": {"x": round(place[0], 1), "z": round(place[1], 1), "radius": radius},
        "config": {key: config.get(f"spawning.{key}") for key in ("populationModel", "silentLife", "silentRules", "wildCalendarSpeed",
                                                                 "silentRoundDays", "silentLivedInRounds",
                                                                 "wildLifespanDays", "populationRefillDays", "wildGroupsPerPlayer")},
        "view_distance": header.get("server", {}).get("view_distance"),
        "cohort": {"animals": len(cohort), "groups": len(packs_before), "species": dict(Counter(a["species"] for a in animals).most_common())},
        "fates": dict(fates),
        "ends": dict(Counter(a["end"]["cause"] for a in animals if "end" in a).most_common()),
        "lived_without_body": {"animals": len(lived), "record_changed": sum(a["record_changed"] for a in lived),
                               "rounds": len(rounds), "regions": len({r.get("biome", "") + str(r.get("cells")) for r in rounds}),
                               "round_records": sum(r.get("records", 0) for r in rounds), "round_ended": sum(r.get("ended", 0) for r in rounds),
                               "round_born": sum(r.get("born", 0) for r in rounds),
                               "round_arrived": sum(r.get("arrived", 0) for r in rounds),
                               "calendar_days": round(after["day"] - before["day"], 2) if after.get("day") is not None and before.get("day") is not None else None},
        "returned": {"groups_with_a_member_back": len(packs_after), "welcomed": sum(1 for a in animals if "welcomed" in a),
                     "moved_to_record": sum(1 for a in animals if a.get("welcomed", {}).get("moved", 0) > 0.5),
                     "mean_shift_blocks": round(statistics.fmean([a["moved"] for a in lived if "moved" in a]), 1) if any("moved" in a for a in lived) else 0,
                     "bodies_left": len(left), "bodies_returned": len(returned),
                     "saved_bodies_kept_out": len(kept_out & set(cohort)), "saved_bodies_kept_out_anywhere": len(refused)},
        "newcomers": dict(newcomers),
        "register": {"first": _count(census[0]) if census else None, "last": _count(census[-1]) if census else None},
        "legs": legs(data),
        "animals": animals,
    }
    result["verdict"] = {
        "persisted": fates["back"] + fates["stayed"] + fates["alive_as_record"], "of": len(cohort),
        "ended_on_record": fates["died_as_record"] + fates["died_loaded"] + fates["tamed"],
        "failed": failed, "exercised": bool(lived),
        "result": "failed" if failed else "persisted" if lived else "not_exercised",
        "why": ("; ".join(f"{count} {name.replace('_', ' ')}" for name, count in
                          (("vanished", fates["vanished"]), ("removed", fates["removed"]), ("no_body", fates["no_body"]),
                           ("unexplained newcomers", newcomers["unexplained"])) if count)
                if failed else "every animal of the place is alive at the later roll call or has an end on record"
                if lived else "no animal of the place was without a body between the two roll calls")}
    return result


def _reach(header: dict) -> float:
    """Blocks around the player the server keeps loaded for certain: its view distance less a chunk at the edge."""
    return (max(2, int(header.get("server", {}).get("view_distance") or 8)) - 1) * 16.0


def _where(data: dict, roll_call: dict, fallback: tuple[float, float]) -> tuple[float, float]:
    """Where the recorded player was at a roll call: at its mark, or at the last tick recorded up to it."""
    mark = data["marks"].get(roll_call["at"])
    if mark and "p" in mark:
        return mark["p"][0], mark["p"][2]
    earlier = [tick for tick in data["positions"] if tick <= roll_call["tick"]]
    at = data["positions"][max(earlier)] if earlier else None
    return (at[0], at[2]) if at else fallback


def _count(row: dict) -> dict:
    keys = ("living", "loaded", "unborn", "silent", "claimed", "shown", "ends", "tiles", "biome", "cells", "groups", "quota", "due", "stayed", "lived")
    return {key: row[key] for key in keys if key in row}


def frames(folder: Path) -> list[dict]:
    """The frame rates of the same legs, from the benchmark's own summary of its folder."""
    import session_bench
    summary = session_bench.summarise(folder)
    return [{"leg": name, **{key: row.get(key) for key in ("seconds", "fps", "fps_low_1pc", "ms_p99", "ms_max", "frames_over_50ms",
                                                          "creatures", "gpu_load", "server_tick_ms", "limit", "valid")}}
            for name, row in (summary or {}).get("phases", {}).items()]


def report(result: dict):
    if not result.get("recorded"):
        print(f"existence: {result['why']}")
        return
    verdict, cohort, fates, lived, back = result["verdict"], result["cohort"], result["fates"], result["lived_without_body"], result["returned"]
    print(f"\nexistence of {result['session']}: roll calls " + ", ".join(
        f"{r['at']} ({r['records']} records, {r['with_body']} with a body)" for r in result["roll_calls"]))
    print(f"the place: {result['place']['x']:.0f} {result['place']['z']:.0f}, {result['place']['radius']:.0f} blocks around it; "
          f"at '{result['from']}' {cohort['animals']} animals in {cohort['groups']} groups: "
          + ", ".join(f"{count} {name}" for name, count in cohort["species"].items()))
    print(f"without a body in between: {lived['animals']} of them (record changed for {lived['record_changed']}); rounds of the rules "
          f"beyond the loaded land: {lived['rounds']} in {lived['regions']} region(s), over {lived['round_records']} records, "
          f"{lived['round_ended']} ended, {lived['round_born']} born")
    print(f"at '{result['to']}': " + ", ".join(f"{count} {name.replace('_', ' ')}" for name, count in sorted(fates.items(), key=lambda item: -item[1]))
          + f"; {back['groups_with_a_member_back']} of {cohort['groups']} groups have a member there"
          + ("; ends by cause: " + ", ".join(f"{count} {cause}" for cause, count in result["ends"].items()) if result["ends"] else ""))
    print(f"bodies: {back['bodies_left']} unloaded and {back['bodies_returned']} read back from the chunks; {back['welcomed']} brought to "
          f"their record ({back['moved_to_record']} moved, the records shifted {back['mean_shift_blocks']} blocks on average); "
          f"{back['saved_bodies_kept_out']} saved bodies of the dead kept out ({back['saved_bodies_kept_out_anywhere']} in all the land)")
    if result["newcomers"]:
        print("new at the place: " + ", ".join(f"{count} {name.replace('_', ' ')}" for name, count in result["newcomers"].items()))
    for name, count in (("first", result["register"]["first"]), ("last", result["register"]["last"])):
        if count:
            region = (f"; region {count['biome']} of {count['cells']} chunks, groups {count['groups']} of quota {count['quota']}"
                      if "biome" in count else "")
            print(f"register at the {name} count: {count['living']} living, {count['loaded']} with a body, {count['unborn']} never had one, "
                  f"{count['ends']} ends on record{region}")
    if result["legs"]:
        print(f"\n{'leg':<8}{'s':>6}{'tick ms':>9}{'p95':>7}{'max':>7}{'>50ms':>7}{'mobs':>6}{'chunks':>8}{'pass ms':>9}{'max':>7}{'records':>9}"
              f"{'welcomed':>10}{'max ms':>8}{'recorder ms':>13}")
        for leg in result["legs"]:
            print(f"{leg['leg']:<8}{leg['seconds']:>6.0f}{leg['tick_ms']:>9}{leg['tick_ms_p95']:>7}{leg['tick_ms_max']:>7}{leg['ticks_over_50ms']:>7}"
                  f"{leg['mobs']:>6}{leg['chunks'] if leg['chunks'] is not None else '-':>8}{leg['pass_ms'] if leg['pass_ms'] is not None else '-':>9}"
                  f"{leg['pass_ms_max'] if leg['pass_ms_max'] is not None else '-':>7}{leg['records'] if leg['records'] is not None else '-':>9}"
                  f"{leg['welcomed']:>10}{leg['welcome_ms_max'] if leg['welcome_ms_max'] is not None else '-':>8}{leg['recorder_ms']:>13}")
    if result.get("frames"):
        print(f"\n{'leg':<8}{'s':>6}{'fps':>7}{'1% low':>8}{'p99 ms':>8}{'max ms':>8}{'>50ms':>7}{'drawn':>7}{'card %':>8}  limit")
        for leg in result["frames"]:
            print(f"{leg['leg']:<8}{leg['seconds']:>6.0f}{leg['fps']:>7}{leg['fps_low_1pc']:>8}{leg['ms_p99']:>8}{leg['ms_max']:>8}"
                  f"{leg['frames_over_50ms']:>7}{leg['creatures'] if leg['creatures'] is not None else '-':>7}{leg.get('gpu_load') or '-':>8}"
                  f"  {leg['limit']}{'' if leg['valid'] else '  INVALID'}")
    print(f"\nverdict: {verdict['result'].upper()}: {verdict['persisted']} of {verdict['of']} alive at '{result['to']}', "
          f"{verdict['ended_on_record']} with an end on record; {verdict['why']}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("session", nargs="?", help="session folder or file; default: the newest recording")
    parser.add_argument("--from", dest="first", help="the roll call the place and its animals are taken from (default: depart, else start)")
    parser.add_argument("--to", dest="last", help="the roll call they are looked for at (default: settled, else end)")
    parser.add_argument("--radius", type=float, help="blocks around the player that make the place (default: spawning.populationRadius)")
    parser.add_argument("--place", type=float, nargs=2, metavar=("X", "Z"),
                        help="the place by its coordinates; every record within the radius counts, with a body or not")
    parser.add_argument("--bench", help="the benchmark folder of the same launch, for the frame rates of the legs")
    arguments = parser.parse_args()
    path = locate(arguments.session)
    result = analyse(path, arguments.first, arguments.last, arguments.radius, tuple(arguments.place) if arguments.place else None)
    if arguments.bench and result.get("recorded"):
        result["frames"] = frames(Path(arguments.bench))
    (path.parent / "existence.json").write_text(json.dumps(result, indent=1), encoding="utf-8")
    report(result)
    print(f"written: {path.parent / 'existence.json'}")
    return 1 if result.get("recorded") and result["verdict"]["result"] == "failed" else 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())
