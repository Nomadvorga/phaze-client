package vorga.phazeclient.implement.menu.components.implement.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.api.system.font.msdf.MsdfFont;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.features.modules.client.Theme;
import vorga.phazeclient.implement.features.modules.other.Waypoints;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.UiMsdfIconAtlas;
import vorga.phazeclient.implement.menu.components.AbstractComponent;
import vorga.phazeclient.implement.menu.components.implement.window.AbstractWindow;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color.SettingColorPickerWindow;
import vorga.phazeclient.implement.messenger.ChatWorld;
import vorga.phazeclient.implement.waypoints.Waypoint;
import vorga.phazeclient.implement.waypoints.WaypointStore;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Create / edit dialog for one waypoint, opened from the Waypoints module list
 * and from coordinate cards in messenger chats. Every colour comes from the
 * menu theme; the panel itself is always opaque.
 */
public class WaypointEditorModal extends AbstractComponent {
    private static final float W = 310.0F;
    private static final float H = 204.0F;
    private static final float PAD = 14.0F;
    private static final float RADIUS = 6.0F;
    private static final float MENU_RADIUS = 8.0F;
    private static final float COLUMN_GAP = 12.0F;
    private static final float LEFT_W = 170.0F;
    private static final float FIELD_GAP = 4.0F;
    private static final float TITLE_SIZE = 9.0F;
    private static final float SECTION_SIZE = 6.4F;
    private static final float BODY_SIZE = 6.2F;
    private static final float BUTTON_H = 16.0F;
    private static final float CHECK_SIZE = 9.0F;
    private static final int HOVER_MS = 160;
    private static final long CONFIRM_MS = 3000L;
    private static final int DANGER = 0xFFE05050;
    private static final float ARROW_HIT = 14.0F;
    private static final float ARROW_SIZE = 7.0F;
    private static final float DIMENSION_TEXT = 6.1F;
    private static final float DIMENSION_SLIDE = 6.0F;
    private static final int DIMENSION_SLIDE_MS = 180;

