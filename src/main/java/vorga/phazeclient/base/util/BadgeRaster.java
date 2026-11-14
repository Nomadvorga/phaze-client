package vorga.phazeclient.base.util;

/**
 * Pixel math behind the badge icons, kept free of GL so it can be checked off-thread.
 * Pixels are packed ARGB, row-major.
 */
final class BadgeRaster {
    private BadgeRaster() {
    }

    record Square(int size, int[] argb) {
    }

    /** The opaque part of the image, centred on a transparent square so it fills it edge to edge. */
    static Square cropSquare(int[] argb, int width, int height) {
        int minX = width, minY = height, maxX = -1, maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((argb[y * width + x] >>> 24) != 0) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            return new Square(0, new int[0]);
        }
        int cropWidth = maxX - minX + 1;
        int cropHeight = maxY - minY + 1;
        int size = Math.max(cropWidth, cropHeight);
        int offsetX = (size - cropWidth) / 2;
        int offsetY = (size - cropHeight) / 2;
        int[] out = new int[size * size];
        for (int y = 0; y < cropHeight; y++) {
            System.arraycopy(argb, (minY + y) * width + minX, out, (offsetY + y) * size + offsetX, cropWidth);
        }
        return new Square(size, out);
    }

    /**
     * Area-averaged reduction to {@code target}×{@code target}: every output pixel is the
     * alpha-weighted mean of exactly the source area it covers, so the result shown 1:1 keeps
     * as much of the drawing as that many screen pixels can hold. Pixels at least half covered
     * by the letter are solid and the rest clear, which keeps a hard pixel-art outline.
     */
    static int[] downsample(Square square, int target) {
        int size = square.size();
        if (target >= size) {
            return square.argb().clone();
        }
        float[] red = new float[target * target];
        float[] green = new float[target * target];
        float[] blue = new float[target * target];
        float[] alpha = new float[target * target];
        float ratio = target / (float) size;
        for (int sy = 0; sy < size; sy++) {
            float y0 = sy * ratio, y1 = (sy + 1) * ratio;
            for (int sx = 0; sx < size; sx++) {
                int pixel = square.argb()[sy * size + sx];
                float a = (pixel >>> 24) / 255.0F;
                if (a == 0.0F) continue;
                float r = (pixel >> 16 & 0xFF) * a, g = (pixel >> 8 & 0xFF) * a, b = (pixel & 0xFF) * a;
                float x0 = sx * ratio, x1 = (sx + 1) * ratio;
                for (int ty = (int) y0; ty < target && ty < y1; ty++) {
                    float coverY = Math.min(y1, ty + 1) - Math.max(y0, ty);
                    if (coverY <= 0.0F) continue;
                    for (int tx = (int) x0; tx < target && tx < x1; tx++) {
                        float cover = (Math.min(x1, tx + 1) - Math.max(x0, tx)) * coverY;
                        if (cover <= 0.0F) continue;
                        int i = ty * target + tx;
                        red[i] += r * cover;
                        green[i] += g * cover;
                        blue[i] += b * cover;
                        alpha[i] += a * cover;
                    }
                }
            }
        }
        int[] out = new int[target * target];
        for (int i = 0; i < out.length; i++) {
            float a = alpha[i];
            if (a < 0.5F) continue;
            out[i] = 0xFF000000
                    | channel(red[i] / a) << 16
                    | channel(green[i] / a) << 8
                    | channel(blue[i] / a);
        }
        return out;
    }

    /**
     * How a badge {@code wanted} screen pixels wide is drawn from art {@code source} pixels wide:
     * {@code [texture pixels, screen pixels per texture pixel]}. Larger than the art it is scaled
     * by a whole number so every texel stays a sharp block.
     */
    static int[] plan(int wanted, int source) {
        wanted = Math.max(1, wanted);
        if (source <= 0 || wanted <= source) {
            return new int[]{wanted, 1};
        }
        int scale = (wanted + source - 1) / source;
        return new int[]{Math.max(1, Math.round(wanted / (float) scale)), scale};
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }
}
