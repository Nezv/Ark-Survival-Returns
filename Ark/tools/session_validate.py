"""Extract real-client evidence for Dashboard.csv without marking whole features verified.

    python tools/session_validate.py <session-folder>

Run session_analyze.py first. Reads its database and summary without modifying them, and writes
validation.json beside the recording. A complete recording can still leave a check unexercised.
Population counts are horizontal, like NaturalPopulations; the changing regional target is not
recorded, so these counts cannot establish exact target compliance. Appearance, audio, multiplayer
and long-session ledger feedback still need their own validations.
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

import duckdb

from session_analyze import locate


def query(con, sql, parameters=None):
    cursor = con.execute(sql, parameters or [])
    names = [column[0] for column in cursor.description]
    return [dict(zip(names, row)) for row in cursor.fetchall()]


def validate(folder: Path) -> dict:
    summary = json.loads((folder / "summary.json").read_text(encoding="utf-8"))
    incidents = [json.loads(line) for line in (folder / "incidents.jsonl").read_text(encoding="utf-8").splitlines()]
    with duckdb.connect(str(folder / "session.duckdb"), read_only=True) as con:
        header = json.loads(con.execute("SELECT value FROM meta WHERE key = 'header'").fetchone()[0])
        config = header.get("config", {})
        radius = float(config.get("spawning.populationRadius", 128))
        full_radius = float(config.get("behavior.fullRadius", 64))
        density = config.get("spawning.populationDensityPerChunk")
        census = query(con, """
            SELECT t.tick, game_s(t.tick) AS game_s, p.e IS NOT NULL AS player_position_recorded,
                count(DISTINCT s.e) FILTER (n.ark AND n.natural AND s.hp > 0 AND NOT coalesce(s.tamed, false)
                    AND n.dim = 'minecraft:overworld') AS loaded_natural,
                CASE WHEN p.e IS NULL THEN NULL ELSE count(DISTINCT s.e) FILTER (n.ark AND n.natural AND s.hp > 0 AND NOT coalesce(s.tamed, false)
                    AND n.dim = 'minecraft:overworld'
                    AND power(s.x - s.px, 2) + power(s.z - s.pz, 2) <= 4096) END AS within_64_horizontal,
                CASE WHEN p.e IS NULL THEN NULL ELSE count(DISTINCT s.e) FILTER (n.ark AND n.natural AND s.hp > 0 AND NOT coalesce(s.tamed, false)
                    AND n.dim = 'minecraft:overworld'
                    AND power(s.x - s.px, 2) + power(s.z - s.pz, 2) <= ?) END AS within_population_radius
            FROM ticks t LEFT JOIN status_x s ON s.tick = t.tick LEFT JOIN entities n ON n.e = s.e
            LEFT JOIN players p ON p.tick = t.tick AND p.e = (SELECT e FROM subject)
            WHERE t.snap OR t.tick = (SELECT min(tick) FROM ticks)
            GROUP BY t.tick, p.e ORDER BY t.tick
        """, [radius * radius])
        species = query(con, """
            SELECT s.species, n.realm, n.predator, count(DISTINCT s.e) AS bodies,
                count(DISTINCT s.e) FILTER (s.player_gap <= 64) AS within_64_body,
                round(min(s.player_gap), 2) AS closest_body
            FROM status_x s JOIN entities n ON n.e = s.e WHERE n.ark
            GROUP BY s.species, n.realm, n.predator ORDER BY bodies DESC
        """)
        origins = query(con, """
            SELECT origin, coalesce(note, '') AS note, count(*) AS bodies,
                min(game_s(tick)) AS first_s, max(game_s(tick)) AS last_s
            FROM entities WHERE ark GROUP BY origin, note ORDER BY bodies DESC
        """)
        tiers = query(con, """
            SELECT tier, realm, count(DISTINCT e) AS bodies, count(*) AS snapshots,
                round(min(player_dist), 2) AS closest_centres,
                round(max(player_dist), 2) AS farthest_centres
            FROM status_x WHERE species IS NOT NULL GROUP BY tier, realm ORDER BY tier, realm
        """)
        nearby_tier = query(con, """
            SELECT count(*) AS snapshots, count(*) FILTER(tier <> 'FULL') AS not_full
            FROM status_x WHERE realm IN ('LAND', 'AMPHIBIOUS') AND age > 40 AND hp > 0
                AND player_dist < ?
        """, [max(0, full_radius - 8)])[0]
        exposure = query(con, """
            SELECT count(*) AS player_ticks, count(*) FILTER(p.hp <= 0) AS dead_ticks,
                count(*) FILTER(p.hp > 0 AND p.hp < 8) AS low_health_ticks,
                round(max(p.x) - min(p.x), 2) AS x_span, round(max(p.z) - min(p.z), 2) AS z_span,
                round(min(p.hp), 2) AS hp_min, round(max(p.hp), 2) AS hp_max
            FROM players p JOIN subject u ON u.e = p.e
        """)[0]
        predator_exposure = query(con, """
            SELECT count(DISTINCT s.e) AS bodies, count(DISTINCT s.tick) AS snapshot_ticks,
                min(game_s(s.tick)) AS first_s, max(game_s(s.tick)) AS last_s
            FROM status_x s JOIN subject u ON true JOIN players p ON p.tick = s.tick AND p.e = u.e
            WHERE s.predator AND s.player_gap <= 32 AND p.hp >= 8
        """)[0]
        player_events = query(con, """
            SELECT game_s(ev.tick) AS game_s, ev.seq, ev.ev, ev.src, ev.dmg, ev.hp,
                source.species AS attacker_species, source.type AS attacker_type
            FROM events ev JOIN subject u ON u.e = ev.e LEFT JOIN entities source ON source.e = ev.by_e
            WHERE ev.ev IN ('damage', 'death', 'respawn') ORDER BY ev.seq
        """)
        senses = query(con, """
            WITH movement AS (
                SELECT e, tick, sqrt(power(x - lag(x) OVER w, 2) + power(y - lag(y) OVER w, 2)
                    + power(z - lag(z) OVER w, 2)) AS moved
                FROM players WINDOW w AS (PARTITION BY e ORDER BY tick))
            SELECT count(*) AS checks, count(*) FILTER(n.moving) AS movement_registered,
                count(*) FILTER(m.moved > 0.02) AS position_moving_checks,
                count(*) FILTER(n.seen) AS sight_detections,
                count(*) FILTER(NOT n.seen AND n.hear > n.dist) AS hearing_detections,
                count(*) FILTER(NOT n.seen AND NOT n.hear > n.dist AND n.smelt) AS scent_detections
            FROM decision_players n JOIN subject u ON u.e = n.player
                LEFT JOIN movement m ON m.e = n.player AND m.tick = n.tick WHERE n.str IS NOT NULL
        """)[0]
        sense_columns = {row[1] for row in con.execute("PRAGMA table_info('decision_players')").fetchall()}
        if "los_eye" in sense_columns:
            senses["alternate_clear"] = con.execute("""
                SELECT count(*) FROM decision_players n JOIN subject u ON u.e = n.player
                WHERE n.str IS NOT NULL AND n.los AND n.los_eye = false
            """).fetchone()[0]
        navigation = query(con, """
            SELECT n.o, count(*) AS requests,
                count(*) FILTER(NOT d.ground AND NOT d.in_water) AS off_ground,
                count(*) FILTER(d.st = 'RETURN_HOME') AS return_home
            FROM decision_nav n JOIN decisions d ON d.seq = n.seq GROUP BY n.o ORDER BY requests DESC
        """)
        combat = query(con, """
            SELECT c.species, CASE WHEN u.e IS NULL THEN 'other prey' ELSE 'player' END AS target,
                ev.r, count(*) AS events FROM events ev JOIN entities c ON c.e = ev.e
                LEFT JOIN subject u ON u.e = ev.at_e WHERE ev.ev = 'strike'
            GROUP BY c.species, target, ev.r ORDER BY c.species, target, ev.r
        """)
        home_passes = con.execute("SELECT count(*) FROM decisions WHERE st = 'RETURN_HOME'").fetchone()[0]
        water = query(con, """
            SELECT c.species, count(DISTINCT d.e) FILTER(d.st = 'SEEK_WATER') AS thirsty_bodies,
                count(*) FILTER(d.st = 'SEEK_WATER') AS seek_passes,
                count(DISTINCT d.e) FILTER(d.st = 'DRINK') AS drinking_bodies,
                count(*) FILTER(d.st = 'DRINK') AS drink_passes
            FROM decisions d JOIN entities c ON c.e = d.e GROUP BY c.species
            HAVING seek_passes > 0 OR drink_passes > 0 ORDER BY seek_passes DESC
        """)
        flee_stalls = query(con, """
            WITH marked AS (
                SELECT d.*, lag(st) OVER w AS previous, lag(tick) OVER w AS previous_tick
                FROM decisions d WINDOW w AS (PARTITION BY e ORDER BY tick, seq)),
            grouped AS (
                SELECT *, sum((st IS DISTINCT FROM previous OR tick - previous_tick > 20)::INT) OVER
                    (PARTITION BY e ORDER BY tick, seq) AS episode FROM marked)
            SELECT d.e, c.species, min(game_s(d.tick)) AS from_s, max(game_s(d.tick)) AS to_s,
                count(*) AS passes, max(d.x) - min(d.x) AS x_span, max(d.z) - min(d.z) AS z_span,
                count(*) FILTER(d.nav_o = 'no_destination') AS no_destination,
                count(*) FILTER(NOT d.ground AND NOT d.in_water) AS off_ground
            FROM grouped d JOIN entities c ON c.e = d.e WHERE d.st = 'FLEE'
            GROUP BY d.e, c.species, d.episode
            HAVING max(d.tick) - min(d.tick) >= 200
                AND power(max(d.x) - min(d.x), 2) + power(max(d.z) - min(d.z), 2) < 0.09
            ORDER BY to_s - from_s DESC
        """)
        flee_paths = query(con, """
            SELECT c.species, n.o, count(*) AS requests FROM decision_nav n
            JOIN decisions d ON d.seq = n.seq JOIN entities c ON c.e = d.e
            WHERE d.br = 'flee' GROUP BY c.species, n.o ORDER BY requests DESC
        """)
        performance = query(con, """
            SELECT round(avg(dur_us) / 1000.0, 2) AS tick_ms_avg,
                round(quantile_cont(dur_us / 1000.0, 0.99), 2) AS tick_ms_p99,
                max(dur_us) / 1000.0 AS tick_ms_max, count(*) FILTER(dur_us > 50000) AS over_50ms,
                round(avg(cap_us) / 1000.0, 3) AS capture_ms_avg,
                round(quantile_cont(cap_us / 1000.0, 0.99), 3) AS capture_ms_p99,
                max(q) AS queue_max FROM ticks
        """)[0]

    loops = [i for i in incidents if i["kind"] == "return_home_loop"]
    chases = [i for i in incidents if i["kind"] == "chase_without_movement" and i.get("target") == "player"]
    deaths = [event for event in player_events if event["ev"] == "death"]
    respawns = [event for event in player_events if event["ev"] == "respawn"]
    usable = bool(summary["integrity"]["complete"] and not summary["integrity"].get("capture_errors"))
    real_client = header.get("ready_by") == "client" or header.get("client") is not None
    population_observed = any(row["loaded_natural"] for row in census)
    player_hits = sum(row["events"] for row in combat if row["target"] == "player" and row["r"] == "hit")
    checks = [
        {"id": "B07", "check": "recording integrity", "result": "observed" if usable else "needs_review"},
        {"id": "P21", "check": "population and server cost", "result": "fixture_only" if not real_client else "measured" if population_observed else "not_exercised",
         "limit": "Regional abundance/target is absent; no exact density assertion. Five minutes do not verify the long ledger cycle."},
        {"id": "P00", "check": "nearby land tier", "result": "needs_review" if nearby_tier["not_full"] else "observed" if nearby_tier["snapshots"] else "not_exercised"},
        {"id": "B08", "check": "player chase progress", "result": "needs_review" if chases else "observed_strike" if player_hits else "not_exercised",
         "limit": "No stall flag alone does not establish successful pursuit; inspect strikes and encounter coverage."},
        {"id": "B08", "check": "return-home loop", "result": "needs_review" if loops else "observed_no_loop" if home_passes else "not_exercised",
         "limit": "A recovery ending does not establish actual arrival home."},
        {"id": "B09", "check": "client movement and hearing", "result": "observed" if senses["hearing_detections"] else "needs_review" if senses["position_moving_checks"] and not senses["movement_registered"] else "not_exercised"},
        {"id": "B09", "check": "partial-cover sight", "result": "observed" if senses.get("alternate_clear", 0) else "not_exercised"},
        {"id": "B09", "check": "water and turns", "result": "measured" if water or summary["turn_in_place"] else "not_exercised",
         "limit": "Compare equivalent active periods; sleeping animals do not exercise water search or fleeing."},
        {"id": "B09", "check": "flee progress", "result": "needs_review" if flee_stalls else "measured" if flee_paths else "not_exercised",
         "limit": "A body that holds FLEE for ten seconds with under 0.3 blocks of horizontal span needs its paths and terrain inspected."},
        {"id": "P02", "check": "death and respawn", "result": "observed" if deaths and respawns else "not_exercised",
         "limit": "Downed attachment, HUD, ally revive and bedroll respawn are not directly captured."},
    ]
    if not usable:
        for check in checks:
            if check["id"] != "B07":
                check["result"] = "capture_errors" if summary["integrity"]["complete"] else "incomplete_recording"
    return {"session": summary["session"], "real_client": real_client,
            "recording_started_after_join_s": header.get("waited_ms", 0) / 1000.0,
            "build": summary["setup"]["build"], "seed": header.get("server", {}).get("seed"),
            "integrity": summary["integrity"], "world": summary["world"], "checks": checks,
            "population": {"radius": radius, "density": density,
                "unscaled_reference": round(density * math.pi * radius * radius / 256) if density is not None else None,
                "actual_target_recorded": False, "census": census, "species": species, "origins": origins},
            "tiers": tiers, "nearby_land_tier": nearby_tier,
            "player": {**exposure, "healthy_predator_exposure": predator_exposure, "events": player_events},
            "senses": senses, "navigation": navigation, "combat": combat, "home_passes": home_passes,
            "player_chase_stalls": chases, "water": water, "flee_stalls": flee_stalls, "flee_paths": flee_paths,
            "turns": summary["turn_in_place"], "performance": performance}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("session", nargs="?", help="analysed session folder; default: newest recording")
    arguments = parser.parse_args()
    folder = locate(arguments.session).parent
    if not (folder / "summary.json").is_file() or not (folder / "session.duckdb").is_file():
        parser.error("run session_analyze.py on this session first")
    result = validate(folder)
    target = folder / "validation.json"
    target.write_text(json.dumps(result, indent=1), encoding="utf-8")
    for check in result["checks"]:
        print(f"{check['id']} {check['result']}: {check['check']}")
    census = result["population"]["census"]
    if census:
        local = [sample["within_population_radius"] for sample in census if sample["player_position_recorded"]]
    else:
        local = []
    if local:
        print(f"natural wildlife within {result['population']['radius']:g} horizontal blocks: "
              f"{local[0]} at start, {local[-1]} at end, {min(local)}-{max(local)} observed")
    print(f"written: {target}")
    return 0 if result["integrity"]["complete"] and not result["integrity"].get("capture_errors") else 2


if __name__ == "__main__":
    raise SystemExit(main())
