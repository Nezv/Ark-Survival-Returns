"""Build the beacon monoliths: weathered stone pillars of stacked, tilted blocks hung with vines,
each with a dragon nest and a loot crate in its eye. The templates hold no dragon, only a marker on the nest
that stands for it: it comes when a player first climbs into the eye (SkyBeaconStructure.watch).
Run from anywhere; no third-party assets or source structures are copied.
"""
from pathlib import Path
import gzip
import json
import math
import random
import struct

ROOT = Path(__file__).resolve().parents[1]
NS = 'arksurvivalreturns'
DATA = ROOT / 'src/main/resources/data' / NS
# Minecraft 26.1.2. A template without it is read as 1.9 data and run through every data fixer since.
DATA_VERSION = 4790

# Keep in step with SkyBeaconStructure (SIZE, NEST, CRATE, MARK).
SIZE = (33, 100, 33)
C = 16                  # the pillar's vertical axis, in x and z
BASE = 4                # the tip; the rows below it only hold trailing vines
EYE = (C, 66)           # centre of the round opening (x, y)
EYE_R = 6.2
NEST = (C, 63, C)       # on the opening's floor; the dragon's home
CRATE = (12, 63, 15)
EMBLEM_Y = 83
LOOT = NS + ':chests/sky_beacon'
MARK = NS + '.sky_beacon'

# One dragon colour per variant: the emblem glass, and the local stone worked into the grey.
VARIANTS = {
    'red': {'seed': 7101, 'glass': 'red_stained_glass', 'lamp': 'glowstone',
            'accent': ('granite', 'granite', 'polished_granite'), 'trim': 'polished_granite'},
    'white': {'seed': 7202, 'glass': 'light_blue_stained_glass', 'lamp': 'sea_lantern',
              'accent': ('diorite', 'calcite', 'polished_diorite'), 'trim': 'polished_diorite'},
    'black': {'seed': 7303, 'glass': 'purple_stained_glass', 'lamp': 'sea_lantern',
              'accent': ('cobbled_deepslate', 'cobbled_deepslate', 'polished_deepslate'), 'trim': 'deepslate_bricks'},
}
FAMILIES = ('bricks', 'bricks', 'smooth', 'smooth', 'rough', 'accent')
FACE = {
    'bricks': ('stone_bricks', 'stone_bricks', 'stone_bricks', 'cracked_stone_bricks'),
    'smooth': ('stone', 'stone', 'stone', 'andesite', 'andesite', 'cobblestone'),
    'rough': ('cobblestone', 'cobblestone', 'cobblestone', 'andesite', 'tuff', 'tuff', 'stone'),
}
FRAME = {'bricks': 'stone_bricks', 'smooth': 'stone_bricks', 'rough': 'cobblestone'}
MOSSY = {'stone_bricks': 'mossy_stone_bricks', 'cracked_stone_bricks': 'mossy_stone_bricks',
         'cobblestone': 'mossy_cobblestone', 'stone': 'mossy_cobblestone'}
SIDES = {'east': (1, 0), 'west': (-1, 0), 'south': (0, 1), 'north': (0, -1)}


# Minimal big-endian NBT writer for native structure templates.
def string(s):
    b = s.encode(); return struct.pack('>H', len(b)) + b
def payload(kind, v):
    if kind == 1: return struct.pack('>b', v)
    if kind == 3: return struct.pack('>i', v)
    if kind == 5: return struct.pack('>f', v)
    if kind == 6: return struct.pack('>d', v)
    if kind == 8: return string(v)
    if kind == 9:
        t, items = v
        return bytes([t]) + struct.pack('>i', len(items)) + b''.join(payload(t, i) for i in items)
    if kind == 10:
        return b''.join(bytes([t]) + string(k) + payload(t, x) for k, (t, x) in v.items()) + b'\0'
    raise ValueError(kind)
def ints(v): return (9, (3, list(v)))


