package vorga.phazeclient.api.system.hud;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;

/** Offline planning/cache proofs. Pixel equivalence is additionally checked with a real GL context. */
public final class HudBlurOptimizationChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        history();
        samplingCoverage();
        cache();
        roundedContours();
        System.out.println("HUD blur optimization checks passed: " + checks);
    }

    private static void history() {
        HudBlurPlanner planner = new HudBlurPlanner();
        check(planner.groups().isEmpty(), "first frame has no speculative work");
        planner.beginFrame();
        for (int i = 0; i < 17; i++) {
            planner.remember(24.0F, new HudBlurPlanner.Rect(i * 100, i % 2 * 500, 40, 20));
        }
        planner.endFrame();
        List<HudBlurPlanner.Group> groups = planner.groups();
        check(groups.size() == 1 && groups.getFirst().regions().size() == 17, "17 disjoint HUDs share one pipeline");
        check(groups.getFirst().regions().stream().mapToInt(r -> r.width() * r.height()).sum() == 17 * 800,
                "sparse HUDs never turn into a full-screen bounding box draw");
        planner.beginFrame(); planner.endFrame(); planner.endFrame();
        check(planner.groups().getFirst().regions().size() == 17, "skipped frames and repeated teardown preserve hints");
        planner.beginFrame();
        planner.remember(8.0F, new HudBlurPlanner.Rect(0, 0, 20, 20));
        planner.endFrame();
        check(planner.groups().isEmpty(), "fine-only rendering drops obsolete strong hints");
        planner.beginFrame();
        planner.remember(12.0F, new HudBlurPlanner.Rect(10, 10, 20, 20));
        planner.remember(12.0F, new HudBlurPlanner.Rect(12, 12, 4, 4));
        planner.remember(12.001F, new HudBlurPlanner.Rect(10, 10, 20, 20));
        planner.endFrame();
        check(planner.groups().size() == 2, "nearby radii are not quantized");
        check(planner.groups().getFirst().regions().size() == 1, "contained requests do not repeat filter work");
        planner.beginFrame();
        for (int i = 0; i < 1000; i++) planner.remember(24.0F, new HudBlurPlanner.Rect(i * 3, 0, 2, 2));
        planner.endFrame();
        check(planner.groups().getFirst().regions().size() == HudBlurPlanner.MAX_REQUESTS, "layout hints are bounded");
        planner.reset();
        check(planner.groups().isEmpty(), "reload and resize clear layout hints");
    }

    private static void roundedContours() {
        for (float sx : new float[]{0.5F, 1, 1.5F, 3, 4}) for (float sy : new float[]{1, 2, 4}) {
            for (float radius : new float[]{0.1F, 1, 8, 14}) {
                HudRoundedContour mesh = HudRoundedContour.get(80, 28, radius, sx, sy);
                check(mesh == HudRoundedContour.get(80, 28, radius, sx, sy), "warm contour lookup reuses geometry");
                int[] colors = mesh.colors(0x502060A0, 0xB0FF8020, 4);
                check(colors == mesh.colors(0x502060A0, 0xB0FF8020, 4), "warm static palette has no allocation");
                float expectedWidth = Math.min(1, radius * Math.min(sx, sy) * Math.min(sx, sy) / Math.max(sx, sy));
                for (int i = 0; i < mesh.points(); i++) {
                    float dx = (mesh.outerX(i) - mesh.innerX(i)) * sx;
                    float dy = (mesh.outerY(i) - mesh.innerY(i)) * sy;
                    check(Math.abs(Math.hypot(dx, dy) - expectedWidth) < 0.0001, "one physical pixel AA, also under nonuniform scale");
                    check(mesh.innerY(i) >= 0 && mesh.innerY(i) <= mesh.height / 2, "inner contour never crosses itself");
                    if (i > 0) check(mesh.innerX(i) >= mesh.innerX(i - 1), "fill strips share monotonic endpoints");
                }
            }
        }
        HudRoundedContour typical = HudRoundedContour.get(160, 48, 8, 1, 1);
        int newQuads = (typical.points() - 1) * 3 + 2;
        int oldQuads = 1 + HudBackgroundGeometry.caps(8, 1).strips * 6;
        check(newQuads < oldQuads, "typical rounded solid emits fewer quads than the old staircase");
        System.out.println("Rounded contour topology (160x48, radius 8, scale 1): " + oldQuads + " -> " + newQuads + " quads; not an FPS benchmark");
    }

    private static void samplingCoverage() {
        Random random = new Random(0xB10F);
        for (int i = 0; i < 4000; i++) {
            int sourceWidth = 1 + random.nextInt(1000), sourceHeight = 1 + random.nextInt(700);
            int targetWidth = 1 + random.nextInt(1000), targetHeight = 1 + random.nextInt(700);
            int x = random.nextInt(targetWidth), y = random.nextInt(targetHeight);
            HudBlurPlanner.Rect target = new HudBlurPlanner.Rect(x, y, 1 + random.nextInt(targetWidth - x),
                    1 + random.nextInt(targetHeight - y));
            float offset = random.nextFloat() * 8.0F;
            for (boolean down : new boolean[]{false, true}) {
                HudBlurPlanner.Rect source = HudBlurPlanner.requiredSource(target, targetWidth, targetHeight,
                        sourceWidth, sourceHeight, offset, down);
                double[][] taps = down ? new double[][]{{0, 0}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}}
                        : new double[][]{{-2, 0}, {-1, 1}, {0, 2}, {1, 1}, {2, 0}, {1, -1}, {0, -2}, {-1, -1}};
                for (int tx : new int[]{x, x + target.width() - 1}) {
                    for (int ty : new int[]{y, y + target.height() - 1}) {
                        for (double[] tap : taps) {
                            int sx = (int) Math.floor((tx + 0.5) * sourceWidth / targetWidth - 0.5 + tap[0] * offset);
                            int sy = (int) Math.floor((ty + 0.5) * sourceHeight / targetHeight - 0.5 + tap[1] * offset);
                            for (int dx = 0; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) {
                                int px = Math.clamp(sx + dx, 0, sourceWidth - 1);
                                int py = Math.clamp(sy + dy, 0, sourceHeight - 1);
                                check(source.contains(new HudBlurPlanner.Rect(px, py, 1, 1)), "all bilinear taps have fresh source pixels");
                            }
                        }
                    }
                }
            }
        }
    }

    private static void cache() throws Exception {
        Class<?> type = Class.forName("vorga.phazeclient.api.system.shape.implement.Blur$HudBlurCache");
        Class<?> rect = Class.forName("vorga.phazeclient.api.system.shape.implement.Blur$BlurRegion");
        Constructor<?> constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
        Constructor<?> rectangle = rect.getDeclaredConstructor(int.class, int.class, int.class, int.class);
        rectangle.setAccessible(true);
        Object cache = constructor.newInstance();
        Method select = accessible(type, "select", int.class, float.class, rect, rect, long.class);
        Method remember = accessible(type, "remember", int.class, int.class, float.class, rect, rect);
        Method contains = accessible(type, "hasPrepared", int.class, float.class, rect, long.class);
        Method discard = accessible(type, "discardOverlapping", int.class, rect);
        Object region = rectangle.newInstance(10, 10, 20, 20), write = rectangle.newInstance(6, 6, 28, 28);
        int slot = (int) select.invoke(cache, 2, 12.0F, region, write, 1L);
        remember.invoke(cache, slot, 2, 12.0F, region, write);
        check((boolean) contains.invoke(cache, 2, 12.0F, region, 1L), "prepared region hit");
        check(!(boolean) contains.invoke(cache, 2, 12.001F, region, 1L), "radius change cannot read a stale blur");
        check(!(boolean) contains.invoke(cache, 2, 12.0F, region, 2L), "new world source cannot reuse old pixels");
        Object moved = rectangle.newInstance(11, 10, 20, 20);
        check(!(boolean) contains.invoke(cache, 2, 12.0F, moved, 1L), "moving and growing HUD falls back to live preparation");
        Object neighbour = rectangle.newInstance(31, 6, 5, 5);
        discard.invoke(cache, slot, neighbour);
        check(!(boolean) contains.invoke(cache, 2, 12.0F, region, 1L), "overlapping half-resolution guard invalidates metadata");
        for (int i = 0; i < 17; i++) {
            Object part = rectangle.newInstance(i * 2, 0, 20, 20);
            remember.invoke(cache, slot, 2, 24.0F, part, part);
        }
        for (int i = 0; i < 17; i++) {
            Object part = rectangle.newInstance(i * 2, 0, 20, 20);
            check((boolean) contains.invoke(cache, 2, 24.0F, part, 1L), "overlapping same-radius group retains every prepared region");
        }
        select.invoke(cache, 2, 12.0F, region, write, 2L);
        check(!(boolean) contains.invoke(cache, 2, 24.0F, rectangle.newInstance(0, 0, 20, 20), 1L), "source revision invalidates all regions");
    }

    private static Method accessible(Class<?> owner, String name, Class<?>... parameters) throws Exception {
        Method method = owner.getDeclaredMethod(name, parameters); method.setAccessible(true); return method;
    }

    private static void check(boolean success, String message) {
        checks++;
        if (!success) throw new AssertionError(message);
    }
}
