package vorga.phazeclient.implement.menu.components.implement.other;

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

/**
 * Compact share-key / rename modal. Two modes share the same panel:
 *
 * <ul>
 *   <li>{@link Mode#SHARE} - "Создание ключа". Numeric input
 *       ("Количество использований"), primary "Создать" uploads the
 *       config and copies the resulting code to clipboard.</li>
 *   <li>{@link Mode#RENAME} - "Переименование конфига". Text input
 *       ("Новое имя"), primary "Сохранить" renames the file in
 *       place.</li>
 * </ul>
 *
 * <p>The dim backdrop matches the menu's outer rounded corners so a
 * dark rectangular bleed doesn't poke through past the menu's
 * curved edge. Buttons sit centred under the input - no underline on
 * the secondary because the user explicitly asked for a clean
 * symmetric layout.
 */
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
    private static final float SECONDARY_W = 38.0F;
    private static final float BUTTON_GAP = 10.0F;

    /** Menu corner radius, matched in the dim backdrop so the
     *  dim layer hugs the menu's silhouette instead of bleeding
     *  past its rounded corners. Mirrors {@code BackgroundComponent}. */
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

    /** Set by RENAME mode when the rename succeeds, so the caller
     *  (ConfigsViewComponent) can refresh its row list. */
    private Runnable onRenamed = null;

    public ConfigShareModalComponent() {
        size(MODAL_W, MODAL_H);
        // Animations default to FORWARDS / value=1, which would make
        // the modal render at full opacity from the very first frame
        // even when isOpen()==false. Park the open animation at the
        // BACKWARDS-finished state on construction so render() exits
        // immediately until openShare/openRename actually fires.
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

    /**
     * Opens the modal in IMPORT mode. The user pastes a share-key
     * code (like {@code nomad-9wxf-49k7}) and we download the
     * matching share-string from the server, then hand it off to
     * {@link ConfigManager#importFromString} which persists it as a
     * fresh imported_<n> entry. {@code onImported} fires once the
     * import succeeds so the configs view can refresh its list.
     */
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

    /* ============================================================ */
    /* render                                                       */
    /* ============================================================ */

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!open && openAnimation.getOutputFloat() <= 0.001F) {
            return;
        }
        // Pull the latest language selection from Theme so a
        // language flip while this modal is open repaints in the
        // new locale on the very next frame.
        Theme.getInstance().syncLanguage();
        // Combine our own open/close fade with the menu's outer
        // open/close fade (globalAlpha) so the modal softly trails
        // off when the user closes the menu instead of disappearing
        // in one frame.
        float fadeAlpha = openAnimation.getOutputFloat() * globalAlpha;

        float canvasW = this.width;
        float canvasH = this.height;
        float modalX = x + (canvasW - MODAL_W) * 0.5F;
        float modalY = y + (canvasH - MODAL_H) * 0.5F;

        // Backdrop dim — rounded to match the menu's outer corner
        // radius so the dim layer doesn't bleed past the menu's
        // silhouette.
        rectangle.render(ShapeProperties.create(
                context.getMatrices(), x, y, canvasW, canvasH)
                .round(MENU_CORNER_RADIUS)
                .color(MenuStyle.withAlpha(0xFF000000, fadeAlpha * 0.55F))
                .build());

        // Solid panel underbase from the active palette so panel
        // translucency can never bleed through. We layer THREE solid
        // fills here and force the underbase pair to 0xFF alpha
        // directly (palette colours often carry 0x5C-0xA0 alpha so
        // {@link MenuStyle#withAlpha} can only attenuate, never
        // promote them; bit-or-ing in the alpha byte ourselves is
        // the only way to guarantee the panel never bleeds).
        int panelOpaqueBase = forceOpaque(MenuStyle.PANEL_BG);
        int panelOpaqueMid = forceOpaque(MenuStyle.PANEL_CONTENT);
        int panelTop = MenuStyle.mix(MenuStyle.PANEL_CONTENT, MenuStyle.PANEL_HEADER, 0.45F);
        int borderColor = MenuStyle.BORDER_LIGHT;

        // First layer: full-opacity PANEL_BG so even a fully
        // translucent theme blocks the menu chrome behind us.
        rectangle.render(ShapeProperties.create(
                context.getMatrices(), modalX, modalY, MODAL_W, MODAL_H)
                .round(MODAL_RADIUS)
                .color(MenuStyle.withAlpha(panelOpaqueBase, fadeAlpha))
                .build());
        // Second layer: full-opacity PANEL_CONTENT mid tone for the
        // visible body colour - the previous single-layer build let
        // the dim backdrop bleed through palette alphas of <0xC0.
        rectangle.render(ShapeProperties.create(
                context.getMatrices(), modalX, modalY, MODAL_W, MODAL_H)
                .round(MODAL_RADIUS)
                .color(MenuStyle.withAlpha(panelOpaqueMid, fadeAlpha))
                .build());
        // Third layer: themed top tint + outline.
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
            case SHARE: return Lang.t("modal.share.title");
            case RENAME: return Lang.t("modal.rename.title");
            case IMPORT: return Lang.t("modal.import.title");
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
        MatrixStack matrix = context.getMatrices();
        String title = titleText();
        String sub = subText();

        MsdfRenderer.renderText(
                MsdfFonts.bold(), title, TITLE_SIZE,
                MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, fadeAlpha),
                matrix.peek().getPositionMatrix(),
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), title, TITLE_SIZE, modalX, MODAL_W),
                modalY + PAD_Y, 0.0F);
        MsdfRenderer.renderText(
                MsdfFonts.bold(), sub, SUB_SIZE,
                MenuStyle.withAlpha(MenuStyle.TEXT_MUTED, fadeAlpha * 0.85F),
                matrix.peek().getPositionMatrix(),
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
        MatrixStack matrix = context.getMatrices();
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
                matrix.peek().getPositionMatrix(),
                ix + 9.0F,
                MenuStyle.centerMsdfTextY(INPUT_TEXT_SIZE, iy, INPUT_HEIGHT),
                0.0F);
        if (inputFocused && !placeholder
                && (System.currentTimeMillis() / 530L) % 2L == 0L) {
            // Position the caret to land exactly at the right edge
            // of the rendered text. We can't just use ix + padding +
            // getWidth(...) because MsdfRenderer.renderText adds two
            // adjustments getWidth doesn't account for:
            //   1. it shifts the first glyph by -0.75 px so the
            //      stroke's anti-aliased halo doesn't crop against
            //      the caller-supplied x;
            //   2. applyGlyphs inside the renderer advances by
            //      {thickness*0.5*size + spacing} after EVERY glyph
            //      (= 0.025*size per character at the default
            //      thickness=0.05, spacing=0 we use here).
            // Mirroring those two offsets keeps the caret pinned
            // flush against the last glyph regardless of input
            // length - without them, "rt" leaves a visible gap and
            // "333" overshoots into the last digit.
            // For our input fields the text is only digits / ASCII
            // letters / hyphen / underscore - all of which are in
            // the bold MSDF atlas - so the per-glyph advance count
            // equals inputText.length(). If we ever start letting
            // users type characters that may be missing from the
            // atlas, this needs to walk the string and count present
            // glyphs explicitly.
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
        MatrixStack matrix = context.getMatrices();
        float by = buttonsY(modalY);

        // Buttons centred under the input.
        float groupW = PRIMARY_W + BUTTON_GAP + SECONDARY_W;
        float primaryX = modalX + (MODAL_W - groupW) * 0.5F;
        float cancelX = primaryX + PRIMARY_W + BUTTON_GAP;

        boolean primaryHovered = MathUtil.isHovered(mouseX, mouseY, primaryX, by, PRIMARY_W, BUTTON_HEIGHT);
        boolean cancelHovered = MathUtil.isHovered(mouseX, mouseY, cancelX, by, SECONDARY_W, BUTTON_HEIGHT);
        primaryHover.setDirection(primaryHovered ? Direction.FORWARDS : Direction.BACKWARDS);
        cancelHover.setDirection(cancelHovered ? Direction.FORWARDS : Direction.BACKWARDS);

        // Primary - themed accent fill (CHIP_ACTIVE = palette accent).
        float pHover = primaryHover.getOutputFloat();
        int primaryFill = MenuStyle.mix(MenuStyle.CHIP_ACTIVE, 0xFFFFFFFF, pHover * 0.10F);
        int primaryOutline = MenuStyle.mix(MenuStyle.CHIP_ACTIVE, 0xFFFFFFFF, 0.18F);
        rectangle.render(ShapeProperties.create(matrix, primaryX, by, PRIMARY_W, BUTTON_HEIGHT)
                .round(4.0F).thickness(1.5F)
                .outlineColor(MenuStyle.withAlpha(primaryOutline, fadeAlpha))
                .color(MenuStyle.withAlpha(primaryFill, fadeAlpha))
                .build());
        String primaryLabel = primaryLabel();
        MsdfRenderer.renderText(
                MsdfFonts.bold(), primaryLabel, BUTTON_TEXT_SIZE,
                MenuStyle.withAlpha(0xFFFFFFFF, fadeAlpha),
                matrix.peek().getPositionMatrix(),
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), primaryLabel, BUTTON_TEXT_SIZE, primaryX, PRIMARY_W),
                MenuStyle.centerMsdfTextY(BUTTON_TEXT_SIZE, by, BUTTON_HEIGHT),
                0.0F);

        // Secondary — text only, NO underline. Hover lightens the
        // text color to give visual feedback.
        float cHover = cancelHover.getOutputFloat();
        int cancelText = MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, 0.40F + cHover * 0.50F);
        String cancelLabel = Lang.t("button.cancel");
        float labelX = MenuStyle.centerMsdfTextX(MsdfFonts.bold(), cancelLabel, BUTTON_TEXT_SIZE, cancelX, SECONDARY_W);
        float labelY = MenuStyle.centerMsdfTextY(BUTTON_TEXT_SIZE, by, BUTTON_HEIGHT);
        MsdfRenderer.renderText(
                MsdfFonts.bold(), cancelLabel, BUTTON_TEXT_SIZE,
                MenuStyle.withAlpha(cancelText, fadeAlpha),
                matrix.peek().getPositionMatrix(),
                labelX, labelY, 0.0F);
    }

    private void renderStatus(DrawContext context, float modalX, float modalY, float fadeAlpha) {
        if (statusMessage == null || statusMessage.isEmpty()) return;
        int color = statusError ? 0xFFE05050 : MenuStyle.ACCENT_GREEN;
        MsdfRenderer.renderText(
                MsdfFonts.bold(), statusMessage, STATUS_TEXT_SIZE,
                MenuStyle.withAlpha(color, fadeAlpha),
                context.getMatrices().peek().getPositionMatrix(),
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), statusMessage, STATUS_TEXT_SIZE, modalX, MODAL_W),
                statusY(modalY), 0.0F);
    }

    /* ============================================================ */
    /* events                                                       */
    /* ============================================================ */

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
        // Input.
        float ix = modalX + PAD_X;
        float iy = inputY(modalY);
        float iw = MODAL_W - PAD_X * 2.0F;
        if (MathUtil.isHovered(mouseX, mouseY, ix, iy, iw, INPUT_HEIGHT)) {
            inputFocused = true;
            return true;
        } else {
            inputFocused = false;
        }
        // Buttons.
        float by = buttonsY(modalY);
        float groupW = PRIMARY_W + BUTTON_GAP + SECONDARY_W;
        float primaryX = modalX + (MODAL_W - groupW) * 0.5F;
        float cancelX = primaryX + PRIMARY_W + BUTTON_GAP;
        if (MathUtil.isHovered(mouseX, mouseY, primaryX, by, PRIMARY_W, BUTTON_HEIGHT)) {
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
        // Ctrl+V / Ctrl+Insert paste. Goes through the same per-mode
        // filter as charTyped so a paste of "abc 123" into the SHARE
        // count field drops everything but digits, and a paste of an
        // import code into IMPORT lower-cases / strips bad chars
        // automatically.
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean isPasteCombo = (ctrl && keyCode == GLFW.GLFW_KEY_V)
                || (shift && keyCode == GLFW.GLFW_KEY_INSERT);
        if (isPasteCombo) {
            String pasted = readClipboard();
            if (pasted == null || pasted.isEmpty()) return true;
            // Cap how much we even attempt to feed in, so a 1MB
            // accidental paste doesn't loop char-by-char for
            // milliseconds. The per-field length cap below also
            // handles this, but bounding the input first is cheaper.
            if (pasted.length() > 256) pasted = pasted.substring(0, 256);
            for (int i = 0; i < pasted.length(); i++) {
                char c = pasted.charAt(i);
                // Reuse the existing per-mode filter / append logic.
                charTyped(c, modifiers);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!open || !inputFocused) return false;
        // Filter character class per mode.
        if (mode == Mode.SHARE) {
            if (chr < '0' || chr > '9') return true;
            if (inputText.length() >= 15) return true;
            if (inputText.isEmpty() && chr == '0') return true;
        } else if (mode == Mode.IMPORT) {
            // Share keys are lower-case letters / digits / hyphens.
            // Auto-lower-case so the user can paste a fragment that
            // had its case mangled by clipboard middleware (some
            // launchers titlecase what they paste).
            char lc = Character.toLowerCase(chr);
            if (!((lc >= 'a' && lc <= 'z') || (lc >= '0' && lc <= '9') || lc == '-' || lc == '_')) {
                return true;
            }
            if (inputText.length() >= 32) return true;
            inputText = inputText + lc;
            return true;
        } else {
            // Rename: allow letters, digits, underscore, dash. Reject
            // path separators / NUL / non-printable.
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

    /* ============================================================ */
    /* actions                                                      */
    /* ============================================================ */

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
        // Resolve the local Minecraft username so the server can
        // embed the first 5 chars in the public id and tag the row
        // for the admin dashboard. Falls back to null = "phaze".
        final String captured = share;
        final String authorCaptured = authorLabel == null || authorLabel.isEmpty() ? null : authorLabel;
        // Parse the user's "Количество использований" input. Empty
        // input means "unlimited" (server treats null as no cap).
        Integer maxUsesParsed;
        try {
            maxUsesParsed = inputText.isEmpty() ? null : Integer.valueOf(inputText);
        } catch (NumberFormatException nfe) {
            maxUsesParsed = null;
        }
        final Integer maxUsesCaptured = maxUsesParsed;
        // Carry the local config file name so importers can save the
        // imported copy under the same name (server stores it next to
        // the share-string and downloadFull returns it; see
        // {@link #triggerImport}).
        final String nameToShare = configName != null && !configName.isEmpty()
                ? configName
                : ConfigManager.getInstance().getCurrentConfigName();
        CompletableFuture
                .supplyAsync(() -> ConfigShareApi.upload(captured, authorCaptured, maxUsesCaptured, nameToShare))
                .thenAccept(id -> {
                    if (token != actionToken) return;
                    if (id == null) {
                        String detail = ConfigShareApi.getLastError();
                        // Surface the actual error so the user can
                        // tell network from server-side rejection.
                        // Keeps the message short - the panel only
                        // has room for ~30 chars before truncation.
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

    /**
     * IMPORT mode handler - downloads the share-string for the
     * supplied code, decodes it, and adds it to the local configs
     * folder as a fresh imported_<n> entry. The user can then
     * activate it from the configs list. We deliberately don't
     * auto-load it on import: the user might still be picking
     * between several pasted keys and shouldn't have their current
     * config silently swapped.
     */
    private void triggerImport() {
        String code = inputText.trim().toLowerCase();
        if (code.isEmpty()) {
            statusMessage = Lang.t("status.enter_code");
            statusError = true;
            return;
        }
        // Quick client-side sanity check so we don't burn a network
        // round-trip on input that obviously isn't a share-id. The
        // server uses the same regex on its end.
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
                        statusMessage = detail == null
                                ? Lang.t("status.key_not_found")
                                : Lang.t("status.error_prefix") + ": " + (detail.length() > 60 ? detail.substring(0, 60) + "..." : detail);
                        statusError = true;
                        return;
                    }
                    // Hand the import path the suggested name from
                    // the server. ConfigManager picks an "imported_<n>"
                    // fallback when the suggested name is null OR
                    // already taken locally so we never overwrite a
                    // user's existing config of the same label.
                    //
                    // Marshalled onto the render thread because
                    // {@link ConfigManager#importFromString} touches
                    // the same in-memory state that the per-tick
                    // autosave reads/writes from. Running it on the
                    // {@code supplyAsync} pool race-conditioned with
                    // autosave: the new file would land first, then
                    // an outgoing-autosave on the render thread would
                    // copy the (still-old) in-memory state OVER the
                    // freshly imported file - the user reported this
                    // as "configs swap places after import".
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

    /* ============================================================ */
    /* helpers                                                      */
    /* ============================================================ */

    /**
     * Strips the alpha channel off a palette colour and forces it to
     * 0xFF. {@code MenuStyle.withAlpha} can only attenuate (it
     * multiplies the existing alpha by the supplied factor), so it
     * can't promote a translucent palette colour to fully opaque -
     * this helper does that bit-twiddle directly.
     */
    private static int forceOpaque(int color) {
        return (color & 0x00FFFFFF) | 0xFF000000;
    }

    /**
     * Reads the system clipboard via Minecraft's keyboard helper.
     * Returns the empty string on any failure (no-clipboard / I/O
     * blocked / unsupported), which the paste handler then safely
     * treats as "nothing to paste".
     */
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
