"""Render the taming roster and rider-seat tables from the code and the generated audits.

Source of truth:
  * `CreatureProfileRegistry.java` for method, size band, target duration, food and resistance
  * `docs/taming-seat-manifest.json` for the per-creature seat and the model validation flags
  * `docs/taming-animation-matrix.json` for the torpor clip assets

Run after changing the profile table, the models or the import list. The generated markdown is what the
repository keeps; it is never edited by hand.
"""
from pathlib import Path
import json
import re
from datetime import date

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs'
REGISTRY = ROOT / 'src/main/java/dev/nez/arksurvivalreturns/feature/taming/CreatureProfileRegistry.java'
SPECIES = ROOT / 'src/main/java/dev/nez/arksurvivalreturns/feature/creature/Species.java'

TAG_LABELS = {
    'SMALL_PLANTS': 'berries, leaves',
    'PLANTS': 'berries, leaves, carrot, apple, wheat',
    'MEAT': 'raw land meat',
    'FISH': 'raw fish',
    'MEAT_AND_FISH': 'raw meat, raw fish',
    'PLANTS_AND_MEAT': 'plants, raw meat',
}
BERRY_LABELS = {'amarberry': 'Amarberry', 'tintoberry': 'Tintoberry', 'azulberry': 'Azulberry'}
ITEM_LABELS = {'COD': 'Raw Cod', 'SALMON': 'Raw Salmon', 'BEEF': 'Raw Beef', 'PORKCHOP': 'Raw Porkchop',
               'CHICKEN': 'Raw Chicken', 'RABBIT': 'Raw Rabbit', 'MUTTON': 'Raw Mutton',
               'CARROT': 'Carrot', 'APPLE': 'Apple', 'WHEAT': 'Wheat'}
# Torpor ceilings are configuration defaults, mirrored here for the table only.
MAX_TORPOR = {'SMALL': 60, 'MEDIUM': 150, 'LARGE': 350, 'GIANT': 700}
# Torpor recovery defaults, mirrored from Config: waking happens at 20 % of the maximum, the meter drains
# 0.5 % of the maximum per second, and the drain only starts ten seconds after the last dose.
WAKE_THRESHOLD_RATIO = 0.20
TORPOR_RECOVERY_PER_SECOND = 0.005
TORPOR_RECOVERY_DELAY_SECONDS = 10
FEED_INTERVAL_SECONDS = 20


def species_table():
    """Registry id, display name and already size-multiplied hitbox from Species.java."""
    text = SPECIES.read_text(encoding='utf-8')
    sizes = {'titanosaur': 6}
    for name in ('giganotosaurus', 'tyrannosaurus', 'spinosaurus', 'acrocanthosaurus'):
        sizes[name] = 3
    for name in ('cnidaria', 'plesiosaur', 'megalodon', 'liopleurodon', 'mosasaurus', 'tusoteuthis',
                 'kaprosuchus', 'sarco', 'deinosuchus', 'titanoboa', 'megalocerus', 'unicorn', 'mammoth',
                 'direwolf', 'sabertooth', 'megapithecus', 'paraceratherium', 'terrorbird', 'ravager',
                 'archaeopteryx', 'quetzal', 'dragon'):
        sizes[name] = 1
    rows = {}
    entry = re.compile(r'^\s{4}([A-Z_]+)\("([a-z_]+)", "([^"]+)",\s*([\d.]+),\s*([\d.]+),\s*([\d.]+),'
                       r'\s*([\d.]+)f,\s*([\d.]+)f,', re.M)
    for match in entry.finditer(text):
        enum, ident, display, _hp, _dmg, _speed, width, height = match.groups()
        factor = sizes.get(ident, 2)
        rows[enum] = {'id': ident, 'display': display, 'enum': enum,
                      'width': round(float(width) * factor, 3), 'height': round(float(height) * factor, 3)}
    return rows


