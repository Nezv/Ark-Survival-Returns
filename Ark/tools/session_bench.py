"""Measures the frame times of the real client on a fixed camera path, one launch per setup, and compares them.

    python tools/session_bench.py --prepare [--seed SEED]     the benchmark world, made once and kept
    python tools/session_bench.py --warm                      one more pass over it (more chunks and far terrain)
    python tools/session_bench.py [--setups full,noshader,...] [--heap 8G] [--repeat N] [--profile]

Each setup plays a disposable copy of run/saves/ArkBenchmark, so every one starts from the same chunks and the
same Distant Horizons data. The client (client/FrameBenchmark) stands the player at the world spawn at noon,
waits for chunks and shaders, turns once on the ground and flies east over the terrain; the path follows the
clock, so a slow setup sees the same views as a fast one. Per setup the folder
run/diagnostics/bench/<stamp>/<setup> gets every frame's duration, a sample a second (window state, thread
load, heap), the graphics card's load from nvidia-smi, two screenshots of the game's picture and one of the
desktop; summary.json and the printed table compare the setups.

For the run only: full screen, vertical sync off, no frame limit, muted, and the settings a setup changes
(shader pack, its options, Grassier Grass, Distant Horizons). The game is put in front of every other window
and kept there, because a full-screen window opened from the background draws nothing; a second in which it
was not in front is counted, and a measured phase with one is marked invalid. The user's files are put back
afterwards, also when the run fails. Do not use the machine while it runs.
"""
from __future__ import annotations

import argparse
import ctypes
import json
import os
import re
import shutil
import statistics
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import session_run as run  # noqa: E402

WORLD = "ArkBenchmark"
KEPT = "benchmark_world.txt"
BENCH = run.DIAGNOSTICS / "bench"
MEASURED = ("pan", "flight")
OPTIONS = {"pauseOnLostFocus": "false", "fullscreen": "true", "soundCategory_master": "0.0", "enableVsync": "false",
           "maxFps": "260", "inactivityFpsLimit": '"minimized"', "startedCleanly": "true"}
GRASS_OFF = {"grassSparsity": "1.0", "grassPlantsAsBlades": "false"}
# What each setup changes from the full stack: shader pack on as configured, Grassier Grass as configured,
# Distant Horizons drawing. iris: config/iris.properties; pack: the shader pack's options; grass and dh: their toml;
# game: properties of the client run (build.gradle), Mobs=false empties the world of creatures.
SETUPS = {
    "full": {},
    "noshader": {"iris": {"enableShaders": "false"}},
    "nograss": {"grass": GRASS_OFF},
    "nodh": {"dh": {"rendererMode": '"DISABLED"'}},
    "taau75": {"pack": {"TAAU": "true", "TAAU_RENDER_SCALE": "0.75"}},
    "taau50": {"pack": {"TAAU": "true", "TAAU_RENDER_SCALE": "0.50"}},
    "bare": {"iris": {"enableShaders": "false"}, "grass": GRASS_OFF, "dh": {"rendererMode": '"DISABLED"'}},
    "nomobs": {"game": {"Mobs": "false"}},
    "barenomobs": {"iris": {"enableShaders": "false"}, "grass": GRASS_OFF, "dh": {"rendererMode": '"DISABLED"'}, "game": {"Mobs": "false"}},
}


def toml_set(path: Path, values: dict[str, str]):
    """Sets key = value lines of a toml file where the key occurs once, keeping everything else."""
    raw = path.read_bytes().decode("utf-8")
    for key, value in values.items():
        raw, count = re.subn(rf"(?m)^(\s*){re.escape(key)} = [^\r\n]*", lambda match: f"{match.group(1)}{key} = {value}", raw)
        if count != 1:
            sys.exit(f"{path.name}: '{key}' found {count} times")
    path.write_bytes(raw.encode("utf-8"))


