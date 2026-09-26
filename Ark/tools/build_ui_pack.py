"""Build the "Ark UI" resource pack: a carved-stone re-skin of the vanilla interface.

Every texture starts from the vanilla 26.1.2 original in the local source jar, so every slot, arrow and
widget keeps its exact position (the game draws them at fixed coordinates). Neutral greys are remapped:

* Containers (inventory, chests, crafting, the cooking pot's dispenser panel...) become light limestone
  with a fine grain and moss-grey slots. Vanilla draws container titles in dark grey, so the panel stays
  light enough to read them.
* Buttons, tabs, sliders, the hotbar and tooltips become dark basalt with moss and ember accents, the
  same palette as the project showcase (Ark-Survival-Returns.html).

Run from Ark: python tools/build_ui_pack.py
"""
import io
import json
import random
import zipfile
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / 'build/moddev/artifacts/minecraft-patched-26.1.2.109-sources.jar'
PACK = ROOT / 'src/main/resources/resourcepacks/ark_ui'
GUI = PACK / 'assets/minecraft/textures/gui'
PREVIEW = ROOT / 'design/ui-rework'

INK = (34, 37, 31)
MOSS = (75, 102, 65)
MOSS_LIGHT = (143, 176, 127)
EMBER = (184, 72, 27)
EMBER_LIGHT = (226, 118, 63)

# Vanilla grey level -> limestone container palette.
LIGHT = [(0, INK), (55, (61, 66, 55)), (85, (126, 131, 117)), (139, (132, 139, 122)),
         (198, (211, 214, 205)), (255, (242, 243, 239))]
# Vanilla grey level -> basalt widget palette.
DARK = [(0, (15, 17, 14)), (44, (38, 41, 35)), (93, (61, 66, 55)), (111, (52, 56, 47)),
        (126, (90, 96, 82)), (147, (115, 122, 105)), (198, (150, 156, 140)), (255, (230, 232, 225))]
MOSS_RAMP = [(0, (15, 17, 14)), (111, MOSS), (117, MOSS), (255, (230, 232, 225))]
EMBER_RAMP = [(0, (58, 24, 10)), (100, (138, 51, 16)), (175, EMBER), (235, EMBER_LIGHT), (255, (246, 188, 140))]


def vanilla(path):
    with zipfile.ZipFile(JAR) as jar:
        return Image.open(io.BytesIO(jar.read('assets/minecraft/textures/gui/' + path))).convert('RGBA')


def vanilla_bytes(path):
    with zipfile.ZipFile(JAR) as jar:
        return jar.read('assets/minecraft/textures/gui/' + path)


def ramp(level, stops):
    for (a, ca), (b, cb) in zip(stops, stops[1:]):
        if a <= level <= b:
            t = 0 if b == a else (level - a) / (b - a)
            return tuple(round(ca[i] + (cb[i] - ca[i]) * t) for i in range(3))
    return stops[-1][1] if level > stops[-1][0] else stops[0][1]


def remap(image, stops, grain=None, neutral_only=True, seed=1):
    """Maps grey pixels through a palette ramp; optional grain on the panel face keeps it from looking flat."""
    out = image.copy()
    px = out.load()
    rng = random.Random(seed)
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            if neutral_only and (max(r, g, b) - min(r, g, b) > 6):
                continue
            level = (r + g + b) // 3
            color = ramp(level, stops)
            if grain and grain[0] <= level <= grain[1]:
                shade = rng.choice((-3, -2, -1, 0, 0, 0, 1, 1, 2)) - (5 if rng.random() < 0.03 else 0)
                color = tuple(max(0, min(255, c + shade)) for c in color)
            px[x, y] = (*color, a)
    return out


def save(image, path):
    target = GUI / path
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target)
    return image


def copy_meta(path):
    try:
        data = vanilla_bytes(path + '.mcmeta')
    except KeyError:
        return
    (GUI / (path + '.mcmeta')).write_bytes(data)


def containers():
    with zipfile.ZipFile(JAR) as jar:
        names = [n for n in jar.namelist() if n.endswith('.png') and (
            n.startswith('assets/minecraft/textures/gui/container/')
            or n.startswith('assets/minecraft/textures/gui/sprites/container/')
            or n.startswith('assets/minecraft/textures/gui/sprites/recipe_book/')
            or n == 'assets/minecraft/textures/gui/recipe_book.png')]
    for i, name in enumerate(sorted(names)):
        path = name.split('textures/gui/')[1]
        save(remap(vanilla(path), LIGHT, grain=(190, 205), seed=i), path)
        copy_meta(path)
    return len(names)


def widgets():
    count = 0
    for path, stops in [('sprites/widget/button.png', DARK), ('sprites/widget/button_disabled.png', DARK),
                        ('sprites/widget/button_highlighted.png', MOSS_RAMP),
                        ('sprites/widget/slider.png', DARK), ('sprites/widget/slider_highlighted.png', DARK),
                        ('sprites/widget/slider_handle.png', DARK), ('sprites/widget/slider_handle_highlighted.png', MOSS_RAMP),
                        ('sprites/widget/tab.png', DARK), ('sprites/widget/tab_highlighted.png', DARK),
                        ('sprites/widget/tab_selected.png', DARK), ('sprites/widget/tab_selected_highlighted.png', DARK),
                        ('sprites/widget/scroller.png', DARK), ('sprites/widget/scroller_background.png', DARK),
                        ('sprites/widget/checkbox.png', DARK), ('sprites/widget/checkbox_highlighted.png', MOSS_RAMP),
                        ('sprites/widget/checkbox_selected.png', DARK), ('sprites/widget/checkbox_selected_highlighted.png', MOSS_RAMP),
                        ('sprites/hud/hotbar_offhand_left.png', DARK), ('sprites/hud/hotbar_offhand_right.png', DARK)]:
        save(remap(vanilla(path), stops), path)
        copy_meta(path)
        count += 1
    return count


