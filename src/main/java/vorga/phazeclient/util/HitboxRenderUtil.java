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
    
    /**
     * Renders thick custom colored hitboxes that visually override vanilla ones
     */
    public static void drawBox(MatrixStack matrices, VertexConsumer vertices, Entity entity, float yaw) {
        HitboxCustomizer module = HitboxCustomizer.getInstance();
        if (!module.isEnabled()) {
            return;
        }

        // Get entity bounding box in local coordinates
        Box box = entity.getBoundingBox().offset(-entity.getX(), -entity.getY(), -entity.getZ());
        
        // Get custom color from module
        Color hitboxColor = getCustomColor();
        
        // Extract color components with full opacity for better visibility
        float red = hitboxColor.getRed() / 255.0f;
        float green = hitboxColor.getGreen() / 255.0f;
        float blue = hitboxColor.getBlue() / 255.0f;
        float alpha = 1.0f; // Full opacity to override vanilla hitboxes

        // Render multiple overlapping boxes for thickness
        renderThickBox(matrices, vertices, box, red, green, blue, alpha);
    }

    /**
     * Renders custom colored box with specified colors (for direct DebugRenderer integration)
     */
    public static void drawCustomBox(MatrixStack matrices, VertexConsumer vertices, Box box, float red, float green, float blue, float alpha) {
        HitboxCustomizer module = HitboxCustomizer.getInstance();
        if (!module.isEnabled()) {
            return;
        }

        // Get custom color from module
        Color hitboxColor = getCustomColor();
        
        // Extract color components
        float customRed = hitboxColor.getRed() / 255.0f;
        float customGreen = hitboxColor.getGreen() / 255.0f;
        float customBlue = hitboxColor.getBlue() / 255.0f;
        float customAlpha = ((hitboxColor.getAlpha() & 0xFF) / 255.0f);

        // Render with custom colors instead of vanilla colors
        renderThickBox(matrices, vertices, box, customRed, customGreen, customBlue, customAlpha);
    }

    /**
     * Gets the custom color from module settings
     */
    private static Color getCustomColor() {
        HitboxCustomizer module = HitboxCustomizer.getInstance();
        int colorInt = module.getHitboxColor();
        
        // Extract RGBA from color int
        int alpha = (colorInt >> 24) & 0xFF;
        int red = (colorInt >> 16) & 0xFF;
        int green = (colorInt >> 8) & 0xFF;
        int blue = colorInt & 0xFF;
        
        return new Color(red, green, blue, alpha);
    }

    /**
     * Renders thick hitbox by drawing multiple slightly offset boxes
     */
    private static void renderThickBox(MatrixStack matrices, VertexConsumer vertices, Box box, float red, float green, float blue, float alpha) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        
        // Render main box
        renderSingleBox(vertices, matrix, box, red, green, blue, alpha);
        
        // Render slightly expanded boxes for thickness
        double offset = 0.001; // Small offset for thickness
        Box thickBox = box.expand(offset);
        renderSingleBox(vertices, matrix, thickBox, red, green, blue, alpha * 0.8f);
        
        // Render another layer for even more visibility
        Box thickerBox = box.expand(offset * 2);
        renderSingleBox(vertices, matrix, thickerBox, red, green, blue, alpha * 0.6f);
    }

    /**
     * Renders a single box outline
     */
    private static void renderSingleBox(VertexConsumer vertices, Matrix4f matrix, Box box, float red, float green, float blue, float alpha) {
        // Draw all 12 edges of the box
        // Bottom face
        line(vertices, matrix, (float)box.minX, (float)box.minY, (float)box.minZ, (float)box.maxX, (float)box.minY, (float)box.minZ, red, green, blue, alpha);
        line(vertices, matrix, (float)box.maxX, (float)box.minY, (float)box.minZ, (float)box.maxX, (float)box.minY, (float)box.maxZ, red, green, blue, alpha);
        line(vertices, matrix, (float)box.maxX, (float)box.minY, (float)box.maxZ, (float)box.minX, (float)box.minY, (float)box.maxZ, red, green, blue, alpha);
        line(vertices, matrix, (float)box.minX, (float)box.minY, (float)box.maxZ, (float)box.minX, (float)box.minY, (float)box.minZ, red, green, blue, alpha);
        
        // Top face
        line(vertices, matrix, (float)box.minX, (float)box.maxY, (float)box.minZ, (float)box.maxX, (float)box.maxY, (float)box.minZ, red, green, blue, alpha);
        line(vertices, matrix, (float)box.maxX, (float)box.maxY, (float)box.minZ, (float)box.maxX, (float)box.maxY, (float)box.maxZ, red, green, blue, alpha);
        line(vertices, matrix, (float)box.maxX, (float)box.maxY, (float)box.maxZ, (float)box.minX, (float)box.maxY, (float)box.maxZ, red, green, blue, alpha);
        line(vertices, matrix, (float)box.minX, (float)box.maxY, (float)box.maxZ, (float)box.minX, (float)box.maxY, (float)box.minZ, red, green, blue, alpha);
        
        // Vertical edges
        line(vertices, matrix, (float)box.minX, (float)box.minY, (float)box.minZ, (float)box.minX, (float)box.maxY, (float)box.minZ, red, green, blue, alpha);
        line(vertices, matrix, (float)box.maxX, (float)box.minY, (float)box.minZ, (float)box.maxX, (float)box.maxY, (float)box.minZ, red, green, blue, alpha);
        line(vertices, matrix, (float)box.maxX, (float)box.minY, (float)box.maxZ, (float)box.maxX, (float)box.maxY, (float)box.maxZ, red, green, blue, alpha);
        line(vertices, matrix, (float)box.minX, (float)box.minY, (float)box.maxZ, (float)box.minX, (float)box.maxY, (float)box.maxZ, red, green, blue, alpha);
    }

    /**
     * Draws a single line between two points
     */
    private static void line(VertexConsumer vertexConsumer, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float red, float green, float blue, float alpha) {
        vertexConsumer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha).normal(0.0f, 1.0f, 0.0f);
        vertexConsumer.vertex(matrix, x2, y2, z2).color(red, green, blue, alpha).normal(0.0f, 1.0f, 0.0f);
    }
}