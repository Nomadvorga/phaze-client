package vorga.phazeclient.implement.menu.components.implement.settings;

import vorga.phazeclient.api.system.localization.LocalizationManager;
import net.minecraft.client.gui.DrawContext;
import vorga.phazeclient.api.feature.module.setting.implement.ButtonSetting;
import vorga.phazeclient.implement.menu.font.GuiFonts;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.implement.menu.components.implement.other.ButtonComponent;
import vorga.phazeclient.base.util.math.MathUtil;

import static vorga.phazeclient.api.system.font.Fonts.Type.INTER_BOLD;

public class SButtonComponent extends AbstractSettingComponent {
    private final ButtonComponent buttonComponent = new ButtonComponent();
    private final ButtonSetting setting;

    public SButtonComponent(ButtonSetting setting) {
        super(setting);
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateVisibilityAnimation();
        var labelFont = GuiFonts.getSize(14, INTER_BOLD);

        boolean isModified = setting.isModified();
        float textOffset = animatedTextOffset(isModified);

        height = ROW_HEIGHT;
        float hoverProgress = animatedCardHover(MathUtil.isHovered(mouseX, mouseY, x, y, width, height));

        renderSettingCard(context, 0.0f, hoverProgress);

        resetIcon.position(x, y, height).alpha(currentAlpha).modified(isModified).render(context.getMatrices());

        String buttonText = setting.getButtonName();
        if (buttonText == null || buttonText.isBlank()) {
            buttonText = LocalizationManager.getInstance().get("module.setting.button_click");
        } else {
            buttonText = Lang.translate(buttonText);
        }

        ButtonComponent lunarButton = (ButtonComponent) buttonComponent.setText(buttonText)
                .setRunnable(setting.getRunnable());
        float buttonWidth = lunarButton.measureWidth();

        String text = label.singleLine(setting.getLocalizedName());
        label.render(context.getMatrices(), labelFont, text, x + 10 + textOffset, centeredTextY(labelFont, text),
                x + width - 17 - buttonWidth, y, height, primaryText());
        updateLabelHover(mouseX, mouseY);

        lunarButton.position(x + width - 9 - buttonWidth, y + height / 2.0F - 7.0F);
        lunarButton.globalAlpha = currentAlpha;
        lunarButton.render(context, mouseX, mouseY, delta);
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && resetIcon.isHovered(mouseX, mouseY)) {
            playButtonClickSound();
            setting.reset();
            return true;
        }

        if (buttonComponent.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        if (!setting.isVisible()) {
            return false;
        }
        float buttonWidth = buttonComponent.measureWidth();
        if (MathUtil.isHovered(mouseX, mouseY, x + width - 9 - buttonWidth, y + height / 2.0F - 7.0F, buttonWidth, buttonComponent.height)) {
            return true;
        }
        return MathUtil.isHovered(mouseX, mouseY, x + 10, y + 6, width - buttonWidth - 20, height - 12);
    }
}
