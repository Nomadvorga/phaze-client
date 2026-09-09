package vorga.phazeclient.implement.menu.components.implement.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.base.util.render.GuiMatrix;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.cosmetics.CosmeticsRenderer;
import vorga.phazeclient.implement.cosmetics.CosmeticsState;
import vorga.phazeclient.implement.cosmetics.PreviewMarker;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.UiMsdfIconAtlas;
import vorga.phazeclient.implement.menu.components.AbstractComponent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Standalone COSMETICS top-tab. It owns selection, preview and cosmetic
 * settings without exposing cosmetics as a module in the Mods catalog.
 */
public final class CosmeticsViewComponent extends AbstractComponent {
    private static final float HEADER_OFFSET = 41.0F;
    private static final float SIDE_MARGIN = 9.0F;
    private static final float GAP = 7.0F;
    private static final float CATEGORY_WIDTH = 61.0F;
    private static final float PREVIEW_WIDTH = 142.0F;
    private static final float TITLE_SIZE = 8.4F;
    private static final float SUBTITLE_SIZE = 5.3F;
    private static final float LABEL_SIZE = 5.8F;
    private static final float CARD_LABEL_SIZE = 5.9F;
    private static final Identifier ICON_ALL = Identifier.of("phaze", "textures/cosmetics/category_all.png");
    private static final Identifier ICON_WINGS = Identifier.of("phaze", "textures/cosmetics/category_wings.png");
    private static final Identifier ICON_CAPES = Identifier.of("phaze", "textures/cosmetics/category_capes.png");
    private static final Identifier ICON_HATS = Identifier.of("phaze", "textures/cosmetics/category_hats.png");
    private static final Identifier ICON_PETS = Identifier.of("phaze", "textures/cosmetics/category_pets.png");

    private boolean open;
    private String category = "ALL";
    private String previewSelection = CosmeticsState.WIMGS;
    private float previewYaw = 18.0F;
    private double catalogScroll;
    private double smoothedCatalogScroll;
    private boolean draggingPreview;
    private boolean draggingMotion;
    private boolean draggingPetFollow;
    private double sliderAnimation = -1.0D;
    private double petSliderAnimation = -1.0D;
    private final Animation pageAnimation = new DecelerateAnimation().setMs(180).setValue(1);
    private final CheckComponent hideElytraToggle = new CheckComponent();
    private final Map<String, CardAnimations> cardAnimations = new HashMap<>();
    private final Set<String> cosmeticWarmupRequested = new HashSet<>();
    private long catalogFrameId;

    public CosmeticsViewComponent() {
        pageAnimation.setDirectionAndFinish(Direction.BACKWARDS);
        hideElytraToggle.setRunnable(() -> {
            CosmeticsState state = CosmeticsState.getInstance();
            state.setHideElytra(!state.isHideElytra());
        });
    }

    public void open() {
        open = true;
        pageAnimation.setDirection(Direction.FORWARDS);
        CosmeticsState.getInstance().refreshCatalog();
        catalogScroll = 0.0D;
        smoothedCatalogScroll = 0.0D;
        previewSelection = CosmeticsState.getInstance().getSelectedWing();
        if (CosmeticsState.NONE.equalsIgnoreCase(previewSelection)
                || !CosmeticsState.getInstance().isAvailable(previewSelection)) {
            List<CosmeticsState.CosmeticEntry> catalog = CosmeticsState.getInstance().getCatalog();
            previewSelection = catalog.isEmpty() ? CosmeticsState.WIMGS : catalog.getFirst().name();
        }
    }

    public void close() {
        open = false;
        pageAnimation.setDirection(Direction.BACKWARDS);
        draggingPreview = false;
        draggingMotion = false;
        draggingPetFollow = false;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean shouldRender() {
        return open || pageAnimation.getOutputFloat() > 0.002F;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!shouldRender()) return;

        float inheritedAlpha = globalAlpha;
        globalAlpha *= pageAnimation.getOutputFloat();
        Layout layout = layout();
        try {
            renderTitle(context, layout);
            renderCategories(context, layout, mouseX, mouseY);
            renderCatalog(context, layout, mouseX, mouseY);
            renderPreview(context, layout, mouseX, mouseY);
        } finally {
            globalAlpha = inheritedAlpha;
        }
    }

    private void renderTitle(DrawContext context, Layout layout) {
        MsdfRenderer.renderText(
                MsdfFonts.bold(), Lang.t("cosmetics.title"), TITLE_SIZE, applyGlobalAlpha(MenuStyle.TEXT_PRIMARY),
                GuiMatrix.mat4(context.getMatrices()), layout.catalogX, layout.top, 0.0F
        );
        MsdfRenderer.renderText(
                MsdfFonts.medium(), Lang.t("cosmetics.subtitle"), SUBTITLE_SIZE,
                applyGlobalAlpha(MenuStyle.TEXT_MUTED), GuiMatrix.mat4(context.getMatrices()),
                layout.catalogX, layout.top + 10.5F, 0.0F
        );
    }

