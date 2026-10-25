"""Convert an authored GeckoLib .geo.json (+ texture + animations) into a
Blockbench `geckolib_model` .bbmodel master.

This flips the pipeline to the ecosystem-standard direction: the .bbmodel is
the editable master in Blockbench (GeckoLib Models & Animations plugin), and
Blockbench exports .geo.json / .animations.json / texture for the mod.

Usage:
    python geo_to_bbmodel.py --geo <model.geo.json> --texture <tex.png>
        [--animations <file.animation.json>] [--out <out.bbmodel>] [--name <id>]
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import uuid

FACES = ("north", "east", "south", "west", "up", "down")


def new_uuid() -> str:
    return str(uuid.uuid4())


def cube_to_element(bone_name: str, index: int, cube: dict) -> dict:
    origin = cube["origin"]
    size = cube["size"]
    uv = cube.get("uv", {})
    faces = {}
    if isinstance(uv, dict):
        for fname in FACES:
            entry = uv.get(fname)
            if not entry:
                continue
            u, v = entry["uv"]
            w, h = entry.get("uv_size", [1, 1])
            faces[fname] = {"uv": [u, v, u + w, v + h], "texture": 0}
    else:
        u, v = uv
        w, h = cube.get("uv_size", size)
        for fname in FACES:
            faces[fname] = {"uv": [u, v, u + w, v + h], "texture": 0}
    return {
        "name": f"{bone_name}_{index}",
        "box_uv": False,
        "rescale": False,
        "locked": False,
        "from": list(origin),
        "to": [origin[0] + size[0], origin[1] + size[1], origin[2] + size[2]],
        "autouv": 0,
        "color": index % 8,
        "origin": list(cube.get("pivot", [0, 0, 0])),
        "rotation": list(cube.get("rotation", [0, 0, 0])),
        "inflate": float(cube.get("inflate", 0.0)),
        "mirror_uv": bool(cube.get("mirror", False)),
        "faces": faces,
        "type": "cube",
        "uuid": new_uuid(),
    }


def build_outliner(bones: list[dict], bone_uuid: dict, element_uuids: dict) -> list[dict]:
    children_by_parent: dict[str, list[str]] = {}
    for bone in bones:
        parent = bone.get("parent") or ""
        children_by_parent.setdefault(parent, []).append(bone["name"])

    def node(name: str) -> dict:
        bone = next(b for b in bones if b["name"] == name)
        return {
            "name": name,
            "origin": list(bone.get("pivot", [0, 0, 0])),
            "rotation": list(bone.get("rotation", [0, 0, 0])),
            "bedrock_binding": "",
            "export": True,
            "mirror_uv": False,
            "isOpen": False,
            "locked": False,
            "visibility": True,
            "autouv": 0,
            "uuid": bone_uuid[name],
            "children": [
                *element_uuids.get(name, []),
                *[node(child) for child in children_by_parent.get(name, [])],
            ],
        }

    return [node(child) for child in children_by_parent.get("", [])]


def load_animations(path: str) -> list[dict]:
    if not path:
        return []
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    animations = []
    for name, entry in (data.get("animations") or {}).items():
        animations.append(
            {
                "name": name,
                "loop": bool(entry.get("loop", False)),
                "animation_length": float(entry.get("animation_length", 0.0)),
                "bones": entry.get("bones", {}),
            }
        )
    return animations


def convert(geo: str, texture: str, animations: str | None, out: str, name: str) -> None:
    with open(geo, "r", encoding="utf-8") as f:
        geo_doc = json.load(f)
    geometry = geo_doc["minecraft:geometry"][0]
    identifier = geometry.get("description", {}).get("identifier", "")
    tex_w = geometry.get("description", {}).get("texture_width", 64)
    tex_h = geometry.get("description", {}).get("texture_height", 64)
    bones = geometry.get("bones", [])

    model_name = name or (
        identifier.rsplit(".", 1)[-1] if identifier else os.path.basename(geo).replace(".geo.json", "")
    )

    elements: list[dict] = []
    element_uuids: dict[str, list[str]] = {}
    for bone in bones:
        name = bone["name"]
        for index, cube in enumerate(bone.get("cubes", [])):
            element = cube_to_element(name, index, cube)
            elements.append(element)
            element_uuids.setdefault(name, []).append(element["uuid"])

    bone_uuid = {b["name"]: new_uuid() for b in bones}
    outliner = build_outliner(bones, bone_uuid, element_uuids)

    with open(texture, "rb") as f:
        b64 = base64.b64encode(f.read()).decode("ascii")
    texture_name = os.path.basename(texture)
    textures = [
        {
            "path": os.path.abspath(texture),
            "name": texture_name,
            "folder": "",
            "namespace": "",
            "id": "0",
            "group": "",
            "width": tex_w,
            "height": tex_h,
            "uv_width": tex_w,
            "uv_height": tex_h,
            "particle": False,
            "use_as_default": False,
            "layers_enabled": False,
            "sync_to_project": "",
            "render_mode": "default",
            "render_sides": "auto",
            "frame_time": 1,
            "frame_order_type": "loop",
            "frame_order": "",
            "frame_interpolate": False,
            "visible": True,
            "internal": True,
            "saved": True,
            "uuid": new_uuid(),
            "relative_path": texture_name,
            "source": f"data:image/png;base64,{b64}",
        }
    ]

    doc = {
        "meta": {"format_version": "3.2", "model_format": "geckolib_model", "box_uv": False},
        "name": model_name,
        "geo_name": model_name,
        "model_identifier": identifier,
        "resolution": {"width": tex_w, "height": tex_h},
        "elements": elements,
        "outliner": outliner,
        "textures": textures,
        "animations": load_animations(animations),
    }

    out_path = out or os.path.splitext(geo)[0] + ".bbmodel"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(doc, f, indent=1)
    print(f"wrote {out_path} ({len(elements)} cubes, {len(bones)} bones, {len(doc['animations'])} animations)")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--geo", required=True)
    parser.add_argument("--texture", required=True)
    parser.add_argument("--animations")
    parser.add_argument("--out")
    parser.add_argument("--name")
    args = parser.parse_args()
    convert(args.geo, args.texture, args.animations, args.out, args.name)


if __name__ == "__main__":
    main()
