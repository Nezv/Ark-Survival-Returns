"""Regenerate the black dragon's model + texture for All Under Heaven.

The GEOMETRY is reconstructed from the hand-authored reference model
(``dragon.geo.json`` in this folder - the perfected black dragon: wing arms in
their final position, ``wing_l_hand``/``wing_r_hand`` planted at ground level,
bat-membrane sheets whose silhouette the texture alpha cuts). It is emitted
verbatim (byte-identical), so the generated model is *as close to the
reference as possible*, and hand edits survive every run - this script never
writes the reference itself. The only geometry change is the wing membrane
UVs: the reference's scattered, hand-placed panel rects are replaced by one
clean rect per membrane PAIR (the wings are symmetric) in ``MEMBRANE_STRIP``.
Both wings share the same painted sheet; the RIGHT wing samples it INVERTED
(uv origin on the far side, negative width - the reference's own sign
convention) so the torn end of the shared sheet lands at the right wing's tip
too, the south matching across the body.

The TEXTURE is generated procedurally from scratch onto a fresh 2048x2048
sheet:

  * non-membrane faces are classified against the black variant's palette and
    repainted with procedural hide/belly/horn/eye treatments;
  * the wing membranes are painted per pair by rule
    (``paint_membrane_slice``) into their clean rects. The wing anatomy is
    mapped first: 4 membrane sets per wing - Fore<->W4, W4<->W3, W3<->W2,
    W2<->W1 (W1..W4 = finger1..4) - each set built from the reference's own
    rectangles. Every slice is the polygon BETWEEN its two bones: the parts
    of the rectangle that cross the bone lines stay INVISIBLE (a rectangle
    must not try to fit a triangle), so the sail is a trapezoid that narrows
    toward the wrist where the bones converge at the hand. The free (tip)
    end is torn - a ragged cut with little holes whose depth grows toward
    the wingtip and that never reaches the bone lines. ``--holes`` scales
    the tear density, ``--seed`` re-rolls the pattern.

No animations are emitted here - those are handled by build_dragon.py.

DRAGON PARAMETERS - how big, how experienced, how old:

  --size F        body length + wingspan multiplier (spine stretches along Z,
                  wings along X; a mild height factor F^0.6 keeps proportions)
  --age N (0-10)  color (hide/membrane palette lerps young->old), face detail
                  (horns/crest/brow plates grow), whiskers from age 4 (the
                  white variant's mustache, sized by age) and neck posture
                  (rest pitches bend more with age, head compensated so the
                  muzzle angle holds). 5 = the current look.
  --experience N  five levels (0-4), only for age >= 5 (auto-gated): the wing
                  membranes tear more (density + depth) and the wing-hand
                  nails grow (1 + 0.3*N about their pivots).

Defaults (no flags) reproduce the reference exactly; with modifiers the
geometry is re-emitted via the compact Blockbench-format emitter and the
grounding check reports only (the planted stance is re-solved with the
animations later).

Run:  python generate_dragon.py [dragon.geo.json] [--holes 1.0] [--seed 1]
      python generate_dragon.py --name big_old --size 3 --age 10 --experience 4
      python generate_dragon.py --membrane-only   # dump the slices + stats
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import random
import re
import sys

from PIL import Image, ImageDraw, ImageStat

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_REF = os.path.join(HERE, "dragon.geo.json")
OUT_DIR = os.path.join(HERE, "out")
IDENTIFIER = "geometry.allunderheaven.dragon"
TEX_W = TEX_H = 2048

# The wing-membrane slices: each of the model's membrane planes (the 9 per
# wing, in 4 sets - Fore<->W4, W4<->W3, W3<->W2, W2<->W1) gets its own clean
# rect in this strip, which the reference's other UVs never touch. Left and
# right wing planes of the same set share the same rect (mirror-symmetric).
MEMBRANE_STRIP = (0, 1400, 2048, 2048)
SLICE_TEXEL = 4.0          # texture pixels per model unit on the slices
# The membrane's actual painted colour in the reference texture (a flat
# (131,121,121) fill on the finger panels; the big inner panel only varies
# within a few levels from mottle and spar occlusion).
MEMBRANE_BASE = (131, 121, 121)

# Which two bones each membrane set connects: set name -> (upper, lower) bone
# stems. The upper bone is the one closer to the Fore in the fan sequence
# (Fore, W4, W3, W2, W1); the lower is the next one out. Plane-to-set
# assignment is by the bone the plane is attached to (verified against the
# reference's world-space geometry).
MEMBRANE_SETS = {
    "m1_fore_w4": ("fore", "wing_l_finger4"),
    "m2_w4_w3": ("wing_l_finger4", "wing_l_finger3"),
    "m3_w3_w2": ("wing_l_finger3", "wing_l_finger2"),
    "m4_w2_w1": ("wing_l_finger2", "wing_l_finger1"),
}
# bone stem -> membrane set (both wings use the same stems)
PLANE_SET = {
    "bone2": "m1_fore_w4",
    "bone2r": "m1_fore_w4",
    "wing_l_finger4": "m1_fore_w4",
    "wing_r_finger4": "m1_fore_w4",
    "wing_l_finger3": "m2_w4_w3",
    "wing_r_finger3": "m2_w4_w3",
    "wing_l_finger2": "m3_w3_w2",
    "wing_r_finger2": "m3_w3_w2",
    "wing_l_finger1": "m4_w2_w1",
    "wing_r_finger1": "m4_w2_w1",
}

# The black variant's palette (mirrors build_dragon.BLACK). Faces are classified
# by which entry their sampled reference colour is nearest to, so the generated
# sheet keeps the reference's material layout.
PALETTE = {
    "hide": (42, 42, 48),
    "hide_dark": (30, 30, 36),
    "membrane": (128, 118, 118),
    "horn": (118, 112, 126),
    "ridge": (56, 54, 66),
    "eye": (106, 100, 56),
    "socket": (14, 12, 18),
    "teeth": (198, 192, 184),
    "belly": (104, 100, 112),
    "mouth": (42, 16, 18),
    "iris": (250, 220, 30),
}


# --------------------------------------------------------------------- model

def load_model(path: str):
    """Reads the reference model. Returns (doc, bones, cubes) with cubes kept
    in raw geo space; each cube carries its per-face UV rects (normalised).
    ``doc["_raw"]`` holds the file text so emit_geo can write it verbatim."""
    text = open(path, encoding="utf-8", newline="").read()
    doc = json.loads(text)
    doc["_raw"] = text
    geo = doc["minecraft:geometry"][0]
    bones = []
    cubes = []
    for b in geo["bones"]:
        bones.append({"name": b["name"], "parent": b.get("parent"),
                      "pivot": b["pivot"], "rot": b.get("rotation", [0, 0, 0])})
        for c in b.get("cubes", []):
            faces = {}
            uvraw = {}
            for fname, fd in c.get("uv", {}).items():
                ux, uy = fd["uv"]
                uw, uh = fd["uv_size"]
                uvraw[fname] = (ux, uy, uw, uh)
                faces[fname] = (ux, uy, ux + uw, uy + uh)
            cubes.append({
                "bone": b["name"],
                "origin": c["origin"],
                "size": c["size"],
                "rot": c.get("rotation", [0, 0, 0]),
                "pivot": c.get("pivot", [0, 0, 0]),
                "inflate": c.get("inflate", 0.0),
                "mirror": c.get("mirror", False),
                "uv": faces,
                "uvraw": uvraw,
            })
    return doc, bones, cubes


def emit_geo(text, out_path: str):
    """Writes the reference geometry verbatim (byte-identical, keeping
    Blockbench's tab formatting), with the membrane UVs already re-stamped."""
    with open(out_path, "w", encoding="utf-8", newline="") as f:
        f.write(text)
    doc = json.loads(text)
    n_bones = len(doc["minecraft:geometry"][0]["bones"])
    n_cubes = sum(len(b.get("cubes", []))
                  for b in doc["minecraft:geometry"][0]["bones"])
    print(f"geo   {os.path.basename(out_path)}  bones={n_bones} cubes={n_cubes}")


def _fmt(v):
    """Number formatting matching the reference: ints as ints, floats in
    shortest round-trip repr, -0.0 normalised."""
    if isinstance(v, float) and v == int(v):
        return str(int(v))
    if v == 0 and isinstance(v, float) and math.copysign(1.0, v) < 0:
        return "0"
    return repr(v)


def _emit_cube(c, indent):
    """One cube in Blockbench's compact layout: fields each on their own
    line, the uv entries inline. `indent` = the number of tabs before '{'."""
    p = "\t" * indent
    t = p + "{\n"
    t += p + '\t"origin": [' + ", ".join(_fmt(x) for x in c["origin"]) + "],\n"
    t += p + '\t"size": [' + ", ".join(_fmt(x) for x in c["size"]) + "],\n"
    if any(abs(x) > 1e-9 for x in c["rot"]):
        t += p + '\t"pivot": [' + ", ".join(_fmt(x) for x in c["pivot"]) + "],\n"
        t += p + '\t"rotation": [' + ", ".join(_fmt(x) for x in c["rot"]) + "],\n"
    t += p + '\t"uv": {\n'
    faces = [f for f in ("north", "east", "south", "west", "up", "down")
             if f in c["uvraw"]]
    for k, fname in enumerate(faces):
        ux, uy, uw, uh = c["uvraw"][fname]
        t += (p + '\t\t"{}": {{"uv": [{}, {}], "uv_size": [{}, {}]}}'
              .format(fname, _fmt(ux), _fmt(uy), _fmt(uw), _fmt(uh)))
        t += "," if k < len(faces) - 1 else ""
        t += "\n"
    t += p + "\t}\n"
    if c.get("inflate"):
        t += p + '\t"inflate": ' + _fmt(c["inflate"]) + ",\n"
    if c.get("mirror"):
        t += p + '\t"mirror": true,\n'
    t += p + "}"
    return t


def _model_bounds(bones, cubes):
    """World bbox of the model (all cube corners through the bone chains) in
    units - used to size the visible bounds, which the reference's authored
    24x12@[0,4,0] would cull a size-3 dragon's head with."""
    by = {b["name"]: b for b in bones}
    xs, ys, zs = [], [], []
    for c in cubes:
        o, s = c["origin"], c["size"]
        p, r = c.get("pivot", o), c.get("rot", (0, 0, 0))
        for dx in (0, s[0]):
            for dy in (0, s[1]):
                for dz in (0, s[2]):
                    q = tuple(p[i] + _rot(
                        (o[0] + dx - p[0], o[1] + dy - p[1],
                         o[2] + dz - p[2]), r)[i] for i in range(3))
                    w = _world(by, c["bone"], q)
                    xs.append(w[0])
                    ys.append(w[1])
                    zs.append(w[2])
    return (min(xs), max(xs), min(ys), max(ys), min(zs), max(zs))


def emit_geo_doc(bones, cubes, out_path, identifier=IDENTIFIER):
    """Emits the (possibly modified) model in Blockbench's compact format -
    tab-indented, one field per line, uv entries inline - so the membrane
    re-stamp and the git diffs stay clean. The visible bounds are sized from
    the model's own bbox (the reference's authored 24x12 box would cull a
    scaled dragon's head). Never touches the reference."""
    cubes_by = {}
    for c in cubes:
        cubes_by.setdefault(c["bone"], []).append(c)
    x0, x1, y0, y1, z0, z1 = _model_bounds(bones, cubes)
    vb_w = int(math.ceil(max(x1 - x0, z1 - z0) / 16.0)) + 4
    vb_h = int(math.ceil((y1 - y0) / 16.0)) + 4
    vb_oy = int(math.floor(y0 / 16.0)) - 2
    t = "{\n"
    t += '\t"format_version": "1.12.0",\n'
    t += '\t"minecraft:geometry": [\n'
    t += "\t\t{\n"
    t += '\t\t\t"description": {\n'
    t += f'\t\t\t\t"identifier": "{identifier}",\n'
    t += '\t\t\t\t"texture_width": 2048,\n'
    t += '\t\t\t\t"texture_height": 2048,\n'
    t += f'\t\t\t\t"visible_bounds_width": {vb_w},\n'
    t += f'\t\t\t\t"visible_bounds_height": {vb_h},\n'
    t += f'\t\t\t\t"visible_bounds_offset": [0, {vb_oy}, 0]\n'
    t += "\t\t\t},\n"
    t += '\t\t\t"bones": [\n'
    for bi, b in enumerate(bones):
        t += "\t\t\t\t{\n"
        t += f'\t\t\t\t\t"name": "{b["name"]}",\n'
        if b.get("parent"):
            t += f'\t\t\t\t\t"parent": "{b["parent"]}",\n'
        t += '\t\t\t\t\t"pivot": [' + ", ".join(_fmt(x) for x in b["pivot"]) + "]"
        has_rot = any(abs(x) > 1e-9 for x in b["rot"])
        has_cubes = b["name"] in cubes_by
        if has_rot or has_cubes:
            t += ",\n"
        else:
            t += "\n"
        if has_rot:
            t += '\t\t\t\t\t"rotation": [' + ", ".join(_fmt(x) for x in b["rot"]) + "]"
            t += ",\n" if has_cubes else "\n"
        if has_cubes:
            t += '\t\t\t\t\t"cubes": [\n'
            for ci, c in enumerate(cubes_by[b["name"]]):
                t += _emit_cube(c, 6)
                t += "," if ci < len(cubes_by[b["name"]]) - 1 else ""
                t += "\n"
            t += "\t\t\t\t\t]\n"
        t += "\t\t\t\t}"
        t += "," if bi < len(bones) - 1 else ""
        t += "\n"
    t += "\t\t\t]\n"
    t += "\t\t}\n"
    t += "\t]\n"
    t += "}\n"
    t = t.replace("\n", "\r\n")
    if out_path:
        with open(out_path, "w", encoding="utf-8", newline="") as f:
            f.write(t)
        print(f"geo   {os.path.basename(out_path)}  bones={len(bones)} cubes={len(cubes)}")
    return t


# ------------------------------------------------------------ model modifiers

# Chain membership for the SIZE stretch: the spine (body/neck/head/tail) and
# wings scale their length (Z / X), everything gets the mild height factor.
_SPINE = {"body", "head", "tail_fin"} \
    | {f"neck{i}" for i in range(1, 5)} | {f"tail{i}" for i in range(1, 8)}
_WING = set()
for _s in ("l", "r"):
    _WING.add(f"wing_{_s}_arm")
    _WING.add("bone1" if _s == "l" else "bone1r")
    _WING.add("bone2" if _s == "l" else "bone2r")
    _WING.add(f"wing_{_s}_fore")
    _WING.add(f"wing_{_s}_hand")
    for f in range(1, 5):
        _WING |= {f"wing_{_s}_finger{f}", f"wing_{_s}_finger{f}b",
                  f"wing_{_s}_finger{f}c"}
_LEG = {f"leg_{s}_{p}" for s in ("l", "r")
        for p in ("thigh", "shin", "foot", "toes")}
_HEAD_DETAIL = {"head_crest", "brow", "snout", "snout_tip", "jaw",
                "cheek_l", "cheek_r",
                "whisker_l_1", "whisker_l_2", "whisker_r_1", "whisker_r_2"}
for s in ("l", "r"):
    _HEAD_DETAIL |= {f"horn_{s}_1", f"horn_{s}_2", f"horn_{s}_hi",
                     f"horn_{s}_lo"}


def _scale_cube(c, joint_old, joint_new, kx, ky, kz):
    """Scales a cube's origin/size (and its own pivot, so rotated cubes keep
    their anchor) about its bone's joint: the cube's offset from the ORIGINAL
    joint grows by k, laid back onto the NEW joint - so the cube stays
    attached to its bone as it grows. Scaling about the frame origin instead
    is what flung the head/crest/horn cubes away from their joints."""
    o, s, p = c["origin"], c["size"], c["pivot"]
    k = (kx, ky, kz)
    c["origin"] = [joint_new[i] + (o[i] - joint_old[i]) * k[i]
                   for i in range(3)]
    c["size"] = [s[i] * k[i] for i in range(3)]
    if any(abs(x) > 1e-9 for x in c["rot"]):
        c["pivot"] = [joint_new[i] + (p[i] - joint_old[i]) * k[i]
                      for i in range(3)]


def _apply_size(bones, cubes, size):
    """SIZE: body length + wingspan by `size`, plus a mild height factor
    (size^0.6) so a big dragon reads proportionate, not stretched. Pivots
    scale about their PARENT's joint (so the face rides the head, which
    stretches along the spine, instead of being left behind); cubes scale
    about their own joint. Y stays grounded by construction."""
    h = size ** 0.6
    old_pivots = {b["name"]: tuple(b["pivot"]) for b in bones}
    by_cur = {b["name"]: b for b in bones}

    def factors(n):
        if n == "root":
            return (1, 1, 1)
        if n in _WING:
            return (size, h, size)
        if n in _LEG:
            return (h, h, size)
        if n in _HEAD_DETAIL:
            return (h, h, h)
        return (h, h, size)  # spine

    for b in bones:
        n = b["name"]
        if n == "root":
            continue
        k = factors(n)
        parent = b.get("parent")
        if parent in (None, "root"):
            pn = (0.0, 0.0, 0.0)          # the root's joint is the origin
            po = (0.0, 0.0, 0.0)
        else:
            pn = by_cur[parent]["pivot"]  # parent processed first: its real
            po = old_pivots[parent]       # scaled pivot, not a factor product
        b["pivot"] = [pn[i] + (b["pivot"][i] - po[i]) * k[i]
                      for i in range(3)]
    by = {b["name"]: b for b in bones}
    for c in cubes:
        n = c["bone"]
        if n in _WING:
            _scale_cube(c, old_pivots[n], by[n]["pivot"], size, h, size)
        elif n in _HEAD_DETAIL or n == "head":
            _scale_cube(c, old_pivots[n], by[n]["pivot"], h, h, h)
        else:  # root torso, spine, legs
            _scale_cube(c, old_pivots[n], by[n]["pivot"], h, h, size)


def _apply_experience(bones, cubes, exp):
    """EXPERIENCE (0-4): bigger hand nails (the wing-hand claw cubes grow
    about their pivots) and, on the texture side, more torn membranes.
    Returns (holes_mult, depth_mult) for the membrane painter."""
    k = 1 + 0.3 * exp
    n_nails = 0
    for c in cubes:
        if c["bone"] in ("wing_l_hand", "wing_r_hand") and \
                c["size"][2] > c["size"][0]:
            p, o, s = c["pivot"], c["origin"], c["size"]
            c["origin"] = [p[i] + (o[i] - p[i]) * k for i in range(3)]
            c["size"] = [x * k for x in s]
            n_nails += 1
    print(f"exp   nails x{k:.2f} ({n_nails} wing-hand claws)")
    return 0.5 + 0.5 * exp, 1 + 0.22 * exp


def _apply_age(bones, cubes, by, age):
    """AGE (0-10): neck posture (rest pitches bend more with age, head
    rotation compensated so the muzzle keeps its angle), facial detail
    (horns/crest/brow/cheek plates grow), and the whiskers ("mustache", the
    white variant's feature) from age 4 up, sized by age."""
    bend = 0.6 + 0.08 * age
    necks = [b for b in bones if b["name"].startswith("neck")]
    necks.sort(key=lambda b: int(b["name"][4:]))
    neck_sum = sum(b["rot"][0] for b in necks)
    for b in necks:
        b["rot"] = [b["rot"][0] * bend, b["rot"][1], b["rot"][2]]
    for b in bones:
        if b["name"] == "head":
            b["rot"] = [b["rot"][0] + neck_sum * (1 - bend),
                        b["rot"][1], b["rot"][2]]
    detail = 1 + 0.045 * age
    by_a = {b["name"]: b for b in bones}
    for c in cubes:
        if c["bone"] in _HEAD_DETAIL and c["bone"] not in ("snout",
                                                           "snout_tip", "jaw"):
            j = by_a[c["bone"]]["pivot"]
            _scale_cube(c, j, j, detail, detail, detail)
    if age >= 4:
        _add_whiskers(bones, cubes, by, age)
    print(f"age   {age}: neck bend x{bend:.2f}, face detail x{detail:.2f}, "
          f"whiskers={'yes' if age >= 4 else 'no'}")


def _add_whiskers(bones, cubes, by, age):
    """The trailing snout barbels (the white variant's mustache), two
    segments per side grown from the snout tip, sized by age. The anchor is
    at the muzzle tip IN THE SNOUT_TIP'S FRAME (its cubes sit near
    (0, 84, -128) - the frame origin is the model base, not the joint)."""
    tip = next((b for b in bones if b["name"] == "snout_tip"), None)
    if tip is None:
        return
    w = 0.4 + 0.07 * (age - 4)
    ln = 7.0 + 0.6 * (age - 4)
    for side, sx in (("l", 1), ("r", -1)):
        a = (2.6 * sx, 84.5, -131.0)
        b1 = {"name": f"whisker_{side}_1", "parent": "snout_tip",
              "pivot": list(a), "rot": [10, 28 * sx, 0]}
        bones.append(b1)
        c1 = {"bone": f"whisker_{side}_1",
              "origin": [a[0] - w / 2, a[1] - w / 2, a[2]],
              "size": [w, w, ln], "rot": [0, 0, 0], "pivot": [0, 0, 0],
              "inflate": 0.0, "mirror": False,
              "uv": _whisker_uv()[0], "uvraw": _whisker_uv()[1]}
        cubes.append(c1)
        b2 = {"name": f"whisker_{side}_2", "parent": f"whisker_{side}_1",
              "pivot": [a[0], a[1], a[2] + ln * 0.85],
              "rot": [16, 6 * sx, 0]}
        bones.append(b2)
        c2 = {"bone": f"whisker_{side}_2",
              "origin": [a[0] - w * 0.7, a[1] - w * 0.7,
                         a[2] + ln * 0.8],
              "size": [w * 0.8, w * 0.8, ln * 0.9], "rot": [0, 0, 0],
              "pivot": [0, 0, 0], "inflate": 0.0, "mirror": False,
              "uv": _whisker_uv()[0], "uvraw": _whisker_uv()[1]}
        cubes.append(c2)


def _whisker_uv():
    """A tiny rect in a free corner of the sheet for the whisker strands
    (painted horn by paint_texture). Returns (uv rects, uvraw) - the raw
    form is (ux, uy, uw, uh), NOT a rect, or the emitter writes a
    (6, 1302)-sized quad pointing outside the texture."""
    ux, uy, uw, uh = 0, 1296, 6, 6
    faces = ("north", "east", "south", "west", "up", "down")
    uvraw = {f: (ux, uy, uw, uh) for f in faces}
    uv = {f: (ux, uy, ux + uw, uy + uh) for f in faces}
    return uv, uvraw


# ------------------------------------------------------------- face helpers

def norm_rect(r):
    x1, y1, x2, y2 = r
    return (int(round(min(x1, x2))), int(round(min(y1, y2))),
            int(round(max(x1, x2))), int(round(max(y1, y2))))


def is_membrane(cube):
    """A zero-thickness plane whose silhouette is cut by texture alpha."""
    return abs(cube["size"][1]) < 1e-6


def sample_mean(img, rect):
    r = norm_rect(rect)
    if r[2] - r[0] < 1 or r[3] - r[1] < 1:
        return None
    crop = img.crop(r)
    if crop.getchannel("A").getextrema()[1] == 0:
        return None
    mean = ImageStat.Stat(crop.convert("RGB"),
                          mask=crop.getchannel("A")).mean
    return tuple(int(x) for x in mean)


def classify(color):
    """Nearest-palette material for a sampled face colour (Euclidean)."""
    if color is None:
        return "hide"
    best, best_d = None, math.inf
    for mat, rgb in PALETTE.items():
        d = sum((a - b) ** 2 for a, b in zip(color, rgb))
        if d < best_d:
            best, best_d = mat, d
    return best


# ------------------------------------------------------------- paint helpers

def _sh(c, f, a=255):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))),
            max(0, min(255, int(c[2] * f))), a)


