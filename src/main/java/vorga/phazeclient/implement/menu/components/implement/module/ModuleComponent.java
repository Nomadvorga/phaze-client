package vorga.phazeclient.implement.menu.components.implement.module;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.feature.module.setting.SettingComponentAdder;
import vorga.phazeclient.api.feature.module.setting.implement.*;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.snapshot.CardSnapshotCache;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.base.util.other.StringUtil;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.AbstractComponent;
import vorga.phazeclient.implement.menu.components.implement.settings.AbstractSettingComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static vorga.phazeclient.api.system.font.Fonts.Type.INTER_BOLD;

@Getter
public class ModuleComponent extends AbstractComponent {
    private static final Gson GSON_COMPACT = new Gson();
    private static final long COPY_PASTE_COOLDOWN_MS = 150;
    private static final float TITLE_TEXT_SIZE = 8.4F;
    private static final float ROW_TEXT_SIZE = 6.45F;
    private static final float BIND_TEXT_SIZE = 5.8F;
    private static final float CARD_INSET = 1.3F;
    private static final float STANDALONE_OPTIONS_RADIUS = 5.2F;
    private static final float OPTIONS_ROW_HEIGHT = 18.0F;
    private static final float ENABLED_ROW_HEIGHT = 18.0F;
    private static final float BASE_HEIGHT_ENABLED = 86.0F;
    private static final float BASE_HEIGHT_SIMPLE = 62.0F;
    private static final float BASE_HEIGHT_OPTIONS_ONLY = BASE_HEIGHT_ENABLED - ENABLED_ROW_HEIGHT;

    private final List<AbstractSettingComponent> components = new ArrayList<>();
    private final Module module;

    private boolean binding;
    private boolean isHovered = false;

    private long lastCopyTime = 0;
    private long lastPasteTime = 0;

    private final Animation hoverAnimation = new DecelerateAnimation().setMs(200).setValue(1);
    private final Animation optionsHoverAnimation = new DecelerateAnimation().setMs(180).setValue(1);
    private final Animation outlineColorAnimation = new DecelerateAnimation().setMs(300).setValue(1);
    private final Animation stateRowHoverAnimation = new DecelerateAnimation().setMs(180).setValue(1);

    private float visualX, visualY;
    private float prevX, prevY;
    private boolean lastModuleState;

    public ModuleComponent(Module module) {
        this.module = module;
        new SettingComponentAdder().addSettingComponent(module.settings(), components);
        this.visualX = this.prevX = 0;
        this.visualY = this.prevY = 0;
        this.lastModuleState = module.isState();
        this.hoverAnimation.setDirectionAndFinish(Direction.BACKWARDS);
        this.optionsHoverAnimation.setDirectionAndFinish(Direction.BACKWARDS);
        this.stateRowHoverAnimation.setDirectionAndFinish(Direction.BACKWARDS);
        this.outlineColorAnimation.setDirectionAndFinish(module.isState() ? Direction.FORWARDS : Direction.BACKWARDS);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Always run lifecycle bookkeeping first so the rest of the
        // menu (mouseClicked, isHover) sees consistent component
        // bounds whether or not the cache short-circuits the draw.
        visualX = x;
        visualY = y;
        height = getComponentHeight();

        boolean inSearchMode = isInSearchMode();
        // preUpdateAnimations runs every frame, even on a cache hit.
        // It mirrors all setDirection calls that the legacy render
        // body buried inside renderOptionsRow / renderStateRow / the
        // inSearchMode block at the top of render(). Pulling them
        // out here is what lets canUseCache() inspect FRESH animation
        // state (each anim's isDone()) and correctly decide that no
        // transitions are in flight before reading from the FBO -
        // otherwise the cache would always look "stable" because the
        // setDirection calls happen INSIDE the immediate render that
        // we are about to skip.
        preUpdateAnimations(mouseX, mouseY, inSearchMode);

        if (canUseCache()) {
            renderViaCache(context, mouseX, mouseY, delta, inSearchMode);
        } else {
            renderImmediate(context, mouseX, mouseY, delta, inSearchMode);
        }
    }

