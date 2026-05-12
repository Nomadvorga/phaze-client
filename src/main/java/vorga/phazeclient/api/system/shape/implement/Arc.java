package vorga.phazeclient.api.system.shape.implement;

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
import vorga.phazeclient.api.system.shape.Shape;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.QuickImports;
import vorga.phazeclient.base.util.color.ColorUtil;

public class Arc implements Shape, QuickImports {
    private final ShaderProgramKey SHADER_KEY = new ShaderProgramKey(Identifier.of("phaze", "core/arc"), VertexFormats.POSITION, Defines.EMPTY);

    @Override
    public void render(ShapeProperties shape) {
        // Arc opens its own Tessellator BufferBuilder; the shared
        // Tessellator only allows ONE active buffer, so any pending
        // BatchedRectangle batch must flush first or its tessellator
        // .begin() would collide with ours.
        vorga.phazeclient.api.system.shape.batched.BatchedRectangle.flushIfBatching();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();

        if (window() == null) return;
        float scale = (float) window().getScaleFactor();
        float alpha = RenderSystem.getShaderColor()[3];

        Matrix4f matrix4f = shape.getMatrix().peek().getPositionMatrix();
        Vector3f pos = matrix4f.transformPosition(shape.getX(), shape.getY(), 0, new Vector3f()).mul(scale);
        Vector3f size = matrix4f.getScale(new Vector3f()).mul(scale);
        Vector4f round = shape.getRound().mul(size.y);

        float width = shape.getWidth() * size.x;
        float height = shape.getHeight() * size.y;

        BufferBuilder buffer = tessellator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        drawEngine.quad(matrix4f, buffer, shape.getX(), shape.getY(), shape.getWidth(), shape.getHeight());

        ShaderProgram shader = RenderSystem.setShader(SHADER_KEY);
        if (shader == null) return;
        shader.getUniformOrDefault("size").set(width, height);
        shader.getUniformOrDefault("location").set(pos.x, window().getHeight() - height - pos.y);
        shader.getUniformOrDefault("radius").set(round.x);
        shader.getUniformOrDefault("thickness").set(shape.getThickness());
        shader.getUniformOrDefault("start").set(shape.getStart());
        shader.getUniformOrDefault("end").set(shape.getEnd());
        shader.getUniformOrDefault("color1").set(ColorUtil.redf(shape.getColor().x),ColorUtil.greenf(shape.getColor().x),ColorUtil.bluef(shape.getColor().x),ColorUtil.alphaf(ColorUtil.multAlpha(shape.getColor().x,alpha)));
        shader.getUniformOrDefault("color2").set(ColorUtil.redf(shape.getColor().y),ColorUtil.greenf(shape.getColor().y),ColorUtil.bluef(shape.getColor().y),ColorUtil.alphaf(ColorUtil.multAlpha(shape.getColor().y,alpha)));

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }
}
