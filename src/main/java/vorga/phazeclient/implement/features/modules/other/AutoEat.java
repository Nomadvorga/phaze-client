package vorga.phazeclient.implement.features.modules.other;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
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
    private static final long USE_RETRY_DELAY_MS = 250L;
    private static final long EAT_TIMEOUT_MS = 10000L;
    private static final long NEXT_BITE_DELAY_MS = 500L;

    public final SectionSetting generalSection = new SectionSetting("General");
    public final BooleanSetting useCommand = new BooleanSetting(
            "Use /feed Command",
            "Send the /feed chat command instead of eating physical food (server-side support required)"
    ).setValue(false);
    public final ValueSetting hungerThreshold = new ValueSetting(
            "Hunger Threshold",
            "Auto-eat when the food bar drops to this value (out of 20)"
    ).range(1, 19).setValue(17);

    private boolean eating;
    private boolean swapped;
    private boolean startedUsingItem;
    private int activeHotbarSlot = -1;
    private int sourceScreenSlot = -1;
    private Item foodItem;
    private int foodCountBefore;
    private int hungerBefore;
    private long eatStartedMs;
    private long nextUseAttemptMs;
    private long nextBiteMs;
    private long lastCommandMs;

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

    @Override
    public String getIcon() {
        return "auto_eat.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public boolean isAutoEating() {
        return isEnabled() && eating;
    }

    @Override
    public void deactivate() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            cancelEating(mc);
        }
    }

    private void tick(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.interactionManager == null) {
            if (eating && mc != null) {
                cancelEating(mc);
            }
            return;
        }

        if (!isEnabled()) {
            if (eating) {
                cancelEating(mc);
            }
            return;
        }

        int hunger = mc.player.getHungerManager().getFoodLevel();
        int threshold = hungerThreshold.getInt();

        if (useCommand.isValue()) {
            if (eating) {
                cancelEating(mc);
            }
            if (mc.currentScreen == null && hunger <= threshold && mc.getNetworkHandler() != null) {
                long now = System.currentTimeMillis();
                if (now - lastCommandMs >= FEED_COMMAND_COOLDOWN_MS) {
                    mc.getNetworkHandler().sendChatCommand("feed");
                    lastCommandMs = now;
                }
            }
            return;
        }

        if (eating) {
            tickEating(mc);
            return;
        }

        if (mc.currentScreen != null || hunger > threshold || System.currentTimeMillis() < nextBiteMs) {
            return;
        }

        startEating(mc, hunger);
    }

    private void startEating(MinecraftClient mc, int hunger) {
        PlayerInventory inventory = mc.player.getInventory();
        int selectedSlot = inventory.getSelectedSlot();
        int foodInventorySlot = findFoodInInventory(mc.player, selectedSlot);
        if (foodInventorySlot < 0) {
            return;
        }

        activeHotbarSlot = selectedSlot;
        sourceScreenSlot = -1;
        swapped = foodInventorySlot != selectedSlot;

        if (swapped) {
            sourceScreenSlot = inventorySlotToPlayerScreenSlot(foodInventorySlot);
            if (sourceScreenSlot < 0 || sourceScreenSlot >= mc.player.playerScreenHandler.slots.size()) {
                resetState();
                return;
            }
            // The selected hotbar index stays unchanged. Only the stacks are
            // exchanged, so the server uses the same slot the player holds.
            mc.interactionManager.clickSlot(
                    mc.player.playerScreenHandler.syncId,
                    sourceScreenSlot,
                    activeHotbarSlot,
                    SlotActionType.SWAP,
                    mc.player
            );
        }

        ItemStack heldFood = inventory.getStack(activeHotbarSlot);
        if (!isFood(heldFood)) {
            restoreItems(mc);
            resetState();
            return;
        }

        foodItem = heldFood.getItem();
        foodCountBefore = heldFood.getCount();
        hungerBefore = hunger;
        startedUsingItem = false;
        eatStartedMs = System.currentTimeMillis();
        nextUseAttemptMs = eatStartedMs + 50L;
        eating = true;
    }

    private void tickEating(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        PlayerInventory inventory = mc.player.getInventory();

        if (activeHotbarSlot < 0 || activeHotbarSlot > 8
                || inventory.getSelectedSlot() != activeHotbarSlot) {
            cancelEating(mc);
            return;
        }

        ItemStack heldStack = inventory.getStack(activeHotbarSlot);
        boolean consumed = mc.player.getHungerManager().getFoodLevel() > hungerBefore
                || heldStack.isEmpty()
                || heldStack.getItem() != foodItem
                || heldStack.getCount() < foodCountBefore;
        if (consumed) {
            completeEating(mc);
            return;
        }

        if (!isFood(heldStack) || now - eatStartedMs >= EAT_TIMEOUT_MS) {
            cancelEating(mc);
            return;
        }

        mc.options.useKey.setPressed(true);
        if (mc.player.isUsingItem()) {
            startedUsingItem = true;
            return;
        }

        if (startedUsingItem) {
            // Wait for the server's stack update before trying another use.
            startedUsingItem = false;
            nextUseAttemptMs = now + USE_RETRY_DELAY_MS;
            return;
        }

        if (now >= nextUseAttemptMs) {
            ActionResult result = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            if (result.isAccepted() && mc.player.isUsingItem()) {
                startedUsingItem = true;
            }
            nextUseAttemptMs = now + USE_RETRY_DELAY_MS;
        }
    }

    private void completeEating(MinecraftClient mc) {
        releaseUseKey(mc);
        restoreItems(mc);
        resetState();
        nextBiteMs = System.currentTimeMillis() + NEXT_BITE_DELAY_MS;
    }

    private void cancelEating(MinecraftClient mc) {
        if (mc != null && mc.player != null && mc.interactionManager != null && mc.player.isUsingItem()) {
            mc.interactionManager.stopUsingItem(mc.player);
        }
        releaseUseKey(mc);
        restoreItems(mc);
        resetState();
        nextBiteMs = System.currentTimeMillis() + NEXT_BITE_DELAY_MS;
    }

    private void restoreItems(MinecraftClient mc) {
        if (!swapped || mc == null || mc.player == null || mc.interactionManager == null) {
            return;
        }
        if (sourceScreenSlot < 0 || activeHotbarSlot < 0
                || sourceScreenSlot >= mc.player.playerScreenHandler.slots.size()) {
            return;
        }

        // Reverse the original exchange: held item and remaining food both
        // return to exactly the slots they occupied before Auto Eat started.
        mc.interactionManager.clickSlot(
                mc.player.playerScreenHandler.syncId,
                sourceScreenSlot,
                activeHotbarSlot,
                SlotActionType.SWAP,
                mc.player
        );
        swapped = false;
    }

    private void releaseUseKey(MinecraftClient mc) {
        if (mc != null && mc.options != null) {
            mc.options.useKey.setPressed(false);
        }
    }

    private void resetState() {
        eating = false;
        swapped = false;
        startedUsingItem = false;
        activeHotbarSlot = -1;
        sourceScreenSlot = -1;
        foodItem = null;
        foodCountBefore = 0;
        hungerBefore = 0;
        eatStartedMs = 0L;
        nextUseAttemptMs = 0L;
    }

    private int findFoodInInventory(PlayerEntity player, int selectedSlot) {
        PlayerInventory inventory = player.getInventory();
        if (isFood(inventory.getStack(selectedSlot))) {
            return selectedSlot;
        }
        for (int slot = 0; slot < 36; slot++) {
            if (slot != selectedSlot && isFood(inventory.getStack(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private int inventorySlotToPlayerScreenSlot(int inventorySlot) {
        if (inventorySlot >= 0 && inventorySlot < 9) {
            return 36 + inventorySlot;
        }
        if (inventorySlot >= 9 && inventorySlot < 36) {
            return inventorySlot;
        }
        return -1;
    }

    private boolean isFood(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.contains(DataComponentTypes.FOOD);
    }
}
