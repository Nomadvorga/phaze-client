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
    public final BooleanSetting repeat = new BooleanSetting(
            "Repeat",
            "Replay the alert (sound and chat line if enabled) several more times after the initial trigger"
    ).setValue(false);
    /**
     * Number of <em>additional</em> alerts played after the initial
     * threshold trigger. Minimum 2 keeps the toggle meaningful - if
     * the user only wanted a single alert they would leave Repeat
     * off, so the slider only ever spans counts that make a Repeat
     * sequence worth scheduling. Hidden when {@link #repeat} is off.
     */
    public final ValueSetting repetitions = new ValueSetting(
            "Number of Repetitions",
            "How many extra times the alert is replayed after the initial trigger"
    ).range(2, 10).setValue(3).visible(repeat::isValue);
    /**
     * Seconds between successive repeats. Lower bound 3 sits below
     * the existing {@link #ALERT_COOLDOWN_MS} (4s) used for fresh
     * threshold crossings, but the repeat scheduler intentionally
     * bypasses that cooldown - the cooldown only debounces repeated
     * threshold crossings, not the user's own scheduled cadence.
     */
    public final ValueSetting repeatDelay = new ValueSetting(
            "Repeat Delay",
            "Seconds between successive repeated alerts"
    ).range(3, 30).setValue(5).visible(repeat::isValue);

    private final Map<EquipmentSlot, Boolean> wasBelow = new EnumMap<>(EquipmentSlot.class);
    private long lastAlertMs = 0L;
    /**
     * How many repeat alerts still need to fire. Decremented each
     * time the scheduler emits a repeat, zero means the sequence is
     * idle. Overwritten (not appended to) on every new threshold
     * crossing so the freshest alert wins when armor breaks again
     * mid-sequence.
     */
    private int pendingRepeats = 0;
    /** Wall-clock timestamp (ms) for the next pending repeat. */
    private long nextRepeatMs = 0L;
    /**
     * Snapshot of the slots that triggered the initial alert. The
     * scheduler resends the same chat lines on every repeat so the
     * user sees a coherent "Helmet almost broke!" reminder even if
     * a piece has been repaired or swapped in the meantime.
     */
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
        // A disabled module must NOT continue nagging the player - clear
        // any pending sequence so the next enable starts from a clean
        // slate. {@code lastAlertMs} is intentionally left alone so the
        // 4-second debounce still protects against rapid toggle spam.
        pendingRepeats = 0;
        pendingSlots.clear();
    }

    private void tick(MinecraftClient mc) {
        if (!isEnabled() || mc == null || mc.player == null) {
            return;
        }

        long now = System.currentTimeMillis();

        // Repeat scheduler runs before the threshold scan so a pending
        // repeat fires this tick even if no new threshold crossing
        // happens; this is the whole point of "Repeat" - it's an
        // ongoing nag independent of armor durability ticking down
        // again. Bypasses the ALERT_COOLDOWN_MS gate because that
        // cooldown is a debouncer for fresh threshold crossings, not
        // for the user's explicit repeat cadence.
        if (pendingRepeats > 0 && now >= nextRepeatMs) {
            playPing(mc);
            if (chatNotify.isValue()) {
                for (EquipmentSlot slot : pendingSlots) {
                    sendChatAlert(mc, slot);
                }
            }
            pendingRepeats--;
            // Round to ms to stay within long arithmetic. Slider step is
            // 0.1s so we always land on a clean multiple of 100ms.
            nextRepeatMs = now + (long) (repeatDelay.getValue() * 1000.0F);
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
            if (now - lastAlertMs >= ALERT_COOLDOWN_MS) {
                playPing(mc);
                if (chatNotify.isValue()) {
                    for (EquipmentSlot slot : newSlots) {
                        sendChatAlert(mc, slot);
                    }
                }
                lastAlertMs = now;
                // Arm the repeat sequence. We overwrite (rather than
                // append to) any in-flight sequence so a fresh threshold
                // crossing wins: if another piece breaks while the
                // previous repeat queue is still draining, the user
                // sees the alert reset around the newest event instead
                // of two staggered queues colliding. Snapshot the slot
                // list defensively because {@code newSlots} is a tick-
                // local builder we don't want to share by reference.
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
