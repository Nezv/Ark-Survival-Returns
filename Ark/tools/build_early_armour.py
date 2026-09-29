"""Keratin and Bronze armour, using the Steel harness's native worn-model format.

Keratin: prehistoric horn scutes lashed onto hide, an open face, short swept horn
tips, cord ties and moccasins. Bronze: an early metal-age open crested cap,
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


def keratin_helmet():
    w = A.Worn('keratin_helmet', 64, 64)
    b = w.bone('head', 'hide_cap_and_scutes')
    sym(b, 4.72, (-8.75, -6.15), (-4.70, 4.70), Hide('skullcap'), (9, 3, 9))
    sym(b, 3.65, (-9.65, -8.50), (-3.65, 3.65), Horn('crown'), (7, 1, 7))
    sym(b, 4.92, (-6.65, -5.20), (-4.94, -3.75), Horn('brow'), (10, 2, 1))
    sym(b, 4.78, (-6.0, -1.8), (3.7, 4.90), Hide('nape'), (10, 4, 1))
    # A short raised central scute continues backwards along the crown.
    sym(b, 0.95, (-10.7, -9.5), (-2.6, 3.5), Horn('crown_ridge', 0.74), (2, 1, 6))
    for side in (-1, 1):
        # Small swept horn tips sit behind the temples, leaving eyes and jaw exposed.
        def box(x0, x1, y0, y1, z0, z1, key, base=0.65):
            lo, hi = sorted((side * x0, side * x1))
            plate(b, (lo, y0, z0), (hi, y1, z1), Horn(key, base))
        box(3.9, 5.15, -5.5, -1.15, 0.1, 3.8, 'temple')
        box(4.7, 5.75, -7.1, -5.25, 1.6, 4.3, 'horn_root', 0.52)
        box(5.2, 6.15, -8.3, -6.8, 3.1, 5.5, 'horn_tip', 0.83)
        lashing(b, side * 3.6, -6.35, -5.12)
    return w


def keratin_chestplate():
    w = A.Worn('keratin_chestplate', 64, 64)
    b = w.bone('body', 'hide_vest_and_lashed_scutes')
    sym(b, 4.45, (0.65, 10.15), (-2.48, 2.48), Hide('vest'), (9, 10, 5))
    # Separate irregular scutes: gaps expose the hide and cord rather than a solid cuirass.
    for k, (y, half, h) in enumerate(((1.55, 4.12, 2.5), (4.30, 3.92, 2.4), (7.00, 3.62, 2.3))):
        for side in (-1, 1):
            x0, x1 = sorted((side * 0.38, side * half))
            plate(b, (x0, y + (0.25 if side > 0 else 0), -3.18 - k * 0.07),
                  (x1, y + h, -2.34), Horn(f'breast_scute{k}_{side}'))
            lashing(b, side * (half - 0.75), y + 0.65, -3.42 - k * 0.07)
        sym(b, half - 0.25, (y + 0.15, y + h - 0.25), (2.3, 3.05 + k * 0.06),
            Horn(f'back_scute{k}', 0.56))
    for side in (-1, 1):
        x0, x1 = sorted((side * 2.25, side * 3.40))
        strap(b, (x0, -0.15, -2.75), (x1, 1.35, 2.78), 'shoulder_binding')
    # Bound scutes at the shoulders and wrists; hands and most of the arms stay visible.
    def arm(a):
        plate(a, (-3.75, -2.70, -2.9), (1.48, -0.35, 2.9), Horn('shoulder_shell'), (5, 2, 6))
        plate(a, (-4.18, -1.45, -2.6), (-3.55, 1.25, 2.6), Horn('shoulder_edge', 0.74), (1, 3, 5))
        strap(a, (-3.43, 0.9, -2.39), (1.42, 2.15, 2.39), 'upper_arm_tie')
        strap(a, (-3.40, 6.85, -2.38), (1.42, 8.8, 2.38), 'wrist_wrap')
        plate(a, (-3.15, 5.60, -2.96), (1.05, 9.15, -2.18), Horn('wrist_scute'), (4, 4, 1))
        lashing(a, -1.0, 6.85, -3.15)
    w.arm(arm, sided=False)
    return w


def keratin_leggings():
    w = A.Worn('keratin_leggings', 64, 64)
    b = w.bone('body', 'rawhide_waist_tie')
    sym(b, 4.52, (10.60, 12.4), (-2.60, 2.60), Hide('waistband'), (9, 2, 5))
    sym(b, 4.65, (11.05, 11.70), (-2.74, -2.50), Cord(), (9, 1, 1))
    sym(b, 0.48, (11.2, 13.5), (-2.98, -2.61), Cord(), (1, 2, 1))
    def leg(l):
        # Front and rear rawhide apron panels follow each leg independently when walking.
        plate(l, (-2.78, 0.1, -2.73), (1.56, 4.95, -2.30), Hide('apron'), (4, 5, 1))
        plate(l, (-2.74, 0.15, 2.29), (1.53, 4.40, 2.74), Hide('rear_apron'), (4, 4, 1))
        plate(l, (-2.95, 0.55, -3.15), (1.24, 2.65, -2.65), Horn('hip_scute'), (4, 2, 1))
        plate(l, (-2.45, 2.85, -3.04), (0.84, 4.65, -2.65), Horn('thigh_scute', 0.71), (3, 2, 1))
        lashing(l, -1.8, 0.70, -3.30)
        strap(l, (-2.44, 6.15, -2.44), (2.36, 7.05, 2.44), 'knee_tie')
        plate(l, (-2.63, 5.8, -2.86), (1.12, 7.55, -2.32), Horn('knee_scute'), (4, 2, 1))
    leg_pair(w, leg)
    return w


def keratin_boots():
    w = A.Worn('keratin_boots', 64, 32)
    def leg(l):
        plate(l, (-2.57, 8.05, -2.58), (2.43, 11.05, 2.58), Hide('moccasin_cuff'), (5, 3, 5))
        plate(l, (-2.73, 7.65, -2.75), (2.52, 8.85, 2.75), Hide('fur_cuff', fur=True), (5, 1, 5))
        plate(l, (-2.72, 10.5, -3.5), (2.59, 12.16, 2.72), Hide('moccasin'), (5, 2, 6))
        plate(l, (-2.41, 9.15, -2.91), (1.23, 10.90, -2.49), Horn('ankle_scute'), (4, 2, 1))
        plate(l, (-2.27, 10.20, -3.65), (1.87, 11.75, -3.20), Horn('toe_scute', 0.59), (4, 2, 1))
        lashing(l, -0.8, 8.70, -3.06)
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
        clashes = S.zfight(built, arms)
        assert not clashes, '\n'.join([f'{arms} overlapping faces:'] + clashes)


def previews(tier, built):
    S.set_sheet(built).save(DESIGN / f'{tier}_set.png')
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
    if set(tiers) == set(TIERS):
        comparison()
    print('Validated wide and slim models, UV bounds, positive geometry and no coplanar face overlaps.')


if __name__ == '__main__':
    main()
