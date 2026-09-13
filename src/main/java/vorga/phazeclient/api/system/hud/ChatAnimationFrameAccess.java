package vorga.phazeclient.api.system.hud;

import net.minecraft.client.gui.hud.ChatHudLine;

public interface ChatAnimationFrameAccess {
    void phaze$tickAnimationFrame();

    boolean phaze$shouldShiftChatLine(ChatHudLine.Visible line);

    float phaze$getChatFrameDx();

    float phaze$getChatFrameDy();

    boolean phaze$shouldDrawChatBadge(ChatHudLine.Visible line);

    boolean phaze$isCodeBadge(ChatHudLine.Visible line);
}
