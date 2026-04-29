package vorga.phazeclient.implement.menu.components.implement.window.implement;

import lombok.RequiredArgsConstructor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.api.system.localization.LocalizationManager;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.color.ColorUtil;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.base.util.other.StringUtil;
import vorga.phazeclient.implement.menu.components.implement.window.AbstractWindow;
import vorga.phazeclient.implement.menu.components.implement.window.implement.module.ModuleBindWindow;

@RequiredArgsConstructor
public abstract class AbstractBindWindow extends AbstractWindow {
    private boolean binding;

    protected abstract int getKey();

    protected abstract void setKey(int key);

    protected abstract int getType();

    protected abstract void setType(int type);

    @Override
    public void drawWindow(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrix = context.getMatrices();

        rectangle.render(ShapeProperties.create(matrix, x, y, width, height)
                .round(4).softness(25).color(0x32000000).build());

        rectangle.render(ShapeProperties.create(matrix, x, y, width, height)
                .round(4).thickness(2).outlineColor(ColorUtil.getOutline(0.8F,1)).color(ColorUtil.getRect(1)).build());

        Fonts.getSize(14).drawString(matrix, LocalizationManager.getInstance().get("ui.binding_module"), x + 5, y + 8, -1);

        image.setTexture("textures/trash.png").render(ShapeProperties.create(matrix, x + width - 13, y + 5.3f, 8, 8).build());

        drawKeyButton(matrix);
        drawTypeButton(matrix);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (MathUtil.isHovered(mouseX, mouseY, x + width - 57, y + 37F, 52, 13)) {
                setType(getType() != 1 ? 1 : 0);
            }

            float stringWidth = Fonts.getSize(14).getStringWidth(StringUtil.getBindName(getKey()));

            if (MathUtil.isHovered(mouseX, mouseY, x + width - stringWidth - 15, y + 18.8F, stringWidth + 10, 13)) {
                binding = !binding;
            }

            if (MathUtil.isHovered(mouseX, mouseY, x + width - 13, y + 5.3f, 8, 8)) {
                setKey(-1);
                if (this instanceof ModuleBindWindow) {
                    ((ModuleBindWindow) this).getModule().setState(false);
                }
            }
        }

        if (binding && button > 1) {
            setKey(button);
            binding = false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int key = keyCode == GLFW.GLFW_KEY_DELETE ? -1 : keyCode;
        if (binding) {
            setKey(key);
            binding = false;
            if (key == -1 && this instanceof ModuleBindWindow) {
                ((ModuleBindWindow) this).getModule().setState(false);
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }


    private void drawKeyButton(MatrixStack matrix) {
        float stringWidth = Fonts.getSize(14).getStringWidth(StringUtil.getBindName(getKey()));

        rectangle.render(ShapeProperties.create(matrix, x + width - stringWidth - 15, y + 18.8F, stringWidth + 10, 13)
                .round(2).thickness(2).softness(1).outlineColor(ColorUtil.getOutline(0.8F,1)).color(ColorUtil.getOutline(0.1F,1)).build());

        int bindingColor = binding ? 0xFF8187FF : ColorUtil.getText();

        Fonts.getSize(14).drawString(matrix, StringUtil.getBindName(getKey()), x + width - 10 - stringWidth, y + 23.6F, bindingColor);
        Fonts.getSize(14).drawString(matrix, LocalizationManager.getInstance().get("ui.key"), (int) (x + 5), (int) (y + 24.3), ColorUtil.getText());
    }

    private void drawTypeButton(MatrixStack matrix) {
        rectangle.render(ShapeProperties.create(matrix, x + width - 57, y + 37F, 52, 13)
                .round(2).thickness(2).softness(1).outlineColor(ColorUtil.getOutline(0.8F,1)).color(ColorUtil.getOutline(0.1F,1)).build());

        if (getType() == 1) {
            rectangle.render(ShapeProperties.create(matrix, x + width - 34, y + 37F, 29, 13)
                    .round(2, 2, 0, 0).color(0xFF8187FF).build());
        } else {
            rectangle.render(ShapeProperties.create(matrix, x + width - 57, y + 37F, 23, 13)
                    .round(0, 0, 2, 2).color(0xFF8187FF).build());
        }

        Fonts.getSize(12).drawString(matrix, LocalizationManager.getInstance().get("ui.hold"), x + 52, y + 42.3, ColorUtil.getText());
        Fonts.getSize(12).drawString(matrix, LocalizationManager.getInstance().get("ui.toggle"), x + 73, y + 42.3, ColorUtil.getText());

        Fonts.getSize(14).drawString(matrix, LocalizationManager.getInstance().get("ui.bind_mode"), (int) (x + 5), (int) (y + 42.3F), ColorUtil.getText());
    }
}