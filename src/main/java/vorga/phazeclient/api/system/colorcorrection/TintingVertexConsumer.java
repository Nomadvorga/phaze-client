package vorga.phazeclient.api.system.colorcorrection;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;

public class TintingVertexConsumer implements VertexConsumer {
    private static final int PHAZE$PLAYER_MARKER_ALPHA = 254;
    private static final int PHAZE$ENTITY_MARKER_ALPHA = 253;

    private final VertexConsumer parent;
    private final WorldColorCorrectionController.Target target;
    private final boolean colorTransform;
    private final boolean alphaTransform;
    private final boolean entityTextureCorrection;

    // Correction factors, resolved once here instead of per vertex.
    //
    // A consumer is created per render layer per pass and lives for that pass
    // only, so the sliders behind these values cannot change while it is in
    // use - they are only edited from the GUI, between frames. Previously
    // every single vertex re-read them through
    // ColorCorrection.getInstance(), which meant an enum switch and a
    // settings read per factor, plus an isTargetActive() check that itself
    // re-read all six sliders for the target. Roughly 25 settings reads per
    // vertex to recompute six constants.
    private final boolean alphaBlend;
    private final float alphaMultiplier;
    private final float redMultiplier;
    private final float greenMultiplier;
    private final float blueMultiplier;
    private final float saturation;
    private final float brightness;

    public TintingVertexConsumer(VertexConsumer parent, WorldColorCorrectionController.Target target) {
        this.parent = parent;
        this.target = target;
        this.colorTransform = WorldColorCorrectionController.needsColorTransform(target);
        this.alphaTransform = WorldColorCorrectionController.needsAlphaTransform(target);
        this.entityTextureCorrection = colorTransform
                && (target == WorldColorCorrectionController.Target.PLAYERS
                || target == WorldColorCorrectionController.Target.ENTITIES);

        if (colorTransform) {
            this.redMultiplier = WorldColorCorrectionController.getRed(target);
            this.greenMultiplier = WorldColorCorrectionController.getGreen(target);
            this.blueMultiplier = WorldColorCorrectionController.getBlue(target);
            this.saturation = WorldColorCorrectionController.getSaturation(target);
            this.brightness = WorldColorCorrectionController.getBrightness(target);
        } else {
            this.redMultiplier = 1.0F;
            this.greenMultiplier = 1.0F;
            this.blueMultiplier = 1.0F;
            this.saturation = 1.0F;
            this.brightness = 0.0F;
        }

        // needsBlend uses a < 0.999 threshold while needsAlphaTransform uses
        // != 1.0, so an alpha in (0.999, 1.0) transforms to itself. Keeping
        // both flags preserves that exact edge case.
        this.alphaBlend = alphaTransform && WorldColorCorrectionController.needsBlend(target);
        this.alphaMultiplier = alphaTransform
                ? WorldColorCorrectionController.getAlpha(target)
                : 1.0F;
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        parent.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        if (!colorTransform && !alphaTransform) {
            parent.color(red, green, blue, alpha);
            return this;
        }
        parent.color(phaze$transformArgb((alpha << 24) | (red << 16) | (green << 8) | blue));
        return this;
    }

    @Override
    public VertexConsumer color(float red, float green, float blue, float alpha) {
        if (!colorTransform && !alphaTransform) {
            parent.color(red, green, blue, alpha);
            return this;
        }

        int argb = (Math.round(alpha * 255.0F) << 24)
                | (Math.round(red * 255.0F) << 16)
                | (Math.round(green * 255.0F) << 8)
                | Math.round(blue * 255.0F);
        parent.color(phaze$transformArgb(argb));
        return this;
    }

    @Override
    public VertexConsumer color(int argb) {
        if (!colorTransform && !alphaTransform) {
            parent.color(argb);
            return this;
        }
        parent.color(phaze$transformArgb(argb));
        return this;
    }

    // 1.21.11: VertexConsumer.colorRgb(int) was removed from the interface; the
    // opaque-RGB shorthand is gone and callers pass a full ARGB through color(int).
    // Nothing in Phaze called it, so the override is dropped rather than kept as a
    // dead non-override method.

