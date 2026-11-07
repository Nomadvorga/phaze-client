package vorga.phazeclient.implement.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import static vorga.phazeclient.implement.hud.HudInfoText.*;
import static vorga.phazeclient.implement.hud.HudMath.*;
import static vorga.phazeclient.implement.hud.HudTextDraw.*;

/** Colour math plus background, gradient and outline drawing shared by HUD widgets. */
final class HudPaint {
    private HudPaint() {}

    static int withAlpha(int rgb, int alpha) {
        int a = MathHelper.clamp(alpha, 0, 255);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    static float getClampedHudCornerRadius(float requestedRadius, float width, float height) {
        return MathHelper.clamp(requestedRadius, 0.0F, Math.max(0.0F, Math.min(width, height) * 0.5F));
    }

    static void renderSolidHudBackground(
            DrawContext context,
            float x,
            float y,
            float width,
            float height,
            int color,
            float requestedRadius
    ) {
        float radius = getClampedHudCornerRadius(requestedRadius, width, height);
        if (radius <= 0.01F) {
            context.fill(Math.round(x), Math.round(y), Math.round(x + width), Math.round(y + height), color);
            return;
        }

        // All HUD fills share the gradient's one-pixel contour, including the
        // no-blur path. No standalone shader flush or framebuffer coordinates.
        vorga.phazeclient.api.system.hud.HudBackgroundGeometryRenderer.solidRounded(context, x, y, width, height, color, radius);
    }

    /**
     * Draws the four gradient corners through DrawContext's GUI batch instead
     * of the independent rounded-rect shader. That keeps the background in
     * the same draw order as cached blur and avoids the white shader fallback
     * seen on some Sodium/Iris render paths. The strips are queued into the
     * same GUI batch, not uploaded as separate textures or framebuffers.
     */
    static void renderGradientRect(
            DrawContext context,
            float x,
            float y,
            float width,
            float height,
            int hoverFill,
            int startColor,
            int endColor,
            String direction,
            float animationOffset,
            boolean animated,
            float requestedRadius
    ) {
        if (hoverFill != 0) {
            startColor = blendARGB(startColor, hoverFill);
            endColor = blendARGB(endColor, hoverFill);
        }
        int left = Math.round(x);
        int top = Math.round(y);
        int right = Math.max(left + 1, Math.round(x + width));
        int bottom = Math.max(top + 1, Math.round(y + height));
        String selectedDirection = direction == null ? "Left to Right" : direction;
        float radius = getClampedHudCornerRadius(requestedRadius, width, height);

        if (radius > 0.01F) {
            renderRoundedGradient(context, x, y, x + width, y + height, startColor, endColor,
                    selectedDirection, animationOffset, animated, radius);
            return;
        }

        if (!animated) {
            renderStaticGradient(context, left, top, right, bottom, startColor, endColor, selectedDirection);
            return;
        }

        if ("Pulse".equals(selectedDirection)) {
            context.fill(left, top, right, bottom, movingGradientColor(startColor, endColor, animationOffset));
            return;
        }

        if ("Top to Bottom".equals(selectedDirection) || "Bottom to Top".equals(selectedDirection)) {
            float directionSign = "Top to Bottom".equals(selectedDirection) ? -1.0F : 1.0F;
            renderMovingAxisGradient(context, left, top, right, bottom, startColor, endColor,
                    directionSign * animationOffset, false);
            return;
        }

        if ("Left to Right".equals(selectedDirection) || "Right to Left".equals(selectedDirection)) {
            float directionSign = "Right to Left".equals(selectedDirection) ? 1.0F : -1.0F;
            renderMovingAxisGradient(context, left, top, right, bottom, startColor, endColor,
                    directionSign * animationOffset, true);
            return;
        }

        vorga.phazeclient.api.system.hud.HudBackgroundGeometryRenderer.movingDiagonal(context, left, top, right, bottom,
                startColor, endColor, selectedDirection, animationOffset);
    }

    static void renderRoundedGradient(
            DrawContext context,
            float left,
            float top,
            float right,
            float bottom,
            int startColor,
            int endColor,
            String direction,
            float animationOffset,
            boolean animated,
            float radius
    ) {
        vorga.phazeclient.api.system.hud.HudBackgroundGeometryRenderer.roundedGradient(context, left, top, right, bottom,
                startColor, endColor, direction, animationOffset, animated, radius);
    }

    static void renderStaticGradient(
            DrawContext context,
            int left,
            int top,
            int right,
            int bottom,
            int startColor,
            int endColor,
            String direction
    ) {
        vorga.phazeclient.api.system.hud.HudBackgroundGeometryRenderer.staticGradient(context, left, top, right, bottom,
                startColor, endColor, direction);
    }

    /**
     * Renders one travelling triangular color wave with hardware-interpolated
     * segments. At most four quads are needed for the whole HUD, regardless
     * of its size, so there are no visible strip boundaries.
     */
    static void renderMovingAxisGradient(
            DrawContext context,
            float left,
            float top,
            float right,
            float bottom,
            int startColor,
            int endColor,
            float phase,
            boolean horizontal
    ) {
        float position = 0.0F;
        for (int segment = 0; segment < 4 && position < 0.99999F; segment++) {
            float coordinate = position + phase;
            float nextStop = ((float) Math.floor(coordinate * 2.0F + 0.00001F) + 1.0F) * 0.5F - phase;
            float endPosition = MathHelper.clamp(nextStop, position + 0.0001F, 1.0F);
            int fromColor = movingLinearGradientColor(startColor, endColor, coordinate);
            int toColor = movingLinearGradientColor(startColor, endColor, endPosition + phase);

            if (horizontal) {
                float x = MathHelper.lerp(position, left, right);
                float width = MathHelper.lerp(endPosition, left, right) - x;
                drawHorizontalGradient(context, x, top, width, bottom - top, fromColor, toColor);
            } else {
                float y = MathHelper.lerp(position, top, bottom);
                float height = MathHelper.lerp(endPosition, top, bottom) - y;
                drawVerticalGradient(context, left, y, right - left, height, fromColor, toColor);
            }
            position = endPosition;
        }
    }

    static void drawVerticalGradient(
            DrawContext context,
            float x,
            float y,
            float width,
            float height,
            int startColor,
            int endColor
    ) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        vorga.phazeclient.api.system.hud.HudBackgroundGeometryRenderer.quad(context, x, y, x + width, y + height,
                startColor, endColor, endColor, startColor);
    }

