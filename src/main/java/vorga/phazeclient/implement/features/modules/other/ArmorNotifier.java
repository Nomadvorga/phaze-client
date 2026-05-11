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
    /**
     * Remaining durability (max - damage) for the damageable stack we
     * saw in this slot last tick. {@code -1} means the slot did not
     * contain a damageable stack last tick (empty / non-armor / not
     * yet observed). Used to detect a break transition: if the slot
     * had a damageable stack last tick AND that stack was already
     * inside the user-configured "almost broken" band, and the slot
     * is now empty, we treat that as a break event. Manual unequip
     * from a sub-threshold state shares the same signature and is an
     * accepted (very rare) false positive.
     */
    private final Map<EquipmentSlot, Integer> lastRemaining = new EnumMap<>(EquipmentSlot.class);
    /**
     * Last-seen display name (custom anvil name or vanilla localized
     * label) for the damageable stack in this slot. Retained past the
     * break so the {@code <name> broke!} chat line can still identify
     * the piece even though the {@link ItemStack} is already gone.
     */
    private final Map<EquipmentSlot, String> lastName = new EnumMap<>(EquipmentSlot.class);
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
     * user sees a coherent "Helmet almost broke!" reminder even if a
     * piece has been repaired or swapped in the meantime. Slots are
     * pruned from this list as soon as their stack disappears (break
     * or manual unequip) so the repeat sequence stops nagging about
     * armor that is no longer worn - matching the user requirement
     * "if it's broken, do not repeat anymore".
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
        lastRemaining.clear();
        lastName.clear();
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
        // Singleplayer pause freezes the world: durability cannot tick
        // down, sound + chat would queue against a static scene, and
        // the user explicitly does not want reminders while the game
        // is not "moving". {@link MinecraftClient#isPaused()} returns
        // true only for the integrated-server pause; multiplayer pause
        // screens leave the client running and continue to nag, which
        // is correct because the server is still ticking.
        if (mc.isPaused()) {
            return;
        }

        long now = System.currentTimeMillis();

        // Walk every armor slot ONCE. Both transitions live in the same
        // pass: stack now empty when it wasn't (break), and durability
        // fraction crossing the threshold downwards (almost broken).
        // Doing it in one pass keeps {@code lastRemaining}/{@code wasBelow}
        // mutations atomic per slot.
        float pct = threshold.getValue() / 100.0F;
        List<EquipmentSlot> newSlots = null;
        List<EquipmentSlot> brokenSlots = null;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            // Only check actual armor slots; skip MAINHAND/OFFHAND/etc.
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
                // Refresh the cached display name every tick so anvil
                // renames applied mid-game propagate to the next break
                // alert. {@code getString} strips formatting codes; we
                // re-apply our own colour around the body when sending.
                lastName.put(slot, stack.getName().getString());
            } else {
                // Stack disappeared. If we had a damageable stack last
                // tick AND it was already inside the user's "almost
                // broken" band, the only natural outcome is vanilla
                // destroying it from damage - we treat that as a break.
                if (prevRem > 0 && wasBelowFlag) {
                    if (brokenSlots == null) {
                        brokenSlots = new ArrayList<>(4);
                    }
                    brokenSlots.add(slot);
                }
                wasBelow.put(slot, false);
                lastRemaining.put(slot, -1);
                // {@code lastName} is intentionally NOT cleared - the
                // break handler below consumes it after the empty
                // transition, and a stale entry is harmless once a
                // fresh damageable item lands in the slot (the present
                // branch above overwrites it on the very next tick).
            }
        }

        // Handle breaks FIRST. A break is a one-shot event with its own
        // (different) sound and "broke!" chat line; it also cancels any
        // pending repeat sequence that referenced this slot - "do not
        // repeat once it's broken" was an explicit user request.
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

        // Filter out any other slot in {@code pendingSlots} whose stack
        // vanished without reaching the break heuristic (e.g. player
        // unequipped intact-but-low armor). Nag silence on disappearance
        // matches the user's intent: don't keep playing the alert for
        // armor that is no longer worn.
        if (pendingRepeats > 0) {
            pendingSlots.removeIf(s -> {
                ItemStack st = mc.player.getEquippedStack(s);
                return st == null || st.isEmpty() || st.getMaxDamage() <= 0;
            });
            if (pendingSlots.isEmpty()) {
                pendingRepeats = 0;
            }
        }

        // Repeat scheduler runs AFTER the break filter so a piece that
        // broke this tick has already been pruned from {@code pendingSlots}
        // and cannot trigger one last redundant ping. Bypasses the
        // ALERT_COOLDOWN_MS gate because that cooldown only debounces
        // fresh threshold crossings, not the user's explicit cadence.
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
            // Slider step is 0.1s so this always lands on a clean
            // multiple of 100ms when cast to {@code long}.
            nextRepeatMs = now + (long) (repeatDelay.getValue() * 1000.0F);
        }

        // Fresh threshold-crossing alerts last so they can arm a new
        // repeat sequence on top of any cleanup performed earlier in
        // the tick.
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
                // Arm the repeat sequence. Overwrite (rather than append
                // to) any in-flight sequence so a fresh threshold
                // crossing wins: if another piece crosses the threshold
                // while the previous queue is still draining, the user
                // sees the alert reset around the newest event instead
                // of two staggered queues colliding. Snapshot
                // {@code newSlots} defensively because it is a tick-
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
     * Hard-coded English fallback used only when we cannot recover the
     * stack's actual display name (slot was empty before we ever
     * observed it). The hot paths funnel renamed / localized stack
     * names through {@link ItemStack#getName()} so the chat line
     * respects custom anvil names and the player's current locale -
     * the user explicitly asked for that; this fallback is a last
     * resort label.
     */
    private static String fallbackName(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "Helmet";
            case CHEST -> "Chestplate";
            case LEGS -> "Leggings";
            case FEET -> "Boots";
            default -> "Armor";
        };
    }

    /**
     * Pushes a client-side chat line of the form
     * {@code [Phaze] <name> almost broke!}. {@code name} is the stack's
     * actual {@link ItemStack#getName() display name}, which already
     * respects anvil renames and the active game locale. The verb is
     * kept English ("almost broke!") to match the existing client log
     * style. Routed through {@code ChatHud.addMessage} so it shares
     * the fade-in / collapse pipeline with every other chat message.
     */
    private static void sendAlmostBrokeMessage(MinecraftClient mc, String pieceName) {
        if (mc.inGameHud == null) {
            return;
        }
        MutableText prefix = Text.literal("[Phaze] ").formatted(Formatting.AQUA, Formatting.BOLD);
        MutableText body = Text.literal(pieceName + " almost broke!").formatted(Formatting.WHITE);
        mc.inGameHud.getChatHud().addMessage(prefix.append(body));
    }

    /**
     * Pushes a client-side chat line of the form
     * {@code [Phaze] <name> broke!} coloured red, fired exactly once
     * when vanilla destroys the armor stack. Mirrors
     * {@link #sendAlmostBrokeMessage} for naming rules (localized /
     * renamed name + English verb), red to emphasise the piece is
     * actually gone now.
     */
    private static void sendBrokeMessage(MinecraftClient mc, String pieceName) {
        if (mc.inGameHud == null) {
            return;
        }
        MutableText prefix = Text.literal("[Phaze] ").formatted(Formatting.AQUA, Formatting.BOLD);
        MutableText body = Text.literal(pieceName + " broke!").formatted(Formatting.RED);
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

    /**
     * Distinct cue for an actual break - vanilla's
     * {@link SoundEvents#ENTITY_ITEM_BREAK item break} sound at
     * pitch 1.0 + volume 1.0 so the audio language matches the
     * in-game event the player is being notified about and cuts
     * through the usual sound bed. Intentionally a different sound
     * profile from the threshold "pling" per explicit user request.
     */
    private void playBreakSound(MinecraftClient mc) {
        SoundManager soundManager = mc.getSoundManager();
        if (soundManager == null) {
            return;
        }
        // {@code ENTITY_ITEM_BREAK} is one of the SoundEvents fields
        // exposed as a bare {@code SoundEvent} (no RegistryEntry wrap),
        // unlike {@code BLOCK_NOTE_BLOCK_PLING} above which needs
        // {@code .value()}. The asymmetry is upstream - yarn mirrors it.
        SoundEvent sound = SoundEvents.ENTITY_ITEM_BREAK;
        soundManager.play(PositionedSoundInstance.master(sound, 1.0F, 1.0F));
    }
}
