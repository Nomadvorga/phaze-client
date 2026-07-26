package vorga.phazeclient.implement.features.modules.other;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;

public final class AutoSprint extends Module {
    private static final AutoSprint INSTANCE = new AutoSprint();

    public final vorga.phazeclient.api.feature.module.setting.implement.SectionSetting generalSection =
            new vorga.phazeclient.api.feature.module.setting.implement.SectionSetting("General");
    public final BooleanSetting showInSprintHud = new BooleanSetting("Show in sprint hud", "Show AutoSprint in Sprint HUD instead of Vanilla").setValue(true);

    public static AutoSprint getInstance() {
        return INSTANCE;
    }

    private AutoSprint() {
        super("auto_sprint", "AutoSprint", ModuleCategory.UTILITIES, true, false);
        showInSprintHud.setFullWidth(true);
        setup(generalSection, showInSprintHud);

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient client) {
        if (client == null || client.player == null || client.options == null) {
            return;
        }

        if (!isEnabled()) {
            // Release the sprint key whenever the module is off so we don't
            // hold sprint after toggling.
            client.options.sprintKey.setPressed(false);
            return;
        }

        // Hold sprint key down while enabled.
        client.options.sprintKey.setPressed(true);

        // Mirror upstream conditions: only auto-sprint when actually moving
        // forward, not sneaking / using an item, and with enough hunger.
        if (client.player.forwardSpeed > 0
                && !client.player.isSprinting()
                && !client.player.isSneaking()
                && !client.player.isUsingItem()
                && client.player.getHungerManager().getFoodLevel() > 6) {
            client.player.setSprinting(true);
        }
    }

    @Override
    public String getDescription() {
        return "Automatically sprints while holding forward";
    }

    @Override
    public String getIcon() {
        return "auto_sprint.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
