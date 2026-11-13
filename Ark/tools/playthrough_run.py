"""Plays a day of Ark in the Testing Ground with nobody at the keyboard, and writes down what happened (F43).

    python tools/playthrough_run.py [--routine standard|smoke]

The client makes a new Ark Testing Ground world (saves/RecorderRun) and feature/playthrough/Playthrough drives a
survival player through the routine: gather and craft each stage, then a kill streak and a damage test, then the supply
drop tiers. Every step is one line of run/diagnostics/playthrough/<time>/events.jsonl, with pictures in screenshots/;
nothing is judged during the run. Shaders, the grass and Distant Horizons are off, render and simulation distance 8,
windowed, muted, never paused; the user's files are put back afterwards. The client closes itself at the end.
"""
from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import session_bench as bench  # noqa: E402
import session_run as run  # noqa: E402
import playthrough_report as report  # noqa: E402
import ui_screens as screens  # noqa: E402

BARE = {"iris": {"enableShaders": "false"}, "grass": bench.GRASS_OFF, "dh": {"rendererMode": '"DISABLED"'}}
OPTIONS = {"pauseOnLostFocus": "false", "fullscreen": "false", "soundCategory_master": "0.0", "renderDistance": "8",
           "simulationDistance": "8", "enableVsync": "false", "maxFps": "120", "inactivityFpsLimit": '"minimized"',
           "overrideWidth": "1280", "overrideHeight": "720"}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--routine", default="standard", help="standard, smoke (three species), stages or sweeps")
    parser.add_argument("--hours", type=float, default=2.5, help="close the client after this long whatever happens")
    arguments = parser.parse_args()
    if run.clients():
        sys.exit("a dev client is already running; close it first")
    folder = run.DIAGNOSTICS / "playthrough" / time.strftime("%Y%m%d-%H%M%S")
    folder.mkdir(parents=True)
    screens.TIMEOUT = int(arguments.hours * 3600)
    run.borrow((*run.BORROWED, "config/DistantHorizons.toml", "config/grassiergrass-client.toml"))
    try:
        run.drop_copy()
        bench.apply(BARE, None)
        run.lines_set(run.RUN / "options.txt", ":", OPTIONS)
        print(f"playthrough {arguments.routine} -> {folder}", flush=True)
        screens.launch(folder, [f"-ParkFreshWorld={run.COPY}", "-ParkWorldType=testing_ground",
                                f"-ParkPlaythrough={folder.as_posix()}", f"-ParkRoutine={arguments.routine}"])
    finally:
        run.give_back()
        played = run.SAVES / run.COPY
        if played.is_dir() and not (played / run.MARKER).is_file():
            (played / run.MARKER).write_text("Created by tools/playthrough_run.py for one run; safe to delete.\n", encoding="utf-8")
    log = run.RUN / "logs" / "latest.log"
    if log.is_file():
        lines = log.read_text(encoding="utf-8", errors="replace").splitlines()
        errors = [line for line in lines if "/ERROR]" in line or "Exception" in line or "Playthrough:" in line]
        (folder / "errors.log").write_text("".join(line + "\n" for line in errors), encoding="utf-8")
    events = folder / "events.jsonl"
    lines = events.read_text(encoding="utf-8").splitlines() if events.is_file() else []
    print(f"{len(lines)} events in {events}", flush=True)
    if lines:
        print(lines[-1], flush=True)
        print(f"report: {report.report(folder)}", flush=True)
    return 0 if lines and '"run_end"' in lines[-1] else 1


if __name__ == "__main__":
    sys.exit(main())
