package vorga.phazeclient.mixins;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.GenericContainerScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.hud.InventoryHud;

/**
 * Snapshot the contents of any open ender-chest GUI into
 * {@link InventoryHud}'s cache so the HUD can keep showing those
 * 27 slots after the screen is closed.
 *
 * <h3>Why GenericContainerScreen</h3>
 * Vanilla 1.21.4 routes ender chests through
 * {@link GenericContainerScreen} backed by a
 * {@link GenericContainerScreenHandler} (same handler family as a
 * normal chest). We can't tell ender from normal at the handler
 * level, so we match by the screen's display title containing
 * "Ender Chest" - case-insensitive substring so server renames
 * with prefixes still work.
 *
 * <h3>Capture timing</h3>
 * {@code HandledScreen.handledScreenTick} is called every game
 * tick the screen is open. We copy slot contents into the cache
 * each tick, so the snapshot tracks any in-flight changes the
 * user makes (item swaps, stack pickups). When the screen closes,
 * the last captured state stays in the cache until another ender
 * chest is opened.
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenInventoryHudMixin {

    @Inject(method = "handledScreenTick", at = @At("TAIL"))
    private void phaze$snapshotEnderChest(CallbackInfo ci) {
        HandledScreen<?> self = (HandledScreen<?>) (Object) this;
        if (!(self instanceof GenericContainerScreen genericScreen)) {
            return;
        }
        // Title-based detection: Ender chest's display name is
        // "Ender Chest" (or a server-customised string CONTAINING
        // those words for prefixed servers). String compare is the
        // only reliable distinguisher between ender and normal
        // double chests at the handler level.
        String title = self.getTitle().getString().toLowerCase();
        if (!title.contains("ender chest")) {
            return;
        }

        GenericContainerScreenHandler handler = genericScreen.getScreenHandler();
        // Ender chest = single 27-slot row layout. The screen
        // handler exposes the upper inventory as the first 27 slots;
        // anything beyond that is the player's hotbar/main and
        // belongs to the player, not the ender chest. Cap at 27 so
        // we never overwrite cache out of bounds when a server
        // serves a 54-slot custom ender variant.
        int upper = Math.min(27, handler.getInventory().size());
        InventoryHud hud = InventoryHud.getInstance();
        for (int i = 0; i < upper; i++) {
            hud.updateEnderChestSlot(i, handler.getInventory().getStack(i));
        }
    }
}
