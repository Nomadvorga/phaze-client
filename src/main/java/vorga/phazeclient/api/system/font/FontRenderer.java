package vorga.phazeclient.api.system.font;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import vorga.phazeclient.api.system.font.entry.DrawEntry;
import vorga.phazeclient.api.system.font.glyph.Glyph;
import vorga.phazeclient.api.system.font.glyph.GlyphMap;
import vorga.phazeclient.base.QuickImports;
import vorga.phazeclient.base.util.color.ColorUtil;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.base.util.other.StringUtil;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.awt.*;

import static net.minecraft.client.render.VertexFormat.DrawMode.QUADS;
import static net.minecraft.client.render.VertexFormats.POSITION_TEXTURE_COLOR;

@Setter
@Accessors(chain = true)
public class FontRenderer implements QuickImports {
    private static final int STRING_BUILDER_POOL_SIZE = 16;
    private static final java.util.Queue<StringBuilder> STRING_BUILDER_POOL = new java.util.ArrayDeque<>(STRING_BUILDER_POOL_SIZE);

    static {
        for (int i = 0; i < STRING_BUILDER_POOL_SIZE; i++) {
            STRING_BUILDER_POOL.offer(new StringBuilder(256));
        }
    }

    public static double ANIMATION_TIME = 0.0;

    private final Object2ObjectMap<Identifier, ObjectList<DrawEntry>> GLYPH_PAGE_CACHE = new Object2ObjectOpenHashMap<>();
    private final ObjectList<GlyphMap> maps = new ObjectArrayList<>();
    private final java.util.Map<Character, Glyph> glyphCache = new java.util.HashMap<>();
    private static final java.util.Map<Long, Integer> COLOR_INTERP_CACHE = new java.util.HashMap<>();
    @Getter
    private Font font;

    public FontRenderer(Font font, float sizePx) {
        init(font, sizePx);
    }

    private void init(Font font, float sizePx) {
        this.font = font.deriveFont(sizePx * 2);
    }

    private GlyphMap generateMap(char from, char to) {
        GlyphMap glyphMap = new GlyphMap(from, to, this.font, randomIdentifier(), 5);
        maps.add(glyphMap);
        return glyphMap;
    }

    private Glyph locateGlyph(char glyph) {
        Glyph cached = glyphCache.get(glyph);
        if (cached != null) {
            return cached;
        }

        for (GlyphMap map : maps) {
            if (map.contains(glyph)) {
                Glyph found = map.getGlyph(glyph);
                glyphCache.put(glyph, found);
                return found;
            }
        }

        char base = (char) MathUtil.floorNearestMulN(glyph, 128);
        Glyph result = generateMap(base, (char) (base + 128)).getGlyph(glyph);
        glyphCache.put(glyph, result);
        return result;
    }

    private static StringBuilder acquireStringBuilder() {
        StringBuilder sb = STRING_BUILDER_POOL.poll();
        if (sb == null) {
            sb = new StringBuilder(256);
        } else {
            sb.setLength(0);
        }
        return sb;
    }

    private static void releaseStringBuilder(StringBuilder sb) {
        if (STRING_BUILDER_POOL.size() < STRING_BUILDER_POOL_SIZE) {
            sb.setLength(0);
            STRING_BUILDER_POOL.offer(sb);
        }
    }

    public void drawText(MatrixStack matrix, Text text, double x, double y) {
        StringBuilder sb = acquireStringBuilder();
        try {
            findStyle(sb, text);
            drawString(matrix, sb.toString(), x, y, ColorUtil.getText());
        } finally {
            releaseStringBuilder(sb);
        }
    }

    public void findStyle(StringBuilder sb, Text component) {
        Style style = component.getStyle();
        if (component.getSiblings().isEmpty()) {
            if (style.getColor() != null) sb.append(ColorUtil.formatting(style.getColor().getRgb()));
            sb.append(component.getString()).append(Formatting.RESET);
        } else component.getWithStyle(style).forEach(text -> findStyle(sb, text));
    }

    public void drawStringWithScroll(MatrixStack matrix, String text, double x, double y, float width, int color) {
        String separation = "  |  ";
        float textWidth = getStringWidth(text + separation);

        if (textWidth - width < 10) {
            drawString(matrix, text, x, y, color);
            return;
        }

        drawString(matrix, text + separation + text, x - MathUtil.textScrolling(textWidth), y, color);
    }

    public void drawString(MatrixStack matrix, String text, double x, double y, int color) {
        char[] chars = text.toCharArray();
        float xOffset = 0;
        float yOffset = 0;
        int lineStart = 0;
        StringBuilder stringColor = new StringBuilder();
        boolean colorFormat = false;
        boolean textColor = false;
        int clr = color;

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];

