package vorga.phazeclient.implement.menu.components.implement.settings.multiselect;

import vorga.phazeclient.api.feature.module.setting.implement.MultiSelectSetting;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.font.FontRenderer;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.base.util.other.StringUtil;
import vorga.phazeclient.base.util.render.ScissorManager;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.implement.settings.AbstractSettingComponent;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

import static vorga.phazeclient.api.system.font.Fonts.Type.INTER_BOLD;

public class MultiSelectComponent extends AbstractSettingComponent {
    private final List<MultiSelectedButton> multiSelectedButtons = new ArrayList<>();

    private static final List<MultiSelectComponent> openDropdowns = new ArrayList<>();

    private final MultiSelectSetting setting;
    private boolean open;

    private float dropdownListX,
            dropDownListY,
            dropDownListWidth,
            dropDownListHeight;

    private final Animation alphaAnimation = new DecelerateAnimation().setMs(300).setValue(1);

    private final Animation slideAnimation = new DecelerateAnimation().setMs(250).setValue(1);

    public MultiSelectComponent(MultiSelectSetting setting) {
        super(setting);
        this.setting = setting;

        alphaAnimation.setDirection(Direction.BACKWARDS);
        slideAnimation.setDirection(Direction.BACKWARDS);

        for (String s : setting.getList()) {
            multiSelectedButtons.add(new MultiSelectedButton(setting, s));
        }
    }

    private void setOpen(boolean open) {
        if (this.open == open) return;

        this.open = open;
        if (open) {
            closeAllDropdowns();
            openDropdowns.add(this);
        } else {
            openDropdowns.remove(this);
        }
    }

    public static void closeAllDropdowns() {
        for (MultiSelectComponent dropdown : new ArrayList<>(openDropdowns)) {
            dropdown.open = false;
        }
        openDropdowns.clear();
    }

    public static void handleGlobalClick(double mouseX, double mouseY) {
        for (MultiSelectComponent dropdown : new ArrayList<>(openDropdowns)) {
            if (!dropdown.isHover(mouseX, mouseY)) {
                dropdown.setOpen(false);
            }
        }
    }


    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateVisibilityAnimation();
        var labelFont = Fonts.getSize(14, INTER_BOLD);

        boolean isModified = setting.isModified();
        float textOffset = animatedTextOffset(isModified);

        MatrixStack matrices = context.getMatrices();
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();

        String wrapped = StringUtil.wrap(setting.getLocalizedName(), (int) (width - 89 - textOffset), 14);
        float wrappedHeight = Fonts.getSize(14).getStringHeight(wrapped);
        height = (int) (20 + Math.max(0, (wrappedHeight - 14) / 2));
        float hoverProgress = animatedCardHover(MathUtil.isHovered(mouseX, mouseY, x, y, width, height));

        List<String> fullSettingsList = setting.getList();

        this.dropdownListX = x + width - 75 + 2;
        this.dropDownListY = y + height + 2;
        this.dropDownListWidth = 66;
        this.dropDownListHeight = fullSettingsList.size() * 12;

        alphaAnimation.setDirection(open ? Direction.FORWARDS : Direction.BACKWARDS);
        slideAnimation.setDirection(open ? Direction.FORWARDS : Direction.BACKWARDS);

        renderSettingCard(context, open ? 1.0f : 0.0f, hoverProgress);
        renderSelected(matrices, positionMatrix);
        if (!alphaAnimation.isFinished(Direction.BACKWARDS))
            renderSelectList(context, positionMatrix, mouseX, mouseY, delta);

        resetIcon.position(x, y, height).alpha(currentAlpha).modified(isModified).render(matrices);

        float textX = x + 10 + textOffset;
        labelFont.drawString(matrices, wrapped, textX, centeredTextY(labelFont, wrapped), primaryText());
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (resetIcon.isHovered(mouseX, mouseY)) {
                playButtonClickSound();
                setting.reset();
                return true;
            }

            float buttonY = y + height / 2 - 7;
            if (MathUtil.isHovered(mouseX, mouseY, x + width - 75 + 2, buttonY, 66, 14)) {
                playButtonClickSound();
                setOpen(!open);
                return true;
            } else if (open && !isHoveredList(mouseX, mouseY)) {
                setOpen(false);
                return true;
            }

