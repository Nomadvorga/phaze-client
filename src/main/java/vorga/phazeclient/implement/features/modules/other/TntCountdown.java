package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.TntEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.joml.Quaternionf;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

import java.util.Locale;

public class TntCountdown extends Module {
    private static final TntCountdown INSTANCE = new TntCountdown();

    public final BooleanSetting dynamicColor = new BooleanSetting("Dynamic Color", "Shift green -> yellow -> red as fuse runs down").setValue(true);
    public final ColorSetting staticColor = new ColorSetting("Color", "Custom countdown text color").value(0xFFFF3333);
    public final ValueSetting scale = new ValueSetting("Scale", "Size of the countdown tag").range(0.5f, 2.5f).step(0.1f).setValue(1.0f);

    private TntCountdown() {
        super("tnt_countdown", "TNT Countdown", ModuleCategory.OTHER);
        setup(dynamicColor, staticColor, scale);
    }

    public static TntCountdown getInstance() {
        return INSTANCE;
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
        if (fuse <= 0.0f) return;

        TntCountdown module = getInstance();
        if (module == null || !module.isEnabled()) return;

        float seconds = fuse / 20.0f;
        int color;
        if (module.dynamicColor.isValue()) {
            if (fuse > 40.0f) {
                color = 0xFF00FF88;
            } else if (fuse > 20.0f) {
                color = 0xFFFFAA00;
            } else {
                color = 0xFFFF2222;
            }
        } else {
            color = module.staticColor.getColor();
        }

        String formatted = String.format(Locale.ROOT, "%.1fs", seconds);
        Text text = Text.literal(formatted);
        int textWidth = textRenderer.getWidth(text);

        matrices.push();
        matrices.translate(0.0, 1.25, 0.0);
        matrices.multiply(cameraRotation);
        float s = 0.025f * module.scale.getValue();
        matrices.scale(-s, -s, s);

        textRenderer.draw(
                text,
                -textWidth / 2.0f,
                0.0f,
                color,
                false,
                matrices.peek().getPositionMatrix(),
                vertexConsumers,
                TextRenderer.TextLayerType.SEE_THROUGH,
                0x80000000,
                0xF000F0
        );
        matrices.pop();
    }
}
