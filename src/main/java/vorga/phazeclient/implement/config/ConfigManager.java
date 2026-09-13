package vorga.phazeclient.implement.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.client.MinecraftClient;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.feature.module.setting.implement.BindSetting;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.GroupSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ItemPickerSetting;
import vorga.phazeclient.api.feature.module.setting.implement.MultiColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.MultiSelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.TextSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.features.modules.hud.ArmorHud;
import vorga.phazeclient.implement.features.modules.hud.RectHudModule;
import vorga.phazeclient.implement.menu.MenuUiSettings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

    private static final long AUTOSAVE_DEBOUNCE_MS = 250L;
    private volatile long dirtyAt = 0L;
    private volatile boolean autoSaveEnabled = false;

    private volatile boolean isLoadingConfig = false;

    public static ConfigManager getInstance() {
        return INSTANCE;
    }

    private ConfigManager() {
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
                moduleData.addProperty("hud_x_ratio", rectHudModule.getHudXRatio());
                moduleData.addProperty("hud_y_ratio", rectHudModule.getHudYRatio());
                moduleData.addProperty("hud_scale", rectHudModule.getHudScale());
            } else if (module instanceof ArmorHud armorHud) {
                moduleData.addProperty("hud_x", armorHud.getHudX());
                moduleData.addProperty("hud_y", armorHud.getHudY());
                moduleData.addProperty("hud_x_ratio", armorHud.getHudXRatio());
                moduleData.addProperty("hud_y_ratio", armorHud.getHudYRatio());
                moduleData.addProperty("hud_scale", armorHud.getHudScale());
            }

            JsonObject settings = new JsonObject();
            for (Setting setting : module.settings()) {
                try {
                    serializeSetting(setting, settings);
                } catch (Throwable t) {

                    System.err.println("[Phaze] failed to serialize setting " + setting.getRawName() + " of " + module.getName() + ": " + t);
                }
            }
            moduleData.add("settings", settings);

            modules.add(module.getName(), moduleData);
        }

        config.add("modules", modules);
        vorga.phazeclient.implement.features.modules.client.Theme theme = vorga.phazeclient.implement.features.modules.client.Theme.getInstance();
        config.addProperty("theme", theme.menuTheme.getSelected());
        config.addProperty("blurRadius", theme.blurRadius.getValue());
        config.addProperty("menuPanoramaSpeed", MenuUiSettings.getInstance().getPanoramaSpeed());
        config.addProperty("menuGuiFpsLimit", MenuUiSettings.getInstance().getGuiFpsLimit());
        config.addProperty("menuGuiScale", MenuUiSettings.getInstance().getGuiScale());
        config.addProperty("menuPanoramaPreset", MenuUiSettings.getInstance().getSelectedPanoramaPresetId());
        config.addProperty("customMainMenuEnabled", MenuUiSettings.getInstance().isCustomMainMenuEnabled());
        config.addProperty("menuPanoramaSpeedScaleVersion", MenuUiSettings.PANORAMA_SPEED_SCALE_VERSION);

        if (configFile.exists()) {
            try (Reader rdr = new FileReader(configFile)) {
                JsonObject existing = GSON.fromJson(rdr, JsonObject.class);
                if (existing != null && existing.has("imported")
                        && existing.get("imported").getAsBoolean()) {
                    config.addProperty("imported", true);
                }
            } catch (Throwable ignored) {

            }
        }

        Path target = configFile.toPath();
        Path tmp = target.resolveSibling(configFile.getName() + ".tmp");
        Path bak = target.resolveSibling(configFile.getName() + ".bak");

        try {

            try (FileWriter writer = new FileWriter(tmp.toFile())) {
                GSON.toJson(config, writer);
            }

            if (Files.exists(target)) {
                try {
                    Files.move(target, bak, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {

                }
            }

            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicFailed) {

                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            e.printStackTrace();

            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    public void saveConfig(String configName) {
        save(getConfigFile(configName));
    }

    public String exportCurrentToString() {
        try {
            JsonObject config = buildCurrentConfigJson();
            String json = GSON.toJson(config);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(out)) {
                gz.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            String b64 = java.util.Base64.getEncoder().withoutPadding().encodeToString(out.toByteArray());
            return "PHAZE1:" + b64;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    public String importFromString(String shareString) {
        return importFromString(shareString, null);
    }

    public String importFromString(String shareString, String preferredName) {
        if (shareString == null) return null;

        flushNow();
        String trimmed = shareString.trim();

        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 1) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (!trimmed.startsWith("PHAZE1:")) {
            return null;
        }
        try {
            String b64 = trimmed.substring("PHAZE1:".length());
            byte[] gz = java.util.Base64.getDecoder().decode(b64);
            byte[] raw;
            try (java.util.zip.GZIPInputStream in = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(gz))) {
                raw = in.readAllBytes();
            }
            String json = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
            JsonObject parsed = GSON.fromJson(json, JsonObject.class);
            if (parsed == null) {
                return null;
            }

            String name = pickImportName(preferredName);
            File target = getConfigFile(name);

            parsed.addProperty("imported", true);
            try (FileWriter writer = new FileWriter(target)) {
                GSON.toJson(parsed, writer);
            }

            isLoadingConfig = true;
            try {
                currentConfig = target;
                setCurrentConfigName(name);

                applyInCodeDefaults();

                applyConfigJson(parsed);
            } finally {
                dirtyAt = 0L;
                isLoadingConfig = false;
            }
            return name;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    private String pickImportName(String preferredName) {
        if (preferredName != null) {
            String sanitised = preferredName.trim();
            if (!sanitised.isEmpty()
                    && sanitised.matches("[A-Za-z0-9_\\-]{1,32}")
                    && !getConfigFile(sanitised).exists()) {
                return sanitised;
            }

            if (sanitised.matches("[A-Za-z0-9_\\-]{1,30}")) {
                for (int i = 2; i < 1000; i++) {
                    String candidate = sanitised + "_" + i;
                    if (!getConfigFile(candidate).exists()) {
                        return candidate;
                    }
                }
            }
        }
        return nextImportedName();
    }

    private String nextImportedName() {
        for (int i = 1; i < 1000; i++) {
            String candidate = i == 1 ? "imported" : "imported_" + i;
            if (!getConfigFile(candidate).exists()) {
                return candidate;
            }
        }
        return "imported_" + System.currentTimeMillis();
    }

    private JsonObject buildCurrentConfigJson() {
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
                try {
                    serializeSetting(setting, settings);
                } catch (Throwable ignored) {}
            }
            moduleData.add("settings", settings);
            modules.add(module.getName(), moduleData);
        }

        config.add("modules", modules);
        vorga.phazeclient.implement.features.modules.client.Theme theme = vorga.phazeclient.implement.features.modules.client.Theme.getInstance();
        config.addProperty("theme", theme.menuTheme.getSelected());
        config.addProperty("blurRadius", theme.blurRadius.getValue());
        config.addProperty("menuPanoramaSpeed", MenuUiSettings.getInstance().getPanoramaSpeed());
        config.addProperty("menuGuiFpsLimit", MenuUiSettings.getInstance().getGuiFpsLimit());
        config.addProperty("menuGuiScale", MenuUiSettings.getInstance().getGuiScale());
        config.addProperty("menuPanoramaPreset", MenuUiSettings.getInstance().getSelectedPanoramaPresetId());
        config.addProperty("customMainMenuEnabled", MenuUiSettings.getInstance().isCustomMainMenuEnabled());
        config.addProperty("menuPanoramaSpeedScaleVersion", MenuUiSettings.PANORAMA_SPEED_SCALE_VERSION);
        return config;
    }

    public void saveCurrentConfig() {

        if (isLoadingConfig) {
            return;
        }
        if (currentConfig != null) {
            save(currentConfig);
            saveCurrentConfigName();
        }
    }

    public void enableAutoSave() {
        autoSaveEnabled = true;
    }

    public void markDirty() {
        if (autoSaveEnabled && !isLoadingConfig) {
            dirtyAt = System.currentTimeMillis();
        }
    }

    public void flushIfDirty() {
        if (isLoadingConfig) {
            return;
        }
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

    public void flushNow() {
        if (!autoSaveEnabled || isLoadingConfig) {
            return;
        }
        dirtyAt = 0L;
        saveCurrentConfig();
    }

    public void loadConfig(String configName) {

        isLoadingConfig = true;
        try {
            loadConfigInternal(configName);
        } finally {

            dirtyAt = 0L;
            isLoadingConfig = false;
        }
    }

    private void loadConfigInternal(String configName) {

        if (currentConfig != null && currentConfig.exists() && !currentConfigName.equals(configName)) {
            save(currentConfig);
        }

        File configFile = getConfigFile(configName);

        applyInCodeDefaults();

        JsonObject config = readConfigFile(configFile);
        File backupFile = new File(configFile.getParentFile(), configFile.getName() + ".bak");
        if (config == null && backupFile.exists()) {
            System.err.println("[Phaze] primary config '" + configFile.getName()
                    + "' unreadable, falling back to .bak");
            config = readConfigFile(backupFile);
        }

        currentConfig = configFile;
        setCurrentConfigName(configName);

        if (config == null) {
            return;
        }

        applyConfigJson(config);
    }

    private void applyConfigJson(JsonObject config) {
        try {
            if (config.has("modules")) {
                JsonObject modules = config.getAsJsonObject("modules");
                for (Module module : Main.getInstance().getModuleProvider().getModules()) {
                    try {
                        loadModule(module, modules);
                    } catch (Throwable t) {
                        System.err.println("[Phaze] failed to load module '"
                                + module.getName() + "' from config: " + t);
                    }
                }
            }

            if (config.has("theme")) {
                try {
                    String theme = config.get("theme").getAsString();
                    vorga.phazeclient.implement.features.modules.client.Theme.getInstance().menuTheme.setSelected(theme);
                } catch (Throwable ignored) {}
            }

            if (config.has("blurRadius")) {
                try {
                    float blurRadius = config.get("blurRadius").getAsFloat();
                    vorga.phazeclient.implement.features.modules.client.Theme.getInstance().blurRadius.setValue(blurRadius);
                } catch (Throwable ignored) {}
            }

            double panoramaSpeed = MenuUiSettings.DEFAULT_PANORAMA_SPEED;
            if (config.has("menuPanoramaSpeed")) {
                try {
                    panoramaSpeed = config.get("menuPanoramaSpeed").getAsDouble();
                } catch (Throwable ignored) {}
            }

            int guiFpsLimit = MenuUiSettings.DEFAULT_GUI_FPS_LIMIT;
            if (config.has("menuGuiFpsLimit")) {
                try {
                    guiFpsLimit = config.get("menuGuiFpsLimit").getAsInt();
                } catch (Throwable ignored) {}
            }

            float guiScale = MenuUiSettings.DEFAULT_GUI_SCALE;
            if (config.has("menuGuiScale")) {
                try {
                    guiScale = config.get("menuGuiScale").getAsFloat();
                } catch (Throwable ignored) {}
            }

            String panoramaPreset = MenuUiSettings.DEFAULT_PANORAMA_PRESET_ID;
            if (config.has("menuPanoramaPreset")) {
                try {
                    panoramaPreset = config.get("menuPanoramaPreset").getAsString();
                } catch (Throwable ignored) {}
            }

            boolean customMainMenuEnabled = MenuUiSettings.DEFAULT_CUSTOM_MAIN_MENU_ENABLED;
            if (config.has("customMainMenuEnabled")) {
                try {
                    customMainMenuEnabled = config.get("customMainMenuEnabled").getAsBoolean();
                } catch (Throwable ignored) {}
            }

            int panoramaSpeedScaleVersion = 1;
            if (config.has("menuPanoramaSpeedScaleVersion")) {
                try {
                    panoramaSpeedScaleVersion = config.get("menuPanoramaSpeedScaleVersion").getAsInt();
                } catch (Throwable ignored) {}
            }

            if (panoramaSpeedScaleVersion >= MenuUiSettings.PANORAMA_SPEED_SCALE_VERSION) {
                MenuUiSettings.getInstance().applyConfig(panoramaSpeed, guiFpsLimit, guiScale, panoramaPreset, customMainMenuEnabled);
            } else if (panoramaSpeedScaleVersion == 2) {
                MenuUiSettings.getInstance().applyLegacyScaleV2Config(panoramaSpeed, guiFpsLimit, panoramaPreset, customMainMenuEnabled);
            } else {
                MenuUiSettings.getInstance().applyLegacyScaleV1Config(panoramaSpeed, guiFpsLimit, panoramaPreset, customMainMenuEnabled);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void loadConfigInternalLegacyApply(JsonObject config) {
        try {
            if (config.has("modules")) {
                JsonObject modules = config.getAsJsonObject("modules");
                for (Module module : Main.getInstance().getModuleProvider().getModules()) {

                    try {
                        loadModule(module, modules);
                    } catch (Throwable t) {
                        System.err.println("[Phaze] failed to load module '"
                                + module.getName() + "' from config: " + t);
                    }
                }
            }

            if (config.has("theme")) {
                try {
                    String theme = config.get("theme").getAsString();
                    vorga.phazeclient.implement.features.modules.client.Theme.getInstance().menuTheme.setSelected(theme);
                } catch (Throwable ignored) {}
            }

            if (config.has("blurRadius")) {
                try {
                    float blurRadius = config.get("blurRadius").getAsFloat();
                    vorga.phazeclient.implement.features.modules.client.Theme.getInstance().blurRadius.setValue(blurRadius);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {

            t.printStackTrace();
        }
    }

    private void applyInCodeDefaults() {
        for (Module module : Main.getInstance().getModuleProvider().getModules()) {

            if (module.isState() != module.isDefaultStateOn()) {
                module.switchState();
            }
            module.setKey(0);

            resetSettingsRecursive(module.settings());
            if (module instanceof RectHudModule rectHudModule) {
                rectHudModule.resetHudTransform();
            } else if (module instanceof ArmorHud armorHud) {
                armorHud.resetHudTransform();
            }
        }

        vorga.phazeclient.implement.features.modules.client.Theme.getInstance().menuTheme.setSelected("Lunar Blue");
        vorga.phazeclient.implement.features.modules.client.Theme.getInstance().blurRadius.setValue(5.0F);
        MenuUiSettings.getInstance().resetToDefaults();
    }

    private void resetSettingsRecursive(List<? extends Setting> settings) {
        if (settings == null) return;
        for (Setting setting : settings) {
            try {
                setting.reset();
                if (setting instanceof GroupSetting group) {
                    resetSettingsRecursive(group.getSubSettings());
                }
            } catch (Throwable ignored) {

            }
        }
    }

    private JsonObject readConfigFile(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try (Reader reader = new FileReader(file)) {
            JsonObject parsed = GSON.fromJson(reader, JsonObject.class);
            return parsed;
        } catch (JsonSyntaxException | IOException e) {

            System.err.println("[Phaze] could not parse config file '"
                    + file.getAbsolutePath() + "': " + e);
            return null;
        }
    }

    private void loadModule(Module module, JsonObject modules) {
        boolean hasModuleData = modules.has(module.getName())
                && modules.get(module.getName()).isJsonObject();
        JsonObject moduleData = hasModuleData ? modules.getAsJsonObject(module.getName()) : null;

        if (module.isShowEnable()) {

            boolean enabled = hasModuleData && moduleData.has("enabled")
                    ? moduleData.get("enabled").getAsBoolean()
                    : module.isDefaultStateOn();
            module.setState(enabled);
        }

        if (hasModuleData && moduleData.has("key")) {
            module.setKey(moduleData.get("key").getAsInt());
        }

        if (hasModuleData && moduleData.has("settings") && moduleData.get("settings").isJsonObject()) {
            JsonObject settings = moduleData.getAsJsonObject("settings");
            for (Setting setting : module.settings()) {
                try {
                    deserializeSetting(setting, settings);
                } catch (Exception ignored) {

                }
            }
        }

        if (module instanceof RectHudModule rectHudModule) {
            if (!hasModuleData) {
                rectHudModule.resetHudTransform();
            } else {
                if (moduleData.has("hud_x")) {
                    rectHudModule.setHudX(moduleData.get("hud_x").getAsFloat());
                } else if (moduleData.has("hud_x_ratio")) {
                    rectHudModule.setHudXRatio(moduleData.get("hud_x_ratio").getAsFloat());
                } else {
                    rectHudModule.resetHudTransform();
                }
                if (moduleData.has("hud_y")) {
                    rectHudModule.setHudY(moduleData.get("hud_y").getAsFloat());
                } else if (moduleData.has("hud_y_ratio")) {
                    rectHudModule.setHudYRatio(moduleData.get("hud_y_ratio").getAsFloat());
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
                } else if (moduleData.has("hud_x_ratio")) {
                    armorHud.setHudXRatio(moduleData.get("hud_x_ratio").getAsFloat());
                } else {
                    armorHud.resetHudTransform();
                }
                if (moduleData.has("hud_y")) {
                    armorHud.setHudY(moduleData.get("hud_y").getAsFloat());
                } else if (moduleData.has("hud_y_ratio")) {
                    armorHud.setHudYRatio(moduleData.get("hud_y_ratio").getAsFloat());
                }
                if (moduleData.has("hud_scale")) {
                    armorHud.setHudScale(moduleData.get("hud_scale").getAsFloat());
                }
            }
        }
    }

    public void loadCurrentConfig() {
        loadConfig(currentConfigName);
    }

    public String[] getConfigList() {
        File[] files = configsDir.listFiles((dir, name) -> name.endsWith(".Phaze"));

        Set<String> uniqueNames = new LinkedHashSet<>();

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

    public boolean isImportedConfig(String configName) {
        File f = getConfigFile(configName);
        if (!f.exists()) return false;
        try (Reader r = new FileReader(f)) {
            JsonObject obj = GSON.fromJson(r, JsonObject.class);
            return obj != null
                    && obj.has("imported")
                    && obj.get("imported").getAsBoolean();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void deleteConfig(String configName) {
        if (configName == null || configName.isEmpty()) return;

        File configFile = getConfigFile(configName);
        File backupFile = new File(configFile.getParentFile(), configFile.getName() + ".bak");
        boolean wasActive = currentConfigName.equalsIgnoreCase(configName);

        if (wasActive) {
            currentConfig = null;
        }

        try {
            Files.deleteIfExists(configFile.toPath());
        } catch (IOException e) {
            System.err.println("[Phaze] Failed to delete config '"
                    + configFile.getAbsolutePath() + "': " + e.getMessage());
        }
        try {
            Files.deleteIfExists(backupFile.toPath());
        } catch (IOException e) {
            System.err.println("[Phaze] Failed to delete .bak '"
                    + backupFile.getAbsolutePath() + "': " + e.getMessage());
        }

        if (wasActive) {
            String fallback = "default";

            if (!getConfigFile(fallback).exists()) {
                String[] remaining = getConfigList();
                for (String name : remaining) {
                    if (!name.equalsIgnoreCase(configName)) {
                        fallback = name;
                        break;
                    }
                }
            }
            loadConfig(fallback);
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

    private static void serializeSetting(Setting setting, JsonObject target) {
        if (setting == null || !setting.isSaveToConfig()) {
            return;
        }

        String key = setting.getRawName();

        switch (setting) {
            case BooleanSetting booleanSetting -> target.addProperty(key, booleanSetting.isValue());
            case ValueSetting valueSetting -> target.addProperty(key, valueSetting.getValue());
            case TextSetting textSetting -> {
                String value = textSetting.getText();
                if (value != null) {

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
            case ItemPickerSetting itemPickerSetting -> {
                if (itemPickerSetting.isCustomPicker() && !itemPickerSetting.isActive()) {
                    target.addProperty(key, false);
                    return;
                }
                JsonObject itemObject = new JsonObject();
                itemObject.addProperty("active", itemPickerSetting.isActive());
                itemObject.addProperty("enabled", itemPickerSetting.isEnabled());
                itemObject.addProperty("highlightColor", itemPickerSetting.getHighlightColor());
                itemObject.addProperty("itemId", itemPickerSetting.getItemId());
                itemObject.addProperty("displayName", itemPickerSetting.getDisplayName());
                itemObject.addProperty("matchName", itemPickerSetting.getMatchName());
                target.add(key, itemObject);
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

            }
        }
    }

    private static final Map<String, String[]> LEGACY_KEY_ALIASES = Map.of(

            "Message Animation", new String[]{"Chat Smooth Scroll"},
            "Message Animation Speed", new String[]{"Chat Scroll Speed"}
    );

    private static void deserializeSetting(Setting setting, JsonObject source) {
        if (setting == null || !setting.isSaveToConfig()) {
            return;
        }

        String key = setting.getRawName();
        if (!source.has(key)) {
            String[] aliases = LEGACY_KEY_ALIASES.get(key);
            if (aliases != null) {
                for (String alias : aliases) {
                    if (source.has(alias)) {
                        key = alias;
                        break;
                    }
                }
            }
            if (!source.has(key)) {
                return;
            }
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

                selected.removeIf(s -> s.isEmpty() || !multiSelectSetting.getList().contains(s));
                multiSelectSetting.setSelected(selected);
            }
            case ItemPickerSetting itemPickerSetting -> {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
                    itemPickerSetting.setEnabled(element.getAsBoolean());
                    return;
                }
                if (!element.isJsonObject()) {
                    return;
                }
                JsonObject itemObject = element.getAsJsonObject();
                String itemId = itemObject.has("itemId") ? itemObject.get("itemId").getAsString() : "";
                String displayName = itemObject.has("displayName") ? itemObject.get("displayName").getAsString() : "";
                String matchName = itemObject.has("matchName") ? itemObject.get("matchName").getAsString() : "";
                boolean enabled = itemObject.has("enabled")
                        ? itemObject.get("enabled").getAsBoolean()
                        : itemPickerSetting.isEnabled();
                int highlightColor = itemObject.has("highlightColor")
                        ? itemObject.get("highlightColor").getAsInt()
                        : itemPickerSetting.getHighlightColor();
                boolean active = itemObject.has("active")
                        ? itemObject.get("active").getAsBoolean()
                        : !itemId.isBlank();
                itemPickerSetting.setSerializedState(active, itemId, displayName, matchName, enabled, highlightColor);
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

            }
        }
    }
}
