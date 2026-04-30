package vorga.phazeclient.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
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

@Mixin(EntityRenderer.class)
public abstract class EntityRendererNametagMixin {
    private static boolean phaze$backgroundDrawnThisLabel = false;

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
        if (module.nametagOpacity.getValue() <= 0.0f) {
            ci.cancel();
        }
        phaze$backgroundDrawnThisLabel = false;
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

    @Redirect(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I")
    )
    private int phaze$drawNametagWithSettings(TextRenderer textRenderer, Text text, float x, float y, int color, boolean shadow, Matrix4f matrix, VertexConsumerProvider vertexConsumers, TextRenderer.TextLayerType layerType, int backgroundColor, int light) {
        NametagHud module = NametagHud.getInstance();
        int resolvedBackground = phaze$drawBlurBackgroundIfNeeded(textRenderer.getWidth(text), matrix, x, y, backgroundColor);
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
        int resolvedBackground = phaze$drawBlurBackgroundIfNeeded(textRenderer.getWidth(text), matrix, x, y, backgroundColor);
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

    private static int phaze$resolvedTextColor(int originalColor) {
        NametagHud module = NametagHud.getInstance();
        if (!module.isEnabled()) {
            return originalColor;
        }
        float opacity = MathHelper.clamp(module.nametagOpacity.getValue() / 100.0f, 0.0f, 1.0f);
        int originalAlpha = (originalColor >>> 24) & 0xFF;
        if (originalAlpha == 0) {
            originalAlpha = 255;
        }
        int alpha = MathHelper.clamp(Math.round(originalAlpha * opacity), 0, 255);
        return (alpha << 24) | (originalColor & 0x00FFFFFF);
    }

    private static int phaze$resolvedBackgroundColor(int vanillaBackgroundColor) {
        NametagHud module = NametagHud.getInstance();
        if (!module.isEnabled()) {
            return vanillaBackgroundColor;
        }
        if (!module.background.isValue() || vanillaBackgroundColor == 0) {
            return 0;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        int resolved = client != null ? module.getResolvedBackgroundColor(client) : vanillaBackgroundColor;
        int vanillaAlpha = (vanillaBackgroundColor >>> 24) & 0xFF;
        int resolvedAlpha = (resolved >>> 24) & 0xFF;
        int baseAlpha = resolvedAlpha > 0 ? Math.min(resolvedAlpha, vanillaAlpha) : vanillaAlpha;
        float opacity = MathHelper.clamp(module.nametagOpacity.getValue() / 100.0f, 0.0f, 1.0f);
        int alpha = MathHelper.clamp(Math.round(baseAlpha * opacity), 0, 255);
        return (alpha << 24) | (resolved & 0x00FFFFFF);
    }

    private static int phaze$drawBlurBackgroundIfNeeded(float textWidth, Matrix4f matrix, float x, float y, int vanillaBackgroundColor) {
        NametagHud module = NametagHud.getInstance();
        if (!module.isEnabled() || !module.background.isValue() || vanillaBackgroundColor == 0) {
            return phaze$resolvedBackgroundColor(vanillaBackgroundColor);
        }

        float blurRadius = module.backgroundBlurRadius.getValue();
        int background = phaze$resolvedBackgroundColor(vanillaBackgroundColor);
        if (blurRadius <= 0.0f || phaze$backgroundDrawnThisLabel) {
            return background;
        }

        float left = x - 1.0f;
        float top = y - 1.0f;
        float width = textWidth + 2.0f;
        float height = 10.0f;
        float quality = MathHelper.clamp(0.35f + blurRadius * 0.10f, 0.35f, 4.2f);

        Blur.INSTANCE.renderWorldRect(matrix, left, top, width, height, quality, 0xFFFFFFFF);
        drawSolidRect3D(matrix, left, top, width, height, background);
        phaze$backgroundDrawnThisLabel = true;
        return 0;
    }

    private static void drawSolidRect3D(Matrix4f matrix, float x, float y, float width, float height, int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255.0f;
        if (a <= 0.0f || width <= 0.0f || height <= 0.0f) return;
        float r = ((argb >>> 16) & 0xFF) / 255.0f;
        float g = ((argb >>> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, x, y, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, x, y + height, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, x + width, y + height, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, x + width, y, 0.0f).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
