package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

import java.util.*;

/**
 * Clean-room 3D skin layer voxelizer.
 * Extrudes outer skin layers into real 3D volume with:
 * - Exact 1x1 texel bounds (no texture bleeding or distortion)
 * - Closed inward faces (no hollow voids or see-through gaps when looking from angles)
 * - Coplanar neighbor face culling (removes hidden internal quads)
 * - Seamless edge alignment across all 6 directional faces
 */
public final class ThreeDSkinsVoxelizer {

    public record VoxelVertex(float x, float y, float z, float u, float v) {}
    public record VoxelQuad(VoxelVertex[] vertices, Vector3f normal) {}

    public static final class BakedSkinLayers {
        public final List<VoxelQuad> hat = new ArrayList<>();
        public final List<VoxelQuad> jacket = new ArrayList<>();
        public final List<VoxelQuad> leftSleeveWide = new ArrayList<>();
        public final List<VoxelQuad> leftSleeveSlim = new ArrayList<>();
        public final List<VoxelQuad> rightSleeveWide = new ArrayList<>();
        public final List<VoxelQuad> rightSleeveSlim = new ArrayList<>();
        public final List<VoxelQuad> leftPants = new ArrayList<>();
        public final List<VoxelQuad> rightPants = new ArrayList<>();

        public boolean hasAny() {
            return !hat.isEmpty() || !jacket.isEmpty() || !leftSleeveWide.isEmpty()
                    || !rightSleeveWide.isEmpty() || !leftPants.isEmpty() || !rightPants.isEmpty();
        }
    }

    private static final int CACHE_LIMIT = 128;
    private static final long RETRY_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
    // Java-owned alpha masks survive geometry invalidation without retaining native memory.
    // All cache access is synchronized, including capture on the downloader thread.
    private static final Map<Identifier, SkinSource> SOURCES = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<Identifier, Long> RETRIES = new LinkedHashMap<>(16, 0.75f, true);

    private static final class SkinSource {
        private final boolean[] solid;
        private BakedSkinLayers baked;

        private SkinSource(boolean[] solid) {
            this.solid = solid;
        }
    }

    private static <T> void trim(Map<Identifier, T> cache) {
        while (cache.size() > CACHE_LIMIT) {
            cache.remove(cache.keySet().iterator().next());
        }
    }

    private static final Vector3f NORMAL_NORTH = new Vector3f(0.0f, 0.0f, -1.0f);
    private static final Vector3f NORMAL_SOUTH = new Vector3f(0.0f, 0.0f, 1.0f);
    private static final Vector3f NORMAL_WEST  = new Vector3f(-1.0f, 0.0f, 0.0f);
    private static final Vector3f NORMAL_EAST  = new Vector3f(1.0f, 0.0f, 0.0f);
    private static final Vector3f NORMAL_UP    = new Vector3f(0.0f, -1.0f, 0.0f);
    private static final Vector3f NORMAL_DOWN  = new Vector3f(0.0f, 1.0f, 0.0f);

    private ThreeDSkinsVoxelizer() {}

    public static synchronized void clearCache() {
        SOURCES.values().forEach(source -> source.baked = null);
    }

    public static synchronized BakedSkinLayers get(Identifier skinId) {
        if (skinId == null) return null;
        SkinSource source = SOURCES.get(skinId);
        if (source != null) return bakeSource(source);

        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            // Texture ownership belongs to the client thread. Never close its image.
            if (client == null || !client.isOnThread()) return null;
            if (client != null && client.getTextureManager() != null) {
                var tex = client.getTextureManager().getTexture(skinId);
                if (tex instanceof net.minecraft.client.texture.NativeImageBackedTexture nibt && nibt.getImage() != null) {
                    bake(skinId, nibt.getImage());
                    source = SOURCES.get(skinId);
                    if (source != null) return bakeSource(source);
                }
            }
        } catch (RuntimeException ignored) {}

