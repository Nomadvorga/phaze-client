package vorga.phazeclient.implement.cosmetics;

import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import org.joml.Matrix3x2f;

/** Deferred 3D catalog thumbnail submitted to the 1.21.11 GUI renderer. */
public record CosmeticGuiElementState(
        String selection,
        int x1,
        int y1,
        int x2,
        int y2,
        float alpha,
        float scale,
        long frameId,
        Matrix3x2f pose,
        ScreenRect scissorArea,
        ScreenRect bounds
) implements SpecialGuiElementRenderState {
    public CosmeticGuiElementState(String selection, int x1, int y1, int x2, int y2,
                                   float alpha, long frameId,
                                   Matrix3x2f pose, ScreenRect scissorArea) {
        this(selection, x1, y1, x2, y2, alpha, 1.0F, frameId, pose, scissorArea,
                bounds(x1, y1, x2, y2, pose, scissorArea));
    }

    private static ScreenRect bounds(int x1, int y1, int x2, int y2,
                                     Matrix3x2f pose, ScreenRect scissor) {
        ScreenRect transformed = new ScreenRect(x1, y1, x2 - x1, y2 - y1).transform(pose);
        return scissor == null ? transformed : scissor.intersection(transformed);
    }
}
