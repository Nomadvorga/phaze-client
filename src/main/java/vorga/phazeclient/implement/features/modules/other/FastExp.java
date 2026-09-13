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
