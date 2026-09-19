"""Giga: anatomical paint layers sampled on a shared model-space brush grid."""
import json
import sys
from pathlib import Path
import numpy as np

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'scripts'))
import skin_studio as st

COLORS = ['#f4ddb0', '#e3bd83', '#ce9256', '#b87540', '#98532f',
          '#793b25', '#572b22', '#38221e', '#201d1b', '#625747',
          '#fff0ce', '#74363a', '#ba6863', '#ecc04c']
C = np.array([st.rgb(x) for x in COLORS])
NAIL_TIPS = []
STYLE = {**st.GIGA_STYLE, 'quantize': False, 'dither': 0,
         'region_overrides': dict(st.GIGA_STYLE['region_overrides'])}
for side in ('l', 'r'):
    for digit in 'ABC':
        STYLE['region_overrides'][f'{side}_finger{digit}3'] = 'claw'
    for digit in 'ABCD':
        STYLE['region_overrides'][f'{side}_toe{digit}3'] = 'claw'


def snap(v, step):
    return (np.floor(v / step) + .5) * step


def paint(points, normals, ids, bounds, features, palette, style, field, shade=True):
    # A shared grid prevents each cube from restarting the painted pattern.
    p = snap(points, 1.5)
    s, d = field.dorsal_weight(p)
    s = snap(s, 2.4)
    d = np.clip(d, 0, 1)
    def part(*names):
        return np.isin(ids, [st.PART_ID[n] for n in names])
    body = part('torso', 'neck', 'tail')
    head = part('head', 'eye')
    local = np.clip((p[:, 1] - bounds[ids, 2]) /
                    np.maximum(bounds[ids, 3] - bounds[ids, 2], .01), 0, 1)
    # Warm midtones occupy most of the side; darkest tones stay on the back.
    level = np.digitize(d, [.17, .30, .43, .59, .75, .91])
    index = level.copy()
    # Broad tapered bars descend from the dorsal mass. Deterministic rhythm,
    # two-unit-wide brush cells, no independent texel randomness.
    phase = np.mod(s + snap((1-d)*3.0, 1.5), 10.8)
    bars = body & (phase < 2.4) & (d > .35)
    index[bars] = np.minimum(index[bars] + 2, 7)
    # An amber shoulder/flank highlight stays within the middle value range.
    highlight = body & (d > .33) & (d < .60) & (phase >= 4.8) & (phase < 7.2)
    index[highlight] = np.maximum(index[highlight] - 1, 1)
    limb = part('legl', 'legr', 'arml', 'armr', 'footl', 'footr')
    index[limb] = np.digitize(local[limb], [.22, .47, .72]) + 2
    # Inner limb plane is softer and lighter, using position rather than
    # face normals (which would emphasize every extracted cuboid).
    inner = limb & (np.abs(p[:, 0]) < 3.6)
    index[inner] = np.maximum(index[inner] - 1, 1)
    index[part('jaw')] = np.where(local[part('jaw')] > .7, 1, 0)
    index[part('tongue')] = 12
    index[part('claw')] = 8
    for tip in NAIL_TIPS:
        dist = np.linalg.norm(points-tip, axis=1)
        index[limb & (dist < .95)] = 8
        index[limb & (dist >= .95) & (dist < 1.3)] = 9
    frame = features.get('head_frame')
    if frame is not None:
        f, u, lateral = frame.coords(snap(points, .6))
        hd = np.clip((u + frame.half_height)/(2*frame.half_height), 0, 1)
        index[head] = np.digitize(hd[head], [.22, .38, .56, .72, .88]) + 1
        for center in features['eye_centers']:
            ef, eu, el = frame.coords(center)
            distance = np.maximum(np.abs(f-ef), np.abs(u-eu))
            side = head & (np.abs(lateral) > frame.half_width*.40)
            index[side & (distance < 1.15)] = 7
            index[side & (distance < .65)] = 13
            index[side & (distance < .26)] = 8
        # Single bold snout mark; facial details retain their own brush size.
        nostril = head & (f > frame.length*.25) & (f < frame.length*.36) & (hd > .4) & (hd < .57)
        index[nostril] = 8
    profile = features.get('profile')
    if profile:
        z = points[:, 2]
        lip = np.interp(z, profile['centers'], profile['lip'])
        jaw = np.interp(z, profile['centers'], profile['jaw_top'])
        mouth = ((part('head') & (np.abs(points[:, 1]-lip) < .48)) |
                 (part('jaw') & (np.abs(points[:, 1]-jaw) < .48))) & (z > profile['z0']) & (z < profile['z1'])
        index[mouth] = 11
        index[mouth & (np.mod(z, 1.6) < .85)] = 10
    # Direct lookup keeps mouth/pupil colours out of body shade quantization.
    return C[index]


def main():
    creature = ROOT / 'Creatures/Giganotosaur'
    _, cubes, skeleton = st.load_cubes(creature/'geo/giganotosaur.geo.json', STYLE)
    for cube in cubes:
        name = cube['bone']
        if ('toe' in name or 'finger' in name) and name.endswith('2'):
            terminal = name[:-1]+'3'
            if terminal in skeleton['names'] and cube['cube_index'] == 0:
                pivot = skeleton['pivots'][skeleton['names'].index(terminal)]
                NAIL_TIPS.append(cube['bone_world'] + cube['bone_matrix'] @ (pivot-cube['bone_pivot']))
    st.paint_points = paint
    report, *_ = st.build_skin('Giganotosaur', creature,
        ['#98532f','#ce9256','#572b22','#e3bd83','#f4ddb0','#38221e','#ecc04c','#ba6863'],
        style=STYLE, out_name='skin_v5')
    src = json.loads((creature/'Giganotosaur_GeckoLib.bbmodel').read_text())
    dst = json.loads((creature/'skin_v5/Giganotosaur_Textured.bbmodel').read_text())
    assert src['animations'] == dst['animations'] and src['outliner'] == dst['outliner']
    def mesh(doc):
        return [{k:v for k,v in e.items() if k != 'faces'} for e in doc['elements']]
    assert mesh(src) == mesh(dst)
    assert all(report['checks'].values())
    mapping = {b['name']: st.part_of(b['name'], STYLE['region_overrides'])
               for b in json.loads((creature/'geo/giganotosaur.geo.json').read_text())['minecraft:geometry'][0]['bones'] if b.get('cubes')}
    (creature/'skin_v5/paint_regions.json').write_text(json.dumps(mapping, indent=2)+'\n')
    (creature/'skin_v5/painting_rules.json').write_text(json.dumps({
        'body_brush_model_units':1.5, 'longitudinal_brush_model_units':2.4,
        'head_brush_model_units':.6, 'stripe_period_model_units':10.8,
        'nail_tip_centers':[p.tolist() for p in NAIL_TIPS],
        'nail_mask':'distance < 0.95 model units from terminal digit pivot; approximate anatomical mask',
        'palette':COLORS}, indent=2)+'\n')
    print(json.dumps({'checks':report['checks'], 'parts':report['parts'], 'metrics':report['metrics'],
                      'mesh_skeleton_animations_unchanged':True}, indent=2))

if __name__ == '__main__':
    main()
