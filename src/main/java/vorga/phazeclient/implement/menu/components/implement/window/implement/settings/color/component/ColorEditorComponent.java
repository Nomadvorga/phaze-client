package vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color.component;

import lombok.RequiredArgsConstructor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.api.system.font.FontRenderer;
import vorga.phazeclient.api.system.localization.LocalizationManager;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.color.ColorUtil;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.menu.components.AbstractComponent;
import org.lwjgl.glfw.GLFW;

@RequiredArgsConstructor
public class ColorEditorComponent extends AbstractComponent {
    private final ColorSetting setting;

    private boolean typing = false;
    private String hexInput = "";
    private int cursorPosition = 0;
    private int selectionStart = -1;
    private int selectionEnd = -1;
    private float xOffset = 0;
    private boolean dragging = false;
    private long lastClickTime = 0;


    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrix = context.getMatrices();

        rectangle.render(ShapeProperties.create(matrix, x + 6, y + 90.5F, 31, 14)
                .round(1.5F).thickness(2).outlineColor(ColorUtil.getOutline(0.8F)).color(ColorUtil.getRect(1)).build());

        Fonts.getSize(13).drawString(context.getMatrices(), LocalizationManager.getInstance().get("ui.hex"), x + 10, y + 96, -1);

        float hexFieldX = x + 40;
        float hexFieldY = y + 90.5F;
        float hexFieldWidth = 80;
        float hexFieldHeight = 14;

        rectangle.render(ShapeProperties.create(matrix, hexFieldX, hexFieldY, hexFieldWidth, hexFieldHeight)
                .round(1.5F).thickness(2).outlineColor(ColorUtil.getOutline(0.8F)).color(ColorUtil.getRect(1)).build());

        String currentHex = String.format("%08X", setting.getColor()).toUpperCase();

        if (!typing) {
            hexInput = currentHex;
        }

        updateXOffset(Fonts.getSize(13), cursorPosition);

        FontRenderer font = Fonts.getSize(13);
        float textStartX = hexFieldX + 5 - xOffset;

        if (typing && selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
            int start = Math.max(0, Math.min(getStartOfSelection(), hexInput.length()));
            int end = Math.max(0, Math.min(getEndOfSelection(), hexInput.length()));
            if (start < end) {
                String textBeforeSelection = "#" + hexInput.substring(0, start);
                String textUpToEndSelection = "#" + hexInput.substring(0, end);

                float selectionXStart = textStartX + font.getStringWidth(textBeforeSelection);
                float selectionXEnd = textStartX + font.getStringWidth(textUpToEndSelection);
                float selectionWidth = selectionXEnd - selectionXStart;

                rectangle.render(ShapeProperties.create(matrix, selectionXStart, hexFieldY + 3, selectionWidth, 8).color(0xFF5585E8).build());
            }
        }

        String displayText = "#" + hexInput;
        font.drawString(context.getMatrices(), displayText, textStartX, hexFieldY + 4.5F, typing ? -1 : ColorUtil.getDescription());

        long currentTime = System.currentTimeMillis();
        boolean focused = typing && (currentTime % 1000 < 500);
        if (focused && (selectionStart == -1 || selectionStart == selectionEnd)) {
            float cursorX = Fonts.getSize(13).getStringWidth("#" + hexInput.substring(0, cursorPosition));
            rectangle.render(ShapeProperties.create(matrix, hexFieldX + 5 - xOffset + cursorX, hexFieldY + 3.5F, 0.5F, 7).color(-1).build());
        }

        rectangle.render(ShapeProperties.create(matrix, x + 122, y + 90.5F, 22, 14)
                .round(1.5F).thickness(2).outlineColor(ColorUtil.getOutline(0.8F)).color(ColorUtil.getRect(1)).build());

