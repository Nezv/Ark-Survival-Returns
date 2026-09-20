"""Derive the taming animation matrix and the rider seat manifest from the runtime assets.

Reads the registered roster from `Species.java`, the runtime GeckoLib animations and the runtime
GeckoLib models, and writes two JSON reports. Seat coordinates are computed from real bone pivots and
real cube extents; no coordinate is hand-written.

Model space -> entity-local space
---------------------------------
`tools/import_creatures.py` scales every bone and cube so that 16 model units equal one block and the
lowest mesh point sits at model Y = 0, which is the entity's feet. `CreatureRenderer.applyRotations`
then rotates the whole model 180 degrees about Y, so a model-space point (mx, my, mz) lands at the
entity-local offset (-mx/16, my/16, -mz/16) in blocks. Both directions are emitted so the renderer's
correction can be reviewed.
"""
from pathlib import Path
import json
import re
from datetime import date

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/arksurvivalreturns'
SOURCE = ROOT.parent / 'Creatures'
DOCS = ROOT / 'docs'

# Model-space forward axis per rig, measured from the head bone against the rear of the spine.
# "z" rigs already face the +Z the renderer expects; "x" rigs are built sideways and the renderer's
# single 180 degree correction leaves their visual heading 90 degrees off entity movement.
SEAT_YAW_BY_FORWARD = {'z': 0.0, 'x': -90.0, '-x': 90.0, 'none': 0.0}

# Body plan per registry id, used to pick the anatomical seat and the rider pose.
# quadruped | quadrupedHerbivore | sauropod | bipedCarnivore | bipedHerbivore | horned | armored |
# flyer | longSwimmer | broadSwimmer | radial
BODY_PLAN = {
    'pteranodon': 'flyer', 'velociraptor': 'bipedCarnivore', 'argentavis': 'flyer',
    'triceratops': 'horned', 'therizinosaurus': 'bipedHerbivore', 'brontosaurus': 'sauropod',
    'tyrannosaurus': 'bipedCarnivore', 'giganotosaurus': 'bipedCarnivore', 'titanosaur': 'sauropod',
    'spinosaurus': 'bipedCarnivore', 'parasaur': 'quadrupedHerbivore', 'ceratosaurus': 'bipedCarnivore',
    'dilophosaur': 'bipedCarnivore', 'acrocanthosaurus': 'bipedCarnivore', 'allosaurus': 'bipedCarnivore',
    'ankylosaurus': 'armored', 'carnotaurus': 'bipedCarnivore', 'pegomastax': 'quadrupedHerbivore',
    'lystrosaurus': 'quadrupedHerbivore', 'cnidaria': 'radial', 'plesiosaur': 'longSwimmer',
    'megalodon': 'broadSwimmer', 'liopleurodon': 'longSwimmer', 'mosasaurus': 'longSwimmer',
    'tusoteuthis': 'broadSwimmer', 'kaprosuchus': 'quadruped', 'sarco': 'quadruped',
    'deinosuchus': 'quadruped', 'titanoboa': 'longSwimmer', 'megalocerus': 'quadrupedHerbivore',
    'unicorn': 'quadrupedHerbivore', 'mammoth': 'quadrupedHerbivore', 'direwolf': 'quadruped',
    'sabertooth': 'quadruped', 'megapithecus': 'bipedHerbivore', 'paraceratherium': 'sauropod',
    'terrorbird': 'bipedCarnivore', 'ravager': 'quadruped', 'archaeopteryx': 'flyer',
    'quetzal': 'flyer', 'dragon': 'flyer',
}

