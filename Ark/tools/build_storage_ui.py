"""New P16 storage compositions: a carved coffer and an architectural archive.

Reuses only P14's carving primitives, discrete palette and light, never ornament().
Tom's source atlases retain their alpha and UV layout. Candidates and outer frames
ship under Ark's namespace for a future screen hook, not as unreadable dark overrides
of screens whose label colours/variable-height slicing have not yet been adapted.
Run: python tools/build_storage_ui.py (also called by build_baroque_ui.py).
"""
import io
import json
import math
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
import build_workstation_crests as stone

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'src/main/resources/assets/arksurvivalreturns/textures/gui'
OUT = ROOT / 'design/ui-rework/baroque'
TOMS = ART / 'storage/toms_storage'
ARCHIVE_MARGIN, ARCHIVE_TOP, ARCHIVE_BOTTOM = 44, 52, 34
FACE, WELL, EDGE = stone.TONES[4], stone.TONES[2], stone.TONES[7]


class Surface:
    def __init__(self, width, height, left=26, top=33, bottom=30):
        self.width, self.height = width, height
        self.left, self.top = left, top
        self.image = Image.new('RGBA', (width+left*2, height+top+bottom))
        self.ramp = [stone.rgb(c) for c in stone.TONES]

    def put(self, x, y, color):
        x, y = x+self.left, y+self.top
        if 0 <= x < self.image.width and 0 <= y < self.image.height:
            self.image.putpixel((x, y), self.ramp[max(0, min(11, color))] if isinstance(color, int) else color)

    def finish(self):
        return stone.outline(self.image, stone.rgb(stone.TONES[0]))


def line(r, points, radius=1.4):
    r.tube(points, radius)


def rail(r, points):
    """A squared ogee moulding, intentionally unlike P14's beaded surround."""
    r.tube(points, 3.3, flat=.75)
    r.carve(points, 1.5, .85)


def braid(r, x0, x1, y, amplitude=1.7):
    for sign in (-1, 1):
        path = [(x, y+sign*amplitude*math.sin((x-x0)*math.tau/13)) for x in range(x0, x1+1)]
        r.tube(path, .85, base=.5)


def reflected(r, part, w, h, fx=False, fy=False):
    # No mutable workstation globals: any storage size is independent.
    for (x, y), value in part.h.items():
        r.lift(w-1-x if fx else x, h-1-y if fy else y, value)


def badge(surface, r, cx, cy, symbol, kind):
    """A shield or cut-corner tablet; neither uses the workstation's round medallion."""
    if kind == 'shield':
        rim = [(cx-12, cy-13), (cx+12, cy-13), (cx+13, cy+4), (cx+8, cy+11),
               (cx, cy+15), (cx-8, cy+11), (cx-13, cy+4), (cx-12, cy-13)]
    else:
        rim = [(cx-15, cy-7), (cx-9, cy-13), (cx+9, cy-13), (cx+15, cy-7),
               (cx+15, cy+7), (cx+9, cy+13), (cx-9, cy+13), (cx-15, cy+7), (cx-15, cy-7)]
    d = ImageDraw.Draw(surface.image)
    d.polygon([(x+surface.left, y+surface.top) for x, y in rim], fill=stone.DISC)
    r.tube(rim, 2.1)
    r.carve(rim, .55, .4)
    return (cx-8+surface.left, cy-8+surface.top), stone.sigil(symbol)


