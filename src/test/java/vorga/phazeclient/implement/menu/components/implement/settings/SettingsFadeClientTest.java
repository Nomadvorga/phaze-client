package vorga.phazeclient.implement.menu.components.implement.settings;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import vorga.phazeclient.api.system.font.msdf.MsdfFont;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.base.util.render.ScissorManager;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.menu.font.GuiFonts;
import vorga.phazeclient.implement.menu.font.MsdfGuiFontRenderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Opt-in real-shader regression test; the main suite owns entrypoint registration. */
public final class SettingsFadeClientTest implements FabricClientGameTest {
    private static final String TEXT = "M".repeat(40);
    private static final float SIZE = 16;
    private static final float PIXEL_SCALE = 3.375F;
    private static final float TEXT_Y = 4;
    private static final int COLOR = 0xB86ACEE8; // Translucent, non-neutral tint tests all RGB channels.
    private static final int PLAIN_STEP = 104;
    private static final int GUARD = 12;
    private static final Case[] CASES = {
            new Case("direct-both", Route.DIRECT, false, false, 1, 1),
            new Case("direct-partial", Route.DIRECT, false, false, 0.65F, 0.35F),
            new Case("msdf-idle", Route.MSDF, false, false, 0, 1),
            new Case("msdf-tail", Route.MSDF, true, false, 1, 0),
            new Case("adapter-idle", Route.ADAPTER, false, false, 0, 1),
            new Case("adapter-tail", Route.ADAPTER, true, false, 1, 0),
            new Case("msdf-parent", Route.MSDF, false, true, 0, 1),
            new Case("adapter-parent", Route.ADAPTER, false, true, 0, 1)
    };

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        Screen saved = context.computeOnClient(client -> client.currentScreen);
        try {
            for (Case testCase : CASES) {
                FixtureScreen fixture = context.computeOnClient(client -> new FixtureScreen(testCase));
                context.runOnClient(client -> client.setScreen(fixture));
                context.waitTicks(1);
                Path referencePath = context.takeScreenshot("settings-fade-" + testCase.name() + "-reference");
                context.runOnClient(client -> fixture.reference = false);
                context.waitTicks(1);
                Path fadedPath = context.takeScreenshot("settings-fade-" + testCase.name() + "-faded");
                context.runOnClient(client -> check(!MsdfRenderer.isUsingFallback(),
                        testCase.name() + ": MSDF shader must not silently fall back"));
                try (NativeImage reference = readPixels(referencePath); NativeImage faded = readPixels(fadedPath)) {
                    verify(fixture, reference, faded);
                }
                System.out.println("Settings fade pixel checks PASSED: " + testCase.name() + " " + fadedPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not read settings fade fixture screenshots", e);
        } finally {
            context.runOnClient(client -> client.setScreen(saved));
        }
    }

    private static NativeImage readPixels(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            return NativeImage.read(input);
        }
    }

