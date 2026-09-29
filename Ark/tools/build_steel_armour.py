"""Steel armour (Iron Age): the worn models and textures of the steel helmet, chestplate, leggings and boots.

Plain late-medieval plate, the steel tier after keratin and bronze, with no creature motifs:
  helmet      great helm: flat crown, one eye slit crossed by a reinforcing ridge, breaths on the right
              cheek, a brow band and a flared rim at the neck
  chestplate  keeled breastplate, gorget, three-lame pauldrons, a pointed plackart, a two-lame fauld and a
              strapped backplate; rerebraces, winged couters, vambraces and gauntlets on the arms
  leggings    leather belt, two-lame tassets, cuisses strapped at the back, winged poleyns, greaves
  boots       greave cuffs over articulated sabatons with slightly pointed toes
The look follows the 16 px item sprites of build_iron_age_art.py (which stay untouched).

The models use the worn-accessory format (accessory_art.py, client/accessory/WornModels.java): cuboids with
box UVs hung on the vanilla humanoid parts, wide and slim arm bones for the chestplate, and no armour "fit"
(the pieces are the armour, nothing pushes them out). They live in assets/.../armour/, not worn/: worn/ is
the accessory catalogue (WornModels loads every file there as an accessory); an armour layer bakes these
with the same WornModels/WornModel code from armour/ and binds the texture named in each file. Colours come
only from the Ark steel ramp (STEEL / PLATE), the Bronze Age LEATHER ramp and four tiny brass rivets. Noise
is hashed per texel, so every output is a pure function of this file (and the vanilla jar for the preview
skins).

Writes (hand-authored assets, committed):
  src/main/resources/assets/arksurvivalreturns/armour/steel_<piece>.json                  worn geometry
  src/main/resources/assets/arksurvivalreturns/textures/entity/armour/steel_<piece>.png   worn texture
and review renders into design/armour: the set on Steve (front, back, three-quarters, walking), on Alex and
at a distance; each piece alone next to its item sprite; the four textures. It also reports faces that
would z-fight (coplanar, overlapping, same direction) across the whole set and the player skin.

Run from Ark: python tools/build_steel_armour.py
"""
import json
import zlib
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

import accessory_art as A
from build_bronze_age_art import BRASS, LEATHER, rgb
from build_iron_age_art import PLATE

ARK = Path(__file__).resolve().parents[1]
ASSETS = ARK / 'src/main/resources/assets/arksurvivalreturns'
MODELS = ASSETS / 'armour'                     # not worn/: that folder is the accessory catalogue
TEXTURES = ASSETS / 'textures/entity/armour'
ITEMS = ASSETS / 'textures/item'
DESIGN = ARK / 'design/armour'
PIECES = ('helmet', 'chestplate', 'leggings', 'boots')
BREATHS = 'right'                              # the wearer's cheek that carries the great helm's breaths

RAMP = [PLATE[str(k)] for k in range(1, 8)]    # #252d38 .. #b9c8d6 and the glint: seven steel tones
SLIT = PLATE['0']                              # eye slit, breaths, the dark inside of the helm
GLINT = PLATE['7']
SHADOW = PLATE['2']
RIVET = 0.76                                   # a steel rivet head, a tone or two above its plate
RIVET_BRASS = rgb(BRASS[3])


def hash01(*key):
    """Deterministic 0..1 per key (no run-time randomness)."""
    return zlib.crc32(repr(key).encode()) / 0xFFFFFFFF


# ------------------------------------------------------------------------------------------ materials

