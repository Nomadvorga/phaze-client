package vorga.phazeclient.base.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import vorga.phazeclient.base.QuickImports;

import java.util.ArrayDeque;
import java.util.Deque;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ScissorManager implements QuickImports {
    Deque<Scissor> scissorStack = new ArrayDeque<>();
    Deque<Scissor> freeScissors = new ArrayDeque<>();
    Vector3f scratchPosition = new Vector3f();
    Vector3f scratchSize = new Vector3f();

    public void push(Matrix4f matrix4f, float x, float y, float width, float height) {

        vorga.phazeclient.api.system.shape.batched.BatchedRectangle.flushIfBatching();

        Vector3f pos = matrix4f.transformPosition(x, y, 0, scratchPosition);
        Vector3f size = matrix4f.getScale(scratchSize).mul(width, height, 0);

        Scissor newScissor = freeScissors.pollFirst();
        if (newScissor == null) {
            newScissor = new Scissor();
        }

        if (!scissorStack.isEmpty()) {
            Scissor prevScissor = scissorStack.peek();

            double intersectX = Math.max(prevScissor.x, pos.x);
            double intersectY = Math.max(prevScissor.y, pos.y);
            double intersectWidth = Math.min(prevScissor.x + prevScissor.width, pos.x + size.x) - intersectX;
            double intersectHeight = Math.min(prevScissor.y + prevScissor.height, pos.y + size.y) - intersectY;

            newScissor.set(intersectX, intersectY, intersectWidth, intersectHeight);
        } else {
            newScissor.set(pos.x, pos.y, size.x, size.y);
        }

        scissorStack.push(newScissor);
        setScissor(newScissor);
    }

    public void pop() {

        vorga.phazeclient.api.system.shape.batched.BatchedRectangle.flushIfBatching();

        if (!scissorStack.isEmpty()) {
            freeScissors.addFirst(scissorStack.pop());
            if (scissorStack.isEmpty()) {
                RenderSystem.disableScissorForRenderTypeDraws();
            } else {
                setScissor(scissorStack.peek());
            }
        }
    }

    public boolean fullyContains(Matrix4f matrix4f, float x, float y, float width, float height) {
        Scissor active = scissorStack.peek();
        if (active == null) {
            return true;
        }
        Vector3f pos = matrix4f.transformPosition(x, y, 0, scratchPosition);
        Vector3f size = matrix4f.getScale(scratchSize).mul(width, height, 0);
        return pos.x >= active.x
                && pos.y >= active.y
                && pos.x + size.x <= active.x + active.width
                && pos.y + size.y <= active.y + active.height;
    }

    private void setScissor(Scissor scissor) {
        int scaleFactor = (int) window().getScaleFactor();
        int x = scissor.x * scaleFactor;
        int y = window().getHeight() - (scissor.y * scaleFactor + scissor.height * scaleFactor);
        int width = scissor.width * scaleFactor;
        int height = scissor.height * scaleFactor;

        RenderSystem.enableScissorForRenderTypeDraws(x, y, width, height);
    }

    private static class Scissor {
        public int x, y;
        public int width, height;

        public void set(double x, double y, double width, double height) {
            this.x = Math.max(0, (int) Math.round(x));
            this.y = Math.max(0, (int) Math.round(y));
            this.width = Math.max(0, (int) Math.round(width));
            this.height = Math.max(0, (int) Math.round(height));
        }

        Scissor copy() {
            Scissor newScissor = new Scissor();
            newScissor.set(this.x, this.y, this.width, this.height);
            return newScissor;
        }
    }
}
