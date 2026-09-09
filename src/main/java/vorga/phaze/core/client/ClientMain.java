package vorga.phazeclient.core.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.MenuPanoramaRenderer;
import vorga.phazeclient.implement.menu.MenuPanoramaRegistry;
import vorga.phazeclient.implement.features.modules.other.MotionBlur;
import vorga.phazeclient.implement.features.modules.other.ColorCorrection;

public class ClientMain implements ClientModInitializer {
    /**
     * 1.21.11: KeyBinding no longer takes a raw translation-key String for its
     * category - it takes a {@link KeyBinding.Category} record created from an
     * Identifier. The displayed label is derived by the game as
     * {@code Identifier.toTranslationKey("key.category")}, i.e. this category
     * renders as the lang key {@code key.category.phaze.main}
     * (the old key was {@code category.phaze}).
     * TODO(1.21.11): add "key.category.phaze.main" to assets/phaze/lang/*.json
     * (value "Phaze") and drop the now-unused "category.phaze" entry.
     */
    private static final KeyBinding.Category PHAZE_CATEGORY =
            KeyBinding.Category.create(Identifier.of("phaze", "main"));

    private static final KeyBinding OPEN_MENU_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                    "key.phaze.open_menu",
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_RIGHT_SHIFT,
                    PHAZE_CATEGORY
            )
    );

    private int saveTimer = 0;
    private int preloadTimer = 0;
    private boolean guiPreloaded = false;
    private static final int SAVE_INTERVAL_TICKS = 100;
    private static final int PRELOAD_DELAY_TICKS = 40;

    @Override
    public void onInitializeClient() {
        MenuPanoramaRegistry.ensureDirectoryExists();
        vorga.phazeclient.implement.cosmetics.bridge.PhazeCosmeticPetRenderer.register();
        vorga.phazeclient.implement.cosmetics.bridge.PhazeCosmeticGraffitiRenderer.register();
        SpecialGuiElementRegistry.register(context ->
                new vorga.phazeclient.implement.cosmetics.CosmeticGuiElementRenderer(
                        context.vertexConsumers()));
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(this::onClientStopping);

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public void reload(ResourceManager manager) {
                MotionBlur.getInstance().shader.reload();
                ColorCorrection.getInstance().shader.reload();
                MenuPanoramaRenderer.onResourcesReloaded();
                MenuPanoramaRegistry.onResourcesReloaded();
            }

            @Override
            public Identifier getFabricId() {
                return Identifier.of("phazeclient", "shader_reloader");
            }
        });
    }

    private void onClientTick(MinecraftClient client) {
        vorga.phazeclient.implement.cosmetics.CosmeticsSyncService.getInstance().tick(client);
        while (OPEN_MENU_KEY.wasPressed()) {
            if (client.currentScreen instanceof MenuScreen) {
                client.setScreen(null);
            } else {
                MenuScreen.INSTANCE.openGui();
            }
        }

        if (!guiPreloaded) {
            preloadTimer++;
            if (preloadTimer >= PRELOAD_DELAY_TICKS) {
                MenuScreen.preload();
                guiPreloaded = true;
            }
        }

        saveTimer++;
        if (saveTimer >= SAVE_INTERVAL_TICKS) {
            saveTimer = 0;
        }
    }

    private void onClientStopping(MinecraftClient client) {
        vorga.phazeclient.core.Main.getInstance().getConfigManager().saveCurrentConfig();
        System.out.println("[Phaze Client] Client stopping, config saved!");
    }
}
