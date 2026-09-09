package vorga.phazeclient.implement.menu.components.implement.settings;

import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.feature.module.setting.implement.GroupSetting;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.base.util.other.StringUtil;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.components.implement.other.CheckComponent;
import vorga.phazeclient.implement.menu.components.implement.other.SettingComponent;
import vorga.phazeclient.implement.menu.components.implement.window.AbstractWindow;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.group.GroupWindow;
import vorga.phazeclient.base.util.render.GuiMatrix;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

import static vorga.phazeclient.api.system.font.Fonts.Type.INTER_BOLD;

public class GroupComponent extends AbstractSettingComponent {
    private final CheckComponent checkComponent = new CheckComponent();
    private final SettingComponent settingComponent = new SettingComponent();

    private final GroupSetting setting;

    public GroupComponent(GroupSetting setting) {
        super(setting);
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateVisibilityAnimation();
        var labelFont = Fonts.getSize(14, INTER_BOLD);

        boolean isModified = setting.isModified();
        float textOffset = animatedTextOffset(isModified);
        float labelMaxWidth = width - 42 - textOffset;
        String wrapped = StringUtil.wrap(setting.getLocalizedName(), (int) labelMaxWidth, 14);
        float wrappedHeight = Fonts.getSize(14).getStringHeight(wrapped);
        height = (int) (20 + Math.max(0, (wrappedHeight - 14) / 2));
        float hoverProgress = animatedCardHover(MathUtil.isHovered(mouseX, mouseY, x, y, width, height));
        float activeProgress = (isGroupWindowOpen() || (setting.isCheckbox() && setting.isValue())) ? 1.0f : 0.0f;

        resetIcon.position(x, y, height).alpha(currentAlpha).modified(isModified).render(context);
        renderSettingCard(context, activeProgress, hoverProgress);

        float textX = x + 10 + textOffset;
        // 1.21.11: the GUI pose is a Matrix3x2fStack, but FontRenderer still
        // draws through a 4x4 MatrixStack, so promote the pose once per row.
        // Nothing above mutates the GUI pose, so the bake matches 1.21.4 geometry.
        // TODO(1.21.11): drop this once FontRenderer takes a Matrix3x2fc directly.
        MatrixStack textPose = new MatrixStack();
        textPose.multiplyPositionMatrix(GuiMatrix.mat4(context.getMatrices()));
        labelFont.drawString(textPose, wrapped, textX, centeredTextY(labelFont, wrapped), primaryText());

        boolean isWindowOpen = isGroupWindowOpen();

        if (setting.isCheckbox()) {
            ((CheckComponent) checkComponent.position(x + width - 38, y + height / 2 - 5.0F))
                    .setRunnable(() -> setting.setValue(!setting.isValue()))
                    .setState(setting.isValue())
                    .render(context, mouseX, mouseY, delta);

            ((SettingComponent) settingComponent.position(x + width - 17, y + height / 2 - 5.5F))
                    .setRunnable(() -> spawnWindow(mouseX, mouseY))
                    .setWindowOpen(isWindowOpen)
                    .render(context, mouseX, mouseY, delta);
        } else {
            ((SettingComponent) settingComponent.position(x + width - 17, y + height / 2 - 5.5F))
                    .setRunnable(() -> spawnWindow(mouseX, mouseY))
                    .setWindowOpen(isWindowOpen)
                    .render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && resetIcon.isHovered(mouseX, mouseY)) {
            setting.reset();
            return true;
        }

        if (setting.isCheckbox() && checkComponent.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (settingComponent.mouseClicked(mouseX, mouseY, button)) {
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

    private void spawnWindow(int mouseX, int mouseY) {
        AbstractWindow existingWindow = findWindow(setting);

        if (existingWindow != null) {
            closeChildGroupWindows(setting);
            windowManager.delete(existingWindow);
        } else {
            int windowWidth = 137;
            int windowHeight = 200;

            int windowX = mouseX + 5;
            int windowY = mouseY + 5;

            windowX = MenuScreen.INSTANCE.clampOverlayX(windowX, windowWidth);
            windowY = MenuScreen.INSTANCE.clampOverlayY(windowY, windowHeight);

            AbstractWindow groupWindow = new GroupWindow(setting)
                    .position(windowX, windowY)
                    .size(windowWidth, 23)
                    .draggable(false);

            windowManager.add(groupWindow);
        }
    }

    private void closeChildGroupWindows(GroupSetting parentSetting) {
        java.util.List<AbstractWindow> windowsCopy = new java.util.ArrayList<>(windowManager.getWindows());

        for (Setting childSetting : parentSetting.getSubSettings()) {
            if (childSetting instanceof GroupSetting groupSetting) {
                closeChildGroupWindows(groupSetting);

                AbstractWindow childWindow = findWindow(groupSetting, windowsCopy);
                if (childWindow != null) {
                    windowManager.delete(childWindow);
                }
            }
        }
    }

    private boolean isGroupWindowOpen() {
        return findWindow(setting) != null;
    }

    private AbstractWindow findWindow(GroupSetting targetSetting) {
        return findWindow(targetSetting, windowManager.getWindows());
    }

    private AbstractWindow findWindow(GroupSetting targetSetting, java.util.List<AbstractWindow> windows) {
        for (AbstractWindow window : windows) {
            if (window instanceof GroupWindow groupWindow && groupWindow.getSetting() == targetSetting) {
                return window;
            }
        }
        return null;
    }
}
