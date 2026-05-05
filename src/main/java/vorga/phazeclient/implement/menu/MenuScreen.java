package vorga.phazeclient.implement.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import lombok.Setter;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.animation.implement.LinearAnimation;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.implement.Blur;
import vorga.phazeclient.base.QuickImports;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.base.util.color.ColorUtil;
import vorga.phazeclient.implement.features.modules.client.Theme;
import vorga.phazeclient.implement.menu.components.AbstractComponent;
import vorga.phazeclient.implement.menu.components.implement.other.BackgroundComponent;
import vorga.phazeclient.implement.menu.components.implement.other.CategoryContainerComponent;
import vorga.phazeclient.implement.menu.components.implement.other.ModuleDetailComponent;
import vorga.phazeclient.implement.menu.components.implement.other.ModuleDescriptionComponent;
import vorga.phazeclient.implement.menu.components.implement.other.SearchComponent;
import vorga.phazeclient.implement.menu.components.implement.settings.TextComponent;
import vorga.phazeclient.implement.menu.components.implement.settings.multiselect.MultiSelectComponent;
import vorga.phazeclient.implement.menu.components.implement.settings.select.SelectComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static vorga.phazeclient.api.system.animation.Direction.BACKWARDS;
import static vorga.phazeclient.api.system.animation.Direction.FORWARDS;

@Setter
@Getter
public class MenuScreen extends Screen implements QuickImports {
    private static final double PREFERRED_OVERLAY_SCALE = 2.0D;
    private static final double MIN_OVERLAY_SCALE = 1.0D;
    private static final int DEFAULT_WIDTH = 462;
    private static final int DEFAULT_HEIGHT = 300;
    private static final int MIN_WIDTH = 382;
    private static final int MIN_HEIGHT = 248;
    private static final int SCREEN_MARGIN = 24;
    private static final float CATEGORY_START_X = 112.0F;
    private static final float SEARCH_WIDTH = 82.0F;
    private static final float SEARCH_RIGHT_MARGIN = 10.0F;
    private static final float CATEGORY_SEARCH_GAP = 10.0F;
    private static final float CATEGORY_ROW_Y = 39.0F;
    private static final int HEADER_DRAG_HEIGHT = 34;

    public static MenuScreen INSTANCE = new MenuScreen();
    private final List<AbstractComponent> components = new ArrayList<>();
    private final BackgroundComponent backgroundComponent = new BackgroundComponent();
    private final SearchComponent searchComponent = new SearchComponent();
    private final CategoryContainerComponent categoryContainerComponent = new CategoryContainerComponent();
    private final ModuleDescriptionComponent moduleDescriptionComponent = new ModuleDescriptionComponent();
    private final ModuleDetailComponent moduleDetailComponent = new ModuleDetailComponent();
    public final Animation animation = new LinearAnimation().setMs(200).setValue(1);
    public ModuleCategory category = ModuleCategory.ALL;
    public int x, y, width, height;

    private boolean menuDragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private int customX = -1;
    private int customY = -1;
    private double overlayScaleFactor = PREFERRED_OVERLAY_SCALE;
    private float overlayRenderScale = 1.0F;
    private int overlayViewportWidth = -1;
    private int overlayViewportHeight = -1;

    private final Animation horizontalGuideAnimation = new DecelerateAnimation().setMs(150).setValue(1);
    private final Animation verticalGuideAnimation = new DecelerateAnimation().setMs(150).setValue(1);

    public MenuScreen() {
        super(Text.of("MenuScreen"));
        initialize();
    }

    public static void preload() {
        INSTANCE.updateOverlayMetrics();
        INSTANCE.categoryContainerComponent.initializeCategoryComponents();
    }

    public void initialize() {
        animation.setDirection(FORWARDS);
        categoryContainerComponent.initializeCategoryComponents();

        backgroundComponent.setSearchComponent(searchComponent);

        components.addAll(Arrays.asList(
                backgroundComponent,
                searchComponent,
                categoryContainerComponent,
                moduleDescriptionComponent,
                moduleDetailComponent
        ));
    }

