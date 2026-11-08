package vorga.phazeclient.implement.features.modules.hud;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import vorga.phazeclient.implement.hud.CustomHudRenderer;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.world.Difficulty;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.api.system.hud.BatchedHudBuffer;
import vorga.phazeclient.api.system.hud.HudAnimationRefreshClock;
import vorga.phazeclient.api.system.hud.HudBuffer;
import vorga.phazeclient.api.system.hud.HudCachedTransform;
import vorga.phazeclient.api.system.hud.HudRefreshPolicy;
import vorga.phazeclient.api.system.hud.Render2DState;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.config.ConfigManager;
import vorga.phazeclient.implement.features.modules.client.Theme;
import vorga.phazeclient.implement.features.modules.other.Animations;
import vorga.phazeclient.implement.features.modules.other.HudOptimizer;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.components.implement.settings.ScaleSnapOverlay;
import vorga.phazeclient.implement.menu.components.implement.window.WindowManager;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.CRC32;

/** Native GLFW input and actual world-backed HUD rendering. All files and options stay in build/. */
public final class HudEditorClientTest implements FabricClientGameTest {
    private static int checks;

    @Override
    public void runTest(ClientGameTestContext context) {
        checks = 0;
        context.waitFor(client -> client.getOverlay() == null);
        SavedState saved = context.computeOnClient(client -> {
            check(System.getProperty("fabric.client.gametest") != null && client.world == null
                            && client.runDirectory.toPath().toAbsolutePath().normalize().getParent().getFileName().equals(Path.of("build")),
                    "HUD editor checks require an isolated build/ runner");
            return SavedState.capture(client);
        });
        TestSingleplayerContext world = null;
        try (var mouse = new NativeMouseInput()) {
            context.runOnClient(client -> {
                Setting.setGlobalChangeListener(null);
                Module.setGlobalStateChangeListener(null);
                disableModules();
                client.options.hudHidden = false;
                client.options.pauseOnLostFocus = false;
                client.options.getViewDistance().setValue(2);
                client.options.getGuiScale().setValue(2);
                client.getToastManager().clear();
                if (client.inGameHud.getDebugHud().shouldShowDebugHud()) client.inGameHud.getDebugHud().toggleDebugHud();
                MenuScreen.INSTANCE.closeModuleDetail();
                WindowManager.INSTANCE.clear();
            });
            context.getInput().resizeWindow(1600, 900);
            world = context.worldBuilder().adjustSettings(creator -> {
                creator.setWorldName("Phaze cached HUD editor integration");
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
                Item[] items = {Items.GOLDEN_APPLE, Items.ENDER_PEARL, Items.TOTEM_OF_UNDYING,
                        Items.DIAMOND, Items.EMERALD, Items.OAK_LOG, Items.IRON_INGOT, Items.APPLE, Items.STONE};
                for (int slot = 9; slot < 36; slot++) player.getInventory().setStack(slot, new ItemStack(items[(slot - 9) % items.length], 16));
                player.currentScreenHandler.sendContentUpdates();
            });
            context.waitFor(client -> client.player != null && client.getOverlay() == null
                    && client.player.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.DIAMOND_HELMET)
                    && client.player.getInventory().getStack(9).isOf(Items.GOLDEN_APPLE));
            context.runOnClient(client -> {
                HudEditorProbe.start();
                client.inGameHud.getChatHud().addMessage(Text.literal("LIVE CHAT — never captured in the Phaze HUD texture"));
            });
            List<Target> targets = context.computeOnClient(client -> List.of(new Target(FpsHud.getInstance(), 0),
                    new Target(ArmorHud.getInstance(), 29), new Target(Consumable.getInstance(), 22),
                    new Target(InventoryHud.getInstance(), 26), new Target(PaperDoll.getInstance(), 24)));
            for (int guiScale : new int[]{1, 2, 3}) for (Target target : targets) dragAndResize(context, mouse, target, guiScale);
            idleBudgets(context, targets.getFirst());
            backgroundBudgets(context, mouse, targets.getFirst());
            for (Target target : targets) {
                optimizerToggle(context, mouse, target);
                for (String interruption : List.of("screen", "F1", "disable", "guiScale", "framebuffer")) {
                    interrupt(context, mouse, target, interruption);
                }
            }
            context.runOnClient(client -> {
                check(HudEditorProbe.cachedItems > 0 && HudEditorProbe.cachedModels > 0, "real item and entity renderers participated in cache captures");
                check(HudEditorProbe.chatRenders > 0 && HudEditorProbe.capturedChat == 0, "vanilla chat remains live outside both caches");
                check(HudBuffer.activeCaptureTarget == -1 && HudBuffer.getActiveCaptureFramebuffer() == null, "editor leaves no framebuffer redirect behind");
                check(GL11C.glGetError() == GL11C.GL_NO_ERROR, "native editor rendering introduces no GL errors");
            });
        } finally {
            try {
                context.runOnClient(client -> {
                    HudEditorProbe.recording = false;
                    disableModules();
                    client.setScreen(null);
                    MenuScreen.INSTANCE.closeModuleDetail();
                    WindowManager.INSTANCE.clear();
                    ScaleSnapOverlay.hide();
                });
                if (world != null) world.close();
            } finally {
                context.getInput().resizeWindow(saved.width(), saved.height());
                context.runOnClient(client -> saved.restore(client));
            }
        }
        System.out.println("HUD cached editor native integration PASSED: " + checks
                + " checks; five input paths at GUI scale 1/2/3, independent background clocks and interruptions (no game FPS benchmark)");
    }

    private static void dragAndResize(ClientGameTestContext context, NativeMouseInput mouse, Target target, int guiScale) {
        configure(context, target, guiScale);
        double[] pointer = point(context, target, false);
        context.getInput().setCursorPos(pointer[0], pointer[1]);
        render(context, 2);
        mouse.hold(context);
        render(context, 2);
        Frozen drag = context.computeOnClient(client -> {
            check(target.interaction(false) && selected() == target.index(), target.label() + ": native press starts cached drag");
            check((int) field(CursorManager.class, "lastAppliedShape", null) == CursorManager.SHAPE_MOVE, "cached drag preserves the native move cursor");
            check(!hasAlpha(HudEditorProbe.batchFramebuffer, bounds(target)), "selected widget is removed from the shared texture");
            check(!hasAlpha(HudEditorProbe.batchFramebuffer, new float[]{0, 720, 420, 180}), "vanilla chat/input rows never enter the shared texture");
            check(hasAlpha(HudEditorProbe.childFramebuffer, bounds(target)), "selected texture contains the actual widget");
            return Frozen.capture();
        });
        float[] origin = context.computeOnClient(client -> bounds(target));
        for (int step = 1; step <= 3; step++) {
            double dx = 37.25 * step, dy = 29.5 * step;
            context.getInput().setCursorPos(pointer[0] + dx, pointer[1] + dy);
            context.runOnClient(client -> {
                render(client, 3);
                close(bounds(target)[0], origin[0] + (float) dx, target.label() + ": drag keeps its pixel X offset");
                close(bounds(target)[1], origin[1] + (float) dy, target.label() + ": drag keeps its pixel Y offset");
                drag.assertUnchanged();
            });
        }
        release(context, mouse, target, false);

        pointer = point(context, target, true);
        context.getInput().setCursorPos(pointer[0], pointer[1]);
        render(context, 2);
        mouse.hold(context);
        render(context, 2);
        Frozen resize = context.computeOnClient(client -> {
            check(target.interaction(true) && selected() == target.index(), target.label() + ": native corner starts cached resize");
            check((int) field(CursorManager.class, "lastAppliedShape", null) == CursorManager.SHAPE_HRESIZE, "cached resize preserves the resize cursor");
            return Frozen.capture();
        });
        float reference = context.computeOnClient(client -> target.model() ? PaperDoll.BASE_WIDTH
                : target.index() == 29 || target.index() == 22
                ? 60 : ((float[]) hudField("HUD_LAYOUT_BASE_WIDTH"))[target.index()]);
        for (int step = 1; step <= 3; step++) {
            double delta = reference / 3.0 * step * (target.model() ? guiScale : 1);
            float expected = 1 + 0.15F * step;
            context.getInput().setCursorPos(pointer[0] + delta, pointer[1] + delta);
            context.runOnClient(client -> {
                render(client, 3);
                close(target.scale(), expected, target.label() + ": resize preserves its legacy sensitivity");
                float pixelScale = target.scale() * 2 * (target.model() ? guiScale : 1);
                close(bounds(target)[2], ((float[]) hudField("HUD_LAYOUT_BASE_WIDTH"))[target.index()] * pixelScale, "live resize updates the snap width");
                close(bounds(target)[3], ((float[]) hudField("HUD_LAYOUT_BASE_HEIGHT"))[target.index()] * pixelScale, "live resize updates the snap height");
                check(field(ScaleSnapOverlay.class, "valueText", null) != null, "scale readout stays live outside the textures");
                resize.assertUnchanged();
            });
        }
        release(context, mouse, target, true);
        System.out.println("HUD native cached drag/resize PASSED: " + target.label() + ", GUI scale " + guiScale);
    }

    private static void idleBudgets(ClientGameTestContext context, Target target) {
        configure(context, target, 2);
        for (int fps : new int[]{10, 30, 120}) for (boolean menu : new boolean[]{false, true}) {
            context.runOnClient(client -> {
                HudOptimizer.getInstance().refreshRate.setValue(fps);
                if (menu) MenuScreen.INSTANCE.openGui(); else client.setScreen(new ChatScreen(""));
                render(client, 3);
                park(BatchedHudBuffer.INSTANCE);
                HudEditorProbe.Counts before = HudEditorProbe.counts();
                render(client, 60);
                check(before.equals(HudEditorProbe.counts()), "idle chat/MenuScreen does not force captures on display frames");
                check(BatchedHudBuffer.INSTANCE.getTargetFps() == fps, "chat/MenuScreen keep the configured static-HUD rate");
                clock(BatchedHudBuffer.INSTANCE).reset();
                int initial = HudEditorProbe.batchCaptures;
                long started = System.nanoTime();
                render(client, 90);
                long elapsed = System.nanoTime() - started;
                int captures = HudEditorProbe.batchCaptures - initial;
                check(captures >= 1 && captures <= Math.ceil(elapsed * fps / 1_000_000_000.0) + 2,
                        "real captures obey their wall-clock budget instead of screen/display FPS");
            });
        }
    }

    private static void backgroundBudgets(ClientGameTestContext context, NativeMouseInput mouse, Target target) {
        configure(context, target, 2);
        context.runOnClient(client -> {
            CpsHud.getInstance().backgroundPreset.setSelected("Gradient");
            CpsHud.getInstance().backgroundBlurRadius.setValue(4);
            HudOptimizer.getInstance().blurBackgroundFps.setValue(180);
            render(client, 3);
        });
        double[] pointer = point(context, target, false);
        context.getInput().setCursorPos(pointer[0], pointer[1]);
        render(context, 2);
        mouse.hold(context);
        render(context, 2);
        context.runOnClient(client -> {
            Frozen selected = Frozen.capture();
            check(clock(editedBuffer()).due(System.nanoTime()) == false && (int) field(HudAnimationRefreshClock.class, "fps", clock(editedBuffer())) == 30,
                    "a static selection keeps 30 Hz beside a faster animated/blurred neighbour");
            for (int frame = 0; frame < 5; frame++) {
                clock(BatchedHudBuffer.INSTANCE).reset();
                int parent = HudEditorProbe.batchCaptures;
                render(client, 1);
                check(HudEditorProbe.batchCaptures == parent + 1 && HudEditorProbe.childCaptures == selected.counts().child(),
                        "a due parent does not force an early child capture");
                check(selected.transform() == transform() && selected.crc() == crc(selected.child()), "parent refresh preserves the child's published pixels/transform");
            }
            park(BatchedHudBuffer.INSTANCE);
            clock(editedBuffer()).reset();
            HudEditorProbe.Counts before = HudEditorProbe.counts();
            render(client, 1);
            check(before.equals(HudEditorProbe.counts()), "a child deadline never forces an early parent capture");
            clock(BatchedHudBuffer.INSTANCE).reset();
            render(client, 1);
            check(HudEditorProbe.childCaptures == before.child() + 1, "the due child updates when the shared batch refreshes");
            check(selected.child() == HudEditorProbe.childFramebuffer, "child captures reuse a single allocation");

            CpsHud.getInstance().backgroundPreset.setSelected("Vanilla");
            CpsHud.getInstance().backgroundBlurRadius.setValue(0);
            for (int blurFps : new int[]{30, 180, 360}) for (boolean gradient : new boolean[]{false, true}) for (boolean blur : new boolean[]{false, true}) {
                HudOptimizer.getInstance().blurBackgroundFps.setValue(blurFps);
                FpsHud.getInstance().backgroundPreset.setSelected(gradient ? "Gradient" : "Custom Color");
                FpsHud.getInstance().backgroundBlurRadius.setValue(blur ? 4 : 0);
                clock(BatchedHudBuffer.INSTANCE).reset();
                clock(editedBuffer()).reset();
                render(client, 1);
                int expected = HudRefreshPolicy.edited(30, gradient, blur, blurFps);
                check((int) field(HudAnimationRefreshClock.class, "fps", clock(editedBuffer())) == expected,
                        "selected background respects configured blur/gradient cadence");
                check(BatchedHudBuffer.INSTANCE.getTargetFps() == HudRefreshPolicy.batch(30, gradient || blur, blur, blurFps),
                        "shared cache preserves the existing background budget");
                Frozen frozen = Frozen.capture();
                render(client, 12);
                frozen.assertUnchanged();
            }

            // Blur toggles must invalidate even when Gradient keeps the combined live-background flag true.
            HudOptimizer.getInstance().blurBackgroundFps.setValue(30);
            FpsHud.getInstance().backgroundPreset.setSelected("Gradient");
            FpsHud.getInstance().backgroundBlurRadius.setValue(0);
            clock(BatchedHudBuffer.INSTANCE).reset();
            clock(editedBuffer()).reset();
            render(client, 1);
            Frozen frozen = Frozen.capture();
            FpsHud.getInstance().backgroundBlurRadius.setValue(4);
            render(client, 1);
            check(HudEditorProbe.childCaptures == frozen.counts().child() + 1 && transform() != frozen.transform(),
                    "blur flip immediately repairs a selected animated gradient despite unchanged 120 Hz policy");
            check(HudEditorProbe.uncachedModels == 0, "PaperDoll has no separate unthrottled render path");
        });
        release(context, mouse, target, false);
    }

    private static void optimizerToggle(ClientGameTestContext context, NativeMouseInput mouse, Target target) {
        configure(context, target, 2);
        double[] pointer = point(context, target, false);
        context.getInput().setCursorPos(pointer[0], pointer[1]);
        render(context, 2);
        mouse.hold(context);
        render(context, 2);
        context.runOnClient(client -> {
            HudOptimizer.getInstance().setStateSilent(false);
            render(client, 2);
            check(target.interaction(false) && selected() == -1, "disabling the optimizer preserves the current native drag");
        });
        context.getInput().setCursorPos(pointer[0] + 47.5, pointer[1] + 31.25);
        context.runOnClient(client -> {
            render(client, 2);
            close(bounds(target)[0], 111.5F, "direct fallback retains the drag offset");
            HudOptimizer.getInstance().setStateSilent(true);
            render(client, 2);
            check(target.interaction(false) && selected() == target.index(), "re-enabling the optimizer resumes isolated dragging without another press");
            Frozen frozen = Frozen.capture();
            render(client, 15);
            frozen.assertUnchanged();
        });
        release(context, mouse, target, false);
    }

    private static void interrupt(ClientGameTestContext context, NativeMouseInput mouse, Target target, String kind) {
        configure(context, target, 2);
        double[] pointer = point(context, target, false);
        context.getInput().setCursorPos(pointer[0], pointer[1]);
        render(context, 2);
        mouse.hold(context);
        render(context, 2);
        context.runOnClient(client -> {
            check(target.interaction(false), target.label() + ": interaction is live before " + kind);
            switch (kind) {
                case "screen" -> MenuScreen.INSTANCE.openGui();
                case "F1" -> client.options.hudHidden = true;
                case "disable" -> target.module().setStateSilent(false);
                case "guiScale" -> { client.options.getGuiScale().setValue(3); client.onResolutionChanged(); }
                case "framebuffer" -> { }
                default -> throw new AssertionError(kind);
            }
        });
        if (kind.equals("framebuffer")) context.getInput().resizeWindow(1440, 810);
        context.runOnClient(client -> {
            render(client, 2);
            check(!target.interaction(false) && !target.interaction(true) && selected() == -1,
                    target.label() + ": " + kind + " clears both interaction guards and isolated membership");
            client.options.hudHidden = false;
            target.module().setStateSilent(true);
            client.setScreen(new ChatScreen(""));
            BatchedHudBuffer.INSTANCE.invalidate();
            render(client, 2);
            check(!target.interaction(false) && !target.interaction(true), "restoring the HUD while held does not synthesize another press");
        });
        float[] restored = context.computeOnClient(client -> bounds(target));
        context.getInput().setCursorPos(pointer[0] + 65, pointer[1] + 45);
        context.runOnClient(client -> {
            render(client, 2);
            check(!target.interaction(false) && !target.interaction(true), "held motion after interruption cannot restart editing");
            close(bounds(target)[0], restored[0], "interrupted drag never jumps on subsequent mouse motion");
            close(bounds(target)[1], restored[1], "interrupted vertical offset is not reused");
        });
        mouse.release(context);
        render(context, 2);
        if (kind.equals("framebuffer")) context.getInput().resizeWindow(1600, 900);
    }

    private static void configure(ClientGameTestContext context, Target target, int guiScale) {
        context.runOnClient(client -> {
            disableModules();
            client.options.hudHidden = false;
            client.options.getGuiScale().setValue(guiScale);
            client.onResolutionChanged();
            check(client.getWindow().getScaleFactor() == guiScale, "requested native GUI scale is active");
            HudOptimizer optimizer = HudOptimizer.getInstance();
            optimizer.setStateSilent(true);
            optimizer.refreshRate.setValue(30);
            optimizer.blurBackgroundFps.setValue(120);
            Animations.getInstance().setStateSilent(true);
            Animations.getInstance().dynamicCursor.setValue(true);
            CpsHud neighbour = CpsHud.getInstance();
            neighbour.setStateSilent(true);
            solid(neighbour);
            neighbour.setHudScale(1);
            neighbour.setHudPosition(1200, 36, 1600, 900);
            if (target.module() instanceof RectHudModule rect) {
                solid(rect);
                rect.setHudScale(1);
                float coordinateScale = target.model() ? guiScale : 1;
                rect.setHudPosition(64 / coordinateScale, 64 / coordinateScale,
                        target.model() ? client.getWindow().getScaledWidth() : client.getWindow().getWidth(),
                        target.model() ? client.getWindow().getScaledHeight() : client.getWindow().getHeight());
                if (target.model()) PaperDoll.getInstance().headMode.setSelected(PaperDoll.HEAD_LOCKED);
            } else {
                ArmorHud armor = (ArmorHud) target.module();
                armor.background.setValue(true);
                armor.backgroundPreset.setSelected("Custom Color");
                armor.backgroundBlurRadius.setValue(0);
                armor.cornerRounding.setValue(4);
                armor.setHudScale(1);
                armor.setHudPosition(64, 64, client.getWindow().getWidth(), client.getWindow().getHeight());
            }
            target.module().setStateSilent(true);
            client.setScreen(new ChatScreen(""));
            BatchedHudBuffer.INSTANCE.invalidate();
            render(client, 3);
            check(((boolean[]) hudField("HUD_SNAP_VALID"))[target.index()], target.label() + ": warm capture publishes native bounds");
        });
    }

    private static void solid(RectHudModule hud) {
        hud.background.setValue(true);
        hud.backgroundPreset.setSelected("Custom Color");
        hud.backgroundBlurRadius.setValue(0);
        hud.gradientSpeed.setValue(0.75F);
        hud.cornerRounding.setValue(4);
    }

    private static double[] point(ClientGameTestContext context, Target target, boolean corner) {
        return context.computeOnClient(client -> {
            float[] bounds = bounds(target);
            float coordinateScale = target.model() ? (float) client.getWindow().getScaleFactor() : 1;
            if (!corner) return new double[]{bounds[0] + 20 * coordinateScale, bounds[1] + 20 * coordinateScale};
            float inset = target.model() ? 7 * coordinateScale : target.index() == 22 ? 5 : 0;
            return new double[]{bounds[0] + bounds[2] - inset, bounds[1] + bounds[3] - (target.index() == 22 ? 5 : 0)};
        });
    }

    private static void release(ClientGameTestContext context, NativeMouseInput mouse, Target target, boolean resize) {
        int before = context.computeOnClient(client -> HudEditorProbe.batchCaptures);
        mouse.release(context);
        context.runOnClient(client -> {
            render(client, 3);
            check(!target.interaction(false) && !target.interaction(true) && selected() == -1 && transform() == null,
                    target.label() + ": release clears cached " + (resize ? "resize" : "drag"));
            check(HudEditorProbe.batchCaptures > before && hasAlpha(HudEditorProbe.batchFramebuffer, bounds(target)),
                    "release immediately merges the widget into the shared texture at its final transform");
            check(!editedBuffer().hasContent(), "released isolated pixels cannot be replayed as a ghost");
        });
    }

    private static void disableModules() {
        for (Module module : Main.getInstance().getModuleProvider().getModules()) module.setStateSilent(false);
    }

    private static void render(ClientGameTestContext context, int frames) { context.runOnClient(client -> render(client, frames)); }

    private static void render(MinecraftClient client, int frames) {
        for (int frame = 0; frame < frames; frame++) client.gameRenderer.render(RenderTickCounter.ZERO, true);
    }

    private static int selected() { return (int) hudField("isolatedHudIndex"); }
    private static HudCachedTransform transform() { return (HudCachedTransform) hudField("editedHudTransform"); }
    private static HudBuffer editedBuffer() { return (HudBuffer) hudField("PHAZE_EDITED_HUD_BUFFER"); }
    private static Object hudField(String name) { return field(CustomHudRenderer.class, name, null); }

    private static float[] bounds(Target target) {
        int index = target.index();
        return new float[]{((float[]) hudField("HUD_SNAP_X"))[index], ((float[]) hudField("HUD_SNAP_Y"))[index],
                ((float[]) hudField("HUD_SNAP_WIDTH"))[index], ((float[]) hudField("HUD_SNAP_HEIGHT"))[index]};
    }

    private static HudAnimationRefreshClock clock(Object buffer) { return (HudAnimationRefreshClock) field(buffer.getClass(), "refreshClock", buffer); }

    private static void park(Object buffer) { clock(buffer).captured(System.nanoTime() + 60_000_000_000L); }

    private static long crc(Framebuffer framebuffer) {
        var pixels = MemoryUtil.memAlloc(framebuffer.textureWidth * framebuffer.textureHeight * 4);
        try (Render2DState state = Render2DState.capture()) {
            GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, framebuffer.fbo);
            GL11C.glReadPixels(0, 0, framebuffer.textureWidth, framebuffer.textureHeight, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixels);
            CRC32 crc = new CRC32();
            crc.update(pixels);
            return crc.getValue();
        } finally { MemoryUtil.memFree(pixels); }
    }

    private static boolean hasAlpha(Framebuffer framebuffer, float[] rectangle) {
        int left = Math.clamp((int) Math.floor(rectangle[0]), 0, framebuffer.textureWidth);
        int top = Math.clamp((int) Math.floor(rectangle[1]), 0, framebuffer.textureHeight);
        int right = Math.clamp((int) Math.ceil(rectangle[0] + rectangle[2]), left, framebuffer.textureWidth);
        int bottom = Math.clamp((int) Math.ceil(rectangle[1] + rectangle[3]), top, framebuffer.textureHeight);
        if (right == left || bottom == top) return false;
        var pixels = MemoryUtil.memAlloc((right - left) * (bottom - top) * 4);
        try (Render2DState state = Render2DState.capture()) {
            GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, framebuffer.fbo);
            GL11C.glReadPixels(left, framebuffer.textureHeight - bottom, right - left, bottom - top, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixels);
            for (int index = 3; index < pixels.capacity(); index += 4) if (pixels.get(index) != 0) return true;
            return false;
        } finally { MemoryUtil.memFree(pixels); }
    }

    private record Frozen(HudEditorProbe.Counts counts, HudCachedTransform transform, Framebuffer child, long crc) {
        static Frozen capture() {
            check(HudEditorClientTest.transform() != null && editedBuffer().hasContent(), "selected capture publishes pixels with their transform");
            park(BatchedHudBuffer.INSTANCE);
            park(editedBuffer());
            return new Frozen(HudEditorProbe.counts(), HudEditorClientTest.transform(), HudEditorProbe.childFramebuffer,
                    HudEditorClientTest.crc(HudEditorProbe.childFramebuffer));
        }

        void assertUnchanged() {
            check(counts.equals(HudEditorProbe.counts()), "mouse/display frames do not recapture content, items, entity models or blur masks");
            check(transform == HudEditorClientTest.transform(), "live input never overwrites a cached image's published transform");
            check(child == HudEditorProbe.childFramebuffer && crc == HudEditorClientTest.crc(child), "drag/resize keeps the same FBO and pixels");
        }
    }

    private record Target(Module module, int index) {
        boolean model() { return index == 24; }
        String label() { return module.getVisibleName(); }
        float scale() { return module instanceof RectHudModule rect ? rect.getHudScale() : ((ArmorHud) module).getHudScale(); }
        boolean interaction(boolean resize) {
            return index == 29 ? (boolean) hudField(resize ? "armorResizing" : "armorDragging")
                    : ((boolean[]) hudField(resize ? "RECT_RESIZING" : "RECT_DRAGGING"))[index];
        }
    }

    private record SavedState(JsonObject config, String language, Screen screen, int width, int height, int guiScale,
                              int viewDistance, boolean hidden, boolean pause, Consumer<Setting> settings, Consumer<Module> modules) {
        @SuppressWarnings("unchecked")
        static SavedState capture(MinecraftClient client) {
            return new SavedState(((JsonObject) invoke(ConfigManager.getInstance(), "buildCurrentConfigJson", new Class<?>[0])).deepCopy(),
                    Theme.getInstance().language.getSelected(), client.currentScreen, client.getWindow().getWidth(), client.getWindow().getHeight(),
                    client.options.getGuiScale().getValue(), client.options.getViewDistance().getValue(), client.options.hudHidden,
                    client.options.pauseOnLostFocus, (Consumer<Setting>) field(Setting.class, "globalChangeListener", null),
                    (Consumer<Module>) field(Module.class, "globalStateChangeListener", null));
        }

        void restore(MinecraftClient client) {
            try {
                invoke(ConfigManager.getInstance(), "applyConfigJson", new Class<?>[]{JsonObject.class}, config);
                client.options.getGuiScale().setValue(guiScale);
                client.onResolutionChanged();
                client.options.getViewDistance().setValue(viewDistance);
                client.options.hudHidden = hidden;
                client.options.pauseOnLostFocus = pause;
                Lang.setActive(language);
                client.setScreen(screen);
                BatchedHudBuffer.INSTANCE.invalidate();
            } finally {
                Setting.setGlobalChangeListener(settings);
                Module.setGlobalStateChangeListener(modules);
            }
        }
    }

    private static Object field(Class<?> owner, String name, Object instance) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(owner.getName() + "." + name, failure); }
    }

    private static Object invoke(Object instance, String name, Class<?>[] types, Object... arguments) {
        try {
            var method = instance.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(instance, arguments);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(name, failure); }
    }

    private static void close(float actual, float expected, String message) { check(Math.abs(actual - expected) < 0.05F, message + ": " + actual + " vs " + expected); }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
