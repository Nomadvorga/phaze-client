#version 330

// Same rounded-box SDF and the same white inversion output as the 1.21.4
// version; only the parameter source changed from uniforms to varyings.
in vec4 vInvRect;
in vec4 vInvRadius;
in vec2 vInvParams;

out vec4 fragColor;

float roundedBoxSDF(vec2 center, vec2 halfSize, vec4 radius) {
    radius.xy = (center.x > 0.0) ? radius.xy : radius.zw;
    radius.x  = (center.y > 0.0) ? radius.x : radius.y;

    vec2 q = abs(center) - halfSize + radius.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius.x;
}

void main() {
    vec2 location = vInvRect.xy;
    vec2 size = vInvRect.zw;
    float softness = vInvParams.x;

    float distance = roundedBoxSDF(gl_FragCoord.xy - location - (size / 2.0), size / 2.0, vInvRadius);

    // Edge smoothing that accounts for softness
    float smoothedAlpha = 1.0 - smoothstep(-1.0, softness + 1.0, distance);

    // White, so the inverting blend function flips whatever is underneath
    fragColor = vec4(1.0, 1.0, 1.0, smoothedAlpha);
}
