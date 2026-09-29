"""Iron Age machines: Mechanical Press -> Milling Machine -> Cutting Machine (P07, planned).

Ported from the Create-style generator in Models.md (repo root): the geometry, the moving parts and the
animation rules are unchanged; the materials use its second, colour-varied texture set, with the yellow
brass replaced by brushed steel. Each machine is 1x2x1 (y = 0..32 px) on a shared chassis: a belt with
rollers from the input (east) to the output (west), a heat port at the back (south) and a heat gauge on the
front (north). Heat picks the internal speed (none 0, heated 32, superheated 64 RPM); there is no drive shaft.

Writes (the blocks are not registered yet; blockstates and the ghost upper block come with them):
  assets/arksurvivalreturns/models/block/machines/<machine>/base.json   static body, rest pose
  assets/arksurvivalreturns/models/block/machines/<machine>/<part>.json moving partials (block space)
  assets/arksurvivalreturns/models/block/machines/common/<part>.json    shared partials (rollers, needle)
  assets/arksurvivalreturns/textures/block/machines/<material>.png      16x16, deterministic noise
  assets/arksurvivalreturns/machines/<machine>.json                     animation spec for the renderer
  design/machines/<machine>.png, line.png                               offline previews (--no-preview skips)
Conventions: front = north (z = 0), input = east (x = 16), output = west (x = 0), heat = south (z = 16).
Units: px (1/16 block), degrees, ticks; right-hand-rule rotations, as in Minecraft element rotations.

Run from Ark: python tools/build_machine_assets.py
"""
import argparse
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw

ARK = Path(__file__).resolve().parents[1]
ASSETS = ARK / 'src/main/resources/assets/arksurvivalreturns'
PREVIEW = ARK / 'design/machines'
MOD_ID = "arksurvivalreturns"
RPM = {"none": 0, "heated": 32, "superheated": 64}  # heat level -> internal drive speed

# Colour-varied set: base colour + how many adjacent shades each texel may use (1-5). "steel" replaces the
# set's aged brass everywhere (arches, gauge rim, name plate, spindle head, guards).
PALETTE = {
    "casing": (96, 88, 80),     # riveted soot-iron plate
    "frame":  (104, 66, 40),    # dark walnut
    "alloy":  (150, 146, 134),  # gunmetal
    "steel":  (138, 148, 160),  # brushed steel
    "copper": (182, 98, 58),    # copper
    "iron":   (54, 52, 54),     # blackened iron
    "belt":   (96, 60, 38),     # oiled leather
    "blade":  (204, 202, 194),  # tool steel
    "dial":   (228, 214, 178),  # aged ivory
    "needle": (152, 36, 28),    # oxblood
}
VARY = {"casing": 4, "frame": 5, "alloy": 3, "steel": 3, "copper": 4,
        "iron": 3, "belt": 3, "blade": 2, "dial": 2, "needle": 1}
SHADE_STEP = 0.055                 # brightness gap between adjacent shades
PATINA, RIVET, STITCH = (86, 148, 124), (188, 196, 206), (196, 164, 112)

COMMON = {
    "static": [
        [0, 0, 0, 16, 6, 16, "casing"],                  # chassis
        [1.5, 6, 2, 14.5, 9, 4, "frame"],                # belt rails
        [1.5, 6, 12, 14.5, 9, 14, "frame"],
        [1.5, 5, 4, 14.5, 8, 12, "belt"],                # belt, top at y=8
        [14.5, 6, 2, 16, 12.5, 4, "steel"],              # intake arch (east / input)
        [14.5, 6, 12, 16, 12.5, 14, "steel"],
        [14.5, 11, 4, 16, 12.5, 12, "steel"],
        [0, 6, 2, 1.5, 12.5, 4, "copper"],               # exit arch (west / output)
        [0, 6, 12, 1.5, 12.5, 14, "copper"],
        [0, 11, 4, 1.5, 12.5, 12, "copper"],
        [4, 1, 15.5, 12, 5, 16.25, "copper"],            # heat port (south / back)
        [5, 2, 16.25, 11, 4, 16.5, "iron"],
        [10, 0.5, -0.5, 15, 5.5, 0, "steel"],            # heat gauge (front)
        [10.5, 1, -0.75, 14.5, 5, -0.5, "dial"],
        [12.1, 2.6, -1.1, 12.9, 3.4, -0.9, "steel"],
    ],
    "parts": {
        "roller_in": {"boxes": [[13.25, 5.25, 4, 15.75, 7.75, 12, "alloy"],
                                [13.25, 5.25, 4.1, 15.75, 7.75, 11.9, "alloy", ["z", 45, 14.5, 6.5, 8]]],
                    "mode": "rotate", "axis": "z", "pivot": [14.5, 6.5, 8],
                    "drive": "spin", "spin_idle": 1, "spin_proc": 1},
        "roller_out": {"boxes": [[0.25, 5.25, 4, 2.75, 7.75, 12, "alloy"],
                                [0.25, 5.25, 4.1, 2.75, 7.75, 11.9, "alloy", ["z", 45, 1.5, 6.5, 8]]],
                    "mode": "rotate", "axis": "z", "pivot": [1.5, 6.5, 8],
                    "drive": "spin", "spin_idle": 1, "spin_proc": 1},
        "gauge_needle": {"boxes": [[12.25, 3, -1, 12.75, 4.75, -0.75, "needle"]],
                        "mode": "rotate", "axis": "z", "pivot": [12.5, 3, -0.875], "drive": "heat",
                        "heat_angles": {"none": -70, "heated": 10, "superheated": 70}, "jitter": 3},
    },
}

