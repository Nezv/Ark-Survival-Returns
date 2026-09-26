// Ark: Survival Returns title scene, foreground layer (FancyMenu "GLSL Shader" element, full screen, blended).
// Drawn over the creature: swaying ferns, near rain, ground mist, the campfire's warm haze, lightning and
// a vignette. Same scene units, textures and strike clock as background.glsl.
// iChannel0 scene_far.png, iChannel1 scene_near.png (B ferns, A near fronds)

const float X0 = -1.2;
const float X1 = 1.2;
const vec2 FIRE = vec2(-0.02, 0.318);

const vec3 FERN = vec3(0.010, 0.016, 0.012);
const vec3 MIST = vec3(0.060, 0.078, 0.092);
const vec3 WARM = vec3(1.00, 0.52, 0.2);
const vec3 RAIN = vec3(0.55, 0.62, 0.72);
const vec3 BOLT = vec3(0.70, 0.78, 1.00);

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
    for (int i = 0; i < 4; i++) {
        sum += amp * vnoise(p);
        p = p * 2.07 + vec2(11.3, 5.9);
        amp *= 0.5;
    }
    return sum;
}

// Same strike clock as background.glsl and StormClock.java.
float lightning() {
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
        flash = max(flash, f * strength);
    }
    return flash;
}

vec2 uvOf(vec2 s) {
    return vec2((s.x - X0) / (X1 - X0), 1.0 - s.y);
}

// Fronds bend with the wind: the higher above the ground, the further a leaf travels. Gusts roll through
// every few seconds; a small ripple keeps single fronds moving between them.
vec2 sway(vec2 s, float gust, float stiffness) {
    float lift = max(s.y + 0.06, 0.0);
    float bend = (gust * 0.02 + 0.004 * sin(iTime * 2.3 + s.y * 9.0 + s.x * 4.0)) * pow(lift, 1.35) / stiffness;
    // Sample where the leaf rests: opposite the wind, and a little higher as bent tips dip.
    return vec2(s.x - bend, s.y + abs(bend) * 0.15);
}

float rainStreaks(vec2 s, float scale, float speed, float seed, float width) {
    vec2 p = vec2(s.x * scale + s.y * scale * 0.12, s.y * scale * 0.09 + mod(iTime, 1000.0) * speed);
    vec2 cell = floor(p);
    vec2 f = fract(p);
    float h = hash21(cell + seed);
    float x = 0.2 + 0.6 * hash21(cell + seed + 3.7);
    float body = smoothstep(width, 0.0, abs(f.x - x)) * smoothstep(0.05, 0.35, f.y) * smoothstep(0.95, 0.6, f.y);
    return body * step(0.62, h) * (0.5 + 0.5 * hash21(cell + seed + 9.1));
}

void over(inout vec4 acc, vec3 color, float alpha) {
    alpha = clamp(alpha, 0.0, 1.0);
    acc.rgb = color * alpha + acc.rgb * (1.0 - alpha);
    acc.a = alpha + acc.a * (1.0 - alpha);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 s = vec2((fragCoord.x - 0.5 * iResolution.x) / iResolution.y, fragCoord.y / iResolution.y);
    vec2 mouse = fmMouse.zw;
    if (mouse.x <= 0.0 && mouse.y <= 0.0) mouse = vec2(0.5);
    vec2 par = clamp(mouse, 0.0, 1.0) - 0.5;
    float flash = lightning();
    float gust = sin(iTime * 0.55) * 0.6 + sin(iTime * 1.7 + 1.3) * 0.25 + (vnoise(vec2(iTime * 0.35, 3.1)) - 0.5) * 1.2;
    float fireNear = exp(-length((s - FIRE - vec2(0.0, 0.03)) * vec2(1.0, 1.3)) * 5.0)
                   * (0.85 + 0.15 * vnoise(vec2(iTime * 5.0, 1.3)));
    vec4 acc = vec4(0.0);

    // Air between the viewer and the creature: a thin veil, the fire's warm haze, mist rolling over the ground.
    over(acc, mix(MIST, WARM * 0.35, fireNear), 0.03 + 0.08 * fireNear);
    float mistShape = fbm(vec2(s.x * 1.7 - iTime * 0.07, s.y * 5.0 + iTime * 0.03));
    float mist = smoothstep(0.34, 0.0, s.y) * (0.25 + 0.75 * mistShape);
    over(acc, mix(MIST, WARM * 0.3, fireNear * 0.6), mist * 0.30);

    // Rain close to the camera: long streaks, lit by the fire and the sky.
    float light = 0.06 + 1.4 * fireNear + 1.6 * flash;
    float rain = rainStreaks(s, 26.0, 4.2, 1.0, 0.035) + rainStreaks(s, 41.0, 5.6, 5.0, 0.03) * 0.7;
    over(acc, mix(mix(RAIN, WARM, fireNear), BOLT, flash), rain * 0.16 * light);

    // Ferns: black-green, a wet rim toward the fire, a flicker of highlight when lightning hits.
    vec2 fernAt = sway(s + par * vec2(0.022, 0.006), gust, 1.0);
    float fern = texture(iChannel1, uvOf(fernAt)).b;
    vec2 toLight = normalize(FIRE - s);
    float rim = fern * (1.0 - texture(iChannel1, uvOf(fernAt + toLight * 0.0035)).b);
    vec3 fernColor = FERN + (WARM * 0.08 * (0.2 + fireNear) + BOLT * flash * 0.45) * rim;
    over(acc, fernColor, fern);

    // Nearest fronds, out of focus.
    vec2 frondAt = sway(s + par * vec2(0.034, 0.008), gust * 0.8, 1.6);
    float frond = texture(iChannel1, uvOf(frondAt)).a;
    over(acc, FERN * 0.6 + BOLT * flash * 0.04, frond * 0.97);

    // Lightning lights the whole frame for an instant; the vignette holds the corners dark.
    over(acc, BOLT, flash * 0.16);
    vec2 v = fragCoord / iResolution.xy - 0.5;
    over(acc, vec3(0.0), smoothstep(0.35, 0.85, length(v * vec2(1.0, 0.8))) * 0.55);

    float dither = (hash21(fragCoord + fract(iTime) * 61.0) - 0.5) / 255.0;
    vec3 color = acc.a > 0.0001 ? acc.rgb / acc.a : vec3(0.0);
    fragColor = vec4(color + dither, acc.a);
}
