package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Passive observations only; all access is on the client/render thread. */
public final class LowHealthOverlayProbe {
    public static final Identifier MASK = Identifier.of("phaze", "low_health_vignette_mask");
    private static final List<Frame> frames = new ArrayList<>();
    private static boolean armed;
    private static Frame frame;
    private static Thread clientThread;
    private static AbstractTexture cachedTexture;
    private static int registrations;
    private static int allocations;
    private static int uploads;

    private LowHealthOverlayProbe() {}

    public static void reset() {
        stop();
        cachedTexture = null;
        registrations = allocations = uploads = 0;
    }

    public static void start() {
        frames.clear();
        frame = null;
        clientThread = Thread.currentThread();
        armed = true;
    }

    public static void stop() {
        armed = false;
        frame = null;
    }

    public static void beginFrame() {
        if (armed) frame = new Frame();
    }

    public static void endFrame() {
        if (frame != null) frames.add(frame);
        frame = null;
    }

    public static void hud() {
        if (frame != null) frame.hud = ++frame.sequence;
    }

    public static void debug() {
        if (frame != null) frame.debug = ++frame.sequence;
    }

    public static void scoreboard() {
        if (frame != null) frame.scoreboard = ++frame.sequence;
    }

    public static void screen(Screen screen) {
        if (frame != null) {
            frame.screen = ++frame.sequence;
            frame.screenClass = screen.getClass();
        }
    }

    public static void flushed(DrawContext context) {
        if (frame == null) return;
        frame.lastFlush = ++frame.sequence;
        frame.lastFlushContext = context;
        if (frame.returned && context == frame.warningContext) frame.flushesAfterWarning++;
    }

    public static void beginWarning(DrawContext context) {
        if (frame == null) return;
        frame.warningCalls++;
        frame.inWarning = true;
        frame.warningContext = context;
        frame.warning = ++frame.sequence;
        frame.flushedVanilla = frame.lastFlushContext == context
                && frame.lastFlush > Math.max(frame.hud, Math.max(frame.screen, Math.max(frame.debug, frame.scoreboard)));
    }

    public static void endWarning(boolean rendered) {
        if (frame == null) return;
        frame.inWarning = false;
        frame.returned = true;
        frame.rendered = rendered;
    }

    public static void mask(DrawContext context, Function<Identifier, RenderLayer> layer,
                            Identifier id, int x, int y, int width, int height, int color) {
        if (frame == null || !MASK.equals(id)) return;
        frame.quads++;
        frame.mask = ++frame.sequence;
        frame.validQuad &= frame.inWarning && context == frame.warningContext
                && x == 0 && y == 0 && width == context.getScaledWindowWidth()
                && height == context.getScaledWindowHeight() && (color >>> 24) > 0
                && layer.apply(id) == RenderLayer.getGuiTexturedOverlay(id);
        AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(id);
        if (cachedTexture == null) cachedTexture = texture;
        frame.validQuad &= texture == cachedTexture && texture instanceof NativeImageBackedTexture;
        if (texture instanceof NativeImageBackedTexture backed) {
            frame.validQuad &= backed.getImage() != null && backed.getImage().getWidth() == 256
                    && backed.getImage().getHeight() == 256;
        }
    }

    public static void registered(Identifier id) {
        if (Thread.currentThread() == clientThread && frame != null && MASK.equals(id)) registrations++;
    }

    public static void allocatedImage() {
        if (insideWarning()) {
            frame.allocations++;
            allocations++;
        }
    }

    public static void uploadedImage() {
        if (insideWarning()) {
            frame.uploads++;
            uploads++;
        }
    }

    public static void framebuffer() {
        if (insideWarning()) frame.framebuffers++;
    }

    private static boolean insideWarning() {
        // NativeImage also runs on resource-loading workers; don't attribute their work to this render.
        return Thread.currentThread() == clientThread && frame != null && frame.inWarning;
    }

    public static void verify(String name, boolean active, boolean debug, boolean scoreboard,
                              Class<? extends Screen> screen) {
        check(frames.size() >= 6, name + ": at least six actual GameRenderer frames observed");
        for (int i = 0; i < frames.size(); i++) {
            Frame f = frames.get(i);
            String at = name + " frame " + i;
            check(f.warningCalls == 1, at + ": warning.render runs exactly once (" + f.warningCalls + ")");
            check(f.quads == (active ? 1 : 0), at + ": mask quad count=" + f.quads);
            check(f.rendered == active, at + ": render return value matches visual work");
            check(f.flushesAfterWarning == (active ? 1 : 0), at + ": conditional overlay flush count=" + f.flushesAfterWarning);
            check(f.flushedVanilla && f.hud > 0, at + ": vanilla GUI is flushed before warning.render");
            check((f.debug > 0) == debug, at + ": actual F3 rendering matches fixture");
            check((f.scoreboard > 0) == scoreboard, at + ": populated sidebar was actually rendered");
            check(f.screenClass == screen, at + ": actual screen render=" + f.screenClass);
            if (active) {
                check(f.validQuad, at + ": one viewport-sized cached mask uses the overlay render layer");
                check(f.mask > f.warning && f.warning > Math.max(f.screen, Math.max(f.debug, f.scoreboard)),
                        at + ": overlay comes after debug, scoreboard and screen/tooltip drawing");
            }
            check(f.framebuffers == 0, at + ": warning creates/initializes/binds no framebuffer");
            if (!active || i > 0) {
                check(f.allocations == 0 && f.uploads == 0, at + ": no per-frame native image allocation/upload");
            }
        }
        check(registrations <= 1 && allocations <= 1 && uploads <= 1,
                name + ": at most one lazy mask registration/allocation/upload across all cases: "
                        + registrations + "/" + allocations + "/" + uploads);
        System.out.println("Low health overlay " + name + " PASSED: " + frames.size()
                + " frames, " + (active ? 1 : 0) + " quad/frame; cumulative mask registrations/images/uploads="
                + registrations + "/" + allocations + "/" + uploads);
        stop();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Frame {
        int sequence, hud, debug, scoreboard, screen, warning, mask, lastFlush;
        int warningCalls, quads, flushesAfterWarning, allocations, uploads, framebuffers;
        boolean inWarning, returned, rendered, flushedVanilla;
        boolean validQuad = true;
        DrawContext lastFlushContext, warningContext;
        Class<? extends Screen> screenClass;
    }
}
