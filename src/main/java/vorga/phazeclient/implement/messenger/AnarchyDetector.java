package vorga.phazeclient.implement.messenger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Formatting;
import vorga.phazeclient.base.util.ServerUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which anarchy of a multi-anarchy network the player is on, read from the
 * sidebar: FunTime shows "Анархия-103" (labelled "Анархия 103"), HolyWorld ends
 * the sidebar with the mode and number ("ДуоЛайт #13", "Лайт(1.20) #1"). Every anarchy of a network
 * shares one address, so this label is what tells their worlds apart.
 *
 * <p>Scanned twice a second on the client thread. The label survives a sidebar
 * that briefly goes missing and is reset only when the world itself changes.</p>
 */
public final class AnarchyDetector {
    private static final int SCAN_TICKS = 10;
    /** What a label may look like anywhere it travels: chat cards, the store key, the UI. */
    public static final Pattern LABEL = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} #().-]{0,23}");
    private static final Pattern FUNTIME = Pattern.compile("Анархия\\s*[-–—#№:]?\\s*(\\d{1,4})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
    /** "ДуоЛайт #13", "Прайм #1", or a mode with its game version: "Лайт(1.20) #1". */
    private static final Pattern HOLYWORLD = Pattern.compile(
            "(\\p{L}{3,16})\\s*(\\(\\s*\\d{1,2}(?:\\.\\d{1,2}){0,2}\\s*\\))?\\s*#\\s*(\\d{1,4})",
            Pattern.UNICODE_CHARACTER_CLASS);
    /** Cards from before the label lost its hyphen. */
    private static final Pattern LEGACY_FUNTIME = Pattern.compile("Анархия-(\\d{1,4})");

    private enum Network { FUNTIME, HOLYWORLD }

    private static volatile String current;
    private static ClientWorld world;
    private static int ticks;

    private AnarchyDetector() {
    }

    /** "Анархия 103", "КланЛайт #32", or null when unknown or not on such a network. */
    public static String current() {
        return current;
    }

    public static void tick(MinecraftClient client) {
        if (client.world != world) {
            world = client.world;
            current = null;
            ticks = 0;
        }
        if (world == null || ticks++ % SCAN_TICKS != 0) return;
        Network network = networkOf(ServerUtil.getCurrentServerHost());
        if (network == null) {
            current = null;
            return;
        }
        String found = scan(world.getScoreboard(), network);
        if (found != null) current = found;
    }

    private static Network networkOf(String host) {
        if (host == null || host.isEmpty()) return null;
        for (String segment : host.split("\\.")) {
            switch (segment) {
                case "funtime" -> {
                    return Network.FUNTIME;
                }
                case "holyworld", "hollyworld" -> {
                    return Network.HOLYWORLD;
                }
                default -> {
                }
            }
        }
        return null;
    }

    private static String scan(Scoreboard scoreboard, Network network) {
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) return null;
        List<ScoreboardEntry> entries = new ArrayList<>();
        for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(objective)) {
            if (!entry.hidden()) entries.add(entry);
        }
        // Bottom line first: the lowest score is drawn last.
        entries.sort((a, b) -> {
            int byValue = Integer.compare(a.value(), b.value());
            return byValue != 0 ? byValue : String.CASE_INSENSITIVE_ORDER.compare(b.owner(), a.owner());
        });
        for (ScoreboardEntry entry : entries) {
            Team team = scoreboard.getScoreHolderTeam(entry.owner());
            String line = Formatting.strip(Team.decorateName(team, entry.name()).getString());
            String label = parse(line, network);
            if (label != null) return label;
        }
        return network == Network.FUNTIME ? parse(Formatting.strip(objective.getDisplayName().getString()), network) : null;
    }

    private static String parse(String line, Network network) {
        if (line == null || line.isEmpty()) return null;
        if (network == Network.FUNTIME) {
            Matcher matcher = FUNTIME.matcher(line);
            return matcher.find() ? valid("Анархия " + Integer.parseInt(matcher.group(1))) : null;
        }
        Matcher matcher = HOLYWORLD.matcher(line);
        if (!matcher.find()) return null;
        String version = matcher.group(2) == null ? "" : matcher.group(2).replaceAll("\\s+", "");
        return valid(matcher.group(1) + version + " #" + Integer.parseInt(matcher.group(3)));
    }

    /** A label as cards carry it, in today's spelling: "Анархия-103" reads as "Анархия 103". */
    public static String normalize(String label) {
        if (label == null) return null;
        Matcher legacy = LEGACY_FUNTIME.matcher(label);
        return legacy.matches() ? "Анархия " + legacy.group(1) : label;
    }

    /** The same parsing the sidebar scan uses, for one line. */
    public static String parseFunTime(String line) {
        return parse(line, Network.FUNTIME);
    }

    public static String parseHolyWorld(String line) {
        return parse(line, Network.HOLYWORLD);
    }

    private static String valid(String label) {
        return LABEL.matcher(label).matches() ? label : null;
    }

    public static boolean isValidLabel(String label) {
        return label != null && LABEL.matcher(label).matches();
    }
}
