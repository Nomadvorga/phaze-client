package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.base.util.PhazeAnnouncements;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TotemTracker extends Module {
    private static final TotemTracker INSTANCE = new TotemTracker();

    public static final byte STATUS_USE_TOTEM = 35;

    public final SectionSetting generalSection = new SectionSetting("General");

    public final ValueSetting radius = new ValueSetting(
            "Radius",
            "Maximum distance from you in blocks within which a totem pop is counted"
    ).range(8, 256).setValue(32);

    public final BooleanSetting chatNotify = new BooleanSetting(
            "Chat Notify",
            "Print a chat message when a tracked player pops a totem"
    ).setValue(true);

    public final BooleanSetting nametagSuffix = new BooleanSetting(
            "Nametag Suffix",
            "Append \" | -N\" to tracked players' nametags showing how many totems they've lost"
    ).setValue(true);

    public final ValueSetting resetCooldownSeconds = new ValueSetting(
            "Reset Cooldown",
            "Seconds of inactivity after which a player's totem-loss counter resets to 0 (0 = never)"
    ).range(0, 600).setValue(0);

    private final Map<String, Integer> losses = new ConcurrentHashMap<>();

    private final Map<String, Long> lastUseMs = new ConcurrentHashMap<>();

    public static TotemTracker getInstance() {
        return INSTANCE;
    }

    private TotemTracker() {
        super("totem_tracker", "Totem Tracker", ModuleCategory.UTILITIES);
        radius.setFullWidth(true);
        chatNotify.setFullWidth(true);
        nametagSuffix.setFullWidth(true);
        resetCooldownSeconds.setFullWidth(true);
        setup(generalSection, radius, chatNotify, nametagSuffix, resetCooldownSeconds);
    }

    public void recordTotemUse(LivingEntity entity) {
        if (!isEnabled() || entity == null) {
            return;
        }
        if (!(entity instanceof PlayerEntity player)) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }

        if (player == client.player) {
            return;
        }
        double maxDist = radius.getValue();
        double distSq = player.squaredDistanceTo(client.player);
        if (distSq > maxDist * maxDist) {
            return;
        }

        String name = player.getName().getString();
        if (name == null || name.isEmpty()) {
            return;
        }
        int newCount = losses.merge(name, 1, Integer::sum);
        lastUseMs.put(name, System.currentTimeMillis());

        if (chatNotify.isValue()) {

            MutableText line = Text.literal(name).formatted(Formatting.WHITE)
                    .append(Text.literal(" lost a totem ").formatted(Formatting.GRAY))
                    .append(Text.literal("(-" + newCount + ")").formatted(Formatting.RED));
            client.execute(() -> {
                if (client.inGameHud != null) {
                    client.inGameHud.getChatHud().addMessage(PhazeAnnouncements.systemMessage(line));
                }
            });
        }
    }

    public int getLossCount(String name) {
        if (name == null) {
            return 0;
        }
        return losses.getOrDefault(name, 0);
    }

    public int getLossCountFromText(String displayed) {
        if (displayed == null || displayed.isEmpty() || losses.isEmpty()) {
            return 0;
        }
        for (Map.Entry<String, Integer> e : losses.entrySet()) {
            String key = e.getKey();
            if (key.isEmpty()) continue;
            if (displayed.contains(key)) {
                return e.getValue();
            }
        }
        return 0;
    }

    public boolean shouldDecorateNametag(String name) {
        return isEnabled() && nametagSuffix.isValue() && getLossCount(name) > 0;
    }

    public void pruneStaleEntries() {
        if (!isEnabled()) {
            return;
        }
        int cooldown = resetCooldownSeconds.getInt();
        if (cooldown <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long windowMs = cooldown * 1000L;
        Iterator<Map.Entry<String, Long>> it = lastUseMs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            if (now - e.getValue() >= windowMs) {
                losses.remove(e.getKey());
                it.remove();
            }
        }
    }

    public Map<String, Integer> snapshot() {
        return new HashMap<>(losses);
    }

    public void resetAll() {
        losses.clear();
        lastUseMs.clear();
    }

    @Override
    public String getDescription() {
        return "Tracks who pops totems near you - prints to chat and adds \" | -N\" to their nametag";
    }

    @Override
    public String getIcon() {
        return "totem_tracker.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
