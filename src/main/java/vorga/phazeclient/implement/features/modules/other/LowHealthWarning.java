package vorga.phazeclient.implement.features.modules.other;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.Identifier;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public class LowHealthWarning extends Module {
    private static final LowHealthWarning INSTANCE = new LowHealthWarning();
    private static final Identifier VIGNETTE_MASK = Identifier.of("phaze", "low_health_vignette_mask");
    private static final int MASK_SIZE = 256;
    private static boolean vignetteMaskReady;

    public final SectionSetting generalSection = new SectionSetting("General");
    public final SectionSetting soundSection = new SectionSetting("Audio");
    public final SectionSetting colorSection = new SectionSetting("Color");

    public final ValueSetting threshold = new ValueSetting("Threshold (HP)", "HP threshold to activate warning (6.0 = 3 hearts)")
            .range(2.0f, 14.0f)
            .step(1.0f)
            .setValue(6.0f);

    public final ColorSetting color = new ColorSetting("Color", "Warning vignette tint and intensity (alpha)").value(0xD9FF1111);
    public final ValueSetting pulseSpeed = new ValueSetting("Pulse Speed", "Pulsing animation speed").range(1.0f, 5.0f).step(0.5f).setValue(1.0f);
    public final BooleanSetting heartbeatSound = new BooleanSetting("Heartbeat Sound", "Play immersive heartbeat sound while in critical state").setValue(true);
    public final ValueSetting soundVolume = new ValueSetting("Sound Volume", "Heartbeat sound volume").range(10, 100).step(5).setValue(75);

    private PositionedSoundInstance currentHeartbeatInstance = null;
    private long lastHeartbeatTime = 0;
    private float activeHeartbeatVolume = -1.0f;

    private LowHealthWarning() {
        super("low_health_warning", "Low HP Warning", ModuleCategory.OTHER);
        threshold.setFullWidth(true);
        pulseSpeed.setFullWidth(true);
        heartbeatSound.setFullWidth(true);
        soundVolume.setFullWidth(true);
        color.setFullWidth(true);
        soundVolume.visible(heartbeatSound::isValue);
        setup(generalSection, threshold, pulseSpeed,
                soundSection, heartbeatSound, soundVolume,
                colorSection, color);
    }

    public static LowHealthWarning getInstance() {
        return INSTANCE;
    }

    /** Upgrade the old opacity slider on config read; never mutate the source config. */
    public static JsonObject migrateLegacySettings(JsonObject settings) {
        if (!settings.has("Intensity")) return settings;
        JsonObject migrated = settings.deepCopy();
        migrated.remove("Intensity");
        try {
            float intensity = settings.get("Intensity").getAsFloat();
            if (!Float.isFinite(intensity)) return migrated;
            int oldColor = settings.has("Color") ? settings.get("Color").getAsInt() : 0xFFFF1111;
            int alpha = Math.round((oldColor >>> 24) * MathHelper.clamp(intensity, 0.0F, 1.0F));
            migrated.addProperty("Color", (alpha << 24) | (oldColor & 0x00FFFFFF));
        } catch (RuntimeException ignored) {
            // A corrupt legacy value must not discard valid settings in this module.
        }
        return migrated;
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

    public boolean render(DrawContext context) {
        if (!isEnabled()) {
            stopHeartbeat();
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.player.isCreative() || client.player.isSpectator()) {
            stopHeartbeat();
            return false;
        }

        float hp = client.player.getHealth();
        if (hp > threshold.getValue() || hp <= 0.0f) {
            stopHeartbeat();
            return false;
        }

        // Calculate critical factor: closer to 0 HP = faster pulse and deeper red
        float hpRatio = MathHelper.clamp(hp / threshold.getValue(), 0.0f, 1.0f);
        float speedMultiplier = 1.0f + (1.0f - hpRatio) * 0.8f;

        double time = System.currentTimeMillis() / 1000.0 * (pulseSpeed.getValue() * speedMultiplier);
        float pulse = (float) ((Math.sin(time * Math.PI * 2.0) + 1.0) * 0.5);
        float baseAlpha = 0.4f + 0.6f * pulse;

        int argb = color.getColor();
        drawVignette(context, argb, baseAlpha);

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
        return (argb >>> 24) > 0;
    }

    /** One complete mask stretched over the viewport, never repeated in screen-sized UVs. */
    static void drawVignette(DrawContext context, int argb, float opacity) {
        int alpha = Math.round(MathHelper.clamp(opacity, 0.0f, 1.0f) * (argb >>> 24));
        if (alpha == 0) return;
        ensureVignetteMask(MinecraftClient.getInstance());
        int tint = (alpha << 24) | (argb & 0x00FFFFFF);
        context.drawTexture(RenderLayer::getGuiTexturedOverlay, VIGNETTE_MASK, 0, 0, 0.0f, 0.0f,
                context.getScaledWindowWidth(), context.getScaledWindowHeight(),
                MASK_SIZE, MASK_SIZE, MASK_SIZE, MASK_SIZE, tint);
    }

    private static void ensureVignetteMask(MinecraftClient client) {
        if (vignetteMaskReady) {
            return;
        }
        NativeImage image = new NativeImage(MASK_SIZE, MASK_SIZE, false);
        float center = (MASK_SIZE - 1) / 2.0f;
        for (int y = 0; y < MASK_SIZE; y++) {
            for (int x = 0; x < MASK_SIZE; x++) {
                float dx = (x - center) / center;
                float dy = (y - center) / center;
                float distance = MathHelper.clamp((float) Math.sqrt(dx * dx + dy * dy), 0.0f, 1.0f);
                float alpha = MathHelper.clamp((distance - 0.42f) / 0.58f, 0.0f, 1.0f);
                image.setColorArgb(x, y, ColorHelper.fromFloats(alpha, 1.0f, 1.0f, 1.0f));
            }
        }
        NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
        texture.setClamp(true);
        texture.setFilter(true, false);
        client.getTextureManager().registerTexture(VIGNETTE_MASK, texture);
        vignetteMaskReady = true;
    }
}
