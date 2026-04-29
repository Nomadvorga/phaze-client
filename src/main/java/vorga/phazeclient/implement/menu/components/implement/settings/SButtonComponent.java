package vorga.phazeclient.implement.menu.components.implement.settings;

import vorga.phazeclient.api.system.localization.LocalizationManager;
import net.minecraft.client.gui.DrawContext;
import vorga.phazeclient.api.feature.module.setting.implement.ButtonSetting;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.base.util.other.StringUtil;
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
        var labelFont = Fonts.getSize(14, INTER_BOLD);

        boolean isModified = setting.isModified();
        float textOffset = animatedTextOffset(isModified);

        String wrapped = StringUtil.wrap(setting.getLocalizedName(), (int) (width - 89 - textOffset), 14);
        float wrappedHeight = Fonts.getSize(14).getStringHeight(wrapped);
        height = (int) (20 + Math.max(0, (wrappedHeight - 14) / 2));
        float hoverProgress = animatedCardHover(MathUtil.isHovered(mouseX, mouseY, x, y, width, height));

        renderSettingCard(context, 0.0f, hoverProgress);

        resetIcon.position(x, y, height).alpha(currentAlpha).modified(isModified).render(context.getMatrices());

        float textX = x + 10 + textOffset;
        labelFont.drawString(context.getMatrices(), wrapped, textX, centeredTextY(labelFont, wrapped), primaryText());

        ButtonComponent lunarButton = (ButtonComponent) buttonComponent.setText(LocalizationManager.getInstance().get("module.setting.button_click"))
                .setRunnable(setting.getRunnable());
        float buttonWidth = lunarButton.measureWidth();

        lunarButton.position(x + width - 9 - buttonWidth, y + height / 2 - 6);
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
        if (MathUtil.isHovered(mouseX, mouseY, x + width - 9 - buttonWidth, y + height / 2 - 6, buttonWidth, buttonComponent.height)) {
            return true;
        }
        return MathUtil.isHovered(mouseX, mouseY, x + 10, y + 6, width - buttonWidth - 20, height - 12);
    }
}
