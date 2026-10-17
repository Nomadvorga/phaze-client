package vorga.phazeclient.implement.menu.components.implement.module;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.Text;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.implement.window.AbstractWindow;
import vorga.phazeclient.implement.menu.components.implement.window.WindowManager;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Opt-in real card pixels/input; the main suite owns fabric-client-gametest registration. */
public final class ModuleCardGeometryClientTest implements FabricClientGameTest {
    private static final int PIXEL_SCALE = 2;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        SavedState saved = context.computeOnClient(client -> new SavedState(client.currentScreen,
                MenuScreen.INSTANCE.getModuleDetailComponent().getModule(),
                MenuScreen.INSTANCE.getModuleDetailComponent().scroll,
                MenuScreen.INSTANCE.getModuleDetailComponent().smoothedScroll,
                new ArrayList<>(WindowManager.INSTANCE.getWindows())));
        FixtureScreen fixture = context.computeOnClient(client -> new FixtureScreen());
        try {
            context.runOnClient(client -> {
                WindowManager.INSTANCE.clear();
                client.setScreen(fixture);
            });
            for (boolean batched : new boolean[]{false, true}) {
                context.runOnClient(client -> fixture.batched = batched);
                context.waitTicks(1);
                Path screenshot = context.takeScreenshot("module-card-geometry-" + (batched ? "batched" : "eager"));
                try (var input = Files.newInputStream(screenshot); NativeImage pixels = NativeImage.read(input)) {
                    verifyPixels(fixture, pixels);
                }
            }
            context.runOnClient(client -> {
                for (Card card : fixture.cards) verifyGeometryAndInput(card);
            });
            System.out.println("Module card geometry/pixel/input checks PASSED");
        } catch (IOException e) {
            throw new RuntimeException("Could not read module card fixture screenshot", e);
        } finally {
            context.runOnClient(client -> {
                WindowManager.INSTANCE.clear();
                MenuScreen.INSTANCE.closeModuleDetail();
                if (saved.detail() != null) MenuScreen.INSTANCE.openModuleDetail(saved.detail());
                MenuScreen.INSTANCE.getModuleDetailComponent().scroll = saved.scroll();
                MenuScreen.INSTANCE.getModuleDetailComponent().smoothedScroll = saved.smoothedScroll();
                saved.windows().forEach(WindowManager.INSTANCE::add);
                client.setScreen(saved.screen());
            });
        }
    }

    private static void verifyPixels(FixtureScreen fixture, NativeImage pixels) {
        check(pixels.getWidth() >= 752 && pixels.getHeight() >= 412, "all fixture cards and guards fit framebuffer");
        for (Card card : fixture.cards) {
            ModuleComponent row = card.component;
            if (card.settings) {
                float optionsY = number(row, "getOptionsRowY");
                // Both probes lie inside the newly added 1px side bands on inset rows,
                // away from text, the divider, the top rule and rounded bottom corners.
                color(pixels, row.x + 1.5F, optionsY + 7, MenuStyle.CARD_OPTIONS, card, "settings left expansion");
                color(pixels, row.x + row.width - 1.5F, optionsY + 7, MenuStyle.CARD_OPTIONS, card, "settings right expansion");
            }
            if (card.module.isShowEnable()) {
                int expected = card.module.isState() ? MenuStyle.ACCENT_GREEN : 0xFFA01E40;
                float stateY = number(row, "getStateRowY");
                color(pixels, row.x + 1.5F, stateY + 7, expected, card, "state left expansion");
                color(pixels, row.x + row.width - 1.5F, stateY + 7, expected, card, "state right expansion");
                // Last two framebuffer pixels are the added logical pixel, not the
                // old 17px fill or the outline. The center avoids text and corner AA.
                color(pixels, row.x + row.width / 2, stateY + 17.25F, expected, card, "state extra bottom pixel");
            }
            // No card/strip pixels may escape into the next grid row. The outline
            // softness can occupy a one-pixel fringe, so leave a two-pixel guard.
            for (float dx : new float[]{-2, row.width / 2, row.width + 2}) {
                int pixel = pixels.getColorArgb(pixel(row.x + dx), pixel(row.y + row.getComponentHeight() + 3));
                check((pixel & 0xFFFFFF) == 0, card.name + ": card does not spill into the following row");
            }
        }
    }

    private static void verifyGeometryAndInput(Card card) {
        ModuleComponent row = card.component;
        boolean state = card.module.isShowEnable();
        float optionsY = number(row, "getOptionsRowY");
        float stateY = number(row, "getStateRowY");
        float rowX = number(row, "getRowX", !state);
        float rowWidth = number(row, "getRowWidth", !state);
        near(rowX, row.x + (state ? 0.3F : 0), card.name + ": inset is 0.3px, standalone stays full width");
        near(rowWidth, row.width - (state ? 0.6F : 0), card.name + ": inset strips gain 2px total width");
        near(row.height, row.getComponentHeight(), card.name + ": rendered/cache bounds use layout height");
        near(row.height, state ? 86 : card.settings ? 68 : 62, card.name + ": existing card shapes keep their layout pitch");
        float savedY = row.y;
        for (float fraction : new float[]{0.125F, 0.3F, 0.7F}) {
            row.y = savedY + fraction;
            near(row.getComponentHeight(), row.height, card.name + ": fractional scrolling cannot inflate layout height");
        }
        row.y = savedY;

        for (boolean options : new boolean[]{true, false}) {
            boolean present = options ? card.settings : state;
            float top = options ? optionsY : stateY;
            float left = number(row, "getRowX", options && !state);
            float width = number(row, "getRowWidth", options && !state);
            double middleY = top + 7;
            String helper = options ? "isOptionsRowHovered" : "isStateRowHovered";
            if (present) {
                var animation = options ? row.getOptionsHoverAnimation() : row.getStateRowHoverAnimation();
                invoke(row, "preUpdateAnimations", new Class<?>[]{int.class, int.class, boolean.class},
                        (int) Math.ceil(left), (int) middleY, false);
                check(animation.isDirection(Direction.FORWARDS), card.name + ": expanded edge drives real hover animation");
                invoke(row, "preUpdateAnimations", new Class<?>[]{int.class, int.class, boolean.class},
                        (int) Math.floor(left) - 1, (int) middleY, false);
                check(animation.isDirection(Direction.BACKWARDS), card.name + ": outside edge reverses real hover animation");
            }
            for (double x : new double[]{left + 0.05, left + width - 0.05}) {
                check(hover(row, helper, x, middleY) == present, card.name + ": row hover includes both expanded edges");
                for (int button : new int[]{0, 1}) {
                    int toggles = card.module.toggles;
                    boolean handled = row.mouseClicked(x, middleY, button);
                    check(handled == (present && (options || button == 0)), card.name + ": edge click routes only supported buttons");
                    check(card.module.toggles == toggles + (present && !options && button == 0 ? 1 : 0),
                            card.name + ": state edge toggles exactly once; options never toggles");
                    if (present && options) {
                        check(MenuScreen.INSTANCE.getModuleDetailComponent().getModule() == card.module,
                                card.name + ": settings edge opens the correct fixture detail");
                        MenuScreen.INSTANCE.closeModuleDetail();
                    }
                }
            }
            for (double x : new double[]{left - 0.05, left + width + 0.05}) {
                check(!hover(row, helper, x, middleY), card.name + ": hover excludes space beyond the painted width");
                check(!row.mouseClicked(x, middleY, 0), card.name + ": outside horizontal edge is not clickable");
            }
            check(!hover(row, helper, left + width / 2, top - 0.05), card.name + ": row hover excludes its top exterior");
            check(!hover(row, helper, left + width / 2, top + 18), card.name + ": row bottom is half-open");
            if (present && (!options || !state)) {
                check(!hover(row, helper, left + 0.05, top + 17.95), card.name + ": transparent rounded corner does not hover");
                check(!row.mouseClicked(left + 0.05, top + 17.95, 0), card.name + ": transparent rounded corner does not click");
            }
        }
        if (state) {
            near(optionsY + 18, stateY, card.name + ": settings and state share a seam without overlap");
            near(stateY + 18, row.y + row.getComponentHeight(), card.name + ": layout includes the extended state bottom");
            double center = row.x + row.width / 2;
            check(!hover(row, "isOptionsRowHovered", center, stateY), card.name + ": seam is not settings hover");
            check(hover(row, "isStateRowHovered", center, stateY), card.name + ": seam belongs to state hover");
            int toggles = card.module.toggles;
            check(row.mouseClicked(center, stateY, 0), card.name + ": seam is clickable");
            check(card.module.toggles == toggles + 1, card.name + ": seam toggles instead of opening settings");
            check(row.mouseClicked(center, stateY + 17.75, 0), card.name + ": extended bottom pixel is clickable");
            check(card.module.toggles == toggles + 2, card.name + ": extended bottom toggles exactly once");
            check(hover(row, "isStateRowHovered", center, stateY + 17.75), card.name + ": extended bottom also hovers");
            check(!row.mouseClicked(center, stateY + 18.05, 0), card.name + ": below extended bottom does not click");
        }
        // Locked cards consume a valid click but must never change fixture state or open settings.
        card.module.locked = true;
        int toggles = card.module.toggles;
        if (state) check(row.mouseClicked(row.x + row.width / 2, stateY + 17.75, 0), "locked state consumes bottom click");
        if (card.settings) check(row.mouseClicked(row.x + row.width / 2, optionsY + 7, 0), "locked settings consumes click");
        check(card.module.toggles == toggles, "locked card never toggles");
        check(MenuScreen.INSTANCE.getModuleDetailComponent().getModule() == null, "locked card never opens detail");
        card.module.locked = false;
    }

    private static int pixel(float coordinate) { return (int) Math.floor(coordinate * PIXEL_SCALE); }

    private static void color(NativeImage pixels, float x, float y, int expected, Card card, String message) {
        int actual = pixels.getColorArgb(pixel(x), pixel(y));
        // The fixture is rendered through the menu's scaled matrix and the
        // batched rectangle shader, so exact RGB varies with the sampled
        // fractional edge. Geometry/input assertions below own the exact
        // expanded bounds; the pixel probe only verifies the strip is painted
        // rather than transparent/black at the chosen interior sample.
        check((actual & 0xFFFFFF) != 0,
                card.name + ": " + message + " must remain visibly painted (got " + Integer.toHexString(actual) + ")");
    }

    // Inspect the actual geometry/hover helpers without expanding the production API for tests.
    private static Object invoke(ModuleComponent row, String name, Class<?>[] types, Object... args) {
        try {
            Method method = ModuleComponent.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(row, args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot inspect card geometry " + name, e);
        }
    }

    private static float number(ModuleComponent row, String name) {
        return ((Number) invoke(row, name, new Class<?>[0])).floatValue();
    }

    private static float number(ModuleComponent row, String name, boolean standalone) {
        return ((Number) invoke(row, name, new Class<?>[]{boolean.class}, standalone)).floatValue();
    }

    private static boolean hover(ModuleComponent row, String name, double x, double y) {
        return (Boolean) invoke(row, name, new Class<?>[]{double.class, double.class}, x, y);
    }

    private static void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < 0.001F, message + " (expected " + expected + ", got " + actual + ")");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FixtureModule extends Module {
        private int toggles;
        private boolean locked;

        private FixtureModule(String name, boolean stateRow, boolean settings, boolean enabled) {
            super(name, ModuleCategory.OTHER, stateRow, false);
            state = enabled;
            if (settings) {
                BooleanSetting setting = new BooleanSetting("Geometry fixture setting", "");
                setting.setSaveToConfig(false);
                setup(setting);
            }
        }

        // These unregistered fixture modules bypass state listeners, network requests
        // and user-config persistence, while exercising the real card input dispatch.
        @Override public void switchState() { state = !state; toggles++; }
        @Override public boolean isServerLocked() { return locked; }
    }

    private static final class Card {
        private final String name;
        private final boolean settings;
        private final FixtureModule module;
        private final ModuleComponent component;

        private Card(String name, boolean stateRow, boolean settings, boolean enabled, float width, int index) {
            this.name = name;
            this.settings = settings;
            module = new FixtureModule(name, stateRow, settings, enabled);
            component = new ModuleComponent(module);
            component.position(16 + index % 3 * 122, 16 + index / 3 * 102);
            component.size(width, component.getComponentHeight());
        }
    }

    private static final class FixtureScreen extends Screen {
        private final List<Card> cards = List.of(
                new Card("Enabled/settings", true, true, true, 100, 0),
                new Card("Disabled/settings", true, true, false, 104.5F, 1),
                new Card("Settings only", false, true, false, 110.25F, 2),
                new Card("Enabled only", true, false, true, 100.25F, 3),
                new Card("Disabled only", true, false, false, 108, 4),
                new Card("No lower rows", false, false, false, 98, 5));
        private boolean batched;

        private FixtureScreen() { super(Text.literal("Module card geometry fixture")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0xFF000000);
            context.draw();
            float[] shaderColor = RenderSystem.getShaderColor().clone();
            context.getMatrices().push();
            try {
                RenderSystem.setShaderColor(1, 1, 1, 1);
                float scale = (float) (PIXEL_SCALE / client.getWindow().getScaleFactor());
                context.getMatrices().scale(scale, scale, 1);
                if (batched) {
                    BatchedRectangle.beginScope();
                    check(BatchedRectangle.isBatching(), "batched card shader is available");
                }
                try {
                    for (Card card : cards) {
                        // Stable, cursor-independent snapshots, including after test clicks.
                        ModuleComponent row = card.component;
                        row.getHoverAnimation().setDirectionAndFinish(Direction.BACKWARDS);
                        row.getOptionsHoverAnimation().setDirectionAndFinish(Direction.BACKWARDS);
                        row.getStateRowHoverAnimation().setDirectionAndFinish(Direction.BACKWARDS);
                        row.getOutlineColorAnimation().setDirectionAndFinish(card.module.isState() ? Direction.FORWARDS : Direction.BACKWARDS);
                        row.render(context, -1000, -1000, delta);
                    }
                } finally {
                    if (batched) BatchedRectangle.endScope();
                }
            } finally {
                context.getMatrices().pop();
                RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            }
        }
    }

    private record SavedState(Screen screen, Module detail, double scroll, double smoothedScroll,
                              List<AbstractWindow> windows) {}
}
