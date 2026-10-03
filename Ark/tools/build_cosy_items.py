"""Cosy item sprites: the nineteen items that still borrowed a vanilla sprite.

Plant Fiber, the five nest eggs, the Companion Whistle, the Field Journal, the Fiber Bandage, both cargo
harnesses, the Healing Mixture, the Hearty Stew, the Trail Mix, the Dried Ration, both tranquilizer arrows, the
Concentrated Sedative and the Debug Spyglass. Hand-pixelled on a 16 px grid and doubled to the 32 px Ark item
size, like the other hand-drawn items (build_bronze_age_art.py). The look is warm and soft: rounded shapes, straw,
twine and stitched leather, and an outline that is a darker shade of whatever it borders instead of one black line.
The arrows and the spyglass start from the vanilla sprites, so they stay in their families.

Deterministic: every output is a pure function of this file and the local Minecraft source jar.

Run from Ark: python tools/build_cosy_items.py
"""
from PIL import Image, ImageDraw

from build_bronze_age_art import ARK, TEX, item32, paint, save, vanilla, WRITTEN

REVIEW = ARK / 'design/items/cosy-sprites.png'
WARM = (52, 36, 22)

STRAW = {'N': (226, 194, 124), 'n': (192, 152, 88), 's': (146, 108, 58), 'S': (108, 78, 40)}
TWINE = {'T': (214, 180, 120), 't': (160, 120, 68)}
GLASS = {'g': (70, 104, 148), 'w': (226, 240, 252), 'x': (176, 204, 232, 150)}
LEAF = {'G': (128, 160, 70), 'j': (84, 112, 46)}


def rim(image, darken=0.42, pull=0.35):
    """A one-pixel outline in a darker shade of what it borders, pulled toward warm brown."""
    out = image.copy()
    src, dst = image.load(), out.load()
    for y in range(image.height):
        for x in range(image.width):
            if src[x, y][3]:
                continue
            near = [src[x + dx, y + dy] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
                    if 0 <= x + dx < image.width and 0 <= y + dy < image.height and src[x + dx, y + dy][3] > 128]
            if near:
                mean = [sum(p[i] for p in near) / len(near) for i in range(3)]
                dst[x, y] = tuple(round(c * darken * (1 - pull) + w * pull) for c, w in zip(mean, WARM)) + (255,)
    return out


def sprite(rows, palette):
    for row in rows:
        assert len(row) == 16 and len(rows) == 16, row
    return rim(paint(rows, palette))


def over(image, pixels):
    """Recolour single pixels of a finished drawing: speckles, studs, glints."""
    px = image.load()
    for (x, y), colour in pixels.items():
        if px[x, y][3]:
            px[x, y] = colour + (255,)
    return image


# ----------------------------------------------------------------------------------------- camp materials

def plant_fiber():
    """A sheaf of green grass fibre, tied near the cut ends with twine; the tips have dried to straw."""
    return sprite(["................",
                   "...y..y..y..y...",
                   "...ly.ly.ly.ly..",
                   "..yglyglyglygl..",
                   "..lmglmglmglmg..",
                   "...mglmglmgmd...",
                   "....glmglmgm....",
                   "....mgmglmgd....",
                   ".....dmgmgd.....",
                   "....tTTkTTTt....",
                   "....TtTTtTtt....",
                   ".....mgmgmd.t...",
                   ".....gmgmdm.....",
                   "....mdgmdmdm....",
                   "................",
                   "................"],
                  {'d': (61, 88, 40), 'm': (92, 124, 52), 'g': (128, 160, 70), 'l': (168, 194, 96),
                   'y': (212, 206, 130), 'k': (236, 210, 156), **TWINE})


def fiber_bandage():
    """The Herbal Bandage's roll without the herbs: coarse woven fibre, the loose end trailing."""
    return sprite(["................",
                   "................",
                   "................",
                   "......cccc......",
                   "....ccWWWWcc....",
                   "...cWWsssWWWc...",
                   "..cWWsWWWsWWWc..",
                   "..cWsWcoocsWWc..",
                   "..cWsWo..oWsWc..",
                   "..cWsWcoocWsWc..",
                   "..cWWssssWsWWc..",
                   "...cWWWWWsWWsWc.",
                   "....ccWWWWsWWsc.",
                   "......ccWWWsWc..",
                   "..........ccc...",
                   "................"],
                  {'W': (230, 218, 176), 'c': (168, 152, 108), 's': (198, 184, 136), 'o': (112, 96, 62)})


