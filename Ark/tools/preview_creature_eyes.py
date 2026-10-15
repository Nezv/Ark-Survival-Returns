"""Close-ups of the designed eyes (build_creature_eyes.DESIGNS) in the states the client gives them.

Each species is posed in its idle clip and its eyes set the way client/CreatureEyes.java sets them: lids scaled
from their outer edge, the brow lowered and tilted, the slit narrowed or widened, the yellow or the red eyeball.
Renders follow GeckoLib's own baking (geckolib_mesh), nearest texel, so the eye art is seen as the game draws it.

    python tools/preview_creature_eyes.py [species ...]      -> Ark/design/creatures/eyes/<species>.png
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

import geckolib_mesh as gm
from build_creature_eyes import DESIGNS, LID_REST, LID_TUCK

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/arksurvivalreturns'
OUT = ROOT / 'design/creatures/eyes'
IDLE = {'tyrannosaurus': 'Rex-Idle', 'giganotosaurus': 'Giganotosaurus-Idle', 'spinosaurus': 'Spino-Idle',
        'ceratosaurus': 'Ceratosaurus_Idle', 'acrocanthosaurus': 'Acro_Idle', 'carnotaurus': 'Carno-Idle'}
# The expressions of client/CreatureEyes.java: lid cover (eye heights), brow drop (eye heights) and tilt
# (degrees, front end down), slit width (scale), red eyes.
STATES = {
    'calm': dict(upper=.30, lower=.12, drop=0, tilt=0, slit=1.0, red=False),
    'alert': dict(upper=.22, lower=.10, drop=.06, tilt=6, slit=.7, red=False),
    'hunt': dict(upper=.40, lower=.20, drop=.12, tilt=8, slit=.45, red=True),
    'blink': dict(upper=.55, lower=.30, drop=.03, tilt=0, slit=1.0, red=False),
    'closed': dict(upper=.70, lower=.44, drop=.06, tilt=0, slit=1.0, red=False),
    'flee': dict(upper=.10, lower=.04, drop=-.10, tilt=-4, slit=1.3, red=False),
}


def eye_pose(design, state):
    """Bone snapshot values of one expression, as CreatureRenderer applies them."""
    e = STATES[state]
    height = design['size'][1]
    pose = {}
    for side in ('l', 'r'):
        pose[f'ark_eye_lid_upper_{side}'] = {'scale': (1, (e['upper'] + LID_TUCK['upper']) / (LID_REST['upper'] + LID_TUCK['upper']), 1)}
        pose[f'ark_eye_lid_lower_{side}'] = {'scale': (1, (e['lower'] + LID_TUCK['lower']) / (LID_REST['lower'] + LID_TUCK['lower']), 1)}
        pose[f'ark_eye_brow_{side}'] = {'pos': (0, -e['drop'] * height, 0), 'rot': (math.radians(e['tilt']), 0, 0)}
        pose[f'ark_eye_pupil_{side}'] = {'scale': (1, 1, e['slit'])}
        pose[f'ark_eyeball_calm_{side}'] = {'hide': e['red']}
        pose[f'ark_eyeball_hunt_{side}'] = {'hide': not e['red']}
    return pose


def descendants(model, root):
    kids = {}
    for b in model['bones']:
        kids.setdefault(b.get('parent'), []).append(b['name'])
    found, stack = set(), [root]
    while stack:
        name = stack.pop()
        found.add(name)
        stack.extend(kids.get(name, []))
    return found


def eye_point(model, pose, side='l'):
    """The eye's centre and its outward direction in the posed model."""
    world, _ = gm.bone_matrices(model, pose)
    bone = next(b for b in model['bones'] if b['name'] == f'ark_eye_{side}')
    m = world[bone['name']]
    pivot = np.array(bone['pivot'], float) * [-1, 1, 1]
    centre = m[:3, :3] @ pivot + m[:3, 3]
    sign = 1 if side == 'l' else -1
    return centre, m[:3, :3] @ np.array([sign, 0.0, 0.0]), m[:3, :3] @ np.array([0, 0, 1.0])


def sheet(identifier, variant='ivory'):
    design = DESIGNS[identifier]
    model = gm.load(ASSETS / f'geckolib/models/entity/{identifier}.geo.json')
    clip = json.loads((ASSETS / f'geckolib/animations/entity/{identifier}.animation.json').read_text())['animations'][IDLE[identifier]]
    idle = gm.clip_pose(clip, 0.0)
    texture = Image.open(ASSETS / f'textures/entity/{identifier}_{variant}.png')
    glow = Image.open(ASSETS / f'textures/entity/{identifier}_eyes.png')
    length = design['size'][0]
    tiles = []
    for state in ('calm', 'hunt', 'blink', 'closed', 'alert', 'flee'):
        pose = gm.merge(idle, eye_pose(design, state))
        centre, out, forward = eye_point(model, pose)
        faces = gm.quads(model, pose)
        near = gm.render(faces, texture, centre + out * length * 3.2 + forward * length * .6, centre, size=(420, 320), fov=30,
                         glow=glow if state == 'hunt' else None)
        ImageDraw.Draw(near).text((6, 4), f'{identifier} {variant} {state}', fill=(235, 235, 235))
        tiles.append(near)
    # Night, hunting: the glow over a dark scene; and the head from where a player stands.
    pose = gm.merge(idle, eye_pose(design, 'hunt'))
    centre, out, forward = eye_point(model, pose)
    faces = gm.quads(model, pose)
    night = gm.render(faces, texture, centre + out * length * 3.2 + forward * length * .6, centre, size=(420, 320), fov=30,
                      glow=glow, background=(8, 10, 16), tint=(70, 76, 98))
    ImageDraw.Draw(night).text((6, 4), 'hunt at night (glow)', fill=(235, 235, 235))
    tiles.append(night)
    pose = gm.merge(idle, eye_pose(design, 'calm'))
    centre, out, forward = eye_point(model, pose)
    faces = gm.quads(model, pose)
    wide = gm.render(faces, texture, centre + out * length * 7 + forward * length * 6 + np.array([0, -length * 2.5, 0]),
                     centre - forward * length * .8, size=(420, 320), fov=34)
    ImageDraw.Draw(wide).text((6, 4), 'calm, head', fill=(235, 235, 235))
    tiles.append(wide)
    picture = Image.new('RGB', (420 * 4, 320 * 2))
    for i, tile in enumerate(tiles):
        picture.paste(tile, (420 * (i % 4), 320 * (i // 4)))
    return picture


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for identifier in [i for i in sys.argv[1:] if i in DESIGNS] or sorted(DESIGNS):
        sheet(identifier).save(OUT / f'{identifier}.png')
        print('wrote', OUT / f'{identifier}.png')


if __name__ == '__main__':
    main()
