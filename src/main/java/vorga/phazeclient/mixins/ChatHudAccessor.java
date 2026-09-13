package vorga.phazeclient.mixins;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ChatHud.class)
public interface ChatHudAccessor {

    @Accessor("messages")
    List<ChatHudLine> phaze$getMessages();

    @Accessor("visibleMessages")
    List<ChatHudLine.Visible> phaze$getVisibleMessages();

    @Accessor("messageHistory")
    net.minecraft.util.collection.ArrayListDeque<String> phaze$getMessageHistory();
}
