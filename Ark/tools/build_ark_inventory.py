"""The Ark inventory panel in P16 carved stone (F38): the texture ArkInventoryScreen draws, frame and panel in one.

The panel is a T: a wide top band (the worn gear round a player standing in an arched niche, the inventory's crafting
graph on the right) over a narrower storage block (eleven columns, the side pockets left and right of the nine, over the
hotbar). The bottom corners are cut away; a carved console fills each step under the band. The frame is P14's carving
(build_workstation_crests: the height field, the palette, the top-left light) kept slim so the whole panel fits a
1080p screen at GUI scale 4: a beaded moulding round the T, rosettes at its corners, a beaded rail where band and
storage meet, a pilaster between the gear and the graph, the inventory's pouch sigil in a cartouche on top and a
scallop under the storage.

Numbers must match ArkInventoryLayout.java. Run from Ark/: python tools/build_ark_inventory.py
Writes textures/gui/ark_inventory.png and the review sheet design/ui-rework/ark-inventory.png.
"""
import glob
import io
import json
import math
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw

import build_workstation_crests as stone
from build_storage_ui import Surface
from stone_figures import rosette

ROOT = Path(__file__).resolve().parents[1]
TEXTURE = ROOT / 'src/main/resources/assets/arksurvivalreturns/textures/gui/ark_inventory.png'
PREVIEW = ROOT / 'design/ui-rework/ark-inventory.png'
JAR = ROOT / 'build/moddev/artifacts/minecraft-patched-26.1.2.109-merged.jar'

# ArkInventoryLayout.java. The gear stands in seven rows beside the player's niche, row by row from head to feet: two
# curio columns on the left, the armour column on the right with the charm, offhand and universal curio between pieces.
W, BAND_H, H = 336, 142, 228
SECTION_X0, SECTION_X1 = 62, 274
FRAME_X, FRAME_TOP, FRAME_BOTTOM = 14, 15, 12
ROWS = [8 + 18 * k for k in range(7)]
CURIOS = [((8, ROWS[0]), 'head'), ((26, ROWS[0]), 'head'), ((8, ROWS[1]), 'necklace'), ((26, ROWS[1]), 'back'),
          ((8, ROWS[2]), 'body'), ((26, ROWS[2]), 'hands'), ((8, ROWS[3]), 'bracelet'), ((26, ROWS[3]), 'bracelet'),
          ((8, ROWS[4]), 'belt'), ((26, ROWS[4]), 'ring'), ((8, ROWS[5]), 'legs'), ((26, ROWS[5]), 'ring'),
          ((8, ROWS[6]), 'feet'), ((26, ROWS[6]), 'feet'), ((98, ROWS[1]), 'charm'), ((98, ROWS[4]), 'curio')]
ARMOR = [(98, ROWS[0]), (98, ROWS[2]), (98, ROWS[5]), (98, ROWS[6])]
OFFHAND = (98, ROWS[3])
PLAYER = (44, 7, 96, 135)
CRAFT = (121, 7, 208, 128)
STORAGE_X, STORAGE_Y, HOTBAR_Y = 88, 147, 205
STORAGE = [(STORAGE_X + c * 18, STORAGE_Y + r * 18) for r in range(3) for c in range(9)]
POCKETS = [(STORAGE_X - 18, STORAGE_Y + r * 18) for r in range(3)] + [(STORAGE_X + 9 * 18, STORAGE_Y + r * 18) for r in range(3)]
HOTBAR = [(STORAGE_X + c * 18, HOTBAR_Y) for c in range(9)]
CX = W // 2

FACE, WELL, EDGE = stone.TONES[4], stone.TONES[2], stone.TONES[7]


def inside(x, y):
    return 0 <= x < W and 0 <= y < BAND_H or SECTION_X0 <= x < SECTION_X1 and BAND_H <= y < H


def outside_distance(x, y):
    """Chebyshev distance from a pixel outside the T to the T (1 = touching it)."""
    best = None
    for x0, y0, x1, y1 in ((0, 0, W, BAND_H), (SECTION_X0, BAND_H, SECTION_X1, H)):
        dx = max(x0 - x, 0, x - (x1 - 1))
        dy = max(y0 - y, 0, y - (y1 - 1))
        d = max(dx, dy)
        best = d if best is None else min(best, d)
    return best


# ------------------------------------------------------------------------------------------------ the panel

def slot(d, x, y, well=WELL, rim=EDGE):
    d.rectangle((x - 1, y - 1, x + 16, y + 16), fill=well, outline=stone.TONES[1])
    d.line((x - 1, y + 16, x + 16, y + 16, x + 16, y - 1), fill=rim)


