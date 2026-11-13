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
                       "given": sum(1 for e in inside if e["type"] == "given" and e.get("why") not in ("recipe_sweep", "block_sweep")),
                       "forced": sum(1 for e in inside if e["type"] == "craft" and e.get("forced")),
                       "refused": sum(1 for e in inside if e["type"] == "craft" and e.get("result") != "crafted"),
                       "errors": sum(1 for e in inside if e["type"] in ("step_error", "step_timeout"))})
    kills = defaultdict(list)
    for e in by["kill_streak"]:
        if e["end"] == "dead":
            kills[e["label"]].append(e["ticks"] / 20)
    taken = defaultdict(list)
    for e in by["damage_test"]:
        if e["blows"]:
            taken[e["label"]].append(e["blows"])
    return {
        "routine": start.get("routine"), "minutes": round((end["wall"] - start["wall"]) / 60000, 1), "end": end.get("why"),
        "stages": stages,
        "kill_seconds_median": {k: round(statistics.median(v), 1) for k, v in kills.items()},
        "blows_to_three_lives_median": {k: statistics.median(v) for k, v in taken.items()},
        "given": len([e for e in by["given"] if e.get("why") not in ("recipe_sweep", "block_sweep")]),
        "recipes": Counter(e["result"] for e in by["craft"] if e["stage"] == "RECIPES"),
        "errors": len(by["step_error"]) + len(by["step_timeout"]),
    }


