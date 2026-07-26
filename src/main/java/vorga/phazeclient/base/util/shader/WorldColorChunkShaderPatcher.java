package vorga.phazeclient.base.util.shader;

import java.util.regex.Pattern;

public final class WorldColorChunkShaderPatcher {
    private static final String VERTEX_MARKER = "v_PhazeWorldColorFluid";
    private static final String FRAGMENT_MARKER = "phazeApplyWorldColorCorrection";

    private static final Pattern MESH_ID_ASSIGN =
            Pattern.compile("((?:_draw_id|_vert_mesh_id)\\s*=\\s*[^;]+;)");

    private static final Pattern FRAG_COLOR_ASSIGN =
            Pattern.compile("((?:fragColor|out_FragColor)\\s*=\\s*[^;]+;)");

    private WorldColorChunkShaderPatcher() {
    }

    public static String patch(String original) {
        if (original == null) {
            return null;
        }

        String meshIdToken = detectMeshIdToken(original);
        boolean isChunkVertex = meshIdToken != null && original.contains("_vert_position") && original.contains("_vert_color");
        if (isChunkVertex) {
            if (original.contains(VERTEX_MARKER)) {
                return original;
            }
            return patchVertex(original);
        }

        String fragColorToken = detectFragColorToken(original);
        boolean isChunkFragment = fragColorToken != null
                && original.contains("u_BlockTex");
        if (isChunkFragment) {
            if (original.contains(FRAGMENT_MARKER)) {
                return original;
            }
            return patchFragment(original, fragColorToken);
        }

        return original;
    }

    private static String patchVertex(String original) {
        int insertAt = findUniformInsertionPoint(original);
        if (insertAt < 0) {
            return original;
        }

        String withDecl = original.substring(0, insertAt)
                + "\nout float v_PhazeWorldColorFluid;\n"
                + original.substring(insertAt);

        String result = MESH_ID_ASSIGN.matcher(withDecl).replaceFirst(
                "$1\n    v_PhazeWorldColorFluid = (_vert_color.a < 0.999) ? 1.0 : 0.0;\n"
                        + "    if (v_PhazeWorldColorFluid > 0.5) { _vert_color.a = 1.0; }"
        );
        return result.equals(withDecl) ? original : result;
    }

    private static String patchFragment(String original, String fragColorToken) {
        int insertAt = findUniformInsertionPoint(original);
        if (insertAt < 0) {
            return original;
        }

        String withDecl = original.substring(0, insertAt)
                + """

in float v_PhazeWorldColorFluid;
uniform vec4 PhazeBlockColor;
uniform vec4 PhazeBlockParams;
uniform vec4 PhazeFluidColor;
uniform vec4 PhazeFluidParams;

vec3 phazeApplyWorldColorBrightness(vec3 value, float brightness) {
    if (brightness > 0.0) {
        return mix(value, vec3(1.0), clamp(brightness, 0.0, 1.0));
    }
    if (brightness < 0.0) {
        return value * (1.0 + clamp(brightness, -1.0, 0.0));
    }
    return value;
}

vec4 phazeApplyWorldColorCorrection(vec4 color, vec4 tint, vec4 params) {
    if (params.z < 0.5) {
        return color;
    }

    vec3 rgb = color.rgb * max(tint.rgb, vec3(0.0));
    float gray = dot(clamp(rgb, 0.0, 1.0), vec3(0.2126, 0.7152, 0.0722));
    rgb = mix(vec3(gray), rgb, clamp(params.x, 0.0, 2.0));
    rgb = phazeApplyWorldColorBrightness(rgb, params.y);
    color.rgb = max(rgb, vec3(0.0));
    return color;
}
"""
                + original.substring(insertAt);

        String result = FRAG_COLOR_ASSIGN.matcher(withDecl).replaceFirst(
                "$1\n    " + fragColorToken + " = phazeApplyWorldColorCorrection("
                        + fragColorToken
                        + ", v_PhazeWorldColorFluid > 0.5 ? PhazeFluidColor : PhazeBlockColor,"
                        + " v_PhazeWorldColorFluid > 0.5 ? PhazeFluidParams : PhazeBlockParams);"
        );
        return result.equals(withDecl) ? original : result;
    }

    private static String detectMeshIdToken(String original) {
        if (original.contains("_draw_id")) {
            return "_draw_id";
        }
        if (original.contains("_vert_mesh_id")) {
            return "_vert_mesh_id";
        }
        return null;
    }

    private static String detectFragColorToken(String original) {
        if (original.contains("out vec4 fragColor")) {
            return "fragColor";
        }
        if (original.contains("out vec4 out_FragColor")) {
            return "out_FragColor";
        }
        return null;
    }

    private static int findUniformInsertionPoint(String source) {
        if (source.isEmpty()) {
            return -1;
        }
        int charPos = 0;
        int lastDirectiveEnd = -1;
        while (charPos < source.length()) {
            int lineEnd = source.indexOf('\n', charPos);
            if (lineEnd < 0) {
                lineEnd = source.length();
            }
            String line = source.substring(charPos, lineEnd).trim();
            if (line.isEmpty() || line.startsWith("//")) {
                charPos = lineEnd + 1;
                continue;
            }
            if (line.startsWith("#")) {
                lastDirectiveEnd = lineEnd;
                charPos = lineEnd + 1;
                continue;
            }
            return lastDirectiveEnd >= 0 ? lastDirectiveEnd : charPos;
        }
        return lastDirectiveEnd;
    }
}
