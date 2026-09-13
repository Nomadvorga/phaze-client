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
import vorga.phazeclient.base.util.PhazeAnnouncements;

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
    public final BooleanSetting repeat = new BooleanSetting(
            "Repeat",
            "Replay the alert (sound and chat line if enabled) several more times after the initial trigger"
    ).setValue(false);

    public final ValueSetting repetitions = new ValueSetting(
            "Number of Repetitions",
            "How many extra times the alert is replayed after the initial trigger"
    ).range(2, 10).setValue(3).visible(repeat::isValue);

    public final ValueSetting repeatDelay = new ValueSetting(
            "Repeat Delay",
            "Seconds between successive repeated alerts"
    ).range(3, 30).setValue(5).visible(repeat::isValue);

    private final Map<EquipmentSlot, Boolean> wasBelow = new EnumMap<>(EquipmentSlot.class);

    private final Map<EquipmentSlot, Integer> lastRemaining = new EnumMap<>(EquipmentSlot.class);

    private final Map<EquipmentSlot, String> lastName = new EnumMap<>(EquipmentSlot.class);
    private long lastAlertMs = 0L;

    private int pendingRepeats = 0;

    private long nextRepeatMs = 0L;

    private final List<EquipmentSlot> pendingSlots = new ArrayList<>(4);

    private ArmorNotifier() {
        super("armor_notifier", "Armor Notifier", ModuleCategory.UTILITIES);
        threshold.setFullWidth(true);
        chatNotify.setFullWidth(true);
        repeat.setFullWidth(true);
        repetitions.setFullWidth(true);
        repeatDelay.setFullWidth(true);
        setup(generalSection, threshold, chatNotify, repeat, repetitions, repeatDelay);

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
        lastRemaining.clear();
        lastName.clear();

        pendingRepeats = 0;
        pendingSlots.clear();
    }

    private void tick(MinecraftClient mc) {
        if (!isEnabled() || mc == null || mc.player == null) {
            return;
        }

        if (mc.isPaused()) {
            return;
        }

        long now = System.currentTimeMillis();

        float pct = threshold.getValue() / 100.0F;
        List<EquipmentSlot> newSlots = null;
        List<EquipmentSlot> brokenSlots = null;

        for (EquipmentSlot slot : EquipmentSlot.values()) {

            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue;
            }
            ItemStack stack = mc.player.getEquippedStack(slot);
            int prevRem = lastRemaining.getOrDefault(slot, -1);
            boolean wasBelowFlag = wasBelow.getOrDefault(slot, false);
            boolean present = stack != null && !stack.isEmpty() && stack.getMaxDamage() > 0;

            if (present) {
                int max = stack.getMaxDamage();
                int rem = max - stack.getDamage();
                float fraction = (float) rem / (float) max;
                boolean below = fraction <= pct;
                if (below && !wasBelowFlag) {
                    if (newSlots == null) {
                        newSlots = new ArrayList<>(4);
                    }
                    newSlots.add(slot);
                }
                wasBelow.put(slot, below);
                lastRemaining.put(slot, rem);

                lastName.put(slot, stack.getName().getString());
            } else {

                if (prevRem > 0 && wasBelowFlag) {
                    if (brokenSlots == null) {
                        brokenSlots = new ArrayList<>(4);
                    }
                    brokenSlots.add(slot);
                }
                wasBelow.put(slot, false);
                lastRemaining.put(slot, -1);

            }
        }

        if (brokenSlots != null) {
            playBreakSound(mc);
            if (chatNotify.isValue()) {
                for (EquipmentSlot slot : brokenSlots) {
                    String name = lastName.getOrDefault(slot, fallbackName(slot));
                    sendBrokeMessage(mc, name);
                }
            }
            pendingSlots.removeAll(brokenSlots);
            if (pendingSlots.isEmpty()) {
                pendingRepeats = 0;
            }
        }

        if (pendingRepeats > 0) {
            pendingSlots.removeIf(s -> {
                ItemStack st = mc.player.getEquippedStack(s);
                return st == null || st.isEmpty() || st.getMaxDamage() <= 0;
            });
            if (pendingSlots.isEmpty()) {
                pendingRepeats = 0;
            }
        }

        if (pendingRepeats > 0 && now >= nextRepeatMs) {
            playPing(mc);
            if (chatNotify.isValue()) {
                for (EquipmentSlot slot : pendingSlots) {
                    ItemStack st = mc.player.getEquippedStack(slot);
                    String name = (st != null && !st.isEmpty())
                            ? st.getName().getString()
                            : lastName.getOrDefault(slot, fallbackName(slot));
                    sendAlmostBrokeMessage(mc, name);
                }
            }
            pendingRepeats--;

            nextRepeatMs = now + (long) (repeatDelay.getValue() * 1000.0F);
        }

        if (newSlots != null) {
            if (now - lastAlertMs >= ALERT_COOLDOWN_MS) {
                playPing(mc);
                if (chatNotify.isValue()) {
                    for (EquipmentSlot slot : newSlots) {
                        ItemStack st = mc.player.getEquippedStack(slot);
                        String name = (st != null && !st.isEmpty())
                                ? st.getName().getString()
                                : fallbackName(slot);
                        sendAlmostBrokeMessage(mc, name);
                    }
                }
                lastAlertMs = now;

                if (repeat.isValue()) {
                    pendingRepeats = repetitions.getInt();
                    nextRepeatMs = now + (long) (repeatDelay.getValue() * 1000.0F);
                    pendingSlots.clear();
                    pendingSlots.addAll(newSlots);
                } else {
                    pendingRepeats = 0;
                    pendingSlots.clear();
                }
            }
        }
    }

    private static String fallbackName(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "Helmet";
            case CHEST -> "Chestplate";
            case LEGS -> "Leggings";
            case FEET -> "Boots";
            default -> "Armor";
        };
    }

    private static void sendAlmostBrokeMessage(MinecraftClient mc, String pieceName) {
        if (mc.inGameHud == null) {
            return;
        }
        MutableText body = Text.literal(pieceName + " almost broke!").formatted(Formatting.WHITE);
        mc.inGameHud.getChatHud().addMessage(PhazeAnnouncements.systemMessage(body));
    }

    private static void sendBrokeMessage(MinecraftClient mc, String pieceName) {
        if (mc.inGameHud == null) {
            return;
        }
        MutableText body = Text.literal(pieceName + " broke!").formatted(Formatting.RED);
        mc.inGameHud.getChatHud().addMessage(PhazeAnnouncements.systemMessage(body));
    }

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

        soundManager.play(PositionedSoundInstance.ui(sound, 1.6F, 0.9F));
    }

    private void playBreakSound(MinecraftClient mc) {
        SoundManager soundManager = mc.getSoundManager();
        if (soundManager == null) {
            return;
        }

        SoundEvent sound = SoundEvents.ENTITY_ITEM_BREAK.value();

        soundManager.play(PositionedSoundInstance.ui(sound, 1.0F, 1.0F));
    }
}
