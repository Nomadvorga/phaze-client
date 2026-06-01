package vorga.phazeclient.implement.menu.components.implement.window;

import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import vorga.phazeclient.implement.menu.components.AbstractComponent;
import vorga.phazeclient.implement.menu.components.implement.other.ModuleDescriptionComponent;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.components.implement.settings.AbstractSettingComponent;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color.ColorWindow;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.group.GroupWindow;

import java.util.ArrayList;
import java.util.List;

@Getter
public class WindowManager extends AbstractComponent {
    public static final WindowManager INSTANCE = new WindowManager();
    private final List<AbstractWindow> windows = new ArrayList<>();

    public void add(AbstractWindow window) {
        windows.add(window);
    }

    public void delete(AbstractWindow window) {
        window.startCloseAnimation();
    }

    public void closeAll() {
        for (AbstractWindow window : windows) {
            window.startCloseAnimation();
        }
    }

    public void clear() {
        windows.clear();
    }

    public boolean isMouseOverAnyWindow(double mouseX, double mouseY) {
        for (AbstractWindow window : windows) {
            if (window.isHovered(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }


    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        List<AbstractWindow> toRemove = new ArrayList<>();

        windows.forEach(window -> {
            window.render(context, mouseX, mouseY, delta);

            if (window.isCloseAnimationFinished()) {
                toRemove.add(window);
            }
        });
        windows.removeAll(toRemove);

        renderWindowHoverDescriptions(context, mouseX, mouseY, delta);
    }
    
    private void renderWindowHoverDescriptions(DrawContext context, int mouseX, int mouseY, float delta) {
        MenuScreen menuScreen = MenuScreen.INSTANCE;
        ModuleDescriptionComponent descriptionComponent = menuScreen.getModuleDescriptionComponent();

        if (descriptionComponent != null) {
            boolean mouseOverWindow = false;
            for (int i = windows.size() - 1; i >= 0; i--) {
                AbstractWindow window = windows.get(i);
                if (window.isHovered(mouseX, mouseY)) {
                    mouseOverWindow = true;
                    handleWindowHoverDescriptions(window, descriptionComponent, mouseX, mouseY, context, delta);
                    break;
                }
            }

            if (!mouseOverWindow) {
                descriptionComponent.hide();
            }
        }
    }
    
    private void handleWindowHoverDescriptions(AbstractWindow window, ModuleDescriptionComponent descriptionComponent, 
                                            int mouseX, int mouseY, DrawContext context, float delta) {
        if (window instanceof GroupWindow) {
            GroupWindow groupWindow =
                (GroupWindow) window;

            for (AbstractSettingComponent settingComponent : groupWindow.getComponents()) {
                if (settingComponent.isHover(mouseX, mouseY)) {
                    descriptionComponent.setHoveredSetting(settingComponent.getSetting());
                    descriptionComponent.render(context, mouseX, mouseY, delta);
                    return;
                }
            }
        }

        if (window instanceof ColorWindow) {
            ColorWindow colorWindow =
                (ColorWindow) window;

            for (AbstractComponent component : colorWindow.getComponents()) {
                if (component.isHover(mouseX, mouseY)) {
                    String componentDescription = getColorComponentDescription(component);
                    if (componentDescription != null && !componentDescription.isEmpty()) {
                        descriptionComponent.setHoveredSettingDescription(componentDescription);
                        descriptionComponent.render(context, mouseX, mouseY, delta);
                        return;
                    }
                }
            }
        }

        descriptionComponent.hide();
    }
    
    private String getColorComponentDescription(AbstractComponent component) {
        String className = component.getClass().getSimpleName();
        switch (className) {
            case "HueComponent":
                return "setting.color.hue.name";
            case "SaturationComponent":
                return "setting.color.saturation.name";
            case "AlphaComponent":
                return "setting.color.alpha.name";
            case "ColorEditorComponent":
                return "setting.color.editor.name";
            case "ColorPresetComponent":
                return "setting.color.preset.name";
            default:
                return null;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean clickedInsideWindow = false;

        List<AbstractWindow> windowsCopy = new ArrayList<>(windows);

        for (int i = windowsCopy.size() - 1; i >= 0; i--) {
            AbstractWindow window = windowsCopy.get(i);
            if (window.isHovered(mouseX, mouseY)) {
                clickedInsideWindow = true;

                windows.remove(window);
                windows.add(window);

                window.mouseClicked(mouseX, mouseY, button);
                break;
            }
        }

        if (!clickedInsideWindow) {
            for (AbstractWindow window : windows) {
                window.startCloseAnimation();

            }
            return false;
        }

        return true;
    }


    @Override
    public boolean isHover(double mouseX, double mouseY) {
        windows.forEach(window -> window.isHovered(mouseX, mouseY));

        for (AbstractWindow window : windows) {
            if (window.isHover(mouseX, mouseY)) {
                return true;
            }
        }
        return super.isHover(mouseX, mouseY);
    }


    @Override
    public boolean charTyped(char chr, int modifiers) {
        windows.forEach(window -> window.charTyped(chr, modifiers));
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        for (AbstractWindow window : windows) {
            if (window.mouseScrolled(mouseX, mouseY, amount)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        windows.forEach(window -> window.keyPressed(keyCode, scanCode, modifiers));
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        windows.forEach(window -> window.keyReleased(keyCode, scanCode, modifiers));
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        windows.forEach(window -> window.mouseReleased(mouseX, mouseY, button));
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
