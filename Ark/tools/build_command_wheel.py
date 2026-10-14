"""Command wheel (F22): the carved ring the order key opens, in the P16 baroque material, and the whistle of each order.

Six wedges round a recessed medallion, clockwise from the top: Follow, Stay, Wander, Work, Pet, Call, each with its
symbol incised in a stone disc (stone_symbols). The ring is one height field lit from the top left like the workstation
frames (build_workstation_crests): a beaded rim, spokes with bosses between the wedges and a moulding round the
medallion, where the screen draws the tame. A lit copy of every wedge (the buttons' hover tint) and a dimmed one (an
order the tame cannot take) are cut from the same carving in the other tints, so they drop over it pixel for pixel; a
small ember gem marks the order a tame already has. Two texels per GUI unit, like the P16 buttons.

The whistles are synthesized, one call per order: a breathy pure tone with a little vibrato, gliding along its tune.

Writes assets/arksurvivalreturns/textures/gui/command_wheel/{wheel,hot_<order>,off_<order>,gem}.png,
sounds/companion/whistle_<order>.ogg with their sounds.json entries, and design/ui-rework/baroque/command-wheel.png.
client/CommandWheelScreen draws the textures at the sizes below (WHEEL, OUTER, INNER).
Run from Ark/: python tools/build_command_wheel.py
"""
import io
import json
import math
from pathlib import Path

import numpy as np
import soundfile as sf
from PIL import Image, ImageDraw, ImageFont

import build_workstation_crests as crest
from stone_symbols import engraved, symbol_mask

ARK = Path(__file__).resolve().parents[1]
ASSETS = ARK / 'src/main/resources/assets/arksurvivalreturns'
GUI = ASSETS / 'textures/gui/command_wheel'
SOUNDS = ASSETS / 'sounds/companion'
PREVIEW = ARK / 'design/ui-rework/baroque/command-wheel.png'
PAL = json.loads((crest.DESIGN / 'graph_style.json').read_text(encoding='utf-8'))['palette']

# Clockwise from the top; CompanionCommand lists them in this order.
ORDERS = ['follow', 'stay', 'wander', 'work', 'pet', 'call']
LABELS = {'follow': 'Follow', 'stay': 'Stay', 'wander': 'Wander', 'work': 'Work', 'pet': 'Pet', 'call': 'Call'}

UNIT = 2                  # texels per GUI unit
WHEEL = 136               # GUI units: the texture's side
OUTER, INNER = 64, 28     # GUI units: the ring's outer edge and the medallion's edge
T = WHEEL * UNIT
C = T / 2
RO, RI = OUTER * UNIT, INNER * UNIT
RM = (RO + RI) / 2        # the wedge's stone disc sits half-way out
DISC = 21                 # texels: the stone disc's radius under a 32-texel symbol
SHADOW = (0, 0, 0, 90)


def angle(i):
    """Centre of wedge i in degrees, screen-wise (y down): -90 is the top, angles grow clockwise."""
    return -90 + 60 * i


def ramp(state):
    tones = [crest.rgb(c) for c in crest.TONES]
    if state == 'hot':
        light = crest.rgb(PAL['buttonHotLight'])
        return [tuple(round(c[k] * .65 + light[k] * .35) for k in range(3)) + (255,) for c in tones]
    if state == 'off':
        return [tuple(round(v * .55) for v in c[:3]) + (255,) for c in tones]
    return tones


class Target:
    """Paints a Relief (or a ramp index) into an image in the given tint."""
    def __init__(self, image, tones):
        self.px = image.load()
        self.w, self.h = image.size
        self.tones = tones

    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = self.tones[max(0, min(len(self.tones) - 1, c))] if isinstance(c, int) else c


def circle(radius, step=1.5):
    n = max(24, int(2 * math.pi * radius / step))
    return [(C + math.cos(math.tau * k / n) * radius, C + math.sin(math.tau * k / n) * radius) for k in range(n + 1)]


def polar(a, r):
    return C + math.cos(math.radians(a)) * r, C + math.sin(math.radians(a)) * r


