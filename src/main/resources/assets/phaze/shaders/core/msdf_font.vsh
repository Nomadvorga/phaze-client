#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// 1.21.11: Range / Thickness / Smoothness used to be loose uniforms.
// Loose uniforms are gone from the pipeline model, so they ride along as
// a vertex attribute - identical on every vertex of a text batch, which
// makes the interpolated value constant per fragment.
in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec4 MsdfParams;  // range, thickness, smoothness, unused

out vec2 TexCoord;
out vec4 FragColor;
out vec4 vMsdfParams;

void main() {
    TexCoord = UV0;
    FragColor = Color;
    vMsdfParams = MsdfParams;

    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
