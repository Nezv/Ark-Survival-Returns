"""Builds every Ark accessory's art: the inventory icon, the worn model on the player and its texture.

Primitive gadgets are made from creature parts and camp materials; relics and totems are the mystical set.
The catalogue (ids, slots, effects) lives in Java (feature/accessory/Accessory.java); this file owns the look.

Writes (hand-authored assets, committed):
  src/main/resources/assets/arksurvivalreturns/textures/item/accessory/<id>.png     16x16 icon
  src/main/resources/assets/arksurvivalreturns/textures/item/trophy/<id>.png        creature parts
  src/main/resources/assets/arksurvivalreturns/worn/<id>.json                       worn geometry
  src/main/resources/assets/arksurvivalreturns/textures/entity/accessory/<id>.png   worn texture (+ _glow)
and review renders into design/accessories (Steve and Alex, front and back, with and without armour fit).

Run from Ark: python tools/build_accessories.py [id ...]
"""
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

import accessory_art as A
import accessory_palette as P

ARK = Path(__file__).resolve().parents[1]
ASSETS = ARK / 'src/main/resources/assets/arksurvivalreturns'
DESIGN = ARK / 'design/accessories'

ITEMS = {}


def item(ident, group, slot):
    def register(fn):
        ITEMS[ident] = (group, slot, fn)
        return fn
    return register


# ------------------------------------------------------------------------------ custom materials

class Goggles(A.Mat):
    """Slit goggles: carved bone plate, two eye slits and a notch for the nose."""

    def pattern(self, c):
        if c.face == 'north' and c.j == 1 and c.i in (3, 4):
            return 0, 0
        if c.face == 'north' and c.j == 1 and c.i in (1, 2, 5, 6):
            return (24, 21, 18), 255
        if c.face == 'north' and c.j == 0 and c.i in (0, 7):
            return 0.1, 255
        return 0.55 + (c.rng.random() - 0.5) * self.noise, 255


class WolfFur(A.Fur):
    """Wolf pelt with amber eyes on the brow of the cap."""

    def pattern(self, c):
        if c.face == 'north' and c.fw == 8 and c.j == c.fh - 1 and c.i in (1, 6):
            return (224, 156, 38), 255
        return super().pattern(c)


class Lattice(A.Mat):
    """Snowshoe: bent-wood rim, rawhide lacing criss-cross, open holes between."""

    def __init__(self, rim, lace, **kw):
        super().__init__(*rim, **kw)
        self.lace = [A.rgb(t) for t in lace]

    def pattern(self, c):
        if c.face in ('down', 'up'):
            if (math.floor(c.x) + math.floor(c.z)) % 2 == 0 or math.floor(c.z) % 3 == 0:
                return self.lace[(math.floor(c.x) + math.floor(c.z)) % len(self.lace)], 255
            return 0, 0
        return 0.45 + (c.rng.random() - 0.5) * self.noise, 255


class Lamellar(A.Mat):
    """Bone plates laced in rows (Chukchi / Inuit lamellar)."""

    def __init__(self, plates, lace, **kw):
        super().__init__(*plates, **kw)
        self.lace = A.rgb(lace)

    def pattern(self, c):
        # Vertical plates two texels wide in rows four tall; lacing shows as dots at each row's head.
        row = math.floor(c.y) % 4
        column = c.i % 2
        if c.face in ('down', 'up'):
            return 0.5 + (c.rng.random() - 0.5) * self.noise, 255
        if row == 0 and column == 0:
            return self.lace, 255
        t = (0.8 if column == 0 else 0.5) - (0.18 if row == 3 else 0)
        return t + (c.rng.random() - 0.5) * self.noise, 255


class Ember(A.Mat):
    """Clay pot mouth packed with glowing tinder."""

    def __init__(self, **kw):
        super().__init__(*P.CLAY, **kw)
        self.glow_tones = [A.rgb(t) for t in P.EMBER_GLOW]

    def pattern(self, c):
        if c.face == 'down':
            k = c.rng.random()
            return (self.glow_tones[int(k * 3)] if k < 0.75 else (60, 36, 24)), 255
        return 0.5 + (c.rng.random() - 0.5) * self.noise, 255

    def glows(self, c, col):
        return c.face == 'down' and col[0] > 200


class Notched(A.Mat):
    """The Ishango-style tally bone: pale shaft cut with dark notches in groups."""

    def pattern(self, c):
        if c.face in ('north', 'west', 'east', 'south') and math.floor(c.y) % 2 == 1 and c.i != 0:
            return 0.02, 255
        return 0.65 + (c.rng.random() - 0.5) * self.noise, 255


# ------------------------------------------------------------------------ primitive: head

@item('bone_snow_goggles', 'primitive', 'head')
def snow_goggles():
    W = A.Worn('bone_snow_goggles')
    h = W.bone('head', 'goggles', fit='helmet')
    h.box((-4, -5, -5), (8, 2, 1), Goggles(*P.IVORY, noise=0.12), grow=(0.12, 0.05, 0.02))
    h.box((-4, -5, -4), (8, 1, 8), P.leather(), grow=0.56)
    for x in (-4.9, 3.9):
        h.box((x, -5.3, -4.6), (1, 2, 1), P.bone(), grow=-0.1)
    icon = A.icon([
        '................',
        '................',
        '................',
        '................',
        '..c..........c..',
        '.c............c.',
        '.c.oooooooooo.c.',
        '.cobllllllllsoc.',
        '.cobKKKllKKKboc.',
        '.coslllooollsoc.',
        '..oooooo.ooooo..',
        '................',
        '................',
        '................',
        '................',
        '................'], {'o': '#6e6450', 'b': '#d6cbad', 'l': '#ece4cf', 's': '#b9ab8a', 'K': '#1d1a16',
                              'c': '#6b4529'})
    return W, icon


@item('pelt_hood', 'primitive', 'head')
def pelt_hood():
    W = A.Worn('pelt_hood', 64, 64)
    fur = WolfFur(*P.WOLF, noise=0.2)
    fur_plain = A.Fur(*P.WOLF, noise=0.2)
    h = W.bone('head', 'hood', fit='helmet')
    h.box((-4, -9, -4), (8, 2, 8), fur, grow=0.55)
    h.box((-4, -8, 3.4), (8, 6, 1), fur_plain, grow=0.5)
    h.box((-4.6, -8, -3), (1, 4, 6), fur_plain, grow=0.3)
    h.box((3.6, -8, -3), (1, 4, 6), fur_plain, grow=0.3)
    snout = A.Fur(*P.WOLF[1:], fringe=False, noise=0.15)
    h.box((-1.5, -9.1, -7.6), (3, 2, 4), snout, grow=0.05)
    h.box((-1, -9.6, -7.4), (2, 1, 3), snout)
    h.box((-0.5, -9.4, -8.2), (1, 1, 1), A.Solid('#151414', '#232120'))
    for x in (-1.4, 0.4):
        h.box((x, -7.2, -7.3), (1, 1, 0), A.Tapered(*P.IVORY, tip='+y', noise=0.05), faces=('north', 'south'))
    ear = W.bone('head', 'ear_right', pivot=(-2.6, -9.5, -1.2), rot=(-6, 0, -10), fit='helmet')
    ear.box((-1, -2, -0.5), (2, 2, 1), A.Fur(*P.WOLF, fringe=False))
    ear.box((-1, -2, -0.8), (2, 2, 0), A.Solid('#5b4a48', '#6a5654'), faces=('north',))
    ear.box((-0.5, -3.4, -0.5), (1, 2, 1), A.Solid(P.WOLF[1], P.WOLF[2]))
    W.mirror(ear, name='ear_left')
    drape = W.bone('body', 'drape', fit='chest')
    drape.box((-4, 0, 2), (8, 5, 1), fur_plain, grow=0.45)
    paw = W.bone('body', 'paw_right', fit='chest')
    paw.box((-4, -0.4, -3), (2, 4, 1), A.Fur(*P.WOLF, noise=0.2), grow=0.1)
    paw.box((-4, 3.4, -3.2), (2, 1, 1), A.Solid('#26221f', '#3a3430'), grow=-0.1)
    W.mirror(paw, name='paw_left')
    icon = A.icon([
        '................',
        '...o......o.....',
        '..oLo....oLo....',
        '..oHHooooHHo....',
        '..oHHHHHHHHHo...',
        '.oHHHAHHHHAHHo..',
        '.oHHHHHHHHHHHMo.',
        '.oMHHHHHHHHMMMNo',
        '.oMMHHHHHHMMMMoo',
        '.oMMMWMMMMWMMo..',
        '.oDMMMMMMMMMMo..',
        '.oDDMMMMMMMDDo..',
        '..oDDDMMMDDDo...',
        '..oDoDDoDDoDo...',
        '...o.oo.oo.o....',
        '................'], {'o': '#2a2826', 'L': '#b7b3ab', 'H': '#9a9690', 'M': '#7a7772', 'D': '#5b5956',
                              'A': '#e09c26', 'N': '#151414', 'W': '#ece4cf'})
    return W, icon


