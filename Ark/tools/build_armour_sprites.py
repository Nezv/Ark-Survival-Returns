"""Armour item sprites drawn from the worn 3D models: Keratin, Bronze and Steel.

Each sprite is an orthographic three-quarter view of the shipped worn model (assets/.../armour/<tier>_<piece>.json
and its texture), the geometry the Models page and the armour layer use, shown like a piece on an armour stand
(arms and legs opened a little so their silhouettes part). The pipeline:
  1. texel noise inside each cube face of the texture is evened out (strong marks such as rivets, slits, straps
     and seams stay), because a texel ends up barely two pixels wide;
  2. the view is rasterised eight times over per side and lit per face (tops brightest, fronts lit, the turned
     side in shade); metal colours get a cylindrical sheen across each upright plate;
  3. each pixel takes the face covering most of it and that face's averaged colour, so plate edges stay crisp
     while slits, ridges, crests and straps keep the shapes the model gives them;
  4. pixel-art finish: a lit and a shaded rim on every plate, the darkest tones opened a little, the value range
     widened so mid-grey steel does not sink into the grey slot, a 12-colour palette, then a dark outline of the
     sprite's own colours and a softer line where a nearer part overlaps a farther one.

This module owns the twelve sprites and the tech icons drawn from them (Thick skin, Tin Can, Colossus, Harder):
build_early_armour.py and build_steel_armour.py render the geometry they just built through sprite(), and
build_keratin_items.py, build_bronze_age_art.py and build_iron_age_art.py take their armour sprites from here,
so the older 2D drawings cannot come back.

Run from Ark: python tools/build_armour_sprites.py
Writes textures/item/<tier>_<piece>.png, textures/gui/tech/icons/{armoured,tincan,colossus,steel_set}.png,
design/technology-tree/icons/keratin_armour.png and the review sheet design/armour/sprites.png.
"""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

import accessory_art as A

ARK = Path(__file__).resolve().parents[1]
ASSETS = ARK / 'src/main/resources/assets/arksurvivalreturns'
TECH = ASSETS / 'textures/gui/tech/icons'
DESIGN = ARK / 'design/armour'
TIERS = ('keratin', 'bronze', 'steel')
PIECES = ('helmet', 'chestplate', 'leggings', 'boots')
SIZE, SS, MARGIN = 32, 8, 1               # sprite size, supersampling per side, free pixels round the art

# Camera per piece (the rasteriser's yaw and pitch; the wearer's left side turns into the shade on the right) and
# the stand pose: arms and legs opened a little so they part from the body and from each other.
VIEW = {'helmet': (-32, 12), 'chestplate': (-24, 12), 'leggings': (-16, 8), 'boots': (-30, 22), 'set': (-24, 8)}
POSE = {'chestplate': {'right_arm': (0, 0, 14), 'left_arm': (0, 0, -14)},
        'leggings': {'right_leg': (0, 0, 10), 'left_leg': (0, 0, -10)},
        'boots': {'right_leg': (0, 0, 8), 'left_leg': (0, 0, -8)},
        'set': {'right_arm': (0, 0, 8), 'left_arm': (0, 0, -8), 'right_leg': (0, 0, 3), 'left_leg': (0, 0, -3)}}
LIGHT = np.array([-0.35, 0.7, 0.62])      # camera space (x right, y up, z to the viewer): upper left, in front
LIGHT = LIGHT / np.linalg.norm(LIGHT)
AMBIENT, DIFFUSE = 0.55, 0.55             # tops ~1.0, fronts ~0.85, the turned side ~0.55
SHEEN = {'keratin': 0.15, 'bronze': 0.34, 'steel': 0.42}    # metal sheen; horn is nearly matte
TEXEL_NOISE = 80                          # texture tones closer than this (RGB sum) are one surface
COVER = 0.45                              # share of a pixel the art must cover to be drawn
BEVEL = (1.1, 0.88)                       # each plate's edge toward the light, and away from it
LIFT = 0.8                                # gamma that opens the darkest hides and leathers a little
TONE_FROM, TONE_TO = (8, 92), (0.2, 0.85) # value percentiles of a sprite and where they may widen to
COLOURS = 12                              # palette of a finished sprite, before its outline tones
EDGE_DEPTH = 1.2                          # model units between overlapping parts that earn an inner line


# ------------------------------------------------------------------------------------------------ input

