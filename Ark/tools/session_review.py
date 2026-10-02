"""Interactive review page of a creature session recording: review.html beside the recording.

    python tools/session_review.py                  newest recording under run/diagnostics
    python tools/session_review.py <dir-or-file>    a given recording
    ... --dark                                       dark surface and its own palette steps
    ... --creatures 8                                how many of the closest creatures the charts follow
    ... --at 42                                      the replay opens at that game second

Runs tools/session_analyze.py first, so session.duckdb, summary.json and incidents.jsonl are fresh.
The page works offline (Plotly is embedded): a top-down replay with a time slider, the distance and
awareness of the creatures that came closest, what each one was doing and what it made of you, the
blocks around every stalled body, and the incident table. Times are game seconds (ticks / 20).

A replayed position is a recorded sample, not a simulation: between two samples nothing is known.
Requires: pip install duckdb plotly
"""
from __future__ import annotations

import argparse
import html
import json
import math
import sys
from pathlib import Path

import plotly.graph_objects as go
from plotly.subplots import make_subplots

sys.path.insert(0, str(Path(__file__).resolve().parent))
from session_analyze import analyse, locate, query  # noqa: E402

# Validated with the dataviz palette checks: the first three series hues pass every pair in both modes
# (the replay and the strips put any two marks side by side); the six-hue order passes as neighbours (lines).
THEMES = {
    "light": {"surface": "#fcfcfb", "page": "#f9f9f7", "ink": "#0b0b0b", "ink2": "#52514e", "muted": "#898781",
              "grid": "#e1e0d9", "axis": "#c3c2b7", "series": ["#2a78d6", "#eb6834", "#1baf7a", "#eda100", "#e87ba4", "#008300"],
              "ramp": ["#cde2fb", "#3987e5", "#0d366b"], "good": "#0ca30c", "critical": "#d03b3b"},
    "dark": {"surface": "#1a1a19", "page": "#0d0d0d", "ink": "#ffffff", "ink2": "#c3c2b7", "muted": "#898781",
             "grid": "#2c2c2a", "axis": "#383835", "series": ["#3987e5", "#d95926", "#199e70", "#c98500", "#d55181", "#008300"],
             "ramp": ["#0d366b", "#256abf", "#9ec5f4"], "good": "#0ca30c", "critical": "#d03b3b"},
}
FONT = 'system-ui, -apple-system, "Segoe UI", sans-serif'
# Behaviour groups of the replay and the strips: hue says how much attention the animal pays, shape says which kind.
GROUPS = [
    ("resting", "Asleep or resting", "muted", "circle-open", ("SLEEP", "REST")),
    ("calm", "Calm: roaming, grazing, drinking", 0, "circle", ()),
    ("aware", "Aware: alert, investigating, warning", 2, "diamond", ("ALERT", "INVESTIGATE", "THREATEN")),
    ("fleeing", "Fleeing", 2, "triangle-down", ("FLEE",)),
    ("attacking", "Attacking: hunt or defend", 1, "triangle-up", ("HUNT", "DEFEND")),
]
STAGES = [
    ("out", "Not considered: out of range, day routine, game mode", "muted", ("day_routine", "out_of_scan", "mode", "peaceful", "capped", "irrelevant")),
    ("missed", "Considered, not detected", 0, ("undetected", "candidate", "admitted")),
    ("displaced", "Detected, another target chosen", 2, ("displaced", "detected")),
    ("sensed", "Sensed", 1, ("sensed",)),
]


def group_of(state: str | None) -> str:
    for key, _, _, _, states in GROUPS:
        if state in states:
            return key
    return "calm"


def colour(theme: dict, slot) -> str:
    return theme[slot] if isinstance(slot, str) else theme["series"][slot]