def noise(x, y, z, scale, seed):
    """Smooth value noise in 0..1, so moss and vines gather in patches instead of speckling."""
    def corner(i, j, k):
        n = (i * 73856093 ^ j * 19349663 ^ k * 83492791 ^ seed * 2654435761) & 0xffffffff
        n = (n ^ n >> 15) * 0x2c1b3c6d & 0xffffffff
        n = (n ^ n >> 12) * 0x297a2d39 & 0xffffffff
        return (n ^ n >> 15) / 0xffffffff
    x, y, z = x / scale, y / scale, z / scale
    i, j, k = math.floor(x), math.floor(y), math.floor(z)
    u, v, w = (t * t * (3 - 2 * t) for t in (x - i, y - j, z - k))
    def lerp(a, b, t): return a + (b - a) * t
    return lerp(lerp(lerp(corner(i, j, k), corner(i + 1, j, k), u), lerp(corner(i, j + 1, k), corner(i + 1, j + 1, k), u), v),
                lerp(lerp(corner(i, j, k + 1), corner(i + 1, j, k + 1), u),
                     lerp(corner(i, j + 1, k + 1), corner(i + 1, j + 1, k + 1), u), v), w)


def plates(rng):
    """The pillar as a list of tilted plates and cubes: (kind, centre, half sizes, roll, yaw, family, relief)."""
    out = []
    def add(kind, x, y, z, a, b, t, roll=45.0, yaw=0.0, family=None, relief=True):
        out.append((kind, x, y, z, a, b, t, math.radians(roll), math.radians(yaw),
                    family or rng.choice(FAMILIES), relief))
    # The tip: one long shard pointing at the ground.
    add('rhomb', C, BASE + 15, C, 4.6, 15, 2, roll=rng.uniform(-4, 4), family='smooth', relief=False)
    # The shaft: diamonds stepping left and right up to the neck, with cubes clinging to their corners.
    y, side = BASE + 13, rng.choice((-1, 1))
    while y < 51:
        s = min(8.5, 3.0 + (y - BASE) * 0.16) * rng.uniform(.9, 1.1)
        off = side * s * rng.uniform(.25, .55); side = -side
        add('box', C + off, y, C + rng.choice((-2, -1, 0, 1, 2)), s, s * rng.uniform(.9, 1.05),
            (2 if y < 20 else 3 if y < 32 else 4) + rng.choice((0, 0, 1)), roll=rng.choice((45, 45, 45, 45, 45, 30, 60, 0)))
        if rng.random() < .7:
            c = max(1.6, s * rng.uniform(.28, .42))
            add('box', C + off + side * -1 * s * rng.uniform(.9, 1.15), y + rng.uniform(-.5, .5) * s,
                C + rng.uniform(-2, 2), c, c, c + rng.uniform(0, 1.5), roll=rng.choice((0, 45, 45)),
                yaw=rng.choice((0, 45, 45)), relief=False)
        y += s * rng.uniform(1.25, 1.55)
    # The neck and the head: one large diamond holding the eye, with a cube on each shoulder.
    add('box', C + rng.uniform(-2, 2), 52, C, 7.5, 6.5, 4, roll=rng.choice((0, 45)))
    add('box', EYE[0], EYE[1], C, 10.5, 10.5, 5, family='bricks', relief=False)
    for side in (-1, 1):
        add('box', C + side * 11.4, EYE[1] + rng.uniform(-3, 3), C + rng.uniform(-1, 1), 2.6, 2.6, 3.4,
            roll=rng.choice((0, 45)), yaw=45, relief=False)
        add('box', C + side * rng.uniform(5.5, 7.5), EYE[1] + rng.choice((-1, 1)) * rng.uniform(7, 9), C, 3.2, 3.2, 5.6,
            roll=rng.choice((0, 45, 20)), relief=False)
    # The emblem plate and a broken, forked crown.
    add('box', C, EMBLEM_Y, C, 6.5, 6.5, 3, family='smooth', relief=False)
    add('rhomb', C - 3.6, 89, C, 2.7, 10.4, 2, roll=rng.uniform(3, 7), family='bricks', relief=False)
    add('rhomb', C + 3.9, 86.5, C, 2.7, 8.4, 2, roll=-rng.uniform(4, 8), family='rough', relief=False)
    add('box', C + rng.choice((-1, 1)) * 6.5, 79, C + rng.uniform(-1, 1), 2.2, 2.2, 3, roll=rng.choice((0, 45)), yaw=45, relief=False)
    return out


