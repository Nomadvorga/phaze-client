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
 * <p>Patches applied (vertex shader):
 * <ol>
 *   <li>Inject {@code uniform float u_PhazeChunkAnimOffset[256];},
 *       {@code uniform vec3 u_PhazeChunkAnimDir;}, and
 *       {@code uniform float u_PhazeChunkAnimFade[256];} alongside
 *       Sodium's existing {@code u_RegionOffset} declaration.</li>
 *   <li>Declare {@code out float v_PhazeChunkAnimFade;} so the
 *       fragment shader can read the per-section progress.</li>
 *   <li>After the {@code _draw_id = ...;} assignment, append
 *       {@code _vert_position += offset * dir;} and
 *       {@code v_PhazeChunkAnimFade = u_PhazeChunkAnimFade[draw_id];}.
 *       The offset add drives Top/Bottom/Side animation types;
 *       the fade assign drives the Fade animation type via the
 *       fragment-side dither-discard patch below.</li>
 * </ol>
 *
 * <p>Patches applied (chunk fragment shader):
 * <ol>
 *   <li>Inject {@code in float v_PhazeChunkAnimFade;} (matches the
 *       vertex {@code out}) plus a 4x4 Bayer threshold function
 *       {@code phaze_dither_threshold(vec2)}.</li>
 *   <li>At the very top of {@code main()}, add a discard test:
 *       {@code if (v_PhazeChunkAnimFade < 1.0 && v_PhazeChunkAnimFade <= phaze_dither_threshold(gl_FragCoord.xy)) discard;}.
 *       When the per-section uniform array is filled with 1.0s
 *       (the steady state) the {@code < 1.0} guard short-circuits
 *       the threshold lookup, so non-Fade modes pay nothing.</li>
 * </ol>
 *
 * <p>Safety: every patch step is gated by content sniffing -
 * marker tokens that must all be present. Missing any marker
 * (e.g. unusual Iris transformation, modern shader pipeline,
 * non-terrain shader) causes the function to return the source
 * unchanged. The Java-side mixin then falls back to the
 * region-level path via {@code setRegionOffset} args (Top/Bottom/
 * Side) or to no animation at all (Fade). The worst case is
 * "coarser animation" or "no animation", never a broken shader.
 */
public final class ChunkAnimatorShaderPatcher {

    private ChunkAnimatorShaderPatcher() {
        // utility
    }

    /**
     * Marks the inserted UBO in the VERTEX shader. Used as a "have
     * we already patched this source?" guard so re-running the
     * patcher on a previously-patched vertex string is idempotent.
     * The fragment shader uses a separate marker
     * ({@link #FRAGMENT_PATCH_MARKER}) because the fragment patch
     * doesn't declare the UBO.
     *
     * <p>Token is the UBO block name - chosen to be distinctive
     * enough that no upstream shader code (Sodium, Iris, shader
     * pack) accidentally collides with it.
     */
    private static final String PATCH_MARKER = "PhazeChunkAnimBlock";

    /**
     * Fixed UBO binding point for {@code PhazeChunkAnimBlock}.
     * Sodium 0.6.x doesn't declare any UBOs in its terrain shaders
     * (verified against {@code chunk_matrices.glsl} /
     * {@code chunk_vertex.glsl}), so binding 0 is free in the
     * common case. The mixin re-binds the buffer to this point on
     * every frame so we don't depend on the program's own binding
     * state surviving Iris's program-swap dance between shadow /
     * gbuffer passes.
     */
    public static final int CHUNK_ANIM_UBO_BINDING = 0;

    /**
     * Idempotency marker for the FRAGMENT shader patch. The fragment
     * side adds {@code phaze_dither_threshold} as a helper function,
     * which we use both for its actual purpose (Bayer lookup) and as
     * a unique-enough token to short-circuit a re-patch.
     */
    private static final String FRAGMENT_PATCH_MARKER = "phaze_dither_threshold";

