package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.*;
import vorga.phazeclient.base.util.ServerUtil;

public final class ShiftTap extends Module {
    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("Phaze/ShiftTap");
    private static final ShiftTap INSTANCE = new ShiftTap();
    private static final int MIN_DURATION = 10;
    private static final int MAX_DURATION = 200;
    private static final long BLOCK_NOTIFY_INTERVAL_MS = 10000L;

    private long lastBlockNotifyMs = 0L;

    public final SectionSetting generalSection = new SectionSetting("General");
    public final ValueSetting sneakDuration = new ValueSetting("Sneak Duration", "Duration of sneak in milliseconds")
            .range(MIN_DURATION, MAX_DURATION)
            .setValue(50);

    private long shiftTapEndTime = 0L;
    private boolean moduleControllingSneak = false;

    private ShiftTap() {
        super("shifttap", "Shift Tap", ModuleCategory.UTILITIES);

        sneakDuration.setFullWidth(true);
        setup(generalSection, sneakDuration);
    }

    public static ShiftTap getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Automatically taps shift for short duration";
    }

    @Override
    public String getIcon() {
        return "shifttap.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    @Override
    public boolean isServerAllowed() {
        return ServerUtil.isShiftTapSupported();
    }

    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!isEnabled()) {
            stopShiftTap();
            return;
        }

        if (mc.player == null || mc.player.isSpectator() || !isWorldSupported()) {
            stopShiftTap();
            return;
        }

        if (moduleControllingSneak && System.currentTimeMillis() >= shiftTapEndTime) {
            stopShiftTap();
        }
    }

    public void triggerShiftTapIfPlayerTarget(net.minecraft.entity.Entity target, net.minecraft.entity.player.PlayerEntity attacker) {
        if (!(target instanceof net.minecraft.entity.player.PlayerEntity victim)
                || victim.equals(attacker)) {
            return;
        }
        triggerShiftTap();
    }

    public void triggerShiftTap() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!isEnabled() || mc.player == null || !isWorldSupported()) {

            if (!isState()) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastBlockNotifyMs >= BLOCK_NOTIFY_INTERVAL_MS) {
                lastBlockNotifyMs = now;
                String reason = phaze$blockReason(mc);
                net.minecraft.text.MutableText line = net.minecraft.text.Text
                        .literal("ShiftTap: ").formatted(net.minecraft.util.Formatting.GRAY)
                        .append(net.minecraft.text.Text.literal("blocked - " + reason)
                                .formatted(net.minecraft.util.Formatting.RED));
                mc.execute(() -> {
                    if (mc.inGameHud != null) {
                        mc.inGameHud.getChatHud().addMessage(
                                vorga.phazeclient.base.util.PhazeAnnouncements.systemMessage(line));
                    }
                });
                LOGGER.info("Trigger blocked: {}", reason);
            }
            return;
        }

        shiftTapEndTime = System.currentTimeMillis() + sneakDuration.getInt();
        if (!moduleControllingSneak) {
            mc.options.sneakKey.setPressed(true);
            moduleControllingSneak = true;
            LOGGER.info("Engaged sneak for {}ms", sneakDuration.getInt());
        }
    }

    private String phaze$blockReason(MinecraftClient mc) {
        if (mc.player == null) {
            return "not in a world";
        }
        if (!isState()) {
            return "module disabled";
        }
        if (!isEnabled()) {
            return "locked by server rules";
        }
        if (!isWorldSupported()) {
            return "server not in the ShiftTap whitelist";
        }
        return "unknown";
    }

    public void stopShiftTap() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!moduleControllingSneak) {
            return;
        }

        mc.options.sneakKey.setPressed(false);
        moduleControllingSneak = false;
        shiftTapEndTime = 0L;
    }

    private boolean isWorldSupported() {
        return ServerUtil.isShiftTapSupported();
    }

    protected void onDisable() {
        stopShiftTap();
    }
}
