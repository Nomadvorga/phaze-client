#version 150

in vec2 texCoord;

uniform sampler2D Sampler0;
uniform vec2 Direction;
uniform vec2 TexelSize;
uniform int Support;
uniform float Sigma;
uniform float Brightness;

out vec4 fragColor;

void main() {
    float sigma = max(Sigma, 0.0001);
    vec4 result = texture(Sampler0, texCoord);
    float weightSum = 1.0;

    for (int i = 1; i <= 64; i++) {
        if (i > Support) {
            continue;
        }

        float fi = float(i);
        float weight = exp(-0.5 * (fi * fi) / (sigma * sigma));
        vec2 offset = Direction * TexelSize * fi;
        vec4 samplePair = texture(Sampler0, texCoord + offset) + texture(Sampler0, texCoord - offset);
        result += samplePair * weight;
        weightSum += weight * 2.0;
    }

    result /= max(weightSum, 0.0001);
    fragColor = vec4(result.rgb, result.a * Brightness);
}

