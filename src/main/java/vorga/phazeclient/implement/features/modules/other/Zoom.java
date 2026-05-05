/**
 * Zoom module - zoom functionality
 * Code from ok-boomer by glisco (MIT License)
 * Copyright (c) 2022 glisco
 * https://modrinth.com/mod/ok-boomer
 */

package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.util.Identifier;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.BindSetting;

public final class Zoom extends Module {
    private static final Zoom INSTANCE = new Zoom();
    
    private static boolean zoomActive = false;
    private static float currentZoomLevel = 7.5f;
    private static float savedZoomLevel = 7.5f;

    public final SectionSetting zoomSection = new SectionSetting("Zoom");
    public final BindSetting keybind = new BindSetting("Keybind", "Key to toggle zoom");
    public final BooleanSetting hold = new BooleanSetting("Hold", "Hold key to zoom (release to disable)").setValue(false);
    public final BooleanSetting resumeZoom = new BooleanSetting("Resume Zoom", "Resume zoom level after disabling").setValue(false);
    public final ValueSetting defaultZoom = new ValueSetting("Default Zoom", "Default zoom level").range(2.0f, 15.0f).step(0.5f).setValue(2.0f);
    public final BooleanSetting cinematicCamera = new BooleanSetting("Cinematic Camera", "Use cinematic camera when zooming").setValue(false);
    public final ValueSetting zoomInDuration = new ValueSetting("Zoom In Duration", "Duration of zoom in animation").range(0.1f, 2.0f).step(0.1f).setValue(0.5f);
    public final ValueSetting zoomOutDuration = new ValueSetting("Zoom Out Duration", "Duration of zoom out animation").range(0.1f, 2.0f).step(0.1f).setValue(0.5f);
    public final ValueSetting zoomScrollMultiplier = new ValueSetting("Scroll Multiplier", "Zoom scroll multiplier").range(1.0f, 5.0f).setValue(2.0f);
    public final ValueSetting zoomScrollSensitivity = new ValueSetting("Scroll Sensitivity", "Zoom scroll sensitivity").range(0.1f, 5.0f).setValue(1.0f);
    public final BooleanSetting showCurrentZoom = new BooleanSetting("Show Current Zoom", "Show current zoom level above hotbar").setValue(false);
    
    public final SectionSetting limitsSection = new SectionSetting("Limits");
    public final BooleanSetting enableLimits = new BooleanSetting("Enable Limits", "Enable zoom limits").setValue(true);
    public final ValueSetting maxZoom = new ValueSetting("Max Zoom", "Maximum zoom level").range(10, 5000).setValue(100).visible(() -> enableLimits.isValue());

    private Zoom() {
        super("zoom", "Zoom", ModuleCategory.UTILITIES);
        
        // Set full width for settings
        keybind.setFullWidth(true);
        hold.setFullWidth(true);
        cinematicCamera.setFullWidth(true);
        defaultZoom.setFullWidth(true);
        resumeZoom.setFullWidth(true);
        zoomInDuration.setFullWidth(true);
        zoomOutDuration.setFullWidth(true);
        zoomScrollMultiplier.setFullWidth(true);
        zoomScrollSensitivity.setFullWidth(true);
        showCurrentZoom.setFullWidth(true);
        enableLimits.setFullWidth(true);
        maxZoom.setFullWidth(true);
        
        setup(zoomSection, keybind, hold, cinematicCamera, defaultZoom, resumeZoom, zoomInDuration, zoomOutDuration, zoomScrollMultiplier, zoomScrollSensitivity, showCurrentZoom, limitsSection, enableLimits, maxZoom);
    }

    public static Zoom getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Zoom functionality";
    }

    @Override
    public String getIcon() {
        return "zoom.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    @Override
    public boolean isCanBind() {
        return false;
    }

    // Settings getters
    public float getCurrentZoomLevel() {
        return currentZoomLevel;
    }
    
    public void setCurrentZoomLevel(float level) {
        currentZoomLevel = level;
    }
    
    public boolean isHold() {
        return hold.isValue();
    }
    
    public boolean isCinematicCamera() {
        return cinematicCamera.isValue();
    }

    public boolean isShowCurrentZoom() {
        return showCurrentZoom.isValue();
    }

    public float getDefaultZoom() {
        return defaultZoom.getValue();
    }

    public boolean isResumeZoom() {
        return resumeZoom.isValue();
    }

    public float getZoomInDuration() {
        return zoomInDuration.getValue();
    }

    public float getZoomOutDuration() {
        return zoomOutDuration.getValue();
    }

    public float getZoomScrollMultiplier() {
        return zoomScrollMultiplier.getValue();
    }

    public float getZoomScrollSensitivity() {
        return zoomScrollSensitivity.getValue();
    }

    public boolean isEnableLimits() {
        return enableLimits.isValue();
    }

    public int getMaxZoom() {
        return maxZoom.getInt();
    }
    
    public static boolean isZoomActive() {
        return zoomActive;
    }
    
    public static void setZoomActive(boolean active) {
        zoomActive = active;
        
        // Resume Zoom logic
        if (!active && Zoom.getInstance().isResumeZoom()) {
            // Save zoom level when disabling
            savedZoomLevel = currentZoomLevel;
        } else if (active && Zoom.getInstance().isResumeZoom()) {
            // Restore zoom level when enabling
            currentZoomLevel = savedZoomLevel;
        } else if (active && !Zoom.getInstance().isResumeZoom()) {
            // Reset to default zoom when enabling without Resume Zoom
            currentZoomLevel = Zoom.getInstance().getDefaultZoom();
        }
    }
}
