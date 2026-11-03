package vorga.phazeclient.api.system.hud;

import net.minecraft.client.gui.DrawContext;

public interface BossBarHudAccess {
    void phaze$updateLayout();

    void phaze$renderPreview(DrawContext context);
}
