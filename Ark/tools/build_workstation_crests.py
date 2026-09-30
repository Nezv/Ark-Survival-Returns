"""Frames for the workstation screens (P14): each bench's panel is set into a frame of its material, sigil on top.

Each bench names its frame in its design file ({"crest": {"frame": "steel", "sigil": "helm"}}). The frame is the panel's
edge, not an ornament above it: a band around all four sides that covers the panel's vanilla outline and bevel, corner
pieces, a crossbar between the graph well and the craft bar, the bench's sigil in a medallion set into the top edge and
a smaller piece at the bottom. Riveted steel with leather straps for the Armoury, logs lashed with rope for the Working
Station, cut stone blocks and a millstone for the Mortar & Pestle, a vine-wrapped branch with herbs for the Medicine
Bench, hammered iron with gold bolts, chains and forge heat for the Smithing Table, a ring of fire-blackened
fieldstones with a spit on forked sticks and a heap of embers for the Campfire, dressed blocks of every stone it cuts
round a carved saw ring for the Stonecutter, smoke-blackened fired brick with a glowing iron bar, bellows and a
tapping arch for the Primitive Forge, and the Mechanical Press's dark wooden frame, riveted casing and copper drive
gear. Tones sit a step darker than the materials' items so the frame belongs to the screen's dark palette; light
comes from the top left, like the GUI.
Sigils are 16x16 pixel art, like an item.

Every bench also gets a baroque frame, the same for all of them but for the sigil in its cartouche: a carved moulding
with scrolls, shells and acanthus in the panel's own dark tones, shaded from a height field. It needs more room than the
material frames, so its panel sits at (B_MARGIN, B_TOP) with B_BOTTOM units below (<bench>_baroque.png); the showcase
switches between the two, or no frame, from its controls.

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

    # Three logs in a tipi, a flame rising out of them, a ring of stones.
    'campfire': (["................",
                  "........Y.......",
                  ".......YO.......",
                  "......YOY...Y...",
                  "......OWYO..O...",
                  ".....OYWWYOOY...",
                  ".....OYWWYYO....",
                  "....ROYYWYYOR...",
                  "....RLOYYYOLR...",
                  "...LLlLOOOLlLL..",
                  "..LllLlLLLlLllL.",
                  "..BLllLlllLllB..",
                  ".SSBBLLllLLBBSS.",
                  ".SsSSBBBBBBSSsS.",
                  "..SSs.SSSS.sSS..",
                  "................"],
                 {'Y': '#ffd35a', 'O': '#ff8a3a', 'W': '#fff4c8', 'R': '#c8402a', 'L': '#8a5a34', 'l': '#5a3a20',
                  'B': '#3a2618', 'S': '#8f8f86', 's': '#5d5d56'}),
    # A clay bloomery with its glowing mouth and a curl of smoke.
    'bloomery': (["..........ss....",
                  ".........s..s...",
                  "..........ss....",
                  "......CCCC......",
                  "......CccC......",
                  ".....CCccCC.....",
                  ".....CcCCcC.....",
                  "....CCcCCccC....",
                  "....CcCCCCcC....",
                  "...CCCOYYOCCC...",
                  "...CcCYWWYCcC...",
                  "..CCcCOYYOCcCC..",
                  "..CcCCROORCCcC..",
                  ".KKKKKKKKKKKKKK.",
                  ".KkkKkkkKkkkKkK.",
                  "................"],
                 {'C': '#b8714a', 'c': '#7a3f27', 'O': '#ff8a3a', 'Y': '#ffd35a', 'W': '#fff4c8', 'R': '#c8402a',
                  'K': '#6c6e67', 'k': '#3c3e3c', 's': '#8a8f86'}),
    # A screw press: posts, a beam with the screw and its bar, the platen on a stack of paper.
    'press': (["................",
               "..HHHHHHHHHHHH..",
               ".......SS.......",
               ".PPWWWWSSWWWWPP.",
               ".PwwwwwSSwwwwwP.",
               ".Pp....ss....pP.",
               ".Pp....SS....pP.",
               ".Pp..LLLLLL..pP.",
               ".Pp..llllll..pP.",
               ".Pp..QQQQQQ..pP.",
               ".Pp..qqqqqq..pP.",
               ".Pp..QQQQQQ..pP.",
               ".PBBBBBBBBBBBBP.",
               ".PbbbbbbbbbbbbP.",
               ".PP..........PP.",
               "................"],
              {'H': '#cf7a44', 'S': '#c3ccd2', 's': '#8b959c', 'P': '#8f5037', 'p': '#5c3022', 'W': '#a0643f',
               'w': '#5c3022', 'L': '#9aa3a9', 'l': '#5a6268', 'Q': '#f2ecd6', 'q': '#c9bf9e', 'B': '#474c50',
               'b': '#24272a'}),
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


def saw():
    """A circular saw blade with eight teeth, sunk into a block of cut stone."""
    image = Image.new('RGBA', (16, 16))
    px = image.load()
    cx, cy = 8, 7
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            a = (math.atan2(dy, dx) / (2 * math.pi) * 8) % 1
            if d <= 1.2:
                px[x, y] = rgb('#2a2d30')
            elif d <= 4.6 or (d <= 6.4 and a < 0.42 - (d - 4.6) * 0.2):
                fc = facing(dx, dy)
                px[x, y] = rgb('#eef3f6' if fc > 0.55 and d > 2 else '#c3ccd2' if fc > -0.1 else
                               '#9aa3a9' if fc > -0.6 else '#6f787e')
    for y in range(10, 15):
        for x in range(1, 15):
            edge = y == 10 or x == 1
            px[x, y] = rgb('#b4b7af' if edge else '#4a4d48' if y == 14 or x == 14 else '#8e918a')
    for x in range(5, 11):   # the kerf the blade has cut
        px[x, 10] = rgb('#2a2d30')
    for x, y in ((4, 12), (9, 12), (11, 13), (6, 13)):
        px[x, y] = rgb('#6c6f68')
    return image


def sigil(name):
    if name == 'hammer':
        return outline(hammer())
    if name == 'saw':
        return outline(saw())
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

    def __init__(self, ramp, seed, margin=MARGIN, top=PANEL_TOP, bottom=PANEL_BOTTOM):
        self.margin, self.top = margin, top
        self.w, self.h = PANEL_W + 2 * margin, top + PANEL_H + bottom
        self.image = Image.new('RGBA', (self.w, self.h))
        self.px = self.image.load()
        self.ramp = [rgb(c) for c in ramp]
        self.tones = {}
        self.rng = random.Random(seed)

    def put(self, x, y, c):
        ix, iy = x + self.margin, y + self.top
        if not (0 <= ix < self.w and 0 <= iy < self.h):
            return
        if isinstance(c, int):
            c = max(0, min(len(self.ramp) - 1, c))
            self.tones[x, y] = c
            self.px[ix, iy] = self.ramp[c]
        else:
            self.tones.pop((x, y), None)
            self.px[ix, iy] = rgb(c) if isinstance(c, str) else c

    def get(self, x, y):
        ix, iy = x + self.margin, y + self.top
        return self.px[ix, iy] if 0 <= ix < self.w and 0 <= iy < self.h else (0, 0, 0, 0)

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
        for y in range(2, self.h):
            for x in range(1, self.w):
                inside = self.margin <= x < self.margin + PANEL_W and self.top <= y < self.top + PANEL_H
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
    f.image.alpha_composite(sigil(sigil_name), (CX - 8 + f.margin, cy - 8 + f.top))


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


# ------------------------------------------------------------------------ the fire, the saw, the forge, the press

COPPER = ['#3a1a0a', '#6e3416', '#a4542a', '#cf7a44', '#eba36c']


def lump(f, cells, face, light, dark, ramp=None):
    """A rounded stone: its pixels shaded as a dome lit from the top left."""
    xs, ys = [x for x, _ in cells], [y for _, y in cells]
    cx, cy = (min(xs) + max(xs) + 1) / 2, (min(ys) + max(ys) + 1) / 2
    rx, ry = (max(xs) - min(xs) + 1) / 2, (max(ys) - min(ys) + 1) / 2
    for x, y in cells:
        dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
        fc = facing(dx, dy) * min(1, math.hypot(dx, dy) * 1.3)
        tone = light if fc > 0.35 else face if fc > -0.3 else dark
        f.put(x, y, tone if ramp is None else ramp[tone])


def cobbles(f, x0, y0, x1, y1, horizontal, short, long_, face=(3, 4)):
    """Rounded fieldstones along a band: each one a dome, dark gaps between."""
    for x, y in rect(x0, y0, x1, y1):
        f.put(x, y, 0)
    a, end = (x0, x1) if horizontal else (y0, y1)
    lo, hi = (y0, y1) if horizontal else (x0, x1)
    while a <= end:
        b = min(end, a + f.rng.randint(short, long_))
        if end - b < short // 2:
            b = end
        last = b if b == end else b - 1
        cells = {(u, v) if horizontal else (v, u) for u in range(a, last + 1) for v in range(lo, hi + 1)
                 if not (u in (a, last) and v in (lo, hi))}
        tone = f.rng.choice(face)
        lump(f, cells, tone, tone + 1, tone - 1)
        a = b + 1


HEARTH = ['#121010', '#211d1a', '#332d28', '#48403a', '#5f564d', '#7a6f63', '#978a7a']
CHAR = ['#0e0a07', '#1d1510', '#2e2219', '#433224', '#5a4431']


def hearth(sigil_name, accent):
    """Campfire: a ring of fire-blackened fieldstones, a spit on forked sticks across the craft bar, embers glowing
    in the gaps of the lower stones and heaped under the bottom, sparks over the top."""
    f, t = Frame(HEARTH, 'hearth'), 7
    q = 7
    cobbles(f, q + 1, -t, PANEL_W - q - 2, LIP - 1, True, 7, 12)
    cobbles(f, q + 1, PANEL_H - LIP, PANEL_W - q - 2, PANEL_H + t - 1, True, 7, 12)
    cobbles(f, -t, q + 1, LIP - 1, PANEL_H - q - 2, False, 7, 12)
    cobbles(f, PANEL_W - LIP, q + 1, PANEL_W + t - 1, PANEL_H - q - 2, False, 7, 12)
    # boulders at the corners
    for piece in corners({(x, y) for x in range(-t - 2, q + 1) for y in range(-t - 2, q + 1)
                          if math.hypot(x - (q - t - 2) / 2, y - (q - t - 2) / 2) <= (q + t + 3) / 2}):
        lump(f, piece, 4, 5, 2)
        for x, y in list(piece):
            if f.rng.random() < 0.1:
                f.shift(x, y, -1)
    # soot: the stones darken toward the fire (inside) and toward the top, where the smoke goes
    for (x, y), tone in list(f.tones.items()):
        inner = min(x, y, PANEL_W - 1 - x, PANEL_H - 1 - y)
        if -2 <= inner <= LIP - 1 and f.rng.random() < 0.55:
            f.shift(x, y, -1)
        if y < -2 and f.rng.random() < 0.25:
            f.shift(x, y, -1)
    # embers in the gaps of the lower half, brighter toward the bottom
    for (x, y), tone in list(f.tones.items()):
        if tone == 0:
            heat = (y - PANEL_H * 0.62) / (PANEL_H * 0.38)
            if heat > 0 and f.rng.random() < heat * 0.55:
                f.put(x, y, EMBER[2] if f.rng.random() < 0.6 else EMBER[1])
                if heat > 0.85 and f.rng.random() < 0.3:
                    f.put(x, y, EMBER[3])
    # the spit: a charred stick across the craft bar, resting in a forked stick at each side
    for side in (1, -1):
        base = -t - 6 if side == 1 else PANEL_W + t + 3
        for y in range(RAIL + 2, RAIL + 20):
            f.put(base, y, CHAR[4])
            f.put(base + 1, y, CHAR[3])
            f.put(base + 2, y, CHAR[1])
        for k in range(1, 6):   # the fork's two tines
            f.put(base - (k + 1) // 2, RAIL + 2 - k, CHAR[4])
            f.put(base + 1 - (k + 1) // 2, RAIL + 2 - k, CHAR[2])
            f.put(base + 2 + (k + 1) // 2, RAIL + 2 - k, CHAR[3])
            f.put(base + 3 + (k + 1) // 2, RAIL + 2 - k, CHAR[1])
    for x in range(-t - 9, PANEL_W + t + 8):
        for k, tone in enumerate((4, 3, 2, 1)):
            f.put(x, RAIL - 1 + k, CHAR[tone])
        if f.rng.random() < 0.2:
            f.put(x, RAIL + f.rng.randint(0, 1), CHAR[1])
    for x in (-t - 9, PANEL_W + t + 7):   # the cut ends of the stick
        for k, c in enumerate(('#6b5238', '#8a6c4a', '#6b5238', '#4a3826')):
            f.put(x, RAIL - 1 + k, c)
    for x in range(CX - 70, CX + 70):
        if f.rng.random() < 0.09:
            f.put(x, RAIL + f.rng.randint(0, 1), EMBER[3] if abs(x - CX) < 36 else EMBER[2])
    # the medallion: a ring of small stones round the fire's sigil, warmed on their inner faces
    def ring(f, x, y, dx, dy, d):
        a = (math.atan2(dy, dx) / (2 * math.pi) * 11) % 1
        fc = facing(dx, dy)
        if d <= 10.4:
            f.put(x, y, EMBER[1] if d > 9.4 else '#130c09')
        elif a < 0.12:
            f.put(x, y, 0)
        else:
            f.put(x, y, 5 if fc > 0.45 else 4 if fc > -0.2 else 3 if d > 11.5 else 2)
            if d < 11.3 and fc < 0:
                f.put(x, y, mix(f.get(x, y), EMBER[3], 0.45))
    seal(f, ring, '#130c09', sigil_name, r=14, hole=10.4)
    # embers heaped under the bottom stones, a few flames licking up the front
    heap = set()
    for y in range(PANEL_H + t - 2, PANEL_H + t + 10):
        half = 24 - max(0, y - (PANEL_H + t)) * 2.4
        heap |= {(x, y) for x in range(int(CX - half), int(CX + half))}
    for x, y in heap:
        r, depth = f.rng.random(), abs(x + 0.5 - CX) / 24 + (y - PANEL_H - t) / 14
        if depth < 0.45:
            f.put(x, y, EMBER[4] if r < 0.5 else EMBER[3])
        elif depth < 0.8:
            f.put(x, y, EMBER[3] if r < 0.45 else EMBER[2])
        else:
            f.put(x, y, EMBER[2] if r < 0.4 else EMBER[1] if r < 0.8 else CHAR[1])
    for x in range(CX - 14, CX + 14, 5):   # charred ends sticking out of the embers
        for k in range(3):
            f.put(x + k, PANEL_H + t + 3 + (k + x) % 2, CHAR[3])
    for x, h in ((CX - 11, 4), (CX - 6, 7), (CX - 1, 9), (CX + 4, 7), (CX + 9, 5), (CX + 13, 3)):
        for k in range(h):   # flames licking up over the bottom stones
            y = PANEL_H + t - 2 - k
            f.put(x, y, EMBER[4] if k < h * 0.6 else EMBER[3])
            if k < h - 2:
                f.put(x + 1, y, EMBER[3] if k < h * 0.5 else EMBER[2])
    # sparks drifting over the top
    for x, y in ((CX - 30, -12), (CX - 22, -16), (CX + 25, -14), (CX + 33, -10), (CX - 44, -9), (CX + 47, -12)):
        f.put(x, y, EMBER[4])
    return f.finish()


ASHLAR = {'stone': ['#1e1f1f', '#353634', '#4e4f4b', '#686964', '#838480', '#a2a39d', '#c1c2bb'],
          'deepslate': ['#151519', '#24242a', '#34343c', '#46464f', '#5a5a64', '#72727c', '#8c8c96'],
          'granite': ['#231613', '#3d2721', '#5a3a30', '#7a5243', '#966a58', '#b0846f', '#c9a08b'],
          'sandstone': ['#2a2418', '#4a4029', '#6e5f3d', '#8f7d53', '#ad9a6c', '#c9b889', '#e0d2a6'],
          'tuff': ['#1a1c19', '#2e312c', '#434740', '#595e55', '#70766b', '#8a9085', '#a4aa9e']}


def mason(sigil_name, accent):
    """Stonecutter: one course of dressed blocks, every stone kind it cuts, each drafted at its margin and tooled
    with chisel lines; carved quoins, a lintel over the craft bar, a carved ring round the saw, stepped stone below."""
    f, t = Frame(ASHLAR['stone'], 'mason'), 7
    q = 7
    kinds = list(ASHLAR)

    def course(x0, y0, x1, y1, horizontal):
        for x, y in rect(x0, y0, x1, y1):
            f.put(x, y, '#141514')
        a, end = (x0, x1) if horizontal else (y0, y1)
        while a <= end:
            b = min(end, a + f.rng.randint(16, 28))
            if end - b < 8:
                b = end
            block = rect(a, y0, b - 1 if b < end else b, y1) if horizontal else rect(x0, a, x1, b - 1 if b < end else b)
            ramp = ASHLAR[f.rng.choice(kinds)]
            dress(block, ramp)
            a = b + 1

    def dress(block, ramp):
        f.bevel(block, 4, 5, 2, ramp=ramp)
        xs, ys = [x for x, _ in block], [y for _, y in block]
        x0, y0, x1, y1 = min(xs) + 1, min(ys) + 1, max(xs) - 1, max(ys) - 1
        slant = f.rng.choice((1, -1))
        for x, y in block:
            inside = x0 < x < x1 and y0 < y < y1
            if inside and (x * slant + y) % 3 == 0 and f.rng.random() < 0.55:
                f.put(x, y, ramp[3])   # chisel tooling
            elif inside and f.rng.random() < 0.08:
                f.put(x, y, ramp[5])
    course(q + 1, -t, PANEL_W - q - 2, LIP - 1, True)
    course(q + 1, PANEL_H - LIP, PANEL_W - q - 2, PANEL_H + t - 1, True)
    course(-t, q + 1, LIP - 1, PANEL_H - q - 2, False)
    course(PANEL_W - LIP, q + 1, PANEL_W + t - 1, PANEL_H - q - 2, False)
    # quoins with a mason's mark cut in
    for i, piece in enumerate(corners(rect(-t - 2, -t - 2, q, q))):
        dress(piece, ASHLAR['stone'])
        xs, ys = [x for x, _ in piece], [y for _, y in piece]
        cx, cy = (min(xs) + max(xs)) // 2, (min(ys) + max(ys)) // 2
        for k in range(-3, 4):
            f.put(cx + k, cy, ASHLAR['stone'][1])
            f.put(cx, cy + k, ASHLAR['stone'][1])
        for k in (-3, 3):
            f.put(cx + k, cy - 1, ASHLAR['stone'][1])
            f.put(cx - 1, cy + k, ASHLAR['stone'][1])
    # the lintel over the craft bar, its ends set into the side courses
    for x, y in rect(LIP, RAIL - 1, PANEL_W - LIP - 1, RAIL + 3):
        f.put(x, y, ASHLAR['stone'][(5, 4, 4, 3, 1)[y - RAIL + 1]])
    for x in range(LIP, PANEL_W - LIP):
        if f.rng.random() < 0.3:
            f.put(x, RAIL + f.rng.randint(0, 1), ASHLAR['stone'][3])
    for piece in ends(rect(-t - 2, RAIL - 3, -1, RAIL + 6)):
        dress(piece, ASHLAR['stone'])
    # stone dust along the foot of the frame
    for _ in range(26):
        x = f.rng.randint(-t, PANEL_W + t - 1)
        f.put(x, PANEL_H + t, ASHLAR['stone'][5])

    def ring(f, x, y, dx, dy, d):
        fc = facing(dx, dy)
        a = (math.atan2(dy, dx) / (2 * math.pi) * 8) % 1
        tone = 5 if fc > 0.5 else 4 if fc > -0.1 else 3 if fc > -0.6 else 2
        if 11.2 < d < 12.2:
            tone = 2 if fc > 0 else 5      # the carved groove
        elif d > 13 and a < 0.08:
            tone = 1                       # notches round the rim
        f.put(x, y, ASHLAR['stone'][tone])
    seal(f, ring, '#101111', sigil_name, r=14, hole=10)
    # stepped stone under the bottom course: the shapes this bench cuts
    steps = set()
    for i, half in enumerate((22, 16, 10)):
        y0 = PANEL_H + t - 1 + i * 4
        steps |= rect(CX - half, y0, CX + half - 1, y0 + 3)
    dress(steps, ASHLAR['sandstone'])
    for i, half in enumerate((22, 16, 10)):
        for x in range(CX - half, CX + half):
            f.put(x, PANEL_H + t - 1 + i * 4, ASHLAR['sandstone'][5])
        f.put(CX - half, PANEL_H + t - 1 + i * 4 + 3, ASHLAR['sandstone'][2])
    return f.finish()


CLAY = ['#1a0d08', '#31170f', '#4b2317', '#673221', '#83432c', '#9e5a3b', '#b8744f']
SOOT = '#141110'


def bloomery(sigil_name, accent):
    """Primitive Forge: courses of fired clay brick, blackened by smoke toward the top and glowing with heat below,
    stone footings at the corners, a hot iron bar across the craft bar with bellows at its ends, the round mouth of the
    furnace round the sigil and a glowing tapping arch under the bottom."""
    f, t = Frame(CLAY, 'bloomery'), 7

    def bricks(x0, y0, x1, y1, horizontal):
        for x, y in rect(x0, y0, x1, y1):
            f.put(x, y, 1)
        rows = range(y0, y1 + 1) if horizontal else range(x0, x1 + 1)
        span = (x0, x1) if horizontal else (y0, y1)
        size = 4
        for r, row in enumerate(range(rows.start, rows.stop, size)):
            offset = 0 if r % 2 == 0 else 7
            a = span[0] - offset
            while a <= span[1]:
                b = a + 13
                cells = set()
                for u in range(max(a, span[0]), min(b, span[1] + 1)):
                    for v in range(row, min(row + size - 1, rows.stop)):
                        cells.add((u, v) if horizontal else (v, u))
                if cells:
                    face = f.rng.choice((3, 3, 4))
                    f.bevel(cells, face, face + 1, face - 1)
                    for x, y in cells:
                        if f.rng.random() < 0.1:
                            f.shift(x, y, -1)
                a = b + 1
    q = 6
    bricks(q + 1, -t, PANEL_W - q - 2, LIP - 1, True)
    bricks(q + 1, PANEL_H - LIP, PANEL_W - q - 2, PANEL_H + t - 1, True)
    bricks(-t, q + 1, LIP - 1, PANEL_H - q - 2, False)
    bricks(PANEL_W - LIP, q + 1, PANEL_W + t - 1, PANEL_H - q - 2, False)
    # smoke blackens the upper bricks; heat reddens the mortar of the lower ones
    for (x, y), tone in list(f.tones.items()):
        up = 1 - (y + t) / (PANEL_H * 0.7)
        if up > 0:
            f.put(x, y, mix(f.get(x, y), SOOT, min(0.7, up * 0.75 + (0.15 if f.rng.random() < 0.2 else 0))))
        down = (y - PANEL_H * 0.6) / (PANEL_H * 0.4)
        if tone == 1 and down > 0 and f.rng.random() < down * 0.8:
            f.put(x, y, EMBER[2] if down > 0.75 else EMBER[1])
    # stone footings at the corners
    for piece in corners(rect(-t - 2, -t - 2, q, q)):
        f.bevel(piece, 4, 5, 2, ramp=STONE)
        for x, y in piece:
            if f.rng.random() < 0.14:
                f.put(x, y, STONE[f.rng.choice((2, 3, 5))])
    # the iron bar across the craft bar, glowing where it crosses the fire
    for x in range(-t - 3, PANEL_W + t + 3):
        heat = max(0.0, 1 - abs(x + 0.5 - CX) / (PANEL_W * 0.42))
        for k, tone in enumerate((5, 4, 3, 1)):
            base = IRON[tone]
            f.put(x, RAIL - 1 + k, mix(base, EMBER[4] if k < 2 else EMBER[2], heat * (0.9 if k < 3 else 0.6)))
    for x in (-t - 1, PANEL_W + t - 1):
        rivet(f, x, RAIL, IRON[6], IRON[4], IRON[3], IRON[0])
    # bellows hanging under both ends of the bar: the nozzle up into it, leather folds between two boards
    for cx in (-t - 5, PANEL_W + t + 4):
        for k in range(2):   # the nozzle
            f.put(cx, RAIL + 3 + k, IRON[5])
            f.put(cx + 1, RAIL + 3 + k, IRON[3])
        for k in range(13):
            y = RAIL + 5 + k
            half = min(5, 1 + k // 2)
            for x in range(cx - half, cx + half + 2):
                edge = x in (cx - half, cx + half + 1)
                f.put(x, y, WOOD[5] if edge and x < cx else WOOD[3] if edge else
                      LEATHER[4] if k % 3 == 1 else LEATHER[3] if x <= cx else LEATHER[2])
        for x in range(cx - 6, cx + 8):
            f.put(x, RAIL + 18, WOOD[5] if x < cx else WOOD[4])
            f.put(x, RAIL + 19, WOOD[2])
        for k in range(3):   # the handle
            f.put(cx, RAIL + 20 + k, WOOD[4])
            f.put(cx + 1, RAIL + 20 + k, WOOD[2])
    # the furnace mouth: a thick fired-clay collar, the fire showing at its inner lip
    def ring(f, x, y, dx, dy, d):
        fc = facing(dx, dy)
        a = (math.atan2(dy, dx) / (2 * math.pi) * 14) % 1
        if d <= 10.3:
            f.put(x, y, EMBER[2] if d > 9.5 else '#140a07')
        elif a < 0.1:
            f.put(x, y, 1)
        else:
            f.put(x, y, 5 if fc > 0.45 else 4 if fc > -0.2 else 3 if fc > -0.6 else 2)
            if d < 11.6:
                f.put(x, y, mix(f.get(x, y), EMBER[3], 0.5))
    seal(f, ring, '#140a07', sigil_name, r=14, hole=10.3)
    # the tapping arch under the bottom course, fire inside it
    arch = set()
    for y in range(PANEL_H + t - 3, PANEL_H + t + 8):
        for x in range(CX - 13, CX + 13):
            if math.hypot(x + 0.5 - CX, max(0, PANEL_H + t + 3 - y)) <= 13:
                arch.add((x, y))
    f.bevel(arch, 4, 5, 2)
    for x, y in arch:
        dx, dy = x + 0.5 - CX, max(0, PANEL_H + t + 3 - y)
        if math.hypot(dx, dy) <= 8.5 and y > PANEL_H + t - 2:
            glow = 1 - math.hypot(dx, dy * 1.4) / 9
            f.put(x, y, EMBER[4] if glow > 0.55 else EMBER[3] if glow > 0.3 else EMBER[2] if glow > 0.05 else EMBER[1])
    return f.finish()


PRESSWOOD = ['#170b07', '#2a150e', '#401f15', '#572a1d', '#703726', '#8a4632', '#a35a42']
CASING = ['#141617', '#222527', '#313538', '#43484c', '#575d61', '#70777c', '#8d959a']
VERDIGRIS = ['#2f6b58', '#4f9a80', '#79c2a4']


def press(sigil_name, accent):
    """Mechanical Press: the machine's dark wooden frame round riveted steel casing plates, capped corners, the steel
    press bed across the craft bar with a copper gear at each end, the copper drive gear (verdigris in its teeth)
    round the sigil, and the maker's plate under the bottom."""
    f, t = Frame(PRESSWOOD, 'press'), 7
    outer = 4
    for x, y, side, d in band(t):
        if d < outer:
            f.put(x, y, shade([5, 4, 3, 2], side, d))
        else:
            f.put(x, y, CASING[shade([4, 3, 3, 2, 1], side, d - outer)])
    # grain in the beams, seams and rivets in the casing
    for (x, y), tone in list(f.tones.items()):
        if f.rng.random() < 0.12:
            f.shift(x, y, -1)
    for s in range(34, PANEL_W - 30, 42):
        for x in (s, PANEL_W - 1 - s):
            for y0 in (-t + outer, PANEL_H - LIP):
                for k in range(0, t + LIP - outer):
                    f.put(x, y0 + k, CASING[0])
            for y in (-t + outer + 1, PANEL_H + t - outer - 2):
                f.put(x - 2, y, STEEL[6])
                f.put(x + 2, y, STEEL[6])
    for s in range(34, PANEL_H - 30, 40):
        for y in (s, PANEL_H - 1 - s):
            for x0 in (-t + outer, PANEL_W - LIP):
                for k in range(0, t + LIP - outer):
                    f.put(x0 + k, y, CASING[0])
    # capped corner posts
    for piece in corners(rect(-t - 2, -t - 2, 5, 5)):
        f.bevel(piece, 4, 5, 2)
    for x, y in spots([(-t - 1, -t - 1)], 3, 3):
        for i, j, c in ((0, 0, 6), (1, 0, 5), (0, 1, 5), (1, 1, 4), (2, 1, 2), (1, 2, 2), (2, 2, 1)):
            f.put(x + i, y + j, STEEL[c])
    # the press bed across the craft bar
    for x in range(LIP, PANEL_W - LIP):
        for k, tone in enumerate((5, 4, 2, 0)):
            f.put(x, RAIL + k, CASING[tone])
        if x % 12 == 6:
            f.put(x, RAIL + 1, STEEL[6])
            f.put(x + 1, RAIL + 2, CASING[0])
    # copper gears at both ends of the bed, half set into the frame
    def gear(cx, cy, r, teeth, spin=0.0):
        for y in range(cy - r - 2, cy + r + 3):
            for x in range(cx - r - 2, cx + r + 3):
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                d = math.hypot(dx, dy)
                a = (math.atan2(dy, dx) / (2 * math.pi) * teeth + spin) % 1
                fc = facing(dx, dy)
                if d <= 1.6:
                    f.put(x, y, CASING[1])
                elif d <= r * 0.45:
                    f.put(x, y, COPPER[3] if fc > 0 else COPPER[1])
                elif d <= r or (d <= r + 2 and a < 0.5):
                    tone = 4 if fc > 0.5 else 3 if fc > -0.1 else 2 if fc > -0.6 else 1
                    f.put(x, y, COPPER[tone])
                    if a < 0.5 and d > r - 1 and f.rng.random() < 0.25:
                        f.put(x, y, VERDIGRIS[f.rng.randint(0, 2)])
    for cx in (-t // 2 - 1, PANEL_W + t // 2):
        gear(cx, RAIL + 1, 5, 8)

    def ring(f, x, y, dx, dy, d):
        fc = facing(dx, dy)
        a = (math.atan2(dy, dx) / (2 * math.pi) * 14) % 1
        if d <= 10.4:
            f.put(x, y, CASING[3] if d > 9.6 else '#101112')
        elif d <= 12.2 or a < 0.5:
            tone = 4 if fc > 0.5 else 3 if fc > -0.1 else 2 if fc > -0.6 else 1
            f.put(x, y, COPPER[tone])
            if d > 12.2 and f.rng.random() < 0.35:
                f.put(x, y, VERDIGRIS[1 if fc > 0 else 0])
    seal(f, ring, '#101112', sigil_name, r=14, hole=10.4)
    # the maker's plate: a riveted cream plaque with its red mark
    plate = rect(CX - 8, PANEL_H + t - 2, CX + 7, PANEL_H + t + 8)
    f.bevel(plate, 4, 5, 2, ramp=CASING)
    for x, y in rect(CX - 6, PANEL_H + t, CX + 5, PANEL_H + t + 6):
        f.put(x, y, '#d9ceb0' if (x + y) % 5 else '#bfb393')
    for i, j in ((-2, 1), (-1, 1), (0, 1), (1, 1), (-2, 2), (-2, 3), (-1, 3), (0, 3), (-2, 4), (-2, 5), (-1, 5), (0, 5), (1, 5)):
        f.put(CX + i, PANEL_H + t + j - 1, '#a02820')
    for x in (CX - 7, CX + 6):
        f.put(x, PANEL_H + t - 1, STEEL[6])
        f.put(x, PANEL_H + t + 7, STEEL[5])
    return f.finish()


# ---------------------------------------------------------------------------------------------- baroque

# The panel's own tones (graph_style palette: outline, bevel dark, well, face, button, bevel light, button light, faint,
# muted) with steps between them, dark to light.
BAROQUE = ['#050604', '#0b0c0a', '#0f110e', '#151813', '#1a1d18', '#242820', '#30352b', '#3a4034', '#4c5445', '#5d6258',
           '#747970', '#8a8f86']
LIGHT = tuple(v / math.sqrt(1 + 1 + 1.8 ** 2) for v in (-1, -1, 1.8))


def sample(path, step=0.25):
    """A polyline resampled every `step` units."""
    out = []
    for (ax, ay), (bx, by) in zip(path, path[1:]):
        n = max(1, int(math.hypot(bx - ax, by - ay) / step))
        out += [(ax + (bx - ax) * i / n, ay + (by - ay) * i / n) for i in range(n)]
    return out + [path[-1]]


def curve(p0, p1, p2, p3, n=32):
    """A cubic Bezier as a polyline."""
    pts = []
    for i in range(n + 1):
        t = i / n
        a, b, c, d = (1 - t) ** 3, 3 * (1 - t) ** 2 * t, 3 * (1 - t) * t * t, t ** 3
        pts.append((a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1]))
    return pts


