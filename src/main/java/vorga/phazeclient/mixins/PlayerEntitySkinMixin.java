package vorga.phazeclient.mixins;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import vorga.phazeclient.api.system.threeskins.SkinMeshData;
import vorga.phazeclient.api.system.threeskins.PlayerSkinDataAccessor;

@Mixin(AbstractClientPlayerEntity.class)
public class PlayerEntitySkinMixin implements PlayerSkinDataAccessor {
    @Unique
    private SkinMeshData phaze$headMesh;
    @Unique
    private SkinMeshData phaze$torsoMesh;
    @Unique
    private SkinMeshData phaze$leftArmMesh;
    @Unique
    private SkinMeshData phaze$rightArmMesh;
    @Unique
    private SkinMeshData phaze$leftLegMesh;
    @Unique
    private SkinMeshData phaze$rightLegMesh;
    @Unique
    private Identifier phaze$currentSkin;
    @Unique
    private boolean phaze$thinArms;
    @Unique
    private boolean phaze$skinInitialized;

    @Override
    public SkinMeshData phaze$getHeadMesh() { return phaze$headMesh; }
    @Override
    public SkinMeshData phaze$getTorsoMesh() { return phaze$torsoMesh; }
    @Override
    public SkinMeshData phaze$getLeftArmMesh() { return phaze$leftArmMesh; }
    @Override
    public SkinMeshData phaze$getRightArmMesh() { return phaze$rightArmMesh; }
    @Override
    public SkinMeshData phaze$getLeftLegMesh() { return phaze$leftLegMesh; }
    @Override
    public SkinMeshData phaze$getRightLegMesh() { return phaze$rightLegMesh; }

    @Override
    public void phaze$setHeadMesh(SkinMeshData mesh) { phaze$headMesh = mesh; }
    @Override
    public void phaze$setTorsoMesh(SkinMeshData mesh) { phaze$torsoMesh = mesh; }
    @Override
    public void phaze$setLeftArmMesh(SkinMeshData mesh) { phaze$leftArmMesh = mesh; }
    @Override
    public void phaze$setRightArmMesh(SkinMeshData mesh) { phaze$rightArmMesh = mesh; }
    @Override
    public void phaze$setLeftLegMesh(SkinMeshData mesh) { phaze$leftLegMesh = mesh; }
    @Override
    public void phaze$setRightLegMesh(SkinMeshData mesh) { phaze$rightLegMesh = mesh; }

    @Override
    public Identifier phaze$getCurrentSkin() { return phaze$currentSkin; }
    @Override
    public void phaze$setCurrentSkin(Identifier skin) { phaze$currentSkin = skin; }

    @Override
    public boolean phaze$isThinArms() { return phaze$thinArms; }
    @Override
    public void phaze$setThinArms(boolean thin) { phaze$thinArms = thin; }

    @Override
    public boolean phaze$isSkinInitialized() { return phaze$skinInitialized; }
    @Override
    public void phaze$setSkinInitialized(boolean initialized) { phaze$skinInitialized = initialized; }

    @Override
    public void phaze$clearMeshes() {
        phaze$headMesh = null;
        phaze$torsoMesh = null;
        phaze$leftArmMesh = null;
        phaze$rightArmMesh = null;
        phaze$leftLegMesh = null;
        phaze$rightLegMesh = null;
        phaze$currentSkin = null;
        phaze$skinInitialized = false;
    }
}
