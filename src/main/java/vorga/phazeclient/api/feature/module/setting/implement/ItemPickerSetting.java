package vorga.phazeclient.api.feature.module.setting.implement;

import lombok.Getter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import vorga.phazeclient.api.feature.module.setting.Setting;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

@Getter
public class ItemPickerSetting extends Setting {
    private static final int[] DEFAULT_COLOR_PRESETS = {
            0x63D2FF,
            0xFFB347,
            0x8AE99A,
            0xFF8FAB,
            0xB59BFF,
            0xFFD86B,
            0x72E1D1,
            0xFF6F61
    };

    private boolean active;
    private boolean defaultActive;

    private boolean enabled = true;
    private boolean defaultEnabled = true;

    private int highlightColor;
    private int defaultHighlightColor;

    private String itemId = "";
    private String defaultItemId = "";

    private String displayName = "";
    private String defaultDisplayName = "";

    private String matchName = "";
    private String defaultMatchName = "";

    private boolean partialMatch;
    private boolean defaultPartialMatch;

    private boolean pickerEnabled = true;
    private boolean defaultPickerEnabled = true;

    private boolean clearable = true;
    private boolean defaultClearable = true;

    private String rowLabel = "";
    private int[] colorPresets = DEFAULT_COLOR_PRESETS.clone();

    public ItemPickerSetting(String name, String description, int defaultColor) {
        super(name, description);
        this.highlightColor = defaultColor;
        this.defaultHighlightColor = defaultColor;
    }

    public ItemPickerSetting visible(Supplier<Boolean> visible) {
        setVisible(visible);
        return this;
    }

    public ItemPickerSetting rowLabel(String rowLabel) {
        this.rowLabel = rowLabel == null ? "" : rowLabel;
        return this;
    }

    public ItemPickerSetting colorPresets(int... presets) {
        if (presets != null && presets.length > 0) {
            this.colorPresets = Arrays.copyOf(presets, presets.length);
        }
        return this;
    }

    public ItemPickerSetting fixedSelection(Item item, String matchName) {
        String resolvedItemId = Registries.ITEM.getId(item).toString();
        String safeMatch = matchName == null ? "" : matchName;

        active = true;
        defaultActive = true;

        itemId = resolvedItemId;
        defaultItemId = resolvedItemId;
        displayName = "";
        defaultDisplayName = "";
        this.matchName = safeMatch;
        defaultMatchName = safeMatch;
        partialMatch = !safeMatch.isBlank();
        defaultPartialMatch = partialMatch;

        pickerEnabled = false;
        defaultPickerEnabled = false;
        clearable = false;
        defaultClearable = false;
        return this;
    }

    public boolean hasSelection() {
        return itemId != null && !itemId.isBlank();
    }

