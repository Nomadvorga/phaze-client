#version 150

in vec2 Position;
in vec2 UV;
uniform vec2 Viewport;
uniform vec4 Rect;
out vec2 QuadUV;

void main() {
    vec2 pixel = Rect.xy + Position * Rect.zw;
    gl_Position = vec4(pixel.x / Viewport.x * 2.0 - 1.0,
                       1.0 - pixel.y / Viewport.y * 2.0, 0.0, 1.0);
    QuadUV = UV;
}
