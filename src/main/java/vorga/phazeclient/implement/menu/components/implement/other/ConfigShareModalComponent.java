package vorga.phazeclient.implement.menu.components.implement.other;

import vorga.phazeclient.base.util.render.GuiMatrix;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.config.ConfigManager;
import vorga.phazeclient.implement.features.modules.client.Theme;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.AbstractComponent;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public class ConfigShareModalComponent extends AbstractComponent {

    public enum Mode { SHARE, RENAME, IMPORT }

    private static final float MODAL_W = 220.0F;
    private static final float MODAL_H = 120.0F;
    private static final float MODAL_RADIUS = 6.0F;
    private static final float PAD_X = 16.0F;
    private static final float PAD_Y = 13.0F;

    private static final float TITLE_SIZE = 9.0F;
    private static final float SUB_SIZE = 5.4F;
    private static final float INPUT_TEXT_SIZE = 6.0F;
    private static final float BUTTON_TEXT_SIZE = 6.5F;
    private static final float STATUS_TEXT_SIZE = 5.4F;

    private static final float INPUT_HEIGHT = 17.0F;
    private static final float BUTTON_HEIGHT = 16.0F;
    private static final float PRIMARY_W = 56.0F;
    private static final float CLOUD_PRIMARY_W = 104.0F;
    private static final float SECONDARY_W = 48.0F;
    private static final float BUTTON_GAP = 10.0F;

    private static final float MENU_CORNER_RADIUS = 8.0F;

    private final Animation openAnimation = new DecelerateAnimation().setMs(220).setValue(1);
    private final Animation primaryHover = new DecelerateAnimation().setMs(140).setValue(1);
    private final Animation cancelHover = new DecelerateAnimation().setMs(140).setValue(1);

    private boolean open = false;
    private Mode mode = Mode.SHARE;
    private String configName = null;
    private String authorLabel = "";
    private String inputText = "";
    private boolean inputFocused = false;

    private String statusMessage = "";
    private boolean statusError = false;
    private long actionToken = 0L;

    private Runnable onRenamed = null;

    public ConfigShareModalComponent() {
        size(MODAL_W, MODAL_H);

        openAnimation.setDirectionAndFinish(Direction.BACKWARDS);
    }

    public void openShare(String configName) {
        this.open = true;
        this.openAnimation.setDirection(Direction.FORWARDS);
        this.mode = Mode.SHARE;
        this.configName = configName;
        this.inputText = "";
        this.inputFocused = true;
        this.statusMessage = "";
        this.statusError = false;
        this.authorLabel = resolveAuthorLabel();
        this.onRenamed = null;
    }

    public void openRename(String configName, Runnable onRenamed) {
        this.open = true;
        this.openAnimation.setDirection(Direction.FORWARDS);
        this.mode = Mode.RENAME;
        this.configName = configName;
        this.inputText = configName == null ? "" : configName;
        this.inputFocused = true;
        this.statusMessage = "";
        this.statusError = false;
        this.authorLabel = resolveAuthorLabel();
        this.onRenamed = onRenamed;
    }

    public void openImport(Runnable onImported) {
        this.open = true;
        this.openAnimation.setDirection(Direction.FORWARDS);
        this.mode = Mode.IMPORT;
        this.configName = null;
        this.inputText = "";
        this.inputFocused = true;
        this.statusMessage = "";
        this.statusError = false;
        this.authorLabel = resolveAuthorLabel();
        this.onRenamed = onImported;
    }

    public void close() {
        this.open = false;
        this.openAnimation.setDirection(Direction.BACKWARDS);
        this.inputFocused = false;
    }

    public boolean isOpen() {
        return open;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!open && openAnimation.getOutputFloat() <= 0.001F) {
            return;
        }

        Theme.getInstance().syncLanguage();

        float fadeAlpha = openAnimation.getOutputFloat() * globalAlpha;

        float canvasW = this.width;
        float canvasH = this.height;
        float modalX = x + (canvasW - MODAL_W) * 0.5F;
        float modalY = y + (canvasH - MODAL_H) * 0.5F;

        rectangle.render(ShapeProperties.create(
                context.getMatrices(), x, y, canvasW, canvasH)
                .round(MENU_CORNER_RADIUS)
                .color(MenuStyle.withAlpha(0xFF000000, fadeAlpha * 0.55F))
                .build());

        int panelOpaqueBase = forceOpaque(MenuStyle.PANEL_BG);
        int panelOpaqueMid = forceOpaque(MenuStyle.PANEL_CONTENT);
        int panelTop = MenuStyle.mix(MenuStyle.PANEL_CONTENT, MenuStyle.PANEL_HEADER, 0.45F);
        int borderColor = MenuStyle.BORDER_LIGHT;

        rectangle.render(ShapeProperties.create(
                context.getMatrices(), modalX, modalY, MODAL_W, MODAL_H)
                .round(MODAL_RADIUS)
                .color(MenuStyle.withAlpha(panelOpaqueBase, fadeAlpha))
                .build());

        rectangle.render(ShapeProperties.create(
                context.getMatrices(), modalX, modalY, MODAL_W, MODAL_H)
                .round(MODAL_RADIUS)
                .color(MenuStyle.withAlpha(panelOpaqueMid, fadeAlpha))
                .build());

        rectangle.render(ShapeProperties.create(
                context.getMatrices(), modalX, modalY, MODAL_W, MODAL_H)
                .round(MODAL_RADIUS)
                .softness(1.0F)
                .thickness(1.5F)
                .outlineColor(MenuStyle.withAlpha(borderColor, fadeAlpha))
                .color(MenuStyle.withAlpha(panelTop, fadeAlpha))
                .build());

        renderHeader(context, modalX, modalY, fadeAlpha);
        renderInput(context, mouseX, mouseY, modalX, modalY, fadeAlpha);
        renderButtons(context, mouseX, mouseY, modalX, modalY, fadeAlpha);
        renderStatus(context, modalX, modalY, fadeAlpha);
    }

    private String titleText() {
        switch (mode) {
            case SHARE: return Lang.t("modal.cloud.title");
            case RENAME: return Lang.t("modal.rename.title");
            case IMPORT: return Lang.t("modal.cloud.title");
        }
        return "";
    }

    private String subText() {
        if (mode == Mode.IMPORT) {
            return Lang.t("modal.import.subtitle");
        }

        String name = configName == null ? ConfigManager.getInstance().getCurrentConfigName() : configName;
        return Lang.t("modal.share.subtitle.prefix") + " " + name + "  •  " + authorLabel;
    }

    private String placeholderText() {
        switch (mode) {
            case SHARE: return Lang.t("modal.share.placeholder");
            case RENAME: return Lang.t("modal.rename.placeholder");
            case IMPORT: return Lang.t("modal.import.placeholder");
        }
        return "";
    }

    private String primaryLabel() {
        switch (mode) {
            case SHARE: return Lang.t("modal.share.primary");
            case RENAME: return Lang.t("modal.rename.primary");
            case IMPORT: return Lang.t("modal.import.primary");
        }
        return "";
    }

    private void renderHeader(DrawContext context, float modalX, float modalY, float fadeAlpha) {
        Matrix3x2fStack matrix = context.getMatrices();
        String title = titleText();
        String sub = subText();

        MsdfRenderer.renderText(
                MsdfFonts.bold(), title, TITLE_SIZE,
                MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, fadeAlpha),
                GuiMatrix.mat4(matrix),
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), title, TITLE_SIZE, modalX, MODAL_W),
                modalY + PAD_Y, 0.0F);
        MsdfRenderer.renderText(
                MsdfFonts.bold(), sub, SUB_SIZE,
                MenuStyle.withAlpha(MenuStyle.TEXT_MUTED, fadeAlpha * 0.85F),
                GuiMatrix.mat4(matrix),
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), sub, SUB_SIZE, modalX, MODAL_W),
                modalY + PAD_Y + TITLE_SIZE + 3.0F, 0.0F);
    }

    private float inputY(float modalY) {
        return modalY + PAD_Y + TITLE_SIZE + SUB_SIZE + 12.0F;
    }

    private float buttonsY(float modalY) {
        return modalY + MODAL_H - PAD_Y - BUTTON_HEIGHT;
    }

    private float statusY(float modalY) {
        return buttonsY(modalY) - STATUS_TEXT_SIZE - 5.0F;
    }

    private void renderInput(DrawContext context, int mouseX, int mouseY,
                             float modalX, float modalY, float fadeAlpha) {
        Matrix3x2fStack matrix = context.getMatrices();
        float ix = modalX + PAD_X;
        float iy = inputY(modalY);
        float iw = MODAL_W - PAD_X * 2.0F;

        int fill = MenuStyle.mix(MenuStyle.CARD_INNER, MenuStyle.PANEL_CHIP, 0.50F);
        int outline = inputFocused
                ? MenuStyle.mix(MenuStyle.BORDER_LIGHT, MenuStyle.CHIP_ACTIVE, 0.55F)
                : MenuStyle.BORDER;
        rectangle.render(ShapeProperties.create(matrix, ix, iy, iw, INPUT_HEIGHT)
                .round(4.0F).thickness(1.5F)
                .outlineColor(MenuStyle.withAlpha(outline, fadeAlpha))
                .color(MenuStyle.withAlpha(fill, fadeAlpha))
                .build());

        boolean placeholder = inputText.isEmpty();
        String visible = placeholder ? placeholderText() : inputText;
        int textColor = placeholder
                ? MenuStyle.mix(MenuStyle.TEXT_MUTED, 0x000000FF, 0.30F)
                : MenuStyle.TEXT_PRIMARY;
        MsdfRenderer.renderText(
                MsdfFonts.bold(), visible, INPUT_TEXT_SIZE,
                MenuStyle.withAlpha(textColor, fadeAlpha),
                GuiMatrix.mat4(matrix),
                ix + 9.0F,
                MenuStyle.centerMsdfTextY(INPUT_TEXT_SIZE, iy, INPUT_HEIGHT),
                0.0F);
        if (inputFocused && !placeholder
                && (System.currentTimeMillis() / 530L) % 2L == 0L) {

            float thicknessPerChar = 0.025F * INPUT_TEXT_SIZE;
            float caretX = ix + 9.0F - 0.75F
                    + MsdfFonts.bold().getWidth(inputText, INPUT_TEXT_SIZE)
                    + thicknessPerChar * inputText.length();
            rectangle.render(ShapeProperties.create(matrix, caretX, iy + 4.0F, 1.0F, INPUT_HEIGHT - 8.0F)
                    .color(MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, fadeAlpha))
                    .build());
        }
    }

    private void renderButtons(DrawContext context, int mouseX, int mouseY,
                               float modalX, float modalY, float fadeAlpha) {
        Matrix3x2fStack matrix = context.getMatrices();
        float by = buttonsY(modalY);
        float primaryW = mode == Mode.RENAME ? PRIMARY_W : SECONDARY_W;

        float groupW = primaryW + BUTTON_GAP + SECONDARY_W;
        float primaryX = modalX + (MODAL_W - groupW) * 0.5F;
        float cancelX = primaryX + primaryW + BUTTON_GAP;

        boolean primaryHovered = MathUtil.isHovered(mouseX, mouseY, primaryX, by, primaryW, BUTTON_HEIGHT);
        boolean cancelHovered = MathUtil.isHovered(mouseX, mouseY, cancelX, by, SECONDARY_W, BUTTON_HEIGHT);
        primaryHover.setDirection(primaryHovered ? Direction.FORWARDS : Direction.BACKWARDS);
        cancelHover.setDirection(cancelHovered ? Direction.FORWARDS : Direction.BACKWARDS);

        float pHover = primaryHover.getOutputFloat();
        int primaryFill = MenuStyle.mix(MenuStyle.CHIP_ACTIVE, 0xFFFFFFFF, pHover * 0.10F);
        int primaryOutline = MenuStyle.mix(MenuStyle.CHIP_ACTIVE, 0xFFFFFFFF, 0.18F);
        rectangle.render(ShapeProperties.create(matrix, primaryX, by, primaryW, BUTTON_HEIGHT)
                .round(4.0F).thickness(1.25F)
                .outlineColor(MenuStyle.withAlpha(primaryOutline, fadeAlpha))
                .color(MenuStyle.withAlpha(primaryFill, fadeAlpha))
                .build());
        String primaryLabel = primaryLabel();
        int primaryTextColor = 0xFFFFFFFF;
        MsdfRenderer.renderText(
                MsdfFonts.bold(), primaryLabel, BUTTON_TEXT_SIZE,
                MenuStyle.withAlpha(primaryTextColor, fadeAlpha),
                GuiMatrix.mat4(matrix),
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), primaryLabel, BUTTON_TEXT_SIZE, primaryX, primaryW),
                MenuStyle.centerMsdfTextY(BUTTON_TEXT_SIZE, by, BUTTON_HEIGHT),
                0.0F);

        float cHover = cancelHover.getOutputFloat();
        int cancelFill = MenuStyle.mix(MenuStyle.PANEL_BG_SOFT, MenuStyle.TEXT_PRIMARY, cHover * 0.08F);
        int cancelOutline = MenuStyle.mix(MenuStyle.BORDER, MenuStyle.BORDER_LIGHT, 0.45F + cHover * 0.25F);
        rectangle.render(ShapeProperties.create(matrix, cancelX, by, SECONDARY_W, BUTTON_HEIGHT)
                .round(4.0F).thickness(1.25F)
                .outlineColor(MenuStyle.withAlpha(cancelOutline, fadeAlpha))
                .color(MenuStyle.withAlpha(cancelFill, fadeAlpha))
                .build());
        int cancelText = MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, 0.40F + cHover * 0.50F);
        String cancelLabel = Lang.t("button.cancel");
        float labelX = MenuStyle.centerMsdfTextX(MsdfFonts.bold(), cancelLabel, BUTTON_TEXT_SIZE, cancelX, SECONDARY_W);
        float labelY = MenuStyle.centerMsdfTextY(BUTTON_TEXT_SIZE, by, BUTTON_HEIGHT);
        MsdfRenderer.renderText(
                MsdfFonts.bold(), cancelLabel, BUTTON_TEXT_SIZE,
                MenuStyle.withAlpha(cancelText, fadeAlpha),
                GuiMatrix.mat4(matrix),
                labelX, labelY, 0.0F);
    }

    private void renderStatus(DrawContext context, float modalX, float modalY, float fadeAlpha) {
        if (statusMessage == null || statusMessage.isEmpty()) return;
        int color = statusError ? 0xFFE05050 : MenuStyle.ACCENT_GREEN;
        MsdfRenderer.renderText(
                MsdfFonts.bold(), statusMessage, STATUS_TEXT_SIZE,
                MenuStyle.withAlpha(color, fadeAlpha),
                GuiMatrix.mat4(context.getMatrices()),
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), statusMessage, STATUS_TEXT_SIZE, modalX, MODAL_W),
                statusY(modalY), 0.0F);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) return false;
        if (button != 0) return true;

        float modalX = x + (this.width - MODAL_W) * 0.5F;
        float modalY = y + (this.height - MODAL_H) * 0.5F;
        if (!MathUtil.isHovered(mouseX, mouseY, modalX, modalY, MODAL_W, MODAL_H)) {
            playButtonClickSound();
            close();
            return true;
        }

        float ix = modalX + PAD_X;
        float iy = inputY(modalY);
        float iw = MODAL_W - PAD_X * 2.0F;
        if (MathUtil.isHovered(mouseX, mouseY, ix, iy, iw, INPUT_HEIGHT)) {
            inputFocused = true;
            return true;
        } else {
            inputFocused = false;
        }

        float by = buttonsY(modalY);
        float primaryW = mode == Mode.RENAME ? PRIMARY_W : SECONDARY_W;
        float groupW = primaryW + BUTTON_GAP + SECONDARY_W;
        float primaryX = modalX + (MODAL_W - groupW) * 0.5F;
        float cancelX = primaryX + primaryW + BUTTON_GAP;
        if (MathUtil.isHovered(mouseX, mouseY, primaryX, by, primaryW, BUTTON_HEIGHT)) {
            playButtonClickSound();
            triggerPrimary();
            return true;
        }
        if (MathUtil.isHovered(mouseX, mouseY, cancelX, by, SECONDARY_W, BUTTON_HEIGHT)) {
            playButtonClickSound();
            close();
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            playButtonClickSound();
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            triggerPrimary();
            return true;
        }
        if (!inputFocused) return false;
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !inputText.isEmpty()) {
            inputText = inputText.substring(0, inputText.length() - 1);
            return true;
        }

        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean isPasteCombo = (ctrl && keyCode == GLFW.GLFW_KEY_V)
                || (shift && keyCode == GLFW.GLFW_KEY_INSERT);
        if (isPasteCombo) {
            String pasted = readClipboard();
            if (pasted == null || pasted.isEmpty()) return true;

            if (pasted.length() > 256) pasted = pasted.substring(0, 256);
            for (int i = 0; i < pasted.length(); i++) {
                char c = pasted.charAt(i);

                charTyped(c, modifiers);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!open || !inputFocused) return false;

        if (mode == Mode.SHARE) {
            if (chr < '0' || chr > '9') return true;
            if (inputText.length() >= 15) return true;
            if (inputText.isEmpty() && chr == '0') return true;
        } else if (mode == Mode.IMPORT) {

            char lc = Character.toLowerCase(chr);
            if (!((lc >= 'a' && lc <= 'z') || (lc >= '0' && lc <= '9') || lc == '-' || lc == '_')) {
                return true;
            }
            if (inputText.length() >= 32) return true;
            inputText = inputText + lc;
            return true;
        } else {

            if (chr < 0x20 || chr == 0x7F) return false;
            if (chr == '/' || chr == '\\' || chr == ':' || chr == '*'
                    || chr == '?' || chr == '"' || chr == '<' || chr == '>' || chr == '|') {
                return true;
            }
            if (inputText.length() >= 32) return true;
        }
        inputText = inputText + chr;
        return true;
    }

    private void triggerPrimary() {
        if (mode == Mode.SHARE) {
            triggerShare();
        } else if (mode == Mode.IMPORT) {
            triggerImport();
        } else {
            triggerRename();
        }
    }

    private void triggerShare() {
        statusMessage = Lang.t("status.loading");
        statusError = false;
        long token = ++actionToken;
        String share;
        try {
            share = ConfigManager.getInstance().exportCurrentToString();
        } catch (Throwable t) {
            share = null;
        }
        if (share == null) {
            statusMessage = Lang.t("status.copy_failed");
            statusError = true;
            return;
        }

        final String captured = share;
        final String authorCaptured = authorLabel == null || authorLabel.isEmpty() ? null : authorLabel;

        Integer maxUsesParsed;
        try {
            maxUsesParsed = inputText.isEmpty() ? null : Integer.valueOf(inputText);
        } catch (NumberFormatException nfe) {
            maxUsesParsed = null;
        }
        final Integer maxUsesCaptured = maxUsesParsed;

        final String nameToShare = configName != null && !configName.isEmpty()
                ? configName
                : ConfigManager.getInstance().getCurrentConfigName();
        CompletableFuture
                .supplyAsync(() -> ConfigShareApi.upload(captured, authorCaptured, maxUsesCaptured, nameToShare))
                .thenAccept(id -> {
                    if (token != actionToken) return;
                    if (id == null) {
                        String detail = ConfigShareApi.getLastError();
                        if (Lang.t("status.cloud_disabled_detail").equals(detail)) {
                            statusMessage = detail;
                            statusError = true;
                            return;
                        }

                        statusMessage = detail == null
                                ? Lang.t("status.server_unreachable")
                                : Lang.t("status.error_prefix") + ": " + (detail.length() > 60 ? detail.substring(0, 60) + "..." : detail);
                        statusError = true;
                        return;
                    }
                    writeClipboard(id);
                    statusMessage = Lang.t("status.copied_prefix") + " " + id + " " + Lang.t("status.copied_suffix");
                    statusError = false;
                });
    }

    private void triggerImport() {
        String code = inputText.trim().toLowerCase();
        if (code.isEmpty()) {
            statusMessage = Lang.t("status.enter_code");
            statusError = true;
            return;
        }

        if (!code.matches("^[a-z0-9_]{1,5}-[a-z0-9]{4}-[a-z0-9]{4}$")) {
            statusMessage = Lang.t("status.invalid_code_format");
            statusError = true;
            return;
        }
        statusMessage = Lang.t("status.loading");
        statusError = false;
        long token = ++actionToken;
        CompletableFuture
                .supplyAsync(() -> ConfigShareApi.downloadFull(code))
                .thenAccept(result -> {
                    if (token != actionToken) return;
                    if (result == null) {
                        String detail = ConfigShareApi.getLastError();
                        if (Lang.t("status.cloud_disabled_detail").equals(detail)) {
                            statusMessage = detail;
                            statusError = true;
                            return;
                        }
                        statusMessage = detail == null
                                ? Lang.t("status.key_not_found")
                                : Lang.t("status.error_prefix") + ": " + (detail.length() > 60 ? detail.substring(0, 60) + "..." : detail);
                        statusError = true;
                        return;
                    }

                    MinecraftClient mc = MinecraftClient.getInstance();
                    Runnable importTask = () -> {
                        String name = ConfigManager.getInstance().importFromString(result.payload, result.name);
                        if (name == null) {
                            statusMessage = Lang.t("status.import_failed");
                            statusError = true;
                            return;
                        }
                        statusMessage = Lang.t("status.imported_prefix") + " " + name;
                        statusError = false;
                        if (onRenamed != null) onRenamed.run();
                    };
                    if (mc != null) {
                        mc.execute(importTask);
                    } else {
                        importTask.run();
                    }
                });
    }

    private void triggerRename() {
        if (configName == null) {
            statusMessage = Lang.t("status.config_name_missing");
            statusError = true;
            return;
        }
        String newName = inputText.trim();
        if (newName.isEmpty()) {
            statusMessage = Lang.t("status.enter_new_name");
            statusError = true;
            return;
        }
        if (newName.equalsIgnoreCase(configName)) {
            close();
            return;
        }
        ConfigManager mgr = ConfigManager.getInstance();
        if (mgr.getConfigFile(newName).exists()) {
            statusMessage = Lang.t("status.name_taken");
            statusError = true;
            return;
        }
        try {
            try {
                java.lang.reflect.Method renameMethod =
                        mgr.getClass().getMethod("renameConfig", String.class, String.class);
                renameMethod.invoke(mgr, configName, newName);
            } catch (NoSuchMethodException ignored) {
                File from = mgr.getConfigFile(configName);
                File to = mgr.getConfigFile(newName);
                if (from.exists() && !to.exists()) {
                    if (!from.renameTo(to)) {
                        statusMessage = Lang.t("status.rename_failed");
                        statusError = true;
                        return;
                    }
                    if (configName.equalsIgnoreCase(mgr.getCurrentConfigName())) {
                        mgr.loadConfig(newName);
                    }
                }
            }
        } catch (Throwable t) {
            statusMessage = Lang.t("status.rename_error");
            statusError = true;
            return;
        }
        if (onRenamed != null) onRenamed.run();
        close();
    }

    private static int forceOpaque(int color) {
        return (color & 0x00FFFFFF) | 0xFF000000;
    }

    private static String readClipboard() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.keyboard != null) {
                String s = mc.keyboard.getClipboard();
                return s == null ? "" : s;
            }
        } catch (Throwable ignored) {}
        return "";
    }

    private static String resolveAuthorLabel() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.getSession() != null && mc.getSession().getUsername() != null) {
                return mc.getSession().getUsername();
            }
        } catch (Throwable ignored) {}
        return "PHAZE";
    }

    private static void writeClipboard(String value) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.keyboard != null) mc.keyboard.setClipboard(value);
        } catch (Throwable ignored) {}
    }
}
