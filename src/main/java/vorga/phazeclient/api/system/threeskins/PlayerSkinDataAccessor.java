package vorga.phazeclient.api.system.threeskins;

import net.minecraft.util.Identifier;

public interface PlayerSkinDataAccessor {
    SkinMeshData phaze$getHeadMesh();
    SkinMeshData phaze$getTorsoMesh();
    SkinMeshData phaze$getLeftArmMesh();
    SkinMeshData phaze$getRightArmMesh();
    SkinMeshData phaze$getLeftLegMesh();
    SkinMeshData phaze$getRightLegMesh();

    void phaze$setHeadMesh(SkinMeshData mesh);
    void phaze$setTorsoMesh(SkinMeshData mesh);
    void phaze$setLeftArmMesh(SkinMeshData mesh);
    void phaze$setRightArmMesh(SkinMeshData mesh);
    void phaze$setLeftLegMesh(SkinMeshData mesh);
    void phaze$setRightLegMesh(SkinMeshData mesh);

    Identifier phaze$getCurrentSkin();
    void phaze$setCurrentSkin(Identifier skin);

    boolean phaze$isThinArms();
    void phaze$setThinArms(boolean thin);

    boolean phaze$isSkinInitialized();
    void phaze$setSkinInitialized(boolean initialized);

    void phaze$clearMeshes();
}
