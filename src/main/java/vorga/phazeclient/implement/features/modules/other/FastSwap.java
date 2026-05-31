package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BindSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.base.util.ServerUtil;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Pulse-style hotbar selector: every configured key jumps to the first
 * matching item in the player's hotbar.
 */
public final class FastSwap extends Module {
    private static final FastSwap INSTANCE = new FastSwap();
    private static final String KEY_DESCRIPTION = "Select the first matching item in your hotbar";

    private static final String TRAPKA_NAME = "трапка";
    private static final String PLATE_NAME = "пласт";
    private static final String DISORIENTATION_NAME = "дезориентация";
    private static final String REVEALING_DUST_NAME = "явная пыль";

    private static final String BALL_LIGHTNING_NAME = "шаровая молния";
    private static final String SLIME_LUMP_NAME = "ком слизи";
    private static final String TURTLE_GRIP_NAME = "черепаший захват";
    private static final String SPIDER_FATE_NAME = "паучья судьба";
    private static final String STUN_NAME = "стан";
    private static final String MAGNETIC_SPHERE_NAME = "магнитный шар";
    private static final String EXPLOSIVE_TRAPKA_NAME = "взрывная трапка";
    private static final String EXPLOSIVE_THING_NAME = "взрывная штучка";
    private static final String STAR_STUN_NAME = "стан звезды";

    public final SectionSetting basicSection = new SectionSetting("Basic");
    public final SectionSetting funtimeSection = new SectionSetting("FunTime");
    public final SectionSetting fillCubeSection = new SectionSetting("FillCube");

    public final BindSetting chorusKey = bindSetting("Chorus Key");
    public final BindSetting enderpearlKey = bindSetting("Enderpearl Key");
    public final BindSetting healingPotionKey = bindSetting("Healing Potion Key");

    public final BindSetting trapkaKey = bindSetting("Trapka Key");
    public final BindSetting disorientationKey = bindSetting("Disorientation Key");
    public final BindSetting plateKey = bindSetting("Plate Key");
    public final BindSetting revealingDustKey = bindSetting("Revealing Dust Key");

    public final BindSetting ballLightningKey = bindSetting("Ball Lightning Key");
    public final BindSetting slimeLumpKey = bindSetting("Slime Lump Key");
    public final BindSetting turtleGripKey = bindSetting("Turtle Grip Key");
    public final BindSetting spiderFateKey = bindSetting("Spider Fate Key");
    public final BindSetting stunKey = bindSetting("Stun Key");
    public final BindSetting magneticSphereKey = bindSetting("Magnetic Sphere Key");
    public final BindSetting explosiveTrapkaKey = bindSetting("Explosive Trapka Key");
    public final BindSetting explosiveThingKey = bindSetting("Explosive Thing Key");
    public final BindSetting starStunKey = bindSetting("Star Stun Key");
    public final BindSetting snowLumpKey = bindSetting("Snow Lump Key");

    private final List<ItemHotkey> hotkeys;

