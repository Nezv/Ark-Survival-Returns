"""Author the First Guardian item sprites: workshop schematic and trophy."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/main/resources/assets/arksurvivalreturns/textures/item'
OUT.mkdir(parents=True, exist_ok=True)


def schematic():
    im = Image.new('RGBA', (32, 32))
    d = ImageDraw.Draw(im)
    d.rectangle((5, 3, 27, 29), fill='#3b3a35')
    d.rectangle((6, 4, 26, 28), fill='#cdbd8f')
    d.rectangle((8, 6, 24, 12), fill='#b9a97c')
    d.line([(9, 8), (23, 8)], fill='#8a7c58')
    d.line([(9, 10), (19, 10)], fill='#8a7c58')
    for x in range(8, 25, 4):
        d.line((x, 15, x, 26), fill='#b3a57c')
    for y in range(15, 27, 4):
        d.line((8, y, 24, y), fill='#b3a57c')
    d.ellipse((13, 16, 21, 24), outline='#6f6448')
    d.ellipse((15, 18, 19, 22), outline='#6f6448')
    d.line([(17, 13), (17, 16)], fill='#6f6448')
    d.line([(10, 24), (14, 24)], fill='#6f6448')
    im.save(OUT / 'workshop_schematic.png')


def trophy():
    im = Image.new('RGBA', (32, 32))
    d = ImageDraw.Draw(im)
    d.polygon([(9, 3), (23, 3), (24, 7), (20, 20), (16, 29), (12, 20), (8, 7)], fill='#4a4234')
    d.polygon([(10, 5), (22, 5), (23, 7), (19, 19), (16, 26), (13, 19), (9, 7)], fill='#efe6cf')
    d.polygon([(10, 5), (22, 5), (22, 8), (10, 8)], fill='#d9a520')
    d.line([(20, 5), (20, 8)], fill='#a97d12')
    d.line([(13, 10), (14, 18)], fill='#fffaf0')
    d.line([(18, 10), (17, 17)], fill='#cfc3a6')
    im.save(OUT / 'guardian_trophy.png')


def main():
    schematic()
    trophy()
    print('Built 3 First Guardian item sprites.')


if __name__ == '__main__':
    main()
