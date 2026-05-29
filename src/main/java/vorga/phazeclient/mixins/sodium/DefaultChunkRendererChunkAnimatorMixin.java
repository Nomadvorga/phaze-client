package vorga.phazeclient.mixins.sodium;

import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.iterator.ByteIterator;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import vorga.phazeclient.base.util.render.FogColorTracker;
import vorga.phazeclient.base.util.shader.ChunkAnimatorShaderPatcher;
import vorga.phazeclient.implement.features.modules.other.ChunkAnimator;

/**
 * Sodium-specific ChunkAnimator implementation - Java side.
 *
 * <p>Pairs with {@link ShaderParserChunkAnimatorMixin}, which
 * patches Sodium's terrain vertex shader to add a per-section
 * uniform array {@code u_PhazeChunkAnimY[256]} and use it as a
 * Y add on top of the existing {@code position}. This mixin runs
 * once per region per render-layer pass, computes the per-section
 * offset array, and uploads it into that uniform via
 * {@link GL20#glUniform1fv(int, float[])}.
 *
 * <p>Hook site is the {@code TAIL} of
 * {@code DefaultChunkRenderer.setModelMatrixUniforms} -
 * {@code setupState} on the {@code ChunkShaderInterface} has
 * already been called by then (it bound the program), and Sodium's
 * own {@code setRegionOffset} call has populated the region
 * uniform - so the program we want is currently bound and the GL
 * state is exactly where it should be for additional uniform
 * uploads.
 *
 * <p>Why per-section instead of per-region:
 * Sodium's {@code setRegionOffset(x, y, z)} is one offset per 8x4x8
 * section box (128x64x128 blocks). With elytra flight that produces
 * one animation event per ~128 blocks of horizontal travel - the
 * player visibly sees only a handful of "region drops" per second.
 * The per-section path animates each individual chunk as it first
 * comes into the player's view, matching the vanilla feel.
 *
 * <p>Uniform location lookup is cached per program ID. Sodium
 * compiles a separate program per render layer (cutout, opaque,
 * translucent, fog variants); the cache picks them up the first
 * time each program is bound and reuses the location for the
 * lifetime of that program. {@code -1} means the uniform was
 * optimised out (shouldn't happen since {@code position.y +=}
 * makes it relevant, but we treat it as "do nothing" anyway).
 *
 * <p>This mixin is only ever loaded when Sodium is actually on
 * classpath - {@link vorga.phazeclient.mixins.PhazeMixinPlugin}
 * gates it on {@code FabricLoader.isModLoaded("sodium")}.
 */
@Mixin(value = DefaultChunkRenderer.class, remap = false)
public abstract class DefaultChunkRendererChunkAnimatorMixin {

    /**
     * Cache of patched-shader UBO + uniform handles, keyed by
     * {@link ChunkShaderInterface} object identity. Each Sodium
     * program has its own ChunkShaderInterface instance, so reference
     * equality on the shader is a sound proxy for "is this the same
     * GL program as last time?" without paying the driver round-trip
     * of {@code glGetInteger(GL_CURRENT_PROGRAM)} on every region.
     * Sodium iterates many regions back-to-back with the same bound
     * shader (one shader per render pass), so the identity comparison
     * short-circuits TRUE for the vast majority of calls; the driver
     * query only fires on shader switches (a few times per frame
     * total instead of ~30+).
     *
     * <p>The previous design stored three separate uniform-array
     * locations (offset / fade / scale) plus a direction-vector
     * uniform; the UBO refactor consolidates those into a single
     * vec4[256] block plus a global mode int. The block's index in
     * the program (NOT a uniform location - a separate handle space
     * GL maintains for uniform blocks) gets cached here and bound to
     * a fixed binding point ({@link ChunkAnimatorShaderPatcher#CHUNK_ANIM_UBO_BINDING})
     * via {@link GL31#glUniformBlockBinding} once per program switch.
     *
     * <p>{@link GL31#GL_INVALID_INDEX} (= 0xFFFFFFFF) on the cached
     * block index means the program was compiled without our patch
     * (Iris-with-shaders, non-terrain shader, etc.). The TAIL upload
     * path early-returns and the fallback {@code @ModifyArgs} hook
     * shifts {@code u_RegionOffset} instead. {@code -1} on the mode
     * uniform location has the same meaning - either it wasn't
     * patched in, or the GLSL optimiser dropped it (impossible given
     * our patch always uses it, but defensive).
     */
    private static ChunkShaderInterface phaze$cachedShader;
    private static int phaze$cachedBlockIdx     = GL31.GL_INVALID_INDEX;
    private static int phaze$cachedModeLoc      = -1;
    private static int phaze$cachedFadeStyleLoc = -1;
    private static int phaze$cachedFogMixColorLoc = -1;

