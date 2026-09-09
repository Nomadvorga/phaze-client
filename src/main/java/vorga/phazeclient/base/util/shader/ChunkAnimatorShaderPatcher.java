package vorga.phazeclient.base.util.shader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Patches Sodium 0.8.x terrain vertices using Sodium's own per-draw timing table. */
public final class ChunkAnimatorShaderPatcher {
    private static final String PATCH_MARKER = "u_PhazeChunkAnimMode";

    private static final Pattern POSITION_ASSIGN = Pattern.compile(
            "(vec3\\s+position\\s*=\\s*_vert_position\\s*\\+\\s*translation\\s*;)"
    );

    private static final String UNIFORMS =
            "\nuniform int u_PhazeChunkAnimMode;"
          + "\nuniform float u_PhazeChunkAnimDurationInv;"
          + "\nuniform float u_PhazeChunkAnimDistance;"
          + "\nuniform vec3 u_PhazeChunkAnimDirection;\n";

    private static final String POSITION_PATCH =
            "$1\n"
          + "    if (u_PhazeChunkAnimMode != 0) {\n"
          + "        // LocalSectionIndex packs Y into the low two bits. The\n"
          + "        // corresponding ivec4 therefore contains every Y section\n"
          + "        // of one 16x16 X/Z chunk column. Use the newest timestamp\n"
          + "        // so the whole column starts and grows as a single chunk.\n"
          + "        ivec4 phazeColumnTimes = u_chunkFades[int(_draw_id) >> 2];\n"
          + "        int phazeStartTime = max(max(phazeColumnTimes.x, phazeColumnTimes.y),"
          + " max(phazeColumnTimes.z, phazeColumnTimes.w));\n"
          + "        float phazeProgress = (phazeStartTime < 0) ? 1.0"
          + " : clamp(float(u_CurrentTime - phazeStartTime) * u_PhazeChunkAnimDurationInv, 0.0, 1.0);\n"
          + "        if (u_PhazeChunkAnimMode == 1) {\n"
          + "            position += u_PhazeChunkAnimDirection * u_PhazeChunkAnimDistance * (1.0 - phazeProgress);\n"
          + "        } else if (u_PhazeChunkAnimMode == 3) {\n"
          + "            vec3 phazeChunkCenter = u_RegionOffset + _get_draw_translation(_draw_id) + vec3(8.0);\n"
          + "            position = mix(phazeChunkCenter, position, phazeProgress);\n"
          + "        }\n"
          + "    }";

    private ChunkAnimatorShaderPatcher() { }

    public static String patch(String original) {
        if (original == null || original.contains(PATCH_MARKER)) return original;

        // These tokens uniquely identify Sodium 0.8.x's fully-expanded terrain
        // vertex shader. Iris or a future Sodium version safely falls through.
        if (!original.contains("_draw_id")
                || !original.contains("u_chunkFades")
                || !original.contains("u_CurrentTime")
                || !original.contains("_get_draw_translation")) {
            return original;
        }

        Matcher position = POSITION_ASSIGN.matcher(original);
        if (!position.find()) return original;

        int insertAt = findDeclarationInsertionPoint(original);
        if (insertAt < 0) return original;

        String withUniforms = original.substring(0, insertAt) + UNIFORMS + original.substring(insertAt);
        String patched = POSITION_ASSIGN.matcher(withUniforms).replaceFirst(POSITION_PATCH);
        return patched.equals(withUniforms) ? original : patched;
    }

    /** Inserts after #version / preprocessor directives, before declarations. */
    private static int findDeclarationInsertionPoint(String source) {
        int cursor = 0;
        while (cursor < source.length()) {
            int lineEnd = source.indexOf('\n', cursor);
            if (lineEnd < 0) lineEnd = source.length();
            String line = source.substring(cursor, lineEnd).trim();
            if (!line.isEmpty() && !line.startsWith("#") && !line.startsWith("//")) {
                return cursor;
            }
            cursor = Math.min(source.length(), lineEnd + 1);
        }
        return -1;
    }
}
