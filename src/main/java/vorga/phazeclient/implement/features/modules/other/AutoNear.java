package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.*;
import vorga.phazeclient.base.util.ServerUtil;

public final class AutoNear extends Module {
    private static final AutoNear INSTANCE = new AutoNear();

    // Settings
    public final SectionSetting generalSection = new SectionSetting("General");
    public final ValueSetting delaySeconds = new ValueSetting("Delay", "Delay between /near commands in seconds")
            .range(10, 300)
            .setValue(60);

    // Runtime state
    private long lastCommandMs = 0L;

    private AutoNear() {
        super("autonear", "Auto Near", ModuleCategory.UTILITIES);
        
        delaySeconds.setFullWidth(true);
        setup(generalSection, delaySeconds);
    }

    public static AutoNear getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Automatically executes /near max command";
    }

    @Override
    public String getIcon() {
        return "autonear.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    @Override
    public boolean isServerAllowed() {
        return ServerUtil.isFunTimeServer();
    }

    public void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        
        if (!isEnabled() || mc.player == null || mc.getNetworkHandler() == null || !ServerUtil.isFunTimeServer()) {
            return;
        }

        long now = System.currentTimeMillis();
        long delayMs = Math.max(10L, delaySeconds.getInt()) * 1000L;
        
        if (now - lastCommandMs < delayMs) {
            return;
        }

        mc.getNetworkHandler().sendChatCommand("near max");
        lastCommandMs = now;
    }

    protected void onDisable() {
        lastCommandMs = 0L;
    }
}
