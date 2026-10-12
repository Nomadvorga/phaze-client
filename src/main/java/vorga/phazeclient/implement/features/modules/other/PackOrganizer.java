package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;

public class PackOrganizer extends Module {
    private static final PackOrganizer INSTANCE = new PackOrganizer();

    public final BooleanSetting searchBar = new BooleanSetting("Search Bar", "Show quick search bar on resource pack screen").setValue(true);

    private PackOrganizer() {
        super("pack_organizer", "Pack Organizer", ModuleCategory.OTHER);
        setup(searchBar);
    }

    public static PackOrganizer getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Adds quick search bar to resource pack selection screen";
    }

    @Override
    public String getIcon() {
        return "shulker_preview.png";
    }
}