# Rider pose and short anatomical target text per body plan (mirrors Taming_Patch.md section 5).
POSE = {
    'quadruped': ('STANDING', 'center of upper back'),
    'quadrupedHerbivore': ('STANDING', 'upper back behind the shoulder girdle'),
    'sauropod': ('STANDING', 'upper back at the neck base'),
    'bipedCarnivore': ('STANDING', 'back above the hips, slightly forward of the pelvis'),
    'bipedHerbivore': ('STANDING', 'back above the pelvis'),
    'horned': ('STANDING', 'back behind shoulders and frill'),
    'armored': ('STANDING', 'clear saddle pocket on the back'),
    'flyer': ('STANDING', 'dorsal torso near the wing roots'),
    'longSwimmer': ('STANDING', 'dorsal torso behind the head and neck'),
    'broadSwimmer': ('STANDING', 'center of the upper torso'),
    'radial': ('STANDING', 'upper central body'),
}
# Fraction of the hip-to-neck span used for the seat pivot, per body plan.
SEAT_FRACTION = {
    'quadruped': 0.55, 'quadrupedHerbivore': 0.55, 'sauropod': 0.45, 'bipedCarnivore': 0.30,
    'bipedHerbivore': 0.35, 'horned': 0.55, 'armored': 0.45, 'flyer': 0.45, 'longSwimmer': 0.70,
    'broadSwimmer': 0.45, 'radial': 0.50,
}
CHAIN_PATTERNS = [
    re.compile(r'^c_back(\d+)$', re.I),
    re.compile(r'^Cnt_Spine_(\d+)_JNT_SKL$', re.I),
    re.compile(r'^Spine_?(\d+)$', re.I),
    re.compile(r'^Spine(\d+)_M$', re.I),
    re.compile(r'^Back(\d+)$', re.I),
    re.compile(r'^c_body(\d+)$', re.I),
    re.compile(r'^bodyTop(\d+)$', re.I),
    re.compile(r'^c_tail(\d+)$', re.I),
]
HEAD_PATTERN = re.compile(r'(^|[_\W])head([_\W]|$)', re.I)
WING_ROOT_PATTERN = re.compile(r'(clavicle|wing.?root|wingShoulder|wing_shoulder)', re.I)
TORPID_PARTS = ['in', 'loop', 'eat', 'out_tamed', 'out_wild']


def classify_torpor(name):
    """Map a source clip name to its torpor phase."""
    lowered = name.lower()
    if 'out' in lowered:
        if 'tamed' in lowered:
            return 'out_tamed'
        if 'wild' in lowered:
            return 'out_wild'
        return 'out_tamed'
    if lowered.endswith('eat') or '_eat' in lowered:
        return 'eat'
    if lowered.endswith('in') or '_in' in lowered:
        return 'in'
    return 'loop'
# Rigs whose spine loop is named after the animal rather than a shared prefix.
SOURCE_FOLDERS = {
    'pteranodon': 'Piterodon', 'velociraptor': 'Velociraptor', 'argentavis': 'Argentavis',
    'triceratops': 'Triceratops', 'therizinosaurus': 'Therezinosaur', 'brontosaurus': 'Brontosaur',
    'tyrannosaurus': 'Tyranosaur', 'giganotosaurus': 'Giganotosaur', 'titanosaur': 'Titanosaur',
    'spinosaurus': 'Spinosaurus', 'parasaur': 'Parasaur', 'ceratosaurus': 'Ceratosaurus',
    'dilophosaur': 'Dilophosaur', 'acrocanthosaurus': 'Acrochantosaur', 'allosaurus': 'Allosaurus',
    'ankylosaurus': 'Ankylosaurus', 'carnotaurus': 'Carnotaurus', 'pegomastax': 'Pegomastax',
    'lystrosaurus': 'Lystrosaurus', 'cnidaria': 'Cnidaria', 'plesiosaur': 'Plesiosaur',
    'megalodon': 'Megalodon', 'liopleurodon': 'Liopleurodon', 'mosasaurus': 'Mosasaurus',
    'tusoteuthis': 'Tusoteuthis', 'kaprosuchus': 'Kaprosuchus', 'sarco': 'Sarco',
    'deinosuchus': 'Deinosuchus', 'titanoboa': 'Titanoboa', 'megalocerus': 'Megalocerus',
    'unicorn': 'Unicorn', 'mammoth': 'Mammoth', 'direwolf': 'Direwolf', 'sabertooth': 'Sabertooth',
    'megapithecus': 'Megapithecus', 'paraceratherium': 'Paraceratherium', 'terrorbird': 'Terrorbird',
    'ravager': 'Ravager', 'archaeopteryx': 'Archaeopteryx', 'quetzal': 'Quetzal', 'dragon': 'Dragon',
}


