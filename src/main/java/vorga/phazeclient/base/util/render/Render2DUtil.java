package vorga.phazeclient.base.util.render;

import com.mojang.blaze3d.vertex.VertexFormat;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import vorga.phazeclient.api.system.draw.PhazeAlpha;
import vorga.phazeclient.api.system.draw.PhazeDrawLayers;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.implement.Blur;
import vorga.phazeclient.base.QuickImports;
import vorga.phazeclient.base.util.color.ColorUtil;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class Render2DUtil implements QuickImports {
    private final List<Quad> QUAD = new ArrayList<>();

    public void onRender(DrawContext context) {
        Matrix3x2fStack matrix = context.getMatrices();
        Matrix4f matrix4f = GuiMatrix.mat4(matrix);
        if (!QUAD.isEmpty()) {
            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            QUAD.forEach(quad -> drawEngine.quad(matrix4f, buffer, quad.x, quad.y, quad.width, quad.height, quad.color));
            PhazeDrawLayers.POSITION_COLOR.draw(buffer.end());
            QUAD.clear();
        }
    }

    public void defaultDrawStack(@NonNull DrawContext context, @NonNull ItemStack stack, float x, float y, boolean rect, boolean drawItemInSlot, float scale) {
        Matrix3x2fStack matrix = context.getMatrices();
        if (rect) Blur.INSTANCE.render(ShapeProperties.create(matrix, x, y, 16 * scale + 2, 16 * scale + 2)
                .round(2).color(ColorUtil.HALF_BLACK).build());
        matrix.pushMatrix();
        matrix.translate(x + 1, y + 1);
        matrix.scale(scale, scale);
        context.drawItem(stack, 0, 0);
        MinecraftClient client = MinecraftClient.getInstance();
        if (drawItemInSlot && client != null) context.drawStackOverlay(client.textRenderer, stack, 0, 0);
        matrix.popMatrix();
    }

    public void drawTexture(@NonNull DrawContext context, Identifier id, float x, float y, float size, float round, int uvSize, int regionSize, int textureSize, int backgroundColor) {
        drawTexture(context, id, x, y, size, round, uvSize, regionSize, textureSize, backgroundColor, -1);
    }

    public void drawTexture(@NonNull DrawContext context, Identifier id, float x, float y, float size, float round, int uvSize, int regionSize, int textureSize, int backgroundColor, int color) {
        Matrix3x2fStack matrix = context.getMatrices();
        rectangle.render(ShapeProperties.create(matrix, x, y, size, size).round(round).color(backgroundColor).build());

        if (id != null) {
            matrix.pushMatrix();
            matrix.translate(x, y);
            matrix.scale(size, size);

            drawTexture(matrix, id, 0, 0, 1, 1, uvSize, uvSize, regionSize, regionSize, textureSize, textureSize, color);

            matrix.translate(-x, -y);
            matrix.popMatrix();
        }
    }

    public void drawHead(@NonNull DrawContext context, Identifier id, float x, float y, float size, float round, int backgroundColor, int color) {
        Matrix3x2fStack matrix = context.getMatrices();
        rectangle.render(ShapeProperties.create(matrix, x, y, size, size).round(round).color(backgroundColor).build());

        if (id != null) {
            matrix.pushMatrix();
            matrix.translate(x, y);
            matrix.scale(size, size);

            // 1.21.11: the two GL40C.glTexParameteri(GL_TEXTURE_2D, ..., GL_NEAREST) calls that
            // used to sit here are gone. Filtering is no longer per-bound-texture GL state; it is
            // a GpuSampler chosen when the draw's RenderSetup binds the texture, and no texture is
            // bound at this point any more (RenderLayer.draw binds it). Poking raw GL here would
            // also desync GlCommandEncoder's cached state. The skin texture's own sampler applies.
            // TODO(1.21.11): if heads come out filtered, give PhazeDrawLayers a NEAREST variant via
            // RenderSetup.Builder.texture(name, id, () -> RenderSystem.getSamplerCache().get(FilterMode.NEAREST)).
            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            Matrix4f matrix4f = GuiMatrix.mat4(matrix);

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

            PhazeDrawLayers.positionTexColor(id).draw(buffer.end());

            matrix.translate(-x, -y);
            matrix.popMatrix();
        }
    }

    // 1.21.11: these four used to take a MatrixStack, which is what DrawContext.getMatrices()
    // returned. The GUI pose is an org.joml.Matrix3x2fStack now, so they take Matrix3x2fc - the
    // read-only view every 2D pose (including Matrix3x2fStack) satisfies. Callers that pass
    // context.getMatrices() compile unchanged; world-space MatrixStack never reached these.
    public void drawSprite(@NonNull Matrix3x2fc matrix, @NonNull Sprite sprite, float x, float y, float width, int height) {
        drawSprite(matrix, sprite, x, y, width, height, -1);
    }

    public void drawSprite(@NonNull Matrix3x2fc matrix, @NonNull Sprite sprite, float x, float y, float width, int height, int color) {
        if (width != 0 && height != 0) {
            drawTexturedQuad(matrix, sprite.getAtlasId(), x, x + width, y, y + height, sprite.getMinU(), sprite.getMaxU(), sprite.getMinV(), sprite.getMaxV(), color);
        }
    }

    public void drawTexture(@NonNull Matrix3x2fc matrix, @NonNull Identifier texture, int x, int y, float width, float height, float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        drawTexture(matrix, texture, x, x + width, y, y + height, 0, regionWidth, regionHeight, u, v, textureWidth, textureHeight, color);
    }

    public void drawTexture(@NonNull Matrix3x2fc matrix, @NonNull Identifier texture, float x1, float x2, float y1, float y2, float z, int regionWidth, int regionHeight, float u, float v, int textureWidth, int textureHeight, int color) {
        drawTexturedQuad(matrix, texture, x1, x2, y1, y2, (u + 0.0F) / (float) textureWidth, (u + (float) regionWidth) / (float) textureWidth, (v + 0.0F) / (float) textureHeight, (v + (float) regionHeight) / (float) textureHeight, color);
    }

    public void drawTexturedQuad(@NonNull Matrix3x2fc matrix, @NonNull Identifier texture, float x1, float x2, float y1, float y2, float u1, float u2, float v1, float v2, int color) {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        Matrix4f matrix4f = GuiMatrix.mat4(matrix);
        buffer.vertex(matrix4f, x1, y1, 0).texture(u1, v1).color(color);
        buffer.vertex(matrix4f, x1, y2, 0).texture(u1, v2).color(color);
        buffer.vertex(matrix4f, x2, y2, 0).texture(u2, v2).color(color);
        buffer.vertex(matrix4f, x2, y1, 0).texture(u2, v1).color(color);
        PhazeDrawLayers.positionTexColor(texture).draw(buffer.end());
    }

    public void drawQuad(float x, float y, float width, float height, int color) {
        // 1.21.11: RenderSystem.getShaderColor() is gone; the global alpha lives in PhazeAlpha now.
        QUAD.add(new Quad(x, y, width, height, ColorUtil.multAlpha(color, PhazeAlpha.get())));
    }

    public record Quad(float x, float y, float width, float height, int color) {
    }
}
