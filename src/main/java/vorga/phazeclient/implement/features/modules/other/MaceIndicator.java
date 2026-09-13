package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public final class MaceIndicator extends Module {
    private static final MaceIndicator INSTANCE = new MaceIndicator();

    private static final int RGB_RED = 0xFF3030;
    private static final int RGB_YELLOW = 0xFFD840;
    private static final int RGB_GREEN = 0x33FF55;

    public final SectionSetting generalSection = new SectionSetting("General");
    public final ValueSetting opacity = new ValueSetting(
            "Opacity",
            "Fill opacity in percent"
    ).range(0, 100).step(1).setValue(55);

    private MaceIndicator() {
        super("mace_indicator", "Mace Indicator", ModuleCategory.UTILITIES);
        opacity.setFullWidth(true);
        setup(generalSection, opacity);
    }

    public static MaceIndicator getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Tints maces in your inventory red/yellow/green based on attack cooldown progress";
    }

    @Override
    public String getIcon() {
        return "mace_indicator.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public int colorForStack(ItemStack stack) {
        if (!isEnabled() || stack == null || stack.isEmpty()) {
            return 0;
        }
        if (!stack.isOf(Items.MACE)) {
            return 0;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return 0;
        }
        PlayerEntity p = mc.player;
        if (p == null) {
            return 0;
        }

        ItemStack held = p.getMainHandStack();
        if (held == null || !held.isOf(Items.MACE)) {
            return 0;
        }

        float charge = chargeOf(p);
        int rgb;
        if (charge <= 0.30F) {
            rgb = RGB_RED;
        } else if (charge <= 0.60F) {
            rgb = RGB_YELLOW;
        } else {
            rgb = RGB_GREEN;
        }
        return packArgb(rgb, opacity.getInt() / 100.0F);
    }

    public float chargeOf(PlayerEntity p) {
        if (p == null) {
            return 0.0F;
        }
        return Math.min(1.0F, Math.max(0.0F, p.getAttackCooldownProgress(0.0F)));
    }

    private static int packArgb(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }
}
