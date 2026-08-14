package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.LabelCommandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import vorga.phazeclient.implement.features.modules.hud.NametagHud;

/**
 * Nametag text shadow and background, for the 1.21.11 label pipeline.
 *
 * <h3>1.21.11 port</h3>
 *
 * <p>Through 1.21.4 both settings were applied from {@code EntityRendererMixin},
 * where {@code renderLabelIfPresent} drew the label itself. 1.21.11 made entity
 * labels deferred: {@code renderLabelIfPresent} only calls
 * {@code OrderedRenderCommandQueue.submitLabel} and issues no draw at all, so
 * those hooks became unreachable - which is why the shadow toggle did nothing
 * and the background could not be switched off.
 *
 * <p>The draw moved here. {@link LabelCommandRenderer#render} walks the
 * collected see-through and normal labels and calls
 * {@code TextRenderer.draw(Text, x, y, color, shadow, Matrix4f, provider,
 * layerType, backgroundColor, light)} once per label. Both values Phaze cares
 * about are plain arguments of that call, so two {@code @ModifyArg}s replace
 * what used to be a much larger intercept - and because both label lists funnel
 * through the same call site, one hook each covers see-through and normal
 * labels alike.
 */
@Mixin(LabelCommandRenderer.class)
public class LabelCommandRendererMixin {

    @Unique
    private static final String PHAZE_DRAW_TARGET =
            "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V";

    /** {@code shadow} - argument 4. */
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

    /**
     * {@code backgroundColor} - argument 8.
     *
     * <p>Zero is how vanilla itself expresses "no background": the label
     * background quad is skipped when the colour's alpha is zero, so returning
     * 0 turns it off rather than drawing a transparent box over the world.
     */
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
}