    /**
     * Mirrors every {@code setDirection} / {@code setDirectionAndFinish}
     * the legacy render path applied to {@code hoverAnimation},
     * {@code optionsHoverAnimation}, {@code outlineColorAnimation} and
     * {@code stateRowHoverAnimation}. Centralizing these here means a
     * cache-hit frame (which skips {@link #renderImmediate}) still
     * advances animation state, so the moment any animation does
     * actually start running we see it in {@code canUseCache()} and
     * fall back to the eager path on the very next frame.
     *
     * <p>The duplicate {@code setDirection} calls still remaining
     * inside {@link #renderOptionsRow} and the inline block before
     * {@link #renderStateRow} are intentionally left in place: they
     * are idempotent (setting the same direction twice is a no-op
     * inside {@code Animation.setDirection}) and removing them would
     * couple the helper methods to the precondition that this
     * pre-update ran first, making accidental future call-site
     * changes harder to validate.
     */
    private void preUpdateAnimations(int mouseX, int mouseY, boolean inSearchMode) {
        this.isHovered = isHover(mouseX, mouseY);
        hoverAnimation.setDirection(isHovered ? Direction.FORWARDS : Direction.BACKWARDS);

        // Dynamic cursor: a hovered module card is clickable (toggle /
        // open settings), so request the hand shape. Commit happens at
        // ScreenCursorMixin TAIL.
        if (isHovered) {
            vorga.phazeclient.api.system.cursor.CursorManager.requestHand();
        }

        boolean currentModuleState = module.isState();
        if (currentModuleState != lastModuleState) {
            outlineColorAnimation.setDirection(currentModuleState ? Direction.FORWARDS : Direction.BACKWARDS);
            lastModuleState = currentModuleState;
        }

        // Replicate the row-hover bounds checks that renderOptionsRow
        // and the renderStateRow caller block do, so canUseCache
        // sees the right direction when the cursor crosses a row
        // boundary even on a cache-hit frame.
        float baseHeight = getBaseHeightFloat();
        boolean showStateRow = module.isShowEnable();
        float optionsY = y + baseHeight - (showStateRow ? (OPTIONS_ROW_HEIGHT + ENABLED_ROW_HEIGHT) : OPTIONS_ROW_HEIGHT);

        if (hasSettings()) {
            boolean standaloneRow = !showStateRow;
            float rowX = standaloneRow ? x : x + CARD_INSET;
            float rowWidth = standaloneRow ? width : width - CARD_INSET * 2.0F;
            boolean rowHovered = MathUtil.isHovered(mouseX, mouseY, rowX, optionsY, rowWidth, OPTIONS_ROW_HEIGHT);
            optionsHoverAnimation.setDirection(rowHovered ? Direction.FORWARDS : Direction.BACKWARDS);
        }

        if (showStateRow) {
            float enabledY = optionsY + OPTIONS_ROW_HEIGHT;
            boolean stateRowHovered = MathUtil.isHovered(mouseX, mouseY, x, enabledY, width, ENABLED_ROW_HEIGHT);
            stateRowHoverAnimation.setDirection(stateRowHovered ? Direction.FORWARDS : Direction.BACKWARDS);
        }
    }

    /**
     * Master switch for the per-card FBO snapshot cache. Currently
     * latched OFF because the capture path (begin/end inside
     * {@link CardSnapshotCache}) is producing empty / mis-colored
     * snapshots in the live menu - cards rendered cleanly during
     * hover animation (which forces the eager {@link #renderImmediate}
     * path) but disappeared the moment {@link #canUseCache} returned
     * true and the cache blitted its FBO content. The fix for the
     * SDF-rect framebuffer-height mismatch
     * ({@link vorga.phazeclient.api.system.shape.batched.BatchedRectangle#setRenderTargetFbHeight})
     * did not resolve the visible-pixel symptoms, so until the capture
     * pass is debugged with proper instrumentation the cache is held
     * inert at the canUseCache boundary - {@code renderImmediate} runs
     * every frame, just like the legacy pre-cache code path. Other
     * pieces of the cache infrastructure (CardSnapshotCache class, the
     * BatchedRectangle FB-height override, the cache invalidation
     * hook in CategoryContainerComponent) stay wired in place so
     * flipping this flag back to {@code true} is the only edit needed
     * to re-enable it once the underlying issue is found.
     */
    private static final boolean CARD_SNAPSHOT_CACHE_ENABLED = false;

