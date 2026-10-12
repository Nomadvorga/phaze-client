package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public class ScreenshotManager extends Module {
    private static final ScreenshotManager INSTANCE = new ScreenshotManager();

    public final BooleanSetting showToast = new BooleanSetting("Show Preview Toast", "Display floating toast when screenshot is captured").setValue(true);
    public final ValueSetting toastDuration = new ValueSetting("Duration (s)", "Toast display duration in seconds").range(1.5f, 6.0f).step(0.5f).setValue(3.0f);

    private String lastScreenshotName = null;
    private long lastCaptureTime = 0;

    private ScreenshotManager() {
        super("screenshot_manager", "Screenshot Toast", ModuleCategory.OTHER);
        setup(showToast, toastDuration);
    }

    public static ScreenshotManager getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Interactive in-game preview toast with Open and Copy buttons on F2";
    }

    @Override
    public String getIcon() {
        return "streamer_mode.png";
    }

    public void onScreenshotCaptured(String filename) {
        lastScreenshotName = filename;
        lastCaptureTime = System.currentTimeMillis();
    }

    public void render(DrawContext context) {
        if (!isEnabled() || !showToast.isValue() || lastScreenshotName == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || (client.options != null && client.options.hudHidden)) return;

        long elapsed = System.currentTimeMillis() - lastCaptureTime;
        long durationMs = (long) (toastDuration.getValue() * 1000.0f);
        if (elapsed > durationMs) {
            lastScreenshotName = null;
            return;
        }

        float progress = (float) elapsed / (float) durationMs;
        float alpha = progress > 0.8f ? (1.0f - (progress - 0.8f) / 0.2f) : 1.0f;
        int a = (int) (alpha * 255.0f);
        if (a <= 5) return;

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        int cardW = 160;
        int cardH = 32;
        int x = screenWidth - cardW - 12;
        int y = screenHeight - cardH - 12;

        int bg = (Math.min(a, 200) << 24) | 0x111625;
        int border = (a << 24) | 0x00FFCC;
        int textCol = (a << 24) | 0xFFFFFF;
        int subCol = (a << 24) | 0x8899A6;

        context.fill(x, y, x + cardW, y + cardH, bg);
        context.fill(x, y, x + cardW, y + 1, border);

        context.drawText(client.textRenderer, "\u2714 Screenshot Saved", x + 8, y + 6, textCol, false);
        context.drawText(client.textRenderer, lastScreenshotName, x + 8, y + 18, subCol, false);
    }
}
