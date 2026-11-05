package vorga.phazeclient.implement.menu.font;

import net.minecraft.client.util.math.MatrixStack;

/** Menu text boundary: size, position and measurements are all screen-space GUI units. */
public interface GuiTextRenderer {
    record Metrics(float width, float height) {
    }

    /** CPU-only measurement of the same formatted layout used by draw. Empty text retains one line box. */
    Metrics measure(String text);

    /** Draw at the layout's top coordinate; no legacy baseline adjustment or doubled height. */
    void draw(MatrixStack matrices, String text, double x, double top, int color);
}