def roster():
    """Registry id plus the already size-multiplied hitbox from Species.java."""
    text = (ROOT / 'src/main/java/dev/nez/arksurvivalreturns/feature/creature/Species.java').read_text(encoding='utf-8')
    sizes = {'titanosaur': 6}
    for id in ('giganotosaurus', 'tyrannosaurus', 'spinosaurus', 'acrocanthosaurus'):
        sizes[id] = 3
    for id in ('cnidaria', 'plesiosaur', 'megalodon', 'liopleurodon', 'mosasaurus', 'tusoteuthis',
               'kaprosuchus', 'sarco', 'deinosuchus', 'titanoboa', 'megalocerus', 'unicorn', 'mammoth',
               'direwolf', 'sabertooth', 'megapithecus', 'paraceratherium', 'terrorbird', 'ravager',
               'archaeopteryx', 'quetzal', 'dragon'):
        sizes[id] = 1
    rows = []
    entry = re.compile(r'^\s{4}([A-Z_]+)\("([a-z_]+)", "([^"]+)",\s*([\d.]+),\s*([\d.]+),\s*([\d.]+),'
                       r'\s*([\d.]+)f,\s*([\d.]+)f,\s*(\d+),\s*(\d+),\s*(\d+),\s*(?:true|false),'
                       r'\s*"([^"]*)",\s*"([^"]*)",\s*"([^"]*)"', re.M)
    for match in entry.finditer(text):
        (enum, ident, name, _hp, _dmg, _speed, width, height, _min, _max, _weight,
         idle, walk, attack) = match.groups()
        size = sizes.get(ident, 2)
        rows.append({'enum': enum, 'id': ident, 'display': name,
                     'width': round(float(width) * size, 4), 'height': round(float(height) * size, 4),
                     'idle': idle, 'walk': walk, 'attack': attack})
    if len(rows) != len(set(r['id'] for r in rows)):
        raise SystemExit('Species parse produced duplicates: ' + repr([r['id'] for r in rows]))
    return rows


def geometry(ident):
    data = json.loads((ASSETS / f'geckolib/models/entity/{ident}.geo.json').read_text(encoding='utf-8'))
    return data['minecraft:geometry'][0]


def runtime_clips(ident):
    data = json.loads((ASSETS / f'geckolib/animations/entity/{ident}.animation.json').read_text(encoding='utf-8'))
    return data['animations']


def source_animations(ident):
    folder = SOURCE / SOURCE_FOLDERS[ident]
    path = next((folder / 'animations').glob('*.json'))
    return json.loads(path.read_text(encoding='utf-8'))['animations']


def head_bone(model):
    matches = [bone for bone in model['bones']
               if HEAD_PATTERN.search(bone['name']) and bone.get('cubes')
               and not re.search(r'end|tip', bone['name'], re.I)]
    if not matches:
        return None
    return max(matches, key=lambda bone: len(bone['cubes']))


def spine_to_head(model):
    """Root-to-head ancestor chain, or an ordered numbered spine chain when the rig has no head bone."""
    by_name = {bone['name']: bone for bone in model['bones']}
    head = head_bone(model)
    if head is not None:
        path, current = [], head
        while current is not None and len(path) < 64:
            path.append(current)
            current = by_name.get(current.get('parent')) if current.get('parent') else None
        path.reverse()
        if len(path) >= 3:
            return path
    for pattern in CHAIN_PATTERNS:
        found = [(int(match.group(1)), bone) for bone in model['bones']
                 for match in [pattern.match(bone['name'])] if match]
        if len(found) >= 3:
            return [bone for _, bone in sorted(found)]
    return []


def mesh_bounds(model, axis=1):
    values = []
    for bone in model['bones']:
        for cube in bone.get('cubes', []):
            values += [cube['origin'][axis], cube['origin'][axis] + cube['size'][axis]]
    return (min(values), max(values)) if values else (0.0, 0.0)


