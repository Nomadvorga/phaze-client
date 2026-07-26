package vorga.phazeclient.implement.menu.components.implement.window.implement.settings.group;

import lombok.Getter;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.feature.module.setting.SettingComponentAdder;
import vorga.phazeclient.api.feature.module.setting.implement.GroupSetting;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.color.ColorUtil;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.base.util.render.ScissorManager;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.menu.components.AbstractComponent;
import vorga.phazeclient.implement.menu.components.implement.settings.AbstractSettingComponent;
import vorga.phazeclient.implement.menu.components.implement.window.AbstractWindow;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Getter
public class GroupWindow extends AbstractWindow {
    private final List<AbstractSettingComponent> components = new ArrayList<>();
    private final GroupSetting setting;
    private int cachedComponentHeight = 0;

    public GroupWindow(GroupSetting setting) {
        this.setting = setting;

        new SettingComponentAdder().addSettingComponent(
                setting.getSubSettings(),
                components
        );
    }


    @Override
    public void drawWindow(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrix = context.getMatrices();

        ScissorManager scissorManager = Main.getInstance()
                .getScissorManager();

        cachedComponentHeight = calculateComponentHeight();
        height = MathHelper.clamp(cachedComponentHeight, 0, 200);

        rectangle.render(ShapeProperties.create(matrix, x, y, width, height)
                .round(4).thickness(2).softness(1).outlineColor(applyGlobalAlpha(ColorUtil.getOutline())).color(applyGlobalAlpha(ColorUtil.getGuiRectColor(1))).build());

        Fonts.getSize(15, Fonts.Type.INTER_BOLD).drawString(context.getMatrices(), setting.getLocalizedName(), x + 9, y + 10, applyGlobalAlpha(ColorUtil.getText()));

        boolean isLimitedHeight = MathHelper.clamp(height, 0, 200) == 200;
        if (isLimitedHeight) scissorManager.push(matrix.peek().getPositionMatrix(), x, y + 23, width, height - 28);

        float offset = 0;
        int totalHeight = 0;
        for (int i = components.size() - 1; i >= 0; i--) {
            AbstractSettingComponent component = components.get(i);
            Supplier<Boolean> visible = component.getSetting().getVisible();

            if (visible != null && !visible.get()) {
                continue;
            }

            component.x = x;
            component.y = (float) (y + 19 + offset + (height - 25 - component.height) + smoothedScroll);
            component.width = width;
            component.globalAlpha = this.globalAlpha;

            component.render(context, mouseX, mouseY, delta);

            offset -= component.height;
            totalHeight += (int) component.height;
        }

        if (isLimitedHeight) scissorManager.pop();

        int maxScroll = (int) Math.max(0, totalHeight - (height - 23));
        scroll = MathHelper.clamp(scroll, -maxScroll, 0);
        smoothedScroll = MathHelper.lerp(0.1F, smoothedScroll, scroll);
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (MathUtil.isHovered(mouseX, mouseY, x, y, width, 19) && button == 1) {
            closeChildGroupWindows(setting);
            windowManager.delete(this);
            return true;
        }

        draggable(MathUtil.isHovered(mouseX, mouseY, x, y, width, 19) && button == 0);

        boolean isAnyComponentHovered = components
                .stream()
                .anyMatch(abstractComponent -> abstractComponent.isHover(mouseX, mouseY));

        if (isAnyComponentHovered) {
            closeChildGroupWindows(setting);

            for (AbstractSettingComponent component : components) {
                if (component.isHover(mouseX, mouseY)) {
                    if (component.mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        components.forEach(abstractComponent -> abstractComponent.mouseClicked(mouseX, mouseY, button));
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void closeChildGroupWindows(GroupSetting parentSetting) {
        List<AbstractWindow> windowsCopy = new ArrayList<>(windowManager.getWindows());

        for (Setting childSetting : parentSetting.getSubSettings()) {
            if (childSetting instanceof GroupSetting groupSetting) {
                closeChildGroupWindows(groupSetting);

                for (AbstractWindow window : windowsCopy) {
                    if (window instanceof GroupWindow groupWindow) {
                        if (groupWindow.getSetting() == groupSetting) {
                            windowManager.delete(window);
                            break;
                        }
                    }
                }
            }
        }
    }


    @Override
    public boolean isHover(double mouseX, double mouseY) {
        components.forEach(abstractComponent -> abstractComponent.isHover(mouseX, mouseY));

        for (AbstractComponent abstractComponent : components) {
            if (abstractComponent.isHover(mouseX, mouseY)) {
                return true;
            }
        }
        return super.isHover(mouseX, mouseY);
    }

    @Override
    public boolean isHovered(double mouseX, double mouseY) {
        for (AbstractComponent abstractComponent : components) {
            if (abstractComponent.isHover(mouseX, mouseY)) {
                return true;
            }
        }
        return super.isHovered(mouseX, mouseY);
    }


    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        components.forEach(abstractComponent -> abstractComponent.mouseReleased(mouseX, mouseY, button));
        return super.mouseReleased(mouseX, mouseY, button);
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        boolean scrolled = MathHelper.clamp(height, 0, 200) == 200 && MathUtil.isHovered(mouseX, mouseY, x, y, width, height);
        if (scrolled) {
            scroll += amount * 20;
        }
        components.forEach(abstractComponent -> abstractComponent.mouseScrolled(mouseX, mouseY, amount));
        return scrolled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        components.forEach(abstractComponent -> abstractComponent.keyPressed(keyCode, scanCode, modifiers));
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        components.forEach(abstractComponent -> abstractComponent.charTyped(chr, modifiers));
        return super.charTyped(chr, modifiers);
    }

    private int calculateComponentHeight() {
        float offsetY = 0;
        for (AbstractSettingComponent component : components) {
            Supplier<Boolean> visible = component.getSetting().getVisible();

            if (visible != null && !visible.get()) {
                continue;
            }

            offsetY += component.height;
        }
        return (int) (offsetY + 25);
    }

    public int getComponentHeight() {
        return cachedComponentHeight;
    }
}