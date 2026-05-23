package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.*;
import vorga.phazeclient.base.util.ServerUtil;

import java.util.concurrent.CountDownLatch;

public final class AutoSwap extends Module {
    private static final AutoSwap INSTANCE = new AutoSwap();
    private static final int FIXED_SLOT_COUNT = 3;
    private static final String SPHERE_KEY = "сфера";
    private static final String TALISMAN_KEY = "талисман";
    private static final String TOTEM_KEY = "тотем";
    private static final int SWAP_DELAY_MS = 50; // 1 tick (50ms)

    // Settings
    public final SectionSetting generalSection = new SectionSetting("General");
    public final BindSetting keybind = new BindSetting("Keybind", "Key to activate swap");
    public final SelectSetting swapType = new SelectSetting("Swap Type", "Automatic swap type")
            .value("Sphere -> Totem", "Sphere -> Talisman", "Talisman -> Totem", "Talisman -> Talisman", "Sphere -> Sphere")
            .selected("Sphere -> Totem")
            .onChange(this::onSwapTypeChanged);

    // Runtime state
    private boolean isSwapping = false;
    private int currentSlotIndex = 0;

    private AutoSwap() {
        super("autoswap", "Auto Swap", ModuleCategory.UTILITIES);
        
        // Set full width for settings
        keybind.setFullWidth(true);
        swapType.setFullWidth(true);
        
        setup(generalSection, keybind, swapType);
    }

