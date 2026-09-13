package vorga.phazeclient.implement.menu.components.implement.settings;

import vorga.phazeclient.base.util.render.GuiMatrix;

import org.joml.Matrix3x2fStack;
import org.joml.Matrix3x2fc;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.system.font.FontRenderer;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.implement.window.AbstractWindow;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color.ColorWindow;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color.SettingColorPickerWindow;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color.component.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static vorga.phazeclient.api.system.font.Fonts.Type.INTER_BOLD;

public class ColorComponent extends AbstractSettingComponent {
    private static final float ROW_HEIGHT = 24.0F;
    private static final float LEFT_PADDING = 10.0F;
    private static final float COLOR_SIZE = 12.0F;
    private static final float COLOR_RIGHT = 10.0F;

    private static final float HEADER_TEXT_SIZE = 7.0F;

    private static final float HEADER_TOP_PAD = 3.0F;

    private final ColorSetting setting;
    private final List<vorga.phazeclient.implement.menu.components.AbstractComponent> components = new ArrayList<>();

    private final HueComponent hueComponent;
    private final SaturationComponent saturationComponent;
    private final AlphaComponent alphaComponent;
    private final ColorEditorComponent colorEditorComponent;
    private final ColorPresetComponent colorPresetComponent;

    public ColorComponent(ColorSetting setting) {
        super(setting);
        this.setting = setting;

        components.addAll(
                Arrays.asList(
                        hueComponent = new HueComponent(setting),
                        saturationComponent = new SaturationComponent(setting),
                        alphaComponent = new AlphaComponent(setting),
                        colorEditorComponent = new ColorEditorComponent(setting),
                        colorPresetComponent = new ColorPresetComponent(setting)
                )
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateVisibilityAnimation();

        if (!setting.isVisible()) {
            return;
        }
        if (setting.isPopupRow()) {
            renderPopupRow(context, mouseX, mouseY);
            return;
        }

        Matrix3x2fStack matrix = context.getMatrices();

        ((ColorPresetComponent) colorPresetComponent.position(x, y)).getWindowHeight();

        alphaComponent.position(x + 5, y + 5);
        hueComponent.position(x + 5, y + 5);
        saturationComponent.position(x + 5, y + 5);
        colorEditorComponent.position(x + 5, y + 5);

        components.forEach(component -> {

            if (setting.isNoAlpha() && component == alphaComponent) {
                return;
            }
            component.globalAlpha = currentAlpha;
            component.render(context, mouseX, mouseY, delta);
        });

        renderHeader(matrix);

        height = 73;
    }

    private void renderPopupRow(DrawContext context, int mouseX, int mouseY) {

        Matrix3x2fStack matrix = context.getMatrices();

        MatrixStack textPose = new MatrixStack();
        textPose.multiplyPositionMatrix(GuiMatrix.mat4(matrix));

        FontRenderer titleFont = Fonts.getSize(13, INTER_BOLD);
        boolean isModified = setting.isModified();
        float textOffset = animatedTextOffset(isModified);
        boolean hovered = MathUtil.isHovered(mouseX, mouseY, x, y, width, ROW_HEIGHT);
        if (hovered) {
            vorga.phazeclient.api.system.cursor.CursorManager.requestHand();
        }
        float hoverProgress = animatedCardHover(hovered);

        height = (int) ROW_HEIGHT;
        renderSettingCard(context, 0.0F, hoverProgress);
        resetIcon.position(x, y, height).alpha(currentAlpha).modified(isModified).render(context);

        float colorX = x + width - COLOR_RIGHT - COLOR_SIZE;
        float colorY = y + ROW_HEIGHT / 2.0F - COLOR_SIZE / 2.0F;
        float textX = x + LEFT_PADDING + textOffset;
        float textWidth = Math.max(12.0F, colorX - textX - 8.0F);

        String title = trimToWidth(titleFont, setting.getName(), textWidth);
        titleFont.drawString(textPose, title, textX, centeredTextY(titleFont, title, y, ROW_HEIGHT), primaryText());

        int colorFill = MenuStyle.withAlpha(0xFF000000 | (setting.getColor() & 0x00FFFFFF), currentAlpha);
        int colorOutline = MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.settingOutline(false), 0xFFFFFFFF, hovered ? 0.12F : 0.0F), currentAlpha);
        rectangle.render(ShapeProperties.create(matrix, colorX, colorY, COLOR_SIZE, COLOR_SIZE)
                .round(3.2F)
                .thickness(1.05F)
                .outlineColor(colorOutline)
                .color(colorFill)
                .build());
    }

    private void renderHeader(Matrix3x2fc matrix) {
        String settingName = setting.getLocalizedName();
        if (settingName == null) settingName = "";

        String moduleName = resolveModuleVisibleName();

        String header;
        if (!moduleName.isEmpty() && !settingName.isEmpty()) {
            header = moduleName + " " + settingName;
        } else if (!moduleName.isEmpty()) {
            header = moduleName;
        } else {
            header = settingName;
        }
        if (header.isEmpty()) {
            return;
        }

        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                header,
                HEADER_TEXT_SIZE,
                MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, currentAlpha),
                GuiMatrix.mat4(matrix),
                x + 10,
                y + HEADER_TOP_PAD,
                0.0F
        );
    }

    private String resolveModuleVisibleName() {
        String moduleId = setting.getModuleContext();
        if (moduleId == null || moduleId.isEmpty()) {
            return "";
        }
        Main main = Main.getInstance();
        if (main == null || main.getModuleProvider() == null) {
            return "";
        }
        Module module = main.getModuleProvider().get(moduleId);
        if (module == null) {
            return "";
        }
        String visible = module.getVisibleName();
        return visible == null ? "" : visible;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) {
            return false;
        }
        if (setting.isPopupRow()) {
            return handlePopupRowClick(mouseX, mouseY, button);
        }

        components.forEach(component -> {
            if (setting.isNoAlpha() && component == alphaComponent) return;
            component.mouseClicked(mouseX, mouseY, button);
        });
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (setting.isPopupRow()) {
            return super.mouseScrolled(mouseX, mouseY, amount);
        }
        components.forEach(component -> {
            if (setting.isNoAlpha() && component == alphaComponent) return;
            component.mouseScrolled(mouseX, mouseY, amount);
        });
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (setting.isPopupRow()) {
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        components.forEach(component -> {
            if (setting.isNoAlpha() && component == alphaComponent) return;
            component.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        });
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (setting.isPopupRow()) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        components.forEach(component -> {
            if (setting.isNoAlpha() && component == alphaComponent) return;
            component.mouseReleased(mouseX, mouseY, button);
        });
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        if (!setting.isVisible()) {
            return false;
        }
        if (setting.isPopupRow()) {
            return MathUtil.isHovered(mouseX, mouseY, x, y, width, ROW_HEIGHT);
        }
        return MathUtil.isHovered(mouseX, mouseY, x, y, width, height);
    }

    private boolean handlePopupRowClick(double mouseX, double mouseY, int button) {
        if (button == 0 && resetIcon.isHovered(mouseX, mouseY)) {
            playButtonClickSound();
            setting.reset();
            return true;
        }

        if (button == 0 && MathUtil.isHovered(mouseX, mouseY, x, y, width, ROW_HEIGHT)) {
            playButtonClickSound();
            toggleColorWindow();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void toggleColorWindow() {
        AbstractWindow existingWindow = null;
        for (AbstractWindow window : windowManager.getWindows()) {
            if (window instanceof SettingColorPickerWindow pickerWindow && pickerWindow.getSetting() == setting) {
                existingWindow = window;
                break;
            }
        }

        if (existingWindow != null) {
            windowManager.delete(existingWindow);
            return;
        }

        for (AbstractWindow window : windowManager.getWindows()) {
            if (window instanceof SettingColorPickerWindow) {
                windowManager.delete(window);
            }
        }

        int windowWidth = Math.round(SettingColorPickerWindow.WINDOW_WIDTH);
        int windowHeight = Math.round(SettingColorPickerWindow.getWindowHeight(setting));
        float colorX = x + width - COLOR_RIGHT - COLOR_SIZE;
        int windowX = MenuScreen.INSTANCE.clampOverlayX(colorX + COLOR_SIZE + 8.0F, windowWidth);
        int windowY = MenuScreen.INSTANCE.clampOverlayY(y + ROW_HEIGHT / 2.0F - windowHeight / 2.0F, windowHeight);

        windowManager.add(new SettingColorPickerWindow(setting)
                .position(windowX, windowY)
                .size(windowWidth, windowHeight)
                .draggable(false));
    }

    private static String trimToWidth(FontRenderer font, String text, float maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (font.getStringWidth(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        float ellipsisWidth = font.getStringWidth(ellipsis);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (font.getStringWidth(builder.toString() + ch) + ellipsisWidth > maxWidth) {
                break;
            }
            builder.append(ch);
        }
        return builder.isEmpty() ? ellipsis : builder + ellipsis;
    }
}
