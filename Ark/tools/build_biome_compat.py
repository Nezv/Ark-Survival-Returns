"""Map every Terralith biome to its closest vanilla analog, so Ark spawns and difficulty cover Terralith worlds.

Similarity = weighted Jaccard over biome tags (vanilla, NeoForge c: and the tags Terralith adds) minus a
climate distance (temperature, downfall, precipitation). Climate-defining tags weigh more than structure
tags. The result is committed (config/integrations/terralith-biomes.json); ArkData turns it into optional
tag entries, which are ignored when Terralith is not installed.

Run from Ark: python tools/build_biome_compat.py
"""
import glob
import io
import json
import zipfile
from collections import defaultdict
from pathlib import Path

ARK = Path(__file__).resolve().parents[1]
MC = ARK / 'build/moddev/artifacts/minecraft-patched-26.1.2.109.jar'
NEO = next(Path.home().glob('.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/26.1.2.109/*/neoforge-26.1.2.109-universal.jar'), None)
TERRALITH = next(iter(glob.glob(str(ARK / 'shared-mods/Terralith_*.jar'))), None)
OUT = ARK / 'config/integrations/terralith-biomes.json'
KEY_TAGS = {'is_snowy', 'is_cold', 'is_icy', 'is_hot', 'is_dry', 'is_wet', 'is_temperate', 'is_jungle', 'is_savanna',
            'is_badlands', 'is_desert', 'is_forest', 'is_taiga', 'is_mountain', 'is_peak', 'is_slope', 'is_ocean',
            'is_deep_ocean', 'is_beach', 'is_stony_shores', 'is_river', 'is_swamp', 'is_plains', 'is_cave',
            'is_underground', 'is_mushroom', 'is_birch_forest', 'is_flower_forest', 'is_old_growth', 'is_windswept',
            'is_dense_vegetation', 'is_sparse_vegetation', 'is_lush', 'is_hill', 'is_plateau', 'is_floral'}
SKIP_TAGS = {'is_overworld', 'overworld'}
# Name keywords narrow the candidates before scoring; first match wins.
KEYWORDS = [
    (('cave', 'caves', 'underground', 'mantle', 'crystal'), ('dripstone_caves', 'lush_caves')),
    (('snowy_maple', 'wintry', 'siberian', 'snowy_cherry', 'snowy_shield', 'snowy_forest'), ('snowy_taiga', 'grove')),
    (('frozen', 'glacial', 'ice', 'snowy', 'winter', 'frost', 'tundra', 'polar', 'arctic'),
     ('snowy_plains', 'ice_spikes', 'snowy_taiga', 'grove', 'snowy_slopes', 'frozen_peaks', 'snowy_beach')),
    (('cold',), ('taiga', 'snowy_plains', 'windswept_hills')),
    (('desert', 'sands', 'dunes', 'oasis', 'sandstone'), ('desert',)),
    (('badlands', 'mesa', 'canyon', 'painted', 'red_rock'), ('badlands', 'wooded_badlands', 'eroded_badlands')),
    (('jungle', 'rainforest', 'tropical'), ('jungle', 'sparse_jungle', 'bamboo_jungle')),
    (('beach', 'shore', 'coast', 'cliffs', 'gravel'), ('beach', 'stony_shore')),
    (('savanna', 'steppe', 'shrubland', 'scrub', 'ashen', 'volcanic'), ('savanna', 'savanna_plateau', 'windswept_savanna')),
    (('swamp', 'marsh', 'bog', 'fen', 'mire', 'wetland', 'bayou', 'mangrove'), ('swamp', 'mangrove_swamp')),
    (('peaks', 'mountain', 'alps', 'summit', 'crags', 'highlands', 'emerald', 'yosemite', 'rocky'),
     ('windswept_hills', 'stony_peaks', 'jagged_peaks', 'windswept_gravelly_hills', 'meadow')),
    (('taiga', 'pine', 'spruce', 'conifer', 'redwood'), ('taiga', 'old_growth_pine_taiga', 'old_growth_spruce_taiga')),
    (('birch',), ('birch_forest', 'old_growth_birch_forest')),
    (('grove', 'forest', 'woods', 'orchard', 'wood', 'thicket', 'cherry', 'sakura', 'temperate'),
     ('forest', 'flower_forest', 'dark_forest', 'cherry_grove', 'birch_forest')),
    (('meadow', 'valley', 'garden', 'blooming', 'lavender', 'moonlight', 'plateau'), ('meadow', 'flower_forest', 'plains')),
    (('plains', 'prairie', 'field', 'islands', 'isles', 'skylands', 'hills', 'clearing'), ('plains', 'sunflower_plains', 'meadow')),
    (('ocean', 'sea', 'reef', 'lagoon'), ('ocean', 'warm_ocean', 'lukewarm_ocean')),
    (('river', 'lake', 'stream'), ('river',)),
    (('mushroom', 'fungal'), ('mushroom_fields',)),
]


