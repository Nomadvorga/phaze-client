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
        MatrixStack matrix = context.getMatrices();

        rectangle.render(ShapeProperties.create(matrix, x, y, width, height)
                .round(8).softness(1).thickness(2)
                .outlineColor(applyGlobalAlpha(MenuStyle.BORDER_LIGHT))
                .color(applyGlobalAlpha(MenuStyle.PANEL_BG))
                .build());

        rectangle.render(ShapeProperties.create(matrix, x + 1.0F, y + 1.0F, width - 2.0F, height - 2.0F)
                .round(7).color(applyGlobalAlpha(MenuStyle.PANEL_BG_SOFT)).build());

        rectangle.render(ShapeProperties.create(matrix, x + 1.0F, y + 1.0F, width - 2.0F, HEADER_HEIGHT)
                .round(7, 0, 7, 0).color(applyGlobalAlpha(MenuStyle.PANEL_HEADER)).build());

        rectangle.render(ShapeProperties.create(matrix, x + 1.0F, y + HEADER_HEIGHT + 1.0F, SIDEBAR_WIDTH - 1.0F, height - HEADER_HEIGHT - 2.0F)
                .round(0, 0, 0, 7).color(applyGlobalAlpha(MenuStyle.PANEL_SIDEBAR)).build());

        rectangle.render(ShapeProperties.create(matrix, x + SIDEBAR_WIDTH, y + HEADER_HEIGHT + 1.0F, width - SIDEBAR_WIDTH - 1.0F, height - HEADER_HEIGHT - 2.0F)
                .round(0, 7, 0, 0).color(applyGlobalAlpha(MenuStyle.PANEL_CONTENT)).build());

        rectangle.render(ShapeProperties.create(matrix, x + SIDEBAR_WIDTH, y + HEADER_HEIGHT + 1.0F, 1.0F, height - HEADER_HEIGHT - 2.0F)
                .color(applyGlobalAlpha(MenuStyle.BORDER)).build());
        rectangle.render(ShapeProperties.create(matrix, x + 1.0F, y + HEADER_HEIGHT, width - 2.0F, 1.0F)
                .color(applyGlobalAlpha(MenuStyle.BORDER)).build());

        renderHeader(context, mouseX, mouseY);
        renderConfigs(context, mouseX, mouseY);
        renderSidebarFooter(context, mouseX, mouseY);
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

        String[] labels = {"MODS", "SETTINGS"};
        boolean settingsActive = isSettingsTabActive();
        boolean[] active = {!settingsActive, settingsActive};
        float totalTabsWidth = 0.0F;
        for (int i = 0; i < labels.length; i++) {
            totalTabsWidth += getTopTabWidth(labels[i]);
            if (i < labels.length - 1) {
                totalTabsWidth += TOP_TAB_GAP;
            }
        }

        float tabsX = x + width / 2F - totalTabsWidth / 2.0F;
        for (int i = 0; i < labels.length; i++) {
            tabsX += drawTopTab(context, mouseX, mouseY, tabsX, labels[i], active[i]) + TOP_TAB_GAP;
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

        float hoverProgress = hoverAnimation.getOutput().floatValue();
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

        float hoverProgress = saveConfigHoverAnimation.getOutput().floatValue();

        rectangle.render(ShapeProperties.create(matrix, buttonX, buttonY, buttonWidth, FOOTER_BUTTON_HEIGHT)
                .round(2).thickness(2.0F)
                .outlineColor(MenuStyle.BORDER)
                .color(MenuStyle.mix(MenuStyle.PANEL_CHIP, MenuStyle.TEXT_PRIMARY, 0.06F * hoverProgress)).build());
        String label = "NEW CONFIG";
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

            float rowHoverProgress = rowHoverAnimation.getOutput().floatValue();
            float deleteHoverProgress = deleteHoverAnimation.getOutput().floatValue();

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

            // Only show delete icon if not default config
            if (!config.equalsIgnoreCase("default")) {
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
        float newConfigHoverProgress = saveConfigHoverAnimation.getOutput().floatValue();

        rectangle.render(ShapeProperties.create(matrix, footerX, newConfigY, footerWidth, FOOTER_BUTTON_HEIGHT)
                .round(2).thickness(2.0F)
                .outlineColor(applyGlobalAlpha(MenuStyle.BORDER))
                .color(applyGlobalAlpha(MenuStyle.mix(MenuStyle.PANEL_CHIP, MenuStyle.TEXT_PRIMARY, 0.06F * newConfigHoverProgress))).build());
        String newConfigLabel = "NEW CONFIG";
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
        float editHoverProgress = editHudHoverAnimation.getOutput().floatValue();

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
                // Only allow deletion if not default config
                if (!config.equalsIgnoreCase("default")) {
                    playButtonClickSound();
                    configManager.deleteConfig(config);
                    return true;
                }
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
        String[] labels = {"MODS", "SETTINGS"};
        float totalTabsWidth = 0.0F;
        for (int i = 0; i < labels.length; i++) {
            totalTabsWidth += getTopTabWidth(labels[i]);
            if (i < labels.length - 1) {
                totalTabsWidth += TOP_TAB_GAP;
            }
        }

        float tabsX = x + width / 2F - totalTabsWidth / 2.0F;
        float tabY = y + (HEADER_HEIGHT - TOP_TAB_HEIGHT) / 2.0F;
        for (String label : labels) {
            float tabWidth = getTopTabWidth(label);
            if (MathUtil.isHovered(mouseX, mouseY, tabsX, tabY, tabWidth, TOP_TAB_HEIGHT)) {
                playButtonClickSound();
                if ("SETTINGS".equals(label)) {
                    MenuScreen.INSTANCE.openModuleDetail(Theme.getInstance());
                } else {
                    MenuScreen.INSTANCE.closeModuleDetail();
                    if (MenuScreen.INSTANCE.getCategory() == ModuleCategory.CLIENT) {
                        MenuScreen.INSTANCE.setCategory(ModuleCategory.VISUALS);
                    }
                }
                return true;
            }
            tabsX += tabWidth + TOP_TAB_GAP;
        }
        return false;
    }

    private boolean isTopTabHovered(double mouseX, double mouseY) {
        String[] labels = {"MODS", "SETTINGS"};
        float totalTabsWidth = 0.0F;
        for (int i = 0; i < labels.length; i++) {
            totalTabsWidth += getTopTabWidth(labels[i]);
            if (i < labels.length - 1) {
                totalTabsWidth += TOP_TAB_GAP;
            }
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
        return false;
    }

    private boolean isSettingsTabActive() {
        return MenuScreen.INSTANCE.getModuleDetailComponent().isOpen()
                && MenuScreen.INSTANCE.getModuleDetailComponent().getModule() == Theme.getInstance();
    }
}
