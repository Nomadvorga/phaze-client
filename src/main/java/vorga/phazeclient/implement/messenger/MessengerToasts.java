package vorga.phazeclient.implement.messenger;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.base.QuickImports;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.base.util.MessengerPrefs;
import vorga.phazeclient.base.util.MessengerService;
import vorga.phazeclient.base.util.SkinAvatarService;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.MenuStyle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * In-game messenger toasts in the top-right corner: a new message, a friend
 * coming online, a friend request or a group invite. At most three at once,
 * each for five seconds; the stream thread only enqueues, the HUD draws.
 */
public final class MessengerToasts implements QuickImports {
    private static final MessengerToasts INSTANCE = new MessengerToasts();
    private static final long LIFETIME_MS = 5_000L;
    private static final long FADE_MS = 220L;
    private static final int MAX_VISIBLE = 3;
    private static final int MAX_QUEUED = 8;
    private static final float WIDTH = 150.0F;
    private static final float HEIGHT = 30.0F;
    private static final float MARGIN = 6.0F;
    private static final float GAP = 4.0F;
    private static final float PAD = 6.0F;
    private static final float AVATAR = HEIGHT - PAD * 2.0F;
    private static final float TITLE_SIZE = 6.6F;
    private static final float BODY_SIZE = 5.8F;

    private record Toast(String username, String title, String body, long shownAt) {}

    private final Deque<Toast> queue = new ArrayDeque<>();
    private final List<Toast> visible = new ArrayList<>(MAX_VISIBLE);

    private MessengerToasts() {
    }

    public static void install() {
        MessengerService.getInstance().setNoticeListener(INSTANCE::offer);
    }

    public static void render(DrawContext context) {
        INSTANCE.draw(context);
    }

    private void offer(MessengerService.Notice notice) {
        MessengerPrefs prefs = MessengerPrefs.get();
        if (!prefs.toasts() || prefs.doNotDisturb()) return;
        if (notice.kind() == MessengerService.NoticeKind.FRIEND_ONLINE && !prefs.friendOnlineToasts()) return;
        MessengerService.Ping ping = notice.ping();
        // A marker the player can not see here is not worth interrupting them for.
        if (notice.kind() == MessengerService.NoticeKind.PING
                && (ping == null || !ChatWorld.isHere(ping.serverKey(), ping.dimension()))) return;
        String name = notice.username() == null ? "Unknown" : notice.username();
        String body = switch (notice.kind()) {
            case MESSAGE -> notice.text();
            case FRIEND_ONLINE -> Lang.t("messenger.toast_online");
            case FRIEND_REQUEST -> Lang.t("messenger.toast_request");
            case GROUP_INVITE -> Lang.t("messenger.toast_invite") + " «" + notice.text() + "»";
            case PING -> (notice.text() == null || notice.text().isBlank() ? Lang.t("messenger.toast_ping") : notice.text())
                    + " · " + ping.x() + " " + ping.y() + " " + ping.z();
        };
        String title = notice.kind() == MessengerService.NoticeKind.MESSAGE && notice.conversationId() != null
                ? titleFor(notice.conversationId(), name) : name;
        synchronized (queue) {
            if (queue.size() >= MAX_QUEUED) queue.pollFirst();
            queue.addLast(new Toast(name, title, body == null ? "" : body, 0L));
        }
    }

    private static String titleFor(String conversationId, String sender) {
        MessengerService.Group group = MessengerService.getInstance().getGroup(conversationId);
        return group == null ? sender : sender + " · " + group.name();
    }

    private void draw(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();
        visible.removeIf(toast -> now - toast.shownAt() > LIFETIME_MS);
        synchronized (queue) {
            while (visible.size() < MAX_VISIBLE && !queue.isEmpty()) {
                Toast next = queue.pollFirst();
                visible.add(new Toast(next.username(), next.title(), next.body(), now));
            }
        }
        if (visible.isEmpty() || client.options.hudHidden || client.currentScreen instanceof MenuScreen) return;

        float screenW = client.getWindow().getScaledWidth();
        float y = MARGIN;
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        for (Toast toast : visible) {
            long age = now - toast.shownAt();
            float in = Math.min(1.0F, age / (float) FADE_MS);
            float out = Math.min(1.0F, (LIFETIME_MS - age) / (float) FADE_MS);
            float alpha = Math.max(0.0F, Math.min(in, out));
            float ease = 1.0F - (1.0F - in) * (1.0F - in);
            float x = screenW - MARGIN - WIDTH + (1.0F - ease) * 18.0F;

            rectangle.render(ShapeProperties.create(context.getMatrices(), x, y, WIDTH, HEIGHT)
                    .round(5.0F).softness(1.0F).thickness(1.2F)
                    .outlineColor(MenuStyle.withAlpha(MenuStyle.BORDER, alpha))
                    .color(MenuStyle.withAlpha(MenuStyle.PANEL_BG, alpha * 0.94F)).build());
            BatchedRectangle.flushIfBatching();
            Identifier head = SkinAvatarService.getInstance().head(toast.username());
            drawHead(matrix, head, x + PAD, y + PAD, AVATAR, MenuStyle.withAlpha(0xFFFFFFFF, alpha));

            float textX = x + PAD + AVATAR + 6.0F;
            float textW = x + WIDTH - PAD - textX;
            MsdfRenderer.renderText(MsdfFonts.bold(), fit(toast.title(), textW, TITLE_SIZE, true), TITLE_SIZE,
                    MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, alpha), matrix, textX, y + PAD + 0.5F, 0.0F);
            MsdfRenderer.renderText(MsdfFonts.medium(), fit(toast.body(), textW, BODY_SIZE, false), BODY_SIZE,
                    MenuStyle.withAlpha(MenuStyle.TEXT_MUTED, alpha), matrix, textX, y + PAD + 10.0F, 0.0F);
            y += HEIGHT + GAP;
        }
    }

    private static String fit(String text, float width, float size, boolean bold) {
        String value = text == null ? "" : text.replace('\n', ' ');
        var font = bold ? MsdfFonts.bold() : MsdfFonts.medium();
        if (font.getWidth(value, size) <= width) return value;
        while (value.length() > 1 && font.getWidth(value + "...", size) > width) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "...";
    }

    private static void drawHead(Matrix4f matrix, Identifier texture, float x, float y, float size, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, x, y, 0.0F).texture(0.0F, 0.0F).color(color);
        buffer.vertex(matrix, x, y + size, 0.0F).texture(0.0F, 1.0F).color(color);
        buffer.vertex(matrix, x + size, y + size, 0.0F).texture(1.0F, 1.0F).color(color);
        buffer.vertex(matrix, x + size, y, 0.0F).texture(1.0F, 0.0F).color(color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }
}
