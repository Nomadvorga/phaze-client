#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Shares Arc's ArcRect / ArcParams attribute layout - same two vec4s,
// no colors (the fragment stage emits white and the inverting blend on
// the pipeline does the rest).
in vec3 Position;
in vec4 ArcRect;    // location.xy, size.xy
in vec4 ArcParams;  // radius, thickness, start, end

out vec4 vArcRect;
out vec4 vArcParams;

void main() {
    vArcRect = ArcRect;
    vArcParams = ArcParams;

    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
