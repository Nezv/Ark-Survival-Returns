"""Build the night-rain title scene (F09): the masks the FancyMenu GLSL layers sample and the layout itself.

Scene units: x runs from the screen centre in screen heights (16:9 spans -0.89..0.89), y from the bottom
(0..1). The shaders (tools/title_scene/*.glsl) and the Ark creature element use the same units, so the
T-Rex stays on the painted path at any resolution or aspect ratio.

textures/gui/title/scene_far.png   R distant treeline, G outpost, B warm windows, A side conifers
textures/gui/title/scene_near.png  R ground, G wet path and puddles, B ferns, A near fronds (blurred)
config/fancymenu/customization/ark_title_screen.txt  the layout, shaders inlined the way FancyMenu stores
multi-line properties (one line, %%!serialized_property_newline!%% for each line break).

Run from Ark: python tools/build_title_scene.py [--layout]   (--layout rewrites only the layout, after a shader edit)
Preview: python tools/preview_title_scene.py
"""
import math
import random
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'src/main/resources/assets/arksurvivalreturns/textures/gui/title'
SHADERS = ROOT / 'tools/title_scene'
LAYOUT = ROOT / 'config/fancymenu/customization/ark_title_screen.txt'
X0, X1 = -1.2, 1.2          # scene units covered by the textures (21:9 plus parallax margin)
PPU = 1200                  # final pixels per scene unit
SS = 2                      # supersampling while drawing
W, H = int((X1 - X0) * PPU), PPU
GROUND = 0.305              # ground line at the path, where the creature stands


class Mask:
    """One 8-bit channel drawn at SS x resolution in scene units."""

    def __init__(self):
        self.image = Image.new('L', (W * SS, H * SS), 0)
        self.draw = ImageDraw.Draw(self.image)

    @staticmethod
    def px(x, y):
        return ((x - X0) * PPU * SS, (1.0 - y) * PPU * SS)

    def polygon(self, points, value=255):
        self.draw.polygon([self.px(x, y) for x, y in points], fill=value)

    def line(self, points, width, value=255):
        self.draw.line([self.px(x, y) for x, y in points], fill=value, width=max(1, round(width * PPU * SS)),
                       joint='curve')

    def ellipse(self, x, y, rx, ry, value=255):
        (ax, ay), (bx, by) = self.px(x - rx, y + ry), self.px(x + rx, y - ry)
        self.draw.ellipse((ax, ay, bx, by), fill=value)

    def rect(self, x0, y0, x1, y1, value=255):
        (ax, ay), (bx, by) = self.px(x0, y1), self.px(x1, y0)
        self.draw.rectangle((ax, ay, bx, by), fill=value)

    def final(self, blur=0.0):
        image = self.image.resize((W, H), Image.Resampling.BOX)
        return image.filter(ImageFilter.GaussianBlur(blur * PPU)) if blur else image


def quad_bezier(p0, p1, p2, t):
    a = (1 - t) * (1 - t)
    b = 2 * (1 - t) * t
    c = t * t
    return (a * p0[0] + b * p1[0] + c * p2[0], a * p0[1] + b * p1[1] + c * p2[1])


# ---------------------------------------------------------------- trees

def far_conifer(mask, rng, x, base, height):
    """Distant spruce: a jagged spire of stacked tiers."""
    width = height * rng.uniform(0.26, 0.38)
    tiers = rng.randint(6, 10)
    for i in range(tiers):
        t0 = i / tiers
        top = base + height * (0.18 + 0.82 * (i + 1) / tiers)
        bottom = base + height * (0.10 + 0.82 * t0)
        half = width * 0.5 * (1.0 - t0) ** 0.85 * rng.uniform(0.8, 1.15)
        droop = height * 0.03
        mask.polygon([(x - half, bottom - droop), (x, top), (x + half, bottom - droop * rng.uniform(0.5, 1.5)),
                      (x + half * 0.35, bottom), (x - half * 0.4, bottom)])
    mask.polygon([(x - height * 0.012, base), (x - height * 0.008, base + height * 0.2),
                  (x + height * 0.008, base + height * 0.2), (x + height * 0.012, base)])
    mask.line([(x, base + height * 0.95), (x, base + height * 1.04)], height * 0.006)


