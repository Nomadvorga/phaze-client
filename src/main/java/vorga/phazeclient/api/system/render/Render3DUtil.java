package vorga.phazeclient.api.system.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class Render3DUtil {
    private static final int MAX_CACHED_CIRCLE_SEGMENTS = 256;
    private static final int MAX_CACHED_SPHERE_STACKS = 64;
    private static final CircleLut[] CIRCLE_LUT_CACHE = new CircleLut[MAX_CACHED_CIRCLE_SEGMENTS + 1];
    private static final SphereLatitudeLut[] SPHERE_LATITUDE_CACHE = new SphereLatitudeLut[MAX_CACHED_SPHERE_STACKS + 1];
    private static final Vector3f BILLBOARD_RIGHT = new Vector3f();
    private static final Vector3f BILLBOARD_UP = new Vector3f();

    private static final float DEFAULT_LINE_WIDTH = 1.0F;

    private Render3DUtil() {
    }

    public static void drawBoxFill(MatrixStack matrices,
                                   float x1, float y1, float z1,
                                   float x2, float y2, float z2,
                                   int color,
                                   float fillAlphaScale) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float fillA = a * Math.max(0.0F, Math.min(1.0F, fillAlphaScale));

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, fillA);

        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, fillA);

        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, fillA);

        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, fillA);

        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, fillA);

        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, fillA);

        vorga.phazeclient.util.render.PhazeRenderLayers.getBlockFill().draw(buffer.end());
    }

    public static void drawBox(MatrixStack matrices,
                               float x1, float y1, float z1,
                               float x2, float y2, float z2,
                               int color,
                               float fillAlphaScale,
                               float lineWidth) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float fillA = a * Math.max(0.0F, Math.min(1.0F, fillAlphaScale));

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, fillA);

        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, fillA);

        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, fillA);

        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, fillA);

        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, fillA);

        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, fillA);
        vorga.phazeclient.util.render.PhazeRenderLayers.getHitboxFill().draw(buffer.end());

        float lw = Math.max(1.0F, lineWidth);
        buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);

        line(matrix, buffer, x1, y1, z1, x2, y1, z1, r, g, b, a, lw);
        line(matrix, buffer, x2, y1, z1, x2, y1, z2, r, g, b, a, lw);
        line(matrix, buffer, x2, y1, z2, x1, y1, z2, r, g, b, a, lw);
        line(matrix, buffer, x1, y1, z2, x1, y1, z1, r, g, b, a, lw);

        line(matrix, buffer, x1, y2, z1, x2, y2, z1, r, g, b, a, lw);
        line(matrix, buffer, x2, y2, z1, x2, y2, z2, r, g, b, a, lw);
        line(matrix, buffer, x2, y2, z2, x1, y2, z2, r, g, b, a, lw);
        line(matrix, buffer, x1, y2, z2, x1, y2, z1, r, g, b, a, lw);

        line(matrix, buffer, x1, y1, z1, x1, y2, z1, r, g, b, a, lw);
        line(matrix, buffer, x2, y1, z1, x2, y2, z1, r, g, b, a, lw);
        line(matrix, buffer, x2, y1, z2, x2, y2, z2, r, g, b, a, lw);
        line(matrix, buffer, x1, y1, z2, x1, y2, z2, r, g, b, a, lw);
        vorga.phazeclient.util.render.PhazeRenderLayers.getThickLines(lw).draw(buffer.end());

    }

    public static void vertexBoxFill(MatrixStack matrices,
                                     VertexConsumer consumer,
                                     Box box,
                                     int color,
                                     float fillAlphaScale) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float fillA = a * Math.max(0.0F, Math.min(1.0F, fillAlphaScale));
        float x1 = (float) box.minX;
        float y1 = (float) box.minY;
        float z1 = (float) box.minZ;
        float x2 = (float) box.maxX;
        float y2 = (float) box.maxY;
        float z2 = (float) box.maxZ;

        consumer.vertex(matrix, x1, y1, z1).color(r, g, b, fillA);
        consumer.vertex(matrix, x2, y1, z1).color(r, g, b, fillA);
        consumer.vertex(matrix, x2, y1, z2).color(r, g, b, fillA);
        consumer.vertex(matrix, x1, y1, z2).color(r, g, b, fillA);

        consumer.vertex(matrix, x1, y2, z1).color(r, g, b, fillA);
        consumer.vertex(matrix, x1, y2, z2).color(r, g, b, fillA);
        consumer.vertex(matrix, x2, y2, z2).color(r, g, b, fillA);
        consumer.vertex(matrix, x2, y2, z1).color(r, g, b, fillA);

        consumer.vertex(matrix, x1, y1, z1).color(r, g, b, fillA);
        consumer.vertex(matrix, x1, y2, z1).color(r, g, b, fillA);
        consumer.vertex(matrix, x2, y2, z1).color(r, g, b, fillA);
        consumer.vertex(matrix, x2, y1, z1).color(r, g, b, fillA);

        consumer.vertex(matrix, x1, y1, z2).color(r, g, b, fillA);
        consumer.vertex(matrix, x2, y1, z2).color(r, g, b, fillA);
        consumer.vertex(matrix, x2, y2, z2).color(r, g, b, fillA);
        consumer.vertex(matrix, x1, y2, z2).color(r, g, b, fillA);

        consumer.vertex(matrix, x1, y1, z1).color(r, g, b, fillA);
        consumer.vertex(matrix, x1, y1, z2).color(r, g, b, fillA);
        consumer.vertex(matrix, x1, y2, z2).color(r, g, b, fillA);
        consumer.vertex(matrix, x1, y2, z1).color(r, g, b, fillA);

        consumer.vertex(matrix, x2, y1, z1).color(r, g, b, fillA);
        consumer.vertex(matrix, x2, y2, z1).color(r, g, b, fillA);
        consumer.vertex(matrix, x2, y2, z2).color(r, g, b, fillA);
        consumer.vertex(matrix, x2, y1, z2).color(r, g, b, fillA);
    }

    public static void drawSolidBox(MatrixStack matrices,
                                    float x1, float y1, float z1,
                                    float x2, float y2, float z2,
                                    int color,
                                    float fillAlphaScale,
                                    boolean depthTest) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float fillA = a * Math.max(0.0F, Math.min(1.0F, fillAlphaScale));

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, fillA);

        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, fillA);

        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, fillA);

        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, fillA);

        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, fillA);

        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, fillA);
        vorga.phazeclient.util.render.PhazeRenderLayers.getHitboxFill().draw(buffer.end());

    }

    public static void drawSphereSolid(MatrixStack matrices,
                                       float cx, float cy, float cz,
                                       float radius,
                                       int color,
                                       int stacks, int segments) {
        drawSphereSolid(matrices, cx, cy, cz, radius, color, stacks, segments, true);
    }

    public static void drawSphereSolid(MatrixStack matrices,
                                       float cx, float cy, float cz,
                                       float radius,
                                       int color,
                                       int stacks, int segments,
                                       boolean depthTest) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        CircleLut circle = circleLut(segments);
        SphereLatitudeLut latitudes = sphereLatitudeLut(stacks);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < latitudes.stacks; i++) {
            float y1 = latitudes.sin[i] * radius;
            float y2 = latitudes.sin[i + 1] * radius;
            float r1 = latitudes.cos[i] * radius;
            float r2 = latitudes.cos[i + 1] * radius;
            for (int j = 0; j < circle.segments; j++) {
                float c1 = circle.cos[j];
                float s1 = circle.sin[j];
                float c2 = circle.cos[j + 1];
                float s2 = circle.sin[j + 1];

                float x11 = cx + r1 * c1, z11 = cz + r1 * s1;
                float x12 = cx + r1 * c2, z12 = cz + r1 * s2;
                float x21 = cx + r2 * c1, z21 = cz + r2 * s1;
                float x22 = cx + r2 * c2, z22 = cz + r2 * s2;

                buffer.vertex(matrix, x11, cy + y1, z11).color(r, g, b, a);
                buffer.vertex(matrix, x21, cy + y2, z21).color(r, g, b, a);
                buffer.vertex(matrix, x22, cy + y2, z22).color(r, g, b, a);
                buffer.vertex(matrix, x12, cy + y1, z12).color(r, g, b, a);
            }
        }
        net.minecraft.client.render.BuiltBuffer built = buffer.endNullable();
        if (built != null) {
            vorga.phazeclient.util.render.PhazeRenderLayers.getHitboxFill().draw(built);
        }

    }

    public static void drawBillboard(MatrixStack matrices, net.minecraft.util.Identifier texture,
                                     float cx, float cy, float cz,
                                     float radius, int color) {
        drawBillboard(matrices, texture, cx, cy, cz, radius, color, false);
    }

    public static void drawBillboard(MatrixStack matrices, net.minecraft.util.Identifier texture,
                                     float cx, float cy, float cz,
                                     float radius, int color, boolean depthTest) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null || client.gameRenderer.getCamera() == null) {
            return;
        }
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        var camera = client.gameRenderer.getCamera();
        Vector3f rightV = BILLBOARD_RIGHT;
        Vector3f upV = BILLBOARD_UP;
        org.joml.Quaternionf rotation = camera.getRotation();
        rotation.transform(1.0F, 0.0F, 0.0F, rightV);
        rotation.transform(0.0F, 1.0F, 0.0F, upV);

        float rx = rightV.x * radius, ry = rightV.y * radius, rz = rightV.z * radius;
        float ux = upV.x * radius,    uy = upV.y * radius,    uz = upV.z * radius;

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        buffer.vertex(matrix, cx - rx - ux, cy - ry - uy, cz - rz - uz).texture(0.0F, 1.0F).color(r, g, b, a);
        buffer.vertex(matrix, cx + rx - ux, cy + ry - uy, cz + rz - uz).texture(1.0F, 1.0F).color(r, g, b, a);
        buffer.vertex(matrix, cx + rx + ux, cy + ry + uy, cz + rz + uz).texture(1.0F, 0.0F).color(r, g, b, a);
        buffer.vertex(matrix, cx - rx + ux, cy - ry + uy, cz - rz + uz).texture(0.0F, 0.0F).color(r, g, b, a);

        net.minecraft.client.render.BuiltBuffer built = buffer.endNullable();
        if (built != null) {
            vorga.phazeclient.util.render.PhazeRenderLayers.getTextured(texture).draw(built);
        }

    }

    private static void line(Matrix4f matrix, BufferBuilder buffer,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float r, float g, float b, float a, float lineWidth) {

        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1.0E-4F) {
            dx /= len;
            dy /= len;
            dz /= len;
        } else {
            dx = 0.0F;
            dy = 1.0F;
            dz = 0.0F;
        }
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(dx, dy, dz).lineWidth(lineWidth);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).normal(dx, dy, dz).lineWidth(lineWidth);
    }

    public static void drawThickFlatRing(MatrixStack matrices,
                                         float centerX, float centerY, float centerZ,
                                         float radius, float thickness,
                                         int color, int segments,
                                         boolean depthTest) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float inner = Math.max(0.0F, radius - thickness * 0.5F);
        float outer = radius + thickness * 0.5F;

        CircleLut circle = circleLut(segments);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < circle.segments; i++) {
            float c1 = circle.cos[i], s1 = circle.sin[i];
            float c2 = circle.cos[i + 1], s2 = circle.sin[i + 1];

            float ix1 = centerX + c1 * inner, iz1 = centerZ + s1 * inner;
            float ix2 = centerX + c2 * inner, iz2 = centerZ + s2 * inner;
            float ox1 = centerX + c1 * outer, oz1 = centerZ + s1 * outer;
            float ox2 = centerX + c2 * outer, oz2 = centerZ + s2 * outer;

            buffer.vertex(matrix, ix1, centerY, iz1).color(r, g, b, a);
            buffer.vertex(matrix, ix2, centerY, iz2).color(r, g, b, a);
            buffer.vertex(matrix, ox2, centerY, oz2).color(r, g, b, a);
            buffer.vertex(matrix, ox1, centerY, oz1).color(r, g, b, a);
        }
        vorga.phazeclient.util.render.PhazeRenderLayers.getHitboxFill().draw(buffer.end());

    }

    public static void drawFlatGlowDisc(MatrixStack matrices,
                                        float centerX, float centerY, float centerZ,
                                        float radius,
                                        int centerColor, int rimColor,
                                        int segments,
                                        boolean depthTest) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float ca = ((centerColor >>> 24) & 0xFF) / 255.0F;
        float cr = ((centerColor >>> 16) & 0xFF) / 255.0F;
        float cg = ((centerColor >>> 8) & 0xFF) / 255.0F;
        float cb = (centerColor & 0xFF) / 255.0F;
        float ra = ((rimColor >>> 24) & 0xFF) / 255.0F;
        float rr = ((rimColor >>> 16) & 0xFF) / 255.0F;
        float rg = ((rimColor >>> 8) & 0xFF) / 255.0F;
        float rb = (rimColor & 0xFF) / 255.0F;

        CircleLut circle = circleLut(segments);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < circle.segments; i++) {
            float x1 = centerX + circle.cos[i] * radius;
            float z1 = centerZ + circle.sin[i] * radius;
            float x2 = centerX + circle.cos[i + 1] * radius;
            float z2 = centerZ + circle.sin[i + 1] * radius;

            buffer.vertex(matrix, centerX, centerY, centerZ).color(cr, cg, cb, ca);
            buffer.vertex(matrix, x1, centerY, z1).color(rr, rg, rb, ra);
            buffer.vertex(matrix, x2, centerY, z2).color(rr, rg, rb, ra);
        }
        vorga.phazeclient.util.render.PhazeRenderLayers.getTriangles().draw(buffer.end());

    }

    public static void drawCylinderOutline(MatrixStack matrices,
                                           float centerX, float centerY, float centerZ,
                                           float radius, float height,
                                           int color, float lineWidth, int segments) {
        drawCylinderOutline(matrices, centerX, centerY, centerZ, radius, height, color, lineWidth, segments, true);
    }

    public static void drawCylinderOutline(MatrixStack matrices,
                                           float centerX, float centerY, float centerZ,
                                           float radius, float height,
                                           int color, float lineWidth, int segments,
                                           boolean depthTest) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        float lw = Math.max(1.0F, lineWidth);

        CircleLut circle = circleLut(segments);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);
        float topY = centerY + height;
        for (int i = 0; i < circle.segments; i++) {
            float x1 = centerX + circle.cos[i] * radius;
            float z1 = centerZ + circle.sin[i] * radius;
            float x2 = centerX + circle.cos[i + 1] * radius;
            float z2 = centerZ + circle.sin[i + 1] * radius;
            buffer.vertex(matrix, x1, centerY, z1).color(r, g, b, a).normal(0, 1, 0).lineWidth(lw);
            buffer.vertex(matrix, x2, centerY, z2).color(r, g, b, a).normal(0, 1, 0).lineWidth(lw);
            buffer.vertex(matrix, x1, topY, z1).color(r, g, b, a).normal(0, 1, 0).lineWidth(lw);
            buffer.vertex(matrix, x2, topY, z2).color(r, g, b, a).normal(0, 1, 0).lineWidth(lw);
        }
        vorga.phazeclient.util.render.PhazeRenderLayers.getThickLines(lw).draw(buffer.end());

    }

    public static void drawCircleOnFace(MatrixStack matrices,
                                        float centerX, float centerY, float centerZ,
                                        float nx, float ny, float nz,
                                        float radius,
                                        int color, float lineWidth, int segments,
                                        boolean depthTest) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        float ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
        float hx, hy, hz;
        if (ax <= ay && ax <= az) { hx = 1; hy = 0; hz = 0; }
        else if (ay <= ax && ay <= az) { hx = 0; hy = 1; hz = 0; }
        else { hx = 0; hy = 0; hz = 1; }

        float uxRaw = hy * nz - hz * ny;
        float uyRaw = hz * nx - hx * nz;
        float uzRaw = hx * ny - hy * nx;
        float uLen = (float) Math.sqrt(uxRaw * uxRaw + uyRaw * uyRaw + uzRaw * uzRaw);
        if (uLen < 1.0e-6F) return;
        float ux = uxRaw / uLen, uy = uyRaw / uLen, uz = uzRaw / uLen;

        float vx = ny * uz - nz * uy;
        float vy = nz * ux - nx * uz;
        float vz = nx * uy - ny * ux;

        float lw = Math.max(1.0F, lineWidth);

        CircleLut circle = circleLut(segments);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);
        for (int i = 0; i < circle.segments; i++) {
            float c1 = circle.cos[i], s1 = circle.sin[i];
            float c2 = circle.cos[i + 1], s2 = circle.sin[i + 1];

            float p1x = centerX + (ux * c1 + vx * s1) * radius;
            float p1y = centerY + (uy * c1 + vy * s1) * radius;
            float p1z = centerZ + (uz * c1 + vz * s1) * radius;
            float p2x = centerX + (ux * c2 + vx * s2) * radius;
            float p2y = centerY + (uy * c2 + vy * s2) * radius;
            float p2z = centerZ + (uz * c2 + vz * s2) * radius;
            buffer.vertex(matrix, p1x, p1y, p1z).color(r, g, b, a).normal(0, 1, 0).lineWidth(lw);
            buffer.vertex(matrix, p2x, p2y, p2z).color(r, g, b, a).normal(0, 1, 0).lineWidth(lw);
        }
        vorga.phazeclient.util.render.PhazeRenderLayers.getThickLines(lw).draw(buffer.end());

    }

    public static void drawThickRingOnFace(MatrixStack matrices,
                                           float centerX, float centerY, float centerZ,
                                           float nx, float ny, float nz,
                                           float radius, float thickness,
                                           int color, int segments,
                                           boolean depthTest) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float inner = Math.max(0.0F, radius - thickness * 0.5F);
        float outer = radius + thickness * 0.5F;

        float ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
        float hx, hy, hz;
        if (ax <= ay && ax <= az) { hx = 1; hy = 0; hz = 0; }
        else if (ay <= ax && ay <= az) { hx = 0; hy = 1; hz = 0; }
        else { hx = 0; hy = 0; hz = 1; }
        float uxRaw = hy * nz - hz * ny;
        float uyRaw = hz * nx - hx * nz;
        float uzRaw = hx * ny - hy * nx;
        float uLen = (float) Math.sqrt(uxRaw * uxRaw + uyRaw * uyRaw + uzRaw * uzRaw);
        if (uLen < 1.0e-6F) return;
        float ux = uxRaw / uLen, uy = uyRaw / uLen, uz = uzRaw / uLen;
        float vx = ny * uz - nz * uy;
        float vy = nz * ux - nx * uz;
        float vz = nx * uy - ny * ux;

        CircleLut circle = circleLut(segments);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < circle.segments; i++) {
            float c1 = circle.cos[i], s1 = circle.sin[i];
            float c2 = circle.cos[i + 1], s2 = circle.sin[i + 1];

            float ix1 = centerX + (ux * c1 + vx * s1) * inner;
            float iy1 = centerY + (uy * c1 + vy * s1) * inner;
            float iz1 = centerZ + (uz * c1 + vz * s1) * inner;
            float ix2 = centerX + (ux * c2 + vx * s2) * inner;
            float iy2 = centerY + (uy * c2 + vy * s2) * inner;
            float iz2 = centerZ + (uz * c2 + vz * s2) * inner;
            float ox1 = centerX + (ux * c1 + vx * s1) * outer;
            float oy1 = centerY + (uy * c1 + vy * s1) * outer;
            float oz1 = centerZ + (uz * c1 + vz * s1) * outer;
            float ox2 = centerX + (ux * c2 + vx * s2) * outer;
            float oy2 = centerY + (uy * c2 + vy * s2) * outer;
            float oz2 = centerZ + (uz * c2 + vz * s2) * outer;

            buffer.vertex(matrix, ix1, iy1, iz1).color(r, g, b, a);
            buffer.vertex(matrix, ix2, iy2, iz2).color(r, g, b, a);
            buffer.vertex(matrix, ox2, oy2, oz2).color(r, g, b, a);
            buffer.vertex(matrix, ox1, oy1, oz1).color(r, g, b, a);
        }
        vorga.phazeclient.util.render.PhazeRenderLayers.getHitboxFill().draw(buffer.end());

    }

    public static void vertexLine(MatrixStack matrices, VertexConsumer buffer,
                                  Vec3d start, Vec3d end,
                                  int startColor, int endColor) {
        vertexLine(matrices, buffer,
                start.x, start.y, start.z,
                end.x, end.y, end.z,
                startColor, endColor, DEFAULT_LINE_WIDTH);
    }

    public static void vertexLine(MatrixStack matrices, VertexConsumer buffer,
                                  Vec3d start, Vec3d end,
                                  int startColor, int endColor, float lineWidth) {
        vertexLine(matrices, buffer,
                start.x, start.y, start.z,
                end.x, end.y, end.z,
                startColor, endColor, lineWidth);
    }

    public static void vertexLine(MatrixStack matrices, VertexConsumer buffer,
                                  double startX, double startY, double startZ,
                                  double endX, double endY, double endZ,
                                  int startColor, int endColor) {
        vertexLine(matrices, buffer, startX, startY, startZ, endX, endY, endZ,
                startColor, endColor, DEFAULT_LINE_WIDTH);
    }

    public static void vertexLine(MatrixStack matrices, VertexConsumer buffer,
                                  double startX, double startY, double startZ,
                                  double endX, double endY, double endZ,
                                  int startColor, int endColor, float lineWidth) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        double dx = endX - startX;
        double dy = endY - startY;
        double dz = endZ - startZ;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float normalX = length > 1.0E-7 ? (float) (dx / length) : 0.0F;
        float normalY = length > 1.0E-7 ? (float) (dy / length) : 1.0F;
        float normalZ = length > 1.0E-7 ? (float) (dz / length) : 0.0F;
        float lw = Math.max(1.0F, lineWidth);
        float a1 = ((startColor >>> 24) & 0xFF) / 255.0F;
        float r1 = ((startColor >>> 16) & 0xFF) / 255.0F;
        float g1 = ((startColor >>> 8) & 0xFF) / 255.0F;
        float b1 = (startColor & 0xFF) / 255.0F;
        float a2 = ((endColor >>> 24) & 0xFF) / 255.0F;
        float r2 = ((endColor >>> 16) & 0xFF) / 255.0F;
        float g2 = ((endColor >>> 8) & 0xFF) / 255.0F;
        float b2 = (endColor & 0xFF) / 255.0F;
        buffer.vertex(matrix, (float) startX, (float) startY, (float) startZ)
                .color(r1, g1, b1, a1).normal(normalX, normalY, normalZ).lineWidth(lw);
        buffer.vertex(matrix, (float) endX, (float) endY, (float) endZ)
                .color(r2, g2, b2, a2).normal(normalX, normalY, normalZ).lineWidth(lw);
    }

    public static void vertexLine(MatrixStack matrices, VertexConsumer buffer,
                                  Vec3d start, Vec3d end, int color) {
        vertexLine(matrices, buffer, start, end, color, color, DEFAULT_LINE_WIDTH);
    }

    public static void drawLineWorld(MatrixStack matrices, Vec3d start, Vec3d end, int color, float lineWidth) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null || client.gameRenderer.getCamera() == null) {
            return;
        }

        Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float gC = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        float lw = Math.max(1.0F, lineWidth);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);
        buffer.vertex(matrix,
                (float) (start.x - camera.x),
                (float) (start.y - camera.y),
                (float) (start.z - camera.z)).color(r, gC, b, a).normal(0, 1, 0).lineWidth(lw);
        buffer.vertex(matrix,
                (float) (end.x - camera.x),
                (float) (end.y - camera.y),
                (float) (end.z - camera.z)).color(r, gC, b, a).normal(0, 1, 0).lineWidth(lw);
        vorga.phazeclient.util.render.PhazeRenderLayers.getThickLines(lw).draw(buffer.end());

    }

    public static void drawPolyline(MatrixStack matrices,
                                    java.util.List<Vec3d> points,
                                    int color, float lineWidth, boolean depthTest) {
        drawPolylineOffset(matrices, points, 0.0, 0.0, 0.0, color, lineWidth, depthTest);
    }

    public static void drawPolylineOffset(MatrixStack matrices,
                                          java.util.List<Vec3d> points,
                                          double offsetX, double offsetY, double offsetZ,
                                          int color, float lineWidth, boolean depthTest) {
        if (!hasRenderablePolylineSegment(points)) return;

        Vec3d origin = points.get(0);
        matrices.push();
        matrices.translate(origin.x + offsetX, origin.y + offsetY, origin.z + offsetZ);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        float lw = Math.max(1.0F, lineWidth);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d start = points.get(i);
            Vec3d end = points.get(i + 1);
            if (!isRenderablePolylineSegment(start, end)) continue;
            double dx = end.x - start.x;
            double dy = end.y - start.y;
            double dz = end.z - start.z;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            float normalX = (float) (dx / length);
            float normalY = (float) (dy / length);
            float normalZ = (float) (dz / length);
            buffer.vertex(matrix,
                    (float) (start.x - origin.x),
                    (float) (start.y - origin.y),
                    (float) (start.z - origin.z)).color(r, g, b, a).normal(normalX, normalY, normalZ).lineWidth(lw);
            buffer.vertex(matrix,
                    (float) (end.x - origin.x),
                    (float) (end.y - origin.y),
                    (float) (end.z - origin.z)).color(r, g, b, a).normal(normalX, normalY, normalZ).lineWidth(lw);
        }
        vorga.phazeclient.util.render.PhazeRenderLayers.getThickLines(lw).draw(buffer.end());

        matrices.pop();
    }

    public static void drawPolylineFaded(MatrixStack matrices,
                                         java.util.List<Vec3d> points,
                                         int color, float lineWidth,
                                         float fadeDistance,
                                         boolean depthTest) {
        drawPolylineFadedOffset(matrices, points, 0.0, 0.0, 0.0, color, lineWidth, fadeDistance, depthTest);
    }

    public static void drawPolylineFadedOffset(MatrixStack matrices,
                                               java.util.List<Vec3d> points,
                                               double offsetX, double offsetY, double offsetZ,
                                               int color, float lineWidth,
                                               float fadeDistance,
                                               boolean depthTest) {
        if (!hasRenderablePolylineSegment(points)) return;
        if (fadeDistance <= 0.0F) {
            drawPolylineOffset(matrices, points, offsetX, offsetY, offsetZ, color, lineWidth, depthTest);
            return;
        }
        Vec3d origin = points.get(0);
        matrices.push();
        matrices.translate(origin.x + offsetX, origin.y + offsetY, origin.z + offsetZ);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float maxA = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        float lw = Math.max(1.0F, lineWidth);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);

        double accLen = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d start = points.get(i);
            Vec3d end = points.get(i + 1);
            if (!isRenderablePolylineSegment(start, end)) continue;
            double sx = start.x - origin.x;
            double sy = start.y - origin.y;
            double sz = start.z - origin.z;
            double ex = end.x - origin.x;
            double ey = end.y - origin.y;
            double ez = end.z - origin.z;
            double dx = ex - sx;
            double dy = ey - sy;
            double dz = ez - sz;
            double segLen = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (segLen < 1e-7) continue;
            float normalX = (float) (dx / segLen);
            float normalY = (float) (dy / segLen);
            float normalZ = (float) (dz / segLen);

            double startLen = accLen;
            double endLen = accLen + segLen;

            float startAlpha = (float) Math.min(1.0, Math.max(0.0, startLen / fadeDistance)) * maxA;
            float endAlpha = (float) Math.min(1.0, Math.max(0.0, endLen / fadeDistance)) * maxA;

            if (startLen < fadeDistance && endLen > fadeDistance) {
                double t = (fadeDistance - startLen) / segLen;
                double mx = sx + dx * t;
                double my = sy + dy * t;
                double mz = sz + dz * t;

                buffer.vertex(matrix, (float) sx, (float) sy, (float) sz).color(r, g, b, startAlpha).normal(normalX, normalY, normalZ).lineWidth(lw);
                buffer.vertex(matrix, (float) mx, (float) my, (float) mz).color(r, g, b, maxA).normal(normalX, normalY, normalZ).lineWidth(lw);

                buffer.vertex(matrix, (float) mx, (float) my, (float) mz).color(r, g, b, maxA).normal(normalX, normalY, normalZ).lineWidth(lw);
                buffer.vertex(matrix, (float) ex, (float) ey, (float) ez).color(r, g, b, maxA).normal(normalX, normalY, normalZ).lineWidth(lw);
            } else {
                buffer.vertex(matrix, (float) sx, (float) sy, (float) sz).color(r, g, b, startAlpha).normal(normalX, normalY, normalZ).lineWidth(lw);
                buffer.vertex(matrix, (float) ex, (float) ey, (float) ez).color(r, g, b, endAlpha).normal(normalX, normalY, normalZ).lineWidth(lw);
            }

            accLen = endLen;
        }
        vorga.phazeclient.util.render.PhazeRenderLayers.getThickLines(lw).draw(buffer.end());

        matrices.pop();
    }

    private static boolean hasRenderablePolylineSegment(java.util.List<Vec3d> points) {
        if (points == null || points.size() < 2) return false;
        for (int i = 0; i < points.size() - 1; i++) {
            if (isRenderablePolylineSegment(points.get(i), points.get(i + 1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRenderablePolylineSegment(Vec3d start, Vec3d end) {
        if (start == null || end == null) return false;
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        return Double.isFinite(dx) && Double.isFinite(dy) && Double.isFinite(dz)
                && dx * dx + dy * dy + dz * dz >= 1.0E-14;
    }

    private static CircleLut circleLut(int requestedSegments) {
        int segments = Math.max(3, requestedSegments);
        if (segments > MAX_CACHED_CIRCLE_SEGMENTS) {
            return new CircleLut(segments);
        }
        CircleLut cached = CIRCLE_LUT_CACHE[segments];
        if (cached == null) {
            cached = new CircleLut(segments);
            CIRCLE_LUT_CACHE[segments] = cached;
        }
        return cached;
    }

    private static SphereLatitudeLut sphereLatitudeLut(int requestedStacks) {
        int stacks = Math.max(2, requestedStacks);
        if (stacks > MAX_CACHED_SPHERE_STACKS) {
            return new SphereLatitudeLut(stacks);
        }
        SphereLatitudeLut cached = SPHERE_LATITUDE_CACHE[stacks];
        if (cached == null) {
            cached = new SphereLatitudeLut(stacks);
            SPHERE_LATITUDE_CACHE[stacks] = cached;
        }
        return cached;
    }

    private static final class CircleLut {
        private final int segments;
        private final float[] cos;
        private final float[] sin;

        private CircleLut(int segments) {
            this.segments = segments;
            this.cos = new float[segments + 1];
            this.sin = new float[segments + 1];
            for (int i = 0; i <= segments; i++) {
                double angle = Math.PI * 2.0 * i / segments;
                cos[i] = (float) Math.cos(angle);
                sin[i] = (float) Math.sin(angle);
            }
        }
    }

    private static final class SphereLatitudeLut {
        private final int stacks;
        private final float[] cos;
        private final float[] sin;

        private SphereLatitudeLut(int stacks) {
            this.stacks = stacks;
            this.cos = new float[stacks + 1];
            this.sin = new float[stacks + 1];
            for (int i = 0; i <= stacks; i++) {
                double angle = Math.PI * i / stacks - Math.PI / 2.0;
                cos[i] = (float) Math.cos(angle);
                sin[i] = (float) Math.sin(angle);
            }
        }
    }
}