def pack_options() -> str:
    """The file Iris keeps the active shader pack's changed options in, relative to the game folder."""
    for line in (run.RUN / "config" / "iris.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith("shaderPack="):
            return f"shaderpacks/{line.split('=', 1)[1].strip()}.txt"
    sys.exit("config/iris.properties names no shader pack")


def apply(setup: dict):
    run.lines_set(run.RUN / "options.txt", ":", OPTIONS)
    run.lines_set(run.RUN / "config" / "iris.properties", "=", {"enableShaders": "true", **setup.get("iris", {})})
    toml_set(run.RUN / "config" / "DistantHorizons.toml", {"rendererMode": '"DEFAULT"', **setup.get("dh", {})})
    if "grass" in setup:
        toml_set(run.RUN / "config" / "grassiergrass-client.toml", setup["grass"])
    if "pack" in setup:
        run.lines_set(run.RUN / pack_options(), "=", setup["pack"])
    arm = run.DIAGNOSTICS / "arm"
    if arm.is_file():
        arm.unlink()


user32 = ctypes.windll.user32 if os.name == "nt" else None
if user32 is not None:
    user32.GetForegroundWindow.restype = ctypes.c_void_p


def game_window(pids: list[int]) -> int | None:
    found = []

    @ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_void_p, ctypes.c_void_p)
    def visit(window, _):
        pid = ctypes.c_ulong()
        user32.GetWindowThreadProcessId(ctypes.c_void_p(window), ctypes.byref(pid))
        name = ctypes.create_unicode_buffer(64)
        user32.GetClassNameW(ctypes.c_void_p(window), name, 64)
        if pid.value in pids and user32.IsWindowVisible(ctypes.c_void_p(window)) and name.value.startswith("GLFW"):
            found.append(window)
        return True

    user32.EnumWindows(visit, 0)
    return found[0] if found else None


def in_front(window: int) -> bool:
    return user32.GetForegroundWindow() == window and not user32.IsIconic(ctypes.c_void_p(window))


def bring_forward(window: int):
    """Windows refuses the foreground to a background process unless it has just pressed a key: Alt, here."""
    user32.keybd_event(0x12, 0, 0, 0)
    user32.keybd_event(0x12, 0, 2, 0)
    if user32.IsIconic(ctypes.c_void_p(window)):
        user32.ShowWindow(ctypes.c_void_p(window), 9)
    user32.SetForegroundWindow(ctypes.c_void_p(window))


def covers_screen(window: int) -> bool:
    rect = (ctypes.c_long * 4)()
    user32.GetWindowRect(ctypes.c_void_p(window), ctypes.byref(rect))
    return rect[0] <= 0 and rect[1] <= 0 and rect[2] >= user32.GetSystemMetrics(0) and rect[3] >= user32.GetSystemMetrics(1)


def on_mains() -> bool | None:
    if os.name != "nt":
        return None
    status = (ctypes.c_ubyte * 12)()
    ctypes.windll.kernel32.GetSystemPowerStatus(ctypes.byref(status))
    return {0: False, 1: True}.get(status[0])


def desktop_picture(target: Path):
    """What the screen shows, not what the game drew: proof that the picture reached the display."""
    try:
        from PIL import ImageGrab
        picture = ImageGrab.grab()
        picture.thumbnail((1280, 1280))
        picture.convert("RGB").save(target, quality=85)
    except Exception as error:  # the capture is evidence, never a reason to lose the run
        print(f"no desktop picture: {error}", flush=True)


