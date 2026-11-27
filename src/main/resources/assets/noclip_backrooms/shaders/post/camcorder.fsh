#version 330

// Unified non-shader lighting + real-time flashlight cone + instant-toggle VHS camcorder filter
// (see dev.noclip.backrooms.core.look.Look).
// ClockSampler is 4x1, uploaded without reloading the post-effect pipeline:
//   Pixel 0: r/g = tick count (hi/lo byte), b = frame random for grain
//   Pixel 1: r = fog start, g = fog end, b = lightmap falloff (0..1)
//   Pixel 2: r = camcorder VHS toggle (0 or 1), g = flashlight beam intensity (0..1), b = flashlight style (0 warm, 1 infinite arc)
//   Pixel 3: r = beam center X offset (0.5 = center), g = beam center Y offset (0.5 = center), b = normalized hit distance (0..1)

uniform sampler2D InSampler;
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
    vec4 flags = texelFetch(ClockSampler, ivec2(2, 0), 0);
    vec4 beamData = texelFetch(ClockSampler, ivec2(3, 0), 0);

    float seconds = (floor(clock.r * 255.0 + 0.5) * 256.0 + floor(clock.g * 255.0 + 0.5)) / 20.0;
    float frame = floor(clock.b * 255.0 + 0.5);
    float falloff = mood.b;
    bool camOn = flags.r > 0.5;
    float flStrength = flags.g;
    float flInfinite = flags.b;

    vec2 px = 1.0 / InSize;
    vec2 c = texCoord - 0.5;
    float r2 = dot(c, c);

    // 1. Base sample: crisp native view when VHS filter is OFF, subtle wide-lens + chroma fringe when ON.
    vec2 uv = texCoord;
    vec3 color;
    if (camOn) {
        uv = 0.5 + c * (0.95 + 0.12 * r2);
        float row = floor(uv.y * InSize.y / 2.0);
        uv.x += (hash(vec3(row, frame, 7.0)) - 0.5) * px.x * 0.9;
        vec2 fringe = c * (0.0015 + 0.014 * r2);
        color = vec3(tap(uv + fringe).r, tap(uv).g, tap(uv - fringe).b);
        vec3 soft = (tap(uv + vec2(px.x, 0.0)) + tap(uv - vec2(px.x, 0.0))
                   + tap(uv + vec2(0.0, px.y)) + tap(uv - vec2(0.0, px.y))) * 0.25;
        color = mix(color, soft, 0.12);
    } else {
        color = tap(uv);
    }

    // 2. Non-shader lighting contrast: keep unlit darkness deep while letting lit 5-block lamp pools breathe.
    float baseLuma = dot(color, vec3(0.299, 0.587, 0.114));
    float litLift = smoothstep(0.04, 0.45, baseLuma);
    color *= 1.0 + falloff * (0.35 + 0.85 * litLift);

    // 3. Luminous lamp bloom (always active when shaders are off so glowing lights keep a real halo even with F7 off).
    vec3 glow = vec3(0.0);
    float reach = InSize.y / 540.0;
    for (int i = 0; i < 12; i++) {
        float a = float(i) * 0.5236;
        vec2 dir = vec2(cos(a), sin(a));
        for (int k = 1; k <= 2; k++) {
            vec3 s = tap(uv + dir * px * (3.5 + 7.5 * float(k)) * reach);
            glow += max(s - vec3(0.66), vec3(0.0));
        }
    }
    color += glow * (2.2 / 24.0);

    // 4. Real-time smooth directional flashlight beam cone (dual hotspot + wide spill + optical reflector ring).
    if (flStrength > 0.005) {
        vec2 flCenter = vec2(0.5 + (beamData.r - 0.5) * 0.24, 0.5 + (beamData.g - 0.5) * 0.24);
        float flDist = beamData.b; // 0 = very close wall, 1 = far corridor
        float aspect = InSize.x / max(InSize.y, 1.0);
        vec2 delta = vec2((uv.x - flCenter.x) * aspect, uv.y - flCenter.y);
        float d = length(delta);

        float coreR = mix(0.19, 0.105, flDist);
        float spillR = mix(0.50, 0.34, flDist);
        float hotspot = exp(-(d * d) / max(coreR * coreR, 0.0005));
        float spill = exp(-(d * d) / max(spillR * spillR, 0.001));
        float ring = exp(-pow((d - coreR * 1.48) / 0.038, 2.0)) * 0.16;

        float distAtten = mix(1.22, 0.68, flDist * flDist);
        float beam = (hotspot * 0.72 + spill * 0.42 + ring) * flStrength * distAtten;
        vec3 tint = mix(vec3(1.06, 0.96, 0.80), vec3(0.90, 0.99, 1.08), flInfinite);

        // Reveal surface albedo inside the beam while lifting dark corridors naturally without turning void gray
        float surfaceMask = smoothstep(0.012, 0.085, baseLuma);
        color = color * (1.0 + beam * tint * (1.15 + 0.55 * surfaceMask))
              + tint * beam * (0.045 + 0.065 * hotspot) * (0.35 + 0.65 * surfaceMask);

        // Gentle periphery contrast outside the spill cone so the spotlight feels crisp
        color *= 1.0 - 0.09 * flStrength * smoothstep(spillR * 0.75, spillR * 1.65, d);
    }

    // 5. Optional VHS camcorder look (toggled instantaneously via F7 without rebuilding the pipeline).
    if (camOn) {
        float luma = dot(color, vec3(0.299, 0.587, 0.114));
        color = mix(vec3(luma), color, 0.90) * vec3(1.02, 1.0, 0.92);
        color = color * 1.15 / (1.0 + 0.15 * color);
        color = color * 0.985 + 0.012;

        // Vignette (lighter than before so lamps near edges stay legible)
        color *= 1.0 - smoothstep(0.16, 0.56, r2) * 0.40;
        // Sensor grain, slightly stronger in dark shadows
        color += (hash(vec3(gl_FragCoord.xy, frame)) - 0.5) * (0.038 + 0.042 * (1.0 - smoothstep(0.0, 0.28, luma)));
        // Subtle scanlines + occasional rolling tracking bar
        color *= 0.988 + 0.012 * sin(gl_FragCoord.y * 3.14159);
        float line = 1.0 - fract(seconds / 14.0);
        float near = smoothstep(0.010, 0.0, abs(texCoord.y - line));
        color += (hash(vec3(gl_FragCoord.xy * 0.5, frame + 3.0)) - 0.3) * 0.065 * near;
    } else {
        // Gentle filmic highlight roll-off when VHS filter is OFF so lamps & flashlight hotspot never harshly clip
        color = color / (1.0 + 0.12 * max(color - vec3(0.85), vec3(0.0)));
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
