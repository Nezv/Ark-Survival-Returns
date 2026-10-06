"""Inventory icons for any vanilla or Ark item, drawn the way a slot shows them, as 32x32 RGBA images.

Flat items stack their layer textures, tinted where the game tints them (undyed leather, water bottles, vines).
Block items are rendered from their block model with the model's own gui display rotation and scale, Minecraft's
face shading (top 1.0, north/south 0.8, east/west 0.6, bottom 0.5) and nearest sampling. The special renderers get
close stand-ins: beds and chests become boxes of their material, banners a cloth on a pole, the shield its face.
Sources: the vanilla jar and the mod's assets (main, generated, and the design pack for planned blocks).

    icons = Icons(); image = icons.icon('minecraft:oak_stairs')
"""
import io
import json
import math
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

ARK = Path(__file__).resolve().parents[1]
JAR = ARK / 'build/moddev/artifacts/minecraft-patched-26.1.2.109-merged.jar'
ARK_ROOTS = [ARK / 'src/main/resources/assets/arksurvivalreturns', ARK / 'src/generated/resources/assets/arksurvivalreturns',
             ARK / 'design/prehistoric-camp/assets/arksurvivalreturns']
SIZE = 32
SHADE = {'up': 1.0, 'down': 0.5, 'north': 0.8, 'south': 0.8, 'east': 0.6, 'west': 0.6}
BLOCK_GUI = {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625, 0.625, 0.625]}
DYES = {'white': (0xF9, 0xFF, 0xFE), 'orange': (0xF9, 0x80, 0x1D), 'magenta': (0xC7, 0x4E, 0xBD), 'light_blue': (0x3A, 0xB3, 0xDA),
        'yellow': (0xFE, 0xD8, 0x3D), 'lime': (0x80, 0xC7, 0x1F), 'pink': (0xF3, 0x8B, 0xAA), 'gray': (0x47, 0x4F, 0x52),
        'light_gray': (0x9D, 0x9D, 0x97), 'cyan': (0x16, 0x9C, 0x9C), 'purple': (0x89, 0x32, 0xB8), 'blue': (0x3C, 0x44, 0xAA),
        'brown': (0x83, 0x54, 0x32), 'green': (0x5E, 0x7C, 0x16), 'red': (0xB0, 0x2E, 0x26), 'black': (0x1D, 0x1D, 0x21)}
FOLIAGE = (72, 181, 24)


def argb(value):
    value &= 0xFFFFFFFF
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255)