def cube_top(bone):
    return max((cube['origin'][1] + cube['size'][1] for cube in bone.get('cubes', [])), default=None)


def pick_seat(model, plan):
    path = spine_to_head(model)
    if not path:
        return None, 'no head bone and no numbered spine chain in this rig'
    head = path[-1]
    if plan == 'radial':
        # Radial bodies have no spine: take the central top disc of the bell.
        discs = [bone for bone in model['bones'] if re.match(r'^bodyTop\d+$', bone['name'], re.I)]
        if not discs:
            return None, 'no disc bones for a radial body'
        best = min(discs, key=lambda bone: (abs(bone['pivot'][0]), -cube_top(bone)))
        return best, 'central upper disc of the radial body'
    if plan == 'flyer':
        roots = [bone for bone in model['bones'] if WING_ROOT_PATTERN.search(bone['name'])]
        parents = [bone for bone in (path if path else model['bones'])
                   for root in roots if bone['name'] == root.get('parent')]
        if parents:
            seat = max(parents, key=lambda bone: len(bone.get('cubes', [])))
            return seat, f'wing roots attach to {seat["name"]}'
    span = [bone for bone in path[1:] if bone is not head and bone.get('cubes')]
    if not span:
        span = [bone for bone in path if bone is not head and bone.get('cubes')]
    if not span:
        return None, 'no cube-bearing bone between the root and the head'
    fraction = SEAT_FRACTION[plan]
    index = min(len(span) - 1, max(0, round(fraction * (len(span) - 1))))
    seat = span[index]
    return seat, f'{fraction:.0%} of the {span[0]["name"]} to {span[-1]["name"]} span ({len(span)} bones)'


def forward_axis(model):
    path = spine_to_head(model)
    if len(path) < 2:
        return 'none'
    rear, head = path[1], path[-1]
    dx = head['pivot'][0] - rear['pivot'][0]
    dz = head['pivot'][2] - rear['pivot'][2]
    if abs(dx) > abs(dz) * 1.5:
        return 'x' if dx > 0 else '-x'
    return 'z'


MODES = {'flyer': 'FLIGHT', 'longSwimmer': 'SWIM', 'broadSwimmer': 'SWIM', 'radial': 'SWIM',
         'quadruped': 'GROUND', 'quadrupedHerbivore': 'GROUND', 'sauropod': 'GROUND',
         'bipedCarnivore': 'GROUND', 'bipedHerbivore': 'GROUND', 'horned': 'GROUND', 'armored': 'GROUND'}


