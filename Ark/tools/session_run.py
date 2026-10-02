"""Records a creature session in the real client with nobody at the keyboard, then analyses it.

    python tools/session_run.py [--world NAME | --new-world [--seed SEED] [--flat]] [--time TICKS] [--seconds 300]
                                [--delay 60] [--mode approach|stand] [--scenario water|encounter]

Copies a saved world (or has the client create a new one), launches the dev client straight into it with
the session recorder armed and a scripted player (client/SessionAutopilot: walk up to the nearest wild
creature, stand in front of it, back away, pick the next; or only stand), waits until the recording is
saved and the game has closed itself, and runs session_analyze.py, session_sight.py and
session_review.py and session_validate.py on the result. validation.json maps recorded evidence to
Dashboard.csv items and identifies checks that were not exercised; it does not close whole features.

The player starts healed and fed; --time moves the clock forward to that time of day when the player
joins (the recording starts --delay seconds after the loading screen, about 1,300 ticks later with the
default). For the run only: shaders off, windowed, muted, no pause when the window loses focus.
options.txt, the Iris setting and an arm file that was already there are put back afterwards, also when
the run fails or is interrupted, and the played world is deleted; the world it was copied from is never
opened.

--scenario water runs six explicitly marked thirsty-animal trials at loaded natural shorelines.
The observer becomes creative and flies above the bank; animals keep their ordinary wild AI. Setup
checks a reachable path without giving it to the animal. Starts are 12 and 20 blocks from a verified
bank (other closer water may exist). Each trial has 60 game seconds to drink; recording ends once all
six trials finish or at --seconds, whichever comes first.
Allow --seconds 420 for all six, plus extra time if suitable routes are hard to find. Use --mode stand.
This is controlled validation, not a natural census. A copied save's experimental-world confirmation
is accepted on its disposable copy so Minecraft can enter it unattended.

--scenario encounter spawns one wild creature at a time at a set distance from the standing survival
observer, who is healed every tick: a Giganotosaurus at 30, 60 and 90 blocks and at 30 behind a line of
oaks, by night; a Parasaur at 16 and a Pegomastax at 10 by day; a thirsty Lystrosaurus 20 blocks out with a
pond ten blocks beyond it. Natural spawning is off and other mobs near the observer are removed before each
trial. The recording ends when the seven trials are done (about five and a half minutes). Use it with
--new-world --flat (open plains: grass on dirt, plains biome, no villages), --mode stand and --delay 0.
"""
from __future__ import annotations

import argparse
import gzip
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import time
from pathlib import Path

ARK = Path(__file__).resolve().parent.parent
RUN = ARK / "run"
SAVES = RUN / "saves"
DIAGNOSTICS = RUN / "diagnostics"
COPY = "RecorderRun"
MARKER = "recorder_copy.txt"
# The user's own files wait here while the run has its versions in place.
HOLD = DIAGNOSTICS / "restore"
BORROWED = ("options.txt", "config/iris.properties", "diagnostics/arm")
SESSION = re.compile(r"^\d{8}-\d{6}-[0-9a-f]{4}$")


def lines_set(path: Path, separator: str, values: dict[str, str]):
    """Sets key<separator>value lines of a settings file, keeping every other line and the line endings."""
    raw = path.read_bytes().decode("utf-8") if path.is_file() else ""
    ending = "\r\n" if "\r\n" in raw else "\n"
    lines = [line for line in raw.replace("\r\n", "\n").split("\n") if line]
    for key, value in values.items():
        lines = [line for line in lines if not line.startswith(key + separator)]
        lines.append(key + separator + value)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((ending.join(lines) + ending).encode("utf-8"))


def borrow():
    """Sets the user's files aside. A folder left by a run that died is given back first."""
    if HOLD.exists():
        give_back()
    HOLD.mkdir(parents=True)
    present = {}
    for name in BORROWED:
        source = RUN / name
        present[name] = source.is_file()
        if present[name]:
            shutil.copy2(source, HOLD / name.replace("/", "__"))
    (HOLD / "manifest.json").write_text(json.dumps(present), encoding="utf-8")


def give_back():
    manifest = HOLD / "manifest.json"
    if manifest.is_file():
        for name, present in json.loads(manifest.read_text(encoding="utf-8")).items():
            target = RUN / name
            if present:
                shutil.copy2(HOLD / name.replace("/", "__"), target)
            elif target.is_file():
                target.unlink()
    shutil.rmtree(HOLD, ignore_errors=True)


