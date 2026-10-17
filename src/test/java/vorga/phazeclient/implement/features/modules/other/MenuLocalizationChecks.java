package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.base.util.Lang;

import java.util.List;

/** Checks the display/storage boundary without loading a saved user config. */
public final class MenuLocalizationChecks {
    private static int checks;

    public static void main(String[] args) {
        String previousLocale = Lang.getActive();
        try {
            for (String locale : new String[]{Lang.EN, Lang.RU}) {
                Lang.setActive(locale);
                for (String title : new String[]{"Pack Organizer", "Low HP Warning", "Auto Eat", "Custom Glint", "3D Skins", "Kill Sounds", "Themes"}) {
                    Module module = new Module("test_module", title, ModuleCategory.OTHER);
                    check(module.getLocalizedName().equals(title), "module title stays English: " + title);
                    check(module.getVisibleName().equals(title), "canonical title unchanged");
                    check(module.getIdentifier().equals("test_module"), "module ID unchanged");
                }
                for (ModuleCategory category : ModuleCategory.values()) {
                    check(category.getReadableName().equals(category.name()), "top-level GUI tab stays English");
                }
            }
            Lang.setActive(Lang.RU);
            check(new SectionSetting("General").getName().equals("Общие"), "section inside settings is translated");
            SelectSetting mode = new SelectSetting("Mode", "Glint color mode")
                    .value("Custom", "Rainbow", "Turquoise", "Golden", "Ruby").selected("Turquoise");
            check(mode.getName().equals("Режим"), "setting label is translated");
            check(mode.getDescription().equals("Режим цвета зачарования"), "setting description is translated");
            check(Lang.translate(mode.getSelected()).equals("Бирюзовый"), "selected value has a Russian display label");
            check(Lang.translate("Golden").equals("Золотой"), "golden preset is translated");
            check(Lang.translate("Ruby").equals("Рубиновый"), "ruby preset is translated");
            check(mode.getRawName().equals("Mode") && mode.getSelected().equals("Turquoise"), "config keys/values remain English");
            check(mode.getRawDescription().equals("Glint color mode"), "raw description unchanged");
            Lang.setActive(Lang.EN);
            check(mode.getName().equals("Mode") && Lang.translate(mode.getSelected()).equals("Turquoise"), "English settings remain English");
            checkModuleGrouping();
            String lowHpDescription = "Warning vignette tint and intensity (alpha)";
            Lang.setActive(Lang.RU);
            check(Lang.translate(lowHpDescription).equals("Цвет и интенсивность виньетки предупреждения (альфа-канал)"),
                    "new Low HP color description is translated");
            Lang.setActive(Lang.EN);
            check(Lang.translate(lowHpDescription).equals(lowHpDescription), "Low HP English description unchanged");
        } finally {
            Lang.setActive(previousLocale);
        }
        System.out.println("Menu localization checks passed: " + checks);
    }

    private static void checkModuleGrouping() {
        ThreeDSkins skins = ThreeDSkins.getInstance();
        check(skins.settings().equals(List.of(skins.voxelsSection, skins.voxelThickness, skins.maxDistance,
                skins.visibleSection, skins.showOthers, skins.hat3D, skins.jacket3D, skins.sleeves3D, skins.pants3D)),
                "3D Skins groups voxel controls before visibility controls in the requested order");
        check(skins.voxelThickness.isFullWidth() && skins.maxDistance.isFullWidth() && skins.showOthers.isFullWidth(),
                "voxel sliders and Show Others each span a full row");
        for (Setting setting : List.of(skins.hat3D, skins.jacket3D, skins.sleeves3D, skins.pants3D)) {
            check(setting.isVisible() && !setting.isFullWidth(), "3D layer toggles use the normal two-column layout");
        }
        check(skins.voxelsSection.getRawName().equals("Voxels settings")
                && skins.visibleSection.getRawName().equals("Visible"), "section keys remain English");
        checkLocalizedModule(skins, "three_d_skins", "3D Skins", List.of("Voxel Depth", "Max Distance",
                "Show Others", "3D Hat", "3D Jacket", "3D Sleeves", "3D Pants"), 2);
        Lang.setActive(Lang.RU);
        check(skins.voxelsSection.getName().equals("Настройки вокселей"), "voxel section Russian label");
        check(skins.visibleSection.getName().equals("Видимость"), "visibility section Russian label");

        GlintColorizer glint = GlintColorizer.getInstance();
        check(glint.settings().equals(List.of(glint.generalSection, glint.mode, glint.customColor, glint.speed)),
                "Custom Glint has one General section before its existing controls");
        checkLocalizedModule(glint, "glint_colorizer", "Custom Glint", List.of("Mode", "Color", "Speed"), 1);

        KillSounds sounds = KillSounds.getInstance();
        check(sounds.settings().equals(List.of(sounds.generalSection, sounds.soundType, sounds.volume,
                sounds.pitch, sounds.playersOnly)), "Kill Sounds has one General section before its existing controls");
        List<String> soundChoices = List.of("Laser", "Ding", "Destroy", "Hit", "Pop", "Crunch", "Pling", "Bell",
                "Orb XP", "Level Up", "Anvil");
        checkLocalizedModule(sounds, "kill_sounds", "Kill Sounds", List.of("Sound", "Volume", "Pitch", "Players Only"), 1);
        check(sounds.soundType.getList().equals(soundChoices), "sound choice config values remain English");
        Lang.setActive(Lang.RU);
        for (String choice : soundChoices) {
            check(!Lang.translate(choice).equals(choice), "sound choice has a Russian display label: " + choice);
        }
    }

    private static void checkLocalizedModule(Module module, String identifier, String title,
                                             List<String> configKeys, int sectionCount) {
        String rawDescription = module.getDescription();
        long sections = module.settings().stream().filter(SectionSetting.class::isInstance).count();
        check(sections == sectionCount, title + ": expected section count");
        for (String locale : new String[]{Lang.EN, Lang.RU, Lang.EN}) {
            Lang.setActive(locale);
            boolean russian = locale.equals(Lang.RU);
            check(module.getIdentifier().equals(identifier) && module.getLocalizedName().equals(title),
                    title + ": identifier and title unchanged across locale switches");
            check(module.getDescription().equals(rawDescription), title + ": raw description unchanged");
            check(module.getDisplayDescription().equals(rawDescription) != russian,
                    title + ": module description uses the active locale");
            check(module.settings().stream().filter(Setting::isSaveToConfig).map(Setting::getRawName).toList().equals(configKeys),
                    title + ": saved config keys unchanged across locale switches");
            for (Setting setting : module.settings()) {
                check(setting.getName().equals(setting.getRawName()) != russian,
                        title + ": localized setting/section label: " + setting.getRawName());
                if (!setting.getRawDescription().isEmpty()) {
                    check(setting.getDescription().equals(setting.getRawDescription()) != russian,
                            title + ": localized setting description: " + setting.getRawName());
                }
                if (setting instanceof SectionSetting) {
                    check(setting.isFullWidth() && setting.isVisible() && !setting.isSaveToConfig(),
                            title + ": section is a visible full-width display-only heading");
                }
            }
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
