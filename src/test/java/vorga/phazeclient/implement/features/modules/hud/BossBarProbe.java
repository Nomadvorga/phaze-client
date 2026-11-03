package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;
import vorga.phazeclient.api.system.hud.ExordiumAnimationBridge;

import java.util.ArrayList;
import java.util.List;

/** Test-mod observations and deliberately failing foreign hooks. Never shipped in the client jar. */
public final class BossBarProbe {
    public static final Identifier FOREIGN_TEXTURE = Identifier.ofVanilla("hud/hotbar");
    public static boolean recording, timing;
    public static boolean cancelRender, failRender, cancelProgress, failProgress, replaceProgress;
    public static int foreignRenders, foreignProgress;
    public static int renders, passes, textDraws, widthReads, images, uploads, registrations;
    public static int bufferCalls, captures, cachedDraws, suppressedDraws;
    public static long renderNanos;
    public static Identifier sprite;
    public static final List<Quad> quads = new ArrayList<>();
    public static final List<Text> names = new ArrayList<>();
    private static int renderDepth;
    private static Thread renderThread;
    private static boolean samplingLayoutWidths;

    private BossBarProbe() {}

    public static void start() {
        stop();
        renders = passes = textDraws = widthReads = images = uploads = registrations = 0;
        foreignRenders = foreignProgress = 0;
        bufferCalls = captures = cachedDraws = suppressedDraws = 0;
        renderNanos = 0;
        quads.clear();
        names.clear();
        renderThread = Thread.currentThread();
        recording = true;
    }

    public static void stop() {
        recording = timing = false;
        samplingLayoutWidths = false;
        renderDepth = 0;
        sprite = null;
    }

    public static void startWidths() {
        start();
        samplingLayoutWidths = true;
    }

    public static void clearForeignHooks() {
        cancelRender = failRender = cancelProgress = failProgress = replaceProgress = false;
    }

    public static long enterRender() {
        if (!recording && !timing) return 0;
        renders++;
        renderDepth++;
        return timing ? System.nanoTime() : 0;
    }

    public static void exitRender(long started) {
        if (!recording && !timing) return;
        if (started != 0) renderNanos += System.nanoTime() - started;
        renderDepth--;
    }

    public static void foreignRender() {
        if (recording) foreignRenders++;
        if (failRender) throw new ForeignRenderException();
    }

    public static void foreignPass(boolean progress) {
        if (recording) passes++;
        if (progress && recording) foreignProgress++;
        if (progress && failProgress) throw new ForeignRenderException();
    }

    public static void text(Text name) {
        if (recording && renderDepth > 0) {
            textDraws++;
            names.add(name);
        }
    }

    public static void width(StringVisitable name) {
        if (recording && (renderDepth > 0 || samplingLayoutWidths)) widthReads++;
    }

    public static void quad(DrawContext context, int x1, int x2, int y1, int y2, int color) {
        if (!recording || sprite == null) return;
        var matrix = context.getMatrices().peek().getPositionMatrix();
        Vector3f first = matrix.transformPosition(new Vector3f(x1, y1, 0));
        Vector3f second = matrix.transformPosition(new Vector3f(x2, y2, 0));
        quads.add(new Quad(sprite, Math.min(first.x, second.x), Math.min(first.y, second.y),
                Math.max(first.x, second.x), Math.max(first.y, second.y), color, renderDepth > 0));
    }

    public static boolean insideRender() {
        return recording && renderDepth > 0 && Thread.currentThread() == renderThread;
    }

    public static void buffer(Object instance) {
        if (!recording || !isBossBuffer(instance)) return;
        bufferCalls++;
        if (ExordiumAnimationBridge.isCapturing(ExordiumAnimationBridge.BOSS_BAR)) captures++;
    }

    public static void cached(Object buffer, boolean suppressed) {
        if (!recording) return;
        Object ids = field(ExordiumAnimationBridge.class, "COMPONENT_BY_BUFFER", null);
        if (!(ids instanceof java.util.Map<?, ?> map)
                || !ExordiumAnimationBridge.BOSS_BAR.equals(map.get(buffer))) return;
        if (suppressed) suppressedDraws++;
        else cachedDraws++;
    }

    private static boolean isBossBuffer(Object instance) {
        return ExordiumAnimationBridge.BOSS_BAR.equals(String.valueOf(field(instance.getClass(), "id", instance)));
    }

    public static Object field(Class<?> owner, String name, Object instance) {
        try {
            var field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not inspect " + owner.getName() + "." + name, e);
        }
    }

    public record Quad(Identifier texture, float x1, float y1, float x2, float y2, int color, boolean inBossRender) {
        public boolean progress() { return texture.getPath().contains("progress"); }
        public boolean background() { return texture.getPath().contains("background"); }
    }

    public static final class ForeignRenderException extends RuntimeException {}
}