    private void renderCategories(DrawContext context, Layout layout, int mouseX, int mouseY) {
        String[] labels = {"ALL", "WINGS", "CAPES", "HATS", "PETS"};
        float rowY = layout.cardsTop;
        for (String label : labels) {
            boolean selected = label.equals(category);
            boolean hovered = MathUtil.isHovered(mouseX, mouseY, layout.categoryX, rowY, CATEGORY_WIDTH, 24.0F);
            int fill = selected
                    ? MenuStyle.mix(MenuStyle.PANEL_CHIP, MenuStyle.CHIP_ACTIVE, 0.28F)
                    : MenuStyle.mix(MenuStyle.PANEL_CHIP, MenuStyle.CARD_INNER, 0.34F);
            int border = selected ? MenuStyle.mix(MenuStyle.BORDER_LIGHT, MenuStyle.CHIP_ACTIVE, 0.55F) : MenuStyle.BORDER;
            if (hovered) fill = MenuStyle.mix(fill, MenuStyle.TEXT_PRIMARY, 0.06F);

            rectangle.render(ShapeProperties.create(context.getMatrices(), layout.categoryX, rowY, CATEGORY_WIDTH, 24.0F)
                    .round(3.0F).thickness(2.0F)
                    .outlineColor(applyGlobalAlpha(border)).color(applyGlobalAlpha(fill)).build());
            drawCategoryGlyph(context, label, layout.categoryX + 8.0F, rowY + 7.0F,
                    selected ? MenuStyle.CHIP_ACTIVE : MenuStyle.TEXT_MUTED);
            MsdfRenderer.renderText(
                    MsdfFonts.bold(), categoryLabel(label), LABEL_SIZE,
                    applyGlobalAlpha(selected ? MenuStyle.TEXT_PRIMARY : MenuStyle.TEXT_MUTED),
                    GuiMatrix.mat4(context.getMatrices()),
                    layout.categoryX + 21.0F,
                    MenuStyle.centerMsdfTextY(LABEL_SIZE, rowY, 24.0F),
                    0.0F
            );
            rowY += 28.0F;
        }
    }

    private void drawCategoryGlyph(DrawContext context, String label, float x, float y, int color) {
        Identifier icon = switch (label) {
            case "WINGS" -> ICON_WINGS;
            case "CAPES" -> ICON_CAPES;
            case "HATS" -> ICON_HATS;
            case "PETS" -> ICON_PETS;
            default -> ICON_ALL;
        };
        UiMsdfIconAtlas.renderIcon(context, icon, x - 0.5F, y - 0.5F, 11.0F, 11.0F,
                applyGlobalAlpha(color), true);
    }

    private static String categoryLabel(String category) {
        return switch (category) {
            case "WINGS" -> Lang.t("cosmetics.category.wings");
            case "CAPES" -> Lang.t("cosmetics.category.capes");
            case "HATS" -> Lang.t("cosmetics.category.hats");
            case "PETS" -> Lang.t("cosmetics.category.pets");
            default -> Lang.t("cosmetics.category.all");
        };
    }

