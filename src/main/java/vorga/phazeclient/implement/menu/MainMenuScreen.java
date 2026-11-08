package vorga.phazeclient.implement.menu;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.realms.gui.screen.RealmsMainScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.util.Window;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.system.font.msdf.MsdfFont;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.api.system.shape.implement.Rectangle;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.base.util.RemoteRulesService;
import vorga.phazeclient.base.util.render.Render2DUtil;
import vorga.phazeclient.implement.features.modules.client.Theme;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.implement.settings.SettingLabel;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainMenuScreen extends TitleScreen {
    private static final double PREFERRED_OVERLAY_SCALE = 2.0D;
    private static final double MIN_OVERLAY_SCALE = 1.0D;
    private static final int DEFAULT_OVERLAY_WIDTH = 462;
    private static final int DEFAULT_OVERLAY_HEIGHT = 300;
    private static final String FOOTER_LEFT_TEXT = "Phaze Client 1.21.4 (v1.2)";
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
    private static final Identifier ICON_REPLAY_MOD = Identifier.of("phaze", "textures/menu/replay_mod.png");
    private static final Identifier ICON_ACCOUNT_SWITCHER = Identifier.of("phaze", "textures/menu/account_switcher.png");
    private static final Identifier ICON_PAINTBRUSH = Identifier.of("phaze", "textures/menu/paintbrush.png");
    private static final Identifier ICON_VANILLA_MAIN_MENU = Identifier.of("phaze", "textures/menu/vanilla_main_menu.png");
    private static final Identifier ICON_CROSS = Identifier.of("phaze", "textures/menu/cross.png");
    private static final Identifier ICON_DOWNLOAD = Identifier.of("phaze", "textures/menu/arrow_download.png");
    private static final Identifier ICON_RESET = Identifier.ofVanilla("textures/reset.png");
    /** MSDF text sits on a baseline at y + 0.7 em and its caps/ascenders reach ~0.70 em above it. */
    private static final float MSDF_CAP_HEIGHT = 0.70F;
    /** One spacing unit for every edge and block gap inside the theme modals. */
    private static final float THEME_MODAL_GAP = 12.0F;
    /** Unscaled outline drawn by renderThemeModalPanel; gaps are measured from its inner edge. */
    private static final float THEME_MODAL_BORDER = 1.0F;
    private static final float THEME_TITLE_TEXT_SIZE = 14.0F;
    private static final float THEME_SECTION_TEXT_SIZE = 9.4F;
    private static final float THEME_HINT_TEXT_SIZE = 7.0F;
    private static final float THEME_FOOTER_TEXT_SIZE = 13.8F / 1.2F;
    private static final float THEME_RESET_ICON_SIZE = 11.0F * 1.6F;
    private static final float THEME_RESET_ICON_GAP = 4.0F;
    private static final float THEME_ACTION_SIZE = 18.0F;
    private static final float THEME_SETTINGS_ICON_SIZE = 13.0F;
    private static final float THEME_CLOSE_ICON_SIZE = (13.0F / (1.2F * 1.1F)) * (2.5F / 1.2F) / 1.15F;
    private static final float THEME_CARD_NAME_OFFSET = 6.0F;
    private static final float THEME_CARD_NAME_SIZE = 8.6F;
    private static final float THEME_CARD_BADGE_SIZE = 11.0F;
    private static final float THEME_CARD_INSET = 6.0F;
    private static final float THEME_SLIDER_TITLE_SIZE = 9.8F;
    private static final float THEME_SLIDER_VALUE_SIZE = 9.2F;
    private static final float THEME_SLIDER_SUBTITLE_SIZE = 7.9F;
    private static final float THEME_SLIDER_TRACK_HEIGHT = 7.0F;
    private static final float THEME_SLIDER_INNER_GAP = 8.0F;
    private static final float TOOLTIP_PADDING = 8.0F;
    private static final float TOOLTIP_LINE_GAP = 7.0F;
    private static final float TOOLTIP_TITLE_SIZE = 9.6F;
    private static final float TOOLTIP_LINE_SIZE = 8.6F;
    private static final float TOOLTIP_ICON_GAP = 4.0F;
    private static final int TOOLTIP_TEXT_COLOR = 0xFF9EA7BB;
    private static final int ICON_TINT_COLOR = 0xFF9EA7BB;
    private static final int ACCOUNT_SWITCHER_NAME_COLOR = 0xFFCC8888;
    private static final int THEME_MODAL_DIM_COLOR = 0x96000000;
    private final Rectangle rectangle = new Rectangle();
    private final Vector3f scissorMin = new Vector3f();
    private final Vector3f scissorMax = new Vector3f();
    private MainMenuButtonWidget singleplayerButton;
    private MainMenuButtonWidget multiplayerButton;
    private MainMenuButtonWidget quitButton;
    private MainMenuButtonWidget modMenuButton;
    private MainMenuButtonWidget settingsButton;
    private MainMenuButtonWidget realmsButton;
    private MainMenuButtonWidget flashbackButton;
    private MainMenuButtonWidget replayModButton;
    private MainMenuButtonWidget accountSwitcherButton;
    private MainMenuButtonWidget vanillaMainMenuButton;
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
    private float themeSelectorResetHoverAnim = 0.0F;
    private float themeSelectorOpenAnim = 0.0F;
    private float themeSelectorSettingsOpenAnim = 0.0F;
    private float panoramaSliderVisualProgress = (float) (MenuUiSettings.getInstance().getPanoramaSpeed() / 100.0D);
    private float guiFpsSliderVisualProgress = (MenuUiSettings.getInstance().getGuiFpsLimit() - MenuUiSettings.MIN_GUI_FPS_LIMIT)
            / (float) (MenuUiSettings.MAX_GUI_FPS_LIMIT - MenuUiSettings.MIN_GUI_FPS_LIMIT);
    private float guiScaleSliderVisualProgress = (MenuUiSettings.getInstance().getGuiScale() - MenuUiSettings.MIN_GUI_SCALE)
            / (MenuUiSettings.MAX_GUI_SCALE - MenuUiSettings.MIN_GUI_SCALE);
    private long themeUiLastFrameNs = -1L;
    private float themeUiSmoothing = 1.0F;
    private float themeModalSmoothing = 1.0F;
    private ThemeSettingsSlider activeThemeSettingsSlider = ThemeSettingsSlider.NONE;
    private float themeSelectorCardScrollOffset = 0.0F;
    private float themeSelectorCardScrollTarget = 0.0F;
    private float themeSelectorCardMaxScroll = 0.0F;
    private String lastMenuLocale = Lang.getActive();
    private final Map<String, Float> themeCardHoverAnims = new HashMap<>();
    private final Map<String, Float> themeCardDeleteHoverAnims = new HashMap<>();
    private final Map<String, Float> themeCardDownloadProgressAnims = new HashMap<>();
    private final Map<String, Float> themeSettingsPresetHoverAnims = new HashMap<>();
    private final Map<String, SettingLabel> themeCardNameLabels = new HashMap<>();
    private DrawContext themeCardClipContext;
    private float themeCardClipX;
    private float themeCardClipY;
    private float themeCardClipW;
    private float themeCardClipH;
    /** Card names clip to their own box intersected with the scrolling card viewport. */
    private final SettingLabel.Clip themeCardNameClip = new SettingLabel.Clip() {
        @Override
        public void push(Matrix4f matrix, float x, float y, float width, float height) {
            if (themeCardClipContext == null) return;
            float left = Math.max(x, themeCardClipX);
            float top = Math.max(y, themeCardClipY);
            float right = Math.min(x + width, themeCardClipX + themeCardClipW);
            float bottom = Math.min(y + height, themeCardClipY + themeCardClipH);
            enableOverlayScissor(themeCardClipContext, left, top, Math.max(0.0F, right - left), Math.max(0.0F, bottom - top));
        }

        @Override
        public void pop() {
            if (themeCardClipContext == null) return;
            enableOverlayScissor(themeCardClipContext, themeCardClipX, themeCardClipY, themeCardClipW, themeCardClipH);
        }
    };
    private Boolean originalIasTitleTextEnabled;
    private Boolean originalIasTitleButtonEnabled;
    private boolean iasMainMenuOverrideApplied;
    public MainMenuScreen() {
        super(false);
    }

    public static boolean isCustomMainMenuEnabled() {
        return MenuUiSettings.getInstance().isCustomMainMenuEnabled();
    }

    public static void setCustomMainMenuEnabled(boolean enabled) {
        MenuUiSettings.getInstance().setCustomMainMenuEnabled(enabled);
    }

    @Override
    protected void init() {
        Theme.getInstance().syncLanguage();
        updateOverlayMetrics();
        clearChildren();
        syncDisplayedPanoramaName();
        lastMenuLocale = Lang.getActive();
        if (this.client != null) {
            MenuUiSettings.getInstance().getSelectedPanoramaPreset().getRenderer().prepareTextures(this.client);
            UiMsdfIconAtlas.warmup();
        }
        float scale = phaze$menuScale();
        float sizeMul = 1.3F / 1.2F;
        int overlayW = getOverlayViewportWidth();
        int overlayH = getOverlayViewportHeight();
        int width = Math.max(96, Math.round(320.0F * scale * sizeMul));
        int height = Math.max(12, Math.round(36.0F * scale * sizeMul));
        int gap = Math.max(3, Math.round(8.0F * scale));
        int x = overlayW / 2 - width / 2;
        int y = overlayH / 2 - Math.round(72.0F * scale);

        singleplayerButton = new MainMenuButtonWidget(x, y, width, height, Text.literal(Lang.translate("Singleplayer")), ICON_SINGLE,
                b -> this.client.setScreen(new SelectWorldScreen(this)));
        addDrawableChild(singleplayerButton);
        y += height + gap;
        multiplayerButton = new MainMenuButtonWidget(x, y, width, height, Text.literal(Lang.translate("Multiplayer")), ICON_MULTI,
                b -> this.client.setScreen(new MultiplayerScreen(this)));
        addDrawableChild(multiplayerButton);
        y += height + gap;
        quitButton = new MainMenuButtonWidget(x, y, width, height, Text.literal(Lang.translate("Quit")), ICON_LOGOUT, true,
                b -> this.client.scheduleStop());
        addDrawableChild(quitButton);

        int size = Math.max(12, Math.round(30.0F * scale * sizeMul));
        int by = overlayH - Math.round(50.0F * scale);
        int dockGap = Math.max(6, Math.round(8.0F * scale));
        int dockStep = size + dockGap;
        boolean modMenuLoaded = FabricLoader.getInstance().isModLoaded("modmenu");
        boolean flashbackLoaded = FabricLoader.getInstance().isModLoaded("flashback");
        boolean replayModLoaded = FabricLoader.getInstance().isModLoaded("replaymod");
        boolean accountSwitcherLoaded = FabricLoader.getInstance().isModLoaded("ias");
        int dockButtonCount = 2
                + (flashbackLoaded ? 1 : 0)
                + (replayModLoaded ? 1 : 0)
                + (accountSwitcherLoaded ? 1 : 0)
                + (modMenuLoaded ? 1 : 0);
        int dockTotalWidth = dockButtonCount * size + Math.max(0, dockButtonCount - 1) * dockGap;
        int currentDockX = overlayW / 2 - dockTotalWidth / 2;

        settingsButton = new MainMenuButtonWidget(
                currentDockX,
                by,
                size,
                size,
                Text.literal(""),
                ICON_SETTINGS,
                1.7F,
                b -> this.client.setScreen(new OptionsScreen(this, this.client.options))
        );
        addDrawableChild(settingsButton);
        currentDockX += dockStep;

        realmsButton = new MainMenuButtonWidget(
                currentDockX,
                by,
                size,
                size,
                Text.literal(""),
                ICON_REALMS,
                1.7F,
                b -> openRealms()
        );
        addDrawableChild(realmsButton);
        currentDockX += dockStep;

        flashbackButton = null;
        if (flashbackLoaded) {
            flashbackButton = new MainMenuButtonWidget(
                    currentDockX, by, size, size, Text.literal(""), ICON_FLASHBACK, 1.7F, b -> openFlashbackReplays()
            );
            addDrawableChild(flashbackButton);
            currentDockX += dockStep;
        }

        replayModButton = null;
        if (replayModLoaded) {
            replayModButton = new MainMenuButtonWidget(
                    currentDockX, by, size, size, Text.literal(""), ICON_REPLAY_MOD, 1.7F, b -> openReplayModViewer()
            );
            addDrawableChild(replayModButton);
            currentDockX += dockStep;
        }

        accountSwitcherButton = null;
        if (accountSwitcherLoaded) {
            accountSwitcherButton = new MainMenuButtonWidget(
                    currentDockX, by, size, size, Text.literal(""), ICON_ACCOUNT_SWITCHER, 2.84F, b -> openAccountSwitcher()
            );
            addDrawableChild(accountSwitcherButton);
            currentDockX += dockStep;
        }

        modMenuButton = null;
        if (modMenuLoaded) {
            modMenuButton = new MainMenuButtonWidget(
                    currentDockX, by, size, size, Text.literal(""), ICON_TAB, 1.7F, b -> openModMenu()
            );
            addDrawableChild(modMenuButton);
        }

        int topInset = screenMargin();
        vanillaMainMenuButton = new MainMenuButtonWidget(
                overlayW - topInset,
                topInset,
                size,
                size,
                Text.literal(""),
                ICON_VANILLA_MAIN_MENU,
                MainMenuButtonWidget.ButtonVisualStyle.TOP_BAR_ICON,
                2.96F,
                b -> switchToVanillaMainMenu()
        );
        addDrawableChild(vanillaMainMenuButton);
        themeSelectorButton = new MainMenuButtonWidget(
                overlayW - topInset - size,
                topInset,
                size,
                size,
                Text.literal(""),
                ICON_PAINTBRUSH,
                MainMenuButtonWidget.ButtonVisualStyle.TOP_BAR_ICON,
                1.7F,
                b -> setThemeSelectorOpen(!themeSelectorOpen)
        );
        addDrawableChild(themeSelectorButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Theme.getInstance().syncLanguage();
        refreshLocalizedMainMenuTexts();
        updateOverlayMetrics();
        applyIasMainMenuOverrides();
        suppressExternalMainMenuButtons();
        stabilizeMainMenuButtonLayout();
        syncDisplayedPanoramaName();
        updateThemeUiAnimationTiming();
        renderMainMenuBackground(context, delta);
        context.getMatrices().push();
        context.getMatrices().scale(overlayRenderScale, overlayRenderScale, 1.0F);
        BatchedRectangle.beginScope();
        try {
            renderOverlay(context, mouseX, mouseY, delta);
        } finally {
            BatchedRectangle.endScope();
            context.getMatrices().pop();
        }
    }

    private void renderOverlay(DrawContext context, int mouseX, int mouseY, float delta) {
        int overlayMouseX = Math.round(toOverlayCoordinate(mouseX));
        int overlayMouseY = Math.round(toOverlayCoordinate(mouseY));
        float overlayW = getOverlayViewportWidth();
        float overlayH = getOverlayViewportHeight();
        renderPhazeOnlineCounter(context);
        if (themeSelectorOpen) {
            themeSelectorOpenAnim = animateThemeModalValue(themeSelectorOpenAnim, 1.0F);
            float selectorModalProgress = easeOutThemeModal(themeSelectorOpenAnim);
            renderThemeSelectorBackdrop(context, overlayW, overlayH, selectorModalProgress);
            renderThemeSelectorModal(context, overlayMouseX, overlayMouseY, mouseX, mouseY, delta, selectorModalProgress);
        } else {
            themeSelectorOpenAnim = 0.0F;
            themeSelectorSettingsOpenAnim = 0.0F;
            renderMainMenuWidgets(context, overlayMouseX, overlayMouseY, delta);
            renderAccountSwitcherCurrentUser(context);
            renderFooterTexts(context, overlayW, overlayH);
            renderSettingsTooltip(context);
            renderRealmsTooltip(context);
            renderFlashbackTooltip(context);
            renderReplayModTooltip(context);
            renderAccountSwitcherTooltip(context);
            renderModMenuTooltip(context, overlayMouseX, overlayMouseY);
            renderThemeSelectorTooltip(context);
        }
    }

    private void refreshLocalizedMainMenuTexts() {
        String activeLocale = Lang.getActive();
        if (activeLocale.equals(lastMenuLocale)) {
            return;
        }
        lastMenuLocale = activeLocale;
        if (singleplayerButton != null) {
            singleplayerButton.setMessage(Text.literal(Lang.translate("Singleplayer")));
        }
        if (multiplayerButton != null) {
            multiplayerButton.setMessage(Text.literal(Lang.translate("Multiplayer")));
        }
        if (quitButton != null) {
            quitButton.setMessage(Text.literal(Lang.translate("Quit")));
        }
    }

    private void stabilizeMainMenuButtonLayout() {
        float scale = phaze$menuScale();
        float sizeMul = 1.3F / 1.2F;
        int overlayW = getOverlayViewportWidth();
        int overlayH = getOverlayViewportHeight();
        int gap = Math.max(3, Math.round(8.0F * scale));
        int mainWidth = Math.max(96, Math.round(320.0F * scale * sizeMul));
        int mainHeight = Math.max(12, Math.round(36.0F * scale * sizeMul));
        int mainY = overlayH / 2 - Math.round(72.0F * scale);
        int mainX = overlayW / 2 - mainWidth / 2;

        setButtonLayout(singleplayerButton, mainX, mainY, mainWidth, mainHeight);
        if (singleplayerButton != null) {
            mainY += singleplayerButton.getHeight() + gap;
        }
        setButtonLayout(multiplayerButton, mainX, mainY, mainWidth, mainHeight);
        if (multiplayerButton != null) {
            mainY += multiplayerButton.getHeight() + gap;
        }
        setButtonLayout(quitButton, mainX, mainY, mainWidth, mainHeight);

        int dockY = overlayH - Math.round(50.0F * scale);
        int dockGap = Math.max(6, Math.round(8.0F * scale));
        int dockSize = resolveDockButtonSize();
        int dockButtonCount = 2
                + (flashbackButton != null ? 1 : 0)
                + (replayModButton != null ? 1 : 0)
                + (accountSwitcherButton != null ? 1 : 0)
                + (modMenuButton != null ? 1 : 0);
        int dockTotalWidth = dockButtonCount * dockSize + Math.max(0, dockButtonCount - 1) * dockGap;
        int dockX = overlayW / 2 - dockTotalWidth / 2;

        setButtonLayout(settingsButton, dockX, dockY, dockSize, dockSize);
        dockX += dockSize + dockGap;
        setButtonLayout(realmsButton, dockX, dockY, dockSize, dockSize);
        dockX += dockSize + dockGap;
        if (flashbackButton != null) {
            setButtonLayout(flashbackButton, dockX, dockY, dockSize, dockSize);
            dockX += dockSize + dockGap;
        }
        if (replayModButton != null) {
            setButtonLayout(replayModButton, dockX, dockY, dockSize, dockSize);
            dockX += dockSize + dockGap;
        }
        if (accountSwitcherButton != null) {
            setButtonLayout(accountSwitcherButton, dockX, dockY, dockSize, dockSize);
            dockX += dockSize + dockGap;
        }
        setButtonLayout(modMenuButton, dockX, dockY, dockSize, dockSize);

        if (themeSelectorButton != null) {
            int topInset = screenMargin();
            setButtonLayout(
                    themeSelectorButton,
                    overlayW - topInset - dockSize * 2 - gap,
                    topInset,
                    dockSize,
                    dockSize
            );
            if (vanillaMainMenuButton != null) {
                setButtonLayout(
                        vanillaMainMenuButton,
                        overlayW - topInset - dockSize,
                        topInset,
                        dockSize,
                        dockSize
                );
            }
        }
    }

    private int resolveDockButtonSize() {
        return Math.max(12, Math.round(30.0F * phaze$menuScale() * (1.3F / 1.2F)));
    }

    private void setButtonLayout(MainMenuButtonWidget button, int x, int y, int width, int height) {
        if (button == null) {
            return;
        }
        button.phaze$setDimensions(width, height);
        if (button.getX() != x || button.getY() != y) {
            button.setPosition(x, y);
        }
    }

    private void renderMainMenuBackground(DrawContext context, float delta) {
        if (this.client != null && this.client.world != null) {
            this.renderInGameBackground(context);
            return;
        }
        this.renderPanoramaBackground(context, delta);
    }

    private void renderThemeSelectorBackdrop(DrawContext context, float overlayW, float overlayH, float alpha) {
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 120.0F);
        rectangle.render(ShapeProperties.create(context.getMatrices(), 0.0F, 0.0F, overlayW, overlayH)
                .softness(1.0F)
                .color(scaleColorAlpha(THEME_MODAL_DIM_COLOR, alpha))
                .build());
        context.getMatrices().pop();
    }

    private void renderMainMenuWidgets(DrawContext context, int mouseX, int mouseY, float delta) {
        for (var child : this.children()) {
            String className = child.getClass().getName();
            if (className.startsWith("com.replaymod.") || className.startsWith("ru.vidtu.ias.")) {
                continue;
            }
            if (child instanceof net.minecraft.client.gui.Drawable drawable) {
                drawable.render(context, mouseX, mouseY, delta);
            }
        }
    }

    private void renderPhazeOnlineCounter(DrawContext context) {
        int count = RemoteRulesService.getInstance().getOnlineCount();
        String text = count < 0
                ? "Phaze: connecting..."
                : "Phaze: " + count + " online";

        float textSize = 10.2F * phaze$menuScale();
        float margin = screenMargin();

        MsdfRenderer.renderText(
                MsdfFonts.medium(),
                text,
                textSize,
                0xFFFFFFFF,
                context.getMatrices().peek().getPositionMatrix(),
                textXForInkLeft(MsdfFonts.medium(), text, textSize, margin),
                margin,
                0.0F
        );
    }

    private void renderAccountSwitcherCurrentUser(DrawContext context) {
        if (accountSwitcherButton == null || this.client == null || this.client.getSession() == null || quitButton == null) {
            return;
        }

        String username = this.client.getSession().getUsername();
        if (username == null || username.isBlank()) {
            return;
        }

        float scale = phaze$menuScale();
        float textSize = 10.0F * scale;
        String accountText = "Current Account: " + username;
        float y = quitButton.getY() + quitButton.getHeight() + 12.0F * scale;
        float x = textXForInkCenter(MsdfFonts.medium(), accountText, textSize, quitButton.getX() + quitButton.getWidth() / 2.0F);

        MsdfRenderer.renderText(
                MsdfFonts.medium(),
                accountText,
                textSize,
                ACCOUNT_SWITCHER_NAME_COLOR,
                context.getMatrices().peek().getPositionMatrix(),
                x,
                y,
                0.0F
        );
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void renderPanoramaBackground(DrawContext context, float delta) {
        if (this.client == null) {
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

    private void switchToVanillaMainMenu() {
        if (this.client == null) {
            return;
        }
        setThemeSelectorOpen(false);
        setCustomMainMenuEnabled(false);
        this.client.setScreen(new TitleScreen(false));
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

    private void openReplayModViewer() {
        if (this.client == null) return;
        try {
            Class<?> replayClass = Class.forName("com.replaymod.replay.ReplayModReplay");
            Object replayInstance = replayClass.getField("instance").get(null);
            if (replayInstance == null) {
                return;
            }

            Class<?> viewerClass = Class.forName("com.replaymod.replay.gui.screen.GuiReplayViewer");
            Constructor<?> ctor = viewerClass.getConstructor(replayClass);
            Object viewer = ctor.newInstance(replayInstance);
            try {
                viewerClass.getMethod("display").invoke(viewer);
            } catch (Throwable displayFailed) {
                Screen screen = (Screen) viewerClass.getMethod("toMinecraft").invoke(viewer);
                this.client.setScreen(screen);
            }
        } catch (Throwable ignored) {
        }
    }

    private void openAccountSwitcher() {
        if (this.client == null) return;
        try {
            Class<?> screenClass = Class.forName("ru.vidtu.ias.screen.AccountScreen");
            Constructor<?> ctor = screenClass.getConstructor(Screen.class);
            Screen screen = (Screen) ctor.newInstance(this);
            this.client.setScreen(screen);
        } catch (Throwable ignored) {
        }
    }

    private void renderModMenuTooltip(DrawContext context, int mouseX, int mouseY) {
        renderExternalIntegrationTooltip(context, modMenuButton, Lang.translate("Fabric Mod Menu"));
    }

    private void renderSettingsTooltip(DrawContext context) {
        renderTwoLineTooltip(context, settingsButton, Lang.translate("Minecraft Settings"), ICON_CUBE, 9.0F * phaze$menuScale(), Lang.translate("Minecraft"));
    }

    private void renderRealmsTooltip(DrawContext context) {
        renderTwoLineTooltip(context, realmsButton, Lang.translate("Minecraft Realms"), ICON_CUBE, 9.0F * phaze$menuScale(), Lang.translate("Minecraft"));
    }

    private void renderFlashbackTooltip(DrawContext context) {
        renderExternalIntegrationTooltip(context, flashbackButton, Lang.translate("Flashback Mod"));
    }

    private void renderReplayModTooltip(DrawContext context) {
        renderExternalIntegrationTooltip(context, replayModButton, "Replay Mod");
    }

    private void renderAccountSwitcherTooltip(DrawContext context) {
        renderExternalIntegrationTooltip(context, accountSwitcherButton, "In-Game Account Switcher");
    }

    private void renderExternalIntegrationTooltip(DrawContext context, MainMenuButtonWidget button, String title) {
        renderTwoLineTooltip(context, button, title, ICON_EXTERNAL, (12.5F / (1.3F * 1.15F)) * phaze$menuScale(), Lang.translate("External"));
    }

    private void renderThemeSelectorTooltip(DrawContext context) {
        if (themeSelectorButton == null || !themeSelectorButton.isHovered()) {
            return;
        }
        float scale = phaze$menuScale();
        String title = Lang.translate("Theme Selector");
        float textSize = TOOLTIP_TITLE_SIZE * scale;
        // Height matches the button it sits beside; the side padding copies the resulting vertical padding.
        float h = themeSelectorButton.getHeight();
        float pad = (h - capHeight(textSize)) / 2.0F;
        float w = MsdfFonts.bold().getInkWidth(title, textSize) + pad * 2.0F;
        float x = Math.max(screenMargin(), themeSelectorButton.getX() - w - 6.0F * scale);
        float y = themeSelectorButton.getY();

        renderTooltipBox(context, x, y, w, h);
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                title,
                textSize,
                TOOLTIP_TEXT_COLOR,
                context.getMatrices().peek().getPositionMatrix(),
                textXForInkLeft(MsdfFonts.bold(), title, textSize, x + pad),
                y + pad,
                0.0F
        );
    }

    private void suppressExternalMainMenuButtons() {
        for (var child : this.children()) {
            if (child instanceof ClickableWidget widget) {
                String className = child.getClass().getName();
                String message = widget.getMessage() == null ? "" : widget.getMessage().getString();
                boolean externalWidget = className.startsWith("com.replaymod.")
                        || className.startsWith("ru.vidtu.ias.")
                        || "In-Game Account Switcher".equals(message);
                if (externalWidget) {
                    widget.visible = false;
                    widget.active = false;
                }
            }
        }
    }

    private void applyIasMainMenuOverrides() {
        if (iasMainMenuOverrideApplied || !FabricLoader.getInstance().isModLoaded("ias")) {
            return;
        }

        try {
            Class<?> configClass = Class.forName("ru.vidtu.ias.config.IASConfig");
            Field titleTextField = configClass.getField("titleText");
            Field titleButtonField = configClass.getField("titleButton");

            originalIasTitleTextEnabled = titleTextField.getBoolean(null);
            originalIasTitleButtonEnabled = titleButtonField.getBoolean(null);

            titleTextField.setBoolean(null, false);
            titleButtonField.setBoolean(null, false);
            iasMainMenuOverrideApplied = true;
        } catch (Throwable ignored) {
        }
    }

    private void restoreIasMainMenuOverrides() {
        if (!iasMainMenuOverrideApplied) {
            return;
        }

        try {
            Class<?> configClass = Class.forName("ru.vidtu.ias.config.IASConfig");
            if (originalIasTitleTextEnabled != null) {
                configClass.getField("titleText").setBoolean(null, originalIasTitleTextEnabled);
            }
            if (originalIasTitleButtonEnabled != null) {
                configClass.getField("titleButton").setBoolean(null, originalIasTitleButtonEnabled);
            }
        } catch (Throwable ignored) {
        } finally {
            iasMainMenuOverrideApplied = false;
            originalIasTitleTextEnabled = null;
            originalIasTitleButtonEnabled = null;
        }
    }

    private void renderThemeSelectorModal(DrawContext context, int mouseX, int mouseY, int rawMouseX, int rawMouseY, float delta, float modalProgress) {
        ThemeSelectorLayout layout = getThemeSelectorLayout();
        List<MenuUiSettings.PanoramaDescriptor> visiblePresets = getVisibleThemeSelectorPresets();
        boolean themeSelectorInteractive = !themeSelectorSettingsOpen;
        float modalAlpha = modalProgress;
        float modalYOffset = (1.0F - modalProgress) * 20.0F * layout.scale;
        syncDisplayedPanoramaName();
        if (themeSelectorSettingsOpen) {
            themeSelectorSettingsOpenAnim = animateThemeModalValue(themeSelectorSettingsOpenAnim, 1.0F);
        } else {
            themeSelectorSettingsOpenAnim = 0.0F;
        }
        float settingsModalProgress = easeOutThemeModal(themeSelectorSettingsOpenAnim);
        if (!themeSelectorInteractive) {
            themeSelectorSettingsHoverAnim = 0.0F;
            themeSelectorCloseHoverAnim = 0.0F;
            themeSelectorResetHoverAnim = 0.0F;
        }
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, modalYOffset, 140.0F);

        renderThemeModalPanel(context, layout.panelX, layout.panelY, layout.panelW, layout.panelH, menuRadius(10.0F), 0xFF11161F, 0xFF212838, modalAlpha);

        float titleTextSize = THEME_TITLE_TEXT_SIZE * layout.scale;
        String title = Lang.translate("Select Theme");
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                title,
                titleTextSize,
                scaleColorAlpha(0xFFF6F8FE, modalAlpha),
                context.getMatrices().peek().getPositionMatrix(),
                textXForInkLeft(MsdfFonts.bold(), title, titleTextSize, layout.contentX),
                layout.titleY,
                0.0F
        );

        boolean settingsHovered = themeSelectorInteractive && isPointInside(mouseX, mouseY, layout.settingsX, layout.settingsY, layout.settingsSize, layout.settingsSize);
        boolean closeHovered = themeSelectorInteractive && isPointInside(mouseX, mouseY, layout.closeX, layout.closeY, layout.closeSize, layout.closeSize);
        themeSelectorSettingsHoverAnim = animateThemeUiValue(themeSelectorSettingsHoverAnim, settingsHovered ? 1.0F : 0.0F);
        themeSelectorCloseHoverAnim = animateThemeUiValue(themeSelectorCloseHoverAnim, closeHovered ? 1.0F : 0.0F);
        renderIconInkCentered(context, ICON_SETTINGS, layout.settingsCenterX, layout.actionCenterY, THEME_SETTINGS_ICON_SIZE * layout.scale,
                scaleColorAlpha(lerpArgb(ICON_TINT_COLOR, 0xFFFFFFFF, themeSelectorSettingsHoverAnim), modalAlpha));
        renderThemeCloseIcon(context, layout.closeCenterX, layout.actionCenterY, layout.scale, themeSelectorCloseHoverAnim, modalAlpha);

        boolean searchHovered = themeSelectorInteractive && isPointInside(mouseX, mouseY, layout.searchX, layout.searchY, layout.searchW, layout.searchH);
        rectangle.render(ShapeProperties.create(context.getMatrices(), layout.searchX, layout.searchY, layout.searchW, layout.searchH)
                .round(menuRadius(6.0F))
                .softness(1.0F)
                .thickness(1.0F)
                .outlineColor(scaleColorAlpha(themeSelectorSearchFocused ? 0xFF4D617F : (searchHovered ? 0xFF364256 : 0xFF2B3343), modalAlpha))
                .color(scaleColorAlpha(0xFF232734, modalAlpha))
                .build());

        renderThemeSelectorSearchText(context, layout, modalAlpha);

        float sectionTextSize = THEME_SECTION_TEXT_SIZE * layout.scale;
        String sectionLabel = Lang.translate("Themes") + " (" + visiblePresets.size() + ")";
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                sectionLabel,
                sectionTextSize,
                scaleColorAlpha(0xFFE5EAF7, modalAlpha),
                context.getMatrices().peek().getPositionMatrix(),
                textXForInkLeft(MsdfFonts.bold(), sectionLabel, sectionTextSize, layout.contentX),
                layout.sectionLabelY,
                0.0F
        );
        float importHintSize = THEME_HINT_TEXT_SIZE * layout.scale;
        String importHint = Lang.translate("Drop .zip or use Phaze/Panoramas");
        MsdfRenderer.renderText(
                MsdfFonts.medium(),
                importHint,
                importHintSize,
                scaleColorAlpha(0xFF7E8799, modalAlpha),
                context.getMatrices().peek().getPositionMatrix(),
                textXForInkRight(MsdfFonts.medium(), importHint, importHintSize, layout.contentRight),
                layout.sectionBaselineY - capHeight(importHintSize),
                0.0F
        );

        renderThemePreviewCards(context, layout, visiblePresets, mouseX, mouseY, themeSelectorInteractive, modalAlpha);

        boolean resetHovered = themeSelectorInteractive && isPointInside(mouseX, mouseY, layout.resetX, layout.resetY, layout.resetW, layout.resetH);
        themeSelectorResetHoverAnim = animateThemeUiValue(themeSelectorResetHoverAnim, resetHovered ? 1.0F : 0.0F);
        float footerTextSize = THEME_FOOTER_TEXT_SIZE * layout.scale;
        String activeText = Lang.translate("Active") + ": " + activeThemeName;
        MsdfRenderer.renderText(
                MsdfFonts.medium(),
                activeText,
                footerTextSize,
                scaleColorAlpha(0xFFE5EAF7, modalAlpha),
                context.getMatrices().peek().getPositionMatrix(),
                textXForInkLeft(MsdfFonts.medium(), activeText, footerTextSize, layout.contentX),
                layout.footerY,
                0.0F
        );

        renderIconInkCentered(
                context,
                ICON_RESET,
                layout.resetIconCenterX,
                layout.resetIconCenterY,
                THEME_RESET_ICON_SIZE * layout.scale,
                scaleColorAlpha(lerpArgb(ICON_TINT_COLOR, 0xFFFFFFFF, themeSelectorResetHoverAnim), modalAlpha),
                0.06F,
                0.38F
        );
        MsdfRenderer.renderText(
                MsdfFonts.medium(),
                Lang.translate("Reset to default"),
                footerTextSize,
                scaleColorAlpha(lerpArgb(0xFFCBD4E6, 0xFFFFFFFF, themeSelectorResetHoverAnim), modalAlpha),
                context.getMatrices().peek().getPositionMatrix(),
                layout.resetTextX,
                layout.footerY,
                0.0F
        );

        context.getMatrices().pop();
        if (themeSelectorSettingsOpen) {
            renderThemeSettingsBackdrop(context, settingsModalProgress);
            renderThemeSettingsModal(context, layout, mouseX, mouseY, settingsModalProgress);
        }
    }

    private void renderThemePreviewCards(DrawContext context, ThemeSelectorLayout layout, List<MenuUiSettings.PanoramaDescriptor> presets, int mouseX, int mouseY, boolean interactive, float alpha) {
        if (presets.isEmpty()) {
            themeSelectorCardMaxScroll = 0.0F;
            themeSelectorCardScrollTarget = 0.0F;
            themeSelectorCardScrollOffset = 0.0F;
            float textSize = 9.2F * layout.scale;
            String text = Lang.translate("No panoramas found");
            MsdfRenderer.renderText(
                    MsdfFonts.medium(),
                    text,
                    textSize,
                    scaleColorAlpha(0xFF9099AD, alpha),
                    context.getMatrices().peek().getPositionMatrix(),
                    textXForInkCenter(MsdfFonts.medium(), text, textSize, layout.panelX + layout.panelW / 2.0F),
                    textYForCapCenter(textSize, layout.cardsViewportY + layout.cardsViewportH / 2.0F),
                    0.0F
            );
            return;
        }

        int rowCount = (presets.size() + 3) / 4;
        float rowStep = layout.cardTotalHeight + layout.cardsGap;
        // Scrolling ends where the last row's name/dot ends, so it rests one gap above the footer like the clip edge.
        float contentHeight = (rowCount - 1) * rowStep + layout.cardContentHeight;
        themeSelectorCardMaxScroll = Math.max(0.0F, contentHeight - layout.cardsViewportH);
        themeSelectorCardScrollTarget = Math.max(0.0F, Math.min(themeSelectorCardMaxScroll, themeSelectorCardScrollTarget));
        themeSelectorCardScrollOffset = animateThemeUiValue(themeSelectorCardScrollOffset, themeSelectorCardScrollTarget);

        themeCardClipContext = context;
        themeCardClipX = layout.panelX + 8.0F * layout.scale;
        themeCardClipY = layout.cardsViewportY;
        themeCardClipW = layout.panelW - 16.0F * layout.scale;
        themeCardClipH = layout.cardsViewportH;
        enableOverlayScissor(context, themeCardClipX, themeCardClipY, themeCardClipW, themeCardClipH);

        for (int row = 0; row < rowCount; row++) {
            int rowStart = row * 4;
            int rowEnd = Math.min(rowStart + 4, presets.size());
            int itemsInRow = rowEnd - rowStart;
            float rowY = layout.cardsY + row * rowStep - themeSelectorCardScrollOffset;
            if (rowY + layout.cardTotalHeight < layout.cardsViewportY || rowY > layout.cardsViewportY + layout.cardsViewportH) {
                continue;
            }

            float rowWidth = itemsInRow * layout.cardPreviewW + Math.max(0, itemsInRow - 1) * layout.cardsGap;
            float rowX = layout.panelX + (layout.panelW - rowWidth) / 2.0F;
            for (int column = 0; column < itemsInRow; column++) {
                renderThemePreviewCard(context, layout, presets.get(rowStart + column), rowX + column * (layout.cardPreviewW + layout.cardsGap), rowY, mouseX, mouseY, interactive, alpha);
            }
        }

        disableOverlayScissor(context);
        themeCardClipContext = null;
        renderThemePreviewScrollbar(context, layout, contentHeight, alpha);
    }

    private void renderThemePreviewCard(DrawContext context, ThemeSelectorLayout layout, MenuUiSettings.PanoramaDescriptor preset, float cardX, float cardY, int mouseX, int mouseY, boolean interactive, float alpha) {
        float cardW = layout.cardPreviewW;
        float cardH = layout.cardTotalHeight;
        float previewX = cardX;
        float previewY = cardY;
        float previewW = cardW;
        float previewH = layout.cardPreviewH;

        boolean selected = MenuUiSettings.getInstance().getSelectedPanoramaPresetId().equalsIgnoreCase(preset.getId());
        boolean hovered = interactive && isPointInside(mouseX, mouseY, cardX, cardY, cardW, cardH);
        float hoverAnim = interactive
                ? animateThemeUiValue(themeCardHoverAnims.getOrDefault(preset.getId(), 0.0F), hovered ? 1.0F : 0.0F)
                : 0.0F;
        themeCardHoverAnims.put(preset.getId(), hoverAnim);

        int previewOutline = selected
                ? lerpArgb(0xFF165A41, 0xFF2DB37F, hoverAnim)
                : lerpArgb(0xFF242A37, 0xFF5C6A85, hoverAnim);
        // Tint layers cover the whole thumbnail, edge included, and the outline goes on top of them,
        // so the rim brightens and dims together with the rest of the card.
        renderPanoramaPresetPreview(context, preset, previewX, previewY, previewW, previewOutline, alpha);

        if (preset.isRemote()) {
            // Remote cards stay visibly unavailable until their archive is installed.
            rectangle.render(ShapeProperties.create(context.getMatrices(), previewX, previewY, previewW, previewH)
                    .round(menuRadius(7.0F))
                    .softness(1.0F)
                    .color(scaleColorAlpha(preset.isDownloading() ? 0x3A000000 : 0x64000000, alpha))
                    .build());

            float progress = animateThemeUiValue(
                    themeCardDownloadProgressAnims.getOrDefault(preset.getId(), 0.0F),
                    preset.isDownloading() ? preset.downloadProgress() : 0.0F
            );
            themeCardDownloadProgressAnims.put(preset.getId(), progress);
            if (preset.isDownloading()) {
                float inset = 8.0F * layout.scale;
                float barH = 3.0F * layout.scale;
                float barX = previewX + inset;
                float barY = previewY + previewH - inset - barH;
                float barW = previewW - inset * 2.0F;
                rectangle.render(ShapeProperties.create(context.getMatrices(), barX, barY, barW, barH)
                        .round(barH / 2.0F)
                        .softness(1.0F)
                        .color(scaleColorAlpha(0x7A071017, alpha))
                        .build());
                rectangle.render(ShapeProperties.create(context.getMatrices(), barX, barY, Math.max(1.0F, barW * progress), barH)
                        .round(barH / 2.0F)
                        .softness(1.0F)
                        .color(scaleColorAlpha(0xFF28D778, alpha))
                        .build());
                String percent = Math.round(progress * 100.0F) + "%";
                float textSize = 7.0F * layout.scale;
                MsdfRenderer.renderText(MsdfFonts.bold(), percent, textSize, scaleColorAlpha(0xFFFFFFFF, alpha),
                        context.getMatrices().peek().getPositionMatrix(),
                        textXForInkRight(MsdfFonts.bold(), percent, textSize, previewX + previewW - inset),
                        previewY + inset, 0.0F);
            } else {
                float iconSize = Math.min(previewW, previewH) * 0.42F;
                renderIconInkCentered(context, ICON_DOWNLOAD, previewX + previewW / 2.0F, previewY + previewH / 2.0F,
                        iconSize, scaleColorAlpha(0xFFF2FAF5, alpha));
            }
        }

        int previewHoverAlpha = Math.round(hoverAnim * 28.0F * alpha);
        if (previewHoverAlpha > 0) {
            rectangle.render(ShapeProperties.create(context.getMatrices(), previewX, previewY, previewW, previewH)
                    .round(menuRadius(7.0F))
                    .softness(1.0F)
                    .color((previewHoverAlpha << 24) | 0xFFFFFF)
                    .build());
        }
        rectangle.render(ShapeProperties.create(context.getMatrices(), previewX, previewY, previewW, previewH)
                .round(menuRadius(7.0F))
                .softness(1.0F)
                .thickness(1.3F)
                .outlineColor(scaleColorAlpha(previewOutline, alpha))
                .color(0x00000000)
                .build());

        if (preset.isCustom()) {
            ThemeCardDeleteBadgeLayout customBadgeLayout = getThemeCardDeleteBadgeLayout(layout, previewX, previewY, previewW);
            boolean deleteHovered = interactive && isPointInside(mouseX, mouseY, customBadgeLayout.deleteX, customBadgeLayout.deleteY, customBadgeLayout.deleteSize, customBadgeLayout.deleteSize);
            float deleteHoverAnim = interactive
                    ? animateThemeUiValue(themeCardDeleteHoverAnims.getOrDefault(preset.getId(), 0.0F), deleteHovered ? 1.0F : 0.0F)
                    : 0.0F;
            themeCardDeleteHoverAnims.put(preset.getId(), deleteHoverAnim);
            rectangle.render(ShapeProperties.create(context.getMatrices(), customBadgeLayout.badgeX, customBadgeLayout.badgeY, customBadgeLayout.badgeW, customBadgeLayout.badgeH)
                    .round(customBadgeLayout.cornerRound)
                    .softness(1.0F)
                    .thickness(1.0F)
                    .outlineColor(scaleColorAlpha(lerpArgb(0xFF2D5A49, 0xFF4B8D72, hoverAnim), alpha))
                    .color(scaleColorAlpha(lerpArgb(0xB9132B22, 0xD61B3A2D, hoverAnim), alpha))
                    .build());
            MsdfRenderer.renderText(
                    MsdfFonts.bold(),
                    "ZIP",
                    customBadgeLayout.badgeTextSize,
                    scaleColorAlpha(0xFFF2FAF5, alpha),
                    context.getMatrices().peek().getPositionMatrix(),
                    textXForInkCenter(MsdfFonts.bold(), "ZIP", customBadgeLayout.badgeTextSize,
                            customBadgeLayout.badgeX + customBadgeLayout.badgeW / 2.0F),
                    textYForCapCenter(customBadgeLayout.badgeTextSize, customBadgeLayout.badgeY + customBadgeLayout.badgeH / 2.0F),
                    0.0F
            );

            rectangle.render(ShapeProperties.create(context.getMatrices(), customBadgeLayout.deleteX, customBadgeLayout.deleteY, customBadgeLayout.deleteSize, customBadgeLayout.deleteSize)
                    .round(customBadgeLayout.cornerRound)
                    .softness(1.0F)
                    .thickness(1.0F)
                    .outlineColor(scaleColorAlpha(lerpArgb(0xFF6C242B, 0xFFB1404D, deleteHoverAnim), alpha))
                    .color(scaleColorAlpha(lerpArgb(0xB92A0D14, 0xE5411C28, deleteHoverAnim), alpha))
                    .build());
            // This badge is tiny after the main-menu scale is applied. At
            // 5.7 units the two diagonal MSDF strokes collapsed to roughly
            // one screen pixel, and the old X-only half-pixel offset made
            // one arm noticeably softer than the other. Give the glyph
            // enough screen coverage and keep it exactly centered.
            float deleteIconSize = 7.4F * layout.scale;
            renderIconInkCentered(
                    context,
                    ICON_CROSS,
                    customBadgeLayout.deleteX + customBadgeLayout.deleteSize / 2.0F,
                    customBadgeLayout.deleteY + customBadgeLayout.deleteSize / 2.0F,
                    deleteIconSize,
                    scaleColorAlpha(lerpArgb(0xFFE8A9B0, 0xFFFFD6D9, deleteHoverAnim), alpha),
                    0.16F,
                    0.38F
            );
        }

        float nameTextSize = THEME_CARD_NAME_SIZE * layout.scale;
        float nameY = previewY + previewH + THEME_CARD_NAME_OFFSET * layout.scale;
        String name = preset.displayName();
        float badgeOuterSize = THEME_CARD_BADGE_SIZE * layout.scale;
        float badgeInnerSize = 7.0F * layout.scale;
        float badgeX = previewX + previewW - badgeOuterSize;
        float badgeY = nameY + (capHeight(nameTextSize) - badgeOuterSize) / 2.0F;

        SettingLabel nameLabel = themeCardNameLabels.computeIfAbsent(preset.getId(),
                ignored -> new SettingLabel().clip(themeCardNameClip));
        nameLabel.updateHover(hovered);
        nameLabel.renderMsdf(
                context.getMatrices(),
                name,
                nameTextSize,
                textXForInkLeft(MsdfFonts.bold(), name, nameTextSize, previewX),
                nameY,
                badgeX - 3.0F * layout.scale,
                nameY - nameTextSize * 0.3F,
                nameTextSize * 1.4F,
                scaleColorAlpha(0xFFF5F7FD, alpha)
        );

        rectangle.render(ShapeProperties.create(context.getMatrices(), badgeX, badgeY, badgeOuterSize, badgeOuterSize)
                .round(badgeOuterSize / 2.0F)
                .softness(1.0F)
                .color(scaleColorAlpha(selected ? 0xFF133926 : 0xFF2A3140, alpha))
                .build());
        float badgeInnerOffset = (badgeOuterSize - badgeInnerSize) / 2.0F;
        rectangle.render(ShapeProperties.create(context.getMatrices(), badgeX + badgeInnerOffset, badgeY + badgeInnerOffset, badgeInnerSize, badgeInnerSize)
                .round(badgeInnerSize / 2.0F)
                .softness(1.0F)
                .color(scaleColorAlpha(selected ? 0xFF25E163 : 0xFF7F8798, alpha))
                .build());
    }

    private ThemeCardDeleteBadgeLayout getThemeCardDeleteBadgeLayout(ThemeSelectorLayout layout, float previewX, float previewY, float previewW) {
        float badgeTextSize = 6.5F * layout.scale;
        float badgePaddingX = 5.0F * layout.scale;
        float badgeW = MsdfFonts.bold().getWidth("ZIP", badgeTextSize) + badgePaddingX * 2.0F;
        float badgeH = 11.0F * layout.scale;
        float deleteSize = 11.0F * layout.scale;
        float gap = 3.0F * layout.scale;
        float inset = THEME_CARD_INSET * layout.scale;
        float cornerRound = 3.3F * layout.scale;
        float badgeY = previewY + inset;
        float deleteX = previewX + previewW - deleteSize - inset;
        float badgeX = deleteX - gap - badgeW;
        return new ThemeCardDeleteBadgeLayout(badgeTextSize, badgePaddingX, badgeW, badgeH, badgeX, badgeY, deleteSize, deleteX, badgeY, cornerRound);
    }

    /** The thumbnail's anti-aliased rim blends into {@code rimColor}, so it must follow the animated outline color. */
    private void renderPanoramaPresetPreview(DrawContext context, MenuUiSettings.PanoramaDescriptor preset, float x, float y, float size, int rimColor, float alpha) {
        // A card only draws its thumbnail. Calling getRenderer() here used
        // to instantiate the full panorama and, for a custom ZIP, decode
        // and upload all six cube faces as soon as its row entered the
        // scroll viewport. That was the large hitch when opening/scrolling
        // the selector. Full faces are now loaded only when a preset is
        // actually selected for the main-menu background.
        Identifier preview = preset.previewTexture();
        if (!preset.hasPreviewTexture()) {
            rectangle.render(ShapeProperties.create(context.getMatrices(), x, y, Math.max(1.0F, size), Math.max(1.0F, size))
                    .round(menuRadius(7.0F))
                    .softness(1.0F)
                    .color(scaleColorAlpha(0xFF18202B, alpha))
                    .build());
            return;
        }
        int textureSize = preset.previewTextureSize();
        int cropInset = preset.previewCropInset();
        int cropSize = preset.previewCropSize();
        Render2DUtil.drawTexture(
                context,
                preview,
                x,
                y,
                Math.max(1.0F, size),
                menuRadius(7.0F),
                cropInset,
                cropSize,
                textureSize,
                scaleColorAlpha(rimColor, alpha),
                scaleColorAlpha(0xFFFFFFFF, alpha)
        );
    }

    /**
     * Each modal level stacks the same full-screen dim: the selector gets exactly the dim the
     * panorama got when the selector opened, and the panorama behind both gets it twice.
     */
    private void renderThemeSettingsBackdrop(DrawContext context, float alpha) {
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 180.0F);
        rectangle.render(ShapeProperties.create(context.getMatrices(), 0.0F, 0.0F, getOverlayViewportWidth(), getOverlayViewportHeight())
                .softness(1.0F)
                .color(scaleColorAlpha(THEME_MODAL_DIM_COLOR, alpha))
                .build());
        context.getMatrices().pop();
    }

    private void renderThemePreviewScrollbar(DrawContext context, ThemeSelectorLayout layout, float contentHeight, float alpha) {
        if (themeSelectorCardMaxScroll <= 0.0F || contentHeight <= 0.0F) {
            return;
        }

        float trackW = 3.0F * layout.scale;
        float trackX = layout.contentRight + (layout.gap - trackW) / 2.0F;
        float trackY = layout.cardsViewportY;
        float trackH = layout.cardsViewportH;
        float thumbH = Math.max(18.0F * layout.scale, trackH * (trackH / contentHeight));
        float thumbTravel = Math.max(0.0F, trackH - thumbH);
        float thumbY = trackY + thumbTravel * (themeSelectorCardMaxScroll <= 0.0F ? 0.0F : (themeSelectorCardScrollOffset / themeSelectorCardMaxScroll));

        rectangle.render(ShapeProperties.create(context.getMatrices(), trackX, trackY, trackW, trackH)
                .round(trackW / 2.0F)
                .softness(1.0F)
                .color(scaleColorAlpha(0x1FFFFFFF, alpha))
                .build());
        rectangle.render(ShapeProperties.create(context.getMatrices(), trackX, thumbY, trackW, thumbH)
                .round(trackW / 2.0F)
                .softness(1.0F)
                .color(scaleColorAlpha(0x66C8D3E8, alpha))
                .build());
    }

    /**
     * Clips to a rectangle in the current matrix space, rounded once in framebuffer pixels.
     * DrawContext.enableScissor snaps to whole GUI pixels (2-4 screen pixels), which left the
     * clipped cards a visibly different distance from the footer than the panel edge.
     */
    private void enableOverlayScissor(DrawContext context, float x, float y, float width, float height) {
        BatchedRectangle.flushIfBatching();
        context.draw();
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        matrix.transformPosition(x, y, 0.0F, scissorMin);
        matrix.transformPosition(x + width, y + height, 0.0F, scissorMax);
        Window window = MinecraftClient.getInstance().getWindow();
        double scaleFactor = window.getScaleFactor();
        int left = (int) Math.round(Math.min(scissorMin.x, scissorMax.x) * scaleFactor);
        int right = (int) Math.round(Math.max(scissorMin.x, scissorMax.x) * scaleFactor);
        int top = (int) Math.round(Math.min(scissorMin.y, scissorMax.y) * scaleFactor);
        int bottom = (int) Math.round(Math.max(scissorMin.y, scissorMax.y) * scaleFactor);
        RenderSystem.enableScissor(left, window.getFramebufferHeight() - bottom, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    private static void disableOverlayScissor(DrawContext context) {
        BatchedRectangle.flushIfBatching();
        context.draw();
        RenderSystem.disableScissor();
    }

    private static void renderThemeCloseIcon(DrawContext context, float centerX, float centerY, float scale, float hoverAnim, float alpha) {
        renderIconInkCentered(
                context,
                ICON_CROSS,
                centerX,
                centerY,
                THEME_CLOSE_ICON_SIZE * scale,
                scaleColorAlpha(lerpArgb(ICON_TINT_COLOR, 0xFFFFFFFF, hoverAnim), alpha),
                0.04F,
                0.38F
        );
    }

    private void renderThemeSettingsModal(DrawContext context, ThemeSelectorLayout themeLayout, int mouseX, int mouseY, float modalProgress) {
        ThemeSettingsLayout layout = getThemeSettingsLayout(themeLayout);
        float modalAlpha = modalProgress;
        float modalYOffset = (1.0F - modalProgress) * 16.0F * layout.scale;
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, modalYOffset, 200.0F);

        renderThemeModalPanel(context, layout.panelX, layout.panelY, layout.panelW, layout.panelH, menuRadius(10.0F), 0xFF121720, 0xFF222A39, modalAlpha);

        float titleSize = THEME_TITLE_TEXT_SIZE * layout.scale;
        String title = Lang.translate("Settings");
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                title,
                titleSize,
                scaleColorAlpha(0xFFF6F8FE, modalAlpha),
                context.getMatrices().peek().getPositionMatrix(),
                textXForInkLeft(MsdfFonts.bold(), title, titleSize, layout.contentX),
                layout.titleY,
                0.0F
        );

        boolean closeHovered = isPointInside(mouseX, mouseY, layout.closeX, layout.closeY, layout.closeSize, layout.closeSize);
        themeSettingsCloseHoverAnim = animateThemeUiValue(themeSettingsCloseHoverAnim, closeHovered ? 1.0F : 0.0F);
        renderThemeCloseIcon(context, layout.closeCenterX, layout.closeCenterY, layout.scale, themeSettingsCloseHoverAnim, modalAlpha);

        panoramaSliderVisualProgress = animateThemeUiValue(
                panoramaSliderVisualProgress,
                (float) (MenuUiSettings.getInstance().getPanoramaSpeed() / 100.0D)
        );
        guiFpsSliderVisualProgress = animateThemeUiValue(
                guiFpsSliderVisualProgress,
                (MenuUiSettings.getInstance().getGuiFpsLimit() - MenuUiSettings.MIN_GUI_FPS_LIMIT)
                        / (float) (MenuUiSettings.MAX_GUI_FPS_LIMIT - MenuUiSettings.MIN_GUI_FPS_LIMIT)
        );
        guiScaleSliderVisualProgress = animateThemeUiValue(guiScaleSliderVisualProgress,
                (MenuUiSettings.getInstance().getGuiScale() - MenuUiSettings.MIN_GUI_SCALE)
                        / (MenuUiSettings.MAX_GUI_SCALE - MenuUiSettings.MIN_GUI_SCALE));

        renderThemeSettingsSlider(
                context,
                layout,
                Lang.translate("Rotating Panorama Speed"),
                Lang.translate("Default") + ": 10%",
                Math.round(MenuUiSettings.getInstance().getPanoramaSpeed()) + "%",
                layout.panoramaTrackX,
                layout.panoramaTrackY,
                layout.trackWidth,
                layout.trackHeight,
                panoramaSliderVisualProgress,
                mouseX,
                mouseY,
                activeThemeSettingsSlider == ThemeSettingsSlider.PANORAMA_SPEED,
                layout.panoramaLabelY,
                modalAlpha
        );

        renderThemeSettingsSlider(context, layout, Lang.translate("GUI Scale"),
                Lang.translate("Scales the entire Phaze GUI"),
                String.format(java.util.Locale.ROOT, "%.1fx", MenuUiSettings.getInstance().getGuiScale()),
                layout.scaleTrackX, layout.scaleTrackY, layout.trackWidth, layout.trackHeight,
                guiScaleSliderVisualProgress, mouseX, mouseY,
                activeThemeSettingsSlider == ThemeSettingsSlider.GUI_SCALE,
                layout.scaleLabelY, modalAlpha);

        renderThemeSettingsSlider(
                context,
                layout,
                Lang.translate("Menu FPS Limit"),
                Lang.translate("Applies to Main Menu, Mod Menu, Sodium and other screens"),
                MenuUiSettings.getInstance().getGuiFpsLimit() + " FPS",
                layout.fpsTrackX,
                layout.fpsTrackY,
                layout.trackWidth,
                layout.trackHeight,
                guiFpsSliderVisualProgress,
                mouseX,
                mouseY,
                activeThemeSettingsSlider == ThemeSettingsSlider.GUI_FPS_LIMIT,
                layout.fpsLabelY,
                modalAlpha
        );

        context.getMatrices().pop();
    }

    private void renderThemeModalPanel(DrawContext context, float x, float y, float width, float height, float round, int fillColor, int outlineColor, float alpha) {
        rectangle.render(ShapeProperties.create(context.getMatrices(), x, y, width, height)
                .round(round)
                .softness(1.0F)
                .color(scaleColorAlpha(outlineColor, alpha))
                .build());

        float inset = 1.0F;
        rectangle.render(ShapeProperties.create(
                        context.getMatrices(),
                        x + inset,
                        y + inset,
                        Math.max(1.0F, width - inset * 2.0F),
                        Math.max(1.0F, height - inset * 2.0F)
                )
                .round(Math.max(0.0F, round - 1.0F))
                .softness(1.0F)
                .color(scaleColorAlpha(fillColor, alpha))
                .build());
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
            float labelY,
            float alpha
    ) {
        float clampedProgress = Math.max(0.0F, Math.min(1.0F, progress));
        float titleSize = THEME_SLIDER_TITLE_SIZE * layout.scale;
        float valueSize = THEME_SLIDER_VALUE_SIZE * layout.scale;
        float subtitleSize = THEME_SLIDER_SUBTITLE_SIZE * layout.scale;

        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                title,
                titleSize,
                scaleColorAlpha(0xFFF0F4FE, alpha),
                context.getMatrices().peek().getPositionMatrix(),
                textXForInkLeft(MsdfFonts.bold(), title, titleSize, layout.contentX),
                labelY,
                0.0F
        );

        // Right-aligned to the content edge and sharing the title's baseline.
        MsdfRenderer.renderText(
                MsdfFonts.medium(),
                valueLabel,
                valueSize,
                scaleColorAlpha(0xFFCAD5EC, alpha),
                context.getMatrices().peek().getPositionMatrix(),
                textXForInkRight(MsdfFonts.medium(), valueLabel, valueSize, layout.contentRight),
                labelY + capHeight(titleSize) - capHeight(valueSize),
                0.0F
        );

        boolean hovered = isPointInside(mouseX, mouseY, trackX, trackY - 3.0F * layout.scale, trackWidth, trackHeight + 6.0F * layout.scale);
        rectangle.render(ShapeProperties.create(context.getMatrices(), trackX, trackY, trackWidth, trackHeight)
                .round(trackHeight / 2.0F)
                .softness(1.0F)
                .thickness(1.0F)
                .outlineColor(scaleColorAlpha(hovered || dragging ? 0xFF32435E : 0xFF263142, alpha))
                .color(scaleColorAlpha(0xFF1A212D, alpha))
                .build());

        float knobSize = trackHeight + 5.0F * layout.scale;
        // The fill ends at the knob centre; while the knob covers all of it, its anti-aliased rim would still show.
        float fillWidth = trackWidth * clampedProgress;
        if (fillWidth > knobSize / 2.0F) {
            rectangle.render(ShapeProperties.create(context.getMatrices(), trackX, trackY, fillWidth, trackHeight)
                    .round(Math.min(trackHeight, fillWidth) / 2.0F)
                    .softness(1.0F)
                    .color(scaleColorAlpha(0xFF4F9CFF, alpha))
                    .build());
        }

        float knobX = trackX + trackWidth * clampedProgress - knobSize / 2.0F;
        float knobY = trackY - (knobSize - trackHeight) / 2.0F;
        rectangle.render(ShapeProperties.create(context.getMatrices(), knobX, knobY, knobSize, knobSize)
                .round(knobSize / 2.0F)
                .softness(1.0F)
                .thickness(1.0F)
                .outlineColor(scaleColorAlpha(0xFF1F2A38, alpha))
                .color(scaleColorAlpha(dragging ? 0xFFFFFFFF : 0xFFF3F7FF, alpha))
                .build());

        MsdfRenderer.renderText(
                MsdfFonts.medium(),
                subtitle,
                subtitleSize,
                scaleColorAlpha(0xFF8E99AF, alpha),
                context.getMatrices().peek().getPositionMatrix(),
                textXForInkLeft(MsdfFonts.medium(), subtitle, subtitleSize, layout.contentX),
                trackY + trackHeight + THEME_SLIDER_INNER_GAP * layout.scale,
                0.0F
        );
    }

    /**
     * Two-line tooltip above a button. The box is sized from the visible content so the padding is the same on
     * all four sides: title cap top, line-two bottom, leftmost and rightmost ink.
     */
    private void renderTwoLineTooltip(DrawContext context, MainMenuButtonWidget button, String title, Identifier icon, float iconHeight, String line) {
        if (button == null || !button.isHovered()) {
            return;
        }
        float scale = phaze$menuScale();
        float pad = TOOLTIP_PADDING * scale;
        float titleSize = TOOLTIP_TITLE_SIZE * scale;
        float lineSize = TOOLTIP_LINE_SIZE * scale;
        float iconGap = TOOLTIP_ICON_GAP * scale;
        float iconInkW = iconInkWidth(icon, iconHeight);
        float lineHeight = Math.max(capHeight(lineSize), iconInkHeight(icon, iconHeight));
        float contentW = Math.max(MsdfFonts.bold().getInkWidth(title, titleSize),
                iconInkW + iconGap + MsdfFonts.medium().getInkWidth(line, lineSize));
        float w = contentW + pad * 2.0F;
        float h = pad + capHeight(titleSize) + TOOLTIP_LINE_GAP * scale + lineHeight + pad;
        float x = button.getX() + (button.getWidth() - w) / 2.0F;
        float y = button.getY() - h - 6.0F * scale;

        renderTooltipBox(context, x, y, w, h);
        float contentX = x + pad;
        MsdfRenderer.renderText(
                MsdfFonts.bold(), title, titleSize, TOOLTIP_TEXT_COLOR,
                context.getMatrices().peek().getPositionMatrix(),
                textXForInkLeft(MsdfFonts.bold(), title, titleSize, contentX), y + pad, 0.0F
        );
        float lineCenterY = y + h - pad - lineHeight / 2.0F;
        renderIconInkCentered(context, icon, contentX + iconInkW / 2.0F, lineCenterY, iconHeight, ICON_TINT_COLOR);
        MsdfRenderer.renderText(
                MsdfFonts.medium(), line, lineSize, TOOLTIP_TEXT_COLOR,
                context.getMatrices().peek().getPositionMatrix(),
                textXForInkLeft(MsdfFonts.medium(), line, lineSize, contentX + iconInkW + iconGap),
                textYForCapCenter(lineSize, lineCenterY), 0.0F
        );
    }

    private void renderTooltipBox(DrawContext context, float x, float y, float w, float h) {
        rectangle.render(ShapeProperties.create(context.getMatrices(), x, y, w, h)
                .round(menuRadius(6.0F))
                .softness(1.1F)
                .thickness(1.4F)
                .outlineColor(0xFF1E2740)
                .color(0xFF0A1020)
                .build());
    }

    /** Distance from every screen edge to the nearest visible element of the main menu chrome. */
    private int screenMargin() {
        return Math.max(8, Math.round(10.0F * phaze$menuScale()));
    }

    private void renderFooterTexts(DrawContext context, float overlayWidth, float overlayHeight) {
        float scale = phaze$menuScale();
        float footerSize = 12.0F * scale;
        float margin = screenMargin();
        float footerTextY = overlayHeight - margin - capHeight(footerSize);
        int footerColor = MenuStyle.withAlpha(TOOLTIP_TEXT_COLOR, 0.65F);

        MsdfRenderer.renderText(
                MsdfFonts.medium(),
                FOOTER_LEFT_TEXT,
                footerSize,
                footerColor,
                context.getMatrices().peek().getPositionMatrix(),
                textXForInkLeft(MsdfFonts.medium(), FOOTER_LEFT_TEXT, footerSize, margin),
                footerTextY,
                0.0F
        );

        String rightFooterText = Lang.translate(FOOTER_RIGHT_TEXT);
        MsdfRenderer.renderText(
                MsdfFonts.medium(),
                rightFooterText,
                footerSize,
                footerColor,
                context.getMatrices().peek().getPositionMatrix(),
                Math.max(margin, textXForInkRight(MsdfFonts.medium(), rightFooterText, footerSize, overlayWidth - margin)),
                footerTextY,
                0.0F
        );
    }

    private float phaze$menuScale() {
        return (1.0F / 1.4F) * MenuUiSettings.getInstance().getGuiScale();
    }

    /** Radii are authored for the default GUI scale and follow the user's GUI scale. */
    private static float menuRadius(float radius) {
        return radius * MenuUiSettings.getInstance().getGuiScale() / MenuUiSettings.DEFAULT_GUI_SCALE;
    }

    private static void renderMenuIconPrecise(DrawContext context, Identifier icon, float x, float y, float width, float height, int color) {
        renderMenuIconPrecise(context, icon, x, y, width, height, color, 0.0F, 0.42F);
    }

    private static void renderMenuIconPrecise(
            DrawContext context,
            Identifier icon,
            float x,
            float y,
            float width,
            float height,
            int color,
            float msdfThickness,
            float msdfSmoothness
    ) {
        if (UiMsdfIconAtlas.renderIcon(
                context,
                icon,
                x,
                y,
                width,
                height,
                color,
                true,
                msdfThickness,
                msdfSmoothness
        )) {
            return;
        }
        FittedIconRect fittedRect = fitIconRect(x, y, width, height, getIconAspectRatio(icon), true);
        Render2DUtil.drawTexture(
                context.getMatrices(),
                icon,
                fittedRect.x(),
                fittedRect.x() + fittedRect.width(),
                fittedRect.y(),
                fittedRect.y() + fittedRect.height(),
                0.0F,
                getIconRegionWidth(icon),
                getIconRegionHeight(icon),
                getIconRegionX(icon),
                getIconRegionY(icon),
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
        return 0;
    }

    private static int getIconRegionY(Identifier icon) {
        return 0;
    }

    private static int getIconRegionWidth(Identifier icon) {
        return getIconTextureWidth(icon);
    }

    private static int getIconRegionHeight(Identifier icon) {
        return getIconTextureHeight(icon);
    }

    private static float getIconAspectRatio(Identifier icon) {
        return UiMsdfIconAtlas.resolveAspectRatio(icon);
    }

    private static FittedIconRect fitIconRect(float x, float y, float width, float height, float aspectRatio, boolean precise) {
        float safeWidth = precise ? Math.max(1.0F, width) : Math.max(1.0F, Math.round(width));
        float safeHeight = precise ? Math.max(1.0F, height) : Math.max(1.0F, Math.round(height));
        float safeAspect = aspectRatio > 0.0F ? aspectRatio : 1.0F;

        float drawWidth = safeWidth;
        float drawHeight = drawWidth / safeAspect;
        if (drawHeight > safeHeight) {
            drawHeight = safeHeight;
            drawWidth = drawHeight * safeAspect;
        }

        float drawX = x + (safeWidth - drawWidth) * 0.5F;
        float drawY = y + (safeHeight - drawHeight) * 0.5F;
        if (!precise) {
            drawX = Math.round(drawX);
            drawY = Math.round(drawY);
            drawWidth = Math.max(1.0F, Math.round(drawWidth));
            drawHeight = Math.max(1.0F, Math.round(drawHeight));
        }

        return new FittedIconRect(drawX, drawY, drawWidth, drawHeight);
    }

    private static float iconInkWidth(Identifier icon, float height) {
        return height * getIconAspectRatio(icon) * UiMsdfIconAtlas.ink(icon).width();
    }

    private static float iconInkHeight(Identifier icon, float height) {
        return height * UiMsdfIconAtlas.ink(icon).height();
    }

    /** Draws an icon whose visible shape, not its padded atlas cell, is centred on the given point. */
    private static void renderIconInkCentered(DrawContext context, Identifier icon, float centerX, float centerY, float height,
                                              int color, float msdfThickness, float msdfSmoothness) {
        UiMsdfIconAtlas.IconInk ink = UiMsdfIconAtlas.ink(icon);
        float width = height * getIconAspectRatio(icon);
        renderMenuIconPrecise(context, icon, centerX - width * ink.centerX(), centerY - height * ink.centerY(),
                width, height, color, msdfThickness, msdfSmoothness);
    }

    private static void renderIconInkCentered(DrawContext context, Identifier icon, float centerX, float centerY, float height, int color) {
        renderIconInkCentered(context, icon, centerX, centerY, height, color, 0.0F, 0.42F);
    }

    private static float textXForInkLeft(MsdfFont font, String text, float size, float inkLeft) {
        return inkLeft - font.getInkLeft(text, size);
    }

    private static float textXForInkRight(MsdfFont font, String text, float size, float inkRight) {
        return inkRight - font.getInkRight(text, size);
    }

    private static float textXForInkCenter(MsdfFont font, String text, float size, float centerX) {
        return centerX - (font.getInkLeft(text, size) + font.getInkRight(text, size)) * 0.5F;
    }

    /** MSDF y is the cap top, so this centres the cap height on a line. */
    private static float textYForCapCenter(float size, float centerY) {
        return centerY - MSDF_CAP_HEIGHT * size * 0.5F;
    }

    private static float capHeight(float size) {
        return MSDF_CAP_HEIGHT * size;
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

    private static int scaleColorAlpha(int color, float alpha) {
        float clamped = Math.max(0.0F, Math.min(1.0F, alpha));
        int baseAlpha = (color >>> 24) & 0xFF;
        int scaledAlpha = Math.round(baseAlpha * clamped);
        return (color & 0x00FFFFFF) | (scaledAlpha << 24);
    }

    private static float easeOutThemeModal(float progress) {
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        float inverted = 1.0F - clamped;
        return 1.0F - inverted * inverted * inverted;
    }

    private void updateThemeUiAnimationTiming() {
        long nowNs = System.nanoTime();
        float dt = themeUiLastFrameNs > 0L
                ? Math.min(0.05F, (nowNs - themeUiLastFrameNs) / 1_000_000_000.0F)
                : (1.0F / 60.0F);
        themeUiLastFrameNs = nowNs;
        themeUiSmoothing = 1.0F - (float) Math.exp(-dt * 14.0F);
        themeModalSmoothing = 1.0F - (float) Math.exp(-dt * 6.0F);
    }

    private float animateThemeUiValue(float current, float target) {
        float next = current + (target - current) * themeUiSmoothing;
        if (Math.abs(target - next) < 0.001F) {
            return target;
        }
        return next;
    }

    private float animateThemeModalValue(float current, float target) {
        float next = current + (target - current) * themeModalSmoothing;
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
        this.themeSelectorOpenAnim = open ? 0.0F : 0.0F;
        this.themeSelectorSettingsOpenAnim = 0.0F;
        this.themeSelectorSettingsOpen = false;
        this.themeSelectorSearchFocused = open;
        if (open) {
            this.themeSelectorSearchQuery = "";
        }
        this.themeSelectorSettingsHoverAnim = 0.0F;
        this.themeSelectorCloseHoverAnim = 0.0F;
        this.themeSettingsCloseHoverAnim = 0.0F;
        this.themeSelectorResetHoverAnim = 0.0F;
        this.themeSelectorCardScrollOffset = 0.0F;
        this.themeSelectorCardScrollTarget = 0.0F;
        this.themeSelectorCardMaxScroll = 0.0F;
        this.themeCardHoverAnims.clear();
        this.themeCardDeleteHoverAnims.clear();
        this.themeCardDownloadProgressAnims.clear();
        this.themeSettingsPresetHoverAnims.clear();
        this.panoramaSliderVisualProgress = (float) (MenuUiSettings.getInstance().getPanoramaSpeed() / 100.0D);
        this.guiFpsSliderVisualProgress = (MenuUiSettings.getInstance().getGuiFpsLimit() - MenuUiSettings.MIN_GUI_FPS_LIMIT)
                / (float) (MenuUiSettings.MAX_GUI_FPS_LIMIT - MenuUiSettings.MIN_GUI_FPS_LIMIT);
        this.activeThemeSettingsSlider = ThemeSettingsSlider.NONE;
    }

    private void syncDisplayedPanoramaName() {
        this.activeThemeName = MenuUiSettings.getInstance().getSelectedPanoramaPreset().displayName();
    }

    private void setThemeSelectorSettingsOpen(boolean open) {
        if (this.themeSelectorSettingsOpen == open) {
            return;
        }
        this.themeSelectorSettingsOpen = open;
        this.themeSelectorSettingsOpenAnim = open ? 0.0F : 0.0F;
        this.themeSelectorSearchFocused = !open && this.themeSelectorOpen;
        if (open) {
            this.panoramaSliderVisualProgress = (float) (MenuUiSettings.getInstance().getPanoramaSpeed() / 100.0D);
            this.guiFpsSliderVisualProgress = (MenuUiSettings.getInstance().getGuiFpsLimit() - MenuUiSettings.MIN_GUI_FPS_LIMIT)
                    / (float) (MenuUiSettings.MAX_GUI_FPS_LIMIT - MenuUiSettings.MIN_GUI_FPS_LIMIT);
        }
        this.themeSettingsPresetHoverAnims.clear();
        this.activeThemeSettingsSlider = ThemeSettingsSlider.NONE;
    }

    private ThemeSelectorLayout getThemeSelectorLayout() {
        float scale = phaze$menuScale();
        float overlayW = getOverlayViewportWidth();
        float overlayH = getOverlayViewportHeight();
        float panelW = Math.min(overlayW - 26.0F * scale, 418.0F * scale);
        float panelH = Math.min(overlayH - 28.0F * scale, 280.0F * scale);
        panelW = Math.max(320.0F * scale, panelW);
        panelH = Math.max(210.0F * scale, panelH);
        panelW = Math.min(panelW, overlayW - 12.0F * scale);
        panelH = Math.min(panelH, overlayH - 12.0F * scale);

        ThemeSelectorLayout l = new ThemeSelectorLayout();
        l.scale = scale;
        l.panelX = (overlayW - panelW) / 2.0F;
        l.panelY = (overlayH - panelH) / 2.0F;
        l.panelW = panelW;
        l.panelH = panelH;

        // Every visible edge (border, clip edge, text cap/baseline, icon shape) keeps one gap to its neighbour.
        float gap = THEME_MODAL_GAP * scale;
        float inset = THEME_MODAL_BORDER + gap;
        l.gap = gap;
        l.contentX = l.panelX + inset;
        l.contentRight = l.panelX + panelW - inset;
        float contentW = l.contentRight - l.contentX;

        l.titleY = l.panelY + inset;
        float titleCapCenterY = l.titleY + capHeight(THEME_TITLE_TEXT_SIZE * scale) * 0.5F;
        l.actionCenterY = titleCapCenterY;
        l.actionSize = THEME_ACTION_SIZE * scale;
        float closeInkW = iconInkWidth(ICON_CROSS, THEME_CLOSE_ICON_SIZE * scale);
        float settingsInkW = iconInkWidth(ICON_SETTINGS, THEME_SETTINGS_ICON_SIZE * scale);
        l.closeCenterX = l.contentRight - closeInkW * 0.5F;
        l.settingsCenterX = l.closeCenterX - closeInkW * 0.5F - gap - settingsInkW * 0.5F;
        l.closeX = l.closeCenterX - l.actionSize * 0.5F;
        l.closeY = titleCapCenterY - l.actionSize * 0.5F;
        l.closeSize = l.actionSize;
        l.settingsX = l.settingsCenterX - l.actionSize * 0.5F;
        l.settingsY = l.closeY;
        l.settingsSize = l.actionSize;

        l.searchX = l.contentX;
        l.searchY = l.titleY + capHeight(THEME_TITLE_TEXT_SIZE * scale) + gap;
        l.searchW = contentW;
        l.searchH = 22.0F * scale;
        l.sectionLabelY = l.searchY + l.searchH + gap;
        l.sectionBaselineY = l.sectionLabelY + capHeight(THEME_SECTION_TEXT_SIZE * scale);

        l.cardsGap = 9.0F * scale;
        l.cardsY = l.sectionBaselineY + gap;
        l.cardPreviewW = (contentW - l.cardsGap * 3.0F) / 4.0F;
        l.cardPreviewH = l.cardPreviewW;
        l.cardTotalHeight = l.cardPreviewH + 18.0F * scale;
        float nameCap = capHeight(THEME_CARD_NAME_SIZE * scale);
        float badgeSize = THEME_CARD_BADGE_SIZE * scale;
        // Last visible pixel of a card: the name baseline or the selection dot centred on the name, whichever is lower.
        l.cardContentHeight = l.cardPreviewH + THEME_CARD_NAME_OFFSET * scale + Math.max(nameCap, (nameCap + badgeSize) * 0.5F);

        float footerTextSize = THEME_FOOTER_TEXT_SIZE * scale;
        l.footerY = l.panelY + panelH - inset - capHeight(footerTextSize);
        l.cardsViewportY = l.cardsY;
        l.cardsViewportH = Math.max(1.0F, l.footerY - gap - l.cardsY);

        String resetText = Lang.translate("Reset to default");
        l.resetTextX = textXForInkRight(MsdfFonts.medium(), resetText, footerTextSize, l.contentRight);
        float resetIconSize = THEME_RESET_ICON_SIZE * scale;
        float resetInkW = iconInkWidth(ICON_RESET, resetIconSize);
        float resetIconInkRight = l.resetTextX + MsdfFonts.medium().getInkLeft(resetText, footerTextSize) - THEME_RESET_ICON_GAP * scale;
        l.resetIconCenterX = resetIconInkRight - resetInkW * 0.5F;
        l.resetIconCenterY = l.footerY + capHeight(footerTextSize) * 0.5F;
        l.resetX = resetIconInkRight - resetInkW;
        l.resetY = l.resetIconCenterY - resetIconSize * 0.5F;
        l.resetW = l.contentRight - l.resetX;
        l.resetH = resetIconSize;
        return l;
    }

    private ThemeSettingsLayout getThemeSettingsLayout(ThemeSelectorLayout parentLayout) {
        float scale = parentLayout.scale;
        float gap = THEME_MODAL_GAP * scale;
        float inset = THEME_MODAL_BORDER + gap;
        float innerGap = THEME_SLIDER_INNER_GAP * scale;
        float titleCap = capHeight(THEME_TITLE_TEXT_SIZE * scale);
        float labelCap = capHeight(THEME_SLIDER_TITLE_SIZE * scale);
        float trackHeight = THEME_SLIDER_TRACK_HEIGHT * scale;
        float sectionStep = labelCap + innerGap + trackHeight + innerGap + capHeight(THEME_SLIDER_SUBTITLE_SIZE * scale) + gap;
        // Height follows the content so the last subtitle keeps the same gap to the bottom border as the title has to the top.
        float contentH = titleCap + gap + sectionStep * 3.0F - gap;

        ThemeSettingsLayout l = new ThemeSettingsLayout();
        l.scale = scale;
        l.panelW = Math.min(parentLayout.panelW - 28.0F * scale, 282.0F * scale);
        l.panelH = Math.min(parentLayout.panelH - 18.0F * scale, contentH + inset * 2.0F);
        l.panelX = parentLayout.panelX + (parentLayout.panelW - l.panelW) / 2.0F;
        l.panelY = parentLayout.panelY + (parentLayout.panelH - l.panelH) / 2.0F;
        l.contentX = l.panelX + inset;
        l.contentRight = l.panelX + l.panelW - inset;
        l.trackWidth = l.contentRight - l.contentX;
        l.trackHeight = trackHeight;

        l.titleY = l.panelY + inset;
        l.closeSize = THEME_ACTION_SIZE * scale;
        float closeInkW = iconInkWidth(ICON_CROSS, THEME_CLOSE_ICON_SIZE * scale);
        l.closeCenterX = l.contentRight - closeInkW * 0.5F;
        l.closeCenterY = l.titleY + titleCap * 0.5F;
        l.closeX = l.closeCenterX - l.closeSize * 0.5F;
        l.closeY = l.closeCenterY - l.closeSize * 0.5F;

        l.panoramaLabelY = l.titleY + titleCap + gap;
        l.fpsLabelY = l.panoramaLabelY + sectionStep;
        l.scaleLabelY = l.fpsLabelY + sectionStep;
        float labelToTrack = labelCap + innerGap;
        l.panoramaTrackX = l.contentX;
        l.fpsTrackX = l.contentX;
        l.scaleTrackX = l.contentX;
        l.panoramaTrackY = l.panoramaLabelY + labelToTrack;
        l.fpsTrackY = l.fpsLabelY + labelToTrack;
        l.scaleTrackY = l.scaleLabelY + labelToTrack;
        return l;
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
            return;
        }

        if (slider == ThemeSettingsSlider.GUI_SCALE) {
            MenuUiSettings.getInstance().setGuiScale(MenuUiSettings.MIN_GUI_SCALE
                    + progress * (MenuUiSettings.MAX_GUI_SCALE - MenuUiSettings.MIN_GUI_SCALE));
        }
    }

    private MenuUiSettings.PanoramaDescriptor getThemeSelectorPresetAt(ThemeSelectorLayout layout, float mouseX, float mouseY) {
        if (!isPointInsideCardsViewport(layout, mouseX, mouseY)) {
            return null;
        }

        List<MenuUiSettings.PanoramaDescriptor> visiblePresets = getVisibleThemeSelectorPresets();
        int rowCount = (visiblePresets.size() + 3) / 4;
        float rowStep = layout.cardTotalHeight + layout.cardsGap;
        for (int row = 0; row < rowCount; row++) {
            int rowStart = row * 4;
            int rowEnd = Math.min(rowStart + 4, visiblePresets.size());
            int itemsInRow = rowEnd - rowStart;
            float rowY = layout.cardsY + row * rowStep - themeSelectorCardScrollOffset;
            float rowWidth = itemsInRow * layout.cardPreviewW + Math.max(0, itemsInRow - 1) * layout.cardsGap;
            float rowX = layout.panelX + (layout.panelW - rowWidth) / 2.0F;
            for (int column = 0; column < itemsInRow; column++) {
                float cardX = rowX + column * (layout.cardPreviewW + layout.cardsGap);
                if (isPointInside(mouseX, mouseY, cardX, rowY, layout.cardPreviewW, layout.cardTotalHeight)) {
                    return visiblePresets.get(rowStart + column);
                }
            }
        }
        return null;
    }

    private MenuUiSettings.PanoramaDescriptor getThemeSelectorDeletePresetAt(ThemeSelectorLayout layout, float mouseX, float mouseY) {
        if (!isPointInsideCardsViewport(layout, mouseX, mouseY)) {
            return null;
        }

        List<MenuUiSettings.PanoramaDescriptor> visiblePresets = getVisibleThemeSelectorPresets();
        int rowCount = (visiblePresets.size() + 3) / 4;
        float rowStep = layout.cardTotalHeight + layout.cardsGap;
        for (int row = 0; row < rowCount; row++) {
            int rowStart = row * 4;
            int rowEnd = Math.min(rowStart + 4, visiblePresets.size());
            int itemsInRow = rowEnd - rowStart;
            float rowY = layout.cardsY + row * rowStep - themeSelectorCardScrollOffset;
            float rowWidth = itemsInRow * layout.cardPreviewW + Math.max(0, itemsInRow - 1) * layout.cardsGap;
            float rowX = layout.panelX + (layout.panelW - rowWidth) / 2.0F;
            for (int column = 0; column < itemsInRow; column++) {
                MenuUiSettings.PanoramaDescriptor preset = visiblePresets.get(rowStart + column);
                if (!preset.isCustom()) {
                    continue;
                }
                float cardX = rowX + column * (layout.cardPreviewW + layout.cardsGap);
                ThemeCardDeleteBadgeLayout deleteBadgeLayout = getThemeCardDeleteBadgeLayout(layout, cardX, rowY, layout.cardPreviewW);
                if (isPointInside(mouseX, mouseY, deleteBadgeLayout.deleteX, deleteBadgeLayout.deleteY, deleteBadgeLayout.deleteSize, deleteBadgeLayout.deleteSize)) {
                    return preset;
                }
            }
        }
        return null;
    }

    private void deleteThemeSelectorCustomPreset(MenuUiSettings.PanoramaDescriptor preset) {
        if (preset == null || !preset.isCustom()) {
            return;
        }
        boolean wasSelected = MenuUiSettings.getInstance().getSelectedPanoramaPresetId().equalsIgnoreCase(preset.getId());
        if (MenuPanoramaRegistry.deleteCustomPanorama(preset.getId())) {
            themeCardHoverAnims.remove(preset.getId());
            themeCardDeleteHoverAnims.remove(preset.getId());
            themeCardDownloadProgressAnims.remove(preset.getId());
            if (wasSelected) {
                MenuUiSettings.getInstance().setSelectedPanoramaPreset(MenuUiSettings.PanoramaPreset.VANILLA);
            }
            syncDisplayedPanoramaName();
            themeSelectorCardScrollTarget = Math.max(0.0F, Math.min(themeSelectorCardMaxScroll, themeSelectorCardScrollTarget));
        }
    }

    private boolean isPointInsideCardsViewport(ThemeSelectorLayout layout, float mouseX, float mouseY) {
        return isPointInside(mouseX, mouseY, layout.panelX, layout.cardsViewportY, layout.panelW, layout.cardsViewportH);
    }

    private void scrollThemeSelectorCards(ThemeSelectorLayout layout, double verticalAmount) {
        if (themeSelectorCardMaxScroll <= 0.0F || verticalAmount == 0.0D) {
            return;
        }
        float step = layout.cardTotalHeight * 0.70F;
        themeSelectorCardScrollTarget -= (float) verticalAmount * step;
        if (themeSelectorCardScrollTarget < 0.0F) {
            themeSelectorCardScrollTarget = 0.0F;
        }
        if (themeSelectorCardScrollTarget > themeSelectorCardMaxScroll) {
            themeSelectorCardScrollTarget = themeSelectorCardMaxScroll;
        }
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
        restoreIasMainMenuOverrides();
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

                    if (isPointInside(overlayMouseX, overlayMouseY, settingsLayout.scaleTrackX, settingsLayout.scaleTrackY - 4.0F * settingsLayout.scale, settingsLayout.trackWidth, settingsLayout.trackHeight + 8.0F * settingsLayout.scale)) {
                        activeThemeSettingsSlider = ThemeSettingsSlider.GUI_SCALE;
                        updateThemeSettingsSlider(activeThemeSettingsSlider, settingsLayout, overlayMouseX);
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

                MenuUiSettings.PanoramaDescriptor deletePreset = getThemeSelectorDeletePresetAt(layout, overlayMouseX, overlayMouseY);
                if (deletePreset != null) {
                    deleteThemeSelectorCustomPreset(deletePreset);
                    return true;
                }

                MenuUiSettings.PanoramaDescriptor preset = getThemeSelectorPresetAt(layout, overlayMouseX, overlayMouseY);
                if (preset != null) {
                    if (preset.isRemote()) {
                        MenuPanoramaRegistry.activateRemotePanorama(preset.getId());
                        return true;
                    }
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
            if (!themeSelectorSettingsOpen) {
                ThemeSelectorLayout layout = getThemeSelectorLayout();
                float overlayMouseX = toOverlayCoordinate(mouseX);
                float overlayMouseY = toOverlayCoordinate(mouseY);
                if (isPointInsideCardsViewport(layout, overlayMouseX, overlayMouseY)) {
                    scrollThemeSelectorCards(layout, verticalAmount);
                }
            }
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
    public void onFilesDropped(List<Path> paths) {
        if (!themeSelectorOpen || themeSelectorSettingsOpen) {
            super.onFilesDropped(paths);
            return;
        }

        List<MenuUiSettings.PanoramaDescriptor> imported = MenuPanoramaRegistry.importArchives(paths);
        if (!imported.isEmpty()) {
            MenuUiSettings.getInstance().setSelectedPanoramaPreset(imported.get(imported.size() - 1));
            syncDisplayedPanoramaName();
            themeSelectorSearchQuery = "";
            themeCardHoverAnims.clear();
            themeCardDeleteHoverAnims.clear();
            themeCardDownloadProgressAnims.clear();
            themeSelectorCardScrollTarget = Float.MAX_VALUE;
        }
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

    private void renderThemeSelectorSearchText(DrawContext context, ThemeSelectorLayout layout, float alpha) {
        String displayText = themeSelectorSearchQuery.isEmpty() && !themeSelectorSearchFocused ? Lang.translate("Search...") : themeSelectorSearchQuery;
        float textSize = 8.7F * layout.scale;
        int textColor = scaleColorAlpha(themeSelectorSearchQuery.isEmpty() && !themeSelectorSearchFocused ? 0xFF9099AD : 0xFFE6ECFA, alpha);
        // Side padding equals the vertical space around the cap height.
        float pad = (layout.searchH - capHeight(textSize)) / 2.0F;
        float textX = textXForInkLeft(MsdfFonts.medium(), displayText, textSize, layout.searchX + pad);
        float textY = layout.searchY + pad;

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
            float caretX = themeSelectorSearchQuery.isEmpty()
                    ? layout.searchX + pad
                    : textX + MsdfFonts.medium().getInkRight(themeSelectorSearchQuery, textSize) + 1.5F * layout.scale;
            float caretY = layout.searchY + 6.0F * layout.scale;
            float caretH = layout.searchH - 12.0F * layout.scale;
            rectangle.render(ShapeProperties.create(context.getMatrices(), caretX, caretY, Math.max(1.0F, 1.1F * layout.scale), Math.max(5.0F, caretH))
                    .round(menuRadius(1.0F))
                    .softness(1.0F)
                    .color(scaleColorAlpha(0xFFD8E2F8, alpha))
                    .build());
        }
    }

    private List<MenuUiSettings.PanoramaDescriptor> getVisibleThemeSelectorPresets() {
        String query = themeSelectorSearchQuery.trim().toLowerCase(Locale.ROOT);
        List<MenuUiSettings.PanoramaDescriptor> presets = new ArrayList<>();
        for (MenuUiSettings.PanoramaDescriptor preset : MenuPanoramaRegistry.getAllPresets()) {
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
        private enum ButtonVisualStyle {
            DEFAULT,
            TOP_BAR_ICON
        }

        private final Identifier leftIcon;
        private final boolean dangerStyle;
        private final ButtonVisualStyle visualStyle;
        private final float iconScaleMultiplier;
        private float hoverAnim = 0.0F;
        private long lastFrameTimeNs = -1L;

        MainMenuButtonWidget(int x, int y, int width, int height, Text message, Identifier leftIcon, PressAction onPress) {
            this(x, y, width, height, message, leftIcon, false, ButtonVisualStyle.DEFAULT, 1.0F, onPress);
        }

        MainMenuButtonWidget(int x, int y, int width, int height, Text message, Identifier leftIcon, float iconScaleMultiplier, PressAction onPress) {
            this(x, y, width, height, message, leftIcon, false, ButtonVisualStyle.DEFAULT, iconScaleMultiplier, onPress);
        }

        MainMenuButtonWidget(int x, int y, int width, int height, Text message, Identifier leftIcon, ButtonVisualStyle visualStyle, float iconScaleMultiplier, PressAction onPress) {
            this(x, y, width, height, message, leftIcon, false, visualStyle, iconScaleMultiplier, onPress);
        }

        MainMenuButtonWidget(int x, int y, int width, int height, Text message, Identifier leftIcon, boolean dangerStyle, PressAction onPress) {
            this(x, y, width, height, message, leftIcon, dangerStyle, ButtonVisualStyle.DEFAULT, 1.0F, onPress);
        }

        MainMenuButtonWidget(int x, int y, int width, int height, Text message, Identifier leftIcon, boolean dangerStyle, float iconScaleMultiplier, PressAction onPress) {
            this(x, y, width, height, message, leftIcon, dangerStyle, ButtonVisualStyle.DEFAULT, iconScaleMultiplier, onPress);
        }

        MainMenuButtonWidget(int x, int y, int width, int height, Text message, Identifier leftIcon, boolean dangerStyle, ButtonVisualStyle visualStyle, float iconScaleMultiplier, PressAction onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
            this.leftIcon = leftIcon;
            this.dangerStyle = dangerStyle;
            this.visualStyle = visualStyle;
            this.iconScaleMultiplier = iconScaleMultiplier;
        }

        private void phaze$setDimensions(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            boolean hovered = this.isHovered();
            boolean topBarIconStyle = visualStyle == ButtonVisualStyle.TOP_BAR_ICON && this.getMessage().getString().isEmpty();
            float target = hovered ? 1.0F : 0.0F;
            long nowNs = System.nanoTime();
            float dt = lastFrameTimeNs > 0L ? Math.min(0.05F, (nowNs - lastFrameTimeNs) / 1_000_000_000.0F) : (1.0F / 60.0F);
            lastFrameTimeNs = nowNs;
            float smoothing = 1.0F - (float) Math.exp(-dt * 14.0F);
            hoverAnim += (target - hoverAnim) * smoothing;
            if (Math.abs(target - hoverAnim) < 0.01F) {
                hoverAnim = target;
            }

            int outline = topBarIconStyle
                    ? lerpArgb(0xFF0A1020, 0xFF101828, hoverAnim)
                    : dangerStyle
                    ? lerpArgb(0xFF7A2A36, 0xFF8A3341, hoverAnim)
                    : 0xFF27324A;
            int fill = topBarIconStyle
                    ? lerpArgb(0xFF090F1C, 0xFF101827, hoverAnim)
                    : dangerStyle
                    ? lerpArgb(0xFF2A0C13, 0xFF351018, hoverAnim)
                    : 0xFF090F1C;
            int hoverOverlayBase = dangerStyle ? 0xC14A61 : 0xFFFFFF;
            int hoverOverlayAlpha = dangerStyle ? (int) (hoverAnim * 14.0F) : (topBarIconStyle ? (int) (hoverAnim * 10.0F) : (int) (hoverAnim * 26.0F));
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
                        .round(menuRadius(7.0F / 1.3F))
                        .softness(1.2F)
                        .thickness(topBarIconStyle ? 1.0F : 1.8F)
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
                            .round(menuRadius(7.0F / 1.3F))
                            .softness(1.0F)
                            .color(hoverOverlay)
                            .build());
                }
            }

            // phaze$menuScale() includes the historical 1 / 1.4 layout
            // baseline. Text and icons were already authored at their
            // correct size before GUI scaling was added, so applying that
            // baseline to them a second time makes GUI scale 1.0 render at
            // roughly 71% of the original size. Only apply the user-facing
            // GUI scale here; widget geometry continues to use menuScale.
            float contentScale = screen != null
                    ? MenuUiSettings.getInstance().getGuiScale()
                    : 1.0F;
            String label = this.getMessage().getString();
            boolean compactIconButton = label.isEmpty();
            float size = ((compactIconButton ? 8.6F : 10.6F) / 1.3F) * contentScale;
            int textColor = lerpArgb(0xFFB8BCC8, 0xFFDCE1EC, hoverAnim);
            // Icon and label are centred as one group by their visible ink, both on the button's centre line.
            float centerX = this.getX() + this.width / 2.0F;
            float centerY = this.getY() + this.height / 2.0F;
            boolean hasLabel = !label.isEmpty();
            float textX = textXForInkCenter(MsdfFonts.bold(), label, size, centerX);
            if (leftIcon != null) {
                float iconH = (compactIconButton ? 8.0F : 10.0F) * this.iconScaleMultiplier * contentScale;
                float iconInkW = iconInkWidth(leftIcon, iconH);
                float gap = hasLabel ? 6.0F * contentScale : 0.0F;
                float totalW = iconInkW + gap + (hasLabel ? MsdfFonts.bold().getInkWidth(label, size) : 0.0F);
                float left = centerX - totalW / 2.0F;
                renderIconInkCentered(context, leftIcon, left + iconInkW / 2.0F, centerY, iconH, ICON_TINT_COLOR);
                textX = textXForInkLeft(MsdfFonts.bold(), label, size, left + iconInkW + gap);
            }
            if (hasLabel) {
                MsdfRenderer.renderText(
                        MsdfFonts.bold(),
                        label,
                        size,
                        textColor,
                        context.getMatrices().peek().getPositionMatrix(),
                        textX,
                        textYForCapCenter(size, centerY),
                        0.0F
                );
            }
        }
    }

    private static class ThemeCardDeleteBadgeLayout {
        private final float badgeTextSize;
        private final float badgePaddingX;
        private final float badgeW;
        private final float badgeH;
        private final float badgeX;
        private final float badgeY;
        private final float deleteSize;
        private final float deleteX;
        private final float deleteY;
        private final float cornerRound;

        private ThemeCardDeleteBadgeLayout(
                float badgeTextSize,
                float badgePaddingX,
                float badgeW,
                float badgeH,
                float badgeX,
                float badgeY,
                float deleteSize,
                float deleteX,
                float deleteY,
                float cornerRound
        ) {
            this.badgeTextSize = badgeTextSize;
            this.badgePaddingX = badgePaddingX;
            this.badgeW = badgeW;
            this.badgeH = badgeH;
            this.badgeX = badgeX;
            this.badgeY = badgeY;
            this.deleteSize = deleteSize;
            this.deleteX = deleteX;
            this.deleteY = deleteY;
            this.cornerRound = cornerRound;
        }
    }

    private record FittedIconRect(float x, float y, float width, float height) {
    }

    /** Filled once per frame by getThemeSelectorLayout. Text y values are cap tops. */
    private static final class ThemeSelectorLayout {
        private float scale;
        private float gap;
        private float panelX;
        private float panelY;
        private float panelW;
        private float panelH;
        private float contentX;
        private float contentRight;
        private float titleY;
        private float actionSize;
        private float actionCenterY;
        private float settingsCenterX;
        private float closeCenterX;
        private float settingsX;
        private float settingsY;
        private float settingsSize;
        private float closeX;
        private float closeY;
        private float closeSize;
        private float searchX;
        private float searchY;
        private float searchW;
        private float searchH;
        private float sectionLabelY;
        private float sectionBaselineY;
        private float cardsY;
        private float cardsGap;
        private float cardPreviewW;
        private float cardPreviewH;
        private float cardTotalHeight;
        private float cardContentHeight;
        private float cardsViewportY;
        private float cardsViewportH;
        private float footerY;
        private float resetTextX;
        private float resetIconCenterX;
        private float resetIconCenterY;
        private float resetX;
        private float resetY;
        private float resetW;
        private float resetH;
    }

    private static final class ThemeSettingsLayout {
        private float scale;
        private float panelX;
        private float panelY;
        private float panelW;
        private float panelH;
        private float titleY;
        private float closeCenterX;
        private float closeCenterY;
        private float closeX;
        private float closeY;
        private float closeSize;
        private float contentX;
        private float contentRight;
        private float trackWidth;
        private float trackHeight;
        private float panoramaLabelY;
        private float panoramaTrackX;
        private float panoramaTrackY;
        private float fpsLabelY;
        private float fpsTrackX;
        private float fpsTrackY;
        private float scaleLabelY;
        private float scaleTrackX;
        private float scaleTrackY;
    }

    private enum ThemeSettingsSlider {
        NONE,
        PANORAMA_SPEED,
        GUI_FPS_LIMIT
        ,GUI_SCALE
    }
}
