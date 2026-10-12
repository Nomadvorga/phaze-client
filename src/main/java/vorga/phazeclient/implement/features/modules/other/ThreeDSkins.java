package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public class ThreeDSkins extends Module {
    private static final ThreeDSkins INSTANCE = new ThreeDSkins();

    public final ValueSetting voxelThickness = new ValueSetting("Voxel Depth", "Depth of extruded outer skin voxels")
            .range(0.2f, 1.2f).step(0.05f).setValue(0.65f)
            .onChange(v -> ThreeDSkinsVoxelizer.clearCache());
    public final ValueSetting maxDistance = new ValueSetting("Max Distance", "Maximum distance to render 3D voxels (blocks)")
            .range(12, 64).step(4).setValue(28);
    public final BooleanSetting showOthers = new BooleanSetting("Show Others", "Apply 3D skin layer to other players")
            .setValue(true);
    public final BooleanSetting hat3D = new BooleanSetting("3D Hat", "Extrude hat and hair layer").setValue(true);
    public final BooleanSetting jacket3D = new BooleanSetting("3D Jacket", "Extrude jacket and torso layer").setValue(true);
    public final BooleanSetting sleeves3D = new BooleanSetting("3D Sleeves", "Extrude sleeve layers").setValue(true);
    public final BooleanSetting pants3D = new BooleanSetting("3D Pants", "Extrude pants layers").setValue(true);

    private ThreeDSkins() {
        super("three_d_skins", "3D Skins", ModuleCategory.OTHER);
        setup(voxelThickness, maxDistance, showOthers, hat3D, jacket3D, sleeves3D, pants3D);
    }

    public static ThreeDSkins getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Clean-room 3D skin layer voxelization with optimized neighbor face culling";
    }

    @Override
    public String getIcon() {
        return "player_model_hud.png";
    }
}
