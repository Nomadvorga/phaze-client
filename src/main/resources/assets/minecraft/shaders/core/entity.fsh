#version 330

// Phaze override of vanilla minecraft:core/entity.
//
// Rebased onto the 1.21.11 vanilla source. Versus the 1.21.4 version:
//   - #version 150 -> 330.
//   - ColorModulator moved into the std140 "DynamicTransforms" block
//     (dynamictransforms.glsl); FogColor / FogStart / FogEnd moved into the
//     "Fog" block (fog.glsl). Redeclaring FogColor here is what produced
//     "C1038: declaration of FogColor conflicts with previous declaration".
//   - linear_fog(...) was replaced by apply_fog(...), which takes both the
//     spherical and the cylindrical distance plus four fog bounds.
//   - vanilla gained the PER_FACE_LIGHTING variant.
//
// The Phaze world-colour correction below is unchanged.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

// Phaze world colour tint, fed by WorldColorShaderHelper.
//
// These are intentionally default-block uniforms. ShaderProgram does not
// enumerate them, while ShaderProgramWorldColorMixin uploads them after the
// GL backend binds each render-pass program. This keeps the update live and
// avoids rebuilding entity buffers.
uniform vec4 PhazePlayerColor;
uniform vec4 PhazePlayerParams;
uniform vec4 PhazeEntityColor;
uniform vec4 PhazeEntityParams;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
in vec4 vertexPerFaceColorBack;
in vec4 vertexPerFaceColorFront;
#else
in vec4 vertexColor;
#endif
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
flat in float v_PhazeEntityTarget;

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
#ifdef PER_FACE_LIGHTING
    color *= (gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack) * ColorModulator;
#else
    color *= vertexColor * ColorModulator;
#endif
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

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
