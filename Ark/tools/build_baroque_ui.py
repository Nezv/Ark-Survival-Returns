"""P16 first asset pass: hotbar, shared controls and a dedicated Storage Crate panel.

Uses P14's original height-field carving, lighting and palette. No generated art.
Run from Ark: python tools/build_baroque_ui.py. Also called by build_ui_pack.py.
The crate assets are staged for a dedicated screen; generic_54 is not replaced.
"""
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
import build_workstation_crests as crest

ROOT = Path(__file__).resolve().parents[1]
GUI = ROOT / 'src/main/resources/resourcepacks/ark_ui/assets/minecraft/textures/gui'
CRATE = ROOT / 'src/main/resources/assets/arksurvivalreturns/textures/gui/container'
OUT = ROOT / 'design/ui-rework/baroque'
PAL = json.loads((crest.DESIGN / 'graph_style.json').read_text(encoding='utf-8'))['palette']
FILES = []


class Canvas:
    """A relief target independent of the workstation's panel dimensions."""
    def __init__(self, w, h, state='normal'):
        self.image = Image.new('RGBA', (w, h))
        self.ramp = [crest.rgb(c) for c in crest.TONES]
        if state == 'hover':
            self.ramp = [tuple(round(c[i] * .65 + crest.rgb(PAL['buttonHotLight'])[i] * .35) for i in range(3)) + (255,) for c in self.ramp]
        elif state == 'disabled':
            self.ramp = [tuple(round(v * .55) for v in c[:3]) + (255,) for c in self.ramp]

    def put(self, x, y, c):
        if 0 <= x < self.image.width and 0 <= y < self.image.height:
            self.image.putpixel((x, y), self.ramp[max(0, min(len(self.ramp)-1, c))] if isinstance(c, int) else c)


def save(image, path, logical=None, border=None):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)
    FILES.append(path.relative_to(ROOT).as_posix())
    if logical and border is not None:
        meta = {'gui': {'scaling': {'type': 'nine_slice', 'width': logical[0], 'height': logical[1], 'border': border}}}
        path.with_suffix('.png.mcmeta').write_text(json.dumps(meta, indent=2) + '\n', encoding='utf-8')
    return image


def moulding(r, w, h, inset=1, beads=True):
    for d, v in enumerate((1.1, 2.3, 1.4, .5)):
        a = inset + d
        for x in range(a, w-a):
            r.lift(x, a, v)
            r.lift(x, h-1-a, v)
        for y in range(a, h-a):
            r.lift(a, y, v)
            r.lift(w-1-a, y, v)
    if beads:
        for x in range(8, w-7, 7):
            for y in (inset+1.5, h-inset-1.5):
                r.dome(x+.5, y, 1.2, 1.0, 1.2, 1.5)


def button(w=200, h=20, state='normal', caps=True):
    # 2 texels/design unit: detailed carving fits inside normal 20-unit controls.
    c = Canvas(w*2, h*2, state)
    d = ImageDraw.Draw(c.image)
    face = PAL['buttonOff'] if state == 'disabled' else PAL['buttonHot'] if state == 'hover' else PAL['face']
    d.rounded_rectangle((0, 0, w*2-1, h*2-1), radius=4, fill=face, outline=PAL['outline'])
    r = crest.Relief()
    moulding(r, w*2, h*2)
    if caps:
        for side in (-1, 1):
            cx = 9 if side < 0 else w*2-9
            cy = h
            r.scroll(crest.spiral(cx, cy, 5.5, 1, 90, 410), 1.6, .7)
            for sy in (-1, 1):
                r.leaf(crest.curve((cx, cy+sy*5), (cx-side*3, cy+sy*7), (cx-side*2, cy+sy*10), (cx, cy+sy*13)), 1.2, 2)
    r.paint(c)
    if state == 'hover':
        ImageDraw.Draw(c.image).line((18, h*2-6, w*2-19, h*2-6), fill=PAL['buttonHotLight'])
    return c.image


