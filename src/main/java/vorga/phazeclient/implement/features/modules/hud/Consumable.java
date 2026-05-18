package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.MultiSelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phaze port of the Soup-Better {@code Consumable} HUD module.
 *
 * <h3>What it does</h3>
 * Counts how many of each user-selected item type the player is
 * carrying across their entire inventory (main + offhand + armor)
 * and exposes the result as a list the {@code InGameHudMixin}
 * draws as item icons inside the standard rect HUD background.
 *
 * <h3>Why category HUD</h3>
 * Consumable lives in the {@code HUD} tab so it sits alongside the
 * other HUD widgets the user composes in chat-edit mode. We extend
 * {@link RectHudModule} so the module gets the shared drag /
 * resize / background-blur / preset-colour pipeline, and the
 * 6-arg constructor accepts a custom {@link ModuleCategory} so the
 * tab placement is independent of the inherited HUD machinery.
 *
 * <h3>Render path</h3>
 * Counting / setting introspection lives in this class. Actual
 * pixel-pushing happens in {@code InGameHudMixin.renderConsumableHud}
 * because the Phaze HUD pipeline owns the matrix-stack /
 * scaling / chat-edit drag handling for every rect HUD. The mixin
 * pulls dimensions and item lists from {@link #computeLayout()} so
 * the layout maths stay in one testable place.
 */
public final class Consumable extends RectHudModule {
    private static final Consumable INSTANCE = new Consumable();

    /**
     * Item-display-name -&gt; vanilla {@link Item} mapping. Order
     * matters: {@link MultiSelectSetting#value} renders the toggles
     * in this exact sequence in the GUI, and the iteration order in
     * the live HUD also follows it. Matches the Soup-Better source
     * verbatim so a user migrating between the two clients sees the
     * same item ordering.
     */
    public static final Map<String, Item> ITEM_MAP = new LinkedHashMap<>();
    static {
        ITEM_MAP.put("Snowball", Items.SNOWBALL);
        ITEM_MAP.put("Egg", Items.EGG);
        ITEM_MAP.put("Wind Charge", Items.WIND_CHARGE);
        ITEM_MAP.put("Golden Apple", Items.GOLDEN_APPLE);
        ITEM_MAP.put("Enchanted Golden Apple", Items.ENCHANTED_GOLDEN_APPLE);
        ITEM_MAP.put("Arrow", Items.ARROW);
        ITEM_MAP.put("Spectral Arrow", Items.SPECTRAL_ARROW);
        ITEM_MAP.put("Tipped Arrow", Items.TIPPED_ARROW);
        ITEM_MAP.put("Totem", Items.TOTEM_OF_UNDYING);
        ITEM_MAP.put("Chorus Fruit", Items.CHORUS_FRUIT);
        ITEM_MAP.put("Ender Pearl", Items.ENDER_PEARL);
        ITEM_MAP.put("Firework Rocket", Items.FIREWORK_ROCKET);
        ITEM_MAP.put("Experience Bottle", Items.EXPERIENCE_BOTTLE);
    }

    public final SectionSetting itemsSection = new SectionSetting("Items");

    public final MultiSelectSetting itemTypes = new MultiSelectSetting(
            "Item Types",
            "Item types tracked by the HUD - icons appear only for those whose count is greater than zero"
    ).value(
            "Snowball", "Egg", "Wind Charge", "Golden Apple", "Enchanted Golden Apple",
            "Arrow", "Spectral Arrow", "Tipped Arrow", "Totem", "Chorus Fruit",
            "Ender Pearl", "Firework Rocket", "Experience Bottle"
    ).selected("Golden Apple", "Enchanted Golden Apple", "Totem", "Ender Pearl");

    public final SectionSetting displaySection = new SectionSetting("Display");

    public final SelectSetting layout = new SelectSetting(
            "Layout",
            "Whether icons stack horizontally, vertically, or in a square grid"
    ).value("Line", "Column", "Table").selected("Line");

    public final BooleanSetting showCount = new BooleanSetting(
            "Show Count",
            "Draw the small stack-count number in the bottom-right corner of each icon"
    ).setValue(true);

    public static Consumable getInstance() {
        return INSTANCE;
    }

    private Consumable() {
        super("consumable", "Consumable", ModuleCategory.HUD, 320.0f, 220.0f, 1.0f);
        itemsSection.setFullWidth(true);
        itemTypes.setFullWidth(true);
        displaySection.setFullWidth(true);
        layout.setFullWidth(true);
        showCount.setFullWidth(true);
        setup(itemsSection, itemTypes, displaySection, layout, showCount);
    }

    /**
     * One row of the laid-out HUD: the {@link ItemStack} (with the
     * computed count baked in for the count overlay) plus its grid
     * coordinates inside the icon grid. The mixin walks the list,
     * multiplies coords by {@code iconSize}, and draws each entry.
     */
    public record IconEntry(ItemStack stack, int row, int col) {}

    /**
     * Result of the layout pass: the icons-with-positions list,
     * plus the grid bounds the rect HUD background should size
     * itself to. Calling this on the render thread is fine; nothing
     * here mutates the player or world state.
     */
    public record Layout(List<IconEntry> entries, int cols, int rows, float baseWidth, float baseHeight) {}

    /**
     * Compute the live {@link Layout} for this frame. Walks the
     * inventory, counts each selected item type, then arranges the
     * non-zero entries in the configured grid.
     *
     * <p>Constants ({@code padding}, {@code baseItemSize}) are kept
     * inside the function so the layout maths is self-contained -
     * the mixin only needs to consume the result. Sizes are in
     * "base" pixels (un-scaled); the mixin applies
     * {@link RectHudModule#getHudScale()} on top via the matrix
     * stack, mirroring how {@code renderRectHud} scales its text.
     */
    public Layout computeLayout(boolean chatEditing) {
        MinecraftClient client = MinecraftClient.getInstance();
        List<IconEntry> entries = new ArrayList<>();
        int cols;
        int rows;
        float padding = 3.0f;
        float baseItemSize = 16.0f;
        float itemSize = baseItemSize + 2.0f;

        // Two-pass walk so the visible-set rule is unambiguous:
        //   1. Count every selected item type once.
        //   2. If any of them is in the inventory, show only the
        //      present ones; otherwise (nothing selected is in the
        //      inventory) show every selected entry with its 0 count
        //      so the user can still see what's tracked.
        // In chat-editing mode all entries are forced visible with a
        // placeholder count of 1 so the rect stays draggable on a
        // fresh inventory.
        List<String> selected = itemTypes.getSelected();
        List<ItemStack> presentStacks = new ArrayList<>();
        List<ItemStack> fallbackStacks = new ArrayList<>();
        for (String type : selected) {
            Item item = ITEM_MAP.get(type);
            if (item == null) continue;
            if (chatEditing) {
                fallbackStacks.add(new ItemStack(item, 1));
                continue;
            }
            int count = countItem(client, item);
            if (count > 0) {
                presentStacks.add(new ItemStack(item, count));
            } else {
                fallbackStacks.add(new ItemStack(item, 1));
            }
        }

        List<ItemStack> visibleStacks = chatEditing
                ? fallbackStacks
                : (presentStacks.isEmpty() ? fallbackStacks : presentStacks);

        // Empty-inventory fallback in chat-editing mode when nothing
        // is selected: a single golden apple placeholder so the
        // dragged rect stays positioned.
        if (visibleStacks.isEmpty() && chatEditing) {
            visibleStacks.add(new ItemStack(Items.GOLDEN_APPLE, 1));
        }

        int n = visibleStacks.size();
        if (n == 0) {
            return new Layout(entries, 0, 0, 0.0f, 0.0f);
        }

        switch (layout.getSelected()) {
            case "Line" -> { cols = n; rows = 1; }
            case "Column" -> { cols = 1; rows = n; }
            case "Table" -> {
                cols = (int) Math.ceil(Math.sqrt(n));
                rows = (int) Math.ceil((double) n / cols);
            }
            default -> { cols = n; rows = 1; }
        }

        for (int i = 0; i < n; i++) {
            int r, c;
            switch (layout.getSelected()) {
                case "Column" -> { r = i; c = 0; }
                case "Table" -> { r = i / cols; c = i % cols; }
                default      -> { r = 0;     c = i; }
            }
            entries.add(new IconEntry(visibleStacks.get(i), r, c));
        }

        float width = cols * itemSize + padding * 2.0f;
        float height = rows * itemSize + padding * 2.0f;
        return new Layout(entries, cols, rows, width, height);
    }

    /**
     * Sum of the given item across the player's inventory main
     * (which includes hotbar) plus offhand. Armor slots are
     * excluded because the tracked item types are all consumables
     * that don't fit into armor. Returns 0 if no player.
     */
    private static int countItem(MinecraftClient client, Item item) {
        if (client == null || client.player == null) {
            return 0;
        }
        int sum = 0;
        var inv = client.player.getInventory();
        int size = inv.size();
        for (int i = 0; i < size; i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && stack.isOf(item)) {
                sum += stack.getCount();
            }
        }
        return sum;
    }

    @Override
    public String getDescription() {
        return "Counts consumable items in your inventory and shows them as a draggable icon grid";
    }

    @Override
    public String getIcon() {
        return "consumable.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