    /**
     * Matches the per-vertex mesh-id assignment - the LAST statement
     * in {@code _vert_init()}. Sodium 0.6.x ships this as
     * {@code _draw_id = a_LightAndData[3];}; older / forked Sodium
     * (and some downstream renderers) declare it as
     * {@code _vert_mesh_id} instead. Accepting either token here
     * keeps the patcher source-stable across Sodium upgrades - the
     * actual token used by the current source is detected by
     * {@link #detectMeshIdToken(String)} and substituted into the
     * patch payload at apply time.
     *
     * <p>We append the offset-add right after this line so that:
     *
     * <ul>
     *   <li>The mesh id is already initialised - the index lookup
     *       {@code u_PhazeChunkAnimOffset[int(meshId) & 0xFF]}
     *       reads the correct slot. Adding earlier (e.g. right after
     *       {@code _vert_position = ...}) would index by {@code 0}
     *       since the mesh id global is default-initialised to zero
     *       before {@code _vert_init} sets it last.</li>
     *   <li>{@code _vert_position} is also set, so our additive write
     *       happens on top of the freshly-decoded chunk-local
     *       position. Subsequent reads (e.g.
     *       {@code main()}'s {@code position = _vert_position + translation}
     *       or Iris's {@code gl_Vertex} construction) see our
     *       offset.</li>
     * </ul>
     */
    private static final Pattern MESH_ID_ASSIGN =
            Pattern.compile("((?:_draw_id|_vert_mesh_id)\\s*=\\s*[^;]+;)");

    /**
     * GLSL appended right after the matched mesh-id line. Replaces
     * the previous trio of uniform-array lookups with a single
     * indexed UBO read - {@code phazeChunkData[i]} - whose four
     * components are interpreted by {@code u_PhazeChunkAnimMode}:
     *
     * <ul>
     *   <li>{@code mode == 1} (offset / Top-Bottom-Side):
     *       {@code .xyz} is the pre-multiplied (magnitude * dir)
     *       offset vector. CPU side does the multiply so the shader
     *       drops the previous {@code u_PhazeChunkAnimDir} uniform
     *       and one vec3 multiply per vertex.</li>
     *   <li>{@code mode == 2} (fade): {@code .xyz} is zero,
     *       {@code .w} carries 0..1 fade progress. The vertex only
     *       carries the progress into the {@code v_PhazeChunkAnimFade}
     *       varying; the actual smooth pixel-level fade (dither
     *       discard or fog-mix) runs in the fragment patch. This
     *       works for both Sodium-native chunk fragments AND for
     *       Iris-compiled pack chunk fragments thanks to the
     *       relaxed pack-aware detection in {@link #patch(String)}.
     *       <p>The {@code _vert_color.a} channel is deliberately
     *       NOT touched - shader packs interpret vertex alpha as a
     *       lighting / AO / emissive modulation (not a transparency
     *       channel for solid terrain), so multiplying alpha here
     *       would produce a darken-then-brighten artefact instead
     *       of a fade.</li>
     *   <li>{@code mode == 3} (scale): {@code .xyz} is zero,
     *       {@code .w} carries 0..1 scale progress; the vertex
     *       shader {@code mix()}es position toward the section's
     *       block-content centre when below 1.0. The centre is
     *       {@code vec3(8.0)} because Sodium 0.6.x's compressed
     *       vertex format decodes section-local positions into the
     *       range [-8, 24] (see chunk_vertex.glsl - VERTEX_SCALE *
     *       2^20 = 32, VERTEX_OFFSET = -8). The actual block grid
     *       sits at [0, 16] within that range, so {@code (8,8,8)}
     *       is the geometric centre of the section's drawable
     *       blocks - mixing toward it makes the section appear to
     *       grow outward from its centre instead of from a corner.</li>
     *   <li>{@code mode == 0} (none / disabled): identity vec4
     *       {@code (0,0,0,1)} so all three branches are no-ops.</li>
     * </ul>
     *
     * <p>Multiple branches, all gated by the global mode int. The
     * GPU compiler folds the mode comparison into a uniform-control-
     * flow branch, which all modern hardware predicts perfectly
     * (one mode per draw call), so the cost of having every branch
     * present is essentially zero at runtime.
     */
    private static final String OFFSET_PATCH_TEMPLATE =
            "$1\n    vec4 phazeAnimData = phazeChunkData[int({mesh_id}) & 0xFF];\n"
          + "    if (u_PhazeChunkAnimMode == 1) { _vert_position += phazeAnimData.xyz; }\n"
          + "    v_PhazeChunkAnimFade = (u_PhazeChunkAnimMode == 2) ? phazeAnimData.w : 1.0;\n"
          + "    if (u_PhazeChunkAnimMode == 3 && phazeAnimData.w < 1.0) {"
          + " _vert_position = mix(vec3(8.0), _vert_position, phazeAnimData.w); }";

