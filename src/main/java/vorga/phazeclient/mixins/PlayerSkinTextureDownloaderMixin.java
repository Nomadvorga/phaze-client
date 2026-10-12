package vorga.phazeclient.mixins;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.PlayerSkinTextureDownloader;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.ThreeDSkinsVoxelizer;

import java.util.concurrent.CompletableFuture;

@Mixin(PlayerSkinTextureDownloader.class)
public abstract class PlayerSkinTextureDownloaderMixin {

    @Inject(
            method = "registerTexture(Lnet/minecraft/util/Identifier;Lnet/minecraft/client/texture/NativeImage;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD")
    )
    private static void phaze$onRegisterSkinTexture(Identifier id, NativeImage image, CallbackInfoReturnable<CompletableFuture<Identifier>> cir) {
        ThreeDSkinsVoxelizer.bake(id, image);
    }
}