def relief():
    r = crest.Relief()
    r.tube(circle(RO - 3.5), 3.5)                      # the rim
    for k in range(60):                                # beads along its crown
        x, y = polar(k * 6 + 3, RO - 3.5)
        r.dome(x, y, 1.6, 1.6, 4.6)
    r.tube(circle(RO - 9.5), 1.1)                      # a fillet inside it
    r.tube(circle(RI + 3), 3)                          # the medallion's moulding
    for k in range(6):                                 # spokes between the wedges, a boss at each end
        a = angle(k) - 30
        r.tube([polar(a, RI + 6), polar(a, RO - 8)], 2.2, 1.6)
        for rr, size in ((RO - 9, 3.4), (RI + 7, 2.8)):
            x, y = polar(a, rr)
            r.dome(x, y, size, size, size)
    for k in range(6):                                 # a ring round each wedge's stone disc
        x, y = polar(angle(k), RM)
        r.tube([(x + math.cos(t / 40 * math.tau) * (DISC + 1.5), y + math.sin(t / 40 * math.tau) * (DISC + 1.5))
                for t in range(41)], 1.3)
    return r


def carve(state):
    tones = ramp(state)
    image = Image.new('RGBA', (T, T))
    px = image.load()
    hexes = ['#%02x%02x%02x' % c[:3] for c in tones]
    for y in range(T):
        for x in range(T):
            d = math.hypot(x + .5 - C, y + .5 - C)
            if d <= RO:
                if d >= RI:
                    px[x, y] = tones[4]                    # the ring's face
                elif (x % 6, y % 6) == (3, 3):
                    px[x, y] = crest.rgb(PAL['wellDot']) if state != 'off' else tones[3]
                else:
                    px[x, y] = tones[2]                    # the medallion's well
    for k in range(6):
        cx, cy = polar(angle(k), RM)
        for y in range(int(cy - DISC - 1), int(cy + DISC + 2)):
            for x in range(int(cx - DISC - 1), int(cx + DISC + 2)):
                if math.hypot(x + .5 - cx, y + .5 - cy) <= DISC:
                    px[x, y] = tones[6]                    # stone disc (crest.DISC in this tint)
    relief().paint(Target(image, tones))
    for k, order in enumerate(ORDERS):
        cx, cy = polar(angle(k), RM)
        x0, y0 = round(cx) - 16, round(cy) - 16
        image.alpha_composite(engraved(order, hexes, crest.LIGHT, 32), (x0, y0))
        if state == 'hot':
            # The chosen order's cut glows, so the symbol reads on the lit wedge.
            glow, cut = crest.rgb(PAL['linkHot']), symbol_mask(order, 32).load()
            for y in range(32):
                for x in range(32):
                    a = cut[x, y] / 255 * .8
                    if a:
                        p = px[x0 + x, y0 + y]
                        px[x0 + x, y0 + y] = tuple(round(p[i] * (1 - a) + glow[i] * a) for i in range(3)) + (255,)
    return image


def wedge_mask(i):
    """Wedge i's field between its spokes, inside the rim and outside the medallion's moulding."""
    mask = Image.new('L', (T, T))
    px = mask.load()
    for y in range(T):
        for x in range(T):
            dx, dy = x + .5 - C, y + .5 - C
            d = math.hypot(dx, dy)
            if not RI + 6.5 <= d <= RO - 7.5:
                continue
            a = math.degrees(math.atan2(dy, dx))
            offset = (a - angle(i) + 180) % 360 - 180
            if abs(offset) > 30:
                continue
            # Distance to either spoke's centre line keeps the spokes unlit.
            if min(abs(math.sin(math.radians(30 - abs(offset)))) * d, 99) <= 3.6:
                continue
            px[x, y] = 255
    return mask


def finish(image):
    """The outline every Ark GUI piece has and a soft shadow down and right of the ring."""
    image = crest.outline(image)
    px = image.load()
    out = Image.new('RGBA', image.size)
    sp = out.load()
    for y in range(2, T):
        for x in range(1, T):
            if not px[x, y][3] and px[x - 1, y - 2][3]:
                sp[x, y] = SHADOW
    out.alpha_composite(image)
    return out