    /**
     * Top-of-fragment-shader injection: the fade varying input, the
     * style selector uniform, the Bayer LUT, and the threshold
     * helper. Both fade flavours (Dither + Fog Mix) are compiled
     * into every patched shader so the user can switch styles at
     * runtime via {@code u_PhazeFadeStyle} without paying for a
     * shader recompile - the unused branch is a constant-fold the
     * GPU's branch predictor handles for free once {@code
     * u_PhazeFadeStyle} is set per frame.
     *
     * <p>Uses {@code mod()} instead of bitwise-and for the LUT
     * index folding so the generated GLSL stays compatible with the
     * lower-version shader packs that Iris may compile against -
     * bitwise ops on ints aren't legal until GLSL 1.30, but
     * {@code mod()} works from 1.20 onward.
     *
     * <p>Declared as a fenced block so {@code findUniformInsertionPoint}
     * places it after directives but before any code, where global
     * function/array declarations are legal.
     */
    private static final String FRAGMENT_DECL =
              "\nin float v_PhazeChunkAnimFade;"
            + "\nuniform int u_PhazeFadeStyle;"
            + "\nconst float phaze_kBayer4x4[16] = float[16]("
            + "\n     0.0/16.0,  8.0/16.0,  2.0/16.0, 10.0/16.0,"
            + "\n    12.0/16.0,  4.0/16.0, 14.0/16.0,  6.0/16.0,"
            + "\n     3.0/16.0, 11.0/16.0,  1.0/16.0,  9.0/16.0,"
            + "\n    15.0/16.0,  7.0/16.0, 13.0/16.0,  5.0/16.0"
            + "\n);"
            + "\nfloat phaze_dither_threshold(vec2 fragCoord) {"
            + "\n    int x = int(mod(fragCoord.x, 4.0));"
            + "\n    int y = int(mod(fragCoord.y, 4.0));"
            + "\n    return phaze_kBayer4x4[y * 4 + x];"
            + "\n}\n";

    /**
     * Matches the opening brace of {@code main()} in the fragment
     * shader. We append the dither-discard check right after the
     * brace so it's the very first statement executed - bailing
     * before any expensive texture sampling for fragments that
     * would be discarded anyway.
     *
     * <p>The {@code < 1.0} guard before the threshold lookup is the
     * steady-state fast path: once every section's fade has reached
     * 1.0, this compares-and-branches without touching the LUT.
     * The {@code u_PhazeFadeStyle == 0} guard ensures Fog Mix users
     * (style 1) don't pay for the LUT lookup or branch into the
     * discard - their fragColor needs to survive to be blended at
     * the end of main().
     */
    private static final Pattern FRAG_MAIN_OPEN =
            Pattern.compile("(void\\s+main\\s*\\(\\s*\\)\\s*\\{)");
    private static final String FRAG_DISCARD_PATCH =
              "$1\n    if (u_PhazeFadeStyle == 0"
            + " && v_PhazeChunkAnimFade < 1.0"
            + " && v_PhazeChunkAnimFade <= phaze_dither_threshold(gl_FragCoord.xy)) {"
            + "\n        discard;"
            + "\n    }";

