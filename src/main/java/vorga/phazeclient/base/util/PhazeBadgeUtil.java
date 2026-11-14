package vorga.phazeclient.base.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Shared badge helpers for marking known Phaze users in vanilla text
 * surfaces (chat, tab list, world nametags) without rewriting the
 * actual glyph stream into a custom font/icon character.
 */
public final class PhazeBadgeUtil {
    public static final Identifier BADGE_ICON =
            Identifier.of("phaze", "textures/gui/phaze_user_badge.png");
    public static final Identifier CODE_BADGE_ICON =
            Identifier.of("phaze", "textures/gui/phaze_code_badge.png");
    /**
     * Pixel art stays sharp only when every texel covers a whole number of screen pixels,
     * so each badge is drawn in two resolutions and the one that fits best is scaled up.
     */
    private static final BadgeTexture[] USER_TEXTURES = {
            new BadgeTexture(BADGE_ICON, 16),
            new BadgeTexture(Identifier.of("phaze", "textures/gui/phaze_user_badge_small.png"), 12)
    };
    private static final BadgeTexture[] CODE_TEXTURES = {
            new BadgeTexture(CODE_BADGE_ICON, 16),
            new BadgeTexture(Identifier.of("phaze", "textures/gui/phaze_code_badge_small.png"), 12)
    };
    private static final String BADGE_PADDING = "  ";
    private static final Pattern LEADING_TAGS = Pattern.compile("^(?:\\[[^\\]]*]\\s*)+");

    private PhazeBadgeUtil() {
    }

    public static boolean isPhazeUser(String username) {
        return RemoteRulesService.getInstance().isKnownClientUser(username);
    }

    public static boolean isCodeBadgeUser(String username) {
        return username != null
                && ("nomadvorgayt".equalsIgnoreCase(username) || "nomadvorga".equalsIgnoreCase(username));
    }

    public static boolean hasBadgePadding(Text text) {
        return text != null && hasBadgePadding(text.getString());
    }

    public static boolean hasBadgePadding(String text) {
        return text != null && text.startsWith(BADGE_PADDING);
    }

    public static Text withBadgePadding(Text text) {
        return withBadgePadding(text, BADGE_PADDING.length());
    }

    public static Text withBadgePadding(Text text, int spaces) {
        if (text == null || hasBadgePadding(text)) {
            return text;
        }
        MutableText prefixed = Text.literal(" ".repeat(Math.max(1, spaces)));
        prefixed.append(text.copy());
        return prefixed;
    }

    public static String extractChatSender(String flat) {
        if (flat == null || flat.isBlank()) {
            return null;
        }

        String trimmed = stripBadgePadding(flat).stripLeading();
        if (trimmed.startsWith("<")) {
            int close = trimmed.indexOf('>');
            if (close > 1) {
                return sanitizeUsername(trimmed.substring(1, close));
            }
        }

        int separator = findHeaderSeparator(trimmed, 80);
        if (separator < 0) {
            return findKnownUserNearChatStart(trimmed);
        }

        String header = trimmed.substring(0, separator).trim();
        String detected = findKnownUserNearChatStart(header);
        if (detected != null) {
            return detected;
        }

        header = LEADING_TAGS.matcher(header).replaceFirst("").trim();
        int trailingTag = header.lastIndexOf(']');
        if (trailingTag >= 0 && trailingTag + 1 < header.length()) {
            header = header.substring(trailingTag + 1).trim();
        }

        if (header.isEmpty()) {
            return null;
        }

        String[] tokens = header.split("\\s+");
        return tokens.length == 0 ? null : sanitizeUsername(tokens[tokens.length - 1]);
    }

    public static String extractNametagIdentity(String flat) {
        if (flat == null || flat.isBlank()) {
            return null;
        }
        String trimmed = stripBadgePadding(flat).trim();
        int suffix = trimmed.indexOf(" | ");
        if (suffix > 0) {
            trimmed = trimmed.substring(0, suffix);
        }
        String direct = sanitizeUsername(trimmed);
        if (direct != null && isPhazeUser(direct)) {
            return direct;
        }

        String embedded = PhazePlayerPresence.getInstance().findKnownUserInText(trimmed);
        if (embedded != null) {
            return embedded;
        }

        return direct;
    }

    public static float guiBadgeSize(TextRenderer renderer) {
        if (renderer == null) {
            return 12.0F;
        }
        return Math.max(8.0F, renderer.fontHeight - 1.0F) * 1.5F;
    }

    public static int alphaWhite(int color) {
        return (color & 0xFF000000) | 0x00FFFFFF;
    }

    public static void drawGuiBadge(DrawContext context, float x, float y, float size, int color) {
        drawGuiBadge(context, x, y, size, color, false);
    }