MACHINES = {
    "mechanical_press": {
        "static": [
            [0, 6, 0, 2, 22, 2, "frame"], [14, 6, 0, 16, 22, 2, "frame"],       # corner posts
            [0, 6, 14, 2, 22, 16, "frame"], [14, 6, 14, 16, 22, 16, "frame"],
            [0, 22, 0, 16, 30, 16, "casing"],                                   # press housing
            [5, 23.5, -0.5, 11, 28.5, 0, "steel"],                              # name plate
            [6.5, 6, 14, 9.5, 22, 16, "copper"],                                # heat riser
            [6, 12, 13.5, 10, 13.5, 16.25, "copper"],
        ],
        "parts": {
            "press_head": {"boxes": [[6.75, 20, 6.75, 9.25, 30, 9.25, "alloy"],  # ram
                                    [5, 19, 5, 11, 20, 11, "steel"],
                                    [3, 16, 3, 13, 19, 13, "iron"],
                                    [4, 15, 4, 12, 16, 12, "alloy"]],           # die face
                        "mode": "translate", "axis": "y", "pivot": [8, 0, 8],
                        "drive": "cycle", "cycle_ticks": 20,
                        "keys": [[0, 0, "in"], [0.3, -6, "hold"], [0.45, -6, "out"], [1, 0, "hold"]]},
            "drive_cog": {"boxes": [[5, 30, 5, 11, 31.5, 11, "frame"],
                                    [5, 30, 5, 11, 31.25, 11, "frame", ["y", 45, 8, 30, 8]],
                                    [2.75, 30.25, 7, 13.25, 31, 9, "frame"],
                                    [7, 30.25, 2.75, 9, 31, 13.25, "frame"],
                                    [2.75, 30.25, 7, 13.25, 31, 9, "frame", ["y", 45, 8, 30, 8]],
                                    [7, 30.25, 2.75, 9, 31, 13.25, "frame", ["y", 45, 8, 30, 8]],
                                    [7, 30, 7, 9, 32, 9, "alloy"]],
                        "mode": "rotate", "axis": "y", "pivot": [8, 30, 8],
                        "drive": "spin", "spin_idle": 1, "spin_proc": 1},
        },
    },
    "milling_machine": {
        "static": [
            [2, 6, 12.5, 14, 28, 16, "casing"],                                 # column
            [1.5, 28, 12, 14.5, 29.5, 16, "frame"],
            [5, 9, 12, 11, 20, 12.5, "iron"],                                   # slide ways
            [3.5, 20, 3, 12.5, 27, 12.5, "steel"],                              # spindle head
            [5, 27, 5, 11, 30.5, 11, "copper"],                                 # heat motor drum
            [5, 27, 5, 11, 30.25, 11, "copper", ["y", 45, 8, 27, 8]],
            [6.5, 30.5, 6.5, 9.5, 31.5, 9.5, "iron"],
            [10.5, 17, 4, 11.5, 20, 5, "copper"],                               # coolant nozzle
        ],
        "parts": {
            "quill": {"boxes": [[6.5, 18, 6.5, 9.5, 26, 9.5, "alloy"], [6, 17, 6, 10, 18, 10, "steel"]],
                    "mode": "translate", "axis": "y", "pivot": [8, 0, 8],
                    "drive": "cycle", "cycle_ticks": 40,
                    "keys": [[0, 0, "inout"], [0.2, -2.5, "hold"], [0.8, -2.5, "inout"], [1, 0, "hold"]]},
            "spindle": {"boxes": [[6.25, 15, 6.25, 9.75, 17, 9.75, "iron"],
                                [6.25, 15.25, 6.25, 9.75, 17, 9.75, "iron", ["y", 45, 8, 16, 8]],
                                [7.25, 12, 7.25, 8.75, 15, 8.75, "blade"],
                                [6.5, 12.25, 7.75, 9.5, 14.5, 8.25, "blade", ["y", 22.5, 8, 13, 8]],
                                [7.75, 12.25, 6.5, 8.25, 14.5, 9.5, "blade", ["y", 22.5, 8, 13, 8]]],
                        "parent": "quill", "mode": "rotate", "axis": "y", "pivot": [8, 0, 8],
                        "drive": "spin", "spin_idle": 1, "spin_proc": 4},
        },
    },
    "cutting_machine": {
        "static": [
            [3, 6, 12, 13, 26, 16, "casing"],                                   # saw tower
            [8, 17, 11.5, 11.5, 23, 12, "steel"],                               # arm slot plate
            [4, 26, 12.5, 12, 29, 15.5, "copper"],                              # heat motor drum
            [4.25, 26, 12.5, 11.75, 29, 15.5, "copper", ["x", 45, 8, 27.5, 14]],
            [3, 26.5, 13, 4, 28.5, 15, "steel"], [12, 26.5, 13, 13, 28.5, 15, "steel"],
        ],
        "parts": {
            "arm": {"boxes": [[9, 19, 3.69, 10.5, 21, 16, "iron", ["x", -45, 8, 20, 15]],
                            [6.75, 16, 3, 9.25, 17, 11, "steel"],               # blade guard
                            [8.75, 12, 3, 9.25, 16, 11, "steel"]],
                    "mode": "rotate", "axis": "x", "pivot": [8, 20, 15],
                    "drive": "cycle", "cycle_ticks": 40,
                    "keys": [[0, 25, "inout"], [0.45, 0, "hold"], [0.6, 0, "inout"], [1, 25, "hold"]]},
            "blade": {"boxes": [[7.5, 9, 4, 8.5, 15, 10, "blade"],
                                [7.6, 9, 4, 8.4, 15, 10, "blade", ["x", 22.5, 8, 12, 7]],
                                [7.7, 9, 4, 8.3, 15, 10, "blade", ["x", 45, 8, 12, 7]],
                                [7.8, 9, 4, 8.2, 15, 10, "blade", ["x", -22.5, 8, 12, 7]],
                                [7, 11, 6, 9, 13, 8, "alloy"],
                                [7.1, 11, 6, 8.9, 13, 8, "alloy", ["x", 45, 8, 12, 7]]],
                    "parent": "arm", "mode": "rotate", "axis": "x", "pivot": [8, 12, 7],
                    "drive": "spin", "spin_idle": -3, "spin_proc": -3},
        },
    },
}

