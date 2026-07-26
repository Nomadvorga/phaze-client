package vorga.phazeclient.api.feature.module.setting;

import com.google.common.collect.Lists;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import vorga.phazeclient.base.trait.Setupable;

import java.util.Arrays;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SettingRepository implements Setupable {
    List<Setting> settings = Lists.newArrayList();

    @Override
    public void setup(Setting... setting) {
        settings.addAll(Arrays.asList(setting));
    }

    public Setting get(String name) {
        return settings.stream()
                .filter(setting -> setting.getNameKey().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public List<Setting> settings() {
        return settings;
    }
}
