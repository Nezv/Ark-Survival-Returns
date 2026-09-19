#!/usr/bin/env python3
"""Reusable painted-skin studio for the fitted ARK cube models.

Upgraded pass over the original Giganotosaur pipeline. New in this version:

* body-centerline field (arc length ``s`` + dorsal radial ``r``) so stripes and
  the dorsal/belly gradient flow across tail, torso, neck and head;
* adaptive detail density (head/jaw/tongue/eye get larger islands, tail
  tips/toes stay cheap);
* rig-derived facial frame (nose axis, eye disc, nostrils) instead of
  hard-coded world coordinates;
* per-species style config (palette, stripe frequency, belly width, scutes,
  region overrides, detail scales);
* clustered Bayer dithering into a controlled 16-32 colour palette;
* expanded validation: island overlap, border seam deltas, palette size,
  bilateral symmetry and animated-pose previews.

Outputs go to a new folder next to the model; source files are never touched.
"""
from __future__ import annotations

import base64
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy.spatial.transform import Rotation as R

BACKGROUND = (16, 21, 20)
TEX_SIZES = (256, 384, 512)
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
         "legl", "legr", "arml", "armr", "footl", "footr", "claw", "horn",
         "feather", "other"]
PART_ID = {name: index for index, name in enumerate(PARTS)}
LIGHT = np.array([-0.35, 0.72, 0.60], np.float32)
LIGHT /= np.linalg.norm(LIGHT)
BAYER4 = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], np.float32) / 16.0

DEFAULT_STYLE = {
    "base_scale": 2.0,
    "stripe_period": 10.5,
    "stripe_sharp": (0.34, 0.96),
    "stripe_span": (0.28, 0.66),
    "stripe_weight": 0.86,
    "stripe_blend": 0.35,
    "leg_stripe": 0.36,
    "tail_stripe": 0.40,
    "belly_width": 0.30,
    "plate_period": 2.4,
    "plates": 0.14,
    "scute_period": 3.0,
    "scutes": 0.25,
    "ridge": 0.22,
    "scales": 0.25,
    "scale_threshold": 0.84,
    "neck_folds": 0.16,
    "row_lines": 0.09,
    "grain_fine": 0.06,
    "grain_coarse": 0.05,
    "dorsal_wash": 0.28,
    "quantize": True,
    "dither": 5.0,
    "detail_scale": {},
    "min_island": {},
    "region_overrides": {},
    "centerline_chain": None,
    "eye": {
        "socket_r": 1.05, "iris_r": 0.66, "pupil_r": 0.28,
        "lateral_fraction": 0.35, "outer_slab": 0.8,
        "nostril_back": 0.115, "nostril_lateral": 0.44,
        "nostril_down": 0.18, "nostril_r": 0.40,
    },
}
GIGA_STYLE = {
    **DEFAULT_STYLE,
    "base_scale": 1.8,
    "centerline_chain": ["c_tail5", "c_tail4", "c_tail3", "c_tail2", "c_tail1",
                         "c_back1", "c_back2", "c_back3", "c_back4",
                         "c_neck1", "c_neck2", "c_neck3", "c_head"],
    "detail_scale": {"head": 3.4, "jaw": 3.0, "tongue": 2.6, "eye": 4.0, "claw": 3.0},
    "min_island": {"eye": 3, "tongue": 2, "head": 2, "jaw": 2, "legl": 2, "legr": 2,
                   "arml": 2, "armr": 2, "footl": 2, "footr": 2, "tail": 2, "claw": 2},
    "region_overrides": {"l_eye": "eye", "r_eye": "eye", "l_upperEye": "head", "r_upperEye": "head",
                         "l_lowerEye": "head", "r_lowerEye": "head"},
}
SPECIES_STYLE = {"Giganotosaur": GIGA_STYLE}


def rgb(code):
    code = str(code).lstrip("#")
    return np.array([int(code[i:i + 2], 16) for i in (0, 2, 4)], np.float32)


def mix(a, b, t):
    t = np.asarray(t, np.float32)
    if t.ndim == 1:
        t = t[:, None]
    return np.asarray(a, np.float32) * (1 - t) + np.asarray(b, np.float32) * t


def darken(color, amount):
    return np.asarray(color, np.float32) * (1 - amount)


def lighten(color, amount):
    return mix(color, (255, 255, 255), amount)


def palette_roles(colors):
    p = [rgb(c) for c in colors]
    return {
        "DORSAL": darken(p[2], 0.20),
        "BACK": mix(p[2], p[0], 0.45),
        "FLANK": p[0],
        "FLANK3": p[1],
        "BELLY": lighten(p[3], 0.12),
        "BELLY2": p[4],
        "STRIPE": darken(p[5], 0.10),
        "JAW": p[3],
        "JAW2": lighten(p[3], 0.25),
        "TONGUE": p[7],
        "TONGUE2": darken(p[7], 0.25),
        "EYE": p[6],
        "PUPIL": p[5],
        "SOCKET": darken(p[2], 0.45),
        "CLAW": mix(p[5], (230, 230, 220), 0.10),
        "TEETH": lighten(p[4], 0.30),
        "MOUTH": darken(p[7], 0.55),
        "HORN": mix(p[6], p[7], 0.40),
        "FEATHER": lighten(p[1], 0.12),
    }


RAMP_POINTS = (0.00, 0.10, 0.28, 0.50, 0.74, 1.00)
RAMP_ROLES = ("BELLY", "BELLY2", "FLANK3", "FLANK", "BACK", "DORSAL")