def emit_java(seat_rows, animation_rows):
    """Write the generated Java tables so no coordinate or clip name is retyped by hand."""
    target = ROOT / 'src/main/java/dev/nez/arksurvivalreturns/feature/taming'
    seats = ['package dev.nez.arksurvivalreturns.feature.taming;', '',
             'import java.util.EnumMap;', 'import java.util.Map;',
             'import dev.nez.arksurvivalreturns.feature.creature.Species;', '',
             '/**',
             ' * Seat transforms measured from each runtime GeckoLib model by',
             ' * {@code tools/build_taming_manifests.py}. Generated file: do not edit by hand; rerun the tool',
             ' * after any model change. Coordinates are entity-local blocks, taken from the real bone pivot of',
             ' * the chosen back bone with the same 180 degree Y correction the renderer applies.',
             ' */',
             'public final class CreatureSeats {',
             '    private static final Map<Species, CreatureRideProfile> PROFILES = build();', '',
             '    private static Map<Species, CreatureRideProfile> build() {',
             '        var map = new EnumMap<Species, CreatureRideProfile>(Species.class);']
    for row in seat_rows:
        seats.append('        map.put(Species.%s, new CreatureRideProfile(CreatureRideProfile.MovementMode.%s,' % (
            row['enum'], MODES[row['body_plan']]))
        seats.append('                %.3f, %.3f, %.3f, %.1f,' % (
            row['local_x'], row['recommended_local_y'], row['local_z'], row['yaw_offset']))
        seats.append('                %.3f, %.3f, %.3f, %.3f, %.3f, %.3f,' % (
            row['ground_dismount']['x'], row['ground_dismount']['y'], row['ground_dismount']['z'],
            row['alt_dismount']['x'], row['alt_dismount']['y'], row['alt_dismount']['z']))
        seats.append('                "%s", "%s"));' % (row['bone'], row['rider_pose']))
    seats += ['        return Map.copyOf(map);', '    }', '',
              '    public static CreatureRideProfile of(Species species) { return PROFILES.get(species); }',
              '    private CreatureSeats() {}', '}', '']
    (target / 'CreatureSeats.java').write_text('\n'.join(seats), encoding='utf-8')

    clips = ['package dev.nez.arksurvivalreturns.feature.taming;', '',
             'import java.util.EnumMap;', 'import java.util.Map;',
             'import dev.nez.arksurvivalreturns.feature.creature.Species;', '',
             '/**',
             ' * Imported torpor clip names and their authored lengths, read from the runtime animation files by',
             ' * {@code tools/build_taming_manifests.py}. Generated file: do not edit by hand. A null clip means',
             ' * the source library has no such clip, and the animation bridge falls back to the sleep pose.',
             ' */',
             'public final class CreatureTorporClips {',
             '    private static final Map<Species, Clips> CLIPS = build();', '',
             '    private static Map<Species, Clips> build() {',
             '        var map = new EnumMap<Species, Clips>(Species.class);']
    for row in animation_rows:
        phases = {value['phase']: value for value in row['torpor'].values()} if isinstance(row['torpor'], dict) \
            else {key: value for key, value in row['torpor'].items()}
        def cell(phase, with_ticks=True):
            entry = phases.get(phase)
            if not entry or not entry.get('runtime'):
                return 'null, 0' if with_ticks else 'null'
            ticks = max(1, round(entry['animation_length'] * 20))
            return ('"%s", %d' % (entry['source'], ticks)) if with_ticks else '"%s"' % entry['source']
        clips.append('        map.put(Species.%s, new Clips(%s, %s, %s, %s, %s));' % (
            row['enum'], cell('in'), cell('loop', False), cell('eat'), cell('out_tamed'), cell('out_wild')))
    clips += ['        return Map.copyOf(map);', '    }', '',
              '    public static Clips of(Species species) { return CLIPS.get(species); }', '',
              '    /**',
              '     * @param in       collapse clip, drawn while the entity is falling into unconsciousness',
              '     * @param inTicks  authored length of {@code in}, in ticks',
              '     * @param loop     unconscious idle, held while torpor is above the wake threshold',
              '     * @param eat      feeding clip played for a single meal',
              '     * @param eatTicks authored length of {@code eat}, in ticks',
              '     * @param outTamed wake clip after a successful tame',
              '     * @param outWild  wake clip after a failed attempt',
              '     */',
              '    public record Clips(String in, int inTicks, String loop, String eat, int eatTicks,',
              '                        String outTamed, int outTamedTicks, String outWild, int outWildTicks) {}',
              '    private CreatureTorporClips() {}', '}', '']
    (target / 'CreatureTorporClips.java').write_text('\n'.join(clips), encoding='utf-8')

    attacks = ['package dev.nez.arksurvivalreturns.feature.creature;', '',
               'import java.util.EnumMap;', 'import java.util.Map;', '',
               '/**',
               ' * Imported melee clip names and their authored lengths, read from the runtime animation files by',
               ' * {@code tools/build_taming_manifests.py}. Generated file: do not edit by hand. The length drives',
               ' * the wind-up before a strike lands and the attack recovery that gates the next one.',
               ' */',
               'public final class CreatureAttackClips {',
               '    private static final Map<Species, Clips> CLIPS = build();', '',
               '    private static Map<Species, Clips> build() {',
               '        var map = new EnumMap<Species, Clips>(Species.class);']
    for row in animation_rows:
        attack = row['attack']
        attacks.append('        map.put(Species.%s, new Clips("%s", %d));' % (
            row['enum'], attack['clip'], attack['ticks']))
    attacks += ['        return Map.copyOf(map);', '    }', '',
                '    public static Clips of(Species species) { return CLIPS.get(species); }', '',
                '    /** @param attack authored melee clip; @param attackTicks its length in ticks */',
                '    public record Clips(String attack, int attackTicks) {}',
                '    private CreatureAttackClips() {}', '}', '']
    (ROOT / 'src/main/java/dev/nez/arksurvivalreturns/feature/creature/CreatureAttackClips.java'
     ).write_text('\n'.join(attacks), encoding='utf-8')


