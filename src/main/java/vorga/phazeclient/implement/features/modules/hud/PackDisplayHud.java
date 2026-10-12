package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.resource.ResourcePackProfile;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

import java.util.Collection;

public class PackDisplayHud extends RectHudModule {
    private static final PackDisplayHud INSTANCE = new PackDisplayHud();

    public final ValueSetting posX = new ValueSetting("X Offset", "X screen coordinate").range(0, 1920).step(5).setValue(20);
    public final ValueSetting posY = new ValueSetting("Y Offset", "Y screen coordinate").range(0, 1080).step(5).setValue(140);
    public final BooleanSetting showPrefix = new BooleanSetting("Show Prefix", "Show \"Pack: \" prefix").setValue(true);

    private String cachedPackName = "Default";
    private long lastCheckTime = 0;

    private PackDisplayHud() {
        super("pack_display_hud", "Pack Display", 20.0f, 140.0f, 1.0f);
        setup(showPrefix, posX, posY);
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
        for (ResourcePackProfile profile : profiles) {
            String name = profile.getId();
            if (!name.equalsIgnoreCase("vanilla") && !name.startsWith("fabric") && !name.startsWith("file/fabric")) {
                topCustomPack = profile.getDisplayName().getString();
            }
        }
        cachedPackName = topCustomPack;
    }

    public void render(DrawContext context) {
        if (!isEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || (client.options != null && client.options.hudHidden)) return;

        updatePackName(client);

        String textStr = (showPrefix.isValue() ? "Pack: " : "") + cachedPackName;
        int textWidth = client.textRenderer.getWidth(textStr);
        int pad = 4;
        int width = textWidth + pad * 2;
        int height = 14;

        int x = (int) posX.getValue();
        int y = (int) posY.getValue();

        if (background.isValue()) {
            int bg = getResolvedBackgroundColor(client);
            context.fill(x, y, x + width, y + height, bg);
        }

        context.drawText(client.textRenderer, textStr, x + pad, y + 3, 0xFFFFFFFF, textShadow.isValue());
    }
}
