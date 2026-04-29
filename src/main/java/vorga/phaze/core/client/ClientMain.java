package vorga.phazeclient.core.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.implement.menu.MenuScreen;

public class ClientMain implements ClientModInitializer {
    private static final KeyBinding OPEN_MENU_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                    "key.phaze.open_menu",
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_RIGHT_SHIFT,
                    "category.phaze"
            )
    );

    private int saveTimer = 0;
    private static final int SAVE_INTERVAL_TICKS = 100;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(this::onClientStopping);
    }

    private void onClientTick(MinecraftClient client) {
        while (OPEN_MENU_KEY.wasPressed()) {
            if (client.currentScreen instanceof MenuScreen) {
                client.setScreen(null);
            } else {
                MenuScreen.INSTANCE.openGui();
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