package vorga.phazeclient.implement.menu.components.implement.category;

import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.feature.module.setting.implement.GroupSetting;
import vorga.phazeclient.api.feature.module.setting.implement.MultiSelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.base.util.render.ScissorManager;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.AbstractComponent;
import vorga.phazeclient.implement.menu.components.implement.module.ModuleComponent;
import vorga.phazeclient.implement.menu.components.implement.other.ModuleDescriptionComponent;
import vorga.phazeclient.implement.menu.components.implement.settings.AbstractSettingComponent;

import java.util.*;

public class CategoryComponent extends AbstractComponent {
    private static final float CHIP_TEXT_SIZE = 6.25F;
    private static final int COLUMN_COUNT = 3;
    private static final int COLUMN_GAP = 6;
    private static final int ROW_GAP = 8;
    private static final float SIDEBAR_WIDTH = 100f;
    private static final float GRID_TOP = 55f;
    private static final float GRID_BOTTOM_PADDING = 6f;

    private final List<ModuleComponent> moduleComponents = new ArrayList<>();

    @Getter
    private final ModuleCategory category;

    private final Animation hoverAnimation = new DecelerateAnimation().setMs(180).setValue(1);
    private final Animation selectionAnimation = new DecelerateAnimation().setMs(220).setValue(1);

    private final Map<ModuleComponent, Integer> assignedColumns = new HashMap<>();
    private final List<ModuleComponent> visibleComponents = new ArrayList<>();
    /**
     * Sum of (componentHeight + ROW_GAP) per column, captured in
     * {@link #assignColumns()}. Used as the seed array for the render-loop
     * offsets and as the source for {@link #calculateMaxScrollHeight()},
     * eliminating two O(n) iterations over {@link #visibleComponents} every
     * frame. Invalidated whenever assignColumns() runs (category/search/
     * visibility change), which is the only time per-card heights or column
     * mapping can shift.
     */
    private final int[] cachedColumnHeights = new int[COLUMN_COUNT];
    private int cachedMaxColumnHeight = 0;
    private ModuleCategory lastCategory = null;
    private String lastSearchText = "";
    private boolean visibleCacheDirty = true;
    private boolean isInitialized = false;

    private int mouseX = 0;
    private int mouseY = 0;

    public CategoryComponent(ModuleCategory category) {
        this.category = category;
        initialize();
    }

    public String getTabLabel() {
        return switch (category) {
            case ALL -> "ALL";
            case UTILITIES -> "UTILITIES";
            case HUD -> "HUD";
            case OTHER -> "OTHER";
            default -> category.name();
        };
    }

