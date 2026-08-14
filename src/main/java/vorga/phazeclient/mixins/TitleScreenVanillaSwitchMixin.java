package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.menu.MainMenuScreen;

@Mixin(TitleScreen.class)
public abstract class TitleScreenVanillaSwitchMixin extends Screen {
    private static final Identifier PHAZE_MENU_SWITCH_ICON =
            Identifier.of("phaze", "textures/menu/vanilla_main_menu.png");
    @Unique
    private ButtonWidget phaze$switchBackButton;

    protected TitleScreenVanillaSwitchMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void phaze$addSwitchBackButton(CallbackInfo ci) {
        if (MainMenuScreen.isCustomMainMenuEnabled()) {
            return;
        }

        phaze$switchBackButton = this.addDrawableChild(
                ButtonWidget.builder(
                                Text.empty(),
                                button -> {
                                    MainMenuScreen.setCustomMainMenuEnabled(true);
                                    if (this.client != null) {
                                        this.client.setScreen(new MainMenuScreen());
                                    }
                                }
                        )
                        .dimensions(this.width - 28, 8, 20, 20)
                        .build()
        );
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void phaze$renderSwitchButtonIcon(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (MainMenuScreen.isCustomMainMenuEnabled() || phaze$switchBackButton == null || !phaze$switchBackButton.visible) {
            return;
        }

        int iconSize = 16;
        int iconX = phaze$switchBackButton.getX() + (phaze$switchBackButton.getWidth() - iconSize) / 2;
        int iconY = phaze$switchBackButton.getY() + (phaze$switchBackButton.getHeight() - iconSize) / 2;

        // 1.21.11 DrawContext records GUI elements for a deferred render pass.
        // The old immediate Render2DUtil draw ran after TitleScreen had already
        // submitted its GUI state and disappeared, leaving only the gray button.
        context.createNewRootLayer();
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                PHAZE_MENU_SWITCH_ICON,
                iconX,
                iconY,
                0.0F,
                0.0F,
                iconSize,
                iconSize,
                64,
                64,
                64,
                64,
                0xFFFFFFFF
        );
    }
}
