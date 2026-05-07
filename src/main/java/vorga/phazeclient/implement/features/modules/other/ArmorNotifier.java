package vorga.phazeclient.implement.features.modules.other;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ArmorNotifier extends Module {
    private static final ArmorNotifier INSTANCE = new ArmorNotifier();
    private static final long ALERT_COOLDOWN_MS = 4000L;

    public final SectionSetting generalSection = new SectionSetting("General");
    public final ValueSetting threshold = new ValueSetting(
            "Threshold (%)",
            "Play an alert sound when any armor piece drops below this durability percentage"
    ).range(1, 50).setValue(5);
    public final BooleanSetting chatNotify = new BooleanSetting(
            "Show in Chat",
            "Also print '[Phaze] <piece> almost broke!' to chat (English) when an armor piece crosses the threshold"
    ).setValue(false);

    private final Map<EquipmentSlot, Boolean> wasBelow = new EnumMap<>(EquipmentSlot.class);
    private long lastAlertMs = 0L;

    private ArmorNotifier() {
        super("armor_notifier", "Armor Notifier", ModuleCategory.UTILITIES);
        threshold.setFullWidth(true);
        chatNotify.setFullWidth(true);
        setup(generalSection, threshold, chatNotify);

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    public static ArmorNotifier getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Plays a sound when any armor piece drops below a durability threshold";
    }

    @Override
    public String getIcon() {
        return "armor_notifier.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    @Override
    public void deactivate() {
        wasBelow.clear();
    }

    private void tick(MinecraftClient mc) {
        if (!isEnabled() || mc == null || mc.player == null) {
            return;
        }

        float pct = threshold.getValue() / 100.0F;
        // Track every slot that crossed the threshold THIS tick so the chat
        // notifier can emit one line per piece even when several break
        // simultaneously (e.g. an end-crystal hit damaging the whole set).
        List<EquipmentSlot> newSlots = null;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            // Only check actual armor slots; skip MAINHAND/OFFHAND/etc.
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue;
            }
            ItemStack stack = mc.player.getEquippedStack(slot);
            boolean below = isBelowThreshold(stack, pct);
            boolean previouslyBelow = wasBelow.getOrDefault(slot, false);
            if (below && !previouslyBelow) {
                if (newSlots == null) {
                    newSlots = new ArrayList<>(4);
                }
                newSlots.add(slot);
            }
            wasBelow.put(slot, below);
        }

        if (newSlots != null) {
            long now = System.currentTimeMillis();
            if (now - lastAlertMs >= ALERT_COOLDOWN_MS) {
                playPing(mc);
                if (chatNotify.isValue()) {
                    for (EquipmentSlot slot : newSlots) {
                        sendChatAlert(mc, slot);
                    }
                }
                lastAlertMs = now;
            }
        }
    }

    /**
     * Pushes a client-side chat line of the form
     * {@code [Phaze] <Piece> almost broke!}. Slot name lookup is hard-coded
     * to the four English armor labels so the message stays English
     * regardless of the player's language setting (per the explicit user
     * request). Routed through {@code ChatHud.addMessage} so it shares the
     * fade-in / collapse pipeline with every other chat message.
     */
    private static void sendChatAlert(MinecraftClient mc, EquipmentSlot slot) {
        if (mc.inGameHud == null) {
            return;
        }
        String pieceName = switch (slot) {
            case HEAD -> "Helmet";
            case CHEST -> "Chestplate";
            case LEGS -> "Leggings";
            case FEET -> "Boots";
            default -> "Armor";
        };
        MutableText prefix = Text.literal("[Phaze] ").formatted(Formatting.AQUA, Formatting.BOLD);
        MutableText body = Text.literal(pieceName + " almost broke!").formatted(Formatting.WHITE);
        mc.inGameHud.getChatHud().addMessage(prefix.append(body));
    }

    /**
     * True iff the stack is a damageable armor item whose remaining
     * durability fraction is strictly below {@code thresholdFraction}. Empty
     * or unbreakable items return false so they never trigger an alert.
     */
    private boolean isBelowThreshold(ItemStack stack, float thresholdFraction) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        int max = stack.getMaxDamage();
        if (max <= 0) {
            return false;
        }
        int remaining = max - stack.getDamage();
        float fraction = (float) remaining / (float) max;
        return fraction <= thresholdFraction;
    }

    private void playPing(MinecraftClient mc) {
        SoundManager soundManager = mc.getSoundManager();
        if (soundManager == null) {
            return;
        }
        SoundEvent sound = SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
        soundManager.play(PositionedSoundInstance.master(sound, 1.6F, 0.9F));
    }
}
