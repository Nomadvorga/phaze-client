package vorga.phazeclient.api.system.shape.implement;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import lombok.Setter;
import lombok.experimental.Accessors;
import vorga.phazeclient.api.system.shape.Shape;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.base.QuickImports;
import vorga.phazeclient.implement.menu.UiMsdfIconAtlas;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
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

        Identifier textureId = Identifier.of(texture);
        RenderSystem.setShaderTexture(0, textureId);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        float width = shape.getWidth();
        float x = shape.getX() + width;
        float y = shape.getY();

        matrix.push();
        matrix.translate(x, y, 0.0F);
        matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(shape.getRotation()));
        matrix.translate(-x, -y, 0.0F);

        float rawWidth = shape.getHeight();
        float rawHeight = width;
        float aspectRatio = UiMsdfIconAtlas.resolveAspectRatio(textureId);
        float drawWidth = rawWidth;
        float drawHeight = drawWidth / Math.max(0.0001F, aspectRatio);
        if (drawHeight > rawHeight) {
            drawHeight = rawHeight;
            drawWidth = drawHeight * Math.max(0.0001F, aspectRatio);
        }
        float drawX = x + (rawWidth - drawWidth) * 0.5F;
        float drawY = y + (rawHeight - drawHeight) * 0.5F;

        if (!UiMsdfIconAtlas.renderIcon(matrix, textureId, drawX, drawY, drawWidth, drawHeight, shape.getColor().x)) {
            renderRawTexture(matrix, textureId, drawX, drawY, drawWidth, drawHeight, shape.getColor().x);
        }

        matrix.pop();

        RenderSystem.disableBlend();
    }

    private static void renderRawTexture(MatrixStack matrix, Identifier textureId, float x, float y, float width, float height, int color) {
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        Matrix4f positionMatrix = matrix.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(positionMatrix, x, y, 0.0F).texture(0.0F, 0.0F).color(color);
        buffer.vertex(positionMatrix, x, y + height, 0.0F).texture(0.0F, 1.0F).color(color);
        buffer.vertex(positionMatrix, x + width, y + height, 0.0F).texture(1.0F, 1.0F).color(color);
        buffer.vertex(positionMatrix, x + width, y, 0.0F).texture(1.0F, 0.0F).color(color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
}
