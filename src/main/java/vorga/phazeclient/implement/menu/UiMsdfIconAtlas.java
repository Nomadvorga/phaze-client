package vorga.phazeclient.implement.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.font.msdf.ResourceProvider;
import vorga.phazeclient.api.system.font.msdf.MsdfBatch;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.api.system.shape.batched.GuiBatch;
import vorga.phazeclient.api.system.shape.batched.GuiDrawStats;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Static UI icon MSDF atlas shared by the main menu and in-game GUI.
 *
 * <p>The atlas itself is generated offline by {@code tools/generate_ui_msdf_icons.py}
 * and loaded here as a regular resource texture. That keeps runtime rendering
 * fast and ensures we actually use real RGB MSDF data instead of a single-channel
 * SDF generated on the client.</p>
 */
public final class UiMsdfIconAtlas {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final Identifier ATLAS_TEXTURE_ID = Identifier.of("phaze", "msdf/ui_icons.png");
    private static final Identifier ATLAS_DATA_ID = Identifier.of("phaze", "msdf/ui_icons.json");
    public static final Identifier BACK_ARROW_ICON_ID = Identifier.ofVanilla("textures/back_arrow.png");
    /** Visible chevron height in back_arrow.svg as a fraction of its 24-unit box, the PNG's size convention. */
    private static final float BACK_ARROW_INK_HEIGHT = 14.7972F / 24.0F;
    private static final float DEFAULT_DISTANCE_RANGE = 12.0F;
    private static final float SMOOTHNESS = 0.42F;
    private static final float THICKNESS = 0.0F;
    private static final float UV_INSET = 1.0F;

    private static final Map<Identifier, AtlasIcon> ATLAS_ICONS = new HashMap<>();
    private static final Set<Identifier> MISSING_ICONS = new HashSet<>();
    private static final Map<Identifier, Float> ASPECT_RATIO_CACHE = new HashMap<>();

    private static AbstractTexture atlasTexture;
    private static float distanceRange = DEFAULT_DISTANCE_RANGE;
    private static boolean atlasLoaded;
    private static boolean filterApplied;
    private static boolean globalFailed;

    private UiMsdfIconAtlas() {
    }

    public static void warmup() {
        synchronized (UiMsdfIconAtlas.class) {
            ensureAtlasLoaded();
        }
    }

    /** Resource reloads reset the atlas sampler parameters in-place. */
    public static synchronized void onResourcesReloaded() {
        filterApplied = false;
        atlasTexture = null;
        atlasLoaded = false;
    }

    public static void registerTexture(String texture) {
        try {
            registerTexture(Identifier.of(texture));
        } catch (Throwable ignored) {
        }
    }

    public static synchronized void registerTexture(Identifier icon) {
        if (icon == null || globalFailed) {
            return;
        }
        ensureAtlasLoaded();
    }

    /** The back chevron, as large as the old 24px texture at {@code size} and centred on its visible stroke. */
    public static boolean renderBackArrow(DrawContext context, float centerX, float centerY, float size, int color) {
        IconInk ink = ink(BACK_ARROW_ICON_ID);
        float drawSize = size * BACK_ARROW_INK_HEIGHT / Math.max(0.0001F, ink.height());
        return renderIcon(context, BACK_ARROW_ICON_ID,
                centerX - drawSize * ink.centerX(), centerY - drawSize * ink.centerY(),
                drawSize, drawSize, color, true);
    }

    /** Scales the icon until its visible shape fills {@code maxInkWidth} x {@code maxInkHeight} and centres that shape on the point. */
    public static boolean renderIconInkFitted(DrawContext context, Identifier icon, float centerX, float centerY,
                                              float maxInkWidth, float maxInkHeight, int color) {
        IconInk ink = ink(icon);
        float aspect = resolveAspectRatio(icon);
        float scale = Math.min(maxInkWidth / Math.max(0.0001F, aspect * ink.width()),
                maxInkHeight / Math.max(0.0001F, ink.height()));
        return renderIconInkScaled(context, icon, centerX, centerY, scale, color);
    }

    /** Draws the icon's quad {@code quadHeight} tall with its visible shape, not its padded cell, centred on the point. */
    public static boolean renderIconInkScaled(DrawContext context, Identifier icon, float centerX, float centerY,
                                              float quadHeight, int color) {
        IconInk ink = ink(icon);
        float quadWidth = quadHeight * resolveAspectRatio(icon);
        return renderIcon(context, icon, centerX - quadWidth * ink.centerX(), centerY - quadHeight * ink.centerY(),
                quadWidth, quadHeight, color, true);
    }

