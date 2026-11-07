package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.system.hud.BatchedHudBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class PackDisplayHud extends RectHudModule {
    private static final PackDisplayHud INSTANCE = new PackDisplayHud();
    public static final int ICON_SIZE = 28;
    private static final Identifier UNKNOWN_PACK_ICON = Identifier.ofVanilla("textures/misc/unknown_pack.png");
    private static final int UNKNOWN_PACK_ICON_SIZE = 128;
    /** Larger pack.png files are downscaled before upload so a huge icon cannot pin megabytes of VRAM. */
    private static final int MAX_ICON_SIZE = 256;
    private static final Identifier PACK_ICON_TEXTURE = Identifier.of("phaze", "dynamic/pack_display_icon");
    private static final ResourcePackSource FABRIC_MOD_SOURCE = resolveFabricModSource();
    private static final float TEXT_PADDING = 4.0F;

    private String cachedPackName = "Default";
    private String cachedProfileId;
    private String cachedText = "Default";
    private int cachedTextWidth;
    private String cachedDescription = "";
    private String cachedTextWithDescription = "Default";
    private int cachedDescriptionWidth;
    private Text cachedNameText = Text.literal("Default");
    private Text cachedDescriptionText = Text.empty();
    private List<Text> cachedDescriptionLines = List.of();
    private Identifier cachedIcon;
    private Identifier ownedIcon;
    private int cachedIconWidth = 16;
    private int cachedIconHeight = 16;
    private int cachedDrawIconWidth = ICON_SIZE;
    private int cachedDrawIconHeight = ICON_SIZE;
    private Collection<ResourcePackProfile> observedProfiles;
    private boolean observedFirst;
    private boolean observedIgnoreServer;
    private boolean textStale = true;
    private boolean iconStale = true;
    private int iconGeneration;
    public final BooleanSetting displayPackIcon = new BooleanSetting("Показывать иконку пака", "Показывать иконку resource pack слева").setValue(true);
    public final BooleanSetting displayDescription = new BooleanSetting("Показывать описание", "Показывать описание resource pack").setValue(true);
    public final BooleanSetting ignoreServerPack = new BooleanSetting("Игнорировать серверный пак", "Не учитывать серверный resource pack").setValue(false);
    public final SelectSetting packOrder = new SelectSetting("Порядок паков", "Выбирать первый или последний пак")
            .value("Первый", "Последний").selected("Последний");

    private PackDisplayHud() {
        super("pack_display_hud", "Pack Display", 20.0f, 140.0f, 1.0f);
        cornerRounding.setValue(4.0f);
        setup(displayPackIcon, displayDescription, ignoreServerPack, packOrder);
    }

    public static PackDisplayHud getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "HUD widget showing name of current active resource pack";
    }

    @Override
    public String getIcon() {
        return "item_scroller.png";
    }

    /**
     * ResourcePackManager replaces its enabled list on every change, so an identity
     * check detects a new pack on the next frame without rescanning profiles.
     */
    private void updatePackName(MinecraftClient client) {
        if (client == null || client.getResourcePackManager() == null) return;
        Collection<ResourcePackProfile> profiles = client.getResourcePackManager().getEnabledProfiles();
        boolean first = "Первый".equals(packOrder.getSelected());
        boolean ignoreServer = ignoreServerPack.isValue();
        if (profiles == observedProfiles && first == observedFirst && ignoreServer == observedIgnoreServer) return;
        observedProfiles = profiles;
        observedFirst = first;
        observedIgnoreServer = ignoreServer;

        String topCustomPack = "Default";
        String topDescription = "";
        Text topNameText = Text.literal("Default");
        Text topDescriptionText = Text.empty();
        ResourcePackProfile selectedProfile = null;
        ResourcePackProfile defaultProfile = null;
        for (ResourcePackProfile profile : profiles) {
            String name = profile.getId();
            if (name.equalsIgnoreCase("vanilla")) {
                defaultProfile = profile;
            } else {
                if (isModPack(profile)) continue;
                if (ignoreServer && (profile.getSource() == ResourcePackSource.SERVER
                        || name.toLowerCase(Locale.ROOT).contains("server"))) continue;
                topCustomPack = profile.getDisplayName().getString();
                topDescription = profile.getDescription().getString();
                topNameText = profile.getDisplayName();
                topDescriptionText = profile.getDescription();
                selectedProfile = profile;
                if (first) break;
            }
        }
        if (selectedProfile == null) selectedProfile = defaultProfile;
        String profileId = selectedProfile == null ? null : selectedProfile.getId();
        if (iconStale || !Objects.equals(cachedProfileId, profileId)) {
            cachedProfileId = profileId;
            iconStale = false;
            requestIcon(client, selectedProfile);
        }
        if (textStale || !cachedPackName.equals(topCustomPack) || !cachedDescription.equals(topDescription)) {
            textStale = false;
            cachedPackName = topCustomPack;
            cachedText = cachedPackName;
            cachedDescription = topDescription;
            cachedTextWithDescription = cachedText + "\n" + cachedDescription;
            cachedNameText = topNameText;
            cachedDescriptionText = topDescriptionText;
            cachedDescriptionLines = splitDescription(topDescriptionText);
            cachedTextWidth = client.textRenderer.getWidth(cachedNameText);
            cachedDescriptionWidth = 0;
            for (Text line : cachedDescriptionLines) {
                cachedDescriptionWidth = Math.max(cachedDescriptionWidth, client.textRenderer.getWidth(line));
            }
            invalidateHud();
        }
    }

    /** Built-in packs that Fabric mods contribute ("Fabric Mods", "Fabric Mod "X"") are not user resource packs. */
    private static boolean isModPack(ResourcePackProfile profile) {
        if ("fabric".equals(profile.getId())) return true;
        ResourcePackSource source = profile.getSource();
        if (source == null) return false;
        return source == FABRIC_MOD_SOURCE || source.getClass().getName().startsWith("net.fabricmc.");
    }

    private static ResourcePackSource resolveFabricModSource() {
        try {
            Object source = Class.forName("net.fabricmc.fabric.impl.resource.loader.ModResourcePackCreator")
                    .getField("RESOURCE_PACK_SOURCE").get(null);
            return source instanceof ResourcePackSource packSource ? packSource : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private void invalidateHud() {
        getHudBuffer().invalidate();
        BatchedHudBuffer.INSTANCE.invalidate();
    }

    public String getDisplayText(MinecraftClient client) {
        updatePackName(client);
        if (displayDescription.isValue() && !cachedDescription.isBlank()) {
            return cachedTextWithDescription;
        }
        return cachedText;
    }

    public int getDisplayTextWidth() {
        return cachedTextWidth;
    }

    public Text getNameText(MinecraftClient client) {
        updatePackName(client);
        return cachedNameText;
    }

    public Text getDescriptionText(MinecraftClient client) {
        updatePackName(client);
        return cachedDescriptionText;
    }

    public boolean hasDescription(MinecraftClient client) {
        updatePackName(client);
        return displayDescription.isValue() && !cachedDescription.isBlank();
    }

    public List<Text> getDescriptionLines(MinecraftClient client) {
        updatePackName(client);
        return cachedDescriptionLines;
    }

    /** The icon slot is reserved while the first icon decodes, so the panel never changes width when it arrives. */
    public float getTextStartX() {
        return (displayPackIcon.isValue() ? ICON_SIZE : 0) + TEXT_PADDING;
    }

    /** Name and description share the same left edge, with padding beside the icon. */
    public float getPanelBaseWidth(MinecraftClient client) {
        updatePackName(client);
        int width = hasDescription(client) ? Math.max(cachedTextWidth, cachedDescriptionWidth) : cachedTextWidth;
        return getTextStartX() + width + TEXT_PADDING;
    }

    public Identifier getPackIcon(MinecraftClient client) {
        updatePackName(client);
        return displayPackIcon.isValue() ? cachedIcon : null;
    }

    public int getPackIconWidth() {
        return cachedIconWidth;
    }

    public int getPackIconHeight() {
        return cachedIconHeight;
    }

    /** One draw of the full cached image; fitting is calculated only when the icon changes. */
    public void drawPackIcon(DrawContext context) {
        if (cachedIcon == null || !displayPackIcon.isValue()) return;
        context.drawTexture(RenderLayer::getGuiTextured, cachedIcon,
                (ICON_SIZE - cachedDrawIconWidth) / 2, (ICON_SIZE - cachedDrawIconHeight) / 2, 0.0F, 0.0F,
                cachedDrawIconWidth, cachedDrawIconHeight,
                cachedIconWidth, cachedIconHeight, cachedIconWidth, cachedIconHeight);
    }

    private void setIconSize(int width, int height) {
        cachedIconWidth = Math.max(1, width);
        cachedIconHeight = Math.max(1, height);
        int longestSide = Math.max(cachedIconWidth, cachedIconHeight);
        cachedDrawIconWidth = Math.max(1, Math.round((float) ICON_SIZE * cachedIconWidth / longestSide));
        cachedDrawIconHeight = Math.max(1, Math.round((float) ICON_SIZE * cachedIconHeight / longestSide));
    }

    /** The current icon stays on screen until its replacement is decoded, so a reload never blanks the widget. */
    public void onResourcesReloaded(MinecraftClient client) {
        observedProfiles = null;
        textStale = true;
        iconStale = true;
        invalidateHud();
    }

    private void releaseOwnedIcon(MinecraftClient client) {
        if (ownedIcon != null) {
            client.getTextureManager().destroyTexture(ownedIcon);
            ownedIcon = null;
        }
    }

    /**
     * Zip access and PNG decoding run on the IO worker; only the GPU upload returns to the
     * render thread. A newer request bumps the generation, so a stale result is closed unused.
     */
    private void requestIcon(MinecraftClient client, ResourcePackProfile profile) {
        int generation = ++iconGeneration;
        if (profile == null) {
            applyIcon(client, generation, null);
            return;
        }
        CompletableFuture.supplyAsync(() -> loadIcon(profile), Util.getIoWorkerExecutor())
                .thenAcceptAsync(image -> applyIcon(client, generation, image), client);
    }

    private void applyIcon(MinecraftClient client, int generation, NativeImage image) {
        if (generation != iconGeneration) {
            if (image != null) image.close();
            return;
        }
        if (image == null) {
            releaseOwnedIcon(client);
            cachedIcon = fallbackIcon();
        } else {
            setIconSize(image.getWidth(), image.getHeight());
            client.getTextureManager().registerTexture(PACK_ICON_TEXTURE, new NativeImageBackedTexture(image));
            ownedIcon = PACK_ICON_TEXTURE;
            cachedIcon = PACK_ICON_TEXTURE;
        }
        invalidateHud();
    }

    /** Same icon resolution as the vanilla pack screen: the pack's own pack.png, else null for the unknown-pack icon. */
    private static NativeImage loadIcon(ResourcePackProfile profile) {
        try (ResourcePack pack = profile.createResourcePack()) {
            InputSupplier<InputStream> supplier = pack.openRoot("pack.png");
            if (supplier == null) return null;
            NativeImage image;
            try (InputStream stream = supplier.get()) {
                image = NativeImage.read(NativeImage.Format.RGBA, stream);
            }
            if (isMissingTextureImage(image)) {
                image.close();
                return null;
            }
            return fitIcon(image);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static NativeImage fitIcon(NativeImage image) {
        int longestSide = Math.max(image.getWidth(), image.getHeight());
        if (longestSide <= MAX_ICON_SIZE) return image;
        int width = Math.max(1, Math.round((float) image.getWidth() * MAX_ICON_SIZE / longestSide));
        int height = Math.max(1, Math.round((float) image.getHeight() * MAX_ICON_SIZE / longestSide));
        NativeImage scaled = new NativeImage(image.getFormat(), width, height, false);
        try {
            image.resizeSubRectTo(0, 0, image.getWidth(), image.getHeight(), scaled);
            return scaled;
        } catch (RuntimeException exception) {
            scaled.close();
            throw exception;
        } finally {
            image.close();
        }
    }

    private Identifier fallbackIcon() {
        setIconSize(UNKNOWN_PACK_ICON_SIZE, UNKNOWN_PACK_ICON_SIZE);
        return UNKNOWN_PACK_ICON;
    }

    private static boolean isMissingTextureImage(NativeImage image) {
        if (image.getWidth() <= 0 || image.getHeight() <= 0) return true;
        int first = image.getColorArgb(0, 0) & 0x00FFFFFF;
        if (first != 0x00FF00FF) return false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getColorArgb(x, y) & 0x00FFFFFF) != first) return false;
            }
        }
        return true;
    }

    private static List<Text> splitDescription(Text description) {
        List<Text> lines = new ArrayList<>();
        MutableText[] current = {Text.empty()};
        description.visit((style, fragment) -> {
            Style activeStyle = style;
            StringBuilder run = new StringBuilder();
            for (int i = 0; i < fragment.length(); i++) {
                char character = fragment.charAt(i);
                if (character == '\u00A7' && i + 1 < fragment.length()) {
                    appendRun(current[0], run, activeStyle);
                    Formatting formatting = Formatting.byCode(fragment.charAt(++i));
                    if (formatting == Formatting.RESET) {
                        activeStyle = Style.EMPTY;
                    } else if (formatting != null) {
                        activeStyle = formatting.isColor()
                                ? Style.EMPTY.withColor(formatting) : activeStyle.withFormatting(formatting);
                    }
                } else if (character == '\n' || character == '\r') {
                    appendRun(current[0], run, activeStyle);
                    if (character == '\r' && i + 1 < fragment.length() && fragment.charAt(i + 1) == '\n') i++;
                    lines.add(current[0]);
                    current[0] = Text.empty();
                } else {
                    run.append(character);
                }
            }
            appendRun(current[0], run, activeStyle);
            return java.util.Optional.empty();
        }, description.getStyle());
        lines.add(current[0]);
        return List.copyOf(lines);
    }

    private static void appendRun(MutableText line, StringBuilder run, Style style) {
        if (!run.isEmpty()) {
            line.append(Text.literal(run.toString()).setStyle(style));
            run.setLength(0);
        }
    }
}