class Steel(A.Mat):
    """Plate steel on a quantised ramp: rolled (bright) top edges, shaded lower edges, a curved highlight
    across each face and faintly brushed rows.

    marks maps (face, i, j) to a tone (float) or a colour (tuple); negative i or j count from the far edge.
    paint(face, i, j, fw, fh) may return the same for anything marks does not cover.
    inner (right-leg plates, mirrored onto the left leg) paints the two texel columns next to the cube's +x
    side as one shaded edge column: the mirrored twin samples that same texel where the two legs overlap at
    the crotch, so the coplanar strip shows one colour and cannot flicker, and the shade parts the legs.
    """

    def __init__(self, key, base=0.5, curve=0.12, top=0.14, bottom=-0.16, sides=-0.06, noise=0.035, streak=0.035,
                 marks=None, paint=None, inner=False, ramp=RAMP):
        super().__init__(*ramp, noise=noise)
        self.key, self.base, self.curve = key, base, curve
        self.top, self.bottom, self.sides, self.streak = top, bottom, sides, streak
        self.marks = marks or {}
        self.paint = paint
        self.inner = inner

    def column(self, c):
        """(texel column to paint, whether it is the shaded inner edge)."""
        if not self.inner or c.face in ('west', 'east') or c.fw < 3:
            return c.i, False
        edge = 0 if c.face == 'south' else c.fw - 1   # the south face runs from +x to -x
        return (edge, True) if abs(c.i - edge) <= 1 else (c.i, False)

    def mark(self, face, i, j, fw, fh):
        for key in ((face, i, j), (face, i - fw, j), (face, i, j - fh), (face, i - fw, j - fh)):
            if key in self.marks:
                return self.marks[key]
        return self.paint(face, i, j, fw, fh) if self.paint else None

    def pattern(self, c):
        (i, inner_edge), j, fw, fh = self.column(c), c.j, c.fw, c.fh
        m = self.mark(c.face, i, j, fw, fh)
        if m is not None:
            return m, 255
        t = self.base - (0.12 if inner_edge else 0)
        if c.face in ('north', 'south', 'west', 'east'):
            t += self.curve * (1 - abs(2 * (i + 0.5) / fw - 1)) - self.curve * 0.45
            if fh == 1:
                t += self.top * 0.4
            elif j == 0:
                t += self.top
            elif j == fh - 1:                  # a two-row lame keeps its body: the step below makes the line
                t += self.bottom * (0.4 if fh == 2 else 1)
            if fw >= 3 and i in (0, fw - 1):
                t += self.sides
        elif c.face == 'down':                 # the visual top: lit, with a brighter rolled rim
            t += 0.06 + (0.1 if i in (0, fw - 1) or j in (0, fh - 1) else 0)
        else:                                  # underside
            t -= 0.14
        t += (hash01(self.key, c.face, j, 'row') - 0.5) * self.streak
        t += (hash01(self.key, c.face, i, j, c.w, c.h, c.d) - 0.5) * self.noise
        return t, 255


class Leather(Steel):
    """Dark oiled leather: the Bronze Age LEATHER ramp, grain along the strap, darker stitched edges."""

    def __init__(self, key, **kw):
        kw = dict(dict(base=0.55, curve=0.0, top=0.12, bottom=-0.3, sides=0.0, noise=0.18, streak=0.3,
                       ramp=LEATHER), **kw)
        super().__init__(key, **kw)


def dark(key):
    """The inside of the helm, seen through the slit."""
    return Steel(key, base=0.4, curve=0, top=0, bottom=0, sides=0, noise=0.5, streak=0,
                 ramp=[SLIT, (22, 27, 34)])


def rivets(marks, face, points, colour=GLINT):
    for i, j in points:
        marks[(face, i, j)] = colour
    return marks


def leather_tone(i, j):
    return LEATHER[1 + (hash01('strap', i, j) > 0.55)]


# ------------------------------------------------------------------------------------------ geometry

def plate(bone, lo, hi, mat, size=None, faces=A.ALL):
    """Cube spanning lo..hi in the bone's space, painted with `size` texels (default: rounded extents)."""
    ext = [hi[k] - lo[k] for k in range(3)]
    size = size or [max(1, int(e + 0.5)) for e in ext]
    grow = [(ext[k] - size[k]) / 2 for k in range(3)]
    origin = [lo[k] + grow[k] for k in range(3)]
    return bone.box(origin, size, mat, grow=grow, faces=faces)


