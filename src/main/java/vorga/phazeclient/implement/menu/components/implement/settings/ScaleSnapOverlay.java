package vorga.phazeclient.implement.menu.components.implement.settings;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.base.util.render.GuiMatrix;
import vorga.phazeclient.implement.menu.MenuStyle;

/** Centre-screen readout shown while the HUD GUI-scale slider is dragged. */
public final class ScaleSnapOverlay {
    private static final float VALUE_SIZE = 34.0F;
    private static final float HINT_SIZE = 9.0F;
    private static final float GAP = 6.0F;
    private static final long STALE_AFTER_MS = 250L;

    private static volatile String valueText;
    private static volatile String hintText;
    private static volatile boolean snapped;
    private static volatile long updatedAt;

    private ScaleSnapOverlay() {}

    public static void show(float value, boolean isSnapped, String hint) {
        valueText = "x" + String.format(java.util.Locale.ROOT, "%.2f", value);
        hintText = hint;
        snapped = isSnapped;
        updatedAt = System.currentTimeMillis();
    }

    public static void hide() {
        valueText = null;
    }

    public static void render(DrawContext context) {
        String value = valueText;
        if (value == null) return;
        if (System.currentTimeMillis() - updatedAt > STALE_AFTER_MS) {
            valueText = null;
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;

        double guiScale = Math.max(1.0D, client.getWindow().getScaleFactor());
        float centerX = client.getWindow().getScaledWidth() / 2.0F;
        float centerY = client.getWindow().getScaledHeight() / 2.0F;

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(centerX, centerY);
        float inverse = (float) (1.0D / guiScale);
        matrices.scale(inverse, inverse);

        float valueX = -MsdfFonts.bold().getWidth(value, VALUE_SIZE) / 2.0F;
        MsdfRenderer.renderText(MsdfFonts.bold(), value, VALUE_SIZE,
                snapped ? MenuStyle.CHIP_ACTIVE : MenuStyle.TEXT_PRIMARY,
                GuiMatrix.mat4(matrices), valueX, -VALUE_SIZE / 2.0F, 0.0F);

        String hint = hintText;
        if (hint != null && !hint.isEmpty()) {
            float hintX = -MsdfFonts.medium().getWidth(hint, HINT_SIZE) / 2.0F;
            MsdfRenderer.renderText(MsdfFonts.medium(), hint, HINT_SIZE,
                    MenuStyle.TEXT_MUTED, GuiMatrix.mat4(matrices),
                    hintX, VALUE_SIZE / 2.0F + GAP, 0.0F);
        }
        matrices.popMatrix();
    }
}
