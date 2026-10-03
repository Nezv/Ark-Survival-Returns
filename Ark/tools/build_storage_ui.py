"""P16 storage artwork: Gorgon masks, scaled serpents, painting frames and pillars.

Reuses only P14's carving primitives, discrete palette and light, never ornament().
Tom's source atlases retain their alpha and UV layout. The built-in Ark UI pack
supplies the atlases; StorageFrames draws the adaptive exterior and client mixins
provide light labels and room for the terminal's tall-mode frame.
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
TOM_PACK = ROOT / 'src/main/resources/resourcepacks/ark_ui/assets/toms_storage/textures/gui'
CRATE_MARGIN, CRATE_TOP, CRATE_BOTTOM = 32, 50, 36
ARCHIVE_MARGIN, ARCHIVE_TOP, ARCHIVE_BOTTOM = 44, 64, 36
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


def badge(surface, r, cx, cy, symbol):
    rim = [(cx-15, cy-7), (cx-9, cy-13), (cx+9, cy-13), (cx+15, cy-7),
           (cx+15, cy+7), (cx+9, cy+13), (cx-9, cy+13), (cx-15, cy+7), (cx-15, cy-7)]
    d = ImageDraw.Draw(surface.image)
    d.polygon([(x+surface.left, y+surface.top) for x, y in rim], fill=stone.DISC)
    r.tube(rim, 2.1)
    r.carve(rim, .55, .4)
    return (cx-8+surface.left, cy-8+surface.top), stone.sigil(symbol)


def coffer_frame():
    from stone_figures import picture_frame, snake, gorgon, face_details
    w, h = 176, 168
    f = Surface(w, h, CRATE_MARGIN, CRATE_TOP, CRATE_BOTTOM)
    r = stone.Relief()
    picture_frame(r, w, h, width=20)
    # Two identifiable serpents lie on the broad side rails of a painting frame.
    path = stone.curve((-16, h-7), (-26, h-32), (-8, h-47), (-17, h-67))
    path += stone.curve(path[-1], (-27, 77), (-7, 58), (-18, 41))[1:]
    path += stone.curve(path[-1], (-23, 31), (-22, 22), (-15, 18))[1:]
    snake(r, path, 2.4, 4)
    snake(r, [(w-1-x, y) for x, y in path], 2.4, 4)
    gorgon(r, w//2, -21)
    xy, icon = badge(f, r, w//2, h+13, 'crate')
    r.paint(f)
    face_details(f, w//2, -21)
    f.image.alpha_composite(icon, xy)
    return f.finish()


def archive_frame(w, h, symbol='archive'):
    from stone_figures import picture_frame, pillar, block, snake, gorgon, face_details
    f = Surface(w, h, ARCHIVE_MARGIN, ARCHIVE_TOP, ARCHIVE_BOTTOM)
    r = stone.Relief()
    picture_frame(r, w, h, width=8)
    for x in (-20, w+19):
        pillar(r, x, -9, h+16)
    # A continuous, stepped lintel and plinth give the frame architectural weight.
    for y0, y1, overhang in ((-24, -20, 35), (-19, -15, 31), (-14, -6, 28)):
        block(r, -overhang, y0, w+overhang, y1, 4)
    for y0, y1, overhang in ((h+3, h+8, 28), (h+9, h+13, 32), (h+14, h+18, 35)):
        block(r, -overhang, y0, w+overhang, y1, 4)
    for x in range(5, w-4, 8):
        block(r, x-1, -12, x+2, -8, 4.5)
    # Paired snakes rest along the lintel, facing the central Gorgon.
    for s in (-1, 1):
        cx = w//2
        path = stone.curve((cx+s*(w/2+19), -19), (cx+s*65, -30),
                           (cx+s*57, -12), (cx+s*42, -22))
        path += stone.curve(path[-1], (cx+s*36, -29), (cx+s*28, -27), (cx+s*25, -19))[1:]
        snake(r, path, 2.1, 4)
    gorgon(r, w//2, -31)
    xy, icon = badge(f, r, w//2, h+15, symbol)
    r.paint(f)
    face_details(f, w//2, -31)
    f.image.alpha_composite(icon, xy)
    return f.finish()


def slot(d, x, y):
    d.rectangle((x-1, y-1, x+16, y+16), fill=WELL, outline=stone.TONES[1])
    d.line((x-1, y+16, x+16, y+16, x+16, y-1), fill=EDGE)


def crate_art():
    panel = Image.new('RGBA', (256, 256))
    d = ImageDraw.Draw(panel)
    d.rectangle((0, 0, 175, 167), fill=FACE, outline=stone.TONES[0])
    # Stone crosshatching on the narrow label/divider rail, with a clear text inset.
    for y in (15, 71, 82, 140, 163):
        d.line((4, y, 171, y), fill=stone.TONES[5])
        for x in range(6, 172, 6):
            d.point((x, y), fill=EDGE)
    for origin, rows in (((8, 18), 3), ((8, 85), 3), ((8, 143), 1)):
        for row in range(rows):
            for col in range(9):
                slot(d, origin[0]+18*col, origin[1]+18*row)
    frame = coffer_frame()
    composite = Image.new('RGBA', frame.size)
    composite.alpha_composite(panel.crop((0, 0, 176, 168)), (CRATE_MARGIN, CRATE_TOP))
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
    d.text((40, 78), 'Gorgons, serpents and architectural carving in the original stone palette.', font=font(18), fill='#8a8f86')
    d.line((40, 119, 1400, 119), fill=EDGE)
    d.text((56, 153), '01 / STORAGE CRATE', font=font(23), fill='#e6e8e1')
    d.text((748, 153), "02 / TOM'S STORAGE", font=font(23), fill='#e6e8e1')
    scale = min(652/crate.width, 710/crate.height)
    sheet.alpha_composite(crate.resize((round(crate.width*scale), round(crate.height*scale)), Image.Resampling.NEAREST), (28, 238))
    terminal = screens['storage_terminal']
    scale = min(652/terminal.width, 710/terminal.height)
    sheet.alpha_composite(terminal.resize((round(terminal.width*scale), round(terminal.height*scale)), Image.Resampling.NEAREST), (748, 238))
    d.text((56, 983), 'Gorgon mask / scaled snakes / deep picture-frame moulding', font=font(16), fill='#8a8f86')
    d.text((748, 983), 'Gorgon mask / paired snakes / classical stone columns', font=font(16), fill='#8a8f86')
    save(sheet, OUT / 'storage-patterns-preview.png')
    sheet = Image.new('RGBA', (1660, 1460), '#10130f')
    d = ImageDraw.Draw(sheet)
    d.text((36, 26), "TOM'S STORAGE / ARCHIVE FAMILY", font=font(28), fill='#e6e8e1')
    d.text((36, 68), 'Storage and crafting terminals, plus the supporting network panels', font=font(18), fill='#8a8f86')
    for i, (name, image) in enumerate(screens.items()):
        x, y = 24+(i % 3)*550, 120+(i//3)*660
        d.text((x+12, y), name.replace('_', ' ').upper(), font=font(20), fill='#c1c7b9')
        scale = min(2, 520/image.width, 584/image.height)
        im = image.resize((round(image.width*scale), round(image.height*scale)), Image.Resampling.NEAREST)
        sheet.alpha_composite(im, (x+(520-im.width)//2, y+48))
    save(sheet, OUT / 'toms-storage-suite-preview.png')


def motif_preview():
    from stone_figures import gorgon, face_details, snake, pillar
    sheet = Image.new('RGBA', (1120, 410), '#10130f')
    d = ImageDraw.Draw(sheet)
    d.text((30, 20), 'CARVED MOTIFS / NATIVE PIXEL DETAIL', font=font(24), fill='#e6e8e1')
    for i, name in enumerate(('gorgon', 'serpent', 'pillar')):
        height = 112 if name == 'pillar' else 64
        f, r = Surface(64, height, 0, 0, 0), stone.Relief()
        if name == 'gorgon':
            gorgon(r, 32, 34)
        elif name == 'serpent':
            path = stone.curve((12, 54), (50, 58), (55, 32), (32, 36))
            path += stone.curve(path[-1], (3, 43), (9, 15), (30, 24))[1:]
            path += stone.curve(path[-1], (54, 38), (53, 12), (42, 10))[1:]
            snake(r, path, 2.5, 2)
        else:
            pillar(r, 32, 6, 106)
        r.paint(f)
        if name == 'gorgon':
            face_details(f, 32, 34)
        image = f.finish()
        save(image, ART / f'storage/motifs/{name}.png')
        scale = 2.5 if name == 'pillar' else 4
        image = image.resize((round(image.width*scale), round(image.height*scale)), Image.Resampling.NEAREST)
        x = 16+i*370+(350-image.width)//2
        sheet.alpha_composite(image, (x, 73))
        label = {'gorgon': 'Gorgon mask and snake hair', 'serpent': 'Tapered snake, scales and head', 'pillar': 'Fluted shaft, capital and plinth'}[name]
        width = d.textbbox((0, 0), label, font=font(16))[2]
        d.text((16+i*370+(350-width)/2, 367), label, font=font(16), fill='#8a8f86')
    save(sheet, OUT / 'storage-motifs-preview.png')


def build(refresh_overview=True):
    panel, frame, composite = crate_art()
    save(panel, ART / 'container/storage_crate.png')
    save(frame, ART / 'container/storage_crate_frame.png')
    save(composite, OUT / 'storage-crate-asset.png')
    crate_preview = panel_preview(panel, frame, (176, 168), (CRATE_MARGIN, CRATE_TOP), 'Storage Crate', 74)
    files, screens = [], {}
    with zipfile.ZipFile(source_jar()) as jar:
        for name, (size, symbol, title, inventory_y) in SPECS.items():
            source = Image.open(io.BytesIO(jar.read(f'assets/toms_storage/textures/gui/{name}.png'))).convert('RGBA')
            atlas = stone_atlas(source)
            border = archive_frame(*size, symbol)
            save(atlas, TOMS / f'{name}.png')
            save(atlas, TOM_PACK / f'{name}.png')
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
        save(stone_atlas(source), TOM_PACK / 'side_scrollbar.png')
        # The mod's custom button sprites share the same stone surfaces. Keep icons intact.
        for name in jar.namelist():
            prefix = 'assets/toms_storage/textures/gui/sprites/widget/'
            if name.startswith(prefix) and name.endswith('.png'):
                source = Image.open(io.BytesIO(jar.read(name))).convert('RGBA')
                save(stone_atlas(source), TOM_PACK / 'sprites/widget' / Path(name).name)
    previews(crate_preview, screens)
    motif_preview()
    spec = {'style': 'Figurative Gorgon/serpent painting frame and classical colonnade; P14 stone palette and lighting.',
            'palette': stone.TONES, 'generator': 'tools/build_storage_ui.py',
            'runtime': 'Dedicated StorageCrateMenu/Screen; Tom atlas overrides in the built-in Ark UI pack with adaptive StorageFrames and light-label client hooks.',
            'crate': {'panelSize': [176, 168], 'textureSize': [256, 256], 'frameSize': list(frame.size),
                      'frameDrawOffset': [-CRATE_MARGIN, -CRATE_TOP], 'symbol': 'crate', 'layout': 'Original 27-slot chest coordinates'},
            'toms': files, 'labelColor': '#e6e8e1',
            'integration': 'StorageFrames stretches side rails to native panel height and contracts exterior crest/footer to available screen space. Frames render before native panels, floating slots and widgets. Tall terminals reserve ornament space. Native atlas slices and slot hitboxes remain unchanged. In-client visual review pending.'}
    (OUT / 'storage-assets.json').write_text(json.dumps(spec, indent=2)+'\n', encoding='utf-8')
    # Refresh the prior three-part overview so it no longer advertises the reused frame.
    if refresh_overview:
        from build_baroque_ui import preview
        preview(composite)
    stone.preview_symbols()
    print('Gorgon and serpent frames, six active Tom panels, stone widgets and reusable motifs written.')


if __name__ == '__main__':
    build()
