package vorga.phazeclient.implement.features.modules.other;

import com.google.gson.JsonObject;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.base.util.Lang;

import java.util.List;

/** Standalone settings-contract checks; no saved configuration or game world is needed. */
public final class LowHealthSettingsChecks {
    private static int checks;

    public static void main(String[] args) {
        checks = 0;
        LowHealthWarning warning = LowHealthWarning.getInstance();
        String previousLanguage = Lang.getActive();
        boolean previousHeartbeat = warning.heartbeatSound.isValue();
        float previousVolume = warning.soundVolume.getValue();
        int previousColor = warning.color.getColor();
        try {
            check(warning.getIdentifier().equals("low_health_warning"), "module ID remains compatible with configs");
            check(warning.getVisibleName().equals("Low HP Warning"), "canonical module title unchanged");
            check(warning.settings().equals(orderedSettings(warning)), "General, Audio, Color sections and controls have the intended order");
            checkValue(warning.threshold, "Threshold (HP)", 2, 14, 1, 6);
            check(warning.settings().stream().noneMatch(s -> s.getRawName().equals("Intensity")),
                    "intensity is no longer a separate setting");
            checkValue(warning.pulseSpeed, "Pulse Speed", 1, 5, 0.5f, 1);
            checkValue(warning.soundVolume, "Sound Volume", 10, 100, 5, 75);
            check(warning.soundVolume.isInteger(), "volume retains integer slider semantics");
            check(warning.heartbeatSound.getRawName().equals("Heartbeat Sound"), "heartbeat config key unchanged");
            check(Boolean.TRUE.equals(warning.heartbeatSound.getDefaultValue()), "heartbeat defaults to enabled");
            check(warning.color.getRawName().equals("Color"), "color config key unchanged");
            check(Integer.valueOf(0xD9FF1111).equals(warning.color.getDefaultColor()), "default tint includes previous .85 intensity");
            check(!warning.color.isNoAlpha(), "color picker exposes opacity as intensity");
            int tintRgb = warning.color.getColor() & 0xFFFFFF;
            for (float alpha : new float[]{0, 0.25F, 0.5F, 0.85F, 1}) {
                warning.color.setAlpha(alpha);
                check((warning.color.getColor() >>> 24) == Math.round(alpha * 255), "picker alpha controls intensity");
                check((warning.color.getColor() & 0xFFFFFF) == tintRgb, "opacity does not alter tint");
            }
            migrationChecks();

            for (String locale : List.of(Lang.EN, Lang.RU, Lang.EN)) {
                Lang.setActive(locale);
                boolean russian = locale.equals(Lang.RU);
                checkSection(warning.generalSection, "General", russian ? "Общие" : "General");
                checkSection(warning.soundSection, "Audio", russian ? "Звук" : "Audio");
                checkSection(warning.colorSection, "Color", russian ? "Цвет" : "Color");
                check(warning.getIdentifier().equals("low_health_warning"), "language switch preserves module ID");
                check(warning.settings().equals(orderedSettings(warning)), "language switch preserves settings identity/order");
                check(warning.threshold.getRawName().equals("Threshold (HP)")
                                && warning.pulseSpeed.getRawName().equals("Pulse Speed")
                                && warning.heartbeatSound.getRawName().equals("Heartbeat Sound")
                                && warning.soundVolume.getRawName().equals("Sound Volume")
                                && warning.color.getRawName().equals("Color"),
                        "language switch preserves every stored English key");
            }

            warning.soundVolume.setValue(35);
            for (boolean heartbeat : new boolean[]{true, false, true, false, true}) {
                warning.heartbeatSound.setValue(heartbeat);
                checkVisibility(warning, heartbeat);
                check(warning.soundVolume.getValue() == 35, "hiding/revealing volume preserves its configured value");
            }
            warning.heartbeatSound.setValue(false);
            warning.heartbeatSound.reset();
            check(warning.heartbeatSound.isValue(), "reset restores the default heartbeat state");
            checkVisibility(warning, true);
        } finally {
            warning.heartbeatSound.setValue(previousHeartbeat);
            warning.soundVolume.setValue(previousVolume);
            warning.color.setColor(previousColor);
            Lang.setActive(previousLanguage);
        }
        System.out.println("Low health settings checks passed: " + checks);
    }

    static List<Setting> orderedSettings(LowHealthWarning warning) {
        return List.of(warning.generalSection, warning.threshold, warning.pulseSpeed,
                warning.soundSection, warning.heartbeatSound, warning.soundVolume, warning.colorSection, warning.color);
    }

    private static void migrationChecks() {
        for (int color : new int[]{0xFFFF1111, 0x8000FFCC, 0x000011FF}) {
            for (float opacity : new float[]{0, 0.2F, 0.85F, 1, -1, 2}) {
                JsonObject old = new JsonObject();
                old.addProperty("Color", color);
                old.addProperty("Intensity", opacity);
                old.addProperty("Pulse Speed", 2.5F);
                JsonObject migrated = LowHealthWarning.migrateLegacySettings(old);
                int result = migrated.get("Color").getAsInt();
                check((result & 0xFFFFFF) == (color & 0xFFFFFF), "migration preserves RGB");
                check((result >>> 24) == Math.round((color >>> 24) * Math.max(0, Math.min(1, opacity))),
                        "migration combines legacy slider and existing color alpha exactly once");
                check(!migrated.has("Intensity") && migrated.get("Pulse Speed").getAsFloat() == 2.5F,
                        "migration removes obsolete key and keeps other settings");
                check(old.has("Intensity") && old.get("Color").getAsInt() == color, "migration never mutates input");
                check(LowHealthWarning.migrateLegacySettings(migrated) == migrated, "new configs are a no-op without allocations");
                check(LowHealthWarning.migrateLegacySettings(old).equals(migrated), "reloading legacy input is stable");
            }
        }
        JsonObject missingColor = new JsonObject();
        missingColor.addProperty("Intensity", 0.5F);
        check(LowHealthWarning.migrateLegacySettings(missingColor).get("Color").getAsInt() == 0x80FF1111,
                "missing old color uses the old opaque default before applying intensity");
        JsonObject corrupt = new JsonObject();
        corrupt.addProperty("Intensity", "bad");
        corrupt.addProperty("Color", 0xFF123456);
        check(LowHealthWarning.migrateLegacySettings(corrupt).get("Color").getAsInt() == 0xFF123456,
                "bad old opacity cannot discard valid tint");
    }

    private static void checkValue(ValueSetting setting, String key, float min, float max, float step, float value) {
        check(setting.getRawName().equals(key), key + ": raw config key unchanged");
        check(setting.getMin() == min && setting.getMax() == max && setting.getStep() == step
                        && Float.valueOf(value).equals(setting.getDefaultValue()),
                key + ": range, step and default unchanged");
    }

    private static void checkSection(SectionSetting section, String raw, String display) {
        check(section.getRawName().equals(raw), raw + ": canonical section key");
        check(section.getName().equals(display), raw + ": localized label is " + display);
        check(!section.isSaveToConfig(), raw + ": decorative section is not persisted as a setting");
    }

    private static void checkVisibility(LowHealthWarning warning, boolean heartbeat) {
        for (Setting setting : orderedSettings(warning)) {
            check(setting.isFullWidth(), setting.getRawName() + ": occupies a full panel row");
            check(setting.isVisible() == (setting != warning.soundVolume || heartbeat),
                    setting.getRawName() + ": visibility follows heartbeat only for volume (heartbeat=" + heartbeat + ")");
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