    private static void verify(FixtureScreen fixture, NativeImage reference, NativeImage faded) {
        Case testCase = fixture.testCase;
        Clip viewport = fixture.viewport;
        Clip clip = testCase.parent() ? fixture.parent : viewport;
        check(reference.getWidth() == faded.getWidth() && reference.getHeight() == faded.getHeight(),
                testCase.name() + ": framebuffer size stayed fixed between captures");
        check(faded.getWidth() > viewport.right() + GUARD
                        && faded.getHeight() > viewport.bottom() + PLAIN_STEP + GUARD,
                testCase.name() + ": fixture and guards fit framebuffer");
        int solid = 0, clippedInk = 0, unchangedPlainInk = 0;
        int peakBlue = Math.round(channel(COLOR, 0) * channel(COLOR, 24) / 255.0F);
        for (int y = viewport.top() - GUARD; y < viewport.bottom() + GUARD; y++) {
            for (int x = viewport.left() - GUARD; x < viewport.right() + GUARD; x++) {
                int original = reference.getColorArgb(x, y);
                int actual = faded.getColorArgb(x, y);
                if (!clip.contains(x, y)) {
                    check((actual & 0xFFFFFF) == 0, at(testCase, "ink escaped clip/parent", x, y));
                    if (channel(original, 0) >= peakBlue - 2) clippedInk++;
                } else {
                    // Screenshots have opaque alpha. On black, C = tint * sourceAlpha;
                    // faded/reference RGB is therefore the effective alpha multiplier.
                    // Identical glyph positions also cancel MSDF edge antialiasing.
                    float expected = expectedFade(fixture, x + 0.5F);
                    for (int shift : new int[]{0, 8, 16}) {
                        check(Math.abs(channel(actual, shift) - channel(original, shift) * expected) <= 2.5F,
                                at(testCase, "RGB/alpha differs from continuous smoothstep (channel " + shift + ")", x, y));
                    }
                    if (channel(original, 0) >= peakBlue - 1) {
                        solid++;
                        for (int shift : new int[]{0, 8, 16}) {
                            float tinted = channel(COLOR, shift) * channel(COLOR, 24) / 255.0F;
                            check(Math.abs(channel(original, shift) - tinted) <= 2,
                                    at(testCase, "reference retains requested translucent RGB tint", x, y));
                        }
                    }
                }

                // This text was submitted immediately after the label, at the same X
                // but an integer-pixel Y offset. Leaked fade uniforms affect it too.
                int plainOriginal = reference.getColorArgb(x, y + PLAIN_STEP);
                int plainActual = faded.getColorArgb(x, y + PLAIN_STEP);
                for (int shift : new int[]{0, 8, 16}) {
                    check(Math.abs(channel(plainActual, shift) - channel(plainOriginal, shift)) <= 1,
                            at(testCase, "post-label plain text inherited fade or scissor", x, y + PLAIN_STEP));
                }
                if (!clip.contains(x, y) && channel(plainOriginal, 0) >= peakBlue - 2) unchangedPlainInk++;
            }
        }
        check(solid > 100, testCase.name() + ": real, fully covered glyph interiors were sampled");
        check(clippedInk > 20, testCase.name() + ": clip assertions cut actual reference glyphs, not empty space");
        check(unchangedPlainInk > 20, testCase.name() + ": plain text proves scissor restoration outside label clip");
        if (!testCase.parent()) {
            if (testCase.leftStrength() > 0) verifyRamp(fixture, reference, faded, true, peakBlue);
            if (testCase.rightStrength() > 0) verifyRamp(fixture, reference, faded, false, peakBlue);
        }
    }

    private static void verifyRamp(FixtureScreen fixture, NativeImage reference, NativeImage faded,
                                   boolean left, int peakBlue) {
        Clip viewport = fixture.viewport;
        float fadeWidth = SettingLabelState.FADE_WIDTH * PIXEL_SCALE;
        int columns = 0;
        Set<Integer> levels = new HashSet<>();
        float previous = Float.NaN;
        for (int x = viewport.left(); x < viewport.right(); x++) {
            float distance = left ? x + 0.5F - viewport.left() : viewport.right() - x - 0.5F;
            if (distance < 0 || distance >= fadeWidth) continue;
            int bestY = viewport.top(), bestBlue = 0;
            for (int y = viewport.top(); y < viewport.bottom(); y++) {
                int blue = channel(reference.getColorArgb(x, y), 0);
                if (blue > bestBlue) {
                    bestBlue = blue;
                    bestY = y;
                }
            }
            // Reject AA edges/empty columns, so quantization and coverage cannot
            // manufacture apparent extra levels in an old eight-band renderer.
            if (bestBlue < peakBlue - 1) continue;
            float ratio = channel(faded.getColorArgb(x, bestY), 0) / (float) bestBlue;
            check(Math.abs(ratio - expectedFade(fixture, x + 0.5F)) <= 0.018F,
                    at(fixture.testCase, "interior alpha ratio does not follow smoothstep", x, bestY));
            if (!Float.isNaN(previous)) {
                check(left ? ratio >= previous - 0.013F : ratio <= previous + 0.013F,
                        at(fixture.testCase, "fade ramp is not monotonic", x, bestY));
            }
            previous = ratio;
            levels.add(Math.round(ratio * 200));
            columns++;
        }
        check(columns >= 18, fixture.testCase.name() + ": sufficient solid columns across " + (left ? "left" : "right") + " ramp");
        check(levels.size() >= 13, fixture.testCase.name() + ": " + (left ? "left" : "right")
                + " fade has " + levels.size() + " levels; eight repeated alpha bands must fail");
    }

