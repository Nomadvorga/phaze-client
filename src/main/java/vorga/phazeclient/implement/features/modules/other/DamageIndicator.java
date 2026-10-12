package vorga.phazeclient.implement.features.modules.other;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public class DamageIndicator extends Module {
    private static final DamageIndicator INSTANCE = new DamageIndicator();
    private static boolean registered = false;

    public final BooleanSetting showHearts = new BooleanSetting("Show Hearts", "Include heart symbol in damage numbers").setValue(true);
    public final ColorSetting normalColor = new ColorSetting("Normal Color", "Color of normal hit damage").value(0xFFFF5555);
    public final ColorSetting critColor = new ColorSetting("Crit Color", "Color of critical hit damage").value(0xFFFFD700);
    public final ValueSetting lifetimeMs = new ValueSetting("Lifetime (ms)", "Duration number stays visible").range(400, 1500).step(50).setValue(800);
    public final ValueSetting scale = new ValueSetting("Scale", "Size of damage numbers").range(0.5f, 2.0f).step(0.1f).setValue(1.0f);

    private static final int MAX_INDICATORS = 32;
    private final double[] indX = new double[MAX_INDICATORS];
    private final double[] indY = new double[MAX_INDICATORS];
    private final double[] indZ = new double[MAX_INDICATORS];
    private final float[] indAmount = new float[MAX_INDICATORS];
    private final boolean[] indCrit = new boolean[MAX_INDICATORS];
    private final long[] indSpawnTime = new long[MAX_INDICATORS];
    private int indHead = 0;

    private LivingEntity targetEntity = null;
    private float lastEntityHealth = 0.0f;
    private long lastHitTimestamp = 0;

    private DamageIndicator() {
        super("damage_indicator", "Damage Indicator", ModuleCategory.OTHER);
        setup(showHearts, normalColor, critColor, lifetimeMs, scale);
        registerEvents();
    }

    public static DamageIndicator getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Display floating combat damage numbers above opponents";
    }

    @Override
    public String getIcon() {
        return "hit_range.png";
    }

    private synchronized void registerEvents() {
        if (registered) return;
        registered = true;

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() && isEnabled() && entity instanceof LivingEntity living) {
                targetEntity = living;
                lastEntityHealth = living.getHealth();
                lastHitTimestamp = System.currentTimeMillis();
            }
            return ActionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!isEnabled() || targetEntity == null) return;
            if (System.currentTimeMillis() - lastHitTimestamp > 500) {
                targetEntity = null;
                return;
            }

            float currentHp = targetEntity.getHealth();
            if (currentHp < lastEntityHealth) {
                float diff = lastEntityHealth - currentHp;
                if (diff > 0.05f) {
                    boolean isCrit = client.player != null && client.player.getVelocity().y < -0.08 && !client.player.isOnGround();
                    spawnIndicator(targetEntity.getX(), targetEntity.getY() + targetEntity.getHeight() * 0.7, targetEntity.getZ(), diff, isCrit);
                }
                targetEntity = null;
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!isEnabled()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            VertexConsumerProvider consumers = context.consumers();
            if (consumers == null) return;

            MatrixStack matrices = context.matrixStack();
            Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
            long now = System.currentTimeMillis();
            long maxLife = (long) lifetimeMs.getValue();
            float baseScale = scale.getValue();

            TextRenderer textRenderer = client.textRenderer;

            for (int i = 0; i < MAX_INDICATORS; i++) {
                long elapsed = now - indSpawnTime[i];
                if (elapsed >= maxLife || elapsed < 0) continue;

                float progress = (float) elapsed / (float) maxLife;
                float yOffset = progress * 0.75f;
                float alphaProgress = 1.0f - (progress * progress);
                int alpha = (int) (alphaProgress * 255.0f);
                if (alpha <= 5) continue;

                int baseRgb = indCrit[i] ? (critColor.getColor() & 0x00FFFFFF) : (normalColor.getColor() & 0x00FFFFFF);
                int finalColor = (alpha << 24) | baseRgb;

                float amount = indAmount[i];
                int whole = (int) amount;
                int tenth = (int) (amount * 10.0f) % 10;
                String str = "-" + whole + "." + tenth + (showHearts.isValue() ? " \u2764" : "");
                Text text = Text.literal(str);
                int textWidth = textRenderer.getWidth(text);

                matrices.push();
                matrices.translate(indX[i] - cameraPos.x, indY[i] + yOffset - cameraPos.y, indZ[i] - cameraPos.z);
                matrices.multiply(client.getEntityRenderDispatcher().getRotation());
                float s = 0.02f * baseScale * (indCrit[i] ? 1.25f : 1.0f);
                matrices.scale(-s, -s, s);

                textRenderer.draw(
                        text,
                        -textWidth / 2.0f,
                        0,
                        finalColor,
                        false,
                        matrices.peek().getPositionMatrix(),
                        consumers,
                        TextRenderer.TextLayerType.SEE_THROUGH,
                        0x00000000,
                        0xF000F0
                );
                matrices.pop();
            }
        });
    }

    private void spawnIndicator(double x, double y, double z, float amount, boolean isCrit) {
        int idx = indHead;
        indX[idx] = x;
        indY[idx] = y;
        indZ[idx] = z;
        indAmount[idx] = amount;
        indCrit[idx] = isCrit;
        indSpawnTime[idx] = System.currentTimeMillis();
        indHead = (indHead + 1) % MAX_INDICATORS;
    }
}
