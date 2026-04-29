package vorga.phazeclient.implement.menu.components.implement.other;

import lombok.Setter;
import lombok.experimental.Accessors;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.components.AbstractComponent;
import vorga.phazeclient.implement.menu.components.implement.category.CategoryComponent;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

@Setter
@Accessors(chain = true)
public class CategoryContainerComponent extends AbstractComponent {
    private static final float CHIP_TEXT_SIZE = 6.25F;
    private static final float CHIP_HEIGHT = 14.0F;
    private static final float CHIP_HORIZONTAL_PADDING = 16.0F;
    private static final float CHIP_GAP = 4.0F;
    private static final List<ModuleCategory> CHIP_CATEGORIES = List.of(
            ModuleCategory.VISUALS,
            ModuleCategory.HUD,
            ModuleCategory.WORLD,
            ModuleCategory.OTHER
    );

    private final List<CategoryComponent> categoryComponents = new ArrayList<>();

    public void initializeCategoryComponents() {
        categoryComponents.clear();
        CHIP_CATEGORIES.stream().map(CategoryComponent::new).forEach(categoryComponents::add);
    }

    public float getTotalWidth() {
        float total = 0.0F;
        for (int i = 0; i < categoryComponents.size(); i++) {
            String label = categoryComponents.get(i).getTabLabel();
            total += MsdfFonts.bold().getWidth(label, CHIP_TEXT_SIZE) + CHIP_HORIZONTAL_PADDING;
            if (i < categoryComponents.size() - 1) {
                total += CHIP_GAP;
            }
        }
        return total;
    }


    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float offset = 0f;
        for (CategoryComponent component : categoryComponents) {
            String label = component.getTabLabel();
            float chipWidth = MsdfFonts.bold().getWidth(label, CHIP_TEXT_SIZE) + CHIP_HORIZONTAL_PADDING;

            component.x = x + offset - 5.0F;
            component.y = y;
            component.width = chipWidth;
            component.height = CHIP_HEIGHT;
            component.globalAlpha = globalAlpha;

            component.render(context, mouseX, mouseY, delta);
            offset += chipWidth + CHIP_GAP;
        }
    }

    @Override
    public void tick() {
        categoryComponents.forEach(AbstractComponent::tick);
        super.tick();
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return categoryComponents.stream()
                .anyMatch(categoryComponent -> categoryComponent.mouseClicked(mouseX, mouseY, button))
                || super.mouseClicked(mouseX, mouseY, button);
    }


    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        categoryComponents.forEach(categoryComponent -> categoryComponent.mouseReleased(mouseX, mouseY, button));
        return super.mouseReleased(mouseX, mouseY, button);
    }


    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        categoryComponents.forEach(categoryComponent -> categoryComponent.mouseDragged(mouseX, mouseY, button, deltaX, deltaY));
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        categoryComponents.forEach(categoryComponent -> categoryComponent.mouseScrolled(mouseX, mouseY, amount));
        return super.mouseScrolled(mouseX, mouseY, amount);
    }


    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        categoryComponents.forEach(categoryComponent -> categoryComponent.keyPressed(keyCode, scanCode, modifiers));
        return super.keyPressed(keyCode, scanCode, modifiers);
    }


    @Override
    public boolean charTyped(char chr, int modifiers) {
        categoryComponents.forEach(categoryComponent -> categoryComponent.charTyped(chr, modifiers));
        return super.charTyped(chr, modifiers);
    }

    public boolean isInteractiveHover(double mouseX, double mouseY) {
        return categoryComponents.stream().anyMatch(categoryComponent -> categoryComponent.isHover(mouseX, mouseY));
    }
}
