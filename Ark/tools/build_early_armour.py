"""Keratin and Bronze armour, using the Steel harness's native worn-model format.

Keratin: a closed dark hide hood, tapered ribbed horn face guard, broad carapace
shoulder scutes (no feathers), sewn hide sleeves/trousers and bound horn bracers. Bronze: a red-crested cheek-guard helmet, russet leather cuirass, diagonal bronze
baldric, segmented shoulders, red undercloth and tall golden greaves.

Run from Ark: python tools/build_early_armour.py [keratin|bronze ...]
Writes armour/<tier>_<piece>.json, textures/entity/armour/<tier>_<piece>.png,
the 32 px item sprites and tech icons drawn from these models (build_armour_sprites.py)
and review renders in design/armour. Like Steel, these models are consumed by the
Models showcase; game renderer wiring is separate.
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

import accessory_art as A
import build_armour_sprites as sprites
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
    """Warm golden bronze with rolled edges and broad highlighted shoulder panels."""

    def __init__(self, key, base=0.76, marks=None, inner=False, scales=False):
        super().__init__(key, base=base, ramp=BRONZE_RAMP, marks=marks,
                         curve=0.12, top=0.13, bottom=-0.10, noise=0.025, streak=0.0, inner=inner)
        self.scales = scales

    def pattern(self, c):
        t, a = super().pattern(c)
        if isinstance(t, tuple):
            return t, a
        h = S.hash01(self.key, c.face, c.i, c.j)
        if self.scales and c.face in ('north', 'south', 'west', 'east'):
            t += 0.075 if (c.i // 2 + c.j) % 2 else -0.055
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


class OchreRed(S.Leather):
    """Dyed cloth, red leather cuffs and the helmet's short horsehair crest."""
    def __init__(self, key, crest=False, inner=False):
        super().__init__(key, ramp=[(50, 11, 10), (83, 17, 13), (124, 26, 18),
                                   (163, 39, 26), (197, 62, 36), (221, 86, 45)],
                         base=0.76 if crest else 0.58, top=0.08, bottom=-0.13,
                         noise=0.07, streak=0.09, inner=inner)


class CuirassHide(S.Leather):
    """Russet hide cuirass with broad sewn bands and a bronze diagonal baldric."""
    def __init__(self, key, baldric=False, row0=0, inner=False):
        super().__init__(key, ramp=[(44, 22, 13), (66, 31, 17), (88, 41, 21),
                                   (113, 51, 24), (138, 65, 30), (162, 82, 42)],
                         base=0.54, top=0.08, bottom=-0.10, noise=0.08, streak=0.06, inner=inner)
        self.baldric, self.row0 = baldric, row0

    def pattern(self, c):
        t, alpha = super().pattern(c)
        if c.face in ('north', 'south'):
            if c.j % 3 == 2:
                t -= 0.12
            if self.baldric:
                i = c.i if c.face == 'north' else c.fw - 1 - c.i
                distance = abs(i - (0.1 + (c.j + self.row0) * 0.91))
                if distance < 1.15:
                    return BRONZE_RAMP[6 if distance < 0.65 else 5], 255
        return t, alpha


