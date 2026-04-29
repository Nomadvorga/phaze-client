package vorga.phazeclient.api.feature.module;

import lombok.Getter;

@Getter
public enum ModuleCategory {
    VISUALS("category.visuals"),
    WORLD("category.world"),
    CLIENT("category.client"),
    OTHER("category.other"),
    HUD("category.hud"),
    SEARCH("category.search");

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