            if (c == '§') {
                colorFormat = true;
                continue;
            } else if (colorFormat) {
                colorFormat = false;
                char c1 = Character.toUpperCase(c);
                if (ColorUtil.colorCodes.containsKey(c1)) {
                    clr = new Color(ColorUtil.colorCodes.get(c1)).getRGB();
                } else if (c1 == 'R') {
                    clr = color;
                }
                continue;
            }

            if (c == '⏏') {
                if (textColor) {
                    try {
                        String colorString = stringColor.toString();
                        if (colorString.matches("\\d+")) {
                            clr = new Color(Integer.parseInt(colorString)).getRGB();
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                    stringColor.setLength(0);
                }
                textColor = !textColor;
                continue;
            } else if (textColor) {
                stringColor.append(c);
                continue;
            }

            if (c == '\n') {
                yOffset += getStringHeight(text.substring(lineStart, i)) - 2;
                xOffset = 0;
                lineStart = i + 1;
                continue;
            }

            Glyph glyph = locateGlyph(c);
            if (glyph != null) {
                if (glyph.value() != ' ') {
                    Identifier i1 = glyph.owner().bindToTexture;
                    DrawEntry entry = new DrawEntry(xOffset, yOffset, clr, glyph);
                    GLYPH_PAGE_CACHE.computeIfAbsent(i1, integer -> new ObjectArrayList<>()).add(entry);
                }
                xOffset += glyph.width();
            }
        }

        if (!GLYPH_PAGE_CACHE.isEmpty()) drawGlyphs(matrix, x, y);
        clearGlyphCache();
    }

