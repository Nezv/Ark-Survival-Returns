"""Offline preview of the title scene: renders the two FancyMenu GLSL layers exactly as FancyMenu wraps them
(#version 150 Shadertoy wrapper, same uniforms), the creature from its GeckoLib geometry and idle clip, and a
mock of the layout's logo and buttons read from the generated layout file.

Needs an OpenGL 3.3 context (moderngl). On Linux without a display: xvfb-run -a python tools/preview_title_scene.py
Writes docs/title-scene.jpg (or --out). --flash previews a lightning strike, --time sets the scene clock.
"""
import argparse
import json
import math
import re
from pathlib import Path
import numpy as np
import moderngl
from PIL import Image, ImageDraw, ImageFont
import build_title_scene as scene

ROOT = scene.ROOT
ASSETS = ROOT / 'src/main/resources/assets/arksurvivalreturns'
FONT = ROOT / 'tools/fonts/Bitter.ttf'

# GlslShaderRuntime.UNIFORMS (FancyMenu 3.9.12): declared unless the source declares them itself.
FM_UNIFORMS = [
    'uniform vec3 iResolution;', 'uniform float iTime;', 'uniform float iTimeDelta;', 'uniform float iFrameRate;',
    'uniform int iFrame;', 'uniform vec4 iMouse;', 'uniform vec4 iDate;', 'uniform float iSampleRate;',
    'uniform float iChannelTime[4];', 'uniform vec3 iChannelResolution[4];', 'uniform sampler2D iChannel0;',
    'uniform sampler2D iChannel1;', 'uniform sampler2D iChannel2;', 'uniform sampler2D iChannel3;',
    'uniform vec2 fmAreaOffset;', 'uniform vec2 fmAreaSize;', 'uniform vec2 fmAreaPosition;',
    'uniform vec2 fmAreaTopLeft;', 'uniform vec2 fmScreenSize;', 'uniform float fmGuiScale;', 'uniform vec4 fmMouse;',
    'uniform vec2 fmMouseDelta;', 'uniform ivec4 fmMouseButtons;', 'uniform ivec4 fmMouseClickCount;',
    'uniform ivec4 fmMouseReleaseCount;', 'uniform vec2 fmMouseScroll;', 'uniform vec2 fmMouseScrollTotal;',
    'uniform ivec4 fmKeyEvent;', 'uniform int fmKeyEventCount;', 'uniform ivec4 fmCharEvent;',
    'uniform int fmCharEventCount;', 'uniform ivec4 fmDateParts;', 'uniform ivec4 fmTimeParts;',
    'uniform int fmDayOfYear;', 'uniform int fmWeekOfYear;', 'uniform int fmUnixTimeSeconds;',
    'uniform int fmUnixTimeMilliseconds;', 'uniform float fmPartialTick;', 'uniform float fmGameDeltaTicks;',
    'uniform float fmRealtimeDeltaTicks;', 'uniform int fmInWorld;', 'uniform int fmIsPaused;',
    'uniform float fmOpacity;', 'uniform int fmVariableCount;']

VERTEX = """#version 150
in vec2 Position;
out vec2 fmUv_FancyMenu;
void main() {
    fmUv_FancyMenu = (Position + 1.0) * 0.5;
    gl_Position = vec4(Position, 0.0, 1.0);
}
"""


def fancymenu_fragment(source):
    """GlslShaderRuntime.normalizeSource + buildShadertoyFragment."""
    src = source.replace('﻿', ' ').replace('\r\n', '\n').replace('\r', '\n')
    src = re.sub(r'(?m)^\s*#version\s+.+$', '', src)
    src = re.sub(r'(?m)^\s*precision\s+\w+\s+\w+\s*;\s*$', '', src).strip()
    declarations = []
    for line in FM_UNIFORMS:
        name = re.match(r'uniform \w+ (\w+)', line).group(1)
        if not re.search(r'(?m)^\s*uniform\s+[^;]*\b' + re.escape(name) + r'\b', src):
            declarations.append(line)
    return ('#version 150\nin vec2 fmUv_FancyMenu;\nout vec4 fmOutputColor_FancyMenu;\n#define iGlobalTime iTime\n'
            '#define texture2D texture\n#define textureCube texture\n' + '\n'.join(declarations) + '\n\n' + src +
            '\n\nvoid main() {\n    vec4 fmColor_FancyMenu = vec4(0.0);\n'
            '    mainImage(fmColor_FancyMenu, gl_FragCoord.xy - fmAreaOffset);\n'
            '    fmOutputColor_FancyMenu = vec4(fmColor_FancyMenu.rgb, fmColor_FancyMenu.a * fmOpacity);\n}\n')


