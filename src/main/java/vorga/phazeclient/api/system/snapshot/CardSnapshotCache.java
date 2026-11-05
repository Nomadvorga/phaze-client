package vorga.phazeclient.api.system.snapshot;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL40C;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.implement.Rectangle;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.api.system.hud.Render2DState;

import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-component framebuffer (FBO) snapshot cache. Lets a heavy GUI
 * component (in particular module cards in the Phaze menu) render its
 * pixel content into a private offscreen color attachment ONCE, then
 * blit that texture to the main framebuffer on subsequent frames as
 * long as the component's visual state has not changed.
 *
 * <h3>Why per-card FBO caching</h3>
 *
 * Reusable off-screen snapshots, currently used by cosmetic previews.
 * Module-card caching is intentionally disabled in ModuleComponent
 * pending graphical validation. This class makes no FPS/timing guarantees;
 * callers must benchmark their live and cached paths in the actual game.
 *
 * <h3>Cache key</h3>
 *
 * The cache is keyed by component identity (object reference, via
 * {@link IdentityHashMap}) and the freshness of each entry is checked
 * against an int hash that the caller computes from EVERY visual input
 * the component reads while rendering: module flags (state / bind /
 * showEnable / locked), keybind value, hover/active flags, palette
 * colors that the card shader will sample, card width and height, and
 * any other state that ends up in the captured pixels. The caller is
 * responsible for both quantizing animation outputs into stable
 * buckets (or skipping the cache entirely while animations are in
 * flight) AND for invalidating on lifecycle events (category switch,
 * window resize, theme change) by calling {@link #invalidate} or
 * {@link #clearAll}.
 *
 * <h3>Capture mechanics</h3>
 *
 * {@link #beginCapture} saves the current GL state (projection
 * matrix + projection type, viewport, scissor box and enable flag,
 * shader-color, currently-bound framebuffer) and configures the GL
 * pipeline to render the next batch of draws into the component's FBO
 * at a card-local coordinate system: an orthographic projection of
 * (0,0)-(widthGui, heightGui) maps to NDC (-1,1)-(1,-1) with the
 * usual y-down GUI convention, and the matrix-stack push translates
 * the caller's positions back to local origin. The viewport spans the
 * full FBO texture dimensions (widthGui * scale, heightGui * scale).
 * Scissor is fully disabled during capture because the menu's scissor
 * box is in main-framebuffer pixel coords and would clip incorrectly
 * against the small card-local FBO. The currently-batched
 * {@link BatchedRectangle} queue is flushed on entry and exit so
 * pending rects rasterize into the correct framebuffer.
 *
 * <h3>Blit mechanics</h3>
 *
 * {@link #blit} draws a textured quad sampling the component's FBO
 * color attachment at the component's screen-space rectangle, with
 * vertex colors carrying a caller-supplied alpha multiplier so the
 * menu's open/close fade can apply to a card without re-capturing it.
 * Texture-y is flipped from the standard top-down GUI convention
 * because GL framebuffer origin is bottom-left.
 *
 * <h3>Limitations</h3>
 *
 * <ul>
 *   <li>FBO color is RGBA8, no depth attachment - components that
 *       depend on depth-test ordering inside their own draws would
 *       see no z-buffer effect inside capture.</li>
 *   <li>Sodium / Iris hooks can redirect framebuffer and shader binds.
 *       Captures need graphical validation with the actual shaderpack.</li>
 *   <li>Components that read the main framebuffer color (Phaze blur
 *       backdrops) cannot be cached this way - they explicitly opt
 *       out by never calling into this cache.</li>
 *   <li>Animation outputs that change every frame defeat the cache
 *       unless quantized; the caller (e.g. {@link
 *       vorga.phazeclient.implement.menu.components.implement.module.ModuleComponent})
 *       skips the cache while any of its animations are mid-transition.</li>
 * </ul>
 */
public final class CardSnapshotCache {
    private static final Rectangle RECTANGLE = new Rectangle();
    /**
     * Identity map - cards are referenced by the {@code ModuleComponent}
     * instance, NOT by the underlying module, so two components that
     * happen to wrap the same module (search results, detail panel)
     * each get their own FBO. {@code IdentityHashMap} avoids any
     * accidental {@code equals} / {@code hashCode} overrides on
     * components from collapsing distinct cards into the same entry.
     */
    private static final Map<Object, Snapshot> CACHE = new IdentityHashMap<>();

    /**
     * Active capture record while inside {@link #beginCapture} /
     * {@link #endCapture}. Single-element because GUI rendering is
     * single-threaded and captures are not nested - the client only
     * caches one component at a time per render call.
     */
    private static CaptureState active = null;

    private CardSnapshotCache() {
    }

    public static final class Snapshot {
        // Package-private fields (FBO + size) stay encapsulated -
        // {@link #beginCapture} / {@link #blit} are the only legitimate
        // callers that need to bind / sample the underlying texture.
        Framebuffer fbo;
        int fbWidth;
        int fbHeight;
        boolean useDepth;
        // hash / populated are the freshness contract between the
        // cache and its callers. {@code populated} is set by
        // {@link #endCapture} once the capture pass actually finishes;
        // {@code hash} is owned by the caller (the component knows
        // its visual inputs) so it's writable from outside this
        // package.
        public int hash;
        public boolean populated;
    }

    /**
     * Snapshot of the entire GL pipeline state we touch during capture
     * so {@link #endCapture} can restore exactly what the caller had
     * when {@link #beginCapture} was invoked. Without this, any
     * downstream rendering in the same frame would inherit the
     * card-local projection / viewport / scissor and silently
     * mis-render.
     */
    private static final class CaptureState {
        Snapshot snapshot;
        float savedTargetHeight;
        Render2DState renderState;
    }

    /** Framebuffer creation/deletion can bind FBO 0; preserve the enclosing GUI target. */
    private static final class FramebufferState implements AutoCloseable {
        private final int draw = GL11.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        private final int read = GL11.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        private final int[] viewport = new int[4];

        private FramebufferState() {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        }

        @Override
        public void close() {
            GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, draw);
            GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, read);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    /**
     * @return existing entry if its FBO matches the requested size,
     *         otherwise (re)allocates the FBO at the new size and
     *         marks the entry as needing re-capture by clearing
     *         {@code populated}. The returned snapshot's
     *         {@link Snapshot#hash} is preserved across resizes so a
     *         caller-driven hash check can decide independently
     *         whether content also needs re-rendering.
     */
    public static Snapshot getOrCreate(Object key, int fbWidth, int fbHeight) {
        return getOrCreate(key, fbWidth, fbHeight, false);
    }

    public static Snapshot getOrCreate(
            Object key,
            int fbWidth,
            int fbHeight,
            boolean useDepth
    ) {
        if (fbWidth <= 0 || fbHeight <= 0) {
            throw new IllegalArgumentException("Snapshot framebuffer dimensions must be positive");
        }
        Snapshot snap = CACHE.computeIfAbsent(key, k -> new Snapshot());
        if (snap.fbo == null
                || snap.fbWidth != fbWidth
                || snap.fbHeight != fbHeight
                || snap.useDepth != useDepth) {
            if (active != null && active.snapshot == snap) {
                throw new IllegalStateException("Cannot resize an active snapshot");
            }
            try (FramebufferState ignored = new FramebufferState()) {
                if (snap.fbo != null) {
                    snap.fbo.delete();
                    snap.fbo = null;
                }
                snap.populated = false;
                snap.fbo = new SimpleFramebuffer(fbWidth, fbHeight, useDepth);
                snap.fbo.setTexFilter(GL11C.GL_LINEAR);
                snap.fbWidth = fbWidth;
                snap.fbHeight = fbHeight;
                snap.useDepth = useDepth;
            }
        }
        return snap;
    }

    /**
     * Drops the entry for {@code key} and frees its FBO. Call from
     * any lifecycle hook that knows the component is gone or its
     * pixel content can no longer be reused (e.g. the component is
     * detached from the screen, or the screen itself closes).
     */
    public static void invalidate(Object key) {
        Snapshot snap = CACHE.get(key);
        if (active != null && active.snapshot == snap) {
            throw new IllegalStateException("Cannot delete an active snapshot");
        }
        CACHE.remove(key);
        if (snap != null && snap.fbo != null) {
            try (FramebufferState ignored = new FramebufferState()) {
                snap.fbo.delete();
                snap.fbo = null;
                snap.populated = false;
            }
        }
    }

    /**
     * Drops every cached entry. Used on bulk invalidations - palette
     * change (every card needs the new colors), category switch (the
     * visible card set changed, old FBOs are no longer needed),
     * window/scale resize (FBO sizes are wrong).
     */
    public static void clearAll() {
        endCapture(false);
        // Defensive copy because we mutate CACHE during iteration via
        // the closing loop (vanilla Framebuffer.delete unbinds, no
        // map-side effect, but this future-proofs the call against
        // any added side effect on Framebuffer.delete).
        List<Snapshot> snapshots = new ArrayList<>(CACHE.values());
        CACHE.clear();
        if (snapshots.isEmpty()) return;
        try (FramebufferState ignored = new FramebufferState()) {
            for (Snapshot s : snapshots) {
                if (s.fbo != null) {
                    s.fbo.delete();
                    s.fbo = null;
                    s.populated = false;
                }
            }
        }
    }

    /**
     * Begins rendering into {@code snapshot.fbo} at the given GUI-pixel
     * card-local size. Saves the entire GL pipeline state described in
     * {@link CaptureState}, binds the FBO, sets a card-local
     * orthographic projection, sets the viewport to the FBO size,
     * disables scissor for the duration, and clears the FBO to fully
     * transparent (color 0,0,0,0).
     *
     * <p>Expected call shape:
     * <pre>{@code
     * cache.beginCapture(snap, widthGui, heightGui);
     * try {
     *     matrices.push();
     *     matrices.translate(-cardX, -cardY, 0); // remap to local origin
     *     // ... draw card content via the component's normal render path
     *     BatchedRectangle.flushIfBatching();
     *     matrices.pop();
     * } finally {
     *     cache.endCapture();
     * }
     * }</pre>
     *
     * <p>The caller must NOT issue draws to any other framebuffer
     * between begin and end - all GL state changes are scoped to the
     * snapshot's FBO until {@link #endCapture} runs.
     */
    public static void beginCapture(Snapshot snapshot, float widthGui, float heightGui) {
        if (active != null) throw new IllegalStateException("Snapshot captures cannot be nested");
        if (!(widthGui > 0) || !(heightGui > 0) || !Float.isFinite(widthGui) || !Float.isFinite(heightGui)) {
            throw new IllegalArgumentException("Snapshot dimensions must be positive");
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getFramebuffer() == null || snapshot == null || snapshot.fbo == null) {
            return;
        }

        // Drain any rectangles still queued in the parent's
        // BatchedRectangle scope - they belong on the main FB, not
        // the FBO we're about to bind. Without this flush, the
        // following Tessellator/BufferBuilder usage during card
        // capture would either reuse the parent's open builder
        // (writing those rects into the FBO instead of main FB) or
        // collide with the singleton tessellator's "one buffer at a
        // time" invariant.
        BatchedRectangle.flushIfBatching();

        CaptureState s = new CaptureState();
        s.snapshot = snapshot;
        s.renderState = Render2DState.capture();
        s.savedTargetHeight = BatchedRectangle.getRenderTargetFbHeight();
        active = s;
        snapshot.populated = false;
        try {
            RenderSystem.disableScissor();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            snapshot.fbo.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            snapshot.fbo.clear();
            snapshot.fbo.beginWrite(true);
            RenderSystem.viewport(0, 0, snapshot.fbWidth, snapshot.fbHeight);

            // Preserve the established card-local projection and GUI Z range.
            Matrix4f cardProj = new Matrix4f().setOrtho(0.0F, widthGui, heightGui, 0.0F, 1000.0F, 21000.0F);
            cardProj.translate(0.0F, 0.0F, -11000.0F);
            RenderSystem.setProjectionMatrix(cardProj, ProjectionType.ORTHOGRAPHIC);
            BatchedRectangle.setRenderTargetFbHeight(snapshot.fbHeight);
        } catch (RuntimeException | Error failure) {
            try {
                endCapture(false);
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    public static void endCapture() {
        endCapture(true);
    }

    /** An interrupted/failed capture must never become a reusable snapshot. */
    public static void endCapture(boolean completed) {
        if (active == null) {
            return;
        }
        CaptureState s = active;
        active = null;

        // Drain anything still queued (BatchedRectangle, MSDF buffer,
        // etc.) into the FBO before we unbind it. Without this flush,
        // a pending BufferBuilder.end during the next frame would
        // emit those vertices into whatever FB was current then.
        // Flush here BEFORE clearing the FB-height override so the
        // queued vertices, whose RECT_BASE values were pre-baked
        // against the card FBO height, render correctly into the
        // FBO.
        boolean flushed = false;
        try {
            BatchedRectangle.flushIfBatching();
            flushed = true;
        } finally {
            try {
                BatchedRectangle.restoreRenderTargetFbHeight(s.savedTargetHeight);
            } finally {
                restoreCaptureState(s);
                s.snapshot.populated = completed && flushed;
            }
        }
    }

    private static void restoreCaptureState(CaptureState s) {
        // Restore the actual caller target, which need not be the main framebuffer.
        s.renderState.close();
    }

    /**
     * Entity render layers (and Iris' compatible replacements) may bind their
     * own target while a GUI snapshot is being populated. Call immediately
     * after a layer starts so its shader/blend state is retained but geometry
     * still lands in the active transparent snapshot instead of the main
     * framebuffer.
     */
    public static void rebindActiveCaptureTarget() {
        if (active == null || active.snapshot == null
                || active.snapshot.fbo == null) {
            return;
        }
        active.snapshot.fbo.beginWrite(false);
        RenderSystem.viewport(
                0, 0, active.snapshot.fbWidth, active.snapshot.fbHeight
        );
    }

    /**
     * One-time validation for third-party render pipelines. Iris/RenderLayer
     * may legally redirect a draw after an off-screen FBO was bound; in that
     * case the snapshot is marked populated but remains fully transparent.
     * Reading it once after creation lets callers fall back to their live VBO
     * path instead of displaying a permanently blank card.
     */
    public static boolean hasVisiblePixels(Snapshot snapshot) {
        if (snapshot == null || snapshot.fbo == null
                || snapshot.fbWidth <= 0 || snapshot.fbHeight <= 0) {
            return false;
        }
        int previousRead = GL11.glGetInteger(
                GL30C.GL_READ_FRAMEBUFFER_BINDING
        );
        ByteBuffer pixels = BufferUtils.createByteBuffer(
                snapshot.fbWidth * snapshot.fbHeight * 4
        );
        try {
            GlStateManager._glBindFramebuffer(
                    GL30C.GL_READ_FRAMEBUFFER, snapshot.fbo.fbo
            );
            GL11.glReadPixels(
                    0, 0, snapshot.fbWidth, snapshot.fbHeight,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels
            );
            for (int i = 3; i < pixels.capacity(); i += 4) {
                if ((pixels.get(i) & 0xFF) > 2) {
                    return true;
                }
            }
            return false;
        } finally {
            GlStateManager._glBindFramebuffer(
                    GL30C.GL_READ_FRAMEBUFFER, previousRead
            );
        }
    }

    /**
     * Draws the snapshot's FBO color texture as a textured quad at
     * (x, y, x+widthGui, y+heightGui) on whatever framebuffer is
     * currently bound. {@code alpha} multiplies the texel alpha so the
     * caller can fade a card in / out (menu open/close) without
     * having to re-capture at every fade frame.
     *
     * <p>Texture coords are y-flipped from the standard GUI textured-
     * quad convention because the FBO color attachment uses
     * GL-default origin (bottom-left). With v=1 at the quad's TOP
     * vertex and v=0 at the BOTTOM vertex, the FBO content appears
     * right-side up to the user.
     */
    public static void blit(DrawContext context, Snapshot snapshot, float x, float y, float widthGui, float heightGui, float alpha) {
        if (snapshot == null || snapshot.fbo == null || !snapshot.populated) {
            return;
        }

        // Defensive flush: if the parent is mid-batch, blit's own
        // BufferBuilder must not collide with that pending batch.
        BatchedRectangle.flushIfBatching();

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        // Pack alpha into the per-vertex int color so the standard
        // POSITION_TEX_COLOR shader applies it as a multiplier
        // against the sampled FBO texel without us having to set
        // RenderSystem.setShaderColor (which is global state and
        // would leak into subsequent draws if we forgot to reset).
        int alphaByte = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        int color = (alphaByte << 24) | 0x00FFFFFF;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, snapshot.fbo.getColorAttachment());
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        // Standard GUI quad winding (TL, BL, BR, TR), with v flipped
        // so the FBO renders right-side up.
        buffer.vertex(matrix, x, y, 0.0F).texture(0.0F, 1.0F).color(color);
        buffer.vertex(matrix, x, y + heightGui, 0.0F).texture(0.0F, 0.0F).color(color);
        buffer.vertex(matrix, x + widthGui, y + heightGui, 0.0F).texture(1.0F, 0.0F).color(color);
        buffer.vertex(matrix, x + widthGui, y, 0.0F).texture(1.0F, 1.0F).color(color);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }

    public static void blitRounded(
            DrawContext context,
            Snapshot snapshot,
            float x,
            float y,
            float widthGui,
            float heightGui,
            float alpha,
            float round,
            int backgroundColor
    ) {
        if (snapshot == null || snapshot.fbo == null || !snapshot.populated) {
            return;
        }

        BatchedRectangle.flushIfBatching();
        RECTANGLE.render(ShapeProperties.create(context.getMatrices(), x, y, widthGui, heightGui)
                .round(round)
                .color(backgroundColor)
                .build());

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        int alphaByte = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        int color = (alphaByte << 24) | 0x00FFFFFF;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL40C.GL_DST_ALPHA, GL40C.GL_ONE_MINUS_DST_ALPHA);
        RenderSystem.setShaderTexture(0, snapshot.fbo.getColorAttachment());
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, x, y, 0.0F).texture(0.0F, 1.0F).color(color);
        buffer.vertex(matrix, x, y + heightGui, 0.0F).texture(0.0F, 0.0F).color(color);
        buffer.vertex(matrix, x + widthGui, y + heightGui, 0.0F).texture(1.0F, 0.0F).color(color);
        buffer.vertex(matrix, x + widthGui, y, 0.0F).texture(1.0F, 1.0F).color(color);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * Copies a color region from an already-rendered framebuffer into
     * the snapshot FBO exactly once, without re-running the source
     * screen's render logic. Useful for freezing a live background as
     * a static preview thumbnail.
     *
     * <p>Source coordinates are in framebuffer pixels with the GL
     * bottom-left origin convention used by {@code glBlitFramebuffer}.
     * The source region is linearly scaled to the snapshot FBO size.
     */
    public static void copyRegionFromFramebuffer(Snapshot snapshot, Framebuffer source, int srcX, int srcY, int srcWidth, int srcHeight) {
        if (snapshot == null || snapshot.fbo == null || source == null || srcWidth <= 0 || srcHeight <= 0) {
            return;
        }

        BatchedRectangle.flushIfBatching();

        int previousRead = GL11.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);

        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, source.fbo);
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, snapshot.fbo.fbo);
        GL30C.glBlitFramebuffer(
                srcX,
                srcY,
                srcX + srcWidth,
                srcY + srcHeight,
                0,
                0,
                snapshot.fbWidth,
                snapshot.fbHeight,
                GL11C.GL_COLOR_BUFFER_BIT,
                GL11C.GL_LINEAR
        );
        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousRead);
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDraw);
        snapshot.populated = true;
    }
}
