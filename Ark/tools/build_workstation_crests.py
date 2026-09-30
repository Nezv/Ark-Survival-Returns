"""Frames for the workstation screens (P14): each bench's panel is set into a frame of its material, sigil on top.

Each bench names its frame in its design file ({"crest": {"frame": "steel", "sigil": "helm"}}). The frame is the panel's
edge, not an ornament above it: a band around all four sides that covers the panel's vanilla outline and bevel, corner
pieces, a crossbar between the graph well and the craft bar, the bench's sigil in a medallion set into the top edge and
a smaller piece at the bottom. Riveted steel with leather straps for the Armoury, logs lashed with rope for the Working
Station, cut stone blocks and a millstone for the Mortar & Pestle, a vine-wrapped branch with herbs for the Medicine
Bench, and hammered iron with gold bolts, chains and forge heat for the Smithing Table. Tones sit a step darker than
the materials' items so the frame belongs to the screen's dark palette; light comes from the top left, like the GUI.
Sigils are 16x16 pixel art, like an item.

The panel size comes from design/workstations/graph_style.json. Writes design/workstations/crests/<bench>.png (the
frame, FRAME_W x FRAME_H; the panel's top-left corner sits at (MARGIN, PANEL_TOP) of the image, PANEL_BOTTOM units of
frame hang below it; the panel interior is transparent) and <bench>_sigil.png (32x32, the sigil at 2x, the icon of a
planned bench block). The showcase draws the frame over the panel face and under the graph; the mod will blit the same
files as GUI sprites.
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

PANEL = json.loads((DESIGN / 'graph_style.json').read_text(encoding='utf-8'))['panel']
PANEL_W, PANEL_H = PANEL['width'], PANEL['height']
RAIL = PANEL_H - PANEL['bar'] - PANEL['inset']  # the well's lower edge: the crossbar runs here, above the craft bar
MARGIN, PANEL_TOP, PANEL_BOTTOM = 16, 22, 21
FRAME_W, FRAME_H = PANEL_W + 2 * MARGIN, PANEL_TOP + PANEL_H + PANEL_BOTTOM
CX = PANEL_W // 2
LIP = 2         # the frame covers the panel's outline and light bevel: panel units 0 and 1 from each edge
SEAL_Y = -5     # the medallion's centre, on the top band
SHADOW = (0, 0, 0, 90)


def mix(a, b, t):
    ca, cb = rgb(a) if isinstance(a, str) else a, rgb(b) if isinstance(b, str) else b
    return tuple(round(ca[i] + (cb[i] - ca[i]) * t) for i in range(3)) + (255,)


class Frame:
    """One frame image, drawn in panel units: (0, 0) is the panel's top-left corner. A colour is an index into the
    material's ramp (dark to light), a hex string or an RGBA tuple."""

    def __init__(self, ramp, seed):
        self.image = Image.new('RGBA', (FRAME_W, FRAME_H))
        self.px = self.image.load()
        self.ramp = [rgb(c) for c in ramp]
        self.tones = {}
        self.rng = random.Random(seed)

    def put(self, x, y, c):
        ix, iy = x + MARGIN, y + PANEL_TOP
        if not (0 <= ix < FRAME_W and 0 <= iy < FRAME_H):
            return
        if isinstance(c, int):
            c = max(0, min(len(self.ramp) - 1, c))
            self.tones[x, y] = c
            self.px[ix, iy] = self.ramp[c]
        else:
            self.tones.pop((x, y), None)
            self.px[ix, iy] = rgb(c) if isinstance(c, str) else c

    def get(self, x, y):
        ix, iy = x + MARGIN, y + PANEL_TOP
        return self.px[ix, iy] if 0 <= ix < FRAME_W and 0 <= iy < FRAME_H else (0, 0, 0, 0)

    def shift(self, x, y, by):
        """Darkens (by < 0) or lightens a pixel of the material."""
        t = self.tones.get((x, y))
        if t is not None:
            self.put(x, y, t + by)

    def bevel(self, mask, face, light=None, dark=None, ramp=None):
        """Fills a set of pixels as a raised piece: top and left edges catch the light, bottom and right fall dark."""
        light = face + 1 if light is None else light
        dark = face - 2 if dark is None else dark
        for x, y in mask:
            if (x, y - 1) not in mask or (x - 1, y) not in mask:
                c = light
            elif (x, y + 1) not in mask or (x + 1, y) not in mask:
                c = dark
            else:
                c = face
            self.put(x, y, c if ramp is None else ramp[max(0, min(len(ramp) - 1, c))])

    def finish(self):
        """Outlines every piece (inside the panel that line is the frame's shadow on the face) and drops a soft shadow
        down and right of the frame, outside the panel."""
        image = outline(self.image)
        px = image.load()
        out = Image.new('RGBA', image.size)
        sp = out.load()
        for y in range(2, FRAME_H):
            for x in range(1, FRAME_W):
                inside = MARGIN <= x < MARGIN + PANEL_W and PANEL_TOP <= y < PANEL_TOP + PANEL_H
                if not px[x, y][3] and not inside and px[x - 1, y - 2][3]:
                    sp[x, y] = SHADOW
        out.alpha_composite(image)
        return out


