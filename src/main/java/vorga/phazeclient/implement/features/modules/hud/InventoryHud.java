package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class InventoryHud extends RectHudModule {
    private static final InventoryHud INSTANCE = new InventoryHud();

    public final SectionSetting otherSection = new SectionSetting("Other");
    public final BooleanSetting drawCounts = new BooleanSetting(
            "Item Counts",
            "Draw the stack count badge on each item"
    ).setValue(true);

    private InventoryHud() {

        super("inventory_hud", "Inventory", 22.0F, 22.0F, 1.5F);

        background.setVisible(() -> false);
        backgroundPreset.setVisible(() -> false);
        colorBrightness.setVisible(() -> false);
        backgroundOpacity.setVisible(() -> false);
        backgroundBlurRadius.setVisible(() -> false);
        textShadow.setVisible(() -> false);
        showBrackets.setVisible(() -> false);

        mainSection.setVisible(() -> false);
        colorSection.setVisible(() -> false);

        drawCounts.setFullWidth(true);
        setup(otherSection, drawCounts, cornerRounding);
    }

    public static InventoryHud getInstance() {
        return INSTANCE;
    }

    private static final ItemStack[] SNAPSHOT = new ItemStack[27];
    private static final boolean[] OVERLAY_FLAGS = new boolean[27];

    private static int lastSnapshotTick = Integer.MIN_VALUE;

    public static void refreshSnapshotIfStale(MinecraftClient mc) {
        if (mc == null || mc.player == null) {
            return;
        }
        int tick = mc.inGameHud != null ? mc.inGameHud.getTicks() : 0;
        if (tick == lastSnapshotTick) {
            return;
        }
        lastSnapshotTick = tick;
        ClientPlayerEntity player = mc.player;
        PlayerInventory inv = player.getInventory();

        DefaultedList<ItemStack> mainStacks = inv.getMainStacks();
        for (int i = 0; i < 27; i++) {
            ItemStack stack = mainStacks.get(9 + i);
            SNAPSHOT[i] = stack;
            OVERLAY_FLAGS[i] = computeOverlayFlag(stack, mc, player);
        }
    }

    public static ItemStack[] getSnapshotStacks() {
        return SNAPSHOT;
    }

    public static boolean[] getSnapshotOverlayFlags() {
        return OVERLAY_FLAGS;
    }

    private static boolean computeOverlayFlag(ItemStack stack, MinecraftClient mc, ClientPlayerEntity player) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getCount() != 1) return true;
        if (stack.isItemBarVisible()) return true;
        return player.getItemCooldownManager().getCooldownProgress(stack, 0.0F) > 0.0F;
    }

    @Override
    public String getDescription() {
        return "Renders your 27 storage slots or last-seen ender chest on the HUD";
    }

    @Override
    public String getIcon() {
        return "inventory_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

}
