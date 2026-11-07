package vorga.phazeclient.api.system.hud;

/** Published with the pixels, never updated by mouse motion. Coordinates are framebuffer pixels, top-left origin. */
public record HudCachedTransform(float x, float y, float scale, int left, int top, int right, int bottom) {
    public static HudCachedTransform capture(float x, float y, float width, float height, float scale,
                                              float padding, int framebufferWidth, int framebufferHeight) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(scale) || scale <= 0
                || !Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0
                || !Float.isFinite(padding) || padding < 0 || framebufferWidth <= 0 || framebufferHeight <= 0) return null;
        int left = Math.max(0, (int) Math.floor(x - padding));
        int top = Math.max(0, (int) Math.floor(y - padding));
        int right = Math.min(framebufferWidth, (int) Math.ceil(x + width + padding));
        int bottom = Math.min(framebufferHeight, (int) Math.ceil(y + height + padding));
        return right <= left || bottom <= top ? null : new HudCachedTransform(x, y, scale, left, top, right, bottom);
    }

    public float mapX(float sourceX, float liveX, float liveScale) { return liveX + (sourceX - x) * liveScale / scale; }
    public float mapY(float sourceY, float liveY, float liveScale) { return liveY + (sourceY - y) * liveScale / scale; }
}