    /**
     * GL buffer object name backing the {@code PhazeChunkAnimBlock}
     * UBO. {@code 0} = "not yet created"; the upload path lazily
     * calls {@link GL15#glGenBuffers()} on the first frame after the
     * GL context is ready. Persistent for the JVM lifetime - we
     * never glDeleteBuffers it because GL contexts go away on game
     * exit and the driver reaps the buffer along with the context.
     *
     * <p>Re-uploaded every frame via {@link GL15#glBufferData} with
     * the same size, which is the canonical orphan-and-reupload
     * idiom: the driver allocates fresh memory each call so we
     * never block on the previous frame's draw still reading the
     * buffer.
     */
    private static int phaze$uboBufferId = 0;

    /**
     * Last value uploaded into {@code u_PhazeChunkAnimMode} for the
     * currently-cached program. Sentinel {@code -1} forces a fresh
     * upload after a program switch (or first-ever bind). Lets us
     * amortise the {@code glUniform1i} to once per mode-change-or-
     * shader-switch instead of per region per frame.
     */
    private static int phaze$lastUploadedMode = -1;

    /**
     * Last value uploaded into {@code u_PhazeFadeStyle} for the
     * currently-cached program. Same sentinel-forces-upload pattern
     * as {@link #phaze$lastUploadedMode}.
     */
    private static int phaze$lastUploadedFadeStyle = -1;
    private static float phaze$lastFogMixR = Float.NaN;
    private static float phaze$lastFogMixG = Float.NaN;
    private static float phaze$lastFogMixB = Float.NaN;

    /**
     * Tracks whether the last UBO payload uploaded to the currently
     * bound program was the "identity" vec4 array - {@code (0,0,0,1)}
     * for every slot, which means "no animation active anywhere in
     * this region". When the next region also produces an identity
     * payload we skip the {@code glBufferData} call entirely; this
     * is the steady-state fast path after every visible chunk has
     * finished animating. Resets to {@code false} on program switch
     * because the new program's UBO storage is undefined - we MUST
     * re-upload to seed it before the program draws.
     */
    private static boolean phaze$lastWasIdentity = false;

    /**
     * Reusable scratch buffer for the {@code vec4[256]} UBO payload.
     * Layout is interleaved: slot {@code i} occupies float indices
     * {@code [4i, 4i+1, 4i+2, 4i+3]} = {@code (offset.x, offset.y,
     * offset.z, fade-or-scale-progress)}. The shader's mode uniform
     * picks which interpretation applies. Single thread (render
     * thread) writes / reads it.
     */
    private static final float[] PHAZE$VEC4 = new float[1024];

    /**
     * Scratch float[256] used as an intermediate for the offset /
     * fade / scale write methods on {@link ChunkAnimator}, which
     * still produce one scalar per slot. The mixin then expands
     * these scalars into the {@link #PHAZE$VEC4} layout based on
     * the active animation mode (multiplying by direction for
     * offset, dropping into the {@code .w} channel for fade/scale).
     */
    private static final float[] PHAZE$SCALAR_SCRATCH = new float[256];

    /**
     * Reusable 3-element direction buffer. {@code writeAnimationDirection}
     * fills it for the Iris-fallback path's region-level shift; the
     * patched-shader path uses {@code writeAnimationDirectionPerSection}
     * to fill it before pre-multiplying the per-slot magnitudes into
     * {@link #PHAZE$VEC4}'s xyz channels.
     */
    private static final float[] PHAZE$DIR = new float[3];

    /**
     * Reusable buffer for slot indices of sections that have
     * geometry to draw this frame. Filled by iterating the
     * region's {@code sectionsWithGeometryIterator} and passed to
     * the animator so it only registers first-seen times for
     * sections that are actually rendering - which is what makes
     * each chunk animate individually as it uploads, instead of
     * the whole region animating at once.
     */
    private static final int[] PHAZE$SLOT_BUFFER = new int[256];

