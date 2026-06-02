package vorga.phazeclient.implement.menu.components.implement.other;

import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.feature.module.setting.SettingComponentAdder;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.font.msdf.MsdfFont;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.base.util.render.ScissorManager;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.AbstractComponent;
import vorga.phazeclient.implement.menu.components.implement.settings.AbstractSettingComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Getter
public class ModuleDetailComponent extends AbstractComponent {
    private static final float PANEL_PADDING = 12.0F;
    private static final float SETTINGS_TOP = 41.0F;
    private static final float SETTINGS_BOTTOM = 0.0F;
    private static final float COLUMN_GAP = 8.0F;
    private static final float ROW_GAP = 8.0F;
    private static final float BACK_BUTTON_SIZE = 13.0F;
    private static final float DESCRIPTION_TEXT_SIZE = 4.9F;
    private static final float DESCRIPTION_TOP_OFFSET = 11.2F;

    private final List<AbstractSettingComponent> settingComponents = new ArrayList<>();
    private final Animation openAnimation = new DecelerateAnimation().setMs(1).setValue(1);
    private final Animation backHoverAnimation = new DecelerateAnimation().setMs(180).setValue(1);
    // fadeAnimation was previously used to cross-fade the settings
    // panel when the user switched modules / opened SETTINGS. Removed
    // because the user explicitly asked for no fade on tab/category
    // switching - panelAlpha now drives off the open animation alone,
    // which is set to 1ms so the panel + settings + theme controls
    // appear instantly the moment the user clicks SETTINGS, with no
    // perceptible alpha ramp.

    private Module module;
    private double lastMeasuredHeight = 0.0;

    public void open(Module module) {
        if (module == null) {
            return;
        }

        if (this.module != module) {
            settingComponents.clear();
            new SettingComponentAdder().addSettingComponent(module.settings(), settingComponents);
        }

        this.module = module;
        this.scroll = 0.0;
        this.smoothedScroll = 0.0;
        openAnimation.setDirection(Direction.FORWARDS);
        MenuScreen.INSTANCE.getModuleDescriptionComponent().hide();
    }

    public void closeDetail() {
        module = null;
        scroll = 0.0;
        smoothedScroll = 0.0;
    }

    public boolean isOpen() {
        return module != null;
    }

    public float contentX() {
        MenuScreen menuScreen = MenuScreen.INSTANCE;
        return menuScreen.x + 101.0F;
    }

    public float contentY() {
        MenuScreen menuScreen = MenuScreen.INSTANCE;
        return menuScreen.y + 25.5F;
    }

    public float contentWidth() {
        MenuScreen menuScreen = MenuScreen.INSTANCE;
        return menuScreen.width - 102.5F;
    }

    public float contentHeight() {
        MenuScreen menuScreen = MenuScreen.INSTANCE;
        return menuScreen.height - 27.0F;
    }

    private float backButtonX() {
        return contentX() + PANEL_PADDING;
    }

    private float backButtonY() {
        return contentY() + PANEL_PADDING;
    }

    private float settingsPanelX() {
        return contentX() + PANEL_PADDING;
    }

    private float settingsPanelY() {
        return contentY() + SETTINGS_TOP;
    }

    private float settingsPanelWidth() {
        return contentWidth() - PANEL_PADDING * 2.0F;
    }

    private float settingsPanelHeight() {
        return contentHeight() - SETTINGS_TOP - SETTINGS_BOTTOM;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isOpen()) {
            return;
        }

