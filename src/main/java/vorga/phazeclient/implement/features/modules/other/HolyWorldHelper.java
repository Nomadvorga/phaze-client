package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.MultiSelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.TextSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.implement.features.modules.client.Theme;
import vorga.phazeclient.implement.menu.MenuPalette;
import vorga.phazeclient.implement.menu.MenuPalettes;

public final class HolyWorldHelper extends Module {
    private static final String[] COLOR_PRESETS = {
            "Black", "Lunar Blue", "Mocha Gold", "Rose Quartz", "Emerald Frost",
            "Arctic Mint", "Crimson Silk", "Solar Ember", "Midnight Bloom",
            "Desert Mirage", "Sapphire Steel", "Velvet Plum", "Frosted Peach",
            "Moss Smoke", "Polar Night", "Snow", "Obsidian", "Nebula",
            "Coral", "Jade", "Sunset", "Violet", "Ocean"
    };
    private static final HolyWorldHelper INSTANCE = new HolyWorldHelper();

    public final SectionSetting abilitiesSection = new SectionSetting("Abilities");
    public final MultiSelectSetting enabledAbilities = new MultiSelectSetting(
            "Enabled Abilities", "Choose HolyWorld abilities to visualize"
    ).value("Trap", "Jake's Lamp", "Stun", "Explosive Trap")
            .selected("Trap", "Jake's Lamp", "Stun", "Explosive Trap");

    public final SectionSetting visualSection = new SectionSetting("Visual");
    public final ValueSetting fillOpacity = new ValueSetting(
            "Fill Opacity", "Trap wall fill opacity"
    ).range(0.0F, 100.0F).step(1.0F).setValue(18.0F);

    public final SectionSetting boxColorSection = new SectionSetting("Box Color");
    public final vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting boxUseThemeColor = new vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting(
            "Theme Color", "Use the active Theme accent for Trap"
    ).setValue(true);
    public final SelectSetting boxColorPreset = new SelectSetting(
            "Color Preset", "Fixed Trap color when Theme Color is off"
    ).value(COLOR_PRESETS).selected("Lunar Blue")
            .visible(() -> !boxUseThemeColor.isValue());

    public final SectionSetting nameSection = new SectionSetting("Name Overrides");
    public final TextSetting trapName = new TextSetting("Trap Name", "Held item name for Trap")
            .setText("трапка").setMax(48);
    public final TextSetting jakesLampName = new TextSetting("Jake's Lamp Name", "Held item name for Jake's Lamp")
            .setText("светильник джейка").setMax(48);
    public final TextSetting stunName = new TextSetting("Stun Name", "Held item name for Stun")
            .setText("стан").setMax(48);
    public final TextSetting explosiveTrapName = new TextSetting("Explosive Trap Name", "Held item name for Explosive Trap")
            .setText("взрывная трапка").setMax(48);

    private HolyWorldHelper() {
        super("holyworld_helper", "HolyWorld Helper", ModuleCategory.UTILITIES);
        enabledAbilities.setFullWidth(true);
        fillOpacity.setFullWidth(true);
        boxUseThemeColor.setFullWidth(true);
        boxColorPreset.setFullWidth(true);
        trapName.setFullWidth(true);
        jakesLampName.setFullWidth(true);
        stunName.setFullWidth(true);
        explosiveTrapName.setFullWidth(true);
        setup(abilitiesSection, enabledAbilities, visualSection, fillOpacity,
                boxColorSection, boxUseThemeColor, boxColorPreset, nameSection,
                trapName, jakesLampName, stunName, explosiveTrapName);
    }

    public static HolyWorldHelper getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "HolyWorld ability range visualizer";
    }

    @Override
    public String getIcon() {
        return "ft_helper.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public HighlightType getHighlightType() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || client == null || client.player == null) {
            return HighlightType.NONE;
        }
        String itemName = client.player.getMainHandStack().getName().getString().toLowerCase(java.util.Locale.ROOT);
        if (enabledAbilities.getSelected().contains("Trap")
                && client.player.getMainHandStack().isOf(Items.POPPED_CHORUS_FRUIT)
                && matches(itemName, trapName.getText())) {
            return HighlightType.TRAP;
        }
        if (enabledAbilities.getSelected().contains("Jake's Lamp") && matches(itemName, jakesLampName.getText())) {
            return HighlightType.JAKES_LAMP;
        }
        if (enabledAbilities.getSelected().contains("Stun") && matches(itemName, stunName.getText())) {
            return HighlightType.STUN;
        }
        if (enabledAbilities.getSelected().contains("Explosive Trap") && matches(itemName, explosiveTrapName.getText())) {
            return HighlightType.EXPLOSIVE_TRAP;
        }
        return HighlightType.NONE;
    }

    private static boolean matches(String itemName, String configuredName) {
        return configuredName != null && !configuredName.isBlank()
                && itemName.contains(configuredName.toLowerCase(java.util.Locale.ROOT));
    }

    public enum HighlightType {
        NONE,
        TRAP,
        JAKES_LAMP,
        STUN,
        EXPLOSIVE_TRAP
    }

    public int resolveBoxColor() {
        int rgb;
        if (!boxUseThemeColor.isValue() && "Black".equalsIgnoreCase(boxColorPreset.getSelected())) {
            rgb = 0;
        } else {
            MenuPalette palette = boxUseThemeColor.isValue()
                    ? Theme.getInstance().getCurrentMenuPalette()
                    : MenuPalettes.byName(boxColorPreset.getSelected());
            rgb = palette.chipActive() & 0x00FFFFFF;
        }
        return 0xFF000000 | rgb;
    }
}
