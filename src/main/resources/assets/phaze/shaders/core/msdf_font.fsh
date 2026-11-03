#version 150

in vec2 TexCoord;
in vec4 FragColor;
in vec2 GlobalPos;

uniform sampler2D Sampler0;
uniform float Range;
uniform float Thickness;
uniform float Smoothness;
uniform bool Outline;
uniform float OutlineThickness;
uniform vec4 OutlineColor;
uniform vec4 ColorModulator;
uniform bool EnableFadeout;
uniform float FadeoutStart;
uniform float FadeoutEnd;
uniform float MaxWidth;
uniform float TextPosX;
uniform bool LabelFadeEnabled;
uniform vec4 LabelFadeBounds;
uniform vec2 LabelFadeStrength;

out vec4 OutColor;

float median(vec3 color) {
    return max(min(color.r, color.g), min(max(color.r, color.g), color.b));
}

void main() {
    float dist = median(texture(Sampler0, TexCoord).rgb) - 0.5 + Thickness;
    // Use both UV gradients so glyphs and icons keep a finite range when rotated.
    vec2 unitRange = vec2(Range) / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = vec2(1.0) / max(fwidth(TexCoord), vec2(1.0e-6));
    // A sub-pixel screen range makes the nominally empty part of a tiny
    // MSDF quad partially opaque, which shows up as a pale square behind
    // small UI glyphs (notably the panorama-card cross). One screen pixel
    // is the standard lower bound for stable MSDF antialiasing.
    float pixels = max(0.5 * dot(unitRange, screenTexSize), 1.0);
    float alpha = smoothstep(-Smoothness, Smoothness, dist * pixels);
    vec4 color = vec4(FragColor.rgb, FragColor.a * alpha);

    if (Outline) {
        color = mix(OutlineColor, FragColor, alpha);
        color.a *= smoothstep(-Smoothness, Smoothness, (dist + OutlineThickness) * pixels);
    }

    if (EnableFadeout) {
        float fadeAlpha = 1.0;
        float relativeX = GlobalPos.x - TextPosX;
        float normalizedX = relativeX / MaxWidth;
        if (normalizedX > FadeoutStart) {
            fadeAlpha = 1.0 - smoothstep(FadeoutStart, FadeoutEnd, normalizedX);
        }
        color.a *= fadeAlpha;
    }

    if (LabelFadeEnabled) {
        float leftRamp = smoothstep(0.0, LabelFadeBounds.z, GlobalPos.x - LabelFadeBounds.x);
        float rightRamp = smoothstep(0.0, LabelFadeBounds.z, LabelFadeBounds.y - GlobalPos.x);
        color.a *= mix(1.0, leftRamp, LabelFadeStrength.x) * mix(1.0, rightRamp, LabelFadeStrength.y);
    }

    OutColor = color * ColorModulator;
}

