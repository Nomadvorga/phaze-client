package vorga.phazeclient.implement.menu.components.implement.settings;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.base.util.other.StringUtil;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color.component.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static vorga.phazeclient.api.system.font.Fonts.Type.*;

public class ColorComponent extends AbstractSettingComponent {
    private final ColorSetting setting;
    private final List<vorga.phazeclient.implement.menu.components.AbstractComponent> components = new ArrayList<>();

    private final HueComponent hueComponent;
    private final SaturationComponent saturationComponent;
    private final AlphaComponent alphaComponent;
    private final ColorEditorComponent colorEditorComponent;
    private final ColorPresetComponent colorPresetComponent;

    public ColorComponent(ColorSetting setting) {
        super(setting);
        this.setting = setting;

        components.addAll(
                Arrays.asList(
                        hueComponent = new HueComponent(setting),
                        saturationComponent = new SaturationComponent(setting),
                        alphaComponent = new AlphaComponent(setting),
                        colorEditorComponent = new ColorEditorComponent(setting),
                        colorPresetComponent = new ColorPresetComponent(setting)
                )
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateVisibilityAnimation();

        if (!setting.isVisible()) {
            return;
        }

        MatrixStack matrix = context.getMatrices();

        // Always render color picker inline without background rectangle
        int pickerHeight = (int) (((ColorPresetComponent) colorPresetComponent.position(x, y))
                .getWindowHeight() - 20);

        alphaComponent.position(x + 5, y + 5);
        hueComponent.position(x + 5, y + 5);
        saturationComponent.position(x + 5, y + 5);
        colorEditorComponent.position(x + 5, y + 5);

        components.forEach(component -> {
            component.globalAlpha = currentAlpha;
            component.render(context, mouseX, mouseY, delta);
        });

        height = pickerHeight + 10;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) {
            return false;
        }

        components.forEach(component -> component.mouseClicked(mouseX, mouseY, button));
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        components.forEach(component -> component.mouseScrolled(mouseX, mouseY, amount));
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        components.forEach(component -> component.mouseDragged(mouseX, mouseY, button, deltaX, deltaY));
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        components.forEach(component -> component.mouseReleased(mouseX, mouseY, button));
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        if (!setting.isVisible()) {
            return false;
        }
        return MathUtil.isHovered(mouseX, mouseY, x, y, width, height);
    }
}
