package vorga.phazeclient.api.feature.module.setting.implement;

import vorga.phazeclient.api.feature.module.setting.Setting;

import java.util.function.Supplier;

public class SectionSetting extends Setting {
    public SectionSetting(String name) {
        super(name);
        setSaveToConfig(false);
        setFullWidth(true);
    }

    public SectionSetting visible(Supplier<Boolean> visible) {
        setVisible(visible);
        return this;
    }

    @Override
    public boolean isModified() {
        return false;
    }
}
