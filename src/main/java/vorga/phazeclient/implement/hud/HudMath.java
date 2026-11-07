package vorga.phazeclient.implement.hud;

import net.minecraft.util.math.MathHelper;

import static vorga.phazeclient.implement.hud.HudInfoText.*;
import static vorga.phazeclient.implement.hud.HudPaint.*;
import static vorga.phazeclient.implement.hud.HudTextDraw.*;

/** Pure geometry and easing helpers for HUD layout and animation. */
final class HudMath {
    private HudMath() {}

    static float wrap360(float angle) {
        float a = angle % 360.0f;
        if (a < 0.0f) a += 360.0f;
        return a;
    }

    static float shortestAngleDeg(float target, float current) {
        float diff = (target - current) % 360.0f;
        if (diff > 180.0f) diff -= 360.0f;
        if (diff < -180.0f) diff += 360.0f;
        return diff;
    }

    static float approachAngle(float current, float target, float step) {
        float diff = shortestAngleDeg(target, current);
        return wrap360(current + diff * MathHelper.clamp(step, 0.0f, 1.0f));
    }

    static boolean isHovered(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    static boolean isNearRect(double mouseX, double mouseY, float x, float y, float width, float height, float distance) {
        return mouseX >= x - distance
                && mouseX <= x + width + distance
                && mouseY >= y - distance
                && mouseY <= y + height + distance;
    }

    static float directionEdgeFade(float x, float width) {
        float fadeWidth = Math.max(18.0f, width * 0.16f);
        float left = MathHelper.clamp((x - 4.0f) / fadeWidth, 0.0f, 1.0f);
        float right = MathHelper.clamp((width - 4.0f - x) / fadeWidth, 0.0f, 1.0f);
        return smoothStep(left) * smoothStep(right);
    }

    static float smoothStep(float value) {
        float t = MathHelper.clamp(value, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    static float approachExp(float current, float target, float speed, float dt) {
        float t = 1.0f - (float) Math.exp(-speed * dt);
        return current + (target - current) * t;
    }
}
