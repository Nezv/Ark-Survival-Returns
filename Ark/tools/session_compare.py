"""Puts the behaviour measures of two or more recorded sessions side by side.

    python tools/session_compare.py <baseline-session> <session> [...]

Each session is analysed with the current session_analyze.py first, so recordings made by older builds
are read the same way. A measure a recording cannot answer shows as a dash. The numbers are what one
session happened to exercise: compare sessions played the same way, and read a zero count of events as
"did not happen here", not as a rate.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

from session_analyze import analyse, locate


def one(con, sql: str, default=None):
    try:
        row = con.execute(sql).fetchone()
    except Exception:
        return default
    return default if row is None else (row[0] if len(row) == 1 else row)


def measures(con, summary: dict) -> list[tuple[str, str]]:
    def text(value, form="{}"):
        return "-" if value is None else form.format(value)

    def ratio(part, whole):
        return "-" if part is None or not whole else f"{part} of {whole} ({100 * part / whole:.0f}%)"

    integrity, timing = summary["integrity"], summary["timing"]
    world = (summary["world"] or [{}])[0]
    bands = {band["blocks"]: band for band in summary["detection"]}
    near = [bands[key] for key in ("under 8", "8-16") if key in bands]
    within32 = near + ([bands["16-32"]] if "16-32" in bands else [])
    total = lambda rows, key: sum(row[key] for row in rows)
    every = list(bands.values())
    pego = next((turn for turn in summary["turn_in_place"] if turn["species"] == "pegomastax"), None)
    flee, pursuit = summary["flee"], summary["pursuit"]
    asked = sum(flee["requests"].get(key, 0) for key in ("path", "no_destination", "no_path", "cornered"))
    strikes = {(row["species"], row["result"]): row["n"] for row in pursuit["strikes_at_player"]}
    trampled = pursuit["trampling"]
    return [
        ("recording", f"{'complete' if integrity['complete'] else 'INCOMPLETE'}, {integrity['records']:,} records"),
        ("build", str((summary["setup"]["build"] or {}).get("commit", "-"))[:8]),
        ("day time", f"{world.get('day_from', '-')} to {world.get('day_to', '-')}"),
        ("Ark creatures", str(sum(summary["entities"]["ark_by_species"].values()))),
        ("hearing: checks while you moved / flagged / heard",
         f"{total(every, 'was_moving')} / {total(every, 'flagged_moving')} / {total(every, 'heard')}"),
        ("sight within 32 blocks: no line of sight", ratio(total(within32, "no_line_of_sight"), total(within32, "checks"))),
        ("noticed within 16 blocks", ratio(total(near, "detected"), total(near, "checks"))),
        ("turning on the spot, Pegomastax: share of path time", "-" if pego is None else f"{pego['percent_of_path_time']}%"),
        ("turns of a second or more, all species", str(sum(turn["a_second_or_more"] for turn in summary["turn_in_place"]))),
        ("return-home loops", str(summary["incident_counts"].get("return_home_loop", 0))),
        ("return-home paths refused", text(one(con, """
            SELECT count(*) FROM decision_nav n JOIN decisions d ON d.seq = n.seq
            WHERE d.st = 'RETURN_HOME' AND n.o = 'no_path'"""))),
        ("paths refused while off the ground", text(one(con, """
            SELECT count(*) FROM decision_nav n JOIN decisions d ON d.seq = n.seq WHERE n.o = 'no_path' AND NOT d.ground"""))),
        ("escapes: found a way", ratio(flee["requests"].get("path", 0), asked)),
        ("escapes: nowhere to go", ratio(flee["requests"].get("no_destination", 0), asked)),
        ("animals that stood in FLEE ten seconds or more", f"{flee['stood_ten_seconds_or_more']} (longest {flee['longest_stand_s']:g} s)"),
        ("a run shown by a standing body", ratio(pursuit["of_them_standing"], pursuit["snapshots_showing_a_run"])),
        ("chases without movement", str(summary["incident_counts"].get("chase_without_movement", 0))),
        ("strikes at you: started / hit", f"{sum(n for (_, r), n in strikes.items() if r == 'start')} / "
                                          f"{sum(n for (_, r), n in strikes.items() if r == 'hit')}"),
        ("trees knocked down: logs / leaves", "-" if not trampled else
         f"{sum(row['logs'] or 0 for row in trampled)} / {sum(row['leaves'] or 0 for row in trampled)}"),
        ("thirsty animals that drank", text(one(con, """
            SELECT count(DISTINCT e) FILTER (st = 'DRINK') || ' of ' || count(DISTINCT e) FILTER (st IN ('SEEK_WATER', 'DRINK'))
            FROM decisions"""))),
        ("looking for water: passes, standing still", text(one(con, """
            WITH f AS (SELECT st, sqrt(power(x - lag(x) OVER w, 2) + power(z - lag(z) OVER w, 2)) AS moved
                       FROM decisions WINDOW w AS (PARTITION BY e ORDER BY tick))
            SELECT count(*) || ', ' || count(*) FILTER (moved < 0.25) || ' ('
                   || coalesce(round(100.0 * count(*) FILTER (moved < 0.25) / nullif(count(*), 0))::INT::VARCHAR, '-') || '%)'
            FROM f WHERE st = 'SEEK_WATER'"""))),
        ("turned back at the home range on the way to water", text(one(con, """
            WITH f AS (SELECT st, why, lag(st) OVER w AS previous, lag(water_dest_x) OVER w AS bank
                       FROM decisions WINDOW w AS (PARTITION BY e ORDER BY tick))
            SELECT count(*) FROM f WHERE st = 'RETURN_HOME' AND previous = 'SEEK_WATER' AND bank IS NOT NULL"""))),
        ("tick time: average / longest", f"{timing['tick_ms_avg']} / {timing['tick_ms_max']} ms"),
    ]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("sessions", nargs="+", help="session folders or files, the baseline first")
    arguments = parser.parse_args()
    columns, names = [], []
    for session in arguments.sessions:
        source = locate(session)
        con, summary, _ = analyse(source, source.parent)
        columns.append(measures(con, summary))
        names.append(str(summary["session"]))
        con.close()
    width = max(len(label) for label, _ in columns[0])
    widths = [max(len(names[i]), *(len(value) for _, value in column)) for i, column in enumerate(columns)]
    print(" " * width + "  " + "  ".join(name.ljust(widths[i]) for i, name in enumerate(names)))
    for row, (label, _) in enumerate(columns[0]):
        print(label.ljust(width) + "  " + "  ".join(column[row][1].ljust(widths[i]) for i, column in enumerate(columns)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
