#version 150

// Fragment SDF math is a direct port of phaze:core/round.fsh - same
// rounded-box SDF, same outline / fill blending, same softness
// behavior. The only structural differences are:
//   * The per-corner gradient that the original shader rebuilt every
//     pixel via createGradient(coords, color1..color4) now arrives
//     pre-interpolated as vColor (rasterizer-bilinear over the four
//     vertex colors that BatchedRectangle wrote).
//   * RectBase / RectSize / Radius / thickness / softness /
//     OutlineColor that used to be uniforms are now per-vertex
//     varyings. Their values are identical across the rect's four
//     verts so smooth interpolation == constant value, no flat
//     qualifier needed and we keep GLSL 150 compatibility.

in vec4 vColor;
in vec2 vRectBase;
in vec2 vRectSize;
in vec4 vRadius;
in vec2 vParams;
in vec4 vOutlineColor;

out vec4 fragColor;

float roundedBoxSDF(vec2 center, vec2 halfSize, vec4 radius) {
    radius.xy = (center.x > 0.0) ? radius.xy : radius.zw;
    radius.x  = (center.y > 0.0) ? radius.x : radius.y;

    vec2 q = abs(center) - halfSize + radius.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius.x;
}

void main() {
    vec2 halfSize = vRectSize / 2.0;
    vec2 center = gl_FragCoord.xy - vRectBase - halfSize;

    float thickness = vParams.x;
    float softness = vParams.y;

    float distance = roundedBoxSDF(center, halfSize, vRadius);
    float smoothedAlpha = 1.0 - smoothstep(-1.0, thickness > 0.0 ? 1.0 : softness + 1.0, distance);
    float smoothedBorderAlpha = 1.0 - smoothstep(-softness, softness, distance);
    float borderAlpha = 1.0 - smoothstep(thickness - 2.0, thickness, abs(distance));

    if (smoothedAlpha < 0.42 && thickness > 0.0) {
        fragColor = vec4(vOutlineColor.rgb, vOutlineColor.a * smoothedBorderAlpha);
    } else {
        vec4 basicColor = vec4(vColor.rgb, vColor.a * smoothedAlpha);
        vec4 strokeColor = thickness > 0.0
                ? vec4(vOutlineColor.rgb, vOutlineColor.a * borderAlpha)
                : basicColor;

        fragColor = mix(vec4(vColor.rgb, 0.0), mix(basicColor, strokeColor, borderAlpha), smoothedAlpha);
    }
}