def layout(theme: dict, title: str, height: int, **extra) -> dict:
    return dict(
        title=dict(text=title, x=0, xanchor="left", font=dict(size=15, color=theme["ink"])),
        paper_bgcolor=theme["surface"], plot_bgcolor=theme["surface"], height=height,
        font=dict(family=FONT, size=12, color=theme["ink2"]), margin=dict(l=64, r=24, t=64, b=48),
        legend=dict(orientation="h", yanchor="bottom", y=1.0, x=0, font=dict(color=theme["ink2"]), itemsizing="constant"),
        hoverlabel=dict(bgcolor=theme["surface"], bordercolor=theme["axis"], font=dict(color=theme["ink"], family=FONT)),
        **extra)


def axis(theme: dict, title: str = "", **extra) -> dict:
    return dict(title=dict(text=title, font=dict(color=theme["ink2"], size=12)), gridcolor=theme["grid"], gridwidth=1,
                zeroline=False, linecolor=theme["axis"], tickcolor=theme["axis"], tickfont=dict(color=theme["muted"]), **extra)


def label(row: dict) -> str:
    return f"#{row['e']} {row['species'] or (row.get('type') or '?').split(':')[-1]}"


# ------------------------------------------------------------------------------------ charts

def replay(con, theme: dict, opening: float = 0.0) -> go.Figure | None:
    """Top view at every status snapshot: who stood where, doing what, around the recorded player."""
    track = query(con, "SELECT p.tick, p.x, p.z FROM players p JOIN subject u ON u.e = p.e ORDER BY p.tick")
    rows = query(con, """
        SELECT tick, e, x, z, species, type, st, act, why, tier, width, player_gap FROM status_x ORDER BY tick, e""")
    if not track or not rows:
        return None
    ox, oz = round(track[0]["x"]), round(track[0]["z"])
    xs, zs = [p["x"] - ox for p in track], [p["z"] - oz for p in track]
    reach = 72
    x_range = [min(xs) - reach, max(xs) + reach]
    z_range = [max(zs) + reach, min(zs) - reach]
    by_tick: dict[int, list[dict]] = {}
    for row in rows:
        by_tick.setdefault(row["tick"], []).append(row)
    position = {p["tick"]: (p["x"] - ox, p["z"] - oz) for p in track}
    first = min(by_tick)
    ring = [(64 * math.cos(a * math.pi / 24), 64 * math.sin(a * math.pi / 24)) for a in range(49)]

    def frame(tick: int) -> list[go.Scatter]:
        buckets = {key: ([], [], [], []) for key in ["other"] + [g[0] for g in GROUPS]}
        for row in by_tick[tick]:
            x, z = row["x"] - ox, row["z"] - oz
            if not (x_range[0] <= x <= x_range[1] and z_range[1] <= z <= z_range[0]):
                continue
            key = group_of(row["st"]) if row["species"] else "other"
            xs_, zs_, sizes, texts = buckets[key]
            xs_.append(round(x, 2))
            zs_.append(round(z, 2))
            sizes.append(max(7, min(26, round(row["width"] * 4))))
            gap = "" if row["player_gap"] is None else f" · {row['player_gap']:.1f} blocks from you"
            texts.append(f"{label(row)} · {row['st']}/{row['act']} · {row['why']} · {row['tier']}{gap}" if row["species"]
                         else f"{label(row)}{gap}")
        px, pz = position.get(tick, position[min(position, key=lambda t: abs(t - tick))])
        data = [go.Scatter(x=[px + dx for dx, _ in ring], y=[pz + dz for _, dz in ring])]
        for key in ["other"] + [g[0] for g in GROUPS]:
            xs_, zs_, sizes, texts = buckets[key]
            if not xs_:
                # Plotly drops the legend entry of a trace without points; an empty group keeps one invisible point.
                xs_, zs_, sizes, texts = [None], [None], [8], [""]
            data.append(go.Scatter(x=xs_, y=zs_, text=texts, marker=dict(size=sizes)))
        data.append(go.Scatter(x=[px], y=[pz]))
        return data

    ticks = sorted(by_tick)
    shown = min(range(len(ticks)), key=lambda index: abs(ticks[index] - (first + opening * 20)))
    start = frame(ticks[shown])
    figure = go.Figure()
    figure.add_trace(go.Scatter(x=xs, y=zs, mode="lines", line=dict(color=theme["axis"], width=2), name="Your path",
                                hoverinfo="skip"))
    figure.add_trace(go.Scatter(x=start[0].x, y=start[0].y, mode="lines", line=dict(color=theme["muted"], width=1),
                                name="Full-detail radius (64 blocks)", hoverinfo="skip"))
    figure.add_trace(go.Scatter(x=start[1].x, y=start[1].y, text=start[1].text, mode="markers", name="Other animals",
                                marker=dict(size=6, color=theme["axis"]), hovertemplate="%{text}<extra></extra>"))
    for index, (key, name, slot, symbol, _) in enumerate(GROUPS):
        data = start[2 + index]
        figure.add_trace(go.Scatter(
            x=data.x, y=data.y, text=data.text, mode="markers", name=name, hovertemplate="%{text}<extra></extra>",
            showlegend=True, marker=dict(size=data.marker.size, symbol=symbol, color=colour(theme, slot),
                        line=dict(color=colour(theme, slot) if symbol.endswith("open") else theme["surface"], width=2))))
    figure.add_trace(go.Scatter(x=start[-1].x, y=start[-1].y, mode="markers", name="You",
                                marker=dict(size=16, symbol="star", color=theme["ink"], line=dict(color=theme["surface"], width=2)),
                                hovertemplate="you<extra></extra>"))
    names = [f"{(tick - first) / 20:.1f}" for tick in ticks]
    frames = []
    for tick, name in zip(ticks, names):
        data = frame(tick)
        # The marker size list replaces the whole marker of a frame trace, so each frame restates symbol and colour.
        styled = [data[0], data[1]]
        for index, (_, _, slot, symbol, _) in enumerate(GROUPS):
            styled.append(go.Scatter(x=data[2 + index].x, y=data[2 + index].y, text=data[2 + index].text,
                                     marker=dict(size=data[2 + index].marker.size, symbol=symbol, color=colour(theme, slot),
                                                 line=dict(color=colour(theme, slot) if symbol.endswith("open") else theme["surface"], width=2))))
        styled[1] = go.Scatter(x=data[1].x, y=data[1].y, text=data[1].text, marker=dict(size=6, color=theme["axis"]))
        styled.append(data[-1])
        frames.append(go.Frame(name=name, data=styled, traces=list(range(1, 3 + len(GROUPS) + 1))))
    figure.frames = frames
    still = dict(mode="immediate", frame=dict(duration=0, redraw=False), transition=dict(duration=0))

    def play(milliseconds: int) -> dict:
        return dict(frame=dict(duration=milliseconds, redraw=False), transition=dict(duration=0), fromcurrent=True, mode="immediate")

    figure.update_layout(**layout(
        theme, "Replay from above, one frame per status snapshot (every 10 ticks)", 720,
        xaxis=axis(theme, "blocks east of where you started", range=x_range),
        yaxis=axis(theme, "blocks south", range=z_range, scaleanchor="x", scaleratio=1),
        updatemenus=[dict(type="buttons", direction="left", x=0, y=-0.12, xanchor="left", yanchor="top", showactive=False,
                          bgcolor=theme["surface"], bordercolor=theme["axis"], font=dict(color=theme["ink"]),
                          buttons=[dict(label="Play", method="animate", args=[None, play(500)]),
                                   dict(label="Play 4x", method="animate", args=[None, play(125)]),
                                   dict(label="Pause", method="animate", args=[[None], still])])],
        sliders=[dict(active=shown, x=0.2, len=0.8, y=-0.1, yanchor="top", pad=dict(t=0, b=8),
                      currentvalue=dict(prefix="game time ", suffix=" s", font=dict(color=theme["ink"], size=13)),
                      font=dict(color="rgba(0,0,0,0)"), tickcolor=theme["axis"], bordercolor=theme["axis"],
                      bgcolor=theme["grid"], activebgcolor=theme["series"][0],
                      steps=[dict(method="animate", label=name, args=[[name], still]) for name in names])]))
    figure.update_layout(margin=dict(b=110, r=300, t=48),
                         legend=dict(orientation="v", x=1.01, xanchor="left", y=1, yanchor="top", itemsizing="constant"))
    return figure


