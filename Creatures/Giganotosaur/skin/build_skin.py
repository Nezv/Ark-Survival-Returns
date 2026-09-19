#!/usr/bin/env python3
"""Paint a Minecraft-style skin for the Giganotosaur and export true per-face UVs.

The converter approximates the ARK mesh with 247 rigid cubes, each already
carrying a per-face UV slot but sampling flat palette bands. This studio keeps
the cubes and replaces the palette hack with a real 256x256 pixel-art skin:

  1. decode every cube into bind-pose world space
  2. give each of the six faces its own island in a packed atlas (classic box
     unwrap), using the exact UV axis convention Blockbench/Bedrock use per
     face key
  3. paint each island texel by mapping it back to its 3D surface point, then
     stylizing in world space: dorsal-to-ventral shading, stripes, belly
     plates, scales, a lip line with teeth, eyes, nostrils and claws
  4. export the geometry and a ready-to-open Blockbench project

Outputs live beside this script.
"""
from __future__ import annotations

import base64
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image
from scipy.spatial.transform import Rotation as R

HERE = Path(__file__).resolve().parent
CREATURE = HERE.parent
SOURCE_GEO = CREATURE / "geo" / "giganotosaur.geo.json"
SOURCE_BB = CREATURE / "Giganotosaur_GeckoLib.bbmodel"

TEX_SIZE = 256
SCALE = 2.0
PADDING = 1
BACKGROUND = (16, 21, 20)

CORNERS = np.array(
    [[0, 0, 0], [1, 0, 0], [1, 1, 0], [0, 1, 0], [0, 0, 1], [1, 0, 1], [1, 1, 1], [0, 1, 1]],
    float,
)
FACE_CORNERS = {
    "north": [0, 3, 2, 1],
    "south": [4, 5, 6, 7],
    "down": [0, 1, 5, 4],
    "up": [3, 7, 6, 2],
    "west": [0, 4, 7, 3],
    "east": [1, 2, 6, 5],
}
FACE_NORMAL = {
    "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0),
    "east": (1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0),
}

PARTS = ["bg", "torso", "neck", "tail", "head", "jaw", "tongue", "eye",
         "legl", "legr", "arml", "armr", "footl", "footr"]
PART_ID = {name: index for index, name in enumerate(PARTS)}


def rgb(code):
    return np.array([int(code[i:i + 2], 16) for i in (0, 2, 4)], np.float32)


DORSAL = rgb("24322c")
BACK = rgb("33443c")
FLANK = rgb("4b5f54")
FLANK3 = rgb("71836e")
BELLY = rgb("b4b192")
BELLY2 = rgb("cfc9a9")
STRIPE = rgb("141d1a")
JAW = rgb("b9b5a0")
JAW2 = rgb("d6cfb4")
TONGUE = rgb("9e6956")
TONGUE2 = rgb("7a4a3e")
EYE = rgb("e3b13f")
PUPIL = rgb("121614")
SOCKET = rgb("222d28")
CLAW = rgb("232320")
TEETH = rgb("eae4cf")
MOUTH = rgb("3d1b18")
RAMP_STOPS = [
    (0.00, BELLY),
    (0.08, BELLY2),
    (0.28, FLANK3),
    (0.50, FLANK),
    (0.74, BACK),
    (1.00, DORSAL),
]


