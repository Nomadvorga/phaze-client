package vorga.phazeclient.implement.menu.components.implement.settings.select;

import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.base.util.other.StringUtil;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.implement.settings.AbstractSettingComponent;
import vorga.phazeclient.implement.menu.components.implement.settings.ResetIconComponent;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

import java.util.List;

public class SelectComponent extends AbstractSettingComponent {
    private static final float LABEL_TEXT_SIZE = 7.0F;
    private static final float VALUE_TEXT_SIZE = 6.1F;
    private static final float CARD_MIN_HEIGHT = 26.0F;
    private static final float SELECT_BOX_HEIGHT = 16.0F;
    private static final float SELECT_BOX_MIN_WIDTH = 92.0F;
    private static final float SELECT_BOX_MAX_WIDTH = 118.0F;
    private static final float SELECT_BOX_RIGHT_MARGIN = 8.0F;
    private static final float ARROW_HIT_WIDTH = 14.0F;
    private static final float ARROW_TEXT_SIZE = 7.0F;
    private static final float SLIDE_OFFSET = 6.0F;
    private static final int TRANSITION_MS = 150;
    private static final int HOVER_MS = 120;
    private static final int ICON_ANIMATION_MS = 120;

    private final SelectSetting setting;

    private final Animation transitionAnimation = new DecelerateAnimation()
            .setMs(TRANSITION_MS).setValue(1);
    private final Animation leftHoverAnimation = new DecelerateAnimation()
            .setMs(HOVER_MS).setValue(1);
    private final Animation rightHoverAnimation = new DecelerateAnimation()
            .setMs(HOVER_MS).setValue(1);
    private final Animation textOffsetAnimation = new DecelerateAnimation()
            .setMs(ICON_ANIMATION_MS).setValue(1);
    private final Animation resetIconAnimation = new DecelerateAnimation()
            .setMs(ICON_ANIMATION_MS).setValue(1);
    private String previousValue = null;
    private int cycleDirection = 1;

    public SelectComponent(SelectSetting setting) {
        super(setting);
        this.setting = setting;
        transitionAnimation.setDirection(Direction.FORWARDS);
        boolean isModified = setting.isModified();
        textOffsetAnimation.setDirectionAndFinish(isModified ? Direction.FORWARDS : Direction.BACKWARDS);
        resetIconAnimation.setDirectionAndFinish(isModified ? Direction.FORWARDS : Direction.BACKWARDS);
    }

    public static void closeAllDropdowns() {
    }

    public static void handleGlobalClick(double mouseX, double mouseY) {
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

        float selectedBoxX = selectedBoxX();
        float selectedBoxWidth = selectedBoxWidth();
        String wrapped = StringUtil.wrap(setting.getLocalizedName(), (int) Math.max(28.0F, selectedBoxX - x - 14.0F - animatedTextOffset), 14);
        float wrappedHeight = Fonts.getSize(14).getStringHeight(wrapped);
        height = Math.round(Math.max(CARD_MIN_HEIGHT, 22 + Math.max(0, (wrappedHeight - 14) / 2.0F)));
        float hoverProgress = animatedCardHover(MathUtil.isHovered(mouseX, mouseY, x, y, width, height));

        renderSettingCard(context, 0.0f, hoverProgress);
        renderArrowSelector(matrices, mouseX, mouseY);

        resetIcon.position(x, y, height).alpha(currentAlpha * resetIconAlpha).modified(isModified).render(matrices);

        float textX = x + 10 + animatedTextOffset;
        renderLabelText(matrices, wrapped, textX, primaryText());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (resetIcon.isHovered(mouseX, mouseY)) {
                playButtonClickSound();
                setting.reset();
                return true;
            }

            float boxX = selectedBoxX();
            float boxY = selectedBoxY();
            float boxWidth = selectedBoxWidth();

            if (MathUtil.isHovered(mouseX, mouseY, boxX, boxY, ARROW_HIT_WIDTH, SELECT_BOX_HEIGHT)) {
                playButtonClickSound();
                cyclePrevious();
                return true;
            }

            if (MathUtil.isHovered(mouseX, mouseY, boxX + boxWidth - ARROW_HIT_WIDTH, boxY, ARROW_HIT_WIDTH, SELECT_BOX_HEIGHT)) {
                playButtonClickSound();
                cycleNext();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        return MathUtil.isHovered(mouseX, mouseY, x, y, width, height);
    }

    private void renderArrowSelector(MatrixStack matrices, int mouseX, int mouseY) {
        float boxX = selectedBoxX();
        float boxY = selectedBoxY();
        float boxWidth = selectedBoxWidth();
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();

        rectangle.render(ShapeProperties.create(matrices, boxX, boxY, boxWidth, SELECT_BOX_HEIGHT)
                .round(2).softness(1.1F).thickness(1.1F)
                .outlineColor(MenuStyle.withAlpha(MenuStyle.BORDER, currentAlpha))
                .color(MenuStyle.withAlpha(MenuStyle.PANEL_CHIP, currentAlpha))
                .build());

        boolean leftHovered = MathUtil.isHovered(mouseX, mouseY, boxX, boxY, ARROW_HIT_WIDTH, SELECT_BOX_HEIGHT);
        leftHoverAnimation.setDirection(leftHovered ? Direction.FORWARDS : Direction.BACKWARDS);
        float leftProgress = leftHoverAnimation.getOutputFloat();
        float leftAlpha = (0.7F + 0.3F * leftProgress) * currentAlpha;
        int leftColor = MenuStyle.withAlpha(MenuStyle.CHIP_ACTIVE, leftAlpha);
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                "<",
                ARROW_TEXT_SIZE,
                leftColor,
                positionMatrix,
                boxX + 4.0F,
                MenuStyle.centerMsdfTextY(ARROW_TEXT_SIZE, boxY, SELECT_BOX_HEIGHT),
                0.0F
        );

        boolean rightHovered = MathUtil.isHovered(mouseX, mouseY, boxX + boxWidth - ARROW_HIT_WIDTH, boxY, ARROW_HIT_WIDTH, SELECT_BOX_HEIGHT);
        rightHoverAnimation.setDirection(rightHovered ? Direction.FORWARDS : Direction.BACKWARDS);
        float rightProgress = rightHoverAnimation.getOutputFloat();
        float rightAlpha = (0.7F + 0.3F * rightProgress) * currentAlpha;
        int rightColor = MenuStyle.withAlpha(MenuStyle.CHIP_ACTIVE, rightAlpha);
        float rightArrowWidth = MsdfFonts.bold().getWidth(">", ARROW_TEXT_SIZE);
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                ">",
                ARROW_TEXT_SIZE,
                rightColor,
                positionMatrix,
                boxX + boxWidth - 4.0F - rightArrowWidth,
                MenuStyle.centerMsdfTextY(ARROW_TEXT_SIZE, boxY, SELECT_BOX_HEIGHT),
                0.0F
        );

