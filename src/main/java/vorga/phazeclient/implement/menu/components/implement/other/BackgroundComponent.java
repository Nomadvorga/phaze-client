package vorga.phazeclient.implement.menu.components.implement.other;

import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.features.modules.client.Theme;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.AbstractComponent;
import vorga.phazeclient.implement.config.ConfigManager;
import vorga.phazeclient.core.Main;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Setter
@Accessors(chain = true)
public class BackgroundComponent extends AbstractComponent {
    private static final float BRAND_TEXT_SIZE = 9.4F;
    private static final float TAB_TEXT_SIZE = 6.9F;
    private static final float FOOTER_TEXT_SIZE = 5.5F;
    private static final float CONFIG_TEXT_SIZE = 6.0F;
    private static final float HEADER_HEIGHT = 34f;
    private static final float TOP_TAB_HEIGHT = 17.0F;
    private static final float TOP_TAB_GAP = 8.0F;
    private static final float TOP_TAB_HORIZONTAL_PADDING = 19.0F;
    private static final float SIDEBAR_WIDTH = 100f;
    private static final float CONFIG_ROW_HEIGHT = 17.5F;
    private static final float CONFIG_ROW_GAP = 1.0F;
    private static final float CONFIG_DELETE_WIDTH = 19.5F;
    private static final float CONFIG_TEXT_PADDING = 6.0F;
    private static final float CONFIG_LIST_PADDING = 1.5F;
    private static final float FOOTER_BUTTON_HEIGHT = 15.0F;
    private static final float FOOTER_BUTTON_GAP = 14.0F;

    private final ConfigManager configManager = Main.getInstance().getConfigManager();
    private final Animation saveConfigHoverAnimation = new DecelerateAnimation().setMs(180).setValue(1);
    private final Animation editHudHoverAnimation = new DecelerateAnimation().setMs(180).setValue(1);
    private final Map<String, Animation> configHoverAnimations = new HashMap<>();
    private final Map<String, Animation> configDeleteHoverAnimations = new HashMap<>();
    private final Map<String, Animation> topTabHoverAnimations = new HashMap<>();
    private SearchComponent searchComponent;

    private String editingConfigName = null;
    private String editingText = "";
    private long lastClickTime = 0;
    private String lastClickedConfig = null;

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Apply the language selection from Theme each frame so a
        // mid-session locale flip repaints in the new strings without
        // a restart.
        Theme.getInstance().syncLanguage();
        MatrixStack matrix = context.getMatrices();
        // Sidebar is hidden while the CONFIGS view is active so the
        // configs list spans the full width of the menu's content
        // pane. Keep the same border / background panels otherwise so
        // the menu chrome stays identical with or without the sidebar.
        boolean configsOpen = MenuScreen.INSTANCE.isConfigsViewOpen();

        rectangle.render(ShapeProperties.create(matrix, x, y, width, height)
                .round(8).softness(1).thickness(2)
                .outlineColor(applyGlobalAlpha(MenuStyle.BORDER_LIGHT))
                .color(applyGlobalAlpha(MenuStyle.PANEL_BG))
                .build());

        rectangle.render(ShapeProperties.create(matrix, x + 1.0F, y + 1.0F, width - 2.0F, height - 2.0F)
                .round(7).color(applyGlobalAlpha(MenuStyle.PANEL_BG_SOFT)).build());

        rectangle.render(ShapeProperties.create(matrix, x + 1.0F, y + 1.0F, width - 2.0F, HEADER_HEIGHT)
                .round(7, 0, 7, 0).color(applyGlobalAlpha(MenuStyle.PANEL_HEADER)).build());

        if (!configsOpen) {
            rectangle.render(ShapeProperties.create(matrix, x + 1.0F, y + HEADER_HEIGHT + 1.0F, SIDEBAR_WIDTH - 1.0F, height - HEADER_HEIGHT - 2.0F)
                    .round(0, 0, 0, 7).color(applyGlobalAlpha(MenuStyle.PANEL_SIDEBAR)).build());

            rectangle.render(ShapeProperties.create(matrix, x + SIDEBAR_WIDTH, y + HEADER_HEIGHT + 1.0F, width - SIDEBAR_WIDTH - 1.0F, height - HEADER_HEIGHT - 2.0F)
                    .round(0, 7, 0, 0).color(applyGlobalAlpha(MenuStyle.PANEL_CONTENT)).build());

            rectangle.render(ShapeProperties.create(matrix, x + SIDEBAR_WIDTH, y + HEADER_HEIGHT + 1.0F, 1.0F, height - HEADER_HEIGHT - 2.0F)
                    .color(applyGlobalAlpha(MenuStyle.BORDER)).build());
        } else {
            // Full-width content pane (no sidebar separator).
            rectangle.render(ShapeProperties.create(matrix, x + 1.0F, y + HEADER_HEIGHT + 1.0F, width - 2.0F, height - HEADER_HEIGHT - 2.0F)
                    .round(0, 0, 7, 7).color(applyGlobalAlpha(MenuStyle.PANEL_CONTENT)).build());
        }
        rectangle.render(ShapeProperties.create(matrix, x + 1.0F, y + HEADER_HEIGHT, width - 2.0F, 1.0F)
                .color(applyGlobalAlpha(MenuStyle.BORDER)).build());