@item('antler_frontlet', 'primitive', 'head')
def antler_frontlet():
    W = A.Worn('antler_frontlet', 64, 32)
    h = W.bone('head', 'cap', fit='helmet')
    h.box((-4, -9, -4), (8, 2, 8), A.Fur(*P.MAMMOTH, noise=0.2), grow=0.5)
    h.box((-2, -9.2, -5), (4, 3, 1), A.Ivory(*P.BONE, noise=0.12), grow=0.05)
    h.box((-4, -7, -4), (8, 1, 8), P.sinew(), grow=0.58)
    beam = W.bone('head', 'antler_right', pivot=(-2.4, -9.2, -1.2), rot=(-12, 0, -22), fit='helmet')
    tip_up = A.Tapered(*P.ANTLER, tip='-y', noise=0.12)
    beam.box((-0.5, -9, -0.5), (1, 9, 1), tip_up)
    beam.box((-0.5, -3, -3.5), (1, 1, 3), A.Tapered(*P.ANTLER, tip='-z', noise=0.1))
    beam.box((-0.5, -6.2, -2.8), (1, 1, 2), A.Tapered(*P.ANTLER, tip='-z', noise=0.1))
    beam.box((-2.6, -8.2, -0.5), (2, 1, 1), A.Tapered(*P.ANTLER, tip='-x', noise=0.1))
    beam.box((-0.5, -11, -1.5), (1, 2, 1), tip_up)
    W.mirror(beam, name='antler_left')
    icon = A.icon([
        '..w..........w..',
        '.wA.w......w.Aw.',
        '..AwA......AwA..',
        '..AA.w....w.AA..',
        '...AA......AA...',
        'wA..AA....AA..Aw',
        '..AAAA....AAAA..',
        '....AAAooAAA....',
        '.....oFFFFo.....',
        '....oFbbbbFo....',
        '...oFFbllbFFo...',
        '...oFFbbbbFFo...',
        '...oSSSSSSSSo...',
        '....oFFFFFFo....',
        '.....oooooo.....',
        '................'], {'o': '#2c1a10', 'w': '#e8dcc2', 'A': '#a08563', 'F': '#6f4a2c', 'b': '#d6cbad',
                              'l': '#ece4cf', 'S': '#a0805a'})
    return W, icon


# -------------------------------------------------------------------- primitive: necklace

@item('fang_necklace', 'primitive', 'necklace')
def fang_necklace():
    W = A.Worn('fang_necklace')
    cord = P.fiber()
    ring = W.bone('body', 'cord', fit='chest')
    ring.box((-4, 0, -2), (8, 1, 4), cord, grow=(0.27, -0.2, 0.27))
    side = W.bone('body', 'strand_right', pivot=(-3.2, 0.3, -2.3), rot=(0, 0, -38), fit='chest')
    side.box((-0.5, 0, 0), (1, 4, 0), cord, faces=('north', 'south'))
    W.mirror(side, name='strand_left')
    fangs = W.bone('body', 'fangs', fit='chest', anim='sway', pivot=(0, 3.2, -2.45))
    tooth = A.Tapered(*P.IVORY, tip='+y', noise=0.1)
    small = A.Tapered(*P.IVORY, tip='+y', noise=0.1)
    fangs.box((-1, 0, -0.35), (2, 4, 1), tooth, grow=(-0.25, 0, -0.3))
    fangs.box((-2.6, -0.8, -0.3), (1, 3, 1), small, grow=(-0.05, 0, -0.3))
    fangs.box((1.6, -0.8, -0.3), (1, 3, 1), small, grow=(-0.05, 0, -0.3))
    fangs.box((-3.9, -1.9, -0.25), (1, 2, 1), small, grow=(-0.1, 0, -0.3))
    fangs.box((2.9, -1.9, -0.25), (1, 2, 1), small, grow=(-0.1, 0, -0.3))
    bead = A.Solid(P.OCHRE, '#7a2e1c')
    for x in (-1.8, 0.8, -3.2, 2.2):
        fangs.box((x, -0.6 if abs(x) < 2 else -1.5, -0.35), (1, 1, 1), bead, grow=-0.22)
    icon = A.icon([
        '................',
        '..cc........cc..',
        '..c..........c..',
        '...c........c...',
        '...c........c...',
        '....c......c....',
        '....oc....co....',
        '...oWo.cc.oWo...',
        '...oWoRccRoWo...',
        '...oIo.oo.oIo...',
        '....o..oWo.o....',
        '.......oWo......',
        '.......oIo......',
        '.......oIo......',
        '........o.......',
        '................'], {'c': '#77763f', 'o': '#4a4232', 'W': '#f6f0e1', 'I': '#cdbf9c', 'R': '#a8402a'})
    return W, icon


# ------------------------------------------------------------------------ primitive: back

@item('membrane_glider', 'primitive', 'back')
def membrane_glider():
    W = A.Worn('membrane_glider', 64, 64)
    mount = W.bone('body', 'mount', fit='chest')
    mount.box((-1, -0.5, 2), (2, 4, 1), P.bone(), grow=0.1)
    mount.box((-4, 0, -2), (1, 1, 4), P.leather(), grow=0.28)
    mount.box((3, 0, -2), (1, 1, 4), P.leather(), grow=0.28)
    wing = W.bone('body', 'wing_right', pivot=(-1, 0.4, 2.7), rot=(90, 0, -90), open=(0, -12, 6), anim='wing',
                  fit='chest')
    wing.box((-11, -0.5, -0.5), (11, 1, 1), A.Ivory(*P.BONE, noise=0.1), grow=-0.15)
    wing.box((-11, 0, 0), (11, 0, 4), A.Membrane(*P.MEMBRANE, noise=0.14), faces=('down', 'up'))
    wing.box((-6, -0.3, 0), (1, 1, 4), A.Ivory(*P.BONE, noise=0.1), grow=(-0.3, -0.35, 0))
    W.mirror(wing, name='wing_left')
    icon = A.icon([
        '................',
        '................',
        '.ooo........ooo.',
        'oBBBoo....ooBBBo',
        'oMMMBBo..oBBMMMo',
        '.oMMmMBooBMmMMo.',
        '..oMMmMBBMmMMo..',
        '...oMMmBBmMMo...',
        '....oMMBBMMo....',
        '.....oMLLMo.....',
        '......oLLo......',
        '.....oLooLo.....',
        '......o..o......',
        '................',
        '................',
        '................'], {'o': '#3b2a22', 'B': '#d6cbad', 'M': '#9a705c', 'm': '#7d5646', 'L': '#6b4529'})
    return W, icon


@item('pack_frame', 'primitive', 'back')
def pack_frame():
    W = A.Worn('pack_frame', 64, 64)
    frame = W.bone('body', 'frame', fit='chest')
    rod = A.Grain(*P.WOOD, streak=0.5, noise=0.12)
    frame.box((-4, -3, 3), (1, 15, 1), rod)
    frame.box((3, -3, 3), (1, 15, 1), rod)
    frame.box((-4, -4, 3), (8, 1, 1), rod)
    frame.box((-3, 4, 3.1), (6, 1, 1), rod, grow=-0.1)
    frame.box((-3, 9, 3.1), (6, 1, 1), rod, grow=-0.1)
    frame.box((-3, -1, 4), (6, 8, 3), A.Grain(*P.RAWHIDE, streak=0.3, noise=0.14))
    frame.box((-3, 1, 4), (6, 1, 3), P.fiber(), grow=0.18)
    frame.box((-3, 5, 4), (6, 1, 3), P.fiber(), grow=0.18)
    frame.box((-2, -3, 4.5), (4, 2, 2), A.Fur(*P.MAMMOTH, noise=0.2))
    strap = W.bone('body', 'strap_right', fit='chest')
    strap.box((-3.5, -0.3, -2.3), (1, 1, 5), P.leather(), grow=0.05)
    strap.box((-3.5, 0, -2.35), (1, 6, 0), P.leather(), faces=('north', 'south'))
    W.mirror(strap, name='strap_left')
    icon = A.icon([
        '...oo......oo...',
        '...oWoooooooWo..',
        '...oW.FFFF..Wo..',
        '...oWoFFFFo.Wo..',
        '...oWHHHHHHHWo..',
        '...oWHhhhhhHWo..',
        '...oWcccccccWo..',
        '...oWHhhhhhHWo..',
        '...oWWWWWWWWWo..',
        '...oWHhhhhhHWo..',
        '...oWcccccccWo..',
        '...oWHHHHHHHWo..',
        '...oWoooooooWo..',
        '...oWWWWWWWWWo..',
        '...oWo.....oWo..',
        '...oo.......oo..'], {'o': '#2e1f14', 'W': '#7b5634', 'H': '#b99268', 'h': '#9c7651', 'c': '#949152',
                              'F': '#6f4a2c'})
    return W, icon


class Wrap(A.Mat):
    """Puttee: a strip wound diagonally round the shin, each turn overlapping the last."""

    def pattern(self, c):
        k = (math.floor(c.y) * 2 + math.floor(c.x + c.z)) % 4
        return (0.25 if k == 0 else 0.55 + 0.1 * (k % 2)) + (c.rng.random() - 0.5) * self.noise, 255


class Bracer(A.Mat):
    """Beaker-culture wristguard: ground greenstone with a drilled hole near each end."""

    def pattern(self, c):
        if c.face == 'north' and c.i in (0, c.fw - 1) and c.j in (1, c.fh - 2):
            return (28, 30, 26), 255
        t = 0.55 + (c.rng.random() - 0.5) * self.noise
        if c.face == 'north' and c.i == 1:
            t += 0.25
        return t, 255


class Fin(A.Mat):
    """Flipper blade: webbing between long rays, wider and notched at the tip."""

    def pattern(self, c):
        if c.face not in ('down', 'up'):
            return 0.3, 255
        nx = c.x / max(c.w, 1)
        nz = c.z / max(c.d, 1)            # 0 at the tip (front), 1 at the heel
        half = 0.28 + (1 - nz) * 0.22
        if abs(nx - 0.5) > half:
            return 0, 0
        if nz < 0.12 and math.floor(c.x) % 2 == 1:
            return 0, 0
        ray = math.floor(c.x) % 2 == 0
        return (0.2 if ray else 0.62) + (c.rng.random() - 0.5) * self.noise, 255


class Pebbles(A.Mat):
    """River stones: rounded look from darker corners."""

    def pattern(self, c):
        corner = (c.i in (0, c.fw - 1)) and (c.j in (0, c.fh - 1))
        if corner and c.fw > 1 and c.fh > 1:
            return 0.05, 255
        return 0.55 + (c.rng.random() - 0.5) * self.noise, 255


