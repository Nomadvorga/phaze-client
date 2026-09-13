package vorga.phazeclient.base.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import vorga.phazeclient.api.system.nametag.NametagBlurStorage;
import vorga.phazeclient.implement.features.modules.hud.NametagHud;
import vorga.phazeclient.implement.features.modules.other.TotemTracker;

public final class NametagTextDecorator {

    private NametagTextDecorator() {
    }

    public static Text decorate(Text original, float labelDistance) {
        if (original == null) {
            return null;
        }
        Text text = recolorSelfName(original);
        text = appendTotemSuffix(text);
        Text decorated = prependBadgePadding(text);

        if (decorated != null) {
            NametagBlurStorage.recordLabelDistance(decorated, labelDistance);
        }
        return decorated;
    }

    private static Text recolorSelfName(Text original) {
        NametagHud module = NametagHud.getInstance();
        if (!module.isEnabled()) return original;

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

    private static Text appendTotemSuffix(Text original) {
        TotemTracker tracker = TotemTracker.getInstance();
        if (tracker == null || !tracker.isEnabled() || !tracker.nametagSuffix.isValue()) {
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

    private static Text prependBadgePadding(Text original) {
        String identity = PhazeBadgeUtil.extractNametagIdentity(original.getString());
        if (identity == null || !PhazeBadgeUtil.isPhazeUser(identity)) {
            return original;
        }
        return PhazeBadgeUtil.withBadgePadding(original);
    }
}
