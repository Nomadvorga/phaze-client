package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ThreeDSkinsVoxelizer {
    public static final class BakedSkinLayers {
        public final List<ModelPart.Cuboid> hat = new ArrayList<>();
        public final List<ModelPart.Cuboid> jacket = new ArrayList<>();
        public final List<ModelPart.Cuboid> leftSleeveWide = new ArrayList<>();
        public final List<ModelPart.Cuboid> leftSleeveSlim = new ArrayList<>();
        public final List<ModelPart.Cuboid> rightSleeveWide = new ArrayList<>();
        public final List<ModelPart.Cuboid> rightSleeveSlim = new ArrayList<>();
        public final List<ModelPart.Cuboid> leftPants = new ArrayList<>();
        public final List<ModelPart.Cuboid> rightPants = new ArrayList<>();

        public boolean hasAny() {
            return !hat.isEmpty() || !jacket.isEmpty() || !leftSleeveWide.isEmpty()
                    || !rightSleeveWide.isEmpty() || !leftPants.isEmpty() || !rightPants.isEmpty();
        }
    }

    private static final Map<Identifier, BakedSkinLayers> CACHE = new ConcurrentHashMap<>();

    private ThreeDSkinsVoxelizer() {}

    public static void clearCache() {
        CACHE.clear();
    }

    public static BakedSkinLayers get(Identifier skinId) {
        if (skinId == null) return null;
        BakedSkinLayers existing = CACHE.get(skinId);
        if (existing != null) return existing;

        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null && client.getTextureManager() != null) {
                var tex = client.getTextureManager().getTexture(skinId);
                if (tex instanceof net.minecraft.client.texture.NativeImageBackedTexture nibt && nibt.getImage() != null) {
                    bake(skinId, nibt.getImage());
                    return CACHE.get(skinId);
                }
            }
        } catch (Throwable ignored) {}

        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null && client.getResourceManager() != null) {
                var res = client.getResourceManager().getResource(skinId);
                if (res.isPresent()) {
                    try (var is = res.get().getInputStream();
                         NativeImage img = NativeImage.read(is)) {
                        bake(skinId, img);
                        return CACHE.get(skinId);
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public static void bake(Identifier id, NativeImage image) {
        if (id == null || image == null) return;
        try {
            int w = image.getWidth();
            int h = image.getHeight();
            if (w < 64 || h < 64) return;

            BakedSkinLayers layers = new BakedSkinLayers();
            float th = ThreeDSkins.getInstance() != null ? ThreeDSkins.getInstance().voxelThickness.getValue() : 0.65f;

            // 1. Hat: (u=32..63, v=0..31), base size 8x8x8, center at (-4, -8, -4) with dilation 0.5f
            voxelizeBox(image, layers.hat, 8, 8, 8, -4.0f, -8.0f, -4.0f, 0.5f, 32, 0, th);

            // 2. Jacket: (u=16..39, v=32..47), base size 8x12x4, center at (-4, 0, -2) with dilation 0.25f
            voxelizeBox(image, layers.jacket, 8, 12, 4, -4.0f, 0.0f, -2.0f, 0.25f, 16, 32, th);

            // 3. Right Sleeve (wide): size 4x12x4, center at (-3, -2, -2) with dilation 0.25f
            voxelizeBox(image, layers.rightSleeveWide, 4, 12, 4, -3.0f, -2.0f, -2.0f, 0.25f, 40, 32, th);
            // Right Sleeve (slim): size 3x12x4, center at (-2, -2, -2) with dilation 0.25f
            voxelizeBox(image, layers.rightSleeveSlim, 3, 12, 4, -2.0f, -2.0f, -2.0f, 0.25f, 40, 32, th);

            // 4. Left Sleeve (wide): size 4x12x4, center at (-1, -2, -2) with dilation 0.25f
            voxelizeBox(image, layers.leftSleeveWide, 4, 12, 4, -1.0f, -2.0f, -2.0f, 0.25f, 48, 48, th);
            // Left Sleeve (slim): size 3x12x4, center at (-1, -2, -2) with dilation 0.25f
            voxelizeBox(image, layers.leftSleeveSlim, 3, 12, 4, -1.0f, -2.0f, -2.0f, 0.25f, 48, 48, th);

            // 5. Right Pants: (u=0..15, v=32..47), base size 4x12x4, center at (-2, 0, -2) with dilation 0.25f
            voxelizeBox(image, layers.rightPants, 4, 12, 4, -2.0f, 0.0f, -2.0f, 0.25f, 0, 32, th);

            // 6. Left Pants: (u=0..15, v=48..63), base size 4x12x4, center at (-2, 0, -2) with dilation 0.25f
            voxelizeBox(image, layers.leftPants, 4, 12, 4, -2.0f, 0.0f, -2.0f, 0.25f, 0, 48, th);

            if (layers.hasAny()) {
                CACHE.put(id, layers);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void voxelizeBox(NativeImage image, List<ModelPart.Cuboid> dest,
                                    int sizeX, int sizeY, int sizeZ,
                                    float baseX, float baseY, float baseZ,
                                    float dilation,
                                    int texU, int texV,
                                    float thickness) {
        float minX = baseX - dilation;
        float minY = baseY - dilation;
        float minZ = baseZ - dilation;
        float maxX = baseX + sizeX + dilation;
        float maxY = baseY + sizeY + dilation;
        float maxZ = baseZ + sizeZ + dilation;

        // Front Face (Z = minZ): uOffset = sizeZ, vOffset = sizeZ, dimensions: sizeX x sizeY
        int frontU = texU + sizeZ;
        int frontV = texV + sizeZ;
        voxelizePlane(image, dest, sizeX, sizeY, frontU, frontV, minX, minY, minZ - thickness, 1.0f, 1.0f, thickness, Direction.NORTH);

        // Back Face (Z = maxZ): uOffset = sizeZ + sizeX + sizeZ, vOffset = sizeZ, dimensions: sizeX x sizeY
        int backU = texU + sizeZ + sizeX + sizeZ;
        int backV = texV + sizeZ;
        voxelizePlane(image, dest, sizeX, sizeY, backU, backV, minX, minY, maxZ, 1.0f, 1.0f, thickness, Direction.SOUTH);

        // Right Face (X = minX): uOffset = 0, vOffset = sizeZ, dimensions: sizeZ x sizeY
        int rightU = texU;
        int rightV = texV + sizeZ;
        voxelizePlane(image, dest, sizeZ, sizeY, rightU, rightV, minX - thickness, minY, minZ, thickness, 1.0f, 1.0f, Direction.WEST);

        // Left Face (X = maxX): uOffset = sizeZ + sizeX, vOffset = sizeZ, dimensions: sizeZ x sizeY
        int leftU = texU + sizeZ + sizeX;
        int leftV = texV + sizeZ;
        voxelizePlane(image, dest, sizeZ, sizeY, leftU, leftV, maxX, minY, minZ, thickness, 1.0f, 1.0f, Direction.EAST);

        // Top Face (Y = minY): uOffset = sizeZ, vOffset = 0, dimensions: sizeX x sizeZ
        int topU = texU + sizeZ;
        int topV = texV;
        voxelizePlane(image, dest, sizeX, sizeZ, topU, topV, minX, minY - thickness, minZ, 1.0f, thickness, 1.0f, Direction.UP);

        // Bottom Face (Y = maxY): uOffset = sizeZ + sizeX, vOffset = 0, dimensions: sizeX x sizeZ
        int botU = texU + sizeZ + sizeX;
        int botV = texV;
        voxelizePlane(image, dest, sizeX, sizeZ, botU, botV, minX, maxY, minZ, 1.0f, thickness, 1.0f, Direction.DOWN);
    }

    private static void voxelizePlane(NativeImage image, List<ModelPart.Cuboid> dest,
                                      int width, int height,
                                      int startU, int startV,
                                      float startX, float startY, float startZ,
                                      float voxelSizeX, float voxelSizeY, float voxelSizeZ,
                                      Direction outward) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int u = startU + x;
                int v = startV + y;
                if (u < 0 || u >= image.getWidth() || v < 0 || v >= image.getHeight()) continue;

                int color = image.getColorArgb(u, v);
                int alpha = (color >> 24) & 0xFF;
                if (alpha < 15) continue; // transparent pixel -> no voxel

                // Calculate voxel world offset
                float vx = startX + (voxelSizeX > 0.9f ? x * voxelSizeX : 0);
                float vy = startY + (voxelSizeY > 0.9f ? y * voxelSizeY : 0);
                float vz = startZ + (voxelSizeZ > 0.9f ? (outward == Direction.UP || outward == Direction.DOWN ? y * voxelSizeZ : x * voxelSizeZ) : 0);

                // Build visible faces: cull internal faces where neighbor pixels exist
                Set<Direction> visibleFaces = EnumSet.of(outward);

                // Check 4 planar neighbors to cull side walls
                boolean hasLeft = x > 0 && isSolid(image, startU + x - 1, v);
                boolean hasRight = x < width - 1 && isSolid(image, startU + x + 1, v);
                boolean hasTop = y > 0 && isSolid(image, u, startV + y - 1);
                boolean hasBottom = y < height - 1 && isSolid(image, u, startV + y + 1);

                if (outward == Direction.NORTH || outward == Direction.SOUTH) {
                    if (!hasLeft) visibleFaces.add(Direction.WEST);
                    if (!hasRight) visibleFaces.add(Direction.EAST);
                    if (!hasTop) visibleFaces.add(Direction.UP);
                    if (!hasBottom) visibleFaces.add(Direction.DOWN);
                } else if (outward == Direction.EAST || outward == Direction.WEST) {
                    if (!hasLeft) visibleFaces.add(Direction.NORTH);
                    if (!hasRight) visibleFaces.add(Direction.SOUTH);
                    if (!hasTop) visibleFaces.add(Direction.UP);
                    if (!hasBottom) visibleFaces.add(Direction.DOWN);
                } else if (outward == Direction.UP || outward == Direction.DOWN) {
                    if (!hasLeft) visibleFaces.add(Direction.WEST);
                    if (!hasRight) visibleFaces.add(Direction.EAST);
                    if (!hasTop) visibleFaces.add(Direction.NORTH);
                    if (!hasBottom) visibleFaces.add(Direction.SOUTH);
                }

                ModelPart.Cuboid cuboid = new ModelPart.Cuboid(
                        u, v,
                        vx, vy, vz,
                        voxelSizeX, voxelSizeY, voxelSizeZ,
                        0.0f, 0.0f, 0.0f,
                        false,
                        64.0f, 64.0f,
                        visibleFaces
                );
                dest.add(cuboid);
            }
        }
    }

    private static boolean isSolid(NativeImage image, int u, int v) {
        if (u < 0 || u >= image.getWidth() || v < 0 || v >= image.getHeight()) return false;
        int color = image.getColorArgb(u, v);
        return ((color >> 24) & 0xFF) >= 15;
    }
}