        float anim = openAnimation.getOutputFloat() * globalAlpha;
        renderHeader(context, mouseX, mouseY, anim);
        renderDescription(context, anim);
        renderSettingsPanel(context, mouseX, mouseY, delta, anim);
    }

    private void renderHeader(DrawContext context, int mouseX, int mouseY, float anim) {
        MatrixStack matrices = context.getMatrices();

        boolean backHovered = MathUtil.isHovered(mouseX, mouseY, backButtonX(), backButtonY(), BACK_BUTTON_SIZE, BACK_BUTTON_SIZE);
        backHoverAnimation.setDirection(backHovered ? Direction.FORWARDS : Direction.BACKWARDS);
        float backHover = backHoverAnimation.getOutputFloat();
        int backBg = backHovered ? MenuStyle.mix(MenuStyle.PANEL_CHIP, MenuStyle.CARD_OPTIONS, 0.45F) : MenuStyle.PANEL_CHIP;
        int backBorder = backHovered ? MenuStyle.BORDER_LIGHT : MenuStyle.BORDER;

        rectangle.render(ShapeProperties.create(matrices, backButtonX(), backButtonY(), BACK_BUTTON_SIZE, BACK_BUTTON_SIZE)
                .round(3)
                .thickness(1.2F)
                .outlineColor(MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.PANEL_BG, backBorder, anim), 0.96F * anim))
                .color(MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.PANEL_BG, backBg, anim), 0.92F * anim))
                .build());
        float backIconSize = 7.5F;
        float backIconX = backButtonX() + (BACK_BUTTON_SIZE - backIconSize) / 2.0F;
        float backIconY = backButtonY() + (BACK_BUTTON_SIZE - backIconSize) / 2.0F;
        image.setTexture("textures/back_arrow.png")
                .render(ShapeProperties.create(matrices,
                                backIconX - backIconSize,
                                backIconY,
                                backIconSize,
                                backIconSize)
                        .rotation(0)
                        .color(MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.TEXT_PRIMARY, 0xFFFFFFFF, backHover * 0.18F), anim))
                        .build());

        String title = module.getLocalizedName().toUpperCase(Locale.ROOT);
        float titleX = backButtonX() + BACK_BUTTON_SIZE + 8.0F;
        float titleY;
        titleY = MenuStyle.centerMsdfTextY(8.2F, backButtonY(), BACK_BUTTON_SIZE);
        MsdfRenderer.renderText(MsdfFonts.bold(), title, 8.2F, MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, anim), matrices.peek().getPositionMatrix(), titleX, titleY, 0.0F);
    }

    private void renderDescription(DrawContext context, float anim) {
        MatrixStack matrices = context.getMatrices();
        String description = module.getDescription();
        if (description == null || description.isEmpty()) {
            description = "Custom themes for GUI";
        }
        // Auto-translate the module description through Lang. The
        // table is keyed by the canonical English string itself, so
        // modules don't need to be touched - if a translation exists
        // we render it, otherwise the original English text shows
        // through (Lang.t falls back to the input). Names of modules
        // and categories are intentionally NOT translated.
        description = Lang.translate(description);

        float descX = backButtonX() + BACK_BUTTON_SIZE + 8.0F;
        float descY = backButtonY() + DESCRIPTION_TOP_OFFSET;
        float lineHeight = DESCRIPTION_TEXT_SIZE + 1.6F;
        MsdfFont descFont = MsdfFonts.medium();

        for (String line : wrap(description, descFont, DESCRIPTION_TEXT_SIZE, contentWidth() - PANEL_PADDING * 2.0F - 32.0F)) {
            MsdfRenderer.renderText(
                    descFont,
                    line,
                    DESCRIPTION_TEXT_SIZE,
                    MenuStyle.withAlpha(MenuStyle.TEXT_MUTED, anim),
                    matrices.peek().getPositionMatrix(),
                    descX,
                    descY,
                    0.0F
            );
            descY += lineHeight;
        }
    }

    private void renderSettingsPanel(DrawContext context, int mouseX, int mouseY, float delta, float anim) {
        MatrixStack matrices = context.getMatrices();
        float panelX = settingsPanelX();
        float panelY = settingsPanelY();
        float panelWidth = settingsPanelWidth();
        float panelHeight = settingsPanelHeight();

        float fadeProgress = 1.0F;
        float panelAlpha = Math.max(0.0F, Math.min(1.0F, fadeProgress * anim));

        float innerX = panelX + 2.0F;
        float innerY = panelY + 2.0F;
        float innerWidth = panelWidth - 4.0F;
        float innerHeight = panelHeight - 4.0F;
        // Keep column mode stable for the module; do not switch to single-column
        // dynamically when some settings are hidden, otherwise rows "jump" around.
        boolean singleColumnLayout = settingComponents.size() <= 2;
        float columnWidth = singleColumnLayout ? innerWidth : (innerWidth - COLUMN_GAP) / 2.0F;

        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        ScissorManager scissorManager = Main.getInstance().getScissorManager();
        scissorManager.push(positionMatrix, innerX, innerY, innerWidth, innerHeight);

        float[] columnOffsets = new float[]{0.0F, 0.0F};
        // Visible-band cull: any setting whose row strip falls
        // entirely outside [innerY, innerY + innerHeight] won't be
        // visible at all. The GL scissor we just pushed clips the
        // pixels for us, but the per-component render() still runs
        // expensive layout / hover-animation work for those rows -
        // visible in the user's screenshot as the bottom rows
        // bleeding through the panel border (MSDF batches don't
        // pick up the scissor boundary the way solid-color rects do).
        // Skipping render() outright AND the hover-tracking branches
        // for off-screen rows fixes both the visual leak and the
        // "invisible setting still steals my hover" interaction bug.
        float visibleTop = innerY;
        float visibleBottom = innerY + innerHeight;
        for (AbstractSettingComponent component : settingComponents) {
            if (!shouldRenderSetting(component.getSetting())) {
                continue;
            }

            boolean fullWidthSetting = singleColumnLayout || component.getSetting().isFullWidth();
            if (fullWidthSetting) {
                float rowOffset = Math.max(columnOffsets[0], columnOffsets[1]);
                float componentY = (float) (innerY + rowOffset - smoothedScroll);
                float rowHeight = component.getExpandedHeight();

                component.x = innerX;
                component.y = componentY;
                component.width = innerWidth;
                if (componentY + rowHeight >= visibleTop && componentY <= visibleBottom) {
                    component.setExternalAlpha(panelAlpha);
                    component.render(context, mouseX, mouseY, delta);
                    component.setExternalAlpha(1.0F);
                }

                float nextOffset = rowOffset + rowHeight + ROW_GAP;
                columnOffsets[0] = nextOffset;
                columnOffsets[1] = nextOffset;
            } else {
                int column = columnOffsets[0] <= columnOffsets[1] ? 0 : 1;
                float componentX = innerX + column * (columnWidth + COLUMN_GAP);
                float componentY = (float) (innerY + columnOffsets[column] - smoothedScroll);
                float rowHeight = component.getExpandedHeight();

                component.x = componentX;
                component.y = componentY;
                component.width = columnWidth;
                if (componentY + rowHeight >= visibleTop && componentY <= visibleBottom) {
                    component.setExternalAlpha(panelAlpha);
                    component.render(context, mouseX, mouseY, delta);
                    component.setExternalAlpha(1.0F);
                }

                columnOffsets[column] += rowHeight + ROW_GAP;
            }
        }

        scissorManager.pop();

        // {@code columnOffsets} carries an extra trailing
        // {@code ROW_GAP} after the last row's height. We keep
        // that extra gap baked into {@code lastMeasuredHeight} so
        // the user can scroll a few px past the last setting and
        // its bottom doesn't kiss the panel border.
        lastMeasuredHeight = Math.max(columnOffsets[0], columnOffsets[1]);
        double maxScroll = Math.max(0.0, lastMeasuredHeight - innerHeight);
        scroll = Math.max(0.0, Math.min(scroll, maxScroll));
        smoothedScroll = MathUtil.interpolateSmooth(2, smoothedScroll, scroll);
    }

    private boolean shouldRenderSetting(Setting setting) {
        return setting != null && setting.isVisible();
    }

    /**
     * True if the component's row strip overlaps the visible band of
     * the settings panel for the current scroll position. Used to
     * gate event delivery so an invisible (scrolled-off) row can't
     * absorb clicks, hovers, or scroll-wheel events that should fall
     * through to the user's actual cursor target.
     *
     * <p>Recomputes the visible band from {@link #settingsPanelY()}
     * and {@link #settingsPanelHeight()} rather than caching - the
     * panel rect can move between frames (window resize, settings
     * collapse/expand) and an out-of-date cache would re-introduce
     * the "click absorbed by invisible row" bug we're fixing.
     */
    private boolean isComponentVisible(AbstractSettingComponent component) {
        float visibleTop = settingsPanelY() + 2.0F;
        float visibleBottom = visibleTop + settingsPanelHeight() - 4.0F;
        float rowTop = component.y;
        float rowBottom = component.y + component.getExpandedHeight();
        return rowBottom >= visibleTop && rowTop <= visibleBottom;
    }

    private List<String> wrap(String text, MsdfFont font, float size, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }

        StringBuilder currentLine = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (font.getWidth(candidate, size) <= maxWidth) {
                currentLine = new StringBuilder(candidate);
            } else {
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                }
                currentLine = new StringBuilder(word);
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    public boolean blocksInteraction(double mouseX, double mouseY) {
        return isOpen() && MathUtil.isHovered(mouseX, mouseY, contentX(), contentY(), contentWidth(), contentHeight());
    }

    public boolean isInteractiveHover(double mouseX, double mouseY) {
        if (!isOpen()) {
            return false;
        }

        if (MathUtil.isHovered(mouseX, mouseY, backButtonX(), backButtonY(), BACK_BUTTON_SIZE, BACK_BUTTON_SIZE)) {
            return true;
        }

        for (AbstractSettingComponent component : settingComponents) {
            if (shouldRenderSetting(component.getSetting()) && isComponentVisible(component) && component.isHover(mouseX, mouseY)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isOpen()) {
            return false;
        }

        if (button == 0 && MathUtil.isHovered(mouseX, mouseY, backButtonX(), backButtonY(), BACK_BUTTON_SIZE, BACK_BUTTON_SIZE)) {
            playButtonClickSound();
            closeDetail();
            return true;
        }

        for (AbstractSettingComponent component : settingComponents) {
            if (shouldRenderSetting(component.getSetting()) && isComponentVisible(component) && component.isHover(mouseX, mouseY) && component.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        return blocksInteraction(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!isOpen()) {
            return false;
        }

        for (AbstractSettingComponent component : settingComponents) {
            if (shouldRenderSetting(component.getSetting()) && isComponentVisible(component)) {
                component.mouseReleased(mouseX, mouseY, button);
            }
        }
        return blocksInteraction(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!isOpen()) {
            return false;
        }

        for (AbstractSettingComponent component : settingComponents) {
            if (shouldRenderSetting(component.getSetting()) && isComponentVisible(component)) {
                component.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
            }
        }
        return blocksInteraction(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isOpen()) {
            return false;
        }

        if (MathUtil.isHovered(mouseX, mouseY, settingsPanelX(), settingsPanelY(), settingsPanelWidth(), settingsPanelHeight())) {
            scroll += amount * -20.0;
        }

        for (AbstractSettingComponent component : settingComponents) {
            if (shouldRenderSetting(component.getSetting()) && isComponentVisible(component) && component.isHover(mouseX, mouseY)) {
                component.mouseScrolled(mouseX, mouseY, amount);
            }
        }

        return blocksInteraction(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isOpen()) {
            return false;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return false;
        }

        for (AbstractSettingComponent component : settingComponents) {
            if (shouldRenderSetting(component.getSetting()) && component.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (!isOpen()) {
            return false;
        }

        for (AbstractSettingComponent component : settingComponents) {
            if (shouldRenderSetting(component.getSetting())) {
                component.keyReleased(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!isOpen()) {
            return false;
        }

        for (AbstractSettingComponent component : settingComponents) {
            if (shouldRenderSetting(component.getSetting()) && component.charTyped(chr, modifiers)) {
                return true;
            }
        }
        return true;
    }
}
