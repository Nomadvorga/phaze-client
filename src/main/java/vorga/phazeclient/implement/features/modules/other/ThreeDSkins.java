package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class ThreeDSkins extends Module {
    private static final ThreeDSkins INSTANCE = new ThreeDSkins();

    public final SectionSetting playerSection = new SectionSetting("Player Layers");
    public final BooleanSetting enableHat = new BooleanSetting("Hat", "3D hat layer").setValue(true);
    public final BooleanSetting enableJacket = new BooleanSetting("Jacket", "3D jacket layer").setValue(true);
    public final BooleanSetting enableLeftSleeve = new BooleanSetting("Left Sleeve", "3D left sleeve").setValue(true);
    public final BooleanSetting enableRightSleeve = new BooleanSetting("Right Sleeve", "3D right sleeve").setValue(true);
    public final BooleanSetting enableLeftPants = new BooleanSetting("Left Pants", "3D left pants").setValue(true);
    public final BooleanSetting enableRightPants = new BooleanSetting("Right Pants", "3D right pants").setValue(true);

    public final SectionSetting skullSection = new SectionSetting("Skulls");
    public final BooleanSetting enableSkulls = new BooleanSetting("Skulls", "3D player skulls").setValue(true);
    public final BooleanSetting enableSkullsItems = new BooleanSetting("Skull Items", "3D skull items in inventory").setValue(true);

    public final SectionSetting advancedSection = new SectionSetting("Advanced");
    public final ValueSetting renderDistanceLOD = new ValueSetting("Render Distance", "Max distance for 3D layers").range(5, 40).setValue(20);
    public final ValueSetting baseVoxelSize = new ValueSetting("Base Voxel Size", "Voxel size for body layers").range(1.0f, 1.4f).setValue(1.1f);
    public final ValueSetting headVoxelSize = new ValueSetting("Head Voxel Size", "Voxel size for head layer").range(1.0f, 1.25f).setValue(1.1f);
    public final ValueSetting bodyVoxelWidthSize = new ValueSetting("Body Voxel Width", "Voxel width for torso").range(1.0f, 1.4f).setValue(1.1f);

    private ThreeDSkins() {
        super("3d_skins", "3D Skins", ModuleCategory.OTHER, true, false);

        enableHat.setFullWidth(true);
        enableJacket.setFullWidth(true);
        enableLeftSleeve.setFullWidth(true);
        enableRightSleeve.setFullWidth(true);
        enableLeftPants.setFullWidth(true);
        enableRightPants.setFullWidth(true);
        enableSkulls.setFullWidth(true);
        enableSkullsItems.setFullWidth(true);
        renderDistanceLOD.setFullWidth(true);
        baseVoxelSize.setFullWidth(true);
        headVoxelSize.setFullWidth(true);
        bodyVoxelWidthSize.setFullWidth(true);

        setup(playerSection, enableHat, enableJacket, enableLeftSleeve, enableRightSleeve, enableLeftPants, enableRightPants,
                skullSection, enableSkulls, enableSkullsItems,
                advancedSection, renderDistanceLOD, baseVoxelSize, headVoxelSize, bodyVoxelWidthSize);
    }

    public static ThreeDSkins getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Renders 3D layers on player skins";
    }
}
