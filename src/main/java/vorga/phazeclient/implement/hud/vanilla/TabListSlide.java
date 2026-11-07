package vorga.phazeclient.implement.hud.vanilla;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import vorga.phazeclient.api.system.hud.ExordiumAnimationBridge;
import vorga.phazeclient.implement.features.modules.other.Animations;

/** Drives the tab-list slide animation and renders the closing slide after the key is released. */
public final class TabListSlide {
    private static boolean tabWasOpenedThisCycle = false;

    private TabListSlide() {}

    public static void tick(MinecraftClient client) {
        Animations module = Animations.getInstance();
        if (module == null || !module.isTabSlideEnabled()) {
            tabWasOpenedThisCycle = false;
            return;
        }
        if (client == null || client.options == null) {
            tabWasOpenedThisCycle = false;
            return;
        }
        if (client.options.hudHidden) {
            module.snapTabClosed();
            tabWasOpenedThisCycle = false;
            return;
        }

        boolean keyPressed = client.options.playerListKey.isPressed();
        module.tickTabSlide(keyPressed);

        if (keyPressed && vanillaTabListWouldRender(client)) {
            tabWasOpenedThisCycle = true;
        }
    }

    public static void renderClosing(MinecraftClient client, PlayerListHud playerListHud, DrawContext context) {
        Animations module = Animations.getInstance();
        if (module == null || !module.isTabSlideEnabled()) {
            tabWasOpenedThisCycle = false;
            return;
        }
        if (client == null || client.options == null || client.options.hudHidden) {
            tabWasOpenedThisCycle = false;
            return;
        }

        if (client.options.playerListKey.isPressed()) {
            return;
        }
        if (!module.isTabSlideRendering(false)) {
            tabWasOpenedThisCycle = false;
            return;
        }
        if (!tabWasOpenedThisCycle) return;

        if (ExordiumAnimationBridge.renderClosingPlayerListFromCache()) {
            return;
        }

        // Exordium is optional. When it is absent or has not completed its
        // first player-list capture yet, preserve the original full-render
        // close path as a correctness fallback.
        playerListHud.setVisible(true);
        Scoreboard scoreboard = client.world == null ? null : client.world.getScoreboard();
        ScoreboardObjective objective = scoreboard == null ? null
                : scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST);
        playerListHud.render(context, context.getScaledWindowWidth(), scoreboard, objective);
        playerListHud.setVisible(false);
    }

    private static boolean vanillaTabListWouldRender(MinecraftClient client) {
        if (client.player == null) return false;
        if (!client.isInSingleplayer()) return true;
        if (client.player.networkHandler != null
                && client.player.networkHandler.getListedPlayerListEntries().size() > 1) return true;
        if (client.world != null) {
            Scoreboard scoreboard = client.world.getScoreboard();
            return scoreboard != null && scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST) != null;
        }
        return false;
    }
}
