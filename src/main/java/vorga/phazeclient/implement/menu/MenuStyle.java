package vorga.phazeclient.implement.menu;

import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.font.FontRenderer;
import vorga.phazeclient.api.system.font.msdf.MsdfFont;

public final class MenuStyle {
    private static final float MSDF_CENTER_Y_OFFSET = 1.15F;
    private static final int TRANSITION_MS = 300;
    private static MenuPalette currentPalette = MenuPalettes.LUNAR_BLUE;
    private static MenuPalette previousPalette = MenuPalettes.LUNAR_BLUE;
    private static MenuPalette targetPalette = MenuPalettes.LUNAR_BLUE;
    private static final Animation themeAnimation = new DecelerateAnimation().setMs(TRANSITION_MS).setValue(1);
    private static String appliedPaletteName = currentPalette.name();

    private MenuStyle() {
    }

    public static int PANEL_BG = currentPalette.panelBg();
    public static int PANEL_BG_SOFT = currentPalette.panelBgSoft();
    public static int PANEL_HEADER = currentPalette.panelHeader();
    public static int PANEL_SIDEBAR = currentPalette.panelSidebar();
    public static int PANEL_CONTENT = currentPalette.panelContent();
    public static int PANEL_CHIP = currentPalette.panelChip();
    public static int PANEL_ROW = currentPalette.panelRow();
    public static int PANEL_ROW_ACTIVE = currentPalette.panelRowActive();

    public static int CARD_BG = currentPalette.cardBg();
    public static int CARD_INNER = currentPalette.cardInner();
    public static int CARD_OPTIONS = currentPalette.cardOptions();
    public static int CARD_DISABLED = currentPalette.cardDisabled();

    public static int BORDER = currentPalette.border();
    public static int BORDER_LIGHT = currentPalette.borderLight();

    public static int TEXT_PRIMARY = currentPalette.textPrimary();
    public static int TEXT_MUTED = currentPalette.textMuted();

    public static int CHIP_ACTIVE = currentPalette.chipActive();
    public static int CHIP_NEW = currentPalette.chipNew();
    public static int ACCENT_GREEN = currentPalette.accentGreen();

    public static void apply(MenuPalette palette) {
        MenuPalette newPalette = palette == null ? MenuPalettes.LUNAR_BLUE : palette;
        String newName = newPalette.name();

        if (!newName.equals(appliedPaletteName)) {
            previousPalette = currentPalette;
            targetPalette = newPalette;
            appliedPaletteName = newName;
            themeAnimation.setDirection(Direction.FORWARDS);
            themeAnimation.reset();
        }

        float progress = themeAnimation.getOutput().floatValue();
        currentPalette = newPalette;

        if (progress < 1.0F) {
            PANEL_BG = mix(previousPalette.panelBg(), targetPalette.panelBg(), progress);
            PANEL_BG_SOFT = mix(previousPalette.panelBgSoft(), targetPalette.panelBgSoft(), progress);
            PANEL_HEADER = mix(previousPalette.panelHeader(), targetPalette.panelHeader(), progress);
            PANEL_SIDEBAR = mix(previousPalette.panelSidebar(), targetPalette.panelSidebar(), progress);
            PANEL_CONTENT = mix(previousPalette.panelContent(), targetPalette.panelContent(), progress);
            PANEL_CHIP = mix(previousPalette.panelChip(), targetPalette.panelChip(), progress);
            PANEL_ROW = mix(previousPalette.panelRow(), targetPalette.panelRow(), progress);
            PANEL_ROW_ACTIVE = mix(previousPalette.panelRowActive(), targetPalette.panelRowActive(), progress);

            CARD_BG = mix(previousPalette.cardBg(), targetPalette.cardBg(), progress);
            CARD_INNER = mix(previousPalette.cardInner(), targetPalette.cardInner(), progress);
            CARD_OPTIONS = mix(previousPalette.cardOptions(), targetPalette.cardOptions(), progress);
            CARD_DISABLED = mix(previousPalette.cardDisabled(), targetPalette.cardDisabled(), progress);

            BORDER = mix(previousPalette.border(), targetPalette.border(), progress);
            BORDER_LIGHT = mix(previousPalette.borderLight(), targetPalette.borderLight(), progress);

            TEXT_PRIMARY = mix(previousPalette.textPrimary(), targetPalette.textPrimary(), progress);
            TEXT_MUTED = mix(previousPalette.textMuted(), targetPalette.textMuted(), progress);

            CHIP_ACTIVE = mix(previousPalette.chipActive(), targetPalette.chipActive(), progress);
            CHIP_NEW = mix(previousPalette.chipNew(), targetPalette.chipNew(), progress);
            ACCENT_GREEN = mix(previousPalette.accentGreen(), targetPalette.accentGreen(), progress);
        } else {
            PANEL_BG = targetPalette.panelBg();
            PANEL_BG_SOFT = targetPalette.panelBgSoft();
            PANEL_HEADER = targetPalette.panelHeader();
            PANEL_SIDEBAR = targetPalette.panelSidebar();
            PANEL_CONTENT = targetPalette.panelContent();
            PANEL_CHIP = targetPalette.panelChip();
            PANEL_ROW = targetPalette.panelRow();
            PANEL_ROW_ACTIVE = targetPalette.panelRowActive();

            CARD_BG = targetPalette.cardBg();
            CARD_INNER = targetPalette.cardInner();
            CARD_OPTIONS = targetPalette.cardOptions();
            CARD_DISABLED = targetPalette.cardDisabled();

            BORDER = targetPalette.border();
            BORDER_LIGHT = targetPalette.borderLight();

            TEXT_PRIMARY = targetPalette.textPrimary();
            TEXT_MUTED = targetPalette.textMuted();

            CHIP_ACTIVE = targetPalette.chipActive();
            CHIP_NEW = targetPalette.chipNew();
            ACCENT_GREEN = targetPalette.accentGreen();
        }
    }