PREVIEW_ITEMS = {  # preview only: the workpiece under each tool (not exported)
    "mechanical_press": [[4.5, 8, 5, 11.5, 9, 11, "steel"]],
    "milling_machine": [[5, 8, 5.5, 11, 9.5, 10.5, "steel"]],
    "cutting_machine": [[3, 8, 7, 13, 9.5, 9, "steel"]],
}
LINE = [("mechanical_press", 16), ("milling_machine", 0), ("cutting_machine", -16)]  # left -> right


# ---------------- math ----------------
def add(a, b): return (a[0] + b[0], a[1] + b[1], a[2] + b[2])
def sub(a, b): return (a[0] - b[0], a[1] - b[1], a[2] - b[2])
def dot(a, b): return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
def cross(a, b): return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
def unit(a):
    n = math.sqrt(dot(a, a)); return (a[0] / n, a[1] / n, a[2] / n)

def rot(p, axis, deg, o):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    if axis == "x": x, y, z = x, y * c - z * s, y * s + z * c
    elif axis == "y": x, y, z = x * c + z * s, y, -x * s + z * c
    else: x, y, z = x * c - y * s, x * s + y * c, z
    return (x + o[0], y + o[1], z + o[2])

def apply(p, ops):
    for op in ops:
        p = add(p, op[1]) if op[0] == "t" else rot(p, op[1], op[2], op[3])
    return p

