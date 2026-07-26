package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
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
import vorga.phazeclient.base.util.PhazeBadgeUtil;
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

        // Never render the camera entity's label in first person. Its
        // billboard sits on the near plane, so even a tiny 10px backdrop is
        // projected into a giant rounded-looking shape over most of the
        // screen. The option controls the useful case only: the local label
        // while the player model is visible in third person.
        boolean firstPerson = client.options.getPerspective().isFirstPerson();
        cir.setReturnValue(!firstPerson && module.thirdPersonNametag.isValue());
    }

    @Inject(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void phaze$controlNametagVisibility(EntityRenderState state, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
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

    @Redirect(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I")
    )
    private int phaze$drawNametagWithSettings(TextRenderer textRenderer, Text text, float x, float y, int color, boolean shadow, Matrix4f matrix, VertexConsumerProvider vertexConsumers, TextRenderer.TextLayerType layerType, int backgroundColor, int light) {
        NametagHud module = NametagHud.getInstance();
        int resolvedBackground = phaze$drawBlurBackgroundIfNeeded(
                phaze$getCachedTextWidth(textRenderer, text),
                matrix,
                x,
                y,
                vertexConsumers,
                layerType,
                backgroundColor
        );
        phaze$drawNametagBadgeIfNeeded(matrix, vertexConsumers, x, y, layerType, light);
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
        int resolvedBackground = phaze$drawBlurBackgroundIfNeeded(
                phaze$getCachedTextWidth(textRenderer, text),
                matrix,
                x,
                y,
                vertexConsumers,
                layerType,
                backgroundColor
        );
        phaze$drawNametagBadgeIfNeeded(matrix, vertexConsumers, x, y, layerType, light);
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

    @ModifyVariable(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Text phaze$prependBadgePadding(Text original) {
        if (original == null) {
            return null;
        }
        String identity = PhazeBadgeUtil.extractNametagIdentity(original.getString());
        if (identity == null || !PhazeBadgeUtil.isPhazeUser(identity)) {
            return original;
        }
        phaze$drawBadgeThisLabel = true;
        return PhazeBadgeUtil.withBadgePadding(original);
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
        PhazeBadgeUtil.drawWorldBadge(matrix, vertexConsumers, layerType, x - 2.0F, y - 1.0F, 10.0F, light, 0xFFFFFFFF);
    }

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

        // Draw the blur itself only on the primary text pass. The auxiliary
        // pass contributes just the cheap selected-color fallback.
        if (layerType != TextRenderer.TextLayerType.NORMAL) {
            // Vanilla already submits the through-wall background in this
            // SEE_THROUGH text pass. Reuse that rectangle as our selected-color
            // fallback instead of issuing a separate solid draw for every
            // normal (non-sneaking) nametag.
            if (phaze$shouldDrawDepthAwareBlur(textWidth, blurRadius)) {
                // Flush the current entity before its SEE_THROUGH text is
                // queued. This puts the player model into the depth buffer
                // while keeping the label itself out of its blur snapshot.
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
        // Stabilize backdrop against sub-pixel jitter from entity label
        // interpolation: snap rect geometry to half-pixel grid so tiny
        // float drift between frames doesn't show as blur flicker.
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

        // Sneaking labels have no SEE_THROUGH pass, so prepare their depth and
        // clean blur input here. Normal labels have already done this before
        // queuing their auxiliary text pass.
        if (phaze$fallbackQueuedThisLabel) {
            // Commit the fallback + dim through-wall text before the immediate
            // blur draw. TextRenderer would flush this layer on the following
            // NORMAL request anyway; only the timing moves forward.
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
            // The current dynamic entity layer would be flushed immediately
            // afterward when TextRenderer requests its own layer anyway.
            // Move only that flush forward instead of draining every fixed
            // entity buffer for every nametag.
            immediate.drawCurrentLayer();
        }
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
                .begin(com.mojang.blaze3d.vertex.VertexFormat.DrawMode.QUADS, net.minecraft.client.render.VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, x, y, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, x, y + height, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, x + width, y + height, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, x + width, y, 0.0f).color(r, g, b, a);
        net.minecraft.client.render.BufferRenderer.drawWithGlobalProgram(buffer.end());
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    private static float phaze$snapHalfPixel(float value) {
        return Math.round(value * 2.0f) * 0.5f;
    }
}
