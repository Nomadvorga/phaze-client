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

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
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

    /**
     * Longer Chat History section. Vanilla caps both the visible-message
     * buffer and the recent-input buffer at 100 entries; this toggle
     * raises that cap to whatever value the slider is at, mirroring the
     * upstream
     * <a href="https://github.com/xBackpack/InfChatHistory">InfChatHistory</a>
     * mod by xBackpack (CC0). The mixin
     * {@link vorga.phazeclient.mixins.ChatHudHistoryLimitMixin}
     * rewrites the three {@code 100} integer constants in
     * {@link net.minecraft.client.gui.hud.ChatHud} to the value
     * returned by {@link #getChatHistoryLimit()}.
     */
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

    /**
     * Anti-Caps section. When the local player sends a chat message
     * whose alphabetical content is &gt;= {@link #ANTI_CAPS_THRESHOLD}
     * uppercase, the {@code @ModifyArg} hook in
     * {@link vorga.phazeclient.mixins.ClientPlayNetworkHandlerAntiCapsMixin}
     * lowercases the entire string in-place before it leaves the
     * client, so the recipient sees a non-shouty version. Commands
     * (slash-prefixed) are intentionally untouched - command
     * arguments often need exact case (player names, JSON, etc.).
     */
    public final SectionSetting antiCapsSection = new SectionSetting("Anti Caps");
    public final BooleanSetting antiCaps = new BooleanSetting(
            "Anti Caps",
            "Auto-lowercase outgoing chat messages whose content is at least 75% uppercase"
    ).setValue(false);

    /** Minimum proportion of uppercase letters that triggers the auto-lowercase rewrite. */
    private static final float ANTI_CAPS_THRESHOLD = 0.75F;

    private boolean bypass = false;

    /**
     * The original styled text of the most recent unique message. Stored so
     * collapsed re-renders can keep the original colors / formatting and
     * append a separately-colored {@code (Nx)} suffix instead of flattening
     * everything to plain white.
     */
    private Text lastBaseStyled;
    private String lastBaseRaw;
    private int lastCount;

    /**
     * Coalesces same-frame F2 spam into a single clipboard push.
     * Successive screenshots within 50 ms - which mostly happens when
     * other mods take their own programmatic screenshots back-to-back
     * - reuse the first one's clipboard contents instead of fighting
     * for the AWT clipboard lock.
     */
    private final AtomicLong lastClipboardWriteMs = new AtomicLong(0L);

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
        // The mixin reads getChatHistoryLimit() lazily on every
        // addMessage / addToMessageHistory call, so the new cap takes
        // effect for any incoming traffic from this point. We don't
        // grow the buffers retroactively - that's a non-issue because
        // they're already small and will fill up naturally.
        phaze$applyHistoryLimit();
    }

    @Override
    public void deactivate() {
        super.deactivate();
        // When the user turns the module off we have to actively trim
        // the buffers back down to vanilla's 100-cap. The mixin reverts
        // its returned cap immediately (getChatHistoryLimit() now
        // returns 100), but vanilla only enforces that cap inside
        // {@code while (size > 100)} guards that fire on the next
        // addMessage / addToMessageHistory call. Until then any
        // already-accumulated 200..32767 entries stay in memory and
        // visible in scrollback / Up-arrow recall, which the user
        // reasonably reads as "module didn't disable". Trimming
        // explicitly here makes the disable instant.
        phaze$applyHistoryLimit();
    }

    /**
     * Trims {@link ChatHud}'s three internal lists down to the current
     * effective cap. Called from {@link #activate()} /
     * {@link #deactivate()} and from the {@link #longerHistory} toggle's
     * {@code onChange} callback so any state transition that reduces
     * the cap takes effect on the very next frame. When the cap is
     * being raised this is a no-op (every list size is already <= the
     * new larger cap).
     */
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
        return 21.0F;
    }

    /**
     * Set while we're recursively re-adding a message we modified, so the
     * collapse mixin doesn't reprocess our own output as a duplicate.
     */
    public boolean isBypassActive() {
        return bypass;
    }

    /**
     * Inspects the latest message in the {@link ChatHud}. If the new message
     * matches the most recent unique base (ignoring any prior {@code (Nx)}
     * suffix), removes the previous occurrence and returns a styled
     * replacement: the ORIGINAL incoming text (with full color/formatting)
     * followed by a red {@code (Nx)} sibling. Returns {@code null} when no
     * collapse should happen.
     */
    public Text tryCollapse(ChatHud hud, Text incoming) {
        if (!isEnabled() || !collapseRepeats.isValue() || incoming == null) {
            return null;
        }

        String incomingRaw = stripSuffixRaw(incoming.getString());

        // Different message arrived: reset the collapse state so the next
        // duplicate is counted against THIS one (whose styled form vanilla
        // is about to render normally).
        if (lastBaseRaw == null || !lastBaseRaw.equals(incomingRaw)) {
            lastBaseStyled = incoming;
            lastBaseRaw = incomingRaw;
            lastCount = 1;
            return null;
        }

        // Duplicate of the previous message - we need to remove whatever is
        // currently sitting at the top of the chat and re-add a collapsed
        // version.
        ChatHudAccessor accessor = (ChatHudAccessor) hud;
        List<ChatHudLine> messages = accessor.phaze$getMessages();
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        ChatHudLine removed = messages.remove(0);
        List<ChatHudLine.Visible> visible = accessor.phaze$getVisibleMessages();
        if (visible != null && !visible.isEmpty() && removed != null) {
            // Vanilla's addVisibleMessage breaks long messages into N
            // wrapped Visible entries, ALL sharing the same addedTime
            // (= the parent ChatHudLine's creationTick). Removing only
            // visible.remove(0) drops one wrapped line but leaves the
            // others as orphans with the same recent timestamp - they
            // still render in vanilla's loop (their addedTime stays
            // young, so the unfocused-mode `age >= 200` skip never
            // triggers) but the matching text has already been pulled,
            // so the user sees a stack of phantom row backgrounds with
            // no visible text above the chat - exactly the dark
            // rectangle reported when "many lines" of collapsible
            // messages have arrived. Removing every Visible whose
            // addedTime matches the removed ChatHudLine's creationTick
            // wipes the whole wrapped block in one pass.
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

    /**
     * Strips a trailing {@code " (Nx)"} suffix from a raw message string so
     * comparisons treat "hello" and "hello (3x)" as the same base. The numeric
     * count itself is irrelevant when matching - we keep our own counter.
     */
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

    /** True when the screenshot mixin should intercept the save and push to clipboard. */
    public boolean shouldCopyScreenshot() {
        return isEnabled() && screencopy.isValue();
    }

    /**
     * Effective limit on chat-line / recent-message buffers, used by
     * {@link vorga.phazeclient.mixins.ChatHudHistoryLimitMixin}. Returns
     * {@code 100} (the vanilla constant) when the module or the
     * Longer Chat History toggle is off, so disabling either path
     * leaves the user with the standard limit and no surprise memory
     * growth on next launch. Slider clamp keeps us under
     * {@link Short#MAX_VALUE} which matches the upstream
     * InfChatHistory cap.
     */
    public int getChatHistoryLimit() {
        if (!isEnabled() || !longerHistory.isValue()) {
            return 100;
        }
        return Math.max(100, Math.min(Short.MAX_VALUE, historyLimit.getInt()));
    }

    /**
     * Returns the lowercase form of {@code message} when Anti-Caps
     * applies, the original string otherwise. Mixin
     * {@link vorga.phazeclient.mixins.ClientPlayNetworkHandlerAntiCapsMixin}
     * forwards every outgoing chat message through here right before
     * {@code ClientPlayNetworkHandler.sendChatMessage} hands it to
     * the network layer, so the recipient sees the rewritten text.
     *
     * <p>The "uppercase ratio" is computed against alphabetic
     * characters only - digits, spaces, emoji and punctuation don't
     * pull the ratio either way. A short message of 4 letters
     * needs 3 uppercase to cross the 75% threshold, which matches
     * the spec ("на 75% состоит из капса"). Empty / no-letter
     * messages can't trigger because their alpha count is zero.
     *
     * <p>Slash-prefixed commands are skipped: command arguments
     * (player names, JSON, raw strings) routinely need preserved
     * case. Anti-Caps is only meaningful for plain chat.
     */
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

    /**
     * Reads pixels off the {@link NativeImage} synchronously (it's only
     * alive on the calling IO worker thread, and closing happens right
     * after the mixin returns), then hands the resulting
     * {@link BufferedImage} to a daemon thread for the actual clipboard
     * push. Clipboard system calls can block on a contested clipboard
     * lock; running them on the IO worker would back up other screenshot
     * saves queued behind this one.
     *
     * <p>Adapted from <a href="https://github.com/ImUrX/screencopy">screencopy</a>
     * by ImUrX, used under the MIT License (Copyright (c) 2021 ImUrX
     * contributors). See {@code THIRD_PARTY_LICENSES.md} at the project
     * root for the full notice.
     */
    public void copyImageToClipboardAsync(NativeImage image, Consumer<Text> messageReceiver) {
        if (image == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long previous = lastClipboardWriteMs.get();
        if (now - previous < 50L) {
            // Two screenshots fired in the same ~tick window: skip the
            // second clipboard push to avoid thrashing the AWT lock.
            return;
        }
        lastClipboardWriteMs.set(now);

        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        // NativeImage.getColor returns ABGR little-endian (R is low
        // byte, A is high byte) for the default RGBA format that
        // ScreenshotRecorder produces. Repack to ARGB for AWT.
        NativeImageGetColorInvoker invoker = (NativeImageGetColorInvoker) (Object) image;
        BufferedImage buf = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int abgr = invoker.phaze$invokeGetColor(x, y);
                int r = abgr & 0xFF;
                int g = (abgr >>> 8) & 0xFF;
                int b = (abgr >>> 16) & 0xFF;
                int a = (abgr >>> 24) & 0xFF;
                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                buf.setRGB(x, y, argb);
            }
        }

        Thread t = new Thread(() -> {
            try {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(new ImageTransferable(buf), null);
                if (messageReceiver != null) {
                    messageReceiver.accept(Text.literal("Screenshot copied to clipboard")
                            .formatted(Formatting.GREEN));
                }
            } catch (Throwable err) {
                if (messageReceiver != null) {
                    messageReceiver.accept(Text.literal("Screencopy failed: " + err.getMessage())
                            .formatted(Formatting.RED));
                }
            }
        }, "Phaze-Screencopy");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Minimal AWT {@link Transferable} that exposes a single
     * {@link BufferedImage} via {@link DataFlavor#imageFlavor}. AWT's
     * default clipboard supports {@code imageFlavor} on all three
     * desktop platforms (Windows, macOS, X11/Wayland with a working
     * java.awt clipboard service), so we don't try to fall back to
     * other flavors - either it works or the catch in
     * {@link #copyImageToClipboardAsync} surfaces the error.
     */
    private record ImageTransferable(BufferedImage image) implements Transferable {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { DataFlavor.imageFlavor };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!DataFlavor.imageFlavor.equals(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }
}