def broadleaf(mask, rng, x, base, height):
    """Distant rounded canopy made of overlapping blobs."""
    for _ in range(rng.randint(5, 9)):
        r = height * rng.uniform(0.18, 0.32)
        mask.ellipse(x + rng.uniform(-0.35, 0.35) * height, base + height * rng.uniform(0.45, 0.8), r, r * 0.8)
    mask.rect(x - height * 0.02, base, x + height * 0.02, base + height * 0.5)


def needles(mask, rng, px, py, size, droop):
    """A ragged clump of needles hanging off a branch: a few jittered spikes around a small core."""
    spikes = []
    count = rng.randint(7, 11)
    for k in range(count):
        a = 2 * math.pi * k / count + rng.uniform(-0.25, 0.25)
        # Longer below the branch: the clumps hang.
        r = size * rng.uniform(0.45, 1.0) * (1.0 + droop * max(0.0, -math.sin(a)))
        spikes.append((px + math.cos(a) * r, py + math.sin(a) * r * 0.75))
        spikes.append((px + math.cos(a + math.pi / count) * size * 0.3, py + math.sin(a + math.pi / count) * size * 0.25))
    mask.polygon(spikes)


def spruce(mask, rng, x, base, height, lean=0.0):
    """Side conifer: a tapering trunk, irregular drooping branches and ragged needle clumps along them."""
    top = base + height
    trunk = height * 0.009
    mask.polygon([(x - trunk * 2.4, base), (x + lean * height - trunk * 0.25, top),
                  (x + lean * height + trunk * 0.25, top), (x + trunk * 2.4, base)])
    t = rng.uniform(0.12, 0.22)                       # bare lower trunk
    while t < 0.97:
        y = base + height * t
        cx = x + lean * height * t
        reach = height * (0.2 * (1.0 - t) ** 0.85 + 0.018) * rng.uniform(0.65, 1.25)
        for side in (-1, 1):
            if rng.random() < 0.18:
                continue                              # a gap in the whorl
            length = reach * rng.uniform(0.55, 1.15)
            droop = length * rng.uniform(0.2, 0.7)
            p0 = (cx, y)
            p1 = (cx + side * length * rng.uniform(0.4, 0.6), y + length * rng.uniform(-0.02, 0.1))
            p2 = (cx + side * length, y - droop)
            mask.line([quad_bezier(p0, p1, p2, k / 10) for k in range(11)], height * 0.0035)
            steps = max(3, int(length / (height * 0.009)))
            for k in range(1, steps + 1):
                u = k / (steps + 1)
                px, py = quad_bezier(p0, p1, p2, u)
                size = height * rng.uniform(0.02, 0.032) * (1.2 - 0.55 * u)
                needles(mask, rng, px + rng.uniform(-0.3, 0.3) * size, py - size * 0.2, size, 0.8)
        # Whorls crowd together toward the crown.
        t += rng.uniform(0.016, 0.036) * (1.1 - 0.5 * t)
    # Crown: a thin leader with a few short sprigs.
    leader = x + lean * height
    mask.line([(leader, top - height * 0.06), (leader + rng.uniform(-0.004, 0.004), top + height * 0.03)], height * 0.004)
    for k in range(4):
        yy = top - height * 0.05 + k * height * 0.018
        for side in (-1, 1):
            needles(mask, rng, leader + side * height * 0.008, yy, height * 0.009, 0.5)


# ---------------------------------------------------------------- outpost

OUTPOST = (0.02, 0.66)       # facade from x..x
MAST_X = 0.46                # floodlight mast
LAMP = (0.465, 0.745)        # floodlight head (the shaders' LIGHT_POS)


