"""Figurative stone carving for the storage frames: masks, snakes and architecture."""
import math

from PIL import Image, ImageDraw

import build_workstation_crests as stone


def hollow(r, cx, cy, rx, ry, depth):
    for y in range(math.floor(cy-ry), math.ceil(cy+ry)+1):
        for x in range(math.floor(cx-rx), math.ceil(cx+rx)+1):
            q = 1-((x+.5-cx)/rx)**2-((y+.5-cy)/ry)**2
            if q > 0 and (x, y) in r.h:
                r.h[x, y] = max(.15, r.h[x, y]-depth*math.sqrt(q))


def block(r, x0, y0, x1, y1, height=3):
    for y in range(math.floor(y0), math.ceil(y1)):
        for x in range(math.floor(x0), math.ceil(x1)):
            edge = min(x-x0+.5, y-y0+.5, x1-x-.5, y1-y-.5)
            r.lift(x, y, min(height, 1+max(0, edge)*1.4))


def snake(r, path, width=2, base=3):
    """Tapered tail, belly scales, broader head, incised eyes and divided jaw."""
    r.tube(path, .35, width, base=base, flat=.85)
    pts = stone.sample(path, 1)
    for i in range(6, len(pts)-5, 5):
        x, y = pts[i]
        ax, ay = pts[i-2]
        bx, by = pts[i+2]
        distance = math.hypot(bx-ax, by-ay) or 1
        nx, ny = -(by-ay)/distance, (bx-ax)/distance
        radius = .3+(width-.3)*i/len(pts)
        r.carve([(x-nx*radius*.65, y-ny*radius*.65),
                 (x+nx*radius*.65, y+ny*radius*.65)], .8, .4)
    hx, hy = path[-1]
    dx, dy = hx-path[-3][0], hy-path[-3][1]
    length = math.hypot(dx, dy) or 1
    dx, dy = dx/length, dy/length
    nx, ny = -dy, dx
    # A flattened lance-shaped head with distinct jaw and eye sockets.
    for y in range(math.floor(hy-4), math.ceil(hy+4)+1):
        for x in range(math.floor(hx-4), math.ceil(hx+4)+1):
            u = (x+.5-hx)*dx+(y+.5-hy)*dy
            v = (x+.5-hx)*nx+(y+.5-hy)*ny
            q = 1-(u/3.5)**2-(v/2.5)**2
            if q > 0:
                r.lift(x, y, base+2.2*math.sqrt(q))
    for side in (-1, 1):
        hollow(r, hx+dx*.6+nx*side*1.5, hy+dy*.6+ny*side*1.5, .9, .9, 5)
    r.carve([(hx+dx*2-nx*1.4, hy+dy*2-ny*1.4),
             (hx+dx*2.7, hy+dy*2.7), (hx+dx*2+nx*1.4, hy+dy*2+ny*1.4)], 1.6, .5)


def gorgon(r, cx, cy):
    """A small frontal face with brow, eye sockets, nose, lips and six snake locks."""
    for s in (-1, 1):
        # Each lock ends in an actual snake head, not an anonymous scroll eye.
        snake(r, stone.curve((cx+s*5, cy-5), (cx+s*3, cy-18), (cx+s*14, cy-18), (cx+s*16, cy-10)), 1.7, 1)
        snake(r, stone.curve((cx+s*6, cy-7), (cx+s*17, cy-11), (cx+s*21, cy-22), (cx+s*11, cy-22)), 1.6, 1)
        snake(r, stone.curve((cx+s*5, cy+8), (cx+s*17, cy+11), (cx+s*19, cy-4), (cx+s*11, cy-2)), 1.6, 1)
    r.dome(cx, cy-1, 7, 10, 3.6, 1.6)
    r.dome(cx, cy+6, 4.7, 4.5, 3.3, 1.7)
    for s in (-1, 1):
        r.dome(cx+s*4.3, cy+2.5, 3.1, 3.7, 2.2, 3)
        r.dome(cx+s*7, cy+1, 1.7, 3, 2.3, 1.7)
        r.tube([(cx+s*5.8, cy-1.7), (cx+s*3.8, cy-3), (cx+s*1.4, cy-2)], 1, base=4.2)
        hollow(r, cx+s*3.2, cy, 2.25, 1.5, 5)
        hollow(r, cx+s*1.3, cy+3.3, .7, .7, 2.7)
    r.tube([(cx, cy-2), (cx, cy+3)], 1.1, base=4.4)
    r.dome(cx, cy+3, 1.5, 1.1, 1.5, 4)
    r.tube([(cx-2, cy+6), (cx, cy+5.5), (cx+2, cy+6)], .85, base=3.6)
    r.carve([(cx-2.7, cy+5), (cx, cy+5.3), (cx+2.7, cy+5)], 2.7, .55)
    r.carve([(cx-1.5, cy+8), (cx+1.5, cy+8)], .7, .4)


