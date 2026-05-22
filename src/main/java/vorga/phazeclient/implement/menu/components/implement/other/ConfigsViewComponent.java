package vorga.phazeclient.implement.menu.components.implement.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.config.ConfigManager;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.AbstractComponent;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * "CONFIGS" tab content. The sidebar is hidden by {@code MenuScreen}
 * while this view is open, so the row list spans the full menu width
 * minus a tiny margin. No header / back-arrow / title - the user told
 * us those duplicated information already shown in the top tabs.
 *
 * <p>Each row carries:
 * <ul>
 *   <li>Top-left: {@code dd.MM HH:mm} timestamp from the file's
 *       last-modified time. Cached when the view opens so we don't
 *       hammer the disk on every frame.</li>
 *   <li>Bottom-left: the config name + author tag. Author is the
 *       local Minecraft username (server-authoritative tagging will
 *       come when accounts ship).</li>
 *   <li>Right: a vertical three-dot kebab. Clicking it spawns a
 *       small popup at the click point with three actions
 *       (Поделиться / Переименовать / Удалить).</li>
 * </ul>
 *
 * <p>Click on the row body activates that config (loads it).
 */
public class ConfigsViewComponent extends AbstractComponent {

    private static final float ROW_HEIGHT = 32.0F;
    private static final float ROW_GAP = 5.0F;
    private static final float ROW_RADIUS = 5.0F;
    private static final float ROW_PAD_X = 12.0F;
    private static final float TIMESTAMP_SIZE = 5.6F;
    private static final float NAME_SIZE = 7.0F;
    private static final float KEBAB_AREA_W = 22.0F;
    private static final float DOT_SIZE = 1.6F;
    private static final float DOT_GAP = 1.4F;

    /** Top + bottom inset from the menu's content area. Keeps the
     *  list out from under the top tabs and the bottom blur frame. */
    private static final float TOP_MARGIN = 50.0F;
    private static final float BOTTOM_MARGIN = 16.0F;
    private static final float SIDE_MARGIN = 12.0F;

    private final Animation openAnim = new DecelerateAnimation().setMs(1).setValue(1);
    private final Map<String, Animation> rowHoverAnims = new HashMap<>();
    private final Map<String, Animation> kebabHoverAnims = new HashMap<>();

    /** Popup attached to the row whose kebab the user clicked.
     *  Lives until its fade-out animation finishes so close-clicks
     *  visually trail off instead of snapping. */
    private KebabPopup popup = null;

    private boolean open = false;

    private final Map<String, String> timestampCache = new LinkedHashMap<>();
    private final SimpleDateFormat tsFormat = new SimpleDateFormat("dd.MM HH:mm", Locale.ROOT);

    private float scrollOffset = 0.0F;
    private float maxScrollOffset = 0.0F;

    public void open() {
        this.open = true;
        this.openAnim.setDirection(Direction.FORWARDS);
        if (this.popup != null) this.popup.close();
        this.popup = null;
        this.scrollOffset = 0.0F;
        refreshTimestamps();
    }

    public void close() {
        this.open = false;
        this.openAnim.setDirection(Direction.BACKWARDS);
        if (this.popup != null) this.popup.close();
    }

    public boolean isOpen() {
        return open || openAnim.getOutputFloat() > 0.001F;
    }

    private void refreshTimestamps() {
        timestampCache.clear();
        ConfigManager mgr = ConfigManager.getInstance();
        for (String name : mgr.getConfigList()) {
            File file = mgr.getConfigFile(name);
            long mtime = file.exists() ? file.lastModified() : 0L;
            timestampCache.put(name, mtime > 0 ? tsFormat.format(new Date(mtime)) : "—");
        }
    }

    /**
     * Hook the import modal calls after a successful download. Just
     * a thin wrapper around {@link #refreshTimestamps} that the menu
     * can pass as a {@code Runnable} - keeps the import path from
     * having to know about ConfigsView's internals.
     */
    public void refreshAfterImport() {
        refreshTimestamps();
    }

