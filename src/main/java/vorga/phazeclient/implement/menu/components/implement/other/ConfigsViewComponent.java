package vorga.phazeclient.implement.menu.components.implement.other;

import vorga.phazeclient.base.util.render.GuiMatrix;

import org.joml.Matrix3x2fc;
import org.joml.Matrix3x2fStack;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
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

public class ConfigsViewComponent extends AbstractComponent {

    private static final float ROW_HEIGHT = 40.0F;
    private static final float ROW_GAP = 6.0F;
    private static final float ROW_RADIUS = 5.0F;
    private static final float ROW_PAD_X = 12.0F;
    private static final float TIMESTAMP_SIZE = 5.6F;
    private static final float NAME_SIZE = 7.0F;
    private static final float META_SIZE = 5.6F;

    private static final float ACTION_ICON_SIZE = 11.0F;
    private static final float ACTION_BUTTON_W = 22.0F;
    private static final float ACTION_BUTTON_GAP = 2.0F;
    private static final float ACTIONS_TRAIL_PAD = 6.0F;
    private static final float DOT_SIZE = 1.6F;
    private static final float DOT_GAP = 1.4F;

    private static final float NAME_DOT_SIZE = (16.7F * 44.0F / 256.0F) / 1.1F;
    private static final float NAME_DOT_TEXT_GAP = 2.0F;

    private static final float META_ICON_SIZE = 6.5F;
    private static final float META_ICON_SIZE_LARGE = 7.8F;
    private static final float META_ICON_GAP = 3.5F;
    private static final float META_GROUP_GAP = 12.0F;

    private static final float TOP_MARGIN = 50.0F;
    private static final float BOTTOM_MARGIN = 16.0F;
    private static final float SIDE_MARGIN = 12.0F;

    private final Animation openAnim = new DecelerateAnimation().setMs(1).setValue(1);
    private final Map<String, Animation> rowHoverAnims = new HashMap<>();

    private final Map<String, Animation> actionHoverAnims = new HashMap<>();

    private final Map<String, Animation> activeAnims = new HashMap<>();

    private KebabPopup popup = null;

    private boolean open = false;

    private final Map<String, String> timestampCache = new LinkedHashMap<>();
    private final Map<String, String> sizeCache = new LinkedHashMap<>();
    private final Map<String, Long> modifiedTimeCache = new LinkedHashMap<>();
    private final Map<String, String> relativeModifiedCache = new LinkedHashMap<>();
    private final Map<String, Boolean> importedCache = new LinkedHashMap<>();
    private final SimpleDateFormat tsFormat = new SimpleDateFormat("dd.MM HH:mm", Locale.ROOT);
    private long nextRelativeRefreshMs = 0L;

    private float scrollOffset = 0.0F;
    private float maxScrollOffset = 0.0F;

    public void open() {
        this.open = true;
        this.openAnim.setDirection(Direction.FORWARDS);
        if (this.popup != null) this.popup.close();
        this.popup = null;
        this.scrollOffset = 0.0F;
        refreshMetadataCaches();
    }

    public void close() {
        this.open = false;
        this.openAnim.setDirection(Direction.BACKWARDS);
        if (this.popup != null) this.popup.close();
    }

    public boolean isOpen() {
        return open || openAnim.getOutputFloat() > 0.001F;
    }

    private void refreshMetadataCaches() {
        timestampCache.clear();
        sizeCache.clear();
        modifiedTimeCache.clear();
        relativeModifiedCache.clear();
        importedCache.clear();
        ConfigManager mgr = ConfigManager.getInstance();
        long now = System.currentTimeMillis();
        for (String name : mgr.getConfigList()) {
            File file = mgr.getConfigFile(name);
            boolean exists = file.exists();
            long mtime = exists ? file.lastModified() : 0L;
            timestampCache.put(name, mtime > 0 ? tsFormat.format(new Date(mtime)) : "—");
            sizeCache.put(name, exists ? humanReadableSize(file.length()) : "—");
            modifiedTimeCache.put(name, mtime);
            relativeModifiedCache.put(name, humanReadableModified(now, mtime));
            importedCache.put(name, mgr.isImportedConfig(name));
        }
        nextRelativeRefreshMs = now + 1000L;
    }

