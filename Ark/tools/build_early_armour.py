"""Keratin and Bronze armour, using the Steel harness's native worn-model format.

Keratin: a closed dark hide hood, tapered ribbed horn face guard, broad carapace
shoulder scutes (no feathers), sewn hide sleeves/trousers and bound horn bracers. Bronze: an early metal-age open crested cap,
hammered cuirass, leather skirt tabs and strapped greaves over sandals.

Run from Ark: python tools/build_early_armour.py [keratin|bronze ...]
Writes armour/<tier>_<piece>.json, textures/entity/armour/<tier>_<piece>.png,
matching 32 px item sprites and review renders in design/armour. Like Steel,
these models are consumed by the Models showcase; game renderer wiring is separate.
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

import accessory_art as A
import build_steel_armour as S
from build_bronze_age_art import BRONZE, rgb
from build_keratin_items import STOPS

ARK = Path(__file__).resolve().parents[1]
ASSETS = ARK / 'src/main/resources/assets/arksurvivalreturns'
DESIGN = ARK / 'design/armour'
PIECES = S.PIECES
TIERS = ('keratin', 'bronze')
CORD = [(75, 52, 30), (137, 106, 61), (190, 159, 98), (222, 199, 146)]
HIDE = [(47, 29, 21), (79, 47, 31), (108, 65, 39), (144, 91, 54), (174, 121, 74)]
FUR = [(67, 45, 31), (112, 77, 46), (151, 109, 66), (185, 145, 94)]
BRONZE_RAMP = [rgb(c) for c in BRONZE]
PATINA = (71, 100, 74)
plate, sym = S.plate, S.sym


class Horn(S.Steel):
    """Warm horn, fibrous ridges, growth rings, pale worn edge; no metallic glints."""

    def __init__(self, key, base=0.65):
        super().__init__(key, base=base, curve=0.08, top=0.07, bottom=-0.14,
                         noise=0.07, streak=0.0, ramp=STOPS)

    def pattern(self, c):
        t, alpha = super().pattern(c)
        if c.face in ('north', 'south', 'west', 'east'):
            t += 0.07 * (1 - (c.j + 0.5) / c.fh)
            if c.j % 3 == 2:
                t -= 0.10
            if c.i % 3 == 1:
                t += 0.035
        return t, alpha


class Hide(S.Leather):
    def __init__(self, key, fur=False):
        super().__init__(key, ramp=FUR if fur else HIDE, base=0.58,
                         noise=0.12, streak=0.12, top=0.06, bottom=-0.10)


class Cord(A.Mat):
    def __init__(self):
        super().__init__(*CORD, light=False)

    def pattern(self, c):
        return (0.72 if (c.i + c.j) % 3 else 0.42), 255


class Bronze(S.Steel):
    """Hammered bronze with rolled edges and sparse green oxidation at the seams."""

    def __init__(self, key, base=0.60, marks=None):
        super().__init__(key, base=base, ramp=BRONZE_RAMP, marks=marks,
                         curve=0.15, top=0.13, bottom=-0.12, noise=0.025, streak=0.0)

    def pattern(self, c):
        t, a = super().pattern(c)
        if isinstance(t, tuple):
            return t, a
        h = S.hash01(self.key, c.face, c.i, c.j)
        if c.fw >= 4 and c.fh >= 3 and c.j == c.fh - 1 and h < 0.10:
            return PATINA, 255
        return t + (0.035 if h > 0.88 else -0.045 if h < 0.12 else 0), a


def strap(b, lo, hi, key):
    return plate(b, lo, hi, Hide(key))


def lashing(b, x, y, z):
    plate(b, (x - 0.28, y, z - 0.10), (x + 0.28, y + 1.4, z + 0.28), Cord(), (1, 2, 1))


def leg_pair(w, draw):
    b = w.bone('right_leg', 'right_leg')
    draw(b)
    w.mirror(b, 'left_leg', 'left_leg')


class RawHide(A.Mat):
    """Matte sewn hide with broad worn patches, not polished plate edge highlights."""
    def __init__(self, key, hood=False, inner=False, harness=False):
        tones = ([(16, 17, 20), (25, 25, 28), (35, 33, 32), (47, 43, 38), (60, 53, 44)] if hood else
                 [(23, 19, 17), (39, 30, 23), (56, 41, 29), (76, 55, 37), (98, 73, 48), (121, 91, 61)])
        super().__init__(*tones, light=True)
        self.key, self.inner, self.harness = key, inner, harness

    def pattern(self, c):
        i = c.i
        if self.inner and c.face in ('north', 'south', 'down', 'up'):
            edge = 0 if c.face == 'south' else c.fw - 1
            if abs(i - edge) <= 1:
                i = edge
        patch = S.hash01(self.key, c.face, i // 2, c.j // 2)
        t = 0.43 + (patch - 0.5) * 0.38
        if c.face == 'north' and self.harness:
            # A broad diagonal rawhide strap is part of the sewn jerkin surface.
            if abs(i - (1.0 + c.j * 0.58)) < 1.0:
                t = 0.19 if abs(i - (1.0 + c.j * 0.58)) < 0.65 else 0.35
            if c.j >= c.fh - 2:
                t -= 0.10
        if c.face in ('north', 'south') and c.fw > 3 and i in (0, c.fw - 1):
            t -= 0.07
        return t, 255


class RawHorn(A.Mat):
    """Dull keratin: visible growth ridges and broad scutes, no feather-like geometry."""
    def __init__(self, key, dark=False, inner=False):
        tones = ([(29, 27, 25), (43, 39, 32), (59, 52, 40), (80, 69, 51), (109, 95, 72)] if dark else
                 [(81, 65, 43), (132, 110, 76), (173, 151, 112), (207, 189, 149), (232, 218, 181)])
        super().__init__(*tones, light=True)
        self.key, self.dark, self.inner = key, dark, inner

    def pattern(self, c):
        i = c.i
        if self.inner and c.face in ('north', 'south', 'down', 'up'):
            edge = 0 if c.face == 'south' else c.fw - 1
            if abs(i - edge) <= 1:
                i = edge
        t = (0.53 if self.dark else 0.70) + (S.hash01(self.key, c.face, i // 2, c.j // 2) - 0.5) * 0.14
        if c.face in ('north', 'south', 'west', 'east'):
            if c.j == 0:
                t += 0.12
            elif c.j == c.fh - 1:
                t -= 0.18
            if i == 0:
                t -= 0.10
        return t, 255


def keratin_helmet():
    w = A.Worn('keratin_helmet', 64, 64)
    b = w.bone('head', 'hide_hood_and_ribbed_mask')
    # A closed hood hides the skin/hair beneath the mask slots. Its stepped crown
    # and inward cheek folds replace the earlier open horn cap entirely.
    sym(b, 5.12, (-8.82, 0.68), (-5.12, 5.18), RawHide('hood', hood=True), (10, 10, 10))
    sym(b, 4.64, (-9.70, -8.55), (-4.70, 4.72), RawHide('hood_crown', hood=True), (9, 1, 9))
    sym(b, 3.80, (-10.22, -9.55), (-3.55, 4.02), RawHide('hood_fold', hood=True), (8, 1, 8))
    for side in (-1, 1):
        for k, (inner, outer, y0, y1) in enumerate(((4.0, 5.55, -7.5, -4.45),
                                                   (3.55, 5.38, -4.35, -1.65),
                                                   (2.65, 4.89, -1.55, 0.48))):
            x0, x1 = sorted((side * inner, side * outer))
            plate(b, (x0, y0, -5.97 + k * 0.11), (x1, y1, -4.94 + k * 0.10),
                  RawHide(f'hood_cheek{k}', hood=True))
    # Horizontal horn ribs taper toward the chin; the only openings are deliberate
    # dark slots in the face guard, never a naked jaw or an exposed forehead.
    for k, (half, y0, y1) in enumerate(((3.95, -6.95, -6.10), (3.78, -5.65, -4.80),
                                       (3.46, -4.35, -3.50), (3.05, -3.05, -2.20),
                                       (2.63, -1.75, -0.90), (2.10, -0.45, 0.32))):
        sym(b, half, (y0, y1), (-5.81 - k * 0.025, -5.00 + k * 0.02),
            RawHorn(f'mask_rib{k}'), (max(1, round(half * 2)), 1, 1))
    sym(b, 0.43, (-7.07, 0.38), (-6.20, -5.67), RawHorn('mask_spine'), (1, 7, 1))
    return w


def keratin_chestplate():
    w = A.Worn('keratin_chestplate', 64, 64)
    b = w.bone('body', 'sewn_hide_jerkin_and_carapace_mantle')
    sym(b, 4.56, (-0.42, 12.57), (-2.59, 2.62), RawHide('jerkin', harness=True), (9, 13, 5))
    # A wrap collar and an uninterrupted hide mantle sit under large shell scutes.
    sym(b, 4.94, (-0.73, 2.34), (-3.20, 3.23), RawHide('mantle_collar', hood=True), (10, 3, 6))
    for k, (x0, x1, end) in enumerate(((-4.70, -1.65, 4.20), (-1.53, 1.34, 3.55), (1.46, 4.73, 4.65))):
        plate(b, (x0, 1.9, -3.42 - k * 0.10), (x1, end, -2.92 + k * 0.10),
              RawHorn(f'front_mantle_scute{k}', dark=True))
        plate(b, (x0 + 0.07, 1.82, 2.94 + k * 0.11), (x1 - 0.06, end + 0.17, 3.44 + k * 0.11),
              RawHorn(f'back_mantle_scute{k}', dark=True))
    # Thick, broad, irregular osteoderms replace the reference's feathers. The
    # mantle travels with the arm; a full sleeve beneath it closes the armpit/elbow.
    def arm(a):
        plate(a, (-3.44, -2.57, -2.52), (1.44, 10.48, 2.56), RawHide('sleeve'), (5, 13, 5))
        plate(a, (-4.45, -3.28, -3.25), (1.62, 2.96, 3.29), RawHide('shoulder_mantle', hood=True), (6, 6, 7))
        for k, (x0, x1, y0, y1) in enumerate(((-4.75, -1.40, -2.90, -0.30),
                                              (-1.25, 1.35, -2.62, -0.12),
                                              (-4.48, -1.85, -0.10, 2.38),
                                              (-1.67, 1.10, 0.10, 2.69),
                                              (-4.08, -1.43, 2.52, 4.15))):
            plate(a, (x0, y0, -3.79 - k * 0.15), (x1, y1, -3.11 + k * 0.09),
                  RawHorn(f'shoulder_scute{k}', dark=True))
            plate(a, (x0 + 0.06, y0 + 0.13, 3.14 + k * 0.10),
                  (x1 - 0.04, y1 + 0.11, 3.83 + k * 0.15), RawHorn(f'rear_scute{k}', dark=True))
        plate(a, (-4.87, -2.17, -2.73), (-4.35, 0.37, 2.78), RawHorn('outer_shell', dark=True), (1, 3, 6))
        plate(a, (-4.55, 0.56, -2.42), (-3.99, 3.41, 2.48), RawHorn('lower_outer_shell', dark=True), (1, 3, 5))
        # Bound horn strips enclose the forearm all round, above a dark hide glove.
        plate(a, (-3.64, 5.20, -2.77), (1.66, 9.59, 2.81), RawHorn('bracer_base'), (5, 4, 6))
        for k, y in enumerate((5.33, 6.77, 8.21)):
            plate(a, (-3.79 - k * 0.10, y, -3.03 - k * 0.11),
                  (1.83 + k * 0.10, y + 0.78, 3.08 + k * 0.11), RawHorn(f'bracer_rib{k}'), (6, 1, 6))
        plate(a, (-3.58, 9.80, -2.67), (1.61, 10.69, 2.70), RawHide('hide_glove'), (5, 1, 5))
    w.arm(arm, sided=False)
    return w


def keratin_leggings():
    w = A.Worn('keratin_leggings', 64, 64)
    b = w.bone('body', 'tied_hide_belt')
    sym(b, 4.73, (10.40, 12.73), (-2.81, 2.85), RawHide('belt'), (9, 2, 6))
    sym(b, 0.95, (10.94, 12.0), (-3.17, -2.76), RawHorn('horn_toggle'), (2, 1, 1))
    sym(b, 0.29, (11.3, 13.24), (-3.45, -3.07), Cord(), (1, 2, 1))
    def leg(l):
        plate(l, (-2.49, -0.40, -2.53), (2.43, 9.61, 2.57), RawHide('hide_trousers', inner=True), (5, 10, 5))
        plate(l, (-2.78, 0.26, -2.88), (2.65, 3.15, -2.26), RawHide('front_skirt', inner=True), (5, 3, 1))
        plate(l, (-2.68, 0.33, 2.31), (2.56, 3.32, 2.93), RawHide('rear_skirt', inner=True), (5, 3, 1))
        # Small patches on the outside of the thigh, leaving the full trousers visible.
        plate(l, (-2.99, 1.68, -1.69), (-2.41, 4.46, 1.73), RawHorn('thigh_patch'), (1, 3, 3))
        plate(l, (-2.76, 4.60, -2.92), (2.58, 6.53, -2.40), RawHide('knee_patch', inner=True), (5, 2, 1))
        plate(l, (-2.86, 7.0, -2.78), (2.71, 7.65, 2.81), RawHide('shin_binding', inner=True), (6, 1, 6))
    leg_pair(w, leg)
    return w


def keratin_boots():
    w = A.Worn('keratin_boots', 64, 64)
    def leg(l):
        # High hide boots overlap the trousers; toes, ankles and calves stay enclosed.
        plate(l, (-2.65, 8.73, -2.73), (2.61, 11.34, 2.77), RawHide('boot_shaft', inner=True), (5, 3, 6))
        plate(l, (-2.82, 10.90, -3.38), (2.76, 12.54, 2.95), RawHide('hide_boot', inner=True), (6, 2, 6))
        plate(l, (-2.97, 8.52, -2.92), (2.89, 9.35, 2.97), RawHorn('boot_cuff', inner=True), (6, 1, 6))
        plate(l, (-2.87, 9.63, -3.02), (2.81, 10.39, 3.09), RawHide('boot_lashing', inner=True), (6, 1, 6))
        plate(l, (-2.53, 11.1, -3.59), (2.46, 11.99, -3.12), RawHorn('toe_guard', inner=True), (5, 1, 1))
    leg_pair(w, leg)
    return w


def bronze_helmet():
    w = A.Worn('bronze_helmet', 64, 64)
    b = w.bone('head', 'open_crested_cap')
    sym(b, 4.72, (-8.7, -5.85), (-4.72, 4.72), Bronze('cap'), (9, 3, 9))
    sym(b, 3.75, (-9.65, -8.5), (-3.75, 3.75), Bronze('dome', 0.70), (8, 1, 8))
    sym(b, 4.90, (-6.2, -5.15), (-4.98, -3.7), Bronze('brow'), (10, 1, 1))
    sym(b, 4.76, (-5.45, -1.95), (3.6, 4.82), Bronze('neck_guard', 0.48), (10, 4, 1))
    for side in (-1, 1):
        x0, x1 = sorted((side * 3.87, side * 4.87))
        plate(b, (x0, -5.7, -0.6), (x1, -0.95, 3.75), Bronze('side_guard'), (1, 5, 4))
        x0, x1 = sorted((side * 3.15, side * 4.70))
        plate(b, (x0, -3.65, -4.76), (x1, -0.90, -3.85), Bronze('cheek'), (2, 3, 1))
    # Integral bronze crest (no knight visor, nasal bar or horsehair plume).
    sym(b, 0.60, (-11.25, -9.4), (-2.75, 4.00), Bronze('cast_crest', 0.77), (1, 2, 7))
    sym(b, 0.45, (-10.4, -8.85), (-4.30, -2.70), Bronze('crest_front', 0.66), (1, 2, 2))
    return w


def bronze_chestplate():
    w = A.Worn('bronze_chestplate', 64, 64)
    b = w.bone('body', 'hammered_cuirass')
    sym(b, 4.50, (0.75, 9.88), (-2.50, 2.50), Hide('lining'), (9, 9, 5))
    sym(b, 4.73, (1.55, 8.82), (-3.07, -2.28), Bronze('breastplate'), (9, 7, 1))
    sym(b, 4.58, (1.75, 9.35), (2.30, 3.02), Bronze('backplate', 0.52), (9, 8, 1))
    # Two shallow bosses and a broad waist lip suggest beaten metal, not a knight's keel.
    for side in (-1, 1):
        x0, x1 = sorted((side * 0.40, side * 3.75))
        plate(b, (x0, 2.12, -3.38), (x1, 4.55, -2.99), Bronze('chest_boss', 0.68), (3, 2, 1))
        x0, x1 = sorted((side * 2.35, side * 3.55))
        strap(b, (x0, -0.10, -2.72), (x1, 1.42, 2.72), 'shoulder_strap')
        # Small fasteners at the shoulders, in the same bronze as the cuirass.
        plate(b, (x0 + 0.16, 0.4, -2.90), (x1 - 0.16, 1.3, -2.65), Bronze('shoulder_pin', 0.80), (1, 1, 1))
    sym(b, 4.86, (8.95, 10.15), (-3.12, -2.22), Bronze('waist_lip'), (10, 1, 1))
    def arm(a):
        # Narrow bronze shoulder cap and a strapped forearm shield; elbows/hands are exposed.
        plate(a, (-3.7, -2.55, -2.65), (1.43, -0.65, 2.65), Bronze('shoulder_cap'), (5, 2, 5))
        strap(a, (-3.50, 0.05, -2.36), (1.45, 1.20, 2.36), 'arm_strap')
        plate(a, (-3.67, 5.65, -2.93), (1.62, 8.55, -2.10), Bronze('forearm'), (5, 3, 1))
        for y in (5.9, 7.82):
            strap(a, (-3.50, y, -2.40), (1.45, y + 0.6, 2.40), 'forearm_tie')
    w.arm(arm, sided=False)
    return w


def bronze_leggings():
    w = A.Worn('bronze_leggings', 64, 64)
    b = w.bone('body', 'leather_skirt_belt')
    sym(b, 4.52, (10.45, 12.40), (-2.59, 2.59), Hide('belt'), (9, 2, 5))
    sym(b, 1.0, (10.65, 12.05), (-2.95, -2.47), Bronze('belt_clasp', 0.72), (2, 1, 1))
    def leg(l):
        # Three independently leg-bound leather skirt tabs with bronze studs, open behind the knee.
        for k, x in enumerate((-2.70, -1.18, 0.34)):
            end = (4.65, 5.30, 4.85)[k]
            plate(l, (x, 0.25, -2.91), (x + 1.22, end, -2.31), Hide(f'skirt_tab{k}'), (1, 5, 1))
            plate(l, (x + 0.20, 0.8, -3.12), (x + 0.95, 1.45, -2.85), Bronze('tab_stud', 0.76), (1, 1, 1))
        plate(l, (-2.84, 0.45, -1.9), (-2.31, 4.1, 2.0), Hide('side_tab'), (1, 4, 4))
        plate(l, (-2.61, 0.45, 2.28), (1.58, 4.45, 2.89), Hide('rear_tab'), (4, 4, 1))
        plate(l, (-2.55, 5.55, -2.95), (1.42, 7.75, -2.26), Bronze('greave_knee'), (4, 2, 1))
        plate(l, (-2.38, 7.85, -2.87), (1.28, 9.70, -2.24), Bronze('upper_greave'), (4, 2, 1))
        strap(l, (-2.38, 6.5, -2.40), (2.45, 7.1, 2.40), 'greave_strap')
    leg_pair(w, leg)
    return w


def bronze_boots():
    w = A.Worn('bronze_boots', 64, 32)
    def leg(l):
        # Separate lower greave over an open sandal: no articulated steel toe/knight sabaton.
        plate(l, (-2.44, 9.86, -2.94), (1.38, 11.28, -2.24), Bronze('lower_greave'), (4, 1, 1))
        strap(l, (-2.59, 10.15, -2.40), (2.45, 10.75, 2.40), 'ankle_strap')
        plate(l, (-2.55, 11.50, -3.15), (2.39, 12.45, 2.58), Hide('sandal_sole'), (5, 1, 6))
        plate(l, (-2.36, 10.85, -3.13), (2.45, 11.47, -1.30), Hide('sandal_front_tie'), (5, 1, 2))
        plate(l, (-2.35, 10.85, 0.7), (2.45, 11.47, 2.15), Hide('sandal_rear_tie'), (5, 1, 1))
    leg_pair(w, leg)
    return w


BUILDERS = {tier: {p: globals()[f'{tier}_{p}'] for p in PIECES} for tier in TIERS}


def item_sprite(tier, piece):
    """Inventory silhouette from the same geometry, rendered at native 32 px with no player skin."""
    worn = BUILDERS[tier][piece]()
    worn.pack()
    tex = worn.paint()
    groups = A.worn_quads(worn, tex, pose={}, arms='wide')
    points = np.concatenate([v for _, quads, _ in groups for v, _ in quads])
    lo, hi = points.min(axis=0), points.max(axis=0)
    centre = (lo + hi) / 2
    scale = 26 / max(hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2])
    return A.rasterise(groups, (32, 32), -12, 8, scale, centre, None)


def build(tier, piece):
    worn = BUILDERS[tier][piece]()
    worn.pack()
    return worn, worn.paint(), item_sprite(tier, piece)


def set_icon(tier):
    built = {p: build(tier, p) for p in PIECES}
    return S.frame(S.scene(built, PIECES), -12, 8, size=(64, 64), scale=1.7,
                   centre=(0, 6, 0), background=None)


def verify_keratin_coverage(built):
    """An opaque magenta player reveals any exposed skin or clothing in the full set.

    Checks both base and outer skin layers, wide/slim arms, front/back views and
    a walking pose. Face slots are backed by the hood, so they expose no skin.
    """
    marker = Image.new('RGBA', (64, 64), (255, 0, 255, 255))
    for slim in (False, True):
        for pose_name, pose in (('standing', {}), ('walking', S.WALK)):
            groups = A.player_quads(slim, skin=marker, pose=pose)
            for worn, tex, _ in built.values():
                groups += A.worn_quads(worn, tex, pose=pose, arms='slim' if slim else 'wide')
            for yaw in (0, -35, 180, 145):
                pixels = np.array(S.frame(groups, yaw, 12, size=(220, 300), scale=7.5))
                exposed = (pixels[..., 0] > 40) & (pixels[..., 1] < 20) & (pixels[..., 2] > 40)
                assert not exposed.any(), f'Keratin exposes skin: slim={slim}, {pose_name}, yaw={yaw}, {exposed.sum()} pixels'


def verify(built):
    """Check the runtime model contract and all intended faces before writing assets."""
    for piece, (worn, tex, icon) in built.items():
        assert tex.size == (worn.width, worn.height)
        assert icon.size == (32, 32) and icon.getbbox(), (worn.id, 'empty icon')
        names = set()
        for b in worn.bones:
            assert b.part in A.PART_POSE and b.name not in names, (worn.id, b.name)
            names.add(b.name)
            assert not any(b.rot) and b.fit == 'none'
            for c in b.cubes:
                lo = np.array(c.origin) - np.array(c.grow)
                hi = np.array(c.origin) + np.array(c.size) + np.array(c.grow)
                assert np.isfinite(lo).all() and (hi > lo).all(), worn.id
                u, v = c.uv
                x, y, z = c.size
                assert u >= 0 and v >= 0 and u + 2 * (x + z) <= worn.width and v + z + y <= worn.height
    for arms in ('wide', 'slim'):
        # A real shared plane is a z-fight; nearby faces on closed hide wraps are intentional.
        clashes = S.zfight(built, arms, tol=0.001)
        assert not clashes, '\n'.join([f'{arms} overlapping faces:'] + clashes)
    if built['helmet'][0].id == 'keratin_helmet':
        verify_keratin_coverage(built)


def previews(tier, built):
    S.set_sheet(built).save(DESIGN / f'{tier}_set.png')
    if tier == 'keratin':
        frames = [S.label(S.frame(S.scene(built, PIECES, pose=S.WALK), yaw, 10,
                                  size=(360, 500), scale=12.2, centre=(0, 7.0, 0)), label)
                  for yaw, label in ((-32, 'Keratin - front'), (148, 'Keratin - back'))]
        S.strip(frames).save(DESIGN / 'keratin_review.png')
    for piece in PIECES:
        worn, tex, icon = built[piece]
        card = Image.new('RGBA', (140, 350), S.BG)
        card.alpha_composite(icon.resize((128, 128), Image.Resampling.NEAREST), (6, 110))
        S.label(card, f'{tier}_{piece}')
        groups = S.scene(built, [piece], pose=S.WALK)
        S.strip([card, S.frame(groups, -34, 14), S.frame(groups, 146, 14)]).save(DESIGN / f'{tier}_{piece}.png')
    tiles = []
    for piece, (_, tex, _) in built.items():
        tile = Image.new('RGBA', (tex.width * 3, tex.height * 3 + 20), (40, 42, 46, 255))
        tile.alpha_composite(tex.resize((tex.width * 3, tex.height * 3), Image.Resampling.NEAREST), (0, 20))
        ImageDraw.Draw(tile).text((4, 3), f'{tier}_{piece} {tex.width}x{tex.height}', fill='white')
        tiles.append(tile)
    S.strip(tiles, background=(40, 42, 46, 255)).save(DESIGN / f'{tier}_textures.png')


def write(tier, built):
    for piece, (worn, tex, icon) in built.items():
        data = worn.to_json()
        data['glow'] = False
        data['texture'] = f'arksurvivalreturns:textures/entity/armour/{tier}_{piece}.png'
        model = ASSETS / f'armour/{tier}_{piece}.json'
        texture = ASSETS / f'textures/entity/armour/{tier}_{piece}.png'
        texture.parent.mkdir(parents=True, exist_ok=True)
        model.write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8')
        tex.save(texture)
        icon.save(ASSETS / f'textures/item/{tier}_{piece}.png')
        print(f'{tier}_{piece}: {S.cube_count(worn)} wide / {S.cube_count(worn, "slim")} slim cubes, {tex.size}')
    previews(tier, built)


def refresh_icons(tiers):
    from build_keratin_items import icon
    tech = ASSETS / 'textures/gui/tech/icons'
    if 'keratin' in tiers:
        chest = Image.open(ASSETS / 'textures/item/keratin_chestplate.png').convert('RGBA')
        icon(chest).save(ARK / 'design/technology-tree/icons/keratin_armour.png')
        spec = json.loads((ARK / 'design/technology-tree/technology-tree.json').read_text(encoding='utf-8'))
        for node in spec['nodes']:
            if node.get('icon') == 'keratin_armour':
                # Match build_tech_menu_assets.export's source-icon resampling.
                icon(chest).resize((64, 64), Image.Resampling.LANCZOS).save(tech / f'{node["id"]}.png')
    if 'bronze' in tiers:
        Image.open(ASSETS / 'textures/item/bronze_helmet.png').resize((64, 64), Image.Resampling.NEAREST).save(tech / 'tincan.png')
        set_icon('bronze').save(tech / 'colossus.png')


def comparison():
    frames = []
    for tier, title in (('keratin', 'Keratin - Prehistoric'), ('bronze', 'Bronze - Early metal age'), ('steel', 'Steel - Knight reference')):
        built = {p: (S.build(p) if tier == 'steel' else build(tier, p)) for p in PIECES}
        frames.append(S.label(S.frame(S.scene(built, PIECES, pose=S.WALK), -34, 12), title))
    S.strip(frames).save(DESIGN / 'early_armour_comparison.png')


def main():
    tiers = sys.argv[1:] or TIERS
    assert set(tiers) <= set(TIERS), f'Choose from {TIERS}'
    DESIGN.mkdir(parents=True, exist_ok=True)
    for tier in tiers:
        built = {piece: build(tier, piece) for piece in PIECES}
        verify(built)
        write(tier, built)
    refresh_icons(tiers)
    comparison()
    print('Validated wide and slim models, UV bounds, positive geometry and no coplanar face overlaps.')


if __name__ == '__main__':
    main()
