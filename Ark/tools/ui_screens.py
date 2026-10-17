"""Takes the showcase's Interface screens from the real client: the title screen, the new-world screen, the Ark
inventory and the options, with the shader pack on, without JEI and without FancyMenu's editor bar.

    python tools/ui_screens.py [--gui-scale 4]

Plays a disposable copy of run/saves/ArkBenchmark (python tools/session_bench.py --prepare makes it) full screen with
the shader pack, launched with -ParkNoJei so JEI's item list is not drawn beside the inventory. client/ScreenTour
stands the player at the world spawn in survival on a clear morning, takes the pictures and closes the game. They go
to design/ui-rework/screens as JPEG, where build_showcase.py reads them. The Game screen there is a picked shader
screenshot (game.jpg); the tour's own view of the benchmark plains stays in the run folder as screenshots/game.png.
The user's files are put back afterwards, also when the run fails. About three minutes; the game window is kept in front, so leave the machine alone meanwhile.
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import time
from pathlib import Path
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import session_bench as bench  # noqa: E402
import session_run as run  # noqa: E402

SHOTS = ("title", "new_game", "inventory", "options")
FANCYMENU = "config/fancymenu/options.txt"
OUT = run.ARK / "design" / "ui-rework" / "screens"
TIMEOUT = 600


def launch(folder: Path, arguments: list[str]) -> int | None:
    """Runs the client until the tour closes it, keeping its window in front. Returns gradle's exit code."""
    environment = dict(os.environ)
    jdk = Path.home() / ".jbang" / "cache" / "jdks" / "25"
    if not Path(environment.get("JAVA_HOME", "")).joinpath("bin").is_dir() and jdk.is_dir():
        environment["JAVA_HOME"] = str(jdk)
    gradle = ["cmd", "/c", str(run.ARK / "gradlew.bat")] if os.name == "nt" else [str(run.ARK / "gradlew")]
    started = time.monotonic()
    with (folder / "client.log").open("wb") as output:
        process = subprocess.Popen(gradle + ["runClient", *arguments, "--console=plain"], cwd=run.ARK, env=environment,
                                   stdout=output, stderr=subprocess.STDOUT)
        pids = []
        while process.poll() is None:
            time.sleep(1)
            if bench.user32 is not None:
                pids = pids or run.clients()
                game = bench.game_window(pids) if pids else None
                if game and not bench.in_front(game):
                    bench.bring_forward(game)
            if time.monotonic() - started > TIMEOUT:
                print(f"no end after {TIMEOUT} s; closing the client", flush=True)
                for pid in run.clients():
                    run.kill(pid)
                try:
                    process.wait(60)
                except subprocess.TimeoutExpired:
                    run.kill(process.pid)
                break
    print(f"[{time.monotonic() - started:5.0f} s] client closed, gradle exit {process.returncode}", flush=True)
    return process.returncode


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--gui-scale", type=int, default=4, help="GUI scale of the pictures (4: the Ark inventory's own at 1080p)")
    arguments = parser.parse_args()
    if run.clients():
        sys.exit("a dev client is already running; close it first")
    kept = run.SAVES / bench.WORLD
    if not (kept / "level.dat").is_file():
        sys.exit("no benchmark world yet: python tools/session_bench.py --prepare")
    folder = run.DIAGNOSTICS / "screens" / time.strftime("%Y%m%d-%H%M%S")
    folder.mkdir(parents=True)
    run.borrow((*run.BORROWED, "config/DistantHorizons.toml", "config/grassiergrass-client.toml", bench.pack_options(), FANCYMENU))
    try:
        bench.apply(bench.SETUPS["full"])
        run.lines_set(run.RUN / "options.txt", ":", {"guiScale": str(arguments.gui_scale)})
        menus = run.RUN / FANCYMENU
        if menus.is_file():  # the editor's menu bar over the top of every menu
            menus.write_bytes(re.sub(rb"(?m)^B:show_customization_overlay = '\w+';", b"B:show_customization_overlay = 'false';",
                                     menus.read_bytes()))
        run.copy_world(bench.WORLD, bench.near_start(bench.world_start(kept)))
        print(f"screens -> {folder}", flush=True)
        launch(folder, [f"-ParkWorld={run.COPY}", f"-ParkScreens={folder.as_posix()}", "-ParkNoJei=true"])
    finally:
        run.give_back()
        run.drop_copy()
    shots = folder / "screenshots"
    missing = [name for name in SHOTS if not (shots / f"{name}.png").is_file()]
    if missing:
        print(f"missing {', '.join(missing)}; see {folder / 'client.log'} and {run.RUN / 'logs' / 'latest.log'}", flush=True)
        return 1
    OUT.mkdir(parents=True, exist_ok=True)
    for name in SHOTS:
        Image.open(shots / f"{name}.png").convert("RGB").save(OUT / f"{name}.jpg", quality=90)
    print(f"wrote {len(SHOTS)} screens to {OUT}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
