package vorga.phazeclient.base.util;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

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
    private static final int CODE_BADGE_RGB = 0xFF0000;
    private static final String BADGE_PADDING = "  ";
    private static final Pattern LEADING_TAGS = Pattern.compile("^(?:\\[[^\\]]*]\\s*)+");

    private PhazeBadgeUtil() {
    }

    public static boolean isPhazeUser(String username) {
        return RemoteRulesService.getInstance().isKnownClientUser(username);
    }

    public static boolean isCodeBadgeUser(String username) {
        return username != null
                && ("nomadvorgayt".equalsIgnoreCase(username)
                || "nomadvorga".equalsIgnoreCase(username));
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
        int drawSize = Math.max(1, Math.round(size));
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                badgeIcon(codeBadge),
                Math.round(x),
                Math.round(y),
                0.0F,
                0.0F,
                drawSize,
                drawSize,
                64,
                64,
                64,
                64,
                badgeColor(color, codeBadge)
        );
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
        int badgeColor = badgeColor(color, codeBadge);
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

    private static int badgeColor(int color, boolean codeBadge) {
        return codeBadge ? (color & 0xFF000000) | CODE_BADGE_RGB : color;
    }

    private static RenderLayer resolveTextRenderLayer(TextRenderer.TextLayerType layerType, boolean codeBadge) {
        // 1.21.11: the RenderLayer static factories moved to net.minecraft.client.render.RenderLayers
        // (getTextX -> textX). Same memoized instances, same blend/depth semantics.
        return switch (layerType) {
            case SEE_THROUGH -> RenderLayers.textSeeThrough(badgeIcon(codeBadge));
            case POLYGON_OFFSET -> RenderLayers.textPolygonOffset(badgeIcon(codeBadge));
            case NORMAL -> RenderLayers.text(badgeIcon(codeBadge));
        };
    }
}
