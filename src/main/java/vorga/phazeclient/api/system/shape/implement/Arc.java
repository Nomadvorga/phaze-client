package vorga.phazeclient.api.system.shape.implement;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
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
import vorga.phazeclient.base.util.color.ColorUtil;

/**
 * Arc / ring segment.
 *
 * <h3>1.21.11 port</h3>
 *
 * The eight loose uniforms of the 1.21.4 version (size, location, radius,
 * thickness, start, end, color1, color2) became vertex attributes, since
 * the pipeline model no longer supports loose uniforms at all. The arc
 * maths in the fragment shader is unchanged.
 */
public class Arc implements Shape, QuickImports {

    /** Shared with {@link InvertedArc}, which needs the same two attributes. */
    static final VertexFormatElement ARC_RECT;
    static final VertexFormatElement ARC_PARAMS;
    private static final VertexFormatElement ARC_COLOR1;
    private static final VertexFormatElement ARC_COLOR2;
    private static final VertexFormat FORMAT;
    private static final RenderLayer LAYER;

    static {
        ARC_RECT = registerGeneric();
        ARC_PARAMS = registerGeneric();
        ARC_COLOR1 = registerGeneric();
        ARC_COLOR2 = registerGeneric();

        FORMAT = VertexFormat.builder()
                .add("Position", VertexFormatElement.POSITION)
                .add("ArcRect", ARC_RECT)
                .add("ArcParams", ARC_PARAMS)
                .add("ArcColor1", ARC_COLOR1)
                .add("ArcColor2", ARC_COLOR2)
                .build();

        RenderPipeline pipeline = RenderPipeline.builder()
                .withLocation(Identifier.of("phaze", "pipeline/arc"))
                .withVertexShader(Identifier.of("phaze", "core/arc"))
                .withFragmentShader(Identifier.of("phaze", "core/arc"))
                .withVertexFormat(FORMAT, VertexFormat.DrawMode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(true)
                .build();

        LAYER = RenderLayer.of(
                "phaze_arc",
                RenderSetup.builder(pipeline).translucent().build());
    }

    private static VertexFormatElement registerGeneric() {
        for (int id = 7; id < 32; id++) {
            if (VertexFormatElement.get(id) == null) {
                return VertexFormatElement.register(
                        id, 0, VertexFormatElement.ComponentType.FLOAT,
                        VertexFormatElement.Usage.GENERIC, 4);
            }
        }
        throw new IllegalStateException("No free VertexFormatElement slot for Arc");
    }

    private final Vector3f scratchPosition = new Vector3f();
    private final Vector3f scratchSize = new Vector3f();
    private final Vector4f scratchRound = new Vector4f();

    @Override
    public void render(ShapeProperties shape) {
        // Arc opens its own Tessellator BufferBuilder; the shared
        // Tessellator only allows ONE active buffer, so any pending
        // BatchedRectangle batch must flush first or its tessellator
        // .begin() would collide with ours.
        BatchedRectangle.flushIfBatching();

        if (window() == null) return;
        float scale = (float) window().getScaleFactor();
        float alpha = RenderSystem.getShaderColor()[3];

        Matrix4f matrix4f = shape.getMatrix().peek().getPositionMatrix();
        Vector3f pos = matrix4f.transformPosition(shape.getX(), shape.getY(), 0, scratchPosition).mul(scale);
        Vector3f size = matrix4f.getScale(scratchSize).mul(scale);
        Vector4f round = scratchRound.set(shape.getRound()).mul(size.y);

        float width = shape.getWidth() * size.x;
        float height = shape.getHeight() * size.y;

        float locX = pos.x;
        float locY = BatchedRectangle.getActiveFbHeight() - height - pos.y;

        int c1 = ColorUtil.multAlpha(shape.getColor().x, alpha);
        int c2 = ColorUtil.multAlpha(shape.getColor().y, alpha);

        float x = shape.getX();
        float y = shape.getY();
        float w = shape.getWidth();
        float h = shape.getHeight();

        BufferBuilder buffer = tessellator().begin(VertexFormat.DrawMode.QUADS, FORMAT);
        emit(buffer, matrix4f, x, y, locX, locY, width, height, round.x, shape, c1, c2);
        emit(buffer, matrix4f, x, y + h, locX, locY, width, height, round.x, shape, c1, c2);
        emit(buffer, matrix4f, x + w, y + h, locX, locY, width, height, round.x, shape, c1, c2);
        emit(buffer, matrix4f, x + w, y, locX, locY, width, height, round.x, shape, c1, c2);

        LAYER.draw(buffer.end());
    }

    private static void emit(BufferBuilder buffer, Matrix4f matrix,
                             float x, float y,
                             float locX, float locY, float sizeX, float sizeY,
                             float radius, ShapeProperties shape, int color1, int color2) {
        buffer.vertex(matrix, x, y, 0.0F);

        long ptr = buffer.beginElement(ARC_RECT);
        MemoryUtil.memPutFloat(ptr, locX);
        MemoryUtil.memPutFloat(ptr + 4L, locY);
        MemoryUtil.memPutFloat(ptr + 8L, sizeX);
        MemoryUtil.memPutFloat(ptr + 12L, sizeY);

        ptr = buffer.beginElement(ARC_PARAMS);
        MemoryUtil.memPutFloat(ptr, radius);
        MemoryUtil.memPutFloat(ptr + 4L, shape.getThickness());
        MemoryUtil.memPutFloat(ptr + 8L, shape.getStart());
        MemoryUtil.memPutFloat(ptr + 12L, shape.getEnd());

        ptr = buffer.beginElement(ARC_COLOR1);
        putColor(ptr, color1);

        ptr = buffer.beginElement(ARC_COLOR2);
        putColor(ptr, color2);
    }

    private static void putColor(long ptr, int color) {
        MemoryUtil.memPutFloat(ptr, ColorUtil.redf(color));
        MemoryUtil.memPutFloat(ptr + 4L, ColorUtil.greenf(color));
        MemoryUtil.memPutFloat(ptr + 8L, ColorUtil.bluef(color));
        MemoryUtil.memPutFloat(ptr + 12L, ColorUtil.alphaf(color));
    }
}