    private void renderCatalog(DrawContext context, Layout layout, int mouseX, int mouseY) {
        List<CatalogItem> items = allCatalogItems();
        long frameId = ++catalogFrameId;
        List<VisibleCard> visibleCards = new ArrayList<>(8);
        float cardGap = 6.0F;
        float cardW = (layout.catalogWidth - cardGap) / 2.0F;
        float cardH = 61.0F;
        float viewportHeight = catalogViewportHeight(layout);
        double maxScroll = catalogMaxScroll(layout, items.size(), cardH, cardGap);
        catalogScroll = Math.max(-maxScroll, Math.min(0.0D, catalogScroll));
        smoothedCatalogScroll = MathUtil.interpolateSmooth(
                3.2D, smoothedCatalogScroll, catalogScroll
        );
        if (Math.abs(smoothedCatalogScroll - catalogScroll) < 0.01D) {
            smoothedCatalogScroll = catalogScroll;
        }

        Main.getInstance().getScissorManager().push(
                GuiMatrix.mat4(context.getMatrices()),
                layout.catalogX, layout.cardsTop, layout.catalogWidth, viewportHeight
        );
        context.enableScissor(
                Math.round(layout.catalogX), Math.round(layout.cardsTop),
                Math.round(layout.catalogX + layout.catalogWidth),
                Math.round(layout.cardsTop + viewportHeight));
        for (int i = 0; i < items.size(); i++) {
            CatalogItem item = items.get(i);
            int column = i % 2;
            int row = i / 2;
            float cardX = layout.catalogX + column * (cardW + cardGap);
            float cardY = layout.cardsTop + row * (cardH + cardGap)
                    + (float) smoothedCatalogScroll;
            if (cardY + cardH < layout.cardsTop - 1.0F
                    || cardY > layout.cardsTop + viewportHeight + 1.0F) {
                continue;
            }
            boolean available = !item.locked && CosmeticsState.getInstance().isAvailable(item.id);
            boolean selected = item.id.equalsIgnoreCase(previewSelection);
            boolean equipped = !item.locked && CosmeticsState.getInstance().isEquipped(item.id);
            boolean hovered = MathUtil.isHovered(
                    mouseX, mouseY, layout.catalogX, layout.cardsTop,
                    layout.catalogWidth, viewportHeight
            ) && MathUtil.isHovered(mouseX, mouseY, cardX, cardY, cardW, cardH);
            CardAnimations animations = cardAnimations.computeIfAbsent(
                    item.id.toLowerCase(Locale.ROOT), ignored -> new CardAnimations(hovered, selected)
            );
            animations.hover.setDirection(hovered ? Direction.FORWARDS : Direction.BACKWARDS);
            animations.selection.setDirection(selected ? Direction.FORWARDS : Direction.BACKWARDS);
            float hoverProgress = animations.hover.getOutputFloat();
            float selectionProgress = animations.selection.getOutputFloat();

            int fill = item.locked || !available ? MenuStyle.CARD_DISABLED : MenuStyle.CARD_BG;
            if (!item.locked) fill = MenuStyle.mix(fill, MenuStyle.TEXT_PRIMARY, hoverProgress * 0.055F);
            int selectedBorder = MenuStyle.mix(MenuStyle.BORDER_LIGHT, MenuStyle.CHIP_ACTIVE, 0.72F);
            int border = MenuStyle.mix(MenuStyle.BORDER, selectedBorder, selectionProgress);

            rectangle.render(ShapeProperties.create(context.getMatrices(), cardX, cardY, cardW, cardH)
                    .round(4.0F).thickness(1.5F + 0.7F * selectionProgress)
                    .outlineColor(applyGlobalAlpha(border)).color(applyGlobalAlpha(fill)).build());

            if (!available) {
                drawWingThumbnail(context, cardX + cardW / 2.0F, cardY + 24.0F,
                        item.primary, item.accent, true);
            }

            String baseLabel = catalogItemLabel(item);
            String label = available || item.locked
                    ? baseLabel
                    : baseLabel + " · " + Lang.t("cosmetics.missing_suffix");
            int textColor = item.locked || !available ? MenuStyle.TEXT_MUTED
                    : selected ? MenuStyle.CHIP_ACTIVE : MenuStyle.TEXT_PRIMARY;
            visibleCards.add(new VisibleCard(
                    item, cardX, cardY, cardW, cardH,
                    available, selected, equipped, selectionProgress, label, textColor
            ));
        }

        // Static cosmetic thumbnails are captured once into small transparent
        // FBOs. Re-rendering every imported 3D model on every menu frame was
        // the remaining 400 -> 200 FPS regression; subsequent frames are now
        // one cheap textured quad per visible card.
        BatchedRectangle.flushIfBatching();
        for (VisibleCard card : visibleCards) {
            if (!card.available) continue;
            renderCatalogModelPreview(
                    context, card.item.id,
                    card.x, card.y, card.width, card.height, frameId
            );
        }

        // Parse files and decode capes away from the render thread. Do not
        // submit invisible 3D cards here: an alpha-zero special element still
        // rasterises its FBO, which turned the warm-up loop into one costly
        // model render every frame. A newly visible card is populated under
        // the renderer's one-thumbnail-per-frame budget instead.
        for (CatalogItem item : items) {
            if (cosmeticWarmupRequested.add(item.id)) {
                if (CosmeticsState.isCape(item.id)) {
                    CosmeticsRenderer.requestCapeWarmup(item.id);
                } else {
                    CosmeticsRenderer.requestModelWarmup(item.id);
                }
            }
            if (CosmeticsState.isCape(item.id)) {
                CosmeticsRenderer.capePreviewTextureIfReady(item.id);
            }
        }
        // Submit text and markers after models so thumbnails cannot cover UI.
        for (VisibleCard card : visibleCards) {
            MsdfRenderer.renderText(
                    MsdfFonts.medium(), card.label, CARD_LABEL_SIZE,
                    applyGlobalAlpha(card.textColor),
                    GuiMatrix.mat4(context.getMatrices()),
                    MenuStyle.centerMsdfTextX(
                            MsdfFonts.medium(), card.label, CARD_LABEL_SIZE,
                            card.x, card.width),
                    card.y + card.height - 14.0F, 0.0F
            );

            if (!card.equipped && card.item.locked) {
                String locked = Lang.t("cosmetics.locked");
                MsdfRenderer.renderText(
                        MsdfFonts.bold(), locked, 4.2F, applyGlobalAlpha(MenuStyle.TEXT_MUTED),
                        GuiMatrix.mat4(context.getMatrices()),
                        card.x + card.width - MsdfFonts.bold().getWidth(locked, 4.2F) - 4.0F,
                        card.y + 4.0F, 0.0F
                );
            } else if (card.selected) {
                rectangle.render(ShapeProperties.create(
                                context.getMatrices(),
                                card.x + card.width - 10.0F, card.y + 4.0F,
                                6.0F, 6.0F)
                        .round(3.0F).color(applyGlobalAlpha(MenuStyle.withAlpha(
                                MenuStyle.CHIP_ACTIVE, card.selectionProgress))).build());
            }
        }

        BatchedRectangle.flushIfBatching();
        for (VisibleCard card : visibleCards) {
            if (!card.item.locked && card.equipped) {
                renderEquippedBadge(context, card.x, card.y, card.width);
            }
        }
        BatchedRectangle.flushIfBatching();
        context.disableScissor();
        Main.getInstance().getScissorManager().pop();
    }

    private void renderEquippedBadge(DrawContext context, float cardX, float cardY, float cardW) {
        String worn = Lang.t("cosmetics.equipped");
        float badgeW = MsdfFonts.bold().getWidth(worn, 3.9F) + 8.0F;
        float badgeX = cardX + cardW - badgeW - 4.0F;
        rectangle.render(ShapeProperties.create(
                        context.getMatrices(), badgeX, cardY + 4.0F, badgeW, 8.0F)
                .round(4.0F)
                .color(applyGlobalAlpha(MenuStyle.mix(
                        MenuStyle.CHIP_ACTIVE, MenuStyle.ACCENT_GREEN, 0.28F)))
                .build());
        MsdfRenderer.renderText(
                MsdfFonts.bold(), worn, 3.9F,
                applyGlobalAlpha(MenuStyle.TEXT_PRIMARY),
                GuiMatrix.mat4(context.getMatrices()),
                MenuStyle.centerMsdfTextX(
                        MsdfFonts.bold(), worn, 3.9F, badgeX, badgeW),
                MenuStyle.centerMsdfTextY(3.9F, cardY + 4.0F, 8.0F) - 0.25F,
                0.0F
        );
    }

    private static String catalogItemLabel(CatalogItem item) {
        if (!item.locked) return item.label;
        boolean russian = Lang.RU.equals(Lang.getActive());
        return switch (item.id) {
            case "Dragon Wings" -> russian ? "Драконьи крылья" : "Dragon Wings";
            case "Nebula Wings" -> russian ? "Крылья туманности" : "Nebula Wings";
            case "Frost Wings" -> russian ? "Морозные крылья" : "Frost Wings";
            case "Infernal Wings" -> russian ? "Адские крылья" : "Infernal Wings";
            default -> item.label;
        };
    }

