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

/**
 * Installs the GUI orthographic projection around an immediate GPU draw.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Through 1.21.4, {@code Screen.render} issued real draw calls, so anything
 * Phaze drew from inside a screen landed while the GUI projection was live and
 * simply worked.
 *
 * <p>1.21.11 made GUI rendering deferred: {@code DrawContext} only appends to a
 * {@link net.minecraft.client.gui.render.state.GuiRenderState}, and the actual
 * GPU pass runs later, inside {@code GuiRenderer.render}, which is the thing
 * that sets the orthographic projection. Phaze still submits its own geometry
 * immediately (via {@code RenderLayer.draw(BuiltBuffer)} and friends), so those
 * draws execute at a point in the frame where the {@code Projection} UBO still
 * holds whatever the last caller left there - the world's perspective matrix,
 * or on the title screen the panorama's. GUI-space coordinates pushed through a
 * perspective matrix land nowhere near the screen, which is why the menu and
 * the in-game HUD rendered as nothing at all rather than as something wrong.
 *
 * <p>{@link #begin()} / {@link #end()} bracket such a draw with the same
 * projection vanilla's own GUI pass uses - {@code ProjectionMatrix2("gui",
 * 1000, 11000, invertY = true)} sized to the scaled window, bound as
 * {@link ProjectionType#ORTHOGRAPHIC} - and restore the previous one after.
 *
 * <p>Nesting is reference-counted because
 * {@link RenderSystem#backupProjectionMatrix()} keeps exactly one saved slot;
 * a nested backup would overwrite it and the outer restore would put back the
 * wrong matrix. Only the outermost pair touches RenderSystem.
 */
public final class GuiProjection {

    private static ProjectionMatrix2 projection;
    private static int depth;

    private GuiProjection() {
    }

    /** Switch to the GUI orthographic projection. Must be paired with {@link #end()}. */
    public static void begin() {
        if (depth > 0) {
            depth++;
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client == null ? null : client.getWindow();
        if (window == null) {
            // Bail WITHOUT incrementing. If depth were bumped here the
            // matching end() would pop a model-view matrix that was never
            // pushed and restore a projection backup that was never taken,
            // corrupting the stack for every draw after it. With depth left
            // at 0 the matching end() is a no-op, which is the only safe
            // reading of "begin did nothing".
            return;
        }
        if (projection == null) {
            projection = new ProjectionMatrix2("phaze_gui", 1000.0F, 11000.0F, true);
        }
        int scale = window.getScaleFactor();
        if (scale <= 0) {
            scale = 1;
        }
        // Everything that can fail or return early is done above; the state
        // mutations below and the depth increment that authorises end() to
        // undo them now happen as one unit.
        GpuBufferSlice slice = projection.set(
                (float) window.getFramebufferWidth() / scale,
                (float) window.getFramebufferHeight() / scale);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(slice, ProjectionType.ORTHOGRAPHIC);

        // The GUI ortho matrix has nearZ = 1000 / farZ = 11000, so a vertex at
        // z = 0 sits in FRONT of the near plane and is clipped away entirely -
        // which is exactly what was happening: correct coordinates, a real
        // draw call, and nothing on screen. Vanilla's GuiRenderer solves this
        // by translating its model-view by z = -11000 before drawing, putting
        // GUI geometry on the far plane; we mirror that here so Phaze's
        // z = 0 quads land in the same place vanilla's do.
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        modelView.translate(0.0F, 0.0F, -11000.0F);
        depth = 1;
    }

    /**
     * Model-view matrix for a GUI draw that supplies its own instead of reading
     * {@link RenderSystem#getModelViewStack()}, and whose vertex positions were
     * ALREADY transformed by the GUI pose on the CPU.
     *
     * <p>{@link #begin()} puts the z = -11000 offset on the RenderSystem stack,
     * which covers everything drawn through {@code RenderLayer.draw}. Draws that
     * pass an explicit matrix - {@code GpuDraw.draw}, which writes
     * {@code DynamicTransforms} straight from its argument - never look at that
     * stack, so they have to fold the same offset in themselves or their
     * geometry lands in front of the near plane and is clipped away.
     *
     * <p>This is the overload every current Phaze caller wants.
     * {@code BufferBuilder.vertex(Matrix4f, x, y, z)} transforms the position on
     * the CPU and stores the RESULT, so by the time the buffer is built the pose
     * is already baked into every vertex. The shader then does
     * {@code ProjMat * ModelViewMat * Position}, so anything other than the bare
     * z offset here would apply the pose a second time.
     *
     * @param dest matrix to write into, to avoid allocating per draw
     */
    public static Matrix4f guiModelView(Matrix4f dest) {
        return dest.translation(0.0F, 0.0F, -11000.0F);
    }

    /**
     * As {@link #guiModelView(Matrix4f)}, but for a draw whose vertices are in
     * RAW GUI coordinates and still need {@code pose} applied by the shader.
     *
     * <p>DO NOT use this with vertices built through
     * {@code BufferBuilder.vertex(Matrix4f, ...)} - those already carry the
     * pose, and passing it again scales/translates the geometry twice. Phaze has
     * no such caller today; every {@code GpuDraw} site bakes its pose and must
     * use {@link #guiModelView(Matrix4f)} instead.
     *
     * @param pose the GUI-space pose the shader should apply
     * @param dest matrix to write into, to avoid allocating per draw
     */
    public static Matrix4f guiModelView(Matrix4fc pose, Matrix4f dest) {
        return dest.translation(0.0F, 0.0F, -11000.0F).mul(pose);
    }

    /** Restore the projection that was live before the matching {@link #begin()}. */
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
