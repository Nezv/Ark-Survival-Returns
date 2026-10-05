"""Concept preview of creature saddles, for every species.

Fits one parametric saddle kit to each runtime creature model and renders it over the creature's real skin.
The trunk is measured station by station; every strap and pad plate is laid tangent to that outline and is
owned by the bone of the cube under it, so the same boxes follow the animation. The seat is chosen from the
measured back: straddle where a player's legs fit round it, a framed chair where it is too wide, a platform
for the largest, and nothing where the animal is smaller than the seat.

Nothing here ships: the boxes are drawn, not written as geometry. Output: Scratch/saddles/*.png.

    python Ark/tools/preview_saddles.py [--species tyrannosaurus triceratops]
"""
import argparse
import json
import math
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
import skin_studio as ss  # noqa: E402
from build_taming_manifests import BODY_PLAN  # noqa: E402

ASSETS = ROOT / "Ark/src/main/resources/assets/arksurvivalreturns"
JAVA = ROOT / "Ark/src/main/java/dev/nez/arksurvivalreturns"
OUT = ROOT / "Scratch/saddles"
RIDER = 0.9375  # the player model is drawn at 15/16

FAMILIES = {
    "theropods": ("bipedCarnivore", "bipedHerbivore"),
    "quadrupeds": ("quadruped", "quadrupedHerbivore", "horned", "armored"),
    "giants": ("sauropod",),
    "flyers": ("flyer",),
    "swimmers": ("longSwimmer", "broadSwimmer", "radial"),
}
# What the measurements cannot say: the animals that carry a deck, and the looks already agreed.
OVERRIDES = {
    "velociraptor": dict(skin="midnight"),
    "pteranodon": dict(skin="burgundy"),
    "triceratops": dict(skin="ivory"),
    "tyrannosaurus": dict(skin="burgundy"),
    "brontosaurus": dict(skin="ivory", tier="bronze", kind="platform"),
    "titanosaur": dict(skin="midnight", tier="bronze", kind="platform"),
    "quetzal": dict(skin="ivory", tier="bronze", kind="platform"),
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
SKINS = {"hide": ("midnight", "burgundy"), "bronze": ("ivory", "midnight"), "steel": ("burgundy", "ivory")}
RIDER_COLOR = (150, 162, 174)
THIN = 2.0  # half-width under which a low crest is ignored: the saddle sits over it
EDGES = np.array([(0, 1), (1, 2), (2, 3), (3, 0), (4, 5), (5, 6), (6, 7), (7, 4), (0, 4), (1, 5), (2, 6), (3, 7)])


def catalog():
    """Every species with what the game already says about it: seat bone, body plan, harness."""
    ids = dict(re.findall(r'^\s+([A-Z_]+)\("([a-z_]+)"', (JAVA / "feature/creature/Species.java").read_text("utf-8"),
                          re.M))
    seats = re.findall(r'Species\.([A-Z_]+), new CreatureRideProfile\([^"]*"([^"]+)"',
                       (JAVA / "feature/taming/CreatureSeats.java").read_text("utf-8"))
    harness = {}
    for tier, capacity, names in re.findall(r"put\(Harness\.(\w+), (\d+),([^;]+)\);",
                                            (JAVA / "feature/cargo/CargoProfiles.java").read_text("utf-8")):
        for name in re.findall(r"Species\.([A-Z_]+)", names):
            harness[name] = (tier.lower(), int(capacity))
    species = {}
    for name, bone in seats:
        sid = ids[name]
        cargo, capacity = harness.get(name, ("pack", 150))
        # light animals are shown bare of cargo; the rest carry the harness the game asks of them
        tier = "hide" if capacity <= 150 else "bronze" if cargo == "pack" else "steel"
        spec = dict(seat=bone, plan=BODY_PLAN[sid], tier=tier, cargo=cargo if capacity >= 200 else None)
        spec.update(OVERRIDES.get(sid, {}))
        spec.setdefault("skin", SKINS[spec["tier"]][sum(map(ord, sid)) % 2])
        species[sid] = spec
    return species


SPECIES = catalog()


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


def extents(points, crossing):
    """Per cube, the box of its slice: x0, x1, y0, y1 (infinite where the cube does not cross)."""
    x, y = points[..., 0], points[..., 1]
    return (np.where(crossing, x, np.inf).min(1), np.where(crossing, x, -np.inf).max(1),
            np.where(crossing, y, np.inf).min(1), np.where(crossing, y, -np.inf).max(1))


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
    """The creature at rest: all cubes, the central trunk, the limbs against it and what stands on its back."""

    def __init__(self, sid):
        self.sid = sid
        _, self.cubes, self.skeleton = ss.load_cubes(ASSETS / f"geckolib/models/entity/{sid}.geo.json")
        self.corners = np.array([ss.cube_corners(c) for c in self.cubes])
        sided = np.array([bool(ss.side_of(c["bone"])) for c in self.cubes])
        part = np.array([c["part"] for c in self.cubes])
        central = np.isin(part, ("torso", "neck", "tail")) & ~sided
        torso = central & (part == "torso")
        if not torso.any():  # squid, jellyfish: no spine names, the unsided body bones are the trunk
            central = torso = ~sided & np.array([bool(re.search("body|main", c["bone"], re.I)) for c in self.cubes])
        self.trunk = np.flatnonzero(central)
        self.is_torso = torso[self.trunk]
        self.extra = np.flatnonzero(~central & ~sided)
        self.limbs = np.flatnonzero(sided & ~np.isin(part, ("head", "jaw", "eye", "horn")))
        self.wrap = np.flatnonzero(sided & ~np.isin(part, ("head", "jaw", "eye", "horn", "feather")))
        # hind legs: rigs name them leg, foot or "back" (which reads as torso), never arm
        self.hind = np.flatnonzero(sided & np.isin(part, ("torso", "leg", "legl", "legr", "foot", "footl", "footr")))
        box = self.corners[self.trunk]
        edges = np.stack([box[:, 1] - box[:, 0], box[:, 3] - box[:, 0], box[:, 4] - box[:, 0]], 1)
        length = np.linalg.norm(edges, axis=2)
        axes = edges / np.maximum(length, 1e-9)[..., None]
        for k in range(3):  # a flat plane has no third edge: take it from the other two
            flat = length[:, k] < 1e-6
            axes[flat, k] = np.cross(axes[flat, (k + 1) % 3], axes[flat, (k + 2) % 3])
        self.centre, self.axes, self.half = box.mean(1), axes, np.maximum(length / 2, 1e-4)
        points = self.corners[torso].reshape(-1, 3)
        self.lo, self.hi = points.min(0), points.max(0)
        self.length = float(self.hi[2] - self.lo[2])
        self.width = float(np.abs(points[:, 0]).max())
        self.step = max(0.5, self.length / 160)
        self._section, self._point, self._crest = {}, {}, {}

    def grid(self, lo, hi):
        """Stations between two ends, on one grid so that their sections are measured once."""
        first, last = math.ceil(lo / self.step), math.floor(hi / self.step)
        return [k * self.step for k in range(first, last + 1)]

    def section(self, z):
        """The trunk at this station: its taut outline (a convex hull, as a strap pulled tight would lie),
        its span, the cubes in it and the ridge standing on it.

        The trunk cubes are a shell in places and the shoulders and hips fill its flanks, so the upper half
        of whatever limb crosses the station is wrapped in too. A sail, a fin or a row of tall spines is not
        part of the outline: it is a ridge the saddle has to go round.
        """
        key = round(float(z), 3)
        if key in self._section:
            return self._section[key]
        result = None
        points, crossing = slices(self.corners[self.trunk], z)
        has = crossing.any(1)
        if has.any():
            x0, x1, y0, y1 = extents(points, crossing)
            half = np.maximum(-x0, x1)
            core = has & self.is_torso
            member = has.copy()
            if core.any():  # antlers and a neck held over the back cross the station without being trunk here
                top, bottom = y1[core].max(), y0[core].min()
                slack = 0.15 * (top - bottom)
                member = has & (core | ((half <= 1.3 * self.width) & (y0 <= top + slack) & (y1 >= bottom - slack)))
            widest = half[member].max()
            wide = member & (half >= 0.45 * widest)
            top = y1[wide].max()
            # what stands narrow on the wide body; a fin a couple of pixels thick is decoration, not a ridge
            crest = member & ~wide & (y1 > top + 3) & (half > THIN)
            other, other_crossing = slices(self.corners[self.extra], z)
            ox0, ox1, oy0, oy1 = extents(other, other_crossing)
            other_half = np.maximum(-ox0, ox1)
            fin = other_crossing.any(1) & (other_half < 0.45 * widest) & (oy0 < top + 3) & (oy1 > top) \
                & ((other_half > THIN) | (oy1 > top + 12))
            heights = np.concatenate([y1[crest], oy1[fin]])
            widths = np.concatenate([half[crest], other_half[fin]])
            ridge = None
            # a hump as wide as it is tall can be sat on; a sail or a fin cannot
            if len(heights) and heights.max() - top > 7 \
                    and (heights.max() - top > 2.5 * widths.max() or heights.max() - top > 20):
                member &= ~crest
                ridge = float(widths.max())
            trunk = points[member][crossing[member]]
            lo, hi = float(trunk[:, 1].min()), float(trunk[:, 1].max())
            limbs, limb_crossing = slices(self.corners[self.wrap], z)
            limbs = limbs[limb_crossing]
            reach = 1.35 * np.abs(trunk[:, 0]).max()
            limbs = limbs[(limbs[:, 1] > (lo + hi) / 2) & (limbs[:, 1] < hi) & (np.abs(limbs[:, 0]) < reach)]
            both = np.vstack([trunk, limbs])
            both = np.vstack([both, both * [-1, 1]])  # the models are symmetric; keep the outline so
            result = dict(hull=convex_hull(both), centre=np.array([0.0, (lo + hi) / 2]), lo=lo, hi=hi,
                          members=self.trunk[member], ridge=ridge)
        self._section[key] = result
        return result

    def nearest(self, p):
        local = np.einsum("nij,nj->ni", self.axes, p - self.centre)
        return int(self.trunk[np.argmin(np.linalg.norm(np.maximum(np.abs(local) - self.half, 0), axis=1))])

    def point(self, z, theta):
        """Outline point at this station, theta degrees round from the top (positive towards +x), and the
        trunk cube nearest to it."""
        key = (round(float(z), 3), round(theta, 1))
        if key not in self._point:
            section = self.section(z)
            hull, centre = section["hull"], section["centre"]
            d = np.array([math.sin(math.radians(theta)), math.cos(math.radians(theta))])
            e = np.roll(hull, -1, axis=0) - hull
            den = d[0] * e[:, 1] - d[1] * e[:, 0]
            den = np.where(np.abs(den) < 1e-12, 1e-12, den)
            t = ((hull[:, 0] - centre[0]) * e[:, 1] - (hull[:, 1] - centre[1]) * e[:, 0]) / den
            u = ((hull[:, 0] - centre[0]) * d[1] - (hull[:, 1] - centre[1]) * d[0]) / den
            ok = (t > 0) & (u >= -1e-6) & (u <= 1 + 1e-6)
            r = t[ok].max() if ok.any() else 0.0
            p = np.array([centre[0] + r * d[0], centre[1] + r * d[1], z])
            self._point[key] = (p, self.nearest(p))
        return self._point[key]

    def top(self, z, x=0.0):
        """Height of the outline over this point, or None off the trunk."""
        section = self.section(z)
        if section is None:
            return None
        hull = section["hull"]
        a, b = hull, np.roll(hull, -1, axis=0)
        spans = (np.minimum(a[:, 0], b[:, 0]) <= x) & (np.maximum(a[:, 0], b[:, 0]) >= x) & (a[:, 0] != b[:, 0])
        if not spans.any():
            return None
        t = (x - a[spans, 0]) / (b[spans, 0] - a[spans, 0])
        return float((a[spans, 1] + t * (b[spans, 1] - a[spans, 1])).max())

    def ridge_angle(self, z0, z1):
        """How far round from the top a sail or fin keeps the saddle off the back between two stations."""
        angle = 0.0
        for z in np.linspace(z0, z1, 3):
            section = self.section(z)
            if section and section["ridge"]:
                for theta in range(2, 88, 2):
                    if abs(self.point(z, theta)[0][0]) >= section["ridge"] + 1.5:
                        break
                angle = max(angle, float(theta))
        return angle

    def crest(self, z):
        """How high anything stands in the rider's place over this station: head, frill, antler, sail, fin."""
        key = round(float(z), 3)
        if key not in self._crest:
            section = self.section(z)
            if section is None:
                self._crest[key] = np.inf
            else:
                points, crossing = slices(self.corners, z)
                x0, x1, y0, y1 = extents(points, crossing)
                has = crossing.any(1)
                has[section["members"]] = False
                hi = section["hi"]
                block = has & (x0 < 5) & (x1 > -5) & (y1 > hi + 2.5) & (y0 < hi + 32) \
                    & ((x1 - x0 > 2 * THIN) | (y1 > hi + 12))
                self._crest[key] = float(y1[block].max() - hi) if block.any() else 0.0
        return self._crest[key]

    def seat_station(self, nominal, back, front):
        """The station nearest the game's seat where the rider has room: (station, what is still in the way)."""
        best = None
        for z in self.grid(float(self.lo[2]) + back, float(self.hi[2]) + 0.15 * self.length):
            worst = max(self.crest(s) for s in self.grid(z - back, z + front))
            key = (max(worst, 2.5), abs(z - nominal))
            if best is None or key < best[0]:
                best = (key, z, worst)
        return best[1], best[2]

    def cost(self, z, width, cubes):
        """How many of these limb cubes lie against the flank here, where a girth would have to pass."""
        section = self.section(z)
        if section is None:
            return None
        lo, hi = section["lo"], section["hi"]
        hit = np.zeros(len(cubes), bool)
        for plane in (z - width / 2, z, z + width / 2):
            points, crossing = slices(self.corners[cubes], plane)
            hit |= (crossing & (points[..., 1] > lo + 0.3 * (hi - lo)) & (points[..., 1] < hi)).any(1)
        return int(hit.sum())

    def clear(self, z, width, lo, hi):
        """The station in [lo, hi] with the fewest limbs in the way, nearest to z: (station, limbs)."""
        best = None
        for station in self.grid(lo, hi):
            cost = self.cost(station, width, self.limbs)
            if cost is not None and (best is None or (cost, abs(station - z)) < (best[1], abs(best[0] - z))):
                best = (float(station), cost)
        return best

    def hind_front(self, width):
        """Front edge of the hind legs: a girth further back would sit on the rump, holding nothing."""
        lo = float(self.lo[2])
        blocked = [z for z in self.grid(lo + width, lo + 0.6 * self.length) if self.cost(z, width, self.hind)]
        return max(blocked) + self.step if blocked else lo


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
        """A strap round the trunk, or part of the way; it stops either side of a sail or fin."""
        bars = [(t0, t1)]
        gap = self.body.ridge_angle(z - width / 2, z + width / 2)
        if gap and t0 < gap and t1 > -gap:
            bars = [(a, b) for a, b in ((t0, min(t1, -gap)), (max(t0, gap), t1)) if b - a > 4]
        for a, b in bars:
            edges = np.linspace(a, b, max(1, round((b - a) / step)) + 1)
            for e0, e1 in zip(edges[:-1], edges[1:]):
                self.plate(z - width / 2, z + width / 2, e0, e1, thick, "strap", lift,
                           buckle=buckle is not None and e0 <= buckle < e1)

    def pad(self, z0, z1, drop, thick, lift, density, rows, step=21.0):
        """The blanket: rows of plates over the back, or two side panels where a sail or fin is in the way."""
        cuts = np.linspace(z0, z1, rows + 1)
        outline = np.array([self.body.point((z0 + z1) / 2, t)[0] for t in np.linspace(0, drop, 9)])
        arc_px = np.linalg.norm(np.diff(outline, axis=0), axis=1).sum() * density
        for row in range(rows):
            gap = self.body.ridge_angle(cuts[row], cuts[row + 1])
            reach = max(drop, gap + 40.0) if gap else drop
            panels = [(-reach, reach)] if not gap else [(-reach, -gap), (gap, reach)]
            for lo, hi in panels:
                edges = np.linspace(lo, hi, max(1 if gap else 2, round((hi - lo) / step)) + 1)
                for a, b in zip(edges[:-1], edges[1:]):
                    self.plate(cuts[row], cuts[row + 1], a, b, thick, "pad", lift, a0=a / reach, a1=b / reach,
                               b0=row / rows, b1=(row + 1) / rows, arc_px=arc_px, len_px=(z1 - z0) * density)

    def rest(self, zs, reach, half_width, limit):
        """Where a seat this long rests on the back: height at zs, lean, the gap left under it, the cube."""
        stations, tops = [], []
        for z in np.linspace(zs - reach, zs + reach, 9):
            hits = [h for h in (self.body.top(z, x) for x in (0.0, -half_width, half_width)) if h is not None]
            if hits:
                stations.append(z - zs)
                tops.append(max(hits))
        stations, tops = np.array(stations), np.array(tops)
        lean = float(np.clip(math.atan(np.polyfit(stations, tops, 1)[0]), -limit, limit)) if len(tops) > 1 else 0.0
        line = math.tan(lean) * stations
        height = float((tops - line).max())
        owner = self.body.nearest(np.array([0.0, self.body.top(zs), zs]))
        return height, lean, float((height + line - tops).max()), owner

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

    def chair_seat(self, zs, lift, frame=None, owner=None):
        """The framed seat: resting on the back, or set on a deck."""
        if frame is None:
            height, lean, gap, owner = self.rest(zs, 13.0, 9.0, 0.21)
            self.mount((0, height + lift, zs), lean)
            fill = min(gap, 10.0)
        else:
            self.mount(*frame)
            fill = 0.0
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
                if self.body.section(zk - length / 2) is None:
                    continue
                flank, _ = self.body.point(zk, side * 72.0)
                widest = max(abs(self.body.point(zk + dz, side * t)[0][0])
                             for dz in (-length / 2, 0, length / 2) for t in (72, 85, 98, 110))
                centre = (side * (widest + lift + depth / 2), flank[1] + 0.2 * height - height / 2, zk)
                self.box(centre, [(0, 0, -side), (0, 1, 0), (side, 0, 0)], (length / 2, height / 2, depth / 2),
                         "bag", owner)
                # the strap the bag hangs from, over the back
                self.ring(zk, 0.22 * size, 0.5, lift + 0.15, *sorted((side * 6.0, side * 72.0)), step=22.0)

    def platform(self, zc, length, width, lift, cargo, k):
        """A deck on legs over the back, lying along it; k scales its timbers with the animal, the seat
        stays rider-sized."""
        height, lean, _, owner = self.rest(zc, length / 2, 6.0, 0.14)
        self.mount((0, height + lift + 3.5 * k, zc), lean)
        edge = width / 2 - 1.5 * k
        for dz in (-0.42, -0.14, 0.14, 0.42):
            self.block((0, -1.6 * k, dz * length), (width - 2 * k, 3.2 * k, 3.2 * k), "wood", owner, dark=True)
        self.block((0, k, 0), (width, 2 * k, length), "wood", owner)
        for side in (-1, 1):
            for dz in (-0.5, -0.17, 0.17, 0.5):
                self.block((side * edge, 7.0 * k, dz * (length - 3 * k)), (2.4 * k, 10 * k, 2.4 * k), "wood", owner)
            self.block((side * edge, 11.0 * k, 0), (2 * k, 2 * k, length), "wood", owner, dark=True)
            self.block((side * edge, 6.5 * k, 0), (1.2 * k, 1.6 * k, length - 3 * k), "strap", owner)
        for x in np.linspace(-edge, edge, 5)[1:-1]:
            self.block((x, 7.0 * k, -(length - 3 * k) / 2), (2.4 * k, 10 * k, 2.4 * k), "wood", owner)
        self.block((0, 11.0 * k, -(length - 3 * k) / 2), (width - 3 * k, 2 * k, 2 * k), "wood", owner, dark=True)
        if cargo:
            for x, z, s in ((-0.24, -0.22, 14), (0.2, -0.3, 11), (0.27, -0.02, 9), (-0.26, 0.08, 10)):
                self.block((x * width, (2 + s / 2) * k, z * length), (s * k, s * k, s * k), "crate", owner)
            self.block((-0.02 * width, 6 * k, -0.36 * length), (20 * k, 8 * k, 8 * k), "roll", owner)
        deck, turn = self.origin, self.turn
        self.mount()
        for side in (-1, 1):  # legs down to the flank, braced against it
            for dz in (-0.42, 0.42):
                corner = deck + turn @ np.array([side * edge, 0, dz * length])
                if self.body.section(corner[2]) is None:
                    continue
                flank, _ = self.body.point(corner[2], side * 72.0)
                foot = np.array([corner[0], min(flank[1], corner[1] - 2 * k), corner[2]])
                self.along(foot, corner, (3 * k, 3 * k), "wood", owner, dark=True)
                if abs(flank[0]) < edge:
                    self.along(foot + (0, 2 * k, 0), (side * (abs(flank[0]) - 1), foot[1] + 2 * k, corner[2]),
                               (2.4 * k, 2.4 * k), "wood", owner, hint=(0, 0, 1), dark=True)
        seat = deck + turn @ np.array([0, 2.0 * k, length / 2 - 12.0 - 2 * k])
        return self.chair_seat(None, 0.0, frame=(seat, lean), owner=owner)

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
    length = body.length
    nominal = float(next(c["bone_world"][2] for c in body.cubes if c["bone"] == spec["seat"]))
    nominal = float(np.clip(nominal, body.lo[2] + 1, body.hi[2] - 1))
    girth = body.section(nominal)
    height = girth["hi"] - girth["lo"]
    thigh = max(abs(body.point(nominal, t)[0][0]) for t in (40, 55, 70))
    kind = spec.get("kind") or ("straddle" if thigh <= 9.5 else "chair")
    if kind != "platform" and (length < 12 or thigh < 2.5):
        kind = "none"
    info = dict(kind=kind, tier=tier, cargo=cargo, boxes=0, plan=spec["plan"], blocks=(round(length / 16, 1),
                round(2 * thigh / 16, 1)), density=1.0, seat_owner=None, moved=0.0, blocked=0.0, girths=[])
    if kind == "none":
        return body, kit, info
    if height >= 150:  # the skins of the giants are coarse; keep the saddle near them
        density = float(np.clip(1.5 * texel_density(body), 0.2, 0.6))
    else:
        density = float(np.clip(texel_density(body), 0.6, 1.0))
    thick = float(np.clip(0.02 * height, 0.8, 2.2))
    strap = float(np.clip(0.05 * length, 2.0, 7.0))
    size = float(np.clip(0.2 * height, 5.0, 15.0)) * (1.25 if cargo == "reinforced" else 1.0)
    drop = 48.0 if spec["plan"] == "flyer" else 62.0 if height < 60 else 52.0
    lift = 2 * thick + 0.6
    rear_end, front_end = float(body.lo[2]) + strap, float(body.hi[2]) - strap
    hind = max(rear_end, body.hind_front(strap))

    if kind == "platform":
        zs = float(body.lo[2] + body.hi[2]) / 2
        widest = max(abs(body.point(zs + d * length, 90.0)[0][0]) for d in (-0.15, 0, 0.15))
        deck_w = min((1.6 if spec["plan"] == "flyer" else 2.1) * widest, 208.0)  # a flyer's deck clears its wings
        deck_l = float(np.clip(0.56 * length, 48.0, 288.0))
        scale = float(np.clip(deck_w / 70.0, 1.0, 3.0))
        z0, z1 = max(rear_end, zs - 0.46 * deck_l), min(front_end, zs + 0.46 * deck_l)
        girths = [g for g in (body.clear(zs + 0.34 * deck_l, strap, max(zs, hind), front_end),
                              body.clear(zs - 0.34 * deck_l, strap, rear_end, zs)) if g]
    else:
        # sit where nothing stands in the rider's place: behind a frill or a crest, ahead of a sail
        room = {"straddle": (5.0, 8.0), "chair": (6.0, 13.0)}
        zs, worst = body.seat_station(nominal, *room[kind])
        wide = max(abs(body.point(zs, t)[0][0]) for t in (40, 55, 70)) > 9.5
        if not spec.get("kind") and wide != (kind == "chair"):  # the back is another width where the seat went
            kind = info["kind"] = "chair" if wide else "straddle"
            zs, worst = body.seat_station(nominal, *room[kind])
        info["moved"], info["blocked"] = round(zs - nominal, 1), round(worst, 1)
        seat_back, seat_front = (8.0, 8.0) if kind == "straddle" else (14.0, 15.0)
        back = max(seat_back + 2, 0.15 * length) + (1.5 * size if cargo else 0) \
            + (1.3 * size if cargo == "reinforced" else 0)
        z1 = min(front_end, zs + max(seat_front + 1, 0.11 * length))
        z0 = max(rear_end, min(zs - back, z1 - max(16.0, 0.25 * length)))
        ahead = max(hind, min(zs - 2, z1 - 3 * strap))
        girths = [g for g in (body.clear(z1 - strap, strap, ahead, max(ahead, z1 - strap / 2)),
                              body.clear(z0 + strap, strap, max(hind, z0 + strap / 2), min(zs, z1 - 3 * strap))
                              if min(zs, z1 - 3 * strap) > max(hind, z0 + strap / 2) else None) if g]
    # a second girth only where nothing is in its way and it is not on top of the first
    girths = girths[:1] + [g for g in girths[1:] if g[1] == 0 and girths[0][0] - g[0] >= 2 * strap]
    z0 = min([z0] + [g[0] - strap for g in girths])
    z1 = max([z1] + [g[0] + strap for g in girths])
    for z, _ in girths:
        kit.ring(z, strap, thick, 0.3, buckle=96.0)
    kit.pad(z0, z1, 70.0 if kind == "platform" else drop, thick, thick + 0.6, density,
            rows=int(np.clip(round((z1 - z0) / max(9.0, 0.13 * length)), 1, 4)))
    if kind == "platform":
        seat, owner = kit.platform(zs, deck_l, deck_w, lift, cargo, scale)
    else:
        seat, owner = kit.straddle_seat(zs, lift) if kind == "straddle" else kit.chair_seat(zs, lift)
        if cargo:
            behind = min(zs, z1) - seat_back - 0.8 * size
            kit.bags(behind, size, lift + 0.3, owner, count=2 if cargo == "reinforced" else 1)
            tops = [h for h in (body.top(behind + d) for d in (-0.2 * size, 0.2 * size, 0.6 * size)) if h is not None]
            if tops and not body.ridge_angle(behind - 0.2 * size, behind + 0.6 * size):
                kit.block((0, max(tops) + lift + 0.36 * size, behind + 0.2 * size),
                          (max(12.0, 1.5 * thigh), 0.72 * size, 0.72 * size), "roll", owner)
    info.update(boxes=len(kit.boxes), density=density, seat_owner=owner,
                girths=[(round(z - zs, 1), cost) for z, cost in girths])
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
    width = max([256] + [64 * math.ceil((f[2].shape[1] + 2) / 64) for f in faces])
    x, y, shelf, spots = 0, 0, 0, {}
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


def clip_of(sid):
    """The clip that shows the animal moving the way it is ridden."""
    clips = json.loads((ASSETS / f"geckolib/animations/entity/{sid}.animation.json").read_text("utf-8"))["animations"]
    words = ("Fly-Fwd", "Fly") if SPECIES[sid]["plan"] == "flyer" else ()
    for word in words + ("Move-Fwd", "Swim-Fwd", "Swim", "Walk", "Move"):
        name = next((n for n in clips if word.lower() in n.lower()), None)
        if name:
            return name, clips[name]
    return None, None


def scene(sid, tier=None, cargo="default", rider=True, clip=None, time=0.0):
    """Everything the renderer needs: cubes with UVs, the joined texture, the saddle's corners, the facts."""
    body, kit, info = build(sid, tier, cargo, rider)
    skin = Image.open(ASSETS / f"textures/entity/{sid}_{SPECIES[sid]['skin']}.png").convert("RGB")
    atlas, uvs = texture(kit, info["density"], skin.height) if kit.boxes else (np.zeros((1, 1, 3), np.uint8), [])
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
    saddle = [c for c, box in zip(kit_corners, kit.boxes) if box["mat"] != "rider"]
    return decoded, sheet, np.concatenate(saddle) if saddle else None, info, atlas


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
    if info["kind"] == "none":
        return f"no saddle: the trunk is {info['blocks'][0]} blocks long and {info['blocks'][1]} wide, smaller than the seat"
    cargo = f", {info['cargo']} harness" if info["cargo"] else ""
    return f"{info['kind']} seat, {info['tier']} tier{cargo}  |  {info['boxes']} boxes"


def species_sheet(sid):
    decoded, sheet, saddle, info, atlas = scene(sid)
    tile = (1000, 700)
    front = caption(ss.render(decoded, sheet, CAMS["front"], tile, margin=70), sid.capitalize(),
                    describe(info) + ("  |  grey figure: a player, for scale" if saddle is not None else ""))
    if saddle is None:
        front.save(OUT / f"{sid}.png")
        return info, front
    room = 1.6 if info["kind"] == "straddle" else 1.12  # a small saddle: leave the rider in the frame
    tiles = [front, caption(close_up(decoded, sheet, saddle, CAMS["rear"], tile, room), "Saddle, from behind")]
    name, clip = clip_of(sid)
    if clip:
        moving = scene(sid, clip=clip, time=0.3 * float(clip.get("animation_length", 1.0)))
        tiles.append(caption(ss.render(moving[0], moving[1], CAMS["side"], tile, margin=70), "In motion, mid-clip",
                             f"{name}: every box rides the bone of the cube under it"))
    else:
        tiles.append(caption(ss.render(decoded, sheet, CAMS["side"], tile, margin=70), "Side"))
    tiles.append(caption(close_up(decoded, sheet, saddle, CAMS["top"], tile, room), "Saddle, from above"))
    page = Image.new("RGB", (2000, 1400))
    for index, image in enumerate(tiles):
        page.paste(image, (index % 2 * 1000, index // 2 * 700))
    page.save(OUT / f"{sid}.png")
    return info, front


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
    fronts = {}
    for sid in args.species:
        info, fronts[sid] = species_sheet(sid)
        print(f"{sid:17s} {info['plan']:18s} {info['kind']:9s} {info['tier']:7s} {str(info['cargo']):11s} "
              f"boxes {info['boxes']:3d}  seat moved {info['moved']:6.1f}  in the way {info['blocked']:5.1f}  "
              f"girths {info['girths']}")
    if args.species == list(SPECIES):
        tier_sheet()
        for family, plans in FAMILIES.items():
            members = [sid for sid in SPECIES if SPECIES[sid]["plan"] in plans]
            page = Image.new("RGB", (3000, 700 * math.ceil(len(members) / 3)), (19, 30, 39))
            for index, sid in enumerate(members):
                page.paste(fronts[sid], (index % 3 * 1000, index // 3 * 700))
            page.save(OUT / f"overview_{family}.png")


if __name__ == "__main__":
    main()
