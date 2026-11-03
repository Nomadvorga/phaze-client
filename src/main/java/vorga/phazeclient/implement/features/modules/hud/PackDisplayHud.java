package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class PackDisplayHud extends RectHudModule {
    private static final PackDisplayHud INSTANCE = new PackDisplayHud();
    public static final int ICON_SIZE = 28;
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
        String profileId = selectedProfile == null ? null : selectedProfile.getId();
        if (!cachedPackName.equals(topCustomPack) || !Objects.equals(cachedProfileId, profileId) || cachedIcon == null) {
            cachedPackName = topCustomPack;
            cachedProfileId = profileId;
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
            releaseOwnedIcon(client);
            cachedIcon = loadIcon(client, selectedProfile);
        }
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

    public float getTextStartX() {
        return (cachedIcon != null && displayPackIcon.isValue() ? ICON_SIZE : 0) + TEXT_PADDING;
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

    public void onResourcesReloaded(MinecraftClient client) {
        releaseOwnedIcon(client);
        cachedIcon = null;
        cachedProfileId = null;
        lastCheckTime = 0;
        getHudBuffer().invalidate();
    }

    private void releaseOwnedIcon(MinecraftClient client) {
        if (ownedIcon != null) {
            client.getTextureManager().destroyTexture(ownedIcon);
            ownedIcon = null;
        }
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
                setIconSize(image.getWidth(), image.getHeight());
                NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
                Identifier id = Identifier.of("phaze", "dynamic/pack_display_" + Math.abs(profile.getId().hashCode())
                        + "_" + Math.abs(cachedPackName.hashCode()));
                client.getTextureManager().registerTexture(id, texture);
                ownedIcon = id;
                return id;
            }
        } catch (IOException | RuntimeException ignored) {
            return fallbackIcon();
        } finally {
            if (pack != null) pack.close();
        }
    }

    private Identifier fallbackIcon() {
        setIconSize(16, 16);
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
