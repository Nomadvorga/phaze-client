package vorga.phazeclient.base.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import vorga.phazeclient.base.QuickImports;

import java.util.Stack;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ScissorManager implements QuickImports {
    Stack<Scissor> scissorStack = new Stack<>();

    public void push(Matrix4f matrix4f, float x, float y, float width, float height) {
        // Drain any pending batched rectangles BEFORE the scissor box
        // changes. glClear and rasterization are both gated by the
        // current scissor; if we left rects queued they would either
        // be clipped to the new (potentially smaller) scissor when
        // flushed later or rasterize against the wrong clip box. The
        // legacy eager Rectangle.render path implicitly issued every
        // rect immediately so the scissor active at submit time was
        // ALWAYS the scissor active at draw time - this flush keeps
        // that invariant under the deferred batched path.
        vorga.phazeclient.api.system.shape.batched.BatchedRectangle.flushIfBatching();

        Vector3f pos = matrix4f.transformPosition(x,y,0, new Vector3f());
        Vector3f size = matrix4f.getScale(new Vector3f()).mul(width, height, 0);

        Scissor newScissor = new Scissor();

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
        // Same reasoning as push(): rects submitted under this scissor
        // must rasterize before the scissor pops to a wider box.
        vorga.phazeclient.api.system.shape.batched.BatchedRectangle.flushIfBatching();

        if (!scissorStack.isEmpty()) {
            scissorStack.pop();
            if (scissorStack.isEmpty()) {
                RenderSystem.disableScissor();
            } else {
                setScissor(scissorStack.peek());
            }
        }
    }

    private void setScissor(Scissor scissor) {
        int scaleFactor = (int) window().getScaleFactor();
        int x = scissor.x * scaleFactor;
        int y = window().getHeight() - (scissor.y * scaleFactor + scissor.height * scaleFactor);
        int width = scissor.width * scaleFactor;
        int height = scissor.height * scaleFactor;

        RenderSystem.enableScissor(x, y, width, height);
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
