"""Measures the frame times of the real client on a fixed camera path, one launch per setup, and compares them.

    python tools/session_bench.py --prepare [--seed SEED]     the benchmark world, made once and kept
    python tools/session_bench.py --prepare --biome minecraft:plains --pregen 512
                                                              the same, started in the middle of the nearest plains and with
                                                              512 chunks generated around it in every direction by Chunky
    python tools/session_bench.py --warm                      one more pass over it (more chunks and far terrain)
    python tools/session_bench.py [--setups full,noshader,...] [--heap 8G] [--repeat N] [--profile]
    python tools/session_bench.py --setups rd6,rd8,rd12,rd16  the real render distance, dense fog around it and
                                                              Distant Horizons at 64 chunks behind
    python tools/session_bench.py --setups full,bare --draw geckolib|allfaces|nolod|serializer|easing|ark
                                                              who draws the creatures (client/draw)
    python tools/session_bench.py --setups bare --verify      Ark's creature writer checked against GeckoLib's, not timed
    python tools/session_bench.py --setups full --path return --record --config spawning.silentRoundDays=0.05
                                                              out and back: the land a player leaves and comes back to

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

--pregen takes hours for a large radius (a square of 2 x radius + 1 chunks a side) and needs no window in front;
Chunky's jar is fetched once from Modrinth and is in run/mods only for that launch. A setup's copy of the world
leaves out the chunk files farther than 2,048 blocks from the start: the path never loads them, and what
Distant Horizons draws out there comes from its own data, which is copied whole.

The rd setups show how far creatures were drawn (the column 'far', in blocks): the server sends a creature to
the client only within the render distance, 192 blocks at most. Their fog is a patched copy of the shader pack
in use, made for the run (Photon alone: with Distant Horizons it ties its border fog to that mod's distance).

--path return flies out and back instead of the turn and the flight: the player is taken 768 blocks from the
start to where the most land is (--teleport), stands there a minute while the land is settled (--arrive), flies
straight until that place is the render distance and eleven chunks behind (--out), stays away (--hold, 150 s),
flies back and hovers over it (--home, 60 s). Each leg is a phase of the table, and screenshots/depart.png and
home.png show the same view before and after.
--record arms the session recorder for the launch: its file (run/diagnostics/<session>) follows every body and
the wildlife register through the path, session_analyze.py and session_existence.py are run on it, and the
existence report says who was there before, who lived on as a record and who was there after. --config sets a
value of Ark's server configuration for the played copy alone, for example spawning.silentRoundDays=0.05 (a round
of the rules beyond the loaded land every game minute instead of every game day, so a stay of minutes holds some).
"""
from __future__ import annotations

import argparse
import ctypes
import hashlib
import json
import os
import re
import shutil
import statistics
import subprocess
import sys
import time
import urllib.request
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import session_run as run  # noqa: E402

WORLD = "ArkBenchmark"
KEPT = "benchmark_world.txt"
BENCH = run.DIAGNOSTICS / "bench"
MEASURED = ("pan", "flight")
# The legs of the way out and back (--path return): standing where it starts, flying out, staying away, flying back, hovering.
RETURN = ("arrive", "out", "away", "back", "home")
SERVER_CONFIG = "arksurvivalreturns-server.toml"
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
    # The full stack under another garbage collector: two that tidy up while the game runs, and the default one
    # (G1) asked for pauses of 50 ms instead of 200.
    "zgc": {"game": {"Gc": "zgc"}},
    "shenandoah": {"game": {"Gc": "shenandoah"}},
    "g1short": {"game": {"Gc": "g1short"}},
    # The full stack with Iris converting the creatures' vertices to the shader pack's format, as before F27's own sink.
    "serializer": {"game": {"Draw": "serializer"}},
    # The full stack with GeckoLib's easing objects reading the animation's keys, as before client/draw/PlainKeys.
    "easing": {"game": {"Draw": "easing"}},
    # Distant Horizons builds far terrain on six threads at full pace as configured, and allocates most of what the
    # collector has to clear; here on two or three threads that rest half the time.
    "dh2": {"dh": {"numberOfThreads": "2", "threadRunTimeRatio": '"0.5"'}},
    "dh3": {"dh": {"numberOfThreads": "3", "threadRunTimeRatio": '"0.5"'}},
    # The same with the shader pack drawing three quarters of the picture's width and height and scaling it up.
    "dh3taau75": {"dh": {"numberOfThreads": "3", "threadRunTimeRatio": '"0.5"'}, "pack": {"TAAU": "true", "TAAU_RENDER_SCALE": "0.75"}},
}
# The trim to measure (P12): the real render distance, fog closing around it, Distant Horizons at 64 chunks behind.
SETUPS.update({f"rd{chunks}": {"dh": {"lodChunkRenderDistanceRadius": "64"}, "fog": True, "game": {"RenderDistance": str(chunks)}}
               for chunks in (6, 8, 12, 16)})
