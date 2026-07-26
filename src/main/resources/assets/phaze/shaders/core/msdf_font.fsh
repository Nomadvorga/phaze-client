#version 330

// MSDF text.
//
// The 1.21.4 version carried twelve uniforms. Phaze only ever drove three
// of them with real values (Range, Thickness, Smoothness); the rest were
// nailed to constants at every call site - Outline off, fade-out off,
// ColorModulator white. 1.21.11 has no loose uniforms at all, so the
// three live values became a vertex attribute and the constant branches
// are folded away here rather than being carried as dead uniforms.
//
// The distance-field maths is unchanged.
in vec2 TexCoord;
in vec4 FragColor;
in vec4 vMsdfParams;

uniform sampler2D Sampler0;

out vec4 OutColor;

float median(vec3 color) {
    return max(min(color.r, color.g), min(max(color.r, color.g), color.b));
}

void main() {
    float range = vMsdfParams.x;
    float thickness = vMsdfParams.y;
    float smoothness = vMsdfParams.z;

    float dist = median(texture(Sampler0, TexCoord).rgb) - 0.5 + thickness;
    vec2 h = vec2(dFdx(TexCoord.x), dFdy(TexCoord.y)) * textureSize(Sampler0, 0);
    float pixels = range * inversesqrt(h.x * h.x + h.y * h.y);
    float alpha = smoothstep(-smoothness, smoothness, dist * pixels);

    OutColor = vec4(FragColor.rgb, FragColor.a * alpha);
}
