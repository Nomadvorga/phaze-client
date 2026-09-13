package vorga.phazeclient.implement.menu.components.implement.settings;

import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.base.QuickImports;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.UiMsdfIconAtlas;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public class ResetIconComponent implements QuickImports {

    private static final float BASE_ICON_SIZE = 8.0f;
    private static final float ICON_SIZE = BASE_ICON_SIZE * (1.7f / 1.15f / 1.2f);
    private static final float ICON_X_OFFSET = 8.0f - (ICON_SIZE - BASE_ICON_SIZE) / 2.0f;
    private static final float ICON_Y_OFFSET = 6.0f - (ICON_SIZE - BASE_ICON_SIZE) / 2.0f;
    private static final String ICON_TEXTURE = "textures/reset.png";
    private static final Identifier ICON_ID = Identifier.ofVanilla(ICON_TEXTURE);

    private float x, y;
    private float alpha = 1.0f;
    private boolean isModified = false;
    private final Animation visibilityAnimation = new DecelerateAnimation().setMs(140).setValue(1);

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

    public void render(DrawContext context) {
        Direction targetDir = isModified ? Direction.FORWARDS : Direction.BACKWARDS;
        if (!visibilitySeeded) {

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

        if (!UiMsdfIconAtlas.renderIcon(context, ICON_ID, x, y, ICON_SIZE, ICON_SIZE, iconColor, true)) {
            image.setTexture(ICON_TEXTURE)
                    .render(ShapeProperties.create(context.getMatrices(), x, y, ICON_SIZE, ICON_SIZE)
                            .color(iconColor)
                            .build());
        }
    }

    public boolean isHovered(double mouseX, double mouseY) {
        if (alpha <= 0.01f) {
            return false;
        }
        return MathUtil.isHovered(mouseX, mouseY, x, y, ICON_SIZE, ICON_SIZE);
    }

    public static float getTextOffset() {
        return BASE_ICON_SIZE + 2.0f;
    }

    public static float getXOffset() {
        return ICON_X_OFFSET;
    }

    public static float getYOffset() {
        return ICON_Y_OFFSET;
    }
}
