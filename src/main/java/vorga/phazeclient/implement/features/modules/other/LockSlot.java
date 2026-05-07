package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

/**
 * Locks selected hotbar slots and/or offhand from being dropped or thrown.
 * Useful on PvP servers where pressing Q on a totem/sphere is fatal.
 */
public final class LockSlot extends Module {
    private static final LockSlot INSTANCE = new LockSlot();

    public final SectionSetting generalSection = new SectionSetting("General");
    public final BooleanSetting slot1 = new BooleanSetting("Slot 1", "Lock hotbar slot 1").setValue(false);
    public final BooleanSetting slot2 = new BooleanSetting("Slot 2", "Lock hotbar slot 2").setValue(false);
    public final BooleanSetting slot3 = new BooleanSetting("Slot 3", "Lock hotbar slot 3").setValue(false);
    public final BooleanSetting slot4 = new BooleanSetting("Slot 4", "Lock hotbar slot 4").setValue(false);
    public final BooleanSetting slot5 = new BooleanSetting("Slot 5", "Lock hotbar slot 5").setValue(false);
    public final BooleanSetting slot6 = new BooleanSetting("Slot 6", "Lock hotbar slot 6").setValue(false);
    public final BooleanSetting slot7 = new BooleanSetting("Slot 7", "Lock hotbar slot 7").setValue(false);
    public final BooleanSetting slot8 = new BooleanSetting("Slot 8", "Lock hotbar slot 8").setValue(false);
    public final BooleanSetting slot9 = new BooleanSetting("Slot 9", "Lock hotbar slot 9").setValue(false);
    public final BooleanSetting offhand = new BooleanSetting("Offhand", "Lock the offhand slot").setValue(false);

    private final BooleanSetting[] hotbarSlots = {slot1, slot2, slot3, slot4, slot5, slot6, slot7, slot8, slot9};

    private LockSlot() {
        super("lockslot", "Lock Slot", ModuleCategory.UTILITIES);
        setup(generalSection, slot1, slot2, slot3, slot4, slot5, slot6, slot7, slot8, slot9, offhand);
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

    /**
     * Whether the given inventory selection index is locked.
     * @param hotbarIndex 0-8 for hotbar, 40 for offhand.
     */
    public boolean isSlotLocked(int hotbarIndex) {
        if (!isEnabled()) {
            return false;
        }
        if (hotbarIndex == 40) {
            return offhand.isValue();
        }
        if (hotbarIndex < 0 || hotbarIndex > 8) {
            return false;
        }
        return hotbarSlots[hotbarIndex].isValue();
    }

    /**
     * Convenience for hotbar slot indices 0..8.
     */
    public boolean isHotbarSlotLocked(int hotbarIndex) {
        return isSlotLocked(hotbarIndex);
    }

    /**
     * Whether the offhand is locked.
     */
    public boolean isOffhandLocked() {
        return isSlotLocked(40);
    }
}
