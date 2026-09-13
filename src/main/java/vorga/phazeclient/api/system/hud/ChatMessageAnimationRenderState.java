package vorga.phazeclient.api.system.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudLine;

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
