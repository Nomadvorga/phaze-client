#version 330

// Identical arc maths to the 1.21.4 version; parameters are varyings now
// instead of loose uniforms.
in vec4 vArcRect;
in vec4 vArcParams;

out vec4 fragColor;

#define PI 3.141592653589793
#define RAD 0.0174533

void main() {
    vec2 location = vArcRect.xy;
    vec2 size = vArcRect.zw;
    float radius = vArcParams.x;
    float thickness = vArcParams.y;
    float start = vArcParams.z;
    float end = vArcParams.w;

    float startAngle = start * RAD;
    float endAngle = startAngle + min(end * RAD, PI * 2);

    float smoothThresh = 6.0 * (1.0 / length(size));
    vec2 centerPos = ((gl_FragCoord.xy - location) / size.xy) * 2.0 - 1.0;

    float dist = length(centerPos);
    float bandAlpha = smoothstep(radius, radius + smoothThresh, dist) * smoothstep(radius + thickness, (radius + thickness) - smoothThresh, dist);
    float angle = (atan(centerPos.y, centerPos.x) + PI);
    float angleAlpha = smoothstep(angle, angle - smoothThresh, startAngle - 0.1) * smoothstep(angle, angle + smoothThresh, endAngle + 0.1);

    // White, so the inverting blend function flips whatever is underneath
    fragColor = vec4(1.0, 1.0, 1.0, bandAlpha * angleAlpha);
}
