package vorga.phazeclient.api.feature.module.setting.implement;

import vorga.phazeclient.api.feature.module.setting.Setting;

/** The saved-waypoint list in the Waypoints module; its data lives in the waypoint store, not the config. */
public class WaypointListSetting extends Setting {
    public WaypointListSetting(String name, String description) {
        super(name, description);
        setSaveToConfig(false);
        fullWidth(true);
    }
}
