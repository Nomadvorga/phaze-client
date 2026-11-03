package vorga.phazeclient.implement.features.modules.hud;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.world.Difficulty;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.features.modules.other.HudOptimizer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Visual 3x references use the actual HUD renderer and a server-backed player. */
public final class HudScaleClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        SavedState saved = context.computeOnClient(client -> {
            check(System.getProperty("fabric.client.gametest") != null && client.world == null
                            && client.runDirectory.toPath().toAbsolutePath().normalize().getParent().getFileName()
                            .equals(Path.of("build")), "HUD screenshots require an isolated build/ runner");
            return SavedState.capture(client);
        });
        TestSingleplayerContext world = null;
        try {
            context.runOnClient(client -> {
                Setting.setGlobalChangeListener(null);
                Module.setGlobalStateChangeListener(null);
                for (SavedHud savedHud : saved.huds()) {
                    savedHud.module().setStateSilent(false);
                    if (savedHud.module() instanceof RectHudModule hud) {
                        hud.setHudScale(100);
                        check(hud.getHudScale() == 3 && hud.getMaxHudScale() == 3,
                                hud.getName() + ": the actual HUD instance clamps its scale to 3x");
                    } else {
                        var armor = (ArmorHud) savedHud.module();
                        armor.setHudScale(100);
                        check(armor.getHudScale() == 3 && armor.getMaxHudScale() == 3, "Armor: shared 3x limit");
                    }
                }
                HudOptimizer.getInstance().setStateSilent(false);
                client.options.hudHidden = false;
                client.options.pauseOnLostFocus = false;
                client.options.getGuiScale().setValue(2);
                client.options.getViewDistance().setValue(2);
            });
            context.getInput().resizeWindow(1280, 720);
            world = context.worldBuilder().adjustSettings(creator -> {
                creator.setWorldName("Phaze HUD 3x visual reference");
                creator.setGameMode(WorldCreator.Mode.CREATIVE);
                creator.setDifficulty(Difficulty.PEACEFUL);
            }).create();
            world.getClientWorld().waitForChunksRender();
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                player.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                player.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
                player.equipStack(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                Item[] items = {Items.DIAMOND, Items.GOLD_INGOT, Items.IRON_INGOT, Items.GRASS_BLOCK,
                        Items.OAK_LOG, Items.STONE, Items.APPLE, Items.EMERALD, Items.REDSTONE};
                for (int slot = 9; slot < 36; slot++) {
                    player.getInventory().setStack(slot, new ItemStack(items[(slot - 9) % items.length], 64));
                }
                player.currentScreenHandler.sendContentUpdates();
            });
            context.waitFor(client -> client.player != null && client.getOverlay() == null
                    && client.player.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.DIAMOND_HELMET)
                    && client.player.getInventory().getStack(9).isOf(Items.DIAMOND));
            context.runOnClient(client -> {
                client.setScreen(null);
                var armor = ArmorHud.getInstance();
                armor.setHudScale(8);
                check(armor.getHudScale() == 3 && armor.getRenderHudScale() == 6,
                        "Armor clamps old large scales to 3x, preserving the 108px reference row spacing");
                armor.setHudX(client.getWindow().getWidth() - 300);
                armor.setHudY(24);
                armor.setStateSilent(true);
                show(FpsHud.getInstance(), 24, 24);
                show(PackDisplayHud.getInstance(), 24, 160);
            });
            screenshot(context, "hud-3x-armor-text-pack");
            context.runOnClient(client -> {
                ArmorHud.getInstance().setStateSilent(false);
                FpsHud.getInstance().setStateSilent(false);
                PackDisplayHud.getInstance().setStateSilent(false);
                show(InventoryHud.getInstance(), 24, 24);
            });
            screenshot(context, "hud-3x-inventory");
            world.getServer().runCommand("scoreboard objectives add hud_scale dummy \"HUD 3x\"");
            world.getServer().runCommand("scoreboard players set First hud_scale 363");
            world.getServer().runCommand("scoreboard players set Second hud_scale 528");
            world.getServer().runCommand("scoreboard objectives setdisplay sidebar hud_scale");
            context.waitFor(client -> {
                var objective = client.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
                return objective != null && objective.getName().equals("hud_scale")
                        && client.world.getScoreboard().getScoreboardEntries(objective).size() == 2;
            });
            context.runOnClient(client -> {
                InventoryHud.getInstance().setStateSilent(false);
                show(ScoreboardHud.getInstance(), 24, 24);
            });
            screenshot(context, "hud-3x-scoreboard");
        } finally {
            try {
                context.runOnClient(client -> client.setScreen(null));
                if (world != null) world.close();
            } finally {
                context.getInput().resizeWindow(saved.width(), saved.height());
                context.runOnClient(client -> saved.restore(client));
            }
        }
        System.out.println("HUD 3x real-client visual integration PASSED");
    }

    private static void show(RectHudModule hud, float x, float y) {
        hud.setHudScale(8);
        check(hud.getHudScale() == 3, hud.getName() + ": renderer/config input clamps to 3x");
        hud.setHudX(x);
        hud.setHudY(y);
        hud.setStateSilent(true);
    }

    private static void screenshot(ClientGameTestContext context, String name) {
        context.runOnClient(client -> {
            for (int i = 0; i < 5; i++) client.gameRenderer.render(RenderTickCounter.ZERO, true);
        });
        System.out.println("HUD 3x screenshot: " + context.takeScreenshot(name));
    }

    private record SavedHud(Module module, boolean enabled, float x, float y, float scale, int width, int height) {
        static SavedHud capture(Module module) {
            if (module instanceof RectHudModule hud) {
                return new SavedHud(module, module.state, hud.getHudX(), hud.getHudY(), hud.getHudScale(),
                        hud.getHudRefWidth(), hud.getHudRefHeight());
            }
            var armor = (ArmorHud) module;
            return new SavedHud(module, module.state, armor.getHudX(), armor.getHudY(), armor.getHudScale(),
                    armor.getHudRefWidth(), armor.getHudRefHeight());
        }

        void restore() {
            if (module instanceof RectHudModule hud) {
                hud.setHudPosition(x, y, width, height);
                hud.setHudScale(scale);
            } else {
                var armor = (ArmorHud) module;
                armor.setHudPosition(x, y, width, height);
                armor.setHudScale(scale);
            }
            module.setStateSilent(enabled);
        }
    }

    private record SavedState(Screen screen, int width, int height, int guiScale, int viewDistance, boolean hidden,
                              boolean pauseOnLostFocus, boolean optimizer, List<SavedHud> huds,
                              Consumer<Setting> settings, Consumer<Module> modules) {
        static SavedState capture(MinecraftClient client) {
            List<SavedHud> huds = new ArrayList<>();
            for (Module module : Main.getInstance().getModuleProvider().getModules()) {
                if (module instanceof RectHudModule || module instanceof ArmorHud) huds.add(SavedHud.capture(module));
            }
            return new SavedState(client.currentScreen, client.getWindow().getWidth(), client.getWindow().getHeight(),
                    client.options.getGuiScale().getValue(), client.options.getViewDistance().getValue(),
                    client.options.hudHidden, client.options.pauseOnLostFocus, HudOptimizer.getInstance().state, huds,
                    listener(Setting.class, "globalChangeListener"), listener(Module.class, "globalStateChangeListener"));
        }

        void restore(MinecraftClient client) {
            try {
                huds.forEach(SavedHud::restore);
                HudOptimizer.getInstance().setStateSilent(optimizer);
                client.options.getGuiScale().setValue(guiScale);
                client.options.getViewDistance().setValue(viewDistance);
                client.options.hudHidden = hidden;
                client.options.pauseOnLostFocus = pauseOnLostFocus;
                client.setScreen(screen);
            } finally {
                Setting.setGlobalChangeListener(settings);
                Module.setGlobalStateChangeListener(modules);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Consumer<T> listener(Class<?> owner, String name) {
        try {
            var field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return (Consumer<T>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not preserve " + owner.getSimpleName() + "." + name, e);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