    /* ============================================================ */
    /* render                                                       */
    /* ============================================================ */

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!open && openAnim.getOutputFloat() <= 0.001F) return;
        // Combine the menu's open/close fade (globalAlpha, set by
        // MenuScreen each frame) with our own configs-view open/close
        // fade so the rows AND the kebab popup softly fade in/out
        // alongside the rest of the GUI instead of snapping.
        float fadeAlpha = openAnim.getOutputFloat() * globalAlpha;

        float listX = x + SIDE_MARGIN;
        float listW = width - SIDE_MARGIN * 2.0F;
        float listTop = y + TOP_MARGIN;
        float listBottom = y + height - BOTTOM_MARGIN;

        renderRows(context, mouseX, mouseY, listX, listTop, listW, listBottom, fadeAlpha);

        // Drop the popup once its fade-out is fully done so we don't
        // keep dispatching hover work to a transparent panel.
        if (popup != null) {
            popup.render(context, mouseX, mouseY, fadeAlpha);
            if (popup.isFullyClosed()) {
                popup = null;
            }
        }
    }

    private void renderRows(DrawContext context, int mouseX, int mouseY,
                            float listX, float listY, float listW, float listBottom,
                            float fadeAlpha) {
        MatrixStack matrix = context.getMatrices();
        ConfigManager mgr = ConfigManager.getInstance();
        String[] names = mgr.getConfigList();

        // Bound scroll to the actual visible window so the user can
        // never scroll past the last row or above the first.
        float listH = listBottom - listY;
        float contentH = names.length * (ROW_HEIGHT + ROW_GAP) - ROW_GAP;
        maxScrollOffset = Math.max(0.0F, contentH - listH);
        if (scrollOffset < 0.0F) scrollOffset = 0.0F;
        if (scrollOffset > maxScrollOffset) scrollOffset = maxScrollOffset;

        // GL scissor so any row that runs past the visible window is
        // clipped to it. Without this the bottom row would visibly
        // poke under the menu's rounded bottom border.
        context.enableScissor((int) listX, (int) listY,
                (int) (listX + listW), (int) listBottom);

        String authorLabel = resolveAuthorLabel();
        float rowY = listY - scrollOffset;
        for (String name : names) {
            if (rowY + ROW_HEIGHT > listY && rowY < listBottom) {
                renderRow(matrix, mouseX, mouseY, listX, rowY, listW, name, authorLabel, fadeAlpha);
            }
            rowY += ROW_HEIGHT + ROW_GAP;
        }

        context.disableScissor();
    }

    private void renderRow(MatrixStack matrix, int mouseX, int mouseY,
                           float listX, float rowY, float listW,
                           String name, String authorLabel, float fadeAlpha) {
        boolean hovered = MathUtil.isHovered(mouseX, mouseY, listX, rowY, listW, ROW_HEIGHT);
        Animation rowAnim = rowHoverAnims.computeIfAbsent(name,
                k -> new DecelerateAnimation().setMs(160).setValue(1));
        rowAnim.setDirection(hovered ? Direction.FORWARDS : Direction.BACKWARDS);
        float h = rowAnim.getOutputFloat();

        int fill = MenuStyle.mix(MenuStyle.PANEL_CHIP, MenuStyle.CARD_OPTIONS, 0.30F + h * 0.10F);
        int outline = MenuStyle.mix(MenuStyle.BORDER, MenuStyle.BORDER_LIGHT, h * 0.40F);
        rectangle.render(ShapeProperties.create(matrix, listX, rowY, listW, ROW_HEIGHT)
                .round(ROW_RADIUS).thickness(1.2F)
                .outlineColor(MenuStyle.withAlpha(outline, fadeAlpha))
                .color(MenuStyle.withAlpha(fill, fadeAlpha * 0.92F))
                .build());

        // Timestamp at the top, name + author below. Vertically the
        // two stack with a small inter-line gap; horizontally both
        // share the same left padding.
        String ts = timestampCache.getOrDefault(name, "—");
        MsdfRenderer.renderText(
                MsdfFonts.bold(), ts, TIMESTAMP_SIZE,
                MenuStyle.withAlpha(MenuStyle.TEXT_MUTED, fadeAlpha * 0.85F),
                matrix.peek().getPositionMatrix(),
                listX + ROW_PAD_X,
                rowY + 5.5F,
                0.0F
        );
        String label = name + "  •  " + authorLabel;
        MsdfRenderer.renderText(
                MsdfFonts.bold(), label, NAME_SIZE,
                MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, fadeAlpha),
                matrix.peek().getPositionMatrix(),
                listX + ROW_PAD_X,
                rowY + ROW_HEIGHT - NAME_SIZE - 6.0F,
                0.0F
        );

        // Kebab.
        float kebabX = listX + listW - KEBAB_AREA_W;
        boolean kHover = MathUtil.isHovered(mouseX, mouseY, kebabX, rowY, KEBAB_AREA_W, ROW_HEIGHT);
        Animation kAnim = kebabHoverAnims.computeIfAbsent(name,
                k -> new DecelerateAnimation().setMs(140).setValue(1));
        kAnim.setDirection(kHover ? Direction.FORWARDS : Direction.BACKWARDS);
        renderKebab(matrix, kebabX, rowY, kAnim.getOutputFloat(), fadeAlpha);
    }

    private void renderKebab(MatrixStack matrix, float kebabX, float rowY, float hover, float fadeAlpha) {
        int color = MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, 0.30F + hover * 0.55F);
        float cx = kebabX + KEBAB_AREA_W * 0.5F - DOT_SIZE * 0.5F;
        float cy = rowY + ROW_HEIGHT * 0.5F - DOT_SIZE * 0.5F;
        for (int i = -1; i <= 1; i++) {
            rectangle.render(ShapeProperties.create(matrix,
                    cx, cy + i * (DOT_SIZE + DOT_GAP), DOT_SIZE, DOT_SIZE)
                    .round(DOT_SIZE * 0.5F).color(MenuStyle.withAlpha(color, fadeAlpha))
                    .build());
        }
    }

    /* ============================================================ */
    /* events                                                       */
    /* ============================================================ */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) return false;
        if (button != 0) return false;

        // Popup intercepts first so a click inside it lands on its
        // action menu, not on the row underneath. Only popups that
        // are still opening / fully open answer to clicks - one that
        // is mid-fade-out is purely visual.
        if (popup != null && popup.isInteractive()) {
            if (popup.handleClick(mouseX, mouseY)) {
                popup.close();
                return true;
            }
            // Click outside the popup starts its smooth fade-out. We
            // deliberately FALL THROUGH so a click on a different
            // kebab in the same gesture immediately spawns the new
            // popup - without this, the user has to click twice
            // (once to close the old popup, once to open the new
            // one), which reads as "clicking the kebab does nothing"
            // half the time.
            popup.close();
        }

        float listX = x + SIDE_MARGIN;
        float listW = width - SIDE_MARGIN * 2.0F;
        float listTop = y + TOP_MARGIN;
        float listBottom = y + height - BOTTOM_MARGIN;
        if (mouseY < listTop || mouseY > listBottom) return false;
        if (mouseX < listX || mouseX > listX + listW) return false;

        ConfigManager mgr = ConfigManager.getInstance();
        String[] names = mgr.getConfigList();
        float rowY = listTop - scrollOffset;
        for (String name : names) {
            if (rowY + ROW_HEIGHT > listTop && rowY < listBottom) {
                float kebabX = listX + listW - KEBAB_AREA_W;
                if (MathUtil.isHovered(mouseX, mouseY, kebabX, rowY, KEBAB_AREA_W, ROW_HEIGHT)) {
                    playButtonClickSound();
                    popup = new KebabPopup(name, kebabX + KEBAB_AREA_W * 0.5F,
                            rowY + ROW_HEIGHT - 2.0F, this);
                    return true;
                }
                if (MathUtil.isHovered(mouseX, mouseY, listX, rowY, listW - KEBAB_AREA_W, ROW_HEIGHT)) {
                    playButtonClickSound();
                    mgr.loadConfig(name);
                    refreshTimestamps();
                    return true;
                }
            }
            rowY += ROW_HEIGHT + ROW_GAP;
        }
        return false;
    }

    /**
     * Returns true while a kebab popup is currently visible (open or
     * mid-fade-out). MenuScreen reads this to refuse menu-window
     * drags while a popup or modal is up - otherwise the click that
     * closes the popup also drags the entire menu, which feels broken.
     */
    public boolean isPopupOpen() {
        return popup != null && popup.isInteractive();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!open) return false;
        scrollOffset -= (float) amount * (ROW_HEIGHT + ROW_GAP);
        if (scrollOffset < 0.0F) scrollOffset = 0.0F;
        if (scrollOffset > maxScrollOffset) scrollOffset = maxScrollOffset;
        return true;
    }

    /* ============================================================ */
    /* helpers                                                      */
    /* ============================================================ */

    private static String resolveAuthorLabel() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.getSession() != null && mc.getSession().getUsername() != null) {
                return mc.getSession().getUsername();
            }
        } catch (Throwable ignored) {}
        return "PHAZE";
    }

    private static String nextRename(String base) {
        for (int i = 2; i < 100; i++) {
            String candidate = base + "_" + i;
            if (!ConfigManager.getInstance().getConfigFile(candidate).exists()) {
                return candidate;
            }
        }
        return base + "_" + System.currentTimeMillis();
    }

    private static void renameConfig(String oldName, String newName) {
        ConfigManager mgr = ConfigManager.getInstance();
        try {
            java.lang.reflect.Method renameMethod =
                    mgr.getClass().getMethod("renameConfig", String.class, String.class);
            renameMethod.invoke(mgr, oldName, newName);
            return;
        } catch (Throwable ignored) {}
        try {
            File from = mgr.getConfigFile(oldName);
            File to = mgr.getConfigFile(newName);
            if (from.exists() && !to.exists()) {
                from.renameTo(to);
                if (oldName.equalsIgnoreCase(mgr.getCurrentConfigName())) {
                    mgr.loadConfig(newName);
                }
            }
        } catch (Throwable ignored) {}
    }

    /* ============================================================ */
    /* popup inner class                                            */
    /* ============================================================ */

    /**
     * Small action menu spawned at click-point. Three rows:
     * Поделиться / Переименовать / Удалить. Smooth fade-in on
     * spawn AND smooth fade-out when dismissed (outside-click,
     * action picked, or sister popup opened on a different row).
     * Per-item hover is animated through its own DecelerateAnimation
     * so the highlight slides instead of snapping under the cursor.
     */
    private static final class KebabPopup {
        private static final float ITEM_H = 16.0F;
        private static final float WIDTH = 100.0F;
        private static final float TEXT_SIZE = 6.5F;
        private static final String[] ITEMS = {"Поделиться", "Переименовать", "Удалить"};

        private final String configName;
        private final float anchorX;
        private final float anchorY;
        private final ConfigsViewComponent owner;
        private final Animation fade;
        private final Animation[] itemHover;
        private boolean closing = false;

        KebabPopup(String configName, float anchorX, float anchorY, ConfigsViewComponent owner) {
            this.configName = configName;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.owner = owner;
            this.fade = new DecelerateAnimation().setMs(140).setValue(1);
            this.fade.setDirectionAndFinish(Direction.BACKWARDS);
            this.fade.setDirection(Direction.FORWARDS);
            this.itemHover = new Animation[ITEMS.length];
            for (int i = 0; i < ITEMS.length; i++) {
                itemHover[i] = new DecelerateAnimation().setMs(140).setValue(1);
                itemHover[i].setDirectionAndFinish(Direction.BACKWARDS);
            }
        }

        /** Begin the fade-out tween. The popup keeps rendering until
         *  {@link #isFullyClosed()} flips true so its highlight rows
         *  visibly trail off instead of disappearing in one frame. */
        void close() {
            this.closing = true;
            this.fade.setDirection(Direction.BACKWARDS);
            for (Animation anim : itemHover) {
                anim.setDirection(Direction.BACKWARDS);
            }
        }

        /** True while the popup is opening or fully open - i.e. it
         *  should still receive clicks and block menu drags. Once
         *  {@link #close()} is called this flips false even before
         *  the fade-out finishes, so the next click can reach the
         *  rows beneath. */
        boolean isInteractive() {
            return !closing;
        }

        boolean isFullyClosed() {
            return closing && fade.getOutputFloat() <= 0.001F;
        }

        void render(DrawContext context, int mouseX, int mouseY, float menuFadeAlpha) {
            MatrixStack matrix = context.getMatrices();
            float a = fade.getOutputFloat() * menuFadeAlpha;
            if (a <= 0.001F) return;

            // Layout: popup hangs DOWN-LEFT from the kebab so its
            // upper-right corner lines up with the dots. If it would
            // overflow the menu's bottom edge, anchor it ABOVE the
            // kebab instead.
            float popupX = anchorX - WIDTH;
            float popupY = anchorY + 4.0F;
            float popupH = ITEMS.length * ITEM_H;
            float menuBottom = owner.y + owner.height - 8.0F;
            if (popupY + popupH > menuBottom) {
                popupY = anchorY - popupH - 12.0F;
            }
            float menuLeft = owner.x + 6.0F;
            if (popupX < menuLeft) popupX = menuLeft;

            int fill = MenuStyle.mix(MenuStyle.PANEL_CONTENT, 0x000000FF, 0.10F);
            rectangle.render(ShapeProperties.create(matrix, popupX, popupY, WIDTH, popupH)
                    .round(5.0F).softness(1.0F).thickness(1.2F)
                    .outlineColor(MenuStyle.withAlpha(MenuStyle.BORDER, a))
                    .color(MenuStyle.withAlpha(fill, a * 0.97F))
                    .build());

            for (int i = 0; i < ITEMS.length; i++) {
                float iy = popupY + i * ITEM_H;
                // Only register hover while the popup is open;
                // closing popups should drain their highlight to 0
                // alongside the panel fade so the row glow doesn't
                // outlive the panel.
                boolean hover = !closing
                        && MathUtil.isHovered(mouseX, mouseY, popupX, iy, WIDTH, ITEM_H);
                itemHover[i].setDirection(hover ? Direction.FORWARDS : Direction.BACKWARDS);
                float hoverAlpha = itemHover[i].getOutputFloat();

                if (hoverAlpha > 0.001F) {
                    int hoverFill = MenuStyle.mix(MenuStyle.CARD_OPTIONS, MenuStyle.PANEL_CHIP, 0.35F);
                    rectangle.render(ShapeProperties.create(matrix, popupX, iy, WIDTH, ITEM_H)
                            .round(3.0F)
                            .color(MenuStyle.withAlpha(hoverFill, a * 0.85F * hoverAlpha))
                            .build());
                }
                int baseTextColor = MenuStyle.TEXT_PRIMARY;
                int textColor = i == 2
                        ? MenuStyle.mix(baseTextColor, 0xFFE05050, hoverAlpha)
                        : baseTextColor;
                MsdfRenderer.renderText(
                        MsdfFonts.bold(), ITEMS[i], TEXT_SIZE,
                        MenuStyle.withAlpha(textColor, a),
                        matrix.peek().getPositionMatrix(),
                        popupX + 10.0F,
                        MenuStyle.centerMsdfTextY(TEXT_SIZE, iy, ITEM_H),
                        0.0F
                );
            }
        }

        boolean handleClick(double mouseX, double mouseY) {
            float popupX = anchorX - WIDTH;
            float popupY = anchorY + 4.0F;
            float popupH = ITEMS.length * ITEM_H;
            float menuBottom = owner.y + owner.height - 8.0F;
            if (popupY + popupH > menuBottom) popupY = anchorY - popupH - 12.0F;
            float menuLeft = owner.x + 6.0F;
            if (popupX < menuLeft) popupX = menuLeft;

            if (!MathUtil.isHovered(mouseX, mouseY, popupX, popupY, WIDTH, popupH)) return false;
            int idx = (int) ((mouseY - popupY) / ITEM_H);
            if (idx < 0 || idx >= ITEMS.length) return false;

            switch (idx) {
                case 0 -> MenuScreen.INSTANCE.openConfigShareModal(configName);
                case 1 -> MenuScreen.INSTANCE.openConfigRenameModal(configName, owner::refreshTimestamps);
                case 2 -> {
                    ConfigManager mgr = ConfigManager.getInstance();
                    // Default is now a regular file - the manager
                    // recreates it on first save - so it's safe to
                    // delete from the UI too. Active-config delete
                    // is also fine: deleteConfig auto-switches to
                    // the next available config.
                    mgr.deleteConfig(configName);
                    owner.refreshTimestamps();
                }
            }
            return true;
        }
    }
}
