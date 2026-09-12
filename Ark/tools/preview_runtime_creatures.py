"""Offline preview of the imported runtime resources, using the repository's existing renderer."""
import json
import sys
import math
from PIL import Image
from import_creatures import ROOT, ASSETS, SPECIES
sys.path.insert(0, str(ROOT.parent/'scripts'))
from preview_dinosaurs import decode_geometry, render

def main():
    sheet = Image.new('RGB', (1500, math.ceil(len(SPECIES)/3)*370), '#131e27')
    for i, (_, identifier, height, idle, *_) in enumerate(SPECIES):
        geo = json.loads((ASSETS/f'geckolib/models/entity/{identifier}.geo.json').read_text())
        anim = json.loads((ASSETS/f'geckolib/animations/entity/{identifier}.animation.json').read_text())['animations'][idle]
        tex = Image.open(ASSETS/f'textures/entity/{identifier}.png').convert('RGB')
        colors = ['#%02x%02x%02x' % tex.getpixel((j*8+2,2)) for j in range(8)]
        tile = render(decode_geometry(geo), anim, 0, colors, identifier.capitalize(),
                      f'{height:g} block body height / runtime idle', size=(500,370))
        sheet.paste(tile, ((i%3)*500,(i//3)*370))
    sheet.save(ROOT/'docs/creature-runtime-preview.png')

if __name__ == '__main__': main()
