package vorga.phazeclient.implement.menu.components.implement.settings;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.setting.implement.BindSetting;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.base.util.other.StringUtil;
import vorga.phazeclient.implement.menu.MenuStyle;

import static vorga.phazeclient.api.system.font.Fonts.Type.INTER_BOLD;

public class BindComponent extends AbstractSettingComponent {
    private final BindSetting setting;
    private boolean binding;

    public BindComponent(BindSetting setting) {
        super(setting);
        this.setting = setting;
    }


    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateVisibilityAnimation();
        var bindFont = Fonts.getSize(13, INTER_BOLD);
        var labelFont = Fonts.getSize(14, INTER_BOLD);

        boolean isModified = setting.isModified();
        float textOffset = animatedTextOffset(isModified);

        MatrixStack matrix = context.getMatrices();

        String bindName = StringUtil.getBindName(setting.getKey());
        String name = binding ? "(" + bindName + ") ..." : bindName;
        float stringWidth = bindFont.getStringWidth(name) - 2;

        String wrapped = StringUtil.wrap(setting.getLocalizedName(), (int) (width - 74 - textOffset), 14);
        float wrappedHeight = Fonts.getSize(14).getStringHeight(wrapped);
        height = (int) (20 + Math.max(0, (wrappedHeight - 14) / 2));
        float hoverProgress = animatedCardHover(MathUtil.isHovered(mouseX, mouseY, x, y, width, height));

        renderSettingCard(context, binding ? 1.0f : 0.0f, hoverProgress);

        rectangle.render(ShapeProperties.create(matrix, x + width - stringWidth - 17, y + height / 2 - 5.75f, stringWidth + 10, 11.5f)
                .round(2).thickness(1.1F)
                .outlineColor(MenuStyle.withAlpha(MenuStyle.settingOutline(binding), currentAlpha))
                .color(MenuStyle.withAlpha(MenuStyle.settingSurface(binding), currentAlpha))
                .build());

        float badgeY = y + height / 2 - 5.75f;
        bindFont.drawString(matrix, name, x + width - 12 - stringWidth - 1, centeredTextY(bindFont, name, badgeY, 11.5F), mutedText());

        resetIcon.position(x, y, height).alpha(currentAlpha).modified(isModified).render(matrix);

        float textX = x + 10 + textOffset;
        labelFont.drawString(matrix, wrapped, textX, centeredTextY(labelFont, wrapped), primaryText());
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && resetIcon.isHovered(mouseX, mouseY)) {
            playButtonClickSound();
            setting.reset();
            return true;
        }

        if (button == 0 && MathUtil.isHovered(mouseX, mouseY, x, y, width, height)) {
            playButtonClickSound();
            binding = !binding;
            return true;
        }

        if (binding && button > 1) {
            setting.setKey(button);
            binding = false;
            return true;
        }

        if (binding && button == 0) {
            binding = false;
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
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (binding) {
            int key = keyCode == GLFW.GLFW_KEY_DELETE ? -1 : keyCode;
            setting.setKey(key);
            binding = false;
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
