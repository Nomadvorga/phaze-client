#version 330

// 1.21.11: ModelViewMat / ProjMat come from the vanilla std140 blocks.
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// The SDF parameters used to be loose uniforms (size, location, radius,
// softness). 1.21.11 dropped loose uniforms from the pipeline model, so
// they travel as vertex attributes instead - identical on all four
// vertices of the quad, which makes the interpolated value constant per
// fragment, exactly what the uniform version provided.
in vec3 Position;
in vec4 InvRect;    // location.xy, size.xy
in vec4 InvRadius;  // per-corner radii
in vec2 InvParams;  // softness, unused

out vec4 vInvRect;
out vec4 vInvRadius;
out vec2 vInvParams;

void main() {
    vInvRect = InvRect;
    vInvRadius = InvRadius;
    vInvParams = InvParams;

    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
