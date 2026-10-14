package vorga.phazeclient.implement.features.modules.other;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.TntEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.TntEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
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
    public final SelectSetting timeFormat = new SelectSetting("Time Format", "Countdown format")
            .value("0.0s", "0.00s", "Ticks").selected("0.00s");
    public final BooleanSetting dynamicColor = new BooleanSetting("Dynamic Color", "Shift green -> yellow -> red as fuse runs down").setValue(true);
    public final ColorSetting staticColor = new ColorSetting("Color", "Custom countdown text color").value(0xFFFF3333);
    public final ValueSetting scale = new ValueSetting("Scale", "Size of the countdown tag").range(0.5f, 2.5f).step(0.1f).setValue(1.0f);

    private TntCountdown() {
        super("tnt_countdown", "TNT Countdown", ModuleCategory.OTHER);
        setup(showText, timeFormat, dynamicColor, staticColor, scale);
        WorldRenderEvents.AFTER_ENTITIES.register(TntCountdown::renderWorldTimers);
    }

    public static TntCountdown getInstance() {
        return INSTANCE;
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

        float seconds = Math.max(0.0f, fuse / 20.0f);
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

        String formatted = switch (module.timeFormat.getSelected()) {
            case "0.0s" -> String.format(Locale.ROOT, "%.1fs", seconds);
            case "Ticks" -> String.format(Locale.ROOT, "%.0f", fuse);
            default -> String.format(Locale.ROOT, "%.2fs", seconds);
        };
        Text text = Text.literal(formatted);
        int textWidth = textRenderer.getWidth(text);

        matrices.push();
        // Position directly above the top of the TNT block (TNT is 1x1x1)
        matrices.translate(0.0, 1.25, 0.0);
        matrices.multiply(cameraRotation);
        // Correct billboard scale (positive X, negative Y to keep normal winding)
        float s = 0.025f * module.scale.getValue();
        // Match vanilla name-tag winding. Positive X made the text face away
        // from the camera with the renderer's quaternion on some views.
        matrices.scale(-s, -s, s);

        Matrix4f posMatrix = matrices.peek().getPositionMatrix();
        float x = -textWidth / 2.0f;

        // Draw background see-through so it's always readable through walls
        textRenderer.draw(
                text,
                x,
                0.0f,
                color,
                false,
                posMatrix,
                vertexConsumers,
                TextRenderer.TextLayerType.SEE_THROUGH,
                0x90000000,
                0xF000F0
        );

        // Draw foreground crisp text
        textRenderer.draw(
                text,
                x,
                0.0f,
                color,
                false,
                posMatrix,
                vertexConsumers,
                TextRenderer.TextLayerType.NORMAL,
                0,
                0xF000F0
        );

        matrices.pop();
    }

    private static void renderWorldTimers(WorldRenderContext context) {
        TntCountdown module = getInstance();
        MinecraftClient client = MinecraftClient.getInstance();
        if (module == null || !module.showText.isValue()
                || client.world == null || client.player == null || context.consumers() == null) return;

        float tickDelta = context.tickCounter().getTickDelta(false);
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        Box search = client.player.getBoundingBox().expand(48.0);
        for (TntEntity tnt : client.world.getEntitiesByClass(TntEntity.class, search, entity -> true)) {
            float fuse = tnt.getFuse() - tickDelta;
            if (fuse <= 0.0f) continue;
            MatrixStack matrices = context.matrixStack();
            matrices.push();
            matrices.translate(tnt.getX() - camera.x, tnt.getY() - camera.y, tnt.getZ() - camera.z);
            renderTimer(new TntEntityRenderStateProxy(fuse).state(), matrices, context.consumers(),
                    client.gameRenderer.getCamera().getRotation(), client.textRenderer);
            matrices.pop();
        }
    }

    // The renderer mixin uses the native state; the world fallback only needs
    // the same fuse value and avoids relying on a renderer injection target.
    private record TntEntityRenderStateProxy(float fuseValue) {
        TntEntityRenderState state() {
            TntEntityRenderState state = new TntEntityRenderState();
            state.fuse = fuseValue;
            return state;
        }
    }
}
