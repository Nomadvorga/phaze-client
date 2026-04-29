package vorga.phazeclient.api.system.localization;

public class LocalizationManager {
    private static final LocalizationManager INSTANCE = new LocalizationManager();

    public static LocalizationManager getInstance() {
        return INSTANCE;
    }

    public String get(String key) {
        return key;
    }
}
