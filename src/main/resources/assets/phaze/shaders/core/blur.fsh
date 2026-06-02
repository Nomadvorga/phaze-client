#version 150

#moj_import <phaze:common.glsl>

in vec2 FragCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;
uniform vec2 Size;
uniform vec4 Radius;
uniform float Smoothness;
uniform float BlurRadius;
uniform int BlurMode;

out vec4 fragColor;

vec3 sampleBlurCurrent() {
    vec2 texSize = vec2(textureSize(Sampler0, 0));
    vec2 texCoord = gl_FragCoord.xy / texSize;
    vec2 texel = 1.0 / texSize;

    if (BlurRadius <= 0.001) {
        return texture(Sampler0, texCoord).rgb;
    }

    // Soup blur.fsh port: gaussian pair sampling.
    // The source shader is separable; here we do horizontal + vertical in one masked pass
    // so it works on Minecraft's HUD blur pipeline without an extra broken FBO pass.
    const vec2 DIRS[2] = vec2[](
        vec2(1.000000, 0.000000),
        vec2(0.000000, 1.000000)
    );

    float sigma = max(0.35, 0.35 + BlurRadius * 0.55);
    float pairSupport = clamp(BlurRadius, 0.0, 16.0);
    int pairCount = int(clamp(ceil(pairSupport), 1.0, 16.0));
    vec3 acc = texture(Sampler0, texCoord).rgb;
    float weightSum = 1.0;

    for (int d = 0; d < 2; d++) {
        vec2 dir = DIRS[d] * texel;
        for (int i = 1; i <= 16; i++) {
            if (i > pairCount) {
                continue;
            }

            float fi = float(i);
            float tapFactor = clamp(pairSupport - float(i - 1), 0.0, 1.0);
            if (tapFactor <= 0.0) {
                continue;
            }
            float weight = exp(-0.5 * (fi * fi) / (sigma * sigma));
            weight *= tapFactor;
            vec2 offset = dir * fi;
            vec3 samplePair = texture(Sampler0, texCoord + offset).rgb
                            + texture(Sampler0, texCoord - offset).rgb;
            acc += samplePair * weight;
            weightSum += weight * 2.0;
        }
    }

    return acc / max(weightSum, 0.0001);
}

vec3 sampleBlurSoup() {
    vec2 texSize = vec2(textureSize(Sampler0, 0));
    vec2 texCoord = gl_FragCoord.xy / texSize;
    vec2 texel = 1.0 / texSize;
    vec2 directionScale = vec2(BlurRadius) * texel;

    if (BlurRadius <= 0.10) {
        return texture(Sampler0, texCoord).rgb;
    }

    const vec2 DIRS[8] = vec2[](
        vec2(1.000000, 0.000000),
        vec2(0.923880, 0.382683),
        vec2(0.707107, 0.707107),
        vec2(0.382683, 0.923880),
        vec2(0.000000, 1.000000),
        vec2(-0.382683, 0.923880),
        vec2(-0.707107, 0.707107),
        vec2(-0.923880, 0.382683)
    );

    float sigma = max(1.2, BlurRadius * 0.85);
    int pairCount = int(clamp(floor(BlurRadius * 0.95), 2.0, 12.0));
    int directionCount = 8;

    vec3 acc = texture(Sampler0, texCoord).rgb;
    float weightSum = 1.0;

    for (int d = 0; d < 8; d++) {
        if (d >= directionCount) {
            continue;
        }

        vec2 dir = DIRS[d] * directionScale;
        for (int i = 1; i <= 12; i++) {
            if (i > pairCount) {
                continue;
            }

            float fi = float(i);
            float w = exp(-0.5 * (fi * fi) / (sigma * sigma));
            vec2 offset = dir * fi;

            vec3 pair = texture(Sampler0, texCoord + offset).rgb
                      + texture(Sampler0, texCoord - offset).rgb;
            acc += pair * w;
            weightSum += 2.0 * w;
        }
    }

    return acc / max(weightSum, 0.0001);
}

