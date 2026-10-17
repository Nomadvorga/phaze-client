#version 150

uniform vec4 Rect;
uniform float Radius;
uniform vec4 Color;
uniform vec4 BorderColor;
uniform float BorderWidth;
in vec2 QuadUV;
out vec4 FragColor;

float roundedDistance(vec2 point, vec2 halfSize, float radius) {
    vec2 q = abs(point) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

void main() {
    vec2 halfSize = Rect.zw * 0.5;
    float distance = roundedDistance((QuadUV - 0.5) * Rect.zw, halfSize, Radius);
    float aa = max(fwidth(distance), 0.0001);
    float outer = clamp(0.5 - distance / aa, 0.0, 1.0);
    float inner = clamp(0.5 - (distance + BorderWidth) / aa, 0.0, 1.0);
    float border = BorderWidth > 0.0 ? outer - inner : 0.0;
    float body = BorderWidth > 0.0 ? inner : outer;
    // Combine the track and its border before straight-alpha compositing.
    float alpha = Color.a * body + BorderColor.a * border;
    vec3 rgb = (Color.rgb * Color.a * body + BorderColor.rgb * BorderColor.a * border)
               / max(alpha, 0.000001);
    FragColor = vec4(rgb, alpha);
}