@item('hide_quiver', 'primitive', 'back')
def hide_quiver():
    W = A.Worn('hide_quiver', 64, 64)
    q = W.bone('body', 'quiver', pivot=(0.5, 6, 3.6), rot=(0, 0, -32), fit='chest')
    q.box((-1.5, -7, -1.5), (3, 12, 3), A.Grain(*P.LEATHER, streak=0.3, noise=0.16))
    q.box((-1.5, -7, -1.5), (3, 1, 3), A.Solid('#3b2618', '#4e3220'), grow=0.25)
    q.box((-1.5, 4, -1.5), (3, 1, 3), A.Solid('#3b2618', '#4e3220'), grow=0.2)
    q.box((-1.5, -3, -1.5), (3, 1, 3), P.fiber(), grow=0.15)
    shaft = A.Grain(*P.WOOD, noise=0.1)
    for (ax, az, color) in ((-0.9, -0.4, P.FEATHER_WHITE), (0.5, 0.1, ('#6e2418', '#9c3322', '#c24a33', '#d9745a')),
                            (-0.2, 0.9, P.FEATHER_WHITE)):
        q.box((ax, -9.4, az), (1, 3, 1), shaft, grow=(-0.38, 0, -0.38))
        feather = A.Feather(*color, noise=0.1)
        q.box((ax - 0.5, -10.4, az + 0.5), (2, 2, 0), feather, faces=('north', 'south'))
        q.box((ax + 0.5, -10.4, az - 0.5), (0, 2, 2), feather, faces=('east', 'west'))
    strap = W.bone('body', 'strap', pivot=(0, 5.5, -2.3), rot=(0, 0, -40), fit='chest')
    strap.box((-0.5, -8, 0), (1, 16, 0), P.leather(), faces=('north', 'south'))
    W.bone('body', 'shoulder', fit='chest').box((-4, -0.2, -2), (1, 1, 4), P.leather(), grow=0.22)
    icon = A.icon([
        '................',
        '...........w..r.',
        '..........wwbrr.',
        '..........bw.r..',
        '.........oRRRo..',
        '........oLLHHo..',
        '.......oLLHHo...',
        '......oLLHHo....',
        '.....oCCCCo.....',
        '....oLLHHo......',
        '...oLLHHo.......',
        '..oLLHHo........',
        '..oLHHo.........',
        '..oRRo..........',
        '...oo...........',
        '................'], {'o': '#2b1a10', 'R': '#3b2618', 'L': '#9d6e45', 'H': '#6b4529', 'C': '#949152',
                              'w': '#f4f4ee', 'r': '#c24a33', 'b': '#7b5634'})
    return W, icon


@item('fur_mantle', 'primitive', 'back')
def fur_mantle():
    W = A.Worn('fur_mantle', 64, 64)
    fur = A.Fur(*P.MAMMOTH, noise=0.22)
    collar = W.bone('body', 'collar', fit='chest')
    collar.box((-4, -0.6, -2), (8, 2, 4), A.Fur(*P.MAMMOTH, noise=0.22), grow=(0.95, 0.2, 0.95))
    collar.box((-0.5, 0.6, -3.3), (1, 1, 1), A.Tapered(*P.IVORY, tip='+y'), grow=0.05)
    cape = W.bone('body', 'cape', pivot=(0, 0.2, 2.6), anim='cape', fit='chest')
    cape.box((-5, 0, 0), (10, 14, 1), fur)
    icon = A.icon([
        '................',
        '...oooooooooo...',
        '..oFFFFFFFFFFo..',
        '.oFfFFFFFFFfFFo.',
        '.oFFFFFiiFFFFFo.',
        '.oDFFFFiiFFFFDo.',
        '..oDFFFFFFFFDo..',
        '..oDFFfFFFFFDo..',
        '..oDFFFFFFfFDo..',
        '..oDFFFFFFFFDo..',
        '..oDFfFFFFFFDo..',
        '..oDFFFFFFFFDo..',
        '..oDDFDFFDFDDo..',
        '..oDoDoDDoDoDo..',
        '...o.o.oo.o.o...',
        '................'], {'o': '#24150d', 'F': '#6f4a2c', 'f': '#8a613c', 'D': '#56351f', 'i': '#e6dcc3'})
    return W, icon


# ------------------------------------------------------------------------ primitive: body

@item('ghillie_wrap', 'primitive', 'body')
def ghillie_wrap():
    W = A.Worn('ghillie_wrap', 64, 64)
    leaves = A.Leaves(*P.LEAF, noise=0.2)
    torso = W.bone('body', 'torso', fit='chest')
    torso.box((-4, 0, -2), (8, 12, 4), leaves, grow=0.45)
    tuft = W.bone('body', 'shoulder_right', fit='chest')
    tuft.box((-5.6, -1.2, -2.6), (3, 3, 5), A.Leaves(*P.LEAF, noise=0.2))
    W.mirror(tuft, name='shoulder_left')
    strands = W.bone('body', 'strands', fit='chest')
    for k, x in enumerate((-3.5, -1.6, 0.4, 2.3)):
        strands.box((x, 11.5, -2.6), (1, 3, 0), A.Grain(*(P.LEAF if k % 2 else P.FIBER), noise=0.2),
                    faces=('north', 'south'))
        strands.box((x + 0.6, 11.5, 2.6), (1, 3, 0), A.Grain(*(P.FIBER if k % 2 else P.LEAF), noise=0.2),
                    faces=('north', 'south'))
    W.arm(lambda b: b.box((-3, -2, -2), (4, 6, 4), A.Leaves(*P.LEAF, noise=0.2), grow=0.42), sided=False)
    icon = A.icon([
        '................',
        '..gG.oooooo.Gg..',
        '.gGGoLgLLgLoGGg.',
        '.GgoLLgLlLLLogG.',
        '..oLlLLgLLlLLo..',
        '..oLLgLLlLLgLo..',
        '..oLgLLLLgLLLo..',
        '..oLLlLgLLLlLo..',
        '..oLLLLLLgLLLo..',
        '..ogLLlLLLLLgo..',
        '..oLLLgLLlLLLo..',
        '..oLgLLLLLgLLo..',
        '..oLfLgfLLfLgo..',
        '...ofo.fo.fo....',
        '....f..f...f....',
        '................'], {'o': '#1a2c12', 'L': '#517a33', 'l': '#86ad52', 'g': '#3d6128', 'G': '#6a9440',
                              'f': '#949152'})
    return W, icon


@item('bone_vest', 'primitive', 'body')
def bone_vest():
    W = A.Worn('bone_vest', 64, 64)
    plates = Lamellar(P.BONE, '#4e3220', noise=0.1)
    torso = W.bone('body', 'torso', fit='chest')
    torso.box((-4, 0, -2), (8, 10, 4), plates, grow=0.42)
    torso.box((-4, 9.6, -2), (8, 1, 4), P.leather(), grow=0.5)
    W.arm(lambda b: b.box((-3, -2, -2), (4, 3, 4), Lamellar(P.BONE, '#4e3220', noise=0.1), grow=0.5), sided=False)
    icon = A.icon([
        '................',
        '..oooo....oooo..',
        '.oWwWwo..oWwWwo.',
        '.oWwWwoooowWwWo.',
        '.oLLLLLLLLLLLLo.',
        '..oWwWwWwWwWwo..',
        '..oWwWwWwWwWwo..',
        '..oLLLLLLLLLLo..',
        '..oWwWwWwWwWwo..',
        '..oWwWwWwWwWwo..',
        '..oLLLLLLLLLLo..',
        '..oWwWwWwWwWwo..',
        '..oWwWwWwWwWwo..',
        '..oBBBBBBBBBBo..',
        '...oooooooooo...',
        '................'], {'o': '#3a3024', 'W': '#e6dcc3', 'w': '#b9ab8a', 'L': '#4e3220', 'B': '#855a36'})
    return W, icon


# ------------------------------------------------------------------------ primitive: legs

@item('thornproof_wraps', 'primitive', 'legs')
def thornproof_wraps():
    W = A.Worn('thornproof_wraps')

    def build(b):
        b.box((-2, 4, -2), (4, 7, 4), Wrap(*P.RAWHIDE, noise=0.12), grow=0.3)
        b.box((-2, 4, -2), (4, 1, 4), P.sinew(), grow=0.42)
        b.box((-2, 10, -2), (4, 1, 4), P.sinew(), grow=0.42)
    W.legs(build, fit='legs')
    icon = A.icon([
        '................',
        '..oooo....oooo..',
        '..oSSo....oSSo..',
        '..oLHo....oLHo..',
        '..oHLo....oHLo..',
        '..oLLo....oLLo..',
        '..oDLo....oDLo..',
        '..oLDo....oLDo..',
        '..oLLo....oLLo..',
        '..oDLo....oDLo..',
        '..oLDo....oLDo..',
        '..oLLo....oLLo..',
        '..oSSo....oSSo..',
        '..oooo....oooo..',
        '................',
        '................'], {'o': '#3d2b1b', 'S': '#a0805a', 'L': '#b99268', 'H': '#cfae84', 'D': '#7a5a3c'})
    return W, icon


@item('croc_waders', 'primitive', 'legs')
def croc_waders():
    W = A.Worn('croc_waders')

    def build(b):
        b.box((-2, 0, -2), (4, 12, 4), A.Scales(*P.CROC, noise=0.12, cell=3), grow=0.36)
        b.box((-2, 0, -2), (4, 1, 4), P.leather(), grow=0.46)
        b.box((-2, 5, -2.9), (4, 2, 1), A.Scales(*P.CROC, noise=0.1, cell=2), grow=(0.1, 0, -0.2))
    W.legs(build, fit='legs')
    icon = A.icon([
        '................',
        '..oooooooooooo..',
        '..oBBBBBBBBBBo..',
        '..oSsSsoOsSsSo..',
        '..osSsSooSsSso..',
        '..oSsSso.oSsSo..',
        '..oKKKKo.oKKKo..',
        '..osSsSo.osSso..',
        '..oSsSso.oSsSo..',
        '..osSsSo.osSso..',
        '..oSsSso.oSsSo..',
        '..osSsSo.osSso..',
        '..oSsSso.oSsSo..',
        '..oooooo.ooooo..',
        '................',
        '................'], {'o': '#1c2414', 'B': '#6b4529', 'S': '#5c6b3b', 's': '#44522e', 'K': '#77864c',
                              'O': '#1c2414'})
    return W, icon


# ------------------------------------------------------------------------ primitive: feet

