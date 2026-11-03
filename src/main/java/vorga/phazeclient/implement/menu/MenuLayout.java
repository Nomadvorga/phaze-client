package vorga.phazeclient.implement.menu;

/**
 * Shared geometry tokens for the main menu. Keeping the structural spacing in
 * one place prevents a component from drifting by a few pixels just because it
 * was laid out with a different local offset.
 */
public final class MenuLayout {
    public static final float SIDEBAR_WIDTH = 100.0F;
    public static final float CONTENT_INSET = 6.0F;
    public static final float PANEL_GAP = 6.0F;
    public static final float GRID_COLUMN_GAP = 6.0F;
    public static final float GRID_ROW_GAP = 8.0F;

    private MenuLayout() {
    }
}
