"""Reads a playthrough run (F43) and writes its report: report.html and summary.json in the run's folder, and one entry
in run/diagnostics/playthrough/ledger.json so runs can be compared after a balance change.

    python tools/playthrough_report.py [run folder]     (default: the newest run)

Everything is worked out here, after the run: the run itself only wrote down what happened (events.jsonl).
"""
from __future__ import annotations

import html
import json
import statistics
import sys
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "run" / "diagnostics" / "playthrough"

# A crude worth per item for rating supply crates: keyword -> points, the first that matches; 1 otherwise.
WORTH = [("saddle", 12), ("totem", 15), ("pendant", 15), ("crown", 15), ("amulet", 12), ("ring", 10), ("schematic", 10),
         ("bronze", 6), ("keratin", 4), ("ingot", 4), ("diamond", 10), ("emerald", 8), ("tranquilizer", 3),
         ("bandage", 3), ("narcotics", 3), ("vitamins", 3), ("arrow", 1), ("meat", 1)]


def worth(item: str) -> int:
    name = item.split(":")[-1]
    return next((points for word, points in WORTH if word in name), 1)


def load(folder: Path) -> list[dict]:
    lines = (folder / "events.jsonl").read_text(encoding="utf-8").splitlines()
    return [json.loads(line) for line in lines if line.strip()]


def esc(value) -> str:
    return html.escape(str(value))


def table(headers: list[str], rows: list[list], cls: str = "") -> str:
    head = "".join(f"<th>{esc(h)}</th>" for h in headers)
    body = "".join("<tr>" + "".join(cell if isinstance(cell, str) and cell.startswith("<td") else f"<td>{esc(cell)}</td>"
                                     for cell in row) + "</tr>" for row in rows)
    return f'<table class="{cls}"><thead><tr>{head}</tr></thead><tbody>{body}</tbody></table>' if rows else "<p>None.</p>"


def matrix(events: list[dict], value, flag) -> tuple[list[str], list[list]]:
    """Species (rows, in run order) by label (columns)."""
    labels, species, cells = [], [], {}
    for e in events:
        if e["label"] not in labels:
            labels.append(e["label"])
        if e["species"] not in species:
            species.append(e["species"])
        cells[e["species"], e["label"]] = e
    rows = []
    for s in species:
        row = [s]
        for label in labels:
            e = cells.get((s, label))
            row.append("<td></td>" if e is None else f'<td class="{"bad" if flag(e) else ""}">{esc(value(e))}</td>')
        rows.append(row)
    return ["species", *labels], rows


def summarise(events: list[dict]) -> dict:
    by = defaultdict(list)
    for e in events:
        by[e["type"]].append(e)
    start, end = events[0], events[-1]
    stages = []
    marks = [e for e in events if e["type"] == "stage"]
    for i, mark in enumerate(marks):
        until = marks[i + 1]["wall"] if i + 1 < len(marks) else end["wall"]
        inside = [e for e in events if e["stage"] == mark["stage"]]
        stages.append({"stage": mark["stage"], "minutes": round((until - mark["wall"]) / 60000, 1),
                       "blocked": sum(1 for e in inside if e["type"] == "blocked"),
                       "lifted_xp": sum(e["supplied_xp"] for e in inside if e["type"] == "level_lift"),
                       "refused": sum(1 for e in inside if e["type"] == "craft" and e.get("result") != "crafted"),
                       "errors": sum(1 for e in inside if e["type"] in ("step_error", "step_timeout"))})
    kills = defaultdict(list)
    for e in by["kill_streak"]:
        if "seconds_to_kill" in e:
            kills[e["label"]].append(e["seconds_to_kill"])
    taken = defaultdict(list)
    for e in by["damage_test"]:
        # Only measured or projected tests: one cut short (window, timeout, no blow) says nothing about three lives.
        if e["end"] in ("taken", "projected") and "blows_to_three_lives" in e:
            taken[e["label"]].append(e["blows_to_three_lives"])
    lifts = by["level_lift"]
    return {
        "routine": start.get("routine"), "minutes": round((end["wall"] - start["wall"]) / 60000, 1), "end": end.get("why"),
        "stages": stages,
        "kill_seconds_median": {k: round(statistics.median(v), 1) for k, v in kills.items()},
        "blows_to_three_lives_median": {k: statistics.median(v) for k, v in taken.items()},
        "blocked": len(by["blocked"]),
        "blocked_goals": sorted({e["goal"] for e in by["blocked"]}),
        "recipes_obtained": len([e for e in by["obtained"] if e["stage"] == "RECIPES"]),
        "recipes_blocked": len([e for e in by["blocked"] if e["stage"] == "RECIPES"]),
        "errors": len(by["step_error"]) + len(by["step_timeout"]),
        "level_end": end.get("level"), "earned_xp": end.get("earned_xp"), "supplied_xp": end.get("supplied_xp"),
        "level_lifts": len(lifts), "hunt_fills": len(by["hunt_fill"]),
        "filled_xp": sum(e.get("xp", 0) for e in by["hunt_fill"]), "provenance_failures": len(provenance(events)),
        "downs": len(by["player_down"]) + len(by["player_dead"]),
        "tech_missed": sorted(set(by["tech"][-1].get("open", [])) - set(by["tech"][-1].get("future", []))) if by["tech"] else [],
    }


