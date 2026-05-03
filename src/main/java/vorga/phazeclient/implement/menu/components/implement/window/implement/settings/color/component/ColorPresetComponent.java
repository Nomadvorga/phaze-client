package vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color.component;

import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.implement.menu.components.AbstractComponent;

@Getter
public class ColorPresetComponent extends AbstractComponent {
    private final ColorSetting setting;
    private float windowHeight;

    public ColorPresetComponent(ColorSetting setting) {
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Disabled - removed prepared colors
        windowHeight = 132;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
