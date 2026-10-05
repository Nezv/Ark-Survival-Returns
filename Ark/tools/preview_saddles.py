"""Concept preview of creature saddles.

Fits a parametric saddle kit to the runtime creature models and renders it over the creature's real skin.
The trunk is measured station by station, every strap and pad plate is laid tangent to that outline and is
owned by the bone of the cube under it, so the same boxes follow the animation.

Nothing here ships: the boxes are drawn, not written as geometry. Output: Scratch/saddles/*.png.

    python Ark/tools/preview_saddles.py [--species tyrannosaurus triceratops]
"""
import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
import skin_studio as ss  # noqa: E402

ASSETS = ROOT / "Ark/src/main/resources/assets/arksurvivalreturns"
OUT = ROOT / "Scratch/saddles"
RIDER = 0.9375  # the player model is drawn at 15/16

# seat: the back bone CreatureSeats uses. The seat kind follows from the measured back: straddle when a
# player's legs fit round it, chair (a framed seat) when it is too wide; platform is asked for.
SPECIES = {
    "velociraptor": dict(seat="Cnt_Spine_003_JNT_SKL", skin="midnight", tier="hide", cargo=None),
    "pteranodon": dict(seat="c_back4", skin="burgundy", tier="hide", cargo=None, drop=48, clip="Fly-Fwd"),
    "triceratops": dict(seat="c_back4", skin="ivory", tier="bronze", cargo="pack"),
    "tyrannosaurus": dict(seat="Cnt_Spine_002_JNT_SKL", skin="burgundy", tier="steel", cargo="reinforced"),
    "brontosaurus": dict(seat="c_back3", skin="ivory", tier="bronze", cargo="reinforced", kind="platform"),
}

TIERS = {
    "hide": dict(pad=(156, 124, 86), pad2=(134, 104, 72), trim=(216, 204, 178), accent=(98, 72, 50),
                 strap=(92, 66, 46), stitch=(146, 112, 80), seat=(112, 78, 54), metal=(228, 218, 196),
                 metal_lo=(176, 164, 138), wood=(128, 96, 62), bag=(148, 116, 80), roll=(124, 108, 90)),
    "bronze": dict(pad=(142, 48, 42), pad2=(124, 40, 37), trim=(72, 30, 28), accent=(208, 164, 68),
                   strap=(112, 76, 46), stitch=(186, 146, 98), seat=(126, 78, 44), metal=(204, 140, 62),
                   metal_lo=(140, 92, 40), wood=(136, 100, 60), bag=(152, 114, 70), roll=(180, 152, 104)),
    "steel": dict(pad=(54, 68, 86), pad2=(46, 58, 74), trim=(28, 36, 46), accent=(150, 170, 186),
                  strap=(50, 43, 40), stitch=(116, 108, 102), seat=(66, 50, 44), metal=(176, 186, 196),
                  metal_lo=(108, 118, 130), wood=(100, 82, 62), bag=(90, 88, 76), roll=(126, 54, 48)),
}
RIDER_COLOR = (150, 162, 174)
EDGES = np.array([(0, 1), (1, 2), (2, 3), (3, 0), (4, 5), (5, 6), (6, 7), (7, 4), (0, 4), (1, 5), (2, 6), (3, 7)])


def unit(v):
    v = np.asarray(v, float)
    return v / max(np.linalg.norm(v), 1e-9)


def slices(corners, z):
    """Where each cube's edges cross the plane of a station: (x, y) per edge, and which edges cross."""
    a, b = corners[:, EDGES[:, 0]], corners[:, EDGES[:, 1]]
    da, db = a[..., 2] - z, b[..., 2] - z
    crossing = (da * db <= 0) & (da != db)
    t = np.where(crossing, da / np.where(crossing, da - db, 1.0), 0.0)
    return (a + (b - a) * t[..., None])[..., :2], crossing


def convex_hull(points):
    points = np.unique(np.round(points, 4), axis=0)
    points = points[np.lexsort((points[:, 1], points[:, 0]))]

    def half(sequence):
        out = []
        for p in sequence:
            while len(out) >= 2:
                e, f = out[-1] - out[-2], p - out[-2]
                if e[0] * f[1] - e[1] * f[0] > 0:
                    break
                out.pop()
            out.append(p)
        return out[:-1]
    return np.array(half(points) + half(points[::-1]))


