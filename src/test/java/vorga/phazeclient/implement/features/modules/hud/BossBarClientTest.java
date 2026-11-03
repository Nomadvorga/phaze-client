package vorga.phazeclient.implement.features.modules.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.Difficulty;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.api.system.hud.BossBarColorContext;
import vorga.phazeclient.api.system.hud.BossBarHudAccess;
import vorga.phazeclient.api.system.hud.ExordiumAnimationBridge;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.config.ConfigManager;
import vorga.phazeclient.implement.features.modules.client.Theme;
import vorga.phazeclient.implement.features.modules.other.Animations;
import vorga.phazeclient.implement.features.modules.other.NoRender;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.MenuUiSettings;
import vorga.phazeclient.implement.menu.components.implement.settings.AbstractSettingComponent;
import vorga.phazeclient.implement.menu.components.implement.settings.ScaleSnapOverlay;
import vorga.phazeclient.implement.menu.components.implement.settings.ValueComponent;
import vorga.phazeclient.implement.menu.components.implement.window.WindowManager;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color.SettingColorPickerWindow;
import vorga.phazeclient.implement.menu.profile.MenuProfileManager;
import vorga.phazeclient.mixins.test.BossBarTestMixins;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/** Uses the real menu, mouse callbacks, server packets, vanilla renderer and optional Exordium runtime. */
public final class BossBarClientTest implements FabricClientGameTest {
    private static final BossBarHud MODULE = BossBarHud.getInstance();
    private static final int HUD_INDEX = 28;
    private static final int FILL = 0xFF35C975;
    private static final Identifier SENTINEL = Identifier.ofVanilla("boss_bar/white_progress");
    private static final String LONG_NAME = "A long boss name: bold text, descenders gjpqy, and a vanilla shadow";
    private static int checks;

    @Override
    public void runTest(ClientGameTestContext context) {
        checks = 0;
        context.waitFor(client -> client.getOverlay() == null);
        SavedState saved = context.computeOnClient(client -> {
            requireIsolatedRun(client);
            check(client.world == null, "fixture does not replace an existing world");
            return SavedState.capture(client);
        });
        boolean exordium = FabricLoader.getInstance().isModLoaded("exordium");
        System.out.println("Boss Bar runtime namespace: " + FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace());
        TestSingleplayerContext world = null;
        try {
            context.runOnClient(client -> {
                Setting.setGlobalChangeListener(null);
                Module.setGlobalStateChangeListener(null);
                disableModules();
                client.options.pauseOnLostFocus = false;
                client.options.hudHidden = false;
                client.options.getGuiScale().setValue(2);
                client.options.getViewDistance().setValue(2);
                client.getToastManager().clear();
                if (client.inGameHud.getDebugHud().shouldShowDebugHud()) client.inGameHud.getDebugHud().toggleDebugHud();
            });
            context.getInput().resizeWindow(1280, 720);
            menuSettings(context);
            configurationFiles(context);
            context.runOnClient(client -> {
                disableModules();
                resetBossBar();
                client.setScreen(null);
            });
            world = context.worldBuilder().adjustSettings(creator -> {
                creator.setWorldName("Phaze Boss Bar integration" + (exordium ? " Exordium" : ""));
                creator.setGameMode(WorldCreator.Mode.CREATIVE);
                creator.setDifficulty(Difficulty.PEACEFUL);
                creator.setCheatsEnabled(true);
            }).create();
            world.getClientWorld().waitForChunksRender();
            context.waitFor(client -> client.player != null && client.getOverlay() == null);
            addServerBars(world);
            context.waitFor(client -> bars(client).size() == 7);
            context.runOnClient(client -> check(bars(client).values().stream().allMatch(bar -> bar.getPercent() == 0.75F),
                    "the client received complete server-backed bars"));
            vanillaRendering(context);
            packetAndReload(context, world);
            foreignHooks(context);
            gameplayAndCache(context, exordium);
            if (exordium) cachedResourceReload(context);
            editor(context);
            performance(context, exordium);
            removeServerBars(world);
            context.waitFor(client -> bars(client).isEmpty());
            preview(context);
        } finally {
            try {
                context.getInput().releaseMouse(0);
                context.runOnClient(client -> {
                    BossBarProbe.stop();
                    BossBarProbe.clearForeignHooks();
                    MODULE.setStateSilent(false);
                    MenuScreen.INSTANCE.closeModuleDetail();
                    WindowManager.INSTANCE.clear();
                    client.setScreen(null);
                });
                if (world != null) world.close();
            } finally {
                context.getInput().resizeWindow(saved.width(), saved.height());
                context.runOnClient(client -> saved.restore(client));
            }
        }
        System.out.println("Boss Bar real-client integration PASSED: " + checks + " checks; Exordium=" + exordium);
    }

