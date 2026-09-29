"""Shared mesh helpers for the showcase's 3D Models page and the offline previews.

java_quads() bakes a Minecraft block/item model's elements into textured quads (element rotation applied, the
game's per-face vertex order and UVs), and render() draws quads with a painter's algorithm, one polygon per
texel, sampling the real textures. Coordinates stay in model pixels (1/16 block), y up, like the game.
"""
import math

from PIL import Image, ImageDraw

# Face corners in the game's order (top-left, top-right, bottom-right, bottom-left, seen from outside), as
# functions of the element's (x1, y1, z1, x2, y2, z2).
CORNERS = {
    'north': lambda a, b: [(b[0], b[1], a[2]), (a[0], b[1], a[2]), (a[0], a[1], a[2]), (b[0], a[1], a[2])],
    'south': lambda a, b: [(a[0], b[1], b[2]), (b[0], b[1], b[2]), (b[0], a[1], b[2]), (a[0], a[1], b[2])],
    'west': lambda a, b: [(a[0], b[1], a[2]), (a[0], b[1], b[2]), (a[0], a[1], b[2]), (a[0], a[1], a[2])],
    'east': lambda a, b: [(b[0], b[1], b[2]), (b[0], b[1], a[2]), (b[0], a[1], a[2]), (b[0], a[1], b[2])],
    'up': lambda a, b: [(a[0], b[1], a[2]), (b[0], b[1], a[2]), (b[0], b[1], b[2]), (a[0], b[1], b[2])],
    'down': lambda a, b: [(a[0], a[1], b[2]), (b[0], a[1], b[2]), (b[0], a[1], a[2]), (a[0], a[1], a[2])],
}
AUTO_UV = {
    'north': lambda a, b: (16 - b[0], 16 - b[1], 16 - a[0], 16 - a[1]),
    'south': lambda a, b: (a[0], 16 - b[1], b[0], 16 - a[1]),
    'west': lambda a, b: (a[2], 16 - b[1], b[2], 16 - a[1]),
    'east': lambda a, b: (16 - b[2], 16 - b[1], 16 - a[2], 16 - a[1]),
    'up': lambda a, b: (a[0], a[2], b[0], b[2]),
    'down': lambda a, b: (a[0], 16 - b[2], b[0], 16 - a[2]),
}


def rotate(p, axis, degrees, origin):
    """Right-hand rotation about an axis through origin, as Minecraft element rotations."""
    c, s = math.cos(math.radians(degrees)), math.sin(math.radians(degrees))
    x, y, z = p[0] - origin[0], p[1] - origin[1], p[2] - origin[2]
    if axis == 'x':
        y, z = y * c - z * s, y * s + z * c
    elif axis == 'y':
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + origin[0], y + origin[1], z + origin[2])


def element_rotation(element):
    """The element's rotations as (axis, degrees, origin) steps, single-axis or the 26.1 Euler XYZ form."""
    r = element.get('rotation')
    if not r:
        return []
    if 'axis' in r:
        return [(r['axis'], r['angle'], r['origin'])]
    # EulerXYZ: the matrix Rx * Ry * Rz, so the vertex turns about z first.
    return [(axis, r.get(axis, 0), r['origin']) for axis in ('z', 'y', 'x') if r.get(axis, 0)]


