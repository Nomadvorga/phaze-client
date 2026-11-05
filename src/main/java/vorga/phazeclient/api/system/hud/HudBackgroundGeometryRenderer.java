package vorga.phazeclient.api.system.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;

/** Vanilla GUI BufferBuilder output: no shaders, framebuffer coordinates, flushes or matrix-stack churn. */
public final class HudBackgroundGeometryRenderer {
    private HudBackgroundGeometryRenderer() {}

    public static void solidRounded(DrawContext context, float x, float y, float width, float height, int color, float radius) {
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        VertexConsumer vertices = context.vertexConsumers.getBuffer(RenderLayer.getGui());
        float windowScale = windowScale();
        float scaleX = Math.max(0.01F, Math.abs(matrix.m00()) * windowScale);
        float scaleY = Math.max(0.01F, Math.abs(matrix.m11()) * windowScale);
        HudBackgroundGeometry.Caps caps = HudBackgroundGeometry.caps(radius, scaleX);
        float aa = Math.min(radius, 1.0F / scaleY);
        quad(vertices, matrix, x + radius, y, x + width - radius, y + height, color, color, color, color);
        for (int i = 0; i < caps.strips; i++) {
            float exactTop = y + caps.inset(i), exactBottom = y + height - caps.inset(i);
            if (exactBottom <= exactTop) continue;
            solidStrip(vertices, matrix, x + caps.x0(i), exactTop, caps.width(i), exactBottom, color, aa);
            solidStrip(vertices, matrix, x + width - caps.x1(i), exactTop, caps.width(i), exactBottom, color, aa);
        }
    }

    private static void solidStrip(VertexConsumer vertices, Matrix4f matrix, float x, float top, float width,
                                   float bottom, int color, float aa) {
        float half = aa * 0.5F;
        vertical(vertices, matrix, x, Math.min(bottom, top + half), width,
                Math.max(top, bottom - half) - Math.min(bottom, top + half), color, color);
        if (aa > 0.001F) {
            vertical(vertices, matrix, x, top - half, width, aa, color & 0x00FFFFFF, color);
            vertical(vertices, matrix, x, bottom - half, width, aa, color, color & 0x00FFFFFF);
        }
    }

    public static void roundedGradient(DrawContext context, float left, float top, float right, float bottom,
                                       int first, int second, String selectedDirection, float offset, boolean animated, float radius) {
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        VertexConsumer vertices = context.vertexConsumers.getBuffer(RenderLayer.getGui());
        float windowScale = windowScale();
        HudBackgroundGeometry geometry = HudBackgroundGeometry.rounded(right - left, bottom - top, radius,
                Math.max(0.01F, Math.abs(matrix.m00()) * windowScale), Math.max(0.01F, Math.abs(matrix.m11()) * windowScale));
        int direction = HudBackgroundGeometryGradients.direction(selectedDirection);
        HudBackgroundGeometry.Colors colors = animated || direction == HudBackgroundGeometryGradients.PULSE
                ? null : geometry.staticColors(first, second, direction);
        int pulse = direction == HudBackgroundGeometryGradients.PULSE
                ? HudBackgroundGeometryGradients.sample(first, second, direction, 0, 0, offset, animated) : 0;

        // Animated diagonals intentionally retain the old strip topology: a four-corner
        // quad's triangle interpolation is not the old vertically-interpolated cosine field.
        boolean splitCenter = !animated || !HudBackgroundGeometryGradients.diagonal(direction);
        for (int i = 0; i < geometry.strips; i++) {
            if (splitCenter && i == geometry.centerStart && geometry.centerEnd > i) {
                center(vertices, matrix, geometry, left, top, first, second, direction, offset, animated, pulse);
                i = geometry.centerEnd - 1;
                continue;
            }
            float inset = geometry.inset(i);
            float exactTop = top + inset, exactBottom = bottom - inset;
            if (exactBottom <= exactTop) continue;
            float solidTop = top + geometry.solidTop(i), solidBottom = top + geometry.solidBottom(i);
            float x = left + geometry.x0(i), width = geometry.x1(i) - geometry.x0(i);
            float progress = geometry.xProgress(i);
            boolean uniformY = HudBackgroundGeometryGradients.horizontal(direction) || direction == HudBackgroundGeometryGradients.PULSE;
            int topColor = direction == HudBackgroundGeometryGradients.PULSE ? pulse : colors != null ? colors.top(i)
                    : HudBackgroundGeometryGradients.sample(first, second, direction, progress, geometry.solidTop(i) / geometry.height, offset, true);
            int bottomColor = uniformY ? topColor : colors != null ? colors.bottom(i)
                    : HudBackgroundGeometryGradients.sample(first, second, direction, progress, geometry.solidBottom(i) / geometry.height, offset, true);
            vertical(vertices, matrix, x, solidTop, width, solidBottom - solidTop, topColor, bottomColor);
            if (geometry.isCap(i) && geometry.antialias > 0.001F) {
                int exactTopColor = uniformY ? topColor : colors != null ? colors.exactTop(i)
                        : HudBackgroundGeometryGradients.sample(first, second, direction, progress, inset / geometry.height, offset, true);
                int exactBottomColor = uniformY ? topColor : colors != null ? colors.exactBottom(i)
                        : HudBackgroundGeometryGradients.sample(first, second, direction, progress, 1.0F - inset / geometry.height, offset, true);
                vertical(vertices, matrix, x, exactTop - geometry.antialias * 0.5F, width, geometry.antialias,
                        exactTopColor & 0x00FFFFFF, exactTopColor);
                vertical(vertices, matrix, x, exactBottom - geometry.antialias * 0.5F, width, geometry.antialias,
                        exactBottomColor, exactBottomColor & 0x00FFFFFF);
            }
        }
    }

