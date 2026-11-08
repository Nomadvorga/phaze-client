package vorga.phazeclient.implement.features.modules.hud;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.Difficulty;
import org.lwjgl.opengl.GL11C;
import vorga.phazeclient.implement.features.modules.other.HudOptimizer;
import vorga.phazeclient.implement.hud.PaperDollRenderer;

import java.util.Arrays;
import java.util.Locale;

/** Screenshots of the doll in a few poses, state-restore checks and its frame cost. */
public final class PaperDollClientTest implements FabricClientGameTest {
    private static final int ROUNDS = 10;
    private static final int FRAMES = 60;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        context.runOnClient(client -> {
            client.options.pauseOnLostFocus = false;
            client.options.hudHidden = false;
            client.options.getViewDistance().setValue(4);
            client.options.getGuiScale().setValue(2);
            client.getToastManager().clear();
        });
        context.getInput().resizeWindow(1600, 900);
        TestSingleplayerContext world = context.worldBuilder().adjustSettings(creator -> {
            creator.setWorldName("Phaze PaperDoll");
            creator.setGameMode(WorldCreator.Mode.CREATIVE);
            creator.setDifficulty(Difficulty.PEACEFUL);
        }).create();
        PaperDoll doll = PaperDoll.getInstance();
        try {
            world.getClientWorld().waitForChunksRender();
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                player.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                player.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
            });
            context.waitFor(client -> client.player != null
                    && client.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.DIAMOND_CHESTPLATE));
            context.runOnClient(client -> {
                doll.setStateSilent(true);
                doll.setHudScale(1.5F);
                doll.setHudX(0);
                doll.setHudY(0);
                client.player.setYaw(35);
                client.player.setHeadYaw(35);
                client.player.setPitch(25);
            });
            context.waitTicks(20);

            for (String mode : new String[]{PaperDoll.HEAD_FLOATY, PaperDoll.HEAD_LOCKED, PaperDoll.HEAD_STATIC_HORIZONTAL}) {
                context.runOnClient(client -> doll.headMode.setSelected(mode));
                context.waitTicks(5);
                System.out.println("PaperDoll screenshot: " + context.takeScreenshot("paper-doll-" + mode.toLowerCase(Locale.ROOT).replace(' ', '-')));
            }
            context.runOnClient(client -> doll.headMode.setSelected(PaperDoll.HEAD_FLOATY));

            context.getInput().holdKey(options -> options.sneakKey);
            context.waitTicks(10);
            System.out.println("PaperDoll screenshot: " + context.takeScreenshot("paper-doll-sneaking"));
            context.getInput().releaseKey(options -> options.sneakKey);
            context.waitTicks(10);

            context.runOnClient(PaperDollClientTest::checkStateRestored);

            context.runOnClient(client -> client.options.setPerspective(Perspective.THIRD_PERSON_BACK));
            context.runOnClient(client -> check(PaperDollRenderer.isHidden(client, doll, client.player), "hidden in F5"));
            context.runOnClient(client -> client.options.setPerspective(Perspective.FIRST_PERSON));
            context.runOnClient(client -> check(!PaperDollRenderer.isHidden(client, doll, client.player), "shown in first person"));

            context.runOnClient(client -> doll.autoHide.setValue(true));
            context.waitTicks(15);
            context.runOnClient(client -> check(PaperDollRenderer.isHidden(client, doll, client.player), "auto hide while idle"));
            context.runOnClient(client -> client.setScreen(new ChatScreen("")));
            context.waitTicks(2);
            System.out.println("PaperDoll screenshot: " + context.takeScreenshot("paper-doll-chat-editor"));
            context.runOnClient(client -> {
                client.setScreen(null);
                doll.autoHide.setValue(false);
            });

            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                var source = player.getCommandSource().withLevel(4);
                server.getCommandManager().executeWithPrefix(source, "summon minecraft:oak_boat ~ ~ ~");
                server.getCommandManager().executeWithPrefix(source, "ride @s mount @e[type=minecraft:oak_boat,limit=1,sort=nearest]");
                server.getCommandManager().executeWithPrefix(source, "effect give @s minecraft:glowing infinite 0 true");
            });
            context.waitFor(client -> client.player.hasVehicle());
            context.waitTicks(20);
            System.out.println("PaperDoll screenshot: " + context.takeScreenshot("paper-doll-boat-glowing"));
            context.runOnClient(PaperDollClientTest::checkStateRestored);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                player.stopRiding();
                player.clearStatusEffects();
            });
            context.waitFor(client -> !client.player.hasVehicle());
            context.waitTicks(10);

            for (boolean optimizer : new boolean[]{true, false}) {
                context.runOnClient(client -> HudOptimizer.getInstance().setStateSilent(optimizer));
                context.waitTicks(5);
                context.runOnClient(client -> bench(client, doll, optimizer));
            }
        } finally {
            context.runOnClient(client -> {
                doll.setStateSilent(false);
                client.setScreen(null);
            });
            world.close();
        }
        System.out.println("PaperDoll client test finished");
    }

    private static void checkStateRestored(MinecraftClient client) {
        var player = client.player;
        float yaw = player.getYaw(), pitch = player.getPitch(), body = player.bodyYaw, head = player.headYaw;
        var velocity = player.getVelocity();
        for (int i = 0; i < 3; i++) client.gameRenderer.render(RenderTickCounter.ZERO, true);
        check(player.getYaw() == yaw && player.getPitch() == pitch, "yaw/pitch restored");
        check(player.bodyYaw == body && player.headYaw == head, "body/head yaw restored");
        check(player.getVelocity() == velocity, "velocity restored");
        check(!PaperDollRenderer.isDrawing(), "outline suppression released");
        if (player.getVehicle() != null) {
            var vehicle = player.getVehicle();
            float vehicleYaw = vehicle.getYaw();
            client.gameRenderer.render(RenderTickCounter.ZERO, true);
            check(vehicle.getYaw() == vehicleYaw, "vehicle yaw restored");
        }
    }

    private static void bench(MinecraftClient client, PaperDoll doll, boolean optimizer) {
        double[] without = new double[ROUNDS], with = new double[ROUNDS];
        for (int round = 0; round < ROUNDS; round++) {
            for (int pass = 0; pass < 2; pass++) {
                boolean enabled = (round + pass) % 2 == 0;
                doll.setStateSilent(enabled);
                for (int i = 0; i < 20; i++) client.gameRenderer.render(RenderTickCounter.ZERO, true);
                GL11C.glFinish();
                long start = System.nanoTime();
                for (int i = 0; i < FRAMES; i++) client.gameRenderer.render(RenderTickCounter.ZERO, true);
                GL11C.glFinish();
                double ms = (System.nanoTime() - start) / (double) FRAMES / 1_000_000.0;
                (enabled ? with : without)[round] = ms;
            }
        }
        doll.setStateSilent(true);
        double base = median(without), doll_ = median(with);
        System.out.printf(Locale.ROOT, "PaperDoll bench (HUD optimizer %s): without %.3f ms (%.0f fps), with %.3f ms (%.0f fps), cost %.3f ms%n",
                optimizer ? "on" : "off", base, 1000 / base, doll_, 1000 / doll_, doll_ - base);
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError("PaperDoll: " + message);
    }
}