    public static boolean renderIcon(
            DrawContext context,
            Identifier icon,
            float x,
            float y,
            float width,
            float height,
            int color,
            boolean precise
    ) {
        return renderIcon(context, icon, x, y, width, height, color, precise, THICKNESS, SMOOTHNESS);
    }

    public static boolean renderIcon(
            DrawContext context,
            Identifier icon,
            float x,
            float y,
            float width,
            float height,
            int color,
            boolean precise,
            float thickness,
            float smoothness
    ) {
        AtlasIcon atlasIcon = ensureIconReady(icon);
        if (atlasIcon == null) {
            return false;
        }

        float x1 = precise ? x : Math.round(x);
        float y1 = precise ? y : Math.round(y);
        float drawWidth = precise ? Math.max(1.0F, width) : Math.max(1.0F, Math.round(width));
        float drawHeight = precise ? Math.max(1.0F, height) : Math.max(1.0F, Math.round(height));
        FittedRect fittedRect = fitRect(x1, y1, drawWidth, drawHeight, atlasIcon.aspectRatio);
        return renderQuad(
                context.getMatrices().peek().getPositionMatrix(),
                atlasIcon,
                fittedRect.left,
                fittedRect.top,
                fittedRect.right,
                fittedRect.bottom,
                color,
                false,
                thickness,
                smoothness
        );
    }

    public static boolean renderIcon(
            MatrixStack matrix,
            Identifier icon,
            float x,
            float y,
            float width,
            float height,
            int color
    ) {
        AtlasIcon atlasIcon = ensureIconReady(icon);
        if (atlasIcon == null) {
            return false;
        }
        FittedRect fittedRect = fitRect(x, y, Math.max(1.0F, width), Math.max(1.0F, height), atlasIcon.aspectRatio);
        return renderQuad(matrix.peek().getPositionMatrix(), atlasIcon, fittedRect.left, fittedRect.top, fittedRect.right, fittedRect.bottom, color, true, THICKNESS, SMOOTHNESS);
    }

    public static boolean renderIcon(
            Matrix4f matrix,
            Identifier icon,
            float x,
            float y,
            float width,
            float height,
            int color,
            boolean precise
    ) {
        AtlasIcon atlasIcon = ensureIconReady(icon);
        if (atlasIcon == null) {
            return false;
        }

        float x1 = precise ? x : Math.round(x);
        float y1 = precise ? y : Math.round(y);
        float drawWidth = precise ? Math.max(1.0F, width) : Math.max(1.0F, Math.round(width));
        float drawHeight = precise ? Math.max(1.0F, height) : Math.max(1.0F, Math.round(height));
        FittedRect fittedRect = fitRect(x1, y1, drawWidth, drawHeight, atlasIcon.aspectRatio);
        return renderQuad(matrix, atlasIcon, fittedRect.left, fittedRect.top, fittedRect.right, fittedRect.bottom, color, false, THICKNESS, SMOOTHNESS);
    }

    private static synchronized boolean ensureAtlasLoaded() {
        if (atlasLoaded) {
            return true;
        }
        if (globalFailed || MC == null || MC.getTextureManager() == null) {
            return false;
        }

        try {
            AtlasDefinition definition = ResourceProvider.fromJsonToInstance(ATLAS_DATA_ID, AtlasDefinition.class);
            if (definition == null || definition.atlas == null) {
                globalFailed = true;
                return false;
            }

            atlasTexture = MC.getTextureManager().getTexture(ATLAS_TEXTURE_ID);
            if (atlasTexture == null) {
                globalFailed = true;
                return false;
            }

            float atlasWidth = Math.max(1.0F, definition.atlas.width);
            float atlasHeight = Math.max(1.0F, definition.atlas.height);
            distanceRange = definition.atlas.distanceRange > 0.0F ? definition.atlas.distanceRange : DEFAULT_DISTANCE_RANGE;

            ATLAS_ICONS.clear();
            MISSING_ICONS.clear();
            if (definition.icons != null) {
                for (IconDefinition iconDefinition : definition.icons) {
                    if (iconDefinition == null || iconDefinition.id == null || iconDefinition.atlasBounds == null) {
                        continue;
                    }

                    try {
                        Identifier identifier = Identifier.of(iconDefinition.id);
                        AtlasBounds bounds = iconDefinition.atlasBounds;
                        float minU = (bounds.left + UV_INSET) / atlasWidth;
                        float maxU = (bounds.right - UV_INSET) / atlasWidth;
                        float minV = (bounds.top + UV_INSET) / atlasHeight;
                        float maxV = (bounds.bottom - UV_INSET) / atlasHeight;
                        float iconWidth = Math.max(1.0F, bounds.right - bounds.left);
                        float iconHeight = Math.max(1.0F, bounds.bottom - bounds.top);
                        float aspectRatio = iconDefinition.contentAspectRatio > 0.0F
                                ? iconDefinition.contentAspectRatio
                                : iconWidth / iconHeight;
                        ATLAS_ICONS.put(identifier, new AtlasIcon(minU, minV, maxU, maxV, aspectRatio, resolveInk(bounds, iconDefinition.inkBounds)));
                        ASPECT_RATIO_CACHE.put(identifier, aspectRatio);
                    } catch (Throwable ignored) {
                    }
                }
            }

            filterApplied = false;
            atlasLoaded = true;
            return true;
        } catch (Throwable ignored) {
            globalFailed = true;
            ATLAS_ICONS.clear();
            MISSING_ICONS.clear();
            return false;
        }
    }