def panel():
    image = Image.new('RGBA', (W, H))
    px = image.load()
    face = stone.rgb(FACE)
    for y in range(H):
        for x in range(W):
            if inside(x, y):
                px[x, y] = face
    d = ImageDraw.Draw(image)
    for (x, y), _ in CURIOS:
        slot(d, x, y)
    for x, y in ARMOR + [OFFHAND] + STORAGE + HOTBAR:
        slot(d, x, y)
    # The side pockets: a shallower well and a lighter rim, so the two added columns read as their own.
    for x, y in POCKETS:
        slot(d, x, y, stone.TONES[3], stone.TONES[8])
    # The player stands in an arched niche.
    x0, y0, x1, y1 = PLAYER
    r = (x1 - x0) / 2
    cx, cy = (x0 + x1 - 1) / 2, y0 + r
    for y in range(y0, y1):
        for x in range(x0, x1):
            if y < cy and math.hypot(x + .5 - (cx + .5), y + .5 - cy) > r:
                continue
            arch = y < cy and math.hypot(x + .5 - (cx + .5), y + .5 - cy) > r - 1
            if x == x0 or (arch and x < cx) or (y == y0):
                c = stone.TONES[0]
            elif x == x1 - 1 or y == y1 - 1 or (arch and x >= cx):
                c = EDGE
            else:
                c = stone.TONES[1]
            px[x, y] = stone.rgb(c)
    # The graph's own panel is drawn by the screen; this well only shows before it loads.
    gx, gy, gw, gh = CRAFT
    d.rectangle((gx, gy, gx + gw - 1, gy + gh - 1), fill=WELL, outline=stone.TONES[0])
    return image


# ------------------------------------------------------------------------------------------------ the frame

def relief():
    r, t, lip = stone.Relief(), 7, 2
    profile = [1.2, 2.2, 2.6, 2.3, 1.1, 0.7, 1.5, 1.6, 1.0]
    for y in range(-t, H + t):
        for x in range(-t, W + t):
            if inside(x, y):
                near = any(not inside(x + dx, y + dy) for dx in range(-lip, lip + 1) for dy in range(-lip, lip + 1))
                if not near:
                    continue
                depth = 0 if any(not inside(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1)) else 1
                r.lift(x, y, profile[t + depth])
            else:
                k = outside_distance(x, y)
                if k <= t:
                    r.lift(x, y, profile[t - k])

    # Beads along every straight run of the moulding, half a unit outside the panel's edge.
    def beads_h(y, xa, xb):
        for k in range(xa, xb, 4):
            r.dome(k + 0.5, y, 1.5, 1.2, 1.0, 1.3)

    def beads_v(x, ya, yb):
        for k in range(ya, yb, 4):
            r.dome(x, k + 0.5, 1.2, 1.5, 1.0, 1.3)
    beads_h(-0.5, 6, W - 6)
    beads_v(-0.5, 6, BAND_H - 6)
    beads_v(W - 0.5, 6, BAND_H - 6)
    beads_h(BAND_H + 0.5, 6, SECTION_X0 - 6)
    beads_h(BAND_H + 0.5, SECTION_X1 + 6, W - 6)
    beads_v(SECTION_X0 - 0.5, BAND_H + 6, H - 6)
    beads_v(SECTION_X1 - 0.5, BAND_H + 6, H - 6)
    beads_h(H + 0.5, SECTION_X0 + 6, SECTION_X1 - 6)

    # Rosettes on the outer corners.
    for cx, cy in ((-3, -3), (W + 2, -3), (-3, BAND_H + 2), (W + 2, BAND_H + 2),
                   (SECTION_X0 - 3, H + 2), (SECTION_X1 + 2, H + 2)):
        rosette(r, cx, cy)

    # A console in each cut corner: a C-scroll under the band curling down into its eye at the step, an acanthus leaf
    # trailing down the storage's side. Drawn on the left and mirrored.
    c = stone.Relief()
    x = SECTION_X0
    c.scroll([(x - 52, 153), (x - 38, 153.5), (x - 24, 154)] + stone.spiral(x - 16, 161, 7, 1.7, 270, 270 + 330)[1:],
             2.8, 1.4, eye=(x - 16, 161))
    c.leaf(stone.curve((x - 10, 171), (x - 8, 183), (x - 11, 197), (x - 9, 214), 28), 3.0, 3, 1)
    c.leaf(stone.curve((x - 46, 159), (x - 40, 165), (x - 31, 166), (x - 25, 162), 16), 2.1, 2, -1)
    for (x, y), v in c.h.items():
        r.lift(x, y, v)
        r.lift(W - 1 - x, y, v)

    # Where band and storage meet: a beaded rail; between the gear and the graph: a slim beaded pilaster.
    r.tube([(SECTION_X0, BAND_H + 0.3), (SECTION_X1, BAND_H + 0.3)], 1.7)
    for k in range(SECTION_X0 + 4, SECTION_X1 - 3, 5):
        r.dome(k + 0.5, BAND_H + 0.5, 1.1, 1.1, 0.7, 1.3)
    px = CRAFT[0] - 2.5
    r.tube([(px, 9), (px, BAND_H - 9)], 1.3, flat=0.9)
    for k in range(12, BAND_H - 10, 6):
        r.dome(px, k + 0.5, 1.0, 1.0, 0.6, 1.1)

    # The cartouche on top round the inventory's sigil, a leaf along the moulding on each side.
    sy = -1
    r.tube(stone.spiral(CX, sy, 10.6, 10.6, 0, 360, 120), 2.3)
    for k in range(10):
        a = math.radians(k * 36 + 18)
        r.dome(CX + math.cos(a) * 10.6, sy + math.sin(a) * 10.6, 1.4, 1.4, 1.1, 2.2)
    for s in (-1, 1):
        r.leaf(stone.curve((CX + s * 13, -4), (CX + s * 20, -7), (CX + s * 28, -6), (CX + s * 35, -3), 20), 2.0, 2, -s)
    # A scallop hanging under the storage.
    r.shell((CX, H + 1), 90, 130, 7.5, 9, 1.0, 1.6)
    r.dome(CX, H + 1, 3.6, 2.6, 2.6, 1.0)
    return r, sy


