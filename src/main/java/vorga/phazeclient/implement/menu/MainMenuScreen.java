package vorga.phazeclient.implement.menu;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.realms.gui.screen.RealmsMainScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.util.Window;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.implement.Rectangle;
import vorga.phazeclient.base.util.render.Render2DUtil;
import vorga.phazeclient.implement.menu.MenuStyle;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainMenuScreen extends Screen {
    private static final double PREFERRED_OVERLAY_SCALE = 2.0D;
    private static final double MIN_OVERLAY_SCALE = 1.0D;
    private static final int DEFAULT_OVERLAY_WIDTH = 462;
    private static final int DEFAULT_OVERLAY_HEIGHT = 300;
    private static final String FOOTER_LEFT_TEXT = "Phaze Client 1.21.4 (1.0)";
    private static final String FOOTER_RIGHT_TEXT = "Not affiliated with Mojang or Microsoft. Do not distribute!";
    private static final Identifier ICON_SINGLE = Identifier.of("phaze", "textures/menu/user.png");
    private static final Identifier ICON_MULTI = Identifier.of("phaze", "textures/menu/users.png");
    private static final Identifier ICON_TAB = Identifier.of("phaze", "textures/menu/tab.png");
    private static final Identifier ICON_SETTINGS = Identifier.of("phaze", "textures/menu/settings.png");
    private static final Identifier ICON_CUBE = Identifier.of("phaze", "textures/menu/cube.png");
    private static final Identifier ICON_EXTERNAL = Identifier.of("phaze", "textures/menu/arrow_external_bold.png");
    private static final Identifier ICON_LOGOUT = Identifier.of("phaze", "textures/menu/logout.png");
    private static final Identifier ICON_REALMS = Identifier.of("phaze", "textures/menu/gem_alt_filled.png");
    private static final Identifier ICON_FLASHBACK = Identifier.of("phaze", "textures/menu/flashback.png");
    private static final Identifier ICON_PAINTBRUSH = Identifier.of("phaze", "textures/menu/paintbrush.png");
    private static final Identifier ICON_CROSS = Identifier.of("phaze", "textures/menu/cross.png");
    private static final Identifier ICON_RESET = Identifier.ofVanilla("textures/reset.png");
    private static final int TOOLTIP_TEXT_COLOR = 0xFF9EA7BB;
    private static final int ICON_TINT_COLOR = 0xFF9EA7BB;
    private final Rectangle rectangle = new Rectangle();
    private MainMenuButtonWidget modMenuButton;
    private MainMenuButtonWidget settingsButton;
    private MainMenuButtonWidget realmsButton;
    private MainMenuButtonWidget flashbackButton;
    private MainMenuButtonWidget themeSelectorButton;
    private double overlayScaleFactor = PREFERRED_OVERLAY_SCALE;
    private float overlayRenderScale = 1.0F;
    private int overlayViewportWidth = -1;
    private int overlayViewportHeight = -1;
    private boolean themeSelectorOpen = false;
    private boolean themeSelectorSettingsOpen = false;
    private String activeThemeName = MenuUiSettings.getInstance().getSelectedPanoramaPreset().displayName();
    private String themeSelectorSearchQuery = "";
    private boolean themeSelectorSearchFocused = false;
    private float themeSelectorSettingsHoverAnim = 0.0F;
    private float themeSelectorCloseHoverAnim = 0.0F;
    private float themeSettingsCloseHoverAnim = 0.0F;
    private float panoramaSliderVisualProgress = (float) (MenuUiSettings.getInstance().getPanoramaSpeed() / 100.0D);
    private float guiFpsSliderVisualProgress = (MenuUiSettings.getInstance().getGuiFpsLimit() - MenuUiSettings.MIN_GUI_FPS_LIMIT)
            / (float) (MenuUiSettings.MAX_GUI_FPS_LIMIT - MenuUiSettings.MIN_GUI_FPS_LIMIT);
    private long themeUiLastFrameNs = -1L;
    private float themeUiSmoothing = 1.0F;
    private ThemeSettingsSlider activeThemeSettingsSlider = ThemeSettingsSlider.NONE;

    public MainMenuScreen() {
        super(Text.literal("Phaze Main Menu"));
    }

    @Override
    protected void init() {
        updateOverlayMetrics();
        clearChildren();
        syncDisplayedPanoramaName();
        float scale = phaze$menuScale();
        float sizeMul = 1.3F / 1.2F;
        int overlayW = getOverlayViewportWidth();
        int overlayH = getOverlayViewportHeight();
        int width = Math.max(96, Math.round(320.0F * scale * sizeMul));
        int height = Math.max(12, Math.round(36.0F * scale * sizeMul));
        int gap = Math.max(3, Math.round(8.0F * scale));
        int x = overlayW / 2 - width / 2;
        int y = overlayH / 2 - Math.round(72.0F * scale);

        addDrawableChild(new MainMenuButtonWidget(x, y, width, height, Text.literal("Singleplayer"), ICON_SINGLE,
                b -> this.client.setScreen(new SelectWorldScreen(this))));
        y += height + gap;
        addDrawableChild(new MainMenuButtonWidget(x, y, width, height, Text.literal("Multiplayer"), ICON_MULTI,
                b -> this.client.setScreen(new MultiplayerScreen(this))));
        y += height + gap;
        addDrawableChild(new MainMenuButtonWidget(x, y, width, height, Text.literal("Quit"), ICON_LOGOUT, true,
                b -> this.client.scheduleStop()));

        int size = Math.max(12, Math.round(30.0F * scale * sizeMul));
        int dockRightX = overlayW / 2 + Math.round(42.0F * scale * sizeMul);
        int by = overlayH - Math.round(50.0F * scale);
        int dockGap = Math.max(6, Math.round(8.0F * scale));
        int dockStep = size + dockGap;
        boolean modMenuLoaded = FabricLoader.getInstance().isModLoaded("modmenu");
        boolean flashbackLoaded = FabricLoader.getInstance().isModLoaded("flashback");
        int currentDockX = dockRightX;

        modMenuButton = null;
        if (modMenuLoaded) {
            modMenuButton = new MainMenuButtonWidget(
                    currentDockX, by, size, size, Text.literal(""), ICON_TAB, b -> openModMenu()
            );
            addDrawableChild(modMenuButton);
            currentDockX -= dockStep;
        }

        flashbackButton = null;
        if (flashbackLoaded) {
            flashbackButton = new MainMenuButtonWidget(
                    currentDockX, by, size, size, Text.literal(""), ICON_FLASHBACK, b -> openFlashbackReplays()
            );
            addDrawableChild(flashbackButton);
            currentDockX -= dockStep;
        }

        realmsButton = new MainMenuButtonWidget(
                currentDockX,
                by,
                size,
                size,
                Text.literal(""),
                ICON_REALMS,
                b -> openRealms()
        );
        addDrawableChild(realmsButton);
        currentDockX -= dockStep;

        settingsButton = new MainMenuButtonWidget(
                currentDockX,
                by,
                size,
                size,
                Text.literal(""),
                ICON_SETTINGS,
                b -> this.client.setScreen(new OptionsScreen(this, this.client.options))
        );
        addDrawableChild(settingsButton);

        int topInset = Math.max(8, Math.round(10.0F * scale));
        themeSelectorButton = new MainMenuButtonWidget(
                overlayW - topInset - size,
                topInset,
                size,
                size,
                Text.literal(""),
                ICON_PAINTBRUSH,
                b -> setThemeSelectorOpen(!themeSelectorOpen)
        );
        addDrawableChild(themeSelectorButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateOverlayMetrics();
        syncDisplayedPanoramaName();
        updateThemeUiAnimationTiming();
        renderMainMenuBackground(context, delta);
        context.getMatrices().push();
        context.getMatrices().scale(overlayRenderScale, overlayRenderScale, 1.0F);
        int overlayMouseX = Math.round(toOverlayCoordinate(mouseX));
        int overlayMouseY = Math.round(toOverlayCoordinate(mouseY));
        float overlayW = getOverlayViewportWidth();
        float overlayH = getOverlayViewportHeight();
        if (themeSelectorOpen) {
            context.fill(0, 0, Math.round(overlayW), Math.round(overlayH), 0x76070B12);
            renderThemeSelectorModal(context, overlayMouseX, overlayMouseY, mouseX, mouseY, delta);
        } else {
            renderMainMenuWidgets(context, overlayMouseX, overlayMouseY, delta);
            renderFooterTexts(context, overlayW, overlayH);
            renderSettingsTooltip(context);
            renderRealmsTooltip(context);
            renderFlashbackTooltip(context);
            renderModMenuTooltip(context, overlayMouseX, overlayMouseY);
            renderThemeSelectorTooltip(context);
        }
        context.getMatrices().pop();
    }

    private void renderMainMenuBackground(DrawContext context, float delta) {
        if (this.client != null && this.client.world != null) {
            this.renderInGameBackground(context);
            return;
        }
        this.renderPanoramaBackground(context, delta);
    }

    private void renderMainMenuWidgets(DrawContext context, int mouseX, int mouseY, float delta) {
        for (var child : this.children()) {
            if (child instanceof net.minecraft.client.gui.Drawable drawable) {
                drawable.render(context, mouseX, mouseY, delta);
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void renderPanoramaBackground(DrawContext context, float delta) {
        if (this.client == null
                || this.client.getOverlay() != null
                || MenuUiSettings.getInstance().getSelectedPanoramaPreset() == MenuUiSettings.PanoramaPreset.VANILLA) {
            super.renderPanoramaBackground(context, delta);
            return;
        }
        MenuUiSettings.getInstance().getSelectedPanoramaPreset().getRenderer().render(context, this.width, this.height, 1.0F);
    }

    private void openModMenu() {
        if (this.client == null) return;
        try {
            Class<?> screenClass = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
            Constructor<?> ctor = screenClass.getConstructor(Screen.class);
            Screen screen = (Screen) ctor.newInstance(this);
            this.client.setScreen(screen);
        } catch (Throwable ignored) {
        }
    }

    private void openRealms() {
        if (this.client == null) return;
        this.client.setScreen(new RealmsMainScreen(this));
    }

    private void openFlashbackReplays() {
        if (this.client == null) return;
        try {
            Class<?> screenClass = Class.forName("com.moulberry.flashback.screen.select_replay.SelectReplayScreen");
            Constructor<?> ctor = screenClass.getConstructor(Screen.class);
            Screen screen = (Screen) ctor.newInstance(this);
            this.client.setScreen(screen);
        } catch (Throwable ignored) {
        }
    }

    private void renderModMenuTooltip(DrawContext context, int mouseX, int mouseY) {
        if (modMenuButton == null || !modMenuButton.isHovered()) {
            return;
        }
        float scale = phaze$menuScale();
        float w = 116.0F * scale;
        float h = 40.0F * scale;
        float x = modMenuButton.getX() + (modMenuButton.getWidth() - w) / 2.0F;
        float y = modMenuButton.getY() - h - (6.0F * scale);

        rectangle.render(ShapeProperties.create(context.getMatrices(), x, y, w, h)
                .round(6.0F)
                .softness(1.1F)
                .thickness(1.4F)
                .outlineColor(0xFF1E2740)
                .color(0xFF0A1020)
                .build());

        String line1 = "Fabric Mod Menu";
        String line2 = "External";
        MsdfRenderer.renderText(
                MsdfFonts.bold(), line1, 9.6F * scale, TOOLTIP_TEXT_COLOR,
                context.getMatrices().peek().getPositionMatrix(),
                x + 8.0F * scale, y + 8.0F * scale, 0.0F
        );
        renderTooltipLine(context, ICON_EXTERNAL, line2, x + 8.0F * scale, y + 22.0F * scale, scale, -1.0F * scale, (12.5F / (1.3F * 1.15F)) * scale);
    }

    private void renderSettingsTooltip(DrawContext context) {
        if (settingsButton == null || !settingsButton.isHovered()) {
            return;
        }
        float scale = phaze$menuScale();
        float w = 140.0F * scale;
        float h = 40.0F * scale;
        float x = settingsButton.getX() + (settingsButton.getWidth() - w) / 2.0F;
        float y = settingsButton.getY() - h - (6.0F * scale);

        rectangle.render(ShapeProperties.create(context.getMatrices(), x, y, w, h)
                .round(6.0F)
                .softness(1.1F)
                .thickness(1.4F)
                .outlineColor(0xFF1E2740)
                .color(0xFF0A1020)
                .build());

        MsdfRenderer.renderText(
                MsdfFonts.bold(), "Minecraft Settings", 9.6F * scale, TOOLTIP_TEXT_COLOR,
                context.getMatrices().peek().getPositionMatrix(),
                x + 8.0F * scale, y + 8.0F * scale, 0.0F
        );
        renderTooltipLine(context, ICON_CUBE, "Minecraft", x + 8.0F * scale, y + 22.0F * scale, scale, -1.0F * scale);
    }

    private void renderRealmsTooltip(DrawContext context) {
        if (realmsButton == null || !realmsButton.isHovered()) {
            return;
        }
        float scale = phaze$menuScale();
        float w = 136.0F * scale;
        float h = 40.0F * scale;
        float x = realmsButton.getX() + (realmsButton.getWidth() - w) / 2.0F;
        float y = realmsButton.getY() - h - (6.0F * scale);

        rectangle.render(ShapeProperties.create(context.getMatrices(), x, y, w, h)
                .round(6.0F)
                .softness(1.1F)
                .thickness(1.4F)
                .outlineColor(0xFF1E2740)
                .color(0xFF0A1020)
                .build());

        MsdfRenderer.renderText(
                MsdfFonts.bold(), "Minecraft Realms", 9.6F * scale, TOOLTIP_TEXT_COLOR,
                context.getMatrices().peek().getPositionMatrix(),
                x + 8.0F * scale, y + 8.0F * scale, 0.0F
        );
        renderTooltipLine(context, ICON_CUBE, "Minecraft", x + 8.0F * scale, y + 22.0F * scale, scale, -1.0F * scale);
    }

    private void renderFlashbackTooltip(DrawContext context) {
        if (flashbackButton == null || !flashbackButton.isHovered()) {
            return;
        }
        float scale = phaze$menuScale();
        float w = 116.0F * scale;
        float h = 40.0F * scale;
        float x = flashbackButton.getX() + (flashbackButton.getWidth() - w) / 2.0F;
        float y = flashbackButton.getY() - h - (6.0F * scale);

        rectangle.render(ShapeProperties.create(context.getMatrices(), x, y, w, h)
                .round(6.0F)
                .softness(1.1F)
                .thickness(1.4F)
                .outlineColor(0xFF1E2740)
                .color(0xFF0A1020)
                .build());

        MsdfRenderer.renderText(
                MsdfFonts.bold(), "Flashback Mod", 9.6F * scale, TOOLTIP_TEXT_COLOR,
                context.getMatrices().peek().getPositionMatrix(),
                x + 8.0F * scale, y + 8.0F * scale, 0.0F
        );
        renderTooltipLine(context, ICON_EXTERNAL, "External", x + 8.0F * scale, y + 22.0F * scale, scale, -1.0F * scale, (12.5F / (1.3F * 1.15F)) * scale);
    }

    private void renderThemeSelectorTooltip(DrawContext context) {
        if (themeSelectorButton == null || !themeSelectorButton.isHovered()) {
            return;
        }
        float scale = phaze$menuScale();
        String title = "Theme Selector";
        float textSize = 9.6F * scale;
        float h = 30.0F * scale;
        float w = Math.max(84.0F * scale, MsdfFonts.bold().getWidth(title, textSize) + 18.0F * scale);
        float x = Math.max(6.0F * scale, themeSelectorButton.getX() - w - (6.0F * scale));
        float y = themeSelectorButton.getY() + (themeSelectorButton.getHeight() - h) / 2.0F;

        rectangle.render(ShapeProperties.create(context.getMatrices(), x, y, w, h)
                .round(6.0F)
                .softness(1.1F)
                .thickness(1.4F)
                .outlineColor(0xFF1E2740)
                .color(0xFF0A1020)
                .build());

        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                title,
                textSize,
                TOOLTIP_TEXT_COLOR,
                context.getMatrices().peek().getPositionMatrix(),
                x + 9.0F * scale,
                MenuStyle.centerMsdfTextY(textSize, y, h),
                0.0F
        );
    }

    private void renderThemeSelectorModal(DrawContext context, int mouseX, int mouseY, int rawMouseX, int rawMouseY, float delta) {
        ThemeSelectorLayout layout = getThemeSelectorLayout();
        List<MenuUiSettings.PanoramaPreset> visiblePresets = getVisibleThemeSelectorPresets();
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 140.0F);

        rectangle.render(ShapeProperties.create(context.getMatrices(), layout.panelX, layout.panelY, layout.panelW, layout.panelH)
                .round(8.0F)
                .softness(1.2F)
                .thickness(1.5F)
                .outlineColor(0xFF212838)
                .color(0xFF11161F)
                .build());

        float titleTextSize = 14.0F * layout.scale;
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                "Select Theme",
                titleTextSize,
                0xFFF6F8FE,
                context.getMatrices().peek().getPositionMatrix(),
                layout.panelX + 12.0F * layout.scale,
                layout.panelY + 11.0F * layout.scale,
                0.0F
        );

        boolean settingsHovered = isPointInside(mouseX, mouseY, layout.settingsX, layout.settingsY, layout.settingsSize, layout.settingsSize);
        boolean closeHovered = isPointInside(mouseX, mouseY, layout.closeX, layout.closeY, layout.closeSize, layout.closeSize);
        themeSelectorSettingsHoverAnim = animateThemeUiValue(themeSelectorSettingsHoverAnim, settingsHovered ? 1.0F : 0.0F);
        themeSelectorCloseHoverAnim = animateThemeUiValue(themeSelectorCloseHoverAnim, closeHovered ? 1.0F : 0.0F);
        renderThemeSelectorIconAction(context, ICON_SETTINGS, layout.settingsX, layout.settingsY, layout.settingsSize, themeSelectorSettingsHoverAnim);
        renderThemeSelectorCloseAction(context, layout.closeX, layout.closeY, layout.closeSize, themeSelectorCloseHoverAnim);

        boolean searchHovered = isPointInside(mouseX, mouseY, layout.searchX, layout.searchY, layout.searchW, layout.searchH);
        rectangle.render(ShapeProperties.create(context.getMatrices(), layout.searchX, layout.searchY, layout.searchW, layout.searchH)
                .round(6.0F)
                .softness(1.0F)
                .thickness(1.0F)
                .outlineColor(themeSelectorSearchFocused ? 0xFF4D617F : (searchHovered ? 0xFF364256 : 0xFF2B3343))
                .color(0xFF232734)
                .build());

        renderThemeSelectorSearchText(context, layout);

        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                "Themes (" + visiblePresets.size() + ")",
                9.4F * layout.scale,
                0xFFE5EAF7,
                context.getMatrices().peek().getPositionMatrix(),
                layout.panelX + 12.0F * layout.scale,
                layout.sectionLabelY,
                0.0F
        );

        renderThemePreviewCards(context, layout, visiblePresets, mouseX, mouseY);

        boolean resetHovered = isPointInside(mouseX, mouseY, layout.resetX, layout.resetY, layout.resetW, layout.resetH);
        float footerTextSize = (13.8F / 1.2F) * layout.scale;
        float resetTextSize = (13.65F / 1.2F) * layout.scale;
        float footerRowY = layout.footerY - 9.0F * layout.scale;
        float footerTextY = MenuStyle.centerMsdfTextY(footerTextSize, footerRowY, 18.0F * layout.scale);
        float resetTextY = MenuStyle.centerMsdfTextY(resetTextSize, footerRowY, 18.0F * layout.scale);
        float resetIconSize = 11.0F * layout.scale;
        float resetIconY = footerRowY + (18.0F * layout.scale - resetIconSize) / 2.0F;
        MsdfRenderer.renderText(
                MsdfFonts.medium(),
                "Active: " + activeThemeName,
                footerTextSize,
                0xFFE5EAF7,
                context.getMatrices().peek().getPositionMatrix(),
                layout.panelX + 12.0F * layout.scale,
                footerTextY,
                0.0F
        );

        renderMenuIcon(context, ICON_RESET, layout.resetX, resetIconY, resetIconSize, resetIconSize, resetHovered ? 0xFFFFFFFF : ICON_TINT_COLOR);
        MsdfRenderer.renderText(
                MsdfFonts.medium(),
                "Reset to default",
                resetTextSize,
                resetHovered ? 0xFFFFFFFF : 0xFFCBD4E6,
                context.getMatrices().peek().getPositionMatrix(),
                layout.resetX + resetIconSize + 4.0F * layout.scale,
                resetTextY,
                0.0F
        );

        if (themeSelectorSettingsOpen) {
            renderThemeSettingsModal(context, layout, mouseX, mouseY);
        }
        context.getMatrices().pop();
    }

    private void renderThemePreviewCards(DrawContext context, ThemeSelectorLayout layout, List<MenuUiSettings.PanoramaPreset> presets, int mouseX, int mouseY) {
        if (presets.isEmpty()) {
            float textSize = 9.2F * layout.scale;
            String text = "No panoramas found";
            MsdfRenderer.renderText(
                    MsdfFonts.medium(),
                    text,
                    textSize,
                    0xFF9099AD,
                    context.getMatrices().peek().getPositionMatrix(),
                    layout.panelX + (layout.panelW - MsdfFonts.medium().getWidth(text, textSize)) / 2.0F,
                    layout.cardsY + layout.cardPreviewH / 2.0F - textSize / 2.0F,
                    0.0F
            );
            return;
        }

        int index = 0;
        for (MenuUiSettings.PanoramaPreset preset : presets) {
            renderThemePreviewCard(context, layout, preset, index++, presets.size(), mouseX, mouseY);
        }
    }

    private void renderThemePreviewCard(DrawContext context, ThemeSelectorLayout layout, MenuUiSettings.PanoramaPreset preset, int index, int visibleCount, int mouseX, int mouseY) {
        float cardX = getThemeSelectorCardX(layout, index, visibleCount);
        float cardY = layout.cardsY;
        float cardW = layout.cardPreviewW;
        float cardH = layout.cardTotalHeight;
        float previewX = cardX;
        float previewY = cardY;
        float previewW = cardW;
        float previewH = layout.cardPreviewH;

        boolean selected = MenuUiSettings.getInstance().getSelectedPanoramaPreset() == preset;
        boolean hovered = isPointInside(mouseX, mouseY, cardX, cardY, cardW, cardH);

        int previewOutline = selected
                ? (hovered ? 0xFF1F7E5A : 0xFF165A41)
                : (hovered ? 0xFF343C4F : 0xFF242A37);
        rectangle.render(ShapeProperties.create(context.getMatrices(), previewX, previewY, previewW, previewH)
                .round(7.0F)
                .softness(1.0F)
                .thickness(1.3F)
                .outlineColor(previewOutline)
                .color(0x00000000)
                .build());
        renderPanoramaPresetPreview(context, preset, previewX + 1.0F, previewY + 1.0F, previewW - 2.0F);

        if (hovered) {
            rectangle.render(ShapeProperties.create(context.getMatrices(), previewX + 1.0F, previewY + 1.0F, previewW - 2.0F, previewH - 2.0F)
                    .round(6.0F)
                    .softness(1.0F)
                    .color(0x14FFFFFF)
                    .build());
        }

        float nameTextSize = 8.6F * layout.scale;
        float nameY = previewY + previewH + 6.0F * layout.scale;
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                preset.displayName(),
                nameTextSize,
                0xFFF5F7FD,
                context.getMatrices().peek().getPositionMatrix(),
                previewX,
                nameY,
                0.0F
        );

        float badgeOuterSize = 11.0F * layout.scale;
        float badgeInnerSize = 7.0F * layout.scale;
        float badgeX = previewX + previewW - badgeOuterSize;
        float badgeY = nameY + 0.5F * layout.scale;
        rectangle.render(ShapeProperties.create(context.getMatrices(), badgeX, badgeY, badgeOuterSize, badgeOuterSize)
                .round(badgeOuterSize / 2.0F)
                .softness(1.0F)
                .color(selected ? 0xFF133926 : 0xFF2A3140)
                .build());
        float badgeInnerOffset = (badgeOuterSize - badgeInnerSize) / 2.0F;
        rectangle.render(ShapeProperties.create(context.getMatrices(), badgeX + badgeInnerOffset, badgeY + badgeInnerOffset, badgeInnerSize, badgeInnerSize)
                .round(badgeInnerSize / 2.0F)
                .softness(1.0F)
                .color(selected ? 0xFF25E163 : 0xFF7F8798)
                .build());
    }

    private void renderPanoramaPresetPreview(DrawContext context, MenuUiSettings.PanoramaPreset preset, float x, float y, float size) {
        if (this.client != null) {
            preset.getRenderer().prepareTextures(this.client);
        }
        int textureSize = preset.previewTextureSize();
        int cropInset = preset.previewCropInset();
        int cropSize = preset.previewCropSize();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Render2DUtil.drawTexture(
                context.getMatrices(),
                preset.previewTexture(),
                x,
                x + Math.max(1.0F, size),
                y,
                y + Math.max(1.0F, size),
                0.0F,
                cropSize,
                cropSize,
                cropInset,
                cropInset,
                textureSize,
                textureSize,
                0xFFFFFFFF
        );
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private float getThemeSelectorCardX(ThemeSelectorLayout layout, int index, int visibleCount) {
        float totalWidth = visibleCount * layout.cardPreviewW + Math.max(0, visibleCount - 1) * layout.cardsGap;
        float startX = layout.panelX + (layout.panelW - totalWidth) / 2.0F;
        return startX + index * (layout.cardPreviewW + layout.cardsGap);
    }

    private void renderThemeSelectorIconAction(DrawContext context, Identifier icon, float x, float y, float size, float hoverAnim) {
        float iconHeight = 13.0F * phaze$menuScale();
        float iconWidth = iconHeight * getIconAspectRatio(icon);
        renderMenuIcon(
                context,
                icon,
                x + (size - iconWidth) / 2.0F,
                y + (size - iconHeight) / 2.0F,
                iconWidth,
                iconHeight,
                lerpArgb(ICON_TINT_COLOR, 0xFFFFFFFF, hoverAnim)
        );
    }

    private void renderThemeSelectorCloseAction(DrawContext context, float x, float y, float size, float hoverAnim) {
        float iconSize = (13.0F / (1.2F * 1.1F)) * phaze$menuScale();
        renderMenuIcon(
                context,
                ICON_CROSS,
                x + (size - iconSize) / 2.0F,
                y + (size - iconSize) / 2.0F,
                iconSize,
                iconSize,
                lerpArgb(ICON_TINT_COLOR, 0xFFFFFFFF, hoverAnim)
        );
    }

    private void renderThemeSettingsModal(DrawContext context, ThemeSelectorLayout themeLayout, int mouseX, int mouseY) {
        ThemeSettingsLayout layout = getThemeSettingsLayout(themeLayout);
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 200.0F);

        rectangle.render(ShapeProperties.create(context.getMatrices(), layout.panelX, layout.panelY, layout.panelW, layout.panelH)
                .round(8.0F)
                .softness(1.2F)
                .thickness(1.5F)
                .outlineColor(0xFF222A39)
                .color(0xFF121720)
                .build());

        float titleSize = 14.0F * layout.scale;
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                "Settings",
                titleSize,
                0xFFF6F8FE,
                context.getMatrices().peek().getPositionMatrix(),
                layout.panelX + 13.0F * layout.scale,
                layout.panelY + 11.0F * layout.scale,
                0.0F
        );

        boolean closeHovered = isPointInside(mouseX, mouseY, layout.closeX, layout.closeY, layout.closeSize, layout.closeSize);
        themeSettingsCloseHoverAnim = animateThemeUiValue(themeSettingsCloseHoverAnim, closeHovered ? 1.0F : 0.0F);
        renderThemeSelectorCloseAction(context, layout.closeX, layout.closeY, layout.closeSize, themeSettingsCloseHoverAnim);

        panoramaSliderVisualProgress = animateThemeUiValue(
                panoramaSliderVisualProgress,
                (float) (MenuUiSettings.getInstance().getPanoramaSpeed() / 100.0D)
        );
        guiFpsSliderVisualProgress = animateThemeUiValue(
                guiFpsSliderVisualProgress,
                (MenuUiSettings.getInstance().getGuiFpsLimit() - MenuUiSettings.MIN_GUI_FPS_LIMIT)
                        / (float) (MenuUiSettings.MAX_GUI_FPS_LIMIT - MenuUiSettings.MIN_GUI_FPS_LIMIT)
        );

        renderThemeSettingsSlider(
                context,
                layout,
                "Rotating Panorama Speed",
                "Default: 10%",
                Math.round(MenuUiSettings.getInstance().getPanoramaSpeed()) + "%",
                layout.panoramaTrackX,
                layout.panoramaTrackY,
                layout.trackWidth,
                layout.trackHeight,
                panoramaSliderVisualProgress,
                mouseX,
                mouseY,
                activeThemeSettingsSlider == ThemeSettingsSlider.PANORAMA_SPEED,
                layout.panoramaValueX,
                layout.panoramaLabelY
        );

        renderThemeSettingsSlider(
                context,
                layout,
                "Menu FPS Limit",
                "Applies to Main Menu, Mod Menu, Sodium and other screens",
                MenuUiSettings.getInstance().getGuiFpsLimit() + " FPS",
                layout.fpsTrackX,
                layout.fpsTrackY,
                layout.trackWidth,
                layout.trackHeight,
                guiFpsSliderVisualProgress,
                mouseX,
                mouseY,
                activeThemeSettingsSlider == ThemeSettingsSlider.GUI_FPS_LIMIT,
                layout.fpsValueX,
                layout.fpsLabelY
        );

        float presetTitleSize = 9.8F * layout.scale;
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                "Panorama Preset",
                presetTitleSize,
                0xFFF0F4FE,
                context.getMatrices().peek().getPositionMatrix(),
                layout.contentX,
                layout.presetSectionY,
                0.0F
        );

        renderPanoramaPresetOption(context, layout, MenuUiSettings.PanoramaPreset.VANILLA, 0, mouseX, mouseY);
        renderPanoramaPresetOption(context, layout, MenuUiSettings.PanoramaPreset.CHATEAU, 1, mouseX, mouseY);
        renderPanoramaPresetOption(context, layout, MenuUiSettings.PanoramaPreset.POST_SOVIET_NIGHT, 2, mouseX, mouseY);
        renderPanoramaPresetOption(context, layout, MenuUiSettings.PanoramaPreset.CASTLE, 3, mouseX, mouseY);
        context.getMatrices().pop();
    }

    private void renderThemeSettingsSlider(
            DrawContext context,
            ThemeSettingsLayout layout,
            String title,
            String subtitle,
            String valueLabel,
            float trackX,
            float trackY,
            float trackWidth,
            float trackHeight,
            float progress,
            int mouseX,
            int mouseY,
            boolean dragging,
            float valueX,
            float labelY
    ) {
        float clampedProgress = Math.max(0.0F, Math.min(1.0F, progress));
        float titleSize = 9.8F * layout.scale;
        float valueSize = 9.2F * layout.scale;
        float subtitleSize = 7.9F * layout.scale;

        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                title,
                titleSize,
                0xFFF0F4FE,
                context.getMatrices().peek().getPositionMatrix(),
                layout.contentX,
                labelY,
                0.0F
        );

        MsdfRenderer.renderText(
                MsdfFonts.medium(),
                valueLabel,
                valueSize,
                0xFFCAD5EC,
                context.getMatrices().peek().getPositionMatrix(),
                valueX,
                labelY + 1.0F * layout.scale,
                0.0F
        );

        boolean hovered = isPointInside(mouseX, mouseY, trackX, trackY - 3.0F * layout.scale, trackWidth, trackHeight + 6.0F * layout.scale);
        rectangle.render(ShapeProperties.create(context.getMatrices(), trackX, trackY, trackWidth, trackHeight)
                .round(trackHeight / 2.0F)
                .softness(1.0F)
                .thickness(1.0F)
                .outlineColor(hovered || dragging ? 0xFF32435E : 0xFF263142)
                .color(0xFF1A212D)
                .build());

        float fillWidth = Math.max(trackHeight, trackWidth * clampedProgress);
        rectangle.render(ShapeProperties.create(context.getMatrices(), trackX, trackY, fillWidth, trackHeight)
                .round(trackHeight / 2.0F)
                .softness(1.0F)
                .color(0xFF4F9CFF)
                .build());

        float knobSize = trackHeight + 5.0F * layout.scale;
        float knobX = trackX + trackWidth * clampedProgress - knobSize / 2.0F;
        float knobY = trackY - (knobSize - trackHeight) / 2.0F;
        rectangle.render(ShapeProperties.create(context.getMatrices(), knobX, knobY, knobSize, knobSize)
                .round(knobSize / 2.0F)
                .softness(1.0F)
                .thickness(1.0F)
                .outlineColor(0xFF1F2A38)
                .color(dragging ? 0xFFFFFFFF : 0xFFF3F7FF)
                .build());

        MsdfRenderer.renderText(
                MsdfFonts.medium(),
                subtitle,
                subtitleSize,
                0xFF8E99AF,
                context.getMatrices().peek().getPositionMatrix(),
                layout.contentX,
                trackY + trackHeight + 8.0F * layout.scale,
                0.0F
        );
    }

    private void renderPanoramaPresetOption(
            DrawContext context,
            ThemeSettingsLayout layout,
            MenuUiSettings.PanoramaPreset preset,
            int index,
            int mouseX,
            int mouseY
    ) {
        float rowY = getPanoramaPresetRowY(layout, index);
        boolean selected = MenuUiSettings.getInstance().getSelectedPanoramaPreset() == preset;
        boolean hovered = isPointInside(mouseX, mouseY, layout.contentX, rowY, layout.contentRight - layout.contentX, layout.presetRowHeight);

        rectangle.render(ShapeProperties.create(context.getMatrices(), layout.contentX, rowY, layout.contentRight - layout.contentX, layout.presetRowHeight)
                .round(6.0F)
                .softness(1.0F)
                .thickness(1.0F)
                .outlineColor(selected ? 0xFF2D5B46 : hovered ? 0xFF313D50 : 0xFF242D3A)
                .color(selected ? 0xFF15211B : hovered ? 0xFF18202A : 0xFF141A23)
                .build());

        float dotOuter = 10.0F * layout.scale;
        float dotInner = 6.0F * layout.scale;
        float dotX = layout.contentX + 8.0F * layout.scale;
        float dotY = rowY + (layout.presetRowHeight - dotOuter) / 2.0F;
        rectangle.render(ShapeProperties.create(context.getMatrices(), dotX, dotY, dotOuter, dotOuter)
                .round(dotOuter / 2.0F)
                .softness(1.0F)
                .color(selected ? 0xFF244734 : 0xFF262F3C)
                .build());
        float dotInset = (dotOuter - dotInner) / 2.0F;
        rectangle.render(ShapeProperties.create(context.getMatrices(), dotX + dotInset, dotY + dotInset, dotInner, dotInner)
                .round(dotInner / 2.0F)
                .softness(1.0F)
                .color(selected ? 0xFF25E163 : 0xFF7F8798)
                .build());

        float nameSize = 8.9F * layout.scale;
        MsdfRenderer.renderText(
                MsdfFonts.medium(),
                preset.displayName(),
                nameSize,
                selected ? 0xFFF4F8FF : 0xFFCCD5E6,
                context.getMatrices().peek().getPositionMatrix(),
                dotX + dotOuter + 6.0F * layout.scale,
                MenuStyle.centerMsdfTextY(nameSize, rowY, layout.presetRowHeight),
                0.0F
        );
    }

    private void renderTooltipLine(DrawContext context, Identifier icon, String text, float x, float y, float scale) {
        renderTooltipLine(context, icon, text, x, y, scale, 0.0F);
    }

    private void renderTooltipLine(DrawContext context, Identifier icon, String text, float x, float y, float scale, float iconYOffset) {
        renderTooltipLine(context, icon, text, x, y, scale, iconYOffset, 9.0F * scale);
    }

    private void renderTooltipLine(DrawContext context, Identifier icon, String text, float x, float y, float scale, float iconYOffset, float iconSize) {
        renderMenuIcon(context, icon, x, y + iconYOffset, iconSize, iconSize);
        MsdfRenderer.renderText(
                MsdfFonts.medium(), text, 8.6F * scale, TOOLTIP_TEXT_COLOR,
                context.getMatrices().peek().getPositionMatrix(),
                x + iconSize + 4.0F * scale, y, 0.0F
        );
    }

    private void renderFooterTexts(DrawContext context, float overlayWidth, float overlayHeight) {
        float scale = phaze$menuScale();
        float footerSize = 12.0F * scale;
        float footerMarginX = 10.0F;
        float footerRowHeight = 16.0F * scale;
        float footerBottomMargin = 10.0F;
        float rightFooterExtraOffset = 10.0F;
        float footerRowY = overlayHeight - footerBottomMargin - footerRowHeight;
        float footerTextY = MenuStyle.centerMsdfTextY(footerSize, footerRowY, footerRowHeight);
        int footerColor = MenuStyle.withAlpha(TOOLTIP_TEXT_COLOR, 0.65F);

        MsdfRenderer.renderText(
                MsdfFonts.medium(),
                FOOTER_LEFT_TEXT,
                footerSize,
                footerColor,
                context.getMatrices().peek().getPositionMatrix(),
                footerMarginX,
                footerTextY,
                0.0F
        );

        float rightFooterWidth = MsdfFonts.medium().getWidth(FOOTER_RIGHT_TEXT, footerSize);
        float rightFooterX = Math.max(footerMarginX, overlayWidth - footerMarginX - rightFooterExtraOffset - rightFooterWidth);
        MsdfRenderer.renderText(
                MsdfFonts.medium(),
                FOOTER_RIGHT_TEXT,
                footerSize,
                footerColor,
                context.getMatrices().peek().getPositionMatrix(),
                rightFooterX,
                footerTextY,
                0.0F
        );
    }

    private float phaze$menuScale() {
        return 1.0F / 1.4F;
    }

    private static void renderMenuIcon(DrawContext context, Identifier icon, float x, float y, float width, float height) {
        renderMenuIcon(context, icon, x, y, width, height, ICON_TINT_COLOR);
    }

    private static void renderMenuIcon(DrawContext context, Identifier icon, float x, float y, float width, float height, int color) {
        Render2DUtil.drawTexture(
                context.getMatrices(),
                icon,
                Math.round(x),
                Math.round(y),
                Math.max(1, Math.round(width)),
                Math.max(1, Math.round(height)),
                getIconRegionX(icon),
                getIconRegionY(icon),
                getIconRegionWidth(icon),
                getIconRegionHeight(icon),
                getIconTextureWidth(icon),
                getIconTextureHeight(icon),
                color
        );
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static int getIconTextureWidth(Identifier icon) {
        if (ICON_PAINTBRUSH.equals(icon)) {
            return 288;
        }
        if (ICON_FLASHBACK.equals(icon)) {
            return 96;
        }
        if (ICON_LOGOUT.equals(icon)) {
            return 512;
        }
        if (ICON_SINGLE.equals(icon)) {
            return 224;
        }
        if (ICON_MULTI.equals(icon)) {
            return 320;
        }
        if (ICON_RESET.equals(icon)) {
            return 32;
        }
        return 256;
    }

    private static int getIconTextureHeight(Identifier icon) {
        if (ICON_PAINTBRUSH.equals(icon)) {
            return 256;
        }
        if (ICON_FLASHBACK.equals(icon)) {
            return 96;
        }
        if (ICON_LOGOUT.equals(icon)) {
            return 512;
        }
        if (ICON_RESET.equals(icon)) {
            return 32;
        }
        return 256;
    }

    private static int getIconRegionX(Identifier icon) {
        if (ICON_PAINTBRUSH.equals(icon)) {
            return 16;
        }
        if (ICON_EXTERNAL.equals(icon)) {
            return 41;
        }
        if (ICON_REALMS.equals(icon)) {
            return 42;
        }
        if (ICON_FLASHBACK.equals(icon)) {
            return 7;
        }
        if (ICON_CROSS.equals(icon)) {
            return 34;
        }
        if (ICON_RESET.equals(icon)) {
            return 2;
        }
        return ICON_LOGOUT.equals(icon) ? 48 : 0;
    }

    private static int getIconRegionY(Identifier icon) {
        if (ICON_PAINTBRUSH.equals(icon)) {
            return 0;
        }
        if (ICON_EXTERNAL.equals(icon)) {
            return 27;
        }
        if (ICON_REALMS.equals(icon)) {
            return 25;
        }
        if (ICON_FLASHBACK.equals(icon)) {
            return 17;
        }
        if (ICON_CROSS.equals(icon)) {
            return 34;
        }
        if (ICON_RESET.equals(icon)) {
            return 2;
        }
        return ICON_LOGOUT.equals(icon) ? 34 : 0;
    }

    private static int getIconRegionWidth(Identifier icon) {
        if (ICON_PAINTBRUSH.equals(icon)) {
            return 272;
        }
        if (ICON_EXTERNAL.equals(icon)) {
            return 188;
        }
        if (ICON_REALMS.equals(icon)) {
            return 172;
        }
        if (ICON_FLASHBACK.equals(icon)) {
            return 82;
        }
        if (ICON_CROSS.equals(icon)) {
            return 188;
        }
        if (ICON_RESET.equals(icon)) {
            return 28;
        }
        return ICON_LOGOUT.equals(icon) ? 422 : getIconTextureWidth(icon);
    }

    private static int getIconRegionHeight(Identifier icon) {
        if (ICON_PAINTBRUSH.equals(icon)) {
            return 256;
        }
        if (ICON_EXTERNAL.equals(icon)) {
            return 188;
        }
        if (ICON_REALMS.equals(icon)) {
            return 206;
        }
        if (ICON_FLASHBACK.equals(icon)) {
            return 62;
        }
        if (ICON_CROSS.equals(icon)) {
            return 188;
        }
        if (ICON_RESET.equals(icon)) {
            return 28;
        }
        return ICON_LOGOUT.equals(icon) ? 443 : getIconTextureHeight(icon);
    }

    private static float getIconAspectRatio(Identifier icon) {
        return getIconRegionWidth(icon) / (float) getIconRegionHeight(icon);
    }

    private static int lerpArgb(int from, int to, float progress) {
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        int fromA = (from >>> 24) & 0xFF;
        int fromR = (from >>> 16) & 0xFF;
        int fromG = (from >>> 8) & 0xFF;
        int fromB = from & 0xFF;
        int toA = (to >>> 24) & 0xFF;
        int toR = (to >>> 16) & 0xFF;
        int toG = (to >>> 8) & 0xFF;
        int toB = to & 0xFF;
        int a = Math.round(fromA + (toA - fromA) * clamped);
        int r = Math.round(fromR + (toR - fromR) * clamped);
        int g = Math.round(fromG + (toG - fromG) * clamped);
        int b = Math.round(fromB + (toB - fromB) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void updateThemeUiAnimationTiming() {
        long nowNs = System.nanoTime();
        float dt = themeUiLastFrameNs > 0L
                ? Math.min(0.05F, (nowNs - themeUiLastFrameNs) / 1_000_000_000.0F)
                : (1.0F / 60.0F);
        themeUiLastFrameNs = nowNs;
        themeUiSmoothing = 1.0F - (float) Math.exp(-dt * 14.0F);
    }

    private float animateThemeUiValue(float current, float target) {
        float next = current + (target - current) * themeUiSmoothing;
        if (Math.abs(target - next) < 0.001F) {
            return target;
        }
        return next;
    }

    private void updateOverlayMetrics() {
        Window currentWindow = MinecraftClient.getInstance().getWindow();
        if (currentWindow == null) {
            overlayScaleFactor = PREFERRED_OVERLAY_SCALE;
            overlayRenderScale = 1.0F;
            overlayViewportWidth = DEFAULT_OVERLAY_WIDTH;
            overlayViewportHeight = DEFAULT_OVERLAY_HEIGHT;
            return;
        }

        int framebufferWidth = Math.max(1, currentWindow.getFramebufferWidth());
        int framebufferHeight = Math.max(1, currentWindow.getFramebufferHeight());
        double maxScaleByWidth = framebufferWidth / (double) DEFAULT_OVERLAY_WIDTH;
        double maxScaleByHeight = framebufferHeight / (double) DEFAULT_OVERLAY_HEIGHT;
        double safePreferredScale = Math.min(PREFERRED_OVERLAY_SCALE, Math.min(maxScaleByWidth, maxScaleByHeight));

        overlayScaleFactor = Math.max(MIN_OVERLAY_SCALE, safePreferredScale);
        overlayRenderScale = (float) (overlayScaleFactor / currentWindow.getScaleFactor());
        overlayViewportWidth = Math.max(1, (int) Math.floor(framebufferWidth / overlayScaleFactor));
        overlayViewportHeight = Math.max(1, (int) Math.floor(framebufferHeight / overlayScaleFactor));
    }

    private int getOverlayViewportWidth() {
        Window currentWindow = MinecraftClient.getInstance().getWindow();
        return overlayViewportWidth > 0 ? overlayViewportWidth : currentWindow != null ? currentWindow.getScaledWidth() : DEFAULT_OVERLAY_WIDTH;
    }

    private int getOverlayViewportHeight() {
        Window currentWindow = MinecraftClient.getInstance().getWindow();
        return overlayViewportHeight > 0 ? overlayViewportHeight : currentWindow != null ? currentWindow.getScaledHeight() : DEFAULT_OVERLAY_HEIGHT;
    }

    private float toOverlayCoordinate(double value) {
        return overlayRenderScale == 0.0F ? (float) value : (float) (value / overlayRenderScale);
    }

    private void setThemeSelectorOpen(boolean open) {
        if (this.themeSelectorOpen == open) {
            return;
        }
        this.themeSelectorOpen = open;
        this.themeSelectorSettingsOpen = false;
        this.themeSelectorSearchFocused = open;
        if (open) {
            this.themeSelectorSearchQuery = "";
        }
        this.themeSelectorSettingsHoverAnim = 0.0F;
        this.themeSelectorCloseHoverAnim = 0.0F;
        this.themeSettingsCloseHoverAnim = 0.0F;
        this.panoramaSliderVisualProgress = (float) (MenuUiSettings.getInstance().getPanoramaSpeed() / 100.0D);
        this.guiFpsSliderVisualProgress = (MenuUiSettings.getInstance().getGuiFpsLimit() - MenuUiSettings.MIN_GUI_FPS_LIMIT)
                / (float) (MenuUiSettings.MAX_GUI_FPS_LIMIT - MenuUiSettings.MIN_GUI_FPS_LIMIT);
        this.activeThemeSettingsSlider = ThemeSettingsSlider.NONE;
    }

    private void syncDisplayedPanoramaName() {
        this.activeThemeName = MenuUiSettings.getInstance().getSelectedPanoramaPreset().displayName();
    }

    private void setThemeSelectorSettingsOpen(boolean open) {
        this.themeSelectorSettingsOpen = open;
        this.themeSelectorSearchFocused = !open && this.themeSelectorOpen;
        if (open) {
            this.panoramaSliderVisualProgress = (float) (MenuUiSettings.getInstance().getPanoramaSpeed() / 100.0D);
            this.guiFpsSliderVisualProgress = (MenuUiSettings.getInstance().getGuiFpsLimit() - MenuUiSettings.MIN_GUI_FPS_LIMIT)
                    / (float) (MenuUiSettings.MAX_GUI_FPS_LIMIT - MenuUiSettings.MIN_GUI_FPS_LIMIT);
        }
        this.activeThemeSettingsSlider = ThemeSettingsSlider.NONE;
    }

    private ThemeSelectorLayout getThemeSelectorLayout() {
        float scale = phaze$menuScale();
        float overlayW = getOverlayViewportWidth();
        float overlayH = getOverlayViewportHeight();
        float panelW = Math.min(overlayW - 26.0F * scale, 418.0F);
        float panelH = Math.min(overlayH - 28.0F * scale, 238.0F);
        panelW = Math.max(320.0F, panelW);
        panelH = Math.max(210.0F, panelH);
        panelW = Math.min(panelW, overlayW - 12.0F * scale);
        panelH = Math.min(panelH, overlayH - 12.0F * scale);

        float panelX = (overlayW - panelW) / 2.0F;
        float panelY = (overlayH - panelH) / 2.0F;
        float closeSize = 18.0F * scale;
        float closeX = panelX + panelW - closeSize - 10.0F * scale;
        float closeY = panelY + 9.0F * scale;
        float settingsSize = closeSize;
        float settingsX = closeX - settingsSize - 6.0F * scale;
        float settingsY = closeY;

        float searchX = panelX + 12.0F * scale;
        float searchY = panelY + 34.0F * scale;
        float searchW = panelW - 24.0F * scale;
        float searchH = 22.0F * scale;
        float sectionLabelY = searchY + searchH + 12.0F * scale;

        float cardsGap = 9.0F * scale;
        float cardsX = panelX + 12.0F * scale;
        float cardsY = sectionLabelY + 13.0F * scale;
        float availableCardWidth = panelW - 24.0F * scale - cardsGap * 3.0F;
        float cardPreviewW = availableCardWidth / 4.0F;
        float cardPreviewH = cardPreviewW;
        float cardTotalHeight = cardPreviewH + 18.0F * scale;
        float footerY = panelY + panelH - 16.0F * scale;
        float resetTextSize = 13.65F * scale;
        float resetIconSize = 11.0F * scale;
        float resetW = resetIconSize + 4.0F * scale + MsdfFonts.medium().getWidth("Reset to default", resetTextSize);
        float resetH = 18.0F * scale;
        float resetX = panelX + panelW - 12.0F * scale - resetW;
        float resetY = footerY - 4.0F * scale;

        return new ThemeSelectorLayout(
                scale,
                overlayW,
                overlayH,
                panelX,
                panelY,
                panelW,
                panelH,
                settingsX,
                settingsY,
                settingsSize,
                closeX,
                closeY,
                closeSize,
                searchX,
                searchY,
                searchW,
                searchH,
                sectionLabelY,
                cardsX,
                cardsY,
                cardsGap,
                cardPreviewW,
                cardPreviewH,
                cardTotalHeight,
                footerY,
                resetX,
                resetY,
                resetW,
                resetH
        );
    }

    private ThemeSettingsLayout getThemeSettingsLayout(ThemeSelectorLayout parentLayout) {
        float scale = parentLayout.scale;
        float panelW = Math.min(parentLayout.panelW - 28.0F * scale, 282.0F);
        float panelH = Math.min(parentLayout.panelH - 18.0F * scale, 238.0F);
        float panelX = parentLayout.panelX + (parentLayout.panelW - panelW) / 2.0F;
        float panelY = parentLayout.panelY + 13.0F * scale;
        float closeSize = 18.0F * scale;
        float closeX = panelX + panelW - closeSize - 10.0F * scale;
        float closeY = panelY + 10.0F * scale;
        float contentX = panelX + 14.0F * scale;
        float contentRight = panelX + panelW - 14.0F * scale;
        float trackWidth = panelW - 28.0F * scale;
        float trackHeight = 7.0F * scale;
        float panoramaLabelY = panelY + 36.0F * scale;
        float panoramaTrackX = contentX;
        float panoramaTrackY = panoramaLabelY + 16.0F * scale;
        float fpsLabelY = panoramaTrackY + 35.0F * scale;
        float fpsTrackX = contentX;
        float fpsTrackY = fpsLabelY + 16.0F * scale;
        float presetSectionY = fpsTrackY + trackHeight + 24.0F * scale;
        float presetRowHeight = 18.0F * scale;
        float presetRowGap = 5.0F * scale;

        return new ThemeSettingsLayout(
                scale,
                panelX,
                panelY,
                panelW,
                panelH,
                closeX,
                closeY,
                closeSize,
                contentX,
                contentRight,
                trackWidth,
                trackHeight,
                panoramaLabelY,
                panoramaTrackX,
                panoramaTrackY,
                contentRight - MsdfFonts.medium().getWidth("100%", 9.2F * scale),
                fpsLabelY,
                fpsTrackX,
                fpsTrackY,
                contentRight - MsdfFonts.medium().getWidth(MenuUiSettings.MAX_GUI_FPS_LIMIT + " FPS", 9.2F * scale),
                presetSectionY,
                presetRowHeight,
                presetRowGap
        );
    }

    private void updateThemeSettingsSlider(ThemeSettingsSlider slider, ThemeSettingsLayout layout, float mouseX) {
        float progress = (mouseX - layout.contentX) / layout.trackWidth;
        progress = Math.max(0.0F, Math.min(1.0F, progress));

        if (slider == ThemeSettingsSlider.PANORAMA_SPEED) {
            MenuUiSettings.getInstance().setPanoramaSpeed(progress * 100.0D);
            return;
        }

        if (slider == ThemeSettingsSlider.GUI_FPS_LIMIT) {
            int fpsRange = MenuUiSettings.MAX_GUI_FPS_LIMIT - MenuUiSettings.MIN_GUI_FPS_LIMIT;
            int fps = MenuUiSettings.MIN_GUI_FPS_LIMIT + Math.round(progress * fpsRange);
            MenuUiSettings.getInstance().setGuiFpsLimit(fps);
        }
    }

    private float getPanoramaPresetRowY(ThemeSettingsLayout layout, int index) {
        return layout.presetSectionY + 12.0F * layout.scale + index * (layout.presetRowHeight + layout.presetRowGap);
    }

    private MenuUiSettings.PanoramaPreset getPanoramaPresetAt(ThemeSettingsLayout layout, float mouseX, float mouseY) {
        float rowWidth = layout.contentRight - layout.contentX;
        int index = 0;
        for (MenuUiSettings.PanoramaPreset preset : MenuUiSettings.PanoramaPreset.values()) {
            float rowY = getPanoramaPresetRowY(layout, index++);
            if (isPointInside(mouseX, mouseY, layout.contentX, rowY, rowWidth, layout.presetRowHeight)) {
                return preset;
            }
        }
        return null;
    }

    private MenuUiSettings.PanoramaPreset getThemeSelectorPresetAt(ThemeSelectorLayout layout, float mouseX, float mouseY) {
        List<MenuUiSettings.PanoramaPreset> visiblePresets = getVisibleThemeSelectorPresets();
        int index = 0;
        for (MenuUiSettings.PanoramaPreset preset : visiblePresets) {
            float cardX = getThemeSelectorCardX(layout, index++, visiblePresets.size());
            if (isPointInside(mouseX, mouseY, cardX, layout.cardsY, layout.cardPreviewW, layout.cardTotalHeight)) {
                return preset;
            }
        }
        return null;
    }

    private static boolean isPointInside(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        init(client, width, height);
    }

    @Override
    public void removed() {
        super.removed();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        updateOverlayMetrics();
        float overlayMouseX = toOverlayCoordinate(mouseX);
        float overlayMouseY = toOverlayCoordinate(mouseY);

        if (themeSelectorOpen) {
            if (themeSelectorButton != null && isPointInside(
                    overlayMouseX,
                    overlayMouseY,
                    themeSelectorButton.getX(),
                    themeSelectorButton.getY(),
                    themeSelectorButton.getWidth(),
                    themeSelectorButton.getHeight()
            )) {
                return super.mouseClicked(overlayMouseX, overlayMouseY, button);
            }

            ThemeSelectorLayout layout = getThemeSelectorLayout();
            if (button == 0) {
                if (themeSelectorSettingsOpen) {
                    ThemeSettingsLayout settingsLayout = getThemeSettingsLayout(layout);
                    if (isPointInside(overlayMouseX, overlayMouseY, settingsLayout.closeX, settingsLayout.closeY, settingsLayout.closeSize, settingsLayout.closeSize)
                            || !isPointInside(overlayMouseX, overlayMouseY, settingsLayout.panelX, settingsLayout.panelY, settingsLayout.panelW, settingsLayout.panelH)) {
                        setThemeSelectorSettingsOpen(false);
                        return true;
                    }

                    if (isPointInside(overlayMouseX, overlayMouseY, settingsLayout.panoramaTrackX, settingsLayout.panoramaTrackY - 4.0F * settingsLayout.scale, settingsLayout.trackWidth, settingsLayout.trackHeight + 8.0F * settingsLayout.scale)) {
                        activeThemeSettingsSlider = ThemeSettingsSlider.PANORAMA_SPEED;
                        updateThemeSettingsSlider(activeThemeSettingsSlider, settingsLayout, overlayMouseX);
                        return true;
                    }

                    if (isPointInside(overlayMouseX, overlayMouseY, settingsLayout.fpsTrackX, settingsLayout.fpsTrackY - 4.0F * settingsLayout.scale, settingsLayout.trackWidth, settingsLayout.trackHeight + 8.0F * settingsLayout.scale)) {
                        activeThemeSettingsSlider = ThemeSettingsSlider.GUI_FPS_LIMIT;
                        updateThemeSettingsSlider(activeThemeSettingsSlider, settingsLayout, overlayMouseX);
                        return true;
                    }

                    MenuUiSettings.PanoramaPreset preset = getPanoramaPresetAt(settingsLayout, overlayMouseX, overlayMouseY);
                    if (preset != null) {
                        MenuUiSettings.getInstance().setSelectedPanoramaPreset(preset);
                        syncDisplayedPanoramaName();
                        return true;
                    }

                    return true;
                }

                if (isPointInside(overlayMouseX, overlayMouseY, layout.settingsX, layout.settingsY, layout.settingsSize, layout.settingsSize)) {
                    setThemeSelectorSettingsOpen(true);
                    return true;
                }

                themeSelectorSearchFocused = isPointInside(overlayMouseX, overlayMouseY, layout.searchX, layout.searchY, layout.searchW, layout.searchH);
                if (themeSelectorSearchFocused) {
                    return true;
                }

                if (isPointInside(overlayMouseX, overlayMouseY, layout.closeX, layout.closeY, layout.closeSize, layout.closeSize)
                        || !isPointInside(overlayMouseX, overlayMouseY, layout.panelX, layout.panelY, layout.panelW, layout.panelH)) {
                    setThemeSelectorOpen(false);
                    return true;
                }

                if (isPointInside(overlayMouseX, overlayMouseY, layout.resetX, layout.resetY, layout.resetW, layout.resetH)) {
                    MenuUiSettings.getInstance().setSelectedPanoramaPreset(MenuUiSettings.PanoramaPreset.VANILLA);
                    syncDisplayedPanoramaName();
                    return true;
                }

                MenuUiSettings.PanoramaPreset preset = getThemeSelectorPresetAt(layout, overlayMouseX, overlayMouseY);
                if (preset != null) {
                    MenuUiSettings.getInstance().setSelectedPanoramaPreset(preset);
                    syncDisplayedPanoramaName();
                    return true;
                }
            }
            return true;
        }

        return super.mouseClicked(overlayMouseX, overlayMouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        updateOverlayMetrics();
        if (themeSelectorOpen) {
            activeThemeSettingsSlider = ThemeSettingsSlider.NONE;
            return true;
        }
        return super.mouseReleased(toOverlayCoordinate(mouseX), toOverlayCoordinate(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        updateOverlayMetrics();
        if (themeSelectorOpen) {
            if (themeSelectorSettingsOpen && button == 0 && activeThemeSettingsSlider != ThemeSettingsSlider.NONE) {
                updateThemeSettingsSlider(activeThemeSettingsSlider, getThemeSettingsLayout(getThemeSelectorLayout()), toOverlayCoordinate(mouseX));
            }
            return true;
        }
        return super.mouseDragged(
                toOverlayCoordinate(mouseX),
                toOverlayCoordinate(mouseY),
                button,
                toOverlayCoordinate(deltaX),
                toOverlayCoordinate(deltaY)
        );
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        updateOverlayMetrics();
        if (themeSelectorOpen) {
            return true;
        }
        return super.mouseScrolled(
                toOverlayCoordinate(mouseX),
                toOverlayCoordinate(mouseY),
                horizontalAmount,
                verticalAmount
        );
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (themeSelectorOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (themeSelectorSettingsOpen) {
                    setThemeSelectorSettingsOpen(false);
                    return true;
                }
                setThemeSelectorOpen(false);
                return true;
            }

            if (!themeSelectorSettingsOpen && themeSelectorSearchFocused) {
                if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_A) {
                    themeSelectorSearchQuery = "";
                    return true;
                }
                if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
                    String clipboard = this.client != null ? this.client.keyboard.getClipboard() : "";
                    if (clipboard != null && !clipboard.isEmpty()) {
                        themeSelectorSearchQuery = sanitizeThemeSelectorSearch(themeSelectorSearchQuery + clipboard);
                    }
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !themeSelectorSearchQuery.isEmpty()) {
                    themeSelectorSearchQuery = themeSelectorSearchQuery.substring(0, themeSelectorSearchQuery.length() - 1);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_DELETE && !themeSelectorSearchQuery.isEmpty()) {
                    themeSelectorSearchQuery = "";
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (themeSelectorOpen && !themeSelectorSettingsOpen && themeSelectorSearchFocused && !Character.isISOControl(chr)) {
            themeSelectorSearchQuery = sanitizeThemeSelectorSearch(themeSelectorSearchQuery + chr);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private void renderThemeSelectorSearchText(DrawContext context, ThemeSelectorLayout layout) {
        String displayText = themeSelectorSearchQuery.isEmpty() && !themeSelectorSearchFocused ? "Search..." : themeSelectorSearchQuery;
        float textSize = 8.7F * layout.scale;
        int textColor = themeSelectorSearchQuery.isEmpty() && !themeSelectorSearchFocused ? 0xFF9099AD : 0xFFE6ECFA;
        float textX = layout.searchX + 12.0F * layout.scale;
        float textY = MenuStyle.centerMsdfTextY(textSize, layout.searchY, layout.searchH);

        MsdfRenderer.renderText(
                MsdfFonts.medium(),
                displayText,
                textSize,
                textColor,
                context.getMatrices().peek().getPositionMatrix(),
                textX,
                textY,
                0.0F
        );

        if (themeSelectorSearchFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float caretX = textX + MsdfFonts.medium().getWidth(themeSelectorSearchQuery, textSize) + 1.5F * layout.scale;
            float caretY = layout.searchY + 6.0F * layout.scale;
            float caretH = layout.searchH - 12.0F * layout.scale;
            rectangle.render(ShapeProperties.create(context.getMatrices(), caretX, caretY, Math.max(1.0F, 1.1F * layout.scale), Math.max(5.0F, caretH))
                    .round(1.0F)
                    .softness(1.0F)
                    .color(0xFFD8E2F8)
                    .build());
        }
    }

    private List<MenuUiSettings.PanoramaPreset> getVisibleThemeSelectorPresets() {
        String query = themeSelectorSearchQuery.trim().toLowerCase(Locale.ROOT);
        List<MenuUiSettings.PanoramaPreset> presets = new ArrayList<>();
        for (MenuUiSettings.PanoramaPreset preset : MenuUiSettings.PanoramaPreset.values()) {
            if (query.isEmpty() || preset.displayName().toLowerCase(Locale.ROOT).contains(query)) {
                presets.add(preset);
            }
        }
        return presets;
    }

    private String sanitizeThemeSelectorSearch(String text) {
        StringBuilder builder = new StringBuilder();
        String raw = text == null ? "" : text;
        for (int i = 0; i < raw.length(); i++) {
            char chr = raw.charAt(i);
            if (!Character.isISOControl(chr) && builder.length() < 28) {
                builder.append(chr);
            }
        }
        return builder.toString();
    }

    private static class MainMenuButtonWidget extends ButtonWidget {
        private final Identifier leftIcon;
        private final boolean dangerStyle;
        private float hoverAnim = 0.0F;
        private long lastFrameTimeNs = -1L;

        MainMenuButtonWidget(int x, int y, int width, int height, Text message, Identifier leftIcon, PressAction onPress) {
            this(x, y, width, height, message, leftIcon, false, onPress);
        }

        MainMenuButtonWidget(int x, int y, int width, int height, Text message, Identifier leftIcon, boolean dangerStyle, PressAction onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
            this.leftIcon = leftIcon;
            this.dangerStyle = dangerStyle;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            boolean hovered = this.isHovered();
            boolean flatThemeSelectorStyle = leftIcon != null && leftIcon.equals(ICON_PAINTBRUSH) && this.getMessage().getString().isEmpty();
            float target = hovered ? 1.0F : 0.0F;
            long nowNs = System.nanoTime();
            float dt = lastFrameTimeNs > 0L ? Math.min(0.05F, (nowNs - lastFrameTimeNs) / 1_000_000_000.0F) : (1.0F / 60.0F);
            lastFrameTimeNs = nowNs;
            float smoothing = 1.0F - (float) Math.exp(-dt * 14.0F);
            hoverAnim += (target - hoverAnim) * smoothing;
            if (Math.abs(target - hoverAnim) < 0.01F) {
                hoverAnim = target;
            }

            int outline = flatThemeSelectorStyle
                    ? lerpArgb(0xFF0A1020, 0xFF101828, hoverAnim)
                    : dangerStyle
                    ? lerpArgb(0xFF7A2A36, 0xFF8A3341, hoverAnim)
                    : 0xFF27324A;
            int fill = flatThemeSelectorStyle
                    ? lerpArgb(0xFF090F1C, 0xFF101827, hoverAnim)
                    : dangerStyle
                    ? lerpArgb(0xFF2A0C13, 0xFF351018, hoverAnim)
                    : 0xFF090F1C;
            int hoverOverlayBase = dangerStyle ? 0xC14A61 : 0xFFFFFF;
            int hoverOverlayAlpha = dangerStyle ? (int) (hoverAnim * 14.0F) : (flatThemeSelectorStyle ? (int) (hoverAnim * 10.0F) : (int) (hoverAnim * 26.0F));
            int hoverOverlay = (hoverOverlayAlpha << 24) | hoverOverlayBase;

            MainMenuScreen screen = (MainMenuScreen) MinecraftClient.getInstance().currentScreen;
            if (screen != null) {
                screen.rectangle.render(ShapeProperties.create(
                                context.getMatrices(),
                                this.getX(),
                                this.getY(),
                                this.width,
                                this.height
                        )
                        .round(7.0F / 1.3F)
                        .softness(1.2F)
                        .thickness(flatThemeSelectorStyle ? 1.0F : 1.8F)
                        .outlineColor(outline)
                        .color(fill)
                        .build());
                if (hoverAnim > 0.001F) {
                    screen.rectangle.render(ShapeProperties.create(
                                    context.getMatrices(),
                                    this.getX(),
                                    this.getY(),
                                    this.width,
                                    this.height
                            )
                            .round(7.0F / 1.3F)
                            .softness(1.0F)
                            .color(hoverOverlay)
                            .build());
                }
            }

            String label = this.getMessage().getString();
            float size = (this.width <= 32 ? 8.6F : 10.6F) / 1.3F;
            int textColor = hovered ? 0xFFFFFFFF : 0xFFF4F7FF;
            float textX = MenuStyle.centerMsdfTextX(MsdfFonts.bold(), label, size, this.getX(), this.width);
            if (leftIcon != null) {
                float iconSize = this.width <= 32 ? 8.0F : 10.0F;
                float iconW = iconSize * getIconAspectRatio(leftIcon);
                float iconH = iconSize;
                boolean hasLabel = !label.isEmpty();
                float gap = hasLabel ? 6.0F : 0.0F;
                float totalW = iconW + gap + (hasLabel ? MsdfFonts.bold().getWidth(label, size) : 0.0F);
                float left = this.getX() + (this.width - totalW) / 2.0F;
                float iconX = hasLabel ? left : this.getX() + (this.width - iconW) / 2.0F;
                float iconY = this.getY() + (this.height - iconH) / 2.0F;
                renderMenuIcon(context, leftIcon, iconX, iconY, iconW, iconH);
                textX = left + iconW + gap;
            }
            if (!label.isEmpty()) {
                MsdfRenderer.renderText(
                        MsdfFonts.bold(),
                        label,
                        size,
                        textColor,
                        context.getMatrices().peek().getPositionMatrix(),
                        textX,
                        MenuStyle.centerMsdfTextY(size, this.getY(), this.height),
                        0.0F
                );
            }
        }
    }

    private static class ThemeSelectorLayout {
        private final float scale;
        private final float overlayWidth;
        private final float overlayHeight;
        private final float panelX;
        private final float panelY;
        private final float panelW;
        private final float panelH;
        private final float settingsX;
        private final float settingsY;
        private final float settingsSize;
        private final float closeX;
        private final float closeY;
        private final float closeSize;
        private final float searchX;
        private final float searchY;
        private final float searchW;
        private final float searchH;
        private final float sectionLabelY;
        private final float cardsX;
        private final float cardsY;
        private final float cardsGap;
        private final float cardPreviewW;
        private final float cardPreviewH;
        private final float cardTotalHeight;
        private final float footerY;
        private final float resetX;
        private final float resetY;
        private final float resetW;
        private final float resetH;

        private ThemeSelectorLayout(
                float scale,
                float overlayWidth,
                float overlayHeight,
                float panelX,
                float panelY,
                float panelW,
                float panelH,
                float settingsX,
                float settingsY,
                float settingsSize,
                float closeX,
                float closeY,
                float closeSize,
                float searchX,
                float searchY,
                float searchW,
                float searchH,
                float sectionLabelY,
                float cardsX,
                float cardsY,
                float cardsGap,
                float cardPreviewW,
                float cardPreviewH,
                float cardTotalHeight,
                float footerY,
                float resetX,
                float resetY,
                float resetW,
                float resetH
        ) {
            this.scale = scale;
            this.overlayWidth = overlayWidth;
            this.overlayHeight = overlayHeight;
            this.panelX = panelX;
            this.panelY = panelY;
            this.panelW = panelW;
            this.panelH = panelH;
            this.settingsX = settingsX;
            this.settingsY = settingsY;
            this.settingsSize = settingsSize;
            this.closeX = closeX;
            this.closeY = closeY;
            this.closeSize = closeSize;
            this.searchX = searchX;
            this.searchY = searchY;
            this.searchW = searchW;
            this.searchH = searchH;
            this.sectionLabelY = sectionLabelY;
            this.cardsX = cardsX;
            this.cardsY = cardsY;
            this.cardsGap = cardsGap;
            this.cardPreviewW = cardPreviewW;
            this.cardPreviewH = cardPreviewH;
            this.cardTotalHeight = cardTotalHeight;
            this.footerY = footerY;
            this.resetX = resetX;
            this.resetY = resetY;
            this.resetW = resetW;
            this.resetH = resetH;
        }
    }

    private static class ThemeSettingsLayout {
        private final float scale;
        private final float panelX;
        private final float panelY;
        private final float panelW;
        private final float panelH;
        private final float closeX;
        private final float closeY;
        private final float closeSize;
        private final float contentX;
        private final float contentRight;
        private final float trackWidth;
        private final float trackHeight;
        private final float panoramaLabelY;
        private final float panoramaTrackX;
        private final float panoramaTrackY;
        private final float panoramaValueX;
        private final float fpsLabelY;
        private final float fpsTrackX;
        private final float fpsTrackY;
        private final float fpsValueX;
        private final float presetSectionY;
        private final float presetRowHeight;
        private final float presetRowGap;

        private ThemeSettingsLayout(
                float scale,
                float panelX,
                float panelY,
                float panelW,
                float panelH,
                float closeX,
                float closeY,
                float closeSize,
                float contentX,
                float contentRight,
                float trackWidth,
                float trackHeight,
                float panoramaLabelY,
                float panoramaTrackX,
                float panoramaTrackY,
                float panoramaValueX,
                float fpsLabelY,
                float fpsTrackX,
                float fpsTrackY,
                float fpsValueX,
                float presetSectionY,
                float presetRowHeight,
                float presetRowGap
        ) {
            this.scale = scale;
            this.panelX = panelX;
            this.panelY = panelY;
            this.panelW = panelW;
            this.panelH = panelH;
            this.closeX = closeX;
            this.closeY = closeY;
            this.closeSize = closeSize;
            this.contentX = contentX;
            this.contentRight = contentRight;
            this.trackWidth = trackWidth;
            this.trackHeight = trackHeight;
            this.panoramaLabelY = panoramaLabelY;
            this.panoramaTrackX = panoramaTrackX;
            this.panoramaTrackY = panoramaTrackY;
            this.panoramaValueX = panoramaValueX;
            this.fpsLabelY = fpsLabelY;
            this.fpsTrackX = fpsTrackX;
            this.fpsTrackY = fpsTrackY;
            this.fpsValueX = fpsValueX;
            this.presetSectionY = presetSectionY;
            this.presetRowHeight = presetRowHeight;
            this.presetRowGap = presetRowGap;
        }
    }

    private enum ThemeSettingsSlider {
        NONE,
        PANORAMA_SPEED,
        GUI_FPS_LIMIT
    }
}