def main():
    rows, animation_matrix, missing = [], {}, []
    for species in roster():
        ident = species['id']
        model = geometry(ident)
        source = source_animations(ident)
        runtime = runtime_clips(ident)
        animated_height = model['description']['visible_bounds_offset'][1] * 2

        # --- animation inventory ---
        torpor_source = {name: clip for name, clip in source.items() if 'torp' in name.lower()}
        clips = {}
        for name, clip in sorted(torpor_source.items()):
            phase = classify_torpor(name)
            clips[phase] = {
                'phase': phase,
                'source': name,
                'runtime': name if name in runtime else None,
                'animation_length': clip['animation_length'],
                'loop': clip.get('loop', False),
                'shared_exit': phase == 'out_tamed' and name.lower().endswith('out'),
            }
        absent = [key for key, value in clips.items() if value['runtime'] is None]
        if absent or not clips:
            missing.append({'id': ident,
                            'missing_runtime': [clips[k]['source'] for k in absent],
                            'has_source_sequence': bool(clips),
                            'phases': sorted(clips)})
        animation_matrix[ident] = {
            'torpor': clips, 'runtime_clip_count': len(runtime), 'runtime_clips': sorted(runtime),
            'enum': species['enum'],
        }

        # --- melee timing ---
        attack_name = species['attack']
        attack_clip = runtime.get(attack_name)
        if attack_clip is None:
            raise SystemExit(f'{ident}: attack clip {attack_name!r} is not imported')
        animation_matrix[ident]['attack'] = {
            'clip': attack_name,
            'ticks': max(1, round(attack_clip['animation_length'] * 20)),
        }

        # --- seat ---
        plan = BODY_PLAN[ident]
        seat, reason = pick_seat(model, plan)
        if seat is None:
            raise SystemExit(f'{ident}: {reason}')
        px, py, pz = seat['pivot']
        top = cube_top(seat)
        raw_y = py / 16.0
        surface_y = ((top + 0.15) / 16.0) if top is not None else raw_y
        local_y = max(0.4, min(surface_y, species['height'] - 0.2))
        forward = forward_axis(model)
        mesh_floor, mesh_ceiling = mesh_bounds(model, 1)
        mesh_x_min, mesh_x_max = mesh_bounds(model, 0)
        mesh_z_min, mesh_z_max = mesh_bounds(model, 2)
        mesh_floor_y = mesh_floor / 16.0
        mesh_span = (mesh_ceiling - mesh_floor) / 16.0
        # The declared source bounds are only reliable for some rigs, so the mesh floor is the check that
        # matters for seating: a mesh whose lowest cube is far from the entity's feet cannot be trusted
        # for a rider offset without clamping.
        grounded = abs(mesh_floor_y) <= 0.35
        ground_z = -(species['width'] / 2.0 + 0.8)
        side_x = species['width'] / 2.0 + 0.8
        pose, target = POSE[plan]
        rows.append({
            'enum': species['enum'],
            'id': ident, 'bone': seat['name'], 'has_cubes': bool(seat.get('cubes')),
            'body_plan': plan, 'anatomical_target': target,
            'pivot_x': round(px, 3), 'pivot_y': round(py, 3), 'pivot_z': round(pz, 3),
            'cube_top_y': round(top, 3) if top is not None else None,
            'local_x': round(-px / 16.0, 3), 'local_y_raw': round(raw_y, 3),
            'mesh_surface_y': round(surface_y, 3), 'recommended_local_y': round(local_y, 3),
            'local_z': round(-pz / 16.0, 3), 'yaw_offset': SEAT_YAW_BY_FORWARD[forward],
            'forward_axis': forward, 'rider_pose': pose,
            'ground_dismount': {'x': 0.0, 'y': 0.0, 'z': round(ground_z, 3)},
            'alt_dismount': {'x': round(side_x, 3), 'y': 0.0, 'z': 0.0},
            'mesh_local_x_min': round(-mesh_x_max / 16.0, 3), 'mesh_local_x_max': round(-mesh_x_min / 16.0, 3),
            'mesh_local_z_min': round(-mesh_z_max / 16.0, 3), 'mesh_local_z_max': round(-mesh_z_min / 16.0, 3),
            'seat_inside_mesh': (-mesh_x_max / 16.0 - 0.25) <= (-px / 16.0) <= (-mesh_x_min / 16.0 + 0.25)
                                and (-mesh_z_max / 16.0 - 0.25) <= (-pz / 16.0) <= (-mesh_z_min / 16.0 + 0.25),
            'model_height': animated_height, 'entity_height': species['height'],
            'entity_width': species['width'],
            'mesh_floor_y': round(mesh_floor_y, 3), 'mesh_span': round(mesh_span, 3),
            'model_normalized': grounded,
            'seat_validated': bool(seat.get('cubes')) and abs(local_y - surface_y) < 0.05,
            'notes': reason + ('' if grounded else '; model mesh is not normalized to the entity origin'),
        })
        if abs(animated_height - species['height']) > 0.001:
            raise SystemExit(f'{ident}: model height {animated_height} != registered height {species["height"]}')
    ungrounded = [row['id'] for row in rows if not row['model_normalized']]
    unseated = [row['id'] for row in rows if not row['seat_validated']]
    emit_java(rows, [animation_matrix[species['id']] for species in roster()])
    DOCS.mkdir(exist_ok=True)
    (DOCS / 'taming-seat-manifest.json').write_text(json.dumps({
        'generated': date.today().isoformat(),
        'coordinate_system': {
            'units': 'entity-local blocks; model units are 1/16 block and were divided by 16',
            'origin': 'the entity\'s feet at the centre of the hitbox; local +Y is up',
            'axes': 'local +X is the entity\'s left-to-right axis, -Z is the vanilla forward direction',
            'renderer_correction': 'CreatureRenderer rotates the imported model 180 degrees about Y, so a '
                                   'model point (mx,my,mz) becomes (-mx/16, my/16, -mz/16)',
            'server_authority': 'these offsets are applied by CreatureEntity.positionRider on the server; '
                                'client bone positions are never authoritative',
        },
        'species': rows,
        'models_not_normalized_to_the_entity_origin': ungrounded,
        'seats_clamped_into_the_hitbox': unseated,
    }, indent=1) + '\n', encoding='utf-8')
    (DOCS / 'taming-animation-matrix.json').write_text(json.dumps({
        'generated': date.today().isoformat(),
        'source_torpor_clips': {ident: row['torpor'] for ident, row in animation_matrix.items()},
        'runtime_clips': {ident: row['runtime_clips'] for ident, row in animation_matrix.items()},
        'melee_clips': {ident: row['attack'] for ident, row in animation_matrix.items()},
        'species_without_torpor_assets': sorted(entry['id'] for entry in missing if not entry['has_source_sequence']),
        'incomplete': missing,
    }, indent=1) + '\n', encoding='utf-8')
    print(f'{len(rows)} species. Species with an incomplete torpor set: {len(missing)}. '
          f'Wrote docs/taming-seat-manifest.json, docs/taming-animation-matrix.json, CreatureSeats.java, '
          f'CreatureTorporClips.java and CreatureAttackClips.java.')
    print(f'Meshes not seated on the entity origin: {len(ungrounded)} -> {", ".join(ungrounded)}')
    print(f'Seats clamped into the hitbox: {len(unseated)} -> {", ".join(unseated)}')
    for entry in missing:
        print('  incomplete:', entry['id'], 'missing runtime:', entry['missing_runtime'],
              'phases:', entry['phases'])


if __name__ == '__main__':
    main()
