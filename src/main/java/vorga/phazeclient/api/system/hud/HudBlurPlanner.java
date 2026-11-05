package vorga.phazeclient.api.system.hud;

import java.util.ArrayList;
import java.util.List;

/** Previous layout is only a preparation hint; live draws still validate radius, region and revision. */
public final class HudBlurPlanner {
    public static final int MAX_REQUESTS = 128;
    private List<Request> previous = new ArrayList<>(MAX_REQUESTS);
    private List<Request> current = new ArrayList<>(MAX_REQUESTS);
    private boolean rendered;

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(Rect other) {
            return other.x >= x && other.y >= y
                    && other.x + other.width <= x + width && other.y + other.height <= y + height;
        }
    }

    public record Request(float radius, Rect region) {}
    public record Group(float radius, List<Rect> regions) {}

    public void beginFrame() { current.clear(); rendered = false; }

    public void remember(float radius, Rect region) {
        rendered = true;
        if (!Float.isFinite(radius) || radius <= 8.0F || region.width <= 0 || region.height <= 0) return;
        Request request = new Request(radius, region);
        if (current.size() < MAX_REQUESTS && !current.contains(request)) current.add(request);
    }

    public void endFrame() {
        // Frames that only blit the HUD cache must not erase the last rendered layout.
        if (!rendered) return;
        rendered = false;
        List<Request> old = previous;
        previous = current;
        current = old;
        current.clear();
    }

    public void reset() { previous.clear(); current.clear(); rendered = false; }

    public List<Group> groups() {
        List<Group> groups = new ArrayList<>();
        for (Request request : previous) {
            Group group = null;
            for (Group candidate : groups) {
                // Do not quantize the slider or reuse a neighbouring radius.
                if (Float.floatToIntBits(candidate.radius) == Float.floatToIntBits(request.radius)) {
                    group = candidate;
                    break;
                }
            }
            if (group == null) {
                group = new Group(request.radius, new ArrayList<>());
                groups.add(group);
            }
            boolean covered = false;
            for (Rect region : group.regions) if (region.contains(request.region)) { covered = true; break; }
            if (!covered) {
                group.regions.removeIf(request.region::contains);
                group.regions.add(request.region);
            }
        }
        return groups;
    }

    /** Integer pixel coverage, including the bilinear guard, without stretching a cropped blit. */
    public static Rect scale(Rect rect, int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
        return map(rect, sourceWidth, sourceHeight, targetWidth, targetHeight, 2);
    }

    /** Back-propagate a pass's footprint; every tap must read freshly prepared pixels. */
    public static Rect requiredSource(Rect target, int targetWidth, int targetHeight,
                                      int sourceWidth, int sourceHeight, float offset, boolean downsample) {
        int guard = (int) Math.ceil(Math.max(0.0F, offset) * (downsample ? 1.0F : 2.0F)) + 1;
        return map(target, targetWidth, targetHeight, sourceWidth, sourceHeight, guard);
    }

    private static Rect map(Rect rect, int fromWidth, int fromHeight, int toWidth, int toHeight, int guard) {
        int left = Math.max(0, (int) Math.floor(rect.x * (double) toWidth / fromWidth) - guard);
        int bottom = Math.max(0, (int) Math.floor(rect.y * (double) toHeight / fromHeight) - guard);
        int right = Math.min(toWidth, (int) Math.ceil((rect.x + rect.width) * (double) toWidth / fromWidth) + guard);
        int top = Math.min(toHeight, (int) Math.ceil((rect.y + rect.height) * (double) toHeight / fromHeight) + guard);
        return new Rect(left, bottom, Math.max(1, right - left), Math.max(1, top - bottom));
    }
}