def band(t, inner=LIP):
    """Every pixel of a band t units outside the panel's edge and `inner` inside it: (x, y, side, depth from the outer
    edge). The corners are mitred."""
    for y in range(-t, PANEL_H + t):
        for x in range(-t, PANEL_W + t):
            if t + inner <= x < PANEL_W - inner - t and t + inner <= y < PANEL_H - inner - t:
                continue
            side, d = min((('top', y + t), ('bottom', PANEL_H - 1 + t - y), ('left', x + t), ('right', PANEL_W - 1 + t - x)),
                          key=lambda s: s[1])
            if d < t + inner:
                yield x, y, side, d


def shade(profile, side, d):
    """A band pixel's tone: the profile runs from the lit edge to the dark one, and top and left bands face the light,
    so a band reads as a bar lit from the top left on every side."""
    return profile[d] if side in ('top', 'left') else profile[len(profile) - 1 - d]


def rect(x0, y0, x1, y1):
    return {(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)}


def corners(mask):
    """A top-left piece and its mirror images at the other three corners."""
    return [{(PANEL_W - 1 - x if fx else x, PANEL_H - 1 - y if fy else y) for x, y in mask}
            for fx in (False, True) for fy in (False, True)]


def ends(mask):
    """A left-side piece and its mirror image on the right."""
    return [mask, {(PANEL_W - 1 - x, y) for x, y in mask}]


def spots(points, w=2, h=2, fy=True):
    """Top-left points of w x h details at a top-left corner, repeated at every corner (or both sides when not fy)."""
    out = []
    for x, y in points:
        out += [(x, y), (PANEL_W - w - x, y)]
        if fy:
            out += [(x, PANEL_H - h - y), (PANEL_W - w - x, PANEL_H - h - y)]
    return out


def facing(dx, dy):
    """1 where a round surface faces the top-left light, -1 where it faces away."""
    d = math.hypot(dx, dy) or 1
    return (-dx - dy) / (d * math.sqrt(2))


def seal(f, ring, inner, sigil_name, r=13, hole=10, cy=SEAL_Y):
    """The medallion set into the top band: ring(f, x, y, dx, dy, d) paints the material between hole and r, inner fills
    the disc behind the sigil."""
    for y in range(cy - r - 1, cy + r + 1):
        for x in range(CX - r - 1, CX + r + 1):
            dx, dy = x + 0.5 - CX, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d <= hole:
                f.put(x, y, inner)
            elif d <= r:
                ring(f, x, y, dx, dy, d)
    f.image.alpha_composite(sigil(sigil_name), (CX - 8 + MARGIN, cy - 8 + PANEL_TOP))


def rivet(f, x, y, hi=6, mid=4, lo=3, shadow=1):
    """A 2x2 dome with its shadow to the bottom right."""
    f.put(x, y, hi)
    f.put(x + 1, y, mid)
    f.put(x, y + 1, mid)
    f.put(x + 1, y + 1, lo)
    f.put(x + 2, y + 1, shadow)
    f.put(x + 1, y + 2, shadow)
    f.put(x + 2, y + 2, shadow)


def gem(f, x, y, accent):
    """A 4x4 cut stone in the bench's accent colour."""
    tones = {'L': mix(accent, '#ffffff', 0.5), 'A': rgb(accent), 'd': mix(accent, '#000000', 0.35),
             'D': mix(accent, '#000000', 0.62)}
    for j, row in enumerate(('.LA.', 'LAAd', 'AAdD', '.dD.')):
        for i, ch in enumerate(row):
            if ch != '.':
                f.put(x + i, y + j, tones[ch])


def leaf(f, x0, y0, angle, length, width, ramp):
    """A pointed leaf from (x0, y0) along angle (degrees, 0 = right, 90 = down): a midrib, a lit half toward the top
    left and a darker half."""
    a = math.radians(angle)
    ux, uy = math.cos(a), math.sin(a)
    px_, py_ = -uy, ux
    lit_side = 1 if (px_ * -1 + py_ * -1) > 0 else -1
    reach = int(length + width + 2)
    for y in range(y0 - reach, y0 + reach + 1):
        for x in range(x0 - reach, x0 + reach + 1):
            rx, ry = x + 0.5 - (x0 + 0.5), y + 0.5 - (y0 + 0.5)
            along, across = rx * ux + ry * uy, rx * px_ + ry * py_
            if along < -0.3 or along > length:
                continue
            half = width * math.sin(math.pi * min(1, max(0, (along + 0.3) / (length + 0.3)))) ** 0.8
            if abs(across) > half + 0.25:
                continue
            if along < 1.2:
                c = ramp[1]
            elif abs(across) < 0.5 and along < length - 1.5:
                c = ramp[2]
            elif abs(across) > half - 0.6:
                c = ramp[2] if across * lit_side > 0 else ramp[1]
            else:
                c = ramp[4] if across * lit_side > 0 else ramp[3]
            f.put(x, y, c)


# ---------------------------------------------------------------------------------------------- materials

STEEL = ['#101316', '#1e2429', '#30383e', '#475158', '#657077', '#8f9aa1', '#c2cacf']
LEATHER = ['#26120c', '#442015', '#62301f', '#81452c', '#a15f3d']