    public String getDisplayName() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return itemId == null ? "" : itemId;
    }

    public String getRowTitle() {
        if (pickerEnabled && hasSelection() && displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        if (rowLabel != null && !rowLabel.isBlank()) {
            return vorga.phazeclient.base.util.Lang.translate(rowLabel);
        }
        if (hasSelection()) {
            return getDisplayName();
        }
        return getName();
    }

    public boolean isCustomPicker() {
        return pickerEnabled;
    }

    public int getAccentColor() {
        return highlightColor;
    }

    public void setActive(boolean active) {
        if (this.active != active) {
            this.active = active;
            notifyChange();
        }
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            notifyChange();
        }
    }

    public void setHighlightColor(int highlightColor) {
        if (this.highlightColor != highlightColor) {
            this.highlightColor = highlightColor;
            notifyChange();
        }
    }

    public void cycleHighlightColor() {
        if (colorPresets.length == 0) {
            return;
        }

        int current = highlightColor & 0x00FFFFFF;
        int nextIndex = 0;
        for (int i = 0; i < colorPresets.length; i++) {
            if ((colorPresets[i] & 0x00FFFFFF) == current) {
                nextIndex = (i + 1) % colorPresets.length;
                break;
            }
        }
        setHighlightColor(0xFF000000 | (colorPresets[nextIndex] & 0x00FFFFFF));
    }

    public void selectFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        String selectedItemId = Registries.ITEM.getId(stack.getItem()).toString();
        String selectedDisplayName = stack.getName().getString();
        String vanillaName = new ItemStack(stack.getItem()).getName().getString();
        String selectedMatchName = selectedDisplayName.equalsIgnoreCase(vanillaName) ? "" : selectedDisplayName;

        applySelection(true, selectedItemId, selectedDisplayName, selectedMatchName, false, true);
    }

    public void setSerializedState(boolean active, String itemId, String displayName, String matchName) {
        applySelection(active, itemId, displayName, matchName, partialMatch, true);
    }

    public void setSerializedState(boolean active, String itemId, String displayName, String matchName, boolean enabled, int highlightColor) {
        boolean changed = this.enabled != enabled || this.highlightColor != highlightColor;
        this.enabled = enabled;
        this.highlightColor = highlightColor;
        applySelection(active, itemId, displayName, matchName, partialMatch, !changed);
        if (changed) {
            notifyChange();
        }
    }

    public void clearSelection() {
        applySelection(false, "", "", "", false, true);
    }

    public boolean matches(ItemStack stack) {
        return enabled && matchesSelectedItem(stack);
    }

    public boolean matchesSelectedItem(ItemStack stack) {
        if (!active || !hasSelection() || stack == null || stack.isEmpty()) {
            return false;
        }

        String stackItemId = Registries.ITEM.getId(stack.getItem()).toString();
        if (!stackItemId.equals(itemId)) {
            return false;
        }

        if (matchName == null || matchName.isBlank()) {
            return true;
        }

        String display = normalize(stack.getName().getString());
        if (partialMatch) {
            for (String token : matchName.split("\\|")) {
                String normalizedToken = normalize(token);
                if (!normalizedToken.isBlank() && display.contains(normalizedToken)) {
                    return true;
                }
            }
            return false;
        }

        return display.equals(normalize(matchName));
    }

    public ItemStack createPreviewStack() {
        if (!hasSelection()) {
            return ItemStack.EMPTY;
        }

        try {
            Item item = Registries.ITEM.get(Identifier.of(itemId));
            if (item == null || item == Items.AIR) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item);
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public boolean isModified() {
        return active != defaultActive
                || enabled != defaultEnabled
                || highlightColor != defaultHighlightColor
                || !Objects.equals(itemId, defaultItemId)
                || !Objects.equals(displayName, defaultDisplayName)
                || !Objects.equals(matchName, defaultMatchName)
                || partialMatch != defaultPartialMatch;
    }

    @Override
    public void reset() {
        boolean changed = active != defaultActive
                || enabled != defaultEnabled
                || highlightColor != defaultHighlightColor
                || !Objects.equals(itemId, defaultItemId)
                || !Objects.equals(displayName, defaultDisplayName)
                || !Objects.equals(matchName, defaultMatchName)
                || partialMatch != defaultPartialMatch
                || pickerEnabled != defaultPickerEnabled
                || clearable != defaultClearable;

        active = defaultActive;
        enabled = defaultEnabled;
        highlightColor = defaultHighlightColor;
        itemId = defaultItemId;
        displayName = defaultDisplayName;
        matchName = defaultMatchName;
        partialMatch = defaultPartialMatch;
        pickerEnabled = defaultPickerEnabled;
        clearable = defaultClearable;

        if (changed) {
            notifyChange();
        }
    }

    private void applySelection(boolean active, String itemId, String displayName, String matchName, boolean partialMatch, boolean notify) {
        String safeItemId = itemId == null ? "" : itemId;
        String safeDisplayName = displayName == null ? "" : displayName;
        String safeMatchName = matchName == null ? "" : matchName;

        boolean changed = this.active != active
                || !Objects.equals(this.itemId, safeItemId)
                || !Objects.equals(this.displayName, safeDisplayName)
                || !Objects.equals(this.matchName, safeMatchName)
                || this.partialMatch != partialMatch;

        this.active = active;
        this.itemId = safeItemId;
        this.displayName = safeDisplayName;
        this.matchName = safeMatchName;
        this.partialMatch = partialMatch;

        if (changed && notify) {
            notifyChange();
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