def coffer_frame():
    w, h = 176, 166
    f, r = Surface(w, h, bottom=34), stone.Relief()
    # Straight carcass with a braided inset, clipped corner plates and projecting feet.
    rail(r, [(-4, -4), (w+3, -4), (w+3, h+3), (-4, h+3), (-4, -4)])
    line(r, [(0, 0), (w-1, 0), (w-1, h-1), (0, h-1), (0, 0)], 1)
    braid(r, 12, w-13, -7)
    braid(r, 14, w-15, h+8)
    corner = stone.Relief()
    # A curled clasp wraps each corner: rectangular/diagonal rather than scallop fans.
    rail(corner, [(21, -10), (2, -10), (-10, 2), (-10, 23)])
    corner.leaf(stone.curve((18, -15), (7, -26), (-8, -26), (-17, -12)), 3.2, 4, -1)
    corner.scroll(stone.spiral(-12, -5, 7, 1.3, -130, 235), 2.7, 1.2)
    corner.dome(-4, -4, 4, 4, 3.8)
    corner.carve([(-7, -7), (-1, -1)], 1.2, .8)
    for fx in (False, True):
        for fy in (False, True):
            reflected(r, corner, w, h, fx, fy)
    # Two scroll handles, with lobed acanthus tails following the chest's sides.
    side = stone.Relief()
    middle = 69
    side.scroll(stone.curve((-6, middle-29), (-26, middle-23), (-26, middle+23), (-6, middle+29)), 3, 2)
    side.scroll(stone.spiral(-14, middle-20, 5, 1, 180, 540), 2.5, 1)
    side.scroll(stone.spiral(-14, middle+20, 5, 1, 180, -180), 2.5, 1)
    side.leaf(stone.curve((-9, middle-31), (-12, middle-43), (-23, middle-43), (-21, middle-50)), 2.8, 3)
    side.leaf(stone.curve((-9, middle+31), (-12, middle+43), (-21, middle+44), (-19, middle+50)), 2.8, 3, -1)
    for fx in (False, True):
        reflected(r, side, w, h, fx)
    # A low split crest flowing into a broad tablet (no crown or hanging shell).
    cx = w//2
    for s in (-1, 1):
        r.scroll(stone.curve((cx+s*15, -11), (cx+s*27, -29), (cx+s*40, -26), (cx+s*46, -17))+
                 stone.spiral(cx+s*44, -14, 4, 1, -45 if s > 0 else 225, 275 if s > 0 else -95)[1:], 2.8, 1)
        r.leaf(stone.curve((cx+s*40, -17), (cx+s*51, -23), (cx+s*57, -16), (cx+s*66, -13)), 2.8, 3, s)
        # The plinth's central clasp replaces the workstation pendant.
        r.scroll(stone.spiral(cx+s*16, h+16, 7, 1.2, 0 if s > 0 else 180, 340 if s > 0 else -160), 2.8, 1.2)
        r.leaf(stone.curve((cx+s*22, h+18), (cx+s*36, h+28), (cx+s*43, h+17), (cx+s*53, h+15)), 2.7, 3, s)
    rail(r, [(cx-5, h+4), (cx-5, h+21), (cx+5, h+21), (cx+5, h+4)])
    for x in (17, w-18):
        r.tube([(x-7, h+10), (x-7, h+18), (x+7, h+18), (x+7, h+10)], 2.3)
    xy, icon = badge(f, r, cx, -11, 'crate', 'tablet')
    r.paint(f)
    f.image.alpha_composite(icon, xy)
    return f.finish()


def archive_frame(w, h, symbol='archive'):
    """Split pediment, fluted pilasters, scroll capitals, linked lozenge frieze."""
    f, r = Surface(w, h, ARCHIVE_MARGIN, ARCHIVE_TOP, ARCHIVE_BOTTOM), stone.Relief()
    cx = w//2
    rail(r, [(-4, -4), (w+3, -4), (w+3, h+3), (-4, h+3), (-4, -4)])
    line(r, [(0, 0), (w-1, 0), (w-1, h-1), (0, h-1), (0, 0)], .9)
    # A shallow guilloche of linked diamonds extends along the shelf rails.
    for x in range(14, w-13, 12):
        for y in (-8, h+7):
            line(r, [(x-6, y), (x, y-2), (x+6, y), (x, y+2), (x-6, y)], .75)
    # Columns have long vertical flutes and scroll capitals, not side shells.
    for s in (-1, 1):
        x = -12 if s < 0 else w+11
        r.tube([(x, 20), (x, h-18)], 4.3, flat=.65)
        for dx in (-2, 0, 2):
            r.carve([(x+dx, 25), (x+dx, h-24)], 1.3, .45)
        for y in (12, 18, h-17, h-10):
            line(r, [(x-6, y), (x+6, y)], 1.8)
        r.scroll(stone.spiral(x, 3, 8, 1.4, 85, 430), 3, 1.4)
        r.leaf(stone.curve((x+s*4, 17), (x+s*18, 9), (x+s*12, -8), (x+s*2, -14)), 3, 4, s)
        r.leaf(stone.curve((x+s*2, h-17), (x+s*17, h-11), (x+s*13, h+6), (x+s*2, h+12)), 3.1, 4, s)
        # Fine branching ornament along the outer column, punctuated by rosettes.
        mid = h//2
        for dy in (-1, 1):
            r.leaf(stone.curve((x+s*3, mid), (x+s*20, mid+dy*13), (x+s*14, mid+dy*32), (x+s*7, mid+dy*43)), 2.6, 4, s*dy)
        r.dome(x+s*8, mid, 3, 4.5, 3)
        r.carve([(x+s*8, mid-2), (x+s*8, mid+2)], 1.5, .5)
        # The broken pediment arches toward the central archive shield.
        r.tube(stone.curve((8 if s < 0 else w-9, -11), (cx+s*60, -38), (cx+s*35, -34), (cx+s*19, -21)), 3.3)
        r.scroll(stone.spiral(cx+s*23, -23, 5, 1.1, 0 if s > 0 else 180, 345 if s > 0 else -165), 2.2, 1.1)
        r.leaf(stone.curve((cx+s*30, -29), (cx+s*38, -42), (cx+s*49, -39), (cx+s*56, -32)), 2.8, 3, s)
        r.scroll(stone.curve((cx+s*9, h+9), (cx+s*18, h+25), (cx+s*32, h+24), (cx+s*39, h+13)), 2.8, 1.3)
        r.leaf(stone.curve((cx+s*41, h+13), (cx+s*52, h+23), (cx+s*65, h+18), (cx+s*70, h+12)), 2.6, 3, s)
    # Three stone drops under the archive; no workstation scallop shell.
    for dx, length in ((-6, 7), (0, 13), (6, 7)):
        line(r, [(cx+dx, h+8), (cx+dx, h+8+length)], 1)
        r.dome(cx+dx, h+10+length, 1.7, 2.2, 2)
    xy, icon = badge(f, r, cx, -17, symbol, 'shield')
    r.paint(f)
    f.image.alpha_composite(icon, xy)
    return f.finish()


