package vorga.phazeclient.implement.menu.components.implement.settings;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import vorga.phazeclient.api.feature.module.setting.implement.ItemPickerSetting;
import vorga.phazeclient.api.system.font.FontRenderer;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.menu.ItemPickerScreen;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.implement.other.CheckComponent;
import vorga.phazeclient.implement.menu.components.implement.window.AbstractWindow;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.itempicker.ItemPickerColorWindow;

import static vorga.phazeclient.api.system.font.Fonts.Type.INTER_BOLD;

public final class ItemPickerComponent extends AbstractSettingComponent {
    private static final float ROW_HEIGHT = 24.0F;
    private static final float ICON_SIZE = 16.0F;
    private static final float EMPTY_PLUS_SIZE = 12.4F;
    private static final float LEFT_PADDING = 10.0F;
    private static final float TEXT_GAP = 7.0F;
    private static final float COLOR_SIZE = 12.0F;
    private static final float COLOR_RIGHT = 10.0F;
    private static final float TOGGLE_GAP = 9.0F;

    private final CheckComponent checkComponent = new CheckComponent();
    private final ItemPickerSetting setting;

    public ItemPickerComponent(ItemPickerSetting setting) {
        super(setting);
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateVisibilityAnimation();

        FontRenderer titleFont = Fonts.getSize(13, INTER_BOLD);
        boolean isModified = setting.isModified();
        float textOffset = animatedTextOffset(isModified);
        boolean hovered = MathUtil.isHovered(mouseX, mouseY, x, y, width, ROW_HEIGHT);
        float hoverProgress = animatedCardHover(hovered);
        float activeProgress = setting.isEnabled() ? 0.84F : 0.08F;

        height = (int) ROW_HEIGHT;
        renderSettingCard(context, activeProgress, hoverProgress);

        resetIcon.position(x, y, height).alpha(currentAlpha).modified(isModified).render(context.getMatrices());

        float colorX = x + width - COLOR_RIGHT - COLOR_SIZE;
        float colorY = y + ROW_HEIGHT / 2.0F - COLOR_SIZE / 2.0F;
        float toggleX = colorX - TOGGLE_GAP - CheckComponent.getToggleWidth();
        float toggleY = y + ROW_HEIGHT / 2.0F - CheckComponent.getToggleHeight() / 2.0F;

        float iconX = x + LEFT_PADDING + textOffset;
        float iconY = y + ROW_HEIGHT / 2.0F - ICON_SIZE / 2.0F;
        float textX = iconX + ICON_SIZE + TEXT_GAP;
        float textWidth = Math.max(12.0F, toggleX - textX - 8.0F);

        ItemStack previewStack = setting.createPreviewStack();
        if (!previewStack.isEmpty()) {
            context.drawItem(previewStack, Math.round(iconX), Math.round(iconY));
            context.draw();
        } else {
            String plus = "+";
            MsdfRenderer.renderText(
                    MsdfFonts.bold(),
                    plus,
                    EMPTY_PLUS_SIZE,
                    MenuStyle.withAlpha(0xFFFFFFFF, currentAlpha),
                    context.getMatrices().peek().getPositionMatrix(),
                    MenuStyle.centerMsdfTextX(MsdfFonts.bold(), plus, EMPTY_PLUS_SIZE, iconX, ICON_SIZE) + 3.0F,
                    MenuStyle.centerMsdfTextY(EMPTY_PLUS_SIZE, iconY, ICON_SIZE) + 0.15F,
                    0.0F
            );
        }

        String title = trimToWidth(titleFont, setting.getRowTitle(), textWidth);
        int titleColor = setting.isEnabled()
                ? MenuStyle.mix(primaryText(), MenuStyle.withAlpha(0xFFFFFFFF, currentAlpha), hoverProgress * 0.10F)
                : MenuStyle.mix(mutedText(), primaryText(), 0.18F);
        titleFont.drawString(context.getMatrices(), title, textX, centeredTextY(titleFont, title, y, ROW_HEIGHT), titleColor);

        ((CheckComponent) checkComponent.position(toggleX, toggleY))
                .setRunnable(() -> setting.setEnabled(!setting.isEnabled()))
                .setState(setting.isEnabled())
                .setAlpha(currentAlpha)
                .render(context, mouseX, mouseY, delta);

        int colorFill = MenuStyle.withAlpha(0xFF000000 | (setting.getHighlightColor() & 0x00FFFFFF), currentAlpha);
        int colorOutline = MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.settingOutline(true), 0xFFFFFFFF, hovered ? 0.12F : 0.0F), currentAlpha);
        rectangle.render(ShapeProperties.create(context.getMatrices(), colorX, colorY, COLOR_SIZE, COLOR_SIZE)
                .round(3.2F)
                .thickness(1.05F)
                .outlineColor(colorOutline)
                .color(colorFill)
                .build());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && resetIcon.isHovered(mouseX, mouseY)) {
            playButtonClickSound();
            setting.reset();
            return true;
        }

        if (checkComponent.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        float colorX = x + width - COLOR_RIGHT - COLOR_SIZE;
        float colorY = y + ROW_HEIGHT / 2.0F - COLOR_SIZE / 2.0F;
        if (button == 0 && MathUtil.isHovered(mouseX, mouseY, colorX, colorY, COLOR_SIZE, COLOR_SIZE)) {
            playButtonClickSound();
            toggleColorWindow(colorX, colorY);
            return true;
        }

        if (button == 0 && MathUtil.isHovered(mouseX, mouseY, x, y, width, ROW_HEIGHT)) {
            playButtonClickSound();
            if (setting.isCustomPicker()) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null) {
                    client.setScreen(new ItemPickerScreen(client.currentScreen, setting));
                }
            } else {
                setting.setEnabled(!setting.isEnabled());
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        if (!setting.isVisible()) {
            return false;
        }
        return MathUtil.isHovered(mouseX, mouseY, x, y, width, ROW_HEIGHT);
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

    private void toggleColorWindow(float colorX, float colorY) {
        AbstractWindow existingWindow = null;

        for (AbstractWindow window : windowManager.getWindows()) {
            if (window instanceof ItemPickerColorWindow pickerWindow && pickerWindow.getSetting() == setting) {
                existingWindow = window;
                break;
            }
        }

        if (existingWindow != null) {
            windowManager.delete(existingWindow);
            return;
        }

        for (AbstractWindow window : windowManager.getWindows()) {
            if (window instanceof ItemPickerColorWindow) {
                windowManager.delete(window);
            }
        }

        int windowWidth = Math.round(ItemPickerColorWindow.WINDOW_WIDTH);
        int windowHeight = Math.round(ItemPickerColorWindow.WINDOW_HEIGHT);
        int windowX = MenuScreen.INSTANCE.clampOverlayX(colorX + COLOR_SIZE + 8.0F, windowWidth);
        int windowY = MenuScreen.INSTANCE.clampOverlayY(y + ROW_HEIGHT / 2.0F - windowHeight / 2.0F, windowHeight);

        windowManager.add(new ItemPickerColorWindow(setting)
                .position(windowX, windowY)
                .size(ItemPickerColorWindow.WINDOW_WIDTH, ItemPickerColorWindow.WINDOW_HEIGHT)
                .draggable(false));
    }
}
