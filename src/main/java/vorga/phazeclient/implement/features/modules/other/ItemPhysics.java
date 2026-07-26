package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class ItemPhysics extends Module {
    private static final ItemPhysics INSTANCE = new ItemPhysics();

    public final SectionSetting mainSection = new SectionSetting("General");
    public final ValueSetting rotationSpeed = new ValueSetting("Rotation Speed", "Rotation speed in air").range(0.1f, 5.0f).setValue(1.0f);

    private ItemPhysics() {
        super("item_physics", "Item Physics", ModuleCategory.OTHER, true, false);
        
        rotationSpeed.setFullWidth(true);
        
        setup(mainSection, rotationSpeed);
    }

    public static ItemPhysics getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Realistic item physics";
    }

    @Override
    public String getIcon() {
        return "item_physics.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public float getRotationSpeed() {
        return rotationSpeed.getValue();
    }
}
