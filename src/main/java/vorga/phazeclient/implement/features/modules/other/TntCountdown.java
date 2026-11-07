package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.TntEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;

import java.util.Locale;

public class TntCountdown extends Module {
    private static final TntCountdown INSTANCE = new TntCountdown();

    public final BooleanSetting showText = new BooleanSetting("Show Text", "Show the countdown above primed TNT").setValue(true);
    public final BooleanSetting showBackground = new BooleanSetting("Text Background", "Show a dark background behind the countdown").setValue(true);
    public final SelectSetting timeFormat = new SelectSetting("Time Format", "Countdown format")
            .value("0.0s", "0.00s", "Ticks").selected("0.00s");
    public final BooleanSetting dynamicColor = new BooleanSetting("Dynamic Color", "Shift green -> yellow -> red as fuse runs down").setValue(true);
    public final ColorSetting staticColor = new ColorSetting("Color", "Custom countdown text color").value(0xFFFF3333);
    public final ValueSetting scale = new ValueSetting("Scale", "Size of the countdown tag").range(0.5f, 2.5f).step(0.1f).setValue(1.0f);

    private TntCountdown() {
        super("tnt_countdown", "TNT Countdown", ModuleCategory.OTHER);
        setup(showText, showBackground, timeFormat, dynamicColor, staticColor, scale);
    }

    public static TntCountdown getInstance() {
        return INSTANCE;
    }

    public String getDisplayText(float fuse) {
        float seconds = Math.max(0.0f, fuse / 20.0f);
        return switch (timeFormat.getSelected()) {
            case "0.0s" -> String.format(Locale.ROOT, "%.1fs", seconds);
            case "Ticks" -> String.format(Locale.ROOT, "%.0f", fuse);
            default -> String.format(Locale.ROOT, "%.2fs", seconds);
        };
    }

    public int getDisplayColor(float fuse) {
        if (!dynamicColor.isValue()) return staticColor.getColor();
        if (fuse > 40.0f) return 0xFF00FF88;
        if (fuse > 20.0f) return 0xFFFFAA00;
        return 0xFFFF2222;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public String getDescription() {
        return "Display billboard detonation timer above primed TNT entities";
    }

    @Override
    public String getIcon() {
        return "trap_timer.png";
    }

    public static void renderTimer(TntEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, Quaternionf cameraRotation, TextRenderer textRenderer) {
        float fuse = state.fuse;
        TntCountdown module = getInstance();
        if (module == null || !module.showText.isValue()) return;

        int color = module.getDisplayColor(fuse);
        Text text = Text.literal(module.getDisplayText(fuse));
        float x = -textRenderer.getWidth(text) / 2.0f;

        matrices.push();
        // Match EntityRenderer's label transform. TNT normally has no name
        // label, so the mixin supplies this anchor explicitly.
        Vec3d labelPos = state.nameLabelPos != null
                ? state.nameLabelPos
                : new Vec3d(0.0, 1.0, 0.0);
        matrices.translate(labelPos.x, labelPos.y + 0.5, labelPos.z);
        matrices.multiply(cameraRotation);
        float s = 0.025f * module.scale.getValue();
        matrices.scale(s, -s, s);

        // Same two-layer submission as vanilla name labels: the see-through layer
        // keeps the countdown readable behind blocks, the normal layer draws it crisp.
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        int background = module.showBackground.isValue() ? 0x90000000 : 0;
        textRenderer.draw(text, x, 0.0f, color, false, matrix, vertexConsumers,
                TextRenderer.TextLayerType.SEE_THROUGH, background, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        textRenderer.draw(text, x, 0.0f, color, false, matrix, vertexConsumers,
                TextRenderer.TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);

        matrices.pop();
    }
}
