package vorga.phazeclient.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
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
import vorga.phazeclient.base.util.render.Render2DUtil;
import vorga.phazeclient.implement.menu.MainMenuScreen;
import vorga.phazeclient.implement.menu.UiMsdfIconAtlas;

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
                        .dimensions(this.width - 28, -3, 20, 20)
                        .build()
        );
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void phaze$renderSwitchButtonIcon(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (MainMenuScreen.isCustomMainMenuEnabled() || phaze$switchBackButton == null || !phaze$switchBackButton.visible) {
            return;
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        float iconHeight = 22.0F;
        float iconWidth = iconHeight * Math.max(1.0F, UiMsdfIconAtlas.resolveAspectRatio(PHAZE_MENU_SWITCH_ICON));
        float iconX = phaze$switchBackButton.getX() + (phaze$switchBackButton.getWidth() - iconWidth) / 2.0F;
        float iconY = phaze$switchBackButton.getY() + (phaze$switchBackButton.getHeight() - iconHeight) / 2.0F;
        int tint = 0xFFFFFFFF;
        if (!UiMsdfIconAtlas.renderIcon(context, PHAZE_MENU_SWITCH_ICON, iconX, iconY, iconWidth, iconHeight, tint, true)) {
            Render2DUtil.drawTexture(
                    context.getMatrices(),
                    PHAZE_MENU_SWITCH_ICON,
                    iconX,
                    iconX + iconWidth,
                    iconY,
                    iconY + iconHeight,
                    0.0F,
                    64,
                    64,
                    0.0F,
                    0.0F,
                    64,
                    64,
                    tint
            );
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
