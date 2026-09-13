package vorga.phazeclient.implement.menu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

import vorga.phazeclient.base.util.render.GuiMatrix;

import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.implement.Rectangle;
import vorga.phazeclient.base.util.PhazeAnnouncements;

import java.util.List;

public final class AnnouncementOverlay {

    private static final float MAX_WIDTH = 320.0F;
    private static final float PAD_X = 12.0F;
    private static final float PAD_Y = 9.0F;
    private static final float TEXT_SIZE = 8.0F;

    private static final float TOP_FRACTION = 0.17F;
    private static final float MIN_TOP_MARGIN = 26.0F;

    private static final int BACKGROUND = 0xFF000000;
    private static final float BACKGROUND_ALPHA = 0.5F;
    private static final float CARD_GAP = 5.0F;
    private static final float CORNER = 6.0F;

    private static final float RENDER_THICKNESS = 0.05F;
    private static final float PER_GLYPH_EXTRA = RENDER_THICKNESS * 0.5F * TEXT_SIZE;

    private static final float BASELINE_NUDGE = 1.15F;

    private static final long FADE_MS = 600L;

    private static final Rectangle RECTANGLE = new Rectangle();

    private AnnouncementOverlay() {
    }

    public static void render(DrawContext context) {
        List<PhazeAnnouncements.Banner> banners = PhazeAnnouncements.activeBanners();
        if (banners.isEmpty()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }

        float screenWidth = client.getWindow().getScaledWidth();
        float screenHeight = client.getWindow().getScaledHeight();
        float maxCardWidth = Math.min(MAX_WIDTH, screenWidth - 24.0F);
        float maxTextWidth = maxCardWidth - PAD_X * 2.0F;

        float y = Math.max(MIN_TOP_MARGIN, screenHeight * TOP_FRACTION);

        Matrix3x2fStack matrices = context.getMatrices();
        long now = System.currentTimeMillis();

        for (PhazeAnnouncements.Banner banner : banners) {
            String[] lines = wrap(banner.text(), maxTextWidth);

            float widest = 0.0F;
            for (String line : lines) {
                widest = Math.max(widest, measure(line));
            }
            float cardWidth = Math.min(maxCardWidth, widest + PAD_X * 2.0F);

            float lineHeight = TEXT_SIZE + 3.0F;
            float textBlockHeight = lines.length * lineHeight - 3.0F;
            float cardHeight = PAD_Y * 2.0F + textBlockHeight;
            float x = (screenWidth - cardWidth) / 2.0F;

            long remaining = banner.shownUntilMs() - now;
            float alpha = remaining >= FADE_MS
                    ? 1.0F
                    : Math.max(0.0F, remaining / (float) FADE_MS);

            RECTANGLE.render(ShapeProperties.create(matrices, x, y, cardWidth, cardHeight)
                    .round(CORNER)
                    .color(MenuStyle.withAlpha(BACKGROUND, alpha * BACKGROUND_ALPHA))
                    .build());

            float textY = y + (cardHeight - textBlockHeight) / 2.0F + BASELINE_NUDGE;
            for (String line : lines) {
                float lineX = x + (cardWidth - measure(line)) / 2.0F;
                MsdfRenderer.renderText(
                        MsdfFonts.bold(), line, TEXT_SIZE,
                        MenuStyle.withAlpha(banner.color(), alpha),
                        GuiMatrix.mat4(matrices),
                        lineX, textY, 0.0F);
                textY += lineHeight;
            }

            y += cardHeight + CARD_GAP;
        }
    }

    private static float measure(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0F;
        }
        return MsdfFonts.bold().getWidth(text, TEXT_SIZE) + text.length() * PER_GLYPH_EXTRA;
    }

    private static String[] wrap(String text, float maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (measure(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
            }
            while (measure(word) > maxWidth && word.length() > 1) {
                int cut = word.length();
                while (cut > 1 && measure(word.substring(0, cut)) > maxWidth) {
                    cut--;
                }
                lines.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            current.append(word);
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines.isEmpty() ? new String[]{text} : lines.toArray(new String[0]);
    }
}
