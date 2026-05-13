/*
 * Adapted from screencopy by ImUrX (https://github.com/ImUrX/screencopy).
 *
 * Copyright (c) 2021 ImUrX contributors
 * Licensed under the MIT License - see THIRD_PARTY_LICENSES.md
 * at the project root for the full notice.
 */
package vorga.phazeclient.mixins;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.ChatHelper;

import java.io.File;
import java.util.function.Consumer;

/**
 * Screencopy path inside {@link
 * vorga.phazeclient.implement.features.modules.other.ChatHelper}.
 * Hooks the inner save helper that vanilla calls on the IO worker
 * after capturing the framebuffer - that's the earliest spot where
 * we have a fully populated {@link NativeImage} but the disk write
 * hasn't happened yet, so the same one-shot intercept lets us:
 *
 * <ol>
 *   <li>Mirror the bitmap onto the system clipboard, asynchronously
 *       so AWT clipboard locking can't stall the IO worker.</li>
 *   <li>Cancel the disk write entirely if the user opted out of
 *       {@code Save To Disk}, which would otherwise leave a stray
 *       PNG file behind every time they press F2.</li>
 * </ol>
 *
 * <p>The mixin is purely a forwarder: it only checks
 * {@link ChatHelper#shouldCopyScreenshot()} and
 * {@link ChatHelper#shouldSaveScreenshotToDisk()} and delegates the
 * actual clipboard work to the module - so module-level disable,
 * profile reload, and config save all "just work" without the mixin
 * needing to know any of that.
 *
 * <p>Method name {@code saveScreenshotInner} is the yarn 1.21.4
 * mapping for what older mods referenced as {@code method_1661};
 * the signature {@code (NativeImage, File, Consumer<Text>)} is
 * stable across the 1.20.x -> 1.21.x window.
 */
@Mixin(ScreenshotRecorder.class)
public abstract class ScreenshotRecorderScreencopyMixin {

    @Inject(method = "saveScreenshotInner", at = @At("HEAD"), cancellable = true)
    private static void phaze$screencopyOnSave(NativeImage image, File file, Consumer<Text> messageReceiver, CallbackInfo ci) {
        ChatHelper helper = ChatHelper.getInstance();
        if (helper == null || !helper.shouldCopyScreenshot()) {
            return;
        }

        // Capture pixels NOW on the IO worker (image is still alive
        // here; the calling site closes it in a try-finally AFTER
        // this method returns). The actual clipboard system call
        // happens on a daemon thread inside copyImageToClipboardAsync
        // so we don't pay AWT init / clipboard-lock latency on the
        // worker queue.
        try {
            helper.copyImageToClipboardAsync(image, messageReceiver);
        } catch (Throwable t) {
            // Defensive: a bad pixel readout or AWT init failure must
            // never crash the screenshot flow - vanilla still gets to
            // save the file below.
            if (messageReceiver != null) {
                messageReceiver.accept(Text.literal("Screencopy failed: " + t.getClass().getSimpleName()));
            }
            return;
        }

        if (!helper.shouldSaveScreenshotToDisk()) {
            ci.cancel();
        }
    }
}