def _grad_fill(drw, r, base, top_f, bot_f):
    x1, y1, x2, y2 = r
    for row in range(y1, y2):
        t = (row - y1) / max(1, y2 - y1 - 1)
        drw.line([(x1, row), (x2 - 1, row)], fill=_sh(base, top_f + (bot_f - top_f) * t))


def _mottle(drw, r, base, rng, strength=0.10, density=24):
    x1, y1, x2, y2 = r
    for _ in range(max(1, (x2 - x1) * (y2 - y1) // density)):
        px = rng.randrange(x1, x2)
        py = rng.randrange(y1, y2)
        f = 1.0 + rng.uniform(-strength, strength)
        drw.rectangle([px, py, min(px + rng.randint(0, 1), x2 - 1),
                       min(py + rng.randint(0, 1), y2 - 1)], fill=_sh(base, f))


def _scales(drw, r, base, rng):
    x1, y1, x2, y2 = r
    w, h = x2 - x1, y2 - y1
    if w < 6 or h < 6:
        return
    s = 4 if min(w, h) >= 12 else 3
    dark, light = _sh(base, 0.78), _sh(base, 1.16)
    row = 0
    for py in range(y1 + 2, y2 - 1, s):
        off = (s // 2) if row % 2 else 0
        for px in range(x1 + 1 + off, x2 - 1, s):
            drw.line([(max(x1, px - 1), py), (min(x2 - 1, px + 1), py)], fill=dark)
            drw.point((px, py - 1), fill=light)
        row += 1


def _edge_ao(drw, r, base, f=0.82):
    x1, y1, x2, y2 = r
    drw.rectangle([x1, y1, x2 - 1, y2 - 1], outline=_sh(base, f))


def _paint_hide(drw, r, base, rng, fname, scaly=True):
    if fname == "up":
        _grad_fill(drw, r, base, 0.90, 0.97)
    elif fname == "down":
        _grad_fill(drw, r, base, 1.10, 1.16)
    else:
        _grad_fill(drw, r, base, 0.92, 1.08)
    _mottle(drw, r, base, rng)
    if scaly:
        _scales(drw, r, base, rng)
    _edge_ao(drw, r, base)


def _paint_belly(drw, r, base, rng):
    _grad_fill(drw, r, base, 1.04, 0.96)
    x1, y1, x2, y2 = r
    for py in range(y1 + 1, y2, 4):
        drw.line([(x1, py), (x2 - 1, py)], fill=_sh(base, 0.82))
        if py + 1 < y2:
            drw.line([(x1, py + 1), (x2 - 1, py + 1)], fill=_sh(base, 1.10))
    _mottle(drw, r, base, rng, strength=0.05, density=40)
    _edge_ao(drw, r, base, 0.85)


def _paint_horn(drw, r, base, rng):
    x1, y1, x2, y2 = r
    w, h = x2 - x1, y2 - y1
    _grad_fill(drw, r, base, 0.92, 1.06)
    ring = _sh(base, 0.84)
    if w >= h:
        for px in range(x1 + 2, x2 - 1, 3):
            drw.line([(px, y1), (px, y2 - 1)], fill=ring)
    else:
        for py in range(y1 + 2, y2 - 1, 3):
            drw.line([(x1, py), (x2 - 1, py)], fill=ring)
    _edge_ao(drw, r, base, 0.85)


def _paint_eye(drw, img, r, base, fname, iris):
    x1, y1, x2, y2 = norm_rect(r)
    w, h = x2 - x1, y2 - y1
    drw.rectangle([x1, y1, x2 - 1, y2 - 1], fill=_sh(base, 0.85))
    if fname in ("up", "down") or w < 3 or h < 3:
        return
    if w >= 5 and h >= 4:
        drw.rectangle([x1 + 1, y1 + 1, x2 - 2, y2 - 2], fill=_sh(iris, 1.0))
    cx = (x1 + x2) // 2
    sw = 2 if w >= 9 else 1
    drw.rectangle([cx - sw // 2, y1 + 1, cx - sw // 2 + sw - 1, y2 - 2],
                  fill=(14, 10, 10, 255))
    img.putpixel((max(x1, cx - sw), y1 + 1), (240, 244, 248, 255))


# ------------------------------------------------------------- membrane rule

def _stable_hash(*parts):
    """Process-stable hash (builtin hash() is salted per run)."""
    h = hashlib.md5()
    for p in parts:
        h.update(str(p).encode("utf-8"))
    return int.from_bytes(h.digest()[:8], "little")


def _carve_hole(img, cx, cy, rng, max_cy):
    """One little membrane tear centred at (cx, cy): an irregular blob cut to
    alpha 0, sized in texels - mostly small holes, occasional bigger rents."""
    big = rng.random() < 0.30
    rx = rng.uniform(1.5, 2.6) + (rng.uniform(2.5, 5.5) if big else 0)
    ry = rng.uniform(1.2, 2.0) + (rng.uniform(2.0, 4.0) if big else 0)
    ang = rng.random() * math.pi
    px, py = [], []
    n = rng.randint(6, 10)
    for k in range(n):
        a = 2 * math.pi * k / n + rng.uniform(-0.25, 0.25)
        rr = math.sqrt(rng.random()) * 0.9 + 0.1
        px.append(int(cx + math.cos(a) * rx * rr * math.cos(ang)
                      - math.sin(a) * ry * rr * math.sin(ang)))
        py.append(int(cy + math.cos(a) * rx * rr * math.sin(ang)
                      + math.sin(a) * ry * rr * math.cos(ang)))
    img_draw = ImageDraw.Draw(img)
    img_draw.polygon(list(zip(px, py)), fill=(0, 0, 0, 0))
    if rng.random() < 0.4:                # a short ragged crack tail
        sx, sy = px[0], py[0]
        img_draw.line([(sx, sy),
                       (sx + int(rng.uniform(-3, 3)),
                        min(max_cy - 1, sy + int(rng.uniform(3, 8))))],
                      fill=(0, 0, 0, 0), width=1)


def paint_membrane_slice(img, rect, meta, seed, holes=1.0, depth_mult=1.0,
                         base=None):
    """Paints one membrane pair's shared slice: the polygon BETWEEN its two
    bones. The two bone lines (meta['strip'], in the plane's local frame)
    bound the sail - the rectangle's corners that cross the bones stay
    invisible, so the sheet is a triangle/trapezoid, not a rectangle trying
    to fit one. The polygon narrows toward the wrist end (the bones converge
    at the hand) and the free (tip) end is torn: a ragged cut with little
    holes whose depth scales with the slice's position t along the set, never
    reaching the bone lines. Flat (131,121,121) base, mottle, lighter toward
    the free edge - the reference's look."""
    x1, y1, x2, y2 = rect
    w, h = x2 - x1, y2 - y1
    if w < 8 or h < 8:
        return
    rng = random.Random(seed * 1000003 + _stable_hash(meta["pair_key"]))
    base = base or MEMBRANE_BASE
    t = meta["t"]
    tip = meta["tip"]
    if tip in ("left", "right"):
        length, gap, rows = w, h, True
    else:
        length, gap, rows = h, w, False

    def px(u, v):
        if rows:
            return (x1 + u if tip == "left" else x2 - 1 - u, y1 + v)
        return (x1 + v, y1 + u if tip == "top" else y2 - 1 - u)

    pw, pd = meta["pw"], meta["pd"]
    (m1, b1), (m2, b2) = meta["strip"]

    def local_coords(u, v):
        """Plane-local (x along the length, z across the gap) for a pixel at
        distance u from the tip edge and gap offset v (v=0 at the image top;
        the plane's local z=0 maps to the region bottom)."""
        fu = u / max(1, length - 1)
        fv = v / max(1, gap - 1)
        if rows:
            x = pw * fu if tip == "left" else pw * (1 - fu)
            z = pd * (1 - fv)
        else:
            z = pd * (1 - fu) if tip == "top" else pd * fu
            x = pw * fv
        return x, z

    def strip_rows(x):
        """Image v-range of the strip (between the two bone lines) at local
        x, or None if the bones don't bound a sensible strip there."""
        z1 = m1 * x + b1
        z2 = m2 * x + b2
        lo = max(0.0, min(z1, z2))
        hi = min(pd, max(z1, z2))
        if hi - lo < 0.02 * pd:
            return None
        return ((1 - hi / pd) * (gap - 1), (1 - lo / pd) * (gap - 1))

    # brachial planes (Fore<->W4): the fore line is nearly perpendicular to
    # the finger planes, so the strip is degenerate there - fall back to the
    # full rectangle rather than a sliver
    sr_tip = strip_rows(local_coords(0, 0)[0])
    strip_off = sr_tip is None or (sr_tip[1] - sr_tip[0]) < 0.3 * (gap - 1)
    if strip_off:
        def strip_rows(x):
            return (0, gap - 1)

    depth_max = (0.10 + 0.35 * t) * length * depth_mult

    def cut_at(v):
        g = v / max(1, gap - 1)
        shape = math.sin(math.pi * max(0.0, min(1.0, g))) ** 1.3
        j = rng.uniform(0.75, 1.25)            # ragged
        if rng.random() < 0.06:
            j += rng.uniform(0.4, 0.9)         # a deep rent, mid-gap only
        return min(0.55 * length, depth_max * shape * j)

    band = max(2, gap // 16)                   # AO rim along the bone lines
    band_w = max(2, length // 14)              # AO rim at the wrist end
    for v in range(gap):
        u0 = min(length - 1, max(0, int(round(cut_at(v)))))
        for u in range(u0, length):
            x, z = local_coords(u, v)
            sr = strip_rows(x)
            if sr is None:
                continue
            va, vb = sr
            if not (va <= v <= vb):
                continue                       # outside the bone strip
            f = 0.94 + 0.06 * (1 - u / max(1, length - 1))
            f *= 1.0 + rng.uniform(-0.03, 0.03)
            img.putpixel(px(u, v), _sh(base, f))
    # AO along the bone lines + at the wrist apex
    for u in range(length):
        x, _z = local_coords(u, 0)
        sr = strip_rows(x)
        if sr is None:
            continue
        va, vb = sr
        va, vb = int(round(va)), int(round(vb))
        if u >= length - band_w:
            rows_ao = [va, vb]                 # the apex: the seam only
        else:
            rows_ao = (list(range(va, min(gap, va + band)))
                       + list(range(max(0, vb - band + 1), vb + 1)))
        for v in rows_ao:
            if not (va <= v <= vb):            # never outside the bone strip
                continue
            f = 0.90 if u >= length - band_w else (0.87 if v <= va + band - 1 else 0.90)
            img.putpixel(px(u, v), _sh(base, f))

    # the little holes: inside the strip, biased toward the tip end, clear of
    # the bone rims so the polygon stays connected along both bones
    n_holes = max(1, int(round(holes * (0.3 + 1.7 * t) * (w * h) / 1600)))
    for _ in range(n_holes):
        for _try in range(24):
            u = int(rng.uniform(0.05, 0.6) * length)
            v = rng.randrange(band + 1, gap - band - 1)
            x, z = local_coords(u, v)
            sr = strip_rows(x)
            if sr is None:
                continue
            va, vb = sr
            if not (va + band <= v <= vb - band):
                continue                       # clear of the bone lines
            if u >= int(round(cut_at(v))) + 3:
                break
        else:
            continue
        _carve_hole(img, *px(u, v), rng, y2 - 1)


def _slice_stats(img, rect, meta):
    """Per-slice verification: fill %, whether the strip's two bone lines are
    solid (the membrane is connected to both bones), and the mean tear depth
    from the tip end."""
    x1, y1, x2, y2 = rect
    w, h = x2 - x1, y2 - y1
    tip = meta["tip"]
    rows = tip in ("left", "right")
    length = w if rows else h
    gap = h if rows else w
    pw, pd = meta["pw"], meta["pd"]
    (m1, b1), (m2, b2) = meta["strip"]

    def local_coords(u, v):
        fu = u / max(1, length - 1)
        fv = v / max(1, gap - 1)
        if rows:
            x = pw * fu if tip == "left" else pw * (1 - fu)
            z = pd * (1 - fv)
        else:
            z = pd * (1 - fu) if tip == "top" else pd * fu
            x = pw * fv
        return x, z

    def strip_rows(x):
        z1 = m1 * x + b1
        z2 = m2 * x + b2
        lo = max(0.0, min(z1, z2))
        hi = min(pd, max(z1, z2))
        if hi - lo < 0.02 * pd:
            return None
        return ((1 - hi / pd) * (gap - 1), (1 - lo / pd) * (gap - 1))

    # same brachial fallback as the painter
    sr_tip = strip_rows(local_coords(0, 0)[0])
    if sr_tip is None or (sr_tip[1] - sr_tip[0]) < 0.3 * (gap - 1):
        def strip_rows(x):
            return (0, gap - 1)

    def img_px(u, v):
        if rows:
            return (x1 + u if tip == "left" else x2 - 1 - u, y1 + v)
        return (x1 + v, y1 + u if tip == "top" else y2 - 1 - u)

    a = img.crop(rect).getchannel("A")
    fill = sum(1 for v in a.get_flattened_data() if v > 128) / max(1, w * h)

    solid_ok = solid_n = 0
    for u in range(0, length, 4):
        sr = strip_rows(local_coords(u, 0)[0])
        if sr is None:
            continue
        va, vb = int(round(sr[0])), int(round(sr[1]))
        if vb - va < 1:
            continue
        solid_n += 1
        solid_ok += (img.getpixel(img_px(u, va))[3] > 128
                     and img.getpixel(img_px(u, vb))[3] > 128)
    solid = solid_ok / max(1, solid_n)

    tear_sum = tear_n = 0
    sr0 = strip_rows(local_coords(0, 0)[0])
    if sr0 is not None:
        va0, vb0 = int(round(sr0[0])), int(round(sr0[1]))
        for v in range(va0 + 1, vb0):
            d = 0
            while d < length and img.getpixel(img_px(d, v))[3] <= 128:
                d += 1
            tear_sum += d
            tear_n += 1
    tear = tear_sum / max(1, tear_n)
    return fill, solid, tear


# ------------------------------------------------------ wing anatomy + plan

def _rot(v, r):
    rx, ry, rz = (math.radians(a) for a in r)
    x, y, z = v
    cx, sx = math.cos(rx), math.sin(rx); y, z = y * cx - z * sx, y * sx + z * cx
    cy, sy = math.cos(ry), math.sin(ry); x, z = x * cy + z * sy, -x * sy + z * cy
    cz, sz = math.cos(rz), math.sin(rz); x, y = x * cz - y * sz, x * sz + y * cz
    return (x, y, z)


def _world(by, name, p):
    """Bone-chain transform: local point p of bone `name` -> world space."""
    b = by[name]
    q = tuple(b["pivot"][i] + _rot(tuple(p[j] - b["pivot"][j] for j in range(3)),
                                   b.get("rot", (0, 0, 0)))[i] for i in range(3))
    return _world(by, b["parent"], q) if b.get("parent") else q


def _spar_lines(by, side, cubes_by_bone):
    """World-space spar lines + wrist point for one wing. Returns
    {stem: (a, b)} with stems 'fore', 'wing_<side>_finger1..4', plus 'hand'."""
    lines = {}

    def cube_corners(name, c):
        o, s = c["origin"], c["size"]
        p = c.get("pivot", o)
        r = c.get("rot", (0, 0, 0))
        return [_world(by, name, tuple(p[i] + _rot(
            (o[0] + x - p[0], o[1] + y - p[1], o[2] + z - p[2]), r)[i]
            for i in range(3)))
            for x in (0, s[0]) for y in (0, s[1]) for z in (0, s[2])]

    def far_end(name):
        """The world corner of the chain's last spar cube that is farthest
        from the bone's pivot - the bone's tip."""
        base = _world(by, name, by[name]["pivot"])
        seg, cs = name, None
        while True:
            kids = [b for b in by.values()
                    if b.get("parent") == seg and not b["name"].endswith("c")]
            nxt = kids[0]["name"] if kids else None
            if not nxt:
                break
            seg = nxt
        for c in cubes_by_bone.get(seg, []):
            if abs(c["size"][1]) < 1e-6:
                continue
            cs = cube_corners(seg, c)
        if not cs:
            return base
        return max(cs, key=lambda q: math.dist(q, base))

    # fore: from the elbow (far end of the arm chain) to the hand
    arm_tip = far_end(f"wing_{side}_arm")
    hand = _world(by, f"wing_{side}_hand", by[f"wing_{side}_hand"]["pivot"])
    lines["fore"] = (arm_tip, hand)
    lines["hand"] = hand
    for i in range(1, 5):
        stem = f"wing_{side}_finger{i}"
        lines[stem] = (_world(by, stem, by[stem]["pivot"]), far_end(stem))
    return lines


def _plane_meta(cube, by, lines, set_spec, hand):
    """Per-plane orientation by authoring intent. The planes are strips
    authored along the spar in the finger's frame, so the plane's LONG edges
    run along the membrane (the two bone edges) and the SHORT edges are the
    ends. The wrist end = the short edge whose world midpoint is nearest the
    hand; the tip (free) end is the other.
    Also computes ``strip``: the two set bone lines expressed in the plane's
    LOCAL frame as z(x) = m*x + b (x along the plane's length axis, z across
    the gap). The painted polygon is the strip BETWEEN those two lines - the
    parts of the rectangle that cross the bones stay invisible (a rectangle
    must not try to fit a triangle)."""
    o = cube["origin"]
    s = cube["size"]
    p = cube.get("pivot", o)
    r = cube.get("rot", (0, 0, 0))

    def corner(dx, dz):
        return _world(by, cube["bone"], tuple(p[i] + _rot(
            (o[0] + dx - p[0], o[1] - p[1], o[2] + dz - p[2]), r)[i]
            for i in range(3)))

    A = corner(0, 0)          # local (0,0)  = rect top-left
    B = corner(s[0], 0)       # local (w,0)  = rect top-right
    C = corner(0, s[2])       # local (0,d)  = rect bottom-left
    D = corner(s[0], s[2])    # local (w,d)  = rect bottom-right
    lo_line = lines[set_spec[1]]

    def mid(pa, pb):
        return tuple((pa[i] + pb[i]) / 2 for i in range(3))

    # the two ends: for a strip along the spar (w >= d) they are the local
    # x=0/x=w short edges (rect left/right); the bones are the top/bottom
    if s[0] >= s[2]:
        ends = {"left": (A, C), "right": (B, D)}
    else:
        ends = {"top": (A, B), "bottom": (C, D)}
    wrist = min(ends, key=lambda k: math.dist(mid(*ends[k]), hand))
    tip = {"left": "right", "right": "left",
           "top": "bottom", "bottom": "top"}[wrist]

    # the strip: both set bones projected onto the plane's local frame
    def unit(v):
        n = math.sqrt(sum(x * x for x in v)) or 1.0
        return tuple(x / n for x in v)

    def project(p0, p1):
        """Bone line p0-p1 in plane coords: returns (m, b) with z = m*x + b."""
        X = unit(tuple(B[i] - A[i] for i in range(3)))
        Z = unit(tuple(C[i] - A[i] for i in range(3)))

        def proj(p):
            d = tuple(p[i] - A[i] for i in range(3))
            return (sum(d[i] * X[i] for i in range(3)),
                    sum(d[i] * Z[i] for i in range(3)))
        (x0, z0), (x1, z1) = proj(p0), proj(p1)
        if abs(x1 - x0) < 1e-6:
            return (0.0, (z0 + z1) / 2)
        m = (z1 - z0) / (x1 - x0)
        return (m, z0 - m * x0)

    strip = tuple(project(*lines[t]) for t in set_spec)
    # raw t: plane centre projected on the set axis (hand -> lower-bone tip);
    # normalized within the set by _membrane_plan
    axis = tuple(lo_line[1][i] - hand[i] for i in range(3))
    al2 = sum(x * x for x in axis)
    ctr = mid(A, D)
    t_raw = sum((ctr[i] - hand[i]) * axis[i] for i in range(3)) / al2 if al2 else 0.0
    return {"wrist": wrist, "tip": tip, "t_raw": t_raw,
            "strip": strip, "pw": s[0], "pd": s[2]}


def _membrane_plan(cubes, by):
    """Builds the ordered per-plane plan for every membrane cube in geo order
    (left wing first, then right): (pair_key, stem, size, meta, side). The
    wings are symmetric, so a pair - the same set + position on both wings -
    shares ONE painted slice and ONE rect; the right-wing plane samples it
    with INVERTED UVs so the torn end lands at its tip. The paint uses the
    left-side meta. t is normalized within each set (left side): the
    wrist-most plane -> 0 (nearly whole), the tip-most -> 1."""
    plan = []
    per_set = {}
    cubes_by_bone = {}
    for cube in cubes:
        cubes_by_bone.setdefault(cube["bone"], []).append(cube)
    for cube in cubes:
        if not is_membrane(cube):
            continue
        stem = cube["bone"]
        set_name = PLANE_SET[stem]
        idx = per_set.get(set_name, 0)
        per_set[set_name] = idx + 1
        side = "r" if (stem.endswith("r") or "_r_" in stem) else "l"

        def localize(s):
            return s.replace("wing_l_", f"wing_{side}_").replace("wing_r_",
                                                                 f"wing_{side}_")
        up, lo = (localize(s) for s in MEMBRANE_SETS[set_name])
        lines = _spar_lines(by, side, cubes_by_bone)
        meta = _plane_meta(cube, by, lines, (up, lo), lines["hand"])
        plan.append(((set_name, idx), stem, tuple(cube["size"]), meta, side))
    # normalize t within each set over the LEFT-side metas (the paint source)
    raw = {}
    for key, stem, _sz, meta, side in plan:
        if side == "l":
            raw.setdefault(key[0], []).append(meta["t_raw"])
    for key, stem, _sz, meta, side in plan:
        if side == "r" or key[0] not in raw:
            continue
        vals = sorted(raw[key[0]])
        lo_v, hi_v = vals[0], vals[-1]
        t = 0.5 if math.isclose(lo_v, hi_v) else \
            (meta["t_raw"] - lo_v) / (hi_v - lo_v)
        meta["t"] = max(0.0, min(1.0, t))
        meta.pop("t_raw", None)
    return plan


def _layout_slices(plan):
    """Shelf-packs one rect per PAIR into MEMBRANE_STRIP, preserving the left
    plane's aspect. Starts at SLICE_TEXEL px per unit and steps the density
    down until everything fits (a size-3 dragon's wings are three times as
    wide). Returns {pair_key: rect}."""
    strip = MEMBRANE_STRIP
    sx1, sy1, sx2, sy2 = strip
    sizes = {}
    for key, stem, (w, _y, d), meta, side in plan:
        if key not in sizes:
            sizes[key] = (w, d)
    s = SLICE_TEXEL
    while s >= 1.2:
        x, y, shelf_h = sx1, sy1, 0
        ok = True
        rects = {}
        for key, (w, d) in sizes.items():
            rw = max(4, int(round(w * s)))
            rh = max(4, int(round(d * s)))
            if x + rw > sx2:
                x, y = sx1, y + shelf_h + 4
                shelf_h = 0
            if y + rh > sy2:
                ok = False
                break
            rects[key] = (x, y, x + rw, y + rh)
            x += rw + 4
            shelf_h = max(shelf_h, rh)
        if ok:
            if s < SLICE_TEXEL:
                print(f"uv    slice density stepped to {s:.1f} px/unit")
            return rects
        s -= 0.5
    raise SystemExit("membrane strip too small even at min density")


# ------------------------------------------------------------- membrane rule

def reuv_membranes(text, rects, sides):
    """Re-stamps each membrane plane's up/down UV rects onto its PAIR's clean
    slice rect (``rects`` in geo order), editing the raw reference text in
    place (Blockbench's compact serializer is not Python-json, so the patch
    is line-targeted). Membrane cubes are found by their zero Y size - the
    only 0-thick cubes in the model. The LEFT wing samples the rect plain;
    the RIGHT wing samples it INVERTED (uv origin on the far side, negative
    width, the reference's own sign convention) so the shared sheet - whose
    torn end sits at the rect's u_min side - lands at the right wing's tip
    too. Everything else stays byte-identical."""
    import re
    size_re = re.compile(r'^\s*"size": \[[^\]]+,\s*0(?:\.0+)?,\s*[^\]]+\],?$')
    uv_open = re.compile(r'^(\s*)"uv": \{$')
    uv_close = re.compile(r'^\s*\}$')
    up_re = re.compile(r'^(\s*)"up": (.*)$')
    down_re = re.compile(r'^(\s*)"down": (.*)$')
    lines = text.split("\n")
    out = []
    i, n = 0, len(lines)
    n_planes = 0
    while i < n:
        line = lines[i]
        if size_re.match(line.rstrip("\r")):
            j = i + 1
            while j < n and not uv_open.match(lines[j].rstrip("\r")):
                j += 1
            if j < n:
                k = j + 1
                while k < n and not uv_close.match(lines[k].rstrip("\r")):
                    k += 1
                rx1, ry1, rx2, ry2 = rects[n_planes]
                rw, rh = rx2 - rx1, ry2 - ry1
                inv = sides[n_planes] == "r"
                u_lo, u_hi = (rx1, rx2) if not inv else (rx2, rx1)
                out.append(line)
                out.extend(lines[i + 1:j + 1])
                for idx in range(j + 1, k):
                    l = lines[idx].rstrip("\r")
                    mu, md = up_re.match(l), down_re.match(l)
                    if mu:
                        out.append(f'{mu.group(1)}"up": {{"uv": [{u_lo}, {ry1 + rh}], '
                                   f'"uv_size": [{-rw if inv else rw}, -{rh}]}},\r')
                    elif md:
                        out.append(f'{md.group(1)}"down": {{"uv": [{u_lo}, {ry1}], '
                                   f'"uv_size": [{-rw if inv else rw}, {rh}]}}\r')
                    else:
                        out.append(lines[idx])
                if k < n:
                    out.append(lines[k])
                    n_planes += 1
                    i = k + 1
                    continue
        out.append(line)
        i += 1
    print(f"uv    re-stamped {n_planes} membrane planes onto "
          f"{len(rects)} shared slice rects (right wing inverted)")
    return "\n".join(out)


# ------------------------------------------------------------------- texture

def age_palette(age):
    """The black variant's palette lerped with age: young = lighter, old =
    darker. `age` None keeps the reference palette."""
    if age is None:
        return PALETTE
    young = {
        "hide": (62, 62, 70), "hide_dark": (46, 46, 54),
        "membrane": (140, 130, 130), "horn": (128, 122, 136),
        "ridge": (74, 72, 84), "eye": (112, 106, 62),
        "socket": (20, 18, 24), "teeth": (206, 200, 192),
        "belly": (116, 112, 124), "mouth": (50, 20, 22),
        "iris": (252, 226, 40),
    }
    old = {
        "hide": (34, 34, 40), "hide_dark": (24, 24, 30),
        "membrane": (116, 106, 106), "horn": (106, 100, 114),
        "ridge": (48, 46, 58), "eye": (96, 90, 50),
        "socket": (10, 9, 14), "teeth": (186, 180, 172),
        "belly": (92, 88, 100), "mouth": (34, 12, 14),
        "iris": (248, 214, 24),
    }
    f = max(0.0, min(1.0, age / 10.0))
    return {k: tuple(int(a + (b - a) * f) for a, b in zip(young[k], old[k]))
            for k in PALETTE}


def paint_texture(cubes, plan, rects, ref_png, out_path, holes=1.0, seed=1,
                  palette=None, depth_mult=1.0):
    """Generates the sheet. Non-membrane faces are classified against the
    (possibly age-shifted) palette from their sampled reference colour and
    repainted; each membrane slice is then painted by rule into its own clean
    rect (the polygon between its two bones)."""
    palette = palette or PALETTE
    ref = Image.open(ref_png).convert("RGBA")
    img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    drw = ImageDraw.Draw(img)
    counts = {}
    n_membrane = 0
    for cube in cubes:
        if is_membrane(cube):
            n_membrane += 1
            continue
        if cube["bone"].startswith("whisker"):
            rng = random.Random(seed * 104729 + _stable_hash(cube["bone"], "w") % 2 ** 31)
            for fname, rect in cube["uv"].items():
                r = norm_rect(rect)
                if r[2] - r[0] < 1 or r[3] - r[1] < 1:
                    continue
                _paint_horn(drw, r, palette["horn"], rng)
            counts["whisker"] = counts.get("whisker", 0) + 1
            continue
        for fname, rect in cube["uv"].items():
            r = norm_rect(rect)
            if r[2] - r[0] < 1 or r[3] - r[1] < 1:
                continue
            rng = random.Random(seed * 104729 + _stable_hash(cube["bone"], fname) % 2 ** 31)
            base_c = sample_mean(ref, rect)
            mat = classify(base_c)
            counts[mat] = counts.get(mat, 0) + 1
            base = palette.get(mat, palette["hide"])
            if mat in ("hide", "hide_dark", "ridge"):
                _paint_hide(drw, r, base, rng, fname, scaly=(mat != "ridge"))
            elif mat == "belly":
                _paint_belly(drw, r, base, rng)
            elif mat in ("horn", "teeth"):
                _paint_horn(drw, r, base, rng)
            elif mat == "eye":
                _paint_eye(drw, img, r, base, fname, palette["iris"])
            elif mat == "socket":
                _grad_fill(drw, r, base, 0.9, 1.05)
            else:  # mouth
                _grad_fill(drw, r, base, 0.75, 1.0)
    painted = set()
    for key, stem, _sz, meta, side in plan:
        if side == "r" or key in painted:
            continue
        painted.add(key)
        meta = dict(meta, pair_key=stem)
        paint_membrane_slice(img, rects[key], meta, seed, holes, depth_mult,
                             palette["membrane"])
    img.save(out_path)
    print(f"tex   {os.path.basename(out_path)}  membranes={n_membrane} "
          f"slices={len(painted)} "
          f"materials={dict(sorted(counts.items()))}")


# ------------------------------------------------------------ grounding check

def check_grounding(geo_path: str, strict=True):
    """Verifies the wing hands sit at the ground plane in the planted stance,
    the way the user set them in the reference. Reuses build_dragon's ground
    solve, so the report is exactly what the game's idle/walk poses use.
    With modifiers the planted-stance solve is scale-naive (it targets the
    hand pivot, whose authored rest rotation sweeps a scaled cube deeper), so
    modified models report only - the stance is handled with the animations."""
    sys.path.insert(0, HERE)
    import build_dragon as bd
    bd.reset()
    bd.load_geo(geo_path)
    bd.sync_variant_to_geo(bd.BLACK)
    st, _plant, _nx, _hx = bd._ground_stance(bd.BLACK)
    tf = bd.bone_world_transform(st)

    def min_y(name):
        pts = []
        for c in bd.CUBES:
            if c.bone != name:
                continue
            for x in (c.lo[0], c.hi[0]):
                for y in (c.lo[1], c.hi[1]):
                    for z in (c.lo[2], c.hi[2]):
                        pts.append(tf[c.bone]((x, y, z))[1])
        return min(pts) if pts else math.inf

    toes = min_y("leg_l_toes")
    lh = min_y("wing_l_hand")
    rh = min_y("wing_r_hand")
    # hands must rest at the same level as the toe pads (the ground plane)
    ok = (abs(lh - rh) < 4.0 and abs(lh - toes) < 6.0
          and abs(rh - toes) < 6.0)
    print(f"ground wing_l_hand={lh:7.2f} wing_r_hand={rh:7.2f} "
          f"toes={toes:7.2f}  {'OK' if ok else ('CHECK (report only)' if not strict else 'CHECK')}")
    return ok if strict else True


# ---------------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser(description="Regenerate the dragon model+texture")
    ap.add_argument("ref", nargs="?", default=DEFAULT_REF)
    ap.add_argument("--holes", type=float, default=1.0,
                    help="membrane tear density multiplier (default 1.0)")
    ap.add_argument("--seed", type=int, default=1, help="texture RNG seed")
    ap.add_argument("--name", default="dragon",
                    help="output file base (default: dragon)")
    ap.add_argument("--identifier", default=None,
                    help="geometry identifier override (default: "
                         "geometry.allunderheaven.<name>)")
    ap.add_argument("--no-texture", action="store_true",
                    help="emit geometry only; skip the texture paint "
                         "(the png is supplied by another step)")
    ap.add_argument("--size", type=float, default=1.0,
                    help="body length + wingspan multiplier (1 = current black)")
    ap.add_argument("--age", type=int, default=None,
                    help="age 0-10: color, face detail, whiskers, neck posture "
                         "(5 = current look)")
    ap.add_argument("--experience", type=int, default=0,
                    help="experience 0-4 (only if age >= 5): wing tears + nail size")
    ap.add_argument("--membrane-only", action="store_true",
                    help="paint only the membrane atlas to out/<name>_membrane.png "
                         "for quick iteration on the rule")
    args = ap.parse_args()

    os.makedirs(OUT_DIR, exist_ok=True)
    doc, bones, cubes = load_model(args.ref)
    by = {b["name"]: b for b in bones}

    eff_exp = args.experience if (args.age is None or args.age >= 5) else 0
    if eff_exp != args.experience:
        print(f"note  experience {args.experience} gated to 0 "
              f"(age {args.age} < 5)")
    holes_mult = depth_mult = 1.0
    if args.age is not None:
        _apply_age(bones, cubes, by, args.age)   # first: adds the whiskers
    if args.size != 1.0:
        _apply_size(bones, cubes, args.size)     # then size scales them too
    if eff_exp:
        holes_mult, depth_mult = _apply_experience(bones, cubes, eff_exp)
    by = {b["name"]: b for b in bones}

    plan = _membrane_plan(cubes, by)
    rects = _layout_slices(plan)
    geo_out = os.path.join(OUT_DIR, f"{args.name}.geo.json")
    png_out = os.path.join(OUT_DIR, f"{args.name}.png")
    ref_png = os.path.splitext(args.ref)[0] + ".png"
    if not os.path.exists(ref_png):
        ref_png = os.path.join(HERE, "dragon.png")

    if args.membrane_only:
        img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
        painted = set()
        for key, stem, _sz, meta, side in plan:
            if side == "r" or key in painted:
                continue
            painted.add(key)
            meta = dict(meta, pair_key=stem)
            paint_membrane_slice(img, rects[key], meta, args.seed, args.holes,
                                 depth_mult, MEMBRANE_BASE)
        path = os.path.join(OUT_DIR, f"{args.name}_membrane.png")
        img.save(path)
        print(f"membrane-only {os.path.basename(path)}  slices={len(painted)}")
        for key, stem, _sz, meta, side in plan:
            if side == "r":
                continue
            fill, solid, tear = _slice_stats(img, rects[key], meta)
            print(f"  {stem:22} t={meta['t']:.2f} tip={meta['tip']:<6} "
                  f"fill={100 * fill:4.1f}% "
                  f"solid={'1' if solid > 0.99 else '0'} "
                  f"tear={tear:.0f}px")
        print("wrote", path)
        return

    identifier = args.identifier or f"geometry.allunderheaven.{args.name}"
    modified = (args.size != 1.0 or args.age is not None or eff_exp)
    if modified:
        text = emit_geo_doc(bones, cubes, None, identifier=identifier)
    else:
        text = re.sub(r'"geometry\.[^"]*"', f'"{identifier}"', doc["_raw"])
    rect_list = [rects[key] for key, _s, _sz, _m, side in plan]
    side_list = [side for _k, _s, _sz, _m, side in plan]
    text = reuv_membranes(text, rect_list, side_list)
    emit_geo(text, geo_out)
    if not args.no_texture:
        palette = age_palette(args.age)
        paint_texture(cubes, plan, rects, ref_png, png_out,
                      holes=args.holes * holes_mult, seed=args.seed,
                      palette=palette, depth_mult=depth_mult)
    ok = check_grounding(geo_out, strict=not modified)
    print("wrote", OUT_DIR)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
