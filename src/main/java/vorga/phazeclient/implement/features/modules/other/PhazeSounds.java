package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class PhazeSounds {
    public static final SoundEvent HEARTBEAT = SoundEvent.of(Identifier.of("phaze", "heartbeat"));
    public static final SoundEvent KILL_LASER = SoundEvent.of(Identifier.of("phaze", "kill_laser"));
    public static final SoundEvent KILL_DING = SoundEvent.of(Identifier.of("phaze", "kill_ding"));
    public static final SoundEvent KILL_DESTROY = SoundEvent.of(Identifier.of("phaze", "kill_destroy"));
    public static final SoundEvent KILL_HIT = SoundEvent.of(Identifier.of("phaze", "kill_hit"));
    public static final SoundEvent KILL_CRUNCH = SoundEvent.of(Identifier.of("phaze", "kill_crunch"));
    public static final SoundEvent KILL_POP = SoundEvent.of(Identifier.of("phaze", "kill_pop"));
    /** Someone wrote @nick in a chat. */
    public static final SoundEvent PING = SoundEvent.of(Identifier.of("phaze", "ping"));
    /** Someone shared a point in a chat. */
    public static final SoundEvent WAYPOINT = SoundEvent.of(Identifier.of("phaze", "waypoint"));

    private PhazeSounds() {}
}
