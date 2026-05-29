package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BindSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.base.util.ServerUtil;

import java.lang.reflect.Method;

/**
 * Quick-swap chestplate <-> elytra utility. Triggered by a dedicated keybind
 * (default R). Restricted to the supported FunTime-family servers and singleplayer.
 */
public final class ElytraUtility extends Module {
    private static final ElytraUtility INSTANCE = new ElytraUtility();
    private static final int CHEST_SLOT_ID = 6;
    private static final long SWAP_DELAY_MS = 120L;
    private static final long DEBOUNCE_MS = 200L;

    public final SectionSetting generalSection = new SectionSetting("General");
    public final BindSetting keybind = new BindSetting("Keybind", "Key to swap elytra/chestplate")
            .setKey(GLFW.GLFW_KEY_R);

    private boolean swapping;
    private long lastActionMs;

    private ElytraUtility() {
        // Internal id stays as "elytrautility" so existing user configs that
        // reference the module by id continue to load without migration.
        super("elytrautility", "Elytra Swap", ModuleCategory.UTILITIES);
        keybind.setFullWidth(true);
        setup(generalSection, keybind);
    }

    public static ElytraUtility getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Swap chestplate and elytra with a single keybind";
    }

    @Override
    public String getIcon() {
        return "elytrautility.png";
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
        return ServerUtil.isElytraUtilitySupported();
    }

    /**
     * Called by the keyboard mixin when a key is pressed/released.
     */
    public void onBindStateChanged(int code, int action) {
        if (!isEnabled() || action != GLFW.GLFW_PRESS) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        // Allow ElytraSwap inside Creative inventory. For all other
        // screens keep the old guard to avoid accidental swaps in UI.
        if (mc.currentScreen != null && !(mc.currentScreen instanceof CreativeInventoryScreen)) {
            return;
        }
        if (!ServerUtil.isElytraUtilitySupported()) {
            return;
        }
        if (keybind.getKey() == GLFW.GLFW_KEY_UNKNOWN || code != keybind.getKey()) {
            return;
        }
        beginInventorySwap();
    }

    private void beginInventorySwap() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (swapping || now - lastActionMs < DEBOUNCE_MS) {
            return;
        }

        ItemStack chestStack = mc.player.playerScreenHandler.getSlot(CHEST_SLOT_ID).getStack();
        boolean elytraEquipped = chestStack.isOf(Items.ELYTRA);
        int sourceSlotId = elytraEquipped ? findChestplateSlotId() : findElytraSlotId();
        if (sourceSlotId == -1) {
            return;
        }

        swapping = true;
        lastActionMs = now;
        mc.setScreen(new InventoryScreen(mc.player));

        Thread worker = new Thread(() -> {
            try {
                Thread.sleep(SWAP_DELAY_MS);
                mc.execute(() -> {
                    phaze$ensureCreativeInventoryTab();
                    performSwap(sourceSlotId);
                });
                Thread.sleep(SWAP_DELAY_MS + 60L);
                mc.execute(this::finishSwap);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                mc.execute(this::finishSwap);
            }
        }, "PhazeClient-ElytraSwap");
        worker.setDaemon(true);
        worker.start();
    }

    private void phaze$ensureCreativeInventoryTab() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || !mc.player.isCreative()) {
            return;
        }
        if (mc.currentScreen instanceof CreativeInventoryScreen creativeScreen) {
            try {
                ItemGroup inventoryGroup = Registries.ITEM_GROUP.get(ItemGroups.INVENTORY);
                if (inventoryGroup == null) {
                    return;
                }
                for (Method method : creativeScreen.getClass().getDeclaredMethods()) {
                    if (method.getParameterCount() == 1 && method.getReturnType() == Void.TYPE) {
                        Class<?> param = method.getParameterTypes()[0];
                        if (param.isInstance(inventoryGroup)) {
                            method.setAccessible(true);
                            method.invoke(creativeScreen, inventoryGroup);
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {
                // Fail-safe: if mappings/mods change this method, we still
                // continue with swap attempt instead of hard-failing.
            }
        }
    }

    private void performSwap(int sourceSlotId) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) {
            return;
        }
        if (sourceSlotId < 0 || sourceSlotId >= mc.player.playerScreenHandler.slots.size()) {
            return;
        }
        int syncId = mc.player.playerScreenHandler.syncId;
        mc.interactionManager.clickSlot(syncId, sourceSlotId, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, CHEST_SLOT_ID, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, sourceSlotId, 0, SlotActionType.PICKUP, mc.player);
    }

    private void finishSwap() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.closeHandledScreen();
        }
        mc.setScreen(null);
        swapping = false;
    }

    private int findElytraSlotId() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return -1;
        for (int slotId = 9; slotId <= 44; slotId++) {
            if (mc.player.playerScreenHandler.getSlot(slotId).getStack().isOf(Items.ELYTRA)) {
                return slotId;
            }
        }
        return -1;
    }

    private int findChestplateSlotId() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return -1;
        for (int slotId = 9; slotId <= 44; slotId++) {
            ItemStack stack = mc.player.playerScreenHandler.getSlot(slotId).getStack();
            EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
            if (equippable != null && equippable.slot() == EquipmentSlot.CHEST && !stack.isOf(Items.ELYTRA)) {
                return slotId;
            }
        }
        return -1;
    }
}
