package vorga.phazeclient.implement.menu.components.implement.other;

import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.gui.DrawContext;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.EaseInOutAnimation;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.AbstractComponent;
import net.minecraft.util.math.RotationAxis;

@Setter
@Accessors(chain = true)
public class SettingComponent extends AbstractComponent {
    private Runnable runnable;
    private boolean windowOpen = false;

    private final Animation rotationAnimation = new EaseInOutAnimation().setMs(600).setValue(1);

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float buttonSize = 11.0F;
        if (windowOpen) {
            rotationAnimation.setDirection(Direction.FORWARDS);
        } else {
            rotationAnimation.setDirection(Direction.BACKWARDS);
        }

        float rotationProgress = rotationAnimation.getOutput().floatValue();
        float rotationAngle = 90 + (rotationProgress * 360);

        float centerX = x + buttonSize / 2f;
        float centerY = y + buttonSize / 2f;

        rectangle.render(ShapeProperties.create(context.getMatrices(), x, y, buttonSize, buttonSize)
                .round(2)
                .thickness(1.0F)
                .outlineColor(MenuStyle.BORDER)
                .color(MenuStyle.PANEL_CHIP)
                .build());

        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY, 0);
        context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotationAngle));
        context.getMatrices().translate(-centerX, -centerY, 0);

        image.setTexture("textures/settings.png").render(
            ShapeProperties.create(context.getMatrices(), x + 2, y + 2, 7, 7)
                .color(MenuStyle.TEXT_MUTED)
                .build()
        );

        context.getMatrices().pop();
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (MathUtil.isHovered(mouseX, mouseY, x, y, 11, 11) && button == 0) {
            playButtonClickSound();
            runnable.run();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
