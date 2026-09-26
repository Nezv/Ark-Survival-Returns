"""Sprites for the keratin tier and the sharp rock.

Keratin and the sharp rock are drawn here at 32 px like the rock. The spear and the armour are the vanilla
stone spear and copper armour re-toned to a horn ramp (dark root to pale tip), with faint growth bands on
the worn layers, so their silhouettes match vanilla exactly. Tech tree icons are 4x nearest upscales.

Run from Ark: python tools/build_keratin_items.py
"""
import io
import math
import zipfile
from pathlib import Path

from PIL import Image

ARK = Path(__file__).resolve().parents[1]
JAR = ARK / 'build/moddev/artifacts/minecraft-patched-26.1.2.109-merged.jar'
TEX = ARK / 'src/main/resources/assets/arksurvivalreturns/textures'
ICONS = ARK / 'design/technology-tree/icons'
# Horn, root to tip: the creature horn material (Creatures/parts.py) warmed toward amber.
KERATIN = ['#2f261e', '#4a3b2d', '#6a543d', '#8b6f4d', '#ab8d62', '#c9ad80', '#e2cea2']
OUTLINE = (34, 27, 21, 255)


def rgb(hex_color):
    return tuple(int(hex_color[i:i + 2], 16) for i in (1, 3, 5))


STOPS = [rgb(c) for c in KERATIN]


def ramp(t):
    t = min(max(t, 0.0), 1.0) * (len(STOPS) - 1)
    i = min(int(t), len(STOPS) - 2)
    f = t - i
    return tuple(round(a + (b - a) * f) for a, b in zip(STOPS[i], STOPS[i + 1]))


def vanilla(path):
    with zipfile.ZipFile(JAR) as jar:
        return Image.open(io.BytesIO(jar.read(f'assets/minecraft/textures/{path}.png'))).convert('RGBA')


