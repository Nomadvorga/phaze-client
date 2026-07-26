#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Arc parameters as vertex attributes rather than loose uniforms - see
// InvertedRectangle / round_batched for why. All four vertices of the
// quad carry identical values, so the interpolated value is constant per
// fragment.
//
// The two gradient colors cannot ride on the standard COLOR attribute the
// way round_batched's corner gradient does: arc.fsh mixes them by polar
// angle, not by position, so both have to reach every fragment intact.
in vec3 Position;
in vec4 ArcRect;    // location.xy, size.xy
in vec4 ArcParams;  // radius, thickness, start, end
in vec4 ArcColor1;
in vec4 ArcColor2;

out vec4 vArcRect;
out vec4 vArcParams;
out vec4 vArcColor1;
out vec4 vArcColor2;

void main() {
    vArcRect = ArcRect;
    vArcParams = ArcParams;
    vArcColor1 = ArcColor1;
    vArcColor2 = ArcColor2;

    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