def set_uniform(program, name, value):
    if name in program:
        program[name].value = value


class ShaderLayer:
    def __init__(self, ctx, source, channels):
        self.ctx = ctx
        self.program = ctx.program(vertex_shader=VERTEX, fragment_shader=fancymenu_fragment(source))
        quad = np.array([-1, -1, 1, -1, -1, 1, 1, 1], dtype='f4')
        self.vao = ctx.vertex_array(self.program, [(ctx.buffer(quad.tobytes()), '2f', 'Position')])
        self.channels = channels

    def draw(self, size, clock, mouse):
        seconds, millis, elapsed = clock
        for i, texture in enumerate(self.channels):
            texture.use(location=i)
            set_uniform(self.program, f'iChannel{i}', i)
        set_uniform(self.program, 'iResolution', (size[0], size[1], 1.0))
        set_uniform(self.program, 'iTime', elapsed)
        set_uniform(self.program, 'fmUnixTimeSeconds', seconds)
        set_uniform(self.program, 'fmUnixTimeMilliseconds', millis)
        set_uniform(self.program, 'fmAreaOffset', (0.0, 0.0))
        set_uniform(self.program, 'fmOpacity', 1.0)
        set_uniform(self.program, 'fmMouse', (mouse[0] * size[0], mouse[1] * size[1], mouse[0], mouse[1]))
        self.vao.render(moderngl.TRIANGLE_STRIP)


def load_texture(ctx, path):
    image = Image.open(path).convert('RGBA')
    texture = ctx.texture(image.size, 4, image.tobytes())   # first row = top row, as Minecraft uploads it
    texture.filter = (moderngl.LINEAR, moderngl.LINEAR)
    texture.repeat_x = texture.repeat_y = False
    return texture


# ---------------------------------------------------------------- creature (GeckoLib conventions)

def rot(rx, ry, rz):
    """GeckoLib bone rotation: PoseStack Z, then Y, then X (radians)."""
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    mx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    my = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    mz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return mz @ my @ mx


def keyframe(track, time):
    if not track:
        return None
    if isinstance(track, list):
        return np.array(track, dtype=float)
    times = sorted(track, key=float)
    values = []
    for t in times:
        v = track[t]
        if isinstance(v, dict):
            v = v.get('post', v.get('pre'))
        values.append(np.array(v, dtype=float))
    ts = np.array([float(t) for t in times])
    if time <= ts[0]:
        return values[0]
    if time >= ts[-1]:
        return values[-1]
    i = int(np.searchsorted(ts, time)) - 1
    f = (time - ts[i]) / (ts[i + 1] - ts[i])
    return values[i] * (1 - f) + values[i + 1] * f


