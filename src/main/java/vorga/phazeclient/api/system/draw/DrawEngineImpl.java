package vorga.phazeclient.api.system.draw;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import vorga.phazeclient.base.QuickImports;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import org.joml.Matrix4f;

import static net.minecraft.client.render.VertexFormat.DrawMode.QUADS;
import static net.minecraft.client.render.VertexFormats.POSITION_TEXTURE_COLOR;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DrawEngineImpl implements DrawEngine, QuickImports {
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
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        BufferBuilder buffer = tessellator().begin(QUADS, POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix4f, x, y + height, 0).texture(0, 0).color(color);
        buffer.vertex(matrix4f, x + width, y + height, 0).texture(0, 1).color(color);
        buffer.vertex(matrix4f, x + width, y, 0).texture(1, 1).color(color);
        buffer.vertex(matrix4f, x, y, 0).texture(1, 0).color(color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
}
