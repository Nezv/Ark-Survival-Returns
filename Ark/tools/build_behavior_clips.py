"""Measure every runtime clip and write the behaviour clip book the wildlife state machine plays.

Reads the runtime GeckoLib models and animations (after tools/import_creatures.py) and writes
src/main/java/dev/nez/arksurvivalreturns/feature/behavior/BehaviorClips.java: per species, the
length of every clip in ticks, the ground speed each locomotion clip was authored for, and the
behaviour roles (turn in place, look around, sniff, poop, startle, threat, flinch, flight banking).

Natural ground speed
--------------------
ARK locomotion clips are authored in place: the root never travels. While a foot is planted it
slides backward under the body at exactly the speed the animal is meant to walk, so the clip's own
ground speed is the mean horizontal speed of the lowest bones during their stance phase. The bone
transforms follow GeckoLib's runtime conventions (Bedrock pivots and rotations with X/Y rotation and
X pivot negated, rotation order Z, Y, X, animated position offsets), 16 model units per block.
Speeds are an animation property, not gameplay: the runtime still owns movement and only uses them to
match cadence to real travel, so feet stop sliding. Run from Ark: python tools/build_behavior_clips.py
"""
from pathlib import Path
import json
import math
import re

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/arksurvivalreturns/geckolib'
TARGET = ROOT / 'src/main/java/dev/nez/arksurvivalreturns/feature/behavior/BehaviorClips.java'
SAMPLES_PER_SECOND = 60

# Behaviour roles resolved from each rig's own clip names; the first pattern that matches wins.
# In-place turns come first; rigs without one turn in place with their walking-turn clip.
# The same table decides which extra source clips tools/import_creatures.py brings in.
GROUND_ROLES = [
    ('TURN_LEFT', [r'Ground-Turn-Lft$', r'(?<!Fly)-Turn-Lft$', r'_Move_TurnLeft$', r'-Move-Lft$',
                   r'-Walk-Lft$', r'_Move_Walk_LEFT$', r'_Move_TurningLeft$', r'_Move_Left$', r'-Trot-Lft$']),
    ('TURN_RIGHT', [r'Ground-Turn-Rit$', r'(?<!Fly)-Turn-Rit$', r'_Move_TurnRight$', r'-Move-Rit$',
                    r'-Walk-Rit$', r'_Move_Walk_RIGHT$', r'_Move_TurningRight$', r'_Move_Right$', r'-Trot-Rit$']),
]
# Aim offsets, rider/saddle poses and alternate stances are never behaviour clips.
EXCLUDED = re.compile(r'(Aim|Biped|Zipline|Platform|Mounted|Holding|Carried|Shield|Savage|Torp)')
SHARED_ROLES = [
    ('LOOK', [r'-Look-Around$', r'-Turret-Check-Idle$']),
    ('SNIFF', [r'-Sniff$']),
    ('POOP', [r'-Poop$']),
    ('STARTLE', [r'-Startled$', r'_Startled$', r'-Startled-Lft$', r'-Startle$']),
    ('THREAT', [r'-Idle-Aggressive$', r'_Aggro$']),
    ('HURT', [r'-Hurt-Big-Rit$', r'-Hurt-Big-Lft$', r'-Hurt-Big$', r'-Hurt-Lft$', r'-Hurt-Small-Rit$',
              r'-Hurt-Small-Lft$', r'-Hurt-Small$', r'-Ground-Hurt-Rit$', r'-Hurt$']),
    ('SETTLE', [r'_Basking_Start$']),
    ('REST', [r'_Basking_Idle$']),
    ('WAKE', [r'_Basking_End$']),
    ('TROT', [r'-Trot-Fwd$']),
    ('FLAP', [r'-Fly-Flap$', r'-Fly-Flap-Fwd$', r'^Archaeopteryx-Fly$']),
    ('FLY_LEFT', [r'-Fly-Lft$']),
    ('FLY_RIGHT', [r'-Fly-Rit$']),
    ('FLY_IDLE', [r'-Fly-Idle$']),
    ('GLIDE', [r'-Glide$']),
]
WATER_ROLES = [
    ('SWIM_LEFT', [r'-Swim-Lft$', r'-Idle-Lft$', r'-Move-Lft$']),
    ('SWIM_RIGHT', [r'-Swim-Rit$', r'-Idle-Rit$', r'-Move-Rit$']),
]
LOOPING_ROLES = {'TURN_LEFT', 'TURN_RIGHT', 'THREAT', 'REST', 'TROT', 'FLAP', 'FLY_LEFT', 'FLY_RIGHT', 'FLY_IDLE',
                 'GLIDE', 'SWIM_LEFT', 'SWIM_RIGHT'}
LOCOMOTION = re.compile(r'(Move|Walk|Charge|Trot)', re.I)
NOT_GROUND = re.compile(r'(Fly|Glide|Swim|Torp|Attack|Hover|Idle|Land|Take-Off|Startle|Turn)', re.I)


