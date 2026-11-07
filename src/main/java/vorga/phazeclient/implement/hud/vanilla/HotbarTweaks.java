package vorga.phazeclient.implement.hud.vanilla;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import vorga.phazeclient.api.system.hud.ExordiumAnimationBridge;
import vorga.phazeclient.api.system.hud.HotbarSelectionRenderer;
import vorga.phazeclient.api.system.hud.HotbarSlideAnimation;
import vorga.phazeclient.implement.features.modules.hud.Cooldowns;
import vorga.phazeclient.implement.features.modules.other.Animations;
import vorga.phazeclient.implement.features.modules.other.HealingHelper;
import vorga.phazeclient.implement.features.modules.other.ItemHighlighter;
import vorga.phazeclient.implement.features.modules.other.MaceIndicator;
import vorga.phazeclient.implement.hud.CustomHudRenderer;

import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;

/** Hotbar additions: cooldown numbers, the sliding selector and per-slot utility highlights. */
public final class HotbarTweaks {
    private static final int SLOT_PX = 20;
    private static final int BG_W = 182;
    private static final int BG_H = 22;
    private static final HotbarSlideAnimation SLIDE = new HotbarSlideAnimation();
    private static final DoubleUnaryOperator SMOOTHNESS =
            speed -> Animations.getInstance().smoothnessForSpeed((float) speed);

    /** Draws the vanilla selector sprite at the given x; supplied by the caller's original draw call. */
    @FunctionalInterface
    public interface SelectionDraw {
        void drawAt(int x);
    }

    private HotbarTweaks() {}

    public static void drawCooldownNumber(DrawContext context, int x, int y, float tickDelta, PlayerEntity player, ItemStack stack) {
        Cooldowns module = Cooldowns.getInstance();
        if (module == null || !module.isEnabled()) return;
        if (stack == null || stack.isEmpty() || player == null) return;
        ItemCooldownManager manager = player.getItemCooldownManager();
        if (manager == null) return;
        float progress = manager.getCooldownProgress(stack, tickDelta);
        if (progress <= 0.0F) return;

        Identifier groupId = manager.getGroup(stack);
        ItemCooldownManager.Entry entry = manager.entries.get(groupId);
        if (entry == null) return;
        float remainingTicks = entry.endTick - (manager.tick + tickDelta);
        if (remainingTicks <= 0.0F) return;
        float remainingSeconds = remainingTicks / 20.0F;

        String text = module.formatSeconds(remainingSeconds);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.textRenderer == null) return;
        int color = module.colorForProgress(progress);
        int textWidth = mc.textRenderer.getWidth(text);
        int drawX = x + 8 - textWidth / 2;
        int drawY = y + 1;
        // +200 Z keeps the digits above the 3D item glyph, the same trick vanilla uses for stack counts.
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0.0F, 0.0F, 200.0F);
        context.drawText(mc.textRenderer, text, drawX, drawY, color, module.textShadow.isValue());
        // Flush under this Z so the pop doesn't leave the digits behind later hotbar overlays.
        context.draw();
        matrices.pop();
    }

    public static void advanceSlide() {
        Animations module = Animations.getInstance();
        if (module == null || !module.isHotbarSlideEnabled()) {
            SLIDE.resetTracking();
            ExordiumAnimationBridge.updateHotbarAnimation(0.0F, 0.0F, false, 0);
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) {
            SLIDE.resetTracking();
            ExordiumAnimationBridge.updateHotbarAnimation(0.0F, 0.0F, false, 0);
            return;
        }

        int selected = mc.player.getInventory().selectedSlot;
        float target = selected * SLOT_PX;

        SLIDE.advance(selected, module.isHotbarRolloverEnabled(), module.hotbarSpeed.getValue(),
                System.nanoTime(), SMOOTHNESS);
        ExordiumAnimationBridge.updateHotbarAnimation(
                SLIDE.currentX(),
                target,
                SLIDE.mirrorOffset() != 0,
                SLIDE.mirrorOffset()
        );
    }

    public static void renderSelection(DrawContext context, Function<Identifier, RenderLayer> layerFactory,
                                       Identifier texture, int x, int y, int width, int height, SelectionDraw original) {
        Animations module = Animations.getInstance();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (module == null || !module.isHotbarSlideEnabled() || mc == null || mc.player == null) {
            original.drawAt(x);
            return;
        }

        if (ExordiumAnimationBridge.isCapturingHotbar()) {
            ExordiumAnimationBridge.recordHotbarSelection(layerFactory, texture, x, y, width, height);
            // The cached layer deliberately contains the expensive hotbar
            // background/items but not the selection frame. The frame is one
            // cheap live sprite, so it can move at display FPS over the cache.
            return;
        }

        int selectedSlot = mc.player.getInventory().selectedSlot;
        int newX = x - selectedSlot * SLOT_PX + Math.round(SLIDE.currentX());
        int mirrorOffset = SLIDE.mirrorOffset();
        if (mirrorOffset == 0) {
            original.drawAt(newX);
            return;
        }
        int left = context.getScaledWindowWidth() / 2 - BG_W / 2;
        int top = context.getScaledWindowHeight() - BG_H;
        HotbarSelectionRenderer.drawClipped(context, layerFactory, texture, newX, y, width, height,
                left, top, left + BG_W, top + BG_H);
        HotbarSelectionRenderer.drawClipped(context, layerFactory, texture, newX + mirrorOffset, y, width, height,
                left, top, left + BG_W, top + BG_H);
    }

    public static void drawUtilityHighlights(DrawContext context) {
        MaceIndicator mace = MaceIndicator.getInstance();
        ItemHighlighter highlighter = ItemHighlighter.getInstance();
        HealingHelper healing = HealingHelper.getInstance();
        boolean maceEnabled = mace != null && mace.isEnabled();
        boolean highlighterEnabled = highlighter != null && highlighter.isEnabled();
        boolean healingEnabled = healing != null && healing.isEnabled();
        if (!maceEnabled && !highlighterEnabled && !healingEnabled) return;
        if (highlighterEnabled) highlighter.beginRenderPass();
        if (healingEnabled) healing.beginRenderPass();
        paintUtilityFills(
                context,
                maceEnabled ? mace : null,
                highlighterEnabled ? highlighter : null,
                healingEnabled ? healing : null
        );
    }

    public static void flush(DrawContext context) {
        context.draw();
        CustomHudRenderer.resetGuiRenderState();
    }

    private static void paintUtilityFills(DrawContext context, MaceIndicator mace, ItemHighlighter highlighter, HealingHelper healing) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        PlayerEntity player = mc.player;

        int centerX = context.getScaledWindowWidth() / 2;
        int itemY = context.getScaledWindowHeight() - 16 - 3;

        context.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        for (int n = 0; n < 9; n++) {
            ItemStack stack = player.getInventory().main.get(n);
            int itemX = centerX - 90 + n * 20 + 2;
            if (mace != null) {
                fillSlot(context, itemX, itemY, mace.colorForStack(stack));
            }
            if (highlighter != null) {
                fillSlot(context, itemX, itemY, highlighter.colorForPreparedStack(stack));
            }
            if (healing != null) {
                fillSlot(context, itemX, itemY, healing.colorForPreparedStack(stack));
            }
        }
        context.draw();
        RenderSystem.depthMask(true);
        CustomHudRenderer.resetGuiRenderState();
    }

    private static void fillSlot(DrawContext context, int x, int y, int color) {
        if ((color & 0xFF000000) != 0) {
            context.fill(x, y, x + 16, y + 16, color);
        }
    }
}