    @Override
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (menuDragging && client != null && client.getWindow() != null
                && GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            menuDragging = false;
        }
        close();
        components.forEach(AbstractComponent::tick);
        super.tick();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateOverlayMetrics();
        Theme.getInstance().applyMenuTheme();
        super.render(context, mouseX, mouseY, delta);
        int overlayMouseX = Math.round(toOverlayCoordinate(mouseX));
        int overlayMouseY = Math.round(toOverlayCoordinate(mouseY));
        width = Math.min(DEFAULT_WIDTH, Math.max(MIN_WIDTH, getOverlayViewportWidth() - SCREEN_MARGIN));
        height = Math.min(DEFAULT_HEIGHT, Math.max(MIN_HEIGHT, getOverlayViewportHeight() - SCREEN_MARGIN));

        if (customX != -1 && customY != -1) {
            x = customX;
            y = customY;
        } else {
            x = getOverlayViewportWidth() / 2 - width / 2;
            y = getOverlayViewportHeight() / 2 - height / 2;
        }

        backgroundComponent.position(x, y).size(width, height);

        float categoryX = x + CATEGORY_START_X;
        float searchX = Math.max(
                x + width - SEARCH_WIDTH - SEARCH_RIGHT_MARGIN,
                categoryX + categoryContainerComponent.getTotalWidth() + CATEGORY_SEARCH_GAP
        );
        categoryContainerComponent.position(categoryX, y + CATEGORY_ROW_Y);
        searchComponent.position(searchX, y + CATEGORY_ROW_Y);

