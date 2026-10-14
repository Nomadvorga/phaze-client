package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.ChunkPos;
import org.joml.Matrix4f;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.util.render.PhazeRenderLayers;

public class ChunkBorders extends Module {
    private static final ChunkBorders INSTANCE = new ChunkBorders();

    public final SelectSetting heightMode = new SelectSetting("Height Mode", "Vertical bounds mode")
            .value("Full World", "Player Level")
            .selected("Full World");

    public final ColorSetting borderColor = new ColorSetting("Border Color", "Outline color of chunk boundaries")
            .value(0xFFFF2222);

    public final BooleanSetting fillWalls = new BooleanSetting("Fill Walls", "Draw translucent colored walls along borders")
            .setValue(true);

    public final ColorSetting wallColor = new ColorSetting("Wall Color", "Color of the translucent wall fill")
            .value(0x33FF2222);

    public final ValueSetting wallOpacity = new ValueSetting("Wall Opacity", "Transparency of filled walls")
            .range(0.05f, 0.8f)
            .step(0.05f)
            .setValue(0.20f);

    public final BooleanSetting showSubChunks = new BooleanSetting("Sub-Chunks", "Draw 16x16 sub-chunk vertical sections")
            .setValue(true);

    public final ColorSetting subChunkColor = new ColorSetting("Sub-Chunk Color", "Color of sub-chunk section dividers")
            .value(0x88FFCC00);

    public final ValueSetting lineWidth = new ValueSetting("Line Width", "Thickness of chunk border lines")
            .range(1.0f, 4.0f)
            .step(0.5f)
            .setValue(2.0f);

    private ChunkBorders() {
        super("chunk_borders", "Chunk Borders", ModuleCategory.OTHER);
        setup(heightMode, borderColor, fillWalls, wallColor, wallOpacity, showSubChunks, subChunkColor, lineWidth);
    }

    public static ChunkBorders getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Lunar-style custom chunk boundaries with translucent walls and sub-chunk dividers";
    }

    @Override
    public String getIcon() {
        return "block_overlay.png";
    }

    public void renderCustomChunkBorders(MatrixStack matrices, VertexConsumerProvider vertexConsumers, double cameraX, double cameraY, double cameraZ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        ChunkPos chunkPos = client.player.getChunkPos();
        double minX = (chunkPos.x * 16) - cameraX;
        double minZ = (chunkPos.z * 16) - cameraZ;
        double maxX = minX + 16.0;
        double maxZ = minZ + 16.0;

        double minY, maxY;
        if ("Player Level".equals(heightMode.getSelected())) {
            double pY = client.player.getY() - cameraY;
            minY = Math.max(client.world.getBottomY() - cameraY, pY - 16.0);
            maxY = Math.min((client.world.getBottomY() + client.world.getHeight()) - cameraY, pY + 16.0);
        } else {
            minY = client.world.getBottomY() - cameraY;
            maxY = (client.world.getBottomY() + client.world.getHeight()) - cameraY;
        }

        Matrix4f posMatrix = matrices.peek().getPositionMatrix();

        // 1. Translucent Lunar-style Walls
        if (fillWalls.isValue()) {
            int wc = wallColor.getColor();
            float wr = ((wc >> 16) & 0xFF) / 255.0f;
            float wg = ((wc >> 8) & 0xFF) / 255.0f;
            float wb = (wc & 0xFF) / 255.0f;
            float wa = wallOpacity.getValue();

            VertexConsumer wallBuf = vertexConsumers.getBuffer(RenderLayer.getDebugQuads());

            // North Wall (Z = minZ)
            drawDoubleSidedQuad(posMatrix, wallBuf, (float) minX, (float) minY, (float) minZ, (float) maxX, (float) maxY, (float) minZ, wr, wg, wb, wa, true, false);
            // South Wall (Z = maxZ)
            drawDoubleSidedQuad(posMatrix, wallBuf, (float) minX, (float) minY, (float) maxZ, (float) maxX, (float) maxY, (float) maxZ, wr, wg, wb, wa, true, false);
            // West Wall (X = minX)
            drawDoubleSidedQuad(posMatrix, wallBuf, (float) minX, (float) minY, (float) minZ, (float) minX, (float) maxY, (float) maxZ, wr, wg, wb, wa, false, true);
            // East Wall (X = maxX)
            drawDoubleSidedQuad(posMatrix, wallBuf, (float) maxX, (float) minY, (float) minZ, (float) maxX, (float) maxY, (float) maxZ, wr, wg, wb, wa, false, true);
        }

        // 2. Corner Pillars and Outer Box Lines
        int bc = borderColor.getColor();
        float br = ((bc >> 16) & 0xFF) / 255.0f;
        float bg = ((bc >> 8) & 0xFF) / 255.0f;
        float bb = (bc & 0xFF) / 255.0f;
        float ba = ((bc >> 24) & 0xFF) / 255.0f;
        if (ba <= 0.01f) ba = 1.0f;

        float width = lineWidth.getValue();
        RenderLayer lineLayer = width > 1.0f ? PhazeRenderLayers.getThickLines(width) : RenderLayer.getLines();
        VertexConsumer lineConsumer = vertexConsumers.getBuffer(lineLayer);

        VertexRendering.drawBox(matrices, lineConsumer, minX, minY, minZ, maxX, maxY, maxZ, br, bg, bb, ba);

        // 3. Sub-chunk 16x16 horizontal dividers
        if (showSubChunks.isValue()) {
            int sc = subChunkColor.getColor();
            float sr = ((sc >> 16) & 0xFF) / 255.0f;
            float sg = ((sc >> 8) & 0xFF) / 255.0f;
            float sb = (sc & 0xFF) / 255.0f;
            float sa = ((sc >> 24) & 0xFF) / 255.0f;
            if (sa <= 0.01f) sa = 0.6f;

            int bottomY = client.world.getBottomY();
            int topY = bottomY + client.world.getHeight();

            for (int y = bottomY + 16; y < topY; y += 16) {
                double relY = y - cameraY;
                if (relY < minY || relY > maxY) continue;

                // Draw horizontal perimeter rectangle at sub-chunk boundary
                VertexRendering.drawBox(matrices, lineConsumer, minX, relY - 0.02, minZ, maxX, relY + 0.02, maxZ, sr, sg, sb, sa);
            }
        }
    }

    private static void drawDoubleSidedQuad(Matrix4f matrix, VertexConsumer buf,
                                            float x1, float y1, float z1,
                                            float x2, float y2, float z2,
                                            float r, float g, float b, float a,
                                            boolean isZPlane, boolean isXPlane) {
        if (isZPlane) {
            // Quad in X-Y at constant Z (DEBUG_QUADS has culling disabled, visible from both sides)
            buf.vertex(matrix, x1, y1, z1).color(r, g, b, a);
            buf.vertex(matrix, x2, y1, z1).color(r, g, b, a);
            buf.vertex(matrix, x2, y2, z1).color(r, g, b, a);
            buf.vertex(matrix, x1, y2, z1).color(r, g, b, a);
        } else if (isXPlane) {
            // Quad in Z-Y at constant X (DEBUG_QUADS has culling disabled, visible from both sides)
            buf.vertex(matrix, x1, y1, z1).color(r, g, b, a);
            buf.vertex(matrix, x1, y1, z2).color(r, g, b, a);
            buf.vertex(matrix, x1, y2, z2).color(r, g, b, a);
            buf.vertex(matrix, x1, y2, z1).color(r, g, b, a);
        }
    }
}