def role_table(aquatic):
    return (WATER_ROLES if aquatic else GROUND_ROLES) + SHARED_ROLES


def behavior_extras(names, aquatic):
    """Role -> clip name for every behaviour role a rig can play, chosen from its own clip names."""
    found = {}
    names = [name for name in names if not EXCLUDED.search(name)]
    for role, patterns in role_table(aquatic):
        for pattern in patterns:
            match = next((name for name in names if re.search(pattern, name)), None)
            if match:
                found[role] = match
                break
    return found


def load(path):
    return json.loads(path.read_text(encoding='utf-8'))


def roster():
    """Registry ids and enum names, in declaration order, from Species.java."""
    text = (ROOT / 'src/main/java/dev/nez/arksurvivalreturns/feature/creature/Species.java').read_text(encoding='utf-8')
    return re.findall(r'^\s{4}([A-Z_]+)\("([a-z_]+)", "', text, re.M)


# ------------------------------------------------------------------------------------ rig math

def matmul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def translation(x, y, z):
    return [[1, 0, 0, x], [0, 1, 0, y], [0, 0, 1, z], [0, 0, 0, 1]]


def rotation(axis, angle):
    c, s = math.cos(angle), math.sin(angle)
    if axis == 'x':
        return [[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]]
    if axis == 'y':
        return [[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]]
    return [[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def apply(m, p):
    return [m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3] for i in range(3)]


def keyframe_value(value):
    if isinstance(value, list):
        return value
    if isinstance(value, dict):
        for key in ('post', 'vector', 'pre'):
            if key in value:
                inner = value[key]
                return inner if isinstance(inner, list) else inner.get('vector', [0, 0, 0])
    return [0, 0, 0]


def sample(track, t):
    """Linear sample of a Bedrock keyframe track; constants and single keys hold their value."""
    if track is None:
        return [0.0, 0.0, 0.0]
    if isinstance(track, list):
        return [float(v) if isinstance(v, (int, float)) else 0.0 for v in track]
    keys = sorted(((float(k), keyframe_value(v)) for k, v in track.items()), key=lambda kv: kv[0])
    if t <= keys[0][0]:
        return [float(v) if isinstance(v, (int, float)) else 0.0 for v in keys[0][1]]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            f = 0 if t1 == t0 else (t - t0) / (t1 - t0)
            return [(float(a) if isinstance(a, (int, float)) else 0.0) * (1 - f)
                    + (float(b) if isinstance(b, (int, float)) else 0.0) * f for a, b in zip(v0, v1)]
    return [float(v) if isinstance(v, (int, float)) else 0.0 for v in keys[-1][1]]


class Rig:
    def __init__(self, model):
        self.bones = {b['name']: b for b in model['bones']}
        self.order = []
        seen = set()

        def visit(name):
            if name in seen:
                return
            parent = self.bones[name].get('parent')
            if parent in self.bones:
                visit(parent)
            seen.add(name)
            self.order.append(name)
        for name in self.bones:
            visit(name)

    def joints(self, clip, t):
        """Model-space joint positions (model units) of every bone at time t."""
        tracks = clip.get('bones', {})
        world, points = {}, {}
        for name in self.order:
            bone = self.bones[name]
            px, py, pz = bone.get('pivot', [0, 0, 0])
            pivot = (-px, py, pz)
            rx, ry, rz = bone.get('rotation', [0, 0, 0])
            track = tracks.get(name, {})
            ax, ay, az = sample(track.get('rotation'), t)
            ox, oy, oz = sample(track.get('position'), t)
            local = translation(-ox, oy, oz)
            local = matmul(local, translation(*pivot))
            local = matmul(local, rotation('z', math.radians(rz + az)))
            local = matmul(local, rotation('y', math.radians(-(ry + ay))))
            local = matmul(local, rotation('x', math.radians(-(rx + ax))))
            local = matmul(local, translation(-pivot[0], -pivot[1], -pivot[2]))
            parent = bone.get('parent')
            world[name] = matmul(world[parent], local) if parent in world else local
            points[name] = apply(world[name], pivot)
        return points


def ground_speed(rig, clip):
    """Blocks per second a planted foot slides backward, or None when no stance phase is visible."""
    length = float(clip.get('animation_length') or 0)
    if length <= 0.05:
        return None
    frames = max(12, int(length * SAMPLES_PER_SECOND))
    times = [length * i / frames for i in range(frames + 1)]
    poses = [rig.joints(clip, t) for t in times]
    lowest = min(min(p[1] for p in pose.values()) for pose in poses)
    top = max(max(p[1] for p in pose.values()) for pose in poses)
    height = max(1e-6, top - lowest)
    # Contact bones: joints that reach the ground band during the cycle and are not the root.
    roots = {name for name, bone in rig.bones.items() if 'parent' not in bone}
    step = length / frames
    speeds = []
    for name in rig.bones:
        if name in roots:
            continue
        ys = [pose[name][1] for pose in poses[:-1]]
        low, high = min(ys), max(ys)
        if low - lowest > height * 0.06 or high - low < height * 0.01:
            continue
        # Principal horizontal axis of the foot's sweep (2x2 covariance, closed form).
        xs = [pose[name][0] for pose in poses[:-1]]
        zs = [pose[name][2] for pose in poses[:-1]]
        mx, mz = sum(xs) / frames, sum(zs) / frames
        cxx = sum((x - mx) ** 2 for x in xs)
        czz = sum((z - mz) ** 2 for z in zs)
        cxz = sum((x - mx) * (z - mz) for x, z in zip(xs, zs))
        angle = 0.5 * math.atan2(2 * cxz, cxx - czz)
        ax, az = math.cos(angle), math.sin(angle)
        # The clip loops, so the last sample wraps to the first when taking velocities.
        along = [((xs[(i + 1) % frames] - xs[i]) * ax + (zs[(i + 1) % frames] - zs[i]) * az) / step
                 for i in range(frames)]
        forward = [i for i in range(frames) if along[i] > 0]
        backward = [i for i in range(frames) if along[i] < 0]
        if len(forward) < 3 or len(backward) < 3:
            continue
        # Stance is the sweep during which the foot stays lower; the swing lifts it.
        lower_forward = sum(ys[i] for i in forward) / len(forward) < sum(ys[i] for i in backward) / len(backward)
        stance = forward if lower_forward else backward
        # Trim touch-down and toe-off: the middle of the planted sweep carries the body speed.
        speeds_in_stance = sorted(abs(along[i]) for i in stance)
        speeds.append(speeds_in_stance[len(speeds_in_stance) // 2] / 16.0)
    if not speeds:
        return None
    speeds.sort()
    return speeds[len(speeds) // 2]


# ---------------------------------------------------------------------------------------- tables

def aquatic_ids():
    from collection_catalog import COLLECTION
    return {entry['id'] for entry in COLLECTION if entry['realm'] == 'WATER'}


def measure(ident, aquatic):
    model = load(ASSETS / f'models/entity/{ident}.geo.json')['minecraft:geometry'][0]
    clips = load(ASSETS / f'animations/entity/{ident}.animation.json')['animations']
    rig = Rig(model)
    rows = []
    for name, clip in clips.items():
        ticks = max(1, round(float(clip.get('animation_length') or 0) * 20))
        # Swimming and flight have no stance phase; only ground gaits carry a measurable ground speed.
        speed = ground_speed(rig, clip) if not aquatic and LOCOMOTION.search(name) and not NOT_GROUND.search(name) else None
        rows.append((name, ticks, speed, bool(clip.get('loop'))))
    return rows, behavior_extras(list(clips), aquatic)


def emit(species):
    lines = ['package dev.nez.arksurvivalreturns.feature.behavior;', '',
             'import java.util.HashMap;', 'import java.util.Map;', '',
             '/**',
             ' * Runtime clip lengths, authored ground speeds and behaviour roles, measured from the runtime',
             ' * GeckoLib models and animations by {@code tools/build_behavior_clips.py}. Generated file: do',
             ' * not edit by hand; rerun the tool after importing clips. Speeds are blocks per second, or NaN',
             ' * for clips without a visible stance phase.',
             ' */',
             'public final class BehaviorClips {',
             '    private static final Map<String, ClipBook> BOOKS = new HashMap<>();', '',
             '    static {']
    for enum, ident, rows, found in species:
        lines.append(f'        BOOKS.put("{ident}", new ClipBook.Builder("{ident}")')
        for name, ticks, speed, loop in rows:
            value = 'Double.NaN' if speed is None else f'{speed:.3f}'
            lines.append(f'                .clip("{name}", {ticks}, {value}, {str(loop).lower()})')
        for role, name in sorted(found.items()):
            lines.append(f'                .role(ClipRole.{role}, "{name}")')
        lines.append('                .build());')
    lines += ['    }', '',
              '    /** The clip book of a registry id; an empty book when the species has no runtime clips. */',
              '    public static ClipBook of(String species) {',
              '        return BOOKS.getOrDefault(species, ClipBook.EMPTY);',
              '    }', '',
              '    private BehaviorClips() {}', '}', '']
    TARGET.write_text('\n'.join(lines), encoding='utf-8')


def main():
    species = []
    water = aquatic_ids()
    for enum, ident in roster():
        rows, found = measure(ident, ident in water)
        species.append((enum, ident, rows, found))
        walkers = [f'{n}={s:.2f}' for n, _, s, _ in rows if s is not None]
        print(f'{ident:17s} roles={len(found):2d}  ' + ', '.join(walkers))
    emit(species)
    print(f'Wrote {TARGET.relative_to(ROOT)} for {len(species)} species.')


if __name__ == '__main__':
    main()
