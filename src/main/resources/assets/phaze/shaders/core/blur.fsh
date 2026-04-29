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

    if (BlurRadius <= 0.10) {
        return texture(Sampler0, texCoord).rgb;
    }

    // Soup blur.fsh port: gaussian pair sampling.
    // The source shader is separable; here we do horizontal + vertical in one masked pass
    // so it works on Minecraft's HUD blur pipeline without an extra broken FBO pass.
    const vec2 DIRS[2] = vec2[](
        vec2(1.000000, 0.000000),
        vec2(0.000000, 1.000000)
    );

    float sigma = max(1.0, BlurRadius * 0.55);
    int pairCount = int(clamp(floor(BlurRadius), 1.0, 16.0));
    vec3 acc = texture(Sampler0, texCoord).rgb;
    float weightSum = 1.0;

    for (int d = 0; d < 2; d++) {
        vec2 dir = DIRS[d] * texel;
        for (int i = 1; i <= 16; i++) {
            if (i > pairCount) {
                continue;
            }

            float fi = float(i);
            float weight = exp(-0.5 * (fi * fi) / (sigma * sigma));
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

    if (BlurRadius <= 0.10) {
        return texture(Sampler0, texCoord).rgb;
    }

    vec2 texel = 1.0 / texSize;
    float offsetScale = max(1.0, BlurRadius * 0.75);
    vec2 d = texel * offsetScale;

    vec3 color = texture(Sampler0, texCoord).rgb * 4.0;
    color += texture(Sampler0, texCoord + vec2(-d.x, -d.y)).rgb;
    color += texture(Sampler0, texCoord + vec2( d.x, -d.y)).rgb;
    color += texture(Sampler0, texCoord + vec2(-d.x,  d.y)).rgb;
    color += texture(Sampler0, texCoord + vec2( d.x,  d.y)).rgb;

    vec2 d2 = d * 0.5;
    color += texture(Sampler0, texCoord + vec2(-d2.x, 0.0)).rgb * 0.75;
    color += texture(Sampler0, texCoord + vec2( d2.x, 0.0)).rgb * 0.75;
    color += texture(Sampler0, texCoord + vec2(0.0, -d2.y)).rgb * 0.75;
    color += texture(Sampler0, texCoord + vec2(0.0,  d2.y)).rgb * 0.75;

    return color / 11.0;
}

vec3 sampleBlur() {
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


