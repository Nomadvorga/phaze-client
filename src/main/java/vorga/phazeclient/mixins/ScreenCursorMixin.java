package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.implement.menu.ItemPickerScreen;
import vorga.phazeclient.implement.menu.MenuScreen;

@Mixin(Screen.class)
public abstract class ScreenCursorMixin {

    @Inject(method = "renderWithTooltip", at = @At("HEAD"))
    private void phaze$cursorBeginFrame(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if ((Object) this instanceof MenuScreen || (Object) this instanceof ItemPickerScreen
                || (Object) this instanceof ChatScreen) {
            CursorManager.beginFrame();
        }
    }

    @Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void phaze$cursorEndFrame(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if ((Object) this instanceof MenuScreen || (Object) this instanceof ItemPickerScreen
                || (Object) this instanceof ChatScreen) {
            CursorManager.endFrame(true);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void phaze$cursorOnRemoved(CallbackInfo ci) {
        if ((Object) this instanceof MenuScreen || (Object) this instanceof ItemPickerScreen
                || (Object) this instanceof ChatScreen) {
            CursorManager.forceArrow();
        }
    }
}
