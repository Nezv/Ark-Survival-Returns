#!/usr/bin/env python3
"""Build a brush-sized, part-aware Giganotosaur texture.

The v2/v3 painters evaluated a colour at nearly every atlas texel.  That is
useful for a continuous material, but it produces a noisy field when the
source is a rigid-cube extraction.  This pass treats the texture like a
Minecraft artist would: classify the surface into body parts, sample a small
number of large 3-D brush cells, then quantize each UV island into visible
brush blocks.  The mesh, native skeleton, UV packing, and animations are left
to the shared studio exporter.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts"))

import skin_studio as studio  # noqa: E402
from build_dinosaurs import PALETTES  # noqa: E402


STYLE = {
    **studio.GIGA_STYLE,
    # Atlas brush: every mark is at least this many texels where the face is
    # large enough.  World brushes control the body pattern independently of
    # atlas resolution.
    "brush_px": 4,
    "body_brush_s": 2.8,
    "body_brush_d": 0.13,
    "tail_band_period": 8.5,
    "tail_band_width": 2.1,
    "body_mark_period": 11.0,
    "body_mark_width": 3.8,
    "limb_brush": 0.24,
    "limb_band_period": 0.72,
    "limb_band_width": 0.26,
    "belly_edge": 0.33,
    "dorsal_edge": 0.72,
    "stripe_blend": 0.0,
    "stripe_weight": 0.0,
    "tail_stripe": 0.0,
    "leg_stripe": 0.0,
    "plate_period": 4.0,
    "plates": 0.0,
    "scute_period": 4.0,
    "scutes": 0.0,
    "ridge": 0.0,
    "scales": 0.0,
    "neck_folds": 0.0,
    "row_lines": 0.0,
    "grain_fine": 0.0,
    "grain_coarse": 0.0,
    "dorsal_wash": 0.0,
    "dither": 0.0,
    "quantize": True,
    "detail_scale": {"head": 3.2, "jaw": 2.8, "tongue": 2.4, "eye": 4.0, "claw": 3.0},
    "min_island": {"eye": 3, "tongue": 2, "head": 2, "jaw": 2, "legl": 2, "legr": 2,
                   "arml": 2, "armr": 2, "footl": 2, "footr": 2, "tail": 2, "claw": 2},
}

# A warm earth palette is closer to the supplied Minecraft references and is
# deliberately kept separate from the shared species palette so the method
# can be evaluated without changing any other creature.
BRUSH_PALETTE = [
    "#865038",  # flank
    "#b3764b",  # flank highlight
    "#563127",  # dorsal
    "#d09a64",  # belly
    "#efd09a",  # belly highlight
    "#3a211b",  # stripe
    "#dfad3c",  # eye
    "#a65350",  # tongue
]


def _snap(value, step):
    """Return the centre of a deterministic brush cell."""
    return (studio.np.floor(value / step) + 0.5) * step


def _band(value, period, width):
    """Hard-edged broad band with an explicit world-space brush width."""
    return studio.np.mod(value, period) < width


def _part_mask(part_ids, *names):
    mask = studio.np.zeros(len(part_ids), dtype=bool)
    for name in names:
        mask |= part_ids == studio.PART_ID[name]
    return mask


def brush_base_skin(points, normals, part_ids, bounds, palette, style, field):
    """Paint body roles first, then apply a few large, named marks."""
    pid = studio.np.clip(part_ids, 0, len(studio.PARTS) - 1)
    y, z = points[:, 1], points[:, 2]
    local_d = studio.np.clip(
        (y - bounds[pid, 2]) / studio.np.maximum(bounds[pid, 3] - bounds[pid, 2], 1e-6), 0.0, 1.0
    )

    torso = _part_mask(part_ids, "torso")
    neck = _part_mask(part_ids, "neck")
    tail = _part_mask(part_ids, "tail")
    head = _part_mask(part_ids, "head")
    jaw = _part_mask(part_ids, "jaw")
    tongue = _part_mask(part_ids, "tongue")
    leg = _part_mask(part_ids, "legl", "legr")
    arm = _part_mask(part_ids, "arml", "armr")
    foot = _part_mask(part_ids, "footl", "footr")
    claw = _part_mask(part_ids, "claw")
    horn = _part_mask(part_ids, "horn")
    feather = _part_mask(part_ids, "feather")
    body = torso | neck | tail | head
    limb = leg | arm | foot
    axial = body | jaw | tongue

    s_field, d_field = field.dorsal_weight(points)
    if s_field is None:
        body_s = z
        body_d = local_d
    else:
        body_s = studio.np.where(axial, s_field, z)
        body_d = studio.np.where(axial, d_field, local_d)

    # Quantizing the 3-D coordinates before colour lookup is the world-space
    # equivalent of a large brush: neighbouring faces share the same mark.
    brush_s = _snap(body_s, style["body_brush_s"])
    brush_d = _snap(body_d, style["body_brush_d"])
    body_color = studio.ramp(studio.np.clip(brush_d, 0.0, 1.0), palette)
    color = studio.np.where(body[:, None], body_color, palette["FLANK"])

    # Limbs use their own length axis, so arms and legs remain readable even
    # though they do not belong to the tail-to-snout field.
    limb_d = _snap(local_d, style["limb_brush"])
    limb_color = studio.ramp(studio.np.clip(0.18 + 0.64 * limb_d, 0.0, 1.0), palette)
    color = studio.np.where(limb[:, None], limb_color, color)

    # Deliberate broad regions: light underside, dark dorsal cap, and one
    # warm flank mark.  These are shapes, not texture noise.
    color = studio.np.where((axial & (body_d < style["belly_edge"]))[:, None], palette["BELLY2"], color)
    color = studio.np.where((axial & (body_d > style["dorsal_edge"]))[:, None], palette["DORSAL"], color)
    color = studio.np.where((body & (body_d > 0.42) & (body_d < 0.73)
                             & _band(brush_s, style["body_mark_period"], style["body_mark_width"]))[:, None],
                            palette["FLANK3"], color)

    # Tail bars follow the curved body field and have a true brush width.
    tail_bars = tail & (body_d > 0.34) & _band(brush_s, style["tail_band_period"], style["tail_band_width"])
    color = studio.np.where(tail_bars[:, None], palette["BACK"], color)

    # A single broad limb band gives the legs/arms the painted segmentation
    # visible in the reference images without sprinkling individual pixels.
    limb_bars = limb & _band(limb_d, style["limb_band_period"], style["limb_band_width"])
    color = studio.np.where(limb_bars[:, None], palette["BACK"], color)

    # Explicit role colours.  The foot/hand end cells act as nails; add_details
    # still draws the existing jaw profile, teeth, eyes and nostrils.
    nail = claw | (foot & (local_d < 0.26)) | (arm & (local_d < 0.16))
    color = studio.np.where(nail[:, None], palette["CLAW"], color)
    color = studio.np.where(jaw[:, None], studio.mix(palette["JAW"], palette["JAW2"], _snap(local_d, 0.20)), color)
    color = studio.np.where(tongue[:, None], studio.mix(palette["TONGUE2"], palette["TONGUE"], _snap(local_d, 0.25)), color)
    color = studio.np.where(horn[:, None], palette["HORN"], color)
    color = studio.np.where(feather[:, None], palette["FEATHER"], color)
    return color


def brush_compose(cubes, faces, bounds, features, palette, style, field, tex_size):
    """Compose per-face islands while enforcing a visible atlas brush size."""
    canvas = studio.np.zeros((tex_size, tex_size, 3), studio.np.float32)
    canvas[:] = studio.BACKGROUND
    brush = max(1, int(style.get("brush_px", 1)))
    for face in faces:
        points, normals, part_ids = studio.face_points(face)
        color = studio.paint_points(points, normals, part_ids, bounds, features, palette, style, field)
        grid = studio.np.clip(color.reshape(face["height"], face["width"], 3), 0, 255).astype(studio.np.float32)
        if brush > 1:
            rows = studio.np.minimum((studio.np.arange(face["height"]) // brush) * brush + brush // 2,
                                     face["height"] - 1)
            cols = studio.np.minimum((studio.np.arange(face["width"]) // brush) * brush + brush // 2,
                                     face["width"] - 1)
            grid = grid[studio.np.ix_(rows, cols)]
        canvas[face["y"]:face["y"] + face["height"], face["x"]:face["x"] + face["width"]] = grid

    # One-pixel padding copies the nearest brush colour, avoiding dark seams
    # without introducing a new mark of its own.
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


def brush_quant_palette(palette, style):
    roles = ["BELLY2", "BELLY", "FLANK3", "FLANK", "BACK", "DORSAL", "STRIPE",
             "JAW", "JAW2", "TONGUE", "TONGUE2", "EYE", "PUPIL", "SOCKET",
             "CLAW", "TEETH", "MOUTH", "HORN", "FEATHER"]
    colors = studio.np.array([palette[role] for role in roles] + [studio.BACKGROUND], studio.np.float32)
    return studio.np.unique(studio.np.clip(studio.np.round(colors), 0, 255).astype(studio.np.uint8), axis=0)


def main():
    creature = ROOT / "Creatures" / "Giganotosaur"
    # Keep the original Giga palette; only the mark construction changes.
    studio.base_skin = brush_base_skin
    studio.compose = brush_compose
    studio.build_quant_palette = brush_quant_palette
    report, geo, model, texture = studio.build_skin(
        "Giganotosaur", creature, BRUSH_PALETTE, style=STYLE,
        out_name="skin_v4", write_previews=True,
    )
    report_path = creature / "skin_v4" / "skin_report.json"
    report_doc = json.loads(report_path.read_text(encoding="utf-8"))
    report_doc["style"].update({
        "brush_px": STYLE["brush_px"],
        "body_brush_s": STYLE["body_brush_s"],
        "body_brush_d": STYLE["body_brush_d"],
        "tail_band_period": STYLE["tail_band_period"],
        "tail_band_width": STYLE["tail_band_width"],
    })
    report_path.write_text(json.dumps(report_doc, indent=2) + "\n", encoding="utf-8")
    print(f"v4: {texture}  {report['texture']['size'][0]}px  {report['metrics']['palette_colors']} colours")
    print(f"brush: {STYLE['brush_px']}px atlas / {STYLE['body_brush_s']} body units / {STYLE['body_brush_d']} dorsal units")
    print(f"checks: {report['checks']}")


if __name__ == "__main__":
    main()
