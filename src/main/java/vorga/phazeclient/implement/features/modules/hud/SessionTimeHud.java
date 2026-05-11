package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;

public final class SessionTimeHud extends RectHudModule {
    private static final SessionTimeHud INSTANCE = new SessionTimeHud();

    public final SectionSetting sessionSection = new SectionSetting("Session");
    public final SelectSetting displayOption = new SelectSetting("Display Option", "Session time format")
            .value("12h 34m 56s", "123.456s", "12:34:56", "12:34", "12:34:56.789")
            .selected("12:34:56");

    public static SessionTimeHud getInstance() {
        return INSTANCE;
    }

    private SessionTimeHud() {
        super("session_time_hud", "Session Time", 22.0f, 476.0f, 1.0f);
        displayOption.setFullWidth(true);
        setup(sessionSection, displayOption);
    }

    @Override
    public String getIcon() {
        return "session_time.png";
    }

    @Override
    public String getDescription() {
        return "Shows current session duration";
    }
}
