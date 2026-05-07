package vorga.phazeclient.implement.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.feature.module.setting.implement.BindSetting;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.GroupSetting;
import vorga.phazeclient.api.feature.module.setting.implement.MultiColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.MultiSelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.TextSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.features.modules.hud.ArmorHud;
import vorga.phazeclient.implement.features.modules.hud.RectHudModule;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ConfigManager INSTANCE = new ConfigManager();
    
    private final File configDir;
    private final File configsDir;
    private final File filesDir;
    private final File currentConfigFile;
    private File currentConfig;
    private String currentConfigName = "default";

    // Debounced auto-save state. The global Setting change listener flips
    // dirtyAt to System.currentTimeMillis(); flushIfDirty (called once per
    // tick) actually writes when the dirty timer is older than the debounce
    // window. This avoids spamming disk during slider drags or rapid bind
    // re-presses while still guaranteeing single-action settings persist.
    private static final long AUTOSAVE_DEBOUNCE_MS = 250L;
    private volatile long dirtyAt = 0L;
    private volatile boolean autoSaveEnabled = false;

    public static ConfigManager getInstance() {
        return INSTANCE;
    }

    public ConfigManager() {
        File minecraftDir = MinecraftClient.getInstance().runDirectory;
        configDir = new File(minecraftDir, "Phaze");
        configsDir = new File(configDir, "configs");
        filesDir = new File(configDir, "files");
        currentConfigFile = new File(filesDir, "current_config.json");

        initDirectories();
        loadCurrentConfigName();
        currentConfig = getConfigFile(currentConfigName);
    }

    private void initDirectories() {
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        if (!configsDir.exists()) {
            configsDir.mkdirs();
        }
        if (!filesDir.exists()) {
            filesDir.mkdirs();
        }
    }
    
    private void loadCurrentConfigName() {
        if (currentConfigFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(currentConfigFile))) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json != null && json.has("current_config")) {
                    currentConfigName = json.get("current_config").getAsString();
                }
                if (currentConfigName == null || currentConfigName.isEmpty()) {
                    currentConfigName = "default";
                }
            } catch (IOException e) {
                currentConfigName = "default";
            }
        }
    }
    
    private void saveCurrentConfigName() {
        try (FileWriter writer = new FileWriter(currentConfigFile)) {
            JsonObject json = new JsonObject();
            json.addProperty("current_config", currentConfigName);
            GSON.toJson(json, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public String getCurrentConfigName() {
        return currentConfigName;
    }
    
    public void setCurrentConfigName(String name) {
        this.currentConfigName = name;
        saveCurrentConfigName();
    }
    
    public File getConfigsDir() {
        return configsDir;
    }
    
    public File getConfigFile(String configName) {
        return new File(configsDir, configName + ".Phaze");
    }
    
    public void save(File configFile) {
        JsonObject config = new JsonObject();
        JsonObject modules = new JsonObject();
        
        for (Module module : Main.getInstance().getModuleProvider().getModules()) {
            JsonObject moduleData = new JsonObject();
            moduleData.addProperty("enabled", module.isState());
            moduleData.addProperty("key", module.getKey());

            if (module instanceof RectHudModule rectHudModule) {
                moduleData.addProperty("hud_x", rectHudModule.getHudX());
                moduleData.addProperty("hud_y", rectHudModule.getHudY());
                moduleData.addProperty("hud_scale", rectHudModule.getHudScale());
            } else if (module instanceof ArmorHud armorHud) {
                moduleData.addProperty("hud_x", armorHud.getHudX());
                moduleData.addProperty("hud_y", armorHud.getHudY());
                moduleData.addProperty("hud_scale", armorHud.getHudScale());
            }
            
            JsonObject settings = new JsonObject();
            for (Setting setting : module.settings()) {
                serializeSetting(setting, settings);
            }
            moduleData.add("settings", settings);
            
            modules.add(module.getName(), moduleData);
        }
        
        config.add("modules", modules);
        vorga.phazeclient.implement.features.modules.client.Theme theme = vorga.phazeclient.implement.features.modules.client.Theme.getInstance();
        config.addProperty("theme", theme.menuTheme.getSelected());
        config.addProperty("blurRadius", theme.blurRadius.getValue());
        
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveConfig(String configName) {
        save(getConfigFile(configName));
    }
    
    /**
     * Persists the in-memory state to whatever config is currently active.
     *
     * <p>Special-case: the {@code default} config is treated as a
     * read-only baseline (it's effectively a "factory reset" target).
     * Any attempt to write to it - whether via the auto-save pipeline
     * after the user changes a setting, or via an explicit save - is
     * transparently redirected to a config named {@code autosave}, which
     * is created on demand and switched to the active config so the
     * {@code default} file stays pristine. This way the user can always
     * "Reset to Default" without first having to remember which settings
     * they had changed.
     */
    public void saveCurrentConfig() {
        if ("default".equalsIgnoreCase(currentConfigName)) {
            currentConfigName = "autosave";
            currentConfig = getConfigFile("autosave");
        }
        if (currentConfig != null) {
            save(currentConfig);
            saveCurrentConfigName();
        }
    }

    /**
     * Enables the auto-save pipeline. Should be called AFTER the initial
     * config load completes so the load-time {@code setValue} calls don't
     * trigger their own redundant writes.
     */
    public void enableAutoSave() {
        autoSaveEnabled = true;
    }

    /**
     * Marks the in-memory config as dirty so the next {@link #flushIfDirty}
     * tick writes it to disk. Cheap and lock-free; safe to call from any
     * setting's notifyChange path.
     */
    public void markDirty() {
        if (autoSaveEnabled) {
            dirtyAt = System.currentTimeMillis();
        }
    }

    /**
     * Persists the current config to disk if {@link #markDirty} was called
     * at least {@link #AUTOSAVE_DEBOUNCE_MS} ago. Called once per client tick
     * from {@link vorga.phazeclient.core.Main}; the debounce keeps slider
     * drags / bind re-presses from spamming disk.
     */
    public void flushIfDirty() {
        long ts = dirtyAt;
        if (ts == 0L) {
            return;
        }
        if (System.currentTimeMillis() - ts < AUTOSAVE_DEBOUNCE_MS) {
            return;
        }
        dirtyAt = 0L;
        saveCurrentConfig();
    }
    
    public void loadConfig(String configName) {
        try {
            loadConfigInternal(configName);
        } finally {
            // Settings updated during the load fire notifyChange() ->
            // markDirty(), which would otherwise cause the very next
            // flushIfDirty tick to write the freshly-loaded state back
            // to disk. Worse: when the user switches to the "default"
            // config, that flush would hit saveCurrentConfig's default
            // -> autosave redirect and immediately flip the active
            // config back to autosave (overwriting the autosave file
            // with the default state in the process). Clearing the
            // dirty timestamp here scopes auto-save to genuine post-
            // load user actions only.
            dirtyAt = 0L;
        }
    }

    private void loadConfigInternal(String configName) {
        // Save current config before loading new one, only when switching to a different config
        if (currentConfig != null && currentConfig.exists() && !currentConfigName.equals(configName)) {
            save(currentConfig);
        }
        
        File configFile = getConfigFile(configName);
        
        // If loading default config, reset it to default state instead of loading from file
        if (configName.equalsIgnoreCase("default")) {
            // Reset all modules to default state
            for (Module module : Main.getInstance().getModuleProvider().getModules()) {
                if (module.isState()) {
                    module.switchState();
                }
            }
            for (Module module : Main.getInstance().getModuleProvider().getModules()) {
                if (module instanceof RectHudModule rectHudModule) {
                    rectHudModule.resetHudTransform();
                } else if (module instanceof ArmorHud armorHud) {
                    armorHud.resetHudTransform();
                }
            }
            // Reset theme to default
            vorga.phazeclient.implement.features.modules.client.Theme.getInstance().menuTheme.setSelected("Lunar Blue");
            vorga.phazeclient.implement.features.modules.client.Theme.getInstance().blurRadius.setValue(5.0F);
            
            currentConfig = configFile;
            setCurrentConfigName(configName);
            return;
        }
        
        if (!configFile.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            JsonObject config = GSON.fromJson(reader, JsonObject.class);
            
            if (config.has("modules")) {
                JsonObject modules = config.getAsJsonObject("modules");
                for (Module module : Main.getInstance().getModuleProvider().getModules()) {
                    boolean hasModuleData = modules.has(module.getName());
                    JsonObject moduleData = hasModuleData ? modules.getAsJsonObject(module.getName()) : null;

                    if (module.isShowEnable()) {
                        boolean enabled = hasModuleData && moduleData.has("enabled") && moduleData.get("enabled").getAsBoolean();
                        module.setState(enabled);
                    }

                    if (hasModuleData && moduleData.has("key")) {
                        module.setKey(moduleData.get("key").getAsInt());
                    }

                    if (hasModuleData && moduleData.has("settings")) {
                        JsonObject settings = moduleData.getAsJsonObject("settings");
                        for (Setting setting : module.settings()) {
                            try {
                                deserializeSetting(setting, settings);
                            } catch (Exception ignored) {
                                // Skip invalid values
                            }
                        }
                    }

                    if (module instanceof RectHudModule rectHudModule) {
                        if (!hasModuleData) {
                            rectHudModule.resetHudTransform();
                        } else {
                            if (moduleData.has("hud_x")) {
                                rectHudModule.setHudX(moduleData.get("hud_x").getAsFloat());
                            } else {
                                rectHudModule.resetHudTransform();
                            }
                            if (moduleData.has("hud_y")) {
                                rectHudModule.setHudY(moduleData.get("hud_y").getAsFloat());
                            }
                            if (moduleData.has("hud_scale")) {
                                rectHudModule.setHudScale(moduleData.get("hud_scale").getAsFloat());
                            }
                        }
                    } else if (module instanceof ArmorHud armorHud) {
                        if (!hasModuleData) {
                            armorHud.resetHudTransform();
                        } else {
                            if (moduleData.has("hud_x")) {
                                armorHud.setHudX(moduleData.get("hud_x").getAsFloat());
                            } else {
                                armorHud.resetHudTransform();
                            }
                            if (moduleData.has("hud_y")) {
                                armorHud.setHudY(moduleData.get("hud_y").getAsFloat());
                            }
                            if (moduleData.has("hud_scale")) {
                                armorHud.setHudScale(moduleData.get("hud_scale").getAsFloat());
                            }
                        }
                    }
                }
            }
            
            if (config.has("theme")) {
                String theme = config.get("theme").getAsString();
                vorga.phazeclient.implement.features.modules.client.Theme.getInstance().menuTheme.setSelected(theme);
            }
            
            if (config.has("blurRadius")) {
                float blurRadius = config.get("blurRadius").getAsFloat();
                vorga.phazeclient.implement.features.modules.client.Theme.getInstance().blurRadius.setValue(blurRadius);
            }
            
            currentConfig = configFile;
            setCurrentConfigName(configName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void loadCurrentConfig() {
        loadConfig(currentConfigName);
    }
    
    /**
     * Returns the list of available config names, ordered so that the
     * pinned configs come first:
     * <ol>
     *   <li>{@code default} - always present even if no
     *       {@code default.Phaze} file has been written yet (it's a
     *       virtual factory-reset target).</li>
     *   <li>{@code autosave} - second when present, so the user's most
     *       recent auto-saved state sits right under the reset target.</li>
     *   <li>Everything else, case-insensitive alphabetical.</li>
     * </ol>
     */
    public String[] getConfigList() {
        File[] files = configsDir.listFiles((dir, name) -> name.endsWith(".Phaze"));

        Set<String> uniqueNames = new LinkedHashSet<>();
        // Always expose "default" - even on a fresh install where no
        // default.Phaze exists yet - because the load path treats it as
        // a virtual reset target rather than a real file.
        uniqueNames.add("default");
        if (files != null) {
            for (File file : files) {
                uniqueNames.add(file.getName().replace(".Phaze", ""));
            }
        }

        String[] configs = uniqueNames.toArray(new String[0]);
        Arrays.sort(configs, CONFIG_LIST_ORDER);
        return configs;
    }

    /** See {@link #getConfigList()} for ordering rules. */
    private static final Comparator<String> CONFIG_LIST_ORDER = (a, b) -> {
        int aRank = pinnedRank(a);
        int bRank = pinnedRank(b);
        if (aRank != bRank) return Integer.compare(aRank, bRank);
        return a.compareToIgnoreCase(b);
    };

    private static int pinnedRank(String name) {
        if ("default".equalsIgnoreCase(name)) return 0;
        if ("autosave".equalsIgnoreCase(name)) return 1;
        return 2;
    }
    
    public boolean configExists(String configName) {
        return getConfigFile(configName).exists();
    }
    
    public void deleteConfig(String configName) {
        // Prevent deletion of current config
        if (currentConfigName.equals(configName)) {
            return;
        }
        
        File configFile = getConfigFile(configName);
        if (configFile.exists()) {
            configFile.delete();
        }
    }
    
    public void renameConfig(String oldName, String newName) {
        File oldFile = getConfigFile(oldName);
        File newFile = getConfigFile(newName);
        if (oldFile.exists()) {
            oldFile.renameTo(newFile);
        }
    }

    public String createNewConfig() {
        String name = nextNewConfigName();
        saveConfig(name);
        loadConfig(name);
        return name;
    }

    private String nextNewConfigName() {
        int index = 1;
        while (true) {
            String name = "new-config" + index;
            if (!configExists(name)) {
                return name;
            }
            index++;
        }
    }

    /**
     * Writes a single {@link Setting} into the supplied JSON object,
     * keyed by {@link Setting#getName()} (= {@code nameKey}). Mirrors
     * the per-type encoding {@link vorga.phazeclient.implement.menu.components.implement.module.ModuleComponent}
     * uses for its clipboard copy/paste flow so saved configs and the
     * copy/paste payload share an on-disk shape - including settings
     * the previous inline switch silently dropped (text, multi-select,
     * multi-color, group + recursive sub-settings).
     *
     * <p>Skips settings that opt out of persistence via
     * {@link Setting#isSaveToConfig()} - {@link vorga.phazeclient.api.feature.module.setting.implement.SectionSetting}
     * and {@link vorga.phazeclient.api.feature.module.setting.implement.ButtonSetting}
     * fall under this; they are pure UI affordances with nothing to
     * persist.
     */
    private static void serializeSetting(Setting setting, JsonObject target) {
        if (setting == null || !setting.isSaveToConfig()) {
            return;
        }

        String key = setting.getName();

        switch (setting) {
            case BooleanSetting booleanSetting -> target.addProperty(key, booleanSetting.isValue());
            case ValueSetting valueSetting -> target.addProperty(key, valueSetting.getValue());
            case TextSetting textSetting -> {
                String value = textSetting.getText();
                if (value != null) {
                    // Match ModuleComponent's transport encoding so a config
                    // round-trips identically through both the auto-save
                    // pipeline and the clipboard import flow.
                    value = value.replace(" ", "%%").replace("/", "++");
                }
                target.addProperty(key, value);
            }
            case BindSetting bindSetting -> target.addProperty(key, bindSetting.getKey());
            case ColorSetting colorSetting -> target.addProperty(key, colorSetting.getColor());
            case SelectSetting selectSetting -> target.addProperty(key, selectSetting.getSelected());
            case MultiSelectSetting multiSelectSetting -> {
                List<String> selected = multiSelectSetting.getSelected();
                target.addProperty(key, selected == null ? "" : String.join(",", selected));
            }
            case MultiColorSetting multiColor -> {
                JsonObject colorObject = new JsonObject();
                colorObject.addProperty("selectedColorIndex", multiColor.getSelectedColorIndex());

                JsonArray colorsArray = new JsonArray();
                for (ColorSetting color : multiColor.getAllColors()) {
                    colorsArray.add(color.getColor());
                }
                colorObject.add("colors", colorsArray);
                target.add(key, colorObject);
            }
            case GroupSetting group -> {
                JsonObject groupObject = new JsonObject();
                groupObject.addProperty("state", group.isValue());
                for (Setting subSetting : group.getSubSettings()) {
                    serializeSetting(subSetting, groupObject);
                }
                target.add(key, groupObject);
            }
            default -> {
                // Unsupported / non-persisting setting; drop silently so
                // a future setting type doesn't crash the whole save.
            }
        }
    }

    /**
     * Pulls a single {@link Setting} value back out of the supplied
     * JSON object, matching the encoding produced by
     * {@link #serializeSetting(Setting, JsonObject)}. Returns silently
     * when the key is absent or the setting opts out of persistence.
     *
     * <p>{@link MultiColorSetting} retains a backwards-compat path for
     * configs written by older builds that stored a single int instead
     * of the structured object - the legacy int is mapped onto the
     * first color so existing user configs aren't reset on upgrade.
     */
    private static void deserializeSetting(Setting setting, JsonObject source) {
        if (setting == null || !setting.isSaveToConfig()) {
            return;
        }

        String key = setting.getName();
        if (!source.has(key)) {
            return;
        }
        JsonElement element = source.get(key);
        if (element == null || element.isJsonNull()) {
            return;
        }

        switch (setting) {
            case BooleanSetting booleanSetting -> booleanSetting.setValue(element.getAsBoolean());
            case ValueSetting valueSetting -> valueSetting.setValue(element.getAsFloat());
            case TextSetting textSetting -> {
                String value = element.getAsString();
                if (value != null) {
                    value = value.replace("%%", " ").replace("++", "/");
                }
                textSetting.setText(value);
            }
            case BindSetting bindSetting -> bindSetting.setKey(element.getAsInt());
            case ColorSetting colorSetting -> colorSetting.setColor(element.getAsInt());
            case SelectSetting selectSetting -> selectSetting.setSelected(element.getAsString());
            case MultiSelectSetting multiSelectSetting -> {
                String value = element.getAsString();
                List<String> selected = new ArrayList<>(Arrays.asList(value.split(",")));
                // Drop entries that no longer exist in the option list
                // (so renaming an option in code doesn't strand the user
                // with a phantom selection that fails the equality check
                // everywhere downstream).
                selected.removeIf(s -> s.isEmpty() || !multiSelectSetting.getList().contains(s));
                multiSelectSetting.setSelected(selected);
            }
            case MultiColorSetting multiColor -> {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                    int legacyColor = element.getAsInt();
                    if (multiColor.getColor1() != null) {
                        multiColor.getColor1().setColor(legacyColor);
                    }
                    return;
                }
                JsonObject colorObject = element.getAsJsonObject();
                if (colorObject.has("selectedColorIndex")) {
                    multiColor.setSelectedColorIndex(colorObject.get("selectedColorIndex").getAsInt());
                }
                if (colorObject.has("colors")) {
                    JsonArray colorsArray = colorObject.getAsJsonArray("colors");
                    List<ColorSetting> colorSettings = multiColor.getAllColors();
                    int n = Math.min(colorsArray.size(), colorSettings.size());
                    for (int i = 0; i < n; i++) {
                        colorSettings.get(i).setColor(colorsArray.get(i).getAsInt());
                    }
                }
            }
            case GroupSetting group -> {
                JsonObject groupObject = element.getAsJsonObject();
                if (groupObject.has("state")) {
                    group.setValue(groupObject.get("state").getAsBoolean());
                }
                for (Setting subSetting : group.getSubSettings()) {
                    try {
                        deserializeSetting(subSetting, groupObject);
                    } catch (Exception ignored) {
                    }
                }
            }
            default -> {
                // Unsupported / non-persisting setting type.
            }
        }
    }
}
