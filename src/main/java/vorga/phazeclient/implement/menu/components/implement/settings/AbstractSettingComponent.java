package vorga.phazeclient.implement.menu.components.implement.settings;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.gui.DrawContext;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.AbstractComponent;

@Getter
@RequiredArgsConstructor
public abstract class AbstractSettingComponent extends AbstractComponent {
    private final Setting setting;
    private final Animation visibilityAnimation = new DecelerateAnimation().setMs(350).setValue(1);
    private final Animation textOffsetAnimation = new DecelerateAnimation().setMs(140).setValue(1);
    private final Animation cardHoverAnimation = new DecelerateAnimation().setMs(170).setValue(1);
    protected final ResetIconComponent resetIcon = new ResetIconComponent();
    private boolean textOffsetInitialized = false;

    public float currentAlpha = 1.0f;
    private float externalAlpha = 1.0f;

    public void updateVisibilityAnimation() {
        boolean wasVisible = visibilityAnimation.isDirection(Direction.FORWARDS);
        boolean isVisible = setting.isVisible();

        if (!wasVisible && isVisible) {
            visibilityAnimation.setValue(1);
            visibilityAnimation.setDirection(Direction.FORWARDS);
        } else {
            visibilityAnimation.setDirection(isVisible ? Direction.FORWARDS : Direction.BACKWARDS);
        }
        currentAlpha = visibilityAnimation.getOutput().floatValue() * externalAlpha;
    }

    public void setExternalAlpha(float externalAlpha) {
        this.externalAlpha = Math.max(0.0f, Math.min(1.0f, externalAlpha));
    }

    public double getVisibilityProgress() {
        return visibilityAnimation.getOutput();
    }

    public boolean shouldSkipRender() {
        return getVisibilityProgress() <= 0.01;
    }

    public float getExpandedHeight() {
        return height;
    }

    protected void renderSettingCard(DrawContext context, boolean active) {
        renderSettingCard(context, active ? 1.0f : 0.0f, 0.0f);
    }

    protected void renderSettingCard(DrawContext context, float activeProgress, float hoverProgress) {
        float clampedActive = Math.max(0.0f, Math.min(1.0f, activeProgress));
        float clampedHover = Math.max(0.0f, Math.min(1.0f, hoverProgress));

        int baseBackground = MenuStyle.mix(
                MenuStyle.settingSurface(false),
                MenuStyle.settingSurface(true),
                clampedActive
        );
        int baseOutline = MenuStyle.mix(
                MenuStyle.settingOutline(false),
                MenuStyle.settingOutline(true),
                clampedActive
        );

        int hoveredBackground = MenuStyle.mix(baseBackground, 0xFFFFFFFF, clampedHover * 0.08f);
        int hoveredOutline = MenuStyle.mix(baseOutline, 0xFFFFFFFF, clampedHover * 0.12f);

        int background = MenuStyle.withAlpha(hoveredBackground, currentAlpha * 0.78F);
        int outline = MenuStyle.withAlpha(hoveredOutline, currentAlpha * 0.74F);

        rectangle.render(ShapeProperties.create(context.getMatrices(), x, y, width, height)
                .round(4)
                .softness(1.12F)
                .thickness(1.2F)
                .outlineColor(outline)
                .color(background)
                .build());
    }

    protected int primaryText() {
        return MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, currentAlpha);
    }

    protected int mutedText() {
        return MenuStyle.withAlpha(MenuStyle.TEXT_MUTED, currentAlpha);
    }

    protected float animatedTextOffset(boolean modified) {
        if (!textOffsetInitialized) {
            textOffsetAnimation.setDirectionAndFinish(modified ? Direction.FORWARDS : Direction.BACKWARDS);
            textOffsetInitialized = true;
        }
        textOffsetAnimation.setDirection(modified ? Direction.FORWARDS : Direction.BACKWARDS);
        return ResetIconComponent.getTextOffset() * textOffsetAnimation.getOutput().floatValue();
    }

    protected float animatedCardHover(boolean hovered) {
        cardHoverAnimation.setDirection(hovered ? Direction.FORWARDS : Direction.BACKWARDS);
        return cardHoverAnimation.getOutput().floatValue();
    }
}
