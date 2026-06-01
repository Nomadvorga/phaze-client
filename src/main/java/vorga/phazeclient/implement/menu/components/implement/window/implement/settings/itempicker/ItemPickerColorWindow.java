package vorga.phazeclient.implement.menu.components.implement.window.implement.settings.itempicker;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
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
import vorga.phazeclient.api.feature.module.setting.implement.ItemPickerSetting;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.implement.window.AbstractWindow;

import java.awt.*;

import static net.minecraft.util.math.MathHelper.clamp;

@Getter
public final class ItemPickerColorWindow extends AbstractWindow {
    private static final Identifier HUE_TEXTURE = Identifier.of("textures/color_picker/hue.png");

    public static final float WINDOW_WIDTH = 126.0F;
    public static final float WINDOW_HEIGHT = 123.0F;

    private static final float TITLE_SIZE = 7.0F;
    private static final float HEADER_HEIGHT = 24.0F;
    private static final float PICKER_X = 13.0F;
    private static final float PICKER_Y = 28.0F;
    private static final float PICKER_WIDTH = 100.0F;
    private static final float PICKER_HEIGHT = 66.0F;
    private static final float HUE_BAR_Y = 100.0F;
    private static final float HUE_BAR_HEIGHT = 7.0F;
    private static final float HUE_BAR_PADDING = 8.0F;
    private static final float PICKER_INDICATOR_SIZE = 9.0F;
    private static final float HUE_INDICATOR_WIDTH = 4.0F;
    private static final float HUE_INDICATOR_EXTRA_HEIGHT = 4.0F;
    private final ItemPickerSetting setting;

    private float hue;
    private float saturation;
    private float brightness;

    private boolean pickerDragging;
    private boolean hueDragging;

    public ItemPickerColorWindow(ItemPickerSetting setting) {
        this.setting = setting;
        syncFromColor(setting.getHighlightColor());
        getScaleAnimation().setMs(200);
    }

