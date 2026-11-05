#version 150

#moj_import <phaze:common.glsl>

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 Size;
uniform vec2 MaskPadding;

out vec2 FragCoord;
out vec4 FragColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    FragCoord = (rvertexcoord(gl_VertexID) * (Size + 2.0 * MaskPadding) - MaskPadding) / max(Size, vec2(0.001));
    FragColor = Color;
}


