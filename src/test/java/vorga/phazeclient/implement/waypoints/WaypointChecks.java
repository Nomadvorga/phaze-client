package vorga.phazeclient.implement.waypoints;

import vorga.phazeclient.implement.messenger.AnarchyDetector;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Headless checks of the waypoint store: sanitizing, limits, lookups and the file round trip. */
public final class WaypointChecks {
    private static final String WORLD = "a".repeat(64);
    private static final String OTHER = "sp-" + "b".repeat(64);
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path file = Path.of("Phaze", "files", "waypoints.json");
        Files.deleteIfExists(file);
        names();
        anarchies();
        sanitizing();
        storeLimits();
        Path written = waitForFile(file);
        roundTrip(written);
        tamperedFile(written);
        System.out.println("Waypoint checks passed: " + checks);
    }

    private static void anarchies() {
        expect("Анархия-103".equals(AnarchyDetector.parseFunTime("Анархия-103")), "FunTime anarchy line");
        expect("Анархия-7".equals(AnarchyDetector.parseFunTime(" ⚔ АНАРХИЯ #007 ")), "FunTime case, '#' and zeros");
        expect(AnarchyDetector.parseFunTime("Баланс: 103") == null, "FunTime ignores other numbers");
        expect("ДуоЛайм #13".equals(AnarchyDetector.parseHolyWorld("ДуоЛайм #13")), "HolyWorld duo");
        expect("КланЛайт #32".equals(AnarchyDetector.parseHolyWorld("  КланЛайт  #32")), "HolyWorld clan");
        expect("Прайм #1".equals(AnarchyDetector.parseHolyWorld("Прайм#1")), "HolyWorld prime without space");
        expect(AnarchyDetector.parseHolyWorld("Онлайн: 1200") == null, "HolyWorld ignores lines without '#'");
        expect(AnarchyDetector.isValidLabel("СолоЛайт #1") && !AnarchyDetector.isValidLabel("bad\nlabel")
                && !AnarchyDetector.isValidLabel("x".repeat(25)), "label format");
    }

    private static void names() {
        expect(WaypointStore.cleanName("  Base \u00A7cred\n\t camp  ").equals("Base cred camp"), "name is cleaned");
        expect(WaypointStore.cleanName("x".repeat(80)).length() == WaypointStore.NAME_MAX, "name is capped");
        expect(WaypointStore.cleanName("\u200Bhidden\u202E").equals("hidden"), "format characters are dropped");
        expect(WaypointStore.cleanName(null).isEmpty(), "null name is empty");
    }

    private static void sanitizing() {
        Waypoint far = waypoint(0L, "far", "minecraft:overworld", 99_000_000.0D, 9_999.0D, -99_000_000.0D, 0x12345678);
        Waypoint clean = WaypointStore.sanitize(far);
        expect(clean.x() == WaypointStore.COORD_LIMIT && clean.z() == -WaypointStore.COORD_LIMIT, "x/z clamp to the border");
        expect(clean.y() == WaypointStore.Y_LIMIT, "y clamps");
        expect((clean.color() >>> 24) == 0xFF && (clean.color() & 0xFFFFFF) == 0x345678, "colour is opaque");
        expect(WaypointStore.sanitize(waypoint(0L, "nan", "minecraft:overworld", Double.NaN, 0, 0, 0)) == null, "NaN rejected");
        expect(WaypointStore.sanitize(waypoint(0L, "bad", "../../etc", 0, 0, 0, 0)) == null, "bad dimension rejected");
        expect(WaypointStore.sanitize(waypoint(0L, "r", "minecraft:the_end", 1.23456D, 2.0D, -3.005D, 0)).x() == 1.23D,
                "coordinates keep two decimals");
        expect(!WaypointStore.isValidWorldKey("not-a-key") && WaypointStore.isValidWorldKey(WORLD)
                && WaypointStore.isValidWorldKey(OTHER), "world key format");
    }

    private static void storeLimits() {
        WaypointStore store = WaypointStore.get();
        int version = store.version();
        expect(store.put("bogus", waypoint(0L, "x", "minecraft:overworld", 0, 0, 0, 0)) == null, "bad world rejected");
        Waypoint first = store.put(WORLD, new Waypoint(0L, "Home", "minecraft:overworld", 10.5D, 64, -3.25D, 0xFF00FF00,
                true, true, false, true, 1L, 42L));
        expect(first != null && first.id() != 0L && store.version() > version, "draft gets an id and bumps the version");
        expect(store.findByMessage(WORLD, 42L) == first, "found by chat message");
        Waypoint renamed = store.put(WORLD, new Waypoint(first.id(), "Home 2", first.dimension(), first.x(), first.y(),
                first.z(), first.color(), true, true, false, true, first.createdAt(), first.messageId()));
        expect(store.list(WORLD).size() == 1 && renamed.name().equals("Home 2"), "same id replaces");
        for (int index = 1; index < WaypointStore.MAX_PER_WORLD; index++) {
            store.put(WORLD, waypoint(0L, "p" + index, "minecraft:the_nether", index, 70, index, 0xFF0000FF));
        }
        expect(store.isFull(WORLD) && store.put(WORLD, waypoint(0L, "over", "minecraft:overworld", 0, 0, 0, 0)) == null,
                "per-world limit holds");
        expect(store.snapshot(WORLD, "minecraft:overworld").size() == 1, "snapshot filters by dimension");
        Waypoint other = store.put(OTHER, waypoint(0L, "sp", "minecraft:overworld", 1, 2, 3, 0));
        store.remove(OTHER, other.id());
        expect(store.list(OTHER).isEmpty(), "removed");
    }

    private static void roundTrip(Path file) throws Exception {
        WaypointStore fresh = freshStore();
        expect(fresh.list(WORLD).size() == WaypointStore.MAX_PER_WORLD, "all points reload");
        Waypoint home = fresh.findByMessage(WORLD, 42L);
        expect(home != null && home.name().equals("Home 2") && home.x() == 10.5D && !home.showBeam(), "fields reload");
        expect(!Files.readString(file, StandardCharsets.UTF_8).contains("mc."), "no server address in the file");
    }

    private static void tamperedFile(Path file) throws Exception {
        String hostile = "{\"worlds\":{\"" + WORLD + "\":[{\"id\":5,\"name\":\"\u00A7kBad\",\"dimension\":\"minecraft:overworld\","
                + "\"x\":1e300,\"y\":-1e9,\"z\":\"nope\",\"color\":1},"
                + "{\"id\":6,\"name\":\"\u00A7kOk\",\"dimension\":\"minecraft:overworld\",\"x\":1e300,\"y\":-1e9,\"z\":2,\"color\":1},"
                + "{\"id\":6,\"name\":\"dup\",\"dimension\":\"minecraft:overworld\",\"x\":0,\"y\":0,\"z\":0,\"color\":1}],"
                + "\"../escape\":[{\"id\":7}]}}";
        Files.writeString(file, hostile, StandardCharsets.UTF_8);
        WaypointStore fresh = freshStore();
        var list = fresh.list(WORLD);
        expect(list.size() == 1, "malformed and duplicate entries are skipped");
        Waypoint ok = list.get(0);
        expect(ok.name().equals("kOk") && ok.x() == WaypointStore.COORD_LIMIT && ok.y() == -WaypointStore.Y_LIMIT,
                "loaded entries are sanitized");
        expect(fresh.list("../escape").isEmpty(), "bad world keys are ignored");
    }

    private static WaypointStore freshStore() throws Exception {
        Constructor<WaypointStore> constructor = WaypointStore.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        WaypointStore store = constructor.newInstance();
        Method load = WaypointStore.class.getDeclaredMethod("ensureLoaded");
        load.setAccessible(true);
        load.invoke(store);
        return store;
    }

    private static Path waitForFile(Path file) throws InterruptedException {
        for (int attempt = 0; attempt < 200 && !Files.exists(file); attempt++) Thread.sleep(10L);
        Thread.sleep(400L);
        expect(Files.exists(file), "store writes its file");
        return file;
    }

    private static Waypoint waypoint(long id, String name, String dimension, double x, double y, double z, int color) {
        return new Waypoint(id, name, dimension, x, y, z, color, true, true, true, true, 0L, 0L);
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
