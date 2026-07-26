package vorga.phazeclient.api.system.draw;

import net.minecraft.client.render.BuiltBuffer;

/**
 * TEMPORARY - swallows world-overlay draws for the GUI test build.
 *
 * <p>The world overlays ({@code Render3DUtil}, {@code PredictionsRenderer},
 * {@code HolyWorldHelperRenderer}) issue their geometry through ~17 draw
 * sites spread over four different draw modes. Porting each of those to
 * its own {@link net.minecraft.client.render.RenderLayer} is real work
 * and none of it is needed to exercise the menu, so for the first
 * testable jar the draws are routed here instead.
 *
 * <p>Effect: FT Helper, Block Overlay, HolyWorld Helper, predictions and
 * hit-range draw nothing. Everything else about them - state tracking,
 * settings, HUD text - still runs. The buffers are closed properly so
 * this leaks nothing.
 *
 * <p>This class must be deleted and the call sites given real layers
 * before the port is finished. It exists only so the GUI can be tested
 * ahead of the world-render work.
 */
public final class PhazeWorldDrawStub {

    public static void drawStubbed(BuiltBuffer buffer) {
        if (buffer != null) {
            buffer.close();
        }
    }

    private PhazeWorldDrawStub() {
    }
}