def widgets():
    for name, state in [('button', 'normal'), ('button_highlighted', 'hover'), ('button_disabled', 'disabled')]:
        save(button(state=state), GUI / f'sprites/widget/{name}.png', (200, 20), {'left': 8, 'right': 8, 'top': 4, 'bottom': 4})
    # Sliders, tabs, checkboxes and narrow scroll controls share the same material.
    for name, state in [('slider', 'normal'), ('slider_highlighted', 'hover')]:
        save(button(state=state, caps=False), GUI / f'sprites/widget/{name}.png', (200, 20), 4)
    for name in ('slider_handle', 'slider_handle_highlighted', 'scroller', 'scroller_background',
                 'tab', 'tab_highlighted', 'tab_selected', 'tab_selected_highlighted',
                 'checkbox', 'checkbox_highlighted', 'checkbox_selected', 'checkbox_selected_highlighted'):
        path = GUI / f'sprites/widget/{name}.png'
        # Vanilla dimensions are authoritative, including the tab's asymmetric border.
        from build_ui_pack import vanilla
        original = vanilla(f'sprites/widget/{name}.png')
        w, h = original.size
        state = 'hover' if 'highlighted' in name else 'normal'
        im = button(w, h, state, caps=False)
        d = ImageDraw.Draw(im)
        if 'checkbox_selected' in name:
            d.line([(w-7, h), (w-2, h+5), (w+7, h-6)], fill=PAL['title'], width=3)
        if 'slider_handle' in name or name == 'scroller':
            for x in (w-2, w+2):
                d.line((x, h-4, x, h+4), fill=PAL['muted'], width=1)
        if name.startswith('tab') and 'selected' in name:
            d.line((10, h*2-5, w*2-11, h*2-5), fill=PAL['select'], width=2)
        # Retain the tab silhouettes/cutouts; the generated texture has twice the resolution.
        im.putalpha(original.getchannel('A').resize(im.size, Image.Resampling.NEAREST))
        save(im, path)


def hotbar():
    c = Canvas(364, 44)
    d = ImageDraw.Draw(c.image)
    d.rounded_rectangle((0, 0, 363, 43), radius=3, fill=PAL['face'], outline=PAL['outline'])
    for i in range(9):
        x = 2 + i*40
        d.rectangle((x+2, 6, x+37, 37), fill=PAL['slotFace'])
        d.line((x+2, 37, x+37, 37), fill=PAL['bevelLight'])
        if i:
            d.line((x, 6, x, 37), fill=PAL['bevelLight'])
            d.line((x+1, 6, x+1, 37), fill=PAL['outline'])
    r = crest.Relief()
    moulding(r, 364, 44, beads=True)
    # Small curled bosses between wells; the item area remains empty.
    for x in range(42, 324, 40):
        for y in (3, 41):
            r.dome(x, y, 2, 1.5, 1.5, .8)
    r.paint(c)
    save(c.image, GUI / 'sprites/hud/hotbar.png')
    sel = Image.new('RGBA', (48, 46))
    d = ImageDraw.Draw(sel)
    for box, color in [((0, 0, 47, 45), PAL['outline']), ((2, 2, 45, 43), '#9a482b'), ((3, 3, 44, 42), PAL['select']), ((4, 4, 43, 41), '#eec09b')]:
        d.rounded_rectangle(box, radius=3, outline=color, width=1)
    for x in (4, 43):
        for y in (4, 41):
            d.polygon([(x-3, y), (x, y-3), (x+3, y), (x, y+3)], fill=PAL['select'], outline='#f1cba9')
    save(sel, GUI / 'sprites/hud/hotbar_selection.png')
    from build_ui_pack import vanilla
    for side in ('left', 'right'):
        name = f'sprites/hud/hotbar_offhand_{side}.png'
        original = vanilla(name)
        im = button(*original.size, caps=False)
        im.putalpha(original.getchannel('A').resize(im.size, Image.Resampling.NEAREST))
        save(im, GUI / name)


