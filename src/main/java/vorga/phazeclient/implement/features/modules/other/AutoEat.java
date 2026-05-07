package vorga.phazeclient.implement.features.modules.other;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public final class AutoEat extends Module {
    private static final AutoEat INSTANCE = new AutoEat();
    private static final long FEED_COMMAND_COOLDOWN_MS = 2000L;

    public final SectionSetting generalSection = new SectionSetting("General");
    public final BooleanSetting useCommand = new BooleanSetting(
            "Use /feed Command",
            "Send the /feed chat command instead of eating physical food (server-side support required)"
    ).setValue(false);
    public final ValueSetting hungerThreshold = new ValueSetting(
            "Hunger Threshold",
            "Auto-eat when the food bar drops to this value (out of 20)"
    ).range(1, 19).setValue(17);

    private boolean eating = false;
    private int previousSlot = -1;
    private long lastCommandMs = 0L;

    private AutoEat() {
        super("auto_eat", "Auto Eat", ModuleCategory.UTILITIES);
        useCommand.setFullWidth(true);
        hungerThreshold.setFullWidth(true);
        setup(generalSection, useCommand, hungerThreshold);

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    public static AutoEat getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Automatically eats food (or runs /feed) when hunger drops below the threshold";
    }

    /**
     * Returns true while a physical-eating cycle is currently in progress.
     * The {@link vorga.phazeclient.mixins.ClientPlayerInteractionManagerMixin}
     * uses this to suppress vanilla's automatic {@code stopUsingItem} call
     * that would otherwise fire every tick because the use-key isn't held.
     */
    public boolean isAutoEating() {
        return isEnabled() && eating;
    }

    @Override
    public void deactivate() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            finishEating(mc);
        }
    }

    private void tick(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.interactionManager == null) {
            if (eating && mc != null) {
                finishEating(mc);
            }
            return;
        }

        if (!isEnabled()) {
            if (eating) {
                finishEating(mc);
            }
            return;
        }

        // Block any user-visible activity while a screen is open: vanilla
        // GUIs (inventory, chest, ESC menu, advancements...), the chat
        // screen, and our own ClickGui all populate mc.currentScreen, so
        // a single null-check covers them. We still let an in-flight
        // physical bite tick to its natural completion below - the bite
        // is server-tick driven and would resolve weirdly if we cancelled
        // it mid-use just because the user opened their inventory.
        boolean screenOpen = mc.currentScreen != null;

        int hunger = mc.player.getHungerManager().getFoodLevel();
        int threshold = hungerThreshold.getInt();

        if (useCommand.isValue()) {
            if (eating) {
                finishEating(mc);
            }
            if (screenOpen) {
                return;
            }
            if (hunger <= threshold && mc.getNetworkHandler() != null) {
                long now = System.currentTimeMillis();
                if (now - lastCommandMs > FEED_COMMAND_COOLDOWN_MS) {
                    mc.getNetworkHandler().sendChatCommand("feed");
                    lastCommandMs = now;
                }
            }
            return;
        }

        // Physical eating mode.
        if (eating) {
            if (mc.player.isUsingItem()) {
                // Still eating - the ClientPlayerInteractionManager mixin
                // suppresses vanilla's stopUsingItem call so the use ticks
                // through to natural completion.
                return;
            }
            // Vanilla finished using the item (Item.finishUsing fired).
            finishEating(mc);
            return;
        }

        // Don't START a new bite while any screen is open - the user is
        // interacting with a GUI, chat, ESC menu, or our ClickGui and
        // wouldn't expect a hotbar-slot swap + interactItem behind the
        // back of whatever they're doing.
        if (screenOpen) {
            return;
        }

        if (hunger > threshold) {
            return;
        }

        int foodSlot = findFoodInHotbar(mc.player);
        if (foodSlot < 0) {
            return;
        }

        PlayerInventory inventory = mc.player.getInventory();
        previousSlot = inventory.selectedSlot;
        selectHotbarSlot(mc, foodSlot);
        ActionResult result = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        if (result.isAccepted()) {
            eating = true;
        } else {
            // Item couldn't be used - swap back immediately.
            selectHotbarSlot(mc, previousSlot);
            previousSlot = -1;
        }
    }

    /**
     * Switches the player's currently held hotbar slot and syncs the change
     * to the server, mirroring how vanilla updates the slot when the user
     * scrolls or presses a number key.
     */
    private void selectHotbarSlot(MinecraftClient mc, int slot) {
        if (mc == null || mc.player == null) {
            return;
        }
        mc.player.getInventory().selectedSlot = slot;
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    private int findFoodInHotbar(PlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (isFood(stack)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.contains(DataComponentTypes.FOOD);
    }

    private void finishEating(MinecraftClient mc) {
        boolean wasEating = eating;
        eating = false;
        if (wasEating && previousSlot >= 0 && mc != null && mc.player != null) {
            selectHotbarSlot(mc, previousSlot);
        }
        previousSlot = -1;
    }
}
