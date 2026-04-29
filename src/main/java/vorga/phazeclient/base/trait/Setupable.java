package vorga.phazeclient.base.trait;

import vorga.phazeclient.api.feature.module.setting.Setting;

public interface Setupable {
    void setup(Setting... settings);
}