def followed(con, count: int) -> list[dict]:
    """The Ark creatures that came closest to the recorded player, closest first; colour follows this order."""
    return query(con, f"""
        SELECT e, any_value(species) AS species, min(player_gap) AS gap FROM status_x
        WHERE species IS NOT NULL AND player_gap IS NOT NULL GROUP BY e ORDER BY gap LIMIT {int(count)}""")


def marks(con) -> list[dict]:
    return query(con, "SELECT game_s(tick) AS t, note FROM events WHERE ev = 'mark' ORDER BY tick")


def mark_lines(figure: go.Figure, theme: dict, moments: list[dict]):
    for moment in moments:
        figure.add_vline(x=moment["t"], line=dict(color=theme["ink"], width=1), annotation_text="mark",
                         annotation_font=dict(color=theme["ink2"], size=11), annotation_position="top")


def distances(con, theme: dict, creatures: list[dict], wake, moments) -> go.Figure | None:
    """Gap between each followed creature's body and yours, every tick it was sampled."""
    lead = creatures[:6]
    if not lead:
        return None
    figure = go.Figure()
    for index, creature in enumerate(lead):
        rows = query(con, "SELECT game_s, player_gap FROM motion_x WHERE e = ? AND player_gap IS NOT NULL ORDER BY tick", [creature["e"]])
        if not rows:
            rows = query(con, "SELECT game_s, player_gap FROM status_x WHERE e = ? AND player_gap IS NOT NULL ORDER BY tick", [creature["e"]])
        figure.add_trace(go.Scatter(x=[r["game_s"] for r in rows], y=[round(r["player_gap"], 2) for r in rows], mode="lines",
                                    name=label(creature), line=dict(color=theme["series"][index], width=2),
                                    hovertemplate="%{y:.1f} blocks<extra>" + html.escape(label(creature)) + "</extra>"))
    if wake:
        figure.add_hline(y=wake, line=dict(color=theme["muted"], width=1), annotation_text=f"wake distance {wake:g}",
                         annotation_font=dict(color=theme["ink2"], size=11), annotation_position="top right")
    mark_lines(figure, theme, moments)
    figure.update_layout(**layout(theme, "Distance between each body and yours (blocks between the collision boxes)", 380,
                                  hovermode="x unified", xaxis=axis(theme, "game time (s)"),
                                  yaxis=axis(theme, "blocks", rangemode="tozero")))
    return figure


