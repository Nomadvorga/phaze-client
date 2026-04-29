package vorga.phazeclient.implement.menu.components.implement.settings;

import net.minecraft.client.gui.DrawContext;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.base.util.other.StringUtil;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.implement.other.CheckComponent;
import vorga.phazeclient.base.util.math.MathUtil;

import static vorga.phazeclient.api.system.font.Fonts.Type.INTER_BOLD;

public class CheckboxComponent extends AbstractSettingComponent {
    private final CheckComponent checkComponent = new CheckComponent();
    private final BooleanSetting setting;
    private final Animation hoverAnimation = new DecelerateAnimation().setMs(180).setValue(1);
    private final Animation activeAnimation = new DecelerateAnimation().setMs(180).setValue(1);

    public CheckboxComponent(BooleanSetting setting) {
        super(setting);
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateVisibilityAnimation();
        var labelFont = Fonts.getSize(14, INTER_BOLD);

        boolean isModified = setting.isModified();
        float textOffset = animatedTextOffset(isModified);

        boolean hovered = MathUtil.isHovered(mouseX, mouseY, x, y, width, height);
        hoverAnimation.setDirection(hovered ? Direction.FORWARDS : Direction.BACKWARDS);
        float hoverProgress = hoverAnimation.getOutput().floatValue();
        activeAnimation.setDirection(setting.isValue() ? Direction.FORWARDS : Direction.BACKWARDS);
        float activeProgress = activeAnimation.getOutput().floatValue();

        String wrapped = StringUtil.wrap(setting.getLocalizedName(), (int) (width - 30 - textOffset), 14);
        float wrappedHeight = Fonts.getSize(14).getStringHeight(wrapped);
        height = (int) (20 + Math.max(0, (wrappedHeight - 14) / 2));

        renderSettingCard(context, activeProgress, hoverProgress);

        resetIcon.position(x, y, height).alpha(currentAlpha).modified(isModified).render(context.getMatrices());

        float textX = x + 10 + textOffset;
        float textY = centeredTextY(labelFont, wrapped);
        int textColor = MenuStyle.mix(primaryText(), MenuStyle.withAlpha(0xFFFFFFFF, currentAlpha), hoverProgress * 0.16f);
        labelFont.drawString(context.getMatrices(), wrapped, textX, textY, textColor);

        ((CheckComponent) checkComponent.position(x + width - 29, y + height / 2 - 5.0F))
                .setRunnable(() -> setting.setValue(!setting.isValue()))
                .setState(setting.isValue())
                .setAlpha(currentAlpha)
                .render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && resetIcon.isHovered(mouseX, mouseY)) {
            setting.reset();
            return true;
        }

        if (checkComponent.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        if (!setting.isVisible()) {
            return false;
        }
        return MathUtil.isHovered(mouseX, mouseY, x, y, width, height);
    }
}
