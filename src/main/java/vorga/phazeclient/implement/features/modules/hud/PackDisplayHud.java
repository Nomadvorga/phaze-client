package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePack;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;

import java.util.Collection;
import java.io.IOException;
import java.io.InputStream;

public class PackDisplayHud extends RectHudModule {
    private static final PackDisplayHud INSTANCE = new PackDisplayHud();

    private String cachedPackName = "Default";
    private String cachedText = "Default";
    private int cachedTextWidth;
    private String cachedDescription = "";
    private Text cachedNameText = Text.literal("Default");
    private Text cachedDescriptionText = Text.empty();
    private Identifier cachedIcon;
    private int cachedIconWidth = 16;
    private int cachedIconHeight = 16;
    private long lastCheckTime = 0;
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

    private void updatePackName(MinecraftClient client) {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < 2000) return;
        lastCheckTime = now;

        if (client.getResourcePackManager() == null) return;
        Collection<ResourcePackProfile> profiles = client.getResourcePackManager().getEnabledProfiles();
        String topCustomPack = "Default";
        String topDescription = "";
        Text topNameText = Text.literal("Default");
        Text topDescriptionText = Text.empty();
        ResourcePackProfile selectedProfile = null;
        ResourcePackProfile defaultProfile = null;
        boolean first = "Первый".equals(packOrder.getSelected());
        for (ResourcePackProfile profile : profiles) {
            String name = profile.getId();
            if (name.equalsIgnoreCase("vanilla")) {
                defaultProfile = profile;
            } else {
                if (ignoreServerPack.isValue() && name.toLowerCase().contains("server")) continue;
                topCustomPack = profile.getDisplayName().getString();
                topDescription = profile.getDescription().getString();
                topNameText = profile.getDisplayName();
                topDescriptionText = profile.getDescription();
                selectedProfile = profile;
                if (first) break;
            }
        }
        if (selectedProfile == null) selectedProfile = defaultProfile;
        if (!cachedPackName.equals(topCustomPack) || (cachedIcon == null && selectedProfile != null)) {
            cachedPackName = topCustomPack;
            cachedText = cachedPackName;
            cachedTextWidth = client.textRenderer.getWidth(cachedText);
            cachedDescription = topDescription;
            cachedNameText = topNameText;
            cachedDescriptionText = topDescriptionText;
            cachedIcon = loadIcon(client, selectedProfile);
        }
    }

    public String getDisplayText(MinecraftClient client) {
        updatePackName(client);
        if (displayDescription.isValue() && !cachedDescription.isBlank()) {
            return cachedText + "\n" + cachedDescription;
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
        return displayDescription.isValue() && !getDescriptionText(client).getString().isBlank();
    }

    /** Exact base-space width: widest visible line plus equal 4px padding. */
    public float getPanelBaseWidth(MinecraftClient client) {
        updatePackName(client);
        float width = client.textRenderer.getWidth(cachedNameText);
        if (hasDescription(client)) {
            for (String line : cachedDescriptionText.getString().split("\\R", -1)) {
                width = Math.max(width, client.textRenderer.getWidth(line));
            }
        }
        return width + (cachedIcon != null && displayPackIcon.isValue() ? 28.0f : 0.0f) + 8.0f;
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

    private Identifier loadIcon(MinecraftClient client, ResourcePackProfile profile) {
        if (profile == null) return fallbackIcon();
        if (profile.getId().equalsIgnoreCase("vanilla")) {
            return fallbackIcon();
        }
        ResourcePack pack = null;
        try {
            pack = profile.createResourcePack();
            InputSupplier<InputStream> supplier = pack.openRoot("pack.png");
            if (supplier == null) return fallbackIcon();
            try (InputStream stream = supplier.get()) {
                NativeImage image = NativeImage.read(stream);
                if (isMissingTextureImage(image)) {
                    image.close();
                    return fallbackIcon();
                }
                cachedIconWidth = Math.max(1, image.getWidth());
                cachedIconHeight = Math.max(1, image.getHeight());
                NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
                Identifier id = Identifier.of("phaze", "dynamic/pack_display_" + Math.abs(profile.getId().hashCode())
                        + "_" + Math.abs(cachedPackName.hashCode()));
                client.getTextureManager().registerTexture(id, texture);
                return id;
            }
        } catch (IOException | RuntimeException ignored) {
            return fallbackIcon();
        } finally {
            if (pack != null) pack.close();
        }
    }

    private Identifier fallbackIcon() {
        cachedIconWidth = 16;
        cachedIconHeight = 16;
        return Identifier.of("minecraft", "textures/item/grass_block.png");
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
}
