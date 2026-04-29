package vorga.phazeclient.implement.menu.components.implement.settings.multiselect;

import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import vorga.phazeclient.api.feature.module.setting.implement.MultiSelectSetting;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.AbstractComponent;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MultiSelectedButton extends AbstractComponent {
    private static final float ITEM_TEXT_SIZE = 5.7F;
    private final MultiSelectSetting setting;
    private final String text;
    @Setter
    @Accessors(chain = true)
    private float alpha;
    private final Animation alphaAnimation = new DecelerateAnimation().setMs(300).setValue(0.5);

    public MultiSelectedButton(MultiSelectSetting setting, String text) {
        this.setting = setting;
        this.text = text;

        alphaAnimation.setDirection(Direction.BACKWARDS);
    }

    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrix = context.getMatrices();
        alphaAnimation.setDirection(setting.getSelected().contains(text) ? Direction.FORWARDS : Direction.BACKWARDS);

        float opacity = alphaAnimation.getOutput().floatValue();
        int selectedOpacity = MenuStyle.withAlpha(MenuStyle.CHIP_ACTIVE, opacity * alpha);

        if (!alphaAnimation.isFinished(Direction.BACKWARDS)) {
            rectangle.render(ShapeProperties.create(matrix, x, y, width, height + 0.15F).round(getRound(setting.getList(), text)).color(selectedOpacity).build());
        }
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                text,
                ITEM_TEXT_SIZE,
                MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, alpha),
                matrix.peek().getPositionMatrix(),
                x + 4,
                MenuStyle.centerMsdfTextY(ITEM_TEXT_SIZE, y, height),
                0.0F
        );
    }

    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (MathUtil.isHovered(mouseX, mouseY, x, y, width, height) && button == 0) {
            playButtonClickSound();
            List<String> selected = new ArrayList<>(setting.getSelected());
            if (selected.contains(text)) {
                selected.remove(text);
            } else {
                selected.add(text);
                sortSelectedAccordingToList(selected, setting.getList());
            }
            setting.setSelected(selected);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    
    private void sortSelectedAccordingToList(List<String> selected, List<String> list) {
        selected.sort(Comparator.comparingInt(list::indexOf));
    }

    public static Vector4f getRound(List<String> list, String text) {
        if (list.size() == 1) return new Vector4f(4);
        if (list.getLast().contains(text)) return new Vector4f(0, 4, 0, 4);
        if (list.getFirst().contains(text)) return new Vector4f(4, 0, 4, 0);
        return new Vector4f(0);
    }
}