def awareness(con, theme: dict, creatures: list[dict], moments) -> go.Figure | None:
    """The mind's awareness of whatever it sensed, from every decision pass."""
    figure = go.Figure()
    for index, creature in enumerate(creatures[:6]):
        rows = query(con, "SELECT game_s, aware FROM decisions_x WHERE e = ? AND aware IS NOT NULL ORDER BY tick", [creature["e"]])
        if rows:
            figure.add_trace(go.Scatter(x=[r["game_s"] for r in rows], y=[r["aware"] for r in rows], mode="lines",
                                        name=label(creature), line=dict(color=theme["series"][index], width=2, shape="hv"),
                                        hovertemplate="%{y:.2f}<extra>" + html.escape(label(creature)) + "</extra>"))
    if not figure.data:
        return None
    figure.add_hline(y=0.45, line=dict(color=theme["muted"], width=1), annotation_text="warning builds from 0.45",
                     annotation_font=dict(color=theme["ink2"], size=11), annotation_position="bottom right")
    mark_lines(figure, theme, moments)
    figure.update_layout(**layout(theme, "Awareness at each decision pass (0 unaware, 1 certain)", 300, hovermode="x unified",
                                  xaxis=axis(theme, "game time (s)"), yaxis=axis(theme, "awareness", range=[0, 1.05])))
    return figure


