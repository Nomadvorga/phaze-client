package vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color.component;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.menu.components.AbstractComponent;

import static net.minecraft.util.math.MathHelper.clamp;

@RequiredArgsConstructor
public class SaturationComponent extends AbstractComponent {
    // Bounding-box size for the triangle indicator on both axes.
    // The actual visible triangle inside the PNG occupies roughly the
    // central two-thirds of the box, so 6 px gives a comfortable
    // visual size against the 6 px slider width without crowding the
    // adjacent alpha strip 3 px to the right.
    private static final float INDICATOR_SIZE = 6.0F;

    private final ColorSetting setting;
    private boolean saturationDragging;

    private float X, Y, W, H;


    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrix = context.getMatrices();

        // Vertical hue strip placed to the right of the saturation /
        // brightness picker (HueComponent at x+6 .. x+144 wide). The
        // 4px gap matches the spacing the alpha strip uses against
        // this one, keeping the right-side stack visually consistent.
        X = x + 148;
        Y = y + 10.5F;
        W = 6;
        H = 50;

        // Render the rainbow strip with a custom textured quad whose
        // UVs are swapped 90 degrees so the source texture (a single-
        // row left-to-right rainbow) gets sampled top-to-bottom: top
        // of the slider lands on texture u=0 (red), bottom lands on
        // u=1 (red wraparound). Image.render can't be reused for this
        // because it bakes a fixed +90 deg matrix rotation around
        // the rect's top-right corner, so any extra outer rotation
        // composes oddly with its internal pivot. A direct
        // POSITION_TEXTURE_COLOR draw with hand-picked UV mapping is
        // the cleanest way to render a rotated-texture rect at an
        // arbitrary axis-aligned position.
        renderVerticalGradientStrip(matrix, "textures/color_picker/hue.png", X, Y, W, H, applyGlobalAlpha(0xFFFFFFFF));

        // Selector indicator: a left-pointing triangle (apex toward
        // the slider, base on the right) anchored to the slider's
        // right edge at the current value's Y. The triangle source
        // PNG points DOWN (apex at bottom-center), so the helper
        // remaps UVs 90 deg CW to render it pointing LEFT without
        // having to push a rotation matrix. INDICATOR_SIZE doubles
        // as both width and height of the bounding square; the apex
        // lands at (X + W, apexY) = (slider right edge, value Y).
        float apexY = clamp(Y + H * setting.getHue(), Y, Y + H);
        float triX = X + W;
        float triY = apexY - INDICATOR_SIZE / 2.0F;
        renderLeftPointingTriangle(matrix, "textures/color_picker/triangle.png", triX, triY, INDICATOR_SIZE, INDICATOR_SIZE, applyGlobalAlpha(0xFFFFFFFF));

        if (saturationDragging) {
            setting.setHue(clamp((float) (mouseY - Y) / H, 0, 1));
        }
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        saturationDragging = button == 0 && MathUtil.isHovered(mouseX, mouseY, X, Y, W, H);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        saturationDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Draws a vertical rect at (x, y, w, h) and samples the supplied
     * texture so that the texture's LEFT edge (u = 0) lands on the
     * rect's TOP edge and the texture's RIGHT edge (u = 1) lands on
     * the rect's BOTTOM edge. Effectively a 90 deg CW rotation of
     * the texture without touching the matrix stack, which keeps
     * subsequent draws (selector indicator, alpha strip) in the
     * standard axis-aligned coordinate frame that BatchedRectangle's
     * SDF expects.
     *
     * <p>The pending BatchedRectangle queue is drained first so this
     * texture-shader draw lands above any rounded rects already
     * submitted in the same scope; otherwise the shared Tessellator
     * buffer would fight over the active vertex format between the
     * batched-rect GENERIC attributes and this draw's POSITION_
     * TEXTURE_COLOR layout.
     */
    private static void renderVerticalGradientStrip(MatrixStack matrix, String texture, float x, float y, float w, float h, int color) {
        BatchedRectangle.flushIfBatching();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, Identifier.of(texture));
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        // Linear filtering so the gradient interpolates smoothly when
        // a small source texture (e.g. 256x1 hue.png) gets stretched
        // across the slider's height.
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        Matrix4f mat = matrix.peek().getPositionMatrix();
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        // UV layout that rotates the texture 90 deg CW relative to a
        // straight POSITION_TEX_COLOR quad:
        //   slider TL  -> (u=0, v=0)  (texture left edge, top-of-row)
        //   slider BL  -> (u=1, v=0)  (texture right edge, top-of-row)
        //   slider BR  -> (u=1, v=1)  (texture right edge, bottom-of-row)
        //   slider TR  -> (u=0, v=1)  (texture left edge, bottom-of-row)
        // For a single-row source texture (hue.png is effectively 1px
        // tall after stretching), v doesn't matter visually, but the
        // 0..1 sweep along v keeps the quad well-defined in case a
        // future texture is multi-row.
        buf.vertex(mat, x,     y,     0).texture(0, 0).color(color);
        buf.vertex(mat, x,     y + h, 0).texture(1, 0).color(color);
        buf.vertex(mat, x + w, y + h, 0).texture(1, 1).color(color);
        buf.vertex(mat, x + w, y,     0).texture(0, 1).color(color);

        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }

    /**
     * Renders the source triangle texture (PNG drawn as a down-
     * pointing triangle with apex at bottom-center) rotated 90 deg CW
     * via UV remapping so the on-screen result is a LEFT-pointing
     * triangle (apex at left-center). Cleaner than pushing a rotation
     * matrix because the screen quad stays axis-aligned, so positions
     * and hit-tests downstream don't have to consult any inverse
     * transform.
     *
     * <p>UV layout (PNG convention, v=0 at the top of the source):
     * <ul>
     *   <li>screen TL  -> tex (u=0, v=1)  (old bottom-left,  empty)</li>
     *   <li>screen BL  -> tex (u=1, v=1)  (old bottom-right, empty)</li>
     *   <li>screen BR  -> tex (u=1, v=0)  (old top-right,    base)</li>
     *   <li>screen TR  -> tex (u=0, v=0)  (old top-left,     base)</li>
     * </ul>
     * The old apex pixel (PNG bottom-center, u=0.5, v=1) ends up at
     * the new screen LEFT-center (u'=0, v'=0.5), so the apex visually
     * lands on the slider's right edge when the bounding box is
     * placed at (X + W, ...).
     */
    private static void renderLeftPointingTriangle(MatrixStack matrix, String texture, float x, float y, float w, float h, int color) {
        BatchedRectangle.flushIfBatching();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, Identifier.of(texture));
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        Matrix4f mat = matrix.peek().getPositionMatrix();
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        buf.vertex(mat, x,     y,     0).texture(0, 1).color(color);
        buf.vertex(mat, x,     y + h, 0).texture(1, 1).color(color);
        buf.vertex(mat, x + w, y + h, 0).texture(1, 0).color(color);
        buf.vertex(mat, x + w, y,     0).texture(0, 0).color(color);

        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }
}