            if (open) {
                for (MultiSelectedButton selectedButton : multiSelectedButtons) {
                    if (selectedButton.mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                }
                if (isHoveredList(mouseX, mouseY)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }


    @Override
    public boolean isHover(double mouseX, double mouseY) {
        float buttonY = y + height / 2 - 7;
        if (MathUtil.isHovered(mouseX, mouseY, x + width - 75 + 2, buttonY, 66, 14)) {
            return true;
        }
        if (MathUtil.isHovered(mouseX, mouseY, x + 9, y + 6, width - 75 - 9, height - 12)) {
            return true;
        }
        return open && isHoveredList(mouseX, mouseY);
    }


    private void renderSelected(MatrixStack matrix, Matrix4f positionMatrix) {
        FontRenderer font = Fonts.getSize(12);
        int x1 = (int) (x + width - 72 + 2);
        float buttonY = y + height / 2 - 7;

        rectangle.render(ShapeProperties.create(matrix, x1 - 3, buttonY, 66, 14)
                .round(2).thickness(1.1F).softness(0.5F)
                .outlineColor(MenuStyle.withAlpha(open ? MenuStyle.CHIP_ACTIVE : MenuStyle.BORDER, currentAlpha))
                .color(MenuStyle.withAlpha(MenuStyle.PANEL_CHIP, currentAlpha))
                .build());

        String selectedName = String.join(", ", setting.getSelected());

        float offset = 64;

        ScissorManager scissor = Main.getInstance().getScissorManager();
        scissor.push(positionMatrix, x1 - 1, buttonY, 62, 14);

        font.drawStringWithScroll(matrix, selectedName, x1, centeredTextY(font, selectedName, buttonY, 14), offset, MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, currentAlpha));

        scissor.pop();

        if (font.getStringWidth(selectedName) - offset > 0) {
            rectangle.render(ShapeProperties.create(matrix, x + width - 13.75F + 2, buttonY + 1, 4, 12)
                    .round(3).color(
                            MenuStyle.withAlpha(MenuStyle.PANEL_CHIP, currentAlpha),
                            MenuStyle.withAlpha(MenuStyle.PANEL_CHIP, currentAlpha),
                            MenuStyle.withAlpha(MenuStyle.PANEL_BG, 0.0F),
                            MenuStyle.withAlpha(MenuStyle.PANEL_BG, 0.0F)
                    ).build());

            rectangle.render(ShapeProperties.create(matrix, x1 - 2.25F, buttonY + 1, 4, 12)
                    .round(3).color(
                            MenuStyle.withAlpha(MenuStyle.PANEL_BG, 0.0F),
                            MenuStyle.withAlpha(MenuStyle.PANEL_BG, 0.0F),
                            MenuStyle.withAlpha(MenuStyle.PANEL_CHIP, currentAlpha),
                            MenuStyle.withAlpha(MenuStyle.PANEL_CHIP, currentAlpha)
                    ).build());
        }
    }


    private void renderSelectList(DrawContext context, Matrix4f positionMatrix, int mouseX, int mouseY, float delta) {
        float opacity = alphaAnimation.getOutputFloat() * currentAlpha;
        float slideProgress = slideAnimation.getOutputFloat();

        float animatedHeight = dropDownListHeight * slideProgress;
        float animatedY = dropDownListY;

        context.getMatrices().push();

        rectangle.render(ShapeProperties.create(context.getMatrices(), dropdownListX, animatedY, dropDownListWidth, animatedHeight)
                .round(4).thickness(1.1F).outlineColor(MenuStyle.withAlpha(MenuStyle.BORDER_LIGHT, opacity)).color(MenuStyle.withAlpha(MenuStyle.PANEL_BG_SOFT, opacity)).build());

        ScissorManager scissor = Main.getInstance().getScissorManager();
        scissor.push(positionMatrix, dropdownListX, animatedY, dropDownListWidth, animatedHeight);

        float offset = dropDownListY;
        int visibleButtons = Math.max(1, (int) (multiSelectedButtons.size() * slideProgress));

        for (int i = 0; i < Math.min(visibleButtons, multiSelectedButtons.size()); i++) {
            MultiSelectedButton button = multiSelectedButtons.get(i);
            button.x = dropdownListX;
            button.y = offset;
            button.width = dropDownListWidth;
            button.height = 12;

            button.setAlpha(opacity);

            button.render(context, mouseX, mouseY, delta);
            offset += 12;
        }

        scissor.pop();
        context.getMatrices().pop();
    }


    private boolean isHoveredList(double mouseX, double mouseY) {
        float slideProgress = slideAnimation.getOutputFloat();
        float animatedHeight = dropDownListHeight * slideProgress;
        return MathUtil.isHovered(mouseX, mouseY, dropdownListX, dropDownListY - 16, dropDownListWidth, animatedHeight + 16);
    }

    public float getExpandedHeight() {
        if (!open || slideAnimation.getOutputFloat() < 0.01f) {
            return height;
        }
        float slideProgress = slideAnimation.getOutputFloat();
        float dropdownHeight = dropDownListHeight * slideProgress;
        return height + 2 + dropdownHeight;
    }
}