def sym(bone, half_x, y, z, mat, size=None):
    """A cube centred on x (head and body pieces)."""
    return plate(bone, (-half_x, y[0], z[0]), (half_x, y[1], z[1]), mat, size)


# ------------------------------------------------------------------------------------------- helmet

def helmet():
    """Great helm on the head (head box x -4..4, y -8..0, z -4..4; the hat layer reaches 4.5)."""
    W = A.Worn('steel_helmet', 64, 64)
    h = W.bone('head', 'great_helm')
    # Upper skull: the ring of its top shows round the raised crown plate, riveted.
    top_ring = {('down', i, j): GLINT for i in range(10) for j in range(10)
                if (i in (0, 9) or j in (0, 9)) and (i + j) % 3 == 0}
    sym(h, 5.0, (-9.4, -4.5), (-5.0, 5.0), Steel('helm_skull', base=0.52, marks=top_ring), (10, 5, 10))
    sym(h, 4.3, (-10.0, -9.2), (-4.3, 4.3), Steel('helm_crown', base=0.58, curve=0.08, top=0.12), (9, 1, 9))
    # Brow band above the slit: steel rivets all round, two brass ones at the temples (as on the sprite).
    brow = {}
    rivets(brow, 'north', [(2, 0), (-3, 0)], RIVET)
    rivets(brow, 'west', [(-2, 0)], RIVET_BRASS)           # west is the wearer's right; its front is i = -1
    rivets(brow, 'east', [(1, 0)], RIVET_BRASS)            # east runs from the front, i = 0
    rivets(brow, 'west', [(3, 0)], RIVET)
    rivets(brow, 'east', [(-4, 0)], RIVET)
    rivets(brow, 'south', [(2, 0), (5, 0), (8, 0)], RIVET)
    sym(h, 5.25, (-5.7, -4.4), (-5.25, 5.25), Steel('helm_brow', base=0.54, curve=0.12, marks=brow), (10, 1, 10))
    # The slit: a dark liner behind the gap, closed at the sides and back by a steel band.
    sym(h, 4.7, (-4.8, -3.4), (-4.7, 4.7), dark('helm_liner'), (9, 1, 9))
    plate(h, (-5.1, -4.55, -2.6), (5.1, -3.55, 5.1),
          Steel('helm_rear', base=0.42, curve=0.1, paint=lambda f, i, j, fw, fh: SLIT if f == 'north' else None),
          (10, 1, 8))
    # Face plate: breaths (two columns of two) on the chosen cheek, a lit lip under each and under the slit.
    face = {}
    for i in ((1, 3) if BREATHS == 'right' else (-2, -4)):
        for j in (1, 3):
            face[('north', i, j)] = SLIT
            face[('north', i, j + 1)] = 0.64
    sym(h, 5.2, (-3.7, 0.4), (-5.2, 5.2), Steel('helm_face', base=0.50, top=0.16, marks=face), (10, 5, 10))
    sym(h, 5.5, (-0.1, 1.0), (-5.5, 5.5), Steel('helm_rim', base=0.48, curve=0.12, top=0.2), (11, 1, 11))
    # The reinforcing ridge down the face crosses the slit; a riveted seam closes the back.
    ridge = {('north', 0, j): (0.93 if j % 3 else 0.78) for j in range(10)}
    sym(h, 0.5, (-9.3, 0.7), (-5.8, -4.9), Steel('helm_ridge', base=0.66, curve=0, marks=ridge), (1, 10, 1))
    seam = {('south', 0, j): GLINT for j in (1, 4, 7)}
    sym(h, 0.45, (-9.3, 0.6), (4.9, 5.75), Steel('helm_seam', base=0.44, curve=0, marks=seam), (1, 10, 1))
    return W


# --------------------------------------------------------------------------------------- chestplate

