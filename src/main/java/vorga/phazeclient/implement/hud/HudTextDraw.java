package vorga.phazeclient.implement.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import vorga.phazeclient.implement.features.modules.client.Theme;
import java.util.LinkedHashMap;
import java.util.Map;

import static vorga.phazeclient.implement.hud.HudInfoText.*;
import static vorga.phazeclient.implement.hud.HudPaint.*;
import static vorga.phazeclient.implement.hud.HudStyle.*;

/** HUD text measurement (with a bounded width cache) and MSDF text drawing. */
final class HudTextDraw {
    private HudTextDraw() {}

    static final int HUD_TEXT_WIDTH_CACHE_MAX = 512;

    static final Map<String, Float> HUD_TEXT_WIDTH_CACHE = new LinkedHashMap<>(HUD_TEXT_WIDTH_CACHE_MAX, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Float> eldest) {
            return size() > HUD_TEXT_WIDTH_CACHE_MAX;
        }
    };

    static float getHudTextWidth(MinecraftClient client, String text, float textSize) {
        String cacheKey = "V|" + textSize + "|" + text;
        Float cached = HUD_TEXT_WIDTH_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        float width = client.textRenderer.getWidth(text);
        HUD_TEXT_WIDTH_CACHE.put(cacheKey, width);
        return width;
    }

    static float getTextHudBaseWidth(MinecraftClient client, String text) {
        float width = Math.max(44.0f, getHudTextWidth(client, text, HUD_TEXT_SIZE) + 14.0f);
        // Add 2 pixels to background width when text contains "A"
        if (text.contains("A") || text.contains("a")) {
            width += 2.0f;
        }
        return width;
    }

    static void renderHudTextWithAlpha(DrawContext context, MinecraftClient client, String text, float x, float y, float textSize, boolean shadow, int alpha) {
        renderHudTextColoredWithAlpha(context, client, text, x, y, textSize, shadow, resolveHudTextColor() & 0x00FFFFFF, alpha);
    }

    static int resolveHudTextColor() {
        Theme theme = Theme.getInstance();
        if (theme == null) {
            return HUD_TEXT_COLOR;
        }
        return theme.getHudTextColor();
    }

    static void renderHudTextColoredWithAlpha(DrawContext context, MinecraftClient client, String text, float x, float y, float textSize, boolean shadow, int rgbColor, int alpha) {
        int textColor = (MathHelper.clamp(alpha, 0, 255) << 24) | (rgbColor & 0x00FFFFFF);
        context.drawText(client.textRenderer, text, Math.round(x), Math.round(y), textColor, shadow);
    }

    static void renderHudText(DrawContext context, MinecraftClient client, String text, float x, float y, float msdfSize, boolean shadow) {
        renderHudTextWithAlpha(context, client, text, x, y, msdfSize, shadow, 255);
    }

    static void renderHudTextColored(DrawContext context, MinecraftClient client, String text, float x, float y, float msdfSize, boolean shadow, int rgbColor) {
        renderHudTextColoredWithAlpha(context, client, text, x, y, msdfSize, shadow, rgbColor, 255);
    }

    static void renderScaledHudText(
            DrawContext context,
            MinecraftClient client,
            String text,
            float hudX,
            float hudY,
            float textX,
            float textY,
            float textSize,
            float scale,
            boolean shadow
    ) {
        context.getMatrices().push();
        context.getMatrices().translate(hudX, hudY, HUD_RENDER_Z + 25.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        renderHudText(context, client, text, textX, textY, textSize, shadow);
        context.getMatrices().pop();
    }

    static void renderScaledHudTextWithAlpha(
            DrawContext context,
            MinecraftClient client,
            String text,
            float hudX,
            float hudY,
            float textX,
            float textY,
            float textSize,
            float scale,
            boolean shadow,
            int alpha
    ) {
        context.getMatrices().push();
        context.getMatrices().translate(hudX, hudY, HUD_RENDER_Z + 25.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        renderHudTextWithAlpha(context, client, text, textX, textY, textSize, shadow, alpha);
        context.getMatrices().pop();
    }

    static void renderScaledHudTextColored(
            DrawContext context,
            MinecraftClient client,
            String text,
            float hudX,
            float hudY,
            float textX,
            float textY,
            float textSize,
            float scale,
            boolean shadow,
            int rgbColor
    ) {
        context.getMatrices().push();
        context.getMatrices().translate(hudX, hudY, HUD_RENDER_Z + 25.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        renderHudTextColored(context, client, text, textX, textY, textSize, shadow, rgbColor);
        context.getMatrices().pop();
    }
}
