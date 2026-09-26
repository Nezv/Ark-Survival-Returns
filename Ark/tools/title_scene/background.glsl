// Ark: Survival Returns title scene, background layer (FancyMenu "GLSL Shader" menu background).
// Night and rain over a primitive camp: two log barracks around a campfire, a moon lost in the clouds.
//
// Scene units: x from the screen centre in screen heights, y from the bottom (0..1). The textures cover
// x -1.2..1.2; the creature element and tools/build_title_scene.py use the same units.
// iChannel0 scene_far.png : R distant treeline, G huts and camp, B firelit doors and torches, A side conifers
// iChannel1 scene_near.png: R ground, G wet path and puddles, B ferns, A near fronds
// Lightning follows the strike clock shared with foreground.glsl and the Java element (StormClock).

const float X0 = -1.2;
const float X1 = 1.2;
const vec2 FIRE = vec2(-0.02, 0.318);
const vec2 MOON = vec2(0.6, 0.86);
const float GROUND = 0.318;

const vec3 SKY_TOP = vec3(0.0004, 0.0006, 0.0009);
const vec3 SKY_LOW = vec3(0.0028, 0.0048, 0.0070);
const vec3 FOG = vec3(0.0036, 0.0060, 0.0086);
const vec3 MOONLIGHT = vec3(0.62, 0.74, 0.92);
const vec3 WARM = vec3(1.00, 0.45, 0.14);
const vec3 BOLT = vec3(0.62, 0.70, 0.95);

uint hashu(uint x) {
    x ^= x >> 16u;
    x *= 0x7feb352du;
    x ^= x >> 15u;
    x *= 0x846ca68bu;
    x ^= x >> 16u;
    return x;
}

float hash21(vec2 p) {
    ivec2 i = ivec2(floor(p)) + ivec2(4096);
    return float(hashu(uint(i.x) * 1597334677u ^ hashu(uint(i.y)))) / 4294967295.0;
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amp * vnoise(p);
        p = p * 2.03 + vec2(17.1, 9.2);
        amp *= 0.5;
    }
    return sum;
}

// Strike clock: one slot every 9 s, 40% of slots strike somewhere in their first 7 s.
float lightning(out float boltX) {
    boltX = 0.0;
    float flash = 0.0;
    float ms = float(fmUnixTimeMilliseconds) / 1000.0;
    for (int k = 0; k < 2; k++) {
        int slot = fmUnixTimeSeconds / 9 - k;
        uint h = hashu(uint(slot));
        if (h % 100u >= 40u) continue;
        float t = float(fmUnixTimeSeconds - slot * 9) + ms - float((h >> 8u) % 7000u) / 1000.0;
        if (t < 0.0 || t > 1.8) continue;
        float strength = 0.55 + 0.45 * float((h >> 20u) % 100u) / 100.0;
        float f = exp(-t * 7.0);
        f += 0.8 * exp(-abs(t - 0.14) * 32.0) * float((h >> 4u) & 1u);
        f += 0.6 * exp(-abs(t - 0.33) * 26.0) * float((h >> 5u) & 1u);
        if (f * strength > flash) {
            flash = f * strength;
            boltX = float((h >> 12u) % 100u) / 50.0 - 1.0;
        }
    }
    return flash;
}

vec2 uvOf(vec2 s) {
    return vec2((s.x - X0) / (X1 - X0), 1.0 - s.y);
}

// The fire's breathing: slow swells and quick licks.
float flicker() {
    return 0.8 + 0.16 * vnoise(vec2(iTime * 5.0, 1.3)) + 0.06 * sin(iTime * 23.0) + 0.04 * sin(iTime * 37.0 + 1.1);
}

// Warm light cast by the campfire: a hot core, the circle it lights, and a faint wide spill.
float fireGlow(vec2 s) {
    float d = length((s - FIRE - vec2(0.0, 0.02)) * vec2(1.0, 1.35));
    return 0.10 * exp(-d * 22.0) + 0.05 * exp(-d * 7.0) + 0.012 * exp(-d * 2.2);
}

// Flame tongues over the logs.
vec3 flames(vec2 s) {
    vec2 p = (s - FIRE - vec2(0.0, 0.006)) / 0.034;
    if (p.y < -0.4 || p.y > 3.2 || abs(p.x) > 1.8) return vec3(0.0);
    float n = fbm(vec2(p.x * 2.2, p.y * 1.3 - iTime * 3.4));
    float width = 0.95 * (1.0 - clamp(p.y / 2.7, 0.0, 1.0));
    float sway = 0.18 * sin(p.y * 2.3 + iTime * 4.1) * clamp(p.y, 0.0, 2.0);
    float shape = width - abs(p.x - sway) - (n - 0.45) * 1.1;
    float body = smoothstep(0.0, 0.3, shape) * smoothstep(-0.4, 0.05, p.y);
    float heat = clamp(1.0 - p.y / 2.2 - abs(p.x) * 0.4, 0.0, 1.0);
    return mix(vec3(0.85, 0.12, 0.01), vec3(1.0, 0.78, 0.35), heat * heat) * (1.2 + 5.0 * heat * heat) * body;
}

