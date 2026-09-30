"""Crests for the workstation screens (P14): a frame over the top of each panel with the bench's sigil in a medallion.

Each bench names its crest in its design file ({"frame": "steel", "sigil": "helm"}). The frame hugs the panel's top edge,
curls past both corners and rises into a dome around the medallion, in the bench's material: riveted steel for the
Armoury, lashed wood for the Working Station, stone blocks for the Mortar & Pestle, a leafy vine for the Medicine Bench
and bolted iron with chains for the Smithing Table. Sigils are 16x16 pixel art, like an item.

Writes design/workstations/crests/<bench>.png (the crest, CREST_W x CREST_H; the panel's top-left corner sits at
(MARGIN, PANEL_TOP) of the image) and <bench>_sigil.png (32x32, the sigil at 2x, the icon of a planned bench block).
The showcase draws them from there; the mod will blit the same files as GUI sprites.
Run from Ark/: python tools/build_workstation_crests.py
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ARK = Path(__file__).resolve().parents[1]
DESIGN = ARK / 'design/workstations'
OUT = DESIGN / 'crests'
PANEL_W = 320
MARGIN = 18
PANEL_TOP = 30
CREST_W, CREST_H = PANEL_W + 2 * MARGIN, PANEL_TOP + 18
CX = CREST_W // 2
OUTLINE = (14, 16, 13, 255)


def rgb(hex_colour, alpha=255):
    h = hex_colour.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), alpha)


def outline(image, colour=OUTLINE):
    """A one-pixel outline around every opaque pixel (4-neighbours), like item sprites."""
    src = image.load()
    out = image.copy()
    dst = out.load()
    for y in range(image.height):
        for x in range(image.width):
            if src[x, y][3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < image.width and 0 <= ny < image.height and src[nx, ny][3] > 128:
                    dst[x, y] = colour
                    break
    return out


# ------------------------------------------------------------------------------------------------ sigils

def paint(rows, palette):
    image = Image.new('RGBA', (16, 16))
    px = image.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                px[x, y] = rgb(palette[ch])
    return image


SIGILS = {
    # A great helm with a T visor and two short horns (the user's sketch).
    'helm': (["H..............H",
              "Hh............hH",
              ".Hh..........hH.",
              ".HhhLLLLLLLLhhH.",
              "..hLWWLLLLLLLMh.",
              "..LWLLMMMMMMMMD.",
              "..LWLMMMMMMMMMD.",
              "..LLMMEMMMMEMMD.",
              "..VVVVVVVVVVVVV.",
              "..LMMMMMVVMMMMD.",
              "..LMMMMMVVMMMMD.",
              "..LMMMEMVVMEMMD.",
              "..LMMMMMVVMMMMD.",
              "..DMMMMMMMMMMMD.",
              "...DDDDDDDDDDD..",
              "................"],
             {'H': '#e8dfc4', 'h': '#b1a47f', 'W': '#eef3f6', 'L': '#c3ccd2', 'M': '#8b959c', 'D': '#555e65',
              'V': '#15191b', 'E': '#e2763f'}),
    # A stone mortar with its pestle and two blackberries.
    'mortar': ([".............PP.",
                "............PpP.",
                "...........PpP..",
                "..........PpP...",
                ".........PpP....",
                "..NN....PpP.....",
                ".NnNN..PpP......",
                ".NNn.RRPPRRRR...",
                "..RRrrrrrrrrrRR.",
                ".RGGrrrrrrrrrGGR",
                ".RGGGGGGGGGGGGGR",
                ".RGgGGGGGGGGGgGR",
                "..RGgGGGGGGGgGR.",
                "...RgggggggggR..",
                ".....RRRRRRR....",
                "....RRRRRRRRR..."],
               {'P': '#e2d8bd', 'p': '#aa9f82', 'N': '#6b3f9e', 'n': '#a77ad8', 'R': '#5d615a', 'r': '#2c2f2b',
                'G': '#a3a69c', 'g': '#7c8076'}),
    # A round flask of green tonic with a leaf.
    'flask': (["......CC........",
               "......cC....LL..",
               ".....GGGG..LlL..",
               "......GG..LlL...",
               "......GG.LlL....",
               "......GG.lL.....",
               ".....GWGG.......",
               "....GW...G......",
               "...GW.....G.....",
               "...GAAAAAAAG....",
               "...GAaAAAAAG....",
               "...GAAAAaAAG....",
               "....GAAAAAG.....",
               ".....GGGGG......",
               "................",
               "................"],
              {'C': '#9c6b3f', 'c': '#6e4a28', 'G': '#cfe6ea', 'W': '#ffffff', 'A': '#5fbf5a', 'a': '#9ae07f',
               'L': '#6fb04a', 'l': '#3f7a2c'}),
    # An anvil with three sparks.
    'anvil': (["......Y.........",
               "..Y......O......",
               "........Y...Y...",
               "....O...........",
               "................",
               ".LLLLLLLLLLLLLL.",
               "LWWLLLLLLLLLLLMD",
               ".LMMMMMMMMMMMMD.",
               "....DMMMMMMD....",
               ".....DMMMMD.....",
               ".....DMMMMD.....",
               "....DMMMMMMD....",
               "...LLMMMMMMMMD..",
               "..LMMMMMMMMMMMD.",
               "..DDDDDDDDDDDDD.",
               "................"],
              {'L': '#8f979d', 'W': '#c9d0d4', 'M': '#5a6268', 'D': '#30363a', 'Y': '#ffd35a', 'O': '#ff8a3a'}),
}


def hammer():
    """A claw hammer on the diagonal: the handle from the bottom left, the head across it at the top right."""
    image = Image.new('RGBA', (16, 16))
    px = image.load()
    for i in range(10):
        px[1 + i, 14 - i] = rgb('#b07a45')
        px[2 + i, 14 - i] = rgb('#6e4a28')
    for d, shade in ((5, '#d0d8de'), (6, '#8b959c'), (7, '#555e65')):
        for s in range(12, 21):
            if (s + d) % 2 == 0 and 11 <= s <= 20:
                x, y = (s + d) // 2, (s - d) // 2
                px[x, y] = rgb(shade)
    for x, y in ((8, 2), (7, 2), (8, 1)):
        px[x, y] = rgb('#8b959c')
    px[13, 7] = rgb('#eef3f6')
    return image


def sigil(name):
    if name == 'hammer':
        return outline(hammer())
    rows, palette = SIGILS[name]
    return outline(paint(rows, palette))


# ------------------------------------------------------------------------------------------------ frames

STYLES = {
    'steel': {'thick': 3, 'dark': '#3d454b', 'base': '#8c97a0', 'light': '#d0d8de', 'accent': '#e2763f', 'inner': '#161a1c'},
    'wood': {'thick': 4, 'dark': '#4a3220', 'base': '#8a5f3a', 'light': '#b98b58', 'accent': '#d8c28a', 'inner': '#191510'},
    'stone': {'thick': 4, 'dark': '#55584f', 'base': '#8f918a', 'light': '#b9bbb2', 'accent': '#8a5cc0', 'inner': '#16171a'},
    'vine': {'thick': 2, 'dark': '#2f5222', 'base': '#4f8a35', 'light': '#86c15a', 'accent': '#d95a5a', 'inner': '#111a12'},
    'iron': {'thick': 3, 'dark': '#22272a', 'base': '#4d555b', 'light': '#8a939a', 'accent': '#d8b25a', 'inner': '#141617'},
}


def bezier(p0, p1, p2, p3, steps=40):
    out = []
    for i in range(steps + 1):
        t = i / steps
        mt = 1 - t
        out.append((mt ** 3 * p0[0] + 3 * mt * mt * t * p1[0] + 3 * mt * t * t * p2[0] + t ** 3 * p3[0],
                    mt ** 3 * p0[1] + 3 * mt * mt * t * p1[1] + 3 * mt * t * t * p2[1] + t ** 3 * p3[1]))
    return out


def spiral(cx, cy, r0, turns, start, direction):
    out = []
    steps = int(60 * turns)
    for i in range(steps + 1):
        t = i / steps
        angle = start + direction * t * turns * 2 * math.pi
        r = r0 * (1 - 0.75 * t)
        out.append((cx + math.cos(angle) * r, cy + math.sin(angle) * r))
    return out


def centreline():
    """The left half of the frame's middle line, from the curl at the corner to the top of the dome."""
    top = PANEL_TOP - 3
    curl = spiral(MARGIN - 5, top - 5, 6, 1.1, math.pi / 2, 1)[::-1]  # winds out of the curl toward the rail
    rail = [(MARGIN - 5 + i, top) for i in range(0, 110)]
    dome = bezier((MARGIN + 104, top), (CX - 36, top), (CX - 30, 7), (CX, 4), 50)
    return curl + rail + dome


