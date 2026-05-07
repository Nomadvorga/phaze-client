package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
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
 * Animates the hotbar selection sprite ("hud/hotbar_selection") to slide
 * smoothly between slots instead of teleporting on each slot change.
 *
 * Strategy (different code, same end result as the smooth-scrolling mod's
 * approach): we keep an interpolated X position in pixels and run the same
 * frame-rate independent exponential decay used elsewhere in this module.
 * On each frame, the target X comes from {@code selectedSlot * 20}; we tick
 * once per renderHotbar (so smoothness is consistent with the FPS), then
 * rewrite the second {@code drawGuiTexture} call's X argument with the
 * smoothed value via ModifyArgs.
 *
 * For Hotbar Rollover we additionally watch for big jumps between frames
 * (|delta| > half hotbar) and shift the current X into the wrap direction
 * BEFORE smoothing - that way the slide goes "off the right edge and back
 * in from the left" instead of taking the long path across all 9 slots.
 * To fill the visual gap while the indicator is partly off-screen we draw
 * a mirrored copy at currentX +/- 9*20 so the user always sees a moving
 * indicator.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudHotbarSlideMixin {

    @Unique private static final int SLOT_PIXEL_WIDTH = 20;
    @Unique private static final int HOTBAR_SLOTS = 9;
    @Unique private static final int HOTBAR_PIXEL_WIDTH = SLOT_PIXEL_WIDTH * HOTBAR_SLOTS;

    /** Smoothed X offset relative to slot 0. Pixels. */
    @Unique private float phaze$currentSlotX = 0.0F;
    @Unique private int phaze$lastSelectedSlot = -1;
    @Unique private long phaze$lastFrameNanos = 0L;
    /** Captured render args so the mirror @Inject can access the DrawContext. */
    @Unique private DrawContext phaze$lastContext;
    @Unique private float phaze$lastDrawX;
    @Unique private float phaze$lastDrawY;
    @Unique private int phaze$lastWidth;
    @Unique private int phaze$lastHeight;
    @Unique private Identifier phaze$lastTexture;
    @Unique private Function<Identifier, ?> phaze$lastSpriteFn;
    @Unique private boolean phaze$shouldDrawMirror;
    @Unique private int phaze$mirrorOffsetX;

    /**
     * Tick the slide each time vanilla begins drawing the hotbar - this gives
     * us roughly one update per render frame regardless of FPS, which is
     * everything {@code Math.pow(smoothness, dt)} needs.
     */
    @Inject(method = "renderHotbar", at = @At("HEAD"))
    private void phaze$tickHotbarSlide(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Animations module = Animations.getInstance();
        if (module == null || !module.isHotbarSlideEnabled()) {
            phaze$lastSelectedSlot = -1;
            phaze$lastFrameNanos = 0L;
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity cameraPlayer = mc == null ? null : mc.player;
        if (cameraPlayer == null) {
            return;
        }

        int selected = cameraPlayer.getInventory().selectedSlot;
        float target = selected * SLOT_PIXEL_WIDTH;

        // Wrap detection for rollover: if we're currently far from the new
        // target slot, shift current to the near side of the wrap so the
        // smoothed slide takes the short route across the boundary instead
        // of all the way back through the middle.
        if (module.isHotbarRolloverEnabled() && phaze$lastSelectedSlot >= 0) {
            int slotDelta = selected - phaze$lastSelectedSlot;
            if (slotDelta >= 5) {
                // Jumped right-to-left (e.g. 8 -> 0) - pull current X right.
                phaze$currentSlotX += HOTBAR_PIXEL_WIDTH;
            } else if (slotDelta <= -5) {
                // Jumped left-to-right (e.g. 0 -> 8) - pull current X left.
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

        // Snap to target once we're sub-pixel close so the slide settles.
        if (Math.abs(phaze$currentSlotX - target) < 0.4F) {
            phaze$currentSlotX = target;
        }

        // Reset the mirror-flag from any previous frame.
        phaze$shouldDrawMirror = false;
    }

    /**
     * Rewrite the X arg of the SECOND drawGuiTexture call inside renderHotbar
     * (the selection sprite). The first call paints the hotbar background
     * itself which we leave alone.
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
        // Reverse-engineer the slot-0 base X: vanilla passes (centerX - 91 +
        // selected*20 - 1) which means base = origX - selected*20.
        int baseX = origX - selectedSlot * SLOT_PIXEL_WIDTH;
        int newX = baseX + Math.round(phaze$currentSlotX);
        args.set(2, newX);

        // Stash everything we need to draw the rollover mirror after vanilla
        // finishes its own draw call. We can't draw mid-ModifyArgs because
        // the texture is still being submitted.
        if (!module.isHotbarRolloverEnabled()) {
            phaze$shouldDrawMirror = false;
            return;
        }
        int leftEdge = baseX;
        int rightEdge = baseX + HOTBAR_PIXEL_WIDTH - SLOT_PIXEL_WIDTH;
        if (newX < leftEdge) {
            // Indicator partly off the LEFT edge - draw a mirror at +9*20.
            phaze$shouldDrawMirror = true;
            phaze$mirrorOffsetX = HOTBAR_PIXEL_WIDTH;
        } else if (newX > rightEdge) {
            // Indicator partly off the RIGHT edge - draw a mirror at -9*20.
            phaze$shouldDrawMirror = true;
            phaze$mirrorOffsetX = -HOTBAR_PIXEL_WIDTH;
        } else {
            phaze$shouldDrawMirror = false;
        }
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
     * After vanilla draws the indicator at its (slid) position, draw a second
     * copy on the wraparound side so the slide visually reads as a continuous
     * roll across the hotbar boundary.
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
    private void phaze$drawHotbarRolloverMirror(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!phaze$shouldDrawMirror || phaze$lastTexture == null || phaze$lastSpriteFn == null) {
            return;
        }
        int mirrorX = Math.round(phaze$lastDrawX) + phaze$mirrorOffsetX;
        context.drawGuiTexture(
                (Function<Identifier, net.minecraft.client.render.RenderLayer>) phaze$lastSpriteFn,
                phaze$lastTexture,
                mirrorX,
                Math.round(phaze$lastDrawY),
                phaze$lastWidth,
                phaze$lastHeight
        );
        phaze$shouldDrawMirror = false;
    }
}
