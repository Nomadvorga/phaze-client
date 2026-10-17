package vorga.phazeclient.api.system.shape;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.Text;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.api.system.shape.implement.Rectangle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Real shader pixels, no surrogate shader math. Main suite owns entrypoint registration. */
public final class RoundedOutlineClientTest implements FabricClientGameTest {
    private static final int SIZE = 64;
    private static final int STEP = 88;
    private static final int[] FILLS = {0x000040FF, 0x00FF4000, 0xFF202020, 0x00000000};
    private static final int[] OUTLINES = {0xAA808080, 0xAA808080, 0xAA808080, 0xAA2050FF};

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        Screen saved = context.computeOnClient(client -> client.currentScreen);
        try {
            context.runOnClient(client -> client.setScreen(new FixtureScreen()));
            Path screenshot = context.takeScreenshot("rounded-outline-alpha-regression");
            try (var input = Files.newInputStream(screenshot); NativeImage pixels = NativeImage.read(input)) {
                check(pixels.getWidth() >= 380 && pixels.getHeight() >= 190, "fixture fits framebuffer");
                for (int row = 0; row < 2; row++) {
                    for (int column = 0; column < FILLS.length; column++) {
                        verifyTile(pixels, row, column);
                    }
                }
                // Both paths must agree despite the eager shader's RGB-only dithering.
                for (int column = 0; column < FILLS.length; column++) {
                    for (int y = 0; y < SIZE; y++) {
                        for (int x = 0; x < SIZE; x++) {
                            int eager = pixels.getColorArgb(20 + column * STEP + x, 20 + y);
                            int batched = pixels.getColorArgb(20 + column * STEP + x, 20 + STEP + y);
                            for (int shift : new int[]{0, 8, 16}) {
                                check(Math.abs(channel(eager, shift) - channel(batched, shift)) <= 2,
                                        "eager/batched agree at " + column + ":" + x + "," + y);
                            }
                        }
                    }
                }
            }
            System.out.println("Rounded outline shader pixel checks PASSED: " + screenshot);
        } catch (IOException e) {
            throw new RuntimeException("Could not read rounded outline fixture screenshot", e);
        } finally {
            context.runOnClient(client -> client.setScreen(saved));
        }
    }

    private static void verifyTile(NativeImage pixels, int row, int column) {
        int left = 20 + column * STEP;
        int top = 20 + row * STEP;
        int visibleCorner = 0;
        int partialCorner = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int pixel = pixels.getColorArgb(left + x, top + y);
                int red = channel(pixel, 16), green = channel(pixel, 8), blue = channel(pixel, 0);
                if (column < 3) {
                    check(Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue)) <= 1,
                            "neutral outline has no theme bleed: row=" + row + " tile=" + column + " pixel=" + x + "," + y);
                }
                if (x < 16 && y < 16) {
                    if (blue > 8) visibleCorner++;
                    if (blue > 2 && blue < 35) partialCorner++;
                }
            }
        }
        check(visibleCorner > 8, "normal rounded corner outline remains visible");
        check(partialCorner > 2, "rounded corner retains partially covered AA pixels");
        int center = pixels.getColorArgb(left + SIZE / 2, top + SIZE / 2);
        check(Math.abs(channel(center, 0) - (column == 2 ? 32 : 0)) <= 1,
                "transparent fill stays invisible; opaque dark panel stays filled");
        int cutout = pixels.getColorArgb(left, top);
        check((cutout & 0xFFFFFF) == 0, "rounded corner cutout remains transparent over black");
        if (column == 3) {
            int edge = pixels.getColorArgb(left + SIZE / 2, top);
            check(channel(edge, 0) > channel(edge, 16) + 25, "intentional theme-colored outline is preserved");
        }
    }

    private static int channel(int color, int shift) { return (color >>> shift) & 255; }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FixtureScreen extends Screen {
        private final Rectangle rectangle = new Rectangle();

        private FixtureScreen() { super(Text.literal("Rounded outline alpha fixture")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0xFF000000);
            context.draw();
            float[] shaderColor = RenderSystem.getShaderColor().clone();
            context.getMatrices().push();
            try {
                RenderSystem.setShaderColor(1, 1, 1, 1);
                // Exact framebuffer pixels independent of Minecraft GUI scale.
                float inverseScale = (float) (1.0 / client.getWindow().getScaleFactor());
                context.getMatrices().scale(inverseScale, inverseScale, 1);
                renderRow(context, 0);
                BatchedRectangle.beginScope();
                try {
                    check(BatchedRectangle.isBatching(), "batched shader available for regression coverage");
                    renderRow(context, 1);
                } finally {
                    BatchedRectangle.endScope();
                }
            } finally {
                context.getMatrices().pop();
                RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            }
        }

        private void renderRow(DrawContext context, int row) {
            for (int column = 0; column < FILLS.length; column++) {
                // Fractional placement samples both sides of the SDF's AA edge.
                rectangle.render(ShapeProperties.create(context.getMatrices(),
                                20.25F + column * STEP, 20.25F + row * STEP, SIZE, SIZE)
                        .round(16).softness(1).thickness(2)
                        .color(FILLS[column]).outlineColor(OUTLINES[column]).build());
            }
        }
    }
}