def mirror(points):
    return [(CREST_W - 1 - x, y) for x, y in points]


def stamp_band(mask, shade, points, thick):
    """Marks every pixel within thick/2 of the line; shade holds -1 (upper edge) .. 1 (lower edge)."""
    h = thick / 2
    for i in range(len(points) - 1):
        (x0, y0), (x1, y1) = points[i], points[i + 1]
        length = math.hypot(x1 - x0, y1 - y0) or 1
        nx, ny = -(y1 - y0) / length, (x1 - x0) / length
        if ny < 0:
            nx, ny = -nx, -ny
        for s in range(int(length * 3) + 1):
            t = s / (length * 3)
            cx, cy = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            for o in range(-int(h * 4), int(h * 4) + 1):
                off = o / 4
                x, y = int(round(cx + nx * off)), int(round(cy + ny * off))
                if 0 <= x < CREST_W and 0 <= y < CREST_H:
                    mask[y][x] = True
                    shade[y][x] = off / max(h, 0.5)


def frame(style_name, sigil_name, accent):
    style = STYLES[style_name]
    image = Image.new('RGBA', (CREST_W, CREST_H))
    px = image.load()
    mask = [[False] * CREST_W for _ in range(CREST_H)]
    shade = [[0.0] * CREST_W for _ in range(CREST_H)]
    left = centreline()
    thick = style['thick']
    for points in (left, mirror(left)):
        stamp_band(mask, shade, points, thick)
    dark, base, light = rgb(style['dark']), rgb(style['base']), rgb(style['light'])
    rng = random.Random(style_name)
    for y in range(CREST_H):
        for x in range(CREST_W):
            if mask[y][x]:
                s = shade[y][x]
                colour = light if s < -0.45 else dark if s > 0.55 else base
                if style_name == 'wood' and colour == base and rng.random() < 0.18:
                    colour = rgb('#76502f')
                if style_name == 'stone' and colour == base and rng.random() < 0.22:
                    colour = rgb('#7d8078') if rng.random() < 0.5 else rgb('#a3a59c')
                px[x, y] = colour
    decorate(image, style_name, style, left, accent)
    medallion(image, style, sigil_name)
    return outline(image)