    /**
     * Region-level fallback that activates when the per-section
     * uniforms aren't present in the currently-bound program. This is
     * the Iris-with-shaders case: Iris compiles chunk shaders from
     * the shader pack source instead of going through Sodium's
     * {@code ShaderParser.parseShader}, so {@link ShaderParserChunkAnimatorMixin}
     * never gets a chance to inject our uniforms. {@code glGetUniformLocation}
     * returns -1 for both names, the {@link #phaze$uploadSectionAnimOffsets}
     * TAIL path early-returns, and we fall back to bumping the
     * {@code setRegionOffset(x, y, z)} args here. The whole region
     * slides in together rather than per-section, but at least the
     * animation still happens under shaders.
     *
     * <p>Cache resolution is done EAGERLY here (at the
     * {@code setRegionOffset} INVOKE, before {@code setRegionOffset}
     * actually runs) so the TAIL path that fires later in this same
     * method invocation reads up-to-date {@code phaze$cachedOffsetLoc}
     * and either uploads the per-section array or skips - exactly
     * one path runs per region per frame.
     */
    @ModifyArgs(
            method = "setModelMatrixUniforms",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/shader/ChunkShaderInterface;setRegionOffset(FFF)V",
                    remap = false
            ),
            remap = false
    )
    private static void phaze$fallbackRegionOffset(
            Args args,
            @Local(argsOnly = true) ChunkShaderInterface shader,
            @Local(argsOnly = true) RenderRegion region) {
        ChunkAnimator animator = ChunkAnimator.getInstance();
        if (animator == null || region == null || shader == null) {
            return;
        }

        // Refresh the cached uniform locations only when the bound
        // shader actually changes - reference equality on the
        // ChunkShaderInterface tracks program switches without the
        // driver round-trip of glGetInteger(GL_CURRENT_PROGRAM). The
        // TAIL inject site later in this same setModelMatrixUniforms
        // invocation reads the SAME cache (we both run on the render
        // thread, no synchronisation needed).
        //
        // CRITICAL: this refresh runs unconditionally, BEFORE any
        // isEnabled() check. When the module is toggled off mid-
        // animation, the TAIL flush path needs an up-to-date cached
        // offset location for the program currently bound; if we
        // skipped the refresh because !isEnabled() we'd write zeros
        // to a STALE location belonging to a different program (Iris
        // shader-pass switches happen every frame), corrupting an
        // unrelated uniform slot and leaving the actual animation
        // uniform untouched - which is the exact "chunks frozen mid-
        // air after disabling Chunk Animator" symptom users hit.
        if (shader != phaze$cachedShader) {
            int programId = GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            if (programId == 0) {
                return;
            }
            phaze$cachedShader = shader;
            // Resolve our UBO block index in the new program. UBOs
            // live in their own handle space (separate from regular
            // uniform locations), so we need the GL31 query. The
            // result is GL_INVALID_INDEX (0xFFFFFFFF) when the patch
            // didn't apply - which is also the field's default
            // sentinel, so the rest of the code can treat
            // !(>=0 && != GL_INVALID_INDEX) uniformly as "no UBO".
            phaze$cachedBlockIdx = GL31.glGetUniformBlockIndex(
                    programId, "PhazeChunkAnimBlock");
            if (phaze$cachedBlockIdx != GL31.GL_INVALID_INDEX) {
                // Bind the program's block to our fixed binding
                // point exactly once per program switch. After this,
                // the program reads from whatever buffer is bound to
                // {@code CHUNK_ANIM_UBO_BINDING} via
                // {@link GL30#glBindBufferBase}; we re-bind that per
                // region in the TAIL hook so the buffer-content
                // matches the region we're about to draw.
                GL31.glUniformBlockBinding(programId,
                        phaze$cachedBlockIdx,
                        ChunkAnimatorShaderPatcher.CHUNK_ANIM_UBO_BINDING);
            }
            phaze$cachedModeLoc =
                    GL20.glGetUniformLocation(programId, "u_PhazeChunkAnimMode");
            phaze$cachedFadeStyleLoc =
                    GL20.glGetUniformLocation(programId, "u_PhazeFadeStyle");
            phaze$cachedFogMixColorLoc =
                    GL20.glGetUniformLocation(programId, "u_PhazeFogMixColor");
            // Force re-upload of every cached scalar uniform on the
            // new program. Uniform state persists per-GL-program for
            // the program's lifetime; when Iris swaps between
            // shadow/main/gbuffers terrain programs each frame, the
            // newly-rebound program still holds whatever values we
            // last wrote to it - which belonged to a DIFFERENT region
            // and possibly a DIFFERENT animation type on the previous
            // (program, region) tuple. Resetting the sentinel triplet
            // forces the TAIL flush to seed the new program's state.
            phaze$lastUploadedFadeStyle = -1;
            phaze$lastUploadedMode = -1;
            phaze$lastFogMixR = Float.NaN;
            phaze$lastFogMixG = Float.NaN;
            phaze$lastFogMixB = Float.NaN;
            // Identity flag resets to false - the new program's UBO
            // binding might point to whatever buffer the previous
            // program left there (UBO bindings are global GL state),
            // so we MUST re-upload at least once on the new program
            // to seed our buffer at the right binding point. Same
            // "stale-state-leak" risk as the old per-uniform flags.
            phaze$lastWasIdentity = false;
        }
        // Module disabled: cache is now refreshed for the TAIL flush
        // to use, no fallback region-offset shift needed (the section
        // animation it would amplify is gone). Bail.
        if (!animator.isEnabled()) {
            return;
        }
        // If the patched UBO exists in this program, the per-section
        // TAIL path handles this region. Don't shift the region
        // offset here - that would double-apply on top of per-vertex
        // offsets.
        if (phaze$cachedBlockIdx != GL31.GL_INVALID_INDEX) {
            return;
        }

        // Iris-with-shaders fallback: gather drawable slots and bump
        // setRegionOffset's args by the region-level magnitude.
        ChunkRenderList renderList = region.getRenderList();
        if (renderList == null) {
            return;
        }
        ByteIterator iter = renderList.sectionsWithGeometryIterator(false);
        if (iter == null) {
            return;
        }
        int slotCount = 0;
        while (iter.hasNext() && slotCount < PHAZE$SLOT_BUFFER.length) {
            PHAZE$SLOT_BUFFER[slotCount++] = iter.nextByteAsInt();
        }
        if (slotCount == 0) {
            return;
        }

        float magnitude = animator.getRegionMagnitude(
                region.getOriginX(), region.getOriginY(), region.getOriginZ(),
                PHAZE$SLOT_BUFFER, slotCount);
        if (magnitude == 0.0F) {
            return;
        }
        animator.writeAnimationDirection(PHAZE$DIR);
        // setRegionOffset's args are the region-relative translation
        // for the shader's u_RegionOffset uniform; adding magnitude *
        // direction to each component shifts the entire region in
        // world space, which the shader pack's MVP transform will
        // correctly project regardless of which terrain shader is
        // bound (the offset rides through u_RegionOffset that every
        // chunk shader - Sodium's or Iris's - already consumes).
        if (PHAZE$DIR[0] != 0.0F) args.set(0, args.<Float>get(0) + magnitude * PHAZE$DIR[0]);
        if (PHAZE$DIR[1] != 0.0F) args.set(1, args.<Float>get(1) + magnitude * PHAZE$DIR[1]);
        if (PHAZE$DIR[2] != 0.0F) args.set(2, args.<Float>get(2) + magnitude * PHAZE$DIR[2]);
    }

    @Inject(
            method = "setModelMatrixUniforms",
            at = @At("TAIL"),
            remap = false
    )
    private static void phaze$uploadSectionAnimOffsets(
            ChunkShaderInterface shader,
            RenderRegion region,
            CameraTransform cameraTransform,
            CallbackInfo ci) {
        ChunkAnimator animator = ChunkAnimator.getInstance();
        if (animator == null || region == null) {
            return;
        }

        // No UBO in the currently bound program: the shader-side
        // patch didn't apply (Iris-with-shaders, non-terrain
        // program, etc.). The @ModifyArgs fallback already shifted
        // setRegionOffset for the offset modes; Fade and Scale have
        // no graceful fallback (they need our shader patch) and
        // simply produce no animation in that case. Either way,
        // nothing to do here.
        if (phaze$cachedBlockIdx == GL31.GL_INVALID_INDEX) {
            return;
        }

        int mode = animator.getAnimationModeIndex();

        // Module disabled (mode == 0): flush identity payload + mode
        // back to the GPU exactly once per program-switch so any
        // section that was mid-animation when the user toggled the
        // module off lands cleanly at its final position. Once the
        // identity flag is true the GPU is in the right state and
        // subsequent regions on the same program can early-return.
        if (mode == 0) {
            if (!phaze$lastWasIdentity) {
                phaze$fillIdentity(PHAZE$VEC4);
                phaze$uploadUbo(PHAZE$VEC4);
                phaze$lastWasIdentity = true;
            }
            if (phaze$lastUploadedMode != 0 && phaze$cachedModeLoc >= 0) {
                GL20.glUniform1i(phaze$cachedModeLoc, 0);
                phaze$lastUploadedMode = 0;
            }
            return;
        }

        // Pull the per-frame render list for this region. Sodium
        // populates it during update() (before render()) with the
        // slot indices of sections that actually have geometry to
        // draw this frame - exactly the sections we want to register
        // first-seen times for. Sections still uploading or out of
        // view are absent from this list; they keep their UBO slot
        // at the identity value so they're not yet animating.
        ChunkRenderList renderList = region.getRenderList();
        if (renderList == null) {
            return;
        }
        ByteIterator iter = renderList.sectionsWithGeometryIterator(false);
        if (iter == null) {
            return;
        }
        int slotCount = 0;
        while (iter.hasNext() && slotCount < PHAZE$SLOT_BUFFER.length) {
            PHAZE$SLOT_BUFFER[slotCount++] = iter.nextByteAsInt();
        }
        if (slotCount == 0) {
            return;
        }

        // Pack the per-section data into the vec4 layout based on
        // the active mode. Returns false when every slot ended up at
        // the identity value (nothing animating in this region) - we
        // still need to flush once after a mode change but can skip
        // the glBufferData when both old and new states are identity.
        boolean hasNonIdentity = phaze$packAnimData(
                animator, region, slotCount, mode, PHAZE$VEC4);

        if (hasNonIdentity || !phaze$lastWasIdentity) {
            phaze$uploadUbo(PHAZE$VEC4);
            phaze$lastWasIdentity = !hasNonIdentity;
        }

        // Mode uniform - one int per program per mode-change. The
        // animation-type setting only mutates when the user clicks
        // the dropdown, so the cache-vs-current comparison short-
        // circuits in the overwhelmingly common case (no upload).
        if (phaze$cachedModeLoc >= 0 && mode != phaze$lastUploadedMode) {
            GL20.glUniform1i(phaze$cachedModeLoc, mode);
            phaze$lastUploadedMode = mode;
        }

        // Fade-style uniform - same amortised-upload pattern as the
        // mode int. Only meaningful when Fade is the active mode AND
        // the fragment-side patch landed (the {@code u_PhazeFadeStyle}
        // uniform exists). Otherwise the location is -1 and the
        // upload is skipped - Dither stays the implicit "effect"
        // because the unpatched shader has no dither/blend logic at
        // all.
        if (phaze$cachedFadeStyleLoc >= 0 && animator.isFadeMode()) {
            int currentStyle = animator.getFadeStyleIndex();
            if (currentStyle != phaze$lastUploadedFadeStyle) {
                GL20.glUniform1i(phaze$cachedFadeStyleLoc, currentStyle);
                phaze$lastUploadedFadeStyle = currentStyle;
            }
        }
        if (phaze$cachedFogMixColorLoc >= 0 && animator.isFadeMode()) {
            float r = FogColorTracker.red();
            float g = FogColorTracker.green();
            float b = FogColorTracker.blue();
            if (Float.compare(r, phaze$lastFogMixR) != 0
                    || Float.compare(g, phaze$lastFogMixG) != 0
                    || Float.compare(b, phaze$lastFogMixB) != 0) {
                GL20.glUniform3f(phaze$cachedFogMixColorLoc, r, g, b);
                phaze$lastFogMixR = r;
                phaze$lastFogMixG = g;
                phaze$lastFogMixB = b;
            }
        }
    }

    /**
     * Fills the {@code vec4[256]} payload with identity values -
     * {@code (0, 0, 0, 1)} per slot, which the patched vertex shader
     * reads as "no offset, full opacity, no scale shrink". Used both
     * for the disabled-mode flush and as the starting point for
     * per-region packing (slots that don't get explicitly populated
     * by the active mode's writer keep their identity value).
     */
    private static void phaze$fillIdentity(float[] vec4) {
        for (int i = 0; i < 256; i++) {
            int o = i * 4;
            vec4[o]     = 0.0F;
            vec4[o + 1] = 0.0F;
            vec4[o + 2] = 0.0F;
            vec4[o + 3] = 1.0F;
        }
    }

    /**
     * Mode-aware packer. Calls the existing per-section writers on
     * {@link ChunkAnimator}, expands the resulting scalar arrays
     * into the vec4 layout the shader expects, and returns
     * {@code true} when at least one slot ended up non-identity.
     *
     * <p>Layout per mode (matches the shader's interpretation in
     * {@link ChunkAnimatorShaderPatcher#OFFSET_PATCH_TEMPLATE}):
     *
     * <ul>
     *   <li>{@code mode == 1} (offset / Top-Bottom-Side): {@code .xyz} =
     *       {@code magnitude * direction} pre-multiplied on the CPU,
     *       {@code .w} = 1.0 (unused for fade/scale in this mode).</li>
     *   <li>{@code mode == 2} (fade): {@code .xyz} = 0,
     *       {@code .w} = 0..1 fade progress.</li>
     *   <li>{@code mode == 3} (scale): {@code .xyz} = 0,
     *       {@code .w} = 0..1 scale progress.</li>
     * </ul>
     *
     * <p>Always starts by filling the buffer with identity values.
     * The per-mode writers only touch the slots that have an active
     * animation; everything else stays at identity, which the shader
     * reads as a no-op in every branch.
     */
    private static boolean phaze$packAnimData(
            ChunkAnimator animator, RenderRegion region,
            int slotCount, int mode, float[] vec4) {
        phaze$fillIdentity(vec4);

        if (mode == 1) {
            boolean hasNonZero = animator.writeRegionSectionYOffsets(
                    region.getOriginX(), region.getOriginY(), region.getOriginZ(),
                    PHAZE$SLOT_BUFFER, slotCount, PHAZE$SCALAR_SCRATCH);
            if (!hasNonZero) {
                return false;
            }
            // Per-section direction is always (0,1,0) for Top/Bottom
            // (the magnitude carries the sign) or (sx, 0, sz) for Side.
            // CPU-side pre-multiply means the vertex shader drops the
            // old per-vertex {@code offset * dir} multiply and just
            // does {@code _vert_position += data.xyz}.
            animator.writeAnimationDirectionPerSection(PHAZE$DIR);
            for (int i = 0; i < 256; i++) {
                float mag = PHAZE$SCALAR_SCRATCH[i];
                if (mag != 0.0F) {
                    int o = i * 4;
                    vec4[o]     = mag * PHAZE$DIR[0];
                    vec4[o + 1] = mag * PHAZE$DIR[1];
                    vec4[o + 2] = mag * PHAZE$DIR[2];
                    // .w stays at 1.0 (identity for fade/scale)
                }
            }
            return true;
        }

        if (mode == 2) {
            boolean hasNonOne = animator.writeRegionSectionFadeValues(
                    region.getOriginX(), region.getOriginY(), region.getOriginZ(),
                    PHAZE$SLOT_BUFFER, slotCount, PHAZE$SCALAR_SCRATCH);
            if (!hasNonOne) {
                return false;
            }
            for (int i = 0; i < 256; i++) {
                vec4[i * 4 + 3] = PHAZE$SCALAR_SCRATCH[i];
            }
            return true;
        }

        if (mode == 3) {
            boolean hasNonOne = animator.writeRegionSectionScaleValues(
                    region.getOriginX(), region.getOriginY(), region.getOriginZ(),
                    PHAZE$SLOT_BUFFER, slotCount, PHAZE$SCALAR_SCRATCH);
            if (!hasNonOne) {
                return false;
            }
            for (int i = 0; i < 256; i++) {
                vec4[i * 4 + 3] = PHAZE$SCALAR_SCRATCH[i];
            }
            return true;
        }

        return false;
    }

    /**
     * Lazily creates the UBO buffer object on first use, then re-
     * uploads the entire {@code vec4[256]} payload via the canonical
     * orphan-and-reupload idiom. Re-binds to our fixed binding point
     * each call - cheap, defensive against any other GL code that
     * might have rebound the binding point in between regions, and
     * required at minimum once per program switch (the binding-point
     * mapping inside the program is set up by
     * {@link GL31#glUniformBlockBinding} in the cache-refresh hook,
     * but the actual buffer at that binding point is global GL state
     * that any other code can clobber).
     */
    private static void phaze$uploadUbo(float[] vec4) {
        if (phaze$uboBufferId == 0) {
            phaze$uboBufferId = GL15.glGenBuffers();
        }
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, phaze$uboBufferId);
        // GL_STREAM_DRAW signals "written once per frame, drawn many
        // times" - the driver picks the appropriate memory pool
        // (often pinned host memory with a DMA upload). Same-size
        // glBufferData calls are idiomatic orphans: the driver
        // allocates fresh storage and the previous frame's GPU reads
        // continue against the old storage without stalling our CPU.
        GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, vec4, GL15.GL_STREAM_DRAW);
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER,
                ChunkAnimatorShaderPatcher.CHUNK_ANIM_UBO_BINDING,
                phaze$uboBufferId);
    }
}
