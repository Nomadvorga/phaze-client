#version 150

in vec2 vLocal;

uniform float radius;
uniform vec4 color;

out vec4 fragColor;

void main() {
    float dist = length(vLocal);
    float aa = fwidth(dist);
    float alpha = 1.0 - smoothstep(radius - aa, radius + aa, dist);
    fragColor = vec4(color.rgb, color.a * alpha);
}
