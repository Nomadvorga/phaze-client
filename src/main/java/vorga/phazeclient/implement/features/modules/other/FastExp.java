package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public final class FastExp extends Module {
    private static final FastExp INSTANCE = new FastExp();

    public final SectionSetting generalSection = new SectionSetting("General");
    public final ValueSetting delay = new ValueSetting(
            "Delay",
            "Minimum gap (ms) between bottle throws. 50 reproduces the unrestricted Fast-Exp behavior (one throw per game tick); raising it throttles the rate, with 200 matching vanilla's natural 4-tick cooldown."
    ).range(50, 200).step(10).setValue(50);

    /**
     * Wall-clock time of the last frame on which the mixin was allowed to
     * zero {@code itemUseCooldown}. Used to enforce {@link #delay} between
     * consecutive bypasses without touching the underlying tick counter.
     */
    private long lastBypassMs = 0L;

    private FastExp() {
        super("fast_exp", "Fast Exp", ModuleCategory.UTILITIES);
        delay.setFullWidth(true);
        setup(generalSection, delay);
    }

    public static FastExp getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Removes the right-click delay when throwing experience bottles";
    }

    @Override
    public String getIcon() {
        return "fast_exp.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    /**
     * Returns true when the local player is currently holding an experience
     * bottle in either hand, the module is enabled, AND enough wall-clock
     * time has elapsed since the last bypass to satisfy the {@link #delay}
     * setting. The mixin uses this to decide whether to zero out
     * {@code MinecraftClient.itemUseCooldown} so holding right-click throws
     * bottles at the user-configured rate instead of vanilla's fixed
     * 4-tick cooldown.
     *
     * <p>Side-effect: when the method returns {@code true} it advances
     * {@link #lastBypassMs} to the current time. Callers must therefore
     * invoke it exactly once per frame on the throttle path - which the
     * mixin does, gated by every other condition that would suppress the
     * bypass anyway.
     */
    public static boolean shouldFastThrow() {
        FastExp module = INSTANCE;
        if (module == null || !module.isEnabled()) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return false;
        }
        PlayerEntity player = client.player;
        if (player == null) {
            return false;
        }

        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();
        boolean holding = (mainHand != null && mainHand.isOf(Items.EXPERIENCE_BOTTLE))
                || (offHand != null && offHand.isOf(Items.EXPERIENCE_BOTTLE));
        if (!holding) {
            return false;
        }

        long now = System.currentTimeMillis();
        long delayMs = (long) module.delay.getValue();
        if (now - module.lastBypassMs < delayMs) {
            return false;
        }
        module.lastBypassMs = now;
        return true;
    }
}
