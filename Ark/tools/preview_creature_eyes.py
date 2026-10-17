"""Close-ups of the designed eyes (build_creature_eyes.DESIGNS) in the states the client gives them.

Each species is posed in its idle clip and its eyes set the way client/CreatureEyes.java sets them: lids scaled
from their hinges to the closure of the look, the brow lowered and tilted, the pupil narrowed or widened, the
calm or the hunting eyeball. Renders follow GeckoLib's own baking (geckolib_mesh), nearest texel, so the eye art
is seen as the game draws it.

    python tools/preview_creature_eyes.py [species ...]      -> Ark/design/creatures/eyes/<species>.png
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

import geckolib_mesh as gm
from build_creature_eyes import DESIGNS, LID_SHUT, LID_TUCK, design_of, lid_cover, pupil_kind
from import_creatures import SPECIES

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/arksurvivalreturns'
OUT = ROOT / 'design/creatures/eyes'
IDLE = {identifier: idle for _, identifier, _, idle, *_ in SPECIES}
# The expressions of client/CreatureEyes.java: lid closure (0 the species' own lids, 1 shut, below 0 wider),
# brow drop (eye heights) and tilt (degrees, front end down), pupil width (a scale), the hunting eyes.
STATES = {
    'calm': dict(closure=0.00, drop=0.00, tilt=0, slit=1.00, red=False),
    'alert': dict(closure=-0.27, drop=0.06, tilt=6, slit=0.70, red=False),
    'hunt': dict(closure=0.28, drop=0.12, tilt=8, slit=0.45, red=True),
    'blink': dict(closure=0.75, drop=0.03, tilt=0, slit=1.00, red=False),
    'closed': dict(closure=1.00, drop=0.06, tilt=0, slit=1.00, red=False),
    'flee': dict(closure=-0.67, drop=-0.10, tilt=-4, slit=1.30, red=False),
}


def eye_pose(design, state):
    """Bone snapshot values of one expression, as CreatureRenderer applies them."""
    e = STATES[state]
    height = design['size'][1]
    # A slit narrows across, a round pupil all round over a smaller range, a grazer's bar mostly in height.
    kind = pupil_kind(design)
    across = {'slit': e['slit'], 'round': 1 + (e['slit'] - 1) * .5, 'bar': 1 + (e['slit'] - 1) * .25}.get(kind, 1)
    along = {'round': across, 'bar': 1 + (e['slit'] - 1) * .8}.get(kind, 1)
    red = e['red'] and design['iris']['hunt'] is not None
    pose = {}
    for side in ('l', 'r'):
        for lid in design['lids']:
            rest = design['rest'][lid]
            scale = (lid_cover(e['closure'], rest, LID_SHUT[lid]) + LID_TUCK[lid]) / (rest + LID_TUCK[lid])
            pose[f'ark_eye_lid_{lid}_{side}'] = {'scale': (1, scale, 1)}
        pose[f'ark_eye_brow_{side}'] = {'pos': (0, -e['drop'] * height, 0), 'rot': (math.radians(e['tilt']), 0, 0)}
        pose[f'ark_eye_pupil_{side}'] = {'scale': (1, along, across)}
        pose[f'ark_eyeball_calm_{side}'] = {'hide': red}
        pose[f'ark_eyeball_hunt_{side}'] = {'hide': not red}
    return pose


def eye_point(model, pose, side='l'):
    """The eye's centre, its outward direction and its forward direction in the posed model."""
    world, _ = gm.bone_matrices(model, pose)
    bone = next(b for b in model['bones'] if b['name'] == f'ark_eye_{side}')
    m = world[bone['name']]
    pivot = np.array(bone['pivot'], float) * [-1, 1, 1]
    centre = m[:3, :3] @ pivot + m[:3, 3]
    sign = 1 if side == 'l' else -1
    return centre, m[:3, :3] @ np.array([sign, 0.0, 0.0]), m[:3, :3] @ np.array([0, 0, 1.0])


def sheet(identifier, variant='ivory'):
    design = design_of(identifier)
    model = gm.load(ASSETS / f'geckolib/models/entity/{identifier}.geo.json')
    clip = json.loads((ASSETS / f'geckolib/animations/entity/{identifier}.animation.json').read_text())['animations'][IDLE[identifier]]
    idle = gm.clip_pose(clip, 0.0)
    texture = Image.open(ASSETS / f'textures/entity/{identifier}_{variant}.png')
    glow_file = ASSETS / f'textures/entity/{identifier}_eyes.png'
    glow = Image.open(glow_file) if glow_file.is_file() else None
    length = design['size'][0]
    tiles = []

    def tile(state, label, background=(28, 32, 38), tint=None, shine=None, distance=3.2):
        pose = gm.merge(idle, eye_pose(design, state))
        centre, out, forward = eye_point(model, pose)
        picture = gm.render(gm.quads(model, pose), texture, centre + out * length * distance + forward * length * .6, centre,
                            size=(420, 320), fov=30, glow=shine, background=background, tint=tint)
        ImageDraw.Draw(picture).text((6, 4), f'{identifier} {variant} {label}', fill=(235, 235, 235))
        tiles.append(picture)

    for state in ('calm', 'hunt', 'blink', 'closed', 'alert', 'flee'):
        tile(state, state if state != 'hunt' or design['iris']['hunt'] else 'hunt look', shine=glow if state == 'hunt' else None)
    tile('hunt' if glow else 'calm', 'at night' + (' (glow)' if glow else ''), background=(8, 10, 16), tint=(70, 76, 98), shine=glow)
    # The head round the eye, from where a player stands.
    pose = gm.merge(idle, eye_pose(design, 'calm'))
    centre, out, forward = eye_point(model, pose)
    faces = gm.quads(model, pose)
    near = np.concatenate([f[0] for f in faces])
    near = near[np.linalg.norm(near - centre, axis=1) < 6 * length]
    middle = near.mean(0)
    radius = float(np.linalg.norm(near - middle, axis=1).max())
    wide = gm.render(faces, texture, middle + out * radius * 2.0 + forward * radius * 1.2 + np.array([0, radius * .5, 0]), middle,
                     size=(420, 320), fov=34)
    ImageDraw.Draw(wide).text((6, 4), 'calm, head', fill=(235, 235, 235))
    tiles.append(wide)
    picture = Image.new('RGB', (420 * 4, 320 * 2))
    for i, one in enumerate(tiles):
        picture.paste(one, (420 * (i % 4), 320 * (i // 4)))
    return picture


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for identifier in [i for i in sys.argv[1:] if i in DESIGNS] or sorted(DESIGNS):
        sheet(identifier).save(OUT / f'{identifier}.png')
        print('wrote', OUT / f'{identifier}.png')


if __name__ == '__main__':
    main()
