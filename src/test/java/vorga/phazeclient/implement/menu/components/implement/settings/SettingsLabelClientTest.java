package vorga.phazeclient.implement.menu.components.implement.settings;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.Screen;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.implement.features.modules.client.Theme;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.MenuUiSettings;
import vorga.phazeclient.implement.menu.components.implement.other.CheckComponent;

import java.util.List;

/** Isolated, opt-in entrypoint; registration is owned by the main test suite. */
public final class SettingsLabelClientTest implements FabricClientGameTest {
    private static final String LONG_KEY = "Distance in blocks at which the fog reaches full opacity";

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        SavedState saved = context.computeOnClient(client -> new SavedState(client.currentScreen,
                Theme.getInstance().language.getSelected(), Lang.getActive(), client.mouse.getX(), client.mouse.getY()));
        Fixture fixture = context.computeOnClient(client -> new Fixture());
        try {
            moveOutside(context);
            context.runOnClient(client -> {
                Theme.getInstance().language.setSelected(Lang.RU);
                Theme.getInstance().syncLanguage();
                MenuScreen.INSTANCE.openGui();
                MenuScreen.INSTANCE.openModuleDetail(fixture.module);
            });
            context.waitTicks(12);
            context.runOnClient(client -> {
                near(AbstractSettingComponent.ROW_HEIGHT, 23.5F, "standard setting row height is exactly 23.5 pixels");
                check(MenuScreen.INSTANCE.getModuleDetailComponent().getModule() == fixture.module, "synthetic detail is open");
                check(!Lang.translate(LONG_KEY).equals(LONG_KEY), "fixture uses real Russian Lang translation");
                for (AbstractSettingComponent row : rows()) {
                    check(row.label.state().isOverflowing(), "long RU label overflows: " + row.getSetting().getRawName());
                    check(row.height == AbstractSettingComponent.ROW_HEIGHT,
                            "Value/Select/Boolean labels keep the shared fractional row height");
                    near(row.label.state().offset(System.nanoTime()), 0, "labels initially idle");
                    check(!row.label.state().isHovered(), "initial cursor is outside the setting cards");
                }
            });
            System.out.println("Settings label idle screenshot: " + context.takeScreenshot("settings-label-ru-idle"));
            context.runOnClient(client -> {
                for (AbstractSettingComponent row : rows()) {
                    // Dispatch only the click: keep the real cursor outside the card
                    // so a hover entry cannot masquerade as a click-triggered cycle.
                    check(!row.mouseClicked(row.x + 35, row.y + row.height / 2, 0),
                            "Value/Select/Boolean label clicks keep their original unhandled semantics");
                    near(row.label.state().offset(System.nanoTime()), 0, "click alone does not start a marquee");
                }
                fixture.checkUnchanged();
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                fixture.checkUnchanged();
                for (AbstractSettingComponent row : rows()) {
                    near(row.label.state().offset(System.nanoTime()), 0, "clicks do not schedule a later animation");
                }
            });

            // A real pointer can hover only one row at a time. Test each type
            // independently, entering over its control rather than its label.
            hoverCycle(context, fixture, fixture.value, "value");
            hoverCycle(context, fixture, fixture.select, "select");
            hoverCycle(context, fixture, fixture.bool, "boolean");

            // Returning to a card arms another cycle. Leaving mid-animation
            // scrolls the label back to its start.
            hoverControl(context, fixture.value);
            context.waitFor(client -> component(fixture.value).label.state().offset(System.nanoTime()) > 1.0F);
            moveOutside(context);
            context.waitFor(client -> !component(fixture.value).label.state().isHovered());
            context.waitFor(client -> component(fixture.value).label.state().offset(System.nanoTime()) == 0);

            context.runOnClient(client -> {
                fixture.checkUnchanged();
                AbstractSettingComponent slider = component(fixture.value);
                check(slider.mouseClicked(slider.x + slider.width - 50, slider.y + slider.height / 2, 0),
                        "slider control still starts a drag");
                slider.mouseReleased(slider.x + slider.width - 50, slider.y + slider.height / 2, 0);
                fixture.value.setValue(75.0F);
                AbstractSettingComponent select = component(fixture.select);
                check(select.mouseClicked(select.x + select.width - 14, select.y + select.height / 2, 0),
                        "right arrow still cycles");
                check(fixture.select.getSelected().equals("Second"), "mode is stored using canonical English value");
                AbstractSettingComponent checkbox = component(fixture.bool);
                check(checkbox.mouseClicked(checkbox.x + checkbox.width - 20, checkbox.y + checkbox.height / 2, 0),
                         "boolean toggle still handles clicks");
                check(fixture.bool.isValue(), "boolean control still toggles");
                checkCheckboxHitCentering(checkbox, fixture.bool);
                for (AbstractSettingComponent row : rows()) {
                    check(!row.label.state().isHovered(), "direct control clicks do not move the cursor");
                    near(row.label.state().offset(System.nanoTime()), 0, "control clicks alone do not start a marquee");
                }
            });
            // Let modified/reset visibility animations settle before testing their hit areas.
            context.waitTicks(6);
            context.runOnClient(client -> {
                for (AbstractSettingComponent row : rows()) {
                    if (row instanceof CheckboxComponent) {
                        double resetX = row.x + ResetIconComponent.getXOffset() + 2;
                        double centerY = row.y + row.height / 2.0;
                        for (int direction : new int[]{-1, 1}) {
                            check(row.resetIcon.isHovered(resetX, centerY + direction * 4),
                                    "boolean reset hit area includes both sides of the row center");
                            check(!row.resetIcon.isHovered(resetX, centerY + direction * 6),
                                    "boolean reset hit area excludes points beyond its centered icon");
                        }
                    }
                    check(row.mouseClicked(row.x + ResetIconComponent.getXOffset() + 2,
                            row.y + row.height / 2, 0), "reset icon hit area still consumes click");
                }
                fixture.checkUnchanged();
            });
            smoothViewportChanges(context, fixture);
        } finally {
            context.runOnClient(client -> {
                Theme.getInstance().language.setSelected(saved.language());
                Lang.setActive(saved.activeLanguage());
                MenuScreen.INSTANCE.closeModuleDetail();
                client.setScreen(saved.screen());
            });
            context.getInput().setCursorPos(saved.mouseX(), saved.mouseY());
        }
        System.out.println("Settings label client checks PASSED");
    }

    private static void checkCheckboxHitCentering(AbstractSettingComponent checkbox, BooleanSetting setting) {
        double toggleX = checkbox.x + checkbox.width - 20;
        double centerY = checkbox.y + checkbox.height / 2.0;
        double halfToggleHeight = CheckComponent.getToggleHeight() / 2.0;
        for (int direction : new int[]{-1, 1}) {
            check(checkbox.isHover(toggleX, centerY + direction * (checkbox.height / 2.0 - 0.5)),
                    "boolean card hover includes its full shared row height");
            check(!checkbox.isHover(toggleX, centerY + direction * (checkbox.height / 2.0 + 0.5)),
                    "boolean card hover excludes points outside its height");
            boolean before = setting.isValue();
            check(!checkbox.mouseClicked(toggleX, centerY + direction * (halfToggleHeight + 0.5), 0),
                    "click just outside the centered boolean toggle is unhandled");
            check(setting.isValue() == before, "outside click does not toggle boolean");
            check(checkbox.mouseClicked(toggleX, centerY + direction * (halfToggleHeight - 0.5), 0),
                    "click just inside either vertical toggle edge is handled");
            check(setting.isValue() != before, "both centered toggle edges change the value");
        }
        check(setting.isValue(), "symmetric toggle checks preserve the modified value for reset");
    }

    private static void smoothViewportChanges(ClientGameTestContext context, Fixture fixture) {
        moveOutside(context);
        // Keep the reset icon visible on both values: this isolates the abrupt
        // digit-width change (0.5 -> 100.0) from the already animated left inset.
        context.runOnClient(client -> fixture.value.setValue(0.5F));
        context.waitTicks(8);
        float wide = context.computeOnClient(client -> component(fixture.value).label.state().getWidth());
        context.runOnClient(client -> fixture.value.setValue(100.0F));
        context.waitTicks(2);
        float shrinking = context.computeOnClient(client -> component(fixture.value).label.state().getWidth());
        System.out.println("Settings label animated clip screenshot: "
                + context.takeScreenshot("settings-label-animated-clip"));
        context.waitTicks(8);
        float narrow = context.computeOnClient(client -> component(fixture.value).label.state().getWidth());
        check(wide > shrinking + 0.001F && shrinking > narrow + 0.001F,
                "real slider label clip shrinks through an intermediate width rather than snapping");

        context.runOnClient(client -> fixture.value.setValue(0.5F));
        context.waitTicks(2);
        float growing = context.computeOnClient(client -> component(fixture.value).label.state().getWidth());
        context.waitTicks(8);
        float restored = context.computeOnClient(client -> component(fixture.value).label.state().getWidth());
        check(narrow + 0.001F < growing && growing + 0.001F < restored,
                "real slider label clip grows through an intermediate width rather than snapping");
        near(restored, wide, "viewport returns exactly to its original width");
        context.runOnClient(client -> fixture.value.reset());
        context.waitTicks(8);
        context.runOnClient(client -> fixture.checkUnchanged());
        System.out.println("Settings label animated clip checks PASSED");
    }

    private static void hoverCycle(ClientGameTestContext context, Fixture fixture, Setting setting, String name) {
        moveOutside(context);
        context.waitFor(client -> rows().stream().noneMatch(row -> row.label.state().isHovered()));
        hoverControl(context, setting);
        context.waitFor(client -> component(setting).label.state().isHovered()
                && component(setting).label.state().offset(System.nanoTime()) > 1.0F);
        context.waitFor(client -> {
            SettingLabelState state = component(setting).label.state();
            return Math.abs(state.offset(System.nanoTime()) - state.getOverflow()) < 0.1F;
        });
        context.runOnClient(client -> {
            fixture.checkUnchanged();
            SettingLabelState state = component(setting).label.state();
            near(state.alphaAt(0, state.getOverflow()), 0, "tail view fades hidden left text");
            check(rows().stream().filter(row -> row.getSetting() != setting)
                    .noneMatch(row -> row.label.state().isHovered()), "only the card under the pointer is hovered");
        });
        System.out.println("Settings label " + name + " hover tail screenshot: "
                + context.takeScreenshot("settings-label-ru-" + name + "-hover-tail"));
        context.waitFor(client -> component(setting).label.state().offset(System.nanoTime()) == 0);
        // Keep the cursor stationary over the control: completed hover must not loop.
        context.waitTicks(8);
        context.runOnClient(client -> {
            fixture.checkUnchanged();
            SettingLabelState state = component(setting).label.state();
            check(state.isHovered(), "cursor remains over the same card");
            near(state.offset(System.nanoTime()), 0, "stationary hover does not restart the cycle");
        });
        System.out.println("Settings label " + name + " hover returned screenshot: "
                + context.takeScreenshot("settings-label-ru-" + name + "-hover-returned"));
    }

    private static void hoverControl(ClientGameTestContext context, Setting setting) {
        double[] cursor = context.computeOnClient(client -> {
            AbstractSettingComponent row = component(setting);
            float controlRightInset = setting instanceof ValueSetting ? 50.0F
                    : setting instanceof SelectSetting ? 14.0F : 20.0F;
            MenuScreen menu = MenuScreen.INSTANCE;
            double centerX = menu.x + menu.width / 2.0;
            double centerY = menu.y + menu.height / 2.0;
            double guiScale = MenuUiSettings.getInstance().getGuiScale();
            // Inverse of MenuScreen.toMenuCoordinateX/Y: logical card -> overlay
            // -> vanilla scaled screen -> raw framebuffer coordinates for TestInput.
            double factor = menu.getOverlayRenderScale() * client.getWindow().getScaleFactor();
            return new double[]{(centerX + (row.x + row.width - controlRightInset - centerX) * guiScale) * factor,
                    (centerY + (row.y + row.height / 2.0 - centerY) * guiScale) * factor};
        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
    }

    private static void moveOutside(ClientGameTestContext context) {
        context.getInput().setCursorPos(0, 0);
    }

    private static List<AbstractSettingComponent> rows() {
        return MenuScreen.INSTANCE.getModuleDetailComponent().getSettingComponents();
    }

    private static AbstractSettingComponent component(Setting setting) {
        return rows().stream().filter(row -> row.getSetting() == setting).findFirst().orElseThrow();
    }

    private static String longTranslatedLabel() {
        String translated = Lang.translate(LONG_KEY);
        return translated + " / " + translated + " / " + translated;
    }

    private static final class Fixture {
        private final ValueSetting value = new ValueSetting("Background Blur Radius", "") {
            @Override public String getLocalizedName() { return longTranslatedLabel(); }
        }.range(0.0F, 100.0F).setValue(50.0F);
        private final SelectSetting select = new SelectSetting("Background Preset", "") {
            @Override public String getLocalizedName() { return longTranslatedLabel(); }
        }.value("First", "Second").selected("First");
        private final BooleanSetting bool = new BooleanSetting("Boolean Label Fixture", "") {
            @Override public String getLocalizedName() { return longTranslatedLabel(); }
        }.setValue(false);
        private final Module module = new Module("SettingsLabelFixture", ModuleCategory.OTHER, false);

        private Fixture() {
            // Fixture changes cannot enter the user's config persistence path.
            for (Setting setting : new Setting[]{value, select, bool}) setting.setSaveToConfig(false);
            module.setup(value, select, bool);
        }

        private void checkUnchanged() {
            near(value.getValue(), 50, "hover/label click did not drag slider");
            check(select.getSelected().equals("First"), "hover/label click did not change selection");
            check(!bool.isValue(), "hover/label click did not toggle boolean");
            check(value.getRawName().equals("Background Blur Radius"), "setting key stays canonical English");
        }
    }

    private static void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < 0.01F, message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record SavedState(Screen screen, String language, String activeLanguage, double mouseX, double mouseY) {}
}
