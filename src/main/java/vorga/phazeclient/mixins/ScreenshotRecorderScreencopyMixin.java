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

            if (vanillaImageConsumer != null) {
                vanillaImageConsumer.accept(image);
            }
        };
        original.call(framebuffer, downscaleFactor, decorated);
    }
}