# ------------------------------------------------------------------------------------------- nest eggs

NEST = ["................",
        "................",
        "......hllb......",
        ".....hllbbb.....",
        ".....llbbbb.....",
        "....hlbbbbbd....",
        "....lbbbbbbd....",
        "....lbbbbbbd....",
        "....bbbbbbdd....",
        "..N.bbbbbbdd.N..",
        ".NnNdbbbbdddNns.",
        ".snNnNddddNnNns.",
        "..snNnsnNnNsns..",
        "...ssnNsnNnss...",
        ".....ssSSss.....",
        "................"]
SMALL_NEST = ["................",
              "................",
              "................",
              "................",
              "....hl....hl....",
              "...hlbb..hlbb...",
              "...lbbbd.lbbbd..",
              "...lbbbd.lbbbd..",
              "...bbbdd.bbbdd..",
              "..Nbbbdd.bbbddN.",
              ".NnNbddnNnbddNs.",
              ".snNnNnNnNnNnns.",
              "..snNnsnNnNsns..",
              "...ssnNsnNnss...",
              ".....ssSSss.....",
              "................"]
TALL_NEST = ["................",
             ".......hl.......",
             "......hllb......",
             "......llbb......",
             ".....hlbbbd.....",
             ".....lbbbbd.....",
             ".....lbbbbd.....",
             ".....lbbbbd.....",
             ".....bbbbdd.....",
             "..N..bbbbdd..N..",
             ".NnN.bbbddd.Nns.",
             ".snNnNnddnNnNns.",
             "..snNnsnNnNsns..",
             "...ssnNsnNnss...",
             ".....ssSSss.....",
             "................"]


def nest_egg(rows, shell, marks):
    """An egg sitting in a cup of straw: h, l, b, d are the shell from lit to shaded, marks its pattern."""
    h, l, b, d = shell
    return over(sprite(rows, {'h': h, 'l': l, 'b': b, 'd': d, **STRAW}), marks)


def nest_eggs():
    slate, rust, olive, cream = (112, 120, 136), (160, 88, 50), (124, 132, 98), (240, 230, 186)
    ember, soot = (252, 190, 80), (84, 20, 28)
    return {
        'archaeopteryx_egg': nest_egg(SMALL_NEST, ((255, 248, 226), (246, 228, 192), (232, 206, 160), (198, 166, 120)),
                                      {(4, 6): rust, (5, 8): rust, (6, 7): rust, (10, 6): rust, (11, 8): rust, (12, 7): rust}),
        'argentavis_egg': nest_egg(NEST, ((255, 255, 250), (246, 242, 232), (232, 226, 212), (196, 186, 170)),
                                   {(6, 4): slate, (7, 4): slate, (9, 6): slate, (10, 6): slate, (9, 7): slate,
                                    (5, 8): slate, (6, 8): slate, (8, 9): slate, (10, 9): slate}),
        'pteranodon_egg': nest_egg(TALL_NEST, ((244, 234, 204), (226, 210, 168), (206, 186, 140), (168, 146, 104)),
                                   {(7, 3): olive, (8, 5): olive, (6, 6): olive, (9, 7): olive, (7, 8): olive, (8, 10): olive}),
        'quetzal_egg': nest_egg(NEST, ((196, 240, 228), (124, 208, 194), (72, 172, 162), (44, 126, 126)),
                                {(7, 4): cream, (9, 5): cream, (6, 6): cream, (9, 8): cream, (7, 8): cream, (10, 7): cream}),
        'dragon_egg': nest_egg(NEST, ((240, 124, 86), (192, 64, 50), (150, 40, 36), (98, 24, 30)),
                               {(8, 3): soot, (6, 5): soot, (9, 5): soot, (5, 7): soot, (8, 7): soot, (10, 7): soot,
                                (6, 9): soot, (9, 9): soot, (7, 4): ember, (8, 6): ember, (7, 8): ember, (10, 8): ember}),
    }


# ------------------------------------------------------------------------------------- taming and cargo

