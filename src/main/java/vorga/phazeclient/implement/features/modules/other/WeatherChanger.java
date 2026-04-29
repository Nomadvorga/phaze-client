package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public final class WeatherChanger extends Module {
    private static final WeatherChanger INSTANCE = new WeatherChanger();
    private float prevRainGradient = 0.0f;
    private float prevThunderGradient = 0.0f;

    public static WeatherChanger getInstance() {
        return INSTANCE;
    }

    public final SelectSetting weatherType = new SelectSetting("Weather Type", "Select the type of weather")
            .value("Clear", "Rain", "Thunder", "Snow")
            .selected("Clear");
    public final ValueSetting rainDensity = new ValueSetting("Rain Density", "Set the density of rain (0-100%)");
    public final ValueSetting thunderStrength = new ValueSetting("Thunder Strength", "Set the strength of thunder (0-100%)");
    public final ValueSetting snowDensity = new ValueSetting("Snow Density", "Set the density of snow (0-100%)");

    private WeatherChanger() {
        super("weather_changer", "Weather Changer", ModuleCategory.VISUALS, true, false);
        setSecondaryCategory(ModuleCategory.OTHER);
        rainDensity.range(0, 100).setValue(100).visible(() -> weatherType.isSelected("Rain"));
        thunderStrength.range(0, 100).setValue(100).visible(() -> weatherType.isSelected("Thunder"));
        snowDensity.range(0, 100).setValue(100).visible(() -> weatherType.isSelected("Snow"));
        setup(weatherType, rainDensity, thunderStrength, snowDensity);
    }

    @Override
    public void deactivate() {
        prevRainGradient = 0.0f;
        prevThunderGradient = 0.0f;
    }

    public boolean isWeatherOverrideActive() {
        return isEnabled();
    }

    public float getRainGradient() {
        if (!isEnabled() || weatherType.getSelected().equals("Clear")) {
            prevRainGradient = 0.0f;
            return 0.0f;
        }
        float target = 1.0f;
        if (weatherType.getSelected().equals("Rain")) target = rainDensity.getValue() / 100.0f;
        else if (weatherType.getSelected().equals("Snow")) target = snowDensity.getValue() / 100.0f;
        prevRainGradient = target;
        return target;
    }

    public float getThunderGradient() {
        if (!isEnabled() || !weatherType.getSelected().equals("Thunder")) {
            prevThunderGradient = 0.0f;
            return 0.0f;
        }
        float target = thunderStrength.getValue() / 100.0f;
        prevThunderGradient = target;
        return target;
    }

    @Override
    public String getDescription() {
        return "Change the weather in the world (client-side, works on any server)";
    }

    @Override
    public String getIcon() {
        return "weather_changer.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    @Override
    public boolean showIconInSettings() {
        return false;
    }
}