def face_details(surface, cx, cy):
    """Resolve the tiny mask's facial planes explicitly at native pixel scale."""
    # Broad forehead, paired almond eye sockets, a nose bridge, lips and chin.
    # These planes are too small for a sampled height field alone to read reliably.
    image = Image.new('RGBA', (19, 23))
    d = ImageDraw.Draw(image)
    c = [stone.rgb(value) for value in stone.TONES]
    d.polygon([(5, 1), (13, 1), (16, 5), (16, 13), (13, 19), (9, 22),
               (5, 19), (2, 13), (2, 5)], fill=c[6], outline=c[2])
    d.polygon([(5, 2), (12, 2), (14, 5), (12, 7), (7, 6), (4, 7), (3, 5)], fill=c[8])
    d.line([(5, 2), (11, 2), (13, 3)], fill=c[10])
    d.polygon([(3, 9), (7, 11), (7, 15), (5, 17), (3, 13)], fill=c[9])
    d.polygon([(11, 11), (15, 9), (15, 14), (12, 17), (11, 15)], fill=c[5])
    d.polygon([(4, 8), (6, 7), (8, 9), (6, 10), (4, 9)], fill=c[1])
    d.polygon([(11, 9), (13, 7), (15, 8), (14, 10), (12, 10)], fill=c[1])
    d.line([(4, 7), (6, 6), (8, 7)], fill=c[10])
    d.line([(11, 7), (13, 6), (15, 7)], fill=c[8])
    d.line([(5, 10), (7, 10)], fill=c[7])
    d.line([(12, 10), (14, 10)], fill=c[6])
    d.polygon([(9, 7), (8, 13), (7, 14), (9, 15), (11, 14), (10, 12), (10, 8)], fill=c[7])
    d.line([(9, 8), (9, 12), (8, 14)], fill=c[10])
    d.line([(10, 9), (10, 12), (11, 14)], fill=c[3])
    d.point((7, 14), fill=c[1])
    d.point((11, 14), fill=c[1])
    d.line([(7, 17), (9, 16), (11, 17)], fill=c[3])
    d.line([(6, 17), (12, 17)], fill=c[2])
    d.line([(7, 18), (10, 18)], fill=c[9])
    d.line([(7, 20), (10, 21), (12, 19)], fill=c[7])
    surface.image.alpha_composite(image, (cx-9+surface.left, cy-8+surface.top))


def rosette(r, cx, cy):
    for i in range(8):
        a = i*math.tau/8
        r.dome(cx+math.cos(a)*3.5, cy+math.sin(a)*3.5, 1.9, 1.9, 1.8, 3)
    r.dome(cx, cy, 2, 2, 2.6, 3.5)


def picture_frame(r, w, h, width=20):
    """Continuous broad moulding with mitred corners and a recessed inner border."""
    profile = (1, 2.3, 3.6, 4.3, 3.8, 2.1, 1.1, 1.5, 2.4, 3.1, 3.3,
               3.4, 3.3, 3, 2.5, 1.5, 1, 1.5, 2.5, 3, 2.4, 1.1)
    for y in range(-width, h+width):
        for x in range(-width, w+width):
            distance = min(x, y, w-1-x, h-1-y)
            if distance <= 1:
                r.lift(x, y, profile[min(len(profile)-1, distance+width)])
    for x in range(12, w-11, 9):
        for y in (-11, h+10):
            r.dome(x, y, 2, 2.5, 1.6, 3)
            r.carve([(x, y-1), (x, y+1)], .8, .4)
    for y in range(12, h-11, 9):
        for x in (-11, w+10):
            r.dome(x, y, 2.5, 2, 1.6, 3)
    for x, sx in ((-11, 1), (w+10, -1)):
        for y, sy in ((-11, 1), (h+10, -1)):
            r.carve([(x-sx*7, y-sy*7), (x+sx*9, y+sy*9)], 1, .5)
            rosette(r, x, y)


def pillar(r, x, top, bottom):
    """Broad fluted shaft seated on a square plinth, with a tiered Ionic capital."""
    r.tube([(x, top+15), (x, bottom-13)], 7.4, flat=.75, base=1.5)
    for dx in (-4, -2, 0, 2, 4):
        r.carve([(x+dx, top+20), (x+dx, bottom-18)], 2, .55)
    for offset, half, height in ((0, 13, 3), (4, 11, 3), (8, 9, 4), (13, 8, 3)):
        block(r, x-half, top+offset, x+half, top+offset+height, 4)
        block(r, x-half, bottom-offset-height, x+half, bottom-offset, 4)
    for s in (-1, 1):
        r.tube(stone.spiral(x+s*8, top+10, 3.5, .8, 180 if s<0 else 0, -160 if s<0 else 340), 1.1, base=2)
