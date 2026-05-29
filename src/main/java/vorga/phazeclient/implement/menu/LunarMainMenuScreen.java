package vorga.phazeclient.implement.menu;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.util.Window;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.implement.Rectangle;
import vorga.phazeclient.base.util.render.Render2DUtil;
import vorga.phazeclient.implement.menu.MenuStyle;

import java.lang.reflect.Constructor;

public class LunarMainMenuScreen extends Screen {
    private static final double PREFERRED_OVERLAY_SCALE = 2.0D;
    private static final double MIN_OVERLAY_SCALE = 1.0D;
    private static final int DEFAULT_OVERLAY_WIDTH = 462;
    private static final int DEFAULT_OVERLAY_HEIGHT = 300;
    private static final Identifier ICON_SINGLE = Identifier.of("phaze", "textures/menu/user.png");
    private static final Identifier ICON_MULTI = Identifier.of("phaze", "textures/menu/users.png");
    private static final Identifier ICON_TAB = Identifier.of("phaze", "textures/menu/tab.png");
    private static final Identifier ICON_SETTINGS = Identifier.of("phaze", "textures/menu/settings.png");
    private static final Identifier ICON_CUBE = Identifier.of("phaze", "textures/menu/cube.png");
    private static final Identifier ICON_EXTERNAL = Identifier.of("phaze", "textures/menu/arrow_external_bold.png");
    private static final int TOOLTIP_TEXT_COLOR = 0xFF9EA7BB;
    private static final int ICON_TINT_COLOR = 0xFF9EA7BB;
    private final Rectangle rectangle = new Rectangle();
    private LunarButtonWidget modMenuButton;
    private LunarButtonWidget settingsButton;
    private double overlayScaleFactor = PREFERRED_OVERLAY_SCALE;
    private float overlayRenderScale = 1.0F;
    private int overlayViewportWidth = -1;
    private int overlayViewportHeight = -1;

    public LunarMainMenuScreen() {
        super(Text.literal("Phaze Main Menu"));
    }

    @Override
    protected void init() {
        updateOverlayMetrics();
        clearChildren();
        float scale = phaze$menuScale();
        float sizeMul = 1.3F / 1.2F;
        int overlayW = getOverlayViewportWidth();
        int overlayH = getOverlayViewportHeight();
        int width = Math.max(96, Math.round(320.0F * scale * sizeMul));
        int height = Math.max(12, Math.round(36.0F * scale * sizeMul));
        int gap = Math.max(3, Math.round(8.0F * scale));
        int x = overlayW / 2 - width / 2;
        int y = overlayH / 2 - Math.round(72.0F * scale);

        addDrawableChild(new LunarButtonWidget(x, y, width, height, Text.literal("Singleplayer"), ICON_SINGLE,
                b -> this.client.setScreen(new SelectWorldScreen(this))));
        y += height + gap;
        addDrawableChild(new LunarButtonWidget(x, y, width, height, Text.literal("Multiplayer"), ICON_MULTI,
                b -> this.client.setScreen(new MultiplayerScreen(this))));
        y += height + gap;
        addDrawableChild(new LunarButtonWidget(x, y, width, height, Text.literal("Quit"), null,
                b -> this.client.scheduleStop()));

        int size = Math.max(12, Math.round(30.0F * scale * sizeMul));
        int bx = overlayW / 2 + Math.round(42.0F * scale * sizeMul);
        int by = overlayH - Math.round(50.0F * scale);
        int dockGap = Math.max(6, Math.round(8.0F * scale));
        settingsButton = new LunarButtonWidget(
                bx - size - dockGap,
                by,
                size,
                size,
                Text.literal(""),
                ICON_SETTINGS,
                b -> this.client.setScreen(new OptionsScreen(this, this.client.options))
        );
        addDrawableChild(settingsButton);

        if (FabricLoader.getInstance().isModLoaded("modmenu")) {
            modMenuButton = new LunarButtonWidget(
                    bx, by, size, size, Text.literal(""), ICON_TAB, b -> openModMenu()
            );
            addDrawableChild(modMenuButton);
        } else {
            modMenuButton = null;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateOverlayMetrics();
        renderBackground(context, mouseX, mouseY, delta);
        context.getMatrices().push();
        context.getMatrices().scale(overlayRenderScale, overlayRenderScale, 1.0F);
        int overlayMouseX = Math.round(toOverlayCoordinate(mouseX));
        int overlayMouseY = Math.round(toOverlayCoordinate(mouseY));
        String title = "PHAZE CLIENT";
        float titleSize = (14.5F * phaze$menuScale()) / 1.3F;
        float overlayW = getOverlayViewportWidth();
        float overlayH = getOverlayViewportHeight();
        float titleX = MenuStyle.centerMsdfTextX(MsdfFonts.bold(), title, titleSize, overlayW / 2.0F - 180.0F, 360.0F);
        float titleY = overlayH / 2.0F - 128.0F;
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                title,
                titleSize,
                0xFFFFFFFF,
                context.getMatrices().peek().getPositionMatrix(),
                titleX,
                titleY,
                0.0F
        );
        super.render(context, overlayMouseX, overlayMouseY, delta);
        renderSettingsTooltip(context);
        renderModMenuTooltip(context, overlayMouseX, overlayMouseY);
        context.getMatrices().pop();
    }

