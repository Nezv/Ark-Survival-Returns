"""Models page of the showcase: every authored 3D asset, live in the browser.

Machines (build_machine_assets.py) run their animation specs; armour sets turn on a walking player; the
authored weapons (build_weapon_models.py) and the tall benches (build_station_assets.py) turn in place. Everything is baked here from the shipped files into
textured quads (world space: block pixels, y up), so the page only draws them: three.js from jsDelivr, loaded
when the Models page opens. Each card also carries a static render, shown until (or instead of) the 3D view.
build_showcase.py calls section().
"""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

import accessory_art as A
import model_mesh as M

ARK = Path(__file__).resolve().parents[1]
ASSETS = ARK / 'src/main/resources/assets/arksurvivalreturns'
MACHINES = [('mechanical_press', 'Mechanical Press', 'Presses metal sheets. The ram slams down 6 px, holds, then eases back; the cog on top always turns while heated.'),
            ('milling_machine', 'Milling Machine', 'Mills pipes. The spindle idles slowly and spins four times faster while the quill plunges into the workpiece.'),
            ('cutting_machine', 'Cutting Machine', 'Cuts screws. A guarded blade spins nonstop while heated and the hinged arm swings down to cut.')]
WEAPONS = [('bronze_longsword', 'Bronze Longsword', 'Faceted pommel, wrapped grip, rolled crossguard and a fullered blade with bright edges.'),
           ('bronze_hammer', 'Bronze Hammer', 'A wrapped haft, socket langets and a flared two-faced head.'),
           ('keratin_spear', 'Keratin Spear', 'A leaf-shaped keratin head on a mid-ridge, a fiber binding and an ochre-dyed grip.')]
STATIONS = [('saddlery', 'Saddlery', "A saddler's bench under a saddle horse: the bronze saddle with its blanket, stirrup and girth, "
             'the hide and steel blankets, saddlebags, a bedroll, and the round knife, mallet, awl, thread and shears.')]
ARMOUR_PIECES = ('helmet', 'chestplate', 'leggings', 'boots')
WALK = {'right_arm': (-18, 0, 4), 'left_arm': (18, 0, -4), 'right_leg': (16, 0, 0), 'left_leg': (-16, 0, 0)}


def r3(values):
    return [round(float(v), 3) for v in values]


class Asset:
    """Parts with meshes grouped by texture; vertices are stored relative to the part's pivot."""

    def __init__(self, ident, title, group, note, chip):
        self.data = {'id': ident, 'title': title, 'group': group, 'note': note, 'chip': chip,
                     'textures': [], 'parts': [], 'spin': True}
        self._tex = {}

    def texture(self, key, image, uri):
        if key not in self._tex:
            self._tex[key] = len(self.data['textures'])
            self.data['textures'].append(uri(image, 'PNG'))
        return self._tex[key]

    def part(self, name, pivot=(0, 0, 0), parent=None, anim=None):
        self.data['parts'].append({'name': name, 'pivot': r3(pivot), 'parent': parent, 'anim': anim, 'mesh': {}})
        return len(self.data['parts']) - 1

    def quad(self, part, tex, corners, uvs):
        """corners are counter-clockwise seen from outside, in world space."""
        p = self.data['parts'][part]
        mesh = p['mesh'].setdefault(str(tex), {'p': [], 'uv': []})
        for (x, y, z), (u, v) in zip(corners, uvs):
            mesh['p'] += r3((x - p['pivot'][0], y - p['pivot'][1], z - p['pivot'][2]))
            mesh['uv'] += [round(float(u), 5), round(1 - float(v), 5)]

    def frame(self, lo, hi):
        self.data['centre'] = r3([(a + b) / 2 for a, b in zip(lo, hi)])
        self.data['radius'] = round(float(math.dist(lo, hi) / 2), 3)


def java_model(identifier):
    """A shipped Java model by id, textures resolved to images."""
    path = ASSETS / 'models' / (identifier.split(':')[1] + '.json')
    model = json.loads(path.read_text(encoding='utf-8'))
    images = {k: Image.open(ASSETS / 'textures' / (M.resolve(model, k).split(':')[1] + '.png')).convert('RGBA')
              for k in model.get('textures', {}) if not M.resolve(model, k).startswith('#')}
    return model, images


