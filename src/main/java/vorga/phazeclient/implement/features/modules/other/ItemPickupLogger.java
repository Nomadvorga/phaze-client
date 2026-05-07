package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class ItemPickupLogger extends Module {
    private static final ItemPickupLogger INSTANCE = new ItemPickupLogger();

    public final SectionSetting filterSection = new SectionSetting("Filter");
    public final BooleanSetting onlyRare = new BooleanSetting(
            "Only Rare",
            "Log only items that are not COMMON rarity (uncommon/rare/epic)"
    ).setValue(false);

    private ItemPickupLogger() {
        super("item_pickup_logger", "Item Pickup Logger", ModuleCategory.UTILITIES);
        onlyRare.setFullWidth(true);
        setup(filterSection, onlyRare);
    }

    public static ItemPickupLogger getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Logs every picked-up item to the chat";
    }

    /**
     * Posts a client-side chat message describing the pickup. Called from the
     * mixin on {@code ClientPlayNetworkHandler.onItemPickupAnimation} after it
     * has confirmed the local player is the collector.
     */
    public void onPickup(ItemStack stack, int amount) {
        if (!isEnabled() || stack == null || stack.isEmpty()) {
            return;
        }

        if (onlyRare.isValue()) {
            Rarity rarity = stack.getRarity();
            if (rarity == null || rarity == Rarity.COMMON) {
                return;
            }
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.inGameHud == null) {
            return;
        }

        MutableText itemName = stack.getName().copy();
        Text message = Text.literal("[Pickup] ")
                .formatted(Formatting.GOLD)
                .append(itemName)
                .append(Text.literal(" x" + amount).formatted(Formatting.GRAY));
        mc.inGameHud.getChatHud().addMessage(message);
    }
}