@item('snowshoes', 'primitive', 'feet')
def snowshoes():
    W = A.Worn('snowshoes', 64, 32)

    def build(b):
        rim = A.Grain(*P.WOOD, streak=0.4, noise=0.12)
        for x0, z0, w, d in ((-2, -6, 4, 1), (-2, 4, 4, 1), (-3, -5, 1, 9), (2, -5, 1, 9)):
            b.box((x0, 11.2, z0), (w, 1, d), rim, grow=(0, -0.2, 0))
        b.box((-2, 11.55, -5), (4, 0, 9), Lattice(P.WOOD, P.SINEW, noise=0.12), faces=('down', 'up'))
        b.box((-2, 10, -2), (4, 1, 4), P.sinew(), grow=0.36)
        b.box((-2, 11, -2.4), (4, 1, 1), P.sinew(), grow=0.1)
    W.legs(build, fit='boots')
    icon = A.icon([
        '....oooooo......',
        '...oWWWWWWo.....',
        '..oWlSlSlSWo....',
        '..oWSlSlSlWo....',
        '..oWlSlSlSWo....',
        '..oWSlSlSlWo....',
        '..oWlSccSlWo....',
        '..oWScccclWo....',
        '..oWlSccSlWo....',
        '..oWSlSlSlWo....',
        '..oWlSlSlSWo....',
        '...oWSlSlWo.....',
        '...oWlSlSWo.....',
        '....oWWWWo......',
        '.....oooo.......',
        '................'], {'o': '#2c1d12', 'W': '#7b5634', 'S': '#bf9f74', 'l': '#4a3320', 'c': '#a0805a'})
    return W, icon


@item('bone_skates', 'primitive', 'feet')
def bone_skates():
    W = A.Worn('bone_skates', 64, 32)

    def build(b):
        runner = A.Ivory(*P.BONE, noise=0.12)
        b.box((-0.5, 11.2, -4.5), (1, 1, 8), runner, grow=(0, -0.1, 0))
        b.box((-0.5, 10.2, -5.2), (1, 1, 1), runner, grow=(0, 0, -0.05))
        b.box((-0.5, 9.4, -4.8), (1, 1, 1), runner, grow=(-0.1, 0, -0.15))
        b.box((-0.5, 10.4, 3.4), (1, 1, 1), runner, grow=(0, 0, -0.1))
        b.box((-2, 10, -2), (4, 1, 4), P.leather(), grow=0.36)
        b.box((-2, 11, -2), (4, 1, 4), P.leather(), grow=0.34)
    W.legs(build, fit='boots')
    icon = A.icon([
        '................',
        '................',
        '................',
        '................',
        '....oooo........',
        '...oLLLLo.......',
        '...oLHHLo.......',
        '...oLLLLooooo...',
        '...oLHHHHHHHLo..',
        '...oLLLLLLLLLo..',
        '..oooooooooooo..',
        '.oWWWWWWWWWWWWo.',
        '.oWBBBBBBBBBBWo.',
        '..ooooooooooooo.',
        '................',
        '................'], {'o': '#2e2419', 'L': '#6b4529', 'H': '#855a36', 'W': '#ece4cf', 'B': '#b9ab8a'})
    return W, icon


@item('stalker_moccasins', 'primitive', 'feet')
def stalker_moccasins():
    W = A.Worn('stalker_moccasins', 64, 32)

    def build(b):
        soft = A.Grain(*P.RAWHIDE, streak=0.25, noise=0.14)
        b.box((-2, 9, -2), (4, 3, 4), soft, grow=0.32)
        b.box((-2, 10, -3), (4, 2, 1), soft, grow=(0.2, 0.2, 0.1))
        b.box((-2, 8, -2), (4, 1, 4), A.Fur(*P.RAWHIDE, noise=0.2), grow=0.46)
        b.box((-1, 10, -3.35), (2, 1, 0), A.Beads(P.RAWHIDE[0], ('#a8402a', '#e6dcc3', '#3b6e8a'), every=1),
              faces=('north',))
    W.legs(build, fit='boots')
    icon = A.icon([
        '................',
        '................',
        '................',
        '.....ofofofo....',
        '.....oFFFFFo....',
        '.....oHHHHHo....',
        '.....oHLLLHo....',
        '.....oHLLLHo....',
        '....oHLLLLHooo..',
        '...oHLLLLLLLHHo.',
        '..oHLLLLrwbLLHo.',
        '..oHLLLLLLLLLHo.',
        '..oDDDDDDDDDDDo.',
        '...ooooooooooo..',
        '................',
        '................'], {'o': '#3a2a1a', 'f': '#cfae84', 'F': '#9c7651', 'H': '#9c7651', 'L': '#cfae84',
                              'D': '#7a5a3c', 'r': '#a8402a', 'w': '#e6dcc3', 'b': '#3b6e8a'})
    return W, icon


@item('flipper_sandals', 'primitive', 'feet')
def flipper_sandals():
    W = A.Worn('flipper_sandals', 64, 32)

    def build(b):
        b.box((-2.5, 11.7, -8.5), (5, 0, 7), Fin(*P.MARINE, noise=0.12), faces=('down', 'up'))
        b.box((-2, 11.4, -2), (4, 1, 4), A.Grain(*P.MARINE, noise=0.12), grow=(0.1, -0.2, 0.1))
        b.box((-2, 10, -2), (4, 1, 4), P.leather(), grow=0.36)
        b.box((-2, 11, -2.2), (4, 1, 1), P.leather(), grow=0.12)
    W.legs(build, fit='boots')
    icon = A.icon([
        '................',
        '..o.o.o.o.o.....',
        '..oMoMoMoMo.....',
        '..oMmMmMmMo.....',
        '..oMmMmMmMo.....',
        '...oMmMmMo......',
        '...oMmMmMo......',
        '...oMmMmMo......',
        '....oMmMo.......',
        '....oLLLo.......',
        '....oMMMo.......',
        '....oLLLo.......',
        '....oMMMoo......',
        '....oMMMMMo.....',
        '.....ooooo......',
        '................'], {'o': '#1b252d', 'M': '#5f7a8c', 'm': '#445a6b', 'L': '#6b4529'})
    return W, icon


# ------------------------------------------------------------------------ primitive: hands

@item('climbing_claws', 'primitive', 'hands')
def climbing_claws():
    W = A.Worn('climbing_claws', 64, 32)

    def glove(b):
        b.box((-3, 6, -2), (4, 4, 4), A.Grain(*P.LEATHER, streak=0.25, noise=0.14), grow=0.3)
        b.box((-3, 7, -2), (4, 1, 4), P.sinew(), grow=0.42)
    W.arm(glove, sided=False)
    claw = A.Tapered(*P.KERATIN, tip='-z', noise=0.1)
    for arms, xs, px in (('wide', (-2.8, -1.4, 0), 0), ('slim', (-2.3, -1.1, 0.1), 0)):
        b = W.bone('right_arm', f'claws_right_{arms}', pivot=(px, 9.3, -2.2), rot=(28, 0, 0), arms=arms)
        for x in xs:
            b.box((x, 0, -2.4), (1, 1, 2), claw, grow=(-0.13, -0.12, 0))
        W.mirror(b, 'left_arm', f'claws_left_{arms}')
    icon = A.icon([
        '................',
        '................',
        '....oooooo......',
        '...oLLLLLLo.....',
        '...oLHHHHLo.....',
        '...oLHHHHLo.....',
        '...oSSSSSSo.....',
        '...oLHHHHLo.....',
        '...oLLLLLLooo...',
        '...oKoKoKoKKKo..',
        '...oKKoKKoKKo...',
        '....oKKoKKoKo...',
        '.....oKKoKKo....',
        '......oKoKo.....',
        '.......o.o......',
        '................'], {'o': '#15120f', 'L': '#6b4529', 'H': '#855a36', 'S': '#a0805a', 'K': '#453d36'})
    return W, icon


@item('scythe_claws', 'primitive', 'hands')
def scythe_claws():
    W = A.Worn('scythe_claws', 64, 32)
    W.arm(lambda b: b.box((-3, 6, -2), (4, 4, 4), A.Grain(*P.LEATHER, streak=0.25, noise=0.14), grow=0.3),
          sided=False)
    blade = A.Tapered(*P.KERATIN, tip='+y', noise=0.1)
    for arms, xs in (('wide', (-2.7, -1.5, -0.3)), ('slim', (-2.1, -1.0, 0.1))):
        for k, x in enumerate(xs):
            b = W.bone('right_arm', f'blade{k}_right_{arms}', pivot=(x, 9.6, -1.6 + k * 0.2), rot=(-38 + k * 4, 0, 0),
                       arms=arms)
            b.box((-0.5, 0, -0.5), (1, 7, 1), blade, grow=(-0.18, 0, -0.1))
            W.mirror(b, 'left_arm', f'blade{k}_left_{arms}')
    icon = A.icon([
        '................',
        '..oooooo........',
        '.oLLLLLLo.......',
        '.oLHHHHLo.......',
        '.oLHHHHLo.......',
        '.oLLLLLLo.......',
        '..oKoKoKo.......',
        '...oKoKoKo......',
        '....oKoKoKo.....',
        '.....oKoKoKo....',
        '......oKoKoKo...',
        '.......okoKoKo..',
        '........okokoKo.',
        '.........okoko..',
        '..........o.o...',
        '................'], {'o': '#15120f', 'L': '#6b4529', 'H': '#855a36', 'K': '#5f554b', 'k': '#2f2a26'})
    return W, icon


# ------------------------------------------------------------------- primitive: wrist, ring

