package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.entity.LivingEntity;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class ComboCounterHud extends RectHudModule {
    private static final ComboCounterHud INSTANCE = new ComboCounterHud();

    public static ComboCounterHud getInstance() {
        return INSTANCE;
    }

    public final SectionSetting otherSection = new SectionSetting("Other");

    public final BooleanSetting reverseOrder = new BooleanSetting("Reverse Order", "Show value before label, e.g. \"3 Combo\" instead of \"Combo 3\"").setValue(false);

    private int combo = 0;
    private LivingEntity lastTarget = null;
    private boolean wasHit = false;

    private ComboCounterHud() {
        super("combo_counter_hud", "Combo Counter", 100.0f, 50.0f, 1.0f);
        reverseOrder.setFullWidth(true);
        setup(otherSection, reverseOrder, cornerRounding);
    }

    public void onAttack(LivingEntity target) {
        if (lastTarget == null || !lastTarget.equals(target)) {
            combo = 0;
            lastTarget = target;
            wasHit = false;
        }
        combo++;
    }

    public void onHitByEnemy() {
        combo = 0;
        lastTarget = null;
        wasHit = true;
    }

    public void onWorldJoin() {

        combo = 0;
        lastTarget = null;
        wasHit = false;
    }

    public String getComboText() {
        if (combo == 0 && !wasHit) {
            return "No Combo";
        }

        return reverseOrder.isValue() ? combo + " Combo" : "Combo " + combo;
    }

    public int getCombo() {
        return combo;
    }

    @Override
    public void activate() {
        super.activate();
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }

    @Override
    public String getDescription() {
        return "Counts combo hits until enemy hits back";
    }

    @Override
    public String getIcon() {
        return "combo_counter_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