def smoothstep(edge0, edge1, x):
    t = np.clip((x - edge0) / (edge1 - edge0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def ramp(d):
    d = np.clip(d, 0.0, 1.0)
    stops = np.array([s[0] for s in RAMP_STOPS])
    out = np.empty(d.shape + (3,), np.float32)
    for channel in range(3):
        values = np.array([s[1][channel] for s in RAMP_STOPS])
        out[..., channel] = np.interp(d.ravel(), stops, values).reshape(d.shape)
    return out


def hash3(x, y, z):
    value = np.sin(x * 127.1 + y * 311.7 + z * 74.7) * 43758.5453
    return value - np.floor(value)


def stripe(z):
    band = np.sin(z * (2.0 * math.pi / 10.5)) + 0.45 * np.sin(z * (2.0 * math.pi / 23.0) + 1.7)
    return smoothstep(0.28, 0.92, band)


def tint(color, target, weight):
    return color + (np.asarray(target, np.float32) - color) * weight[..., None]


def local_uv(key, s, t):
    """Unit-cube local coordinates of face key at (s, t) in [0,1], matching the
    Blockbench/Bedrock per-face UV convention (UVToLocal / mapAutoUV)."""
    if key == "north":
        return (1.0 - s, 1.0 - t, 0.0)
    if key == "south":
        return (s, 1.0 - t, 1.0)
    if key == "west":
        return (0.0, 1.0 - t, s)
    if key == "east":
        return (1.0, 1.0 - t, 1.0 - s)
    if key == "up":
        return (s, 1.0, t)
    return (s, 0.0, 1.0 - t)


def corner_st(key, unit_xyz):
    """(s, t) of a unit-cube corner for the given face key."""
    x, y, z = unit_xyz
    if key == "north":
        return 1.0 - x, 1.0 - y
    if key == "south":
        return x, 1.0 - y
    if key == "west":
        return z, 1.0 - y
    if key == "east":
        return 1.0 - z, 1.0 - y
    if key == "up":
        return x, z
    return x, 1.0 - z


def part_of(name):
    if name in ("l_eye", "r_eye"):
        return "eye"
    if name.startswith("c_tongue"):
        return "tongue"
    if name == "c_head":
        return "head"
    if name == "c_jaw":
        return "jaw"
    if name.startswith("c_neck"):
        return "neck"
    if name.startswith("c_back"):
        return "torso"
    if name.startswith("c_tail"):
        return "tail"
    if "toe" in name or "ankle" in name:
        return "foot" + name[0]
    if name[0] in "lr" and ("leg" in name or "knee" in name):
        return "leg" + name[0]
    if name[0] in "lr":
        return "arm" + name[0]
    return "torso"


def load_cubes():
    doc = json.loads(SOURCE_GEO.read_text(encoding="utf-8"))
    geometry = doc["minecraft:geometry"][0]
    cubes = []
    for bone_index, bone in enumerate(geometry["bones"]):
        for cube_index, cube in enumerate(bone.get("cubes", [])):
            size = np.array(cube["size"], float)
            origin = np.array(cube["origin"], float)
            origin[0] = -origin[0] - size[0]
            pivot = np.array(cube.get("pivot", bone["pivot"]), float) * [-1, 1, 1]
            rot = R.from_euler("xyz", np.array(cube.get("rotation", [0, 0, 0]), float) * [-1, -1, 1], degrees=True)
            cubes.append({
                "bone": bone["name"],
                "bone_index": bone_index,
                "cube_index": cube_index,
                "part": part_of(bone["name"]),
                "origin": origin,
                "size": size,
                "pivot": pivot,
                "rot": rot,
            })
    return doc, cubes


def world_from_local(cube, c):
    local = cube["origin"] + np.asarray(c, float) * cube["size"]
    return cube["rot"].apply(local - cube["pivot"]) + cube["pivot"]


def cube_corners(cube):
    return world_from_local(cube, CORNERS)


def part_points(cubes, part):
    return np.concatenate([cube_corners(c) for c in cubes if c["part"] == part])


def part_bounds(cubes):
    bounds = np.zeros((len(PARTS), 6), np.float32)
    for index in range(1, len(PARTS)):
        points = np.concatenate([cube_corners(c) for c in cubes if PART_ID[c["part"]] == index])
        bounds[index] = [points[:, 0].min(), points[:, 0].max(), points[:, 1].min(),
                         points[:, 1].max(), points[:, 2].min(), points[:, 2].max()]
    return bounds


def face_frames(cubes):
    faces = []
    for cube in cubes:
        for key in FACE_CORNERS:
            p00 = world_from_local(cube, local_uv(key, 0.0, 0.0))
            p10 = world_from_local(cube, local_uv(key, 1.0, 0.0))
            p01 = world_from_local(cube, local_uv(key, 0.0, 1.0))
            p11 = world_from_local(cube, local_uv(key, 1.0, 1.0))
            normal = cube["rot"].apply(np.array(FACE_NORMAL[key], float))
            normal = normal / np.linalg.norm(normal)
            faces.append({
                "cube": cube, "key": key, "corner": (p00, p10, p11, p01),
                "normal": normal.astype(np.float32),
                "width": max(1, int(round(np.linalg.norm(p10 - p00) * SCALE))),
                "height": max(1, int(round(np.linalg.norm(p01 - p00) * SCALE))),
            })
    return faces


def pack_faces(faces, width):
    order = sorted(range(len(faces)), key=lambda i: (-faces[i]["height"], -faces[i]["width"], i))
    x = y = shelf = 0
    for index in order:
        face = faces[index]
        if x + face["width"] > width:
            y += shelf + PADDING
            x = 0
            shelf = 0
        face["x"], face["y"] = x, y
        x += face["width"] + PADDING
        shelf = max(shelf, face["height"])
    return y + shelf


def lip_profiles(cubes):
    def samples(part, want_up):
        out = []
        for cube in cubes:
            if cube["part"] != part:
                continue
            for key in FACE_CORNERS:
                normal = cube["rot"].apply(np.array(FACE_NORMAL[key], float))
                if (want_up and normal[1] > 0.35) or ((not want_up) and normal[1] < -0.35):
                    for s in (0.15, 0.5, 0.85):
                        for t in (0.15, 0.5, 0.85):
                            out.append(world_from_local(cube, local_uv(key, s, t)))
        return np.array(out)

    head_bottom = samples("head", want_up=False)
    jaw_top = samples("jaw", want_up=True)
    z0, z1 = 20.0, 29.8
    centers = np.linspace(z0 + 0.3, z1 - 0.3, 22)

    def envelope(points, mode):
        values = []
        for center in centers:
            window = points[np.abs(points[:, 2] - center) < 0.9]
            if len(window):
                values.append(window[:, 1].min() if mode == "min" else window[:, 1].max())
            else:
                values.append(np.nan)
        values = np.array(values)
        good = ~np.isnan(values)
        filled = np.interp(centers, centers[good], values[good])
        kernel = np.array([0.25, 0.5, 0.25])
        return np.convolve(filled, kernel, mode="same")

    return {"z0": z0, "z1": z1, "centers": centers,
            "lip": envelope(head_bottom, "min"),
            "jaw_top": envelope(jaw_top, "max")}


def face_points(face):
    width, height = face["width"], face["height"]
    s = ((np.arange(width) + 0.5) / width)[None, :]
    t = ((np.arange(height) + 0.5) / height)[:, None]
    p00, p10, p11, p01 = face["corner"]
    points = ((1 - s)[..., None] * (1 - t)[..., None] * p00
              + s[..., None] * (1 - t)[..., None] * p10
              + s[..., None] * t[..., None] * p11
              + (1 - s)[..., None] * t[..., None] * p01).reshape(-1, 3)
    normals = np.repeat(face["normal"][None, :], len(points), axis=0)
    part_ids = np.full(len(points), PART_ID[face["cube"]["part"]], np.int16)
    return points, normals, part_ids


def base_skin(points, normals, part_ids, bounds):
    x, y, z = points[:, 0], points[:, 1], points[:, 2]
    pid = np.clip(part_ids, 0, len(PARTS) - 1)
    d = np.clip((y - bounds[pid, 2]) / np.maximum(bounds[pid, 3] - bounds[pid, 2], 1e-6), 0.0, 1.0)
    color = ramp(d)

    is_head = part_ids == PART_ID["head"]
    is_jaw = part_ids == PART_ID["jaw"]
    is_tongue = part_ids == PART_ID["tongue"]
    is_torso = part_ids == PART_ID["torso"]
    is_neck = part_ids == PART_ID["neck"]
    is_tail = part_ids == PART_ID["tail"]
    is_leg = (part_ids == PART_ID["legl"]) | (part_ids == PART_ID["legr"])
    is_arm = (part_ids == PART_ID["arml"]) | (part_ids == PART_ID["armr"])
    is_foot = (part_ids == PART_ID["footl"]) | (part_ids == PART_ID["footr"])
    body = is_torso | is_neck | is_tail
    limb = is_leg | is_arm | is_foot
    up = normals[:, 1] > 0.55
    down = normals[:, 1] < -0.55

    color = np.where(limb[:, None], ramp(0.20 + 0.55 * d), color)
    jaw_base = JAW + (JAW2 - JAW) * (0.45 - 0.45 * d)[:, None]
    color = np.where(is_jaw[:, None], jaw_base, color)
    tongue = TONGUE + (TONGUE2 - TONGUE) * hash3(x * 1.1, y, z)[:, None] * 0.55
    color = np.where(is_tongue[:, None], tongue, color)
    color = tint(color, DORSAL, 0.22 * is_head * smoothstep(0.45, 1.0, d))
    color = tint(color, DORSAL, 0.18 * is_leg * smoothstep(0.55, 1.0, d))

    stripes = stripe(z + 0.7 * np.sin(y * 0.85))
    color = tint(color, STRIPE, 1.0 * stripes * smoothstep(0.20, 0.58, d) * body)
    color = tint(color, STRIPE, 0.42 * stripe(y * 1.15) * smoothstep(0.30, 0.80, d) * is_leg)
    color = tint(color, STRIPE, 0.45 * stripe(z * 1.45 + 3.7) * smoothstep(0.22, 0.60, d) * is_tail)
    color = tint(color, DORSAL, 0.30 * up * body)
    color = tint(color, DORSAL, 0.30 * up * is_head)
    row_line = smoothstep(0.10, 0.0, np.abs((y * 0.75) % 1.6 - 0.8))
    color = tint(color, DORSAL, 0.09 * row_line * body)
    color = tint(color, DORSAL, 0.16 * is_neck * smoothstep(0.10, 0.0, np.abs((y * 0.9) % 2.0 - 1.0)))
    plates = smoothstep(0.12, 0.0, np.abs((y * 1.1) % 2.4 - 1.2))
    color = tint(color, DORSAL, 0.16 * plates * smoothstep(0.30, 0.08, d) * body)
    color = tint(color, BELLY2, 0.30 * down * body)
    color = tint(color, JAW2, 0.30 * is_head * smoothstep(0.42, 0.10, d))
    color = tint(color, CLAW, 0.40 * is_arm * smoothstep(0.30, 0.0, d))

    scutes = up & body & (np.abs(x) < 0.85) & ((np.floor(z * 0.9) % 3.0) < 1.0)
    color = tint(color, STRIPE, 0.35 * scutes)
    belly_bands = down & body & ((np.floor(z * 0.9) % 2.0) < 1.0)
    color = tint(color, BELLY, 0.30 * belly_bands)

    patch = hash3(np.floor(x * 0.7), np.floor(y * 0.7), np.floor(z * 0.7))
    color = tint(color, DORSAL, 0.14 * (patch > 0.80) * (body | limb))
    color = tint(color, FLANK3, 0.12 * (patch < 0.18) * body)
    grain = (hash3(np.floor(x * 1.6), np.floor(y * 1.6), np.floor(z * 1.6)) - 0.5) * 0.12
    grain += (hash3(np.floor(x * 0.6) + 7.0, np.floor(y * 0.6), np.floor(z * 0.6)) - 0.5) * 0.10
    color = color * (1.0 + grain[:, None] * (body | limb | is_head | is_jaw)[:, None])
    scales = body & (hash3(np.floor(x * 0.9) + 3.0, np.floor(y * 0.9), np.floor(z * 0.9)) > 0.82)
    color = tint(color, DORSAL, 0.28 * scales)
    return color


def add_details(color, points, normals, part_ids, features):
    x, y, z = points[:, 0], points[:, 1], points[:, 2]
    is_head = part_ids == PART_ID["head"]
    is_jaw = part_ids == PART_ID["jaw"]
    is_foot = (part_ids == PART_ID["footl"]) | (part_ids == PART_ID["footr"])
    in_zone = (z > features["mouth_z0"]) & (z < features["mouth_z1"])
    centers = features["lip_centers"]

    for center in features["eye_centers"]:
        sign = np.sign(center[0])
        dyz = np.sqrt((y - center[1]) ** 2 + (z - center[2]) ** 2)
        region = is_head & (sign * x > 1.0) & (dyz < 1.3)
        if region.any():
            outer = float(np.max(sign * x[region]))
            surface = region & (sign * x > outer - 0.8)
            socket = surface & (dyz < 1.05)
            color = np.where(socket[:, None], SOCKET, color)
            color = np.where((socket & (dyz < 0.66))[:, None], EYE, color)
            color = np.where((socket & (dyz < 0.28))[:, None], PUPIL, color)
            color = np.where((socket & (dyz > 0.95))[:, None], DORSAL, color)

    for center in features["nostrils"]:
        sign = np.sign(center[0])
        dyz = np.sqrt((y - center[1]) ** 2 + (z - center[2]) ** 2)
        color = np.where((is_head & (sign * x > 1.0) & (dyz < 0.40))[:, None], SOCKET, color)

    eye_y, eye_z = features["eye_centers"][0][1], features["eye_centers"][0][2]
    mask_stripe = is_head & (np.abs(x) > 1.1) & (np.abs(y - eye_y) < 0.75) & (z < eye_z - 0.8)
    color = tint(color, STRIPE, 0.35 * mask_stripe)

    head_lip = np.interp(z, centers, features["lip"])
    jaw_lip = np.interp(z, centers, features["jaw_top"])
    upper_lip = is_head & in_zone & (np.abs(y - head_lip) < 0.50)
    lower_lip = is_jaw & in_zone & (np.abs(y - jaw_lip) < 0.50)
    lips = upper_lip | lower_lip
    color = np.where(lips[:, None], MOUTH, color)
    phase = np.floor(z * 1.7) + np.where(is_jaw, 0.85, 0.0)
    color = np.where((lips & ((phase % 2.0) < 1.0))[:, None], TEETH, color)

    claws = is_foot & ((y < features["foot_y_min"] + 0.7) | (z > features["foot_z_max"] - 0.9))
    color = np.where(claws[:, None], CLAW, color)
    return color


def compose(cubes, faces, bounds, features):
    canvas = np.zeros((TEX_SIZE, TEX_SIZE, 3), np.float32)
    canvas[:] = BACKGROUND
    light = np.array([-0.35, 0.72, 0.60], np.float32)
    light /= np.linalg.norm(light)
    for face in faces:
        points, normals, part_ids = face_points(face)
        color = base_skin(points, normals, part_ids, bounds)
        color = add_details(color, points, normals, part_ids, features)
        color = color * (0.90 + 0.10 * np.clip(normals @ light, 0.0, 1.0))[:, None]
        grid = np.clip(color.reshape(face["height"], face["width"], 3), 0, 255).astype(np.uint8)
        canvas[face["y"]:face["y"] + face["height"], face["x"]:face["x"] + face["width"]] = grid
    return canvas


def dilate(canvas, faces):
    for face in faces:
        x, y, w, h = face["x"], face["y"], face["width"], face["height"]
        if y > 0:
            canvas[y - 1, x:x + w] = canvas[y, x:x + w]
        if y + h < TEX_SIZE:
            canvas[y + h, x:x + w] = canvas[y + h - 1, x:x + w]
        if x > 0:
            canvas[y:y + h, x - 1] = canvas[y:y + h, x]
        if x + w < TEX_SIZE:
            canvas[y:y + h, x + w] = canvas[y:y + h, x + w - 1]
    return canvas


def assign_uvs(faces):
    for face in faces:
        face["cube"].setdefault("uv", {})[face["key"]] = (face["x"], face["y"], face["width"], face["height"])


def write_geo(cubes):
    doc = json.loads(SOURCE_GEO.read_text(encoding="utf-8"))
    geometry = doc["minecraft:geometry"][0]
    geometry["description"]["texture_width"] = TEX_SIZE
    geometry["description"]["texture_height"] = TEX_SIZE
    seen = {(cube["bone_index"], cube["cube_index"]): cube for cube in cubes}
    for bone_index, bone in enumerate(geometry["bones"]):
        for cube_index, target in enumerate(bone.get("cubes", [])):
            cube = seen[(bone_index, cube_index)]
            target["uv"] = {
                key: {"uv": [rect[0], rect[1]], "uv_size": [rect[2], rect[3]]}
                for key, rect in cube["uv"].items()
            }
    path = HERE / "giganotosaurus.geo.json"
    path.write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
    return path


def write_bbmodel(cubes, texture_path):
    doc = json.loads(SOURCE_BB.read_text(encoding="utf-8"))
    per_bone = {}
    for element in doc["elements"]:
        per_bone.setdefault(element["name"].split("/")[0].strip(), []).append(element)
    for cube in cubes:
        element = per_bone[cube["bone"]][cube["cube_index"]]
        for key, (u0, v0, du, dv) in cube["uv"].items():
            element["faces"][key]["uv"] = [u0, v0, u0 + du, v0 + dv]
    encoded = base64.b64encode(texture_path.read_bytes()).decode()
    texture = doc["textures"][0]
    texture.update({"width": TEX_SIZE, "height": TEX_SIZE, "uv_width": TEX_SIZE,
                    "uv_height": TEX_SIZE, "source": "data:image/png;base64," + encoded})
    doc.setdefault("resolution", {}).update({"width": TEX_SIZE, "height": TEX_SIZE})
    path = HERE / "Giganotosaurus_Textured.bbmodel"
    path.write_text(json.dumps(doc, separators=(",", ":")), encoding="utf-8")
    return path


def write_report(cubes, faces, packed_height, features):
    in_bounds = all(0 <= f["x"] and 0 <= f["y"] and f["x"] + f["width"] <= TEX_SIZE
                    and f["y"] + f["height"] <= TEX_SIZE for f in faces)
    used = sum(f["width"] * f["height"] for f in faces)
    report = {
        "texture": {"file": "giganotosaurus.png", "size": [TEX_SIZE, TEX_SIZE], "pixels_per_unit": SCALE},
        "cubes": len(cubes),
        "faces": len(faces),
        "atlas": {"packed_height": packed_height, "used_pixels": used,
                  "coverage": round(used / (TEX_SIZE * TEX_SIZE), 4)},
        "parts": {name: sum(1 for cube in cubes if cube["part"] == name)
                  for name in PARTS if name != "bg"},
        "features": {
            "eye_centers": [[round(float(v), 3) for v in c] for c in features["eye_centers"]],
            "lip_z_range": [round(features["mouth_z0"], 3), round(features["mouth_z1"], 3)],
        },
        "checks": {
            "all_faces_in_bounds": in_bounds,
            "all_faces_positive_size": all(f["width"] >= 1 and f["height"] >= 1 for f in faces),
            "face_count_matches": len(faces) == len(cubes) * 6,
            "atlas_fits": packed_height <= TEX_SIZE,
        },
    }
    path = HERE / "skin_report.json"
    path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return report


def main():
    doc, cubes = load_cubes()
    bounds = part_bounds(cubes)
    faces = face_frames(cubes)
    packed_height = pack_faces(faces, TEX_SIZE)
    assert packed_height <= TEX_SIZE, f"atlas needs {packed_height}px height; raise TEX_SIZE"

    eyes = part_points(cubes, "eye")
    left_eye = eyes[eyes[:, 0] < 0].mean(axis=0)
    right_eye = eyes[eyes[:, 0] > 0].mean(axis=0)
    head = part_points(cubes, "head")
    foot = np.concatenate([part_points(cubes, "footl"), part_points(cubes, "footr")])
    profile = lip_profiles(cubes)
    features = {
        "eye_centers": [left_eye, right_eye],
        "nostrils": [np.array([sx * 1.35, right_eye[1] - 1.35, head[:, 2].max() - 1.35]) for sx in (-1, 1)],
        "mouth_z0": profile["z0"],
        "mouth_z1": profile["z1"],
        "lip_centers": profile["centers"],
        "lip": profile["lip"],
        "jaw_top": profile["jaw_top"],
        "foot_y_min": float(foot[:, 1].min()),
        "foot_z_max": float(foot[:, 2].max()),
    }
    canvas = np.clip(dilate(compose(cubes, faces, bounds, features), faces), 0, 255).astype(np.uint8)
    texture = Image.fromarray(canvas, "RGB")
    texture_path = HERE / "giganotosaurus.png"
    texture.save(texture_path)

    assign_uvs(faces)
    geo_path = write_geo(cubes)
    bb_path = write_bbmodel(cubes, texture_path)
    report = write_report(cubes, faces, packed_height, features)
    print(f"texture {texture_path.name}: {TEX_SIZE}x{TEX_SIZE}, atlas used {report['atlas']['coverage']:.1%}")
    print(f"geometry {geo_path.name}: {len(cubes)} cubes, {len(faces)} face islands")
    print(f"blockbench {bb_path.name}")
    print("checks:", report["checks"])

    import preview_skin
    preview_skin.main()


if __name__ == "__main__":
    main()
