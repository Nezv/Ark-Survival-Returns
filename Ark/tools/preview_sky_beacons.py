"""Render a geometry preview from the exact native beacon builder; not an in-game screenshot."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from build_sky_beacons import blocks, ROOT, SIZE, VARIANTS, C
COLORS = {'stone': (125, 125, 125), 'andesite': (136, 136, 137), 'cobblestone': (118, 117, 117), 'tuff': (108, 109, 102),
    'stone_bricks': (122, 121, 122), 'cracked_stone_bricks': (112, 111, 112), 'chiseled_stone_bricks': (140, 139, 140),
    'mossy_stone_bricks': (108, 121, 98), 'mossy_cobblestone': (104, 120, 92), 'moss_block': (89, 109, 45),
    'granite': (149, 103, 85), 'polished_granite': (154, 106, 89), 'diorite': (188, 188, 188),
    'polished_diorite': (192, 193, 194), 'calcite': (223, 224, 220), 'cobbled_deepslate': (77, 77, 80),
    'polished_deepslate': (72, 72, 73), 'deepslate_bricks': (70, 70, 71), 'coarse_dirt': (119, 85, 59),
    'rooted_dirt': (144, 103, 76), 'bone_block': (209, 206, 179), 'sea_lantern': (211, 237, 226), 'glowstone': (240, 200, 110),
    'red_stained_glass': (229, 86, 94), 'light_blue_stained_glass': (143, 218, 238), 'purple_stained_glass': (156, 112, 223),
    'azalea_leaves': (90, 115, 44), 'flowering_azalea_leaves': (118, 122, 78), 'storage_crate': (150, 106, 60)}
PLANTS = {'vine': (58, 96, 38), 'moss_carpet': (89, 109, 45), 'short_grass': (110, 150, 70), 'fern': (96, 140, 66),
    'dragon_nest': (176, 140, 78)}
def render():
    im = Image.new('RGB', (1560, 1020), (18, 28, 40)); d = ImageDraw.Draw(im)
    font = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', 22)
    title = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', 34)
    d.text((55, 32), 'Beacon monoliths', font=title, fill=(235, 241, 246))
    d.text((55, 80), 'Native block geometry • %d × %d × %d • one shape rule, three seeds and local stones' % SIZE, font=font, fill=(170, 189, 204))
    for i, v in enumerate(VARIANTS):
        names = {p: b.split(':')[1].split('[')[0] for p, b in blocks(v).items()}
        shape = {p: n for p, n in names.items() if n in COLORS}
        ox, oy = 265 + i * 510, 915
        def proj(x, y, z): return (ox + (x - C) * 7.4 + (z - C) * 2.9, oy - y * 7.9 + (z - C) * 1.5 - (x - C) * .75)
        for (x, y, z), b in sorted(names.items(), key=lambda kv: kv[0][0] * .4 - kv[0][2] * .9 + kv[0][1] * .3):
            if b in PLANTS:
                # Thin plants: a vine is a sheet on the wall, the rest lie on the ledge.
                flat = [(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)] if b == 'vine' else [(0, .2, 0), (1, .2, 0), (1, .2, 1), (0, .2, 1)]
                d.polygon([proj(x + a, y + e, z + c) for a, e, c in flat], fill=PLANTS[b])
                continue
            for adj, face, light in [
                ((1, 0, 0), [(1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)], .78),
                ((0, 0, -1), [(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)], 1),
                ((0, 1, 0), [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)], 1.16)]:
                if (x + adj[0], y + adj[1], z + adj[2]) in shape: continue
                d.polygon([proj(x + a, y + e, z + c) for a, e, c in face], fill=tuple(min(255, int(c * light)) for c in COLORS[b]))
        d.text((ox - 35, 938), v.upper(), font=font, fill=(226, 234, 241))
    d.text((55, 981), 'Geometry preview; textures, the dragon and lighting need an in-game check.', font=font, fill=(155, 176, 194))
    path = ROOT / 'docs/sky-beacons.png'; im.save(path); print(path)
if __name__ == '__main__': render()