def newest_world() -> str:
    worlds = [folder for folder in SAVES.iterdir() if folder.name != COPY and (folder / "level.dat").is_file()]
    if not worlds:
        sys.exit(f"no world under {SAVES}")
    return max(worlds, key=lambda folder: (folder / "level.dat").stat().st_mtime).name


def copy_world(name: str) -> Path:
    source, target = SAVES / name, SAVES / COPY
    if not (source / "level.dat").is_file():
        sys.exit(f"no world '{name}' under {SAVES}")
    drop_copy()
    shutil.copytree(source, target, ignore=shutil.ignore_patterns("session.lock"))
    (target / MARKER).write_text(f"Copy of '{name}' made by tools/session_run.py for one recording; safe to delete.\n", encoding="utf-8")
    # Minecraft otherwise leaves quick-play at its experimental-world backup prompt. This world is
    # already an isolated disposable copy; accept only its existing byte flag, never the original.
    name_tag = b"confirmedExperimentalSettings"
    unset = b"\x01" + struct.pack(">H", len(name_tag)) + name_tag + b"\x00"
    level = target / "level.dat"
    raw = gzip.decompress(level.read_bytes())
    if raw.count(unset) == 1:
        level.write_bytes(gzip.compress(raw.replace(unset, unset[:-1] + b"\x01", 1), mtime=0))
        print("accepted experimental-world confirmation on the disposable copy", flush=True)
    return target


def drop_copy():
    target = SAVES / COPY
    if not target.exists():
        return
    if not (target / MARKER).is_file():
        sys.exit(f"{target} exists and was not made by this script; move it away first")
    shutil.rmtree(target)


def clients() -> list[int]:
    """Process ids of dev clients of any checkout: their command line names the client run's argument file."""
    if os.name == "nt":
        script = ("Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | "
                  "Where-Object { $_.CommandLine -like '*clientRunVmArgs*' } | ForEach-Object { $_.ProcessId }")
        out = subprocess.run(["powershell", "-NoProfile", "-Command", script], capture_output=True, text=True).stdout
    else:
        out = subprocess.run(["pgrep", "-f", "clientRunVmArgs"], capture_output=True, text=True).stdout
    return [int(word) for word in out.split() if word.isdigit()]


def kill(pid: int):
    if os.name == "nt":
        subprocess.run(["taskkill", "/PID", str(pid), "/T", "/F"], capture_output=True)
    else:
        subprocess.run(["kill", "-9", str(pid)], capture_output=True)


def sessions() -> set[str]:
    return {folder.name for folder in DIAGNOSTICS.iterdir() if folder.is_dir() and SESSION.match(folder.name)} if DIAGNOSTICS.is_dir() else set()


