package vorga.phazeclient.implement.menu.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.feature.module.setting.implement.*;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.base.QuickImports;
import vorga.phazeclient.implement.features.modules.hud.ArmorHud;
import vorga.phazeclient.implement.features.modules.hud.RectHudModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class MenuProfileManager implements QuickImports {
    private static final String DEFAULT_PROFILE_NAME = "DEFAULT";
    private static final MenuProfileManager INSTANCE = new MenuProfileManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<String> profiles = new ArrayList<>();

    @Getter
    private String activeProfile = DEFAULT_PROFILE_NAME;

    private boolean initialized = false;
    private java.nio.file.Path activeProfileFile;

    private MenuProfileManager() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        Path root = client != null ? client.runDirectory.toPath() : Path.of(".");
        activeProfileFile = root.resolve("Phaze").resolve("files").resolve("current_config");
    }

    public static MenuProfileManager getInstance() {
        return INSTANCE;
    }

    public synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }

        loadProfilesFromDisk();
        loadActiveProfile();
        
        System.out.println("[MenuProfileManager] Loaded profiles: " + profiles);
        System.out.println("[MenuProfileManager] Active profile before check: " + activeProfile);
        
        if (profiles.isEmpty()) {
            profiles.add(DEFAULT_PROFILE_NAME);
            activeProfile = DEFAULT_PROFILE_NAME;
            saveProfile(DEFAULT_PROFILE_NAME);
            System.out.println("[MenuProfileManager] No profiles found, created DEFAULT");
        } else {
            sortProfiles();
            activeProfile = findExistingProfile(activeProfile);
            System.out.println("[MenuProfileManager] Active profile after findExisting: " + activeProfile);
            if (activeProfile == null) {
                activeProfile = profiles.getFirst();
                System.out.println("[MenuProfileManager] Active profile not found, using first: " + activeProfile);
            }
        }

        System.out.println("[MenuProfileManager] Loading profile settings: " + activeProfile);
        loadProfile(activeProfile);
        Main.getInstance().getConfigManager().setCurrentConfigName(activeProfile);

        initialized = true;
    }

    private void loadActiveProfile() {
        try {
            if (Files.exists(activeProfileFile)) {
                String content = Files.readString(activeProfileFile, StandardCharsets.UTF_8);
                System.out.println("[MenuProfileManager] Loaded active profile from file: " + content);
                if (content != null && !content.trim().isEmpty()) {
                    activeProfile = sanitizeProfileName(content.trim());
                    System.out.println("[MenuProfileManager] Sanitized active profile: " + activeProfile);
                }
            } else {
                System.out.println("[MenuProfileManager] Active profile file does not exist: " + activeProfileFile);
            }
        } catch (IOException e) {
            System.out.println("[MenuProfileManager] Error loading active profile: " + e.getMessage());
        }
    }

    private void saveActiveProfile() {
        try {
            Files.createDirectories(activeProfileFile.getParent());
            Files.writeString(
                    activeProfileFile,
                    activeProfile,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException ignored) {
        }
    }

    public synchronized List<String> getProfiles() {
        ensureInitialized();
        return List.copyOf(profiles);
    }

    public synchronized String createNewProfile() {
        ensureInitialized();
        String name = nextNewProfileName();
        saveProfile(name);
        activeProfile = name;
        saveActiveProfile();
        return name;
    }

    public synchronized boolean selectProfile(String profileName) {
        ensureInitialized();
        String existing = findExistingProfile(profileName);
        if (existing == null) {
            return false;
        }

        saveCurrentProfile();
        if (loadProfile(existing)) {
            activeProfile = existing;
            saveActiveProfile();
            Main.getInstance().getConfigManager().setCurrentConfigName(activeProfile);
            return true;
        }
        return false;
    }

    public synchronized boolean renameProfile(String oldName, String newName) {
        ensureInitialized();

        String existingOld = findExistingProfile(oldName);
        if (existingOld == null) {
            return false;
        }

        String sanitizedNew = sanitizeProfileName(newName);
        if (sanitizedNew.isBlank()) {
            return false;
        }

        String existingTarget = findExistingProfile(sanitizedNew);
        if (existingTarget != null && !existingTarget.equalsIgnoreCase(existingOld)) {
            return false;
        }

        Path oldPath = profilePath(existingOld);
        Path newPath = profilePath(sanitizedNew);
        JsonObject root = readProfile(oldPath);
        if (root == null) {
            root = serializeCurrentState(existingOld);
        }

        root.addProperty("profileName", sanitizedNew);
        writeProfile(newPath, root);

        if (!oldPath.equals(newPath)) {
            try {
                Files.deleteIfExists(oldPath);
            } catch (IOException ignored) {
            }
        }

        profiles.removeIf(profile -> profile.equalsIgnoreCase(existingOld));
        profiles.add(sanitizedNew);
        sortProfiles();

        if (activeProfile != null && activeProfile.equalsIgnoreCase(existingOld)) {
            activeProfile = sanitizedNew;
            saveActiveProfile();
        }
        return true;
    }

    public synchronized boolean deleteProfile(String profileName) {
        ensureInitialized();

        String existing = findExistingProfile(profileName);
        if (existing == null) {
            return false;
        }

        // Prevent deletion of DEFAULT profile
        if (existing.equalsIgnoreCase(DEFAULT_PROFILE_NAME)) {
            return false;
        }

        // Prevent deletion of active profile
        if (activeProfile != null && activeProfile.equalsIgnoreCase(existing)) {
            return false;
        }

        Path path = profilePath(existing);
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }

        profiles.removeIf(profile -> profile.equalsIgnoreCase(existing));
        sortProfiles();

        return true;
    }

    public synchronized void saveCurrentProfile() {
        ensureInitialized();
        String target = activeProfile == null ? DEFAULT_PROFILE_NAME : activeProfile;
        saveProfile(target);
    }

    public synchronized String getDefaultProfileName() {
        return DEFAULT_PROFILE_NAME;
    }

    private void loadProfilesFromDisk() {
        profiles.clear();
        try {
            Files.createDirectories(profilesDirectory());
            try (var stream = Files.list(profilesDirectory())) {
                stream.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".profile"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .forEach(path -> {
                            JsonObject root = readProfile(path);
                            String profileName = root != null && root.has("profileName")
                                    ? sanitizeProfileName(root.get("profileName").getAsString())
                                    : stripExtension(path.getFileName().toString());

                            if (!profileName.isBlank() && findExistingProfile(profileName) == null) {
                                profiles.add(profileName);
                            }
                        });
            }
        } catch (IOException ignored) {
        }
    }

    private void saveProfile(String profileName) {
        String normalizedName = sanitizeProfileName(profileName);
        JsonObject root = serializeCurrentState(normalizedName);
        writeProfile(profilePath(normalizedName), root);

        if (findExistingProfile(normalizedName) == null) {
            profiles.add(normalizedName);
            sortProfiles();
        }
    }

    private boolean loadProfile(String profileName) {
        JsonObject root = readProfile(profilePath(profileName));
        if (root == null) {
            return false;
        }

        JsonObject modulesObject = root.has("modules") && root.get("modules").isJsonObject()
                ? root.getAsJsonObject("modules")
                : root;

        for (Module module : Main.getInstance().getModuleProvider().getModules()) {
            boolean hasModuleData = modulesObject.has(module.getIdentifier()) && modulesObject.get(module.getIdentifier()).isJsonObject();
            JsonObject moduleData = hasModuleData ? modulesObject.getAsJsonObject(module.getIdentifier()) : null;

            if (module.isShowEnable()) {
                boolean enabled = hasModuleData && moduleData.has("enabled") && moduleData.get("enabled").getAsBoolean();
                module.setStateSilent(enabled);
            }

            if (!hasModuleData) {
                if (module instanceof RectHudModule rectHudModule) {
                    rectHudModule.resetHudTransform();
                } else if (module instanceof ArmorHud armorHud) {
                    armorHud.resetHudTransform();
                }
                continue;
            }

            if (moduleData.has("bind")) {
                module.setKey(moduleData.get("bind").getAsInt());
            }

            for (Setting setting : module.settings()) {
                try {
                    loadSetting(setting, moduleData);
                } catch (Exception ignored) {
                }
            }

            if (module instanceof RectHudModule rectHudModule) {
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
            } else if (module instanceof ArmorHud armorHud) {
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

        return true;
    }

    private JsonObject serializeCurrentState(String profileName) {
        JsonObject root = new JsonObject();
        root.addProperty("profileName", profileName);
        root.addProperty("savedAt", Instant.now().toString());

        JsonObject modulesObject = new JsonObject();
        for (Module module : Main.getInstance().getModuleProvider().getModules()) {
            JsonObject moduleData = new JsonObject();
            moduleData.addProperty("enabled", module.isState());
            moduleData.addProperty("bind", module.getKey());

            for (Setting setting : module.settings()) {
                saveSetting(setting, moduleData);
            }

            if (module instanceof RectHudModule rectHudModule) {
                moduleData.addProperty("hud_x", rectHudModule.getHudX());
                moduleData.addProperty("hud_y", rectHudModule.getHudY());
                moduleData.addProperty("hud_scale", rectHudModule.getHudScale());
            } else if (module instanceof ArmorHud armorHud) {
                moduleData.addProperty("hud_x", armorHud.getHudX());
                moduleData.addProperty("hud_y", armorHud.getHudY());
                moduleData.addProperty("hud_scale", armorHud.getHudScale());
            }

            modulesObject.add(module.getIdentifier(), moduleData);
        }

        root.add("modules", modulesObject);
        return root;
    }

    private void saveSetting(Setting setting, JsonObject moduleData) {
        if (!setting.isSaveToConfig()) {
            return;
        }

        String key = setting.getNameKey();
        switch (setting) {
            case BooleanSetting booleanSetting -> moduleData.addProperty(key, booleanSetting.isValue());
            case ValueSetting valueSetting -> moduleData.addProperty(key, valueSetting.getValue());
            case TextSetting textSetting -> {
                String value = textSetting.getText();
                value = value.replace(" ", "%%").replace("/", "++");
                moduleData.addProperty(key, value);
            }
            case BindSetting bindSetting -> moduleData.addProperty(key, bindSetting.getKey());
            case ColorSetting colorSetting -> moduleData.addProperty(key, colorSetting.getColor());
            case SelectSetting selectSetting -> moduleData.addProperty(key, selectSetting.getSelected());
            case MultiSelectSetting multiSelectSetting -> moduleData.addProperty(key, String.join(",", multiSelectSetting.getSelected()));
            case MultiColorSetting multiColor -> {
                JsonObject colorObject = new JsonObject();
                colorObject.addProperty("selectedColorIndex", multiColor.getSelectedColorIndex());

                JsonArray colorsArray = new JsonArray();
                for (ColorSetting color : multiColor.getAllColors()) {
                    colorsArray.add(color.getColor());
                }
                colorObject.add("colors", colorsArray);
                moduleData.add(key, colorObject);
            }
            case GroupSetting group -> {
                JsonObject groupObject = new JsonObject();
                groupObject.addProperty("state", group.isValue());

                for (Setting subSetting : group.getSubSettings()) {
                    saveSetting(subSetting, groupObject);
                }

                moduleData.add(key, groupObject);
            }
            default -> {
            }
        }
    }

    private void loadSetting(Setting setting, JsonObject moduleData) {
        if (!setting.isSaveToConfig()) {
            return;
        }

        String key = setting.getNameKey();
        if (!moduleData.has(key)) {
            return;
        }

        JsonElement element = moduleData.get(key);
        switch (setting) {
            case BooleanSetting booleanSetting -> booleanSetting.setValue(element.getAsBoolean());
            case ValueSetting valueSetting -> valueSetting.setValue(element.getAsFloat());
            case TextSetting textSetting -> {
                String value = element.getAsString();
                value = value.replace("%%", " ").replace("++", "/");
                textSetting.setText(value);
            }
            case BindSetting bindSetting -> bindSetting.setKey(element.getAsInt());
            case ColorSetting colorSetting -> colorSetting.setColor(element.getAsInt());
            case SelectSetting selectSetting -> selectSetting.setSelected(element.getAsString());
            case MultiSelectSetting multiSelectSetting -> {
                String value = element.getAsString();
                List<String> selected = new ArrayList<>(List.of(value.split(",")));
                selected.removeIf(s -> !multiSelectSetting.getList().contains(s));
                multiSelectSetting.setSelected(selected);
            }
            case MultiColorSetting multiColor -> {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                    int oldColor = element.getAsInt();
                    if (multiColor.getColor1() != null) {
                        multiColor.getColor1().setColor(oldColor);
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
                    for (int i = 0; i < Math.min(colorsArray.size(), colorSettings.size()); i++) {
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
                    loadSetting(subSetting, groupObject);
                }
            }
            default -> {
            }
        }
    }

    private JsonObject readProfile(Path path) {
        try {
            if (!Files.exists(path)) {
                return null;
            }
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return GSON.fromJson(content, JsonObject.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeProfile(Path path, JsonObject root) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(
                    path,
                    GSON.toJson(root),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException ignored) {
        }
    }

    private Path profilesDirectory() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        Path root = client != null ? client.runDirectory.toPath() : Path.of(".");
        return root.resolve("Phaze").resolve("configs");
    }

    private Path profilePath(String profileName) {
        return profilesDirectory().resolve(sanitizeFileName(profileName) + ".profile");
    }

    private String nextNewProfileName() {
        int index = 1;
        while (true) {
            String name = "new-profile" + index;
            if (findExistingProfile(name) == null) {
                return name;
            }
            index++;
        }
    }

    private String findExistingProfile(String name) {
        if (name == null) {
            return null;
        }

        for (String profile : profiles) {
            if (profile.equalsIgnoreCase(name)) {
                return profile;
            }
        }
        return null;
    }

    private void sortProfiles() {
        profiles.sort((first, second) -> {
            boolean firstDefault = first.equalsIgnoreCase(DEFAULT_PROFILE_NAME);
            boolean secondDefault = second.equalsIgnoreCase(DEFAULT_PROFILE_NAME);
            if (firstDefault && !secondDefault) {
                return -1;
            }
            if (!firstDefault && secondDefault) {
                return 1;
            }
            return first.compareToIgnoreCase(second);
        });
    }

    private String sanitizeProfileName(String name) {
        if (name == null) {
            return "";
        }

        String sanitized = name.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "-")
                .replaceAll("\\s+", " ");
        if (sanitized.isBlank()) {
            return "";
        }
        return sanitized;
    }

    private String sanitizeFileName(String name) {
        return sanitizeProfileName(name).replace(' ', '_');
    }

    private String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }
}
