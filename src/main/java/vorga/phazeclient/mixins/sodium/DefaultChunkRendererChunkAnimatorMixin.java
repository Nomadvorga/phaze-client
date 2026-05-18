package vorga.phazeclient.mixins.sodium;

import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.iterator.ByteIterator;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
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
     * Cache of the patched-shader uniform locations keyed by
     * {@link ChunkShaderInterface} object identity. Each Sodium
     * program has its own ChunkShaderInterface instance, so reference
     * equality on the shader is a sound proxy for "is this the same
     * GL program as last time?" without paying the driver round-trip
     * cost of {@code glGetInteger(GL_CURRENT_PROGRAM)} on every
     * region. Sodium iterates many regions back-to-back with the same
     * bound shader (one shader per render pass), so the identity
     * comparison short-circuits TRUE for the vast majority of calls;
     * the driver query only fires on shader switches (a few times per
     * frame total instead of ~30+).
     *
     * <p>{@code -1} on either loc means the uniform isn't present in
     * the current program (the shader was compiled without our patch,
     * or the GLSL compiler optimised it out), which we treat as
     * "do nothing on this program; fall back to the region-level path".
     *
     * <p>Held by reference identity, NOT a strong reference for
     * lifetime purposes - Sodium owns the shader's lifecycle. If a
     * program is destroyed and a new one allocated, the next call
     * sees a different identity and re-queries. The stale int loc
     * left behind is harmless; we never use it without first matching
     * identities.
     */
    private static ChunkShaderInterface phaze$cachedShader;
    private static int phaze$cachedOffsetLoc = -1;
    private static int phaze$cachedDirLoc    = -1;

    /**
     * Tracks whether the last value written into the
     * {@code u_PhazeChunkAnimY} uniform of the cached program was
     * "all zeros". When the next region's offsets are also all zero
     * we skip the {@code glUniform1fv} entirely - which is the
     * common steady-state case once every visible chunk has finished
     * animating. Whole-array uploads cost ~5 microseconds each on
     * typical drivers; bypassing them turns the per-frame overhead
     * from ~1ms to a handful of microseconds. The flag resets on
     * program switch (a fresh-linked program starts with all-zero
     * uniforms by GL spec, so {@code true} is the correct initial
     * state).
     */
    private static boolean phaze$lastUploadWasZero = true;

    /**
     * Reusable scratch buffer for the float[256] upload. Avoids
     * one ~1KB allocation per region per frame; the per-region
     * call site copies the offset values into this buffer in
     * {@link ChunkAnimator#writeRegionSectionYOffsets} order.
     * Single thread (render thread) writes / reads it.
     */
    private static final float[] PHAZE$SCRATCH = new float[256];

    /**
     * Reusable 3-element direction buffer. {@code writeAnimationDirection}
     * fills it before each upload of {@code u_PhazeChunkAnimDir}.
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
            phaze$cachedOffsetLoc =
                    GL20.glGetUniformLocation(programId, "u_PhazeChunkAnimOffset");
            phaze$cachedDirLoc =
                    GL20.glGetUniformLocation(programId, "u_PhazeChunkAnimDir");
            // Force the next upload regardless of hasNonZero. Uniform
            // state persists per-GL-program for the program's lifetime;
            // when Iris swaps between shadow/main/gbuffers terrain
            // programs each frame, the program we just rebound still
            // holds whatever offsets we last uploaded to it - which
            // belonged to a DIFFERENT region's animations on the
            // previous (program,region) tuple. Skipping the upload
            // because "the last upload anywhere was zeros" leaks those
            // stale per-slot offsets back onto random sections,
            // producing the scattered chunk-fragment artifact users
            // see after re-entering an area with shaders on. Starting
            // a freshly-switched program with lastUploadWasZero=false
            // guarantees the first upload re-zeroes (or refreshes)
            // its uniform state.
            phaze$lastUploadWasZero = false;
        }
        // Module disabled: cache is now refreshed for the TAIL flush
        // to use, no fallback region-offset shift needed (the section
        // animation it would amplify is gone). Bail.
        if (!animator.isEnabled()) {
            return;
        }
        // If the patched uniform exists, the per-section TAIL path
        // handles this region. Don't shift the region offset here -
        // that would double-apply on top of per-vertex offsets.
        if (phaze$cachedOffsetLoc >= 0) {
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

        // Module disabled mid-frame: flush a single zero array into
        // u_PhazeChunkAnimOffset so the shader stops adding stale
        // per-section offsets to every vertex. Without this, any
        // sections that were mid-fall when the user toggled the
        // module off freeze in mid-air at whatever the last upload
        // told them - the user-reported "chunks stuck floating in
        // the sky after disabling Chunk Animator" bug. Once
        // phaze$lastUploadWasZero is true the GPU is already in the
        // identity state, so subsequent regions on the same program
        // can early-return; a shader switch (Iris pass change, etc.)
        // resets the flag in @ModifyArgs and the next disabled-frame
        // region of the new program does its own one-shot flush.
        if (!animator.isEnabled()) {
            if (phaze$cachedOffsetLoc < 0 || phaze$lastUploadWasZero) {
                return;
            }
            java.util.Arrays.fill(PHAZE$SCRATCH, 0, 256, 0.0F);
            GL20.glUniform1fv(phaze$cachedOffsetLoc, PHAZE$SCRATCH);
            phaze$lastUploadWasZero = true;
            return;
        }

        // Pull the per-frame render list for this region. Sodium
        // populates it during update() (before render()) with the
        // slot indices of sections that actually have geometry to
        // draw this frame - exactly the sections we want to register
        // first-seen times for. Sections still uploading or out of
        // view are absent from this list; they keep their u_Phaze
        // ChunkAnimY entry at 0.0F so they're not yet animating.
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

        // The cache was already refreshed by the @ModifyArgs that
        // ran earlier in this same setModelMatrixUniforms invocation.
        // We just check the result here. If the shader-side patch
        // didn't apply (e.g. Iris with shaders), -1 means "the
        // fallback @ModifyArgs already shifted setRegionOffset for
        // us" and we have nothing to upload.
        if (phaze$cachedOffsetLoc < 0) {
            return;
        }

        // Write directly into the persistent scratch buffer to avoid
        // a 1KB float[] allocation per region per frame. The buffer
        // is zeroed inside writeRegionSectionYOffsets so any leftover
        // values from the previous region don't bleed in.
        boolean hasNonZero = animator.writeRegionSectionYOffsets(
                region.getOriginX(), region.getOriginY(), region.getOriginZ(),
                PHAZE$SLOT_BUFFER, slotCount, PHAZE$SCRATCH
        );

        // Steady-state fast path: no active animations in this region
        // and the GPU-side uniform is already all-zero from the last
        // upload. Skipping glUniform1fv here is what keeps the per-
        // frame cost at ~tens of microseconds once the world has
        // settled, instead of paying the upload on every region. The
        // direction uniform tags along - if magnitudes are all zero
        // it doesn't matter what direction is on the GPU, the shader
        // multiplies by zero anyway.
        if (!hasNonZero && phaze$lastUploadWasZero) {
            return;
        }
        GL20.glUniform1fv(phaze$cachedOffsetLoc, PHAZE$SCRATCH);
        if (phaze$cachedDirLoc >= 0) {
            // The per-section path needs the "per-section" direction
            // variant (always (0,1,0) for Top/Bottom) because
            // writeRegionSectionYOffsets fills the array with SIGNED
            // Y deltas: positive for Top, negative for Bottom. Using
            // the old writeAnimationDirection here would flip
            // Bottom's negative deltas back to positive Y inside the
            // shader's "offset * dir" multiply, lifting the section
            // above its target instead of below it.
            animator.writeAnimationDirectionPerSection(PHAZE$DIR);
            GL20.glUniform3f(phaze$cachedDirLoc, PHAZE$DIR[0], PHAZE$DIR[1], PHAZE$DIR[2]);
        }
        phaze$lastUploadWasZero = !hasNonZero;
    }
}
