package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class StreamerMode extends Module {
    private static final StreamerMode INSTANCE = new StreamerMode();

    private static final String[] PASSWORD_COMMANDS = {
            "/l",
            "/login",
            "/log",
            "/r",
            "/reg",
            "/register",
            "/registration",
            "/changepass",
            "/changepassword",
            "/changepw",
            "/cp",
            "/auth",
            "/authme",
            "/pass",
            "/password",
            "/unreg",
            "/unregister",

            "/л",
            "/логин",
            "/р",
            "/рег",
            "/регистрация",
            "/смп",
            "/сменапароля",
    };

    public final SectionSetting generalSection = new SectionSetting("General");
    public final BooleanSetting hideCoordinates = new BooleanSetting(
            "Hide Coordinates",
            "Hide your XYZ position in the F3 debug screen, the WAILA HUD, and the Coordinates HUD"
    ).setValue(true);
    public final BooleanSetting hidePasswords = new BooleanSetting(
            "Hide Passwords",
            "Replace passwords with * (visually only) when typing /login, /reg, /register, /changepass, etc."
    ).setValue(true);

    private StreamerMode() {

        super("streamer_mode", "Streamer Mode", ModuleCategory.OTHER);
        hideCoordinates.setFullWidth(true);
        hidePasswords.setFullWidth(true);
        setup(generalSection, hideCoordinates, hidePasswords);
    }

    public static StreamerMode getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Hides sensitive info while streaming: coordinates and passwords";
    }

    @Override
    public String getIcon() {
        return "streamer_mode.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public boolean isHideCoordinatesEnabled() {
        return isEnabled() && hideCoordinates.isValue();
    }

    public boolean isHidePasswordsEnabled() {
        return isEnabled() && hidePasswords.isValue();
    }

    public static String maskPasswordIfMatching(String text) {
        if (text == null || text.length() < 2 || text.charAt(0) != '/') {
            return text;
        }
        String lower = text.toLowerCase();
        for (String cmd : PASSWORD_COMMANDS) {
            if (!lower.startsWith(cmd)) {
                continue;
            }

            if (lower.length() != cmd.length() && lower.charAt(cmd.length()) != ' ') {
                continue;
            }
            int spaceIdx = text.indexOf(' ');
            if (spaceIdx < 0) {

                return text;
            }
            StringBuilder sb = new StringBuilder(text.length());
            sb.append(text, 0, spaceIdx + 1);
            for (int i = spaceIdx + 1; i < text.length(); i++) {
                char c = text.charAt(i);
                sb.append(c == ' ' ? ' ' : '*');
            }
            return sb.toString();
        }
        return text;
    }
}