def luminance(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


def retone(image, select=lambda p: True, bands=False):
    """Map the selected pixels' brightness onto the horn ramp; the darkest become the outline."""
    out = image.copy()
    px = out.load()
    chosen = [(x, y) for y in range(out.height) for x in range(out.width) if px[x, y][3] and select(px[x, y])]
    if not chosen:
        return out
    values = [luminance(px[x, y]) for x, y in chosen]
    low, high = min(values), max(values)
    for (x, y), value in zip(chosen, values):
        a = px[x, y][3]
        if value < 32:
            px[x, y] = OUTLINE[:3] + (a,)
            continue
        t = ((value - low) / max(high - low, 1)) ** 1.35  # deeper mid-tones: horn, not bone
        if bands and (y // 2) % 3 == 0:
            t -= 0.08  # growth bands across the plates
        px[x, y] = ramp(0.08 + t * 0.86) + (a,)
    return out


def grey(p):
    return max(p[:3]) - min(p[:3]) < 16


def keratin_sprite():
    """A curved horn sheath: dark root, pale tip, darker growth rings."""
    size = 32
    image = Image.new('RGBA', (size, size))
    px = image.load()
    p0, p1, p2 = (7.0, 27.0), (5.0, 9.0), (26.0, 5.0)
    samples = []
    for i in range(160):
        t = i / 159
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
        samples.append((x, y, t, 5.2 * (1 - t) ** 0.8 + 0.7))
    for py in range(size):
        for pxl in range(size):
            best = None
            for x, y, t, r in samples:
                d = math.hypot(pxl + 0.5 - x, py + 0.5 - y)
                if d <= r and (best is None or d / r < best[0]):
                    best = (d / r, t, x, y)
            if best is None:
                continue
            edge, t, x, y = best
            shade = t * 0.85 + 0.15 - 0.22 * edge * (1 if (pxl + 0.5 - x) + (py + 0.5 - y) > 0 else -0.4)
            if int(t * 9) != int((t + 0.012) * 9) and t < 0.85:
                shade -= 0.18  # growth ring
            px[pxl, py] = ramp(shade) + (255,)
    return outline(image)


def sharp_rock_sprite():
    """A knapped flake: two facets and a bright cutting edge, in the rock's own greys."""
    size = 32
    image = Image.new('RGBA', (size, size))
    px = image.load()
    poly = [(6, 28), (5, 18), (12, 8), (26, 3), (21, 15), (13, 26)]
    ridge = ((8, 25), (24, 5))

    def inside(x, y):
        hit = False
        for (x1, y1), (x2, y2) in zip(poly, poly[1:] + poly[:1]):
            if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
                hit = not hit
        return hit

    greys = [(78, 76, 72), (104, 102, 97), (132, 130, 125), (158, 156, 150), (186, 184, 178), (214, 212, 206)]
    (ax, ay), (bx, by) = ridge
    for y in range(size):
        for x in range(size):
            if not inside(x + 0.5, y + 0.5):
                continue
            side = (bx - ax) * (y + 0.5 - ay) - (by - ay) * (x + 0.5 - ax)
            level = 3 if side < 0 else 1
            if abs(side) < 14:
                level = 4  # the ridge between the two flake scars
            level += (x * 7 + y * 13) % 5 == 0  # conchoidal ripple
            px[x, y] = greys[min(level, 5)] + (255,)
    # the cutting edge along the upper-right rim
    for x, y in [(21, 4), (22, 4), (23, 4), (24, 4), (25, 4), (24, 5), (23, 6), (23, 7), (22, 8), (22, 9), (21, 10),
                 (21, 11), (20, 12), (20, 13)]:
        if px[x, y][3]:
            px[x, y] = greys[5] + (255,)
    return outline(image)


def outline(image):
    """A one-pixel dark outline around the shape, like the other Ark sprites."""
    out = image.copy()
    src, dst = image.load(), out.load()
    for y in range(image.height):
        for x in range(image.width):
            if src[x, y][3]:
                continue
            if any(0 <= x + dx < image.width and 0 <= y + dy < image.height and src[x + dx, y + dy][3]
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                dst[x, y] = OUTLINE
    return out


def icon(image):
    image = image.resize((32, 32), Image.Resampling.NEAREST) if image.width == 16 else image
    scale = 64 // image.width
    big = image.resize((image.width * scale, image.height * scale), Image.Resampling.NEAREST)
    canvas = Image.new('RGBA', (64, 64))
    canvas.alpha_composite(big, ((64 - big.width) // 2, (64 - big.height) // 2))
    return canvas


def main():
    items = {
        'keratin': keratin_sprite(),
        'sharp_rock': sharp_rock_sprite(),
        'keratin_spear': retone(vanilla('item/stone_spear'), grey),
        'keratin_spear_in_hand': retone(vanilla('item/stone_spear_in_hand'), grey),
        **{f'keratin_{piece}': retone(vanilla(f'item/copper_{piece}')) for piece in ('helmet', 'chestplate', 'leggings', 'boots')},
    }
    (TEX / 'item').mkdir(parents=True, exist_ok=True)
    for name, image in items.items():
        if image.width == 16:  # Ark item sprites are 32 px (tools/verify_assets.py)
            image = image.resize((32, 32), Image.Resampling.NEAREST)
        image.save(TEX / 'item' / f'{name}.png')
    for layer in ('humanoid', 'humanoid_leggings', 'humanoid_baby'):
        folder = TEX / 'entity/equipment' / layer
        folder.mkdir(parents=True, exist_ok=True)
        retone(vanilla(f'entity/equipment/{layer}/copper'), bands=True).save(folder / 'keratin.png')
    ICONS.mkdir(parents=True, exist_ok=True)
    for node, sprite in (('sharp_rock', 'sharp_rock'), ('keratin', 'keratin'), ('keratin_spear', 'keratin_spear'),
                         ('keratin_armour', 'keratin_chestplate')):
        icon(items[sprite]).save(ICONS / f'{node}.png')
    print(f'Wrote {len(items)} item sprites, 3 armour layers and 4 tech icons')


if __name__ == '__main__':
    main()
