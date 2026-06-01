package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.feature.module.setting.implement.ButtonSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ItemPickerSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.base.util.ServerUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Paints a per-item-type tint behind every matching slot in the
 * player's inventory + hotbar so the user can spot signature items
 * at a glance.
 */
public final class ItemHighlighter extends Module {
    private static final int[] CUSTOM_ITEM_COLORS = {
            0x63D2FF,
            0xFFB347,
            0x8AE99A,
            0xFF8FAB,
            0xB59BFF,
            0xFFD86B,
            0x72E1D1,
            0xFF6F61
    };

    private static final ItemHighlighter INSTANCE = new ItemHighlighter();

    public final SectionSetting generalSection = new SectionSetting("General");
    public final ValueSetting opacity = new ValueSetting(
            "Opacity",
            "Highlight overlay opacity in percent"
    ).range(0, 100).step(1).setValue(40);

    public final SectionSetting basicSection = new SectionSetting("Basic");
    public final SectionSetting funTimeSection = new SectionSetting("FunTime").visible(this::shouldShowFunTimeEntries);
    public final SectionSetting holyWorldSection = new SectionSetting("HolyWorld").visible(this::shouldShowHolyWorldEntries);
    public final SectionSetting customSection = new SectionSetting("Custom");
    public final ButtonSetting addItem = new ButtonSetting(
            "Add Item",
            "Reveal another custom highlight row and pick an inventory item"
    ).setButtonName("Add").visible(this::hasInactiveCustomItem);

    private final List<ItemPickerSetting> entries = new ArrayList<>();
    private final List<ItemPickerSetting> customItems = new ArrayList<>();

    private ItemHighlighter() {
        super("item_highlighter", "Item Highlighter", ModuleCategory.UTILITIES);
        opacity.setFullWidth(true);
        addItem.setFullWidth(true);
        addItem.setRunnable(this::activateNextCustomItem);

        List<Setting> all = new ArrayList<>();
        all.add(generalSection);
        all.add(opacity);

        all.add(basicSection);
        addBuiltIn(all, "Totem of Undying", Items.TOTEM_OF_UNDYING, "", 0xFFCC00, () -> true);
        addBuiltIn(all, "Experience Bottle", Items.EXPERIENCE_BOTTLE, "", 0x66FFCC, () -> true);
        addBuiltIn(all, "Golden Apple", Items.GOLDEN_APPLE, "", 0xFFD700, () -> true);
        addBuiltIn(all, "Enchanted Golden Apple", Items.ENCHANTED_GOLDEN_APPLE, "", 0xFF66CC, () -> true);
        addBuiltIn(all, "Chorus Fruit", Items.CHORUS_FRUIT, "", 0x9400D3, () -> true);
        addBuiltIn(all, "Ender Pearl", Items.ENDER_PEARL, "", 0x66CC99, () -> true);

        all.add(funTimeSection);
        addBuiltIn(all, "Disorientation", Items.ENDER_EYE, "дезориентация", 0x00FF00, this::shouldShowFunTimeEntries);
        addBuiltIn(all, "Revealing Dust", Items.SUGAR, "явная пыль", 0xFFFFFF, this::shouldShowFunTimeEntries);
        addBuiltIn(all, "Trapka", Items.NETHERITE_SCRAP, "трапка", 0xC2B28C, this::shouldShowFunTimeEntries);
        addBuiltIn(all, "Plate", Items.DRIED_KELP, "пласт", 0x808080, this::shouldShowFunTimeEntries);
        addBuiltIn(all, "Freeze Snowball", Items.SNOWBALL, "снежок заморозки", 0xADD8E5, this::shouldShowFunTimeEntries);

        all.add(holyWorldSection);
        addBuiltIn(all, "Trapka 2", Items.POPPED_CHORUS_FRUIT, "трапка 2|прожаренный плод хоруса", 0xFF8C5A, this::shouldShowHolyWorldEntries);
        addBuiltIn(all, "Stun", Items.NETHER_STAR, "стан|звезда визера", 0x7BA8FF, this::shouldShowHolyWorldEntries);
        addBuiltIn(all, "Jack O'Lantern", Items.JACK_O_LANTERN, "светильник джека|jack o'lantern", 0xFFB11A, this::shouldShowHolyWorldEntries);
        addBuiltIn(all, "Explosive Trapka", Items.PRISMARINE_SHARD, "взрывная трапка|осколок призмарина", 0x62E1C7, this::shouldShowHolyWorldEntries);

        all.add(customSection);
        all.add(addItem);
        for (int i = 0; i < CUSTOM_ITEM_COLORS.length; i++) {
            final int slotIndex = i;
            ItemPickerSetting customItem = new ItemPickerSetting(
                    "Custom Item " + (i + 1),
                    "Highlight a custom inventory item selected from the picker",
                    CUSTOM_ITEM_COLORS[i]
            ).rowLabel("Custom Item Slot " + (i + 1))
                    .colorPresets(CUSTOM_ITEM_COLORS)
                    .visible(() -> customItemVisible(slotIndex));
            customItem.setFullWidth(true);
            customItems.add(customItem);
            entries.add(customItem);
            all.add(customItem);
        }

        setup(all.toArray(new Setting[0]));
    }

    public static ItemHighlighter getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Tints inventory slots holding signature items so they're visible at a glance";
    }

    @Override
    public String getIcon() {
        return "item_highlighter.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public int colorForStack(ItemStack stack) {
        if (!isEnabled() || stack == null || stack.isEmpty()) {
            return 0;
        }
        int op = opacity.getInt();
        if (op <= 0) {
            return 0;
        }
        int alpha = Math.max(0, Math.min(255, Math.round(op / 100.0F * 255.0F)));

        for (ItemPickerSetting entry : entries) {
            if (!entry.isVisible()) {
                continue;
            }
            if (!entry.matches(stack)) {
                continue;
            }
            return (alpha << 24) | (entry.getHighlightColor() & 0x00FFFFFF);
        }

        return 0;
    }

    private void addBuiltIn(List<Setting> all, String label, Item item, String matchName, int color, Supplier<Boolean> visible) {
        ItemPickerSetting setting = new ItemPickerSetting(
                label,
                "Tint slots holding " + label.toLowerCase(Locale.ROOT),
                color
        ).fixedSelection(item, matchName)
                .rowLabel(label)
                .visible(visible);
        setting.setFullWidth(true);
        entries.add(setting);
        all.add(setting);
    }

    private boolean hasActiveCustomItem() {
        return customItems.stream().anyMatch(ItemPickerSetting::isActive);
    }

    private boolean hasInactiveCustomItem() {
        return customItems.stream().anyMatch(customItem -> !customItem.isActive());
    }

    private boolean customItemVisible(int index) {
        return index >= 0 && index < customItems.size() && customItems.get(index).isActive();
    }

    private boolean shouldShowFunTimeEntries() {
        return isSingleplayerContext() || ServerUtil.isFunTimeServer();
    }

    private boolean shouldShowHolyWorldEntries() {
        return isSingleplayerContext() || ServerUtil.isHolyWorldServer();
    }

    private static boolean isSingleplayerContext() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.isInSingleplayer();
    }

    private void activateNextCustomItem() {
        for (ItemPickerSetting customItem : customItems) {
            if (!customItem.isActive()) {
                customItem.setActive(true);
                break;
            }
        }
    }
}
