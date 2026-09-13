package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LightType;
import vorga.phazeclient.implement.features.modules.other.StreamerMode;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class BetterF3Renderer {
    private static final int FPS_HISTORY_SIZE = 60;
    private static final List<Integer> fpsHistory = new ArrayList<>();

    private static final int LABEL_COLOR = 0xFFAAB7C9;

    private static final int VALUE_DEFAULT = 0xFFFFFFFF;

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

    private BetterF3Renderer() {
    }

    public static void render(DrawContext context) {
        BetterF3 module = BetterF3.getInstance();
        if (module == null || !module.isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;
        if (mc.textRenderer == null) return;

        sampleFps(mc.getCurrentFps());

        ClientPlayerEntity player = mc.player;
        BlockPos pos = player.getBlockPos();
        boolean colorize = module.colorCoding.isValue();
        boolean shadow = module.textShadow.isValue();

        List<List<Segment>> rows = new ArrayList<>();

        if (module.showFps.isValue()) {
            int fps = mc.getCurrentFps();
            rows.add(line("FPS", String.valueOf(fps),
                    colorize ? colorForFps(fps) : VALUE_DEFAULT));
        }
        if (module.showCoords.isValue()) {
            if (StreamerMode.getInstance().isHideCoordinatesEnabled()) {
                rows.add(line("Pos", "hidden", VALUE_DEFAULT));
                rows.add(line("Chunk", "hidden", VALUE_DEFAULT));
            } else {

                List<Segment> row = new ArrayList<>();
                row.add(seg("Pos: ", LABEL_COLOR));
                row.add(seg(String.format("%.2f", player.getX()),
                        colorize ? 0xFFFF6B6B : VALUE_DEFAULT));
                row.add(seg(" / ", LABEL_COLOR));
                row.add(seg(String.format("%.2f", player.getY()),
                        colorize ? 0xFF6BFF6B : VALUE_DEFAULT));
                row.add(seg(" / ", LABEL_COLOR));
                row.add(seg(String.format("%.2f", player.getZ()),
                        colorize ? 0xFF6B9DFF : VALUE_DEFAULT));
                rows.add(row);

                rows.add(line("Chunk",
                        (pos.getX() & 15) + " " + (pos.getY() & 15) + " " + (pos.getZ() & 15)
                                + " in [" + (pos.getX() >> 4) + ", " + (pos.getZ() >> 4) + "]",
                        VALUE_DEFAULT));
            }
        }
        if (module.showFacing.isValue()) {
                rows.add(line("Facing",
                    facingFor(player) + " ("
                            + String.format("yaw=%.1f, pitch=%.1f",
                                    normalizedYaw(player.getYaw()), player.getPitch()) + ")",
                    VALUE_DEFAULT));
        }
        if (module.showBiome.isValue()) {
            String biome = mc.world.getBiome(pos).getKey()
                    .map(k -> k.getValue().toString())
                    .orElse("unknown");
            rows.add(line("Biome", biome, VALUE_DEFAULT));
        }
        if (module.showLight.isValue()) {
            int block = mc.world.getLightLevel(LightType.BLOCK, pos);
            int sky = mc.world.getLightLevel(LightType.SKY, pos);
            rows.add(line("Light", "block=" + block + ", sky=" + sky, VALUE_DEFAULT));
        }
        if (module.showDimension.isValue()) {
            rows.add(line("Dimension",
                    mc.world.getRegistryKey().getValue().toString(),
                    VALUE_DEFAULT));
        }
        if (module.showServer.isValue()) {
            String brand = "Singleplayer";
            if (mc.getNetworkHandler() != null && mc.getCurrentServerEntry() != null) {
                brand = mc.getCurrentServerEntry().address;
            }
            int ping = -1;
            if (mc.getNetworkHandler() != null && mc.player != null) {
                var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                if (entry != null) ping = entry.getLatency();
            }

            int pingColor = colorize && ping >= 0 ? colorForPing(ping) : VALUE_DEFAULT;
            List<Segment> row = new ArrayList<>();
            row.add(seg("Server: ", LABEL_COLOR));
            row.add(seg(brand, VALUE_DEFAULT));
            if (ping >= 0) {
                row.add(seg(" (", LABEL_COLOR));
                row.add(seg(ping + " ms", pingColor));
                row.add(seg(")", LABEL_COLOR));
            }
            rows.add(row);
        }
        if (module.showMemory.isValue()) {
            long maxMem = Runtime.getRuntime().maxMemory();
            long totalMem = Runtime.getRuntime().totalMemory();
            long freeMem = Runtime.getRuntime().freeMemory();
            long usedMem = totalMem - freeMem;
            int pct = (int) (usedMem * 100L / Math.max(1L, maxMem));
            int memColor = colorize ? colorForMemory(pct) : VALUE_DEFAULT;
            List<Segment> row = new ArrayList<>();
            row.add(seg("Mem: ", LABEL_COLOR));
            row.add(seg(pct + "%", memColor));
            row.add(seg(String.format(" (%d MB / %d MB)",
                    usedMem / 1024L / 1024L, maxMem / 1024L / 1024L), VALUE_DEFAULT));
            rows.add(row);
        }
        if (module.showTime.isValue()) {
            rows.add(line("Time", TIME_FORMAT.format(new Date()), VALUE_DEFAULT));
        }
        if (module.showSystem.isValue()) {
            rows.add(line("Java", System.getProperty("java.version") + " ("
                    + System.getProperty("java.vendor") + ")", VALUE_DEFAULT));
            rows.add(line("OS", System.getProperty("os.name") + " "
                    + System.getProperty("os.arch"), VALUE_DEFAULT));
        }
        if (module.showTargeted.isValue()) {
            HitResult target = mc.crosshairTarget;
            if (target instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
                BlockPos bp = bhr.getBlockPos();
                String id = mc.world.getBlockState(bp).getBlock().getName().getString();
                rows.add(line("Block",
                        id + " @ " + bp.getX() + " " + bp.getY() + " " + bp.getZ(),
                        VALUE_DEFAULT));
            } else if (target instanceof EntityHitResult ehr) {
                Text n = ehr.getEntity().getDisplayName();
                rows.add(line("Entity", n == null ? "?" : n.getString(), VALUE_DEFAULT));
            }
        }

        int lineHeight = module.compactMode.isValue() ? 9 : 10;
        int x = 4;
        int y = 4;
        int bgColor = mc.options.getTextBackgroundColor(0.5F);

        for (List<Segment> row : rows) {
            int rowWidth = 0;
            for (Segment s : row) rowWidth += mc.textRenderer.getWidth(s.text);

            context.fill(x - 1, y - 1, x + rowWidth + 1, y + lineHeight - 1, bgColor);
            int sx = x;
            for (Segment s : row) {
                if (shadow) {
                    context.drawTextWithShadow(mc.textRenderer, s.text, sx, y, s.color);
                } else {
                    context.drawText(mc.textRenderer, s.text, sx, y, s.color, false);
                }
                sx += mc.textRenderer.getWidth(s.text);
            }
            y += lineHeight;
        }

        if (module.showFps.isValue() && module.showFpsBar.isValue() && !fpsHistory.isEmpty()) {
            int barW = FPS_HISTORY_SIZE;
            int barH = 24;
            int barX = x;
            int barY = y + 2;
            int max = 1;
            for (int s : fpsHistory) if (s > max) max = s;
            context.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, bgColor);
            for (int i = 0; i < fpsHistory.size(); i++) {
                int sample = fpsHistory.get(i);
                int h = Math.max(1, sample * barH / max);
                int color = colorize ? colorForFps(sample) : 0xFFAAAAAA;
                context.fill(barX + i, barY + barH - h, barX + i + 1, barY + barH, color);
            }
        }
    }

    private static List<Segment> line(String name, String value, int valueColor) {
        List<Segment> row = new ArrayList<>(2);
        row.add(seg(name + ": ", LABEL_COLOR));
        row.add(seg(value, valueColor));
        return row;
    }

    private static Segment seg(String text, int color) {
        return new Segment(text, color);
    }

    private static void sampleFps(int fps) {
        fpsHistory.add(fps);
        while (fpsHistory.size() > FPS_HISTORY_SIZE) {
            fpsHistory.remove(0);
        }
    }

    private static String facingFor(Entity entity) {
        float yaw = normalizedYaw(entity.getYaw());
        if (yaw >= 315 || yaw < 45) return "South (+Z)";
        if (yaw < 135) return "West (-X)";
        if (yaw < 225) return "North (-Z)";
        return "East (+X)";
    }

    private static float normalizedYaw(float yaw) {
        return MathHelper.wrapDegrees(yaw);
    }

    private static int colorForFps(int fps) {
        if (fps < 30) return 0xFFFF5555;
        if (fps < 60) return 0xFFFFFF55;
        if (fps < 120) return 0xFF55FF55;
        return 0xFF55FFFF;
    }

    private static int colorForMemory(int pct) {
        if (pct >= 80) return 0xFFFF5555;
        if (pct >= 50) return 0xFFFFFF55;
        return 0xFF55FF55;
    }

    private static int colorForPing(int ping) {
        if (ping > 200) return 0xFFFF5555;
        if (ping > 80) return 0xFFFFFF55;
        return 0xFF55FF55;
    }

    private record Segment(String text, int color) {
    }
}
