/*
 * Adapted from screencopy by ImUrX (https://github.com/ImUrX/screencopy).
 *
 * Copyright (c) 2021 ImUrX contributors
 * Licensed under the MIT License - see THIRD_PARTY_LICENSES.md
 * at the project root for the full notice.
 */
package vorga.phazeclient.mixins;

import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the package-private {@code NativeImage.getColor(x, y)}
 * accessor so the screencopy feature inside {@link
 * vorga.phazeclient.implement.features.modules.other.ChatHelper} can
 * read pixels from a captured screenshot without going through
 * reflection. Mirrors the approach upstream {@code screencopy}
 * (ImUrX/screencopy 1.2.3) takes - the only stable cross-version
 * pixel readout from NativeImage is through this private getter,
 * and the Mixin Invoker mechanism keeps the access purely
 * compile-time without runtime {@code setAccessible} calls.
 *
 * <p>Returned int format matches the underlying {@code NativeImage}
 * pixel format (ABGR little-endian for the default Format.RGBA
 * screenshots), not ARGB. Callers must reinterpret the bytes.
 */
@Mixin(NativeImage.class)
public interface NativeImageGetColorInvoker {

    @Invoker("getColor")
    int phaze$invokeGetColor(int x, int y);
}