    /**
     * Matches the LAST {@code fragColor = ...;} (or {@code out_FragColor = ...;})
     * assignment in {@code main()}. Sodium's chunk fragment shader
     * has exactly one such assignment - the final
     * {@code fragColor = _linearFog(...);} that delivers the lit,
     * fogged colour. Anchoring the Fog Mix injection right after
     * this assignment lets the blend run on top of the fully
     * computed colour without us having to re-implement Sodium's
     * fog math; we just blend further toward {@code u_FogColor} as
     * the section's fade progresses.
     *
     * <p>The output token ({@code fragColor} vs {@code out_FragColor})
     * is detected by {@link #detectFragColorToken(String)} and
     * substituted into the patch via the {@code {frag_color}}
     * placeholder - same machinery as the vertex {@code {mesh_id}}
     * substitution.
     */
    private static final Pattern FRAG_COLOR_ASSIGN =
            Pattern.compile("((?:fragColor|out_FragColor)\\s*=\\s*[^;]+;)");
    private static final String FRAG_FOGMIX_PATCH_TEMPLATE =
              "$1\n    if (u_PhazeFadeStyle == 1 && v_PhazeChunkAnimFade < 1.0) {"
            + "\n        {frag_color} = mix({frag_color}, u_FogColor,"
            + " 1.0 - v_PhazeChunkAnimFade);"
            + "\n    }";

    /**
     * Patches the GLSL source. Returns the original unchanged when
     * any required marker is missing (shader-type detection fails)
     * or when the source has already been patched. Otherwise:
     *
     * <ul>
     *   <li>Chunk VERTEX shader (has {@code _vert_position} +
     *       either {@code _draw_id} or {@code _vert_mesh_id}):
     *       uniforms declared, offset and fade varying assigned at
     *       the end of {@code _vert_init()}.</li>
     *   <li>Chunk FRAGMENT shader (no vertex markers, but has
     *       {@code u_BlockTex} and either {@code fragColor} or
     *       {@code out_FragColor}): fade varying and Bayer threshold
     *       function declared, dither-discard check inserted at the
     *       top of {@code main()}.</li>
     *   <li>Anything else: returned unchanged.</li>
     * </ul>
     */
    public static String patch(String original) {
        if (original == null) {
            return null;
        }
        // No Iris-shader-pack guard anymore. The patcher is invoked
        // from two mixins (Sodium's ShaderParser + Iris's GlShader),
        // and only the LATTER sees the chunk shaders that actually
        // draw under an active pack - so guarding here would block
        // exactly the path we need for the user-visible animations
        // to keep working with shaders on. Per-shader source
        // sniffing (the markers below) is enough to keep non-chunk
        // shaders untouched, and the idempotency markers ({@link
        // #PATCH_MARKER} / {@link #FRAGMENT_PATCH_MARKER}) defend
        // against double patches if both mixins ever fire on the
        // same source.
        //
        // Practical consequences when a pack IS active:
        // - Iris-compiled chunk vertex shaders get the offset / fade
        //   / scale injection. {@code _vert_position} and the mesh
        //   id global are preserved by Iris's bridge code (it
        //   constructs {@code gl_Vertex} from them), so the patch
        //   anchors find what they expect.
        // - Iris-compiled chunk fragment shaders may NOT match our
        //   anchor tokens ({@code u_BlockTex} +
        //   {@code fragColor}/{@code out_FragColor}), in which case
        //   the fragment patch returns the source unchanged and the
        //   pack's own fragment runs. Fade falls back to the
        //   vertex-side {@code _vert_color.a *= w} modulation
        //   (which most packs respect for terrain).
        // - Sodium-native fallback shaders (shadow pass, etc.)
        //   compiled by Sodium for the no-pack-override case still
        //   get patched too; cost is one extra shader-compile pass
        //   per startup, no runtime overhead.
        String meshIdToken = detectMeshIdToken(original);
        boolean isVertex = original.contains("_vert_position")
                && meshIdToken != null;
        if (isVertex) {
            // Idempotency guard - both mixins might end up running
            // on the same shader if Iris ever pipes a Sodium-parsed
            // shader through GlShader.<init>; double patches would
            // compile but duplicate the offset add (visually 2x
            // distance) and shadow the fade varying.
            if (original.contains(PATCH_MARKER)) {
                return original;
            }
            return patchVertex(original, meshIdToken);
        }
        // Chunk-fragment signature: Sodium's terrain frag shaders
        // sample u_BlockTex - clouds use ColorModulator and no
        // u_BlockTex, sky shaders use u_SkyTex. The block texture
        // uniform is the cleanest single-token marker that's unique
        // to terrain. The output variable name varies across Sodium
        // versions (0.6.x ships {@code fragColor}, earlier versions
        // and some forks use {@code out_FragColor}); we accept
        // either so the patch survives across Sodium upgrades
        // without needing a code change.
        //
        // Fade is INTENTIONALLY scoped to Sodium-native chunk frags
        // only. Iris-compiled pack chunk frags don't match this
        // marker, so they remain unpatched and Fade is a silent
        // no-op under shader packs. (Offset and Scale still work
        // under packs via the vertex patch, which only requires
        // markers Iris preserves.)
        boolean isChunkFragment = original.contains("u_BlockTex")
                && (original.contains("out vec4 fragColor")
                    || original.contains("out vec4 out_FragColor"));
        if (isChunkFragment) {
            if (original.contains(FRAGMENT_PATCH_MARKER)) {
                return original;
            }
            return patchFragment(original);
        }
        return original;
    }

