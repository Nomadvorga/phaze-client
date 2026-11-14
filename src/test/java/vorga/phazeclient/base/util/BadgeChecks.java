package vorga.phazeclient.base.util;

import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
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
        raster();
        System.out.println("Badge checks passed: " + checks);
    }

    private static void raster() {
        for (String name : new String[]{"user", "code"}) {
            BufferedImage art = art(name);
            int width = art.getWidth(), height = art.getHeight();
            int[] argb = art.getRGB(0, 0, width, height, null, 0, width);
            BadgeRaster.Square square = BadgeRaster.cropSquare(argb, width, height);
            int size = square.size();
            expect(size > 32 && size <= Math.max(width, height), name + " art is cropped to its letter");
            expect(touches(square, true) || touches(square, false), name + " letter fills the square on its long side");

            double fullCoverage = coverage(square.argb()) / (size * (double) size);
            for (int target : new int[]{7, 8, 12, 14, 16, 21, 28, 35, 42}) {
                int[] small = BadgeRaster.downsample(square, target);
                expect(small.length == target * target, name + " " + target + "px has the asked size");
                double smallCoverage = coverage(small) / (target * (double) target);
                expect(Math.abs(smallCoverage - fullCoverage) < 1.5 / target, name + " " + target + "px keeps the letter's area");
                boolean hardEdge = true;
                for (int pixel : small) hardEdge &= (pixel >>> 24) == 0 || (pixel >>> 24) == 255;
                expect(hardEdge, name + " " + target + "px has a hard outline");
            }
            expect(BadgeRaster.downsample(square, size + 10).length == size * size, name + " is never enlarged by filtering");

            int[][] mips = BadgeRaster.mipChain(square);
            expect(mips.length == 32 - Integer.numberOfLeadingZeros(size), name + " mips run down to one pixel");
            boolean sized = true;
            for (int level = 0; level < mips.length; level++) sized &= mips[level].length == (size >> level) * (size >> level);
            expect(sized, name + " every mip level halves like the GPU expects");
            expect(alphaMatches(mips[0], square.argb()), name + " the art itself is the top level");
            int[] soft = mips[1];
            boolean partial = false;
            for (int pixel : soft) partial |= (pixel >>> 24) > 0 && (pixel >>> 24) < 255;
            expect(partial, name + " levels below the art fade at the edge");
            int levelSize = size >> 1;
            expect(Math.abs(coverage(soft) / (levelSize * (double) levelSize) - fullCoverage) < 0.01,
                    name + " smaller levels keep the letter's area");
            expect(noDarkFringe(soft, levelSize), name + " clear pixels at the edge carry the letter's colour");
        }

        int[] flat = new int[16 * 16];
        java.util.Arrays.fill(flat, 0xFF3A7BD5);
        int[] reduced = BadgeRaster.downsample(new BadgeRaster.Square(16, flat), 5);
        boolean exact = true;
        for (int pixel : reduced) exact &= pixel == 0xFF3A7BD5;
        expect(exact, "a flat colour stays exactly that colour");

        int[] mostlyRed = {0xFFFF0000, 0xFFFF0000, 0xFFFF0000, 0};
        expect(BadgeRaster.downsample(new BadgeRaster.Square(2, mostlyRed), 1)[0] == 0xFFFF0000,
                "a mostly covered pixel is solid and clear pixels do not darken it");
        int[] mostlyClear = {0xFFFF0000, 0, 0, 0};
        expect(BadgeRaster.downsample(new BadgeRaster.Square(2, mostlyClear), 1)[0] == 0,
                "a mostly clear pixel stays clear");

        expect(java.util.Arrays.equals(BadgeRaster.plan(14, 46), new int[]{14, 1}), "small badges are drawn 1:1");
        expect(java.util.Arrays.equals(BadgeRaster.plan(46, 46), new int[]{46, 1}), "full art is drawn 1:1");
        int[] large = BadgeRaster.plan(100, 46);
        expect(large[1] == 3 && large[0] <= 46 && Math.abs(large[0] * large[1] - 100) <= 2,
                "larger than the art means whole-number blocks");
    }

    private static BufferedImage art(String name) {
        try (InputStream stream = BadgeChecks.class.getResourceAsStream(
                "/assets/phaze/textures/gui/phaze_" + name + "_badge_hd.png")) {
            if (stream == null) throw new AssertionError("missing " + name + " badge art");
            return ImageIO.read(stream);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean touches(BadgeRaster.Square square, boolean horizontal) {
        int size = square.size();
        boolean first = false, last = false;
        for (int i = 0; i < size; i++) {
            int a = horizontal ? square.argb()[i * size] : square.argb()[i];
            int b = horizontal ? square.argb()[i * size + size - 1] : square.argb()[(size - 1) * size + i];
            first |= (a >>> 24) != 0;
            last |= (b >>> 24) != 0;
        }
        return first && last;
    }

    private static boolean alphaMatches(int[] a, int[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] >>> 24 != b[i] >>> 24) return false;
        return true;
    }

    /** Every clear pixel touching the letter has been given a non-black colour. */
    private static boolean noDarkFringe(int[] argb, int size) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int pixel = argb[y * size + x];
                if ((pixel >>> 24) != 0) continue;
                boolean touches = false;
                for (int dy = -1; dy <= 1 && !touches; dy++) {
                    for (int dx = -1; dx <= 1 && !touches; dx++) {
                        int nx = x + dx, ny = y + dy;
                        touches = nx >= 0 && ny >= 0 && nx < size && ny < size && (argb[ny * size + nx] >>> 24) != 0;
                    }
                }
                if (touches && (pixel & 0xFFFFFF) == 0) return false;
            }
        }
        return true;
    }

    private static double coverage(int[] argb) {
        double sum = 0;
        for (int pixel : argb) sum += (pixel >>> 24) / 255.0;
        return sum;
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