def chestplate():
    """Harness on the body (x -4..4, y 0..12, z -2..2) and both arms, wide and slim."""
    W = A.Worn('steel_chestplate', 64, 64)
    b = W.bone('body', 'harness')
    # Cuirass core: the flanks carry the leather straps and buckles that close breast and back plates.
    flank = {}
    for face in ('west', 'east'):
        for j in (3, 7):
            for i in range(6):
                flank[(face, i, j)] = leather_tone(i, j)
            flank[(face, 1, j)] = GLINT
            flank[(face, 2, j)] = SHADOW
    sym(b, 4.85, (-0.5, 9.2), (-2.85, 2.85), Steel('cuirass', base=0.42, marks=flank), (10, 10, 6))
    # Breastplate with its keel: a raised centre band and the ridge line, over which the plackart rises.
    sym(b, 4.55, (0.6, 8.4), (-3.25, -2.5), Steel('breastplate', base=0.46, curve=0.22), (9, 8, 1))
    sym(b, 2.0, (1.62, 5.6), (-3.5, -3.0), Steel('keel', base=0.56, curve=0.2, top=0.1), (4, 4, 1))
    keel = {('north', 0, j): 0.93 for j in range(8)}
    sym(b, 0.55, (1.5, 9.0), (-3.78, -3.35), Steel('keel_ridge', base=0.72, curve=0, marks=keel), (1, 8, 1))
    gorget = {}
    for face in ('west', 'east'):
        rivets(gorget, face, [(1, 1), (-2, 1)], RIVET)
    sym(b, 4.95, (-0.7, 1.5), (-3.4, 3.4), Steel('gorget', base=0.54, curve=0.14, top=0.18, bottom=-0.14,
                                                   marks=gorget), (10, 2, 7))
    # Plackart: three stepped plates make the point over the lower breast.
    sym(b, 1.4, (4.3, 5.3), (-3.62, -2.6), Steel('plackart_tip', base=0.66, curve=0.1, top=0.2), (3, 1, 1))
    sym(b, 2.9, (5.3, 6.6), (-3.62, -2.6), Steel('plackart_mid', base=0.64, curve=0.12, top=0.2), (6, 1, 1))
    placket = rivets({}, 'north', [(1, 1), (-2, 1)], RIVET)
    sym(b, 4.4, (6.6, 9.3), (-3.62, -2.6), Steel('plackart', base=0.6, curve=0.16, top=0.2, marks=placket),
        (9, 3, 1))
    # Fauld: two hooped lames, each lower one wider.
    for k, (half, y, z) in enumerate(((5.05, (8.9, 10.4), 3.3), (5.25, (10.1, 11.4), 3.5))):
        lame = {}
        for face in ('north', 'south'):
            rivets(lame, face, [(1, 1), (-2, 1)], RIVET)
        sym(b, half, y, (-z, z), Steel(f'fauld{k}', base=0.50, curve=0.14, top=0.18, bottom=-0.16, marks=lame),
            (10, 2, 7))
    # Backplate: a central ridge, the shoulder straps from the breastplate buckled onto it.
    back = {('south', 4, j): 0.72 for j in range(8)}
    for i in (1, 2, 6, 7):
        for j in (0, 1):
            back[('south', i, j)] = leather_tone(i, j)
    for i in (1, 6):
        back[('south', i, 2)] = GLINT
        back[('south', i + 1, 2)] = SHADOW
    sym(b, 4.5, (0.6, 8.8), (2.6, 3.25), Steel('backplate', base=0.48, curve=0.18, marks=back), (9, 8, 1))

    def arm(a):
        """Right arm, wide model (x -3..1, y -2..10, z -2..2); accessory_art derives slim and left."""
        spaulder = rivets({}, 'west', [(2, 1)], RIVET_BRASS)
        rivets(spaulder, 'north', [(1, 1), (-2, 1)], RIVET)
        plate(a, (-4.45, -3.3, -3.15), (1.8, -0.4, 3.15), Steel('pauldron', base=0.52, top=0.16, marks=spaulder),
              (6, 3, 6))
        plate(a, (-3.9, -3.85, -2.6), (1.2, -3.1, 2.6), Steel('pauldron_cap', base=0.60, curve=0.1), (5, 1, 5))
        plate(a, (-4.2, -0.8, -2.95), (1.6, 0.9, 2.95), Steel('pauldron_lame1', base=0.50, top=0.16), (6, 2, 6))
        plate(a, (-3.95, 0.5, -2.75), (1.5, 2.1, 2.75), Steel('pauldron_lame2', base=0.48, top=0.16), (5, 2, 5))
        plate(a, (-3.6, 1.7, -2.55), (1.4, 4.3, 2.55), Steel('rerebrace', base=0.5, top=0.06, bottom=-0.1), (5, 3, 5))
        plate(a, (-3.8, 4.0, -2.95), (1.65, 6.1, 2.95), Steel('couter', base=0.54, top=0.16), (5, 2, 6))
        wing = {('west', 1, 1): GLINT, ('west', 2, 1): GLINT, ('west', 0, 0): 0.3, ('west', 3, 0): 0.3,
                ('west', 0, 2): 0.25, ('west', 3, 2): 0.25}
        plate(a, (-4.35, 3.5, -2.0), (-3.55, 6.6, 2.0), Steel('couter_wing', base=0.58, curve=0.2, marks=wing),
              (1, 3, 4))
        brace = {}
        for face in ('south', 'east'):
            for i in range(5):
                brace[(face, i, 1)] = leather_tone(i, 1)
            brace[(face, 2, 1)] = GLINT
        plate(a, (-3.55, 5.9, -2.5), (1.4, 8.5, 2.5), Steel('vambrace', base=0.52, top=0.06, bottom=-0.1,
                                                            marks=brace), (5, 3, 5))
        plate(a, (-3.85, 8.2, -2.8), (1.7, 9.55, 2.8), Steel('gauntlet_cuff', base=0.54, top=0.2), (5, 1, 6))
        fingers = {('up', i, j): (0.2 if i % 2 else 0.5) for i in range(5) for j in range(5)}
        fingers.update({('north', i, 0): (0.35 if i % 2 else 0.6) for i in range(5)})
        plate(a, (-3.45, 9.3, -2.45), (1.4, 10.5, 2.45), Steel('gauntlet', base=0.44, marks=fingers), (5, 1, 5))

    W.arm(arm, sided=False)
    return W


