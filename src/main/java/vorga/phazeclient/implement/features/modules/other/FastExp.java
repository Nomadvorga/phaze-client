package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;

public final class FastExp extends Module {
    private static final FastExp INSTANCE = new FastExp();

    private FastExp() {
        super("fast_exp", "Fast Exp", ModuleCategory.UTILITIES);
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
     * bottle in either hand and the module is enabled. The mixin uses this to
     * decide whether to zero out {@code MinecraftClient.itemUseCooldown} so
     * holding right-click throws bottles every tick instead of every 4 ticks.
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
        return (mainHand != null && mainHand.isOf(Items.EXPERIENCE_BOTTLE))
                || (offHand != null && offHand.isOf(Items.EXPERIENCE_BOTTLE));
    }
}