    @Override
    public VertexConsumer texture(float u, float v) {
        parent.texture(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        parent.overlay(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlay(int uv) {
        parent.overlay(uv);
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        parent.light(u, v);
        return this;
    }

    @Override
    public VertexConsumer light(int uv) {
        parent.light(uv);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        parent.normal(x, y, z);
        return this;
    }

    // 1.21.11: line width became a per-vertex attribute (VertexFormatElement.LINE_WIDTH)
    // and lineWidth(float) is now abstract on VertexConsumer, replacing the removed
    // global RenderSystem.lineWidth. Colour correction does not touch it - pass through.
    @Override
    public VertexConsumer lineWidth(float width) {
        parent.lineWidth(width);
        return this;
    }

    @Override
    public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
        parent.vertex(x, y, z, phaze$transformArgb(color), u, v, overlay, light, normalX, normalY, normalZ);
    }

    @Override
    public void quad(MatrixStack.Entry matrixEntry, BakedQuad quad, float red, float green, float blue, float alpha, int light, int overlay) {
        if (!colorTransform && !alphaTransform) {
            parent.quad(matrixEntry, quad, red, green, blue, alpha, light, overlay);
            return;
        }

        int corrected = phaze$transformArgb(
                (Math.round(alpha * 255.0F) << 24)
                        | (Math.round(red * 255.0F) << 16)
                        | (Math.round(green * 255.0F) << 8)
                        | Math.round(blue * 255.0F)
        );
        parent.quad(
                matrixEntry,
                quad,
                ((corrected >>> 16) & 255) / 255.0F,
                ((corrected >>> 8) & 255) / 255.0F,
                (corrected & 255) / 255.0F,
                ((corrected >>> 24) & 255) / 255.0F,
                light,
                overlay
        );
    }

    // 1.21.11: the trailing `boolean useQuadColorData` parameter was removed from this
    // overload. BakedQuad no longer carries per-vertex colour data on this path - vanilla's
    // default impl now derives the vertex colour purely from brightnesses[i] * (r,g,b) with
    // the given alpha - so there is no flag left to forward. Tinting is unaffected: we still
    // correct the r/g/b/a tint before handing it down.
    @Override
    public void quad(MatrixStack.Entry matrixEntry, BakedQuad quad, float[] brightnesses, float red, float green, float blue, float alpha, int[] lights, int overlay) {
        if (!colorTransform && !alphaTransform) {
            parent.quad(matrixEntry, quad, brightnesses, red, green, blue, alpha, lights, overlay);
            return;
        }

        int corrected = phaze$transformArgb(
                (Math.round(alpha * 255.0F) << 24)
                        | (Math.round(red * 255.0F) << 16)
                        | (Math.round(green * 255.0F) << 8)
                        | Math.round(blue * 255.0F)
        );
        parent.quad(
                matrixEntry,
                quad,
                brightnesses,
                ((corrected >>> 16) & 255) / 255.0F,
                ((corrected >>> 8) & 255) / 255.0F,
                (corrected & 255) / 255.0F,
                ((corrected >>> 24) & 255) / 255.0F,
                lights,
                overlay
        );
    }

    final int phaze$transformArgb(int argb) {
        int originalAlpha = (argb >>> 24) & 0xFF;
        int correctedAlpha = alphaTransform
                ? WorldColorCorrectionController.applyAlphaIntWith(originalAlpha, alphaBlend, alphaMultiplier)
                : originalAlpha;
        int encodedAlpha = phaze$encodeEntityMarkerAlpha(correctedAlpha);
        boolean shaderHandlesRgb = entityTextureCorrection && encodedAlpha != correctedAlpha;
        int rgb = argb & 0x00FFFFFF;
        if (colorTransform && !shaderHandlesRgb) {
            rgb = WorldColorCorrectionController.applyRgbWith(
                    (argb >>> 16) & 0xFF,
                    (argb >>> 8) & 0xFF,
                    argb & 0xFF,
                    redMultiplier,
                    greenMultiplier,
                    blueMultiplier,
                    saturation,
                    brightness
            );
        }
        return (encodedAlpha << 24) | rgb;
    }

    final int phaze$encodeEntityMarkerAlpha(int alpha) {
        if (!entityTextureCorrection || alpha < 250) {
            return alpha;
        }
        return target == WorldColorCorrectionController.Target.PLAYERS
                ? PHAZE$PLAYER_MARKER_ALPHA
                : PHAZE$ENTITY_MARKER_ALPHA;
    }
}
