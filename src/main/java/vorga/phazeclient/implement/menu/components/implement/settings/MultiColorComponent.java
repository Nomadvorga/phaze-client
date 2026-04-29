package vorga.phazeclient.implement.menu.components.implement.settings;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import vorga.phazeclient.api.feature.module.setting.implement.MultiColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.base.util.other.StringUtil;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.implement.window.AbstractWindow;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color.ColorWindow;

import static vorga.phazeclient.api.system.font.Fonts.Type.*;

public class MultiColorComponent extends AbstractSettingComponent {
    private final MultiColorSetting setting;

    public MultiColorComponent(MultiColorSetting setting) {
        super(setting);
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateVisibilityAnimation();
        var labelFont = Fonts.getSize(14, INTER_BOLD);

        boolean isModified = setting.isModified();
        float textOffset = animatedTextOffset(isModified);

        if (!setting.isVisible()) {
            return;
        }

        MatrixStack matrix = context.getMatrices();

        int colorCount = setting.getColorCount();
        String wrapped = StringUtil.wrap(setting.getLocalizedName(), (int) (width - colorCount * 9 - 18 - textOffset), 14);
        float wrappedHeight = Fonts.getSize(14).getStringHeight(wrapped);
        height = (int) (20 + Math.max(0, (wrappedHeight - 14) / 2));
        float hoverProgress = animatedCardHover(MathUtil.isHovered(mouseX, mouseY, x, y, width, height));

        resetIcon.position(x, y, height).alpha(currentAlpha).modified(isModified).render(matrix);
        renderSettingCard(context, 0.0f, hoverProgress);

        float textX = x + 10 + textOffset;
        labelFont.drawString(matrix, wrapped, textX, centeredTextY(labelFont, wrapped), primaryText());

        for (int i = 0; i < colorCount; i++) {
            ColorSetting colorSetting = setting.getColor(i);
            if (colorSetting != null) {
                float colorX = x + width - 15.5f - ((colorCount - 1 - i) * 9);
                float colorY = y + height / 2 - 3.5f;

                rectangle.render(ShapeProperties.create(matrix, colorX, colorY, 7, 7)
                        .round(3.5F).color(colorSetting.getColor()).build());

                rectangle.render(ShapeProperties.create(matrix, colorX, colorY, 7, 7)
                        .round(3.5F).thickness(1.2F).softness(1).outlineColor(MenuStyle.BORDER_LIGHT).color(0).build());
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) {
            return false;
        }

        if (button == 0 && resetIcon.isHovered(mouseX, mouseY)) {
            playButtonClickSound();
            setting.reset();
            return true;
        }

        int colorCount = setting.getColorCount();

        for (int i = 0; i < colorCount; i++) {
            float colorX = x + width - 15.5f - ((colorCount - 1 - i) * 9);
            float colorY = y + height / 2 - 3.5f;

            if (MathUtil.isHovered(mouseX, mouseY, colorX, colorY, 7, 7)) {
                if (button == 0) {
                    playButtonClickSound();
                    setting.setSelectedColorIndex(i);
                    ColorSetting selectedColor = setting.getColor(i);

                    if (selectedColor != null) {
                        AbstractWindow existingWindow = null;

                        for (AbstractWindow window : windowManager.getWindows()) {
                            if (window instanceof ColorWindow) {
                                existingWindow = window;
                                break;
                            }
                        }

                        if (existingWindow != null) {
                            windowManager.delete(existingWindow);
                        } else {

                            int windowWidth = 150;
                            int windowHeight = 165;

                            int windowX = (int) (mouseX + 10);
                            int windowY = (int) (mouseY + 10);

                            windowX = MenuScreen.INSTANCE.clampOverlayX(windowX, windowWidth);
                            windowY = MenuScreen.INSTANCE.clampOverlayY(windowY, windowHeight);

                            AbstractWindow colorWindow = new ColorWindow(selectedColor)
                                    .position(windowX, windowY)
                                    .size(windowWidth, windowHeight)
                                    .draggable(true);

                            windowManager.add(colorWindow);
                        }
                    }
                    return true;
                } else if (button == 1) {
                    playButtonClickSound();
                    setting.setSelectedColorIndex(i);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        if (!setting.isVisible()) {
            return false;
        }

        int colorCount = setting.getColorCount();
        for (int i = 0; i < colorCount; i++) {
            float colorX = x + width - 15.5f - ((colorCount - 1 - i) * 9);
            float colorY = y + height / 2 - 3.5f;
            if (MathUtil.isHovered(mouseX, mouseY, colorX, colorY, 7, 7)) {
                return true;
            }
        }
        if (MathUtil.isHovered(mouseX, mouseY, x + 9, y + 6, width - colorCount * 9 - 18, height - 12)) {
            return true;
        }
        return false;
    }
}
