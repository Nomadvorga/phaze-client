package vorga.phazeclient.base.util.render;

import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class GuiMatrix {

    private GuiMatrix() {
    }

    public static Matrix4f mat4(Matrix3x2fc pose) {
        return new Matrix4f().mul(pose);
    }

    public static Matrix4f mat4(Matrix3x2fc pose, Matrix4f dest) {
        return dest.identity().mul(pose);
    }

    public static Vector3f getScale(Matrix3x2fc pose, Vector3f dest) {
        return dest.set(
                (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01()),
                (float) Math.sqrt(pose.m10() * pose.m10() + pose.m11() * pose.m11()),
                1.0F);
    }
}