        context.getMatrices().push();
        context.getMatrices().scale(overlayRenderScale, overlayRenderScale, 1.0F);
        float scaleAnimation = getScaleAnimation();
        float alphaAnimation = getAlphaAnimation();
        MathUtil.scale(context.getMatrices(), x + (float) width / 2, y + (float) height / 2, scaleAnimation, () -> renderGuiRegionBlur(context));
        MathUtil.scale(context.getMatrices(), x + (float) width / 2, y + (float) height / 2, scaleAnimation, () -> {
            boolean moduleDetailOpen = moduleDetailComponent.isOpen();
            for (AbstractComponent component : components) {
                if (moduleDetailOpen && (component == searchComponent || component == categoryContainerComponent || component == moduleDescriptionComponent)) {
                    continue;
                }
                component.globalAlpha = alphaAnimation;
                component.render(context, overlayMouseX, overlayMouseY, delta);
            }
            windowManager.render(context, overlayMouseX, overlayMouseY, delta);
        });
        context.getMatrices().pop();
    }

    private void renderGuiRegionBlur(DrawContext context) {
        float blurRadius = Theme.getInstance().getMenuBlurRadius() * getScaleAnimation();
        if (blurRadius <= 0.0F) {
            return;
        }

        Blur.INSTANCE.render(ShapeProperties.create(context.getMatrices(), x, y, width, height)
                .round(8.0F)
                .softness(1.2F)
                .quality(blurRadius * 2.0F)
                .color(0xFFFFFFFF)
                .build());
    }

    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        Window currentWindow = MinecraftClient.getInstance().getWindow();
        int windowWidth = currentWindow != null ? currentWindow.getScaledWidth() : getOverlayViewportWidth();
        int windowHeight = currentWindow != null ? currentWindow.getScaledHeight() : getOverlayViewportHeight();
        int radius = 3;

        boolean nearHorizontalCenter = false;
        boolean nearVerticalCenter = false;

        if (menuDragging) {
            float mouseDragX = (float) (mouseX + dragOffsetX);
            float mouseDragY = (float) (mouseY + dragOffsetY);
            nearHorizontalCenter = Math.abs(mouseDragX - (windowWidth - width) / 2.0F) <= radius;
            nearVerticalCenter = Math.abs(mouseDragY - (windowHeight - height) / 2.0F) <= radius;
        }

        horizontalGuideAnimation.setDirection(nearHorizontalCenter ? FORWARDS : BACKWARDS);
        verticalGuideAnimation.setDirection(nearVerticalCenter ? FORWARDS : BACKWARDS);

        float horizontalAlpha = horizontalGuideAnimation.getOutput().floatValue() * 0.5F;
        float verticalAlpha = verticalGuideAnimation.getOutput().floatValue() * 0.5F;

        RenderSystem.disableScissor();
        if (horizontalAlpha > 0.0F) {
            drawGuideLine(context, windowWidth / 2, 0, 1, windowHeight, horizontalAlpha);
        }

        if (verticalAlpha > 0.0F) {
            drawGuideLine(context, 0, windowHeight / 2, windowWidth, 1, verticalAlpha);
        }

        super.renderBackground(context, mouseX, mouseY, delta);
    }

    protected void renderDarkening(DrawContext context) {
    }

    protected void renderDarkening(DrawContext context, int x, int y, int width, int height) {
    }

    protected void applyBlur() {
    }

    private void drawGuideLine(DrawContext context, float x, float y, float width, float height, float alpha) {
        context.fill((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + width), (int) Math.ceil(y + height), ColorUtil.getText(alpha));
    }

    public void openGui() {
        updateOverlayMetrics();
        animation.setDirection(Direction.FORWARDS);
        categoryContainerComponent.initializeCategoryComponents();
        closeModuleDetail();
        Theme.getInstance().applyMenuTheme();
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(this);
        }
    }

    public void openModuleDetail(vorga.phazeclient.api.feature.module.Module module) {
        moduleDetailComponent.open(module);
    }

    public void closeModuleDetail() {
        moduleDetailComponent.closeDetail();
    }

    public float getScaleAnimation() {
        return animation.getOutput().floatValue();
    }

    public float getAlphaAnimation() {
        float progress = animation.getOutput().floatValue();
        return progress * progress;
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        updateOverlayMetrics();
        double overlayMouseX = toOverlayCoordinate(mouseX);
        double overlayMouseY = toOverlayCoordinate(mouseY);
        boolean insideMenu = MathUtil.isHovered(overlayMouseX, overlayMouseY, x, y, width, height);

        if (shouldStartMenuDrag(overlayMouseX, overlayMouseY, button, insideMenu)) {
            startMenuDrag(mouseX, mouseY);
            return true;
        }

        if (moduleDetailComponent.isOpen()) {
            boolean detailHandled = moduleDetailComponent.mouseClicked(overlayMouseX, overlayMouseY, button);
            boolean backgroundHandled = backgroundComponent.mouseClicked(overlayMouseX, overlayMouseY, button);
            boolean windowHandled = windowManager.mouseClicked(overlayMouseX, overlayMouseY, button);
            SelectComponent.handleGlobalClick(overlayMouseX, overlayMouseY);
            MultiSelectComponent.handleGlobalClick(overlayMouseX, overlayMouseY);

            if (detailHandled || backgroundHandled || windowHandled) {
                return true;
            }
            return true;
        }

        if (!windowManager.mouseClicked(overlayMouseX, overlayMouseY, button)) {
            for (AbstractComponent component : components) {
                if (component.mouseClicked(overlayMouseX, overlayMouseY, button)) {
                    return true;
                }
            }

            SelectComponent.handleGlobalClick(overlayMouseX, overlayMouseY);
            MultiSelectComponent.handleGlobalClick(overlayMouseX, overlayMouseY);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }


    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        updateOverlayMetrics();
        double overlayMouseX = toOverlayCoordinate(mouseX);
        double overlayMouseY = toOverlayCoordinate(mouseY);
        if (menuDragging && button == 0) {
            menuDragging = false;
            return true;
        }

        if (moduleDetailComponent.isOpen()) {
            moduleDetailComponent.mouseReleased(overlayMouseX, overlayMouseY, button);
            return true;
        }

        components.forEach(component -> component.mouseReleased(overlayMouseX, overlayMouseY, button));
        windowManager.mouseReleased(overlayMouseX, overlayMouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        updateOverlayMetrics();
        double overlayMouseX = toOverlayCoordinate(mouseX);
        double overlayMouseY = toOverlayCoordinate(mouseY);
        double overlayDeltaX = toOverlayCoordinate(deltaX);
        double overlayDeltaY = toOverlayCoordinate(deltaY);
        if (menuDragging) {
            float mouseDragX = (float) (overlayMouseX + dragOffsetX);
            float mouseDragY = (float) (overlayMouseY + dragOffsetY);

            int windowWidth = getOverlayViewportWidth();
            int windowHeight = getOverlayViewportHeight();
            customX = (int) Math.max(0, Math.min(mouseDragX, windowWidth - width));
            customY = (int) Math.max(0, Math.min(mouseDragY, windowHeight - height));

            int radius = 3;
            if (Math.abs(mouseDragX - (windowWidth - width) / 2.0f) <= radius) {
                customX = (windowWidth - width) / 2;
            }

            if (Math.abs(mouseDragY - (windowHeight - height) / 2.0f) <= radius) {
                customY = (windowHeight - height) / 2;
            }

            return true;
        }

        if (moduleDetailComponent.isOpen()) {
            moduleDetailComponent.mouseDragged(overlayMouseX, overlayMouseY, button, overlayDeltaX, overlayDeltaY);
            return true;
        }

        if (!windowManager.mouseDragged(overlayMouseX, overlayMouseY, button, overlayDeltaX, overlayDeltaY)) {
            components.forEach(component -> component.mouseDragged(overlayMouseX, overlayMouseY, button, overlayDeltaX, overlayDeltaY));
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        updateOverlayMetrics();
        double overlayMouseX = toOverlayCoordinate(mouseX);
        double overlayMouseY = toOverlayCoordinate(mouseY);
        if (moduleDetailComponent.isOpen()) {
            moduleDetailComponent.mouseScrolled(overlayMouseX, overlayMouseY, vertical);
            return true;
        }

        if (!windowManager.mouseScrolled(overlayMouseX, overlayMouseY, vertical)) {
            components.forEach(component -> component.mouseScrolled(overlayMouseX, overlayMouseY, vertical));
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (backgroundComponent.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (moduleDetailComponent.isOpen() && moduleDetailComponent.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (keyCode == 256 && shouldCloseOnEsc()) {
            if (animation.isDirection(FORWARDS)) {
                animation.setDirection(BACKWARDS);
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && !SearchComponent.typing) {
            SearchComponent.typing = true;
            searchComponent.setText("");
            searchComponent.setCursorPosition(0);
            searchComponent.setPreviousCategory(category != ModuleCategory.SEARCH ? category : ModuleCategory.ALL);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (!windowManager.keyPressed(keyCode, scanCode, modifiers)) {
                components.forEach(component -> component.keyPressed(keyCode, scanCode, modifiers));
            }
            return true;
        }

        if (!windowManager.keyPressed(keyCode, scanCode, modifiers)) {
            components.forEach(component -> component.keyPressed(keyCode, scanCode, modifiers));
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }


    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (backgroundComponent.keyReleased(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (moduleDetailComponent.isOpen() && moduleDetailComponent.keyReleased(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (!windowManager.keyReleased(keyCode, scanCode, modifiers)) {
            components.forEach(component -> component.keyReleased(keyCode, scanCode, modifiers));
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (backgroundComponent.charTyped(chr, modifiers)) {
            return true;
        }

        if (moduleDetailComponent.isOpen() && moduleDetailComponent.charTyped(chr, modifiers)) {
            return true;
        }

        if (!windowManager.charTyped(chr, modifiers)) {
            components.forEach(component -> component.charTyped(chr, modifiers));
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (animation.isFinished(BACKWARDS)) {
            SelectComponent.closeAllDropdowns();
            MultiSelectComponent.closeAllDropdowns();
            super.close();
        }
    }

    private void updateOverlayMetrics() {
        Window currentWindow = MinecraftClient.getInstance().getWindow();
        if (currentWindow == null) {
            overlayScaleFactor = PREFERRED_OVERLAY_SCALE;
            overlayRenderScale = 1.0F;
            overlayViewportWidth = DEFAULT_WIDTH;
            overlayViewportHeight = DEFAULT_HEIGHT;
            return;
        }

        int framebufferWidth = Math.max(1, currentWindow.getFramebufferWidth());
        int framebufferHeight = Math.max(1, currentWindow.getFramebufferHeight());

        double maxScaleByWidth = framebufferWidth / (double) (MIN_WIDTH + SCREEN_MARGIN);
        double maxScaleByHeight = framebufferHeight / (double) (MIN_HEIGHT + SCREEN_MARGIN);
        double safePreferredScale = Math.min(PREFERRED_OVERLAY_SCALE, Math.min(maxScaleByWidth, maxScaleByHeight));

        overlayScaleFactor = Math.max(MIN_OVERLAY_SCALE, safePreferredScale);
        overlayRenderScale = (float) (overlayScaleFactor / currentWindow.getScaleFactor());
        overlayViewportWidth = Math.max(1, (int) Math.floor(framebufferWidth / overlayScaleFactor));
        overlayViewportHeight = Math.max(1, (int) Math.floor(framebufferHeight / overlayScaleFactor));
    }

    public int getOverlayViewportWidth() {
        Window currentWindow = MinecraftClient.getInstance().getWindow();
        return overlayViewportWidth > 0 ? overlayViewportWidth : currentWindow != null ? currentWindow.getScaledWidth() : DEFAULT_WIDTH;
    }

    public int getOverlayViewportHeight() {
        Window currentWindow = MinecraftClient.getInstance().getWindow();
        return overlayViewportHeight > 0 ? overlayViewportHeight : currentWindow != null ? currentWindow.getScaledHeight() : DEFAULT_HEIGHT;
    }

    public int clampOverlayX(double value, float elementWidth) {
        return (int) Math.max(0, Math.min(value, getOverlayViewportWidth() - elementWidth));
    }

    public int clampOverlayY(double value, float elementHeight) {
        return (int) Math.max(0, Math.min(value, getOverlayViewportHeight() - elementHeight));
    }

    public float toOverlayCoordinate(double value) {
        return overlayRenderScale == 0.0F ? (float) value : (float) (value / overlayRenderScale);
    }

    private void startMenuDrag(double mouseX, double mouseY) {
        menuDragging = true;
        dragOffsetX = (int) (x - toOverlayCoordinate(mouseX));
        dragOffsetY = (int) (y - toOverlayCoordinate(mouseY));
    }

    private boolean shouldStartMenuDrag(double overlayMouseX, double overlayMouseY, int button, boolean insideMenu) {
        return button == 0
                && insideMenu
                && !isPointerOverInteractiveElement(overlayMouseX, overlayMouseY);
    }

    private boolean isPointerOverInteractiveElement(double overlayMouseX, double overlayMouseY) {
        if (backgroundComponent.isInteractiveHover(overlayMouseX, overlayMouseY)) {
            return true;
        }

        if (moduleDetailComponent.isOpen()) {
            return moduleDetailComponent.isInteractiveHover(overlayMouseX, overlayMouseY);
        }

        if (searchComponent.isHover(overlayMouseX, overlayMouseY)) {
            return true;
        }

        return categoryContainerComponent.isInteractiveHover(overlayMouseX, overlayMouseY);
    }
}