def strip(theme: dict, title: str, legend: list[tuple], points: dict[str, list[tuple]], order: list[str], moments) -> go.Figure:
    figure = go.Figure()
    for key, name, slot, symbol in legend:
        xs, ys, texts = zip(*points[key]) if points.get(key) else ((None,), (None,), ("",))
        figure.add_trace(go.Scatter(x=list(xs), y=list(ys), text=list(texts), mode="markers", name=name, showlegend=True,
                                    marker=dict(size=9, symbol=symbol, color=colour(theme, slot),
                                                line=dict(color=colour(theme, slot) if symbol.endswith("open") else theme["surface"], width=1)),
                                    hovertemplate="%{text}<extra></extra>"))
    mark_lines(figure, theme, moments)
    figure.update_layout(**layout(theme, title, 110 + 28 * len(order), xaxis=axis(theme, "game time (s)"),
                                  yaxis=axis(theme, "", type="category", categoryorder="array",
                                             categoryarray=list(reversed(order)), showgrid=False)))
    return figure


def behaviour(con, theme: dict, creatures: list[dict], moments) -> go.Figure | None:
    if not creatures:
        return None
    names = {c["e"]: label(c) for c in creatures}
    ids = ", ".join(str(e) for e in names)
    points: dict[str, list[tuple]] = {}
    for r in query(con, f"SELECT e, game_s, st, act, why, tier, player_gap FROM status_x WHERE e IN ({ids}) AND st IS NOT NULL ORDER BY tick"):
        points.setdefault(group_of(r["st"]), []).append(
            (r["game_s"], names[r["e"]], f"{names[r['e']]} · {r['game_s']:.1f} s · {r['st']}/{r['act']} · {r['why']} · {r['tier']} · "
                                         f"{r['player_gap']:.1f} blocks away"))
    return strip(theme, "What each creature was doing (state at every status snapshot)",
                 [(key, name, slot, symbol) for key, name, slot, symbol, _ in GROUPS], points, list(names.values()), moments)


def funnel(con, theme: dict, creatures: list[dict], moments) -> go.Figure | None:
    if not creatures:
        return None
    names = {c["e"]: label(c) for c in creatures}
    ids = ", ".join(str(e) for e in names)
    lookup = {stage: key for key, _, _, stages in STAGES for stage in stages}
    points: dict[str, list[tuple]] = {}
    present = []
    for r in query(con, f"""
            SELECT n.e, game_s(n.tick) AS game_s, n.stage, n.body, d.st, d.why, d.br, d.nav_o
            FROM decision_players n JOIN decisions d ON d.seq = n.seq JOIN subject u ON u.e = n.player
            WHERE n.e IN ({ids}) ORDER BY n.tick"""):
        if names[r["e"]] not in present:
            present.append(names[r["e"]])
        route = f" · path {r['nav_o']}" if r["nav_o"] else ""
        points.setdefault(lookup.get(r["stage"], "out"), []).append(
            (r["game_s"], names[r["e"]], f"{names[r['e']]} · {r['game_s']:.1f} s · you: {r['stage']} at {r['body']:.1f} blocks · "
                                         f"{r['st']} ({r['why']}) · {r['br']}{route}"))
    if not present:
        return None
    order = [name for name in names.values() if name in present]
    symbols = {"out": "circle-open", "missed": "circle", "displaced": "diamond", "sensed": "triangle-up"}
    return strip(theme, "What each creature made of you at every decision pass",
                 [(key, name, slot, symbols[key]) for key, name, slot, _ in STAGES], points, order, moments)


