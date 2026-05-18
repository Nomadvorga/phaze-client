package vorga.phazeclient.implement.menu.components.implement.settings;

import lombok.Getter;
import org.joml.Matrix4f;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.base.util.other.StringUtil;
import vorga.phazeclient.implement.menu.MenuStyle;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ValueComponent extends AbstractSettingComponent {
    private static final float LABEL_TEXT_SIZE = 7.0F;
    private static final float VALUE_TEXT_SIZE = 6.1F;
    private static final float SLIDER_MARGIN = 9.0F;
    private static final float TRACK_HEIGHT = 2.0F;
    private static final float THUMB_OUTER = 8.0F;
    private static final float THUMB_INNER = 6.0F;
    private static final double SMOOTH_SPEED = 4.0;
    private static final int ANIMATION_MS = 120;

    private final ValueSetting setting;

    private boolean dragging;
    private double animation;
    private float previousValue;
    private boolean wasVisible = true;
    @Getter
    private boolean isSliderHovered = false;

    private static final float RESET_AREA = 16.0F;
    private static final float SLIDER_FIXED_WIDTH = 70.0F;

    private float cachedSliderStartX, cachedSliderEndX, cachedSliderWidth, cachedSliderY;

    private final Animation textOffsetAnimation = new DecelerateAnimation().setMs(ANIMATION_MS).setValue(1);
    private final Animation resetIconAnimation = new DecelerateAnimation().setMs(ANIMATION_MS).setValue(1);

    public ValueComponent(ValueSetting setting) {
        super(setting);
        this.setting = setting;
        this.previousValue = setting.getValue();
        this.animation = (setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin()) * SLIDER_FIXED_WIDTH;
        boolean isModified = setting.isModified();
        this.textOffsetAnimation.setDirectionAndFinish(isModified ? Direction.FORWARDS : Direction.BACKWARDS);
        this.resetIconAnimation.setDirectionAndFinish(isModified ? Direction.FORWARDS : Direction.BACKWARDS);
    }

    @Override
    public void tick() {
        super.tick();
    }

    private void computeSliderGeometry() {
        cachedSliderWidth = Math.min(SLIDER_FIXED_WIDTH, width * 0.35F);
        cachedSliderEndX = x + width - SLIDER_MARGIN - RESET_AREA;
        cachedSliderStartX = cachedSliderEndX - cachedSliderWidth;
        cachedSliderY = y + height / 2.0F;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateVisibilityAnimation();

        boolean isModified = setting.isModified();
        textOffsetAnimation.setDirection(isModified ? Direction.FORWARDS : Direction.BACKWARDS);
        resetIconAnimation.setDirection(isModified ? Direction.FORWARDS : Direction.BACKWARDS);

        float textOffsetProgress = textOffsetAnimation.getOutputFloat();
        float animatedTextOffset = ResetIconComponent.getTextOffset() * textOffsetProgress;
        float resetIconAlpha = resetIconAnimation.getOutputFloat();

        MatrixStack matrices = context.getMatrices();

        float selectedBoxX = x + width - 100;
        String wrapped = StringUtil.wrap(setting.getLocalizedName(), (int) Math.max(28.0F, selectedBoxX - x - 20 - animatedTextOffset), 14);
        float wrappedHeight = MsdfFonts.bold().getWidth(wrapped, LABEL_TEXT_SIZE) / 10;
        height = Math.round(Math.max(26.0F, 22 + Math.max(0, (wrappedHeight - 14) / 2.0F)));
        float hoverProgress = animatedCardHover(MathUtil.isHovered(mouseX, mouseY, x, y, width, height));

        computeSliderGeometry();

        float centerY = y + height / 2.0F;

        isSliderHovered = MathUtil.isHovered(mouseX, mouseY, cachedSliderStartX - 4, centerY - 6, cachedSliderWidth + 8, 12);

        renderSettingCard(context, 0.0f, hoverProgress);

        renderLabelText(matrices, wrapped, x + 10 + animatedTextOffset, primaryText());

        String value = String.valueOf(setting.getValue());
        float valueWidth = MsdfFonts.bold().getWidth(value, VALUE_TEXT_SIZE);
        float valueX = cachedSliderStartX - valueWidth - 6;
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                value,
                VALUE_TEXT_SIZE,
                MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, currentAlpha),
                matrices.peek().getPositionMatrix(),
                valueX,
                centerY - VALUE_TEXT_SIZE / 2 + 0.5F,
                0.0F
        );

        changeValue(renderSlider(mouseX, matrices, cachedSliderStartX, centerY, cachedSliderWidth));

        resetIcon.position(x, y, height).alpha(currentAlpha * resetIconAlpha).modified(isModified).render(matrices);
    }

    private void renderLabelText(MatrixStack matrices, String wrapped, float textX, int color) {
        String[] lines = wrapped.split("\n");
        float lineHeight = LABEL_TEXT_SIZE + 1.5F;
        float totalHeight = lines.length == 0 ? LABEL_TEXT_SIZE : lines.length * lineHeight - 1.5F;
        float startY = y + (height - totalHeight) / 2.0F + 0.6F + 0.5F;

        for (String line : lines) {
            MsdfRenderer.renderText(
                    MsdfFonts.bold(),
                    line,
                    LABEL_TEXT_SIZE,
                    color,
                    matrices.peek().getPositionMatrix(),
                    textX,
                    startY,
                    0.0F
            );
            startY += lineHeight;
        }
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        if (!setting.isVisible()) {
            return false;
        }
        return MathUtil.isHovered(mouseX, mouseY, x, y, width, height);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && resetIcon.isHovered(mouseX, mouseY)) {
            playButtonClickSound();
            setting.reset();
            return true;
        }

        computeSliderGeometry();
        float centerY = y + height / 2.0F;
        boolean wasClicked = MathUtil.isHovered(mouseX, mouseY, cachedSliderStartX - 4, centerY - 6, cachedSliderWidth + 8, 12) && button == 0;
        if (wasClicked) {
            playButtonClickSound();
            dragging = true;
            return true;
        }
        dragging = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }


    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private float renderSlider(int mouseX, MatrixStack matrix, float sliderStartX, float sliderY, float sliderWidth) {
        float percentValue = sliderWidth * (setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
        float difference = MathHelper.clamp(mouseX - sliderStartX, 0, sliderWidth);

        if (!wasVisible && setting.isVisible()) {
            animation = percentValue;
        }
        wasVisible = setting.isVisible();

        animation = MathUtil.interpolateSmooth(2.5, animation, dragging ? difference : percentValue);

        int bgColor = MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.PANEL_CHIP, MenuStyle.CARD_OPTIONS, 0.35F), currentAlpha);
        int sliderColor = MenuStyle.withAlpha(MenuStyle.CHIP_ACTIVE, currentAlpha);
        int thumbBgColor = MenuStyle.withAlpha(MenuStyle.PANEL_BG, currentAlpha);

        rectangle.render(ShapeProperties.create(matrix, sliderStartX, sliderY - TRACK_HEIGHT / 2, sliderWidth, TRACK_HEIGHT)
                .round(1).color(bgColor).build());

        float fillWidth = MathHelper.clamp((float) animation, 0, sliderWidth);
        if (fillWidth > 0) {
            rectangle.render(ShapeProperties.create(matrix, sliderStartX, sliderY - TRACK_HEIGHT / 2, fillWidth, TRACK_HEIGHT)
                    .round(1).color(sliderColor).build());
        }

        float thumbX = MathHelper.clamp((float) (sliderStartX + animation), sliderStartX, sliderStartX + sliderWidth);
        // One SDF rect for the whole thumb: fill = sliderColor for
        // the inner disc, outline = thumbBgColor as the 1 px ring.
        // Visually identical to the previous (outer thumbBgColor
        // disc + smaller sliderColor disc on top) pair, but in a
        // single rasterisation pass.
        float thumbBorder = (THUMB_OUTER - THUMB_INNER) * 0.5F;
        rectangle.render(ShapeProperties.create(matrix, thumbX - THUMB_OUTER / 2, sliderY - THUMB_OUTER / 2, THUMB_OUTER, THUMB_OUTER)
                .round(THUMB_OUTER / 2)
                .thickness(thumbBorder)
                .outlineColor(thumbBgColor)
                .color(sliderColor)
                .build());

        return difference;
    }


    private void changeValue(float difference) {
        BigDecimal bd = BigDecimal.valueOf((difference / cachedSliderWidth) * (setting.getMax() - setting.getMin()) + setting.getMin())
                .setScale(2, RoundingMode.HALF_UP);

        if (dragging) {
            float newValue = difference == 0 ? setting.getMin() : bd.floatValue();
            if (setting.isInteger()) newValue = (int) newValue;

            if (newValue != previousValue) {
                previousValue = newValue;
            }

            setting.setValue(newValue);
        }
    }
}
