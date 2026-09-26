"""Part isolation for the creature painter (Giganotosaur/textures/build_skin_v7.py).

Horns, antlers, tusks, claws, hooves, beaks, frills, wing membranes and flight feathers get their own
material instead of body skin. Each rule gives a cube a material and a base-to-tip axis; the painter
turns the axis into a five-step gradient and keeps its own seeded 3x3 brush shades on top, so every
part shares the body's noise level. Skin-derived materials (frill, membrane, feather) start from the
creature's anatomy layers, so they follow each variant's palette.

Thresholds are in the painter's source units (Creatures/<name>/geo), not the scaled runtime models.
"""
import re
import numpy as np
import skin_studio as st

EXCLUDED = {'Dragon'}  # to be replaced by the All-Under-Heaven dragon (dashboard I10)

# Five stops from root to tip.
FIXED = {
    'horn': ['#4f4336', '#66594a', '#85775f', '#a69879', '#c8bc9c'],
    'tusk': ['#a8977a', '#bfb092', '#d3c7aa', '#e3dac2', '#f0ead8'],
    'claw': ['#3b342c', '#4c4338', '#625748', '#7f735f', '#a39781'],
    'beak': ['#5e4c35', '#78623f', '#977d4d', '#b39862', '#cbb27a'],
}
ACCENT = '#b8472e'         # display colour blended into frills toward the edge
MEMBRANE_TINT = '#e2c4a2'  # thin, backlit skin
DIGITS = re.compile(r'toe|finger|digit|claw|fingy|talon|nail|thumb', re.I)

# Share of the head length, measured back from the snout, that is beak.
BEAKS = {'Argentavis': .30, 'Terrorbird': .45, 'Piterodon': .55, 'Quetzal': .6}
# Pterosaurs: thin plates on the wing and finger bones are membrane.
MEMBRANES = {'Piterodon', 'Quetzal'}
# Parts fitted to a skull bone that carries no name for them: (bone, material, test on the cube's
# centre offset from the bone pivot and its extents).
SKULL_PARTS = {
    'Megalocerus': ('c_neck3', 'horn', lambda d, e: d[1] > 1.75 or abs(d[0]) > 2.45),
    'Unicorn': ('c_neck3', 'tusk', lambda d, e: d[1] > .25 and e[0] < 1.0 and d[2] > 1.5),
}


def isolate(name, cubes, skeleton):
    """{id(cube): (material, base, tip, start, radial)}.

    The material covers cells from `start` (0-1) of the way from base to tip. Bone-anchored parts grade by
    distance from the joint (radial), so paired horns or antlers on one bone both fade to pale tips; beaks
    grade along the snout axis.
    """
    if name in EXCLUDED:
        return {}
    out = {}
    names, parents = skeleton['names'], skeleton['parents']
    by_bone = {}
    for cube in cubes:
        by_bone.setdefault(cube['bone'], []).append(cube)
    kids = {i: [] for i in range(len(names))}
    for i, parent in enumerate(parents):
        if parent >= 0:
            kids[parent].append(i)

    def bears_digit(i):
        return any((DIGITS.search(names[j]) and names[j] in by_bone) or bears_digit(j) for j in kids[i])

    def mark(group, material, base=None, start=0.0):
        base = group[0]['bone_world'] if base is None else np.asarray(base, float)
        corners = np.concatenate([st.cube_corners(c) for c in group])
        tip = corners[np.argmax(np.linalg.norm(corners - base, axis=1))]
        for cube in group:
            out[id(cube)] = (material, base, tip, start, True)

    for bone, group in by_bone.items():
        low = bone.lower()
        if re.search('frill|crest', low):
            mark(group, 'frill')
        elif 'tusk' in low:
            mark(group, 'tusk')
        elif re.search('horn|spike|antler', low):
            mark(group, 'horn')
        elif 'hoof' in low:
            mark(group, 'claw')
        elif 'beak' in low:
            mark(group, 'beak')
        elif 'feather' in low and name not in MEMBRANES:
            mark(group, 'feather')
    # The last phalanx of every digit: a claw on its outer half, or the whole cube for claw bones.
    for i, bone in enumerate(names):
        low = bone.lower()
        if bone not in by_bone or not DIGITS.search(bone) or 'bigfinger' in low or bears_digit(i):
            continue
        if name in MEMBRANES and 'finger' in low and 'mini' not in low:
            continue
        mark(by_bone[bone], 'claw', start=0.0 if 'claw' in low else .45)
    if name in MEMBRANES:
        for bone, group in by_bone.items():
            if not re.search('wing|finger', bone, re.I):
                continue
            plates = [c for c in group if thin(c)]
            if plates:
                mark(plates, 'membrane')
    if name in SKULL_PARTS:
        bone, material, test = SKULL_PARTS[name]
        group = [c for c in by_bone.get(bone, [])
                 if test(st.cube_corners(c).mean(0) - c['bone_world'], np.ptp(st.cube_corners(c), 0))]
        if group:
            mark(group, material)
    if name in BEAKS:
        # Every imported skeleton faces +Z, so the snout is the head's largest Z (crests point back).
        skull = [c for c in cubes if c['part'] in ('head', 'jaw')]
        if skull:
            z = np.concatenate([st.cube_corners(c) for c in skull])[:, 2]
            snout = np.array([0.0, 0.0, z.max()])
            base = snout - [0.0, 0.0, (z.max() - z.min()) * BEAKS[name]]
            for cube in skull:
                if id(cube) not in out:
                    out[id(cube)] = ('beak', base, snout, 0.0, False)
    return out


def thin(cube):
    extent = np.sort(np.ptp(st.cube_corners(cube), 0))
    return extent[0] < .25 * extent[2]


def cells(spec, points):
    """Per brush cell: the gradient step (0-4) and whether the part covers the cell."""
    _, base, tip, start, radial = spec
    axis = np.asarray(tip, float) - base
    if radial:
        t = np.linalg.norm(np.asarray(points, float) - base, axis=1) / max(float(np.linalg.norm(axis)), 1e-9)
    else:
        t = (np.asarray(points, float) - base) @ axis / max(float(axis @ axis), 1e-9)
    covered = t >= start
    step = np.digitize(np.clip((t - start) / max(1.0 - start, 1e-6), 0, 1), [.2, .4, .6, .8])
    return step, covered


def table(material, skin, offsets):
    """(5 gradient steps, 5 brush shades, rgb) for one material; `skin` is the painter's layer palette."""
    mid = skin[:, 2].astype(np.float32)
    if material in FIXED:
        stops = np.array([st.rgb(c) for c in FIXED[material]], np.float32)
    elif material == 'frill':
        stops = np.array([st.mix(mid[2], st.rgb(ACCENT), w) for w in (0, .15, .3, .45, .6)], np.float32)
    elif material == 'membrane':
        stops = np.array([st.mix(mid[k], st.rgb(MEMBRANE_TINT), w)
                          for k, w in ((3, .25), (3, .35), (2, .42), (2, .5), (1, .55))], np.float32)
    elif material == 'feather':
        stops = np.array([mid[2], mid[2], mid[3], mid[4], st.darken(mid[4], .15)], np.float32)
    else:
        raise ValueError(material)
    return np.clip(stops[:, None, :] + np.asarray(offsets, np.float32)[None, :, None], 0, 255).astype(np.uint8)