def load(tier, piece):
    """The shipped worn model and its texture."""
    model = json.loads((ASSETS / f'armour/{tier}_{piece}.json').read_text(encoding='utf-8'))
    texture = Image.open(ASSETS / f'textures/entity/armour/{tier}_{piece}.png').convert('RGBA')
    return model, texture


def face_rects(model):
    """Texture rectangles of every cube face (box UV of ModelPart.Cube)."""
    rects = set()
    for bone in model['bones']:
        for cube in bone['cubes']:
            (u, v), (w, h, d) = cube['uv'], cube['size']
            rects |= {(u + d, v, w, d), (u + d + w, v, w, d), (u, v + d, d, h), (u + d, v + d, w, h),
                      (u + d + w, v + d, d, h), (u + d + w + d, v + d, w, h)}
    return rects


def denoise(model, texture, passes=2, limit=TEXEL_NOISE):
    """Even out texel noise inside each cube face: a texel moves toward the neighbours that differ from it by less
    than `limit`, so rivets, slits, straps and seams stay sharp while brushed or grained surfaces settle."""
    tex = np.array(texture.convert('RGBA')).astype(float)
    for u, v, w, h in face_rects(model):
        if w < 2 and h < 2:
            continue
        region = tex[v:v + h, u:u + w, :3]
        for _ in range(passes):
            src = region.copy()
            for j in range(h):
                for i in range(w):
                    near = src[max(0, j - 1):j + 2, max(0, i - 1):i + 2].reshape(-1, 3)
                    region[j, i] = near[np.abs(near - src[j, i]).sum(axis=1) < limit].mean(axis=0)
    return Image.fromarray(np.clip(np.rint(tex), 0, 255).astype(np.uint8), 'RGBA')


def worn(tier, piece, pose, model=None, texture=None):
    """Root-space quads of one piece (wide arms) in a pose, its texture denoised."""
    if model is None or texture is None:
        model, texture = load(tier, piece)
    if not isinstance(model, dict):
        model = model.to_json()
    return A.worn_quads(model, denoise(model, texture), pose=pose, arms='wide')


# ---------------------------------------------------------------------------------------------- render

def _view(yaw, pitch):
    """The rasteriser's camera (accessory_art.rasterise): world x right, y up, z toward the viewer."""
    ya, pa = math.radians(yaw), math.radians(pitch)
    return np.array([[math.cos(ya), 0, math.sin(ya)],
                     [math.sin(ya) * math.sin(pa), math.cos(pa), -math.cos(ya) * math.sin(pa)],
                     [-math.sin(ya) * math.cos(pa), math.sin(pa), math.cos(ya) * math.cos(pa)]])


def raster(groups, yaw, pitch, size, margin, ss=SS):
    """Supersampled buffers fitted to the image: texture colour (unlit), face index, depth (larger is nearer),
    the faces' normals and their horizontal extent."""
    view = _view(yaw, pitch)
    faces = []
    for _part, quads, tex in groups:
        for verts, uvs in quads:
            cam = (verts * np.array([1, -1, -1])) @ view.T     # model +x left, +y down, -z front -> world
            normal = np.cross(cam[1] - cam[0], cam[2] - cam[0])
            length = np.linalg.norm(normal)
            if length > 1e-9 and normal[2] / length > -0.02:   # back faces never win the depth test
                faces.append((cam, uvs, tex, normal / length))
    points = np.concatenate([cam for cam, *_ in faces])
    lo, hi = points[:, :2].min(axis=0), points[:, :2].max(axis=0)
    scale = (size - 2 * margin) / max(hi - lo) * ss
    centre = (lo + hi) / 2
    width = size * ss
    colour = np.zeros((width, width, 4), dtype=np.uint8)
    face_id = np.full((width, width), -1, dtype=np.int32)
    depth = np.full((width, width), -np.inf)
    extent = np.zeros((len(faces), 2))
    for k, (cam, uvs, tex, _normal) in enumerate(faces):
        th, tw = tex.shape[:2]
        p = cam.copy()
        p[:, 0] = (p[:, 0] - centre[0]) * scale + width / 2
        p[:, 1] = width / 2 - (p[:, 1] - centre[1]) * scale
        extent[k] = p[:, 0].min(), p[:, 0].max()
        for tri in ((0, 1, 2), (0, 2, 3)):
            t, tuv = p[list(tri)], uvs[list(tri)]
            lo_px = np.maximum([0, 0], np.floor(t[:, :2].min(axis=0)).astype(int))
            hi_px = np.minimum([width - 1, width - 1], np.ceil(t[:, :2].max(axis=0)).astype(int))
            if (lo_px > hi_px).any():
                continue
            xx, yy = np.meshgrid(np.arange(lo_px[0], hi_px[0] + 1) + .5, np.arange(lo_px[1], hi_px[1] + 1) + .5)
            a, b, c = t[:, :2]
            den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if abs(den) < 1e-9:
                continue
            w0 = ((b[1] - c[1]) * (xx - c[0]) + (c[0] - b[0]) * (yy - c[1])) / den
            w1 = ((c[1] - a[1]) * (xx - c[0]) + (a[0] - c[0]) * (yy - c[1])) / den
            w2 = 1 - w0 - w1
            zz = w0 * t[0, 2] + w1 * t[1, 2] + w2 * t[2, 2]
            coords = w0[..., None] * tuv[0] + w1[..., None] * tuv[1] + w2[..., None] * tuv[2]
            col = tex[np.clip((coords[..., 1] * th).astype(int), 0, th - 1),
                      np.clip((coords[..., 0] * tw).astype(int), 0, tw - 1)]
            region = depth[lo_px[1]:hi_px[1] + 1, lo_px[0]:hi_px[0] + 1]
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            mask = inside & (zz > region + 1e-4) & (col[..., 3] > 25)
            region[mask] = zz[mask]
            colour[lo_px[1]:hi_px[1] + 1, lo_px[0]:hi_px[0] + 1][mask] = col[mask]
            face_id[lo_px[1]:hi_px[1] + 1, lo_px[0]:hi_px[0] + 1][mask] = k
    return colour, face_id, depth, np.array([n for *_, n in faces]), extent


