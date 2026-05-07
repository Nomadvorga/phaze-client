package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.api.system.shape.implement.Blur;
import vorga.phazeclient.implement.features.modules.hud.NametagHud;

import java.util.LinkedHashMap;
import java.util.Map;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererNametagMixin {
    private static boolean phaze$backgroundDrawnThisLabel = false;
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

        if (!module.thirdPersonNametag.isValue() && !client.options.getPerspective().isFirstPerson()) {
            cir.setReturnValue(false);
            return;
        }

        cir.setReturnValue(true);
    }

    @Inject(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void phaze$controlNametagVisibility(EntityRenderState state, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        NametagHud module = NametagHud.getInstance();
        if (!module.isEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) return;

        if (module.hideInF1.isValue() && client.options.hudHidden) {
            ci.cancel();
            return;
        }
        
        if (state != null && state.invisible) {
            ci.cancel();
            return;
        }
        
        if (client.cameraEntity != null && client.player != null) {
            double distance = client.cameraEntity.squaredDistanceTo(client.player);
            if (distance > 4096.0) {
                ci.cancel();
                return;
            }
        }
        
        phaze$backgroundDrawnThisLabel = false;
    }

    @Redirect(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I")
    )
    private int phaze$drawNametagWithSettings(TextRenderer textRenderer, Text text, float x, float y, int color, boolean shadow, Matrix4f matrix, VertexConsumerProvider vertexConsumers, TextRenderer.TextLayerType layerType, int backgroundColor, int light) {
        NametagHud module = NametagHud.getInstance();
        int resolvedBackground = phaze$drawBlurBackgroundIfNeeded(phaze$getCachedTextWidth(textRenderer, text), matrix, x, y, layerType, backgroundColor);
        TextRenderer.TextLayerType forcedLayer = layerType;
        return textRenderer.draw(
                text,
                x,
                y,
                phaze$resolvedTextColor(color),
                module.isEnabled() ? module.nametagTextShadow.isValue() : shadow,
                matrix,
                vertexConsumers,
                forcedLayer,
                resolvedBackground,
                light
        );
    }

    @Redirect(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/text/OrderedText;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I")
    )
    private int phaze$drawOrderedNametagWithSettings(TextRenderer textRenderer, OrderedText text, float x, float y, int color, boolean shadow, Matrix4f matrix, VertexConsumerProvider vertexConsumers, TextRenderer.TextLayerType layerType, int backgroundColor, int light) {
        NametagHud module = NametagHud.getInstance();
        int resolvedBackground = phaze$drawBlurBackgroundIfNeeded(phaze$getCachedTextWidth(textRenderer, text), matrix, x, y, layerType, backgroundColor);
        TextRenderer.TextLayerType forcedLayer = layerType;
        return textRenderer.draw(
                text,
                x,
                y,
                phaze$resolvedTextColor(color),
                module.isEnabled() ? module.nametagTextShadow.isValue() : shadow,
                matrix,
                vertexConsumers,
                forcedLayer,
                resolvedBackground,
                light
        );
    }

    @ModifyVariable(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Text phaze$modifyNametagText(Text original) {
        NametagHud module = NametagHud.getInstance();
        if (!module.isEnabled() || original == null) return original;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return original;

        String selfName = client.player.getName().getString();
        int textColor = 0xFFFFFF;
        if (module.replaceOwnNameColor.isValue() && selfName.equals(original.getString())) {
            textColor = 0x55FFFF;
        }

        MutableText styled = original.copy();
        styled.setStyle(styled.getStyle().withColor(textColor));
        return styled;
    }

    private static int phaze$resolvedTextColor(int originalColor) {
        Integer cached = TEXT_COLOR_CACHE.get(originalColor);
        if (cached != null) {
            return cached;
        }
        // Preserve original alpha (vanilla distance-based opacity fade)
        int resolved = originalColor;
        TEXT_COLOR_CACHE.put(originalColor, resolved);
        return resolved;
    }

    private static int phaze$resolvedBackgroundColor(int vanillaBackgroundColor) {
        NametagHud module = NametagHud.getInstance();
        if (!module.isEnabled()) {
            return vanillaBackgroundColor;
        }
        if (!module.background.isValue() || vanillaBackgroundColor == 0) {
            return 0;
        }

        long settingsSignature = phaze$backgroundSettingsSignature(module);
        if (settingsSignature == lastBackgroundSettingsSignature && vanillaBackgroundColor == lastBackgroundInputColor) {
            return lastBackgroundResolvedColor;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        // NametagHud.getResolvedBackgroundColor already encodes the
        // nametag-specific alpha rules: 30% pinned for vanilla preset
        // and a linear slider->alpha mapping capped at 30% for custom
        // presets. The mixin just consumes that color directly so the
        // alpha curve stays smooth all the way down to slider=0 (no more
        // "snap to invisible" around the 30 mark that the old multiplier
        // path produced).
        int out = client != null ? module.getResolvedBackgroundColor(client) : vanillaBackgroundColor;

        lastBackgroundSettingsSignature = settingsSignature;
        lastBackgroundInputColor = vanillaBackgroundColor;
        lastBackgroundResolvedColor = out;
        return out;
    }

    private static int phaze$drawBlurBackgroundIfNeeded(float textWidth, Matrix4f matrix, float x, float y, TextRenderer.TextLayerType layerType, int vanillaBackgroundColor) {
        NametagHud module = NametagHud.getInstance();
        if (!module.isEnabled() || !module.background.isValue() || vanillaBackgroundColor == 0) {
            return phaze$resolvedBackgroundColor(vanillaBackgroundColor);
        }

        float blurRadius = module.backgroundBlurRadius.getValue();
        int background = phaze$resolvedBackgroundColor(vanillaBackgroundColor);
        if (blurRadius <= 0.0f || phaze$backgroundDrawnThisLabel) {
            return background;
        }

        // Calculate distance from camera for LOD system
        MinecraftClient client = MinecraftClient.getInstance();
        float distance = 0.0f;
        if (client != null && client.player != null && client.cameraEntity != null) {
            distance = (float) client.cameraEntity.getPos().distanceTo(client.player.getPos());
        }

        // Check player speed for motion blur reduction
        float playerSpeed = Blur.INSTANCE.getPlayerSpeed(client);

        float left = x - 1.0f;
        float top = y - 1.0f;
        float rightExtend = -1.0f;
        float width = textWidth + 2.0f + rightExtend;
        float height = 10.0f;
        
        // Early Exit: Skip blur for very small nametags (< 50 pixels area)
        if (width * height < 50.0f) {
            drawSolidRect3D(matrix, left, top, width, height, background);
            phaze$backgroundDrawnThisLabel = true;
            return vanillaBackgroundColor;
        }
        
        float quality = MathHelper.clamp(0.35f + blurRadius * 0.10f, 0.35f, 4.2f);

        // Enhanced LOD: Reduce both quality AND blur radius based on distance
        if (distance > 50.0f) {
            // Very far (> 50 blocks): no blur at all
            drawSolidRect3D(matrix, left, top, width, height, background);
            phaze$backgroundDrawnThisLabel = true;
            return vanillaBackgroundColor;
        } else if (distance > 30.0f) {
            // Far (30-50 blocks): minimal blur
            quality *= 0.25f;
            blurRadius *= 0.3f;
        } else if (distance > 20.0f) {
            // Medium-far (20-30 blocks): reduced blur
            quality *= 0.4f;
            blurRadius *= 0.5f;
        } else if (distance > 10.0f) {
            // Medium (10-20 blocks): slightly reduced blur
            quality *= 0.7f;
            blurRadius *= 0.8f;
        }
        // Close (< 10 blocks): full quality - no changes

        // Speed-based blur reduction: reduce blur at high speeds (> 20 blocks/sec)
        if (playerSpeed > 20.0f) {
            float speedFactor = MathHelper.clamp(20.0f / playerSpeed, 0.3f, 1.0f);
            blurRadius *= speedFactor;
            quality *= speedFactor;
        }

        // Render blur immediately
        Blur.INSTANCE.renderWorldRect(matrix, left, top, width, height, quality, 0xFFFFFFFF);
        drawSolidRect3D(matrix, left, top, width, height, background);
        phaze$backgroundDrawnThisLabel = true;
        // Keep vanilla backdrop in both passes and draw solid background on top.
        return vanillaBackgroundColor;
    }

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

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.gl.ShaderProgramKeys.POSITION_COLOR);
        net.minecraft.client.render.BufferBuilder buffer = net.minecraft.client.render.Tessellator.getInstance()
                .begin(net.minecraft.client.render.VertexFormat.DrawMode.QUADS, net.minecraft.client.render.VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, x, y, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, x, y + height, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, x + width, y + height, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, x + width, y, 0.0f).color(r, g, b, a);
        net.minecraft.client.render.BufferRenderer.drawWithGlobalProgram(buffer.end());
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }
}
