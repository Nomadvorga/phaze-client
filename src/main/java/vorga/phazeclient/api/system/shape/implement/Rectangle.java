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
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.base.QuickImports;
import vorga.phazeclient.api.system.shape.Shape;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.color.ColorUtil;

public class Rectangle implements Shape, QuickImports {
    private final ShaderProgramKey SHADER_KEY = new ShaderProgramKey(Identifier.of("phaze", "core/round"), VertexFormats.POSITION, Defines.EMPTY);

    @Override
    public void render(ShapeProperties shape) {
        // Fast batched path: if a BatchedRectangle scope is open
        // (currently entered for menu rendering only), defer this rect
        // into the shared BufferBuilder instead of issuing its own
        // BufferBuilder.begin -> 8 uniform sets -> drawWithGlobalProgram
        // -> disableBlend cycle. Outside of a batching scope (HUD,
        // standalone components, etc.) we keep the original eager path
        // below so unrelated callers see no behavioral change. The
        // batched path produces pixel-identical output - same SDF math,
        // same gradient bilinear, same outline blend - because
        // BatchedRectangle replicates Rectangle's per-rect math and
        // the round_batched shader is a direct port of round.fsh with
        // the uniforms reshaped as per-vertex attributes.
        if (BatchedRectangle.isBatching()) {
            BatchedRectangle.submit(shape);
            return;
        }

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

        float softness = shape.getSoftness();
        float thickness = shape.getThickness();
        float width = shape.getWidth() * size.x;
        float height = shape.getHeight() * size.y;

        BufferBuilder buffer = tessellator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        drawEngine.quad(matrix4f, buffer, shape.getX() - softness / 2, shape.getY() - softness / 2, shape.getWidth() + softness, shape.getHeight() + softness);

        ShaderProgram shader = RenderSystem.setShader(SHADER_KEY);

        if (shader == null) return;
        shader.getUniformOrDefault("size").set(width, height);
        // Use the cross-cutting active render-target FB height instead
        // of the raw main-window value: while CardSnapshotCache has
        // bound a card snapshot FBO, gl_FragCoord ranges over the
        // small card box and the SDF location uniform must match.
        // This eager path normally only runs when BatchedRectangle is
        // disabled (Sodium / Iris claimed all GENERIC vertex slots),
        // but FBO capture still routes through here in that fallback
        // mode and would otherwise punch out every fragment.
        shader.getUniformOrDefault("location").set(pos.x, BatchedRectangle.getActiveFbHeight() - height - pos.y);
        shader.getUniformOrDefault("radius").set(round);
        shader.getUniformOrDefault("softness").set(softness);
        shader.getUniformOrDefault("thickness").set(thickness);
        shader.getUniformOrDefault("color1").set(ColorUtil.redf(shape.getColor().x), ColorUtil.greenf(shape.getColor().x), ColorUtil.bluef(shape.getColor().x), ColorUtil.alphaf(ColorUtil.multAlpha(shape.getColor().x, alpha)));
        shader.getUniformOrDefault("color2").set(ColorUtil.redf(shape.getColor().y), ColorUtil.greenf(shape.getColor().y), ColorUtil.bluef(shape.getColor().y), ColorUtil.alphaf(ColorUtil.multAlpha(shape.getColor().y, alpha)));
        shader.getUniformOrDefault("color3").set(ColorUtil.redf(shape.getColor().z), ColorUtil.greenf(shape.getColor().z), ColorUtil.bluef(shape.getColor().z), ColorUtil.alphaf(ColorUtil.multAlpha(shape.getColor().z, alpha)));
        shader.getUniformOrDefault("color4").set(ColorUtil.redf(shape.getColor().w), ColorUtil.greenf(shape.getColor().w), ColorUtil.bluef(shape.getColor().w), ColorUtil.alphaf(ColorUtil.multAlpha(shape.getColor().w, alpha)));
        shader.getUniformOrDefault("outlineColor").set(ColorUtil.redf(shape.getOutlineColor()), ColorUtil.greenf(shape.getOutlineColor()), ColorUtil.bluef(shape.getOutlineColor()), ColorUtil.alphaf(ColorUtil.multAlpha(shape.getOutlineColor(), alpha)));

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }
}
