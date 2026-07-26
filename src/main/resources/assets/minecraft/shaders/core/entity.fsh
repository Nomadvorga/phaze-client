#version 150

#moj_import <minecraft:fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform vec4 PhazePlayerColor;
uniform vec4 PhazePlayerParams;
uniform vec4 PhazeEntityColor;
uniform vec4 PhazeEntityParams;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in float v_PhazeEntityTarget;

out vec4 fragColor;

vec3 phazeEntityBrightness(vec3 value, float brightness) {
    if (brightness > 0.0) {
        return mix(value, vec3(1.0), clamp(brightness, 0.0, 1.0));
    }
    if (brightness < 0.0) {
        return value * (1.0 + clamp(brightness, -1.0, 0.0));
    }
    return value;
}

vec4 phazeApplyEntityCorrection(vec4 color, vec4 tint, vec4 params) {
    if (params.z < 0.5) {
        return color;
    }

    vec3 rgb = color.rgb * max(tint.rgb, vec3(0.0));
    float gray = dot(clamp(rgb, 0.0, 1.0), vec3(0.2126, 0.7152, 0.0722));
    rgb = mix(vec3(gray), rgb, clamp(params.x, 0.0, 2.0));
    rgb = phazeEntityBrightness(rgb, params.y);
    return vec4(max(rgb, vec3(0.0)), color.a);
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    color *= vertexColor * ColorModulator;
#ifndef NO_OVERLAY
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
#endif
#ifndef EMISSIVE
    color *= lightMapColor;
#endif

    if (v_PhazeEntityTarget > 0.5) {
        color = phazeApplyEntityCorrection(
                color,
                v_PhazeEntityTarget < 1.5 ? PhazePlayerColor : PhazeEntityColor,
                v_PhazeEntityTarget < 1.5 ? PhazePlayerParams : PhazeEntityParams
        );
    }

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
