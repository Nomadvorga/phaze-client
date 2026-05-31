#version 150

in vec2 vLocal;

uniform vec2 rectSize;
uniform float radius;
uniform float arcWidth;
uniform vec4 rectColor;

out vec4 fragColor;

float roundedBoxSDF(vec2 p, vec2 size, float r) {
    vec2 q = abs(p) - size + r;
    return length(max(q, 0.0)) - r;
}

void main() {
    vec2 centered = vLocal - rectSize * 0.5;
    float dist1 = roundedBoxSDF(centered, rectSize * 0.5, radius);
    float dist2 = roundedBoxSDF(centered, rectSize * 0.5 - arcWidth, max(radius - arcWidth, 0.0));
    float dist = max(-dist2, dist1);
    float aa = fwidth(dist);
    float alpha = 1.0 - smoothstep(-aa, aa, dist);
    fragColor = vec4(rectColor.rgb, rectColor.a * alpha);
}