# Generating a world needs neither the shader pack nor the grass; Distant Horizons stays on to keep what it is shown.
PREGEN = {"iris": {"enableShaders": "false"}, "grass": GRASS_OFF}
CHUNKY = {"file": "Chunky-NeoForge-1.5.4.jar",
          "url": "https://cdn.modrinth.com/data/fALzjamp/versions/EyCqftOK/Chunky-NeoForge-1.5.4.jar",
          "sha512": "ffcbedca6a0b5018962a8a62df21afdafe327ef306e6acb2f602b3bb56f313b88c90e9a57d9a2abaa9d12bd122e888d933ccb243c8fa3388e086a5dd1c18e30d"}
REGION = re.compile(r"^r\.(-?\d+)\.(-?\d+)\.mc[ac]$")
KEEP_BLOCKS = 2048
FOG_PACK = "Ark-Bench-Fog.zip"
FOG_FILE = "shaders/include/fog/simple_fog.glsl"
FOG_WAS = ("#else\n"
           "    float fog = length(scene_pos.xz) / float(lod_render_distance);\n"
           "    fog = exp2(-2.4 * sqr(fog));\n"
           "#endif")
# The pack's own curve for a world without far terrain, with a floor: what lies beyond keeps that much of itself.
FOG_NOW = ("#elif defined DISTANT_HORIZONS\n"
           "    float fog = cubic_length(scene_pos.xz) / far;\n"
           "    fog = mix(%.2f, 1.0, exp2(-8.0 * pow8(fog)));\n"
           + FOG_WAS)


def toml_set(path: Path, values: dict[str, str]):
    """Sets key = value lines of a toml file where the key occurs once, keeping everything else."""
    raw = path.read_bytes().decode("utf-8")
    for key, value in values.items():
        raw, count = re.subn(rf"(?m)^(\s*){re.escape(key)} = [^\r\n]*", lambda match: f"{match.group(1)}{key} = {value}", raw)
        if count != 1:
            sys.exit(f"{path.name}: '{key}' found {count} times")
    path.write_bytes(raw.encode("utf-8"))


def server_config(world: Path, values: dict[str, str]):
    """
    Ark's server configuration for one world: a copy of the instance's file in the world's serverconfig folder, which
    the game reads in its place, with these values set (section.key to a toml value). The instance's file is not touched.
    """
    raw = (run.RUN / "config" / SERVER_CONFIG).read_bytes().decode("utf-8")
    ending = "\r\n" if "\r\n" in raw else "\n"
    lines = raw.replace("\r\n", "\n").split("\n")
    for name, value in values.items():
        section, _, key = name.rpartition(".")
        if not section:
            sys.exit(f"--config {name}: name the section too, for example spawning.{name}")
        heads = [index for index, line in enumerate(lines) if line.strip() == f"[{section}]"]
        if len(heads) != 1:
            sys.exit(f"--config {name}: {SERVER_CONFIG} has {len(heads)} sections [{section}]")
        # The section's own keys end at the next table, its sub-tables included.
        end = next((index for index in range(heads[0] + 1, len(lines)) if lines[index].strip().startswith("[")), len(lines))
        indent = lines[heads[0]][:len(lines[heads[0]]) - len(lines[heads[0]].lstrip())] + "\t"
        found = [index for index in range(heads[0] + 1, end) if re.match(rf"\s*{re.escape(key)}\s*=", lines[index])]
        if found:
            lines[found[0]] = f"{indent}{key} = {value}"
        else:
            # A key the file does not hold yet (a newer build's): the game fills in its comment when it reads the file.
            lines.insert(heads[0] + 1, f"{indent}{key} = {value}")
    target = world / "serverconfig" / SERVER_CONFIG
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(ending.join(lines).encode("utf-8"))