def gem():
    """The ember gem on the rim of the order a tame already has: 6 GUI units across."""
    size = 12
    image = Image.new('RGBA', (size, size))
    tones = ['#5a2a17', '#9a482b', PAL['select'], '#eec09b', '#fbe6d2']
    c = size / 2
    for y in range(size):
        for x in range(size):
            dx, dy = (x + .5 - c) / 4.6, (y + .5 - c) / 4.6
            q = 1 - dx * dx - dy * dy
            if q <= 0:
                continue
            nz = math.sqrt(q)
            s = -dx * crest.LIGHT[0] - dy * crest.LIGHT[1] + nz * crest.LIGHT[2]
            image.putpixel((x, y), crest.rgb(tones[max(0, min(4, int(s * 4.6 - 0.4)))]))
    return crest.outline(image)


# --------------------------------------------------------------------------------------------- whistles

RATE = 44100
# Each tune: (start s, length s, frequency at the start, at the end, wobble Hz, wobble depth), and the peak level.
TUNES = {
    'follow': ([(0.00, 0.15, 1350, 1500, 0, 0), (0.19, 0.30, 1500, 2050, 0, 0)], 0.80),   # come along: rising
    'stay': ([(0.00, 0.50, 2050, 1300, 0, 0)], 0.80),                                       # halt: one falling call
    'wander': ([(0.00, 0.70, 1650, 1450, 4.5, 230)], 0.70),                                 # off you go: a lazy warble
    'work': ([(0.00, 0.10, 1800, 1860, 0, 0), (0.16, 0.10, 1800, 1860, 0, 0),
              (0.32, 0.22, 2150, 2050, 0, 0)], 0.80),                                       # to work: whit-whit-wheet
    'pet': ([(0.00, 0.17, 1600, 1420, 0, 0), (0.22, 0.26, 1520, 1180, 0, 0)], 0.45),       # soft, falling
    'call': ([(0.00, 0.34, 1100, 2450, 0, 0), (0.37, 0.46, 2450, 1500, 0, 0)], 0.90),      # attention: up and down
}


def whistle(order):
    notes, peak = TUNES[order]
    length = max(s + d for s, d, *_ in notes) + 0.12
    out = np.zeros(int(length * RATE))
    rng = np.random.default_rng(len(order))
    for start, dur, f0, f1, wobble, depth in notes:
        n = int(dur * RATE)
        t = np.arange(n) / RATE
        u = t / dur
        glide = f0 + (f1 - f0) * (u * u * (3 - 2 * u))                 # eased glide
        freq = glide * (1 + 0.006 * np.sin(math.tau * 5.6 * t)) + depth * np.sin(math.tau * wobble * t)
        phase = np.cumsum(freq) / RATE * math.tau
        attack, release = min(0.025, dur / 3), min(0.06, dur / 2)
        env = np.minimum(1, t / attack) * np.minimum(1, (dur - t) / release)
        env = np.clip(env, 0, 1) ** 1.4
        tone = np.sin(phase) + 0.06 * np.sin(2 * phase)
        # Breath: noise ringing at the tone's pitch through a two-pole resonator.
        noise = rng.standard_normal(n)
        breath = np.zeros(n)
        y1 = y2 = 0.0
        for k in range(n):
            w = math.tau * freq[k] / RATE
            r = 0.985
            y = noise[k] * (1 - r) + 2 * r * math.cos(w) * y1 - r * r * y2
            breath[k] = y
            y2, y1 = y1, y
        breath /= max(1e-9, np.abs(breath).max())
        i = int(start * RATE)
        out[i:i + n] += env * (tone + 0.22 * breath)
    out *= peak / max(1e-9, np.abs(out).max())
    return out.astype(np.float32)


def sounds():
    SOUNDS.mkdir(parents=True, exist_ok=True)
    for order in ORDERS:
        buffer = io.BytesIO()
        sf.write(buffer, whistle(order), RATE, format='OGG', subtype='VORBIS')
        (SOUNDS / f'whistle_{order}.ogg').write_bytes(buffer.getvalue())
    path = ASSETS / 'sounds.json'
    table = json.loads(path.read_text(encoding='utf-8'))
    for order in ORDERS:
        table[f'companion.whistle.{order}'] = {'subtitle': 'subtitles.arksurvivalreturns.whistle',
                                               'sounds': [{'name': f'arksurvivalreturns:companion/whistle_{order}'}]}
    path.write_text(json.dumps(table, indent=2) + '\n', encoding='utf-8')