    private void refreshRelativeModifiedCache() {
        long now = System.currentTimeMillis();
        if (now < nextRelativeRefreshMs) {
            return;
        }
        for (Map.Entry<String, Long> entry : modifiedTimeCache.entrySet()) {
            relativeModifiedCache.put(entry.getKey(), humanReadableModified(now, entry.getValue()));
        }
        nextRelativeRefreshMs = now + 1000L;
    }

    public void refreshAfterImport() {
        refreshMetadataCaches();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!open && openAnim.getOutputFloat() <= 0.001F) return;

        float fadeAlpha = openAnim.getOutputFloat() * globalAlpha;
        refreshRelativeModifiedCache();

        float listX = x + SIDE_MARGIN;
        float listW = width - SIDE_MARGIN * 2.0F;
        float listTop = y + TOP_MARGIN;
        float listBottom = y + height - BOTTOM_MARGIN;

        renderRows(context, mouseX, mouseY, listX, listTop, listW, listBottom, fadeAlpha);

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
        Matrix3x2fStack matrix = context.getMatrices();
        ConfigManager mgr = ConfigManager.getInstance();
        String[] names = mgr.getConfigList();

        float listH = listBottom - listY;
        float contentH = names.length * (ROW_HEIGHT + ROW_GAP) - ROW_GAP;
        maxScrollOffset = Math.max(0.0F, contentH - listH);
        if (scrollOffset < 0.0F) scrollOffset = 0.0F;
        if (scrollOffset > maxScrollOffset) scrollOffset = maxScrollOffset;

