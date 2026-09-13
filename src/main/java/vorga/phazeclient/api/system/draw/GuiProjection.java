package vorga.phazeclient.api.system.draw;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.client.util.Window;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;

public final class GuiProjection {

    private static ProjectionMatrix2 projection;
    private static int depth;

    private GuiProjection() {
    }

    public static void begin() {
        if (depth > 0) {
            depth++;
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client == null ? null : client.getWindow();
        if (window == null) {

            return;
        }
        if (projection == null) {
            projection = new ProjectionMatrix2("phaze_gui", 1000.0F, 11000.0F, true);
        }
        int scale = window.getScaleFactor();
        if (scale <= 0) {
            scale = 1;
        }

        GpuBufferSlice slice = projection.set(
                (float) window.getFramebufferWidth() / scale,
                (float) window.getFramebufferHeight() / scale);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(slice, ProjectionType.ORTHOGRAPHIC);

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        modelView.translate(0.0F, 0.0F, -11000.0F);
        depth = 1;
    }

    public static Matrix4f guiModelView(Matrix4f dest) {
        return dest.translation(0.0F, 0.0F, -11000.0F);
    }

    public static Matrix4f guiModelView(Matrix4fc pose, Matrix4f dest) {
        return dest.translation(0.0F, 0.0F, -11000.0F).mul(pose);
    }

    public static void end() {
        if (depth <= 0) {
            return;
        }
        if (--depth > 0) {
            return;
        }
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.restoreProjectionMatrix();
    }
}
