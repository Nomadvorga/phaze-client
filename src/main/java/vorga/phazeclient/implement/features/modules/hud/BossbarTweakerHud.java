package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public class BossbarTweakerHud extends Module {
    private static final BossbarTweakerHud INSTANCE = new BossbarTweakerHud();

    public final ValueSetting scale = new ValueSetting("Scale", "Bossbar size scale")
            .range(0.5f, 1.5f).step(0.05f).setValue(1.0f);
    public final ValueSetting offsetY = new ValueSetting("Y Offset", "Vertical offset in pixels")
            .range(-50f, 200f).step(5f).setValue(0f);
    public final BooleanSetting hideBossbar = new BooleanSetting("Hide Bossbar", "Hide all bossbars completely")
            .setValue(false);

    private BossbarTweakerHud() {
        super("bossbar_tweaker", "Bossbar Tweaker", ModuleCategory.HUD);
        setup(scale, offsetY, hideBossbar);
    }

    public static BossbarTweakerHud getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Customize size and vertical position of vanilla boss health bars";
    }

    @Override
    public String getIcon() {
        return "scoreboard_hud.png";
    }
}