        context.enableScissor((int) listX, (int) listY,
                (int) (listX + listW), (int) listBottom);
        vorga.phazeclient.base.util.render.ScissorManager scissorManager =
                vorga.phazeclient.core.Main.getInstance().getScissorManager();
        scissorManager.push(GuiMatrix.mat4(matrix), listX, listY, listW, listBottom - listY);
        try {
            String authorLabel = resolveAuthorLabel();
            float rowY = listY - scrollOffset;
            for (String name : names) {
                if (rowY + ROW_HEIGHT > listY && rowY < listBottom) {
                    renderRow(matrix, mouseX, mouseY, listX, rowY, listW, name, authorLabel, fadeAlpha);
                }
                rowY += ROW_HEIGHT + ROW_GAP;
            }
        } finally {
            context.disableScissor();
            scissorManager.pop();
        }
    }

    private static final float ICON_AREA_W = 28.0F;

    private static final float ICON_SIZE = 19.6875F;

    private void renderRow(Matrix3x2fc matrix, int mouseX, int mouseY,
                           float listX, float rowY, float listW,
                           String name, String authorLabel, float fadeAlpha) {
        boolean hovered = MathUtil.isHovered(mouseX, mouseY, listX, rowY, listW, ROW_HEIGHT);
        if (hovered) {
            vorga.phazeclient.api.system.cursor.CursorManager.requestHand();
        }
        Animation rowAnim = rowHoverAnims.computeIfAbsent(name,
                k -> new DecelerateAnimation().setMs(160).setValue(1));
        rowAnim.setDirection(hovered ? Direction.FORWARDS : Direction.BACKWARDS);
        float h = rowAnim.getOutputFloat();

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

        boolean imported = importedCache.getOrDefault(name, Boolean.FALSE);
        String iconTexture = imported ? "textures/file_import.png" : "textures/file.png";
        float iconWidth = resolveUiIconWidth(iconTexture, ICON_SIZE);
        float iconX = listX + 9.0F + (ICON_AREA_W - 6.0F - iconWidth) * 0.5F;
        float iconY = rowY + (ROW_HEIGHT - ICON_SIZE) * 0.5F;
        renderUiIcon(matrix, iconTexture, iconX, iconY, iconWidth, ICON_SIZE,
                MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, fadeAlpha * 0.85F));

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
                GuiMatrix.mat4(matrix),
                textX,
                timestampY,
                0.0F
        );

        int nameColor = MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, fadeAlpha);
        int authorColor = MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, fadeAlpha * 0.85F);
        MsdfRenderer.renderText(
                MsdfFonts.bold(), name, NAME_SIZE,
                nameColor,
                GuiMatrix.mat4(matrix),
                textX,
                nameY,
                0.0F
        );
        float nameWidth = MsdfFonts.bold().getWidth(name, NAME_SIZE);
        float dotX = textX + nameWidth + NAME_DOT_TEXT_GAP;

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
                    GuiMatrix.mat4(matrix),
                    authorX,
                    nameY,
                    0.0F
            );
        }

        renderRowMeta(matrix, textX, metaY, name, imported, fadeAlpha);

        renderActionButtons(matrix, mouseX, mouseY, listX, rowY, listW, name, fadeAlpha);
    }

    private void renderRowMeta(Matrix3x2fc matrix, float startX, float metaY,
                               String configName, boolean imported, float fadeAlpha) {
        float cursorX = startX;
        int metaColor = MenuStyle.withAlpha(MenuStyle.TEXT_MUTED, fadeAlpha * 0.95F);

        cursorX = renderMetaChip(matrix, cursorX, metaY,
                "textures/size.png",
                Lang.translate("Size") + ": " + sizeCache.getOrDefault(configName, "—"),
                metaColor, fadeAlpha, 1.0F, META_ICON_SIZE);
        cursorX += META_GROUP_GAP;

        if (imported) {

            renderMetaChip(matrix, cursorX, metaY,
                    "textures/cloud.png",
                    Lang.translate("Imported from cloud"),
                    metaColor, fadeAlpha, 1.5F, META_ICON_SIZE_LARGE);
        } else {
            renderMetaChip(matrix, cursorX, metaY,
                    "textures/clock.png",
                    Lang.translate("Last modified") + ": " + relativeModifiedCache.getOrDefault(configName, "—"),
                    metaColor, fadeAlpha, 1.5F, META_ICON_SIZE_LARGE);
        }
    }

    private float renderMetaChip(Matrix3x2fc matrix, float startX, float baselineY,
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
                GuiMatrix.mat4(matrix),
                labelX, baselineY + labelDeltaY, 0.0F
        );
        return labelX + MsdfFonts.bold().getWidth(label, META_SIZE);
    }

    private float resolveUiIconWidth(String iconTexture, float iconHeight) {
        float aspectRatio = UiMsdfIconAtlas.resolveAspectRatio(Identifier.ofVanilla(iconTexture));
        return iconHeight * Math.max(0.0001F, aspectRatio);
    }

    private void renderUiIcon(Matrix3x2fc matrix, String iconTexture, float x, float y, float width, float height, int color) {

        image.setTexture(iconTexture)
                .render(ShapeProperties.create(matrix, x, y, height, width)
                        .color(color)
                        .build());
    }

    private String humanReadableSize(long bytes) {
        if (bytes < 0L) return "—";
        if (bytes < 1024L) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024.0) return String.format(Locale.ROOT, "%.1f KB", kb);
        double mb = kb / 1024.0;
        return String.format(Locale.ROOT, "%.1f MB", mb);
    }

    private String humanReadableModified(long now, long modifiedAt) {
        if (modifiedAt <= 0L) return "—";
        long delta = now - modifiedAt;
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

    private enum ActionKind {
        RENAME("textures/edit.png"),
        SHARE("textures/share.png"),
        DELETE("textures/trash.png");

        final String texture;
        ActionKind(String texture) {
            this.texture = texture;
        }
    }

    private void renderActionButtons(Matrix3x2fc matrix, int mouseX, int mouseY,
                                     float listX, float rowY, float listW,
                                     String configName, float fadeAlpha) {
        ActionKind[] kinds = ActionKind.values();
        float totalW = kinds.length * ACTION_BUTTON_W
                + (kinds.length - 1) * ACTION_BUTTON_GAP;
        float startX = listX + listW - totalW - ACTIONS_TRAIL_PAD;

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

    private void renderActionButton(Matrix3x2fc matrix, int mouseX, int mouseY,
                                    float btnX, float rowY, String configName,
                                    ActionKind kind, float fadeAlpha) {
        boolean hover = MathUtil.isHovered(mouseX, mouseY, btnX, rowY, ACTION_BUTTON_W, ROW_HEIGHT);
        if (hover) {
            vorga.phazeclient.api.system.cursor.CursorManager.requestHand();
        }
        String key = configName + "::" + kind.name();
        Animation a = actionHoverAnims.computeIfAbsent(key,
                k -> new DecelerateAnimation().setMs(140).setValue(1));
        a.setDirection(hover ? Direction.FORWARDS : Direction.BACKWARDS);
        float h = a.getOutputFloat();

        int color = kind == ActionKind.DELETE
                ? MenuStyle.mix(0xFFB55F73, 0xFFFF9BAA, 0.30F + h * 0.55F)
                : MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, 0.30F + h * 0.55F);
        float iconX = btnX + (ACTION_BUTTON_W - ACTION_ICON_SIZE) * 0.5F;
        float iconY = rowY + (ROW_HEIGHT - ACTION_ICON_SIZE) * 0.5F;
        image.setTexture(kind.texture)
                .render(ShapeProperties.create(matrix, iconX, iconY, ACTION_ICON_SIZE, ACTION_ICON_SIZE)
                        .color(MenuStyle.withAlpha(color, fadeAlpha))
                        .build());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) return false;
        if (button != 0) return false;

        if (popup != null && popup.isInteractive()) {
            if (popup.handleClick(mouseX, mouseY)) {
                popup.close();
                return true;
            }

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
                    refreshMetadataCaches();
                    return true;
                }
            }
            rowY += ROW_HEIGHT + ROW_GAP;
        }
        return false;
    }

    private void handleActionClick(String configName, ActionKind kind) {
        switch (kind) {
            case RENAME -> MenuScreen.INSTANCE.openConfigRenameModal(configName, this::refreshMetadataCaches);
            case SHARE -> MenuScreen.INSTANCE.openConfigShareModal(configName);
            case DELETE -> {
                ConfigManager.getInstance().deleteConfig(configName);
                refreshMetadataCaches();
            }
        }
    }

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

        void close() {
            this.closing = true;
            this.fade.setDirection(Direction.BACKWARDS);
            for (Animation anim : itemHover) {
                anim.setDirection(Direction.BACKWARDS);
            }
        }

        boolean isInteractive() {
            return !closing;
        }

        boolean isFullyClosed() {
            return closing && fade.getOutputFloat() <= 0.001F;
        }

        void render(DrawContext context, int mouseX, int mouseY, float menuFadeAlpha) {
            Matrix3x2fStack matrix = context.getMatrices();
            float a = fade.getOutputFloat() * menuFadeAlpha;
            if (a <= 0.001F) return;

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
                        GuiMatrix.mat4(matrix),
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
                case 1 -> MenuScreen.INSTANCE.openConfigRenameModal(configName, owner::refreshMetadataCaches);
                case 2 -> {
                    ConfigManager mgr = ConfigManager.getInstance();

                    mgr.deleteConfig(configName);
                    owner.refreshMetadataCaches();
                }
            }
            return true;
        }
    }
}
