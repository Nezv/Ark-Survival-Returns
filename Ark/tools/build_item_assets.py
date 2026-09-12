"""Author crisp 32px item sprites from pixel shapes, and an inventory review sheet."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from import_creatures import SPECIES

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/main/resources/assets/arksurvivalreturns/textures/item'
OUT.mkdir(parents=True, exist_ok=True)
PALETTES = {
    'tintoberry': ('#421923', '#9c2e40', '#da4b51', '#ff9a87'),
    'amarberry': ('#5a3519', '#b77c21', '#edbc3e', '#fff094'),
    'azulberry': ('#172b4c', '#2c539a', '#4e89d0', '#a2d9ed'),
    'narcoberry': ('#171426', '#342942', '#60516c', '#b6a0c1'),
}

def berry(name, colors, layout):
    im = Image.new('RGBA', (32, 32)); d = ImageDraw.Draw(im)
    d.line([(15, 13), (16, 5), (20, 3)], fill='#354626', width=2)
    d.polygon([(16, 9), (8, 9), (5, 5), (11, 4), (15, 6)], fill='#273d2a')
    d.polygon([(7, 5), (12, 5), (15, 8), (10, 7)], fill='#66934c')
    d.polygon([(17, 8), (21, 5), (26, 6), (23, 10), (18, 11)], fill='#3f693a')
    d.line([(19, 8), (23, 7)], fill='#89ae58', width=1)
    for x, y, radius in layout:
        d.rounded_rectangle((x-radius, y-radius, x+radius, y+radius), radius=radius-1, fill=colors[0])
        d.rounded_rectangle((x-radius+1, y-radius+1, x+radius-1, y+radius-1), radius=radius-2, fill=colors[1])
        d.ellipse((x-radius+2, y-radius+1, x+radius-2, y+radius-3), fill=colors[2])
        d.rectangle((x-2, y-radius+2, x, y-radius+3), fill=colors[3])
        d.point((x+1, y+radius-1), fill=colors[0])
    im.save(OUT / f'{name}.png')
    return im

def main():
    layouts = [ [(11,15,6),(22,16,6),(16,24,5)], [(10,17,5),(20,14,5),(21,24,5),(12,25,4)],
                [(10,15,5),(21,15,5),(11,24,5),(21,24,5)], [(11,16,6),(22,17,5),(17,25,4)] ]
    sprites = [berry(name, colors, layout) for (name, colors), layout in zip(PALETTES.items(), layouts)]
    # Each species has its own two-tone egg icon, derived from its supplied palette.
    for folder, identifier, *_ in SPECIES:
        palette = Image.open(next((ROOT.parent/'Creatures'/folder/'textures/entity').glob('*.png'))).convert('RGB')
        base, spot = palette.getpixel((2,2)), palette.getpixel((18,2))
        im = Image.new('RGBA',(32,32)); d = ImageDraw.Draw(im)
        d.polygon([(13,3),(19,3),(24,9),(27,19),(26,25),(22,29),(10,29),(6,25),(5,19),(8,9)], fill='#25282c')
        d.polygon([(13,5),(18,5),(22,10),(25,19),(24,24),(21,27),(11,27),(8,24),(7,19),(10,10)], fill=base)
        for box in [(11,9,15,13),(19,16,23,21),(10,22,14,25)]: d.rectangle(box,fill=spot)
        d.line([(12,7),(10,11),(9,16)], fill=tuple(min(255,c+55) for c in base),width=2)
        im.save(OUT/f'{identifier}_spawn_egg.png')
    sheet = Image.new('RGB',(920,320),'#18252b'); d = ImageDraw.Draw(sheet)
    try:
        title = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf',25)
        label = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',19)
    except OSError: title = label = ImageFont.load_default()
    d.text((24,18),'ARK SURVIVAL RETURNS / WILD BERRIES',fill='#e1e7d5',font=title)
    for i, (name, sprite) in enumerate(zip(PALETTES,sprites)):
        x=24+i*225
        enlarged=sprite.resize((160,160),Image.Resampling.NEAREST)
        sheet.paste(enlarged,(x+20,70),enlarged)
        d.text((x+25,244),name.capitalize(),fill='#e1e7d5',font=label)
        d.text((x+25,273),'Sedative / no effect yet' if i==3 else 'Material / no use yet',fill='#99ada8',font=label)
    (ROOT/'docs').mkdir(exist_ok=True)
    sheet.save(ROOT/'docs/berry-assets.png')
    print(f'Built four berry sprites, {len(SPECIES)} spawn egg sprites and review sheet.')

if __name__ == '__main__': main()