def spiral(cx, cy, r0, r1, a0, a1, n=64):
    """A spiral round (cx, cy) from angle a0 at radius r0 to a1 at r1 (degrees; 90 points down, as on screen)."""
    return [(cx + math.cos(math.radians(a0 + (a1 - a0) * i / n)) * (r0 + (r1 - r0) * i / n),
             cy + math.sin(math.radians(a0 + (a1 - a0) * i / n)) * (r0 + (r1 - r0) * i / n)) for i in range(n + 1)]


class Relief:
    """A height field in panel units. Ornaments are tubes, domes and mouldings unioned by height, then shaded from the
    top-left light in the panel's own tones, so the whole frame reads as carved from one dark material."""

    def __init__(self):
        self.h = {}

    def lift(self, x, y, v):
        if v > self.h.get((x, y), 0):
            self.h[x, y] = v

    def tube(self, path, r0, r1=None, base=0.0, flat=1.0):
        """A round moulding along a path, tapering from radius r0 to r1 (or r0(u) along it, u from 0 to 1)."""
        r1 = r0 if r1 is None else r1
        pts = sample(path)
        for i, (px, py) in enumerate(pts):
            u = i / max(1, len(pts) - 1)
            r = r0(u) if callable(r0) else r0 + (r1 - r0) * u
            for y in range(math.floor(py - r), math.ceil(py + r) + 1):
                for x in range(math.floor(px - r), math.ceil(px + r) + 1):
                    d2 = (x + 0.5 - px) ** 2 + (y + 0.5 - py) ** 2
                    if d2 < r * r:
                        self.lift(x, y, base + flat * math.sqrt(r * r - d2))

    def dome(self, cx, cy, rx, ry, height, base=0.0):
        for y in range(math.floor(cy - ry), math.ceil(cy + ry) + 1):
            for x in range(math.floor(cx - rx), math.ceil(cx + rx) + 1):
                q = 1 - ((x + 0.5 - cx) / rx) ** 2 - ((y + 0.5 - cy) / ry) ** 2
                if q > 0:
                    self.lift(x, y, base + height * math.sqrt(q))

    def carve(self, path, depth, width=0.55):
        """Cuts a groove (a leaf's rib, a scroll's channel) into what is already there."""
        cut = set()
        for px, py in sample(path, 0.2):
            for y in (math.floor(py - 1), math.floor(py), math.floor(py + 1)):
                for x in (math.floor(px - 1), math.floor(px), math.floor(px + 1)):
                    if (x + 0.5 - px) ** 2 + (y + 0.5 - py) ** 2 < width * width and (x, y) in self.h:
                        cut.add((x, y))
        for p in cut:
            self.h[p] = max(0.15, self.h[p] - depth)

    def scroll(self, path, r0, r1, eye=None):
        """A C-scroll: a tapering moulding with a channel down its middle, ending in a round eye."""
        self.tube(path, r0, r1)
        self.carve(path[3:-6], 0.7, 0.5)
        if eye:
            self.dome(eye[0], eye[1], r1 + 0.9, r1 + 0.9, r1 + 1.1)

    def leaf(self, path, r0, lobes=3, side=1):
        """An acanthus leaf: a body swelling from its stem to a point, a midrib groove, and rounded lobes curling off
        its edges, alternating sides (side picks the first)."""
        def width(u):
            return max(0.45, r0 * math.sin(math.pi * (0.22 + 0.78 * u)) ** 0.55)
        pts = sample(path)
        n = len(pts) - 1
        self.tube(path, width, flat=0.5, base=0.5)
        for k in range(lobes):
            u = (k + 0.7) / (lobes + 0.5)
            i = int(n * u)
            (x0, y0), (x1, y1) = pts[max(0, i - 3)], pts[min(n, i + 3)]
            d = math.hypot(x1 - x0, y1 - y0) or 1
            tx, ty = (x1 - x0) / d, (y1 - y0) / d
            sd = side if k % 2 == 0 else -side
            w = width(u)
            px, py = pts[i]
            self.tube([(px, py), (px - ty * sd * w * 0.9 + tx * w * 0.7, py + tx * sd * w * 0.9 + ty * w * 0.7),
                       (px - ty * sd * w * 1.9 + tx * w * 2.0, py + tx * sd * w * 1.9 + ty * w * 2.0)],
                      lambda v, w=w: max(0.45, w * 0.9 * (1 - v) ** 0.6), base=0.4, flat=0.5)
        self.carve(pts[:int(n * 0.8)], 0.7, 0.45)

    def shell(self, hinge, direction, spread, length, ribs, r0=1.2, r1=1.8):
        """A scallop: ribs fanning out of a hinge boss toward `direction` (degrees), a scalloped rim at their ends."""
        hx, hy = hinge
        for k in range(ribs):
            a = math.radians(direction - spread / 2 + spread * k / (ribs - 1))
            end = (hx + math.cos(a) * length, hy + math.sin(a) * length)
            self.tube([hinge, end], r0, r1, base=0.4, flat=0.7)
            self.dome(end[0], end[1], r1 + 0.3, r1 + 0.3, r1 + 0.2, 0.6)
        self.dome(hx, hy, length * 0.28, length * 0.28, length * 0.2 + 1, 0.8)

    def stamp(self, other, fx=False, fy=False):
        """Unions another relief, mirrored across the panel's middle."""
        for (x, y), v in other.h.items():
            self.lift(PANEL_W - 1 - x if fx else x, PANEL_H - 1 - y if fy else y, v)

    def paint(self, f):
        h = self.h
        for (x, y), v in h.items():
            if v < 0.1:
                continue
            gx = (h.get((x + 1, y), 0) - h.get((x - 1, y), 0)) / 2
            gy = (h.get((x, y + 1), 0) - h.get((x, y - 1), 0)) / 2
            n = math.sqrt(gx * gx + gy * gy + 1)
            s = (-gx * LIGHT[0] - gy * LIGHT[1] + LIGHT[2]) / n
            f.put(x, y, int(round(5.6 + (s - LIGHT[2]) * 19 + min(v, 4) * 0.45)))