# ---------------------------------------------------------------------------------------------- preview

def preview(base, hot, off):
    """The wheel as the screen composes it, at 3x, with Follow hovered, Work out of reach and the gem on Stay."""
    scale = 3
    width, height = 620, 600
    sheet = Image.new('RGBA', (width * scale // 2, height * scale // 2), '#141a1f')
    d = ImageDraw.Draw(sheet)
    for y in range(sheet.height):                   # dusk behind the ring, where the world is
        v = y / sheet.height
        d.line((0, y, sheet.width, y), fill=(int(36 + 30 * v), int(44 + 26 * v), int(52 + 6 * v)))
    wheel = base.copy()
    wheel.alpha_composite(hot['follow'])
    wheel.alpha_composite(off['work'])
    g = gem()
    gx, gy = polar(angle(1), RO - 3.5)
    wheel.alpha_composite(g, (round(gx) - 6, round(gy) - 6))
    k = scale / UNIT
    big = wheel.resize((round(T * k), round(T * k)), Image.Resampling.NEAREST)
    ox, oy = (sheet.width - big.width) // 2, (sheet.height - big.height) // 2 + 10
    sheet.alpha_composite(big, (ox, oy))

    def font(size):
        for name in ('C:/Windows/Fonts/consola.ttf', 'DejaVuSansMono.ttf'):
            try:
                return ImageFont.truetype(name, size)
            except OSError:
                pass
        return ImageFont.load_default(size=size)
    label, small = font(24), font(19)
    cx, cy = ox + big.width / 2, oy + big.height / 2
    for i, order in enumerate(ORDERS):
        a = math.radians(angle(i))
        x, y = cx + math.cos(a) * (OUTER + 10) * scale, cy + math.sin(a) * (OUTER + 10) * scale
        text = LABELS[order]
        box = d.textbbox((0, 0), text, font=label)
        w, h = box[2] - box[0], box[3] - box[1]
        anchor_x = x - w / 2 if abs(math.cos(a)) < .3 else (x if math.cos(a) > 0 else x - w)
        colour = PAL['title'] if order == 'follow' else PAL['buttonTextOff'] if order == 'work' else PAL['muted']
        d.text((anchor_x, y - h / 2 - 4), text, fill=colour, font=label)
    title = 'Triceratops  -  Lv 23  -  staying'
    box = d.textbbox((0, 0), title, font=label)
    d.text(((sheet.width - box[2]) / 2, 34), title, fill=PAL['title'], font=label)
    hint = 'Follow: walks after you wherever you go'
    box = d.textbbox((0, 0), hint, font=small)
    d.text(((sheet.width - box[2]) / 2, sheet.height - 70), hint, fill=PAL['linkHot'], font=small)
    hint = 'release the key to order  /  Esc to cancel'
    box = d.textbbox((0, 0), hint, font=small)
    d.text(((sheet.width - box[2]) / 2, sheet.height - 42), hint, fill=PAL['faint'], font=small)
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(PREVIEW)


def build():
    GUI.mkdir(parents=True, exist_ok=True)
    base = finish(carve('normal'))
    base.save(GUI / 'wheel.png')
    lit, dim = carve('hot'), carve('off')
    hot, off = {}, {}
    for i, order in enumerate(ORDERS):
        mask = wedge_mask(i)
        for source, store, prefix in ((lit, hot, 'hot'), (dim, off, 'off')):
            piece = Image.new('RGBA', (T, T))
            piece.paste(source, (0, 0), mask)
            piece.save(GUI / f'{prefix}_{order}.png')
            store[order] = piece
    gem().save(GUI / 'gem.png')
    sounds()
    preview(base, hot, off)
    print(f'F22: wheel, {2 * len(ORDERS)} wedge overlays, gem and {len(ORDERS)} whistles written.')


if __name__ == '__main__':
    build()
