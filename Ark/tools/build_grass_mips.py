"""Give the block atlas its four mip levels back (A01 OPT-02).

Grassier Grass ships two block sprites too small for mipmaps: `blade` (2x2) and `grass_bands` (8x4). Minecraft drops
the whole block atlas to the level the smallest sprite allows, so every block in the game is drawn with one mip level
instead of four. This writes both sprites scaled up without smoothing to a multiple of 16 on each side, into the
built-in Ark UI pack inside the built jar. With nearest filtering a sprite scaled this way samples the same everywhere.

The closed mod's art is read from its pinned jar at build time and never committed. When the jar is not present
(a build without the client mods), nothing is written and the atlas behaves as before.

    python tools/build_grass_mips.py <output dir>
"""
import io
import json
import sys
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT / 'config/client-mods.lock.json'
SPRITES = ['blade', 'grass_bands']
PACK_PATH = 'resources/resourcepacks/ark_ui/assets/grassiergrass/textures/block'


def grassier_jar():
    lock = json.loads(LOCK.read_text(encoding='utf-8'))
    for entry in lock.get('entries', []):
        name = entry.get('filename', '')
        if name.startswith('grassiergrass') and (ROOT / 'client-mods' / name).is_file():
            return ROOT / 'client-mods' / name
    return None


def scaled(image):
    """Nearest-neighbour scale so both sides are multiples of 16."""
    factor = 1
    while (image.width * factor) % 16 or (image.height * factor) % 16:
        factor *= 2
    return image.resize((image.width * factor, image.height * factor), Image.NEAREST)


def main(output):
    out = Path(output) / PACK_PATH
    jar = grassier_jar()
    if jar is None:
        print('Grassier Grass jar not found; the atlas mip fix is skipped.')
        return
    out.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(jar) as archive:
        for name in SPRITES:
            image = Image.open(io.BytesIO(archive.read(f'assets/grassiergrass/textures/block/{name}.png'))).convert('RGBA')
            big = scaled(image)
            big.save(out / f'{name}.png')
            print(f'{name}: {image.size} -> {big.size}')


if __name__ == '__main__':
    main(sys.argv[1])
