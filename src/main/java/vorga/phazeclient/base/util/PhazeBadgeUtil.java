package vorga.phazeclient.base.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.CharacterVisitor;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Shared badge helpers for marking known Phaze users in vanilla text
 * surfaces (chat, tab list, world nametags). The text only gains an
 * invisible fixed-width slot; the pixel-art icon is drawn over it.
 */
public final class PhazeBadgeUtil {
    public static final Identifier BADGE_ICON =
            Identifier.of("phaze", "textures/gui/phaze_user_badge.png");
    public static final Identifier CODE_BADGE_ICON =
            Identifier.of("phaze", "textures/gui/phaze_code_badge.png");
    private static final int FALLBACK_ICON_PIXELS = 16;
    /** Height of the badge's letter in GUI units next to 8-unit text rows. */
    public static final float GUI_BADGE_SIZE = 7.0F;
    /**
     * Invisible glyphs of the {@code phaze:badge} font that reserve the badge's slot. A font of
     * our own keeps the slot's width fixed under server resource packs that resize spaces, and
     * lets a rendered line say by itself whether, and which, badge it carries.
     */
    public static final Identifier BADGE_FONT = Identifier.of("phaze", "badge");
    public static final int NO_BADGE = 0;
    public static final int USER_BADGE = 1;
    public static final int CODE_BADGE = 2;
    private static final char USER_MARK = '\uE000';
    private static final char CODE_MARK = '\uE001';
    private static final Pattern LEADING_TAGS = Pattern.compile("^(?:\\[[^\\]]*]\\s*)+");
    /** Render-thread scratch for {@link #badgeAt}, so the per-frame check allocates nothing. */
    private static int firstMark;
    private static final CharacterVisitor FIRST_MARK = (index, style, codePoint) -> {
        firstMark = BADGE_FONT.equals(style.getFont()) ? markBadge(codePoint) : NO_BADGE;
        return false;
    };

    private PhazeBadgeUtil() {
    }

    public static boolean isPhazeUser(String username) {
        return RemoteRulesService.getInstance().isKnownClientUser(username);
    }

    public static boolean isCodeBadgeUser(String username) {
        return username != null
                && ("nomadvorgayt".equalsIgnoreCase(username) || "nomadvorga".equalsIgnoreCase(username));
    }

    public static boolean hasBadge(Text text) {
        return text != null && text.visit(
                (style, part) -> BADGE_FONT.equals(style.getFont()) ? StringVisitable.TERMINATE_VISIT : Optional.empty(),
                Style.EMPTY
        ).isPresent();
    }

    /** Which badge a rendered line opens with: {@link #USER_BADGE}, {@link #CODE_BADGE} or {@link #NO_BADGE}. */
    public static int badgeAt(OrderedText line) {
        firstMark = NO_BADGE;
        if (line != null) {
            line.accept(FIRST_MARK);
        }
        return firstMark;
    }

    /** The badge slot ahead of everything a server put in the name, prefixes and rank glyphs included. */
    public static Text withBadge(Text text, boolean codeBadge) {
        return withBadge(text, codeBadge, 0);
    }

    public static Text withBadge(Text text, boolean codeBadge, int extraSpaces) {
        if (text == null || hasBadge(text)) {
            return text;
        }
        MutableText prefixed = Text.empty().append(badgeSlot(codeBadge));
        if (extraSpaces > 0) {
            prefixed.append(Text.literal(" ".repeat(extraSpaces)));
        }
        return prefixed.append(text);
    }

    /**
     * Puts the badge slot at the start of the line that names the sender: servers that open a
     * message with blank or decorative lines still get the badge beside the nickname.
     */
    public static Text withChatBadge(Text text, String sender, boolean codeBadge) {
        if (text == null || hasBadge(text)) {
            return text;
        }
        int lineStart = senderLineStart(text.getString(), sender);
        if (lineStart <= 0) {
            return withBadge(text, codeBadge);
        }
        MutableText out = Text.empty();
        int[] offset = {0};
        text.visit((style, part) -> {
            int start = offset[0];
            offset[0] += part.length();
            if (lineStart >= start && lineStart < offset[0]) {
                int cut = lineStart - start;
                if (cut > 0) {
                    out.append(Text.literal(part.substring(0, cut)).setStyle(style));
                }
                out.append(badgeSlot(codeBadge));
                out.append(Text.literal(part.substring(cut)).setStyle(style));
            } else if (!part.isEmpty()) {
                out.append(Text.literal(part).setStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    /** Start of the first non-blank line naming {@code sender}, else of the first non-blank line. */
    static int senderLineStart(String flat, String sender) {
        String needle = sender == null || sender.isBlank() ? null : sender.toLowerCase(Locale.ROOT);
        int firstContent = -1;
        int start = 0;
        while (start <= flat.length()) {
            int end = flat.indexOf('\n', start);
            if (end < 0) {
                end = flat.length();
            }
            String line = Formatting.strip(flat.substring(start, end));
            if (line != null && !line.isBlank()) {
                if (firstContent < 0) {
                    firstContent = start;
                }
                if (needle != null && line.toLowerCase(Locale.ROOT).contains(needle)) {
                    return start;
                }
            }
            start = end + 1;
        }
        return Math.max(firstContent, 0);
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
        int wantedPixels = Math.max(1, Math.round(size * pose.m00() * guiScale));

        int[] plan = BadgeRaster.plan(wantedPixels, PhazeBadgeTextures.sourceSize(codeBadge));
        Identifier texture = PhazeBadgeTextures.texture(codeBadge, plan[0]);
        int texturePixels = plan[0];
        if (texture == null) {
            texture = badgeIcon(codeBadge);
            texturePixels = FALLBACK_ICON_PIXELS;
            plan[1] = Math.max(1, Math.round(wantedPixels / (float) FALLBACK_ICON_PIXELS));
        }

        int drawPixels = texturePixels * plan[1];
        Vector3f center = pose.transformPosition(x + size / 2.0F, y + size / 2.0F, 0.0F, new Vector3f());
        int left = Math.round(center.x * guiScale - drawPixels / 2.0F);
        int top = Math.round(center.y * guiScale - drawPixels / 2.0F);

        matrices.push();
        matrices.peek().getPositionMatrix().identity()
                .translate(0.0F, 0.0F, center.z)
                .scale(1.0F / guiScale, 1.0F / guiScale, 1.0F);
        context.drawTexture(
                RenderLayer::getGuiTextured,
                texture,
                left,
                top,
                0.0F,
                0.0F,
                drawPixels,
                drawPixels,
                texturePixels,
                texturePixels,
                texturePixels,
                texturePixels,
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
        drawGuiBadge(context, Math.round(x) + 0.5F, Math.round(y + 1.0F) + 0.5F, GUI_BADGE_SIZE, color, codeBadge);
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
        if (text == null || (text.indexOf(USER_MARK) < 0 && text.indexOf(CODE_MARK) < 0)) {
            return text;
        }
        return text.replace(String.valueOf(USER_MARK), "").replace(String.valueOf(CODE_MARK), "");
    }

    private static MutableText badgeSlot(boolean codeBadge) {
        return Text.literal(String.valueOf(codeBadge ? CODE_MARK : USER_MARK))
                .setStyle(Style.EMPTY.withFont(BADGE_FONT));
    }

    private static int markBadge(int codePoint) {
        return codePoint == CODE_MARK ? CODE_BADGE : codePoint == USER_MARK ? USER_BADGE : NO_BADGE;
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
}
