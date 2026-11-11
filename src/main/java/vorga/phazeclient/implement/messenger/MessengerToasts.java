package vorga.phazeclient.implement.messenger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import org.joml.Matrix4f;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.QuickImports;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.base.util.MessengerPrefs;
import vorga.phazeclient.base.util.MessengerService;
import vorga.phazeclient.base.util.SkinAvatarService;
import vorga.phazeclient.implement.features.modules.other.PhazeSounds;
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

    private static final long PING_SOUND_GAP_MS = 1_500L;
    private volatile long lastPingAt;
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
        if (prefs.doNotDisturb()) return;
        if (notice.kind() == MessengerService.NoticeKind.POINT) {
            playSound(PhazeSounds.WAYPOINT, 1.0F);
            if (notice.conversationId() != null && notice.conversationId().equals(openConversation())) return;
        }
        if (!prefs.toasts()) return;
        if (notice.kind() == MessengerService.NoticeKind.FRIEND_ONLINE && !prefs.friendOnlineToasts()) return;
        MessengerService.Ping ping = notice.ping();
        // A marker the player can not see here is not worth interrupting them for.
        if (notice.kind() == MessengerService.NoticeKind.PING
                && (ping == null || !ChatWorld.isHere(ping.serverKey(), ping.dimension()))) return;
        if (notice.kind() == MessengerService.NoticeKind.MENTION) {
            playSound(PhazeSounds.PING, 0.9F);
            // Already reading that chat: the sound is enough.
            if (notice.conversationId() != null && notice.conversationId().equals(openConversation())) return;
        }
        String name = notice.username() == null ? "Unknown" : notice.username();
        String body = switch (notice.kind()) {
            case MESSAGE -> notice.text();
            case MENTION -> Lang.t("messenger.toast_mention") + ": " + notice.text();
            case POINT -> Lang.t("messenger.toast_point") + ": " + notice.text();
            case FRIEND_ONLINE -> Lang.t("messenger.toast_online");
            case FRIEND_REQUEST -> Lang.t("messenger.toast_request");
            case GROUP_INVITE -> Lang.t("messenger.toast_invite") + " «" + notice.text() + "»";
            case PING -> (notice.text() == null || notice.text().isBlank() ? Lang.t("messenger.toast_ping") : notice.text())
                    + " · " + ping.x() + " " + ping.y() + " " + ping.z();
        };
        boolean chat = notice.kind() == MessengerService.NoticeKind.MESSAGE
                || notice.kind() == MessengerService.NoticeKind.MENTION
                || notice.kind() == MessengerService.NoticeKind.POINT;
        String title = chat && notice.conversationId() != null ? titleFor(notice.conversationId(), name) : name;
        synchronized (queue) {
            if (queue.size() >= MAX_QUEUED) queue.pollFirst();
            queue.addLast(new Toast(name, title, body == null ? "" : body, 0L));
        }
    }

    private static String titleFor(String conversationId, String sender) {
        MessengerService.Group group = conversationId == null ? null : MessengerService.getInstance().getGroup(conversationId);
        return group == null ? sender : sender + " · " + group.name();
    }

    /** One sound per burst: a flood of mentions or points must not turn into a flood of sounds. */
    private void playSound(net.minecraft.sound.SoundEvent sound, float volume) {
        long now = System.currentTimeMillis();
        if (now - lastPingAt < PING_SOUND_GAP_MS) return;
        lastPingAt = now;
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> client.getSoundManager().play(
                PositionedSoundInstance.master(sound, 1.0F, volume)));
    }

    /** The conversation on screen in the open messenger, or null. */
    private static String openConversation() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof MenuScreen menu) || !menu.isMessengerViewOpen()) return null;
        return MessengerService.getInstance().getSelectedConversation();
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
            SkinAvatarService skins = SkinAvatarService.getInstance();
            skins.draw(matrix, skins.head(toast.username()), x + PAD, y + PAD, AVATAR, MenuStyle.withAlpha(0xFFFFFFFF, alpha));

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
}
