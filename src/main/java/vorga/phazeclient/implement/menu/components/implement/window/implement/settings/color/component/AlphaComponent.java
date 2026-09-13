package vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color.component;

import vorga.phazeclient.base.util.render.GuiMatrix;

import org.joml.Matrix3x2fStack;
import org.joml.Matrix3x2fc;

import lombok.RequiredArgsConstructor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.menu.components.AbstractComponent;

import static net.minecraft.util.math.MathHelper.clamp;

@RequiredArgsConstructor
public class AlphaComponent extends AbstractComponent {

    private static final float INDICATOR_SIZE = 6.0F;

    private final ColorSetting setting;
    private boolean alphaDragging;

    private float X, Y, W, H;

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Matrix3x2fStack matrix = context.getMatrices();

        X = x + 162;
        Y = y + 10.5F;
        W = 6;
        H = 50;
        boolean hoveredStrip = MathUtil.isHovered(mouseX, mouseY, X, Y, W, H);
        if (alphaDragging || hoveredStrip) {
            vorga.phazeclient.api.system.cursor.CursorManager.requestVerticalResize();
        }

        renderVerticalGradientStrip(matrix, "textures/color_picker/alpha.png", X, Y, W, H, applyGlobalAlpha(0xFFFFFFFF));

        int gradColorWithAlpha = applyGlobalAlpha(setting.getColorWithAlpha());
        int transparent = applyGlobalAlpha(0x80000000);
        rectangle.render(ShapeProperties.create(matrix, X, Y - 0.2, W, H + 0.5)
                .round(1.5F)
                .color(transparent, gradColorWithAlpha, transparent, gradColorWithAlpha)
                .build());

        float apexY = clamp(Y + H * (1.0F - setting.getAlpha()), Y, Y + H);
        float triX = X + W;
        float triY = apexY - INDICATOR_SIZE / 2.0F;
        renderLeftPointingTriangle(matrix, "textures/color_picker/triangle.png", triX, triY, INDICATOR_SIZE, INDICATOR_SIZE, applyGlobalAlpha(0xFFFFFFFF));

        if (alphaDragging) {

            setting.setAlpha(clamp(1.0F - (float) (mouseY - Y) / H, 0, 1));
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

    private static void renderVerticalGradientStrip(Matrix3x2fc matrix, String texture, float x, float y, float w, float h, int color) {
        BatchedRectangle.flushIfBatching();

        net.minecraft.util.Identifier phaze$tex = Identifier.of(texture);

        Matrix4f mat = GuiMatrix.mat4(matrix);
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        buf.vertex(mat, x,     y,     0).texture(0, 0).color(color);
        buf.vertex(mat, x,     y + h, 0).texture(1, 0).color(color);
        buf.vertex(mat, x + w, y + h, 0).texture(1, 1).color(color);
        buf.vertex(mat, x + w, y,     0).texture(0, 1).color(color);

        vorga.phazeclient.api.system.draw.GuiProjection.begin();
        try {
            vorga.phazeclient.api.system.draw.PhazeDrawLayers.positionTexColor(phaze$tex).draw(buf.end());
        } finally {
            vorga.phazeclient.api.system.draw.GuiProjection.end();
        }
    }

    private static void renderLeftPointingTriangle(Matrix3x2fc matrix, String texture, float x, float y, float w, float h, int color) {
        BatchedRectangle.flushIfBatching();

        net.minecraft.util.Identifier phaze$tex = Identifier.of(texture);

        Matrix4f mat = GuiMatrix.mat4(matrix);
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        buf.vertex(mat, x,     y,     0).texture(0, 1).color(color);
        buf.vertex(mat, x,     y + h, 0).texture(1, 1).color(color);
        buf.vertex(mat, x + w, y + h, 0).texture(1, 0).color(color);
        buf.vertex(mat, x + w, y,     0).texture(0, 0).color(color);

        vorga.phazeclient.api.system.draw.GuiProjection.begin();
        try {
            vorga.phazeclient.api.system.draw.PhazeDrawLayers.positionTexColor(phaze$tex).draw(buf.end());
        } finally {
            vorga.phazeclient.api.system.draw.GuiProjection.end();
        }
    }
}