def java_quads(model):
    """[(texture key, 4 corners, 4 uvs in 0..1, face name)] for every element face of a Java model."""
    out = []
    for element in model.get('elements', []):
        a, b = element['from'], element['to']
        steps = element_rotation(element)
        for face, spec in element.get('faces', {}).items():
            corners = CORNERS[face](a, b)
            for axis, degrees, origin in steps:
                corners = [rotate(p, axis, degrees, origin) for p in corners]
            u1, v1, u2, v2 = spec.get('uv') or AUTO_UV[face](a, b)
            uvs = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
            turn = (spec.get('rotation', 0) // 90) % 4  # the texture turns clockwise on the face
            uvs = [uvs[(i - turn) % 4] for i in range(4)]
            out.append((spec['texture'].lstrip('#'), corners, [(u / 16, v / 16) for u, v in uvs], face))
    return out


def resolve(model, key):
    textures = model.get('textures', {})
    seen = set()
    while key in textures and key not in seen:
        seen.add(key)
        value = textures[key]
        if not value.startswith('#'):
            return value
        key = value[1:]
    return key


# --------------------------------------------------------------------------------------------- preview

def _sub(a, b): return (a[0] - b[0], a[1] - b[1], a[2] - b[2])
def _dot(a, b): return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
def _cross(a, b): return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def render(quads, images, yaw=-30, pitch=24, size=512, bg=(0, 0, 0, 0), ss=2):
    """Painter's algorithm, one polygon per texel; images maps a texture key to an RGBA PIL image."""
    a, p = math.radians(yaw), math.radians(pitch)
    d = (-math.cos(p) * math.sin(a), -math.sin(p), math.cos(p) * math.cos(a))
    r = _cross(d, (0, 1, 0))
    n = math.sqrt(_dot(r, r))
    r = (r[0] / n, r[1] / n, r[2] / n)
    u = _cross(r, d)
    light = (-0.35, 0.9, -0.5)
    polys = []
    for key, c, uv, _ in quads:
        normal = _cross(_sub(c[3], c[0]), _sub(c[1], c[0]))
        ln = math.sqrt(_dot(normal, normal))
        if ln < 1e-9 or _dot(normal, d) >= 0:
            continue
        lit = 0.55 + 0.45 * max(0.0, _dot(normal, light) / ln / math.sqrt(_dot(light, light)))
        image = images[key]
        w, h = image.size
        px = image.load()
        tu = abs(uv[1][0] - uv[0][0]) * w
        tv = abs(uv[3][1] - uv[0][1]) * h
        nu, nv = max(1, round(tu)), max(1, round(tv))
        for i in range(nu):
            for j in range(nv):
                s0, s1, t0, t1 = i / nu, (i + 1) / nu, j / nv, (j + 1) / nv
                sm, tm = (s0 + s1) / 2, (t0 + t1) / 2

                def at(s, t):  # bilinear over the corners TL, TR, BR, BL
                    top = [c[0][k] + (c[1][k] - c[0][k]) * s for k in range(3)]
                    bottom = [c[3][k] + (c[2][k] - c[3][k]) * s for k in range(3)]
                    return [top[k] + (bottom[k] - top[k]) * t for k in range(3)]
                uu = uv[0][0] + (uv[1][0] - uv[0][0]) * sm
                vv = uv[0][1] + (uv[3][1] - uv[0][1]) * tm
                col = px[min(w - 1, max(0, int(uu * w))), min(h - 1, max(0, int(vv * h)))]
                if col[3] < 128:
                    continue
                pts = [at(s0, t0), at(s1, t0), at(s1, t1), at(s0, t1)]
                centre = at(sm, tm)
                polys.append((_dot(centre, d), [(_dot(q, r), -_dot(q, u)) for q in pts],
                              tuple(min(255, int(ch * lit)) for ch in col[:3])))
    polys.sort(key=lambda q: -q[0])
    xs = [x for _, pts, _ in polys for x, _ in pts]
    ys = [y for _, pts, _ in polys for _, y in pts]
    k = 0.86 * size / max(max(xs) - min(xs), max(ys) - min(ys))
    ox, oy = size / 2 - k * (max(xs) + min(xs)) / 2, size / 2 - k * (max(ys) + min(ys)) / 2
    img = Image.new('RGBA', (size * ss, size * ss), bg)
    draw = ImageDraw.Draw(img)
    for _, pts, col in polys:
        draw.polygon([((x * k + ox) * ss, (y * k + oy) * ss) for x, y in pts], fill=col + (255,), outline=col + (255,))
    return img.resize((size, size), Image.Resampling.LANCZOS)