        float progress = transitionAnimation.getOutputFloat();
        float textAreaX = boxX + ARROW_HIT_WIDTH;
        float textAreaWidth = boxWidth - ARROW_HIT_WIDTH * 2;
        float textY = MenuStyle.centerMsdfTextY(VALUE_TEXT_SIZE, boxY, SELECT_BOX_HEIGHT);

        String selectedName = setting.getSelected();

        if (previousValue != null && progress < 1.0F) {
            float oldAlpha = (1.0F - progress) * currentAlpha;
            float oldSlide = progress * SLIDE_OFFSET * cycleDirection;
            float oldWidth = MsdfFonts.bold().getWidth(previousValue, VALUE_TEXT_SIZE);
            float oldCenterX = textAreaX + (textAreaWidth - oldWidth) / 2.0F + oldSlide;
            MsdfRenderer.renderText(
                    MsdfFonts.bold(),
                    previousValue,
                    VALUE_TEXT_SIZE,
                    MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, oldAlpha),
                    positionMatrix,
                    oldCenterX,
                    textY,
                    0.0F
            );

            float newAlpha = progress * currentAlpha;
            float newSlide = (1.0F - progress) * SLIDE_OFFSET * -cycleDirection;
            float newWidth = MsdfFonts.bold().getWidth(selectedName, VALUE_TEXT_SIZE);
            float newCenterX = textAreaX + (textAreaWidth - newWidth) / 2.0F + newSlide;
            MsdfRenderer.renderText(
                    MsdfFonts.bold(),
                    selectedName,
                    VALUE_TEXT_SIZE,
                    MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, newAlpha),
                    positionMatrix,
                    newCenterX,
                    textY,
                    0.0F
            );
        } else {
            previousValue = null;
            float valueWidth = MsdfFonts.bold().getWidth(selectedName, VALUE_TEXT_SIZE);
            float centerX = textAreaX + (textAreaWidth - valueWidth) / 2.0F;
            MsdfRenderer.renderText(
                    MsdfFonts.bold(),
                    selectedName,
                    VALUE_TEXT_SIZE,
                    MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, currentAlpha),
                    positionMatrix,
                    centerX,
                    textY,
                    0.0F
            );
        }
    }

    private void startTransition(int direction) {
        previousValue = setting.getSelected();
        cycleDirection = direction;
        transitionAnimation.reset();
        transitionAnimation.setDirection(Direction.BACKWARDS);
        transitionAnimation.setDirection(Direction.FORWARDS);
    }

    private void cyclePrevious() {
        startTransition(-1);
        List<String> list = setting.getList();
        int currentIndex = list.indexOf(setting.getSelected());
        int newIndex = (currentIndex - 1 + list.size()) % list.size();
        setting.setSelected(list.get(newIndex));
    }

    private void cycleNext() {
        startTransition(1);
        List<String> list = setting.getList();
        int currentIndex = list.indexOf(setting.getSelected());
        int newIndex = (currentIndex + 1) % list.size();
        setting.setSelected(list.get(newIndex));
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

    public void renderTooltips(DrawContext context) {
    }

    public float getExpandedHeight() {
        return height;
    }

    private float selectedBoxWidth() {
        return Math.min(SELECT_BOX_MAX_WIDTH, Math.max(SELECT_BOX_MIN_WIDTH, width * 0.42F));
    }

    private float selectedBoxX() {
        return x + width - selectedBoxWidth() - SELECT_BOX_RIGHT_MARGIN;
    }

    private float selectedBoxY() {
        return y + height / 2.0F - SELECT_BOX_HEIGHT / 2.0F;
    }
}
