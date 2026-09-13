/**
 * Based on HitboxPlus by PingIsFun (https://github.com/PingIsFun/hitboxplus)
 * Licensed under MIT License
 * Original Copyright (c) 2022 PingIsFun
 * Modified for Phaze Client - renders thick custom hitboxes that override vanilla ones
 */
package vorga.phazeclient.util;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import org.joml.Matrix4f;
import vorga.phazeclient.implement.features.modules.other.HitboxCustomizer;

import java.awt.Color;

public class HitboxRenderUtil {

    public static void drawBox(MatrixStack matrices, VertexConsumer vertices, Entity entity, float yaw) {
        HitboxCustomizer module = HitboxCustomizer.getInstance();
        if (!module.isEnabled()) {
            return;
        }

        Box box = entity.getBoundingBox().offset(-entity.getX(), -entity.getY(), -entity.getZ());

        Color hitboxColor = getCustomColor();

        float red = hitboxColor.getRed() / 255.0f;
        float green = hitboxColor.getGreen() / 255.0f;
        float blue = hitboxColor.getBlue() / 255.0f;
        float alpha = 1.0f;

        renderThickBox(matrices, vertices, box, red, green, blue, alpha);
    }

    public static void drawCustomBox(MatrixStack matrices, VertexConsumer vertices, Box box, float red, float green, float blue, float alpha) {
        HitboxCustomizer module = HitboxCustomizer.getInstance();
        if (!module.isEnabled()) {
            return;
        }

        Color hitboxColor = getCustomColor();

        float customRed = hitboxColor.getRed() / 255.0f;
        float customGreen = hitboxColor.getGreen() / 255.0f;
        float customBlue = hitboxColor.getBlue() / 255.0f;
        float customAlpha = ((hitboxColor.getAlpha() & 0xFF) / 255.0f);

        renderThickBox(matrices, vertices, box, customRed, customGreen, customBlue, customAlpha);
    }

    private static Color getCustomColor() {
        HitboxCustomizer module = HitboxCustomizer.getInstance();
        int colorInt = module.getHitboxColor();

        int alpha = (colorInt >> 24) & 0xFF;
        int red = (colorInt >> 16) & 0xFF;
        int green = (colorInt >> 8) & 0xFF;
        int blue = colorInt & 0xFF;

        return new Color(red, green, blue, alpha);
    }

    private static void renderThickBox(MatrixStack matrices, VertexConsumer vertices, Box box, float red, float green, float blue, float alpha) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        renderSingleBox(vertices, matrix, box, red, green, blue, alpha);

        double offset = 0.001;
        Box thickBox = box.expand(offset);
        renderSingleBox(vertices, matrix, thickBox, red, green, blue, alpha * 0.8f);

        Box thickerBox = box.expand(offset * 2);
        renderSingleBox(vertices, matrix, thickerBox, red, green, blue, alpha * 0.6f);
    }

    private static void renderSingleBox(VertexConsumer vertices, Matrix4f matrix, Box box, float red, float green, float blue, float alpha) {

        line(vertices, matrix, (float)box.minX, (float)box.minY, (float)box.minZ, (float)box.maxX, (float)box.minY, (float)box.minZ, red, green, blue, alpha);
        line(vertices, matrix, (float)box.maxX, (float)box.minY, (float)box.minZ, (float)box.maxX, (float)box.minY, (float)box.maxZ, red, green, blue, alpha);
        line(vertices, matrix, (float)box.maxX, (float)box.minY, (float)box.maxZ, (float)box.minX, (float)box.minY, (float)box.maxZ, red, green, blue, alpha);
        line(vertices, matrix, (float)box.minX, (float)box.minY, (float)box.maxZ, (float)box.minX, (float)box.minY, (float)box.minZ, red, green, blue, alpha);

        line(vertices, matrix, (float)box.minX, (float)box.maxY, (float)box.minZ, (float)box.maxX, (float)box.maxY, (float)box.minZ, red, green, blue, alpha);
        line(vertices, matrix, (float)box.maxX, (float)box.maxY, (float)box.minZ, (float)box.maxX, (float)box.maxY, (float)box.maxZ, red, green, blue, alpha);
        line(vertices, matrix, (float)box.maxX, (float)box.maxY, (float)box.maxZ, (float)box.minX, (float)box.maxY, (float)box.maxZ, red, green, blue, alpha);
        line(vertices, matrix, (float)box.minX, (float)box.maxY, (float)box.maxZ, (float)box.minX, (float)box.maxY, (float)box.minZ, red, green, blue, alpha);

        line(vertices, matrix, (float)box.minX, (float)box.minY, (float)box.minZ, (float)box.minX, (float)box.maxY, (float)box.minZ, red, green, blue, alpha);
        line(vertices, matrix, (float)box.maxX, (float)box.minY, (float)box.minZ, (float)box.maxX, (float)box.maxY, (float)box.minZ, red, green, blue, alpha);
        line(vertices, matrix, (float)box.maxX, (float)box.minY, (float)box.maxZ, (float)box.maxX, (float)box.maxY, (float)box.maxZ, red, green, blue, alpha);
        line(vertices, matrix, (float)box.minX, (float)box.minY, (float)box.maxZ, (float)box.minX, (float)box.maxY, (float)box.maxZ, red, green, blue, alpha);
    }

    private static void line(VertexConsumer vertexConsumer, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float red, float green, float blue, float alpha) {
        vertexConsumer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha).normal(0.0f, 1.0f, 0.0f);
        vertexConsumer.vertex(matrix, x2, y2, z2).color(red, green, blue, alpha).normal(0.0f, 1.0f, 0.0f);
    }
}
