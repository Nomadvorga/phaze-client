#version 330

// Phaze override of vanilla minecraft:core/entity.
//
// Rebased onto the 1.21.11 vanilla source. What changed versus the 1.21.4
// version of this file:
//   - #version 150 -> 330.
//   - Light0_Direction / Light1_Direction are no longer loose uniforms; they
//     live in the std140 "Lighting" block declared by light.glsl. Redeclaring
//     them here is what produced
//     "C1038: declaration of Light0_Direction conflicts with previous
//     declaration" and took every entity pipeline down with it.
//   - ModelViewMat / TextureMat / ColorModulator come from the
//     "DynamicTransforms" block, ProjMat from "Projection".
//   - fog_distance(Position, FogShape) split into fog_spherical_distance and
//     fog_cylindrical_distance; FogShape no longer exists.
//   - vanilla gained the PER_FACE_LIGHTING variant and moved the
//     lightMapColor fetch under #ifndef EMISSIVE.
//
// The Phaze part (world-colour entity/player tint marker) is preserved
// verbatim - see the comment at the marker below.

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
out vec4 vertexPerFaceColorBack;
out vec4 vertexPerFaceColorFront;
#else
out vec4 vertexColor;
#endif
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
flat out float v_PhazeEntityTarget;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);

    // Phaze world colour: WorldColorShaderHelper tags a draw by nudging the
    // vertex alpha into (0.990, 0.999) - 1 = player, 2 = other entity - and
    // the tag is decoded here, then the alpha is restored to fully opaque so
    // the tag never shows up as transparency.
    //
    // 1.21.4 read this off vertexColor AFTER lighting. Reading it off the raw
    // input Color instead is equivalent (minecraft_mix_light passes alpha
    // through untouched) and, unlike the old form, also works in the new
    // PER_FACE_LIGHTING branch where there is no single vertexColor.
    vec4 phazeColor = Color;
    v_PhazeEntityTarget = 0.0;
    if (phazeColor.a > 0.990 && phazeColor.a < 0.999) {
        v_PhazeEntityTarget = phazeColor.a > 0.994 ? 1.0 : 2.0;
        phazeColor.a = 1.0;
    }

#ifdef PER_FACE_LIGHTING
    vec2 light = minecraft_compute_light(Light0_Direction, Light1_Direction, Normal);
    vertexPerFaceColorBack = minecraft_mix_light_separate(-light, phazeColor);
    vertexPerFaceColorFront = minecraft_mix_light_separate(light, phazeColor);
#elif defined(NO_CARDINAL_LIGHTING)
    vertexColor = phazeColor;
#else
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, phazeColor);
#endif
#ifndef EMISSIVE
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
#endif
    overlayColor = texelFetch(Sampler1, UV1, 0);

    texCoord0 = UV0;
#ifdef APPLY_TEXTURE_MATRIX
    texCoord0 = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
#endif
}
