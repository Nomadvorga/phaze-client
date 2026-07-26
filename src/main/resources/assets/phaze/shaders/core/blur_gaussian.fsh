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
    float gaussianFactor = -0.5 / (sigma * sigma);
    vec4 result = texture(Sampler0, texCoord);
    float weightSum = 1.0;

    // Optimized separable Gaussian using bilinear pair sampling:
    // each iteration folds two neighboring taps into one filtered
    // sample per side, which keeps the blur profile close to a true
    // Gaussian while nearly halving texture fetches versus the old
    // 1-tap-per-pixel loop.
    for (int i = 1; i <= 64; i += 2) {
        if (i > Support) {
            break;
        }

        float fi0 = float(i);
        float fi1 = min(float(Support), fi0 + 1.0);

        float weight0 = exp(fi0 * fi0 * gaussianFactor);
        float weight1 = (fi1 > float(Support))
                ? 0.0
                : exp(fi1 * fi1 * gaussianFactor);

        float pairWeight = weight0 + weight1;
        float pairOffset = (fi0 * weight0 + fi1 * weight1) / max(pairWeight, 0.0001);
        vec2 offset = Direction * TexelSize * pairOffset;
        vec4 samplePair = texture(Sampler0, texCoord + offset) + texture(Sampler0, texCoord - offset);
        result += samplePair * pairWeight;
        weightSum += pairWeight * 2.0;
    }

    result /= max(weightSum, 0.0001);
    fragColor = vec4(result.rgb, result.a * Brightness);
}

