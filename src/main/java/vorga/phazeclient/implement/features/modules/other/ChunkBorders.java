package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.ChunkPos;
import org.joml.Matrix4f;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public class ChunkBorders extends Module {
    private static final ChunkBorders INSTANCE = new ChunkBorders();

    // 1. Grid Category (matches media_1791988561884_3f54c295.png)
    public final ValueSetting gridSize = new ValueSetting("Grid Size", "Size of grid cells in blocks (1x1 to 5x5)")
            .range(1.0f, 5.0f).step(1.0f).setValue(1.0f);
    public final ValueSetting gridThickness = new ValueSetting("Line Thickness", "Thickness of chunk grid lines")
            .range(1.0f, 5.0f).step(1.0f).setValue(1.0f);
    public final ColorSetting gridColor = new ColorSetting("Grid Color", "Color of the chunk grid lines")
            .value(0xFFFFFF00); // Vanilla default yellow (#ffffff00)

    public final SectionSetting gridGroup = new SectionSetting("Grid");
    public final BooleanSetting gridEnabled = new BooleanSetting("Grid", "Enable chunk grid lines").setValue(true);

    // 2. Inner Corners Category (matches media_1791988671896_21c52917.png)
    public final ValueSetting innerThickness = new ValueSetting("Line Thickness", "Thickness of inner chunk corner lines")
            .range(1.0f, 5.0f).step(1.0f).setValue(1.0f);
    public final ColorSetting innerColor = new ColorSetting("Inner Chunk Corner Color", "Color of inner chunk corners and sub-chunk frames")
            .value(0xFF0000FF); // Blue (#ff0000ff)

    public final SectionSetting innerCornersGroup = new SectionSetting("Inner Corners");
    public final BooleanSetting innerEnabled = new BooleanSetting("Inner Corners", "Enable inner chunk corners").setValue(true);

    // 3. Outer Corners Category (matches media_1791988743968_84b415d0.png)
    public final ValueSetting outerThickness = new ValueSetting("Line Thickness", "Thickness of outer chunk corner lines")
            .range(1.0f, 5.0f).step(1.0f).setValue(5.0f);
    public final ColorSetting outerColor = new ColorSetting("Outer Chunk Corner Color", "Color of outer neighboring chunk corners")
            .value(0xFFFF0000); // Red (#ffff0000)

    public final SectionSetting outerCornersGroup = new SectionSetting("Outer Corners");
    public final BooleanSetting outerEnabled = new BooleanSetting("Outer Corners", "Enable outer chunk corners").setValue(true);

    private ChunkBorders() {
        super("chunk_borders", "Toggle Chunk Borders", ModuleCategory.OTHER);
        innerColor.setFullWidth(true);
        outerColor.setFullWidth(true);
        setup(
                gridGroup, gridEnabled, gridSize, gridThickness, gridColor,
                innerCornersGroup, innerEnabled, innerThickness, innerColor,
                outerCornersGroup, outerEnabled, outerThickness, outerColor
        );
    }

    public static ChunkBorders getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Customizable Lunar-style chunk boundaries with grid, inner corners, and outer corners";
    }

    @Override
    public String getIcon() {
        return "block_overlay.png";
    }

    public void renderCustomChunkBorders(MatrixStack matrices, VertexConsumerProvider vertexConsumers, double cameraX, double cameraY, double cameraZ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        try {
            Entity cameraEntity = client.getCameraEntity();
            if (cameraEntity == null) cameraEntity = client.player;

            float minY = (float) (client.world.getBottomY() - cameraY);
            float maxY = (float) ((client.world.getTopYInclusive() + 1) - cameraY);

            ChunkPos chunkPos = cameraEntity.getChunkPos();
            float minBlockX = (float) (chunkPos.getStartX() - cameraX);
            float minBlockZ = (float) (chunkPos.getStartZ() - cameraZ);

            Matrix4f posMatrix = matrices.peek().getPositionMatrix();

            // 1. Outer Corners (red vertical lines at 3x3 chunk boundaries around the player)
            if (outerEnabled.isValue()) {
                double thick = (double) outerThickness.getValue();
                VertexConsumer buf = vertexConsumers.getBuffer(RenderLayer.getDebugLineStrip(thick));

                int col = outerColor.getColor();
                float r = ((col >> 16) & 0xFF) / 255.0f;
                float g = ((col >> 8) & 0xFF) / 255.0f;
                float b = (col & 0xFF) / 255.0f;
                float a = ((col >> 24) & 0xFF) / 255.0f;
                if (a <= 0.01f) a = 1.0f;
                float halfA = a * 0.5f;

                for (int x = -16; x <= 32; x += 16) {
                    for (int z = -16; z <= 32; z += 16) {
                        if (innerEnabled.isValue() && (x == 0 || x == 16) && (z == 0 || z == 16)) {
                            continue;
                        }
                        buf.vertex(posMatrix, minBlockX + x, minY, minBlockZ + z).color(r, g, b, 0.0f);
                        buf.vertex(posMatrix, minBlockX + x, minY, minBlockZ + z).color(r, g, b, halfA);
                        buf.vertex(posMatrix, minBlockX + x, maxY, minBlockZ + z).color(r, g, b, halfA);
                        buf.vertex(posMatrix, minBlockX + x, maxY, minBlockZ + z).color(r, g, b, 0.0f);
                    }
                }
            }

            // 2. Grid (yellow vertical lines along X and Z boundaries + horizontal rings at each block level)
            if (gridEnabled.isValue()) {
                double thick = (double) gridThickness.getValue();
                VertexConsumer buf = vertexConsumers.getBuffer(RenderLayer.getDebugLineStrip(thick));

                int col = gridColor.getColor();
                float r = ((col >> 16) & 0xFF) / 255.0f;
                float g = ((col >> 8) & 0xFF) / 255.0f;
                float b = (col & 0xFF) / 255.0f;
                float a = ((col >> 24) & 0xFF) / 255.0f;
                if (a <= 0.01f) a = 1.0f;

                int step = Math.max(1, (int) gridSize.getValue());

                // Vertical lines along X (at Z = 0 and Z = 16)
                for (int x = step; x < 16; x += step) {
                    buf.vertex(posMatrix, minBlockX + x, minY, minBlockZ).color(r, g, b, 0.0f);
                    buf.vertex(posMatrix, minBlockX + x, minY, minBlockZ).color(r, g, b, a);
                    buf.vertex(posMatrix, minBlockX + x, maxY, minBlockZ).color(r, g, b, a);
                    buf.vertex(posMatrix, minBlockX + x, maxY, minBlockZ).color(r, g, b, 0.0f);

                    buf.vertex(posMatrix, minBlockX + x, minY, minBlockZ + 16.0f).color(r, g, b, 0.0f);
                    buf.vertex(posMatrix, minBlockX + x, minY, minBlockZ + 16.0f).color(r, g, b, a);
                    buf.vertex(posMatrix, minBlockX + x, maxY, minBlockZ + 16.0f).color(r, g, b, a);
                    buf.vertex(posMatrix, minBlockX + x, maxY, minBlockZ + 16.0f).color(r, g, b, 0.0f);
                }

                // Vertical lines along Z (at X = 0 and X = 16)
                for (int z = step; z < 16; z += step) {
                    buf.vertex(posMatrix, minBlockX, minY, minBlockZ + z).color(r, g, b, 0.0f);
                    buf.vertex(posMatrix, minBlockX, minY, minBlockZ + z).color(r, g, b, a);
                    buf.vertex(posMatrix, minBlockX, maxY, minBlockZ + z).color(r, g, b, a);
                    buf.vertex(posMatrix, minBlockX, maxY, minBlockZ + z).color(r, g, b, 0.0f);

                    buf.vertex(posMatrix, minBlockX + 16.0f, minY, minBlockZ + z).color(r, g, b, 0.0f);
                    buf.vertex(posMatrix, minBlockX + 16.0f, minY, minBlockZ + z).color(r, g, b, a);
                    buf.vertex(posMatrix, minBlockX + 16.0f, maxY, minBlockZ + z).color(r, g, b, a);
                    buf.vertex(posMatrix, minBlockX + 16.0f, maxY, minBlockZ + z).color(r, g, b, 0.0f);
                }

                // Horizontal perimeter rings
                int bottomY = client.world.getBottomY();
                int topY = client.world.getTopYInclusive() + 1;
                for (int y = bottomY; y <= topY; y += step) {
                    float relY = (float) (y - cameraY);
                    buf.vertex(posMatrix, minBlockX, relY, minBlockZ).color(r, g, b, 0.0f);
                    buf.vertex(posMatrix, minBlockX, relY, minBlockZ).color(r, g, b, a);
                    buf.vertex(posMatrix, minBlockX, relY, minBlockZ + 16.0f).color(r, g, b, a);
                    buf.vertex(posMatrix, minBlockX + 16.0f, relY, minBlockZ + 16.0f).color(r, g, b, a);
                    buf.vertex(posMatrix, minBlockX + 16.0f, relY, minBlockZ).color(r, g, b, a);
                    buf.vertex(posMatrix, minBlockX, relY, minBlockZ).color(r, g, b, a);
                    buf.vertex(posMatrix, minBlockX, relY, minBlockZ).color(r, g, b, 0.0f);
                }
            }

            // 3. Inner Corners (blue corners of current chunk + 16x16 sub-chunk horizontal frames)
            if (innerEnabled.isValue()) {
                double thick = (double) innerThickness.getValue();
                VertexConsumer buf = vertexConsumers.getBuffer(RenderLayer.getDebugLineStrip(thick));

                int col = innerColor.getColor();
                float r = ((col >> 16) & 0xFF) / 255.0f;
                float g = ((col >> 8) & 0xFF) / 255.0f;
                float b = (col & 0xFF) / 255.0f;
                float a = ((col >> 24) & 0xFF) / 255.0f;
                if (a <= 0.01f) a = 1.0f;

                // 4 corners of current chunk
                for (int x = 0; x <= 16; x += 16) {
                    for (int z = 0; z <= 16; z += 16) {
                        buf.vertex(posMatrix, minBlockX + x, minY, minBlockZ + z).color(r, g, b, 0.0f);
                        buf.vertex(posMatrix, minBlockX + x, minY, minBlockZ + z).color(r, g, b, a);
                        buf.vertex(posMatrix, minBlockX + x, maxY, minBlockZ + z).color(r, g, b, a);
                        buf.vertex(posMatrix, minBlockX + x, maxY, minBlockZ + z).color(r, g, b, 0.0f);
                    }
                }

                // 16-block sub-chunk horizontal frames
                int bottomY = client.world.getBottomY();
                int topY = client.world.getTopYInclusive() + 1;
                for (int y = bottomY; y <= topY; y += 16) {
                    float relY = (float) (y - cameraY);
                    buf.vertex(posMatrix, minBlockX, relY, minBlockZ).color(r, g, b, 0.0f);
                    buf.vertex(posMatrix, minBlockX, relY, minBlockZ).color(r, g, b, a);
                    buf.vertex(posMatrix, minBlockX, relY, minBlockZ + 16.0f).color(r, g, b, a);
                    buf.vertex(posMatrix, minBlockX + 16.0f, relY, minBlockZ + 16.0f).color(r, g, b, a);
                    buf.vertex(posMatrix, minBlockX + 16.0f, relY, minBlockZ).color(r, g, b, a);
                    buf.vertex(posMatrix, minBlockX, relY, minBlockZ).color(r, g, b, a);
                    buf.vertex(posMatrix, minBlockX, relY, minBlockZ).color(r, g, b, 0.0f);
                }
            }
        } catch (Throwable ignored) {
            // Failsafe: rendering chunk borders must never crash the game client
        }
    }
}