    public void drawGradientString(MatrixStack matrix, String text, double x, double y, int colorStart, int colorEnd) {
        char[] chars = text.toCharArray();
        float xOffset = 0;
        float yOffset = 0;
        int lineStart = 0;
        int textLength = text.length();

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '\n') {
                yOffset += getStringHeight(text.substring(lineStart, i)) - 2;
                xOffset = 0;
                lineStart = i + 1;
                continue;
            }
            Glyph glyph = locateGlyph(c);
            if (glyph != null) {
                if (glyph.value() != ' ') {
                    float t = (float) i / (textLength - 1);
                    int color = interpolateColor(colorStart, colorEnd, t);
                    Identifier i1 = glyph.owner().bindToTexture;
                    DrawEntry entry = new DrawEntry(xOffset, yOffset, color, glyph);
                    GLYPH_PAGE_CACHE.computeIfAbsent(i1, integer -> new ObjectArrayList<>()).add(entry);
                }
                xOffset += glyph.width();
            }
        }

        if (!GLYPH_PAGE_CACHE.isEmpty()) drawGlyphs(matrix, x, y);
        clearGlyphCache();
    }

    public void drawAnimatedGradientString(MatrixStack matrix, String text, double x, double y,
                                          int colorStart, int colorEnd, double waveLength, double speed) {
        ANIMATION_TIME = System.currentTimeMillis() / 1000.0;

        char[] chars = text.toCharArray();
        float xOffset = 0;
        float yOffset = 0;
        int lineStart = 0;
        int textLength = text.length();

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '\n') {
                yOffset += getStringHeight(text.substring(lineStart, i)) - 2;
                xOffset = 0;
                lineStart = i + 1;
                continue;
            }
            Glyph glyph = locateGlyph(c);
            if (glyph != null) {
                if (glyph.value() != ' ') {
                    float t = (float) i / Math.max(1, textLength - 1);

                    double wave = Math.sin(t * waveLength - ANIMATION_TIME * speed);
                    float normalizedWave = (float) ((wave + 1.0) / 2.0);

                    int color = interpolateColor(colorStart, colorEnd, normalizedWave);
                    Identifier i1 = glyph.owner().bindToTexture;
                    DrawEntry entry = new DrawEntry(xOffset, yOffset, color, glyph);
                    GLYPH_PAGE_CACHE.computeIfAbsent(i1, integer -> new ObjectArrayList<>()).add(entry);
                }
                xOffset += glyph.width();
            }
        }

        if (!GLYPH_PAGE_CACHE.isEmpty()) drawGlyphs(matrix, x, y);
        clearGlyphCache();
    }

    public void drawAnimatedGradientString(MatrixStack matrix, String text, double x, double y,
                                          int colorStart, int colorEnd) {
        drawAnimatedGradientString(matrix, text, x, y, colorStart, colorEnd, Math.PI, 5.0);
    }

    private void clearGlyphCache() {
        for (ObjectList<DrawEntry> list : GLYPH_PAGE_CACHE.values()) {
            list.clear();
        }
        GLYPH_PAGE_CACHE.clear();
    }

    private void drawGlyphs(MatrixStack matrix, double x, double y) {
        // All FontRenderer.drawXxx variants funnel through here for the
        // actual GPU submission; flushing the BatchedRectangle queue
        // before the glyph BufferBuilder begins keeps draw order
        // consistent (text on top of rects) and avoids double-open
        // tessellator state.
        vorga.phazeclient.api.system.shape.batched.BatchedRectangle.flushIfBatching();

        matrix.push();
        matrix.translate(x, y - 3F, 0);
        matrix.scale(0.5F, 0.5F, 1);
        Matrix4f matrix4f = matrix.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        for (Identifier identifier : GLYPH_PAGE_CACHE.keySet()) {
            RenderSystem.setShaderTexture(0, identifier);
        BufferBuilder buffer = tessellator().begin(QUADS, POSITION_TEXTURE_COLOR);
            for (DrawEntry drawEntry : GLYPH_PAGE_CACHE.get(identifier)) {
                float x1 = drawEntry.atX();
                float y1 = drawEntry.atY();

                Glyph glyph = drawEntry.toDraw();
                GlyphMap glyphMap = glyph.owner();

                float width = glyph.width();
                float height = glyph.height();

                float u1 = (float) glyph.u() / glyphMap.width;
                float v1 = (float) glyph.v() / glyphMap.height;
                float u2 = (float) (glyph.u() + glyph.width()) / glyphMap.width;
                float v2 = (float) (glyph.v() + glyph.height()) / glyphMap.height;

                int color = drawEntry.color();

                buffer.vertex(matrix4f, x1 + 0, y1 + height, 0).texture(u1, v2).color(color);
                buffer.vertex(matrix4f, x1 + width, y1 + height, 0).texture(u2, v2).color(color);
                buffer.vertex(matrix4f, x1 + width, y1 + 0, 0).texture(u2, v1).color(color);
                buffer.vertex(matrix4f, x1 + 0, y1 + 0, 0).texture(u1, v1).color(color);
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }
        RenderSystem.disableBlend();
        matrix.pop();
    }

    public void drawCenteredString(MatrixStack stack, String s, double x, double y, int color) {
        drawString(stack, s, (int) (x - getStringWidth(s) / 2f), (float) y, color);
    }

    public float getStringWidth(Text text) {
        return text != null ? getStringWidth(text.getString()) : 0;
    }

    public float getStringWidth(String text) {
        float currentLine = 0;
        float maxPreviousLines = 0;
        boolean ignore = false;

        for (char c : text.toCharArray()) {
            if (ignore) {
                ignore = false;
                continue;
            } else if (c == '§') {
                ignore = true;
                continue;
            } else if (c == '\n') {
                maxPreviousLines = Math.max(currentLine, maxPreviousLines);
                currentLine = 0;
                continue;
            }

            Glyph glyph = locateGlyph(c);
            currentLine += glyph == null ? 0 : glyph.width();
        }

        return Math.max(currentLine, maxPreviousLines) / 2;
    }

    public float getStringHeight(Text text) {
        return getStringHeight(text.getString());
    }

    public float getStringHeight(String text) {
        float currentLine = 0;
        float previous = 0;

        for (char c : (text.isEmpty() ? " " : text).toCharArray()) {
            if (c == '\n') {
                currentLine = (currentLine == 0 ? locateGlyph(' ').height() : currentLine);
                previous += currentLine;
                currentLine = 0;
                continue;
            }
            Glyph glyph = locateGlyph(c);
            currentLine = Math.max(glyph == null ? 0 : glyph.height(), currentLine);
        }

        return currentLine + previous;
    }

    private int interpolateColor(int colorStart, int colorEnd, float t) {
        int quantizedT = (int) (t * 100);
        long key = ((long) colorStart << 32) | (colorEnd & 0xFFFFFFFFL) | ((long) quantizedT << 48);

        Integer cached = COLOR_INTERP_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        float startAlpha = (colorStart >> 24 & 255) / 255.0F;
        float startRed = (colorStart >> 16 & 255) / 255.0F;
        float startGreen = (colorStart >> 8 & 255) / 255.0F;
        float startBlue = (colorStart & 255) / 255.0F;

        float endAlpha = (colorEnd >> 24 & 255) / 255.0F;
        float endRed = (colorEnd >> 16 & 255) / 255.0F;
        float endGreen = (colorEnd >> 8 & 255) / 255.0F;
        float endBlue = (colorEnd & 255) / 255.0F;

        float alpha = startAlpha + t * (endAlpha - startAlpha);
        float red = startRed + t * (endRed - startRed);
        float green = startGreen + t * (endGreen - startGreen);
        float blue = startBlue + t * (endBlue - startBlue);

        int result = ((int) (alpha * 255.0F) << 24) | ((int) (red * 255.0F) << 16) | ((int) (green * 255.0F) << 8) | (int) (blue * 255.0F);

        if (COLOR_INTERP_CACHE.size() < 10000) {
            COLOR_INTERP_CACHE.put(key, result);
        }

        return result;
    }

    @Contract(value = "-> new", pure = true)
    public static @NotNull Identifier randomIdentifier() {
        return Identifier.of("phaze", "temp/" + StringUtil.randomString(32));
    }
}