    private static synchronized AtlasIcon ensureIconReady(Identifier icon) {
        if (icon == null || globalFailed || MC == null || MC.getTextureManager() == null) {
            return null;
        }
        if (!atlasLoaded && !ensureAtlasLoaded()) {
            return null;
        }
        if (MISSING_ICONS.contains(icon)) {
            return null;
        }

        AtlasIcon atlasIcon = ATLAS_ICONS.get(icon);
        if (atlasIcon == null) {
            MISSING_ICONS.add(icon);
        }
        return atlasIcon;
    }

    /** Visible shape of an icon as fractions of the quad it is drawn into; the full quad when unknown. */
    public static synchronized IconInk ink(Identifier icon) {
        AtlasIcon atlasIcon = ensureIconReady(icon);
        return atlasIcon == null ? IconInk.FULL : atlasIcon.ink;
    }

    private static IconInk resolveInk(AtlasBounds cell, AtlasBounds ink) {
        if (ink == null) {
            return IconInk.FULL;
        }
        // The quad samples the cell inset by UV_INSET texels on each side.
        float left = cell.left + UV_INSET;
        float top = cell.top + UV_INSET;
        float width = Math.max(1.0F, cell.right - cell.left - UV_INSET * 2.0F);
        float height = Math.max(1.0F, cell.bottom - cell.top - UV_INSET * 2.0F);
        return new IconInk(
                (ink.left - left) / width,
                (ink.top - top) / height,
                (ink.right - left) / width,
                (ink.bottom - top) / height
        );
    }

    public static float resolveAspectRatio(Identifier icon) {
        if (icon == null) {
            return 1.0F;
        }

        Float cached = ASPECT_RATIO_CACHE.get(icon);
        if (cached != null && cached > 0.0F) {
            return cached;
        }

        try {
            if (!atlasLoaded) {
                ensureAtlasLoaded();
            }

            AtlasIcon atlasIcon = ATLAS_ICONS.get(icon);
            if (atlasIcon != null && atlasIcon.aspectRatio > 0.0F) {
                ASPECT_RATIO_CACHE.put(icon, atlasIcon.aspectRatio);
                return atlasIcon.aspectRatio;
            }

            return resolveResourceAspectRatio(icon);
        } catch (Throwable ignored) {
        }

        return 1.0F;
    }

    private static float resolveResourceAspectRatio(Identifier icon) {
        try {
            Optional<Resource> resource = MC.getResourceManager().getResource(icon);
            if (resource.isPresent()) {
                try (InputStream stream = resource.get().getInputStream()) {
                    NativeImage image = NativeImage.read(stream);
                    float aspectRatio = image.getWidth() / (float) Math.max(1, image.getHeight());
                    image.close();
                    ASPECT_RATIO_CACHE.put(icon, aspectRatio);
                    return aspectRatio;
                }
            }
        } catch (Throwable ignored) {
        }
        return 1.0F;
    }

    private static FittedRect fitRect(float x, float y, float width, float height, float aspectRatio) {
        float safeAspect = aspectRatio > 0.0F ? aspectRatio : 1.0F;
        float drawWidth = width;
        float drawHeight = drawWidth / safeAspect;

        if (drawHeight > height) {
            drawHeight = height;
            drawWidth = drawHeight * safeAspect;
        }

        float offsetX = (width - drawWidth) * 0.5F;
        float offsetY = (height - drawHeight) * 0.5F;
        return new FittedRect(x + offsetX, y + offsetY, x + offsetX + drawWidth, y + offsetY + drawHeight);
    }

