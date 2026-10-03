"""Frames for the workstation screens (P14): every bench's panel sits in the same baroque frame, its sigil in the
cartouche on top.

The frame is the panel's edge, not an ornament above it: a carved moulding round all four sides that covers the
panel's vanilla outline and bevel, scrolls, shells and acanthus at the corners and sides, a beaded rail between the
graph well and the craft bar, a beaded cartouche round the bench's sigil and a scallop shell under the bottom. It is
carved in the panel's own dark tones (the graph_style palette and steps between them), shaded from a height field with
the light from the top left, like the GUI. Each bench names its symbol in its design
file ({"sigil": "helm"}); symbols are incised into a stone medallion in the same palette.

The panel size comes from design/workstations/graph_style.json. Writes design/workstations/crests/<bench>.png (the
frame, FRAME_W x FRAME_H; the panel's top-left corner sits at (MARGIN, PANEL_TOP) of the image, PANEL_BOTTOM units of
frame hang below it; the panel interior is transparent) and <bench>_sigil.png (32x32, the sigil at 2x: the bench's tab
icon and the icon of a planned bench block). The showcase draws the frame over the panel face and under the graph; the
mod will blit the same files as GUI sprites.
Run from Ark/: python tools/build_workstation_crests.py
"""
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

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

def sigil(name):
    from stone_symbols import engraved
    return engraved(name, TONES, LIGHT)


# ------------------------------------------------------------------------------------------------ frames

PANEL = json.loads((DESIGN / 'graph_style.json').read_text(encoding='utf-8'))['panel']
PANEL_W, PANEL_H = PANEL['width'], PANEL['height']
RAIL = PANEL_H - PANEL['bar'] - PANEL['inset']  # the well's lower edge: the rail runs here, above the craft bar
MARGIN, PANEL_TOP, PANEL_BOTTOM = 26, 33, 30   # room round the panel for the ornament
FRAME_W, FRAME_H = PANEL_W + 2 * MARGIN, PANEL_TOP + PANEL_H + PANEL_BOTTOM
CX = PANEL_W // 2
LIP = 2         # the frame covers the panel's outline and light bevel: panel units 0 and 1 from each edge
SEAL_Y = -5     # the cartouche's centre, on the top moulding
SHADOW = (0, 0, 0, 90)
# The panel's own tones (graph_style palette: outline, bevel dark, well, face, button, bevel light, button light, faint,
# muted) with steps between them, dark to light.
TONES = ['#050604', '#0b0c0a', '#0f110e', '#151813', '#1a1d18', '#242820', '#30352b', '#3a4034', '#4c5445', '#5d6258',
         '#747970', '#8a8f86']
DISC = '#30352b'  # stone face under the incised symbol (TONES[6])
LIGHT = tuple(v / math.sqrt(1 + 1 + 1.8 ** 2) for v in (-1, -1, 1.8))


class Frame:
    """One frame image, drawn in panel units: (0, 0) is the panel's top-left corner. A colour is an index into TONES,
    a hex string or an RGBA tuple."""

    def __init__(self):
        self.image = Image.new('RGBA', (FRAME_W, FRAME_H))
        self.px = self.image.load()
        self.ramp = [rgb(c) for c in TONES]

    def put(self, x, y, c):
        ix, iy = x + MARGIN, y + PANEL_TOP
        if not (0 <= ix < FRAME_W and 0 <= iy < FRAME_H):
            return
        if isinstance(c, int):
            c = self.ramp[max(0, min(len(self.ramp) - 1, c))]
        self.px[ix, iy] = rgb(c) if isinstance(c, str) else c

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


def facing(dx, dy):
    """1 where a round surface faces the top-left light, -1 where it faces away."""
    d = math.hypot(dx, dy) or 1
    return (-dx - dy) / (d * math.sqrt(2))


def seal(f, sigil_name, hole=10.2, cy=SEAL_Y):
    """Stone cartouche with an incised symbol, lit from the same top-left as the frame."""
    for y in range(cy - 11, cy + 12):
        for x in range(CX - 11, CX + 12):
            if math.hypot(x + 0.5 - CX, y + 0.5 - cy) <= hole:
                f.put(x, y, DISC)
    f.image.alpha_composite(sigil(sigil_name), (CX - 8 + MARGIN, cy - 8 + PANEL_TOP))


