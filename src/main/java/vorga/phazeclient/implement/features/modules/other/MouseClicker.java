package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.Hand;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.base.util.ServerUtil;

/**
 * Auto-click utility (a.k.a. Tape Mouse). Periodically attacks the targeted
 * entity or uses the held item depending on the configured hand.
 *
 * Restricted to a fixed list of supported servers (see {@link ServerUtil#isMouseClickerSupported()}).
 */
public final class MouseClicker extends Module {
    private static final MouseClicker INSTANCE = new MouseClicker();

    private static final String HAND_LEFT = "Left";
    private static final String HAND_RIGHT = "Right";

    public final SectionSetting generalSection = new SectionSetting("General");
    public final ValueSetting clickDelay = new ValueSetting("Click Delay", "Ticks between clicks (20 = 1s)")
            .range(1, 100)
            .setValue(10);
    public final SelectSetting hand = new SelectSetting("Hand", "Which hand action to perform")
            .value(HAND_LEFT, HAND_RIGHT)
            .selected(HAND_LEFT);

    private int tickCounter;

    private MouseClicker() {
        super("mouseclicker", "Tape Mouse", ModuleCategory.UTILITIES);
        clickDelay.setFullWidth(true);
        hand.setFullWidth(true);
        setup(generalSection, clickDelay, hand);
    }

    public static MouseClicker getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Periodically attacks or uses item based on selected hand";
    }

    @Override
    public String getIcon() {
        return "mouseclicker.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    @Override
    public boolean isServerAllowed() {
        return ServerUtil.isMouseClickerSupported();
    }

    @Override
    public void deactivate() {
        tickCounter = 0;
    }

    public void onTick() {
        if (!isEnabled()) {
            tickCounter = 0;
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null
                || client.interactionManager == null || client.currentScreen != null) {
            return;
        }

        if (!ServerUtil.isMouseClickerSupported()) {
            return;
        }

        tickCounter++;
        int delay = Math.max(1, clickDelay.getInt());
        if (tickCounter < delay) {
            return;
        }
        tickCounter = 0;

        performClick(client);
    }

    private void performClick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager interaction = client.interactionManager;
        if (player == null || interaction == null) {
            return;
        }

        if (HAND_RIGHT.equalsIgnoreCase(hand.getSelected())) {
            // Use item / interact with block
            interaction.interactItem(player, Hand.MAIN_HAND);
            return;
        }

        // Left-hand: attack the targeted entity if any, otherwise just swing.
        if (client.crosshairTarget != null && client.targetedEntity != null) {
            interaction.attackEntity(player, client.targetedEntity);
            player.swingHand(Hand.MAIN_HAND);
        } else {
            player.swingHand(Hand.MAIN_HAND);
        }
    }
}
