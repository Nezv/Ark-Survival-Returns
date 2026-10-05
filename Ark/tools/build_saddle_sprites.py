"""Item sprites for the creature saddles and for the hide. Design only: no such item is registered yet.

The saddles are drawn from the concept kit of tools/preview_saddles.py, the way the armour sprites are drawn from
the worn models (build_armour_sprites.py): the kit's own boxes, materials and painted faces, in the three tiers
(hide, bronze, steel) and the four seats it fits (straddle, framed chair, deck, and the Spinosaurus's own nest).
An item has no animal under it, so the blanket and the girths are fitted to a plain barrel, cut off under the
flanks. The straddle seat is the kit's; the chair, the deck and the nest are built here with fewer and heavier
timbers than the kit gives them, because a rail one texel thick is nothing at 32 px. The blanket's edge tells
the tiers apart, as on the animals: a cream fringe on hide, gold piping in a dark border on bronze, a riveted
plate on steel.

The hide is hand-pixelled on a 16 px grid and doubled, like the other materials (build_cosy_items.py): a raw
skin, scale side up, one corner turned over, so it does not read as tanned vanilla leather.

Output: design/items/sprites/*.png (32 px) and the review sheet design/items/saddle-sprites.png.

Run from Ark: python tools/build_saddle_sprites.py
"""
import math

import numpy as np
from PIL import Image, ImageDraw

import build_armour_sprites as armour
import preview_saddles as kit_of
from build_bronze_age_art import ARK, JAR, item32, save, vanilla, WRITTEN
from build_cosy_items import sprite

OUT = ARK / 'design/items/sprites'
REVIEW = ARK / 'design/items/saddle-sprites.png'
TIERS = ('hide', 'bronze', 'steel')
VIEW = {'straddle': (-52, 30), 'chair': (-38, 30), 'deck': (-52, 24), 'sail': (-35, 30)}  # yaw, pitch
SHEEN = 0.2
PLAIN = ('wood', 'crate', 'leather', 'metal', 'bag')  # one tone a face: their grain is noise at this size
FACE_CORNERS = np.array([(0, 1), (1, 1), (1, 0), (0, 0)], float)


class Stand:
    """A barrel in place of an animal (half-width a, half-height b, lying along z): what the kit asks of a body."""

    def __init__(self, a, b, length):
        self.a, self.b = a, b
        self.lo, self.hi = np.array([-a, -b, -length / 2]), np.array([a, b, length / 2])

    def section(self, z):
        return dict(lo=-self.b, hi=self.b, ridge=None) if self.lo[2] <= z <= self.hi[2] else None

    def point(self, z, theta):
        s, c = math.sin(math.radians(theta)), math.cos(math.radians(theta))
        r = 1 / math.sqrt((s / self.a) ** 2 + (c / self.b) ** 2)
        return np.array([r * s, r * c, z]), 0

    def top(self, z, x=0.0):
        if self.section(z) is None or abs(x) > self.a:
            return None
        return self.b * math.sqrt(1 - (x / self.a) ** 2)

    def nearest(self, p):
        return 0

    def ridge_angle(self, z0, z1):
        return 0.0


def blanket(tier, a, b, half, drop, girths, strap, thick=1.0, rows=2):
    """The kit's blanket and girths on a barrel, the girths cut off under the flanks; the kit and the seat height."""
    kit = kit_of.Kit(Stand(a, b, 2 * half + 8), tier)
    for z in girths:
        kit.ring(z, strap, thick, 0.3, t0=-110.0, t1=110.0, buckle=96.0)
    kit.pad(-half, half, drop, thick, thick + 0.6, 1.0, rows=rows)
    return kit, b + 2 * thick + 0.6


def straddle(tier):
    kit, top = blanket(tier, 7.0, 8.0, 11.0, 66.0, (5.0,), 2.6)
    kit.straddle_seat(0.5, top - 8.0)
    return kit


def chair(tier):
    """A padded seat and back in a timber frame, a rail either side."""
    kit, top = blanket(tier, 9.5, 9.0, 15.0, 64.0, (9.0, -9.0), 2.6)
    kit.mount((0, top, 0))
    kit.block((0, 0.8, 0), (15, 1.6, 15), 'wood', 0)
    kit.block((0, 2.8, 1.0), (10, 2.4, 10), 'leather', 0)
    kit.block((0, 8.0, -6.4), (10, 12, 2.2), 'leather', 0, pitch=10)
    kit.block((0, 14.4, -7.6), (13, 1.8, 2.8), 'wood', 0, dark=True)
    for side in (-1, 1):
        kit.block((side * 6.6, 4.0, 5.2), (1.8, 5, 1.8), 'wood', 0, dark=True)
        kit.block((side * 6.6, 7.2, -0.6), (2.0, 1.8, 14), 'metal', 0)
    return kit


