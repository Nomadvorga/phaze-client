/*
 * Adapted from screencopy by ImUrX (https://github.com/ImUrX/screencopy).
 *
 * Copyright (c) 2021 ImUrX contributors
 * Licensed under the MIT License - see THIRD_PARTY_LICENSES.md
 * at the project root for the full notice.
 */
package vorga.phazeclient.mixins;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import vorga.phazeclient.implement.features.modules.other.ChatHelper;

import java.io.File;
import java.util.function.Consumer;

/**
 * Screencopy path inside {@link
 * vorga.phazeclient.implement.features.modules.other.ChatHelper}.
 * Hooks the inner save helper that vanilla calls on the IO worker
 * after capturing the framebuffer - that's the earliest spot where
 * we have a fully populated {@link NativeImage} but the disk write
 * hasn't happened yet, so we can mirror the bitmap onto the system
 * clipboard while leaving the vanilla disk save intact.
 *
 * <p>The mixin is purely a forwarder: it checks
 * {@link ChatHelper#shouldCopyScreenshot()} and delegates the actual
 * clipboard work to the module - so module-level disable, profile
 * reload, and config save all "just work" without the mixin needing
 * to know any of that. Disk save is never cancelled; we always let
 * vanilla finish writing the {@code .png} so the user keeps a local
 * copy alongside the clipboard push.
 *
 * <p>1.21.11 reshaped this path. The private {@code saveScreenshotInner}
 * helper is gone, and screenshot capture became asynchronous: the
 * former {@code ScreenshotRecorder.takeScreenshot(Framebuffer)} that
 * returned a {@link NativeImage} is now
 * {@code takeScreenshot(Framebuffer, int downscale, Consumer<NativeImage>)}
 * which returns {@code void} and delivers the image to a callback once
 * the GPU-&gt;CPU buffer copy completes. The public entry point
 * {@code saveScreenshot(File, String, Framebuffer, int, Consumer<Text>)}
 * is what actually issues that call (the 3-arg overload just delegates
 * to it), so we wrap the {@code takeScreenshot} invocation there and
 * decorate the vanilla image consumer instead of intercepting a return
 * value.
 *
 * <p>Ordering is preserved: the clipboard push runs before vanilla's
 * consumer schedules the disk write, exactly as the old
 * "wrap the returning call" form did. The original call always
 * proceeds - vanilla's disk save path is not affected.
 */
@Mixin(ScreenshotRecorder.class)
public abstract class ScreenshotRecorderScreencopyMixin {

    @WrapOperation(
            method = "saveScreenshot(Ljava/io/File;Ljava/lang/String;Lnet/minecraft/client/gl/Framebuffer;ILjava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/ScreenshotRecorder;takeScreenshot(Lnet/minecraft/client/gl/Framebuffer;ILjava/util/function/Consumer;)V")
    )
    private static void phaze$screencopyWrapTakeScreenshot(Framebuffer framebuffer,
                                                           int downscaleFactor,
                                                           Consumer<NativeImage> vanillaImageConsumer,
                                                           Operation<Void> original,
                                                           File directory,
                                                           String fileName,
                                                           Framebuffer targetFramebuffer,
                                                           int targetDownscaleFactor,
                                                           Consumer<Text> messageReceiver) {
        Consumer<NativeImage> decorated = image -> {
            ChatHelper helper = ChatHelper.getInstance();
            if (image != null && helper != null && helper.shouldCopyScreenshot()) {
                try {
                    helper.copyImageToClipboardAsync(image, messageReceiver);
                } catch (Throwable t) {
                    if (messageReceiver != null) {
                        messageReceiver.accept(Text.literal("Screencopy failed: " + t.getClass().getSimpleName()));
                    }
                }
            }
            // Always hand the image on to vanilla so the .png still lands on disk.
            if (vanillaImageConsumer != null) {
                vanillaImageConsumer.accept(image);
            }
        };
        original.call(framebuffer, downscaleFactor, decorated);
    }
}