def terrain(con, theme: dict) -> go.Figure | None:
    """The blocks around the first stalls: how many layers of each column, over the body's height, block movement."""
    captures = query(con, """
        SELECT t.seq, t.tick, t.e, t.why, t.box, t.ox, t.oz, t.sx, t.sz, t.clip, t.trunc, n.species, n.type, game_s(t.tick) AS game_s
        FROM terrain t JOIN entities n ON n.e = t.e ORDER BY (n.species IS NULL), t.tick LIMIT 6""")
    if not captures:
        return None
    columns = min(3, len(captures))
    rows = math.ceil(len(captures) / columns)
    figure = make_subplots(rows=rows, cols=columns, horizontal_spacing=0.08, vertical_spacing=0.16, subplot_titles=[
        f"{label(c)} · {c['why']} · {c['game_s']:.1f} s" for c in captures])
    low, mid, high = theme["ramp"]
    scale = [[0, theme["surface"]], [0.001, low], [0.5, mid], [1, high]]
    for index, capture in enumerate(captures):
        box = capture["box"]
        feet, top = math.floor(box[1]), box[4]
        layers = max(1, math.ceil(top) - feet)
        counts = [[0] * capture["sx"] for _ in range(capture["sz"])]
        names: dict[tuple, set] = {}
        for b in query(con, "SELECT x, y, z, block, kind FROM terrain_blocks WHERE seq = ? AND kind IN (1, 2)", [capture["seq"]]):
            if feet <= b["y"] < top:
                dx, dz = b["x"] - capture["ox"], b["z"] - capture["oz"]
                counts[dz][dx] += 100 / layers
                names.setdefault((dz, dx), set()).add(b["block"].split(":")[-1])
        text = [[", ".join(sorted(names.get((dz, dx), ()))) or "clear" for dx in range(capture["sx"])] for dz in range(capture["sz"])]
        row, column = index // columns + 1, index % columns + 1
        centre_x, centre_z = (box[0] + box[3]) / 2, (box[2] + box[5]) / 2
        figure.add_trace(go.Heatmap(
            z=counts, text=text, x=[capture["ox"] + dx + 0.5 - centre_x for dx in range(capture["sx"])],
            y=[capture["oz"] + dz + 0.5 - centre_z for dz in range(capture["sz"])], colorscale=scale, zmin=0, zmax=100,
            xgap=2, ygap=2, showscale=index == 0,
            colorbar=dict(title=dict(text="body height blocked", font=dict(color=theme["ink2"])), ticksuffix="%",
                          tickfont=dict(color=theme["muted"]), outlinewidth=0, len=0.6),
            hovertemplate="%{x:.1f} east, %{y:.1f} south of the body's centre<br>%{z:.0f}% of its height blocked<br>%{text}<extra></extra>"),
            row=row, col=column)
        figure.add_shape(type="rect", x0=box[0] - centre_x, x1=box[3] - centre_x, y0=box[2] - centre_z, y1=box[5] - centre_z,
                         line=dict(color=theme["ink"], width=2), row=row, col=column)
        figure.update_xaxes(row=row, col=column, showgrid=False, zeroline=False, tickfont=dict(color=theme["muted"]),
                            linecolor=theme["axis"], title=dict(text="blocks east", font=dict(color=theme["ink2"], size=11)))
        figure.update_yaxes(row=row, col=column, showgrid=False, zeroline=False, tickfont=dict(color=theme["muted"]),
                            linecolor=theme["axis"], autorange="reversed", scaleanchor=f"x{index + 1 if index else ''}", scaleratio=1,
                            title=dict(text="blocks south", font=dict(color=theme["ink2"], size=11)))
    figure.update_layout(**layout(theme, "Blocks around a stalled body (outline: the collision box; darker: more of its height blocked)",
                                  120 + 330 * rows))
    figure.update_annotations(font=dict(color=theme["ink2"], size=12))
    return figure


# -------------------------------------------------------------------------------------- page

def cell(value) -> str:
    if isinstance(value, float):
        value = f"{value:.2f}".rstrip("0").rstrip(".")
    if isinstance(value, (dict, list)):
        value = json.dumps(value, default=str)
    return html.escape(str(value))


