package vorga.phazeclient.implement.menu.components.implement.settings;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.implement.menu.MenuStyle;

/**
 * The large "x1.00" readout that appears in the middle of the screen
 * while a scale slider is being dragged.
 *
 * <h3>Why it is centred and not next to the slider</h3>
 * The thing being resized is somewhere else on screen, so the eye is
 * already off the slider. A centred readout is where you are looking
 * anyway, and it is far enough from the pointer not to sit under it.
 *
 * <h3>Why it ignores GUI Scale</h3>
 * Minecraft scales the whole interface by an integer factor, so text
 * drawn normally is four times bigger at GUI Scale 4 than at 1. This
 * readout is a measuring aid, not part of the layout - it should be
 * the same physical size on screen whichever scale the player runs.
 * Dividing the transform by the window's scale factor cancels
 * Minecraft's multiplication, leaving a constant on-screen size.
 *
 * <p>State is static and set by whichever {@link ValueComponent} is
 * being dragged: only one slider can be dragged at a time, and the
 * overlay has to outlive the component's own render pass so the menu
 * can draw it last, above every panel and dialog.
 */
public final class ScaleSnapOverlay {

    /** Physical size of the value text, in unscaled screen pixels. */
    private static final float VALUE_SIZE = 34.0F;
    private static final float HINT_SIZE = 9.0F;
    private static final float GAP = 6.0F;

    /** Cleared this long after the last update, in case a drag ends abnormally. */
    private static final long STALE_AFTER_MS = 250L;

    private static volatile String valueText;
    private static volatile String hintText;
    private static volatile boolean snapped;
    private static volatile long updatedAt;

    private ScaleSnapOverlay() {
    }

    /**
     * Called every frame a scale slider is being dragged.
     *
     * @param value   current value, already snapped
     * @param snapped true when the magnet is holding it, which is
     *                worth showing - otherwise the player cannot tell
     *                "exactly 1.0" from "1.0 rounded for display"
     */
    public static void show(float value, boolean snapped, String hint) {
        valueText = "x" + String.format(java.util.Locale.ROOT, "%.2f", value);
        hintText = hint;
        ScaleSnapOverlay.snapped = snapped;
        updatedAt = System.currentTimeMillis();
    }

    public static void hide() {
        valueText = null;
    }

    public static void render(DrawContext context) {
        String value = valueText;
        if (value == null) {
            return;
        }
        if (System.currentTimeMillis() - updatedAt > STALE_AFTER_MS) {
            valueText = null;
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }

        double guiScale = client.getWindow().getScaleFactor();
        if (guiScale <= 0.0D) {
            guiScale = 1.0D;
        }
        // Work in scaled coordinates for positioning, then undo the
        // GUI scale for the glyph size only, so the text lands in the
        // middle of the screen at a fixed physical size.
        float centerX = client.getWindow().getScaledWidth() / 2.0F;
        float centerY = client.getWindow().getScaledHeight() / 2.0F;

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(centerX, centerY, 0.0F);
        float inverse = (float) (1.0D / guiScale);
        matrices.scale(inverse, inverse, 1.0F);

        int valueColor = snapped ? MenuStyle.CHIP_ACTIVE : MenuStyle.TEXT_PRIMARY;

        float valueX = -MsdfFonts.bold().getWidth(value, VALUE_SIZE) / 2.0F;
        MsdfRenderer.renderText(
                MsdfFonts.bold(), value, VALUE_SIZE,
                valueColor,
                matrices.peek().getPositionMatrix(),
                valueX, -VALUE_SIZE / 2.0F, 0.0F);

        String hint = hintText;
        if (hint != null && !hint.isEmpty()) {
            float hintX = -MsdfFonts.medium().getWidth(hint, HINT_SIZE) / 2.0F;
            MsdfRenderer.renderText(
                    MsdfFonts.medium(), hint, HINT_SIZE,
                    MenuStyle.TEXT_MUTED,
                    matrices.peek().getPositionMatrix(),
                    hintX, VALUE_SIZE / 2.0F + GAP, 0.0F);
        }

        matrices.pop();
    }
}