def companion_whistle():
    """A carved wooden whistle on a loop of red cord."""
    return sprite(["................",
                   "........cccc....",
                   ".......c....C...",
                   "......c......C..",
                   "......c......C..",
                   ".......c....C...",
                   "........cuuC....",
                   "........WWWW....",
                   ".......WWwwwv...",
                   ".WWWWWWWwkkwv...",
                   ".WwwwwwwwkwwvV..",
                   "..vvvvvwwwwwvV..",
                   ".......vwwwvV...",
                   "........vvVV....",
                   "................",
                   "................"],
                  {'W': (222, 180, 120), 'w': (190, 144, 88), 'v': (150, 108, 62), 'V': (114, 80, 44), 'k': (70, 46, 26),
                   'c': (184, 70, 56), 'C': (136, 44, 40), 'u': (222, 180, 84)})


HARNESS = ["................",
           ".....rRRRRRr....",
           "....rRkRRRkRe...",
           "....rrkrrrkre...",
           "...LLLLLLLLLLL..",
           "..LlltlltlltllS.",
           "..SSSlllllllSSS.",
           "..ffff.....ffff.",
           "..fBBF.....fBBF.",
           "..FuUF.....FuUF.",
           "..bbbd.....bbbd.",
           "..dddS.....Sddd.",
           "....SSl...lSS...",
           "......SuUS......",
           "................",
           "................"]


def pack_harness():
    """A girth of stitched tan leather: a saddlebag on each flank, a rolled blanket lashed on top, a brass buckle."""
    return sprite(HARNESS, {'R': (236, 224, 194), 'r': (194, 178, 142), 'e': (168, 148, 110), 'k': (110, 72, 42),
                            'L': (196, 150, 98), 'l': (160, 112, 66), 'S': (118, 78, 46), 't': (236, 214, 164),
                            'f': (184, 136, 86), 'F': (140, 96, 58), 'B': (204, 158, 106), 'b': (164, 116, 72),
                            'd': (120, 82, 48), 'u': (236, 196, 100), 'U': (170, 126, 50)})


def reinforced_harness():
    """The heavy harness: dark oiled leather, bronze studs, plates and buckles, and a bronze-bound crate on top."""
    rows = list(HARNESS)
    rows[1:4] = [".....oBBBBBo....",
                 ".....WWwWwWW....",
                 ".....oBBBBBo...."]
    rows[5] = "..LlollollolloS."
    rows[11] = "..oddS.....Sddo."
    return sprite(rows, {'W': (170, 132, 74), 'w': (98, 70, 34), 'B': (176, 121, 44), 'o': (238, 203, 126),
                         'L': (136, 88, 60), 'l': (104, 64, 44), 'S': (70, 42, 30),
                         'f': (128, 82, 56), 'F': (92, 56, 38), 'b': (112, 70, 48), 'd': (78, 46, 32),
                         'u': (238, 203, 126), 'U': (176, 121, 44)})


def field_journal():
    """A leather field journal: a three-toed track pressed into the cover, a band to hold it shut, a red ribbon."""
    return sprite(["................",
                   "...sLLLLLLLLL...",
                   "..sSLcccccckCp..",
                   "..sScccccccKcp..",
                   "..sScecececKcp..",
                   "..sScceeeccKcp..",
                   "..sScccecccKcp..",
                   "..sScccecccKcp..",
                   "..sScccccccKcp..",
                   "..sScccccccKcp..",
                   "..sSccccccckcp..",
                   "..sSDDDDDDDkDp..",
                   "..sppppRRppppP..",
                   "...PPPPRRPPPP...",
                   ".......Rr.......",
                   "................"],
                  {'s': (84, 50, 30), 'S': (120, 76, 44), 'L': (190, 138, 84), 'c': (160, 108, 62), 'C': (190, 138, 84),
                   'D': (124, 80, 46), 'e': (232, 196, 128), 'k': (70, 44, 30), 'K': (98, 60, 38),
                   'p': (244, 236, 212), 'P': (204, 192, 160), 'R': (200, 56, 52), 'r': (144, 36, 40)})


# -------------------------------------------------------------------------------------------- kitchen

