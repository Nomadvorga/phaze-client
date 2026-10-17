package vorga.phazeclient.implement.menu.components.implement.settings;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.feature.module.setting.implement.*;
import vorga.phazeclient.implement.menu.ItemPickerScreen;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.components.AbstractComponent;
import vorga.phazeclient.implement.menu.components.implement.other.CheckComponent;
import vorga.phazeclient.implement.menu.components.implement.window.AbstractWindow;
import vorga.phazeclient.implement.menu.components.implement.window.WindowManager;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color.SettingColorPickerWindow;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.group.GroupWindow;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.group.WorldColorGroupWindow;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.itempicker.ItemPickerColorWindow;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Real render/input regressions; only synthetic, non-persisted settings are changed. */
public final class SettingsRowHeightClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        SavedState saved = context.computeOnClient(client -> new SavedState(client.currentScreen,
                MenuScreen.INSTANCE.getModuleDetailComponent().getModule(),
                new ArrayList<>(WindowManager.INSTANCE.getWindows()),
                client.mouse.getX(), client.mouse.getY(),
                (Integer) field(TextComponent.class, "ACTIVE_TEXT_COMPONENT_ID", null),
                MenuScreen.INSTANCE.category,
                field(MenuScreen.class, "activeTopTab", MenuScreen.INSTANCE),
                field(MenuScreen.class, "pendingTopTab", MenuScreen.INSTANCE),
                MenuScreen.INSTANCE.getModuleDetailComponent().scroll,
                MenuScreen.INSTANCE.getModuleDetailComponent().smoothedScroll));
        Fixture fixture = context.computeOnClient(client -> new Fixture());
        try {
            context.getInput().setCursorPos(0, 0);
            context.runOnClient(client -> {
                check(AbstractSettingComponent.ROW_HEIGHT == 23.5F,
                        "public standard row-height contract is exactly 23.5F, independently of rendered rows");
                WindowManager.INSTANCE.clear();
                MenuScreen.INSTANCE.openGui();
            });
            open(context, fixture.controls);
            context.runOnClient(client -> {
                checkFractionalDetailLayout();
                checkTextInput(component(fixture.text), fixture.text);
                checkBind(component(fixture.bind), fixture.bind);
                checkButton(component(fixture.button), fixture);
                checkExistingControls(fixture);
            });
            context.waitTicks(8);
            context.runOnClient(client -> {
                for (Setting setting : List.of(fixture.value, fixture.bool, fixture.select, fixture.text, fixture.bind)) {
                    checkReset(component(setting));
                    check(!setting.isModified(), setting.getRawName() + ": reset restores its default");
                }
                check(fixture.text.getText().equals("seed"), "text reset updates the committed setting");
            });

            open(context, fixture.popups);
            context.runOnClient(client -> checkFractionalDetailLayout());
            checkGroup(context, fixture.openGroup, true);
            checkGroup(context, fixture.regularGroup, false);
            checkColorPopup(context, fixture);
            checkMultiColor(context, fixture);
            checkItemPicker(context, fixture);
            context.runOnClient(client -> {
                fixture.color.setColor(0xFF224466);
                fixture.multi.getColor(0).setColor(0xFF663322);
                fixture.item.setEnabled(false);
            });
            context.waitTicks(8);
            context.runOnClient(client -> {
                for (Setting setting : List.of(fixture.color, fixture.multi, fixture.item)) {
                    checkReset(component(setting));
                    check(!setting.isModified(), setting.getRawName() + ": centered reset preserves defaults");
                }
                checkFractionalDetailLayout();
                check(WindowManager.INSTANCE.getWindows().isEmpty(), "all fixture popups have closed");
            });
        } finally {
            context.runOnClient(client -> {
                WindowManager.INSTANCE.clear();
                MenuScreen.INSTANCE.closeModuleDetail();
                if (saved.module() != null) MenuScreen.INSTANCE.openModuleDetail(saved.module());
                saved.windows().forEach(WindowManager.INSTANCE::add);
                MenuScreen.INSTANCE.category = saved.category();
                setField(MenuScreen.class, "activeTopTab", MenuScreen.INSTANCE, saved.topTab());
                setField(MenuScreen.class, "pendingTopTab", MenuScreen.INSTANCE, saved.pendingTopTab());
                MenuScreen.INSTANCE.getModuleDetailComponent().scroll = saved.scroll();
                MenuScreen.INSTANCE.getModuleDetailComponent().smoothedScroll = saved.smoothedScroll();
                setField(TextComponent.class, "ACTIVE_TEXT_COMPONENT_ID", null, saved.textActivation());
                client.setScreen(saved.screen());
            });
            context.getInput().setCursorPos(saved.mouseX(), saved.mouseY());
        }
        System.out.println("Uniform 23.5-pixel setting row client checks PASSED");
    }

    private static void open(ClientGameTestContext context, Module module) {
        context.runOnClient(client -> {
            MenuScreen.INSTANCE.openModuleDetail(module);
            for (AbstractSettingComponent row : rows()) {
                check(row.height == AbstractSettingComponent.ROW_HEIGHT,
                        row.getSetting().getRawName() + ": height is seeded before the first render/layout");
            }
        });
        context.waitTicks(8);
        context.runOnClient(client -> {
            for (AbstractSettingComponent row : rows()) {
                check(row.height == AbstractSettingComponent.ROW_HEIGHT,
                        row.getClass().getSimpleName() + ": rendered height remains exactly 23.5F");
                near(row.getExpandedHeight(), AbstractSettingComponent.ROW_HEIGHT,
                        "collapsed standard row has no extra layout height");
                check(row.label.state().getWidth() > 0, "fixture row actually rendered, rather than being culled");
            }
        });
    }

    private static void checkFractionalDetailLayout() {
        List<AbstractSettingComponent> rows = rows();
        check(rows.size() == 6, "each fixture renders three two-column rows");
        for (int i = 0; i < rows.size(); i += 2) {
            AbstractSettingComponent left = rows.get(i);
            AbstractSettingComponent right = rows.get(i + 1);
            near(left.y, right.y, "paired controls share the same top edge");
            near(left.width, right.width, "paired cards have equal width");
            near(right.x - left.x - left.width, 8, "horizontal column gap remains eight pixels");
            if (i >= 2) {
                AbstractSettingComponent previous = rows.get(i - 2);
                near(left.y - previous.y, 31.5F, "row pitch preserves the half-pixel rather than rounding");
                near(left.y - previous.y - previous.height, 8, "fractional rows preserve the eight-pixel vertical gap");
                check(!left.isHover(left.x + left.width / 2, previous.y + previous.height + 4),
                        "space between rows is not part of a card hitbox");
            }
        }
    }

    private static void checkTextInput(AbstractSettingComponent row, TextSetting setting) {
        float inputY = number(row, "rectY");
        float inputHeight = number(row, "rectHeight");
        near(inputY + inputHeight / 2, centerY(row), "rendered text field is vertically centered");
        double inputX = number(row, "rectX") + number(row, "rectWidth") - 2;
        for (int direction : new int[]{-1, 1}) {
            check(!row.mouseClicked(inputX, centerY(row) + direction * (inputHeight / 2 + 0.25), 0),
                    "text field rejects clicks just beyond either centered edge");
            row.charTyped('x', 0);
            check(setting.getText().equals("seed"), "outside input does not change committed text");
        }
        check(row.mouseClicked(inputX, centerY(row), 0), "centered text field accepts focus");
        row.mouseReleased(inputX, centerY(row), 0);
        row.charTyped('4', 0);
        row.charTyped('2', 0);
        check(setting.getText().equals("seed"), "typing does not commit until Enter");
        row.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        check(setting.getText().equals("seed42"), "typing and Enter commit through the rendered input");
        row.charTyped('x', 0);
        row.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        check(setting.getText().equals("seed42"), "Enter ends input focus");
    }

    private static void checkBind(AbstractSettingComponent row, BindSetting setting) {
        for (int direction : new int[]{-1, 1}) {
            check(!row.mouseClicked(row.x + row.width - 12,
                    centerY(row) + direction * (row.height / 2 + 0.25), 0), "bind rejects clicks outside its row");
            check(!row.keyPressed(GLFW.GLFW_KEY_Z, 0, 0), "outside bind click does not arm capture");
        }
        check(row.mouseClicked(row.x + row.width - 12, centerY(row), 0), "bind is clickable at row center");
        check(row.keyPressed(GLFW.GLFW_KEY_K, 0, 0), "focused bind captures a key");
        check(setting.getKey() == GLFW.GLFW_KEY_K, "bind stores the captured key");
        check(!row.keyPressed(GLFW.GLFW_KEY_Z, 0, 0), "binding ends after one key");
    }

    private static void checkButton(AbstractSettingComponent row, Fixture fixture) {
        AbstractComponent button = child(row, "buttonComponent");
        near(button.y + button.height / 2, centerY(row), "SButton's rendered button is centered");
        double x = button.x + button.width / 2;
        for (int direction : new int[]{-1, 1}) {
            check(!row.mouseClicked(x, centerY(row) + direction * (button.height / 2 + 0.25), 0),
                    "SButton rejects clicks outside either vertical button edge");
        }
        check(fixture.buttonClicks == 0, "missed SButton clicks do not run the action");
        for (int direction : new int[]{-1, 1}) {
            check(row.mouseClicked(x, centerY(row) + direction * (button.height / 2 - 0.25), 0),
                    "SButton accepts clicks just inside both centered edges");
        }
        check(fixture.buttonClicks == 2, "each accepted button click runs exactly one action");
    }

    private static void checkExistingControls(Fixture fixture) {
        AbstractSettingComponent slider = component(fixture.value);
        near(number(slider, "cachedSliderY"), centerY(slider), "slider track is centered in the fractional row");
        double sliderX = slider.x + slider.width - 50;
        check(slider.mouseClicked(sliderX, centerY(slider), 0), "slider still starts a drag at center");
        slider.mouseReleased(sliderX, centerY(slider), 0);
        fixture.value.setValue(75);
        AbstractSettingComponent checkbox = component(fixture.bool);
        AbstractComponent toggle = child(checkbox, "checkComponent");
        near(toggle.y + CheckComponent.getToggleHeight() / 2, centerY(checkbox), "checkbox toggle is centered");
        check(checkbox.mouseClicked(toggle.x + CheckComponent.getToggleWidth() / 2, centerY(checkbox), 0),
                "centered checkbox is clickable");
        check(fixture.bool.isValue(), "checkbox changes its setting");
        AbstractSettingComponent select = component(fixture.select);
        double arrowX = select.x + select.width - 14;
        for (int direction : new int[]{-1, 1}) {
            check(!select.mouseClicked(arrowX, centerY(select) + direction * 8.25, 0),
                    "select rejects clicks beyond either centered 16-pixel arrow edge");
            check(select.mouseClicked(arrowX, centerY(select) + direction * 7.75, 0),
                    "select accepts both centered arrow edges");
        }
        check(fixture.select.getSelected().equals("First"), "two accepted arrow clicks cycle twice");
        check(select.mouseClicked(arrowX, centerY(select), 0), "select center click is handled");
        check(fixture.select.getSelected().equals("Second"), "select stores the next canonical value");
    }

    private static void checkGroup(ClientGameTestContext context, GroupSetting setting, boolean openStyle) {
        context.runOnClient(client -> {
            AbstractSettingComponent row = component(setting);
            AbstractComponent control = child(row, openStyle ? "buttonComponent" : "settingComponent");
            // SettingComponent draws/hit-tests an 11px square without assigning its size fields.
            float controlHeight = openStyle ? control.height : 11.0F;
            float controlWidth = openStyle ? control.width : 11.0F;
            near(control.y + controlHeight / 2, centerY(row), "group opener is centered");
            for (int direction : new int[]{-1, 1}) {
                check(!row.mouseClicked(control.x + controlWidth / 2,
                        centerY(row) + direction * (controlHeight / 2 + 0.25), 0),
                        "group opener rejects clicks beyond either centered edge");
            }
            check(row.mouseClicked(control.x + controlWidth / 2, centerY(row), 0), "group opener accepts center click");
        });
        context.waitTicks(8);
        AbstractWindow window = context.computeOnClient(client -> {
            check(WindowManager.INSTANCE.getWindows().size() == 1, "group click opens exactly one window");
            AbstractWindow opened = WindowManager.INSTANCE.getWindows().getFirst();
            List<AbstractSettingComponent> children;
            if (openStyle) {
                check(opened instanceof WorldColorGroupWindow, "Open button creates the color-style group popup");
                WorldColorGroupWindow group = (WorldColorGroupWindow) opened;
                check(group.getSetting() == setting, "Open popup belongs to the clicked group");
                children = group.getComponents();
                near(group.getCachedComponentHeight(), 70.5F, "Open popup's cached content height keeps the final half pixel");
            } else {
                check(opened instanceof GroupWindow, "regular group creates its own popup type");
                GroupWindow group = (GroupWindow) opened;
                check(group.getSetting() == setting, "regular popup belongs to the clicked group");
                children = group.getComponents();
                near(group.getComponentHeight(), 95.5F, "regular popup keeps fractional content plus its 25-pixel header");
            }
            List<AbstractSettingComponent> ordered = children.stream().sorted(Comparator.comparingDouble(row -> row.y)).toList();
            check(ordered.size() == 3, "odd child count exercises fractional window totals");
            for (int i = 0; i < ordered.size(); i++) {
                AbstractSettingComponent row = ordered.get(i);
                near(row.height, 23.5F, "group child uses the literal fractional height after rendering");
                if (i > 0) near(row.y - ordered.get(i - 1).y, 23.5F, "group stacks children without losing half pixels");
            }
            AbstractSettingComponent bottom = ordered.getLast();
            check(bottom.y + bottom.height <= opened.y + opened.height + 0.01F,
                    "group window contains the entire last fractional child");
            check(opened.height >= 3 * 23.5F + (openStyle ? 30 : 25),
                    "group window reserves the whole odd-count content height plus its header/padding");
            AbstractSettingComponent checkbox = children.stream()
                    .filter(row -> row.getSetting() instanceof BooleanSetting).findFirst().orElseThrow();
            AbstractComponent toggle = child(checkbox, "checkComponent");
            check(opened.mouseClicked(toggle.x + CheckComponent.getToggleWidth() / 2, centerY(checkbox), 0),
                    "real group window dispatches a click to its centered child toggle");
            opened.mouseReleased(toggle.x, centerY(checkbox), 0);
            check(((BooleanSetting) checkbox.getSetting()).isValue(), "popup child click changes only its fixture setting");
            return opened;
        });
        context.runOnClient(client -> {
            AbstractSettingComponent row = component(setting);
            AbstractComponent control = child(row, openStyle ? "buttonComponent" : "settingComponent");
            check(row.mouseClicked(control.x + (openStyle ? control.width : 11.0F) / 2, centerY(row), 0),
                    "same group opener toggles the popup closed");
        });
        waitClosed(context, window);
    }

    private static void checkColorPopup(ClientGameTestContext context, Fixture fixture) {
        context.runOnClient(client -> {
            AbstractSettingComponent row = component(fixture.color);
            for (int direction : new int[]{-1, 1}) {
                check(!row.mouseClicked(row.x + row.width - 16,
                        centerY(row) + direction * (row.height / 2 + 0.25), 0), "color row rejects outside clicks");
            }
            check(row.mouseClicked(row.x + row.width - 16, centerY(row), 0), "popup color swatch center is clickable");
        });
        context.waitTicks(8);
        AbstractWindow window = context.computeOnClient(client -> {
            SettingColorPickerWindow picker = onlyWindow(SettingColorPickerWindow.class);
            check(picker.getSetting() == fixture.color, "color popup edits the clicked color setting");
            return picker;
        });
        context.runOnClient(client -> {
            AbstractSettingComponent row = component(fixture.color);
            check(row.mouseClicked(row.x + row.width - 16, centerY(row), 0), "second color click closes its popup");
        });
        waitClosed(context, window);
    }

    private static void checkMultiColor(ClientGameTestContext context, Fixture fixture) {
        for (int index = 0; index < fixture.multi.getColorCount(); index++) {
            int selected = index;
            context.runOnClient(client -> {
                AbstractSettingComponent row = component(fixture.multi);
                double swatchX = row.x + row.width - 12 - (fixture.multi.getColorCount() - 1 - selected) * 9;
                for (int direction : new int[]{-1, 1}) {
                    check(!row.mouseClicked(swatchX, centerY(row) + direction * 3.75, 0),
                            "MultiColor rejects clicks outside either centered seven-pixel swatch edge");
                }
                check(row.mouseClicked(swatchX, centerY(row) - 3.25, 0), "MultiColor top swatch edge is clickable");
                check(fixture.multi.getSelectedColorIndex() == selected, "MultiColor selects the clicked index");
            });
            context.waitTicks(8);
            AbstractWindow window = context.computeOnClient(client -> {
                SettingColorPickerWindow picker = onlyWindow(SettingColorPickerWindow.class);
                check(picker.getSetting() == fixture.multi.getColor(selected), "MultiColor popup edits the clicked child color");
                return picker;
            });
            context.runOnClient(client -> {
                AbstractSettingComponent row = component(fixture.multi);
                double swatchX = row.x + row.width - 12 - (fixture.multi.getColorCount() - 1 - selected) * 9;
                check(row.mouseClicked(swatchX, centerY(row) + 3.25, 0), "MultiColor bottom edge closes the picker");
            });
            waitClosed(context, window);
        }
    }

    private static void checkItemPicker(ClientGameTestContext context, Fixture fixture) {
        context.runOnClient(client -> {
            AbstractSettingComponent row = component(fixture.item);
            AbstractComponent toggle = child(row, "checkComponent");
            near(toggle.y + CheckComponent.getToggleHeight() / 2, centerY(row), "ItemPicker toggle is centered");
            check(row.mouseClicked(toggle.x + CheckComponent.getToggleWidth() / 2, centerY(row), 0),
                    "ItemPicker toggle handles the center click");
            check(!fixture.item.isEnabled(), "ItemPicker toggle changes enabled state without opening a screen");
            check(client.currentScreen == MenuScreen.INSTANCE, "toggle preserves the menu screen");
            check(row.mouseClicked(row.x + row.width - 16, centerY(row) - 5.75, 0),
                    "ItemPicker color swatch top edge opens its popup");
        });
        context.waitTicks(8);
        AbstractWindow window = context.computeOnClient(client -> {
            ItemPickerColorWindow picker = onlyWindow(ItemPickerColorWindow.class);
            check(picker.getSetting() == fixture.item, "ItemPicker color popup owns the correct setting");
            return picker;
        });
        context.runOnClient(client -> {
            AbstractSettingComponent row = component(fixture.item);
            check(row.mouseClicked(row.x + row.width - 16, centerY(row) + 5.75, 0),
                    "ItemPicker color swatch bottom edge closes its popup");
        });
        waitClosed(context, window);
        context.runOnClient(client -> {
            AbstractSettingComponent fixed = component(fixture.fixedItem);
            check(fixed.mouseClicked(fixed.x + 45, centerY(fixed), 0), "fixed ItemPicker row remains clickable");
            check(!fixture.fixedItem.isEnabled(), "fixed ItemPicker row toggles instead of opening a screen");
            AbstractSettingComponent row = component(fixture.item);
            check(row.mouseClicked(row.x + 45, centerY(row), 0), "custom ItemPicker row opens item selection");
            check(client.currentScreen instanceof ItemPickerScreen, "custom ItemPicker opens the real selection screen");
            client.currentScreen.close();
            check(client.currentScreen == MenuScreen.INSTANCE, "closing item selection restores its parent menu");
        });
        context.waitTicks(8);
    }

    private static void checkReset(AbstractSettingComponent row) {
        double resetX = row.x + ResetIconComponent.getXOffset() + 2;
        for (int direction : new int[]{-1, 1}) {
            check(row.resetIcon.isHovered(resetX, centerY(row) + direction * 4),
                    "reset hitbox includes both sides of the fractional row center");
            check(!row.resetIcon.isHovered(resetX, centerY(row) + direction * 6),
                    "reset hitbox excludes points beyond its centered icon");
        }
        check(row.mouseClicked(resetX, centerY(row), 0), row.getClass().getSimpleName() + ": centered reset click is handled");
    }

    private static void waitClosed(ClientGameTestContext context, AbstractWindow window) {
        context.waitFor(client -> !WindowManager.INSTANCE.getWindows().contains(window));
        context.runOnClient(client -> check(WindowManager.INSTANCE.getWindows().isEmpty(), "popup close animation removes the window"));
    }

    private static <T extends AbstractWindow> T onlyWindow(Class<T> type) {
        List<AbstractWindow> windows = WindowManager.INSTANCE.getWindows();
        check(windows.size() == 1 && type.isInstance(windows.getFirst()), "exactly the requested popup type is open");
        return type.cast(windows.getFirst());
    }

    private static List<AbstractSettingComponent> rows() {
        return MenuScreen.INSTANCE.getModuleDetailComponent().getSettingComponents();
    }

    private static AbstractSettingComponent component(Setting setting) {
        return rows().stream().filter(row -> row.getSetting() == setting).findFirst().orElseThrow();
    }

    private static float centerY(AbstractComponent row) {
        return row.y + row.height / 2;
    }

    // Read rendered child geometry without widening production APIs for tests.
    private static AbstractComponent child(Object owner, String name) {
        return (AbstractComponent) field(owner.getClass(), name, owner);
    }

    private static float number(Object owner, String name) {
        return ((Number) field(owner.getClass(), name, owner)).floatValue();
    }

    private static Object field(Class<?> type, String name, Object owner) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot inspect rendered control " + name, e);
        }
    }

    private static void setField(Class<?> type, String name, Object owner, Object value) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(owner, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot restore control state " + name, e);
        }
    }

    private static void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < 0.01F, message + " (expected " + expected + ", got " + actual + ")");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Fixture {
        private final ValueSetting value = new ValueSetting("Row slider", "").range(0, 100).setValue(50);
        private final BooleanSetting bool = new BooleanSetting("Row checkbox", "").setValue(false);
        private final SelectSetting select = new SelectSetting("Row select", "").value("First", "Second").selected("First");
        private final TextSetting text = new TextSetting("Row text", "").setText("seed");
        private final BindSetting bind = new BindSetting("Row bind", "").setKey(GLFW.GLFW_KEY_UNKNOWN);
        private int buttonClicks;
        private final ButtonSetting button = new ButtonSetting("Row button", "").setButtonName("Apply").setRunnable(() -> buttonClicks++);
        private final GroupSetting openGroup = group("Open group", true);
        private final GroupSetting regularGroup = group("Regular group", false);
        private final ColorSetting color = new ColorSetting("Popup color", "").value(0xFF5599CC).popupRow();
        private final ItemPickerSetting item = new ItemPickerSetting("Custom item", "", 0xFF5599CC);
        private final MultiColorSetting multi = new MultiColorSetting("Multi color", "").colors("One", "Two").defaultColors(0xFF336699, 0xFF99CC55);
        private final ItemPickerSetting fixedItem = new ItemPickerSetting("Fixed item", "", 0xFF5599CC).fixedSelection(Items.STONE, "");
        private final Module controls = new Module("StandardRowControlsFixture", ModuleCategory.OTHER, false);
        private final Module popups = new Module("StandardRowPopupsFixture", ModuleCategory.OTHER, false);

        private Fixture() {
            for (Setting setting : List.of(value, bool, select, text, bind, button, openGroup, regularGroup, color, item, multi, fixedItem)) {
                isolate(setting);
            }
            controls.setup(value, bool, select, text, bind, button);
            popups.setup(openGroup, regularGroup, color, item, multi, fixedItem);
        }

        private static GroupSetting group(String name, boolean openStyle) {
            return new GroupSetting(name, "", false).colorPickerStyleWindow(openStyle).popupMaxHeight(190)
                    .settings(new TextSetting("Child text", "").setText("child"),
                            new BooleanSetting("Child checkbox", "").setValue(false),
                            new BindSetting("Child bind", "").setKey(GLFW.GLFW_KEY_UNKNOWN));
        }

        private static void isolate(Setting setting) {
            setting.setSaveToConfig(false);
            if (setting instanceof GroupSetting group) group.getSubSettings().forEach(Fixture::isolate);
            if (setting instanceof MultiColorSetting colors) colors.getAllColors().forEach(Fixture::isolate);
        }
    }

    private record SavedState(Screen screen, Module module, List<AbstractWindow> windows,
                              double mouseX, double mouseY, int textActivation, ModuleCategory category,
                              Object topTab, Object pendingTopTab, double scroll, double smoothedScroll) {}
}
