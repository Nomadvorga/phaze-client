package vorga.phazeclient.implement.hud.vanilla;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import vorga.phazeclient.helpers.ColorHelper;
import vorga.phazeclient.helpers.TextureHelper;
import vorga.phazeclient.implement.features.modules.other.Saturation;

/** Saturation pips over the vanilla hunger bar, plus the air-bubble lift for the "Second Hunger Bar" style. */
public final class SaturationOverlay {
    private static final int BUBBLE_LIFT_PX = 10;
    private static final int ICON_SIZE = 9;

    private static boolean bubblesLifted = false;

    private SaturationOverlay() {}

    public static void renderStatusBars(DrawContext context) {
        if (!Saturation.getInstance().isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;
        if (player == null) return;

        float saturationLevel = player.getHungerManager().getSaturationLevel();
        if (saturationLevel <= 0) return;

        int right = context.getScaledWindowWidth() / 2 + 91;
        int top = context.getScaledWindowHeight() - 39;
        drawSaturation(context, saturationLevel, 0, 1.0F, right, top);
    }

    public static void beforeAirBubbles(DrawContext context) {
        if (shouldLiftBubbles()) {
            context.getMatrices().push();
            context.getMatrices().translate(0.0F, -BUBBLE_LIFT_PX, 0.0F);
            bubblesLifted = true;
        }
    }

    public static void afterAirBubbles(DrawContext context) {
        if (bubblesLifted) {
            context.getMatrices().pop();
            bubblesLifted = false;
        }
    }

    private static boolean shouldLiftBubbles() {
        Saturation sat = Saturation.getInstance();
        if (sat == null || !sat.isEnabled()) return false;
        return "Second Hunger Bar".equals(sat.style.getSelected());
    }

    private static void drawSaturation(DrawContext context, float saturationLevel, float saturationGained, float alpha, int right, int top) {
        if (saturationLevel + saturationGained < 0) return;
        int alphaColor = ColorHelper.argbFromRGBA(1.0F, 1.0F, 1.0F, alpha);
        float modifiedSaturation = Math.max(0, Math.min(saturationLevel + saturationGained, 20));
        int startSaturationBar = 0;
        int endSaturationBar = (int) Math.ceil(modifiedSaturation / 2.0F);
        if (saturationGained != 0) startSaturationBar = (int) Math.max(saturationLevel / 2.0F, 0);

        int saturationTop = top - 10;
        boolean isYellowBar = Saturation.getInstance().style.getSelected().equals("Yellow Bar");

        for (int i = startSaturationBar; i < endSaturationBar; ++i) {
            int x = right - i * 8 - 9;
            float effectiveSaturationOfBar = (modifiedSaturation / 2.0F) - i;

            if (!isYellowBar) {
                if (effectiveSaturationOfBar <= 0) continue;
                Identifier foodTexture = null;
                if (effectiveSaturationOfBar >= 1) {
                    foodTexture = TextureHelper.FOOD_FULL_TEXTURE;
                } else if (effectiveSaturationOfBar >= .5) {
                    foodTexture = TextureHelper.FOOD_HALF_TEXTURE;
                }
                if (foodTexture == null) continue;
                context.drawGuiTexture(RenderLayer::getGuiTextured, TextureHelper.FOOD_EMPTY_TEXTURE, x, saturationTop, ICON_SIZE, ICON_SIZE);
                context.drawGuiTexture(RenderLayer::getGuiTextured, foodTexture, x, saturationTop, ICON_SIZE, ICON_SIZE);
            } else {
                int u = 0;
                if (effectiveSaturationOfBar >= 1) u = 3 * ICON_SIZE;
                else if (effectiveSaturationOfBar > .5) u = 2 * ICON_SIZE;
                else if (effectiveSaturationOfBar > .25) u = ICON_SIZE;
                context.drawTexture(RenderLayer::getGuiTextured, TextureHelper.MOD_ICONS, x, top, u, 0, ICON_SIZE, ICON_SIZE, 256, 256, alphaColor);
            }
        }
    }
}
