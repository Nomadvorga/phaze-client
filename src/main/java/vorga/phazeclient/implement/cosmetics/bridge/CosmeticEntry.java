package vorga.phazeclient.implement.cosmetics.bridge;

import net.minecraft.util.Identifier;

public record CosmeticEntry(
        int id,
        CosmeticCategory category,
        String name,
        CosmeticKind kind,
        String modelResource,
        String textureResource,
        String previewResource,
        int textureWidth,
        int textureHeight,
        int frameCount,
        int frameWidth,
        int frameHeight,
        int frameTime,
        int price
) {
    public Identifier textureId() {
        return textureResource == null ? null : Identifier.of(textureResource);
    }
}
