package vorga.phazeclient.implement.features.modules.other;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.util.render.PhazeRenderLayers;

public class Waypoints extends Module {
    private static final Waypoints INSTANCE = new Waypoints();
    private static boolean registered = false;

    public final BooleanSetting deathWaypoint = new BooleanSetting("Death Waypoint", "Automatically place a 3D marker upon death").setValue(true);
    public final BooleanSetting showBeam = new BooleanSetting("Show Beam", "Draw vertical light beam to the sky").setValue(true);
    public final ColorSetting deathColor = new ColorSetting("Death Color", "Color of death waypoint").value(0xFFFF3333);
    public final ValueSetting maxDistance = new ValueSetting("Max Distance", "Maximum render distance in blocks").range(200, 5000).step(100).setValue(2000);

    private boolean hasDeathPos = false;
    private double deathX = 0, deathY = 0, deathZ = 0;
    private boolean wasDead = false;

    private Waypoints() {
        super("waypoints", "Waypoints", ModuleCategory.OTHER);
        setup(deathWaypoint, showBeam, deathColor, maxDistance);
        registerEvents();
    }

    public static Waypoints getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "3D in-world beacons and markers with distance tags and death waypoints";
    }

    @Override
    public String getIcon() {
        return "death_coords.png";
    }

    private synchronized void registerEvents() {
        if (registered) return;
        registered = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!isEnabled() || client.player == null) return;

            boolean dead = client.player.isDead() || client.player.getHealth() <= 0.0f;
            if (dead && !wasDead && deathWaypoint.isValue()) {
                deathX = client.player.getX();
                deathY = client.player.getY();
                deathZ = client.player.getZ();
                hasDeathPos = true;
            }
            wasDead = dead;
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!isEnabled() || !hasDeathPos) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            VertexConsumerProvider consumers = context.consumers();
            if (consumers == null) return;

            MatrixStack matrices = context.matrixStack();
            Vec3d cameraPos = client.gameRenderer.getCamera().getPos();

            double dx = deathX - client.player.getX();
            double dy = deathY - client.player.getY();
            double dz = deathZ - client.player.getZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > maxDistance.getValue()) return;

            int col = deathColor.getColor();
            float a = ((col >> 24) & 0xFF) / 255.0f;
            float r = ((col >> 16) & 0xFF) / 255.0f;
            float g = ((col >> 8) & 0xFF) / 255.0f;
            float b = (col & 0xFF) / 255.0f;

            double rx = deathX - cameraPos.x;
            double ry = deathY - cameraPos.y;
            double rz = deathZ - cameraPos.z;

            // Vertical light beam
            if (showBeam.isValue()) {
                VertexConsumer consumer = consumers.getBuffer(PhazeRenderLayers.getThickLines(2.0f));
                Box beamBox = new Box(rx - 0.2, ry, rz - 0.2, rx + 0.2, ry + 128.0, rz + 0.2);
                VertexRendering.drawBox(matrices, consumer, beamBox, r, g, b, a * 0.7f);
            }

            // 3D Billboard text tag
            String textStr = "Death (" + (int) dist + "m)";
            Text text = Text.literal(textStr);
            TextRenderer textRenderer = client.textRenderer;
            int textWidth = textRenderer.getWidth(text);

            matrices.push();
            matrices.translate(rx, ry + 2.0, rz);
            matrices.multiply(client.getEntityRenderDispatcher().getRotation());
            float s = 0.035f * Math.max(1.0f, (float) (dist / 16.0));
            matrices.scale(-s, -s, s);

            textRenderer.draw(
                    text,
                    -textWidth / 2.0f,
                    0,
                    col,
                    false,
                    matrices.peek().getPositionMatrix(),
                    consumers,
                    TextRenderer.TextLayerType.SEE_THROUGH,
                    0x80000000,
                    0xF000F0
            );
            matrices.pop();
        });
    }
}