def pack_name() -> str:
    for line in (run.RUN / "config" / "iris.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith("shaderPack="):
            return line.split("=", 1)[1].strip()
    sys.exit("config/iris.properties names no shader pack")


def pack_options() -> str:
    """The file Iris keeps the active shader pack's changed options in, relative to the game folder."""
    return f"shaderpacks/{pack_name()}.txt"


def fog_pack(floor: float) -> str:
    """A copy of the shader pack in use whose border fog closes around the real render distance while Distant
    Horizons draws, with the pack's options. Returns its name; drop_fog_pack removes it."""
    packs = run.RUN / "shaderpacks"
    source = packs / pack_name()
    if not source.is_file() or not zipfile.is_zipfile(source):
        sys.exit(f"the fog of the rd setups patches a zipped shader pack; {source.name} is none")
    found = False
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(packs / FOG_PACK, "w", zipfile.ZIP_DEFLATED) as patched:
        for item in original.infolist():
            data = original.read(item)
            if item.filename == FOG_FILE:
                text = data.decode("utf-8")
                ending = "\r\n" if "\r\n" in text else "\n"
                was = FOG_WAS.replace("\n", ending)
                if text.count(was) != 1:
                    break
                data = text.replace(was, (FOG_NOW % floor).replace("\n", ending)).encode("utf-8")
                found = True
            patched.writestr(item, data)
    if not found:
        drop_fog_pack()
        sys.exit(f"{source.name} has no border fog of the kind the rd setups patch ({FOG_FILE}); they are written for Photon")
    if (packs / f"{source.name}.txt").is_file():
        shutil.copy2(packs / f"{source.name}.txt", packs / f"{FOG_PACK}.txt")
    return FOG_PACK


def drop_fog_pack():
    for name in (FOG_PACK, f"{FOG_PACK}.txt"):
        (run.RUN / "shaderpacks" / name).unlink(missing_ok=True)


def chunky() -> Path:
    """Chunky's jar, fetched once from Modrinth and checked against the pinned checksum."""
    jar = run.ARK / "build" / "bench-mods" / CHUNKY["file"]
    if not jar.is_file():
        jar.parent.mkdir(parents=True, exist_ok=True)
        print(f"fetching {CHUNKY['url']}", flush=True)
        request = urllib.request.Request(CHUNKY["url"], headers={"User-Agent": "Ark-Survival-Returns session_bench"})
        with urllib.request.urlopen(request, timeout=120) as response:
            jar.write_bytes(response.read())
    if hashlib.sha512(jar.read_bytes()).hexdigest() != CHUNKY["sha512"]:
        sys.exit(f"{jar} is not the pinned Chunky build; delete it and run again")
    return jar


def world_start(world: Path) -> tuple[int, int] | None:
    """Where the path starts, as the prepared world's note keeps it (x and z)."""
    note = world / KEPT
    found = re.search(r"^start=(-?\d+) (-?\d+)$", note.read_text(encoding="utf-8"), re.M) if note.is_file() else None
    return (int(found[1]), int(found[2])) if found else None


def near_start(start: tuple[int, int] | None):
    """What a setup's copy leaves out: the lock, and the chunk files too far from the start for the path to load."""
    def ignore(folder, names):
        out = {name for name in names if name == "session.lock"}
        for name in names if start else ():
            region = REGION.match(name)
            if region and max(abs(int(region[1]) * 512 + 256 - start[0]), abs(int(region[2]) * 512 + 256 - start[1])) > KEEP_BLOCKS + 256:
                out.add(name)
        return out
    return ignore


def apply(setup: dict, fog_floor: float | None = 0.25):
    run.lines_set(run.RUN / "options.txt", ":", OPTIONS)
    run.lines_set(run.RUN / "config" / "iris.properties", "=", {"enableShaders": "true", **setup.get("iris", {})})
    toml_set(run.RUN / "config" / "DistantHorizons.toml", {"rendererMode": '"DEFAULT"', **setup.get("dh", {})})
    if "grass" in setup:
        toml_set(run.RUN / "config" / "grassiergrass-client.toml", setup["grass"])
    if "pack" in setup:
        run.lines_set(run.RUN / pack_options(), "=", setup["pack"])
    if setup.get("fog") and fog_floor is not None:
        run.lines_set(run.RUN / "config" / "iris.properties", "=", {"shaderPack": fog_pack(fog_floor)})
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
    pids, pictured, measuring, told, told_at = [], False, False, "", 0.0
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
                    if phase == "pregen":
                        # Hours of chunk generation: no window to keep in front, a line of progress a minute.
                        progress = (folder / "pregen").read_text(encoding="utf-8") if (folder / "pregen").is_file() else ""
                        if progress != told and time.monotonic() - told_at >= 60:
                            told, told_at = progress, time.monotonic()
                            print(f"[{time.monotonic() - started:5.0f} s] {progress}", flush=True)
                    elif game:
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
    marks = sorted(meta["phases"].items(), key=lambda item: item[1]["epoch_ms"])
    for phase in RETURN if meta.get("path") == "return" else MEASURED:
        times = sorted(frames.get(phase, []))
        rows = [row for row in samples if row["phase"] == phase]
        if not times or not rows:
            continue
        slowest = times[-max(1, len(times) // 100):]
        mean = lambda key: statistics.fmean(float(row[key]) for row in rows)  # noqa: E731
        begin = meta["phases"][phase]["epoch_ms"]
        # A leg flown to a place has no length of its own: it lasted until the next phase began.
        later = [mark["epoch_ms"] for _, mark in marks if mark["epoch_ms"] > begin]
        seconds = (later[0] - begin) / 1000 if later else meta["phases"][phase]["seconds"]
        unseen = sum(row["focused"] != "true" or row["iconified"] != "false" for row in rows)
        throttled = sum(row["throttle"] != "NONE" for row in rows)
        result = {
            "seconds": round(seconds, 1), "frames": len(times), "fps": round(len(times) / (sum(times) / 1000), 1),
            "fps_low_1pc": round(1000 / statistics.fmean(slowest), 1),
            "ms_p50": round(percentile(times, 0.5), 2), "ms_p99": round(percentile(times, 0.99), 2), "ms_max": round(times[-1], 1),
            "frames_over_50ms": sum(value > 50 for value in times),
            "render_thread": round(mean("render_cpu"), 2), "server_thread": round(mean("server_cpu"), 2),
            "other_processes": round(max(0.0, mean("system_cpu") - mean("process_cpu")), 2),
            "server_tick_ms": round(mean("server_tick_ms"), 1),
            "gc_ms": int(rows[-1]["gc_ms"]) - int(rows[0]["gc_ms"]), "heap_mb": round(max(float(row["heap_mb"]) for row in rows)),
            "entities": round(mean("entities")), "creatures": round(mean("creatures")) if "creatures" in rows[0] else None,
            "creature_far": round(max(float(row["creature_far"]) for row in rows)) if "creature_far" in rows[0] else None,
            "unseen_seconds": unseen, "throttled_seconds": throttled,
            **card_load(folder, begin, begin + seconds * 1000),
        }
        # Nearly all of the card's time used: the picture waits for the card, not for the game's threads.
        result["limit"] = ("card" if result.get("gpu_load", 0) >= 92 else "render thread" if result["render_thread"] >= 0.85 else "mixed")
        result["valid"] = unseen == 0 and throttled == 0 and window.get("covers_screen") is not False and not window.get("killed")
        phases[phase] = result
    session = (folder / "session.txt").read_text(encoding="utf-8").strip() if (folder / "session.txt").is_file() else None
    return {"setup": meta["variant"], "creature_draw": meta.get("creature_draw", {}).get("mode"), "window": meta["window"], "fullscreen": meta["fullscreen"], "gpu": meta["gpu"],
            "shaders": meta["shaders"], "heap_max_mb": meta["heap_max_mb"], "start": meta["start"],
            "path": meta.get("path"), "anchor": meta.get("anchor"), "out_blocks": meta.get("out_blocks"), "session": session,
            "render_distance": meta.get("render_distance"), "simulation_distance": meta.get("simulation_distance"),
            "mains_power": window.get("mains_power"), "in_front": f"{window.get('in_front')}/{window.get('checks')}",
            "brought_forward": window.get("brought_forward"), "phases": phases}


def table(results: list[dict]):
    print(f"\n{'setup':<10}{'phase':<8}{'fps':>7}{'1% low':>8}{'p50 ms':>8}{'p99 ms':>8}{'max ms':>8}{'>50ms':>6}{'card %':>7}"
          f"{'VRAM':>6}{'render':>7}{'server':>7}{'others':>7}{'GC ms':>7}{'mobs':>6}{'ours':>6}{'far':>5}  limit / valid")
    for result in results:
        for phase, row in result["phases"].items():
            print(f"{result['setup']:<10}{phase:<8}{row['fps']:>7}{row['fps_low_1pc']:>8}{row['ms_p50']:>8}{row['ms_p99']:>8}"
                  f"{row['ms_max']:>8}{row['frames_over_50ms']:>6}{row.get('gpu_load', '-'):>7}{row.get('vram_mb', '-'):>6}"
                  f"{row['render_thread']:>7}{row['server_thread']:>7}{row['other_processes']:>7}{row['gc_ms']:>7}{row['entities']:>6}"
                  f"{row.get('creatures') if row.get('creatures') is not None else '-':>6}"
                  f"{row.get('creature_far') if row.get('creature_far') is not None else '-':>5}"
                  f"  {row['limit']}{'' if row['valid'] else '  INVALID'}")
    for result in results:
        if result.get("path") == "return":
            print(f"{result['setup']}: out and back from {result['anchor']}, {result['out_blocks']} blocks out; legs of "
                  + ", ".join(f"{phase} {row['seconds']:.0f} s" for phase, row in result["phases"].items())
                  + (f"; recorded as {result['session']}" if result.get("session") else ""))
        print(f"{result['setup']}: {result['window']} full screen {result['fullscreen']}, {result['gpu']}, shaders {result['shaders']}, "
              f"render distance {result.get('render_distance', '?')} chunks, "
              f"creatures drawn by {result.get('creature_draw') or 'geckolib'}, "
              f"in front {result['in_front']} checks (brought forward {result['brought_forward']} times), "
              f"mains power {result['mains_power']}")


def recording(folder: Path):
    """The launch's session recording, analysed: its tables, and what became of the animals of the place left and come back to."""
    if not (folder / "session.txt").is_file():
        print(f"no session was recorded; see {folder / 'client.log'}", flush=True)
        return
    session = run.DIAGNOSTICS / (folder / "session.txt").read_text(encoding="utf-8").strip()
    tools = run.ARK / "tools"
    subprocess.run([sys.executable, str(tools / "session_analyze.py"), str(session)])
    subprocess.run([sys.executable, str(tools / "session_existence.py"), str(session), "--bench", str(folder)])


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--prepare", action="store_true", help=f"create run/saves/{WORLD} and fly the path once")
    parser.add_argument("--warm", action="store_true", help=f"fly the path once more over run/saves/{WORLD} and keep what it generated")
    parser.add_argument("--seed", default="2026", help="seed of the prepared world")
    parser.add_argument("--biome", help="with --prepare: start in the middle of the nearest patch of this biome, for example minecraft:plains")
    parser.add_argument("--pregen", type=int, metavar="CHUNKS",
                        help="with --prepare: Chunky generates this many chunks around the start in every direction before the path is flown")
    parser.add_argument("--fog-floor", type=float, default=0.25,
                        help="rd setups: the part of itself the terrain beyond the render distance keeps through the fog, 0 to 1 (default 0.25)")
    parser.add_argument("--no-fog", action="store_true", help="rd setups: leave the shader pack as it is (should the patched one not compile)")
    parser.add_argument("--setups", default=",".join(SETUPS), help=f"comma-separated, of: {', '.join(SETUPS)}")
    parser.add_argument("--heap", help="client heap for the run, for example 8G (default: config/dev-runtime.properties)")
    parser.add_argument("--settle", type=int, help="seconds to wait for chunks and shaders before measuring (default 30; 150 when preparing)")
    parser.add_argument("--repeat", type=int, default=1, help="runs per setup")
    parser.add_argument("--interleave", action="store_true",
                        help="with --repeat: every setup once, then every setup again, so the machine warming up counts against all alike")
    parser.add_argument("--profile", action="store_true", help="also record the Java threads with Flight Recorder (profile.jfr in the setup's folder)")
    parser.add_argument("--draw", choices=("geckolib", "allfaces", "nolod", "serializer", "easing", "ark"),
                        help="who writes the creatures' cubes: GeckoLib; Ark's writer with every face; without the hidden faces; "
                             "with distance detail as well; with all but the plain reading of the animation's keys; "
                             "or also in the shader pack's own vertex format, its defaults "
                             "(default: the client settings)")
    parser.add_argument("--verify", action="store_true",
                        help="draw every eighth creature both ways and compare the vertices (meta.json, creature_draw.verify); not a run to time")
    parser.add_argument("--path", choices=("return",), help="return: fly out and back instead of the turn and the flight")
    parser.add_argument("--teleport", type=int, help="--path return: blocks from the world's start to where the way out and back begins (default 768)")
    parser.add_argument("--arrive", type=int, help="--path return: seconds standing there before leaving, while the land is settled (default 60)")
    parser.add_argument("--out", type=int, help="--path return: chunks to fly out (default: the render distance and eleven more)")
    parser.add_argument("--hold", type=int, help="--path return: seconds to stay away (default 150)")
    parser.add_argument("--home", type=int, help="--path return: seconds to hover over the start after the return (default 60)")
    parser.add_argument("--record", action="store_true",
                        help="arm the session recorder for the launch and analyse its file: every body and the wildlife register through the path")
    parser.add_argument("--config", action="append", default=[], metavar="SECTION.KEY=VALUE",
                        help="a value of Ark's server configuration for the played copy alone, as toml: spawning.silentRoundDays=0.05")
    parser.add_argument("--timeout", type=int, help="seconds before a client is closed by force (default 600, 900 on the way out and back; none with --pregen)")
    parser.add_argument("--summarise", help="only print the table of an earlier run's folder under run/diagnostics/bench")
    arguments = parser.parse_args()

    if arguments.summarise:
        results = [result for folder in sorted((BENCH / arguments.summarise).iterdir()) if folder.is_dir() and (result := summarise(folder))]
        table(results)
        return 0
    if run.clients():
        sys.exit("a dev client is already running; close it first")
    if (arguments.biome or arguments.pregen) and not arguments.prepare:
        sys.exit("--biome and --pregen shape a new world; add --prepare")
    if not 0 <= arguments.fog_floor <= 1:
        sys.exit("--fog-floor is a share, 0 to 1")
    jar = chunky() if arguments.pregen else None
    timeout = arguments.timeout or (float("inf") if arguments.pregen else 900 if arguments.path else 600)
    config = dict(pair.split("=", 1) for pair in arguments.config if "=" in pair)
    if len(config) != len(arguments.config):
        sys.exit("--config takes SECTION.KEY=VALUE")
    if (arguments.path or arguments.record or config) and (arguments.prepare or arguments.warm):
        sys.exit("--path, --record and --config are for a run on the kept world, not for preparing it")
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
    extra += ([f"-ParkDraw={arguments.draw}"] if arguments.draw else []) + (["-ParkVerify=true"] if arguments.verify else [])
    extra += [f"-Park{name.capitalize()}={value}" for name in ("path", "teleport", "arrive", "out", "hold", "home")
              if (value := getattr(arguments, name)) is not None]
    if on_mains() is False:
        print("on battery: the graphics card is throttled, the numbers will not be the machine's", flush=True)
    results = []
    order = ([(name, turn) for turn in range(arguments.repeat) for name in names] if arguments.interleave
             else [(name, turn) for name in names for turn in range(arguments.repeat)])
    for name, turn in order:
        folder = stamp / (name if arguments.repeat == 1 else f"{name}-{turn + 1}")
        folder.mkdir(parents=True)
        run.borrow((*run.BORROWED, "config/DistantHorizons.toml", "config/grassiergrass-client.toml", pack_options()))
        mod = run.RUN / "mods" / CHUNKY["file"] if jar else None
        try:
            apply(PREGEN if jar else SETUPS.get(name, {}), None if arguments.no_fog else arguments.fog_floor)
            if arguments.prepare:
                opening = [f"-ParkFreshWorld={WORLD}", f"-ParkSeed={arguments.seed}"]
                opening += [f"-ParkBiome={arguments.biome}"] if arguments.biome else []
                if jar:
                    opening.append(f"-ParkPregen={arguments.pregen}")
                    mod.parent.mkdir(exist_ok=True)
                    shutil.copy2(jar, mod)
            else:
                # A warmed copy becomes the kept world, so it is copied whole.
                played = run.copy_world(WORLD, near_start(None if arguments.warm else world_start(kept)))
                opening = [f"-ParkWorld={run.COPY}"]
                if config:
                    server_config(played, config)
            before = run.sessions()
            if arguments.record:
                # The recording runs from the moment the player is in control until the game closes.
                (run.DIAGNOSTICS / "arm").write_text("delaySeconds=0\nrecordSeconds=3600\n", encoding="utf-8")
            print(f"setup '{name}' -> {folder}", flush=True)
            game = [f"-Park{key}={value}" for key, value in SETUPS.get(name, {}).get("game", {}).items()]
            profile = [f"-ParkJfr={(folder / 'profile.jfr').as_posix()}"] if arguments.profile else []
            launch(folder, name, opening, extra + game + profile, timeout, folder / "client.log")
            recorded = sorted(run.sessions() - before)
            if recorded:
                (folder / "session.txt").write_text(recorded[-1], encoding="utf-8")
        finally:
            run.give_back()
            drop_fog_pack()
            if mod:
                mod.unlink(missing_ok=True)
        result = summarise(folder)
        if arguments.record:
            recording(folder)
        refused = (folder / "error").read_text(encoding="utf-8") if (folder / "error").is_file() else None
        if refused:
            print(f"setup '{name}' could not start: {refused}", flush=True)
        elif result is None:
            print(f"setup '{name}' wrote no measurements; see {folder / 'client.log'} and {run.RUN / 'logs' / 'latest.log'}", flush=True)
        else:
            results.append(result)
        if arguments.prepare and refused and kept.is_dir():
            shutil.rmtree(kept)
        elif arguments.prepare and (kept / "level.dat").is_file():
            x, _, z = result["start"].split() if result else ("", "", "")
            (kept / KEPT).write_text("The benchmark world of tools/session_bench.py; every setup plays a copy of it.\n"
                                     + (f"start={x} {z}\n" if result else ""), encoding="utf-8")
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