def metal(rgb):
    """1 where a texture colour reads as polished metal (bright steel greys, warm bronze), else 0."""
    value = rgb.max(axis=-1)
    sat = np.where(value > 0, (value - rgb.min(axis=-1)) / np.maximum(value, 1e-6), 0)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    hue = np.degrees(np.arctan2(math.sqrt(3) * (g - b), 2 * r - g - b)) % 360
    return (((sat < 0.28) & (value > 0.3)) | ((hue > 26) & (hue < 60) & (sat > 0.3) & (value > 0.5))).astype(float)


def lighting(colour, face_id, normals, extent, sheen, ss=SS):
    """Lit colour per sample: the face light and, on metal, a cylindrical sheen across each upright plate wider
    than three pixels: a highlight a third of the way in from the lit side, falling off toward the far edge."""
    rgb = colour[..., :3].astype(float) / 255
    solid = face_id >= 0
    k = np.maximum(face_id, 0)
    factor = np.where(solid, (AMBIENT + DIFFUSE * np.maximum(0.0, normals @ LIGHT))[k], 0.0)
    x0, x1 = extent[k, 0], extent[k, 1]
    s = np.clip((np.arange(face_id.shape[1])[None, :] + 0.5 - x0) / np.maximum(x1 - x0, 1e-6), 0, 1)
    shape = 0.9 * np.maximum(0, 1 - np.abs(s - 0.3) / 0.22) - 0.6 * np.maximum(0, (s - 0.62) / 0.38)
    plate = solid & ((x1 - x0) >= 3 * ss) & (np.abs(normals[k, 1]) < 0.5)
    factor = factor * (1 + sheen * shape * metal(rgb) * plate)
    return np.clip(rgb * factor[..., None] * 255, 0, 255)


def reduce(lit, face_id, depth, size, ss=SS):
    """Down to the final size: every covered pixel takes the face covering most of it and that face's mean
    colour there, so plate edges stay crisp (no blended seams) while a texel's noise averages out."""
    out = np.zeros((size, size, 4), dtype=np.uint8)
    near = np.full((size, size), -np.inf)
    face = np.full((size, size), -1, dtype=np.int32)
    for y in range(size):
        for x in range(size):
            block = np.s_[y * ss:(y + 1) * ss, x * ss:(x + 1) * ss]
            covered = face_id[block] >= 0
            if covered.mean() < COVER:
                continue
            ids = face_id[block][covered]
            values, counts = np.unique(ids, return_counts=True)
            face[y, x] = values[counts.argmax()]
            out[y, x] = (*np.rint(lit[block][covered][ids == face[y, x]].mean(axis=0)).astype(np.uint8), 255)
            near[y, x] = float(np.median(depth[block][covered]))
    return out, near, face


# ---------------------------------------------------------------------------------------------- finish