def outpost(structure, lights, rng):
    base = 0.318
    left, right = OUTPOST
    # Main hall, upper storey and a service block.
    structure.polygon([(left, base), (left, 0.495), (left + 0.03, 0.51), (right - 0.05, 0.51), (right, 0.49),
                       (right, base)])
    structure.rect(0.16, 0.50, 0.52, 0.585)
    structure.rect(0.52, 0.47, 0.62, 0.53)
    structure.rect(-0.12, base, left + 0.01, 0.43)                       # annex
    structure.polygon([(-0.13, 0.43), (-0.045, 0.462), (left + 0.012, 0.43)])
    # Roof clutter: vents, rails, antenna.
    for vx in (0.19, 0.25, 0.41):
        structure.rect(vx, 0.585, vx + 0.03, 0.602)
    for rx in [0.16 + i * 0.018 for i in range(21)]:
        structure.line([(rx, 0.585), (rx, 0.598)], 0.0012)
    structure.line([(0.16, 0.598), (0.52, 0.598)], 0.0016)
    structure.line([(0.30, 0.60), (0.30, 0.665)], 0.0018)
    structure.line([(0.295, 0.645), (0.305, 0.645)], 0.0012)
    # Floodlight mast: two legs, cross bracing, the lamp head and its hood.
    legs = ((MAST_X - 0.02, 0.51), (MAST_X + 0.02, 0.51))
    structure.line([legs[0], (MAST_X - 0.004, LAMP[1] - 0.015)], 0.0022)
    structure.line([legs[1], (MAST_X + 0.004, LAMP[1] - 0.015)], 0.0022)
    steps = 7
    for i in range(steps):
        y0 = 0.51 + (LAMP[1] - 0.525) * i / steps
        y1 = 0.51 + (LAMP[1] - 0.525) * (i + 1) / steps
        w0 = 0.02 - 0.016 * i / steps
        w1 = 0.02 - 0.016 * (i + 1) / steps
        structure.line([(MAST_X - w0, y0), (MAST_X + w1, y1)], 0.0011)
        structure.line([(MAST_X + w0, y0), (MAST_X - w1, y1)], 0.0011)
    structure.rect(LAMP[0] - 0.034, LAMP[1] - 0.012, LAMP[0] + 0.034, LAMP[1] + 0.012)
    structure.polygon([(LAMP[0] - 0.04, LAMP[1] + 0.012), (LAMP[0] + 0.04, LAMP[1] + 0.012),
                       (LAMP[0] + 0.03, LAMP[1] + 0.022), (LAMP[0] - 0.03, LAMP[1] + 0.022)])
    # Perimeter fence with posts and a gap for the gate.
    for fx in [-0.95 + i * 0.045 for i in range(46)]:
        if 0.24 < fx < 0.44:
            continue
        structure.line([(fx, base - 0.012), (fx, base + 0.045)], 0.0016)
    for fy in (base + 0.018, base + 0.040):
        structure.line([(-0.95, fy), (0.24, fy)], 0.0009)
        structure.line([(0.44, fy), (1.1, fy)], 0.0009)
    # Warm windows: a lit strip in the hall, a scattering upstairs, the open gate bay.
    for i in range(22):
        wx = left + 0.03 + i * 0.027
        if rng.random() < 0.72:
            lights.rect(wx, 0.445, wx + 0.018, 0.47, rng.randint(150, 255))
        if rng.random() < 0.3:
            lights.rect(wx, 0.395, wx + 0.018, 0.418, rng.randint(90, 200))
    for i in range(12):
        wx = 0.175 + i * 0.029
        if rng.random() < 0.45:
            lights.rect(wx, 0.535, wx + 0.016, 0.556, rng.randint(80, 190))
    lights.rect(0.28, base, 0.38, 0.40, 255)                             # gate bay
    structure.rect(0.28, 0.40, 0.38, 0.41)
    lights.rect(-0.1, 0.36, -0.07, 0.385, 180)                           # annex window
    # Cut the lit areas out of the structure so the shader can glow them.
    return structure


# ---------------------------------------------------------------- ground

def ground_line(x):
    bump = 0.018 * math.exp(-((x - 0.33) / 0.55) ** 2)
    return GROUND + bump + 0.006 * math.sin(x * 9.0) + 0.003 * math.sin(x * 31.0 + 1.3)