def frame():
    f = Surface(W, H, FRAME_X, FRAME_TOP, FRAME_BOTTOM)
    r, sy = relief()
    r.paint(f)
    # The cartouche's stone face and the incised pouch, the inventory's sigil (design/workstations/inventory.json).
    for y in range(sy - 9, sy + 10):
        for x in range(CX - 9, CX + 10):
            if math.hypot(x + 0.5 - CX, y + 0.5 - sy) <= 8.4:
                f.put(x, y, stone.rgb(stone.DISC))
    f.image.alpha_composite(stone.sigil('pouch'), (CX - 8 + FRAME_X, sy - 8 + FRAME_TOP))
    return f.finish()


def build():
    art = Image.new('RGBA', (W + 2 * FRAME_X, H + FRAME_TOP + FRAME_BOTTOM))
    art.alpha_composite(panel(), (FRAME_X, FRAME_TOP))
    art.alpha_composite(frame())
    TEXTURE.parent.mkdir(parents=True, exist_ok=True)
    art.save(TEXTURE)
    preview(art)
    print(f'Ark inventory: {art.width} x {art.height} texture')
    return 1


def preview(art):
    """The texture with the empty-slot icons the game draws and the graph panel's dark face, at 3x."""
    sheet = art.copy()
    icons = {}
    curios = sorted(glob.glob(str(ROOT / 'shared-mods/curios-neoforge-*.jar')))
    if curios:
        with zipfile.ZipFile(curios[-1]) as jar:
            for name in ('head', 'necklace', 'body', 'belt', 'feet', 'charm', 'back', 'hands', 'bracelet', 'ring', 'curio'):
                icons[name] = Image.open(io.BytesIO(jar.read(f'assets/curios/textures/slot/empty_{name}_slot.png'))).convert('RGBA')
    icons['legs'] = Image.open(ROOT / 'src/main/resources/assets/arksurvivalreturns/textures/slot/empty_legs_slot.png').convert('RGBA')
    at = lambda x, y: (x + FRAME_X, y + FRAME_TOP)
    for (x, y), name in CURIOS:
        if name in icons:
            sheet.alpha_composite(icons[name].resize((16, 16)), at(x, y))
    if JAR.is_file():
        with zipfile.ZipFile(JAR) as jar:
            def sprite(name):
                return Image.open(io.BytesIO(jar.read(f'assets/minecraft/textures/gui/sprites/container/slot/{name}.png'))).convert('RGBA')
            for (x, y), name in zip(ARMOR, ('helmet', 'chestplate', 'leggings', 'boots')):
                sheet.alpha_composite(sprite(name), at(x, y))
            sheet.alpha_composite(sprite('shield'), at(*OFFHAND))
    style = json.loads((ROOT / 'design/workstations/graph_style.json').read_text(encoding='utf-8'))['palette']
    def rgb(key):
        h = style[key].lstrip('#')[-6:]
        return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
    gx, gy, gw, gh = CRAFT
    d = ImageDraw.Draw(sheet)
    ox, oy = at(gx, gy)
    d.rectangle((ox, oy, ox + gw - 1, oy + gh - 1), fill=rgb('bevelLight'), outline=rgb('outline'))
    d.rectangle((ox + 2, oy + 2, ox + gw - 3, oy + gh - 3), fill=rgb('face'))
    d.rectangle((ox + 4, oy + 15, ox + gw - 5, oy + gh - 35), fill=rgb('well'))
    sheet.resize((sheet.width * 3, sheet.height * 3), Image.Resampling.NEAREST).save(PREVIEW)


if __name__ == '__main__':
    build()
