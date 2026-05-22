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
    
    /**
     * Persists the current in-memory state to {@code configFile}.
     *
     * <p>Writes are atomic-with-backup: we never overwrite the target
     * file in place because a crash / Alt+F4 / kill mid-{@code write}
     * would leave a half-written JSON that {@link Gson} chokes on at
     * the next startup, which used to silently reset every setting.
     * Sequence:
     * <ol>
     *   <li>Serialize the whole config tree to a fresh {@code .tmp}
     *       sibling.</li>
     *   <li>Rotate the existing target into {@code .bak} (so a
     *       corrupted next-write or accidental delete still has a
     *       previous-good fallback that {@link #loadConfigInternal}
     *       can recover from).</li>
     *   <li>Atomic-move the temp on top of the target. If the move
     *       isn't atomic on the host filesystem (rare on NTFS, but
     *       e.g. on FAT32) we fall back to a non-atomic replace -
     *       still safer than the original write-in-place.</li>
     * </ol>
     */
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
                try {
                    serializeSetting(setting, settings);
                } catch (Throwable t) {
                    // One bad setting must not strand the whole save.
                    // Skip silently - the next load will fall back to
                    // the in-code default for this single key.
                    System.err.println("[Phaze] failed to serialize setting " + setting.getName() + " of " + module.getName() + ": " + t);
                }
            }
            moduleData.add("settings", settings);

            modules.add(module.getName(), moduleData);
        }

        config.add("modules", modules);
        vorga.phazeclient.implement.features.modules.client.Theme theme = vorga.phazeclient.implement.features.modules.client.Theme.getInstance();
        config.addProperty("theme", theme.menuTheme.getSelected());
        config.addProperty("blurRadius", theme.blurRadius.getValue());

        Path target = configFile.toPath();
        Path tmp = target.resolveSibling(configFile.getName() + ".tmp");
        Path bak = target.resolveSibling(configFile.getName() + ".bak");

        try {
            // 1. write to temp
            try (FileWriter writer = new FileWriter(tmp.toFile())) {
                GSON.toJson(config, writer);
            }
            // 2. rotate previous-good into .bak (best-effort)
            if (Files.exists(target)) {
                try {
                    Files.move(target, bak, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                    // .bak rotation failing is not fatal - the atomic
                    // move below still gives us a consistent target.
                }
            }
            // 3. atomic publish
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicFailed) {
                // Fallback for filesystems that don't support atomic
                // move (FAT32, some network mounts). Still safer than
                // overwriting the original in place.
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            e.printStackTrace();
            // Clean up the temp so a retry doesn't trip over stale data.
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    public void saveConfig(String configName) {
        save(getConfigFile(configName));
    }

    /**
     * Serialize the current in-memory configuration into a portable
     * shareable string. The format is:
     *
     *   PHAZE1:<base64(gzip(JSON))>
     *
     * <p>The {@code PHAZE1:} prefix is a magic marker so a clipboard
     * paste with foreign text can be rejected fast in {@link
     * #importFromString}; gzip+base64 keeps the payload short enough
     * to fit in a Discord message (typical config compresses 5-10x).
     * The JSON body is the same shape as what {@link #save} writes to
     * disk, so anything saveable on disk is also shareable.
     *
     * <p>Returns {@code null} if the serialization fails - the caller
     * should fall back to displaying an error rather than feeding a
     * garbage string to the clipboard.
     */
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

    /**
     * Decode a {@code PHAZE1:...} share-string and apply it as the
     * current config (after persisting the result to a fresh
     * {@code imported_<n>} entry so the user doesn't overwrite their
     * existing setup by accident).
     *
     * <p>Returns the name of the new config on success, or {@code null}
     * if the input wasn't a valid share-string. The active config is
     * then switched to the imported one.
     */
    public String importFromString(String shareString) {
        if (shareString == null) return null;
        String trimmed = shareString.trim();
        // Tolerate CRLF / surrounding quotes from clipboard pastes.
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
            // Persist as a brand-new config so we don't overwrite
            // anything the user already had on disk. Pick a slot that
            // doesn't already exist on disk.
            String name = nextImportedName();
            File target = getConfigFile(name);
            try (FileWriter writer = new FileWriter(target)) {
                GSON.toJson(parsed, writer);
            }
            // Switch to the new config; loadConfig handles the
            // dirty-suppression / autosave-redirect interactions.
            loadConfig(name);
            return name;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    /**
     * Pick the next free {@code imported_<n>} slot. The simple
     * sequential search is fine because a user is unlikely to ever
     * cross the double-digit imports threshold and the directory
     * scan is one stat per attempted name.
     */
    private String nextImportedName() {
        for (int i = 1; i < 1000; i++) {
            String candidate = i == 1 ? "imported" : "imported_" + i;
            if (!getConfigFile(candidate).exists()) {
                return candidate;
            }
        }
        return "imported_" + System.currentTimeMillis();
    }

    /**
     * Build the same JSON tree {@link #save} writes, but return it
     * directly instead of serializing to disk. Shared by the regular
     * save path and the export-to-string path so the two never drift
     * apart.
     */
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
        return config;
    }
    
    /**
     * Persists the in-memory state to whatever config is currently active.
     *
     * <p>Each named config (including {@code default}) is treated as a
     * regular file: auto-save writes straight into the active file and
     * never silently swaps the active config behind the user's back.
     * The previous "default redirects to autosave" trick caused two
     * separate bugs:
     * <ul>
     *   <li>Settings appeared to merge between configs because changes
     *       made on {@code default} kept being written to {@code autosave}
     *       and bled into whatever happened to load that file next.</li>
     *   <li>Switching to {@code default} silently flipped the active
     *       config back to {@code autosave} on the very next setting
     *       change.</li>
     * </ul>
     * If the user wants a true factory reset, they can delete their
     * {@code default.Phaze} file - the in-code defaults take over.
     */
    public void saveCurrentConfig() {
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

    /**
     * Immediate save bypass for events that the user expects to persist
     * even if the game crashes within the debounce window. Module
     * enable/disable toggles and bind reassignments are the canonical
     * cases: a user who toggles a module then Alt+F4s a frame later
     * (e.g. because the module's just-enabled effect made the game
     * unresponsive) would otherwise lose the toggle. Slider drags are
     * still debounced via {@link #markDirty} - they fire dozens of
     * notifications per second and immediate-saving each one would
     * write the config dozens of times per drag.
     */
    public void flushNow() {
        if (!autoSaveEnabled) {
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
        // Save current config before loading new one, only when switching
        // to a different config. Skipped when target == current so the
        // initial load on startup doesn't write the in-code defaults
        // straight back over a user's freshly-loaded file.
        if (currentConfig != null && currentConfig.exists() && !currentConfigName.equals(configName)) {
            save(currentConfig);
        }

        File configFile = getConfigFile(configName);

        // Reset every module / hud / theme to its in-code default BEFORE
        // applying the new file. Without this, modules that the new file
        // doesn't mention would keep whatever state the previous config
        // left them in - which manifested as "configs merge" when
        // switching between profiles. This mirrors soup's onUnload step.
        applyInCodeDefaults();

        // Try the primary file first; on a corrupt-or-missing read,
        // fall back to the .bak rotation save() leaves behind.
        JsonObject config = readConfigFile(configFile);
        File backupFile = new File(configFile.getParentFile(), configFile.getName() + ".bak");
        if (config == null && backupFile.exists()) {
            System.err.println("[Phaze] primary config '" + configFile.getName()
                    + "' unreadable, falling back to .bak");
            config = readConfigFile(backupFile);
        }

        // Update the active-config pointer regardless of whether the
        // file existed - if the user is switching to a config name
        // that has no .Phaze yet (e.g. a fresh "default" on first
        // launch), we still want subsequent saves to land on that name
        // instead of writing into the previous active config.
        currentConfig = configFile;
        setCurrentConfigName(configName);

        if (config == null) {
            return; // nothing to apply; in-code defaults stay in place
        }

        try {
            if (config.has("modules")) {
                JsonObject modules = config.getAsJsonObject("modules");
                for (Module module : Main.getInstance().getModuleProvider().getModules()) {
                    // Wrap every module's load in its own try so a
                    // single broken module entry can't strand all the
                    // ones that come after it in the iteration order.
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
            // Defensive: anything that escapes the per-module try (e.g.
            // a structural cast on the top-level "modules" object) is
            // logged but not rethrown.
            t.printStackTrace();
        }
    }

    /**
     * Resets every module / HUD / theme back to its in-code default.
     * Called as the first step of {@link #loadConfigInternal} so the
     * about-to-be-applied JSON only adds back the deltas the saved
     * config explicitly captured - everything not mentioned in the
     * file ends up at its in-code default rather than carrying over
     * from the previously-active config.
     *
     * <p>Mirrors soup's {@code onUnload} pass + module init defaults:
     * disables every currently-enabled module, resets HUD positions,
     * and reverts theme name + blur radius to the boot values.
     */
    private void applyInCodeDefaults() {
        for (Module module : Main.getInstance().getModuleProvider().getModules()) {
            // Toggle off any module that's currently on. switchState()
            // routes through the same notifyChange path a manual click
            // would take, which is what we want - keybinds, listeners,
            // overlay registrations all see the disable.
            if (module.isState()) {
                module.switchState();
            }
            module.setKey(0);
            // Best-effort settings reset: walk every setting and ask
            // it to revert to its declared default. Settings that
            // don't expose a reset API are left as-is - in practice
            // most user-visible settings extend a base that does.
            for (Setting setting : module.settings()) {
                try {
                    resetSettingToDefault(setting);
                } catch (Throwable ignored) {
                    // Skip a single bad setting without taking the
                    // rest of the module reset down with it.
                }
            }
            if (module instanceof RectHudModule rectHudModule) {
                rectHudModule.resetHudTransform();
            } else if (module instanceof ArmorHud armorHud) {
                armorHud.resetHudTransform();
            }
        }
        // Theme + blur back to boot defaults.
        vorga.phazeclient.implement.features.modules.client.Theme.getInstance().menuTheme.setSelected("Lunar Blue");
        vorga.phazeclient.implement.features.modules.client.Theme.getInstance().blurRadius.setValue(5.0F);
    }

    /**
     * Best-effort reset of a single setting to its declared default
     * value. Each setting type carries the default in a different
     * field name; we sniff via {@code reflect} so this stays
     * decoupled from concrete API additions. Failures are swallowed
     * because settings that don't expose a default reset still leave
     * the existing in-memory value, which is fine - the next loaded
     * file will overwrite it anyway.
     */
    private void resetSettingToDefault(Setting setting) {
        if (setting == null || !setting.isSaveToConfig()) return;
        try {
            // Most settings store the boot default in a {@code defaultValue}
            // field; reflection avoids a per-type switch and stays
            // forward-compat when new setting types are added.
            java.lang.reflect.Field f = null;
            for (Class<?> c = setting.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    f = c.getDeclaredField("defaultValue");
                    break;
                } catch (NoSuchFieldException ignored) {}
            }
            if (f == null) return;
            f.setAccessible(true);
            Object def = f.get(setting);
            if (def == null) return;
            switch (setting) {
                case BooleanSetting b -> b.setValue((Boolean) def);
                case ValueSetting v -> v.setValue(((Number) def).floatValue());
                case TextSetting t -> t.setText(def.toString());
                case BindSetting b -> b.setKey(((Number) def).intValue());
                case ColorSetting c -> c.setColor(((Number) def).intValue());
                case SelectSetting s -> s.setSelected(def.toString());
                case GroupSetting g -> {
                    g.setValue((Boolean) def);
                    for (Setting sub : g.getSubSettings()) resetSettingToDefault(sub);
                }
                default -> {}
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Reads and parses a single config file into a {@link JsonObject},
     * or returns {@code null} if the file is missing, empty, or
     * unparseable. Used by {@link #loadConfigInternal} so the caller
     * can transparently retry against the {@code .bak} sibling.
     */
    private JsonObject readConfigFile(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try (Reader reader = new FileReader(file)) {
            JsonObject parsed = GSON.fromJson(reader, JsonObject.class);
            return parsed; // may be null if file was empty - caller treats that as no-op
        } catch (JsonSyntaxException | IOException e) {
            // Half-written / truncated / hand-corrupted file. Let the
            // caller fall back to .bak. We log so the operator can see
            // why their configs aren't applying.
            System.err.println("[Phaze] could not parse config file '"
                    + file.getAbsolutePath() + "': " + e);
            return null;
        }
    }

    /**
     * Applies a single module's data block out of the {@code modules}
     * top-level object. Extracted from the inline body of
     * {@link #loadConfigInternal} so each module's load can be wrapped
     * in its own try/catch up there - without that isolation, a single
     * malformed module entry would short-circuit the iteration and
     * leave every later module on its in-code defaults.
     */
    private void loadModule(Module module, JsonObject modules) {
        boolean hasModuleData = modules.has(module.getName())
                && modules.get(module.getName()).isJsonObject();
        JsonObject moduleData = hasModuleData ? modules.getAsJsonObject(module.getName()) : null;

        if (module.isShowEnable()) {
            boolean enabled = hasModuleData && moduleData.has("enabled") && moduleData.get("enabled").getAsBoolean();
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
                    // Skip a single bad setting without taking the rest
                    // of the module down with it.
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
    
    /**
     * Removes a config from disk and from the listing. If the user
     * deletes the currently-active config, we transparently switch
     * to {@code default} (or the first available config when the
     * default has also been removed) and load it - this mirrors
     * soup's deleteConfig flow and stops the GUI from showing a row
     * that no longer exists on disk.
     *
     * <p>The literal name {@code "default"} is intentionally
     * deletable: it's just a regular file. Deleting it just makes
     * the next save recreate it from the in-code defaults.
     */
    public void deleteConfig(String configName) {
        if (configName == null || configName.isEmpty()) return;

        File configFile = getConfigFile(configName);
        File backupFile = new File(configFile.getParentFile(), configFile.getName() + ".bak");
        boolean wasActive = currentConfigName.equalsIgnoreCase(configName);

        // Delete primary + .bak so the listing doesn't keep showing a
        // stale entry. {@code File.delete} returns false on missing
        // files, which is fine - we just want to ensure neither lives
        // after this call.
        if (configFile.exists()) configFile.delete();
        if (backupFile.exists()) backupFile.delete();

        // If we deleted what we were actively saving to, fall back to
        // a real config so subsequent setting changes have a stable
        // file to land in.
        if (wasActive) {
            String fallback = "default";
            // If the user just deleted "default" too, pick whatever's
            // first in the listing; if even that is empty, leave the
            // active pointer on default so the next save creates it.
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
     * Per-setting legacy aliases. When a setting's user-facing name is
     * renamed in code we still need to honour the old key on disk so
     * existing user configs round-trip without resetting to default.
     * The lookup is keyed by the CURRENT name and lists every prior
     * name in deprecation order; first match wins.
     *
     * <p>Add a new entry here whenever a setting's display label
     * changes, then drop the entry once enough release cycles have
     * passed that everyone's configs have been re-saved with the new
     * key.
     */
    private static final Map<String, String[]> LEGACY_KEY_ALIASES = Map.of(
            // Animations module: Chat Smooth Scroll -> Message Animation
            // (the toggle now describes only the new-message slide-in;
            // the smooth-scroll port was removed in commit e0233a4).
            "Message Animation", new String[]{"Chat Smooth Scroll"},
            "Message Animation Speed", new String[]{"Chat Scroll Speed"}
    );

    /**
     * Pulls a single {@link Setting} value back out of the supplied
     * JSON object, matching the encoding produced by
     * {@link #serializeSetting(Setting, JsonObject)}. Returns silently
     * when the key is absent or the setting opts out of persistence.
     *
     * <p>Looks up {@link #LEGACY_KEY_ALIASES} when the current key is
     * missing so renames don't reset existing user configs.
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
