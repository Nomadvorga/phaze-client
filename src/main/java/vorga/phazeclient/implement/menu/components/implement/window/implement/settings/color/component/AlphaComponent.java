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
public class AlphaComponent extends AbstractComponent {
    // Same indicator sizing constant as SaturationComponent so the
    // two sliders remain visually paired; tweaking one without the
    // other would break the symmetry MultiColorComponent's window
    // layout was designed around.
    private static final float INDICATOR_SIZE = 6.0F;

    private final ColorSetting setting;
    private boolean alphaDragging;

    private float X, Y, W, H;


    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrix = context.getMatrices();

        // Vertical alpha strip placed after the hue strip and its
        // triangle indicator. Layout from the picker's right edge:
        //   x+148..x+154   hue strip          (6 px)
        //   x+154..x+160   hue triangle       (6 px)
        //   x+162..x+168   alpha strip        (6 px, 2 px gap)
        //   x+168..x+174   alpha triangle     (6 px)
        // Total picker block ends at x+174; MultiColorComponent sizes
        // the window to 178 so the alpha triangle has a 4 px gap to
        // the rounded window border.
        X = x + 162;
        Y = y + 10.5F;
        W = 6;
        H = 50;

        // Underlying checker pattern that visualizes transparency.
        // Same UV-rotation trick as SaturationComponent: stretch the
        // texture's u axis along the slider's vertical axis so the
        // checker repeats top-to-bottom rather than left-to-right.
        renderVerticalGradientStrip(matrix, "textures/color_picker/alpha.png", X, Y, W, H, applyGlobalAlpha(0xFFFFFFFF));

        // Solid-color overlay that fades from the picker's current
        // color (top) to fully transparent (bottom). The four corner
        // colors map (in submit's order) to:
        //   c1 = visual BL = transparent
        //   c2 = visual TL = current color
        //   c3 = visual BR = transparent
        //   c4 = visual TR = current color
        // i.e. left and right columns share the same color so the
        // gradient runs purely along Y, and inverting top vs bottom
        // (compared to the legacy horizontal slider's left vs right)
        // moves the alpha=1 region to the top of the strip - which
        // matches the indicator math below where alpha increases as
        // the user drags upward.
        int gradColorWithAlpha = applyGlobalAlpha(setting.getColorWithAlpha());
        int transparent = applyGlobalAlpha(0x80000000);
        rectangle.render(ShapeProperties.create(matrix, X, Y - 0.2, W, H + 0.5)
                .round(1.5F)
                .color(transparent, gradColorWithAlpha, transparent, gradColorWithAlpha)
                .build());

        // Left-pointing triangle indicator anchored at the slider's
        // right edge; identical visual language to SaturationComponent
        // so the two strips read as a paired control. The apex Y
        // tracks setting.getAlpha() in [0, 1] from top to bottom.
        float apexY = clamp(Y + H * setting.getAlpha(), Y, Y + H);
        float triX = X + W;
        float triY = apexY - INDICATOR_SIZE / 2.0F;
        renderLeftPointingTriangle(matrix, "textures/color_picker/triangle.png", triX, triY, INDICATOR_SIZE, INDICATOR_SIZE, applyGlobalAlpha(0xFFFFFFFF));

        if (alphaDragging) {
            setting.setAlpha(clamp((float) (mouseY - Y) / H, 0, 1));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        alphaDragging = button == 0 && MathUtil.isHovered(mouseX, mouseY, X, Y, W, H);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        alphaDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Same UV-rotation trick {@link SaturationComponent} uses, copied
     * here so AlphaComponent doesn't have to import a sibling package
     * member - keeps the pair of slider components symmetric and lets
     * each control its own POSITION_TEX_COLOR draw without crossing
     * dependencies.
     */
    private static void renderVerticalGradientStrip(MatrixStack matrix, String texture, float x, float y, float w, float h, int color) {
        BatchedRectangle.flushIfBatching();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, Identifier.of(texture));
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        Matrix4f mat = matrix.peek().getPositionMatrix();
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        buf.vertex(mat, x,     y,     0).texture(0, 0).color(color);
        buf.vertex(mat, x,     y + h, 0).texture(1, 0).color(color);
        buf.vertex(mat, x + w, y + h, 0).texture(1, 1).color(color);
        buf.vertex(mat, x + w, y,     0).texture(0, 1).color(color);

        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }

    /**
     * Renders the source triangle texture rotated 90 deg CW via UV
     * remap, mirroring {@link SaturationComponent}'s indicator. See
     * that class for the full UV-mapping breakdown; duplicated here
     * so AlphaComponent stays self-contained against future package
     * reorganization.
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