def ground(ground_mask, wet, rng):
    points = [(x / 200 * (X1 - X0) + X0, ground_line(x / 200 * (X1 - X0) + X0)) for x in range(201)]
    ground_mask.polygon(points + [(X1, -0.05), (X0, -0.05)])
    # Grass tufts break the ground line.
    for _ in range(900):
        x = rng.uniform(X0, X1)
        if 0.18 < x < 0.5 and rng.random() < 0.8:
            continue  # the path stays clear
        y = ground_line(x) - 0.004
        h = rng.uniform(0.006, 0.022)
        lean = rng.uniform(-0.6, 0.6) * h
        ground_mask.polygon([(x - 0.0025, y), (x + lean, y + h), (x + 0.0025, y)])
    # The path runs from the gate towards the viewer, widening with perspective.
    path = [(0.29, ground_line(0.29)), (0.38, ground_line(0.38))]
    for i in range(1, 11):
        t = i / 10
        y = ground_line(0.33) * (1 - t) - 0.02 * t
        half = 0.05 + 0.55 * t ** 1.2
        center = 0.335 + 0.05 * t
        path.append((center + half, y))
    left_edge = []
    for i in range(10, 0, -1):
        t = i / 10
        y = ground_line(0.33) * (1 - t) - 0.02 * t
        half = 0.05 + 0.55 * t ** 1.2
        center = 0.335 + 0.05 * t
        left_edge.append((center - half, y))
    wet.polygon(path + left_edge, 70)
    # Puddles: flat ellipses, wider and softer nearer the viewer.
    for _ in range(46):
        t = rng.uniform(0.05, 1.0) ** 1.4
        y = ground_line(0.33) * (1 - t) + 0.01
        half = 0.05 + 0.55 * t ** 1.2
        cx = 0.335 + 0.05 * t + rng.uniform(-0.85, 0.85) * half
        rx = rng.uniform(0.012, 0.05) * (0.4 + 1.6 * t)
        wet.ellipse(cx, y, rx, rx * rng.uniform(0.08, 0.16), rng.randint(170, 255))


# ---------------------------------------------------------------- ferns

def rachis(base, angle, length, droop, steps=40):
    """Frond stem that rises at `angle` (degrees) and bends toward the ground along its length."""
    side = 1.0 if math.cos(math.radians(angle)) >= 0 else -1.0
    x, y = base
    points = [(x, y)]
    for i in range(1, steps + 1):
        s = i / steps
        a = math.radians(angle - side * droop * s ** 1.6)
        x += math.cos(a) * length / steps
        y += math.sin(a) * length / steps
        points.append((x, y))
    return points


def frond(mask, rng, base, angle, length, droop, width=1.0, stipe=0.14):
    """Pinnate fern frond: slender leaflets on both sides of a drooping stem, longest in the middle."""
    stem = rachis(base, angle, length, droop)
    mask.line(stem, 0.0016 * (0.6 + length))
    longest = length * 0.13 * width
    pairs = int((1 - stipe) * length / (longest * 0.24))
    samples = 14
    for i in range(pairs):
        t = stipe + (0.995 - stipe) * (i + 0.5) / pairs
        k = t * (len(stem) - 1)
        j = min(int(k), len(stem) - 2)
        f = k - j
        px = stem[j][0] + (stem[j + 1][0] - stem[j][0]) * f
        py = stem[j][1] + (stem[j + 1][1] - stem[j][1]) * f
        tx, ty = stem[j + 1][0] - stem[j][0], stem[j + 1][1] - stem[j][1]
        heading = math.atan2(ty, tx)
        u = (t - stipe) / (1 - stipe)
        # Lanceolate frond: leaflets longest a third of the way up, tiny at the tip.
        size = longest * math.sin(math.pi * min(1.0, 0.12 + 0.9 * u ** 0.85)) ** 0.8 * rng.uniform(0.9, 1.06)
        for side in (-1, 1):
            spread = math.radians(rng.uniform(62, 74) - 20 * u)
            a = heading + side * spread
            upper, lower = [], []
            for s in range(samples + 1):
                v = s / samples
                la = a - side * math.radians(16) * v          # curves toward the frond tip
                cx = px + math.cos(la) * size * v
                cy = py + math.sin(la) * size * v - 0.08 * size * v * v
                # Narrow stalk, widest at a third, finely toothed edge, pointed tip.
                half = size * 0.1 * math.sin(math.pi * v ** 0.65) * (1 - v) ** 0.25 * (1.0 + 0.18 * (s % 2))
                nx, ny = -math.sin(la), math.cos(la)
                upper.append((cx + nx * half, cy + ny * half))
                lower.append((cx - nx * half, cy - ny * half))
            mask.polygon(upper + lower[::-1])


