#version 150

uniform sampler2D Atlas;
uniform vec4 UvRect;
uniform vec2 AtlasSize;
uniform float DistanceRange;
uniform float Alpha;
in vec2 QuadUV;
out vec4 FragColor;

float median(vec3 value) {
    return max(min(value.r, value.g), min(max(value.r, value.g), value.b));
}

void main() {
    vec2 uv = UvRect.xy + QuadUV * UvRect.zw;
    // The RGB median, not the MTSDF alpha channel, reconstructs sharp MSDF corners.
    float distance = median(texture(Atlas, uv).rgb) - 0.5;
    vec2 unitRange = vec2(DistanceRange) / AtlasSize;
    vec2 screenTexSize = 1.0 / max(fwidth(uv), vec2(0.000001));
    float screenRange = max(0.5 * dot(unitRange, screenTexSize), 1.0);
    float coverage = clamp(distance * screenRange + 0.5, 0.0, 1.0);
    FragColor = vec4(1.0, 1.0, 1.0, coverage * Alpha);
}
