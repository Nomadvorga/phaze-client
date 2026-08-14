package vorga.phazeclient.implement.cosmetics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import vorga.phazeclient.base.util.Lang;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.zip.ZipFile;

/**
 * Persistent state for the standalone COSMETICS page.
 *
 * <p>This deliberately does not implement Module: cosmetics are a top-level
 * client feature and should not appear in the Mods catalog or inherit module
 * keybind/toggle semantics.</p>
 */
public final class CosmeticsState {
    public static final String NONE = "None";
    public static final String WIMGS = "Wimgs";
    public static final String FLUFFY_WINGS = "Fluffy Wings";
    public static final String PHAZE_CAPE = "Phaze Cape";
    public static final String GOLDFISH_PET = "Goldfish Pet";
    public static final String BIRB_COMPANION = "Birb";
    public static final String TURTLE_HAT = "Turtle Pet";
    private static final List<BundledCape> BUNDLED_CAPES = List.of(
            new BundledCape("Phaze Cape.png", "/assets/phaze/textures/cosmetics/phaze_cape.png"),
            new BundledCape("награда 15 лет+вишня.png", "/assets/phaze/cosmetics/capes/anniversary_cherry.png"),
            new BundledCape("Old Mogang Cape.png", "/assets/phaze/cosmetics/capes/old_mogang_cape.png"),
            new BundledCape("Unused Cape 1.png", "/assets/phaze/cosmetics/capes/unused_cape_1.png"),
            new BundledCape("Turtle Shell.png", "/assets/phaze/cosmetics/capes/turtle_shell.png"),
            new BundledCape("Fauna Faire.png", "/assets/phaze/cosmetics/capes/fauna_faire.png"),
            new BundledCape("Фиолетовый миграция.png", "/assets/phaze/cosmetics/capes/purple_migration.png"),
            new BundledCape("жабижаби.png", "/assets/phaze/cosmetics/capes/zhabizhabi.png"),
            new BundledCape("Зелёная миграция.png", "/assets/phaze/cosmetics/capes/green_migration.png"),
            new BundledCape("Eyeblossom.png", "/assets/phaze/cosmetics/capes/eyeblossom.png"),
            new BundledCape("sigma_face.png", "/assets/phaze/cosmetics/capes/sigma_face.png"),
            new BundledCape("Purple Sky.png", "/assets/phaze/cosmetics/capes/purple_sky.png"),
            new BundledCape("Migrator Night Sky.png", "/assets/phaze/cosmetics/capes/migrator_night_sky.png"),
            new BundledCape("Rubirubi.png", "/assets/phaze/cosmetics/capes/rubirubi.png"),
            new BundledCape("MineCon 2012.png", "/assets/phaze/cosmetics/capes/minecon_2012.png"),
            new BundledCape("новий медний.png", "/assets/phaze/cosmetics/capes/new_copper.png"),
            new BundledCape("Undertale.png", "/assets/phaze/cosmetics/capes/undertale.png"),
            new BundledCape("tik tok.png", "/assets/phaze/cosmetics/capes/tik_tok.png"),
            new BundledCape("ayunull.png", "/assets/phaze/cosmetics/capes/ayunull.png"),
            new BundledCape("d4d81e4e27209a037607b25100aa3796.png", "/assets/phaze/cosmetics/capes/d4d81e4e27209a037607b25100aa3796.png"),
            new BundledCape("b134c4742c683cbdf16e218d4c8df3c6.png", "/assets/phaze/cosmetics/capes/b134c4742c683cbdf16e218d4c8df3c6.png"),
            new BundledCape("Cape of Enderborn.png", "/assets/phaze/cosmetics/capes/cape_of_enderborn.png"),
            new BundledCape("1ab92d8196d83bc446d4f84f9573fc16.png", "/assets/phaze/cosmetics/capes/1ab92d8196d83bc446d4f84f9573fc16.png"),
            new BundledCape("uzi_dronesmurder.png", "/assets/phaze/cosmetics/capes/uzi_dronesmurder.png"),
            new BundledCape("15-th Anniversary.png", "/assets/phaze/cosmetics/capes/15th_anniversary.png"),
            new BundledCape("MCC 15th Year.png", "/assets/phaze/cosmetics/capes/mcc_15th_year.png"),
            new BundledCape("твич плаш.png", "/assets/phaze/cosmetics/capes/twitch_cape.png"),
            new BundledCape("Луна.gif", "/assets/phaze/cosmetics/capes/moon.gif"),
            new BundledCape("Фиолетовый дождь.gif", "/assets/phaze/cosmetics/capes/purple_rain.gif"),
            new BundledCape("Сияющая кирка.gif", "/assets/phaze/cosmetics/capes/glowing_pickaxe.gif"),
            new BundledCape("Портал в Нижний мир.gif", "/assets/phaze/cosmetics/capes/nether_portal.gif"),
            new BundledCape("Эндермен.gif", "/assets/phaze/cosmetics/capes/enderman.gif"),
            new BundledCape("Разноцветный.gif", "/assets/phaze/cosmetics/capes/multicolor.gif"),
            new BundledCape("Блэкстоун.gif", "/assets/phaze/cosmetics/capes/blackstone.gif"),
            new BundledCape("Красный вирус.gif", "/assets/phaze/cosmetics/capes/red_virus.gif"),
            new BundledCape("Молния анимация.gif", "/assets/phaze/cosmetics/capes/lightning.gif"),
            new BundledCape("Магма.gif", "/assets/phaze/cosmetics/capes/magma.gif")
    );

