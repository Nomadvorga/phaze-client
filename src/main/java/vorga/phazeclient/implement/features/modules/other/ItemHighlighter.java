package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Paints a per-item-type tint behind every matching slot in the
 * player's inventory + hotbar so the user can spot signature items
 * at a glance.
 *
 * <h3>How it matches</h3>
 * Two-tier predicate per slot stack:
 * <ol>
 *   <li>{@link Highlight#vanillaItem()} must equal the stack's
 *       item type. This filters the cheap path - 99% of slots are
 *       eliminated by an identity comparison.</li>
 *   <li>If the highlight has a {@link Highlight#nameNeedle()} (the
 *       FunTime ability variants - трапка, явная пыль, etc.), the
 *       stack's display name (lowercased) must contain the needle.
 *       This separates magic-imbued variants from the vanilla item
 *       (a regular netherite scrap shouldn't pulse trapka-orange).</li>
 * </ol>
 * Per-highlight {@link BooleanSetting} toggles the family on / off
 * so the user can trim the visualizer down to just the items that
 * matter for their loadout.
 *
 * <h3>Why no Custom Presets</h3>
 * The upstream Soup-Better module exposes user-defined presets
 * (query string + colour). The Phaze port keeps the curated
 * highlight list because: (a) the preset pipeline upstream is wired
 * through the dedicated config manager + GUI we don't replicate,
 * and (b) the curated list covers the FT loadout exhaustively.
 * Adding presets is a logical follow-up if needed.
 *
 * <h3>Logic credits</h3>
 * Adapted from
 * {@code winvi.moscow.soupbetter.modules.ItemHighlighterModule}.
 * The Phaze port keeps the same per-item-family colour palette and
 * detection rules.
 */
public final class ItemHighlighter extends Module {
    private static final ItemHighlighter INSTANCE = new ItemHighlighter();

    /**
     * The curated highlight list. Each entry pairs a vanilla item
     * with an optional name needle (for variant-only matches like
     * "трапка" sitting on a netherite scrap stack) and an RGB
     * colour. Order matters only for the per-stack first-match
     * shortcut in {@link #colorForStack}, but in practice no two
     * entries share both their vanilla item and overlapping name
     * needles.
     */
    public enum Highlight {
        DISORIENTATION("Disorientation", Items.ENDER_EYE,            "дезориентация",   0x00FF00),
        FIRE_VORTEX("Fire Vortex",       Items.FIRE_CHARGE,          "огненный смерч",  0xFF8C00),
        REVEALING_DUST("Revealing Dust", Items.SUGAR,                "явная пыль",      0xFFFFFF),
        TOTEM("Totem of Undying",        Items.TOTEM_OF_UNDYING,     null,              0xFFCC00),
        EXPERIENCE_BOTTLE("Experience Bottle", Items.EXPERIENCE_BOTTLE, null,           0x66FFCC),
        TRAPKA("Trapka",                 Items.NETHERITE_SCRAP,      "трапка",          0xC2B28C),
        PLATE("Plate",                   Items.DRIED_KELP,           "пласт",           0x808080),
        GOLDEN_APPLE("Golden Apple",     Items.GOLDEN_APPLE,         null,              0xFFD700),
        ENCHANTED_GOLDEN_APPLE("Enchanted Golden Apple", Items.ENCHANTED_GOLDEN_APPLE, null, 0xFF66CC),
        CHORUS_FRUIT("Chorus Fruit",     Items.CHORUS_FRUIT,         null,              0x9400D3),
        ENDER_PEARL("Ender Pearl",       Items.ENDER_PEARL,          null,              0x66CC99),
        SNOWBALL("Snowball",             Items.SNOWBALL,             null,              0xADD8E5);

        private final String label;
        private final Item vanillaItem;
        private final String nameNeedle;
        private final int rgb;

        Highlight(String label, Item vanillaItem, String nameNeedle, int rgb) {
            this.label = label;
            this.vanillaItem = vanillaItem;
            this.nameNeedle = nameNeedle;
            this.rgb = rgb;
        }

        public String label() { return label; }
        public Item vanillaItem() { return vanillaItem; }
        public String nameNeedle() { return nameNeedle; }
        public int rgb() { return rgb; }
    }

    public final SectionSetting generalSection = new SectionSetting("General");
    public final ValueSetting opacity = new ValueSetting(
            "Opacity",
            "Highlight overlay opacity in percent"
    ).range(0, 100).step(1).setValue(40);

    public final SectionSetting itemsSection = new SectionSetting("Items");
    private final Map<Highlight, BooleanSetting> toggles = new EnumMap<>(Highlight.class);

    private ItemHighlighter() {
        super("item_highlighter", "Item Highlighter", ModuleCategory.UTILITIES);
        opacity.setFullWidth(true);
        java.util.List<vorga.phazeclient.api.feature.module.setting.Setting> all = new java.util.ArrayList<>();
        all.add(generalSection);
        all.add(opacity);
        all.add(itemsSection);
        for (Highlight h : Highlight.values()) {
            BooleanSetting setting = new BooleanSetting(
                    h.label(),
                    "Tint slots holding " + h.label().toLowerCase(Locale.ROOT)
            ).setValue(true);
            setting.setFullWidth(true);
            toggles.put(h, setting);
            all.add(setting);
        }
        setup(all.toArray(new vorga.phazeclient.api.feature.module.setting.Setting[0]));
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

    /**
     * Pre-multiplied ARGB tint the mixins should fill over the slot
     * occupied by {@code stack}, or {@code 0} when the slot
     * shouldn't be tinted (no module / opacity 0 / no matching
     * highlight). Mirrors the contract used by
     * {@code MaceIndicator.colorForStack}: the slot mixins skip a
     * stack when the alpha byte is 0, so opacity 0% effectively
     * hides everything without a separate gate.
     */
    public int colorForStack(ItemStack stack) {
        if (!isEnabled() || stack == null || stack.isEmpty()) {
            return 0;
        }
        int op = opacity.getInt();
        if (op <= 0) {
            return 0;
        }
        String displayName = stack.getName().getString().toLowerCase(Locale.ROOT);
        // Registry-name match is included in the upstream's custom-preset
        // path; we keep the comparison cheap and use only display-name
        // here because every Highlight entry is keyed primarily by the
        // vanilla item, and the registry-name guard never disagrees
        // with item identity for vanilla blocks.
        @SuppressWarnings("unused")
        String registryName = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase(Locale.ROOT);

        for (Highlight h : Highlight.values()) {
            BooleanSetting toggle = toggles.get(h);
            if (toggle == null || !toggle.isValue()) {
                continue;
            }
            if (stack.getItem() != h.vanillaItem()) {
                continue;
            }
            if (h.nameNeedle() != null && !displayName.contains(h.nameNeedle().toLowerCase(Locale.ROOT))) {
                continue;
            }
            int alpha = Math.max(0, Math.min(255, Math.round(op / 100.0F * 255.0F)));
            return (alpha << 24) | (h.rgb() & 0x00FFFFFF);
        }
        return 0;
    }
}
