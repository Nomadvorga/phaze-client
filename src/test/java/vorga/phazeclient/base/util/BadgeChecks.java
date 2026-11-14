package vorga.phazeclient.base.util;

import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Headless checks of where the badge slot lands in chat, nametag and TAB text. */
public final class BadgeChecks {
    private static int checks;

    public static void main(String[] args) {
        senderLine();
        chatPlacement();
        detection();
        System.out.println("Badge checks passed: " + checks);
    }

    private static void senderLine() {
        expect(PhazeBadgeUtil.senderLineStart("Nick: hi", "nick") == 0, "single line");
        expect(PhazeBadgeUtil.senderLineStart("\n\n[VIP] Nick: hi", "nick") == 2, "leading blank lines are skipped");
        expect(PhazeBadgeUtil.senderLineStart(" \n\u00A7r\n[VIP] Nick: hi", "nick") == 5, "blank and colour-only lines");
        expect(PhazeBadgeUtil.senderLineStart("=====\n[VIP] Nick: hi", "nick") == 6, "decorative line above the sender");
        expect(PhazeBadgeUtil.senderLineStart("\nHidden: hi", "nick") == 1, "unknown name falls back to first content");
        expect(PhazeBadgeUtil.senderLineStart("\n\n", null) == 0, "all blank");
    }

    private static void chatPlacement() {
        Text message = Text.literal("\n").append(Text.literal("[VIP] ").formatted(Formatting.RED))
                .append(Text.literal("Nick: hi"));
        Text badged = PhazeBadgeUtil.withChatBadge(message, "nick", false);
        expect(badged.getString().equals("\n\uE000[VIP] Nick: hi"), "slot opens the sender's line");
        List<Style> styles = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        badged.visit((style, part) -> {
            styles.add(style);
            parts.add(part);
            return Optional.empty();
        }, Style.EMPTY);
        int slot = parts.indexOf("\uE000");
        expect(slot >= 0 && PhazeBadgeUtil.BADGE_FONT.equals(styles.get(slot).getFont()), "slot uses the badge font");
        int rank = parts.indexOf("[VIP] ");
        expect(rank > slot && styles.get(rank).getColor() != null
                && styles.get(rank).getColor().getRgb() == 0xFF5555, "server styling is kept");
        expect(PhazeBadgeUtil.withChatBadge(badged, "nick", true) == badged, "a badged message is not badged twice");

        Text dev = PhazeBadgeUtil.withChatBadge(Text.literal("Nick: hi"), "nick", true);
        expect(dev.getString().equals("\uE001Nick: hi"), "developer slot at the start");
        Text tab = PhazeBadgeUtil.withBadge(Text.literal("[A] Nick"), false, 1);
        expect(tab.getString().equals("\uE000 [A] Nick"), "TAB slot ahead of the prefix, extra space after it");
    }

    private static void detection() {
        Style badgeFont = Style.EMPTY.withFont(PhazeBadgeUtil.BADGE_FONT);
        expect(PhazeBadgeUtil.badgeAt(OrderedText.styledForwardsVisitedString("\uE000Nick", badgeFont))
                == PhazeBadgeUtil.USER_BADGE, "user line");
        expect(PhazeBadgeUtil.badgeAt(OrderedText.styledForwardsVisitedString("\uE001Nick", badgeFont))
                == PhazeBadgeUtil.CODE_BADGE, "developer line");
        expect(PhazeBadgeUtil.badgeAt(OrderedText.styledForwardsVisitedString("  Centered", Style.EMPTY))
                == PhazeBadgeUtil.NO_BADGE, "server lines with leading spaces carry no badge");
        expect(PhazeBadgeUtil.badgeAt(OrderedText.styledForwardsVisitedString("\uE000x", Style.EMPTY))
                == PhazeBadgeUtil.NO_BADGE, "the mark counts only in the badge font");
        expect(!PhazeBadgeUtil.hasBadge(Text.literal("  Nick")), "spaces are not a badge");
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