    private void initialize() {
        List<Module> modules = new ArrayList<>(Main.getInstance().getModuleProvider().getModules());
        // Render iterates visibleComponents from last to first, but the offset
        // arithmetic puts the FIRST list element at the visual top of its
        // column. Sort A..Z so the menu reads A..Z top-down.
        modules.sort((a, b) -> a.getVisibleName().compareToIgnoreCase(b.getVisibleName()));

        for (Module module : modules) {
            if (!module.isVisible()) {
                continue;
            }

            moduleComponents.add(new ModuleComponent(module));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MenuScreen menuScreen = MenuScreen.INSTANCE;

        this.mouseX = mouseX;
        this.mouseY = mouseY;

        drawCategoryTab(context, context.getMatrices(), mouseX, mouseY);

        ModuleCategory currentCategory = menuScreen.getCategory();
        if (!shouldRenderContentForCurrentCategory(currentCategory)) {
            return;
        }

        String currentSearchText = menuScreen.getSearchComponent().getText().toLowerCase();
        refreshVisibleCache(currentCategory, currentSearchText);

        if (!isInitialized && currentCategory == category) {
            isInitialized = true;
            lastCategory = currentCategory;
            lastSearchText = currentSearchText;
            assignColumns();
        } else if (!currentCategory.equals(lastCategory) || !currentSearchText.equals(lastSearchText) || visibleCacheDirty) {
            lastCategory = currentCategory;
            lastSearchText = currentSearchText;
            assignColumns();
        }
        visibleCacheDirty = false;

        float gridX = menuScreen.x + SIDEBAR_WIDTH + 6f;
        float gridY = menuScreen.y + GRID_TOP;
        float gridWidth = menuScreen.width - SIDEBAR_WIDTH - 12f;
        float gridHeight = menuScreen.height - GRID_TOP - GRID_BOTTOM_PADDING;
        float columnWidth = (gridWidth - COLUMN_GAP * (COLUMN_COUNT - 1)) / COLUMN_COUNT;

        Matrix4f positionMatrix = context.getMatrices().peek().getPositionMatrix();
        ScissorManager scissorManager = Main.getInstance().getScissorManager();
        scissorManager.push(positionMatrix, gridX, gridY, gridWidth, gridHeight);

        // Working copy of the cached column totals; the render loop
        // decrements per-column as it walks cards from the bottom of each
        // stack upwards. Cached array is preserved for the next frame.
        int[] offsets = new int[COLUMN_COUNT];
        System.arraycopy(cachedColumnHeights, 0, offsets, 0, COLUMN_COUNT);
        float gridBottom = gridY + gridHeight;
        for (int i = visibleComponents.size() - 1; i >= 0; i--) {
            ModuleComponent component = visibleComponents.get(i);
            int componentHeight = component.getComponentHeight() + ROW_GAP;
            int column = assignedColumns.getOrDefault(component, 0);

            component.x = gridX + (column * (columnWidth + COLUMN_GAP));
            component.y = (float) (gridY + ROW_GAP + offsets[column] - componentHeight + smoothedScroll);
            component.width = columnWidth;
            // Keep height in sync so mouseClicked / isHover work even for
            // cards we are about to cull (preserves the contract that
            // component bounds are always valid after CategoryComponent.render).
            component.height = component.getComponentHeight();

            float cardTop = component.y;
            float cardBottom = cardTop + component.height;
            boolean offscreen = cardBottom < gridY || cardTop > gridBottom;
            if (!offscreen) {
                component.globalAlpha = globalAlpha;
                component.render(context, mouseX, mouseY, delta);
            }
            offsets[column] -= componentHeight;
        }

        scissorManager.pop();
        renderTooltips(context);

        int maxScrollHeight = calculateMaxScrollHeight();
        scroll = MathHelper.clamp(scroll, -maxScrollHeight, 0);
        smoothedScroll = MathUtil.interpolateSmooth(2, smoothedScroll, scroll);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        MenuScreen menuScreen = MenuScreen.INSTANCE;

        if (MathUtil.isHovered(mouseX, mouseY, x, y, width, height) && button == 0) {
            playButtonClickSound();
            menuScreen.closeModuleDetail();
            MenuScreen.INSTANCE.setCategory(category);
            return true;
        }

        ModuleCategory currentCategory = menuScreen.getCategory();
        if (!shouldRenderContentForCurrentCategory(currentCategory)) {
            return false;
        }

        float gridX = menuScreen.x + SIDEBAR_WIDTH + 6f;
        float gridY = menuScreen.y + GRID_TOP;
        float gridWidth = menuScreen.width - SIDEBAR_WIDTH - 12f;
        float gridHeight = menuScreen.height - GRID_TOP - GRID_BOTTOM_PADDING;
        if (MathUtil.isHovered(mouseX, mouseY, gridX, gridY, gridWidth, gridHeight)) {
            // Single-pass: was previously iterating visibleComponents twice
            // (anyMatch -> for-loop). Each isHover() call is a bounds check.
            boolean anyHovered = false;
            for (ModuleComponent moduleComponent : visibleComponents) {
                if (moduleComponent.isHover(mouseX, mouseY)) {
                    anyHovered = true;
                    if (moduleComponent.mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                }
            }
            if (anyHovered) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        // Removed redundant forEach(isHover) call whose result was discarded.
        for (ModuleComponent moduleComponent : visibleComponents) {
            if (moduleComponent.isHover(mouseX, mouseY)) {
                return true;
            }
        }
        return super.isHover(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        visibleComponents.forEach(moduleComponent -> moduleComponent.mouseReleased(mouseX, mouseY, button));
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        MenuScreen menuScreen = MenuScreen.INSTANCE;
        float gridX = menuScreen.x + SIDEBAR_WIDTH + 6f;
        float gridY = menuScreen.y + GRID_TOP;
        float gridWidth = menuScreen.width - SIDEBAR_WIDTH - 12f;
        float gridHeight = menuScreen.height - GRID_TOP - GRID_BOTTOM_PADDING;

        if (MathUtil.isHovered(mouseX, mouseY, gridX, gridY, gridWidth, gridHeight)) {
            scroll += amount * 20;
        }

        visibleComponents.forEach(moduleComponent -> moduleComponent.mouseScrolled(mouseX, mouseY, amount));
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        visibleComponents.forEach(moduleComponent -> moduleComponent.keyPressed(keyCode, scanCode, modifiers));
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        visibleComponents.forEach(moduleComponent -> moduleComponent.charTyped(chr, modifiers));
        return super.charTyped(chr, modifiers);
    }

    private void drawCategoryTab(DrawContext context, MatrixStack matrix, int mouseX, int mouseY) {
        boolean isSelected = MenuScreen.INSTANCE.getCategory() == category;
        selectionAnimation.setDirection(isSelected ? Direction.FORWARDS : Direction.BACKWARDS);

        boolean isHovered = MathUtil.isHovered(mouseX, mouseY, x, y, width, height);
        hoverAnimation.setDirection(isHovered ? Direction.FORWARDS : Direction.BACKWARDS);

        // Dynamic cursor: category chips behave like buttons - request
        // the hand pointer while hovered. The end-of-frame commit lives
        // in ScreenCursorMixin.
        if (isHovered) {
            vorga.phazeclient.api.system.cursor.CursorManager.requestHand();
        }

        float hoverProgress = hoverAnimation.getOutputFloat();
        float selectionProgress = selectionAnimation.getOutputFloat();

        String label = getTabLabel();
        int accentColor = label.equals("NEW") ? MenuStyle.CHIP_NEW : MenuStyle.CHIP_ACTIVE;
        int baseChipColor = MenuStyle.mix(MenuStyle.PANEL_CHIP, accentColor, 0.24F);
        int hoverChipColor = MenuStyle.mix(baseChipColor, accentColor, 0.20F);
        int selectedChipColor = MenuStyle.mix(baseChipColor, accentColor, 0.44F);
        int chipColor = MenuStyle.mix(MenuStyle.mix(baseChipColor, hoverChipColor, hoverProgress), selectedChipColor, selectionProgress);

        float textEmphasis = Math.min(1.0F, 0.16F + hoverProgress * 0.18F + selectionProgress * 0.76F);
        int textColor = MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, textEmphasis);

        rectangle.render(ShapeProperties.create(matrix, x, y, width, height)
                .round(2).thickness(1.2F).outlineColor(applyGlobalAlpha(MenuStyle.BORDER)).color(applyGlobalAlpha(chipColor))
                .build());

        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                label,
                CHIP_TEXT_SIZE,
                applyGlobalAlpha(textColor),
                matrix.peek().getPositionMatrix(),
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), label, CHIP_TEXT_SIZE, x, width),
                MenuStyle.centerMsdfTextY(CHIP_TEXT_SIZE, y, height),
                0.0F
        );
    }

