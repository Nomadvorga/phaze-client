package vorga.phazeclient.api.system.threeskins;

import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class SkinMeshData {
    private final List<Voxel> voxels = new ArrayList<>();
    private final float pivotX, pivotY, pivotZ;
    private final int width, height, depth;

    public SkinMeshData(int width, int height, int depth, float pivotX, float pivotY, float pivotZ) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
    }

    public void addVoxel(int x, int y, int z, int color, float voxelSize) {
        voxels.add(new Voxel(x, y, z, color, voxelSize, new boolean[6]));
    }

    public void addVoxel(int x, int y, int z, int color, float voxelSize, boolean[] hideFaces) {
        voxels.add(new Voxel(x, y, z, color, voxelSize, hideFaces));
    }

    public boolean isEmpty() {
        return voxels.isEmpty();
    }

    public void render(Matrix4f positionMatrix, Matrix3f normalMatrix, VertexConsumer vertexConsumer, int light, int overlay) {
        for (Voxel v : voxels) {
            v.render(positionMatrix, normalMatrix, vertexConsumer, light, overlay, pivotX, pivotY, pivotZ);
        }
    }

    private static class Voxel {
        final float x, y, z;
        final int color;
        final float size;
        final boolean[] hideFaces; // DOWN, UP, NORTH, SOUTH, WEST, EAST

        Voxel(int x, int y, int z, int color, float voxelSize, boolean[] hideFaces) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
            this.size = voxelSize / 16.0f;
            this.hideFaces = hideFaces;
        }

        void render(Matrix4f matrix, Matrix3f normal, VertexConsumer vc, int light, int overlay,
                    float pivotX, float pivotY, float pivotZ) {
            float px = (x - pivotX) / 16.0f;
            float py = (y - pivotY) / 16.0f;
            float pz = (z - pivotZ) / 16.0f;

            float x0 = px;
            float y0 = py;
            float z0 = pz;
            float x1 = px + size;
            float y1 = py + size;
            float z1 = pz + size;

            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            int a = (color >> 24) & 0xFF;

            // Front face (+Z) - SOUTH
            if (!hideFaces[3]) {
                addQuad(vc, matrix, normal, light, overlay, r, g, b, a,
                        x0, y0, z1,
                        x1, y0, z1,
                        x1, y1, z1,
                        x0, y1, z1,
                        0, 0, 1);
            }

            // Back face (-Z) - NORTH
            if (!hideFaces[2]) {
                addQuad(vc, matrix, normal, light, overlay, r, g, b, a,
                        x1, y0, z0,
                        x0, y0, z0,
                        x0, y1, z0,
                        x1, y1, z0,
                        0, 0, -1);
            }

            // Top face (+Y) - UP
            if (!hideFaces[1]) {
                addQuad(vc, matrix, normal, light, overlay, r, g, b, a,
                        x0, y1, z1,
                        x1, y1, z1,
                        x1, y1, z0,
                        x0, y1, z0,
                        0, 1, 0);
            }

            // Bottom face (-Y) - DOWN
            if (!hideFaces[0]) {
                addQuad(vc, matrix, normal, light, overlay, r, g, b, a,
                        x0, y0, z0,
                        x1, y0, z0,
                        x1, y0, z1,
                        x0, y0, z1,
                        0, -1, 0);
            }

            // Right face (+X) - EAST
            if (!hideFaces[5]) {
                addQuad(vc, matrix, normal, light, overlay, r, g, b, a,
                        x1, y0, z1,
                        x1, y0, z0,
                        x1, y1, z0,
                        x1, y1, z1,
                        1, 0, 0);
            }

            // Left face (-X) - WEST
            if (!hideFaces[4]) {
                addQuad(vc, matrix, normal, light, overlay, r, g, b, a,
                        x0, y0, z0,
                        x0, y0, z1,
                        x0, y1, z1,
                        x0, y1, z0,
                        -1, 0, 0);
            }
        }

        private void addQuad(VertexConsumer vc, Matrix4f matrix, Matrix3f normal, int light, int overlay,
                             int r, int g, int b, int a,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float nx, float ny, float nz) {
            vc.vertex(matrix, x0, y0, z0).color(r, g, b, a).texture(0, 0).overlay(overlay).light(light).normal(nx, ny, nz);
            vc.vertex(matrix, x1, y1, z1).color(r, g, b, a).texture(1, 0).overlay(overlay).light(light).normal(nx, ny, nz);
            vc.vertex(matrix, x2, y2, z2).color(r, g, b, a).texture(1, 1).overlay(overlay).light(light).normal(nx, ny, nz);
            vc.vertex(matrix, x3, y3, z3).color(r, g, b, a).texture(0, 1).overlay(overlay).light(light).normal(nx, ny, nz);
        }
    }
}
