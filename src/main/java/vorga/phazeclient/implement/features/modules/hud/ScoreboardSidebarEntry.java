package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.text.Text;

/** Scoreboard row data kept outside the mixin package. */
public record ScoreboardSidebarEntry(Text name, Text score, boolean hasScore, int scoreWidth) {
}