def smoothstep(edge0, edge1, x):
    t = np.clip((x - edge0) / (edge1 - edge0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def ramp(d, palette):
    d = np.clip(d, 0.0, 1.0)
    out = np.empty(d.shape + (3,), np.float32)
    for channel in range(3):
        values = np.array([palette[role][channel] for role in RAMP_ROLES])
        out[..., channel] = np.interp(d.ravel(), RAMP_POINTS, values).reshape(d.shape)
    return out


def hash3(x, y, z):
    value = np.sin(x * 127.1 + y * 311.7 + z * 74.7) * 43758.5453
    return value - np.floor(value)


def stripe(value, style):
    period = style["stripe_period"]
    band = np.sin(value * (2.0 * math.pi / period)) + 0.45 * np.sin(value * (2.0 * math.pi / 23.0) + 1.7)
    return smoothstep(style["stripe_sharp"][0], style["stripe_sharp"][1], band)


def tint(color, target, weight):
    return color + (np.asarray(target, np.float32) - color) * weight[..., None]


def side_of(name):
    low = name.lower()
    if low.startswith("l_") or low.startswith("lft_") or low.endswith("_l") or "_l_" in low:
        return "l"
    if low.startswith("r_") or low.startswith("rht_") or low.endswith("_r") or "_r_" in low:
        return "r"
    return ""


def part_of(name, overrides=None):
    if overrides and name in overrides:
        return overrides[name]
    low = name.lower()
    side = side_of(name)
    if "eyelid" in low or "lidmain" in low or low in ("l_uppereye", "l_lowereye", "r_uppereye", "r_lowereye"):
        return "head"
    if "eye" in low:
        return "eye"
    if "tongue" in low:
        return "tongue"
    if "jaw" in low or "lip" in low:
        return "jaw"
    if "head" in low or "skull" in low or "cnt_head" in low:
        return "head"
    if "neck" in low or "dewlap" in low:
        return "neck"
    if "tail" in low:
        return "tail"
    if "claw" in low:
        return "claw"
    if any(token in low for token in ("horn", "frill", "crest", "tusk", "spike", "antler")):
        return "horn"
    if "feather" in low or "wing" in low:
        return "feather"
    if "toe" in low or "ankle" in low or "heel" in low:
        return "foot" + side
    if any(token in low for token in ("leg", "knee", "thigh", "shin")):
        return "leg" + side
    if any(token in low for token in ("arm", "finger", "wrist", "elbow", "shoulder",
                                      "scapula", "clavicle", "thumb", "index", "middle",
                                      "ring", "pinky", "hand")):
        return "arm" + side
    if any(token in low for token in ("back", "spine", "chest", "belly", "waist",
                                      "rump", "torso", "fat", "hip", "root")):
        return "torso"
    return "other"


def local_uv(key, s, t):
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


def creature_files(creature_dir):
    geos = sorted((creature_dir / "geo").glob("*.geo.json"))
    models = sorted(creature_dir.glob("*.bbmodel"))
    assert geos, f"no geo in {creature_dir}"
    assert models, f"no bbmodel in {creature_dir}"
    return geos[0], models[0]


def load_cubes(geo_path, style=None):
    style = style or DEFAULT_STYLE
    doc = json.loads(Path(geo_path).read_text(encoding="utf-8"))
    geometry = doc["minecraft:geometry"][0]
    bones = geometry["bones"]
    names = [bone["name"] for bone in bones]
    parents = [names.index(bone["parent"]) if "parent" in bone else -1 for bone in bones]
    pivots = np.array([bone["pivot"] for bone in bones], float) * [-1, 1, 1]
    rotations = np.array([bone.get("rotation", [0, 0, 0]) for bone in bones], float) * [-1, -1, 1]
    world_pivots, world_matrices = [], []
    for index, parent in enumerate(parents):
        matrix = R.from_euler("xyz", rotations[index], degrees=True).as_matrix()
        if parent < 0:
            world_pivots.append(pivots[index])
            world_matrices.append(matrix)
        else:
            world_pivots.append(world_pivots[parent] + world_matrices[parent] @ (pivots[index] - pivots[parent]))
            world_matrices.append(world_matrices[parent] @ matrix)
    cubes = []
    for bone_index, bone in enumerate(bones):
        for cube_index, cube in enumerate(bone.get("cubes", [])):
            size = np.array(cube["size"], float)
            origin = np.array(cube["origin"], float)
            origin[0] = -origin[0] - size[0]
            pivot = np.array(cube.get("pivot", bone["pivot"]), float) * [-1, 1, 1]
            rot = R.from_euler("xyz", np.array(cube.get("rotation", [0, 0, 0]), float) * [-1, -1, 1], degrees=True)
            model_corners = rot.apply(origin + CORNERS * size - pivot) + pivot
            cubes.append({"bone": bone["name"], "bone_index": bone_index, "cube_index": cube_index,
                          "part": part_of(bone["name"], style.get("region_overrides")), "origin": origin,
                          "size": size, "pivot": pivot, "rot": rot, "model_corners": model_corners,
                          "source_uv": cube.get("uv"),
                          "bone_world": world_pivots[bone_index],
                          "bone_matrix": world_matrices[bone_index],
                          "bone_pivot": pivots[bone_index]})
    assert cubes, f"no cubes in {geo_path}"
    skeleton = {"names": names, "parents": parents, "pivots": pivots, "rotations": rotations}
    return doc, cubes, skeleton


def world_from_local(cube, c):
    local = cube["origin"] + np.asarray(c, float) * cube["size"]
    rotated = cube["rot"].apply(local - cube["pivot"]) + cube["pivot"]
    return cube["bone_world"] + (rotated - cube["bone_pivot"]) @ cube["bone_matrix"].T


def cube_corners(cube):
    return cube["bone_world"] + (cube["model_corners"] - cube["bone_pivot"]) @ cube["bone_matrix"].T


def part_points(cubes, part):
    points = [cube_corners(c) for c in cubes if c["part"] == part]
    return np.concatenate(points) if points else np.zeros((0, 3))


def part_bounds(cubes):
    bounds = np.zeros((len(PARTS), 6), np.float32)
    for index in range(1, len(PARTS)):
        points = [cube_corners(c) for c in cubes if PART_ID[c["part"]] == index]
        if not points:
            continue
        points = np.concatenate(points)
        bounds[index] = [points[:, 0].min(), points[:, 0].max(), points[:, 1].min(),
                         points[:, 1].max(), points[:, 2].min(), points[:, 2].max()]
    return bounds


class BodyField:
    """Arc-length / dorsal-radial coordinates along the tail-to-snout spine."""

    def __init__(self, cubes, chain=None):
        pivots = {}
        for cube in cubes:
            pivots.setdefault(cube["bone"], np.asarray(cube["bone_world"], float))
        if chain:
            points = np.array([pivots[name] for name in chain if name in pivots], float)
        else:
            points = self._infer_chain(cubes, pivots)
        if len(points) < 2:
            self.points = None
            return
        tail = part_points(cubes, "tail")
        head = part_points(cubes, "head")
        if len(tail):
            direction = points[0] - points[1]
            direction = direction / max(np.linalg.norm(direction), 1e-9)
            points = np.vstack([tail[np.argmax(tail @ direction)], points])
        if len(head):
            direction = points[-1] - points[-2]
            direction = direction / max(np.linalg.norm(direction), 1e-9)
            points = np.vstack([points, head[np.argmax(head @ direction)]])
        if len(points) >= 5:
            kernel = np.array([0.25, 0.5, 0.25])
            padded = np.vstack([points[:1], points, points[-1:]])
            points = np.column_stack([np.convolve(padded[:, i], kernel, "valid") for i in range(3)])
        self.points = points
        self.segments = points[1:] - points[:-1]
        self.seg_len = np.linalg.norm(self.segments, axis=1)
        self.cum = np.concatenate([[0.0], np.cumsum(self.seg_len)])
        up = np.array([0.0, 1.0, 0.0])
        dorsal = []
        for tangent in self.segments:
            length = np.linalg.norm(tangent)
            unit = tangent / length if length > 1e-9 else np.array([0.0, 0.0, 1.0])
            direction = up - unit * float(unit @ up)
            norm = np.linalg.norm(direction)
            dorsal.append(direction / norm if norm > 1e-6 else np.array([0.0, 1.0, 0.0]))
        self.dorsal = np.array(dorsal)
        body = np.concatenate([cube_corners(c) for c in cubes
                               if c["part"] in ("torso", "neck", "tail", "head", "jaw")]
                              or [np.zeros((0, 3))])
        if len(body):
            s, r = self.coords(body)
            bins = np.linspace(0.0, self.cum[-1], 41)
            centers = (bins[:-1] + bins[1:]) / 2
            rmin = np.full(len(centers), np.nan)
            rmax = np.full(len(centers), np.nan)
            idx = np.clip(np.digitize(s, bins) - 1, 0, len(centers) - 1)
            for i in range(len(centers)):
                mask = idx == i
                if mask.sum() >= 3:
                    rmin[i] = np.percentile(r[mask], 2)
                    rmax[i] = np.percentile(r[mask], 98)
            good = ~np.isnan(rmin) & ~np.isnan(rmax)
            if good.sum() >= 2:
                rmin = np.interp(centers, centers[good], rmin[good])
                rmax = np.interp(centers, centers[good], rmax[good])
                kernel = np.array([0.25, 0.5, 0.25])
                rmin = np.convolve(np.pad(rmin, 1, mode="edge"), kernel, "valid")
                rmax = np.convolve(np.pad(rmax, 1, mode="edge"), kernel, "valid")
                self.centers, self.rmin, self.rmax = centers, rmin, rmax
            else:
                self.centers = None
        else:
            self.centers = None

    def _infer_chain(self, cubes, pivots):
        body = []
        for cube in cubes:
            if cube["part"] in ("torso", "neck", "tail", "head"):
                body.append((cube["bone"], np.asarray(cube["bone_world"], float)))
        body.sort(key=lambda item: item[1][2])
        unique, seen = [], set()
        for name, point in body:
            if name not in seen:
                seen.add(name)
                unique.append(point)
        return np.array(unique, float)

    def coords(self, points):
        P = np.asarray(points, float)
        best_dist = np.full(len(P), np.inf)
        best_t = np.zeros(len(P))
        best_i = np.zeros(len(P), dtype=int)
        for i in range(len(self.segments)):
            delta = P - self.points[i]
            t = np.clip((delta @ self.segments[i]) / max(self.seg_len[i] ** 2, 1e-9), 0.0, 1.0)
            closest = self.points[i] + t[:, None] * self.segments[i]
            dist2 = ((P - closest) ** 2).sum(1)
            update = dist2 < best_dist
            best_dist[update] = dist2[update]
            best_t[update] = t[update]
            best_i[update] = i
        s = self.cum[best_i] + best_t * self.seg_len[best_i]
        closest = self.points[best_i] + best_t[:, None] * self.segments[best_i]
        r = ((P - closest) * self.dorsal[best_i]).sum(1)
        return s, r

    def dorsal_weight(self, points):
        if self.points is None or self.centers is None:
            return None, None
        s, r = self.coords(points)
        lo = np.interp(s, self.centers, self.rmin)
        hi = np.interp(s, self.centers, self.rmax)
        d = np.clip((r - lo) / np.maximum(hi - lo, 1e-6), 0.0, 1.0)
        return s, d


class HeadFrame:
    """Rig-derived head frame: forward snout axis, up, lateral and extents."""

    def __init__(self, cubes):
        head = part_points(cubes, "head")
        if not len(head):
            self.valid = False
            return
        center = head.mean(0)
        _, _, vt = np.linalg.svd(head - center, full_matrices=False)
        forward = vt[0]
        necks = np.array([cube["bone_world"] for cube in cubes if cube["part"] == "neck"], float)
        reference = center - necks.mean(0) if len(necks) else np.array([0.0, 0.0, -1.0])
        if float(forward @ reference) < 0:
            forward = -forward
        up_ref = np.array([0.0, 1.0, 0.0])
        up = up_ref - forward * float(forward @ up_ref)
        length = np.linalg.norm(up)
        up = up / length if length > 1e-6 else vt[1]
        lateral = np.cross(forward, up)
        lateral /= max(np.linalg.norm(lateral), 1e-9)
        proj_f = head @ forward
        proj_u = head @ up
        proj_l = head @ lateral
        self.valid = True
        self.center = center
        self.forward = forward
        self.up = up
        self.lateral = lateral
        self.length = float(proj_f.max() - proj_f.min())
        self.half_width = float((proj_l.max() - proj_l.min()) / 2)
        self.half_height = float((proj_u.max() - proj_u.min()) / 2)
        self.snout = head[int(np.argmax(proj_f))]

    def coords(self, points):
        delta = np.asarray(points, float) - self.center
        return delta @ self.forward, delta @ self.up, delta @ self.lateral


def face_frames(cubes, style=None):
    style = style or DEFAULT_STYLE
    base = style["base_scale"]
    detail = style["detail_scale"]
    minimum = style["min_island"]
    faces = []
    for cube in cubes:
        scale = base * detail.get(cube["part"], 1.0)
        for key in FACE_CORNERS:
            p00 = world_from_local(cube, local_uv(key, 0.0, 0.0))
            p10 = world_from_local(cube, local_uv(key, 1.0, 0.0))
            p01 = world_from_local(cube, local_uv(key, 0.0, 1.0))
            p11 = world_from_local(cube, local_uv(key, 1.0, 1.0))
            normal = cube["rot"].apply(np.array(FACE_NORMAL[key], float))
            normal = normal / np.linalg.norm(normal)
            faces.append({"cube": cube, "key": key, "corner": (p00, p10, p11, p01),
                          "normal": normal.astype(np.float32),
                          "width": max(minimum.get(cube["part"], 1), int(round(np.linalg.norm(p10 - p00) * scale))),
                          "height": max(minimum.get(cube["part"], 1), int(round(np.linalg.norm(p01 - p00) * scale)))})
    return faces


def pack_faces(faces, width):
    order = sorted(range(len(faces)), key=lambda i: (-faces[i]["height"], -faces[i]["width"], i))
    x = y = shelf = 0
    for index in order:
        face = faces[index]
        if x + face["width"] > width:
            y += shelf + 1
            x = 0
            shelf = 0
        face["x"], face["y"] = x, y
        x += face["width"] + 1
        shelf = max(shelf, face["height"])
    return y + shelf


def choose_tex_size(faces):
    for size in TEX_SIZES:
        if pack_faces(faces, size) <= size:
            return size
    raise ValueError("atlas does not fit the largest texture size")


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
    if not len(head_bottom) or not len(jaw_top):
        return None
    z0 = float(np.concatenate([head_bottom, jaw_top])[:, 2].min()) + 2.0
    z1 = float(head_bottom[:, 2].max())
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
        return np.convolve(filled, np.array([0.25, 0.5, 0.25]), mode="same")

    return {"z0": z0, "z1": z1, "centers": centers,
            "lip": envelope(head_bottom, "min"), "jaw_top": envelope(jaw_top, "max")}


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


def base_skin(points, normals, part_ids, bounds, palette, style, field):
    x, y, z = points[:, 0], points[:, 1], points[:, 2]
    pid = np.clip(part_ids, 0, len(PARTS) - 1)
    local_d = np.clip((y - bounds[pid, 2]) / np.maximum(bounds[pid, 3] - bounds[pid, 2], 1e-6), 0.0, 1.0)
    color = ramp(local_d, palette)

    is_head = part_ids == PART_ID["head"]
    is_jaw = part_ids == PART_ID["jaw"]
    is_tongue = part_ids == PART_ID["tongue"]
    is_torso = part_ids == PART_ID["torso"]
    is_neck = part_ids == PART_ID["neck"]
    is_tail = part_ids == PART_ID["tail"]
    is_leg = (part_ids == PART_ID["legl"]) | (part_ids == PART_ID["legr"])
    is_arm = (part_ids == PART_ID["arml"]) | (part_ids == PART_ID["armr"])
    is_foot = (part_ids == PART_ID["footl"]) | (part_ids == PART_ID["footr"])
    is_claw = part_ids == PART_ID["claw"]
    is_horn = part_ids == PART_ID["horn"]
    is_feather = part_ids == PART_ID["feather"]
    is_other = part_ids == PART_ID["other"]
    body = is_torso | is_neck | is_tail | is_other
    limb = is_leg | is_arm | is_foot
    up = normals[:, 1] > 0.55
    down = normals[:, 1] < -0.55
    axial = body | is_head | is_jaw | is_tongue

    s_field, d_field = field.dorsal_weight(points)
    if s_field is not None:
        dorsal = np.where(axial, d_field, local_d)
        stripe_arg = np.where(axial, s_field + 0.7 * np.sin((d_field - 0.5) * 1.7),
                              z + 0.7 * np.sin(y * 0.85))
    else:
        dorsal = local_d
        stripe_arg = z + 0.7 * np.sin(y * 0.85)

    color = np.where(limb[:, None], ramp(0.20 + 0.55 * local_d, palette), color)
    jaw_base = palette["JAW"] + (palette["JAW2"] - palette["JAW"]) * (0.45 - 0.45 * dorsal)[:, None]
    color = np.where(is_jaw[:, None], jaw_base, color)
    tongue = palette["TONGUE"] + (palette["TONGUE2"] - palette["TONGUE"]) * hash3(x * 1.1, y, z)[:, None] * 0.55
    color = np.where(is_tongue[:, None], tongue, color)
    color = tint(color, palette["DORSAL"], 0.22 * is_head * smoothstep(0.45, 1.0, dorsal))
    color = tint(color, palette["DORSAL"], 0.18 * is_leg * smoothstep(0.55, 1.0, local_d))

    stripe_color = mix(palette["STRIPE"], palette["DORSAL"], style["stripe_blend"])
    color = tint(color, stripe_color, style["stripe_weight"] * stripe(stripe_arg, style)
                 * smoothstep(style["stripe_span"][0], style["stripe_span"][1], dorsal) * axial)
    color = tint(color, stripe_color, style["leg_stripe"] * stripe(y * 1.15, style)
                 * smoothstep(0.30, 0.80, local_d) * is_leg)
    if s_field is not None:
        color = tint(color, stripe_color, style["tail_stripe"] * stripe(s_field * 1.45 + 3.7, style)
                     * smoothstep(0.22, 0.60, dorsal) * is_tail)
    color = tint(color, palette["DORSAL"], style["dorsal_wash"] * up * body)
    color = tint(color, palette["DORSAL"], style["dorsal_wash"] * up * is_head)
    row_line = smoothstep(0.10, 0.0, np.abs((y * 0.75) % 1.6 - 0.8))
    color = tint(color, palette["DORSAL"], style["row_lines"] * row_line * body)
    if s_field is not None:
        folds = smoothstep(0.10, 0.0, np.abs((s_field * 0.9) % 2.0 - 1.0))
        color = tint(color, palette["DORSAL"], style["neck_folds"] * folds * is_neck)
    plates = smoothstep(0.12, 0.0, np.abs((y * 1.1) % style["plate_period"] - style["plate_period"] / 2))
    color = tint(color, palette["DORSAL"], style["plates"] * plates
                 * smoothstep(style["belly_width"], 0.08, dorsal) * body)
    color = tint(color, palette["BELLY2"], 0.30 * down * body)
    color = tint(color, palette["JAW2"], 0.30 * is_head * smoothstep(0.42, 0.10, dorsal))
    color = tint(color, palette["CLAW"], 0.40 * is_arm * smoothstep(0.30, 0.0, local_d))
    color = tint(color, palette["CLAW"], 0.55 * is_foot * smoothstep(0.35, 0.0, local_d))

    color = np.where(is_claw[:, None], mix(palette["CLAW"], palette["DORSAL"], 0.25 * local_d), color)
    horn_base = mix(palette["HORN"], palette["STRIPE"], 0.35 * (1 - local_d))
    color = np.where(is_horn[:, None], horn_base, color)
    feather_base = palette["FEATHER"] * (0.85 + 0.25 * local_d)[:, None]
    feather_rows = smoothstep(0.12, 0.0, np.abs((z * 0.9) % 1.4 - 0.7))
    color = np.where(is_feather[:, None], feather_base * (1.0 - 0.18 * feather_rows[:, None]), color)

    ridge = up & body & (np.abs(x) < 0.55)
    color = tint(color, stripe_color, style["ridge"] * ridge)
    color = tint(color, stripe_color, style["scutes"] * ridge
                 * ((np.floor(stripe_arg / style["scute_period"]) % 2.0) < 1.0))

    patch = hash3(np.floor(x * 0.7), np.floor(y * 0.7), np.floor(z * 0.7))
    color = tint(color, palette["DORSAL"], 0.14 * (patch > 0.80) * (body | limb))
    color = tint(color, palette["FLANK3"], 0.12 * (patch < 0.18) * body)
    grain = (hash3(np.floor(x * 1.6), np.floor(y * 1.6), np.floor(z * 1.6)) - 0.5) * style["grain_fine"]
    grain += (hash3(np.floor(x * 0.6) + 7.0, np.floor(y * 0.6), np.floor(z * 0.6)) - 0.5) * style["grain_coarse"]
    organic = body | limb | is_head | is_jaw | is_horn | is_feather
    color = color * (1.0 + grain[:, None] * organic[:, None])
    scales = body & (hash3(np.floor(x * 0.9) + 3.0, np.floor(y * 0.9), np.floor(z * 0.9)) > style["scale_threshold"])
    color = tint(color, palette["DORSAL"], style["scales"] * scales)
    return color


def add_details(color, points, normals, part_ids, features, palette, style):
    x, y, z = points[:, 0], points[:, 1], points[:, 2]
    is_head = part_ids == PART_ID["head"]
    is_jaw = part_ids == PART_ID["jaw"]
    is_foot = (part_ids == PART_ID["footl"]) | (part_ids == PART_ID["footr"])
    eye_cfg = style["eye"]
    frame = features.get("head_frame")

    if frame is not None and features.get("eye_centers"):
        df, du, dl = frame.coords(points)
        for center in features["eye_centers"]:
            sign = 1.0 if center[0] >= 0 else -1.0
            ef, eu, el = frame.coords(center)
            radial = np.sqrt((df - ef) ** 2 + (du - eu) ** 2)
            lateral_ok = (dl * sign > el * sign + 0.15 * frame.half_width)
            region = is_head & lateral_ok & (radial < 1.3)
            if not region.any():
                continue
            outer = float(np.max(dl[region] * sign))
            surface = region & (dl * sign > outer - eye_cfg["outer_slab"])
            socket = surface & (radial < eye_cfg["socket_r"])
            color = np.where(socket[:, None], palette["SOCKET"], color)
            color = np.where((socket & (radial < eye_cfg["iris_r"]))[:, None], palette["EYE"], color)
            color = np.where((socket & (radial < eye_cfg["pupil_r"]))[:, None], palette["PUPIL"], color)
            color = np.where((socket & (radial > eye_cfg["socket_r"] - 0.10))[:, None], palette["DORSAL"], color)
        eye0 = features["eye_centers"][0]
        _, eye_up, _ = frame.coords(eye0)
        stripe_band = is_head & (np.abs(dl) > eye_cfg["lateral_fraction"] * frame.half_width) \
            & (np.abs(du - eye_up) < 0.75) & (df < frame.coords(eye0)[0] - 0.8)
        color = tint(color, mix(palette["STRIPE"], palette["DORSAL"], style["stripe_blend"]), 0.32 * stripe_band)
        for center in features.get("nostrils", ()):
            distance = np.linalg.norm(points - center, axis=1)
            color = np.where((is_head & (distance < eye_cfg["nostril_r"]))[:, None], palette["SOCKET"], color)

    profile = features.get("profile")
    if profile is not None:
        in_zone = (z > profile["z0"]) & (z < profile["z1"])
        centers = profile["centers"]
        head_lip = np.interp(z, centers, profile["lip"])
        jaw_lip = np.interp(z, centers, profile["jaw_top"])
        lips = (is_head & in_zone & (np.abs(y - head_lip) < 0.50)) \
            | (is_jaw & in_zone & (np.abs(y - jaw_lip) < 0.50))
        color = np.where(lips[:, None], palette["MOUTH"], color)
        phase = np.floor(z * 1.7) + np.where(is_jaw, 0.85, 0.0)
        color = np.where((lips & ((phase % 2.0) < 1.0))[:, None], palette["TEETH"], color)

    foot = features.get("foot_bounds")
    if foot is not None:
        y_min, z_max = foot
        claws = is_foot & ((y < y_min + 0.7) | (z > z_max - 0.9))
        color = np.where(claws[:, None], palette["CLAW"], color)
    return color


def paint_points(points, normals, part_ids, bounds, features, palette, style, field, shade=True):
    color = base_skin(points, normals, part_ids, bounds, palette, style, field)
    color = add_details(color, points, normals, part_ids, features, palette, style)
    if shade:
        color = color * (0.90 + 0.10 * np.clip(normals @ LIGHT, 0.0, 1.0))[:, None]
    return color


def build_quant_palette(palette, style):
    body = np.array([ramp(np.array([t]), palette)[0] for t in np.linspace(0.0, 1.0, 10)], np.float32)
    extras = ["STRIPE", "JAW", "JAW2", "TONGUE", "TONGUE2", "EYE", "PUPIL",
              "SOCKET", "CLAW", "TEETH", "MOUTH", "HORN", "FEATHER"]
    colors = [np.asarray(palette[k], np.float32) for k in extras]
    colors.append(np.array(BACKGROUND, np.float32))
    all_colors = np.vstack([body, np.array(colors)])
    rounded = np.unique(np.clip(np.round(all_colors), 0, 255).astype(np.uint8), axis=0)
    return rounded


def quantize_canvas(canvas, palette, amplitude):
    height, width = canvas.shape[:2]
    bayer = np.tile(BAYER4, (height // 4 + 1, width // 4 + 1))[:height, :width]
    shifted = canvas.astype(np.float32) + ((bayer - 0.5) * amplitude)[..., None]
    out = np.empty((height, width, 3), np.uint8)
    step = 65536
    for start in range(0, height * width, step):
        chunk = shifted.reshape(-1, 3)[start:start + step]
        dist = ((chunk[:, None, :] - palette[None, :, :].astype(np.float32)) ** 2).sum(-1)
        out.reshape(-1, 3)[start:start + step] = palette[dist.argmin(1)]
    return out


def compose(cubes, faces, bounds, features, palette, style, field, tex_size):
    canvas = np.zeros((tex_size, tex_size, 3), np.float32)
    canvas[:] = BACKGROUND
    for face in faces:
        points, normals, part_ids = face_points(face)
        color = paint_points(points, normals, part_ids, bounds, features, palette, style, field)
        grid = np.clip(color.reshape(face["height"], face["width"], 3), 0, 255).astype(np.float32)
        canvas[face["y"]:face["y"] + face["height"], face["x"]:face["x"] + face["width"]] = grid
    for face in faces:
        x, y, w, h = face["x"], face["y"], face["width"], face["height"]
        if y > 0:
            canvas[y - 1, x:x + w] = canvas[y, x:x + w]
        if y + h < tex_size:
            canvas[y + h, x:x + w] = canvas[y + h - 1, x:x + w]
        if x > 0:
            canvas[y:y + h, x - 1] = canvas[y:y + h, x]
        if x + w < tex_size:
            canvas[y:y + h, x + w] = canvas[y:y + h, x + w - 1]
    return canvas


def assign_uvs(faces):
    for face in faces:
        face["cube"].setdefault("uv", {})[face["key"]] = (face["x"], face["y"], face["width"], face["height"])


def write_geo(source_geo, cubes, tex_size, destination):
    doc = json.loads(Path(source_geo).read_text(encoding="utf-8"))
    geometry = doc["minecraft:geometry"][0]
    geometry["description"]["texture_width"] = tex_size
    geometry["description"]["texture_height"] = tex_size
    seen = {(cube["bone_index"], cube["cube_index"]): cube for cube in cubes}
    for bone_index, bone in enumerate(geometry["bones"]):
        for cube_index, target in enumerate(bone.get("cubes", [])):
            cube = seen[(bone_index, cube_index)]
            target["uv"] = {key: {"uv": [rect[0], rect[1]], "uv_size": [rect[2], rect[3]]}
                            for key, rect in cube["uv"].items()}
    destination.write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")


def write_bbmodel(source_model, cubes, texture_path, tex_size, destination):
    doc = json.loads(Path(source_model).read_text(encoding="utf-8"))
    per_bone = {}
    for element in doc["elements"]:
        per_bone.setdefault(element["name"].split("/")[0].strip(), []).append(element)
    for cube in cubes:
        element = per_bone[cube["bone"]][cube["cube_index"]]
        for key, (u0, v0, du, dv) in cube["uv"].items():
            element["faces"][key]["uv"] = [u0, v0, u0 + du, v0 + dv]
    encoded = base64.b64encode(texture_path.read_bytes()).decode()
    for texture in doc.get("textures", []):
        texture.update({"width": tex_size, "height": tex_size, "uv_width": tex_size,
                        "uv_height": tex_size, "source": "data:image/png;base64," + encoded})
    doc.setdefault("resolution", {}).update({"width": tex_size, "height": tex_size})
    destination.write_text(json.dumps(doc, separators=(",", ":")), encoding="utf-8")


def face_uvs(cube, key):
    entry = cube["faces"][key]
    if isinstance(entry, dict):
        u0, v0 = entry["uv"]
        du, dv = entry["uv_size"]
    else:
        u0, v0, du, dv = entry
    return np.array([[u0 + s * du, v0 + t * dv]
                     for s, t in (corner_st(key, CORNERS[index]) for index in FACE_CORNERS[key])])


def decode_geo(path, style=None):
    _, cubes, _ = load_cubes(path, style)
    return [{"corners": cube_corners(cube), "faces": cube["source_uv"]} for cube in cubes]


def sample_track(track, time, default):
    if not track:
        return np.array(default, float)
    times = sorted(float(key) for key in track)
    values = []
    for key in sorted(track, key=float):
        value = track[key]
        values.append([value["x"], value["y"], value["z"]] if isinstance(value, dict) else list(value))
    values = np.array(values, float)
    return np.array([np.interp(time, times, values[:, i]) for i in range(3)])


def posed_corners(cubes, skeleton, clip_bones, time):
    names = skeleton["names"]
    parents = skeleton["parents"]
    pivots = skeleton["pivots"]
    rotations = skeleton["rotations"]
    world_pivots, world_matrices = [], []
    for index, parent in enumerate(parents):
        tracks = clip_bones.get(names[index], {}) if clip_bones else {}
        angle = sample_track(tracks.get("rotation"), time, (0, 0, 0)) * [-1, -1, 1]
        offset = sample_track(tracks.get("position"), time, (0, 0, 0)) * [-1, 1, 1]
        matrix = R.from_euler("xyz", angle + rotations[index], degrees=True).as_matrix()
        if parent < 0:
            world_pivots.append(pivots[index] + offset)
            world_matrices.append(matrix)
        else:
            world_pivots.append(world_pivots[parent] + world_matrices[parent] @ (pivots[index] - pivots[parent] + offset))
            world_matrices.append(world_matrices[parent] @ matrix)
    world_pivots = np.array(world_pivots)
    world_matrices = np.array(world_matrices)
    posed = []
    for cube in cubes:
        index = cube["bone_index"]
        posed.append(world_pivots[index]
                     + (cube["model_corners"] - pivots[index]) @ world_matrices[index].T)
    return posed


def _fill(zbuf, tu, tv, fid, screen, depth, uv, fid_value, width, height):
    area = ((screen[1, 0] - screen[0, 0]) * (screen[2, 1] - screen[0, 1])
            - (screen[2, 0] - screen[0, 0]) * (screen[1, 1] - screen[0, 1]))
    if abs(area) < 1e-12:
        return
    if area < 0:
        screen, depth, uv = screen[[0, 2, 1]], depth[[0, 2, 1]], uv[[0, 2, 1]]
        area = -area
    x0 = max(0, int(np.floor(screen[:, 0].min())))
    x1 = min(width - 1, int(np.ceil(screen[:, 0].max())))
    y0 = max(0, int(np.floor(screen[:, 1].min())))
    y1 = min(height - 1, int(np.ceil(screen[:, 1].max())))
    if x0 > x1 or y0 > y1:
        return
    gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
    w0 = ((screen[1, 1] - screen[2, 1]) * (gx - screen[2, 0])
          + (screen[2, 0] - screen[1, 0]) * (gy - screen[2, 1])) / area
    w1 = ((screen[2, 1] - screen[0, 1]) * (gx - screen[2, 0])
          + (screen[0, 0] - screen[2, 0]) * (gy - screen[2, 1])) / area
    w2 = 1.0 - w0 - w1
    inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
    if not inside.any():
        return
    z = w0 * depth[0] + w1 * depth[1] + w2 * depth[2]
    zblock = zbuf[y0:y1 + 1, x0:x1 + 1]
    upd = inside & (z < zblock)
    if not upd.any():
        return
    zblock[upd] = z[upd]
    tu[y0:y1 + 1, x0:x1 + 1][upd] = (w0 * uv[0, 0] + w1 * uv[1, 0] + w2 * uv[2, 0])[upd]
    tv[y0:y1 + 1, x0:x1 + 1][upd] = (w0 * uv[0, 1] + w1 * uv[1, 1] + w2 * uv[2, 1])[upd]
    fid[y0:y1 + 1, x0:x1 + 1][upd] = fid_value


def render(cubes, texture, cam, size, textured=True, focus=None, zoom=1.0, margin=110):
    width, height = size
    points = []
    faces = []
    for index, cube in enumerate(cubes):
        rotated = cube["corners"] @ cam.T
        points.append(rotated)
        for slot, key in enumerate(FACE_CORNERS):
            indices = FACE_CORNERS[key]
            quad = rotated[indices]
            normal = np.cross(quad[1] - quad[0], quad[2] - quad[0])
            length = np.linalg.norm(normal)
            if length < 1e-9:
                continue
            normal = normal / length
            if normal[0] > -1e-6:
                continue
            screen = np.column_stack([quad[:, 2], -quad[:, 1]])
            shade = 0.62 + 0.38 * float(np.clip(normal @ LIGHT, 0, 1))
            faces.append((screen, quad[:, 0], face_uvs(cube, key), shade, index * 6 + slot))
    points = np.concatenate(points)
    screen_all = np.column_stack([points[:, 2], -points[:, 1]])
    focus_screen = np.zeros(2)
    if focus is not None:
        rotated_focus = cam @ np.asarray(focus, float)
        focus_screen = np.array([rotated_focus[2], -rotated_focus[1]])
        screen_all = screen_all - focus_screen
    lo, hi = screen_all.min(0), screen_all.max(0)
    scale = min((width - 2 * margin) / max(hi[0] - lo[0], 1e-6),
                (height - 2 * margin) / max(hi[1] - lo[1], 1e-6)) * zoom
    center = np.zeros(2) if focus is not None else (lo + hi) / 2

    zbuf = np.full((height, width), np.inf)
    tu = np.zeros((height, width))
    tv = np.zeros((height, width))
    fid = np.full((height, width), -1, np.int32)
    shades = {}
    for screen, depth, uv, shade, fid_value in faces:
        screen = screen - focus_screen
        pixel = (screen - center) * scale + [width / 2, height / 2]
        shades[fid_value] = shade
        for tri in ([0, 1, 2], [0, 2, 3]):
            _fill(zbuf, tu, tv, fid, pixel[tri], depth[tri], uv[tri], fid_value, width, height)

    canvas = np.zeros((height, width, 3), np.float32)
    canvas[:] = (19, 30, 39)
    visible = np.isfinite(zbuf)
    if textured:
        tex = np.asarray(texture.convert("RGB"), np.float32)
        th, tw = tex.shape[:2]
        u = np.clip(np.round(tu[visible]).astype(int), 0, tw - 1)
        v = np.clip(np.round(tv[visible]).astype(int), 0, th - 1)
        colors = tex[v, u]
        shade = np.array([shades[int(i)] for i in fid[visible]], np.float32)
        canvas[visible] = colors * shade[:, None]
    else:
        for _, _, _, shade, fid_value in faces:
            mask = visible & (fid == fid_value)
            if not mask.any():
                continue
            hue = np.array([(fid_value * 47) % 200 + 40, (fid_value * 89) % 160 + 60,
                            (fid_value * 131) % 180 + 60], np.float32)
            canvas[mask] = hue * (0.55 + 0.45 * shade)
    return Image.fromarray(np.clip(canvas, 0, 255).astype(np.uint8))


def font(size):
    for name in ("segoeui.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(f"C:/Windows/Fonts/{name}", size)
        except OSError:
            continue
    return ImageFont.load_default()


def title_block(image, title, subtitle):
    draw = ImageDraw.Draw(image)
    draw.text((38, 23), title, fill="#e9eddf", font=font(31))
    if subtitle:
        draw.text((40, 68), subtitle, fill="#95a9ac", font=font(16))
    draw.text((40, image.height - 38), "ARK Survival Returns  /  painted skin studio v2  /  true per-face UVs",
              fill="#718c92", font=font(14))
    return image


def texture_sheet(texture_path, out_path, label, colors=None):
    texture = Image.open(texture_path).convert("RGB")
    sheet = texture.resize((texture.width * 2, texture.height * 2), Image.NEAREST)
    extra = f"  |  {colors} colours" if colors else ""
    title_block(sheet, label, f"painted atlas {texture.width}x{texture.height} (2x, 16px grid){extra}").save(out_path)


def validation(canvas, faces, tex_size):
    occupancy = np.zeros((tex_size, tex_size), np.int16)
    for face in faces:
        occupancy[face["y"]:face["y"] + face["height"], face["x"]:face["x"] + face["width"]] += 1
    overlap = int((occupancy > 1).sum())
    corners = {}
    for face in faces:
        p00, p10, p11, p01 = face["corner"]
        for (s, t), point in (((0, 0), p00), ((1, 0), p10), ((1, 1), p11), ((0, 1), p01)):
            px = min(face["x"] + face["width"] - 1, max(face["x"], int(round(face["x"] + s * (face["width"] - 1)))))
            py = min(face["y"] + face["height"] - 1, max(face["y"], int(round(face["y"] + t * (face["height"] - 1)))))
            key = tuple(np.round(np.asarray(point, float), 2))
            corners.setdefault(key, []).append(canvas[py, px].astype(int))
    deltas = []
    for colors in corners.values():
        if len(colors) < 2:
            continue
        stack = np.array(colors)
        deltas.append(float(np.abs(stack[:, None, :] - stack[None, :, :]).max()))
    return {
        "island_overlap_pixels": overlap,
        "shared_border_corners": len(deltas),
        "seam_delta_mean": round(float(np.mean(deltas)), 2) if deltas else 0.0,
        "seam_delta_max": round(float(np.max(deltas)), 2) if deltas else 0.0,
        "palette_colors": int(len(np.unique(canvas.reshape(-1, 3), axis=0))),
    }


def symmetry_metric(cubes, bounds, features, palette, style, field, samples=2500, seed=11):
    rng = np.random.default_rng(seed)
    faces = face_frames(cubes, style)
    weights = np.array([face["width"] * face["height"] for face in faces], float)
    weights /= weights.sum()
    chosen = rng.choice(len(faces), size=samples, p=weights)
    points, normals, part_ids = [], [], []
    for index in chosen:
        face = faces[index]
        s, t = rng.random(2)
        p00, p10, p11, p01 = face["corner"]
        point = (1 - s) * (1 - t) * p00 + s * (1 - t) * p10 + s * t * p11 + (1 - s) * t * p01
        points.append(point)
        normals.append(face["normal"])
        part_ids.append(PART_ID[face["cube"]["part"]])
    points = np.array(points)
    normals = np.array(normals, np.float32)
    part_ids = np.array(part_ids)
    left = paint_points(points, normals, part_ids, bounds, features, palette, style, field)
    mirrored = points * [-1, 1, 1]
    mirrored_normals = normals * [-1, 1, 1]
    right = paint_points(mirrored, mirrored_normals, part_ids, bounds, features, palette, style, field)
    return round(float(np.abs(left - right).mean()), 2)


def previews(decoded, cubes, skeleton, animations, texture_path, out_dir, label, subtitle, style=None):
    out_dir.mkdir(parents=True, exist_ok=True)
    texture = Image.open(texture_path)
    cams = {
        "side": np.eye(3),
        "three_quarter": R.from_euler("yx", [-34, 10], degrees=True).as_matrix(),
        "front": R.from_euler("y", -90, degrees=True).as_matrix(),
    }
    for name, cam_key, caption in (("side.png", "side", "Side view"),
                                   ("three_quarter.png", "three_quarter", "Three-quarter view"),
                                   ("front.png", "front", "Front view")):
        image = render(decoded, texture, cams[cam_key], (1100, 780))
        title_block(image, label, f"{caption}  |  {subtitle}").save(out_dir / name)
    debug = render(decoded, texture, cams["three_quarter"], (1100, 780), textured=False)
    title_block(debug, label, "Atlas island map (flat debug color per cube face)").save(out_dir / "uv_check.png")
    texture_sheet(texture_path, out_dir / "texture_atlas.png", label)
    if animations:
        clip_name = next((name for name in animations if "idle" in name.lower()),
                         next((name for name in animations if "move" in name.lower()), next(iter(animations))))
        clip = animations[clip_name]
        length = float(clip.get("animation_length", 1.0)) or 1.0
        track_bones = clip.get("bones", {})
        frames = []
        for time in np.linspace(0.0, length, 8, endpoint=False):
            posed = posed_corners(cubes, skeleton, track_bones, float(time))
            posed_decoded = [{"corners": posed[index], "faces": decoded[index]["faces"]}
                             for index in range(len(cubes))]
            frames.append(render(posed_decoded, texture, cams["three_quarter"], (760, 540)))
        middle = frames[len(frames) // 2]
        title_block(middle, label, f"animated pose check  |  {clip_name}").save(out_dir / "pose.png")
        frames[0].save(out_dir / "pose.gif", save_all=True, append_images=frames[1:],
                       duration=max(40, int(length * 1000 / len(frames))), loop=0)


def build_skin(label, creature_dir, palette_colors, style=None, out_name="skin", write_previews=True):
    creature_dir = Path(creature_dir)
    out = creature_dir / out_name
    out.mkdir(exist_ok=True)
    style = {**DEFAULT_STYLE, **(style or {})}
    geo_path, model_path = creature_files(creature_dir)

    palette = palette_roles(palette_colors)
    doc, cubes, skeleton = load_cubes(geo_path, style)
    faces = face_frames(cubes, style)
    tex_size = choose_tex_size(faces)
    packed_height = pack_faces(faces, tex_size)
    bounds = part_bounds(cubes)

    field = BodyField(cubes, style.get("centerline_chain"))
    frame = HeadFrame(cubes)
    eye_points = part_points(cubes, "eye")
    eye_centers = []
    for sign in (-1, 1):
        side = eye_points[np.sign(eye_points[:, 0]) == sign] if len(eye_points) else eye_points
        if len(side):
            eye_centers.append(side.mean(axis=0))
    head = part_points(cubes, "head")
    foot = np.concatenate([part_points(cubes, "footl"), part_points(cubes, "footr")])
    profile = lip_profiles(cubes)
    eye_cfg = style["eye"]
    nostrils = []
    if frame.valid and eye_centers:
        for sign in (-1, 1):
            base = frame.snout - frame.forward * (eye_cfg["nostril_back"] * frame.length)
            base = base + frame.lateral * (sign * eye_cfg["nostril_lateral"] * frame.half_width)
            base = base - frame.up * (eye_cfg["nostril_down"] * frame.half_height)
            nostrils.append(base)
    features = {
        "eye_centers": eye_centers,
        "nostrils": nostrils,
        "head_frame": frame if frame.valid else None,
        "profile": profile,
        "foot_bounds": (float(foot[:, 1].min()), float(foot[:, 2].max())) if len(foot) else None,
    }

    canvas = compose(cubes, faces, bounds, features, palette, style, field, tex_size)
    quant_palette = None
    if style.get("quantize"):
        quant_palette = build_quant_palette(palette, style)
        canvas = quantize_canvas(canvas, quant_palette, style["dither"])
    else:
        canvas = np.clip(canvas, 0, 255).astype(np.uint8)
    texture_path = out / "skin.png"
    Image.fromarray(canvas, "RGB").save(texture_path)

    assign_uvs(faces)
    write_geo(geo_path, cubes, tex_size, out / geo_path.name)
    write_bbmodel(model_path, cubes, texture_path, tex_size, out / f"{label}_Textured.bbmodel")

    animations = None
    animation_files = sorted((creature_dir / "animations").glob("*.animation.json")) if (creature_dir / "animations").exists() else []
    if animation_files:
        animations = json.loads(animation_files[0].read_text(encoding="utf-8")).get("animations", {})

    metrics = validation(canvas, faces, tex_size)
    metrics["bilateral_symmetry_delta"] = symmetry_metric(cubes, bounds, features, palette, style, field)
    metrics["atlas_used_pixels"] = sum(f["width"] * f["height"] for f in faces)
    metrics["islands_1x1"] = sum(1 for f in faces if f["width"] == 1 and f["height"] == 1)
    checks = {
        "all_faces_in_bounds": all(f["x"] >= 0 and f["y"] >= 0 and f["x"] + f["width"] <= tex_size
                                   and f["y"] + f["height"] <= tex_size for f in faces),
        "all_faces_positive_size": all(f["width"] >= 1 and f["height"] >= 1 for f in faces),
        "face_count_matches": len(faces) == len(cubes) * 6,
        "atlas_fits": packed_height <= tex_size,
        "islands_disjoint": metrics["island_overlap_pixels"] == 0,
        "palette_within_target": metrics["palette_colors"] <= 32,
    }
    report = {
        "creature": label,
        "source_geo": str(geo_path.relative_to(creature_dir.parent.parent)),
        "source_model": str(model_path.relative_to(creature_dir.parent.parent)),
        "texture": {"file": texture_path.name, "size": [tex_size, tex_size], "pixels_per_unit": style["base_scale"]},
        "cubes": len(cubes),
        "faces": len(faces),
        "atlas": {"packed_height": packed_height, "used_pixels": metrics["atlas_used_pixels"],
                  "coverage": round(metrics["atlas_used_pixels"] / (tex_size * tex_size), 4)},
        "parts": {part: sum(1 for cube in cubes if cube["part"] == part) for part in PARTS if part != "bg"},
        "unclassified_bones": sorted({cube["bone"] for cube in cubes if cube["part"] == "other"}),
        "palette": list(palette_colors),
        "style": {"stripe_period": style["stripe_period"], "base_scale": style["base_scale"],
                  "detail_scale": style["detail_scale"], "quantize": bool(style.get("quantize")),
                  "quant_palette_colors": int(len(quant_palette)) if quant_palette is not None else None,
                  "centerline": field.points is not None},
        "features": {
            "eye_centers": [[round(float(v), 3) for v in c] for c in eye_centers],
            "nostrils": [[round(float(v), 3) for v in c] for c in nostrils],
            "head_frame": None if not frame.valid else {
                "forward": [round(float(v), 3) for v in frame.forward],
                "length": round(frame.length, 3), "half_width": round(frame.half_width, 3),
                "half_height": round(frame.half_height, 3)},
            "has_jaw_profile": profile is not None,
        },
        "metrics": metrics,
        "checks": checks,
    }
    (out / "skin_report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    if write_previews:
        decoded = decode_geo(out / geo_path.name, style)
        subtitle = f"{len(cubes)} cubes  |  {metrics['palette_colors']} colours  |  {tex_size}x{tex_size}"
        previews(decoded, cubes, skeleton, animations, texture_path, out / "previews", label, subtitle, style)
    return report, out / geo_path.name, out / f"{label}_Textured.bbmodel", texture_path
