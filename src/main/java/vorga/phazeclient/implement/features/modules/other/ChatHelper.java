package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.mixins.ChatHudAccessor;
import vorga.phazeclient.mixins.NativeImageGetColorInvoker;

import io.github.imurx.arboard.ImageData;
import io.github.imurx.arboard.Clipboard;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatHelper extends Module {
    private static final ChatHelper INSTANCE = new ChatHelper();
    private static final Pattern COUNT_SUFFIX = Pattern.compile("\\s*\\((\\d+)x\\)\\s*$");

    public final SectionSetting generalSection = new SectionSetting("General");
    public final BooleanSetting collapseRepeats = new BooleanSetting(
            "Collapse Repeats",
            "Collapse consecutive identical chat messages into one with a red (Nx) suffix"
    ).setValue(true);
    public final SectionSetting screencopySection = new SectionSetting("Screencopy");
    public final BooleanSetting screencopy = new BooleanSetting(
            "Screencopy",
            "Copy taken screenshots to the system clipboard (F2)"
    ).setValue(true);

    public final SectionSetting historySection = new SectionSetting("Longer Chat History");
    public final BooleanSetting longerHistory = new BooleanSetting(
            "Longer Chat History",
            "Raise the chat scrollback / sent-message history cap above the vanilla 100-line limit"
    ).setValue(false).onChange(v -> getInstance().phaze$applyHistoryLimit());
    public final ValueSetting historyLimit = new ValueSetting(
            "History Limit",
            "Maximum number of chat lines and recent messages kept in memory when Longer Chat History is on"
    ).range(200, 32767).setValue(1000)
            .visible(() -> longerHistory.isValue());

    public final SectionSetting antiCapsSection = new SectionSetting("Anti Caps");
    public final BooleanSetting antiCaps = new BooleanSetting(
            "Anti Caps",
            "Auto-lowercase outgoing chat messages whose content is at least 75% uppercase"
    ).setValue(false);

    private static final float ANTI_CAPS_THRESHOLD = 0.75F;

    private boolean bypass = false;

    private Text lastBaseStyled;
    private String lastBaseRaw;
    private int lastCount;

    private final AtomicLong lastClipboardWriteMs = new AtomicLong(0L);
    private final AtomicLong lastClipboardErrorMs = new AtomicLong(0L);
    private static volatile Clipboard phaze$nativeClipboard;
    private static volatile boolean phaze$nativeClipboardBroken = false;

    private ChatHelper() {
        super("chat_helper", "Chat Helper", ModuleCategory.OTHER);
        collapseRepeats.setFullWidth(true);
        screencopy.setFullWidth(true);
        longerHistory.setFullWidth(true);
        historyLimit.setFullWidth(true);
        antiCaps.setFullWidth(true);
        setup(generalSection, collapseRepeats,
                screencopySection, screencopy,
                historySection, longerHistory, historyLimit,
                antiCapsSection, antiCaps);
    }

    public static ChatHelper getInstance() {
        return INSTANCE;
    }

    @Override
    public void activate() {
        super.activate();

        phaze$applyHistoryLimit();
    }

    @Override
    public void deactivate() {
        super.deactivate();

        phaze$applyHistoryLimit();
    }

    private void phaze$applyHistoryLimit() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null) {
            return;
        }
        ChatHud hud = client.inGameHud.getChatHud();
        if (hud == null) {
            return;
        }
        int cap = getChatHistoryLimit();
        ChatHudAccessor accessor = (ChatHudAccessor) hud;
        List<ChatHudLine> messages = accessor.phaze$getMessages();
        if (messages != null) {
            while (messages.size() > cap) {
                messages.remove(messages.size() - 1);
            }
        }
        List<ChatHudLine.Visible> visibleMessages = accessor.phaze$getVisibleMessages();
        if (visibleMessages != null) {
            while (visibleMessages.size() > cap) {
                visibleMessages.remove(visibleMessages.size() - 1);
            }
        }
        net.minecraft.util.collection.ArrayListDeque<String> messageHistory = accessor.phaze$getMessageHistory();
        if (messageHistory != null) {
            while (messageHistory.size() > cap) {
                messageHistory.removeFirst();
            }
        }
    }

    @Override
    public String getDescription() {
        return "Tweaks for chat and screenshots: collapse repeated messages, copy F2 screenshots to clipboard";
    }

    @Override
    public String getIcon() {
        return "chat_helper.png";
    }

    @Override
    public float getIconSize() {
        return 28.35F;
    }

    @Override
    public float getIconOffsetY() {
        return -2.0F;
    }

    public boolean isBypassActive() {
        return bypass;
    }

    public Text tryCollapse(ChatHud hud, Text incoming) {
        if (!isEnabled() || !collapseRepeats.isValue() || incoming == null) {
            return null;
        }

        String incomingRaw = stripSuffixRaw(incoming.getString());

        if (lastBaseRaw == null || !lastBaseRaw.equals(incomingRaw)) {
            lastBaseStyled = incoming;
            lastBaseRaw = incomingRaw;
            lastCount = 1;
            return null;
        }

        ChatHudAccessor accessor = (ChatHudAccessor) hud;
        List<ChatHudLine> messages = accessor.phaze$getMessages();
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        ChatHudLine removed = messages.remove(0);
        List<ChatHudLine.Visible> visible = accessor.phaze$getVisibleMessages();
        if (visible != null && !visible.isEmpty() && removed != null) {

            int removedTick = removed.creationTick();
            visible.removeIf(v -> v != null && v.addedTime() == removedTick);
        }

        lastCount++;
        MutableText replacement = lastBaseStyled.copy()
                .append(Text.literal(" (" + lastCount + "x)").formatted(Formatting.RED));
        return replacement;
    }

    public void runWithBypass(Runnable runnable) {
        bypass = true;
        try {
            runnable.run();
        } finally {
            bypass = false;
        }
    }

    private String stripSuffixRaw(String text) {
        if (text == null) {
            return "";
        }
        Matcher matcher = COUNT_SUFFIX.matcher(text);
        if (matcher.find()) {
            return text.substring(0, matcher.start());
        }
        return text;
    }

    public boolean shouldCopyScreenshot() {
        return isEnabled() && screencopy.isValue();
    }

    public int getChatHistoryLimit() {
        if (!isEnabled() || !longerHistory.isValue()) {
            return 100;
        }
        return Math.max(100, Math.min(Short.MAX_VALUE, historyLimit.getInt()));
    }

    public String maybeAntiCaps(String message) {
        if (!isEnabled() || !antiCaps.isValue() || message == null) {
            return message;
        }
        if (message.isEmpty() || message.startsWith("/")) {
            return message;
        }
        int upper = 0;
        int alpha = 0;
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (Character.isLetter(c)) {
                alpha++;
                if (Character.isUpperCase(c)) {
                    upper++;
                }
            }
        }
        if (alpha == 0) {
            return message;
        }
        if (((float) upper / alpha) >= ANTI_CAPS_THRESHOLD) {
            return message.toLowerCase(java.util.Locale.ROOT);
        }
        return message;
    }

    public void copyImageToClipboardAsync(NativeImage image, Consumer<Text> messageReceiver) {
        if (image == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long previous = lastClipboardWriteMs.get();
        if (now - previous < 250L) {
            return;
        }
        lastClipboardWriteMs.set(now);

        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        NativeImageGetColorInvoker invoker = (NativeImageGetColorInvoker) (Object) image;

        Thread t = new Thread(() -> {
            try {
                if (!phaze$nativeClipboardBroken) {
                    try {
                        byte[] rgba = phaze$toNativeRgbaBytes(image, invoker, width, height);
                        phaze$copyNative(width, height, rgba);
                        return;
                    } catch (UnsatisfiedLinkError e) {
                        phaze$nativeClipboardBroken = true;
                    }
                }
                if (!phaze$copyViaWindowsClipboardBridgeNativeImage(image)) {
                    phaze$sendClipboardError(messageReceiver, "Screencopy Failed: native + bridge unavailable");
                }
            } catch (Throwable err) {
                phaze$sendClipboardError(messageReceiver, "Screencopy Failed: " + err);
            }
        }, "Phaze-Screencopy");
        t.setDaemon(true);
        t.start();
    }

    private void phaze$sendClipboardError(Consumer<Text> messageReceiver, String message) {
        if (messageReceiver == null) return;
        long now = System.currentTimeMillis();
        long last = lastClipboardErrorMs.get();
        if (now - last < 3000L) return;
        lastClipboardErrorMs.set(now);
        messageReceiver.accept(Text.literal(message).formatted(Formatting.RED));
    }

    private static Clipboard phaze$getNativeClipboard() {
        Clipboard local = phaze$nativeClipboard;
        if (local != null) return local;
        synchronized (ChatHelper.class) {
            if (phaze$nativeClipboard == null) {
                phaze$nativeClipboard = new Clipboard();
            }
            return phaze$nativeClipboard;
        }
    }

    private static void phaze$copyNative(int width, int height, byte[] rgba) {
        ImageData data = new ImageData(width, height, rgba);
        try {
            phaze$getNativeClipboard().setImage(data);
        } finally {
            data.close();
        }
    }

    private static byte[] phaze$toNativeRgbaBytes(NativeImage image, NativeImageGetColorInvoker invoker, int width, int height) {
        java.nio.ByteBuffer imageBytes = java.nio.ByteBuffer
                .allocate(width * height * 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                imageBytes.putInt(invoker.phaze$invokeGetColor(x, y));
            }
        }
        return imageBytes.array();
    }

    private static boolean phaze$copyViaWindowsClipboardBridgeNativeImage(NativeImage image) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (!os.contains("win")) return false;
        Path temp = Files.createTempFile("phaze_screencopy_", ".png");
        try {
            image.writeTo(temp);
            String p = temp.toAbsolutePath().toString().replace("'", "''");
            String script =
                    "$path='" + p + "'; " +
                    "Add-Type -AssemblyName PresentationCore; " +
                    "$fs=[System.IO.File]::OpenRead($path); " +
                    "try { " +
                    "$dec=[System.Windows.Media.Imaging.PngBitmapDecoder]::new($fs,[System.Windows.Media.Imaging.BitmapCreateOptions]::PreservePixelFormat,[System.Windows.Media.Imaging.BitmapCacheOption]::OnLoad); " +
                    "[System.Windows.Clipboard]::SetImage($dec.Frames[0]); " +
                    "} finally { $fs.Close() }";
            Process proc = new ProcessBuilder("powershell.exe", "-NoProfile", "-STA", "-Command", script)
                    .redirectErrorStream(true)
                    .start();
            return proc.waitFor() == 0;
        } finally {
            try { Files.deleteIfExists(temp); } catch (Throwable ignored) {}
        }
    }

}
