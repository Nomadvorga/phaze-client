#version 150

in vec4 vertexColor;
in vec2 texCoord;

uniform sampler2D Sampler;
uniform vec2 Size;
uniform float Radius;

out vec4 fragColor;

void main() {
    vec2 texelSize = 1.0 / Size;
    vec4 color = vec4(0.0);
    float total = 0.0;
    
    // Simple Gaussian blur
    for (float x = -Radius; x <= Radius; x++) {
        for (float y = -Radius; y <= Radius; y++) {
            float weight = exp(-(x * x + y * y) / (2.0 * Radius * Radius));
            vec2 offset = vec2(x, y) * texelSize;
            color += texture(Sampler, texCoord + offset) * weight;
            total += weight;
        }
    }
    
    fragColor = vertexColor * (color / total);
}
