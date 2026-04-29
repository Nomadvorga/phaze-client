package vorga.phazeclient.implement.features.modules.other;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;

public final class AutoSprint extends Module {
    private static final AutoSprint INSTANCE = new AutoSprint();
    public final BooleanSetting showInSprintHud = new BooleanSetting("Show in sprint hud", "Show AutoSprint in Sprint HUD instead of Vanilla").setValue(true);

    public static AutoSprint getInstance() {
        return INSTANCE;
    }

    private AutoSprint() {
        super("auto_sprint", "AutoSprint", ModuleCategory.OTHER, true, false);
        setup(showInSprintHud);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!isEnabled()) {
                return;
            }
            tick(client);
        });
    }

    private void tick(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null || client.currentScreen != null) {
            return;
        }
        if (!client.options.forwardKey.isPressed() || client.options.sneakKey.isPressed()) {
            return;
        }
        if (client.player.isUsingItem() || client.player.horizontalCollision) {
            return;
        }
        if (!client.player.isCreative() && !client.player.isSpectator() && client.player.getHungerManager().getFoodLevel() <= 6) {
            return;
        }

        client.player.setSprinting(true);
    }

    @Override
    public String getDescription() {
        return "Automatically sprints while holding forward";
    }

    @Override
    public String getIcon() {
        return null;
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
