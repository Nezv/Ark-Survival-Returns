// Ark: Survival Returns title scene, background layer (FancyMenu "GLSL Shader" menu background).
// Night, rain, fog and a floodlit outpost behind the creature element.
//
// Scene units: x from the screen centre in screen heights, y from the bottom (0..1). The textures cover
// x -1.2..1.2; the creature element and tools/build_title_scene.py use the same units.
// iChannel0 scene_far.png : R distant treeline, G outpost, B warm windows, A side conifers
// iChannel1 scene_near.png: R ground, G wet path and puddles, B ferns, A near fronds
// Lightning follows the strike clock shared with foreground.glsl and the Java element (StormClock).

const float X0 = -1.2;
const float X1 = 1.2;
const vec2 LIGHT = vec2(0.465, 0.745);
const float GROUND = 0.318;

const vec3 SKY_TOP = vec3(0.0004, 0.0006, 0.0009);
const vec3 SKY_LOW = vec3(0.0028, 0.0048, 0.0070);
const vec3 FOG = vec3(0.0036, 0.0060, 0.0086);
const vec3 LAMP = vec3(0.80, 0.92, 1.00);
const vec3 WARM = vec3(1.00, 0.50, 0.19);
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

// Light from the floodlight head reaching a point: the bright core, its halo and the wide wet haze.
float lampGlow(vec2 s) {
    vec2 d = s - LIGHT;
    float r2 = dot(d * vec2(0.8, 1.0), d * vec2(0.8, 1.0));
    return 0.0009 / (r2 + 0.00012) + 0.30 * exp(-sqrt(r2) * 9.0) + 0.05 * exp(-sqrt(r2) * 2.5);
}

// The lamp points down toward the viewer: a broad cone of lit rain and mist below it.
float lampCone(vec2 s) {
    vec2 d = s - LIGHT;
    if (d.y > 0.0) return 0.0;
    float spread = abs(d.x + 0.12 * d.y) / (-d.y + 0.02);
    return smoothstep(1.6, 0.2, spread) * exp(d.y * 1.7) * smoothstep(0.0, 0.08, -d.y);
}