def creature_mesh(species, clip, time, look=(0.0, 0.0), texture=None):
    """Triangles (model units, GeckoLib space) and per-triangle colours sampled from the texture."""
    geo = json.loads((ASSETS / f'geckolib/models/entity/{species}.geo.json').read_text())['minecraft:geometry'][0]
    animation = json.loads((ASSETS / f'geckolib/animations/entity/{species}.animation.json').read_text())
    tracks = animation['animations'][clip]['bones']
    length = animation['animations'][clip].get('animation_length', 1.0)
    time = time % length
    tex = np.asarray(texture.convert('RGBA'), dtype=float) / 255 if texture else None
    tw, th = geo['description']['texture_width'], geo['description']['texture_height']
    bones = {b['name']: b for b in geo['bones']}
    neck = ['Cnt_Neck_001_JNT_SKL', 'Cnt_Neck_002_JNT_SKL', 'Cnt_Neck_003_JNT_SKL', 'Cnt_Head_JNT_SKL']
    share = {name: w for name, w in zip(neck, (0.14, 0.2, 0.22, 0.34))}
    world = {}

    def transform(name):
        if name in world:
            return world[name]
        b = bones[name]
        pivot = np.array(b.get('pivot', [0, 0, 0]), dtype=float) * [-1, 1, 1]
        rest = np.radians(np.array(b.get('rotation', [0, 0, 0]), dtype=float) * [-1, -1, 1])
        t = tracks.get(name, {})
        r = keyframe(t.get('rotation'), time)
        p = keyframe(t.get('position'), time)
        s = keyframe(t.get('scale'), time)
        angles = rest + (np.radians(r * [-1, -1, 1]) if r is not None else 0)
        if name in share:   # TitleScene.lookAround: yaw turns left, positive pitch raises the head
            angles = angles + np.radians([-look[1] * share[name], look[0] * share[name], 0])
        m = np.eye(4)
        m[:3, 3] = (p * [-1, 1, 1]) if p is not None else 0
        local = np.eye(4)
        local[:3, :3] = rot(*angles) @ np.diag(s if s is not None else [1, 1, 1])
        to_pivot, back = np.eye(4), np.eye(4)
        to_pivot[:3, 3] = pivot
        back[:3, 3] = -pivot
        m = m @ to_pivot @ local @ back
        parent = b.get('parent')
        world[name] = (transform(parent) @ m) if parent else m
        return world[name]

    faces = {'north': [(1, 0, 0), (0, 0, 0), (0, 1, 0), (1, 1, 0)], 'south': [(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)],
             'east': [(0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)], 'west': [(1, 0, 1), (1, 0, 0), (1, 1, 0), (1, 1, 1)],
             'up': [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)], 'down': [(0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)]}
    tris, colors = [], []
    for name, b in bones.items():
        m = transform(name)
        hidden = name.startswith('ark_eye_alert') or name.startswith('ark_eyeball_alert')
        if hidden:
            continue
        for cube in b.get('cubes', []):
            size = np.array(cube['size'], dtype=float)
            inflate = cube.get('inflate', 0.0)
            origin = np.array(cube['origin'], dtype=float)
            origin = np.array([-(origin[0] + size[0]), origin[1], origin[2]]) - inflate
            size = size + 2 * inflate
            cube_m = np.eye(4)
            if 'rotation' in cube:
                cp = np.array(cube.get('pivot', [0, 0, 0]), dtype=float) * [-1, 1, 1]
                cr = np.radians(np.array(cube['rotation'], dtype=float) * [-1, -1, 1])
                a, c = np.eye(4), np.eye(4)
                a[:3, 3], c[:3, 3] = cp, -cp
                r = np.eye(4)
                r[:3, :3] = rot(*cr)
                cube_m = a @ r @ c
            full = m @ cube_m
            uv = cube.get('uv', {})
            for face, corners in faces.items():
                pts = [full @ np.append(origin + np.array(k) * size, 1.0) for k in corners]
                pts = [p[:3] for p in pts]
                color = (0.5, 0.5, 0.45)
                if tex is not None and isinstance(uv, dict) and face in uv:
                    u0, v0 = uv[face]['uv']
                    du, dv = uv[face].get('uv_size', [1, 1])
                    x0, x1 = sorted((int(u0 * tex.shape[1] / tw), int((u0 + du) * tex.shape[1] / tw)))
                    y0, y1 = sorted((int(v0 * tex.shape[0] / th), int((v0 + dv) * tex.shape[0] / th)))
                    patch = tex[y0:max(y1, y0 + 1), x0:max(x1, x0 + 1)]
                    if patch.size and patch[..., 3].max() > 0:
                        weights = patch[..., 3:4]
                        color = tuple((patch[..., :3] * weights).sum((0, 1)) / max(weights.sum(), 1e-6))
                tris += [pts[0], pts[1], pts[2], pts[0], pts[2], pts[3]]
                colors += [color] * 6
    return np.array(tris, dtype='f4'), np.array(colors, dtype='f4')


