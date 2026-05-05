#version 150

in vec2 vLocal;

uniform vec2 rectSize;
uniform float radius;
uniform vec4 startColor;
uniform vec4 endColor;
uniform int gradientDirection;

out vec4 fragColor;

float roundedBoxSDF(vec2 center, vec2 size, float radius) {
    return length(max(abs(center) - size + radius, 0.0)) - radius;
}

void main() {
    vec2 centered = vLocal - rectSize * 0.5;
    float dist = roundedBoxSDF(centered, rectSize * 0.5, radius);
    float aa = fwidth(dist);
    float alpha = 1.0 - smoothstep(-aa, aa, dist);
    float f;
    if (gradientDirection == 0) {
        f = clamp(vLocal.y / rectSize.y, 0.0, 1.0);
    } else {
        f = clamp(vLocal.x / rectSize.x, 0.0, 1.0);
    }
    vec4 gradColor = mix(startColor, endColor, f);
    fragColor = vec4(gradColor.rgb, gradColor.a * alpha);
}