def fern_cluster(mask, rng, root, fronds, scale=1.0, width=1.0):
    """fronds: (angle, length, droop) triples radiating from one crown."""
    for angle, length, droop in fronds:
        start = (root[0] + rng.uniform(-0.01, 0.01), root[1])
        frond(mask, rng, start, angle, length * scale, droop, width)


def ferns(near, nearest, rng):
    # Left crown (under the buttons): fronds arch up and over to the right.
    fern_cluster(near, rng, (-0.98, -0.05), [(88, 0.66, 110), (76, 0.74, 115), (64, 0.7, 100), (52, 0.6, 85),
                                             (40, 0.5, 70), (99, 0.52, 95), (110, 0.42, 80), (30, 0.4, 55)])
    fern_cluster(near, rng, (-0.66, -0.07), [(94, 0.38, 100), (80, 0.42, 105), (66, 0.36, 85), (106, 0.32, 80),
                                             (54, 0.28, 60)])
    # Right crown: mirrored and lower, so the creature's head stays clear.
    fern_cluster(near, rng, (1.0, -0.05), [(94, 0.52, 105), (106, 0.6, 110), (118, 0.57, 100), (130, 0.5, 85),
                                           (142, 0.42, 70), (82, 0.44, 90), (154, 0.34, 55)])
    fern_cluster(near, rng, (0.74, -0.08), [(90, 0.28, 90), (104, 0.32, 100), (120, 0.29, 80), (74, 0.24, 70)])
    # Low undergrowth along the bottom edge; it hides the creature's toes in the wet grass.
    for x in [-0.55 + i * 0.085 for i in range(16)]:
        count = rng.randint(2, 4)
        fronds = []
        for _ in range(count):
            angle = rng.uniform(58, 122)
            fronds.append((angle, rng.uniform(0.09, 0.17), rng.uniform(60, 110)))
        fern_cluster(near, rng, (x + rng.uniform(-0.03, 0.03), -0.035), fronds)
    # Nearest fronds: large and out of focus, entering from the lower corners.
    fern_cluster(nearest, rng, (-1.22, -0.04), [(42, 0.92, 70), (24, 0.8, 55), (60, 0.72, 95)], width=1.25)
    fern_cluster(nearest, rng, (1.24, -0.06), [(140, 0.84, 70), (158, 0.72, 55)], width=1.25)


# ---------------------------------------------------------------- build

def build_masks():
    rng = random.Random(26_09_2026)
    far_trees, structure, lights, side_trees = Mask(), Mask(), Mask(), Mask()
    ground_mask, wet, near, nearest = Mask(), Mask(), Mask(), Mask()

    # Distant forest: a dense band of spires and canopies behind the outpost.
    for _ in range(260):
        x = rng.uniform(X0, X1)
        base = 0.30 + rng.uniform(0.0, 0.04)
        if rng.random() < 0.75:
            far_conifer(far_trees, rng, x, base, rng.uniform(0.12, 0.3) * (1.2 if abs(x - 0.3) > 0.5 else 0.85))
        else:
            broadleaf(far_trees, rng, x, base, rng.uniform(0.08, 0.16))
    far_trees.rect(X0, 0.2, X1, 0.33)
    outpost(structure, lights, rng)
    # Side conifers frame the outpost and reach past the top edge.
    for x, height, lean in ((-1.08, 1.05, 0.01), (-0.86, 0.95, -0.015), (-0.62, 0.78, 0.02), (-0.42, 0.62, -0.01),
                            (0.84, 0.92, -0.02), (1.05, 1.1, 0.01), (0.7, 0.66, 0.015)):
        spruce(side_trees, rng, x, 0.29 + rng.uniform(-0.01, 0.01), height, lean)
    ground(ground_mask, wet, rng)
    ferns(near, nearest, rng)

    far = Image.merge('RGBA', (far_trees.final(0.0008), structure.final(), lights.final(), side_trees.final()))
    near_image = Image.merge('RGBA', (ground_mask.final(), wet.final(0.003), near.final(), nearest.final(0.004)))
    return far, near_image


