package vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color.component;

import lombok.RequiredArgsConstructor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.menu.components.AbstractComponent;

import static net.minecraft.util.math.MathHelper.clamp;

@RequiredArgsConstructor
public class SaturationComponent extends AbstractComponent {
    private final ColorSetting setting;
    private boolean saturationDragging;

    private float X, Y, W, H;

    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrix = context.getMatrices();

        X = x + 6;
        Y = y + 73.5F;
        W = 138;
        H = 4;

        float clampedX = clamp(X + W * setting.getHue(), X, X + W - 4);
        float min = clamp((mouseX - X) / W, 0, 1);

        image.setTexture("textures/color_picker/hue.png").render(ShapeProperties.create(matrix, X, Y + 0.5, W, H - 1).build());

        rectangle.render(ShapeProperties.create(matrix, clampedX, Y, H, H)
                .round(H / 2).thickness(3).color(0x00FFFFFF).outlineColor(0xFFFFFFFF).build());

        if (saturationDragging) {
            setting.setHue(min);
        }
    }

    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        saturationDragging = button == 0 && MathUtil.isHovered(mouseX, mouseY, X, Y, W, H);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        saturationDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
