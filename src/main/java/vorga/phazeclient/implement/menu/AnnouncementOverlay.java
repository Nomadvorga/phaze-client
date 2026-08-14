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
    /** Fraction of screen height the stack starts at, floored by MIN_TOP_MARGIN. */
    private static final float TOP_FRACTION = 0.17F;
    private static final float MIN_TOP_MARGIN = 26.0F;

    /**
     * Flat black at half opacity, the same treatment the HUD uses.
     * Opaque on purpose: MenuStyle.withAlpha multiplies the existing
     * alpha byte, so a 0x000000 constant would come out invisible.
     */
    private static final int BACKGROUND = 0xFF000000;
    private static final float BACKGROUND_ALPHA = 0.5F;
    private static final float CARD_GAP = 5.0F;
    private static final float CORNER = 6.0F;

    /**
     * MsdfRenderer's default glyph thickness. applyGlyphs adds
     * {@code thickness * 0.5 * size} to the pen after every glyph,
     * but MsdfFont.getWidth sums glyph widths only - so measured text
     * comes out narrower than drawn text, by this much per character.
     * On a forty-character banner that is several pixels, which is
     * enough to push the text visibly off-centre inside a card sized
     * from the unadjusted measurement.
     */
    private static final float RENDER_THICKNESS = 0.05F;
    private static final float PER_GLYPH_EXTRA = RENDER_THICKNESS * 0.5F * TEXT_SIZE;

    /**
     * MSDF text is drawn from its top edge with the baseline pushed
     * down inside the em box, so geometric centring sits slightly
     * high. Same correction MenuStyle.centerMsdfTextY applies.
     */
    private static final float BASELINE_NUDGE = 1.15F;

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
        float screenHeight = client.getWindow().getScaledHeight();
        float maxCardWidth = Math.min(MAX_WIDTH, screenWidth - 24.0F);
        float maxTextWidth = maxCardWidth - PAD_X * 2.0F;

        // Proportional rather than a fixed offset: sitting clear of the
        // vanilla toast strip at any GUI scale matters more than being
        // at an exact pixel.
        float y = Math.max(MIN_TOP_MARGIN, screenHeight * TOP_FRACTION);

        MatrixStack matrices = context.getMatrices();
        long now = System.currentTimeMillis();

        for (PhazeAnnouncements.Banner banner : banners) {
            String[] lines = wrap(banner.text(), maxTextWidth);

            // The card is sized to its text, so a three-word notice
            // does not get the same slab as a full sentence.
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

            // Centre the text block vertically instead of pinning it to
            // the top padding, so one-line and two-line cards both sit
            // in the middle of their background.
            float textY = y + (cardHeight - textBlockHeight) / 2.0F + BASELINE_NUDGE;
            for (String line : lines) {
                float lineX = x + (cardWidth - measure(line)) / 2.0F;
                MsdfRenderer.renderText(
                        MsdfFonts.bold(), line, TEXT_SIZE,
                        MenuStyle.withAlpha(banner.color(), alpha),
                        matrices.peek().getPositionMatrix(),
                        lineX, textY, 0.0F);
                textY += lineHeight;
            }

            y += cardHeight + CARD_GAP;
        }
    }

    /**
     * Width the text will actually occupy once drawn, as opposed to
     * the sum of its glyph widths.
     */
    private static float measure(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0F;
        }
        return MsdfFonts.bold().getWidth(text, TEXT_SIZE) + text.length() * PER_GLYPH_EXTRA;
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
