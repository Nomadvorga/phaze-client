package vorga.phazeclient.api.system.hud;

/** Draws a Phaze user/developer badge through ChatHud's GUI backend. */
public interface ChatHudBadgeRenderAccess {
    void phaze$drawBadge(int y, float opacity, boolean codeBadge);
}
