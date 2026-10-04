"""Verdicts of a behaviour scenario recording (feature/recorder/BehaviorScenario.java).

    python tools/session_trials.py                  newest recording under run/diagnostics
    python tools/session_trials.py <dir-or-file>    a given recording

Reads the scenario's result events from session.jsonl or session.jsonl.gz, prints one line per trial (pass or
what was wrong, how it ended) with the states every animal went through and how its needs moved, and writes
the same as trials.json beside the recording. Exit code 1 when a trial failed or none was recorded.
"""
from __future__ import annotations

import gzip
import json
import sys
from pathlib import Path

from session_analyze import locate


def results(path: Path) -> list[dict]:
    opener = gzip.open if path.suffix == ".gz" else open
    found = []
    with opener(path, "rt", encoding="utf-8") as handle:
        for line in handle:
            if '"test_behavior"' not in line:
                continue
            try:
                record = json.loads(line)
            except ValueError:
                continue
            if record.get("ev") == "test_behavior" and record.get("r") == "result":
                found.append(json.loads(record["note"]))
    return found


def main() -> int:
    source = locate(sys.argv[1] if len(sys.argv) > 1 else None)
    trials = results(source)
    if not trials:
        print(f"no behaviour trial results in {source}")
        return 1
    failed = [trial for trial in trials if not trial.get("pass")]
    for trial in trials:
        verdict = "pass" if trial.get("pass") else "FAIL: " + str(trial.get("wrong"))
        print(f"{trial['trial']:<22} {verdict}  ({trial.get('ended')}, {trial.get('seconds')} s, observer struck {trial.get('observer_struck', 0)}x)")
        for actor in trial.get("actors", []):
            needs = " ".join(f"{name} {actor.get(name + '0')}>{actor.get(name)}" for name in ("hunger", "thirst", "fatigue"))
            print(f"    {actor['role']:<17} {actor.get('states') or '-'}")
            print(f"    {'':<17} moved {actor.get('moved')}, gap {actor.get('gap0')}>{actor.get('gap')} (min {actor.get('gap_min')}), "
                  f"hp {actor.get('hp0')}>{actor.get('hp')}{' died' if actor.get('died') else ''}, {needs}")
    print(f"{len(trials) - len(failed)} of {len(trials)} trials as expected")
    (source.parent / "trials.json").write_text(json.dumps(trials, indent=1), encoding="utf-8")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
