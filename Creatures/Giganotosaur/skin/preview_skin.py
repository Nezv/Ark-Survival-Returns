#!/usr/bin/env python3
"""Software previews for the hand-painted Giganotosaur skin.

Renders the textured geometry (per-face UV islands exported by build_skin.py)
with a small numpy rasterizer so the skin can be reviewed without Minecraft.
"""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy.spatial.transform import Rotation as R

from build_skin import CORNERS, FACE_CORNERS, corner_st

HERE = Path(__file__).resolve().parent
LIGHT = np.array([-0.35, 0.72, 0.60])


def font(size):
    for name in ("segoeui.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(f"C:/Windows/Fonts/{name}", size)
        except OSError:
            continue
    return ImageFont.load_default()


def decode_geo(path):
    geo = json.loads(Path(path).read_text(encoding="utf-8"))
    geometry = geo["minecraft:geometry"][0]
    cubes = []
    for bone in geometry["bones"]:
        for cube in bone.get("cubes", []):
            size = np.array(cube["size"], float)
            origin = np.array(cube["origin"], float)
            origin[0] = -origin[0] - size[0]
            pivot = np.array(cube.get("pivot", bone["pivot"]), float) * [-1, 1, 1]
            rot = R.from_euler("xyz", np.array(cube.get("rotation", [0, 0, 0]), float) * [-1, -1, 1], degrees=True)
            corners = rot.apply(origin + CORNERS * size - pivot) + pivot
            cubes.append({"corners": corners, "faces": cube["uv"]})
    return cubes, geometry


def face_uvs(cube, key):
    u0, v0, w, h = cube["faces"][key]["uv"][0], cube["faces"][key]["uv"][1], \
        cube["faces"][key]["uv_size"][0], cube["faces"][key]["uv_size"][1]
    coords = []
    for index in FACE_CORNERS[key]:
        s, t = corner_st(key, CORNERS[index])
        coords.append([u0 + s * w, v0 + t * h])
    return np.array(coords)


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
            uv = face_uvs(cube, key)
            screen = np.column_stack([quad[:, 2], -quad[:, 1]])
            shade = 0.62 + 0.38 * float(np.clip(normal @ LIGHT, 0, 1))
            faces.append((screen, quad[:, 0], uv, shade, index * 6 + slot, normal))
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
    for screen, depth, uv, shade, fid_value, _ in faces:
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
        for _, _, _, shade, fid_value, normal in faces:
            mask = visible & (fid == fid_value)
            if not mask.any():
                continue
            hue = np.array([(fid_value * 47) % 200 + 40, (fid_value * 89) % 160 + 60,
                            (fid_value * 131) % 180 + 60], np.float32)
            canvas[mask] = hue * (0.55 + 0.45 * shade)
    return Image.fromarray(np.clip(canvas, 0, 255).astype(np.uint8))


def title_block(image, title, subtitle):
    draw = ImageDraw.Draw(image)
    draw.text((38, 23), title, fill="#e9eddf", font=font(31))
    if subtitle:
        draw.text((40, 68), subtitle, fill="#95a9ac", font=font(16))
    draw.text((40, image.height - 38), "Giganotosaur skin studio  /  per-face islands  /  painted in world space",
              fill="#718c92", font=font(14))
    return image


def cameras():
    return {
        "side": np.eye(3),
        "three_quarter": R.from_euler("yx", [-34, 10], degrees=True).as_matrix(),
        "front": R.from_euler("y", -90, degrees=True).as_matrix(),
        "head": R.from_euler("yx", [-30, 16], degrees=True).as_matrix(),
        "jaw": R.from_euler("yx", [-70, -14], degrees=True).as_matrix(),
        "tail": R.from_euler("yx", [-26, 12], degrees=True).as_matrix(),
    }


def texture_sheet(texture_path, out_path):
    texture = Image.open(texture_path).convert("RGB")
    scale = 3
    sheet = texture.resize((texture.width * scale, texture.height * scale), Image.NEAREST)
    draw = ImageDraw.Draw(sheet)
    for i in range(0, texture.width + 1, 16):
        color = "#2f4a44" if i % 64 else "#4f7a6e"
        draw.line((i * scale, 0, i * scale, sheet.height), fill=color, width=1)
        draw.line((0, i * scale, sheet.width, i * scale), fill=color, width=1)
    title_block(sheet, "Giganotosaur", "256x256 painted atlas (3x, 16px grid)").save(out_path)


def main():
    geo = HERE / "giganotosaurus.geo.json"
    texture_path = HERE / "giganotosaurus.png"
    report = json.loads((HERE / "skin_report.json").read_text(encoding="utf-8"))
    cubes, _ = decode_geo(geo)
    texture = Image.open(texture_path)
    out = HERE / "previews"
    out.mkdir(exist_ok=True)
    cams = cameras()
    specs = [
        ("side.png", "side", {}, f"{len(cubes)} textured cubes  |  {report['faces']} painted islands  |  bind pose"),
        ("three_quarter.png", "three_quarter", {}, "Three-quarter view"),
        ("front.png", "front", {}, "Front view"),
        ("head.png", "head", {"focus": np.array([0.0, 27.0, 23.5]), "zoom": 3.2}, "Head close-up"),
        ("jaw.png", "jaw", {"focus": np.array([0.0, 22.0, 23.0]), "zoom": 3.0}, "Jaw, teeth and tongue"),
        ("tail.png", "tail", {"focus": np.array([0.0, 17.0, -25.0]), "zoom": 1.7}, "Tail striping"),
    ]
    for name, cam_key, extra, subtitle in specs:
        image = render(cubes, texture, cams[cam_key], (1100, 780), **extra)
        title_block(image, "Giganotosaur", subtitle).save(out / name)
    debug = render(cubes, texture, cams["three_quarter"], (1100, 780), textured=False)
    title_block(debug, "Giganotosaur", "Atlas island map (flat debug color per cube face)").save(out / "uv_check.png")
    texture_sheet(texture_path, out / "texture_atlas.png")
    print("previews written to", out)


if __name__ == "__main__":
    main()