def provenance(events: list[dict]) -> list[list]:
    """Each craft, process or cook at a station must name a spot where the player put that station down, from an item
    the bags held (crafted, or earned); anything else is listed."""
    placed, made, bad = {}, set(), []
    for e in events:
        if e["type"] == "craft" and e.get("result") == "crafted":
            made.add(e["item"])
        elif e["type"] in ("obtained", "withdrawn", "hunt_fill"):
            made.add(e["item"])
        elif e["type"] == "placed" and e.get("result") == "placed":
            placed[e["at"]] = e
            if e.get("item") not in made:
                bad.append([e["stage"], "placed", e["block"], e["at"], "its item was never crafted or earned"])
        elif e["type"] in ("craft", "process", "cook") and "at" in e:
            if e["at"] not in placed:
                bad.append([e["stage"], e["type"], e.get("item", ""), e["at"], "no station the player placed stands there"])
    return bad


def report(folder: Path) -> Path:
    events = load(folder)
    by = defaultdict(list)
    for e in events:
        by[e["type"]].append(e)
    summary = summarise(events)
    (folder / "summary.json").write_text(json.dumps(summary, indent=2, default=dict), encoding="utf-8")
    parts = [f"<h1>Playthrough {esc(summary['routine'])}: {esc(folder.name)}</h1>",
             f"<p>{summary['minutes']} minutes, ended: {esc(summary['end'])}; nothing given, {summary['blocked']} goals blocked; "
             f"{summary['errors']} step errors or timeouts.</p>"]

    parts.append("<h2>Stages</h2>" + table(["stage", "minutes", "goals blocked", "XP lifted", "refused crafts", "errors"],
                                           [[s["stage"], s["minutes"], s["blocked"], s["lifted_xp"], s["refused"], s["errors"]] for s in summary["stages"]]))
    parts.append(f"<p>Level at the end {esc(summary['level_end'])}: {esc(summary['earned_xp'])} XP earned by play, "
                 f"{esc(summary['supplied_xp'])} lifted to meet gates or filled from validated carcasses "
                 f"({summary['hunt_fills']} fills, {esc(summary['filled_xp'])} XP). Supplied by the run, by design: the yard's "
                 "blocks, the hunted and measured creatures (each kill validated once, then filled at the measured rate), "
                 "the test wall and screen blocks, and the lifted levels.</p>")

    play = [e for e in events if e["stage"].startswith("R") or e["stage"] in ("RECIPES", "WEAPONS", "ARMOUR")]
    parts.append("<h2>What the economy gave</h2>")
    parts.append(table(["stage", "how", "item", "wanted", "tries / kills", "made", "short", "tool", "game s"],
                       [[e["stage"], e["type"], e["item"], e.get("wanted", e.get("count", "")),
                         e.get("tries", e.get("kills", e.get("kills_equivalent", ""))), e.get("made", ""),
                         e.get("short", ""), e.get("tool", e.get("weapon", e.get("station", ""))), round(e.get("ticks", 0) / 20, 1)]
                        for e in play if e["type"] in ("harvest", "hunt", "hunt_fill", "process")]))
    parts.append("<h2>The XP curve: gates the play had not reached</h2><p>Each row is a craft whose level the XP earned "
                 "so far did not reach; the run lifted the player instead of grinding. Few, small rows mean the curve fits the play.</p>")
    parts.append(table(["stage", "for", "from level", "gate", "XP earned so far", "XP lifted"],
                       [[e["stage"], e["step"], e["from_level"], e["wanted_level"], e["earned_xp"], e["supplied_xp"]] for e in by["level_lift"]]))
    parts.append("<h2>Provenance: every station used was earned and placed</h2>")
    parts.append(table(["stage", "event", "item", "at", "problem"], provenance(events), "warn"))
    parts.append("<h2>The player's own state</h2>")
    parts.append(table(["stage", "event", "cause / item", "food"],
                       [[e["stage"], e["type"], e.get("cause", e.get("item", "")), e.get("food", e.get("food_after", ""))]
                        for e in events if e["type"] in ("player_down", "player_dead", "hungry", "ate", "no_crate")]))
    parts.append("<p>" + esc(", ".join(f"{k} {v}" for k, v in Counter(e.get("end", "") for e in by["ate"]).items())) + "</p>")
    parts.append("<h2>Gaps: goals that could not be earned</h2>")
    parts.append(table(["stage", "goal", "stuck on", "count", "why"], [[e["stage"], e["goal"], e["item"], e["count"], e["why"]] for e in by["blocked"]], "warn"))
    parts.append("<h2>The camp</h2>")
    parts.append(table(["stage", "station", "at"], [[e["stage"], e["block"], e["at"]] for e in by["placed"]]))
    parts.append("<h2>Crafts during play</h2>")
    parts.append(table(["stage", "item", "station", "level", "result", "player level when gated"],
                       [[e["stage"], e["item"], e.get("station", ""), e.get("level", ""), e["result"], e.get("gate", "")]
                        for e in play if e["type"] == "craft"]))

    parts.append("<h2>Kill streak: game seconds to kill (hits)</h2><p>*: projected from the hits of the sample. "
                 "Red: no hit landed, or over a minute.</p>")
    kills = [e for e in by["kill_streak"] if not e["label"].startswith("weapon sweep")]
    headers, rows = matrix(kills, lambda e: f"{e['seconds_to_kill']:.1f}{'' if e['end'] == 'dead' else '*'} ({e['hits_to_kill']})"
                           if "seconds_to_kill" in e else e["end"], lambda e: "seconds_to_kill" not in e or e["seconds_to_kill"] > 60)
    parts.append(table(headers, rows, "matrix"))

    parts.append("<h2>Damage test: blows to take three times the player's health (damage per blow after armour, armour's cut)</h2>"
                 "<p>Projected from the sampled blows. Red: cut short (no_blow: it never struck; window or timeout).</p>")
    headers, rows = matrix(by["damage_test"], lambda e: f"{e['blows_to_three_lives']} ({e['taken'] / max(1, e['blows']):.1f}, -{100 * (1 - e['taken'] / e['raw']):.0f}%)"
                           if e.get("blows_to_three_lives") and e["raw"] else e["end"], lambda e: e["end"] not in ("taken", "projected"))
    parts.append(table(headers, rows, "matrix"))

    parts.append("<h2>Weapons against a level 1 Triceratops</h2>")
    wields = {e["item"]: e for e in by["wield"] if e["stage"] == "WEAPONS"}
    parts.append(table(["weapon", "damage", "attack speed", "missing", "end", "hits", "game s", "durability left"],
                       [[e["weapon"], wields.get(e["weapon"], {}).get("damage", ""), wields.get(e["weapon"], {}).get("speed", ""),
                         wields.get(e["weapon"], {}).get("missing", False), e["end"], e.get("hits_to_kill", e["hits"]),
                         round(e.get("seconds_to_kill", e["ticks"] / 20), 1), e.get("durability_left", "")]
                        for e in by["kill_streak"] if e["label"].startswith("weapon sweep")]))

    parts.append("<h2>Bows and crossbows against a level 1 Triceratops</h2>")
    parts.append(table(["weapon", "ammo", "end", "shots", "dealt", "shots to kill", "torpor", "knocked out"],
                       [[e["weapon"].split(":")[-1], e["ammo"].split(":")[-1], e["end"], e["shots"], round(e.get("dealt", 0), 1),
                         e.get("shots_to_kill", ""), f"{e.get('torpor', 0):.0f}/{e.get('torpor_max', 0):.0f}", e.get("knocked_out", "")] for e in by["ranged"]]))
    parts.append("<h2>Tames</h2>")
    parts.append(table(["species", "method", "end", "game s", "sedating blows", "doses", "meals", "progress", "XP"],
                       [[e["species"], e.get("method", ""), e["end"], round(e["ticks"] / 20, 1), e["sedating_blows"], e["doses"], e.get("meals", ""),
                         e.get("progress", ""), e["xp"]] for e in by["tame"]]))
    parts.append("<h2>A tame's life, the bed and the torch</h2>")
    parts.append(table(["stage", "what", "with", "end", "game s", "note"],
                       [[e["stage"], e.get("what", e["type"]), e.get("species", e.get("bed", "")).split(":")[-1], e["end"],
                         round(e.get("ticks", 0) / 20, 1),
                         f"hunger {e['hunger']:.0f}, fed at {e['fed_at_hunger']:.0f}" if "hunger" in e else
                         f"respawn set: {e['respawn_set']}" if "respawn_set" in e else e.get("order", "")]
                        for e in events if e["type"] in ("companion", "sleep", "torch")]))
    parts.append("<h2>Creature against creature (level 15, the next bigger body)</h2>")
    parts.append(table(["a", "b", "winner", "end", "game s", "a health left", "b health left", "a dealt", "b dealt"],
                       [[e["a"], e["b"], e.get("winner", ""), e["end"], round(e["ticks"] / 20, 1), f"{e.get('a_left', 0):.0%}", f"{e.get('b_left', 0):.0%}",
                         round(e.get("a_dealt", 0)), round(e.get("b_dealt", 0))] for e in by["fight"]]))
    parts.append("<h2>Armour sets</h2>")
    parts.append(table(["stage", "pieces", "armour", "toughness", "missing"],
                       [[e["stage"], ", ".join(p.split(":")[-1] for p in e["pieces"]), e["armor"], e["toughness"],
                         ", ".join(p.split(":")[-1] for p in e.get("missing", []))] for e in by["equip"]]))

    parts.append("<h2>Supply drop tiers</h2>")
    rows = []
    for tier in ("white", "green", "blue", "purple"):
        rolls = [e for e in by["loot"] if e["tier"] == tier]
        if not rolls:
            continue
        items = Counter()
        for e in rolls:
            items.update(e["items"])
        values = [sum(worth(i) * c for i, c in e["items"].items()) for e in rolls]
        stacks = [len(e["items"]) for e in rolls]
        rows.append([tier, len(rolls), round(statistics.mean(stacks), 1), round(statistics.mean(values), 1), min(values), max(values),
                     ", ".join(f"{i.split(':')[-1]} {c}" for i, c in items.most_common(8))])
    parts.append(table(["tier", "rolls", "kinds per crate", "worth mean", "worth min", "worth max", "most common (total count)"], rows))
    parts.append(table(["real drop", "landed after (game s)", "crate", "screen", "items", "worth"],
                       [[e["tier"], round(e["landed_after"] / 20, 1), e["crate_tier"], e["menu"],
                         ", ".join(f"{i.split(':')[-1]} {c}" for i, c in e["items"].items()), sum(worth(i) * c for i, c in e["items"].items())]
                        for e in by["drop"]]))

    parts.append("<h2>Coverage</h2>")
    recipe_goals = [e for e in events if e["stage"] == "RECIPES" and e["type"] in ("obtained", "blocked")]
    blocks = [e for e in by["block_drops"]]
    wall = by["block_wall"][0] if by["block_wall"] else {"blocks": 0, "did_not_stay": []}
    screens = by["screen_open"]
    shots = {e["request"][5:]: e["screen"] for e in by["client"] if e["request"].startswith("shot:")}
    coverage = [
        ["recipes", f"{sum(1 for e in recipe_goals if e['type'] == 'obtained')} earned of {len(recipe_goals)}",
         ", ".join(e["goal"].split(":")[-1] for e in recipe_goals if e["type"] == "blocked")],
        ["blocks", f"{wall['blocks']} placed, {len(wall['did_not_stay'])} did not stay, {sum(1 for e in blocks if not e['gained'])} dropped nothing",
         ", ".join(x.split(":")[-1] for x in wall["did_not_stay"]) + " | nothing: " + ", ".join(e["block"].split(":")[-1] for e in blocks if not e["gained"])],
        ["screens", f"{sum(1 for e in screens if shots.get('screen_' + e['block']))} of {len(screens)} showed a screen",
         ", ".join(e["block"] for e in screens if not shots.get("screen_" + e["block"]))],
        ["armour sets", f"{len(by['equip'])} worn", ""],
        ["weapons", f"{sum(1 for e in by['kill_streak'] if e['label'].startswith('weapon sweep'))} swung", ""],
        ["creatures", f"{len({e['species'] for e in by['kill_streak']})} killed, {len({e['species'] for e in by['damage_test'] if e['blows']})} struck back",
         ", ".join(sorted({e["species"] for e in by["damage_test"] if not e["blows"]}))],
        ["tames", f"{len(by['tame'])} tried", " | ".join(f"{e['species']}: {e.get('end', e.get('status'))}" for e in by["tame"])],
        ["supply drops", f"{len(by['drop'])} of 4 real drops landed and opened", ""],
    ]
    parts.append(table(["what", "result", "missing or failed"], coverage))
    tech = by["tech"][-1] if by["tech"] else None
    if tech and "completed" in tech:
        future = set(tech.get("future", []))
        missed = [n for n in tech["open"] if n not in future]
        parts.append(f"<h2>Tech tree at the end</h2><p>{len(tech['completed'])} completed: {esc(', '.join(tech['completed']))}</p>"
                     f"<p class='{'bad' if missed else ''}'>Not completed though the game has them ({len(missed)}): {esc(', '.join(missed)) or 'none'}</p>"
                     f"<p>Not in the game yet ({len(future)}): {esc(', '.join(sorted(future)))}</p>")

    problems = [e for e in events if e["type"] in ("step_error", "step_timeout")]
    parts.append("<h2>Step errors and timeouts</h2>" + table(["stage", "step", "type", "error"],
                                                             [[e["stage"], e.get("inner", e["step"]), e["type"], e.get("error", "")] for e in problems], "warn"))
    log = folder / "errors.log"
    if log.is_file() and log.read_text(encoding="utf-8").strip():
        parts.append("<h2>Errors in the game log</h2><pre>" + esc(log.read_text(encoding="utf-8")[:20000]) + "</pre>")

    pictures = sorted((folder / "screenshots").glob("*.png")) if (folder / "screenshots").is_dir() else []
    parts.append("<h2>Pictures</h2><div class='shots'>" + "".join(
        f"<figure><a href='screenshots/{esc(p.name)}'><img src='screenshots/{esc(p.name)}' loading='lazy'></a>"
        f"<figcaption>{esc(p.stem)} {esc(shots.get(p.stem, ''))}</figcaption></figure>" for p in pictures) + "</div>")

    style = ("body{font:13px system-ui,sans-serif;margin:24px;background:#1b1a17;color:#e8e2d4}h2{margin-top:28px;color:#d8c08a}"
             "table{border-collapse:collapse;margin:6px 0}td,th{border:1px solid #3d3a33;padding:3px 7px;text-align:left;vertical-align:top}"
             "th{background:#2a2823}.bad{background:#5a2420}.warn td{background:#3a3020}.matrix td{white-space:nowrap}"
             ".shots{display:flex;flex-wrap:wrap;gap:8px}figure{margin:0;width:240px}img{width:240px}figcaption{font-size:11px}pre{white-space:pre-wrap}")
    out = folder / "report.html"
    out.write_text(f"<!DOCTYPE html><html><head><meta charset='utf-8'><title>Playthrough {esc(folder.name)}</title>"
                   f"<style>{style}</style></head><body>{''.join(parts)}</body></html>", encoding="utf-8")

    if folder.resolve().parent != ROOT.resolve():
        return out  # a run kept elsewhere is reported but not entered in the ledger
    ledger = ROOT / "ledger.json"
    entries = json.loads(ledger.read_text(encoding="utf-8")) if ledger.is_file() else []
    entries = [e for e in entries if e.get("run") != folder.name] + [{"run": folder.name, **summary}]
    ledger.write_text(json.dumps(entries, indent=2, default=dict), encoding="utf-8")
    return out


def main() -> int:
    if len(sys.argv) > 1:
        folder = Path(sys.argv[1])
    else:
        runs = sorted(p for p in ROOT.iterdir() if (p / "events.jsonl").is_file()) if ROOT.is_dir() else []
        if not runs:
            sys.exit(f"no run in {ROOT}")
        folder = runs[-1]
    print(report(folder))
    return 0


if __name__ == "__main__":
    sys.exit(main())
