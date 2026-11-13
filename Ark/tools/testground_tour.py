"""Walks the Ark Testing Ground in the real client with nobody at the keyboard, and brings back pictures and numbers.

    python tools/testground_tour.py

The client makes a new world of the Ark Testing Ground type (Creative, commands on) as saves/RecorderRun.
client/TestGroundTour flies the player to each section and takes a picture, opens a Specimen Post's screen, and runs
the posts' actions: a knock-out and a tame, a hit and a kill, an arena fight. The pictures go to
run/diagnostics/testground/<time>/screenshots, and the tour's log lines are printed (what the posts timed, the
workshop's stations, the posts' statuses). Shaders off, windowed, muted; the user's files are put back afterwards.
About four minutes; the game window is kept in front.
"""
from __future__ import annotations

import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import session_run as run  # noqa: E402
import ui_screens as screens  # noqa: E402


def main() -> int:
    if run.clients():
        sys.exit("a dev client is already running; close it first")
    folder = run.DIAGNOSTICS / "testground" / time.strftime("%Y%m%d-%H%M%S")
    folder.mkdir(parents=True)
    run.borrow()
    try:
        run.drop_copy()
        (run.DIAGNOSTICS / "arm").unlink(missing_ok=True)  # no recording: the tour is the whole run
        run.lines_set(run.RUN / "options.txt", ":", {"pauseOnLostFocus": "false", "fullscreen": "false", "soundCategory_master": "0.0",
                                                       "overrideWidth": "1280", "overrideHeight": "720", "fov": "0.0"})
        run.lines_set(run.RUN / "config" / "iris.properties", "=", {"enableShaders": "false"})
        print(f"testing ground tour -> {folder}", flush=True)
        screens.launch(folder, [f"-ParkFreshWorld={run.COPY}", "-ParkWorldType=testing_ground", f"-ParkTestGround={folder.as_posix()}"])
    finally:
        run.give_back()
        played = run.SAVES / run.COPY
        if played.is_dir() and not (played / run.MARKER).is_file():
            (played / run.MARKER).write_text("Created by tools/testground_tour.py for one run; safe to delete.\n", encoding="utf-8")
    log = run.RUN / "logs" / "latest.log"
    lines = [line for line in log.read_text(encoding="utf-8", errors="replace").splitlines()
             if "Testground tour:" in line or "Testing ground:" in line] if log.is_file() else []
    (folder / "tour.log").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines), flush=True)
    shots = sorted((folder / "screenshots").glob("*.png")) if (folder / "screenshots").is_dir() else []
    print(f"{len(shots)} pictures in {folder / 'screenshots'}", flush=True)
    return 0 if shots else 1


if __name__ == "__main__":
    sys.exit(main())