def table(headers: list[str], rows: list[list]) -> str:
    head = "".join(f"<th>{html.escape(h)}</th>" for h in headers)
    body = "".join("<tr>" + "".join(f"<td>{cell(v)}</td>" for v in row) + "</tr>" for row in rows)
    return f"<div class='scroll'><table><thead><tr>{head}</tr></thead><tbody>{body}</tbody></table></div>"


def page(theme: dict, summary: dict, incidents: list[dict], figures: list[tuple[str, go.Figure | None]], creatures: list[dict]) -> str:
    integrity, timing = summary["integrity"], summary["timing"]
    whole = integrity["complete"]
    badge = ("&#10004; Complete: no record missing" if whole else
             f"&#9888; Incomplete: {integrity['missing_records']} records missing, stop reason {html.escape(str(integrity['stop_reason']))}")
    tiles = [("Records", f"{integrity['records']:,}"), ("Game time", f"{timing['ticks'] / 20:.0f} s"),
             ("Unpaused real time", f"{timing['unpaused_s'] or 0:.0f} s"), ("Ticks per second", f"{timing['ticks_per_second']}"),
             ("Ark creatures", f"{sum(summary['entities']['ark_by_species'].values())}"),
             ("Incidents to check", f"{sum(1 for i in incidents if not i['normal'])}")]
    parts = []
    first = True
    for note, figure in figures:
        if figure is None:
            continue
        parts.append(f"<section><p class='note'>{note}</p>"
                     + figure.to_html(full_html=False, include_plotlyjs="inline" if first else False, auto_play=False,
                                      config=dict(displaylogo=False, responsive=True)) + "</section>")
        first = False
    hidden = ("kind", "normal", "score", "e", "who", "t0", "t1", "tick0", "tick1", "gap_min", "seq0", "seq1", "marked")
    incident_rows = [[i["score"], i["kind"], f"#{i['e']} {i['who']}" if i["e"] >= 0 else "-", f"{i['t0']:.1f} - {i['t1']:.1f}",
                      i.get("gap_min", ""), {k: v for k, v in i.items() if k not in hidden}]
                     for i in incidents if not i["normal"]][:60]
    normal_rows = [[i["kind"], f"#{i['e']} {i['who']}", f"{i['t0']:.1f} - {i['t1']:.1f}", i.get("gap_min", ""),
                    {k: v for k, v in i.items() if k not in hidden}] for i in incidents if i["normal"]][:40]
    creature_rows = [[c["e"], c["species"], c["snapshots"], c["gap_min"], c["gap_avg"], ", ".join(sorted(s for s in c["states"] if s))]
                     for c in summary["nearest_creatures"]]
    setup = summary["setup"]
    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Session {html.escape(str(summary['session']))}</title>
<style>
  body {{ margin: 0; background: {theme['page']}; color: {theme['ink']}; font-family: {FONT}; font-size: 14px; }}
  main {{ max-width: 1280px; margin: 0 auto; padding: 24px 16px 64px; }}
  h1 {{ font-size: 22px; margin: 0 0 4px; }} h2 {{ font-size: 16px; margin: 32px 0 8px; }}
  .sub, .note {{ color: {theme['ink2']}; }} .note {{ margin: 0 0 8px; max-width: 90ch; }}
  .badge {{ display: inline-block; margin: 12px 0; padding: 6px 12px; border-radius: 6px; background: {theme['surface']};
            border: 1px solid {theme['good'] if whole else theme['critical']}; font-weight: 600; }}
  .tiles {{ display: flex; flex-wrap: wrap; gap: 12px; margin: 8px 0 16px; }}
  .tile {{ background: {theme['surface']}; border: 1px solid {theme['grid']}; border-radius: 8px; padding: 12px 16px; min-width: 132px; }}
  .tile b {{ display: block; font-size: 24px; font-weight: 600; }} .tile span {{ color: {theme['ink2']}; }}
  section {{ background: {theme['surface']}; border: 1px solid {theme['grid']}; border-radius: 8px; padding: 12px; margin: 16px 0; }}
  .scroll {{ overflow-x: auto; }} table {{ border-collapse: collapse; width: 100%; font-variant-numeric: tabular-nums; }}
  th, td {{ text-align: left; padding: 6px 10px; border-bottom: 1px solid {theme['grid']}; vertical-align: top; }}
  th {{ color: {theme['ink2']}; font-weight: 600; }} td:last-child {{ color: {theme['ink2']}; word-break: break-word; }}
