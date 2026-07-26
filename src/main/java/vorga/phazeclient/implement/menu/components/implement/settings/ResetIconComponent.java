package vorga.phazeclient.implement.menu.components.implement.settings;

import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.base.QuickImports;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.menu.MenuStyle;
import net.minecraft.client.util.math.MatrixStack;

public class ResetIconComponent implements QuickImports {

    private static final float ICON_SIZE = 8.0f;
    private static final float ICON_X_OFFSET = 8.0f;
    private static final float ICON_Y_OFFSET = 6.0f;
    private static final String ICON_TEXTURE = "textures/reset.png";

    private float x, y;
    private float alpha = 1.0f;
    private boolean isModified = false;
    private final Animation visibilityAnimation = new DecelerateAnimation().setMs(140).setValue(1);
    /** Tracks whether the visibility animation has been seeded for
     *  this component yet. The first render after a tab switch
     *  should NOT play a fade-in: the user explicitly asked to drop
     *  the appear animation when flipping between MODS / SETTINGS /
     *  CONFIGS. We still want the fade when {@link #isModified}
     *  toggles during normal use, so the animation stays in place -
     *  it just gets snapped to its end state on the very first
     *  render frame. */
    private boolean visibilitySeeded = false;

    public ResetIconComponent position(float parentX, float parentY) {
        this.x = parentX + ICON_X_OFFSET;
        this.y = parentY + ICON_Y_OFFSET;
        return this;
    }

    public ResetIconComponent position(float parentX, float parentY, float parentHeight) {
        this.x = parentX + ICON_X_OFFSET;
        this.y = parentY + (parentHeight - ICON_SIZE) / 2.0f;
        return this;
    }

    public ResetIconComponent alpha(float alpha) {
        this.alpha = alpha;
        return this;
    }

    public ResetIconComponent modified(boolean isModified) {
        this.isModified = isModified;
        return this;
    }

    public void render(MatrixStack matrix) {
        Direction targetDir = isModified ? Direction.FORWARDS : Direction.BACKWARDS;
        if (!visibilitySeeded) {
            // First render after a fresh attach: snap directly to
            // the resting end value so an in-flight category switch
            // doesn't replay the fade. Subsequent toggles (the user
            // changing a setting away from / back to its default)
            // still go through the regular setDirection path and
            // animate normally.
            visibilityAnimation.setDirectionAndFinish(targetDir);
            visibilitySeeded = true;
        } else {
            visibilityAnimation.setDirection(targetDir);
        }
        float visible = visibilityAnimation.getOutputFloat();
        float iconAlpha = alpha * visible;
        if (iconAlpha <= 0.01f) {
            return;
        }

        int iconColor = MenuStyle.withAlpha(MenuStyle.TEXT_MUTED, iconAlpha);

        image.setTexture(ICON_TEXTURE)
                .render(ShapeProperties.create(matrix, x, y, ICON_SIZE, ICON_SIZE)
                        .color(iconColor)
                        .build());
    }

    public boolean isHovered(double mouseX, double mouseY) {
        if (alpha <= 0.01f) {
            return false;
        }
        return MathUtil.isHovered(mouseX, mouseY, x, y, ICON_SIZE, ICON_SIZE);
    }

    public static float getTextOffset() {
        return ICON_SIZE + 2.0f;
    }

    public static float getXOffset() {
        return ICON_X_OFFSET;
    }

    public static float getYOffset() {
        return ICON_Y_OFFSET;
    }
}
