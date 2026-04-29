package vorga.phazeclient.implement.menu.components.implement.other;

import lombok.Getter;
import lombok.Setter;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.components.AbstractComponent;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

@Getter
@Setter
public class ModuleDescriptionComponent extends AbstractComponent {

    private Module hoveredModule = null;
    private Setting hoveredSetting = null;
    private String description = "";
    private boolean visible = false;

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {

        if (description.isEmpty() || !visible) {
            return;
        }

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        float maxWidth = 300f;
        float lineHeight = 4;
        int fontSize = 16;

        String[] lines = wrapText(description, maxWidth);
        float textWidth = 0f;
        for (String line : lines) {
            float lineWidth = Fonts.getSize(fontSize).getStringWidth(line);
            textWidth = Math.max(textWidth, lineWidth);
        }

        float padding = 4f;
        float boxWidth = textWidth + padding * 2;
        float boxHeight = lines.length * lineHeight + padding * 2;

        int menuX = MenuScreen.INSTANCE.x;
        int menuY = MenuScreen.INSTANCE.y;
        int menuWidth = MenuScreen.INSTANCE.width;

        float boxX = menuX + menuWidth / 2f - boxWidth / 2f;

        float topY = menuY - boxHeight - 10;
        float bottomY = menuY + 238;

        float boxY;
        if (topY >= 10f) {
            boxY = topY;
        } else {
            boxY = bottomY;
        }

        boxX = Math.max(10f, Math.min(boxX, (float) screenWidth - boxWidth - 10f));
        boxY = Math.max(10f, Math.min(boxY, (float) screenHeight - boxHeight - 10f));

        MatrixStack matrices = context.getMatrices();
        rectangle.render(ShapeProperties.create(matrices, boxX, boxY, boxWidth, boxHeight)
                .round(4).softness(1).thickness(1.4F)
                .outlineColor(MenuStyle.BORDER_LIGHT)
                .color(MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.PANEL_CHIP, MenuStyle.CARD_INNER, 0.55F), 0.95F))
                .build());

        int textColor = MenuStyle.TEXT_PRIMARY;
        float textX = boxX + padding;
        float textY = boxY + padding;
        for (String line : lines) {
            Fonts.getSize(fontSize).drawString(matrices, line, textX, textY - 0.5f, textColor);
            textY += lineHeight;
        }
    }

    public void setHoveredModule(Module module) {
        if (module != null) {
            String moduleDescription = module.getDescription();
            if (!moduleDescription.isEmpty()) {
                this.hoveredModule = module;
                this.hoveredSetting = null;
                this.description = moduleDescription;
                this.visible = true;
            } else {
                this.hoveredModule = null;
                this.hoveredSetting = null;
                this.description = "";
                this.visible = false;
            }
        } else {
            this.hoveredModule = null;
            this.hoveredSetting = null;
            this.description = "";
            this.visible = false;
        }
    }

    public void setHoveredSetting(Setting setting) {
        if (setting != null) {
            String settingDescription = setting.getLocalizedDescription();
            if (settingDescription != null && !settingDescription.isEmpty()) {
                this.hoveredModule = null;
                this.hoveredSetting = setting;
                this.description = settingDescription;
                this.visible = true;
            } else {
                this.hoveredModule = null;
                this.hoveredSetting = null;
                this.description = "";
                this.visible = false;
            }
        } else {
            this.hoveredModule = null;
            this.hoveredSetting = null;
            this.description = "";
            this.visible = false;
        }
    }

    public void setHoveredSettingDescription(String descriptionKey) {
        if (descriptionKey != null && !descriptionKey.isEmpty()) {
            String settingDescription = descriptionKey;
            if (!settingDescription.isEmpty()) {
                this.hoveredModule = null;
                this.hoveredSetting = null;
                this.description = settingDescription;
                this.visible = true;
            } else {
                this.hoveredModule = null;
                this.hoveredSetting = null;
                this.description = "";
                this.visible = false;
            }
        } else {
            this.hoveredModule = null;
            this.hoveredSetting = null;
            this.description = "";
            this.visible = false;
        }
    }

    public void hide() {
        this.visible = false;
        this.hoveredModule = null;
        this.hoveredSetting = null;
        this.description = "";
    }

    private String[] wrapText(String text, float maxWidth) {
        if (text.isEmpty()) {
            return new String[0];
        }

        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        java.util.List<String> lines = new java.util.ArrayList<>();

        for (String word : words) {
            String testLine = !currentLine.isEmpty() ? currentLine + " " + word : word;
            float lineWidth = Fonts.getSize(16).getStringWidth(testLine);

            if (lineWidth <= maxWidth) {
                currentLine = new StringBuilder(testLine);
            } else {
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    lines.add(word);
                }
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }

        return lines.toArray(new String[0]);
    }
}