def hearty_stew():
    """A wooden bowl of stew with carrot, greens and a wooden spoon, steam rising."""
    image = sprite(["................",
                    "................",
                    "................",
                    ".............pp.",
                    "............pP..",
                    "...........pP...",
                    "..rrrrrrrrpPrr..",
                    ".rbcBbgBocPbBbr.",
                    ".rBbmBcbBgBcbBr.",
                    ".WrrrrrrrrrrrrD.",
                    ".WWWWWWWWWWWWDD.",
                    "..WWWWWWWWWWDD..",
                    "...WWDWWWWDDD...",
                    "....DDDDDDDD....",
                    ".....dddddd.....",
                    "................"],
                   {'r': (204, 156, 98), 'W': (160, 112, 64), 'D': (118, 78, 42), 'd': (88, 58, 32),
                    'B': (150, 82, 40), 'b': (186, 112, 58), 'c': (244, 148, 44), 'g': (104, 160, 64), 'm': (110, 58, 38),
                    'o': (236, 210, 146), 'p': (196, 150, 96), 'P': (140, 98, 56)})
    px = image.load()
    for x, y, alpha in ((4, 1, 150), (5, 2, 210), (4, 3, 230), (5, 4, 210), (8, 2, 150), (7, 3, 210), (8, 4, 230)):
        px[x, y] = (236, 232, 224, alpha)  # steam is drawn last, so it gets no outline
    return image


def trail_mix():
    """An open cloth pouch heaped with the four berries and dried bits; a few have rolled out."""
    return sprite(["................",
                   "................",
                   "......ryn.......",
                   ".....nuNryv.....",
                   "....CyrNnuyC....",
                   "...CcccccccccC..",
                   "...dCCCCCCCCCd..",
                   "....tTttTttt....",
                   "...cCcccccTcd...",
                   "..cCCcccccctcd..",
                   "..cCcccccccccd..",
                   "..cccccccccddd..",
                   "..dccccccccddD..",
                   "...ddccccdddD.r.",
                   "....DDDDDDDD.n..",
                   "................"],
                  {'c': (218, 200, 164), 'C': (240, 228, 200), 'd': (178, 156, 118), 'D': (140, 118, 84),
                   'r': (218, 75, 81), 'y': (237, 188, 62), 'u': (78, 137, 208), 'v': (96, 81, 108),
                   'n': (176, 124, 72), 'N': (218, 176, 120), **TWINE})


def dried_ration():
    """Pressed dried berries wrapped in a leaf and tied with a twine bow; one end shows the cake."""
    return sprite(["................",
                   "................",
                   ".....TT..TT.....",
                   "....TttTTttT....",
                   "...LLLLtTLLLL...",
                   "..LLlllltlllLr..",
                   "..LlvvlltlvlRRr.",
                   "..LllllvtvllRqr.",
                   "..ttttttTtttRRr.",
                   "..LlvlllttllRqr.",
                   "..dllvvltlllRRr.",
                   "..ddlllltllldr..",
                   "...ddddtdddd....",
                   "................",
                   "................",
                   "................"],
                  {'l': (122, 142, 70), 'L': (160, 178, 92), 'd': (86, 104, 50), 'v': (150, 168, 86),
                   'r': (150, 58, 62), 'R': (192, 86, 82), 'q': (238, 202, 142), **TWINE})


# ------------------------------------------------------------------------------------------- medicine

def healing_mixture():
    """A corked flask of rose tonic with a herb leaf tied to its neck; the Vitamins' glass."""
    return sprite(["................",
                   "......kKk.......",
                   "......kkK.......",
                   ".....gwxxg......",
                   "......wxg.jG....",
                   "......wxgtGGj...",
                   ".....gwxxgjG....",
                   "....gwxxxxg.....",
                   "....gwffffg.....",
                   "...gwpPpppxg....",
                   "...gwpppPppg....",
                   "..gwppppppppg...",
                   "..gwpPpppPpxg...",
                   "..gppppppppgg...",
                   "...gggggggg.....",
                   "................"],
                  {'k': (176, 124, 72), 'K': (122, 82, 42), 'p': (232, 112, 140), 'P': (188, 66, 106), 'f': (252, 190, 200),
                   't': (160, 120, 68), **GLASS, **LEAF})


