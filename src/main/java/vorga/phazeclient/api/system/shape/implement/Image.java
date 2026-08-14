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
     *
     * <p>The pipeline is vanilla's {@link RenderPipelines#GUI_TEXTURED}:
     * {@code core/position_tex_color}, {@code Sampler0}, translucent
     * blend, {@code POSITION_TEXTURE_COLOR} quads, no depth test - which
     * is exactly the state the 1.21.4 path set by hand
     * ({@code POSITION_TEX_COLOR} + {@code defaultBlendFunc}), and it
     * comes with the shader's uniform-block declarations already
     * attached.
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

        // 1.21.11: the GUI pose is a 2D Matrix3x2f, not a MatrixStack.
        // ShapeProperties already hands out an owned copy, but this is
        // mutated in place below so it is copied again rather than
        // relying on that.
        Matrix3x2f pose = new Matrix3x2f(shape.getMatrix());

        Identifier textureId = Identifier.of(texture);
        // No imperative texture bind or glTexParameteri here any more: in
        // 1.21.11 the sampler is declared on the layer's RenderSetup, and
        // filtering is a property of the sampler rather than of whatever
        // texture happens to be bound to unit 0 at this moment.
        // enableBlend/defaultBlendFunc are gone too - blending is the
        // pipeline's BlendFunction.TRANSLUCENT.

        float width = shape.getWidth();
        float x = shape.getX() + width;
        float y = shape.getY();

        // Was push / translate / multiply(POSITIVE_Z.rotationDegrees) /
        // translate / pop on the 4x4 stack. Matrix3x2f.rotate is a Z
        // rotation by definition and takes RADIANS, and the pose is a
        // local copy so there is nothing to pop.
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

        // 1.21.4 got the menu fade for free: the global shader colour was
        // a real uniform in position_tex_color.fsh, so a faded component
        // faded its icons too. There is no global colour in 1.21.11, so
        // the fade has to be baked into the vertex colour - same thing
        // Arc, Blur and BatchedRectangle do.
        int color = PhazeAlpha.tint(shape.getColor().x);

        Matrix4f positionMatrix = GuiMatrix.mat4(pose);

        // UiMsdfIconAtlas keys its "legacy image orientation" winding (the
        // 90-degree-rotated UVs this shape has always used) off the
        // MatrixStack overload, so the promoted pose is handed over in a
        // throwaway stack rather than through the Matrix4f overload,
        // which would silently flip the icon.
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
        // 1.21.11 defers DrawContext work into a GuiRenderState, so this
        // immediate draw runs outside the GUI pass and must install the
        // GUI ortho projection (and its z = -11000 model-view) itself.
        vorga.phazeclient.api.system.draw.GuiProjection.begin();
        try {
            layerFor(textureId).draw(buffer.end());
        } finally {
            vorga.phazeclient.api.system.draw.GuiProjection.end();
        }
    }
}