def launch(folder: Path, name: str, opening: list[str], extra: list[str], timeout: float, log: Path) -> dict:
    """Runs the client until the benchmark closes it, keeping its window in front. Returns what the window did."""
    environment = dict(os.environ)
    jdk = Path.home() / ".jbang" / "cache" / "jdks" / "25"
    if not Path(environment.get("JAVA_HOME", "")).joinpath("bin").is_dir() and jdk.is_dir():
        environment["JAVA_HOME"] = str(jdk)
    gradle = ["cmd", "/c", str(run.ARK / "gradlew.bat")] if os.name == "nt" else [str(run.ARK / "gradlew")]
    command = gradle + ["runClient", *opening, f"-ParkBenchmark={folder.as_posix()}", f"-ParkVariant={name}", *extra, "--console=plain"]
    window = {"checks": 0, "in_front": 0, "brought_forward": 0, "covers_screen": None, "mains_power": on_mains(), "killed": False}
    started = time.monotonic()
    pids, pictured, measuring = [], False, False
    card = None
    try:
        card = subprocess.Popen(["nvidia-smi", "--query-gpu=timestamp,utilization.gpu,memory.used,temperature.gpu,power.draw",
                                 "--format=csv,noheader,nounits", "-lms", "500"], stdout=(folder / "gpu.csv").open("wb"))
    except OSError:
        print("no nvidia-smi: the graphics card's load is not recorded", flush=True)
    try:
        with log.open("wb") as output:
            process = subprocess.Popen(command, cwd=run.ARK, env=environment, stdout=output, stderr=subprocess.STDOUT)
            while process.poll() is None:
                time.sleep(1)
                if user32 is not None:
                    pids = pids or run.clients()
                    game = game_window(pids) if pids else None
                    phase = (folder / "phase").read_text(encoding="utf-8") if (folder / "phase").is_file() else ""
                    if game:
                        front = in_front(game)
                        if phase and phase != "done":
                            if not measuring:
                                measuring = True
                                print(f"[{time.monotonic() - started:5.0f} s] in the world, measuring", flush=True)
                            window["checks"] += 1
                            window["in_front"] += front
                            window["covers_screen"] = covers_screen(game)
                        if not front:
                            window["brought_forward"] += 1
                            bring_forward(game)
                        if phase == "settle" and not pictured and time.time() - (folder / "phase").stat().st_mtime > 15:
                            pictured = True
                            desktop_picture(folder / "desktop.jpg")
                if time.monotonic() - started > timeout:
                    print(f"[{time.monotonic() - started:5.0f} s] no end after {timeout:.0f} s; closing the client", flush=True)
                    window["killed"] = True
                    for pid in run.clients():
                        run.kill(pid)
                    try:
                        process.wait(60)
                    except subprocess.TimeoutExpired:
                        run.kill(process.pid)
                    break
            print(f"[{time.monotonic() - started:5.0f} s] client closed, gradle exit {process.returncode}", flush=True)
    finally:
        if card is not None:
            card.terminate()
    (folder / "window.json").write_text(json.dumps(window, indent=1), encoding="utf-8")
    return window


def percentile(ordered: list[float], fraction: float) -> float:
    return ordered[min(len(ordered) - 1, int(len(ordered) * fraction))]


def card_load(folder: Path, begin_ms: float, end_ms: float) -> dict:
    """Mean load and peak memory of the graphics card between two moments, from nvidia-smi's lines."""
    load, memory, power = [], [], []
    path = folder / "gpu.csv"
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines() if path.is_file() else []:
        cells = [cell.strip() for cell in line.split(",")]
        try:
            stamp = time.mktime(time.strptime(cells[0][:19], "%Y/%m/%d %H:%M:%S")) * 1000 + float(cells[0][20:23] or 0)
            if begin_ms <= stamp <= end_ms:
                load.append(float(cells[1]))
                memory.append(float(cells[2]))
                power.append(float(cells[4]))
        except (ValueError, IndexError):
            continue
    return {"gpu_load": round(statistics.fmean(load), 1), "vram_mb": round(max(memory)), "gpu_watts": round(statistics.fmean(power), 1)} if load else {}


