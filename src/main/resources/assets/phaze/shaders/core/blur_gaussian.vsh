#version 330

// 1.21.11: this pass has no vertex buffer at all - the fullscreen triangle is
// synthesised from gl_VertexID exactly like vanilla's core/screenquad, so the
// pipeline uses VertexFormats.EMPTY and draws 3 vertices. The old version read
// an explicit Position attribute and multiplied by ModelViewMat/ProjMat, both
// of which are now UBO members this pass has no reason to bind.

out vec2 texCoord;

void main() {
    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
    texCoord = uv;
}
