package vorga.phazeclient.api.system.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.VertexSorter;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import vorga.phazeclient.implement.features.modules.other.*;
import vorga.phazeclient.mixins.OverlayLayerAccessor;
import vorga.phazeclient.mixins.OverlayLayerParametersAccessor;
import vorga.phazeclient.mixins.OverlayTextureAccessor;

import java.util.LinkedHashMap;
import java.util.Map;

/** Client geometry is buffered separately, then drawn after the world's framebuffer passes. */
public final class WorldOverlayRenderer {
    private static final Map<Key, Batch> BATCHES = new LinkedHashMap<>();
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static boolean recording;
    private static long frame;
    private static Vec3d camera;
    private static RenderTickCounter tickCounter;

    private WorldOverlayRenderer() {}

    public static void beginFrame(Matrix4f view, Matrix4f projection, Camera worldCamera, RenderTickCounter counter) {
        for (var iterator = BATCHES.entrySet().iterator(); iterator.hasNext();) {
            Batch batch = iterator.next().getValue();
            batch.discard();
            if (frame - batch.lastUsed > 300) {
                batch.allocator.close();
                iterator.remove();
            }
        }
        frame++;
        VIEW.set(view);
        PROJECTION.set(projection);
        camera = worldCamera.getPos();
        tickCounter = counter;
        recording = true;
    }

    public static boolean isRecording() { return recording && !IrisCompat.isShadowPass(); }

    /** Render-thread lifecycle boundary: release native buffers, not just their contents. */
    public static void clear() {
        recording = false;
        camera = null;
        tickCounter = null;
        Throwable failure = null;
        try {
            for (Batch batch : BATCHES.values()) {
                try {
                    try {
                        batch.discard();
                    } finally {
                        batch.allocator.close();
                    }
                } catch (RuntimeException | Error cleanupFailure) {
                    if (failure == null) failure = cleanupFailure;
                    else failure.addSuppressed(cleanupFailure);
                }
            }
        } finally {
            BATCHES.clear();
        }
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
    }

    public static BufferBuilder buffer(VertexFormat.DrawMode mode, VertexFormat format, float width, boolean depth) {
        return buffer(new Key(mode, format, null, width, depth));
    }

    private static BufferBuilder buffer(Key key) {
        Batch batch = BATCHES.computeIfAbsent(key, ignored -> new Batch());
        batch.lastUsed = frame;
        if (batch.builder == null) batch.builder = new BufferBuilder(batch.allocator, key.mode, key.format);
        return batch.builder;
    }

    /** Compatibility adapter for vanilla model/font APIs, not the world's entity provider.
     * RenderLayer is used only for layout/texture metadata; its draw phases are never run. */
    public static final VertexConsumerProvider CONSUMERS = layer -> {
        RenderLayer.MultiPhaseParameters phases = ((OverlayLayerAccessor) layer).phaze$getPhases();
        Identifier texture = ((OverlayTextureAccessor) ((OverlayLayerParametersAccessor) (Object) phases)
                .phaze$getTexture()).phaze$getId().orElse(null);
        boolean depth = !layer.toString().contains("see_through");
        return buffer(new Key(layer.getDrawMode(), layer.getVertexFormat(), texture, 1, depth));
    };

    public static VertexConsumerProvider consumers(VertexConsumerProvider vanilla) {
        return isRecording() ? CONSUMERS : vanilla;
    }

