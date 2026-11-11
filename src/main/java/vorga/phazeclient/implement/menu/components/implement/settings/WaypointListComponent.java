package vorga.phazeclient.implement.menu.components.implement.settings;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import vorga.phazeclient.api.feature.module.setting.implement.WaypointListSetting;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.UiMsdfIconAtlas;
import vorga.phazeclient.implement.menu.components.implement.other.WaypointEditorModal;
import vorga.phazeclient.implement.messenger.ChatWorld;
import vorga.phazeclient.implement.waypoints.Waypoint;
import vorga.phazeclient.implement.waypoints.WaypointStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The current world's saved waypoints: a row each opens the editor, "Add" drafts one at the player. */
public class WaypointListComponent extends AbstractSettingComponent {
    private static final float HEADER_H = 24.0F;
    private static final float ROW_H = 21.0F;
    private static final float EMPTY_H = 22.0F;
    private static final float INSET = 6.0F;
    private static final float BUTTON_H = 14.0F;
    private static final int HOVER_MS = 160;
    private static final Identifier EDIT_ICON = Identifier.ofVanilla("textures/edit.png");

    private final Map<Long, Animation> rowHovers = new HashMap<>();
    private final Animation addHover = new DecelerateAnimation().setMs(HOVER_MS).setValue(1);
    private List<Waypoint> rows = List.of();
    private String rowsWorld;
    private int rowsVersion = -1;

    public WaypointListComponent(WaypointListSetting setting) {
        super(setting);
        addHover.setDirectionAndFinish(Direction.BACKWARDS);
    }

