#version 330

// The camcorder filter (see dev.noclip.backrooms.core.look.Look): light that falls off fast, a wide cheap lens with
// colour fringes, bloom around lights, a cold washed-out grade, vignette, grain, faint scanlines and a tracking line
// rolling down now and then. The fade into black with distance is the game's fog, set by Look.darken.

uniform sampler2D InSampler;
// 2x1, written every tick. Pixel 0: red and green hold the tick count (high and low byte), blue a random number.
// Pixel 1, this level's mood: blue = how fast light falls off, 0..1 (red and green, the fog, are used by Look).
uniform sampler2D ClockSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

vec3 tap(vec2 uv) {
    return texture(InSampler, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
}

void main() {
    vec4 clock = texelFetch(ClockSampler, ivec2(0, 0), 0);
    vec4 mood = texelFetch(ClockSampler, ivec2(1, 0), 0);
    float seconds = (floor(clock.r * 255.0 + 0.5) * 256.0 + floor(clock.g * 255.0 + 0.5)) / 20.0;
    float frame = floor(clock.b * 255.0 + 0.5);
    float falloff = mood.b;
    vec2 px = 1.0 / InSize;
    vec2 c = texCoord - 0.5;
    float r2 = dot(c, c);

    // A wide, cheap lens: barrel distortion and colour fringes that grow toward the edges.
    vec2 uv = 0.5 + c * (0.93 + 0.16 * r2);
    float row = floor(uv.y * InSize.y / 2.0);
    uv.x += (hash(vec3(row, frame, 7.0)) - 0.5) * px.x * 1.2;
    vec2 fringe = c * (0.002 + 0.02 * r2);
    vec3 color = vec3(tap(uv + fringe).r, tap(uv).g, tap(uv - fringe).b);
    vec3 soft = (tap(uv + vec2(px.x, 0.0)) + tap(uv - vec2(px.x, 0.0)) + tap(uv + vec2(0.0, px.y)) + tap(uv - vec2(0.0, px.y))) * 0.25;
    color = mix(color, soft, 0.15);

    // Exposed for the lamps: with the lightmap's fast falloff (Look.falloff) what is near a lamp comes out bright,
    // what is not stays dark.
    color *= 1.0 + falloff * 1.15;

    // Bloom: bright pixels nearby bleed into this one.
    vec3 glow = vec3(0.0);
    float reach = InSize.y / 540.0;
    for (int i = 0; i < 12; i++) {
        float a = float(i) * 0.5236;
        for (int k = 1; k <= 2; k++) {
            glow += max(tap(uv + vec2(cos(a), sin(a)) * px * (3.0 + 7.0 * float(k)) * reach) - 0.75, 0.0);
        }
    }
    color += glow * (2.0 / 24.0);

    // Camcorder colour: a little washed out and yellow, highlights that roll off, blacks lifted only slightly.
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(vec3(luma), color, 0.88) * vec3(1.03, 1.0, 0.9);
    color = color * 1.2 / (1.0 + 0.2 * color);
    color = color * 0.97 + 0.018;

    // Vignette.
    color *= 1.0 - smoothstep(0.12, 0.5, r2) * 0.55;
    // Grain, new every tick, stronger in the dark the way a sensor's noise is.
    color += (hash(vec3(gl_FragCoord.xy, frame)) - 0.5) * (0.05 + 0.05 * (1.0 - smoothstep(0.0, 0.3, luma)));
    // Faint scanlines, and a noisy tracking line rolling down the picture every quarter minute or so.
    color *= 0.985 + 0.015 * sin(gl_FragCoord.y * 3.14159);
    float line = 1.0 - fract(seconds / 14.0);
    float near = smoothstep(0.012, 0.0, abs(texCoord.y - line));
    color += (hash(vec3(gl_FragCoord.xy * 0.5, frame + 3.0)) - 0.3) * 0.08 * near;

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
