package vorga.phazeclient.implement.features.modules.other;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.render.RenderLayer;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.implement.features.modules.client.Theme;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.components.implement.settings.AbstractSettingComponent;

/** Real rendered layout and glint texture-matrix test, in the isolated test client. */
public final class GlintClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        context.runOnClient(client -> {
            Theme.getInstance().language.setSelected(Lang.RU);
            GlintColorizer glint = GlintColorizer.getInstance();
            glint.setStateSilent(true);
            glint.mode.setSelected("Custom");
            MenuScreen.INSTANCE.openGui();
            MenuScreen.INSTANCE.openModuleDetail(glint);
        });
        context.waitTicks(8);
        context.runOnClient(client -> checkLayout(true));
        System.out.println("Glint custom layout screenshot: " + context.takeScreenshot("glint-custom-layout"));
        context.runOnClient(client -> GlintColorizer.getInstance().mode.setSelected("Ruby"));
        context.waitTicks(8);
        context.runOnClient(client -> checkLayout(false));
        System.out.println("Glint preset layout screenshot: " + context.takeScreenshot("glint-preset-layout"));

        // Measure the actual render phase's scrolling transform, not Rainbow hue.
        double slow = measureRate(context, 0.2f);
        double fast = measureRate(context, 4.0f);
        check(slow > 0 && fast / slow > 12 && fast / slow < 28,
                "texture movement scales about 20x from 0.2 to 4: slow=" + slow + ", fast=" + fast);
        context.runOnClient(client -> {
            GlintColorizer.getInstance().mode.setSelected("Custom");
            GlintColorizer.getInstance().speed.setValue(1.0f);
            client.setScreen(new TitleScreen());
        });
        System.out.println("Glint client smoke PASSED: measured speed ratio=" + fast / slow);
    }

    private static double measureRate(ClientGameTestContext context, float speed) {
        context.runOnClient(client -> {
            GlintColorizer.getInstance().speed.setValue(speed);
            sample(); // Observe the new slider value before measuring its interval.
        });
        double[] before = context.computeOnClient(client -> new double[]{sample(), System.nanoTime()});
        context.waitTicks(5);
        double[] after = context.computeOnClient(client -> new double[]{sample(), System.nanoTime()});
        double delta = (after[0] - before[0] + 1.0) % 1.0;
        return delta / ((after[1] - before[1]) / 1_000_000_000.0);
    }

    private static float sample() {
        RenderLayer layer = RenderLayer.getGlint();
        layer.startDrawing();
        try {
            return RenderSystem.getTextureMatrix().m31();
        } finally {
            layer.endDrawing();
        }
    }

    private static void checkLayout(boolean custom) {
        var glint = GlintColorizer.getInstance();
        var detail = MenuScreen.INSTANCE.getModuleDetailComponent();
        check(detail.getModule() == glint, "Custom Glint detail is open");
        AbstractSettingComponent general = detail.getSettingComponents().stream().filter(c -> c.getSetting() == glint.generalSection).findFirst().orElseThrow();
        AbstractSettingComponent mode = detail.getSettingComponents().stream().filter(c -> c.getSetting() == glint.mode).findFirst().orElseThrow();
        AbstractSettingComponent color = detail.getSettingComponents().stream().filter(c -> c.getSetting() == glint.customColor).findFirst().orElseThrow();
        AbstractSettingComponent speed = detail.getSettingComponents().stream().filter(c -> c.getSetting() == glint.speed).findFirst().orElseThrow();
        check(general.y < mode.y && Math.abs(general.x - mode.x) < 0.1f,
                "General section precedes the controls");
        check(general.width > mode.width * 1.9f, "General section spans both columns");
        check(glint.customColor.isVisible() == custom, "color visible only in Custom");
        if (custom) {
            check(Math.abs(mode.y - color.y) < 0.1f && color.x > mode.x, "Mode/Color share first row");
            check(speed.y > mode.y && Math.abs(speed.x - mode.x) < 0.1f, "Speed starts on second row");
            check(speed.width > mode.width * 1.9f, "Speed is full width in Custom");
        } else {
            check(Math.abs(mode.y - speed.y) < 0.1f && speed.x > mode.x, "Mode/Speed share first row in presets");
            check(Math.abs(speed.width - mode.width) < 0.1f, "both preset controls are half width");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
