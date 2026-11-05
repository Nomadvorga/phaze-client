package vorga.phazeclient.api.system.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;

/** Smooth contour geometry in the existing vanilla GUI batch, with no additional render passes. */
public final class HudBackgroundGeometryRenderer {
    private HudBackgroundGeometryRenderer() {}

    public static void solidRounded(DrawContext context, float x, float y, float width, float height, int color, float radius) {
        roundedGradient(context, x, y, x + width, y + height, color, color, "Left to Right", 0, false, radius);
    }

    public static void roundedGradient(DrawContext context, float left, float top, float right, float bottom,
                                       int first, int second, String selectedDirection, float offset, boolean animated, float radius) {
        if (right <= left || bottom <= top) return;
        if (radius <= 0.01F) { staticGradient(context, left, top, right, bottom, first, second, selectedDirection); return; }
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        VertexConsumer vertices = context.vertexConsumers.getBuffer(RenderLayer.getGui());
        float scale = windowScale();
        HudRoundedContour mesh = HudRoundedContour.get(right - left, bottom - top, radius,
                Math.abs(matrix.m00()) * scale, Math.abs(matrix.m11()) * scale);
        int direction = HudBackgroundGeometryGradients.direction(selectedDirection);
        boolean uniform = first == second || direction == HudBackgroundGeometryGradients.PULSE;
        int pulse = direction == HudBackgroundGeometryGradients.PULSE
                ? HudBackgroundGeometryGradients.sample(first, second, direction, 0, 0, offset, animated) : first;
        int[] palette = uniform || animated ? null : mesh.colors(first, second, direction);
        for (int i = 0; i < mesh.points() - 1; i++) {
            float x0 = mesh.innerX(i), x1 = mesh.innerX(i + 1);
            float y0 = mesh.innerY(i), y1 = mesh.innerY(i + 1);
            int tl = uniform ? pulse : palette != null ? palette[i * 2]
                    : sample(mesh, first, second, direction, x0, y0, offset, animated);
            int bl = uniform ? pulse : palette != null ? palette[i * 2 + 1]
                    : sample(mesh, first, second, direction, x0, mesh.height - y0, offset, animated);
            int tr = uniform ? pulse : palette != null ? palette[(i + 1) * 2]
                    : sample(mesh, first, second, direction, x1, y1, offset, animated);
            int br = uniform ? pulse : palette != null ? palette[(i + 1) * 2 + 1]
                    : sample(mesh, first, second, direction, x1, mesh.height - y1, offset, animated);
            // Centre cosine field still needs subdivisions; corners and the AA fringe share endpoints.
            if (i == mesh.capSegments && animated && !uniform
                    && (HudBackgroundGeometryGradients.horizontal(direction) || HudBackgroundGeometryGradients.diagonal(direction))) {
                float from = x0;
                int fromTop = tl, fromBottom = bl;
                for (int s = 1; s <= 64; s++) {
                    float to = x0 + (x1 - x0) * (s / 64.0F);
                    int toTop = sample(mesh, first, second, direction, to, y0, offset, true);
                    int toBottom = sample(mesh, first, second, direction, to, mesh.height - y0, offset, true);
                    column(vertices, matrix, left, top, mesh.height, from, to, y0, y0,
                            fromTop, fromBottom, toBottom, toTop);
                    edge(vertices, matrix, left, top, from, y0, to, y0, from, mesh.outerY(i), to, mesh.outerY(i), fromTop, toTop);
                    edge(vertices, matrix, left, top, to, mesh.height - y0, from, mesh.height - y0,
                            to, mesh.height - mesh.outerY(i), from, mesh.height - mesh.outerY(i), toBottom, fromBottom);
                    from = to; fromTop = toTop; fromBottom = toBottom;
                }
            } else {
                column(vertices, matrix, left, top, mesh.height, x0, x1, y0, y1, tl, bl, br, tr);
                edge(vertices, matrix, left, top, x0, y0, x1, y1,
                        mesh.outerX(i), mesh.outerY(i), mesh.outerX(i + 1), mesh.outerY(i + 1), tl, tr);
                edge(vertices, matrix, left, top, x1, mesh.height - y1, x0, mesh.height - y0,
                        mesh.outerX(i + 1), mesh.height - mesh.outerY(i + 1), mesh.outerX(i), mesh.height - mesh.outerY(i), br, bl);
            }
            if (i == 0) edge(vertices, matrix, left, top, x0, mesh.height - y0, x0, y0,
                    mesh.outerX(i), mesh.height - y0, mesh.outerX(i), y0, bl, tl);
            if (i == mesh.points() - 2) edge(vertices, matrix, left, top, x1, y1, x1, mesh.height - y1,
                    mesh.outerX(i + 1), y1, mesh.outerX(i + 1), mesh.height - y1, tr, br);
        }
    }

