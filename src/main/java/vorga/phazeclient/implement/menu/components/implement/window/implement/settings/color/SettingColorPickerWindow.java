package vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color;

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
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.api.system.shape.implement.Blur;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.features.modules.client.Theme;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.implement.window.AbstractWindow;

import java.awt.*;

import static net.minecraft.util.math.MathHelper.clamp;

@Getter
public final class SettingColorPickerWindow extends AbstractWindow {
    private static final Identifier HUE_TEXTURE = Identifier.of("textures/color_picker/hue.png");

    public static final float WINDOW_WIDTH = 126.0F;
    public static final float WINDOW_HEIGHT = 123.0F;
    public static final float WINDOW_HEIGHT_WITH_ALPHA = 138.0F;

    private static final float TITLE_SIZE = 7.0F;
    private static final float HEADER_HEIGHT = 24.0F;
    private static final float PICKER_X = 13.0F;
    private static final float PICKER_Y = 28.0F;
    private static final float PICKER_WIDTH = 100.0F;
    private static final float PICKER_HEIGHT = 66.0F;
    private static final float HUE_BAR_Y = 100.0F;
    private static final float HUE_BAR_HEIGHT = 7.0F;
    private static final float ALPHA_BAR_Y = 112.0F;
    private static final float ALPHA_BAR_HEIGHT = 7.0F;
    private static final float HUE_BAR_PADDING = 8.0F;
    private static final float PICKER_INDICATOR_SIZE = 9.0F;
    private static final float HUE_INDICATOR_WIDTH = 4.0F;
    private static final float HUE_INDICATOR_EXTRA_HEIGHT = 4.0F;

    private final ColorSetting setting;

    private float hue;
    private float saturation;
    private float brightness;
    private float alpha;

    private boolean pickerDragging;
    private boolean hueDragging;
    private boolean alphaDragging;

    public SettingColorPickerWindow(ColorSetting setting) {
        this.setting = setting;
        syncFromColor(setting.getColor());
        getScaleAnimation().setMs(200);
    }

    public static float getWindowHeight(ColorSetting setting) {
        return setting != null && !setting.isNoAlpha() ? WINDOW_HEIGHT_WITH_ALPHA : WINDOW_HEIGHT;
    }

