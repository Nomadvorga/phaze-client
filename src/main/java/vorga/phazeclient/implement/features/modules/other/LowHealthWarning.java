package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public class LowHealthWarning extends Module {
    private static final LowHealthWarning INSTANCE = new LowHealthWarning();

    public final ValueSetting threshold = new ValueSetting("Threshold (HP)", "HP threshold to activate warning (6.0 = 3 hearts)")
            .range(2.0f, 14.0f)
            .step(1.0f)
            .setValue(6.0f);

    public final ColorSetting color = new ColorSetting("Color", "Warning vignette tint color").value(0xFFFF1111);
    public final ValueSetting pulseSpeed = new ValueSetting("Pulse Speed", "Pulsing animation speed").range(1.0f, 5.0f).step(0.5f).setValue(1.0f);
    public final ValueSetting intensity = new ValueSetting("Intensity", "Maximum opacity of the vignette").range(0.2f, 1.0f).step(0.05f).setValue(0.85f);
    public final BooleanSetting heartbeatSound = new BooleanSetting("Heartbeat Sound", "Play immersive heartbeat sound while in critical state").setValue(true);
    public final ValueSetting soundVolume = new ValueSetting("Sound Volume", "Heartbeat sound volume").range(10, 100).step(5).setValue(75);

    private PositionedSoundInstance currentHeartbeatInstance = null;
    private long lastHeartbeatTime = 0;
    private float activeHeartbeatVolume = -1.0f;

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

    @Override
    public void deactivate() {
        stopHeartbeat();
    }

    public void stopHeartbeat() {
        if (currentHeartbeatInstance != null) {
            try {
                MinecraftClient.getInstance().getSoundManager().stop(currentHeartbeatInstance);
            } catch (Throwable ignored) {}
            currentHeartbeatInstance = null;
        }
        lastHeartbeatTime = 0;
        activeHeartbeatVolume = -1.0f;
    }

    public void render(DrawContext context) {
        if (!isEnabled()) {
            stopHeartbeat();
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.player.isCreative() || client.player.isSpectator()) {
            stopHeartbeat();
            return;
        }

        float hp = client.player.getHealth();
        if (hp > threshold.getValue() || hp <= 0.0f) {
            stopHeartbeat();
            return;
        }

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();

        // Calculate critical factor: closer to 0 HP = faster pulse and deeper red
        float hpRatio = MathHelper.clamp(hp / threshold.getValue(), 0.0f, 1.0f);
        float speedMultiplier = 1.0f + (1.0f - hpRatio) * 0.8f;

        double time = System.currentTimeMillis() / 1000.0 * (pulseSpeed.getValue() * speedMultiplier);
        float pulse = (float) ((Math.sin(time * Math.PI * 2.0) + 1.0) * 0.5);
        float baseAlpha = intensity.getValue() * (0.4f + 0.6f * pulse);

        int baseColor = color.getColor();
        float r = ((baseColor >> 16) & 0xFF) / 255.0f;
        float g = ((baseColor >> 8) & 0xFF) / 255.0f;
        float b = (baseColor & 0xFF) / 255.0f;

        // Vanilla's vignette layer uses subtractive blending, which makes a
        // black picker value appear blue/purple. Use ordinary alpha blending
        // so the selected RGB value is preserved exactly.
        // Do not feed the vanilla vignette PNG through a normal GUI texture
        // layer: that texture is authored for vanilla's subtractive blend and
        // produces a dark rectangle/centre when used with ordinary alpha.
        // Four cheap alpha strips give the same edge-only shape and preserve
        // the picker colour exactly, including true black and white.
        int edgeColor = ColorHelper.fromFloats(baseAlpha, r, g, b);
        int edgeWidth = Math.max(1, Math.round(Math.min(width, height) * 0.18f));
        context.fill(0, 0, edgeWidth, height, edgeColor);
        context.fill(width - edgeWidth, 0, width, height, edgeColor);
        context.fill(0, 0, width, edgeWidth, edgeColor);
        context.fill(0, height - edgeWidth, width, height, edgeColor);

        // Heartbeat sound timing - strictly prevent layering and stop when above threshold
        if (heartbeatSound.isValue()) {
            boolean isPlaying = currentHeartbeatInstance != null && client.getSoundManager().isPlaying(currentHeartbeatInstance);
            long now = System.currentTimeMillis();
            long intervalMs = (long) (1100 - (1.0f - hpRatio) * 550);
            float requestedVolume = (soundVolume.getValue() / 100.0f) * (0.6f + (1.0f - hpRatio) * 0.4f);
            // PositionedSoundInstance is immutable. Restart the currently
            // audible pulse immediately when the slider changes instead of
            // waiting for the old sound buffer to finish.
            if (currentHeartbeatInstance != null
                    && Math.abs(requestedVolume - activeHeartbeatVolume) > 0.001f) {
                stopHeartbeat();
                lastHeartbeatTime = now - intervalMs;
                isPlaying = false;
            }
            if (!isPlaying && (now - lastHeartbeatTime >= intervalMs)) {
                // Explicitly stop a completed instance before replacing it;
                // this also prevents overlapping sounds on resource reloads.
                stopHeartbeat();
                lastHeartbeatTime = now;
                float vol = requestedVolume;
                float pitch = 0.95f + (1.0f - hpRatio) * 0.25f;
                currentHeartbeatInstance = PositionedSoundInstance.master(PhazeSounds.HEARTBEAT, pitch, vol);
                activeHeartbeatVolume = vol;
                client.getSoundManager().play(currentHeartbeatInstance);
            }
        } else {
            stopHeartbeat();
        }
    }
}
