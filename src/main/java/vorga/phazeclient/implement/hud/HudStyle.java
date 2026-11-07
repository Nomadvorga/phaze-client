package vorga.phazeclient.implement.hud;

/** Shared HUD widget dimensions, colours and Z layers. */
final class HudStyle {
    private HudStyle() {}

    static final float BASE_WIDTH = 60.0f;
    static final float BASE_HEIGHT = 20.0f;
    static final int HUD_TEXT_COLOR = 0xFFFFFFFF;
    static final float HUD_TEXT_SIZE = 8.0f;
    static final float HUD_TEXT_RENDER_Z = 1000.0f;
    static final int HANDLE_COLOR = 0xFF72F7D4;
    static final float BASE_HOVER_OUTLINE_THICKNESS = 1.0f;
    static final float HUD_RENDER_Z = 400.0f;
    static final float HANDLE_RENDER_Z = 450.0f;
}
