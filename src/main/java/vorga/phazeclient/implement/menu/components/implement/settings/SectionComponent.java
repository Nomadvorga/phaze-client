package vorga.phazeclient.implement.menu.components.implement.settings;

import net.minecraft.client.gui.DrawContext;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.implement.menu.MenuStyle;

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

        float fontSize = 5.5F;
        String text = label.singleLine(setting.getLocalizedName().toUpperCase(java.util.Locale.ROOT));
        float textWidth = MsdfFonts.bold().getWidth(text, fontSize);
        float centerX = x + Math.max(0.0F, (width - textWidth) / 2.0f);
        int color = MenuStyle.withAlpha(MenuStyle.TEXT_MUTED, currentAlpha * 0.7f);
        label.renderMsdf(context.getMatrices(), text, fontSize, centerX, y + 3.25F,
                x + width, y, height, color);
        updateLabelHover(mouseX, mouseY);
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
