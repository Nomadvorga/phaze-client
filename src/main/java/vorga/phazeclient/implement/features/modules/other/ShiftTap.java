package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.*;
import vorga.phazeclient.base.util.ServerUtil;

public final class ShiftTap extends Module {
    private static final ShiftTap INSTANCE = new ShiftTap();
    private static final int MIN_DURATION = 10;
    private static final int MAX_DURATION = 200;

    // Settings
    public final SectionSetting generalSection = new SectionSetting("General");
    public final ValueSetting sneakDuration = new ValueSetting("Sneak Duration", "Duration of sneak in milliseconds")
            .range(MIN_DURATION, MAX_DURATION)
            .setValue(50);

    // Runtime state
    private long shiftTapEndTime = 0L;
    private boolean moduleControllingSneak = false;

    private ShiftTap() {
        super("shifttap", "Shift Tap", ModuleCategory.UTILITIES);
        
        sneakDuration.setFullWidth(true);
        setup(generalSection, sneakDuration);
    }

    public static ShiftTap getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Automatically taps shift for short duration";
    }

    @Override
    public String getIcon() {
        return "shifttap.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    @Override
    public boolean isServerAllowed() {
        return ServerUtil.isShiftTapSupported();
    }

    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        
        if (!isEnabled()) {
            stopShiftTap();
            return;
        }

        if (mc.player == null || mc.player.isSpectator() || !isWorldSupported()) {
            stopShiftTap();
            return;
        }

        if (moduleControllingSneak && System.currentTimeMillis() >= shiftTapEndTime) {
            stopShiftTap();
        }
    }

    public void triggerShiftTap() {
        MinecraftClient mc = MinecraftClient.getInstance();
        
        if (!isEnabled() || mc.player == null || !isWorldSupported()) {
            return;
        }

        shiftTapEndTime = System.currentTimeMillis() + sneakDuration.getInt();
        if (!moduleControllingSneak) {
            mc.options.sneakKey.setPressed(true);
            moduleControllingSneak = true;
        }
    }

    public void stopShiftTap() {
        MinecraftClient mc = MinecraftClient.getInstance();
        
        if (!moduleControllingSneak) {
            return;
        }

        mc.options.sneakKey.setPressed(false);
        moduleControllingSneak = false;
        shiftTapEndTime = 0L;
    }

    private boolean isWorldSupported() {
        return ServerUtil.isShiftTapSupported();
    }

    protected void onDisable() {
        stopShiftTap();
    }
}
