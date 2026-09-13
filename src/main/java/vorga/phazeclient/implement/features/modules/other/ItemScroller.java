package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.base.util.ServerUtil;

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

    public long getDelayMs() {
        float v = delayMs.getValue();
        if (v < 5.0F) v = 5.0F;
        if (v > 50.0F) v = 50.0F;
        return (long) v;
    }

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
