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
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL40C;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.implement.Rectangle;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;

import java.util.ArrayList;
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
 * The Phaze menu re-renders every visible module card from scratch
 * each frame: per-card that's ~5-10 rounded-rect SDF draws, ~3-5 MSDF
 * text draws, and an icon image - dozens of GL state changes /
 * uniform sets / BufferBuilder begin-end cycles per card. With ~30
 * cards visible in {@code MenuScreen} the per-frame overhead is
 * 1.5-3 ms even when nothing on-screen actually changes (no hover, no
 * toggle, palette stable). Because most cards spend most of their life
 * in a fully-static state, capturing the rendered pixels into an FBO
 * once and replaying that texture turns the per-frame cost into a
 * single textured-quad blit (~10 us per card).
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
 *   <li>Sodium / Iris hooks instrument framebuffer binds and shader
 *       binds; capture introduces extra binds compared to the eager
 *       path. In practice the saved per-card draw cost dwarfs the
 *       extra bind overhead.</li>
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
        Framebuffer mainFb;
        Matrix4f savedProj;
        ProjectionType savedProjType;
        int savedViewportX, savedViewportY, savedViewportW, savedViewportH;
        boolean savedScissorEnabled;
        int savedScissorX, savedScissorY, savedScissorW, savedScissorH;
        float[] savedShaderColor;
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
        Snapshot snap = CACHE.computeIfAbsent(key, k -> new Snapshot());
        if (snap.fbo == null || snap.fbWidth != fbWidth || snap.fbHeight != fbHeight) {
            if (snap.fbo != null) {
                snap.fbo.delete();
            }
            // SimpleFramebuffer(width, height, useDepth=false) - we
            // don't need a depth attachment because GUI components
            // are flat 2D draws ordered by call sequence, not by Z.
            snap.fbo = new SimpleFramebuffer(fbWidth, fbHeight, false);
            snap.fbo.setTexFilter(GL11C.GL_LINEAR);
            snap.fbWidth = fbWidth;
            snap.fbHeight = fbHeight;
            snap.populated = false;
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
        Snapshot snap = CACHE.remove(key);
        if (snap != null && snap.fbo != null) {
            snap.fbo.delete();
        }
    }

    /**
     * Drops every cached entry. Used on bulk invalidations - palette
     * change (every card needs the new colors), category switch (the
     * visible card set changed, old FBOs are no longer needed),
     * window/scale resize (FBO sizes are wrong).
     */
    public static void clearAll() {
        // Defensive copy because we mutate CACHE during iteration via
        // the closing loop (vanilla Framebuffer.delete unbinds, no
        // map-side effect, but this future-proofs the call against
        // any added side effect on Framebuffer.delete).
        List<Snapshot> snapshots = new ArrayList<>(CACHE.values());
        CACHE.clear();
        for (Snapshot s : snapshots) {
            if (s.fbo != null) {
                s.fbo.delete();
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
        s.mainFb = mc.getFramebuffer();
        s.savedProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        s.savedProjType = RenderSystem.getProjectionType();
        // Vanilla doesn't expose a getter for the current viewport, so
        // we ask GL directly. Same for scissor below.
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        s.savedViewportX = viewport[0];
        s.savedViewportY = viewport[1];
        s.savedViewportW = viewport[2];
        s.savedViewportH = viewport[3];
        s.savedScissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (s.savedScissorEnabled) {
            int[] scissor = new int[4];
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
            s.savedScissorX = scissor[0];
            s.savedScissorY = scissor[1];
            s.savedScissorW = scissor[2];
            s.savedScissorH = scissor[3];
            // Scissor box was set up against the main framebuffer
            // dimensions; it does not apply to our smaller card FBO
            // and would clip the entire card to nothing.
            RenderSystem.disableScissor();
        }
        s.savedShaderColor = RenderSystem.getShaderColor().clone();
        // Ensure the card's content is captured at full opacity - the
        // caller already excludes the cache while globalAlpha < 1.0
        // (menu fade), so any setShaderColor alpha less than 1 here
        // would be the caller's mistake. Reset for safety.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // Bind FBO + clear to transparent. Vanilla SimpleFramebuffer
        // defaults clearColor to (1, 1, 1, 0) (white with alpha 0)
        // which would tint anti-aliased edges; force fully transparent
        // black so blended pixels in the FBO source = (0, 0, 0, 0).
        snapshot.fbo.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        snapshot.fbo.clear();
        snapshot.fbo.beginWrite(true);

        RenderSystem.viewport(0, 0, snapshot.fbWidth, snapshot.fbHeight);

        // Card-local ortho: x=0..widthGui maps to NDC -1..1, y=0..
        // heightGui maps to NDC 1..-1 (y-down GUI convention). Same
        // projection vanilla uses for its DrawContext rendering, just
        // sized to the card box instead of the screen box.
        Matrix4f cardProj = new Matrix4f().setOrtho(0.0F, widthGui, heightGui, 0.0F, 1000.0F, 21000.0F);
        // The translate matches DrawContext's -2000 z-translate so
        // GUI z values stay inside the [near, far] range and don't
        // get z-clipped. (Without this, MC rounds z-positions like
        // -1000 outside the projection.)
        cardProj.translate(0.0F, 0.0F, -11000.0F);
        RenderSystem.setProjectionMatrix(cardProj, ProjectionType.ORTHOGRAPHIC);

        // Inform the rounded-rect SDF that gl_FragCoord during this
        // capture pass spans the small card FBO, not the main window
        // framebuffer. Without this, every batched rect's baseY is
        // computed against the 1000-2000px-tall main FB while the
        // card FBO only spans ~160-200px, so the SDF distance is
        // dominated by a constant offset and every fragment falls
        // outside the rounded box - the rect captures as fully
        // transparent and the cache blits an empty texture. Rect
        // body, outline, and the options/state rows all rely on
        // this; the card looks completely invisible without it.
        BatchedRectangle.setRenderTargetFbHeight(snapshot.fbHeight);

        active = s;
    }

    public static void endCapture() {
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
        BatchedRectangle.flushIfBatching();

        // Restore the main-window FB height baseline for SDF math so
        // any rects submitted AFTER this capture pass (e.g. the menu's
        // background, sibling cards still using the eager path,
        // post-card chrome) compute their RECT_BASE against the main
        // window framebuffer again. clearRenderTargetFbHeight() also
        // calls flushIfBatching internally as a defense against a
        // future caller queueing rects between our flush above and
        // this reset; with the queue empty here the inner flush is a
        // no-op.
        BatchedRectangle.clearRenderTargetFbHeight();

        // Restore main framebuffer first - any subsequent shader /
        // viewport / scissor changes must apply to the main FB so the
        // caller's after-capture draws (typically the blit itself)
        // render to the screen.
        s.mainFb.beginWrite(false);

        RenderSystem.viewport(s.savedViewportX, s.savedViewportY, s.savedViewportW, s.savedViewportH);
        RenderSystem.setProjectionMatrix(s.savedProj, s.savedProjType);
        if (s.savedScissorEnabled) {
            // RenderSystem.enableScissor takes (x, y, width, height)
            // in framebuffer coords - same coord system glGetIntegerv
            // returned, so just pass through.
            RenderSystem.enableScissor(s.savedScissorX, s.savedScissorY, s.savedScissorW, s.savedScissorH);
        }
        RenderSystem.setShaderColor(s.savedShaderColor[0], s.savedShaderColor[1], s.savedShaderColor[2], s.savedShaderColor[3]);

        s.snapshot.populated = true;
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
