package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.item.ThrowablePotionItem;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;

public class ShinyPots extends Module {
    private static final ShinyPots INSTANCE = new ShinyPots();

    public final BooleanSetting allPotions = new BooleanSetting("All Potions", "Enchanted glint on all potion bottles").setValue(true);
    public final BooleanSetting xpBottles = new BooleanSetting("XP Bottles", "Enchanted glint on bottles o' enchanting").setValue(true);
    public final BooleanSetting ominousBottles = new BooleanSetting("Ominous Bottles", "Enchanted glint on ominous bottles").setValue(true);

    private ShinyPots() {
        super("shiny_pots", "Shiny Pots", ModuleCategory.OTHER);
        setup(allPotions, xpBottles, ominousBottles);
    }

    public static ShinyPots getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Adds animated enchanted shine overlay to potion bottles and throwables";
    }

    @Override
    public String getIcon() {
        return "potion_hud.png";
    }

    public static boolean isPotion(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ShinyPots instance = getInstance();
        if (instance == null || !instance.isEnabled()) return false;

        Item item = stack.getItem();
        if (instance.allPotions.isValue()) {
            if (item instanceof PotionItem || item instanceof ThrowablePotionItem) return true;
            if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) return true;
            if (stack.contains(DataComponentTypes.POTION_CONTENTS)) return true;
        }

        if (instance.xpBottles.isValue() && item == Items.EXPERIENCE_BOTTLE) {
            return true;
        }

        if (instance.ominousBottles.isValue()) {
            if (item == Items.OMINOUS_BOTTLE || stack.contains(DataComponentTypes.OMINOUS_BOTTLE_AMPLIFIER)) {
                return true;
            }
        }

        return false;
    }
}
