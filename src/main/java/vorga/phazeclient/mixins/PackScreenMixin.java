package vorga.phazeclient.mixins;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.PackOrganizer;

@Mixin(PackScreen.class)
public abstract class PackScreenMixin extends Screen {
    protected PackScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void phaze$addPackSearchBar(CallbackInfo ci) {
        PackOrganizer module = PackOrganizer.getInstance();
        if (module != null && module.isEnabled() && module.searchBar.isValue()) {
            int w = 180;
            int h = 18;
            int x = this.width / 2 - w / 2;
            int y = 14;
            TextFieldWidget searchField = new TextFieldWidget(this.textRenderer, x, y, w, h, Text.literal("Search"));
            searchField.setPlaceholder(Text.literal("Search packs..."));
            this.addDrawableChild(searchField);
        }
    }
}