    private boolean shouldRenderContentForCurrentCategory(ModuleCategory currentCategory) {
        if (currentCategory == ModuleCategory.SEARCH) {
            // Every CategoryComponent ({ALL, HUD, UTILITIES, OTHER}) shares
            // the SAME backing module list (initialize() does not filter by
            // this.category - see line 92-98), so letting all four render
            // in SEARCH mode would draw every matching card 4x at the same
            // grid coordinates. The visible result was text glyphs blending
            // over themselves (apparent "bolding"/thickening) and the card
            // outline being stroked 4x (apparent widening). Pin search-mode
            // rendering to a single CategoryComponent (ALL) so each matching
            // card is drawn exactly once.
            return category == ModuleCategory.ALL;
        }
        return category == currentCategory;
    }

    private void assignColumns() {
        assignedColumns.clear();
        int[] heights = new int[COLUMN_COUNT];

        for (int i = visibleComponents.size() - 1; i >= 0; i--) {
            ModuleComponent component = visibleComponents.get(i);
            int shortestColumn = findShortestColumn(heights);
            assignedColumns.put(component, shortestColumn);
            heights[shortestColumn] += component.getComponentHeight() + ROW_GAP;
        }

        // Cache totals for reuse in render() and calculateMaxScrollHeight()
        // so we don't iterate visibleComponents twice every frame.
        System.arraycopy(heights, 0, cachedColumnHeights, 0, COLUMN_COUNT);
        int max = 0;
        for (int h : heights) {
            if (h > max) max = h;
        }
        cachedMaxColumnHeight = max;
    }