# ----------------------------------------------------------------------------------------- leggings

def leggings():
    """Belt on the body; cuisses, tassets, poleyns and greaves on the legs (x -2..2, y 0..12, z -2..2).
    Leg pieces reach past x 2.25 on the inner side (over the skin layer) and use Steel(inner=True)."""
    W = A.Worn('steel_leggings', 64, 32)
    b = W.bone('body', 'belt')
    sym(b, 4.6, (10.9, 12.35), (-2.7, 2.7), Leather('belt'), (9, 1, 5))
    buckle = {('north', 0, 0): GLINT, ('north', 1, 0): 0.8, ('north', 0, 1): 0.62, ('north', 1, 1): SLIT}
    sym(b, 0.85, (11.0, 12.5), (-2.95, -2.55), Steel('buckle', base=0.52, curve=0, marks=buckle), (2, 2, 1))

    def leg(l):
        back = {('south', i, j): leather_tone(i, j) for i in range(5) for j in (1, 4)}   # straps round the thigh
        back[('south', 2, 1)] = back[('south', 2, 4)] = GLINT
        plate(l, (-2.6, -0.1, -2.6), (2.45, 6.3, 2.6), Steel('cuisse', base=0.44, marks=back, inner=True), (5, 6, 5))
        tasset = rivets({}, 'north', [(1, 1), (3, 1)], RIVET)
        plate(l, (-3.0, 0.1, -3.15), (2.65, 2.7, -1.2), Steel('tasset1', base=0.62, top=0.18, marks=tasset,
                                                              inner=True), (5, 3, 2))
        plate(l, (-2.85, 2.4, -3.0), (2.55, 4.3, -1.35), Steel('tasset2', base=0.6, top=0.18, inner=True), (5, 2, 2))
        plate(l, (-2.7, 4.3, -2.85), (2.55, 5.2, 1.3), Steel('poleyn_lame', base=0.48, top=0.2, inner=True),
              (5, 1, 4))
        cop = {('north', 2, 0): GLINT, ('north', 2, 1): 0.8}
        plate(l, (-2.85, 5.0, -3.1), (2.65, 7.3, 1.4), Steel('poleyn', base=0.62, top=0.22, marks=cop, inner=True),
              (5, 2, 4))
        boss = {('north', 0, 0): GLINT, ('north', 1, 0): 0.84, ('north', 0, 1): 0.7, ('north', 1, 1): 0.5}
        plate(l, (-1.2, 5.45, -3.45), (0.7, 6.85, -2.95), Steel('poleyn_boss', base=0.66, marks=boss), (2, 2, 1))
        wing = {('west', 1, 1): GLINT, ('west', 0, 0): 0.3, ('west', 2, 0): 0.3, ('west', 0, 2): 0.25,
                ('west', 2, 2): 0.25}
        plate(l, (-3.4, 4.6, -2.4), (-2.65, 7.8, 0.9), Steel('poleyn_wing', base=0.58, curve=0.2, marks=wing),
              (1, 3, 3))
        shin = {('north', 2, j): 0.84 for j in range(1, 5)}
        shin.update({('west', 1, j): GLINT for j in (1, 4)})
        plate(l, (-2.45, 6.2, -2.45), (2.35, 11.9, 2.45), Steel('greave', base=0.50, marks=shin, inner=True),
              (5, 6, 5))

    W.legs(leg)
    return W


