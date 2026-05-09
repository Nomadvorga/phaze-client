package vorga.phazeclient.mixins;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.base.util.ServerUtil;
import vorga.phazeclient.implement.features.modules.other.ItemScroller;

import java.util.HashSet;
import java.util.Set;

/**
 * Wires {@link ItemScroller} into vanilla's slot-click pipeline.
 *
 * <p>Workflow:
 * <ol>
 *   <li>User Shift+LMB-clicks a slot. Vanilla performs the QUICK_MOVE
 *       transfer for that slot itself; we just record the slot id in
 *       {@link #phaze$transferredSlots} so the drag path below doesn't
 *       try to re-transfer it (the slot would be empty by then anyway,
 *       but skipping the lookup keeps things tidy).</li>
 *   <li>While the LMB is held and Shift stays pressed, vanilla fires
 *       {@code mouseDragged} for every cursor movement. We resolve the
 *       slot under the cursor, and if it's a NEW slot that contains an
 *       item we fire a synthetic QUICK_MOVE on it - same behaviour as
 *       if the user had clicked it manually.</li>
 *   <li>Releasing LMB clears the per-session state.</li>
 * </ol>
 *
 * <p>The {@link ItemScroller#getDelayMs()} throttle gates consecutive
 * transfers so a fast sweep doesn't burst a dozen packets in one tick.
 * Slots already transferred this session are remembered so the cursor
 * can wander back over them without retriggering. We intentionally
 * don't cancel vanilla's drag-place mechanic (which only kicks in when
 * the cursor holds an item, which never happens during shift-drag
 * because every shift-click leaves the cursor empty).
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenItemScrollerMixin {

    @Shadow
    @Nullable
    protected abstract Slot getSlotAt(double x, double y);

    @Shadow
    protected abstract void onMouseClick(@Nullable Slot slot, int slotId, int button, SlotActionType actionType);

    /** Slot ids already shift-clicked during the current LMB-down session. */
    @Unique private final Set<Integer> phaze$transferredSlots = new HashSet<>();

    /** True between Shift+LMB-down and LMB-up while the module is active. */
    @Unique private boolean phaze$shiftDragActive = false;

    /** Wall-clock millis of the last synthetic QUICK_MOVE we fired. */
    @Unique private long phaze$lastTransferAt = 0L;

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void phaze$onMouseClickedHead(double mouseX, double mouseY, int button,
                                          CallbackInfoReturnable<Boolean> cir) {
        ItemScroller mod = ItemScroller.getInstance();
        if (mod == null || !mod.isEnabled() || !ServerUtil.isItemScrollerSupported()) {
            phaze$shiftDragActive = false;
            return;
        }
        if (button != 0 || !Screen.hasShiftDown()) {
            return;
        }

        // Begin a shift-drag session. Vanilla still handles the first
        // click's QUICK_MOVE itself - we only record the slot id so the
        // drag path skips re-transferring it. Clearing the set here
        // resets state from any prior interrupted session.
        phaze$shiftDragActive = true;
        phaze$transferredSlots.clear();
        Slot slot = getSlotAt(mouseX, mouseY);
        if (slot != null) {
            phaze$transferredSlots.add(slot.id);
        }
        phaze$lastTransferAt = System.currentTimeMillis();
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void phaze$onMouseReleasedHead(double mouseX, double mouseY, int button,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (button == 0) {
            phaze$shiftDragActive = false;
            phaze$transferredSlots.clear();
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"))
    private void phaze$onMouseDraggedHead(double mouseX, double mouseY, int button,
                                          double deltaX, double deltaY,
                                          CallbackInfoReturnable<Boolean> cir) {
        ItemScroller mod = ItemScroller.getInstance();
        if (mod == null || !mod.isEnabled() || !ServerUtil.isItemScrollerSupported()) {
            return;
        }
        if (button != 0 || !Screen.hasShiftDown()) {
            return;
        }

        // Lazy-start a session if we got here without a prior shift+LMB
        // click - covers the case where the user pressed LMB first and
        // THEN held shift while dragging. Without this gate the drag
        // would feel inconsistent ("works only sometimes") because it'd
        // depend on the exact press-order of LMB and shift.
        if (!phaze$shiftDragActive) {
            phaze$shiftDragActive = true;
            phaze$transferredSlots.clear();
            phaze$lastTransferAt = 0L;
        }

        long now = System.currentTimeMillis();
        if (now - phaze$lastTransferAt < mod.getDelayMs()) {
            return;
        }

        // Sample slots along the drag path so fast cursor sweeps don't
        // skip intermediate slots. mouseDragged fires once per frame
        // with an accumulated delta; on a 60Hz frame the cursor can
        // easily span 3-4 slot widths when yanked across an inventory,
        // and a single getSlotAt(mouseX, mouseY) call would only catch
        // the LAST one. We walk from where the cursor was last frame
        // (mouseX - deltaX, mouseY - deltaY) to the current position
        // in 4-pixel increments (slots are 16x16, so 4px guarantees at
        // least 4 samples per slot crossed) and shift-click every
        // previously-untouched slot whose hitbox we cross.
        double startX = mouseX - deltaX;
        double startY = mouseY - deltaY;
        double dist = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        int steps = Math.max(1, (int) Math.ceil(dist / 4.0));

        boolean transferred = false;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double sampleX = startX + deltaX * t;
            double sampleY = startY + deltaY * t;
            Slot slot = getSlotAt(sampleX, sampleY);
            if (slot == null || !slot.hasStack()) {
                continue;
            }
            if (phaze$transferredSlots.contains(slot.id)) {
                continue;
            }
            phaze$transferredSlots.add(slot.id);
            // Use the public clickSlot API on Screen so creative
            // inventory's overridden quick-move path is respected -
            // CreativeInventoryScreen translates QUICK_MOVE into its
            // own give-or-delete handler when the slot belongs to one
            // of its picker tabs, and the survival ScreenHandler.
            // quickMove() pipeline runs unchanged for everything else.
            onMouseClick(slot, slot.id, 0, SlotActionType.QUICK_MOVE);
            transferred = true;
        }

        if (transferred) {
            phaze$lastTransferAt = now;
        }
    }
}
