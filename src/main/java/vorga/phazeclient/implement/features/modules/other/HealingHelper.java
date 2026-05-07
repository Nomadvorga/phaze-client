package vorga.phazeclient.implement.features.modules.other;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

/**
 * Combat helper that draws a pulsing colored overlay on inventory and hotbar
 * slots whenever the player is in a state that calls for a healing item.
 *
 * <p>Three thresholds drive the highlight rules:
 * <ul>
 *   <li>{@link #hpThreshold} - paint healing potions green when current HP
 *       falls to or below this value (1-20). Flips to yellow if the
 *       saturation rule is also active, signaling the user should also
 *       deal with low food.</li>
 *   <li>{@link #gappleCooldownSec} - paint enchanted golden apples red on
 *       module enable, then suppress the highlight for N seconds after one
 *       is consumed before re-enabling it as a "you can eat another now"
 *       reminder. 1-120 s range matches the upstream
 *       {@code ItemHighlighterModule} use cases.</li>
 *   <li>{@link #saturationThreshold} - paint regular golden apples orange
 *       when saturation drops to or below this value (1-20). Also flips
 *       the healing-potion color to yellow per the rule above.</li>
 * </ul>
 *
 * <p>All three highlights share the same 500 ms sine pulse so they breathe
 * together and don't visually compete. Color packing is done via
 * {@link #packArgb}: RGB is decided by the rule, alpha by the global pulse
 * scaled with {@link #MAX_ALPHA} to keep the overlay readable without
 * masking the item sprite.
 *
 * <p>Enchanted-gapple consumption is detected entirely client-side by
 * watching {@link PlayerEntity#isUsingItem()} transitions on each client
 * tick: if the player WAS using an enchanted gapple last tick at a use
 * time at or near the consumable's finish (~32 ticks for the 1.6 s
 * default) and now isn't, we mark "eaten" and start the cooldown clock.
 * Cancelled eats (right-click released early) are ignored because their
 * use time stayed below the threshold. No mixin or networking required.
 */
public final class HealingHelper extends Module {
    private static final HealingHelper INSTANCE = new HealingHelper();

    /** Approx. tick at which a 1.6 s consumable finishes (20 TPS * 1.6 s). */
    private static final int CONSUMABLE_FINISH_TICKS = 32;
    /** Tolerance window for "we observed the use right at consumption time". */
    private static final int FINISH_TOLERANCE = 2;
    /** Pulse cycle length in ms - one full fade in + fade out. */
    private static final long PULSE_PERIOD_MS = 500L;
    /** Peak alpha at the apex of the pulse. Keeps the item sprite readable. */
    private static final float MAX_ALPHA = 0.55F;

    private static final int RGB_GREEN = 0x33FF55;
    private static final int RGB_YELLOW = 0xFFD840;
    private static final int RGB_RED = 0xFF3030;
    private static final int RGB_ORANGE = 0xFF8A00;

    public final SectionSetting generalSection = new SectionSetting("General");
    public final ValueSetting hpThreshold = new ValueSetting(
            "Healing Potion HP",
            "Highlight healing potions green while your HP is at or below this value (in half-hearts pairs, 1 = half a heart). Turns yellow if the saturation rule below is also flashing."
    ).range(1, 20).step(1).setValue(14);
    public final ValueSetting gappleCooldownSec = new ValueSetting(
            "Enchanted Gapple Cooldown",
            "Number of seconds to suppress the enchanted-gapple flash after you eat one; once this much time has passed it starts flashing again as a re-eat reminder."
    ).range(1, 120).step(1).setValue(60);
    public final ValueSetting saturationThreshold = new ValueSetting(
            "Gapple Saturation",
            "Highlight regular golden apples orange while your saturation is at or below this value (saturation max is 20)."
    ).range(1, 20).step(1).setValue(6);

    /** Tick state for the eat-detector. -1 means we weren't using an egapple last tick. */
    private int prevEgappleUseTime = -1;
    /** Wall-clock time (ms) of the last detected enchanted-gapple consumption. 0 = never. */
    private long lastEgappleEatenMs = 0L;

    private HealingHelper() {
        super("healing_helper", "Healing Helper", ModuleCategory.UTILITIES);
        hpThreshold.setFullWidth(true);
        gappleCooldownSec.setFullWidth(true);
        saturationThreshold.setFullWidth(true);
        setup(generalSection, hpThreshold, gappleCooldownSec, saturationThreshold);

        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    public static HealingHelper getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Pulses healing potions, golden apples, and enchanted gapples in your inventory based on HP, saturation, and a re-eat timer";
    }