    private int findShortestColumn(int[] heights) {
        int shortest = 0;
        for (int i = 1; i < heights.length; i++) {
            if (heights[i] < heights[shortest]) {
                shortest = i;
            }
        }
        return shortest;
    }

    private int calculateMaxScrollHeight() {
        MenuScreen menuScreen = MenuScreen.INSTANCE;
        int visibleHeight = Math.round(menuScreen.height - GRID_TOP - GRID_BOTTOM_PADDING);
        int maxScroll = cachedMaxColumnHeight - visibleHeight;
        return Math.max(0, maxScroll);
    }

    private boolean shouldRenderComponent(ModuleComponent component) {
        MenuScreen menuScreen = MenuScreen.INSTANCE;
        ModuleCategory moduleCategory = component.getModule().getCategory();
        ModuleCategory secondaryCategory = component.getModule().getSecondaryCategory();
        ModuleCategory currentCategory = menuScreen.getCategory();
        String searchText = menuScreen.getSearchComponent().getText().toLowerCase();

        if (!component.getModule().isVisible()) {
            return false;
        }

        if (currentCategory == ModuleCategory.SEARCH) {
            if (searchText.equalsIgnoreCase("")) {
                return false;
            }

            String moduleName = component.getModule().getLocalizedName().toLowerCase();
            if (moduleName.contains(searchText)) {
                return true;
            }

            return component.getModule().settings().stream().anyMatch(setting ->
                    searchInSetting(setting, searchText)
            );
        }

        if (currentCategory == ModuleCategory.ALL) {
            return true; // Show all modules in ALL category
        }

        return moduleCategory.equals(currentCategory) ||
                (secondaryCategory != null && secondaryCategory.equals(currentCategory));
    }

    private void refreshVisibleCache(ModuleCategory currentCategory, String currentSearchText) {
        if (!visibleCacheDirty
                && currentCategory == lastCategory
                && Objects.equals(currentSearchText, lastSearchText)) {
            return;
        }

        visibleComponents.clear();
        for (ModuleComponent moduleComponent : moduleComponents) {
            if (shouldRenderComponent(moduleComponent)) {
                visibleComponents.add(moduleComponent);
            }
        }
        visibleCacheDirty = true;
    }

    private boolean searchInSetting(Setting setting, String searchText) {
        if (setting.getLocalizedName().toLowerCase().contains(searchText)) {
            return true;
        }

        if (setting.getLocalizedDescription() != null && setting.getLocalizedDescription().toLowerCase().contains(searchText)) {
            return true;
        }

        if (setting instanceof SelectSetting selectSetting && selectSetting.getList() != null) {
            return selectSetting.getList().stream().anyMatch(option -> option.toLowerCase().contains(searchText));
        }

        if (setting instanceof MultiSelectSetting multiSelectSetting && multiSelectSetting.getList() != null) {
            return multiSelectSetting.getList().stream().anyMatch(option -> option.toLowerCase().contains(searchText));
        }

        if (setting instanceof GroupSetting groupSetting) {
            return groupSetting.getSubSettings().stream().anyMatch(subSetting ->
                    searchInSetting(subSetting, searchText)
            );
        }

        return false;
    }

    private void renderTooltips(DrawContext context) {
        ModuleDescriptionComponent descriptionComponent = MenuScreen.INSTANCE.getModuleDescriptionComponent();
        if (descriptionComponent != null) {
            descriptionComponent.hide();
        }
    }

    private int countAllSettings(Module module) {
        int count = 0;
        for (Setting setting : module.settings()) {
            count++;

            if (setting instanceof GroupSetting groupSetting) {
                count += countSettingsInGroup(groupSetting);
            }
        }
        return count;
    }

    private int countSettingsInGroup(GroupSetting group) {
        int count = 0;
        for (Setting setting : group.getSubSettings()) {
            count++;

            if (setting instanceof GroupSetting nestedGroup) {
                count += countSettingsInGroup(nestedGroup);
            }
        }
        return count;
    }
}