        long now = System.nanoTime();
        Long retry = RETRIES.get(skinId);
        if (retry != null && now - retry < RETRY_NANOS) return null;
        RETRIES.put(skinId, now);
        trim(RETRIES);

        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null && client.getResourceManager() != null) {
                var res = client.getResourceManager().getResource(skinId);
                if (res.isPresent()) {
                    try (var is = res.get().getInputStream();
                         NativeImage img = NativeImage.read(is)) {
                        bake(skinId, img);
                        source = SOURCES.get(skinId);
                        return source == null ? null : bakeSource(source);
                    }
                }
            }
        } catch (java.io.IOException | RuntimeException ignored) {}

        return null;
    }

    /** Capture while the caller still owns a live image; never retain or close it. */
    public static synchronized void capture(Identifier id, NativeImage image) {
        if (id == null || image == null) return;
        try {
            // Geometry and UVs below intentionally target the vanilla 64x64 layout.
            if (image.getWidth() != 64 || image.getHeight() != 64) return;
            boolean[] solid = new boolean[64 * 64];
            for (int v = 0; v < 64; v++) {
                for (int u = 0; u < 64; u++) {
                    solid[v * 64 + u] = ((image.getColorArgb(u, v) >>> 24) & 0xFF) >= 15;
                }
            }
            SOURCES.put(id, new SkinSource(solid));
            trim(SOURCES);
            RETRIES.remove(id);
        } catch (RuntimeException ignored) {}
    }

    public static synchronized void bake(Identifier id, NativeImage image) {
        capture(id, image);
        SkinSource source = SOURCES.get(id);
        if (source != null) bakeSource(source);
    }

    private static BakedSkinLayers bakeSource(SkinSource image) {
        if (image.baked != null) return image.baked;
            BakedSkinLayers layers = new BakedSkinLayers();
            float th = ThreeDSkins.getInstance() != null ? ThreeDSkins.getInstance().voxelThickness.getValue() : 0.65f;

            // 1. Hat: (texU=32, texV=0), size 8x8x8, base (-4, -8, -4), dilation 0.5f
            buildLayer(image, layers.hat, 8, 8, 8, -4.0f, -8.0f, -4.0f, 0.0f, 32, 0, th);

            // 2. Jacket: (texU=16, texV=32), size 8x12x4, base (-4, 0, -2), dilation 0.25f
            buildLayer(image, layers.jacket, 8, 12, 4, -4.0f, 0.0f, -2.0f, 0.0f, 16, 32, th);

            // 3. Right Sleeve (wide): size 4x12x4, base (-3, -2, -2), dilation 0.25f
            buildLayer(image, layers.rightSleeveWide, 4, 12, 4, -3.0f, -2.0f, -2.0f, 0.0f, 40, 32, th);
            // Right Sleeve (slim): size 3x12x4, base (-2, -2, -2), dilation 0.25f
            buildLayer(image, layers.rightSleeveSlim, 3, 12, 4, -2.0f, -2.0f, -2.0f, 0.0f, 40, 32, th);

            // 4. Left Sleeve (wide): size 4x12x4, base (-1, -2, -2), dilation 0.25f
            buildLayer(image, layers.leftSleeveWide, 4, 12, 4, -1.0f, -2.0f, -2.0f, 0.0f, 48, 48, th);
            // Left Sleeve (slim): size 3x12x4, base (-1, -2, -2), dilation 0.25f
            buildLayer(image, layers.leftSleeveSlim, 3, 12, 4, -1.0f, -2.0f, -2.0f, 0.0f, 48, 48, th);

            // 5. Right Pants: size 4x12x4, base (-2, 0, -2), dilation 0.25f
            buildLayer(image, layers.rightPants, 4, 12, 4, -2.0f, 0.0f, -2.0f, 0.0f, 0, 32, th);

            // 6. Left Pants: size 4x12x4, base (-2, 0, -2), dilation 0.25f
            buildLayer(image, layers.leftPants, 4, 12, 4, -2.0f, 0.0f, -2.0f, 0.0f, 0, 48, th);

            // Cache empty geometry too: transparent overlays must not rebake every frame.
            image.baked = layers;
            return layers;
    }

    private static void buildLayer(SkinSource image, List<VoxelQuad> dest,
                                   int sizeX, int sizeY, int sizeZ,
                                   float baseX, float baseY, float baseZ,
                                   float dilation,
                                   int texU, int texV,
                                   float thickness) {
        // 1. FRONT face (NORTH): width = sizeX, height = sizeY, texOffset = (sizeZ, sizeZ)
        voxelizePlane(image, dest,
                sizeX, sizeY,
                texU + sizeZ, texV + sizeZ,
                baseX, baseY, baseZ,
                sizeX, sizeY, sizeZ,
                dilation, thickness, PlaneType.NORTH);

        // 2. BACK face (SOUTH): width = sizeX, height = sizeY, texOffset = (sizeZ + sizeX + sizeZ, sizeZ)
        voxelizePlane(image, dest,
                sizeX, sizeY,
                texU + sizeZ + sizeX + sizeZ, texV + sizeZ,
                baseX, baseY, baseZ,
                sizeX, sizeY, sizeZ,
                dilation, thickness, PlaneType.SOUTH);

        // 3. RIGHT face (WEST): width = sizeZ, height = sizeY, texOffset = (0, sizeZ)
        voxelizePlane(image, dest,
                sizeZ, sizeY,
                texU, texV + sizeZ,
                baseX, baseY, baseZ,
                sizeX, sizeY, sizeZ,
                dilation, thickness, PlaneType.WEST);

        // 4. LEFT face (EAST): width = sizeZ, height = sizeY, texOffset = (sizeZ + sizeX, sizeZ)
        voxelizePlane(image, dest,
                sizeZ, sizeY,
                texU + sizeZ + sizeX, texV + sizeZ,
                baseX, baseY, baseZ,
                sizeX, sizeY, sizeZ,
                dilation, thickness, PlaneType.EAST);

        // 5. TOP face (UP): width = sizeX, height = sizeZ, texOffset = (sizeZ, 0)
        voxelizePlane(image, dest,
                sizeX, sizeZ,
                texU + sizeZ, texV,
                baseX, baseY, baseZ,
                sizeX, sizeY, sizeZ,
                dilation, thickness, PlaneType.UP);

        // 6. BOTTOM face (DOWN): width = sizeX, height = sizeZ, texOffset = (sizeZ + sizeX, 0)
        voxelizePlane(image, dest,
                sizeX, sizeZ,
                texU + sizeZ + sizeX, texV,
                baseX, baseY, baseZ,
                sizeX, sizeY, sizeZ,
                dilation, thickness, PlaneType.DOWN);
    }

    private enum PlaneType {
        NORTH, SOUTH, WEST, EAST, UP, DOWN
    }

    private static void voxelizePlane(SkinSource image, List<VoxelQuad> quads,
                                      int planeW, int planeH,
                                      int startU, int startV,
                                      float baseX, float baseY, float baseZ,
                                      int sizeX, int sizeY, int sizeZ,
                                      float dilation, float thickness,
                                      PlaneType plane) {
        for (int px = 0; px < planeW; px++) {
            for (int py = 0; py < planeH; py++) {
                int u = startU + px;
                int v = startV + py;
                if (!isSolid(image, u, v)) continue;

                // Precise UVs for this pixel with 0.005f inward margin to prevent bilinear filter edge bleed
                float u0 = (u + 0.005f) / 64.0f;
                float u1 = (u + 0.995f) / 64.0f;
                float v0 = (v + 0.005f) / 64.0f;
                float v1 = (v + 0.995f) / 64.0f;

                // Compute exact 3D voxel box bounds [x0, x1] x [y0, y1] x [z0, z1]
                float x0, x1, y0, y1, z0, z1;
                boolean cullLeft = false, cullRight = false, cullTop = false, cullBottom = false;

                switch (plane) {
                    case NORTH -> {
                        // Facing -Z (Front). px goes +X, py goes +Y
                        x0 = baseX - dilation + px;
                        x1 = x0 + 1.0f;
                        y0 = baseY - dilation + py;
                        y1 = y0 + 1.0f;
                        z0 = baseZ - dilation - thickness; // outward
                        z1 = baseZ - dilation;             // inward

                        cullLeft   = px > 0 && isSolid(image, startU + px - 1, v);          // -X (WEST)
                        cullRight  = px < planeW - 1 && isSolid(image, startU + px + 1, v);  // +X (EAST)
                        cullTop    = py > 0 && isSolid(image, u, startV + py - 1);          // -Y (UP)
                        cullBottom = py < planeH - 1 && isSolid(image, u, startV + py + 1);  // +Y (DOWN)

                        // Outward face: NORTH (always visible)
                        quads.add(quadNorth(x0, x1, y0, y1, z0, u0, v0, u1, v1));
                        // Side faces
                        if (!cullLeft)   quads.add(quadWest(x0, y0, y1, z0, z1, u0, v0, u1, v1));
                        if (!cullRight)  quads.add(quadEast(x1, y0, y1, z0, z1, u0, v0, u1, v1));
                        if (!cullTop)    quads.add(quadUp(x0, x1, y0, z0, z1, u0, v0, u1, v1));
                        if (!cullBottom) quads.add(quadDown(x0, x1, y1, z0, z1, u0, v0, u1, v1));
                    }
                    case SOUTH -> {
                        // Facing +Z (Back). In skin texture, px=0 is player's left (+X), so px is inverted
                        x0 = baseX - dilation + (sizeX - 1 - px);
                        x1 = x0 + 1.0f;
                        y0 = baseY - dilation + py;
                        y1 = y0 + 1.0f;
                        z0 = baseZ + sizeZ + dilation;             // inward
                        z1 = baseZ + sizeZ + dilation + thickness; // outward

                        // Inverted X: px - 1 is towards +X (EAST), px + 1 is towards -X (WEST)
                        cullRight  = px > 0 && isSolid(image, startU + px - 1, v);          // +X (EAST)
                        cullLeft   = px < planeW - 1 && isSolid(image, startU + px + 1, v);  // -X (WEST)
                        cullTop    = py > 0 && isSolid(image, u, startV + py - 1);          // -Y (UP)
                        cullBottom = py < planeH - 1 && isSolid(image, u, startV + py + 1);  // +Y (DOWN)

                        // Outward face: SOUTH
                        quads.add(quadSouth(x0, x1, y0, y1, z1, u0, v0, u1, v1));
                        // Side faces
                        if (!cullLeft)   quads.add(quadWest(x0, y0, y1, z0, z1, u0, v0, u1, v1));
                        if (!cullRight)  quads.add(quadEast(x1, y0, y1, z0, z1, u0, v0, u1, v1));
                        if (!cullTop)    quads.add(quadUp(x0, x1, y0, z0, z1, u0, v0, u1, v1));
                        if (!cullBottom) quads.add(quadDown(x0, x1, y1, z0, z1, u0, v0, u1, v1));
                    }
                    case WEST -> {
                        // Facing -X (Right). px=0 touches Back (+Z), px=sizeZ-1 touches Front (-Z)
                        x0 = baseX - dilation - thickness; // outward
                        x1 = baseX - dilation;             // inward
                        y0 = baseY - dilation + py;
                        y1 = y0 + 1.0f;
                        z0 = baseZ - dilation + (sizeZ - 1 - px);
                        z1 = z0 + 1.0f;

                        // Inverted Z: px - 1 is towards +Z (SOUTH), px + 1 is towards -Z (NORTH)
                        boolean cullBack  = px > 0 && isSolid(image, startU + px - 1, v);
                        boolean cullFront = px < planeW - 1 && isSolid(image, startU + px + 1, v);
                        cullTop    = py > 0 && isSolid(image, u, startV + py - 1);
                        cullBottom = py < planeH - 1 && isSolid(image, u, startV + py + 1);

                        // Outward face: WEST
                        quads.add(quadWest(x0, y0, y1, z0, z1, u0, v0, u1, v1));
                        // Side faces
                        if (!cullFront)  quads.add(quadNorth(x0, x1, y0, y1, z0, u0, v0, u1, v1));
                        if (!cullBack)   quads.add(quadSouth(x0, x1, y0, y1, z1, u0, v0, u1, v1));
                        if (!cullTop)    quads.add(quadUp(x0, x1, y0, z0, z1, u0, v0, u1, v1));
                        if (!cullBottom) quads.add(quadDown(x0, x1, y1, z0, z1, u0, v0, u1, v1));
                    }
                    case EAST -> {
                        // Facing +X (Left). px=0 touches Front (-Z), px=sizeZ-1 touches Back (+Z)
                        x0 = baseX + sizeX + dilation;             // inward
                        x1 = baseX + sizeX + dilation + thickness; // outward
                        y0 = baseY - dilation + py;
                        y1 = y0 + 1.0f;
                        z0 = baseZ - dilation + px;
                        z1 = z0 + 1.0f;

                        // Standard Z: px - 1 is towards -Z (NORTH), px + 1 is towards +Z (SOUTH)
                        boolean cullFront = px > 0 && isSolid(image, startU + px - 1, v);
                        boolean cullBack  = px < planeW - 1 && isSolid(image, startU + px + 1, v);
                        cullTop    = py > 0 && isSolid(image, u, startV + py - 1);
                        cullBottom = py < planeH - 1 && isSolid(image, u, startV + py + 1);

                        // Outward face: EAST
                        quads.add(quadEast(x1, y0, y1, z0, z1, u0, v0, u1, v1));
                        // Side faces
                        if (!cullFront)  quads.add(quadNorth(x0, x1, y0, y1, z0, u0, v0, u1, v1));
                        if (!cullBack)   quads.add(quadSouth(x0, x1, y0, y1, z1, u0, v0, u1, v1));
                        if (!cullTop)    quads.add(quadUp(x0, x1, y0, z0, z1, u0, v0, u1, v1));
                        if (!cullBottom) quads.add(quadDown(x0, x1, y1, z0, z1, u0, v0, u1, v1));
                    }
                    case UP -> {
                        // Facing -Y (Top). px goes +X. py=0 touches Back (+Z), py=sizeZ-1 touches Front (-Z)
                        x0 = baseX - dilation + px;
                        x1 = x0 + 1.0f;
                        y0 = baseY - dilation - thickness; // outward
                        y1 = baseY - dilation;             // inward
                        z0 = baseZ - dilation + (sizeZ - 1 - py);
                        z1 = z0 + 1.0f;

                        cullLeft  = px > 0 && isSolid(image, startU + px - 1, v);          // -X (WEST)
                        cullRight = px < planeW - 1 && isSolid(image, startU + px + 1, v);  // +X (EAST)
                        boolean cullBack  = py > 0 && isSolid(image, u, startV + py - 1);          // +Z (SOUTH)
                        boolean cullFront = py < planeH - 1 && isSolid(image, u, startV + py + 1);  // -Z (NORTH)

                        // Outward face: UP
                        quads.add(quadUp(x0, x1, y0, z0, z1, u0, v0, u1, v1));
                        // Side faces
                        if (!cullLeft)  quads.add(quadWest(x0, y0, y1, z0, z1, u0, v0, u1, v1));
                        if (!cullRight) quads.add(quadEast(x1, y0, y1, z0, z1, u0, v0, u1, v1));
                        if (!cullFront) quads.add(quadNorth(x0, x1, y0, y1, z0, u0, v0, u1, v1));
                        if (!cullBack)  quads.add(quadSouth(x0, x1, y0, y1, z1, u0, v0, u1, v1));
                    }
                    case DOWN -> {
                        // Facing +Y (Bottom). px goes +X. py=0 touches Front (-Z), py=sizeZ-1 touches Back (+Z)
                        x0 = baseX - dilation + px;
                        x1 = x0 + 1.0f;
                        y0 = baseY + sizeY + dilation;             // inward
                        y1 = baseY + sizeY + dilation + thickness; // outward
                        z0 = baseZ - dilation + py;
                        z1 = z0 + 1.0f;

                        cullLeft  = px > 0 && isSolid(image, startU + px - 1, v);          // -X (WEST)
                        cullRight = px < planeW - 1 && isSolid(image, startU + px + 1, v);  // +X (EAST)
                        boolean cullFront = py > 0 && isSolid(image, u, startV + py - 1);          // -Z (NORTH)
                        boolean cullBack  = py < planeH - 1 && isSolid(image, u, startV + py + 1);  // +Z (SOUTH)

                        // Outward face: DOWN
                        quads.add(quadDown(x0, x1, y1, z0, z1, u0, v0, u1, v1));
                        // Side faces
                        if (!cullLeft)  quads.add(quadWest(x0, y0, y1, z0, z1, u0, v0, u1, v1));
                        if (!cullRight) quads.add(quadEast(x1, y0, y1, z0, z1, u0, v0, u1, v1));
                        if (!cullFront) quads.add(quadNorth(x0, x1, y0, y1, z0, u0, v0, u1, v1));
                        if (!cullBack)  quads.add(quadSouth(x0, x1, y0, y1, z1, u0, v0, u1, v1));
                    }
                }
            }
        }
    }

    // Quad builders with exact Minecraft vertex winding and normals

    private static VoxelQuad quadNorth(float x0, float x1, float y0, float y1, float z, float u0, float v0, float u1, float v1) {
        VoxelVertex[] verts = new VoxelVertex[4];
        verts[0] = new VoxelVertex(x1, y0, z, u1, v0);
        verts[1] = new VoxelVertex(x0, y0, z, u0, v0);
        verts[2] = new VoxelVertex(x0, y1, z, u0, v1);
        verts[3] = new VoxelVertex(x1, y1, z, u1, v1);
        return new VoxelQuad(verts, NORMAL_NORTH);
    }

    private static VoxelQuad quadSouth(float x0, float x1, float y0, float y1, float z, float u0, float v0, float u1, float v1) {
        VoxelVertex[] verts = new VoxelVertex[4];
        verts[0] = new VoxelVertex(x0, y0, z, u1, v0);
        verts[1] = new VoxelVertex(x1, y0, z, u0, v0);
        verts[2] = new VoxelVertex(x1, y1, z, u0, v1);
        verts[3] = new VoxelVertex(x0, y1, z, u1, v1);
        return new VoxelQuad(verts, NORMAL_SOUTH);
    }

    private static VoxelQuad quadWest(float x, float y0, float y1, float z0, float z1, float u0, float v0, float u1, float v1) {
        VoxelVertex[] verts = new VoxelVertex[4];
        verts[0] = new VoxelVertex(x, y0, z0, u1, v0);
        verts[1] = new VoxelVertex(x, y0, z1, u0, v0);
        verts[2] = new VoxelVertex(x, y1, z1, u0, v1);
        verts[3] = new VoxelVertex(x, y1, z0, u1, v1);
        return new VoxelQuad(verts, NORMAL_WEST);
    }

    private static VoxelQuad quadEast(float x, float y0, float y1, float z0, float z1, float u0, float v0, float u1, float v1) {
        VoxelVertex[] verts = new VoxelVertex[4];
        verts[0] = new VoxelVertex(x, y0, z1, u1, v0);
        verts[1] = new VoxelVertex(x, y0, z0, u0, v0);
        verts[2] = new VoxelVertex(x, y1, z0, u0, v1);
        verts[3] = new VoxelVertex(x, y1, z1, u1, v1);
        return new VoxelQuad(verts, NORMAL_EAST);
    }

    private static VoxelQuad quadUp(float x0, float x1, float y, float z0, float z1, float u0, float v0, float u1, float v1) {
        VoxelVertex[] verts = new VoxelVertex[4];
        verts[0] = new VoxelVertex(x1, y, z1, u1, v0);
        verts[1] = new VoxelVertex(x0, y, z1, u0, v0);
        verts[2] = new VoxelVertex(x0, y, z0, u0, v1);
        verts[3] = new VoxelVertex(x1, y, z0, u1, v1);
        return new VoxelQuad(verts, NORMAL_UP);
    }

    private static VoxelQuad quadDown(float x0, float x1, float y, float z0, float z1, float u0, float v0, float u1, float v1) {
        VoxelVertex[] verts = new VoxelVertex[4];
        verts[0] = new VoxelVertex(x1, y, z0, u1, v0);
        verts[1] = new VoxelVertex(x0, y, z0, u0, v0);
        verts[2] = new VoxelVertex(x0, y, z1, u0, v1);
        verts[3] = new VoxelVertex(x1, y, z1, u1, v1);
        return new VoxelQuad(verts, NORMAL_DOWN);
    }

    private static boolean isSolid(SkinSource image, int u, int v) {
        return u >= 0 && u < 64 && v >= 0 && v < 64 && image.solid[v * 64 + u];
    }
}
