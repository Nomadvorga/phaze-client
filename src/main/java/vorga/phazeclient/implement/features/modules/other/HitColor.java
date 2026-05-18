package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.implement.hitcolor.OverlayReloadListener;

public final class HitColor extends vorga.phazeclient.api.feature.module.Module {
    private static final HitColor INSTANCE = new HitColor();

    public static HitColor getInstance() {
        return INSTANCE;
    }

    public final SectionSetting otherSection = new SectionSetting("Other");
    public final BooleanSetting showDamageInArmor = new BooleanSetting("Show Damage In Armor", "Show hit color on armor").setValue(true);
    public final BooleanSetting customHitcolor = new BooleanSetting("Custom Hit Color", "Enable custom hit color")
            .setValue(true)
            .onChange(value -> OverlayReloadListener.event());
    public final ColorSetting hitcolor = new ColorSetting("Hit Color", "Color when entity is hit")
            .value(0xFFFF0000)
            .onChange(value -> OverlayReloadListener.event());

    private HitColor() {
        super("hitcolor", "HitColor", ModuleCategory.OTHER);
        showDamageInArmor.setFullWidth(true);
        customHitcolor.setFullWidth(true);
        hitcolor.setFullWidth(true);
        setup(otherSection, showDamageInArmor, customHitcolor, hitcolor);
    }

    public int getHitColor() {
        return hitcolor.getColor();
    }

    @Override
    public void activate() {
        OverlayReloadListener.event();
    }

    @Override
    public void deactivate() {
        OverlayReloadListener.event();
    }

    @Override
    public String getDescription() {
        return "Custom hit color for entities";
    }

    @Override
    public String getIcon() {
        return "hitcolor.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    @Override
    public boolean isCanBind() {
        return false;
    }
}
