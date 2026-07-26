package vorga.phazeclient.api.system.shape;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector4f;
import org.joml.Vector4i;

@Builder
@Getter
@Setter
public class ShapeProperties {
    /**
     * GUI pose at submission time, as an owned COPY.
     *
     * <p>1.21.11 replaced the 4x4 {@code MatrixStack} with
     * {@code Matrix3x2fStack}. The copy is mandatory rather than stylistic:
     * shapes are consumed lazily (batched rects, blur, card snapshots), so
     * holding the live stack would let a deferred draw read the pose after
     * the caller already popped it. Vanilla copies at every one of its own
     * render-state construction sites for the same reason.
     */
    private Matrix3x2f matrix;
    private float x, y, width, height;
    private float softness, thickness;
    private float start, end;

    @Builder.Default
    private float quality = 20;

    @Builder.Default
    private float rotation = 90;

    private Vector4f round;

    @Builder.Default
    private int outlineColor = -1;
    private Vector4i color;

    @Builder(toBuilder = true)
    private ShapeProperties(Matrix3x2f matrix, float x, float y, float width, float height, float softness, float thickness, float start, float end, float quality, float rotation, Vector4f round, int outlineColor, Vector4i color) {
        this.matrix = matrix;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.softness = softness;
        this.thickness = thickness;
        this.round = round != null ? round : new Vector4f(0);
        this.outlineColor = outlineColor;
        this.color = color != null ? color : new Vector4i(-1);
        this.start = start;
        this.end = end;
        this.quality = quality;
        this.rotation = rotation;
    }

    public static class ShapePropertiesBuilder {

        public ShapePropertiesBuilder color(int color) {
            this.color = new Vector4i(color);
            return this;
        }

        public ShapePropertiesBuilder color(Vector4i color) {
            this.color = color;
            return this;
        }

        public ShapePropertiesBuilder color(int... color) {
            this.color = new Vector4i(color);
            return this;
        }

        public ShapePropertiesBuilder round(float round) {
            this.round = new Vector4f(round);
            return this;
        }

        public ShapePropertiesBuilder round(Vector4f round) {
            this.round = new Vector4f(round);
            return this;
        }

        public ShapePropertiesBuilder round(float... round) {
            this.round = new Vector4f(round);
            return this;
        }
    }

    /**
     * Takes {@code Matrix3x2fc} so that a live {@code Matrix3x2fStack} can
     * be passed straight in - every existing call site compiles unchanged -
     * while the stored value is a defensive copy.
     */
    public static ShapeProperties.ShapePropertiesBuilder create(Matrix3x2fc matrix, double x, double y, double width, double height) {
        return ShapeProperties.builder().matrix(new Matrix3x2f(matrix)).x((float) x).y((float) y).width((float) width).height((float) height);
    }
}