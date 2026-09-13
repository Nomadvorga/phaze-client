package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.text.Text;

public record ScoreboardSidebarEntry(Text name, Text score, boolean hasScore, int scoreWidth) {
}