@item('archer_wristguard', 'primitive', 'bracelet')
def archer_wristguard():
    W = A.Worn('archer_wristguard', 64, 32)

    def build(b):
        b.box((-3, 5, -2.5), (4, 4, 1), Bracer(*P.GREENSTONE, noise=0.1), grow=(0, 0, -0.25))
        b.box((-3, 5.5, -2), (4, 1, 4), P.leather(), grow=0.3)
        b.box((-3, 7.5, -2), (4, 1, 4), P.leather(), grow=0.3)
    W.arm(build)
    icon = A.icon([
        '................',
        '................',
        '....oooooooo....',
        '...oGgggggggo...',
        '..oGGkGGGGkGgo..',
        '..oGGGGGGGGGgo..',
        '.LLGGGGGGGGGgLL.',
        '.LLGGGGGGGGGgLL.',
        '..oGGGGGGGGGgo..',
        '.LLGGGGGGGGGgLL.',
        '.LLGGGGGGGGGgLL.',
        '..oGGkGGGGkGgo..',
        '...oggggggggo...',
        '....oooooooo....',
        '................',
        '................'], {'o': '#26302a', 'G': '#6f7d6e', 'g': '#566457', 'k': '#1c201d', 'L': '#6b4529'})
    return W, icon


@item('thumb_ring', 'primitive', 'ring')
def thumb_ring():
    W = A.Worn('thumb_ring', 32, 32)

    def build(b):
        b.box((-0.2, 8.4, -2.9), (1, 1, 1), A.Ivory(*P.IVORY, noise=0.1), grow=0.18)
        b.box((-0.2, 9.1, -3.3), (1, 1, 1), A.Ivory(*P.BONE, noise=0.1), grow=(0.05, -0.28, 0.12))
    W.arm(build)
    icon = A.icon([
        '................',
        '................',
        '................',
        '.....oooooo.....',
        '....oWWWWWWo....',
        '...oWlllllsWo...',
        '..oWlo....osWo..',
        '..oWlo....osWo..',
        '..oWso....osWo..',
        '..oWso....osWo..',
        '...oWsssssWWo...',
        '....oWWWWWWo....',
        '.....oooooo.....',
        '................',
        '................',
        '................'], {'o': '#5e5442', 'W': '#e6dcc3', 'l': '#f6f0e1', 's': '#b9ab8a'})
    return W, icon


# --------------------------------------------------------------------- primitive: belt, charm

def belt(W, mat=None):
    band = W.bone('body', 'belt', fit='chest')
    band.box((-4, 10, -2), (8, 1, 4), mat or P.fiber(), grow=0.32)
    return band


@item('ember_carrier', 'primitive', 'belt')
def ember_carrier():
    W = A.Worn('ember_carrier', 64, 32)
    belt(W)
    pot = W.bone('body', 'pot', pivot=(-5.4, 10.4, 0), anim='sway', fit='chest')
    pot.box((-1.5, 0.4, -1.5), (3, 3, 3), Ember(noise=0.14))
    pot.box((-1.5, 0.4, -1.5), (3, 1, 3), A.Solid(*P.CLAY[:2]), grow=(0.22, -0.1, 0.22))
    pot.box((-0.5, -0.6, 0), (1, 1, 0), P.fiber(), faces=('north', 'south'))
    icon = A.icon([
        '................',
        '.......y........',
        '......yOy.......',
        '.....yOrOy......',
        '....oyrOryo.....',
        '...oCCCCCCCo....',
        '...oDDDDDDDo....',
        '..oCCcCCCcCCo...',
        '..oCcCCCCCcCo...',
        '..oCCCCcCCCCo...',
        '..oCCCCCCCCCo...',
        '..oDCCCCCCCDo...',
        '...oDDDDDDDo....',
        '....ooooooo.....',
        '................',
        '................'], {'o': '#3d1f10', 'C': '#ad6a3a', 'c': '#c4844e', 'D': '#7a3f22', 'y': '#ffd05a',
                              'O': '#ff9a2e', 'r': '#ff6a1a'})
    return W, icon


@item('herbal_pouch', 'primitive', 'belt')
def herbal_pouch():
    W = A.Worn('herbal_pouch', 64, 32)
    belt(W, P.leather())
    pouch = W.bone('body', 'pouch', pivot=(3.6, 10.2, -2.6), anim='sway', fit='chest')
    pouch.box((-1, 0, -1), (2, 3, 2), A.Grain(*P.RAWHIDE, noise=0.14), grow=0.05)
    pouch.box((-1, -0.3, -1), (2, 1, 2), P.leather(), grow=0.15)
    pouch.box((-0.8, -2.2, 0), (1, 2, 0), A.Grain(*P.LEAF, noise=0.2), faces=('north', 'south'))
    pouch.box((0, -2.8, 0.3), (0, 2, 1), A.Grain(*P.LEAF, noise=0.2), faces=('east', 'west'))
    pouch.box((0.2, -1.6, -0.3), (1, 1, 1), A.Solid('#b3261e', '#d6443a'), grow=-0.25)
    icon = A.icon([
        '................',
        '......g.G.......',
        '.....gGgGg.x....',
        '......GgG.xXx...',
        '....G.gGg..x....',
        '....oooooooo....',
        '...oLLLLLLLLo...',
        '...oHHHHHHHHo...',
        '..oRRRRRRRRRRo..',
        '..oRrRRRRRRrRo..',
        '..oRRRRRRRRRRo..',
        '..oRRRRRRRRRRo..',
        '..oDRRRRRRRRDo..',
        '...oDDDDDDDDo...',
        '....oooooooo....',
        '................'], {'o': '#3a2718', 'L': '#6b4529', 'H': '#4e3220', 'R': '#b99268', 'r': '#cfae84',
                              'D': '#9c7651', 'g': '#3d6128', 'G': '#6a9440', 'x': '#b3261e', 'X': '#d6443a'})
    return W, icon


@item('diver_stones', 'primitive', 'belt')
def diver_stones():
    W = A.Worn('diver_stones', 64, 32)
    belt(W)
    stone = Pebbles(*P.RIVERSTONE, noise=0.14)  # noqa
    for name, pivot in (('stone_right', (-5.2, 10.6, -0.6)), ('stone_left', (5.2, 10.6, -0.6)),
                        ('stone_back', (0, 10.6, 3.1))):
        b = W.bone('body', name, pivot=pivot, anim='sway', fit='chest')
        b.box((-1, 1, -1), (2, 3, 2), stone, grow=0.2)
        b.box((-0.5, 0, 0), (1, 1, 0), P.fiber(), faces=('north', 'south'))
    icon = A.icon([
        '................',
        '..ccccccccccccc.',
        '.cCcCcCcCcCcCcc.',
        '..c....c.....c..',
        '..c....c.....c..',
        '.oSSo.oSSo..oSSo',
        'oSssSoSssSooSssS',
        'oSsSSoSsSSooSsSS',
        'oSSSSoSSSSooSSSS',
        '.oSSo.oSSo..oSSo',
        '................',
        '................',
        '................',
        '................',
        '................',
        '................'], {'c': '#77763f', 'C': '#949152', 'o': '#2e2c2a', 'S': '#8a8781', 's': '#a7a49d'})
    return W, icon


@item('tally_bone', 'primitive', 'charm')
def tally_bone():
    W = A.Worn('tally_bone', 32, 32)
    b = W.bone('body', 'tally', pivot=(4.5, 10.8, -1.2), anim='sway', fit='chest')
    b.box((-0.5, 0.5, -0.5), (1, 5, 1), Notched(*P.IVORY, noise=0.08))
    b.box((-0.5, -0.5, 0), (1, 1, 0), P.fiber(), faces=('north', 'south'))
    b.box((-0.5, 0, -0.5), (1, 1, 1), A.Solid(P.OCHRE), grow=(0.1, -0.2, 0.1))
    icon = A.icon([
        '................',
        '..........oo....',
        '.........oWlo...',
        '.........oKWo...',
        '........oWKo....',
        '........oKWo....',
        '.......oWKo.....',
        '.......oKWo.....',
        '......oWKo......',
        '......oKWo......',
        '.....oWKo.......',
        '.....oWWo.......',
        '....oWso........',
        '....oso.........',
        '.....o..........',
        '................'], {'o': '#5e5442', 'W': '#e6dcc3', 'l': '#f6f0e1', 's': '#b9ab8a', 'K': '#3b3226'})
    return W, icon


# ========================================================================= mystical relics