def slot(d, x, y):
    d.rectangle((x-1, y-1, x+16, y+16), fill=WELL, outline=stone.TONES[1])
    d.line((x-1, y+16, x+16, y+16, x+16, y-1), fill=EDGE)


def crate_art():
    panel = Image.new('RGBA', (256, 256))
    d = ImageDraw.Draw(panel)
    d.rectangle((0, 0, 175, 165), fill=FACE, outline=stone.TONES[0])
    # Stone crosshatching on the narrow label/divider rail, with a clear text inset.
    for y in (15, 71, 81, 139, 161):
        d.line((4, y, 171, y), fill=stone.TONES[5])
        for x in range(6, 172, 6):
            d.point((x, y), fill=EDGE)
    for origin, rows in (((8, 18), 3), ((8, 84), 3), ((8, 142), 1)):
        for row in range(rows):
            for col in range(9):
                slot(d, origin[0]+18*col, origin[1]+18*row)
    frame = coffer_frame()
    composite = Image.new('RGBA', frame.size)
    composite.alpha_composite(panel.crop((0, 0, 176, 166)), (26, 33))
    composite.alpha_composite(frame)
    return panel, frame, composite


def source_jar():
    jars = sorted((ROOT / 'shared-mods').glob('toms_storage-*.jar'))
    if not jars:
        raise FileNotFoundError('Tom\'s Storage jar is required to preserve atlas UVs.')
    return jars[-1]


def stone_atlas(original):
    """Map the original panel geometry into the exact stone ramp; preserve alpha."""
    image = original.copy()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = original.getpixel((x, y))
            if not a:
                continue
            level = (r+g+b)//3
            # Neutral slot wells, bevels and faces occupy separate levels in Tom's atlases.
            tone = 0 if level < 50 else 2 if level < 145 else 7 if level > 225 else 4
            image.putpixel((x, y), (*stone.rgb(stone.TONES[tone])[:3], a))
    return image


