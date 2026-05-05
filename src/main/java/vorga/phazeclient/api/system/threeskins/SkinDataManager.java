package vorga.phazeclient.api.system.threeskins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SkinDataManager {
    private static final SkinDataManager INSTANCE = new SkinDataManager();

    private final Map<UUID, PlayerSkinData> playerData = new HashMap<>();

    public static SkinDataManager getInstance() {
        return INSTANCE;
    }

    public PlayerSkinData getOrCreate(AbstractClientPlayerEntity player) {
        return playerData.computeIfAbsent(player.getUuid(), id -> new PlayerSkinData());
    }

    public void clearAll() {
        playerData.clear();
    }

    public static class PlayerSkinData {
        public SkinMeshData headMesh;
        public SkinMeshData torsoMesh;
        public SkinMeshData leftArmMesh;
        public SkinMeshData rightArmMesh;
        public SkinMeshData leftLegMesh;
        public SkinMeshData rightLegMesh;
        public Identifier currentSkin;
        public boolean thinArms;
        public boolean initialized;

        public void clear() {
            headMesh = null;
            torsoMesh = null;
            leftArmMesh = null;
            rightArmMesh = null;
            leftLegMesh = null;
            rightLegMesh = null;
            currentSkin = null;
            initialized = false;
        }
    }
}
