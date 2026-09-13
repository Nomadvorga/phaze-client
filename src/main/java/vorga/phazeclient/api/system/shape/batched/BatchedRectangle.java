package vorga.phazeclient.api.system.shape.batched;

import vorga.phazeclient.base.util.render.GuiMatrix;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4i;
import org.lwjgl.system.MemoryUtil;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.color.ColorUtil;
import net.minecraft.client.gl.UniformType;

public final class BatchedRectangle {

    public static final VertexFormatElement RECT_BASE;
    public static final VertexFormatElement RECT_SIZE;
    public static final VertexFormatElement RECT_RADIUS;
    public static final VertexFormatElement RECT_PARAMS;
    public static final VertexFormatElement RECT_OUTLINE;

    public static final VertexFormat BATCHED_FORMAT;

    public static final RenderLayer BATCHED_LAYER;

    private static final boolean DISABLED;

    static {
        VertexFormatElement base = null, size = null, radius = null, params = null, outline = null;
        VertexFormat format = null;
        RenderLayer layer = null;
        boolean disabled = true;
        try {
            int id = findFreeSlot(7);
            base = VertexFormatElement.register(id, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 2);
            id = findFreeSlot(id + 1);
            size = VertexFormatElement.register(id, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 2);
            id = findFreeSlot(id + 1);
            radius = VertexFormatElement.register(id, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 4);
            id = findFreeSlot(id + 1);
            params = VertexFormatElement.register(id, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 2);
            id = findFreeSlot(id + 1);
            outline = VertexFormatElement.register(id, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 4);

            format = VertexFormat.builder()
                    .add("Position", VertexFormatElement.POSITION)
                    .add("Color", VertexFormatElement.COLOR)
                    .add("RectBase", base)
                    .add("RectSize", size)
                    .add("Radius", radius)
                    .add("Params", params)
                    .add("OutlineColor", outline)
                    .build();

            RenderPipeline pipeline = RenderPipeline.builder()
                    .withLocation(Identifier.of("phaze", "pipeline/round_batched"))
                    .withVertexShader(Identifier.of("phaze", "core/round_batched"))
                    .withFragmentShader(Identifier.of("phaze", "core/round_batched"))
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    .withVertexFormat(format, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(true)
                    .build();

            layer = RenderLayer.of(
                    "phaze_round_batched",
                    RenderSetup.builder(pipeline).translucent().build());
            disabled = false;
        } catch (Throwable t) {

            System.err.println("[Phaze/BatchedRectangle] Disabled (VertexFormatElement allocation failed): " + t);
        }
        RECT_BASE = base;
        RECT_SIZE = size;
        RECT_RADIUS = radius;
        RECT_PARAMS = params;
        RECT_OUTLINE = outline;
        BATCHED_FORMAT = format;
        BATCHED_LAYER = layer;
        DISABLED = disabled;
    }

    private static int findFreeSlot(int startFrom) {

        for (int id = startFrom; id < 32; id++) {
            if (VertexFormatElement.byId(id) == null) {
                return id;
            }
        }
        throw new IllegalStateException("No free VertexFormatElement slot >= " + startFrom);
    }

    private static int scopeDepth = 0;
    private static BufferBuilder activeBuilder = null;
    private static int pendingRects = 0;
    private static final Vector3f SCRATCH_BASE_POS = new Vector3f();
    private static final Vector3f SCRATCH_SIZE = new Vector3f();
    private static final Vector4f SCRATCH_RADIUS = new Vector4f();

    private static float renderTargetFbHeightOverride = -1.0F;

    private BatchedRectangle() {
    }

    public static void beginScope() {

        if (DISABLED) {
            return;
        }
        scopeDepth++;
    }

    public static void endScope() {
        if (DISABLED || scopeDepth <= 0) {
            return;
        }
        if (--scopeDepth == 0) {
            flush();
        }
    }

    public static boolean isBatching() {
        return !DISABLED && scopeDepth > 0;
    }

    public static void flushIfBatching() {
        if (isBatching()) {
            flush();
        }
    }

    public static void setRenderTargetFbHeight(float fbHeight) {
        flushIfBatching();
        renderTargetFbHeightOverride = fbHeight;
    }

    public static void clearRenderTargetFbHeight() {
        flushIfBatching();
        renderTargetFbHeightOverride = -1.0F;
    }

    public static float getActiveFbHeight() {
        if (renderTargetFbHeightOverride > 0.0F) {
            return renderTargetFbHeightOverride;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return 0.0F;
        }
        return mc.getWindow().getFramebufferHeight();
    }

    public static void flush() {
        BufferBuilder builder = activeBuilder;
        int rects = pendingRects;
        activeBuilder = null;
        pendingRects = 0;
        if (builder == null || rects == 0) {
            return;
        }

        BuiltBuffer built = builder.endNullable();
        if (built == null) {
            return;
        }

        vorga.phazeclient.api.system.draw.GuiProjection.begin();
        try {

            BATCHED_LAYER.draw(built);
        } finally {
            vorga.phazeclient.api.system.draw.GuiProjection.end();
        }
    }

    public static void submit(ShapeProperties shape) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return;
        }