def read_tags(jars):
    """tag id -> set of raw values ('#ns:tag' or 'ns:biome'), merged across jars."""
    raw = defaultdict(set)
    for jar in jars:
        with zipfile.ZipFile(jar) as z:
            for name in z.namelist():
                parts = name.split('/')
                if len(parts) > 4 and parts[0] == 'data' and parts[2:5] == ['tags', 'worldgen', 'biome'] and name.endswith('.json'):
                    tag = parts[1] + ':' + '/'.join(parts[5:])[:-5]
                    for value in json.loads(z.read(name)).get('values', []):
                        raw[tag].add(value['id'] if isinstance(value, dict) else value)
    return raw


def resolve(raw):
    memo = {}

    def members(tag, seen=()):
        if tag in memo:
            return memo[tag]
        out = set()
        for value in raw.get(tag, ()):
            if value.startswith('#'):
                if value[1:] not in seen:
                    out |= members(value[1:], seen + (tag,))
            else:
                out.add(value)
        memo[tag] = out
        return out
    per_biome = defaultdict(set)
    for tag in raw:
        for biome in members(tag):
            per_biome[biome].add(tag)
    return per_biome


def climate(jar, namespace):
    out = {}
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            prefix = f'data/{namespace}/worldgen/biome/'
            if name.startswith(prefix) and name.endswith('.json'):
                data = json.loads(z.read(name))
                out[f'{namespace}:{name[len(prefix):-5]}'] = (float(data.get('temperature', 0.5)), float(data.get('downfall', 0.5)),
                                                             bool(data.get('has_precipitation', True)))
    return out


def weight(tag):
    leaf = tag.split(':')[1].split('/')[-1]
    if leaf in SKIP_TAGS:
        return 0.0
    if leaf in KEY_TAGS:
        return 3.0
    return 0.5 if 'has_structure' in tag else 1.0


def similarity(a, b):
    union = a | b
    if not union:
        return 0.0
    return sum(weight(t) for t in a & b) / max(1e-9, sum(weight(t) for t in union))


def main():
    if not (MC.is_file() and NEO and TERRALITH):
        raise SystemExit('Needs the patched Minecraft jar (run a Gradle build), the NeoForge universal jar and Terralith in shared-mods.')
    tags = resolve(read_tags([MC, NEO, TERRALITH]))
    vanilla_climate = climate(MC, 'minecraft')
    terralith_climate = climate(TERRALITH, 'terralith')
    overworld = {b for b, t in tags.items() if 'minecraft:is_overworld' in t and b.startswith('minecraft:')}
    overworld |= {'minecraft:dripstone_caves', 'minecraft:lush_caves'}
    mapping = {}
    for biome, (temp, rain, wet) in sorted(terralith_climate.items()):
        leaf = biome.split(':')[1].split('/')[-1]
        pool = overworld
        for words, analogs in KEYWORDS:
            if any(w in leaf.split('_') or w in leaf for w in words):
                pool = {'minecraft:' + a for a in analogs}
                break

        def pick(candidates):
            best = None
            for candidate in candidates:
                if candidate not in vanilla_climate:
                    continue
                vt, vr, vw = vanilla_climate[candidate]
                score = similarity(tags.get(biome, set()), tags.get(candidate, set()))                     - 0.35 * abs(temp - vt) - 0.2 * abs(rain - vr) - (0.1 if wet != vw else 0)
                if best is None or score > best[0]:
                    best = (score, candidate)
            return best
        best = pick(pool)
        free = pick(overworld)
        # A keyword is a hint, not a verdict: a far better tag and climate match wins.
        if best is None or (free and free[0] - best[0] > 0.6):
            best = free
        mapping[biome] = {'analog': best[1], 'score': round(best[0], 3)}
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({'about': 'Generated by tools/build_biome_compat.py; the closest vanilla biome for each Terralith biome.',
                               'terralith': mapping}, indent=2) + '\n', encoding='utf-8')
    print(f'Mapped {len(mapping)} Terralith biomes -> {OUT.relative_to(ARK)}')
    for biome, entry in mapping.items():
        print(f'  {biome.split(":")[1]:34} -> {entry["analog"].split(":")[1]:24} {entry["score"]}')


if __name__ == '__main__':
    main()
