package vorga.phazeclient.implement.features.modules.other;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.api.feature.module.setting.implement.WaypointListSetting;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.implement.messenger.ChatWorld;
import vorga.phazeclient.implement.waypoints.Waypoint;
import vorga.phazeclient.implement.waypoints.WaypointStore;

import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

/**
 * Saved in-world points: the list and its editor live in the menu, the markers
 * are drawn by {@link vorga.phazeclient.implement.waypoints.WaypointRenderer}.
 * Each death saves a waypoint of its own; only the newest few stay.
 */
public class Waypoints extends Module {
    private static final Waypoints INSTANCE = new Waypoints();
    /** Death waypoints kept per world; a renamed one is the player's own and never pruned. */
    private static final int KEPT_DEATHS = 5;
    private static boolean registered = false;

    public final WaypointListSetting list = new WaypointListSetting("Saved Waypoints", "Waypoints saved for the current server or world");
    /** Read even while the module is off: group points belong to the messenger. */
    public final BooleanSetting chatWaypoints = new BooleanSetting("Chat Waypoints", "Show points shared in your groups and chats, even with the module off").setValue(true);
    public final BooleanSetting deathWaypoint = new BooleanSetting("Death Waypoint", "Automatically save a waypoint where you die").setValue(true);
    public final ColorSetting deathColor = new ColorSetting("Death Color", "Color of death waypoint").value(0xFFFF3333).noAlpha();
    public final ValueSetting maxDistance = new ValueSetting("Max Distance", "Maximum render distance in blocks").range(100, 30000).step(100).setValue(10000);

    private boolean wasDead = false;

    private Waypoints() {
        super("waypoints", "Waypoints", ModuleCategory.OTHER);
        setup(list, chatWaypoints, deathWaypoint, deathColor, maxDistance);
        registerEvents();
    }

    public static Waypoints getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Saved 3D waypoints with a block highlight, beam and distance tag, plus death points";
    }

    @Override
    public String getIcon() {
        return "death_coords.png";
    }

    private synchronized void registerEvents() {
        if (registered) return;
        registered = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) {
                wasDead = false;
                return;
            }
            boolean dead = client.player.isDead() || client.player.getHealth() <= 0.0F;
            if (dead && !wasDead && isEnabled() && deathWaypoint.isValue()) saveDeath(client);
            wasDead = dead;
        });
    }

    private void saveDeath(MinecraftClient client) {
        String world = WaypointStore.currentWorldKey();
        String dimension = ChatWorld.dimension();
        if (world == null || dimension == null) return;
        String prefix = Lang.t("waypoints.death") + " ";
        WaypointStore store = WaypointStore.get();
        List<Waypoint> deaths = store.list(world).stream().filter(waypoint -> isAutoDeath(waypoint, prefix)).toList();
        // The oldest go first, so the new one always fits, even in a full world.
        int excess = deaths.size() - (KEPT_DEATHS - 1);
        for (int index = 0; index < excess; index++) store.remove(world, deaths.get(index).id());
        if (store.isFull(world) && !deaths.isEmpty() && excess <= 0) store.remove(world, deaths.get(0).id());
        LocalTime now = LocalTime.now();
        String name = prefix + String.format(Locale.ROOT, "%02d:%02d", now.getHour(), now.getMinute());
        int color = deathColor.getColor();
        store.put(world, new Waypoint(0L, name, dimension, client.player.getX(), client.player.getY(), client.player.getZ(),
                color == 0 ? 0xFFFF3333 : color, true, true, true, true, System.currentTimeMillis(), 0L));
    }

    private static boolean isAutoDeath(Waypoint waypoint, String prefix) {
        String name = waypoint.name();
        return waypoint.messageId() == 0L && name.length() == prefix.length() + 5 && name.startsWith(prefix)
                && name.matches(".* \\d{2}:\\d{2}");
    }
}
