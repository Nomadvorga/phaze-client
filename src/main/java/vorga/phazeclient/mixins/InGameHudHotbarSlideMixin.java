package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import vorga.phazeclient.implement.features.modules.other.Animations;

import java.util.function.Function;

/**
 * Animates the hotbar selection sprite ("hud/hotbar_selection") so that:
 *  - Switching slots slides the indicator with frame-rate independent
 *    exponential decay (same maths as the rest of the Animations module).
 *  - With Hotbar Rollover on, going from slot 8 to slot 0 (or vice versa)
 *    no longer teleports across the entire hotbar - the indicator slides
 *    OFF one edge and a mirrored copy slides IN from the other edge in
 *    sync, clipped to the hotbar's bounding box via scissor so neither
 *    half "leaks" outside the hotbar background. Visually identical to
 *    the smooth-scrolling reference, written from scratch with our own
 *    state and shared {@code smoothnessForSpeed} mapping.
 *
 * Pipeline at the selection sprite's {@code drawGuiTexture} call (ordinal=1
 * inside renderHotbar):
 *   HEAD  - reset mirror flag, run the per-frame tick (only when enabled),
 *           detect whether a rollover is currently in flight, decide if a
 *           mirror is needed.
 *   BEFORE INVOKE - if a mirror is needed, enable a scissor clipping the
 *                   draw to the visible hotbar bounding box so the off-edge
 *                   half of either copy doesn't escape.
 *   ModifyArgs    - rewrite the X argument to our smoothed value.
 *   AFTER INVOKE  - if a mirror is needed, draw the second copy at +/-
 *                   9*20 from the smoothed X, then flush + disable scissor.
 *
 * The Animations-disabled path resets {@code phaze$shouldDrawMirror}
 * unconditionally at HEAD so a stale flag from a previous session can't
 * leak through and cause a flicker.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudHotbarSlideMixin {

    @Unique private static final int SLOT_PIXEL_WIDTH = 20;
    @Unique private static final int HOTBAR_SLOTS = 9;
    @Unique private static final int HOTBAR_PIXEL_WIDTH = SLOT_PIXEL_WIDTH * HOTBAR_SLOTS;
    @Unique private static final int HOTBAR_BG_WIDTH_PX = 182;
    @Unique private static final int HOTBAR_BG_HEIGHT_PX = 22;

    @Unique private float phaze$currentSlotX = 0.0F;
    @Unique private int phaze$lastSelectedSlot = -1;
    @Unique private long phaze$lastFrameNanos = 0L;

    @Unique private boolean phaze$shouldDrawMirror = false;
    @Unique private int phaze$mirrorOffsetX = 0;
    @Unique private int phaze$lastDrawX;
    @Unique private int phaze$lastDrawY;
    @Unique private int phaze$lastWidth;
    @Unique private int phaze$lastHeight;
    @Unique private Identifier phaze$lastTexture;
    @Unique private Function<Identifier, ?> phaze$lastSpriteFn;
    @Unique private boolean phaze$scissorOn = false;

    @Inject(method = "renderHotbar", at = @At("HEAD"))
    private void phaze$tickHotbarSlide(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        // Always reset the mirror flag at the very top of the frame: this
        // is what fixes the "flickers even when Animations is off" issue.
        // Previously, if the user toggled Animations off mid-rollover, the
        // flag could remain true and the AFTER inject would draw a stale
        // mirror sprite at coordinates from the last enabled frame.
        phaze$shouldDrawMirror = false;
        phaze$scissorOn = false;

        Animations module = Animations.getInstance();
        if (module == null || !module.isHotbarSlideEnabled()) {
            phaze$lastSelectedSlot = -1;
            phaze$lastFrameNanos = 0L;
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }

        int selected = mc.player.getInventory().selectedSlot;
        float target = selected * SLOT_PIXEL_WIDTH;

        // Wrap detection - shifts currentSlotX to the OTHER side of the
        // upcoming target so the smoothed slide goes the short distance
        // through the wrap boundary instead of all the way back through
        // the middle of the hotbar.
        if (module.isHotbarRolloverEnabled() && phaze$lastSelectedSlot >= 0) {
            int slotDelta = selected - phaze$lastSelectedSlot;
            if (slotDelta >= 5) {
                phaze$currentSlotX += HOTBAR_PIXEL_WIDTH;
            } else if (slotDelta <= -5) {
                phaze$currentSlotX -= HOTBAR_PIXEL_WIDTH;
            }
        }
        phaze$lastSelectedSlot = selected;

        long now = System.nanoTime();
        float dt;
        if (phaze$lastFrameNanos == 0L) {
            dt = 1.0F / 60.0F;
        } else {
            dt = (now - phaze$lastFrameNanos) / 1_000_000_000.0F;
            if (dt > 0.25F) dt = 0.25F;
        }
        phaze$lastFrameNanos = now;

        float smoothness = module.smoothnessForSpeed(module.hotbarSpeed.getValue());
        float decay = (float) Math.pow(smoothness, dt);
        phaze$currentSlotX = (phaze$currentSlotX - target) * decay + target;

        if (Math.abs(phaze$currentSlotX - target) < 0.4F) {
            phaze$currentSlotX = target;
        }

        // Decide whether the indicator currently spans a hotbar boundary -
        // i.e. is being drawn partly outside [slot 0, slot 8]. If so we
        // need a mirror on the OPPOSITE side so the user sees a continuous
        // slide across the wrap rather than a pop-in.
        if (module.isHotbarRolloverEnabled()) {
            float maxNormalX = (HOTBAR_SLOTS - 1) * SLOT_PIXEL_WIDTH;
            if (phaze$currentSlotX < 0.0F) {
                phaze$shouldDrawMirror = true;
                phaze$mirrorOffsetX = HOTBAR_PIXEL_WIDTH;
            } else if (phaze$currentSlotX > maxNormalX) {
                phaze$shouldDrawMirror = true;
                phaze$mirrorOffsetX = -HOTBAR_PIXEL_WIDTH;
            }
        }
    }

    /**
     * Right before vanilla draws the selection sprite, enable a scissor
     * box that exactly matches the hotbar background so any portion of
     * the indicator that's been slid past slot 0 or slot 8 gets clipped.
     * The matching disable happens in the AFTER inject below.
     */
    @Inject(
            method = "renderHotbar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIII)V",
                    ordinal = 1,
                    shift = At.Shift.BEFORE
            )
    )
    private void phaze$openMirrorScissor(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!phaze$shouldDrawMirror) {
            return;
        }
        // Flush whatever vanilla queued before us so the scissor only
        // applies to our indicator+mirror pair, not the hotbar background.
        context.draw();

        int hotbarLeft = context.getScaledWindowWidth() / 2 - HOTBAR_BG_WIDTH_PX / 2;
        int hotbarTop = context.getScaledWindowHeight() - HOTBAR_BG_HEIGHT_PX;
        context.enableScissor(hotbarLeft, hotbarTop, hotbarLeft + HOTBAR_BG_WIDTH_PX, hotbarTop + HOTBAR_BG_HEIGHT_PX);
        phaze$scissorOn = true;
    }

    /**
     * Rewrite the X arg of the selection sprite call (ordinal=1 inside
     * renderHotbar). The args passed to drawGuiTexture for the selection
     * sprite are: (Function spriteFn, Identifier texture, int x, int y,
     * int w, int h).
     */
    @ModifyArgs(
            method = "renderHotbar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIII)V",
                    ordinal = 1
            )
    )
    private void phaze$slideSelectionX(Args args) {
        Animations module = Animations.getInstance();
        if (module == null || !module.isHotbarSlideEnabled()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }

        int selectedSlot = mc.player.getInventory().selectedSlot;
        int origX = args.<Integer>get(2);
        // Recover slot-0 base X by removing vanilla's "+ selected*20" offset.
        int baseX = origX - selectedSlot * SLOT_PIXEL_WIDTH;
        int newX = baseX + Math.round(phaze$currentSlotX);
        args.set(2, newX);

        // Cache for the AFTER inject's mirror render.
        if (phaze$shouldDrawMirror) {
            phaze$lastDrawX = newX;
            phaze$lastDrawY = args.<Integer>get(3);
            phaze$lastWidth = args.<Integer>get(4);
            phaze$lastHeight = args.<Integer>get(5);
            phaze$lastTexture = args.<Identifier>get(1);
            phaze$lastSpriteFn = args.<Function<Identifier, ?>>get(0);
        }
    }

    /**
     * After vanilla finished drawing the (possibly off-edge) primary
     * indicator into the scissored region, draw the mirror copy at
     * +/- 9*20 px so the slide visually wraps. Flush+disable the scissor
     * so the rest of the HUD renders normally.
     */
    @SuppressWarnings("unchecked")
    @Inject(
            method = "renderHotbar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIII)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            )
    )
    private void phaze$drawMirrorAndCloseScissor(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!phaze$shouldDrawMirror || phaze$lastTexture == null || phaze$lastSpriteFn == null) {
            // Belt-and-suspenders: if AFTER fires but we somehow didn't
            // open the scissor, ensure we don't leave one stuck on.
            if (phaze$scissorOn) {
                context.draw();
                context.disableScissor();
                phaze$scissorOn = false;
            }
            return;
        }

        int mirrorX = phaze$lastDrawX + phaze$mirrorOffsetX;
        context.drawGuiTexture(
                (Function<Identifier, RenderLayer>) phaze$lastSpriteFn,
                phaze$lastTexture,
                mirrorX,
                phaze$lastDrawY,
                phaze$lastWidth,
                phaze$lastHeight
        );

        // Flush both queued draws (vanilla indicator + our mirror) WITH
        // the scissor active, then disable it before vanilla continues
        // with item rendering / offhand sprites which need full bounds.
        context.draw();
        if (phaze$scissorOn) {
            context.disableScissor();
            phaze$scissorOn = false;
        }
        phaze$shouldDrawMirror = false;
    }
}
