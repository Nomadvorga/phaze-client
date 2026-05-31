/**
 * Hitbox Customizer Module
 * Based on HitboxPlus by PingIsFun (https://github.com/PingIsFun/hitboxplus)
 * Licensed under MIT License
 * 
 * Original Copyright (c) 2022 PingIsFun
 * Modified for Phaze Client
 */
package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.*;

public final class HitboxCustomizer extends Module {
    private static final HitboxCustomizer INSTANCE = new HitboxCustomizer();

    public final SectionSetting generalSection = new SectionSetting("General");
    public final ColorSetting hitboxColor = new ColorSetting("Hitbox Color", "Color of all entity hitboxes")
            .setColor(0xFFFFFFFF);
    public final ColorSetting reachColor = new ColorSetting("Reach Color", "Color used when looking at an entity in attack range")
            .setColor(0xFFFF3030);
    public final ValueSetting outlineThickness = new ValueSetting("Outline Thickness", "Thickness of the hitbox outline")
            .range(1.0f, 6.0f)
            .step(0.5f)
            .setValue(1.0f);
    public final BooleanSetting showLookLine = new BooleanSetting("Show Eye Line", "Draws a line in the entity look direction")
            .setValue(true);
    public final BooleanSetting redInReach = new BooleanSetting("Change Color When Looking on Entity", "Switches to reach color when crosshair targets an entity within attack range")
            .setValue(false);

    private HitboxCustomizer() {
        super("hitboxcustomizer", "Hitbox Customizer", ModuleCategory.OTHER);

        hitboxColor.setFullWidth(true);
        reachColor.setFullWidth(true);
        outlineThickness.setFullWidth(true);
        showLookLine.setFullWidth(true);
        redInReach.setFullWidth(true);
        reachColor.setVisible(redInReach::isValue);

        setup(generalSection, hitboxColor, showLookLine, redInReach, outlineThickness, reachColor);
    }

    public static HitboxCustomizer getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Customize entity hitbox color";
    }

    @Override
    public String getIcon() {
        return "hitboxcustomizer.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public int getHitboxColor() {
        return hitboxColor.getColor();
    }
}