def add_java(asset, part, model, images, uri, offset=(0, 0, 0), bounds=None):
    for key, corners, uvs, _ in M.java_quads(model):
        tex = asset.texture(M.resolve(model, key), images[key], uri)
        corners = [(x + offset[0], y + offset[1], z + offset[2]) for x, y, z in corners]
        # The game's order is clockwise from outside; the page draws counter-clockwise front faces.
        asset.quad(part, tex, corners[::-1], uvs[::-1])
        if bounds is not None:
            bounds += corners


def extent(points):
    arr = np.array(points, dtype=float)
    return arr.min(axis=0), arr.max(axis=0)


# ------------------------------------------------------------------------------------------ machines

def machine(ident, title, note, uri):
    import build_machine_assets as B
    spec = json.loads((ASSETS / 'machines' / f'{ident}.json').read_text(encoding='utf-8'))
    asset = Asset(ident, title, 'machines', note, 'Iron Age (planned)')
    asset.data.update(machine={'rpm': spec['rpm_by_heat']})
    points = []
    base = asset.part('base')
    model, images = java_model(spec['model'])
    add_java(asset, base, model, images, uri, bounds=points)
    work = B.model_json(spec['workpiece'])
    work_images = {k: Image.open(ASSETS / 'textures' / (v.split(':')[1] + '.png')).convert('RGBA')
                   for k, v in work['textures'].items()}
    add_java(asset, base, work, work_images, uri)
    index = {}
    for p in spec['parts']:  # parents come first in the spec
        anim = {k: p[k] for k in ('mode', 'axis', 'drive', 'spin_idle', 'spin_proc', 'cycle_ticks', 'keys',
                                  'heat_angles', 'jitter') if k in p}
        parent = index.get(p.get('parent'), base)
        index[p['name']] = asset.part(p['name'], p['pivot'], parent, anim)
        model, images = java_model(p['model'])
        add_java(asset, index[p['name']], model, images, uri, bounds=points)
    asset.frame(*extent(points))
    poster = B.render(B.scene(ident, 6, 'heated'), size=300)[0]
    return asset.data, poster


# ------------------------------------------------------------------------------------------ weapons

def weapon(ident, title, note, uri):
    asset = Asset(ident, title, 'weapons', note, 'In game')
    model, images = java_model(f'arksurvivalreturns:item/{ident}_3d')
    points = []
    add_java(asset, asset.part('weapon'), model, images, uri, bounds=points)
    asset.frame(*extent(points))
    asset.data['view'] = {'yaw': 200, 'pitch': 18}
    poster = M.render(M.java_quads(model), images, yaw=200, pitch=18, size=300)
    return asset.data, poster


# ------------------------------------------------------------------------------------------ stations

def station(ident, title, note, uri):
    """A bench two blocks tall, from its stacked item model; it opens on its front (north)."""
    asset = Asset(ident, title, 'stations', note, 'In game')
    model, images = java_model(f'arksurvivalreturns:block/station/{ident}_item')
    points = []
    add_java(asset, asset.part('station'), model, images, uri, bounds=points)
    asset.frame(*extent(points))
    asset.data['view'] = {'yaw': 30, 'pitch': 22}
    poster = M.render(M.java_quads(model), images, yaw=30, pitch=22, size=300)
    return asset.data, poster


# ------------------------------------------------------------------------------------------ armour

def to_world(v):
    """Entity model space (+x to the wearer's left, +y down, -z forward) to page space (y up, facing +z)."""
    return (float(v[0]), -float(v[1]), -float(v[2]))


def armour(ident, title, note, chip, layers, uri):
    """A set on a walking player: layers is [(part, part-space quads, texture key, PIL image)]."""
    asset = Asset(ident, title, 'armour', note, chip)
    skin = A._vanilla('assets/minecraft/textures/entity/player/wide/steve.png')
    parts, points = {}, []
    for part, pose in A.PART_POSE.items():
        swing = WALK.get(part)
        anim = {'mode': 'swing', 'axis': 'x', 'amp': swing[0]} if swing else None
        parts[part] = asset.part(part, to_world(pose), None, anim)
    groups = [(p, qs, 'steve', skin) for p, qs in _player_part_space()] + layers
    for part, quads, key, image in groups:
        tex = asset.texture(key, image, uri)
        offset = np.array(A.PART_POSE[part], dtype=float)
        for verts, uvs in quads:
            world = [to_world(v + offset) for v in verts]
            asset.quad(parts[part], tex, world, [tuple(uv) for uv in uvs])
            points += world
    asset.frame(*extent(points))
    asset.data['view'] = {'yaw': -28, 'pitch': 12}
    posed = [(p, A._to_root(p, qs, dict(WALK)), np.array(img.convert('RGBA'))) for p, qs, _, img in groups]
    posed_points = [v for _, quads, _ in posed for verts, _ in quads for v in verts]
    lo, hi = extent(posed_points)
    centre = (lo + hi) / 2
    scale = 264 / max(hi - lo)
    poster = A.rasterise(posed, (300, 300), -28, 12, scale, centre, None)
    return asset.data, poster