def along(points, spacing, start=0.0):
    """Evenly spaced points (and directions) along a polyline."""
    out, travelled, next_at = [], 0.0, start
    for i in range(len(points) - 1):
        (x0, y0), (x1, y1) = points[i], points[i + 1]
        seg = math.hypot(x1 - x0, y1 - y0)
        while seg and travelled + seg >= next_at:
            t = (next_at - travelled) / seg
            out.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, (x1 - x0) / seg, (y1 - y0) / seg))
            next_at += spacing
        travelled += seg
    return out


def put(image, x, y, colour):
    if 0 <= x < image.width and 0 <= y < image.height:
        image.load()[x, y] = colour


def decorate(image, name, style, left, accent):
    rail = left[len(left) - 160:]
    marks = along(rail, {'steel': 16, 'wood': 26, 'stone': 11, 'vine': 7, 'iron': 18}[name], 8)
    halves = (marks, [(CREST_W - 1 - x, y, -dx, dy) for x, y, dx, dy in marks])
    for side in halves:
        for i, (x, y, dx, dy) in enumerate(side):
            x, y = int(round(x)), int(round(y))
            if name == 'steel':
                put(image, x, y, rgb('#eef3f6'))
                put(image, x + 1, y, rgb('#eef3f6'))
                put(image, x + 1, y + 1, rgb(style['dark']))
            elif name == 'iron':
                for ox, oy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                    put(image, x + ox, y + oy - 1, rgb('#a3abb1'))
                put(image, x, y - 1, rgb(accent))
            elif name == 'wood':
                for k in range(-2, 3):
                    put(image, x + k + 1, y - 2 + (k + 2), rgb('#d9c38c'))
                    put(image, x + k + 2, y - 2 + (k + 2), rgb('#a8925e'))
            elif name == 'stone':
                for k in range(-2, 3):
                    put(image, x, y + k, rgb('#43463f'))
            elif name == 'vine':
                up = -1 if i % 2 else 1
                leaf = [(0, up * 2), (1, up * 2), (1, up * 3), (2, up * 3), (0, up * 3), (-1, up * 2)]
                for ox, oy in leaf:
                    put(image, x + ox, y + oy, rgb('#88c25a') if oy == up * 3 else rgb('#5f9a3f'))
                if i % 5 == 2:
                    put(image, x, y - up * 2, rgb(accent))
    if name == 'iron':
        for side in (1, -1):
            x0 = MARGIN - 4 if side == 1 else CREST_W - MARGIN + 3
            for k in range(3):
                cy = PANEL_TOP + 1 + k * 5
                for ox, oy in ((-1, 0), (1, 0), (-1, 1), (1, 1), (-1, 2), (1, 2), (0, -1), (0, 3)):
                    put(image, x0 + ox, cy + oy, rgb('#8a939a'))
    if name == 'wood':
        for side in (1, -1):
            cx = MARGIN - 6 if side == 1 else CREST_W - MARGIN + 5
            cy = PANEL_TOP - 8
            for yy in range(-4, 5):
                for xx in range(-4, 5):
                    d = math.hypot(xx, yy)
                    if d <= 4.2:
                        put(image, cx + xx, cy + yy, rgb('#c9a06a') if int(d) % 2 == 0 else rgb('#8a5f3a'))
    if name == 'stone':
        for side in (1, -1):
            cx = MARGIN - 6 if side == 1 else CREST_W - MARGIN + 5
            for k, (w, h) in enumerate(((9, 5), (7, 4), (5, 3))):
                top = PANEL_TOP - 4 - k * 5 - h
                for yy in range(h):
                    for xx in range(w):
                        put(image, cx - w // 2 + xx, top + yy, rgb('#a3a59c') if yy == 0 else rgb('#8f918a'))


def medallion(image, style, sigil_name):
    """The disc under the dome that carries the sigil, ringed in the frame's material."""
    cy, r = 16, 11
    px = image.load()
    for y in range(cy - r - 1, cy + r + 2):
        for x in range(CX - r - 1, CX + r + 2):
            d = math.hypot(x + 0.5 - CX, y + 0.5 - cy)
            if d <= r - 2:
                px[x, y] = rgb(style['inner'])
            elif d <= r:
                px[x, y] = rgb(style['light']) if y < cy - 3 else rgb(style['dark']) if y > cy + 3 else rgb(style['base'])
    mark = sigil(sigil_name)
    image.alpha_composite(mark, (CX - 8, cy - 8))


def build():
    OUT.mkdir(parents=True, exist_ok=True)
    for file in sorted(DESIGN.glob('*.json')):
        data = json.loads(file.read_text(encoding='utf-8'))
        crest = data.get('crest')
        if not crest:
            continue
        frame(crest['frame'], crest['sigil'], data.get('accent', '#e2763f')).save(OUT / f'{file.stem}.png')
        sigil(crest['sigil']).resize((32, 32), Image.Resampling.NEAREST).save(OUT / f'{file.stem}_sigil.png')
        print('crest', file.stem, crest)


if __name__ == '__main__':
    build()
