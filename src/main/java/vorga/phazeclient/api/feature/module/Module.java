package vorga.phazeclient.api.feature.module;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.feature.module.setting.SettingRepository;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.base.QuickImports;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.setting.implement.GroupSetting;

import java.util.function.Consumer;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Module extends SettingRepository implements QuickImports {
    String name;
    String visibleName;
    ModuleCategory category;
    @NonFinal ModuleCategory secondaryCategory;
    boolean showEnable;
    boolean canBind;
    Animation animation = new DecelerateAnimation().setMs(150).setValue(1);

    public Module(String name, ModuleCategory category) {
        this.name = name;
        this.category = category;
        this.visibleName = name;
        this.secondaryCategory = null;
        this.showEnable = true;
        this.canBind = true;
        initializeModule();
    }

    public Module(String name, ModuleCategory category, boolean showEnable) {
        this.name = name;
        this.category = category;
        this.visibleName = name;
        this.secondaryCategory = null;
        this.showEnable = showEnable;
        this.canBind = true;
        initializeModule();
    }

    public Module(String name, ModuleCategory category, boolean showEnable, boolean canBind) {
        this.name = name;
        this.category = category;
        this.visibleName = name;
        this.secondaryCategory = null;
        this.showEnable = showEnable;
        this.canBind = canBind;
        initializeModule();
    }

    public Module(String name, String visibleName, ModuleCategory category) {
        this.name = name;
        this.visibleName = visibleName;
        this.category = category;
        this.secondaryCategory = null;
        this.showEnable = true;
        this.canBind = true;
        initializeModule();
    }

    public Module(String name, String visibleName, ModuleCategory category, boolean showEnable) {
        this.name = name;
        this.visibleName = visibleName;
        this.category = category;
        this.secondaryCategory = null;
        this.showEnable = showEnable;
        this.canBind = true;
        initializeModule();
    }

    public Module(String name, String visibleName, ModuleCategory category, boolean showEnable, boolean canBind) {
        this.name = name;
        this.visibleName = visibleName;
        this.category = category;
        this.secondaryCategory = null;
        this.showEnable = showEnable;
        this.canBind = canBind;
        initializeModule();
    }

    @NonFinal
    int key = GLFW.GLFW_KEY_UNKNOWN, type = 1;

    @NonFinal
    public boolean state;

    @NonFinal
    public boolean expanded = false;

    @Setter
    private static Consumer<Module> globalStateChangeListener;

    public void switchState() {
        setState(!state);
    }

    public void setState(boolean state) {
        animation.setDirection(state ? Direction.FORWARDS : Direction.BACKWARDS);
        if (state != this.state) {
            this.state = state;
            handleStateChange();
            notifyStateChange();
        }
    }

    private void notifyStateChange() {
        if (globalStateChangeListener != null) {
            globalStateChangeListener.accept(this);
        }
    }

    public void setKey(int key) {
        if (this.key != key) {
            this.key = key;
            notifyStateChange();
        }
    }

    public void setStateSilent(boolean state) {
        animation.setDirection(state ? Direction.FORWARDS : Direction.BACKWARDS);
        if (state != this.state) {
            this.state = state;
            toggleSilent(isEnabled());
        }
    }

    private void handleStateChange() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.world != null) {
            if (state) {
                activate();
            } else {
                deactivate();
            }
        }
        toggleSilent(isEnabled());
    }

    private void toggleSilent(boolean activate) {
        /* REMOVED - EventManager calls removed */
    }

    private void initializeModule() {
        if (!showEnable) {
            toggleSilent(true);
        }
    }

    public boolean isEnabled() {
        return !showEnable || state;
    }

    public boolean isVisible() {
        return true;
    }

    public void activate() {
    }

    public void deactivate() {
    }

    @Override
    public final void setup(Setting... settings) {
        String moduleContext = extractModuleContext(name);

        for (Setting setting : settings) {
            setModuleContextRecursive(setting, moduleContext);
        }
        super.setup(settings);
    }

    private String extractModuleContext(String moduleName) {
        if (moduleName != null && moduleName.startsWith("module.") && moduleName.endsWith(".name")) {
            String withoutPrefix = moduleName.substring(7);
            return withoutPrefix.substring(0, withoutPrefix.length() - 5);
        }
        return moduleName;
    }

    private void setModuleContextRecursive(Setting setting, String moduleName) {
        setting.setModuleContext(moduleName);

        if (setting instanceof GroupSetting group) {
            for (Setting subSetting : group.getSubSettings()) {
                setModuleContextRecursive(subSetting, moduleName);
            }
        }
    }

    public String getVisibleName() {
        return visibleName;
    }

    @Deprecated
    public String getLocalizedName() {
        return getVisibleName();
    }

    public String getDescription() {
        return "No description";
    }

    public String getIcon() {
        return null;
    }

    public float getIconSize() {
        return 16.0F;
    }

    public boolean showIconInSettings() {
        return true;
    }

    public String getVisibleNameKey() {
        return visibleName;
    }

    public String getIdentifier() {
        return extractModuleContext(name);
    }
}