    /**
     * Detects which mesh-id global the shader source declares.
     * Sodium 0.6.x uses {@code _draw_id}; older releases and
     * forks use {@code _vert_mesh_id}. Returns {@code null} when
     * neither token is present (source is not a chunk vertex shader).
     *
     * <p>Checked in order of likelihood for current Sodium - the
     * {@code _draw_id} branch handles the common case in one
     * {@code String.contains} call.
     */
    private static String detectMeshIdToken(String original) {
        if (original.contains("_draw_id")) {
            return "_draw_id";
        }
        if (original.contains("_vert_mesh_id")) {
            return "_vert_mesh_id";
        }
        return null;
    }

    /**
     * Detects which output variable name the chunk fragment shader
     * uses. Sodium 0.6.x ships {@code out vec4 fragColor;}; older
     * versions and some forks use {@code out vec4 out_FragColor;}.
     * Returns {@code null} when neither full declaration is present
     * (source is not a chunk fragment shader and shouldn't be
     * patched).
     *
     * <p>We match the full {@code out vec4 NAME;} form rather than
     * a bare token to avoid false positives - the bare word
     * {@code fragColor} could in principle appear in a comment or
     * a different shader stage. The full declaration is unique to
     * the fragment-stage output.
     */
    private static String detectFragColorToken(String original) {
        if (original.contains("out vec4 fragColor")) {
            return "fragColor";
        }
        if (original.contains("out vec4 out_FragColor")) {
            return "out_FragColor";
        }
        return null;
    }