    private void renderCatalogModelPreview(DrawContext context, String selection,
                                           float cardX, float cardY, float cardW, float cardH,
                                           long frameId) {
        if (globalAlpha < 0.02F) return;
        float previewX = cardX + 4.0F;
        float previewY = cardY + 3.0F;
        float previewW = cardW - 8.0F;
        float previewH = cardH - 21.0F;
        if (!CosmeticsRenderer.renderCatalogModel(
                context, selection,
                previewX, previewY, previewW, previewH,
                globalAlpha, frameId
        )) {
            // Models are submitted through the 1.21.11 special-GUI path by
            // CosmeticsRenderer. Keep a readable fallback while an imported
            // model is still being loaded.
            drawWingThumbnail(context, cardX + cardW * 0.5F,
                    cardY + 24.0F, 0xFF8BA0B0, 0xFF566673, false);
        }
    }

    private void drawWingThumbnail(DrawContext context, float centerX, float centerY,
                                   int primary, int accent, boolean muted) {
        int feather = applyGlobalAlpha(muted ? MenuStyle.mix(primary, MenuStyle.CARD_DISABLED, 0.65F) : primary);
        int frame = applyGlobalAlpha(muted ? MenuStyle.mix(accent, MenuStyle.CARD_DISABLED, 0.65F) : accent);
        for (int side : new int[]{-1, 1}) {
            for (int i = 0; i < 5; i++) {
                float distance = 4.0F + i * 4.0F;
                float featherX = centerX + side * distance - (side < 0 ? 5.0F : 0.0F);
                float featherY = centerY - 8.0F + i * 2.4F;
                rectangle.render(ShapeProperties.create(context.getMatrices(), featherX, featherY, 5.0F, 15.0F - i)
                        .round(2.2F).color(feather).build());
            }
            float frameX = centerX + side * 4.0F - (side < 0 ? 17.0F : 0.0F);
            rectangle.render(ShapeProperties.create(context.getMatrices(), frameX, centerY - 10.0F, 17.0F, 2.2F)
                    .round(1.0F).color(frame).build());
        }
    }

