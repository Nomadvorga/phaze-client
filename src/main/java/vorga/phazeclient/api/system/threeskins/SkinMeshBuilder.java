package vorga.phazeclient.api.system.threeskins;

import net.minecraft.client.texture.NativeImage;

public class SkinMeshBuilder {

    private static final float PIXEL_SIZE = 1.0f;

    public static SkinMeshData buildMesh(NativeImage skin, int width, int height, int depth,
                                          int textureU, int textureV, boolean topPivot,
                                          float voxelSize, boolean mirror) {
        float pivotX = width / 2.0f;
        float pivotY = topPivot ? 0 : height;
        float pivotZ = depth / 2.0f;

        SkinMeshData mesh = new SkinMeshData(width, height, depth, pivotX, pivotY, pivotZ);

        // Build voxels pixel by pixel like the original
        for (int faceDir = 0; faceDir < 6; faceDir++) {
            Direction face = Direction.values()[faceDir];
            int[] sizeUV = getSizeUV(width, height, depth, face);
            int sizeU = sizeUV[0];
            int sizeV = sizeUV[1];

            for (int u = 0; u < sizeU; u++) {
                for (int v = 0; v < sizeV; v++) {
                    int[] onTextureUV = getOnTextureUV(textureU, textureV, u, v, width, height, depth, face);
                    int texU = onTextureUV[0];
                    int texV = onTextureUV[1];

                    int pixel = getPixelSafe(skin, texU, texV);
                    int alpha = (pixel >> 24) & 0xFF;
                    if (alpha < 26) continue;

                    int[] voxelPos = UVtoXYZ(u, v, width, height, depth, face);
                    int x = voxelPos[0];
                    int y = voxelPos[1];
                    int z = voxelPos[2];

                    // Check which faces to hide
                    boolean[] hideFaces = new boolean[6];
                    checkHiddenFaces(skin, textureU, textureV, width, height, depth, x, y, z, face, hideFaces, alpha >= 250);

                    // Add voxel with hidden faces
                    mesh.addVoxel(x, y, z, pixel, voxelSize, hideFaces);
                }
            }
        }

        return mesh;
    }

    private static void checkHiddenFaces(NativeImage skin, int textureU, int textureV,
                                          int width, int height, int depth,
                                          int x, int y, int z, Direction currentFace,
                                          boolean[] hideFaces, boolean solidPixel) {
        // Hide the back face (opposite to current face)
        hideFaces[currentFace.getOpposite().ordinal()] = true;

        // Check neighboring voxels
        for (int i = 0; i < 6; i++) {
            Direction neighborFace = Direction.values()[i];
            if (neighborFace.getAxis() == currentFace.getAxis()) continue;

            int nx = x + neighborFace.getStepX();
            int ny = y + neighborFace.getStepY();
            int nz = z + neighborFace.getStepZ();

            // Check if neighbor is within bounds
            if (nx >= 0 && nx < width && ny >= 0 && ny < height && nz >= 0 && nz < depth) {
                // Check if neighbor pixel exists on current face
                int[] neighborUV = XYZtoUV(nx, ny, nz, width, height, depth, currentFace);
                if (neighborUV != null) {
                    int[] texUV = getOnTextureUV(textureU, textureV, neighborUV[0], neighborUV[1], width, height, depth, currentFace);
                    int neighborPixel = getPixelSafe(skin, texUV[0], texUV[1]);
                    int neighborAlpha = (neighborPixel >> 24) & 0xFF;

                    if (neighborAlpha >= 26) {
                        // Hide face if neighbor is solid or if both are transparent
                        if (!solidPixel || neighborAlpha >= 250) {
                            hideFaces[neighborFace.ordinal()] = true;
                        }
                    }
                }
            }
        }
    }

    private static int[] getSizeUV(int width, int height, int depth, Direction face) {
        return switch (face) {
            case DOWN, UP -> new int[]{width, depth};
            case NORTH, SOUTH -> new int[]{width, height};
            case WEST, EAST -> new int[]{depth, height};
        };
    }

    private static int[] getOnTextureUV(int textureU, int textureV, int u, int v,
                                         int width, int height, int depth, Direction face) {
        return switch (face) {
            case DOWN -> new int[]{textureU + depth + u, textureV + v};
            case UP -> new int[]{textureU + width + depth + u, textureV + v};
            case NORTH -> new int[]{textureU + depth + u, textureV + depth + v};
            case SOUTH -> new int[]{textureU + depth + width + depth + u, textureV + depth + v};
            case WEST -> new int[]{textureU + u, textureV + depth + v};
            case EAST -> new int[]{textureU + depth + width + u, textureV + depth + v};
        };
    }

    private static int[] UVtoXYZ(int u, int v, int width, int height, int depth, Direction face) {
        return switch (face) {
            case DOWN -> new int[]{u, 0, depth - 1 - v};
            case UP -> new int[]{u, height - 1, depth - 1 - v};
            case NORTH -> new int[]{u, v, 0};
            case SOUTH -> new int[]{width - 1 - u, v, depth - 1};
            case WEST -> new int[]{0, v, depth - 1 - u};
            case EAST -> new int[]{width - 1, v, u};
        };
    }

    private static int[] XYZtoUV(int x, int y, int z, int width, int height, int depth, Direction face) {
        int[] result = switch (face) {
            case DOWN, UP -> new int[]{x, depth - 1 - z};
            case NORTH -> new int[]{x, y};
            case SOUTH -> new int[]{width - 1 - x, y};
            case WEST -> new int[]{depth - 1 - z, y};
            case EAST -> new int[]{z, y};
        };

        // Validate bounds
        int[] sizeUV = getSizeUV(width, height, depth, face);
        if (result[0] < 0 || result[0] >= sizeUV[0] || result[1] < 0 || result[1] >= sizeUV[1]) {
            return null;
        }
        return result;
    }

    private static int getPixelSafe(NativeImage image, int x, int y) {
        if (x < 0 || x >= image.getWidth() || y < 0 || y >= image.getHeight()) {
            return 0;
        }
        try {
            java.lang.reflect.Method getColorMethod = NativeImage.class.getDeclaredMethod("method_61940", int.class, int.class);
            getColorMethod.setAccessible(true);
            return (int) getColorMethod.invoke(image, x, y);
        } catch (Exception e) {
            return 0;
        }
    }

    private enum Direction {
        DOWN(0, -1, 0, 1),
        UP(0, 1, 0, 1),
        NORTH(0, 0, -1, 2),
        SOUTH(0, 0, 1, 2),
        WEST(-1, 0, 0, 0),
        EAST(1, 0, 0, 0);

        private final int stepX, stepY, stepZ, axis;

        Direction(int stepX, int stepY, int stepZ, int axis) {
            this.stepX = stepX;
            this.stepY = stepY;
            this.stepZ = stepZ;
            this.axis = axis;
        }

        public int getStepX() { return stepX; }
        public int getStepY() { return stepY; }
        public int getStepZ() { return stepZ; }
        public int getAxis() { return axis; }

        public Direction getOpposite() {
            return switch (this) {
                case DOWN -> UP;
                case UP -> DOWN;
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case WEST -> EAST;
                case EAST -> WEST;
            };
        }
    }
}