class Body:
    """The creature at rest: all cubes, the central trunk and the limbs against it."""

    def __init__(self, sid):
        self.sid = sid
        _, self.cubes, self.skeleton = ss.load_cubes(ASSETS / f"geckolib/models/entity/{sid}.geo.json")
        self.corners = np.array([ss.cube_corners(c) for c in self.cubes])
        sided = np.array([bool(ss.side_of(c["bone"])) for c in self.cubes])
        part = np.array([c["part"] for c in self.cubes])
        central = np.isin(part, ("torso", "neck", "tail")) & ~sided
        self.trunk = np.flatnonzero(central)
        self.limbs = np.flatnonzero(sided & ~np.isin(part, ("head", "jaw", "eye", "horn")))
        self.wrap = np.flatnonzero(sided & ~np.isin(part, ("head", "jaw", "eye", "horn", "feather")))
        # hind legs: rigs name them leg, foot or "back" (which reads as torso), never arm
        self.hind = np.flatnonzero(sided & np.isin(part, ("torso", "leg", "legl", "legr", "foot", "footl", "footr")))
        self.head = np.flatnonzero(np.isin(part, ("head", "jaw", "horn")))
        box = self.corners[self.trunk]
        edges = np.stack([box[:, 1] - box[:, 0], box[:, 3] - box[:, 0], box[:, 4] - box[:, 0]], 1)
        length = np.linalg.norm(edges, axis=2)
        axes = edges / np.maximum(length, 1e-9)[..., None]
        for k in range(3):  # a flat plane has no third edge: take it from the other two
            flat = length[:, k] < 1e-6
            axes[flat, k] = np.cross(axes[flat, (k + 1) % 3], axes[flat, (k + 2) % 3])
        self.centre, self.axes, self.half = box.mean(1), axes, np.maximum(length / 2, 1e-4)
        torso = self.corners[central & (part == "torso")].reshape(-1, 3)
        self.lo, self.hi = torso.min(0), torso.max(0)
        self.ceiling = self.corners.reshape(-1, 3)[:, 1].max() + 10
        self._span, self._point, self._section = {}, {}, {}

    def span(self, z, x=0.05):
        """Top and bottom of the trunk above this point, and the cube on top."""
        key = (round(z, 2), round(x, 2))
        if key not in self._span:
            o = np.einsum("nij,nj->ni", self.axes, np.array([x, self.ceiling, z]) - self.centre)
            d = self.axes @ np.array([0.0, -1.0, 0.0])
            parallel = np.abs(d) < 1e-9
            safe = np.where(parallel, 1.0, d)
            t1, t2 = (-self.half - o) / safe, (self.half - o) / safe
            inside = np.abs(o) <= self.half
            near = np.where(parallel, np.where(inside, -np.inf, np.inf), np.minimum(t1, t2)).max(1)
            far = np.where(parallel, np.where(inside, np.inf, -np.inf), np.maximum(t1, t2)).min(1)
            index = np.flatnonzero((near <= far) & (far > 0))
            if len(index):
                first = index[np.argmin(near[index])]
                self._span[key] = (self.ceiling - near[first], self.ceiling - far[index].max(), int(self.trunk[first]))
            else:
                self._span[key] = None
        return self._span[key]

    def section(self, z):
        """The taut outline of the trunk at this station: a convex hull, as a strap pulled tight would lie.

        The trunk cubes are a shell in places and the shoulders and hips fill its flanks, so the upper half
        of whatever limb crosses the station is wrapped in too.
        """
        key = round(z, 2)
        if key not in self._section:
            points, crossing = slices(self.corners[self.trunk], z)
            trunk = points[crossing]
            if len(trunk) < 3:
                self._section[key] = None
                return None
            lo, hi = trunk[:, 1].min(), trunk[:, 1].max()
            points, crossing = slices(self.corners[self.wrap], z)
            limbs = points[crossing]
            reach = 1.35 * np.abs(trunk[:, 0]).max()
            limbs = limbs[(limbs[:, 1] > (lo + hi) / 2) & (limbs[:, 1] < hi) & (np.abs(limbs[:, 0]) < reach)]
            both = np.vstack([trunk, limbs])
            both = np.vstack([both, both * [-1, 1]])  # the models are symmetric; keep the outline so
            self._section[key] = (convex_hull(both), np.array([0.0, (lo + hi) / 2]), lo, hi)
        return self._section[key]

    def point(self, z, theta):
        """Outline point at this station, theta degrees round from the top (positive towards +x), and the
        trunk cube nearest to it."""
        key = (round(z, 2), round(theta, 1))
        if key not in self._point:
            hull, centre, _, _ = self.section(z)
            d = np.array([math.sin(math.radians(theta)), math.cos(math.radians(theta))])
            a = hull
            e = np.roll(hull, -1, axis=0) - a
            den = d[0] * e[:, 1] - d[1] * e[:, 0]
            den = np.where(np.abs(den) < 1e-12, 1e-12, den)
            t = ((a[:, 0] - centre[0]) * e[:, 1] - (a[:, 1] - centre[1]) * e[:, 0]) / den
            u = ((a[:, 0] - centre[0]) * d[1] - (a[:, 1] - centre[1]) * d[0]) / den
            ok = (t > 0) & (u >= -1e-6) & (u <= 1 + 1e-6)
            r = t[ok].max() if ok.any() else 0.0
            p = np.array([centre[0] + r * d[0], centre[1] + r * d[1], z])
            local = np.einsum("nij,nj->ni", self.axes, p - self.centre)
            gap = np.linalg.norm(np.maximum(np.abs(local) - self.half, 0), axis=1)
            self._point[key] = (p, int(self.trunk[np.argmin(gap)]))
        return self._point[key]

    def cost(self, z, width, cubes):
        """How many of these limb cubes lie against the flank here, where a girth would have to pass."""
        section = self.section(z)
        if section is None:
            return None
        _, _, lo, hi = section
        hit = np.zeros(len(cubes), bool)
        for plane in (z - width / 2, z, z + width / 2):
            points, crossing = slices(self.corners[cubes], plane)
            hit |= (crossing & (points[..., 1] > lo + 0.3 * (hi - lo)) & (points[..., 1] < hi)).any(1)
        return int(hit.sum())

    def clear(self, z, width, lo, hi):
        """The station in [lo, hi] with the fewest limbs in the way, nearest to z: (station, limbs)."""
        best = None
        for station in np.arange(lo, hi + 0.01, 0.5):
            cost = self.cost(station, width, self.limbs)
            if cost is not None and (best is None or (cost, abs(station - z)) < (best[1], abs(best[0] - z))):
                best = (float(station), cost)
        return best

    def hind_front(self, width):
        """Front edge of the hind legs: a girth further back would sit on the rump, holding nothing."""
        lo, hi = float(self.lo[2]), float(self.hi[2])
        blocked = [z for z in np.arange(lo + width, lo + 0.6 * (hi - lo), 0.5) if self.cost(z, width, self.hind)]
        return max(blocked) + 0.5 if blocked else lo

    def head_back(self, level):
        """How far back the head, frill or crest reaches above the back."""
        reach = [c[:, 2].min() for c in self.corners[self.head]
                 if c[:, 1].max() > level and np.abs(c[:, 0]).min() < 10]
        return min(reach) if reach else np.inf


