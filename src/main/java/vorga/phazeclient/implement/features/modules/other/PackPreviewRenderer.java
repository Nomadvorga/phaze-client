package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compact, row-local preview; assets never come from the enabled pack stack. */
public final class PackPreviewRenderer {
    private static final int HEIGHT = 44;
    private static final int ICON_SIZE = 16;
    private static final int SLOT = 18;
    private static final int MAX_CACHED_PACKS = 3;
    private static final long MAX_CACHED_TEXTURE_BYTES = 32L * 1024 * 1024;
    private static final String[] BUCKETS = {"bucket", "lava_bucket", "water_bucket"};
    private static final String[] BOWS = {"bow", "bow_pulling_0", "bow_pulling_1", "bow_pulling_2"};
    private static final String[] TOOLS = {"iron_sword", "iron_pickaxe", "diamond_sword", "diamond_pickaxe", "totem_of_undying"};
    private static final Map<String, PackPreviewAssets.Data> CACHE = new LinkedHashMap<>(4, 0.75f, true);
    private static String hoveredPack;
    private static long hoveredAtNanos;
    private static long lastRenderedNanos;

    private PackPreviewRenderer() {
    }

    public static void clear(MinecraftClient client) {
        CACHE.values().forEach(PackPreviewAssets.Data::close);
        CACHE.clear();
        hoveredPack = null;
    }

    private static PackPreviewAssets.Data assets(MinecraftClient client, String packId) {
        PackPreviewAssets.Data data = CACHE.get(packId);
        if (data == null) {
            data = PackPreviewAssets.load(client, packId);
            CACHE.put(packId, data);
            long bytes = CACHE.values().stream().mapToLong(PackPreviewAssets.Data::textureBytes).sum();
            var iterator = CACHE.entrySet().iterator();
            while (CACHE.size() > 1 && (CACHE.size() > MAX_CACHED_PACKS || bytes > MAX_CACHED_TEXTURE_BYTES)) {
                PackPreviewAssets.Data oldest = iterator.next().getValue();
                bytes -= oldest.textureBytes();
                oldest.close();
                iterator.remove();
            }
        }
        return data;
    }

    public static void render(DrawContext context, String packId, int x, int y, int width, int height) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (packId == null || packId.isEmpty() || width <= 0 || height <= 0) {
            return;
        }
        PackPreviewAssets.Data data = assets(client, packId);
        long now = System.nanoTime();
        if (!packId.equals(hoveredPack) || now - lastRenderedNanos > 500_000_000L) {
            hoveredPack = packId;
            hoveredAtNanos = now;
        }
        lastRenderedNanos = now;
        long elapsed = Math.max(0, (now - hoveredAtNanos) / 1_000_000L);
        String resolution = data.resolution() > 0 ? data.resolution() + "x" : "—";
        int dividerX = 10 * SLOT + 1;
        int textX = dividerX + 5;
        int infoWidth = Math.max(client.textRenderer.getWidth("Res: " + resolution),
                client.textRenderer.getWidth("Animations: ✓"));
        infoWidth = Math.max(infoWidth, client.textRenderer.getWidth("Lightmap: ✓"));
        infoWidth = Math.max(infoWidth, client.textRenderer.getWidth("Sounds: ✓"));
        float scale = Math.min((float) width / (textX + infoWidth + 1), (float) height / HEIGHT);

        context.getMatrices().push();
        try {
            context.getMatrices().translate(x, y + (height - HEIGHT * scale) / 2.0f, 0);
            context.getMatrices().scale(scale, scale, 1.0f);
            List<PackPreviewAssets.Sprite> blocks = data.blocksAt(elapsed);
            for (int i = 0; i < 4; i++) {
                drawIcon(context, blocks.get(i), i * SLOT, 3);
            }
            drawIcon(context, data.items().get(BUCKETS[PackPreviewAssetLogic.batchIndex(elapsed, BUCKETS.length)]), 4 * SLOT, 3);
            drawIcon(context, data.items().get("golden_apple"), 5 * SLOT, 3);
            drawIcon(context, data.items().get("ender_pearl"), 6 * SLOT, 3);
            drawIcon(context, data.items().get(BOWS[PackPreviewAssetLogic.batchIndex(elapsed, BOWS.length)]), 7 * SLOT, 3);
            for (int i = 0; i < TOOLS.length; i++) {
                drawIcon(context, data.items().get(TOOLS[i]), i * SLOT, 25);
            }
            for (int i = 0; i < 3; i++) {
                drawIcon(context, data.hearts().get(i), (5 + i) * SLOT, 25);
            }
            drawIcon(context, data.particles().get("critical_hit"), 8 * SLOT, 25);
            drawIcon(context, data.particles().get("enchanted_hit"), 9 * SLOT, 25);
            context.fill(dividerX, 1, dividerX + 1, HEIGHT - 1, 0xFFFFFFFF);
            drawStatus(context, "Res: ", resolution, textX, 1, 0xFF71E58B);
            drawStatus(context, "Animations: ", data.animations() ? "✓" : "✕", textX, 12,
                    data.animations() ? 0xFF49E879 : 0xFFFF4D4D);
            drawStatus(context, "Lightmap: ", data.lightmap() ? "✓" : "✕", textX, 23,
                    data.lightmap() ? 0xFF49E879 : 0xFFFF4D4D);
            drawStatus(context, "Sounds: ", data.sounds() ? "✓" : "✕", textX, 34,
                    data.sounds() ? 0xFF49E879 : 0xFFFF4D4D);
        } finally {
            context.getMatrices().pop();
        }
    }

    private static void drawStatus(DrawContext context, String label, String value, int x, int y, int color) {
        var font = MinecraftClient.getInstance().textRenderer;
        context.drawText(font, label, x, y, 0xFFFFFFFF, false);
        context.drawText(font, value, x + font.getWidth(label), y, color, false);
    }

    private static void drawIcon(DrawContext context, PackPreviewAssets.Sprite sprite, int x, int y) {
        context.drawTexture(RenderLayer::getGuiTextured, sprite.id(), x, y, 0.0f, 0.0f,
                ICON_SIZE, ICON_SIZE, sprite.width(), sprite.height(), sprite.width(), sprite.height());
    }
}
