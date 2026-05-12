package vorga.phazeclient.api.system.shape.implement;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import vorga.phazeclient.base.QuickImports;
import vorga.phazeclient.api.system.shape.Shape;
import vorga.phazeclient.api.system.shape.ShapeProperties;

public class InvertedRectangle implements Shape, QuickImports {
    private final ShaderProgramKey SHADER_KEY = new ShaderProgramKey(Identifier.of("phaze", "core/round_inverted"), VertexFormats.POSITION, Defines.EMPTY);

    @Override
    public void render(ShapeProperties shape) {
        vorga.phazeclient.api.system.shape.batched.BatchedRectangle.flushIfBatching();

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.ONE_MINUS_DST_COLOR,
                GlStateManager.DstFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SrcFactor.ONE,
                GlStateManager.DstFactor.ZERO
        );
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();

        if (window() == null) return;
        float scale = (float) window().getScaleFactor();

        Matrix4f matrix4f = shape.getMatrix().peek().getPositionMatrix();
        Vector3f pos = matrix4f.transformPosition(shape.getX(), shape.getY(), 0, new Vector3f()).mul(scale);
        Vector3f size = matrix4f.getScale(new Vector3f()).mul(scale);
        Vector4f round = shape.getRound().mul(size.y);

        float softness = shape.getSoftness();
        float width = shape.getWidth() * size.x;
        float height = shape.getHeight() * size.y;

        BufferBuilder buffer = tessellator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        drawEngine.quad(matrix4f, buffer, shape.getX() - softness / 2, shape.getY() - softness / 2, shape.getWidth() + softness, shape.getHeight() + softness);

        ShaderProgram shader = RenderSystem.setShader(SHADER_KEY);

        if (shader == null) return;
        shader.getUniformOrDefault("size").set(width, height);
        shader.getUniformOrDefault("location").set(pos.x, window().getHeight() - height - pos.y);
        shader.getUniformOrDefault("radius").set(round);
        shader.getUniformOrDefault("softness").set(softness);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }
}
