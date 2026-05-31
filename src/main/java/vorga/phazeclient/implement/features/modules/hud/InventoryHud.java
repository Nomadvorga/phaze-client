package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

/**
 * On-screen mini-inventory: paints the 27 main inventory slots from
 * {@code ClientPlayerEntity.getInventory().main} directly on the HUD
 * so the user can read their loadout without opening the full
 * inventory screen.
 *
 * <h3>Layout</h3>
 * 9 slots wide, 3 rows tall. Each slot is 18x18 GUI pixels (vanilla
 * convention). Total area 162x54 px before any user-set scale. The
 * HUD renders in a fixed screen corner via the standard HUD
 * pipeline with drag/resize support.
 */
public final class InventoryHud extends RectHudModule {
    private static final InventoryHud INSTANCE = new InventoryHud();

    public final SectionSetting otherSection = new SectionSetting("Other");
    public final BooleanSetting drawCounts = new BooleanSetting(
            "Item Counts",
            "Draw the stack count badge on each item"
    ).setValue(true);

    private InventoryHud() {
        // Wider HUD: default footprint 162x54 px so the typical
        // "right edge" anchor sits at hudX = scaled-width - 162 - 4.
        // We let the user drag from the default top-left after the
        // first render, so the initial position is just a sane
        // starting point.
        super("inventory_hud", "Inventory", 22.0F, 22.0F, 1.5F);
        // Hide every inherited RectHudModule setting that doesn't
        // apply to the slot grid - the Inventory HUD draws its own
        // backdrop matching the vanilla inventory look, so the
        // "Background", "Background Preset", "Color Brightness",
        // "Background Opacity", "Background Blur Radius",
        // "Text Shadow" and "Show Brackets" toggles inherited from
        // the base class would just clutter the panel without
        // affecting anything. Setting them invisible keeps the
        // values still serialisable through the standard config
        // pipeline; the menu just skips drawing them.
        background.setVisible(() -> false);
        backgroundPreset.setVisible(() -> false);
        colorBrightness.setVisible(() -> false);
        backgroundOpacity.setVisible(() -> false);
        backgroundBlurRadius.setVisible(() -> false);
        textShadow.setVisible(() -> false);
        showBrackets.setVisible(() -> false);
        // Hide the inherited section headers too: General + Color
        // Settings own no visible children in this HUD, so leaving
        // their dividers in the panel produces empty "Общее" /
        // "Настройки цвета" labels floating above nothing. The
        // colorSection visibility predicate already gates on
        // {@code background.isValue()} which we just nailed to
        // false, so it auto-hides; mainSection has no predicate
        // upstream so we set one explicitly here.
        mainSection.setVisible(() -> false);
        colorSection.setVisible(() -> false);

        drawCounts.setFullWidth(true);
        setup(otherSection, drawCounts);
    }

    public static InventoryHud getInstance() {
        return INSTANCE;
    }

    /**
     * Per-tick snapshot of the 27 main-inventory slots (indices
     * 9..35 in {@link PlayerInventory#main}) plus per-slot
     * "needs overlay" flags. The HUD render path reads from this
     * array every frame instead of poking the live inventory and
     * re-evaluating cooldown / damage state on every redraw.
     *
     * <p>The user's inventory only changes on server-driven inventory
     * packets (~20 ticks per second), so refreshing this snapshot at
     * vanilla tick rate keeps the visual identical while collapsing
     * the per-frame cost from O(27 cooldownManager + 27 isItemBarVisible)
     * down to a flat array lookup. At 240 FPS that's ~12x fewer
     * cooldown queries per second.
     *
     * <p>Slot 0 of {@code SNAPSHOT} maps to inventory index 9
     * (top-left of the storage grid), slot 26 to inventory index 35
     * (bottom-right). Mirrors {@code drawInventoryHud}'s row-major
     * iteration. Stacks are kept by identity reference - we only
     * read {@link ItemStack#isEmpty()} / {@link ItemStack#getCount()} /
     * {@link net.minecraft.client.gui.DrawContext#drawItem} from
     * them, all of which tolerate concurrent reads against the same
     * instance.
     *
     * <p>{@code OVERLAY_FLAGS} is the precomputed result of
     * {@code shouldDrawSlotOverlay} (count != 1 || damaged ||
     * on cooldown). Cooldown progresses every tick, so resampling
     * once per tick keeps the cooldown sweep frame-accurate enough.
     */
    private static final ItemStack[] SNAPSHOT = new ItemStack[27];
    private static final boolean[] OVERLAY_FLAGS = new boolean[27];
    /** Last known tick timestamp the snapshot was refreshed at. Compared
     *  against {@link MinecraftClient#getTicks()} so we only walk the
     *  inventory once per server tick instead of once per render frame. */
    private static int lastSnapshotTick = Integer.MIN_VALUE;

    /**
     * Refresh {@link #SNAPSHOT} / {@link #OVERLAY_FLAGS} from the
     * live player inventory if at least one tick has elapsed since
     * the last refresh. Cheap to call every frame: the early-return
     * is a single int compare on the fast path.
     */
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
        for (int i = 0; i < 27; i++) {
            ItemStack stack = inv.main.get(9 + i);
            SNAPSHOT[i] = stack;
            OVERLAY_FLAGS[i] = computeOverlayFlag(stack, mc, player);
        }
    }

    /** @return cached snapshot of the 27 storage slots; row-major,
     *          index {@code row*9 + col}. Refreshed at most once per tick. */
    public static ItemStack[] getSnapshotStacks() {
        return SNAPSHOT;
    }

    /** @return cached "needs stack overlay" flag for each of the 27 slots,
     *          aligned with {@link #getSnapshotStacks()}. */
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
