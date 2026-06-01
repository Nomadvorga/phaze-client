package vorga.phazeclient.implement.menu.components.implement.window;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.components.AbstractComponent;

public abstract class AbstractWindow extends AbstractComponent {
    private boolean dragging, draggable;
    private int dragX, dragY;

    @Getter
    private final Animation scaleAnimation = new DecelerateAnimation()
            .setValue(1).setMs(200);

    public AbstractWindow() {
        scaleAnimation.setDirection(Direction.FORWARDS);
    }

    public AbstractWindow draggable(boolean draggable) {
        this.draggable = draggable;
        return this;
    }

    @Override
    public AbstractWindow size(float width, float height) {
        this.width = width;
        this.height = height;
        return this;
    }

    @Override
    public AbstractWindow position(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY) && button == 0 && draggable) {
            dragging = true;
            dragX = (int) (x - mouseX);
            dragY = (int) (y - mouseY);
            return true;
        }
        return false;
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (dragging && draggable) {
            x = mouseX + dragX;
            y = mouseY + dragY;

            int screenWidth = MenuScreen.INSTANCE.getOverlayViewportWidth();
            int screenHeight = MenuScreen.INSTANCE.getOverlayViewportHeight();
            x = Math.max(0, Math.min(x, screenWidth - width));
            y = Math.max(0, Math.min(y, screenHeight - height));
        }

        float scale = scaleAnimation.getOutputFloat();
        float alpha = scale * scale;
        context.draw();
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 280.0F);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        MathUtil.scale(context.getMatrices(), x + width / 2, y + height / 2, scale, () -> {
            this.globalAlpha = alpha;
            drawWindow(context, mouseX, mouseY, delta);
        });
        BatchedRectangle.flushIfBatching();
        context.draw();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        context.getMatrices().pop();
    }

    protected abstract void drawWindow(DrawContext context, int mouseX, int mouseY, float delta);

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return true;
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public void startCloseAnimation() {
        scaleAnimation.setDirection(Direction.BACKWARDS);
    }

    public boolean isCloseAnimationFinished() {
        return scaleAnimation.isFinished(Direction.BACKWARDS);
    }
}
