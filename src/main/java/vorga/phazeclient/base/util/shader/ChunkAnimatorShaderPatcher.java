package vorga.phazeclient.base.util.shader;

import java.util.regex.Pattern;

/**
 * Shared GLSL source patcher for the per-section ChunkAnimator
 * effect. Used by two mixins:
 *
 * <ul>
 *   <li>{@link vorga.phazeclient.mixins.sodium.ShaderParserChunkAnimatorMixin}
 *       - hooks Sodium's {@code ShaderParser.parseShader}, runs once
 *       per Sodium-native chunk shader compile.</li>
 *   <li>{@link vorga.phazeclient.mixins.iris.IrisGlShaderChunkAnimatorMixin}
 *       - hooks Iris's {@code GlShader.<init>} via {@code @ModifyArg},
 *       runs once per Iris-compiled chunk shader (i.e. the shader-
 *       pack-derived terrain shader when shaders are active).</li>
 * </ul>
 *
 * <p><b>Key insight:</b> Both Sodium and Iris compile their chunk
 * vertex shaders against Sodium's vertex format. That means the
 * final GLSL source - regardless of who produced it - contains
 * Sodium's {@code _vert_position} and {@code _draw_id} declarations
 * from {@code chunk_vertex.glsl}. Iris's compatibility layer keeps
 * these around because the shader pack's main code (which uses
 * {@code gl_Vertex} and the legacy GL pipeline) is bridged by Iris
 * to construct {@code gl_Vertex} from {@code _vert_position}. So
 * if we add our offset to {@code _vert_position} right after its
 * initial assignment, the offset propagates through every
 * downstream computation - including the shader pack's own MVP
 * transform - <em>without modifying the shader pack itself</em>.
 *
 * <p>Patches applied:
 * <ol>
 *   <li>Inject {@code uniform float u_PhazeChunkAnimOffset[256];}
 *       and {@code uniform vec3 u_PhazeChunkAnimDir;} alongside
 *       Sodium's existing {@code u_RegionOffset} declaration.</li>
 *   <li>Find the first {@code _vert_position = ...;} assignment
 *       and append {@code _vert_position += offset * dir;} after
 *       it.</li>
 * </ol>
 *
 * <p>Safety: every patch step is gated by content sniffing -
 * marker tokens that must all be present. Missing any marker
 * (e.g. unusual Iris transformation, modern shader pipeline,
 * non-terrain shader) causes the function to return the source
 * unchanged. The Java-side mixin then falls back to the
 * region-level path via {@code setRegionOffset} args, so the
 * worst case is "coarser animation" rather than "broken shader".
 */
public final class ChunkAnimatorShaderPatcher {

    private ChunkAnimatorShaderPatcher() {
        // utility
    }

    /**
     * Marks the inserted offset uniform array. Used as a "have we
     * already patched this source?" guard so re-running the patcher
     * on a previously-patched string is idempotent.
     */
    private static final String PATCH_MARKER = "u_PhazeChunkAnimOffset";

    /**
     * Matches Sodium's {@code _draw_id = a_LightAndData[3];}
     * assignment (the LAST statement in {@code _vert_init()}).
     * We append the offset-add right after this line so that:
     *
     * <ul>
     *   <li>{@code _draw_id} is already initialised - the index lookup
     *       {@code u_PhazeChunkAnimOffset[int(_draw_id) & 0xFF]}
     *       reads the correct slot. Adding earlier (e.g. right after
     *       {@code _vert_position = ...}) would index by {@code 0}
     *       since {@code _draw_id} is a global default-initialised to
     *       zero before {@code _vert_init} sets it last.</li>
     *   <li>{@code _vert_position} is also set, so our additive write
     *       happens on top of the freshly-decoded chunk-local
     *       position. Subsequent reads (e.g.
     *       {@code main()}'s {@code position = _vert_position + translation}
     *       or Iris's {@code gl_Vertex} construction) see our
     *       offset.</li>
     * </ul>
     */
    private static final Pattern DRAW_ID_ASSIGN =
            Pattern.compile("(_draw_id\\s*=\\s*[^;]+;)");

    /**
     * GLSL appended right after the matched {@code _draw_id} line.
     * {@code int(_draw_id) & 0xFF} casts the {@code uint _draw_id} to
     * {@code int} for array indexing and masks against an 8-bit
     * range - the maximum draw_id Sodium produces is 255 (8x4x8 =
     * 256 sections per region) so the mask is a no-op today; it's
     * cheap insurance against future Sodium changes that widen the
     * encoding.
     */
    private static final String OFFSET_PATCH =
            "$1\n    _vert_position += u_PhazeChunkAnimOffset[int(_draw_id) & 0xFF] * u_PhazeChunkAnimDir;";

