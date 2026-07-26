package vorga.phazeclient.api.system.colorcorrection;

import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.minecraft.client.render.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

final class SodiumTintingVertexConsumer extends TintingVertexConsumer implements VertexBufferWriter {
    private final VertexBufferWriter writer;
    private final boolean colorTransform;
    private final boolean alphaTransform;

    private SodiumTintingVertexConsumer(
            VertexConsumer parent,
            VertexBufferWriter writer,
            WorldColorCorrectionController.Target target
    ) {
        super(parent, target);
        this.writer = writer;
        this.colorTransform = WorldColorCorrectionController.needsColorTransform(target);
        this.alphaTransform = WorldColorCorrectionController.needsAlphaTransform(target);
    }

    static VertexConsumer create(VertexConsumer parent, WorldColorCorrectionController.Target target) {
        VertexBufferWriter writer = VertexBufferWriter.tryOf(parent);
        return writer == null ? null : new SodiumTintingVertexConsumer(parent, writer, target);
    }

    @Override
    public void push(MemoryStack stack, long pointer, int count, VertexFormat format) {
        int colorOffset = format.getOffset(VertexFormatElement.COLOR);
        if ((!colorTransform && !alphaTransform) || colorOffset < 0) {
            writer.push(stack, pointer, count, format);
            return;
        }

        int stride = format.getVertexSizeByte();
        int bytes = Math.multiplyExact(stride, count);
        long transformed = stack.nmalloc(4, bytes);
        MemoryUtil.memCopy(pointer, transformed, bytes);

        for (int vertex = 0; vertex < count; vertex++) {
            long colorAddress = transformed + (long) vertex * stride + colorOffset;
            int red = MemoryUtil.memGetByte(colorAddress) & 0xFF;
            int green = MemoryUtil.memGetByte(colorAddress + 1L) & 0xFF;
            int blue = MemoryUtil.memGetByte(colorAddress + 2L) & 0xFF;
            int alpha = MemoryUtil.memGetByte(colorAddress + 3L) & 0xFF;

            int corrected = phaze$transformArgb(
                    (alpha << 24) | (red << 16) | (green << 8) | blue
            );
            red = (corrected >>> 16) & 0xFF;
            green = (corrected >>> 8) & 0xFF;
            blue = corrected & 0xFF;
            alpha = (corrected >>> 24) & 0xFF;

            MemoryUtil.memPutByte(colorAddress, (byte) red);
            MemoryUtil.memPutByte(colorAddress + 1L, (byte) green);
            MemoryUtil.memPutByte(colorAddress + 2L, (byte) blue);
            MemoryUtil.memPutByte(colorAddress + 3L, (byte) alpha);
        }

        writer.push(stack, transformed, count, format);
    }
}