def bronze_helmet():
    w = A.Worn('bronze_helmet', 64, 64)
    b = w.bone('head', 'bronze_crest_cheek_guards_and_nasal')
    # A stepped, rounded crown and rolled brow, distinct from Steel's great helm.
    sym(b, 4.79, (-8.76, -5.63), (-4.79, 4.84), Bronze('helmet_shell', 0.77), (10, 3, 10))
    sym(b, 4.14, (-9.52, -8.62), (-4.15, 4.25), Bronze('raised_crown', 0.83), (8, 1, 8))
    sym(b, 3.22, (-9.96, -9.39), (-3.14, 3.43), Bronze('crown_top', 0.78), (6, 1, 7))
    sym(b, 5.05, (-6.09, -4.92), (-5.34, -4.49), Bronze('rolled_brow', 0.86), (10, 1, 1))
    sym(b, 4.93, (-5.76, 0.39), (3.94, 5.12), Bronze('nape_guard', 0.69), (10, 6, 1))
    for side in (-1, 1):
        x0, x1 = sorted((side * 4.14, side * 4.94))
        plate(b, (x0, -5.69, -4.60), (x1, 0.16, 4.27), Bronze('temple_guard', 0.73), (1, 6, 9))
        x0, x1 = sorted((side * 1.63, side * 4.81))
        plate(b, (x0, -4.48, -5.02), (x1, -0.57, -4.27), Bronze('long_cheek_guard', 0.80), (3, 4, 1))
        x0, x1 = sorted((side * 2.19, side * 4.71))
        plate(b, (x0, -0.67, -4.88), (x1, 0.30, -4.17), Bronze('cheek_point', 0.75), (3, 1, 1))
    sym(b, 0.53, (-5.24, -0.66), (-5.47, -4.84), Bronze('nasal', 0.86), (1, 5, 1))
    # A red, front-to-back horsehair crest, held in a narrow bronze socket.
    sym(b, 0.82, (-10.39, -9.79), (-3.44, 3.78), Bronze('crest_socket', 0.74), (2, 1, 7))
    for k, (z0, z1, top) in enumerate(((-3.22, -1.45, -13.25), (-1.45, 0.25, -13.80),
                                      (0.25, 1.95, -13.55), (1.95, 3.48, -12.76))):
        sym(b, 0.57, (top, -10.22), (z0, z1), OchreRed(f'horsehair{k}', crest=True), (1, 4, 2))
    return w


def bronze_chestplate():
    w = A.Worn('bronze_chestplate', 64, 64)
    b = w.bone('body', 'russet_cuirass_and_bronze_baldric')
    # Full cloth underneath keeps the collar, elbow and waist gaps intentional.
    sym(b, 4.49, (-0.38, 12.54), (-2.59, 2.61), OchreRed('tunic'), (9, 13, 5))
    sym(b, 4.60, (0.75, 10.07), (-2.89, 2.96), CuirassHide('cuirass', baldric=True), (9, 9, 6))
    # Raised seams retain the reference's broad leather bands, rather than a metal keel.
    for k, y in enumerate((3.29, 6.38, 9.45)):
        sym(b, 4.72, (y, y + 0.53), (-3.04 - k * 0.025, 3.10 + k * 0.025),
            CuirassHide(f'cuirass_seam{k}', baldric=True, row0=(y - 0.75) * 9 / 9.32), (9, 1, 6))
    # A narrow solid end at the shoulder makes the textured diagonal strap read in silhouette.
    plate(b, (-4.08, -0.19, -3.20), (-2.44, 1.14, 3.30), Bronze('baldric_shoulder', 0.80), (2, 1, 6))
    sym(b, 4.86, (9.98, 10.92), (-3.16, 3.20), Bronze('waist_border', 0.85), (10, 1, 6))
    def arm(a):
        plate(a, (-3.43, -2.56, -2.53), (1.43, 10.61, 2.57), OchreRed('red_sleeve'), (5, 13, 5))
        # Three short bronze segments rest on red cloth, with a leather shoulder yoke.
        plate(a, (-3.88, -3.04, -2.85), (1.53, -1.14, 2.89), CuirassHide('shoulder_yoke'), (5, 2, 6))
        for k, (y0, y1, outer) in enumerate(((-1.85, 0.43, -4.18), (0.58, 2.54, -4.34),
                                              (2.69, 4.52, -4.04))):
            plate(a, (outer, y0, -3.17 - k * 0.10), (1.69, y1, 3.23 + k * 0.10),
                  Bronze(f'segmented_shoulder{k}', 0.79, scales=True), (6, 2, 6))
        plate(a, (-3.66, 4.54, -2.82), (1.57, 5.33, 2.87), OchreRed('shoulder_red_border'), (5, 1, 6))
        plate(a, (-3.58, 6.62, -2.79), (1.61, 9.27, 2.84), Bronze('bronze_bracer', 0.80), (5, 3, 6))
        plate(a, (-3.73, 9.18, -2.95), (1.76, 9.85, 3.00), OchreRed('wrist_binding'), (6, 1, 6))
        plate(a, (-3.61, 9.76, -2.69), (1.64, 10.76, 2.74), OchreRed('red_leather_glove'), (5, 1, 5))
    w.arm(arm, sided=False)
    return w