# ---------------- animation ----------------
EASE = {"linear": lambda s: s, "in": lambda s: s * s, "out": lambda s: 1 - (1 - s) ** 2,
        "inout": lambda s: s * s * (3 - 2 * s), "hold": lambda s: 0.0}

def keyframe(keys, u):
    for (t0, v0, ease), (t1, v1, _) in zip(keys, keys[1:]):
        if u <= t1:
            s = (u - t0) / (t1 - t0) if t1 > t0 else 1.0
            return v0 + (v1 - v0) * EASE[ease](min(1.0, max(0.0, s)))
    return keys[-1][1]

def part_value(p, t, heat, proc):
    rpm = RPM[heat]
    if p["drive"] == "spin":
        return rpm * 0.3 * (p["spin_proc"] if proc else p["spin_idle"]) * t
    if p["drive"] == "cycle":
        if not proc or rpm == 0: return p["keys"][0][1]
        period = p["cycle_ticks"] * 32 / rpm
        return keyframe(p["keys"], (t % period) / period)
    return p["heat_angles"][heat] + (p["jitter"] * math.sin(1.7 * t) if rpm else 0)

def part_ops(parts, name, t, heat, proc):
    p = parts[name]; v = part_value(p, t, heat, proc)
    ops = ([("t", tuple(v if a == p["axis"] else 0 for a in "xyz"))] if p["mode"] == "translate"
        else [("r", p["axis"], v, p["pivot"])])
    return ops + (part_ops(parts, p["parent"], t, heat, proc) if "parent" in p else [])

def scene(name, t=0.0, heat="heated", proc=True, dx=0, items=True):
    m = MACHINES[name]; parts = {**COMMON["parts"], **m["parts"]}
    shift = [("t", (dx, 0, 0))] if dx else []
    out = [(b, shift) for b in COMMON["static"] + m["static"] + (PREVIEW_ITEMS[name] if items else [])]
    for pname, p in parts.items():
        ops = part_ops(parts, pname, t, heat, proc) + shift
        out += [(b, ops) for b in p["boxes"]]
    return out


# ---------------- textures ----------------
def shade(c, k): return tuple(max(0, min(255, int(v * k))) for v in c[:3])


