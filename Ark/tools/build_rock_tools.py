"""Sprites for the rock set (Rock Axe, Pickaxe, Sword, Shovel, Hoe) and Narcotics.

The rock tools start from the vanilla stone tool sprites: the grey head pixels are re-toned onto the Rock
item's own palette (darkest to lightest by brightness), the wooden handle stays. Narcotics are the vanilla
gunpowder pile turned a dark berry violet. All are doubled from 16 to 32 px with nearest-neighbour scaling.

Run from Ark: python tools/build_rock_tools.py
"""
import colorsys
import io
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / 'build/moddev/artifacts/minecraft-patched-26.1.2.109-sources.jar'
OUT = ROOT / 'src/main/resources/assets/arksurvivalreturns/textures/item'

# Ark item id -> vanilla sprite it is drawn from. stone_hatchet is the Rock Axe's id.
TOOLS = {'stone_hatchet': 'stone_axe', 'rock_pickaxe': 'stone_pickaxe', 'rock_sword': 'stone_sword',
         'rock_shovel': 'stone_shovel', 'rock_hoe': 'stone_hoe'}


def vanilla(name):
    with zipfile.ZipFile(JAR) as jar:
        return Image.open(io.BytesIO(jar.read(f'assets/minecraft/textures/item/{name}.png'))).convert('RGBA')


def luma(rgb):
    r, g, b = rgb[:3]
    return 0.299 * r + 0.587 * g + 0.114 * b


def rock_ramp():
    """The Rock sprite's opaque colours, darkest first."""
    rock = Image.open(OUT / 'rock.png').convert('RGBA')
    colours = {px[:3] for px in rock.getdata() if px[3] > 0}
    return sorted(colours, key=luma)


def is_head(rgb):
    """Stone heads are grey; the handle is brown (clearly warmer and more saturated)."""
    h, s, v = colorsys.rgb_to_hsv(*(c / 255 for c in rgb[:3]))
    return s < 0.2


def rock_tool(vanilla_name, ramp):
    image = vanilla(vanilla_name)
    px = image.load()
    heads = [px[x, y] for y in range(16) for x in range(16) if px[x, y][3] > 0 and is_head(px[x, y])]
    lo, hi = min(map(luma, heads)), max(map(luma, heads))
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            if a == 0 or not is_head((r, g, b)):
                continue
            t = 0.0 if hi == lo else (luma((r, g, b)) - lo) / (hi - lo)
            px[x, y] = (*ramp[round(t * (len(ramp) - 1))], a)
    return image


def narcotics():
    image = vanilla('gunpowder')
    px = image.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            r2, g2, b2 = colorsys.hsv_to_rgb(0.80, 0.45 + 0.35 * (1 - v), 0.25 + 0.7 * v)
            px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    return image


def double(image):
    return image.resize((32, 32), Image.Resampling.NEAREST)


def main():
    ramp = rock_ramp()
    for item, source in TOOLS.items():
        double(rock_tool(source, ramp)).save(OUT / f'{item}.png')
    double(narcotics()).save(OUT / 'narcotics.png')
    print(f'Wrote {len(TOOLS) + 1} sprites to {OUT}')


if __name__ == '__main__':
    main()