    private static int sample(HudRoundedContour mesh, int first, int second, int direction, float x, float y, float offset, boolean animated) {
        return HudBackgroundGeometryGradients.sample(first, second, direction, x / mesh.width, y / mesh.height, offset, animated);
    }

    private static void column(VertexConsumer v, Matrix4f m, float x, float y, float height,
                               float x0, float x1, float y0, float y1, int tl, int bl, int br, int tr) {
        vertex(v, m, x + x0, y + y0, tl); vertex(v, m, x + x0, y + height - y0, bl);
        vertex(v, m, x + x1, y + height - y1, br); vertex(v, m, x + x1, y + y1, tr);
    }

    private static void edge(VertexConsumer v, Matrix4f m, float x, float y,
                             float ax, float ay, float bx, float by, float ox, float oy, float px, float py, int a, int b) {
        vertex(v, m, x + ax, y + ay, a); vertex(v, m, x + bx, y + by, b);
        vertex(v, m, x + px, y + py, b & 0x00FFFFFF); vertex(v, m, x + ox, y + oy, a & 0x00FFFFFF);
    }

    public static void staticGradient(DrawContext context, float left, float top, float right, float bottom,
                                      int first, int second, String selectedDirection) {
        int direction = HudBackgroundGeometryGradients.direction(selectedDirection);
        quad(context, left, top, right, bottom,
                HudBackgroundGeometryGradients.sample(first, second, direction, 0, 0, 0, false),
                HudBackgroundGeometryGradients.sample(first, second, direction, 0, 1, 0, false),
                HudBackgroundGeometryGradients.sample(first, second, direction, 1, 1, 0, false),
                HudBackgroundGeometryGradients.sample(first, second, direction, 1, 0, 0, false));
    }

    public static void movingDiagonal(DrawContext context, float left, float top, float right, float bottom,
                                      int first, int second, String selectedDirection, float offset) {
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        VertexConsumer vertices = context.vertexConsumers.getBuffer(RenderLayer.getGui());
        int direction = HudBackgroundGeometryGradients.direction(selectedDirection);
        for (int i = 0; i < 96; i++) {
            float progress = (i + 0.5F) / 96;
            float x = left + (right - left) * (i / 96.0F);
            float next = left + (right - left) * ((i + 1.0F) / 96);
            int from = HudBackgroundGeometryGradients.sample(first, second, direction, progress, 0, offset, true);
            int to = HudBackgroundGeometryGradients.sample(first, second, direction, progress, 1, offset, true);
            quad(vertices, matrix, x, top, next, bottom, from, to, to, from);
        }
    }

    public static void quad(DrawContext context, float left, float top, float right, float bottom, int tl, int bl, int br, int tr) {
        if (right <= left || bottom <= top) return;
        quad(context.vertexConsumers.getBuffer(RenderLayer.getGui()), context.getMatrices().peek().getPositionMatrix(),
                left, top, right, bottom, tl, bl, br, tr);
    }

    private static void quad(VertexConsumer v, Matrix4f m, float left, float top, float right, float bottom, int tl, int bl, int br, int tr) {
        if (right <= left || bottom <= top) return;
        vertex(v, m, left, top, tl); vertex(v, m, left, bottom, bl);
        vertex(v, m, right, bottom, br); vertex(v, m, right, top, tr);
    }

    private static void vertex(VertexConsumer v, Matrix4f m, float x, float y, int color) {
        v.vertex(m, x, y, 0.0F).color(color);
    }

    private static float windowScale() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null || client.getWindow() == null ? 1.0F : Math.max(1.0F, (float) client.getWindow().getScaleFactor());
    }
}
