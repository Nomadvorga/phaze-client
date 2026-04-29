package vorga.phazeclient.implement.menu.components.implement.settings;

import net.minecraft.client.gui.DrawContext;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.system.font.Fonts;
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
        font.drawString(context.getMatrices(), text, centerX, y + 4, color);
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
