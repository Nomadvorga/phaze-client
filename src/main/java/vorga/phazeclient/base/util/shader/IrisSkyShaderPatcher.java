package vorga.phazeclient.base.util.shader;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Iris shader-pack sky patcher used by the GlShader compile hook.
 *
 * <p>Scope:
 * <ul>
 *   <li>Direct sky passes such as {@code gbuffers_skybasic},
 *       {@code gbuffers_skytextured}, {@code d0_sky_map}, etc.</li>
 *   <li>Composite passes that assemble the final sky only on pixels
 *       where the depth buffer says "this is sky" (used by packs like
 *       Sildur's).</li>
 * </ul>
 *
 * <p>The patcher stays fail-open. If the source doesn't look like one
 * of the recognised sky paths, it's returned unchanged.
 */
public final class IrisSkyShaderPatcher {

    private static final String PATCH_MARKER = "phaze_apply_sky_tint";

    private static final Pattern UNIFORM_SKY_COLOR_DECL = Pattern.compile(
            "(?m)^\\s*uniform\\s+vec[234]\\s+skyColor\\b"
    );

    private static final Pattern UNIFORM_FOG_COLOR_DECL = Pattern.compile(
            "(?m)^\\s*uniform\\s+vec[234]\\s+fogColor\\b"
    );

    private static final Pattern OUT_DECL = Pattern.compile(
            "(?m)^\\s*(?:layout\\s*\\([^\\n]+\\)\\s*)?out\\s+vec[234]\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;"
    );

    private static final Pattern GL_FRAGDATA_ASSIGN = Pattern.compile(
            "(gl_FragData\\s*\\[\\s*\\d+\\s*\\])\\s*="
    );

    private static final Pattern SKY_FUNC_ASSIGN = Pattern.compile(
            "((\\b[A-Za-z_][A-Za-z0-9_]*\\b)\\s*=\\s*(?:GetSkyColor|getSkyColor|draw_sky)\\s*\\([^;]*\\);)"
    );

    private static final String HELPERS =
              "\nvec3 phaze_apply_sky_tint(vec3 color) {"
            + "\n    float phazeSkyLum = max(dot(skyColor, vec3(0.2126, 0.7152, 0.0722)), 0.0001);"
            + "\n    vec3 phazeSkyUnit = clamp(skyColor / phazeSkyLum, vec3(0.0), vec3(4.0));"
            + "\n    vec3 phazeSkyTinted = clamp(color * phazeSkyUnit, vec3(0.0), vec3(1.0));"
            + "\n    return mix(color, phazeSkyTinted, 0.78);"
            + "\n}"
            + "\nvec3 phaze_apply_fog_sky_tint(vec3 color) {"
            + "\n    float phazeFogLum = max(dot(fogColor, vec3(0.2126, 0.7152, 0.0722)), 0.0001);"
            + "\n    vec3 phazeFogUnit = clamp(fogColor / phazeFogLum, vec3(0.0), vec3(8.0));"
            + "\n    vec3 phazeFogTinted = clamp(color * phazeFogUnit, vec3(0.0), vec3(1.0));"
            + "\n    return mix(color, phazeFogTinted, 0.92);"
            + "\n}"
            + "\nfloat phaze_sky_depth_threshold(float nearPlane, float farPlane) {"
            + "\n    return max(0.95 - nearPlane / max(farPlane, 0.0001) / max(farPlane, 0.0001), 0.999);"
            + "\n}\n";

    private IrisSkyShaderPatcher() {
    }

    public static String patch(String shaderName, String original) {
        if (shaderName == null || original == null || original.isEmpty()) {
            return original;
        }
        if (original.contains(PATCH_MARKER)) {
            return original;
        }
        if (isBslShader(shaderName, original)) {
            return original;
        }

        PatchMode mode = detectMode(shaderName, original);
        if (mode == PatchMode.NONE) {
            return original;
        }

        String outputToken = detectOutputToken(original);
        if (outputToken == null) {
            return original;
        }

        int insertAt = findUniformInsertionPoint(original);
        if (insertAt < 0) {
            return original;
        }

        boolean useFogTint = shouldUseFogTint(original);
        String helperName = useFogTint ? "phaze_apply_fog_sky_tint" : "phaze_apply_sky_tint";

        String withDecl = injectHelpers(original, insertAt, useFogTint);
        if (shouldPreferFinalOutputTint(original)) {
            return patchFinalColor(withDecl, outputToken, mode, helperName);
        }

        String patched = patchSkyFunctionAssignments(withDecl, helperName);
        return patchFinalColor(patched, outputToken, mode, helperName);
    }

    private static PatchMode detectMode(String shaderName, String source) {
        String lowerName = shaderName.toLowerCase(Locale.ROOT);
        String lower = source.toLowerCase(Locale.ROOT);

        boolean obviousSkyName = lowerName.contains("sky")
                || lowerName.contains("skymap")
                || lowerName.contains("sky_map");
        boolean skyContent = lower.contains("/include/sky/")
                || lower.contains("draw_sky(")
                || lower.contains("getskycolor(")
                || lower.contains("mc_render_stage_custom_sky")
                || lower.contains("skybox")
                || lower.contains("defskybox");

        if ((obviousSkyName || skyContent)
                && !lowerName.contains("shadow")
                && !lowerName.contains("terrain")
                && !lowerName.contains("water")
                && !lower.contains("u_blocktex")) {
            return PatchMode.DIRECT;
        }

        boolean compositeSky = lowerName.contains("composite")
                && lower.contains("bool sky =")
                && lower.contains("depthtex0")
                && (lower.contains("getskycolor(") || lower.contains("defskybox") || lower.contains("draw sky"));
        if (compositeSky) {
            return PatchMode.COMPOSITE_SKY_ONLY;
        }

        return PatchMode.NONE;
    }

    private static String injectHelpers(String source, int insertAt, boolean useFogTint) {
        StringBuilder builder = new StringBuilder(source.length() + HELPERS.length() + 32);
        builder.append(source, 0, insertAt);
        if (!UNIFORM_SKY_COLOR_DECL.matcher(source).find()) {
            builder.append("\nuniform vec3 skyColor;");
        }
        if (useFogTint && !UNIFORM_FOG_COLOR_DECL.matcher(source).find()) {
            builder.append("\nuniform vec3 fogColor;");
        }
        builder.append(HELPERS);
        builder.append("// ").append(PATCH_MARKER).append('\n');
        builder.append(source.substring(insertAt));
        return builder.toString();
    }

    private static String detectOutputToken(String source) {
        Matcher outMatcher = OUT_DECL.matcher(source);
        String fallback = null;
        while (outMatcher.find()) {
            String name = outMatcher.group(1);
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("sky") || lower.contains("frag")) {
                return name;
            }
            if (fallback == null) {
                fallback = name;
            }
        }
        if (fallback != null) {
            return fallback;
        }

        Matcher fragData = GL_FRAGDATA_ASSIGN.matcher(source);
        String last = null;
        while (fragData.find()) {
            last = fragData.group(1).replaceAll("\\s+", "");
        }
        return last;
    }

    private static boolean shouldPreferFinalOutputTint(String source) {
        String lower = source.toLowerCase(Locale.ROOT);
        return lower.contains("capttatsu.com");
    }

    private static boolean shouldUseFogTint(String source) {
        return shouldPreferFinalOutputTint(source);
    }

    private static boolean isBslShader(String shaderName, String source) {
        String lowerName = shaderName.toLowerCase(Locale.ROOT);
        String lowerSource = source.toLowerCase(Locale.ROOT);
        return lowerName.contains("bsl")
                || lowerSource.contains("bsl shaders")
                || lowerSource.contains("capttatsu.com");
    }

    private static String patchFinalColor(String source, String outputToken, PatchMode mode, String helperName) {
        Pattern assignPattern = Pattern.compile("(" + Pattern.quote(outputToken) + "\\s*=\\s*[^;]+;)");
        Matcher matcher = assignPattern.matcher(source);
        int lastStart = -1;
        int lastEnd = -1;
        while (matcher.find()) {
            lastStart = matcher.start(1);
            lastEnd = matcher.end(1);
        }
        if (lastStart < 0) {
            return source;
        }

        StringBuilder patch = new StringBuilder(source.substring(lastStart, lastEnd));
        if (mode == PatchMode.COMPOSITE_SKY_ONLY) {
            patch.append("\n    if (sky) ").append(outputToken)
                    .append(".rgb = ").append(helperName).append("(").append(outputToken).append(".rgb);");
        } else {
            patch.append("\n    ").append(outputToken)
                    .append(".rgb = ").append(helperName).append("(").append(outputToken).append(".rgb);");
        }

        return source.substring(0, lastStart) + patch + source.substring(lastEnd);
    }

    private static String patchSkyFunctionAssignments(String source, String helperName) {
        Matcher matcher = SKY_FUNC_ASSIGN.matcher(source);
        StringBuffer buffer = new StringBuffer(source.length() + 128);
        boolean changed = false;
        while (matcher.find()) {
            changed = true;
            String full = matcher.group(1);
            String varName = matcher.group(2);
            String replacement = full + "\n    " + varName + " = " + helperName + "(" + varName + ");";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        if (!changed) {
            return source;
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static int findUniformInsertionPoint(String source) {
        int charPos = 0;
        int lastDirectiveEnd = -1;
        while (charPos < source.length()) {
            int lineEnd = source.indexOf('\n', charPos);
            if (lineEnd < 0) lineEnd = source.length();
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

    private enum PatchMode {
        NONE,
        DIRECT,
        COMPOSITE_SKY_ONLY
    }
}
