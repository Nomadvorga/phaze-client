package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.item.ItemStack;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;

/**
 * On-screen mini-inventory: paints the 27 main inventory slots (or
 * the player's ender chest as last seen) directly on the HUD so the
 * user can read their loadout without opening the full inventory
 * screen.
 *
 * <h3>Source mode</h3>
 * <ul>
 *   <li><b>Main</b> - the 27 storage slots from {@code ClientPlayerEntity}.
 *       Always live, no extra state needed.</li>
 *   <li><b>Ender Chest</b> - snapshot captured the last time the
 *       player opened a real ender chest GUI. The client doesn't
 *       receive ender chest contents until the screen is open, so
 *       a cached snapshot is the best we can do; it stays visible
 *       until the player opens another ender chest, which refreshes
 *       it. {@link #getEnderChestSnapshot()} returns the cache.</li>
 * </ul>
 *
 * <h3>Snapshot capture</h3>
 * {@code HandledScreenInventoryHudMixin} watches for
 * {@code GenericContainerScreenHandler} screens whose title contains
 * "Ender Chest" and copies the upper inventory's slot contents into
 * the snapshot array on each tick the screen is open. Capture is
 * passive - no packets are sent, so this is purely a client-side
 * UI feature.
 *
 * <h3>Layout</h3>
 * 9 slots wide, 3 rows tall. Each slot is 18x18 GUI pixels (vanilla
 * convention). Total area 162x54 px before any user-set scale. The
 * HUD renders in a fixed screen corner via the standard HUD
 * pipeline with drag/resize support.
 */
public final class InventoryHud extends RectHudModule {
    private static final InventoryHud INSTANCE = new InventoryHud();

    /** 27 slots cached from the last ender chest GUI the player opened. */
    private final ItemStack[] enderChestSnapshot = new ItemStack[27];

    public final SectionSetting otherSection = new SectionSetting("Other");
    public final SelectSetting source = new SelectSetting(
            "Source",
            "Which inventory to display"
    ).value("Main", "Ender Chest").selected("Main");
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
        // The "Color Settings" section divider is already gated on
        // background.isValue() in the base class, so it's auto-
        // hidden when we hide background above.

        // Initialise snapshot with empty stacks so the renderer can
        // walk the array unconditionally without null checks.
        for (int i = 0; i < enderChestSnapshot.length; i++) {
            enderChestSnapshot[i] = ItemStack.EMPTY;
        }
        source.setFullWidth(true);
        drawCounts.setFullWidth(true);
        setup(otherSection, source, drawCounts);
    }

    public static InventoryHud getInstance() {
        return INSTANCE;
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

    public boolean isEnderChestMode() {
        return "Ender Chest".equalsIgnoreCase(source.getSelected());
    }

    /** Returns the cached ender chest snapshot. Length always 27. */
    public ItemStack[] getEnderChestSnapshot() {
        return enderChestSnapshot;
    }

    /**
     * Copy a freshly-seen ender chest slot into the cache. Called
     * from the HandledScreen mixin while the user has an ender
     * chest GUI open. Index is 0..26.
     */
    public void updateEnderChestSlot(int index, ItemStack stack) {
        if (index < 0 || index >= enderChestSnapshot.length) return;
        enderChestSnapshot[index] = stack == null ? ItemStack.EMPTY : stack.copy();
    }
}
