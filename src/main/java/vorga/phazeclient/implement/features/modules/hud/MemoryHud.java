package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;

public final class MemoryHud extends RectHudModule {
    private static final MemoryHud INSTANCE = new MemoryHud();

    public static MemoryHud getInstance() {
        return INSTANCE;
    }

    public final SelectSetting displayMode = new SelectSetting("Display Mode", "Memory display format")
            .value("Percentage", "Megabytes", "Gigabytes");
    public final BooleanSetting colorBasedOnUsage = new BooleanSetting("Color Based On Usage", "Change color based on memory usage").setValue(true);

    private MemoryHud() {
        super("memory_hud", "Memory HUD", 100.0f, 50.0f, 1.0f);
        setup(displayMode, colorBasedOnUsage);
    }

    public String getMemoryText() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        String mode = displayMode.getSelected();
        
        if (mode.equals("Percentage")) {
            double percentage = (usedMemory * 100.0) / maxMemory;
            return String.format("Mem: %.1f%%", percentage);
        } else if (mode.equals("Megabytes")) {
            return String.format("Mem: %d/%d MB", usedMemory / (1024 * 1024), maxMemory / (1024 * 1024));
        } else {
            return String.format("Mem: %.2f/%.2f GB", usedMemory / (1024.0 * 1024 * 1024), maxMemory / (1024.0 * 1024 * 1024));
        }
    }

    public int getMemoryColor() {
        if (!colorBasedOnUsage.isValue()) {
            return 0xFFFFFFFF;
        }

        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        double percentage = (usedMemory * 100.0) / maxMemory;

        if (percentage < 50) {
            return 0xFF00FF00; // Green
        } else if (percentage < 75) {
            return 0xFFFFFF00; // Yellow
        } else {
            return 0xFFFF0000; // Red
        }
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
        return "Shows RAM usage in HUD";
    }

    @Override
    public String getIcon() {
        return "memory_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
