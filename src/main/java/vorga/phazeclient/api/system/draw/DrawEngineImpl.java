package vorga.phazeclient.api.system.draw;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import vorga.phazeclient.base.QuickImports;

import static com.mojang.blaze3d.vertex.VertexFormat.DrawMode.QUADS;
import static net.minecraft.client.render.VertexFormats.POSITION_TEXTURE_COLOR;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DrawEngineImpl implements DrawEngine, QuickImports {

    private static final RenderLayer TEXTURED_QUAD_LAYER = RenderLayer.of(
            "phaze_textured_quad",
            RenderSetup.builder(RenderPipelines.GUI_TEXTURED).translucent().build());

    @Override
    public void quad(Matrix4f matrix4f, BufferBuilder buffer, float x, float y, float width, float height) {
        buffer.vertex(matrix4f, x, y, 0);
        buffer.vertex(matrix4f, x, y + height, 0);
        buffer.vertex(matrix4f, x + width, y + height, 0);
        buffer.vertex(matrix4f, x + width, y, 0);
    }

    @Override
    public void quad(Matrix4f matrix4f, BufferBuilder buffer, float x, float y, float width, float height, int color) {
        buffer.vertex(matrix4f, x, y, 0).color(color);
        buffer.vertex(matrix4f, x, y + height, 0).color(color);
        buffer.vertex(matrix4f, x + width, y + height, 0).color(color);
        buffer.vertex(matrix4f, x + width, y, 0).color(color);
    }

    @Override
    public void quad(Matrix4f matrix4f, float x, float y, float width, float height, int color) {
        BufferBuilder buffer = tessellator().begin(QUADS, POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix4f, x, y + height, 0).texture(0, 0).color(color);
        buffer.vertex(matrix4f, x + width, y + height, 0).texture(0, 1).color(color);
        buffer.vertex(matrix4f, x + width, y, 0).texture(1, 1).color(color);
        buffer.vertex(matrix4f, x, y, 0).texture(1, 0).color(color);

        vorga.phazeclient.api.system.draw.GuiProjection.begin();
        try {
            TEXTURED_QUAD_LAYER.draw(buffer.end());
        } finally {
            vorga.phazeclient.api.system.draw.GuiProjection.end();
        }
    }
}