    private FastSwap() {
        super("fast_swap", "Fast Swap", ModuleCategory.UTILITIES);

        funtimeSection.visible(this::shouldShowFunTimeSection);
        fillCubeSection.visible(this::shouldShowFillCubeSection);

        trapkaKey.visible(this::shouldShowFunTimeSection);
        disorientationKey.visible(this::shouldShowFunTimeSection);
        plateKey.visible(this::shouldShowFunTimeSection);
        revealingDustKey.visible(this::shouldShowFunTimeSection);

        ballLightningKey.visible(this::shouldShowFillCubeSection);
        slimeLumpKey.visible(this::shouldShowFillCubeSection);
        turtleGripKey.visible(this::shouldShowFillCubeSection);
        spiderFateKey.visible(this::shouldShowFillCubeSection);
        stunKey.visible(this::shouldShowFillCubeSection);
        magneticSphereKey.visible(this::shouldShowFillCubeSection);
        explosiveTrapkaKey.visible(this::shouldShowFillCubeSection);
        explosiveThingKey.visible(this::shouldShowFillCubeSection);
        starStunKey.visible(this::shouldShowFillCubeSection);
        snowLumpKey.visible(this::shouldShowFillCubeSection);

        setup(
                basicSection,
                chorusKey,
                enderpearlKey,
                healingPotionKey,
                funtimeSection,
                trapkaKey,
                disorientationKey,
                plateKey,
                revealingDustKey,
                fillCubeSection,
                ballLightningKey,
                slimeLumpKey,
                turtleGripKey,
                spiderFateKey,
                stunKey,
                magneticSphereKey,
                explosiveTrapkaKey,
                explosiveThingKey,
                starStunKey,
                snowLumpKey
        );

        hotkeys = List.of(
                new ItemHotkey(chorusKey, stack -> stack.isOf(Items.CHORUS_FRUIT)),
                new ItemHotkey(enderpearlKey, stack -> stack.isOf(Items.ENDER_PEARL)),
                new ItemHotkey(healingPotionKey, FastSwap::isHealingPotion),

                new ItemHotkey(trapkaKey, namedItem(Items.NETHERITE_SCRAP, TRAPKA_NAME)),
                new ItemHotkey(disorientationKey, namedItem(Items.ENDER_EYE, DISORIENTATION_NAME)),
                new ItemHotkey(plateKey, namedItem(Items.DRIED_KELP, PLATE_NAME)),
                new ItemHotkey(revealingDustKey, namedItem(Items.SUGAR, REVEALING_DUST_NAME)),

                new ItemHotkey(ballLightningKey, namedItem(Items.NETHER_STAR, BALL_LIGHTNING_NAME)),
                new ItemHotkey(slimeLumpKey, namedItem(Items.SLIME_BALL, SLIME_LUMP_NAME)),
                new ItemHotkey(turtleGripKey, namedItem(Items.TURTLE_SCUTE, TURTLE_GRIP_NAME)),
                new ItemHotkey(spiderFateKey, namedItem(Items.COBWEB, SPIDER_FATE_NAME)),
                new ItemHotkey(stunKey, namedItem(Items.ENDER_EYE, STUN_NAME)),
                new ItemHotkey(magneticSphereKey, namedItem(Items.FIREWORK_STAR, MAGNETIC_SPHERE_NAME)),
                new ItemHotkey(explosiveTrapkaKey, namedItem(Items.PRISMARINE_SHARD, EXPLOSIVE_TRAPKA_NAME)),
                new ItemHotkey(explosiveThingKey, namedItem(Items.FIRE_CHARGE, EXPLOSIVE_THING_NAME)),
                new ItemHotkey(starStunKey, namedItem(Items.NETHER_STAR, STAR_STUN_NAME)),
                new ItemHotkey(snowLumpKey, stack -> stack.isOf(Items.SNOWBALL))
        );
    }

    public static FastSwap getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return Lang.translate("Quickly selects matching hotbar items on hotkey press");
    }

    @Override
    public String getIcon() {
        return "autoswap.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public void onKey(int key, int action) {
        if (!isEnabled() || action != GLFW.GLFW_PRESS || key == GLFW.GLFW_KEY_UNKNOWN) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null || mc.currentScreen != null) {
            return;
        }

        for (ItemHotkey hotkey : hotkeys) {
            if (!hotkey.bind.isVisible() || hotkey.bind.getKey() != key) {
                continue;
            }

            int slot = findMatchingHotbarSlot(mc, hotkey.matcher);
            if (slot != -1) {
                mc.player.getInventory().selectedSlot = slot;
            }
            return;
        }
    }

    private boolean shouldShowFunTimeSection() {
        return !ServerUtil.isFillCubeServer();
    }

    private boolean shouldShowFillCubeSection() {
        return !ServerUtil.isFunTimeServer();
    }

    private static BindSetting bindSetting(String name) {
        BindSetting setting = new BindSetting(name, KEY_DESCRIPTION);
        setting.setKey(GLFW.GLFW_KEY_UNKNOWN);
        setting.setFullWidth(true);
        return setting;
    }

    private static int findMatchingHotbarSlot(MinecraftClient mc, Predicate<ItemStack> matcher) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!stack.isEmpty() && matcher.test(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private static Predicate<ItemStack> namedItem(net.minecraft.item.Item item, String needle) {
        return stack -> stack.isOf(item) && matchesDisplayName(stack, needle);
    }

    private static boolean matchesDisplayName(ItemStack stack, String needle) {
        if (needle == null || needle.isBlank()) {
            return true;
        }
        String displayName = stack.getName().getString().toLowerCase(Locale.ROOT);
        return displayName.contains(needle.toLowerCase(Locale.ROOT));
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

    private static final class ItemHotkey {
        private final BindSetting bind;
        private final Predicate<ItemStack> matcher;

        private ItemHotkey(BindSetting bind, Predicate<ItemStack> matcher) {
            this.bind = bind;
            this.matcher = matcher;
        }
    }
}