    public static AutoSwap getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Swaps items on button press";
    }

    @Override
    public String getIcon() {
        return "autoswap.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    @Override
    public boolean isCanBind() {
        return false;
    }

    @Override
    public boolean isServerAllowed() {
        return ServerUtil.isAutoSwapSupported();
    }

    /**
     * Activate direct swap with rules
     */
    public void activateDirectSwap() {
        if (!ServerUtil.isAutoSwapSupported()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§cAutoSwap is not supported on this server!"), true);
            }
            return;
        }
        
        SwapTarget target = findNextAvailableTargetWithRules();
        if (target != null) {
            startSwapSequence(target);
        }
    }

    /**
     * Start swap sequence
     */
    private void startSwapSequence(SwapTarget target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null || isSwapping) {
            return;
        }

        if (target.slotId < 0 || target.slotId >= client.player.playerScreenHandler.slots.size()) {
            return;
        }

        isSwapping = true;
        client.setScreen(new InventoryScreen(client.player));
        
        Thread worker = new Thread(() -> {
            sleepSwapDelay();
            client.execute(() -> performSwap(target.slotId, target.itemName));
        }, "PhazeClient-AutoSwapStart");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Perform the actual swap
     */
    private void performSwap(int targetSlot, String itemName) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null) {
            finishSwap();
            return;
        }

        int syncId = client.player.playerScreenHandler.syncId;
        int offhandSlot = 45;
        boolean hasOffhandItem = !client.player.getInventory().offHand.get(0).isEmpty();

        Thread worker = new Thread(() -> {
            runSwapClick(client, syncId, targetSlot);
            sleepSwapDelay();
            runSwapClick(client, syncId, offhandSlot);

            if (hasOffhandItem) {
                sleepSwapDelay();
                runSwapClick(client, syncId, targetSlot);
            }

            sleepSwapDelay();
            client.execute(() -> {
                showSwapMessage(itemName);
                finishSwap();
            });
        }, "PhazeClient-AutoSwapSteps");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Finish swap and close inventory
     */
    private void finishSwap() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.closeHandledScreen();
        }
        client.setScreen(null);
        isSwapping = false;
    }

    /**
     * Show swap message in action bar
     */
    private void showSwapMessage(String itemName) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || itemName == null || itemName.isEmpty()) {
            return;
        }
        
        // Find the actual item stack to get its formatted name with colors
        ItemStack foundStack = findItemStackByName(itemName);
        if (foundStack != null && !foundStack.isEmpty()) {
            // Use the item's display name which includes colors and formatting
            Text swapMessage = Text.literal("Swap to ").append(foundStack.getName());
            client.player.sendMessage(swapMessage, true);
        } else {
            // Fallback to plain text if item not found
            client.player.sendMessage(Text.literal("Swap to " + itemName), true);
        }
    }
    
    /**
     * Find item stack by name in inventory
     */
    private ItemStack findItemStackByName(String itemName) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return null;

        var inventory = client.player.getInventory();
        for (int i = 0; i < inventory.main.size(); i++) {
            ItemStack stack = inventory.main.get(i);
            if (!stack.isEmpty() && stack.getName().getString().equals(itemName)) {
                return stack;
            }
        }
        
        // Check offhand
        ItemStack offhand = inventory.offHand.get(0);
        if (!offhand.isEmpty() && offhand.getName().getString().equals(itemName)) {
            return offhand;
        }
        
        return null;
    }

    /**
     * Find next available target with rules
     */
    private SwapTarget findNextAvailableTargetWithRules() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return null;

        ItemStack offhandStack = client.player.getInventory().offHand.get(0);
        String currentOffhandItem = offhandStack.isEmpty() ? null : offhandStack.getName().getString();

        return applySwapRules(currentOffhandItem);
    }

    /**
     * Apply swap rules based on current offhand item
     */
    private SwapTarget applySwapRules(String currentItem) {
        String rule = swapType.getSelected();
        String lowerItem = currentItem == null ? "" : currentItem.toLowerCase();

        // If offhand is empty, find first available item based on rule
        if (currentItem == null || currentItem.isEmpty()) {
            if ("Talisman -> Talisman".equals(rule)) {
                SwapTarget talisman = findItemContaining(TALISMAN_KEY);
                if (talisman != null) return talisman;
            }

            SwapTarget totem = findTotemTarget();
            if (totem != null) return totem;

            SwapTarget talisman = findItemContaining(TALISMAN_KEY);
            if (talisman != null) return talisman;

            return findItemContaining(SPHERE_KEY);
        }

        // If current item is totem
        if (isTotemName(currentItem)) {
            if ("Sphere -> Totem".equals(rule) || "Sphere -> Talisman".equals(rule)) {
                SwapTarget sphere = findItemContaining(SPHERE_KEY);
                if (sphere != null) return sphere;
            }

            if ("Talisman -> Totem".equals(rule)) {
                SwapTarget talisman = findItemContaining(TALISMAN_KEY);
                if (talisman != null) return talisman;
            }
            return null;
        }

        // If current item is talisman
        if (lowerItem.contains(TALISMAN_KEY)) {
            if ("Talisman -> Talisman".equals(rule)) {
                SwapTarget talisman = findItemContaining(TALISMAN_KEY);
                if (talisman != null) return talisman;
            }

            if ("Talisman -> Totem".equals(rule)) {
                SwapTarget totem = findTotemTarget();
                if (totem != null) return totem;

                if ("Sphere -> Totem".equals(rule) || "Sphere -> Talisman".equals(rule)) {
                    SwapTarget sphere = findItemContaining(SPHERE_KEY);
                    if (sphere != null) return sphere;
                }
            }

            if ("Sphere -> Talisman".equals(rule)) {
                return findItemContaining(SPHERE_KEY);
            }
            return null;
        }

        // If current item is sphere
        if (lowerItem.contains(SPHERE_KEY)) {
            if ("Sphere -> Sphere".equals(rule)) {
                SwapTarget sphere = findItemContaining(SPHERE_KEY);
                if (sphere != null) return sphere;
            }

            if ("Sphere -> Totem".equals(rule)) {
                SwapTarget totem = findTotemTarget();
                if (totem != null) return totem;

                SwapTarget talisman = findItemContaining(TALISMAN_KEY);
                if (talisman != null) return talisman;
            } else if ("Sphere -> Talisman".equals(rule)) {
                SwapTarget talisman = findItemContaining(TALISMAN_KEY);
                if (talisman != null) return talisman;

                SwapTarget totem = findTotemTarget();
                if (totem != null) return totem;
            }
        }

        return null;
    }

    /**
     * Find totem in inventory
     */
    private SwapTarget findTotemTarget() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return null;

        var inventory = client.player.getInventory();
        for (int i = 0; i < inventory.main.size(); i++) {
            ItemStack stack = inventory.main.get(i);
            if (!stack.isEmpty() && stack.isOf(Items.TOTEM_OF_UNDYING)) {
                return new SwapTarget(stack.getName().getString(), i < 9 ? i + 36 : i);
            }
        }
        return null;
    }

    /**
     * Find item containing search text
     */
    private SwapTarget findItemContaining(String searchText) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return null;

        var inventory = client.player.getInventory();
        for (int i = 0; i < inventory.main.size(); i++) {
            ItemStack stack = inventory.main.get(i);
            if (!stack.isEmpty()) {
                String stackName = stack.getName().getString();
                if (stackName.toLowerCase().contains(searchText)) {
                    return new SwapTarget(stackName, i < 9 ? i + 36 : i);
                }
            }
        }
        return null;
    }

    /**
     * Check if item name is totem
     */
    private boolean isTotemName(String itemName) {
        return itemName != null
                && (itemName.equalsIgnoreCase("Totem of Undying")
                || itemName.toLowerCase().contains(TOTEM_KEY));
    }

    /**
     * Find item slot by exact name
     */
    private int findItemSlotByName(String itemName) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return -1;

        var inventory = client.player.getInventory();
        for (int i = 0; i < inventory.main.size(); i++) {
            ItemStack stack = inventory.main.get(i);
            if (!stack.isEmpty() && stack.getName().getString().equals(itemName)) {
                return i < 9 ? i + 36 : i;
            }
        }
        return -1;
    }

    /**
     * Run swap click on main thread
     */
    private void runSwapClick(MinecraftClient client, int syncId, int slotId) {
        CountDownLatch latch = new CountDownLatch(1);
        client.execute(() -> {
            try {
                if (client.player == null || client.interactionManager == null) {
                    return;
                }
                client.interactionManager.clickSlot(syncId, slotId, 0, SlotActionType.PICKUP, client.player);
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sleep for swap delay
     */
    private void sleepSwapDelay() {
        try {
            Thread.sleep(SWAP_DELAY_MS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isSwapping() {
        return isSwapping;
    }

    /**
     * Called when swap type is changed
     */
    private void onSwapTypeChanged(String newType) {
        // No direct save: Setting.notifyChange already routes
        // through ConfigManager.markDirty() via the global change
        // listener, so a user-triggered swap-type change persists
        // within the autosave debounce window. The previous direct
        // saveCurrentConfig() call ignored the load-time guard and
        // could write half-reset state during applyInCodeDefaults,
        // which manifested as configs swapping places after import.
    }
}

/**
 * Swap target data class
 */
class SwapTarget {
    final String itemName;
    final int slotId;

    SwapTarget(String itemName, int slotId) {
        this.itemName = itemName;
        this.slotId = slotId;
    }
}
