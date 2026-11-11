package vorga.phazeclient.implement.waypoints;

/**
 * One saved point. Coordinates keep two decimals so "Set to current" restores
 * the exact spot; markers snap to the block that contains it.
 *
 * @param id        unique within the store, 0 for a draft that was never saved
 * @param messageId the chat message the point came from, 0 when made by hand
 */
public record Waypoint(long id, String name, String dimension, double x, double y, double z, int color,
                       boolean showText, boolean highlight, boolean showBeam, boolean showDistance,
                       long createdAt, long messageId) {

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }

    /** The display switches as chat cards carry them: 1 name, 2 highlight, 4 beam, 8 distance. */
    public int displayBits() {
        return (showText ? 1 : 0) | (highlight ? 2 : 0) | (showBeam ? 4 : 0) | (showDistance ? 8 : 0);
    }

    public Waypoint withDisplayBits(int bits) {
        return new Waypoint(id, name, dimension, x, y, z, color, (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0,
                (bits & 8) != 0, createdAt, messageId);
    }

    public Waypoint withId(long nextId) {
        return new Waypoint(nextId, name, dimension, x, y, z, color, showText, highlight, showBeam, showDistance,
                createdAt, messageId);
    }
}
