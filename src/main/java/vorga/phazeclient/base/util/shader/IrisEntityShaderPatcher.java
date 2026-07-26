package vorga.phazeclient.base.util.shader;

public final class IrisEntityShaderPatcher {
    private static final String MARKER = "v_PhazeEntityTarget";

    private IrisEntityShaderPatcher() {
    }

    public static String patch(String name, String source) {
        if (name == null || source == null) {
            return source;
        }
        if (!name.toLowerCase().contains("gbuffers_entities") || source.contains(MARKER)) {
            return source;
        }

        String patched = patchFragment(source);
        patched = patchVertex(patched);
        return patched;
    }

    private static String patchFragment(String source) {
        if (!source.contains("#ifdef FRAGMENT_SHADER")
                || !source.contains("in vec4 glColor;")
                || !source.contains("gl_FragData[0] = color;")) {
            return source;
        }

        String injected = source.replace(
                "in vec4 glColor;",
                """
in vec4 glColor;
flat in float v_PhazeEntityTarget;

uniform vec4 PhazePlayerColor;
uniform vec4 PhazePlayerParams;
uniform vec4 PhazeEntityColor;
uniform vec4 PhazeEntityParams;

vec3 phazeEntityBrightness(vec3 value, float brightness) {
    if (brightness > 0.0) {
        return mix(value, vec3(1.0), clamp(brightness, 0.0, 1.0));
    }
    if (brightness < 0.0) {
        return value * (1.0 + clamp(brightness, -1.0, 0.0));
    }
    return value;
}

vec4 phazeApplyEntityCorrection(vec4 color, vec4 tint, vec4 params) {
    if (params.z < 0.5) {
        return color;
    }

    vec3 rgb = color.rgb * max(tint.rgb, vec3(0.0));
    float gray = dot(clamp(rgb, 0.0, 1.0), vec3(0.2126, 0.7152, 0.0722));
    rgb = mix(vec3(gray), rgb, clamp(params.x, 0.0, 2.0));
    rgb = phazeEntityBrightness(rgb, params.y);
    return vec4(max(rgb, vec3(0.0)), color.a);
}
""");
        if (injected.equals(source)) {
            return source;
        }

        return injected.replace(
                "gl_FragData[0] = color;",
                """
if (v_PhazeEntityTarget > 0.5) {
    color = phazeApplyEntityCorrection(
        color,
        v_PhazeEntityTarget < 1.5 ? PhazePlayerColor : PhazeEntityColor,
        v_PhazeEntityTarget < 1.5 ? PhazePlayerParams : PhazeEntityParams
    );
}
gl_FragData[0] = color;
""");
    }

    private static String patchVertex(String source) {
        if (!source.contains("#ifdef VERTEX_SHADER")
                || !source.contains("out vec4 glColor;")
                || !source.contains("glColor = gl_Color;")) {
            return source;
        }

        String injected = source.replace(
                "out vec4 glColor;",
                """
out vec4 glColor;
flat out float v_PhazeEntityTarget;
""");
        if (injected.equals(source)) {
            return source;
        }

        return injected.replace(
                "glColor = gl_Color;",
                """
glColor = gl_Color;
v_PhazeEntityTarget = 0.0;
if (glColor.a > 0.990 && glColor.a < 0.999) {
    v_PhazeEntityTarget = glColor.a > 0.994 ? 1.0 : 2.0;
    glColor.a = 1.0;
}
""");
    }
}