def save(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def font(size):
    for name in ('C:/Windows/Fonts/consola.ttf', 'DejaVuSansMono.ttf'):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default(size=size)


def panel_preview(atlas, frame, size, origin, title, inventory_y=None):
    image = Image.new('RGBA', frame.size)
    image.alpha_composite(atlas.crop((0, 0, *size)), origin)
    image.alpha_composite(frame)
    # Labels here are preview-only; production PNG layers contain no baked text.
    d = ImageDraw.Draw(image)
    d.text((origin[0]+8, origin[1]+4), title, font=font(8), fill='#e6e8e1')
    if inventory_y is not None:
        d.text((origin[0]+8, origin[1]+inventory_y), 'Inventory', font=font(8), fill='#8a8f86')
    return image


SPECS = {
    'storage_terminal': ((194, 202), 'archive', 'Storage', 108),
    'crafting_terminal': ((194, 256), 'archive', 'Crafting', 162),
    'inventory_link': ((176, 166), 'link', 'Inventory Link', None),
    'inventory_configurator': ((176, 166), 'archive', 'Configurator', None),
    'level_emitter': ((176, 166), 'gauge', 'Level Emitter', 72),
    'tag_filter': ((176, 166), 'filter', 'Tag Filter', None),
}


def previews(crate, screens):
    sheet = Image.new('RGBA', (1440, 1050), '#10130f')
    d = ImageDraw.Draw(sheet)
    d.text((40, 28), 'ARK / STORAGE IN CARVED STONE', font=font(29), fill='#e6e8e1')
    d.text((40, 78), 'Two new ornamental compositions. One stone palette.', font=font(18), fill='#8a8f86')
    d.line((40, 119, 1400, 119), fill=EDGE)
    d.text((56, 153), '01 / STORAGE CRATE', font=font(23), fill='#e6e8e1')
    d.text((748, 153), "02 / TOM'S STORAGE", font=font(23), fill='#e6e8e1')
    sheet.alpha_composite(crate.resize((crate.width*3, crate.height*3), Image.Resampling.NEAREST), (20, 246))
    terminal = screens['storage_terminal']
    scale = min(652/terminal.width, 710/terminal.height)
    sheet.alpha_composite(terminal.resize((round(terminal.width*scale), round(terminal.height*scale)), Image.Resampling.NEAREST), (748, 238))
    d.text((56, 983), 'Braided rails / corner clasps / carved scroll handles', font=font(16), fill='#8a8f86')
    d.text((748, 983), 'Split pediment / fluted pillars / linked-diamond rails', font=font(16), fill='#8a8f86')
    save(sheet, OUT / 'storage-patterns-preview.png')
    sheet = Image.new('RGBA', (1660, 1460), '#10130f')
    d = ImageDraw.Draw(sheet)
    d.text((36, 26), "TOM'S STORAGE / ARCHIVE FAMILY", font=font(28), fill='#e6e8e1')
    d.text((36, 68), 'Storage and crafting terminals, plus the supporting network panels', font=font(18), fill='#8a8f86')
    for i, (name, image) in enumerate(screens.items()):
        x, y = 24+(i % 3)*550, 120+(i//3)*660
        d.text((x+12, y), name.replace('_', ' ').upper(), font=font(20), fill='#c1c7b9')
        scale = min(2, 584/image.height)
        im = image.resize((round(image.width*scale), round(image.height*scale)), Image.Resampling.NEAREST)
        sheet.alpha_composite(im, (x+(520-im.width)//2, y+48))
    save(sheet, OUT / 'toms-storage-suite-preview.png')


def build(refresh_overview=True):
    panel, frame, composite = crate_art()
    save(panel, ART / 'container/storage_crate.png')
    save(frame, ART / 'container/storage_crate_frame.png')
    save(composite, OUT / 'storage-crate-asset.png')
    crate_preview = panel_preview(panel, frame, (176, 166), (26, 33), 'Storage Crate', 72)
    files, screens = [], {}
    with zipfile.ZipFile(source_jar()) as jar:
        for name, (size, symbol, title, inventory_y) in SPECS.items():
            source = Image.open(io.BytesIO(jar.read(f'assets/toms_storage/textures/gui/{name}.png'))).convert('RGBA')
            atlas = stone_atlas(source)
            border = archive_frame(*size, symbol)
            save(atlas, TOMS / f'{name}.png')
            save(border, TOMS / f'{name}_frame.png')
            blank = Image.new('RGBA', border.size)
            blank.alpha_composite(atlas.crop((0, 0, *size)), (ARCHIVE_MARGIN, ARCHIVE_TOP))
            blank.alpha_composite(border)
            save(blank, OUT / f'toms-{name}-asset.png')
            screens[name] = panel_preview(atlas, border, size, (ARCHIVE_MARGIN, ARCHIVE_TOP), title, inventory_y)
            files.append({'name': name, 'atlas': (TOMS / f'{name}.png').relative_to(ROOT).as_posix(),
                          'frame': (TOMS / f'{name}_frame.png').relative_to(ROOT).as_posix(),
                          'panelSize': size, 'textureSize': source.size, 'frameSize': border.size,
                          'frameDrawOffset': [-ARCHIVE_MARGIN, -ARCHIVE_TOP], 'symbol': symbol})
        source = Image.open(io.BytesIO(jar.read('assets/toms_storage/textures/gui/side_scrollbar.png'))).convert('RGBA')
        save(stone_atlas(source), TOMS / 'side_scrollbar.png')
    previews(crate_preview, screens)
    spec = {'style': 'New coffer and archive compositions; P14 carving primitives and stone palette only.',
            'palette': stone.TONES, 'generator': 'tools/build_storage_ui.py',
            'runtime': 'Staged artwork. No Tom resource-pack override or new screen code in this design pass. Dedicated crate screen and Tom overlay/label hooks pending.',
            'crate': {'panelSize': [176, 166], 'textureSize': [256, 256], 'frameSize': list(frame.size),
                      'frameDrawOffset': [-26, -33], 'symbol': 'crate', 'layout': 'Original 27-slot chest coordinates'},
            'toms': files, 'labelColor': '#e6e8e1',
            'integration': 'Tom terminals have variable row counts and side controls. Use an adaptive frame renderer for the new border, preserve the source atlas slices, and apply light labels. The supplied full frames preview the standard five storage rows; they are not a drop-in replacement for dynamic-height UI.'}
    (OUT / 'storage-assets.json').write_text(json.dumps(spec, indent=2)+'\n', encoding='utf-8')
    # Refresh the prior three-part overview so it no longer advertises the reused frame.
    if refresh_overview:
        from build_baroque_ui import preview
        preview(composite)
    stone.preview_symbols()
    print('New coffer frame and six Tom archive panels written; assets staged for screen integration.')


if __name__ == '__main__':
    build()
