package vorga.phazeclient.base.util;

/**
 * Pixel math behind the badge icons, kept free of GL so it can be checked off-thread.
 * Pixels are packed ARGB, row-major.
 */
final class BadgeRaster {
    private static final int NEAR_OPAQUE = 250;

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
        // The exported art keeps 254 inside the letter; rounded up so nothing shows through it.
        for (int i = 0; i < out.length; i++) {
            if ((out[i] >>> 24) >= NEAR_OPAQUE) out[i] |= 0xFF000000;
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
        return reduce(square, target, true);
    }

    /**
     * Mip levels from the art itself down to one pixel, each half the previous one rounded down
     * as the GPU expects. The smaller ones are area-averaged with soft edges, so trilinear
     * filtering can blend between neighbours without a visible step.
     */
    static int[][] mipChain(Square square) {
        int top = square.size();
        int count = 32 - Integer.numberOfLeadingZeros(top);
        int[][] levels = new int[count][];
        for (int level = 0; level < count; level++) {
            int size = top >> level;
            int[] pixels = reduce(square, size, false);
            bleed(pixels, size);
            levels[level] = pixels;
        }
        return levels;
    }

    /**
     * Gives clear pixels next to the letter its neighbours' colour while keeping them clear, so
     * filtering that mixes them in fades the edge instead of darkening it.
     */
    static void bleed(int[] argb, int size) {
        boolean[] known = new boolean[argb.length];
        for (int i = 0; i < argb.length; i++) {
            known[i] = (argb[i] >>> 24) != 0;
            if (!known[i]) argb[i] = 0;
        }
        boolean[] filled = new boolean[argb.length];
        for (int pass = 0; pass < 4; pass++) {
            boolean changed = false;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int i = y * size + x;
                    if (known[i]) continue;
                    int r = 0, g = 0, b = 0, n = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || ny < 0 || nx >= size || ny >= size || !known[ny * size + nx]) continue;
                            int p = argb[ny * size + nx];
                            r += p >> 16 & 0xFF;
                            g += p >> 8 & 0xFF;
                            b += p & 0xFF;
                            n++;
                        }
                    }
                    if (n > 0) {
                        argb[i] = (r / n) << 16 | (g / n) << 8 | (b / n);
                        filled[i] = true;
                        changed = true;
                    }
                }
            }
            if (!changed) break;
            for (int i = 0; i < known.length; i++) {
                known[i] |= filled[i];
                filled[i] = false;
            }
        }
    }

    private static int[] reduce(Square square, int target, boolean hardEdge) {
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
            int outAlpha = hardEdge ? (a < 0.5F ? 0 : 255) : Math.min(255, Math.round(a * 255.0F));
            if (outAlpha == 0) continue;
            out[i] = outAlpha << 24
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