    @Override
    protected void drawWindow(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrices = context.getMatrices();
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();

        int outline = applyGlobalAlpha(opaque(MenuStyle.mix(MenuStyle.BORDER, 0xFFFFFFFF, 0.08F)));
        int panelFill = applyGlobalAlpha(opaque(MenuStyle.mix(MenuStyle.PANEL_BG, 0xFF000000, 0.18F)));
        int panelTop = panelFill;
        int pickerOutline = applyGlobalAlpha(opaque(MenuStyle.mix(MenuStyle.BORDER_LIGHT, 0xFFFFFFFF, 0.10F)));
        int hueColor = applyGlobalAlpha(0xFF000000 | (Color.HSBtoRGB(hue, 1.0F, 1.0F) & 0x00FFFFFF));
        int selectedColor = applyGlobalAlpha(0xFF000000 | (Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF));

        rectangle.render(ShapeProperties.create(matrices, x, y, width, height)
                .round(6.0F)
                .softness(1.1F)
                .thickness(1.15F)
                .outlineColor(outline)
                .color(panelFill)
                .build());

        rectangle.render(ShapeProperties.create(matrices, x, y, width, HEADER_HEIGHT)
                .round(9.0F)
                .thickness(0.0F)
                .color(panelTop)
                .build());

        String title = setting.getRowTitle();
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                title,
                TITLE_SIZE,
                applyGlobalAlpha(MenuStyle.TEXT_PRIMARY),
                positionMatrix,
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), title, TITLE_SIZE, x, width),
                MenuStyle.centerMsdfTextY(TITLE_SIZE, y, HEADER_HEIGHT),
                0.0F
        );

        float pickerX = x + PICKER_X;
        float pickerY = y + PICKER_Y;
        float hueBarX = x + HUE_BAR_PADDING;
        float hueBarY = y + HUE_BAR_Y;
        float hueBarWidth = width - HUE_BAR_PADDING * 2.0F;

        int[] pickerColors = {
                applyGlobalAlpha(0xFF000000),
                applyGlobalAlpha(0xFFFFFFFF),
                applyGlobalAlpha(0xFF000000),
                hueColor
        };

        rectangle.render(ShapeProperties.create(matrices, pickerX, pickerY, PICKER_WIDTH, PICKER_HEIGHT)
                .round(8.0F)
                .softness(1.0F)
                .thickness(1.1F)
                .outlineColor(pickerOutline)
                .color(pickerColors)
                .build());

        renderHueStrip(matrices, hueBarX, hueBarY, hueBarWidth, HUE_BAR_HEIGHT, pickerOutline);

        float pickerRadius = PICKER_INDICATOR_SIZE / 2.0F;
        float indicatorX = clamp(pickerX + PICKER_WIDTH * saturation, pickerX + pickerRadius, pickerX + PICKER_WIDTH - pickerRadius);
        float indicatorY = clamp(pickerY + PICKER_HEIGHT * (1.0F - brightness), pickerY + pickerRadius, pickerY + PICKER_HEIGHT - pickerRadius);
        rectangle.render(ShapeProperties.create(matrices, indicatorX - pickerRadius, indicatorY - pickerRadius, PICKER_INDICATOR_SIZE, PICKER_INDICATOR_SIZE)
                .round(pickerRadius)
                .softness(1.0F)
                .thickness(2.0F)
                .outlineColor(applyGlobalAlpha(0xFFFFFFFF))
                .color(selectedColor)
                .build());

        float hueHalfWidth = HUE_INDICATOR_WIDTH / 2.0F;
        float hueIndicatorX = clamp(hueBarX + hueBarWidth * hue, hueBarX + hueHalfWidth, hueBarX + hueBarWidth - hueHalfWidth);
        rectangle.render(ShapeProperties.create(matrices, hueIndicatorX - hueHalfWidth, hueBarY - HUE_INDICATOR_EXTRA_HEIGHT / 2.0F, HUE_INDICATOR_WIDTH, HUE_BAR_HEIGHT + HUE_INDICATOR_EXTRA_HEIGHT)
                .round(2.0F)
                .softness(1.0F)
                .thickness(1.4F)
                .outlineColor(applyGlobalAlpha(0xFFFFFFFF))
                .color(applyGlobalAlpha(0xFFCFE7FF))
                .build());

        if (pickerDragging) {
            updatePicker(mouseX, mouseY);
        }
        if (hueDragging) {
            updateHue(mouseX);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        draggable(MathUtil.isHovered(mouseX, mouseY, x, y, width, HEADER_HEIGHT));
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0) {
            if (MathUtil.isHovered(mouseX, mouseY, x + PICKER_X, y + PICKER_Y, PICKER_WIDTH, PICKER_HEIGHT)) {
                pickerDragging = true;
                updatePicker(mouseX, mouseY);
                return true;
            }

            float hueBarX = x + HUE_BAR_PADDING;
            if (MathUtil.isHovered(mouseX, mouseY, hueBarX, y + HUE_BAR_Y, width - HUE_BAR_PADDING * 2.0F, HUE_BAR_HEIGHT)) {
                hueDragging = true;
                updateHue(mouseX);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (pickerDragging) {
            updatePicker(mouseX, mouseY);
            return true;
        }
        if (hueDragging) {
            updateHue(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        pickerDragging = false;
        hueDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updatePicker(double mouseX, double mouseY) {
        saturation = clamp((float) ((mouseX - (x + PICKER_X)) / PICKER_WIDTH), 0.0F, 1.0F);
        brightness = clamp(1.0F - (float) ((mouseY - (y + PICKER_Y)) / PICKER_HEIGHT), 0.0F, 1.0F);
        commitColor();
    }

    private void updateHue(double mouseX) {
        float hueBarX = x + HUE_BAR_PADDING;
        float hueBarWidth = width - HUE_BAR_PADDING * 2.0F;
        hue = clamp((float) ((mouseX - hueBarX) / hueBarWidth), 0.0F, 1.0F);
        commitColor();
    }

    private void commitColor() {
        setting.setHighlightColor(0xFF000000 | (Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF));
    }

    private void syncFromColor(int color) {
        float[] hsb = Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
    }

    private void renderHueStrip(MatrixStack matrices, float x, float y, float width, float height, int outlineColor) {
        float radius = height / 2.0F;
        rectangle.render(ShapeProperties.create(matrices, x, y, width, height)
                .round(radius)
                .softness(1.0F)
                .thickness(1.0F)
                .outlineColor(outlineColor)
                .color(applyGlobalAlpha(opaque(MenuStyle.mix(MenuStyle.PANEL_BG, 0xFF000000, 0.24F))))
                .build());

        float innerInset = 0.6F;
        float innerX = x + innerInset;
        float innerY = y + innerInset;
        float innerWidth = Math.max(1.0F, width - innerInset * 2.0F);
        float innerHeight = Math.max(1.0F, height - innerInset * 2.0F);
        float capSize = innerHeight;
        float gradientX = innerX + capSize / 2.0F;
        float gradientWidth = Math.max(1.0F, innerWidth - capSize);

        renderHorizontalHueTexture(matrices, gradientX, innerY, gradientWidth, innerHeight, applyGlobalAlpha(0xFFFFFFFF));

        int edgeColor = applyGlobalAlpha(0xFFFF0000);
        rectangle.render(ShapeProperties.create(matrices, innerX, innerY, capSize, innerHeight)
                .round(innerHeight / 2.0F)
                .thickness(0.0F)
                .color(edgeColor)
                .build());
        rectangle.render(ShapeProperties.create(matrices, innerX + innerWidth - capSize, innerY, capSize, innerHeight)
                .round(innerHeight / 2.0F)
                .thickness(0.0F)
                .color(edgeColor)
                .build());
    }

    private static void renderHorizontalHueTexture(MatrixStack matrices, float x, float y, float width, float height, int color) {
        BatchedRectangle.flushIfBatching();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, HUE_TEXTURE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, x, y, 0.0F).texture(0.0F, 0.0F).color(color);
        buffer.vertex(matrix, x, y + height, 0.0F).texture(0.0F, 1.0F).color(color);
        buffer.vertex(matrix, x + width, y + height, 0.0F).texture(1.0F, 1.0F).color(color);
        buffer.vertex(matrix, x + width, y, 0.0F).texture(1.0F, 0.0F).color(color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }

    private static int opaque(int color) {
        return 0xFF000000 | (color & 0x00FFFFFF);
    }
}