    private static void center(VertexConsumer vertices, Matrix4f matrix, HudBackgroundGeometry geometry, float left, float top,
                               int first, int second, int direction, float offset, boolean animated, int pulse) {
        float x0 = geometry.x0(geometry.centerStart), x1 = geometry.x1(geometry.centerEnd - 1);
        float y0 = geometry.solidTop(geometry.centerStart), y1 = geometry.solidBottom(geometry.centerStart);
        if (direction == HudBackgroundGeometryGradients.PULSE) {
            quad(vertices, matrix, left + x0, top + y0, left + x1, top + y1, pulse, pulse, pulse, pulse);
            return;
        }
        if (!animated || !HudBackgroundGeometryGradients.horizontal(direction)) {
            int tl = HudBackgroundGeometryGradients.sample(first, second, direction, x0 / geometry.width, y0 / geometry.height, offset, animated);
            int bl = HudBackgroundGeometryGradients.sample(first, second, direction, x0 / geometry.width, y1 / geometry.height, offset, animated);
            int br = HudBackgroundGeometryGradients.sample(first, second, direction, x1 / geometry.width, y1 / geometry.height, offset, animated);
            int tr = HudBackgroundGeometryGradients.sample(first, second, direction, x1 / geometry.width, y0 / geometry.height, offset, animated);
            quad(vertices, matrix, left + x0, top + y0, left + x1, top + y1, tl, bl, br, tr);
            return;
        }
        // Keep the rounded HUD's cosine wave, NOT the unrounded axis helper's
        // triangular wave. 64 segments/cycle bound curvature error below 0.31/255.
        int segments = geometry.horizontalCenterSegments();
        float from = x0;
        int fromColor = HudBackgroundGeometryGradients.sample(first, second, direction, from / geometry.width, 0, offset, true);
        for (int i = 0; i < segments; i++) {
            float to = x0 + (x1 - x0) * ((i + 1.0F) / segments);
            int toColor = HudBackgroundGeometryGradients.sample(first, second, direction, to / geometry.width, 0, offset, true);
            quad(vertices, matrix, left + from, top + y0, left + to, top + y1, fromColor, fromColor, toColor, toColor);
            from = to; fromColor = toColor;
        }
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
            // The legacy non-axis fallback also used a cosine horizontal field.
            int from = HudBackgroundGeometryGradients.sample(first, second, direction, progress, 0, offset, true);
            int to = HudBackgroundGeometryGradients.sample(first, second, direction, progress, 1, offset, true);
            vertical(vertices, matrix, x, top, next - x, bottom - top, from, to);
        }
    }

    public static void quad(DrawContext context, float left, float top, float right, float bottom, int tl, int bl, int br, int tr) {
        if (right <= left || bottom <= top) return;
        quad(context.vertexConsumers.getBuffer(RenderLayer.getGui()), context.getMatrices().peek().getPositionMatrix(),
                left, top, right, bottom, tl, bl, br, tr);
    }

    private static void vertical(VertexConsumer vertices, Matrix4f matrix, float x, float y, float width, float height, int from, int to) {
        quad(vertices, matrix, x, y, x + width, y + height, from, to, to, from);
    }

    private static void quad(VertexConsumer vertices, Matrix4f matrix, float left, float top, float right, float bottom,
                              int tl, int bl, int br, int tr) {
        if (right <= left || bottom <= top) return;
        // Same winding, local Z and GUI layer as DrawContext.fillGradient.
        vertices.vertex(matrix, left, top, 0.0F).color(tl);
        vertices.vertex(matrix, left, bottom, 0.0F).color(bl);
        vertices.vertex(matrix, right, bottom, 0.0F).color(br);
        vertices.vertex(matrix, right, top, 0.0F).color(tr);
    }

    private static float windowScale() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null || client.getWindow() == null ? 1.0F : Math.max(1.0F, (float) client.getWindow().getScaleFactor());
    }
}
