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
    public final BooleanSetting saveScreenshotToDisk = new BooleanSetting(
            "Save To Disk",
            "Also save the screenshot file to the screenshots folder"
    ).setValue(true).visible(screencopy::isValue);

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
        super("chat_helper", "Chat Helper", ModuleCategory.UTILITIES);
        collapseRepeats.setFullWidth(true);
        screencopy.setFullWidth(true);
        saveScreenshotToDisk.setFullWidth(true);
        setup(generalSection, collapseRepeats, screencopySection, screencopy, saveScreenshotToDisk);
    }

    public static ChatHelper getInstance() {
        return INSTANCE;
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
        messages.remove(0);
        List<ChatHudLine.Visible> visible = accessor.phaze$getVisibleMessages();
        if (visible != null && !visible.isEmpty()) {
            // Long wrapped lines can leave residual visible entries; vanilla
            // refresh on width change cleans them up.
            visible.remove(0);
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
     * True when the screenshot file should still hit disk. Returns true
     * when Screencopy is OFF (vanilla untouched) and when Screencopy is
     * ON but Save To Disk is also ON - the only "don't save" case is
     * "Screencopy on AND Save To Disk off". Keeps the mixin branchless.
     */
    public boolean shouldSaveScreenshotToDisk() {
        return !screencopy.isValue() || saveScreenshotToDisk.isValue();
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