def bevel(pixels, face):
    """A lit rim on every plate's upper-left edges and a shaded one on its lower-right edges, so plates, lames
    and straps separate as a pixel artist would draw them; then the darkest tones open a little."""
    out = pixels.astype(float)
    size = pixels.shape[0]
    for y in range(size):
        for x in range(size):
            f = face[y, x]
            if f < 0:
                continue
            lit = any(face[v, u] != f for v, u in ((y - 1, x), (y, x - 1)) if v >= 0 and u >= 0)
            shaded = any(face[v, u] != f for v, u in ((y + 1, x), (y, x + 1)) if v < size and u < size)
            if lit != shaded:
                out[y, x, :3] *= BEVEL[0] if lit else BEVEL[1]
    value = np.clip(out[..., :3], 0, 255) / 255
    shadow = (1 - value.max(axis=-1, keepdims=True)) ** 2           # only the darkest tones open up
    out[..., :3] = 255 * (value + (value ** LIFT - value) * shadow)
    return np.clip(np.rint(out), 0, 255).astype(np.uint8)


def contrast(pixels):
    """Widen the sprite's own value range (percentiles TONE_FROM) toward TONE_TO, hue and saturation kept, by a
    bounded amount: mid-grey steel would sink into the grey slot, while dark hide must stay dark."""
    out = pixels.copy()
    solid = pixels[..., 3] > 0
    rgb = pixels[..., :3].astype(float) / 255
    value = rgb.max(axis=-1)
    lo, hi = np.percentile(value[solid], TONE_FROM)
    to_lo, to_hi = max(min(lo, TONE_TO[0]), lo * 0.7), min(max(hi, TONE_TO[1]), hi * 1.25)
    target = np.interp(value, (lo, hi), (to_lo, to_hi))
    target = np.where(value < lo, value / max(lo, 1e-6) * to_lo, target)
    target = np.where(value > hi, to_hi + (value - hi) / max(1 - hi, 1e-6) * (1 - to_hi), target)
    scaled = rgb * (target / np.maximum(value, 1e-6))[..., None]
    out[..., :3] = np.where(solid[..., None], np.clip(np.rint(scaled * 255), 0, 255), pixels[..., :3])
    return out


def quantise(pixels, colours=COLOURS):
    """A small palette shared by the whole sprite (median cut over its own colours, no dithering)."""
    solid = pixels[..., 3] > 0
    rgb = Image.fromarray(np.where(solid[..., None], pixels[..., :3], 0).astype(np.uint8), 'RGB')
    reduced = np.array(rgb.quantize(colours + 1, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
                       .convert('RGB'))
    out = pixels.copy()
    out[..., :3] = np.where(solid[..., None], reduced, 0)
    return out


def darken(rgb, factor, toward=(18, 14, 16)):
    return tuple(int(c * factor + t * (1 - factor) * 0.6) for c, t in zip(rgb, toward))


def outline(pixels, near):
    """Dark silhouette ring of the art's own colours; a softer line where a nearer part overlaps a farther one."""
    out = pixels.copy()
    size = pixels.shape[0]
    solid = pixels[..., 3] > 0
    for y in range(size):
        for x in range(size):
            if not solid[y, x]:
                continue
            around = [(y + dy, x + dx) for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1))]
            rgb = tuple(int(c) for c in pixels[y, x, :3])
            if any(not (0 <= v < size and 0 <= u < size) or not solid[v, u] for v, u in around):
                out[y, x, :3] = darken(rgb, 0.42)
            elif any(near[v, u] - near[y, x] > EDGE_DEPTH for v, u in around):
                out[y, x, :3] = darken(rgb, 0.68)
    return out


def render(groups, view, sheen, size=SIZE, margin=MARGIN):
    colour, face_id, depth, normals, extent = raster(groups, *view, size, margin)
    pixels, near, face = reduce(lighting(colour, face_id, normals, extent, sheen), face_id, depth, size)
    return Image.fromarray(outline(quantise(contrast(bevel(pixels, face))), near), 'RGBA')


def sprite(tier, piece, model=None, texture=None):
    """The 32 px item sprite of one piece: the shipped model, or the one an authoring script just built."""
    return render(worn(tier, piece, POSE.get(piece, {}), model, texture), VIEW[piece], SHEEN[tier])


def set_icon(tier, size=64):
    """A 64 px tech icon of the whole set on a stand, drawn like the sprites (Colossus, Harder)."""
    pose = POSE['set']
    groups = [g for g in A.player_quads(pose=pose) if g[0] == 'head']    # the face inside open helmets
    for piece in PIECES:
        groups += worn(tier, piece, pose)
    return render(groups, VIEW['set'], SHEEN[tier], size, margin=2)


