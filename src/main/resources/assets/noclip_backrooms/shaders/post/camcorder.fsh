#version 330

// The camcorder filter (see dev.noclip.backrooms.core.look.Look): a cheap lens, a soft sensor, bloom around lights,
// camcorder colour, vignette, grain, faint scanlines and a tracking line rolling down now and then.

uniform sampler2D InSampler;
// 1x1, written every tick: red and green hold the tick count (high and low byte), blue a random number.
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
    float seconds = (floor(clock.r * 255.0 + 0.5) * 256.0 + floor(clock.g * 255.0 + 0.5)) / 20.0;
    float frame = floor(clock.b * 255.0 + 0.5);
    vec2 px = 1.0 / InSize;
    vec2 c = texCoord - 0.5;
    float r2 = dot(c, c);

    // Lens: a little barrel distortion, colour fringes toward the edges.
    vec2 uv = 0.5 + c * (0.97 + 0.07 * r2);
    // The tape wobbles sideways by a fraction of a pixel, two rows at a time.
    float row = floor(uv.y * InSize.y / 2.0);
    uv.x += (hash(vec3(row, frame, 7.0)) - 0.5) * px.x * 1.2;
    vec2 fringe = c * 0.006 * (0.25 + r2 * 3.0);
    vec3 color = vec3(tap(uv + fringe).r, tap(uv).g, tap(uv - fringe).b);

    // A soft sensor smears fine detail a little.
    vec3 soft = (tap(uv + vec2(px.x, 0.0)) + tap(uv - vec2(px.x, 0.0)) + tap(uv + vec2(0.0, px.y)) + tap(uv - vec2(0.0, px.y))) * 0.25;
    color = mix(color, soft, 0.35);

    // Bloom: bright pixels nearby bleed into this one.
    vec3 glow = vec3(0.0);
    float reach = InSize.y / 540.0;
    for (int i = 0; i < 12; i++) {
        float a = float(i) * 0.5236;
        for (int k = 1; k <= 2; k++) {
            glow += max(tap(uv + vec2(cos(a), sin(a)) * px * (3.0 + 6.0 * float(k)) * reach) - 0.72, 0.0);
        }
    }
    color += glow * (1.6 / 24.0);

    // Camcorder colour: a little less saturated, warm, highlights that roll off, lifted blacks.
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(vec3(luma), color, 0.92) * vec3(1.05, 1.0, 0.88);
    color = color * 1.25 / (1.0 + 0.25 * color);
    color = color * 0.94 + 0.035;

    // Vignette.
    color *= 1.0 - smoothstep(0.18, 0.55, r2) * 0.45;
    // Grain, new every tick.
    color += (hash(vec3(gl_FragCoord.xy, frame)) - 0.5) * 0.06;
    // Faint scanlines, and a noisy tracking line rolling down the picture every quarter minute or so.
    color *= 0.985 + 0.015 * sin(gl_FragCoord.y * 3.14159);
    float line = 1.0 - fract(seconds / 14.0);
    float near = smoothstep(0.012, 0.0, abs(texCoord.y - line));
    color += (hash(vec3(gl_FragCoord.xy * 0.5, frame + 3.0)) - 0.3) * 0.08 * near;

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