def report(folder: Path) -> Path:
    events = load(folder)
    by = defaultdict(list)
    for e in events:
        by[e["type"]].append(e)
    summary = summarise(events)
    (folder / "summary.json").write_text(json.dumps(summary, indent=2, default=dict), encoding="utf-8")
    parts = [f"<h1>Playthrough {esc(summary['routine'])}: {esc(folder.name)}</h1>",
             f"<p>{summary['minutes']} minutes, ended: {esc(summary['end'])}; {summary['given']} items given during play; "
             f"{summary['errors']} step errors or timeouts.</p>"]

    parts.append("<h2>Stages</h2>" + table(["stage", "minutes", "given", "forced level", "refused crafts", "errors"],
                                           [[s["stage"], s["minutes"], s["given"], s["forced"], s["refused"], s["errors"]] for s in summary["stages"]]))

    play = [e for e in events if e["stage"].startswith("R")]
    parts.append("<h2>What the economy gave</h2>")
    parts.append(table(["stage", "how", "item", "wanted", "tries / kills", "made", "short", "tool", "game s"],
                       [[e["stage"], e["type"], e["item"], e["wanted"], e.get("tries", e.get("kills", "")), e.get("made", ""),
                         e.get("short", ""), e.get("tool", e.get("weapon", e.get("station", ""))), round(e.get("ticks", 0) / 20, 1)]
                        for e in play if e["type"] in ("harvest", "hunt", "process")]))
    parts.append("<h2>Gaps: given instead of earned</h2>")
    parts.append(table(["stage", "item", "count", "why"], [[e["stage"], e["item"], e["count"], e["why"]] for e in play if e["type"] == "given"], "warn"))
    parts.append("<h2>Crafts during play</h2>")
    parts.append(table(["stage", "item", "station", "level", "result", "level gate forced from"],
                       [[e["stage"], e["item"], e.get("station", ""), e.get("level", ""), e["result"], e.get("gate", "")]
                        for e in play if e["type"] == "craft"]))

    parts.append("<h2>Kill streak: game seconds to kill (hits)</h2><p>Red: not dead at the cap (health left).</p>")
    kills = [e for e in by["kill_streak"] if not e["label"].startswith("weapon sweep")]
    headers, rows = matrix(kills, lambda e: f"{e['ticks'] / 20:.1f} ({e['hits']})" if e["end"] == "dead"
                           else f"{e['end']} {e.get('health_left', 0):.0f}/{e.get('creature_health', 0):.0f}", lambda e: e["end"] != "dead")
    parts.append(table(headers, rows, "matrix"))

    parts.append("<h2>Damage test: blows to take three times the player's health (damage per blow after armour)</h2>"
                 "<p>Red: the creature never reached three lives (no_blow: it never struck).</p>")
    headers, rows = matrix(by["damage_test"], lambda e: f"{e['blows']} ({e['taken'] / max(1, e['blows']):.1f}, -{100 * (1 - e['taken'] / e['raw']):.0f}%)"
                           if e["blows"] and e["raw"] else e["end"], lambda e: e["end"] != "taken")
    parts.append(table(headers, rows, "matrix"))

    parts.append("<h2>Weapons against a level 1 Triceratops</h2>")
    wields = {e["item"]: e for e in by["wield"] if e["stage"] == "WEAPONS"}
    parts.append(table(["weapon", "damage", "attack speed", "given", "end", "hits", "game s", "durability left"],
                       [[e["weapon"], wields.get(e["weapon"], {}).get("damage", ""), wields.get(e["weapon"], {}).get("speed", ""),
                         wields.get(e["weapon"], {}).get("given", False), e["end"], e["hits"], round(e["ticks"] / 20, 1), e.get("durability_left", "")]
                        for e in by["kill_streak"] if e["label"].startswith("weapon sweep")]))

    parts.append("<h2>Armour sets</h2>")
    parts.append(table(["stage", "pieces", "armour", "toughness", "given"],
                       [[e["stage"], ", ".join(p.split(":")[-1] for p in e["pieces"]), e["armor"], e["toughness"], len(e["given"])] for e in by["equip"]]))

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
    recipe = Counter(e["result"] for e in by["craft"] if e["stage"] == "RECIPES")
    blocks = [e for e in by["block_drops"]]
    wall = by["block_wall"][0] if by["block_wall"] else {"blocks": 0, "did_not_stay": []}
    screens = by["screen_open"]
    shots = {e["request"][5:]: e["screen"] for e in by["client"] if e["request"].startswith("shot:")}
    coverage = [
        ["recipes", f"{recipe.get('crafted', 0)} crafted of {sum(recipe.values())}",
         ", ".join(e["item"].split(":")[-1] + " (" + e["result"] + ")" for e in by["craft"] if e["stage"] == "RECIPES" and e["result"] != "crafted")],
        ["blocks", f"{wall['blocks']} placed, {len(wall['did_not_stay'])} did not stay, {sum(1 for e in blocks if not e['gained'])} dropped nothing",
         ", ".join(x.split(":")[-1] for x in wall["did_not_stay"]) + " | nothing: " + ", ".join(e["block"].split(":")[-1] for e in blocks if not e["gained"])],
        ["screens", f"{sum(1 for e in screens if shots.get('screen_' + e['block']))} of {len(screens)} showed a screen",
         ", ".join(e["block"] for e in screens if not shots.get("screen_" + e["block"]))],
        ["armour sets", f"{len(by['equip'])} worn", ""],
        ["weapons", f"{sum(1 for e in by['kill_streak'] if e['label'].startswith('weapon sweep'))} swung", ""],
        ["creatures", f"{len({e['species'] for e in by['kill_streak']})} killed, {len({e['species'] for e in by['damage_test'] if e['blows']})} struck back",
         ", ".join(sorted({e["species"] for e in by["damage_test"] if not e["blows"]}))],
        ["tames", f"{len(by['tame'])} tried", " | ".join(f"{e['species']}: {e['status']}" for e in by["tame"])],
        ["supply drops", f"{len(by['drop'])} of 4 real drops landed and opened", ""],
    ]
    parts.append(table(["what", "result", "missing or failed"], coverage))
    tech = by["tech"][-1] if by["tech"] else None
    if tech and "completed" in tech:
        parts.append(f"<h2>Tech tree at the end</h2><p>{len(tech['completed'])} completed: {esc(', '.join(tech['completed']))}</p>"
                     f"<p>Open: {esc(', '.join(tech['open']))}</p>")

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
