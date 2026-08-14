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

/**
 * Inverting arc / ring segment.
 *
 * <h3>1.21.11 port</h3>
 *
 * Same treatment as {@link Arc} - the six loose uniforms became vertex
 * attributes - but with no colors (the shader emits white and the
 * inverting blend does the rest) and with that blend baked into the
 * pipeline as {@link BlendFunction#INVERT} instead of being configured
 * imperatively around the draw.
 *
 * <p>Reuses {@link Arc}'s {@code ArcRect} / {@code ArcParams} elements
 * rather than registering its own: element ids are a shared 32-slot
 * registry, and the two shapes want byte-identical attributes.
 */
public class InvertedArc implements Shape, QuickImports {

    private static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("ArcRect", Arc.ARC_RECT)
            .add("ArcParams", Arc.ARC_PARAMS)
            .build();

    private static final RenderLayer LAYER;

    static {
        RenderPipeline pipeline = RenderPipeline.builder()
                .withLocation(Identifier.of("phaze", "pipeline/arc_inverted"))
                .withVertexShader(Identifier.of("phaze", "core/arc_inverted"))
                .withFragmentShader(Identifier.of("phaze", "core/arc_inverted"))
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(FORMAT, VertexFormat.DrawMode.QUADS)
                .withBlend(BlendFunction.INVERT)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(true)
                .build();

        LAYER = RenderLayer.of(
                "phaze_arc_inverted",
                RenderSetup.builder(pipeline).translucent().build());
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

        float width = shape.getWidth() * size.x;
        float height = shape.getHeight() * size.y;

        float locX = pos.x;
        float locY = BatchedRectangle.getActiveFbHeight() - height - pos.y;

        float x = shape.getX();
        float y = shape.getY();
        float w = shape.getWidth();
        float h = shape.getHeight();

        BufferBuilder buffer = tessellator().begin(VertexFormat.DrawMode.QUADS, FORMAT);
        emit(buffer, matrix4f, x, y, locX, locY, width, height, round.x, shape);
        emit(buffer, matrix4f, x, y + h, locX, locY, width, height, round.x, shape);
        emit(buffer, matrix4f, x + w, y + h, locX, locY, width, height, round.x, shape);
        emit(buffer, matrix4f, x + w, y, locX, locY, width, height, round.x, shape);

        // 1.21.11 defers DrawContext work into a GuiRenderState, so this
        // immediate draw runs outside the GUI pass and must install the
        // GUI ortho projection (and its z = -11000 model-view) itself.
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
                             float radius, ShapeProperties shape) {
        buffer.vertex(matrix, x, y, 0.0F);

        long ptr = buffer.beginElement(Arc.ARC_RECT);
        MemoryUtil.memPutFloat(ptr, locX);
        MemoryUtil.memPutFloat(ptr + 4L, locY);
        MemoryUtil.memPutFloat(ptr + 8L, sizeX);
        MemoryUtil.memPutFloat(ptr + 12L, sizeY);

        ptr = buffer.beginElement(Arc.ARC_PARAMS);
        MemoryUtil.memPutFloat(ptr, radius);
        MemoryUtil.memPutFloat(ptr + 4L, shape.getThickness());
        MemoryUtil.memPutFloat(ptr + 8L, shape.getStart());
        MemoryUtil.memPutFloat(ptr + 12L, shape.getEnd());
    }
}