# -------------------------------------------------------------------------------------------- boots

def boots():
    """Greave cuffs and articulated sabatons on the lower legs, over the leggings' greaves."""
    W = A.Worn('steel_boots', 64, 32)

    def boot(l):
        shin = {('north', 2, j): 0.84 for j in range(3)}
        plate(l, (-2.7, 7.7, -2.8), (2.55, 10.4, 2.7), Steel('boot_greave', base=0.50, marks=shin, inner=True),
              (5, 3, 6))
        plate(l, (-2.95, 7.35, -3.05), (2.7, 8.35, 2.95), Steel('boot_cuff', base=0.54, top=0.2, inner=True),
              (6, 1, 6))
        strap = {('west', 2, 0): GLINT, ('west', 3, 0): SHADOW}
        plate(l, (-2.85, 9.7, -2.95), (2.65, 10.3, 2.85), Leather('ankle_strap', marks=strap, inner=True), (5, 1, 6))
        plate(l, (-2.9, 10.45, -3.45), (2.6, 11.4, 2.9), Steel('sabaton1', base=0.52, top=0.2, inner=True), (5, 1, 6))
        foot = {('down', i, j): (0.35 if j % 2 == 0 else 0.66) for i in range(5) for j in range(2)}
        plate(l, (-2.8, 11.1, -3.9), (2.5, 12.35, 2.8), Steel('sabaton2', base=0.50, top=0.2, marks=foot,
                                                              inner=True), (5, 1, 7))
        plate(l, (-2.3, 11.4, -4.7), (1.6, 12.2, -3.6), Steel('sabaton_toe', base=0.54, top=0.2), (4, 1, 1))
        plate(l, (-1.35, 11.7, -5.25), (0.65, 12.05, -4.5), Steel('sabaton_tip', base=0.58, top=0.2), (2, 1, 1))

    W.legs(boot)
    return W


BUILDERS = {'helmet': helmet, 'chestplate': chestplate, 'leggings': leggings, 'boots': boots}


# ------------------------------------------------------------------------------------------- output

def build(piece):
    """(worn model, texture, item sprite) of one piece, the same shape as build_accessories.build."""
    worn = BUILDERS[piece]()
    for bone in worn.bones:
        assert not any(bone.rot), f'{bone.name}: the z-fight check assumes unrotated bones'
    worn.pack()
    tex = worn.paint()
    icon = Image.open(ITEMS / f'steel_{piece}.png').convert('RGBA')
    return worn, tex, icon


