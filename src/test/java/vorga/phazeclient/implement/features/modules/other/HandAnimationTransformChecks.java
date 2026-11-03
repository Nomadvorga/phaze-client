package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Offline regression checks for Change Hand offsets combined with classic item animations. */
public final class HandAnimationTransformChecks {
    private static final float EPSILON = 0.0001F;
    private static final float[] PROGRESS = {0, 0.0001F, 0.05F, 0.15F, 0.25F, 0.5F, 0.75F, 0.95F, 0.9999F, 1};
    private static int checks;

    public static void main(String[] args) {
        checks = 0;
        classicSwing();
        handOffsets();
        System.out.println("Hand animation transform checks passed: " + checks);
    }

    private static void classicSwing() {
        for (float equip : new float[]{0, 0.35F, 1}) {
            for (float progress : PROGRESS) {
                MatrixStack right = new MatrixStack();
                OldAnimations.renderOldSwing(right, Arm.RIGHT, 1, progress, equip);
                matrixEquals(right.peek().getPositionMatrix(), referenceSwing(1, progress, equip),
                        "right-hand swing matches the 1.8 curve at " + progress);

                MatrixStack left = new MatrixStack();
                OldAnimations.renderOldSwing(left, Arm.LEFT, -1, progress, equip);
                matrixEquals(left.peek().getPositionMatrix(), referenceSwing(-1, progress, equip),
                        "left-hand swing matches the 1.8 curve at " + progress);
                Matrix4f mirrored = new Matrix4f().scaling(-1, 1, 1)
                        .mul(right.peek().getPositionMatrix()).scale(-1, 1, 1);
                matrixEquals(left.peek().getPositionMatrix(), mirrored, "left-hand swing is mirrored");
            }
            for (float progress : new float[]{0, 1}) {
                for (Arm arm : Arm.values()) {
                    int sign = arm == Arm.RIGHT ? 1 : -1;
                    MatrixStack matrices = new MatrixStack();
                    OldAnimations.renderOldSwing(matrices, arm, sign, progress, equip);
                    matrixEquals(matrices.peek().getPositionMatrix(),
                            new Matrix4f().translation(sign * 0.56F, -0.52F - equip * 0.6F, -0.72F),
                            "swing endpoints match vanilla idle/equip pose without a 45-degree jump");
                }
            }
        }
    }

    /** 1.8 ItemRenderer rotations, with the fixed -45-degree modern model-basis conversion. */
    private static Matrix4f referenceSwing(int sign, float progress, float equip) {
        float root = MathHelper.sqrt(progress);
        float sinRoot = MathHelper.sin(root * (float) Math.PI);
        return new Matrix4f()
                .translation(sign * (-0.4F * sinRoot + 0.56F),
                        0.2F * MathHelper.sin(root * (float) (Math.PI * 2)) - 0.52F - equip * 0.6F,
                        -0.2F * MathHelper.sin(progress * (float) Math.PI) - 0.72F)
                .rotateY(radians(sign * 45))
                .rotateY(radians(sign * -20 * MathHelper.sin(progress * progress * (float) Math.PI)))
                .rotateZ(radians(sign * -20 * sinRoot))
                .rotateX(radians(-80 * sinRoot))
                .rotateY(radians(sign * -45));
    }