    private void refresh() {
        String world = WaypointStore.currentWorldKey();
        WaypointStore store = WaypointStore.get();
        if (store.version() == rowsVersion && java.util.Objects.equals(world, rowsWorld)) return;
        rows = store.list(world);
        rowsWorld = world;
        rowsVersion = store.version();
        if (rowHovers.size() > 256) rowHovers.clear();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateVisibilityAnimation();
        refresh();
        height = HEADER_H + (rows.isEmpty() ? EMPTY_H : rows.size() * ROW_H) + INSET;
        renderSettingCard(context, 0.0F, 0.0F);
        MatrixStack matrices = context.getMatrices();

        text(matrices, MsdfFonts.bold(), getSetting().getName(), 6.6F, x + 10.0F,
                MenuStyle.centerMsdfTextY(6.6F, y, HEADER_H), primaryText());
        String counter = rows.size() + "/" + WaypointStore.MAX_PER_WORLD;
        float titleW = MsdfFonts.bold().getWidth(getSetting().getName(), 6.6F);
        text(matrices, MsdfFonts.medium(), counter, 5.4F, x + 15.0F + titleW,
                MenuStyle.centerMsdfTextY(5.4F, y, HEADER_H) + 0.5F, mutedText());

        boolean canAdd = rowsWorld != null && rows.size() < WaypointStore.MAX_PER_WORLD;
        float[] add = addButton();
        boolean addHovered = canAdd && inside(mouseX, mouseY, add);
        addHover.setDirection(addHovered ? Direction.FORWARDS : Direction.BACKWARDS);
        if (addHovered) CursorManager.requestHand();
        float hover = addHover.getOutputFloat();
        int fill = MenuStyle.mix(MenuStyle.CARD_INNER, MenuStyle.CHIP_ACTIVE, canAdd ? 0.35F + hover * 0.25F : 0.0F);
        rectangle.render(ShapeProperties.create(matrices, add[0], add[1], add[2], add[3])
                .round(3.5F).thickness(1.2F)
                .outlineColor(MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.BORDER, MenuStyle.BORDER_LIGHT, hover), currentAlpha))
                .color(MenuStyle.withAlpha(fill, currentAlpha)).build());
        int addText = canAdd ? MenuStyle.mix(MenuStyle.TEXT_PRIMARY, 0xFFFFFFFF, hover * 0.2F) : MenuStyle.TEXT_MUTED;
        float plusX = add[0] + 7.5F, plusY = add[1] + add[3] / 2.0F;
        rectangle.render(ShapeProperties.create(matrices, plusX - 2.75F, plusY - 0.55F, 5.5F, 1.1F)
                .round(0.5F).color(MenuStyle.withAlpha(addText, currentAlpha)).build());
        rectangle.render(ShapeProperties.create(matrices, plusX - 0.55F, plusY - 2.75F, 1.1F, 5.5F)
                .round(0.5F).color(MenuStyle.withAlpha(addText, currentAlpha)).build());
        text(matrices, MsdfFonts.bold(), Lang.t("waypoints.add"), 5.6F, add[0] + 13.0F,
                MenuStyle.centerMsdfTextY(5.6F, add[1], add[3]), MenuStyle.withAlpha(addText, currentAlpha));

        float listY = y + HEADER_H;
        if (rows.isEmpty()) {
            String empty = Lang.t(rowsWorld == null ? "waypoints.join_world" : "waypoints.empty");
            text(matrices, MsdfFonts.medium(), empty, 5.8F,
                    MenuStyle.centerMsdfTextX(MsdfFonts.medium(), empty, 5.8F, x, width),
                    MenuStyle.centerMsdfTextY(5.8F, listY, EMPTY_H), mutedText());
            updateLabelHover(mouseX, mouseY);
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        String here = ChatWorld.dimension();
        MenuScreen menu = MenuScreen.INSTANCE;
        float visibleTop = menu.y + 60.0F;
        float visibleBottom = menu.y + menu.height;
        for (int index = 0; index < rows.size(); index++) {
            float rowY = listY + index * ROW_H;
            if (rowY + ROW_H < visibleTop || rowY > visibleBottom) continue;
            renderRow(context, rows.get(index), x + INSET, rowY, width - INSET * 2.0F, mouseX, mouseY, client, here);
        }
        updateLabelHover(mouseX, mouseY);
    }

    private void renderRow(DrawContext context, Waypoint waypoint, float rowX, float rowY, float rowW,
                           int mouseX, int mouseY, MinecraftClient client, String here) {
        MatrixStack matrices = context.getMatrices();
        boolean hovered = MathUtil.isHovered(mouseX, mouseY, rowX, rowY, rowW, ROW_H - 2.0F);
        Animation animation = rowHovers.computeIfAbsent(waypoint.id(), ignored -> new DecelerateAnimation()
                .setMs(HOVER_MS).setValue(1).setDirectionAndFinish(Direction.BACKWARDS));
        animation.setDirection(hovered ? Direction.FORWARDS : Direction.BACKWARDS);
        if (hovered) CursorManager.requestHand();
        float hover = animation.getOutputFloat();
        if (hover > 0.0F) {
            rectangle.render(ShapeProperties.create(matrices, rowX, rowY, rowW, ROW_H - 2.0F)
                    .round(3.5F).color(MenuStyle.withAlpha(MenuStyle.CARD_INNER, currentAlpha * hover)).build());
        }
        boolean sameDimension = waypoint.dimension().equals(here);
        float dot = 7.0F;
        rectangle.render(ShapeProperties.create(matrices, rowX + 6.0F, rowY + (ROW_H - 2.0F - dot) / 2.0F, dot, dot)
                .round(dot / 2.0F).color(MenuStyle.withAlpha(waypoint.color(), currentAlpha * (sameDimension ? 1.0F : 0.55F))).build());

        String distance = "";
        if (sameDimension && client.player != null) {
            double dx = waypoint.blockX() + 0.5D - client.player.getX();
            double dy = waypoint.blockY() + 0.5D - client.player.getY();
            double dz = waypoint.blockZ() + 0.5D - client.player.getZ();
            distance = (int) Math.sqrt(dx * dx + dy * dy + dz * dz) + "m";
        }
        float rightW = distance.isEmpty() ? 0.0F : MsdfFonts.medium().getWidth(distance, 5.4F) + 4.0F;
        float iconSpace = 12.0F * hover;
        float textX = rowX + 18.0F;
        float textW = rowW - 18.0F - rightW - iconSpace - 6.0F;
        text(matrices, MsdfFonts.bold(), fit(waypoint.name(), textW, 6.0F), 6.0F, textX, rowY + 3.0F,
                sameDimension ? primaryText() : mutedText());
        String detail = waypoint.blockX() + " " + waypoint.blockY() + " " + waypoint.blockZ() + " · "
                + dimensionLabel(waypoint.dimension());
        text(matrices, MsdfFonts.medium(), fit(detail, textW, 4.9F), 4.9F, textX, rowY + 11.5F, mutedText());
        if (!distance.isEmpty()) {
            text(matrices, MsdfFonts.medium(), distance, 5.4F, rowX + rowW - rightW - iconSpace - 2.0F,
                    MenuStyle.centerMsdfTextY(5.4F, rowY, ROW_H - 2.0F), mutedText());
        }
        if (hover > 0.01F) {
            UiMsdfIconAtlas.renderIconInkFitted(context, EDIT_ICON, rowX + rowW - 8.0F, rowY + (ROW_H - 2.0F) / 2.0F,
                    6.0F, 6.0F, MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, currentAlpha * hover));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !getSetting().isVisible()) return false;
        if (rowsWorld != null && rows.size() < WaypointStore.MAX_PER_WORLD && inside(mouseX, mouseY, addButton())) {
            playButtonClickSound();
            MenuScreen.INSTANCE.openWaypointEditor(rowsWorld, WaypointEditorModal.draftHere(""), false, ChatWorld.serverLabel());
            return true;
        }
        float listY = y + HEADER_H;
        for (int index = 0; index < rows.size(); index++) {
            float rowY = listY + index * ROW_H;
            if (MathUtil.isHovered(mouseX, mouseY, x + INSET, rowY, width - INSET * 2.0F, ROW_H - 2.0F)) {
                playButtonClickSound();
                MenuScreen.INSTANCE.openWaypointEditor(rowsWorld, rows.get(index), true, ChatWorld.serverLabel());
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        return getSetting().isVisible() && MathUtil.isHovered(mouseX, mouseY, x, y, width, height);
    }

    private float[] addButton() {
        float w = 22.0F + MsdfFonts.bold().getWidth(Lang.t("waypoints.add"), 5.6F);
        return new float[]{x + width - 8.0F - w, y + (HEADER_H - BUTTON_H) / 2.0F, w, BUTTON_H};
    }

    private static boolean inside(double mouseX, double mouseY, float[] bounds) {
        return MathUtil.isHovered(mouseX, mouseY, bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    private void text(MatrixStack matrices, vorga.phazeclient.api.system.font.msdf.MsdfFont font, String value,
                      float size, float textX, float textY, int color) {
        MsdfRenderer.renderText(font, value, size, color, matrices.peek().getPositionMatrix(), textX, textY, 0.0F);
    }

    private static String fit(String value, float maxWidth, float size) {
        if (value == null) return "";
        if (MsdfFonts.medium().getWidth(value, size) <= maxWidth) return value;
        String cut = value;
        while (cut.length() > 1 && MsdfFonts.medium().getWidth(cut + "...", size) > maxWidth) cut = cut.substring(0, cut.length() - 1);
        return cut + "...";
    }

    private static String dimensionLabel(String dimension) {
        return switch (dimension) {
            case "minecraft:overworld" -> Lang.t("messenger.dim_overworld");
            case "minecraft:the_nether" -> Lang.t("messenger.dim_nether");
            case "minecraft:the_end" -> Lang.t("messenger.dim_end");
            default -> dimension.substring(dimension.indexOf(':') + 1);
        };
    }
}
