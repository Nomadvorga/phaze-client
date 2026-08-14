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
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
import vorga.phazeclient.base.util.PhazeBadgeUtil;
import vorga.phazeclient.implement.features.modules.hud.NametagHud;
import vorga.phazeclient.implement.features.modules.other.TotemTracker;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Consolidated {@link EntityRenderer} mixin merging the {@link NametagHud}
 * customisation (visibility / colour / blurred backdrop / first-person
 * tag) and the {@link TotemTracker} per-player loss suffix.
 *
 * <h3>1.21.11 port note - the label pipeline moved</h3>
 * In 1.21.4 {@code renderLabelIfPresent(state, Text, MatrixStack,
 * VertexConsumerProvider, int)} drew the nametag itself via two
 * {@code TextRenderer.draw(...)} calls, so Phaze could redirect those
 * calls and inject the blur backdrop, the Phaze badge quad and the
 * colour/shadow overrides right there.
 *
 * <p>1.21.11 replaced that with a deferred command queue. Verified
 * against the merged jar:
 * <pre>
 * EntityRenderer.renderLabelIfPresent(S, MatrixStack, OrderedRenderCommandQueue, CameraRenderState)
 *     -&gt; queue.submitLabel(matrices, state.nameLabelPos, 0, state.displayName,
 *                          !state.sneaking, state.light, state.squaredDistanceToCamera, cameraState)
 * </pre>
 * The method no longer takes a {@link Text} argument, no longer takes a
 * light int, and issues <b>no draw at all</b>. The billboard matrix, the
 * centering offset, the background colour and both
 * {@code TextRenderer.draw} calls now live in
 * {@code net.minecraft.client.render.command.LabelCommandRenderer}
 * ({@code Commands.add} builds the {@code LabelCommand}, {@code render}
 * draws the SEE_THROUGH then the NORMAL list). {@code TextRenderer.draw}
 * also returns {@code void} now, so the old {@code @Redirect}s could not
 * have been kept even if the call site had survived.
 *
 * <p>What still works from here: label visibility (F1 / distance /
 * first-person self tag) and every text transform, rebound from
 * {@code @ModifyVariable(argsOnly)} onto {@code @ModifyArg} of
 * {@code submitLabel}'s Text parameter.
 *
 * <p>What does not: everything under "DEFERRED DECORATION" at the bottom
 * of this file. See the TODO block there.
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

    /**
     * 1.21.11: the descriptor lost its {@link Text} and {@code light}
     * arguments and gained the command queue plus the camera state - the
     * label is now submitted, not drawn. Everything this method does is
     * still reachable because it only reads {@link EntityRenderState}.
     */
    @Inject(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void phaze$controlNametagVisibility(EntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci) {
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

    /**
     * Replaces the three 1.21.4 {@code @ModifyVariable(argsOnly = true,
     * ordinal = 0)} injectors. {@code renderLabelIfPresent} has no Text
     * parameter any more, so the transforms move onto argument 3 of the
     * {@code submitLabel} call (0 MatrixStack, 1 Vec3d, 2 int yOffset,
     * <b>3 Text</b>, 4 boolean seeThrough, 5 int light, 6 double distSq,
     * 7 CameraRenderState - verified from the invokeinterface descriptor).
     *
     * <p>The three transforms are chained here explicitly rather than as
     * three stacked {@code @ModifyArg}s so the ordering stays visible and
     * guaranteed: the self-name recolor must run before badge padding is
     * prepended, otherwise its {@code selfName.equals(...)} test fails.
     * (Totem suffix commutes with both - it appends literal gray/red
     * text while the other two touch the head of the string.)
     */
    @ModifyArg(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitLabel(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/math/Vec3d;ILnet/minecraft/text/Text;ZIDLnet/minecraft/client/render/state/CameraRenderState;)V"
            ),
            index = 3
    )
    private Text phaze$decorateNametagText(Text original) {
        Text text = phaze$modifyNametagText(original);
        text = phaze$appendTotemSuffix(text);
        return phaze$prependBadgePadding(text);
    }

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
     * TotemTracker per-player loss suffix.
     */
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

    // ------------------------------------------------------------------
    // DEFERRED DECORATION - currently unreachable, kept verbatim.
    //
    // TODO(1.21.11): re-attach the nametag backdrop / badge / colour /
    // shadow overrides. They cannot be driven from EntityRenderer any
    // more: renderLabelIfPresent only calls
    // OrderedRenderCommandQueue.submitLabel and issues no draw.
    //
    // The new hook points, both in
    // net.minecraft.client.render.command.LabelCommandRenderer:
    //
    //   Commands.add(MatrixStack, Vec3d, int, Text, boolean, int, double,
    //                CameraRenderState)
    //       builds the billboard matrix (translate to nameLabelPos + 0.5y,
    //       multiply by cameraState.orientation, scale 0.025/-0.025/0.025),
    //       computes x = -textRenderer.getWidth(text) / 2, and the
    //       background alpha from GameOptions.getTextBackgroundOpacity.
    //       -> the place to override the resolved background colour
    //          (phaze$resolvedBackgroundColor) and to record the badge flag.
    //
    //   render(BatchingRenderCommandQueue, VertexConsumerProvider.Immediate,
    //          TextRenderer)
    //       iterates seethroughLabels (SEE_THROUGH) then normalLabels
    //       (NORMAL) and calls TextRenderer.draw for each.
    //       -> the place the two old @Redirects belong; it is also the only
    //          point where an Immediate is in scope, which the blur input
    //          flush (phaze$flushCurrentNametagLayer) needs.
    //
    // That requires a NEW mixin class + a phaze.mixins.json entry, which is
    // out of scope for this file. Until then: nametag blur backdrop, the
    // world Phaze badge, the nametag text-colour cache and the
    // nametagTextShadow option do nothing. The text transforms above and
    // the visibility control still work.
    //
    // Also note TextRenderer.draw now returns void.
    // OutlineVertexConsumerProviderAccessor is fixed (1.21.11 renamed the
    // field "parent" -> "plainDrawer"), so phaze$flushCurrentNametagLayer
    // itself is sound; what is still missing is the new mixin class
    // described above, which is what would hand it an Immediate to flush.
    // ------------------------------------------------------------------

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
        PhazeBadgeUtil.drawWorldBadge(matrix, vertexConsumers, layerType, x - 2.0F, y - 1.0F, 10.0F, light, 0xFFFFFFFF);
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

    /**
     * 1.21.11: {@code RenderSystem.setShader} /
     * {@code BufferRenderer.drawWithGlobalProgram} and the imperative
     * blend/depth calls are gone - blend, depth test and depth write are
     * baked into the pipeline behind {@link PhazeDrawLayers#POSITION_COLOR}
     * and travel with the draw.
     *
     * <p>Behaviour difference: the 1.21.4 version called
     * {@code disableDepthTest()}, the shared layer uses LEQUAL. For this
     * backdrop that is arguably more correct (it stops the rect punching
     * through walls) but it is a change; if the through-wall look must
     * come back, add a NO_DEPTH_TEST variant to {@code PhazeDrawLayers}
     * rather than reintroducing imperative state.
     */
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
