package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.IdentityHashMap;
import net.minecraft.client.model.ModelPart;

/**
 * 3D Skins renderer that operates in pure additive/subtractive mode:
 * 1. preRender: Hides the vanilla 2D second layers on the model so vanilla does not draw them.
 * 2. postRender: Renders clean extruded 3D voxels attached directly to the model's limb transforms.
 *
 * This completely avoids mutating ModelPart.cuboids lists, guaranteeing 100% compatibility
 * with Sodium, Iris, ImmediatelyFast, and Entity Culling.
 */
public final class ThreeDSkinsHelper {

    private static boolean activeForCurrentEntity = false;
    private static ThreeDSkinsVoxelizer.BakedSkinLayers currentBaked = null;
    private static boolean currentIsSlim = false;
    private static final IdentityHashMap<ModelPart, Boolean> originalVisibility = new IdentityHashMap<>();

    private ThreeDSkinsHelper() {}

    public static void preRender(PlayerEntityModel model, PlayerEntityRenderState state) {
        activeForCurrentEntity = false;
        currentBaked = null;
        originalVisibility.clear();

        if (model == null || state == null) return;

        ThreeDSkins module = ThreeDSkins.getInstance();
        if (module == null || !module.isEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        boolean isLocal = player != null && state.id == player.getId();
        if (!isLocal && !module.showOthers.isValue()) return;

        // Distance LOD check
        if (player != null && !isLocal) {
            double dx = player.getX() - state.x;
            double dy = player.getY() - state.y;
            double dz = player.getZ() - state.z;
            double maxDist = module.maxDistance.getValue();
            if (dx * dx + dy * dy + dz * dz > maxDist * maxDist) return;
        }

        Identifier skinId = state.skinTextures != null ? state.skinTextures.texture() : null;
        if (skinId == null) return;

        ThreeDSkinsVoxelizer.BakedSkinLayers baked = ThreeDSkinsVoxelizer.get(skinId);
        if (baked == null || !baked.hasAny()) return;

        activeForCurrentEntity = true;
        currentBaked = baked;
        currentIsSlim = state.skinTextures != null && state.skinTextures.model() == SkinTextures.Model.SLIM;

        // Hide vanilla flat 2D layers that are replaced by 3D voxels
        if (module.hat3D.isValue() && !baked.hat.isEmpty()) {
            hide(model.hat);
        }
        if (module.jacket3D.isValue() && !baked.jacket.isEmpty()) {
            hide(model.jacket);
        }
        if (module.sleeves3D.isValue()) {
            List<ThreeDSkinsVoxelizer.VoxelQuad> leftSleeve = currentIsSlim ? baked.leftSleeveSlim : baked.leftSleeveWide;
            List<ThreeDSkinsVoxelizer.VoxelQuad> rightSleeve = currentIsSlim ? baked.rightSleeveSlim : baked.rightSleeveWide;
            if (leftSleeve != null && !leftSleeve.isEmpty()) {
                hide(model.leftSleeve);
            }
            if (rightSleeve != null && !rightSleeve.isEmpty()) {
                hide(model.rightSleeve);
            }
        }
        if (module.pants3D.isValue()) {
            if (!baked.leftPants.isEmpty()) {
                hide(model.leftPants);
            }
            if (!baked.rightPants.isEmpty()) {
                hide(model.rightPants);
            }
        }
    }

    public static void postRender(
            PlayerEntityModel model,
            PlayerEntityRenderState state,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int overlay
    ) {
        if (!activeForCurrentEntity || currentBaked == null || model == null || state == null) {
            restoreVisibility();
            activeForCurrentEntity = false;
            currentBaked = null;
            return;
        }

        ThreeDSkins module = ThreeDSkins.getInstance();
        if (module == null || !module.isEnabled()) {
            restoreVisibility();
            activeForCurrentEntity = false;
            currentBaked = null;
            return;
        }

        Identifier skinId = state.skinTextures != null ? state.skinTextures.texture() : null;
        if (skinId == null) {
            restoreVisibility();
            activeForCurrentEntity = false;
            currentBaked = null;
            return;
        }

        // Skin pixels are binary alpha, not blended glass. Cutout keeps the
        // pass in the opaque/cutout pipeline and avoids the expensive sorted
        // translucent queue for every player in a crowd.
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(skinId));

        // 1. Hat (attached to head)
        if (module.hat3D.isValue() && !currentBaked.hat.isEmpty()) {
            matrices.push();
            transformPart(matrices, model.head);
            matrices.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
            renderQuads(consumer, matrices, currentBaked.hat, light, overlay);
            matrices.pop();
        }

        // 2. Jacket (attached to body)
        if (module.jacket3D.isValue() && !currentBaked.jacket.isEmpty()) {
            matrices.push();
            transformPart(matrices, model.body);
            matrices.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
            renderQuads(consumer, matrices, currentBaked.jacket, light, overlay);
            matrices.pop();
        }

        // 3. Sleeves (attached to rightArm and leftArm)
        if (module.sleeves3D.isValue()) {
            List<ThreeDSkinsVoxelizer.VoxelQuad> rightSleeve = currentIsSlim ? currentBaked.rightSleeveSlim : currentBaked.rightSleeveWide;
            if (rightSleeve != null && !rightSleeve.isEmpty()) {
                matrices.push();
                model.rightArm.rotate(matrices);
                matrices.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
                renderQuads(consumer, matrices, rightSleeve, light, overlay);
                matrices.pop();
            }

            List<ThreeDSkinsVoxelizer.VoxelQuad> leftSleeve = currentIsSlim ? currentBaked.leftSleeveSlim : currentBaked.leftSleeveWide;
            if (leftSleeve != null && !leftSleeve.isEmpty()) {
                matrices.push();
                model.leftArm.rotate(matrices);
                matrices.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
                renderQuads(consumer, matrices, leftSleeve, light, overlay);
                matrices.pop();
            }
        }

        // 4. Pants (attached to rightLeg and leftLeg)
        if (module.pants3D.isValue()) {
            if (!currentBaked.rightPants.isEmpty()) {
                matrices.push();
                model.rightLeg.rotate(matrices);
                matrices.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
                renderQuads(consumer, matrices, currentBaked.rightPants, light, overlay);
                matrices.pop();
            }

            if (!currentBaked.leftPants.isEmpty()) {
                matrices.push();
                model.leftLeg.rotate(matrices);
                matrices.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
                renderQuads(consumer, matrices, currentBaked.leftPants, light, overlay);
                matrices.pop();
            }
        }

        restoreVisibility();
        activeForCurrentEntity = false;
        currentBaked = null;
    }

