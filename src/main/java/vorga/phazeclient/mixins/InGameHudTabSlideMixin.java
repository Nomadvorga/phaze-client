package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Drives the tab-list slide animation from a single per-frame hook on
 * {@link InGameHud#render}. Vanilla skips calling {@code playerListHud.render}
 * when the playerList key isn't held, which means the slide-out animation
 * would die instantly when the user releases tab. We watch for that case
 * here, keep ticking the animation, and force one extra render per frame
 * until the slide-out has fully settled.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudTabSlideMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    @Final
    private PlayerListHud playerListHud;

    @Inject(method = "render", at = @At("TAIL"))
    private void phaze$tabSlideTick(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Animations module = Animations.getInstance();
        if (module == null || !module.isTabSlideEnabled()) {
            return;
        }
        if (client == null || client.options == null) {
            return;
        }

        boolean keyPressed = client.options.playerListKey.isPressed();
        // Tick once per frame regardless of which branch we end up in - this
        // is the single source of truth for the offset that PlayerListHudMixin
        // reads when it pushes the matrix translate.
        module.tickTabSlide(keyPressed);

        // Vanilla already rendered the tab list in this frame; nothing more
        // to do on the open path.
        if (keyPressed) {
            return;
        }

        // Key released and slide-out has fully settled - vanilla's
        // playerListHud.setVisible(false) call earlier this frame is fine.
        if (!module.isTabSlideRendering(false)) {
            return;
        }

        // Closing animation still progressing: vanilla turned visibility off
        // and skipped rendering. Re-enable visibility (PlayerListHud.render
        // bails out internally when !visible) and call render manually so
        // PlayerListHudMixin's matrix translate runs and the slide-out
        // actually shows on screen.
        playerListHud.setVisible(true);

        Scoreboard scoreboard = client.world == null ? null : client.world.getScoreboard();
        ScoreboardObjective objective = scoreboard == null ? null
                : scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST);
        playerListHud.render(context, context.getScaledWindowWidth(), scoreboard, objective);
    }
}
