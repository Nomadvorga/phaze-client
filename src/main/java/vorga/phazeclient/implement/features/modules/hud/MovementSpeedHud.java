package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;

public final class MovementSpeedHud extends RectHudModule {
    private static final MovementSpeedHud INSTANCE = new MovementSpeedHud();

    public static MovementSpeedHud getInstance() {
        return INSTANCE;
    }

    public final BooleanSetting onlyUseGroundSpeed = new BooleanSetting("Only Use Ground Speed", "Only count horizontal speed").setValue(false);
    public final SelectSetting roundTo = new SelectSetting("Round To", "Decimal places for speed display")
            .value("Nearest", "1 Decimal", "2 Decimals", "3 Decimals")
            .selected("2 Decimals");
    public final SectionSetting otherSection = new SectionSetting("Other");
    /**
     * Swap the {@code Speed} label position. Default OFF preserves
     * the original {@code "1.23 m/s"} form; ON renders the labelled
     * variant {@code "Speed: 1.23 m/s"}.
     */
    public final BooleanSetting reverseOrder = new BooleanSetting("Reverse Order", "Add \"Speed:\" prefix instead of just \"X m/s\"").setValue(false);

    private MovementSpeedHud() {
        super("movement_speed_hud", "Movement Speed", 22.0f, 510.0f, 1.0f);
        onlyUseGroundSpeed.setFullWidth(true);
        roundTo.setFullWidth(true);
        reverseOrder.setFullWidth(true);
        setup(onlyUseGroundSpeed, roundTo, otherSection, reverseOrder);
    }

    @Override
    public String getDescription() {
        return "Shows current movement speed";
    }

    @Override
    public String getIcon() {
        return "movement_speed.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public String getSpeedText(double speed) {
        double displaySpeed = onlyUseGroundSpeed.isValue() ? Math.sqrt(speed * speed - getVerticalSpeed() * getVerticalSpeed()) : speed;
        String rounding = roundTo.getSelected();
        
        if (rounding.equals("Nearest")) {
            return String.format("%.0f", displaySpeed);
        } else if (rounding.equals("1 Decimal")) {
            return String.format("%.1f", displaySpeed);
        } else if (rounding.equals("2 Decimals")) {
            return String.format("%.2f", displaySpeed);
        } else {
            return String.format("%.3f", displaySpeed);
        }
    }

    private double getVerticalSpeed() {
        // This will be calculated in the mixin
        return 0.0;
    }
}