def write_masks():
    OUT.mkdir(parents=True, exist_ok=True)
    far, near = build_masks()
    for name, image in (('scene_far', far), ('scene_near', near)):
        image.save(OUT / f'{name}.png', optimize=True)
        # Linear filtering and clamped edges: the shaders sample these at screen resolution.
        (OUT / f'{name}.png.mcmeta').write_text('{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n',
                                                encoding='utf-8')
    print(f'Wrote {OUT / "scene_far.png"} and scene_near.png ({W}x{H})')


# ---------------------------------------------------------------- layout

NEWLINE = '%%!serialized_property_newline!%%'   # PropertyContainer.SERIALIZED_PROPERTY_NEWLINE_TOKEN
SOURCE = '[source:location]'
EDITED = 1790467200000                          # fixed, so rebuilding does not churn the file

# The Ark creature element (client/title/fancymenu/CreatureElement.java): scene units as above.
CREATURE = [
    ('creature', 'tyrannosaurus'),
    ('texture_variant', 'midnight'),
    ('idle_clip', ''),
    ('scene_x', '0.38'),
    ('scene_ground', '0.16'),
    ('scene_height', '0.42'),
    ('body_yaw', '-38.0'),
    ('camera_pitch', '-5.0'),
    ('look_around', 'true'),
    ('look_range', '38.0'),
    ('tint', '#56606B'),
    ('lightning_boost', '0.8'),
    ('thunder', 'true'),
    ('thunder_volume', '0.7'),
    ('parallax', '0.012'),
]

# Buttons in a column on the left, over the dark ferns; the creature and the lamp own the right.
BUTTONS = [
    ('mc_titlescreen_singleplayer_button', 24, -16, 164, 20),
    ('mc_titlescreen_multiplayer_button', 24, 8, 164, 20),
    ('forge_titlescreen_mods_button', 24, 32, 164, 20),
    ('mc_titlescreen_options_button', 24, 62, 80, 20),
    ('mc_titlescreen_quit_button', 108, 62, 80, 20),
    ('mc_titlescreen_language_button', 192, 62, 20, 20),
    ('mc_titlescreen_accessibility_button', 216, 62, 20, 20),
]
HIDDEN = ['mc_titlescreen_realms_button', 'minecraft_logo_widget', 'minecraft_splash_widget',
          'minecraft_realms_notification_icons_widget']


def shader(name):
    text = (SHADERS / f'{name}.glsl').read_text(encoding='utf-8').replace('\r\n', '\n').strip()
    if not text.isascii():
        raise ValueError(f'{name}.glsl must be ASCII: FancyMenu keeps the source inline in the layout')
    return text.replace('\n', NEWLINE)


def shader_properties(name):
    return [('inline_shader_source', shader(name)),
            ('image_ichannel0_input', 'resource0'),
            ('image_ichannel1_input', 'resource1'),
            ('ichannel_0_source', SOURCE + 'arksurvivalreturns:textures/gui/title/scene_far.png'),
            ('ichannel_1_source', SOURCE + 'arksurvivalreturns:textures/gui/title/scene_near.png'),
            ('compile_mode', 'shadertoy'),
            ('force_shadertoy_compatibility', 'true'),
            ('freeze_time', 'false'),
            ('time_scale', '1.0'),
            ('use_input', 'true'),
            ('mouse_position_requires_hold', 'false'),
            ('opacity_multiplier', '1.0'),
            ('show_compile_errors', 'true')]


