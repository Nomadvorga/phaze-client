package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.PackOrganizer;
import vorga.phazeclient.implement.features.modules.other.PackPreviewRenderer;

@Mixin(PackListWidget.ResourcePackEntry.class)
public abstract class ResourcePackEntryPreviewMixin {
    // Vanilla draws the icon and its controls first, then the name/description.
    // Replace only that final text section, preserving all pack actions.
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I"),
            cancellable = true, require = 1)
    private void phaze$renderPackPreview(DrawContext context, int index, int y, int x, int entryWidth,
                                         int entryHeight, int mouseX, int mouseY, boolean hovered,
                                         float tickDelta, CallbackInfo ci) {
        PackOrganizer module = PackOrganizer.getInstance();
        if (!hovered || module == null || !module.isEnabled() || !module.packPreviews.isValue()) {
            return;
        }

        ResourcePackOrganizer.Pack pack = ((ResourcePackEntryAccessor) (Object) this).phaze$getPack();
        if (pack != null && PackPreviewRenderer.render(context, pack.getName(), x + 34, y,
                entryWidth - 44, Math.min(32, entryHeight))) {
            ci.cancel();
        }
    }
}
