package vorga.phazeclient.base.util.render;

import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Bridge between the GUI's 2D pose and the 4x4 matrices Phaze's shader
 * draws still take.
 *
 * <h3>Why this exists</h3>
 *
 * 1.21.6 moved GUI rendering from {@code MatrixStack} (4x4) to
 * {@code org.joml.Matrix3x2fStack}, so {@code DrawContext.getMatrices()}
 * no longer returns something with {@code peek().getPositionMatrix()}.
 * Phaze's custom draws feed a {@code Matrix4f} to
 * {@code BufferBuilder.vertex(Matrix4fc, ...)}, which still exists, so
 * the cheapest correct move is to promote the 2D pose at the draw call
 * rather than rewrite every shader path to 2D.
 *
 * <p>{@link #mat4} is vanilla's own idiom - {@code GlyphGuiElementRenderState}
 * promotes its pose the same way.
 */
public final class GuiMatrix {

    private GuiMatrix() {
    }

    /** Promotes a 2D GUI pose to the 4x4 matrix the vertex shaders expect. */
    public static Matrix4f mat4(Matrix3x2fc pose) {
        return new Matrix4f().mul(pose);
    }

    /**
     * Scratch-reusing variant for hot paths (HUD, batched rects) where a
     * fresh Matrix4f per draw would be pure garbage.
     */
    public static Matrix4f mat4(Matrix3x2fc pose, Matrix4f dest) {
        return dest.identity().mul(pose);
    }

    /**
     * Extracts the scale factors from a 2D pose.
     *
     * <p>{@code Matrix3x2f} has no {@code getScale}. The shear terms
     * {@code m01} / {@code m10} are load-bearing here because Phaze
     * rotates some components, so this takes the column lengths rather
     * than reading {@code m00} / {@code m11} directly.
     *
     * <p>Z is always 1: a 2D pose cannot scale depth.
     */
    public static Vector3f getScale(Matrix3x2fc pose, Vector3f dest) {
        return dest.set(
                (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01()),
                (float) Math.sqrt(pose.m10() * pose.m10() + pose.m11() * pose.m11()),
                1.0F);
    }
}
