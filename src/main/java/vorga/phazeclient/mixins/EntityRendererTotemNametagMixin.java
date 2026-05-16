package vorga.phazeclient.mixins;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vorga.phazeclient.implement.features.modules.other.TotemTracker;

/**
 * Appends a {@code " | -N"} suffix to the nametag {@link Text} of
 * any player the {@link TotemTracker} has recorded a totem pop for.
 *
 * <h3>Why a separate mixin from {@code EntityRendererNametagMixin}</h3>
 * The existing nametag mixin owns the colour-override and visibility
 * logic for the {@code NametagHud} module; mixing the totem suffix
 * into that file would entangle two unrelated features in a single
 * {@code @ModifyVariable} chain. Mixin allows multiple
 * {@code @ModifyVariable} hooks on the same target argument - they
 * run in declaration order across all mixins applied to the class -
 * so a separate file keeps each module's contribution easy to read,
 * review, and toggle independently. The execution order between
 * this mixin and {@code EntityRendererNametagMixin} is unspecified
 * by Mixin but irrelevant: both write to the same {@code Text}
 * argument and one does colour-only edits while the other does
 * append-only edits, so they commute.
 *
 * <h3>Why ARG, not the call sites</h3>
 * Hooking {@code TextRenderer.draw} via {@code @Redirect} (the way
 * the colour-override mixin does) would force us to re-emit the
 * vanilla draw call, which is expensive boilerplate when all we
 * want to do is rewrite the text up-front. {@code @ModifyVariable}
 * with {@code argsOnly = true, ordinal = 0} replaces the
 * {@link Text} argument before the method body even starts, which
 * means every downstream consumer (TextRenderer.draw, the optional
 * vanilla scoreboard backdrop, etc.) sees the suffixed text
 * automatically.
 *
 * <h3>Substring matching</h3>
 * We don't have access to a UUID or {@code GameProfile} on this
 * code path - {@code EntityRenderState} doesn't expose one and
 * {@code renderLabelIfPresent} only sees the decorated {@link Text}.
 * The tracker therefore matches its keys (raw player names captured
 * at totem-pop time) against the rendered text via
 * {@link TotemTracker#getLossCountFromText(String)}, which handles
 * the common server case of rank-prefixed nametags ({@code
 * "[VIP] Steve"} still matches the {@code Steve} key).
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererTotemNametagMixin {

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
        // Keep the original style verbatim and append the gray
        // separator + red counter as siblings. The separator gets
        // its own gray colour so it visually disappears into the
        // metadata band even when the original name uses bright
        // rank colours; the counter stays red because the user
        // requested "-N" as a loss indicator and red is the
        // universal Minecraft colour for damage / loss.
        MutableText decorated = original.copy();
        decorated.append(Text.literal(" | ").formatted(Formatting.GRAY));
        decorated.append(Text.literal("-" + count).formatted(Formatting.RED));
        return decorated;
    }
}
