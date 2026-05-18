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

    /**
     * Sent-message history (the strings recalled by Up/Down in the
     * chat input). Exposed so the Longer Chat History toggle can
     * proactively trim this buffer back down to the new cap when the
     * user disables the feature - vanilla only trims on the next
     * {@code addToMessageHistory}, which means a user with a 1000-
     * deep history would still see all 1000 entries until they sent
     * the next message.
     */
    @Accessor("messageHistory")
    net.minecraft.util.collection.ArrayListDeque<String> phaze$getMessageHistory();
}