def tech_icon(image):
    """A 64 px tech icon from a 32 px sprite: nearest 2x, centred (as the build_*_art icon helpers do)."""
    big = image.resize((image.width * 2, image.height * 2), Image.Resampling.NEAREST)
    canvas = Image.new('RGBA', (64, 64))
    canvas.alpha_composite(big, ((64 - big.width) // 2, (64 - big.height) // 2))
    return canvas


def icons(sprites):
    """The tech icons drawn from the armour: Thick skin (armoured), Tin Can, Colossus and Harder (steel_set)."""
    return {'armoured': tech_icon(sprites[('keratin', 'chestplate')]),
            'tincan': tech_icon(sprites[('bronze', 'helmet')]),
            'colossus': set_icon('bronze'),
            'steel_set': set_icon('steel')}


# ---------------------------------------------------------------------------------------------- output

SLOT = (139, 139, 139, 255)


def slot(image, zoom):
    """The sprite on a vanilla inventory slot, as the player sees it."""
    cell = Image.new('RGBA', (18 * zoom, 18 * zoom), SLOT)
    draw = ImageDraw.Draw(cell)
    draw.rectangle((0, 0, 18 * zoom - 1, zoom - 1), fill=(55, 55, 55, 255))
    draw.rectangle((0, 0, zoom - 1, 18 * zoom - 1), fill=(55, 55, 55, 255))
    draw.rectangle((0, 17 * zoom, 18 * zoom - 1, 18 * zoom - 1), fill=(255, 255, 255, 255))
    draw.rectangle((17 * zoom, 0, 18 * zoom - 1, 18 * zoom - 1), fill=(255, 255, 255, 255))
    cell.alpha_composite(image.resize((16 * zoom, 16 * zoom), Image.Resampling.NEAREST), (zoom, zoom))
    return cell


def review(sprites, tech):
    """design/armour/sprites.png: every sprite at 8x, in slots at GUI scales 2 and 4, and the tech icons."""
    big, gap, row = 8, 12, SIZE * 8 + 28
    cell_w = SIZE * big + gap
    sheet = Image.new('RGBA', (gap + cell_w * len(PIECES) + 160, gap + row * len(TIERS) + 2 * 64 + 40),
                      (236, 233, 224, 255))
    draw = ImageDraw.Draw(sheet)
    for r, tier in enumerate(TIERS):
        y = gap + r * row
        draw.text((gap, y), tier.title(), fill=(40, 36, 30, 255))
        for c, piece in enumerate(PIECES):
            image = sprites[(tier, piece)]
            sheet.alpha_composite(image.resize((SIZE * big, SIZE * big), Image.Resampling.NEAREST),
                                  (gap + c * cell_w, y + 16))
            x = gap + cell_w * len(PIECES)
            sheet.alpha_composite(slot(image, 2), (x + c * 38, y + 16))
            sheet.alpha_composite(slot(image, 4), (x + (c % 2) * 74, y + 60 + (c // 2) * 74))
    y = gap + row * len(TIERS)
    draw.text((gap, y), 'Tech icons', fill=(40, 36, 30, 255))
    for c, (name, image) in enumerate(tech.items()):
        tile = Image.new('RGBA', (136, 136), (60, 52, 40, 255))
        tile.alpha_composite(image.resize((128, 128), Image.Resampling.NEAREST), (4, 4))
        sheet.alpha_composite(tile, (gap + c * 148, y + 16))
        draw.text((gap + c * 148, y + 156), name, fill=(40, 36, 30, 255))
    return sheet


def main():
    sprites = {(t, p): sprite(t, p) for t in TIERS for p in PIECES}
    for (tier, piece), image in sprites.items():
        image.save(ASSETS / f'textures/item/{tier}_{piece}.png')
    tech = icons(sprites)
    for name, image in tech.items():
        image.save(TECH / f'{name}.png')
    tech['armoured'].save(ARK / 'design/technology-tree/icons/keratin_armour.png')   # its tech-menu source
    DESIGN.mkdir(parents=True, exist_ok=True)
    review(sprites, tech).save(DESIGN / 'sprites.png')
    print(f'Wrote {len(sprites)} armour sprites, {len(tech)} tech icons and design/armour/sprites.png')


if __name__ == '__main__':
    main()