    /**
     * Returns true iff every input that affects the captured FBO
     * pixels is in a stable, non-animating state. While ANY animation
     * is mid-transition (hover fade-in / fade-out, enabled-state
     * outline color, options-row hover, state-row hover) the captured
     * pixels would change every frame - we'd thrash the FBO with
     * captures and gain nothing. Same logic for the menu's open/close
     * fade: while {@code globalAlpha} is animating we render eagerly
     * and let the cache repopulate once the menu is fully visible.
     *
     * <p>{@code binding} is treated as un-cacheable not because the
     * pixels change every frame (they don't - the bind chip just
     * shows "(NAME) ...") but because key-press transitions back to
     * a normal bind name, and the user typically clicks bind expecting
     * an immediate visual update on the very next frame.
     */
    private boolean canUseCache() {
        if (!CARD_SNAPSHOT_CACHE_ENABLED) {
            return false;
        }
        if (globalAlpha < 0.999F) {
            return false;
        }
        if (binding) {
            return false;
        }
        return hoverAnimation.isDone()
                && optionsHoverAnimation.isDone()
                && outlineColorAnimation.isDone()
                && stateRowHoverAnimation.isDone();
    }

    /**
     * Cache-aware render path. Hashes every visual input the
     * captured FBO depends on, looks up (or allocates) a per-card
     * {@link CardSnapshotCache.Snapshot}, re-captures only when the
     * hash mismatches the previously baked content, and always blits
     * the FBO to the main framebuffer at the card's screen position.
     *
     * <p>The capture path translates the matrix stack by {@code (-x, -y, 0)}
     * so that the renderImmediate body, which writes positions in
     * absolute screen coords like {@code (x, y, x+width, y+height)},
     * lands at FBO-local (0, 0, width, height) under the card-local
     * orthographic projection that {@link CardSnapshotCache#beginCapture}
     * sets up.
     */
    private void renderViaCache(DrawContext context, int mouseX, int mouseY, float delta, boolean inSearchMode) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) {
            renderImmediate(context, mouseX, mouseY, delta, inSearchMode);
            return;
        }
        // FBO size in FRAMEBUFFER pixels: card width/height are in GUI
        // pixels, multiplied by the window's scale factor to match the
        // resolution at which the eager path would have rasterized the
        // same card on the main FB. Without scaling, the cached card
        // would look pixelated relative to the rest of the menu on
        // GUI-scale > 1 setups.
        int scale = Math.max(1, (int) Math.ceil(mc.getWindow().getScaleFactor()));
        int fbW = Math.max(1, Math.round(width * scale));
        int fbH = Math.max(1, Math.round(height * scale));

        int hash = computeStateHash();
        CardSnapshotCache.Snapshot snapshot = CardSnapshotCache.getOrCreate(this, fbW, fbH);

        if (!snapshot.populated || snapshot.hash != hash) {
            CardSnapshotCache.beginCapture(snapshot, width, height);
            try {
                context.getMatrices().push();
                context.getMatrices().translate(-x, -y, 0.0F);
                renderImmediate(context, mouseX, mouseY, delta, inSearchMode);
                context.getMatrices().pop();
                snapshot.hash = hash;
            } finally {
                CardSnapshotCache.endCapture();
            }
        }

        CardSnapshotCache.blit(context, snapshot, x, y, width, height, globalAlpha);
    }

    /**
     * Hash of every visual input that determines the captured pixels.
     * Anything NOT in the hash must either be invariant per
     * {@code ModuleComponent} instance (e.g. the localized module
     * name, icon path - those are owned by the module ref which is
     * itself the cache key's identity) or be quantized into a
     * stable bucket. Animation outputs are bucketed to 8 steps for
     * defensive parity but in practice {@link #canUseCache} already
     * gates this hash to settled animations (output ~0 or ~1), so the
     * bucket value is constant within the cache's freshness window.
     */
    private int computeStateHash() {
        int h = 1;
        h = h * 31 + Boolean.hashCode(module.isState());
        h = h * 31 + Boolean.hashCode(module.isCanBind());
        h = h * 31 + Boolean.hashCode(module.isShowEnable());
        h = h * 31 + Boolean.hashCode(module.isServerLocked());
        h = h * 31 + Integer.hashCode(module.getKey());
        h = h * 31 + Boolean.hashCode(hasSettings());
        h = h * 31 + Boolean.hashCode(this.isHovered);
        h = h * 31 + Boolean.hashCode(binding);

        h = h * 31 + Float.floatToIntBits(width);
        h = h * 31 + Float.floatToIntBits(height);

        // Every MenuStyle palette field that the card content reads
        // either directly or indirectly. If any of these change
        // (palette switch / theme transition) the captured pixels
        // are stale and we re-render.
        h = h * 31 + Integer.hashCode(MenuStyle.BORDER);
        h = h * 31 + Integer.hashCode(MenuStyle.BORDER_LIGHT);
        h = h * 31 + Integer.hashCode(MenuStyle.CARD_OPTIONS);
        h = h * 31 + Integer.hashCode(MenuStyle.PANEL_CHIP);
        h = h * 31 + Integer.hashCode(MenuStyle.CHIP_ACTIVE);
        h = h * 31 + Integer.hashCode(MenuStyle.TEXT_PRIMARY);
        h = h * 31 + Integer.hashCode(MenuStyle.TEXT_MUTED);
        h = h * 31 + Integer.hashCode(MenuStyle.ACCENT_GREEN);

        // Animation buckets. Defensive: canUseCache already requires
        // isDone() on all four, so these are stable within the
        // capture window. Including them protects against a future
        // edit relaxing canUseCache without remembering to update
        // the hash.
        h = h * 31 + (int) (hoverAnimation.getOutputFloat() * 8.0F);
        h = h * 31 + (int) (optionsHoverAnimation.getOutputFloat() * 8.0F);
        h = h * 31 + (int) (outlineColorAnimation.getOutputFloat() * 8.0F);
        h = h * 31 + (int) (stateRowHoverAnimation.getOutputFloat() * 8.0F);

        return h;
    }

    /**
     * The legacy eager render body, extracted verbatim so it can be
     * called either directly (cache miss / not-cacheable) or through
     * the FBO capture path. State-mutating {@code setDirection} calls
     * inside the helpers are now duplicates of {@link #preUpdateAnimations}
     * - left in place because they're idempotent and the helpers
     * stay independent of caller order.
     */
    private void renderImmediate(DrawContext context, int mouseX, int mouseY, float delta, boolean inSearchMode) {
        float offsetX = visualX - x;
        float offsetY = visualY - y;

        float hoverProgress = hoverAnimation.getOutputFloat();
        float baseHeight = getBaseHeightFloat();
        boolean showOptionsRow = hasSettings();
        boolean showStateRow = module.isShowEnable();
        float optionsY = y + baseHeight - (showStateRow ? (OPTIONS_ROW_HEIGHT + ENABLED_ROW_HEIGHT) : OPTIONS_ROW_HEIGHT);

        context.getMatrices().push();
        context.getMatrices().translate(offsetX, offsetY, 0);

        int outlineColor = MenuStyle.mix(MenuStyle.BORDER, MenuStyle.CHIP_ACTIVE, outlineColorAnimation.getOutputFloat() * 0.75f);
        outlineColor = MenuStyle.withAlpha(outlineColor, applyGlobalAlpha(0.96F));
        int hoverFillColor = MenuStyle.withAlpha(0xFFFFFFFF, applyGlobalAlpha(hoverProgress * 0.06F));
        rectangle.render(ShapeProperties.create(context.getMatrices(), x, y, width, height)
                .round(8).softness(1).thickness(3.6F).outlineColor(outlineColor).color(hoverFillColor).build());

        float iconSize = module.getIconSize();
        float iconX = x + (width - iconSize) / 2.0F;
        float iconY = y + 12.0F + module.getIconOffsetY();
        String iconTexture = module.getIcon() != null ? "phaze:textures/modules/" + module.getIcon() : "textures/modules/" + module.getCategory().getIdentifier() + ".png";
        image.setTexture(iconTexture)
                .render(ShapeProperties.create(context.getMatrices(), iconX, iconY, iconSize, iconSize)
                        .color(applyGlobalAlpha(MenuStyle.TEXT_PRIMARY)).build());

        String moduleName = module.getLocalizedName();
        float titleAreaY = y + 31.0F;
        float titleAreaBottom = showOptionsRow ? optionsY - 1.0F : y + baseHeight - 7.0F;
        float titleAreaHeight = Math.max(10.0F, titleAreaBottom - titleAreaY);
        MsdfRenderer.renderText(
                MsdfFonts.medium(),
                moduleName,
                TITLE_TEXT_SIZE,
                applyGlobalAlpha(MenuStyle.TEXT_MUTED),
                context.getMatrices().peek().getPositionMatrix(),
                MenuStyle.centerMsdfTextX(MsdfFonts.medium(), moduleName, TITLE_TEXT_SIZE, x, width),
                MenuStyle.centerMsdfTextY(TITLE_TEXT_SIZE, titleAreaY, titleAreaHeight),
                0.0F
        );

        if (showOptionsRow) {
            renderOptionsRow(context, mouseX, mouseY, optionsY, !showStateRow, inSearchMode);
        }

        if (showStateRow) {
            float enabledY = optionsY + OPTIONS_ROW_HEIGHT;
            boolean stateRowHovered = MathUtil.isHovered(mouseX, mouseY, x, enabledY, width, ENABLED_ROW_HEIGHT);
            stateRowHoverAnimation.setDirection(stateRowHovered ? Direction.FORWARDS : Direction.BACKWARDS);
            renderStateRow(context, enabledY, stateRowHoverAnimation.getOutputFloat());
        }

        if (showStateRow && module.isCanBind()) {
            drawBind(context, x, y);
        }

        context.getMatrices().pop();
    }

    private void renderOptionsRow(DrawContext context, int mouseX, int mouseY, float optionsY, boolean standaloneRow, boolean inSearchMode) {
        boolean hasSettings = hasSettings();
        int baseRowColor = hasSettings
                ? MenuStyle.CARD_OPTIONS
                : MenuStyle.mix(MenuStyle.CARD_OPTIONS, MenuStyle.PANEL_CHIP, 0.45F);
        float iconSectionWidth = 26.0F;
        float rowX = standaloneRow ? x : x + CARD_INSET;
        float rowWidth = standaloneRow ? width : width - CARD_INSET * 2.0F;
        float dividerX = rowX + rowWidth - iconSectionWidth;
        boolean rowHovered = MathUtil.isHovered(mouseX, mouseY, rowX, optionsY, rowWidth, OPTIONS_ROW_HEIGHT);
        optionsHoverAnimation.setDirection(rowHovered ? Direction.FORWARDS : Direction.BACKWARDS);
        float rowHoverProgress = optionsHoverAnimation.getOutputFloat();
        int rowColor = MenuStyle.mix(baseRowColor, MenuStyle.TEXT_PRIMARY, rowHoverProgress * 0.055F);
        int borderColor = MenuStyle.withAlpha(MenuStyle.BORDER_LIGHT, applyGlobalAlpha(standaloneRow ? (0.45F + rowHoverProgress * 0.17F) : (0.35F + rowHoverProgress * 0.13F)));

        rectangle.render(ShapeProperties.create(context.getMatrices(), rowX, optionsY, rowWidth, OPTIONS_ROW_HEIGHT)
                .round(0, standaloneRow ? STANDALONE_OPTIONS_RADIUS : 0, 0, standaloneRow ? STANDALONE_OPTIONS_RADIUS : 0).color(applyGlobalAlpha(rowColor)).build());

        rectangle.render(ShapeProperties.create(context.getMatrices(), rowX, optionsY, rowWidth, 1.0F)
                .color(borderColor).build());

        rectangle.render(ShapeProperties.create(context.getMatrices(), dividerX, optionsY, 1.0F, OPTIONS_ROW_HEIGHT - (standaloneRow ? 0.35F : 0.0F))
                .color(MenuStyle.withAlpha(MenuStyle.BORDER_LIGHT, applyGlobalAlpha(0.55F + rowHoverProgress * 0.15F))).build());

        String optionsLabel = "OPTIONS";
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                optionsLabel,
                ROW_TEXT_SIZE,
                applyGlobalAlpha(hasSettings ? MenuStyle.mix(MenuStyle.TEXT_PRIMARY, 0xFFFFFFFF, rowHoverProgress * 0.10F) : MenuStyle.mix(MenuStyle.TEXT_MUTED, 0xFFFFFFFF, rowHoverProgress * 0.08F)),
                context.getMatrices().peek().getPositionMatrix(),
                rowX + 11.0F,
                MenuStyle.centerMsdfTextY(ROW_TEXT_SIZE, optionsY, OPTIONS_ROW_HEIGHT),
                0.0F
        );

        if (hasSettings) {
            image.setTexture("textures/settings.png")
                    .render(ShapeProperties.create(context.getMatrices(), dividerX + (iconSectionWidth - 9.0F) / 2.0F, optionsY + (OPTIONS_ROW_HEIGHT - 9.0F) / 2.0F, 9.0F, 9.0F)
                            .color(applyGlobalAlpha(MenuStyle.mix(MenuStyle.TEXT_PRIMARY, 0xFFFFFFFF, rowHoverProgress * 0.10F)))
                            .build());
        }
    }

    private void renderStateRow(DrawContext context, float enabledY, float hoverProgress) {
        boolean locked = module.isServerLocked();
        int stateColor;
        String stateText;

        if (locked) {
            stateColor = applyGlobalAlpha(0xFF888986);
            stateText = "LOCKED";
        } else {
            float stateProgress = outlineColorAnimation.getOutputFloat();
            int base = MenuStyle.mix(0xFFA01E40, MenuStyle.ACCENT_GREEN, stateProgress);
            stateColor = applyGlobalAlpha(MenuStyle.mix(base, MenuStyle.TEXT_PRIMARY, hoverProgress * 0.15F));
            stateText = module.isState() ? "ENABLED" : "DISABLED";
        }

        rectangle.render(ShapeProperties.create(context.getMatrices(), x + CARD_INSET, enabledY, width - CARD_INSET * 2.0F, ENABLED_ROW_HEIGHT - 1)
                .round(0, 7, 0, 7).color(stateColor).build());

        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                stateText,
                ROW_TEXT_SIZE,
                applyGlobalAlpha(MenuStyle.TEXT_PRIMARY),
                context.getMatrices().peek().getPositionMatrix(),
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), stateText, ROW_TEXT_SIZE, x, width),
                MenuStyle.centerMsdfTextY(ROW_TEXT_SIZE, enabledY, ENABLED_ROW_HEIGHT - 1),
                0.0F
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float baseHeight = getBaseHeightFloat();
        boolean showOptionsRow = hasSettings();
        boolean showStateRow = module.isShowEnable();
        float optionsY = y + baseHeight - (showStateRow ? (OPTIONS_ROW_HEIGHT + ENABLED_ROW_HEIGHT) : OPTIONS_ROW_HEIGHT);
        float enabledY = optionsY + OPTIONS_ROW_HEIGHT;
        boolean optionsHovered = MathUtil.isHovered(mouseX, mouseY, x, optionsY, width, OPTIONS_ROW_HEIGHT);
        boolean settingsClick = button == 0 || button == 1;

        if (showOptionsRow && settingsClick && optionsHovered) {
            playButtonClickSound();
            MenuScreen.INSTANCE.openModuleDetail(module);
            return true;
        }

        if (showStateRow && button == 0 && MathUtil.isHovered(mouseX, mouseY, x, enabledY, width, ENABLED_ROW_HEIGHT)) {
            if (module.isServerLocked()) {
                return true;
            }
            playButtonClickSound();
            module.switchState();
            return true;
        }

        if (showStateRow && module.isCanBind()) {
            String bindName = StringUtil.getBindName(module.getKey());
            float stringWidth = MsdfFonts.bold().getWidth(bindName, BIND_TEXT_SIZE);
            if (MathUtil.isHovered(mouseX, mouseY, x + width - 15 - stringWidth, y + 8, stringWidth + 6, 9) && button == 0) {
                playButtonClickSound();
                binding = !binding;
                return true;
            } else if (binding) {
                module.setKey(button);
                binding = false;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        MenuScreen menuScreen = MenuScreen.INSTANCE;
        float menuTop = menuScreen.y;
        float menuBottom = menuScreen.y + menuScreen.height;
        float menuLeft = menuScreen.x;
        float menuRight = menuScreen.x + menuScreen.width;

        if (mouseX < menuLeft || mouseX > menuRight || mouseY < menuTop || mouseY > menuBottom) {
            return false;
        }

        boolean hovered = MathUtil.isHovered(mouseX, mouseY, x, y, width, height);
        if (!hovered) {
            return false;
        }

        float visibleTop = Math.max(y, menuTop);
        float visibleBottom = Math.min(y + height, menuBottom);
        return mouseY >= visibleTop && mouseY <= visibleBottom;
    }

    private boolean hasSettings() {
        return !components.isEmpty();
    }

    private float getBaseHeightFloat() {
        if (module.isShowEnable()) {
            return BASE_HEIGHT_ENABLED;
        }
        return hasSettings() ? BASE_HEIGHT_OPTIONS_ONLY : BASE_HEIGHT_SIMPLE;
    }

    public int getComponentHeight() {
        return Math.round(getBaseHeightFloat());
    }

    private boolean isInSearchMode() {
        return MenuScreen.INSTANCE != null && MenuScreen.INSTANCE.getCategory() == ModuleCategory.SEARCH;
    }

    private void drawBind(DrawContext context, float renderX, float renderY) {
        String bindName = StringUtil.getBindName(module.getKey());
        String name = binding ? "(" + bindName + ") ..." : bindName;
        float stringWidth = MsdfFonts.bold().getWidth(name, BIND_TEXT_SIZE);
        float bindX = renderX + width - stringWidth - 18.0F;
        float bindY = renderY + 6.0F;

        rectangle.render(ShapeProperties.create(context.getMatrices(), bindX, bindY, stringWidth + 8.0F, 10.0F)
                .round(2).thickness(1.2F).outlineColor(applyGlobalAlpha(MenuStyle.BORDER)).color(applyGlobalAlpha(MenuStyle.PANEL_CHIP)).build());

        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                name,
                BIND_TEXT_SIZE,
                applyGlobalAlpha(MenuStyle.TEXT_MUTED),
                context.getMatrices().peek().getPositionMatrix(),
                bindX + 4.0F,
                MenuStyle.centerMsdfTextY(BIND_TEXT_SIZE, bindY, 10.0F),
                0.0F
        );
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int key = keyCode == GLFW.GLFW_KEY_DELETE ? -1 : keyCode;
        if (binding && module.isCanBind()) {
            module.setKey(key);
            binding = false;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_C && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (isHovered) {
                copyModuleConfig();
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            pasteModuleConfig();
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void copyModuleConfig() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCopyTime < COPY_PASTE_COOLDOWN_MS) {
            return;
        }
        lastCopyTime = currentTime;

        try {
            JsonObject rootObject = new JsonObject();
            JsonObject moduleData = new JsonObject();

            rootObject.addProperty("moduleId", module.getIdentifier());
            rootObject.addProperty("moduleName", module.getName());

            moduleData.addProperty("enabled", module.isState());
            moduleData.addProperty("bind", module.getKey());

            for (Setting setting : module.settings()) {
                saveSetting(setting, moduleData);
            }

            rootObject.add("config", moduleData);

            String jsonString = GSON_COMPACT.toJson(rootObject);

            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) {
                return;
            }

            long windowHandle = client.getWindow().getHandle();
            GLFW.glfwSetClipboardString(windowHandle, jsonString);
        } catch (Exception ignored) {
        }
    }

    private void pasteModuleConfig() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPasteTime < COPY_PASTE_COOLDOWN_MS) {
            return;
        }
        lastPasteTime = currentTime;

        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) {
                return;
            }

            long windowHandle = client.getWindow().getHandle();
            String jsonString = GLFW.glfwGetClipboardString(windowHandle);

            if (jsonString == null || jsonString.trim().isEmpty()) {
                return;
            }

            JsonObject rootObject = GSON_COMPACT.fromJson(jsonString.trim(), JsonObject.class);
            if (!rootObject.has("moduleId") || !rootObject.has("config")) {
                return;
            }

            String clipboardModuleId = rootObject.get("moduleId").getAsString();
            if (!clipboardModuleId.equals(module.getIdentifier())) {
                return;
            }

            JsonObject moduleData = rootObject.getAsJsonObject("config");

            if (moduleData.has("enabled")) {
                module.setStateSilent(moduleData.get("enabled").getAsBoolean());
            }

            if (moduleData.has("bind")) {
                module.setKey(moduleData.get("bind").getAsInt());
            }

            for (Setting setting : module.settings()) {
                try {
                    loadSetting(setting, moduleData);
                } catch (Exception ignored) {
                }
            }

            if (module.isEnabled()) {
                module.setStateSilent(true);
            }
        } catch (Exception ignored) {
        }
    }

    private void saveSetting(Setting setting, JsonObject moduleData) {
        if (!setting.isSaveToConfig()) {
            return;
        }

        String key = setting.getNameKey();

        switch (setting) {
            case BooleanSetting booleanSetting -> moduleData.addProperty(key, booleanSetting.isValue());
            case ValueSetting valueSetting -> moduleData.addProperty(key, valueSetting.getValue());
            case TextSetting textSetting -> {
                String value = textSetting.getText();
                value = value.replace(" ", "%%").replace("/", "++");
                moduleData.addProperty(key, value);
            }
            case BindSetting bindSetting -> moduleData.addProperty(key, bindSetting.getKey());
            case ColorSetting colorSetting -> moduleData.addProperty(key, colorSetting.getColor());
            case SelectSetting selectSetting -> moduleData.addProperty(key, selectSetting.getSelected());
            case MultiSelectSetting multiSelectSetting -> moduleData.addProperty(key, String.join(",", multiSelectSetting.getSelected()));
            case ItemPickerSetting itemPickerSetting -> {
                if (itemPickerSetting.isCustomPicker() && !itemPickerSetting.isActive()) {
                    moduleData.addProperty(key, false);
                    return;
                }
                JsonObject itemObject = new JsonObject();
                itemObject.addProperty("active", itemPickerSetting.isActive());
                itemObject.addProperty("enabled", itemPickerSetting.isEnabled());
                itemObject.addProperty("highlightColor", itemPickerSetting.getHighlightColor());
                itemObject.addProperty("itemId", itemPickerSetting.getItemId());
                itemObject.addProperty("displayName", itemPickerSetting.getDisplayName());
                itemObject.addProperty("matchName", itemPickerSetting.getMatchName());
                moduleData.add(key, itemObject);
            }
            case MultiColorSetting multiColor -> {
                JsonObject colorObject = new JsonObject();
                colorObject.addProperty("selectedColorIndex", multiColor.getSelectedColorIndex());

                com.google.gson.JsonArray colorsArray = new com.google.gson.JsonArray();
                for (ColorSetting color : multiColor.getAllColors()) {
                    colorsArray.add(color.getColor());
                }
                colorObject.add("colors", colorsArray);
                moduleData.add(key, colorObject);
            }
            case GroupSetting group -> {
                JsonObject groupObject = new JsonObject();
                groupObject.addProperty("state", group.isValue());

                for (Setting subSetting : group.getSubSettings()) {
                    saveSetting(subSetting, groupObject);
                }

                moduleData.add(key, groupObject);
            }
            default -> {
            }
        }
    }

    private void loadSetting(Setting setting, JsonObject moduleData) {
        if (!setting.isSaveToConfig()) {
            return;
        }

        String key = setting.getNameKey();
        if (!moduleData.has(key)) {
            return;
        }

        com.google.gson.JsonElement element = moduleData.get(key);

        switch (setting) {
            case BooleanSetting booleanSetting -> booleanSetting.setValue(element.getAsBoolean());
            case ValueSetting valueSetting -> valueSetting.setValue(element.getAsFloat());
            case TextSetting textSetting -> {
                String value = element.getAsString();
                value = value.replace("%%", " ").replace("++", "/");
                textSetting.setText(value);
            }
            case BindSetting bindSetting -> bindSetting.setKey(element.getAsInt());
            case ColorSetting colorSetting -> colorSetting.setColor(element.getAsInt());
            case SelectSetting selectSetting -> selectSetting.setSelected(element.getAsString());
            case MultiSelectSetting multiSelectSetting -> {
                String value = element.getAsString();
                List<String> selected = new ArrayList<>(Arrays.asList(value.split(",")));
                selected.removeIf(s -> !multiSelectSetting.getList().contains(s));
                multiSelectSetting.setSelected(selected);
            }
            case ItemPickerSetting itemPickerSetting -> {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
                    itemPickerSetting.setEnabled(element.getAsBoolean());
                    return;
                }
                if (!element.isJsonObject()) {
                    return;
                }
                JsonObject itemObject = element.getAsJsonObject();
                String itemId = itemObject.has("itemId") ? itemObject.get("itemId").getAsString() : "";
                String displayName = itemObject.has("displayName") ? itemObject.get("displayName").getAsString() : "";
                String matchName = itemObject.has("matchName") ? itemObject.get("matchName").getAsString() : "";
                boolean enabled = itemObject.has("enabled")
                        ? itemObject.get("enabled").getAsBoolean()
                        : itemPickerSetting.isEnabled();
                int highlightColor = itemObject.has("highlightColor")
                        ? itemObject.get("highlightColor").getAsInt()
                        : itemPickerSetting.getHighlightColor();
                boolean active = itemObject.has("active")
                        ? itemObject.get("active").getAsBoolean()
                        : !itemId.isBlank();
                itemPickerSetting.setSerializedState(active, itemId, displayName, matchName, enabled, highlightColor);
            }
            case MultiColorSetting multiColor -> {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                    int oldColor = element.getAsInt();
                    if (multiColor.getColor1() != null) {
                        multiColor.getColor1().setColor(oldColor);
                    }
                    return;
                }

                JsonObject colorObject = element.getAsJsonObject();
                if (colorObject.has("selectedColorIndex")) {
                    multiColor.setSelectedColorIndex(colorObject.get("selectedColorIndex").getAsInt());
                }

                if (colorObject.has("colors")) {
                    com.google.gson.JsonArray colorsArray = colorObject.getAsJsonArray("colors");
                    List<ColorSetting> colorSettings = multiColor.getAllColors();

                    for (int i = 0; i < Math.min(colorsArray.size(), colorSettings.size()); i++) {
                        colorSettings.get(i).setColor(colorsArray.get(i).getAsInt());
                    }
                }
            }
            case GroupSetting group -> {
                JsonObject groupObject = element.getAsJsonObject();
                if (groupObject.has("state")) {
                    group.setValue(groupObject.get("state").getAsBoolean());
                }

                for (Setting subSetting : group.getSubSettings()) {
                    loadSetting(subSetting, groupObject);
                }
            }
            default -> {
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModuleComponent that = (ModuleComponent) o;
        return module.equals(that.module);
    }

    @Override
    public int hashCode() {
        return Objects.hash(module);
    }
}
