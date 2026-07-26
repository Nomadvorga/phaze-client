package vorga.phazeclient.api.system.snapshot;

import vorga.phazeclient.base.util.render.GuiMatrix;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ScissorState;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import vorga.phazeclient.api.system.draw.GpuDraw;
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
 * text draws, and an icon image - dozens of pipeline switches /
 * uniform writes / BufferBuilder begin-end cycles per card. With ~30
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
 * <h3>Capture mechanics (1.21.11)</h3>
 *
 * 1.21.4 bound the FBO with {@code Framebuffer.beginWrite} and every
 * subsequent immediate-mode draw landed in it. 1.21.11 has no bound
 * framebuffer at all - the render target is picked when a render pass
 * is created. The equivalent redirect is
 * {@link RenderSystem#outputColorTextureOverride} /
 * {@link RenderSystem#outputDepthTextureOverride}: {@code RenderLayer.draw}
 * reads both and, when set, targets those views instead of the layer's
 * own output framebuffer (verified in the remapped jar). Since every
 * Phaze GUI draw goes through a {@code RenderLayer}, setting the two
 * overrides reproduces the old "bind and everything follows" behaviour.
 *
 * <p>{@link #beginCapture} therefore saves the projection UBO slice +
 * projection type, the render-type scissor state and both texture
 * overrides, installs the card FBO as the override target, clears it to
 * fully transparent, and sets a card-local orthographic projection of
 * (0,0)-(widthGui, heightGui) with the usual y-down GUI convention. The
 * viewport is no longer ours to set - a render pass derives it from the
 * attachment it was created with, which is exactly the FBO texture size
 * (widthGui * scale, heightGui * scale). Scissor is fully disabled during
 * capture because the menu's scissor box is in main-framebuffer pixel
 * coords and would clip incorrectly against the small card-local FBO.
 * The currently-batched {@link BatchedRectangle} queue is flushed on
 * entry and exit so pending rects rasterize into the correct target.
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
 *   <li>Components that read the main framebuffer color (Phaze blur
 *       backdrops) cannot be cached this way - they explicitly opt
 *       out by never calling into this cache.</li>
 *   <li>Animation outputs that change every frame defeat the cache
 *       unless quantized; the caller (e.g. {@link
 *       vorga.phazeclient.implement.menu.components.implement.module.ModuleComponent})
 *       skips the cache while any of its animations are mid-transition.</li>
 *   <li>TODO(1.21.11): the clear and the projection-UBO write issued by
 *       {@link #beginCapture} both go through {@code CommandEncoder},
 *       which throws if a render pass is already open. {@code GuiRenderer}
 *       keeps one pass open across the whole GUI batch, so capture has to
 *       run before that pass opens (or from a
 *       {@code SpecialGuiElementRenderer#prepare}). Same constraint as
 *       Blur - tracked as wave-B work, not fixable from inside this
 *       class.</li>
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

    /**
     * Card-local projection UBO. 1.21.11 has no
     * {@code setProjectionMatrix(Matrix4f, ProjectionType)} - the
     * projection is a std140 buffer slice, and {@link ProjectionMatrix2}
     * is vanilla's writer for it (it re-uploads only when the
     * width/height it is handed actually changes, so re-using one
     * instance across differently-sized cards is correct, just not free).
     *
     * <p>near/far are {@code -1000 / 1000}, matching vanilla's
     * {@code itemsProjectionMatrix}, NOT the {@code 1000 / 11000} of its
     * {@code guiProjectionMatrix}. The 1.21.4 code here built
     * {@code setOrtho(0, w, h, 0, 1000, 21000)} and then
     * {@code translate(0, 0, -11000)} so that a z=0 vertex landed at NDC
     * z=0; vanilla's gui projection gets that same shift from the
     * {@code -11000} translation baked into its DynamicTransforms
     * model-view, which our draws do not carry. A symmetric
     * {@code -1000/1000} range maps z=0 to NDC 0 on its own and is
     * therefore the faithful port of the old matrix.
     *
     * <p>Created lazily: the constructor allocates a GpuBuffer and so
     * needs a live GpuDevice. Never closed - it lives for the process,
     * like vanilla's own two instances.
     */
    private static ProjectionMatrix2 cardProjection;

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
     * Snapshot of the pipeline state we touch during capture so
     * {@link #endCapture} can restore exactly what the caller had when
     * {@link #beginCapture} was invoked. Without this, any downstream
     * rendering in the same frame would inherit the card-local
     * projection / render target / scissor and silently mis-render.
     *
     * <p>1.21.11 delta: the viewport is no longer global state (a render
     * pass takes it from its attachment), and there is no global shader
     * colour to save at all, so both are gone from this record. What
     * replaced the framebuffer bind is the pair of texture overrides.
     */
    private static final class CaptureState {
        Snapshot snapshot;
        GpuBufferSlice savedProjection;
        ProjectionType savedProjectionType;
        GpuTextureView savedColorOverride;
        GpuTextureView savedDepthOverride;
        boolean savedScissorEnabled;
        int savedScissorX, savedScissorY, savedScissorW, savedScissorH;
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
            // 1.21.11: SimpleFramebuffer takes the debug name FIRST.
            //
            // useDepth flipped false -> true. GUI content is still flat
            // 2D ordered by call sequence, but RenderLayer.draw picks its
            // depth attachment from the *layer's* output framebuffer
            // (the main one, which has depth) unless
            // outputDepthTextureOverride is set. Handing it the main
            // depth view alongside a card-sized colour view would mean an
            // attachment-size mismatch and a depth test against unrelated
            // contents, so the card owns a matching depth texture that
            // beginCapture clears with the colour.
            snap.fbo = new SimpleFramebuffer("phaze/card_snapshot", fbWidth, fbHeight, true);
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
        // the closing loop (vanilla Framebuffer.delete closes the
        // attachments, no map-side effect, but this future-proofs the
        // call against any added side effect on Framebuffer.delete).
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
     * card-local size. Saves the pipeline state described in
     * {@link CaptureState}, installs the FBO as the render-target
     * override, sets a card-local orthographic projection, disables
     * scissor for the duration, and clears the FBO to fully transparent
     * (0x00000000) plus depth 1.0.
     *
     * <p>Expected call shape:
     * <pre>{@code
     * cache.beginCapture(snap, widthGui, heightGui);
     * try {
     *     matrices.pushMatrix();
     *     matrices.translate(-cardX, -cardY); // remap to local origin
     *     // ... draw card content via the component's normal render path
     *     BatchedRectangle.flushIfBatching();
     *     matrices.popMatrix();
     * } finally {
     *     cache.endCapture();
     * }
     * }</pre>
     *
     * <p>The caller must NOT issue draws to any other framebuffer
     * between begin and end - the target override stays installed until
     * {@link #endCapture} runs.
     */
    public static void beginCapture(Snapshot snapshot, float widthGui, float heightGui) {
        if (snapshot == null || snapshot.fbo == null) {
            return;
        }
        RenderSystem.assertOnRenderThread();

        // Drain any rectangles still queued in the parent's
        // BatchedRectangle scope - they belong on the main FB, not
        // the FBO we're about to redirect to. Without this flush, the
        // following Tessellator/BufferBuilder usage during card
        // capture would either reuse the parent's open builder
        // (writing those rects into the FBO instead of main FB) or
        // collide with the singleton tessellator's "one buffer at a
        // time" invariant.
        BatchedRectangle.flushIfBatching();

        CaptureState s = new CaptureState();
        s.snapshot = snapshot;
        s.savedProjection = RenderSystem.getProjectionMatrixBuffer();
        s.savedProjectionType = RenderSystem.getProjectionType();
        s.savedColorOverride = RenderSystem.outputColorTextureOverride;
        s.savedDepthOverride = RenderSystem.outputDepthTextureOverride;

        // 1.21.11 exposes the render-type scissor as a real object, so
        // the old glGetIntegerv(GL_SCISSOR_BOX) round-trip is gone.
        ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
        s.savedScissorEnabled = scissor.isEnabled();
        if (s.savedScissorEnabled) {
            s.savedScissorX = scissor.getX();
            s.savedScissorY = scissor.getY();
            s.savedScissorW = scissor.getWidth();
            s.savedScissorH = scissor.getHeight();
            // Scissor box was set up against the main framebuffer
            // dimensions; it does not apply to our smaller card FBO
            // and would clip the entire card to nothing.
            RenderSystem.disableScissorForRenderTypeDraws();
        }

        // Clear to transparent BLACK, not vanilla's old (1,1,1,0) white,
        // so blended pixels in the FBO source stay (0,0,0,0) and
        // anti-aliased edges are not tinted. Depth goes to 1.0 so the
        // LEQUAL layers Phaze draws through still pass.
        //
        // TODO(1.21.11): clears throw if a render pass is currently open
        // (GuiRenderer holds one for the whole GUI batch) - see the
        // class javadoc.
        GpuTexture color = snapshot.fbo.getColorAttachment();
        GpuTexture depth = snapshot.fbo.getDepthAttachment();
        if (depth != null) {
            RenderSystem.getDevice().createCommandEncoder()
                    .clearColorAndDepthTextures(color, 0x00000000, depth, 1.0);
        } else {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(color, 0x00000000);
        }

        // Replaces Framebuffer.beginWrite(true): RenderLayer.draw reads
        // these two and, when non-null, creates its render pass against
        // them instead of the layer's own output framebuffer.
        RenderSystem.outputColorTextureOverride = snapshot.fbo.getColorAttachmentView();
        RenderSystem.outputDepthTextureOverride = snapshot.fbo.useDepthAttachment
                ? snapshot.fbo.getDepthAttachmentView()
                : null;

        // Card-local ortho: x=0..widthGui maps to NDC -1..1, y=0..
        // heightGui maps to NDC 1..-1 (y-down GUI convention, which is
        // what ProjectionMatrix2's invertY=true flag produces). See the
        // cardProjection field comment for the near/far choice.
        if (cardProjection == null) {
            cardProjection = new ProjectionMatrix2("phaze/card snapshot", -1000.0F, 1000.0F, true);
        }
        RenderSystem.setProjectionMatrix(
                cardProjection.set(widthGui, heightGui), ProjectionType.ORTHOGRAPHIC);

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
        // etc.) into the FBO before we drop the target override.
        // Without this flush, a pending BufferBuilder.end during the
        // next frame would emit those vertices into whatever target was
        // current then. Flush here BEFORE clearing the FB-height
        // override so the queued vertices, whose RECT_BASE values were
        // pre-baked against the card FBO height, render correctly into
        // the FBO.
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

        // Drop the render-target override first - any subsequent
        // projection / scissor change must apply to the caller's target
        // so the after-capture draws (typically the blit itself) render
        // to the screen. Restoring the previous values rather than
        // hard-nulling keeps us nestable inside vanilla's own override
        // scopes (GuiRenderer uses them for the item atlas).
        RenderSystem.outputColorTextureOverride = s.savedColorOverride;
        RenderSystem.outputDepthTextureOverride = s.savedDepthOverride;

        if (s.savedProjection != null) {
            RenderSystem.setProjectionMatrix(s.savedProjection, s.savedProjectionType);
        }
        if (s.savedScissorEnabled) {
            // enableScissorForRenderTypeDraws keeps the old
            // RenderSystem.enableScissor convention - (x, y, width,
            // height) in framebuffer coords, the same shape ScissorState
            // reported - so this is a straight pass-through.
            // NOTE: DrawContext.enableScissor is (x1, y1, x2, y2) and is
            // NOT interchangeable here.
            RenderSystem.enableScissorForRenderTypeDraws(s.savedScissorX, s.savedScissorY, s.savedScissorW, s.savedScissorH);
        }

        s.snapshot.populated = true;
    }

    /**
     * Straight alpha-blended blit of a snapshot texture.
     *
     * <p>Built on vanilla's position_tex_color shaders. The blend that used
     * to be {@code RenderSystem.defaultBlendFunc()} around the draw is on
     * the pipeline now, so it travels with it.
     *
     * <p>Deliberately NOT {@link BlendFunction#TRANSLUCENT}: that one's
     * destination-alpha factor is {@code ONE_MINUS_SRC_ALPHA}, while
     * {@code defaultBlendFunc} used {@code ZERO}. The difference is
     * invisible on the opaque main framebuffer but accumulates
     * destination alpha on any alpha-carrying offscreen target - i.e.
     * exactly the case where one cached card is blitted while another
     * card's capture is active.
     */
    private static final RenderPipeline TRANSLUCENT_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/card_snapshot"))
            .withVertexShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withFragmentShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA,
                    SourceFactor.ONE, DestFactor.ZERO))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    /**
     * Same, but with the DST_ALPHA blend the rounded variant relies on to
     * clip the snapshot against the rounded background drawn underneath.
     * Two-factor form on purpose - the 1.21.4 call was a plain
     * {@code glBlendFunc}, which applies the pair to colour and alpha alike.
     */
    private static final RenderPipeline DST_ALPHA_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/card_snapshot_rounded"))
            .withVertexShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withFragmentShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(new BlendFunction(SourceFactor.DST_ALPHA, DestFactor.ONE_MINUS_DST_ALPHA))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    /**
     * Draws the snapshot's FBO color texture as a textured quad at
     * (x, y, x+widthGui, y+heightGui). {@code alpha} multiplies the
     * texel alpha so the caller can fade a card in / out (menu
     * open/close) without having to re-capture at every fade frame.
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

        Matrix4f matrix = GuiMatrix.mat4(context.getMatrices());

        // Pack alpha into the per-vertex int color so the standard
        // POSITION_TEX_COLOR shader applies it as a multiplier
        // against the sampled FBO texel. 1.21.11 has no
        // RenderSystem.setShaderColor at all, so this is now the only
        // way - and it never leaks into a later draw.
        int alphaByte = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        int color = (alphaByte << 24) | 0x00FFFFFF;

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        // Standard GUI quad winding (TL, BL, BR, TR), with v flipped
        // so the FBO renders right-side up.
        buffer.vertex(matrix, x, y, 0.0F).texture(0.0F, 1.0F).color(color);
        buffer.vertex(matrix, x, y + heightGui, 0.0F).texture(0.0F, 0.0F).color(color);
        buffer.vertex(matrix, x + widthGui, y + heightGui, 0.0F).texture(1.0F, 0.0F).color(color);
        buffer.vertex(matrix, x + widthGui, y, 0.0F).texture(1.0F, 1.0F).color(color);

        // 1.21.11: blend is on the pipeline and the sampler is bound on the
        // pass. This is a matrix-transformed, alpha-modulated sub-rect, so
        // Framebuffer.drawBlit is NOT a substitute - that one is always
        // full-screen, reads no matrix and has no tint path.
        BuiltBuffer built = buffer.endNullable();
        if (built != null) {
            try {
                GpuDraw.draw(TRANSLUCENT_PIPELINE, built, "Sampler0",
                        snapshot.fbo.getColorAttachmentView(), FilterMode.LINEAR, matrix);
            } finally {
                built.close();
            }
        }
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

        Matrix4f matrix = GuiMatrix.mat4(context.getMatrices());
        int alphaByte = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        int color = (alphaByte << 24) | 0x00FFFFFF;

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, x, y, 0.0F).texture(0.0F, 1.0F).color(color);
        buffer.vertex(matrix, x, y + heightGui, 0.0F).texture(0.0F, 0.0F).color(color);
        buffer.vertex(matrix, x + widthGui, y + heightGui, 0.0F).texture(1.0F, 0.0F).color(color);
        buffer.vertex(matrix, x + widthGui, y, 0.0F).texture(1.0F, 1.0F).color(color);

        // The DST_ALPHA / ONE_MINUS_DST_ALPHA blend that used to be set with
        // blendFunc lives on its own pipeline now - that is what clips the
        // snapshot to the rounded background drawn just above.
        BuiltBuffer built = buffer.endNullable();
        if (built != null) {
            try {
                GpuDraw.draw(DST_ALPHA_PIPELINE, built, "Sampler0",
                        snapshot.fbo.getColorAttachmentView(), FilterMode.LINEAR, matrix);
            } finally {
                built.close();
            }
        }
    }

    /**
     * Copies a color region from an already-rendered framebuffer into
     * the snapshot FBO exactly once, without re-running the source
     * screen's render logic. Useful for freezing a live background as
     * a static preview thumbnail.
     *
     * <p>Source coordinates are in texel space with the GL bottom-left
     * origin convention, unchanged from the {@code glBlitFramebuffer}
     * version this replaces.
     *
     * <p>TODO(1.21.11): the old call scaled the source region up/down to
     * the snapshot size with GL_LINEAR. Its replacement,
     * {@code CommandEncoder.copyTextureToTexture}, hardcodes GL_NEAREST
     * and uses one width/height for both rectangles - it cannot scale at
     * all. So this now copies a 1:1 region, cropped to whichever of the
     * source region / snapshot is smaller. Restoring the scaling
     * behaviour means turning this into a full-screen-quad shader pass
     * sampling with a {@link FilterMode#LINEAR} sampler (same wave-B item
     * as Blur's downsample). No current caller uses this method, so the
     * crop is not observable today.
     */
    public static void copyRegionFromFramebuffer(Snapshot snapshot, Framebuffer source, int srcX, int srcY, int srcWidth, int srcHeight) {
        if (snapshot == null || snapshot.fbo == null || source == null || srcWidth <= 0 || srcHeight <= 0) {
            return;
        }

        BatchedRectangle.flushIfBatching();

        GpuTexture src = source.getColorAttachment();
        GpuTexture dst = snapshot.fbo.getColorAttachment();
        if (src == null || dst == null) {
            return;
        }

        // Clamp against both textures - copyTextureToTexture validates
        // bounds and throws rather than clipping.
        int width = Math.min(Math.min(srcWidth, snapshot.fbWidth), Math.max(0, src.getWidth(0) - srcX));
        int height = Math.min(Math.min(srcHeight, snapshot.fbHeight), Math.max(0, src.getHeight(0) - srcY));
        if (width <= 0 || height <= 0) {
            return;
        }

        // (src, dst, mipLevel, dstX, dstY, srcX, srcY, width, height)
        RenderSystem.getDevice().createCommandEncoder()
                .copyTextureToTexture(src, dst, 0, 0, 0, srcX, srcY, width, height);
        snapshot.populated = true;
    }
}