def _player_part_space():
    """player_quads() without the root offset: (part, part-space quads)."""
    out = []
    for part, quads, _ in A.player_quads(False, pose={}):
        offset = np.array(A.PART_POSE[part], dtype=float)
        out.append((part, [(verts - offset, uvs) for verts, uvs in quads]))
    return out


def authored_set(material):
    """Native worn models of any armour tier, with their declared textures."""
    groups = []
    for piece in ARMOUR_PIECES:
        model_path = ASSETS / f'armour/{material}_{piece}.json'
        model = json.loads(model_path.read_text(encoding='utf-8'))
        texture = model['texture']
        namespace, path = texture.split(':', 1)
        assert namespace == 'arksurvivalreturns', texture
        image = Image.open(ASSETS / path).convert('RGBA')
        for part, root_quads, _ in A.worn_quads(model, image, pose={}, arms='wide'):
            offset = np.array(A.PART_POSE[part], dtype=float)
            groups.append((part, [(verts - offset, uvs) for verts, uvs in root_quads], f'{material}_{piece}', image))
    return groups


# ------------------------------------------------------------------------------------------ page

def card(asset, poster, uri, e):
    controls = ''
    if asset['group'] == 'machines':
        controls = ('<div class="heat" role="group" aria-label="Heat">' + ''.join(
            f'<button type="button" data-heat="{k}" aria-pressed="{str(k == "heated").lower()}">{label}</button>'
            for k, label in (('none', 'Cold'), ('heated', 'Heated'), ('superheated', 'Superheated'))) + '</div>')
    return f'''<article class="dino model-card" data-model="{e(asset['id'])}">
  <div class="model-view"><img class="model-poster" loading="lazy" src="{uri(poster)}" alt="{e(asset['title'])} render"><canvas aria-label="{e(asset['title'])}, drag to turn"></canvas></div>
  <div class="dino-body">
    <div class="dino-head"><h3>{e(asset['title'])}</h3><span class="chip">{e(asset['chip'])}</span></div>
    <p class="muted">{e(asset['note'])}</p>{controls}
  </div>
</article>'''


def section(uri, e):
    assets = {'machines': [], 'stations': [], 'armour': [], 'weapons': []}
    for ident, title, note in MACHINES:
        assets['machines'].append(machine(ident, title, note, uri))
    for ident, title, note in STATIONS:
        assets['stations'].append(station(ident, title, note, uri))
    steel = authored_set('steel')
    if steel:
        assets['armour'].append(armour('steel_armour', 'Steel armour', 'The Iron Age knight: a great helm, keeled '
                                       'breastplate, lamed pauldrons, tassets, knee cops and sabatons.',
                                       'Iron Age (planned)', steel, uri))
    assets['armour'].append(armour('bronze_armour', 'Bronze armour', 'A red-crested bronze helmet with long '
                                   'cheek guards, a russet cuirass and diagonal baldric, segmented golden shoulders, red undercloth and tall greaves.',
                                   '3D design', authored_set('bronze'), uri))
    assets['armour'].append(armour('keratin_armour', 'Keratin armour', 'A hooded prehistoric hide suit: a '
                                   'ribbed keratin face guard, broad carapace shoulder scales, horn-bound bracers and enclosed boots. No feathers.',
                                   '3D design', authored_set('keratin'), uri))
    for ident, title, note in WEAPONS:
        assets['weapons'].append(weapon(ident, title, note, uri))
    data = [a for group in assets.values() for a, _ in group]
    html = {key: '\n'.join(card(a, poster, uri, e) for a, poster in group) for key, group in assets.items()}
    return {'MODELS_MACHINES': html['machines'], 'MODELS_STATIONS': html['stations'], 'MODELS_ARMOUR': html['armour'],
            'MODELS_WEAPONS': html['weapons'],
            'MODELS_DATA': json.dumps(data, separators=(',', ':')), 'MODELS_COUNT': len(data)}