class FeatherScales(A.Mat):
    """A feathered cape: overlapping rows of feathers, pale tips, a red hem."""

    def __init__(self, *tones, hem=None, **kw):
        super().__init__(*tones, **kw)
        self.hem = A.rgb(hem) if hem else None

    def pattern(self, c):
        if self.hem and c.face in ('north', 'south') and c.j >= c.fh - 2:
            return self.hem, 255
        row = math.floor(c.y) % 3
        shift = (math.floor(c.y) // 3) % 2
        column = (c.i + shift) % 2
        if c.face in ('north', 'south') and c.j == c.fh - 1 and column == 1:
            return 0, 0
        t = (0.85 if row == 2 and column == 0 else 0.55 if row == 2 else 0.35 + 0.15 * column)
        return t + (c.rng.random() - 0.5) * self.noise, 255


def cord_necklace(W, mat=None):
    cord = mat or P.sinew()
    ring = W.bone('body', 'cord', fit='chest')
    ring.box((-4, 0, -2), (8, 1, 4), cord, grow=(0.27, -0.2, 0.27))
    side = W.bone('body', 'strand_right', pivot=(-3.2, 0.3, -2.3), rot=(0, 0, -38), fit='chest')
    side.box((-0.5, 0, 0), (1, 4, 0), cord, faces=('north', 'south'))
    W.mirror(side, name='strand_left')
    return W.bone('body', 'pendant', fit='chest', anim='sway', pivot=(0, 3.2, -2.5))


@item('amber_amulet', 'relic', 'necklace')
def amber_amulet():
    W = A.Worn('amber_amulet')
    pendant = cord_necklace(W)
    pendant.box((-1, 0, -0.5), (2, 1, 1), A.Grain(*P.GOLD, noise=0.1), grow=(0.1, -0.1, 0))
    pendant.box((-1, 0.8, -0.6), (2, 3, 1), A.Amber(*P.AMBER, noise=0.12), grow=(0, 0, -0.1))
    pendant.box((-0.5, 3.6, -0.6), (1, 1, 1), A.Amber(*P.AMBER[:3], noise=0.1), grow=(-0.1, -0.2, -0.2))
    icon = A.icon([
        '................',
        '..s..........s..',
        '...s........s...',
        '....s......s....',
        '.....s....s.....',
        '......oGGo......',
        '.....oGgggo.....',
        '.....oYYyAo.....',
        '....oYWyAAAo....',
        '....oYyAAAAo....',
        '....oyAAkAAo....',
        '....oAAAAARo....',
        '.....oAARRo.....',
        '......oRRo......',
        '.......oo.......',
        '................'], {'s': '#a0805a', 'o': '#4a2206', 'G': '#e0b43a', 'g': '#b8871f', 'Y': '#ffd680',
                              'y': '#f2a93b', 'A': '#d6761a', 'R': '#a3480b', 'W': '#fff4d0', 'k': '#3a1a04'})
    return W, icon


@item('alicorn_pendant', 'relic', 'necklace')
def alicorn_pendant():
    W = A.Worn('alicorn_pendant')
    pendant = cord_necklace(W, A.Twist('#b8871f', '#e0b43a'))
    pendant.box((-0.5, 0, -0.5), (1, 1, 1), A.Grain(*P.GOLD, noise=0.1), grow=0.16)
    pendant.box((-0.5, 1, -0.5), (1, 5, 1), A.Spiral(*P.PEARL, noise=0.08), grow=(-0.05, 0, -0.1))
    pendant.box((-0.5, 5.8, -0.5), (1, 1, 1), A.Solid(P.PEARL[3]), grow=(-0.25, 0, -0.3))
    icon = A.icon([
        '................',
        '.............G..',
        '............GgG.',
        '...........oGgo.',
        '..........oWPo..',
        '.........oPWo...',
        '........oWPPo...',
        '.......oPWPo....',
        '......oWPPo.....',
        '.....oPWPo......',
        '....oWPPo.......',
        '...oPWPo........',
        '...oWPo.........',
        '..oWo...........',
        '..oo............',
        '................'], {'o': '#6d675e', 'G': '#e0b43a', 'g': '#b8871f', 'W': '#fbf8f1', 'P': '#cfc9bf'})
    return W, icon


@item('quetzal_mantle', 'relic', 'back')
def quetzal_mantle():
    W = A.Worn('quetzal_mantle', 64, 64)
    collar = W.bone('body', 'collar', fit='chest')
    collar.box((-4, -0.5, -2), (8, 1, 4), FeatherScales('#7a1410', '#a31d16', '#c7372a', noise=0.12), grow=0.62)
    shoulder = W.bone('body', 'shoulder_right', fit='chest')
    shoulder.box((-5.8, -1, -2.6), (3, 2, 5), FeatherScales(*P.QUETZAL, noise=0.12))
    W.mirror(shoulder, name='shoulder_left')
    cape = W.bone('body', 'cape', pivot=(0, 0.2, 2.6), anim='cape', fit='chest')
    cape.box((-5, 0, 0), (10, 13, 1), FeatherScales(*P.QUETZAL, hem='#b3261e', noise=0.12))
    for k, x in enumerate((-2.5, -0.5, 1.5)):
        cape.box((x, 12.5, 0.6), (1, 6 - abs(k - 1), 0), A.Feather(*P.QUETZAL, noise=0.1), faces=('north', 'south'))
    icon = A.icon([
        '................',
        '...orrrrrrrro...',
        '..oGgGgGgGgGgo..',
        '.oGGgGGgGGgGGGo.',
        '.oGLGGLGGLGGLGo.',
        '..oGgGgGgGgGgo..',
        '..oGGLGGLGGLGo..',
        '..oGgGgGgGgGgo..',
        '..oLGGLGGLGGLo..',
        '..oGgGgGgGgGgo..',
        '..orrrrrrrrrro..',
        '...oGo.oGo.oGo..',
        '...oGo.oGo.oLo..',
        '...oLo.oGo..o...',
        '....o..oLo......',
        '........o.......'], {'o': '#062a18', 'G': '#1f9966', 'g': '#12704a', 'L': '#7fe0b0', 'r': '#b3261e'})
    return W, icon


@item('guardian_crown', 'relic', 'head')
def guardian_crown():
    W = A.Worn('guardian_crown', 64, 32)
    h = W.bone('head', 'crown', fit='helmet')
    h.box((-4, -8.4, -4), (8, 2, 8), A.Painted(A.Grain(*P.GOLD, noise=0.12), P.OCHRE, every=3, dots=True), grow=0.62)
    big = A.Tapered(*P.IVORY, tip='-y', noise=0.08)
    small = A.Tapered(*P.IVORY, tip='-y', noise=0.08)
    h.box((-0.5, -12.6, -4.9), (1, 4, 1), big, grow=(0.12, 0, 0))
    for x in (-2.9, 1.9):
        h.box((x, -11.4, -4.9), (1, 3, 1), small)
    for z in (-2.6, 0.4, 2.8):
        h.box((-4.9, -11 + (0.4 if z > 0 else 0), z), (1, 3, 1), small)
        h.box((3.9, -11 + (0.4 if z > 0 else 0), z), (1, 3, 1), small)
    for x in (-2.2, 1.2):
        h.box((x, -10.8, 3.9), (1, 3, 1), small)
    h.box((-1, -8.1, -4.95), (2, 1, 1), A.Solid(P.OCHRE, '#7a2e1c'), grow=(0, 0.1, -0.2))
    icon = A.icon([
        '................',
        '.......o........',
        '......oWo.......',
        '..o...oWo...o...',
        '.oWo..oWo..oWo..',
        '.oWo.oWIWo.oWo..',
        'oWIo.oWIWo.oIWo.',
        'oIIoooIIIoooIIo.',
        'oGGGGGGGGGGGGGGo',
        'oGgRgGgRRgGgRgGo',
        'oGGGGGGGGGGGGGGo',
        '.oggggggggggggo.',
        '..oooooooooooo..',
        '................',
        '................',
        '................'], {'o': '#3b2a08', 'W': '#f6f0e1', 'I': '#cdbf9c', 'G': '#e0b43a', 'g': '#b8871f',
                              'R': '#a8402a'})
    return W, icon


def ring(W, band_mat, stone_mat=None, grow=0.13):
    def build(b):
        b.box((-3, 9, -2), (4, 1, 4), band_mat, grow=grow)
        if stone_mat is not None:
            b.box((-1.5, 8.6, -2.75), (1, 1, 1), stone_mat, grow=0.12)
    W.arm(build)


@item('obsidian_band', 'relic', 'ring')
def obsidian_band():
    W = A.Worn('obsidian_band', 32, 32)
    ring(W, A.Glint(*P.OBSIDIAN, glints=0.12, noise=0.1), A.Glint(*P.OBSIDIAN, glints=0.3, noise=0.1))
    icon = A.icon([
        '................',
        '................',
        '.......oo.......',
        '......oPPo......',
        '.....oPLPPo.....',
        '....oKKPPKKo....',
        '...oKKoooKKKo...',
        '..oKKo....oKKo..',
        '..oKo......oKo..',
        '..oKo......oKo..',
        '..oKKo....oKKo..',
        '...oKKoooKKKo...',
        '....oKKKKKKo....',
        '.....oooooo.....',
        '................',
        '................'], {'o': '#050308', 'K': '#2e2640', 'P': '#5b4b80', 'L': '#b9a6f0'})
    return W, icon


@item('amber_ring', 'relic', 'ring')
def amber_ring():
    W = A.Worn('amber_ring', 32, 32)
    ring(W, A.Ivory(*P.BONE, noise=0.1), A.Amber(*P.AMBER, noise=0.1), grow=0.11)
    icon = A.icon([
        '................',
        '................',
        '......oooo......',
        '.....oYyAo......',
        '.....oyAARo.....',
        '....oWoAARoo....',
        '...oWWooooWWo...',
        '..oWso....osWo..',
        '..oWo......oWo..',
        '..oWo......oso..',
        '..oWso....osso..',
        '...oWWooooWso...',
        '....oWWsssso....',
        '.....oooooo.....',
        '................',
        '................'], {'o': '#4a3e2c', 'W': '#e6dcc3', 's': '#b9ab8a', 'Y': '#ffd680', 'y': '#f2a93b',
                              'A': '#d6761a', 'R': '#a3480b'})
    return W, icon


@item('serpent_ring', 'relic', 'ring')
def serpent_ring():
    W = A.Worn('serpent_ring', 32, 32)

    def build(b):
        b.box((-3, 8.4, -2), (4, 1, 4), A.Scales('#2b4a16', '#3f6a1f', '#5a8f2c', '#86b84a', noise=0.1), grow=0.13)
        b.box((-1.6, 7.9, -2.9), (1, 1, 1), A.Solid('#3f6a1f', '#5a8f2c'), grow=0.08)
        b.box((-1.6, 8.8, -3.05), (1, 1, 0), A.Solid('#f2eed8'), faces=('north',))
    W.arm(build)
    icon = A.icon([
        '................',
        '................',
        '.....oooooo.....',
        '....oGgGgGGo....',
        '...oGgoooogGo...',
        '..oGgo....oGgo..',
        '..oGo......oGo..',
        '..oGo......ogo..',
        '..oGgo....oYGo..',
        '...oGgoooGYYGo..',
        '....oGgGGYkYo...',
        '.....ooooWoWo...',
        '..........o.o...',
        '................',
        '................',
        '................'], {'o': '#132608', 'G': '#5a8f2c', 'g': '#3f6a1f', 'Y': '#86b84a', 'k': '#101010',
                              'W': '#f2eed8'})
    return W, icon


@item('kinship_bracelet', 'relic', 'bracelet')
def kinship_bracelet():
    W = A.Worn('kinship_bracelet', 64, 32)

    def build(b):
        b.box((-3, 7, -2), (4, 1, 4), A.Beads(P.MAMMOTH[2], (P.OCHRE, '#e6dcc3', '#3b6e8a', '#6f4a2c'), every=1),
              grow=0.3)
        b.box((-3.45, 7.8, -0.5), (0, 3, 1), A.Feather(*P.FEATHER_WHITE, bars=2, noise=0.1), faces=('east', 'west'))
        b.box((-3.7, 6.8, 0.6), (1, 1, 1), A.Fur(*P.WOLF, fringe=False), grow=-0.1)
    W.arm(build)
    icon = A.icon([
        '................',
        '................',
        '....oooooooo....',
        '...oRwBfRwBfo...',
        '..oBo......oRo..',
        '..ofo......owo..',
        '..oRo......oBo..',
        '..owo......ofo..',
        '...oBfRwBfRwo...',
        '....oooooooo....',
        '.........oWo....',
        '.........oWo....',
        '........oWKo....',
        '........oWo.....',
        '.........o......',
        '................'], {'o': '#2b2016', 'R': '#a8402a', 'w': '#e6dcc3', 'B': '#3b6e8a', 'f': '#6f4a2c',
                              'W': '#f4f4ee', 'K': '#8a8a86'})
    return W, icon


def totem(W, post_mat):
    t = W.bone('body', 'totem', pivot=(4.7, 10.6, -1.4), anim='sway', fit='chest')
    t.box((-0.5, -0.8, 0), (1, 1, 0), P.fiber(), faces=('north', 'south'))
    t.box((-1, 0, -1), (2, 4, 2), post_mat)
    return t


@item('raptor_totem', 'relic', 'charm')
def raptor_totem():
    W = A.Worn('raptor_totem', 32, 32)
    t = totem(W, A.Painted(A.Grain(*P.WOOD, noise=0.12), '#6a9440', every=2))
    t.box((-1, -1.6, -2.4), (2, 2, 3), A.Painted(A.Grain(*P.WOOD, noise=0.12), P.OCHRE, every=5, dots=True))
    t.box((-0.5, 3.8, -1.3), (1, 2, 1), A.Tapered(*P.KERATIN, tip='+y'), grow=(-0.1, 0, -0.1))
    icon = A.icon([
        '................',
        '....ooooo.......',
        '...oWWWWWoo.....',
        '..oWkWWWWWWo....',
        '..oWWWWWWoRo....',
        '...ooWWWo.o.....',
        '....oGGGo.......',
        '....oWWWo.......',
        '....oGGGo.......',
        '....oWWWo.......',
        '....oGGGo.......',
        '....oWWWo.......',
        '.....oKKo.......',
        '......oKo.......',
        '.......o........',
        '................'], {'o': '#24170d', 'W': '#7b5634', 'k': '#101010', 'R': '#a8402a', 'G': '#6a9440',
                              'K': '#453d36'})
    return W, icon


@item('rex_totem', 'relic', 'charm')
def rex_totem():
    W = A.Worn('rex_totem', 32, 32)
    t = totem(W, A.Painted(A.Grain(*P.WOOD, noise=0.12), P.OCHRE, every=2))
    t.box((-1.5, -2.4, -2.6), (3, 3, 4), A.Grain(*P.WOOD, noise=0.12))
    t.box((-1.5, -0.7, -2.7), (3, 1, 0), A.Beads(P.WOOD[0], ('#f6f0e1',), every=2), faces=('north',))
    icon = A.icon([
        '................',
        '...oooooooo.....',
        '..oWWWWWWWWo....',
        '..oWkWWWWWWWo...',
        '..oWWWWWWWWWo...',
        '..oTWTWTWTWo....',
        '...ooWWWooo.....',
        '....oRRRo.......',
        '....oWWWo.......',
        '....oRRRo.......',
        '....oWWWo.......',
        '....oRRRo.......',
        '....oWWWo.......',
        '.....ooo........',
        '................',
        '................'], {'o': '#24170d', 'W': '#7b5634', 'k': '#101010', 'R': '#a8402a', 'T': '#f6f0e1'})
    return W, icon


@item('argentavis_totem', 'relic', 'charm')
def argentavis_totem():
    W = A.Worn('argentavis_totem', 32, 32)
    t = totem(W, A.Painted(A.Grain(*P.FEATHER_BROWN, noise=0.12), '#e6dcc3', every=3))
    t.box((-1, -2, -1), (2, 2, 2), A.Grain(*P.FEATHER_BROWN, noise=0.12))
    t.box((-0.5, -1.6, -2.1), (1, 2, 1), A.Tapered('#6b5a2a', '#c9a640', '#e8cf6e', tip='+y'), grow=(0, 0, -0.1))
    t.box((-0.5, 4, 0), (1, 3, 0), A.Feather(*P.FEATHER_BROWN, bars=2, noise=0.1), faces=('north', 'south'))
    icon = A.icon([
        '................',
        '.....ooooo......',
        '....oBBBBBo.....',
        '....oBwBBBoo....',
        '....oBBBBYYo....',
        '.....oBBoYYo....',
        '.....oBBBooY....',
        '....oBwwwBo.....',
        '....oBBBBBo.....',
        '....oBwwwBo.....',
        '....oBBBBBo.....',
        '.....oFoFo......',
        '.....oFoFo......',
        '.....oFoFo......',
        '......o.o.......',
        '................'], {'o': '#1c130b', 'B': '#5a4028', 'w': '#e6dcc3', 'Y': '#e8cf6e', 'F': '#7a5a3a'})
    return W, icon


@item('megalodon_totem', 'relic', 'charm')
def megalodon_totem():
    W = A.Worn('megalodon_totem', 32, 32)
    t = totem(W, A.Painted(A.Grain(*P.MARINE, noise=0.12), '#e6dcc3', every=4))
    t.box((-0.5, -2, -0.5), (1, 2, 2), A.Grain(*P.MARINE, noise=0.1), grow=(0, 0, -0.1))
    t.box((-1, 1.5, -1.1), (2, 1, 0), A.Beads(P.MARINE[0], ('#f6f0e1',), every=1), faces=('north',))
    t.box((-0.5, 4, -0.6), (1, 2, 1), A.Tapered(*P.IVORY, tip='+y'), grow=(0, 0, -0.2))
    icon = A.icon([
        '................',
        '.......oo.......',
        '......oMMo......',
        '.....oMMMo......',
        '....oMMMMMo.....',
        '...oMMMMMMMo....',
        '...oMkMMMMMo....',
        '...oTTTTTTTo....',
        '....oMMMMMo.....',
        '....oWWWWWo.....',
        '....oMMMMMo.....',
        '.....oMMMo......',
        '......oWo.......',
        '......oWo.......',
        '.......o........',
        '................'], {'o': '#141c22', 'M': '#5f7a8c', 'k': '#0a0a0a', 'T': '#f6f0e1', 'W': '#e6dcc3'})
    return W, icon


# ============================================================================ trophies (drops)

TROPHIES = {
    'thick_pelt': ([
        '................',
        '..oo.oooo.oo....',
        '.oFFoFFFFoFFo...',
        '.oFfFFFfFFFfFo..',
        '..oFFFFFFFFFFo..',
        '.oFFfFFFFFfFFo..',
        'oFFFFFFfFFFFFFo.',
        'oFfFFFFFFFFFfFo.',
        '.oFFFFfFFfFFFo..',
        '..oFFFFFFFFFo...',
        '.oFFfFFFFFFFFo..',
        '.oFFFFFfFFFfFo..',
        '..oFoFFoFFoFo...',
        '...o.oo.oo.o....',
        '................',
        '................'], {'o': '#2c1b10', 'F': '#6f4a2c', 'f': '#8a613c'}),
    'giant_antler': ([
        '.............ow.',
        '..........o.oAo.',
        '.........oAoAo..',
        '....o....oAAo...',
        '...oAo..oAAo....',
        '....oAooAAo..ow.',
        '.....oAAAooooAo.',
        '.ow...oAAAAAAo..',
        '.oAo..oAAooo....',
        '..oAooAAo.......',
        '...oAAAo........',
        '....oAAo........',
        '...oDDo.........',
        '..oDDo..........',
        '..oo............',
        '................'], {'o': '#3a2a1a', 'A': '#c9b48f', 'w': '#e8dcc2', 'D': '#7d6246'}),
    'fang': ([
        '................',
        '................',
        '.....oooo.......',
        '....oRRRRo......',
        '....oRRRRo......',
        '....oIWWIo......',
        '....oIWWIo......',
        '.....oWWIo......',
        '.....oWWIo......',
        '......oWIo......',
        '......oWWo......',
        '.......oWo......',
        '.......oWo......',
        '........o.......',
        '................',
        '................'], {'o': '#4a4232', 'R': '#a08563', 'I': '#cdbf9c', 'W': '#f6f0e1'}),
    'wing_membrane': ([
        '................',
        '.oo.............',
        '.oBoooo.........',
        '..oBMMMoooo.....',
        '..oBMmMMMMMooo..',
        '...oBMMmMMMMMo..',
        '...oBMMMmMMMo...',
        '....oBMMMmMo....',
        '....oBMMMMMo....',
        '.....oBMmMo.....',
        '.....oBMMo......',
        '......oBo.......',
        '......oo........',
        '................',
        '................',
        '................'], {'o': '#3b2a22', 'B': '#d6cbad', 'M': '#9a705c', 'm': '#7d5646'}),
    'sickle_claw': ([
        '................',
        '.......oooo.....',
        '.....ooKKKKo....',
        '....oKKkkKKKo...',
        '...oKKkooooKKo..',
        '...oKko....oKo..',
        '..oKKo......oKo.',
        '..oKko......oKo.',
        '..oKKo.......o..',
        '..oKKo..........',
        '..oYYo..........',
        '..oYYYo.........',
        '...oYYo.........',
        '....oo..........',
        '................',
        '................'], {'o': '#0f0d0b', 'K': '#453d36', 'k': '#5f554b', 'Y': '#8a7a52'}),
    'scythe_claw': ([
        '................',
        '.oo.............',
        '.oKo............',
        '..oKo...........',
        '..oKko..........',
        '...oKko.........',
        '....oKko........',
        '.....oKko.......',
        '......oKko......',
        '.......oKKo.....',
        '........oKKo....',
        '.........oKYo...',
        '..........oYYo..',
        '...........oYYo.',
        '............oo..',
        '................'], {'o': '#0f0d0b', 'K': '#453d36', 'k': '#5f554b', 'Y': '#8a7a52'}),
    'croc_scute': ([
        '................',
        '................',
        '.....oooooo.....',
        '...ooSSsSSSoo...',
        '..oSSSsLsSSSSo..',
        '..oSsSsLsSSsSo..',
        '.oSSSSsLsSSSSSo.',
        '.oSsSSsLsSSsSSo.',
        '.oSSSSsLsSSSSSo.',
        '..oSSSsLsSSSSo..',
        '..oDSSsSsSSSDo..',
        '...ooDDDDDDoo...',
        '.....oooooo.....',
        '................',
        '................',
        '................'], {'o': '#1c2414', 'S': '#5c6b3b', 's': '#44522e', 'L': '#77864c', 'D': '#2f3a22'}),
    'marine_flipper': ([
        '................',
        '.........ooo....',
        '........oMMMo...',
        '.......oMmMMMo..',
        '......oMMmMMMo..',
        '.....oMMMmMMo...',
        '....oMMMMmMMo...',
        '...oMMMMmMMo....',
        '...oMMMmMMMo....',
        '..oMMMmMMMo.....',
        '..oMMmMMMo......',
        '..oDMMMMo.......',
        '...oDDMo........',
        '....ooo.........',
        '................',
        '................'], {'o': '#1b252d', 'M': '#5f7a8c', 'm': '#86a0ae', 'D': '#445a6b'}),
    'megalodon_tooth': ([
        '................',
        '................',
        '..oooooooooooo..',
        '..oRRRRRRRRRRo..',
        '..oRrRRRRRRrRo..',
        '..oWWWWWWWWWIo..',
        '...oWWWWWWWIo...',
        '...oWWWWWWIIo...',
        '....oWWWWWIo....',
        '....oWWWWIIo....',
        '.....oWWWIo.....',
        '.....oWWIIo.....',
        '......oWIo......',
        '......oWIo......',
        '.......oo.......',
        '................'], {'o': '#2c2a28', 'R': '#5a4632', 'r': '#7a6248', 'W': '#e0ddd4', 'I': '#a9a59b'}),
    'unicorn_horn': ([
        '................',
        '.............oo.',
        '............oWo.',
        '...........oWPo.',
        '..........oPWo..',
        '.........oWPPo..',
        '........oPWPo...',
        '.......oWPPWo...',
        '......oPWPPo....',
        '.....oWPPWPo....',
        '....oPWPPWo.....',
        '...oWPPWPPo.....',
        '...oPPWPPo......',
        '..oGGGGGo.......',
        '..oooooo........',
        '................'], {'o': '#6d675e', 'W': '#fbf8f1', 'P': '#cfc9bf', 'G': '#e0b43a'}),
    'quetzal_feather': ([
        '..............o.',
        '.............oL.',
        '............oGLo',
        '...........oGgo.',
        '..........oGgGo.',
        '.........oGgGo..',
        '........oGgGo...',
        '.......oGgGo....',
        '......oGgGo.....',
        '.....oGgGo......',
        '....oGgGo.......',
        '...oRgGo........',
        '..oRRRo.........',
        '..oRRo..........',
        '.oWo............',
        '.o..............'], {'o': '#062a18', 'G': '#1f9966', 'g': '#12704a', 'L': '#7fe0b0', 'R': '#b3261e',
                              'W': '#e6dcc3'}),
    'venom_fang': ([
        '................',
        '................',
        '.....oooo.......',
        '....oRRRRo......',
        '....oIWWIo......',
        '....oIWWIo......',
        '.....oWWIo......',
        '.....oWWIo......',
        '......oWIo......',
        '......oVVo......',
        '.......oVo......',
        '.......oVo......',
        '........o.......',
        '........v.......',
        '........v.......',
        '................'], {'o': '#3a3a28', 'R': '#a08563', 'I': '#c9d3a0', 'W': '#e9e2cc', 'V': '#8fb04a',
                              'v': '#6f9a2a'}),
    'tyrant_tooth': ([
        '................',
        '..........oo....',
        '.........oRRo...',
        '........oRRRo...',
        '.......oIWWIo...',
        '......oIWWWo....',
        '......oWWWIo....',
        '.....oWWWIo.....',
        '.....oWWWIo.....',
        '....oWWWIo......',
        '....oWWIo.......',
        '...oWWIo........',
        '...oWIo.........',
        '..oWo...........',
        '..oo............',
        '................'], {'o': '#4a4232', 'R': '#7d6246', 'I': '#cdbf9c', 'W': '#f6f0e1'}),
    'argentavis_talon': ([
        '................',
        '..oo............',
        '.oYYo...........',
        '.oYyYo..........',
        '..oYyYo.........',
        '...oYyYo........',
        '....oYyYo.......',
        '.....oYYYoo.....',
        '......oYYKKo....',
        '.......oKKkKo...',
        '........oKKKo...',
        '.........oKKo...',
        '.........oKo....',
        '........oKo.....',
        '........oo......',
        '................'], {'o': '#0f0d0b', 'Y': '#c9a640', 'y': '#e8cf6e', 'K': '#2f2a26', 'k': '#5f554b'}),
    'amber': ([
        '................',
        '................',
        '.....oooooo.....',
        '....oYYyAAAo....',
        '...oYWYyAAAAo...',
        '...oYyyAAAAAo...',
        '..oyyAAAAkAARo..',
        '..oyAAAAkkAARo..',
        '..oAAAAkAAARRo..',
        '...oAAAAAARRRo..',
        '...oRAAAARRRo...',
        '....ooRRRRoo....',
        '......oooo......',
        '................',
        '................',
        '................'], {'o': '#4a2206', 'Y': '#ffd680', 'y': '#f2a93b', 'A': '#d6761a', 'R': '#a3480b',
                              'W': '#fff4d0', 'k': '#3a1a04'}),
    'red_ochre': ([
        '................',
        '................',
        '................',
        '.......oo.......',
        '.....ooRRoo.....',
        '....oRRrRRRo....',
        '...oRrRRRRrRo...',
        '...oRRRRRRRRo...',
        '..oDRRRrRRRRDo..',
        '..oDDRRRRRRDDo..',
        '...oDDDDDDDDo...',
        '....oooooooo....',
        '................',
        '................',
        '................',
        '................'], {'o': '#3a120a', 'R': '#a8402a', 'r': '#c85a3c', 'D': '#7a2e1c'}),
}


def write_trophies():
    out = ASSETS / 'textures/item/trophy'
    out.mkdir(parents=True, exist_ok=True)
    for name, (rows, palette) in TROPHIES.items():
        A.icon(rows, palette).save(out / f'{name}.png')


# ---------------------------------------------------------------------------------- output

def build(ident):
    group, slot, fn = ITEMS[ident]
    worn, ic = fn()
    worn.pack()
    tex = worn.paint()
    return worn, tex, ic


def write(ident, worn, tex, ic):
    (ASSETS / 'worn').mkdir(parents=True, exist_ok=True)
    (ASSETS / 'textures/entity/accessory').mkdir(parents=True, exist_ok=True)
    (ASSETS / 'textures/item/accessory').mkdir(parents=True, exist_ok=True)
    data = worn.to_json()
    data['glow'] = worn.glow is not None
    (ASSETS / 'worn' / f'{ident}.json').write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8')
    tex.save(ASSETS / 'textures/entity/accessory' / f'{ident}.png')
    glow = ASSETS / 'textures/entity/accessory' / f'{ident}_glow.png'
    if worn.glow is not None:
        worn.glow.save(glow)
    elif glow.exists():
        glow.unlink()
    ic.save(ASSETS / 'textures/item/accessory' / f'{ident}.png')


def review(idents, name='sheet'):
    DESIGN.mkdir(parents=True, exist_ok=True)
    cards = []
    for ident in idents:
        worn, tex, ic = build(ident)
        slim = ITEMS[ident][1] in ('hands', 'bracelet', 'ring')
        pose = {'spread': 1.0} if ident == 'membrane_glider' else None
        view = dict(size=(170, 300), scale=6.0, centre=(0, 2, 0))
        front = A.preview(worn, tex, slim=False, **view)
        extra = A.preview(worn, tex, slim=slim, side='left' if slim else None, pose=pose,
                          views=((-30, 10),) if slim else ((150, 24),), **view)
        card = Image.new('RGBA', (170 * 3 + 84, 320), (236, 233, 224, 255))
        card.alpha_composite(ic.resize((64, 64), Image.NEAREST), (8, 26))
        card.alpha_composite(front, (80, 20))
        card.alpha_composite(extra, (420, 20))
        ImageDraw.Draw(card).text((6, 6), ident, fill=(40, 36, 30, 255))
        cards.append(card)
    cols = 2
    sheet = Image.new('RGBA', (cols * cards[0].width, math.ceil(len(cards) / cols) * cards[0].height), (200, 196, 186, 255))
    for k, card in enumerate(cards):
        sheet.alpha_composite(card, ((k % cols) * card.width, (k // cols) * card.height))
    sheet.save(DESIGN / f'{name}.png')
    return sheet


def icon_sheet():
    """All icons at 4x on one sheet, accessories then trophies."""
    icons = [(ident, build(ident)[2]) for ident in ITEMS]
    icons += [(name, A.icon(*TROPHIES[name])) for name in TROPHIES]
    cols = 10
    cell = 96
    sheet = Image.new('RGBA', (cols * cell, math.ceil(len(icons) / cols) * (cell + 14)), (48, 52, 44, 255))
    draw = ImageDraw.Draw(sheet)
    for k, (name, ic) in enumerate(icons):
        x, y = (k % cols) * cell, (k // cols) * (cell + 14)
        sheet.alpha_composite(ic.resize((64, 64), Image.NEAREST), (x + 16, y + 6))
        draw.text((x + 4, y + 74), name[:15], fill=(230, 230, 220, 255))
    sheet.save(DESIGN / 'icons.png')


def main(args):
    idents = args or list(ITEMS)
    for ident in idents:
        worn, tex, ic = build(ident)
        write(ident, worn, tex, ic)
    write_trophies()
    icon_sheet()
    review(idents)
    print(f'Wrote {len(idents)} accessories')


if __name__ == '__main__':
    main(sys.argv[1:])
