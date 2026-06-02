package vorga.phazeclient.implement.menu.components.implement.other;

import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.AbstractComponent;

@Setter
@Accessors(chain = true)
public class ButtonComponent extends AbstractComponent {
    private static final float TEXT_SIZE = 6.7F;
    private static final float BUTTON_HEIGHT = 14.0F;

    private String text;
    private Runnable runnable;
    private int color = MenuStyle.CHIP_ACTIVE;

    public float measureWidth() {
        String resolved = text == null ? "" : text;
        return MsdfFonts.bold().getWidth(resolved, TEXT_SIZE) + 18.0F;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrix = context.getMatrices();
        String resolved = text == null ? "" : text;
        boolean hovered = MathUtil.isHovered(mouseX, mouseY, x, y, width, height);

        width = measureWidth();
        height = BUTTON_HEIGHT;

        int outline = applyGlobalAlpha(MenuStyle.mix(MenuStyle.BORDER, 0xFFFFFFFF, hovered ? 0.12F : 0.04F));
        int fill = applyGlobalAlpha(MenuStyle.mix(color, 0xFFFFFFFF, hovered ? 0.08F : 0.0F));
        int textColor = applyGlobalAlpha(MenuStyle.TEXT_PRIMARY);
        float scale = 0.9F + globalAlpha * 0.1F;
        float centerX = x + width / 2.0F;
        float centerY = y + height / 2.0F;

        matrix.push();
        matrix.translate(centerX, centerY, 0.0F);
        matrix.scale(scale, scale, 1.0F);
        matrix.translate(-centerX, -centerY, 0.0F);
        Matrix4f positionMatrix = matrix.peek().getPositionMatrix();

        rectangle.render(ShapeProperties.create(matrix, x, y, width, height)
                .round(3.4F)
                .thickness(1.1F)
                .softness(1.0F)
                .outlineColor(outline)
                .color(fill)
                .build());

        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                resolved,
                TEXT_SIZE,
                textColor,
                positionMatrix,
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), resolved, TEXT_SIZE, x, width),
                MenuStyle.centerMsdfTextY(TEXT_SIZE, y, height),
                0.0F
        );
        matrix.pop();
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