    /**
     * Vertex-shader pipeline. Injects uniforms + varying out at the
     * top of the file, and appends the offset add + fade varying
     * assign after the detected mesh-id assignment in
     * {@code _vert_init()}.
     *
     * @param meshIdToken token returned by
     *                    {@link #detectMeshIdToken(String)}
     *                    ({@code _draw_id} or {@code _vert_mesh_id});
     *                    substituted into the patch payload.
     */
    private static String patchVertex(String original, String meshIdToken) {
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
        // Single std140 UBO replaces the previous trio of uniform
        // float[256] arrays. Std140 packs vec4 elements at 16-byte
        // alignment - exactly what we want for a vec4[256] payload
        // (4 KB total, naturally aligned). The {@code binding = 0}
        // qualifier requires GLSL 4.20+ (or ARB_shading_language_420pack);
        // we omit it from the source here and bind via
        // {@code glUniformBlockBinding} on the CPU side instead -
        // that's compatible with the GLSL 1.50 / GL 3.3 baseline
        // Sodium targets without needing an explicit version-bump.
        String withUniforms = original.substring(0, insertAt)
                + "\nlayout(std140) uniform PhazeChunkAnimBlock {"
                + "\n    vec4 phazeChunkData[256];"
                + "\n};"
                + "\nuniform int u_PhazeChunkAnimMode;"
                + "\nout float v_PhazeChunkAnimFade;\n"
                + original.substring(insertAt);

        // Inject the offset-add + fade-assign after the mesh-id
        // assignment in _vert_init(). Using replaceFirst because
        // some pipelines inline the function body multiple times
        // (edge case in Iris's transformation) and we only want one
        // copy of each.
        //
        // The OFFSET_PATCH_TEMPLATE carries one back-reference
        // ({@code $1}) for the matched mesh-id line and a literal
        // placeholder ({@code {mesh_id}}) for the token name. We
        // substitute the token via String.replace (literal, no
        // regex) and pass the result straight to replaceFirst -
        // safe because the only regex-special token in the final
        // string is {@code $1}, and meshIdToken is a fixed GLSL
        // identifier (no {@code $}, no {@code \}) that can't
        // introduce additional captures.
        String offsetPatch = OFFSET_PATCH_TEMPLATE
                .replace("{mesh_id}", meshIdToken);
        String result = MESH_ID_ASSIGN.matcher(withUniforms)
                .replaceFirst(offsetPatch);
        if (result.equals(withUniforms)) {
            // Regex didn't match - no mesh-id assignment in a
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
     * Fragment-shader pipeline. Three patches stacked on the same
     * source:
     *
     * <ol>
     *   <li>Top-of-file: fade varying in, style uniform, Bayer
     *       threshold function (see {@link #FRAGMENT_DECL}).</li>
     *   <li>Start of {@code main()}: Bayer-dither-discard guarded
     *       by {@code u_PhazeFadeStyle == 0} (see
     *       {@link #FRAG_DISCARD_PATCH}).</li>
     *   <li>After the {@code fragColor = ...;} (or
     *       {@code out_FragColor = ...;}) assignment: Fog Mix blend
     *       guarded by {@code u_PhazeFadeStyle == 1} (see
     *       {@link #FRAG_FOGMIX_PATCH_TEMPLATE}).</li>
     * </ol>
     *
     * <p>Bail-on-failure semantics: any patch step that doesn't
     * find its anchor returns {@code original} unchanged. We don't
     * ship a half-patched shader because the idempotency marker
     * would then prevent a re-try, leaving the shader stuck in a
     * partial state for the rest of the JVM lifetime.
     */
    private static String patchFragment(String original) {
        // Detect the output variable name BEFORE we inject anything,
        // so the Fog Mix template can reference whichever name the
        // shader actually uses. detectFragColorToken returning null
        // would mean the shader-type gate let through a non-chunk
        // shader; bail safely.
        String fragColorToken = detectFragColorToken(original);
        if (fragColorToken == null) {
            return original;
        }
        int insertAt = findUniformInsertionPoint(original);
        if (insertAt < 0) {
            return original;
        }
        String withDecl = original.substring(0, insertAt)
                + FRAGMENT_DECL
                + original.substring(insertAt);

        // Step 2: inject the dither-discard check right after the
        // opening brace of main(). Using replaceFirst keeps the patch
        // idempotent even if some shader pre-processor inlines
        // multiple main()s (shouldn't happen for chunk shaders but
        // cheap insurance).
        String afterDiscard = FRAG_MAIN_OPEN.matcher(withDecl)
                .replaceFirst(FRAG_DISCARD_PATCH);
        if (afterDiscard.equals(withDecl)) {
            // No main() in a recognisable form - bail rather than
            // ship a shader with our decls but no discard. Same
            // reasoning as the vertex MESH_ID_ASSIGN guard.
            return original;
        }

        // Step 3: inject the Fog Mix blend right after the final
        // fragColor assignment. Substitute the detected token into
        // the template via String.replace (literal, not regex) so
        // the generated GLSL references whichever output name the
        // shader declares.
        String fogMixPatch = FRAG_FOGMIX_PATCH_TEMPLATE
                .replace("{frag_color}", fragColorToken);
        String result = FRAG_COLOR_ASSIGN.matcher(afterDiscard)
                .replaceFirst(fogMixPatch);
        if (result.equals(afterDiscard)) {
            // No fragColor assignment found - patcher detected the
            // shader as a chunk-frag (it has u_BlockTex + a
            // fragColor declaration) but we couldn't anchor the Fog
            // Mix blend. Ship the dither-only patch so Dither users
            // still get their effect; Fog Mix users degrade to no
            // visual fade in this corner case rather than a broken
            // shader.
            return afterDiscard;
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
