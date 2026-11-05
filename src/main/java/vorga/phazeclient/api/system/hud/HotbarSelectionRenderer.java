package vorga.phazeclient.api.system.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.Scaling;
import net.minecraft.util.Identifier;

import java.util.function.Function;

/** Crops the default selector's UVs instead of splitting the GUI batch with a GPU scissor. */
public final class HotbarSelectionRenderer {
    private HotbarSelectionRenderer() {}

    public static void drawClipped(DrawContext context, Function<Identifier, RenderLayer> layerFactory,
                                   Identifier texture, int x, int y, int width, int height,
                                   int left, int top, int right, int bottom) {
        int x1 = Math.max(x, left), y1 = Math.max(y, top);
        int x2 = Math.min(x + width, right), y2 = Math.min(y + height, bottom);
        if (x2 <= x1 || y2 <= y1) return;
        if (x1 == x && y1 == y && x2 == x + width && y2 == y + height) {
            context.drawGuiTexture(layerFactory, texture, x, y, width, height);
            return;
        }
        var atlas = MinecraftClient.getInstance().getGuiAtlasManager();
        if (atlas.getScaling(atlas.getSprite(texture)) instanceof Scaling.Stretch) {
            context.drawGuiTexture(layerFactory, texture, width, height,
                    x1 - x, y1 - y, x1, y1, x2 - x1, y2 - y1);
        } else {
            // Resource packs may tile/nine-slice this sprite. Keep their exact scaling semantics.
            context.enableScissor(left, top, right, bottom);
            try {
                context.drawGuiTexture(layerFactory, texture, x, y, width, height);
            } finally {
                context.disableScissor();
            }
        }
    }
}
