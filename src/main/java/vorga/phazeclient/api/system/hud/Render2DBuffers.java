package vorga.phazeclient.api.system.hud;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.BufferAllocator;

/** Owned 2D allocators: incomplete vertices cannot contaminate Minecraft's shared tessellator. */
public final class Render2DBuffers {
    private static BufferAllocator immediate, batch;

    private Render2DBuffers() {}

    public static BufferBuilder begin(VertexFormat format) {
        if (immediate == null) immediate = new BufferAllocator(4096);
        return new BufferBuilder(immediate, VertexFormat.DrawMode.QUADS, format);
    }

    public static BufferBuilder beginBatch(VertexFormat format) {
        if (batch == null) batch = new BufferAllocator(16 * 1024);
        return new BufferBuilder(batch, VertexFormat.DrawMode.QUADS, format);
    }

    public static void discardImmediate() { discard(immediate); }
    public static void discardBatch() { discard(batch); }

    private static void discard(BufferAllocator allocator) {
        if (allocator == null) return;
        // clear() alone does not rewind un-ended bytes when no CloseableBuffer exists yet.
        // Claim/release raw bytes without end(), which validates the incomplete vertex.
        try (var allocated = allocator.getAllocated()) {
            // Failed geometry is never submitted to the GPU.
        } finally {
            allocator.clear();
        }
    }

    public static void cleanup() {
        BufferAllocator oldImmediate = immediate, oldBatch = batch;
        immediate = batch = null;
        try {
            if (oldImmediate != null) oldImmediate.close();
        } finally {
            if (oldBatch != null) oldBatch.close();
        }
    }
}
