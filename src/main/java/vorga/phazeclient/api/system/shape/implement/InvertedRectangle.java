package vorga.phazeclient.api.system.shape.implement;

import vorga.phazeclient.base.util.render.GuiMatrix;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import vorga.phazeclient.api.system.shape.Shape;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.base.QuickImports;
import net.minecraft.client.gl.UniformType;

public class InvertedRectangle implements Shape, QuickImports {

    private static final VertexFormatElement INV_RECT;
    private static final VertexFormatElement INV_RADIUS;
    private static final VertexFormatElement INV_PARAMS;
    private static final VertexFormat FORMAT;
    private static final RenderLayer LAYER;

    static {

        INV_RECT = registerGeneric(4);
        INV_RADIUS = registerGeneric(4);
        INV_PARAMS = registerGeneric(2);

        FORMAT = VertexFormat.builder()
                .add("Position", VertexFormatElement.POSITION)
                .add("InvRect", INV_RECT)
                .add("InvRadius", INV_RADIUS)
                .add("InvParams", INV_PARAMS)
                .build();

        RenderPipeline pipeline = RenderPipeline.builder()
                .withLocation(Identifier.of("phaze", "pipeline/round_inverted"))
                .withVertexShader(Identifier.of("phaze", "core/round_inverted"))
                .withFragmentShader(Identifier.of("phaze", "core/round_inverted"))
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(FORMAT, VertexFormat.DrawMode.QUADS)
                .withBlend(BlendFunction.INVERT)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(true)
                .build();

        LAYER = RenderLayer.of(
                "phaze_round_inverted",
                RenderSetup.builder(pipeline).translucent().build());
    }

    private static VertexFormatElement registerGeneric(int count) {
        for (int id = 7; id < 32; id++) {
            if (VertexFormatElement.byId(id) == null) {
                return VertexFormatElement.register(
                        id, 0, VertexFormatElement.Type.FLOAT,
                        VertexFormatElement.Usage.GENERIC, count);
            }
        }
        throw new IllegalStateException("No free VertexFormatElement slot for InvertedRectangle");
    }

    private final Vector3f scratchPosition = new Vector3f();
    private final Vector3f scratchSize = new Vector3f();
    private final Vector4f scratchRound = new Vector4f();

    @Override
    public void render(ShapeProperties shape) {
        BatchedRectangle.flushIfBatching();

        if (window() == null) return;
        float scale = (float) window().getScaleFactor();

        Matrix4f matrix4f = GuiMatrix.mat4(shape.getMatrix());
        Vector3f pos = matrix4f.transformPosition(shape.getX(), shape.getY(), 0, scratchPosition).mul(scale);
        Vector3f size = matrix4f.getScale(scratchSize).mul(scale);
        Vector4f round = scratchRound.set(shape.getRound()).mul(size.y);

        float softness = shape.getSoftness();
        float width = shape.getWidth() * size.x;
        float height = shape.getHeight() * size.y;

        float locX = pos.x;
        float locY = BatchedRectangle.getActiveFbHeight() - height - pos.y;

        float x0 = shape.getX() - softness / 2.0F;
        float y0 = shape.getY() - softness / 2.0F;
        float w = shape.getWidth() + softness;
        float h = shape.getHeight() + softness;

        BufferBuilder buffer = tessellator().begin(VertexFormat.DrawMode.QUADS, FORMAT);
        emit(buffer, matrix4f, x0, y0, locX, locY, width, height, round, softness);
        emit(buffer, matrix4f, x0, y0 + h, locX, locY, width, height, round, softness);
        emit(buffer, matrix4f, x0 + w, y0 + h, locX, locY, width, height, round, softness);
        emit(buffer, matrix4f, x0 + w, y0, locX, locY, width, height, round, softness);

        vorga.phazeclient.api.system.draw.GuiProjection.begin();
        try {
            LAYER.draw(buffer.end());
        } finally {
            vorga.phazeclient.api.system.draw.GuiProjection.end();
        }
    }

    private static void emit(BufferBuilder buffer, Matrix4f matrix,
                             float x, float y,
                             float locX, float locY, float sizeX, float sizeY,
                             Vector4f radius, float softness) {
        buffer.vertex(matrix, x, y, 0.0F);

        long ptr = buffer.beginElement(INV_RECT);
        MemoryUtil.memPutFloat(ptr, locX);
        MemoryUtil.memPutFloat(ptr + 4L, locY);
        MemoryUtil.memPutFloat(ptr + 8L, sizeX);
        MemoryUtil.memPutFloat(ptr + 12L, sizeY);

        ptr = buffer.beginElement(INV_RADIUS);
        MemoryUtil.memPutFloat(ptr, radius.x);
        MemoryUtil.memPutFloat(ptr + 4L, radius.y);
        MemoryUtil.memPutFloat(ptr + 8L, radius.z);
        MemoryUtil.memPutFloat(ptr + 12L, radius.w);

        ptr = buffer.beginElement(INV_PARAMS);
        MemoryUtil.memPutFloat(ptr, softness);
        MemoryUtil.memPutFloat(ptr + 4L, 0.0F);
    }
}