    private static final CosmeticsState INSTANCE = new CosmeticsState();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String selectedWing = WIMGS;
    private String selectedCape = NONE;
    private String selectedHat = NONE;
    private String selectedPet = NONE;
    private boolean wingEquipped = true;
    private boolean capeEquipped;
    private boolean hatEquipped;
    private boolean petEquipped;
    private boolean hideElytra = true;
    private float wingMotion = 5.5F;
    private float petFollowIntensity = 0.75F;
    private List<CosmeticEntry> catalog = List.of();

    private CosmeticsState() {
        installBundledModels();
        refreshCatalog();
        load();
    }

    public static CosmeticsState getInstance() {
        return INSTANCE;
    }

    public synchronized String getSelected() {
        return selectedWing;
    }

    public synchronized void setSelected(String selected) {
        if (selected == null || selected.isBlank()
                || (!NONE.equalsIgnoreCase(selected) && !isAvailable(selected))) return;
        CosmeticType type = typeOf(selected);
        String current = switch (type) {
            case CAPE -> selectedCape;
            case HAT -> selectedHat;
            case PET -> selectedPet;
            case WING -> selectedWing;
        };
        if (current.equalsIgnoreCase(selected)) return;
        switch (type) {
            case CAPE -> selectedCape = selected;
            case HAT -> selectedHat = selected;
            case PET -> selectedPet = selected;
            case WING -> selectedWing = selected;
        }
        save();
        CosmeticsSyncService.getInstance().publishLocalState();
    }

    public synchronized boolean isEquipped() {
        return isWingEquipped() || isCapeEquipped() || isHatEquipped() || isPetEquipped();
    }

    public synchronized void setEquipped(boolean equipped) {
        setEquipped(selectedWing, equipped);
    }

    public synchronized String getSelectedWing() {
        return selectedWing;
    }

    public synchronized String getSelectedCape() {
        return selectedCape;
    }

    public synchronized String getSelectedHat() {
        return selectedHat;
    }

    public synchronized String getSelectedPet() {
        return selectedPet;
    }

    public synchronized boolean isWingEquipped() {
        return wingEquipped && !NONE.equalsIgnoreCase(selectedWing);
    }

    public synchronized boolean isCapeEquipped() {
        return capeEquipped && !NONE.equalsIgnoreCase(selectedCape);
    }

    public synchronized boolean isHatEquipped() {
        return hatEquipped && !NONE.equalsIgnoreCase(selectedHat);
    }

    public synchronized boolean isPetEquipped() {
        return petEquipped && !NONE.equalsIgnoreCase(selectedPet);
    }

    public synchronized boolean isEquipped(String cosmetic) {
        if (cosmetic == null) return false;
        return switch (typeOf(cosmetic)) {
            case CAPE -> isCapeEquipped() && selectedCape.equalsIgnoreCase(cosmetic);
            case HAT -> isHatEquipped() && selectedHat.equalsIgnoreCase(cosmetic);
            case PET -> isPetEquipped() && selectedPet.equalsIgnoreCase(cosmetic);
            case WING -> isWingEquipped() && selectedWing.equalsIgnoreCase(cosmetic);
        };
    }

