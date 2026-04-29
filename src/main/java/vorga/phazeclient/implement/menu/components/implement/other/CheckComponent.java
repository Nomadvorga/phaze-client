package vorga.phazeclient.implement.menu.components.implement.other;

import lombok.Setter;
import lombok.experimental.Accessors;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.AbstractComponent;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

@Setter
@Accessors(chain = true)
public class CheckComponent extends AbstractComponent {
    private static final float TOGGLE_WIDTH = 18.0F;
    private static final float TOGGLE_HEIGHT = 10.0F;

    private boolean state;
    private Runnable runnable;
    private boolean initialized = false;
    private float alpha = 1.0F;

    private final Animation toggleAnimation = new DecelerateAnimation().setMs(200).setValue(1);
    private final Animation hoverAnimation = new DecelerateAnimation().setMs(170).setValue(1);

    public static float getToggleWidth() {
        return TOGGLE_WIDTH;
    }

    public static float getToggleHeight() {
        return TOGGLE_HEIGHT;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!initialized) {
            initialized = true;
            toggleAnimation.setDirectionAndFinish(state ? Direction.FORWARDS : Direction.BACKWARDS);
        }
        MatrixStack matrix = context.getMatrices();
        toggleAnimation.setDirection(state ? Direction.FORWARDS : Direction.BACKWARDS);

        float progress = toggleAnimation.getOutput().floatValue();
        boolean hovered = MathUtil.isHovered(mouseX, mouseY, x, y, TOGGLE_WIDTH, TOGGLE_HEIGHT);
        hoverAnimation.setDirection(hovered ? Direction.FORWARDS : Direction.BACKWARDS);
        float hoverProgress = hoverAnimation.getOutput().floatValue();

        int background = state ? MenuStyle.mix(MenuStyle.CHIP_ACTIVE, MenuStyle.ACCENT_GREEN, 0.22F) : MenuStyle.pill(false);
        background = MenuStyle.mix(background, MenuStyle.TEXT_PRIMARY, hoverProgress * 0.035F);
        int outline = state ? MenuStyle.settingOutline(true) : MenuStyle.settingOutline(false);
        outline = MenuStyle.mix(outline, MenuStyle.BORDER_LIGHT, hoverProgress * 0.30F);
        float knobX = x + 1.2F + 7.0F * progress;
        float clampedAlpha = Math.max(0.0F, Math.min(1.0F, alpha));

        rectangle.render(ShapeProperties.create(matrix, x, y, TOGGLE_WIDTH, TOGGLE_HEIGHT)
                .round(5.0F)
                .thickness(1.1F)
                .outlineColor(MenuStyle.withAlpha(outline, clampedAlpha))
                .color(MenuStyle.withAlpha(background, clampedAlpha))
                .build());

        rectangle.render(ShapeProperties.create(matrix, knobX, y + 1.2F, 7.0F, 7.0F)
                .round(3.5F)
                .color(MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, clampedAlpha))
                .build());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (MathUtil.isHovered(mouseX, mouseY, x, y, TOGGLE_WIDTH, TOGGLE_HEIGHT) && button == 0) {
            playButtonClickSound();
            runnable.run();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
