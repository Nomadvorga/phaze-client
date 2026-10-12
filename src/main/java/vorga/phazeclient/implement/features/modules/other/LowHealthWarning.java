package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public class LowHealthWarning extends Module {
    private static final LowHealthWarning INSTANCE = new LowHealthWarning();
    private static final Identifier VIGNETTE_TEXTURE = Identifier.ofVanilla("textures/misc/vignette.png");

    public final ValueSetting threshold = new ValueSetting("Threshold (HP)", "HP threshold to activate warning (6.0 = 3 hearts)")
            .range(2.0f, 14.0f)
            .step(1.0f)
            .setValue(6.0f);

    public final ColorSetting color = new ColorSetting("Color", "Warning vignette tint color").value(0xE0FF1111);
    public final ValueSetting pulseSpeed = new ValueSetting("Pulse Speed", "Pulsing animation speed").range(1.0f, 5.0f).step(0.5f).setValue(2.5f);
    public final ValueSetting intensity = new ValueSetting("Intensity", "Maximum opacity of the vignette").range(0.2f, 1.0f).step(0.05f).setValue(0.85f);
    public final BooleanSetting heartbeatSound = new BooleanSetting("Heartbeat Sound", "Play immersive heartbeat sound while in critical state").setValue(true);
    public final ValueSetting soundVolume = new ValueSetting("Sound Volume", "Heartbeat sound volume").range(10, 100).step(5).setValue(75);

    private long lastHeartbeatTime = 0;

    private LowHealthWarning() {
        super("low_health_warning", "Low HP Warning", ModuleCategory.OTHER);
        setup(threshold, color, pulseSpeed, intensity, heartbeatSound, soundVolume);
    }

    public static LowHealthWarning getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Smooth cinematic screen vignette and pulsing heartbeat when health is low";
    }

    @Override
    public String getIcon() {
        return "health_indicator.png";
    }

    public void render(DrawContext context) {
        if (!isEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.player.isCreative() || client.player.isSpectator()) return;

        float hp = client.player.getHealth();
        if (hp > threshold.getValue() || hp <= 0.0f) return;

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();

        // Calculate critical factor: closer to 0 HP = faster pulse and deeper red
        float hpRatio = MathHelper.clamp(hp / threshold.getValue(), 0.0f, 1.0f);
        float speedMultiplier = 1.0f + (1.0f - hpRatio) * 0.8f;

        double time = System.currentTimeMillis() / 1000.0 * (pulseSpeed.getValue() * speedMultiplier);
        float pulse = (float) ((Math.sin(time * Math.PI * 2.0) + 1.0) * 0.5);
        float baseAlpha = intensity.getValue() * (0.35f + 0.65f * pulse);

        int baseColor = color.getColor();
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;
        int maxA = (baseColor >> 24) & 0xFF;
        if (maxA == 0) maxA = 0xFF;

        int finalA = MathHelper.clamp((int) (maxA * baseAlpha), 0, 255);
        int finalArgb = (finalA << 24) | (r << 16) | (g << 8) | b;

        // Render seamless full-screen vignette overlay using vanilla vignette shader/layer (0 seams, 0 corner overlaps)
        context.drawTexture(
                RenderLayer::getVignette,
                VIGNETTE_TEXTURE,
                0,
                0,
                0.0f,
                0.0f,
                width,
                height,
                width,
                height,
                finalArgb
        );

        // Heartbeat sound timing
        if (heartbeatSound.isValue()) {
            long now = System.currentTimeMillis();
            long intervalMs = (long) (1100 - (1.0f - hpRatio) * 550); // 1100ms at threshold down to 550ms near death
            if (now - lastHeartbeatTime >= intervalMs) {
                lastHeartbeatTime = now;
                float vol = (soundVolume.getValue() / 100.0f) * (0.5f + (1.0f - hpRatio) * 0.5f);
                float pitch = 0.9f + (1.0f - hpRatio) * 0.25f;
                client.getSoundManager().play(PositionedSoundInstance.master(PhazeSounds.HEARTBEAT, pitch, vol));
            }
        }
    }
}