def hash2(x, y, s):  # deterministic integer hash
    h = (x * 374761393 + y * 668265263 + s * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def texel(mat, x, y):
    s, n = sum(map(ord, mat)), VARY[mat]
    mid = hash2(x >> 2, y, s + 1) if mat == "frame" else hash2(x >> 1, y >> 1, s + 1)
    v = 0.5 * hash2(x, y, s) + 0.3 * mid + 0.2 * hash2(x >> 2, y >> 2, s + 2)
    k = 1 + (min(n - 1, int(v * n)) - (n - 1) / 2) * SHADE_STEP      # step on the shade ramp
    base, edge = PALETTE[mat], min(x, y, 15 - x, 15 - y)
    if mat == "casing":
        if edge < 2: return shade(texel("frame", x, y), 0.8 if edge == 0 else 1.0)  # walnut rim
        if x in (2, 13) and y in (2, 13): return shade(RIVET, k)                  # steel rivets
        if x == 2 or y == 2: k *= 1.08                                            # plate bevel
        if x == 13 or y == 13: k *= 0.9
    if mat == "copper" and edge > 0 and hash2(x, y, s + 9) > 0.88: base = PATINA   # verdigris
    if mat == "steel" and (x - y) % 5 == 0: k *= 1.07                              # brushed streaks
    if mat == "iron" and (x + y) % 7 == 0 and hash2(x, y, s + 5) > 0.4: k *= 1.12  # scratches
    if mat == "belt":
        if x % 4 == 0: k *= 0.8                                                    # grooves
        if y in (4, 11) and x % 2 == 0: base = STITCH                              # stitching
    if edge == 0 and mat not in ("dial", "needle", "belt"): k *= 0.8               # vanilla-style border
    return shade(base, k)


def texture(mat):
    img = Image.new("RGBA", (16, 16)); px = img.load()
    for x in range(16):
        for y in range(16):
            px[x, y] = texel(mat, x, y) + (255,)
    return img


TEX = {}
def tex_px(mat, u, v):
    if mat not in TEX: TEX[mat] = [[texel(mat, x, y) for y in range(16)] for x in range(16)]
    return TEX[mat][min(15, max(0, int(u)))][min(15, max(0, int(v)))]


# ---------------- preview renderer (painter's algorithm on 1-texel cells, the game's UVs) ----------------
LIGHT = unit((-0.3, 1.0, -0.55))


def faces(b):  # (corner, edge U, edge V) with U x V = outward normal: east, west, up, down, south, north
    x1, y1, z1, x2, y2, z2 = b[:6]; dx, dy, dz = x2 - x1, y2 - y1, z2 - z1
    return (((x2, y1, z2), (0, 0, -dz), (0, dy, 0)), ((x1, y1, z1), (0, 0, dz), (0, dy, 0)),
            ((x1, y2, z2), (dx, 0, 0), (0, 0, -dz)), ((x1, y1, z1), (dx, 0, 0), (0, 0, dz)),
            ((x1, y1, z2), (dx, 0, 0), (0, dy, 0)), ((x2, y1, z1), (-dx, 0, 0), (0, dy, 0)))


def camera(yaw, pitch):
    a, b = math.radians(yaw), math.radians(pitch)
    d = (-math.cos(b) * math.sin(a), -math.sin(b), math.cos(b) * math.cos(a))
    r = unit(cross(d, (0, 1, 0)))
    return d, r, cross(r, d)


def fit_uv(a, b):
    if b - a >= 16: return 0, 16
    k = math.floor(a / 16) * 16; a, b = a - k, b - k
    if b > 16: a, b = 16 - (b - a), 16
    return round(a, 3), round(b, 3)


def face_uvs(b):  # order matches faces(): east, west, up, down, south, north
    x1, y1, z1, x2, y2, z2 = b[:6]
    raw = {"east": (16 - z2, 16 - y2, 16 - z1, 16 - y1), "west": (z1, 16 - y2, z2, 16 - y1),
           "up": (x1, z1, x2, z2), "down": (x1, 16 - z2, x2, 16 - z1),
           "south": (x1, 16 - y2, x2, 16 - y1), "north": (16 - x2, 16 - y2, 16 - x1, 16 - y1)}
    out = {}
    for f, (u1, v1, u2, v2) in raw.items():
        (u1, u2), (v1, v2) = fit_uv(u1, u2), fit_uv(v1, v2)
        out[f] = (u1, v1, u2, v2)
    return out


def project(boxes, yaw, pitch, cell=1.0):
    d, r, u = camera(yaw, pitch)
    P = lambda v: (dot(v, r), -dot(v, u), dot(v, d))
    polys = []
    for b, ops in boxes:
        ops = ([("r", b[7][0], b[7][1], b[7][2:5])] if len(b) > 7 else []) + ops
        mat = b[6]
        for (p0, U, V), (u1, v1, u2, v2) in zip(faces(b), face_uvs(b).values()):
            q0 = apply(p0, ops)
            eu, ev = sub(apply(add(p0, U), ops), q0), sub(apply(add(p0, V), ops), q0)
            n = cross(eu, ev); ln = math.sqrt(dot(n, n))
            if ln < 1e-9 or dot(n, d) >= 0: continue
            lit = 0.52 + 0.48 * max(0.0, dot(n, LIGHT) / ln)
            lu, lv = math.sqrt(dot(eu, eu)), math.sqrt(dot(ev, ev))
            nu, nv = max(1, math.ceil(lu / cell - 1e-6)), max(1, math.ceil(lv / cell - 1e-6))
            s0, su, sv = P(q0), P(eu), P(ev)
            for i in range(nu):
                a0, a1 = i / nu, (i + 1) / nu; am = (a0 + a1) / 2
                tu = u1 + am * (u2 - u1)
                for j in range(nv):
                    c0, c1 = j / nv, (j + 1) / nv; cm = (c0 + c1) / 2
                    col = shade(tex_px(mat, tu, v2 - cm * (v2 - v1)), lit)
                    pts = [(s0[0] + su[0] * a + sv[0] * c, s0[1] + su[1] * a + sv[1] * c)
                           for a, c in ((a0, c0), (a1, c0), (a1, c1), (a0, c1))]
                    polys.append((s0[2] + su[2] * am + sv[2] * cm, pts, col))
    polys.sort(key=lambda q: -q[0])
    return polys


def render(boxes, yaw=-28, pitch=26, size=640, bg=(0, 0, 0, 0), fit=None, cell=1.0, ss=2):
    polys = project(boxes, yaw, pitch, cell)
    if fit is None:
        xs = [x for _, pts, _ in polys for x, _ in pts]; ys = [y for _, pts, _ in polys for _, y in pts]
        k = 0.86 * size / max(max(xs) - min(xs), max(ys) - min(ys))
        fit = (k, size / 2 - k * (max(xs) + min(xs)) / 2, size / 2 - k * (max(ys) + min(ys)) / 2)
    k, ox, oy = fit
    img = Image.new("RGBA", (size * ss, size * ss), bg); draw = ImageDraw.Draw(img)
    for _, pts, col in polys:
        draw.polygon([((x * k + ox) * ss, (y * k + oy) * ss) for x, y in pts], fill=col + (255,), outline=col + (255,))
    return img.resize((size, size), Image.LANCZOS), fit


# ---------------- Minecraft export ----------------
def element(b):
    el = {"from": list(b[0:3]), "to": list(b[3:6]),
          "faces": {f: {"uv": list(uv), "texture": "#" + b[6]} for f, uv in face_uvs(b).items()}}
    if len(b) > 7:
        el["rotation"] = {"origin": b[7][2:5], "axis": b[7][0], "angle": b[7][1]}
    return el


def tex(mat): return f"{MOD_ID}:block/machines/{mat}"


def model_json(boxes):
    textures = {m: tex(m) for m in sorted({b[6] for b in boxes})}
    textures["particle"] = tex("casing")
    return {"parent": "block/block", "textures": textures, "elements": [element(b) for b in boxes]}


def model_id(group, part): return f"{MOD_ID}:block/machines/{group}/{part}"


def anim_spec(name):
    parts = []
    for group, src in (("common", COMMON["parts"]), (name, MACHINES[name]["parts"])):
        for pname, p in src.items():
            spec = {k: v for k, v in p.items() if k != "boxes"}
            spec.update(name=pname, model=model_id(group, pname))
            parts.append(spec)
    return {
        "machine": name, "model": model_id(name, "base"),
        "footprint": {"blocks": [1, 2, 1]},
        "sides": {"front": "north", "input": "east", "output": "west", "heat_in": "south"},
        "rpm_by_heat": RPM,
        "workpiece": PREVIEW_ITEMS[name],
        "rules": {
            "units": "px (1/16 block), degrees, ticks; right-hand-rule rotations",
            "spin": "angle = rpm * 0.3 * (spin_proc if processing else spin_idle) * ticks",
            "cycle": "only while processing: period = cycle_ticks * 32 / rpm, value = keys at "
                     "u = (t % period) / period; otherwise the first key (rest pose)",
            "heat": "angle = heat_angles[heat] + jitter * sin(1.7 * ticks)",
            "keys": "[u, value, ease_to_next], ease in linear|in|out|inout|hold",
            "transform": "translate along axis by value, or rotate about pivot by value; apply the "
                         "part's own transform, then its parent's, then FACING rotation about (8,8,8)",
        },
        "parts": parts,
    }


def dump(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=1) + "\n", encoding="utf-8")


