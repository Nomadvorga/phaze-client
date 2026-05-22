package vorga.phazeclient.implement.menu.components.implement.settings;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.setting.implement.TextSetting;
import vorga.phazeclient.api.system.font.FontRenderer;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.base.util.other.StringUtil;
import vorga.phazeclient.implement.menu.MenuStyle;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class TextComponent extends AbstractSettingComponent {
    /** Globally-incrementing activation counter. Each click on a
     *  TextComponent bumps {@link #ACTIVE_TEXT_COMPONENT_ID} and
     *  records the new value into {@link #myActivationId}. The
     *  render path then drops focus when our id no longer matches
     *  the latest, which gives the "only one input typing at a time"
     *  contract without an explicit sibling list. */
    static int ACTIVE_TEXT_COMPONENT_ID = 0;
    int myActivationId = -1;

    boolean typing;
    final TextSetting setting;
    float rectX, rectY, rectWidth, rectHeight;
    boolean dragging;
    int cursorPosition = 0;
    int selectionStart = -1;
    int selectionEnd = -1;
    long lastClickTime = 0;
    float xOffset = 0;
    String text = "";

    public TextComponent(TextSetting setting) {
        super(setting);
        this.setting = setting;
        this.text = setting.getText() != null ? setting.getText() : "";
        this.cursorPosition = text.length();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateVisibilityAnimation();

        // Drop focus if a different TextComponent has been clicked
        // since we last claimed focus. Single global counter, so
        // the most recently clicked input is always the one whose
        // myActivationId == ACTIVE_TEXT_COMPONENT_ID.
        if (typing && myActivationId != ACTIVE_TEXT_COMPONENT_ID) {
            typing = false;
            dragging = false;
            clearSelection();
        }

        var labelFont = Fonts.getSize(14, Fonts.Type.INTER_BOLD);

        boolean isModified = setting.isModified();
        float textOffset = animatedTextOffset(isModified);

        MatrixStack matrix = context.getMatrices();
        FontRenderer font = Fonts.getSize(12);

        String wrapped = StringUtil.wrap(setting.getLocalizedName(), (int) (width - 75 - textOffset), 14);
        float wrappedHeight = Fonts.getSize(14).getStringHeight(wrapped);
        height = (int) (20 + Math.max(0, (wrappedHeight - 14) / 2));
        float hoverProgress = animatedCardHover(MathUtil.isHovered(mouseX, mouseY, x, y, width, height));

        // Dynamic cursor: the input rect inside this component is a
        // text input, request the I-beam while the pointer is over
        // that specific rectangle (or while typing). The end-of-frame
        // commit lives in ScreenCursorMixin.
        boolean overInputRect = MathUtil.isHovered(mouseX, mouseY,
                x + width - 61.5F, y + height / 2.0F - 6.0F, 53.0F, 12.0F);
        if (overInputRect || typing) {
            vorga.phazeclient.api.system.cursor.CursorManager.requestBeam();
        }

        renderSettingCard(context, typing ? 1.0f : 0.0f, hoverProgress);

        this.rectX = x + width - 61.5F;
        this.rectY = y + height / 2 - 6.0F;
        this.rectWidth = 53.0F;
        this.rectHeight = 12.0F;

        rectangle.render(ShapeProperties.create(matrix, rectX, rectY, rectWidth, rectHeight)
                .round(2).thickness(1.1F)
                .outlineColor(MenuStyle.withAlpha(typing ? MenuStyle.CHIP_ACTIVE : MenuStyle.BORDER, currentAlpha))
                .color(MenuStyle.withAlpha(MenuStyle.PANEL_CHIP, currentAlpha))
                .build());

        resetIcon.position(x, y, height).alpha(currentAlpha).modified(isModified).render(matrix);

        float textX = x + 10 + textOffset;
        labelFont.drawString(context.getMatrices(), wrapped, textX, centeredTextY(labelFont, wrapped), primaryText());

        // Drag-to-move-cursor: ONLY the component that was clicked
        // first holds the {@link #dragging} flag (set inside
        // mouseClicked + mouseDragged). Other components reset their
        // own flag to false in mouseDragged when they didn't
        // actually receive the press, so a single drag gesture only
        // ever updates one input's cursor. Mouse-released clears
        // the flag.
        if (dragging) {
            cursorPosition = getCursorIndexAt(mouseX);
            if (selectionStart == -1) selectionStart = cursorPosition;
            selectionEnd = cursorPosition;
        }

        updateXOffset(font, cursorPosition);

        String displayText = text.isEmpty() ? (setting.getText() == null ? "" : setting.getText()) : text;
        float inputTextY = centeredTextY(font, displayText.isEmpty() ? "I" : displayText, rectY, rectHeight);
        float cursorTop = inputTextY - 2.5F;
        float cursorHeight = Math.max(7.0F, renderedTextHeight(font, "I"));

        // Scissor-clip everything we draw inside the input box so
        // text + cursor + selection never bleed outside the rect
        // when the cursor walks past the right edge. The clip is
        // measured at the GL window level using the same scaled-
        // GUI factor that DrawContext uses, so it matches the
        // logical {@code rect*} coordinates we render into.
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        double sf = mc.getWindow().getScaleFactor();
        int sx0 = (int) Math.round((rectX + 1) * sf);
        int sy0 = (int) Math.round((rectY + 1) * sf);
        int sx1 = (int) Math.round((rectX + rectWidth - 1) * sf);
        int sy1 = (int) Math.round((rectY + rectHeight - 1) * sf);
        // DrawContext.enableScissor uses logical (GUI) coords on
        // 1.21.4, not raw window pixels, so feed it the un-scaled
        // bounds. The check ensures we don't double-scale.
        context.enableScissor((int) (rectX + 1), (int) (rectY + 1),
                (int) (rectX + rectWidth - 1), (int) (rectY + rectHeight - 1));

        if (typing && selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
            int start = Math.max(0, Math.min(getStartOfSelection(), text.length()));
            int end = Math.max(0, Math.min(getEndOfSelection(), text.length()));
            if (start < end) {
                float selectionXStart = rectX + 3 - xOffset + font.getStringWidth(text.substring(0, start));
                float selectionXEnd = rectX + 3 - xOffset + font.getStringWidth(text.substring(0, end));
                float selectionWidth = selectionXEnd - selectionXStart;

                rectangle.render(ShapeProperties.create(matrix, selectionXStart, cursorTop - 0.5F, selectionWidth, cursorHeight + 1.0F)
                        .color(MenuStyle.withAlpha(MenuStyle.CHIP_ACTIVE, currentAlpha))
                        .build());
            }
        }

        font.drawString(context.getMatrices(), text, rectX + 3 - xOffset, inputTextY,
                MenuStyle.withAlpha(typing ? MenuStyle.TEXT_PRIMARY : MenuStyle.TEXT_MUTED, currentAlpha));

        if (!typing && text.isEmpty()) {
            font.drawString(context.getMatrices(), text = setting.getText(), rectX + 3, inputTextY, mutedText());
        }

        long currentTime = System.currentTimeMillis();
        boolean focused = typing && (currentTime % 1000 < 500);

        if (focused && (selectionStart == -1 || selectionStart == selectionEnd)) {
            float cursorX = font.getStringWidth(text.substring(0, cursorPosition));
            rectangle.render(ShapeProperties.create(matrix, rectX + 3 - xOffset + cursorX, cursorTop, 0.5F, cursorHeight)
                    .color(MenuStyle.withAlpha(0xFFFFFFFF, currentAlpha))
                    .build());
        }

        context.disableScissor();
    }


    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // Only continue drag if THIS component is the one whose
        // input rect originally received the mousedown. Without
        // this every visible TextComponent would treat every
        // dragged mouse motion as an in-progress text selection,
        // so dragging text in one input would also slide cursor
        // in every other input on the same module.
        if (!dragging) {
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && resetIcon.isHovered(mouseX, mouseY)) {
            playButtonClickSound();
            setting.reset();
            text = setting.getText() != null ? setting.getText() : "";
            cursorPosition = text.length();
            return true;
        }

        boolean insideRect = MathUtil.isHovered(mouseX, mouseY, rectX, rectY, rectWidth, rectHeight);
        if (insideRect && button == 0) {
            playButtonClickSound();
            // Tell every OTHER text component to drop focus before
            // we claim it. Otherwise a fresh click on input B while
            // input A is still {@code typing} leaves both flagged
            // typing=true and char input goes into both at once.
            // Implemented as a static notification because
            // AbstractSettingComponent doesn't expose a sibling-
            // iteration API.
            ACTIVE_TEXT_COMPONENT_ID++;
            myActivationId = ACTIVE_TEXT_COMPONENT_ID;
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastClickTime < 250) {
                selectionStart = 0;
                selectionEnd = text.length();
            } else {
                typing = true;
                dragging = true;
                lastClickTime = currentTime;
                cursorPosition = getCursorIndexAt(mouseX);
                selectionStart = cursorPosition;
                selectionEnd = cursorPosition;
            }
            return true;
        } else {
            // Click landed outside our input rect. Drop focus AND
            // any selection so the next char-typed event doesn't
            // hit a stale typing=true.
            typing = false;
            dragging = false;
            clearSelection();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        if (!setting.isVisible()) {
            return false;
        }
        if (MathUtil.isHovered(mouseX, mouseY, rectX, rectY, rectWidth, rectHeight)) {
            return true;
        }
        return MathUtil.isHovered(mouseX, mouseY, x + 9, y + 6, width - rectWidth - 18, height - 12);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (typing && (text.length() < setting.getMax())) {
            deleteSelectedText();
            text = text.substring(0, cursorPosition) + chr + text.substring(cursorPosition);
            cursorPosition++;
            clearSelection();
        }
        return super.charTyped(chr, modifiers);
    }

    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (typing) {
            if (Screen.hasControlDown()) switch (keyCode) {
                case GLFW.GLFW_KEY_A -> selectAllText();
                case GLFW.GLFW_KEY_V -> pasteFromClipboard();
                case GLFW.GLFW_KEY_C -> copyToClipboard();
            }
            else switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_ENTER -> handleTextModification(keyCode);
                case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> moveCursor(keyCode);
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }


    private void pasteFromClipboard() {
        String clipboardText = GLFW.glfwGetClipboardString(window().getHandle());
        if (clipboardText != null) {
            replaceText(cursorPosition, cursorPosition, clipboardText);
        }
    }


    private void copyToClipboard() {
        if (hasSelection()) {
            GLFW.glfwSetClipboardString(window().getHandle(), getSelectedText());
        }
    }


    private void selectAllText() {
        selectionStart = 0;
        selectionEnd = text.length();
    }

    
    private void handleTextModification(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (hasSelection()) {
                replaceText(getStartOfSelection(), getEndOfSelection(), "");
            } else if (cursorPosition > 0) {
                replaceText(cursorPosition - 1, cursorPosition, "");
            }
        } else if (keyCode == GLFW.GLFW_KEY_ENTER) {
            if (text.length() >= setting.getMin() && text.length() <= setting.getMax()) {
                setting.setText(text);
                typing = false;
            }
        }
    }

    
    private void moveCursor(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_LEFT && cursorPosition > 0) {
            cursorPosition--;
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT && cursorPosition < text.length()) {
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

    
    private void replaceText(int start, int end, String replacement) {
        if (start < 0) start = 0;
        if (end > text.length()) end = text.length();
        if (start > end) start = end;

        text = text.substring(0, start) + replacement + text.substring(end);
        cursorPosition = start + replacement.length();
        clearSelection();
    }

    
    private boolean hasSelection() {
        return selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd;
    }


    private String getSelectedText() {
        return text.substring(getStartOfSelection(), getEndOfSelection());
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

    
    private int getCursorIndexAt(double mouseX) {
        FontRenderer font = Fonts.getSize(12, Fonts.Type.INTER_BOLD);
        float relativeX = (float) mouseX - rectX - 3 + xOffset;
        int position = 0;
        while (position < text.length()) {
            float textWidth = font.getStringWidth(text.substring(0, position + 1));
            if (textWidth > relativeX) {
                break;
            }
            position++;
        }
        return position;
    }

    
    private void updateXOffset(FontRenderer font, int cursorPosition) {
        float cursorX = font.getStringWidth(text.substring(0, cursorPosition));
        if (cursorX < xOffset) {
            xOffset = cursorX;
        } else if (cursorX - xOffset > rectWidth - 7) {
            xOffset = cursorX - (rectWidth - 7);
        }
    }

    
    private void deleteSelectedText() {
        if (hasSelection()) {
            replaceText(getStartOfSelection(), getEndOfSelection(), "");
        }
    }
}