def steel(sigil_name, accent):
    """Armoury: riveted steel plate with angle brackets at the corners and leather straps on the sides."""
    f, t = Frame(STEEL, 'steel'), 6
    profile = [5, 4, 4, 3, 3, 3, 2, 1]
    for x, y, side, d in band(t):
        f.put(x, y, shade(profile, side, d))
    m = (-t + LIP - 1) // 2
    # Plate seams, a rivet either side of each.
    for s in (52, 108):
        for x in (s, PANEL_W - 2 - s):
            for y0 in (-t, PANEL_H - LIP):
                for k in range(1, t + LIP - 1):
                    f.put(x, y0 + k, 1)
                    f.put(x + 1, y0 + k, 4)
        for x, y in spots([(s - 6, m), (s + 5, m)]):
            rivet(f, x, y)
    for s in (58, 118):
        for x0 in (-t, PANEL_W - LIP):
            for k in range(1, t + LIP - 1):
                f.put(x0 + k, s, 1)
                f.put(x0 + k, s + 1, 4)
        for x, y in spots([(m, s - 6), (m, s + 5)], fy=False):
            rivet(f, x, y)
    # Leather straps buckled round the sides.
    y0 = 80
    for piece in ends(rect(-t - 2, y0, LIP - 1, y0 + 9)):
        f.bevel(piece, 2, 3, 0, ramp=LEATHER)
        for x, y in piece:
            if y in (y0 + 2, y0 + 7) and x % 2 == 0 and -t - 1 <= x <= PANEL_W + t:
                f.put(x, y, LEATHER[4])
    for x in (-t - 5, PANEL_W + t):
        f.bevel(rect(x, y0 - 2, x + 4, y0 + 11) - rect(x + 1, y0, x + 3, y0 + 9), 4, 6, 2)
        for k in range(y0, y0 + 10):
            for i in (1, 2, 3):
                f.put(x + i, k, LEATHER[2 if i < 3 else 1])
        for i in (1, 2, 3):
            f.put(x + i, y0 + 4, 5)
            f.put(x + i, y0 + 5, 2)
    # Angle brackets at the corners.
    arm = 24
    bracket = {(x, y) for y in range(-t - 1, LIP + 1) for x in range(-t - 1, arm + 1) if x <= arm - 8 + LIP - y}
    bracket |= {(x, y) for x in range(-t - 1, LIP + 1) for y in range(-t - 1, arm + 1) if y <= arm - 8 + LIP - x}
    bracket = {(x, y) for x, y in bracket if (x + t + 1) + (y + t + 1) >= 4}
    for piece in corners(bracket):
        f.bevel(piece, 4, 5, 2)
    for x, y in spots([(m, m), (m + 12, m), (m, m + 12)]):
        rivet(f, x, y)
    # The crossbar between the well and the craft bar, bolted through the sides.
    for x in range(0, PANEL_W):
        for k, tone in enumerate((5, 4, 3, 1)):
            f.put(x, RAIL + k, tone)
    joint = rect(-t - 2, RAIL - 3, -1, RAIL + 6) | rect(0, RAIL, 7, RAIL + 4)
    for piece in ends(joint):
        f.bevel(piece, 4, 5, 2)
    for x, y in spots([(-5, RAIL + 1), (3, RAIL + 1)], fy=False):
        rivet(f, x, y)
    # A raised plate under the medallion, and the medallion: a riveted steel ring round the helm.
    saddle = {(x, y) for x in range(CX - 27, CX + 27) for y in range(-t - 4, LIP + 3)
              if -t - 4 + max(0, int(abs(x + 0.5 - CX)) - 16) // 2 <= y <= LIP + 2 - max(0, int(abs(x + 0.5 - CX)) - 18) // 2}
    f.bevel(saddle, 4, 5, 2)
    for x, y in ((CX - 23, -4), (CX + 21, -4)):
        rivet(f, x, y)

    def ring(f, x, y, dx, dy, d):
        fc = facing(dx, dy)
        if d > 12:
            f.put(x, y, 5 if fc > 0.3 else 3 if fc > -0.3 else 2)
        elif d <= 11:
            f.put(x, y, 1 if fc > 0 else 4)
        else:
            f.put(x, y, 6 if fc > 0.75 else 5 if fc > 0.1 else 4 if fc > -0.5 else 3)
    seal(f, ring, '#0c0f0f', sigil_name)
    for k in range(8):
        a = math.pi / 8 + k * math.pi / 4
        x, y = int(math.floor(CX + math.cos(a) * 11.6)), int(math.floor(SEAL_Y + math.sin(a) * 11.6))
        f.put(x, y, 6)
        f.put(x + 1, y + 1, 2)
    # An escutcheon at the bottom with the accent stone.
    shield = set()
    for y in range(PANEL_H - 4, PANEL_H + t + 7):
        half = 9 if y <= PANEL_H + t else 9 - (y - PANEL_H - t) * 3 // 2
        shield |= {(x, y) for x in range(CX - half, CX + half)}
    f.bevel(shield, 4, 5, 2)
    gem(f, CX - 2, PANEL_H + 1, accent)
    for x in (CX - 7, CX + 5):
        rivet(f, x, PANEL_H - 2)
    return f.finish()


WOOD = ['#1a110a', '#2d1f13', '#45301d', '#61442a', '#80603a', '#a07b4c', '#c29c66']
ROPE = ['#2a2216', '#51442d', '#7c6a46', '#a69064', '#cbb688']


def rope(f, mask, slant=1):
    """Rope wound over a region: diagonal turns with a twist highlight."""
    for x, y in mask:
        k = (x * slant + y) % 4
        f.put(x, y, ROPE[3] if k == 0 else ROPE[2] if k == 1 else ROPE[1] if k == 2 else ROPE[0])


def wood(sigil_name, accent):
    """Working Station: four logs, the top and bottom ones overhanging the sides, lashed with rope at every joint."""
    f, t = Frame(WOOD, 'wood'), 7
    n = t + LIP
    profile = [5, 4, 4, 3, 3, 3, 2, 2, 1]
    over = 9
    logs = []
    for side in ('left', 'right', 'top', 'bottom'):
        if side in ('left', 'right'):
            x0 = -t if side == 'left' else PANEL_W - LIP
            cells = [(x, y) for y in range(-t, PANEL_H + t) for x in range(x0, x0 + n)]
        else:
            y0 = -t if side == 'top' else PANEL_H - LIP
            cells = []
            for x in range(-t - over, PANEL_W + t + over):
                for y in range(y0, y0 + n):
                    # rounded ends
                    end = min(x - (-t - over), PANEL_W + t + over - 1 - x)
                    if end < 4 and math.hypot(4 - end - 0.5, y - (y0 + n / 2 - 0.5)) > 4.6:
                        continue
                    cells.append((x, y))
        logs.append((side, cells))
    for side, cells in logs:
        for x, y in cells:
            d = {'top': y + t, 'bottom': PANEL_H - 1 + t - y, 'left': x + t, 'right': PANEL_W - 1 + t - x}[side]
            f.put(x, y, shade(profile, side, d))
        # bark: streaks along the grain and a few knots
        along_x = side in ('top', 'bottom')
        for _ in range(len(cells) // 7):
            x, y = f.rng.choice(cells)
            for k in range(f.rng.randint(2, 6)):
                f.shift(x + (k if along_x else 0), y + (0 if along_x else k), -1)
        for _ in range(3 if along_x else 2):
            x, y = f.rng.choice(cells)
            d = {'top': y + t, 'bottom': PANEL_H - 1 + t - y, 'left': x + t, 'right': PANEL_W - 1 + t - x}[side]
            if 2 <= d <= n - 3 and abs(x - CX) > 24:
                for kx, ky, tone in ((0, 0, 1), (1, 0, 1), (-1, 0, 2), (2, 0, 2), (0, -1, 5), (1, 1, 3)):
                    f.put(x + kx, y + ky, tone) if along_x else f.put(x + ky, y + kx, tone)
    # end grain on the overhanging log ends
    for y0 in (-t, PANEL_H - LIP):
        for cx in (-t - over + 4, PANEL_W + t + over - 5):
            cy = y0 + n // 2
            for y in range(cy - 5, cy + 6):
                for x in range(cx - 5, cx + 6):
                    d = math.hypot(x - cx, y - cy)
                    if d <= 4.5:
                        fc = facing(x - cx, y - cy)
                        f.put(x, y, (2 if fc > 0 else 1) if d > 3.6 else 3 if d < 0.8 else
                              (6 if int(d * 1.3) % 2 == 0 else 5) - (1 if fc < -0.3 else 0))
    # rope lashings: an X over every corner joint, turns where the plank meets the sides
    lash = {(x, y) for x in range(-t - 1, LIP + 1) for y in range(-t - 1, LIP + 1)
            if abs((x + t + 1) - (y + t + 1)) <= 1 or abs((x + t + 1) + (y + t + 1) - (n + 1)) <= 1}
    for piece in corners(lash):
        rope(f, piece)
    # the plank between the well and the craft bar, nailed to the side logs
    plank = rect(LIP, RAIL, PANEL_W - LIP - 1, RAIL + 3)
    for x, y in plank:
        f.put(x, y, (5, 4, 4, 2)[y - RAIL])
    for _ in range(40):
        x = f.rng.randint(LIP, PANEL_W - LIP - 6)
        y = f.rng.randint(RAIL + 1, RAIL + 2)
        for k in range(f.rng.randint(3, 8)):
            f.shift(x + k, y, -1)
    for x in (LIP + 3, PANEL_W - LIP - 5):
        f.put(x, RAIL + 1, '#9ea39b')
        f.put(x + 1, RAIL + 1, '#6c716a')
        f.put(x, RAIL + 2, '#6c716a')
        f.put(x + 1, RAIL + 2, '#3c403b')
    for piece in ends(rect(-t - 1, RAIL - 2, LIP, RAIL + 5)):
        rope(f, piece)
    # the medallion: a log slice, lashed to the top log either side
    for piece in ends({(x, y) for x in range(CX - 21, CX - 15) for y in range(-t - 1, LIP + 1) if x not in (CX - 18,)}):
        rope(f, piece, -1)

    def ring(f, x, y, dx, dy, d):
        fc = facing(dx, dy)
        if d > 12:
            f.put(x, y, 2 if fc > 0 else 1)
        else:
            f.put(x, y, (5 if int(d * 1.4) % 2 == 0 else 4) + (1 if fc > 0.6 else -1 if fc < -0.6 else 0))
    seal(f, ring, '#130e09', sigil_name, hole=9)
    # a rope binding round the middle of the bottom log
    rope(f, {(x, y) for x in range(CX - 4, CX + 4) for y in range(PANEL_H - LIP - 1, PANEL_H + t + 1)})
    return f.finish()


STONE = ['#17181a', '#28292b', '#3c3e3c', '#535550', '#6c6e67', '#888a80', '#a6a79b']
MOSS = ['#18241a', '#263d21', '#3a5a2c', '#557a37']
BERRY = ['#2a1740', '#4a2b6e', '#7550a8', '#a582d3']


def lay(f, x0, y0, x1, y1, horizontal, short, long_):
    """Fills a rectangle with blocks along one axis, a mortar line between them."""
    for x, y in rect(x0, y0, x1, y1):
        f.put(x, y, 1)
    a, end = (x0, x1) if horizontal else (y0, y1)
    while a <= end:
        b = min(end, a + f.rng.randint(short, long_))
        if end - b < short // 2:
            b = end
        block = rect(a, y0, b - 1 if b < end else b, y1) if horizontal else rect(x0, a, x1, b - 1 if b < end else b)
        face = f.rng.choice((3, 3, 4))
        f.bevel(block, face, face + 1, face - 1)
        for x, y in block:
            if f.rng.random() < 0.12:
                f.shift(x, y, f.rng.choice((-1, 1)))
        a = b + 1


def stone(sigil_name, accent):
    """Mortar & Pestle: two courses of cut stone, quoins at the corners, a stone ledge and a millstone."""
    f, t = Frame(STONE, 'stone'), 7
    q = 6   # quoins reach q units into the panel
    for y0, y1 in ((-t, -3), (-2, LIP - 1)):
        lay(f, q + 1, y0, PANEL_W - q - 2, y1, True, 12, 22)
        lay(f, q + 1, PANEL_H - 1 - y1, PANEL_W - q - 2, PANEL_H - 1 - y0, True, 12, 22)
        lay(f, y0, q + 1, y1, PANEL_H - q - 2, False, 10, 20)
        lay(f, PANEL_W - 1 - y1, q + 1, PANEL_W - 1 - y0, PANEL_H - q - 2, False, 10, 20)
    def ashlar(piece):
        """A dressed stone: bevelled, speckled, with a chiselled margin two units in."""
        f.bevel(piece, 4, 5, 2)
        xs, ys = [x for x, _ in piece], [y for _, y in piece]
        x0, y0, x1, y1 = min(xs) + 2, min(ys) + 2, max(xs) - 2, max(ys) - 2
        for x, y in piece:
            if x0 <= x <= x1 and y0 <= y <= y1 and (x in (x0, x1) or y in (y0, y1)):
                f.put(x, y, 2 if x == x0 or y == y0 else 5)
            elif f.rng.random() < 0.14:
                f.shift(x, y, f.rng.choice((-1, 1)))
    for piece in corners(rect(-t - 2, -t - 2, q, q)):
        ashlar(piece)
    # the ledge between the well and the craft bar, on a bond stone at each end
    lay(f, LIP, RAIL, PANEL_W - LIP - 1, RAIL + 3, True, 20, 34)
    for piece in ends(rect(-t - 2, RAIL - 3, -1, RAIL + 6) | rect(0, RAIL, 6, RAIL + 4)):
        f.bevel(piece, 4, 5, 2)
        for x, y in piece:
            if f.rng.random() < 0.14:
                f.shift(x, y, f.rng.choice((-1, 1)))
    # moss on the upper stones, berries left from the grinding on the lower ones
    for x0, w in ((-t - 3, 11), (24, 8), (74, 6), (PANEL_W - 64, 7), (PANEL_W - 3, 10)):
        for x in range(x0, x0 + w):
            quoin = -t - 2 <= x <= q or PANEL_W - q - 1 <= x <= PANEL_W + t + 1
            top = -t - 3 if quoin else -t
            edge = min(x - x0, x0 + w - 1 - x)
            f.put(x, top - (1 if edge > 1 and f.rng.random() < 0.6 else 0), MOSS[3])
            for k in range(1 + (edge > 0) + (edge > 2) * f.rng.randint(0, 3)):
                f.put(x, top + k, MOSS[2] if k < 2 else MOSS[1])
    for x, y in ((12, PANEL_H + 2), (15, PANEL_H + 3), (PANEL_W - 30, PANEL_H), (PANEL_W - 27, PANEL_H + 1)):
        f.put(x, y, BERRY[3])
        f.put(x + 1, y, BERRY[2])
        f.put(x, y + 1, BERRY[2])
        f.put(x + 1, y + 1, BERRY[1])
    # the medallion: a millstone with its grinding furrows, set between two wedge stones
    for piece in ends({(x, y) for y in range(-t - 3, LIP + 2) for x in range(CX - 25 + (y + t + 3) // 3, CX - 12)}):
        ashlar(piece)

    def ring(f, x, y, dx, dy, d):
        fc = facing(dx, dy)
        tone = 6 if fc > 0.6 else 5 if fc > 0.1 else 4 if fc > -0.5 else 3
        a = (math.atan2(dy, dx) / (2 * math.pi) * 10) % 1
        if d <= 9.9:
            tone = 2 if fc > 0 else 4
        elif a < 0.14 and d < 12.4:
            tone = 2
        elif d > 12.4:
            tone -= 2
        elif f.rng.random() < 0.15:
            tone -= 1
        f.put(x, y, tone)
    seal(f, ring, '#111214', sigil_name, hole=9)
    # a keystone under the bottom course
    key = set()
    for y in range(PANEL_H - 5, PANEL_H + t + 5):
        half = 10 - (y - PANEL_H + 5) // 3
        key |= {(x, y) for x in range(CX - half, CX + half)}
    f.bevel(key, 4, 5, 2)
    for x, y in list(key):
        if f.rng.random() < 0.12:
            f.shift(x, y, f.rng.choice((-1, 1)))
    gem(f, CX - 2, PANEL_H + 2, accent)
    return f.finish()


BRANCH = ['#18120b', '#2d2217', '#453524', '#5f4b32', '#7a6344']
LEAF = ['#0f1d0d', '#1a3216', '#2a4d22', '#3d6e2d', '#5a9540', '#86bd5c']
TWINE = ['#3b3322', '#6e6242', '#a39469', '#cdbf92']


def berries(f, x, y):
    for bx, by in ((0, 0), (2, 1), (0, 2)):
        f.put(x + bx, y + by, '#e3776b')
        f.put(x + bx + 1, y + by, '#b83b33')
        f.put(x + bx, y + by + 1, '#b83b33')
        f.put(x + bx + 1, y + by + 1, '#72201c')


def flower(f, x, y, big=False):
    """A pale flower (a plus, or a larger star when big) with a yellow heart."""
    petals = ((-1, 0), (1, 0), (0, -1), (0, 1))
    if big:
        petals += ((-2, 0), (2, 0), (0, -2), (0, 2), (-1, -1), (1, -1), (-1, 1), (1, 1))
    for dx, dy in petals:
        f.put(x + dx, y + dy, '#a9a48b' if dx + dy > 1 or (dx > 0 and dy > 0) else '#e8e4cd')
    f.put(x, y, '#e5bc4d')
    if big:
        f.put(x + 1, y + 1, '#b08a2e')


def vine(sigil_name, accent):
    """Medicine Bench: a frame of branches crossed and tied at the corners, a leafy vine with berries and flowers wound
    round it, a wreath round the flask and a bundle of drying herbs hanging below."""
    f, t = Frame(BRANCH, 'vine'), 4
    n = t + LIP
    profile = [4, 3, 3, 2, 2, 1]
    over = 6
    for side in ('left', 'right', 'top', 'bottom'):
        if side in ('left', 'right'):
            x0 = -t if side == 'left' else PANEL_W - LIP
            cells = [(x, y) for y in range(-t - over, PANEL_H + t + over) for x in range(x0, x0 + n)
                     if not (min(y + t + over, PANEL_H + t + over - 1 - y) < 2 and x in (x0, x0 + n - 1))]
        else:
            y0 = -t if side == 'top' else PANEL_H - LIP
            cells = [(x, y) for x in range(-t - over, PANEL_W + t + over) for y in range(y0, y0 + n)
                     if not (min(x + t + over, PANEL_W + t + over - 1 - x) < 2 and y in (y0, y0 + n - 1))]
        for x, y in cells:
            d = {'top': y + t, 'bottom': PANEL_H - 1 + t - y, 'left': x + t, 'right': PANEL_W - 1 + t - x}[side]
            f.put(x, y, shade(profile, side, d))
        along_x = side in ('top', 'bottom')
        for _ in range(len(cells) // 9):
            x, y = f.rng.choice(cells)
            for k in range(f.rng.randint(2, 5)):
                f.shift(x + (k if along_x else 0), y + (0 if along_x else k), -1)
    # twine where the branches cross
    for piece in corners({(x, y) for x in range(-t - 1, LIP + 1) for y in range(-t - 1, LIP + 1)
                          if abs(x - y) <= 1 or abs(x + y + t - LIP + 1) <= 1}):
        for x, y in piece:
            f.put(x, y, TWINE[3] if (x + y) % 3 == 0 else TWINE[2] if (x + y) % 3 == 1 else TWINE[1])

    # the vine: winds along the outside of every branch, dipping over it
    def along_side(side):
        if side == 'top':
            return [(s, lambda s, o: (s, -1 - o)) for s in range(-t, PANEL_W + t)]
        if side == 'bottom':
            return [(s, lambda s, o: (s, PANEL_H + o)) for s in range(-t, PANEL_W + t)]
        if side == 'left':
            return [(s, lambda s, o: (-1 - o, s)) for s in range(-t, PANEL_H + t)]
        return [(s, lambda s, o: (PANEL_W + o, s)) for s in range(-t, PANEL_H + t)]
    outward = {'top': -90, 'bottom': 90, 'left': 180, 'right': 0}
    phase = {'top': 0, 'bottom': 1.7, 'left': 3.1, 'right': 4.4}
    period = 20
    for side in ('top', 'bottom', 'left', 'right'):
        prev = None
        count = 0
        crest = round(period / 4 - phase[side] * period / (2 * math.pi))
        for s, at in along_side(side):
            o = 1.5 + 3.2 * math.sin(2 * math.pi * s / period + phase[side])
            x, y = at(s, round(o))
            if side in ('top', 'bottom') and abs(s + 0.5 - CX) < 17:
                prev = None
                continue
            f.put(x, y, LEAF[3] if o > 0 else LEAF[2])
            if prev is not None:   # close gaps where the vine climbs
                px_, py_ = prev
                while abs(px_ - x) + abs(py_ - y) > 1:
                    if side in ('top', 'bottom'):
                        py_ += 1 if y > py_ else -1
                    else:
                        px_ += 1 if x > px_ else -1
                    f.put(px_, py_, LEAF[2])
            prev = (x, y)
            if (s - crest) % period == 0 and 8 < s < (PANEL_W if side in ('top', 'bottom') else PANEL_H) - 8:
                count += 1
                lx, ly = at(s, round(o))
                for lean, length in ((-42, 6.5), (42, 5.5) if count % 2 else (42, 6.5)):
                    leaf(f, lx, ly, outward[side] + lean + f.rng.randint(-8, 8), length + f.rng.choice((-1, 0, 0, 1)), 2.1, LEAF)
                if count % 4 == 2:
                    bx, by = at(s - 1, round(o) + 4)
                    berries(f, bx, by)
                elif count % 4 == 0:
                    fx_, fy_ = at(s, round(o) + 5)
                    flower(f, fx_, fy_, True)
            elif (s - crest) % period == period // 2 and 8 < s:
                lx, ly = at(s, round(o))
                leaf(f, lx, ly, outward[side] + (70 if s % 2 else -70), 3.5, 1.3, LEAF)
    # leaf clusters and a flower at each corner
    for fx in (False, True):
        for fy in (False, True):
            cx, cy = (PANEL_W + t if fx else -t - 1), (PANEL_H + t if fy else -t - 1)
            base = (45 if fx else 135) if fy else (-45 if fx else -135)
            for turn, length in ((-50, 8), (-18, 10.5), (18, 10.5), (50, 8)):
                leaf(f, cx, cy, base + turn, length, 2.8, LEAF)
            flower(f, cx + (4 if fx else -4), cy + (4 if fy else -4), True)
    # a twig between the well and the craft bar, tied to the sides, with a strand of vine
    for x in range(LIP, PANEL_W - LIP):
        for k, tone in enumerate((4, 2, 1)):
            f.put(x, RAIL + k, tone)
        if f.rng.random() < 0.08:
            f.shift(x, RAIL + 1, -1)
    for x in range(LIP + 8, PANEL_W - LIP - 8):
        o = math.sin(2 * math.pi * x / 26)
        f.put(x, RAIL + 1 + round(o * 1.5), LEAF[3] if o < 0 else LEAF[2])
        if x % 26 == 13:
            leaf(f, x, RAIL + 3, 90 + (25 if x % 52 == 13 else -25), 2.5, 1.2, LEAF)
    for piece in ends(rect(-t - 1, RAIL - 1, LIP, RAIL + 3)):
        for x, y in piece:
            f.put(x, y, TWINE[3] if (x + y) % 3 == 0 else TWINE[2] if (x + y) % 3 == 1 else TWINE[1])

    # the medallion: a wreath of leaves with berries
    def ring(f, x, y, dx, dy, d):
        a = (math.atan2(dy, dx) / (2 * math.pi) * 14) % 1
        fc = facing(dx, dy)
        body = abs(d - 11.5) <= 1.9 * math.sin(math.pi * a) + 0.2
        tone = (4 if a < 0.45 else 3) + (1 if fc > 0.5 else -1 if fc < -0.5 else 0) if body else 1
        f.put(x, y, LEAF[max(0, min(5, tone))])
    seal(f, ring, '#0d140e', sigil_name)
    for k in range(4):
        a = math.pi / 4 + k * math.pi / 2
        berries(f, round(CX + math.cos(a) * 11.5) - 1, round(SEAL_Y + math.sin(a) * 11.5) - 1)
    # a bundle of herbs hanging from the bottom branch
    for y in range(PANEL_H + t - 1, PANEL_H + t + 2):
        f.put(CX - 1, y, TWINE[2])
    for angle, length in ((60, 9), (76, 10.5), (90, 11), (104, 10.5), (120, 9)):
        leaf(f, CX - 1, PANEL_H + t + 3, angle, length, 1.8, LEAF)
    for x in range(CX - 4, CX + 3):
        f.put(x, PANEL_H + t + 2, TWINE[3] if x % 2 else TWINE[2])
        f.put(x, PANEL_H + t + 3, TWINE[2] if x % 2 else TWINE[1])
    flower(f, CX - 6, PANEL_H + t + 10)
    flower(f, CX + 4, PANEL_H + t + 11)
    flower(f, CX - 1, PANEL_H + t + 13)
    return f.finish()


IRON = ['#0b0c0d', '#16181a', '#222629', '#31373b', '#454d52', '#5f686e', '#838c92']
GOLD = ['#3f2c10', '#6e4f1c', '#a47a2e', '#d2a650', '#f0d48a']
EMBER = ['#3a150a', '#6b2710', '#a3421a', '#d9692a', '#ffa850']


def bolt(f, x, y):
    """A square gold bolt head."""
    for j, row in enumerate(((4, 3, 3), (3, 3, 2), (3, 2, 1))):
        for i, tone in enumerate(row):
            f.put(x + i, y + j, GOLD[tone])
    for k in range(1, 4):
        f.put(x + 3, y + k, 0)
        f.put(x + k, y + 3, 0)


def chain(f, x, y, links):
    """Links hanging from (x, y): face-on rings and edge-on links in turn."""
    for i in range(links):
        if i % 2 == 0:
            f.put(x, y, 6)
            f.put(x, y + 4, 3)
            for k in range(1, 4):
                f.put(x - 1, y + k, 5)
                f.put(x + 1, y + k, 3)
        else:
            for k in range(5):
                f.put(x, y + k, 5 if k < 2 else 4)
        y += 4
    return y


def iron(sigil_name, accent):
    """Smithing Table: hammered iron bands with gold bolts, bolted corner plates hung with chains, a cog round the anvil
    and the forge's heat glowing on the lower edge."""
    f, t = Frame(IRON, 'iron'), 6
    profile = [5, 4, 3, 3, 3, 2, 2, 1]
    cells = []
    for x, y, side, d in band(t):
        f.put(x, y, shade(profile, side, d))
        if 0 < d < t + LIP - 1:
            cells.append((x, y))
    for _ in range(len(cells) // 14):
        x, y = f.rng.choice(cells)
        f.shift(x, y, -1)
        f.shift(x - 1, y - 1, 1)
    # the forge's heat on the lower edge
    for x, y, side, d in band(t):
        if side == 'bottom' and d <= 2:
            heat = (0.55 - d * 0.18) * (0.7 + 0.3 * math.sin(x * 0.37) * math.sin(x * 0.11))
            f.put(x, y, mix(f.get(x, y), EMBER[2] if d == 0 else EMBER[1], max(0, heat)))
    # gold bolts along every band
    b = (-t + LIP - 1) // 2 - 1
    for s in range(34, PANEL_W // 2 - 20, 30):
        for x, y in spots([(s, b)], 3, 3):
            bolt(f, x, y)
    for s in range(34, RAIL - 10, 30):
        for x, y in spots([(b, s)], 3, 3, fy=False):
            bolt(f, x, y)
    # corner plates with a bolt at each corner, chains hanging from the top ones
    q = 6
    for piece in corners(rect(-t - 2, -t - 2, q, q)):
        f.bevel(piece, 3, 5, 1)
    for x, y in spots([(-t - 1, -t - 1), (q - 3, -t - 1), (-t - 1, q - 3), (q - 3, q - 3)], 3, 3):
        bolt(f, x, y)
    for x in (-t - 4, PANEL_W + t + 3):
        end = chain(f, x, q + 1, 7)
        for dx, dy, c in ((-1, 1, 3), (1, 1, 3), (-1, 2, 2), (1, 2, 2), (0, 3, 1), (0, 0, 4)):
            f.put(x + dx, end + dy, GOLD[c])
    # the crossbar, bolted through the sides
    for x in range(0, PANEL_W):
        for k, tone in enumerate((5, 3, 2, 1)):
            f.put(x, RAIL + k, tone)
    for piece in ends(rect(-t - 2, RAIL - 3, -1, RAIL + 6) | rect(0, RAIL, 6, RAIL + 4)):
        f.bevel(piece, 3, 5, 1)
    for x, y in spots([(-t, RAIL), (2, RAIL + 1)], 3, 3, fy=False):
        bolt(f, x, y)
    # straps either side of the medallion; the medallion: an iron cog with a gold ring round the anvil
    for piece in ends(rect(CX - 25, -t - 3, CX - 15, LIP + 1)):
        f.bevel(piece, 3, 5, 1)
    for x in (CX - 22, CX + 19):
        bolt(f, x, -4)

    def ring(f, x, y, dx, dy, d):
        fc = facing(dx, dy)
        a = (math.atan2(dy, dx) / (2 * math.pi) * 12) % 1
        if d <= 10.9:
            f.put(x, y, GOLD[3 if fc > 0.2 else 2 if fc > -0.4 else 1])
        elif d <= 12 or a < 0.5:
            f.put(x, y, 5 if fc > 0.5 else 4 if fc > -0.1 else 3 if fc > -0.6 else 2)
    seal(f, ring, '#0e0f10', sigil_name, r=14, hole=9.8)
    # a bolted plate under the bottom band
    plate = rect(CX - 14, PANEL_H - 3, CX + 13, PANEL_H + t + 3)
    f.bevel(plate, 3, 5, 1)
    for x, y in plate:
        if y >= PANEL_H + t + 1:
            f.put(x, y, mix(f.get(x, y), EMBER[2], 0.35))
    for x in (CX - 11, CX + 8):
        bolt(f, x, PANEL_H + 1)
    gem(f, CX - 2, PANEL_H + 1, accent)
    return f.finish()


PAINTERS = {'steel': steel, 'wood': wood, 'stone': stone, 'vine': vine, 'iron': iron}


def frame(style_name, sigil_name, accent):
    return PAINTERS[style_name](sigil_name, accent)


def build():
    OUT.mkdir(parents=True, exist_ok=True)
    for file in sorted(DESIGN.glob('*.json')):
        data = json.loads(file.read_text(encoding='utf-8'))
        crest = data.get('crest')
        if not crest:
            continue
        frame(crest['frame'], crest['sigil'], data.get('accent', '#e2763f')).save(OUT / f'{file.stem}.png')
        sigil(crest['sigil']).resize((32, 32), Image.Resampling.NEAREST).save(OUT / f'{file.stem}_sigil.png')
        print('frame', file.stem, crest)


if __name__ == '__main__':
    build()
