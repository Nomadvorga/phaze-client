package vorga.phazeclient.implement.menu.components.implement.other;

import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.AbstractComponent;

import static vorga.phazeclient.api.system.font.Fonts.Type.INTER_BOLD;

@Setter
@Accessors(chain = true)
public class ButtonComponent extends AbstractComponent {
    private String text;
    private Runnable runnable;
    private int color = MenuStyle.CHIP_ACTIVE;

    public float measureWidth() {
        return Fonts.getSize(12).getStringWidth(text) + 16;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrix = context.getMatrices();
        var font = Fonts.getSize(12, INTER_BOLD);

        width = measureWidth();
        height = 12;

        rectangle.render(ShapeProperties.create(matrix, x, y, width, height)
                .round(2)
                .thickness(1.1F)
                .outlineColor(MenuStyle.BORDER)
                .color(color)
                .build());

        font.drawString(matrix, text, centeredTextX(font, text, x, width), centeredTextY(font, text, y, height), MenuStyle.TEXT_PRIMARY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (MathUtil.isHovered(mouseX, mouseY, x, y, width, height) && button == 0) {
            playButtonClickSound();
            runnable.run();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
