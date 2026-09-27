"""Bronze Age art: item sprites, blocks, armour layers, the explosive arrow and the tech tree icons.

Metals follow the keratin / rock method: vanilla silhouettes re-toned onto an Ark ramp (bronze is a warm
golden brown with an olive shadow, tin a cool pale grey with a faint blue, steel a dark blue grey). Sulphur
re-tones the amethyst family onto a sulphur-yellow ramp with orange-brown shadows, so the buds and the
cluster keep amethyst's cross framing. Unique items are hand-pixelled on a 16 px grid and doubled to the
32 px Ark item size (tools/verify_assets.py); the longsword and the hammer are drawn at 32 px.
Tech icons are 64 px: item icons are nearest upscales of the item art, station icons are orthographic
renders of the shipped block models (same camera as tools/render_camp_assets.py), downsampled.

Nothing here is random at run time: every output is a pure function of this file, the mod textures it
samples and the local Minecraft source jar.

Run from Ark: python tools/build_bronze_age_art.py
"""
import colorsys
import io
import json
import math
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

ARK = Path(__file__).resolve().parents[1]
JAR = ARK / 'build/moddev/artifacts/minecraft-patched-26.1.2.109-sources.jar'
if not JAR.exists():  # worktrees share the main checkout's Gradle artifacts
    JAR = ARK.parents[2] / 'Ark/build/moddev/artifacts/minecraft-patched-26.1.2.109-sources.jar'
ASSETS = ARK / 'src/main/resources/assets/arksurvivalreturns'
TEX = ASSETS / 'textures'
ICONS = TEX / 'gui/tech/icons'

OUTLINE = (40, 30, 16, 255)
BRONZE = ['#2a1e0c', '#4a4018', '#6b4a1e', '#8e5f22', '#b0792c', '#c8923a', '#dcae55', '#eecb7e']
TIN = ['#222a33', '#44515e', '#667685', '#8898a8', '#a9b8c6', '#c8d4de', '#e4ecf2']
STEEL = ['#12161c', '#252d38', '#39444f', '#4f5d6c', '#6b7c8d', '#8d9eb0', '#b9c8d6']
SULPHUR = ['#3f220a', '#6e3c10', '#9f5e16', '#cb911c', '#e7bf26', '#f5df4f', '#fff5a0']
HOT = ['#3a0a05', '#7c1706', '#bb330b', '#e3651a', '#f79c2c', '#ffd35e', '#fff6c8']
# Vanilla iron and gold are bright; a steep curve keeps most of the surface in the warm mid-tones.
BRONZE_TONE = dict(gamma=1.8, low=0.04, span=0.86)
BRASS = ['#3a2a08', '#7a5714', '#b88a24', '#dcb23a', '#f2d466', '#fff0a8']


# ----------------------------------------------------------------------------------------------- helpers

def rgb(hex_color):
    return tuple(int(hex_color[i:i + 2], 16) for i in (1, 3, 5))


def ramp_fn(stops):
    stops = [rgb(c) for c in stops]

    def ramp(t):
        t = min(max(t, 0.0), 1.0) * (len(stops) - 1)
        i = min(int(t), len(stops) - 2)
        f = t - i
        return tuple(round(a + (b - a) * f) for a, b in zip(stops[i], stops[i + 1]))
    return ramp


def vanilla(path):
    with zipfile.ZipFile(JAR) as jar:
        image = Image.open(io.BytesIO(jar.read(f'assets/minecraft/textures/{path}.png'))).convert('RGBA')
    return image.crop((0, 0, image.width, image.width)) if image.height > image.width else image


def mod_texture(path):
    image = Image.open(TEX / f'{path}.png').convert('RGBA')
    return image.crop((0, 0, image.width, image.width)) if image.height > image.width else image


