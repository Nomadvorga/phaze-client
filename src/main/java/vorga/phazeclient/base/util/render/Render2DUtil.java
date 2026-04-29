package vorga.phazeclient.base.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.implement.Blur;
import vorga.phazeclient.base.QuickImports;
import vorga.phazeclient.base.util.color.ColorUtil;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL40C;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class Render2DUtil implements QuickImports {
    private final List<Quad> QUAD = new ArrayList<>();

    public void onRender(DrawContext context) {
        MatrixStack matrix = context.getMatrices();
        Matrix4f matrix4f = matrix.peek().getPositionMatrix();
        if (!QUAD.isEmpty()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            QUAD.forEach(quad -> drawEngine.quad(matrix4f, buffer, quad.x, quad.y, quad.width, quad.height, quad.color));
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            RenderSystem.disableBlend();
            QUAD.clear();
        }
    }

    public void defaultDrawStack(@NonNull DrawContext context, @NonNull ItemStack stack, float x, float y, boolean rect, boolean drawItemInSlot, float scale) {
        MatrixStack matrix = context.getMatrices();
        if (rect) Blur.INSTANCE.render(ShapeProperties.create(matrix, x, y, 16 * scale + 2, 16 * scale + 2)
                .round(2).color(ColorUtil.HALF_BLACK).build());
        matrix.push();
        matrix.translate(x + 1, y + 1, 0);
        matrix.scale(scale, scale, 1);
        context.drawItem(stack, 0, 0);
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (drawItemInSlot && client != null) context.drawStackOverlay(client.textRenderer, stack, 0, 0);
        matrix.pop();
    }

    public void drawTexture(@NonNull DrawContext context, Identifier id, float x, float y, float size, float round, int uvSize, int regionSize, int textureSize, int backgroundColor) {
        drawTexture(context, id, x, y, size, round, uvSize, regionSize, textureSize, backgroundColor, -1);
    }

    public void drawTexture(@NonNull DrawContext context, Identifier id, float x, float y, float size, float round, int uvSize, int regionSize, int textureSize, int backgroundColor, int color) {
        MatrixStack matrix = context.getMatrices();
        rectangle.render(ShapeProperties.create(matrix, x, y, size, size).round(round).color(backgroundColor).build());

        if (id != null) {
            matrix.push();
            matrix.translate(x, y, 0);
            matrix.scale(size, size, 1);

            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL40C.GL_DST_ALPHA, GL40C.GL_ONE_MINUS_DST_ALPHA);
            drawTexture(matrix, id, 0, 0, 1, 1, uvSize, uvSize, regionSize, regionSize, textureSize, textureSize, color);
            RenderSystem.disableBlend();

            matrix.translate(-x, -y, 0);
            matrix.pop();
        }
    }

    public void drawHead(@NonNull DrawContext context, Identifier id, float x, float y, float size, float round, int backgroundColor, int color) {
        MatrixStack matrix = context.getMatrices();
        rectangle.render(ShapeProperties.create(matrix, x, y, size, size).round(round).color(backgroundColor).build());

        if (id != null) {
            matrix.push();
            matrix.translate(x, y, 0);
            matrix.scale(size, size, 1);

            RenderSystem.setShaderTexture(0, id);
            RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL40C.GL_DST_ALPHA, GL40C.GL_ONE_MINUS_DST_ALPHA);

            GL40C.glTexParameteri(GL40C.GL_TEXTURE_2D, GL40C.GL_TEXTURE_MIN_FILTER, GL40C.GL_NEAREST);
            GL40C.glTexParameteri(GL40C.GL_TEXTURE_2D, GL40C.GL_TEXTURE_MAG_FILTER, GL40C.GL_NEAREST);

            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            Matrix4f matrix4f = matrix.peek().getPositionMatrix();

            float u1_base = 8.0F / 64.0F;
            float u2_base = 16.0F / 64.0F;
            float v1_base = 8.0F / 64.0F;
            float v2_base = 16.0F / 64.0F;

            float u1_overlay = 40.0F / 64.0F;
            float u2_overlay = 48.0F / 64.0F;
            float v1_overlay = 8.0F / 64.0F;
            float v2_overlay = 16.0F / 64.0F;

            buffer.vertex(matrix4f, 0, 0, 0).texture(u1_base, v1_base).color(color);
            buffer.vertex(matrix4f, 0, 1, 0).texture(u1_base, v2_base).color(color);
            buffer.vertex(matrix4f, 1, 1, 0).texture(u2_base, v2_base).color(color);
            buffer.vertex(matrix4f, 1, 0, 0).texture(u2_base, v1_base).color(color);

            buffer.vertex(matrix4f, 0, 0, 0).texture(u1_overlay, v1_overlay).color(color);
            buffer.vertex(matrix4f, 0, 1, 0).texture(u1_overlay, v2_overlay).color(color);
            buffer.vertex(matrix4f, 1, 1, 0).texture(u2_overlay, v2_overlay).color(color);
            buffer.vertex(matrix4f, 1, 0, 0).texture(u2_overlay, v1_overlay).color(color);

            BufferRenderer.drawWithGlobalProgram(buffer.end());

            RenderSystem.disableBlend();

            matrix.translate(-x, -y, 0);
            matrix.pop();
        }
    }

    public void drawSprite(@NonNull MatrixStack matrix, @NonNull Sprite sprite, float x, float y, float width, int height) {
        drawSprite(matrix, sprite, x, y, width, height, -1);
    }

    public void drawSprite(@NonNull MatrixStack matrix, @NonNull Sprite sprite, float x, float y, float width, int height, int color) {
        if (width != 0 && height != 0) {
            drawTexturedQuad(matrix, sprite.getAtlasId(), x, x + width, y, y + height, sprite.getMinU(), sprite.getMaxU(), sprite.getMinV(), sprite.getMaxV(), color);
        }
    }

    public void drawTexture(@NonNull MatrixStack matrix, @NonNull Identifier texture, int x, int y, float width, float height, float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        drawTexture(matrix, texture, x, x + width, y, y + height, 0, regionWidth, regionHeight, u, v, textureWidth, textureHeight, color);
    }

    public void drawTexture(@NonNull MatrixStack matrix, @NonNull Identifier texture, float x1, float x2, float y1, float y2, float z, int regionWidth, int regionHeight, float u, float v, int textureWidth, int textureHeight, int color) {
        drawTexturedQuad(matrix, texture, x1, x2, y1, y2, (u + 0.0F) / (float) textureWidth, (u + (float) regionWidth) / (float) textureWidth, (v + 0.0F) / (float) textureHeight, (v + (float) regionHeight) / (float) textureHeight, color);
    }

    public void drawTexturedQuad(@NonNull MatrixStack matrix, @NonNull Identifier texture, float x1, float x2, float y1, float y2, float u1, float u2, float v1, float v2, int color) {
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        Matrix4f matrix4f = matrix.peek().getPositionMatrix();
        buffer.vertex(matrix4f, x1, y1, 0).texture(u1, v1).color(color);
        buffer.vertex(matrix4f, x1, y2, 0).texture(u1, v2).color(color);
        buffer.vertex(matrix4f, x2, y2, 0).texture(u2, v2).color(color);
        buffer.vertex(matrix4f, x2, y1, 0).texture(u2, v1).color(color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    public void drawQuad(float x, float y, float width, float height, int color) {
        QUAD.add(new Quad(x, y, width, height, ColorUtil.multAlpha(color, RenderSystem.getShaderColor()[3])));
    }

    public record Quad(float x, float y, float width, float height, int color) {
    }
}
