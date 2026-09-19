"""Create an approval sheet for candidate Giga variant ramps.

This writes proposal files only; it does not rebuild any model or patch
batch_palettes.json. Base colors are ordered belly -> dorsal.
"""
import json
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
SHADE_OFFSETS = (-6, -3, 0, 3, 6)
BRUSH_SIZE = 3
NOISE_SEED = 7319

PROPOSAL = {
    # Ivory is the muted pale animal ramp; no detail accent layer is used.
    "Ivory": ["#d4cfc2", "#c9c4b8", "#bdb7ab", "#b0a9a0", "#9f9992"],
    "Darken": ["#353635", "#2d2e2d", "#252625", "#1c1d1c", "#121312"],
    "Emerald": ["#081a06", "#061505", "#041104", "#030b02", "#010601"],
    # Reordered into a consistent light-to-dark blue ramp and darkened across
    # all five layers.
    "Midnight": ["#101d31", "#0c1829", "#081323", "#050c18", "#02060c"],
    "Burgundy": ["#5b2935", "#4d222d", "#3f1b26", "#30141e", "#1f0a12"],
    "Brown": ["#68402b", "#543327", "#43291f", "#321d17", "#21120c"],
}

def rgb(code):
    code = code.lstrip("#")
    return tuple(int(code[i:i + 2], 16) for i in (0, 2, 4))


def shade_palette(code):
    return [tuple(max(0, min(255, c + offset)) for c in rgb(code))
            for offset in SHADE_OFFSETS]


def draw_noisy_swatch(image, box, code, seed):
    """Fill a swatch with contiguous 3px-brush cells from its local palette."""
    left, top, right, bottom = box
    width, height = right - left, bottom - top
    shades = shade_palette(code)
    # Weighted centre shades keep the pattern alive without salt-and-pepper
    # extremes. A neighbour is reused often enough to form small skin-like runs.
    rng = random.Random(seed)
    weights = (1, 3, 5, 3, 1)
    previous_row = {}
    draw = ImageDraw.Draw(image)
    for y in range(top, bottom, BRUSH_SIZE):
        previous = None
        for x in range(left, right, BRUSH_SIZE):
            if previous is not None and rng.random() < 0.24:
                index = previous
            elif (x, y - BRUSH_SIZE) in previous_row and rng.random() < 0.18:
                index = previous_row[(x, y - BRUSH_SIZE)]
            else:
                index = rng.choices(range(5), weights=weights, k=1)[0]
            previous_row[(x, y)] = index
            previous = index
            draw.rectangle((x, y, min(x + BRUSH_SIZE - 1, right - 1),
                            min(y + BRUSH_SIZE - 1, bottom - 1)), fill=shades[index])


def font(size):
    for name in ("segoeui.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(f"C:/Windows/Fonts/{name}", size)
        except OSError:
            pass
    return ImageFont.load_default()


def main():
    out_png = HERE / "variant_palette_proposal.png"
    out_json = HERE / "variant_palette_proposal.json"
    out_json.write_text(json.dumps({
        "order": "belly -> lower flank -> flank -> upper flank -> dorsal",
        "local_shade_offsets": list(SHADE_OFFSETS),
        "brush_size_pixels": BRUSH_SIZE,
        "noise_method": "Deterministic weighted local-palette fill in contiguous 3x3 brush cells; neighbour reuse forms small mottled runs.",
        "midnight_note": "Midnight is ordered light-to-dark; the previous out-of-order layer-2 darkest placement was removed.",
        "palettes": PROPOSAL,
        "ivory_note": "Ivory uses the muted pale body ramp directly; no detail accent layer is included.",
    }, indent=2) + "\n", encoding="utf-8")

    width, row_h, left, label_w, swatch_w, gap = 1480, 92, 28, 180, 220, 10
    height = 100 + row_h * len(PROPOSAL)
    image = Image.new("RGB", (width, height), "#202a31")
    draw = ImageDraw.Draw(image)
    draw.text((left, 20), "Giganotosaur variant palette proposal", fill="#f0f2e8", font=font(28))
    draw.text((left, 58), "Textured swatches: each cell is a 3px brush sampled from five nearby shades; layers run belly → dorsal.",
                      fill="#b9c5c5", font=font(16))
    for row, (name, colors) in enumerate(PROPOSAL.items()):
        y = 100 + row * row_h
        draw.text((left, y + 31), name, fill="#f0f2e8", font=font(20))
        for column, code in enumerate(colors):
            x = left + label_w + column * (swatch_w + gap)
            draw_noisy_swatch(image, (x, y, x + swatch_w, y + 56), code,
                              NOISE_SEED + row * 101 + column * 17)
            draw.text((x + 8, y + 64), f"{column + 1}  {code}", fill="#dfe7e3", font=font(14))
    image.save(out_png)
    print(out_png)
    print(out_json)


if __name__ == "__main__":
    main()