def write(piece, worn, tex):
    MODELS.mkdir(parents=True, exist_ok=True)
    TEXTURES.mkdir(parents=True, exist_ok=True)
    data = worn.to_json()
    data['glow'] = False
    data['texture'] = f'arksurvivalreturns:textures/entity/armour/steel_{piece}.png'
    (MODELS / f'steel_{piece}.json').write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8')
    tex.save(TEXTURES / f'steel_{piece}.png')


def cube_count(worn, arms='wide'):
    return sum(len(b.cubes) for b in worn.bones if b.arms in (None, arms))


# ------------------------------------------------------------------------------------ z-fight check

def skin(arms):
    """The player's cubes (base and outer layer) as (part, origin, size, grow)."""
    w = 3 if arms == 'slim' else 4
    cubes = [('head', (-4, -8, -4), (8, 8, 8)), ('body', (-4, 0, -2), (8, 12, 4)),
             ('right_arm', (1 - w, -2, -2), (w, 12, 4)), ('left_arm', (-1, -2, -2), (w, 12, 4)),
             ('right_leg', (-2, 0, -2), (4, 12, 4)), ('left_leg', (-2, 0, -2), (4, 12, 4))]
    return [(part, origin, size, g) for part, origin, size in cubes for g in (0.0, 0.5 if part == 'head' else 0.25)]


def boxes(built, arms='wide'):
    """Root-space boxes at rest: (label, part, owner cube or None, lo, hi)."""
    out = []
    for part, origin, size, g in skin(arms):
        lo = np.array(origin, dtype=float) - g + np.array(A.PART_POSE[part], dtype=float)
        out.append((f'skin {part} +{g}', part, None, lo, lo + np.array(size) + 2 * g))
    for piece, (worn, _tex, _icon) in built.items():
        for b in worn.bones:
            if b.arms not in (None, arms):
                continue
            off = np.array(A.PART_POSE[b.part], dtype=float) + np.array(b.pivot)
            for k, c in enumerate(b.cubes):
                lo = np.array(c.origin) - np.array(c.grow) + off
                hi = np.array(c.origin) + np.array(c.size) + np.array(c.grow) + off
                out.append((f'{piece}:{b.name}#{k}', b.part, c.owner(), lo, hi))
    return out


def zfight(built, arms='wide', tol=0.08):
    """Pairs of same-facing faces closer than tol with overlapping area. Mirrored twins on the two legs
    are skipped: their overlap strip samples one texel (Steel inner=True)."""
    found = []
    items = boxes(built, arms)
    for a in range(len(items)):
        for b in range(a + 1, len(items)):
            la, pa, oa, loa, hia = items[a]
            lb, pb, ob, lob, hib = items[b]
            if oa is None and ob is None:
                continue
            if oa is not None and oa is ob and {pa, pb} == {'right_leg', 'left_leg'}:
                continue
            for axis in range(3):
                u, v = [k for k in range(3) if k != axis]
                ou = min(hia[u], hib[u]) - max(loa[u], lob[u])
                ov = min(hia[v], hib[v]) - max(loa[v], lob[v])
                if ou <= 1e-3 or ov <= 1e-3:
                    continue
                for side, (pa_, pb_) in (('-', (loa[axis], lob[axis])), ('+', (hia[axis], hib[axis]))):
                    if abs(pa_ - pb_) < tol:
                        found.append(f'{"xyz"[axis]}{side} {pa_:.2f}/{pb_:.2f}  {la}  <>  {lb}  ({ou:.2f}x{ov:.2f})')
    return found


# ------------------------------------------------------------------------------------------ previews

BG = (236, 233, 224, 255)
STAND = {}
WALK = {'right_arm': (-22, 0, 4), 'left_arm': (22, 0, -4), 'right_leg': (18, 0, 0), 'left_leg': (-18, 0, 0),
        'head': (4, -12, 0)}


def scene(built, pieces, slim=False, pose=None):
    groups = A.player_quads(slim, pose=pose)
    for piece in pieces:
        worn, tex, _icon = built[piece]
        groups += A.worn_quads(worn, tex, pose, None, 'slim' if slim else 'wide')
    return groups


