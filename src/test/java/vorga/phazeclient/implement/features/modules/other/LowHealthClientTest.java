package vorga.phazeclient.implement.features.modules.other;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.Text;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.implement.features.modules.client.Theme;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.components.implement.settings.AbstractSettingComponent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Real GUI/framebuffer regressions in the isolated Fabric client test runner. */
public final class LowHealthClientTest implements FabricClientGameTest {
    private static final int BLUE = 0xFF0000FF;
    private static final int RED = 0xFFFF0000;
    private static final float OPACITY = 0.75f;
    // Allow texture interpolation, GUI scaling and 8-bit framebuffer rounding.
    private static final int PIXEL_TOLERANCE = 6;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        SavedState saved = context.computeOnClient(client -> new SavedState(
                Theme.getInstance().language.getSelected(), Lang.getActive(),
                LowHealthWarning.getInstance().heartbeatSound.isValue(),
                client.getWindow().getWidth(), client.getWindow().getHeight()));
        try {
            context.runOnClient(client -> {
                check(client.player == null && client.world == null, "vignette fixture runs without a player or health state");
                LowHealthSettingsChecks.main(new String[0]);
                client.setScreen(new VignetteScreen());
            });
            resize(context, 854, 480);
            int[] smallBlue = capture(context, "low-health-vignette-blue", BLUE);
            capture(context, "low-health-vignette-red", RED);

            // Resize the existing screen, rather than creating a fresh render fixture.
            resize(context, 1280, 720);
            capture(context, "low-health-vignette-red-1280x720", RED);
            int[] largeBlue = capture(context, "low-health-vignette-blue-1280x720", BLUE);
            compareProfiles(smallBlue, largeBlue, "854x480 -> 1280x720");
            resize(context, 854, 480);
            compareProfiles(smallBlue, capture(context, "low-health-vignette-blue-resized-back", BLUE), "resize back to 854x480");

            resize(context, 1280, 720);
            checkSettingsPanel(context);
            checkThreeDSkinsPanel(context);
            context.runOnClient(client -> MenuScreen.INSTANCE.openModuleDetail(KillSounds.getInstance()));
            context.waitTicks(8);
            System.out.println("Kill sounds settings screenshot: " + context.takeScreenshot("kill-sounds-general-ru"));
        } catch (IOException e) {
            throw new RuntimeException("Could not decode low health regression screenshot", e);
        } finally {
            try {
                context.getInput().resizeWindow(saved.width(), saved.height());
            } finally {
                context.runOnClient(client -> {
                    LowHealthWarning.getInstance().heartbeatSound.setValue(saved.heartbeat());
                    Theme.getInstance().language.setSelected(saved.language());
                    Lang.setActive(saved.activeLanguage());
                    MenuScreen.INSTANCE.closeModuleDetail();
                    client.setScreen(new TitleScreen());
                });
            }
        }
        context.waitForScreen(TitleScreen.class);
        System.out.println("Low health client regressions PASSED");
    }

    private static void resize(ClientGameTestContext context, int width, int height) {
        context.getInput().resizeWindow(width, height);
        context.waitFor(client -> client.getWindow().getWidth() == width && client.getWindow().getHeight() == height);
        context.waitTicks(4);
    }

    private static int[] capture(ClientGameTestContext context, String name, int tint) throws IOException {
        context.runOnClient(client -> {
            check(client.currentScreen instanceof VignetteScreen, "isolated vignette screen remains open after resize");
            ((VignetteScreen) client.currentScreen).tint = tint;
        });
        context.waitTicks(3);
        int[] framebuffer = context.computeOnClient(client -> new int[]{
                client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight()});
        Path screenshot = context.takeScreenshot(name);
        System.out.println("Low health vignette screenshot: " + screenshot);
        try (var input = Files.newInputStream(screenshot); NativeImage image = NativeImage.read(input)) {
            check(image.getWidth() == framebuffer[0] && image.getHeight() == framebuffer[1],
                    name + ": screenshot covers the current framebuffer");
            check(image.getWidth() >= 854 && image.getHeight() >= 480, name + ": fixture has the requested minimum resolution");
            for (double x : new double[]{0.45, 0.5, 0.55}) {
                for (double y : new double[]{0.45, 0.5, 0.55}) {
                    int pixel = image.getColorArgb((int) (x * (image.getWidth() - 1)), (int) (y * (image.getHeight() - 1)));
                    check(channel(pixel, 16) >= 252 && channel(pixel, 8) >= 252 && channel(pixel, 0) >= 252,
                            name + ": transparent center leaves the white background unchanged");
                }
            }
            checkAxis(image, true, tint, name);
            checkAxis(image, false, tint, name);
            // Also cover off-axis pixels/corners, where a black mask or bad tint can hide.
            for (int y = 0; y <= 12; y++) {
                for (int x = 0; x <= 12; x++) {
                    checkTint(image.getColorArgb(x * (image.getWidth() - 1) / 12,
                            y * (image.getHeight() - 1) / 12), tint, name + ": grid " + x + "," + y);
                }
            }
            int[] profile = new int[65];
            for (int i = 0; i < profile.length; i++) {
                double distance = i / 64.0;
                int x = (int) Math.round((image.getWidth() - 1) * (0.5 + distance * 0.5));
                int y = (int) Math.round((image.getHeight() - 1) * (0.5 + distance * 0.5));
                int horizontal = alphaByte(image.getColorArgb(x, image.getHeight() / 2));
                int vertical = alphaByte(image.getColorArgb(image.getWidth() / 2, y));
                check(Math.abs(horizontal - vertical) <= 10, name + ": gradient scales evenly on both axes at " + distance);
                profile[i] = horizontal;
            }
            return profile;
        }
    }

    private static void checkAxis(NativeImage image, boolean horizontal, int tint, String name) {
        String axis = horizontal ? "horizontal" : "vertical";
        int length = horizontal ? image.getWidth() : image.getHeight();
        int middle = (length - 1) / 2;
        int maxLow = 0;
        int maxHigh = 0;
        int gradientSteps = 0;
        int previous = 0;
        for (int offset = 0; offset <= middle; offset++) {
            int low = middle - offset;
            int high = length - 1 - low;
            int lowPixel = image.getColorArgb(horizontal ? low : image.getWidth() / 2,
                    horizontal ? image.getHeight() / 2 : low);
            int highPixel = image.getColorArgb(horizontal ? high : image.getWidth() / 2,
                    horizontal ? image.getHeight() / 2 : high);
            String position = name + ": " + axis + " offset " + offset;
            checkTint(lowPixel, tint, position);
            checkTint(highPixel, tint, position);
            int lowAlpha = alphaByte(lowPixel);
            int highAlpha = alphaByte(highPixel);
            check(Math.abs(lowAlpha - highAlpha) <= PIXEL_TOLERANCE, position + ": opposite edges are symmetric");
            // Compare against the entire preceding ray, not just adjacent pixels:
            // a gradual periodic reset must fail too (the old 64px texture tiling bug).
            check(lowAlpha + PIXEL_TOLERANCE >= maxLow && highAlpha + PIXEL_TOLERANCE >= maxHigh,
                    position + ": opacity increases monotonically toward each edge; no repeated tiles");
            maxLow = Math.max(maxLow, lowAlpha);
            maxHigh = Math.max(maxHigh, highAlpha);
            if (lowAlpha > previous) gradientSteps++;
            previous = lowAlpha;
        }
        check(gradientSteps >= 12, name + ": " + axis + " has a real gradient, not a solid frame");
        check(Math.abs(maxLow - Math.round(OPACITY * 255)) <= 10
                        && Math.abs(maxHigh - Math.round(OPACITY * 255)) <= 10,
                name + ": " + axis + " edge alpha is the requested .75 using normal blending ("
                        + maxLow + ", " + maxHigh + ")");
    }

    private static void checkTint(int pixel, int tint, String message) {
        int retained = channel(pixel, tint == BLUE ? 0 : 16);
        int other = channel(pixel, tint == BLUE ? 16 : 0);
        int green = channel(pixel, 8);
        check(retained >= 250, message + ": selected pure color channel remains bright over white");
        check(Math.abs(other - green) <= 3, message + ": untinted channels have equal alpha attenuation");
        check(green >= 50, message + ": .75 opacity does not produce an opaque/black edge");
    }

    private static int channel(int argb, int shift) {
        return (argb >>> shift) & 255;
    }

    private static int alphaByte(int argb) {
        // Neither pure blue nor pure red contains green: output G = 255 * (1 - alpha).
        return 255 - channel(argb, 8);
    }

    private static void compareProfiles(int[] before, int[] after, String transition) {
        for (int i = 0; i < before.length; i++) {
            check(Math.abs(before[i] - after[i]) <= 10, transition + ": normalized gradient remains stable at sample " + i);
        }
    }

    private static void checkSettingsPanel(ClientGameTestContext context) {
        context.runOnClient(client -> {
            Theme.getInstance().language.setSelected(Lang.RU);
            Theme.getInstance().syncLanguage();
            LowHealthWarning warning = LowHealthWarning.getInstance();
            warning.heartbeatSound.setValue(true);
            MenuScreen.INSTANCE.openGui();
            MenuScreen.INSTANCE.openModuleDetail(warning);
        });
        context.waitTicks(8);
        context.runOnClient(client -> checkLayout(true));
        System.out.println("Low health settings screenshot: " + context.takeScreenshot("low-health-settings"));
        float[] shown = context.computeOnClient(client -> new float[]{
                component(LowHealthWarning.getInstance().colorSection).y,
                component(LowHealthWarning.getInstance().soundVolume).getExpandedHeight()});
        context.runOnClient(client -> LowHealthWarning.getInstance().heartbeatSound.setValue(false));
        context.waitTicks(8);
        context.runOnClient(client -> {
            checkLayout(false);
            check(Math.abs(component(LowHealthWarning.getInstance().colorSection).y - (shown[0] - shown[1] - 8)) < 0.1f,
                    "hidden volume releases its entire row and gap in the rendered layout");
        });
        context.runOnClient(client -> LowHealthWarning.getInstance().heartbeatSound.setValue(true));
        context.waitTicks(8);
        context.runOnClient(client -> {
            checkLayout(true);
            check(Math.abs(component(LowHealthWarning.getInstance().colorSection).y - shown[0]) < 0.1f,
                    "volume reappears in its original row without reopening detail");
        });
    }

    private static void checkThreeDSkinsPanel(ClientGameTestContext context) {
        context.runOnClient(client -> MenuScreen.INSTANCE.openModuleDetail(ThreeDSkins.getInstance()));
        context.waitTicks(8);
        context.runOnClient(client -> {
            ThreeDSkins skins = ThreeDSkins.getInstance();
            var detail = MenuScreen.INSTANCE.getModuleDetailComponent();
            check(skins.voxelsSection.getName().equals("Настройки вокселей")
                    && skins.visibleSection.getName().equals("Видимость"), "3D Skins section names are localized");
            for (var setting : List.of(skins.voxelThickness, skins.maxDistance, skins.showOthers)) {
                var row = component(setting);
                check(Math.abs(row.width - (detail.contentWidth() - 28)) < 0.1F,
                        setting.getRawName() + ": rendered row spans full width");
                check(AbstractSettingComponent.ROW_HEIGHT == 23.5F, "standard setting height is 23.5 pixels");
                check(row.height == AbstractSettingComponent.ROW_HEIGHT,
                        setting.getRawName() + ": checkbox and sliders have the shared fractional height");
                check(!setting.getLocalizedName().equals(setting.getRawName()), "3D Skins setting name is translated");
            }
            check(component(skins.voxelThickness).y < component(skins.maxDistance).y
                    && component(skins.maxDistance).y < component(skins.visibleSection).y
                    && component(skins.visibleSection).y < component(skins.showOthers).y
                    && component(skins.showOthers).y < component(skins.hat3D).y,
                    "3D Skins voxel rows precede Visible with Show Others first");
            check(component(skins.hat3D).y == component(skins.jacket3D).y
                    && component(skins.sleeves3D).y == component(skins.pants3D).y,
                    "remaining Visible toggles use paired columns");
        });
        System.out.println("3D Skins settings screenshot: " + context.takeScreenshot("3d-skins-categories-ru"));
    }

    private static void checkLayout(boolean heartbeat) {
        LowHealthWarning warning = LowHealthWarning.getInstance();
        var detail = MenuScreen.INSTANCE.getModuleDetailComponent();
        check(detail.getModule() == warning, "Low HP Warning detail is open");
        check(detail.getSettingComponents().stream().map(AbstractSettingComponent::getSetting).toList()
                .equals(LowHealthSettingsChecks.orderedSettings(warning)), "rendered component order follows General, Audio, Color");
        check(warning.generalSection.getName().equals("Общие") && warning.soundSection.getName().equals("Звук")
                && warning.colorSection.getName().equals("Цвет"), "detail sections use the Russian Lang labels");
        check(warning.soundVolume.isVisible() == heartbeat, "rendered volume visibility follows heartbeat");
        List<AbstractSettingComponent> visible = detail.getSettingComponents().stream()
                .filter(c -> c.getSetting().isVisible()).toList();
        check(visible.size() == (heartbeat ? 8 : 7), "only volume disappears when heartbeat is disabled");
        float previousBottom = Float.NEGATIVE_INFINITY;
        for (AbstractSettingComponent row : visible) {
            // The detail panel has 12px outer padding and 2px inner padding per side.
            check(Math.abs(row.x - (detail.contentX() + 14)) < 0.1f
                            && Math.abs(row.width - (detail.contentWidth() - 28)) < 0.1f,
                    row.getSetting().getRawName() + ": actual component spans the full settings panel");
            check(row.y >= previousBottom, row.getSetting().getRawName() + ": rows and section headers do not share columns or overlap");
            if (!(row.getSetting() instanceof vorga.phazeclient.api.feature.module.setting.implement.SectionSetting)) {
                check(row.height == AbstractSettingComponent.ROW_HEIGHT,
                        row.getSetting().getRawName() + ": standard Low HP setting uses the shared row height");
            }
            previousBottom = row.y + row.getExpandedHeight();
        }
    }

    private static AbstractSettingComponent component(vorga.phazeclient.api.feature.module.setting.Setting setting) {
        return MenuScreen.INSTANCE.getModuleDetailComponent().getSettingComponents().stream()
                .filter(c -> c.getSetting() == setting).findFirst().orElseThrow();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record SavedState(String language, String activeLanguage, boolean heartbeat, int width, int height) {}

    private static final class VignetteScreen extends Screen {
        private int tint = BLUE;

        private VignetteScreen() {
            super(Text.literal("Low health vignette regression fixture"));
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), 0xFFFFFFFF);
            LowHealthWarning.drawVignette(context, tint, OPACITY);
        }

        @Override
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
            // Deliberately no panorama, blur, widgets or world behind the white reference.
        }
    }
}