def crate_sigil():
    im = Image.new('RGBA', (16, 16))
    d = ImageDraw.Draw(im)
    d.rectangle((1, 3, 14, 13), fill='#66462d', outline='#231c13')
    for y in (4, 7, 10):
        d.line((2, y, 13, y), fill='#b58a53')
        d.line((2, y+1, 13, y+1), fill='#865c37')
    for x in (3, 11):
        d.rectangle((x, 3, x+1, 13), fill='#8a8f86')
        for y in (4, 12):
            d.point((x, y), fill='#e6e8e1')
    d.rectangle((7, 6, 8, 9), fill='#d0a86b')
    d.point((8, 8), fill='#35271a')
    return crest.outline(im)


def crate():
    # Same original 27-slot chest coordinates: slot contents remain vanilla-sized.
    panel = Image.new('RGBA', (256, 256))
    d = ImageDraw.Draw(panel)
    d.rectangle((0, 0, 175, 165), fill=PAL['face'], outline=PAL['outline'])
    d.rectangle((4, 15, 171, 70), fill='#24241b')
    for x in range(5, 171, 14):
        d.line((x, 16, x, 69), fill='#303025')
        d.line((x+1, 16, x+1, 69), fill='#171a13')
    slots = [(8+x*18, 18+y*18) for y in range(3) for x in range(9)]
    slots += [(8+x*18, 84+y*18) for y in range(3) for x in range(9)]
    slots += [(8+x*18, 142) for x in range(9)]
    for x, y in slots:
        d.rectangle((x-1, y-1, x+16, y+16), fill=PAL['slotFace'], outline=PAL['bevelDark'])
        d.line((x-1, y+16, x+16, y+16), fill=PAL['bevelLight'])
        d.line((x+16, y-1, x+16, y+16), fill=PAL['bevelLight'])
    save(panel, CRATE / 'storage_crate.png')
    # Temporarily parameterize the original carver; restore its globals for other builders.
    values = {'PANEL_W': 176, 'PANEL_H': 166, 'RAIL': 74, 'CX': 88, 'FRAME_W': 228, 'FRAME_H': 229}
    old = {k: getattr(crest, k) for k in values}
    try:
        for k, v in values.items():
            setattr(crest, k, v)
        f = crest.Frame()
        relief = crest.ornament()
        # Omit the workstation craft rail from the inventory label's safe region.
        relief.h = {p: v for p, v in relief.h.items() if not (3 <= p[0] < 173 and 70 <= p[1] <= 81)}
        relief.paint(f)
        for y in range(-16, 6):
            for x in range(77, 100):
                if math.hypot(x+.5-88, y+.5+5) <= 10.2:
                    f.put(x, y, crest.DISC)
        f.image.alpha_composite(crate_sigil(), (106, 20))
        frame = save(f.finish(), CRATE / 'storage_crate_frame.png')
    finally:
        for k, v in old.items():
            setattr(crest, k, v)
    save(crate_sigil().resize((32, 32), Image.Resampling.NEAREST), CRATE / 'storage_crate_sigil.png')
    composed = Image.new('RGBA', frame.size)
    composed.alpha_composite(panel.crop((0, 0, 176, 166)), (26, 33))
    composed.alpha_composite(frame)
    save(composed, OUT / 'storage-crate-asset.png')
    return composed


def nine_slice(im, width):
    """Preview the actual button metadata, not a whole-image stretch."""
    out = Image.new('RGBA', (width*2, 40))
    out.alpha_composite(im.crop((0, 0, 16, 40)))
    out.alpha_composite(im.crop((16, 0, im.width-16, 40)).resize((width*2-32, 40), Image.Resampling.NEAREST), (16, 0))
    out.alpha_composite(im.crop((im.width-16, 0, im.width, 40)), (width*2-16, 0))
    return out


