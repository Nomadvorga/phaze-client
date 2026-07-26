#version 330

// 1.21.11: ModelViewMat and ProjMat are no longer loose uniforms. They
// live in the DynamicTransforms / Projection std140 blocks that these
// two vanilla includes declare, so the shader pulls them in instead of
// declaring its own.
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Batched rounded-rect shader: per-vertex SDF data so an arbitrary
// number of independent rectangles can be drawn in a single
// BufferBuilder/draw call. Each rect pushes 4 vertices, and every
// vertex carries the FULL set of SDF parameters for the rect it
// belongs to (RectBase, RectSize, Radius, Params, OutlineColor).
// Within one rect those values are identical across all four
// vertices, so the rasterizer's smooth interpolation produces the
// constant flat values the SDF math expects in the fragment stage.
//
// The per-corner Color attribute IS varied per vertex - that is how
// the corner-color gradient that the legacy uniform-based shader
// used to compute analytically gets reproduced for free via the
// rasterizer's bilinear color interpolation.

in vec3 Position;
in vec4 Color;
in vec2 RectBase;
in vec2 RectSize;
in vec4 Radius;
in vec2 Params;
in vec4 OutlineColor;

out vec4 vColor;
out vec2 vRectBase;
out vec2 vRectSize;
out vec4 vRadius;
out vec2 vParams;
out vec4 vOutlineColor;

void main() {
    vColor = Color;
    vRectBase = RectBase;
    vRectSize = RectSize;
    vRadius = Radius;
    vParams = Params;
    vOutlineColor = OutlineColor;

    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
