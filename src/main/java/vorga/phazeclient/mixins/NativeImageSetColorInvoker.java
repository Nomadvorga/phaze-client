package vorga.phazeclient.mixins;

import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the private {@code NativeImage.setColor(x, y, color)} writer.
 *
 * <p>Counterpart to {@link NativeImageGetColorInvoker}, kept in its own
 * file because that one carries the screencopy (MIT) notice and this
 * has nothing to do with it: the writer is used by
 * {@link vorga.phazeclient.implement.menu.MenuPanoramaRegistry} to
 * build downscaled panorama thumbnails, which needs per-pixel writes
 * into a fresh image.
 *
 * <p>Colour format matches the source image's - for the RGBA panoramas
 * this handles, that is ABGR packed little-endian, the same layout the
 * getter returns. Values read through the getter can be written back
 * unchanged.
 */
@Mixin(NativeImage.class)
public interface NativeImageSetColorInvoker {

    @Invoker("setColor")
    void phaze$invokeSetColor(int x, int y, int color);
}