    public synchronized void setEquipped(String cosmetic, boolean equipped) {
        if (cosmetic == null || NONE.equalsIgnoreCase(cosmetic)) return;
        switch (typeOf(cosmetic)) {
            case CAPE -> {
                if (capeEquipped == equipped) return;
                capeEquipped = equipped;
            }
            case HAT -> {
                if (hatEquipped == equipped) return;
                hatEquipped = equipped;
            }
            case PET -> {
                if (petEquipped == equipped) return;
                petEquipped = equipped;
            }
            case WING -> {
                if (wingEquipped == equipped) return;
                wingEquipped = equipped;
            }
        }
        save();
        CosmeticsSyncService.getInstance().publishLocalState();
    }

    public synchronized boolean isHideElytra() {
        return hideElytra;
    }

    public synchronized void setHideElytra(boolean hideElytra) {
        this.hideElytra = hideElytra;
        save();
    }

    public synchronized float getWingMotion() {
        return wingMotion;
    }

    public synchronized void setWingMotion(float wingMotion) {
        this.wingMotion = Math.max(0.0F, Math.min(16.0F, wingMotion));
        save();
    }

    public synchronized float getPetFollowIntensity() {
        return petFollowIntensity;
    }

    public synchronized void setPetFollowIntensity(float intensity) {
        this.petFollowIntensity = Math.max(0.0F, Math.min(1.0F, intensity));
        save();
    }

    public boolean isAvailable(String cosmetic) {
        return resolveModelPath(cosmetic) != null;
    }

    public synchronized List<CosmeticEntry> getCatalog() {
        return catalog;
    }

    public synchronized void refreshCatalog() {
        List<CosmeticEntry> discovered = new ArrayList<>();
        Path directory = cosmeticsDirectory();
        try {
            Files.createDirectories(directory);
            try (var files = Files.list(directory)) {
                files.filter(Files::isRegularFile)
                        .filter(CosmeticsState::isModelFile)
                        .filter(file -> !isSimpleWingsFile(file))
                        .forEach(file -> discoverEntries(file, discovered));
            }
        } catch (Throwable ignored) {
            // The empty-state UI handles an unavailable directory.
        }
        discovered.sort(Comparator
                .comparingInt((CosmeticEntry entry) -> catalogPriority(entry.name()))
                .thenComparing(CosmeticEntry::name, String.CASE_INSENSITIVE_ORDER));
        catalog = List.copyOf(discovered);
    }

    public synchronized Path resolveModelPath(String cosmetic) {
        CosmeticEntry entry = resolveEntry(cosmetic);
        return entry == null ? null : entry.file();
    }

    public synchronized CosmeticEntry resolveEntry(String cosmetic) {
        if (cosmetic == null) return null;
        for (CosmeticEntry entry : catalog) {
            if (entry.name().equalsIgnoreCase(cosmetic) && Files.isRegularFile(entry.file())) {
                return entry;
            }
        }
        return null;
    }

    public Path cosmeticsDirectory() {
        MinecraftClient client = MinecraftClient.getInstance();
        Path runDirectory = client != null && client.runDirectory != null
                ? client.runDirectory.toPath()
                : Path.of(".");
        return runDirectory.resolve("Phaze").resolve("cosmetics");
    }

