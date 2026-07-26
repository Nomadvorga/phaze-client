#version 150

#moj_import <minecraft:fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

uniform vec4 PhazeBlockColor;
uniform vec4 PhazeBlockParams;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

vec3 phazeBrightness(vec3 value, float brightness) {
    if (brightness > 0.0) {
        return mix(value, vec3(1.0), clamp(brightness, 0.0, 1.0));
    }
    if (brightness < 0.0) {
        return value * (1.0 + clamp(brightness, -1.0, 0.0));
    }
    return value;
}

vec4 phazeApply(vec4 color, vec4 tint, vec4 params) {
    if (params.z < 0.5) {
        return color;
    }

    vec3 rgb = color.rgb * max(tint.rgb, vec3(0.0));
    float gray = dot(clamp(rgb, 0.0, 1.0), vec3(0.2126, 0.7152, 0.0722));
    rgb = mix(vec3(gray), rgb, clamp(params.x, 0.0, 2.0));
    rgb = phazeBrightness(rgb, params.y);

    return vec4(max(rgb, vec3(0.0)), color.a);
}

void main() {
    vec4 sampled = texture(Sampler0, texCoord0);
    vec4 baseColor = sampled * vertexColor * ColorModulator;

#ifdef ALPHA_CUTOUT
    if (baseColor.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    vec4 color = phazeApply(baseColor, PhazeBlockColor, PhazeBlockParams);
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