    private static void menuSettings(ClientGameTestContext context) {
        context.runOnClient(client -> {
            resetBossBar();
            Animations.getInstance().setStateSilent(true);
            Animations.getInstance().dynamicCursor.setValue(true);
            ScaleSnapOverlay.hide();
            MenuScreen.INSTANCE.openGui();
            MenuScreen.INSTANCE.openModuleDetail(MODULE);
            MenuScreen.INSTANCE.animation.setDirectionAndFinish(vorga.phazeclient.api.system.animation.Direction.FORWARDS);
        });
        for (String language : List.of(Lang.EN, Lang.RU)) {
            context.runOnClient(client -> {
                Theme.getInstance().language.setSelected(language);
                Lang.setActive(language);
            });
            render(context, 6);
            context.runOnClient(client -> {
                var health = row(MODULE.renderHealth);
                var text = row(MODULE.renderText);
                var general = row(MODULE.mainSection);
                var color = row(MODULE.colorOptions);
                var mode = row(MODULE.colorMode);
                var picker = row(MODULE.fillColor);
                close(health.y, text.y, "health and text occupy the same rendered row");
                check(health.x < text.x && general.y < health.y && color.y > health.y,
                        "General and Color divide the actual two-column controls");
                close(mode.y, picker.y, "Custom selector and popup picker share a row");
                check(general.width > health.width && color.width == general.width, "section dividers are full width");
                check(MODULE.getDisplayDescription().equals(language.equals(Lang.RU)
                        ? "Настраиваемый боссбар" : "Customizable boss bar"), "the visible description is localized");
                check(MODULE.mainSection.getName().equals(language.equals(Lang.RU) ? "Общие" : "General"), "General label");
                check(MODULE.colorOptions.getName().equals(language.equals(Lang.RU) ? "Цвет" : "Color"), "Color label");
            });
            screenshot(context, "settings-" + language.toLowerCase(Locale.ROOT) + "-custom");
            double[] arrow = context.computeOnClient(client -> {
                var mode = row(MODULE.colorMode);
                return new double[]{mode.x + mode.width - 15, mode.y + mode.height / 2};
            });
            menuClick(context, arrow[0], arrow[1]);
            context.runOnClient(client -> check(MODULE.colorMode.isSelected("Rainbow"), "right arrow selects Rainbow"));
            render(context, 6);
            context.runOnClient(client -> {
                var mode = row(MODULE.colorMode);
                var speed = row(MODULE.rainbowSpeed);
                check(MODULE.rainbowSpeed.isVisible() && !MODULE.fillColor.isVisible(), "Rainbow swaps the picker for the speed slider");
                check(speed.y > mode.y && speed.width == mode.width, "Rainbow Speed appears below the full-width selector");
            });
            screenshot(context, "settings-" + language.toLowerCase(Locale.ROOT) + "-rainbow");
            ValueComponent speed = context.computeOnClient(client -> (ValueComponent) row(MODULE.rainbowSpeed));
            float start = (float) BossBarProbe.field(ValueComponent.class, "cachedSliderStartX", speed);
            float width = (float) BossBarProbe.field(ValueComponent.class, "cachedSliderWidth", speed);
            menuHover(context, start + width / 2, speed.y + speed.height / 2);
            context.getInput().holdMouse(0);
            menuHover(context, start + width + 4, speed.y + speed.height / 2);
            context.runOnClient(client -> check(MODULE.rainbowSpeed.getValue() == 1, "real slider reaches the fast endpoint"));
            menuHover(context, start - 4, speed.y + speed.height / 2);
            context.runOnClient(client -> check(MODULE.rainbowSpeed.getValue() == 0.1F, "real slider reaches the slow endpoint"));
            context.getInput().releaseMouse(0);
            context.runOnClient(client -> check(BossBarProbe.field(ScaleSnapOverlay.class, "valueText", null) == null,
                    "GUI sliders never show the large chat HUD scale readout"));
            double[] left = context.computeOnClient(client -> {
                var mode = row(MODULE.colorMode);
                float boxWidth = Math.min(118, Math.max(92, mode.width * 0.42F));
                return new double[]{mode.x + mode.width - boxWidth - 1, mode.y + mode.height / 2};
            });
            menuClick(context, left[0], left[1]);
            context.runOnClient(client -> check(MODULE.colorMode.isSelected("Custom"), "left arrow returns to Custom"));
            render(context, 5);
            double[] pickerPoint = context.computeOnClient(client -> {
                var picker = row(MODULE.fillColor);
                return new double[]{picker.x + picker.width - 16, picker.y + picker.height / 2};
            });
            menuClick(context, pickerPoint[0], pickerPoint[1]);
            SettingColorPickerWindow picker = context.computeOnClient(client -> WindowManager.INSTANCE.getWindows().stream()
                    .filter(window -> window instanceof SettingColorPickerWindow p && p.getSetting() == MODULE.fillColor)
                    .map(window -> (SettingColorPickerWindow) window).findFirst().orElseThrow());
            context.runOnClient(client -> picker.getScaleAnimation().setDirectionAndFinish(vorga.phazeclient.api.system.animation.Direction.FORWARDS));
            screenshot(context, "settings-" + language.toLowerCase(Locale.ROOT) + "-picker");
            int old = context.computeOnClient(client -> MODULE.fillColor.getColor());
            menuClick(context, picker.x + 88, picker.y + 38);
            context.runOnClient(client -> check(MODULE.fillColor.getColor() != old && MODULE.fillColor.getColor() >>> 24 == 255,
                    "the existing popup edits opaque fill color through its real mouse callback"));
            context.runOnClient(client -> {
                WindowManager.INSTANCE.clear();
                MODULE.fillColor.reset();
                MODULE.rainbowSpeed.reset();
            });
        }
        context.runOnClient(client -> {
            MenuScreen.INSTANCE.closeModuleDetail();
            client.setScreen(null);
            Theme.getInstance().language.setSelected(Lang.EN);
            Lang.setActive(Lang.EN);
        });
        System.out.println("Boss Bar GENERAL/COLOR, Russian labels, arrows, slider and popup PASSED");
    }

