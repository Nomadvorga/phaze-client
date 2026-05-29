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
import net.minecraft.util.Formatting;
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
import vorga.phazeclient.implement.features.modules.other.TotemTracker;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Consolidated {@link EntityRenderer} mixin merging the {@link NametagHud}
 * customisation (visibility / colour / blurred backdrop / first-person
 * tag) and the {@link TotemTracker} per-player loss suffix.
 *
 * <h3>Independence</h3>
 * Both features touch {@code renderLabelIfPresent}'s {@link Text} arg
 * via {@code @ModifyVariable(argsOnly=true, ordinal=0)} and Mixin
 * stacks them in declaration order across the file. Order between
 * "append totem suffix" and "recolor own name" is irrelevant - the
 * suffix is gray/red literal, the recolor edits style on the original
 * substring; they commute.
 *
 * <h3>Caches</h3>
 * Width / colour / settings-signature LinkedHashMap caches are
 * static, bounded ({@link #NAMETAG_CACHE_MAX} entries), and shared
 * across both features because cache pressure scales with the number
 * of distinct nametag strings on screen, not the number of features
 * touching them.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
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
        return textRenderer.draw(
                text,
                x,
                y,
                phaze$resolvedTextColor(color),
                module.isEnabled() ? module.nametagTextShadow.isValue() : shadow,
                matrix,
                vertexConsumers,
                layerType,
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
        return textRenderer.draw(
                text,
                x,
                y,
                phaze$resolvedTextColor(color),
                module.isEnabled() ? module.nametagTextShadow.isValue() : shadow,
                matrix,
                vertexConsumers,
                layerType,
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

    /**
     * TotemTracker per-player loss suffix. Independent
     * {@code @ModifyVariable} on the same arg as
     * {@link #phaze$modifyNametagText}; Mixin chains them so both
     * contributions land on the rendered label. Order between the
     * two doesn't matter: this one appends literal gray/red text,
     * the other rewrites style on the original substring.
     */
    @ModifyVariable(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Text phaze$appendTotemSuffix(Text original) {
        TotemTracker tracker = TotemTracker.getInstance();
        if (tracker == null || !tracker.isEnabled() || !tracker.nametagSuffix.isValue()) {
            return original;
        }
        if (original == null) {
            return original;
        }
        String displayed = original.getString();
        int count = tracker.getLossCountFromText(displayed);
        if (count <= 0) {
            return original;
        }
        MutableText decorated = original.copy();
        decorated.append(Text.literal(" | ").formatted(Formatting.GRAY));
        decorated.append(Text.literal("-" + count).formatted(Formatting.RED));
        return decorated;
    }

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

    private static int phaze$drawBlurBackgroundIfNeeded(float textWidth, Matrix4f matrix, float x, float y, TextRenderer.TextLayerType layerType, int vanillaBackgroundColor) {
        NametagHud module = NametagHud.getInstance();
        if (!module.isEnabled() || !module.background.isValue()) {
            return phaze$resolvedBackgroundColor(vanillaBackgroundColor);
        }
        // Draw nametag blur backdrop only on the primary text pass.
        // Rendering backdrop on auxiliary passes can cause frame-to-frame
        // intensity oscillation (visible flicker) due to multi-pass blend.
        if (layerType != TextRenderer.TextLayerType.NORMAL) {
            return 0;
        }

        float blurRadius = module.backgroundBlurRadius.getValue();
        int background = phaze$resolvedBackgroundColor(vanillaBackgroundColor);
        if (blurRadius <= 0.0f || phaze$backgroundDrawnThisLabel) {
            return background;
        }

        float left = x - 1.0f;
        float top = y - 1.0f;
        float rightExtend = -1.0f;
        float width = textWidth + 2.0f + rightExtend;
        float height = 10.0f;
        // Stabilize backdrop against sub-pixel jitter from entity label
        // interpolation: snap rect geometry to half-pixel grid so tiny
        // float drift between frames doesn't show as blur flicker.
        left = phaze$snapHalfPixel(left);
        top = phaze$snapHalfPixel(top);
        width = phaze$snapHalfPixel(Math.max(0.0f, width));
        height = phaze$snapHalfPixel(Math.max(0.0f, height));

        if (width * height < 50.0f) {
            drawSolidRect3D(matrix, left, top, width, height, background);
            phaze$backgroundDrawnThisLabel = true;
            return 0;
        }

        float quality = MathHelper.clamp(0.35f + blurRadius * 0.10f, 0.35f, 4.2f);
        MinecraftClient client = MinecraftClient.getInstance();
        float distance = 0.0f;
        if (client != null && client.player != null && client.cameraEntity != null) {
            distance = (float) client.cameraEntity.getPos().distanceTo(client.player.getPos());
        }
        float playerSpeed = Blur.INSTANCE.getPlayerSpeed(client);

        if (distance > 50.0f) {
            drawSolidRect3D(matrix, left, top, width, height, background);
            phaze$backgroundDrawnThisLabel = true;
            return 0;
        } else if (distance > 30.0f) {
            quality *= 0.25f;
            blurRadius *= 0.3f;
        } else if (distance > 20.0f) {
            quality *= 0.4f;
            blurRadius *= 0.5f;
        } else if (distance > 10.0f) {
            quality *= 0.7f;
            blurRadius *= 0.8f;
        }

        if (playerSpeed > 20.0f) {
            float speedFactor = MathHelper.clamp(20.0f / playerSpeed, 0.3f, 1.0f);
            blurRadius *= speedFactor;
            quality *= speedFactor;
        }

        Blur.INSTANCE.renderWorldRect(matrix, left, top, width, height, quality, 0xFFFFFFFF);
        drawSolidRect3D(matrix, left, top, width, height, background);
        phaze$backgroundDrawnThisLabel = true;
        return 0;
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

    private static float phaze$snapHalfPixel(float value) {
        return Math.round(value * 2.0f) * 0.5f;
    }
}
