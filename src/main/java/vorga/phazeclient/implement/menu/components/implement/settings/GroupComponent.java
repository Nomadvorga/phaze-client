package vorga.phazeclient.implement.menu.components.implement.settings;

import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.feature.module.setting.implement.GroupSetting;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.base.util.other.StringUtil;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.components.implement.other.ButtonComponent;
import vorga.phazeclient.implement.menu.components.implement.other.CheckComponent;
import vorga.phazeclient.implement.menu.components.implement.other.SettingComponent;
import vorga.phazeclient.implement.menu.components.implement.window.AbstractWindow;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.group.GroupWindow;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.group.WorldColorGroupWindow;
import vorga.phazeclient.base.util.render.GuiMatrix;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

import static vorga.phazeclient.api.system.font.Fonts.Type.INTER_BOLD;

public class GroupComponent extends AbstractSettingComponent {
    private final CheckComponent checkComponent = new CheckComponent();
    private final SettingComponent settingComponent = new SettingComponent();
    private final ButtonComponent buttonComponent = new ButtonComponent();

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
        boolean worldColorStyle = setting.isColorPickerStyleWindow();
        float textOffset = worldColorStyle ? 0.0F : animatedTextOffset(isModified);

        ButtonComponent openButton = null;
        float openButtonWidth = 0.0F;
        if (worldColorStyle) {
            openButton = (ButtonComponent) buttonComponent
                    .setText(Lang.translate("Open"))
                    .setRunnable(() -> spawnWindow((int) x, (int) y));
            openButton.globalAlpha = currentAlpha;
            openButtonWidth = openButton.measureWidth();
        }

        float labelMaxWidth = worldColorStyle
                ? Math.max(52.0F, width - openButtonWidth - 30.0F)
                : width - 42 - textOffset;
        String wrapped = StringUtil.wrap(setting.getLocalizedName(), (int) labelMaxWidth, 14);
        float wrappedHeight = Fonts.getSize(14).getStringHeight(wrapped);
        height = (int) (20 + Math.max(0, (wrappedHeight - 14) / 2));
        float hoverProgress = animatedCardHover(MathUtil.isHovered(mouseX, mouseY, x, y, width, height));
        float activeProgress = (isGroupWindowOpen() || (setting.isCheckbox() && setting.isValue())) ? 1.0f : 0.0f;

        if (!worldColorStyle) {
            resetIcon.position(x, y, height).alpha(currentAlpha).modified(isModified).render(context.getMatrices());
        }
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

        if (worldColorStyle) {
            openButton.position(x + width - 9 - openButtonWidth, y + height / 2.0F - 7.0F);
            openButton.render(context, mouseX, mouseY, delta);
            return;
        }

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
        if (!setting.isColorPickerStyleWindow() && button == 0 && resetIcon.isHovered(mouseX, mouseY)) {
            setting.reset();
            return true;
        }

        if (setting.isColorPickerStyleWindow() && buttonComponent.mouseClicked(mouseX, mouseY, button)) {
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
            int windowWidth = Math.round(setting.isColorPickerStyleWindow() ? setting.getPopupWidth() : 137.0F);
            int windowHeight = setting.isColorPickerStyleWindow()
                    ? estimateWorldColorWindowHeight()
                    : 200;

            int windowX = mouseX + 5;
            int windowY = mouseY + 5;

            windowX = MenuScreen.INSTANCE.clampOverlayX(windowX, windowWidth);
            windowY = MenuScreen.INSTANCE.clampOverlayY(windowY, windowHeight);

            AbstractWindow groupWindow = setting.isColorPickerStyleWindow()
                    ? new WorldColorGroupWindow(setting)
                    .position(windowX, windowY)
                    .size(windowWidth, windowHeight)
                    .draggable(false)
                    : new GroupWindow(setting)
                    .position(windowX, windowY)
                    .size(windowWidth, 23)
                    .draggable(false);

            windowManager.add(groupWindow);
        }
    }

    private int estimateWorldColorWindowHeight() {
        int visibleSettings = 0;
        for (Setting subSetting : setting.getSubSettings()) {
            java.util.function.Supplier<Boolean> visible = subSetting.getVisible();
            if (visible != null && !visible.get()) {
                continue;
            }
            visibleSettings++;
        }

        int estimated = 30 + visibleSettings * 26 + 4;
        return Math.min(Math.round(setting.getPopupMaxHeight()), Math.max(52, estimated));
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
            if (window instanceof WorldColorGroupWindow worldColorGroupWindow && worldColorGroupWindow.getSetting() == targetSetting) {
                return window;
            }
        }
        return null;
    }
}