def profiles():
    """One entry per `add(...)` call in the registry, keyed by the Species constant name."""
    text = REGISTRY.read_text(encoding='utf-8')
    pattern = re.compile(
        r'add\(Species\.([A-Z_]+),\s*TamingMethod\.([A-Z]+),\s*(\d+),\s*([A-Z_]+),\s*'
        r'(?:berry\("([a-z]+)"\)|\(\)\s*->\s*Items\.([A-Z_]+)),\s*([\d.]+)\);')
    found = {}
    for match in pattern.finditer(text):
        enum, method, seconds, food, berry, item, resistance = match.groups()
        favourite = BERRY_LABELS[berry] if berry else ITEM_LABELS[item]
        found[enum] = {'method': method, 'seconds': int(seconds), 'food': TAG_LABELS[food],
                       'favourite': favourite, 'resistance': float(resistance)}
    return found


def realm_source():
    """Realm per species, so the table can state where each method came from."""
    text = SPECIES.read_text(encoding='utf-8')
    realms = {}
    for match in re.finditer(r'^\s{4}([A-Z_]+)\(', text, re.M):
        realms[match.group(1)] = None
    for name in ('PTERANODON', 'ARGENTAVIS', 'ARCHAEOPTERYX', 'QUETZAL', 'DRAGON'):
        realms[name] = 'AIR'
    for name in ('CNIDARIA', 'PLESIOSAUR', 'MEGALODON', 'LIOPLEURODON', 'MOSASAURUS', 'TUSOTEUTHIS'):
        realms[name] = 'WATER'
    for name in ('KAPROSUCHUS', 'SARCO', 'DEINOSUCHUS', 'TITANOBOA'):
        realms[name] = 'AMPHIBIOUS'
    return {name: (realm or 'LAND') for name, realm in realms.items()}


def size_band(height):
    if height <= 1.6:
        return 'SMALL'
    if height <= 3.5:
        return 'MEDIUM'
    if height <= 8.0:
        return 'LARGE'
    return 'GIANT'


