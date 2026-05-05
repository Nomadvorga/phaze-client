package vorga.phazeclient.api.system.threeskins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import vorga.phazeclient.implement.features.modules.other.ThreeDSkins;

public class ThreeDSkinFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    public ThreeDSkinFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (!ThreeDSkins.getInstance().isEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // Check distance LOD
        float maxDist = ThreeDSkins.getInstance().renderDistanceLOD.getValue();
        double dx = state.x - client.player.getX();
        double dy = state.y - client.player.getY();
        double dz = state.z - client.player.getZ();
        if (dx * dx + dy * dy + dz * dz > maxDist * maxDist) return;

        // Get player entity from world using displayName or position matching
        AbstractClientPlayerEntity player = null;
        if (client.world != null) {
            // Try to find player by position (closest match)
            for (var p : client.world.getPlayers()) {
                if (p instanceof AbstractClientPlayerEntity acp) {
                    double pdx = acp.getX() - state.x;
                    double pdy = acp.getY() - state.y;
                    double pdz = acp.getZ() - state.z;
                    if (pdx * pdx + pdy * pdy + pdz * pdz < 0.1) {
                        player = acp;
                        break;
                    }
                }
            }
        }

        if (player == null) return;

        Identifier skinTexture = player.getSkinTextures().texture();
        if (skinTexture == null) return;

        PlayerSkinDataAccessor skinData = (PlayerSkinDataAccessor) player;

        if (!skinData.phaze$isSkinInitialized() || !skinTexture.equals(skinData.phaze$getCurrentSkin())) {
            buildMeshes(player, skinData, skinTexture);
        }

        // Skip if meshes failed to build
        if (!skinData.phaze$isSkinInitialized()) return;

        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(skinTexture));

        ThreeDSkins config = ThreeDSkins.getInstance();
        int overlay = OverlayTexture.DEFAULT_UV;

        PlayerEntityModel model = this.getContextModel();

        // Hide vanilla layers when rendering 3D versions
        if (config.enableHat.isValue() && skinData.phaze$getHeadMesh() != null) {
            model.hat.visible = false;
            renderMesh(matrices, vertexConsumer, light, overlay, skinData.phaze$getHeadMesh(), model.hat);
        }
        if (config.enableJacket.isValue() && skinData.phaze$getTorsoMesh() != null) {
            model.jacket.visible = false;
            renderMesh(matrices, vertexConsumer, light, overlay, skinData.phaze$getTorsoMesh(), model.jacket);
        }
        if (config.enableLeftSleeve.isValue() && skinData.phaze$getLeftArmMesh() != null) {
            model.leftSleeve.visible = false;
            renderMesh(matrices, vertexConsumer, light, overlay, skinData.phaze$getLeftArmMesh(), model.leftSleeve);
        }
        if (config.enableRightSleeve.isValue() && skinData.phaze$getRightArmMesh() != null) {
            model.rightSleeve.visible = false;
            renderMesh(matrices, vertexConsumer, light, overlay, skinData.phaze$getRightArmMesh(), model.rightSleeve);
        }
        if (config.enableLeftPants.isValue() && skinData.phaze$getLeftLegMesh() != null) {
            model.leftPants.visible = false;
            renderMesh(matrices, vertexConsumer, light, overlay, skinData.phaze$getLeftLegMesh(), model.leftPants);
        }
        if (config.enableRightPants.isValue() && skinData.phaze$getRightLegMesh() != null) {
            model.rightPants.visible = false;
            renderMesh(matrices, vertexConsumer, light, overlay, skinData.phaze$getRightLegMesh(), model.rightPants);
        }
    }

    private void renderMesh(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay,
                            SkinMeshData mesh, ModelPart modelPart) {
        matrices.push();
        modelPart.rotate(matrices);
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        Matrix3f normalMatrix = matrices.peek().getNormalMatrix();
        mesh.render(positionMatrix, normalMatrix, vertexConsumer, light, overlay);
        matrices.pop();
    }

    private void buildMeshes(AbstractClientPlayerEntity player, PlayerSkinDataAccessor skinData, Identifier skinTexture) {
        try {
            NativeImage skin = SkinTextureReader.readSkin(skinTexture);
            if (skin == null || skin.getWidth() != 64 || skin.getHeight() != 64) {
                skinData.phaze$setCurrentSkin(skinTexture);
                skinData.phaze$clearMeshes();
                return;
            }

            ThreeDSkins config = ThreeDSkins.getInstance();
            float baseSize = config.baseVoxelSize.getValue();
            float headSize = config.headVoxelSize.getValue();
            float bodyWidth = config.bodyVoxelWidthSize.getValue();

            boolean slim = player.getSkinTextures().model() == net.minecraft.client.util.SkinTextures.Model.SLIM;

            skinData.phaze$setHeadMesh(SkinMeshBuilder.buildMesh(skin, 8, 8, 8, 32, 0, false, headSize, false));
            skinData.phaze$setTorsoMesh(SkinMeshBuilder.buildMesh(skin, 8, 12, 4, 16, 32, true, bodyWidth, false));

            if (slim) {
                skinData.phaze$setLeftArmMesh(SkinMeshBuilder.buildMesh(skin, 3, 12, 4, 48, 48, true, baseSize, true));
                skinData.phaze$setRightArmMesh(SkinMeshBuilder.buildMesh(skin, 3, 12, 4, 40, 32, true, baseSize, false));
            } else {
                skinData.phaze$setLeftArmMesh(SkinMeshBuilder.buildMesh(skin, 4, 12, 4, 48, 48, true, baseSize, true));
                skinData.phaze$setRightArmMesh(SkinMeshBuilder.buildMesh(skin, 4, 12, 4, 40, 32, true, baseSize, false));
            }

            skinData.phaze$setLeftLegMesh(SkinMeshBuilder.buildMesh(skin, 4, 12, 4, 0, 48, true, baseSize, true));
            skinData.phaze$setRightLegMesh(SkinMeshBuilder.buildMesh(skin, 4, 12, 4, 0, 32, true, baseSize, false));

            skinData.phaze$setCurrentSkin(skinTexture);
            skinData.phaze$setThinArms(slim);
            skinData.phaze$setSkinInitialized(true);

            // Don't close the skin image - it's managed by the texture system
        } catch (Exception e) {
            skinData.phaze$setCurrentSkin(skinTexture);
            skinData.phaze$clearMeshes();
        }
    }
}