def launch(mode: str, timeout: float, log: Path, before: set[str], world: list[str]) -> int | None:
    """Runs the client until it closes itself. None when it had to be killed."""
    environment = dict(os.environ)
    jdk = Path.home() / ".jbang" / "cache" / "jdks" / "25"
    if not Path(environment.get("JAVA_HOME", "")).joinpath("bin").is_dir() and jdk.is_dir():
        environment["JAVA_HOME"] = str(jdk)
    gradle = ["cmd", "/c", str(ARK / "gradlew.bat")] if os.name == "nt" else [str(ARK / "gradlew")]
    command = gradle + ["runClient", *world, f"-ParkAutopilot={mode}", "-ParkQuit", "--console=plain"]
    started = time.monotonic()
    seen = None
    with log.open("wb") as output:
        process = subprocess.Popen(command, cwd=ARK, env=environment, stdout=output, stderr=subprocess.STDOUT)
        while process.poll() is None:
            time.sleep(2)
            fresh = sessions() - before
            if fresh and seen is None:
                seen = sorted(fresh)[-1]
                print(f"[{time.monotonic() - started:5.0f} s] recording started: {seen}", flush=True)
            if time.monotonic() - started > timeout:
                print(f"[{time.monotonic() - started:5.0f} s] no end after {timeout:.0f} s; closing the client", flush=True)
                for pid in clients():
                    kill(pid)
                try:
                    process.wait(60)
                except subprocess.TimeoutExpired:
                    kill(process.pid)
                return None
        print(f"[{time.monotonic() - started:5.0f} s] client closed, gradle exit {process.returncode}", flush=True)
        return process.returncode


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--world", help="folder name under run/saves to copy (default: the one played last)")
    parser.add_argument("--new-world", action="store_true", help="have the client create a new survival world instead of copying one")
    parser.add_argument("--seed", help="seed of the new world (default: random; the recording's header holds it)")
    parser.add_argument("--flat", action="store_true", help="the new world is flat open plains with no structures")
    parser.add_argument("--time", type=int, help="time of day, 0 to 23999, the clock is moved forward to when the player joins")
    parser.add_argument("--seconds", type=int, default=300, help="length of the recording, unpaused real seconds")
    parser.add_argument("--delay", type=int, default=60, help="seconds between the player gaining control and the recording")
    parser.add_argument("--mode", choices=("approach", "stand"), default="approach", help="what the scripted player does")
    parser.add_argument("--scenario", choices=("water", "encounter"), help="controlled test setup, explicitly recorded; requires --mode stand")
    parser.add_argument("--shaders", action="store_true", help="leave the shader setting as it is")
    parser.add_argument("--keep-world", action="store_true", help=f"keep the played copy as run/saves/{COPY}")
    parser.add_argument("--timeout", type=int, help="seconds before the client is closed by force (default: delay + seconds + 600)")
    arguments = parser.parse_args()

    if clients():
        sys.exit("a dev client is already running; close it first")
    if arguments.new_world and arguments.world:
        sys.exit("--world and --new-world exclude each other")
    if arguments.time is not None and not 0 <= arguments.time <= 23999:
        sys.exit("--time is a time of day, 0 to 23999")
    if arguments.scenario and arguments.mode != "stand":
        sys.exit("--scenario requires --mode stand")
    if arguments.flat and not arguments.new_world:
        sys.exit("--flat shapes a new world; add --new-world")
    world = None if arguments.new_world else arguments.world or newest_world()
    before = sessions()
    log = DIAGNOSTICS / "session_run.log"
    DIAGNOSTICS.mkdir(parents=True, exist_ok=True)
    borrow()
    try:
        if world:
            copy_world(world)
            opening = [f"-ParkWorld={COPY}"]
        else:
            drop_copy()
            opening = ([f"-ParkFreshWorld={COPY}"] + ([f"-ParkSeed={arguments.seed}"] if arguments.seed else [])
                       + (["-ParkWorldType=flat"] if arguments.flat else []))
        lines_set(RUN / "options.txt", ":", {"pauseOnLostFocus": "false", "fullscreen": "false", "soundCategory_master": "0.0"})
        if not arguments.shaders:
            lines_set(RUN / "config" / "iris.properties", "=", {"enableShaders": "false"})
        clock = "" if arguments.time is None else f"dayTime={arguments.time}\n"
        scenario = "" if arguments.scenario is None else f"scenario={arguments.scenario}\n"
        (DIAGNOSTICS / "arm").write_text(f"delaySeconds={arguments.delay}\nrecordSeconds={arguments.seconds}\nheal=true\n{clock}{scenario}",
                                         encoding="utf-8")
        print((f"world '{world}' copied to saves/{COPY}" if world else
               f"new {'flat plains ' if arguments.flat else ''}world saves/{COPY}, seed {arguments.seed or 'random'}")
              + f"; recording {arguments.seconds} s after {arguments.delay} s, player: {arguments.mode}, "
              f"time of day {'as saved' if arguments.time is None else arguments.time}, "
              f"scenario {arguments.scenario or 'none'}, "
              f"shaders {'as set' if arguments.shaders else 'off'}; game output in {log}", flush=True)
        code = launch(arguments.mode, arguments.timeout or arguments.delay + arguments.seconds + 600, log, before, opening)
    finally:
        give_back()
        played = SAVES / COPY
        if played.is_dir() and not (played / MARKER).is_file():
            (played / MARKER).write_text("Created by tools/session_run.py for one recording; safe to delete.\n", encoding="utf-8")
    try:
        fresh = sorted(sessions() - before)
        if not fresh:
            print(f"no recording was made (client exit: {code}); see {log} and {RUN / 'logs' / 'latest.log'}")
            return 1
        session = DIAGNOSTICS / fresh[-1]
        tools = ARK / "tools"
        result = subprocess.run([sys.executable, str(tools / "session_analyze.py"), str(session)]).returncode
        # The played world holds the blocks of every chunk the session saw, so the sight rays are walked through it.
        subprocess.run([sys.executable, str(tools / "session_sight.py"), str(session), "--world", COPY])
        subprocess.run([sys.executable, str(tools / "session_review.py"), str(session)])
        subprocess.run([sys.executable, str(tools / "session_validate.py"), str(session)])
        return result
    finally:
        if not arguments.keep_world:
            drop_copy()


if __name__ == "__main__":
    sys.exit(main())
