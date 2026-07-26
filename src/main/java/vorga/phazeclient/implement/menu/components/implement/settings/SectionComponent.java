package vorga.phazeclient.implement.menu.components.implement.settings;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.base.util.render.GuiMatrix;
import vorga.phazeclient.implement.menu.MenuStyle;

import static vorga.phazeclient.api.system.font.Fonts.Type.INTER_BOLD;

public class SectionComponent extends AbstractSettingComponent {
    private final SectionSetting setting;

    public SectionComponent(SectionSetting setting) {
        super(setting);
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateVisibilityAnimation();
        height = 12;

        var font = Fonts.getSize(11, INTER_BOLD);
        String text = setting.getLocalizedName().toUpperCase();
        float textWidth = font.getStringWidth(text);
        float centerX = x + (width - textWidth) / 2.0f;
        int color = MenuStyle.withAlpha(MenuStyle.TEXT_MUTED, currentAlpha * 0.7f);

        // 1.21.11: DrawContext.getMatrices() is org.joml.Matrix3x2fStack, but FontRenderer
        // still consumes a world-style MatrixStack. Promote the 2D GUI pose into a throwaway
        // MatrixStack - same geometry, one promotion per render().
        // TODO(1.21.11): drop this once FontRenderer takes a Matrix3x2fc directly.
        MatrixStack textPose = new MatrixStack();
        textPose.multiplyPositionMatrix(GuiMatrix.mat4(context.getMatrices()));
        font.drawString(textPose, text, centerX, y + 4, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        return false;
    }
}
