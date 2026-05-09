package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.base.util.ServerUtil;

/**
 * Item Scroller. Auto-shift-clicks slots while the user holds Shift+LMB
 * and drags the cursor across an inventory / chest / crafting screen.
 *
 * <p>The actual hooking lives in
 * {@link vorga.phazeclient.mixins.HandledScreenItemScrollerMixin}; this
 * class is just the user-facing settings carrier and singleton holder
 * the mixin queries each tick to decide whether to fire and how long
 * to throttle between transfers.
 *
 * <p>The {@link #delayMs} slider throttles consecutive transfers so
 * fast cursor sweeps don't queue dozens of QUICK_MOVE packets in a
 * single client tick - servers usually rate-limit those and would drop
 * the trailing ones. The 5..50 ms range matches what feels responsive
 * (5 = instant) without spamming the server (50 = ~20 transfers/s).
 */
public final class ItemScroller extends Module {
    private static final ItemScroller INSTANCE = new ItemScroller();

    public final SectionSetting generalSection = new SectionSetting("General");

    public final ValueSetting delayMs = new ValueSetting(
            "Delay (ms)",
            "Minimum time between two consecutive shift-click transfers while dragging. Lower = snappier, higher = gentler on the server."
    ).range(5, 50).step(1).setValue(20);

    private ItemScroller() {
        super("item_scroller", "Item Scroller", ModuleCategory.UTILITIES);
        delayMs.setFullWidth(true);
        setup(generalSection, delayMs);
    }

    public static ItemScroller getInstance() {
        return INSTANCE;
    }

    /** Configured throttle, never below 5 ms. */
    public long getDelayMs() {
        float v = delayMs.getValue();
        if (v < 5.0F) v = 5.0F;
        if (v > 50.0F) v = 50.0F;
        return (long) v;
    }

    /**
     * Server-whitelist hook used by the framework. When this returns
     * false the menu paints a gray "LOCKED" badge on the module's
     * state row, ignores toggle clicks, and the per-tick enforcer in
     * {@link vorga.phazeclient.mixins.ClientPlayerEntityMixin} flips
     * any active toggle off the next tick - matching exactly how
     * AutoSwap / AutoPotion / ShiftTap / MouseClicker / ElytraUtility
     * are gated on their own server lists.
     */
    @Override
    public boolean isServerAllowed() {
        return ServerUtil.isItemScrollerSupported();
    }

    @Override
    public String getDescription() {
        return "Hold Shift + drag the mouse over slots to shift-click them all without extra clicks";
    }

    @Override
    public String getIcon() {
        return "item_scroller.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
