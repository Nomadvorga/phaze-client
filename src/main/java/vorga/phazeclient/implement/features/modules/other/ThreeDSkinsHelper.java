package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import vorga.phazeclient.mixins.ModelPartAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class ThreeDSkinsHelper {
    private record VanillaModelCuboids(
            List<ModelPart.Cuboid> hat,
            List<ModelPart.Cuboid> jacket,
            List<ModelPart.Cuboid> leftSleeve,
            List<ModelPart.Cuboid> rightSleeve,
            List<ModelPart.Cuboid> leftPants,
            List<ModelPart.Cuboid> rightPants
    ) {}

    private static final Map<PlayerEntityModel, VanillaModelCuboids> ORIGINAL_PARTS = new WeakHashMap<>();

    private ThreeDSkinsHelper() {}

    public static void apply(PlayerEntityModel model, PlayerEntityRenderState state) {
        if (model == null || state == null) return;

        // Capture vanilla cuboids if not yet cached for this model instance
        VanillaModelCuboids vanilla = ORIGINAL_PARTS.computeIfAbsent(model, m -> new VanillaModelCuboids(
                new ArrayList<>(((ModelPartAccessor) (Object) m.hat).phaze$getCuboids()),
                new ArrayList<>(((ModelPartAccessor) (Object) m.jacket).phaze$getCuboids()),
                new ArrayList<>(((ModelPartAccessor) (Object) m.leftSleeve).phaze$getCuboids()),
                new ArrayList<>(((ModelPartAccessor) (Object) m.rightSleeve).phaze$getCuboids()),
                new ArrayList<>(((ModelPartAccessor) (Object) m.leftPants).phaze$getCuboids()),
                new ArrayList<>(((ModelPartAccessor) (Object) m.rightPants).phaze$getCuboids())
        ));

        ThreeDSkins module = ThreeDSkins.getInstance();
        if (module == null || !module.isEnabled()) {
            restore(model);
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        boolean isLocal = player != null && state.id == player.getId();
        if (!isLocal && !module.showOthers.isValue()) {
            restore(model);
            return;
        }

        // Distance LOD check
        if (player != null && !isLocal) {
            double dx = player.getX() - state.x;
            double dy = player.getY() - state.y;
            double dz = player.getZ() - state.z;
            double maxDist = module.maxDistance.getValue();
            if (dx * dx + dy * dy + dz * dz > maxDist * maxDist) {
                restore(model);
                return;
            }
        }

        Identifier skinId = state.skinTextures != null ? state.skinTextures.texture() : null;
        if (skinId == null) {
            restore(model);
            return;
        }

        ThreeDSkinsVoxelizer.BakedSkinLayers baked = ThreeDSkinsVoxelizer.get(skinId);
        if (baked == null || !baked.hasAny()) {
            restore(model);
            return;
        }

        boolean isSlim = state.skinTextures != null && state.skinTextures.model() == SkinTextures.Model.SLIM;

        ModelPartAccessor hatAcc = (ModelPartAccessor) (Object) model.hat;
        ModelPartAccessor jacketAcc = (ModelPartAccessor) (Object) model.jacket;
        ModelPartAccessor leftSleeveAcc = (ModelPartAccessor) (Object) model.leftSleeve;
        ModelPartAccessor rightSleeveAcc = (ModelPartAccessor) (Object) model.rightSleeve;
        ModelPartAccessor leftPantsAcc = (ModelPartAccessor) (Object) model.leftPants;
        ModelPartAccessor rightPantsAcc = (ModelPartAccessor) (Object) model.rightPants;

        if (module.hat3D.isValue() && !baked.hat.isEmpty()) {
            hatAcc.phaze$setCuboids(baked.hat);
        } else {
            hatAcc.phaze$setCuboids(vanilla.hat());
        }

        if (module.jacket3D.isValue() && !baked.jacket.isEmpty()) {
            jacketAcc.phaze$setCuboids(baked.jacket);
        } else {
            jacketAcc.phaze$setCuboids(vanilla.jacket());
        }

        List<ModelPart.Cuboid> leftSleeveVoxels = isSlim ? baked.leftSleeveSlim : baked.leftSleeveWide;
        if (module.sleeves3D.isValue() && leftSleeveVoxels != null && !leftSleeveVoxels.isEmpty()) {
            leftSleeveAcc.phaze$setCuboids(leftSleeveVoxels);
        } else {
            leftSleeveAcc.phaze$setCuboids(vanilla.leftSleeve());
        }

        List<ModelPart.Cuboid> rightSleeveVoxels = isSlim ? baked.rightSleeveSlim : baked.rightSleeveWide;
        if (module.sleeves3D.isValue() && rightSleeveVoxels != null && !rightSleeveVoxels.isEmpty()) {
            rightSleeveAcc.phaze$setCuboids(rightSleeveVoxels);
        } else {
            rightSleeveAcc.phaze$setCuboids(vanilla.rightSleeve());
        }

        if (module.pants3D.isValue() && !baked.leftPants.isEmpty()) {
            leftPantsAcc.phaze$setCuboids(baked.leftPants);
        } else {
            leftPantsAcc.phaze$setCuboids(vanilla.leftPants());
        }

        if (module.pants3D.isValue() && !baked.rightPants.isEmpty()) {
            rightPantsAcc.phaze$setCuboids(baked.rightPants);
        } else {
            rightPantsAcc.phaze$setCuboids(vanilla.rightPants());
        }
    }

    public static void restore(PlayerEntityModel model) {
        if (model == null) return;
        VanillaModelCuboids vanilla = ORIGINAL_PARTS.get(model);
        if (vanilla == null) return;

        ((ModelPartAccessor) (Object) model.hat).phaze$setCuboids(vanilla.hat());
        ((ModelPartAccessor) (Object) model.jacket).phaze$setCuboids(vanilla.jacket());
        ((ModelPartAccessor) (Object) model.leftSleeve).phaze$setCuboids(vanilla.leftSleeve());
        ((ModelPartAccessor) (Object) model.rightSleeve).phaze$setCuboids(vanilla.rightSleeve());
        ((ModelPartAccessor) (Object) model.leftPants).phaze$setCuboids(vanilla.leftPants());
        ((ModelPartAccessor) (Object) model.rightPants).phaze$setCuboids(vanilla.rightPants());
    }
}
