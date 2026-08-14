package vorga.phazeclient.implement.cosmetics.bridge.pulserender;

import vorga.phazeclient.implement.cosmetics.bridge.CosmeticEntry;
import vorga.phazeclient.implement.cosmetics.bridge.pulserender.PulseSourceModel.AnimationData;
import vorga.phazeclient.implement.cosmetics.bridge.pulserender.PulseSourceModel.BoneAnimationData;
import vorga.phazeclient.implement.cosmetics.bridge.pulserender.geo.*;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Bedrock cosmetic renderer ported from the renderer supplied by the user.
 * The important part is the exact Pulse axis/pivot conversion performed by
 * GeoModelParser + GeckoRenderHelper instead of category-specific root hacks.
 */
public final class PulseGeoRenderer {

    public static boolean render(CosmeticEntry entry,
                                 MatrixStack matrices,
                                 VertexConsumerProvider providers,
                                 int light,
                                 boolean gliding,
                                 boolean swimming,
                                 boolean sneaking,
                                 boolean moving,
                                 float timeSeconds) {
        PulseSourceModel source = PulseSourceModelLoader.get(entry);
        if (source == null || entry.textureId() == null) return false;

        source.resetBones();
        String animationName = source.chooseAnimation(gliding, swimming, sneaking, moving);
        if (animationName != null) {
            AnimationData animation = source.animations.get(animationName);
            if (animation != null) applyAnimation(source, animation, timeSeconds);
        }

        matrices.push();

        // Exact outer transform order from the supplied Pulse CosmeticRenderer.
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));
        matrices.translate(source.x, source.y, source.z);
        if (source.yaw != 0f) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(source.yaw));
        if (source.pitch != 0f) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(source.pitch));
        if (source.roll != 0f) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(source.roll));
        matrices.scale(source.scale, source.scale, source.scale);

        VertexConsumer consumer = providers.getBuffer(RenderLayers.entityCutoutNoCull(entry.textureId()));
        try {
            for (GeoBone bone : source.model.topLevelBones) {
                renderBone(bone, matrices, consumer, light, OverlayTexture.DEFAULT_UV);
            }
        } finally {
            matrices.pop();
        }
        return true;
    }

    /**
     * Pet variant of the supplied Pulse renderer.
     * PetWorldRenderer already applies the source x/y/z as a world attachment,
     * so this method intentionally skips the source translation, but keeps the
     * exact Pulse bone/cube hierarchy and axis conversion. The requested pet
     * correction is then applied around the source root: +180 degrees on Y and
     * a VERTICAL (Y-only) mirror. X is never mirrored.
     */
    public static boolean renderPet(CosmeticEntry entry,
                                    MatrixStack matrices,
                                    VertexConsumerProvider providers,
                                    int light,
                                    boolean gliding,
                                    boolean swimming,
                                    boolean sneaking,
                                    boolean moving,
                                    float timeSeconds) {
        PulseSourceModel source = PulseSourceModelLoader.get(entry);
        if (source == null || entry.textureId() == null) return false;

        source.resetBones();
        String animationName = source.chooseAnimation(gliding, swimming, sneaking, moving);
        if (animationName != null) {
            AnimationData animation = source.animations.get(animationName);
            if (animation != null) applyAnimation(source, animation, timeSeconds);
        }

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));

        // x/y/z are applied by PetWorldRenderer so the old world placement is
        // preserved (including Jaguar's exact -2 block correction).
        if (source.yaw != 0f) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(source.yaw));
        if (source.pitch != 0f) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(source.pitch));
        if (source.roll != 0f) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(source.roll));
        matrices.scale(source.scale, source.scale, source.scale);

        // Apply requested pet orientation around its authored root pivot so the
        // model rotates/mirrors in place rather than orbiting away from player.
        if (!source.model.topLevelBones.isEmpty()) {
            GeoBone root = source.model.topLevelBones.get(0);
            float px = root.getPivotX() / 16.0f;
            float py = root.getPivotY() / 16.0f;
            float pz = root.getPivotZ() / 16.0f;
            matrices.translate(px, py, pz);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));
            matrices.scale(1.0f, -1.0f, 1.0f); // vertical mirror only
            matrices.translate(-px, -py, -pz);
        } else {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));
            matrices.scale(1.0f, -1.0f, 1.0f);
        }

        VertexConsumer consumer = providers.getBuffer(RenderLayers.entityCutoutNoCull(entry.textureId()));
        try {
            for (GeoBone bone : source.model.topLevelBones) {
                renderBone(bone, matrices, consumer, light, OverlayTexture.DEFAULT_UV);
            }
        } finally {
            matrices.pop();
        }
        return true;
    }

    private static void applyAnimation(PulseSourceModel source, AnimationData animation, float timeSeconds) {
        float t = animation.loop ? timeSeconds % animation.length : Math.min(timeSeconds, animation.length);

        for (var entry : animation.bones.entrySet()) {
            GeoBone bone = source.findBone(entry.getKey());
            if (bone == null) continue;
            float[] base = source.initialBoneTransforms.get(entry.getKey());
            if (base == null) base = new float[]{0,0,0,0,0,0,1,1,1};

            BoneAnimationData data = entry.getValue();
            if (!data.rotation.isEmpty()) {
                float[] v = data.rotation.sample(t, new float[]{0,0,0});
                bone.setRotationX(base[0] + (float)Math.toRadians(-v[0]));
                bone.setRotationY(base[1] + (float)Math.toRadians(-v[1]));
                bone.setRotationZ(base[2] + (float)Math.toRadians(v[2]));
            }
            if (!data.position.isEmpty()) {
                float[] v = data.position.sample(t, new float[]{0,0,0});
                bone.setPositionX(base[3] + v[0]);
                bone.setPositionY(base[4] + v[1]);
                bone.setPositionZ(base[5] + v[2]);
            }
            if (!data.scale.isEmpty()) {
                float[] v = data.scale.sample(t, new float[]{1,1,1});
                bone.setScaleX(base[6] * v[0]);
                bone.setScaleY(base[7] * v[1]);
                bone.setScaleZ(base[8] * v[2]);
            }
        }
    }

    private static void renderBone(GeoBone bone,
                                   MatrixStack matrices,
                                   VertexConsumer consumer,
                                   int light,
                                   int overlay) {
        if (bone.isHidden) return;

        matrices.push();
        GeckoRenderHelper.translate(bone, matrices);
        GeckoRenderHelper.moveToPivot(bone, matrices);
        GeckoRenderHelper.rotate(bone, matrices);
        GeckoRenderHelper.scale(bone, matrices);
        GeckoRenderHelper.moveBackFromPivot(bone, matrices);

        for (GeoCube cube : bone.childCubes) {
            renderCube(cube, matrices, consumer, light, overlay);
        }
        for (GeoBone child : bone.childBones) {
            renderBone(child, matrices, consumer, light, overlay);
        }
        matrices.pop();
    }

    private static void renderCube(GeoCube cube,
                                   MatrixStack matrices,
                                   VertexConsumer consumer,
                                   int light,
                                   int overlay) {
        matrices.push();
        GeckoRenderHelper.moveToPivot(cube, matrices);
        GeckoRenderHelper.rotate(cube, matrices);
        GeckoRenderHelper.moveBackFromPivot(cube, matrices);

        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        Matrix3f normalMatrix = matrices.peek().getNormalMatrix();

        for (GeoQuad quad : cube.quads) {
            if (quad == null) continue;

            Vector3f normal = new Vector3f(quad.normal.getX(), quad.normal.getY(), quad.normal.getZ());
            normalMatrix.transform(normal);
            float nx = normal.x();
            float ny = normal.y();
            float nz = normal.z();

            // Same zero-thickness normal correction as the supplied Pulse renderer.
            if ((cube.size.getY() == 0.0F || cube.size.getZ() == 0.0F) && nx < 0.0F) nx = -nx;
            if ((cube.size.getX() == 0.0F || cube.size.getZ() == 0.0F) && ny < 0.0F) ny = -ny;
            if ((cube.size.getX() == 0.0F || cube.size.getY() == 0.0F) && nz < 0.0F) nz = -nz;

            for (GeoVertex vertex : quad.vertices) {
                consumer.vertex(positionMatrix,
                                vertex.position.getX(), vertex.position.getY(), vertex.position.getZ())
                        .color(255, 255, 255, 255)
                        .texture(vertex.textureU, vertex.textureV)
                        .overlay(overlay)
                        .light(light)
                        .normal(nx, ny, nz);
            }
        }
        matrices.pop();
    }

    private PulseGeoRenderer() {}
}
