package vorga.phazeclient.implement.menu.font;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import vorga.phazeclient.api.system.font.FontRenderer;
import vorga.phazeclient.api.system.font.msdf.MsdfFont;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.base.util.color.ColorUtil;
import vorga.phazeclient.base.util.math.MathUtil;

import java.awt.Font;
import java.util.HashMap;
import java.util.Map;

/** FontRenderer-compatible menu adapter. No draw/measurement path uses legacy glyph maps. */
public final class MsdfGuiFontRenderer extends FontRenderer {
    private static final int LAYOUT_CACHE_LIMIT = 512;
    private final float size;
    private final boolean bold;
    private final Map<String, GuiTextLayout> layouts = new HashMap<>();

    MsdfGuiFontRenderer(float size, boolean bold) {
        // The superclass requires an AWT descriptor, but never rasterizes it: every glyph API is overridden.
        super(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, 1), size);
        this.size = size;
        this.bold = bold;
    }

    public MsdfFont msdfFont() {
        return atlas(bold);
    }

    public float msdfSize() {
        return size;
    }

    /** Convert legacy drawString's baseline-adjusted Y to MsdfRenderer's top coordinate. */
    public float msdfY(double y) {
        return (float) y - 1.85F;
    }

    private static MsdfFont atlas(boolean bold) {
        return bold ? MsdfFonts.bold() : MsdfFonts.medium();
    }

    private GuiTextLayout layout(String text) {
        String source = text == null ? "" : text;
        GuiTextLayout cached = layouts.get(source);
        if (cached != null) return cached;
        GuiTextLayout result = GuiTextLayout.create(source, size, bold);
        if (layouts.size() >= LAYOUT_CACHE_LIMIT) layouts.clear();
        layouts.put(source, result);
        return result;
    }

    @Override
    public float getStringWidth(String text) {
        return layout(text).width;
    }

    @Override
    public float getStringWidth(Text text) {
        return getStringWidth(styled(text));
    }

    @Override
    public float getStringHeight(String text) {
        // Legacy callers divide this value by two to obtain screen-space height.
        return layout(text).height * 2.0F;
    }

    @Override
    public float getStringHeight(Text text) {
        return getStringHeight(styled(text));
    }

    @Override
    public void drawString(MatrixStack matrix, String text, double x, double y, int color) {
        if ((color >>> 24) == 0) return;
        for (GuiTextLayout.Run run : layout(text).runs) {
            MsdfRenderer.renderText(atlas(run.bold()), run.text(), size, GuiTextLayout.color(color, run.rgb()),
                    matrix.peek().getPositionMatrix(), (float) x + run.x(), msdfY(y) + run.y(), 0.0F);
        }
    }

    @Override
    public void drawText(MatrixStack matrix, Text text, double x, double y) {
        drawString(matrix, styled(text), x, y, ColorUtil.getText());
    }

    @Override
    public void findStyle(StringBuilder builder, Text text) {
        if (text == null) return;
        text.visit((style, content) -> {
            builder.append("§r");
            if (style.getColor() != null) builder.append('⏏').append(style.getColor().getRgb()).append('⏏');
            if (style.isBold()) builder.append("§l");
            builder.append(content);
            return java.util.Optional.empty();
        }, net.minecraft.text.Style.EMPTY);
    }

    private String styled(Text text) {
        if (text == null) return "";
        StringBuilder builder = new StringBuilder();
        findStyle(builder, text);
        return builder.toString();
    }

    @Override
    public void drawCenteredString(MatrixStack matrix, String text, double x, double y, int color) {
        drawString(matrix, text, x - getStringWidth(text) / 2.0F, y, color);
    }

    @Override
    public void drawStringWithScroll(MatrixStack matrix, String text, double x, double y, float width, int color) {
        String source = text == null ? "" : text;
        String separated = source + "  |  ";
        float textWidth = getStringWidth(separated);
        if (textWidth - width < 10) drawString(matrix, source, x, y, color);
        else drawString(matrix, separated + source, x - MathUtil.textScrolling(textWidth), y, color);
    }

    @Override
    public void drawGradientString(MatrixStack matrix, String text, double x, double y, int start, int end) {
        gradient(matrix, text, x, y, start, end, false, 0, 0);
    }

    @Override
    public void drawAnimatedGradientString(MatrixStack matrix, String text, double x, double y, int start, int end) {
        drawAnimatedGradientString(matrix, text, x, y, start, end, Math.PI, 5.0);
    }

    @Override
    public void drawAnimatedGradientString(MatrixStack matrix, String text, double x, double y,
                                           int start, int end, double waveLength, double speed) {
        gradient(matrix, text, x, y, start, end, true, waveLength, speed);
    }

    private void gradient(MatrixStack matrix, String text, double x, double y, int start, int end,
                          boolean animated, double waveLength, double speed) {
        GuiTextLayout layout = layout(text);
        double time = System.currentTimeMillis() / 1000.0;
        for (int i = 0; i < layout.glyphs.size(); i++) {
            GuiTextLayout.Glyph glyph = layout.glyphs.get(i);
            float t = (float) i / Math.max(1, layout.glyphs.size() - 1);
            if (animated) t = (float) ((Math.sin(t * waveLength - time * speed) + 1.0) * 0.5);
            int color = GuiTextLayout.color(GuiTextLayout.mix(start, end, t), glyph.rgb());
            if ((color >>> 24) == 0) continue;
            MsdfRenderer.renderText(atlas(glyph.bold()), glyph.text(), size, color, matrix.peek().getPositionMatrix(),
                    (float) x + glyph.x(), msdfY(y) + glyph.y(), 0.0F);
        }
    }
}
