package vorga.phazeclient.implement.menu.components.implement.settings;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import vorga.phazeclient.api.system.font.FontRenderer;
import vorga.phazeclient.api.system.font.msdf.MsdfFont;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.base.util.render.ScissorManager;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.menu.font.MsdfGuiFontRenderer;

/**
 * A single-line label, never an ellipsis or a background-coloured mask.
 * One MSDF draw for either fitting or overflowing text. The fragment shader fades
 * both edges continuously; one parent-intersected scissor clips the viewport.
 * No repeated text draws, per-label texture or offscreen framebuffer is needed.
 */
public final class SettingLabel {
    /** Screens that clip outside {@link ScissorManager} must intersect with their own active clip. */
    public interface Clip {
        void push(Matrix4f matrix, float x, float y, float width, float height);

        void pop();
    }

    private final SettingLabelState state = new SettingLabelState();
    private String source;
    private String line = "";
    private float animatedTextInset;
    private Clip clip;

    public void setAnimatedTextInset(float inset) {
        animatedTextInset = inset;
    }

    public SettingLabel clip(Clip clip) {
        this.clip = clip;
        return this;
    }

    public String singleLine(String text) {
        if (text == null) text = "";
        if (!text.equals(source)) {
            source = text;
            line = text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
                    .replace('\u2028', ' ').replace('\u2029', ' ');
        }
        return line;
    }

    public void render(MatrixStack matrices, FontRenderer font, String text, float textX, float textY,
                       float right, float top, float height, int color) {
        text = singleLine(text);
        if (font instanceof MsdfGuiFontRenderer gui) {
            render(matrices, gui.msdfFont(), text, gui.msdfSize(), font.getStringWidth(text),
                    textX, gui.msdfY(textY), right, top, height, color);
        } else {
            float size = font.getFont().getSize2D() / 2.0F;
            render(matrices, MsdfFonts.bold(), text, size, renderedWidth(text, size),
                    textX, textY, right, top, height, color);
        }
    }

    public void renderMsdf(MatrixStack matrices, String text, float size, float textX, float textY,
                           float right, float top, float height, int color) {
        text = singleLine(text);
        // applyGlyphs adds thickness * .5 * size to each advance; getWidth omits it.
        render(matrices, MsdfFonts.bold(), text, size, renderedWidth(text, size), textX, textY, right, top, height, color);
    }

    private static float renderedWidth(String text, float size) {
        return MsdfFonts.bold().getWidth(text, size) + Math.max(0, text.length() - 1) * 0.025F * size;
    }

    private void render(MatrixStack matrices, MsdfFont font, String text, float size, float textWidth,
                        float textX, float textY, float right, float top, float height, int color) {
        long now = System.nanoTime();
        state.layout(text, textWidth, right - textX, height, now, animatedTextInset);
        // The clip, shader fade and marquee share the same animated viewport.
        float width = state.getWidth();
        if (width <= 0.0F || height <= 0.0F) return;
        if (!state.isOverflowing()) {
            MsdfRenderer.renderText(font, text, size, color, matrices.peek().getPositionMatrix(), textX, textY, 0);
            return;
        }
        float offset = state.offset(now);
        float fade = state.fadeWidth();
        float leftStrength = Math.max(0, Math.min(1, offset / fade));
        float rightStrength = Math.max(0, Math.min(1, (state.getOverflow() - offset) / fade));
        Clip activeClip = clip;
        ScissorManager scissor = activeClip == null ? Main.getInstance().getScissorManager() : null;
        // MsdfRenderer's emergency vanilla fallback queues entity vertices.
        // Drain any earlier fallback while the parent clip is still active.
        if (MsdfRenderer.isUsingFallback()) flushFallback();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        if (activeClip != null) activeClip.push(matrix, textX, top, width, height);
        else scissor.push(matrix, textX, top, width, height);
        try {
            float scale = Math.abs(matrix.m00());
            float left = matrix.m00() * textX + matrix.m30();
            MsdfRenderer.renderTextFaded(font, text, size, color, matrix, textX - offset, textY, 0,
                    left, left + width * scale, fade * scale, leftStrength, rightStrength);
            if (MsdfRenderer.isUsingFallback()) flushFallback();
        } finally {
            if (activeClip != null) activeClip.pop();
            else scissor.pop();
        }
    }

    private static void flushFallback() {
        MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers().draw();
    }

    public void updateHover(boolean cardHovered) {
        state.updateHover(cardHovered, System.nanoTime());
    }

    public SettingLabelState state() {
        return state;
    }
}