def deck(tier):
    """A railed deck on legs, open at the front: a crate, a bedroll and the rider's chair aboard."""
    kit, top = blanket(tier, 12.0, 10.0, 17.0, 76.0, (10.0, -10.0), 3.0, rows=3)
    kit.mount((0, top + 3.0, 0))
    for sx in (-1, 1):
        for sz in (-1, 1):
            kit.block((sx * 13.5, -4.5, sz * 13.5), (2.4, 9, 2.4), 'wood', 0, dark=True)
    kit.block((0, 1.0, 0), (31, 2, 33), 'wood', 0)
    for sx in (-1, 1):
        for z in (-15.0, 0.0, 15.0):
            kit.block((sx * 14.2, 5.5, z), (2.2, 7, 2.2), 'wood', 0)
        kit.block((sx * 14.2, 9.6, 0), (2.4, 1.8, 33), 'wood', 0, dark=True)
    kit.block((0, 5.5, -15.0), (2.2, 7, 2.2), 'wood', 0)
    kit.block((0, 9.6, -15.0), (28, 1.8, 2.4), 'wood', 0, dark=True)
    kit.block((-5.5, 6.0, -7.0), (9, 8, 9), 'crate', 0)
    kit.block((5.0, 4.6, -9.0), (12, 5.2, 5.2), 'roll', 0)
    kit.block((2.0, 3.2, 7.0), (9, 2.4, 9), 'leather', 0)
    kit.block((2.0, 7.5, 2.0), (9, 9, 2.0), 'leather', 0, pitch=8)
    return kit


def sail(tier):
    """The Spinosaurus's own: the nest on its stepped cradle, the guard plate leaning back where the sail would
    be, and the two rails that run along the sail's foot, a skirt hanging from each."""
    kit = kit_of.Kit(Stand(9.0, 9.0, 60.0), tier)
    for dz, low in ((-7.0, 5.0), (0.0, 7.5), (7.0, 10.0)):
        kit.block((0, -low / 2, 12 + dz), (12, low, 7.2), 'wood', 0, dark=dz == 0.0)
    kit.mount((0, 0, 12))
    kit.block((0, 0.9, 0), (16, 1.8, 22), 'wood', 0)
    kit.block((0, 2.9, 10.2), (16, 3.0, 1.8), 'wood', 0)
    kit.block((0, 3.4, -1.0), (11, 3.4, 15), 'leather', 0)
    for side in (-1, 1):
        kit.block((side * 7.4, 3.2, 0), (1.8, 3.6, 22), 'wood', 0)
        kit.block((side * 7.4, 5.6, 10.2), (2.6, 1.8, 2.6), 'metal', 0)
    kit.mount()
    up = kit_of.unit((0.0, 1.0, -0.62))
    kit.box(np.array([0.0, 4.0, 1.0]) + up * 13.0, [(1, 0, 0), up, np.cross((1, 0, 0), up)], (8.0, 13.0, 1.2), 'guard', 0)
    for side in (-1, 1):
        front, rear = np.array([side * 9.0, 3.0, 1.0]), np.array([side * 9.0, -1.0, -30.0])
        kit.along(front, rear, (2.6, 2.6), 'wood', 0, hint=(side, 0, 0), dark=True)
        for joint in (front, rear):
            kit.block(joint, (3.8, 3.8, 3.8), 'metal', 0)
        out, along = kit_of.unit((side, 0.22, 0.0)), kit_of.unit(rear - front)
        kit.box((front + rear) / 2 + (side * 1.6, -9.5, 0.0), [np.cross(out, along), out, along], (8.0, 0.6, 12.5), 'pad', 0,
                a0=float(side < 0), a1=float(side > 0), b0=0.0, b1=1.0, arc_px=16.0, len_px=25.0)
    return kit


SEATS = {'straddle': straddle, 'chair': chair, 'deck': deck, 'sail': sail}


