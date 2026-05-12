package vorga.phazeclient.api.system.shape.implement;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Setter;
import lombok.experimental.Accessors;
import vorga.phazeclient.api.system.shape.Shape;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.base.QuickImports;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.opengl.GL11;

@Setter
@Accessors(chain = true)
public class Image implements Shape, QuickImports {
    private String texture;

    @Override
    public void render(ShapeProperties shape) {
        // Drain pending batched rects so this textured quad lands ABOVE
        // them in draw order. The image draw uses its own
        // POSITION_TEXTURE_COLOR BufferBuilder which would otherwise
        // collide with the BatchedRectangle's currently-open
        // POSITION+GENERIC builder on the shared Tessellator.
        BatchedRectangle.flushIfBatching();

        MatrixStack matrix = shape.getMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        RenderSystem.setShaderTexture(0, Identifier.of(texture));

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        float width = shape.getWidth();
        float x = shape.getX() + width;
        float y = shape.getY();

        matrix.push();
        matrix.translate(x, y, 0.0F);
        matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(shape.getRotation()));
        matrix.translate(-x, -y, 0.0F);

        drawEngine.quad(matrix.peek().getPositionMatrix(), x, y, shape.getHeight(), width, shape.getColor().x);

        matrix.pop();

        RenderSystem.disableBlend();
    }
}
