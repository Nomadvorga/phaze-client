package vorga.phazeclient.base.util.shader;

public final class IrisFluidShaderPatcher {
    private static final String MARKER = "phazeApplyFluidCorrection";

    private IrisFluidShaderPatcher() {
    }

    public static String patch(String name, String source) {
        if (name == null || source == null) {
            return source;
        }
        String lowerName = name.toLowerCase();
        if ((!lowerName.contains("water") && !lowerName.contains("translucent")) || source.contains(MARKER)) {
            return source;
        }
        if (!source.contains("#ifdef FRAGMENT_SHADER")
                || !source.contains("in vec4 glColor;")
                || !source.contains("vec4 color")) {
            return source;
        }

        String injected = source.replace(
                "in vec4 glColor;",
                """
in vec4 glColor;
uniform vec4 PhazeFluidColor;
uniform vec4 PhazeFluidParams;

vec3 phazeApplyFluidBrightness(vec3 value, float brightness) {
    if (brightness > 0.0) {
        return mix(value, vec3(1.0), clamp(brightness, 0.0, 1.0));
    }
    if (brightness < 0.0) {
        return value * (1.0 + clamp(brightness, -1.0, 0.0));
    }
    return value;
}

vec4 phazeApplyFluidCorrection(vec4 color, vec4 tint, vec4 params) {
    if (params.z < 0.5) {
        return color;
    }

    vec3 rgb = color.rgb * max(tint.rgb, vec3(0.0));
    float gray = dot(clamp(rgb, 0.0, 1.0), vec3(0.2126, 0.7152, 0.0722));
    rgb = mix(vec3(gray), rgb, clamp(params.x, 0.0, 2.0));
    rgb = phazeApplyFluidBrightness(rgb, params.y);
    color.rgb = max(rgb, vec3(0.0));
    return color;
}
""");
        if (injected.equals(source)) {
            return source;
        }

        if (injected.contains("float skyFade = 0.0;")) {
            return injected.replace(
                    "float skyFade = 0.0;",
                    """
color = phazeApplyFluidCorrection(color, PhazeFluidColor, PhazeFluidParams);
float skyFade = 0.0;
""");
        }

        if (injected.contains("fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);")) {
            return injected.replace(
                    "fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);",
                    """
color = phazeApplyFluidCorrection(color, PhazeFluidColor, PhazeFluidParams);
fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
""");
        }

        if (injected.contains("gl_FragData[0] = color;")) {
            return injected.replace(
                    "gl_FragData[0] = color;",
                    """
color = phazeApplyFluidCorrection(color, PhazeFluidColor, PhazeFluidParams);
gl_FragData[0] = color;
""");
        }

        return source;
    }
}
