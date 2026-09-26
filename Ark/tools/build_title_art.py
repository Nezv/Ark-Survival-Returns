"""Build the title-screen art used by the FancyMenu layout and the project showcase.

background.png: the real danger-band map (the same formula as DangerBands.java) drawn as faint
contours over a dusk gradient, with dinosaur silhouettes cut from the creature previews.
logo.png: the wordmark in Bitter (OFL, tools/fonts) in the showcase palette.
ark_icon.png: the square mod icon (mod list logo for Ark and every Ark-branded integration).

Run from Ark: python tools/build_title_art.py
"""
import math
import random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
CREATURES = ROOT.parent / 'Creatures'
OUT = ROOT / 'src/main/resources/assets/arksurvivalreturns/textures/gui/title'
FONT = ROOT / 'tools/fonts/Bitter.ttf'
W, H = 1920, 1080
RANKS = [(75, 174, 102), (168, 202, 83), (228, 198, 83), (233, 134, 70), (153, 78, 179)]  # XaeroDangerOverlay
PREVIEW_BG = (19, 30, 39)


def level(x, z, width=256):
    """Port of DangerBands.level with the origin at 0,0."""
    radius = 2 * width
    period = 2 * radius

    def curve(c):
        return round(radius * 0.20 * math.sin(2 * math.pi * (c % period) / period))
    u, v = x, z
    u += curve(v)
    v += curve(u)
    dx = abs((u + radius) % period - radius)
    dz = abs((v + radius) % period - radius)
    fraction = max(dx, dz) / radius
    return 1 + int(min(4, math.floor(5 * fraction * fraction)))


def silhouette(folder, view='side.png'):
    """The creature cut out of its preview render as an alpha mask."""
    image = Image.open(CREATURES / folder / 'previews' / view).convert('RGB')
    image = image.crop((30, 110, image.width - 30, 700))
    mask = Image.new('L', image.size)
    src, dst = image.load(), mask.load()
    for y in range(image.height):
        row = [sum(abs(src[x, y][i] - PREVIEW_BG[i]) for i in range(3)) > 30 for x in range(image.width)]
        if sum(row) > image.width * 0.8:
            continue  # the ground line
        for x, on in enumerate(row):
            if on:
                dst[x, y] = 255
    box = mask.getbbox()
    return mask.crop(box) if box else mask