// Sparks rising on the heat, each on its own loop.
float embers(vec2 s) {
    float e = 0.0;
    for (int i = 0; i < 18; i++) {
        float fi = float(i);
        float life = fract(iTime * (0.22 + 0.12 * hash21(vec2(fi, 1.0))) + hash21(vec2(fi, 7.0)));
        vec2 pos = FIRE + vec2((hash21(vec2(fi, 3.0)) - 0.5) * 0.05 + sin(life * 7.0 + fi) * 0.012 + life * 0.06,
                               0.02 + life * 0.3);
        e += smoothstep(0.0024, 0.0, length(s - pos)) * (1.0 - life) * (1.0 - life);
    }
    return e;
}

// Smoke leaning away on the wind, lit from below.
float smoke(vec2 s) {
    vec2 p = s - FIRE - vec2(0.0, 0.03);
    if (p.y < 0.0 || p.y > 0.6) return 0.0;
    float x = (p.x - p.y * p.y * 0.45) / (0.018 + p.y * 0.2);
    float n = fbm(vec2(p.x * 9.0 - iTime * 0.12, p.y * 6.0 - iTime * 0.5));
    return exp(-x * x * 1.5) * n * smoothstep(0.0, 0.05, p.y) * smoothstep(0.6, 0.15, p.y);
}

float rainLayer(vec2 s, float scale, float speed, float seed) {
    // Wrapped clock: float precision would blur the streaks after hours on the menu.
    vec2 p = vec2(s.x * scale + s.y * scale * 0.11, s.y * scale * 0.18 + mod(iTime, 1000.0) * speed);
    vec2 cell = floor(p);
    vec2 f = fract(p);
    float h = hash21(cell + seed);
    float x = 0.15 + 0.7 * hash21(cell + seed + 7.3);
    float streak = smoothstep(0.045, 0.0, abs(f.x - x)) * smoothstep(0.0, 0.22, f.y) * smoothstep(0.9, 0.5, f.y);
    return streak * step(0.55, h);
}