    /** Same position as soup-visuals: after WorldRenderer returns, before the hand pass. */
    public static void draw() {
        if (!recording) return;
        MinecraftClient client = MinecraftClient.getInstance();
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean polygon = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        float polygonFactor = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR);
        float polygonUnits = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        ProjectionType projectionType = RenderSystem.getProjectionType();
        int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int targetRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int targetAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        float lineWidth = RenderSystem.getShaderLineWidth();
        float[] color = RenderSystem.getShaderColor().clone();
        Fog fog = RenderSystem.getShaderFog();
        ShaderProgram shader = RenderSystem.getShader();
        int texture = RenderSystem.getShaderTexture(0);
        RenderSystem.getModelViewStack().pushMatrix();
        try {
            // WorldRenderer/Iris has finished compositing clouds, water and shaderpack targets.
            client.getFramebuffer().beginWrite(false);
            // Vanilla entity/debug matrices contain local transforms only: the camera
            // rotation lives in ModelViewMat. Keep the same contract for queued
            // hitboxes/ranges AND late helper geometry, without applying VIEW twice.
            RenderSystem.getModelViewStack().set(VIEW);
            RenderSystem.setProjectionMatrix(PROJECTION, ProjectionType.PERSPECTIVE);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShaderFog(Fog.DUMMY);
            RenderSystem.disablePolygonOffset();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            MatrixStack matrices = new MatrixStack();
            FTHelperRenderer.renderHighlight(matrices, camera, tickCounter);
            FTHelperRenderer.renderSnowballs(matrices, camera, tickCounter);
            HolyWorldHelperRenderer.render(matrices, camera, tickCounter);
            PredictionsRenderer.render(matrices, camera, tickCounter);
            vorga.phazeclient.implement.waypoints.WaypointRenderer.render(matrices, camera);
            flush();
        } finally {
            recording = false;
            for (Batch batch : BATCHES.values()) batch.discard();
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.setProjectionMatrix(projection, projectionType);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            RenderSystem.setShader(shader);
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShaderFog(fog);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.lineWidth(lineWidth);
            RenderSystem.depthMask(depthMask);
            RenderSystem.depthFunc(depthFunction);
            RenderSystem.polygonOffset(polygonFactor, polygonUnits);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (polygon) RenderSystem.enablePolygonOffset(); else RenderSystem.disablePolygonOffset();
            RenderSystem.blendFuncSeparate(sourceRgb, targetRgb, sourceAlpha, targetAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        }
    }

    public static void flush() {
        // Cached insertion order changes when Fill is enabled after the outline.
        // Always draw translucent surfaces first so they cannot wash out the edges.
        flush(false);
        flush(true);
    }

    private static void flush(boolean lines) {
        for (var entry : BATCHES.entrySet()) {
            Key key = entry.getKey();
            Batch batch = entry.getValue();
            if (key.isLines() != lines || batch.builder == null) continue;
            try (BuiltBuffer built = batch.end(key)) {
                if (built == null) continue;
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableCull();
                if (key.depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.lineWidth(key.width);
                RenderSystem.setShaderColor(1, 1, 1, 1);
                RenderSystem.setShaderFog(Fog.DUMMY);
                if (key.depth && !lines && key.format == VertexFormats.POSITION_COLOR) {
                    // Replace the old RenderLayer view bias without executing its
                    // Iris phases. Ground-aligned fills must not z-fight with terrain.
                    RenderSystem.polygonOffset(-1, -1);
                    RenderSystem.enablePolygonOffset();
                } else {
                    RenderSystem.disablePolygonOffset();
                }
                if (key.texture != null) {
                    RenderSystem.setShaderTexture(0, key.texture);
                    // Shaders bind attributes by their own layout: font glyphs are Position, Color, UV0,
                    // and the POSITION_TEX_COLOR program would read their texture coordinates as colour.
                    RenderSystem.setShader(key.format == VertexFormats.POSITION_COLOR_TEXTURE_LIGHT
                            ? ShaderProgramKeys.RENDERTYPE_TEXT_SEE_THROUGH : ShaderProgramKeys.POSITION_TEX_COLOR);
                } else {
                    RenderSystem.setShader(key.format == VertexFormats.LINES
                            ? ShaderProgramKeys.RENDERTYPE_LINES : ShaderProgramKeys.POSITION_COLOR);
                }
                BufferRenderer.drawWithGlobalProgram(built);
            }
        }
    }

    private record Key(VertexFormat.DrawMode mode, VertexFormat format, Identifier texture, float width, boolean depth) {
        private boolean isLines() {
            return mode == VertexFormat.DrawMode.LINES || mode == VertexFormat.DrawMode.LINE_STRIP
                    || mode == VertexFormat.DrawMode.DEBUG_LINES || mode == VertexFormat.DrawMode.DEBUG_LINE_STRIP;
        }
    }

    private static final class Batch {
        private final BufferAllocator allocator = new BufferAllocator(4096);
        private BufferBuilder builder;
        private long lastUsed;

        private BuiltBuffer end(Key key) {
            BuiltBuffer built = builder.endNullable();
            builder = null;
            try {
                if (built != null && key.mode == VertexFormat.DrawMode.QUADS && key.format == VertexFormats.POSITION_COLOR) {
                    // Vertices are camera-relative: sort translucent box faces back
                    // to front, including boxes belonging to different entities.
                    built.sortQuads(allocator, VertexSorter.BY_DISTANCE);
                }
                return built;
            } catch (RuntimeException | Error failure) {
                if (built != null) built.close();
                throw failure;
            }
        }

        private void discard() {
            if (builder != null) {
                BuiltBuffer built = builder.endNullable();
                if (built != null) built.close();
                builder = null;
            }
            allocator.clear();
        }
    }
}
