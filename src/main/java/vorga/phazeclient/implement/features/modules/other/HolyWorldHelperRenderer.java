package vorga.phazeclient.implement.features.modules.other;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import vorga.phazeclient.api.system.render.Render3DUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HolyWorldHelperRenderer {
    private static final float TRAP_RADIUS = 3.5F;
    private static final float TRAP_SHELL_THICKNESS = 1.0F;
    private static final boolean[][][] TRAP_SHELL = createTrapShell();
    private static final boolean[][][] UNDERGROUND_TRAP = createUndergroundTrap();
    private static final Face[] FACES = Face.values();
    private static final Geometry TRAP_GEOMETRY = createTrapGeometry();
    private static final Geometry UNDERGROUND_GEOMETRY = createUndergroundGeometry();
    private static final Geometry SMALL_CUBE_GEOMETRY = createCubeGeometry(3.0F);
    private static final Geometry STUN_CUBE_GEOMETRY = createCubeGeometry(30.0F);
    private static final BlockPos.Mutable GROUND_SCAN_POS = new BlockPos.Mutable();
    private static Object cachedGroundWorld;
    private static long cachedGroundTick = Long.MIN_VALUE;
    private static int cachedGroundMinX;
    private static int cachedGroundMaxX;
    private static int cachedGroundMinZ;
    private static int cachedGroundMaxZ;
    private static int cachedGroundStartY;
    private static boolean cachedAboveGround;

    private HolyWorldHelperRenderer() {
    }

    public static void render(MatrixStack matrices, Vec3d cameraPos, RenderTickCounter tickCounter) {
        HolyWorldHelper module = HolyWorldHelper.getInstance();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }

        HolyWorldHelper.HighlightType type = module.getHighlightType();
        if (type == HolyWorldHelper.HighlightType.NONE) return;

        var playerBlock = client.player.getBlockPos();
        // Build overlay vertices around the camera, like Predictions.
        // Converting absolute world positions to float breaks block-aligned
        // geometry once the player is far from spawn.
        float centerX = (float) (playerBlock.getX() + 0.5 - cameraPos.x);
        float centerY = (float) (playerBlock.getY() + 1.0 - cameraPos.y);
        float centerZ = (float) (playerBlock.getZ() + 0.5 - cameraPos.z);
        int color = module.resolveBoxColor();
        float fillOpacity = module.fillOpacity.getValue() / 100.0F;
        switch (type) {
            case TRAP -> drawGeometry(matrices, SMALL_CUBE_GEOMETRY, centerX, centerY + 0.5F, centerZ, color, fillOpacity);
            case JAKES_LAMP -> drawJakesLamp(matrices, centerX, (float) (playerBlock.getY() + 0.05 - cameraPos.y),
                    centerZ, color, fillOpacity);
            case STUN -> drawGeometry(matrices, STUN_CUBE_GEOMETRY, centerX, centerY, centerZ, color, fillOpacity);
            case EXPLOSIVE_TRAP -> {
                if (isAtLeastTwoBlocksAboveGround(client)) {
                    drawGeometry(matrices, TRAP_GEOMETRY, centerX, centerY, centerZ + 0.5F, color, fillOpacity);
                } else {
                    drawGeometry(matrices, UNDERGROUND_GEOMETRY, centerX, centerY, centerZ + 0.5F, color, fillOpacity);
                }
            }
            case NONE -> { }
        }
    }

    private static boolean[][][] createTrapShell() {
        boolean[][][] shell = new boolean[8][7][7];
        for (int y = 0; y < 8; y++) {
            for (int z = 0; z < 7; z++) {
                for (int x = 0; x < 7; x++) {
                    float dx = x - 3.0F;
                    float dy = y - 3.5F;
                    float dz = z - 3.0F;
                    float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    shell[y][z][x] = distance <= TRAP_RADIUS && distance >= TRAP_RADIUS - TRAP_SHELL_THICKNESS;
                }
            }
        }
        return shell;
    }

    private static boolean[][][] createUndergroundTrap() {
        String[][] layers = {
                {
                        "1122222211", "1122322111", "14......21", "2.......5.", "5........2",
                        "2........2", "2.......62", "2.......21", "22...5221.", ".1222621.."
                },
                {
                        "..222255..", ".6257526..", "68......2.", "2.......52", "5........2",
                        "2........2", "2.......62", "2.......2.", "21...952..", "..22222..."
                },
                {
                        "..2555....", ".665..555.", "2.......52", "5........6", "2........6",
                        "2........2", "6.......22", "2.......22", "2.....562.", ".2222226.."
                },
                {
                        ".55.......", "55........", "5........5", ".........5", "5........5",
                        "5........6", "........55", "5.......55", "5......55.", "55555555.."
                }
        };
        boolean[][][] shape = new boolean[4][10][10];
        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 10; z++) {
                for (int x = 0; x < 10; x++) {
                    shape[y][z][x] = layers[y][z].charAt(x) != '.';
                }
            }
        }
        return shape;
    }

    private static Geometry createCubeGeometry(float size) {
        List<FaceBox> faces = new ArrayList<>(6);
        Set<Edge> edges = new HashSet<>(12);
        float halfSize = size * 0.5F;
        for (Face face : FACES) {
            addGeometryFace(faces, edges, -halfSize, -halfSize, -halfSize, halfSize, halfSize, halfSize, face);
        }
        return Geometry.of(faces, edges);
    }

    private static Geometry createTrapGeometry() {
        List<FaceBox> faces = new ArrayList<>(256);
        Set<Edge> edges = new HashSet<>(512);
        for (int y = 0; y < 8; y++) {
            for (int z = 0; z < 7; z++) {
                for (int x = 0; x < 7; x++) {
                    if (!TRAP_SHELL[y][z][x]) continue;
                    float x1 = x - 3.5F;
                    float y1 = y - 4.0F;
                    float z1 = z - 3.5F;
                    for (Face face : FACES) {
                        if (isOutsideTrap(x + face.dx, y + face.dy, z + face.dz)) {
                            addGeometryFace(faces, edges, x1, y1, z1, x1 + 1.0F, y1 + 1.0F, z1 + 1.0F, face);
                        }
                    }
                }
            }
        }
        return Geometry.of(faces, edges);
    }

    private static Geometry createUndergroundGeometry() {
        List<FaceBox> faces = new ArrayList<>(512);
        Set<Edge> edges = new HashSet<>(1024);
        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 10; z++) {
                for (int x = 0; x < 10; x++) {
                    if (!UNDERGROUND_TRAP[y][z][x]) continue;
                    float x1 = x - 5.0F;
                    float y1 = y - 2.0F;
                    float z1 = z - 5.0F;
                    for (Face face : FACES) {
                        int nx = x + face.dx;
                        int ny = y + face.dy;
                        int nz = z + face.dz;
                        if (ny < 0 || ny >= 4 || nz < 0 || nz >= 10 || nx < 0 || nx >= 10
                                || !UNDERGROUND_TRAP[ny][nz][nx]) {
                            addGeometryFace(faces, edges, x1, y1, z1, x1 + 1.0F, y1 + 1.0F, z1 + 1.0F, face);
                        }
                    }
                }
            }
        }
        addGeometryFace(faces, edges, -5.0F, -2.01F, -5.0F, 5.0F, -2.0F, 5.0F, Face.UP);
        return Geometry.of(faces, edges);
    }

    private static boolean isAtLeastTwoBlocksAboveGround(MinecraftClient client) {
        if (client.world == null || client.player == null) return false;
        var box = client.player.getBoundingBox();
        int minX = MathHelper.floor(box.minX + 0.001);
        int maxX = MathHelper.floor(box.maxX - 0.001);
        int minZ = MathHelper.floor(box.minZ + 0.001);
        int maxZ = MathHelper.floor(box.maxZ - 0.001);
        int startY = MathHelper.floor(box.minY) - 1;
        long worldTick = client.world.getTime();
        if (cachedGroundWorld == client.world
                && cachedGroundTick == worldTick
                && cachedGroundMinX == minX && cachedGroundMaxX == maxX
                && cachedGroundMinZ == minZ && cachedGroundMaxZ == maxZ
                && cachedGroundStartY == startY) {
            return cachedAboveGround;
        }

        double highestSurface = Double.NEGATIVE_INFINITY;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = startY; y >= client.world.getBottomY(); y--) {
                    GROUND_SCAN_POS.set(x, y, z);
                    var shape = client.world.getBlockState(GROUND_SCAN_POS).getCollisionShape(client.world, GROUND_SCAN_POS);
                    if (!shape.isEmpty()) {
                        highestSurface = Math.max(highestSurface,
                                y + shape.getMax(net.minecraft.util.math.Direction.Axis.Y));
                        break;
                    }
                }
            }
        }
        cachedGroundWorld = client.world;
        cachedGroundTick = worldTick;
        cachedGroundMinX = minX;
        cachedGroundMaxX = maxX;
        cachedGroundMinZ = minZ;
        cachedGroundMaxZ = maxZ;
        cachedGroundStartY = startY;
        cachedAboveGround = highestSurface == Double.NEGATIVE_INFINITY || box.minY - highestSurface >= 2.0;
        return cachedAboveGround;
    }

    private static void drawJakesLamp(MatrixStack matrices, float centerX, float centerY, float centerZ, int color, float fillOpacity) {
        float radius = 7.5F;
        int fillColor = (Math.round(fillOpacity * 255.0F) << 24) | (color & 0x00FFFFFF);
        Render3DUtil.drawFlatGlowDisc(matrices, centerX, centerY, centerZ, radius, fillColor,
                color & 0x00FFFFFF, 64, true);
        Render3DUtil.drawThickFlatRing(matrices, centerX, centerY, centerZ, radius,
                0.20F, color, 64, true);
    }

    private static void drawGeometry(MatrixStack matrices, Geometry geometry, float offsetX, float offsetY, float offsetZ,
                                     int color, float fillOpacity) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float alpha = ((color >>> 24) & 255) / 255.0F;
        float red = ((color >>> 16) & 255) / 255.0F;
        float green = ((color >>> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        float fillAlpha = alpha * fillOpacity;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        if (fillAlpha > 0.0F) {
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            RenderSystem.polygonOffset(-1.0F, -1.0F);
            RenderSystem.enablePolygonOffset();
            BufferBuilder fill = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (FaceBox face : geometry.faces) {
                addFace(fill, matrix, face, offsetX, offsetY, offsetZ, red, green, blue, fillAlpha);
            }
            BufferRenderer.drawWithGlobalProgram(fill.end());
            RenderSystem.disablePolygonOffset();
        }

        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
        RenderSystem.lineWidth(2.0F);
        BufferBuilder outline = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        for (Edge edge : geometry.edges) {
            addLine(outline, matrix, edge, offsetX, offsetY, offsetZ, red, green, blue, alpha);
        }
        BufferRenderer.drawWithGlobalProgram(outline.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static boolean isOutsideTrap(int x, int y, int z) {
        float dx = x - 3.0F;
        float dy = y - 3.5F;
        float dz = z - 3.0F;
        return Math.sqrt(dx * dx + dy * dy + dz * dz) > TRAP_RADIUS;
    }

    private static void addGeometryFace(List<FaceBox> faces, Set<Edge> edges, float x1, float y1, float z1,
                                        float x2, float y2, float z2, Face face) {
        faces.add(new FaceBox(x1, y1, z1, x2, y2, z2, face));
        switch (face) {
            case DOWN -> addQuadEdges(edges, x1, y1, z1, x1, y1, z2, x2, y1, z2, x2, y1, z1);
            case UP -> addQuadEdges(edges, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2);
            case NORTH -> addQuadEdges(edges, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1);
            case SOUTH -> addQuadEdges(edges, x1, y1, z2, x1, y2, z2, x2, y2, z2, x2, y1, z2);
            case WEST -> addQuadEdges(edges, x1, y1, z1, x1, y2, z1, x1, y2, z2, x1, y1, z2);
            case EAST -> addQuadEdges(edges, x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1);
        }
    }

    private static void addQuadEdges(Set<Edge> edges,
                                     float ax, float ay, float az, float bx, float by, float bz,
                                     float cx, float cy, float cz, float dx, float dy, float dz) {
        edges.add(Edge.of(ax, ay, az, bx, by, bz));
        edges.add(Edge.of(bx, by, bz, cx, cy, cz));
        edges.add(Edge.of(cx, cy, cz, dx, dy, dz));
        edges.add(Edge.of(dx, dy, dz, ax, ay, az));
    }

    private static void addFace(BufferBuilder buffer, Matrix4f matrix, FaceBox box,
                                float offsetX, float offsetY, float offsetZ,
                                float r, float g, float b, float a) {
        float x1 = box.x1 + offsetX;
        float y1 = box.y1 + offsetY;
        float z1 = box.z1 + offsetZ;
        float x2 = box.x2 + offsetX;
        float y2 = box.y2 + offsetY;
        float z2 = box.z2 + offsetZ;
        switch (box.face) {
            case DOWN -> addQuad(buffer, matrix, r, g, b, a, x1, y1, z1, x1, y1, z2, x2, y1, z2, x2, y1, z1);
            case UP -> addQuad(buffer, matrix, r, g, b, a, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2);
            case NORTH -> addQuad(buffer, matrix, r, g, b, a, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1);
            case SOUTH -> addQuad(buffer, matrix, r, g, b, a, x1, y1, z2, x1, y2, z2, x2, y2, z2, x2, y1, z2);
            case WEST -> addQuad(buffer, matrix, r, g, b, a, x1, y1, z1, x1, y2, z1, x1, y2, z2, x1, y1, z2);
            case EAST -> addQuad(buffer, matrix, r, g, b, a, x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1);
        }
    }

    private static void addQuad(BufferBuilder buffer, Matrix4f matrix, float r, float g, float b, float a,
                                float ax, float ay, float az, float bx, float by, float bz,
                                float cx, float cy, float cz, float dx, float dy, float dz) {
        buffer.vertex(matrix, ax, ay, az).color(r, g, b, a);
        buffer.vertex(matrix, bx, by, bz).color(r, g, b, a);
        buffer.vertex(matrix, cx, cy, cz).color(r, g, b, a);
        buffer.vertex(matrix, dx, dy, dz).color(r, g, b, a);
    }

    private static void addLine(BufferBuilder buffer, Matrix4f matrix, Edge edge,
                                float offsetX, float offsetY, float offsetZ,
                                float r, float g, float b, float a) {
        buffer.vertex(matrix, edge.startX + offsetX, edge.startY + offsetY, edge.startZ + offsetZ)
                .color(r, g, b, a).normal(edge.normalX, edge.normalY, edge.normalZ);
        buffer.vertex(matrix, edge.endX + offsetX, edge.endY + offsetY, edge.endZ + offsetZ)
                .color(r, g, b, a).normal(edge.normalX, edge.normalY, edge.normalZ);
    }

    private record Geometry(FaceBox[] faces, Edge[] edges) {
        private static Geometry of(List<FaceBox> faces, Set<Edge> edges) {
            return new Geometry(faces.toArray(new FaceBox[0]), edges.toArray(new Edge[0]));
        }
    }

    private record FaceBox(float x1, float y1, float z1, float x2, float y2, float z2, Face face) {
    }

    private record Edge(float startX, float startY, float startZ, float endX, float endY, float endZ,
                        float normalX, float normalY, float normalZ) {
        private static Edge of(float firstX, float firstY, float firstZ, float secondX, float secondY, float secondZ) {
            if (compare(firstX, firstY, firstZ, secondX, secondY, secondZ) > 0) {
                float swapX = firstX;
                float swapY = firstY;
                float swapZ = firstZ;
                firstX = secondX;
                firstY = secondY;
                firstZ = secondZ;
                secondX = swapX;
                secondY = swapY;
                secondZ = swapZ;
            }
            float dx = secondX - firstX;
            float dy = secondY - firstY;
            float dz = secondZ - firstZ;
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float normalX = length == 0.0F ? 0.0F : dx / length;
            float normalY = length == 0.0F ? 1.0F : dy / length;
            float normalZ = length == 0.0F ? 0.0F : dz / length;
            return new Edge(firstX, firstY, firstZ, secondX, secondY, secondZ, normalX, normalY, normalZ);
        }

        private static int compare(float firstX, float firstY, float firstZ, float secondX, float secondY, float secondZ) {
            int x = Float.compare(firstX, secondX);
            if (x != 0) return x;
            int y = Float.compare(firstY, secondY);
            return y != 0 ? y : Float.compare(firstZ, secondZ);
        }
    }

    private enum Face {
        DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0);

        private final int dx;
        private final int dy;
        private final int dz;

        Face(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

    }
}
