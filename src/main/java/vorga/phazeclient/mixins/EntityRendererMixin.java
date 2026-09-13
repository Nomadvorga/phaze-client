package vorga.phazeclient.mixins;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.api.system.draw.PhazeDrawLayers;
import vorga.phazeclient.api.system.shape.implement.Blur;
import vorga.phazeclient.base.util.NametagTextDecorator;
import vorga.phazeclient.base.util.PhazeBadgeUtil;
import vorga.phazeclient.implement.cosmetics.PreviewMarker;
import vorga.phazeclient.implement.features.modules.hud.NametagHud;

import java.util.LinkedHashMap;
import java.util.Map;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    private static boolean phaze$backgroundDrawnThisLabel = false;
    private static boolean phaze$drawBadgeThisLabel = false;
    private static boolean phaze$depthPreparedThisLabel = false;
    private static boolean phaze$fallbackQueuedThisLabel = false;
    private static float phaze$currentLabelDistance = 0.0f;
    private static final int NAMETAG_CACHE_MAX = 256;
    private static final Map<String, Integer> TEXT_WIDTH_CACHE = new LinkedHashMap<>(NAMETAG_CACHE_MAX, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > NAMETAG_CACHE_MAX;
        }
    };
    private static final Map<Integer, Integer> TEXT_COLOR_CACHE = new LinkedHashMap<>(NAMETAG_CACHE_MAX, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
            return size() > NAMETAG_CACHE_MAX;
        }
    };
    private static long lastBackgroundSettingsSignature = Long.MIN_VALUE;
    private static int lastBackgroundInputColor = Integer.MIN_VALUE;
    private static int lastBackgroundResolvedColor = 0;

    @Inject(method = "hasLabel(Lnet/minecraft/entity/Entity;D)Z", at = @At("HEAD"), cancellable = true)
    private void phaze$allowOwnNametagInFirstPerson(Entity entity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir) {
        NametagHud module = NametagHud.getInstance();
        if (!module.isEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null || client.player == null) return;

        if (module.hideInF1.isValue() && client.options.hudHidden) {
            cir.setReturnValue(false);
            return;
        }

        boolean isSelf = entity == client.getCameraEntity() || entity == client.player;
        if (!isSelf) return;

        boolean firstPerson = client.options.getPerspective().isFirstPerson();
        cir.setReturnValue(!firstPerson && module.thirdPersonNametag.isValue());
    }

    @Inject(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void phaze$controlNametagVisibility(EntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci) {

        if (state instanceof PreviewMarker) {
            ci.cancel();
            return;
        }
        NametagHud module = NametagHud.getInstance();
        if (!module.isEnabled()) return;

        phaze$currentLabelDistance = (float) Math.sqrt(Math.max(0.0, state.squaredDistanceToCamera));
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) return;

        if (module.hideInF1.isValue() && client.options.hudHidden) {
            ci.cancel();
            return;
        }

        if (state.squaredDistanceToCamera > 4096.0) {
            ci.cancel();
            return;
        }
        phaze$backgroundDrawnThisLabel = false;
        phaze$drawBadgeThisLabel = false;
        phaze$depthPreparedThisLabel = false;
        phaze$fallbackQueuedThisLabel = false;
    }

    @ModifyArg(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitLabel(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/math/Vec3d;ILnet/minecraft/text/Text;ZIDLnet/minecraft/client/render/state/CameraRenderState;)V"
            ),
            index = 3
    )
    private Text phaze$decorateNametagText(Text original) {
        return NametagTextDecorator.decorate(original, phaze$currentLabelDistance);
    }

    @SuppressWarnings("unused")
    private static int phaze$resolvedTextColor(int originalColor) {
        Integer cached = TEXT_COLOR_CACHE.get(originalColor);
        if (cached != null) {
            return cached;
        }
        int resolved = originalColor;
        TEXT_COLOR_CACHE.put(originalColor, resolved);
        return resolved;
    }

    private static int phaze$resolvedBackgroundColor(int vanillaBackgroundColor) {
        NametagHud module = NametagHud.getInstance();
        if (!module.isEnabled()) {
            return vanillaBackgroundColor;
        }
        if (!module.background.isValue()) {
            return 0;
        }

        long settingsSignature = phaze$backgroundSettingsSignature(module);
        if (settingsSignature == lastBackgroundSettingsSignature && vanillaBackgroundColor == lastBackgroundInputColor) {
            return lastBackgroundResolvedColor;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        int out = client != null ? module.getResolvedBackgroundColor(client) : vanillaBackgroundColor;

        lastBackgroundSettingsSignature = settingsSignature;
        lastBackgroundInputColor = vanillaBackgroundColor;
        lastBackgroundResolvedColor = out;
        return out;
    }

    @SuppressWarnings("unused")
    private static void phaze$drawNametagBadgeIfNeeded(
            Matrix4f matrix,
            VertexConsumerProvider vertexConsumers,
            float x,
            float y,
            TextRenderer.TextLayerType layerType,
            int light
    ) {
        if (!phaze$drawBadgeThisLabel) {
            return;
        }
        PhazeBadgeUtil.drawWorldBadge(matrix, vertexConsumers, layerType, x - 2.0F, y - 1.0F, 10.0F, light, 0xFFFFFFFF, false);
    }

    @SuppressWarnings("unused")
    private static int phaze$drawBlurBackgroundIfNeeded(
            float textWidth,
            Matrix4f matrix,
            float x,
            float y,
            VertexConsumerProvider vertexConsumers,
            TextRenderer.TextLayerType layerType,
            int vanillaBackgroundColor
    ) {
        NametagHud module = NametagHud.getInstance();
        if (!module.isEnabled() || !module.background.isValue()) {
            return phaze$resolvedBackgroundColor(vanillaBackgroundColor);
        }
        float blurRadius = module.backgroundBlurRadius.getValue();
        int background = phaze$resolvedBackgroundColor(vanillaBackgroundColor);

        if (layerType != TextRenderer.TextLayerType.NORMAL) {

            if (phaze$shouldDrawDepthAwareBlur(textWidth, blurRadius)) {

                phaze$prepareDepthAwareBlur(vertexConsumers);
            }
            if (blurRadius > 0.0f) {
                phaze$fallbackQueuedThisLabel = true;
                return background;
            }
            return 0;
        }

        if (blurRadius <= 0.0f || phaze$backgroundDrawnThisLabel) {
            return background;
        }

        float left = x - 1.0f;
        float top = y - 1.0f;
        float rightExtend = -1.0f;
        float width = textWidth + 2.0f + rightExtend;
        float height = 10.0f;

        left = phaze$snapHalfPixel(left);
        top = phaze$snapHalfPixel(top);
        width = phaze$snapHalfPixel(Math.max(0.0f, width));
        height = phaze$snapHalfPixel(Math.max(0.0f, height));

        if (width * height < 50.0f) {
            if (!phaze$fallbackQueuedThisLabel) {
                drawSolidRect3D(matrix, left, top, width, height, background);
            }
            phaze$backgroundDrawnThisLabel = true;
            return 0;
        }

        float quality = MathHelper.clamp(0.35f + blurRadius * 0.10f, 0.35f, 4.2f);
        MinecraftClient client = MinecraftClient.getInstance();
        float distance = phaze$currentLabelDistance;
        float distanceFactor = phaze$blurDistanceFactor(distance);
        float playerSpeed = Blur.INSTANCE.getPlayerSpeed(client);

        if (distanceFactor <= 0.001f) {
            if (!phaze$fallbackQueuedThisLabel) {
                drawSolidRect3D(matrix, left, top, width, height, background);
            }
            phaze$backgroundDrawnThisLabel = true;
            return 0;
        }
        quality *= distanceFactor;

        if (playerSpeed > 20.0f) {
            float speedFactor = MathHelper.clamp(20.0f / playerSpeed, 0.3f, 1.0f);
            quality *= speedFactor;
        }

        if (phaze$fallbackQueuedThisLabel) {

            phaze$flushCurrentNametagLayer(vertexConsumers);
        }
        phaze$prepareDepthAwareBlur(vertexConsumers);
        Blur.INSTANCE.renderWorldRect(
                matrix,
                left,
                top,
                width,
                height,
                quality,
                background,
                !phaze$fallbackQueuedThisLabel,
                distanceFactor
        );
        phaze$backgroundDrawnThisLabel = true;
        return 0;
    }

    private static boolean phaze$shouldDrawDepthAwareBlur(float textWidth, float blurRadius) {
        float width = phaze$snapHalfPixel(Math.max(0.0f, textWidth + 1.0f));
        return blurRadius > 0.0f
                && width * 10.0f >= 50.0f
                && phaze$blurDistanceFactor(phaze$currentLabelDistance) > 0.001f;
    }

    private static float phaze$blurDistanceFactor(float distance) {
        if (distance <= 24.0f) {
            return 1.0f;
        }
        if (distance >= 30.0f) {
            return 0.0f;
        }
        float t = MathHelper.clamp((distance - 24.0f) / 6.0f, 0.0f, 1.0f);
        float smoothstep = t * t * (3.0f - 2.0f * t);
        return 1.0f - smoothstep;
    }

    private static void phaze$prepareDepthAwareBlur(VertexConsumerProvider vertexConsumers) {
        if (phaze$depthPreparedThisLabel) {
            return;
        }

        phaze$flushCurrentNametagLayer(vertexConsumers);
        Blur.INSTANCE.prepareWorldRectInput();
        phaze$depthPreparedThisLabel = true;
    }

    private static void phaze$flushCurrentNametagLayer(VertexConsumerProvider vertexConsumers) {
        VertexConsumerProvider.Immediate immediate = null;
        if (vertexConsumers instanceof VertexConsumerProvider.Immediate direct) {
            immediate = direct;
        } else if (vertexConsumers instanceof OutlineVertexConsumerProvider outline) {
            immediate = ((OutlineVertexConsumerProviderAccessor) outline).phaze$getParent();
        }
        if (immediate != null) {

            immediate.drawCurrentLayer();
        }
    }

    @SuppressWarnings("unused")
    private static int phaze$getCachedTextWidth(TextRenderer textRenderer, Text text) {
        String key = text.getString();
        Integer cached = TEXT_WIDTH_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        int width = textRenderer.getWidth(text);
        TEXT_WIDTH_CACHE.put(key, width);
        return width;
    }

    @SuppressWarnings("unused")
    private static int phaze$getCachedTextWidth(TextRenderer textRenderer, OrderedText text) {
        String key = text.toString();
        Integer cached = TEXT_WIDTH_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        int width = textRenderer.getWidth(text);
        TEXT_WIDTH_CACHE.put(key, width);
        return width;
    }

    private static long phaze$backgroundSettingsSignature(NametagHud module) {
        long h = 0x9E3779B97F4A7C15L;
        h = (h * 0x100000001B3L) ^ (module.background.isValue() ? 1 : 0);
        String preset = module.backgroundPreset.getSelected();
        h = (h * 0x100000001B3L) ^ (preset == null ? 0 : preset.hashCode());
        h = (h * 0x100000001B3L) ^ Math.round(module.colorBrightness.getValue() * 100.0f);
        h = (h * 0x100000001B3L) ^ Math.round(module.backgroundOpacity.getValue() * 100.0f);
        return h;
    }

    private static void drawSolidRect3D(Matrix4f matrix, float x, float y, float width, float height, int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255.0f;
        if (a <= 0.0f || width <= 0.0f || height <= 0.0f) return;
        float r = ((argb >>> 16) & 0xFF) / 255.0f;
        float g = ((argb >>> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;

        BufferBuilder buffer = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, x, y, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, x, y + height, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, x + width, y + height, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, x + width, y, 0.0f).color(r, g, b, a);
        PhazeDrawLayers.POSITION_COLOR.draw(buffer.end());
    }

    private static float phaze$snapHalfPixel(float value) {
        return Math.round(value * 2.0f) * 0.5f;
    }
}
