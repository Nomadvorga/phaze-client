package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import java.util.ArrayDeque;
import java.util.Iterator;
import org.joml.Matrix4f;
import net.minecraft.util.math.RotationAxis;

public class ScreenshotManager extends Module {
    private static final ScreenshotManager INSTANCE = new ScreenshotManager();
    // Toast lifetime is configurable, but motion must feel identical at every
    // lifetime. These are the timings used by the current 2.5 second preview.
    private static final float INTRO_ANIMATION_MS = 400.0f;
    private static final float FADE_ANIMATION_MS = 360.0f;

    public final BooleanSetting showToast = new BooleanSetting("Show Preview Toast", "Display floating toast when screenshot is captured").setValue(true);
    public final ValueSetting toastDuration = new ValueSetting("Duration (s)", "Toast display duration in seconds").range(1.5f, 6.0f).step(0.5f).setValue(3.0f);

    private record Toast(String name, long capturedAt) {}
    private final ArrayDeque<Toast> toasts = new ArrayDeque<>();

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
        toasts.addFirst(new Toast(filename, System.currentTimeMillis()));
        while (toasts.size() > 5) toasts.removeLast();
    }

    public void render(DrawContext context) {
        if (!isEnabled() || !showToast.isValue() || toasts.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || (client.options != null && client.options.hudHidden)) return;

        long durationMs = (long) (toastDuration.getValue() * 1000.0f);
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        int cardW = 204;
        int cardH = 44;
        int margin = 18;
        long now = System.currentTimeMillis();
        Iterator<Toast> iterator = toasts.iterator();
        int index = 0;
        while (iterator.hasNext()) {
            Toast toast = iterator.next();
            long elapsed = now - toast.capturedAt();
            if (elapsed > durationMs) {
                iterator.remove();
                continue;
            }
            float progress = (float) elapsed / (float) durationMs;
            long fadeStart = Math.max(0L, durationMs - (long) FADE_ANIMATION_MS);
            float alpha = elapsed >= fadeStart
                    ? Math.max(0.0f, (durationMs - elapsed) / FADE_ANIMATION_MS)
                    : 1.0f;
            int a = (int) (alpha * 255.0f);
            if (a <= 5) continue;
            float intro = Math.min(1.0f, elapsed / INTRO_ANIMATION_MS);
            float eased = intro * intro * (3.0f - 2.0f * intro);
            int x = screenWidth - cardW - margin + Math.round((1.0f - eased) * (cardW + margin));
            int y = screenHeight - cardH - margin - index * (cardH + 8);
            int bg = (a << 24) | 0x0B1120;
            int textCol = (a << 24) | 0xFFFFFF;
            int subCol = (a << 24) | 0x8899A6;
            int progressCol = (a << 24) | 0x35D07F;
            fillRounded(context, x, y, x + cardW, y + cardH, bg, 10);
            Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
            float titleSize = 8.1f;
            float subSize = 7.3f;
            drawCheckIcon(context, x + 11, y + 11, textCol);
            MsdfRenderer.renderText(MsdfFonts.bold(), "Screenshot Saved", titleSize, textCol, matrix,
                    x + 25, y + 9, 0.0f);
            MsdfRenderer.renderText(MsdfFonts.medium(), toast.name(), subSize, subCol, matrix,
                    x + 25, y + 24, 0.0f);
            // The timeline grows from left to right during the lifetime.
            int progressWidth = Math.max(2, Math.round(progress * (cardW - 24)));
            if (progressWidth > 0) {
                fillRounded(context, x + 12, y + cardH - 4,
                        x + 12 + progressWidth, y + cardH - 1, progressCol, 2);
            }
            index++;
        }
    }

    /** Uses the client's SDF rounded-rectangle renderer, not Minecraft's toast UI. */
    private static void fillRounded(DrawContext context, int left, int top, int right, int bottom,
                                    int color, int radius) {
        BatchedRectangle.beginScope();
        try {
            BatchedRectangle.submit(ShapeProperties.create(context.getMatrices(), left, top,
                            right - left, bottom - top)
                    .round(radius)
                    .softness(1.0f)
                    .color(color)
                    .build());
        } finally {
            BatchedRectangle.endScope();
        }
    }

    private static void drawCheckIcon(DrawContext context, int x, int y, int color) {
        drawCheckStroke(context, x + 3.0f, y + 7.0f, 5.0f, 1.8f, 45.0f, color);
        drawCheckStroke(context, x + 7.0f, y + 4.0f, 7.0f, 1.8f, -45.0f, color);
    }

    private static void drawCheckStroke(DrawContext context, float x, float y, float length,
                                        float thickness, float angle, int color) {
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0.0f);
        context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
        BatchedRectangle.beginScope();
        try {
            BatchedRectangle.submit(ShapeProperties.create(context.getMatrices(),
                            -length / 2.0f, -thickness / 2.0f, length, thickness)
                    .round(thickness / 2.0f).softness(0.6f).color(color).build());
        } finally {
            BatchedRectangle.endScope();
            context.getMatrices().pop();
        }
    }
}