    @Override
    public boolean shouldPause() {
        return false;
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

    private float phaze$menuScale() {
        return 1.0F / 1.4F;
    }

    private static void renderMenuIcon(DrawContext context, Identifier icon, float x, float y, float width, float height) {
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
                ICON_TINT_COLOR
        );
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static int getIconTextureWidth(Identifier icon) {
        if (ICON_SINGLE.equals(icon)) {
            return 224;
        }
        if (ICON_MULTI.equals(icon)) {
            return 320;
        }
        return 256;
    }

    private static int getIconTextureHeight(Identifier icon) {
        return 256;
    }

    private static int getIconRegionX(Identifier icon) {
        return ICON_EXTERNAL.equals(icon) ? 41 : 0;
    }

    private static int getIconRegionY(Identifier icon) {
        return ICON_EXTERNAL.equals(icon) ? 27 : 0;
    }

    private static int getIconRegionWidth(Identifier icon) {
        return ICON_EXTERNAL.equals(icon) ? 188 : getIconTextureWidth(icon);
    }

    private static int getIconRegionHeight(Identifier icon) {
        return ICON_EXTERNAL.equals(icon) ? 188 : getIconTextureHeight(icon);
    }

    private static float getIconAspectRatio(Identifier icon) {
        return getIconTextureWidth(icon) / (float) getIconTextureHeight(icon);
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

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        init(client, width, height);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        updateOverlayMetrics();
        return super.mouseClicked(toOverlayCoordinate(mouseX), toOverlayCoordinate(mouseY), button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        updateOverlayMetrics();
        return super.mouseReleased(toOverlayCoordinate(mouseX), toOverlayCoordinate(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        updateOverlayMetrics();
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
        return super.mouseScrolled(
                toOverlayCoordinate(mouseX),
                toOverlayCoordinate(mouseY),
                horizontalAmount,
                verticalAmount
        );
    }

    private static class LunarButtonWidget extends ButtonWidget {
        private final Identifier leftIcon;
        private float hoverAnim = 0.0F;

        LunarButtonWidget(int x, int y, int width, int height, Text message, Identifier leftIcon, PressAction onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
            this.leftIcon = leftIcon;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            boolean hovered = this.isHovered();
            float target = hovered ? 1.0F : 0.0F;
            hoverAnim += (target - hoverAnim) * Math.min(1.0F, delta * 0.28F);

            int outline = 0xFF27324A;
            int fill = 0xFF090F1C;
            int hoverOverlay = ((int) (hoverAnim * 26.0F) << 24) | 0xFFFFFF;

            LunarMainMenuScreen screen = (LunarMainMenuScreen) MinecraftClient.getInstance().currentScreen;
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
                        .thickness(1.8F)
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
}