    /**
     * Standard cosmetics ship with the mod. The VPS synchronizes only their
     * small catalog ids; it never serves model archives per player.
     */
    private void installBundledModels() {
        Path directory = cosmeticsDirectory();
        // This was an early compressed preview of the regular Phaze cape.
        // Remove already-installed copies too, otherwise catalog discovery
        // would keep showing "Classic Phaze" after it left the bundled list.
        try {
            Files.deleteIfExists(directory.resolve("PhazeClient-cape-preview.png"));
            Files.deleteIfExists(directory.resolve("Ally Companion.zip"));
            // Removed cosmetics must also disappear from existing installs,
            // otherwise discovery would keep listing an archive copied by an
            // older client version.
            Files.deleteIfExists(directory.resolve("Dreamy Snowy Fox Companion.zip"));
            Files.deleteIfExists(directory.resolve("Dreamy Fox Companion.zip"));
        } catch (Throwable ignored) {
            // A read-only game directory must not prevent client startup.
        }
        for (String fileName : List.of(
                "Wimgs.bbmodel",
                "Fluffy Wings 1.0.zip",
                "aylDWT 4.2.zip",
                "Goldfish Pet.zip",
                "Birb v2.0.zip",
                "Turtle Pet.zip",
                "Maple Witch Hat.bbmodel",
                "Hat Kid Collection.bbmodel",
                "Violet Witch Hat.bbmodel"
        )) {
            Path target = directory.resolve(fileName);
            if (Files.isRegularFile(target)) continue;
            String resource = "/assets/phaze/cosmetics/models/" + fileName;
            try (InputStream input = CosmeticsState.class.getResourceAsStream(resource)) {
                if (input == null) continue;
                Files.createDirectories(directory);
                Files.copy(input, target);
            } catch (Throwable ignored) {
                // A read-only game directory should not prevent local models
                // already present on disk from continuing to work.
            }
        }
        for (BundledCape cape : BUNDLED_CAPES) {
            Path target = directory.resolve(cape.fileName());
            boolean refreshBundledPhazeCape =
                    "Phaze Cape.png".equalsIgnoreCase(cape.fileName());
            if (Files.isRegularFile(target) && !refreshBundledPhazeCape) continue;
            try (InputStream input = CosmeticsState.class.getResourceAsStream(cape.resource())) {
                if (input == null) continue;
                Files.createDirectories(directory);
                if (refreshBundledPhazeCape) {
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.copy(input, target);
                }
            } catch (Throwable ignored) {
                // Keep startup usable on read-only installations.
            }
        }
    }

    private Path settingsFile() {
        return cosmeticsDirectory().resolve("settings.json");
    }

    private synchronized void load() {
        try {
            Path file = settingsFile();
            if (!Files.isRegularFile(file)) return;
            JsonObject object = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (object.has("selectedWing") || object.has("selectedCape")) {
                selectedWing = object.has("selectedWing") ? object.get("selectedWing").getAsString() : WIMGS;
                selectedCape = object.has("selectedCape") ? object.get("selectedCape").getAsString() : NONE;
                selectedHat = object.has("selectedHat") ? object.get("selectedHat").getAsString() : NONE;
                selectedPet = object.has("selectedPet") ? object.get("selectedPet").getAsString() : NONE;
                wingEquipped = !object.has("wingEquipped") || object.get("wingEquipped").getAsBoolean();
                capeEquipped = object.has("capeEquipped") && object.get("capeEquipped").getAsBoolean();
                hatEquipped = object.has("hatEquipped") && object.get("hatEquipped").getAsBoolean();
                petEquipped = object.has("petEquipped") && object.get("petEquipped").getAsBoolean();
            } else {
                String legacy = object.has("selected") ? object.get("selected").getAsString() : WIMGS;
                boolean legacyEquipped = !object.has("equipped") || object.get("equipped").getAsBoolean();
                if (isCape(legacy)) {
                    selectedCape = legacy;
                    capeEquipped = legacyEquipped;
                    wingEquipped = false;
                } else {
                    selectedWing = legacy == null || legacy.isBlank() ? WIMGS : legacy;
                    wingEquipped = legacyEquipped;
                }
            }
            hideElytra = !object.has("hideElytra") || object.get("hideElytra").getAsBoolean();
            wingMotion = object.has("wingMotion")
                    ? Math.max(0.0F, Math.min(16.0F, object.get("wingMotion").getAsFloat()))
                    : 5.5F;
            petFollowIntensity = object.has("petFollowIntensity")
                    ? Math.max(0.0F, Math.min(1.0F,
                    object.get("petFollowIntensity").getAsFloat()))
                    : 0.75F;
            if (selectedWing != null
                    && selectedWing.toLowerCase(Locale.ROOT).contains("simplewings")) {
                selectedWing = WIMGS;
            }
            if ("Ally Companion".equalsIgnoreCase(selectedPet)) {
                selectedPet = NONE;
                petEquipped = false;
            }
        } catch (Throwable ignored) {
            // A malformed local preference file must never prevent startup.
        }
    }

