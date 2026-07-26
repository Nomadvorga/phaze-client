package vorga.phazeclient.api.feature.module;

import lombok.Getter;

@Getter
public enum ModuleCategory {
    OTHER("category.other"),
    UTILITIES("category.utilities"),
    HUD("category.hud"),
    SEARCH("category.search"),
    ALL("category.all");

    private final String localizationKey;

    ModuleCategory(String localizationKey) {
        this.localizationKey = localizationKey;
    }

    public String getLocalizedName() {
        return localizationKey;
    }

    public String getReadableName() {
        return getLocalizedName();
    }

    public String getIdentifier() {
        return this.name().toLowerCase();
    }
}