    @Override
    public String getIcon() {
        return "healing_helper.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    /**
     * Runs the enchanted-gapple eat-detector. Cheap (a few ItemStack reads
     * and an integer compare) and safe to call regardless of whether the
     * module is enabled - tracking the timer even while disabled would just
     * mean the user enables the module right after eating and the cooldown
     * is already partially elapsed, which is a fine UX.
     */
    private void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return;
        }
        PlayerEntity p = mc.player;
        if (p == null) {
            prevEgappleUseTime = -1;
            return;
        }

        boolean usingEgappleNow = p.isUsingItem()
                && p.getActiveItem() != null
                && p.getActiveItem().isOf(Items.ENCHANTED_GOLDEN_APPLE);

        if (prevEgappleUseTime >= CONSUMABLE_FINISH_TICKS - FINISH_TOLERANCE && !usingEgappleNow) {
            // Last tick we were within the consumption window, this tick the
            // use is over => we ate it. (If the use was cancelled by
            // releasing right-click before the consumption point, prev would
            // have been below the threshold and we'd skip.)
            lastEgappleEatenMs = System.currentTimeMillis();
        }

        prevEgappleUseTime = usingEgappleNow ? p.getItemUseTime() : -1;
    }

    /**
     * Returns the pre-multiplied ARGB color this module wants to paint over
     * the slot occupied by {@code stack}, or {@code 0} when the stack
     * shouldn't be highlighted right now. Both the
     * inventory mixin and the hotbar mixin call this from their TAIL
     * inject and {@code DrawContext.fill} the result over the slot rect.
     *
     * <p>Returning 0 (fully-transparent black RGB 0x000000) is used as a
     * "no highlight" sentinel - none of the four highlight palettes use
     * pure black so the check is unambiguous.
     */
    public int colorForStack(ItemStack stack) {
        if (!isEnabled() || stack == null || stack.isEmpty()) {
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

        float health = p.getHealth();
        float saturation = p.getHungerManager().getSaturationLevel();
        int hpThr = hpThreshold.getInt();
        int satThr = saturationThreshold.getInt();
        boolean lowHp = health <= hpThr;
        boolean lowSat = saturation <= satThr;

        int rgb = 0;

        if (isHealingPotion(stack)) {
            if (lowHp) {
                // Per the May follow-up: green when slider 3 (saturation
                // rule) is ALSO firing, signaling "you can fix both with
                // a heal potion + a future eat", and yellow when only the
                // HP rule fires (heal but watch your food next).
                rgb = lowSat ? RGB_GREEN : RGB_YELLOW;
            }
        } else if (stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
            long since = System.currentTimeMillis() - lastEgappleEatenMs;
            long cooldownMs = (long) gappleCooldownSec.getValue() * 1000L;
            if (lastEgappleEatenMs == 0L || since >= cooldownMs) {
                rgb = RGB_RED;
            }
        } else if (stack.isOf(Items.GOLDEN_APPLE)) {
            if (lowSat) {
                rgb = RGB_ORANGE;
            }
        }

        if (rgb == 0) {
            return 0;
        }
        return packArgb(rgb, MAX_ALPHA * pulseAlpha());
    }

    /**
     * 0.5 s sine wave in [0, 1]. Drives the synchronized pulse so all
     * highlighted slots breathe in lockstep regardless of when each one
     * started flashing. Reading the system clock instead of incrementing
     * a counter keeps it framerate-independent.
     */
    private static float pulseAlpha() {
        long t = System.currentTimeMillis() % PULSE_PERIOD_MS;
        return (float) Math.sin(Math.PI * t / (double) PULSE_PERIOD_MS);
    }

    /** Packs an RGB triplet plus a 0..1 alpha into the int format {@code DrawContext.fill} expects. */
    private static int packArgb(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    /**
     * True for any potion (regular, splash, or lingering) whose
     * {@link PotionContentsComponent} carries an Instant Health effect.
     * Mirrors the existing {@code AutoPotion#hasMatchingPotionContents}
     * pattern so the registry-entry comparison matches across versions.
     */
    private static boolean isHealingPotion(ItemStack stack) {
        if (!stack.isOf(Items.POTION) && !stack.isOf(Items.SPLASH_POTION) && !stack.isOf(Items.LINGERING_POTION)) {
            return false;
        }
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) {
            return false;
        }
        for (StatusEffectInstance effect : contents.getEffects()) {
            if (effect.getEffectType().equals(StatusEffects.INSTANT_HEALTH)) {
                return true;
            }
        }
        return false;
    }
}