    private synchronized void save() {
        try {
            Path file = settingsFile();
            Files.createDirectories(file.getParent());
            JsonObject object = new JsonObject();
            object.addProperty("selectedWing", selectedWing);
            object.addProperty("selectedCape", selectedCape);
            object.addProperty("selectedHat", selectedHat);
            object.addProperty("selectedPet", selectedPet);
            object.addProperty("wingEquipped", wingEquipped);
            object.addProperty("capeEquipped", capeEquipped);
            object.addProperty("hatEquipped", hatEquipped);
            object.addProperty("petEquipped", petEquipped);
            object.addProperty("hideElytra", hideElytra);
            object.addProperty("wingMotion", wingMotion);
            object.addProperty("petFollowIntensity", petFollowIntensity);
            Files.writeString(file, GSON.toJson(object), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            // Cosmetic preferences are non-critical; rendering can continue.
        }
    }

    private static boolean isModelFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".bbmodel") || name.endsWith(".zip")
                || name.endsWith(".png") || name.endsWith(".gif");
    }

    private static boolean isSimpleWingsFile(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).contains("simplewings");
    }

    private static String displayName(String fileName) {
        String name = fileName.replaceFirst("(?i)\\.(bbmodel|zip|png|gif)$", "");
        return name.replaceFirst("(?i)\\s+v?\\d+(?:\\.\\d+)*$", "");
    }

    private static void discoverEntries(Path file, List<CosmeticEntry> result) {
        String baseName = displayName(file.getFileName().toString());
        if ("Violet Witch Hat".equalsIgnoreCase(baseName)) {
            // The complete hat is stored directly under the avatar's Head
            // branch: its nested group contains the crown while the brim and
            // black backing are sibling elements.
            result.add(new CosmeticEntry(
                    baseName,
                    file,
                    "group:6b954fc5-1a45-4203-b83d-fa5fe38362c3"
            ));
            return;
        }
        if ("Hat Kid Collection".equalsIgnoreCase(baseName)) {
            addHatKidVariant(result, file, "Kid's Hat", "hat1");
            addHatKidVariant(result, file, "Sprint Hat", "hat2");
            addHatKidVariant(result, file, "Brewing Hat", "hat3");
            addHatKidVariant(result, file, "Ice Hat", "hat4");
            return;
        }
        result.add(new CosmeticEntry(baseName, file, ""));
        if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return;
        }

        try (ZipFile archive = new ZipFile(file.toFile())) {
            TreeSet<String> variants = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            archive.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(entry -> entry.getName().replace('\\', '/'))
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith("skins/"))
                    .filter(name -> name.toLowerCase(Locale.ROOT).endsWith("/wings.png"))
                    .forEach(name -> {
                        String[] parts = name.split("/");
                        if (parts.length >= 3 && !"template".equalsIgnoreCase(parts[1])) {
                            variants.add(parts[1]);
                        }
                    });
            for (String variant : variants) {
                // This skin is intentionally not offered as a separate
                // cosmetic, while the shared model archive remains intact
                // for the other wing variants.
                if ("ayldwt".equalsIgnoreCase(baseName)
                        && ("fire".equalsIgnoreCase(variant)
                        || "magma (codexcracked)".equalsIgnoreCase(variant))) {
                    continue;
                }
                result.add(new CosmeticEntry(
                        baseName + " · " + prettifyVariant(variant),
                        file,
                        "skins/" + variant
                ));
            }
        } catch (Throwable ignored) {
            // A regular single-model ZIP needs no variant metadata.
        }
    }

    private static void addHatKidVariant(
            List<CosmeticEntry> result,
            Path file,
            String name,
            String group
    ) {
        result.add(new CosmeticEntry(
                "Hat Kid · " + name,
                file,
                "group:" + group
        ));
    }

    private static String prettifyVariant(String value) {
        String spaced = value
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .trim();
        if (spaced.isEmpty()) return value;
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static int catalogPriority(String name) {
        if (WIMGS.equalsIgnoreCase(name)) return 0;
        if (FLUFFY_WINGS.equalsIgnoreCase(name)) return 1;
        if (PHAZE_CAPE.equalsIgnoreCase(name)) return 2;
        return 3;
    }

    public static boolean isCape(String cosmetic) {
        return typeOf(cosmetic) == CosmeticType.CAPE;
    }

    public static boolean isWing(String cosmetic) {
        return typeOf(cosmetic) == CosmeticType.WING;
    }

    public static boolean isHat(String cosmetic) {
        return typeOf(cosmetic) == CosmeticType.HAT;
    }

    public static boolean isPet(String cosmetic) {
        return typeOf(cosmetic) == CosmeticType.PET;
    }

    public static CosmeticType typeOf(String cosmetic) {
        CosmeticEntry entry = INSTANCE == null ? null : INSTANCE.resolveEntry(cosmetic);
        String fileName = entry == null
                ? (cosmetic == null ? "" : cosmetic)
                : entry.file().getFileName().toString();
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png") || lower.endsWith(".gif")) return CosmeticType.CAPE;
        if (lower.contains("goldfish pet")
                || lower.contains("birb")) {
            return CosmeticType.PET;
        }
        if (lower.contains("turtle pet")
                || lower.contains("witch hat")
                || lower.contains("hat kid collection")) {
            return CosmeticType.HAT;
        }
        return CosmeticType.WING;
    }

    /** Stable catalog ids remain unchanged for sync; only UI labels are translated. */
    public static String displayNameFor(String cosmetic) {
        if (cosmetic == null) return "";
        String lower = cosmetic.toLowerCase(Locale.ROOT);
        boolean russian = Lang.RU.equals(Lang.getActive());
        if (lower.startsWith("ayldwt")) {
            String variant = cosmetic.contains("·")
                    ? cosmetic.substring(cosmetic.indexOf('·') + 1).trim()
                    : "";
            if (!russian) {
                return variant.isBlank() ? "Dragon Wings" : variant + " Wings";
            }
            String translated = switch (variant.toLowerCase(Locale.ROOT)) {
                case "arthur" -> "Крылья Артура";
                case "deep blue" -> "Глубокие синие крылья";
                case "ender dragon" -> "Крылья дракона Края";
                case "magma (codex cracked)" -> "Магмовые треснувшие крылья";
                case "magma purple" -> "Пурпурно-магмовые крылья";
                case "stars and stripes" -> "Крылья «Звёзды и полосы»";
                default -> variant.isBlank() ? "Драконьи крылья" : variant + " крылья";
            };
            return translated;
        }
        if (!russian) {
            return switch (lower) {
                case "wimgs" -> "Angel Wings";
                case "fluffy wings" -> "Fluffy Wings";
                case "phaze cape" -> "Phaze Cape";
                case "goldfish pet" -> "Goldfish";
                case "birb" -> "Birb Companion";
                case "turtle pet" -> "Turtle Hat";
                case "maple witch hat" -> "Maple Witch Hat";
                case "violet witch hat" -> "Violet Witch Hat";
                case "hat kid · kid's hat" -> "Kid's Hat";
                case "hat kid · sprint hat" -> "Sprint Hat";
                case "hat kid · brewing hat" -> "Brewing Hat";
                case "hat kid · ice hat" -> "Ice Hat";
                case "награда 15 лет+вишня" -> "Cherry Anniversary";
                case "old mogang cape" -> "Old Mojang Cape";
                case "unused cape 1" -> "Unreleased Cape";
                case "turtle shell" -> "Turtle Shell";
                case "fauna faire" -> "Fauna Faire";
                case "фиолетовый миграция" -> "Purple Migration";
                case "жабижаби" -> "Frog Cape";
                case "зелёная миграция" -> "Green Migration";
                case "eyeblossom" -> "Eyeblossom";
                case "sigma_face" -> "Sigma";
                case "purple sky" -> "Purple Sky";
                case "migrator night sky" -> "Migrator Night Sky";
                case "rubirubi" -> "Ruby Cape";
                case "minecon 2012" -> "MineCon 2012";
                case "новий медний" -> "Copper Cape";
                case "undertale" -> "Undertale";
                case "tik tok" -> "TikTok";
                case "ayunull" -> "Ayunull";
                case "d4d81e4e27209a037607b25100aa3796" -> "Wanderer Cape";
                case "b134c4742c683cbdf16e218d4c8df3c6" -> "Abyss Cape";
                case "cape of enderborn" -> "Cape of Enderborn";
                case "1ab92d8196d83bc446d4f84f9573fc16" -> "Hero Cape";
                case "uzi_dronesmurder" -> "Uzi";
                case "15-th anniversary" -> "15th Anniversary";
                case "mcc 15th year" -> "MCC 15th Year";
                case "твич плаш" -> "Twitch Cape";
                case "луна" -> "Moon";
                case "фиолетовый дождь" -> "Purple Rain";
                case "сияющая кирка" -> "Glowing Pickaxe";
                case "портал в нижний мир" -> "Nether Portal";
                case "эндермен" -> "Enderman";
                case "разноцветный" -> "Multicolor";
                case "блэкстоун" -> "Blackstone";
                case "красный вирус" -> "Red Virus";
                case "молния анимация" -> "Lightning";
                case "магма" -> "Magma";
                default -> cosmetic.replace('_', ' ').replace('-', ' ');
            };
        }
        return switch (lower) {
            case "wimgs" -> "Ангельские крылья";
            case "fluffy wings" -> "Пушистые крылья";
            case "phaze cape" -> "Плащ Phaze";
            case "goldfish pet" -> "Золотая рыбка";
            case "birb" -> "Птичка";
            case "turtle pet" -> "Черепашка";
            case "maple witch hat" -> "Шляпа ведьмы Мэйпл";
            case "violet witch hat" -> "Фиолетовая шляпа ведьмы";
            case "hat kid · kid's hat" -> "Шляпа Хэт Кид";
            case "hat kid · sprint hat" -> "Спринтерская шляпа";
            case "hat kid · brewing hat" -> "Зельеварская шляпа";
            case "hat kid · ice hat" -> "Ледяная шляпа";
            case "награда 15 лет+вишня" -> "Вишнёвый юбилей";
            case "old mogang cape" -> "Старый плащ Mojang";
            case "unused cape 1" -> "Неизданный плащ";
            case "turtle shell" -> "Черепаший панцирь";
            case "fauna faire" -> "Праздник фауны";
            case "фиолетовый миграция" -> "Фиолетовая миграция";
            case "жабижаби" -> "Жабий плащ";
            case "зелёная миграция" -> "Зелёная миграция";
            case "eyeblossom" -> "Глазоцвет";
            case "sigma_face" -> "Сигма";
            case "purple sky" -> "Фиолетовое небо";
            case "migrator night sky" -> "Ночное небо миграции";
            case "rubirubi" -> "Рубиновый плащ";
            case "minecon 2012" -> "MineCon 2012";
            case "новий медний" -> "Медный плащ";
            case "undertale" -> "Undertale";
            case "tik tok" -> "TikTok";
            case "ayunull" -> "Аюнулл";
            case "d4d81e4e27209a037607b25100aa3796" -> "Плащ странника";
            case "b134c4742c683cbdf16e218d4c8df3c6" -> "Плащ бездны";
            case "cape of enderborn" -> "Плащ рождённого Краем";
            case "1ab92d8196d83bc446d4f84f9573fc16" -> "Плащ героя";
            case "uzi_dronesmurder" -> "Узи";
            case "15-th anniversary" -> "Пятнадцатилетие";
            case "mcc 15th year" -> "MCC: 15 лет";
            case "твич плаш" -> "Плащ Twitch";
            case "луна" -> "Луна";
            case "фиолетовый дождь" -> "Фиолетовый дождь";
            case "сияющая кирка" -> "Сияющая кирка";
            case "портал в нижний мир" -> "Портал в Нижний мир";
            case "эндермен" -> "Эндермен";
            case "разноцветный" -> "Разноцветный";
            case "блэкстоун" -> "Блэкстоун";
            case "красный вирус" -> "Красный вирус";
            case "молния анимация" -> "Молния";
            case "магма" -> "Магма";
            default -> cosmetic.replace('_', ' ').replace('-', ' ');
        };
    }

    private record BundledCape(String fileName, String resource) {
    }

    public record CosmeticEntry(String name, Path file, String textureVariant) {
    }

    public enum CosmeticType {
        WING,
        CAPE,
        HAT,
        PET
    }
}
