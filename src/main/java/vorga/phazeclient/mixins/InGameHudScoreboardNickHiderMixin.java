package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import vorga.phazeclient.implement.features.modules.other.NickHider;

/**
 * Routes every {@link Text} the vanilla scoreboard sidebar pushes
 * through {@link DrawContext#drawText} via {@link NickHider#rewrite}, so
 * the local player's username gets swapped for the user-configured
 * replacement string before it lands on the screen.
 *
 * <h3>Why ModifyArg on drawText, not Team.decorateName</h3>
 * Vanilla builds the per-row entry inside a stream-map lambda
 * ({@code SidebarEntry} record, populated from a
 * {@link net.minecraft.scoreboard.Team#decorateName} call). The decorated
 * names are then drawn from the outer method via {@code drawContext.drawText}.
 * Hooking {@code Team.decorateName} would require targeting the synthetic
 * lambda method; the {@code drawText} call sites live in
 * {@code renderScoreboardSidebar} itself, which is a stable target the
 * mixin can find by its descriptor without lambda-name guesswork.
 *
 * <p>Three {@code drawText} call sites exist in this method:
 * <ol>
 *   <li>The objective title.</li>
 *   <li>The per-row decorated player name.</li>
 *   <li>The per-row score column.</li>
 * </ol>
 * We rewrite all three. {@link NickHider#rewrite} is a no-op fast path
 * when the module is disabled, the username doesn't appear in the text,
 * or the input is null; the cost on the title / score branches is a
 * single {@code String.contains} per draw, which neither path is hot
 * enough to feel.
 *
 * <h3>Style preservation</h3>
 * {@link NickHider#rewrite} walks the input via {@link Text#visit}
 * fragment-by-fragment and rebuilds with each fragment's own
 * {@link net.minecraft.text.Style}, so any team colour, bold flag, or
 * decoration the server applied to the player's name survives the swap
 * intact.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudScoreboardNickHiderMixin {

    @ModifyArg(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)I"
            )
    )
    private Text phaze$hideOwnNickInSidebar(Text original) {
        NickHider hider = NickHider.getInstance();
        if (hider == null || !hider.isEnabled()) {
            return original;
        }
        return hider.rewrite(original);
    }
}