def preview(crate_image):
    OUT.mkdir(parents=True, exist_ok=True)
    sheet = Image.new('RGBA', (1400, 1000), '#10130f')
    d = ImageDraw.Draw(sheet)
    def font(size):
        for name in ('C:/Windows/Fonts/consola.ttf', 'DejaVuSansMono.ttf'):
            try:
                return ImageFont.truetype(name, size)
            except OSError:
                pass
        return ImageFont.load_default(size=size)
    large, medium, small = font(30), font(19), font(15)
    def text(x, y, value, font=medium, color='#e6e8e1'):
        d.text((x, y), value, fill=color, font=font)
    text(40, 25, 'ARK  /  BAROQUE INTERFACE', large)
    text(40, 69, 'P14 carving language  /  First asset pass', small, '#8a8f86')
    d.line((40, 102, 1360, 102), fill='#3a4034')
    text(40, 126, '01  ITEM BAR')
    bar = Image.open(GUI / 'sprites/hud/hotbar.png')
    selection = Image.open(GUI / 'sprites/hud/hotbar_selection.png')
    hud = Image.new('RGBA', (368, 48))
    hud.alpha_composite(bar, (2, 2))
    hud.alpha_composite(selection, (80, 0))
    sheet.alpha_composite(hud.resize((1104, 144), Image.Resampling.NEAREST), (148, 165))
    text(150, 321, '9 clear wells   /   ember selection   /   matching offhand frames', small, '#8a8f86')
    text(40, 383, '02  SHARED BUTTONS')
    for i, (state, label) in enumerate([('normal', 'Singleplayer'), ('hover', 'Options'), ('disabled', 'Create New World')]):
        im = nine_slice(button(state=state), 200)
        sheet.alpha_composite(im, (40, 429+i*76))
        box = d.textbbox((0, 0), label, font=medium)
        text(240-(box[2]-box[0])/2, 439+i*76, label, medium, '#5d6258' if state == 'disabled' else '#e6e8e1')
        text(454, 440+i*76, state.upper(), small, '#8a8f86')
    for i, label in enumerate(('Done', 'Back')):
        sheet.alpha_composite(nine_slice(button(state='hover' if i == 0 else 'normal'), 80), (40+i*180, 677))
        text(91+i*180, 687, label)
    text(40, 739, 'Fixed carved endcaps; stretches with menu width.', small, '#8a8f86')
    text(40, 765, 'Titlescreen / options / new game / vanilla controls', small, '#8a8f86')
    text(650, 383, '03  STORAGE CRATE')
    # The full frame is shown at 2x, keeping the lower shell visible in the sheet.
    sheet.alpha_composite(crate_image.resize((456, 458), Image.Resampling.NEAREST), (762, 425))
    text(832, 506, 'Storage Crate', small)
    text(832, 638, 'Inventory', small, '#8a8f86')
    text(713, 904, '27 slots / crate sigil / original workstation relief', small, '#8a8f86')
    text(713, 930, 'Dedicated screen asset; runtime hookup pending.', small, '#8a8f86')
    save(sheet, OUT / 'baroque-ui-preview.png')


def build():
    FILES.clear()
    widgets()
    hotbar()
    preview(crate())
    spec = {'scope': 'P16 first asset pass', 'source': 'tools/build_workstation_crests.py',
            'runtime': {'hotbar': 'Ark UI resource pack', 'buttons': 'Ark UI shared vanilla widget sprites', 'storage_crate': 'Asset only; dedicated screen registration pending. Do not replace generic_54.'},
            'storage_crate': {'panelSize': [176, 166], 'textureSize': [256, 256], 'frameSize': [228, 229], 'panelOriginInFrame': [26, 33], 'slotSize': 16, 'slotPitch': 18, 'containerOrigin': [8, 18], 'containerGrid': [9, 3], 'inventoryOrigin': [8, 84], 'hotbarOrigin': [8, 142], 'labelOrigins': [[8, 6], [8, 72]], 'labelColor': PAL['title'], 'frameDrawOffset': [-26, -33]},
            'files': FILES}
    (OUT / 'assets.json').write_text(json.dumps(spec, indent=2) + '\n', encoding='utf-8')
    print(f'P16: wrote {len(FILES)} assets; Storage Crate screen hookup remains pending.')


if __name__ == '__main__':
    build()
