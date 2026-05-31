package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BindSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.TextSetting;
import vorga.phazeclient.base.util.Lang;

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
    private static final String NAME_DESCRIPTION = "Substring matched against the hotbar item's display name before selecting it";

    public final SectionSetting hotkeysSection = new SectionSetting("Hotkeys");
    public final SectionSetting nameSection = new SectionSetting("Name Overrides");

    public final BindSetting chorusKey = bindSetting("Chorus Key");
    public final BindSetting enderpearlKey = bindSetting("Enderpearl Key");
    public final BindSetting healingPotionKey = bindSetting("Healing Potion Key");
    public final BindSetting trapkaKey = bindSetting("Trapka Key");
    public final BindSetting plateKey = bindSetting("Plate Key");
    public final BindSetting disorientationKey = bindSetting("Disorientation Key");
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

    public final TextSetting trapkaName = nameSetting("Trapka Name", "трапка");
    public final TextSetting plateName = nameSetting("Plate Name", "пласт");
    public final TextSetting disorientationName = nameSetting("Disorientation Name", "дезориентация");
    public final TextSetting revealingDustName = nameSetting("Revealing Dust Name", "явная пыль");
    public final TextSetting ballLightningName = nameSetting("Ball Lightning Name", "шаровая молния");
    public final TextSetting slimeLumpName = nameSetting("Slime Lump Name", "ком слизи");
    public final TextSetting turtleGripName = nameSetting("Turtle Grip Name", "черепаший захват");
    public final TextSetting spiderFateName = nameSetting("Spider Fate Name", "паучья судьба");
    public final TextSetting stunName = nameSetting("Stun Name", "стан");
    public final TextSetting magneticSphereName = nameSetting("Magnetic Sphere Name", "магнитный шар");
    public final TextSetting explosiveTrapkaName = nameSetting("Explosive Trapka Name", "взрывная трапка");
    public final TextSetting explosiveThingName = nameSetting("Explosive Thing Name", "взрывная штучка");
    public final TextSetting starStunName = nameSetting("Star Stun Name", "стан звезды");
    public final TextSetting snowLumpName = nameSetting("Snow Lump Name", "ком снега");

    private final List<ItemHotkey> hotkeys;

    private FastSwap() {
        super("fast_swap", "Fast Swap", ModuleCategory.UTILITIES);
        setup(
                hotkeysSection,
                chorusKey,
                enderpearlKey,
                healingPotionKey,
                trapkaKey,
                plateKey,
                disorientationKey,
                revealingDustKey,
                ballLightningKey,
                slimeLumpKey,
                turtleGripKey,
                spiderFateKey,
                stunKey,
                magneticSphereKey,
                explosiveTrapkaKey,
                explosiveThingKey,
                starStunKey,
                snowLumpKey,
                nameSection,
                trapkaName,
                plateName,
                disorientationName,
                revealingDustName,
                ballLightningName,
                slimeLumpName,
                turtleGripName,
                spiderFateName,
                stunName,
                magneticSphereName,
                explosiveTrapkaName,
                explosiveThingName,
                starStunName,
                snowLumpName
        );

        hotkeys = List.of(
                new ItemHotkey(chorusKey, stack -> stack.isOf(Items.CHORUS_FRUIT)),
                new ItemHotkey(enderpearlKey, stack -> stack.isOf(Items.ENDER_PEARL)),
                new ItemHotkey(healingPotionKey, FastSwap::isHealingPotion),
                new ItemHotkey(trapkaKey, namedItem(Items.NETHERITE_SCRAP, trapkaName)),
                new ItemHotkey(plateKey, namedItem(Items.DRIED_KELP, plateName)),
                new ItemHotkey(disorientationKey, namedItem(Items.ENDER_EYE, disorientationName)),
                new ItemHotkey(revealingDustKey, namedItem(Items.SUGAR, revealingDustName)),
                new ItemHotkey(ballLightningKey, namedItem(Items.NETHER_STAR, ballLightningName)),
                new ItemHotkey(slimeLumpKey, namedItem(Items.SLIME_BALL, slimeLumpName)),
                new ItemHotkey(turtleGripKey, namedItem(Items.TURTLE_SCUTE, turtleGripName)),
                new ItemHotkey(spiderFateKey, namedItem(Items.COBWEB, spiderFateName)),
                new ItemHotkey(stunKey, namedItem(Items.ENDER_EYE, stunName)),
                new ItemHotkey(magneticSphereKey, namedItem(Items.FIREWORK_STAR, magneticSphereName)),
                new ItemHotkey(explosiveTrapkaKey, namedItem(Items.PRISMARINE_SHARD, explosiveTrapkaName)),
                new ItemHotkey(explosiveThingKey, namedItem(Items.FIRE_CHARGE, explosiveThingName)),
                new ItemHotkey(starStunKey, namedItem(Items.NETHER_STAR, starStunName)),
                new ItemHotkey(snowLumpKey, namedItem(Items.SNOWBALL, snowLumpName))
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
            if (hotkey.bind.getKey() != key) {
                continue;
            }

            int slot = findMatchingHotbarSlot(mc, hotkey.matcher);
            if (slot != -1) {
                mc.player.getInventory().selectedSlot = slot;
            }
            return;
        }
    }

    private static BindSetting bindSetting(String name) {
        BindSetting setting = new BindSetting(name, KEY_DESCRIPTION);
        setting.setKey(GLFW.GLFW_KEY_UNKNOWN);
        setting.setFullWidth(true);
        return setting;
    }

    private static TextSetting nameSetting(String name, String value) {
        TextSetting setting = new TextSetting(name, NAME_DESCRIPTION);
        setting.setText(value);
        setting.setMax(48);
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

    private static Predicate<ItemStack> namedItem(Item item, TextSetting nameSetting) {
        return stack -> stack.isOf(item) && matchesDisplayName(stack, nameSetting.getText());
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