</style></head><body><main>
<h1>Creature session {html.escape(str(summary['session']))}</h1>
<div class="sub">started {html.escape(str(summary['started']))} · build {html.escape(json.dumps(setup['build']))} ·
  wake distance {setup['wake_distance']} · carnivore day sleep {setup['carnivore_day_sleep']} ·
  client {html.escape(json.dumps(setup['client']) if setup['client'] else 'not reported')}</div>
<div class="badge">{badge}</div>
<div class="tiles">{''.join(f"<div class='tile'><b>{html.escape(v)}</b><span>{html.escape(k)}</span></div>" for k, v in tiles)}</div>
{''.join(parts)}
<h2>Incidents to check, most pressing first</h2>
<p class="note">Each row is a hypothesis with its evidence. Read the creature's window before calling it a cause:
  <code>python tools/session_analyze.py --window ID FROM TO</code>.</p>
<section>{table(['score', 'kind', 'creature', 'game s', 'closest (blocks)', 'evidence'], incident_rows)}</section>
<h2>Ordinary responses, for reference</h2>
<section>{table(['kind', 'creature', 'game s', 'closest (blocks)', 'evidence'], normal_rows)}</section>
<h2>Creatures that came closest</h2>
<section>{table(['id', 'species', 'snapshots', 'closest', 'average', 'states seen'], creature_rows)}</section>
</main></body></html>"""


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("session", nargs="?", help="session folder or file; default: the newest under run/diagnostics")
    parser.add_argument("--dark", action="store_true", help="dark surface")
    parser.add_argument("--creatures", type=int, default=10, help="creatures followed in the strips (the line charts take the first six)")
    parser.add_argument("--at", type=float, default=0.0, help="game second the replay opens at")
    arguments = parser.parse_args()
    source = locate(arguments.session)
    con, summary, incidents = analyse(source, source.parent)
    theme = THEMES["dark" if arguments.dark else "light"]
    creatures = followed(con, arguments.creatures)
    moments = marks(con)
    wake = summary["setup"]["wake_distance"]
    figures = [
        ("Drag the slider or press Play. Marker size follows the body width; the thin ring is 64 blocks around you, "
         "the radius inside which a creature runs its full behaviour. Hover a marker for its state, action and the rule that chose it.",
         replay(con, theme, arguments.at)),
        ("The six creatures that came closest. A creature that wants you and keeps its distance shows as a flat line above zero.",
         distances(con, theme, creatures, wake, moments)),
        ("Awareness rises while something is sensed and decays when it is not; the warning timer only runs from 0.45.",
         awareness(con, theme, creatures, moments)),
        ("One row per creature, closest first. Hover a mark for the state, the action, the rule and the tier.",
         behaviour(con, theme, creatures, moments)),
        ("How far you got through each creature's candidate funnel. Not considered is the day routine, game mode or range; "
         "considered and not detected means sight, hearing and scent all failed.",
         funnel(con, theme, creatures, moments)),
        ("Top view of the blocks with collision between the feet and the top of each stalled body, from the first captures.",
         terrain(con, theme)),
    ]
    target = source.parent / "review.html"
    target.write_text(page(theme, summary, incidents, figures, creatures), encoding="utf-8")
    con.close()
    print(f"written: {target} ({target.stat().st_size / 1e6:.1f} MB)")


if __name__ == "__main__":
    main()