        float scale = (float) mc.getWindow().getScaleFactor();

        float windowHeight = renderTargetFbHeightOverride > 0.0F
                ? renderTargetFbHeightOverride
                : mc.getWindow().getFramebufferHeight();
        float globalAlpha = vorga.phazeclient.api.system.draw.PhazeAlpha.get();

        Matrix4f matrix = GuiMatrix.mat4(shape.getMatrix());
        Vector3f basePos = matrix.transformPosition(shape.getX(), shape.getY(), 0, SCRATCH_BASE_POS).mul(scale);
        Vector3f sizeVec = matrix.getScale(SCRATCH_SIZE).mul(scale);

        float scaledWidth = shape.getWidth() * sizeVec.x;
        float scaledHeight = shape.getHeight() * sizeVec.y;

        float baseX = basePos.x;
        float baseY = windowHeight - scaledHeight - basePos.y;

        Vector4f scaledRadius = SCRATCH_RADIUS.set(shape.getRound()).mul(sizeVec.y);
        float softness = shape.getSoftness();
        float thickness = shape.getThickness();

        Vector4i colors = shape.getColor();

        int color1 = ColorUtil.multAlpha(colors.x, globalAlpha);
        int color2 = ColorUtil.multAlpha(colors.y, globalAlpha);
        int color3 = ColorUtil.multAlpha(colors.z, globalAlpha);
        int color4 = ColorUtil.multAlpha(colors.w, globalAlpha);
        int outlineColor = ColorUtil.multAlpha(shape.getOutlineColor(), globalAlpha);

        if (activeBuilder == null) {
            activeBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, BATCHED_FORMAT);
        }

        float x0 = shape.getX() - softness / 2.0F;
        float y0 = shape.getY() - softness / 2.0F;
        float w = shape.getWidth() + softness;
        float h = shape.getHeight() + softness;

        emit(activeBuilder, matrix, x0, y0, color2,
                baseX, baseY, scaledWidth, scaledHeight, scaledRadius, thickness, softness, outlineColor);
        emit(activeBuilder, matrix, x0, y0 + h, color1,
                baseX, baseY, scaledWidth, scaledHeight, scaledRadius, thickness, softness, outlineColor);
        emit(activeBuilder, matrix, x0 + w, y0 + h, color3,
                baseX, baseY, scaledWidth, scaledHeight, scaledRadius, thickness, softness, outlineColor);
        emit(activeBuilder, matrix, x0 + w, y0, color4,
                baseX, baseY, scaledWidth, scaledHeight, scaledRadius, thickness, softness, outlineColor);

        pendingRects++;

        if (!isBatching()) {
            flush();
        }
    }

    private static void emit(BufferBuilder buffer, Matrix4f matrix,
                             float x, float y, int color,
                             float baseX, float baseY, float sizeX, float sizeY,
                             Vector4f radius, float thickness, float softness,
                             int outlineColor) {
        buffer.vertex(matrix, x, y, 0.0F).color(color);

        long ptr = buffer.beginElement(RECT_BASE);
        MemoryUtil.memPutFloat(ptr, baseX);
        MemoryUtil.memPutFloat(ptr + 4L, baseY);

        ptr = buffer.beginElement(RECT_SIZE);
        MemoryUtil.memPutFloat(ptr, sizeX);
        MemoryUtil.memPutFloat(ptr + 4L, sizeY);

        ptr = buffer.beginElement(RECT_RADIUS);
        MemoryUtil.memPutFloat(ptr, radius.x);
        MemoryUtil.memPutFloat(ptr + 4L, radius.y);
        MemoryUtil.memPutFloat(ptr + 8L, radius.z);
        MemoryUtil.memPutFloat(ptr + 12L, radius.w);

        ptr = buffer.beginElement(RECT_PARAMS);
        MemoryUtil.memPutFloat(ptr, thickness);
        MemoryUtil.memPutFloat(ptr + 4L, softness);

        ptr = buffer.beginElement(RECT_OUTLINE);
        MemoryUtil.memPutFloat(ptr, ColorUtil.redf(outlineColor));
        MemoryUtil.memPutFloat(ptr + 4L, ColorUtil.greenf(outlineColor));
        MemoryUtil.memPutFloat(ptr + 8L, ColorUtil.bluef(outlineColor));
        MemoryUtil.memPutFloat(ptr + 12L, ColorUtil.alphaf(outlineColor));
    }
}