// Light scattered toward the viewer by the wet air: moonlight in the clouds and the fire's glow.
vec3 veil(vec2 s, float flash, float boltX, float glow) {
    float d = length((s - MOON) * vec2(0.62, 1.0));
    vec3 v = FOG + MOONLIGHT * (0.06 * exp(-d * 3.2) + 0.014 * exp(-d * 1.2)) + WARM * glow * 0.35;
    v += BOLT * flash * (0.05 + 0.10 * smoothstep(1.5, 0.0, abs(s.x - boltX)));
    return v;
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 s = vec2((fragCoord.x - 0.5 * iResolution.x) / iResolution.y, fragCoord.y / iResolution.y);
    vec2 mouse = fmMouse.zw;
    if (mouse.x <= 0.0 && mouse.y <= 0.0) mouse = vec2(0.5);
    vec2 par = clamp(mouse, 0.0, 1.0) - 0.5;
    float dither = hash21(fragCoord + fract(iTime) * 97.0);
    float boltX;
    float flash = lightning(boltX);
    float flick = flicker();
    vec2 midShift = par * vec2(0.006, 0.003);
    float glow = fireGlow(s - midShift) * flick;
    vec3 air = veil(s, flash, boltX, glow);
    float moonNear = exp(-length((s - MOON) * vec2(0.55, 1.0)) * 2.6);

    // Sky: black above, low cloud faintly silvered by the moon and torn open by lightning.
    vec3 col = mix(SKY_LOW, SKY_TOP, smoothstep(0.34, 1.0, s.y));
    vec2 cp = vec2(s.x * 1.3 + iTime * 0.011, s.y * 3.0);
    float cloud = fbm(cp + vec2(fbm(cp * 0.6 + vec2(iTime * 0.017, 0.0)), 0.0) * 0.9);
    col += MOONLIGHT * 0.06 * cloud * cloud * moonNear;
    // The moon shows only through thin cloud, a blurred disc.
    col += MOONLIGHT * 0.09 * smoothstep(0.034, 0.018, length(s - MOON)) * clamp(1.1 - cloud * 1.6, 0.0, 1.0);
    float boltReach = smoothstep(1.5, 0.0, abs(s.x - boltX));
    col += BOLT * flash * (0.04 + 1.5 * cloud * cloud * cloud) * (0.3 + 0.7 * boltReach) * smoothstep(0.35, 0.8, s.y);
    col += air;

    // Distant treeline: a shade darker than the air in front of it.
    vec4 farFar = texture(iChannel0, uvOf(s + par * vec2(0.004, 0.002)));
    col = mix(col, air * 0.78 + SKY_LOW * 0.3, farFar.r * 0.95);

    // The camp: dark timber and thatch, warmed where it faces the fire, doorways and torches glowing.
    vec4 farMid = texture(iChannel0, uvOf(s + midShift));
    vec2 toFire = normalize(FIRE + vec2(0.0, 0.03) - s);
    float facing = farMid.g * (1.0 - texture(iChannel0, uvOf(s + midShift + toFire * 0.0025)).g);
    col = mix(col, air * 0.5 + WARM * glow * 0.6, farMid.g);
    col += WARM * facing * glow * 2.2;
    float spill = 0.0;
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.785398 + dither;
        spill += texture(iChannel0, uvOf(s + midShift + vec2(cos(a), sin(a)) * 0.016)).b;
    }
    spill /= 8.0;
    float doorFlicker = 0.85 + 0.15 * vnoise(vec2(iTime * 4.0, floor(s.x * 30.0)));
    col = mix(col, WARM * 1.1 * doorFlicker, farMid.b);
    col += WARM * spill * 0.12 * doorFlicker;

    // Side conifers: black, a cool rim from the moon on the edges facing it.
    vec2 sideShift = par * vec2(0.010, 0.004);
    vec4 farNear = texture(iChannel0, uvOf(s + sideShift));
    vec2 toMoon = normalize(MOON - s);
    float edge = farNear.a * (1.0 - texture(iChannel0, uvOf(s + sideShift + toMoon * 0.0016)).a);
    edge *= 0.4 + 0.6 * vnoise(s * 160.0);
    col = mix(col, air * 0.22, farNear.a);
    col += (MOONLIGHT * 0.05 * moonNear + BOLT * flash * 0.35) * edge;

    // Ground: mud warmed around the fire, the trampled path and puddles mirroring the flames.
    vec2 groundShift = par * vec2(0.013, 0.004);
    vec4 near = texture(iChannel1, uvOf(s + groundShift));
    vec3 soil = air * 0.3 + WARM * glow * 0.9;
    col = mix(col, soil, near.r);
    vec2 ring = vec2(vnoise(s * vec2(90.0, 260.0) + vec2(0.0, iTime * 4.0)), vnoise(s * vec2(70.0, 220.0) - iTime * 3.0));
    vec2 mirror = vec2(s.x + (ring.x - 0.5) * 0.016, 2.0 * GROUND - s.y + (ring.y - 0.5) * 0.03);
    vec4 mirrored = texture(iChannel0, uvOf(mirror));
    // Reflections stretch vertically on wet ground.
    vec2 streak = vec2(mirror.x, FIRE.y + (mirror.y - FIRE.y) * 0.35);
    vec3 reflection = WARM * fireGlow(streak) * flick * 3.0 + WARM * mirrored.b * 0.16 + air * 0.7 + BOLT * flash * 0.12;
    reflection *= mix(1.0, 0.3, max(mirrored.a, mirrored.g * 0.6));
    float wet = near.g * near.r * smoothstep(0.0, 0.2, GROUND - s.y + 0.02);
    col = mix(col, reflection, wet * 0.8);

    // The fire itself, its smoke and sparks.
    float smokeAmount = smoke(s - midShift);
    vec3 smokeColor = air * 1.6 + WARM * 0.1 * exp(-(s.y - FIRE.y) * 9.0) * flick;
    col = mix(col, smokeColor, smokeAmount * 0.55);
    col += flames(s - midShift) * flick;
    col += vec3(1.0, 0.55, 0.2) * embers(s - midShift) * 1.6;
    col += WARM * glow * 0.25;

    // Mist over the clearing, drifting and lit by the fire.
    float mistShape = fbm(vec2(s.x * 2.2 - iTime * 0.05, s.y * 7.0 + iTime * 0.02));
    float mist = exp(-abs(s.y - 0.32) * 10.0) * (0.3 + 0.7 * mistShape);
    col += (air * 0.9 + WARM * glow * 0.4) * mist;

    // Rain: nearly invisible in the dark, bright where the fire lights it.
    float rain = rainLayer(s, 70.0, 5.0, 3.0) * 0.6 + rainLayer(s, 120.0, 7.5, 11.0) * 0.4;
    col += (WARM * glow * 7.0 + MOONLIGHT * 0.006 * (1.0 + moonNear)) * rain + BOLT * rain * flash * 0.2;

    // Filmic curve, then dither against banding in the dark gradients.
    col = col * (2.51 * col + 0.03) / (col * (2.43 * col + 0.59) + 0.14);
    col = pow(clamp(col, 0.0, 1.0), vec3(1.0 / 2.2));
    col += (dither - 0.5) / 255.0;
    fragColor = vec4(col, 1.0);
}
