"""Create the small empty structure required by the headless GameTests (NBT, no third party library)."""
from pathlib import Path
import gzip
import struct

def string(value):
    value = value.encode('utf-8')
    return struct.pack('>H', len(value)) + value

def named(tag, name, payload):
    return bytes([tag]) + string(name) + payload

def write_structure(name, width):
    root = Path(__file__).resolve().parents[1]
    size = bytes([3]) + struct.pack('>iiii', 3, width, 12, width)
    empty = bytes([10]) + struct.pack('>i', 0)
    palette = bytes([10]) + struct.pack('>i', 1) + named(8, 'Name', string('minecraft:air')) + b'\0'
    nbt = b'\x0a\x00\x00' + named(9, 'size', size) + named(9, 'entities', empty) + named(9, 'blocks', empty) + named(9, 'palette', palette) + b'\0'
    path = root / f'src/main/resources/data/arksurvivalreturns/structure/{name}.nbt'
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(nbt, mtime=0))
    print(f'Created {width} x 12 x {width} empty GameTest structure.')

def main():
    write_structure('test_empty', 16)
    write_structure('test_population', 128)

if __name__ == '__main__': main()