    /**
     * Patches the GLSL source. Returns the original unchanged when
     * any required marker is missing (chunk-shader detection fails)
     * or when the source has already been patched. Otherwise the
     * returned source has both uniforms declared at the top and the
     * offset applied at the end of {@code _vert_init()}.
     */
    public static String patch(String original) {
        if (original == null) {
            return null;
        }
        // Idempotency guard - both mixins might end up running on
        // the same shader if Iris ever pipes a Sodium-parsed shader
        // through GlShader.<init>; double patches would compile but
        // duplicate the offset add (visually 2x distance).
        if (original.contains(PATCH_MARKER)) {
            return original;
        }
        // Chunk-vertex-shader signature: every patchable target has
        // both tokens. Fragment shaders / clouds / sky shaders don't
        // reference _vert_position or _draw_id and won't pass this
        // gate.
        if (!original.contains("_vert_position")
                || !original.contains("_draw_id")) {
            return original;
        }

        // Always inject uniforms at the top of the file (right after
        // the directive block). Doing it next to the existing
        // u_RegionOffset declaration is a forward-reference trap:
        // Sodium's main shader puts that uniform AFTER the
        // chunk_vertex.glsl include, but our offset-add lives INSIDE
        // chunk_vertex.glsl's _vert_init() function body - so the
        // offset add references a uniform declared later in the
        // file. NVIDIA's compiler reports this as
        // "C1503: undefined variable" at the use site, which is the
        // exact crash we're avoiding.
        int insertAt = findUniformInsertionPoint(original);
        if (insertAt < 0) {
            return original;
        }
        String withUniforms = original.substring(0, insertAt)
                + "\nuniform float u_PhazeChunkAnimOffset[256];"
                + "\nuniform vec3 u_PhazeChunkAnimDir;\n"
                + original.substring(insertAt);

        // Inject the offset-add after the _draw_id assignment in
        // _vert_init(). Using replaceFirst because some pipelines
        // inline the function body multiple times (edge case in
        // Iris's transformation) and we only want one offset add.
        String result = DRAW_ID_ASSIGN.matcher(withUniforms).replaceFirst(OFFSET_PATCH);
        if (result.equals(withUniforms)) {
            // Regex didn't match - no _draw_id assignment in a
            // recognisable form. Don't ship a shader with our
            // uniforms but no usage; the GLSL compiler is fine with
            // unused uniforms but the next patch attempt would hit
            // the idempotency guard and never re-try, so we'd be
            // stuck in a no-op state forever for this shader.
            return original;
        }
        return result;
    }

    /**
     * Finds a safe character offset to inject our uniform declarations
     * when the legacy {@code uniform vec3 u_RegionOffset;} landmark
     * isn't present. We walk past every leading directive
     * ({@code #version}, {@code #extension}, {@code #pragma},
     * {@code #define}) and any blank lines, and insert immediately
     * before the first non-directive line - that's a guaranteed safe
     * position for {@code uniform} declarations in GLSL: after all
     * directives, before any function body or non-directive
     * statement.
     *
     * <p>Returns {@code -1} when the source is empty or doesn't
     * have a recognisable structure, so the caller can bail rather
     * than mis-place the decls.
     */
    private static int findUniformInsertionPoint(String source) {
        if (source.isEmpty()) {
            return -1;
        }
        int charPos = 0;
        int lastDirectiveEnd = -1;
        while (charPos < source.length()) {
            int lineEnd = source.indexOf('\n', charPos);
            if (lineEnd < 0) lineEnd = source.length();
            String line = source.substring(charPos, lineEnd).trim();
            if (line.isEmpty() || line.startsWith("//")) {
                // Blank line or single-line comment - keep walking;
                // these can intersperse the directive block.
                charPos = lineEnd + 1;
                continue;
            }
            if (line.startsWith("#")) {
                lastDirectiveEnd = lineEnd;
                charPos = lineEnd + 1;
                continue;
            }
            // First non-directive, non-comment, non-blank line:
            // insert right before it (or after the last directive
            // if there was one).
            return lastDirectiveEnd >= 0 ? lastDirectiveEnd : charPos;
        }
        return lastDirectiveEnd;
    }
}
