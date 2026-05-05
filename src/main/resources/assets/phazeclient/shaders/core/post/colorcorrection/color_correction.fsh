#version 330 core

uniform sampler2D MainSampler;
uniform float Brightness;
uniform float Contrast;
uniform float Saturation;
uniform float Gamma;
uniform float Temperature;
uniform float Vibrance;

in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

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

    // Temperature (shift blue <-> orange)
    color.r += Temperature * 0.1;
    color.b -= Temperature * 0.1;

    // Gamma
    color = pow(max(color, 0.0), vec3(1.0 / Gamma));

    // Clamp
    color = clamp(color, 0.0, 1.0);

    fragColor = vec4(color, 1.0);
}