    private static final Identifier CLOSE_ICON = Identifier.of("phaze", "textures/menu/cross.png");
    private static final Identifier CHECK_ICON = Identifier.of("phaze", "textures/menu/check.png");
    private static final Identifier NAME_ICON = Identifier.ofVanilla("textures/edit.png");
    private static final Identifier COORDS_ICON = Identifier.of("phaze", "textures/modules/coordinates_hud.png");
    private static final Identifier PIN_ICON = Identifier.of("phaze", "textures/modules/death_coords.png");
    private static final Identifier DIMENSION_ICON = Identifier.of("phaze", "textures/menu/cube.png");
    private static final Identifier DISPLAY_ICON = Identifier.of("phaze", "textures/modules/block_overlay.png");
    private static final Identifier BRUSH_ICON = Identifier.of("phaze", "textures/menu/paintbrush.png");
    private static final Identifier TRASH_ICON = Identifier.ofVanilla("textures/trash.png");
    private static final Identifier SHARE_ICON = Identifier.ofVanilla("textures/share.png");
    private static final String[] DIMENSIONS = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};

    private final Animation openAnimation = new DecelerateAnimation().setMs(200).setValue(1);
    private final Map<String, Animation> hovers = new HashMap<>();
    private final Map<String, Animation> states = new HashMap<>();
    private final MessengerTextField nameField = new MessengerTextField(WaypointStore.NAME_MAX,
            codePoint -> codePoint != '\u00A7' && !Character.isISOControl(codePoint));
    private final MessengerTextField xField = coordinateField();
    private final MessengerTextField yField = coordinateField();
    private final MessengerTextField zField = coordinateField();
    private final Animation dimensionSlide = new DecelerateAnimation().setMs(DIMENSION_SLIDE_MS).setValue(1);
    private String previousDimension;
    private int slideDirection;

    private boolean open;
    private float alpha;
    private String worldKey;
    private String originalWorldKey;
    private long id;
    private long messageId;
    private long createdAt;
    private String dimension = DIMENSIONS[0];
    private int color;
    private boolean showText, highlight, showBeam, showDistance;
    private long deleteArmedUntil;
    private String error;
    private ColorSetting colorSetting;
    /** The host (and anarchy) the point belongs to, shown next to the title; null when unknown. */
    private String serverLabel;
    /** Chat modes: the anarchy the card is on, or null. */
    private String realm;
    /**
     * Chat modes: the primary button hands the point here instead of saving locally.
     * {@code sending} tells a new card from an edit of our own; {@code onDelete} removes it for everyone.
     */
    private CardSubmit onSubmit;
    private Runnable onDelete;
    private boolean sending;

    @FunctionalInterface
    public interface CardSubmit {
        void accept(String serverKey, String realm, Waypoint point);
    }

    public WaypointEditorModal() {
        openAnimation.setDirectionAndFinish(Direction.BACKWARDS);
    }

    private static MessengerTextField coordinateField() {
        return new MessengerTextField(12, codePoint -> (codePoint >= '0' && codePoint <= '9')
                || codePoint == '-' || codePoint == '.' || codePoint == ',');
    }

    /** A new, unsaved waypoint at the player's position, in the theme's accent colour. */
    public static Waypoint draftHere(String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        String dimension = ChatWorld.dimension();
        double x = client.player == null ? 0.0D : client.player.getX();
        double y = client.player == null ? 0.0D : client.player.getY();
        double z = client.player == null ? 0.0D : client.player.getZ();
        return draft(name, dimension == null ? DIMENSIONS[0] : dimension, x, y, z, defaultColor(), 0L);
    }

    public static Waypoint draft(String name, String dimension, double x, double y, double z, int color, long messageId) {
        return new Waypoint(0L, name, dimension, x, y, z, color, true, true, true, true, 0L, messageId);
    }

    public static int defaultColor() {
        return 0xFF000000 | (MenuStyle.CHIP_ACTIVE & 0x00FFFFFF);
    }

    /** Share mode: the point is set up here, then handed over for a new chat card on the current server. */
    public void openForSend(Waypoint draft, CardSubmit onSend) {
        open(ChatWorld.serverKey(), draft, false, ChatWorld.serverLabel());
        if (!open) return;
        realm = ChatWorld.realm();
        onSubmit = onSend;
        sending = true;
    }

    /** Our own chat card: saving rewrites it for everyone, Delete removes it for everyone. */
    public void openForCardEdit(String serverKey, String cardRealm, Waypoint card, String serverLabel,
                                CardSubmit onSave, Runnable onRemove) {
        open(serverKey, card, false, serverLabel);
        if (!open) return;
        realm = cardRealm;
        createdAt = card.createdAt();
        nameField.setFocused(false);
        onSubmit = onSave;
        onDelete = onRemove;
        sending = false;
    }

    private boolean chatCard() {
        return onSubmit != null && !sending;
    }

    private boolean deletable() {
        return id != 0L || onDelete != null;
    }

    /** {@code existing} marks a stored waypoint: the title says Edit and Delete is offered. */
    public void open(String worldKey, Waypoint waypoint, boolean existing, String serverLabel) {
        Waypoint clean = WaypointStore.sanitize(waypoint);
        if (clean == null) return;
        this.onSubmit = null;
        this.onDelete = null;
        this.sending = false;
        this.realm = null;
        this.serverLabel = serverLabel;
        this.worldKey = worldKey;
        this.originalWorldKey = existing ? worldKey : null;
        this.id = existing ? clean.id() : 0L;
        this.messageId = clean.messageId();
        this.createdAt = existing ? clean.createdAt() : 0L;
        this.dimension = clean.dimension();
        this.color = clean.color();
        this.showText = clean.showText();
        this.highlight = clean.highlight();
        this.showBeam = clean.showBeam();
        this.showDistance = clean.showDistance();
        nameField.setValue(clean.name());
        setCoordinates(clean.x(), clean.y(), clean.z());
        nameField.setFocused(!existing);
        xField.setFocused(false);
        yField.setFocused(false);
        zField.setFocused(false);
        previousDimension = null;
        deleteArmedUntil = 0L;
        error = null;
        colorSetting = null;
        hovers.clear();
        states.clear();
        open = true;
        openAnimation.setDirection(Direction.FORWARDS);
    }

    public void close() {
        if (!open) return;
        open = false;
        onSubmit = null;
        onDelete = null;
        openAnimation.setDirection(Direction.BACKWARDS);
        closeColorPicker();
    }

    public boolean isOpen() {
        return open;
    }

    private float modalX() {
        return x + (width - W) / 2.0F;
    }

    private float modalY() {
        return y + (height - H) / 2.0F;
    }

    /* ---- render -------------------------------------------------------- */

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!open && openAnimation.getOutputFloat() <= 0.001F) return;
        Theme.getInstance().syncLanguage();
        float progress = openAnimation.getOutputFloat();
        alpha = progress * globalAlpha;
        if (!open) {
            mouseX = Integer.MIN_VALUE / 4;
            mouseY = Integer.MIN_VALUE / 4;
        }
        MatrixStack matrices = context.getMatrices();
        rectangle.render(ShapeProperties.create(matrices, x, y, width, height)
                .round(MENU_RADIUS).color(MenuStyle.withAlpha(0xFF000000, alpha * 0.55F)).build());

        float mx = modalX();
        float my = modalY();
        float scale = 0.96F + 0.04F * progress;
        matrices.push();
        matrices.translate(mx + W / 2.0F, my + H / 2.0F, 0.0F);
        matrices.scale(scale, scale, 1.0F);
        matrices.translate(-(mx + W / 2.0F), -(my + H / 2.0F), 0.0F);
        try {
            renderPanel(context, mx, my);
            renderHeader(context, mx, my, mouseX, mouseY);
            renderName(context, mx, my);
            renderCoordinates(context, mx, my, mouseX, mouseY);
            renderDisplay(context, mx, my, mouseX, mouseY);
            renderFooter(context, mx, my, mouseX, mouseY);
        } finally {
            matrices.pop();
        }
    }

    private void renderPanel(DrawContext context, float mx, float my) {
        MatrixStack matrices = context.getMatrices();
        rectangle.render(ShapeProperties.create(matrices, mx, my, W, H).round(RADIUS)
                .color(MenuStyle.withAlpha(opaque(MenuStyle.PANEL_BG), alpha)).build());
        rectangle.render(ShapeProperties.create(matrices, mx, my, W, H).round(RADIUS)
                .color(MenuStyle.withAlpha(opaque(MenuStyle.PANEL_CONTENT), alpha)).build());
        rectangle.render(ShapeProperties.create(matrices, mx, my, W, H).round(RADIUS)
                .softness(1.0F).thickness(1.5F)
                .outlineColor(MenuStyle.withAlpha(MenuStyle.BORDER_LIGHT, alpha))
                .color(MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.PANEL_CONTENT, MenuStyle.PANEL_HEADER, 0.45F), alpha))
                .build());
    }

    private void renderHeader(DrawContext context, float mx, float my, int mouseX, int mouseY) {
        String title = Lang.t(sending ? "waypoints.title_send"
                : id != 0L || chatCard() ? "waypoints.title_edit" : "waypoints.title_new");
        text(context, MsdfFonts.bold(), title, TITLE_SIZE, mx + PAD, my + 12.0F, MenuStyle.TEXT_PRIMARY);
        float[] close = closeButton(mx, my);
        if (serverLabel != null) {
            float labelX = mx + PAD + MsdfFonts.bold().getWidth(title, TITLE_SIZE) + 8.0F;
            String label = fit(Lang.t("waypoints.server") + " " + serverLabel, close[0] - 6.0F - labelX, 5.6F);
            text(context, MsdfFonts.medium(), label, 5.6F, labelX, my + 14.4F, MenuStyle.TEXT_MUTED);
        }
        float hover = hover("close", inside(mouseX, mouseY, close));
        if (hover > 0.0F) {
            rectangle.render(ShapeProperties.create(context.getMatrices(), close[0], close[1], close[2], close[3])
                    .round(3.5F).color(MenuStyle.withAlpha(MenuStyle.CARD_INNER, alpha * hover)).build());
        }
        UiMsdfIconAtlas.renderIconInkFitted(context, CLOSE_ICON, close[0] + close[2] / 2.0F, close[1] + close[3] / 2.0F,
                6.5F, 6.5F, MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, hover), alpha));
    }

    private void renderName(DrawContext context, float mx, float my) {
        sectionLabel(context, NAME_ICON, Lang.t("waypoints.name"), mx + PAD, my + 32.0F);
        nameField.render(context.getMatrices(), mx + PAD, my + 42.0F, W - PAD * 2.0F,
                Lang.t("waypoints.name_placeholder"), alpha);
        divider(context, mx + PAD, my + 68.0F, W - PAD * 2.0F);
    }

    private void renderCoordinates(DrawContext context, float mx, float my, int mouseX, int mouseY) {
        MatrixStack matrices = context.getMatrices();
        sectionLabel(context, COORDS_ICON, Lang.t("waypoints.coordinates"), mx + PAD, my + 76.0F);

        float[] current = currentButton(mx, my);
        boolean available = MinecraftClient.getInstance().player != null && WaypointStore.currentWorldKey() != null;
        float hover = hover("current", available && inside(mouseX, mouseY, current));
        if (hover > 0.0F) {
            rectangle.render(ShapeProperties.create(matrices, current[0], current[1], current[2], current[3])
                    .round(3.5F).color(MenuStyle.withAlpha(MenuStyle.CARD_INNER, alpha * hover)).build());
        }
        int currentColor = available ? MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, hover)
                : MenuStyle.withAlpha(MenuStyle.TEXT_MUTED, 0.5F);
        UiMsdfIconAtlas.renderIconInkFitted(context, PIN_ICON, current[0] + 7.5F, current[1] + current[3] / 2.0F,
                6.0F, 6.0F, MenuStyle.withAlpha(currentColor, alpha));
        text(context, MsdfFonts.bold(), Lang.t("waypoints.set_current"), 5.6F, current[0] + 13.0F,
                MenuStyle.centerMsdfTextY(5.6F, current[1], current[3]), currentColor);

        float fieldY = my + 90.0F;
        float fieldW = (LEFT_W - FIELD_GAP * 2.0F) / 3.0F;
        xField.render(matrices, mx + PAD, fieldY, fieldW, null, "X:", alpha);
        yField.render(matrices, mx + PAD + fieldW + FIELD_GAP, fieldY, fieldW, null, "Y:", alpha);
        zField.render(matrices, mx + PAD + (fieldW + FIELD_GAP) * 2.0F, fieldY, fieldW, null, "Z:", alpha);

        float rightX = rightColumnX(mx);
        sectionLabel(context, DIMENSION_ICON, Lang.t("waypoints.dimension"), rightX, my + 76.0F);
        renderDimensionSelector(context, dimensionBox(mx, my), mouseX, mouseY);
        divider(context, mx + PAD, my + 116.0F, W - PAD * 2.0F);
    }

    /** "<  Overworld  >" like the module mode settings: the arrows at the edges cycle, the name slides. */
    private void renderDimensionSelector(DrawContext context, float[] box, int mouseX, int mouseY) {
        MatrixStack matrices = context.getMatrices();
        rectangle.render(ShapeProperties.create(matrices, box[0], box[1], box[2], box[3])
                .round(4.0F).thickness(1.5F)
                .outlineColor(MenuStyle.withAlpha(MenuStyle.BORDER, alpha))
                .color(MenuStyle.withAlpha(MenuStyle.CARD_INNER, alpha)).build());
        float arrowY = MenuStyle.centerMsdfTextY(ARROW_SIZE, box[1], box[3]);
        float left = hover("dim:prev", inside(mouseX, mouseY, previousArrow(box)));
        float right = hover("dim:next", inside(mouseX, mouseY, nextArrow(box)));
        text(context, MsdfFonts.bold(), "<", ARROW_SIZE, box[0] + 5.0F, arrowY,
                MenuStyle.withAlpha(MenuStyle.CHIP_ACTIVE, 0.7F + 0.3F * left));
        text(context, MsdfFonts.bold(), ">", ARROW_SIZE, box[0] + box[2] - 5.0F - MsdfFonts.bold().getWidth(">", ARROW_SIZE),
                arrowY, MenuStyle.withAlpha(MenuStyle.CHIP_ACTIVE, 0.7F + 0.3F * right));

        float areaX = box[0] + ARROW_HIT;
        float areaW = box[2] - ARROW_HIT * 2.0F;
        float textY = MenuStyle.centerMsdfTextY(DIMENSION_TEXT, box[1], box[3]);
        float progress = dimensionSlide.getOutputFloat();
        if (previousDimension != null && progress < 1.0F) {
            dimensionLabelAt(context, previousDimension, areaX, areaW, textY,
                    progress * DIMENSION_SLIDE * slideDirection, 1.0F - progress);
            dimensionLabelAt(context, dimension, areaX, areaW, textY,
                    (1.0F - progress) * DIMENSION_SLIDE * -slideDirection, progress);
        } else {
            previousDimension = null;
            dimensionLabelAt(context, dimension, areaX, areaW, textY, 0.0F, 1.0F);
        }
    }

    private void dimensionLabelAt(DrawContext context, String value, float areaX, float areaW, float textY,
                                  float slide, float opacity) {
        String label = fitBold(dimensionLabel(value), areaW, DIMENSION_TEXT);
        float labelX = areaX + (areaW - MsdfFonts.bold().getWidth(label, DIMENSION_TEXT)) / 2.0F + slide;
        text(context, MsdfFonts.bold(), label, DIMENSION_TEXT, labelX, textY,
                MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, opacity));
    }

    private static float[] previousArrow(float[] box) {
        return new float[]{box[0], box[1], ARROW_HIT, box[3]};
    }

    private static float[] nextArrow(float[] box) {
        return new float[]{box[0] + box[2] - ARROW_HIT, box[1], ARROW_HIT, box[3]};
    }

    /** The vanilla worlds, plus the one the player or the point is in when it is a server's own. */
    private List<String> dimensionChoices() {
        List<String> choices = new ArrayList<>(List.of(DIMENSIONS));
        String here = ChatWorld.dimension();
        if (here != null && !choices.contains(here)) choices.add(here);
        if (!choices.contains(dimension)) choices.add(dimension);
        return choices;
    }

    private void cycleDimension(int direction) {
        List<String> choices = dimensionChoices();
        int index = choices.indexOf(dimension);
        String next = choices.get(Math.floorMod(index + direction, choices.size()));
        if (next.equals(dimension)) return;
        previousDimension = dimension;
        slideDirection = direction;
        dimension = next;
        dimensionSlide.reset();
        dimensionSlide.setDirection(Direction.BACKWARDS);
        dimensionSlide.setDirection(Direction.FORWARDS);
    }

    private void renderDisplay(DrawContext context, float mx, float my, int mouseX, int mouseY) {
        sectionLabel(context, DISPLAY_ICON, Lang.t("waypoints.display"), mx + PAD, my + 124.0F);
        float col2 = mx + PAD + LEFT_W / 2.0F + 4.0F;
        check(context, "text", Lang.t("waypoints.show_text"), mx + PAD, my + 140.0F, showText, mouseX, mouseY);
        check(context, "highlight", Lang.t("waypoints.highlight"), col2, my + 140.0F, highlight, mouseX, mouseY);
        check(context, "beam", Lang.t("waypoints.show_beam"), mx + PAD, my + 156.0F, showBeam, mouseX, mouseY);
        check(context, "distance", Lang.t("waypoints.show_distance"), col2, my + 156.0F, showDistance, mouseX, mouseY);

        MatrixStack matrices = context.getMatrices();
        float rightX = rightColumnX(mx);
        text(context, MsdfFonts.bold(), Lang.t("waypoints.color"), SECTION_SIZE, rightX, my + 124.0F, MenuStyle.TEXT_MUTED);
        float[] swatch = swatch(mx, my);
        float swatchHover = hover("swatch", inside(mouseX, mouseY, swatch));
        rectangle.render(ShapeProperties.create(matrices, swatch[0], swatch[1], swatch[2], swatch[3])
                .round(3.5F).thickness(1.2F)
                .outlineColor(MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.BORDER_LIGHT, 0xFFFFFFFF, swatchHover * 0.4F), alpha))
                .color(MenuStyle.withAlpha(color, alpha)).build());

        float[] change = changeButton(mx, my);
        float changeHover = hover("change", inside(mouseX, mouseY, change));
        rectangle.render(ShapeProperties.create(matrices, change[0], change[1], change[2], change[3])
                .round(3.5F).thickness(1.2F)
                .outlineColor(MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.BORDER, MenuStyle.BORDER_LIGHT, changeHover), alpha))
                .color(MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.CARD_INNER, 0xFFFFFFFF, changeHover * 0.06F), alpha)).build());
        int changeText = MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, 0.45F + changeHover * 0.55F);
        UiMsdfIconAtlas.renderIconInkFitted(context, BRUSH_ICON, change[0] + 8.0F, change[1] + change[3] / 2.0F,
                5.8F, 5.8F, MenuStyle.withAlpha(changeText, alpha));
        text(context, MsdfFonts.bold(), Lang.t("waypoints.change"), 5.6F, change[0] + 14.0F,
                MenuStyle.centerMsdfTextY(5.6F, change[1], change[3]), changeText);
    }

    private void renderFooter(DrawContext context, float mx, float my, int mouseX, int mouseY) {
        float footerY = my + H - PAD - BUTTON_H;
        String info = error != null ? error : createdLabel();
        text(context, MsdfFonts.medium(), fit(info, W - PAD * 2.0F - 130.0F, 5.8F), 5.8F, mx + PAD,
                MenuStyle.centerMsdfTextY(5.8F, footerY, BUTTON_H), error != null ? DANGER : MenuStyle.TEXT_MUTED);

        float[] save = saveButton(mx, my);
        actionButton(context, "save", save, sending ? SHARE_ICON : CHECK_ICON, primaryLabel(),
                MenuStyle.ACCENT_GREEN, mouseX, mouseY);
        if (deletable()) {
            float[] delete = deleteButton(mx, my);
            boolean armed = System.currentTimeMillis() < deleteArmedUntil;
            actionButton(context, "delete", delete, TRASH_ICON,
                    Lang.t(armed ? "waypoints.delete_confirm" : "waypoints.delete"), DANGER, mouseX, mouseY);
        }
    }

    /* ---- pieces ------------------------------------------------------- */

    private void sectionLabel(DrawContext context, Identifier icon, String label, float labelX, float labelY) {
        UiMsdfIconAtlas.renderIconInkFitted(context, icon, labelX + 3.5F, labelY + SECTION_SIZE / 2.0F - 0.3F,
                6.5F, 6.5F, MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, alpha));
        text(context, MsdfFonts.bold(), label, SECTION_SIZE, labelX + 10.0F, labelY, MenuStyle.TEXT_PRIMARY);
    }

    private void check(DrawContext context, String key, String label, float checkX, float checkY, boolean on,
                       int mouseX, int mouseY) {
        MatrixStack matrices = context.getMatrices();
        float hover = hover("check:" + key, MathUtil.isHovered(mouseX, mouseY, checkX, checkY - 2.0F, checkWidth(), CHECK_SIZE + 4.0F));
        float state = state("check:" + key, on);
        int fill = MenuStyle.mix(MenuStyle.mix(MenuStyle.CARD_INNER, 0xFFFFFFFF, hover * 0.05F), MenuStyle.CHIP_ACTIVE, state);
        int border = MenuStyle.mix(MenuStyle.mix(MenuStyle.BORDER, MenuStyle.BORDER_LIGHT, hover), MenuStyle.CHIP_ACTIVE, state);
        rectangle.render(ShapeProperties.create(matrices, checkX, checkY, CHECK_SIZE, CHECK_SIZE)
                .round(2.4F).thickness(1.2F)
                .outlineColor(MenuStyle.withAlpha(border, alpha))
                .color(MenuStyle.withAlpha(fill, alpha)).build());
        if (state > 0.01F) {
            UiMsdfIconAtlas.renderIconInkFitted(context, CHECK_ICON, checkX + CHECK_SIZE / 2.0F, checkY + CHECK_SIZE / 2.0F,
                    5.2F * (0.7F + 0.3F * state), 5.2F * (0.7F + 0.3F * state), MenuStyle.withAlpha(0xFFFFFFFF, alpha * state));
        }
        text(context, MsdfFonts.medium(), label, BODY_SIZE, checkX + CHECK_SIZE + 5.0F,
                MenuStyle.centerMsdfTextY(BODY_SIZE, checkY, CHECK_SIZE),
                MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, 0.65F + hover * 0.35F));
    }

    private float checkWidth() {
        return LEFT_W / 2.0F - 6.0F;
    }

    private void actionButton(DrawContext context, String key, float[] bounds, Identifier icon, String label,
                              int tone, int mouseX, int mouseY) {
        float hover = hover(key, inside(mouseX, mouseY, bounds));
        int base = opaque(MenuStyle.PANEL_BG);
        int fill = MenuStyle.mix(base, tone, 0.18F + hover * 0.14F);
        int border = MenuStyle.mix(base, tone, 0.32F + hover * 0.25F);
        rectangle.render(ShapeProperties.create(context.getMatrices(), bounds[0], bounds[1], bounds[2], bounds[3])
                .round(4.0F).thickness(1.2F)
                .outlineColor(MenuStyle.withAlpha(border, alpha))
                .color(MenuStyle.withAlpha(fill, alpha)).build());
        int textColor = MenuStyle.mix(tone, 0xFFFFFFFF, 0.15F + hover * 0.25F);
        float contentW = 9.0F + MsdfFonts.bold().getWidth(label, BODY_SIZE);
        float startX = bounds[0] + (bounds[2] - contentW) / 2.0F;
        UiMsdfIconAtlas.renderIconInkFitted(context, icon, startX + 3.0F, bounds[1] + bounds[3] / 2.0F,
                6.0F, 6.0F, MenuStyle.withAlpha(textColor, alpha));
        text(context, MsdfFonts.bold(), label, BODY_SIZE, startX + 9.0F,
                MenuStyle.centerMsdfTextY(BODY_SIZE, bounds[1], bounds[3]), textColor);
    }

    private void divider(DrawContext context, float lineX, float lineY, float lineW) {
        rectangle.render(ShapeProperties.create(context.getMatrices(), lineX, lineY, lineW, 0.8F)
                .color(MenuStyle.withAlpha(MenuStyle.BORDER, alpha)).build());
    }

    private void text(DrawContext context, MsdfFont font, String value, float size, float textX, float textY, int color) {
        MsdfRenderer.renderText(font, value, size, MenuStyle.withAlpha(color, alpha),
                context.getMatrices().peek().getPositionMatrix(), textX, textY, 0.0F);
    }

    private static String fit(String value, float maxWidth, float size) {
        if (value == null) return "";
        if (MsdfFonts.medium().getWidth(value, size) <= maxWidth) return value;
        String cut = value;
        while (cut.length() > 1 && MsdfFonts.medium().getWidth(cut + "...", size) > maxWidth) cut = cut.substring(0, cut.length() - 1);
        return cut + "...";
    }

    private static String fitBold(String value, float maxWidth, float size) {
        if (value == null) return "";
        if (MsdfFonts.bold().getWidth(value, size) <= maxWidth) return value;
        String cut = value;
        while (cut.length() > 1 && MsdfFonts.bold().getWidth(cut + "...", size) > maxWidth) cut = cut.substring(0, cut.length() - 1);
        return cut + "...";
    }

    /* ---- layout ------------------------------------------------------- */

    private float rightColumnX(float mx) {
        return mx + PAD + LEFT_W + COLUMN_GAP;
    }

    private float rightColumnW() {
        return W - PAD * 2.0F - LEFT_W - COLUMN_GAP;
    }

    private float[] closeButton(float mx, float my) {
        return new float[]{mx + W - PAD - 12.0F, my + 9.0F, 14.0F, 14.0F};
    }

    private float[] currentButton(float mx, float my) {
        float w = 19.0F + MsdfFonts.bold().getWidth(Lang.t("waypoints.set_current"), 5.6F);
        return new float[]{mx + PAD + LEFT_W - w, my + 73.0F, w, 13.0F};
    }

    private float[] dimensionBox(float mx, float my) {
        return new float[]{rightColumnX(mx), my + 90.0F, rightColumnW(), MessengerTextField.HEIGHT};
    }

    private float[] swatch(float mx, float my) {
        return new float[]{rightColumnX(mx), my + 138.0F, 14.0F, 14.0F};
    }

    private float[] changeButton(float mx, float my) {
        float w = 20.0F + MsdfFonts.bold().getWidth(Lang.t("waypoints.change"), 5.6F);
        return new float[]{rightColumnX(mx) + 19.0F, my + 138.0F, w, 14.0F};
    }

    private String primaryLabel() {
        return Lang.t(sending ? "waypoints.send" : "waypoints.save");
    }

    private float[] saveButton(float mx, float my) {
        float w = Math.max(54.0F, 22.0F + MsdfFonts.bold().getWidth(primaryLabel(), BODY_SIZE));
        return new float[]{mx + W - PAD - w, my + H - PAD - BUTTON_H, w, BUTTON_H};
    }

    private float[] deleteButton(float mx, float my) {
        float[] save = saveButton(mx, my);
        String label = Lang.t(System.currentTimeMillis() < deleteArmedUntil ? "waypoints.delete_confirm" : "waypoints.delete");
        float w = Math.max(56.0F, 22.0F + MsdfFonts.bold().getWidth(label, BODY_SIZE));
        return new float[]{save[0] - 6.0F - w, save[1], w, BUTTON_H};
    }

    private static boolean inside(double mouseX, double mouseY, float[] bounds) {
        return MathUtil.isHovered(mouseX, mouseY, bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    /* ---- input -------------------------------------------------------- */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) return false;
        if (button != 0) return true;
        float mx = modalX();
        float my = modalY();
        if (!MathUtil.isHovered(mouseX, mouseY, mx, my, W, H) || inside(mouseX, mouseY, closeButton(mx, my))) {
            playButtonClickSound();
            close();
            return true;
        }
        boolean field = nameField.mouseClicked(mouseX, mouseY) | xField.mouseClicked(mouseX, mouseY)
                | yField.mouseClicked(mouseX, mouseY) | zField.mouseClicked(mouseX, mouseY);
        if (field) return true;

        if (inside(mouseX, mouseY, currentButton(mx, my))) {
            setToCurrent();
        } else if (inside(mouseX, mouseY, dimensionBox(mx, my))) {
            // Like the module selectors: the left arrow goes back, anywhere else forward.
            playButtonClickSound();
            cycleDimension(inside(mouseX, mouseY, previousArrow(dimensionBox(mx, my))) ? -1 : 1);
        } else if (inside(mouseX, mouseY, swatch(mx, my)) || inside(mouseX, mouseY, changeButton(mx, my))) {
            playButtonClickSound();
            toggleColorPicker(swatch(mx, my));
        } else if (inside(mouseX, mouseY, saveButton(mx, my))) {
            playButtonClickSound();
            save();
        } else if (deletable() && inside(mouseX, mouseY, deleteButton(mx, my))) {
            playButtonClickSound();
            delete();
        } else {
            float col2 = mx + PAD + LEFT_W / 2.0F + 4.0F;
            if (hitCheck(mouseX, mouseY, mx + PAD, my + 140.0F)) showText = !showText;
            else if (hitCheck(mouseX, mouseY, col2, my + 140.0F)) highlight = !highlight;
            else if (hitCheck(mouseX, mouseY, mx + PAD, my + 156.0F)) showBeam = !showBeam;
            else if (hitCheck(mouseX, mouseY, col2, my + 156.0F)) showDistance = !showDistance;
            else return true;
            playButtonClickSound();
        }
        return true;
    }

    private boolean hitCheck(double mouseX, double mouseY, float checkX, float checkY) {
        return MathUtil.isHovered(mouseX, mouseY, checkX, checkY - 2.0F, checkWidth(), CHECK_SIZE + 4.0F);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            cycleFocus((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
            return true;
        }
        MessengerTextField focused = focusedField();
        if (focused != null) focused.keyPressed(keyCode, modifiers);
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!open) return false;
        MessengerTextField focused = focusedField();
        if (focused != null) {
            focused.charTyped(chr);
            error = null;
        }
        return true;
    }

    private MessengerTextField focusedField() {
        for (MessengerTextField field : fields()) if (field.focused()) return field;
        return null;
    }

    private MessengerTextField[] fields() {
        return new MessengerTextField[]{nameField, xField, yField, zField};
    }

    private void cycleFocus(boolean backwards) {
        MessengerTextField[] fields = fields();
        int current = -1;
        for (int index = 0; index < fields.length; index++) if (fields[index].focused()) current = index;
        int next = current < 0 ? 0 : Math.floorMod(current + (backwards ? -1 : 1), fields.length);
        for (int index = 0; index < fields.length; index++) fields[index].setFocused(index == next);
    }

    /* ---- actions ------------------------------------------------------ */

    private void setToCurrent() {
        MinecraftClient client = MinecraftClient.getInstance();
        // A chat card is keyed by server alone and carries its anarchy; a local waypoint's key holds both.
        String key = onSubmit != null ? ChatWorld.serverKey() : WaypointStore.currentWorldKey();
        String here = ChatWorld.dimension();
        if (client.player == null || key == null || here == null) return;
        playButtonClickSound();
        worldKey = key;
        if (onSubmit != null) realm = ChatWorld.realm();
        serverLabel = ChatWorld.serverLabel();
        dimension = here;
        setCoordinates(client.player.getX(), client.player.getY(), client.player.getZ());
        error = null;
    }

    private void setCoordinates(double cx, double cy, double cz) {
        xField.setValue(formatCoordinate(cx));
        yField.setValue(formatCoordinate(cy));
        zField.setValue(formatCoordinate(cz));
        xField.setInvalid(false);
        yField.setInvalid(false);
        zField.setInvalid(false);
    }

    private static String formatCoordinate(double value) {
        double rounded = Math.round(value * 100.0D) / 100.0D;
        if (rounded == Math.rint(rounded)) return Long.toString((long) rounded);
        String text = String.format(Locale.ROOT, "%.2f", rounded);
        return text.endsWith("0") ? text.substring(0, text.length() - 1) : text;
    }

    private void toggleColorPicker(float[] anchor) {
        for (AbstractWindow window : windowManager.getWindows()) {
            if (window instanceof SettingColorPickerWindow picker && picker.getSetting() == colorSetting) {
                windowManager.delete(window);
                colorSetting = null;
                return;
            }
        }
        colorSetting = new ColorSetting("Color", "").value(color).noAlpha();
        colorSetting.onChange(next -> color = 0xFF000000 | (next & 0x00FFFFFF));
        int windowW = Math.round(SettingColorPickerWindow.WINDOW_WIDTH);
        int windowH = Math.round(SettingColorPickerWindow.getWindowHeight(colorSetting));
        int windowX = MenuScreen.INSTANCE.clampOverlayX(anchor[0] + anchor[2] + 70.0F, windowW);
        int windowY = MenuScreen.INSTANCE.clampOverlayY(anchor[1] + anchor[3] / 2.0F - windowH / 2.0F, windowH);
        windowManager.add(new SettingColorPickerWindow(colorSetting)
                .position(windowX, windowY)
                .size(windowW, windowH)
                .draggable(false));
    }

    private void closeColorPicker() {
        if (colorSetting == null) return;
        for (AbstractWindow window : windowManager.getWindows()) {
            if (window instanceof SettingColorPickerWindow picker && picker.getSetting() == colorSetting) {
                windowManager.delete(window);
            }
        }
        colorSetting = null;
    }

    private void save() {
        Double px = parse(xField, WaypointStore.COORD_LIMIT);
        Double py = parse(yField, WaypointStore.Y_LIMIT);
        Double pz = parse(zField, WaypointStore.COORD_LIMIT);
        if (px == null || py == null || pz == null) {
            error = Lang.t("waypoints.invalid_coords");
            return;
        }
        if (!WaypointStore.isValidWorldKey(worldKey)) {
            error = Lang.t("waypoints.no_world");
            return;
        }
        String name = WaypointStore.cleanName(nameField.value());
        if (name.isEmpty()) name = Lang.t("waypoints.default_name");
        if (onSubmit != null) {
            // A new card carries the sender's own server; an edited one may move to any server, never to singleplayer.
            boolean allowed = sending ? worldKey.equals(ChatWorld.serverKey()) : !worldKey.startsWith("sp-");
            if (!allowed) {
                error = Lang.t("waypoints.no_server");
                return;
            }
            Waypoint shared = WaypointStore.sanitize(new Waypoint(0L, name, dimension, px, py, pz, color,
                    showText, highlight, showBeam, showDistance, 0L, messageId));
            CardSubmit submit = onSubmit;
            String key = worldKey;
            String cardRealm = realm;
            close();
            if (shared != null) submit.accept(key, cardRealm, shared);
            return;
        }
        WaypointStore store = WaypointStore.get();
        boolean moved = id != 0L && originalWorldKey != null && !originalWorldKey.equals(worldKey);
        long targetId = moved ? 0L : id;
        Waypoint saved = store.put(worldKey, new Waypoint(targetId, name, dimension, px, py, pz, color,
                showText, highlight, showBeam, showDistance,
                createdAt == 0L ? System.currentTimeMillis() : createdAt, messageId));
        if (saved == null) {
            error = Lang.t("waypoints.limit");
            return;
        }
        if (moved) store.remove(originalWorldKey, id);
        Waypoints module = Waypoints.getInstance();
        if (!module.isEnabled()) module.setState(true);
        close();
    }

    private void delete() {
        long now = System.currentTimeMillis();
        if (now >= deleteArmedUntil) {
            deleteArmedUntil = now + CONFIRM_MS;
            return;
        }
        Runnable remove = onDelete;
        if (remove == null) WaypointStore.get().remove(originalWorldKey != null ? originalWorldKey : worldKey, id);
        close();
        if (remove != null) remove.run();
    }

    private static Double parse(MessengerTextField field, double limit) {
        String raw = field.value().trim().replace(',', '.');
        try {
            double value = Double.parseDouble(raw);
            if (Double.isFinite(value) && Math.abs(value) <= limit) return value;
        } catch (NumberFormatException ignored) {
        }
        field.setInvalid(true);
        return null;
    }

    /* ---- text --------------------------------------------------------- */

    private String createdLabel() {
        if ((id == 0L && !chatCard()) || createdAt <= 0L) return Lang.t("waypoints.unsaved");
        ZoneId zone = ZoneId.systemDefault();
        long days = ChronoUnit.DAYS.between(LocalDate.ofInstant(Instant.ofEpochMilli(createdAt), zone), LocalDate.now(zone));
        if (days <= 0L) return Lang.t("waypoints.created_today");
        if (days == 1L) return Lang.t("waypoints.created_yesterday");
        return String.format(Lang.t("waypoints.created_days"), days);
    }

    static String dimensionLabel(String dimension) {
        if (dimension == null) return "";
        return switch (dimension) {
            case "minecraft:overworld" -> Lang.t("messenger.dim_overworld");
            case "minecraft:the_nether" -> Lang.t("messenger.dim_nether");
            case "minecraft:the_end" -> Lang.t("messenger.dim_end");
            default -> dimension.substring(dimension.indexOf(':') + 1);
        };
    }

    private float hover(String key, boolean hovered) {
        Animation animation = hovers.computeIfAbsent(key, ignored -> new DecelerateAnimation()
                .setMs(HOVER_MS).setValue(1).setDirectionAndFinish(Direction.BACKWARDS));
        boolean active = open && hovered;
        animation.setDirection(active ? Direction.FORWARDS : Direction.BACKWARDS);
        if (active) CursorManager.requestHand();
        return animation.getOutputFloat();
    }

    private float state(String key, boolean on) {
        Animation animation = states.computeIfAbsent(key, ignored -> new DecelerateAnimation()
                .setMs(HOVER_MS).setValue(1).setDirectionAndFinish(on ? Direction.FORWARDS : Direction.BACKWARDS));
        animation.setDirection(on ? Direction.FORWARDS : Direction.BACKWARDS);
        return animation.getOutputFloat();
    }

    private static int opaque(int color) {
        return 0xFF000000 | (color & 0x00FFFFFF);
    }
}