    private static void configurationFiles(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var configs = ConfigManager.getInstance();
            var profiles = MenuProfileManager.getInstance();
            Path profileDir = (Path) invoke(profiles, "profilesDirectory");
            check(profileDir.toAbsolutePath().normalize().startsWith(client.runDirectory.toPath().toAbsolutePath().normalize()),
                    "profile I/O is confined to this runner");
            String suffix = Long.toUnsignedString(System.nanoTime());
            for (boolean profile : new boolean[]{false, true}) {
                String name = "boss_bar_legacy_" + suffix + (profile ? "_profile" : "_config");
                JsonObject data = new JsonObject();
                JsonObject settings = profile ? data : new JsonObject();
                if (!profile) data.add("settings", settings);
                data.addProperty("enabled", true);
                data.addProperty(profile ? "bind" : "key", 48);
                settings.addProperty("Scale", 1.5F);
                settings.addProperty("Y Offset", 25);
                settings.addProperty("Hide Bossbar", true);
                JsonObject modules = new JsonObject();
                modules.add("bossbar_tweaker", data);
                JsonObject root = new JsonObject();
                root.add("modules", modules);
                Path file = profile ? profileDir.resolve(name + ".profile") : configs.getConfigFile(name).toPath();
                writeJson(file, root);
                loadFile(profile, name);
                close(MODULE.getHudScale(), 1.5F, "real legacy size migration");
                close(MODULE.getHudX(), client.getWindow().getWidth() / 2.0F - 91 * 1.5F * (float) client.getWindow().getScaleFactor(),
                        "real legacy centered position migration");
                close(MODULE.getHudY(), 43 * (float) client.getWindow().getScaleFactor(), "real legacy Y Offset migration");
                check(MODULE.isEnabled() && MODULE.getKey() == 48 && !MODULE.renderHealth.isValue() && !MODULE.renderText.isValue(),
                        "legacy visibility, module state and bind survive actual loading");
                check(readJson(file).equals(root), "loading does not rewrite the legacy file");

                MODULE.setHudPositionRatio(0.12F, 0.23F);
                MODULE.setHudScale(8);
                MODULE.renderHealth.setValue(true);
                MODULE.renderText.setValue(true);
                MODULE.colorMode.setSelected("Rainbow");
                MODULE.rainbowSpeed.setValue(1);
                MODULE.fillColor.setColor(FILL);
                String current = name + "_new";
                if (profile) invoke(profiles, "saveProfile", new Class<?>[]{String.class}, current);
                else configs.saveConfig(current);
                Path currentFile = profile ? profileDir.resolve(current + ".profile") : configs.getConfigFile(current).toPath();
                JsonObject written = readJson(currentFile).getAsJsonObject("modules").getAsJsonObject("bossbar_tweaker");
                check(written.get("hud_scale").getAsFloat() == 3 && !written.get("hud_auto_position").getAsBoolean(),
                        "new files store native-relative scale and manual positioning");
                JsonObject writtenSettings = profile ? written : written.getAsJsonObject("settings");
                check(!writtenSettings.has("Scale") && !writtenSettings.has("Y Offset") && !writtenSettings.has("Hide Bossbar"),
                        "new files contain no obsolete Bossbar Tweaker controls");
                resetBossBar();
                loadFile(profile, current);
                check(MODULE.getHudScale() == 3 && MODULE.renderHealth.isValue() && MODULE.renderText.isValue()
                        && MODULE.colorMode.isSelected("Rainbow") && MODULE.rainbowSpeed.getValue() == 1
                        && MODULE.fillColor.getColor() == FILL, "all new settings survive real file round-trip");
                close(MODULE.getHudXRatio(), 0.12F, "X ratio round-trip");
                close(MODULE.getHudYRatio(), 0.23F, "Y ratio round-trip");

                resetBossBar();
                MODULE.setStateSilent(false);
                String automatic = name + "_auto";
                if (profile) invoke(profiles, "saveProfile", new Class<?>[]{String.class}, automatic);
                else configs.saveConfig(automatic);
                MODULE.setHudPositionRatio(0.4F, 0.4F);
                loadFile(profile, automatic);
                check(!MODULE.isEnabled() && MODULE.isHudPositionAutomatic(), "never-enabled module retains automatic positioning after loading");
                MODULE.setStateSilent(true);
                ((BossBarHudAccess) client.inGameHud.getBossBarHud()).phaze$updateLayout();
                close(MODULE.getHudX(), (client.getWindow().getScaledWidth() / 2 - 91) * (float) client.getWindow().getScaleFactor(),
                        "first enable centers a previously saved untouched bar");
                check(MODULE.isHudPositionAutomatic(), "resolving the initial position does not mark it as a manual drag");
            }
            resetBossBar();
            MODULE.setStateSilent(false);
            String exported = configs.exportCurrentToString();
            check(decodeExport(exported).getAsJsonObject("modules").getAsJsonObject("bossbar_tweaker")
                    .get("hud_auto_position").getAsBoolean(), "portable config export preserves automatic positioning too");
        });
        System.out.println("Boss Bar legacy/new .Phaze and .profile disk loading, saving and export PASSED");
    }

    private static void addServerBars(TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerManager().getPlayerList().getFirst();
            for (int i = 0; i < 7; i++) {
                Text name = i == 5 ? Text.literal(LONG_NAME).formatted(Formatting.GOLD, Formatting.BOLD)
                        : Text.literal("Server Boss " + (i + 1));
                var bar = server.getBossBarManager().add(Identifier.of("phaze", "boss_test_" + i), name);
                bar.setColor(BossBar.Color.values()[i]);
                bar.setStyle(BossBar.Style.values()[i % BossBar.Style.values().length]);
                bar.setMaxValue(100);
                bar.setValue(75);
                bar.addPlayer(player);
            }
        });
    }

    private static void vanillaRendering(ClientGameTestContext context) {
        context.runOnClient(client -> {
            MODULE.setStateSilent(false);
            client.setScreen(new RenderFixture());
        });
        Path vanilla = screenshot(context, "vanilla-live-bars");
        int visible = context.computeOnClient(BossBarClientTest::visibleRows);
        context.runOnClient(client -> {
            check(BossBarProbe.renders == 1 && BossBarProbe.foreignRenders == 1 && BossBarProbe.textDraws == visible,
                    "disabled module still executes the original renderer and foreign hooks");
            resetBossBar();
            MODULE.renderText.setValue(true);
            MODULE.fillColor.setColor(FILL);
        });
        Path custom = screenshot(context, "custom-live-bars");
        context.runOnClient(client -> {
            verifyTint(true, visible);
            int longest = bars(client).values().stream().limit(visible).mapToInt(bar -> client.textRenderer.getWidth(bar.getName())).max().orElse(0);
            check(MODULE.getBaseWidth() >= longest + 1 && MODULE.getBaseHeight() == (visible - 1) * 19 + 14,
                    "editor bounds include long formatted names, their shadow and all visible rows");
            check(BossBarProbe.foreignRenders == 1 && BossBarProbe.foreignProgress == visible, "enabled module retains both foreign hook chains");
        });
        compareUnchangedPixels(context, vanilla, custom, visible);
        for (boolean health : new boolean[]{true, false}) {
            for (boolean text : new boolean[]{true, false}) {
                context.runOnClient(client -> {
                    MODULE.renderHealth.setValue(health);
                    MODULE.renderText.setValue(text);
                });
                screenshot(context, "visibility-health-" + health + "-text-" + text);
                context.runOnClient(client -> {
                    check(BossBarProbe.textDraws == (text ? visible : 0), "Render Text gates real vanilla name draws");
                    long backgrounds = BossBarProbe.quads.stream().filter(BossBarProbe.Quad::background).count();
                    check((backgrounds > 0) == health, "Render Health gates background and progress together");
                    check(BossBarProbe.foreignRenders == 1, "hiding visual elements does not cancel the top-level foreign renderer");
                    if (!text) check(BossBarProbe.widthReads == 0, "hidden names incur no text-width work");
                });
            }
        }
        for (int guiScale : new int[]{1, 2, 3}) {
            context.runOnClient(client -> client.options.getGuiScale().setValue(guiScale));
            context.getInput().resizeWindow(1280, 720);
            for (float scale : new float[]{0.5F, 1, 2, 3}) {
                context.runOnClient(client -> {
                    MODULE.renderHealth.setValue(true);
                    MODULE.renderText.setValue(false);
                    MODULE.setHudScale(scale);
                    MODULE.setHudPosition(40, 40, client.getWindow().getWidth(), client.getWindow().getHeight());
                });
                render(context, 2);
                context.runOnClient(client -> {
                    var background = BossBarProbe.quads.stream().filter(BossBarProbe.Quad::background)
                            .filter(quad -> !quad.texture().getPath().contains("notched")).findFirst().orElseThrow();
                    close(background.x2() - background.x1(), 182 * scale, "render width is exactly native 182px times the requested scale");
                    close(background.y2() - background.y1(), 5 * scale, "render height is native-relative too");
                    close(background.x1() * (float) client.getWindow().getScaleFactor(), MODULE.getHudX(), "draw position matches the editor's physical X");
                    close(background.y1() * (float) client.getWindow().getScaleFactor(), MODULE.getHudY(), "draw position matches the editor's physical Y");
                    close(MODULE.getRenderHudScale(), scale * (float) client.getWindow().getScaleFactor(), "GUI and HUD scale are independent");
                });
                if (guiScale == 2 && (scale == 0.5F || scale == 3)) screenshot(context, "native-scale-" + scale);
            }
        }
        context.runOnClient(client -> {
            client.options.getGuiScale().setValue(2);
            resetBossBar();
            MODULE.renderText.setValue(true);
            MODULE.fillColor.setColor(FILL);
        });
        context.getInput().resizeWindow(1280, 720);
        System.out.println("Boss Bar real vanilla passes, colors, long names, visibility and 0.5-3x at GUI scales 1/2/3 PASSED");
    }

    private static void packetAndReload(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            var bar = server.getBossBarManager().get(Identifier.of("phaze", "boss_test_0"));
            bar.setName(Text.literal(LONG_NAME + " - renamed by server").formatted(Formatting.AQUA, Formatting.BOLD));
            bar.setValue(50);
            bar.setStyle(BossBar.Style.NOTCHED_20);
        });
        context.waitFor(client -> bars(client).values().stream().anyMatch(bar -> bar.getName().getString().endsWith("renamed by server")));
        context.waitFor(client -> bars(client).values().stream().anyMatch(bar -> bar.getPercent() == 0.5F));
        render(context, 3);
        context.runOnClient(client -> {
            int longest = bars(client).values().stream().limit(visibleRows(client)).mapToInt(bar -> client.textRenderer.getWidth(bar.getName())).max().orElse(0);
            check(MODULE.getBaseWidth() >= longest + 1, "server rename invalidates the cached layout");
            var draw = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
            MODULE.invalidateNameWidths();
            BossBarProbe.startWidths();
            client.inGameHud.getBossBarHud().render(draw);
            int first = BossBarProbe.widthReads;
            draw.draw();
            BossBarProbe.startWidths();
            client.inGameHud.getBossBarHud().render(draw);
            draw.draw();
            check(first == visibleRows(client) && BossBarProbe.widthReads == 0,
                    "unchanged names are measured once, not every rendered frame: first=" + first + ", second=" + BossBarProbe.widthReads);
            check(BossBarProbe.images == 0 && BossBarProbe.uploads == 0 && BossBarProbe.registrations == 0,
                    "normal draw does not create, upload or register textures");
            BossBarProbe.stop();
        });
        var reload = context.computeOnClient(MinecraftClient::reloadResources);
        context.waitFor(client -> reload.isDone() && client.getOverlay() == null);
        check(!reload.isCompletedExceptionally(), "real resource reload succeeds with live bars");
        screenshot(context, "after-resource-reload");
        context.runOnClient(client -> verifyTint(true, visibleRows(client)));
        System.out.println("Boss Bar real server update, width cache and resource reload PASSED");
    }

    private static void foreignHooks(ClientGameTestContext context) {
        context.runOnClient(client -> {
            MODULE.setHudScale(1.3F);
            MODULE.setHudPosition(90, 70, client.getWindow().getWidth(), client.getWindow().getHeight());
            for (int mode = 0; mode < 5; mode++) {
                BossBarProbe.clearForeignHooks();
                BossBarProbe.cancelRender = mode == 0;
                BossBarProbe.failRender = mode == 1;
                BossBarProbe.cancelProgress = mode == 2;
                BossBarProbe.failProgress = mode == 3;
                BossBarProbe.replaceProgress = mode == 4;
                var draw = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
                draw.getMatrices().translate(7.25F, 11.5F, 30);
                Matrix4f before = new Matrix4f(draw.getMatrices().peek().getPositionMatrix());
                int previous = BossBarColorContext.setTint(0xFF80A0C0);
                boolean thrown = false;
                BossBarProbe.start();
                try {
                    client.inGameHud.getBossBarHud().render(draw);
                } catch (BossBarProbe.ForeignRenderException expected) {
                    thrown = true;
                } finally {
                    check(draw.getMatrices().isEmpty() && draw.getMatrices().peek().getPositionMatrix().equals(before, 0.00001F),
                            "foreign cancellation/exception leaves the caller's complete matrix stack intact: " + mode);
                    check(BossBarColorContext.applyTint(0xFFFFFFFF) == 0xFF80A0C0, "foreign cancellation/exception restores the caller's tint: " + mode);
                    BossBarColorContext.setTint(previous);
                    draw.draw();
                }
                check(thrown == (mode == 1 || mode == 3), "foreign exceptions propagate unchanged");
                check(BossBarProbe.foreignRenders == 1, "foreign top-level injection was really reached");
                if (mode == 2) check(BossBarProbe.quads.stream().noneMatch(BossBarProbe.Quad::progress), "foreign cancellation suppresses progress only");
                if (mode == 4) check(BossBarProbe.quads.stream().anyMatch(quad -> quad.texture().equals(BossBarProbe.FOREIGN_TEXTURE)),
                        "a foreign replacement sprite survives the neutral-texture hook");
                BossBarProbe.stop();
            }
            BossBarProbe.clearForeignHooks();
            resetBossBar();
        });
        System.out.println("Boss Bar foreign cancellation, exceptions, replacement textures, matrix and tint restoration PASSED");
    }

    private static void gameplayAndCache(ClientGameTestContext context, boolean exordium) {
        context.runOnClient(client -> {
            client.setScreen(null);
            MODULE.fillColor.setColor(FILL);
        });
        render(context, 24);
        context.runOnClient(client -> {
            BossBarProbe.start();
            render(client, 120);
            if (exordium) {
                check(BossBarProbe.bufferCalls == 120 && BossBarProbe.renders <= 1 && BossBarProbe.cachedDraws >= 119,
                        "real Exordium serves unchanged Custom from its cache: renders=" + BossBarProbe.renders + ", cached=" + BossBarProbe.cachedDraws);
                check(!(boolean) BossBarProbe.field(ExordiumAnimationBridge.class, "reflectionFailed", null), "Exordium bridge reflection succeeded");
            } else {
                check(BossBarProbe.renders == 120 && BossBarProbe.foreignRenders == 120, "the real gameplay layer retains the vanilla renderer every frame");
            }
            BossBarProbe.stop();
            MODULE.fillColor.setColor(0xFFEEAA33);
            BossBarProbe.start();
            render(client, 1);
            check(!exordium || BossBarProbe.captures == 1, "Custom picker change immediately invalidates the real Exordium capture");
            BossBarProbe.stop();
            MODULE.colorMode.setSelected("Rainbow");
            MODULE.rainbowSpeed.setValue(1);
            BossBarProbe.start();
            long started = System.nanoTime();
            render(client, 240);
            long elapsed = System.nanoTime() - started;
            if (exordium) check(BossBarProbe.captures <= 2 + (long) Math.ceil(elapsed * 60.0 / 1_000_000_000),
                    "real Rainbow captures remain within the 60 FPS budget: " + BossBarProbe.captures);
            check(BossBarProbe.quads.stream().filter(BossBarProbe.Quad::progress).map(BossBarProbe.Quad::color).distinct().count() > 1,
                    "real Rainbow changes vertex colors across display frames");
            check(BossBarProbe.images == 0 && BossBarProbe.uploads == 0 && BossBarProbe.registrations == 0,
                    "Rainbow reuses the existing texture resources");
            BossBarProbe.stop();
            NoRender.getInstance().setStateSilent(true);
            NoRender.getInstance().particleTypes.selected("Boss Bar");
            BossBarProbe.start();
            render(client, 3);
            check(BossBarProbe.renders == 0, "NoRender suppresses the actual vanilla/Exordium boss-bar layer");
            if (exordium) {
                var map = (Map<?, ?>) BossBarProbe.field(ExordiumAnimationBridge.class, "COMPONENT_BY_BUFFER", null);
                Object buffer = map.entrySet().stream().filter(entry -> ExordiumAnimationBridge.BOSS_BAR.equals(entry.getValue()))
                        .map(Map.Entry::getKey).findFirst().orElseThrow();
                check(ExordiumAnimationBridge.deferBufferedComponent(buffer), "NoRender also suppresses a previously cached boss-bar texture");
            }
            BossBarProbe.stop();
            NoRender.getInstance().setStateSilent(false);
            client.options.hudHidden = true;
            BossBarProbe.start();
            render(client, 3);
            check(BossBarProbe.renders == 0, "F1 suppresses live boss bars too");
            BossBarProbe.stop();
            client.options.hudHidden = false;
            resetBossBar();
        });
        screenshot(context, exordium ? "exordium-live-custom" : "gameplay-live-custom");
        System.out.println("Boss Bar real gameplay, NoRender, F1 and " + (exordium ? "Exordium Custom/Rainbow cache" : "vanilla path") + " PASSED");
    }

    private static void cachedResourceReload(ClientGameTestContext context) {
        context.runOnClient(client -> {
            MODULE.renderText.setValue(true);
            MODULE.fillColor.setColor(FILL);
        });
        render(context, 24);
        context.runOnClient(client -> {
            BossBarProbe.start();
            render(client, 120);
            check(BossBarProbe.bufferCalls == 120 && BossBarProbe.captures <= 1 && BossBarProbe.cachedDraws >= 119,
                    "resource reload starts with a warmed, unchanged Exordium Custom cache");
            BossBarProbe.stop();
        });
        var reload = context.computeOnClient(client -> {
            BossBarProbe.start();
            return client.reloadResources();
        });
        context.waitFor(client -> reload.isDone() && client.getOverlay() == null);
        check(!reload.isCompletedExceptionally(), "resource reload succeeds with an already-cached Exordium Custom bar");
        render(context, 4);
        context.runOnClient(client -> {
            check(BossBarProbe.captures >= 1 && BossBarProbe.renders >= 1,
                    "resource reload refreshes the real Exordium boss-bar capture without changing a setting");
            check(BossBarProbe.quads.stream().filter(BossBarProbe.Quad::progress).anyMatch(quad -> quad.color() == FILL),
                    "the refreshed Exordium capture retains Custom progress tint");
            check(BossBarProbe.textDraws >= visibleRows(client), "the refreshed cache redraws the live vanilla names");
            check(!(boolean) BossBarProbe.field(ExordiumAnimationBridge.class, "reflectionFailed", null),
                    "the Exordium bridge remains usable after resource reload");
            BossBarProbe.stop();
        });
        screenshot(context, "exordium-custom-after-warmed-reload");
        context.runOnClient(client -> {
            BossBarProbe.start();
            render(client, 120);
            check(BossBarProbe.captures <= 1 && BossBarProbe.cachedDraws >= 119,
                    "unchanged Custom returns to stable Exordium cache reuse after reload");
            BossBarProbe.stop();
            resetBossBar();
        });
        System.out.println("Boss Bar warmed Exordium Custom cache, real resource reload and stable cache reuse PASSED");
    }

    private static void editor(ClientGameTestContext context) {
        try (var mouse = new NativeMouseInput()) {
            context.getInput().resizeWindow(1600, 900);
            context.runOnClient(client -> {
                resetBossBar();
                MODULE.setHudPosition(80, 80, 1600, 900);
                Animations.getInstance().setStateSilent(true);
                Animations.getInstance().dynamicCursor.setValue(true);
                client.setScreen(new ChatScreen(""));
            });
            render(context, 5);
            screenshot(context, "editor-live-bars");
            context.getInput().setCursorPos(110, 100);
            render(context, 2);
            context.getInput().holdMouse(0);
            context.runOnClient(client -> {
                check(((BossBarTestMixins.MouseState) client.mouse).phazeTest$getActiveButton() == 0,
                        "Fabric delivered the synthetic mouse callback");
                check(GLFW.glfwGetMouseButton(client.getWindow().getHandle(), 0) == GLFW.GLFW_RELEASE,
                        "Fabric's callback does not alter native GLFW button state");
            });
            mouse.hold(context);
            render(context, 2);
            context.runOnClient(client -> check(interaction("RECT_DRAGGING"), "native chat mouse press begins the shared Boss Bar drag"));
            context.getInput().setCursorPos(240, 250);
            render(context, 3);
            context.runOnClient(client -> {
                close(MODULE.getHudX(), 210, "drag preserves pointer X offset");
                close(MODULE.getHudY(), 230, "drag preserves pointer Y offset");
                check(!MODULE.isHudPositionAutomatic(), "a real drag switches to saved manual positioning");
                check((int) BossBarProbe.field(CursorManager.class, "lastAppliedShape", null) == CursorManager.SHAPE_MOVE,
                        "shared HUD drag relay supplies the move cursor");
            });
            screenshot(context, "editor-drag-white-frame");
            mouse.release(context);
            render(context, 2);
            context.runOnClient(client -> {
                check(!interaction("RECT_DRAGGING"), "release ends the drag");
                MODULE.setHudPosition(80, 80, 1600, 900);
            });
            render(context, 2);
            double[] corner = context.computeOnClient(client -> new double[]{MODULE.getHudX() + MODULE.getBaseWidth() * MODULE.getRenderHudScale(),
                    MODULE.getHudY() + MODULE.getBaseHeight() * MODULE.getRenderHudScale()});
            context.getInput().setCursorPos(corner[0], corner[1]);
            render(context, 2);
            mouse.hold(context);
            render(context, 2);
            context.runOnClient(client -> {
                check(interaction("RECT_RESIZING"), "the actual bottom-right corner starts shared resizing");
                check((int) BossBarProbe.field(CursorManager.class, "lastAppliedShape", null) == CursorManager.SHAPE_HRESIZE,
                        "shared corner resize relay supplies the resize cursor");
            });
            context.getInput().setCursorPos(1550, 860);
            render(context, 3);
            context.runOnClient(client -> {
                close(MODULE.getHudScale(), 3, "real corner drag reaches the common 3x maximum");
                check("x3.00".equals(BossBarProbe.field(ScaleSnapOverlay.class, "valueText", null)), "large chat readout shows the actual native-relative scale");
            });
            screenshot(context, "editor-resize-3x");
            context.runOnClient(client -> {
                check(GLFW.glfwGetMouseButton(client.getWindow().getHandle(), 0) == GLFW.GLFW_PRESS,
                        "native mouse stays held across the resize screenshot; focused=" + client.isWindowFocused());
                check(interaction("RECT_RESIZING"), "shared resizing remains active across the screenshot");
            });
            context.getInput().setCursorPos(50, 50);
            render(context, 3);
            context.runOnClient(client -> close(MODULE.getHudScale(), 0.5F,
                    "real corner drag reaches the 0.5x minimum; native="
                            + GLFW.glfwGetMouseButton(client.getWindow().getHandle(), 0)
                            + ", resizing=" + interaction("RECT_RESIZING")
                            + ", pointer=" + client.mouse.getX() + "," + client.mouse.getY()
                            + ", focused=" + client.isWindowFocused()));
            screenshot(context, "editor-resize-half");
            mouse.release(context);
            render(context, 2);
            context.runOnClient(client -> check(!interaction("RECT_RESIZING"), "release ends resizing"));
            for (boolean disable : new boolean[]{true, false}) {
                context.runOnClient(client -> {
                    MODULE.setStateSilent(true);
                    MODULE.setHudScale(1);
                    MODULE.setHudPosition(80, 80, 1600, 900);
                    client.setScreen(new ChatScreen(""));
                });
                context.getInput().setCursorPos(110, 100);
                render(context, 2);
                mouse.hold(context);
                render(context, 2);
                context.runOnClient(client -> {
                    check(interaction("RECT_DRAGGING"), "drag is live before interruption");
                    if (disable) MODULE.setStateSilent(false);
                    else client.setScreen(null);
                });
                render(context, 2);
                context.runOnClient(client -> check(!interaction("RECT_DRAGGING") && !interaction("RECT_RESIZING"),
                        "disable/exit chat clears both interaction guards"));
                mouse.release(context);
            }
        }
        context.runOnClient(client -> {
            resetBossBar();
            Animations.getInstance().setStateSilent(false);
            client.setScreen(null);
        });
        context.getInput().resizeWindow(1280, 720);
        System.out.println("Boss Bar shared white editor, drag/release, corner resize 0.5-3x and interrupted interaction PASSED");
    }

    private static void performance(ClientGameTestContext context, boolean exordium) {
        context.runOnClient(client -> {
            resetBossBar();
            MODULE.renderText.setValue(true);
            MODULE.fillColor.setColor(0xFFFFFFFF);
            client.setScreen(null);
            render(client, 100);
            List<Double> vanilla = new ArrayList<>(), custom = new ArrayList<>(), rainbow = new ArrayList<>();
            List<Double> vanillaBoss = new ArrayList<>(), customBoss = new ArrayList<>(), rainbowBoss = new ArrayList<>();
            for (int round = 0; round < 12; round++) {
                for (int pass = 0; pass < 3; pass++) {
                    int mode = (round + (round % 2 == 0 ? pass : 2 - pass)) % 3;
                    MODULE.setStateSilent(mode != 0);
                    MODULE.colorMode.setSelected(mode == 2 ? "Rainbow" : "Custom");
                    render(client, 8);
                    double[] result = measureFrames(client, 60);
                    (mode == 0 ? vanilla : mode == 1 ? custom : rainbow).add(result[0]);
                    (mode == 0 ? vanillaBoss : mode == 1 ? customBoss : rainbowBoss).add(result[1]);
                }
            }
            double base = median(vanilla), current = median(custom), animated = median(rainbow);
            System.out.printf(Locale.ROOT, "Boss Bar performance%s: synchronized full-frame median vanilla=%.3f ms, Custom=%.3f ms, Rainbow=%.3f ms; boss renderer vanilla=%.3f us, Custom=%.3f us, Rainbow=%.3f us%n",
                    exordium ? " + Exordium" : "", base, current, animated, median(vanillaBoss) * 1000, median(customBoss) * 1000, median(rainbowBoss) * 1000);
            // Alternate samples, synchronize the GPU, and allow normal renderer/OS noise rather than promising identical nanoseconds.
            check(current <= base * 1.05 + 0.05, "Custom has no measurable full-frame regression against the identical vanilla scene");
            check(animated <= base * 1.10 + 0.10, "Rainbow remains close to vanilla full-frame cost");
            resetBossBar();
        });
    }

    private static double[] measureFrames(MinecraftClient client, int frames) {
        GL11.glFinish();
        BossBarProbe.renders = 0;
        BossBarProbe.renderNanos = 0;
        BossBarProbe.timing = true;
        long started = System.nanoTime();
        try {
            for (int frame = 0; frame < frames; frame++) {
                client.gameRenderer.render(RenderTickCounter.ZERO, true);
                GL11.glFinish();
            }
        } finally {
            BossBarProbe.timing = false;
        }
        return new double[]{(System.nanoTime() - started) / (frames * 1_000_000.0),
                BossBarProbe.renderNanos / (frames * 1_000_000.0)};
    }

    private static void removeServerBars(TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            for (var bar : List.copyOf(server.getBossBarManager().getAll())) {
                bar.clearPlayers();
                server.getBossBarManager().remove(bar);
            }
        });
    }

    private static void preview(ClientGameTestContext context) {
        context.runOnClient(client -> {
            resetBossBar();
            MODULE.setHudPosition(80, 80, client.getWindow().getWidth(), client.getWindow().getHeight());
            MODULE.renderText.setValue(true);
            client.setScreen(new ChatScreen(""));
            BossBarProbe.start();
            render(client, 3);
            check(bars(client).isEmpty() && !MODULE.hasLiveBars(), "preview never inserts a fake server event into the live map");
            check(BossBarProbe.foreignProgress > 0 && BossBarProbe.quads.stream().anyMatch(BossBarProbe.Quad::progress),
                    "empty-map editor preview still reaches vanilla progress and foreign hooks");
            check(BossBarColorContext.applyTint(0xFFFFFFFF) == 0xFFFFFFFF, "preview leaves subsequent GUI vertex tint untouched");
            BossBarProbe.stop();
            var access = (BossBarHudAccess) client.inGameHud.getBossBarHud();
            var draw = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
            MODULE.invalidateNameWidths();
            BossBarProbe.startWidths();
            access.phaze$updateLayout();
            access.phaze$renderPreview(draw);
            draw.draw();
            int first = BossBarProbe.widthReads;
            BossBarProbe.startWidths();
            access.phaze$updateLayout();
            access.phaze$renderPreview(draw);
            draw.draw();
            check(first == 1 && BossBarProbe.widthReads == 0, "preview name width is cached across frames too");
            MODULE.renderText.setValue(false);
            MODULE.invalidateNameWidths();
            BossBarProbe.startWidths();
            access.phaze$updateLayout();
            access.phaze$renderPreview(draw);
            draw.draw();
            check(BossBarProbe.widthReads == 0, "hidden preview text is never measured");
            BossBarProbe.stop();
            MODULE.renderText.setValue(true);
        });
        screenshot(context, "editor-empty-preview");
        context.runOnClient(client -> {
            client.setScreen(null);
            BossBarProbe.start();
            render(client, 2);
            check(BossBarProbe.quads.stream().noneMatch(BossBarProbe.Quad::progress), "preview appears only in chat, never ordinary gameplay");
            BossBarProbe.stop();
        });
        System.out.println("Boss Bar empty-map vanilla preview and chat-only visibility PASSED");
    }

    private static void verifyTint(boolean text, int visible) {
        check(BossBarProbe.renders == 1 && BossBarProbe.textDraws == (text ? visible : 0), "one real vanilla render with the expected name draws");
        for (var quad : BossBarProbe.quads) {
            if (!quad.inBossRender()) {
                check(quad.color() == 0xFFFFFFFF, "later GUI sentinel is not tinted");
            } else if (quad.progress()) {
                check(quad.color() == MODULE.getActiveFillColor(), "only progress vertices receive the selected fill color");
                if (!quad.texture().getPath().contains("notched")) check(quad.texture().equals(SENTINEL), "base progress uses the neutral white vanilla sprite");
            } else if (quad.background()) {
                check(quad.color() == 0xFFFFFFFF, "background vertices retain their original white multiplier");
            }
        }
        check(BossBarProbe.quads.stream().anyMatch(BossBarProbe.Quad::progress), "real progress quads were observed, not just settings");
        check(BossBarColorContext.applyTint(0xFFFFFFFF) == 0xFFFFFFFF, "tint is restored after the complete vanilla render");
    }

    private static void compareUnchangedPixels(ClientGameTestContext context, Path vanilla, Path custom, int rows) {
        int gui = context.computeOnClient(client -> (int) client.getWindow().getScaleFactor());
        try (var inA = Files.newInputStream(vanilla); var inB = Files.newInputStream(custom);
             NativeImage first = NativeImage.read(inA); NativeImage second = NativeImage.read(inB)) {
            int left = first.getWidth() / 2 - 91 * gui;
            int changedFill = 0;
            for (int row = 0; row < rows; row++) {
                for (int y = (3 + row * 19) * gui; y < (11 + row * 19) * gui; y++) {
                    for (int x = 0; x < first.getWidth(); x++) {
                        check(first.getColorArgb(x, y) == second.getColorArgb(x, y), "custom fill leaves actual name pixels and shadows unchanged");
                    }
                }
                for (int y = (12 + row * 19) * gui; y < (17 + row * 19) * gui; y++) {
                    for (int x = left + 160 * gui; x < left + 180 * gui; x++) {
                        check(first.getColorArgb(x, y) == second.getColorArgb(x, y), "custom fill leaves unfilled background/notch pixels unchanged");
                    }
                    for (int x = left + 10 * gui; x < left + 100 * gui; x++) {
                        if (first.getColorArgb(x, y) != second.getColorArgb(x, y)) changedFill++;
                    }
                }
            }
            check(changedFill > 500, "custom fill really changed framebuffer pixels");
            for (int y = first.getHeight() - 60; y < first.getHeight() - 20; y++) {
                for (int x = 0; x < 420; x++) {
                    check(first.getColorArgb(x, y) == second.getColorArgb(x, y), "later GUI sprite/text sentinel is pixel-identical");
                }
            }
        } catch (IOException e) {
            throw new AssertionError("Could not compare real boss-bar framebuffer pixels", e);
        }
    }

    private static void requireIsolatedRun(MinecraftClient client) {
        try {
            Path run = client.runDirectory.toPath().toRealPath();
            check(System.getProperty("fabric.client.gametest") != null && run.getParent().getFileName().equals(Path.of("build")),
                    "Boss Bar integration requires an isolated build/ runner");
            check(ConfigManager.getInstance().getConfigsDir().toPath().toRealPath().startsWith(run), "all config writes stay inside the runner");
        } catch (IOException e) {
            throw new AssertionError("Could not verify test isolation", e);
        }
    }

    private static void resetBossBar() {
        MODULE.settings().forEach(Setting::reset);
        MODULE.resetHudTransform();
        MODULE.invalidateNameWidths();
        MODULE.setStateSilent(true);
    }

    private static void disableModules() {
        for (Module module : Main.getInstance().getModuleProvider().getModules()) module.setStateSilent(false);
    }

    private static Map<UUID, ClientBossBar> bars(MinecraftClient client) {
        return ((BossBarTestMixins.Bars) client.inGameHud.getBossBarHud()).phazeTest$getBars();
    }

    private static int visibleRows(MinecraftClient client) {
        return Math.min(bars(client).size(), Math.max(1, (client.getWindow().getScaledHeight() / 3 - 12 + 18) / 19));
    }

    private static boolean interaction(String field) {
        return ((boolean[]) BossBarProbe.field(InGameHud.class, field, null))[HUD_INDEX];
    }

    private static AbstractSettingComponent row(Setting setting) {
        return MenuScreen.INSTANCE.getModuleDetailComponent().getSettingComponents().stream()
                .filter(component -> component.getSetting() == setting).findFirst().orElseThrow();
    }

    private static void menuClick(ClientGameTestContext context, double x, double y) {
        menuHover(context, x, y);
        context.getInput().pressMouse(0);
        render(context, 3);
    }

    private static void menuHover(ClientGameTestContext context, double x, double y) {
        double[] point = context.computeOnClient(client -> {
            var menu = MenuScreen.INSTANCE;
            double scale = menu.getOverlayRenderScale() * client.getWindow().getScaleFactor();
            double guiScale = MenuUiSettings.getInstance().getGuiScale();
            double centerX = menu.x + menu.width / 2, centerY = menu.y + menu.height / 2;
            return new double[]{(centerX + (x - centerX) * guiScale) * scale,
                    (centerY + (y - centerY) * guiScale) * scale};
        });
        context.getInput().setCursorPos(point[0], point[1]);
        render(context, 3);
    }

    private static Path screenshot(ClientGameTestContext context, String name) {
        context.waitTicks(5);
        render(context, 4);
        Path screenshot = context.takeScreenshot("boss-bar-" + name);
        System.out.println("Boss Bar screenshot: " + screenshot);
        return screenshot;
    }

    private static void render(ClientGameTestContext context, int frames) { context.runOnClient(client -> render(client, frames)); }

    private static void render(MinecraftClient client, int frames) {
        for (int frame = 0; frame < frames; frame++) client.gameRenderer.render(RenderTickCounter.ZERO, true);
    }

    private static double median(List<Double> values) {
        double[] sorted = values.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(sorted);
        return (sorted[(sorted.length - 1) / 2] + sorted[sorted.length / 2]) / 2;
    }

    private static void loadFile(boolean profile, String name) {
        if (profile) check((boolean) invoke(MenuProfileManager.getInstance(), "loadProfile", new Class<?>[]{String.class}, name), "actual profile loader succeeds");
        else ConfigManager.getInstance().loadConfig(name);
    }

    private static void writeJson(Path path, JsonObject json) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, json.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) { throw new AssertionError("Could not write isolated fixture " + path, e); }
    }

    private static JsonObject readJson(Path path) {
        try { return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (IOException e) { throw new AssertionError("Could not read isolated fixture " + path, e); }
    }

    private static JsonObject decodeExport(String value) {
        check(value != null && value.startsWith("PHAZE1:"), "config export has the expected portable format");
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(value.substring(7))))) {
            return JsonParser.parseString(new String(gzip.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) { throw new AssertionError("Could not decode exported configuration", e); }
    }

    private static Object invoke(Object target, String name) { return invoke(target, name, new Class<?>[0]); }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... arguments) {
        try {
            var method = target.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(target, arguments);
        } catch (InvocationTargetException e) {
            throw new AssertionError("Real method failed: " + name, e.getCause());
        } catch (ReflectiveOperationException e) { throw new AssertionError("Could not invoke " + name, e); }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) { throw new AssertionError("Could not restore " + name, e); }
    }

    private static void close(float actual, float expected, String message) { check(Math.abs(actual - expected) < 0.002F, message + ": " + actual + " != " + expected); }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private record SavedState(JsonObject config, Object activeFile, String activeName, String language, Screen screen,
                              int width, int height, int guiScale, int viewDistance, boolean hudHidden, boolean pause,
                              Consumer<Setting> settings, Consumer<Module> modules) {
        @SuppressWarnings("unchecked")
        static SavedState capture(MinecraftClient client) {
            var configs = ConfigManager.getInstance();
            return new SavedState((JsonObject) invoke(configs, "buildCurrentConfigJson"), BossBarProbe.field(ConfigManager.class, "currentConfig", configs),
                    configs.getCurrentConfigName(), Lang.getActive(), client.currentScreen, client.getWindow().getWidth(), client.getWindow().getHeight(),
                    client.options.getGuiScale().getValue(), client.options.getViewDistance().getValue(), client.options.hudHidden, client.options.pauseOnLostFocus,
                    (Consumer<Setting>) BossBarProbe.field(Setting.class, "globalChangeListener", null),
                    (Consumer<Module>) BossBarProbe.field(Module.class, "globalStateChangeListener", null));
        }

        void restore(MinecraftClient client) {
            try {
                var configs = ConfigManager.getInstance();
                invoke(configs, "applyInCodeDefaults");
                invoke(configs, "applyConfigJson", new Class<?>[]{JsonObject.class}, config);
                setField(configs, "currentConfig", activeFile);
                configs.setCurrentConfigName(activeName);
                Lang.setActive(language);
                client.options.getGuiScale().setValue(guiScale);
                client.options.getViewDistance().setValue(viewDistance);
                client.options.hudHidden = hudHidden;
                client.options.pauseOnLostFocus = pause;
                client.setScreen(screen);
                CursorManager.forceArrow();
            } finally {
                Setting.setGlobalChangeListener(settings);
                Module.setGlobalStateChangeListener(modules);
            }
        }
    }

    private static final class RenderFixture extends Screen {
        RenderFixture() { super(Text.literal("Vanilla Boss Bar framebuffer reference")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 400);
            context.fill(0, 0, width, height, 0xFF242424);
            context.draw();
            BossBarProbe.start();
            client.inGameHud.getBossBarHud().render(context);
            context.drawGuiTexture(RenderLayer::getGuiTextured, SENTINEL, 182, 5, 0, 0, 12, height - 28, 182, 5);
            context.drawTextWithShadow(textRenderer, "GUI sentinel", 12, height - 16, 0xFFFFFFFF);
            context.draw();
            BossBarProbe.stop();
            context.getMatrices().pop();
        }

        @Override
        public boolean shouldPause() { return false; }

        @Override
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
    }
}