def main():
    ap = argparse.ArgumentParser(description="Iron Age machine asset generator")
    ap.add_argument("--heat", default="heated", choices=list(RPM))
    ap.add_argument("--no-preview", action="store_true")
    args = ap.parse_args()
    models = ASSETS / "models/block/machines"
    for pname, p in COMMON["parts"].items():
        dump(models / "common" / f"{pname}.json", model_json(p["boxes"]))
    for name, m in MACHINES.items():
        dump(models / name / "base.json", model_json(COMMON["static"] + m["static"]))
        for pname, p in m["parts"].items():
            dump(models / name / f"{pname}.json", model_json(p["boxes"]))
        dump(ASSETS / "machines" / f"{name}.json", anim_spec(name))
    (ASSETS / "textures/block/machines").mkdir(parents=True, exist_ok=True)
    for mat in PALETTE:
        texture(mat).save(ASSETS / "textures/block/machines" / f"{mat}.png")
    if not args.no_preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        for name in MACHINES:
            render(scene(name, 6, args.heat))[0].save(PREVIEW / f"{name}.png")
        line = [x for n, dx in LINE for x in scene(n, 6, args.heat, dx=dx)]
        render(line, size=960)[0].save(PREVIEW / "line.png")
    print("Wrote the machine models, textures and animation specs under", ASSETS.relative_to(ARK).as_posix())


if __name__ == "__main__":
    main()