def quads(kit, size=armour.SIZE - 2 * armour.MARGIN):
    """The kit's boxes as the textured quads build_armour_sprites renders, painted at a texel a sprite pixel."""
    corners = np.concatenate([b['centre'] + ((kit_of.ss.CORNERS * 2 - 1) * b['half']) @ b['axes'] for b in kit.boxes])
    density = size / float(np.ptp(corners, axis=0).max())
    palette = kit_of.TIERS[kit.tier]
    groups = []
    for index, box in enumerate(kit.boxes):
        box = dict(box, **{key: box[key] * density for key in ('arc_px', 'len_px') if key in box})
        for slot, (key, (ax_s, ax_t)) in enumerate(kit_of.FACE_DIMS.items()):
            w = max(1, round(2 * box['half'][ax_s] * density))
            h = max(1, round(2 * box['half'][ax_t] * density))
            t, s = np.meshgrid((np.arange(h) + 0.5) / h, (np.arange(w) + 0.5) / w, indexing='ij')
            image = np.clip(kit_of.paint(box, key, kit_of.face_unit(key, s, t), np.random.default_rng(index * 6 + slot),
                                         palette, kit.tier), 0, 255)
            if box['mat'] in PLAIN:
                image[:] = image.mean(axis=(0, 1))
            texture = np.dstack([image, np.full(image.shape[:2], 255.0)]).astype(np.uint8)
            unit = kit_of.face_unit(key, FACE_CORNERS[:, 0], FACE_CORNERS[:, 1])
            world, uv = box['centre'] + ((unit * 2 - 1) * box['half']) @ box['axes'], FACE_CORNERS
            if np.cross(world[1] - world[0], world[2] - world[0]) @ (world.mean(0) - box['centre']) < 0:
                world, uv = world[::-1], uv[::-1]  # wound to face outwards
            groups.append((None, [(world * (1, -1, -1), uv)], texture))  # the renderer's model space
    return groups


def saddle(seat, tier):
    return armour.render(quads(SEATS[seat](tier)), VIEW[seat], SHEEN)


def hide():
    """A raw skin, scale side up: the row of scutes down the back, scales either side, one corner turned over."""
    return sprite(["................",
                   "..hH......HHd...",
                   ".hHHH.hK.HHHHd..",
                   ".hHHsHHkHHsHHd..",
                   "..HHHHHKHHHHd...",
                   "..hHsHHkHHsHd...",
                   "...HHHHKHHHd....",
                   "...hHsHkHsHd....",
                   "...HHHHKHHHd....",
                   "..hHsHHkHHsHd...",
                   "..HHHHHKHuuuu...",
                   ".hHHsHHduUUUd...",
                   ".HHHHdHduUUd....",
                   ".HHd.dHd.ud.....",
                   "................",
                   "................"],
                  {'h': (184, 166, 122), 'H': (140, 120, 84), 's': (98, 82, 58), 'd': (100, 84, 60),
                   'K': (196, 180, 138), 'k': (84, 70, 50), 'U': (232, 200, 168), 'u': (192, 152, 124)})


def sprites():
    images = {f'{tier}_{seat}_saddle': saddle(seat, tier) for seat in SEATS for tier in TIERS}
    images['hide'] = item32(hide())
    return images


def review(images):
    """The review sheet: seats in rows, tiers in columns, then the hide beside the vanilla sprites it must not
    be taken for; each at twice its texture size, on a light slot and on a dark panel."""
    cell, label, columns = 80, 12, 4
    extras = [('hide', images['hide'])]
    if JAR.exists():
        extras += [(f'vanilla {name}', item32(vanilla(f'item/{name}'))) for name in ('leather', 'saddle')]
    grid = []
    for row, seat in enumerate(SEATS):
        grid += [(f'{tier} {seat}', images[f'{tier}_{seat}_saddle']) for tier in TIERS]
        grid.append(extras[row] if row < len(extras) else None)
    sheet = Image.new('RGB', (columns * cell * 2, len(SEATS) * (cell + label)), (36, 30, 26))
    draw = ImageDraw.Draw(sheet)
    for band, colour in enumerate(((198, 190, 172), (58, 46, 38))):
        for i, tile in enumerate(grid):
            if tile is None:
                continue
            x, y = (i % columns + band * columns) * cell, (i // columns) * (cell + label)
            draw.rectangle([x + 3, y + 3, x + cell - 4, y + cell - 4], fill=colour)
            big = tile[1].resize((64, 64), Image.Resampling.NEAREST)
            sheet.paste(big, (x + 8, y + 8), big)
            draw.text((x + 4, y + cell - 3), tile[0], fill=(200, 190, 170))
    return sheet


def main():
    images = sprites()
    for name, image in images.items():
        save(image, OUT / f'{name}.png')
    save(review(images), REVIEW)
    print(f'Wrote {len(WRITTEN)} files')


if __name__ == '__main__':
    main()
