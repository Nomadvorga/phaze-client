package vorga.phazeclient.mixins;

import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.screen.pack.PackListWidget$ResourcePackEntry")
public interface ResourcePackEntryAccessor {
    @Accessor("pack")
    ResourcePackOrganizer.Pack phaze$getPack();
}