    private static void hide(ModelPart part) {
        originalVisibility.putIfAbsent(part, part.visible);
        part.visible = false;
    }

    private static void restoreVisibility() {
        originalVisibility.forEach((part, visible) -> part.visible = visible);
        originalVisibility.clear();
    }

    /** ModelPart#rotate applies only orientation; vanilla render also applies
     * the part pivot. Missing that translation was the source of the visible
     * gap between the baked layer and the player model. */
    private static void transformPart(MatrixStack matrices, ModelPart part) {
        // MatrixStack is already in world/block units here. The voxel
        // coordinates are converted to blocks by the scale that follows, so
        // the model pivot must be converted independently. Using raw model
        // units made arms and legs jump several blocks away.
        matrices.translate(part.pivotX / 16.0f, part.pivotY / 16.0f, part.pivotZ / 16.0f);
        part.rotate(matrices);
    }

    private static void renderQuads(
            VertexConsumer consumer,
            MatrixStack matrices,
            List<ThreeDSkinsVoxelizer.VoxelQuad> quads,
            int light,
            int overlay
    ) {
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f posMatrix = entry.getPositionMatrix();

        for (ThreeDSkinsVoxelizer.VoxelQuad quad : quads) {
            Vector3f normal = quad.normal();
            for (ThreeDSkinsVoxelizer.VoxelVertex v : quad.vertices()) {
                consumer.vertex(posMatrix, v.x(), v.y(), v.z())
                        .color(0xFFFFFFFF)
                        .texture(v.u(), v.v())
                        .overlay(overlay)
                        .light(light)
                        .normal(entry, normal.x(), normal.y(), normal.z());
            }
        }
    }
}
