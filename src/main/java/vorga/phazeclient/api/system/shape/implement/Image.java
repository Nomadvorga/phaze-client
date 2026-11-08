package vorga.phazeclient.api.system.shape.implement;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import lombok.Setter;
import lombok.experimental.Accessors;
import vorga.phazeclient.api.system.shape.Shape;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.api.system.shape.batched.GuiBatch;
import vorga.phazeclient.base.QuickImports;
import vorga.phazeclient.base.util.color.ColorUtil;
import vorga.phazeclient.implement.menu.UiMsdfIconAtlas;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

@Setter
@Accessors(chain = true)
public class Image implements Shape, QuickImports {
    private static final java.util.Map<String, Identifier> IDENTIFIERS = new java.util.HashMap<>();
    private String texture;

    @Override
    public void render(ShapeProperties shape) {
        MatrixStack matrix = shape.getMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Parsing validates every path character; menus re-render the same few textures each frame.
        Identifier textureId = IDENTIFIERS.computeIfAbsent(texture, Identifier::of);
        // Filtering is defined by the image's .mcmeta at load time. Shader texture
        // selection is deferred and cannot make ambient glTexParameteri calls safe.

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
        Matrix4f positionMatrix = matrix.peek().getPositionMatrix();
        if (BatchedRectangle.isBatching()) {
            GuiBatch.texture(textureGlId(textureId)).quad(positionMatrix, x, y, width, height, 0.0F,
                    0.0F, 0.0F, 1.0F, 1.0F, ColorUtil.multShaderColor(color), 0);
            return;
        }
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        emitTexturedQuad(buffer, positionMatrix, x, y, width, height, color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    /** Unit-UV quad, in the vertex order the eager {@code POSITION_TEX_COLOR} path always used. */
    public static void emitTexturedQuad(VertexConsumer consumer, Matrix4f matrix, float x, float y, float width, float height, int color) {
        consumer.vertex(matrix, x, y, 0.0F).texture(0.0F, 0.0F).color(color);
        consumer.vertex(matrix, x, y + height, 0.0F).texture(0.0F, 1.0F).color(color);
        consumer.vertex(matrix, x + width, y + height, 0.0F).texture(1.0F, 1.0F).color(color);
        consumer.vertex(matrix, x + width, y, 0.0F).texture(1.0F, 0.0F).color(color);
    }

    /** GL name of a (lazily loaded) texture, as {@link RenderSystem#setShaderTexture(int, Identifier)} resolves it. */
    public static int textureGlId(Identifier textureId) {
        return MinecraftClient.getInstance().getTextureManager().getTexture(textureId).getGlId();
    }
}