def blocks(variant):
    spec = VARIANTS[variant]
    rng = random.Random(spec['seed'])
    seed = spec['seed']
    solid = {}                                   # (x, y, z) -> block name, full cubes only
    for kind, cx, cy, cz, a, b, t, roll, yaw, family, relief in plates(rng):
        reach = math.ceil(max(a, b, t) * 1.5) + 1
        cr, sr, cw, sw = math.cos(roll), math.sin(roll), math.cos(yaw), math.sin(yaw)
        for x in range(max(0, int(cx) - reach), min(SIZE[0], int(cx) + reach + 2)):
            for y in range(max(BASE, int(cy) - reach), min(SIZE[1], int(cy) + reach + 2)):
                # The pillar narrows to its tip like an obelisk, whatever the plates want.
                if abs(x - C) > 1.0 + (y - BASE) * 0.36: continue
                for z in range(max(0, int(cz) - reach), min(SIZE[2], int(cz) + reach + 2)):
                    if abs(z - C) > 1.6 + (y - BASE) * 0.24 or (x, y, z) in solid: continue
                    dx, dy, dz = x - cx, y - cy, z - cz
                    x1, w = dx * cw + dz * sw, -dx * sw + dz * cw
                    u, v = x1 * cr + dy * sr, -x1 * sr + dy * cr
                    m = abs(u) / a + abs(v) / b if kind == 'rhomb' else max(abs(u) / a, abs(v) / b)
                    if m > 1 or abs(w) > t: continue
                    skin = abs(w) > t - 1
                    # Large plates carry a carved square inside a square, like the reference monoliths.
                    if relief and a >= 4.5 and skin and .52 <= m < .66: continue
                    pick = noise(x * 7.3, y * 7.3, z * 7.3, 1.0, seed)       # per-block choice, stable per variant
                    if family == 'accent':
                        name = spec['trim'] if m >= .82 else spec['accent'][int(pick * len(spec['accent']))]
                    elif m >= .82: name = FRAME[family]
                    elif relief and a >= 4.5 and skin and m < .16: name = 'chiseled_stone_bricks'
                    else: name = FACE[family][int(pick * len(FACE[family]))]
                    solid[(x, y, z)] = name
    # The eye: a round opening through the head, floored for the nest and rimmed with brick.
    for (x, y, z) in list(solid):
        r = math.hypot(x - EYE[0], y - EYE[1])
        if r < EYE_R and y >= NEST[1]: del solid[(x, y, z)]
        elif r < EYE_R + 1.3: solid[(x, y, z)] = 'stone_bricks'
    # A channel cut down the middle of the shaft, front and back, as on the obelisks.
    for y in range(BASE + 8, 47):
        column = [z for z in range(SIZE[2]) if (C, y, z) in solid]
        if len(column) >= 4:
            del solid[(C, y, column[0])]; del solid[(C, y, column[-1])]
    # Weathering: moss creeps up from the ground and settles on every ledge.
    def open_air(p): return p not in solid
    for (x, y, z), name in sorted(solid.items()):
        damp = max(.06, .55 - y / 130) * (0.3 + 1.3 * noise(x, y, z, 7.0, seed + 1))
        ledge = open_air((x, y + 1, z))
        if ledge and rng.random() < damp * 1.25 and name not in ('chiseled_stone_bricks',): solid[(x, y, z)] = 'moss_block'
        elif name in MOSSY and rng.random() < damp * (1.0 if name != 'stone' else .5): solid[(x, y, z)] = MOSSY[name]
    out = {p: 'minecraft:' + name for p, name in solid.items()}
    # The emblem: a glowing diamond on both faces of the plate above the eye.
    for dx in range(-4, 5):
        for dy in range(-4, 5):
            d = abs(dx) + abs(dy)
            if d != 4 and d > 1: continue
            for face, behind in ((C - 3, C - 2), (C + 3, C + 2)):
                out[(C + dx, EMBLEM_Y + dy, face)] = 'minecraft:' + spec['glass']
                out[(C + dx, EMBLEM_Y + dy, behind)] = 'minecraft:' + spec['lamp']
    # The nest floor: earth, moss and old bones, the egg in the middle and the crate against the wall.
    nx, ny, nz = NEST
    floor = set()
    for (x, y, z) in sorted(solid):
        if y != ny - 1 or (x, ny, z) in solid or math.hypot(x - EYE[0], ny - EYE[1]) >= EYE_R: continue
        floor.add((x, y, z))
        out[(x, y, z)] = 'minecraft:' + rng.choice(('moss_block', 'moss_block', 'coarse_dirt', 'mossy_cobblestone', 'rooted_dirt'))
        if max(abs(x - nx), abs(z - nz)) > 1 and (x, ny, z) != CRATE and rng.random() < .3:
            out[(x, ny, z)] = 'minecraft:moss_carpet'
    out[NEST] = NS + ':dragon_nest[egg=true]'
    out[CRATE] = NS + ':storage_crate'
    out[(nx + 4, ny, nz + 2)] = 'minecraft:bone_block[axis=x]'
    out[(nx + 3, ny, nz - 3)] = 'minecraft:bone_block[axis=z]'
    out[(nx - 3, ny, nz + 3)] = 'minecraft:bone_block[axis=y]'
    keep_clear = {(x, y, z) for x in range(nx - 2, nx + 3) for y in range(ny, ny + 7) for z in range(nz - 2, nz + 3)}
    keep_clear |= {(C + dx, EMBLEM_Y + dy, z) for dx in range(-5, 6) for dy in range(-5, 12) for z in (C - 4, C + 4)}
    # Growth on the ledges.
    for (x, y, z), name in sorted(solid.items()):
        above = (x, y + 1, z)
        if above in out or above in keep_clear or (x, y, z) in floor or y + 1 >= SIZE[1]: continue
        roll = rng.random()
        if name == 'moss_block':
            if roll < .34: out[above] = 'minecraft:moss_carpet'
            elif roll < .50: out[above] = 'minecraft:short_grass'
            elif roll < .56: out[above] = 'minecraft:fern'
            elif roll < .60: out[above] = 'minecraft:' + rng.choice(('azalea_leaves', 'flowering_azalea_leaves')) + '[persistent=true]'
        elif roll < .05: out[above] = 'minecraft:moss_carpet'
    # Vines: they start on a wall and trail down, longest near the ground and under the tip.
    full = set(solid)
    for (x, y, z) in sorted((x + ox, y, z + oz) for (x, y, z) in solid for ox, oz in SIDES.values()):
        p = (x, y, z)
        if p in out or p in keep_clear or not (0 <= x < SIZE[0] and 0 <= z < SIZE[2]): continue
        patch = noise(x, y, z, 9.0, seed + 2)
        if patch < .5 or rng.random() >= max(.02, .1 - y / 900) * patch: continue
        faces = {name for name, (ox, oz) in SIDES.items() if (x + ox, y, z + oz) in full}
        for depth in range(rng.randint(2, 7 + max(0, (48 - y) // 5))):
            q = (x, y - depth, z)
            if q[1] < 0 or q in out or q in keep_clear: break
            # A face holds on a wall beside it or on the same face of the vine above; that is all the game checks.
            held = {name for name, (ox, oz) in SIDES.items() if (x + ox, q[1], z + oz) in full} | faces
            props = ','.join(f'{name}={str(name in held).lower()}' for name in sorted(SIDES))
            out[q] = f'minecraft:vine[{props},up={str((x, q[1] + 1, z) in full).lower()}]'
            faces = held
    return out


def state(text):
    name, _, props = text.partition('[')
    tag = {'Name': (8, name)}
    if props: tag['Properties'] = (10, {k: (8, v) for k, v in (p.split('=') for p in props[:-1].split(','))})
    return tag


def build():
    dest = DATA / 'structure/sky_beacon'; dest.mkdir(parents=True, exist_ok=True)
    spot = [NEST[0] + .5, float(NEST[1]), NEST[2] + .5]
    for variant in VARIANTS:
        shape = blocks(variant)
        palette = list(dict.fromkeys(shape.values()))
        index = {n: k for k, n in enumerate(palette)}
        mark = {'id': (8, 'minecraft:marker'), 'Tags': (9, (8, [MARK, f'{MARK}.{variant}']))}
        crate = {'id': (8, NS + ':storage_crate'), 'LootTable': (8, LOOT),
                 'CustomName': (10, {'translate': (8, f'container.{NS}.loot_crate')})}
        placed = []
        for p, n in sorted(shape.items()):
            block = {'pos': ints(p), 'state': (3, index[n])}
            if p == CRATE: block['nbt'] = (10, crate)
            placed.append(block)
        root = {'DataVersion': (3, DATA_VERSION), 'size': ints(SIZE),
                'palette': (9, (10, [state(n) for n in palette])),
                'blocks': (9, (10, placed)),
                'entities': (9, (10, [{'pos': (9, (6, spot)), 'blockPos': ints(NEST), 'nbt': (10, mark)}]))}
        (dest / f'{variant}.nbt').write_bytes(gzip.compress(b'\x0a\0\0' + payload(10, root), mtime=0))
        print(variant, len(shape), 'blocks; one nest, one crate, one mark for the dragon')
    def write(rel, data):
        p = DATA / rel; p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(json.dumps(data, indent=2) + '\n')
    write('worldgen/structure/sky_beacon.json', {'type': NS + ':sky_beacon',
        'biomes': f'#{NS}:has_structure/sky_beacon',
        'step': 'surface_structures', 'spawn_overrides': {}, 'terrain_adaptation': 'none'})
    write('worldgen/structure_set/sky_beacons.json', {'structures': [{'structure': NS + ':sky_beacon', 'weight': 1}],
        'placement': {'type': 'minecraft:random_spread', 'spacing': 32, 'separation': 30, 'salt': 50312010}})
    write('tags/worldgen/biome/has_structure/sky_beacon.json', {'replace': False, 'values': ['#minecraft:is_overworld']})
    def entry(name, low, high, weight):
        return {'type': 'minecraft:item', 'name': name if ':' in name else f'{NS}:{name}', 'weight': weight,
                'functions': [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': low, 'max': high}}]}
    # What a tribe needs after the climb: medicine, taming supplies and the materials the Bronze Age starts with.
    write('loot_table/chests/sky_beacon.json', {'type': 'minecraft:chest', 'pools': [
        {'rolls': {'type': 'minecraft:uniform', 'min': 3, 'max': 5}, 'entries': [
            entry('tranquilizer_arrow', 4, 10, 4), entry('narcotics', 3, 6, 4), entry('healing_mixture', 1, 2, 3),
            entry('herbal_bandage', 1, 3, 3), entry('dried_ration', 2, 4, 3), entry('trail_mix', 2, 4, 2),
            entry('cooked_prime_meat', 1, 3, 2)]},
        {'rolls': {'type': 'minecraft:uniform', 'min': 2, 'max': 4}, 'entries': [
            entry('keratin', 3, 8, 4), entry('thick_pelt', 2, 4, 3), entry('fang', 2, 4, 3), entry('amber', 1, 3, 2),
            entry('raw_tin', 2, 5, 2), entry('minecraft:raw_copper', 2, 6, 2), entry('sulphur', 2, 4, 1),
            entry('wing_membrane', 1, 2, 1)]}]})


if __name__ == '__main__': build()
