package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.TextSetting;

/**
 * "Трапка" use-cooldown timer.
 *
 * <h3>What it does</h3>
 * The FunTime "трапка" ability puts itself on a fixed cooldown
 * after each use - 15 seconds for the regular variant, 20 for the
 * dragon one. The server doesn't broadcast that cooldown via the
 * standard {@code CooldownUpdateS2CPacket}, so vanilla's cooldown
 * pie doesn't show. This module starts a local timer the moment the
 * player right-clicks the trapka item and writes a chat hint when it
 * expires so the user knows the ability is ready again without
 * staring at the slot.
 *
 * <h3>Detection</h3>
 * On every {@code interactItem} (hooked from
 * {@link vorga.phazeclient.mixins.ClientPlayerInteractionManagerMixin})
 * we inspect the held stack:
 * <ol>
 *   <li>The item must be {@link Items#NETHERITE_SCRAP} (the FT
 *       трапка vanilla type).</li>
 *   <li>The display name must contain the user-configured substring
 *       (defaults to {@code "трапка"}, lowercased) so we don't
 *       trigger on a regular netherite scrap. Matching is
 *       case-insensitive.</li>
 * </ol>
 * When both pass, we stamp {@link System#currentTimeMillis} and
 * arm the timer for the configured duration.
 *
 * <h3>Why a millis stopwatch instead of a tick counter</h3>
 * Tick counts pause when the user opens a screen, but the FT cooldown
 * keeps ticking on the server. Using wall-clock milliseconds matches
 * the server-side authoritative timer so the local UX stays correct
 * even if the user opens chat / inventory mid-cooldown.
 *
 * <h3>Logic credits</h3>
 * Adapted from {@code winvi.moscow.soupbetter.modules.TrapTimerModule};
 * the Phaze port replaces the upstream's
 * {@code ConfigManager.setTrapTimerEnabled} state plumbing with the
 * standard module-setting backed values, and substitutes the
 * upstream's silent timer surface with an explicit chat hint when
 * the ability comes off cooldown.
 */
public final class TrapTimer extends Module {
    private static final TrapTimer INSTANCE = new TrapTimer();

    public enum TrapType {
        NORMAL("Обычная", 15_000L),
        DRAGON("Драконья", 20_000L);

        public final String displayName;
        public final long durationMs;

        TrapType(String displayName, long durationMs) {
            this.displayName = displayName;
            this.durationMs = durationMs;
        }
    }

    public final SectionSetting generalSection = new SectionSetting("General");

    public final SelectSetting trapType = new SelectSetting(
            "Trap Type",
            "Which trapka variant the timer measures (regular = 15s, dragon = 20s)"
    ).value("Normal", "Dragon").selected("Normal");

    public final TextSetting trapName = new TextSetting(
            "Trap Name",
            "Substring matched against the held item's display name when deciding to start the timer"
    ).setText("трапка").setMax(48);

    public final BooleanSetting chatNotify = new BooleanSetting(
            "Chat Notify",
            "Print a local chat message when the trap timer expires"
    ).setValue(true);

    private long timerStartMs = 0L;
    private boolean timerActive = false;
    private boolean expiredHandled = true;

    private TrapTimer() {
        super("trap_timer", "Trap Timer", ModuleCategory.UTILITIES);
        trapType.setFullWidth(true);
        trapName.setFullWidth(true);
        chatNotify.setFullWidth(true);
        setup(generalSection, trapType, trapName, chatNotify);
    }

    public static TrapTimer getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Local timer for the FunTime трапка / драконья трапка ability use cooldown";
    }

    @Override
    public String getIcon() {
        return "trap_timer.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    /**
     * Selected variant's wall-clock duration. The
     * {@link SelectSetting} stores the user-facing label, which the
     * upstream config also persisted as the enum name; we map both
     * variants here without lookup overhead since there are only two.
     */
    public TrapType getSelectedType() {
        return "Dragon".equalsIgnoreCase(trapType.getSelected()) ? TrapType.DRAGON : TrapType.NORMAL;
    }

    /**
     * Hook called from {@code ClientPlayerInteractionManagerMixin
     * .phaze$onInteractItem}. Filters the use to a trapka stack and
     * arms the timer when both the vanilla item type and the display
     * name match.
     */
    public void onItemUse(PlayerEntity player, Hand hand) {
        if (!isEnabled() || player == null || hand == null) {
            return;
        }
        ItemStack stack = player.getStackInHand(hand);
        if (stack == null || stack.isEmpty() || stack.getItem() != Items.NETHERITE_SCRAP) {
            return;
        }
        String needle = trapName.getText();
        if (needle == null || needle.isEmpty()) {
            return;
        }
        String itemName = stack.getName().getString().toLowerCase();
        if (!itemName.contains(needle.toLowerCase())) {
            return;
        }
        startTimer();
    }

    /**
     * Per-tick hook from {@code ClientPlayerEntityMixin}. Detects the
     * timer expiring this tick and emits the chat notification once.
     * The {@link #expiredHandled} latch prevents the message from
     * firing every tick after expiry.
     */
    public void tick() {
        if (!timerActive || !isEnabled()) {
            return;
        }
        long elapsed = System.currentTimeMillis() - timerStartMs;
        long duration = getSelectedType().durationMs;
        if (elapsed < duration) {
            return;
        }
        timerActive = false;
        if (expiredHandled) {
            return;
        }
        expiredHandled = true;

        if (!chatNotify.isValue()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.inGameHud == null || mc.inGameHud.getChatHud() == null) {
            return;
        }
        MutableText line = Text.literal("[Phaze] ").formatted(Formatting.GOLD)
                .append(Text.literal("Trap timer expired ").formatted(Formatting.WHITE))
                .append(Text.literal("(").formatted(Formatting.GRAY))
                .append(Text.literal(getSelectedType().displayName).formatted(Formatting.AQUA))
                .append(Text.literal(")").formatted(Formatting.GRAY));
        mc.inGameHud.getChatHud().addMessage(line);
    }

    /** Remaining seconds, rounded UP so the displayed countdown reads "1" right up to the moment of expiry. */
    public int getRemainingSeconds() {
        if (!timerActive) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - timerStartMs;
        long remainingMs = getSelectedType().durationMs - elapsed;
        return (int) Math.max(0L, (remainingMs + 999L) / 1000L);
    }

    /** {@code [0, 1]} progress through the timer; 0 = just started, 1 = expired. */
    public float getProgress() {
        if (!timerActive) {
            return 0.0F;
        }
        long elapsed = System.currentTimeMillis() - timerStartMs;
        long duration = getSelectedType().durationMs;
        return Math.min(1.0F, (float) elapsed / (float) duration);
    }

    public boolean isTimerActive() {
        return timerActive;
    }

    private void startTimer() {
        timerStartMs = System.currentTimeMillis();
        timerActive = true;
        expiredHandled = false;
    }

    @Override
    public void deactivate() {
        super.deactivate();
        timerActive = false;
        expiredHandled = true;
    }
}
