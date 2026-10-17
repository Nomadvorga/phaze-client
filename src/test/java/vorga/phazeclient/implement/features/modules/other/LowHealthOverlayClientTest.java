package vorga.phazeclient.implement.features.modules.other;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.text.Text;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.implement.config.ConfigManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Consumer;

/** Exercises the real GameRenderer hook with a server-backed player, never calls warning.render directly. */
public final class LowHealthOverlayClientTest implements FabricClientGameTest {
    private static final int RED = 0xFFFF0000;
    private static final String OBJECTIVE = "phaze_low_hp";

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        SavedState saved = context.computeOnClient(client -> {
            requireIsolatedRun(client);
            check(client.world == null && client.player == null, "fixture starts without an existing world");
            return SavedState.capture(client);
        });
        TestSingleplayerContext world = null;
        Path save = null;
        try {
            context.runOnClient(client -> {
                // Setting setters normally trigger debounced config saves. Restore these listeners last.
                Setting.setGlobalChangeListener(null);
                Module.setGlobalStateChangeListener(null);
                var warning = LowHealthWarning.getInstance();
                warning.setStateSilent(false);
                warning.stopHeartbeat();
                warning.heartbeatSound.setValue(false);
                warning.threshold.setValue(6);
                warning.pulseSpeed.setValue(1);
                warning.color.setColor(RED);
                client.options.pauseOnLostFocus = false;
                client.options.hudHidden = false;
                client.options.getGuiScale().setValue(2);
                if (client.inGameHud.getDebugHud().shouldShowDebugHud()) client.inGameHud.getDebugHud().toggleDebugHud();
                LowHealthOverlayProbe.reset();
            });
            context.getInput().resizeWindow(854, 480);
            world = context.worldBuilder().adjustSettings(creator -> {
                creator.setWorldName("Phaze Low HP overlay integration");
                creator.setGameMode(WorldCreator.Mode.SURVIVAL);
                creator.setDifficulty(Difficulty.NORMAL);
                creator.setCheatsEnabled(true);
                creator.getGameRules().get(GameRules.NATURAL_REGENERATION).set(false, null);
            }).create();
            save = world.getWorldSave().getSaveDirectory();
            Path run = context.computeOnClient(client -> client.runDirectory.toPath().toRealPath());
            check(save.toRealPath().startsWith(run.resolve("saves")), "test world belongs to the isolated runner");
            world.getClientWorld().waitForChunksRender();
            context.waitFor(client -> client.player != null && client.world != null && client.getOverlay() == null);
            context.runOnClient(client -> client.setScreen(null));
            setPlayer(context, world, GameMode.SURVIVAL, 4);
            context.runOnClient(client -> {
                LowHealthWarning.getInstance().setStateSilent(true);
                check(LowHealthWarning.getInstance().isEnabled(), "warning really enabled in singleplayer");
            });

            sample(context, "survival-hud", true, false, false, null, false);
            world.getServer().runCommand("scoreboard objectives add " + OBJECTIVE + " dummy \"Low HP overlay fixture\"");
            world.getServer().runCommand("scoreboard players set VisibleRow " + OBJECTIVE + " 7");
            world.getServer().runCommand("scoreboard objectives setdisplay sidebar " + OBJECTIVE);
            context.waitFor(client -> {
                var objective = client.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
                return objective != null && objective.getName().equals(OBJECTIVE)
                        && !client.world.getScoreboard().getScoreboardEntries(objective).isEmpty();
            });
            sample(context, "scoreboard", true, false, true, null, false);
            context.getInput().pressKey(GLFW.GLFW_KEY_F3);
            context.waitFor(client -> client.inGameHud.getDebugHud().shouldShowDebugHud());
            sample(context, "f3-and-scoreboard", true, true, true, null, false);
            context.getInput().pressKey(GLFW.GLFW_KEY_F3);
            context.waitFor(client -> !client.inGameHud.getDebugHud().shouldShowDebugHud());

            // Use the actual inventory key and real handler/player, not a surrogate screen.
            context.getInput().pressKey(options -> options.inventoryKey);
            context.waitForScreen(InventoryScreen.class);
            context.runOnClient(client -> check(((InventoryScreen) client.currentScreen).getScreenHandler()
                    == client.player.playerScreenHandler, "inventory uses the live player's screen handler"));
            sample(context, "inventory", true, false, true, InventoryScreen.class, false);
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitForScreen(null);
            context.getInput().pressKey(options -> options.chatKey);
            context.waitForScreen(ChatScreen.class);
            sample(context, "chat", true, false, true, ChatScreen.class, false);

            context.runOnClient(client -> {
                client.getToastManager().clear();
                client.setScreen(new WhiteReferenceScreen());
            });
            sample(context, "white-ui-active", true, false, true, WhiteReferenceScreen.class, true);
            context.runOnClient(client -> LowHealthWarning.getInstance().color.setColor(0x40FF0000));
            sample(context, "white-ui-quarter-alpha", true, false, true, WhiteReferenceScreen.class, true);
            context.runOnClient(client -> LowHealthWarning.getInstance().color.setColor(0x00FF0000));
            sample(context, "alpha-zero", false, false, true, WhiteReferenceScreen.class, true);
            context.runOnClient(client -> {
                LowHealthWarning.getInstance().color.setColor(RED);
                LowHealthWarning.getInstance().setStateSilent(false);
            });
            sample(context, "disabled", false, false, true, WhiteReferenceScreen.class, true);
            context.runOnClient(client -> LowHealthWarning.getInstance().setStateSilent(true));
            setPlayer(context, world, GameMode.SURVIVAL, 7); // Just above the threshold.
            sample(context, "healthy", false, false, true, WhiteReferenceScreen.class, true);
            setPlayer(context, world, GameMode.SURVIVAL, 6); // Inclusive boundary.
            sample(context, "at-threshold", true, false, true, WhiteReferenceScreen.class, true);
            setPlayer(context, world, GameMode.CREATIVE, 4);
            sample(context, "creative", false, false, true, WhiteReferenceScreen.class, true);
            setPlayer(context, world, GameMode.SPECTATOR, 4);
            sample(context, "spectator", false, false, true, WhiteReferenceScreen.class, true);
            setPlayer(context, world, GameMode.SURVIVAL, 4);
            sample(context, "survival-reactivated", true, false, true, WhiteReferenceScreen.class, true);
        } catch (IOException e) {
            throw new RuntimeException("Could not inspect isolated overlay world/screenshot", e);
        } finally {
            try {
                context.runOnClient(client -> {
                    LowHealthOverlayProbe.stop();
                    LowHealthWarning.getInstance().setStateSilent(false);
                    LowHealthWarning.getInstance().stopHeartbeat();
                    client.setScreen(null);
                });
                if (world != null) world.close(); // Disconnect and await integrated-server shutdown before deleting.
                if (save != null) deleteFixtureWorld(context, save);
            } finally {
                try {
                    context.getInput().resizeWindow(saved.width(), saved.height());
                } finally {
                    context.runOnClient(client -> saved.restore(client));
                }
            }
        }
        context.runOnClient(client -> check(client.world == null && client.player == null, "test world disconnected"));
        System.out.println("Low health real-world overlay integration PASSED");
    }

    private static void setPlayer(ClientGameTestContext context, TestSingleplayerContext world, GameMode mode, float hp) {
        world.getServer().runOnServer(server -> {
            check(server.getPlayerManager().getPlayerList().size() == 1, "one isolated server player");
            var player = server.getPlayerManager().getPlayerList().getFirst();
            player.changeGameMode(mode);
            player.setHealth(hp);
        });
        context.waitFor(client -> client.interactionManager.getCurrentGameMode() == mode
                && client.player.getHealth() == hp && client.player.isCreative() == (mode == GameMode.CREATIVE)
                && client.player.isSpectator() == (mode == GameMode.SPECTATOR));
    }

    private static void sample(ClientGameTestContext context, String name, boolean active, boolean debug,
                               boolean scoreboard, Class<? extends Screen> screen, boolean pixels) throws IOException {
        context.runOnClient(client -> {
            check(client.getOverlay() == null, name + ": no loading overlay");
            LowHealthOverlayProbe.start();
            // The screenshot API also invokes the real GameRenderer. Explicit rendering avoids
            // relying on waitTicks producing frames (the gametest runner can run ticks headlessly).
            for (int i = 0; i < 5; i++) client.gameRenderer.render(RenderTickCounter.ZERO, true);
        });
        Path screenshot = context.takeScreenshot("low-health-overlay-" + name);
        context.runOnClient(client -> LowHealthOverlayProbe.verify(name, active, debug, scoreboard, screen));
        System.out.println("Low health overlay screenshot: " + screenshot);
        if (pixels) {
            int alpha = context.computeOnClient(client -> LowHealthWarning.getInstance().color.getColor() >>> 24);
            try (var input = Files.newInputStream(screenshot); NativeImage image = NativeImage.read(input)) {
                check(image.getWidth() >= 854 && image.getHeight() >= 480, name + ": full-size framebuffer");
                for (double x : new double[]{0.02, 0.98}) {
                    for (double y : new double[]{0.02, 0.98}) {
                        int pixel = image.getColorArgb((int) (x * (image.getWidth() - 1)), (int) (y * (image.getHeight() - 1)));
                        int red = channel(pixel, 16), green = channel(pixel, 8), blue = channel(pixel, 0);
                        check(red >= 250 && Math.abs(green - blue) <= 3, name + ": pure red composites normally over neutral white UI");
                        if (active) {
                            // Accept the ENTIRE pulse range; never compare pulse phases/screenshots.
                            check(green >= 255 - alpha - 6 && green <= 255 - Math.round(alpha * 0.4f) + 6,
                                    name + ": visible edge attenuation respects color alpha at every pulse phase: " + green);
                        } else {
                            check(green >= 250 && blue >= 250, name + ": inactive state leaves corners white");
                        }
                    }
                }
                int center = image.getColorArgb(image.getWidth() / 2, image.getHeight() / 2);
                check(channel(center, 16) >= 250 && channel(center, 8) >= 250 && channel(center, 0) >= 250,
                        name + ": transparent center preserves white UI");
            }
        }
    }

    private static int channel(int pixel, int shift) { return (pixel >>> shift) & 255; }

    private static void requireIsolatedRun(MinecraftClient client) {
        try {
            Path run = client.runDirectory.toPath().toRealPath();
            check(System.getProperty("fabric.client.gametest") != null
                            && run.endsWith(Path.of("build", "pack-preview-client-test")),
                    "overlay tests require the build/pack-preview-client-test gametest runner");
            check(ConfigManager.getInstance().getConfigsDir().toPath().toRealPath().startsWith(run),
                    "all Phaze config writes are confined to the test runner");
        } catch (IOException e) {
            throw new AssertionError("Could not verify isolated test directories", e);
        }
    }

    private static void deleteFixtureWorld(ClientGameTestContext context, Path save) {
        try {
            Path run = context.computeOnClient(client -> client.runDirectory.toPath().toRealPath());
            Path real = save.toRealPath();
            check(real.startsWith(run.resolve("saves")) && !real.equals(run.resolve("saves")), "delete only this fixture save");
            try (var paths = Files.walk(real)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
            check(!Files.exists(real), "fixture save deleted after shutdown");
        } catch (IOException e) {
            throw new RuntimeException("Could not remove isolated overlay test world", e);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** The static listener fields have setters, but Lombok does not generate static getters. */
    @SuppressWarnings("unchecked")
    private static <T> Consumer<T> savedListener(Class<?> owner, String name) {
        try {
            var field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return (Consumer<T>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not preserve " + owner.getSimpleName() + "." + name, e);
        }
    }

    private record SavedState(boolean enabled, boolean heartbeat, int color, float threshold, float pulseSpeed,
                              int width, int height, int guiScale, boolean hudHidden, boolean pauseOnLostFocus,
                              boolean debug, Screen screen, Consumer<Setting> settingListener, Consumer<Module> moduleListener) {
        static SavedState capture(MinecraftClient client) {
            var warning = LowHealthWarning.getInstance();
            return new SavedState(warning.state, warning.heartbeatSound.isValue(), warning.color.getColor(),
                    warning.threshold.getValue(), warning.pulseSpeed.getValue(), client.getWindow().getWidth(),
                    client.getWindow().getHeight(), client.options.getGuiScale().getValue(), client.options.hudHidden,
                    client.options.pauseOnLostFocus, client.inGameHud.getDebugHud().shouldShowDebugHud(), client.currentScreen,
                    savedListener(Setting.class, "globalChangeListener"), savedListener(Module.class, "globalStateChangeListener"));
        }

        void restore(MinecraftClient client) {
            try {
                var warning = LowHealthWarning.getInstance();
                warning.stopHeartbeat();
                warning.heartbeatSound.setValue(heartbeat);
                warning.color.setColor(color);
                warning.threshold.setValue(threshold);
                warning.pulseSpeed.setValue(pulseSpeed);
                warning.setStateSilent(enabled);
                client.options.getGuiScale().setValue(guiScale);
                client.options.hudHidden = hudHidden;
                client.options.pauseOnLostFocus = pauseOnLostFocus;
                if (client.inGameHud.getDebugHud().shouldShowDebugHud() != debug) client.inGameHud.getDebugHud().toggleDebugHud();
                client.setScreen(screen);
            } finally {
                Setting.setGlobalChangeListener(settingListener);
                Module.setGlobalStateChangeListener(moduleListener);
            }
        }
    }

    private static final class WhiteReferenceScreen extends Screen {
        WhiteReferenceScreen() { super(Text.literal("Low HP overlay white UI compositing reference")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // A deliberately higher-depth GUI surface catches use of the depth-tested GUI layer.
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 400);
            context.fill(0, 0, width, height, 0xFFFFFFFF);
            context.getMatrices().pop();
        }

        @Override
        public boolean shouldPause() { return false; }

        @Override
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
    }
}
