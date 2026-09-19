#!/usr/bin/env python3
"""Build a low-noise, direct-pattern Giganotosaur skin.

This pass keeps the v2 UV packing, rig-derived head frame, animation binding
and validation, but replaces the painter with a small set of broad anatomical
zones. It intentionally omits hash grain, random patches, scale speckles and
Bayer dithering so the result reads as Minecraft art instead of noisy material
noise.
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts"))

import skin_studio as studio  # noqa: E402
from build_dinosaurs import PALETTES  # noqa: E402


STYLE = {
    **studio.GIGA_STYLE,
    "base_scale": 1.8,
    "stripe_period": 18.0,
    "stripe_sharp": (0.48, 0.88),
    "stripe_span": (0.32, 0.72),
    "stripe_weight": 0.68,
    "stripe_blend": 0.18,
    "belly_width": 0.28,
    "plate_period": 4.0,
    "plates": 0.0,
    "scute_period": 4.5,
    "scutes": 0.20,
    "ridge": 0.16,
    "scales": 0.0,
    "neck_folds": 0.0,
    "row_lines": 0.0,
    "grain_fine": 0.0,
    "grain_coarse": 0.0,
    "dorsal_wash": 0.16,
    "dither": 0.0,
    "quantize": True,
    "detail_scale": {"head": 3.2, "jaw": 2.8, "tongue": 2.4, "eye": 4.0, "claw": 3.0},
    "min_island": {"eye": 3, "tongue": 2, "head": 2, "jaw": 2, "legl": 2, "legr": 2,
                   "arml": 2, "armr": 2, "footl": 2, "footr": 2, "tail": 2, "claw": 2},
}


def direct_base_skin(points, normals, part_ids, bounds, palette, style, field):
    """Paint broad zones and large bands; no per-pixel random noise."""
    x, y, z = points[:, 0], points[:, 1], points[:, 2]
    local_d = (y - bounds[part_ids, 2]) / (bounds[part_ids, 3] - bounds[part_ids, 2] + 1e-6)
    local_d = studio.np.clip(local_d, 0.0, 1.0)

    is_head = part_ids == studio.PART_ID["head"]
    is_jaw = part_ids == studio.PART_ID["jaw"]
    is_tongue = part_ids == studio.PART_ID["tongue"]
    is_torso = part_ids == studio.PART_ID["torso"]
    is_neck = part_ids == studio.PART_ID["neck"]
    is_tail = part_ids == studio.PART_ID["tail"]
    is_leg = (part_ids == studio.PART_ID["legl"]) | (part_ids == studio.PART_ID["legr"])
    is_arm = (part_ids == studio.PART_ID["arml"]) | (part_ids == studio.PART_ID["armr"])
    is_foot = (part_ids == studio.PART_ID["footl"]) | (part_ids == studio.PART_ID["footr"])
    is_claw = part_ids == studio.PART_ID["claw"]
    is_horn = part_ids == studio.PART_ID["horn"]
    is_feather = part_ids == studio.PART_ID["feather"]
    body = is_torso | is_neck | is_tail | is_head
    limb = is_leg | is_arm | is_foot
    axial = body | is_jaw | is_tongue

    s_field, d_field = field.dorsal_weight(points)
    if s_field is None:
        dorsal = local_d
        axial_s = z
    else:
        dorsal = studio.np.where(axial, d_field, local_d)
        axial_s = studio.np.where(axial, s_field, z)

    color = studio.ramp(local_d, palette)
    color = studio.np.where(limb[:, None], studio.ramp(0.24 + 0.48 * local_d, palette), color)

    # Direct, readable anatomical zones.
    color = studio.tint(color, palette["BELLY2"], 0.78 * (axial & (dorsal < style["belly_width"])))
    color = studio.tint(color, palette["DORSAL"], 0.72 * (axial & (dorsal > 0.78)))
    color = studio.tint(color, palette["DORSAL"], 0.18 * (up := (normals[:, 1] > 0.55)) * body)
    color = studio.tint(color, palette["BELLY2"], 0.22 * (normals[:, 1] < -0.55) * body)

    # Three or four large bands running along the body field.
    band_phase = (axial_s + 2.0) % style["stripe_period"]
    band = ((band_phase > 2.2) & (band_phase < 8.0) & axial
            & (dorsal > style["stripe_span"][0]) & (dorsal < style["stripe_span"][1]))
    stripe_color = studio.mix(palette["STRIPE"], palette["DORSAL"], style["stripe_blend"])
    color = studio.tint(color, stripe_color, style["stripe_weight"] * band)

    # A restrained dorsal ridge/scute rhythm; each mark is several pixels wide.
    ridge = up & body & (studio.np.abs(x) < 0.65)
    scute = ridge & ((studio.np.floor((axial_s + 1.0) / style["scute_period"]) % 2.0) == 0)
    color = studio.tint(color, stripe_color, style["ridge"] * ridge)
    color = studio.tint(color, stripe_color, style["scutes"] * scute)

    # Stable material colors for specialized regions.
    jaw_base = studio.mix(palette["JAW"], palette["JAW2"], 0.35 + 0.30 * (1.0 - dorsal))
    color = studio.np.where(is_jaw[:, None], jaw_base, color)
    tongue = studio.mix(palette["TONGUE"], palette["TONGUE2"], 0.35)
    color = studio.np.where(is_tongue[:, None], tongue, color)
    color = studio.np.where(is_claw[:, None], palette["CLAW"], color)
    color = studio.np.where(is_horn[:, None], studio.mix(palette["HORN"], palette["STRIPE"], 0.25), color)
    color = studio.np.where(is_feather[:, None], palette["FEATHER"], color)
    return color


def direct_quant_palette(palette, style):
    """Use a compact 16-colour palette with no procedural noise colours."""
    roles = ["BELLY2", "BELLY", "FLANK3", "FLANK", "BACK", "DORSAL", "STRIPE",
             "JAW", "JAW2", "TONGUE", "EYE", "PUPIL", "SOCKET", "CLAW", "TEETH", "MOUTH"]
    colors = studio.np.array([palette[role] for role in roles] + [studio.BACKGROUND], studio.np.float32)
    return studio.np.unique(studio.np.clip(studio.np.round(colors), 0, 255).astype(studio.np.uint8), axis=0)


def main():
    # Patch only this process; the shared v1/v2 generator remains unchanged.
    studio.base_skin = direct_base_skin
    studio.build_quant_palette = direct_quant_palette
    creature = ROOT / "Creatures" / "Giganotosaur"
    report, geo, model, texture = studio.build_skin(
        "Giganotosaur", creature, PALETTES["Giganotosaur"], style=STYLE,
        out_name="skin_v3", write_previews=True)
    print(f"v3: {texture}  {report['texture']['size'][0]}px  {report['metrics']['palette_colors']} colours")
    print("checks:", report["checks"])


if __name__ == "__main__":
    main()
