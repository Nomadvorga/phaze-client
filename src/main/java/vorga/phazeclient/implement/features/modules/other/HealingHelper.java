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

import java.util.IdentityHashMap;
import java.util.Map;

public final class HealingHelper extends Module {
    private static final HealingHelper INSTANCE = new HealingHelper();

    private static final int CONSUMABLE_FINISH_TICKS = 32;

    private static final int FINISH_TOLERANCE = 2;

    private static final long PULSE_PERIOD_MS = 500L;

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

    private int prevEgappleUseTime = -1;

    private long lastEgappleEatenMs = 0L;
    private final Map<ItemStack, Integer> preparedColorCache = new IdentityHashMap<>();
    private boolean snapshotPrepared;
    private int healingPotionColor;
    private int enchantedGappleColor;
    private int regularGappleColor;

    private HealingHelper() {
        super("healing_helper", "Healing Helper", ModuleCategory.UTILITIES);
        hpThreshold.setFullWidth(true);
        gappleCooldownSec.setFullWidth(true);
        saturationThreshold.setFullWidth(true);
        setup(generalSection, hpThreshold, gappleCooldownSec, saturationThreshold);

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
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

    private void tick(MinecraftClient mc) {
        if (mc == null) {
            return;
        }
        PlayerEntity p = mc.player;
        if (p == null) {
            prevEgappleUseTime = -1;
            return;
        }

        ItemStack activeItem = p.getActiveItem();
        boolean usingEgappleNow = p.isUsingItem() && activeItem.isOf(Items.ENCHANTED_GOLDEN_APPLE);

        if (prevEgappleUseTime >= CONSUMABLE_FINISH_TICKS - FINISH_TOLERANCE && !usingEgappleNow) {

            lastEgappleEatenMs = System.currentTimeMillis();
        }

        prevEgappleUseTime = usingEgappleNow ? p.getItemUseTime() : -1;
    }

    public int colorForStack(ItemStack stack) {
        prepareSnapshot();
        return resolvePreparedColor(stack);
    }

    public void beginRenderPass() {
        prepareSnapshot();
        preparedColorCache.clear();
    }

    public int colorForPreparedStack(ItemStack stack) {
        if (!snapshotPrepared) {
            beginRenderPass();
        }
        if (!isEnabled() || stack == null || stack.isEmpty()) {
            return 0;
        }
        Integer cached = preparedColorCache.get(stack);
        if (cached != null) {
            return cached;
        }
        int color = resolvePreparedColor(stack);
        preparedColorCache.put(stack, color);
        return color;
    }

    private void prepareSnapshot() {
        snapshotPrepared = true;
        healingPotionColor = 0;
        enchantedGappleColor = 0;
        regularGappleColor = 0;
        if (!isEnabled()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return;
        }
        PlayerEntity p = mc.player;
        if (p == null) {
            return;
        }

        float health = p.getHealth();
        float saturation = p.getHungerManager().getSaturationLevel();
        int hpThr = hpThreshold.getInt();
        int satThr = saturationThreshold.getInt();
        boolean lowHp = health <= hpThr;
        boolean lowSat = saturation <= satThr;
        long now = System.currentTimeMillis();
        float alpha = MAX_ALPHA * pulseAlpha(now);

        if (lowHp) {
            healingPotionColor = packArgb(lowSat ? RGB_GREEN : RGB_YELLOW, alpha);
        }
        long cooldownMs = (long) gappleCooldownSec.getValue() * 1000L;
        if (lastEgappleEatenMs == 0L || now - lastEgappleEatenMs >= cooldownMs) {
            enchantedGappleColor = packArgb(RGB_RED, alpha);
        }
        if (lowSat) {
            regularGappleColor = packArgb(RGB_ORANGE, alpha);
        }
    }

    private int resolvePreparedColor(ItemStack stack) {
        if (!isEnabled() || stack == null || stack.isEmpty()) {
            return 0;
        }
        if (isHealingPotion(stack)) {
            return healingPotionColor;
        } else if (stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
            return enchantedGappleColor;
        } else if (stack.isOf(Items.GOLDEN_APPLE)) {
            return regularGappleColor;
        }
        return 0;
    }

    private static float pulseAlpha(long nowMs) {
        long t = nowMs % PULSE_PERIOD_MS;
        return (float) Math.sin(Math.PI * t / (double) PULSE_PERIOD_MS);
    }

    private static int packArgb(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

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
