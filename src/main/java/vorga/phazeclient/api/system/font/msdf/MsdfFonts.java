package vorga.phazeclient.api.system.font.msdf;

public final class MsdfFonts {
    private static MsdfFont medium;
    private static MsdfFont bold;
    private static MsdfFont hud;

    private MsdfFonts() {
    }

    public static MsdfFont medium() {
        if (medium == null) {
            medium = MsdfFont.builder().atlas("medium").data("medium").build();
        }
        return medium;
    }

    public static MsdfFont bold() {
        if (bold == null) {
            bold = MsdfFont.builder().atlas("bold").data("bold").build();
        }
        return bold;
    }

    public static MsdfFont hud() {
        if (hud == null) {
            hud = MsdfFont.builder().atlas("minecraft-ascii-ttf-msdf").data("minecraft-ascii-ttf-msdf").build();
        }
        return hud;
    }
}
