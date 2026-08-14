package vorga.phazeclient.implement.menu.components.implement.other;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.api.system.snapshot.CardSnapshotCache;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.cosmetics.CosmeticsRenderer;
import vorga.phazeclient.implement.cosmetics.CosmeticsState;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.UiMsdfIconAtlas;
import vorga.phazeclient.implement.menu.components.AbstractComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
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

    private static final List<CatalogItem> LOCKED_ITEMS = List.of(
            new CatalogItem("Dragon Wings", "Dragon Wings", true, 0xFF59636A, 0xFF31383D),
            new CatalogItem("Nebula Wings", "Nebula Wings", true, 0xFF62528E, 0xFF2E2850),
            new CatalogItem("Frost Wings", "Frost Wings", true, 0xFF7BBBD0, 0xFF386B85),
            new CatalogItem("Infernal Wings", "Infernal Wings", true, 0xFFC9683D, 0xFF77301F)
    );

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
    private final Map<String, Object> catalogPreviewCacheKeys = new HashMap<>();
    private final Set<String> livePreviewFallback = new HashSet<>();
    private final Object playerPreviewCacheKey = new Object();
    private long lastPlayerPreviewCaptureNs;

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
        livePreviewFallback.clear();
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
        MatrixStack matrices = context.getMatrices();
        MsdfRenderer.renderText(
                MsdfFonts.bold(), Lang.t("cosmetics.title"), TITLE_SIZE, applyGlobalAlpha(MenuStyle.TEXT_PRIMARY),
                matrices.peek().getPositionMatrix(), layout.catalogX, layout.top, 0.0F
        );
        MsdfRenderer.renderText(
                MsdfFonts.medium(), Lang.t("cosmetics.subtitle"), SUBTITLE_SIZE,
                applyGlobalAlpha(MenuStyle.TEXT_MUTED), matrices.peek().getPositionMatrix(),
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
                    context.getMatrices().peek().getPositionMatrix(),
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
                context.getMatrices().peek().getPositionMatrix(),
                layout.catalogX, layout.cardsTop, layout.catalogWidth, viewportHeight
        );
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
                    card.x, card.y, card.width, card.height
            );
        }
        context.draw();

        // Submit text and markers after models so thumbnails cannot cover UI.
        for (VisibleCard card : visibleCards) {
            MsdfRenderer.renderText(
                    MsdfFonts.medium(), card.label, CARD_LABEL_SIZE,
                    applyGlobalAlpha(card.textColor),
                    context.getMatrices().peek().getPositionMatrix(),
                    MenuStyle.centerMsdfTextX(
                            MsdfFonts.medium(), card.label, CARD_LABEL_SIZE,
                            card.x, card.width),
                    card.y + card.height - 14.0F, 0.0F
            );

            if (!card.equipped && card.item.locked) {
                String locked = Lang.t("cosmetics.locked");
                MsdfRenderer.renderText(
                        MsdfFonts.bold(), locked, 4.2F, applyGlobalAlpha(MenuStyle.TEXT_MUTED),
                        context.getMatrices().peek().getPositionMatrix(),
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
        RenderSystem.disableDepthTest();
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 600.0F);
        for (VisibleCard card : visibleCards) {
            if (!card.item.locked && card.equipped) {
                renderEquippedBadge(context, card.x, card.y, card.width);
            }
        }
        BatchedRectangle.flushIfBatching();
        context.getMatrices().pop();
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
                context.getMatrices().peek().getPositionMatrix(),
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
                                           float cardX, float cardY, float cardW, float cardH) {
        if (globalAlpha < 0.02F) return;
        float previewX = cardX + 4.0F;
        float previewY = cardY + 3.0F;
        float previewW = cardW - 8.0F;
        float previewH = cardH - 21.0F;
        String normalizedSelection = selection.toLowerCase(Locale.ROOT);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            CosmeticsRenderer.renderCatalogModel(
                    context, selection,
                    previewX, previewY, previewW, previewH,
                    globalAlpha
            );
            return;
        }
        if (livePreviewFallback.contains(normalizedSelection)) {
            CosmeticsRenderer.renderCatalogModel(
                    context, selection,
                    previewX, previewY, previewW, previewH,
                    globalAlpha
            );
            return;
        }

        float framebufferScale = (float) client.getWindow().getScaleFactor();
        Object cacheKey = catalogPreviewCacheKeys.computeIfAbsent(
                normalizedSelection, ignored -> new Object()
        );
        CardSnapshotCache.Snapshot snapshot = CardSnapshotCache.getOrCreate(
                cacheKey,
                Math.max(1, Math.round(previewW * framebufferScale)),
                Math.max(1, Math.round(previewH * framebufferScale)),
                true
        );
        int visualHash = 31 * normalizedSelection.hashCode()
                + CosmeticsState.typeOf(selection).ordinal();
        if (!snapshot.populated || snapshot.hash != visualHash) {
            boolean rendered = false;
            CardSnapshotCache.beginCapture(snapshot, previewW, previewH);
            try {
                // Use a genuinely card-local DrawContext. Mutating the
                // page's shared MatrixStack was not sufficient because its
                // provider retained the outer GUI pass state; that produced
                // transparent snapshots even for plain cape textures.
                DrawContext captureContext = new DrawContext(
                        client,
                        client.getBufferBuilders().getEntityVertexConsumers()
                );
                rendered = CosmeticsRenderer.renderCatalogModel(
                        captureContext, selection,
                        0.0F, 0.0F, previewW, previewH,
                        1.0F
                );
                captureContext.draw();
            } finally {
                CardSnapshotCache.endCapture();
            }
            // A model may still be loading/invalid on the first catalog
            // frame. Do not permanently cache that transparent attempt.
            if (rendered && CardSnapshotCache.hasVisiblePixels(snapshot)) {
                snapshot.hash = visualHash;
            } else {
                snapshot.populated = false;
                livePreviewFallback.add(normalizedSelection);
            }
        }
        CardSnapshotCache.blit(
                context, snapshot,
                previewX, previewY, previewW, previewH,
                globalAlpha
        );
    }

    private static void captureCatalogSnapshots(
            DrawContext context,
            List<PendingCatalogSnapshot> pending
    ) {
        if (pending.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null
                || client.getFramebuffer() == null) return;
        float framebufferScale = (float) client.getWindow().getScaleFactor();
        int framebufferHeight = client.getWindow().getFramebufferHeight();
        for (PendingCatalogSnapshot capture : pending) {
            Vector3f topLeft = context.getMatrices().peek().getPositionMatrix()
                    .transformPosition(new Vector3f(capture.box.x, capture.box.y, 0.0F));
            Vector3f bottomRight = context.getMatrices().peek().getPositionMatrix()
                    .transformPosition(new Vector3f(
                            capture.box.x + capture.box.width,
                            capture.box.y + capture.box.height,
                            0.0F
                    ));
            int srcX = Math.max(0, Math.round(
                    Math.min(topLeft.x, bottomRight.x) * framebufferScale
            ));
            int srcTop = Math.max(0, Math.round(
                    Math.min(topLeft.y, bottomRight.y) * framebufferScale
            ));
            int srcWidth = Math.max(1, Math.round(
                    Math.abs(bottomRight.x - topLeft.x) * framebufferScale
            ));
            int srcHeight = Math.max(1, Math.round(
                    Math.abs(bottomRight.y - topLeft.y) * framebufferScale
            ));
            int srcY = Math.max(0, framebufferHeight - srcTop - srcHeight);
            CardSnapshotCache.copyRegionFromFramebuffer(
                    capture.snapshot,
                    client.getFramebuffer(),
                    srcX, srcY, srcWidth, srcHeight
            );
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
        float panelY = layout.top;
        float panelH = layout.contentHeight;
        rectangle.render(ShapeProperties.create(context.getMatrices(), panelX, panelY, PREVIEW_WIDTH, panelH)
                .round(5.0F).thickness(1.6F)
                .outlineColor(applyGlobalAlpha(MenuStyle.BORDER))
                .color(applyGlobalAlpha(MenuStyle.mix(MenuStyle.PANEL_BG, MenuStyle.CARD_BG, 0.42F))).build());

        String title = Lang.t("cosmetics.preview");
        MsdfRenderer.renderText(
                MsdfFonts.bold(), title, LABEL_SIZE, applyGlobalAlpha(MenuStyle.TEXT_MUTED),
                context.getMatrices().peek().getPositionMatrix(), panelX + 9.0F, panelY + 8.0F, 0.0F
        );

        float previewBottom = panelY + 133.0F;
        renderPlayerPreview(context, panelX + PREVIEW_WIDTH / 2.0F, previewBottom);

        String rotateHint = draggingPreview
                ? Lang.t("cosmetics.rotate")
                : Lang.t("cosmetics.drag_to_rotate");
        MsdfRenderer.renderText(
                MsdfFonts.medium(), rotateHint, 4.7F, applyGlobalAlpha(MenuStyle.TEXT_MUTED),
                context.getMatrices().peek().getPositionMatrix(),
                MenuStyle.centerMsdfTextX(MsdfFonts.medium(), rotateHint, 4.7F, panelX, PREVIEW_WIDTH),
                panelY + 136.0F, 0.0F
        );

        float infoY = panelY + 150.0F;
        String cosmeticName =
                CosmeticsState.displayNameFor(previewSelection).toUpperCase(Locale.ROOT);
        List<String> nameLines = wrapPreviewName(cosmeticName, 7.4F, PREVIEW_WIDTH - 18.0F);
        for (int i = 0; i < nameLines.size(); i++) {
            MsdfRenderer.renderText(
                    MsdfFonts.bold(), nameLines.get(i), 7.4F,
                    applyGlobalAlpha(MenuStyle.TEXT_PRIMARY),
                    context.getMatrices().peek().getPositionMatrix(),
                    panelX + 9.0F, infoY + i * 9.0F, 0.0F
            );
        }

        CosmeticsState state = CosmeticsState.getInstance();
        if (CosmeticsState.isWing(previewSelection)) {
            float hideY = panelY + 174.0F;
            MsdfRenderer.renderText(
                    MsdfFonts.medium(), Lang.t("cosmetics.hide_elytra"), 5.1F,
                    applyGlobalAlpha(MenuStyle.TEXT_MUTED),
                    context.getMatrices().peek().getPositionMatrix(), panelX + 9.0F,
                    MenuStyle.centerMsdfTextY(5.1F, hideY, 13.0F), 0.0F
            );
            hideElytraToggle.position(layout.toggleX, hideY + 1.5F);
            hideElytraToggle.setAlpha(globalAlpha);
            hideElytraToggle.setState(state.isHideElytra());
            hideElytraToggle.render(context, mouseX, mouseY, 0.0F);

            float motionY = panelY + 193.0F;
            String motion = Lang.t("cosmetics.wing_motion");
            MsdfRenderer.renderText(
                    MsdfFonts.medium(), motion, 4.8F, applyGlobalAlpha(MenuStyle.TEXT_MUTED),
                    context.getMatrices().peek().getPositionMatrix(), panelX + 9.0F, motionY, 0.0F
            );
            drawMotionSlider(context, layout, motionY + 9.0F, mouseX);
        } else if (CosmeticsState.isPet(previewSelection)) {
            float followY = panelY + 174.0F;
            MsdfRenderer.renderText(
                    MsdfFonts.medium(), Lang.t("cosmetics.pet_follow"),
                    4.8F, applyGlobalAlpha(MenuStyle.TEXT_MUTED),
                    context.getMatrices().peek().getPositionMatrix(),
                    panelX + 9.0F, followY, 0.0F
            );
            drawPetFollowSlider(
                    context, layout, followY + 9.0F, mouseX
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
                context.getMatrices().peek().getPositionMatrix(),
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), action, 6.2F, buttonX, buttonW),
                MenuStyle.centerMsdfTextY(6.2F, buttonY, 20.0F), 0.0F
        );
    }

    private void renderPlayerPreview(DrawContext context, float centerX, float bottomY) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || !CosmeticsState.getInstance().isAvailable(previewSelection)) {
            String unavailable = player == null
                    ? Lang.t("cosmetics.enter_world")
                    : Lang.t("cosmetics.model_missing");
            MsdfRenderer.renderText(
                    MsdfFonts.medium(), unavailable, 4.8F, applyGlobalAlpha(MenuStyle.TEXT_MUTED),
                    context.getMatrices().peek().getPositionMatrix(),
                    MenuStyle.centerMsdfTextX(MsdfFonts.medium(), unavailable, 4.8F,
                            centerX - PREVIEW_WIDTH / 2.0F, PREVIEW_WIDTH),
                    bottomY - 65.0F, 0.0F
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
                context, player, centerX, bottomY, globalAlpha
        );
    }

    private void renderPlayerPreviewEntity(
            DrawContext context,
            ClientPlayerEntity player,
            float centerX,
            float bottomY,
            float alpha
    ) {
        Quaternionf bodyRotation = new Quaternionf()
                .rotateZ((float) Math.PI)
                .rotateY((float) Math.toRadians(previewYaw));
        // Keep the player at one size for every yaw. Wide cosmetics may extend
        // outside the preview panel (the scissor is disabled below), so fitting
        // their rotated bounds by shrinking the whole player is unnecessary.
        float previewScale = 43.0F;
        EntityPose previousPose = player.getPose();
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] scissor = scissorEnabled ? new int[4] : null;
        if (scissorEnabled) {
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
            // Wide cosmetics are deliberately allowed to extend beyond the
            // preview panel. A parent GUI scissor otherwise hides them until
            // the animated page/card boundary happens to move past a wing.
            RenderSystem.disableScissor();
        }
        try {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            player.setPose(EntityPose.STANDING);
            CosmeticsRenderer.renderPreview(previewSelection, alpha, previewYaw, () ->
                    InventoryScreen.drawEntity(
                            context, centerX, bottomY, previewScale,
                            new Vector3f(0.0F, 0.0F, 0.0F),
                            bodyRotation, new Quaternionf(), player
                    )
            );
        } finally {
            player.setPose(previousPose);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            if (scissorEnabled) {
                RenderSystem.enableScissor(
                        scissor[0], scissor[1], scissor[2], scissor[3]
                );
            }
        }
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
                context.getMatrices().peek().getPositionMatrix(),
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
                context.getMatrices().peek().getPositionMatrix(),
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

        if (MathUtil.isHovered(mouseX, mouseY, layout.previewX + 5.0F, layout.top + 20.0F,
                PREVIEW_WIDTH - 10.0F, 140.0F)) {
            draggingPreview = true;
            return true;
        }

        if (CosmeticsState.isWing(previewSelection)) {
            if (hideElytraToggle.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            float sliderY = layout.top + 202.0F;
            if (MathUtil.isHovered(mouseX, mouseY, layout.sliderX - 3.0F, sliderY - 5.0F,
                    layout.sliderWidth + 6.0F, 13.0F)) {
                draggingMotion = true;
                updateMotion(mouseX, layout);
                return true;
            }
        } else if (CosmeticsState.isPet(previewSelection)) {
            float sliderY = layout.top + 183.0F;
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

        float buttonY = layout.top + layout.contentHeight - 29.0F;
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
        if ("ALL".equals(category) || "WINGS".equals(category)) {
            for (CatalogItem locked : LOCKED_ITEMS) {
                if (items.size() >= 6) break;
                boolean duplicate = items.stream().anyMatch(item -> item.id.equalsIgnoreCase(locked.id));
                if (!duplicate) items.add(locked);
            }
        }
        return items;
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

    private record CatalogPreviewBox(float x, float y, float width, float height) {
    }

    private record PendingCatalogSnapshot(
            CardSnapshotCache.Snapshot snapshot,
            CatalogPreviewBox box
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