    private static void handOffsets() {
        ChangeHand hand = ChangeHand.getInstance();
        float[] saved = {hand.mainHandX.getValue(), hand.mainHandY.getValue(), hand.mainHandZ.getValue(),
                hand.mainHandScale.getValue(), hand.offHandX.getValue(), hand.offHandY.getValue(),
                hand.offHandZ.getValue(), hand.offHandScale.getValue()};
        try {
            for (Arm mainArm : Arm.values()) {
                for (boolean mainHand : new boolean[]{true, false}) {
                    Arm renderedArm = mainHand ? mainArm : mainArm.getOpposite();
                    int armSign = renderedArm == Arm.RIGHT ? 1 : -1;
                    for (float z : new float[]{-2.5F, -0.75F, 0, 0.35F, 0.75F, 2.5F}) {
                        // Give the other hand different values to catch accidentally shared settings.
                        hand.mainHandX.setValue(0.23F);
                        hand.mainHandY.setValue(-0.17F);
                        hand.mainHandZ.setValue(mainHand ? z : 0.41F);
                        hand.offHandX.setValue(-0.31F);
                        hand.offHandY.setValue(0.11F);
                        hand.offHandZ.setValue(mainHand ? -0.57F : z);
                        for (float scale : new float[]{0.1F, 0.75F, 1, 2}) {
                            hand.mainHandScale.setValue(mainHand ? scale : 0.63F);
                            hand.offHandScale.setValue(mainHand ? 1.37F : scale);
                            for (float progress : PROGRESS) {
                                for (boolean blocking : new boolean[]{false, true}) {
                                    checkPoseOffset(hand, mainArm, mainHand, renderedArm, armSign,
                                            progress, blocking, z, scale);
                                }
                            }
                        }
                    }
                    hand.mainHandX.setValue(0);
                    hand.mainHandY.setValue(0);
                    hand.mainHandZ.setValue(0);
                    hand.mainHandScale.setValue(1);
                    hand.offHandX.setValue(0);
                    hand.offHandY.setValue(0);
                    hand.offHandZ.setValue(0);
                    hand.offHandScale.setValue(1);
                    MatrixStack defaults = cameraMatrices();
                    Matrix4f before = new Matrix4f(defaults.peek().getPositionMatrix());
                    hand.applyHandOffset(defaults, mainHand, mainArm);
                    hand.applyHandScale(defaults, mainHand);
                    matrixEquals(defaults.peek().getPositionMatrix(), before, "default settings are no-ops");
                }
            }
        } finally {
            hand.mainHandX.setValue(saved[0]);
            hand.mainHandY.setValue(saved[1]);
            hand.mainHandZ.setValue(saved[2]);
            hand.mainHandScale.setValue(saved[3]);
            hand.offHandX.setValue(saved[4]);
            hand.offHandY.setValue(saved[5]);
            hand.offHandZ.setValue(saved[6]);
            hand.offHandScale.setValue(saved[7]);
        }
    }

    private static void checkPoseOffset(ChangeHand hand, Arm mainArm, boolean mainHand,
                                        Arm renderedArm, int armSign, float progress,
                                        boolean blocking, float z, float scale) {
        MatrixStack baseline = cameraMatrices();
        renderPose(baseline, renderedArm, armSign, progress, blocking);
        MatrixStack actual = cameraMatrices();
        Matrix4f camera = new Matrix4f(actual.peek().getPositionMatrix());
        actual.push();
        hand.applyHandOffset(actual, mainHand, mainArm);
        renderPose(actual, renderedArm, armSign, progress, blocking);
        hand.applyHandScale(actual, mainHand);

        float xSign = mainArm == Arm.RIGHT ? 1 : -1;
        Vector3f offset = camera.transformDirection(new Vector3f(
                xSign * (mainHand ? 0.23F : -0.31F), mainHand ? -0.17F : 0.11F, z));
        Matrix4f expected = new Matrix4f(baseline.peek().getPositionMatrix());
        expected.m30(expected.m30() + offset.x);
        expected.m31(expected.m31() + offset.y);
        expected.m32(expected.m32() + offset.z);
        expected.scale(scale);
        matrixEquals(actual.peek().getPositionMatrix(), expected,
                "XYZ offsets stay in view space, independent of swing/block rotations and scale");
        check(actual.peek().getNormalMatrix().equals(baseline.peek().getNormalMatrix(), EPSILON),
                "offset and uniform item scale preserve lighting orientation");
        actual.pop();
        matrixEquals(actual.peek().getPositionMatrix(), camera, "one hand's offsets do not leak into the other");
    }

    private static MatrixStack cameraMatrices() {
        MatrixStack matrices = new MatrixStack();
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(13));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-9));
        return matrices;
    }

    private static void renderPose(MatrixStack matrices, Arm arm, int sign, float progress, boolean blocking) {
        if (blocking) {
            OldAnimations.renderSwordBlock(matrices, arm, sign, 0.35F, progress);
        } else {
            OldAnimations.renderOldSwing(matrices, arm, sign, progress, 0.35F);
        }
    }

    private static float radians(float degrees) {
        return degrees * ((float) Math.PI / 180.0F);
    }

    private static void matrixEquals(Matrix4f actual, Matrix4f expected, String message) {
        check(actual.equals(expected, EPSILON), message + "\nExpected: " + expected + "\nActual: " + actual);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
