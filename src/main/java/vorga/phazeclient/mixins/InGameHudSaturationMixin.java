package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.helpers.ColorHelper;
import vorga.phazeclient.helpers.IntPoint;
import vorga.phazeclient.helpers.TextureHelper;
import vorga.phazeclient.implement.features.modules.other.Saturation;

import java.util.Random;
import java.util.Vector;

@Mixin(InGameHud.class)
public class InGameHudSaturationMixin {

    @Unique
    private float unclampedFlashAlpha = 0f;
    @Unique
    private float flashAlpha = 0f;
    @Unique
    private byte alphaDir = 1;
    @Unique
    private final OffsetsCache barOffsets = new OffsetsCache();
    @Unique
    private final HeldFoodCache heldFood = new HeldFoodCache();

    @Inject(method = "renderStatusBars", at = @At("TAIL"))
    private void onRenderStatusBars(DrawContext context, CallbackInfo ci) {
        if (!Saturation.getInstance().isEnabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        PlayerEntity player = client.player;
        HungerManager stats = player.getHungerManager();
        float saturationLevel = stats.getSaturationLevel();

        if (saturationLevel <= 0) {
            resetFlash();
            return;
        }

        // Get hunger bar position from vanilla
        int right = context.getScaledWindowWidth() / 2 + 91;
        int top = context.getScaledWindowHeight() - 39;

        // Draw saturation overlay
        drawSaturationOverlay(context, saturationLevel, 0, 1.0F, client.inGameHud.getTicks(), right, top);

        // Handle held food
        ItemStack heldItem = player.getMainHandStack();
        if (heldItem.isEmpty()) {
            heldItem = player.getOffHandStack();
        }

        if (heldItem.isEmpty() || !heldItem.contains(net.minecraft.component.DataComponentTypes.FOOD)) {
            resetFlash();
            return;
        }

        // Flash animation for held food
        onClientTick();
    }

    @Unique
    private void drawSaturationOverlay(DrawContext context, float saturationLevel, float saturationGained, float alpha, int guiTicks, int right, int top) {
        if (saturationLevel + saturationGained < 0) {
            return;
        }

        int alphaColor = ColorHelper.argbFromRGBA(1.0F, 1.0F, 1.0F, alpha);
        int shadowAlphaColor = ColorHelper.argbFromRGBA(0.0F, 0.0F, 0.0F, alpha * 0.5F);
        float modifiedSaturation = Math.max(0, Math.min(saturationLevel + saturationGained, 20));

        int startSaturationBar = 0;
        int endSaturationBar = (int) Math.ceil(modifiedSaturation / 2.0F);

        if (saturationGained != 0) {
            startSaturationBar = (int) Math.max(saturationLevel / 2.0F, 0);
        }

        int iconSize = 9;
        int saturationTop = top - 10;

        String styleValue = Saturation.getInstance().style.getSelected();
        boolean isYellowBar = styleValue.equals("Yellow Bar");

        for (int i = startSaturationBar; i < endSaturationBar; ++i) {
            int x = right - i * 8 - 9;
            int y = saturationTop;

            // Calculate saturation level for this bar
            float effectiveSaturationOfBar = (modifiedSaturation / 2.0F) - i;

            if (!isYellowBar) {
                // SECOND_HUNGER_BAR style - use vanilla food texture with shadow/background
                // Skip rendering if saturation is empty
                if (effectiveSaturationOfBar <= 0) {
                    continue;
                }

                // Draw filled texture only, no background
                Identifier foodTexture = null;
                if (effectiveSaturationOfBar >= 1) {
                    foodTexture = TextureHelper.FOOD_FULL_TEXTURE;
                } else if (effectiveSaturationOfBar >= .5) {
                    foodTexture = TextureHelper.FOOD_HALF_TEXTURE;
                }
                // Only render if full or half
                if (foodTexture == null) {
                    continue;
                }
                // Draw shadow/background first
                context.drawGuiTexture(RenderLayer::getGuiTextured, TextureHelper.FOOD_EMPTY_TEXTURE, x, y, iconSize, iconSize);
                // Draw filled texture on top
                context.drawGuiTexture(RenderLayer::getGuiTextured, foodTexture, x, y, iconSize, iconSize);
            } else {
                // YELLOW_BAR style - draw saturation icons from AppleSkin icons.png on hunger bar
                int hungerY = top;
                int v = 0;
                int u = 0;

                if (effectiveSaturationOfBar >= 1) {
                    u = 3 * iconSize;
                } else if (effectiveSaturationOfBar > .5) {
                    u = 2 * iconSize;
                } else if (effectiveSaturationOfBar > .25) {
                    u = 1 * iconSize;
                }

                // Draw from icons.png exactly like AppleSkin but on hunger bar position
                context.drawTexture(RenderLayer::getGuiTextured, TextureHelper.MOD_ICONS, x, hungerY, u, v, iconSize, iconSize, 256, 256, alphaColor);
            }
        }
    }

    @Unique
    private void onClientTick() {
        unclampedFlashAlpha += alphaDir * 0.125F;
        if (unclampedFlashAlpha >= 1.5F) {
            alphaDir = -1;
        } else if (unclampedFlashAlpha <= -0.5F) {
            alphaDir = 1;
        }
        flashAlpha = Math.max(0F, Math.min(1F, unclampedFlashAlpha)) * Math.max(0F, Math.min(1F, 1.0F));
    }

    @Unique
    private void resetFlash() {
        unclampedFlashAlpha = flashAlpha = 0;
        alphaDir = 1;
    }

    @Unique
    private static class OffsetsCache {
        private final Vector<IntPoint> foodBarOffsets = new Vector<>();
        private final Vector<IntPoint> healthBarOffsets = new Vector<>();
        private int lastGuiTick = 0;
        private final Random random = new Random();

        private void generate(int guiTicks, PlayerEntity player) {
            final int preferFoodBars = 10;

            // Generate food bar offsets
            if (foodBarOffsets.size() != preferFoodBars) {
                foodBarOffsets.setSize(preferFoodBars);
            }

            // Hard code from InGameHUD
            random.setSeed((long) (guiTicks * 312871));

            // Right alignment, single row
            for (int i = 0; i < preferFoodBars; ++i) {
                int x = -(i * 8) - 9;
                int y = 0;

                IntPoint point = foodBarOffsets.get(i);
                if (point == null) {
                    point = new IntPoint();
                    foodBarOffsets.set(i, point);
                }

                point.x = x;
                point.y = y;
            }

            lastGuiTick = guiTicks;
        }

        public Vector<IntPoint> foodBarOffsets(int guiTicks, PlayerEntity player) {
            if (guiTicks != lastGuiTick) {
                generate(guiTicks, player);
            }
            return this.foodBarOffsets;
        }
    }

    @Unique
    private static class HeldFoodCache {
        private ItemStack lastHeldItem;
        private int lastGuiTick = 0;

        public ItemStack result(int guiTick, PlayerEntity player) {
            if (guiTick != lastGuiTick) {
                ItemStack heldItem = player.getMainHandStack();
                if (heldItem.isEmpty()) {
                    heldItem = player.getOffHandStack();
                }
                lastHeldItem = heldItem;
                lastGuiTick = guiTick;
            }
            return lastHeldItem;
        }
    }
}