vec3 sampleBlurKawase() {
    vec2 texSize = vec2(textureSize(Sampler0, 0));
    vec2 texCoord = gl_FragCoord.xy / texSize;
    vec2 texel = 1.0 / texSize;

    if (BlurRadius <= 0.10) {
        return texture(Sampler0, texCoord).rgb;
    }

    // Real Kawase, single-pass collapse. Classic Kawase is multiple
    // tiny passes with growing offsets (0.5, 1.5, 2.5, ...), each pass
    // sampling 4 diagonal taps. Multi-pass Kawase produces a wide
    // visual blur for very few samples - the canonical "blur with
    // 4 taps that looks like 35-tap Gaussian" trick.
    //
    // We collapse the pass loop into one fragment shader by emitting
    // every ring at once and accumulating. Sample count: up to
    // 1 + 6 * 4 = 25 taps, vs. 64 for the separable Gaussian path
    // and 192 for the Soup radial - 2.5x to 7.7x cheaper at matching
    // perceptual radius.
    //
    // Without per-ring weighting a single-pass Kawase shows visible
    // banding at the ring edges (the original algorithm hides this by
    // ping-ponging between FBOs which spatially low-passes between
    // rings). To compensate we apply a Gaussian falloff per ring so
    // outer rings contribute progressively less - this matches the
    // smoothness of multi-pass Kawase without the second framebuffer.

    int passes = int(clamp(BlurRadius * 0.5, 1.0, 6.0));
    float invPasses = 1.0 / float(passes);

    vec3 acc = texture(Sampler0, texCoord).rgb;
    float weightSum = 1.0;

    for (int p = 0; p < 6; p++) {
        if (p >= passes) {
            continue;
        }

        // Ring offset in pixels, mapped so the outermost ring lands at
        // {@code BlurRadius} regardless of how many rings we run.
        float passOffset = (float(p) + 0.5) * BlurRadius * invPasses;
        vec2 o = texel * passOffset;

        // The four diagonal taps - canonical Kawase pattern.
        vec3 ring = texture(Sampler0, texCoord + vec2( o.x,  o.y)).rgb
                  + texture(Sampler0, texCoord + vec2( o.x, -o.y)).rgb
                  + texture(Sampler0, texCoord + vec2(-o.x,  o.y)).rgb
                  + texture(Sampler0, texCoord + vec2(-o.x, -o.y)).rgb;

        // Gaussian-shaped per-ring falloff: outer rings contribute
        // less so the cumulative result reads as a smooth bell curve
        // rather than a stack of flat rings. Sigma here (1.5 * passes)
        // is wide enough that all rings get a meaningful weight while
        // still tapering toward the edge.
        float fp = float(p);
        float sigma = max(1.0, float(passes) * 0.6);
        float w = exp(-0.5 * (fp * fp) / (sigma * sigma));

        acc += ring * w;
        weightSum += 4.0 * w;
    }

    return acc / max(weightSum, 0.0001);
}

vec3 sampleBlurBox() {
    vec2 texSize = vec2(textureSize(Sampler0, 0));
    vec2 texCoord = gl_FragCoord.xy / texSize;
    vec2 texel = 1.0 / texSize;

    if (BlurRadius <= 0.10) {
        return texture(Sampler0, texCoord).rgb;
    }

    // Box blur - simple average of neighboring pixels
    int radius = int(clamp(BlurRadius, 1.0, 8.0));
    vec3 acc = vec3(0.0);
    int sampleCount = 0;

    // Sample in a box around the pixel
    for (int x = -radius; x <= radius; x++) {
        for (int y = -radius; y <= radius; y++) {
            vec2 offset = vec2(float(x), float(y)) * texel;
            acc += texture(Sampler0, texCoord + offset).rgb;
            sampleCount++;
        }
    }

    return acc / float(sampleCount);
}

vec3 sampleBlur() {
    if (BlurMode == 3) {
        return sampleBlurBox();
    }
    if (BlurMode == 2) {
        return sampleBlurKawase();
    }
    if (BlurMode == 1) {
        return sampleBlurSoup();
    }
    return sampleBlurCurrent();
}

void main() {
    vec4 color = vec4(sampleBlur(), 1.0) * FragColor;
    color.a *= ralpha(Size, FragCoord, Radius, Smoothness);

    if (color.a == 0.0) {
        discard;
    }

    fragColor = color;
}

