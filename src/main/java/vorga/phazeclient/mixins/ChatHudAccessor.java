package vorga.phazeclient.mixins;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Accessor mixin exposing the two private message-list fields of {@link ChatHud}
 * to the {@code ChatHelper} module so it can collapse consecutive duplicates.
 */
@Mixin(ChatHud.class)
public interface ChatHudAccessor {

    @Accessor("messages")
    List<ChatHudLine> phaze$getMessages();

    @Accessor("visibleMessages")
    List<ChatHudLine.Visible> phaze$getVisibleMessages();
}
