package vorga.phazeclient.mixins;

import com.google.common.collect.ImmutableMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gl.CompiledShader;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(ShaderLoader.class)
public abstract class ShaderLoaderWorldColorSourceGuardMixin {
    @Inject(
            method = "loadShaderSource",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void phaze$skipSodiumShaderSourcesWithoutSodium(
            Identifier id,
            Resource resource,
            CompiledShader.Type type,
            Map<Identifier, Resource> allResources,
            ImmutableMap.Builder<?, String> builder,
            CallbackInfo ci
    ) {
        if ("sodium".equals(id.getNamespace()) && !FabricLoader.getInstance().isModLoaded("sodium")) {
            ci.cancel();
        }
    }
}
