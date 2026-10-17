package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;

public class PackOrganizer extends Module {
    private static final PackOrganizer INSTANCE = new PackOrganizer();

    public final BooleanSetting searchBar = new BooleanSetting("Search Bar", "Show quick search bar on resource pack screen").setValue(true);
    public final BooleanSetting packPreviews = new BooleanSetting("Pack Previews", "Show real pack textures and resource details when hovering a pack").setValue(true);

    private PackOrganizer() {
        super("pack_organizer", "Pack Organizer", ModuleCategory.OTHER);
        setup(searchBar, packPreviews);
    }

    public static PackOrganizer getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Adds a quick search bar and a real texture preview to the resource pack screen";
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public String getIcon() {
        return "shulker_preview.png";
    }
}
