package vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color.component;

import lombok.RequiredArgsConstructor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.AbstractComponent;

import static net.minecraft.util.math.MathHelper.clamp;

@RequiredArgsConstructor
public class AlphaComponent extends AbstractComponent {
    private final ColorSetting setting;
    private boolean alphaDragging;

    private float X, Y, W, H;

    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrix = context.getMatrices();

        X = x + 6;
        Y = y + 79.5F;
        W = 138;
        H = 6;

        float clampedX = clamp(X + W * setting.getAlpha(), X, X + W - 4);
        float min = clamp((mouseX - X) / W, 0, 1);

        image.setTexture("textures/color_picker/alpha.png").render(ShapeProperties.create(matrix, X, Y, W, H).round(3).thickness(1.5F).outlineColor(MenuStyle.BORDER_LIGHT).build());

        rectangle.render(ShapeProperties.create(matrix, X, Y - 0.2, W + 0.5, H)
                .round(1.5F).color(0x80000000, 0x8000000, setting.getColorWithAlpha(), setting.getColorWithAlpha()).build());

        rectangle.render(ShapeProperties.create(matrix, clampedX, Y, H, H)
                .round(H / 2).thickness(3).color(0x00FFFFFF).outlineColor(0xFFFFFFFF).build());

        if (alphaDragging) {
            setting.setAlpha(min);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        alphaDragging = button == 0 && MathUtil.isHovered(mouseX, mouseY, X, Y, W, H);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        alphaDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
