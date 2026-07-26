package vorga.phazeclient.api.system.nametag;

import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

public class NametagVBOManager {
    private static final Map<String, VBOEntry> vboCache = new HashMap<>();
    private static final int CACHE_SIZE = 64;

    public static class VBOEntry {
        public final int vboId;
        public final int vertexCount;
        public final long timestamp;

        public VBOEntry(int vboId, int vertexCount) {
            this.vboId = vboId;
            this.vertexCount = vertexCount;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static int createVBO(float[] vertices) {
        String key = generateKey(vertices);
        VBOEntry entry = vboCache.get(key);
        if (entry != null) {
            return entry.vboId;
        }

        int vboId = GL30.glGenBuffers();
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vboId);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(vertices.length);
            buffer.put(vertices);
            buffer.flip();
            GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buffer, GL30.GL_STATIC_DRAW);
        }

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);

        if (vboCache.size() > CACHE_SIZE) {
            // Remove oldest entry
            vboCache.entrySet().removeIf(e -> 
                (System.currentTimeMillis() - e.getValue().timestamp) > 60000);
        }

        vboCache.put(key, new VBOEntry(vboId, vertices.length / 4)); // 4 floats per vertex
        return vboId;
    }

    private static String generateKey(float[] vertices) {
        StringBuilder sb = new StringBuilder();
        for (float v : vertices) {
            sb.append(v).append(",");
        }
        return sb.toString();
    }

    public static void deleteVBO(int vboId) {
        GL30.glDeleteBuffers(vboId);
        vboCache.entrySet().removeIf(e -> e.getValue().vboId == vboId);
    }

    public static void clear() {
        for (VBOEntry entry : vboCache.values()) {
            GL30.glDeleteBuffers(entry.vboId);
        }
        vboCache.clear();
    }
}
