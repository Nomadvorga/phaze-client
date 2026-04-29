package vorga.phazeclient.api.feature.module.setting;

import lombok.Getter;
import lombok.Setter;

import java.util.function.Consumer;
import java.util.function.Supplier;

@Getter
public class Setting {

    private final String nameKey;
    private final String descriptionKey;
    @Setter
    private String moduleContext;
    @Setter
    private Supplier<Boolean> visible;
    @Setter
    private static Consumer<Setting> globalChangeListener;
    @Setter
    private boolean saveToConfig = true;
    @Setter
    private boolean fullWidth = false;

    public Setting(String nameKey) {
        this.nameKey = nameKey;
        this.descriptionKey = null;
    }

    public Setting(String nameKey, String descriptionKey) {
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
    }

    public boolean isVisible() {
        return visible == null || visible.get();
    }

    public String getName() {
        return nameKey;
    }

    public String getDescription() {
        if (descriptionKey == null || descriptionKey.isEmpty()) {
            return "";
        }
        return descriptionKey;
    }

    @Deprecated
    public String getLocalizedName() {
        return getName();
    }

    @Deprecated
    public String getLocalizedDescription() {
        return getDescription();
    }

    public boolean isModified() {
        return false;
    }

    public void reset() {
    }

    public Setting fullWidth(boolean fullWidth) {
        this.fullWidth = fullWidth;
        return this;
    }

    protected void notifyChange() {
        if (globalChangeListener != null) {
            globalChangeListener.accept(this);
        }
    }
}
