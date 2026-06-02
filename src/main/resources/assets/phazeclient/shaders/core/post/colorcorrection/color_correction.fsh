#version 330 core

uniform sampler2D MainSampler;
uniform float Brightness;
uniform float Contrast;
uniform float Saturation;
uniform float Hue;
uniform float Gamma;
uniform float Temperature;
uniform float Vibrance;

in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    vec3 color = texture(MainSampler, texCoord).rgb;

    // Brightness
    color += Brightness;

    // Contrast (around mid-gray)
    color = (color - 0.5) * Contrast + 0.5;

    // Saturation
    float lum = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(vec3(lum), color, Saturation);

    // Vibrance (boost low-saturation colors more)
    float maxC = max(color.r, max(color.g, color.b));
    float minC = min(color.r, min(color.g, color.b));
    float sat = maxC - minC;
    float vibranceScale = 1.0 + Vibrance * (1.0 - sat);
    color = mix(vec3(lum), color, vibranceScale);

    // Hue rotation
    vec3 hsv = rgb2hsv(clamp(color, 0.0, 1.0));
    hsv.x = fract(hsv.x + Hue);
    color = hsv2rgb(hsv);

    // Temperature (shift blue <-> orange)
    color.r += Temperature * 0.1;
    color.b -= Temperature * 0.1;

    // Gamma
    color = pow(max(color, 0.0), vec3(1.0 / Gamma));

    // Clamp
    color = clamp(color, 0.0, 1.0);

    fragColor = vec4(color, 1.0);
}
