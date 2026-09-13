package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.MultiSelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class LockSlot extends Module {
    private static final LockSlot INSTANCE = new LockSlot();

    public final SectionSetting generalSection = new SectionSetting("General");
    public final MultiSelectSetting lockedSlots = new MultiSelectSetting(
            "Locked Slots",
            "Pick which hotbar or offhand slots should be protected from dropping"
    ).value(
            "Slot 1",
            "Slot 2",
            "Slot 3",
            "Slot 4",
            "Slot 5",
            "Slot 6",
            "Slot 7",
            "Slot 8",
            "Slot 9",
            "Offhand"
    ).selected();

    private LockSlot() {
        super("lockslot", "Lock Slot", ModuleCategory.UTILITIES);
        lockedSlots.setFullWidth(true);
        setup(generalSection, lockedSlots);
    }

    public static LockSlot getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Prevent dropping items from chosen hotbar/offhand slots";
    }

    @Override
    public String getIcon() {
        return "lockslot.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public boolean isSlotLocked(int hotbarIndex) {
        if (!isEnabled()) {
            return false;
        }
        if (hotbarIndex == 40) {
            return lockedSlots.isSelected("Offhand");
        }
        if (hotbarIndex < 0 || hotbarIndex > 8) {
            return false;
        }
        return lockedSlots.isSelected("Slot " + (hotbarIndex + 1));
    }

    public boolean isHotbarSlotLocked(int hotbarIndex) {
        return isSlotLocked(hotbarIndex);
    }

    public boolean isOffhandLocked() {
        return isSlotLocked(40);
    }
}
