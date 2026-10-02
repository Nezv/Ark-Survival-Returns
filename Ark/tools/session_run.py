"""Records a creature session in the real client with nobody at the keyboard, then analyses it.

    python tools/session_run.py [--world NAME] [--seconds 300] [--delay 60] [--mode approach|stand]

Copies a saved world, launches the dev client straight into the copy with the session recorder armed and
a scripted player (client/SessionAutopilot: walk up to the nearest wild creature, stand in front of it,
back away, pick the next), waits until the recording is saved and the game has closed itself, and runs
session_analyze.py and session_review.py on the result.

For the run only: shaders off, windowed, muted, no pause when the window loses focus. options.txt, the
Iris setting and an arm file that was already there are put back afterwards, also when the run fails or
is interrupted, and the world copy is deleted; the world it was copied from is never opened.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
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


def launch(mode: str, timeout: float, log: Path, before: set[str]) -> int | None:
    """Runs the client until it closes itself. None when it had to be killed."""
    environment = dict(os.environ)
    jdk = Path.home() / ".jbang" / "cache" / "jdks" / "25"
    if not Path(environment.get("JAVA_HOME", "")).joinpath("bin").is_dir() and jdk.is_dir():
        environment["JAVA_HOME"] = str(jdk)
    gradle = ["cmd", "/c", str(ARK / "gradlew.bat")] if os.name == "nt" else [str(ARK / "gradlew")]
    command = gradle + ["runClient", f"-ParkWorld={COPY}", f"-ParkAutopilot={mode}", "-ParkQuit", "--console=plain"]
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
    parser.add_argument("--seconds", type=int, default=300, help="length of the recording, unpaused real seconds")
    parser.add_argument("--delay", type=int, default=60, help="seconds between the player gaining control and the recording")
    parser.add_argument("--mode", choices=("approach", "stand"), default="approach", help="what the scripted player does")
    parser.add_argument("--shaders", action="store_true", help="leave the shader setting as it is")
    parser.add_argument("--keep-world", action="store_true", help=f"keep the played copy as run/saves/{COPY}")
    parser.add_argument("--timeout", type=int, help="seconds before the client is closed by force (default: delay + seconds + 600)")
    arguments = parser.parse_args()

    if clients():
        sys.exit("a dev client is already running; close it first")
    world = arguments.world or newest_world()
    before = sessions()
    log = DIAGNOSTICS / "session_run.log"
    DIAGNOSTICS.mkdir(parents=True, exist_ok=True)
    borrow()
    try:
        copy_world(world)
        lines_set(RUN / "options.txt", ":", {"pauseOnLostFocus": "false", "fullscreen": "false", "soundCategory_master": "0.0"})
        if not arguments.shaders:
            lines_set(RUN / "config" / "iris.properties", "=", {"enableShaders": "false"})
        (DIAGNOSTICS / "arm").write_text(f"delaySeconds={arguments.delay}\nrecordSeconds={arguments.seconds}\n", encoding="utf-8")
        print(f"world '{world}' copied to saves/{COPY}; recording {arguments.seconds} s after {arguments.delay} s, "
              f"player: {arguments.mode}, shaders {'as set' if arguments.shaders else 'off'}; game output in {log}", flush=True)
        code = launch(arguments.mode, arguments.timeout or arguments.delay + arguments.seconds + 600, log, before)
    finally:
        give_back()
        if not arguments.keep_world:
            drop_copy()
    fresh = sorted(sessions() - before)
    if not fresh:
        print(f"no recording was made (client exit: {code}); see {log} and {RUN / 'logs' / 'latest.log'}")
        return 1
    session = DIAGNOSTICS / fresh[-1]
    result = subprocess.run([sys.executable, str(ARK / "tools" / "session_analyze.py"), str(session)]).returncode
    subprocess.run([sys.executable, str(ARK / "tools" / "session_review.py"), str(session)])
    return result


if __name__ == "__main__":
    sys.exit(main())