class Kit:
    """The saddle as oriented boxes: centre, axes (rows X, Y, Z), half sizes, material, owning cube."""

    def __init__(self, body, tier):
        self.body, self.tier, self.boxes = body, tier, []
        self.origin, self.turn = np.zeros(3), np.eye(3)

    def mount(self, origin=(0, 0, 0), lean=0.0):
        """Blocks are placed in this frame from here on; lean tips it up towards the front (radians)."""
        c, s = math.cos(lean), math.sin(lean)
        self.origin, self.turn = np.asarray(origin, float), np.array([[1, 0, 0], [0, c, s], [0, -s, c]], float)

    def box(self, centre, axes, half, mat, owner, **ctx):
        self.boxes.append(dict(centre=np.asarray(centre, float), axes=np.asarray(axes, float),
                               half=np.asarray(half, float), mat=mat, owner=owner, **ctx))

    def block(self, centre, size, mat, owner, pitch=0.0, **ctx):
        """Axis-aligned box in the mounted frame, optionally leaning back (pitch in degrees)."""
        c, s = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
        axes = np.array([(1, 0, 0), (0, c, -s), (0, s, c)], float) @ self.turn.T
        self.box(self.origin + self.turn @ np.asarray(centre, float), axes, np.asarray(size, float) / 2, mat,
                 owner, **ctx)

    def along(self, start, end, thick, mat, owner, hint=(1, 0, 0), **ctx):
        """A bar from one point to another."""
        start, end, hint = np.asarray(start, float), np.asarray(end, float), np.asarray(hint, float)
        y = unit(end - start)
        x = unit(hint - y * float(y @ hint))
        self.box((start + end) / 2, [x, y, np.cross(x, y)],
                 (thick[0] / 2, np.linalg.norm(end - start) / 2, thick[1] / 2), mat, owner, **ctx)

    def plate(self, z0, z1, t0, t1, thick, mat, lift, **ctx):
        """A plate lying on the trunk between two stations and two angles, clear of everything under it."""
        stations = np.linspace(z0, z1, 3)
        angles = np.linspace(t0, t1, max(3, int(abs(t1 - t0) / 7) + 1))
        hits = [[self.body.point(z, t) for t in angles] for z in stations]
        grid = np.array([[p for p, _ in row] for row in hits])
        p00, p10, p01, p11 = grid[0, 0], grid[0, -1], grid[-1, 0], grid[-1, -1]
        w = unit((p01 - p00) + (p11 - p10))
        u = (p10 - p00) + (p11 - p01)
        u = unit(u - w * float(u @ w))
        n = np.cross(w, u)
        centre = (p00 + p10 + p01 + p11) / 4
        flat = grid.reshape(-1, 3) - centre
        rise = max(0.0, float((flat @ n).max()))
        grow = 1.0 if mat == "pad" else 0.4  # pad plates overlap, so no skin shows between them
        half = (np.abs(flat @ u).max() + grow, thick / 2, np.abs(flat @ w).max() + grow)
        self.box(centre + n * (rise + lift + thick / 2), [u, n, w], half, mat, hits[1][len(angles) // 2][1], **ctx)

    def ring(self, z, width, thick, lift, t0=-180.0, t1=180.0, step=24.0, buckle=None):
        edges = np.linspace(t0, t1, max(1, round((t1 - t0) / step)) + 1)
        for a, b in zip(edges[:-1], edges[1:]):
            self.plate(z - width / 2, z + width / 2, a, b, thick, "strap", lift,
                       buckle=buckle is not None and a <= buckle < b)

    def pad(self, z0, z1, drop, thick, lift, density, rows, step=21.0):
        edges = np.linspace(-drop, drop, max(2, round(2 * drop / step)) + 1)
        cuts = np.linspace(z0, z1, rows + 1)
        outline = np.array([self.body.point((z0 + z1) / 2, t)[0] for t in np.linspace(0, drop, 9)])
        arc_px = np.linalg.norm(np.diff(outline, axis=0), axis=1).sum() * density
        for row in range(rows):
            for a, b in zip(edges[:-1], edges[1:]):
                self.plate(cuts[row], cuts[row + 1], a, b, thick, "pad", lift, a0=a / drop, a1=b / drop,
                           b0=row / rows, b1=(row + 1) / rows, arc_px=arc_px, len_px=(z1 - z0) * density)

    def rest(self, zs, reach, half_width, limit):
        """Where a seat this long rests on the back: height at zs, lean, the gap left under it, the cube."""
        stations, tops = [], []
        for z in np.linspace(zs - reach, zs + reach, 9):
            hits = [h for h in (self.body.span(z, x) for x in (0.05, -half_width, half_width)) if h]
            if hits:
                stations.append(z - zs)
                tops.append(max(h[0] for h in hits))
        stations, tops = np.array(stations), np.array(tops)
        lean = float(np.clip(math.atan(np.polyfit(stations, tops, 1)[0]), -limit, limit))
        line = math.tan(lean) * stations
        height = float((tops - line).max())
        return height, lean, float((height + line - tops).max()), self.body.span(zs)[2]

    def straddle_seat(self, zs, lift):
        height, lean, gap, owner = self.rest(zs, 6.5, 2.5, 0.45)
        fill = min(gap, 2.5)
        self.mount((0, height + lift, zs), lean)
        self.block((0, 0.8 - fill / 2, 0), (8, 1.6 + fill, 12), "leather", owner)
        self.block((0, 3.2, -5.4), (8, 4.2, 1.8), "leather", owner, pitch=14)
        self.block((0, 2.6, 5.0), (4, 2.6, 2.4), "leather", owner)
        self.block((0, 4.6, 5.2), (1.8, 1.8, 1.8), "metal", owner)
        seat = self.origin + self.turn @ np.array([0, 1.6, -0.8])
        self.mount()
        for side in (-1, 1):
            # stirrup leather down the flank to the rider's foot height, then the iron
            for angle in range(35, 125, 5):
                foot, _ = self.body.point(zs + 1.5, side * angle)
                if seat[1] - foot[1] > 10.0:
                    break
            self.ring(zs + 1.5, 1.6, 0.6, lift + 0.2, *sorted((side * 24.0, side * float(angle))), step=20.0)
            self.block(foot + (side * 1.6, -1.6, 0), (1.2, 3.2, 3.4), "metal", owner)
        return seat, owner

    def chair_seat(self, zs, lift, owner=None, base=None):
        if base is None:
            height, lean, gap, owner = self.rest(zs, 13.0, 9.0, 0.21)
            self.mount((0, height + lift, zs), lean)
        else:
            gap = 0.0
            self.mount((0, base, zs))
        fill = min(gap, 10.0)
        self.block((0, 1.25 - fill / 2, 0), (20, 2.5 + fill, 26), "wood", owner)
        self.block((0, 4.0, 12.0), (20, 3, 2), "wood", owner)
        self.block((0, 3.75, -3.0), (11, 2.5, 13), "leather", owner)
        self.block((0, 8.0, -10.2), (11, 10, 2.4), "leather", owner, pitch=12)
        for side in (-1, 1):
            self.block((side * 8.6, 5.0, -3.5), (1.8, 5, 12), "wood", owner)
            self.block((side * 8.6, 8.0, -3.5), (2.2, 1.4, 13), "metal", owner)
        self.block((0, 6.0, 6.5), (2, 7, 2), "wood", owner)
        self.block((0, 10.0, 6.5), (9, 1.8, 1.8), "metal", owner)
        seat = self.origin + self.turn @ np.array([0, 5.0, -3.0])
        self.mount()
        return seat, owner

    def bags(self, z, size, lift, owner, count=1):
        length, height, depth = 1.25 * size, size, 0.55 * size
        for side in (-1, 1):
            for k in range(count):
                zk = z - k * (length + 0.12 * size)
                flank, _ = self.body.point(zk, side * 72.0)
                widest = max(abs(self.body.point(zk + dz, side * t)[0][0])
                             for dz in (-length / 2, 0, length / 2) for t in (72, 85, 98, 110))
                centre = (side * (widest + lift + depth / 2), flank[1] + 0.2 * height - height / 2, zk)
                self.box(centre, [(0, 0, -side), (0, 1, 0), (side, 0, 0)], (length / 2, height / 2, depth / 2),
                         "bag", owner)
                # the strap the bag hangs from, over the back
                self.ring(zk, 0.22 * size, 0.5, lift + 0.15, *sorted((side * 6.0, side * 72.0)), step=22.0)

    def platform(self, zc, length, width, lift, owner, cargo):
        tops = [h[0] for h in (self.body.span(z, x) for z in np.linspace(zc - length / 2, zc + length / 2, 9)
                               for x in (0.05, -6.0, 6.0)) if h]
        y = max(tops) + lift + 3.5
        self.mount((0, y, zc))
        edge = width / 2 - 1.5
        for dz in (-0.42, -0.14, 0.14, 0.42):
            self.block((0, -1.6, dz * length), (width - 2, 3.2, 3.2), "wood", owner, dark=True)
        self.block((0, 1.0, 0), (width, 2, length), "wood", owner)
        for side in (-1, 1):
            for dz in (-0.5, -0.17, 0.17, 0.5):
                self.block((side * edge, 7.0, dz * (length - 3)), (2.4, 10, 2.4), "wood", owner)
            self.block((side * edge, 11.0, 0), (2, 2, length), "wood", owner, dark=True)
            self.block((side * edge, 6.5, 0), (1.2, 1.6, length - 3), "strap", owner)
        for x in np.linspace(-edge, edge, 5)[1:-1]:
            self.block((x, 7.0, -(length - 3) / 2), (2.4, 10, 2.4), "wood", owner)
        self.block((0, 11.0, -(length - 3) / 2), (width - 3, 2, 2), "wood", owner, dark=True)
        if cargo:
            for x, z, s in ((-0.24, -0.22, 14), (0.2, -0.3, 11), (0.27, -0.02, 9), (-0.26, 0.08, 10)):
                self.block((x * width, 2 + s / 2, z * length), (s, s, s), "crate", owner)
            self.block((-0.02 * width, 6, -0.36 * length), (20, 8, 8), "roll", owner)
        self.mount()
        for side in (-1, 1):  # legs down to the flank, braced against it
            for dz in (-0.42, 0.42):
                z = zc + dz * length
                flank, _ = self.body.point(z, side * 88.0)
                self.along((side * edge, flank[1], z), (side * edge, y, z), (3, 3), "wood", owner, dark=True)
                self.along((side * edge, flank[1] + 2, z), (side * (abs(flank[0]) - 1), flank[1] + 2, z),
                           (2.4, 2.4), "wood", owner, hint=(0, 0, 1), dark=True)
        return self.chair_seat(zc + length / 2 - 12.0, 0.0, owner, base=y + 2.0)

    def rider(self, seat):
        s = RIDER
        up, fwd, right = np.array([0, 1, 0.0]), np.array([0, 0, 1.0]), np.array([1, 0, 0.0])
        self.block(seat + up * 6 * s, np.array([8, 12, 4]) * s, "rider", None)
        self.block(seat + up * 16 * s, np.array([8, 8, 8]) * s, "rider", None)
        for side in (-1, 1):
            hip = seat + right * side * 2 * s + up * 1.5 * s
            leg = unit(fwd * math.cos(0.16) - up * math.sin(0.16) + right * side * 0.32)
            self.along(hip, hip + leg * 12 * s, (4 * s, 4 * s), "rider", None, hint=right)
            shoulder = seat + right * side * 6 * s + up * 11 * s
            arm = unit(-up * math.cos(0.63) + fwd * math.sin(0.63))
            self.along(shoulder, shoulder + arm * 12 * s, (4 * s, 4 * s), "rider", None, hint=right)


def texel_density(body):
    values = []
    for cube in body.cubes:
        entry = (cube["source_uv"] or {}).get("north")
        if isinstance(entry, dict) and cube["size"][0] > 1 and cube["size"][1] > 1:
            values.append(abs(entry["uv_size"][0]) / cube["size"][0])
    return float(np.median(values)) if values else 1.0


def build(sid, tier=None, cargo="default", rider=True):
    """Fit the kit to one species: the body, the boxes and the measurements used."""
    spec = SPECIES[sid]
    tier = tier or spec["tier"]
    cargo = spec.get("cargo") if cargo == "default" else cargo
    body = Body(sid)
    kit = Kit(body, tier)
    zs = float(next(c["bone_world"][2] for c in body.cubes if c["bone"] == spec["seat"]))
    top, bottom, _ = body.span(zs)
    height, length = top - bottom, float(body.hi[2] - body.lo[2])
    thigh = max(abs(body.point(zs, t)[0][0]) for t in (40, 55, 70))
    kind = spec.get("kind") or ("straddle" if thigh <= 9.5 else "chair")
    seat_back, seat_front = (8.0, 8.0) if kind == "straddle" else (14.0, 15.0)
    if kind != "platform":  # sit behind whatever of the head hangs over the back
        zs = min(zs, body.head_back(top - 2.0) - seat_front)
    density = float(np.clip(texel_density(body), 0.6, 1.0))
    thick = float(np.clip(0.02 * height, 0.8, 2.2))
    strap = float(np.clip(0.05 * length, 2.0, 7.0))
    size = float(np.clip(0.2 * height, 5.0, 15.0)) * (1.25 if cargo == "reinforced" else 1.0)
    drop = spec.get("drop", 62.0 if height < 60 else 52.0)
    lift = 2 * thick + 0.6
    rear_end, front_end = float(body.lo[2]) + strap, float(body.hi[2]) - strap
    hind = max(rear_end, body.hind_front(strap))

    if kind == "platform":
        deck_l = 0.56 * length
        deck_w = 2.1 * max(abs(body.point(zs + d, 90.0)[0][0]) for d in (-20, 0, 20))
        z0, z1 = zs - 0.46 * deck_l, zs + 0.46 * deck_l
        girths = [g for g in (body.clear(zs + 0.34 * deck_l, strap, max(zs, hind), front_end),
                              body.clear(zs - 0.34 * deck_l, strap, rear_end, zs)) if g]
    else:
        back = max(seat_back + 2, 0.15 * length) + (1.5 * size if cargo else 0) \
            + (1.3 * size if cargo == "reinforced" else 0)
        z0, z1 = max(rear_end, zs - back), min(front_end, zs + max(seat_front + 1, 0.11 * length))
        girths = [g for g in (body.clear(z1 - strap, strap, max(hind, zs - 2), max(hind, z1 - strap / 2)),
                              body.clear(z0 + strap, strap, max(hind, z0 + strap / 2), zs)
                              if zs > max(hind, z0 + strap / 2) else None) if g]
    # a second girth only where nothing is in its way and it is not on top of the first
    girths = girths[:1] + [g for g in girths[1:] if g[1] == 0 and girths[0][0] - g[0] >= 2 * strap]
    z0 = min([z0] + [g[0] - strap for g in girths])
    z1 = max([z1] + [g[0] + strap for g in girths])
    for z, _ in girths:
        kit.ring(z, strap, thick, 0.3, buckle=96.0)
    kit.pad(z0, z1, 70.0 if kind == "platform" else drop, thick, thick + 0.6, density,
            rows=int(np.clip(round((z1 - z0) / max(9.0, 0.13 * length)), 1, 4)))
    if kind == "platform":
        seat, owner = kit.platform(zs, deck_l, deck_w, lift, body.span(zs)[2], cargo)
    else:
        seat, owner = kit.straddle_seat(zs, lift) if kind == "straddle" else kit.chair_seat(zs, lift)
        if cargo:
            behind = zs - seat_back - 0.8 * size
            kit.bags(behind, size, lift + 0.3, owner, count=2 if cargo == "reinforced" else 1)
            tops = [h[0] for h in (body.span(behind + d, 0.05) for d in (-0.2 * size, 0.2 * size, 0.6 * size)) if h]
            kit.block((0, max(tops) + lift + 0.36 * size, behind + 0.2 * size),
                      (max(12.0, 1.5 * thigh), 0.72 * size, 0.72 * size), "roll", owner)
    info = dict(kind=kind, tier=tier, cargo=cargo, boxes=len(kit.boxes), density=density, seat_owner=owner,
                seat_z=round(zs, 1), girths=[(round(z - zs, 1), cost) for z, cost in girths],
                pad=(round(z0 - zs, 1), round(z1 - zs, 1)))
    if rider:
        kit.rider(seat)
    return body, kit, info


# --- texture -------------------------------------------------------------------------------------------

FACE_DIMS = {"north": (0, 1), "south": (0, 1), "west": (2, 1), "east": (2, 1), "up": (0, 2), "down": (0, 2)}


def face_unit(key, s, t):
    """Box-local unit position of each texel of a face, the inverse of the studio's corner mapping."""
    zero, one = np.zeros_like(s), np.ones_like(s)
    return np.stack({"north": (1 - s, 1 - t, zero), "south": (s, 1 - t, one), "west": (zero, 1 - t, s),
                     "east": (one, 1 - t, 1 - s), "up": (s, one, t), "down": (s, zero, 1 - t)}[key], -1)


def paint(box, key, q, rng, palette, tier):
    h, w = q.shape[:2]
    grain = rng.random((h, w))
    rows, cols = np.mgrid[0:h, 0:w]
    edge = (rows == 0) | (rows == h - 1) | (cols == 0) | (cols == w - 1) if h > 2 and w > 2 else np.zeros((h, w), bool)

    def solid(color, amount=0.12):
        return np.asarray(color, float)[None, None, :] * (1 - amount / 2 + amount * grain)[..., None]

    mat = box["mat"]
    if mat == "pad":
        if key != "up":
            return solid(palette["trim"])
        image = solid(palette["pad"])
        image[grain > 0.74] = np.asarray(palette["pad2"], float)
        a = box["a0"] + (box["a1"] - box["a0"]) * q[..., 0]
        b = box["b0"] + (box["b1"] - box["b0"]) * q[..., 2]
        along, across = b * box["len_px"], a * box["arc_px"]
        inset = np.minimum((1 - np.abs(a)) * box["arc_px"], np.minimum(b, 1 - b) * box["len_px"])
        if tier == "hide":  # a fur fringe, uneven
            fringe = inset < 1.2 + 1.6 * grain
            image[fringe] = solid(palette["trim"], 0.2)[fringe]
        elif tier == "bronze":  # dark border, gold piping, a woven line inside it
            image[inset < 2] = solid(palette["trim"])[inset < 2]
            image[(inset >= 2) & (inset < 3)] = np.asarray(palette["accent"], float)
            weave = (inset >= 5) & (inset < 6) & ((np.floor(along) + np.floor(across)) % 2 == 0)
            image[weave] = np.asarray(palette["accent"], float)
        else:  # riveted plate along the edge
            plate = inset < 2.5
            image[plate] = solid(palette["metal"], 0.06)[plate]
            image[plate & (inset > 0.7) & (inset < 1.8) & (np.floor(along + across) % 4 == 0)] = palette["metal_lo"]
            image[(inset >= 2.5) & (inset < 3.5)] = np.asarray(palette["trim"], float)
        return image
    if mat == "strap":
        image = solid(palette["strap"], 0.1)
        if key == "up" and h >= 3:
            image[((rows == 0) | (rows == h - 1)) & (cols % 2 == 0)] = np.asarray(palette["stitch"], float)
        if key == "up" and box.get("buckle"):
            c0 = max(0, w // 2 - h // 2)
            image[:, c0:c0 + h] = solid(palette["metal"], 0.05)[:, c0:c0 + h]
            if h >= 4:
                image[1:h - 1, c0 + 1:c0 + h - 1] = np.asarray(palette["metal_lo"], float) * 0.7
        return image
    if mat == "leather":
        image = solid(palette["seat"], 0.1)
        image[edge] *= 0.8
        if key == "up":
            image[~edge] *= 1.1
        return image
    if mat == "metal":
        image = solid(palette["metal"], 0.05)
        image[(rows == h - 1) | (cols == w - 1)] = np.asarray(palette["metal_lo"], float)
        image[(rows == 0) | (cols == 0)] *= 1.1
        return image
    if mat in ("wood", "crate"):
        image = solid(palette["wood"], 0.14) * (0.72 if box.get("dark") else 1.0)
        across, along = (rows, cols) if w >= h else (cols, rows)
        board = across // 4
        image[across % 4 == 3] *= 0.72
        image[(along + board * 5) % 13 == 0] *= 0.78
        image *= (0.94 + 0.12 * ((board % 3) / 2.0))[..., None]
        if mat == "crate":
            frame = (rows < 2) | (rows >= h - 2) | (cols < 2) | (cols >= w - 2)
            image[frame] = (solid(palette["wood"], 0.1) * 0.66)[frame]
            image[(np.abs(rows * (w - 1) - cols * (h - 1)) < max(w, h)) & ~frame] *= 0.8
        else:
            image[edge] *= 0.82
        return image
    if mat == "bag":
        image = solid(palette["bag"], 0.14)
        image[edge] *= 0.8
        if key not in ("up", "down"):
            flap = max(1, int(round(0.45 * h)))
            image[:flap] *= 0.86
            image[flap:flap + 1] *= 0.62
            if key == "south":
                for c in {w // 3, w - 1 - w // 3}:
                    image[max(0, flap - 2):min(h, flap + 2), c:c + 1] = np.asarray(palette["strap"], float)
                    image[flap:flap + 1, c:c + 1] = np.asarray(palette["metal"], float)
        return image
    if mat == "roll":
        image = solid(palette["roll"], 0.16)
        if key in ("west", "east"):
            image[np.minimum(np.minimum(rows, h - 1 - rows), np.minimum(cols, w - 1 - cols)) % 2 == 1] *= 0.74
        else:
            image[rows % 3 == 2] *= 0.86
            band = (np.abs(cols - 0.22 * w) < max(1, 0.05 * w)) | (np.abs(cols - 0.78 * w) < max(1, 0.05 * w))
            image[band] = solid(palette["strap"], 0.1)[band]
        return image
    image = solid(RIDER_COLOR, 0.04)
    image[edge] *= 0.84
    return image


def texture(kit, density, offset):
    """Paint every box face into an atlas: the atlas and, per box, the face UVs the studio renderer reads."""
    palette = TIERS[kit.tier]
    faces = []
    for index, box in enumerate(kit.boxes):
        for slot, (key, (ax_s, ax_t)) in enumerate(FACE_DIMS.items()):
            w = max(1, int(round(2 * box["half"][ax_s] * density)))
            h = max(1, int(round(2 * box["half"][ax_t] * density)))
            t, s = np.meshgrid((np.arange(h) + 0.5) / h, (np.arange(w) + 0.5) / w, indexing="ij")
            image = paint(box, key, face_unit(key, s, t), np.random.default_rng(index * 6 + slot), palette, kit.tier)
            faces.append((index, key, np.clip(image, 0, 255)))
    width, x, y, shelf, spots = 256, 0, 0, 0, {}
    for i in sorted(range(len(faces)), key=lambda i: -faces[i][2].shape[0]):
        h, w = faces[i][2].shape[:2]
        if x + w + 2 > width:
            x, y, shelf = 0, y + shelf, 0
        spots[i] = (x + 1, y + 1)
        x, shelf = x + w + 2, max(shelf, h + 2)
    atlas = np.zeros((y + shelf, width, 3), np.uint8)
    uvs = [{} for _ in kit.boxes]
    for i, (index, key, image) in enumerate(faces):
        px, py = spots[i]
        h, w = image.shape[:2]
        atlas[py - 1:py + h + 1, px - 1:px + w + 1] = np.pad(image, ((1, 1), (1, 1), (0, 0)), mode="edge")
        uvs[index][key] = [px - 0.5, py - 0.5 + offset, w, h]
    return atlas, uvs


# --- pictures ------------------------------------------------------------------------------------------

CAMS = {
    "front": ss.R.from_euler("yz", [-36, 16], degrees=True).as_matrix(),
    "rear": ss.R.from_euler("yz", [52, 26], degrees=True).as_matrix(),
    "side": np.eye(3),
    "top": ss.R.from_euler("yz", [-12, 66], degrees=True).as_matrix(),
}


def clip_of(sid, word):
    clips = json.loads((ASSETS / f"geckolib/animations/entity/{sid}.animation.json").read_text("utf-8"))["animations"]
    name = next((n for n in clips if word.lower() in n.lower()), None)
    return (name, clips[name]) if name else (None, None)


def scene(sid, tier=None, cargo="default", rider=True, clip=None, time=0.0):
    """Everything the renderer needs: cubes with UVs, the joined texture, the saddle's corners, the facts."""
    body, kit, info = build(sid, tier, cargo, rider)
    skin = Image.open(ASSETS / f"textures/entity/{sid}_{SPECIES[sid]['skin']}.png").convert("RGB")
    atlas, uvs = texture(kit, info["density"], skin.height)
    sheet = Image.new("RGB", (max(skin.width, atlas.shape[1]), skin.height + atlas.shape[0]))
    sheet.paste(skin, (0, 0))
    sheet.paste(Image.fromarray(atlas), (0, skin.height))
    body_corners = body.corners
    kit_corners = [box["centre"] + ((ss.CORNERS * 2 - 1) * box["half"]) @ box["axes"] for box in kit.boxes]
    if clip:
        # the kit rides the bones: each box is carried by the bone of the cube under it
        extra = []
        for box, corners in zip(kit.boxes, kit_corners):
            cube = body.cubes[box["owner"] if box["owner"] is not None else info["seat_owner"]]
            extra.append({"bone_index": cube["bone_index"],
                          "model_corners": (corners - cube["bone_world"]) @ cube["bone_matrix"] + cube["bone_pivot"]})
        posed = ss.posed_corners(body.cubes + extra, body.skeleton, clip.get("bones", {}), time)
        body_corners, kit_corners = posed[:len(body.cubes)], posed[len(body.cubes):]
    decoded = [{"corners": body_corners[i], "faces": c["source_uv"]} for i, c in enumerate(body.cubes)]
    decoded += [{"corners": kit_corners[i], "faces": uvs[i]} for i in range(len(kit.boxes))]
    saddle = np.concatenate([c for c, box in zip(kit_corners, kit.boxes) if box["mat"] != "rider"])
    return decoded, sheet, saddle, info, atlas


def caption(image, title, subtitle=""):
    draw = ImageDraw.Draw(image)
    draw.text((26, 18), title, fill="#e9eddf", font=ss.font(26))
    draw.text((27, 54), subtitle, fill="#95a9ac", font=ss.font(15))
    return image


def close_up(decoded, sheet, saddle, cam, size, room=1.12):
    """Frame the saddle instead of the whole animal."""
    everything = np.concatenate([d["corners"] for d in decoded]) @ cam.T
    part = saddle @ cam.T
    spread = lambda p: max(np.ptp(p[:, 2]) / (size[0] - 100), np.ptp(p[:, 1]) / (size[1] - 100))
    zoom = spread(everything) / (spread(part) * room)
    return ss.render(decoded, sheet, cam, size, focus=(saddle.min(0) + saddle.max(0)) / 2, zoom=max(1.0, zoom),
                     margin=50)


def describe(info):
    cargo = f", {info['cargo']} harness" if info["cargo"] else ""
    return f"{info['kind']} seat, {info['tier']} tier{cargo}  |  {info['boxes']} boxes"


def species_sheet(sid):
    decoded, sheet, saddle, info, atlas = scene(sid)
    tile = (1000, 700)
    room = 1.6 if info["kind"] == "straddle" else 1.12  # a small saddle: leave the rider in the frame
    tiles = [caption(ss.render(decoded, sheet, CAMS["front"], tile, margin=70), sid.capitalize(),
                     describe(info) + "  |  grey figure: a player, for scale"),
             caption(close_up(decoded, sheet, saddle, CAMS["rear"], tile, room), "Saddle, from behind")]
    name, clip = clip_of(sid, SPECIES[sid].get("clip", "Move-Fwd"))
    if clip:
        walking = scene(sid, clip=clip, time=0.3 * float(clip.get("animation_length", 1.0)))
        tiles.append(caption(ss.render(walking[0], walking[1], CAMS["side"], tile, margin=70), "In motion, mid-clip",
                             f"{name}: every box rides the bone of the cube under it"))
    else:
        tiles.append(caption(ss.render(decoded, sheet, CAMS["side"], tile, margin=70), "Side"))
    tiles.append(caption(close_up(decoded, sheet, saddle, CAMS["top"], tile, room), "Saddle, from above"))
    page = Image.new("RGB", (2000, 1400))
    for index, image in enumerate(tiles):
        page.paste(image, (index % 2 * 1000, index // 2 * 700))
    page.save(OUT / f"{sid}.png")
    Image.fromarray(atlas).resize((atlas.shape[1] * 3, atlas.shape[0] * 3), Image.NEAREST).save(OUT / f"{sid}_atlas.png")
    return info, tiles[0]


def tier_sheet(sid="triceratops"):
    """One animal, the three material tiers, each with a different harness."""
    tile = (1000, 700)
    page = Image.new("RGB", (3000, 700))
    for index, (tier, cargo) in enumerate((("hide", None), ("bronze", "pack"), ("steel", "reinforced"))):
        decoded, sheet, saddle, info, _ = scene(sid, tier, cargo, rider=False)
        page.paste(caption(close_up(decoded, sheet, saddle, CAMS["rear"], tile, room=1.05),
                           f"{tier.capitalize()} tier", describe(info)), (index * 1000, 0))
    page.save(OUT / "tiers.png")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--species", nargs="*", default=list(SPECIES))
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    fronts = []
    for sid in args.species:
        info, front = species_sheet(sid)
        fronts.append(front)
        print(sid, {k: v for k, v in info.items() if k != "seat_owner"})
    if args.species == list(SPECIES):
        tier_sheet()
        page = Image.new("RGB", (3000, 1400), (19, 30, 39))
        for index, front in enumerate(fronts):
            page.paste(front, (index % 3 * 1000, index // 3 * 700))
        page.save(OUT / "overview.png")


if __name__ == "__main__":
    main()
