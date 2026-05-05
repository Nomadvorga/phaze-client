#version 150

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 pos;

out vec2 vLocal;

void main() {
    vLocal = Position.xy - pos;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
