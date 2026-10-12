package vorga.phazeclient.implement.features.modules.hud;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public class KillStreakHud extends RectHudModule {
    private static final KillStreakHud INSTANCE = new KillStreakHud();
    private static boolean registered = false;

    public final BooleanSetting playersOnly = new BooleanSetting("Players Only", "Count only player eliminations").setValue(true);
    public final ValueSetting posX = new ValueSetting("X Offset", "X screen coordinate").range(0, 1920).step(5).setValue(20);
    public final ValueSetting posY = new ValueSetting("Y Offset", "Y screen coordinate").range(0, 1080).step(5).setValue(120);

    private int streak = 0;
    private LivingEntity pendingTarget = null;
    private long lastHitTime = 0;

    private KillStreakHud() {
        super("killstreak_hud", "Killstreak", 20.0f, 120.0f, 1.0f);
        setup(playersOnly, posX, posY);
        registerEvents();
    }

    public static KillStreakHud getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "HUD counter tracking current and highest consecutive kill streaks";
    }

    @Override
    public String getIcon() {
        return "combo_counter_hud.png";
    }

    private synchronized void registerEvents() {
        if (registered) return;
        registered = true;

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() && isEnabled() && entity instanceof LivingEntity living) {
                pendingTarget = living;
                lastHitTime = System.currentTimeMillis();
            }
            return ActionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!isEnabled() || client.player == null) return;

            if (client.player.isDead() || client.player.getHealth() <= 0.0f) {
                streak = 0;
                pendingTarget = null;
                return;
            }

            if (pendingTarget != null) {
                if (System.currentTimeMillis() - lastHitTime > 2500) {
                    pendingTarget = null;
                } else if (pendingTarget.isDead() || pendingTarget.getHealth() <= 0.0f) {
                    if (!playersOnly.isValue() || pendingTarget instanceof PlayerEntity) {
                        streak++;
                    }
                    pendingTarget = null;
                }
            }
        });
    }

    public void render(DrawContext context) {
        if (!isEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || (client.options != null && client.options.hudHidden)) return;

        String textStr = "Streak: " + streak;
        int textWidth = client.textRenderer.getWidth(textStr);
        int pad = 4;
        int width = textWidth + pad * 2;
        int height = 14;

        int x = (int) posX.getValue();
        int y = (int) posY.getValue();

        if (background.isValue()) {
            int bg = getResolvedBackgroundColor(client);
            context.fill(x, y, x + width, y + height, bg);
        }

        context.drawText(client.textRenderer, textStr, x + pad, y + 3, 0xFFFFFFFF, textShadow.isValue());
    }
}
