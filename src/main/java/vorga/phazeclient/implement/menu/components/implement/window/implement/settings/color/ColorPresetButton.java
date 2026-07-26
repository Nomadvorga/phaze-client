package vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color;

import lombok.RequiredArgsConstructor;
import net.minecraft.client.gui.DrawContext;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.menu.components.AbstractComponent;

@RequiredArgsConstructor
public class ColorPresetButton extends AbstractComponent {
    private final ColorSetting setting;
    private final int color;
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        rectangle.render(ShapeProperties.create(context.getMatrices(), x, y, 8, 8).round(2).color(color).build());
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (MathUtil.isHovered(mouseX, mouseY, x, y, 8, 8) && button == 0) {
            setting.setColor(color);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