CREATURE_VS = """#version 150
uniform mat4 mvp;
uniform mat3 rotation;
in vec3 position;
in vec3 color;
out vec3 vColor;
out vec3 vWorld;
void main() {
    vColor = color;
    vWorld = rotation * position;
    gl_Position = mvp * vec4(position, 1.0);
}
"""

CREATURE_FS = """#version 150
uniform vec3 tint;
in vec3 vColor;
in vec3 vWorld;
out vec4 fragColor;
void main() {
    vec3 n = normalize(cross(dFdx(vWorld), dFdy(vWorld)));
    // Minecraft's two GUI entity lights, then the element's night tint.
    float light = 0.4 + 0.6 * max(0.0, dot(n, normalize(vec3(0.2, 1.0, -0.7)))) * 0.6
                      + 0.6 * max(0.0, dot(n, normalize(vec3(-0.2, 1.0, 0.7)))) * 0.6;
    fragColor = vec4(vColor * min(light, 1.0) * tint, 1.0);
}
"""


def rest_bounds(species):
    """client/title/ModelBounds.of: cube extents with GeckoLib's mirrored X, cube rotations ignored."""
    geo = json.loads((ASSETS / f'geckolib/models/entity/{species}.geo.json').read_text())
    lo, hi = np.full(3, np.inf), np.full(3, -np.inf)
    for bone in geo['minecraft:geometry'][0]['bones']:
        for cube in bone.get('cubes', []):
            o, sz, inflate = np.array(cube['origin'], float), np.array(cube['size'], float), cube.get('inflate', 0.0)
            start = np.array([-(o[0] + sz[0]), o[1], o[2]])
            lo, hi = np.minimum(lo, start - inflate), np.maximum(hi, start + sz + inflate)
    return lo, hi


def draw_creature(ctx, size, element, clock_seconds, look=(0.0, 0.0)):
    species = element.get('creature', 'tyrannosaurus')
    variant = element.get('texture_variant', 'midnight')
    texture = Image.open(ASSETS / f'textures/entity/{species}_{variant}.png')
    clip = element.get('idle_clip') or 'Rex-Idle'
    tris, colors = creature_mesh(species, clip, clock_seconds, look, texture)
    yaw = math.radians(float(element.get('body_yaw', -38)))
    pitch = math.radians(float(element.get('camera_pitch', -5)))
    # TitleScene framing: model y 0 on scene_ground, rest height = scene_height, centred on the rest bounds,
    # view = Rx(pitch) * Ry(yaw) * (model - centre).
    lo, hi = rest_bounds(species)
    scale = float(element.get('scene_height', 0.4)) / (hi[1] - lo[1])   # scene units per model unit
    cx = float(element.get('scene_x', 0.42))
    ground = float(element.get('scene_ground', 0.165))
    ry = np.array([[math.cos(yaw), 0, math.sin(yaw)], [0, 1, 0], [-math.sin(yaw), 0, math.cos(yaw)]])
    rx = np.array([[1, 0, 0], [0, math.cos(pitch), -math.sin(pitch)], [0, math.sin(pitch), math.cos(pitch)]])
    rotation = rx @ ry
    centre = np.array([(lo[0] + hi[0]) / 2, 0, (lo[2] + hi[2]) / 2])
    placed = (tris - centre) @ rotation.T * scale
    placed[:, 0] += cx
    placed[:, 1] += ground
    aspect = size[0] / size[1]
    # Orthographic scene -> clip space; depth from z.
    clip = np.zeros_like(placed)
    clip[:, 0] = placed[:, 0] / (aspect * 0.5)
    clip[:, 1] = placed[:, 1] * 2 - 1
    clip[:, 2] = -placed[:, 2] / 4.0
    program = ctx.program(vertex_shader=CREATURE_VS, fragment_shader=CREATURE_FS)
    program['mvp'].write(np.eye(4, dtype='f4').tobytes())
    program['rotation'].write(np.eye(3, dtype='f4').tobytes())
    tint = element.get('tint', '#6A7682').lstrip('#')
    program['tint'].value = tuple(int(tint[i:i + 2], 16) / 255 for i in (0, 2, 4))
    buffer = np.hstack([clip, colors]).astype('f4')
    vao = ctx.vertex_array(program, [(ctx.buffer(buffer.tobytes()), '3f 3f', 'position', 'color')])
    ctx.enable(moderngl.DEPTH_TEST)
    vao.render(moderngl.TRIANGLES)
    ctx.disable(moderngl.DEPTH_TEST)


