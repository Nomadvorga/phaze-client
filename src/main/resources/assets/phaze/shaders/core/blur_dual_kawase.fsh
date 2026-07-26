#version 150

in vec2 texCoord;

uniform sampler2D Sampler0;
uniform vec2 TexelSize;
uniform float Offset;
uniform int Downsample;

out vec4 fragColor;

vec3 sampleDown(vec2 uv, vec2 o) {
    // 5-tap weighted downsample, smooth and cheap.
    vec3 c = texture(Sampler0, uv).rgb * 4.0;
    c += texture(Sampler0, uv + vec2( o.x,  o.y)).rgb;
    c += texture(Sampler0, uv + vec2(-o.x,  o.y)).rgb;
    c += texture(Sampler0, uv + vec2( o.x, -o.y)).rgb;
    c += texture(Sampler0, uv + vec2(-o.x, -o.y)).rgb;
    return c / 8.0;
}

vec3 sampleUp(vec2 uv, vec2 o) {
    // 8-tap upsample pattern to approximate gaussian smoothness.
    vec3 c = vec3(0.0);
    c += texture(Sampler0, uv + vec2(-2.0 * o.x,  0.0)).rgb;
    c += texture(Sampler0, uv + vec2(-o.x,  o.y)).rgb * 2.0;
    c += texture(Sampler0, uv + vec2( 0.0,  2.0 * o.y)).rgb;
    c += texture(Sampler0, uv + vec2( o.x,  o.y)).rgb * 2.0;
    c += texture(Sampler0, uv + vec2( 2.0 * o.x,  0.0)).rgb;
    c += texture(Sampler0, uv + vec2( o.x, -o.y)).rgb * 2.0;
    c += texture(Sampler0, uv + vec2( 0.0, -2.0 * o.y)).rgb;
    c += texture(Sampler0, uv + vec2(-o.x, -o.y)).rgb * 2.0;
    return c / 12.0;
}

void main() {
    // Keep HUD blur responsive to small slider changes: a hard 0.5 floor
    // made the first non-zero values look almost identical because they all
    // snapped to the same sampling radius.
    vec2 o = TexelSize * max(0.0, Offset);
    vec3 color = (Downsample == 1) ? sampleDown(texCoord, o) : sampleUp(texCoord, o);
    fragColor = vec4(color, 1.0);
}