# The baroque frame is larger than the material ones: its ornament needs the room. The panel's top-left corner sits at
# (B_MARGIN, B_TOP) of the image and B_BOTTOM units hang below it.
B_MARGIN, B_TOP, B_BOTTOM = 26, 33, 30
B_SEAL = -5


def baroque_relief():
    """The frame without its sigil: the same for every bench."""
    r, t = Relief(), 7
    # The moulding: an outer round, a cove, a bead run and a fillet stepping down to the panel.
    profile = [1.2, 2.2, 2.6, 2.3, 1.1, 0.7, 1.5, 1.6, 1.0]
    for x, y, side, d in band(t):
        r.lift(x, y, profile[d])
    for k in range(-t + 6, PANEL_W + t - 6, 4):
        for y0 in (-t + 6.5, PANEL_H + t - 6.5):
            r.dome(k + 0.5, y0, 1.5, 1.2, 1.0, 1.3)
    for k in range(-t + 6, PANEL_H + t - 6, 4):
        for x0 in (-t + 6.5, PANEL_W + t - 6.5):
            r.dome(x0, k + 0.5, 1.2, 1.5, 1.0, 1.3)

    # A corner: a shell fanning out of a rosette where the mouldings meet, a volute running out along each edge and
    # acanthus trailing after it.
    c = Relief()
    c.shell((-6, -6), 225, 104, 15.5, 9, 1.3, 2.2)
    c.dome(-3, -3, 6.5, 6.5, 4.2)
    for k in range(8):
        a = math.radians(22.5 + k * 45)
        c.dome(-3 + math.cos(a) * 4.4, -3 + math.sin(a) * 4.4, 2.0, 2.0, 1.2, 3.2)
    c.dome(-3, -3, 2.0, 2.0, 1.4, 4.0)
    c.scroll([(4, -10), (13, -11.5), (21, -12.3)] + spiral(27.5, -19, 6.7, 1.7, 90, -270)[1:], 3.2, 1.6, eye=(27.5, -19))
    c.leaf(curve((33, -12), (42, -11), (50, -15), (57, -23), 28), 3.4, 3, -1)
    c.leaf(curve((22, -25.5), (16, -28.5), (9, -27), (5, -21), 18), 2.6, 2, 1)
    c.scroll([(-10, 4), (-11.5, 13), (-12, 19)] + spiral(-17.5, 24, 5.5, 1.5, 0, 340)[1:], 2.9, 1.5, eye=(-17.5, 24))
    c.leaf(curve((-12, 31), (-12.5, 41), (-17, 49), (-20, 58), 26), 3.2, 3, 1)
    for fx in (False, True):
        for fy in (False, True):
            r.stamp(c, fx, fy)

    # The sides: a cartouche with a shell fanning outward, volutes above and below it, and a console under each end of
    # the crossbar.
    s = Relief()
    mid = (15 + RAIL) // 2
    s.shell((-9, mid), 180, 116, 12, 7, 1.2, 1.9)
    s.tube(spiral(-11, mid, 4.6, 4.6, 0, 360, 48), 1.5, base=1.2)
    s.dome(-11, mid, 3.0, 3.0, 3.0, 1.2)
    for sy in (-1, 1):
        s.scroll([(-10, mid + sy * 6), (-11, mid + sy * 11)] + spiral(-15.5, mid + sy * 15.5, 4.5, 1.3, 0, 330 * sy)[1:],
                 2.5, 1.3, eye=(-15.5, mid + sy * 15.5))
        s.leaf(curve((-12, mid + sy * 21), (-12.5, mid + sy * 29), (-17, mid + sy * 36), (-19, mid + sy * 45), 24), 3.0, 3, sy)
    s.scroll([(-9, RAIL - 4)] + spiral(-14, RAIL + 2, 4.6, 1.3, -90, -90 - 340)[1:], 2.6, 1.3, eye=(-14, RAIL + 2))
    s.leaf(curve((-12, RAIL + 8), (-13, RAIL + 13), (-12, RAIL + 18), (-13.5, RAIL + 22), 16), 2.1, 3, 1)
    for fx in (False, True):
        r.stamp(s, fx)

    # The crossbar between the well and the craft bar: a beaded rail.
    r.tube([(0, RAIL + 1.8), (PANEL_W, RAIL + 1.8)], 1.9)
    for k in range(4, PANEL_W - 4, 5):
        r.dome(k + 0.5, RAIL + 2, 1.2, 1.2, 0.7, 1.4)

    # The top: a beaded cartouche round the sigil under a shell crest with leaf wings, flanked by volutes whose
    # acanthus tails trail along the moulding. Drawn on the right and mirrored.
    m = Relief()
    m.tube(spiral(CX, B_SEAL, 16.6, 16.6, 0, 360, 160), 1.1, base=0.8)
    m.tube(spiral(CX, B_SEAL, 13.0, 13.0, 0, 360, 140), 3.0)
    for k in range(12):
        a = math.radians(k * 30 + 15)
        m.dome(CX + math.cos(a) * 13.0, B_SEAL + math.sin(a) * 13.0, 1.6, 1.6, 1.3, 2.4)
    m.shell((CX, B_SEAL - 17), 270, 120, 9, 7, 1.0, 1.6)
    m.leaf(curve((CX + 5, B_SEAL - 17), (CX + 11, B_SEAL - 22), (CX + 17, B_SEAL - 21), (CX + 21, B_SEAL - 17), 16), 2.2, 2, -1)
    m.scroll([(CX + 15, B_SEAL + 6), (CX + 19.5, B_SEAL + 1)] + spiral(CX + 25, B_SEAL - 6, 6.6, 1.7, 125, 125 - 340)[1:],
             3.2, 1.6, eye=(CX + 25, B_SEAL - 6))
    m.leaf(curve((CX + 30, -12), (CX + 38, -25), (CX + 52, -27), (CX + 61, -18), 32), 3.6, 4, -1)
    m.scroll([(CX + 61, -18)] + spiral(CX + 63.5, -13.5, 3.8, 1.2, -110, -110 + 320)[1:], 2.0, 1.1, eye=(CX + 63.5, -13.5))
    m.leaf(curve((CX + 66, -10), (CX + 73, -9), (CX + 80, -10.5), (CX + 88, -9.5), 20), 2.3, 3, -1)
    r.stamp(m)
    r.stamp(m, fx=True)

    # The bottom: a scallop shell hanging from the moulding between two volutes, a bead drop under it.
    b = Relief()
    b.shell((CX, PANEL_H + 2), 90, 140, 19, 11, 1.4, 2.4)
    b.dome(CX, PANEL_H + 2, 5.5, 4.0, 3.8, 1.4)
    for sx in (-1, 1):
        b.scroll([(CX + sx * 13, PANEL_H + 5)] +
                 spiral(CX + sx * 26, PANEL_H + 12, 4.8, 1.3, 180 if sx > 0 else 0, (180 + 330) if sx > 0 else -330)[1:],
                 2.6, 1.3, eye=(CX + sx * 26, PANEL_H + 12))
        b.leaf(curve((CX + sx * 31, PANEL_H + 9), (CX + sx * 40, PANEL_H + 20), (CX + sx * 54, PANEL_H + 21), (CX + sx * 64, PANEL_H + 13), 30),
               3.4, 4, 1 if sx > 0 else -1)
    b.dome(CX, PANEL_H + 24, 2.0, 2.6, 2.0, 0.5)
    r.stamp(b)
    return r


def baroque(sigil_name, accent):
    """Any bench: carved dark moulding in the panel's own tones, scrolls, shells and acanthus at the corners and
    sides, a beaded cartouche round the bench's sigil and a shell at the bottom. Light from the top left, like the
    GUI."""
    f = Frame(BAROQUE, 'baroque', B_MARGIN, B_TOP, B_BOTTOM)
    baroque_relief().paint(f)
    seal(f, lambda *a: None, '#0b0c0a', sigil_name, r=11, hole=10.2, cy=B_SEAL)
    return f.finish()


PAINTERS = {'steel': steel, 'wood': wood, 'stone': stone, 'vine': vine, 'iron': iron, 'hearth': hearth,
            'mason': mason, 'bloomery': bloomery, 'press': press}


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
        baroque(crest['sigil'], data.get('accent', '#e2763f')).save(OUT / f'{file.stem}_baroque.png')
        sigil(crest['sigil']).resize((32, 32), Image.Resampling.NEAREST).save(OUT / f'{file.stem}_sigil.png')
        print('frame', file.stem, crest)


if __name__ == '__main__':
    build()
