package vorga.phazeclient.api.system.hud;

/**
 * Implemented on vanilla's ChatHud by {@code ChatHudMixin}. InGameHud calls
 * this once at the start of every displayed frame so chat animation state keeps
 * advancing even when Exordium serves ChatHud from its cache and skips the
 * vanilla render method.
 */
public interface ChatAnimationFrameAccess {
    void phaze$tickAnimationFrame();
}