def concentrated_sedative():
    """A small jar of dark narcotic concentrate under a tied cloth cover."""
    return sprite(["................",
                   "................",
                   "................",
                   ".....cCCCCc.....",
                   "....cCCCCCCc....",
                   "....tTttTttt....",
                   ".....c.cc.c.....",
                   "....gwppppxg....",
                   "...gwpPpppppg...",
                   "...gwpppPpppg...",
                   "...gwppppppPg...",
                   "...gwpPppppxg...",
                   "....gppppppg....",
                   ".....gggggg.....",
                   "................",
                   "................"],
                  {'c': (214, 196, 160), 'C': (240, 228, 200), 'p': (116, 58, 140), 'P': (70, 34, 92), **TWINE, **GLASS})


# --------------------------------------------------------------------------------------------- arrows

def tranquilizer_arrow(improved=False):
    """The vanilla arrow with a narcotic head: dark purple, or the concentrated dose, bright, with gold bindings."""
    image = vanilla('item/arrow')
    px = image.load()
    dark, mid, light, glint = (70, 34, 92), (116, 58, 140), (160, 96, 184), (214, 170, 232)
    if improved:
        dark, mid, light, glint = (92, 24, 96), (168, 52, 164), (224, 110, 212), (255, 214, 250)
    head = {(12, 2): light, (13, 2): glint, (14, 2): dark, (10, 3): mid, (11, 3): light, (12, 3): light, (13, 3): mid,
            (14, 3): dark, (10, 4): dark, (12, 4): mid, (13, 4): dark, (12, 5): mid, (13, 5): dark, (12, 6): dark}
    for (x, y), colour in head.items():
        px[x, y] = colour + (255,)
    px[11, 4] = px[11, 5] = (40, 30, 11, 255)
    feather = (236, 196, 110) if improved else (176, 150, 196)
    for x, y in ((3, 11), (2, 12), (4, 12), (3, 13)):
        px[x, y] = feather + (255,)
    if improved:
        px[9, 6] = px[7, 8] = (236, 196, 110, 255)
    return image


# ---------------------------------------------------------------------------------------------- debug

def debug_spyglass():
    """The vanilla spyglass with a ladybird on the tube: the inventory icon of the operator's debug scope."""
    image = vanilla('item/spyglass')
    px = image.load()
    red, deep, black = (222, 60, 48), (160, 32, 36), (30, 24, 24)
    for (x, y), colour in {(6, 9): black, (7, 9): red, (8, 9): red, (6, 10): red, (7, 10): black, (8, 10): deep,
                           (7, 11): deep, (5, 9): black}.items():
        px[x, y] = colour + (255,)
    return image


def sprites():
    return {
        'plant_fiber': plant_fiber(),
        'fiber_bandage': fiber_bandage(),
        **nest_eggs(),
        'companion_whistle': companion_whistle(),
        'field_journal': field_journal(),
        'pack_harness': pack_harness(),
        'reinforced_harness': reinforced_harness(),
        'hearty_stew': hearty_stew(),
        'trail_mix': trail_mix(),
        'dried_ration': dried_ration(),
        'healing_mixture': healing_mixture(),
        'concentrated_sedative': concentrated_sedative(),
        'tranquilizer_arrow': tranquilizer_arrow(),
        'improved_tranquilizer_arrow': tranquilizer_arrow(improved=True),
        'debug_spyglass': debug_spyglass(),
    }


def review(images):
    """The review sheet: every sprite at twice its texture size, on a light slot and on a dark panel."""
    cell, columns = 80, 10
    rows = -(-len(images) // columns)
    sheet = Image.new('RGB', (columns * cell, rows * cell * 2), (36, 30, 26))
    draw = ImageDraw.Draw(sheet)
    for band, colour in enumerate(((198, 190, 172), (58, 46, 38))):
        for i, image in enumerate(images.values()):
            x, y = (i % columns) * cell, (i // columns + band * rows) * cell
            draw.rectangle([x + 3, y + 3, x + cell - 4, y + cell - 4], fill=colour)
            big = image.resize((64, 64), Image.Resampling.NEAREST)
            sheet.paste(big, (x + 8, y + 8), big)
    return sheet


def main():
    images = {name: item32(image) for name, image in sprites().items()}
    for name, image in images.items():
        save(image, TEX / f'item/{name}.png')
    save(review(images), REVIEW)
    print(f'Wrote {len(WRITTEN)} files')


if __name__ == '__main__':
    main()