def background():
    img = Image.new('RGB', (W, H))
    px = img.load()
    top, mid, low = (14, 16, 13), (24, 27, 22), (43, 52, 37)
    for y in range(H):
        t = y / H
        a, b, k = (top, mid, t / 0.55) if t < 0.55 else (mid, low, (t - 0.55) / 0.45)
        color = tuple(round(a[i] + (b[i] - a[i]) * k) for i in range(3))
        for x in range(W):
            px[x, y] = color
    # Danger contours: 1 px = 3 blocks, fading toward the horizon.
    overlay = Image.new('RGBA', (W, H))
    ov = overlay.load()
    step = 3
    levels = [[level((x - W // 2) * step, (y - 180) * step) for x in range(W)] for y in range(0, 760)]
    for y in range(1, 759):
        fade = max(0.0, 1.0 - y / 760)
        for x in range(1, W - 1):
            here = levels[y][x]
            edge = here != levels[y][x - 1] or here != levels[y - 1][x]
            r, g, b = RANKS[here - 1]
            alpha = int((80 if edge else 12) * fade)
            if alpha:
                ov[x, y] = (r, g, b, alpha)
    img = Image.alpha_composite(img.convert('RGBA'), overlay)
    # Ember glow behind the horizon.
    glow = Image.new('RGBA', (W, H))
    ImageDraw.Draw(glow).ellipse((W * 0.18, H * 0.58, W * 0.82, H * 1.25), fill=(226, 118, 63, 70))
    img = Image.alpha_composite(img, glow.filter(ImageFilter.GaussianBlur(120)))
    # Distant and near ridges.
    rng = random.Random(4)
    for base, amp, color in ((830, 50, (30, 36, 27, 255)), (905, 38, (17, 19, 15, 255))):
        ridge = Image.new('RGBA', (W, H))
        phase = [rng.random() * 6 for _ in range(3)]
        pts = [(x, base - amp * (0.5 * math.sin(x / 260 + phase[0]) + 0.3 * math.sin(x / 97 + phase[1])
                                  + 0.2 * math.sin(x / 41 + phase[2]))) for x in range(0, W + 8, 8)]
        ImageDraw.Draw(ridge).polygon(pts + [(W, H), (0, H)], fill=color)
        img = Image.alpha_composite(img, ridge)
    # Silhouettes: (folder, height px, x, baseline y, colour)
    for folder, height, x, base, color in (('Brontosaur', 330, 250, 840, (27, 32, 24)),
                                          ('Parasaur', 120, 760, 858, (22, 26, 20)),
                                          ('Parasaur', 105, 880, 862, (22, 26, 20)),
                                          ('Triceratops', 140, 1060, 918, (12, 13, 11)),
                                          ('Tyranosaur', 250, 1330, 915, (12, 13, 11)),
                                          ('Piterodon', 64, 330, 360, (38, 44, 35)),
                                          ('Piterodon', 46, 1560, 250, (38, 44, 35))):
        mask = silhouette(folder)
        scale = height / mask.height
        mask = mask.resize((max(1, int(mask.width * scale)), height), Image.Resampling.LANCZOS)
        shape = Image.new('RGBA', mask.size, (*color, 255))
        shape.putalpha(mask)
        img.alpha_composite(shape, (x, base - height))
    return img.convert('RGB')


def logo():
    font_big = ImageFont.truetype(str(FONT), 230)
    font_big.set_variation_by_axes([800])
    font_small = ImageFont.truetype(str(FONT), 62)
    font_small.set_variation_by_axes([600])
    img = Image.new('RGBA', (1200, 380))
    d = ImageDraw.Draw(img)

    def spaced(text, font, y, fill, tracking):
        widths = [d.textlength(c, font=font) for c in text]
        total = sum(widths) + tracking * (len(text) - 1)
        x = (img.width - total) / 2
        for c, w in zip(text, widths):
            d.text((x, y), c, font=font, fill=fill, stroke_width=6, stroke_fill=(12, 13, 11, 235))
            x += w + tracking

    spaced('ARK', font_big, 0, (230, 232, 225, 255), 26)
    d.rectangle((330, 268, 870, 276), fill=(226, 118, 63, 255))
    spaced('SURVIVAL RETURNS', font_small, 288, (143, 176, 127, 255), 14)
    shadow = Image.new('RGBA', img.size)
    shadow.putalpha(img.getchannel('A').filter(ImageFilter.GaussianBlur(10)).point(lambda a: a * 150 // 255))
    out = Image.new('RGBA', img.size)
    out.alpha_composite(shadow, (0, 6))
    out.alpha_composite(img)
    return out


def icon(size=256):
    """Basalt tile, ember rounded eight-point star (tools/ark_shapes.py) and an Ark 'A'."""
    from ark_shapes import star_polygon
    scale = 4
    S = size * scale
    img = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, S - 1, S - 1), radius=S // 6, fill=(31, 34, 29, 255))
    # Faint danger contours across the tile.
    for i, color in enumerate(RANKS):
        r = S * (0.18 + 0.12 * i)
        d.ellipse((S / 2 - r * 1.25, S * 0.62 - r, S / 2 + r * 1.25, S * 0.62 + r), outline=(*color, 60), width=scale * 2)
    star = star_polygon(S / 2, S / 2, S * 0.40)
    d.polygon(star, fill=(40, 30, 22, 255))
    d.line(star + [star[0]], fill=(226, 118, 63, 255), width=scale * 9, joint='curve')
    font = ImageFont.truetype(str(FONT), int(S * 0.42))
    font.set_variation_by_axes([800])
    d.text((S / 2, S / 2 + S * 0.02), 'A', font=font, fill=(230, 232, 225, 255), anchor='mm')
    return img.resize((size, size), Image.Resampling.LANCZOS)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    background().save(OUT / 'background.png', optimize=True)
    logo().save(OUT / 'logo.png', optimize=True)
    brand = icon()
    brand.save(ROOT / 'src/main/resources/ark_icon.png', optimize=True)
    print(f'Wrote {OUT / "background.png"} and logo.png')


if __name__ == '__main__':
    main()