    public static void drawGuiBadge(DrawContext context, float x, float y, float size, int color, boolean codeBadge) {
        MinecraftClient client = MinecraftClient.getInstance();
        float guiScale = client == null ? 1.0F : (float) client.getWindow().getScaleFactor();
        MatrixStack matrices = context.getMatrices();
        Matrix4f pose = matrices.peek().getPositionMatrix();
        float wantedPixels = size * pose.m00() * guiScale;

        BadgeTexture texture = null;
        int texelPixels = 1;
        float bestError = Float.MAX_VALUE;
        for (BadgeTexture candidate : codeBadge ? CODE_TEXTURES : USER_TEXTURES) {
            int scale = Math.max(1, Math.round(wantedPixels / candidate.size()));
            float error = Math.abs(scale * candidate.size() - wantedPixels);
            if (error < bestError) {
                bestError = error;
                texture = candidate;
                texelPixels = scale;
            }
        }

        int drawPixels = texture.size() * texelPixels;
        Vector3f center = pose.transformPosition(x + size / 2.0F, y + size / 2.0F, 0.0F, new Vector3f());
        int left = Math.round(center.x * guiScale - drawPixels / 2.0F);
        int top = Math.round(center.y * guiScale - drawPixels / 2.0F);

        matrices.push();
        matrices.peek().getPositionMatrix().identity()
                .translate(0.0F, 0.0F, center.z)
                .scale(1.0F / guiScale, 1.0F / guiScale, 1.0F);
        context.drawTexture(
                RenderLayer::getGuiTextured,
                texture.id(),
                left,
                top,
                0.0F,
                0.0F,
                drawPixels,
                drawPixels,
                texture.size(),
                texture.size(),
                texture.size(),
                texture.size(),
                badgeColor(color)
        );
        matrices.pop();
    }

    public static void drawChatBadgeAsText(DrawContext context, TextRenderer renderer, float x, float y, int color) {
        drawChatBadgeAsText(context, renderer, x, y, color, false);
    }

    public static void drawChatBadgeAsText(
            DrawContext context, TextRenderer renderer, float x, float y, int color, boolean codeBadge
    ) {
        float size = renderer == null ? 8.0F : Math.max(8.0F, renderer.fontHeight - 1.0F);
        drawGuiBadge(context, Math.round(x), Math.round(y + 1.0F), size, color, codeBadge);
    }

    public static void drawWorldBadge(
            Matrix4f matrix,
            VertexConsumerProvider vertexConsumers,
            TextRenderer.TextLayerType layerType,
            float x,
            float y,
            float size,
            int light,
            int color,
            boolean codeBadge
    ) {
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(resolveTextRenderLayer(layerType, codeBadge));
        int badgeColor = badgeColor(color);
        vertexConsumer.vertex(matrix, x, y, 0.0F).color(badgeColor).texture(0.0F, 0.0F).light(light);
        vertexConsumer.vertex(matrix, x, y + size, 0.0F).color(badgeColor).texture(0.0F, 1.0F).light(light);
        vertexConsumer.vertex(matrix, x + size, y + size, 0.0F).color(badgeColor).texture(1.0F, 1.0F).light(light);
        vertexConsumer.vertex(matrix, x + size, y, 0.0F).color(badgeColor).texture(1.0F, 0.0F).light(light);
    }

    private static String stripBadgePadding(String text) {
        if (text == null) {
            return null;
        }
        return text.startsWith(BADGE_PADDING) ? text.substring(BADGE_PADDING.length()) : text;
    }

    private static int findHeaderSeparator(String text, int maxChars) {
        int limit = Math.min(text.length(), maxChars);
        for (int i = 0; i < limit; i++) {
            char c = text.charAt(i);
            if (c == ':' || c == '»' || c == '>' || c == '\u203A' || c == '\u2192' || c == '\u279C' || c == '\u27A4' || c == '\u27A1') {
                return i;
            }
        }
        return -1;
    }

    private static String findKnownUserNearChatStart(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String head = text.substring(0, Math.min(text.length(), 96)).trim();
        if (head.isEmpty()) {
            return null;
        }
        return PhazePlayerPresence.getInstance().findKnownUserInText(head);
    }

    private static String sanitizeUsername(String raw) {
        if (raw == null) {
            return null;
        }

        int start = 0;
        int end = raw.length();
        while (start < end && !isUsernameChar(raw.charAt(start))) {
            start++;
        }
        while (end > start && !isUsernameChar(raw.charAt(end - 1))) {
            end--;
        }
        if (start >= end) {
            return null;
        }

        String candidate = raw.substring(start, end).trim();
        return candidate.isEmpty() ? null : candidate.toLowerCase(Locale.ROOT);
    }

    private static boolean isUsernameChar(char c) {
        return c == '_' || Character.isLetterOrDigit(c);
    }

    private static Identifier badgeIcon(boolean codeBadge) {
        return codeBadge ? CODE_BADGE_ICON : BADGE_ICON;
    }

    /** The badges carry their own colours: only the caller's alpha is applied. */
    private static int badgeColor(int color) {
        return alphaWhite(color);
    }

    private static RenderLayer resolveTextRenderLayer(TextRenderer.TextLayerType layerType, boolean codeBadge) {
        Identifier icon = badgeIcon(codeBadge);
        return switch (layerType) {
            case SEE_THROUGH -> RenderLayer.getTextSeeThrough(icon);
            case POLYGON_OFFSET -> RenderLayer.getTextPolygonOffset(icon);
            case NORMAL -> RenderLayer.getText(icon);
        };
    }

    private record BadgeTexture(Identifier id, int size) {
    }
}
