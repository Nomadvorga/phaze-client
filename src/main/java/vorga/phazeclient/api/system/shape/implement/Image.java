package vorga.phazeclient.api.system.shape.implement;

import com.mojang.blaze3d.systems.RenderSystem;
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
    /**
     * One {@link RenderLayer} per texture.
     *
     * <p>1.21.11 no longer binds textures imperatively -
     * {@code RenderSystem.setShaderTexture} is not part of the draw any
     * more. The sampler binding is declared on the {@link RenderSetup},
     * so a layer is specific to the texture it samples and has to be
     * built once per texture rather than per draw. The set of UI textures
     * is small and fixed, so a plain map is enough; entries are created
     * lazily on first use and live for the process.
     */
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
        // Drain pending batched rects so this textured quad lands ABOVE
        // them in draw order. The image draw uses its own
        // POSITION_TEXTURE_COLOR BufferBuilder which would otherwise
        // collide with the BatchedRectangle's currently-open
        // POSITION+GENERIC builder on the shared Tessellator.
        BatchedRectangle.flushIfBatching();

        MatrixStack matrix = shape.getMatrix();


        Identifier textureId = Identifier.of(texture);
        // No imperative texture bind or glTexParameteri here any more: in
        // 1.21.11 the sampler is declared on the layer's RenderSetup, and
        // filtering is a property of the sampler rather than of whatever
        // texture happens to be bound to unit 0 at this moment.

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

    }

    private static void renderRawTexture(MatrixStack matrix, Identifier textureId, float x, float y, float width, float height, int color) {
        Matrix4f positionMatrix = matrix.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(positionMatrix, x, y, 0.0F).texture(0.0F, 0.0F).color(color);
        buffer.vertex(positionMatrix, x, y + height, 0.0F).texture(0.0F, 1.0F).color(color);
        buffer.vertex(positionMatrix, x + width, y + height, 0.0F).texture(1.0F, 1.0F).color(color);
        buffer.vertex(positionMatrix, x + width, y, 0.0F).texture(1.0F, 0.0F).color(color);
        layerFor(textureId).draw(buffer.end());
    }
}
