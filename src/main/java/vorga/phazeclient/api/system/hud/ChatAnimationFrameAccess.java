package vorga.phazeclient.api.system.hud;

import net.minecraft.client.gui.hud.ChatHudLine;

/**
 * Implemented on vanilla's ChatHud by {@code ChatHudMixin}. InGameHud calls
 * this once at the start of every displayed frame so chat animation state keeps
 * advancing even when Exordium serves ChatHud from its cache and skips the
 * vanilla render method.
 */
public interface ChatAnimationFrameAccess {
    void phaze$tickAnimationFrame();

    boolean phaze$shouldShiftChatLine(ChatHudLine.Visible line);

    float phaze$getChatFrameDx();

    float phaze$getChatFrameDy();

    boolean phaze$shouldDrawChatBadge(ChatHudLine.Visible line);

    boolean phaze$isCodeBadge(ChatHudLine.Visible line);
}
