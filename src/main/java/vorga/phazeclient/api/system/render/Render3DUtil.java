package vorga.phazeclient.api.system.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Minimal 3D draw helpers used by the FT Helper renderer, the
 * Predictions module, and the Snowball Tracker. Each helper drains
 * the {@code RenderSystem} state it needs, submits one
 * {@link BufferBuilder}, and restores neutral state on exit.
 *
 * <h3>Camera-relative coordinates</h3>
 * Every call expects coordinates already translated by the camera
 * position. The world-render mixin pushes that translate before
 * invoking these helpers, so callers pass plain world-space values
 * and the matrix stack handles the camera offset implicitly. The
 * one exception is {@link #drawLineWorld} which subtracts the
 * camera position itself for ad-hoc callers that don't sit inside
 * a pushed camera-relative frame.
 *
 * <h3>Shader / state contract</h3>
 * The line / box variants use {@code RENDERTYPE_LINES} and
 * {@code POSITION_COLOR} respectively; both are vanilla
 * {@code ShaderProgramKeys}. We deliberately don't cache a
 * {@code BufferBuilder} between calls because batching would force
 * us to flush at every state change in a way that conflicts with
 * the specialised batched-rect pipeline used by the GUI. Per-shape
 * submission is still cheap because the BufferBuilder pool is
 * thread-local in vanilla.
 *
 * <h3>Adapted from</h3>
 * {@code winvi.moscow.soupbetter.util.Render3DUtil} (FunTime
 * client). Phaze drops the upstream's Theme-coupled colour
 * accessors in favour of explicit ARGB ints so each module can
 * drive its own palette.
 */
public final class Render3DUtil {
    private Render3DUtil() {
    }

    /**
     * Draw only the six filled faces of an axis-aligned box,
     * without any outline geometry. Useful when the caller wants
     * the fill to be drawn alongside another (vanilla or otherwise)
     * outline pass that handles the edge rendering separately.
     * {@code fillAlphaScale} multiplies the supplied color's alpha
     * so a 1.0 scale renders at full alpha and 0.5 at half alpha,
     * matching the convention {@link #drawBox} uses for its filled
     * faces.
     */
    public static void drawBoxFill(MatrixStack matrices,
                                   float x1, float y1, float z1,
                                   float x2, float y2, float z2,
                                   int color,
                                   float fillAlphaScale) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float fillA = a * Math.max(0.0F, Math.min(1.0F, fillAlphaScale));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        // Polygon offset pushes the fill slightly closer to the camera
        // in the depth buffer, eliminating z-fighting flicker against
        // world geometry that shares the same plane. Belt-and-braces
        // pair: disable on exit so subsequent draws (predictions
        // lines, vanilla outline, etc.) aren't biased.
        RenderSystem.polygonOffset(-1.0F, -1.0F);
        RenderSystem.enablePolygonOffset();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        // bottom
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, fillA);
        // top
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, fillA);
        // north
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, fillA);
        // south
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, fillA);
        // west
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, fillA);
        // east
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, fillA);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.disablePolygonOffset();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * Draw an axis-aligned filled box plus its outline in
     * camera-relative coordinates. The fill alpha is reduced to
     * {@code fillAlphaScale} of the supplied colour's alpha so
     * the outline reads on top.
     */
    public static void drawBox(MatrixStack matrices,
                               float x1, float y1, float z1,
                               float x2, float y2, float z2,
                               int color,
                               float fillAlphaScale,
                               float lineWidth) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float fillA = a * Math.max(0.0F, Math.min(1.0F, fillAlphaScale));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        // Filled faces.
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        // bottom
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, fillA);
        // top
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, fillA);
        // north
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, fillA);
        // south
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, fillA);
        // west
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, fillA);
        // east
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, fillA);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, fillA);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        // Outline lines.
        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
        RenderSystem.lineWidth(Math.max(1.0F, lineWidth));
        buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        // bottom edges
        line(matrix, buffer, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(matrix, buffer, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(matrix, buffer, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(matrix, buffer, x1, y1, z2, x1, y1, z1, r, g, b, a);
        // top edges
        line(matrix, buffer, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(matrix, buffer, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(matrix, buffer, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(matrix, buffer, x1, y2, z2, x1, y2, z1, r, g, b, a);
        // verticals
        line(matrix, buffer, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(matrix, buffer, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(matrix, buffer, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(matrix, buffer, x1, y1, z2, x1, y2, z2, r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * Filled UV sphere centered at {@code (cx, cy, cz)} (camera-relative).
     * Built as a stack of latitude rings stitched into quads. Each ring
     * has {@code segments} vertical slices; we use {@code stacks} latitude
     * bands so a typical {@code stacks=12, segments=18} sphere comes out
     * to ~432 quads which is cheap for a single per-frame draw.
     *
     * <p>The sphere uses {@code POSITION_COLOR} with depth-test on
     * but depth-mask off, so it composites against terrain without
     * z-fighting against itself or imprinting into the depth buffer
     * (which would mess with later transparent draws).
     */
    public static void drawSphereSolid(MatrixStack matrices,
                                       float cx, float cy, float cz,
                                       float radius,
                                       int color,
                                       int stacks, int segments) {
        drawSphereSolid(matrices, cx, cy, cz, radius, color, stacks, segments, true);
    }

    /**
     * Filled UV sphere centered at {@code (cx, cy, cz)} (camera-relative)
     * with explicit depth-test control. When {@code depthTest} is
     * {@code false} the sphere paints OVER terrain and entities, used
     * by the Predictions impact-marker so the sphere isn't occluded
     * by the very entity it's marking.
     */
    public static void drawSphereSolid(MatrixStack matrices,
                                       float cx, float cy, float cz,
                                       float radius,
                                       int color,
                                       int stacks, int segments,
                                       boolean depthTest) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (depthTest) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < stacks; i++) {
            double phi1 = Math.PI * i / stacks - Math.PI / 2.0;
            double phi2 = Math.PI * (i + 1) / stacks - Math.PI / 2.0;
            float y1 = (float) (Math.sin(phi1) * radius);
            float y2 = (float) (Math.sin(phi2) * radius);
            float r1 = (float) (Math.cos(phi1) * radius);
            float r2 = (float) (Math.cos(phi2) * radius);
            for (int j = 0; j < segments; j++) {
                double t1 = 2.0 * Math.PI * j / segments;
                double t2 = 2.0 * Math.PI * (j + 1) / segments;
                float c1 = (float) Math.cos(t1);
                float s1 = (float) Math.sin(t1);
                float c2 = (float) Math.cos(t2);
                float s2 = (float) Math.sin(t2);

                float x11 = cx + r1 * c1, z11 = cz + r1 * s1;
                float x12 = cx + r1 * c2, z12 = cz + r1 * s2;
                float x21 = cx + r2 * c1, z21 = cz + r2 * s1;
                float x22 = cx + r2 * c2, z22 = cz + r2 * s2;

                buffer.vertex(matrix, x11, cy + y1, z11).color(r, g, b, a);
                buffer.vertex(matrix, x12, cy + y1, z12).color(r, g, b, a);
                buffer.vertex(matrix, x22, cy + y2, z22).color(r, g, b, a);
                buffer.vertex(matrix, x21, cy + y2, z21).color(r, g, b, a);
            }
        }
        net.minecraft.client.render.BuiltBuffer built = buffer.endNullable();
        if (built != null) {
            BufferRenderer.drawWithGlobalProgram(built);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * Camera-facing textured billboard. Used by the Predictions
     * impact marker to draw a soft bloom halo (bloom_soft.png)
     * behind the sphere. The quad is sized {@code radius * 2}
     * across, pivot at {@code (cx, cy, cz)} (camera-relative), and
     * always faces the active camera so the bloom looks the same
     * from any angle.
     *
     * <p>Uses additive blending ({@code SRC_ALPHA, ONE}) so the
     * texture's alpha modulates a brighter colour value into the
     * frame, which reads as a glow rather than a flat sticker.
     * Depth-test is off so the halo paints over the entity model
     * the marker is anchored to.
     */
    public static void drawBillboard(MatrixStack matrices, net.minecraft.util.Identifier texture,
                                     float cx, float cy, float cz,
                                     float radius, int color) {
        drawBillboard(matrices, texture, cx, cy, cz, radius, color, false);
    }

    /**
     * Camera-facing textured billboard with explicit depth-test
     * control. {@code depthTest=true} enables depth comparison so
     * the billboard is occluded by closer geometry - used by the
     * Predictions glow halo when the user wants the marker to
     * respect world geometry. The default {@code drawBillboard}
     * overload keeps depth-test off (always-on-top) for backward
     * compatibility with the FT helper feet circles.
     */
    public static void drawBillboard(MatrixStack matrices, net.minecraft.util.Identifier texture,
                                     float cx, float cy, float cz,
                                     float radius, int color, boolean depthTest) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null || client.gameRenderer.getCamera() == null) {
            return;
        }
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        // Camera basis vectors. {@code right = camera_right} and
        // {@code up = camera_up} both unit-length so the quad stays
        // square regardless of camera pitch / yaw.
        var camera = client.gameRenderer.getCamera();
        org.joml.Vector3f rightV = new org.joml.Vector3f();
        org.joml.Vector3f upV = new org.joml.Vector3f();
        org.joml.Quaternionf rotation = camera.getRotation();
        rotation.transform(1.0F, 0.0F, 0.0F, rightV);
        rotation.transform(0.0F, 1.0F, 0.0F, upV);

        float rx = rightV.x * radius, ry = rightV.y * radius, rz = rightV.z * radius;
        float ux = upV.x * radius,    uy = upV.y * radius,    uz = upV.z * radius;

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.blendFunc(
                com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE
        );
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        if (depthTest) {
            com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        } else {
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        }
        com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, texture);
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.gl.ShaderProgramKeys.POSITION_TEX_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        // Triangle-fan quad with the camera-aligned right/up basis.
        // Texture UVs map the bloom_soft png onto the full quad so
        // the soft falloff at the texture's edges produces the halo
        // gradient.
        buffer.vertex(matrix, cx - rx - ux, cy - ry - uy, cz - rz - uz).texture(0.0F, 1.0F).color(r, g, b, a);
        buffer.vertex(matrix, cx + rx - ux, cy + ry - uy, cz + rz - uz).texture(1.0F, 1.0F).color(r, g, b, a);
        buffer.vertex(matrix, cx + rx + ux, cy + ry + uy, cz + rz + uz).texture(1.0F, 0.0F).color(r, g, b, a);
        buffer.vertex(matrix, cx - rx + ux, cy - ry + uy, cz - rz + uz).texture(0.0F, 0.0F).color(r, g, b, a);

        net.minecraft.client.render.BuiltBuffer built = buffer.endNullable();
        if (built != null) {
            BufferRenderer.drawWithGlobalProgram(built);
        }

        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    private static void line(Matrix4f matrix, BufferBuilder buffer,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).normal(0, 1, 0);
    }

    /**
     * Draw a thick flat ring (annulus) lying horizontally on the XZ
     * plane at {@code centerY}. Built as a quad strip between an
     * inner and outer radius so the visible thickness is a true
     * world-space measurement rather than depending on OpenGL's
     * quirky {@code glLineWidth} support.
     *
     * <p>Used by the FT helper for the feet circles (CIRCLE_10,
     * BOZHESTVENNAYA_AURA) where {@code drawCylinderOutline}'s thin
     * line-strip turned into a 1px hairline on most GPUs because
     * vendors clamp {@code lineWidth} to 1. This routine emits real
     * geometry instead, so the user-set thickness actually shows up.
     *
     * <p>{@code thickness} is in blocks - typical values are
     * 0.10..0.40. {@code segments} controls smoothness; 64-96 is
     * plenty for radii under 15 blocks.
     */
    public static void drawThickFlatRing(MatrixStack matrices,
                                         float centerX, float centerY, float centerZ,
                                         float radius, float thickness,
                                         int color, int segments,
                                         boolean depthTest) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float inner = Math.max(0.0F, radius - thickness * 0.5F);
        float outer = radius + thickness * 0.5F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (depthTest) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < segments; i++) {
            double t1 = 2.0 * Math.PI * i / segments;
            double t2 = 2.0 * Math.PI * (i + 1) / segments;
            float c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            float c2 = (float) Math.cos(t2), s2 = (float) Math.sin(t2);

            float ix1 = centerX + c1 * inner, iz1 = centerZ + s1 * inner;
            float ix2 = centerX + c2 * inner, iz2 = centerZ + s2 * inner;
            float ox1 = centerX + c1 * outer, oz1 = centerZ + s1 * outer;
            float ox2 = centerX + c2 * outer, oz2 = centerZ + s2 * outer;

            // Quad: inner1 -> inner2 -> outer2 -> outer1 (CCW from above)
            buffer.vertex(matrix, ix1, centerY, iz1).color(r, g, b, a);
            buffer.vertex(matrix, ix2, centerY, iz2).color(r, g, b, a);
            buffer.vertex(matrix, ox2, centerY, oz2).color(r, g, b, a);
            buffer.vertex(matrix, ox1, centerY, oz1).color(r, g, b, a);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * Draw a flat filled disc on the XZ plane at {@code centerY}.
     * Used for the "flat glow" halo under FT helper circles - paints
     * a soft additive disc that radiates from the centre out, giving
     * the ring a glow effect without using a billboard sphere.
     *
     * <p>Each segment is a triangle from the centre to two ring
     * vertices, so passing 64 segments produces a smooth disc. The
     * rim alpha fades to zero by storing two colors at the rim
     * vertices vs the centre vertex - the GPU interpolates linearly,
     * so the disc smoothly fades to transparent at its border.
     */
    public static void drawFlatGlowDisc(MatrixStack matrices,
                                        float centerX, float centerY, float centerZ,
                                        float radius,
                                        int centerColor, int rimColor,
                                        int segments,
                                        boolean depthTest) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float ca = ((centerColor >>> 24) & 0xFF) / 255.0F;
        float cr = ((centerColor >>> 16) & 0xFF) / 255.0F;
        float cg = ((centerColor >>> 8) & 0xFF) / 255.0F;
        float cb = (centerColor & 0xFF) / 255.0F;
        float ra = ((rimColor >>> 24) & 0xFF) / 255.0F;
        float rr = ((rimColor >>> 16) & 0xFF) / 255.0F;
        float rg = ((rimColor >>> 8) & 0xFF) / 255.0F;
        float rb = (rimColor & 0xFF) / 255.0F;

        RenderSystem.enableBlend();
        // Additive blending so the disc reads as a soft glow on top
        // of the world rather than a flat tinted overlay.
        RenderSystem.blendFunc(
                com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE
        );
        RenderSystem.disableCull();
        if (depthTest) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < segments; i++) {
            double t1 = 2.0 * Math.PI * i / segments;
            double t2 = 2.0 * Math.PI * (i + 1) / segments;
            float x1 = centerX + (float) Math.cos(t1) * radius;
            float z1 = centerZ + (float) Math.sin(t1) * radius;
            float x2 = centerX + (float) Math.cos(t2) * radius;
            float z2 = centerZ + (float) Math.sin(t2) * radius;

            buffer.vertex(matrix, centerX, centerY, centerZ).color(cr, cg, cb, ca);
            buffer.vertex(matrix, x1, centerY, z1).color(rr, rg, rb, ra);
            buffer.vertex(matrix, x2, centerY, z2).color(rr, rg, rb, ra);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * Draw a vertical "cylinder" using a top + bottom circle of
     * line segments. {@code centerX/Z} are camera-relative,
     * {@code centerY} sits at the bottom and {@code centerY + height}
     * at the top.
     */
    public static void drawCylinderOutline(MatrixStack matrices,
                                           float centerX, float centerY, float centerZ,
                                           float radius, float height,
                                           int color, float lineWidth, int segments) {
        drawCylinderOutline(matrices, centerX, centerY, centerZ, radius, height, color, lineWidth, segments, true);
    }

    /**
     * Cylinder / flat-ring outline with explicit depth-test control.
     * {@code depthTest=false} disables OpenGL's depth comparison so
     * the ring paints through entities and terrain, used by the
     * Predictions impact-ring so it doesn't get hidden behind the
     * entity it's anchoring.
     */
    public static void drawCylinderOutline(MatrixStack matrices,
                                           float centerX, float centerY, float centerZ,
                                           float radius, float height,
                                           int color, float lineWidth, int segments,
                                           boolean depthTest) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (depthTest) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
        RenderSystem.lineWidth(Math.max(1.0F, lineWidth));

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        float topY = centerY + height;
        for (int i = 0; i < segments; i++) {
            double t1 = 2.0 * Math.PI * i / segments;
            double t2 = 2.0 * Math.PI * (i + 1) / segments;
            float x1 = centerX + (float) (Math.cos(t1) * radius);
            float z1 = centerZ + (float) (Math.sin(t1) * radius);
            float x2 = centerX + (float) (Math.cos(t2) * radius);
            float z2 = centerZ + (float) (Math.sin(t2) * radius);
            buffer.vertex(matrix, x1, centerY, z1).color(r, g, b, a).normal(0, 1, 0);
            buffer.vertex(matrix, x2, centerY, z2).color(r, g, b, a).normal(0, 1, 0);
            buffer.vertex(matrix, x1, topY, z1).color(r, g, b, a).normal(0, 1, 0);
            buffer.vertex(matrix, x2, topY, z2).color(r, g, b, a).normal(0, 1, 0);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * Draw a flat circle outline lying on the plane defined by the
     * given normal direction and centre point. Used for impact
     * markers that need to flatten against the surface they hit -
     * floor stays horizontal, walls turn vertical, ceilings flip
     * upside down.
     *
     * <p>The circle is built in a local 2D basis aligned to the
     * normal: {@code u} is any vector perpendicular to the normal,
     * {@code v = normal x u}. Each vertex is
     * {@code centre + cos(t) * u * radius + sin(t) * v * radius},
     * which keeps the ring perfectly co-planar with the surface
     * regardless of orientation.
     */
    public static void drawCircleOnFace(MatrixStack matrices,
                                        float centerX, float centerY, float centerZ,
                                        float nx, float ny, float nz,
                                        float radius,
                                        int color, float lineWidth, int segments,
                                        boolean depthTest) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        // Build an orthonormal basis (u, v) on the surface plane.
        // Pick a candidate that is least parallel to the normal so
        // the cross product won't degenerate to zero. Cross with the
        // normal once for u, once more for v - both unit length.
        float ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
        float hx, hy, hz;
        if (ax <= ay && ax <= az) { hx = 1; hy = 0; hz = 0; }
        else if (ay <= ax && ay <= az) { hx = 0; hy = 1; hz = 0; }
        else { hx = 0; hy = 0; hz = 1; }
        // u = normalise(h x n)
        float uxRaw = hy * nz - hz * ny;
        float uyRaw = hz * nx - hx * nz;
        float uzRaw = hx * ny - hy * nx;
        float uLen = (float) Math.sqrt(uxRaw * uxRaw + uyRaw * uyRaw + uzRaw * uzRaw);
        if (uLen < 1.0e-6F) return; // pathological normal, skip
        float ux = uxRaw / uLen, uy = uyRaw / uLen, uz = uzRaw / uLen;
        // v = n x u (unit because n and u are unit and orthogonal)
        float vx = ny * uz - nz * uy;
        float vy = nz * ux - nx * uz;
        float vz = nx * uy - ny * ux;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (depthTest) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
        RenderSystem.lineWidth(Math.max(1.0F, lineWidth));

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        for (int i = 0; i < segments; i++) {
            double t1 = 2.0 * Math.PI * i / segments;
            double t2 = 2.0 * Math.PI * (i + 1) / segments;
            float c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            float c2 = (float) Math.cos(t2), s2 = (float) Math.sin(t2);

            float p1x = centerX + (ux * c1 + vx * s1) * radius;
            float p1y = centerY + (uy * c1 + vy * s1) * radius;
            float p1z = centerZ + (uz * c1 + vz * s1) * radius;
            float p2x = centerX + (ux * c2 + vx * s2) * radius;
            float p2y = centerY + (uy * c2 + vy * s2) * radius;
            float p2z = centerZ + (uz * c2 + vz * s2) * radius;
            buffer.vertex(matrix, p1x, p1y, p1z).color(r, g, b, a).normal(0, 1, 0);
            buffer.vertex(matrix, p2x, p2y, p2z).color(r, g, b, a).normal(0, 1, 0);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * Thick flat ring (annulus) lying on the plane defined by the
     * given normal direction and centre. World-space thickness is
     * a true measurement (not relying on glLineWidth which clamps
     * to 1px on most drivers). Built as a quad strip between an
     * inner and outer radius using an orthonormal basis on the
     * plane - same math as {@link #drawCircleOnFace} but emitting
     * real geometry instead of line segments.
     *
     * <p>Used by the Predictions impact-circle style so the user's
     * thickness slider produces a visible difference and depth-test
     * can stay on (line primitives can't reliably depth-occlude).
     */
    public static void drawThickRingOnFace(MatrixStack matrices,
                                           float centerX, float centerY, float centerZ,
                                           float nx, float ny, float nz,
                                           float radius, float thickness,
                                           int color, int segments,
                                           boolean depthTest) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float inner = Math.max(0.0F, radius - thickness * 0.5F);
        float outer = radius + thickness * 0.5F;

        // Build orthonormal basis (u, v) on the surface plane,
        // identical to drawCircleOnFace's pick-least-parallel-axis
        // strategy so a normal that's nearly aligned with one axis
        // still gives a non-degenerate cross product.
        float ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
        float hx, hy, hz;
        if (ax <= ay && ax <= az) { hx = 1; hy = 0; hz = 0; }
        else if (ay <= ax && ay <= az) { hx = 0; hy = 1; hz = 0; }
        else { hx = 0; hy = 0; hz = 1; }
        float uxRaw = hy * nz - hz * ny;
        float uyRaw = hz * nx - hx * nz;
        float uzRaw = hx * ny - hy * nx;
        float uLen = (float) Math.sqrt(uxRaw * uxRaw + uyRaw * uyRaw + uzRaw * uzRaw);
        if (uLen < 1.0e-6F) return;
        float ux = uxRaw / uLen, uy = uyRaw / uLen, uz = uzRaw / uLen;
        float vx = ny * uz - nz * uy;
        float vy = nz * ux - nx * uz;
        float vz = nx * uy - ny * ux;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (depthTest) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < segments; i++) {
            double t1 = 2.0 * Math.PI * i / segments;
            double t2 = 2.0 * Math.PI * (i + 1) / segments;
            float c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            float c2 = (float) Math.cos(t2), s2 = (float) Math.sin(t2);

            float ix1 = centerX + (ux * c1 + vx * s1) * inner;
            float iy1 = centerY + (uy * c1 + vy * s1) * inner;
            float iz1 = centerZ + (uz * c1 + vz * s1) * inner;
            float ix2 = centerX + (ux * c2 + vx * s2) * inner;
            float iy2 = centerY + (uy * c2 + vy * s2) * inner;
            float iz2 = centerZ + (uz * c2 + vz * s2) * inner;
            float ox1 = centerX + (ux * c1 + vx * s1) * outer;
            float oy1 = centerY + (uy * c1 + vy * s1) * outer;
            float oz1 = centerZ + (uz * c1 + vz * s1) * outer;
            float ox2 = centerX + (ux * c2 + vx * s2) * outer;
            float oy2 = centerY + (uy * c2 + vy * s2) * outer;
            float oz2 = centerZ + (uz * c2 + vz * s2) * outer;

            buffer.vertex(matrix, ix1, iy1, iz1).color(r, g, b, a);
            buffer.vertex(matrix, ix2, iy2, iz2).color(r, g, b, a);
            buffer.vertex(matrix, ox2, oy2, oz2).color(r, g, b, a);
            buffer.vertex(matrix, ox1, oy1, oz1).color(r, g, b, a);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * Append a straight line segment to an externally-managed
     * BufferBuilder. Used by the Predictions trajectory pass which
     * batches every projectile arc into a single draw.
     */
    public static void vertexLine(MatrixStack matrices, VertexConsumer buffer,
                                  Vec3d start, Vec3d end,
                                  int startColor, int endColor) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a1 = ((startColor >>> 24) & 0xFF) / 255.0F;
        float r1 = ((startColor >>> 16) & 0xFF) / 255.0F;
        float g1 = ((startColor >>> 8) & 0xFF) / 255.0F;
        float b1 = (startColor & 0xFF) / 255.0F;
        float a2 = ((endColor >>> 24) & 0xFF) / 255.0F;
        float r2 = ((endColor >>> 16) & 0xFF) / 255.0F;
        float g2 = ((endColor >>> 8) & 0xFF) / 255.0F;
        float b2 = (endColor & 0xFF) / 255.0F;
        buffer.vertex(matrix, (float) start.x, (float) start.y, (float) start.z)
                .color(r1, g1, b1, a1).normal(0, 1, 0);
        buffer.vertex(matrix, (float) end.x, (float) end.y, (float) end.z)
                .color(r2, g2, b2, a2).normal(0, 1, 0);
    }

    /** Convenience: same colour at both ends. */
    public static void vertexLine(MatrixStack matrices, VertexConsumer buffer,
                                  Vec3d start, Vec3d end, int color) {
        vertexLine(matrices, buffer, start, end, color, color);
    }

    /**
     * One-shot line draw in absolute world coordinates (handles the
     * camera subtraction internally). Used by ad-hoc callers that
     * don't sit inside a pushed camera-relative frame.
     */
    public static void drawLineWorld(MatrixStack matrices, Vec3d start, Vec3d end, int color, float lineWidth) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null || client.gameRenderer.getCamera() == null) {
            return;
        }
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        Vec3d s = start.subtract(camera);
        Vec3d e = end.subtract(camera);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float gC = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
        RenderSystem.lineWidth(Math.max(1.0F, lineWidth));

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        buffer.vertex(matrix, (float) s.x, (float) s.y, (float) s.z).color(r, gC, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, (float) e.x, (float) e.y, (float) e.z).color(r, gC, b, a).normal(0, 1, 0);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * Camera-relative polyline draw. The caller is expected to have
     * already translated {@code matrices} by {@code -cameraPos} so
     * the points can be passed in raw world coordinates and land on
     * screen at the right place. Used by the snowball-tracker flight
     * trail to paint a polyline along every recorded tick-position
     * of a tracked projectile.
     *
     * @param points    sequence of world-space waypoints (size >= 2)
     * @param color     ARGB
     * @param lineWidth GL line width in pixels
     * @param depthTest true to occlude behind world geometry
     */
    public static void drawPolyline(MatrixStack matrices,
                                    java.util.List<Vec3d> points,
                                    int color, float lineWidth, boolean depthTest) {
        if (points == null || points.size() < 2) return;
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (depthTest) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
        RenderSystem.lineWidth(Math.max(1.0F, lineWidth));

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d s = points.get(i);
            Vec3d e = points.get(i + 1);
            buffer.vertex(matrix, (float) s.x, (float) s.y, (float) s.z).color(r, g, b, a).normal(0, 1, 0);
            buffer.vertex(matrix, (float) e.x, (float) e.y, (float) e.z).color(r, g, b, a).normal(0, 1, 0);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * Camera-relative polyline draw with a head-end fade. Same
     * pipeline as {@link #drawPolyline} but the first
     * {@code fadeDistance} blocks of the polyline (measured along
     * the path from {@code points.get(0)}) are alpha-faded from 0
     * at the start to full alpha once the cumulative arc length
     * exceeds {@code fadeDistance}. Subdivides any segment that
     * straddles the fade boundary so the fade reads as a smooth
     * gradient rather than a step at vertex boundaries. Used by
     * the projectile trail so the line "dissolves" right behind
     * the in-flight projectile.
     *
     * @param points       polyline waypoints (size >= 2). The
     *                     ENTRY at index 0 is treated as the
     *                     fade origin (i.e. the projectile's
     *                     current head position).
     * @param color        ARGB; alpha is the upper limit (full-
     *                     opacity portion of the line uses this
     *                     directly).
     * @param lineWidth    GL line width in pixels.
     * @param fadeDistance Length in blocks over which the alpha
     *                     ramps from 0 -> color.alpha. Pass <= 0
     *                     to disable fading.
     * @param depthTest    {@code true} to occlude behind world
     *                     geometry.
     */
    public static void drawPolylineFaded(MatrixStack matrices,
                                         java.util.List<Vec3d> points,
                                         int color, float lineWidth,
                                         float fadeDistance,
                                         boolean depthTest) {
        if (points == null || points.size() < 2) return;
        if (fadeDistance <= 0.0F) {
            drawPolyline(matrices, points, color, lineWidth, depthTest);
            return;
        }
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float maxA = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (depthTest) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
        RenderSystem.lineWidth(Math.max(1.0F, lineWidth));

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);

        // Walk the polyline, accumulating arc length. Within the
        // fade band the alpha ramps linearly from 0 (at length 0)
        // to maxA (at length fadeDistance). If a single segment
        // crosses the boundary we split it so the visible gradient
        // reads as smooth.
        double accLen = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d s = points.get(i);
            Vec3d e = points.get(i + 1);
            double segLen = e.subtract(s).length();
            if (segLen < 1e-7) continue;

            double startLen = accLen;
            double endLen = accLen + segLen;

            float startAlpha = (float) Math.min(1.0, Math.max(0.0, startLen / fadeDistance)) * maxA;
            float endAlpha = (float) Math.min(1.0, Math.max(0.0, endLen / fadeDistance)) * maxA;

            // If this segment crosses the fade boundary, split at
            // the crossing point so we don't get a hard kink in the
            // visual gradient.
            if (startLen < fadeDistance && endLen > fadeDistance) {
                double t = (fadeDistance - startLen) / segLen;
                Vec3d mid = s.add(e.subtract(s).multiply(t));
                // First half: ramp up to full alpha at the boundary.
                buffer.vertex(matrix, (float) s.x, (float) s.y, (float) s.z).color(r, g, b, startAlpha).normal(0, 1, 0);
                buffer.vertex(matrix, (float) mid.x, (float) mid.y, (float) mid.z).color(r, g, b, maxA).normal(0, 1, 0);
                // Second half: full alpha across the rest.
                buffer.vertex(matrix, (float) mid.x, (float) mid.y, (float) mid.z).color(r, g, b, maxA).normal(0, 1, 0);
                buffer.vertex(matrix, (float) e.x, (float) e.y, (float) e.z).color(r, g, b, maxA).normal(0, 1, 0);
            } else {
                buffer.vertex(matrix, (float) s.x, (float) s.y, (float) s.z).color(r, g, b, startAlpha).normal(0, 1, 0);
                buffer.vertex(matrix, (float) e.x, (float) e.y, (float) e.z).color(r, g, b, endAlpha).normal(0, 1, 0);
            }

            accLen = endLen;
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
