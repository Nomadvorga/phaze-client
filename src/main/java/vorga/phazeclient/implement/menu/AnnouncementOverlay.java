package vorga.phazeclient.implement.menu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.implement.Rectangle;
import vorga.phazeclient.base.util.PhazeAnnouncements;

import java.util.List;

/**
 * Draws operator announcements as a stack of cards near the top of the
 * screen.
 *
 * <p>Rendered from two places - the in-game HUD and the title screen -
 * because an announcement with no server attached is meant to reach
 * players wherever they are, and "new build is out" is arguably most
 * useful on the main menu, before they join anything.
 *
 * <p>Positioned top-centre and below the vanilla toast area: the
 * bottom is chat, the middle is the crosshair, and hugging a corner
 * makes something meant to be read easy to miss.
 */
public final class AnnouncementOverlay {

    private static final float MAX_WIDTH = 320.0F;
    private static final float PAD_X = 12.0F;
    private static final float PAD_Y = 9.0F;
    private static final float TEXT_SIZE = 8.0F;
    private static final float TOP_MARGIN = 14.0F;
    private static final float CARD_GAP = 5.0F;
    private static final float CORNER = 6.0F;

    /** Fade applied over the last moment of a card's life. */
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
        float cardWidth = Math.min(MAX_WIDTH, screenWidth - 24.0F);
        float textWidth = cardWidth - PAD_X * 2.0F;
        float y = TOP_MARGIN;

        MatrixStack matrices = context.getMatrices();
        long now = System.currentTimeMillis();

        for (PhazeAnnouncements.Banner banner : banners) {
            String[] lines = wrap(banner.text(), textWidth);
            float lineHeight = TEXT_SIZE + 3.0F;
            float cardHeight = PAD_Y * 2.0F + lines.length * lineHeight - 3.0F;
            float x = (screenWidth - cardWidth) / 2.0F;

            long remaining = banner.shownUntilMs() - now;
            float alpha = remaining >= FADE_MS
                    ? 1.0F
                    : Math.max(0.0F, remaining / (float) FADE_MS);

            RECTANGLE.render(ShapeProperties.create(matrices, x, y, cardWidth, cardHeight)
                    .round(CORNER)
                    .thickness(1.0F)
                    .outlineColor(MenuStyle.withAlpha(MenuStyle.CHIP_ACTIVE, alpha * 0.55F))
                    .color(MenuStyle.withAlpha(MenuStyle.PANEL_BG, alpha * 0.92F))
                    .build());

            float textY = y + PAD_Y;
            for (String line : lines) {
                float lineX = x + (cardWidth - MsdfFonts.bold().getWidth(line, TEXT_SIZE)) / 2.0F;
                MsdfRenderer.renderText(
                        MsdfFonts.bold(), line, TEXT_SIZE,
                        MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, alpha),
                        matrices.peek().getPositionMatrix(),
                        lineX, textY, 0.0F);
                textY += lineHeight;
            }

            y += cardHeight + CARD_GAP;
        }
    }

    /**
     * Greedy word wrap against the measured font, falling back to a
     * hard split for a single word longer than the card - a pasted URL
     * should overflow into the next line, not off the edge.
     */
    private static String[] wrap(String text, float maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (MsdfFonts.bold().getWidth(candidate, TEXT_SIZE) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
            }
            while (MsdfFonts.bold().getWidth(word, TEXT_SIZE) > maxWidth && word.length() > 1) {
                int cut = word.length();
                while (cut > 1 && MsdfFonts.bold().getWidth(word.substring(0, cut), TEXT_SIZE) > maxWidth) {
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