    public static MenuPalette currentPalette() {
        return currentPalette;
    }

    public static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(((color >>> 24) & 0xFF) * alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    public static int detailScrim(float alpha) {
        return withAlpha(mix(PANEL_BG, 0xFF000000, 0.55F), alpha);
    }

    public static int settingSurface(boolean active) {
        return active
                ? mix(PANEL_CHIP, CARD_OPTIONS, 0.50F)
                : mix(PANEL_CHIP, CARD_INNER, 0.38F);
    }

    public static int settingOutline(boolean active) {
        return active
                ? mix(BORDER_LIGHT, CHIP_ACTIVE, 0.40F)
                : BORDER;
    }

    public static int accent(boolean success) {
        return success ? ACCENT_GREEN : CHIP_ACTIVE;
    }

    public static int pill(boolean active) {
        return active
                ? mix(CHIP_ACTIVE, ACCENT_GREEN, 0.12F)
                : mix(PANEL_CHIP, CARD_OPTIONS, 0.20F);
    }

    public static int mix(int from, int to, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));

        int af = (from >>> 24) & 0xFF;
        int rf = (from >>> 16) & 0xFF;
        int gf = (from >>> 8) & 0xFF;
        int bf = from & 0xFF;

        int at = (to >>> 24) & 0xFF;
        int rt = (to >>> 16) & 0xFF;
        int gt = (to >>> 8) & 0xFF;
        int bt = to & 0xFF;

        int a = (int) (af + (at - af) * t);
        int r = (int) (rf + (rt - rf) * t);
        int g = (int) (gf + (gt - gf) * t);
        int b = (int) (bf + (bt - bf) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static float renderedTextHeight(FontRenderer font, String text) {
        return font.getStringHeight((text == null || text.isEmpty()) ? "I" : text) / 2.0F;
    }

    public static float centerTextY(FontRenderer font, String text, float boxY, float boxHeight) {
        return boxY + (boxHeight - renderedTextHeight(font, text)) / 2.0F + 3.0F;
    }

    public static float centerTextX(FontRenderer font, String text, float boxX, float boxWidth) {
        return boxX + (boxWidth - font.getStringWidth(text)) / 2.0F;
    }

    public static float centerMsdfTextX(MsdfFont font, String text, float size, float boxX, float boxWidth) {
        return boxX + (boxWidth - font.getWidth(text, size)) / 2.0F;
    }

    public static float centerMsdfTextY(float size, float boxY, float boxHeight) {
        return boxY + (boxHeight - size) / 2.0F + MSDF_CENTER_Y_OFFSET;
    }
}
