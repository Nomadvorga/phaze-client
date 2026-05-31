package vorga.phazeclient.implement.menu.components.implement.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.config.ConfigManager;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.UiMsdfIconAtlas;
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

    private static final float ROW_HEIGHT = 40.0F;
    private static final float ROW_GAP = 6.0F;
    private static final float ROW_RADIUS = 5.0F;
    private static final float ROW_PAD_X = 12.0F;
    private static final float TIMESTAMP_SIZE = 5.6F;
    private static final float NAME_SIZE = 7.0F;
    private static final float META_SIZE = 5.6F;
    /** Single inline action button. Three of them stack right-edge:
     *  rename, share, delete. Hover-anim per (config, kind) so each
     *  button glows independently when the cursor lands on it. */
    private static final float ACTION_ICON_SIZE = 11.0F;
    private static final float ACTION_BUTTON_W = 22.0F;
    private static final float ACTION_BUTTON_GAP = 2.0F;
    private static final float ACTIONS_TRAIL_PAD = 6.0F;
    private static final float DOT_SIZE = 1.6F;
    private static final float DOT_GAP = 1.4F;
    /** Inline separator between config name and author label.
     *  Drawn procedurally as a circle so its visual size and text
     *  spacing stay exact instead of depending on dot.png's large
     *  transparent padding. */
    private static final float NAME_DOT_SIZE = (16.7F * 44.0F / 256.0F) / 1.1F;
    private static final float NAME_DOT_TEXT_GAP = 2.0F;
    /** Size of the inline meta icons (size, clock for last-modified,
     *  cloud for imported). The cloud / clock icons are visually
     *  lighter than the size icon, so they're rendered larger to
     *  read at the same weight in the strip. */
    private static final float META_ICON_SIZE = 6.5F;
    private static final float META_ICON_SIZE_LARGE = 7.8F;
    private static final float META_ICON_GAP = 3.5F;
    private static final float META_GROUP_GAP = 12.0F;

    /** Top + bottom inset from the menu's content area. Keeps the
     *  list out from under the top tabs and the bottom blur frame. */
    private static final float TOP_MARGIN = 50.0F;
    private static final float BOTTOM_MARGIN = 16.0F;
    private static final float SIDE_MARGIN = 12.0F;

    private final Animation openAnim = new DecelerateAnimation().setMs(1).setValue(1);
    private final Map<String, Animation> rowHoverAnims = new HashMap<>();
    /** Hover animations per (config name + action kind) so each
     *  inline icon button (rename / share / delete) animates
     *  independently. Key format: {@code <name>::<kind>}. */
    private final Map<String, Animation> actionHoverAnims = new HashMap<>();
    /** Per-row "is this the active config?" animation. Tweens the
     *  outline colour from the standard BORDER to the green-tinted
     *  CHIP_ACTIVE mix when a config becomes active and back when
     *  another config takes its place. Same approach the module
     *  card uses for its enable/disable outline pulse, so the two
     *  surfaces feel like one design language. */
    private final Map<String, Animation> activeAnims = new HashMap<>();

    /** Popup attached to the row whose kebab the user clicked.
     *  Lives until its fade-out animation finishes so close-clicks
     *  visually trail off instead of snapping.
     *
     *  <p>Kept around because the share / rename modals are still
     *  spawned via the same constants the popup formerly used. The
     *  popup itself is no longer rendered or instantiated - the row
     *  shows three inline icon buttons (rename / share / delete)
     *  instead. */
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

    /** Left padding inside the row, before the type icon. */
    private static final float ICON_AREA_W = 28.0F;
    /** Visual size of the file / file_import icon. Smaller than the
     *  area itself so the icon has comfortable breathing room. */
    private static final float ICON_SIZE = 15.75F;

    private void renderRow(MatrixStack matrix, int mouseX, int mouseY,
                           float listX, float rowY, float listW,
                           String name, String authorLabel, float fadeAlpha) {
        boolean hovered = MathUtil.isHovered(mouseX, mouseY, listX, rowY, listW, ROW_HEIGHT);
        Animation rowAnim = rowHoverAnims.computeIfAbsent(name,
                k -> new DecelerateAnimation().setMs(160).setValue(1));
        rowAnim.setDirection(hovered ? Direction.FORWARDS : Direction.BACKWARDS);
        float h = rowAnim.getOutputFloat();

        // Active config gets the same green-tinted outline that an
        // enabled module card uses (BORDER → CHIP_ACTIVE 75% mix);
        // every other row keeps the standard BORDER outline like a
        // disabled module card. The per-row activeAnim tweens the
        // mix factor, so a row visibly fades INTO the active look
        // when the user picks it (and the previously-active row
        // fades back out at the same time) instead of snapping.
        boolean isActive = name.equalsIgnoreCase(ConfigManager.getInstance().getCurrentConfigName());
        Animation activeAnim = activeAnims.computeIfAbsent(name,
                k -> new DecelerateAnimation().setMs(300).setValue(1));
        activeAnim.setDirection(isActive ? Direction.FORWARDS : Direction.BACKWARDS);
        float activeT = activeAnim.getOutputFloat();
        int baseOutline = MenuStyle.mix(MenuStyle.BORDER, MenuStyle.CHIP_ACTIVE, activeT * 0.75F);
        int fill = MenuStyle.mix(MenuStyle.PANEL_CHIP, MenuStyle.CARD_OPTIONS, 0.30F + h * 0.10F);
        int outline = MenuStyle.mix(baseOutline, MenuStyle.BORDER_LIGHT, h * 0.40F);
        rectangle.render(ShapeProperties.create(matrix, listX, rowY, listW, ROW_HEIGHT)
                .round(ROW_RADIUS).thickness(3.2F)
                .outlineColor(MenuStyle.withAlpha(outline, fadeAlpha))
                .color(MenuStyle.withAlpha(fill, fadeAlpha * 0.92F))
                .build());

        // Type icon on the left. {@code file_import.png} for configs
        // imported from a server share-key, {@code file.png} for
        // locally created configs. The marker comes from
        // {@link ConfigManager#isImportedConfig}.
        boolean imported = ConfigManager.getInstance().isImportedConfig(name);
        String iconTexture = imported ? "textures/file_import.png" : "textures/file.png";
        float iconWidth = resolveUiIconWidth(iconTexture, ICON_SIZE);
        float iconX = listX + 9.0F + (ICON_AREA_W - 6.0F - iconWidth) * 0.5F;
        float iconY = rowY + (ROW_HEIGHT - ICON_SIZE) * 0.5F;
        renderUiIcon(matrix, iconTexture, iconX, iconY, iconWidth, ICON_SIZE,
                MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, fadeAlpha * 0.85F));

        // Top: timestamp
        // Middle: name • author (with dot.png separator)
        // Bottom: size + last-modified / imported-from-cloud
        float textX = listX + ROW_PAD_X + ICON_AREA_W;
        float lineGap = 1.6F;
        float metaGap = 1.6F;
        float blockHeight = TIMESTAMP_SIZE + lineGap + NAME_SIZE + metaGap + META_SIZE;
        float timestampY = rowY + (ROW_HEIGHT - blockHeight) * 0.5F;
        float nameY = timestampY + TIMESTAMP_SIZE + lineGap;
        float metaY = nameY + NAME_SIZE + metaGap;

        String ts = timestampCache.getOrDefault(name, "—");
        MsdfRenderer.renderText(
                MsdfFonts.bold(), ts, TIMESTAMP_SIZE,
                MenuStyle.withAlpha(MenuStyle.TEXT_MUTED, fadeAlpha * 0.85F),
                matrix.peek().getPositionMatrix(),
                textX,
                timestampY,
                0.0F
        );

        // Name (bold) + dot separator + author (lighter).
        int nameColor = MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, fadeAlpha);
        int authorColor = MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, fadeAlpha * 0.85F);
        MsdfRenderer.renderText(
                MsdfFonts.bold(), name, NAME_SIZE,
                nameColor,
                matrix.peek().getPositionMatrix(),
                textX,
                nameY,
                0.0F
        );
        float nameWidth = MsdfFonts.bold().getWidth(name, NAME_SIZE);
        float dotX = textX + nameWidth + NAME_DOT_TEXT_GAP;
        // Centre against the cap-height middle (~0.42 of the font
        // size from the text's top) instead of the full bbox - the
        // bbox includes descender room that pushes the dot below
        // the letters' optical mid-line.
        float dotY = nameY + NAME_SIZE * 0.42F - NAME_DOT_SIZE * 0.5F;
        rectangle.render(ShapeProperties.create(matrix, dotX, dotY, NAME_DOT_SIZE, NAME_DOT_SIZE)
                .round(NAME_DOT_SIZE * 0.5F)
                .color(MenuStyle.withAlpha(MenuStyle.TEXT_MUTED, fadeAlpha * 0.85F))
                .build());
        float authorX = dotX + NAME_DOT_SIZE + NAME_DOT_TEXT_GAP;
        if (authorLabel != null && !authorLabel.isEmpty()) {
            MsdfRenderer.renderText(
                    MsdfFonts.bold(), authorLabel, NAME_SIZE,
                    authorColor,
                    matrix.peek().getPositionMatrix(),
                    authorX,
                    nameY,
                    0.0F
            );
        }

        // Bottom meta line. Imported configs swap the
        // "Last modified ..." chip for an "Imported from cloud"
        // marker so the user can spot remote-origin entries at a
        // glance without hovering. Size is shown for both.
        renderRowMeta(matrix, textX, metaY, name, imported, fadeAlpha);

        // Three inline action buttons on the right side: rename →
        // share (create key) → delete, in that visual order. Each
        // button has its own hover animation and click hit-area.
        // The buttons sit RIGHT-aligned with a small trailing pad
        // so they don't kiss the row's outer border.
        renderActionButtons(matrix, mouseX, mouseY, listX, rowY, listW, name, fadeAlpha);
    }

    /**
     * Bottom-line meta strip: size + (imported badge OR last-modified
     * humanised). Each chip is an inline icon followed by a label,
     * separated by a fixed gap so the strip layout stays predictable
     * across different label widths.
     */
    private void renderRowMeta(MatrixStack matrix, float startX, float metaY,
                               String configName, boolean imported, float fadeAlpha) {
        float cursorX = startX;
        int metaColor = MenuStyle.withAlpha(MenuStyle.TEXT_MUTED, fadeAlpha * 0.95F);

        // Size chip - dedicated size.png icon, tinted with the same
        // muted text colour so it reads as part of the meta strip
        // instead of standing out as a stray accent. Label nudged
        // 1px down so the text sits on the icon's optical centre.
        cursorX = renderMetaChip(matrix, cursorX, metaY,
                "textures/size.png",
                Lang.translate("Size") + ": " + humanReadableSize(configName),
                metaColor, fadeAlpha, 1.0F, META_ICON_SIZE);
        cursorX += META_GROUP_GAP;

        if (imported) {
            // Imported chip - cloud-arrow-down. Icon at 1.5× the
            // base meta-icon size (the cloud glyph reads lighter at
            // 6.5px); text nudged down 1.5px to sit on the icon's
            // visual centre line.
            renderMetaChip(matrix, cursorX, metaY,
                    "textures/cloud.png",
                    Lang.translate("Imported from cloud"),
                    metaColor, fadeAlpha, 1.5F, META_ICON_SIZE_LARGE);
        } else {
            renderMetaChip(matrix, cursorX, metaY,
                    "textures/clock.png",
                    Lang.translate("Last modified") + ": " + humanReadableModified(configName),
                    metaColor, fadeAlpha, 1.5F, META_ICON_SIZE_LARGE);
        }
    }

    /** Renders one icon+label chip and returns the x coordinate
     *  immediately after the label, so the caller can append the
     *  next chip with a fixed group gap. {@code labelDeltaY} nudges
     *  the text vertically without moving the icon - useful when an
     *  icon is asymmetrically weighted in its bbox. {@code iconSize}
     *  picks between the standard 6.5px rendering and the 1.5×
     *  oversized variant for the cloud / clock glyphs. */
    private float renderMetaChip(MatrixStack matrix, float startX, float baselineY,
                                 String iconTexture, String label,
                                 int color, float fadeAlpha,
                                 float labelDeltaY, float iconSize) {
        float iconWidth = resolveUiIconWidth(iconTexture, iconSize);
        float iconY = baselineY + (META_SIZE - iconSize) * 0.5F + 0.4F;
        renderUiIcon(matrix, iconTexture, startX, iconY, iconWidth, iconSize, color);
        float labelX = startX + iconWidth + META_ICON_GAP;
        MsdfRenderer.renderText(
                MsdfFonts.bold(), label, META_SIZE,
                color,
                matrix.peek().getPositionMatrix(),
                labelX, baselineY + labelDeltaY, 0.0F
        );
        return labelX + MsdfFonts.bold().getWidth(label, META_SIZE);
    }

    private float resolveUiIconWidth(String iconTexture, float iconHeight) {
        float aspectRatio = UiMsdfIconAtlas.resolveAspectRatio(Identifier.ofVanilla(iconTexture));
        return iconHeight * Math.max(0.0001F, aspectRatio);
    }

    private void renderUiIcon(MatrixStack matrix, String iconTexture, float x, float y, float width, float height, int color) {
        // Image.render still uses the legacy swapped width/height convention,
        // so pass the box dimensions in that order to keep non-square icons
        // aligned without stretching in Configs rows.
        image.setTexture(iconTexture)
                .render(ShapeProperties.create(matrix, x, y, height, width)
                        .color(color)
                        .build());
    }

    private String humanReadableSize(String configName) {
        try {
            File f = ConfigManager.getInstance().getConfigFile(configName);
            if (!f.exists()) return "—";
            long bytes = f.length();
            if (bytes < 1024L) return bytes + " B";
            double kb = bytes / 1024.0;
            if (kb < 1024.0) return String.format(Locale.ROOT, "%.1f KB", kb);
            double mb = kb / 1024.0;
            return String.format(Locale.ROOT, "%.1f MB", mb);
        } catch (Throwable ignored) {
            return "—";
        }
    }

    private String humanReadableModified(String configName) {
        try {
            File f = ConfigManager.getInstance().getConfigFile(configName);
            if (!f.exists()) return "—";
            long delta = System.currentTimeMillis() - f.lastModified();
            if (delta < 0L) delta = 0L;
            long sec = delta / 1000L;
            if (sec < 60L) return Lang.translate("just now");
            long min = sec / 60L;
            if (min < 60L) return formatRelativeAgo(min, "minute");
            long hr = min / 60L;
            if (hr < 24L) return formatRelativeAgo(hr, "hour");
            long day = hr / 24L;
            if (day < 30L) return formatRelativeAgo(day, "day");
            long mon = day / 30L;
            if (mon < 12L) return formatRelativeAgo(mon, "month");
            long yr = mon / 12L;
            return formatRelativeAgo(yr, "year");
        } catch (Throwable ignored) {
            return "—";
        }
    }

    private String formatRelativeAgo(long value, String unit) {
        if (!Lang.RU.equals(Lang.getActive())) {
            return value + " " + unit + (value == 1L ? " ago" : "s ago");
        }
        return switch (unit) {
            case "minute" -> formatRelativeAgoRu(value, "минуту", "минуты", "минут");
            case "hour" -> formatRelativeAgoRu(value, "час", "часа", "часов");
            case "day" -> formatRelativeAgoRu(value, "день", "дня", "дней");
            case "month" -> formatRelativeAgoRu(value, "месяц", "месяца", "месяцев");
            case "year" -> formatRelativeAgoRu(value, "год", "года", "лет");
            default -> value + " " + unit;
        };
    }

    private String formatRelativeAgoRu(long value, String singular, String paucal, String plural) {
        return value + " " + russianPlural(value, singular, paucal, plural) + " назад";
    }

    private String russianPlural(long value, String singular, String paucal, String plural) {
        long mod100 = value % 100L;
        long mod10 = value % 10L;
        if (mod100 >= 11L && mod100 <= 14L) return plural;
        if (mod10 == 1L) return singular;
        if (mod10 >= 2L && mod10 <= 4L) return paucal;
        return plural;
    }

    /** Visual order of the inline action buttons on every row.
     *  Indexed by the {@link ActionKind} ordinal. The kebab popup's
     *  legacy constants ({@code "kebab.share"} etc.) still drive the
     *  share / rename modals, but the row UI itself now uses
     *  these icons instead. */
    private enum ActionKind {
        RENAME("textures/edit.png"),
        SHARE("textures/share.png"),
        DELETE("textures/trash.png");

        final String texture;
        ActionKind(String texture) {
            this.texture = texture;
        }
    }

    private void renderActionButtons(MatrixStack matrix, int mouseX, int mouseY,
                                     float listX, float rowY, float listW,
                                     String configName, float fadeAlpha) {
        ActionKind[] kinds = ActionKind.values();
        float totalW = kinds.length * ACTION_BUTTON_W
                + (kinds.length - 1) * ACTION_BUTTON_GAP;
        float startX = listX + listW - totalW - ACTIONS_TRAIL_PAD;

        // Group container behind the three buttons. Slightly inset
        // top/bottom (centered on the row) with a soft rounded
        // panel so the buttons read as a unit instead of three
        // floating glyphs. Background is fully opaque per the user
        // request, with the panel's border tinted by the menu's
        // standard outline so it sits on the row without fighting
        // the row's own outline.
        float groupPadX = 2.0F;
        float groupPadY = 10.0F;
        float groupX = startX - groupPadX;
        float groupY = rowY + groupPadY;
        float groupW = totalW + groupPadX * 2.0F;
        float groupH = ROW_HEIGHT - groupPadY * 2.0F;
        rectangle.render(ShapeProperties.create(matrix, groupX, groupY, groupW, groupH)
                .round(4.0F).thickness(1.0F)
                .outlineColor(MenuStyle.withAlpha(MenuStyle.BORDER, fadeAlpha))
                .color(MenuStyle.withAlpha(MenuStyle.PANEL_BG, fadeAlpha))
                .build());

        for (int i = 0; i < kinds.length; i++) {
            ActionKind kind = kinds[i];
            float btnX = startX + i * (ACTION_BUTTON_W + ACTION_BUTTON_GAP);
            renderActionButton(matrix, mouseX, mouseY, btnX, rowY, configName, kind, fadeAlpha);
        }
    }

    private void renderActionButton(MatrixStack matrix, int mouseX, int mouseY,
                                    float btnX, float rowY, String configName,
                                    ActionKind kind, float fadeAlpha) {
        boolean hover = MathUtil.isHovered(mouseX, mouseY, btnX, rowY, ACTION_BUTTON_W, ROW_HEIGHT);
        String key = configName + "::" + kind.name();
        Animation a = actionHoverAnims.computeIfAbsent(key,
                k -> new DecelerateAnimation().setMs(140).setValue(1));
        a.setDirection(hover ? Direction.FORWARDS : Direction.BACKWARDS);
        float h = a.getOutputFloat();

        int color = MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, 0.30F + h * 0.55F);
        float iconX = btnX + (ACTION_BUTTON_W - ACTION_ICON_SIZE) * 0.5F;
        float iconY = rowY + (ROW_HEIGHT - ACTION_ICON_SIZE) * 0.5F;
        image.setTexture(kind.texture)
                .render(ShapeProperties.create(matrix, iconX, iconY, ACTION_ICON_SIZE, ACTION_ICON_SIZE)
                        .color(MenuStyle.withAlpha(color, fadeAlpha))
                        .build());
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
        ActionKind[] kinds = ActionKind.values();
        float actionsTotalW = kinds.length * ACTION_BUTTON_W
                + (kinds.length - 1) * ACTION_BUTTON_GAP;
        float actionsStartX = listX + listW - actionsTotalW - ACTIONS_TRAIL_PAD;
        float bodyEndX = actionsStartX - 2.0F;
        for (String name : names) {
            if (rowY + ROW_HEIGHT > listTop && rowY < listBottom) {
                // Inline action buttons take precedence over the
                // body-click "load this config" hit-area, so the
                // user can rename / share / delete without the row
                // also activating itself underneath.
                ActionKind hit = null;
                for (int i = 0; i < kinds.length; i++) {
                    float btnX = actionsStartX + i * (ACTION_BUTTON_W + ACTION_BUTTON_GAP);
                    if (MathUtil.isHovered(mouseX, mouseY, btnX, rowY, ACTION_BUTTON_W, ROW_HEIGHT)) {
                        hit = kinds[i];
                        break;
                    }
                }
                if (hit != null) {
                    playButtonClickSound();
                    handleActionClick(name, hit);
                    return true;
                }

                if (MathUtil.isHovered(mouseX, mouseY, listX, rowY, bodyEndX - listX, ROW_HEIGHT)) {
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

    /** Routes an inline action button click to the appropriate
     *  flow. Renames go through the rename modal so the user can
     *  type a new name; share spawns the share-key creation modal;
     *  delete is one-shot through {@link ConfigManager#deleteConfig}.
     *  All three mirror what the old kebab popup used to do, just
     *  without the popup intermediate UI. */
    private void handleActionClick(String configName, ActionKind kind) {
        switch (kind) {
            case RENAME -> MenuScreen.INSTANCE.openConfigRenameModal(configName, this::refreshTimestamps);
            case SHARE -> MenuScreen.INSTANCE.openConfigShareModal(configName);
            case DELETE -> {
                ConfigManager.getInstance().deleteConfig(configName);
                refreshTimestamps();
            }
        }
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
        private static final String[] ITEMS = {"kebab.share", "kebab.rename", "kebab.delete"};

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
                        MsdfFonts.bold(), Lang.t(ITEMS[i]), TEXT_SIZE,
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