def hud():
    # Hotbar: the translucent slot wells become basalt, the frame greys follow the widget ramp.
    bar = vanilla('sprites/hud/hotbar.png')
    px = bar.load()
    for y in range(bar.height):
        for x in range(bar.width):
            r, g, b, a = px[x, y]
            if a and a < 255:
                px[x, y] = (20, 23, 19, 196)
    save(remap(bar, DARK), 'sprites/hud/hotbar.png')
    for side in ('left', 'right'):
        path = f'sprites/hud/hotbar_offhand_{side}.png'
        off = Image.open(GUI / path)
        px = off.load()
        for y in range(off.height):
            for x in range(off.width):
                r, g, b, a = px[x, y]
                if a and a < 255:
                    px[x, y] = (20, 23, 19, 196)
        off.save(GUI / path)
    selection = vanilla('sprites/hud/hotbar_selection.png')
    px = selection.load()
    for y in range(selection.height):
        for x in range(selection.width):
            r, g, b, a = px[x, y]
            if a == 255 and (r, g, b) != (0, 0, 0):
                px[x, y] = (*ramp((r + g + b) // 3 + 30, EMBER_RAMP), 255)
            elif a == 255:
                px[x, y] = (40, 14, 4, 255)
    save(selection, 'sprites/hud/hotbar_selection.png')
    xp = vanilla('sprites/hud/experience_bar_background.png')
    save(remap(xp, DARK, neutral_only=False), 'sprites/hud/experience_bar_background.png')
    return 5


def tooltips():
    background = vanilla('sprites/tooltip/background.png')
    px = background.load()
    for y in range(background.height):
        for x in range(background.width):
            if px[x, y][3]:
                px[x, y] = (22, 24, 20, 244)
    save(background, 'sprites/tooltip/background.png')
    copy_meta('sprites/tooltip/background.png')
    frame = vanilla('sprites/tooltip/frame.png')
    px = frame.load()
    for y in range(frame.height):
        t = y / (frame.height - 1)
        color = tuple(round(MOSS_LIGHT[i] + (MOSS[i] - MOSS_LIGHT[i]) * t) for i in range(3))
        for x in range(frame.width):
            if px[x, y][3]:
                px[x, y] = (*color, 200)
    save(frame, 'sprites/tooltip/frame.png')
    copy_meta('sprites/tooltip/frame.png')
    return 2


def pack_meta():
    PACK.mkdir(parents=True, exist_ok=True)
    (PACK / 'pack.mcmeta').write_text(json.dumps({"pack": {
        "description": "Ark UI: carved stone interface for Ark Survival Returns",
        "min_format": 84, "max_format": 84}}, indent=2) + '\n', encoding='utf-8')


def preview():
    """Offline sheet of the main screens at 3x, for the showcase and for review. Not a client capture."""
    PREVIEW.mkdir(parents=True, exist_ok=True)
    sheet = Image.new('RGBA', (560 * 3 // 2 + 40, 470), (22, 24, 20, 255))
    inv = Image.open(GUI / 'container/inventory.png').crop((0, 0, 176, 166))
    chest = Image.open(GUI / 'container/generic_54.png')
    chest_img = Image.new('RGBA', (176, 168))
    chest_img.alpha_composite(chest.crop((0, 0, 176, 71)), (0, 0))
    chest_img.alpha_composite(chest.crop((0, 126, 176, 222)), (0, 71))
    sheet.alpha_composite(inv.resize((264, 249), Image.Resampling.NEAREST), (20, 20))
    sheet.alpha_composite(chest_img.resize((264, 252), Image.Resampling.NEAREST), (304, 20))
    bar = Image.open(GUI / 'sprites/hud/hotbar.png')
    sel = Image.open(GUI / 'sprites/hud/hotbar_selection.png')
    hud = Image.new('RGBA', (184, 24))
    hud.alpha_composite(bar, (1, 1))
    hud.alpha_composite(sel, (1 + 20 * 2 - 1, 0))  # third slot selected, drawn one pixel up-left like vanilla
    sheet.alpha_composite(hud.resize((552, 72), Image.Resampling.NEAREST), (20, 300))
    for i, name in enumerate(('button', 'button_highlighted', 'button_disabled')):
        b = Image.open(GUI / f'sprites/widget/{name}.png')
        sheet.alpha_composite(b.resize((300, 30), Image.Resampling.NEAREST), (588, 20 + i * 44))
    tip = Image.new('RGBA', (100, 40))
    tip.alpha_composite(Image.open(GUI / 'sprites/tooltip/background.png').resize((100, 40)))
    tip.alpha_composite(Image.open(GUI / 'sprites/tooltip/frame.png').resize((100, 40)))
    sheet.alpha_composite(tip.resize((300, 120), Image.Resampling.NEAREST), (588, 160))
    sheet.save(PREVIEW / 'ui-preview.png')


def main():
    pack_meta()
    count = containers() + widgets() + hud() + tooltips()
    preview()
    print(f'Wrote {count} textures to {PACK}')


if __name__ == '__main__':
    main()
