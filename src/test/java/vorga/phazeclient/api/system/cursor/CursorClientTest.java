package vorga.phazeclient.api.system.cursor;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.feature.module.setting.implement.TextSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.implement.features.modules.other.Animations;
import vorga.phazeclient.implement.menu.MainMenuScreen;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.MenuUiSettings;
import vorga.phazeclient.implement.menu.components.implement.settings.AbstractSettingComponent;
import vorga.phazeclient.implement.menu.components.implement.settings.ValueComponent;

import java.nio.file.Path;
import java.util.function.Consumer;

/** Uses real screen/widget mixins, mouse callbacks and GLFW rather than a mocked cursor manager. */
public final class CursorClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        SavedState saved = context.computeOnClient(client -> {
            check(System.getProperty("fabric.client.gametest") != null
                            && client.runDirectory.toPath().toAbsolutePath().normalize().getParent().getFileName()
                            .equals(Path.of("build")),
                    "cursor integration must use an isolated build/ runner");
            return new SavedState(client.currentScreen, Animations.getInstance().state,
                    Animations.getInstance().dynamicCursor.isValue(), client.mouse.getX(), client.mouse.getY(),
                    listener(Setting.class, "globalChangeListener"), listener(Module.class, "globalStateChangeListener"));
        });
        try {
            context.runOnClient(client -> {
                Setting.setGlobalChangeListener(null);
                Module.setGlobalStateChangeListener(null);
                Animations.getInstance().setStateSilent(true);
                Animations.getInstance().dynamicCursor.setValue(true);
                check(Animations.getInstance().isDynamicCursorEnabled(), "the actual module/toggle gate is enabled");
                client.setScreen(new WidgetScreen());
            });
            hover(context, 72, 42, CursorManager.SHAPE_HAND, "vanilla button");
            hover(context, 72, 70, CursorManager.SHAPE_NOT_ALLOWED, "disabled button");
            hover(context, 72, 98, CursorManager.SHAPE_BEAM, "vanilla text input");
            hover(context, 16, 16, CursorManager.SHAPE_ARROW, "empty screen");
            hover(context, 72, 126, CursorManager.SHAPE_HAND, "vanilla slider hover");
            context.getInput().holdMouse(0);
            renderAndCheck(context, CursorManager.SHAPE_HRESIZE, "vanilla slider press");
            hover(context, 16, 16, CursorManager.SHAPE_HRESIZE, "drag held outside slider");
            context.getInput().releaseMouse(0);
            renderAndCheck(context, CursorManager.SHAPE_ARROW, "slider release");
            hover(context, 72, 42, CursorManager.SHAPE_HAND, "button before scrolling");
            context.getInput().scroll(0, 1);
            context.runOnClient(client -> check((int) field(CursorManager.class, "lastAppliedShape", null)
                    == CursorManager.SHAPE_VRESIZE, "a real wheel callback immediately applies the scroll cursor"));
            context.waitFor(client -> System.nanoTime() >= (long) field(CursorManager.class, "scrollOverrideUntilNanos", null));
            renderAndCheck(context, CursorManager.SHAPE_HAND, "scroll expiry restores hovered button");

            for (boolean disableModule : new boolean[]{false, true}) {
                hover(context, 72, 42, CursorManager.SHAPE_HAND, "enabled before disabling");
                context.runOnClient(client -> {
                    if (disableModule) Animations.getInstance().setStateSilent(false);
                    else Animations.getInstance().dynamicCursor.setValue(false);
                });
                renderAndCheck(context, CursorManager.SHAPE_ARROW, "disabled module/toggle");
                hover(context, 72, 126, CursorManager.SHAPE_ARROW, "disabled slider hover");
                context.getInput().holdMouse(0);
                renderAndCheck(context, CursorManager.SHAPE_ARROW, "disabled slider press");
                context.getInput().releaseMouse(0);
                context.runOnClient(client -> {
                    Animations.getInstance().setStateSilent(true);
                    Animations.getInstance().dynamicCursor.setValue(true);
                });
                hover(context, 72, 42, CursorManager.SHAPE_HAND, "re-enabled button");
            }

            context.runOnClient(client -> client.setScreen(new WidgetScreen()));
            hover(context, 72, 98, CursorManager.SHAPE_BEAM, "screen change");
            mainMenuControls(context);
            menuControls(context);
        } finally {
            context.getInput().releaseMouse(0);
            context.runOnClient(client -> {
                MenuScreen.INSTANCE.closeModuleDetail();
                Animations.getInstance().dynamicCursor.setValue(saved.dynamicCursor());
                Animations.getInstance().setStateSilent(saved.enabled());
                client.setScreen(saved.screen());
                CursorManager.forceArrow();
                Setting.setGlobalChangeListener(saved.settingListener());
                Module.setGlobalStateChangeListener(saved.moduleListener());
            });
            context.getInput().setCursorPos(saved.mouseX(), saved.mouseY());
        }
        System.out.println("Dynamic cursor real-client integration PASSED");
    }

    private static void mainMenuControls(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.setScreen(new MainMenuScreen());
            client.gameRenderer.render(RenderTickCounter.ZERO, true);
        });
        double[] point = context.computeOnClient(client -> {
            ClickableWidget button = client.currentScreen.children().stream()
                    .filter(child -> child instanceof ClickableWidget widget && widget.visible && widget.active
                            && widget.getWidth() >= 96)
                    .map(child -> (ClickableWidget) child).findFirst().orElseThrow();
            double scale = (float) field(MainMenuScreen.class, "overlayRenderScale", client.currentScreen)
                    * client.getWindow().getScaleFactor();
            return new double[]{(button.getX() + button.getWidth() / 2.0) * scale,
                    (button.getY() + button.getHeight() / 2.0) * scale};
        });
        context.getInput().setCursorPos(point[0], point[1]);
        renderAndCheck(context, CursorManager.SHAPE_HAND, "Phaze main menu button");
    }

    private static void menuControls(ClientGameTestContext context) {
        Controls controls = context.computeOnClient(client -> new Controls());
        context.runOnClient(client -> {
            MenuScreen.INSTANCE.openGui();
            MenuScreen.INSTANCE.openModuleDetail(controls);
            MenuScreen.INSTANCE.animation.setDirectionAndFinish(vorga.phazeclient.api.system.animation.Direction.FORWARDS);
            for (int frame = 0; frame < 8; frame++) client.gameRenderer.render(RenderTickCounter.ZERO, true);
        });
        AbstractSettingComponent text = context.computeOnClient(client -> row(controls.text));
        menuHover(context, text.x + text.width - 32, text.y + text.height / 2,
                CursorManager.SHAPE_BEAM, "Phaze text input");
        ValueComponent slider = context.computeOnClient(client -> (ValueComponent) row(controls.value));
        double x = slider.x + slider.width - 25 - Math.min(70, slider.width * 0.35F) / 2;
        double y = slider.y + slider.height / 2;
        menuHover(context, x, y, CursorManager.SHAPE_HAND, "Phaze slider hover");
        context.getInput().holdMouse(0);
        context.runOnClient(client -> check((int) field(CursorManager.class, "lastAppliedShape", null)
                == CursorManager.SHAPE_HRESIZE, "Phaze slider applies the drag cursor in the same mouse callback"));
        renderAndCheck(context, CursorManager.SHAPE_HRESIZE, "Phaze slider press");
        for (int i = 0; i < 8; i++) renderAndCheck(context, CursorManager.SHAPE_HRESIZE, "Phaze stationary drag");
        context.getInput().releaseMouse(0);
        renderAndCheck(context, CursorManager.SHAPE_HAND, "Phaze slider release");
        for (boolean disableModule : new boolean[]{false, true}) {
            context.getInput().holdMouse(0);
            renderAndCheck(context, CursorManager.SHAPE_HRESIZE, "Phaze drag before disabling");
            context.runOnClient(client -> {
                if (disableModule) Animations.getInstance().setStateSilent(false);
                else Animations.getInstance().dynamicCursor.setValue(false);
            });
            renderAndCheck(context, CursorManager.SHAPE_ARROW, "Phaze disabled drag");
            context.runOnClient(client -> {
                Animations.getInstance().setStateSilent(true);
                Animations.getInstance().dynamicCursor.setValue(true);
            });
            renderAndCheck(context, CursorManager.SHAPE_HRESIZE, "Phaze drag resumes only when enabled");
            context.getInput().releaseMouse(0);
            renderAndCheck(context, CursorManager.SHAPE_HAND, "Phaze re-enabled release");
        }
        var menu = context.computeOnClient(client -> MenuScreen.INSTANCE);
        String[] tabs = {"MODS", "SETTINGS", "COSMETICS", "CONFIGS", "CHAT"};
        double total = (tabs.length - 1) * 8;
        for (String tab : tabs) {
            total += MsdfFonts.bold().getWidth(tab, 6.9F) + 19;
        }
        double tabX = menu.x + menu.width / 2.0 - total / 2
                + (MsdfFonts.bold().getWidth("MODS", 6.9F) + 19) / 2;
        menuHover(context, tabX, menu.y + 17, CursorManager.SHAPE_HAND, "Phaze top tab");
    }

    private static AbstractSettingComponent row(Setting setting) {
        return MenuScreen.INSTANCE.getModuleDetailComponent().getSettingComponents().stream()
                .filter(row -> row.getSetting() == setting).findFirst().orElseThrow();
    }

    private static void menuHover(ClientGameTestContext context, double x, double y, int shape, String name) {
        double[] point = context.computeOnClient(client -> {
            var menu = MenuScreen.INSTANCE;
            double scale = menu.getOverlayRenderScale() * client.getWindow().getScaleFactor();
            double guiScale = MenuUiSettings.getInstance().getGuiScale();
            double centerX = menu.x + menu.width / 2, centerY = menu.y + menu.height / 2;
            return new double[]{(centerX + (x - centerX) * guiScale) * scale,
                    (centerY + (y - centerY) * guiScale) * scale};
        });
        context.getInput().setCursorPos(point[0], point[1]);
        renderAndCheck(context, shape, name);
    }

    private static void hover(ClientGameTestContext context, int x, int y, int shape, String name) {
        double scale = context.computeOnClient(client -> client.getWindow().getScaleFactor());
        context.getInput().setCursorPos(x * scale, y * scale);
        renderAndCheck(context, shape, name);
    }

    private static void renderAndCheck(ClientGameTestContext context, int shape, String name) {
        context.runOnClient(client -> {
            for (int frame = 0; frame < 3; frame++) client.gameRenderer.render(RenderTickCounter.ZERO, true);
            int applied = (int) field(CursorManager.class, "lastAppliedShape", null);
            int requested = (int) field(CursorManager.class, "requestedShape", null);
            check(applied == shape, name + ": expected=" + shape + ", applied=" + applied
                    + ", requested=" + requested + ", gate=" + Animations.getInstance().isDynamicCursorEnabled()
                    + ", mouse=" + client.mouse.getX() + "," + client.mouse.getY());
            String handle = switch (shape) {
                case CursorManager.SHAPE_HAND -> "handCursor";
                case CursorManager.SHAPE_BEAM -> "beamCursor";
                case CursorManager.SHAPE_HRESIZE -> "hResizeCursor";
                case CursorManager.SHAPE_NOT_ALLOWED -> "notAllowedCursor";
                default -> "arrowCursor";
            };
            check((long) field(CursorManager.class, handle, null) != 0, name + ": a real native cursor was created");
        });
    }

    private static Object field(Class<?> owner, String name, Object instance) {
        try {
            var field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not inspect " + owner.getSimpleName() + "." + name, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Consumer<T> listener(Class<?> owner, String name) {
        return (Consumer<T>) field(owner, name, null);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record SavedState(Screen screen, boolean enabled, boolean dynamicCursor, double mouseX, double mouseY,
                              Consumer<Setting> settingListener, Consumer<Module> moduleListener) {}

    private static final class Controls extends Module {
        final TextSetting text = new TextSetting("Cursor input", "Integration text field").setText("seed");
        final ValueSetting value = new ValueSetting("Cursor slider", "Integration slider").range(0, 10).setValue(5);

        Controls() {
            super("cursor_fixture", "Cursor fixture", ModuleCategory.HUD);
            text.setFullWidth(true);
            value.setFullWidth(true);
            setup(text, value);
        }
    }

    private static final class WidgetScreen extends Screen {
        WidgetScreen() { super(Text.literal("Dynamic cursor integration")); }

        @Override
        protected void init() {
            addDrawableChild(ButtonWidget.builder(Text.literal("Button"), button -> {}).dimensions(32, 32, 120, 20).build());
            var disabled = addDrawableChild(ButtonWidget.builder(Text.literal("Disabled"), button -> {})
                    .dimensions(32, 60, 120, 20).build());
            disabled.active = false;
            addDrawableChild(new TextFieldWidget(textRenderer, 32, 88, 120, 20, Text.literal("Text")));
            addDrawableChild(new SliderWidget(32, 116, 120, 20, Text.literal("Slider"), 0.5) {
                @Override
                protected void updateMessage() {}
                @Override
                protected void applyValue() {}
            });
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0xFF202020);
            super.render(context, mouseX, mouseY, delta);
        }
    }
}