def frame(groups, yaw, pitch, size=(250, 350), scale=9.0, centre=(0, 7.5, 0), background=BG):
    return A.rasterise(groups, size, yaw, pitch, scale, centre, background)


def label(image, text):
    ImageDraw.Draw(image).text((6, 4), text, fill=(40, 36, 30, 255))
    return image


def strip(images, background=BG, gap=6):
    width = sum(i.width for i in images) + gap * (len(images) + 1)
    height = max(i.height for i in images) + 2 * gap
    sheet = Image.new('RGBA', (width, height), background)
    x = gap
    for image in images:
        sheet.alpha_composite(image, (x, gap))
        x += image.width + gap
    return sheet


def set_sheet(built):
    full = list(PIECES)
    stand, walk = scene(built, full, pose=STAND), scene(built, full, pose=WALK)
    frames = [label(frame(stand, 0, 4), 'front'), label(frame(stand, 180, 4), 'back'),
              label(frame(walk, -34, 12), 'three-quarter'), label(frame(walk, 146, 14), 'back three-quarter'),
              label(frame(scene(built, full, slim=True, pose=WALK), -34, 12), 'Alex (slim arms)')]
    far = [frame(stand, yaw, 6, size=(70, 96), scale=2.5) for yaw in (0, -34, 146)]
    small = strip(far, gap=4)
    distance = Image.new('RGBA', (small.width, 350), BG)
    distance.alpha_composite(small, (0, 120))
    frames.append(label(distance, 'at a distance'))
    return strip(frames)


def piece_sheet(built, piece):
    groups = scene(built, [piece], pose=WALK)
    centre, scale = {'helmet': ((0, -3, 0), 14.0), 'boots': ((0, 19, 0), 13.0)}.get(piece, ((0, 7.5, 0), 9.0))
    icon = built[piece][2].resize((128, 128), Image.NEAREST)
    card = Image.new('RGBA', (140, 350), BG)
    card.alpha_composite(icon, (6, 110))
    frames = [label(card, f'steel_{piece}'), frame(groups, -34, 14, centre=centre, scale=scale),
              frame(groups, 146, 16, centre=centre, scale=scale)]
    if piece == 'chestplate':
        frames.append(label(frame(scene(built, [piece], slim=True, pose=WALK), -34, 14), 'slim'))
    return strip(frames)


def texture_sheet(built):
    tiles = []
    for piece in PIECES:
        tex = built[piece][1]
        zoom = 4 if tex.width <= 64 else 3
        tile = Image.new('RGBA', (tex.width * zoom, tex.height * zoom + 16), (60, 62, 66, 255))
        tile.alpha_composite(tex.resize((tex.width * zoom, tex.height * zoom), Image.NEAREST), (0, 16))
        ImageDraw.Draw(tile).text((4, 2), f'steel_{piece} {tex.width}x{tex.height}', fill=(230, 230, 220, 255))
        tiles.append(tile)
    return strip(tiles, background=(40, 42, 46, 255))


def main():
    built = {piece: build(piece) for piece in PIECES}
    for piece, (worn, tex, _icon) in built.items():
        write(piece, worn, tex)
        print(f'steel_{piece}: {cube_count(worn)} cubes (wide), {cube_count(worn, "slim")} (slim), '
              f'texture {tex.width}x{tex.height}')
    for arms in ('wide', 'slim'):
        for line in zfight(built, arms):
            print(f'  z-fight ({arms}): {line}')
    DESIGN.mkdir(parents=True, exist_ok=True)
    set_sheet(built).save(DESIGN / 'steel_set.png')
    for piece in PIECES:
        piece_sheet(built, piece).save(DESIGN / f'steel_{piece}.png')
    texture_sheet(built).save(DESIGN / 'steel_textures.png')
    print(f'Wrote {len(PIECES)} steel armour pieces and previews in {DESIGN.relative_to(ARK)}')


if __name__ == '__main__':
    main()
