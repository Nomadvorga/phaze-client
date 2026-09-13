package vorga.phazeclient.api.system.shape.implement;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;

import java.util.HashMap;
import java.util.Map;
import lombok.Setter;
import lombok.experimental.Accessors;
import vorga.phazeclient.api.system.draw.PhazeAlpha;
import vorga.phazeclient.api.system.shape.Shape;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.base.QuickImports;
import vorga.phazeclient.base.util.render.GuiMatrix;
import vorga.phazeclient.implement.menu.UiMsdfIconAtlas;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

@Setter
@Accessors(chain = true)
public class Image implements Shape, QuickImports {

    private static final Map<Identifier, RenderLayer> TEXTURED_LAYERS = new HashMap<>();

    private String texture;

    private static RenderLayer layerFor(Identifier textureId) {
        return TEXTURED_LAYERS.computeIfAbsent(textureId, id -> RenderLayer.of(
                "phaze_image_" + id.getNamespace() + "_" + id.getPath().replace('/', '_'),
                RenderSetup.builder(RenderPipelines.GUI_TEXTURED)
                        .texture("Sampler0", id)
                        .translucent()
                        .build()));
    }

    @Override
    public void render(ShapeProperties shape) {

        BatchedRectangle.flushIfBatching();

        Matrix3x2f pose = new Matrix3x2f(shape.getMatrix());

        Identifier textureId = Identifier.of(texture);

        float width = shape.getWidth();
        float x = shape.getX() + width;
        float y = shape.getY();

        pose.translate(x, y);
        pose.rotate((float) Math.toRadians(shape.getRotation()));
        pose.translate(-x, -y);

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

        int color = PhazeAlpha.tint(shape.getColor().x);

        Matrix4f positionMatrix = GuiMatrix.mat4(pose);

        MatrixStack atlasPose = new MatrixStack();
        atlasPose.multiplyPositionMatrix(positionMatrix);

        if (!UiMsdfIconAtlas.renderIcon(atlasPose, textureId, drawX, drawY, drawWidth, drawHeight, color)) {
            renderRawTexture(positionMatrix, textureId, drawX, drawY, drawWidth, drawHeight, color);
        }
    }

    private static void renderRawTexture(Matrix4f positionMatrix, Identifier textureId, float x, float y, float width, float height, int color) {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(positionMatrix, x, y, 0.0F).texture(0.0F, 0.0F).color(color);
        buffer.vertex(positionMatrix, x, y + height, 0.0F).texture(0.0F, 1.0F).color(color);
        buffer.vertex(positionMatrix, x + width, y + height, 0.0F).texture(1.0F, 1.0F).color(color);
        buffer.vertex(positionMatrix, x + width, y, 0.0F).texture(1.0F, 0.0F).color(color);

        vorga.phazeclient.api.system.draw.GuiProjection.begin();
        try {
            layerFor(textureId).draw(buffer.end());
        } finally {
            vorga.phazeclient.api.system.draw.GuiProjection.end();
        }
    }
}
