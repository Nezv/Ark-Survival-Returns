"""Small incised stone emblems, shared by P14 workstations and the P16 crate.

Symbols are purpose-drawn chisel marks, not desaturated item sprites. Their negative
height field shares the frame's light direction and exact discrete stone palette.
"""
import math

from PIL import Image, ImageDraw


def symbol_mask(name):
    scale = 4
    mask = Image.new('L', (16*scale, 16*scale))
    d = ImageDraw.Draw(mask)

    def line(points, width=1.35, fill=255):
        d.line([(round(x*scale), round(y*scale)) for x, y in points], fill=fill,
               width=round(width*scale), joint='curve')

    def polygon(points, fill=255):
        d.polygon([(round(x*scale), round(y*scale)) for x, y in points], fill=fill)

    def ellipse(box, fill=255):
        d.ellipse(tuple(round(v*scale) for v in box), fill=fill)

    if name == 'helm':
        polygon([(3, 4), (8, 2), (13, 4), (12, 12), (8, 14), (4, 12)])
        line([(4, 7), (12, 7)], 1.5, 0)
        line([(8, 7), (8, 12)], 1.5, 0)
        line([(4, 5), (2, 3), (2, 1)])
        line([(12, 5), (14, 3), (14, 1)])
    elif name == 'saddle':
        polygon([(1, 5), (3, 2), (5, 5), (10, 5), (12, 1), (14, 2), (14, 7), (2, 7)])
        polygon([(5, 7), (11, 7), (10, 10), (6, 10)])
        line([(8, 10), (8, 12)], 1)
        line([(6.4, 12.4), (9.6, 12.4), (9.6, 15), (6.4, 15), (6.4, 12.4)], 1.1)
    elif name == 'hammer':
        polygon([(8, 2), (14, 7), (12, 9), (10, 7), (3, 14), (1, 12), (8, 5), (6, 3)])
    elif name == 'mortar':
        polygon([(2, 7), (14, 7), (12, 12), (10, 14), (6, 14), (4, 12)])
        line([(3, 7), (13, 7)], 1, 0)
        line([(8, 6), (12, 1)], 2.2)
        line([(5, 14), (11, 14)], 1.4)
    elif name == 'flask':
        line([(5, 2), (11, 2)], 1.4)
        line([(6, 3), (6, 6), (3, 11), (4, 14), (12, 14), (13, 11), (10, 6), (10, 3)], 1.6)
        line([(5, 10), (11, 10)], 1.2)
    elif name == 'anvil':
        polygon([(1, 4), (15, 4), (13, 7), (10, 8), (9, 11), (13, 13), (13, 14), (3, 14), (3, 13), (7, 11), (6, 8), (3, 7)])
        line([(6, 1), (6, 2)], 1)
        line([(10, 1), (11, 2)], 1)
    elif name == 'campfire':
        polygon([(8, 1), (7, 5), (4, 4), (3, 8), (5, 11), (10, 11), (13, 8), (11, 4), (10, 7)])
        polygon([(8, 6), (6, 9), (8, 10), (10, 9)], 0)
        line([(2, 12), (13, 15)], 1.6)
        line([(13, 12), (2, 15)], 1.6)
    elif name == 'bloomery':
        polygon([(6, 2), (10, 2), (10, 5), (13, 9), (14, 14), (2, 14), (3, 9), (6, 5)])
        polygon([(6, 13), (6, 10), (8, 8), (10, 10), (10, 13)], 0)
        line([(7, 1), (9, 1)], 1)
    elif name == 'press':
        line([(2, 14), (2, 3), (14, 3), (14, 14)], 1.7)
        line([(5, 1), (11, 1)], 1.4)
        line([(8, 1), (8, 10)], 1.7)
        line([(5, 10), (11, 10)], 1.7)
        line([(1, 14), (15, 14)], 1.7)
    elif name in ('saw', 'chopsaw'):
        cy = 7 if name == 'chopsaw' else 8
        teeth = []
        for k in range(32):
            a = k*math.tau/32
            radius = 6 if k % 4 < 2 else 4.6
            teeth.append((8+math.cos(a)*radius, cy+math.sin(a)*radius))
        polygon(teeth)
        ellipse((5.7, cy-2.3, 10.3, cy+2.3), 0)
        if name == 'chopsaw':
            line([(1, 14), (15, 14)], 1.7)
            line([(2, 10), (2, 14)], 1.5)
    elif name == 'mill':
        line([(2, 14), (14, 14)], 1.7)
        line([(3, 13), (3, 2), (12, 2), (12, 6), (8, 6), (8, 2)], 1.7)
        line([(10, 6), (10, 9)], 1.4)
        line([(6, 11), (14, 11)], 1.7)
        line([(7, 11), (7, 14)], 1.4)
    elif name == 'archive':
        line([(2, 3), (14, 3), (14, 13), (2, 13), (2, 3)], 1.4)
        line([(2, 8), (14, 8)], 1.3)
        line([(8, 3), (8, 13)], 1.3)
        for x, y in ((4, 5), (11, 5), (4, 10), (11, 10)):
            line([(x, y), (x+1, y)], .9)
    elif name == 'link':
        line([(2, 6), (2, 3), (7, 3), (7, 8), (4, 8)], 1.5)
        line([(9, 8), (13, 8), (13, 13), (8, 13), (8, 10)], 1.5)
        line([(5, 6), (10, 11)], 1.8)
    elif name == 'filter':
        line([(2, 3), (14, 3), (9, 9), (9, 13), (6, 14), (6, 9), (2, 3)], 1.5)
    elif name == 'gauge':
        line([(2, 13), (2, 2), (12, 2), (12, 13)], 1.4)
        for y in (5, 8, 11):
            line([(3, y), (6, y)], 1)
        line([(10, 9), (14, 9), (12, 6), (10, 9)], 1.2)
    elif name == 'crate':
        line([(2, 3), (14, 3), (14, 13), (2, 13), (2, 3)], 1.6)
        line([(5, 4), (5, 12)], 1.4)
        line([(11, 4), (11, 12)], 1.4)
        line([(3, 8), (13, 8)], 1.25)
    else:
        raise ValueError(f'Unknown stone symbol: {name}')
    return mask.resize((16, 16), Image.Resampling.BOX)


def engraved(name, tones, light):
    """Transparent 16px carving overlay; the circular stone ground is drawn by seal()."""
    mask = symbol_mask(name)
    pixels = mask.load()
    def depth(x, y):
        return -2.3*pixels[x, y]/255 if 0 <= x < 16 and 0 <= y < 16 else 0
    image = Image.new('RGBA', (16, 16))
    palette = [tuple(bytes.fromhex(c[1:]))+(255,) for c in tones]
    for y in range(16):
        for x in range(16):
            v = depth(x, y)
            gx = (depth(x+1, y)-depth(x-1, y))/2
            gy = (depth(x, y+1)-depth(x, y-1))/2
            if not (v or gx or gy):
                continue
            n = math.sqrt(gx*gx+gy*gy+1)
            diffuse = (-gx*light[0]-gy*light[1]+light[2])/n
            tone = round(6+(diffuse-light[2])*16+v*.6)
            image.putpixel((x, y), palette[max(0, min(len(palette)-1, tone))])
    return image