        int displayValue = (int) (setting.getAlpha() * 100);
        Fonts.getSize(13).drawCenteredString(context.getMatrices(), displayValue + "%", x + 133, y + 96, -1);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (MathUtil.isHovered(mouseX, mouseY, x + 122, y + 90.5F, 22, 14)) {
            setting.setAlpha(MathHelper.clamp((float) (setting.getAlpha() - (amount * 2) / 100), 0, 1));
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float hexFieldX = x + 40;
        float hexFieldY = y + 90.5F;
        float hexFieldWidth = 80;
        float hexFieldHeight = 14;

        if (MathUtil.isHovered(mouseX, mouseY, hexFieldX, hexFieldY, hexFieldWidth, hexFieldHeight) && button == 0) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastClickTime < 250) {
                selectionStart = 0;
                selectionEnd = hexInput.length();
            } else {
                typing = true;
                lastClickTime = currentTime;
                cursorPosition = getCursorIndexAt(mouseX, hexFieldX);
                selectionStart = cursorPosition;
                selectionEnd = cursorPosition;
            }
            return true;
        } else {
            typing = false;
            clearSelection();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (typing) {
            dragging = true;
            float hexFieldX = x + 40;
            cursorPosition = getCursorIndexAt(mouseX, hexFieldX);
            if (selectionStart == -1) {
                selectionStart = cursorPosition;
            }
            selectionEnd = cursorPosition;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (typing && hexInput.length() < 8) {
            if ((chr >= '0' && chr <= '9') || (chr >= 'A' && chr <= 'F') || (chr >= 'a' && chr <= 'f')) {
                deleteSelectedText();
                hexInput = hexInput.substring(0, cursorPosition) + Character.toUpperCase(chr) + hexInput.substring(cursorPosition);
                cursorPosition++;
                clearSelection();
            }
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (typing) {
            if (Screen.hasControlDown()) {
                switch (keyCode) {
                    case GLFW.GLFW_KEY_A -> selectAllText();
                    case GLFW.GLFW_KEY_V -> pasteFromClipboard();
                    case GLFW.GLFW_KEY_C -> copyToClipboard();
                }
            } else {
                switch (keyCode) {
                    case GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_ENTER -> handleTextModification(keyCode);
                    case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> moveCursor(keyCode);
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void handleTextModification(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (hasSelection()) {
                replaceText(getStartOfSelection(), getEndOfSelection(), "");
            } else if (cursorPosition > 0) {
                replaceText(cursorPosition - 1, cursorPosition, "");
            }
        } else if (keyCode == GLFW.GLFW_KEY_ENTER) {
            applyHexColor();
            typing = false;
        }
    }

    private void applyHexColor() {
        try {
            if (hexInput.length() >= 6) {
                long colorValue = Long.parseLong(hexInput, 16);
                setting.setColor((int) colorValue);
            }
        } catch (NumberFormatException e) {
            hexInput = String.format("%08X", setting.getColor()).toUpperCase();
        }
    }

    private void moveCursor(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_LEFT && cursorPosition > 0) {
            cursorPosition--;
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT && cursorPosition < hexInput.length()) {
            cursorPosition++;
        }
        updateSelectionAfterCursorMove();
    }

    private void updateSelectionAfterCursorMove() {
        if (Screen.hasShiftDown()) {
            if (selectionStart == -1) selectionStart = cursorPosition;
            selectionEnd = cursorPosition;
        } else {
            clearSelection();
        }
    }

    private void pasteFromClipboard() {
        String clipboardText = GLFW.glfwGetClipboardString(window().getHandle());
        if (clipboardText != null) {
            StringBuilder filtered = new StringBuilder();
            for (char c : clipboardText.toCharArray()) {
                if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f')) {
                    filtered.append(Character.toUpperCase(c));
                }
                if (filtered.length() >= 8) break;
            }
            replaceText(cursorPosition, cursorPosition, filtered.toString());
        }
    }

    private void copyToClipboard() {
        if (hasSelection()) {
            GLFW.glfwSetClipboardString(window().getHandle(), getSelectedText());
        }
    }

    private void selectAllText() {
        selectionStart = 0;
        selectionEnd = hexInput.length();
    }

    private void replaceText(int start, int end, String replacement) {
        if (start < 0) start = 0;
        if (end > hexInput.length()) end = hexInput.length();
        if (start > end) start = end;

        int maxLength = 8 - (hexInput.length() - (end - start));
        if (replacement.length() > maxLength) {
            replacement = replacement.substring(0, maxLength);
        }

        hexInput = hexInput.substring(0, start) + replacement + hexInput.substring(end);
        cursorPosition = start + replacement.length();
        clearSelection();
    }

    private boolean hasSelection() {
        return selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd;
    }

    private String getSelectedText() {
        return hexInput.substring(getStartOfSelection(), getEndOfSelection());
    }

    private int getStartOfSelection() {
        return Math.min(selectionStart, selectionEnd);
    }

    private int getEndOfSelection() {
        return Math.max(selectionStart, selectionEnd);
    }

    private void clearSelection() {
        selectionStart = -1;
        selectionEnd = -1;
    }

    private int getCursorIndexAt(double mouseX, float hexFieldX) {
        float relativeX = (float) mouseX - hexFieldX - 5 + xOffset;
        float prefixWidth = Fonts.getSize(13).getStringWidth("#");
        relativeX -= prefixWidth;

        if (relativeX <= 0) {
            return 0;
        }

        FontRenderer font = Fonts.getSize(13);

        int low = 0;
        int high = hexInput.length();

        while (low < high) {
            int mid = (low + high + 1) / 2;
            float width = font.getStringWidth(hexInput.substring(0, mid));

            if (width <= relativeX) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }

        return low;
    }

    private void updateXOffset(FontRenderer font, int cursorPosition) {
        float cursorX = font.getStringWidth("#" + hexInput.substring(0, cursorPosition));
        float hexFieldWidth = 80;
        if (cursorX < xOffset) {
            xOffset = cursorX;
        } else if (cursorX - xOffset > hexFieldWidth - 10) {
            xOffset = cursorX - (hexFieldWidth - 10);
        }
    }

    private void deleteSelectedText() {
        if (hasSelection()) {
            replaceText(getStartOfSelection(), getEndOfSelection(), "");
        }
    }
}