def bronze_leggings():
    w = A.Worn('bronze_leggings', 64, 64)
    b = w.bone('body', 'red_belt_and_bronze_trim')
    sym(b, 4.70, (10.58, 12.74), (-2.87, 2.92), OchreRed('red_belt'), (9, 2, 6))
    sym(b, 4.83, (12.09, 12.83), (-3.09, 3.16), Bronze('belt_lower_trim', 0.85), (10, 1, 6))
    sym(b, 0.79, (10.98, 12.05), (-3.24, -2.83), Bronze('belt_toggle', 0.78), (2, 1, 1))
    def leg(l):
        plate(l, (-2.48, -0.41, -2.54), (2.43, 9.78, 2.60), OchreRed('red_leg_wrap', inner=True), (5, 10, 5))
        # Short leather pteruges move with each leg; cloth backs all the gaps.
        for k, x in enumerate((-2.76, -0.88, 1.00)):
            end = (2.62, 3.22, 2.81)[k]
            plate(l, (x, 0.16, -3.06 - k * 0.06), (x + 1.64, end, -2.57),
                  CuirassHide(f'skirt_tab{k}', inner=True), (3, 3, 1))
            plate(l, (x + 0.07, end - 0.55, -3.17 - k * 0.06), (x + 1.57, end + 0.02, -2.96 - k * 0.06),
                  Bronze(f'skirt_tip{k}', 0.82, inner=True), (3, 1, 1))
        plate(l, (-2.81, 0.27, 2.57), (2.69, 2.89, 3.06), CuirassHide('rear_skirt', inner=True), (5, 3, 1))
        plate(l, (-2.89, 0.30, -2.28), (-2.44, 3.05, 2.33), CuirassHide('side_skirt'), (1, 3, 5))
        # Tall, broad golden greaves over the red wraps; no articulated knight knee caps.
        plate(l, (-2.66, 2.93, -2.81), (2.59, 6.63, 2.90), Bronze('upper_greave', 0.78, inner=True), (5, 4, 6))
        plate(l, (-2.55, 6.77, -2.85), (2.48, 9.61, 2.96), Bronze('lower_greave', 0.81, inner=True), (5, 3, 6))
    leg_pair(w, leg)
    return w


def bronze_boots():
    w = A.Worn('bronze_boots', 64, 64)
    def leg(l):
        # Red ankle bindings and a simple bronze toe over an enclosed leather foot wrap.
        plate(l, (-2.63, 9.32, -2.71), (2.58, 11.25, 2.77), OchreRed('ankle_wrap', inner=True), (5, 2, 6))
        plate(l, (-2.79, 9.82, -2.94), (2.73, 10.56, 3.03), CuirassHide('ankle_tie', inner=True), (6, 1, 6))
        plate(l, (-2.67, 10.88, -3.39), (2.61, 12.54, 2.95), CuirassHide('leather_sandal', inner=True), (5, 2, 6))
        plate(l, (-2.75, 11.22, -3.60), (2.69, 12.23, -1.02), Bronze('bronze_toe', 0.84, inner=True), (5, 1, 3))
        plate(l, (-2.76, 12.21, -3.47), (2.71, 12.82, 3.06), CuirassHide('sandal_sole', inner=True), (5, 1, 7))
    leg_pair(w, leg)
    return w

BUILDERS = {tier: {p: globals()[f'{tier}_{p}'] for p in PIECES} for tier in TIERS}


def build(tier, piece):
    """(worn model, texture, item sprite): the sprite is drawn from this geometry (build_armour_sprites)."""
    worn = BUILDERS[tier][piece]()
    worn.pack()
    tex = worn.paint()
    return worn, tex, sprites.sprite(tier, piece, worn, tex)


def item_sprite(tier, piece):
    return build(tier, piece)[2]