    private void renderPreview(DrawContext context, Layout layout, int mouseX, int mouseY) {
        float panelX = layout.previewX;
        float panelH = previewPanelHeight(layout);
        float panelY = previewPanelY(layout);
        rectangle.render(ShapeProperties.create(context.getMatrices(), panelX, panelY, PREVIEW_WIDTH, panelH)
                .round(5.0F).thickness(1.6F)
                .outlineColor(applyGlobalAlpha(MenuStyle.BORDER))
                .color(applyGlobalAlpha(MenuStyle.mix(MenuStyle.PANEL_BG, MenuStyle.CARD_BG, 0.42F))).build());

        String title = Lang.t("cosmetics.preview");
        MsdfRenderer.renderText(
                MsdfFonts.bold(), title, LABEL_SIZE, applyGlobalAlpha(MenuStyle.TEXT_MUTED),
                GuiMatrix.mat4(context.getMatrices()), panelX + 9.0F, panelY + 8.0F, 0.0F
        );

        renderPlayerPreview(context, panelX, panelY);

        String rotateHint = draggingPreview
                ? Lang.t("cosmetics.rotate")
                : Lang.t("cosmetics.drag_to_rotate");
        MsdfRenderer.renderText(
                MsdfFonts.medium(), rotateHint, 4.7F, applyGlobalAlpha(MenuStyle.TEXT_MUTED),
                GuiMatrix.mat4(context.getMatrices()),
                MenuStyle.centerMsdfTextX(MsdfFonts.medium(), rotateHint, 4.7F, panelX, PREVIEW_WIDTH),
                panelY + 124.0F, 0.0F
        );

        float infoY = panelY + 138.0F;
        String cosmeticName =
                CosmeticsState.displayNameFor(previewSelection).toUpperCase(Locale.ROOT);
        String shownName = cosmeticName.length() > 24
                ? cosmeticName.substring(0, 21) + "..." : cosmeticName;
        MsdfRenderer.renderText(
                MsdfFonts.bold(), shownName, 7.4F,
                applyGlobalAlpha(MenuStyle.TEXT_PRIMARY),
                GuiMatrix.mat4(context.getMatrices()), panelX + 9.0F, infoY, 0.0F
        );
        String[] description = wrapCosmeticDescription(
                CosmeticsState.longDescriptionFor(previewSelection), PREVIEW_WIDTH - 18.0F);
        MsdfRenderer.renderText(MsdfFonts.medium(), description[0], 4.2F,
                applyGlobalAlpha(MenuStyle.TEXT_MUTED), GuiMatrix.mat4(context.getMatrices()),
                panelX + 9.0F, panelY + 148.5F, 0.0F);
        MsdfRenderer.renderText(MsdfFonts.medium(), description[1], 4.2F,
                applyGlobalAlpha(MenuStyle.TEXT_MUTED), GuiMatrix.mat4(context.getMatrices()),
                panelX + 9.0F, panelY + 154.5F, 0.0F);

        CosmeticsState state = CosmeticsState.getInstance();
        if (CosmeticsState.isWing(previewSelection)) {
            float hideY = panelY + 163.0F;
            MsdfRenderer.renderText(
                    MsdfFonts.medium(), Lang.t("cosmetics.hide_elytra"), 5.1F,
                    applyGlobalAlpha(MenuStyle.TEXT_MUTED),
                    GuiMatrix.mat4(context.getMatrices()), panelX + 9.0F,
                    hideY + 4.0F, 0.0F
            );
            hideElytraToggle.position(layout.toggleX, hideY + 1.5F);
            hideElytraToggle.setAlpha(globalAlpha);
            hideElytraToggle.setState(state.isHideElytra());
            hideElytraToggle.render(context, mouseX, mouseY, 0.0F);

            float motionY = panelY + 176.0F;
            String motion = Lang.t("cosmetics.wing_motion");
            MsdfRenderer.renderText(
                    MsdfFonts.medium(), motion, 4.8F, applyGlobalAlpha(MenuStyle.TEXT_MUTED),
                    GuiMatrix.mat4(context.getMatrices()), panelX + 9.0F, motionY, 0.0F
            );
            drawMotionSlider(context, layout, panelY + 185.0F, mouseX);
        } else if (CosmeticsState.isPet(previewSelection)) {
            float followY = panelY + 163.0F;
            MsdfRenderer.renderText(
                    MsdfFonts.medium(), Lang.t("cosmetics.pet_follow"),
                    4.8F, applyGlobalAlpha(MenuStyle.TEXT_MUTED),
                    GuiMatrix.mat4(context.getMatrices()),
                    panelX + 9.0F, followY, 0.0F
            );
            drawPetFollowSlider(
                    context, layout, panelY + 172.0F, mouseX
            );
        }

        boolean equipped = state.isEquipped(previewSelection);
        float buttonY = panelY + panelH - 29.0F;
        float buttonX = panelX + 9.0F;
        float buttonW = PREVIEW_WIDTH - 18.0F;
        boolean hovered = MathUtil.isHovered(mouseX, mouseY, buttonX, buttonY, buttonW, 20.0F);
        int buttonColor = equipped
                ? MenuStyle.mix(MenuStyle.PANEL_CHIP, MenuStyle.CHIP_ACTIVE, 0.18F)
                : MenuStyle.mix(MenuStyle.CHIP_ACTIVE, MenuStyle.ACCENT_GREEN, 0.28F);
        if (hovered) buttonColor = MenuStyle.mix(buttonColor, MenuStyle.TEXT_PRIMARY, 0.08F);
        rectangle.render(ShapeProperties.create(context.getMatrices(), buttonX, buttonY, buttonW, 20.0F)
                .round(3.0F).thickness(1.6F)
                .outlineColor(applyGlobalAlpha(MenuStyle.mix(MenuStyle.BORDER_LIGHT, MenuStyle.CHIP_ACTIVE, 0.50F)))
                .color(applyGlobalAlpha(buttonColor)).build());
        String action = equipped ? Lang.t("cosmetics.remove") : Lang.t("cosmetics.equip");
        MsdfRenderer.renderText(
                MsdfFonts.bold(), action, 6.2F, applyGlobalAlpha(MenuStyle.TEXT_PRIMARY),
                GuiMatrix.mat4(context.getMatrices()),
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), action, 6.2F, buttonX, buttonW),
                MenuStyle.centerMsdfTextY(6.2F, buttonY, 20.0F), 0.0F
        );
    }

    private void renderPlayerPreview(DrawContext context, float panelX, float panelY) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || !CosmeticsState.getInstance().isAvailable(previewSelection)) {
            String unavailable = player == null
                    ? Lang.t("cosmetics.enter_world")
                    : Lang.t("cosmetics.model_missing");
            MsdfRenderer.renderText(
                    MsdfFonts.medium(), unavailable, 4.8F, applyGlobalAlpha(MenuStyle.TEXT_MUTED),
                    GuiMatrix.mat4(context.getMatrices()),
                    MenuStyle.centerMsdfTextX(MsdfFonts.medium(), unavailable, 4.8F,
                            panelX, PREVIEW_WIDTH),
                    panelY + 65.0F, 0.0F
            );
            return;
        }

        // Render the player directly. The former screen-region cache copied
        // the panel/world background together with the entity and clipped wide
        // wings to PREVIEW_WIDTH, which caused the rectangular seam. Card
        // models are cached separately, so this one live entity is no longer
        // multiplied by every catalog item.
        BatchedRectangle.flushIfBatching();
        renderPlayerPreviewEntity(
                context, player, panelX, panelY, globalAlpha
        );
    }

    private void renderPlayerPreviewEntity(
            DrawContext context,
            ClientPlayerEntity player,
            float panelX,
            float panelY,
            float alpha
    ) {
        Quaternionf bodyRotation = new Quaternionf()
                .rotateZ((float) Math.PI)
                .rotateY((float) Math.toRadians(previewYaw));
        // Keep the player at one size for every yaw. The cosmetic selection is
        // carried by the render state because GUI entities are rasterised only
        // after the screen has finished submitting its DrawContext commands.
        float previewScale = 43.0F;
        EntityPose previousPose = player.getPose();
        EntityRenderState renderState;
        try {
            player.setPose(EntityPose.STANDING);
            renderState = captureEntityRenderState(player);
        } finally {
            player.setPose(previousPose);
        }
        PreviewMarker marker = (PreviewMarker) (Object) renderState;
        marker.phaze$previewSelection(previewSelection);
        marker.phaze$previewYaw(previewYaw);
        marker.phaze$previewAlpha(alpha);
        int x1 = Math.round(panelX + 5.0F);
        int x2 = Math.round(panelX + PREVIEW_WIDTH - 5.0F);
        int y1 = Math.round(panelY + 20.0F);
        int y2 = Math.round(panelY + 121.0F);
        float translationY = CosmeticsState.isCape(previewSelection) ? 1.05F : 1.15F;
        context.addEntity(renderState, previewScale,
                new Vector3f(0.0F, translationY, 0.0F), bodyRotation,
                new Quaternionf(), x1, y1, x2, y2);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static EntityRenderState captureEntityRenderState(ClientPlayerEntity player) {
        EntityRenderManager dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        EntityRenderer renderer = dispatcher.getRenderer(player);
        EntityRenderState state = renderer.getAndUpdateRenderState(player, 1.0F);
        state.light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        state.shadowPieces.clear();
        state.outlineColor = EntityRenderState.NO_OUTLINE;
        return state;
    }

    private static List<String> wrapPreviewName(String text, float size, float maxWidth) {
        if (MsdfFonts.bold().getWidth(text, size) <= maxWidth) {
            return List.of(text);
        }
        String[] words = text.split("\\s+");
        StringBuilder first = new StringBuilder();
        StringBuilder second = new StringBuilder();
        for (String word : words) {
            String candidate = first.isEmpty() ? word : first + " " + word;
            if (second.isEmpty() && MsdfFonts.bold().getWidth(candidate, size) <= maxWidth) {
                first.setLength(0);
                first.append(candidate);
            } else {
                if (!second.isEmpty()) second.append(' ');
                second.append(word);
            }
        }
        if (first.isEmpty()) first.append(text);
        if (second.isEmpty()) return List.of(first.toString());
        return List.of(first.toString(), second.toString());
    }

    private static String[] wrapCosmeticDescription(String text, float maxWidth) {
        if (text == null || text.isBlank()) return new String[]{"", ""};
        String[] words = text.trim().split("\\s+");
        StringBuilder first = new StringBuilder();
        int index = 0;
        while (index < words.length) {
            String candidate = first.isEmpty() ? words[index] : first + " " + words[index];
            if (!first.isEmpty() && MsdfFonts.medium().getWidth(candidate, 4.2F) > maxWidth) break;
            first.setLength(0);
            first.append(candidate);
            index++;
        }
        StringBuilder second = new StringBuilder();
        while (index < words.length) {
            String candidate = second.isEmpty() ? words[index] : second + " " + words[index];
            if (!second.isEmpty()
                    && MsdfFonts.medium().getWidth(candidate + "…", 4.2F) > maxWidth) {
                second.append('…');
                break;
            }
            second.setLength(0);
            second.append(candidate);
            index++;
        }
        return new String[]{first.toString(), second.toString()};
    }

    private void drawMotionSlider(DrawContext context, Layout layout, float sliderY, int mouseX) {
        CosmeticsState state = CosmeticsState.getInstance();
        float percentValue = layout.sliderWidth * state.getWingMotion() / 16.0F;
        float difference = MathHelper.clamp(mouseX - layout.sliderX, 0.0F, layout.sliderWidth);
        if (sliderAnimation < 0.0D) sliderAnimation = percentValue;
        sliderAnimation = MathUtil.interpolateSmooth(
                2.5D,
                sliderAnimation,
                draggingMotion ? difference : percentValue
        );

        int background = applyGlobalAlpha(
                MenuStyle.mix(MenuStyle.PANEL_CHIP, MenuStyle.CARD_OPTIONS, 0.35F)
        );
        int sliderColor = applyGlobalAlpha(MenuStyle.CHIP_ACTIVE);
        int thumbBackground = applyGlobalAlpha(MenuStyle.PANEL_BG);
        rectangle.render(ShapeProperties.create(
                        context.getMatrices(), layout.sliderX, sliderY - 1.0F,
                        layout.sliderWidth, 2.0F)
                .round(1.0F).color(background).build());

        float fillWidth = MathHelper.clamp(
                (float) sliderAnimation, 0.0F, layout.sliderWidth
        );
        if (fillWidth > 0.0F) {
            rectangle.render(ShapeProperties.create(
                            context.getMatrices(), layout.sliderX, sliderY - 1.0F,
                            fillWidth, 2.0F)
                    .round(1.0F).color(sliderColor).build());
        }

        float thumbX = MathHelper.clamp(
                layout.sliderX + (float) sliderAnimation,
                layout.sliderX,
                layout.sliderX + layout.sliderWidth
        );
        rectangle.render(ShapeProperties.create(context.getMatrices(), thumbX - 4.0F, sliderY - 4.0F, 8.0F, 8.0F)
                .round(4.0F).thickness(1.0F)
                .outlineColor(thumbBackground)
                .color(sliderColor).build());

        if (draggingMotion) {
            updateMotion(mouseX, layout);
        }
        String value = String.format(Locale.ROOT, "%.1f°", state.getWingMotion());
        MsdfRenderer.renderText(
                MsdfFonts.medium(), value, 4.5F, applyGlobalAlpha(MenuStyle.TEXT_MUTED),
                GuiMatrix.mat4(context.getMatrices()),
                layout.sliderX + layout.sliderWidth - MsdfFonts.medium().getWidth(value, 4.5F),
                sliderY + 6.0F, 0.0F
        );
    }

    private void drawPetFollowSlider(
            DrawContext context,
            Layout layout,
            float sliderY,
            int mouseX
    ) {
        CosmeticsState state = CosmeticsState.getInstance();
        float percentValue = layout.sliderWidth
                * state.getPetFollowIntensity();
        float difference = MathHelper.clamp(
                mouseX - layout.sliderX, 0.0F, layout.sliderWidth
        );
        if (petSliderAnimation < 0.0D) {
            petSliderAnimation = percentValue;
        }
        petSliderAnimation = MathUtil.interpolateSmooth(
                2.5D,
                petSliderAnimation,
                draggingPetFollow ? difference : percentValue
        );

        int background = applyGlobalAlpha(
                MenuStyle.mix(
                        MenuStyle.PANEL_CHIP,
                        MenuStyle.CARD_OPTIONS,
                        0.35F
                )
        );
        int sliderColor = applyGlobalAlpha(MenuStyle.CHIP_ACTIVE);
        int thumbBackground = applyGlobalAlpha(MenuStyle.PANEL_BG);
        rectangle.render(ShapeProperties.create(
                        context.getMatrices(), layout.sliderX,
                        sliderY - 1.0F, layout.sliderWidth, 2.0F)
                .round(1.0F).color(background).build());

        float fillWidth = MathHelper.clamp(
                (float) petSliderAnimation, 0.0F, layout.sliderWidth
        );
        if (fillWidth > 0.0F) {
            rectangle.render(ShapeProperties.create(
                            context.getMatrices(), layout.sliderX,
                            sliderY - 1.0F, fillWidth, 2.0F)
                    .round(1.0F).color(sliderColor).build());
        }
        float thumbX = MathHelper.clamp(
                layout.sliderX + (float) petSliderAnimation,
                layout.sliderX,
                layout.sliderX + layout.sliderWidth
        );
        rectangle.render(ShapeProperties.create(
                        context.getMatrices(), thumbX - 4.0F,
                        sliderY - 4.0F, 8.0F, 8.0F)
                .round(4.0F).thickness(1.0F)
                .outlineColor(thumbBackground)
                .color(sliderColor).build());

        if (draggingPetFollow) {
            updatePetFollow(mouseX, layout);
        }
        String value = Math.round(
                state.getPetFollowIntensity() * 100.0F
        ) + "%";
        MsdfRenderer.renderText(
                MsdfFonts.medium(), value, 4.5F,
                applyGlobalAlpha(MenuStyle.TEXT_MUTED),
                GuiMatrix.mat4(context.getMatrices()),
                layout.sliderX + layout.sliderWidth
                        - MsdfFonts.medium().getWidth(value, 4.5F),
                sliderY + 6.0F, 0.0F
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open || button != 0) return false;
        Layout layout = layout();

        String[] categories = {"ALL", "WINGS", "CAPES", "HATS", "PETS"};
        float categoryY = layout.cardsTop;
        for (String candidate : categories) {
            if (MathUtil.isHovered(mouseX, mouseY, layout.categoryX, categoryY, CATEGORY_WIDTH, 24.0F)) {
                category = candidate;
                catalogScroll = 0.0D;
                smoothedCatalogScroll = 0.0D;
                playButtonClickSound();
                return true;
            }
            categoryY += 28.0F;
        }

        float cardGap = 6.0F;
        float cardW = (layout.catalogWidth - cardGap) / 2.0F;
        float cardH = 61.0F;
        List<CatalogItem> items = allCatalogItems();
        float viewportHeight = catalogViewportHeight(layout);
        if (!MathUtil.isHovered(mouseX, mouseY, layout.catalogX, layout.cardsTop,
                layout.catalogWidth, viewportHeight)) {
            items = List.of();
        }
        for (int i = 0; i < items.size(); i++) {
            CatalogItem item = items.get(i);
            float cardX = layout.catalogX + (i % 2) * (cardW + cardGap);
            float cardY = layout.cardsTop + (i / 2) * (cardH + cardGap)
                    + (float) smoothedCatalogScroll;
            if (MathUtil.isHovered(mouseX, mouseY, cardX, cardY, cardW, cardH)) {
                if (!item.locked && CosmeticsState.getInstance().isAvailable(item.id)) {
                    previewSelection = item.id;
                    playButtonClickSound();
                }
                return true;
            }
        }

        float previewY = previewPanelY(layout);
        if (MathUtil.isHovered(mouseX, mouseY, layout.previewX + 5.0F, previewY + 20.0F,
                PREVIEW_WIDTH - 10.0F, 112.0F)) {
            draggingPreview = true;
            return true;
        }

        if (CosmeticsState.isWing(previewSelection)) {
            if (hideElytraToggle.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            float sliderY = previewY + 185.0F;
            if (MathUtil.isHovered(mouseX, mouseY, layout.sliderX - 3.0F, sliderY - 5.0F,
                    layout.sliderWidth + 6.0F, 13.0F)) {
                draggingMotion = true;
                updateMotion(mouseX, layout);
                return true;
            }
        } else if (CosmeticsState.isPet(previewSelection)) {
            float sliderY = previewY + 172.0F;
            if (MathUtil.isHovered(
                    mouseX, mouseY,
                    layout.sliderX - 3.0F, sliderY - 5.0F,
                    layout.sliderWidth + 6.0F, 13.0F
            )) {
                draggingPetFollow = true;
                updatePetFollow(mouseX, layout);
                return true;
            }
        }

        float buttonY = previewY + previewPanelHeight(layout) - 29.0F;
        if (MathUtil.isHovered(mouseX, mouseY, layout.previewX + 9.0F, buttonY,
                PREVIEW_WIDTH - 18.0F, 20.0F)) {
            CosmeticsState state = CosmeticsState.getInstance();
            boolean alreadyEquipped = state.isEquipped(previewSelection);
            if (alreadyEquipped) {
                state.setEquipped(previewSelection, false);
            } else {
                state.setSelected(previewSelection);
                state.setEquipped(previewSelection, true);
            }
            playButtonClickSound();
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!open || button != 0) return false;
        if (draggingPreview) {
            previewYaw += (float) deltaX * 2.2F;
            return true;
        }
        if (draggingMotion) {
            updateMotion(mouseX, layout());
            return true;
        }
        if (draggingPetFollow) {
            updatePetFollow(mouseX, layout());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && (draggingPreview || draggingMotion
                || draggingPetFollow)) {
            draggingPreview = false;
            draggingMotion = false;
            draggingPetFollow = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!open) return false;
        Layout layout = layout();
        if (!MathUtil.isHovered(mouseX, mouseY, layout.catalogX, layout.cardsTop,
                layout.catalogWidth, catalogViewportHeight(layout))) {
            return false;
        }
        double maxScroll = catalogMaxScroll(layout, allCatalogItems().size(), 61.0F, 6.0F);
        if (maxScroll <= 0.0D) return false;
        catalogScroll = Math.max(
                -maxScroll,
                Math.min(0.0D, catalogScroll + amount * 34.0D)
        );
        return true;
    }

    private void updateMotion(double mouseX, Layout layout) {
        float progress = (float) ((mouseX - layout.sliderX) / layout.sliderWidth);
        progress = Math.max(0.0F, Math.min(1.0F, progress));
        float stepped = Math.round(progress * 32.0F) / 2.0F;
        CosmeticsState.getInstance().setWingMotion(stepped);
    }

    private void updatePetFollow(double mouseX, Layout layout) {
        float progress = (float) (
                (mouseX - layout.sliderX) / layout.sliderWidth
        );
        progress = MathHelper.clamp(progress, 0.0F, 1.0F);
        float stepped = Math.round(progress * 20.0F) / 20.0F;
        CosmeticsState.getInstance().setPetFollowIntensity(stepped);
    }

    private static float catalogViewportHeight(Layout layout) {
        return Math.max(1.0F, layout.top + layout.contentHeight - layout.cardsTop);
    }

    private static double catalogMaxScroll(
            Layout layout, int itemCount, float cardHeight, float cardGap
    ) {
        int rows = (itemCount + 1) / 2;
        float contentHeight = rows <= 0
                ? 0.0F
                : rows * cardHeight + (rows - 1) * cardGap;
        return Math.max(0.0F, contentHeight - catalogViewportHeight(layout));
    }

    private List<CatalogItem> allCatalogItems() {
        List<CatalogItem> items = new java.util.ArrayList<>();
        int index = 0;
        for (CosmeticsState.CosmeticEntry entry : CosmeticsState.getInstance().getCatalog()) {
            CosmeticsState.CosmeticType type = CosmeticsState.typeOf(entry.name());
            if (!"ALL".equals(category) && !switch (category) {
                case "WINGS" -> type == CosmeticsState.CosmeticType.WING;
                case "CAPES" -> type == CosmeticsState.CosmeticType.CAPE;
                case "HATS" -> type == CosmeticsState.CosmeticType.HAT;
                case "PETS" -> type == CosmeticsState.CosmeticType.PET;
                default -> false;
            }) continue;
            int[] colors = cosmeticColors(entry.name(), index++);
            items.add(new CatalogItem(entry.name(), CosmeticsState.displayNameFor(entry.name()), false, colors[0], colors[1]));
        }
        return items;
    }

    private static float previewPanelHeight(Layout layout) {
        return Math.min(layout.contentHeight, 228.0F);
    }

    private static float previewPanelY(Layout layout) {
        return layout.top + layout.contentHeight - previewPanelHeight(layout);
    }

    private int[] cosmeticColors(String name, int index) {
        if (CosmeticsState.WIMGS.equalsIgnoreCase(name)) {
            return new int[]{0xFFDDE6E8, 0xFFE0AA3A};
        }
        if (CosmeticsState.FLUFFY_WINGS.equalsIgnoreCase(name)) {
            return new int[]{0xFFE3A8B9, 0xFFD889A2};
        }
        int[][] palette = {
                {0xFF7BBBD0, 0xFF386B85},
                {0xFF8F7BC8, 0xFF4D3E83},
                {0xFF72B9A6, 0xFF2C6A60},
                {0xFFD29A64, 0xFF805332}
        };
        return palette[Math.floorMod(index, palette.length)];
    }

    private Layout layout() {
        float contentX = x + SIDE_MARGIN;
        float top = y + HEADER_OFFSET;
        float contentWidth = width - SIDE_MARGIN * 2.0F;
        float contentHeight = height - HEADER_OFFSET - 9.0F;
        float categoryX = contentX;
        float catalogX = categoryX + CATEGORY_WIDTH + GAP;
        float previewX = x + width - SIDE_MARGIN - PREVIEW_WIDTH;
        float catalogWidth = previewX - GAP - catalogX;
        float cardsTop = top + 25.0F;
        float toggleX = previewX + PREVIEW_WIDTH - 27.0F;
        float sliderX = previewX + 9.0F;
        float sliderWidth = PREVIEW_WIDTH - 18.0F;
        return new Layout(
                top, contentHeight, categoryX, catalogX, catalogWidth,
                cardsTop, previewX, toggleX, sliderX, sliderWidth
        );
    }

    private record CatalogItem(String id, String label, boolean locked, int primary, int accent) {
    }

    private record VisibleCard(
            CatalogItem item,
            float x,
            float y,
            float width,
            float height,
            boolean available,
            boolean selected,
            boolean equipped,
            float selectionProgress,
            String label,
            int textColor
    ) {
    }

    private record Layout(
            float top,
            float contentHeight,
            float categoryX,
            float catalogX,
            float catalogWidth,
            float cardsTop,
            float previewX,
            float toggleX,
            float sliderX,
            float sliderWidth
    ) {
    }

    private static final class CardAnimations {
        private final Animation hover = new DecelerateAnimation().setMs(150).setValue(1);
        private final Animation selection = new DecelerateAnimation().setMs(180).setValue(1);

        private CardAnimations(boolean hovered, boolean selected) {
            hover.setDirectionAndFinish(hovered ? Direction.FORWARDS : Direction.BACKWARDS);
            selection.setDirectionAndFinish(selected ? Direction.FORWARDS : Direction.BACKWARDS);
        }
    }
}
