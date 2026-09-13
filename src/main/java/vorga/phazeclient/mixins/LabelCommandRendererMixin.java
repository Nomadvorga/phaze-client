package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.command.LabelCommandRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import vorga.phazeclient.implement.features.modules.hud.NametagHud;
import vorga.phazeclient.base.util.PhazeBadgeUtil;
import vorga.phazeclient.api.system.nametag.NametagBlurStorage;
import vorga.phazeclient.api.system.shape.implement.Blur;

@Mixin(LabelCommandRenderer.class)
public class LabelCommandRendererMixin {

    @Unique
    private static final String PHAZE_DRAW_TARGET =
            "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V";

    @ModifyArg(
            method = "render",
            at = @At(value = "INVOKE", target = PHAZE_DRAW_TARGET),
            index = 4,
            require = 0
    )
    private boolean phaze$nametagShadow(boolean original) {
        NametagHud module = NametagHud.getInstance();
        if (module == null || !module.isEnabled()) {
            return original;
        }
        return module.nametagTextShadow.isValue();
    }

    @ModifyArg(
            method = "render",
            at = @At(value = "INVOKE", target = PHAZE_DRAW_TARGET),
            index = 8,
            require = 0
    )
    private int phaze$nametagBackground(int original) {
        NametagHud module = NametagHud.getInstance();
        if (module == null || !module.isEnabled()) {
            return original;
        }
        if (!module.background.isValue()) {
            return 0;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null ? module.getResolvedBackgroundColor(client) : original;
    }

    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE", target = PHAZE_DRAW_TARGET),
            require = 0
    )
    private void phaze$drawUserBadge(
            TextRenderer renderer,
            Text text,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f matrix,
            VertexConsumerProvider consumers,
            TextRenderer.TextLayerType layerType,
            int backgroundColor,
            int light,
            Operation<Void> original
    ) {

        boolean blurDrawn = phaze$drawBlurBackdropIfNeeded(renderer, text, x, y, matrix, consumers, layerType);

        original.call(renderer, text, x, y, color, shadow, matrix, consumers, layerType,
                blurDrawn ? 0 : backgroundColor, light);

        if (text == null || !PhazeBadgeUtil.hasBadgePadding(text)) {
            return;
        }
        String identity = PhazeBadgeUtil.extractNametagIdentity(text.getString());
        if (identity == null || !PhazeBadgeUtil.isPhazeUser(identity)) {
            return;
        }

        boolean codeBadge = PhazeBadgeUtil.isCodeBadgeUser(identity);
        float size = codeBadge ? 8.0F : 10.0F;
        float inset = (10.0F - size) / 2.0F;
        PhazeBadgeUtil.drawWorldBadge(
                matrix,
                consumers,
                layerType,
                x - 2.0F + inset,
                y - 1.0F + inset,
                size,
                light,
                PhazeBadgeUtil.alphaWhite(color),
                codeBadge
        );
    }

    @Unique
    private static boolean phaze$drawBlurBackdropIfNeeded(
            TextRenderer renderer,
            Text text,
            float x,
            float y,
            Matrix4f matrix,
            VertexConsumerProvider consumers,
            TextRenderer.TextLayerType layerType
    ) {
        NametagHud module = NametagHud.getInstance();
        if (module == null || !module.isEnabled() || !module.background.isValue()) {
            return false;
        }
        float blurRadius = module.backgroundBlurRadius.getValue();
        if (blurRadius <= 0.0f || text == null || layerType != TextRenderer.TextLayerType.NORMAL) {
            return false;
        }

        float textWidth = renderer.getWidth(text);
        float left = phaze$snapHalfPixel(x - 1.0f);
        float top = phaze$snapHalfPixel(y - 1.0f);
        float width = phaze$snapHalfPixel(Math.max(0.0f, textWidth + 1.0f));
        float height = 10.0f;
        if (width * height < 50.0f) {
            return false;
        }

        float distance = NametagBlurStorage.consumeLabelDistance(text);
        float distanceFactor = Float.isNaN(distance) ? 1.0f : phaze$blurDistanceFactor(distance);
        if (distanceFactor <= 0.001f) {
            return false;
        }

        float quality = MathHelper.clamp(0.35f + blurRadius * 0.10f, 0.35f, 4.2f) * distanceFactor;
        MinecraftClient client = MinecraftClient.getInstance();
        float playerSpeed = Blur.INSTANCE.getPlayerSpeed(client);
        if (playerSpeed > 20.0f) {
            quality *= MathHelper.clamp(20.0f / playerSpeed, 0.3f, 1.0f);
        }

        Blur.INSTANCE.prepareWorldRectInput();

        if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.drawCurrentLayer();
        }

        Blur.INSTANCE.renderWorldRect(
                matrix,
                left,
                top,
                width,
                height,
                quality,
                client != null ? module.getResolvedBackgroundColor(client) : 0x4D000000,
                false,
                distanceFactor
        );
        return true;
    }

    @Unique
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

    @Unique
    private static float phaze$snapHalfPixel(float value) {
        return Math.round(value * 2.0f) * 0.5f;
    }
}
