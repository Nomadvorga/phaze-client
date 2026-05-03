package vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color.component;

import lombok.RequiredArgsConstructor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.AbstractComponent;

import java.awt.*;

import static net.minecraft.util.math.MathHelper.clamp;

@RequiredArgsConstructor
public class HueComponent extends AbstractComponent {
    private final ColorSetting setting;
    private boolean hueDragging;

    private float X, Y, W, H;

    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrix = context.getMatrices();

        X = x + 6;
        Y = y + 10.5F;
        W = 138;
        H = 50;

        int[] color = {
                0xFF000000,
                0xFFFFFFFF,
                0xFF000000,
                Color.HSBtoRGB(setting.getHue(), 1, 1)
        };

        rectangle.render(ShapeProperties.create(matrix, X, Y, W, H)
                .round(2)
                .thickness(2)
                .outlineColor(MenuStyle.BORDER_LIGHT)
                .color(color)
                .build()
        );

        float clampedX = clamp(X + W * setting.getSaturation(), X, X + W - 5);
        float clampedY = clamp(Y + H * (1 - setting.getBrightness()), Y, Y + H - 5);

        rectangle.render(ShapeProperties.create(matrix, clampedX, clampedY, 5, 5)
                .round(2.5F)
                .softness(1)
                .thickness(3)
                .color(0x00FFFFFF)
                .outlineColor(0xFFFFFFFF)
                .build()
        );

        float min = clamp((mouseX - X) / W, 0, 1);

        if (hueDragging) {
            setting.setBrightness(clamp(1 - ((mouseY - Y) / H), 0, 1));
            setting.setSaturation(min);
        }
    }

    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        hueDragging = button == 0 && MathUtil.isHovered(mouseX, mouseY, X, Y, W, H);
        return super.mouseClicked(mouseX, mouseY, button);
    }


    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        hueDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
