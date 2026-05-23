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
    private final vorga.phazeclient.implement.menu.components.implement.other.ConfigsViewComponent configsView =
            new vorga.phazeclient.implement.menu.components.implement.other.ConfigsViewComponent();
    private final vorga.phazeclient.implement.menu.components.implement.other.ConfigShareModalComponent configShareModal =
            new vorga.phazeclient.implement.menu.components.implement.other.ConfigShareModalComponent();
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

        // Open a BatchedRectangle scope for the entire component +
        // window-manager render pass. While this scope is active,
        // every {@code rectangle.render(props)} from any sub-component
        // (cards, settings, color pickers, search bar, sidebar, etc.)
        // routes into a shared BufferBuilder instead of issuing its
        // own draw call. The flush hooks installed in MsdfRenderer,
        // Image, FontRenderer.drawGlyphs, Blur and ScissorManager
        // drain the queue at every text/image/blur/scissor boundary
        // so the on-screen draw order is identical to the legacy
        // eager path - cards still appear under text, hover overlays
        // still appear over base fills, scissor clipping is honored,
        // etc. The outermost endScope() at the bottom of this lambda
        // performs the final drain so nothing leaks past the menu's
        // matrix-pop into whatever renders next on the frame.
        vorga.phazeclient.api.system.shape.batched.BatchedRectangle.beginScope();
        try {
            MathUtil.scale(context.getMatrices(), x + (float) width / 2, y + (float) height / 2, scaleAnimation, () -> {
                boolean moduleDetailOpen = moduleDetailComponent.isOpen();
                boolean configsViewOpen = configsView.isOpen();
                for (AbstractComponent component : components) {
                    if ((moduleDetailOpen || configsViewOpen) && (component == searchComponent || component == categoryContainerComponent || component == moduleDescriptionComponent)) {
                        continue;
                    }
                    component.globalAlpha = alphaAnimation;
                    component.render(context, overlayMouseX, overlayMouseY, delta);
                }
                if (configsViewOpen) {
                    configsView.position(x, y).size(width, height);
                    configsView.globalAlpha = alphaAnimation;
                    configsView.render(context, overlayMouseX, overlayMouseY, delta);
                }
                windowManager.render(context, overlayMouseX, overlayMouseY, delta);
                // ConfigShare modal renders LAST so it floats above
                // the windowManager output (window dialogs, etc.) and
                // its dim layer covers the entire menu canvas. We
                // re-anchor its bounds to the menu rect on every
                // frame so the centred modal stays centred when the
                // user resizes the window.
                configShareModal.position(x, y).size(width, height);
                configShareModal.globalAlpha = alphaAnimation;
                configShareModal.render(context, overlayMouseX, overlayMouseY, delta);
            });
        } finally {
            vorga.phazeclient.api.system.shape.batched.BatchedRectangle.endScope();
        }
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

        float horizontalAlpha = horizontalGuideAnimation.getOutputFloat() * 0.5F;
        float verticalAlpha = verticalGuideAnimation.getOutputFloat() * 0.5F;

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
        // Reset to MODS / ALL on every open. Without this, closing the
        // GUI while in CONFIGS or SETTINGS would re-open the menu on
        // the same view next time, which reads as "the menu doesn't
        // remember the home tab".
        closeConfigsView();
        category = ModuleCategory.ALL;
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

    public void openConfigShareModal() {
        configShareModal.position(x, y).size(width, height);
        configShareModal.openShare(null);
    }

    public void openConfigShareModal(String configName) {
        configShareModal.position(x, y).size(width, height);
        configShareModal.openShare(configName);
    }

    public void openConfigRenameModal(String configName, Runnable onRenamed) {
        configShareModal.position(x, y).size(width, height);
        configShareModal.openRename(configName, onRenamed);
    }

    /**
     * Opens the modal in IMPORT mode so the user can paste a
     * share-key code and download a config from the server. Refreshes
     * the configs view list once import completes so the new entry
     * shows up without a manual reload.
     */
    public void openConfigImportModal() {
        configShareModal.position(x, y).size(width, height);
        configShareModal.openImport(configsView::refreshAfterImport);
    }

    public boolean isConfigShareModalOpen() {
        return configShareModal.isOpen();
    }

    public void openConfigsView() {
        closeModuleDetail();
        configsView.position(x, y).size(width, height);
        configsView.open();
    }

    public void closeConfigsView() {
        configsView.close();
    }

    public boolean isConfigsViewOpen() {
        return configsView.isOpen();
    }

    public float getScaleAnimation() {
        return animation.getOutputFloat();
    }

    public float getAlphaAnimation() {
        float progress = animation.getOutputFloat();
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

        // ConfigShare modal claims input priority while open so clicks
        // on the dim backdrop can close it and clicks on the modal's
        // own widgets aren't intercepted by the search bar / category
        // grid that lives underneath.
        if (configShareModal.isOpen()) {
            configShareModal.position(x, y).size(width, height);
            configShareModal.mouseClicked(overlayMouseX, overlayMouseY, button);
            return true;
        }

        // Configs view (CONFIGS top-tab content) sits between the
        // background sidebar / footer and the rest of the menu.
        // Route clicks here first so kebab pop-ups and row activates
        // don't fall through to the category grid.
        if (configsView.isOpen()) {
            configsView.position(x, y).size(width, height);
            if (configsView.mouseClicked(overlayMouseX, overlayMouseY, button)) {
                return true;
            }
            // Forward to BackgroundComponent so the sidebar (config
            // list, NEW CONFIG button, top tabs) is still clickable
            // while the configs view is open.
            if (backgroundComponent.mouseClicked(overlayMouseX, overlayMouseY, button)) {
                return true;
            }
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
        // Dynamic cursor: announce the wheel event to the cursor
        // manager so it can flip the OS pointer to the vertical/
        // horizontal resize shape for the duration of the spin. The
        // global Mouse.onMouseScroll mixin would normally cover this,
        // but calling directly from the screen path is the most
        // reliable trigger - it runs on the render thread inside the
        // active screen's scroll handler, so the resize shape is
        // armed before the next render frame's endFrame() commits it.
        vorga.phazeclient.api.system.cursor.CursorManager.notifyScroll(horizontal, vertical);

        updateOverlayMetrics();
        double overlayMouseX = toOverlayCoordinate(mouseX);
        double overlayMouseY = toOverlayCoordinate(mouseY);
        if (moduleDetailComponent.isOpen()) {
            moduleDetailComponent.mouseScrolled(overlayMouseX, overlayMouseY, vertical);
            return true;
        }

        // CONFIGS view owns the scroll wheel while open so its row
        // list can pan past the visible window. Without this branch
        // the wheel went to the category grid that sits underneath
        // (which is hidden but still in the components list), so the
        // user couldn't reach configs that overflow the bottom edge.
        if (configsView.isOpen()) {
            configsView.position(x, y).size(width, height);
            configsView.mouseScrolled(overlayMouseX, overlayMouseY, vertical);
            return true;
        }

        if (!windowManager.mouseScrolled(overlayMouseX, overlayMouseY, vertical)) {
            components.forEach(component -> component.mouseScrolled(overlayMouseX, overlayMouseY, vertical));
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Modal owns key input while open so Esc closes it and the
        // text field gets every keystroke before menu hotkeys.
        if (configShareModal.isOpen() && configShareModal.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
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
        if (configShareModal.isOpen() && configShareModal.charTyped(chr, modifiers)) {
            return true;
        }
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
            // Clear the search bar so re-opening the GUI starts on
            // a blank query. Without this, a user who typed something,
            // closed the menu, and re-opened it would still see the
            // old query (and an active SEARCH category), which reads
            // as "the menu remembers stale typing across sessions".
            searchComponent.setText("");
            searchComponent.setCursorPosition(0);
            SearchComponent.typing = false;
            if (category == ModuleCategory.SEARCH) {
                category = searchComponent.getPreviousCategory();
            }
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
        // While a kebab popup or the share/rename modal is up, the
        // first click is meant to dismiss / interact with that
        // overlay - it must NOT also start a menu drag, otherwise
        // closing the popup also yanks the entire window across the
        // screen.
        if (configShareModal.isOpen() || configsView.isPopupOpen()) {
            return false;
        }
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
