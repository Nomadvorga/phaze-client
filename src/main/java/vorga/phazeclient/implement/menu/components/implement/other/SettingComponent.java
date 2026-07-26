package vorga.phazeclient.implement.menu.components.implement.other;

import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.EaseInOutAnimation;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.AbstractComponent;

@Setter
@Accessors(chain = true)
public class SettingComponent extends AbstractComponent {
    private Runnable runnable;
    private boolean windowOpen = false;

    private final Animation rotationAnimation = new EaseInOutAnimation().setMs(600).setValue(1);

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float buttonSize = 11.0F;
        boolean hovered = MathUtil.isHovered(mouseX, mouseY, x, y, buttonSize, buttonSize);
        if (hovered) {
            vorga.phazeclient.api.system.cursor.CursorManager.requestHand();
        }
        if (windowOpen) {
            rotationAnimation.setDirection(Direction.FORWARDS);
        } else {
            rotationAnimation.setDirection(Direction.BACKWARDS);
        }

        float rotationProgress = rotationAnimation.getOutputFloat();
        float rotationAngle = 90 + (rotationProgress * 360);

        float centerX = x + buttonSize / 2f;
        float centerY = y + buttonSize / 2f;

        Matrix3x2fStack matrices = context.getMatrices();

        rectangle.render(ShapeProperties.create(matrices, x, y, buttonSize, buttonSize)
                .round(2)
                .thickness(1.0F)
                .outlineColor(MenuStyle.BORDER)
                .color(MenuStyle.PANEL_CHIP)
                .build());

        // 1.21.11: the GUI pose is a 2D Matrix3x2fStack, so the old
        // translate(center) / multiply(RotationAxis.POSITIVE_Z) / translate(-center)
        // triple has no quaternion to apply. rotateAbout() is exactly that triple
        // in one call - same Z rotation, same pivot - and it takes RADIANS.
        matrices.pushMatrix();
        matrices.rotateAbout((float) Math.toRadians(rotationAngle), centerX, centerY);

        image.setTexture("textures/settings.png").render(
            ShapeProperties.create(matrices, x + 2, y + 2, 7, 7)
                .color(MenuStyle.TEXT_MUTED)
                .build()
        );

        matrices.popMatrix();
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