# ----------------------------------------------------------------------------------------------- ornament

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


def ornament():
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
    m.tube(spiral(CX, SEAL_Y, 16.6, 16.6, 0, 360, 160), 1.1, base=0.8)
    m.tube(spiral(CX, SEAL_Y, 13.0, 13.0, 0, 360, 140), 3.0)
    for k in range(12):
        a = math.radians(k * 30 + 15)
        m.dome(CX + math.cos(a) * 13.0, SEAL_Y + math.sin(a) * 13.0, 1.6, 1.6, 1.3, 2.4)
    m.shell((CX, SEAL_Y - 17), 270, 120, 9, 7, 1.0, 1.6)
    m.leaf(curve((CX + 5, SEAL_Y - 17), (CX + 11, SEAL_Y - 22), (CX + 17, SEAL_Y - 21), (CX + 21, SEAL_Y - 17), 16), 2.2, 2, -1)
    m.scroll([(CX + 15, SEAL_Y + 6), (CX + 19.5, SEAL_Y + 1)] + spiral(CX + 25, SEAL_Y - 6, 6.6, 1.7, 125, 125 - 340)[1:],
             3.2, 1.6, eye=(CX + 25, SEAL_Y - 6))
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


def frame(sigil_name):
    """A bench's frame: the carved ornament, shaded, with the bench's sigil in the cartouche."""
    f = Frame()
    ornament().paint(f)
    seal(f, sigil_name)
    return f.finish()


def build():
    OUT.mkdir(parents=True, exist_ok=True)
    for file in sorted(DESIGN.glob('*.json')):
        data = json.loads(file.read_text(encoding='utf-8'))
        name = data.get('sigil')
        if not name:
            continue
        frame(name).save(OUT / f'{file.stem}.png')
        sigil(name).resize((32, 32), Image.Resampling.NEAREST).save(OUT / f'{file.stem}_sigil.png')
        print('frame', file.stem, name)
    preview_symbols()


def preview_symbols():
    """Show the twelve stone seals with their actual surrounding frame carving."""
    entries = [(json.loads(p.read_text(encoding='utf-8')).get('sigil'), p.stem.replace('_', ' ').title())
               for p in sorted(DESIGN.glob('*.json'))]
    entries = [(name, label) for name, label in entries if name] + [('crate', 'Storage Crate')]
    sheet = Image.new('RGBA', (1120, 830), '#10130f')
    d = ImageDraw.Draw(sheet)
    def font(size):
        for face in ('C:/Windows/Fonts/consola.ttf', 'DejaVuSansMono.ttf'):
            try:
                return ImageFont.truetype(face, size)
            except OSError:
                pass
        return ImageFont.load_default(size=size)
    d.text((32, 20), 'ARK / ENGRAVED STONE SYMBOLS', font=font(28), fill='#e6e8e1')
    d.text((32, 62), 'Shared frame palette / recessed cuts / top-left light', font=font(17), fill='#8a8f86')
    # One original relief is enough; only the centre of the seal changes.
    base = Frame()
    ornament().paint(base)
    for i, (name, label) in enumerate(entries):
        f = Frame()
        f.image = base.image.copy()
        f.px = f.image.load()
        seal(f, name)
        cx, cy = CX+MARGIN, SEAL_Y+PANEL_TOP
        detail = f.finish().crop((cx-27, max(0, cy-28), cx+27, cy+24))
        if name == 'crate':
            from build_storage_ui import coffer_frame, CRATE_MARGIN
            crate = coffer_frame()
            center = CRATE_MARGIN+88
            detail = crate.crop((center-27, crate.height-52, center+27, crate.height))
        x, y = 20+(i % 4)*280, 102+(i // 4)*240
        sheet.alpha_composite(detail.resize((216, 208), Image.Resampling.NEAREST), (x+22, y))
        label_font = font(17)
        width = d.textbbox((0, 0), label, font=label_font)[2]
        d.text((x+130-width/2, y+207), label, font=label_font, fill='#c1c7b9')
    target = ARK / 'design/ui-rework/baroque/engraved-symbols-preview.png'
    target.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(target)


if __name__ == '__main__':
    build()
