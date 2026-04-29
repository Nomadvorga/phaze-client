#version 150

uniform vec2 size;
uniform vec2 location;
uniform vec4 radius;
uniform float softness;

out vec4 fragColor;

float roundedBoxSDF(vec2 center, vec2 size, vec4 radius) {
    radius.xy = (center.x > 0.0) ? radius.xy : radius.zw;
    radius.x  = (center.y > 0.0) ? radius.x : radius.y;

    vec2 q = abs(center) - size + radius.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius.x;
}

void main() {
    float distance = roundedBoxSDF(gl_FragCoord.xy - location - (size / 2.0), size / 2.0, radius);

    // РЎРіР»Р°Р¶РёРІР°РЅРёРµ РєСЂР°РµРІ СЃ СѓС‡РµС‚РѕРј softness
    float smoothedAlpha = 1.0 - smoothstep(-1.0, softness + 1.0, distance);

    // Р‘РµР»С‹Р№ С†РІРµС‚ РґР»СЏ РёРЅРІРµСЂСЃРёРё
    fragColor = vec4(1.0, 1.0, 1.0, smoothedAlpha);
}