        renderHeader(context, mouseX, mouseY);
        if (!configsOpen) {
            renderConfigs(context, mouseX, mouseY);
            renderSidebarFooter(context, mouseX, mouseY);
        }
    }

    private void renderHeader(DrawContext context, int mouseX, int mouseY) {
        MatrixStack matrix = context.getMatrices();
        float brandX = x + 8.0F;
        float brandY = MenuStyle.centerMsdfTextY(BRAND_TEXT_SIZE, y + 1.5F, HEADER_HEIGHT);
        MsdfRenderer.renderText(MsdfFonts.bold(), "PHAZE", BRAND_TEXT_SIZE, applyGlobalAlpha(MenuStyle.TEXT_PRIMARY), matrix.peek().getPositionMatrix(), brandX, brandY, 0.0F);
        MsdfRenderer.renderText(
                MsdfFonts.medium(),
                "CLIENT",
                BRAND_TEXT_SIZE,
                applyGlobalAlpha(MenuStyle.TEXT_MUTED),
                matrix.peek().getPositionMatrix(),
                brandX + MsdfFonts.bold().getWidth("PHAZE", BRAND_TEXT_SIZE) + 5.0F,
                brandY,
                0.0F
        );

        String[] labels = {"MODS", "SETTINGS", "CONFIGS"};
        boolean settingsActive = isSettingsTabActive();
        boolean configsActive = isConfigsTabActive();
        boolean modsActive = !settingsActive && !configsActive;
        boolean[] active = {modsActive, settingsActive, configsActive};
        float totalTabsWidth = 0.0F;
        for (int i = 0; i < labels.length; i++) {
            totalTabsWidth += getTopTabWidth(labels[i]);
            if (i < labels.length - 1) {
                totalTabsWidth += TOP_TAB_GAP;
            }
        }
        // Reserve space for the import-key "+" button only while
        // the CONFIGS tab is active. The plus is irrelevant on the
        // MODS / SETTINGS screens and the user explicitly asked for
        // it to be hidden there. Skipping the reservation when it's
        // not shown lets the three-tab strip recentre on the menu's
        // horizontal centreline.
        if (configsActive) {
            totalTabsWidth += TOP_TAB_GAP + TOP_TAB_HEIGHT;
        }

        float tabsX = x + width / 2F - totalTabsWidth / 2.0F;
        for (int i = 0; i < labels.length; i++) {
            tabsX += drawTopTab(context, mouseX, mouseY, tabsX, labels[i], active[i]) + TOP_TAB_GAP;
        }
        // "+" - opens the import-from-key modal. Only rendered (and
        // only clickable) while CONFIGS is the active top-tab so it
        // doesn't sit there as a dead chip on the MODS / SETTINGS
        // screens.
        if (configsActive) {
            drawImportPlus(context, mouseX, mouseY, tabsX);
        }
    }

    private float getTopTabWidth(String label) {
        return MsdfFonts.bold().getWidth(label, TAB_TEXT_SIZE) + TOP_TAB_HORIZONTAL_PADDING;
    }

    private float drawTopTab(DrawContext context, int mouseX, int mouseY, float tabX, String label, boolean active) {
        MatrixStack matrix = context.getMatrices();
        float tabWidth = getTopTabWidth(label);
        float tabY = y + (HEADER_HEIGHT - TOP_TAB_HEIGHT) / 2.0F;
        boolean hovered = MathUtil.isHovered(mouseX, mouseY, tabX, tabY, tabWidth, TOP_TAB_HEIGHT);
        Animation hoverAnimation = topTabHoverAnimations.computeIfAbsent(label, ignored -> new DecelerateAnimation().setMs(180).setValue(1));
        hoverAnimation.setDirection(hovered ? Direction.FORWARDS : Direction.BACKWARDS);

        float hoverProgress = hoverAnimation.getOutputFloat();
        float activeProgress = active ? 1.0F : 0.0F;

        int baseTabColor = MenuStyle.PANEL_CHIP;
        int hoverTabColor = MenuStyle.mix(baseTabColor, 0xFFFFFFFF, 0.08F);
        int activeTabColor = MenuStyle.mix(baseTabColor, 0xFFFFFFFF, 0.16F);
        int tabColor = MenuStyle.mix(MenuStyle.mix(baseTabColor, hoverTabColor, hoverProgress), activeTabColor, activeProgress);

        int baseBorderColor = MenuStyle.BORDER;
        int hoverBorderColor = MenuStyle.mix(MenuStyle.BORDER, MenuStyle.BORDER_LIGHT, 0.26F);
        int activeBorderColor = MenuStyle.mix(MenuStyle.BORDER, MenuStyle.BORDER_LIGHT, 0.38F);
        int borderColor = MenuStyle.mix(MenuStyle.mix(baseBorderColor, hoverBorderColor, hoverProgress), activeBorderColor, activeProgress);

        int baseTextColor = MenuStyle.TEXT_MUTED;
        int hoverTextColor = MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, 0.45F);
        int activeTextColor = MenuStyle.TEXT_PRIMARY;
        int tabText = MenuStyle.mix(MenuStyle.mix(baseTextColor, hoverTextColor, hoverProgress), activeTextColor, activeProgress);

        rectangle.render(ShapeProperties.create(matrix, tabX, tabY, tabWidth, TOP_TAB_HEIGHT)
                .round(2).thickness(3.0F).outlineColor(applyGlobalAlpha(borderColor)).color(MenuStyle.withAlpha(tabColor, 0)).build());
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                label,
                TAB_TEXT_SIZE,
                applyGlobalAlpha(tabText),
                matrix.peek().getPositionMatrix(),
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), label, TAB_TEXT_SIZE, tabX, tabWidth),
                MenuStyle.centerMsdfTextY(TAB_TEXT_SIZE, tabY, TOP_TAB_HEIGHT),
                0.0F
        );
        return tabWidth;
    }

    private void renderNewConfigButton(DrawContext context, int mouseX, int mouseY) {
        MatrixStack matrix = context.getMatrices();
        float buttonX = x + 4.0F;
        float buttonY = y + HEADER_HEIGHT + 7.0F;
        float buttonWidth = SIDEBAR_WIDTH - 8.0F;
        boolean hovered = MathUtil.isHovered(mouseX, mouseY, buttonX, buttonY, buttonWidth, FOOTER_BUTTON_HEIGHT);
        saveConfigHoverAnimation.setDirection(hovered ? Direction.FORWARDS : Direction.BACKWARDS);

        float hoverProgress = saveConfigHoverAnimation.getOutputFloat();

        rectangle.render(ShapeProperties.create(matrix, buttonX, buttonY, buttonWidth, FOOTER_BUTTON_HEIGHT)
                .round(2).thickness(2.0F)
                .outlineColor(MenuStyle.BORDER)
                .color(MenuStyle.mix(MenuStyle.PANEL_CHIP, MenuStyle.TEXT_PRIMARY, 0.06F * hoverProgress)).build());
        String label = Lang.t("sidebar.new_config");
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                label,
                FOOTER_TEXT_SIZE,
                MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, 0.55F + hoverProgress * 0.45F),
                matrix.peek().getPositionMatrix(),
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), label, FOOTER_TEXT_SIZE, buttonX, buttonWidth),
                MenuStyle.centerMsdfTextY(FOOTER_TEXT_SIZE, buttonY, FOOTER_BUTTON_HEIGHT),
                0.0F
        );
    }

    private void renderConfigs(DrawContext context, int mouseX, int mouseY) {
        MatrixStack matrix = context.getMatrices();
        String[] configs = configManager.getConfigList();
        float rowX = configRowX();
        float rowWidth = configRowWidth();
        float rowY = configRowStartY();
        int visibleConfigCount = 0;

        for (int i = 0; i < configs.length; i++) {
            float currentRowY = rowY + i * (CONFIG_ROW_HEIGHT + CONFIG_ROW_GAP);
            if (currentRowY + CONFIG_ROW_HEIGHT > footerStartY() - 4.0F) {
                break;
            }
            visibleConfigCount++;
        }

        if (visibleConfigCount > 0) {
            float listHeight = visibleConfigCount * CONFIG_ROW_HEIGHT + (visibleConfigCount - 1) * CONFIG_ROW_GAP + CONFIG_LIST_PADDING * 2.0F;
            rectangle.render(ShapeProperties.create(matrix, rowX - CONFIG_LIST_PADDING, rowY - CONFIG_LIST_PADDING, rowWidth + CONFIG_LIST_PADDING * 2.0F, listHeight)
                    .round(1.8F)
                    .thickness(1.15F)
                    .outlineColor(MenuStyle.withAlpha(MenuStyle.BORDER_LIGHT, applyGlobalAlpha(0.62F)))
                    .color(MenuStyle.withAlpha(MenuStyle.PANEL_BG, applyGlobalAlpha(0.08F)))
                    .build());
        }

        String currentConfig = configManager.getCurrentConfigName();
        for (String config : configs) {
            if (rowY + CONFIG_ROW_HEIGHT > footerStartY() - 4.0F) {
                break;
            }

            boolean rowHovered = MathUtil.isHovered(mouseX, mouseY, rowX, rowY, rowWidth, CONFIG_ROW_HEIGHT);
            boolean deleteHovered = isDeleteHovered(mouseX, mouseY, rowY);
            boolean active = config.equalsIgnoreCase(currentConfig);
            Animation rowHoverAnimation = configHoverAnimation(config);
            Animation deleteHoverAnimation = configDeleteHoverAnimation(config);

            rowHoverAnimation.setDirection(rowHovered ? Direction.FORWARDS : Direction.BACKWARDS);
            deleteHoverAnimation.setDirection(deleteHovered ? Direction.FORWARDS : Direction.BACKWARDS);

            float rowHoverProgress = rowHoverAnimation.getOutputFloat();
            float deleteHoverProgress = deleteHoverAnimation.getOutputFloat();

            int baseRowColor = active ? MenuStyle.PANEL_ROW_ACTIVE : MenuStyle.PANEL_ROW;
            int rowColor = MenuStyle.mix(baseRowColor, MenuStyle.TEXT_PRIMARY, 0.07F * rowHoverProgress);
            int outlineColor = active
                    ? MenuStyle.mix(MenuStyle.BORDER_LIGHT, MenuStyle.CHIP_ACTIVE, 0.35F + rowHoverProgress * 0.08F)
                    : MenuStyle.mix(MenuStyle.BORDER, MenuStyle.BORDER_LIGHT, rowHoverProgress);

            rectangle.render(ShapeProperties.create(matrix, rowX, rowY, rowWidth, CONFIG_ROW_HEIGHT)
                    .round(1.5F).thickness(2.0F).outlineColor(applyGlobalAlpha(outlineColor)).color(applyGlobalAlpha(rowColor)).build());

            MsdfRenderer.renderText(
                    MsdfFonts.bold(),
                    config,
                    CONFIG_TEXT_SIZE,
                    applyGlobalAlpha(MenuStyle.TEXT_PRIMARY),
                    context.getMatrices().peek().getPositionMatrix(),
                    rowX + CONFIG_TEXT_PADDING,
                    MenuStyle.centerMsdfTextY(CONFIG_TEXT_SIZE, rowY, CONFIG_ROW_HEIGHT),
                    0.0F
            );

            // Always show the delete icon - even on "default" and on
            // the active config. ConfigManager.deleteConfig handles
            // both: default is a regular file that can be recreated
            // by the next save, and deleting the active config
            // auto-switches to the next available config.
            {
                float iconSize = 6.5F;
                float deleteX = deleteSectionX(rowX, rowWidth) + (CONFIG_DELETE_WIDTH - iconSize) / 2.0F;
                float iconY = rowY + (CONFIG_ROW_HEIGHT - iconSize) / 2.0F;
                int deleteColor = MenuStyle.mix(MenuStyle.TEXT_MUTED, 0xFFFFFFFF, deleteHoverProgress);

                image.setTexture("textures/cross.png")
                        .render(ShapeProperties.create(matrix, deleteX, iconY, iconSize, iconSize)
                                .color(applyGlobalAlpha(deleteColor))
                                .build());
            }

            // Render editing field if this config is being edited
            if (editingConfigName != null && editingConfigName.equals(config)) {
                float textWidth = MsdfFonts.bold().getWidth(editingText + "_", CONFIG_TEXT_SIZE);
                rectangle.render(ShapeProperties.create(matrix, rowX, rowY, Math.max(rowWidth, textWidth + CONFIG_TEXT_PADDING * 2), CONFIG_ROW_HEIGHT)
                        .round(1.5F).thickness(2.0F).outlineColor(applyGlobalAlpha(MenuStyle.CHIP_ACTIVE)).color(applyGlobalAlpha(MenuStyle.PANEL_BG)).build());
                MsdfRenderer.renderText(
                        MsdfFonts.bold(),
                        editingText + "_",
                        CONFIG_TEXT_SIZE,
                        applyGlobalAlpha(MenuStyle.TEXT_PRIMARY),
                        context.getMatrices().peek().getPositionMatrix(),
                        rowX + CONFIG_TEXT_PADDING,
                        MenuStyle.centerMsdfTextY(CONFIG_TEXT_SIZE, rowY, CONFIG_ROW_HEIGHT),
                        0.0F
                );
            }

            rowY += CONFIG_ROW_HEIGHT + CONFIG_ROW_GAP;
        }
    }

    private void renderSidebarFooter(DrawContext context, int mouseX, int mouseY) {
        MatrixStack matrix = context.getMatrices();
        float footerX = x + 4;
        float footerWidth = SIDEBAR_WIDTH - 8;
        float buttonGap = 4.0F;
        
        // NEW CONFIG button (top button in footer)
        float newConfigY = footerStartY() - 5.0F - FOOTER_BUTTON_HEIGHT - buttonGap;
        boolean newConfigHovered = MathUtil.isHovered(mouseX, mouseY, footerX, newConfigY, footerWidth, FOOTER_BUTTON_HEIGHT);
        saveConfigHoverAnimation.setDirection(newConfigHovered ? Direction.FORWARDS : Direction.BACKWARDS);
        float newConfigHoverProgress = saveConfigHoverAnimation.getOutputFloat();

        rectangle.render(ShapeProperties.create(matrix, footerX, newConfigY, footerWidth, FOOTER_BUTTON_HEIGHT)
                .round(2).thickness(2.0F)
                .outlineColor(applyGlobalAlpha(MenuStyle.BORDER))
                .color(applyGlobalAlpha(MenuStyle.mix(MenuStyle.PANEL_CHIP, MenuStyle.TEXT_PRIMARY, 0.06F * newConfigHoverProgress))).build());
        String newConfigLabel = Lang.t("sidebar.new_config");
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                newConfigLabel,
                FOOTER_TEXT_SIZE,
                applyGlobalAlpha(MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, 0.55F + newConfigHoverProgress * 0.45F)),
                matrix.peek().getPositionMatrix(),
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), newConfigLabel, FOOTER_TEXT_SIZE, footerX, footerWidth),
                MenuStyle.centerMsdfTextY(FOOTER_TEXT_SIZE, newConfigY, FOOTER_BUTTON_HEIGHT),
                0.0F
        );
        
        // EDIT HUD LAYOUT button (bottom button in footer)
        float editHudY = footerStartY() - 5.0F;
        boolean editHovered = MathUtil.isHovered(mouseX, mouseY, footerX, editHudY, footerWidth, FOOTER_BUTTON_HEIGHT);
        editHudHoverAnimation.setDirection(editHovered ? Direction.FORWARDS : Direction.BACKWARDS);
        float editHoverProgress = editHudHoverAnimation.getOutputFloat();

        rectangle.render(ShapeProperties.create(matrix, footerX, editHudY, footerWidth, FOOTER_BUTTON_HEIGHT)
                .round(2).thickness(2.0F)
                .outlineColor(applyGlobalAlpha(MenuStyle.BORDER))
                .color(applyGlobalAlpha(MenuStyle.mix(MenuStyle.CHIP_ACTIVE, MenuStyle.TEXT_PRIMARY, 0.08F * editHoverProgress))).build());
        String editLabel = "EDIT HUD LAYOUT";
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                editLabel,
                FOOTER_TEXT_SIZE,
                applyGlobalAlpha(MenuStyle.mix(MenuStyle.TEXT_PRIMARY, MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, 1.0F), editHoverProgress)),
                matrix.peek().getPositionMatrix(),
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), editLabel, FOOTER_TEXT_SIZE, footerX, footerWidth),
                MenuStyle.centerMsdfTextY(FOOTER_TEXT_SIZE, editHudY, FOOTER_BUTTON_HEIGHT),
                0.0F
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (handleTopTabClick(mouseX, mouseY)) {
            return true;
        }

        // While CONFIGS view is open the sidebar isn't rendered, so
        // its click handlers (config rows, NEW CONFIG, EDIT HUD)
        // would react to clicks in the now full-width content pane
        // where they have no visible widgets. Skip them entirely.
        if (MenuScreen.INSTANCE.isConfigsViewOpen()) {
            return false;
        }

        float footerX = x + 4.0F;
        float footerWidth = SIDEBAR_WIDTH - 8.0F;
        float buttonGap = 4.0F;
        
        // NEW CONFIG button click (top button in footer)
        float newConfigY = footerStartY() - 5.0F - FOOTER_BUTTON_HEIGHT - buttonGap;
        if (MathUtil.isHovered(mouseX, mouseY, footerX, newConfigY, footerWidth, FOOTER_BUTTON_HEIGHT)) {
            playButtonClickSound();
            configManager.createNewConfig();
            return true;
        }
        
        // EDIT HUD LAYOUT button click (bottom button in footer)
        float editHudY = footerStartY() - 5.0F;
        if (MathUtil.isHovered(mouseX, mouseY, footerX, editHudY, footerWidth, FOOTER_BUTTON_HEIGHT)) {
            playButtonClickSound();
            // TODO: Open HUD layout editor
            return true;
        }

        float rowX = configRowX();
        float rowWidth = configRowWidth();
        float rowY = configRowStartY();
        String[] configs = configManager.getConfigList();
        for (String config : configs) {
            if (rowY + CONFIG_ROW_HEIGHT > footerStartY() - 4.0F) {
                break;
            }

            if (isDeleteHovered(mouseX, mouseY, rowY)) {
                // Default is now a regular file (no special-cased
                // redirect), and deleteConfig safely auto-switches to
                // the next available config when the user nukes the
                // active one. Both restrictions removed - if the user
                // wants their config gone, give it to them.
                playButtonClickSound();
                configManager.deleteConfig(config);
                return true;
            }

            if (MathUtil.isHovered(mouseX, mouseY, rowX, rowY, rowWidth, CONFIG_ROW_HEIGHT)) {
                long currentTime = System.currentTimeMillis();
                if (config.equals(lastClickedConfig) && (currentTime - lastClickTime) < 500) {
                    // Double click - start editing
                    if (!config.equalsIgnoreCase("default")) {
                        editingConfigName = config;
                        editingText = config;
                        lastClickedConfig = null;
                        lastClickTime = 0;
                    }
                } else {
                    // Single click - load config
                    if (editingConfigName != null) {
                        // Save the edited config name before switching
                        if (!editingText.isEmpty() && !editingText.equals(editingConfigName)) {
                            configManager.renameConfig(editingConfigName, editingText);
                        }
                        editingConfigName = null;
                        editingText = "";
                    }
                    playButtonClickSound();
                    configManager.loadConfig(config);
                    lastClickedConfig = config;
                    lastClickTime = currentTime;
                }
                return true;
            }

            rowY += CONFIG_ROW_HEIGHT + CONFIG_ROW_GAP;
        }

        // Click outside - stop editing
        if (editingConfigName != null) {
            editingConfigName = null;
            editingText = "";
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingConfigName != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                // Save and finish editing
                if (!editingText.isEmpty() && !editingText.equals(editingConfigName)) {
                    configManager.renameConfig(editingConfigName, editingText);
                }
                editingConfigName = null;
                editingText = "";
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                // Cancel editing
                editingConfigName = null;
                editingText = "";
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                // Delete last character
                if (!editingText.isEmpty()) {
                    editingText = editingText.substring(0, editingText.length() - 1);
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (editingConfigName != null) {
            // Add character if valid
            if (Character.isLetterOrDigit(chr) || chr == '_' || chr == '-') {
                editingText += chr;
            }
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    public boolean isInteractiveHover(double mouseX, double mouseY) {
        if (isTopTabHovered(mouseX, mouseY)) {
            return true;
        }

        float footerX = x + 4.0F;
        float footerWidth = SIDEBAR_WIDTH - 8.0F;
        float buttonGap = 4.0F;
        float newConfigY = footerStartY() - 5.0F - FOOTER_BUTTON_HEIGHT - buttonGap;
        float editHudY = footerStartY() - 5.0F;

        if (MathUtil.isHovered(mouseX, mouseY, footerX, newConfigY, footerWidth, FOOTER_BUTTON_HEIGHT)) {
            return true;
        }

        if (MathUtil.isHovered(mouseX, mouseY, footerX, editHudY, footerWidth, FOOTER_BUTTON_HEIGHT)) {
            return true;
        }

        float rowX = configRowX();
        float rowWidth = configRowWidth();
        float rowY = configRowStartY();
        String[] configs = configManager.getConfigList();
        for (String ignored : configs) {
            if (rowY + CONFIG_ROW_HEIGHT > footerStartY() - 4.0F) {
                break;
            }

            if (MathUtil.isHovered(mouseX, mouseY, rowX, rowY, rowWidth, CONFIG_ROW_HEIGHT) || isDeleteHovered(mouseX, mouseY, rowY)) {
                return true;
            }

            rowY += CONFIG_ROW_HEIGHT + CONFIG_ROW_GAP;
        }

        return false;
    }

    private float footerStartY() {
        return y + height - 18.0F;
    }

    private float configRowX() {
        return x + 4.0F;
    }

    private float configRowStartY() {
        return y + HEADER_HEIGHT + 7.0F;
    }

    private float configRowWidth() {
        return SIDEBAR_WIDTH - 8.0F;
    }

    private float deleteSectionX(float rowX, float rowWidth) {
        return rowX + rowWidth - CONFIG_DELETE_WIDTH;
    }

    private boolean isDeleteHovered(double mouseX, double mouseY, float rowY) {
        float iconSize = 6.5F;
        float deleteX = deleteSectionX(configRowX(), configRowWidth()) + (CONFIG_DELETE_WIDTH - iconSize) / 2.0F;
        float deleteY = rowY + (CONFIG_ROW_HEIGHT - iconSize) / 2.0F;
        return MathUtil.isHovered(mouseX, mouseY, deleteX, deleteY, iconSize, iconSize);
    }

    private Animation configHoverAnimation(String configName) {
        return configHoverAnimations.computeIfAbsent(configName, ignored -> new DecelerateAnimation().setMs(180).setValue(1));
    }

    private Animation configDeleteHoverAnimation(String configName) {
        return configDeleteHoverAnimations.computeIfAbsent(configName, ignored -> new DecelerateAnimation().setMs(160).setValue(1));
    }

    private boolean handleTopTabClick(double mouseX, double mouseY) {
        String[] labels = {"MODS", "SETTINGS", "CONFIGS"};
        boolean configsActive = isConfigsTabActive();
        float totalTabsWidth = 0.0F;
        for (int i = 0; i < labels.length; i++) {
            totalTabsWidth += getTopTabWidth(labels[i]);
            if (i < labels.length - 1) {
                totalTabsWidth += TOP_TAB_GAP;
            }
        }
        // Match the offset reservation in renderHeader: the import
        // plus only contributes to the strip's width when CONFIGS is
        // active. Otherwise the hit-test would read tabs at the
        // wrong x-positions.
        if (configsActive) {
            totalTabsWidth += TOP_TAB_GAP + TOP_TAB_HEIGHT;
        }

        float tabsX = x + width / 2F - totalTabsWidth / 2.0F;
        float tabY = y + (HEADER_HEIGHT - TOP_TAB_HEIGHT) / 2.0F;
        for (String label : labels) {
            float tabWidth = getTopTabWidth(label);
            if (MathUtil.isHovered(mouseX, mouseY, tabsX, tabY, tabWidth, TOP_TAB_HEIGHT)) {
                playButtonClickSound();
                if ("SETTINGS".equals(label)) {
                    MenuScreen.INSTANCE.openModuleDetail(Theme.getInstance());
                    MenuScreen.INSTANCE.closeConfigsView();
                } else if ("CONFIGS".equals(label)) {
                    MenuScreen.INSTANCE.openConfigsView();
                } else {
                    MenuScreen.INSTANCE.closeModuleDetail();
                    MenuScreen.INSTANCE.closeConfigsView();
                    MenuScreen.INSTANCE.setCategory(ModuleCategory.ALL);
                }
                return true;
            }
            tabsX += tabWidth + TOP_TAB_GAP;
        }
        // Plus button - only clickable when CONFIGS is open.
        if (configsActive
                && MathUtil.isHovered(mouseX, mouseY, tabsX, tabY, TOP_TAB_HEIGHT, TOP_TAB_HEIGHT)) {
            playButtonClickSound();
            MenuScreen.INSTANCE.openConfigImportModal();
            return true;
        }
        return false;
    }

    private boolean isTopTabHovered(double mouseX, double mouseY) {
        String[] labels = {"MODS", "SETTINGS", "CONFIGS"};
        boolean configsActive = isConfigsTabActive();
        float totalTabsWidth = 0.0F;
        for (int i = 0; i < labels.length; i++) {
            totalTabsWidth += getTopTabWidth(labels[i]);
            if (i < labels.length - 1) {
                totalTabsWidth += TOP_TAB_GAP;
            }
        }
        if (configsActive) {
            totalTabsWidth += TOP_TAB_GAP + TOP_TAB_HEIGHT;
        }

        float tabsX = x + width / 2F - totalTabsWidth / 2.0F;
        float tabY = y + (HEADER_HEIGHT - TOP_TAB_HEIGHT) / 2.0F;
        for (String label : labels) {
            float tabWidth = getTopTabWidth(label);
            if (MathUtil.isHovered(mouseX, mouseY, tabsX, tabY, tabWidth, TOP_TAB_HEIGHT)) {
                return true;
            }
            tabsX += tabWidth + TOP_TAB_GAP;
        }
        if (configsActive
                && MathUtil.isHovered(mouseX, mouseY, tabsX, tabY, TOP_TAB_HEIGHT, TOP_TAB_HEIGHT)) {
            return true;
        }
        return false;
    }

    /**
     * Renders the import-from-key "+" button. Square chip the same
     * height as the top tabs, sitting one TOP_TAB_GAP after the
     * CONFIGS tab. Hover animates a soft outline glow so the hit
     * target reads even when there's no label text in the chip.
     */
    private void drawImportPlus(DrawContext context, int mouseX, int mouseY, float tabX) {
        MatrixStack matrix = context.getMatrices();
        float tabY = y + (HEADER_HEIGHT - TOP_TAB_HEIGHT) / 2.0F;
        boolean hovered = MathUtil.isHovered(mouseX, mouseY, tabX, tabY, TOP_TAB_HEIGHT, TOP_TAB_HEIGHT);
        Animation hoverAnimation = topTabHoverAnimations.computeIfAbsent("__plus__",
                ignored -> new DecelerateAnimation().setMs(180).setValue(1));
        hoverAnimation.setDirection(hovered ? Direction.FORWARDS : Direction.BACKWARDS);
        float hoverProgress = hoverAnimation.getOutputFloat();

        // Match the visual styling of the regular top-tabs so the
        // plus chip reads as a sibling of MODS / SETTINGS / CONFIGS
        // rather than a floating outline. Light-tint base fill +
        // visible border outline + brighter on hover - identical
        // weight curve to drawTopTab.
        int baseTabColor = MenuStyle.PANEL_CHIP;
        int hoverTabColor = MenuStyle.mix(baseTabColor, 0xFFFFFFFF, 0.08F);
        int tabColor = MenuStyle.mix(baseTabColor, hoverTabColor, hoverProgress);

        int baseBorderColor = MenuStyle.BORDER;
        int hoverBorderColor = MenuStyle.mix(MenuStyle.BORDER, MenuStyle.BORDER_LIGHT, 0.45F);
        int borderColor = MenuStyle.mix(baseBorderColor, hoverBorderColor, hoverProgress);

        int baseIconColor = MenuStyle.TEXT_MUTED;
        int hoverIconColor = MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, 0.55F);
        int iconColor = MenuStyle.mix(baseIconColor, hoverIconColor, hoverProgress);

        // Draw the chip outline first - matches drawTopTab styling.
        rectangle.render(ShapeProperties.create(matrix, tabX, tabY, TOP_TAB_HEIGHT, TOP_TAB_HEIGHT)
                .round(2).thickness(3.0F)
                .outlineColor(applyGlobalAlpha(borderColor))
                .color(MenuStyle.withAlpha(tabColor, 0))
                .build());

        // Cross icon centred on the chip. Image's render path
        // pivots around (x+width, y) of the supplied rect at a
        // default 90° rotation, so to land the centre of the icon
        // on the chip's centre we compensate by translating to the
        // chip centre, rotating, then translating back. We push
        // through the matrix stack so the bookkeeping is local to
        // this draw call.
        float iconSize = TOP_TAB_HEIGHT * 0.50F;
        float chipCx = tabX + TOP_TAB_HEIGHT * 0.5F;
        float chipCy = tabY + TOP_TAB_HEIGHT * 0.5F;
        matrix.push();
        // Translate-pivot-translate pattern: anchor the rotation at
        // the chip centre. Image internally rotates +90° around
        // its rect's (x+width, y) corner; combined with the matrix
        // rotation here the net effect lands the cross glyph
        // upright (vertical+horizontal bars) and centred on the
        // chip. The iconSize square is drawn with its top-left at
        // (chipCx - iconSize/2, chipCy - iconSize/2) so after the
        // 90° internal rotation around (x+width, y) it covers the
        // chip-centred area.
        matrix.translate(chipCx, chipCy, 0.0F);
        matrix.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(45.0F));
        matrix.translate(-chipCx, -chipCy, 0.0F);
        image.setTexture("textures/cross.png")
                .render(ShapeProperties.create(matrix,
                        chipCx - iconSize * 0.5F, chipCy - iconSize * 0.5F,
                        iconSize, iconSize)
                        .color(applyGlobalAlpha(iconColor))
                        .build());
        matrix.pop();
    }

    private boolean isSettingsTabActive() {
        return MenuScreen.INSTANCE.getModuleDetailComponent().isOpen()
                && MenuScreen.INSTANCE.getModuleDetailComponent().getModule() == Theme.getInstance();
    }

    private boolean isConfigsTabActive() {
        return MenuScreen.INSTANCE.isConfigsViewOpen();
    }
}