def luminance(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


def saturation(p):
    return colorsys.rgb_to_hsv(*(c / 255 for c in p[:3]))[1]


def grey(p):
    return saturation(p) < 0.2


def retone(image, stops, select=lambda p: True, gamma=1.0, low=0.06, span=0.94, dark=24):
    """Map the selected pixels' brightness onto a ramp; near-black pixels keep the dark outline."""
    ramp = ramp_fn(stops)
    out = image.copy()
    px = out.load()
    chosen = [(x, y) for y in range(out.height) for x in range(out.width) if px[x, y][3] and select(px[x, y])]
    if not chosen:
        return out
    values = [luminance(px[x, y]) for x, y in chosen]
    lo, hi = min(values), max(values)
    for (x, y), value in zip(chosen, values):
        a = px[x, y][3]
        if value < dark:
            px[x, y] = ramp(0.0) + (a,)
            continue
        t = ((value - lo) / max(hi - lo, 1)) ** gamma
        px[x, y] = ramp(low + t * span) + (a,)
    return out


def outline(image, colour=OUTLINE, diagonal=False):
    """A one-pixel dark outline around the shape, like the other Ark sprites."""
    out = image.copy()
    src, dst = image.load(), out.load()
    steps = ((1, 0), (-1, 0), (0, 1), (0, -1)) + (((1, 1), (-1, -1), (1, -1), (-1, 1)) if diagonal else ())
    for y in range(image.height):
        for x in range(image.width):
            if src[x, y][3]:
                continue
            if any(0 <= x + dx < image.width and 0 <= y + dy < image.height and src[x + dx, y + dy][3] > 128
                   for dx, dy in steps):
                dst[x, y] = colour
    return out


def paint(rows, palette, size=16):
    image = Image.new('RGBA', (size, size))
    px = image.load()
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in palette:
                px[x, y] = palette[c] if len(palette[c]) == 4 else palette[c] + (255,)
    return image


def scale(image, factor):
    return image.resize((image.width * factor, image.height * factor), Image.Resampling.NEAREST)


def item32(image):
    return scale(image, 32 // image.width) if image.width < 32 else image


def icon(image):
    """A 64 px tech icon from item art: nearest upscale, centred on a transparent canvas."""
    big = scale(image, 64 // image.width)
    canvas = Image.new('RGBA', (64, 64))
    canvas.alpha_composite(big, ((64 - big.width) // 2, (64 - big.height) // 2))
    return canvas


def glow(image, colour, radius, strength, source=None):
    """A soft additive halo from the given source mask (default: the image's own alpha)."""
    mask = (source if source is not None else image).getchannel('A')
    halo = mask.filter(ImageFilter.GaussianBlur(radius)).point(lambda v: min(255, int(v * strength)))
    layer = Image.new('RGBA', image.size, colour + (0,))
    layer.putalpha(halo)
    under = Image.new('RGBA', image.size)
    under.alpha_composite(layer)
    under.alpha_composite(image)
    return under


def samples(path):
    """The distinct colours of a mod texture, darkest first (used to stay on existing material palettes)."""
    image = mod_texture(path).convert('RGB')
    return sorted({image.getpixel((x, y)) for y in range(image.height) for x in range(image.width)}, key=luminance)


# ------------------------------------------------------------------------------------------ metal items

def bronze_tool(name):
    return retone(vanilla(f'item/iron_{name}'), BRONZE, grey, **BRONZE_TONE)


def armour_piece(piece):
    return retone(vanilla(f'item/iron_{piece}'), BRONZE, **BRONZE_TONE)


def armour_layer(layer):
    return retone(vanilla(f'entity/equipment/{layer}/iron'), BRONZE, gamma=1.0, low=0.16, span=0.8)


def ingot(stops, source='iron_ingot', gamma=1.0):
    return retone(vanilla(f'item/{source}'), stops, gamma=gamma)


def bronze_blend():
    """The gunpowder pile as a mix of copper and tin powder: warm and cool grains side by side."""
    base = vanilla('item/gunpowder')
    px = base.load()
    out = base.copy()
    op = out.load()
    copper = ramp_fn(['#4a2410', '#8a4a22', '#c07038', '#e39a5c', '#f5c696'])
    tin = ramp_fn(TIN[1:])
    values = [luminance(px[x, y]) for y in range(16) for x in range(16) if px[x, y][3]]
    lo, hi = min(values), max(values)
    for y in range(16):
        for x in range(16):
            p = px[x, y]
            if not p[3]:
                continue
            t = (luminance(p) - lo) / max(hi - lo, 1)
            cool = (x * 5 + y * 3) % 7 in (0, 3) or (x + 2 * y) % 11 == 4
            op[x, y] = (tin(0.1 + t * 0.85) if cool else copper(0.05 + t * 0.9)) + (p[3],)
    return out


def ore(base, host, stops, turn=True):
    """Paint the host ore's blob layout over a stone base in the tin ramp; the blob rim darkens."""
    stone = vanilla(f'block/{base}')
    donor = vanilla(f'block/{host}')
    turned = stone
    if turn:  # the host's layout on its side, so the tin blobs do not sit where iron's do
        donor, turned = (im.transpose(Image.Transpose.TRANSPOSE) for im in (donor, stone))
    sp, dp = turned.load(), donor.load()
    mask = [[saturation(dp[x, y]) > 0.12 and dp[x, y][:3] != sp[x, y][:3] for x in range(16)] for y in range(16)]
    out = stone.copy()
    op = out.load()
    ramp = ramp_fn(stops)
    values = [luminance(dp[x, y]) for y in range(16) for x in range(16) if mask[y][x]]
    lo, hi = min(values), max(values)
    for y in range(16):
        for x in range(16):
            if not mask[y][x]:
                continue
            t = (luminance(dp[x, y]) - lo) / max(hi - lo, 1)
            op[x, y] = ramp(0.3 + t * 0.7) + (255,)
    for y in range(16):  # a darker rim under each blob keeps it readable against the grey stone
        for x in range(16):
            if mask[y][x]:
                continue
            if (y > 0 and mask[y - 1][x]) or (x > 0 and mask[y][x - 1]):
                r, g, b, a = op[x, y]
                op[x, y] = (int(r * 0.62), int(g * 0.64), int(b * 0.7), a)
    return out


def sulphur(path, gamma=0.9, low=0.04):
    return retone(vanilla(path), SULPHUR, gamma=gamma, low=low, span=1.0 - low)


def sulphur_crystal():
    """Three sulphur prisms with pointed tips: pale left facets, deep orange-brown right facets."""
    ramp = ramp_fn(SULPHUR)
    image = Image.new('RGBA', (16, 16))
    px = image.load()
    for left, width, top, bottom in ((3, 4, 5, 12), (7, 5, 1, 13), (12, 3, 7, 12)):
        tip = (width + 1) // 2
        for x in range(left, left + width):
            facet = (x - left) / max(width - 1, 1)
            for y in range(top, bottom + 1):
                if y < top + tip and abs(x - (left + (width - 1) / 2)) > (y - top) + 0.5:
                    continue
                t = 0.95 - facet * 0.62 - (0.06 if y > bottom - 2 else 0)
                if y < top + tip:
                    t += 0.05
                px[x, y] = ramp(t) + (255,)
        px[left, top + tip] = ramp(1.0) + (255,)  # a glint on each crystal's edge
    return outline(image, (72, 38, 10, 255))


# ------------------------------------------------------------------------------------------ 32 px weapons

def axis_sprite(shade):
    """Draws along the item diagonal: a runs bottom-left to top-right (0..62), b is the offset across it."""
    image = Image.new('RGBA', (32, 32))
    px = image.load()
    for y in range(32):
        for x in range(32):
            c = shade(x + (31 - y), x + y - 31)
            if c:
                px[x, y] = c if len(c) == 4 else c + (255,)
    return image


WOOD = [(58, 40, 18), (98, 70, 34), (137, 103, 39), (170, 132, 74), (196, 160, 102)]
LEATHER = [(46, 28, 18), (78, 48, 30), (108, 70, 44), (134, 92, 60)]


def forged(t):
    """Bronze for hand-drawn metal: the same compressed range as the re-toned vanilla sprites."""
    return ramp_fn(BRONZE)(BRONZE_TONE['low'] + t * BRONZE_TONE['span'])


def longsword():
    bronze = forged

    def shade(a, b):
        if 3 <= a <= 8 and abs(b) + abs(a - 5.5) <= 3.5:  # pommel
            return bronze(0.72 - 0.14 * b)
        if 8 < a < 19 and abs(b) <= 1:  # leather grip, wrapped
            wrap = (a + b) % 4 in (0, 1)
            return LEATHER[(2 if wrap else 1) - (b > 0) + (b < 0)]
        if 19 <= a <= 21 and abs(b) <= 6 - (a == 21):  # crossguard, rolled at the ends
            return bronze((0.9, 0.7, 0.45)[a - 19] - (0.18 if abs(b) >= 5 else 0))
        if 22 <= a <= 58:
            width = 1 if a <= 55 else 0
            if abs(b) > width:
                return None
            if a <= 24:
                return bronze(0.4 + 0.2 * (b < 0))  # ricasso under the guard
            if b < 0:
                return bronze(0.95)  # the lit edge
            if b > 0:
                return bronze(0.55)
            return bronze(0.76 if a < 55 else 0.95)  # the fuller, bright at the point
        return None
    return outline(axis_sprite(shade))


def war_hammer():
    bronze = forged

    def shade(a, b):
        lo, hi, reach = 41, 50, 10
        flare = abs(b) >= reach - 1  # the striking faces flare past the body
        if lo - flare <= a <= hi + flare and abs(b) <= reach:
            if abs(b) == reach:
                return bronze(0.66 if b < 0 else 0.34)  # striking faces
            if flare:
                return bronze(0.94 if b < 0 else 0.52)  # rim of the faces
            if a >= hi - 1:
                return bronze(0.98 if b <= 0 else 0.88)  # the upper face catches the light
            if a <= lo + 1:
                return bronze(0.22)  # underside in shadow
            if abs(b) <= 2:
                return bronze(1.0 if a == 45 and b == 0 else 0.5)  # socket collar and rivet
            return bronze(0.86 - 0.03 * (b + reach))
        if lo - 4 <= a < lo and abs(b) <= 2:  # socket langets
            return bronze(0.62 - 0.14 * b)
        if 3 <= a <= 6 and abs(b) <= 2:  # butt cap
            return bronze(0.72 - 0.15 * b)
        if 6 < a < lo and abs(b) <= 1:
            if 8 <= a <= 18:
                wrap = (a + b) % 4 in (0, 1)
                return LEATHER[(2 if wrap else 1) - (b > 0) + (b < 0)]
            return WOOD[3 - b]
        return None
    return outline(axis_sprite(shade))


# ------------------------------------------------------------------------------------ hand-drawn items

def explosive_arrow_item():
    """The vanilla arrow with a fiber-wrapped gunpowder charge behind the head and a lit fuse."""
    image = vanilla('item/arrow')
    px = image.load()
    tan, shade, cord, powder, rim = (218, 190, 124), (176, 142, 82), (96, 66, 30), (50, 48, 52), (74, 52, 24)
    for y in range(16):
        for x in range(16):
            a, b = x + (15 - y), x + y - 15
            if not (12 <= a <= 18 and -2 <= b <= 3) or (a in (12, 18) and b in (-2, 3)):
                continue
            if b in (-2, 3) or a in (12, 18):
                colour = rim
            elif a == 15:
                colour = cord  # the fiber binding round the charge
            elif (a, b) in ((13, 1), (17, -1), (16, 2)):
                colour = powder  # gunpowder showing through the wrap
            else:
                colour = tan if b <= 1 else shade
            px[x, y] = colour + (255,)
    for (x, y), colour in (((6, 6), (26, 22, 20)), ((6, 5), (26, 22, 20)), ((5, 4), (26, 22, 20)), ((4, 3), (222, 52, 36)),
                           ((3, 2), (255, 196, 70))):
        px[x, y] = colour + (255,)  # black fuse with a red, sparking tip
    return image


def explosive_arrow_entity():
    image = vanilla('entity/projectiles/arrow')
    px = image.load()
    tan, dark, powder, rope = (206, 176, 108), (120, 92, 46), (64, 64, 66), (54, 38, 20)
    for x0, step in ((9, 1), (22, -1)):  # each half of the shaft strip, behind the head
        cols = [x0 + step * i for i in range(4)]
        for i, x in enumerate(cols):
            for y in (1, 2, 3):
                edge = i in (0, 3)
                px[x, y] = (rope if edge else (powder if (x + y) % 3 == 0 else (tan if y < 3 else dark))) + (255,)
            px[x, 0 if i in (1, 2) else 1] = px[x, 0 if i in (1, 2) else 1] if i in (0, 3) else (rope + (255,))
            px[x, 4 if i in (1, 2) else 3] = px[x, 4 if i in (1, 2) else 3] if i in (0, 3) else (rope + (255,))
    px[10, 0] = (26, 22, 20, 255)
    px[21, 0] = (26, 22, 20, 255)
    return image


def vitamins():
    return outline(paint(["................",
                          "................",
                          "......kKKk......",
                          "......kkKk......",
                          ".....FffffF.....",
                          "......gwlg......",
                          "......gwlg......",
                          ".....gwlllg.....",
                          "....gwrRrrrg....",
                          "...gwrrrsrRrg...",
                          "...gwRrrrrrrg...",
                          "...gwbbbBbbbg...",
                          "...gwbBbbsbBg...",
                          "....gbbbbbbg....",
                          ".....gggggg.....",
                          "................"],
                         {'k': (176, 124, 72), 'K': (122, 82, 42), 'f': (206, 182, 116), 'F': (140, 116, 62),
                          'g': (70, 104, 148), 'w': (226, 240, 252), 'l': (176, 204, 232, 150),
                          'r': (200, 38, 46), 'R': (136, 20, 34), 's': (250, 150, 120),
                          'b': (58, 88, 196), 'B': (32, 48, 128)}), (30, 32, 44, 255))


def herbal_bandage():
    return outline(paint(["................",
                          "................",
                          "................",
                          "......cccc......",
                          "....ccWWWWcc....",
                          "...cWWsssWWWc...",
                          "..cWWsWWWsWWWc..",
                          "..cWsWcoocsWWc..",
                          "..cWsWo..oWsWc..",
                          "..cWsWcoocWsWc..",
                          "..cWWssssWsWGc..",
                          "...cWWWWWsWGGWc.",
                          "....ccWWGGgWWWWc",
                          "......ccGgWWGWc.",
                          "..........ccccc.",
                          "................"],
                         {'W': (236, 228, 204), 'c': (172, 160, 132), 's': (198, 186, 156),
                          'o': (120, 108, 86), 'G': (112, 150, 64), 'g': (74, 106, 44)}), (70, 60, 44, 255))


def roll(body, lit, dark, end, straps, loop=False, outline_colour=(58, 46, 24, 255)):
    """A rolled mat seen from the side, its spiral end turned to the viewer."""
    rows = ["................",
            "................",
            "................",
            "................",
            "...BBBBBBBBBEE..",
            "..BBBBBBBBBEEEE.",
            "..BBBBBBBBBEEEE.",
            "..BBBBBBBBBEEEE.",
            "..BBBBBBBBBEEEE.",
            "..BBBBBBBBBEEEE.",
            "..BBBBBBBBBEEEE.",
            "...BBBBBBBBBEE..",
            "................",
            "................",
            "................",
            "................"]
    image = Image.new('RGBA', (16, 16))
    px = image.load()
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == 'B':
                level = {4: 2, 5: 2, 6: 1, 7: 1, 8: 1, 9: 0, 10: 0, 11: 0}[y]
                colour = (lit, body, dark)[2 - level] if level != 1 else body
                colour = lit if level == 2 else body if level == 1 else dark
                if x in straps:
                    colour = straps[x][0 if y < 8 else 1]
                elif (x + (y // 3)) % 3 == 0 and level:
                    colour = tuple(max(0, c - 10) for c in colour)
                px[x, y] = colour + (255,)
            elif c == 'E':
                d = math.hypot((x + 0.5 - 13.0) / 1.6, (y + 0.5 - 8.0) / 3.1)
                colour = end[0] if d > 1.05 else end[1] if int(d * 3.2) % 2 == 0 else end[2]
                if d < 0.3:
                    colour = end[3]
                px[x, y] = colour + (255,)
    if loop:  # the carrying strap arches over the roll
        for x, y in ((5, 3), (6, 2), (7, 2), (8, 3)):
            px[x, y] = straps[min(straps)][0] + (255,)
    return outline(image, outline_colour)


def mattress():
    reed, light, dark, cord = samples('block/prehistoric/reed'), samples('block/prehistoric/reed_light'), \
        samples('block/prehistoric/reed_dark'), samples('block/prehistoric/cord')
    mid = lambda cs: cs[len(cs) // 2]
    straps = {x: (mid(cord), cord[0]) for x in (4, 9)}
    end = (mid(reed), mid(light), mid(dark), dark[0])
    return roll(mid(reed), mid(light), mid(dark), end, straps)


def bedroll():
    hide, fur, fur_light, cord = samples('block/prehistoric/hide'), samples('block/prehistoric/fur'), \
        samples('block/prehistoric/fur_light'), samples('block/prehistoric/cord')
    mid = lambda cs: cs[len(cs) // 2]
    straps = {x: (mid(cord), cord[0]) for x in (5, 8)}
    end = (mid(hide), mid(fur_light), mid(fur), hide[0])
    return roll(mid(hide), hide[-1], hide[0], end, straps, loop=True, outline_colour=(44, 28, 14, 255))


def cartridges():
    brass = ramp_fn(BRASS)
    lead = [(70, 52, 40), (150, 88, 52), (196, 120, 70), (232, 170, 120)]
    image = Image.new('RGBA', (16, 16))
    px = image.load()
    for x0, bottom, height in ((2, 13, 9), (6, 14, 11), (10, 13, 9)):
        top = bottom - height
        for y in range(top, bottom + 1):
            for i in range(3):
                if y < top + 3:  # bullet
                    if y == top and i != 1:
                        continue
                    colour = lead[3 - i] if y > top else lead[2]
                elif y == top + 3:
                    colour = brass(0.35)  # crimp
                elif y >= bottom - 1:
                    colour = brass(0.3 + 0.1 * (i == 0))  # rim
                else:
                    colour = brass((0.95, 0.72, 0.45)[i])
                px[x0 + i, y] = colour + (255,)
    return outline(image, (38, 26, 8, 255))


# ------------------------------------------------------------------------------------ model rendering

def resolve_model(identifier):
    ns, path = identifier.split(':') if ':' in identifier else ('minecraft', identifier)
    if ns == 'minecraft':
        with zipfile.ZipFile(JAR) as jar:
            data = json.loads(jar.read(f'assets/minecraft/models/{path}.json'))
    else:
        data = json.loads((ASSETS / 'models' / (path + '.json')).read_text())
    parent = {}
    if 'parent' in data and data['parent'] not in ('minecraft:block/block', 'block/block'):
        parent = resolve_model(data['parent'])
    return {**parent, **data, 'textures': {**parent.get('textures', {}), **data.get('textures', {})}}


def texture_array(name, textures, overrides):
    while isinstance(name, str) and name.startswith('#'):
        key = name[1:]
        name = overrides[key] if key in overrides else textures[key]
    if isinstance(name, Image.Image):
        return np.array(name), False
    ns, path = name.split(':') if ':' in name else ('minecraft', name)
    image = vanilla(path) if ns == 'minecraft' else mod_texture(path)
    return np.array(image), any(k in path for k in ('ember', 'flame'))


def elements_faces(elements, textures, offset=(0, 0, 0), overrides=None, yaw=0, double=False):
    overrides = overrides or {}
    faces = []
    for e in elements:
        x, y, z = e['from']
        X, Y, Z = e['to']
        corners = {
            'north': [(X, y, z), (x, y, z), (x, Y, z), (X, Y, z)],
            'south': [(x, y, Z), (X, y, Z), (X, Y, Z), (x, Y, Z)],
            'west': [(x, y, z), (x, y, Z), (x, Y, Z), (x, Y, z)],
            'east': [(X, y, Z), (X, y, z), (X, Y, z), (X, Y, Z)],
            'up': [(x, Y, Z), (X, Y, Z), (X, Y, z), (x, Y, z)],
            'down': [(x, y, z), (X, y, z), (X, y, Z), (x, y, Z)],
        }
        for face, f in e['faces'].items():
            v = np.array(corners[face], dtype=float)
            if 'rotation' in e:
                r = e['rotation']
                axis = 'xyz'.index(r['axis'])
                angle = math.radians(r['angle'])
                a, b = [i for i in range(3) if i != axis]
                if axis == 1:
                    a, b = b, a
                m = np.eye(3)
                m[a, a] = m[b, b] = math.cos(angle)
                m[a, b] = -math.sin(angle)
                m[b, a] = math.sin(angle)
                origin = np.array(r['origin'])
                v = (v - origin) @ m.T + origin
            if yaw == 180:
                v[:, 0] = 16 - v[:, 0]
                v[:, 2] = 16 - v[:, 2]
            v += offset
            normal = np.cross(v[1] - v[0], v[2] - v[0])
            normal /= max(1e-5, np.linalg.norm(normal))
            u, vv, U, V = f.get('uv', [0, 0, 16, 16])
            coords = np.array([(u, V), (U, V), (U, vv), (u, vv)]) / 16
            coords = np.roll(coords, f.get('rotation', 0) // 90, axis=0)
            tex, emissive = texture_array(f['texture'], textures, overrides)
            faces.append((v, normal, coords, tex, emissive, double))
    return faces


def model_faces(identifier, offset=(0, 0, 0), overrides=None, yaw=0):
    data = resolve_model(identifier)
    return elements_faces(data.get('elements', []), data['textures'], offset, overrides, yaw)


def box_faces(origin, size, uv, grow, tex, mirror=False, part=(0, 0, 0)):
    """ModelPart.Cube box UV, in entity model space (y down, the front faces -z), turned upright."""
    x0, y0, z0 = (o + p - grow for o, p in zip(origin, part))
    x1, y1, z1 = (o + s + p + grow for o, s, p in zip(origin, size, part))
    w, h, d = size
    if mirror:
        x0, x1 = x1, x0
    t0, t1, t2, t3 = (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)
    l0, l1, l2, l3 = (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)
    u, v = uv
    u0, u1, u2, u22, u3, u4 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    v0, v1, v2 = v, v + d, v + d + h
    polys = [([l1, l0, t0, t1], u1, v0, u2, v1), ([t2, t3, l3, l2], u2, v1, u22, v0),
             ([t0, l0, l3, t3], u0, v1, u1, v2), ([t1, t0, t3, t2], u1, v1, u2, v2),
             ([l1, t1, t2, l2], u2, v1, u3, v2), ([l0, l1, l2, l3], u3, v1, u4, v2)]
    th, tw = tex.shape[:2]
    centre = np.array([(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2])
    faces = []
    for verts, a, b, c, dd in polys:
        coords = np.array([(c, b), (a, b), (a, dd), (c, dd)], dtype=float) / (tw, th)
        if mirror:
            verts, coords = verts[::-1], coords[::-1]
        vv = np.array(verts, dtype=float)
        normal = vv.mean(axis=0) - centre
        vv[:, 1] *= -1  # model y points down; turn the figure upright and face it to +z
        vv[:, 2] *= -1
        normal[1] *= -1
        normal[2] *= -1
        normal /= max(1e-5, np.linalg.norm(normal))
        faces.append((vv, normal, coords, tex, False, True))
    return faces


def render(faces, size=256, fill=0.86, view=(.65, .63, 1.0), translucent=False):
    """Orthographic render with the camp preview camera; returns the image and its emissive mask."""
    view = np.array(view, dtype=float)
    view /= np.linalg.norm(view)
    right = np.cross([0, 1, 0], view)
    right /= np.linalg.norm(right)
    up = np.cross(view, right)
    camera = np.array([right, up, view])
    light = np.array([-.4, .9, .6])
    light /= np.linalg.norm(light)
    allv = np.concatenate([f[0] for f in faces])
    proj = allv @ camera.T
    lo, hi = proj[:, :2].min(0), proj[:, :2].max(0)
    centre = (lo + hi) / 2
    k = fill * size / max(hi - lo)
    pixels = np.zeros((size, size, 4), dtype=float)
    glowing = np.zeros((size, size), dtype=np.uint8)
    depth = np.full((size, size), -np.inf)
    order = faces
    if translucent:
        order = sorted(faces, key=lambda f: float((f[0] @ camera.T)[:, 2].mean()))
    for vertices, normal, uv, tex, emissive, double in order:
        facing = normal @ view
        if facing <= 0 and not double and not translucent:
            continue
        p = vertices @ camera.T
        p[:, 0] = (p[:, 0] - centre[0]) * k + size / 2
        p[:, 1] = size / 2 - (p[:, 1] - centre[1]) * k
        n = normal if facing > 0 else -normal
        shading = 1.0 if emissive else .73 + .27 * max(0, n @ light)
        for idx in ((0, 1, 2), (0, 2, 3)):
            tri, tuv = p[list(idx)], uv[list(idx)]
            a0 = np.maximum([0, 0], np.floor(tri[:, :2].min(0)).astype(int))
            a1 = np.minimum([size - 1, size - 1], np.ceil(tri[:, :2].max(0)).astype(int))
            if (a0 > a1).any():
                continue
            xx, yy = np.meshgrid(np.arange(a0[0], a1[0] + 1) + .5, np.arange(a0[1], a1[1] + 1) + .5)
            a, b, c = tri[:, :2]
            den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if abs(den) < 1e-8:
                continue
            w0 = ((b[1] - c[1]) * (xx - c[0]) + (c[0] - b[0]) * (yy - c[1])) / den
            w1 = ((c[1] - a[1]) * (xx - c[0]) + (a[0] - c[0]) * (yy - c[1])) / den
            w2 = 1 - w0 - w1
            zz = w0 * tri[0, 2] + w1 * tri[1, 2] + w2 * tri[2, 2]
            coords = w0[..., None] * tuv[0] + w1[..., None] * tuv[1] + w2[..., None] * tuv[2]
            tx = np.clip((coords[..., 0] * tex.shape[1]).astype(int), 0, tex.shape[1] - 1)
            ty = np.clip((coords[..., 1] * tex.shape[0]).astype(int), 0, tex.shape[0] - 1)
            colour = tex[ty, tx].astype(float)
            colour[..., :3] *= shading
            inside = (w0 >= -1e-5) & (w1 >= -1e-5) & (w2 >= -1e-5)
            region = pixels[a0[1]:a1[1] + 1, a0[0]:a1[0] + 1]
            if translucent:
                mask = inside & (colour[..., 3] > 0)
                alpha = colour[..., 3:4] / 255
                blended = region.copy()
                blended[..., :3] = colour[..., :3] * alpha + region[..., :3] * (1 - alpha)
                blended[..., 3:4] = 255 * (alpha + region[..., 3:4] / 255 * (1 - alpha))
                region[mask] = blended[mask]
                continue
            zone = depth[a0[1]:a1[1] + 1, a0[0]:a1[0] + 1]
            mask = inside & (zz > zone) & (colour[..., 3] > 100)
            zone[mask] = zz[mask]
            colour[..., 3] = 255
            region[mask] = colour[mask]
            glowing[a0[1]:a1[1] + 1, a0[0]:a1[0] + 1][mask] = 255 if emissive else 0
    image = Image.fromarray(np.clip(pixels, 0, 255).astype(np.uint8))
    return image, Image.fromarray(glowing)


def shrink(image, size=64):
    return image.resize((size, size), Image.Resampling.LANCZOS)


def forge_icon():
    faces = model_faces('arksurvivalreturns:block/prehistoric/primitive_forge_lit_lower', yaw=180) + \
        model_faces('arksurvivalreturns:block/prehistoric/primitive_forge_lit_upper', (0, 16, 0), yaw=180)
    image, embers = render(faces, 256, 0.9)
    source = Image.new('RGBA', image.size)
    source.putalpha(embers)
    lit = glow(image, (255, 150, 50), 18, 4.0, source)
    lit = glow(lit, (255, 110, 30), 6, 2.4, source)
    return shrink(lit)


def home_icon():
    # The Bedroll wears hide, a fur lining and fiber cord (see the report for the model texture remap).
    overrides = {'canvas': 'arksurvivalreturns:block/prehistoric/hide',
                 'canvas_light': 'arksurvivalreturns:block/prehistoric/fur',
                 'linen': 'arksurvivalreturns:block/prehistoric/fur_light',
                 'leather': 'arksurvivalreturns:block/prehistoric/cord'}
    image, _ = render(model_faces('arksurvivalreturns:block/camp/field_bedroll_rolled', overrides=overrides), 256, 0.92)
    return shrink(image)


def ambulance_icon():
    image, _ = render(model_faces('arksurvivalreturns:block/station/medicine_bench'), 256, 0.9)
    return shrink(image)


CUBE = [{'from': [0, 0, 0], 'to': [16, 16, 16],
         'faces': {f: {'texture': '#all'} for f in ('north', 'south', 'east', 'west', 'up', 'down')}}]
CROSS = [{'from': [-5, 0, 8], 'to': [21, 26, 8], 'rotation': {'origin': [8, 8, 8], 'axis': 'y', 'angle': 45},
          'faces': {'north': {'texture': '#cross'}, 'south': {'texture': '#cross'}}},
         {'from': [8, 0, -5], 'to': [8, 26, 21], 'rotation': {'origin': [8, 8, 8], 'axis': 'y', 'angle': 45},
          'faces': {'west': {'texture': '#cross'}, 'east': {'texture': '#cross'}}}]


def minerals_icon(block, cluster):
    faces = elements_faces(CUBE, {'all': '#b'}, overrides={'b': block})
    crystal = cluster.resize((16, 16), Image.Resampling.NEAREST)
    cross = elements_faces(CROSS, {'cross': '#c'}, (0, 15.5, 0), overrides={'c': crystal})
    faces += [f[:5] + (True,) for f in cross]
    image, _ = render(faces, 256, 0.9)
    return shrink(image)


def glass_icon():
    pane = vanilla('block/glass')
    px = pane.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][3] == 0:
                px[x, y] = (206, 236, 246, 64)
    faces = elements_faces(CUBE, {'all': '#g'}, overrides={'g': pane})
    image, _ = render(faces, 256, 0.84, translucent=True)
    return shrink(image)


def colossus_icon(items):
    """The whole bronze set stacked as it is worn: boots over the greaves, the helmet over the gorget."""
    art = Image.new('RGBA', (32, 32))
    for piece, top in (('leggings', 13), ('boots', 18), ('chestplate', 4), ('helmet', -3)):
        art.alpha_composite(armour_piece(piece), (8, top))
    return icon(art)


# --------------------------------------------------------------------------------------- icon effects

def sparkle(image, cx, cy, arm, colour=(255, 250, 228, 255), core=(255, 255, 255, 255)):
    px = image.load()
    for i in range(-arm, arm + 1):
        for x, y in ((cx + i, cy), (cx, cy + i)):
            if 0 <= x < image.width and 0 <= y < image.height:
                px[x, y] = core if abs(i) <= arm // 3 else colour
    for dx, dy in ((1, 1), (-1, -1), (1, -1), (-1, 1)):
        px[cx + dx, cy + dy] = colour
    return image


def shiny_icon(ingot_sprite):
    art = item32(ingot_sprite).copy()
    px = art.load()
    for y in range(32):  # a diagonal shine across the ingot's upper face
        for x in range(32):
            p = px[x, y]
            if p[3] and 3 <= (x - y) - 4 <= 6 and luminance(p) > 110:
                px[x, y] = tuple(min(255, c + 48) for c in p[:3]) + (255,)
    art = sparkle(art, 25, 7, 4)
    art = sparkle(art, 7, 13, 2)
    return icon(art)


def ironsmelt_icon():
    hot = retone(vanilla('item/iron_ingot'), HOT, gamma=0.8, low=0.12, span=0.88)
    big = icon(hot)
    return glow(big, (255, 120, 40), 6, 1.8)


def steel_icon():
    steel = ingot(STEEL, gamma=1.1)
    px = steel.load()
    for y in range(16):  # folded-steel waves across the faces
        for x in range(16):
            p = px[x, y]
            if p[3] and luminance(p) > 50 and (x + 2 * y + (x // 3)) % 5 == 0:
                px[x, y] = tuple(max(0, c - 18) for c in p[:3]) + (255,)
    return icon(steel)


def kaboom_icon(arrow):
    art = item32(arrow).copy()
    burst = Image.new('RGBA', (32, 32))
    bp = burst.load()
    cx, cy = 23.5, 8.5
    layers = [(9.5, (120, 36, 20)), (8.0, (214, 64, 26)), (6.4, (246, 130, 36)), (4.6, (255, 198, 70)),
              (2.6, (255, 246, 200))]
    for y in range(32):
        for x in range(32):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            angle = math.atan2(dy, dx)
            spike = 0.62 + 0.38 * abs(math.cos(angle * 4.0)) ** 3  # eight flame tongues
            r = math.hypot(dx, dy) / spike
            for radius, colour in layers:
                if r <= radius:
                    bp[x, y] = colour + (255,)
    for x, y in ((12, 2), (30, 17), (17, 17), (29, 1), (14, 12)):
        bp[x, y] = (255, 214, 90, 255)  # flying sparks
    art.alpha_composite(outline(burst, (70, 20, 12, 255)))
    return icon(art)


def knight_icon(sword, hammer):
    art = Image.new('RGBA', (32, 32))
    art.alpha_composite(hammer.transpose(Image.Transpose.FLIP_LEFT_RIGHT))
    art.alpha_composite(sword)
    return icon(art)


# ------------------------------------------------------------------------------------------------ main

def save(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)
    WRITTEN.append(path)


WRITTEN = []


def main():
    items = {
        'raw_tin': retone(vanilla('item/raw_iron'), TIN, gamma=1.1),
        'tin_ingot': ingot(TIN, gamma=1.15),
        'bronze_blend': bronze_blend(),
        'bronze_ingot': retone(vanilla('item/gold_ingot'), BRONZE, **BRONZE_TONE),
        **{f'bronze_{t}': bronze_tool(t) for t in ('pickaxe', 'axe', 'shovel', 'hoe')},
        'bronze_longsword': longsword(),
        'bronze_hammer': war_hammer(),
        **{f'bronze_{p}': armour_piece(p) for p in ('helmet', 'chestplate', 'leggings', 'boots')},
        'sulphur': sulphur_crystal(),
        'explosive_arrow': explosive_arrow_item(),
        'vitamins': vitamins(),
        'herbal_bandage': herbal_bandage(),
        'mattress': mattress(),
        'bedroll': bedroll(),
    }
    for name, image in items.items():
        save(item32(image), TEX / 'item' / f'{name}.png')

    blocks = {
        'tin_ore': ore('stone', 'iron_ore', TIN),
        'deepslate_tin_ore': ore('deepslate', 'deepslate_iron_ore', TIN),
        'sulphur_block': sulphur('block/amethyst_block', 0.9, 0.28),
        'budding_sulphur': sulphur('block/budding_amethyst', 0.9, 0.28),
        'small_sulphur_bud': sulphur('block/small_amethyst_bud', 0.8, 0.14),
        'medium_sulphur_bud': sulphur('block/medium_amethyst_bud', 0.8, 0.14),
        'large_sulphur_bud': sulphur('block/large_amethyst_bud', 0.8, 0.14),
        'sulphur_cluster': sulphur('block/amethyst_cluster', 0.8, 0.14),
    }
    for name, image in blocks.items():
        save(image, TEX / 'block' / f'{name}.png')

    layers = {layer: armour_layer(layer) for layer in ('humanoid', 'humanoid_leggings')}
    for layer, image in layers.items():
        save(image, TEX / 'entity/equipment' / layer / 'bronze.png')
    save(explosive_arrow_entity(), TEX / 'entity/projectiles/explosive_arrow.png')

    icons = {
        'forge': forge_icon(),
        'shiny': shiny_icon(items['bronze_ingot']),
        'home': home_icon(),
        'coal': icon(vanilla('item/coal')),
        'ironsmelt': ironsmelt_icon(),
        'minerals': minerals_icon(blocks['sulphur_block'], blocks['sulphur_cluster']),
        'glass': glass_icon(),
        'ambulance': ambulance_icon(),
        'bandage': icon(items['herbal_bandage']),
        'vitamins': icon(items['vitamins']),
        'knight': knight_icon(items['bronze_longsword'], items['bronze_hammer']),
        'tools': icon(items['bronze_pickaxe']),
        'tincan': icon(items['bronze_helmet']),
        'colossus': colossus_icon(items),
        'kaboom': kaboom_icon(items['explosive_arrow']),
        'steel': steel_icon(),
        'subdue': icon(cartridges()),
    }
    for name, image in icons.items():
        assert image.size == (64, 64) and image.mode == 'RGBA', name
        save(image, ICONS / f'{name}.png')
    print(f'Wrote {len(WRITTEN)} textures:')
    for path in WRITTEN:
        print('  ' + path.relative_to(ARK).as_posix())


if __name__ == '__main__':
    main()
