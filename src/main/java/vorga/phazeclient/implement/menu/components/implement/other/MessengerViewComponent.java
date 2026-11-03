package vorga.phazeclient.implement.menu.components.implement.other;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.MessengerService;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.AbstractComponent;

import java.util.List;

/** Full-width, custom-rendered global chat page inside the Phaze menu. */
public final class MessengerViewComponent extends AbstractComponent {
    private static final float SIDE = 12.0F;
    private static final float TOP = 46.0F;
    private static final float TEXT = 6.4F;
    private static final float SMALL = 5.3F;
    private static final int ACCENT = 0xFF9B7BFF;
    private static final int CONTENT = 0xFF151923;
    private static final int INPUT = 0xFF0D1017;

    private final MessengerService service = MessengerService.getInstance();
    private boolean open;
    private final StringBuilder input = new StringBuilder();
    private double scroll;
    private volatile boolean sendResultReady;
    private volatile boolean sendResultSuccess;
    private boolean sendPending;
    private String pendingText;

    public void open() {
        open = true;
        service.start();
    }

    public void close() {
        open = false;
        input.setLength(0);
        sendPending = false;
        pendingText = null;
        sendResultReady = false;
    }

    public boolean isOpen() { return open; }
    public boolean shouldRender() { return open; }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!open) return;
        if (sendResultReady) {
            sendResultReady = false;
            sendPending = false;
            if (sendResultSuccess && pendingText != null && input.toString().equals(pendingText)) {
                input.setLength(0);
            }
            pendingText = null;
        }
        float left = x + SIDE;
        float right = x + width - SIDE;
        float top = y + TOP;
        float bottom = y + height - 12.0F;
        rectangle.render(ShapeProperties.create(context.getMatrices(), left, top, right - left, bottom - top)
                .round(5.0F).color(applyGlobalAlpha(CONTENT)).build());

        drawText(context, "GLOBAL CHAT", left + 14.0F, top + 13.0F, TEXT, MenuStyle.TEXT_PRIMARY);
        drawText(context, service.getStatus(), right - 62.0F, top + 14.0F, SMALL, MenuStyle.TEXT_MUTED);
        rectangle.render(ShapeProperties.create(context.getMatrices(), left + 12.0F, top + 31.0F,
                        right - left - 24.0F, 1.0F).color(applyGlobalAlpha(MenuStyle.BORDER)).build());

        List<MessengerService.Message> messages = service.getGlobalMessages();
        float lineY = bottom - 58.0F + (float) scroll;
        int first = Math.max(0, messages.size() - 28);
        for (int index = messages.size() - 1; index >= first; index--) {
            MessengerService.Message message = messages.get(index);
            String line = clip(message.senderUsername() + ": " + message.text(), 112);
            drawText(context, line, left + 14.0F, lineY, SMALL, MenuStyle.TEXT_PRIMARY);
            lineY -= 12.0F;
        }
        if (messages.isEmpty()) {
            drawText(context, "No messages yet. Say hello to the Phaze community.",
                    left + 14.0F, top + 62.0F, TEXT, MenuStyle.TEXT_MUTED);
        }

        float inputY = bottom - 35.0F;
        float sendX = right - 58.0F;
        rectangle.render(ShapeProperties.create(context.getMatrices(), left + 12.0F, inputY,
                        right - left - 82.0F, 22.0F).round(3.0F).color(applyGlobalAlpha(INPUT)).build());
        drawText(context, input.isEmpty() ? "Write to global chat..." : input.toString(), left + 19.0F,
                inputY + 7.0F, SMALL, input.isEmpty() ? MenuStyle.TEXT_MUTED : MenuStyle.TEXT_PRIMARY);
        rectangle.render(ShapeProperties.create(context.getMatrices(), sendX, inputY, 58.0F, 22.0F)
                .round(3.0F).color(applyGlobalAlpha(ACCENT)).build());
        drawText(context, "SEND", sendX + 15.0F, inputY + 7.0F, SMALL, 0xFF111018);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open || button != 0) return false;
        float sendX = x + width - SIDE - 58.0F;
        float sendY = y + height - 12.0F - 35.0F;
        if (mouseX >= sendX && mouseX <= sendX + 58.0F && mouseY >= sendY && mouseY <= sendY + 22.0F) {
            submit();
            return true;
        }
        return mouseX >= x + SIDE && mouseX <= x + width - SIDE
                && mouseY >= y + TOP && mouseY <= y + height - 12.0F;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!open) return false;
        scroll = Math.max(-220.0D, Math.min(0.0D, scroll + amount * 12.0D));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) return false;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !input.isEmpty()) {
            if (sendPending) return true;
            input.deleteCharAt(input.length() - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            String clipboard = net.minecraft.client.MinecraftClient.getInstance().keyboard.getClipboard();
            if (clipboard != null && !clipboard.isEmpty() && !sendPending) {
                String value = input + clipboard;
                if (value.codePointCount(0, value.length()) <= 255) input.append(clipboard);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!open || sendPending || Character.isISOControl(chr)) return false;
        if (input.codePointCount(0, input.length()) < 255) input.append(chr);
        return true;
    }

    private void submit() {
        if (input.isEmpty() || sendPending) return;
        pendingText = input.toString();
        sendPending = true;
        service.sendGlobal(pendingText, success -> {
            sendResultSuccess = success;
            sendResultReady = true;
        });
    }

    private void drawText(DrawContext context, String text, float textX, float textY, float size, int color) {
        MsdfRenderer.renderText(MsdfFonts.medium(), text, size, applyGlobalAlpha(color),
                context.getMatrices().peek().getPositionMatrix(), textX, textY, 0.0F);
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
