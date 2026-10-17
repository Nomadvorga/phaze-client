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
 * <p>In 1.21.4 {@code saveScreenshotInner} has the signature
 * {@code (File, String, Framebuffer, Consumer<Text>)} and creates
 * the {@link NativeImage} by delegating to the static helper
 * {@code ScreenshotRecorder.takeScreenshot(Framebuffer)}. We wrap
 * that inner call to intercept the resulting image while it is still
 * alive, then hand it to {@link ChatHelper#copyImageToClipboardAsync}.
 * The original call proceeds unchanged - vanilla's disk save path
 * is not affected.
 */
@Mixin(ScreenshotRecorder.class)
public abstract class ScreenshotRecorderScreencopyMixin {

    @WrapOperation(
            method = "saveScreenshotInner",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/ScreenshotRecorder;takeScreenshot(Lnet/minecraft/client/gl/Framebuffer;)Lnet/minecraft/client/texture/NativeImage;")
    )
    private static NativeImage phaze$screencopyWrapToImage(net.minecraft.client.gl.Framebuffer framebuffer, Operation<NativeImage> original, File file, String fileName, net.minecraft.client.gl.Framebuffer fb2, Consumer<Text> messageReceiver) {
        NativeImage image = original.call(framebuffer);
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
        return image;
    }
}