float godRays(vec2 s, vec2 shift, float dither) {
    vec2 delta = (LIGHT - s) / 20.0;
    vec2 p = s + delta * dither;
    float lit = 0.0;
    float weight = 1.0;
    float total = 0.0;
    for (int i = 0; i < 20; i++) {
        vec4 f = texture(iChannel0, uvOf(p + shift));
        float occluder = max(max(f.g, f.a), f.r * 0.55);
        lit += (1.0 - occluder) * weight;
        total += weight;
        weight *= 0.93;
        p += delta;
    }
    return lit / total;
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

// Light scattered toward the viewer by the wet air: the lamp's halo plus a little ambient skyglow.
vec3 veil(vec2 s, float flash, float boltX) {
    float d = length((s - LIGHT) * vec2(0.62, 1.0));
    vec3 v = FOG + LAMP * (0.13 * exp(-d * 5.0) + 0.010 * exp(-d * 1.5));
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
    vec3 air = veil(s, flash, boltX);
    float lampNear = exp(-length((s - LIGHT) * vec2(0.55, 1.0)) * 2.4);

    // Sky: black above, the low cloud deck lit from below by the floodlight and from within by lightning.
    vec3 col = mix(SKY_LOW, SKY_TOP, smoothstep(0.34, 1.0, s.y));
    vec2 cp = vec2(s.x * 1.3 + iTime * 0.011, s.y * 3.0);
    float cloud = fbm(cp + vec2(fbm(cp * 0.6 + vec2(iTime * 0.017, 0.0)), 0.0) * 0.9);
    col += LAMP * 0.10 * cloud * cloud * lampNear;
    float boltReach = smoothstep(1.5, 0.0, abs(s.x - boltX));
    col += BOLT * flash * (0.04 + 1.5 * cloud * cloud * cloud) * (0.3 + 0.7 * boltReach) * smoothstep(0.35, 0.8, s.y);
    col += air;

    // Distant treeline: a shade darker than the air in front of it.
    vec4 farFar = texture(iChannel0, uvOf(s + par * vec2(0.004, 0.002)));
    col = mix(col, air * 0.78 + SKY_LOW * 0.3, farFar.r * 0.95);

    // Outpost: dark concrete, the lit bays, and the warm spill around them.
    vec2 midShift = par * vec2(0.006, 0.003);
    vec4 farMid = texture(iChannel0, uvOf(s + midShift));
    col = mix(col, air * 0.55, farMid.g);
    float spill = 0.0;
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.785398 + dither;
        spill += texture(iChannel0, uvOf(s + midShift + vec2(cos(a), sin(a)) * 0.02)).b;
    }
    spill /= 8.0;
    float windowFlicker = 0.94 + 0.06 * vnoise(vec2(iTime * 3.0, floor(s.x * 40.0)));
    col = mix(col, WARM * 1.25 * windowFlicker, farMid.b);
    col += WARM * spill * 0.12;

    // Side conifers: black, their edges catching the floodlight.
    vec2 sideShift = par * vec2(0.010, 0.004);
    vec4 farNear = texture(iChannel0, uvOf(s + sideShift));
    vec2 toLight = normalize(LIGHT - s);
    float edge = farNear.a * (1.0 - texture(iChannel0, uvOf(s + sideShift + toLight * 0.0016)).a);
    edge *= 0.4 + 0.6 * vnoise(s * 160.0);
    col = mix(col, air * 0.22, farNear.a);
    col += (LAMP * 0.45 * lampNear * lampNear * lampNear + BOLT * flash * 0.35) * edge;

    // Ground and the wet path mirroring the lights, broken by rain rings.
    vec2 groundShift = par * vec2(0.013, 0.004);
    vec4 near = texture(iChannel1, uvOf(s + groundShift));
    float gateLight = exp(-length((s - vec2(0.33, GROUND)) * vec2(1.2, 3.0)) * 3.0);
    vec3 soil = air * 0.32 + WARM * 0.035 * gateLight;
    col = mix(col, soil, near.r);
    vec2 ring = vec2(vnoise(s * vec2(90.0, 260.0) + vec2(0.0, iTime * 4.0)), vnoise(s * vec2(70.0, 220.0) - iTime * 3.0));
    vec2 mirror = vec2(s.x + (ring.x - 0.5) * 0.016, 2.0 * GROUND - s.y + (ring.y - 0.5) * 0.03);
    vec4 mirrored = texture(iChannel0, uvOf(mirror));
    // Reflections stretch vertically on wet ground.
    vec2 streak = vec2(mirror.x, LIGHT.y + (mirror.y - LIGHT.y) * 0.3);
    vec3 reflection = LAMP * lampGlow(streak) * 0.10 + WARM * mirrored.b * 0.16 + air * 0.7 + BOLT * flash * 0.12;
    reflection *= mix(1.0, 0.3, max(mirrored.a, mirrored.g * 0.6));
    float wet = near.g * near.r * smoothstep(0.0, 0.2, GROUND - s.y + 0.02);
    col = mix(col, reflection, wet * 0.8);

    // Mist over the clearing, drifting and lit by the lamp and the gate.
    float mistShape = fbm(vec2(s.x * 2.2 - iTime * 0.05, s.y * 7.0 + iTime * 0.02));
    float mist = exp(-abs(s.y - 0.32) * 10.0) * (0.3 + 0.7 * mistShape);
    col += (air * 0.9 + WARM * 0.03 * gateLight) * mist;

    // Floodlight: shafts through the trees, the cone of lit rain, the lamp itself.
    float d = length(s - LIGHT);
    if (d < 1.3) {
        float rays = godRays(s, par * vec2(0.008, 0.003), dither);
        col += LAMP * rays * rays * 0.035 * exp(-d * 2.2) * (1.0 - near.r * 0.7);
    }
    float cone = lampCone(s);
    col += LAMP * cone * (0.03 + 0.06 * mistShape);
    float rain = rainLayer(s, 70.0, 5.0, 3.0) * 0.6 + rainLayer(s, 120.0, 7.5, 11.0) * 0.4;
    col += LAMP * rain * (0.004 + 0.35 * cone + 0.25 * lampNear * lampNear) + BOLT * rain * flash * 0.2;
    col += LAMP * lampGlow(s) * 0.5;
    col += LAMP * 0.05 * exp(-abs(s.y - LIGHT.y) * 180.0) * exp(-abs(s.x - LIGHT.x) * 3.0);

    // Filmic curve, then dither against banding in the dark gradients.
    col = col * (2.51 * col + 0.03) / (col * (2.43 * col + 0.59) + 0.14);
    col = pow(clamp(col, 0.0, 1.0), vec3(1.0 / 2.2));
    col += (dither - 0.5) / 255.0;
    fragColor = vec4(col, 1.0);
}