def full_screen(identifier, element_type):
    return [('element_type', element_type), ('instance_identifier', identifier), ('anchor_point', 'top-left'),
            ('x', '0'), ('y', '0'), ('width', '640'), ('height', '360'), ('stretch_x', 'true'), ('stretch_y', 'true'),
            ('stay_on_screen', 'false')]


def layout_containers():
    containers = [
        ('layout-meta', [('identifier', 'title_screen'), ('render_custom_elements_behind_vanilla', 'true'),
                         ('last_edited_time', str(EDITED)), ('is_enabled', 'true'), ('randommode', 'false'),
                         ('randomgroup', '1'), ('randomonlyfirsttime', 'false'), ('layout_index', '0')]),
        ('menu_background', [('background_type', 'glsl'), ('instance_identifier', 'ark_night_scene'),
                             ('show_background', 'true'), ('enable_blending', 'false')] + shader_properties('background')),
        ('customization', [('action', 'backgroundoptions'), ('keepaspectratio', 'false')]),
        ('scroll_list_customization', [('apply_vanilla_background_blur', 'false')]),
        # Custom elements draw in this order, all behind the vanilla buttons.
        ('element', full_screen('ark_creature', 'arksurvivalreturns_creature') + CREATURE),
        ('element', full_screen('ark_night_foreground', 'glsl_shader') + [('enable_blending', 'true')]
         + shader_properties('foreground')),
        ('element', [('element_type', 'image'), ('instance_identifier', 'ark_logo'),
                     ('source', SOURCE + 'arksurvivalreturns:textures/gui/title/logo.png'), ('anchor_point', 'top-left'),
                     ('x', '22'), ('y', '18'), ('width', '200'), ('height', '63'), ('repeat_texture', 'false'),
                     ('nine_slice_texture', 'false'), ('stay_on_screen', 'true')]),
        ('element', [('element_type', 'audio_v2'), ('instance_identifier', 'ark_rain'),
                     ('audio_instance_0', SOURCE + 'ambientsounds:sounds/weather/rain1.ogg'),
                     ('audio_instance_weight_0', '1.0'),
                     ('audio_instance_1', SOURCE + 'ambientsounds:sounds/weather/rain2.ogg'),
                     ('audio_instance_weight_1', '1.0'), ('play_mode', 'shuffle'), ('looping', 'true'),
                     ('sound_source', 'weather'), ('volume', '0.55'), ('anchor_point', 'top-left'), ('x', '4'),
                     ('y', '4'), ('width', '24'), ('height', '24')]),
    ]
    for identifier, x, y, width, height in BUTTONS:
        containers.append(('vanilla_button', [('element_type', 'vanilla_button'), ('instance_identifier', identifier),
                                              ('anchor_point', 'mid-left'), ('x', str(x)), ('y', str(y)),
                                              ('width', str(width)), ('height', str(height)),
                                              ('stay_on_screen', 'true'), ('is_hidden', 'false')]))
    for identifier in HIDDEN:
        containers.append(('vanilla_button', [('element_type', 'vanilla_button'), ('instance_identifier', identifier),
                                              ('is_hidden', 'true')]))
    return containers


def write_layout():
    # PropertiesParser.serializeSetToFancyString: "type = ...", then each container and a blank line.
    text = 'type = fancymenu_layout\n\n'
    for kind, properties in layout_containers():
        text += kind + ' {\n' + ''.join(f'  {key} = {value}\n' for key, value in properties) + '}\n\n'
    LAYOUT.parent.mkdir(parents=True, exist_ok=True)
    LAYOUT.write_text(text, encoding='utf-8', newline='\n')
    print(f'Wrote {LAYOUT}')


def main():
    if '--layout' not in sys.argv:
        write_masks()
    write_layout()


if __name__ == '__main__':
    main()
