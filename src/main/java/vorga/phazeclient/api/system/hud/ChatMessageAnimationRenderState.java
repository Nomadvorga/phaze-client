package vorga.phazeclient.api.system.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudLine;

/** Per-line state shared by the new 1.21.11 chat backend implementations. */
public final class ChatMessageAnimationRenderState {
    private static final ThreadLocal<Offset> ACTIVE = ThreadLocal.withInitial(Offset::new);

    private ChatMessageAnimationRenderState() {
    }

    public static void begin(ChatHudLine.Visible line) {
        Offset offset = ACTIVE.get();
        offset.line = line;
        offset.dx = 0.0F;
        offset.dy = 0.0F;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null
                || !(client.inGameHud.getChatHud() instanceof ChatAnimationFrameAccess access)
                || !access.phaze$shouldShiftChatLine(line)) {
            return;
        }
        offset.dx = access.phaze$getChatFrameDx();
        offset.dy = access.phaze$getChatFrameDy();
    }

    public static void end() {
        // This state is render-thread-local. Keep the small holder allocated
        // instead of recreating it for every visible chat line each frame.
        Offset offset = ACTIVE.get();
        offset.line = null;
        offset.dx = 0.0F;
        offset.dy = 0.0F;
    }

    public static boolean active() {
        Offset offset = ACTIVE.get();
        return offset.dx != 0.0F || offset.dy != 0.0F;
    }

    public static float dx() {
        return ACTIVE.get().dx;
    }

    public static float dy() {
        return ACTIVE.get().dy;
    }

    public static ChatHudLine.Visible line() {
        return ACTIVE.get().line;
    }

    private static final class Offset {
        private float dx;
        private float dy;
        private ChatHudLine.Visible line;
    }
}
