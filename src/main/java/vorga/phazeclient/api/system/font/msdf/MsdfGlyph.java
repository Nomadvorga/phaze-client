package vorga.phazeclient.api.system.font.msdf;

import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

public final class MsdfGlyph {
    private final float minU;
    private final float maxU;
    private final float minV;
    private final float maxV;
    private final float advance;
    private final float topPosition;
    private final float width;
    private final float height;

    public MsdfGlyph(FontData.GlyphData data, float atlasWidth, float atlasHeight) {
        this.advance = data.advance();

        FontData.BoundsData atlasBounds = data.atlasBounds();
        if (atlasBounds != null) {
            this.minU = atlasBounds.left() / atlasWidth;
            this.maxU = atlasBounds.right() / atlasWidth;
            this.minV = 1.0F - atlasBounds.top() / atlasHeight;
            this.maxV = 1.0F - atlasBounds.bottom() / atlasHeight;
        } else {
            this.minU = this.maxU = this.minV = this.maxV = 0.0F;
        }

        FontData.BoundsData planeBounds = data.planeBounds();
        if (planeBounds != null) {
            this.width = planeBounds.right() - planeBounds.left();
            this.height = planeBounds.top() - planeBounds.bottom();
            this.topPosition = planeBounds.top();
        } else {
            this.width = this.height = this.topPosition = 0.0F;
        }
    }

    /**
     * Emits one glyph quad.
     *
     * <p>Takes a {@link BufferBuilder} rather than a plain
     * {@code VertexConsumer} because 1.21.11 has no loose shader uniforms:
     * the MSDF range / thickness / smoothness that used to be uniforms now
     * ride along as a per-vertex attribute, written through
     * {@link BufferBuilder#beginElement}. The values are identical on every
     * vertex, so the interpolated result is constant per fragment - exactly
     * what the uniform version provided.
     */
    public float apply(Matrix4f matrix, BufferBuilder consumer, float size, float x, float y, float z, int color,
                       VertexFormatElement paramsElement, float range, float thickness, float smoothness) {
        y -= this.topPosition * size;
        float scaledWidth = this.width * size;
        float scaledHeight = this.height * size;

        consumer.vertex(matrix, x, y, z).texture(this.minU, this.minV).color(color);
        writeParams(consumer, paramsElement, range, thickness, smoothness);
        consumer.vertex(matrix, x, y + scaledHeight, z).texture(this.minU, this.maxV).color(color);
        writeParams(consumer, paramsElement, range, thickness, smoothness);
        consumer.vertex(matrix, x + scaledWidth, y + scaledHeight, z).texture(this.maxU, this.maxV).color(color);
        writeParams(consumer, paramsElement, range, thickness, smoothness);
        consumer.vertex(matrix, x + scaledWidth, y, z).texture(this.maxU, this.minV).color(color);
        writeParams(consumer, paramsElement, range, thickness, smoothness);

        return this.advance * size;
    }

    private static void writeParams(BufferBuilder buffer, VertexFormatElement element,
                                    float range, float thickness, float smoothness) {
        long ptr = buffer.beginElement(element);
        MemoryUtil.memPutFloat(ptr, range);
        MemoryUtil.memPutFloat(ptr + 4L, thickness);
        MemoryUtil.memPutFloat(ptr + 8L, smoothness);
        MemoryUtil.memPutFloat(ptr + 12L, 0.0F);
    }

    public float getWidth(float size) {
        return this.advance * size;
    }
}