    static void drawHorizontalGradient(
            DrawContext context,
            float x,
            float y,
            float width,
            float height,
            int startColor,
            int endColor
    ) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        vorga.phazeclient.api.system.hud.HudBackgroundGeometryRenderer.quad(context, x, y, x + width, y + height,
                startColor, startColor, endColor, endColor);
    }

    /** Continuous start -> end -> start wave; shifting its phase moves it. */
    static int movingGradientColor(int startColor, int endColor, float coordinate) {
        return vorga.phazeclient.api.system.hud.HudBackgroundGeometryGradients.moving(startColor, endColor, coordinate);
    }

    static int movingLinearGradientColor(int startColor, int endColor, float coordinate) {
        float wrapped = coordinate - (float) Math.floor(coordinate);
        float blend = wrapped < 0.5F ? wrapped * 2.0F : (1.0F - wrapped) * 2.0F;
        return lerpGradientColor(startColor, endColor, blend);
    }

    static int lerpGradientColor(int first, int second, float progress) {
        return vorga.phazeclient.api.system.hud.HudBackgroundGeometryGradients.lerp(first, second, progress);
    }

    static void drawOutlineNoOverlap(DrawContext context, int x, int y, int width, int height, int color) {
        if (width <= 2 || height <= 2) {
            return;
        }
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        if (height > 2) {
            context.fill(x, y + 1, x + 1, y + height - 1, color);
            context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
        }
    }

    static void drawOuterOutline(DrawContext context, float x, float y, float width, float height, int thickness, int color) {
        if (thickness <= 0) {
            return;
        }
        int innerLeft = MathHelper.floor(x);
        int innerTop = MathHelper.floor(y);
        int innerRight = MathHelper.ceil(x + width);
        int innerBottom = MathHelper.ceil(y + height);

        int outerLeft = innerLeft - thickness;
        int outerTop = innerTop - thickness;
        int outerRight = innerRight + thickness;
        int outerBottom = innerBottom + thickness;

        context.fill(outerLeft, outerTop, outerRight, innerTop, color);
        context.fill(outerLeft, innerBottom, outerRight, outerBottom, color);
        context.fill(outerLeft, innerTop, innerLeft, innerBottom, color);
        context.fill(innerRight, innerTop, outerRight, innerBottom, color);
    }

    static int blendARGB(int base, int over) {
        float oa = ((over >>> 24) & 0xFF) / 255.0f;
        if (oa <= 0.0f) {
            return base;
        }
        float ba = ((base >>> 24) & 0xFF) / 255.0f;
        float outA = oa + ba * (1.0f - oa);
        if (outA <= 0.0f) {
            return 0;
        }

        int br = (base >>> 16) & 0xFF;
        int bg = (base >>> 8) & 0xFF;
        int bb = base & 0xFF;
        int or = (over >>> 16) & 0xFF;
        int og = (over >>> 8) & 0xFF;
        int ob = over & 0xFF;

        int outR = MathHelper.clamp(Math.round((or * oa + br * ba * (1.0f - oa)) / outA), 0, 255);
        int outG = MathHelper.clamp(Math.round((og * oa + bg * ba * (1.0f - oa)) / outA), 0, 255);
        int outB = MathHelper.clamp(Math.round((ob * oa + bb * ba * (1.0f - oa)) / outA), 0, 255);
        int outAlpha = MathHelper.clamp(Math.round(outA * 255.0f), 0, 255);
        return (outAlpha << 24) | (outR << 16) | (outG << 8) | outB;
    }

    static int approachColorExp(int current, int target, float speed, float dt) {
        float t = 1.0f - (float) Math.exp(-speed * dt);

        int ca = (current >>> 24) & 0xFF;
        int cr = (current >>> 16) & 0xFF;
        int cg = (current >>> 8) & 0xFF;
        int cb = current & 0xFF;

        int ta = (target >>> 24) & 0xFF;
        int tr = (target >>> 16) & 0xFF;
        int tg = (target >>> 8) & 0xFF;
        int tb = target & 0xFF;

        int a = MathHelper.clamp(Math.round(ca + (ta - ca) * t), 0, 255);
        int r = MathHelper.clamp(Math.round(cr + (tr - cr) * t), 0, 255);
        int g = MathHelper.clamp(Math.round(cg + (tg - cg) * t), 0, 255);
        int b = MathHelper.clamp(Math.round(cb + (tb - cb) * t), 0, 255);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