    private static boolean renderQuad(
            Matrix4f matrix,
            AtlasIcon atlasIcon,
            float x1,
            float y1,
            float x2,
            float y2,
            int color,
            boolean legacyImageOrientation,
            float thickness,
            float smoothness
    ) {
        if (!filterApplied && atlasTexture != null) {
            atlasTexture.setFilter(true, false);
            filterApplied = true;
        }

        if (atlasTexture != null && MsdfBatch.canDefer()) {
            GuiBatch.Run run = MsdfBatch.beginIcon(atlasTexture.getGlId(), distanceRange, thickness, smoothness);
            // Same corner-to-UV mapping as emitQuad; the legacy orientation is a quarter turn.
            if (legacyImageOrientation) {
                run.quad(matrix, x1, y1, x2 - x1, y2 - y1, 0.0F,
                        atlasIcon.maxU, atlasIcon.minV, atlasIcon.minU, atlasIcon.maxV, color, GuiBatch.UV_SWAP);
            } else {
                run.quad(matrix, x1, y1, x2 - x1, y2 - y1, 0.0F,
                        atlasIcon.minU, atlasIcon.minV, atlasIcon.maxU, atlasIcon.maxV, color, 0);
            }
            return true;
        }

        BatchedRectangle.flushIfBatching();
        GuiDrawStats.msdfDraws++;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, ATLAS_TEXTURE_ID);

        ShaderProgram shader = RenderSystem.setShader(MsdfRenderer.MSDF_FONT_SHADER_KEY);
        if (shader != null) {
            shader.getUniform("Range").set(distanceRange);
            shader.getUniform("Thickness").set(thickness);
            shader.getUniform("Smoothness").set(smoothness);
            shader.getUniform("Outline").set(0);
            shader.getUniform("OutlineThickness").set(0.0F);
            shader.getUniform("OutlineColor").set(0.0F, 0.0F, 0.0F, 0.0F);
            shader.getUniform("EnableFadeout").set(0);
            shader.getUniform("LabelFadeEnabled").set(0);
            shader.getUniform("FadeoutStart").set(0.0F);
            shader.getUniform("FadeoutEnd").set(1.0F);
            shader.getUniform("MaxWidth").set(0.0F);
            shader.getUniform("TextPosX").set(x1);
            shader.getUniform("ColorModulator").set(1.0F, 1.0F, 1.0F, 1.0F);
        }

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        emitQuad(builder, matrix, atlasIcon, x1, y1, x2, y2, color, legacyImageOrientation);

        BuiltBuffer builtBuffer = builder.endNullable();
        if (builtBuffer != null) {
            BufferRenderer.drawWithGlobalProgram(builtBuffer);
        }

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        return true;
    }

    private static void emitQuad(BufferBuilder builder, Matrix4f matrix, AtlasIcon atlasIcon,
                                 float x1, float y1, float x2, float y2, int color, boolean legacyImageOrientation) {
        if (legacyImageOrientation) {
            builder.vertex(matrix, x1, y2, 0.0F).texture(atlasIcon.minU, atlasIcon.minV).color(color);
            builder.vertex(matrix, x2, y2, 0.0F).texture(atlasIcon.minU, atlasIcon.maxV).color(color);
            builder.vertex(matrix, x2, y1, 0.0F).texture(atlasIcon.maxU, atlasIcon.maxV).color(color);
            builder.vertex(matrix, x1, y1, 0.0F).texture(atlasIcon.maxU, atlasIcon.minV).color(color);
        } else {
            builder.vertex(matrix, x1, y1, 0.0F).texture(atlasIcon.minU, atlasIcon.minV).color(color);
            builder.vertex(matrix, x1, y2, 0.0F).texture(atlasIcon.minU, atlasIcon.maxV).color(color);
            builder.vertex(matrix, x2, y2, 0.0F).texture(atlasIcon.maxU, atlasIcon.maxV).color(color);
            builder.vertex(matrix, x2, y1, 0.0F).texture(atlasIcon.maxU, atlasIcon.minV).color(color);
        }
    }

    private static final class AtlasDefinition {
        private AtlasMetadata atlas;
        private List<IconDefinition> icons;
    }

    private static final class AtlasMetadata {
        private float distanceRange;
        private float width;
        private float height;
    }

    private static final class IconDefinition {
        private String id;
        private float contentAspectRatio;
        private AtlasBounds atlasBounds;
        private AtlasBounds inkBounds;
    }

    private static final class AtlasBounds {
        private float left;
        private float top;
        private float right;
        private float bottom;
    }

    private record AtlasIcon(float minU, float minV, float maxU, float maxV, float aspectRatio, IconInk ink) {
    }

    public record IconInk(float left, float top, float right, float bottom) {
        public static final IconInk FULL = new IconInk(0.0F, 0.0F, 1.0F, 1.0F);

        public float width() {
            return right - left;
        }

        public float height() {
            return bottom - top;
        }

        public float centerX() {
            return (left + right) * 0.5F;
        }

        public float centerY() {
            return (top + bottom) * 0.5F;
        }
    }

    private record FittedRect(float left, float top, float right, float bottom) {
    }
}