    private static float expectedFade(FixtureScreen fixture, float pixelX) {
        float fadeWidth = SettingLabelState.FADE_WIDTH * PIXEL_SCALE;
        float left = smoothstep((pixelX - fixture.viewport.left()) / fadeWidth);
        float right = smoothstep((fixture.viewport.right() - pixelX) / fadeWidth);
        return (1 - fixture.testCase.leftStrength() * (1 - left))
                * (1 - fixture.testCase.rightStrength() * (1 - right));
    }

    private static float smoothstep(float value) {
        float t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }

    private static int channel(int color, int shift) { return (color >>> shift) & 255; }

    private static String at(Case testCase, String message, int x, int y) {
        return testCase.name() + ": " + message + " at framebuffer " + x + "," + y;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private enum Route { DIRECT, MSDF, ADAPTER }

    private record Case(String name, Route route, boolean tail, boolean parent,
                        float leftStrength, float rightStrength) {}

    private record Clip(int left, int top, int right, int bottom) {
        boolean contains(int x, int y) { return x >= left && x < right && y >= top && y < bottom; }
    }

    private static final class FixtureScreen extends Screen {
        private final Case testCase;
        private final SettingLabel label = new SettingLabel();
        private final MsdfGuiFontRenderer adapter;
        private final MsdfFont font;
        private Clip viewport, parent;
        private boolean reference = true;

        private FixtureScreen(Case testCase) {
            super(Text.literal("Continuous settings label fade fixture"));
            this.testCase = testCase;
            adapter = GuiFonts.getSize((int) SIZE * 2);
            font = testCase.route() == Route.ADAPTER ? adapter.msdfFont() : MsdfFonts.bold();
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            check(!MsdfRenderer.isUsingFallback(), testCase.name() + ": real MSDF shader required");
            context.fill(0, 0, width, height, 0xFF000000);
            context.draw();
            float[] shaderColor = RenderSystem.getShaderColor().clone();
            MatrixStack matrices = context.getMatrices();
            matrices.push();
            try {
                RenderSystem.setShaderColor(1, 1, 1, 1);
                double guiScale = client.getWindow().getScaleFactor();
                int scale = (int) guiScale;
                check(scale >= 1 && Math.abs(guiScale - scale) < 0.0001,
                        "fixture requires the integer GUI scale used by ScissorManager");
                // Align scissor bounds to GUI pixels, but scale the glyphs/fade by
                // a fractional framebuffer factor. GlobalPos is CPU-transformed
                // GUI space, not framebuffer space or untransformed text space.
                int left = aligned(32, scale), top = aligned(24, scale);
                int pixelWidth = aligned(324, scale), pixelHeight = aligned(80, scale);
                viewport = new Clip(left, top, left + pixelWidth, top + pixelHeight);
                parent = new Clip(left + aligned(18, scale), top + aligned(32, scale),
                        viewport.right() - aligned(18, scale), top + aligned(48, scale));
                check(client.getWindow().getFramebufferWidth() > viewport.right() + GUARD
                                && client.getWindow().getFramebufferHeight() > viewport.bottom() + PLAIN_STEP + GUARD,
                        "settings fade fixture needs at least " + (viewport.right() + GUARD + 1)
                                + "x" + (viewport.bottom() + PLAIN_STEP + GUARD + 1) + " framebuffer pixels");
                matrices.translate(left / (float) scale, top / (float) scale, 0);
                matrices.scale(PIXEL_SCALE / scale, PIXEL_SCALE / scale, 1);
                float labelWidth = pixelWidth / PIXEL_SCALE;
                float labelHeight = pixelHeight / PIXEL_SCALE;
                float textWidth = testCase.route() == Route.ADAPTER ? adapter.getStringWidth(TEXT)
                        : font.getWidth(TEXT, SIZE) + (TEXT.length() - 1) * 0.025F * SIZE;
                long now = System.nanoTime();
                label.state().layout(TEXT, textWidth, labelWidth, labelHeight, now);
                check(label.state().isOverflowing(), "fixture text must overflow");
                float textX = testCase.route() == Route.DIRECT ? -8 : testCase.tail() ? -label.state().getOverflow() : 0;
                if (reference) {
                    MsdfRenderer.renderText(font, TEXT, SIZE, COLOR, matrices.peek().getPositionMatrix(), textX, TEXT_Y, 0);
                } else {
                    renderFaded(context, textX, labelWidth, labelHeight, scale, now);
                }

                // No intervening MSDF draw may reset the fade before this probe.
                MsdfRenderer.renderText(font, TEXT, SIZE, COLOR, matrices.peek().getPositionMatrix(),
                        0, TEXT_Y + PLAIN_STEP / PIXEL_SCALE, 0);
                check(!MsdfRenderer.isUsingFallback(), testCase.name() + ": drawing did not trigger fallback");
            } finally {
                matrices.pop();
                RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            }
        }

        private void renderFaded(DrawContext context, float textX, float labelWidth, float labelHeight,
                                 int guiScale, long now) {
            MatrixStack matrices = context.getMatrices();
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            ScissorManager scissor = Main.getInstance().getScissorManager();
            if (testCase.parent()) {
                // Parent is deliberately narrower AND shorter than the label.
                scissor.push(new Matrix4f(), parent.left() / (float) guiScale, parent.top() / (float) guiScale,
                        (parent.right() - parent.left()) / (float) guiScale,
                        (parent.bottom() - parent.top()) / (float) guiScale);
            }
            try {
                if (testCase.route() == Route.DIRECT) {
                    scissor.push(matrix, 0, 0, labelWidth, labelHeight);
                    try {
                        MsdfRenderer.renderTextFaded(font, TEXT, SIZE, COLOR, matrix, textX, TEXT_Y, 0,
                                viewport.left() / (float) guiScale, viewport.right() / (float) guiScale,
                                SettingLabelState.FADE_WIDTH * PIXEL_SCALE / guiScale,
                                testCase.leftStrength(), testCase.rightStrength());
                    } finally {
                        scissor.pop();
                    }
                } else {
                    if (testCase.tail()) {
                        // Re-arm inside the stable tail hold on every frame; no
                        // wall-clock sleeps, reflection, or marquee timing races.
                        label.state().updateHover(false, now);
                        label.state().updateHover(true, now - SettingLabelState.TRAVEL_NANOS
                                - SettingLabelState.HOLD_NANOS / 2);
                    }
                    if (testCase.route() == Route.ADAPTER) {
                        label.render(matrices, adapter, TEXT, 0, TEXT_Y + 1.85F,
                                labelWidth, 0, labelHeight, COLOR);
                    } else {
                        label.renderMsdf(matrices, TEXT, SIZE, 0, TEXT_Y, labelWidth, 0, labelHeight, COLOR);
                    }
                }
                if (testCase.parent()) {
                    // A child pop must restore its parent, not disable scissoring.
                    // This magenta guard is outside the parent but inside our scan.
                    context.fill(-2, 0, 0, 3, 0xFFFF00FF);
                    context.draw();
                }
            } finally {
                if (testCase.parent()) scissor.pop();
            }
        }

        private static int aligned(int pixels, int guiScale) {
            return (int) Math.ceil(pixels / (double) guiScale) * guiScale;
        }
    }
}
