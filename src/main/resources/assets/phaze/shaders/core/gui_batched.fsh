#version 150

// Fragment stage of phaze:core/gui_batched. The rect branch is round.fsh and the MSDF branch
// is msdf_font.fsh, with their per-draw uniforms moved into flat vertex attributes.

in vec4 vColor;
in vec2 vTexCoord;
in vec2 vGlobalPos;
flat in vec4 vShapeA;
flat in vec4 vShapeB;
flat in vec4 vShapeC;
flat in vec4 vOutlineColor;
flat in vec4 vClip;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;
uniform sampler2D Sampler5;
uniform sampler2D Sampler6;
uniform sampler2D Sampler7;

out vec4 fragColor;

// GLSL 150 cannot index samplers with a per-primitive value; the slot is constant per quad.
vec4 sampleSlot(int slot, vec2 uv, vec2 dx, vec2 dy) {
    if (slot == 0) return textureGrad(Sampler0, uv, dx, dy);
    if (slot == 1) return textureGrad(Sampler1, uv, dx, dy);
    if (slot == 2) return textureGrad(Sampler2, uv, dx, dy);
    if (slot == 3) return textureGrad(Sampler3, uv, dx, dy);
    if (slot == 4) return textureGrad(Sampler4, uv, dx, dy);
    if (slot == 5) return textureGrad(Sampler5, uv, dx, dy);
    if (slot == 6) return textureGrad(Sampler6, uv, dx, dy);
    return textureGrad(Sampler7, uv, dx, dy);
}

vec2 slotSize(int slot) {
    if (slot == 0) return vec2(textureSize(Sampler0, 0));
    if (slot == 1) return vec2(textureSize(Sampler1, 0));
    if (slot == 2) return vec2(textureSize(Sampler2, 0));
    if (slot == 3) return vec2(textureSize(Sampler3, 0));
    if (slot == 4) return vec2(textureSize(Sampler4, 0));
    if (slot == 5) return vec2(textureSize(Sampler5, 0));
    if (slot == 6) return vec2(textureSize(Sampler6, 0));
    return vec2(textureSize(Sampler7, 0));
}

float roundedBoxSDF(vec2 center, vec2 halfSize, vec4 radius) {
    radius.xy = (center.x > 0.0) ? radius.xy : radius.zw;
    radius.x  = (center.y > 0.0) ? radius.x : radius.y;

    vec2 q = abs(center) - halfSize + radius.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius.x;
}

float median(vec3 color) {
    return max(min(color.r, color.g), min(max(color.r, color.g), color.b));
}

vec4 shadeRect() {
    vec2 halfSize = vShapeA.zw / 2.0;
    vec2 center = gl_FragCoord.xy - vShapeA.xy - halfSize;

    float thickness = vShapeC.z;
    float softness = vShapeC.w;

    float distance = roundedBoxSDF(center, halfSize, vShapeB);
    float smoothedAlpha = 1.0 - smoothstep(-1.0, thickness > 0.0 ? 1.0 : softness + 1.0, distance);
    float smoothedBorderAlpha = 1.0 - smoothstep(-softness, softness, distance);
    float borderAlpha = 1.0 - smoothstep(thickness - 2.0, thickness, abs(distance));

    if (smoothedAlpha < 0.42 && thickness > 0.0) {
        return vec4(vOutlineColor.rgb, vOutlineColor.a * smoothedBorderAlpha);
    }
    vec4 basicColor = vec4(vColor.rgb, vColor.a * smoothedAlpha);
    vec4 strokeColor = thickness > 0.0
            ? vec4(vOutlineColor.rgb, vOutlineColor.a * borderAlpha)
            : basicColor;

    // Weight RGB by alpha before mixing: transparent fill RGB must not
    // tint the outline or its AA fringe.
    basicColor.rgb *= basicColor.a;
    strokeColor.rgb *= strokeColor.a;
    vec4 blended = mix(basicColor, strokeColor, borderAlpha) * smoothedAlpha;
    return vec4(blended.a > 0.0 ? blended.rgb / blended.a : vec3(0.0), blended.a);
}

vec4 shadeMsdf(int slot, vec2 dx, vec2 dy, vec2 texelWidth) {
    float range = vShapeA.w;
    float thickness = vShapeB.z;
    float smoothness = vShapeB.w;
    float dist = median(sampleSlot(slot, vTexCoord, dx, dy).rgb) - 0.5 + thickness;
    vec2 unitRange = vec2(range) / slotSize(slot);
    vec2 screenTexSize = vec2(1.0) / max(texelWidth, vec2(1.0e-6));
    float pixels = max(0.5 * dot(unitRange, screenTexSize), 1.0);
    float alpha = smoothstep(-smoothness, smoothness, dist * pixels);
    vec4 color = vec4(vColor.rgb, vColor.a * alpha);

    float fadeWidth = vShapeA.z;
    if (fadeWidth > 0.0) {
        float leftRamp = smoothstep(0.0, fadeWidth, vGlobalPos.x - vShapeA.x);
        float rightRamp = smoothstep(0.0, fadeWidth, vShapeA.y - vGlobalPos.x);
        color.a *= mix(1.0, leftRamp, vShapeB.x) * mix(1.0, rightRamp, vShapeB.y);
    }
    return color;
}

void main() {
    // Derivatives first: the clip discard below is not uniform across a pixel quad.
    vec2 dx = dFdx(vTexCoord);
    vec2 dy = dFdy(vTexCoord);
    vec2 texelWidth = fwidth(vTexCoord);

    if (gl_FragCoord.x < vClip.x || gl_FragCoord.y < vClip.y
            || gl_FragCoord.x >= vClip.z || gl_FragCoord.y >= vClip.w) {
        discard;
    }

    int kind = int(vShapeC.x + 0.5);
    int slot = int(vShapeC.y + 0.5);
    if (kind == 0) {
        fragColor = shadeRect();
    } else if (kind == 1) {
        fragColor = shadeMsdf(slot, dx, dy, texelWidth);
    } else {
        vec4 color = sampleSlot(slot, vTexCoord, dx, dy) * vColor;
        if (color.a == 0.0) {
            discard;
        }
        fragColor = color;
    }
}
