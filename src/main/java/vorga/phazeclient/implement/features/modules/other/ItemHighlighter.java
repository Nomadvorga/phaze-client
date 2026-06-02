package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.feature.module.setting.implement.ButtonSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ItemPickerSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.base.util.ServerUtil;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Paints a per-item-type tint behind every matching slot in the
 * player's inventory + hotbar so the user can spot signature items
 * at a glance.
 */
public final class ItemHighlighter extends Module {
    private static final int RESULT_CACHE_MAX = 512;
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
    private final Map<Item, List<CompiledEntry>> compiledEntriesByItem = new IdentityHashMap<>();
    private final Map<ItemStack, Integer> preparedColorCache = new IdentityHashMap<>();
    private final Map<ResultCacheKey, Integer> resolvedColorCache = new LinkedHashMap<>(128, 0.75F, true);
    private long compiledEntriesTick = Long.MIN_VALUE;
    private int compiledStateMask = Integer.MIN_VALUE;
    private int compiledEntriesFingerprint = Integer.MIN_VALUE;
    private int compiledAlpha = 0;

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
        refreshCompiledEntries();
        return resolveCompiledColor(stack);
    }

    public void beginRenderPass() {
        refreshCompiledEntries();
        preparedColorCache.clear();
    }

    public int colorForPreparedStack(ItemStack stack) {
        if (!isEnabled() || stack == null || stack.isEmpty()) {
            return 0;
        }
        Integer cached = preparedColorCache.get(stack);
        if (cached != null) {
            return cached;
        }

        int color = resolveCompiledColor(stack);
        preparedColorCache.put(stack, color);
        return color;
    }

    private int resolveCompiledColor(ItemStack stack) {
        if (compiledAlpha <= 0) {
            return 0;
        }

        List<CompiledEntry> candidates = compiledEntriesByItem.get(stack.getItem());
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }

        boolean needsDisplayName = false;
        for (CompiledEntry entry : candidates) {
            if (entry.requiresDisplayName()) {
                needsDisplayName = true;
                break;
            }
        }

        String normalizedDisplay = needsDisplayName ? normalize(stack.getName().getString()) : "";
        ResultCacheKey cacheKey = new ResultCacheKey(stack.getItem(), normalizedDisplay);
        Integer cached = resolvedColorCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        int resolvedColor = 0;
        for (CompiledEntry entry : candidates) {
            if (!entry.requiresDisplayName()) {
                resolvedColor = (compiledAlpha << 24) | entry.rgb();
                break;
            }
            if (entry.matches(normalizedDisplay)) {
                resolvedColor = (compiledAlpha << 24) | entry.rgb();
                break;
            }
        }

        cacheResolvedColor(cacheKey, resolvedColor);
        return resolvedColor;
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
                compiledEntriesTick = Long.MIN_VALUE;
                break;
            }
        }
    }

    private void refreshCompiledEntries() {
        MinecraftClient client = MinecraftClient.getInstance();
        long tickKey = client != null && client.world != null
                ? client.world.getTime()
                : System.nanoTime() / 50_000_000L;
        int contextMask = (isSingleplayerContext() ? 1 : 0)
                | (ServerUtil.isFunTimeServer() ? 1 << 1 : 0)
                | (ServerUtil.isHolyWorldServer() ? 1 << 2 : 0)
                | (Math.max(0, opacity.getInt()) << 8)
                | (isEnabled() ? 1 << 30 : 0);
        int entriesFingerprint = computeEntriesFingerprint();
        boolean cacheStateChanged = contextMask != compiledStateMask
                || entriesFingerprint != compiledEntriesFingerprint;

        if (tickKey == compiledEntriesTick
                && !cacheStateChanged) {
            return;
        }

        compiledEntriesTick = tickKey;
        compiledStateMask = contextMask;
        compiledEntriesFingerprint = entriesFingerprint;
        if (cacheStateChanged) {
            resolvedColorCache.clear();
        }
        compiledEntriesByItem.clear();
        compiledAlpha = 0;

        int op = opacity.getInt();
        if (!isEnabled() || op <= 0) {
            return;
        }

        compiledAlpha = Math.max(0, Math.min(255, Math.round(op / 100.0F * 255.0F)));
        for (ItemPickerSetting entry : entries) {
            if (!entry.isVisible() || !entry.isActive() || !entry.isEnabled() || !entry.hasSelection()) {
                continue;
            }

            Item item = resolveItem(entry.getItemId());
            if (item == null || item == Items.AIR) {
                continue;
            }

            compiledEntriesByItem
                    .computeIfAbsent(item, ignored -> new ArrayList<>(2))
                    .add(CompiledEntry.of(entry));
        }
    }

    private void cacheResolvedColor(ResultCacheKey key, int color) {
        if (resolvedColorCache.size() >= RESULT_CACHE_MAX && !resolvedColorCache.containsKey(key)) {
            var iterator = resolvedColorCache.entrySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        resolvedColorCache.put(key, color);
    }

    private int computeEntriesFingerprint() {
        int hash = 1;
        for (ItemPickerSetting entry : entries) {
            hash = 31 * hash + (entry.isVisible() ? 1 : 0);
            hash = 31 * hash + (entry.isActive() ? 1 : 0);
            hash = 31 * hash + (entry.isEnabled() ? 1 : 0);
            hash = 31 * hash + entry.getHighlightColor();
            hash = 31 * hash + Objects.hashCode(entry.getItemId());
            hash = 31 * hash + Objects.hashCode(entry.getMatchName());
        }
        return hash;
    }

    private static Item resolveItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Items.AIR;
        }
        try {
            return Registries.ITEM.get(Identifier.of(itemId));
        } catch (Exception ignored) {
            return Items.AIR;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record CompiledEntry(int rgb, String exactName, String[] partialTokens) {
        private static CompiledEntry of(ItemPickerSetting setting) {
            String matchName = setting.getMatchName();
            if (matchName == null || matchName.isBlank()) {
                return new CompiledEntry(setting.getHighlightColor() & 0x00FFFFFF, null, new String[0]);
            }

            if (!setting.isPartialMatch()) {
                return new CompiledEntry(
                        setting.getHighlightColor() & 0x00FFFFFF,
                        normalize(matchName),
                        new String[0]
                );
            }

            List<String> tokens = new ArrayList<>();
            for (String token : matchName.split("\\|")) {
                String normalized = normalize(token);
                if (!normalized.isBlank()) {
                    tokens.add(normalized);
                }
            }

            return new CompiledEntry(
                    setting.getHighlightColor() & 0x00FFFFFF,
                    null,
                    tokens.toArray(new String[0])
            );
        }

        private boolean requiresDisplayName() {
            return exactName != null || partialTokens.length > 0;
        }

        private boolean matches(String normalizedDisplay) {
            if (exactName != null) {
                return exactName.equals(normalizedDisplay);
            }

            for (String token : partialTokens) {
                if (normalizedDisplay.contains(token)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record ResultCacheKey(Item item, String normalizedDisplay) {
    }
}
