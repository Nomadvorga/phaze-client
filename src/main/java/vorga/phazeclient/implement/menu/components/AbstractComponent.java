package vorga.phazeclient.implement.menu.components;

import vorga.phazeclient.api.system.font.FontRenderer;
import vorga.phazeclient.base.QuickImports;
import vorga.phazeclient.base.trait.ResizableMovable;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.menu.MenuStyle;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

public abstract class AbstractComponent implements Component, QuickImports, ResizableMovable {
    public float x, y, width, height;
    public float globalAlpha = 1.0F;

    public double scroll = 0;
    public double smoothedScroll = 0;

    @Override
    public ResizableMovable position(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    @Override
    public ResizableMovable size(float width, float height) {
        this.width = width;
        this.height = height;
        return this;
    }

    @Override
    public void tick() {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return false;
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        return MathUtil.isHovered(mouseX, mouseY, x, y, width, height);
    }

    protected float centeredTextY(FontRenderer font, String text) {
        return MenuStyle.centerTextY(font, text, y, height);
    }

    protected float centeredTextY(FontRenderer font, String text, float boxY, float boxHeight) {
        return MenuStyle.centerTextY(font, text, boxY, boxHeight);
    }

    protected float centeredTextX(FontRenderer font, String text, float boxX, float boxWidth) {
        return MenuStyle.centerTextX(font, text, boxX, boxWidth);
    }

    protected float renderedTextHeight(FontRenderer font, String text) {
        return MenuStyle.renderedTextHeight(font, text);
    }

    protected void playButtonClickSound() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.getSoundManager() == null) {
            return;
        }

        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    protected int applyGlobalAlpha(int color) {
        return MenuStyle.withAlpha(color, globalAlpha);
    }

    protected float applyGlobalAlpha(float alpha) {
        return alpha * globalAlpha;
    }
}
