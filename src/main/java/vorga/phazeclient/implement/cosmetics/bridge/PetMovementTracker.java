package vorga.phazeclient.implement.cosmetics.bridge;

import net.minecraft.client.network.AbstractClientPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-only movement classifier with jitter filtering and stop hysteresis. */
public final class PetMovementTracker {
    private static final double MOVE_PER_TICK_SQ = 0.02 * 0.02;
    private static final int STOP_AFTER_TICKS = 2;
    private static final Map<UUID, State> STATES = new HashMap<>();

    public static boolean isMoving(AbstractClientPlayerEntity player) {
        State state = STATES.computeIfAbsent(player.getUuid(),
                ignored -> new State(player.getX(), player.getZ(), player.age));
        if (state.age == player.age) return state.moving;

        if (player.age < state.age || player.age - state.age > 20) {
            state.x = player.getX();
            state.z = player.getZ();
            state.age = player.age;
            state.stillTicks = 0;
            state.moving = false;
            return false;
        }

        double dx = player.getX() - state.x;
        double dz = player.getZ() - state.z;
        boolean moved = dx * dx + dz * dz >= MOVE_PER_TICK_SQ;
        state.x = player.getX();
        state.z = player.getZ();
        state.age = player.age;

        if (moved) {
            state.stillTicks = 0;
            state.moving = true;
        } else if (++state.stillTicks >= STOP_AFTER_TICKS) {
            state.moving = false;
        }

        if (STATES.size() > 512) {
            STATES.entrySet().removeIf(entry -> entry.getValue().age + 200 < player.age);
        }
        return state.moving;
    }

    private static final class State {
        double x;
        double z;
        int age;
        int stillTicks;
        boolean moving;

        State(double x, double z, int age) {
            this.x = x;
            this.z = z;
            this.age = age;
        }
    }

    private PetMovementTracker() { }
}