def verify_coverage(built, open_face=False):
    """An opaque magenta player reveals any exposed skin or clothing in the full set.

    Checks base and outer skin layers, wide/slim arms, front/back views and
    walking. Bronze deliberately exposes the face inside its cheek guards;
    the head is excluded there, while clothing and all other skin must be covered.
    """
    marker = Image.new('RGBA', (64, 64), (255, 0, 255, 255))
    for slim in (False, True):
        for pose_name, pose in (('standing', {}), ('walking', S.WALK)):
            groups = [g for g in A.player_quads(slim, skin=marker, pose=pose)
                      if not open_face or g[0] != 'head']
            for worn, tex, _ in built.values():
                groups += A.worn_quads(worn, tex, pose=pose, arms='slim' if slim else 'wide')
            for yaw in (0, -35, 180, 145):
                pixels = np.array(S.frame(groups, yaw, 12, size=(220, 300), scale=7.5))
                exposed = (pixels[..., 0] > 40) & (pixels[..., 1] < 20) & (pixels[..., 2] > 40)
                assert not exposed.any(), f'{built["helmet"][0].id} exposes skin/clothing: slim={slim}, {pose_name}, yaw={yaw}, {exposed.sum()} pixels'


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
    verify_coverage(built, open_face=built['helmet'][0].id == 'bronze_helmet')


def bronze_set_sheet(built):
    """Leave room for the taller red crest in every review pose."""
    frames = []
    for slim, pose, yaw, pitch, title in ((False, S.STAND, 0, 4, 'front'),
                                        (False, S.STAND, 180, 4, 'back'),
                                        (False, S.WALK, -34, 12, 'three-quarter'),
                                        (False, S.WALK, 146, 14, 'back three-quarter'),
                                        (True, S.WALK, -34, 12, 'Alex (slim arms)')):
        groups = S.scene(built, PIECES, slim=slim, pose=pose)
        frames.append(S.label(S.frame(groups, yaw, pitch, scale=7.8, centre=(0, 5.2, 0)), title))
    far = [S.frame(S.scene(built, PIECES), yaw, 6, size=(70, 96), scale=2.0, centre=(0, 5.2, 0))
           for yaw in (0, -34, 146)]
    small = S.strip(far, gap=4)
    distance = Image.new('RGBA', (small.width, 350), S.BG)
    distance.alpha_composite(small, (0, 120))
    frames.append(S.label(distance, 'at a distance'))
    return S.strip(frames)


def previews(tier, built):
    (bronze_set_sheet(built) if tier == 'bronze' else S.set_sheet(built)).save(DESIGN / f'{tier}_set.png')
    centre, scale = ((0, 5.2, 0), 11.2) if tier == 'bronze' else ((0, 7.0, 0), 12.2)
    frames = [S.label(S.frame(S.scene(built, PIECES, pose=S.WALK), yaw, 10,
                              size=(360, 500), scale=scale, centre=centre), label)
              for yaw, label in ((-32, f'{tier.title()} - front'), (148, f'{tier.title()} - back'))]
    S.strip(frames).save(DESIGN / f'{tier}_review.png')
    for piece in PIECES:
        worn, tex, icon = built[piece]
        card = Image.new('RGBA', (140, 350), S.BG)
        card.alpha_composite(icon.resize((128, 128), Image.Resampling.NEAREST), (6, 110))
        S.label(card, f'{tier}_{piece}')
        groups = S.scene(built, [piece], pose=S.WALK)
        settings = {'scale': 7.8, 'centre': (0, 5.2, 0)} if tier == 'bronze' else {}
        S.strip([card, S.frame(groups, -34, 14, **settings), S.frame(groups, 146, 14, **settings)]).save(DESIGN / f'{tier}_{piece}.png')
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


def comparison():
    frames = []
    for tier, title in (('keratin', 'Keratin - Prehistoric'), ('bronze', 'Bronze - Early metal age'), ('steel', 'Steel - Knight reference')):
        built = {p: (S.build(p) if tier == 'steel' else build(tier, p)) for p in PIECES}
        settings = {'scale': 7.8, 'centre': (0, 5.2, 0)} if tier == 'bronze' else {}
        frames.append(S.label(S.frame(S.scene(built, PIECES, pose=S.WALK), -34, 12, **settings), title))
    S.strip(frames).save(DESIGN / 'early_armour_comparison.png')


def main():
    tiers = sys.argv[1:] or TIERS
    assert set(tiers) <= set(TIERS), f'Choose from {TIERS}'
    DESIGN.mkdir(parents=True, exist_ok=True)
    for tier in tiers:
        built = {piece: build(tier, piece) for piece in PIECES}
        verify(built)
        write(tier, built)
    sprites.main()      # every armour sprite and the tech icons drawn from them, from the models just written
    comparison()
    print('Validated wide and slim models, UV bounds, positive geometry and no coplanar face overlaps.')


if __name__ == '__main__':
    main()