def main():
    species = species_table()
    table = profiles()
    realms = realm_source()
    manifest = json.loads((DOCS / 'taming-seat-manifest.json').read_text())
    seats = {row['id']: row for row in manifest['species']}
    outside_mesh = [row['id'] for row in manifest['species'] if not row['seat_inside_mesh']]
    ungrounded = manifest['models_not_normalized_to_the_entity_origin']
    clamped = manifest['seats_clamped_into_the_hitbox']
    matrix = json.loads((DOCS / 'taming-animation-matrix.json').read_text())
    torpor = {key: value for key, value in json.loads(
        (DOCS / 'taming-animation-matrix.json').read_text())['source_torpor_clips'].items()}

    missing = [name for name in species if name not in table]
    if missing:
        raise SystemExit('Species without a parsed profile: ' + ', '.join(missing))
    if len(table) != len(species):
        raise SystemExit(f'{len(table)} profiles for {len(species)} species')

    lines = [
        '# Taming roster and rider seats',
        '',
        f'Generated by `tools/build_taming_roster.py` on {date.today().isoformat()} from '
        '`CreatureProfileRegistry`, `docs/taming-seat-manifest.json` and `docs/taming-animation-matrix.json`. '
        'Do not edit by hand: change the profile table or the models and rerun the tool.',
        '',
        '## How to read this table',
        '',
        'Method and duration come from the taming table in the code, size bands are derived from the '
        'registered hitbox height, and the torpor ceiling is the configured default for that band. '
        'Accepted food is a data tag, so a data pack can retune every diet without touching code. '
        'Narcoberry is a sedative and is never accepted as taming food.',
        '',
        'The rider column names the model bone the seat was measured from and whether the model mesh is '
        'normalised to the entity origin. `tools/build_taming_manifests.py` checks each seat against the '
        'XZ bounds of that creature\'s own model mesh and records the result as `seat_inside_mesh` in '
        '`docs/taming-seat-manifest.json` '
        f'({len(outside_mesh)} of {len(seats)} seats fall outside their own mesh); the `taming_roster` '
        'and `taming_riding` game tests additionally assert only that the seat height is above the '
        'entity\'s feet and no higher than the registered height. Neither check proves hitbox containment '
        f'for the {len(ungrounded)} meshes that are not normalised to the entity origin, so whether the '
        'rider sits exactly on the visible back remains a manual in-game check; those species are listed '
        'at the end of this document.',
        '',
        '| Registry ID | Class | Group/size | Taming method | Accepted food tags | Preferred food | '
        'Torpor maximum | Target duration | Ride profile | Animation controller (source torpor clips) |',
        '|---|---|---|---|---|---|---:|---:|---|---|',
    ]
    for name, row in species.items():
        profile = table[name]
        band = size_band(row['height'])
        seat = seats[row['id']]
        clips = torpor.get(row['id'], {})
        clip_names = ', '.join(sorted(value['source'] for value in clips.values())) or 'none (fallback pose)'
        lines.append('| `{id}` | `{enum}` | {group} ({height} blocks) | {method} | {food} | {favourite} | '
                     '{torpor} | {seconds} s | seat `{bone}` ({mode}) | {clips} |'.format(
                         id=row['id'], enum=row['enum'], group=band.title() + ' ' + realms[name].title(),
                         height=row['height'], method=profile['method'].title(), food=profile['food'],
                         favourite=profile['favourite'], torpor=MAX_TORPOR[band],
                         seconds=profile['seconds'], bone=seat['bone'], mode=seat['body_plan'],
                         clips=clip_names))

    lines += [
        '',
        'Villagers and players are deliberately absent: villagers are not tameable, and players can be '
        'sedated but never tamed or ridden.',
        '',
        '## Per-meal progress',
        '',
        'Progress per meal is derived, not configured twice: a tame needs '
        f'`ceil(targetDuration / {FEED_INTERVAL_SECONDS} s)` meals, so one baseline meal is worth '
        '`100 / meals` percent and a favourite meal is worth the configured multiplier more. The actual '
        'duration of a tame equals the number of meals times the feeding interval, because a creature '
        'recovers appetite at one point per second and one meal costs twenty.',
        '',
        '| Band | Meals at 60 s | Meals at 90 s | Meals at 120 s | Meals at 150 s | Meals at 210 s | '
        'Meals at 240 s | Meals at 360 s | Torpor ceiling | Full bar lasts |',
        '|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|',
    ]
    waking_share = 1.0 - WAKE_THRESHOLD_RATIO
    drain_seconds = waking_share / TORPOR_RECOVERY_PER_SECOND
    full_bar_seconds = drain_seconds + TORPOR_RECOVERY_DELAY_SECONDS
    for band, ceiling in MAX_TORPOR.items():
        meals = [f'{max(1, -(-seconds // FEED_INTERVAL_SECONDS))}' for seconds in
                 (60, 90, 120, 150, 210, 240, 360)]
        lines.append(f'| {band.title()} | ' + ' | '.join(meals)
                     + f' | {ceiling} | {full_bar_seconds:.0f} s |')
    lines += [
        '',
        f'A full torpor bar keeps a creature unconscious for about {drain_seconds:.0f} seconds after the '
        f'ten second recovery delay, about {full_bar_seconds:.0f} seconds from the last dose: waking '
        'happens below `wakeThresholdRatio` '
        f'({WAKE_THRESHOLD_RATIO * 100:g} % of the maximum), and the default `torporRecoveryPerSecond` of '
        f'{TORPOR_RECOVERY_PER_SECOND:g} drains {TORPOR_RECOVERY_PER_SECOND * 100:g} % of the maximum per '
        f'second, so the waking {waking_share * 100:g} % of the bar takes '
        f'`{waking_share:g} / {TORPOR_RECOVERY_PER_SECOND:g} = {drain_seconds:.0f}` seconds. Longer tames '
        'therefore need the claimant to top the creature up with narcoberries, which is the intended '
        'maintenance loop and the reason the taming inventory exists.',
        '',
        '## Rider seat manifest',
        '',
        'Coordinates are entity-local blocks: `x` lateral, `y` above the entity feet, `z` along the body '
        'with the vanilla forward direction at negative `z`. They were measured from each runtime GeckoLib '
        'model: the chosen back bone\'s pivot, rotated by the same 180 degrees the renderer applies, with '
        '`y` lifted to the top face of that bone\'s own cubes. `tools/build_taming_manifests.py` recomputes '
        'them from the assets.',
        '',
        '| Registry ID | Model bone reference | Local X | Local Y | Local Z | Yaw offset | Rider pose | '
        'Ground dismount | Alternate dismount | Validated |',
        '|---|---|---:|---:|---:|---:|---|---|---|---|',
    ]
    for name, row in species.items():
        seat = seats[row['id']]
        validated = 'yes' if seat['seat_validated'] and seat['model_normalized'] else (
            'seat only' if seat['seat_validated'] else 'clamped')
        lines.append('| `{id}` | `{bone}` | {x} | {y} | {z} | {yaw} | {pose} | ({gx}, {gy}, {gz}) | '
                     '({ax}, {ay}, {az}) | {validated} |'.format(
                         id=row['id'], bone=seat['bone'], x=seat['local_x'], y=seat['recommended_local_y'],
                         z=seat['local_z'], yaw=seat['yaw_offset'], pose=seat['rider_pose'],
                         gx=seat['ground_dismount']['x'], gy=seat['ground_dismount']['y'],
                         gz=seat['ground_dismount']['z'], ax=seat['alt_dismount']['x'],
                         ay=seat['alt_dismount']['y'], az=seat['alt_dismount']['z'], validated=validated))

    lines += [
        '',
        '### Known model defects that affect seating',
        '',
        f'{len(ungrounded)} of {len(species)} meshes are not normalised to their entity origin: the lowest '
        'cube of the runtime mesh does not sit at the entity feet, or the mesh is not scaled to the '
        'registered height. The seat for those creatures is still derived from the real bone, but its '
        'height is clamped to the registered hitbox height (between 0.4 and `height - 0.2` blocks) so the '
        'rider cannot float above or below the body; the lateral offsets are taken straight from the bone '
        'and are not clamped. This is a pre-existing asset pipeline limitation, not a taming rule: '
        '`tools/import_creatures.py` normalises from the declared source `visible_bounds`, which several '
        'source projects state inaccurately. Fixing it would change the visuals of every affected creature '
        'and is out of scope for this patch.',
        '',
        '- Not normalised: ' + ', '.join(f'`{name}`' for name in ungrounded),
        '',
        f'- Seat height clamped instead of using the measured mesh surface ({len(clamped)}): '
        + ', '.join(f'`{name}`' for name in clamped),
        '',
        '### Torpor assets',
        '',
        'Torpor sequences were imported from the source projects where they exist. Species without a '
        'source sequence fall back to their authored standing sleep pose, and the animation bridge reports '
        'which clip it selected so a missing asset can never silently look like a broken controller.',
        '',
        '- Imported torpor clips: ' + ', '.join(
            f'`{value}`' for value in sorted(set(
                clip['source'] for entry in torpor.values() for clip in entry.values()))),
        '',
        '- No source torpor sequence: ' + ', '.join(
            f'`{name}`' for name in matrix['species_without_torpor_assets']),
        '',
    ]
    (DOCS / 'taming-roster.md').write_text('\n'.join(lines), encoding='utf-8')
    print(f'Wrote docs/taming-roster.md for {len(species)} creatures '
          f'({len(ungrounded)} meshes flagged, {len(matrix["species_without_torpor_assets"])} species '
          f'without torpor assets).')


if __name__ == '__main__':
    main()