class Icons:
    def __init__(self, overrides=None):
        self.jar = zipfile.ZipFile(JAR) if JAR.is_file() else None
        self.overrides = overrides or {}
        self.cache, self.models, self.textures = {}, {}, {}

    # ---------------------------------------------------------------------------------- file access

    def read(self, namespace, path):
        """A resource from assets/<namespace>/<path>, or None."""
        if namespace == 'minecraft':
            override = ARK / 'src/main/resources/assets/minecraft' / path
            if override.is_file():  # a vanilla texture the mod repaints: the hide
                return override.read_bytes()
            try:
                return self.jar.read(f'assets/minecraft/{path}') if self.jar else None
            except KeyError:
                return None
        for root in ARK_ROOTS:
            file = root / path
            if file.is_file():
                return file.read_bytes()
        return None

    def json(self, namespace, path):
        data = self.read(namespace, path)
        return json.loads(data) if data else None

    def texture(self, name):
        if name not in self.textures:
            ns, path = name.split(':') if ':' in name else ('minecraft', name)
            data = self.read(ns, f'textures/{path}.png')
            image = Image.open(io.BytesIO(data)).convert('RGBA') if data else None
            self.textures[name] = image.crop((0, 0, image.width, image.width)) if image else None  # animated: frame 0
        return self.textures[name]

    def model(self, name):
        """A model with its parents merged: textures and display by key, elements from the nearest model that has them."""
        if name in self.models:
            return self.models[name]
        ns, path = name.split(':') if ':' in name else ('minecraft', name)
        if path.startswith('builtin/'):
            self.models[name] = {'generated': path == 'builtin/generated', 'textures': {}, 'display': {}}
            return self.models[name]
        data = self.json(ns, f'models/{path}.json') or {}
        parent = self.model(data['parent']) if 'parent' in data else {'textures': {}, 'display': {}}
        merged = {**parent, **{k: v for k, v in data.items() if k not in ('textures', 'display', 'parent')}}
        merged['textures'] = {**parent.get('textures', {}), **data.get('textures', {})}
        merged['display'] = {**parent.get('display', {}), **data.get('display', {})}
        merged['generated'] = parent.get('generated', False) and 'elements' not in data
        self.models[name] = merged
        return merged

    def resolve(self, textures, name):
        for _ in range(8):
            if isinstance(name, dict):  # 26.1 texture objects: {"sprite": ..., "force_translucent": ...}
                name = name.get('sprite', '')
            if not name.startswith('#'):
                return name
            name = textures.get(name[1:], '')
        return None

    # ---------------------------------------------------------------------------------------- icons

    def icon(self, item):
        if item not in self.cache:
            try:
                self.cache[item] = self.overrides.get(item) or self.draw(item)
            except (KeyError, ValueError, TypeError, OSError, AttributeError, IndexError):
                self.cache[item] = None
        return self.cache[item]

    def draw(self, item):
        ns, path = item.split(':')
        if item == 'minecraft:water_bottle':
            return self.flat(['minecraft:item/potion_overlay', 'minecraft:item/potion'], [(0x38, 0x5D, 0xC6), None])
        definition = self.json(ns, f'items/{path}.json')
        if definition is None:
            texture = f'{ns}:item/{path}' if self.texture(f'{ns}:item/{path}') else f'{ns}:block/{path}'
            return self.flat([texture], [None]) if self.texture(texture) else None
        ref, tints, special = self.reference(definition['model'])
        if special:
            return self.special(path, special, ref)
        model = self.model(ref)
        colours = [self.tint(t) for t in tints]
        if model.get('generated'):
            layers = [self.resolve(model['textures'], model['textures'][f'layer{i}']) for i in range(8) if f'layer{i}' in model['textures']]
            return self.flat(layers, colours)
        if model.get('elements'):
            return self.render(model, colours)
        particle = self.resolve(model['textures'], model['textures'].get('particle', ''))
        return self.flat([particle], [None]) if particle else None

    def reference(self, node):
        """(model id, tints, special renderer) from an item definition's model tree, preferring the gui case."""
        kind = node.get('type', '').split(':')[-1]
        if kind == 'model':
            return node['model'], node.get('tints', []), None
        if kind == 'special':
            return node.get('base'), [], node.get('model', {})
        if kind == 'select':
            for case in node.get('cases', []):
                when = case.get('when')
                if node.get('property', '').endswith('display_context') and ('gui' in when if isinstance(when, list) else when == 'gui'):
                    return self.reference(case['model'])
            return self.reference(node.get('fallback') or node['cases'][0]['model'])
        if kind == 'condition':
            return self.reference(node.get('on_false') or node['on_true'])
        if kind == 'range_dispatch':
            return self.reference(node.get('fallback') or node['entries'][0]['model'])
        if kind == 'composite':
            return self.reference(node['models'][0])
        return self.reference(node['fallback']) if 'fallback' in node else (None, [], None)

    def tint(self, spec):
        if not isinstance(spec, dict):
            return None
        for key in ('default', 'value'):
            if key in spec and isinstance(spec[key], int):
                return argb(spec[key])
        if spec.get('type', '').endswith(('grass', 'foliage')):
            return FOLIAGE
        return None

    def flat(self, layers, colours):
        out = Image.new('RGBA', (SIZE, SIZE))
        for i, name in enumerate(layers):
            texture = self.texture(name) if name else None
            if texture is None:
                continue
            layer = texture.resize((SIZE, SIZE), Image.Resampling.NEAREST)
            colour = colours[i] if i < len(colours) else None
            if colour:
                arr = np.array(layer).astype(float)
                arr[..., :3] *= np.array(colour) / 255
                layer = Image.fromarray(arr.clip(0, 255).astype(np.uint8))
            out.alpha_composite(layer)
        return out if out.getbbox() else None

    def special(self, path, spec, base):
        kind = spec.get('type', '').split(':')[-1]
        colour = next((c for c in sorted(DYES, key=len, reverse=True) if path.startswith(c + '_')), None)
        if kind == 'bed' and colour:
            return self.box(f'minecraft:block/{colour}_wool', f'minecraft:block/{colour}_wool', 9)
        if kind == 'banner':
            return self.banner(DYES.get(colour or 'white'))
        if kind == 'shield':
            face = self.texture('minecraft:entity/shield/shield_base_nopattern') or self.texture('minecraft:entity/shield_base_nopattern')
            if face is None:
                return None
            scale = face.width // 64
            part = face.crop((2 * scale, 2 * scale, 14 * scale, 24 * scale)).resize((12, 22), Image.Resampling.NEAREST)
            out = Image.new('RGBA', (SIZE, SIZE))
            out.alpha_composite(part.resize((16, 29), Image.Resampling.NEAREST), (8, 1))
            return out
        model = self.model(base) if base else {'textures': {}}
        particle = self.resolve(model['textures'], model['textures'].get('particle', '')) or 'minecraft:block/oak_planks'
        return self.box(particle, particle, 14)

    def box(self, top, side, height):
        """A box of one material, the stand-in for beds, chests and pots."""
        model = {'textures': {'top': top, 'side': side}, 'display': {},
                 'elements': [{'from': [1, 0, 1], 'to': [15, height, 15],
                               'faces': {f: {'texture': '#top' if f in ('up', 'down') else '#side'}
                                         for f in ('north', 'south', 'east', 'west', 'up', 'down')}}]}
        return self.render(model, [])

    def banner(self, colour):
        out = Image.new('RGBA', (SIZE, SIZE))
        px = out.load()
        pole, dark = (0x6B, 0x4A, 0x2B, 255), (0x4A, 0x32, 0x1C, 255)
        for y in range(2, 31):
            px[15, y] = pole
            px[16, y] = dark
        for x in range(6, 26):
            px[x, 3] = pole
            px[x, 4] = dark
        for y in range(5, 27):
            for x in range(8, 24):
                shade = 0.82 if x in (8, 23) or y == 26 else 1.0 - (y - 5) * 0.006
                px[x, y] = tuple(int(c * shade) for c in colour) + (255,)
        return out

    def render(self, model, colours):
        """A block model through its gui display transform onto a 32x32 canvas."""
        display = model.get('display', {}).get('gui', BLOCK_GUI)
        rx, ry, rz = (math.radians(a) for a in display.get('rotation', [0, 0, 0]))
        cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
        rot = (np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
               @ np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]))
        scale = np.array(display.get('scale', [1, 1, 1]), dtype=float)
        shift = np.array(display.get('translation', [0, 0, 0]), dtype=float)
        pixels = np.zeros((SIZE, SIZE, 4), dtype=np.uint8)
        depth = np.full((SIZE, SIZE), -np.inf)
        unit = SIZE / 16
        for element in model.get('elements', []):
            x, y, z = element['from']
            X, Y, Z = element['to']
            corners = {'north': [(X, y, z), (x, y, z), (x, Y, z), (X, Y, z)], 'south': [(x, y, Z), (X, y, Z), (X, Y, Z), (x, Y, Z)],
                       'west': [(x, y, z), (x, y, Z), (x, Y, Z), (x, Y, z)], 'east': [(X, y, Z), (X, y, z), (X, Y, z), (X, Y, Z)],
                       'up': [(x, Y, Z), (X, Y, Z), (X, Y, z), (x, Y, z)], 'down': [(x, y, z), (X, y, z), (X, y, Z), (x, y, Z)]}
            for face, spec in element.get('faces', {}).items():
                name = self.resolve(model['textures'], spec.get('texture', ''))
                texture = self.texture(name) if name else None
                if texture is None:
                    continue
                v = np.array(corners[face], dtype=float)
                if 'rotation' in element:
                    r = element['rotation']
                    axis, angle = 'xyz'.index(r['axis']), math.radians(r['angle'])
                    a, b = [i for i in range(3) if i != axis]
                    if axis == 1:
                        a, b = b, a
                    m = np.eye(3)
                    m[a, a] = m[b, b] = math.cos(angle)
                    m[a, b], m[b, a] = -math.sin(angle), math.sin(angle)
                    origin = np.array(r['origin'], dtype=float)
                    v = (v - origin) @ m.T + origin
                v = ((v - 8) * scale) @ rot.T + shift
                normal = np.cross(v[1] - v[0], v[2] - v[0])
                if normal[2] <= 1e-9:
                    continue
                u0, v0, u1, v1 = spec.get('uv', self.default_uv(face, element))
                uv = np.roll(np.array([(u0, v1), (u1, v1), (u1, v0), (u0, v0)], dtype=float) / 16, spec.get('rotation', 0) // 90, axis=0)
                tex = np.array(texture)
                colour = colours[spec['tintindex']] if 'tintindex' in spec and spec['tintindex'] < len(colours) and colours[spec['tintindex']] else None
                shade = SHADE[face] if element.get('shade', True) else 1.0
                screen = np.stack([v[:, 0] * unit + SIZE / 2, SIZE / 2 - v[:, 1] * unit, v[:, 2]], axis=1)
                for tri in ((0, 1, 2), (0, 2, 3)):
                    self.raster(pixels, depth, screen[list(tri)], uv[list(tri)], tex, shade, colour)
        image = Image.fromarray(pixels)
        return image if image.getbbox() else None

    @staticmethod
    def default_uv(face, element):
        (x, y, z), (X, Y, Z) = element['from'], element['to']
        return {'north': (16 - X, 16 - Y, 16 - x, 16 - y), 'south': (x, 16 - Y, X, 16 - y), 'west': (z, 16 - Y, Z, 16 - y),
                'east': (16 - Z, 16 - Y, 16 - z, 16 - y), 'up': (x, z, X, Z), 'down': (x, 16 - Z, X, 16 - z)}[face]

    @staticmethod
    def raster(pixels, depth, tri, tuv, tex, shade, colour):
        lo = np.maximum([0, 0], np.floor(tri[:, :2].min(axis=0)).astype(int))
        hi = np.minimum([SIZE - 1, SIZE - 1], np.ceil(tri[:, :2].max(axis=0)).astype(int))
        if (lo > hi).any():
            return
        xx, yy = np.meshgrid(np.arange(lo[0], hi[0] + 1) + .5, np.arange(lo[1], hi[1] + 1) + .5)
        a, b, c = tri[:, :2]
        den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
        if abs(den) < 1e-9:
            return
        w0 = ((b[1] - c[1]) * (xx - c[0]) + (c[0] - b[0]) * (yy - c[1])) / den
        w1 = ((c[1] - a[1]) * (xx - c[0]) + (a[0] - c[0]) * (yy - c[1])) / den
        w2 = 1 - w0 - w1
        zz = w0 * tri[0, 2] + w1 * tri[1, 2] + w2 * tri[2, 2]
        region = depth[lo[1]:hi[1] + 1, lo[0]:hi[0] + 1]
        coords = w0[..., None] * tuv[0] + w1[..., None] * tuv[1] + w2[..., None] * tuv[2]
        tx = np.clip((coords[..., 0] * tex.shape[1]).astype(int), 0, tex.shape[1] - 1)
        ty = np.clip((coords[..., 1] * tex.shape[0]).astype(int), 0, tex.shape[0] - 1)
        rgba = tex[ty, tx].astype(float)
        rgba[..., :3] *= shade
        if colour:
            rgba[..., :3] *= np.array(colour) / 255
        mask = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6) & (zz > region) & (rgba[..., 3] > 16)
        region[mask] = zz[mask]
        pixels[lo[1]:hi[1] + 1, lo[0]:hi[0] + 1][mask] = rgba[mask].clip(0, 255).astype(np.uint8)