# ---------------------------------------------------------------- layout mock

def parse_layout(path):
    containers, current = [], None
    for line in path.read_text(encoding='utf-8').splitlines():
        compact = re.sub(r'\s+', '', line)
        if compact.endswith('{'):
            current = {'_type': compact[:-1]}
            continue
        if compact.startswith('}') and current is not None:
            containers.append(current)
            current = None
            continue
        if current is not None and '=' in compact:
            key = compact.split('=', 1)[0]
            value = line.split('=', 1)[1]
            current[key] = value[1:] if value.startswith(' ') else value
    return containers


def anchor_origin(anchor, gw, gh):
    ox = {'left': 0, 'centered': gw // 2, 'right': gw}[anchor.split('-')[1]]
    oy = {'top': 0, 'mid': gh // 2, 'bottom': gh}[anchor.split('-')[0]]
    return ox, oy


LABELS = {'mc_titlescreen_singleplayer_button': 'Singleplayer', 'mc_titlescreen_multiplayer_button': 'Multiplayer',
          'forge_titlescreen_mods_button': 'Mods', 'mc_titlescreen_options_button': 'Options...',
          'mc_titlescreen_quit_button': 'Quit Game', 'mc_titlescreen_realms_button': 'Minecraft Realms'}


BUTTON = ROOT / 'src/main/resources/resourcepacks/ark_ui/assets/minecraft/textures/gui/sprites/widget/button.png'


def nine_slice(sprite, width, height, border=3):
    """Vanilla nine-slice GUI scaling (the Ark UI button's mcmeta), in GUI pixels."""
    out = Image.new('RGBA', (width, height))
    sw, sh = sprite.size
    xs = [(0, border, 0, border), (border, sw - border, border, width - border), (sw - border, sw, width - border, width)]
    ys = [(0, border, 0, border), (border, sh - border, border, height - border), (sh - border, sh, height - border, height)]
    for sx0, sx1, dx0, dx1 in xs:
        for sy0, sy1, dy0, dy1 in ys:
            if dx1 > dx0 and dy1 > dy0:
                out.paste(sprite.crop((sx0, sy0, sx1, sy1)).resize((dx1 - dx0, dy1 - dy0), Image.Resampling.NEAREST), (dx0, dy0))
    return out


def draw_ui(image, containers, gui_scale):
    gw, gh = image.width // gui_scale, image.height // gui_scale
    draw = ImageDraw.Draw(image, 'RGBA')
    font = ImageFont.truetype(str(FONT), 9 * gui_scale)
    sprite = Image.open(BUTTON).convert('RGBA') if BUTTON.is_file() else None
    for c in containers:
        if c.get('is_hidden') == 'true' or c.get('anchor_point', 'vanilla') == 'vanilla':
            continue
        if c['_type'] not in ('element', 'vanilla_button') or c.get('element_type') not in ('image', 'vanilla_button'):
            continue
        ox, oy = anchor_origin(c['anchor_point'], gw, gh)
        x, y = (ox + int(c['x'])) * gui_scale, (oy + int(c['y'])) * gui_scale
        w, h = int(c['width']) * gui_scale, int(c['height']) * gui_scale
        if c.get('element_type') == 'image':
            source = c['source'].split(']', 1)[1]
            namespace, path = source.split(':', 1)
            art = Image.open(ROOT / 'src/main/resources/assets' / namespace / path).convert('RGBA').resize((w, h))
            image.alpha_composite(art, (x, y))
            continue
        identifier = c['instance_identifier']
        if sprite:
            face = nine_slice(sprite, int(c['width']), int(c['height']))
            image.alpha_composite(face.resize((w, h), Image.Resampling.NEAREST), (x, y))
        else:
            draw.rectangle((x, y, x + w - 1, y + h - 1), fill=(34, 37, 32, 225), outline=(12, 13, 11, 255), width=gui_scale)
        if not identifier.endswith(('language_button', 'accessibility_button')):
            draw.text((x + w // 2, y + h // 2), LABELS.get(identifier, identifier), font=font, fill=(230, 232, 225, 255),
                      anchor='mm')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', default=str(ROOT / 'docs/title-scene.jpg'))
    parser.add_argument('--size', default='1920x1080')
    parser.add_argument('--time', type=float, default=12.0)
    parser.add_argument('--flash', action='store_true', help='render the frame 0.05 s after a lightning strike')
    parser.add_argument('--mouse', default='0.5,0.5')
    parser.add_argument('--gui-scale', type=int, default=3)
    parser.add_argument('--look', default='0,0', help='creature gaze yaw,pitch in degrees')
    parser.add_argument('--no-ui', action='store_true')
    parser.add_argument('--layers', default='background,creature,foreground', help='comma-separated layers to draw')
    args = parser.parse_args()
    size = tuple(int(v) for v in args.size.split('x'))
    mouse = tuple(float(v) for v in args.mouse.split(','))

    seconds = 1_790_000_000
    millis = 0
    if args.flash:
        seconds, millis = strike_time()
    clock = (seconds, millis, args.time)

    containers = parse_layout(scene.LAYOUT)
    background = next(c for c in containers if c['_type'] == 'menu_background')
    foreground = next((c for c in containers if c.get('element_type') == 'glsl_shader'), None)
    creature = next((c for c in containers if c.get('element_type') == 'arksurvivalreturns_creature'), None)

    ctx = moderngl.create_standalone_context(require=330)
    fbo = ctx.simple_framebuffer(size, components=4)
    fbo.use()
    ctx.clear(0, 0, 0, 1, depth=1.0)
    far = load_texture(ctx, scene.OUT / 'scene_far.png')
    near = load_texture(ctx, scene.OUT / 'scene_near.png')
    ShaderLayer(ctx, background['inline_shader_source'].replace(scene.NEWLINE, '\n'), [far, near]).draw(size, clock, mouse)
    layers = args.layers.split(',')
    if creature and 'creature' in layers:
        draw_creature(ctx, size, creature, args.time, tuple(float(v) for v in args.look.split(',')))
    if foreground and 'foreground' in layers:
        ctx.enable(moderngl.BLEND)
        ctx.blend_func = moderngl.SRC_ALPHA, moderngl.ONE_MINUS_SRC_ALPHA, moderngl.ONE, moderngl.ZERO
        ShaderLayer(ctx, foreground['inline_shader_source'].replace(scene.NEWLINE, '\n'), [far, near]).draw(size, clock, mouse)
        ctx.disable(moderngl.BLEND)
    frame = Image.frombytes('RGBA', size, fbo.read(components=4)).transpose(Image.Transpose.FLIP_TOP_BOTTOM)
    if not args.no_ui:
        draw_ui(frame, containers, args.gui_scale)
    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    frame.convert('RGB').save(args.out, quality=90)
    print(f'Wrote {args.out}')


def strike_time():
    """First strike after a fixed epoch, using the shaders' strike clock; returns a moment 0.05 s into it."""
    def hashu(x):
        x &= 0xFFFFFFFF
        x ^= x >> 16
        x = (x * 0x7feb352d) & 0xFFFFFFFF
        x ^= x >> 15
        x = (x * 0x846ca68b) & 0xFFFFFFFF
        x ^= x >> 16
        return x
    slot = 1_790_000_000 // 9
    while hashu(slot) % 100 >= 40:
        slot += 1
    start_ms = slot * 9000 + (hashu(slot) >> 8) % 7000 + 50
    return start_ms // 1000, start_ms % 1000


if __name__ == '__main__':
    main()
