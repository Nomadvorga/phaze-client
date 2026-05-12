package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

/**
 * Combat helper that paints a colored fill over every mace in the
 * player's inventory and hotbar based on the local player's attack
 * cooldown progress - i.e. how full the white-to-yellow attack bar
 * over the crosshair is. Reads {@link PlayerEntity#getAttackCooldownProgress(float)}
 * which already returns a normalised {@code [0, 1]} value, so no
 * extra scaling is required.
 *
 * <p>Color thresholds match the user-facing spec:
 * <ul>
 *   <li>{@code [0%, 30%]}   -> red    (just swung, attack bar still empty)</li>
 *   <li>{@code (30%, 60%]}  -> yellow (cooldown half-recovered)</li>
 *   <li>{@code (60%, 100%]} -> green  (ready or nearly ready to swing)</li>
 * </ul>
 *
 * <p>Boundary values go to the warmer-side bucket (e.g. exactly 30%
 * reads red) so a player parked on the threshold doesn't visibly
 * flicker between two colors.
 */
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

    /**
     * Pre-multiplied ARGB color the {@code InGameHudMaceIndicatorMixin}
     * and {@code HandledScreenMaceIndicatorMixin} should fill over the
     * slot occupied by {@code stack}, or {@code 0} when the slot
     * shouldn't be highlighted (not a mace, module disabled, no player).
     *
     * <p>Opacity 0 collapses the alpha byte to zero, which the mixins
     * already short-circuit via the {@code (color & 0xFF000000) == 0}
     * test - so the user-facing "Opacity 0%" effectively hides the
     * overlay without a separate boolean.
     */
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

    /**
     * Charge fraction in {@code [0, 1]} - just the vanilla attack
     * cooldown progress. {@code baseTime = 0} returns the progress
     * at the current tick (i.e. no extrapolation toward the next
     * tick), which is what we want for an inventory overlay that
     * shouldn't lead the cooldown bar shown over the crosshair.
     */
    public float chargeOf(PlayerEntity p) {
        if (p == null) {
            return 0.0F;
        }
        return Math.min(1.0F, Math.max(0.0F, p.getAttackCooldownProgress(0.0F)));
    }

    /** Packs an RGB triplet plus a 0..1 alpha into the int format {@code DrawContext.fill} expects. */
    private static int packArgb(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }
}