def summarise(folder: Path) -> dict | None:
    """One row per measured phase of a run: frame rates, stutter, who was busy, and whether the window was seen."""
    if not (folder / "meta.json").is_file():
        return None
    meta = json.loads((folder / "meta.json").read_text(encoding="utf-8"))
    window = json.loads((folder / "window.json").read_text(encoding="utf-8")) if (folder / "window.json").is_file() else {}
    frames: dict[str, list[float]] = {}
    for line in (folder / "frames.csv").read_text(encoding="utf-8").splitlines()[1:]:
        phase, nanos = line.split(",")
        frames.setdefault(phase, []).append(int(nanos) / 1e6)
    lines = (folder / "samples.csv").read_text(encoding="utf-8").splitlines()
    head = lines[0].split(",")
    samples = [dict(zip(head, line.split(","))) for line in lines[1:]]
    phases = {}
    for phase in MEASURED:
        times = sorted(frames.get(phase, []))
        rows = [row for row in samples if row["phase"] == phase]
        if not times or not rows:
            continue
        slowest = times[-max(1, len(times) // 100):]
        mean = lambda key: statistics.fmean(float(row[key]) for row in rows)  # noqa: E731
        begin = meta["phases"][phase]["epoch_ms"]
        unseen = sum(row["focused"] != "true" or row["iconified"] != "false" for row in rows)
        throttled = sum(row["throttle"] != "NONE" for row in rows)
        result = {
            "frames": len(times), "fps": round(len(times) / (sum(times) / 1000), 1),
            "fps_low_1pc": round(1000 / statistics.fmean(slowest), 1),
            "ms_p50": round(percentile(times, 0.5), 2), "ms_p99": round(percentile(times, 0.99), 2), "ms_max": round(times[-1], 1),
            "frames_over_50ms": sum(value > 50 for value in times),
            "render_thread": round(mean("render_cpu"), 2), "server_thread": round(mean("server_cpu"), 2),
            "other_processes": round(max(0.0, mean("system_cpu") - mean("process_cpu")), 2),
            "server_tick_ms": round(mean("server_tick_ms"), 1),
            "gc_ms": int(rows[-1]["gc_ms"]) - int(rows[0]["gc_ms"]), "heap_mb": round(max(float(row["heap_mb"]) for row in rows)),
            "entities": round(mean("entities")), "creatures": round(mean("creatures")) if "creatures" in rows[0] else None,
            "unseen_seconds": unseen, "throttled_seconds": throttled,
            **card_load(folder, begin, begin + meta["phases"][phase]["seconds"] * 1000),
        }
        # Nearly all of the card's time used: the picture waits for the card, not for the game's threads.
        result["limit"] = ("card" if result.get("gpu_load", 0) >= 92 else "render thread" if result["render_thread"] >= 0.85 else "mixed")
        result["valid"] = unseen == 0 and throttled == 0 and window.get("covers_screen") is not False and not window.get("killed")
        phases[phase] = result
    return {"setup": meta["variant"], "window": meta["window"], "fullscreen": meta["fullscreen"], "gpu": meta["gpu"],
            "shaders": meta["shaders"], "heap_max_mb": meta["heap_max_mb"], "start": meta["start"],
            "mains_power": window.get("mains_power"), "in_front": f"{window.get('in_front')}/{window.get('checks')}",
            "brought_forward": window.get("brought_forward"), "phases": phases}


def table(results: list[dict]):
    print(f"\n{'setup':<10}{'phase':<8}{'fps':>7}{'1% low':>8}{'p50 ms':>8}{'p99 ms':>8}{'max ms':>8}{'>50ms':>6}{'card %':>7}"
          f"{'VRAM':>6}{'render':>7}{'server':>7}{'others':>7}{'GC ms':>7}{'mobs':>6}{'ours':>6}  limit / valid")
    for result in results:
        for phase, row in result["phases"].items():
            print(f"{result['setup']:<10}{phase:<8}{row['fps']:>7}{row['fps_low_1pc']:>8}{row['ms_p50']:>8}{row['ms_p99']:>8}"
                  f"{row['ms_max']:>8}{row['frames_over_50ms']:>6}{row.get('gpu_load', '-'):>7}{row.get('vram_mb', '-'):>6}"
                  f"{row['render_thread']:>7}{row['server_thread']:>7}{row['other_processes']:>7}{row['gc_ms']:>7}{row['entities']:>6}"
                  f"{row.get('creatures') if row.get('creatures') is not None else '-':>6}"
                  f"  {row['limit']}{'' if row['valid'] else '  INVALID'}")
    for result in results:
        print(f"{result['setup']}: {result['window']} full screen {result['fullscreen']}, {result['gpu']}, shaders {result['shaders']}, "
              f"in front {result['in_front']} checks (brought forward {result['brought_forward']} times), "
              f"mains power {result['mains_power']}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--prepare", action="store_true", help=f"create run/saves/{WORLD} and fly the path once")
    parser.add_argument("--warm", action="store_true", help=f"fly the path once more over run/saves/{WORLD} and keep what it generated")
    parser.add_argument("--seed", default="2026", help="seed of the prepared world")
    parser.add_argument("--setups", default=",".join(SETUPS), help=f"comma-separated, of: {', '.join(SETUPS)}")
    parser.add_argument("--heap", help="client heap for the run, for example 8G (default: config/dev-runtime.properties)")
    parser.add_argument("--settle", type=int, help="seconds to wait for chunks and shaders before measuring (default 30; 150 when preparing)")
    parser.add_argument("--repeat", type=int, default=1, help="runs per setup")
    parser.add_argument("--profile", action="store_true", help="also record the Java threads with Flight Recorder (profile.jfr in the setup's folder)")
    parser.add_argument("--timeout", type=int, default=600, help="seconds before a client is closed by force")
    parser.add_argument("--summarise", help="only print the table of an earlier run's folder under run/diagnostics/bench")
    arguments = parser.parse_args()

    if arguments.summarise:
        results = [result for folder in sorted((BENCH / arguments.summarise).iterdir()) if folder.is_dir() and (result := summarise(folder))]
        table(results)
        return 0
    if run.clients():
        sys.exit("a dev client is already running; close it first")
    kept = run.SAVES / WORLD
    building = arguments.prepare or arguments.warm
    if arguments.prepare and kept.exists():
        if not (kept / KEPT).is_file():
            sys.exit(f"{kept} exists and was not made by this script; move it away first")
        shutil.rmtree(kept)
    if not arguments.prepare and not (kept / "level.dat").is_file():
        sys.exit(f"no benchmark world yet: python tools/session_bench.py --prepare")
    names = ["prepare" if arguments.prepare else "warm"] if building else [name.strip() for name in arguments.setups.split(",")]
    for name in names:
        if not building and name not in SETUPS:
            sys.exit(f"unknown setup '{name}'; known: {', '.join(SETUPS)}")
    stamp = BENCH / time.strftime("%Y%m%d-%H%M%S")
    extra = ([f"-ParkHeap={arguments.heap}"] if arguments.heap else []) + [f"-ParkSettle={arguments.settle or (150 if building else 30)}"]
    if on_mains() is False:
        print("on battery: the graphics card is throttled, the numbers will not be the machine's", flush=True)
    results = []
    for name in names:
        for turn in range(arguments.repeat):
            folder = stamp / (name if arguments.repeat == 1 else f"{name}-{turn + 1}")
            folder.mkdir(parents=True)
            run.borrow((*run.BORROWED, "config/DistantHorizons.toml", "config/grassiergrass-client.toml", pack_options()))
            try:
                apply(SETUPS.get(name, {}))
                if arguments.prepare:
                    opening = [f"-ParkFreshWorld={WORLD}", f"-ParkSeed={arguments.seed}"]
                else:
                    run.copy_world(WORLD)
                    opening = [f"-ParkWorld={run.COPY}"]
                print(f"setup '{name}' -> {folder}", flush=True)
                game = [f"-Park{key}={value}" for key, value in SETUPS.get(name, {}).get("game", {}).items()]
                profile = [f"-ParkJfr={(folder / 'profile.jfr').as_posix()}"] if arguments.profile else []
                launch(folder, name, opening, extra + game + profile, arguments.timeout, folder / "client.log")
            finally:
                run.give_back()
            result = summarise(folder)
            if result is None:
                print(f"setup '{name}' wrote no measurements; see {folder / 'client.log'} and {run.RUN / 'logs' / 'latest.log'}", flush=True)
            else:
                results.append(result)
            if arguments.prepare and (kept / "level.dat").is_file():
                (kept / KEPT).write_text("The benchmark world of tools/session_bench.py; every setup plays a copy of it.\n", encoding="utf-8")
            elif arguments.warm and result is not None:
                played = run.SAVES / run.COPY
                (played / run.MARKER).unlink()
                (played / KEPT).write_text((kept / KEPT).read_text(encoding="utf-8"), encoding="utf-8")
                shutil.rmtree(kept)
                played.rename(kept)
            else:
                run.drop_copy()
    (stamp / "summary.json").write_text(json.dumps(results, indent=1), encoding="utf-8")
    table(results)
    return 0 if len(results) == len(names) * arguments.repeat else 1


if __name__ == "__main__":
    sys.exit(main())