    @Override
    protected void drawWindow(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrices = context.getMatrices();
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();

        renderWindowBlur(matrices);

        int outline = MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.BORDER_LIGHT, 0xFFFFFFFF, 0.06F), globalAlpha * 0.96F);
        int panelFill = MenuStyle.withAlpha(MenuStyle.PANEL_BG, globalAlpha * 0.94F);
        int panelInner = MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.PANEL_BG_SOFT, MenuStyle.PANEL_CONTENT, 0.42F), globalAlpha * 0.98F);
        int pickerOutline = MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.BORDER_LIGHT, 0xFFFFFFFF, 0.10F), globalAlpha * 0.96F);
        int hueColor = MenuStyle.withAlpha(0xFF000000 | (Color.HSBtoRGB(hue, 1.0F, 1.0F) & 0x00FFFFFF), globalAlpha);
        int selectedOpaqueColor = MenuStyle.withAlpha(0xFF000000 | (Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF), globalAlpha);
        int selectedAlphaColor = MenuStyle.withAlpha((Math.round(alpha * 255.0F) << 24) | (Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF), globalAlpha);

        rectangle.render(ShapeProperties.create(matrices, x, y, width, height)
                .round(9.0F)
                .softness(1.1F)
                .thickness(1.15F)
                .outlineColor(outline)
                .color(panelFill)
                .build());

        rectangle.render(ShapeProperties.create(matrices, x + 1.0F, y + 1.0F, width - 2.0F, height - 2.0F)
                .round(8.0F)
                .thickness(0.0F)
                .color(panelInner)
                .build());

        String title = setting.getName();
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                title,
                TITLE_SIZE,
                MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, globalAlpha),
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
                MenuStyle.withAlpha(0xFF000000, globalAlpha),
                MenuStyle.withAlpha(0xFFFFFFFF, globalAlpha),
                MenuStyle.withAlpha(0xFF000000, globalAlpha),
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
                .outlineColor(MenuStyle.withAlpha(0xFFFFFFFF, globalAlpha))
                .color(selectedOpaqueColor)
                .build());

        float hueHalfWidth = HUE_INDICATOR_WIDTH / 2.0F;
        float hueIndicatorX = clamp(hueBarX + hueBarWidth * hue, hueBarX + hueHalfWidth, hueBarX + hueBarWidth - hueHalfWidth);
        rectangle.render(ShapeProperties.create(matrices, hueIndicatorX - hueHalfWidth, hueBarY - HUE_INDICATOR_EXTRA_HEIGHT / 2.0F, HUE_INDICATOR_WIDTH, HUE_BAR_HEIGHT + HUE_INDICATOR_EXTRA_HEIGHT)
                .round(2.2F)
                .softness(1.0F)
                .thickness(1.4F)
                .outlineColor(MenuStyle.withAlpha(0xFFFFFFFF, globalAlpha))
                .color(MenuStyle.withAlpha(0xFFCFE7FF, globalAlpha))
                .build());

        if (!setting.isNoAlpha()) {
            float alphaBarX = x + HUE_BAR_PADDING;
            float alphaBarY = y + ALPHA_BAR_Y;
            float alphaBarWidth = width - HUE_BAR_PADDING * 2.0F;
            renderAlphaStrip(matrices, alphaBarX, alphaBarY, alphaBarWidth, ALPHA_BAR_HEIGHT, pickerOutline, selectedOpaqueColor);

            float alphaHalfWidth = HUE_INDICATOR_WIDTH / 2.0F;
            float alphaIndicatorX = clamp(alphaBarX + alphaBarWidth * alpha, alphaBarX + alphaHalfWidth, alphaBarX + alphaBarWidth - alphaHalfWidth);
            rectangle.render(ShapeProperties.create(matrices, alphaIndicatorX - alphaHalfWidth, alphaBarY - HUE_INDICATOR_EXTRA_HEIGHT / 2.0F, HUE_INDICATOR_WIDTH, ALPHA_BAR_HEIGHT + HUE_INDICATOR_EXTRA_HEIGHT)
                    .round(2.2F)
                    .softness(1.0F)
                    .thickness(1.4F)
                    .outlineColor(MenuStyle.withAlpha(0xFFFFFFFF, globalAlpha))
                    .color(selectedAlphaColor)
                    .build());
        }

        if (pickerDragging) {
            updatePicker(mouseX, mouseY);
        }
        if (hueDragging) {
            updateHue(mouseX);
        }
        if (alphaDragging) {
            updateAlpha(mouseX);
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

            if (!setting.isNoAlpha() && MathUtil.isHovered(mouseX, mouseY, hueBarX, y + ALPHA_BAR_Y, width - HUE_BAR_PADDING * 2.0F, ALPHA_BAR_HEIGHT)) {
                alphaDragging = true;
                updateAlpha(mouseX);
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
        if (alphaDragging) {
            updateAlpha(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        pickerDragging = false;
        hueDragging = false;
        alphaDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void renderWindowBlur(MatrixStack matrices) {
        float blurRadius = Theme.getInstance().getMenuBlurRadius();
        if (blurRadius <= 0.0F) {
            return;
        }

        Blur.INSTANCE.renderGaussian(ShapeProperties.create(matrices, x, y, width, height)
                .round(9.0F)
                .softness(1.1F)
                .quality(blurRadius * 2.0F)
                .color(MenuStyle.withAlpha(0xFFFFFFFF, globalAlpha))
                .build());
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

    private void updateAlpha(double mouseX) {
        float alphaBarX = x + HUE_BAR_PADDING;
        float alphaBarWidth = width - HUE_BAR_PADDING * 2.0F;
        alpha = clamp((float) ((mouseX - alphaBarX) / alphaBarWidth), 0.0F, 1.0F);
        commitColor();
    }

    private void commitColor() {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF;
        int packedAlpha = setting.isNoAlpha() ? 0xFF000000 : (Math.round(alpha * 255.0F) << 24);
        setting.setColor(packedAlpha | rgb);
    }

    private void syncFromColor(int color) {
        float[] hsb = Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        alpha = ((color >> 24) & 0xFF) / 255.0F;
    }

    private void renderHueStrip(MatrixStack matrices, float x, float y, float width, float height, int outlineColor) {
        float radius = 2.35F;
        rectangle.render(ShapeProperties.create(matrices, x, y, width, height)
                .round(radius)
                .softness(1.0F)
                .thickness(1.0F)
                .outlineColor(outlineColor)
                .color(MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.PANEL_BG, 0xFF000000, 0.24F), globalAlpha * 0.9F))
                .build());

        float innerInset = 0.6F;
        float innerX = x + innerInset;
        float innerY = y + innerInset;
        float innerWidth = Math.max(1.0F, width - innerInset * 2.0F);
        float innerHeight = Math.max(1.0F, height - innerInset * 2.0F);
        float capSize = innerHeight;
        float gradientX = innerX + capSize / 2.0F;
        float gradientWidth = Math.max(1.0F, innerWidth - capSize);

        renderHorizontalHueTexture(matrices, gradientX, innerY, gradientWidth, innerHeight, MenuStyle.withAlpha(0xFFFFFFFF, globalAlpha));

        int edgeColor = MenuStyle.withAlpha(0xFFFF0000, globalAlpha);
        rectangle.render(ShapeProperties.create(matrices, innerX, innerY, capSize, innerHeight)
                .round(2.1F)
                .thickness(0.0F)
                .color(edgeColor)
                .build());
        rectangle.render(ShapeProperties.create(matrices, innerX + innerWidth - capSize, innerY, capSize, innerHeight)
                .round(2.1F)
                .thickness(0.0F)
                .color(edgeColor)
                .build());
    }

    private void renderAlphaStrip(MatrixStack matrices, float x, float y, float width, float height, int outlineColor, int opaqueColor) {
        float radius = 2.35F;
        int whiteBase = MenuStyle.withAlpha(0xFFFFFFFF, globalAlpha);
        rectangle.render(ShapeProperties.create(matrices, x, y, width, height)
                .round(radius)
                .softness(1.0F)
                .thickness(1.0F)
                .outlineColor(outlineColor)
                .color(whiteBase)
                .build());

        float innerInset = 0.6F;
        float innerX = x + innerInset;
        float innerY = y + innerInset;
        float innerWidth = Math.max(1.0F, width - innerInset * 2.0F);
        float innerHeight = Math.max(1.0F, height - innerInset * 2.0F);
        int transparentColor = opaqueColor & 0x00FFFFFF;
        rectangle.render(ShapeProperties.create(matrices, innerX, innerY, innerWidth, innerHeight)
                .round(1.9F)
                .thickness(0.0F)
                .color(whiteBase)
                .build());
        rectangle.render(ShapeProperties.create(matrices, innerX, innerY, innerWidth, innerHeight)
                .round(1.9F)
                .thickness(0.0F)
                .color(transparentColor, transparentColor, opaqueColor, opaqueColor)
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
}
